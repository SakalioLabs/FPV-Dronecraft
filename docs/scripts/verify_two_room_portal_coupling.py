#!/usr/bin/env python3
"""Verify transport and return through nested two-room portals."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


APERTURES = [0, 1, 2, 3, 9, 15, 45]
BANDS = ("low", "mid", "high")


def load(path: Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def strictly_increasing(values: list[float]) -> bool:
    return all(after > before for before, after in zip(values, values[1:]))


def nonincreasing(values: list[float]) -> bool:
    return all(after <= before for before, after in zip(values, values[1:]))


def verify(report: dict[str, object]) -> dict[str, object]:
    if report.get("schema_version") != 1:
        raise ValueError("schema_version must be 1")
    if report.get("status") != "valid-diagnostic":
        raise ValueError("report status is invalid")
    if report.get("room_interior_cells_each") != {
        "length": 9,
        "width": 9,
        "height": 5,
    }:
        raise ValueError("room geometry changed")
    if report.get("ray_count") != 4096:
        raise ValueError("ray_count changed")
    if report.get("maximum_bounces") != 48:
        raise ValueError("maximum_bounces changed")

    steps = report["steps"]
    if [row["aperture_cells"] for row in steps] != APERTURES:
        raise ValueError("portal aperture matrix changed")
    if any(row["escaped_rays"] != 0 for row in steps):
        raise ValueError("closed two-room volume escaped")
    if any(row["openness"] != 0.0 for row in steps):
        raise ValueError("portal transport was mislabeled as openness")

    hit_fraction = [row["room_b_hit_fraction"] for row in steps]
    entering = [row["rays_entering_room_b"] for row in steps]
    returning = [row["rays_returning_to_room_a"] for row in steps]
    mfp = [row["effective_mfp_m"] for row in steps]
    if not all(
        strictly_increasing(values)
        for values in (hit_fraction, entering, returning, mfp)
    ):
        raise ValueError("portal coupling is not strictly increasing")

    rt60_monotonic = all(
        nonincreasing([row["rt60_s"][band] for row in steps])
        for band in BANDS
    )
    edt_monotonic = all(
        nonincreasing([row["edt_s"][band] for row in steps])
        for band in BANDS
    )
    wet_monotonic = nonincreasing([row["wet_gain"] for row in steps])
    if not all((rt60_monotonic, edt_monotonic, wet_monotonic)):
        raise ValueError("coupled-room controls are not monotonic")

    full = steps[-1]
    return {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "steps": len(steps),
        "full_portal_room_b_hit_fraction": full["room_b_hit_fraction"],
        "full_portal_entering_ray_fraction": (
            full["rays_entering_room_b"] / 4096
        ),
        "full_portal_returning_ray_fraction": (
            full["rays_returning_to_room_a"] / 4096
        ),
        "mid_rt60_closed_s": steps[0]["rt60_s"]["mid"],
        "mid_rt60_full_portal_s": full["rt60_s"]["mid"],
        "closed_wet_gain": steps[0]["wet_gain"],
        "full_portal_wet_gain": full["wet_gain"],
        "gates": {
            "portal_transport_not_mislabeled_as_escape": True,
            "room_b_hit_fraction_strictly_increasing": True,
            "entering_rays_strictly_increasing": True,
            "returning_rays_strictly_increasing": True,
            "effective_mfp_strictly_increasing": True,
            "rt60_nonincreasing_all_bands": rt60_monotonic,
            "edt_nonincreasing_all_bands": edt_monotonic,
            "wet_gain_nonincreasing": wet_monotonic,
            "full_portal_above_99_percent_entering": (
                full["rays_entering_room_b"] / 4096 >= 0.99
            ),
            "full_portal_above_95_percent_returning": (
                full["rays_returning_to_room_a"] / 4096 >= 0.95
            ),
            "minecraft_release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Closed two-room material-tagged transport diagnostic, not "
            "measured coupled-room decay truth."
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
