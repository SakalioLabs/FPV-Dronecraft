#!/usr/bin/env python3
"""Apply the frozen D117 model once to the preregistered D118 RIRs."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from collections import defaultdict
from pathlib import Path
from typing import Any

import h5py
import numpy as np

from analyze_dechorate_cpu_dda_experiment import FACETS, ranked_facets
from analyze_dechorate_multichannel_generalization import (
    close_vector,
    evaluate,
    feature_vector,
    metadata_coordinates,
    microphone_id,
)
from dechorate_common import (
    FS,
    SPEED_OF_SOUND,
    WALL_CODE,
)
from verify_dechorate_confirmatory_drive_manifest import expected_entries


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def frozen_model_hash(model: dict[str, Any]) -> str:
    return hashlib.sha256(
        json.dumps(
            model,
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
    ).hexdigest()


def gate_metrics(
    metrics: dict[str, Any], thresholds: dict[str, float]
) -> dict[str, bool]:
    return {
        "top4_overlap_passed": (
            metrics["mean_top4_overlap"]
            >= thresholds["top4_overlap_minimum"]
        ),
        "top1_capture_rate_passed": (
            metrics["top1_capture_rate"]
            >= thresholds["top1_capture_rate_minimum"]
        ),
        "rank_gate_passed": (
            metrics["mean_top4_overlap"]
            >= thresholds["top4_overlap_minimum"]
            and metrics["top1_capture_rate"]
            >= thresholds["top1_capture_rate_minimum"]
        ),
        "amplitude_rmse_gate_passed": (
            metrics["rmse_db"]
            <= thresholds["amplitude_rmse_db_maximum"]
        ),
    }


def analyze(
    preregistration_path: Path,
    drive_manifest_path: Path,
    inventory_pins_path: Path,
    metadata_path: Path,
    sofa_directory: Path,
    d117_path: Path,
    annotation_pins_path: Path,
    annotation_directory: Path,
) -> dict[str, Any]:
    preregistration = json.loads(
        preregistration_path.read_text(encoding="utf-8")
    )
    drive_manifest = json.loads(
        drive_manifest_path.read_text(encoding="utf-8")
    )
    pins = json.loads(inventory_pins_path.read_text(encoding="utf-8"))
    d117 = json.loads(d117_path.read_text(encoding="utf-8"))
    annotation_pins = json.loads(
        annotation_pins_path.read_text(encoding="utf-8")
    )
    preregistration_hash = sha256(preregistration_path)
    drive_manifest_hash = sha256(drive_manifest_path)
    model = d117["model_screen"]["frozen_model"]
    model_hash = frozen_model_hash(model)
    if (
        preregistration["status"]
        != "preregistered-before-drive-id-discovery-or-waveform-access"
        or drive_manifest["source_preregistration_sha256"]
        != preregistration_hash
        or pins["status"]
        != "byte-pins-fixed-before-sofa-payload-or-waveform-access"
        or pins["source_preregistration_sha256"] != preregistration_hash
        or pins["source_drive_manifest_sha256"] != drive_manifest_hash
        or pins["frozen_model_sha256"] != model_hash
        or d117["model_screen"]["frozen_model_sha256"] != model_hash
    ):
        raise ValueError("D118 preregistration/model/source binding changed")
    requirements = preregistration["frozen_model_requirements"]
    if (
        model["family"] != requirements["family"]
        or model["regularization"] != requirements["regularization"]
        or model["feature_count"] != requirements["feature_count"]
        or len(model["coefficients_normalized"]) != 420
        or len(model["feature_scale"]) != 420
        or requirements["refit_allowed"] is not False
        or requirements["threshold_change_allowed"] is not False
    ):
        raise ValueError("D118 frozen model requirements changed")
    entries = expected_entries(preregistration, drive_manifest)
    if set(pins["files"]) != {item["filename"] for item in entries}:
        raise ValueError("D118 byte pin filename set changed")

    annotation_pin = annotation_pins["annotations"]
    annotation_path = annotation_directory / annotation_pin["filename"]
    if (
        annotation_path.stat().st_size != annotation_pin["bytes"]
        or sha256(annotation_path) != annotation_pin["sha256"]
    ):
        raise ValueError("D118 annotation pin changed")
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
        raise ValueError("D118 annotation structure changed")
    annotated_toa = echo_toa[:, :, 4]
    annotated_walls = echo_wall[:, :, 4]

    metadata = metadata_coordinates(metadata_path)
    waveforms: dict[tuple[str, int], np.ndarray] = {}
    waveform_hashes: set[str] = set()
    d117_hashes = {
        record["waveform_sha256"] for record in d117["records"]
    }
    coordinate_bindings = 0
    stale_descriptions = 0
    verified_file_hashes: list[str] = []
    for entry in entries:
        filename = entry["filename"]
        path = sofa_directory / filename
        byte_count, file_hash = pins["files"][filename]
        if (
            path.stat().st_size != byte_count
            or sha256(path) != file_hash
        ):
            raise ValueError(f"D118 SOFA byte pin changed: {filename}")
        verified_file_hashes.append(file_hash)
        with h5py.File(path, "r") as sofa:
            if (
                tuple(sofa["Data.IR"].shape) != (1, 5, 48000)
                or float(sofa["Data.SamplingRate"][0]) != FS
                or not np.allclose(sofa["Data.Delay"][:], 0.0)
            ):
                raise ValueError(f"D118 SOFA structure changed: {filename}")
            source = np.array(sofa["SourcePosition"][0], dtype=float)
            description = sofa.attrs["RoomDescription"].decode()
            stale_descriptions += int("room_code:020002" in description)
            for receiver_index in range(5):
                mic_id = microphone_id(
                    entry["sofa_array_id"], receiver_index
                )
                key = (entry["room_code"], mic_id)
                if key in waveforms:
                    raise ValueError("D118 duplicate room/microphone")
                microphone = np.array(
                    sofa["ReceiverPosition"][receiver_index, :, 0],
                    dtype=float,
                )
                expected = metadata.get(
                    (entry["room_code"], preregistration["source_id"], mic_id)
                )
                if expected is None or (
                    not close_vector(source, expected[0])
                    or not close_vector(microphone, expected[1])
                ):
                    raise ValueError(
                        f"D118 metadata coordinate mismatch: {key}"
                    )
                signal = np.array(
                    sofa["Data.IR"][0, receiver_index, :], dtype="<f8"
                )
                if (
                    not np.all(np.isfinite(signal))
                    or np.max(np.abs(signal)) <= 0.0
                ):
                    raise ValueError(f"D118 invalid RIR channel: {key}")
                signal_hash = hashlib.sha256(
                    signal.tobytes(order="C")
                ).hexdigest()
                if signal_hash in waveform_hashes:
                    raise ValueError("D118 duplicate waveform")
                waveform_hashes.add(signal_hash)
                waveforms[key] = signal
                coordinate_bindings += 1

    microphones = tuple(
        microphone
        for definition in preregistration["arrays"]
        for microphone in definition["microphone_ids"]
    )
    expected_keys = {
        (room, microphone)
        for room in preregistration["room_codes"]
        for microphone in microphones
    }
    aggregate_hash = hashlib.sha256(
        "".join(verified_file_hashes).encode("ascii")
    ).hexdigest()
    if (
        set(waveforms) != expected_keys
        or len(waveforms) != 165
        or len(waveform_hashes) != 165
        or waveform_hashes & d117_hashes
        or aggregate_hash != pins["aggregate_sha256"]
        or sum(
            (sofa_directory / item["filename"]).stat().st_size
            for item in entries
        )
        != pins["total_bytes"]
    ):
        raise ValueError("D118 unseen channel identity matrix changed")

    baseline_peaks: dict[int, int] = {}
    direct_shifts: list[int] = []
    source_positions: dict[int, np.ndarray] = {}
    microphone_positions: dict[int, np.ndarray] = {}
    for microphone in microphones:
        source, receiver = metadata[
            ("000000", preregistration["source_id"], microphone)
        ]
        if (
            not close_vector(annotation_source, source)
            or not close_vector(
                annotation_microphones[:, microphone], receiver
            )
        ):
            raise ValueError("D118 annotation coordinate binding changed")
        source_positions[microphone] = source
        microphone_positions[microphone] = receiver
        signal = waveforms[("000000", microphone)]
        expected_sample = (
            float(np.linalg.norm(source - receiver))
            / SPEED_OF_SOUND
            * FS
        )
        low = max(0, int(expected_sample) - 32)
        high = min(len(signal), int(expected_sample) + 129)
        baseline_peaks[microphone] = (
            low + int(np.argmax(np.abs(signal[low:high])))
        )
    for (room, microphone), signal in waveforms.items():
        baseline = baseline_peaks[microphone]
        low, high = baseline - 16, baseline + 17
        peak = low + int(np.argmax(np.abs(signal[low:high])))
        direct_shifts.append(peak - baseline)

    baseline_energy: dict[tuple[int, str], float] = {}
    observations: dict[tuple[str, int, str], float] = {}
    for microphone in microphones:
        signal = waveforms[("000000", microphone)]
        walls = [
            WALL_CODE[value.decode()]
            for value in annotated_walls[:, microphone]
        ]
        arrivals = annotated_toa[:, microphone] * FS
        direct_annotation = float(arrivals[walls.index("direct")])
        direct = baseline_peaks[microphone]
        for facet in FACETS:
            center = float(arrivals[walls.index(facet)])
            aligned = int(round(center + direct - direct_annotation))
            baseline_energy[(microphone, facet)] = float(
                np.sum(np.square(signal[aligned - 8 : aligned + 9]))
            )
    for room in preregistration["room_codes"]:
        for microphone in microphones:
            signal = waveforms[(room, microphone)]
            baseline = baseline_peaks[microphone]
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

    coefficients = np.array(model["coefficients_normalized"], dtype=float)
    scale = np.array(model["feature_scale"], dtype=float)

    def dataset(
        selected_microphones: tuple[int, ...],
    ) -> tuple[np.ndarray, np.ndarray, list[tuple[str, int, str]]]:
        design: list[np.ndarray] = []
        observed: list[float] = []
        keys: list[tuple[str, int, str]] = []
        for room in preregistration["room_codes"]:
            for microphone in selected_microphones:
                for facet in FACETS:
                    design.append(
                        feature_vector(
                            room,
                            source_positions[microphone],
                            microphone_positions[microphone],
                            facet,
                            model["family"],
                        )
                    )
                    observed.append(
                        observations[(room, microphone, facet)]
                    )
                    keys.append((room, microphone, facet))
        return np.array(design), np.array(observed), keys

    design, observed, keys = dataset(microphones)
    predicted = design / scale @ coefficients
    metrics = evaluate(predicted, observed, keys)
    per_array_metrics = {}
    for definition in preregistration["arrays"]:
        selected = tuple(definition["microphone_ids"])
        array_design, array_observed, array_keys = dataset(selected)
        per_array_metrics[str(definition["sofa_array_id"])] = evaluate(
            array_design / scale @ coefficients,
            array_observed,
            array_keys,
        )
    thresholds = preregistration["confirmatory_metrics"]
    gates = gate_metrics(metrics, thresholds)
    return {
        "schema_version": 1,
        "status": "preserved-dechorate-confirmatory-result",
        "source_preregistration_sha256": preregistration_hash,
        "source_drive_manifest_sha256": drive_manifest_hash,
        "source_inventory_pins_sha256": sha256(inventory_pins_path),
        "source_metadata_sha256": sha256(metadata_path),
        "source_d117_report_sha256": pins["source_d117_report_sha256"],
        "source_annotation_pins_sha256": sha256(annotation_pins_path),
        "source_annotation_sha256": sha256(annotation_path),
        "unblinding_contract": {
            "drive_ids_discovered_after_preregistration": True,
            "byte_pins_fixed_before_sofa_payload_access": True,
            "frozen_model_loaded_without_refit": True,
            "thresholds_loaded_without_change": True,
            "result_must_be_preserved_on_failure": True,
            "new_model_requires_new_version_and_unseen_data": True,
        },
        "channel_audit": {
            "sofa_files": len(entries),
            "channels_per_file": 5,
            "measured_rirs": len(waveforms),
            "unique_waveform_hashes": len(waveform_hashes),
            "waveform_hash_overlap_with_d117": len(
                waveform_hashes & d117_hashes
            ),
            "metadata_coordinate_bindings": coordinate_bindings,
            "microphone_ids": list(microphones),
            "stale_room_descriptions": stale_descriptions,
            "aggregate_file_sha256": aggregate_hash,
            "total_bytes": pins["total_bytes"],
            "direct_shift_median_samples": float(
                np.median(np.abs(direct_shifts))
            ),
            "direct_shift_max_samples": int(
                np.max(np.abs(direct_shifts))
            ),
        },
        "frozen_model": {
            "family": model["family"],
            "regularization": model["regularization"],
            "feature_count": model["feature_count"],
            "frozen_model_sha256": model_hash,
            "per_microphone_coefficients": False,
            "refit_performed": False,
        },
        "thresholds": thresholds,
        "confirmatory_metrics": metrics,
        "diagnostic_per_array_metrics": per_array_metrics,
        "gates": gates,
        "decision": {
            "confirmatory_result_eligible": True,
            "rank_model_admitted_for_further_integration": gates[
                "rank_gate_passed"
            ],
            "amplitude_model_admitted": gates[
                "amplitude_rmse_gate_passed"
            ],
            "production_candidate_model_eligible": False,
            "production_change_required": False,
            "next_action": (
                "integrate-rank-model-behind-research-flag-and-run-"
                "minecraft-end-to-end-perceptual-gates"
                if gates["rank_gate_passed"]
                else "version-new-model-and-preregister-new-unseen-data"
            ),
        },
        "cuda_executed": False,
        "captures_audio": False,
        "physical_endpoint_opened": False,
        "release_calibrated": False,
        "claim_boundary": (
            "The frozen D117 geometry-interaction model is evaluated on "
            "165 previously unseen, byte-pinned source-4 RIRs without "
            "refitting or threshold changes. Passing this dataset alone "
            "does not establish Minecraft integration, perceptual quality, "
            "CUDA execution or release calibration."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preregistration", type=Path, required=True)
    parser.add_argument("--drive-manifest", type=Path, required=True)
    parser.add_argument("--inventory-pins", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--sofa-directory", type=Path, required=True)
    parser.add_argument("--d117-report", type=Path, required=True)
    parser.add_argument("--annotation-pins", type=Path, required=True)
    parser.add_argument("--annotation-directory", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = analyze(
        args.preregistration,
        args.drive_manifest,
        args.inventory_pins,
        args.metadata,
        args.sofa_directory,
        args.d117_report,
        args.annotation_pins,
        args.annotation_directory,
    )
    serialized = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.output_json.is_file():
        if args.output_json.read_text(encoding="utf-8") != serialized:
            raise ValueError("D118 preserved result cannot be overwritten")
    else:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(serialized, encoding="utf-8")
    print(
        json.dumps(
            {
                "status": report["status"],
                "metrics": report["confirmatory_metrics"],
                "gates": report["gates"],
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
