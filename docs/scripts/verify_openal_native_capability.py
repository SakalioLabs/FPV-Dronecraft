#!/usr/bin/env python3
"""Verify a live sound-thread OpenAL EFX/HRTF capability report."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def verify(report: dict[str, object], report_sha256: str) -> dict[str, object]:
    if report.get("schema_version") != 1:
        raise ValueError("OpenAL capability schema must be 1")
    if report.get("status") != "valid-capability-probe":
        raise ValueError("OpenAL capability status is invalid")
    if not report.get("active_context"):
        raise ValueError("OpenAL probe did not run in an active context")
    if "sound" not in str(report.get("thread_name", "")).lower():
        raise ValueError("OpenAL probe did not run on the sound thread")
    for field in ("device_name", "vendor", "renderer", "version"):
        if not str(report.get(field, "")).strip():
            raise ValueError(f"OpenAL {field} is missing")

    efx = report.get("efx_supported") is True
    created = report.get("efx_resources_created") is True
    released = report.get("efx_resources_released") is True
    sends = report.get("maximum_auxiliary_sends")
    if not isinstance(sends, int) or isinstance(sends, bool) or sends < 0:
        raise ValueError("OpenAL auxiliary-send count is invalid")
    if efx:
        if sends < 1 or not created or not released:
            raise ValueError("advertised EFX failed its lifecycle gate")
        if report.get("al_error_code") != 0:
            raise ValueError("EFX resource exercise reported an AL error")
    elif sends != 0 or created or released:
        raise ValueError("non-EFX report claims EFX resources")
    if report.get("hrtf_enabled") is True and (
        report.get("hrtf_extension_supported") is not True
    ):
        raise ValueError("HRTF cannot be enabled without its extension")
    if report.get("default_audio_path_changed") is not False:
        raise ValueError("capability probe changed the default audio path")
    if report.get("release_calibrated") is not False:
        raise ValueError("capability probe must remain uncalibrated")

    native_efx_eligible = (
        efx
        and sends >= 1
        and created
        and released
        and report.get("al_error_code") == 0
    )
    return {
        "schema_version": 1,
        "status": "valid-capability-probe",
        "source_report_sha256": report_sha256,
        "device_name": report["device_name"],
        "vendor": report["vendor"],
        "renderer": report["renderer"],
        "version": report["version"],
        "maximum_auxiliary_sends": sends,
        "gates": {
            "active_sound_thread_context": True,
            "efx_contract_self_consistent": True,
            "efx_resource_lifecycle_clean": (
                not efx or (created and released)
            ),
            "hrtf_state_self_consistent": True,
            "default_audio_path_unchanged": True,
            "native_efx_eligible": native_efx_eligible,
            "hrtf_enabled": report.get("hrtf_enabled") is True,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Capability and temporary resource-lifecycle evidence only; no "
            "Minecraft source filter, auxiliary send, or audible calibration."
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
