#!/usr/bin/env python3
"""Materialize an exclusive-owner EFX/Java fallback continuity diagnostic."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

import numpy as np


SAMPLE_RATE = 48_000
RENDER_FRAMES = 192_000
OUTPUT_BYTES = RENDER_FRAMES * 2
FAULT_STAGES = ("resource-create", "parameter-write", "source-route")
FALLBACK_WINDOWS = ((12_000, 24_000), (60_000, 72_000), (108_000, 120_000))
BOUNDARIES = tuple(value for pair in FALLBACK_WINDOWS for value in pair)
ENVIRONMENTS = ("closed", "partial", "open")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def rms(signal: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(signal))))


def longest_zero_run(signal: np.ndarray) -> int:
    longest = 0
    current = 0
    for value in signal:
        if value == 0:
            current += 1
            longest = max(longest, current)
        else:
            current = 0
    return longest


def require_sources(
    transition: dict[str, Any],
    dry_pcm: bytes,
    efx_pcm: bytes,
    java_pcm: bytes,
    failover: dict[str, Any],
) -> None:
    if (
        transition.get("schema_version") != 1
        or transition.get("status") != "valid-backend-dynamic-transition"
        or transition.get("render_format") != "s16le-mono-48000"
        or transition.get("render_frames") != RENDER_FRAMES
    ):
        raise ValueError("D100 transition report is incompatible")
    for name, payload in (
        ("dry", dry_pcm),
        ("efx", efx_pcm),
        ("java_fdn", java_pcm),
    ):
        if (
            len(payload) != OUTPUT_BYTES
            or transition.get(f"{name}_pcm_bytes") != OUTPUT_BYTES
            or transition.get(f"{name}_pcm_sha256") != sha256(payload)
        ):
            raise ValueError(f"D100 {name} PCM identity changed")
    cycles = failover.get("fault_cycles")
    if (
        failover.get("schema_version") != 1
        or failover.get("status") != "valid-openal-efx-fault-failover"
        or not isinstance(cycles, list)
        or tuple(item.get("stage") for item in cycles) != FAULT_STAGES
        or failover.get("captures_audio") is not False
        or failover.get("physical_endpoint_changed") is not False
    ):
        raise ValueError("D102 failover evidence is incompatible")
    for cycle in cycles:
        fallback = cycle.get("fallback", {})
        recovery = cycle.get("recovery_runtime", {})
        if (
            fallback.get("backend") != "JAVA_FDN"
            or fallback.get("double_wet_path") is not False
            or recovery.get("backend") != "OPENAL_EFX"
            or recovery.get("double_wet_path") is not False
        ):
            raise ValueError("D102 ownership sequence changed")


def materialize(
    transition: dict[str, Any],
    transition_sha256: str,
    dry_pcm: bytes,
    efx_pcm: bytes,
    java_pcm: bytes,
    failover: dict[str, Any],
    failover_sha256: str,
) -> tuple[dict[str, Any], bytes]:
    require_sources(transition, dry_pcm, efx_pcm, java_pcm, failover)
    dry = np.frombuffer(dry_pcm, dtype="<i2").astype(np.float64)
    efx = np.frombuffer(efx_pcm, dtype="<i2").astype(np.float64)
    java = np.frombuffer(java_pcm, dtype="<i2").astype(np.float64)
    output = efx.copy()
    ownership = []
    cursor = 0
    for index, (start, end) in enumerate(FALLBACK_WINDOWS):
        if cursor < start:
            ownership.append(
                {"start_frame": cursor, "end_frame": start, "backend": "OPENAL_EFX"}
            )
        output[start:end] = java[start:end]
        ownership.append(
            {"start_frame": start, "end_frame": end, "backend": "JAVA_FDN"}
        )
        cursor = end
        next_start = (
            FALLBACK_WINDOWS[index + 1][0]
            if index + 1 < len(FALLBACK_WINDOWS)
            else RENDER_FRAMES
        )
        ownership.append(
            {"start_frame": cursor, "end_frame": next_start, "backend": "OPENAL_EFX"}
        )
        cursor = next_start
    output_i16 = output.astype("<i2")
    output_bytes = output_i16.tobytes()

    metrics = []
    for index, boundary in enumerate(BOUNDARIES):
        from_signal, to_signal = (
            (efx, java) if index % 2 == 0 else (java, efx)
        )
        local_differences = np.concatenate(
            (
                np.abs(np.diff(from_signal[boundary - 2_000 : boundary - 100])),
                np.abs(np.diff(to_signal[boundary + 100 : boundary + 2_000])),
            )
        )
        step = float(abs(output[boundary] - output[boundary - 1]))
        local_p99 = float(np.percentile(local_differences, 99.0))
        five_ms = 240
        before_wet = rms(
            output[boundary - five_ms : boundary]
            - dry[boundary - five_ms : boundary]
        )
        after_wet = rms(
            output[boundary : boundary + five_ms]
            - dry[boundary : boundary + five_ms]
        )
        twenty_ms = 960
        output_rms = rms(output[boundary : boundary + twenty_ms])
        dry_rms = rms(dry[boundary : boundary + twenty_ms])
        metrics.append(
            {
                "frame": boundary,
                "direction": (
                    "OPENAL_EFX_TO_JAVA_FDN"
                    if index % 2 == 0
                    else "JAVA_FDN_TO_OPENAL_EFX"
                ),
                "fault_stage": FAULT_STAGES[index // 2],
                "environment": ENVIRONMENTS[index // 2],
                "residual_step_absolute": step,
                "local_difference_p99": local_p99,
                "step_to_p99_ratio": step / max(local_p99, 1.0),
                "five_ms_wet_rms_ratio": after_wet / max(before_wet, 1.0e-12),
                "twenty_ms_output_to_dry_rms_ratio": output_rms
                / max(dry_rms, 1.0e-12),
                "longest_zero_run_samples": longest_zero_run(
                    output_i16[boundary : boundary + twenty_ms]
                ),
            }
        )

    no_click = all(item["step_to_p99_ratio"] <= 2.0 for item in metrics)
    bounded_wet = all(
        0.25 <= item["five_ms_wet_rms_ratio"] <= 4.0 for item in metrics
    )
    no_dropout = all(
        item["twenty_ms_output_to_dry_rms_ratio"] >= 0.5
        and item["longest_zero_run_samples"] <= 48
        for item in metrics
    )
    report = {
        "schema_version": 1,
        "status": (
            "valid-backend-failover-continuity-diagnostic"
            if no_click and bounded_wet and no_dropout
            else "invalid-backend-failover-continuity-diagnostic"
        ),
        "render_format": "s16le-mono-48000",
        "sample_rate_hz": SAMPLE_RATE,
        "render_frames": RENDER_FRAMES,
        "source_transition_report_sha256": transition_sha256,
        "source_failover_report_sha256": failover_sha256,
        "source_pcm_sha256": {
            "dry": sha256(dry_pcm),
            "efx": sha256(efx_pcm),
            "java_fdn": sha256(java_pcm),
        },
        "output_pcm_bytes": len(output_bytes),
        "output_pcm_sha256": sha256(output_bytes),
        "splice_method": "exclusive-backend-selection-no-crossfade",
        "warm_state_source_renders": True,
        "cold_start_measured": False,
        "boundaries": metrics,
        "ownership_intervals": ownership,
        "gates": {
            "three_d102_fault_cycles_bound": True,
            "six_backend_directions_exercised": True,
            "exclusive_wet_owner": True,
            "no_resolved_software_boundary_click": no_click,
            "five_ms_wet_energy_ratio_bounded": bounded_wet,
            "no_twenty_ms_dropout_resolved": no_dropout,
            "physical_playback_or_capture_opened": False,
            "release_calibrated": False,
        },
        "captures_audio": False,
        "physical_endpoint_opened": False,
        "release_calibrated": False,
        "claim_boundary": (
            "Deterministic warm-state splices of independently rendered D100 "
            "OpenAL EFX and Java FDN streams, scheduled from D102 fault stages. "
            "This diagnoses exclusive-owner software boundaries; it does not "
            "measure cold-start DSP state, Minecraft main-context output, a "
            "physical endpoint, an audible threshold, or release calibration."
        ),
    }
    return report, output_bytes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--transition-report", type=Path, required=True)
    parser.add_argument("--dry-pcm", type=Path, required=True)
    parser.add_argument("--efx-pcm", type=Path, required=True)
    parser.add_argument("--java-pcm", type=Path, required=True)
    parser.add_argument("--failover-report", type=Path, required=True)
    parser.add_argument("--output-report", type=Path, required=True)
    parser.add_argument("--output-pcm", type=Path, required=True)
    args = parser.parse_args()
    transition_bytes = args.transition_report.read_bytes()
    failover_bytes = args.failover_report.read_bytes()
    report, output = materialize(
        json.loads(transition_bytes),
        sha256(transition_bytes),
        args.dry_pcm.read_bytes(),
        args.efx_pcm.read_bytes(),
        args.java_pcm.read_bytes(),
        json.loads(failover_bytes),
        sha256(failover_bytes),
    )
    if not report["status"].startswith("valid-"):
        raise ValueError("fallback continuity gates did not pass")
    args.output_report.parent.mkdir(parents=True, exist_ok=True)
    args.output_pcm.parent.mkdir(parents=True, exist_ok=True)
    args.output_report.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    args.output_pcm.write_bytes(output)
    print(json.dumps(report, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
