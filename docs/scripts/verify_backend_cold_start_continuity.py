#!/usr/bin/env python3
"""Verify D105 cold-start EFX/Java lifecycle rendering and tail loss."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any

import numpy as np


SAMPLE_RATE = 48_000
SEGMENT_FRAMES = 48_000
SEGMENT_COUNT = 4
RENDER_FRAMES = SEGMENT_FRAMES * SEGMENT_COUNT
OUTPUT_BYTES = RENDER_FRAMES * 2
FAULT_STAGES = ("resource-create", "parameter-write", "source-route")
FALLBACK_WINDOWS = ((12_000, 24_000), (60_000, 72_000), (108_000, 120_000))
BOUNDARIES = tuple(value for pair in FALLBACK_WINDOWS for value in pair)
EXPECTED_FDN_DELAYS = (1_423, 1_613, 1_789, 1_999, 2_131, 2_347, 2_539, 2_741)
MAXIMUM_ONSET_DELAY_SAMPLES = 2_400
TAIL_EQUIVALENCE_RATIO = 0.5


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


def _reconstruct_layer(
    layer: str,
    transition: dict[str, Any],
    trace: dict[str, Any],
    trace_pcm: bytes,
) -> bytes:
    chunks = sorted(
        [
            item
            for item in trace.get("chunks", [])
            if item.get("layer") == layer
        ],
        key=lambda item: item.get("sequence", -1),
    )[:SEGMENT_COUNT]
    manifest = {
        (item.get("layer"), item.get("sequence")): item.get("pcm_sha256")
        for item in transition.get("inputs", [])
    }
    if len(chunks) != SEGMENT_COUNT:
        raise ValueError(f"{layer} source chunks are incomplete")
    combined = bytearray()
    for chunk in chunks:
        offset = chunk.get("pcm_offset")
        count = chunk.get("pcm_bytes")
        identity = (layer, chunk.get("sequence"))
        if (
            not isinstance(offset, int)
            or not isinstance(count, int)
            or offset < 0
            or count != SEGMENT_FRAMES * 2
            or offset + count > len(trace_pcm)
            or manifest.get(identity) != chunk.get("pcm_sha256")
        ):
            raise ValueError(f"{layer} source chunk detached")
        payload = trace_pcm[offset : offset + count]
        if _sha256(payload) != chunk.get("pcm_sha256"):
            raise ValueError(f"{layer} source PCM hash changed")
        combined.extend(payload)
    result = bytes(combined)
    if _sha256(result) != transition.get(f"{layer}_pcm_sha256"):
        raise ValueError(f"{layer} combined source hash changed")
    return result


def _require_false(report: dict[str, Any], *keys: str) -> None:
    for key in keys:
        if report.get(key) is not False:
            raise ValueError(f"{key} must be False")


def verify(
    report: dict[str, Any],
    report_sha256: str,
    cold_pcm: bytes,
    dry_pcm: bytes,
    java_wet_pcm: bytes,
    transition: dict[str, Any],
    transition_sha256: str,
    transition_dry_pcm: bytes,
    trace: dict[str, Any],
    trace_sha256: str,
    trace_pcm: bytes,
    failover: dict[str, Any],
    failover_sha256: str,
    warm_report: dict[str, Any],
    warm_report_sha256: str,
    warm_pcm: bytes,
) -> dict[str, Any]:
    if (
        report.get("schema_version") != 1
        or report.get("status") != "valid-backend-cold-start-render"
        or report.get("render_format") != "s16le-mono-48000"
        or report.get("sample_rate_hz") != SAMPLE_RATE
        or report.get("render_frames") != RENDER_FRAMES
    ):
        raise ValueError("D105 report shape or status is invalid")
    if (
        report.get("source_transition_report_sha256") != transition_sha256
        or report.get("source_trace_report_sha256") != trace_sha256
        or report.get("source_trace_pcm_sha256") != _sha256(trace_pcm)
        or report.get("source_failover_report_sha256") != failover_sha256
    ):
        raise ValueError("D105 source report binding changed")
    if (
        transition.get("status") != "valid-backend-dynamic-transition"
        or trace.get("status") != "valid-doppler-production-chunk-trace"
        or trace.get("pcm_sha256") != _sha256(trace_pcm)
        or failover.get("status") != "valid-openal-efx-fault-failover"
    ):
        raise ValueError("D100/D094/D102 source status changed")
    motor = _reconstruct_layer("motor", transition, trace, trace_pcm)
    propeller = _reconstruct_layer(
        "propeller", transition, trace, trace_pcm
    )
    if (
        report.get("motor_pcm_sha256") != _sha256(motor)
        or report.get("propeller_pcm_sha256") != _sha256(propeller)
    ):
        raise ValueError("D105 production input binding changed")
    payloads = {
        "cold": cold_pcm,
        "dry": dry_pcm,
        "java_wet": java_wet_pcm,
    }
    for name, payload in payloads.items():
        if (
            len(payload) != OUTPUT_BYTES
            or report.get(f"{name}_pcm_bytes") != OUTPUT_BYTES
            or report.get(f"{name}_pcm_sha256") != _sha256(payload)
        ):
            raise ValueError(f"D105 {name} PCM identity changed")
    if (
        dry_pcm != transition_dry_pcm
        or transition.get("dry_pcm_sha256") != _sha256(dry_pcm)
    ):
        raise ValueError("D105 dry control detached from D100")
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
            or cycle.get("recovery_runtime", {}).get("backend")
            != "OPENAL_EFX"
            or cycle.get("recovery_runtime", {}).get("double_wet_path")
            is not False
        ):
            raise ValueError("D102 exclusive ownership changed")
    if (
        report.get("efx_resources_created_at_recovery") is not True
        or report.get("java_fdn_created_at_fallback") is not True
        or report.get("backend_state_preheated") is not False
        or report.get("splice_method")
        != "cold-start-exclusive-owner-no-crossfade"
        or report.get("exclusive_wet_owner") is not True
    ):
        raise ValueError("D105 cold-start lifecycle semantics changed")
    if any(
        report.get(key) != 0
        for key in ("al_error", "alc_error", "dry_al_error", "dry_alc_error")
    ):
        raise ValueError("D105 OpenAL error is nonzero")
    _require_false(
        report,
        "physical_endpoint_opened",
        "captures_audio",
        "release_calibrated",
    )

    source_environments = transition.get("environments")
    observed_environments = report.get("environments")
    if (
        not isinstance(source_environments, list)
        or not isinstance(observed_environments, list)
        or len(source_environments) != SEGMENT_COUNT
        or len(observed_environments) != SEGMENT_COUNT
    ):
        raise ValueError("D105 environment manifest is incomplete")
    for source, observed in zip(source_environments, observed_environments):
        for key in ("segment", "snapshot_generation", "wet_gain"):
            if observed.get(key) != source.get(key):
                raise ValueError("D105 environment control detached")
        for band in ("low", "mid", "high"):
            if observed.get("rt60_seconds", {}).get(band) != source.get(
                "rt60_seconds", {}
            ).get(band):
                raise ValueError("D105 RT60 control detached")

    wet = np.frombuffer(java_wet_pcm, dtype="<i2")
    window_mask = np.zeros(RENDER_FRAMES, dtype=bool)
    windows = report.get("java_fallback_windows")
    if not isinstance(windows, list) or len(windows) != len(FALLBACK_WINDOWS):
        raise ValueError("D105 Java window manifest is invalid")
    onset_delays = []
    for index, ((start, end), item) in enumerate(
        zip(FALLBACK_WINDOWS, windows)
    ):
        if (
            item.get("fault_stage") != FAULT_STAGES[index]
            or item.get("start_frame") != start
            or item.get("end_frame") != end
            or tuple(item.get("fdn_delay_samples", []))
            != EXPECTED_FDN_DELAYS
        ):
            raise ValueError("D105 Java lifecycle window changed")
        window_mask[start:end] = True
        nonzero = np.flatnonzero(wet[start:end])
        if nonzero.size == 0:
            raise ValueError("D105 Java FDN never produced wet output")
        first = start + int(nonzero[0])
        onset = first - start
        if (
            item.get("first_nonzero_wet_frame") != first
            or item.get("onset_delay_samples") != onset
            or onset != EXPECTED_FDN_DELAYS[0]
        ):
            raise ValueError("D105 Java wet onset detached from delay lines")
        if np.any(wet[start:first] != 0):
            raise ValueError("D105 Java wet output appeared before onset")
        onset_delays.append(onset)
    if np.any(wet[~window_mask] != 0):
        raise ValueError("D105 Java wet output escaped fallback ownership")

    if (
        warm_report.get("status")
        != "valid-backend-failover-continuity-diagnostic"
        or warm_report.get("warm_state_source_renders") is not True
        or warm_report.get("cold_start_measured") is not False
        or warm_report.get("output_pcm_sha256") != _sha256(warm_pcm)
        or warm_report.get("source_transition_report_sha256")
        != transition_sha256
        or warm_report.get("source_failover_report_sha256")
        != failover_sha256
    ):
        raise ValueError("D104 warm-state control binding changed")

    cold = np.frombuffer(cold_pcm, dtype="<i2").astype(np.float64)
    dry = np.frombuffer(dry_pcm, dtype="<i2").astype(np.float64)
    warm = np.frombuffer(warm_pcm, dtype="<i2").astype(np.float64)
    metrics = []
    for index, boundary in enumerate(BOUNDARIES):
        local = np.concatenate(
            (
                np.abs(np.diff(cold[boundary - 2_000 : boundary - 100])),
                np.abs(np.diff(cold[boundary + 100 : boundary + 2_000])),
            )
        )
        step = float(abs(cold[boundary] - cold[boundary - 1]))
        local_p99 = float(np.percentile(local, 99.0))
        twenty_ms = 960
        total_ratio = _rms(cold[boundary : boundary + twenty_ms]) / max(
            _rms(dry[boundary : boundary + twenty_ms]), 1.0e-12
        )
        hundred_ms = 4_800
        cold_wet = _rms(
            cold[boundary : boundary + hundred_ms]
            - dry[boundary : boundary + hundred_ms]
        )
        warm_wet = _rms(
            warm[boundary : boundary + hundred_ms]
            - dry[boundary : boundary + hundred_ms]
        )
        metrics.append(
            {
                "frame": boundary,
                "direction": (
                    "OPENAL_EFX_TO_JAVA_FDN"
                    if index % 2 == 0
                    else "JAVA_FDN_TO_OPENAL_EFX"
                ),
                "fault_stage": FAULT_STAGES[index // 2],
                "residual_step_absolute": step,
                "local_difference_p99": local_p99,
                "step_to_p99_ratio": step / max(local_p99, 1.0),
                "twenty_ms_output_to_dry_rms_ratio": total_ratio,
                "hundred_ms_cold_wet_rms": cold_wet,
                "hundred_ms_warm_wet_rms": warm_wet,
                "cold_to_warm_wet_rms_ratio": cold_wet
                / max(warm_wet, 1.0e-12),
                "longest_zero_run_samples": _longest_zero_run(
                    cold[boundary : boundary + twenty_ms]
                ),
            }
        )
    no_click = all(item["step_to_p99_ratio"] <= 2.0 for item in metrics)
    no_total_dropout = all(
        item["twenty_ms_output_to_dry_rms_ratio"] >= 0.5
        and item["longest_zero_run_samples"] <= 48
        for item in metrics
    )
    onset_bounded = max(onset_delays) <= MAXIMUM_ONSET_DELAY_SAMPLES
    tail_equivalent = all(
        item["cold_to_warm_wet_rms_ratio"] >= TAIL_EQUIVALENCE_RATIO
        for item in metrics
    )
    java_fallback_tail_equivalent = all(
        item["cold_to_warm_wet_rms_ratio"] >= TAIL_EQUIVALENCE_RATIO
        for item in metrics
        if item["direction"] == "OPENAL_EFX_TO_JAVA_FDN"
    )
    efx_recovery_tail_equivalent = all(
        item["cold_to_warm_wet_rms_ratio"] >= TAIL_EQUIVALENCE_RATIO
        for item in metrics
        if item["direction"] == "JAVA_FDN_TO_OPENAL_EFX"
    )
    if not no_click:
        raise ValueError("D105 resolved a software boundary click")
    if not no_total_dropout:
        raise ValueError("D105 resolved a total-output dropout")
    if not onset_bounded:
        raise ValueError("D105 Java wet onset exceeds 50 ms")

    return {
        "schema_version": 1,
        "status": "valid-backend-cold-start-continuity-verification",
        "source_report_sha256": report_sha256,
        "transition_report_sha256": transition_sha256,
        "trace_report_sha256": trace_sha256,
        "failover_report_sha256": failover_sha256,
        "warm_report_sha256": warm_report_sha256,
        "pcm_sha256": {
            "cold": _sha256(cold_pcm),
            "dry": _sha256(dry_pcm),
            "java_wet": _sha256(java_wet_pcm),
            "warm": _sha256(warm_pcm),
        },
        "boundary_metrics": metrics,
        "maximum_step_to_p99_ratio": max(
            item["step_to_p99_ratio"] for item in metrics
        ),
        "minimum_twenty_ms_output_to_dry_rms_ratio": min(
            item["twenty_ms_output_to_dry_rms_ratio"] for item in metrics
        ),
        "maximum_java_wet_onset_delay_samples": max(onset_delays),
        "maximum_java_wet_onset_delay_ms": (
            1_000.0 * max(onset_delays) / SAMPLE_RATE
        ),
        "minimum_cold_to_warm_wet_rms_ratio": min(
            item["cold_to_warm_wet_rms_ratio"] for item in metrics
        ),
        "gates": {
            "production_inputs_and_controls_hash_bound": True,
            "three_cold_start_fault_cycles_bound": True,
            "exclusive_wet_owner": True,
            "no_resolved_software_boundary_click": no_click,
            "no_total_output_dropout_resolved": no_total_dropout,
            "java_wet_onset_below_50_ms": onset_bounded,
            "cold_and_warm_tail_equivalent": tail_equivalent,
            "java_fallback_tail_equivalent":
                java_fallback_tail_equivalent,
            "efx_recovery_tail_equivalent":
                efx_recovery_tail_equivalent,
            "java_history_preroll_research_required":
                not java_fallback_tail_equivalent,
            "efx_recovery_tail_policy_required":
                not efx_recovery_tail_equivalent,
            "physical_playback_or_capture_opened": False,
            "release_calibrated": False,
        },
        "backend_state_preheated": False,
        "captures_audio": False,
        "release_calibrated": False,
        "claim_boundary": (
            "Cold-start lifecycle and warm-state software renders are "
            "compared in isolated OpenAL Soft. Resolved wet-tail loss is an "
            "engineering signal for history pre-roll research, not proof of "
            "audibility or physical endpoint behavior."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--cold-pcm", type=Path, required=True)
    parser.add_argument("--dry-pcm", type=Path, required=True)
    parser.add_argument("--java-wet-pcm", type=Path, required=True)
    parser.add_argument("--transition-report", type=Path, required=True)
    parser.add_argument("--transition-dry-pcm", type=Path, required=True)
    parser.add_argument("--trace-report", type=Path, required=True)
    parser.add_argument("--trace-pcm", type=Path, required=True)
    parser.add_argument("--failover-report", type=Path, required=True)
    parser.add_argument("--warm-report", type=Path, required=True)
    parser.add_argument("--warm-pcm", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    report_bytes = args.report.read_bytes()
    transition_bytes = args.transition_report.read_bytes()
    trace_bytes = args.trace_report.read_bytes()
    failover_bytes = args.failover_report.read_bytes()
    warm_bytes = args.warm_report.read_bytes()
    result = verify(
        json.loads(report_bytes),
        _sha256(report_bytes),
        args.cold_pcm.read_bytes(),
        args.dry_pcm.read_bytes(),
        args.java_wet_pcm.read_bytes(),
        json.loads(transition_bytes),
        _sha256(transition_bytes),
        args.transition_dry_pcm.read_bytes(),
        json.loads(trace_bytes),
        _sha256(trace_bytes),
        args.trace_pcm.read_bytes(),
        json.loads(failover_bytes),
        _sha256(failover_bytes),
        json.loads(warm_bytes),
        _sha256(warm_bytes),
        args.warm_pcm.read_bytes(),
    )
    rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(rendered, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
