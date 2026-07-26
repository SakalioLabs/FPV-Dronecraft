#!/usr/bin/env python3
"""Verify Minecraft kinematics-to-PCM16 Doppler conformance evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path


FLOAT_TOLERANCE = 1.0e-8
MAXIMUM_FREQUENCY_ERROR_PPM = 25.0


def _number(mapping: dict[str, object], key: str) -> float:
    value = mapping.get(key)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{key} must be numeric")
    number = float(value)
    if not math.isfinite(number):
        raise ValueError(f"{key} must be finite")
    return number


def _vector(capture: dict[str, object], key: str) -> tuple[float, float, float]:
    value = capture.get(key)
    if (
        not isinstance(value, list)
        or len(value) != 3
        or any(
            isinstance(item, bool)
            or not isinstance(item, (int, float))
            or not math.isfinite(float(item))
            for item in value
        )
    ):
        raise ValueError(f"{key} must be a finite 3-vector")
    return tuple(float(item) for item in value)


def _close(actual: float, expected: float, label: str) -> None:
    scale = max(1.0, abs(actual), abs(expected))
    if abs(actual - expected) > FLOAT_TOLERANCE * scale:
        raise ValueError(f"{label} is inconsistent")


def _verify_measurement(
    label: str,
    measurement: object,
    render_ratio: float,
    expected_listener_radial: float,
    expected_source_radial: float,
    capture_sound_speed: float,
) -> dict[str, object]:
    if not isinstance(measurement, dict):
        raise ValueError(f"{label} measurement is missing")
    if measurement.get("layer") != label:
        raise ValueError(f"{label} layer identity changed")
    if label == "propeller":
        if measurement.get("tone_kind") != "blade_pass":
            raise ValueError("propeller measurement must use blade-pass tone")
    elif measurement.get("tone_kind") == "blade_pass":
        raise ValueError("motor measurement cannot use blade-pass tone")

    base_hz = _number(measurement, "base_frequency_hz")
    ratio = _number(measurement, "doppler_frequency_ratio")
    expected_hz = _number(measurement, "expected_frequency_hz")
    measured_hz = _number(measurement, "measured_frequency_hz")
    error_hz = _number(measurement, "frequency_error_hz")
    error_ppm = _number(measurement, "frequency_error_ppm")
    sound_speed = _number(measurement, "sound_speed_mps")
    listener_radial = _number(
        measurement, "listener_radial_velocity_mps"
    )
    source_radial = _number(measurement, "source_radial_velocity_mps")
    if base_hz <= 0.0 or expected_hz <= 0.0 or measured_hz <= 0.0:
        raise ValueError(f"{label} frequencies must be positive")
    _close(ratio, render_ratio, f"{label} render ratio")
    _close(expected_hz, base_hz * ratio, f"{label} shifted frequency")
    _close(error_hz, measured_hz - expected_hz, f"{label} error Hz")
    _close(
        error_ppm,
        error_hz / expected_hz * 1_000_000.0,
        f"{label} error ppm",
    )
    _close(sound_speed, capture_sound_speed, f"{label} sound speed")
    _close(
        listener_radial,
        expected_listener_radial,
        f"{label} listener radial velocity",
    )
    _close(
        source_radial,
        expected_source_radial,
        f"{label} source radial velocity",
    )
    physical_ratio = (sound_speed + listener_radial) / (
        sound_speed + source_radial
    )
    _close(ratio, physical_ratio, f"{label} physical Doppler ratio")
    if abs(error_ppm) > MAXIMUM_FREQUENCY_ERROR_PPM:
        raise ValueError(f"{label} PCM frequency error exceeds gate")
    if (
        measurement.get("sample_rate_hz") != 48_000
        or measurement.get("analyzed_samples") != 38_400
        or measurement.get("positive_crossings", 0) < 3
        or measurement.get("clipped_samples") != 0
        or measurement.get("broadband_excluded") is not True
        or measurement.get("pcm16_quantized") is not True
    ):
        raise ValueError(f"{label} PCM measurement contract failed")
    return measurement


def verify(report: dict[str, object], report_sha256: str) -> dict[str, object]:
    if report.get("schema_version") != 1:
        raise ValueError("Doppler PCM schema must be 1")
    if report.get("status") != "valid-doppler-pcm-conformance":
        raise ValueError("Doppler PCM status is invalid")
    capture = report.get("capture")
    if not isinstance(capture, dict):
        raise ValueError("Doppler PCM capture is missing")
    source_position = _vector(capture, "source_position_m")
    source_velocity = _vector(capture, "source_velocity_mps")
    listener_position = _vector(capture, "listener_position_m")
    listener_velocity = _vector(capture, "listener_velocity_mps")
    displacement = tuple(
        source_position[index] - listener_position[index]
        for index in range(3)
    )
    distance = math.sqrt(sum(value * value for value in displacement))
    if distance <= 1.0e-6:
        raise ValueError("source/listener distance is too small")
    direction = tuple(value / distance for value in displacement)
    listener_radial = sum(
        direction[index] * listener_velocity[index] for index in range(3)
    )
    source_radial = sum(
        direction[index] * source_velocity[index] for index in range(3)
    )
    sound_speed = _number(capture, "sound_speed_mps")
    render_ratio = _number(capture, "render_state_doppler_ratio")
    if not 300.0 <= sound_speed <= 380.0:
        raise ValueError("speed of sound is implausible")
    if abs(render_ratio - 1.0) <= 1.0e-6:
        raise ValueError("runtime capture did not exercise Doppler shift")
    motor = _verify_measurement(
        "motor",
        capture.get("motor"),
        render_ratio,
        listener_radial,
        source_radial,
        sound_speed,
    )
    propeller = _verify_measurement(
        "propeller",
        capture.get("propeller"),
        render_ratio,
        listener_radial,
        source_radial,
        sound_speed,
    )

    required_true = (
        "synchronized_minecraft_entity_kinematics",
        "production_doppler_shift",
        "production_phase_continuous_synthesizer",
        "isolated_tone_measurement",
    )
    for key in required_true:
        if report.get(key) is not True:
            raise ValueError(f"{key} must be true")
    required_false = (
        "server_blackbox_csv_bound",
        "mixed_live_stream_measured",
        "openal_playback_capture",
        "real_audio_capture",
        "release_calibrated",
    )
    for key in required_false:
        if report.get(key) is not False:
            raise ValueError(f"{key} must be false")

    maximum_error_ppm = max(
        abs(float(motor["frequency_error_ppm"])),
        abs(float(propeller["frequency_error_ppm"])),
    )
    return {
        "schema_version": 1,
        "status": "valid-doppler-pcm-conformance",
        "source_report_sha256": report_sha256,
        "entity_id": capture["entity_id"],
        "simulation_time_nanos": capture["simulation_time_nanos"],
        "render_state_doppler_ratio": render_ratio,
        "maximum_absolute_frequency_error_ppm": maximum_error_ppm,
        "gates": {
            "source_listener_projection_recomputed": True,
            "physical_ratio_recomputed": True,
            "render_ratio_matches_physics": True,
            "motor_pcm_within_25_ppm": True,
            "propeller_pcm_within_25_ppm": True,
            "pcm16_quantized": True,
            "broadband_excluded": True,
            "server_blackbox_csv_bound": False,
            "mixed_live_stream_measured": False,
            "openal_playback_capture": False,
            "real_audio_capture": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Synchronized Minecraft entity frame re-rendered as isolated tones "
            "through production Doppler and synthesizer code; not server "
            "blackbox CSV, mixed live output, OpenAL playback, or loopback."
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
