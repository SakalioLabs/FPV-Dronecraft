#!/usr/bin/env python3
"""Audit 330 embedded SOFA channels and screen position-general models."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
from collections import defaultdict
from pathlib import Path
from typing import Any

import h5py
import numpy as np

from analyze_dechorate_cpu_dda_experiment import (
    DISCOVERY,
    FACETS,
    HOLDOUT,
    effective_code,
    ranked_facets,
)
from dechorate_common import (
    FS,
    ROOM_SIZE,
    SPEED_OF_SOUND,
    WALL_CODE,
)


LAMBDA_GRID = (0.01, 0.1, 1.0, 10.0, 100.0)
MODEL_FAMILIES = ("surface-coupling", "geometry-interaction")
SOURCE_FOUR_MICROPHONES = tuple(range(10, 25))
SOURCE_SIX_MICROPHONES = (
    0,
    1,
    2,
    3,
    4,
    10,
    11,
    12,
    13,
    14,
    25,
    26,
    27,
    28,
    29,
)
POSITION_DISCOVERY = tuple(
    microphone
    for microphone in SOURCE_FOUR_MICROPHONES
    if microphone % 2 == 0
)
POSITION_VALIDATION = tuple(
    microphone
    for microphone in SOURCE_FOUR_MICROPHONES
    if microphone % 2 == 1
)
NORMALS = {
    "floor": np.array([0.0, 0.0, 1.0]),
    "ceiling": np.array([0.0, 0.0, -1.0]),
    "west": np.array([1.0, 0.0, 0.0]),
    "south": np.array([0.0, 1.0, 0.0]),
    "east": np.array([-1.0, 0.0, 0.0]),
    "north": np.array([0.0, -1.0, 0.0]),
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def close_vector(observed: np.ndarray, expected: np.ndarray) -> bool:
    return bool(np.allclose(observed.reshape(-1), expected, atol=1e-7, rtol=0))


def microphone_id(array_id: int, receiver_index: int) -> int:
    if array_id < 1 or receiver_index < 0 or receiver_index >= 5:
        raise ValueError("invalid SOFA array/channel identity")
    return (array_id - 1) * 5 + receiver_index


def metadata_coordinates(
    metadata_path: Path,
) -> dict[tuple[str, int, int], tuple[np.ndarray, np.ndarray]]:
    result: dict[
        tuple[str, int, int], tuple[np.ndarray, np.ndarray]
    ] = {}
    with metadata_path.open("r", encoding="utf-8", newline="") as stream:
        for row in csv.DictReader(stream):
            try:
                room = row["room_code"]
                source_id = int(float(row["src_id"]))
                mic_id = int(float(row["mic_id"]))
                if source_id not in (4, 6):
                    continue
                source = np.array(
                    [
                        float(row["src_pos_x"]),
                        float(row["src_pos_y"]),
                        float(row["src_pos_z"]),
                    ]
                )
                microphone = np.array(
                    [
                        float(row["mic_pos_x"]),
                        float(row["mic_pos_y"]),
                        float(row["mic_pos_z"]),
                    ]
                )
            except (TypeError, ValueError):
                continue
            key = (room, source_id, mic_id)
            previous = result.get(key)
            if previous is not None and (
                not close_vector(previous[0], source)
                or not close_vector(previous[1], microphone)
            ):
                raise ValueError("metadata coordinates are not unique")
            result[key] = (source, microphone)
    return result


def reflection_geometry(
    source: np.ndarray,
    microphone: np.ndarray,
    facet: str,
) -> np.ndarray:
    axis = {
        "west": 0,
        "east": 0,
        "south": 1,
        "north": 1,
        "floor": 2,
        "ceiling": 2,
    }[facet]
    maximum = facet in {"east", "north", "ceiling"}
    plane = float(ROOM_SIZE[axis]) if maximum else 0.0
    image = source.copy()
    image[axis] = 2.0 * plane - source[axis]
    interpolation = (plane - image[axis]) / (
        microphone[axis] - image[axis]
    )
    point = image + interpolation * (microphone - image)
    source_leg = source - point
    listener_leg = microphone - point
    source_distance = float(np.linalg.norm(source_leg))
    listener_distance = float(np.linalg.norm(listener_leg))
    normal = NORMALS[facet]
    diagonal = float(np.linalg.norm(ROOM_SIZE))
    direct_distance = float(np.linalg.norm(microphone - source))
    return np.concatenate(
        (
            np.array(
                [
                    (source_distance + listener_distance) / diagonal,
                    diagonal / (source_distance + listener_distance),
                ]
            ),
            point / ROOM_SIZE,
            np.array(
                [
                    abs(float(source_leg / source_distance @ normal)),
                    abs(float(listener_leg / listener_distance @ normal)),
                ]
            ),
            microphone / ROOM_SIZE,
            np.array([direct_distance / diagonal]),
        )
    )


def feature_vector(
    room_code: str,
    source: np.ndarray,
    microphone: np.ndarray,
    facet: str,
    family: str,
) -> np.ndarray:
    code = effective_code(room_code)
    states = np.array(
        [float(code[index] == "1") for index in range(1, 6)]
    )
    target = np.eye(6, dtype=float)[FACETS.index(facet)]
    target_state = np.outer(target, states).reshape(-1)
    parts = [states, target_state]
    if family == "geometry-interaction":
        geometry = reflection_geometry(source, microphone, facet)
        parts.extend(
            (
                np.outer(states, geometry).reshape(-1),
                np.outer(target_state, geometry).reshape(-1),
            )
        )
    elif family != "surface-coupling":
        raise ValueError(f"unknown model family: {family}")
    return np.concatenate(parts)


def fit_ridge(
    design: np.ndarray,
    target: np.ndarray,
    regularization: float,
) -> tuple[np.ndarray, np.ndarray]:
    scale = np.sqrt(np.mean(np.square(design), axis=0))
    scale[scale < 1.0e-12] = 1.0
    normalized = design / scale
    gram = normalized.T @ normalized
    coefficients = np.linalg.solve(
        gram + regularization * np.eye(gram.shape[0]),
        normalized.T @ target,
    )
    return coefficients, scale


def predict(
    design: np.ndarray,
    coefficients: np.ndarray,
    scale: np.ndarray,
) -> np.ndarray:
    return design / scale @ coefficients


def evaluate(
    predicted: np.ndarray,
    observed: np.ndarray,
    keys: list[tuple[str, int, str]],
) -> dict[str, Any]:
    errors = predicted - observed
    grouped: defaultdict[tuple[str, int], list[int]] = defaultdict(list)
    for index, (room, microphone, _) in enumerate(keys):
        grouped[(room, microphone)].append(index)
    overlaps: list[float] = []
    top_one = 0
    for indices in grouped.values():
        predicted_values = {
            keys[index][2]: float(predicted[index]) for index in indices
        }
        observed_values = {
            keys[index][2]: float(observed[index]) for index in indices
        }
        predicted_top = set(ranked_facets(predicted_values))
        observed_rank = ranked_facets(observed_values)
        overlaps.append(len(predicted_top & set(observed_rank)) / 4.0)
        top_one += int(observed_rank[0] in predicted_top)
    return {
        "rows": len(keys),
        "groups": len(grouped),
        "rmse_db": float(np.sqrt(np.mean(np.square(errors)))),
        "mae_db": float(np.mean(np.abs(errors))),
        "mean_top4_overlap": float(np.mean(overlaps)),
        "random_expected_top4_overlap": 2.0 / 3.0,
        "top1_captured": top_one,
        "top1_capture_rate": top_one / len(grouped),
        "maximum_abs_error_db": float(np.max(np.abs(errors))),
    }


def analyze(
    subset_path: Path,
    pins_path: Path,
    inventory_path: Path,
    metadata_path: Path,
    sofa_directory: Path,
    d116_path: Path,
) -> dict[str, Any]:
    subset = json.loads(subset_path.read_text(encoding="utf-8"))
    pins = json.loads(pins_path.read_text(encoding="utf-8"))
    inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
    d116 = json.loads(d116_path.read_text(encoding="utf-8"))
    if (
        subset.get("schema_version") != 1
        or pins.get("source_manifest_sha256") != sha256(subset_path)
        or inventory.get("source_manifest_sha256") != sha256(subset_path)
        or inventory.get("source_pins_sha256") != sha256(pins_path)
        or d116.get("status") != "valid-dechorate-cpu-dda-analysis"
    ):
        raise ValueError("D117 source binding changed")

    metadata = metadata_coordinates(metadata_path)
    annotation_pin = pins["annotations"]
    annotation_path = sofa_directory / annotation_pin["filename"]
    if (
        annotation_path.stat().st_size != annotation_pin["bytes"]
        or sha256(annotation_path) != annotation_pin["sha256"]
    ):
        raise ValueError("D117 annotation pin changed")
    with h5py.File(annotation_path, "r") as annotations:
        echo_toa = np.array(annotations["echo_toa"])
        echo_wall = np.array(annotations["echo_wall"])
        annotation_microphones = np.array(
            annotations["microphones"], dtype=float
        )
        annotation_source = np.array(
            annotations["sources_directional_position"][:, 4],
            dtype=float,
        )
    if echo_toa.shape != (7, 30, 6) or echo_wall.shape != (7, 30, 6):
        raise ValueError("D117 annotation structure changed")
    annotated_toa = echo_toa[:, :, 4]
    annotated_walls = echo_wall[:, :, 4]

    pin_files = pins["files"]
    waveforms: dict[tuple[str, int, int], np.ndarray] = {}
    records: list[dict[str, Any]] = []
    stale_descriptions = 0
    receiver_coordinate_bindings = 0
    for item in inventory["files"]:
        filename = item["filename"]
        path = sofa_directory / filename
        expected_pin = pin_files.get(filename)
        if (
            expected_pin is None
            or path.stat().st_size != expected_pin[0]
            or sha256(path) != expected_pin[1]
        ):
            raise ValueError(f"D117 SOFA pin changed: {filename}")
        with h5py.File(path, "r") as sofa:
            if (
                tuple(sofa["Data.IR"].shape) != (1, 5, 48000)
                or float(sofa["Data.SamplingRate"][0]) != FS
                or not np.allclose(sofa["Data.Delay"][:], 0.0)
            ):
                raise ValueError(f"D117 SOFA channel structure changed: {filename}")
            source = np.array(sofa["SourcePosition"][0], dtype=float)
            description = sofa.attrs["RoomDescription"].decode()
            if "room_code:020002" in description:
                stale_descriptions += 1
            for receiver_index in range(5):
                mic_id = microphone_id(
                    item["sofa_array_id"], receiver_index
                )
                key = (item["room_code"], item["source_id"], mic_id)
                if key in waveforms:
                    raise ValueError("D117 duplicate room/source/microphone")
                microphone = np.array(
                    sofa["ReceiverPosition"][receiver_index, :, 0],
                    dtype=float,
                )
                expected = metadata.get(key)
                if expected is None or (
                    not close_vector(source, expected[0])
                    or not close_vector(microphone, expected[1])
                ):
                    raise ValueError(
                        f"D117 metadata coordinate mismatch: {key}"
                    )
                signal = np.array(
                    sofa["Data.IR"][0, receiver_index, :], dtype="<f8"
                )
                if (
                    not np.all(np.isfinite(signal))
                    or np.max(np.abs(signal)) <= 0.0
                ):
                    raise ValueError(f"D117 invalid RIR channel: {key}")
                waveforms[key] = signal
                receiver_coordinate_bindings += 1
                records.append(
                    {
                        "room_code": item["room_code"],
                        "source_id": item["source_id"],
                        "microphone_id": mic_id,
                        "sofa_array_id": item["sofa_array_id"],
                        "receiver_index": receiver_index,
                        "filename": filename,
                        "file_sha256": expected_pin[1],
                        "waveform_sha256": hashlib.sha256(
                            signal.tobytes(order="C")
                        ).hexdigest(),
                        "source_position_m": source.tolist(),
                        "microphone_position_m": microphone.tolist(),
                    }
                )
    expected_keys = {
        (room, 4, microphone)
        for room in subset["google_drive_ids"]
        for microphone in SOURCE_FOUR_MICROPHONES
    } | {
        (room, 6, microphone)
        for room in subset["google_drive_ids"]
        for microphone in SOURCE_SIX_MICROPHONES
    }
    waveform_hashes = {
        record["waveform_sha256"] for record in records
    }
    if (
        set(waveforms) != expected_keys
        or len(records) != 330
        or len(waveform_hashes) != 330
        or stale_descriptions != 66
    ):
        raise ValueError("D117 330-channel identity matrix changed")
    if not close_vector(
        annotation_source,
        metadata[("000000", 4, 10)][0],
    ):
        raise ValueError("D117 annotation source binding changed")
    for microphone in SOURCE_FOUR_MICROPHONES:
        if not close_vector(
            annotation_microphones[:, microphone],
            metadata[("000000", 4, microphone)][1],
        ):
            raise ValueError("D117 annotation microphone binding changed")

    baseline_peaks: dict[tuple[int, int], int] = {}
    direct_shifts: list[int] = []
    for source_id, microphones in (
        (4, SOURCE_FOUR_MICROPHONES),
        (6, SOURCE_SIX_MICROPHONES),
    ):
        for microphone in microphones:
            signal = waveforms[("000000", source_id, microphone)]
            source, receiver = metadata[("000000", source_id, microphone)]
            expected = (
                float(np.linalg.norm(source - receiver))
                / SPEED_OF_SOUND
                * FS
            )
            low = max(0, int(expected) - 32)
            high = min(len(signal), int(expected) + 129)
            baseline_peaks[(source_id, microphone)] = (
                low + int(np.argmax(np.abs(signal[low:high])))
            )
    for key, signal in waveforms.items():
        _, source_id, microphone = key
        baseline = baseline_peaks[(source_id, microphone)]
        low, high = baseline - 16, baseline + 17
        peak = low + int(np.argmax(np.abs(signal[low:high])))
        direct_shifts.append(peak - baseline)

    baseline_energy: dict[tuple[int, str], float] = {}
    observations: dict[tuple[str, int, str], float] = {}
    source_positions: dict[int, np.ndarray] = {}
    microphone_positions: dict[int, np.ndarray] = {}
    for microphone in SOURCE_FOUR_MICROPHONES:
        source, receiver = metadata[("000000", 4, microphone)]
        source_positions[microphone] = source
        microphone_positions[microphone] = receiver
        walls = [
            WALL_CODE[value.decode()]
            for value in annotated_walls[:, microphone]
        ]
        arrivals = annotated_toa[:, microphone] * FS
        direct_annotation = float(arrivals[walls.index("direct")])
        direct = baseline_peaks[(4, microphone)]
        signal = waveforms[("000000", 4, microphone)]
        for facet in FACETS:
            center = float(arrivals[walls.index(facet)])
            aligned = int(round(center + direct - direct_annotation))
            baseline_energy[(microphone, facet)] = float(
                np.sum(np.square(signal[aligned - 8 : aligned + 9]))
            )
    for room in subset["google_drive_ids"]:
        for microphone in SOURCE_FOUR_MICROPHONES:
            signal = waveforms[(room, 4, microphone)]
            baseline = baseline_peaks[(4, microphone)]
            low, high = baseline - 16, baseline + 17
            direct = low + int(np.argmax(np.abs(signal[low:high])))
            walls = [
                WALL_CODE[value.decode()]
                for value in annotated_walls[:, microphone]
            ]
            arrivals = annotated_toa[:, microphone] * FS
            direct_annotation = float(arrivals[walls.index("direct")])
            for facet in FACETS:
                center = float(arrivals[walls.index(facet)])
                aligned = int(round(center + direct - direct_annotation))
                energy = float(
                    np.sum(np.square(signal[aligned - 8 : aligned + 9]))
                )
                observations[(room, microphone, facet)] = (
                    10.0
                    * math.log10(
                        max(energy, np.finfo(float).tiny)
                        / max(
                            baseline_energy[(microphone, facet)],
                            np.finfo(float).tiny,
                        )
                    )
                )

    partitions = {
        "fit": (DISCOVERY, POSITION_DISCOVERY),
        "position_validation": (DISCOVERY, POSITION_VALIDATION),
        "room_validation": (HOLDOUT, POSITION_DISCOVERY),
        "joint_exploratory": (HOLDOUT, POSITION_VALIDATION),
    }

    def dataset(
        rooms: tuple[str, ...],
        microphones: tuple[int, ...],
        family: str,
    ) -> tuple[np.ndarray, np.ndarray, list[tuple[str, int, str]]]:
        design: list[np.ndarray] = []
        target: list[float] = []
        keys: list[tuple[str, int, str]] = []
        for room in rooms:
            for microphone in microphones:
                for facet in FACETS:
                    design.append(
                        feature_vector(
                            room,
                            source_positions[microphone],
                            microphone_positions[microphone],
                            facet,
                            family,
                        )
                    )
                    target.append(observations[(room, microphone, facet)])
                    keys.append((room, microphone, facet))
        return np.array(design), np.array(target), keys

    candidates: list[dict[str, Any]] = []
    fitted: dict[tuple[str, float], tuple[np.ndarray, np.ndarray]] = {}
    for family in MODEL_FAMILIES:
        train_x, train_y, _ = dataset(
            DISCOVERY, POSITION_DISCOVERY, family
        )
        validation_x, validation_y, validation_keys = dataset(
            DISCOVERY, POSITION_VALIDATION, family
        )
        for regularization in LAMBDA_GRID:
            coefficients, scale = fit_ridge(
                train_x, train_y, regularization
            )
            fitted[(family, regularization)] = (coefficients, scale)
            metrics = evaluate(
                predict(validation_x, coefficients, scale),
                validation_y,
                validation_keys,
            )
            candidates.append(
                {
                    "family": family,
                    "regularization": regularization,
                    "feature_count": train_x.shape[1],
                    "position_validation": metrics,
                }
            )
    candidates.sort(
        key=lambda candidate: (
            -candidate["position_validation"]["mean_top4_overlap"],
            -candidate["position_validation"]["top1_capture_rate"],
            candidate["position_validation"]["rmse_db"],
            candidate["feature_count"],
            candidate["regularization"],
        )
    )
    selected = candidates[0]
    selected_key = (
        selected["family"],
        selected["regularization"],
    )
    coefficients, scale = fitted[selected_key]
    partition_metrics: dict[str, Any] = {}
    for name, (rooms, microphones) in partitions.items():
        design, target, keys = dataset(
            rooms, microphones, selected["family"]
        )
        partition_metrics[name] = evaluate(
            predict(design, coefficients, scale),
            target,
            keys,
        )
    baseline_x, _, _ = dataset(
        ("000000",), POSITION_DISCOVERY, selected["family"]
    )
    baseline_prediction_max = float(
        np.max(np.abs(predict(baseline_x, coefficients, scale)))
    )
    frozen_model = {
        "family": selected["family"],
        "regularization": selected["regularization"],
        "feature_count": selected["feature_count"],
        "coefficients_normalized": coefficients.tolist(),
        "feature_scale": scale.tolist(),
    }
    frozen_model_sha256 = hashlib.sha256(
        json.dumps(
            frozen_model,
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
    ).hexdigest()
    joint = partition_metrics["joint_exploratory"]
    return {
        "schema_version": 1,
        "status": "valid-dechorate-multichannel-generalization",
        "source_subset_manifest_sha256": sha256(subset_path),
        "source_pins_sha256": sha256(pins_path),
        "source_inventory_sha256": sha256(inventory_path),
        "source_metadata_sha256": sha256(metadata_path),
        "source_d116_report_sha256": sha256(d116_path),
        "channel_audit": {
            "sofa_files": 66,
            "channels_per_file": 5,
            "measured_rirs": len(records),
            "unique_waveform_hashes": len(waveform_hashes),
            "metadata_coordinate_bindings": receiver_coordinate_bindings,
            "source4_microphones": list(SOURCE_FOUR_MICROPHONES),
            "source6_microphones": list(SOURCE_SIX_MICROPHONES),
            "stale_room_descriptions": stale_descriptions,
            "direct_shift_median_samples": float(
                np.median(np.abs(direct_shifts))
            ),
            "direct_shift_max_samples": int(
                np.max(np.abs(direct_shifts))
            ),
        },
        "records": sorted(
            records,
            key=lambda record: (
                record["room_code"],
                record["source_id"],
                record["microphone_id"],
            ),
        ),
        "split_contract": {
            "room_discovery": list(DISCOVERY),
            "room_validation": list(HOLDOUT),
            "position_discovery": list(POSITION_DISCOVERY),
            "position_validation": list(POSITION_VALIDATION),
            "selection_basis": (
                "model family and ridge strength selected only by "
                "position-validation ordering"
            ),
            "confirmatory_holdout_eligible": False,
            "contamination_reason": (
                "aggregate room×position outcomes were inspected during "
                "preliminary D117 model-family exploration"
            ),
        },
        "model_screen": {
            "lambda_grid": list(LAMBDA_GRID),
            "families": list(MODEL_FAMILIES),
            "candidates": candidates,
            "selected_family": selected["family"],
            "selected_regularization": selected["regularization"],
            "selected_feature_count": selected["feature_count"],
            "frozen_model": frozen_model,
            "frozen_model_sha256": frozen_model_sha256,
            "zero_state_max_abs_prediction_db": baseline_prediction_max,
            "partition_metrics": partition_metrics,
            "per_microphone_coefficients": False,
            "production_generalizable": False,
        },
        "decision": {
            "three_hundred_thirty_rirs_admitted": True,
            "position_general_features_admitted_for_further_study": (
                joint["mean_top4_overlap"] >= 0.8
                and joint["top1_capture_rate"] >= 0.9
            ),
            "joint_result_confirmatory": False,
            "amplitude_rmse_gate_passed": joint["rmse_db"] <= 3.0,
            "production_candidate_model_eligible": False,
            "production_change_required": False,
            "next_action": (
                "preregister-and-fetch-unseen-source4-arrays-1-2-6-"
                "then-run-confirmatory-position-holdout"
            ),
        },
        "captures_audio": False,
        "physical_endpoint_opened": False,
        "release_calibrated": False,
        "claim_boundary": (
            "All 330 channels are recomputed from 66 hash-pinned SOFA files. "
            "The geometry model removes per-microphone coefficients, but "
            "preliminary aggregate inspection contaminated the proposed "
            "joint holdout, so metrics are exploratory model-selection "
            "evidence only. No endpoint, CUDA kernel or release calibration "
            "is claimed."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--subset-manifest", type=Path, required=True)
    parser.add_argument("--pins", type=Path, required=True)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--sofa-directory", type=Path, required=True)
    parser.add_argument("--d116-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = analyze(
        args.subset_manifest,
        args.pins,
        args.inventory,
        args.metadata,
        args.sofa_directory,
        args.d116_report,
    )
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    joint = report["model_screen"]["partition_metrics"][
        "joint_exploratory"
    ]
    print(
        json.dumps(
            {
                "status": report["status"],
                "rirs": report["channel_audit"]["measured_rirs"],
                "unique_waveforms": report["channel_audit"][
                    "unique_waveform_hashes"
                ],
                "selected_family": report["model_screen"][
                    "selected_family"
                ],
                "joint_top4": joint["mean_top4_overlap"],
                "joint_rmse_db": joint["rmse_db"],
                "confirmatory": False,
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
