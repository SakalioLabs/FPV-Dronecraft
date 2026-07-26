#!/usr/bin/env python3
"""Recompute and verify the D120 exploratory dEchorate stress report."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

from analyze_bounded_first_order_gain_dechorate import analyze


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def enforce(report: dict[str, Any]) -> None:
    all_positions = report["all_positions"]
    old = report["old_positions"]
    new = report["new_positions"]
    support = report["spatial_support"]
    clustering = report["arrival_clustering"]
    bounded = report["bounded_positive_gain_db"]
    gates = report["gates"]
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "valid-d120-bounded-gain-exploratory-stress"
        or report.get("source4_rirs") != 330
        or report.get("fit_performed") is not False
        or all_positions["groups"] != 300
        or all_positions["rows"] != 1800
        or old["groups"] != 150
        or new["groups"] != 150
    ):
        raise ValueError("D120 exploratory matrix or no-fit boundary changed")
    if (
        support["old_supported"] != 15
        or support["old_total"] != 15
        or support["new_supported"] != 0
        or support["new_total"] != 15
        or support["new_fallback"] != 15
    ):
        raise ValueError("D120 spatial support fallback changed")
    if (
        clustering["threshold_samples"] != 16
        or not 1 <= clustering["minimum_clusters_per_position"] <= 6
        or not 1 <= clustering["maximum_clusters_per_position"] <= 6
        or clustering["positions_with_at_least_one_collision"] < 1
    ):
        raise ValueError("D120 collision clustering changed")
    if (
        bounded["contract_minimum"] != -60.0
        or bounded["contract_maximum"] != 0.0
        or bounded["minimum"] < -60.0 - 1.0e-12
        or bounded["maximum"] > 1.0e-12
        or gates["all_330_rirs_recomputed"] is not True
        or gates["fit_performed"] is not False
        or gates["positive_gain_within_contract"] is not True
        or gates["unsupported_new_positions_use_material_fallback"]
        is not True
        or gates["production_candidate_eligible"] is not False
        or gates["release_calibrated"] is not False
    ):
        raise ValueError("D120 bounded-gain or release gate changed")
    # This simple unfitted material prior must not be promoted merely because
    # it is numerically safe. Its all-position rank result is at chance.
    if (
        all_positions["mean_top4_overlap"] >= 0.70
        or new["mean_top4_overlap"] >= 0.70
    ):
        raise ValueError("D120 exploratory interpretation changed")
    if (
        report["captures_audio"] is not False
        or report["physical_endpoint_opened"] is not False
        or report["cuda_executed"] is not False
    ):
        raise ValueError("D120 capture or CUDA boundary changed")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--old-pins", type=Path, required=True)
    parser.add_argument("--old-directory", type=Path, required=True)
    parser.add_argument("--new-pins", type=Path, required=True)
    parser.add_argument("--new-directory", type=Path, required=True)
    parser.add_argument("--annotation-directory", type=Path, required=True)
    parser.add_argument("--d119-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    enforce(report)
    recomputed = analyze(
        args.contract,
        args.old_pins,
        args.old_directory,
        args.new_pins,
        args.new_directory,
        args.annotation_directory,
        args.d119_report,
    )
    enforce(recomputed)
    if report != recomputed:
        raise ValueError("D120 exploratory report differs from recomputation")
    result = {
        "schema_version": 1,
        "status": "verified-d120-bounded-gain-exploratory-stress",
        "source_report_sha256": sha256(args.report),
        "source_contract_sha256": sha256(args.contract),
        "metrics": {
            "source4_rirs": report["source4_rirs"],
            "rows": report["all_positions"]["rows"],
            "mean_top4_overlap": report["all_positions"][
                "mean_top4_overlap"
            ],
            "rmse_db": report["all_positions"]["rmse_db"],
            "new_positions_fallback": report["spatial_support"][
                "new_fallback"
            ],
            "positions_with_collision": report["arrival_clustering"][
                "positions_with_at_least_one_collision"
            ],
        },
        "gates": {
            "full_recomputation_exact": True,
            "fit_performed": False,
            "bounded_output": True,
            "spatial_fallback": True,
            "production_candidate_eligible": False,
            "physical_endpoint_opened": False,
            "captures_audio": False,
            "cuda_executed": False,
            "release_calibrated": False,
        },
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
