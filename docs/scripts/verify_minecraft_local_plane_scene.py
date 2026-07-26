#!/usr/bin/env python3
"""Independently verify the offline D121b local-plane scene reference."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any


SOURCE = [0.0, 2.0, 0.0]
LISTENER = [0.5, 2.0, 0.2]
PATCHES = [
    [1, 1, 0.0, -3.0, 3.0, -3.0, 3.0],
    [1, -1, 4.0, -3.0, 3.0, -3.0, 3.0],
    [0, 1, -3.0, 0.0, 4.0, -3.0, 3.0],
    [0, -1, 3.0, 0.0, 4.0, -3.0, 3.0],
    [2, 1, -3.0, -3.0, 3.0, 0.0, 4.0],
    [2, -1, 3.0, -3.0, 3.0, 0.0, 4.0],
    [1, 1, -1.0, -3.0, 3.0, -3.0, 3.0],
    [1, 1, -2.0, -3.0, 3.0, -3.0, 3.0],
]
ABSORPTION = [0.03, 0.05, 0.08]
SCATTERING = 0.18
EPSILON = 1.0e-9


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def close(left: float, right: float, tolerance: float = 1.0e-11) -> bool:
    return math.isclose(left, right, rel_tol=tolerance, abs_tol=tolerance)


def distance(left: list[float], right: list[float]) -> float:
    return math.sqrt(sum((a - b) ** 2 for a, b in zip(left, right)))


def image_path(
    source: list[float],
    listener: list[float],
    patch: list[float],
) -> tuple[list[float], float] | None:
    axis, sign, coordinate, first_min, first_max, second_min, second_max = patch
    axis = int(axis)
    if (
        sign * (source[axis] - coordinate) <= EPSILON
        or sign * (listener[axis] - coordinate) <= EPSILON
    ):
        return None
    image = source.copy()
    image[axis] = 2.0 * coordinate - source[axis]
    denominator = listener[axis] - image[axis]
    if abs(denominator) <= 1.0e-15:
        return None
    interpolation = (coordinate - image[axis]) / denominator
    if not 0.0 < interpolation < 1.0:
        return None
    reflection = [
        image[index] + interpolation * (listener[index] - image[index])
        for index in range(3)
    ]
    first = reflection[1] if axis == 0 else reflection[0]
    second = reflection[1] if axis == 2 else reflection[2]
    if not (
        first_min - EPSILON <= first <= first_max + EPSILON
        and second_min - EPSILON <= second <= second_max + EPSILON
    ):
        return None
    return reflection, distance(source, reflection) + distance(listener, reflection)


def gain(direct: float, reflected: float, absorption: float) -> float:
    raw = (max(direct, 0.25) / max(reflected, 0.25)) ** 2
    raw *= (1.0 - absorption) * (1.0 - SCATTERING)
    return 0.0 if raw <= 0.0 else min(1.0, max(1.0e-6, raw))


def segment_intersects_box(
    start: list[float], end: list[float], box: list[float]
) -> bool:
    minimum = [box[index] + EPSILON for index in range(3)]
    maximum = [box[index + 3] - EPSILON for index in range(3)]
    t_minimum, t_maximum = 0.0, 1.0
    for axis in range(3):
        delta = end[axis] - start[axis]
        if abs(delta) <= 1.0e-15:
            if not minimum[axis] <= start[axis] <= maximum[axis]:
                return False
            continue
        first = (minimum[axis] - start[axis]) / delta
        second = (maximum[axis] - start[axis]) / delta
        t_minimum = max(t_minimum, min(first, second))
        t_maximum = min(t_maximum, max(first, second))
        if t_minimum > t_maximum:
            return False
    return True


def verify(report: dict[str, Any], contract_hash: str) -> dict[str, bool]:
    bounded = report["bounded_scene"]
    direct = distance(SOURCE, LISTENER)
    candidates = []
    for index, patch in enumerate(PATCHES):
        solved = image_path(SOURCE, LISTENER, patch)
        if solved is None:
            continue
        reflection, path_length = solved
        energy = [gain(direct, path_length, absorption) for absorption in ABSORPTION]
        candidates.append((-(sum(energy)), path_length, index, reflection, energy))
    candidates.sort()
    expected = candidates[:6]
    selected = bounded["selected"]
    selection_matches = len(selected) == len(expected) == 6
    for actual, expected_path in zip(selected, expected):
        _, path_length, index, reflection, energy = expected_path
        selection_matches &= actual["patch_index"] == index
        selection_matches &= close(actual["path_length_m"], path_length)
        selection_matches &= all(
            close(actual_value, expected_value)
            for actual_value, expected_value in zip(actual["reflection_m"], reflection)
        )
        selection_matches &= all(
            close(actual_value, expected_value)
            for actual_value, expected_value in zip(actual["energy"], energy)
        )

    slab_source = [0.2, 1.5, 0.4]
    slab_listener = [0.8, 1.5, 0.6]
    slab_reflection = [0.5, 0.5000001, 0.5]
    slab_box = [0.0, 0.0, 0.0, 1.0, 0.5, 1.0]
    obstacle = [0.28, 0.85, 0.40, 0.42, 1.20, 0.50]
    self_clear = not segment_intersects_box(
        slab_source, slab_reflection, slab_box
    ) and not segment_intersects_box(slab_listener, slab_reflection, slab_box)
    obstacle_blocks = segment_intersects_box(
        slab_source, slab_reflection, obstacle
    ) or segment_intersects_box(slab_listener, slab_reflection, obstacle)

    ledger = report["energy_ledger"]
    conserved = all(
        close(
            ledger["early_allocated"][band] + ledger["late_residual"][band],
            ledger["environment_budget"][band],
        )
        for band in range(3)
    )
    directions_unit = all(
        close(math.sqrt(sum(value * value for value in cluster["direction"])), 1.0)
        for cluster in bounded["clusters"]
    )
    cache = report["cache_invalidation"]
    return {
        "contract_hash_matches": report["source_contract_sha256"] == contract_hash,
        "status_matches": report["status"]
        == "valid-minecraft-local-plane-scene-reference",
        "independent_six_candidate_selection_matches": selection_matches,
        "independent_exact_shape_self_clear": self_clear
        and not report["exact_shape_visibility"]["self_occluded"],
        "independent_exact_obstacle_blocks": obstacle_blocks
        and report["exact_shape_visibility"]["obstacle_occluded"],
        "cluster_directions_are_unit_vectors": directions_unit,
        "early_late_energy_conserved": conserved,
        "cache_invalidation_exact": cache
        == {
            "initial_match": False,
            "exact_match": True,
            "source_moved_match": False,
            "listener_moved_match": False,
            "generation_changed_match": False,
        },
        "zero_allocation_scene_solve": report["benchmark"][
            "allocation_windows_bytes"
        ]
        == [0, 0, 0, 0, 0],
        "bounded_runtime_gate": report["benchmark"]["p99_ns_per_scene"]
        <= 100_000.0,
        "captures_audio": report["captures_audio"],
        "physical_endpoint_opened": report["physical_endpoint_opened"],
        "cuda_executed": report["cuda_executed"],
        "minecraft_client_started": report["minecraft_client_started"],
        "minecraft_integration_enabled": report["minecraft_integration_enabled"],
        "worker_handoff_measured": report["worker_handoff_measured"],
        "release_calibrated": report["release_calibrated"],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    gates = verify(report, sha256(args.contract))
    positive = [
        "contract_hash_matches",
        "status_matches",
        "independent_six_candidate_selection_matches",
        "independent_exact_shape_self_clear",
        "independent_exact_obstacle_blocks",
        "cluster_directions_are_unit_vectors",
        "early_late_energy_conserved",
        "cache_invalidation_exact",
        "zero_allocation_scene_solve",
        "bounded_runtime_gate",
    ]
    negative = [
        "captures_audio",
        "physical_endpoint_opened",
        "cuda_executed",
        "minecraft_client_started",
        "minecraft_integration_enabled",
        "worker_handoff_measured",
        "release_calibrated",
    ]
    if not all(gates[name] for name in positive) or any(
        gates[name] for name in negative
    ):
        raise SystemExit(f"local-plane scene verification failed: {gates}")
    result = {
        "schema_version": 1,
        "status": "verified-minecraft-local-plane-scene-reference",
        "source_report_sha256": sha256(args.report),
        "source_contract_sha256": sha256(args.contract),
        "gates": gates,
        "metrics": {
            "selected_count": report["bounded_scene"]["selected_count"],
            "cluster_count": report["bounded_scene"]["cluster_count"],
            "allocation_windows_bytes": report["benchmark"][
                "allocation_windows_bytes"
            ],
            "p99_ns_per_scene": report["benchmark"]["p99_ns_per_scene"],
        },
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
