#!/usr/bin/env python3
"""Independently verify the D121e FrozenBlockView producer evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any

from verify_local_plane_reflection import coalesce


SOURCE_COUNTS = [1, 4, 8, 16]
LISTENER = [0, 2, 0]
PLANNER = {
    "horizontal_radius": 2,
    "vertical_radius": 1,
    "halo": 1,
    "maximum_groups": 4,
    "maximum_sampled_block_states": 4096,
    "maximum_horizontal_span": 32,
    "maximum_vertical_span": 16,
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def percentile(values: list[int], quantile: float) -> int:
    ordered = sorted(values)
    return ordered[math.ceil(quantile * len(ordered)) - 1]


def volume(bounds: list[int]) -> int:
    return (
        (bounds[3] - bounds[0] + 1)
        * (bounds[4] - bounds[1] + 1)
        * (bounds[5] - bounds[2] + 1)
    )


def required(source: list[int]) -> list[int]:
    horizontal = PLANNER["horizontal_radius"] + PLANNER["halo"]
    vertical = PLANNER["vertical_radius"] + PLANNER["halo"]
    return [
        min(LISTENER[0], source[0]) - horizontal,
        min(LISTENER[1], source[1]) - vertical,
        min(LISTENER[2], source[2]) - horizontal,
        max(LISTENER[0], source[0]) + horizontal,
        max(LISTENER[1], source[1]) + vertical,
        max(LISTENER[2], source[2]) + horizontal,
    ]


def union(left: list[int], right: list[int]) -> list[int]:
    return [
        min(left[0], right[0]),
        min(left[1], right[1]),
        min(left[2], right[2]),
        max(left[3], right[3]),
        max(left[4], right[4]),
        max(left[5], right[5]),
    ]


def sources(source_count: int) -> list[list[int]]:
    return [
        [source % 5 - 2, 2, source // 5 - 1]
        for source in range(source_count)
    ]


def shared_plan(source_count: int) -> tuple[int, int, int, int]:
    bounds: list[list[int]] = []
    assigned = 0
    for source in sources(source_count):
        need = required(source)
        selected = -1
        selected_increment: int | None = None
        for index, group in enumerate(bounds):
            combined = union(group, need)
            increment = volume(combined) - volume(group)
            if increment <= volume(need) and (
                selected_increment is None
                or increment < selected_increment
            ):
                selected = index
                selected_increment = increment
        if selected < 0:
            if len(bounds) >= PLANNER["maximum_groups"]:
                continue
            bounds.append(need)
        else:
            bounds[selected] = union(bounds[selected], need)
        assigned += 1
    return len(bounds), assigned, source_count - assigned, sum(
        volume(group) for group in bounds
    )


def procedural_boxes(bounds: list[int]) -> list[list[float]]:
    boxes: list[list[float]] = []
    if not bounds[1] <= 0 <= bounds[4]:
        return boxes
    for x in range(bounds[0], bounds[3] + 1):
        for z in range(bounds[2], bounds[5] + 1):
            shape = (x * 31 + z) % 4
            if shape in (0, 3):
                boxes.append([x, 0, z, x + 1, 1, z + 1])
            elif shape == 1:
                boxes.append([x, 0, z, x + 1, 0.5, z + 1])
            else:
                boxes.append([x, 0, z, x + 1, 0.5, z + 1])
                boxes.append([x, 0.5, z, x + 0.5, 1, z + 1])
    return boxes


def inner_owner_patches(
    boxes: list[list[float]], inner: list[int]
) -> list[list[float | int]]:
    """Rebuild raw union faces, filter owners, then coalesce like Java."""
    coordinates = [
        sorted(
            {box[axis] for box in boxes}
            | {box[axis + 3] for box in boxes}
        )
        for axis in range(3)
    ]
    counts = [len(values) - 1 for values in coordinates]
    solid: set[tuple[int, int, int]] = set()
    for ix in range(counts[0]):
        for iy in range(counts[1]):
            for iz in range(counts[2]):
                center = [
                    (
                        coordinates[axis][(ix, iy, iz)[axis]]
                        + coordinates[axis][(ix, iy, iz)[axis] + 1]
                    )
                    * 0.5
                    for axis in range(3)
                ]
                if any(
                    all(
                        box[axis] < center[axis] < box[axis + 3]
                        for axis in range(3)
                    )
                    for box in boxes
                ):
                    solid.add((ix, iy, iz))
    raw: list[list[float | int]] = []
    for ix, iy, iz in sorted(solid):
        indices = [ix, iy, iz]
        for axis, cell_index, first_index, second_index in (
            (0, ix, iy, iz),
            (1, iy, ix, iz),
            (2, iz, ix, iy),
        ):
            for sign in (-1, 1):
                neighbor = indices.copy()
                neighbor[axis] += sign
                if tuple(neighbor) in solid:
                    continue
                plane_index = cell_index + (1 if sign > 0 else 0)
                first_axis = 1 if axis == 0 else 0
                second_axis = 1 if axis == 2 else 2
                patch: list[float | int] = [
                    axis,
                    sign,
                    coordinates[axis][plane_index],
                    coordinates[first_axis][first_index],
                    coordinates[first_axis][first_index + 1],
                    coordinates[second_axis][second_index],
                    coordinates[second_axis][second_index + 1],
                ]
                first = (float(patch[3]) + float(patch[4])) * 0.5
                second = (float(patch[5]) + float(patch[6])) * 0.5
                owner = [0.0, 0.0, 0.0]
                owner[axis] = float(patch[2]) - sign * 1.0e-6
                owner[first_axis] = first
                owner[second_axis] = second
                owner_cell = [math.floor(value) for value in owner]
                if all(
                    inner[axis_index]
                    <= owner_cell[axis_index]
                    <= inner[axis_index + 3]
                    for axis_index in range(3)
                ):
                    raw.append(patch)
    return coalesce(raw)


def fixture_gates(fixtures: list[dict[str, Any]]) -> dict[str, bool]:
    by_name = {fixture["name"]: fixture for fixture in fixtures}
    complete = by_name["complete"]
    unloaded = by_name["unloaded-cell"]
    changed = by_name["generation-change"]
    budget = by_name["union-grid-budget"]
    escaping = by_name["escaping-box"]
    boxes = procedural_boxes([-1, -1, -1, 1, 1, 1])
    inner_patches = inner_owner_patches(boxes, [0, 0, 0, 0, 0, 0])
    return {
        "fixture_names_exact": list(by_name) == [
            "complete",
            "unloaded-cell",
            "generation-change",
            "union-grid-budget",
            "escaping-box",
        ],
        "complete_counts_recomputed": complete["sampled_cells"] == 27
        and complete["empty_cells"] == 18
        and complete["unloaded_cells"] == 0
        and complete["material_box_count"] == len(boxes) == 11
        and complete["patch_count"] == len(inner_patches) == 4,
        "complete_publishes_geometry": complete["complete"]
        and complete["generation_stable"]
        and not complete["union_grid_budget_exceeded"]
        and complete["published_boxes"] == len(boxes)
        and complete["published_patches"] == len(inner_patches),
        "unloaded_is_empty_incomplete": not unloaded["complete"]
        and unloaded["unloaded_cells"] == 1
        and unloaded["sampled_cells"] == 26
        and unloaded["published_boxes"] == 0
        and unloaded["published_patches"] == 0,
        "generation_change_is_empty_incomplete": changed[
            "generation_before"
        ]
        == 1
        and changed["generation_after"] == 2
        and not changed["generation_stable"]
        and not changed["complete"]
        and changed["published_boxes"] == 0
        and changed["published_patches"] == 0,
        "union_budget_is_empty_incomplete": budget[
            "material_box_count"
        ]
        == 65
        and budget["union_grid_budget_exceeded"]
        and not budget["complete"]
        and budget["published_boxes"] == 0
        and budget["published_patches"] == 0,
        "escaping_box_rejected": escaping == {
            "name": "escaping-box",
            "rejected": True,
        },
    }


def benchmark_gates(
    benchmarks: list[dict[str, Any]],
) -> dict[str, dict[str, bool]]:
    gates: dict[str, dict[str, bool]] = {}
    for benchmark in benchmarks:
        source_count = benchmark["source_count"]
        groups, assigned, fallback, shared_cells = shared_plan(source_count)
        elapsed = benchmark["elapsed_ns"]
        gates[str(source_count)] = {
            "planner_recomputed": (
                groups,
                assigned,
                fallback,
                shared_cells,
            )
            == (
                benchmark["group_count"],
                benchmark["assigned_count"],
                benchmark["fallback_count"],
                benchmark["shared_cells"],
            ),
            "raw_samples_present": len(elapsed) == 128
            and all(value >= 0 for value in elapsed),
            "percentiles_recomputed": math.isclose(
                benchmark["p50_ns"], percentile(elapsed, 0.50)
            )
            and math.isclose(
                benchmark["p99_ns"], percentile(elapsed, 0.99)
            ),
            "p99_below_10_ms": percentile(elapsed, 0.99) <= 10_000_000,
            "allocation_reported": math.isfinite(
                benchmark["allocated_bytes_per_batch"]
            )
            and benchmark["allocated_bytes_per_batch"] >= 0,
            "checksum_positive": benchmark["checksum"] > 0,
        }
    return gates


def verify(report: dict[str, Any], contract_hash: str) -> dict[str, Any]:
    fixtures = fixture_gates(report["fixtures"])
    benchmarks = benchmark_gates(report["benchmarks"])
    return {
        "contract_hash_matches": report["source_contract_sha256"]
        == contract_hash,
        "status_matches": report["status"]
        == "valid-frozen-block-view-snapshot-producer-reference",
        "source_counts_exact": [
            benchmark["source_count"] for benchmark in report["benchmarks"]
        ]
        == SOURCE_COUNTS,
        "fixture_gates": fixtures,
        "all_fixture_gates_pass": all(fixtures.values()),
        "benchmark_gates": benchmarks,
        "all_benchmark_gates_pass": all(
            all(case.values()) for case in benchmarks.values()
        ),
        "frozen_block_view_producer_measured": report[
            "frozen_block_view_producer_measured"
        ],
        "client_level_snapshot_producer_measured": report[
            "client_level_snapshot_producer_measured"
        ],
        "snapshot_producer_measured": report["snapshot_producer_measured"],
        "captures_audio": report["captures_audio"],
        "physical_endpoint_opened": report["physical_endpoint_opened"],
        "minecraft_client_started": report["minecraft_client_started"],
        "client_level_read": report["client_level_read"],
        "cuda_executed": report["cuda_executed"],
        "minecraft_integration_enabled": report[
            "minecraft_integration_enabled"
        ],
        "live_early_renderer_enabled": report[
            "live_early_renderer_enabled"
        ],
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
        "source_counts_exact",
        "all_fixture_gates_pass",
        "all_benchmark_gates_pass",
        "frozen_block_view_producer_measured",
    ]
    negative = [
        "client_level_snapshot_producer_measured",
        "snapshot_producer_measured",
        "captures_audio",
        "physical_endpoint_opened",
        "minecraft_client_started",
        "client_level_read",
        "cuda_executed",
        "minecraft_integration_enabled",
        "live_early_renderer_enabled",
        "release_calibrated",
    ]
    if not all(gates[name] for name in positive) or any(
        gates[name] for name in negative
    ):
        raise SystemExit(f"frozen producer verification failed: {gates}")
    result = {
        "schema_version": 1,
        "status": "verified-frozen-block-view-snapshot-producer-reference",
        "source_report_sha256": sha256(args.report),
        "source_contract_sha256": sha256(args.contract),
        "gates": gates,
        "metrics": {
            "fixture_count": len(report["fixtures"]),
            "maximum_p99_ns": max(
                benchmark["p99_ns"]
                for benchmark in report["benchmarks"]
            ),
            "source_16_shared_cells": report["benchmarks"][-1][
                "shared_cells"
            ],
            "source_16_allocated_bytes_per_batch": report[
                "benchmarks"
            ][-1]["allocated_bytes_per_batch"],
        },
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
