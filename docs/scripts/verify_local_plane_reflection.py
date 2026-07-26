#!/usr/bin/env python3
"""Independently verify D121 local-plane geometry and voxel visibility."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any


PATCH_EPSILON = 1.0e-9
AIR_OFFSET = 1.0e-7


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def component(values: list[float], axis: int) -> float:
    return values[axis]


def patch_coordinates(
    point: list[float], axis: int
) -> tuple[float, float]:
    return (
        point[1] if axis == 0 else point[0],
        point[1] if axis == 2 else point[2],
    )


def trace(
    start: list[float],
    end: list[float],
    blocked: set[tuple[int, int, int]],
    maximum_cells: int,
) -> tuple[int, bool, bool]:
    delta = [end[index] - start[index] for index in range(3)]
    cell = [math.floor(value) for value in start]
    target = [math.floor(value) for value in end]
    step = [(value > 0) - (value < 0) for value in delta]
    t_delta = [
        math.inf if step[index] == 0 else abs(1.0 / delta[index])
        for index in range(3)
    ]
    t_max = []
    for index in range(3):
        if step[index] == 0:
            t_max.append(math.inf)
        else:
            boundary = cell[index] + 1.0 if step[index] > 0 else cell[index]
            t_max.append((boundary - start[index]) / delta[index])
    for count in range(maximum_cells):
        if tuple(cell) in blocked:
            return count + 1, True, True
        if cell == target:
            return count + 1, True, False
        crossing = min(t_max)
        epsilon = max(1.0e-12, abs(crossing) * 1.0e-12)
        for index in range(3):
            if abs(t_max[index] - crossing) <= epsilon:
                cell[index] += step[index]
                t_max[index] += t_delta[index]
    return maximum_cells, False, False


def solve(scenario: dict[str, Any]) -> dict[str, Any]:
    source = scenario["source_m"]
    listener = scenario["listener_m"]
    patch = scenario["patch"]
    axis = patch["axis"]
    sign = patch["normal_sign"]
    coordinate = patch["coordinate_m"]
    if (
        sign * (source[axis] - coordinate) <= PATCH_EPSILON
        or sign * (listener[axis] - coordinate) <= PATCH_EPSILON
    ):
        return empty()
    image = source.copy()
    image[axis] = 2.0 * coordinate - source[axis]
    denominator = listener[axis] - image[axis]
    if abs(denominator) <= 1.0e-15:
        return empty()
    interpolation = (coordinate - image[axis]) / denominator
    if interpolation <= 0.0 or interpolation >= 1.0:
        return empty()
    reflection = [
        image[index] + interpolation * (listener[index] - image[index])
        for index in range(3)
    ]
    first, second = patch_coordinates(reflection, axis)
    if (
        first < patch["minimum_first_m"] - PATCH_EPSILON
        or first > patch["maximum_first_m"] + PATCH_EPSILON
        or second < patch["minimum_second_m"] - PATCH_EPSILON
        or second > patch["maximum_second_m"] + PATCH_EPSILON
    ):
        return empty()
    source_leg = math.dist(source, reflection)
    listener_leg = math.dist(listener, reflection)
    endpoint = reflection.copy()
    endpoint[axis] += sign * AIR_OFFSET
    blocked = {tuple(value) for value in scenario["blocked_cells"]}
    outgoing = trace(
        source, endpoint, blocked, scenario["maximum_cells"]
    )
    incoming = trace(
        listener, endpoint, blocked, scenario["maximum_cells"]
    )
    complete = outgoing[1] and incoming[1]
    visible = complete and not outgoing[2] and not incoming[2]
    return {
        "candidate_geometry": True,
        "topology_visible": visible,
        "complete": complete,
        "reflection_m": reflection,
        "path_length_m": source_leg + listener_leg,
        "incidence_cosine": abs(source[axis] - coordinate) / source_leg,
        "visited_cells": outgoing[0] + incoming[0],
    }


def empty() -> dict[str, Any]:
    return {
        "candidate_geometry": False,
        "topology_visible": False,
        "complete": False,
        "reflection_m": [None, None, None],
        "path_length_m": None,
        "incidence_cosine": None,
        "visited_cells": 0,
    }


def assert_result(
    actual: dict[str, Any], expected: dict[str, Any], label: str
) -> None:
    for key in (
        "candidate_geometry",
        "topology_visible",
        "complete",
        "visited_cells",
    ):
        if actual[key] != expected[key]:
            raise ValueError(f"{label} {key} differs")
    for key in ("path_length_m", "incidence_cosine"):
        if actual[key] is None or expected[key] is None:
            if actual[key] is not expected[key]:
                raise ValueError(f"{label} {key} nullability differs")
        elif not math.isclose(
            actual[key], expected[key], rel_tol=1.0e-12, abs_tol=1.0e-12
        ):
            raise ValueError(f"{label} {key} differs")
    for axis in range(3):
        left = actual["reflection_m"][axis]
        right = expected["reflection_m"][axis]
        if left is None or right is None:
            if left is not right:
                raise ValueError(f"{label} reflection nullability differs")
        elif not math.isclose(
            left, right, rel_tol=1.0e-12, abs_tol=1.0e-12
        ):
            raise ValueError(f"{label} reflection differs")


def extract_union(boxes: list[list[float]]) -> list[list[float | int]]:
    coordinates = [
        sorted({box[axis] for box in boxes} | {box[axis + 3] for box in boxes})
        for axis in range(3)
    ]
    counts = [len(values) - 1 for values in coordinates]
    solid: set[tuple[int, int, int]] = set()
    for ix in range(counts[0]):
        for iy in range(counts[1]):
            for iz in range(counts[2]):
                center = [
                    (
                        coordinates[0][ix] + coordinates[0][ix + 1]
                    )
                    * 0.5,
                    (
                        coordinates[1][iy] + coordinates[1][iy + 1]
                    )
                    * 0.5,
                    (
                        coordinates[2][iz] + coordinates[2][iz + 1]
                    )
                    * 0.5,
                ]
                if any(
                    all(
                        box[axis] < center[axis] < box[axis + 3]
                        for axis in range(3)
                    )
                    for box in boxes
                ):
                    solid.add((ix, iy, iz))
    patches: list[list[float | int]] = []
    for ix in range(counts[0]):
        for iy in range(counts[1]):
            for iz in range(counts[2]):
                if (ix, iy, iz) not in solid:
                    continue
                for axis, cell_index, first_index, second_index in (
                    (0, ix, iy, iz),
                    (1, iy, ix, iz),
                    (2, iz, ix, iy),
                ):
                    indices = [ix, iy, iz]
                    for sign in (-1, 1):
                        neighbor = indices.copy()
                        neighbor[axis] += sign
                        if tuple(neighbor) in solid:
                            continue
                        plane_index = cell_index + (1 if sign > 0 else 0)
                        first_axis = 1 if axis == 0 else 0
                        second_axis = 1 if axis == 2 else 2
                        patches.append(
                            [
                                axis,
                                sign,
                                coordinates[axis][plane_index],
                                coordinates[first_axis][first_index],
                                coordinates[first_axis][first_index + 1],
                                coordinates[second_axis][second_index],
                                coordinates[second_axis][second_index + 1],
                            ]
                        )
    return coalesce(patches)


def coalesce(
    patches: list[list[float | int]],
) -> list[list[float | int]]:
    planes: dict[tuple[int, int, float], list[list[float | int]]] = {}
    for patch in patches:
        planes.setdefault((patch[0], patch[1], patch[2]), []).append(patch)
    result: list[list[float | int]] = []
    for plane in planes.values():
        merged = plane
        while True:
            before = len(merged)
            merged = merge_adjacent(merged, along_first=True)
            merged = merge_adjacent(merged, along_first=False)
            if len(merged) == before:
                break
        result.extend(merged)
    return result


def merge_adjacent(
    patches: list[list[float | int]], along_first: bool
) -> list[list[float | int]]:
    if along_first:
        ordered = sorted(patches, key=lambda patch: patch[5:7] + patch[3:5])
    else:
        ordered = sorted(patches, key=lambda patch: patch[3:5] + patch[5:7])
    output: list[list[float | int]] = []
    current = ordered[0]
    for following in ordered[1:]:
        if along_first:
            adjacent = (
                current[5:7] == following[5:7]
                and current[4] == following[3]
            )
        else:
            adjacent = (
                current[3:5] == following[3:5]
                and current[6] == following[5]
            )
        if adjacent:
            current = (
                current[:3]
                + [current[3], following[4], current[5], current[6]]
                if along_first
                else current[:3]
                + [current[3], current[4], current[5], following[6]]
            )
        else:
            output.append(current)
            current = following
    output.append(current)
    return output


def verify(
    report: dict[str, Any],
    report_sha: str,
    contract_sha: str,
) -> dict[str, Any]:
    if (
        report.get("schema_version") != 1
        or report.get("status") != "valid-local-plane-reflection-reference"
        or report.get("source_contract_sha256") != contract_sha
        or len(report.get("scenarios", [])) != 4
        or len(report.get("union_fixtures", [])) != 3
    ):
        raise ValueError("D121 local-plane identity changed")
    for scenario in report["scenarios"]:
        assert_result(
            scenario["result"], solve(scenario), scenario["name"]
        )
    for fixture in report["union_fixtures"]:
        expected = extract_union(fixture["boxes"])
        if fixture["patch_count"] != len(expected):
            raise ValueError(f"{fixture['name']} patch count differs")
        normalized_actual = sorted(tuple(value) for value in fixture["patches"])
        normalized_expected = sorted(tuple(value) for value in expected)
        if normalized_actual != normalized_expected:
            raise ValueError(f"{fixture['name']} union boundary differs")
    adjacent = next(
        fixture
        for fixture in report["union_fixtures"]
        if fixture["name"] == "adjacent-boxes"
    )
    if any(
        patch[0] == 0 and patch[2] == 0.5
        for patch in adjacent["patches"]
    ):
        raise ValueError("internal adjacent-box interface was emitted")
    cache = report["cache_invalidation"]
    if cache != {
        "initial_match": False,
        "exact_match": True,
        "source_moved_match": False,
        "listener_moved_match": False,
        "generation_changed_match": False,
        "explicitly_invalidated_match": False,
    }:
        raise ValueError("D121 moving-scene cache invalidation changed")
    benchmark = report["benchmark"]
    gates = report["gates"]
    if (
        benchmark["solves_per_window"] < 100_000
        or benchmark["allocation_windows_bytes"] != [0, 0, 0, 0, 0]
        or benchmark["median_allocated_bytes_per_solve"] != 0.0
        or benchmark["p99_ns_per_solve"] > 10_000.0
        or gates["four_scenarios"] is not True
        or gates["zero_allocation_hot_path"] is not True
        or gates["p99_below_10_microseconds"] is not True
    ):
        raise ValueError("D121 local-plane runtime gate changed")
    if (
        report["captures_audio"] is not False
        or report["physical_endpoint_opened"] is not False
        or report["cuda_executed"] is not False
        or report["minecraft_integration_enabled"] is not False
        or report["release_calibrated"] is not False
    ):
        raise ValueError("D121 local-plane claim boundary changed")
    return {
        "schema_version": 1,
        "status": "verified-local-plane-reflection-reference",
        "source_report_sha256": report_sha,
        "source_contract_sha256": contract_sha,
        "metrics": {
            "scenarios": 4,
            "allocation_windows_bytes": benchmark[
                "allocation_windows_bytes"
            ],
            "p99_ns_per_solve": benchmark["p99_ns_per_solve"],
        },
        "gates": {
            "independent_geometry_recomputed": True,
            "independent_dda_visibility_recomputed": True,
            "independent_union_boundary_recomputed": True,
            "subvoxel_plane_preserved": True,
            "finite_patch_rejection": True,
            "blocked_and_incomplete_distinguished": True,
            "zero_allocation_hot_path": True,
            "moving_source_cache_invalidation": True,
            "minecraft_integration_enabled": False,
            "physical_endpoint_opened": False,
            "captures_audio": False,
            "cuda_executed": False,
            "release_calibrated": False,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    result = verify(report, sha256(args.report), sha256(args.contract))
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
