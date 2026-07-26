#!/usr/bin/env python3
"""Verify the integrated Minecraft closed/open/closed portal capture."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def load(path: Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def verify(report: dict[str, object], report_sha256: str) -> dict[str, object]:
    if report.get("schema_version") != 1:
        raise ValueError("Minecraft portal schema must be 1")
    if report.get("status") != "valid-diagnostic":
        raise ValueError("Minecraft portal status is invalid")
    if report.get("source") != "integrated-client-world":
        raise ValueError("Minecraft portal source changed")
    if report.get("sequence") != ["closed", "open", "closed"]:
        raise ValueError("Minecraft portal transition sequence changed")
    if report.get("portal_aperture_cells") != 12:
        raise ValueError("Minecraft portal aperture changed")
    if report.get("ray_count") != 4096:
        raise ValueError("Minecraft portal ray count changed")
    if report.get("maximum_bounces") != 48:
        raise ValueError("Minecraft portal bounce count changed")

    gates = {
        "closed_room_does_not_hit_room_b": (
            report.get("closed_room_b_hits") == 0
        ),
        "open_portal_hits_room_b": report.get("open_room_b_hits", 0) > 0,
        "open_portal_transports_rays": (
            report.get("open_rays_entering_room_b", 0) > 0
        ),
        "open_portal_returns_rays": (
            report.get("open_rays_returning_room_a", 0) > 0
        ),
        "compound_volume_is_not_snapshot_escape": (
            report.get("closed_openness") == 0.0
            and report.get("open_openness") == 0.0
        ),
        "soft_room_reduces_mid_rt60": (
            report.get("open_mid_rt60_s", float("inf"))
            < report.get("closed_mid_rt60_s", 0.0)
        ),
        "closed_endpoint_repeatable": (
            report.get("closed_endpoint_repeatable") is True
        ),
        "minecraft_release_calibrated": False,
    }
    if not all(
        value
        for key, value in gates.items()
        if key != "minecraft_release_calibrated"
    ):
        failed = [key for key, value in gates.items() if not value]
        raise ValueError("Minecraft portal gates failed: " + ", ".join(failed))
    if report.get("release_calibrated") is not False:
        raise ValueError("Minecraft portal must remain uncalibrated")

    return {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "source_report_sha256": report_sha256,
        "open_room_b_hits": report["open_room_b_hits"],
        "open_rays_entering_room_b": report[
            "open_rays_entering_room_b"
        ],
        "open_rays_returning_room_a": report[
            "open_rays_returning_room_a"
        ],
        "closed_mid_rt60_s": report["closed_mid_rt60_s"],
        "open_mid_rt60_s": report["open_mid_rt60_s"],
        "gates": gates,
        "release_calibrated": False,
        "claim_boundary": (
            "Real Minecraft block-state capture and material mapping; not "
            "measured coupled RIR, moving-door interpolation, or OpenAL "
            "scheduling."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    raw = args.report.read_bytes()
    result = verify(
        json.loads(raw.decode("utf-8")),
        hashlib.sha256(raw).hexdigest(),
    )
    payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(payload, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
