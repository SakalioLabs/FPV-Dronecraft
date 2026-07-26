#!/usr/bin/env python3
"""Recompute and verify the post-D118 echo-window failure diagnosis."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import diagnose_dechorate_echo_window_generalization as analyzer


def enforce(report: dict[str, Any]) -> None:
    old = report["old_positions"]
    new = report["new_positions"]
    old_model = old["frozen_model_extrapolation"]
    new_model = new["frozen_model_extrapolation"]
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "valid-dechorate-echo-window-failure-diagnostic"
        or report["source4_rirs"] != 330
        or report["fit_performed"] is not False
        or old["groups"] != 165
        or new["groups"] != 165
    ):
        raise ValueError("D119 diagnostic identity changed")
    if (
        old["window_rank_stability"]["16"]["mean_top4_overlap"] < 0.85
        or new["window_rank_stability"]["16"]["mean_top4_overlap"] < 0.85
        or new["absolute_observation_db"]["maximum"] >= 30.0
        or old_model["predicted_abs_db"]["maximum"] >= 30.0
        or new_model["predicted_abs_db"]["maximum"] <= 100.0
        or new_model["maximum_abs_error_db"] <= 100.0
        or new_model["rows_with_feature_outside_d117_fit_range"]
        <= old_model["rows_with_feature_outside_d117_fit_range"] * 5
    ):
        raise ValueError("D119 extrapolation diagnosis changed")


def verify(
    report: dict[str, Any],
    report_sha: str,
    old_pins_path: Path,
    old_directory: Path,
    new_pins_path: Path,
    new_directory: Path,
    annotation_directory: Path,
    d118_result_path: Path,
    d117_report_path: Path,
) -> dict[str, Any]:
    reconstructed = analyzer.analyze(
        old_pins_path,
        old_directory,
        new_pins_path,
        new_directory,
        annotation_directory,
        d118_result_path,
        d117_report_path,
    )
    if reconstructed != report:
        changed = sorted(
            key
            for key in set(report) | set(reconstructed)
            if report.get(key) != reconstructed.get(key)
        )
        raise ValueError(f"D119 reconstructed report changed: {changed}")
    enforce(report)
    old = report["old_positions"]
    new = report["new_positions"]
    return {
        "schema_version": 1,
        "status": "verified-dechorate-echo-window-failure-diagnostic",
        "source_report_sha256": report_sha,
        "gates": {
            "three_hundred_thirty_source4_rirs_recomputed": True,
            "fit_performed": False,
            "window_target_broadly_stable_at_33_samples": True,
            "measured_new_position_dynamic_range_below_30_db": True,
            "frozen_model_new_position_prediction_exceeds_100_db": True,
            "new_position_extrapolation_exceeds_old_by_fivefold": True,
            "production_model_rejected": True,
            "confirmatory_status_restored": False,
        },
        "metrics": {
            "old_width16_top4_stability": old[
                "window_rank_stability"
            ]["16"]["mean_top4_overlap"],
            "new_width16_top4_stability": new[
                "window_rank_stability"
            ]["16"]["mean_top4_overlap"],
            "old_rows_outside_fit_range": old[
                "frozen_model_extrapolation"
            ]["rows_with_feature_outside_d117_fit_range"],
            "new_rows_outside_fit_range": new[
                "frozen_model_extrapolation"
            ]["rows_with_feature_outside_d117_fit_range"],
            "new_maximum_observed_abs_db": new[
                "absolute_observation_db"
            ]["maximum"],
            "new_maximum_predicted_abs_db": new[
                "frozen_model_extrapolation"
            ]["predicted_abs_db"]["maximum"],
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--old-pins", type=Path, required=True)
    parser.add_argument("--old-directory", type=Path, required=True)
    parser.add_argument("--new-pins", type=Path, required=True)
    parser.add_argument("--new-directory", type=Path, required=True)
    parser.add_argument("--annotation-directory", type=Path, required=True)
    parser.add_argument("--d118-result", type=Path, required=True)
    parser.add_argument("--d117-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    result = verify(
        report,
        analyzer.sha256(args.report),
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
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
