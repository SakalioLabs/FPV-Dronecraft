#!/usr/bin/env python3
"""Verify the live OpenAL native-Doppler ownership and reload evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path


TOLERANCE = 1.0e-6


def _finite_number(state: dict[str, object], key: str) -> float:
    value = state.get(key)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{key} must be numeric")
    number = float(value)
    if not math.isfinite(number):
        raise ValueError(f"{key} must be finite")
    return number


def _verify_state(label: str, state: object) -> dict[str, object]:
    if not isinstance(state, dict):
        raise ValueError(f"{label} Doppler guard state is missing")
    if (
        state.get("enabled") is not True
        or state.get("operational") is not True
        or state.get("guarded_sources", 0) < 2
        or state.get("al_error_code") != 0
    ):
        raise ValueError(f"{label} Doppler guard state failed")

    doppler_factor = _finite_number(state, "doppler_factor")
    speed_of_sound = _finite_number(
        state, "speed_of_sound_meters_per_second"
    )
    guard_velocity_error = _finite_number(
        state, "maximum_guard_velocity_difference"
    )
    native_ratio_error = _finite_number(
        state, "maximum_native_ratio_deviation_after"
    )
    minimum_internal = _finite_number(
        state, "minimum_internal_doppler_ratio"
    )
    maximum_internal = _finite_number(
        state, "maximum_internal_doppler_ratio"
    )
    minimum_pitch = _finite_number(state, "minimum_source_pitch")
    maximum_pitch = _finite_number(state, "maximum_source_pitch")
    if doppler_factor < 0.0:
        raise ValueError(f"{label} Doppler factor is invalid")
    if not 300.0 <= speed_of_sound <= 380.0:
        raise ValueError(f"{label} speed of sound is implausible")
    if guard_velocity_error > TOLERANCE:
        raise ValueError(f"{label} source/listener velocity guard diverged")
    if native_ratio_error > TOLERANCE:
        raise ValueError(f"{label} native Doppler was not neutralized")
    if minimum_internal <= 0.0 or maximum_internal < minimum_internal:
        raise ValueError(f"{label} internal Doppler ratio is invalid")
    if (
        abs(minimum_pitch - 1.0) > TOLERANCE
        or abs(maximum_pitch - 1.0) > TOLERANCE
    ):
        raise ValueError(f"{label} OpenAL source pitch is not unity")
    return state


def verify(report: dict[str, object], report_sha256: str) -> dict[str, object]:
    if report.get("schema_version") != 1:
        raise ValueError("native Doppler guard schema must be 1")
    if report.get("status") != "valid-openal-native-doppler-guard":
        raise ValueError("native Doppler guard status is invalid")

    before = _verify_state("before_reload", report.get("before_reload"))
    after = _verify_state("after_reload", report.get("after_reload"))
    if after.get("context_rebuilds", 0) <= before.get(
        "context_rebuilds", 0
    ):
        raise ValueError("OpenAL context was not rebuilt")

    required_true = (
        "procedural_pcm_tonal_doppler",
        "native_complete_stream_resampling_rejected",
        "native_doppler_neutralized",
        "sound_engine_reload_exercised",
    )
    for key in required_true:
        if report.get(key) is not True:
            raise ValueError(f"{key} must be true")
    required_false = (
        "minecraft_channel_velocity_api_available",
        "minecraft_listener_velocity_api_available",
        "procedural_pcm_broadband_doppler",
        "other_sources_modified",
        "physical_device_switch_exercised",
        "real_audio_capture",
        "release_calibrated",
    )
    for key in required_false:
        if report.get(key) is not False:
            raise ValueError(f"{key} must be false")

    return {
        "schema_version": 1,
        "status": "valid-openal-native-doppler-guard",
        "source_report_sha256": report_sha256,
        "context_rebuilds_before": before["context_rebuilds"],
        "context_rebuilds_after": after["context_rebuilds"],
        "guarded_sources_after": after["guarded_sources"],
        "gates": {
            "tonal_doppler_owned_by_procedural_pcm": True,
            "broadband_not_doppler_shifted": True,
            "openal_source_pitch_unity": True,
            "native_relative_doppler_neutralized": True,
            "unrelated_sources_untouched": True,
            "sound_engine_reload_exercised": True,
            "physical_device_switch_exercised": False,
            "audible_doppler_validated": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Current Minecraft/OpenAL source state and sound-engine reload "
            "only; no audible Doppler validation, physical-device switch, "
            "or release calibration."
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
