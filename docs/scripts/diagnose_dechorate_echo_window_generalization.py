#!/usr/bin/env python3
"""Diagnose D118 echo-window target stability without fitting a model."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from itertools import combinations
from pathlib import Path
from typing import Any

import h5py
import numpy as np

from analyze_dechorate_cpu_dda_experiment import (
    DISCOVERY,
    FACETS,
    ranked_facets,
)
from analyze_dechorate_multichannel_generalization import feature_vector
from dechorate_common import FS, SPEED_OF_SOUND, WALL_CODE


ROOMS = (
    "000000",
    "000001",
    "000010",
    "000100",
    "001000",
    "010000",
    "011000",
    "011100",
    "011110",
    "011111",
    "020002",
)
OLD_MICROPHONES = tuple(range(10, 25))
NEW_MICROPHONES = (*range(0, 10), *range(25, 30))
HALF_WIDTHS = (8, 16, 32)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def percentile(values: list[float], q: float) -> float:
    return float(np.percentile(np.array(values, dtype=float), q))


def source_four_filename(room: str, array_id: int) -> str:
    first = (array_id - 1) * 5 + 1
    last = array_id * 5
    return (
        f"dEchorate_room{room}_src5_arr{array_id}_"
        f"mics{first}-{last}.sofa"
    )


def analyze(
    old_pins_path: Path,
    old_directory: Path,
    new_pins_path: Path,
    new_directory: Path,
    annotation_directory: Path,
    d118_result_path: Path,
    d117_report_path: Path,
) -> dict[str, Any]:
    old_pins = json.loads(old_pins_path.read_text(encoding="utf-8"))
    new_pins = json.loads(new_pins_path.read_text(encoding="utf-8"))
    d118 = json.loads(d118_result_path.read_text(encoding="utf-8"))
    d117 = json.loads(d117_report_path.read_text(encoding="utf-8"))
    if (
        d118["status"] != "preserved-dechorate-confirmatory-result"
        or d118["gates"]["rank_gate_passed"] is not False
        or d118["gates"]["amplitude_rmse_gate_passed"] is not False
    ):
        raise ValueError("D119 requires the preserved D118 failure")
    annotation_pin = old_pins["annotations"]
    annotation_path = annotation_directory / annotation_pin["filename"]
    if (
        annotation_path.stat().st_size != annotation_pin["bytes"]
        or sha256(annotation_path) != annotation_pin["sha256"]
    ):
        raise ValueError("D119 annotation pin changed")
    with h5py.File(annotation_path, "r") as annotations:
        echo_toa = np.array(annotations["echo_toa"][:, :, 4]) * FS
        echo_wall = np.array(annotations["echo_wall"][:, :, 4])

    old_file_pins = old_pins["files"]
    new_file_pins = new_pins["files"]
    waveforms: dict[tuple[str, int], np.ndarray] = {}
    source_positions: dict[int, np.ndarray] = {}
    receiver_positions: dict[int, np.ndarray] = {}
    for room in ROOMS:
        for array_id in range(1, 7):
            filename = source_four_filename(room, array_id)
            directory = (
                new_directory if array_id in (1, 2, 6) else old_directory
            )
            pin = (
                new_file_pins[filename]
                if array_id in (1, 2, 6)
                else old_file_pins[filename]
            )
            byte_count, file_hash = pin
            path = directory / filename
            if (
                path.stat().st_size != byte_count
                or sha256(path) != file_hash
            ):
                raise ValueError(f"D119 SOFA pin changed: {filename}")
            with h5py.File(path, "r") as sofa:
                source = np.array(sofa["SourcePosition"][0], dtype=float)
                for receiver_index in range(5):
                    microphone = (array_id - 1) * 5 + receiver_index
                    signal = np.array(
                        sofa["Data.IR"][0, receiver_index, :],
                        dtype="<f8",
                    )
                    waveforms[(room, microphone)] = signal
                    if room == "000000":
                        source_positions[microphone] = source
                        receiver_positions[microphone] = np.array(
                            sofa["ReceiverPosition"][
                                receiver_index, :, 0
                            ],
                            dtype=float,
                        )
    if len(waveforms) != 330:
        raise ValueError("D119 expected all 330 source-4 RIRs")

    direct_peaks: dict[tuple[str, int], int] = {}
    for microphone in range(30):
        source = source_positions[microphone]
        receiver = receiver_positions[microphone]
        expected = (
            float(np.linalg.norm(source - receiver))
            / SPEED_OF_SOUND
            * FS
        )
        baseline = waveforms[("000000", microphone)]
        low = max(0, int(expected) - 32)
        high = min(len(baseline), int(expected) + 129)
        reference = low + int(np.argmax(np.abs(baseline[low:high])))
        for room in ROOMS:
            signal = waveforms[(room, microphone)]
            search_low, search_high = reference - 16, reference + 17
            direct_peaks[(room, microphone)] = (
                search_low
                + int(
                    np.argmax(
                        np.abs(signal[search_low:search_high])
                    )
                )
            )

    observations: dict[
        int, dict[tuple[str, int, str], float]
    ] = {width: {} for width in HALF_WIDTHS}
    baseline_energies: dict[int, list[float]] = {
        width: [] for width in HALF_WIDTHS
    }
    collision_separations: dict[int, list[float]] = {
        microphone: [] for microphone in range(30)
    }
    for microphone in range(30):
        walls = [
            WALL_CODE[value.decode()] for value in echo_wall[:, microphone]
        ]
        arrivals = {
            wall: float(echo_toa[walls.index(wall), microphone])
            for wall in walls
        }
        collision_separations[microphone] = sorted(
            abs(arrivals[left] - arrivals[right])
            for left, right in combinations(FACETS, 2)
        )
        direct_annotation = arrivals["direct"]
        for width in HALF_WIDTHS:
            baseline_by_facet: dict[str, float] = {}
            baseline_signal = waveforms[("000000", microphone)]
            baseline_direct = direct_peaks[("000000", microphone)]
            for facet in FACETS:
                center = int(
                    round(
                        arrivals[facet]
                        + baseline_direct
                        - direct_annotation
                    )
                )
                energy = float(
                    np.sum(
                        np.square(
                            baseline_signal[
                                center - width : center + width + 1
                            ]
                        )
                    )
                )
                baseline_by_facet[facet] = energy
                baseline_energies[width].append(energy)
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
                                    center - width : center + width + 1
                                ]
                            )
                        )
                    )
                    observations[width][(room, microphone, facet)] = (
                        10.0
                        * math.log10(
                            max(energy, np.finfo(float).tiny)
                            / max(
                                baseline_by_facet[facet],
                                np.finfo(float).tiny,
                            )
                        )
                    )

    model = d117["model_screen"]["frozen_model"]
    if (
        d117["model_screen"]["frozen_model_sha256"]
        != d118["frozen_model"]["frozen_model_sha256"]
        or model["family"] != "geometry-interaction"
        or model["feature_count"] != 420
    ):
        raise ValueError("D119 frozen model identity changed")
    coefficients = np.array(model["coefficients_normalized"], dtype=float)
    scale = np.array(model["feature_scale"], dtype=float)
    training_design = np.array(
        [
            feature_vector(
                room,
                source_positions[microphone],
                receiver_positions[microphone],
                facet,
                model["family"],
            )
            for room in DISCOVERY
            for microphone in range(10, 25, 2)
            for facet in FACETS
        ]
    )
    training_minimum = np.min(training_design, axis=0)
    training_maximum = np.max(training_design, axis=0)

    def group_diagnostics(microphones: tuple[int, ...]) -> dict[str, Any]:
        current_values = [
            observations[8][(room, microphone, facet)]
            for room in ROOMS
            for microphone in microphones
            for facet in FACETS
        ]
        stability: dict[str, Any] = {}
        for width in (16, 32):
            overlaps = []
            top1_capture = 0
            groups = 0
            for room in ROOMS:
                for microphone in microphones:
                    current = {
                        facet: observations[8][
                            (room, microphone, facet)
                        ]
                        for facet in FACETS
                    }
                    alternative = {
                        facet: observations[width][
                            (room, microphone, facet)
                        ]
                        for facet in FACETS
                    }
                    current_rank = ranked_facets(current)
                    alternative_rank = ranked_facets(alternative)
                    overlaps.append(
                        len(
                            set(current_rank)
                            & set(alternative_rank)
                        )
                        / 4.0
                    )
                    top1_capture += int(
                        current_rank[0] in set(alternative_rank)
                    )
                    groups += 1
            stability[str(width)] = {
                "mean_top4_overlap": float(np.mean(overlaps)),
                "current_top1_captured_by_alternative_top4_rate": (
                    top1_capture / groups
                ),
            }
        separations = [
            value
            for microphone in microphones
            for value in collision_separations[microphone]
        ]
        design = np.array(
            [
                feature_vector(
                    room,
                    source_positions[microphone],
                    receiver_positions[microphone],
                    facet,
                    model["family"],
                )
                for room in ROOMS
                for microphone in microphones
                for facet in FACETS
            ]
        )
        predicted = design / scale @ coefficients
        observed = np.array(current_values)
        errors = predicted - observed
        outside = (design < training_minimum - 1.0e-12) | (
            design > training_maximum + 1.0e-12
        )
        return {
            "microphones": list(microphones),
            "groups": len(ROOMS) * len(microphones),
            "absolute_observation_db": {
                "median": percentile(
                    [abs(value) for value in current_values], 50
                ),
                "p95": percentile(
                    [abs(value) for value in current_values], 95
                ),
                "p99": percentile(
                    [abs(value) for value in current_values], 99
                ),
                "maximum": max(abs(value) for value in current_values),
            },
            "window_rank_stability": stability,
            "frozen_model_extrapolation": {
                "predicted_abs_db": {
                    "median": percentile(
                        np.abs(predicted).tolist(), 50
                    ),
                    "p95": percentile(np.abs(predicted).tolist(), 95),
                    "p99": percentile(np.abs(predicted).tolist(), 99),
                    "maximum": float(np.max(np.abs(predicted))),
                },
                "error_rmse_db": float(
                    np.sqrt(np.mean(np.square(errors)))
                ),
                "maximum_abs_error_db": float(
                    np.max(np.abs(errors))
                ),
                "maximum_abs_normalized_feature": float(
                    np.max(np.abs(design / scale))
                ),
                "rows_with_feature_outside_d117_fit_range": int(
                    np.sum(np.any(outside, axis=1))
                ),
                "rows": int(design.shape[0]),
                "feature_values_outside_d117_fit_range": int(
                    np.sum(outside)
                ),
            },
            "first_order_pair_separation_samples": {
                "minimum": min(separations),
                "p05": percentile(separations, 5),
                "pairs_at_or_below_16": sum(
                    value <= 16 for value in separations
                ),
                "pairs_at_or_below_32": sum(
                    value <= 32 for value in separations
                ),
                "total_pairs": len(separations),
            },
        }

    return {
        "schema_version": 1,
        "status": "valid-dechorate-echo-window-failure-diagnostic",
        "source_d118_result_sha256": sha256(d118_result_path),
        "source_d117_frozen_model_sha256": d117[
            "model_screen"
        ]["frozen_model_sha256"],
        "source_old_pins_sha256": sha256(old_pins_path),
        "source_new_pins_sha256": sha256(new_pins_path),
        "source_annotation_sha256": sha256(annotation_path),
        "source4_rirs": len(waveforms),
        "fit_performed": False,
        "old_positions": group_diagnostics(OLD_MICROPHONES),
        "new_positions": group_diagnostics(NEW_MICROPHONES),
        "baseline_energy_by_half_width": {
            str(width): {
                "minimum": min(values),
                "p01": percentile(values, 1),
                "median": percentile(values, 50),
                "maximum": max(values),
            }
            for width, values in baseline_energies.items()
        },
        "claim_boundary": (
            "Post-D118 exploratory diagnosis over all source-4 RIRs. "
            "It compares target-window stability and annotated arrival "
            "collisions, performs no fit, and cannot restore confirmatory "
            "status or select production parameters."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--old-pins", type=Path, required=True)
    parser.add_argument("--old-directory", type=Path, required=True)
    parser.add_argument("--new-pins", type=Path, required=True)
    parser.add_argument("--new-directory", type=Path, required=True)
    parser.add_argument("--annotation-directory", type=Path, required=True)
    parser.add_argument("--d118-result", type=Path, required=True)
    parser.add_argument("--d117-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = analyze(
        args.old_pins,
        args.old_directory,
        args.new_pins,
        args.new_directory,
        args.annotation_directory,
        args.d118_result,
        args.d117_report,
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
