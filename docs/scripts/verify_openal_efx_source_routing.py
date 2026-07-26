#!/usr/bin/env python3
"""Verify transient EFX routing on a live DroneLoopSoundInstance."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def verify(report: dict[str, object], report_sha256: str) -> dict[str, object]:
    if report.get("schema_version") != 1:
        raise ValueError("OpenAL routing schema must be 1")
    if report.get("status") != "valid-routing-probe":
        raise ValueError("OpenAL routing status is invalid")
    if "sound" not in str(report.get("thread_name", "")).lower():
        raise ValueError("OpenAL routing did not run on the sound thread")
    if report.get("source_found") is not True:
        raise ValueError("active Dronecraft source was not found")
    if report.get("instance_type") != "DroneLoopSoundInstance":
        raise ValueError("routing probe did not target a drone loop")
    required_true = (
        "resources_created",
        "direct_filter_and_aux_send_attached",
        "vanilla_routing_restored",
        "resources_released",
    )
    missing = [field for field in required_true if report.get(field) is not True]
    if missing:
        raise ValueError("OpenAL routing lifecycle failed: " + ", ".join(missing))
    if report.get("al_error_code") != 0:
        raise ValueError("OpenAL routing reported an AL error")
    if report.get("persistent_efx_enabled") is not False:
        raise ValueError("diagnostic routing must not leave EFX enabled")
    if report.get("release_calibrated") is not False:
        raise ValueError("routing probe must remain uncalibrated")
    return {
        "schema_version": 1,
        "status": "valid-routing-probe",
        "source_report_sha256": report_sha256,
        "instance_type": report["instance_type"],
        "gates": {
            "active_drone_source_found": True,
            "sound_thread_routing": True,
            "direct_filter_and_aux_send_attached": True,
            "vanilla_routing_restored": True,
            "efx_resources_released": True,
            "al_error_zero": True,
            "persistent_efx_enabled": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Transient routing feasibility only; no persistent EFX "
            "controller, device-reload lifecycle, or audible calibration."
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
