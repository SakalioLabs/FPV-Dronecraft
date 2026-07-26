#!/usr/bin/env python3
"""Bind the PTB selection report to the Java mixed-surface voxel report."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path


CATEGORY_TO_SURFACE = {
    "stone_dense": "floor",
    "porous_wool": "ceiling",
    "wood_solid_panel": "x_walls",
    "glass_window": "z_walls",
}


def load(path: Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def verify(
    material: dict[str, object], mixture: dict[str, object]
) -> dict[str, object]:
    if material.get("schema_version") != 1:
        raise ValueError("PTB material report schema must be 1")
    if mixture.get("schema_version") != 1:
        raise ValueError("PTB mixture report schema must be 1")
    if material.get("status") != "valid-diagnostic":
        raise ValueError("PTB material report status is invalid")
    if mixture.get("status") != "valid-diagnostic":
        raise ValueError("PTB mixture report status is invalid")
    if mixture["source_manifest_sha256"] != material["manifest_sha256"]:
        raise ValueError("mixture report is detached from PTB manifest")
    cases = {case["id"]: case for case in mixture["cases"]}
    candidate = cases["ptb-research-candidates"]
    current = cases["current-hypotheses"]
    for category, surface in CATEGORY_TO_SURFACE.items():
        category_report = material["categories"][category]
        for band in ("low", "mid", "high"):
            expected_candidate = category_report[
                "runtime_candidate_absorption"
            ][band]
            observed_candidate = candidate["surface_absorption"][surface][
                band
            ]
            if not math.isclose(
                observed_candidate,
                expected_candidate,
                rel_tol=0.0,
                abs_tol=1.0e-12,
            ):
                raise ValueError(
                    f"{category} {band} candidate is detached"
                )
            expected_current = category_report[
                "current_runtime_absorption"
            ][band]
            observed_current = current["surface_absorption"][surface][band]
            if not math.isclose(
                observed_current,
                expected_current,
                rel_tol=0.0,
                abs_tol=1.0e-12,
            ):
                raise ValueError(f"{category} {band} current value detached")

    mean_log_errors = candidate[
        "voxel_relative_error_vs_mean_log"
    ].values()
    arithmetic_errors = candidate[
        "voxel_relative_error_vs_arithmetic"
    ].values()
    closer_to_mean_log = all(
        candidate["voxel_relative_error_vs_mean_log"][band]
        < candidate["voxel_relative_error_vs_arithmetic"][band]
        for band in ("low", "mid", "high")
    )
    maximum_mean_log_error = max(mean_log_errors)
    maximum_arithmetic_error = max(arithmetic_errors)
    return {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "raw_rows_match_manifest": material[
            "raw_selection_csv_verified"
        ],
        "selected_categories": len(material["categories"]),
        "raw_rows": material["raw_corpus_audit"]["rows"],
        "raw_rows_with_coefficient_above_one": material[
            "raw_corpus_audit"
        ]["rows_with_coefficient_above_one"],
        "candidate_probe_maximum_relative_error_vs_arithmetic": (
            maximum_arithmetic_error
        ),
        "candidate_probe_maximum_relative_error_vs_mean_log": (
            maximum_mean_log_error
        ),
        "candidate_probe_closer_to_mean_log": closer_to_mean_log,
        "gates": {
            "reports_bound_to_same_manifest": True,
            "raw_rows_match_manifest": material[
                "raw_selection_csv_verified"
            ],
            "candidate_probe_closer_to_mean_log": closer_to_mean_log,
            "candidate_probe_within_10_percent_of_mean_log": (
                maximum_mean_log_error <= 0.10
            ),
            "minecraft_release_calibrated": False,
        },
        "release_calibrated": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--material-report", type=Path, required=True)
    parser.add_argument("--mixture-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    result = verify(load(args.material_report), load(args.mixture_report))
    payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(payload, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
