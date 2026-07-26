#!/usr/bin/env python3
"""Independently verify D121d coverage, grouping and native shape fields."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any

from verify_local_plane_reflection import extract_union


SOURCE_COUNTS = [1, 4, 8, 16]
LAYOUTS = ["coincident", "clustered", "corridor", "dispersed"]
LISTENER = [0, 2, 0]


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


def admissible(bounds: list[int], config: dict[str, int]) -> bool:
    size_x = bounds[3] - bounds[0] + 1
    size_y = bounds[4] - bounds[1] + 1
    size_z = bounds[5] - bounds[2] + 1
    return (
        size_x <= config["maximum_horizontal_span"]
        and size_z <= config["maximum_horizontal_span"]
        and size_y <= config["maximum_vertical_span"]
        and size_x * size_y * size_z
        <= config["maximum_sampled_block_states"]
    )


def required(source: list[int]) -> list[int]:
    return [
        min(LISTENER[0], source[0]) - 3,
        min(LISTENER[1], source[1]) - 2,
        min(LISTENER[2], source[2]) - 3,
        max(LISTENER[0], source[0]) + 3,
        max(LISTENER[1], source[1]) + 2,
        max(LISTENER[2], source[2]) + 3,
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


def plan(
    sources: list[list[int]], config: dict[str, int]
) -> tuple[list[int], list[list[int]]]:
    order = sorted(
        range(len(sources)),
        key=lambda source: (
            sum(
                (sources[source][axis] - LISTENER[axis]) ** 2
                for axis in range(3)
            ),
            source,
        ),
    )
    groups: list[list[int]] = []
    source_groups = [-1] * len(sources)
    for source_index in order:
        need = required(sources[source_index])
        if not admissible(need, config):
            continue
        need_volume = volume(need)
        selected = -1
        selected_increment: int | None = None
        for group_index, group in enumerate(groups):
            combined = union(group, need)
            if not admissible(combined, config):
                continue
            increment = volume(combined) - volume(group)
            if increment <= need_volume and (
                selected_increment is None or increment < selected_increment
            ):
                selected = group_index
                selected_increment = increment
        if selected < 0:
            if len(groups) >= config["maximum_groups"]:
                continue
            selected = len(groups)
            groups.append(need)
        else:
            groups[selected] = union(groups[selected], need)
        source_groups[source_index] = selected
    return source_groups, groups


def material_boxes(bounds: list[int]) -> list[list[float]]:
    if not bounds[1] <= 0 <= bounds[4]:
        return []
    boxes: list[list[float]] = []
    for x in range(bounds[0], bounds[3] + 1):
        for z in range(bounds[2], bounds[5] + 1):
            selector = (x * 31 + z) % 4
            if selector == 0 or selector == 3:
                boxes.append([x, 0, z, x + 1, 1, z + 1])
            elif selector == 1:
                boxes.append([x, 0, z, x + 1, 0.5, z + 1])
            else:
                boxes.append([x, 0, z, x + 1, 0.5, z + 1])
                boxes.append([x, 0.5, z, x + 0.5, 1, z + 1])
    return boxes


def coordinate_grid_cells(boxes: list[list[float]]) -> int:
    if not boxes:
        return 0
    counts = [
        len({box[axis] for box in boxes} | {box[axis + 3] for box in boxes})
        - 1
        for axis in range(3)
    ]
    return counts[0] * counts[1] * counts[2]


def verify_case(
    case: dict[str, Any], config: dict[str, int]
) -> dict[str, bool]:
    source_groups, group_bounds = plan(case["source_cells"], config)
    actual_bounds = [group["bounds"] for group in case["groups"]]
    group_cells = [volume(bounds) for bounds in group_bounds]
    assigned = sum(group >= 0 for group in source_groups)
    independent = sum(
        volume(required(source))
        for source, group in zip(case["source_cells"], source_groups)
        if group >= 0
    )
    boxes = [material_boxes(bounds) for bounds in group_bounds]
    native_boxes = sum(len(group_boxes) for group_boxes in boxes)
    patches = sum(len(extract_union(group_boxes)) for group_boxes in boxes)
    grid_cells = sum(coordinate_grid_cells(group_boxes) for group_boxes in boxes)
    elapsed = case["elapsed_ns"]
    solve_elapsed = case["solve_batch_elapsed_ns"]
    return {
        "planner_groups_match": source_groups == case["source_groups"]
        and group_bounds == actual_bounds,
        "planner_counts_match": assigned == case["assigned_count"]
        and len(source_groups) - assigned == case["fallback_count"],
        "cell_accounting_matches": group_cells
        == [group["cell_count"] for group in case["groups"]]
        and sum(group_cells) == case["shared_cells"]
        and independent == case["independent_assigned_cells"],
        "sharing_never_exceeds_independent": case["shared_cells"]
        <= case["independent_assigned_cells"],
        "native_shape_boxes_recomputed": native_boxes == case["native_boxes"],
        "union_patches_recomputed": patches == case["exposed_patches"],
        "coordinate_grid_recomputed": grid_cells
        == case["coordinate_grid_cells"],
        "raw_latency_samples_present": len(elapsed) == 64
        and all(value >= 0 for value in elapsed),
        "latency_percentiles_recomputed": math.isclose(
            case["p50_ns"], percentile(elapsed, 0.50)
        )
        and math.isclose(case["p99_ns"], percentile(elapsed, 0.99)),
        "latency_budget_pass": percentile(elapsed, 0.99) <= 10_000_000,
        "solve_samples_present": len(solve_elapsed) == 128
        and all(value >= 0 for value in solve_elapsed),
        "solve_percentile_recomputed": math.isclose(
            case["p99_solve_batch_ns"], percentile(solve_elapsed, 0.99)
        ),
        "solve_budget_pass": percentile(solve_elapsed, 0.99) <= 5_000_000,
        "solve_checksum_matches_assignment": (
            case["solve_checksum"] > 0
            if assigned > 0
            else case["solve_checksum"] == 0
        ),
    }


def verify(report: dict[str, Any], contract_hash: str) -> dict[str, Any]:
    config = report["planner_config"]
    cases = report["cases"]
    case_gates = {
        f"{case['layout']}-{case['source_count']}": verify_case(case, config)
        for case in cases
    }
    coverage = report["coverage_fixture"]
    expected_cases = [
        (layout, source_count)
        for layout in LAYOUTS
        for source_count in SOURCE_COUNTS
    ]
    actual_cases = [
        (case["layout"], case["source_count"])
        for case in cases
    ]
    coincident_16 = next(
        case
        for case in cases
        if case["layout"] == "coincident" and case["source_count"] == 16
    )
    dispersed_16 = next(
        case
        for case in cases
        if case["layout"] == "dispersed" and case["source_count"] == 16
    )
    return {
        "contract_hash_matches": report["source_contract_sha256"]
        == contract_hash,
        "status_matches": report["status"]
        == "valid-minecraft-shared-local-plane-snapshot-reference",
        "native_named_shape_api_executed": report["minecraft_version"]
        == "1.21.11"
        and report["native_shape_api"]
        == "named Shapes plus VoxelShape.optimize().toAabbs()",
        "coverage_unknown_is_incomplete": coverage["unknown"]
        == {"complete": False, "visible": False, "coverage_miss": True},
        "covered_blocker_is_complete_occlusion": coverage["blocked"]
        == {"complete": True, "visible": False, "coverage_miss": False},
        "case_matrix_exact": actual_cases == expected_cases,
        "case_gates": case_gates,
        "all_case_gates_pass": all(
            all(gates.values()) for gates in case_gates.values()
        ),
        "coincident_16_all_assigned": coincident_16["assigned_count"] == 16
        and coincident_16["fallback_count"] == 0,
        "dispersed_16_all_fallback": dispersed_16["assigned_count"] == 0
        and dispersed_16["fallback_count"] == 16,
        "native_shape_construction_measured": report[
            "native_shape_construction_measured"
        ],
        "snapshot_producer_measured": report["snapshot_producer_measured"],
        "captures_audio": report["captures_audio"],
        "physical_endpoint_opened": report["physical_endpoint_opened"],
        "minecraft_client_started": report["minecraft_client_started"],
        "client_level_read": report["client_level_read"],
        "cuda_executed": report["cuda_executed"],
        "minecraft_integration_enabled": report["minecraft_integration_enabled"],
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
        "native_named_shape_api_executed",
        "coverage_unknown_is_incomplete",
        "covered_blocker_is_complete_occlusion",
        "case_matrix_exact",
        "all_case_gates_pass",
        "coincident_16_all_assigned",
        "dispersed_16_all_fallback",
        "native_shape_construction_measured",
    ]
    negative = [
        "snapshot_producer_measured",
        "captures_audio",
        "physical_endpoint_opened",
        "minecraft_client_started",
        "client_level_read",
        "cuda_executed",
        "minecraft_integration_enabled",
        "release_calibrated",
    ]
    if not all(gates[name] for name in positive) or any(
        gates[name] for name in negative
    ):
        raise SystemExit(f"shared snapshot verification failed: {gates}")
    result = {
        "schema_version": 1,
        "status": "verified-minecraft-shared-local-plane-snapshot-reference",
        "source_report_sha256": sha256(args.report),
        "source_contract_sha256": sha256(args.contract),
        "gates": gates,
        "metrics": {
            "cases": len(report["cases"]),
            "coincident_16_shared_cells": next(
                case["shared_cells"]
                for case in report["cases"]
                if case["layout"] == "coincident"
                and case["source_count"] == 16
            ),
            "clustered_16_shared_cells": next(
                case["shared_cells"]
                for case in report["cases"]
                if case["layout"] == "clustered"
                and case["source_count"] == 16
            ),
            "corridor_16_fallback_count": next(
                case["fallback_count"]
                for case in report["cases"]
                if case["layout"] == "corridor"
                and case["source_count"] == 16
            ),
            "maximum_p99_ns": max(
                case["p99_ns"] for case in report["cases"]
            ),
        },
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
