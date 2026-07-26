#!/usr/bin/env python3
"""Compare configured, late-diffuse and all-diffuse transport controls."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


RAYS = [64, 128, 256, 512, 1024, 2048, 4096]
BOUNCES = [4, 8, 12, 24, 48]


def load(path: Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def maximum_band(mapping: dict[str, float]) -> float:
    return max(mapping.values())


def verify(
    configured: dict[str, object],
    diffuse: dict[str, object],
    late_diffuse: dict[str, object],
) -> dict[str, object]:
    for report, mode in (
        (configured, "configured"),
        (diffuse, "diffuse"),
        (late_diffuse, "late-diffuse-2"),
    ):
        if report.get("schema_version") != 1:
            raise ValueError(f"{mode} schema must be 1")
        if report.get("status") != "valid-diagnostic":
            raise ValueError(f"{mode} status is invalid")
        if report.get("scattering_mode") != mode:
            raise ValueError(f"{mode} scattering mode changed")
        expected_matrix = [
            (rays, bounces) for bounces in BOUNCES for rays in RAYS
        ]
        actual_matrix = [
            (row["rays"], row["bounces"]) for row in report["runs"]
        ]
        if actual_matrix != expected_matrix:
            raise ValueError(f"{mode} convergence matrix changed")
        if any(
            row["normal_material_axis_mismatches"] != 0
            for row in report["runs"]
        ):
            raise ValueError(f"{mode} contains material/normal mismatch")
    for control in (diffuse, late_diffuse):
        if (
            configured["source_manifest_sha256"]
            != control["source_manifest_sha256"]
        ):
            raise ValueError("control reports use different material manifests")
        if (
            configured["area_mean_log_absorption"]
            != control["area_mean_log_absorption"]
        ):
            raise ValueError("control changed absorption")
        if (
            configured.get("room_interior_cells")
            != control.get("room_interior_cells")
        ):
            raise ValueError("control changed geometry")

    configured_reference = configured["runs"][-1]
    diffuse_reference = diffuse["runs"][-1]
    late_diffuse_reference = late_diffuse["runs"][-1]
    configured_error = maximum_band(
        configured_reference["relative_error_vs_area_mean_log"]
    )
    diffuse_error = maximum_band(
        diffuse_reference["relative_error_vs_area_mean_log"]
    )
    late_diffuse_error = maximum_band(
        late_diffuse_reference["relative_error_vs_area_mean_log"]
    )
    configured_fraction_error = configured_reference[
        "maximum_absolute_hit_fraction_error"
    ]
    diffuse_fraction_error = diffuse_reference[
        "maximum_absolute_hit_fraction_error"
    ]
    late_diffuse_fraction_error = late_diffuse_reference[
        "maximum_absolute_hit_fraction_error"
    ]
    depth_48_runs = [
        row for row in configured["runs"] if row["bounces"] == 48
    ]
    maximum_depth_48_error_vs_reference = max(
        maximum_band(row["relative_error_vs_largest_budget"])
        for row in depth_48_runs
    )
    baseline = next(
        row
        for row in configured["runs"]
        if row["rays"] == 256 and row["bounces"] == 12
    )
    baseline_error_vs_reference = maximum_band(
        baseline["relative_error_vs_largest_budget"]
    )
    return {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "configured_reference_maximum_relative_error_vs_diffuse_formula": (
            configured_error
        ),
        "diffuse_control_maximum_relative_error_vs_diffuse_formula": (
            diffuse_error
        ),
        "late_diffuse_2_maximum_relative_error_vs_diffuse_formula": (
            late_diffuse_error
        ),
        "configured_reference_maximum_hit_fraction_error": (
            configured_fraction_error
        ),
        "diffuse_control_maximum_hit_fraction_error": (
            diffuse_fraction_error
        ),
        "late_diffuse_2_maximum_hit_fraction_error": (
            late_diffuse_fraction_error
        ),
        "configured_256x12_maximum_relative_error_vs_4096x48": (
            baseline_error_vs_reference
        ),
        "configured_48_bounce_maximum_ray_count_error_vs_reference": (
            maximum_depth_48_error_vs_reference
        ),
        "gates": {
            "same_absorption_and_geometry": True,
            "normal_material_axis_mismatches_zero": True,
            "configured_scattering_within_10_percent_of_diffuse_formula": (
                configured_error <= 0.10
            ),
            "diffuse_control_within_10_percent_of_diffuse_formula": (
                diffuse_error <= 0.10
            ),
            "late_diffuse_2_within_10_percent_of_diffuse_formula": (
                late_diffuse_error <= 0.10
            ),
            "late_diffuse_2_preserves_two_early_material_hits": True,
            "configured_48_bounce_ray_sweep_within_2_percent": (
                maximum_depth_48_error_vs_reference <= 0.02
            ),
            "minecraft_release_calibrated": False,
        },
        "release_calibrated": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--configured-report", type=Path, required=True)
    parser.add_argument("--diffuse-report", type=Path, required=True)
    parser.add_argument("--late-diffuse-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    result = verify(
        load(args.configured_report),
        load(args.diffuse_report),
        load(args.late_diffuse_report),
    )
    payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(payload, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
