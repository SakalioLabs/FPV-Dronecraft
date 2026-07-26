#!/usr/bin/env python3
"""Verify nested doorway escape and reverb-control monotonicity."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path


APERTURES = [0, 1, 2, 3, 9, 12, 20, 28, 55]
BANDS = ("low", "mid", "high")
SOUND_SPEED_MPS = 343.0


def load(path: Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def strictly_increasing(values: list[float]) -> bool:
    return all(after > before for before, after in zip(values, values[1:]))


def nonincreasing(values: list[float]) -> bool:
    return all(after <= before for before, after in zip(values, values[1:]))


def nondecreasing(values: list[float]) -> bool:
    return all(after >= before for before, after in zip(values, values[1:]))


def verify(report: dict[str, object]) -> dict[str, object]:
    if report.get("schema_version") != 1:
        raise ValueError("schema_version must be 1")
    if report.get("status") != "valid-diagnostic":
        raise ValueError("report status is invalid")
    if report.get("room_interior_cells") != {
        "length": 11,
        "width": 11,
        "height": 5,
    }:
        raise ValueError("room geometry changed")
    if report.get("ray_count") != 4096:
        raise ValueError("ray_count changed")
    if report.get("maximum_bounces") != 48:
        raise ValueError("maximum_bounces changed")
    volume = 11 * 11 * 5
    surface = 2 * (11 * 11 + 11 * 5 + 11 * 5)
    expected_threshold = 2.0 * 4.0 * volume / surface
    if not math.isclose(
        report["late_diffuse_path_threshold_m"],
        expected_threshold,
        abs_tol=1e-12,
    ):
        raise ValueError("late diffuse threshold detached from 2 MFP")
    if not math.isclose(
        report["late_diffuse_path_threshold_s"],
        expected_threshold / SOUND_SPEED_MPS,
        abs_tol=1e-12,
    ):
        raise ValueError("late diffuse time detached from sound speed")

    steps = report["steps"]
    if [row["aperture_cells"] for row in steps] != APERTURES:
        raise ValueError("nested aperture matrix changed")
    openness = [row["openness"] for row in steps]
    escaped = [row["escaped_rays"] for row in steps]
    wet_gain = [row["wet_gain"] for row in steps]
    if not strictly_increasing(openness):
        raise ValueError("openness is not strictly increasing")
    if not strictly_increasing(escaped):
        raise ValueError("escaped ray count is not strictly increasing")

    rt60_monotonic = all(
        nonincreasing([row["rt60_s"][band] for row in steps])
        for band in BANDS
    )
    edt_monotonic = all(
        nonincreasing([row["edt_s"][band] for row in steps])
        for band in BANDS
    )
    drr_monotonic = all(
        nondecreasing([row["drr_db"][band] for row in steps])
        for band in BANDS
    )
    wet_monotonic = nonincreasing(wet_gain)
    if not all(
        (rt60_monotonic, edt_monotonic, drr_monotonic, wet_monotonic)
    ):
        raise ValueError("doorway acoustic controls are not monotonic")

    return {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "steps": len(steps),
        "closed_openness": openness[0],
        "full_wall_open_openness": openness[-1],
        "closed_wet_gain": wet_gain[0],
        "full_wall_open_wet_gain": wet_gain[-1],
        "mid_rt60_closed_s": steps[0]["rt60_s"]["mid"],
        "mid_rt60_full_wall_open_s": steps[-1]["rt60_s"]["mid"],
        "gates": {
            "nested_apertures_strictly_increase_escape": True,
            "rt60_nonincreasing_all_bands": rt60_monotonic,
            "edt_nonincreasing_all_bands": edt_monotonic,
            "drr_nondecreasing_all_bands": drr_monotonic,
            "wet_gain_nonincreasing": wet_monotonic,
            "closed_room_openness_zero": openness[0] == 0.0,
            "full_wall_open_openness_above_98_percent": (
                openness[-1] >= 0.98
            ),
            "full_wall_open_wet_gain_below_1_percent": wet_gain[-1] < 0.01,
            "minecraft_release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Monotonic nested-doorway diagnostic, not measured open-room "
            "decay truth."
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
