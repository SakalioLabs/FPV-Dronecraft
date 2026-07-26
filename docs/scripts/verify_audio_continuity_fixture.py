#!/usr/bin/env python3
"""Verify the deterministic click/dropout analyzer fixture."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def verify(report: dict[str, object], report_sha256: str) -> dict[str, object]:
    if report.get("schema_version") != 1:
        raise ValueError("audio continuity schema must be 1")
    if report.get("status") != "valid-audio-continuity-diagnostic":
        raise ValueError("audio continuity status is invalid")
    fixture = report.get("fixture")
    audio = report.get("wav")
    analysis = report.get("analysis")
    if (
        not isinstance(fixture, dict)
        or not isinstance(audio, dict)
        or not isinstance(analysis, dict)
    ):
        raise ValueError("fixture, WAV, or analysis section is missing")
    if (
        fixture.get("kind") != "deterministic-analyzer-fixture"
        or fixture.get("injected_reload_click") is not True
        or fixture.get("injected_dropout") is not True
        or fixture.get("real_minecraft_capture") is not False
        or fixture.get("physical_output_loopback_confirmed") is not False
    ):
        raise ValueError("fixture provenance is invalid")
    if (
        audio.get("sample_rate_hz") != 48_000
        or audio.get("channels") != 1
        or audio.get("sample_width_bits") != 24
        or audio.get("frame_count") != 288_000
        or audio.get("duration_s") != 6.0
        or not isinstance(audio.get("wav_sha256"), str)
        or len(audio["wav_sha256"]) != 64
    ):
        raise ValueError("fixture WAV contract failed")
    if (
        analysis.get("reload_guard_click_event_count", 0) < 1
        or analysis.get("reload_guard_peak_over_threshold", 0.0) <= 1.0
    ):
        raise ValueError("injected reload click was not detected")
    if (
        analysis.get("dropout_event_count") != 1
        or not 55.0 <= analysis.get("longest_dropout_ms", 0.0) <= 65.0
    ):
        raise ValueError("injected dropout was not detected")
    events = analysis.get("dropout_events")
    if (
        not isinstance(events, list)
        or len(events) != 1
        or not isinstance(events[0], dict)
        or abs(events[0].get("start_s", 0.0) - 3.65) > 0.005
        or abs(events[0].get("end_s", 0.0) - 3.71) > 0.005
    ):
        raise ValueError("dropout timing is inaccurate")
    if report.get("real_minecraft_capture") is not False:
        raise ValueError("fixture overclaims a real Minecraft capture")
    if report.get("physical_output_loopback_confirmed") is not False:
        raise ValueError("fixture overclaims physical loopback")
    if report.get("openal_callback_underrun_counter_available") is not False:
        raise ValueError("fixture overclaims an OpenAL underrun counter")
    if report.get("release_calibrated") is not False:
        raise ValueError("fixture must remain uncalibrated")

    return {
        "schema_version": 1,
        "status": "valid-audio-continuity-fixture",
        "source_report_sha256": report_sha256,
        "wav_sha256": audio["wav_sha256"],
        "reload_click_events": analysis["reload_guard_click_event_count"],
        "dropout_events": analysis["dropout_event_count"],
        "longest_dropout_ms": analysis["longest_dropout_ms"],
        "gates": {
            "pcm24_contract": True,
            "injected_reload_click_detected": True,
            "injected_dropout_detected": True,
            "dropout_timing_within_one_frame": True,
            "real_minecraft_capture": False,
            "physical_output_loopback_confirmed": False,
            "openal_callback_underrun_counter_available": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Analyzer and deterministic fixture only; no microphone or "
            "loopback recording was performed."
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
