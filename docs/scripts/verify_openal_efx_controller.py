#!/usr/bin/env python3
"""Verify shared EFX persistence across reload and source removal."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def verify(report: dict[str, object], report_sha256: str) -> dict[str, object]:
    if report.get("schema_version") != 1:
        raise ValueError("EFX controller schema must be 1")
    if report.get("status") != "valid-controller-diagnostic":
        raise ValueError("EFX controller status is invalid")
    if report.get("feature_property") != "fpvdrone.openalEfx":
        raise ValueError("EFX feature property changed")
    if report.get("feature_default") is not False:
        raise ValueError("EFX feature must remain default-off")
    if report.get("test_feature_enabled") is not True:
        raise ValueError("EFX test did not opt in")
    if report.get("test_java_reverb_enabled") is not True:
        raise ValueError("mutual-exclusion test did not request Java reverb")
    if report.get("java_wet_bus_suppressed") is not True:
        raise ValueError("EFX did not suppress the Java wet bus")

    active = report.get("active")
    reloaded = report.get("after_sound_engine_reload")
    cleaned = report.get("after_source_removal")
    if (
        not isinstance(active, dict)
        or not isinstance(reloaded, dict)
        or not isinstance(cleaned, dict)
    ):
        raise ValueError("EFX lifecycle sections are missing")
    if (
        active.get("operational") is not True
        or active.get("shared_resources_created") is not True
        or active.get("attached_sources", 0) < 2
        or active.get("allocated_source_filters")
        != active.get("attached_sources")
        or active.get("context_rebuilds", 0) < 1
        or active.get("al_error_code") != 0
    ):
        raise ValueError("active shared EFX state failed")
    if (
        reloaded.get("operational") is not True
        or reloaded.get("shared_resources_created") is not True
        or reloaded.get("attached_sources", 0) < 2
        or reloaded.get("allocated_source_filters")
        != reloaded.get("attached_sources")
        or reloaded.get("context_rebuilds", 0)
        <= active.get("context_rebuilds", 0)
        or reloaded.get("al_error_code") != 0
    ):
        raise ValueError("EFX sound-engine reload recovery failed")
    if (
        cleaned.get("operational") is not False
        or cleaned.get("shared_resources_created") is not False
        or cleaned.get("attached_sources") != 0
        or cleaned.get("allocated_source_filters") != 0
        or cleaned.get("cleanup_count", 0)
        <= reloaded.get("cleanup_count", 0)
        or cleaned.get("al_error_code") != 0
    ):
        raise ValueError("EFX source-removal cleanup failed")
    if report.get("sound_engine_reload_exercised") is not True:
        raise ValueError("sound-engine reload was not exercised")
    if report.get("physical_device_switch_exercised") is not False:
        raise ValueError("report overclaims physical device switching")
    if report.get("release_calibrated") is not False:
        raise ValueError("EFX controller must remain uncalibrated")

    return {
        "schema_version": 1,
        "status": "valid-controller-diagnostic",
        "source_report_sha256": report_sha256,
        "active_attached_sources": active["attached_sources"],
        "active_source_filters": active["allocated_source_filters"],
        "context_rebuilds_after_reload": reloaded["context_rebuilds"],
        "cleanup_count_after_source_removal": cleaned["cleanup_count"],
        "gates": {
            "feature_default_off": True,
            "shared_slot_operational": True,
            "one_filter_per_active_source": True,
            "java_wet_bus_mutually_exclusive": True,
            "sound_engine_reload_recovers": True,
            "source_removal_releases_every_resource": True,
            "al_error_zero": True,
            "physical_device_switch_exercised": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Persistent shared-slot, sound-engine context rebuild, and "
            "source-removal lifecycle only; physical device switching, "
            "audible calibration, and callback underrun are not measured."
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
