#!/usr/bin/env python3
"""Verify recovery from Minecraft's real sound-engine/OpenAL reload."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def verify(report: dict[str, object], report_sha256: str) -> dict[str, object]:
    if report.get("schema_version") != 1:
        raise ValueError("EFX reload schema must be 1")
    if report.get("status") != "valid-sound-engine-reload":
        raise ValueError("EFX reload status is invalid")
    if report.get("reload_entrypoint") != "Minecraft.SoundManager.reload":
        raise ValueError("unexpected sound-engine reload entrypoint")

    before = report.get("before")
    after = report.get("after")
    if not isinstance(before, dict) or not isinstance(after, dict):
        raise ValueError("EFX reload lifecycle sections are missing")
    for label, state in (("before", before), ("after", after)):
        if (
            state.get("operational") is not True
            or state.get("shared_resources_created") is not True
            or state.get("attached_sources", 0) < 2
            or state.get("allocated_source_filters")
            != state.get("attached_sources")
            or state.get("al_error_code") != 0
        ):
            raise ValueError(f"{label} EFX reload state failed")
    if after.get("context_rebuilds", 0) <= before.get(
        "context_rebuilds", 0
    ):
        raise ValueError("OpenAL context was not rebuilt")
    if report.get("java_wet_bus_suppressed") is not True:
        raise ValueError("Java wet bus resumed after EFX reload")
    if report.get("sound_engine_reload_exercised") is not True:
        raise ValueError("sound-engine reload was not exercised")
    if report.get("physical_device_switch_exercised") is not False:
        raise ValueError("report overclaims physical device switching")
    if report.get("release_calibrated") is not False:
        raise ValueError("reload evidence must remain uncalibrated")

    return {
        "schema_version": 1,
        "status": "valid-sound-engine-reload",
        "source_report_sha256": report_sha256,
        "context_rebuilds_before": before["context_rebuilds"],
        "context_rebuilds_after": after["context_rebuilds"],
        "attached_sources_after": after["attached_sources"],
        "gates": {
            "minecraft_sound_engine_reload_exercised": True,
            "openal_context_rebuilt": True,
            "shared_efx_recreated": True,
            "drone_sources_reattached": True,
            "java_wet_bus_remains_suppressed": True,
            "al_error_zero": True,
            "physical_device_switch_exercised": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Minecraft sound-engine destroy/load and OpenAL context rebuild "
            "only; physical output-device switching, audible calibration, "
            "and callback underrun are not measured."
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
