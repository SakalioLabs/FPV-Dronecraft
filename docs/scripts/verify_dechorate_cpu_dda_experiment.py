#!/usr/bin/env python3
"""Reconstruct and independently gate the D116 Java CPU-DDA analysis."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

import analyze_dechorate_cpu_dda_experiment as analyzer


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def enforce(report: dict[str, Any]) -> None:
    geometry = report["geometry_timing"]
    dda = report["java_cpu_dda"]
    heuristic = report["unfitted_candidate_heuristic"]
    controlled = report["controlled_surface_response"]
    benchmark = report["runtime_benchmark"]
    decision = report["decision"]
    if (
        report.get("schema_version") != 1
        or report.get("status") != "valid-dechorate-cpu-dda-analysis"
        or report["coordinate_binding"]["verified_scenarios"] != 66
        or dda["paths_recomputed"] != 924
        or dda["all_topology_visible"] is not True
        or geometry["annotation_paths"] != 21
        or geometry["physical_absolute_residual_max_samples"] > 5.0
        or geometry["voxel_absolute_residual_max_samples"] < 50.0
        or geometry[
            "java_vs_d115_max_absolute_difference_samples"
        ] > 1.0e-9
    ):
        raise ValueError("D116 CPU-DDA corpus or timing gate changed")
    if (
        heuristic["holdout"]["mean_top4_overlap"]
        > heuristic["holdout"]["random_expected_top4_overlap"] + 0.05
        or controlled["metrics"]["holdout"]["mean_top4_overlap"] < 0.95
        or controlled["metrics"]["holdout"]["top1_captured"] != 9
        or controlled["production_generalizable"] is not False
    ):
        raise ValueError("D116 candidate or controlled holdout gate changed")
    if (
        benchmark["p99_ns_per_scenario"] > 50_000.0
        or benchmark["allocation_supported"] is not True
        or benchmark["p95_allocated_bytes_per_scenario"] <= 0.0
        or decision["continuous_boundary_timing_admitted"] is not True
        or decision["one_metre_boundary_timing_rejected"] is not True
        or decision["real_java_cpu_dda_topology_admitted"] is not True
        or decision["latency_research_gate_passed"] is not True
        or decision["allocation_free_gate_passed"] is not False
        or decision[
            "unfitted_material_distance_ranking_admitted"
        ] is not False
        or decision[
            "controlled_surface_response_holdout_rank_admitted"
        ] is not True
        or decision["controlled_coefficients_production_eligible"] is not False
        or decision["cuda_executed"] is not False
        or decision["production_change_required"] is not False
        or report["captures_audio"] is not False
        or report["physical_endpoint_opened"] is not False
        or report["release_calibrated"] is not False
    ):
        raise ValueError("D116 runtime, decision or claim boundary changed")


def verify(
    report: dict[str, Any],
    report_sha: str,
    dda_path: Path,
    measured_path: Path,
) -> dict[str, Any]:
    if (
        report.get("source_dda_report_sha256") != sha256(dda_path)
        or report.get("source_d115_report_sha256") != sha256(measured_path)
    ):
        raise ValueError("D116 source report binding changed")
    reconstructed = analyzer.analyze(dda_path, measured_path)
    if reconstructed != report:
        changed = sorted(
            key
            for key in set(report) | set(reconstructed)
            if report.get(key) != reconstructed.get(key)
        )
        raise ValueError(f"D116 reconstructed report changed: {changed}")
    enforce(report)
    geometry = report["geometry_timing"]
    controlled = report["controlled_surface_response"]["metrics"]
    benchmark = report["runtime_benchmark"]
    return {
        "schema_version": 1,
        "status": "verified-dechorate-cpu-dda-analysis",
        "source_report_sha256": report_sha,
        "gates": {
            "sixty_six_coordinates_bound_to_measured_sofa": True,
            "nine_hundred_twenty_four_java_dda_paths_recomputed": True,
            "continuous_boundary_within_five_samples": True,
            "one_metre_boundary_error_exceeds_fifty_samples": True,
            "unfitted_candidate_ranking_rejected": True,
            "controlled_holdout_rank_passed": True,
            "controlled_coefficients_not_production_generalized": True,
            "local_latency_research_gate_passed": True,
            "allocation_free_gate_failed": True,
            "cuda_executed": False,
            "physical_endpoint_opened": False,
            "captures_audio": False,
            "release_calibrated": False,
        },
        "metrics": {
            "java_dda_paths": report["java_cpu_dda"]["paths_recomputed"],
            "physical_max_residual_samples": geometry[
                "physical_absolute_residual_max_samples"
            ],
            "voxel_max_residual_samples": geometry[
                "voxel_absolute_residual_max_samples"
            ],
            "controlled_holdout_rmse_db": controlled["holdout"]["rmse_db"],
            "controlled_holdout_top4_overlap": controlled["holdout"][
                "mean_top4_overlap"
            ],
            "p99_ns_per_scenario": benchmark["p99_ns_per_scenario"],
            "p95_allocated_bytes_per_scenario": benchmark[
                "p95_allocated_bytes_per_scenario"
            ],
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--dda-report", type=Path, required=True)
    parser.add_argument("--measured-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    result = verify(
        report,
        sha256(args.report),
        args.dda_report,
        args.measured_report,
    )
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
