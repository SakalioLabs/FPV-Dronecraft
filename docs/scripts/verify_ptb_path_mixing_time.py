#!/usr/bin/env python3
"""Verify MFP-scaled early/late transport across fixed room geometries."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path


ROOMS = {
    "low-square": (11, 11, 3),
    "cube": (7, 7, 7),
    "corridor": (21, 5, 3),
    "hall": (15, 9, 5),
}
MULTIPLIERS = [0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0]
SOUND_SPEED_MPS = 343.0


def load(path: Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def verify(report: dict[str, object]) -> dict[str, object]:
    if report.get("schema_version") != 1:
        raise ValueError("schema_version must be 1")
    if report.get("status") != "valid-diagnostic":
        raise ValueError("report status is invalid")
    if report.get("ray_count") != 4096:
        raise ValueError("ray_count changed")
    if report.get("maximum_bounces") != 48:
        raise ValueError("maximum_bounces changed")
    if report.get("path_threshold_mfp_multipliers") != MULTIPLIERS:
        raise ValueError("path multiplier matrix changed")

    expected_keys = []
    for room in ROOMS:
        expected_keys.append((room, "configured", None))
        expected_keys.append((room, "fixed-two-hit", None))
        expected_keys.extend(
            (room, "path-scaled", multiplier)
            for multiplier in MULTIPLIERS
        )
    runs = report["runs"]
    actual_keys = [
        (
            row["room"],
            row["mode"],
            row["path_threshold_mfp_multiplier"],
        )
        for row in runs
    ]
    if actual_keys != expected_keys:
        raise ValueError("room/mode matrix changed")

    for row in runs:
        room = row["room"]
        length, width, height = ROOMS[room]
        if row["dimensions_m"] != {
            "length": length,
            "width": width,
            "height": height,
        }:
            raise ValueError(f"{room} dimensions changed")
        if row["normal_material_axis_mismatches"] != 0:
            raise ValueError(f"{room} contains material/normal mismatch")
        if row["hits"] != 4096 * 48:
            raise ValueError(f"{room} hit count changed")
        multiplier = row["path_threshold_mfp_multiplier"]
        if row["mode"] == "path-scaled":
            expected_meters = multiplier * row["analytic_mfp_m"]
            if not math.isclose(
                row["path_threshold_m"], expected_meters, abs_tol=1e-12
            ):
                raise ValueError("path threshold is detached from 4V/S")
            if not math.isclose(
                row["path_threshold_s"],
                expected_meters / SOUND_SPEED_MPS,
                abs_tol=1e-12,
            ):
                raise ValueError("path time is detached from sound speed")
        elif (
            multiplier is not None
            or row["path_threshold_m"] is not None
            or row["path_threshold_s"] is not None
        ):
            raise ValueError("non-path mode contains a path threshold")

    def select(mode: str, multiplier: float | None = None) -> list[dict]:
        return [
            row
            for row in runs
            if row["mode"] == mode
            and (
                mode != "path-scaled"
                or row["path_threshold_mfp_multiplier"] == multiplier
            )
        ]

    def maximum(rows: list[dict], key: str) -> float:
        if len(rows) != len(ROOMS):
            raise ValueError("aggregate does not contain every room")
        return max(row[key] for row in rows)

    configured_error = maximum(
        select("configured"),
        "maximum_relative_error_vs_diffuse_formula",
    )
    fixed_two_error = maximum(
        select("fixed-two-hit"),
        "maximum_relative_error_vs_diffuse_formula",
    )
    diffuse_error = maximum(
        select("path-scaled", 0.0),
        "maximum_relative_error_vs_diffuse_formula",
    )
    two_mfp_rows = select("path-scaled", 2.0)
    two_mfp_error = maximum(
        two_mfp_rows,
        "maximum_relative_error_vs_diffuse_formula",
    )
    two_mfp_fraction_error = maximum(
        two_mfp_rows,
        "maximum_absolute_hit_fraction_error",
    )
    all_path_error = max(
        row["maximum_relative_error_vs_diffuse_formula"]
        for row in runs
        if row["mode"] == "path-scaled"
    )
    return {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "rooms": len(ROOMS),
        "configured_maximum_relative_error": configured_error,
        "fixed_two_hit_maximum_relative_error": fixed_two_error,
        "all_diffuse_maximum_relative_error": diffuse_error,
        "two_mfp_maximum_relative_error": two_mfp_error,
        "two_mfp_maximum_hit_fraction_error": two_mfp_fraction_error,
        "all_path_thresholds_maximum_relative_error": all_path_error,
        "gates": {
            "configured_exposes_late_field_bias": configured_error > 0.10,
            "all_diffuse_within_1_percent": diffuse_error <= 0.01,
            "two_mfp_within_3_percent": two_mfp_error <= 0.03,
            "two_mfp_hit_fraction_within_1_percent": (
                two_mfp_fraction_error <= 0.01
            ),
            "all_tested_path_thresholds_within_3_percent": (
                all_path_error <= 0.03
            ),
            "minecraft_release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Closed shoebox diagnostic only; 2 MFP is not a measured "
            "mixing time and does not enable the runtime feature."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    result = verify(load(args.report))
    payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(payload, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
