#!/usr/bin/env python3
"""Exploratorily stress D120 bounded gain on all 330 source-4 RIRs."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any

import h5py
import numpy as np

from analyze_dechorate_cpu_dda_experiment import FACETS, ranked_facets
from dechorate_common import FS, ROOM_SIZE, SPEED_OF_SOUND, WALL_CODE
from diagnose_dechorate_echo_window_generalization import (
    NEW_MICROPHONES,
    OLD_MICROPHONES,
    ROOMS,
    source_four_filename,
)


ABSORPTIVE = ((0.25, 0.55, 0.78), 0.65)
REFLECTIVE = ((0.12, 0.22, 0.35), 0.45)
MINIMUM_DISTANCE_M = 0.25
HALF_WIDTH = 8


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def percentile(values: list[float], q: float) -> float:
    return float(np.percentile(np.asarray(values, dtype=float), q))


def effective_code(room: str) -> str:
    return "010001" if room == "020002" else room


def image_source_length(
    source: np.ndarray,
    receiver: np.ndarray,
    facet: int,
) -> float:
    axis, maximum = (
        (2, False),
        (2, True),
        (0, False),
        (1, False),
        (0, True),
        (1, True),
    )[facet]
    plane = ROOM_SIZE[axis] if maximum else 0.0
    image = source.copy()
    image[axis] = 2.0 * plane - source[axis]
    return float(np.linalg.norm(image - receiver))


def broad_gain(
    direct: float,
    reflected: float,
    material: tuple[tuple[float, float, float], float],
) -> float:
    absorptions, scattering = material
    spreading = (
        max(direct, MINIMUM_DISTANCE_M)
        / max(reflected, MINIMUM_DISTANCE_M)
    ) ** 2
    return sum(
        max(
            1.0e-6,
            min(1.0, spreading * (1.0 - value) * (1.0 - scattering)),
        )
        for value in absorptions
    )


def load_waveforms(
    old_pins_path: Path,
    old_directory: Path,
    new_pins_path: Path,
    new_directory: Path,
) -> tuple[
    dict[tuple[str, int], np.ndarray],
    dict[int, np.ndarray],
    dict[int, np.ndarray],
]:
    old_pins = json.loads(old_pins_path.read_text(encoding="utf-8"))["files"]
    new_pins = json.loads(new_pins_path.read_text(encoding="utf-8"))["files"]
    waveforms: dict[tuple[str, int], np.ndarray] = {}
    sources: dict[int, np.ndarray] = {}
    receivers: dict[int, np.ndarray] = {}
    for room in ROOMS:
        for array_id in range(1, 7):
            filename = source_four_filename(room, array_id)
            use_new = array_id in (1, 2, 6)
            directory = new_directory if use_new else old_directory
            pin = new_pins[filename] if use_new else old_pins[filename]
            path = directory / filename
            if path.stat().st_size != pin[0] or sha256(path) != pin[1]:
                raise ValueError(f"D120 SOFA pin changed: {filename}")
            with h5py.File(path, "r") as sofa:
                source = np.asarray(sofa["SourcePosition"][0], dtype=float)
                for receiver_index in range(5):
                    microphone = (array_id - 1) * 5 + receiver_index
                    waveforms[(room, microphone)] = np.asarray(
                        sofa["Data.IR"][0, receiver_index, :],
                        dtype="<f8",
                    )
                    if room == "000000":
                        sources[microphone] = source
                        receivers[microphone] = np.asarray(
                            sofa["ReceiverPosition"][receiver_index, :, 0],
                            dtype=float,
                        )
    if (
        len(waveforms) != 330
        or len(sources) != 30
        or len(receivers) != 30
    ):
        raise ValueError("D120 expected 330 RIRs and 30 coordinates")
    return waveforms, sources, receivers


def analyze(
    contract_path: Path,
    old_pins_path: Path,
    old_directory: Path,
    new_pins_path: Path,
    new_directory: Path,
    annotation_directory: Path,
    d119_report_path: Path,
) -> dict[str, Any]:
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    d119 = json.loads(d119_report_path.read_text(encoding="utf-8"))
    if (
        contract["status"] != "frozen-before-d120-exploratory-evaluation"
        or d119["status"]
        != "valid-dechorate-echo-window-failure-diagnostic"
        or d119["source4_rirs"] != 330
    ):
        raise ValueError("D120 contract or D119 source changed")
    old_pins = json.loads(old_pins_path.read_text(encoding="utf-8"))
    annotation_pin = old_pins["annotations"]
    annotation_path = annotation_directory / annotation_pin["filename"]
    if (
        annotation_path.stat().st_size != annotation_pin["bytes"]
        or sha256(annotation_path) != annotation_pin["sha256"]
    ):
        raise ValueError("D120 annotation pin changed")
    with h5py.File(annotation_path, "r") as annotations:
        echo_toa = np.asarray(annotations["echo_toa"][:, :, 4]) * FS
        echo_wall = np.asarray(annotations["echo_wall"][:, :, 4])
    waveforms, sources, receivers = load_waveforms(
        old_pins_path,
        old_directory,
        new_pins_path,
        new_directory,
    )

    direct_peaks: dict[tuple[str, int], int] = {}
    for microphone in range(30):
        expected = (
            float(np.linalg.norm(sources[microphone] - receivers[microphone]))
            / SPEED_OF_SOUND
            * FS
        )
        baseline = waveforms[("000000", microphone)]
        low = max(0, int(expected) - 32)
        high = min(len(baseline), int(expected) + 129)
        reference = low + int(np.argmax(np.abs(baseline[low:high])))
        for room in ROOMS:
            signal = waveforms[(room, microphone)]
            direct_peaks[(room, microphone)] = (
                reference
                - 16
                + int(
                    np.argmax(
                        np.abs(
                            signal[reference - 16 : reference + 17]
                        )
                    )
                )
            )

    observations: dict[tuple[str, int, str], float] = {}
    arrival_samples: dict[tuple[int, str], float] = {}
    for microphone in range(30):
        walls = [
            WALL_CODE[value.decode()] for value in echo_wall[:, microphone]
        ]
        arrivals = {
            wall: float(echo_toa[walls.index(wall), microphone])
            for wall in walls
        }
        direct_annotation = arrivals["direct"]
        baseline_direct = direct_peaks[("000000", microphone)]
        baseline_signal = waveforms[("000000", microphone)]
        baseline_energy: dict[str, float] = {}
        for facet in FACETS:
            arrival_samples[(microphone, facet)] = arrivals[facet]
            center = int(
                round(arrivals[facet] + baseline_direct - direct_annotation)
            )
            baseline_energy[facet] = float(
                np.sum(
                    np.square(
                        baseline_signal[
                            center - HALF_WIDTH : center + HALF_WIDTH + 1
                        ]
                    )
                )
            )
        for room in ROOMS:
            signal = waveforms[(room, microphone)]
            direct = direct_peaks[(room, microphone)]
            for facet in FACETS:
                center = int(
                    round(arrivals[facet] + direct - direct_annotation)
                )
                energy = float(
                    np.sum(
                        np.square(
                            signal[
                                center - HALF_WIDTH : center + HALF_WIDTH + 1
                            ]
                        )
                    )
                )
                observations[(room, microphone, facet)] = 10.0 * math.log10(
                    max(energy, np.finfo(float).tiny)
                    / max(baseline_energy[facet], np.finfo(float).tiny)
                )

    predictions: dict[tuple[str, int, str], float] = {}
    positive_gain_db: list[float] = []
    for microphone in range(30):
        source = sources[microphone]
        receiver = receivers[microphone]
        direct = float(np.linalg.norm(source - receiver))
        baseline_gains = []
        for facet_index in range(6):
            reflected = image_source_length(
                source, receiver, facet_index
            )
            baseline = broad_gain(direct, reflected, ABSORPTIVE)
            baseline_gains.append(baseline)
            for material in (ABSORPTIVE, REFLECTIVE):
                gain = broad_gain(direct, reflected, material)
                positive_gain_db.extend(
                    [
                        10.0
                        * math.log10(
                            max(
                                1.0e-6,
                                min(1.0, value / 3.0),
                            )
                        )
                        for value in (gain,)
                    ]
                )
        for room in ROOMS:
            code = effective_code(room)
            for facet_index, facet in enumerate(FACETS):
                material = (
                    REFLECTIVE if code[facet_index] == "1" else ABSORPTIVE
                )
                gain = broad_gain(
                    direct,
                    image_source_length(source, receiver, facet_index),
                    material,
                )
                predictions[(room, microphone, facet)] = (
                    10.0 * math.log10(gain / baseline_gains[facet_index])
                )

    evaluation_rooms = tuple(room for room in ROOMS if room != "000000")

    def metrics(microphones: tuple[int, ...]) -> dict[str, Any]:
        errors: list[float] = []
        overlaps: list[float] = []
        top_one = 0
        groups = 0
        for room in evaluation_rooms:
            for microphone in microphones:
                predicted = {
                    facet: predictions[(room, microphone, facet)]
                    for facet in FACETS
                }
                observed = {
                    facet: observations[(room, microphone, facet)]
                    for facet in FACETS
                }
                predicted_top = set(ranked_facets(predicted))
                observed_rank = ranked_facets(observed)
                overlaps.append(
                    len(predicted_top & set(observed_rank)) / 4.0
                )
                top_one += int(observed_rank[0] in predicted_top)
                errors.extend(
                    predicted[facet] - observed[facet] for facet in FACETS
                )
                groups += 1
        return {
            "microphones": list(microphones),
            "groups": groups,
            "rows": len(errors),
            "rmse_db": math.sqrt(
                sum(value * value for value in errors) / len(errors)
            ),
            "mae_db": sum(abs(value) for value in errors) / len(errors),
            "mean_top4_overlap": sum(overlaps) / len(overlaps),
            "random_expected_top4_overlap": 2.0 / 3.0,
            "top1_capture_rate": top_one / groups,
            "maximum_abs_error_db": max(abs(value) for value in errors),
        }

    listener_min = np.min(
        np.asarray([receivers[index] for index in OLD_MICROPHONES]),
        axis=0,
    )
    listener_max = np.max(
        np.asarray([receivers[index] for index in OLD_MICROPHONES]),
        axis=0,
    )
    support = {
        microphone: bool(
            np.all(receivers[microphone] >= listener_min)
            and np.all(receivers[microphone] <= listener_max)
        )
        for microphone in range(30)
    }

    cluster_counts: list[int] = []
    clustered_pairs = 0
    for microphone in range(30):
        arrivals = sorted(
            arrival_samples[(microphone, facet)] for facet in FACETS
        )
        clusters = 1
        for left, right in zip(arrivals[:-1], arrivals[1:], strict=True):
            if right - left > 16.0:
                clusters += 1
            else:
                clustered_pairs += 1
        cluster_counts.append(clusters)

    return {
        "schema_version": 1,
        "status": "valid-d120-bounded-gain-exploratory-stress",
        "source_contract_sha256": sha256(contract_path),
        "source_d119_report_sha256": sha256(d119_report_path),
        "source_old_pins_sha256": sha256(old_pins_path),
        "source_new_pins_sha256": sha256(new_pins_path),
        "source_annotation_sha256": sha256(annotation_path),
        "source4_rirs": len(waveforms),
        "fit_performed": False,
        "material_mapping_hypothesis": {
            "facet_state_0": "AcousticMaterials.SOFT",
            "facet_state_1": "AcousticMaterials.WOOD",
            "release_calibrated": False,
        },
        "all_positions": metrics(tuple(range(30))),
        "old_positions": metrics(OLD_MICROPHONES),
        "new_positions": metrics(NEW_MICROPHONES),
        "spatial_support": {
            "listener_minimum_m": listener_min.tolist(),
            "listener_maximum_m": listener_max.tolist(),
            "old_supported": sum(support[index] for index in OLD_MICROPHONES),
            "old_total": len(OLD_MICROPHONES),
            "new_supported": sum(support[index] for index in NEW_MICROPHONES),
            "new_total": len(NEW_MICROPHONES),
            "new_fallback": sum(
                not support[index] for index in NEW_MICROPHONES
            ),
            "rule": "closed listener AABB fitted only on microphones 10-24",
        },
        "arrival_clustering": {
            "threshold_samples": 16,
            "minimum_clusters_per_position": min(cluster_counts),
            "median_clusters_per_position": percentile(cluster_counts, 50),
            "maximum_clusters_per_position": max(cluster_counts),
            "positions_with_at_least_one_collision": sum(
                count < 6 for count in cluster_counts
            ),
            "adjacent_links_joined": clustered_pairs,
        },
        "bounded_positive_gain_db": {
            "minimum": min(positive_gain_db),
            "maximum": max(positive_gain_db),
            "contract_minimum": -60.0,
            "contract_maximum": 0.0,
        },
        "gates": {
            "all_330_rirs_recomputed": len(waveforms) == 330,
            "fit_performed": False,
            "positive_gain_within_contract": (
                min(positive_gain_db) >= -60.0 - 1.0e-12
                and max(positive_gain_db) <= 1.0e-12
            ),
            "unsupported_new_positions_use_material_fallback": True,
            "production_candidate_eligible": False,
            "release_calibrated": False,
        },
        "captures_audio": False,
        "physical_endpoint_opened": False,
        "cuda_executed": False,
        "claim_boundary": (
            "Post-D118 exploratory reuse of all 330 source-4 RIRs. The "
            "analysis performs no fit and cannot provide a new holdout, "
            "confirmatory claim, Minecraft release parameter, or CUDA result."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--old-pins", type=Path, required=True)
    parser.add_argument("--old-directory", type=Path, required=True)
    parser.add_argument("--new-pins", type=Path, required=True)
    parser.add_argument("--new-directory", type=Path, required=True)
    parser.add_argument("--annotation-directory", type=Path, required=True)
    parser.add_argument("--d119-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = analyze(
        args.contract,
        args.old_pins,
        args.old_directory,
        args.new_pins,
        args.new_directory,
        args.annotation_directory,
        args.d119_report,
    )
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(report, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
