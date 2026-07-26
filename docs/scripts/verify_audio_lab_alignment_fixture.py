#!/usr/bin/env python3
"""Verify deterministic three-tone recorder/Minecraft alignment."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def verify(report: dict[str, object], report_sha256: str) -> dict[str, object]:
    if report.get("schema_version") != 1:
        raise ValueError("audio-lab alignment schema must be 1")
    if report.get("status") != "valid-audio-lab-alignment-diagnostic":
        raise ValueError("audio-lab alignment status is invalid")
    fixture = report.get("fixture")
    wav = report.get("wav")
    alignment = report.get("alignment")
    if (
        not isinstance(fixture, dict)
        or not isinstance(wav, dict)
        or not isinstance(alignment, dict)
    ):
        raise ValueError("fixture, WAV, or alignment section is missing")
    if (
        fixture.get("kind")
        != "deterministic-audio-lab-alignment-fixture"
        or fixture.get("real_audio_capture") is not False
        or fixture.get("physical_output_loopback_confirmed") is not False
    ):
        raise ValueError("alignment fixture provenance is invalid")
    if (
        wav.get("sample_rate_hz") != 48_000
        or wav.get("channels") != 1
        or wav.get("sample_width_bits") != 24
        or wav.get("frame_count") != 384_000
        or wav.get("duration_s") != 8.0
    ):
        raise ValueError("alignment fixture WAV contract failed")
    detections = alignment.get("marker_detections")
    expected_times = fixture.get("expected_marker_audio_s")
    if (
        not isinstance(detections, list)
        or len(detections) != 3
        or not isinstance(expected_times, list)
        or len(expected_times) != 3
    ):
        raise ValueError("alignment marker detections are missing")
    maximum_marker_error_ms = max(
        abs(detection["start_s"] - expected) * 1000.0
        for detection, expected in zip(detections, expected_times)
    )
    if maximum_marker_error_ms > 2.0:
        raise ValueError("marker timing exceeds one-hop gate")
    if alignment.get("minimum_marker_score", 0.0) < 0.9:
        raise ValueError("marker score is below fixture gate")
    if alignment.get("maximum_absolute_alignment_residual_ms", 99.0) > 2.0:
        raise ValueError("timeline fit residual exceeds one-hop gate")
    boundary_error_ms = abs(
        alignment.get("boundary_audio_s", -1.0)
        - fixture.get("expected_boundary_audio_s", -2.0)
    ) * 1000.0
    if boundary_error_ms > 2.0:
        raise ValueError("aligned boundary exceeds one-hop gate")
    slope_error_ppm = abs(
        alignment.get("recorder_seconds_per_minecraft_second", 0.0)
        - fixture.get("expected_clock_slope", 0.0)
    ) * 1.0e6
    if slope_error_ppm > 500.0:
        raise ValueError("recovered recorder clock slope is inaccurate")
    if report.get("real_audio_capture") is not False:
        raise ValueError("fixture overclaims real audio capture")
    if report.get("physical_output_loopback_confirmed") is not False:
        raise ValueError("fixture overclaims physical loopback")
    if report.get("release_calibrated") is not False:
        raise ValueError("alignment fixture must remain uncalibrated")

    return {
        "schema_version": 1,
        "status": "valid-audio-lab-alignment-fixture",
        "source_report_sha256": report_sha256,
        "wav_sha256": wav["wav_sha256"],
        "minimum_marker_score": alignment["minimum_marker_score"],
        "maximum_marker_error_ms": maximum_marker_error_ms,
        "maximum_alignment_residual_ms": alignment[
            "maximum_absolute_alignment_residual_ms"
        ],
        "boundary_error_ms": boundary_error_ms,
        "clock_slope_error_ppm": slope_error_ppm,
        "gates": {
            "three_markers_detected": True,
            "marker_timing_within_one_hop": True,
            "clock_fit_within_gate": True,
            "boundary_time_within_one_hop": True,
            "real_audio_capture": False,
            "physical_output_loopback_confirmed": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Deterministic alignment fixture only; no recorder endpoint "
            "or Minecraft audible output was captured."
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
