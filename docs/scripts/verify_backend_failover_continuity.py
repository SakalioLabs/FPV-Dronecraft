#!/usr/bin/env python3
"""Independently verify the D104 exclusive-owner fallback splice."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
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


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _rms(signal: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(signal))))


def _longest_zero_run(signal: np.ndarray) -> int:
    longest = 0
    current = 0
    for value in signal:
        if value == 0:
            current += 1
            longest = max(longest, current)
        else:
            current = 0
    return longest


def _expected_ownership() -> list[dict[str, int | str]]:
    result = []
    cursor = 0
    for index, (start, end) in enumerate(FALLBACK_WINDOWS):
        if cursor < start:
            result.append(
                {"start_frame": cursor, "end_frame": start, "backend": "OPENAL_EFX"}
            )
        result.append(
            {"start_frame": start, "end_frame": end, "backend": "JAVA_FDN"}
        )
        cursor = end
        next_start = (
            FALLBACK_WINDOWS[index + 1][0]
            if index + 1 < len(FALLBACK_WINDOWS)
            else RENDER_FRAMES
        )
        result.append(
            {"start_frame": cursor, "end_frame": next_start, "backend": "OPENAL_EFX"}
        )
        cursor = next_start
    return result


def verify(
    report: dict[str, Any],
    report_sha256: str,
    output_pcm: bytes,
    transition: dict[str, Any],
    transition_sha256: str,
    dry_pcm: bytes,
    efx_pcm: bytes,
    java_pcm: bytes,
    failover: dict[str, Any],
    failover_sha256: str,
) -> dict[str, Any]:
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "valid-backend-failover-continuity-diagnostic"
        or report.get("render_format") != "s16le-mono-48000"
        or report.get("sample_rate_hz") != SAMPLE_RATE
        or report.get("render_frames") != RENDER_FRAMES
    ):
        raise ValueError("D104 report shape or status is invalid")
    if (
        report.get("source_transition_report_sha256") != transition_sha256
        or report.get("source_failover_report_sha256") != failover_sha256
    ):
        raise ValueError("D104 source report binding changed")
    if (
        transition.get("status") != "valid-backend-dynamic-transition"
        or failover.get("status") != "valid-openal-efx-fault-failover"
    ):
        raise ValueError("D100 or D102 source status changed")
    source_payloads = {
        "dry": dry_pcm,
        "efx": efx_pcm,
        "java_fdn": java_pcm,
    }
    for name, payload in source_payloads.items():
        if (
            len(payload) != OUTPUT_BYTES
            or transition.get(f"{name}_pcm_sha256") != _sha256(payload)
            or report.get("source_pcm_sha256", {}).get(name) != _sha256(payload)
        ):
            raise ValueError(f"{name} source PCM binding changed")
    cycles = failover.get("fault_cycles")
    if (
        not isinstance(cycles, list)
        or tuple(item.get("stage") for item in cycles) != FAULT_STAGES
    ):
        raise ValueError("D102 fault-cycle order changed")
    for cycle in cycles:
        if (
            cycle.get("fallback", {}).get("backend") != "JAVA_FDN"
            or cycle.get("fallback", {}).get("double_wet_path") is not False
            or cycle.get("recovery_runtime", {}).get("backend") != "OPENAL_EFX"
            or cycle.get("recovery_runtime", {}).get("double_wet_path") is not False
        ):
            raise ValueError("D102 exclusive ownership changed")
    if (
        report.get("splice_method")
        != "exclusive-backend-selection-no-crossfade"
        or report.get("warm_state_source_renders") is not True
        or report.get("cold_start_measured") is not False
        or report.get("ownership_intervals") != _expected_ownership()
    ):
        raise ValueError("D104 splice semantics changed")
    for key in (
        "captures_audio",
        "physical_endpoint_opened",
        "release_calibrated",
    ):
        if report.get(key) is not False:
            raise ValueError(f"{key} must be False")

    dry = np.frombuffer(dry_pcm, dtype="<i2").astype(np.float64)
    efx = np.frombuffer(efx_pcm, dtype="<i2").astype(np.float64)
    java = np.frombuffer(java_pcm, dtype="<i2").astype(np.float64)
    expected = efx.copy()
    for start, end in FALLBACK_WINDOWS:
        expected[start:end] = java[start:end]
    expected_i16 = expected.astype("<i2")
    expected_bytes = expected_i16.tobytes()
    if (
        len(output_pcm) != OUTPUT_BYTES
        or report.get("output_pcm_bytes") != OUTPUT_BYTES
        or report.get("output_pcm_sha256") != _sha256(output_pcm)
        or output_pcm != expected_bytes
    ):
        raise ValueError("D104 output is not the exclusive-owner splice")

    observed = report.get("boundaries")
    if not isinstance(observed, list) or len(observed) != len(BOUNDARIES):
        raise ValueError("D104 boundary manifest is invalid")
    recomputed = []
    for index, boundary in enumerate(BOUNDARIES):
        from_signal, to_signal = (
            (efx, java) if index % 2 == 0 else (java, efx)
        )
        local = np.concatenate(
            (
                np.abs(np.diff(from_signal[boundary - 2_000 : boundary - 100])),
                np.abs(np.diff(to_signal[boundary + 100 : boundary + 2_000])),
            )
        )
        step = float(abs(expected[boundary] - expected[boundary - 1]))
        p99 = float(np.percentile(local, 99.0))
        five_ms = 240
        before = _rms(
            expected[boundary - five_ms : boundary]
            - dry[boundary - five_ms : boundary]
        )
        after = _rms(
            expected[boundary : boundary + five_ms]
            - dry[boundary : boundary + five_ms]
        )
        twenty_ms = 960
        output_rms = _rms(expected[boundary : boundary + twenty_ms])
        dry_rms = _rms(dry[boundary : boundary + twenty_ms])
        values = {
            "frame": boundary,
            "direction": (
                "OPENAL_EFX_TO_JAVA_FDN"
                if index % 2 == 0
                else "JAVA_FDN_TO_OPENAL_EFX"
            ),
            "fault_stage": FAULT_STAGES[index // 2],
            "environment": ENVIRONMENTS[index // 2],
            "residual_step_absolute": step,
            "local_difference_p99": p99,
            "step_to_p99_ratio": step / max(p99, 1.0),
            "five_ms_wet_rms_ratio": after / max(before, 1.0e-12),
            "twenty_ms_output_to_dry_rms_ratio": output_rms
            / max(dry_rms, 1.0e-12),
            "longest_zero_run_samples": _longest_zero_run(
                expected_i16[boundary : boundary + twenty_ms]
            ),
        }
        item = observed[index]
        for key, value in values.items():
            if isinstance(value, float):
                if not math.isclose(
                    item.get(key, float("nan")),
                    value,
                    rel_tol=0.0,
                    abs_tol=1.0e-9,
                ):
                    raise ValueError(f"D104 boundary metric {key} changed")
            elif item.get(key) != value:
                raise ValueError(f"D104 boundary identity {key} changed")
        if values["step_to_p99_ratio"] > 2.0:
            raise ValueError("D104 resolved a software boundary click")
        if not 0.25 <= values["five_ms_wet_rms_ratio"] <= 4.0:
            raise ValueError("D104 wet energy changed abruptly")
        if (
            values["twenty_ms_output_to_dry_rms_ratio"] < 0.5
            or values["longest_zero_run_samples"] > 48
        ):
            raise ValueError("D104 resolved a twenty-ms dropout")
        recomputed.append(values)

    gates = report.get("gates", {})
    required_true = (
        "three_d102_fault_cycles_bound",
        "six_backend_directions_exercised",
        "exclusive_wet_owner",
        "no_resolved_software_boundary_click",
        "five_ms_wet_energy_ratio_bounded",
        "no_twenty_ms_dropout_resolved",
    )
    if any(gates.get(key) is not True for key in required_true):
        raise ValueError("D104 positive gate changed")
    if (
        gates.get("physical_playback_or_capture_opened") is not False
        or gates.get("release_calibrated") is not False
    ):
        raise ValueError("D104 claim-boundary gate changed")
    return {
        "schema_version": 1,
        "status": "valid-backend-failover-continuity-verification",
        "source_report_sha256": report_sha256,
        "transition_report_sha256": transition_sha256,
        "failover_report_sha256": failover_sha256,
        "output_pcm_sha256": _sha256(output_pcm),
        "fault_cycles_bound": 3,
        "backend_boundaries": len(recomputed),
        "maximum_step_to_p99_ratio": max(
            item["step_to_p99_ratio"] for item in recomputed
        ),
        "minimum_twenty_ms_output_to_dry_rms_ratio": min(
            item["twenty_ms_output_to_dry_rms_ratio"] for item in recomputed
        ),
        "exclusive_wet_owner": True,
        "cold_start_measured": False,
        "captures_audio": False,
        "release_calibrated": False,
        "claim_boundary": (
            "Verifies the deterministic warm-state exclusive-owner splice. "
            "It is not evidence for cold-start DSP continuity, endpoint "
            "continuity, audibility, or release calibration."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--output-pcm", type=Path, required=True)
    parser.add_argument("--transition-report", type=Path, required=True)
    parser.add_argument("--dry-pcm", type=Path, required=True)
    parser.add_argument("--efx-pcm", type=Path, required=True)
    parser.add_argument("--java-pcm", type=Path, required=True)
    parser.add_argument("--failover-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    report_bytes = args.report.read_bytes()
    transition_bytes = args.transition_report.read_bytes()
    failover_bytes = args.failover_report.read_bytes()
    result = verify(
        json.loads(report_bytes),
        _sha256(report_bytes),
        args.output_pcm.read_bytes(),
        json.loads(transition_bytes),
        _sha256(transition_bytes),
        args.dry_pcm.read_bytes(),
        args.efx_pcm.read_bytes(),
        args.java_pcm.read_bytes(),
        json.loads(failover_bytes),
        _sha256(failover_bytes),
    )
    rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(rendered, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
