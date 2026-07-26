#!/usr/bin/env python3
"""Verify read-only OpenAL clock/source-latency evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path


SAMPLE_NAMES = ("first", "second")
STREAM_BUFFER_DURATION_SECONDS = 1.0
MAXIMUM_PAIR_HOST_INTERVAL_SECONDS = 0.8
MINIMUM_OFFSET_ADVANCE_TOLERANCE_SECONDS = 0.1


def _finite_nonnegative(value: object, field: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{field} must be numeric")
    result = float(value)
    if not math.isfinite(result) or result < 0.0:
        raise ValueError(f"{field} must be finite and nonnegative")
    return result


def _integer_nonnegative(value: object, field: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise ValueError(f"{field} must be a nonnegative integer")
    return value


def _verify_sample(
    sample: dict[str, object],
    pair_name: str,
    sample_name: str,
    device_clock_supported: bool,
    source_latency_supported: bool,
    device_name: str,
) -> None:
    label = f"{pair_name}.{sample_name}"
    if sample.get("source_found") is not True:
        raise ValueError(f"{label} did not find a drone source")
    if sample.get("instance_type") != "DroneLoopSoundInstance":
        raise ValueError(f"{label} used an unexpected source type")
    if sample.get("thread_name") != "Sound engine":
        raise ValueError(f"{label} did not run on the sound engine thread")
    if sample.get("active_context") is not True:
        raise ValueError(f"{label} did not have an active OpenAL context")
    if sample.get("device_name") != device_name or not device_name:
        raise ValueError(f"{label} device identity is invalid")
    if sample.get("device_clock_supported") is not device_clock_supported:
        raise ValueError(f"{label} device-clock support changed")
    if sample.get("source_latency_supported") is not source_latency_supported:
        raise ValueError(f"{label} source-latency support changed")
    if sample.get("native_telemetry_available") is not source_latency_supported:
        raise ValueError(f"{label} native telemetry availability is invalid")
    if sample.get("al_error_code") != 0 or sample.get("alc_error_code") != 0:
        raise ValueError(f"{label} contains an OpenAL error")

    _integer_nonnegative(sample.get("host_monotonic_ns"), f"{label}.host")
    clock_ns = _integer_nonnegative(
        sample.get("device_clock_ns"), f"{label}.device_clock"
    )
    latency_ns = _integer_nonnegative(
        sample.get("device_latency_ns"), f"{label}.device_latency"
    )
    source_offset = _finite_nonnegative(
        sample.get("source_offset_seconds"), f"{label}.source_offset"
    )
    source_latency = _finite_nonnegative(
        sample.get("source_latency_seconds"), f"{label}.source_latency"
    )
    clock_offset = _finite_nonnegative(
        sample.get("source_clock_offset_seconds"),
        f"{label}.source_clock_offset",
    )
    source_clock = _finite_nonnegative(
        sample.get("source_device_clock_seconds"),
        f"{label}.source_device_clock",
    )
    if source_latency_supported:
        if source_latency >= 1.0:
            raise ValueError(f"{label} source latency is implausibly large")
    elif any((source_offset, source_latency)):
        raise ValueError(f"{label} overclaims unsupported source telemetry")
    if not device_clock_supported and any(
        (clock_ns, latency_ns, clock_offset, source_clock)
    ):
        raise ValueError(f"{label} overclaims unsupported device-clock telemetry")


def _verify_pair(
    pair: dict[str, object],
    pair_name: str,
    device_clock_supported: bool,
    source_latency_supported: bool,
    device_name: str,
) -> dict[str, object]:
    samples: dict[str, dict[str, object]] = {}
    for sample_name in SAMPLE_NAMES:
        sample = pair.get(sample_name)
        if not isinstance(sample, dict):
            raise ValueError(f"{pair_name}.{sample_name} is missing")
        _verify_sample(
            sample,
            pair_name,
            sample_name,
            device_clock_supported,
            source_latency_supported,
            device_name,
        )
        samples[sample_name] = sample

    first = samples["first"]
    second = samples["second"]
    reported_host_elapsed = _integer_nonnegative(
        pair.get("host_elapsed_ns"), f"{pair_name}.host_elapsed"
    )
    computed_host_elapsed = (
        int(second["host_monotonic_ns"]) - int(first["host_monotonic_ns"])
    )
    if reported_host_elapsed <= 0 or reported_host_elapsed != computed_host_elapsed:
        raise ValueError(f"{pair_name} host elapsed time is invalid")

    clock_elapsed = _integer_nonnegative(
        pair.get("device_clock_elapsed_ns"),
        f"{pair_name}.device_clock_elapsed",
    )
    ratio = _finite_nonnegative(
        pair.get("device_to_host_clock_rate_ratio"),
        f"{pair_name}.clock_rate_ratio",
    )
    reported_offset_advance = _finite_nonnegative(
        pair.get("source_offset_advance_seconds"),
        f"{pair_name}.source_offset_advance",
    )
    reported_offset_wrapped = pair.get("source_offset_wrapped")
    if not isinstance(reported_offset_wrapped, bool):
        raise ValueError(f"{pair_name}.source_offset_wrapped must be boolean")
    if device_clock_supported:
        expected_clock_elapsed = (
            int(second["device_clock_ns"]) - int(first["device_clock_ns"])
        )
        if clock_elapsed <= 0 or clock_elapsed != expected_clock_elapsed:
            raise ValueError(f"{pair_name} device clock did not advance")
        expected_ratio = clock_elapsed / reported_host_elapsed
        if not math.isclose(ratio, expected_ratio, rel_tol=1e-12, abs_tol=0.0):
            raise ValueError(f"{pair_name} device/host clock ratio is invalid")
    elif clock_elapsed != 0 or ratio != 0.0:
        raise ValueError(f"{pair_name} overclaims unsupported device clock")

    if source_latency_supported:
        if pair.get("native_telemetry_validated") is not True:
            raise ValueError(f"{pair_name} source telemetry was not validated")
        first_offset = float(first["source_offset_seconds"])
        second_offset = float(second["source_offset_seconds"])
        host_elapsed_seconds = reported_host_elapsed / 1e9
        if host_elapsed_seconds >= MAXIMUM_PAIR_HOST_INTERVAL_SECONDS:
            raise ValueError(
                f"{pair_name} interval is too long for one-buffer wrap"
            )
        raw_advance = second_offset - first_offset
        expected_wrapped = raw_advance <= 0.0
        expected_advance = raw_advance + (
            STREAM_BUFFER_DURATION_SECONDS if expected_wrapped else 0.0
        )
        tolerance = max(
            MINIMUM_OFFSET_ADVANCE_TOLERANCE_SECONDS,
            host_elapsed_seconds * 0.5,
        )
        if (
            reported_offset_wrapped is not expected_wrapped
            or not math.isclose(
                reported_offset_advance,
                expected_advance,
                rel_tol=1e-12,
                abs_tol=1e-12,
            )
            or expected_advance <= 0.0
            or abs(expected_advance - host_elapsed_seconds) > tolerance
        ):
            raise ValueError(
                f"{pair_name} source offset advance is inconsistent"
            )
    else:
        if pair.get("native_telemetry_validated") is not False:
            raise ValueError(
                f"{pair_name} overclaims unavailable native telemetry"
            )
        if reported_offset_advance != 0.0 or reported_offset_wrapped:
            raise ValueError(
                f"{pair_name} overclaims unavailable source progress"
            )

    return {
        "host_elapsed_ns": reported_host_elapsed,
        "source_offset_advance_seconds": reported_offset_advance,
        "source_offset_wrapped": reported_offset_wrapped,
        "source_latency_seconds": [
            float(first["source_latency_seconds"]),
            float(second["source_latency_seconds"]),
        ],
        "device_clock_elapsed_ns": clock_elapsed,
        "device_to_host_clock_rate_ratio": ratio,
    }


def verify_timing_pairs(
    first_pair: object,
    second_pair: object,
    *,
    first_name: str,
    second_name: str,
) -> dict[str, object]:
    if not isinstance(first_pair, dict):
        raise ValueError(f"{first_name} is missing")
    first_sample = first_pair.get("first")
    if not isinstance(first_sample, dict):
        raise ValueError(f"{first_name}.first is missing")
    device_clock_supported = first_sample.get("device_clock_supported")
    source_latency_supported = first_sample.get("source_latency_supported")
    if not isinstance(device_clock_supported, bool):
        raise ValueError("device-clock support must be boolean")
    if not isinstance(source_latency_supported, bool):
        raise ValueError("source-latency support must be boolean")
    device_name = first_sample.get("device_name")
    if not isinstance(device_name, str):
        raise ValueError("device name must be a string")

    pairs: dict[str, object] = {}
    for pair_name, pair in (
        (first_name, first_pair),
        (second_name, second_pair),
    ):
        if not isinstance(pair, dict):
            raise ValueError(f"{pair_name} is missing")
        pairs[pair_name] = _verify_pair(
            pair,
            pair_name,
            device_clock_supported,
            source_latency_supported,
            device_name,
        )
    return {
        "device_name": device_name,
        "device_clock_supported": device_clock_supported,
        "source_latency_supported": source_latency_supported,
        "pairs": pairs,
    }


def verify(report: dict[str, object], report_sha256: str) -> dict[str, object]:
    if report.get("schema_version") != 1:
        raise ValueError("OpenAL clock/latency schema must be 1")
    if report.get("status") != "valid-openal-clock-latency-diagnostic":
        raise ValueError("OpenAL clock/latency status is invalid")
    if report.get("extension") != "ALC_SOFT_device_clock":
        raise ValueError("unexpected device-clock extension")
    if report.get("source_extension") != "AL_SOFT_source_latency":
        raise ValueError("unexpected source-latency extension")

    for key, required in (
        ("extension_support_stable", True),
        ("device_name_stable", True),
        ("sound_engine_reload_exercised", True),
        ("physical_device_switch_exercised", False),
        ("callback_underrun_counter_available", False),
        ("audio_path_changed", False),
        ("release_calibrated", False),
    ):
        if report.get(key) is not required:
            raise ValueError(f"{key} must be {str(required).lower()}")

    timing = verify_timing_pairs(
        report.get("before_reload"),
        report.get("after_reload"),
        first_name="before_reload",
        second_name="after_reload",
    )
    device_name = timing["device_name"]
    device_clock_supported = timing["device_clock_supported"]
    source_latency_supported = timing["source_latency_supported"]

    return {
        "schema_version": 1,
        "status": "valid-openal-clock-latency-diagnostic",
        "source_report_sha256": report_sha256,
        "device_name": device_name,
        "device_clock_supported": device_clock_supported,
        "source_latency_supported": source_latency_supported,
        "pairs": timing["pairs"],
        "gates": {
            "sound_engine_thread": True,
            "active_context": True,
            "support_stable_across_reload": True,
            "device_name_stable_across_reload": True,
            "source_telemetry_validated": source_latency_supported,
            "device_clock_telemetry_validated": device_clock_supported,
            "sound_engine_reload_exercised": True,
            "physical_device_switch_exercised": False,
            "callback_underrun_counter_available": False,
            "audio_path_changed": False,
            "end_to_end_latency_measured": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Read-only capability and available OpenAL source/device timing "
            "telemetry around Minecraft sound-engine reload only. Reported "
            "source latency is not electroacoustic end-to-end latency; no "
            "callback underrun counter, physical device switch, loopback, "
            "or release calibration is claimed."
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
