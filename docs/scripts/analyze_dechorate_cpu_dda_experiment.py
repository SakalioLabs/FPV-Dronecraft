#!/usr/bin/env python3
"""Analyze D116 Java CPU-DDA paths and controlled dEchorate holdouts."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from collections import defaultdict
from pathlib import Path
from typing import Any

import numpy as np


FACETS = ("floor", "ceiling", "west", "south", "east", "north")
DISCOVERY = (
    "000000",
    "000001",
    "000010",
    "000100",
    "001000",
    "010000",
    "011000",
    "011100",
)
HOLDOUT = ("011110", "011111", "020002")
FEATURES = ("intercept", "ceiling", "west", "south", "east", "north")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def effective_code(room_code: str) -> str:
    return "010001" if room_code == "020002" else room_code


def feature_vector(room_code: str) -> np.ndarray:
    code = effective_code(room_code)
    return np.array(
        [1.0] + [float(code[index] == "1") for index in range(1, 6)],
        dtype=float,
    )


def ranked_facets(values: dict[str, float], limit: int = 4) -> list[str]:
    return sorted(
        FACETS,
        key=lambda facet: (-values[facet], FACETS.index(facet)),
    )[:limit]


def fit_surface_response(
    observations: dict[tuple[str, int, str], float],
    microphones: tuple[int, ...] = (10, 19, 20),
) -> dict[tuple[int, str], np.ndarray]:
    design = np.array([feature_vector(room) for room in DISCOVERY])
    if np.linalg.matrix_rank(design) != len(FEATURES):
        raise ValueError("controlled surface-response design lost full rank")
    coefficients: dict[tuple[int, str], np.ndarray] = {}
    for microphone in microphones:
        for facet in FACETS:
            target = np.array(
                [
                    observations[(room, microphone, facet)]
                    for room in DISCOVERY
                ],
                dtype=float,
            )
            coefficients[(microphone, facet)] = np.linalg.lstsq(
                design,
                target,
                rcond=None,
            )[0]
    return coefficients


def controlled_metrics(
    observations: dict[tuple[str, int, str], float],
) -> tuple[dict[str, Any], dict[str, Any]]:
    coefficients = fit_surface_response(observations)
    coefficient_report: dict[str, Any] = {}
    for microphone in (10, 19, 20):
        coefficient_report[str(microphone)] = {}
        for facet in FACETS:
            vector = coefficients[(microphone, facet)]
            coefficient_report[str(microphone)][facet] = {
                name: float(value)
                for name, value in zip(FEATURES, vector, strict=True)
            }

    split_report: dict[str, Any] = {}
    for split_name, rooms in (
        ("discovery", DISCOVERY),
        ("holdout", HOLDOUT),
    ):
        errors: list[float] = []
        overlaps: list[float] = []
        top_one_captured = 0
        groups = 0
        by_room: dict[str, Any] = {}
        for room in rooms:
            room_errors: list[float] = []
            room_overlaps: list[float] = []
            room_top_one = 0
            for microphone in (10, 19, 20):
                features = feature_vector(room)
                predicted = {
                    facet: float(
                        features @ coefficients[(microphone, facet)]
                    )
                    for facet in FACETS
                }
                observed = {
                    facet: observations[(room, microphone, facet)]
                    for facet in FACETS
                }
                predicted_top = set(ranked_facets(predicted))
                observed_rank = ranked_facets(observed)
                overlap = len(predicted_top & set(observed_rank)) / 4.0
                group_errors = [
                    predicted[facet] - observed[facet] for facet in FACETS
                ]
                captured = observed_rank[0] in predicted_top
                errors.extend(group_errors)
                room_errors.extend(group_errors)
                overlaps.append(overlap)
                room_overlaps.append(overlap)
                top_one_captured += int(captured)
                room_top_one += int(captured)
                groups += 1
            by_room[room] = {
                "groups": 3,
                "rmse_db": math.sqrt(
                    sum(value * value for value in room_errors)
                    / len(room_errors)
                ),
                "mae_db": sum(abs(value) for value in room_errors)
                / len(room_errors),
                "mean_top4_overlap": sum(room_overlaps)
                / len(room_overlaps),
                "top1_captured": room_top_one,
            }
        split_report[split_name] = {
            "groups": groups,
            "rmse_db": math.sqrt(
                sum(value * value for value in errors) / len(errors)
            ),
            "mae_db": sum(abs(value) for value in errors) / len(errors),
            "mean_top4_overlap": sum(overlaps) / len(overlaps),
            "random_expected_top4_overlap": 2.0 / 3.0,
            "top1_captured": top_one_captured,
            "by_room": by_room,
        }
    return coefficient_report, split_report


def heuristic_metrics(
    scenarios: list[dict[str, Any]],
    observations: dict[tuple[str, int, str], float],
) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for split in ("discovery", "holdout"):
        source_four = [
            scenario
            for scenario in scenarios
            if scenario["split"] == split and scenario["source_id"] == 4
        ]
        overlaps: list[float] = []
        top_one_captured = 0
        reflective_total = 0
        reflective_selected = 0
        split_scenarios = [
            scenario for scenario in scenarios if scenario["split"] == split
        ]
        for scenario in split_scenarios:
            selected = set(
                scenario["bounded_candidates"]["selected_facets"]
            )
            code = scenario["effective_facet_code"]
            for index, facet in enumerate(FACETS):
                if code[index] == "1":
                    reflective_total += 1
                    reflective_selected += int(facet in selected)
        for scenario in source_four:
            selected = set(
                scenario["bounded_candidates"]["selected_facets"]
            )
            values = {
                facet: observations[
                    (
                        scenario["room_code"],
                        scenario["microphone_id"],
                        facet,
                    )
                ]
                for facet in FACETS
            }
            observed_rank = ranked_facets(values)
            overlaps.append(len(selected & set(observed_rank)) / 4.0)
            top_one_captured += int(observed_rank[0] in selected)
        result[split] = {
            "source4_groups": len(source_four),
            "mean_top4_overlap": sum(overlaps) / len(overlaps),
            "random_expected_top4_overlap": 2.0 / 3.0,
            "top1_captured": top_one_captured,
            "reflective_facets_selected": reflective_selected,
            "reflective_facets_total": reflective_total,
            "reflective_facet_recall": (
                reflective_selected / reflective_total
                if reflective_total
                else None
            ),
        }
    return result


def analyze(dda_path: Path, measured_path: Path) -> dict[str, Any]:
    dda = json.loads(dda_path.read_text(encoding="utf-8"))
    measured = json.loads(measured_path.read_text(encoding="utf-8"))
    measured_sha = sha256(measured_path)
    if (
        dda.get("schema_version") != 1
        or dda.get("status") != "valid-dechorate-cpu-dda-experiment"
        or dda.get("source_d115_report_sha256") != measured_sha
        or measured.get("status")
        != "valid-dechorate-measured-sofa-analysis"
    ):
        raise ValueError("D116 Java DDA or D115 source binding changed")

    scenarios = dda["scenarios"]
    if len(scenarios) != 66:
        raise ValueError("D116 scenario count changed")
    expected_rooms = set(DISCOVERY) | set(HOLDOUT)
    grouped: defaultdict[str, list[dict[str, Any]]] = defaultdict(list)
    for scenario in scenarios:
        grouped[scenario["room_code"]].append(scenario)
    if set(grouped) != expected_rooms or any(
        len(grouped[room]) != 6 for room in grouped
    ):
        raise ValueError("D116 room/pair matrix changed")

    measured_records = {
        (
            record["room_code"],
            record["source_id"],
            record["microphone_id"],
        ): record
        for record in measured["records"]
    }
    coordinate_bindings = 0
    topology_paths = 0
    maximum_cells = 0
    for scenario in scenarios:
        key = (
            scenario["room_code"],
            scenario["source_id"],
            scenario["microphone_id"],
        )
        source = measured_records[key]
        if not np.allclose(
            scenario["source_position_m"],
            source["source_position_m"],
            atol=1.0e-12,
            rtol=0,
        ) or not np.allclose(
            scenario["microphone_position_m"],
            source["microphone_position_m"],
            atol=1.0e-12,
            rtol=0,
        ):
            raise ValueError("D116 Java coordinate binding changed")
        if (
            scenario["effective_facet_code"]
            != effective_code(scenario["room_code"])
            or scenario["split"]
            != (
                "holdout"
                if scenario["room_code"] in HOLDOUT
                else "discovery"
            )
            or len(
                scenario["bounded_candidates"]["selected_facets"]
            )
            != 4
        ):
            raise ValueError("D116 room semantics or candidate count changed")
        coordinate_bindings += 1
        for model in ("voxel_boundary", "continuous_boundary"):
            paths = [scenario[model]["direct"]] + scenario[model][
                "reflections"
            ]
            if len(paths) != 7 or any(
                path["topology_visible"] is not True for path in paths
            ):
                raise ValueError("D116 Java DDA topology changed")
            topology_paths += len(paths)
            maximum_cells = max(
                maximum_cells,
                *(path["visited_cells"] for path in paths),
            )

    geometry_lookup = {
        (row["microphone_id"], row["facet"]): row
        for row in measured["geometry_comparison"]
    }
    source_four = {
        scenario["microphone_id"]: scenario
        for scenario in grouped["000000"]
        if scenario["source_id"] == 4
    }
    physical_residuals: list[float] = []
    voxel_residuals: list[float] = []
    java_crosscheck: list[float] = []
    for (microphone, facet), reference in geometry_lookup.items():
        scenario = source_four[microphone]
        if facet == "direct":
            voxel_path = scenario["voxel_boundary"]["direct"]
            physical_path = scenario["continuous_boundary"]["direct"]
        else:
            voxel_path = next(
                path
                for path in scenario["voxel_boundary"]["reflections"]
                if path["facet"] == facet
            )
            physical_path = next(
                path
                for path in scenario["continuous_boundary"]["reflections"]
                if path["facet"] == facet
            )
        physical_residuals.append(
            physical_path["arrival_sample"]
            - reference["annotation_sample"]
        )
        voxel_residuals.append(
            voxel_path["arrival_sample"] - reference["annotation_sample"]
        )
        java_crosscheck.extend(
            (
                abs(
                    physical_path["arrival_sample"]
                    - reference["physical_sample"]
                ),
                abs(
                    voxel_path["arrival_sample"]
                    - reference["voxel_sample"]
                ),
            )
        )

    observations = {
        (
            row["room_code"],
            row["microphone_id"],
            row["facet"],
        ): row["energy_gain_db_vs_000000"]
        for row in measured["echo_observations"]
    }
    coefficients, controlled = controlled_metrics(observations)
    heuristic = heuristic_metrics(scenarios, observations)
    benchmark = dda["benchmark"]
    latency_gate = benchmark["p99_ns_per_scenario"] <= 50_000.0
    allocation_free = (
        benchmark["allocation_supported"]
        and benchmark["p95_allocated_bytes_per_scenario"] == 0.0
    )
    physical_max = max(abs(value) for value in physical_residuals)
    voxel_max = max(abs(value) for value in voxel_residuals)
    heuristic_holdout = heuristic["holdout"]["mean_top4_overlap"]
    controlled_holdout = controlled["holdout"]["mean_top4_overlap"]
    return {
        "schema_version": 1,
        "status": "valid-dechorate-cpu-dda-analysis",
        "source_dda_report_sha256": sha256(dda_path),
        "source_d115_report_sha256": measured_sha,
        "coordinate_binding": {
            "verified_scenarios": coordinate_bindings,
            "room_codes": len(grouped),
            "pairs_per_room": 6,
        },
        "java_cpu_dda": {
            "paths_recomputed": topology_paths,
            "all_topology_visible": True,
            "maximum_visited_cells_per_path": maximum_cells,
            "geometry_cache_hits": dda["geometry_cache"]["hits"],
            "geometry_cache_misses": dda["geometry_cache"]["misses"],
            "geometry_cache_hit_rate": dda["geometry_cache"]["hit_rate"],
        },
        "geometry_timing": {
            "annotation_paths": len(physical_residuals),
            "physical_absolute_residual_median_samples": float(
                np.median(np.abs(physical_residuals))
            ),
            "physical_absolute_residual_max_samples": physical_max,
            "voxel_absolute_residual_median_samples": float(
                np.median(np.abs(voxel_residuals))
            ),
            "voxel_absolute_residual_max_samples": voxel_max,
            "java_vs_d115_max_absolute_difference_samples": max(
                java_crosscheck
            ),
        },
        "unfitted_candidate_heuristic": heuristic,
        "controlled_surface_response": {
            "fit_scope": (
                "per-microphone/per-observed-facet linear coupling from "
                "five switchable facet states"
            ),
            "features": list(FEATURES),
            "discovery_rooms": list(DISCOVERY),
            "holdout_rooms": list(HOLDOUT),
            "coefficients_db": coefficients,
            "metrics": controlled,
            "production_generalizable": False,
        },
        "runtime_benchmark": benchmark,
        "decision": {
            "continuous_boundary_timing_admitted": physical_max <= 5.0,
            "one_metre_boundary_timing_rejected": voxel_max >= 50.0,
            "real_java_cpu_dda_topology_admitted": True,
            "latency_research_gate_passed": latency_gate,
            "allocation_free_gate_passed": allocation_free,
            "unfitted_material_distance_ranking_admitted": (
                heuristic_holdout
                > heuristic["holdout"]["random_expected_top4_overlap"]
                + 0.05
            ),
            "controlled_surface_response_holdout_rank_admitted": (
                controlled_holdout >= 0.95
                and controlled["holdout"]["top1_captured"] == 9
            ),
            "controlled_coefficients_production_eligible": False,
            "cuda_executed": False,
            "production_change_required": False,
            "next_action": (
                "expand-to-fifteen-receivers-and-fit-geometry-generalized-"
                "candidate-response"
            ),
        },
        "captures_audio": False,
        "physical_endpoint_opened": False,
        "release_calibrated": False,
        "claim_boundary": (
            "Hash-bound analysis of Java CPU DDA topology, image-source "
            "timing and a fixed-geometry discovery/holdout surface-response "
            "screen. Runtime timing is a local microbenchmark; coefficients "
            "are not position-generalized, no CUDA kernel ran, and no "
            "physical audio endpoint was opened."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dda-report", type=Path, required=True)
    parser.add_argument("--measured-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = analyze(args.dda_report, args.measured_report)
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(
        json.dumps(
            {
                "status": report["status"],
                "paths": report["java_cpu_dda"]["paths_recomputed"],
                "physical_max_samples": report["geometry_timing"][
                    "physical_absolute_residual_max_samples"
                ],
                "voxel_max_samples": report["geometry_timing"][
                    "voxel_absolute_residual_max_samples"
                ],
                "holdout_top4": report["controlled_surface_response"][
                    "metrics"
                ]["holdout"]["mean_top4_overlap"],
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
