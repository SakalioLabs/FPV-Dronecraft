#!/usr/bin/env python3
"""Verify D106 bounded Java FDN history pre-roll quality and budget."""

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
HISTORY_END_FRAME = 96_000
EVALUATION_FRAMES = 12_000
SIGNAL_BYTES = EVALUATION_FRAMES * 2
REFERENCE_HISTORY_MS = 1_000
PREROLL_MILLISECONDS = (0, 50, 100, 250, 500, 1_000)
ENVIRONMENTS = ("closed", "partial", "open")
EXPECTED_SIDECAR_BYTES = (
    len(ENVIRONMENTS)
    * (len(PREROLL_MILLISECONDS) + 1)
    * SIGNAL_BYTES
)
MINIMUM_CORRELATION = 0.90
MINIMUM_RMS_RATIO = 0.90
MAXIMUM_RMS_RATIO = 1.10
MAXIMUM_NORMALIZED_RMSE = 0.50
MAXIMUM_P99_MS = (2_048 / SAMPLE_RATE) * 1_000 * 0.25
METRIC_TOLERANCE = 5.0e-4


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _rms(signal: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(signal))))


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
        raise ValueError(f"{layer} trace chunks are incomplete")
    combined = bytearray()
    for chunk in chunks:
        offset = chunk.get("pcm_offset")
        count = chunk.get("pcm_bytes")
        if (
            not isinstance(offset, int)
            or not isinstance(count, int)
            or offset < 0
            or count != SEGMENT_FRAMES * 2
            or offset + count > len(trace_pcm)
            or manifest.get((layer, chunk.get("sequence")))
            != chunk.get("pcm_sha256")
        ):
            raise ValueError(f"{layer} trace input detached")
        payload = trace_pcm[offset : offset + count]
        if _sha256(payload) != chunk.get("pcm_sha256"):
            raise ValueError(f"{layer} trace PCM hash changed")
        combined.extend(payload)
    result = bytes(combined)
    if _sha256(result) != transition.get(f"{layer}_pcm_sha256"):
        raise ValueError(f"{layer} combined input hash changed")
    return result


def _payload(
    item: dict[str, Any],
    sidecar: bytes,
    occupied: list[tuple[int, int]],
) -> np.ndarray:
    offset = item.get("pcm_offset")
    count = item.get("pcm_bytes")
    if (
        not isinstance(offset, int)
        or not isinstance(count, int)
        or offset < 0
        or count != SIGNAL_BYTES
        or offset + count > len(sidecar)
    ):
        raise ValueError("D106 sidecar range is invalid")
    end = offset + count
    if any(offset < other_end and end > other_start for other_start, other_end in occupied):
        raise ValueError("D106 sidecar ranges overlap")
    occupied.append((offset, end))
    payload = sidecar[offset:end]
    if _sha256(payload) != item.get("pcm_sha256"):
        raise ValueError("D106 sidecar range hash changed")
    return np.frombuffer(payload, dtype="<i2").astype(np.float64)


def _passes(item: dict[str, Any]) -> bool:
    return (
        item["correlation"] >= MINIMUM_CORRELATION
        and MINIMUM_RMS_RATIO
        <= item["rms_ratio"]
        <= MAXIMUM_RMS_RATIO
        and item["normalized_rmse"] <= MAXIMUM_NORMALIZED_RMSE
        and item["p99_ms"] <= MAXIMUM_P99_MS
    )


def verify(
    report: dict[str, Any],
    report_sha256: str,
    sidecar: bytes,
    transition: dict[str, Any],
    transition_sha256: str,
    trace: dict[str, Any],
    trace_sha256: str,
    trace_pcm: bytes,
) -> dict[str, Any]:
    if (
        report.get("schema_version") != 1
        or report.get("status") != "valid-fdn-history-preroll-sweep"
        or report.get("sample_rate_hz") != SAMPLE_RATE
        or report.get("history_end_frame") != HISTORY_END_FRAME
        or report.get("evaluation_frames") != EVALUATION_FRAMES
        or report.get("reference_history_ms") != REFERENCE_HISTORY_MS
    ):
        raise ValueError("D106 report shape or status is invalid")
    if (
        report.get("source_transition_report_sha256") != transition_sha256
        or report.get("source_trace_report_sha256") != trace_sha256
        or report.get("source_trace_pcm_sha256") != _sha256(trace_pcm)
    ):
        raise ValueError("D106 source report binding changed")
    if (
        transition.get("status") != "valid-backend-dynamic-transition"
        or trace.get("status") != "valid-doppler-production-chunk-trace"
        or trace.get("pcm_sha256") != _sha256(trace_pcm)
    ):
        raise ValueError("D100/D094 source status changed")
    motor = _reconstruct_layer("motor", transition, trace, trace_pcm)
    propeller = _reconstruct_layer(
        "propeller", transition, trace, trace_pcm
    )
    if (
        report.get("motor_pcm_sha256") != _sha256(motor)
        or report.get("propeller_pcm_sha256") != _sha256(propeller)
    ):
        raise ValueError("D106 production input binding changed")
    if (
        len(sidecar) != EXPECTED_SIDECAR_BYTES
        or report.get("sidecar_bytes") != EXPECTED_SIDECAR_BYTES
        or report.get("sidecar_sha256") != _sha256(sidecar)
        or report.get("sidecar_format") != "s16le-mono-48000"
    ):
        raise ValueError("D106 sidecar identity changed")
    thresholds = report.get("thresholds", {})
    expected_thresholds = {
        "minimum_correlation": MINIMUM_CORRELATION,
        "minimum_rms_ratio": MINIMUM_RMS_RATIO,
        "maximum_rms_ratio": MAXIMUM_RMS_RATIO,
        "maximum_normalized_rmse": MAXIMUM_NORMALIZED_RMSE,
        "maximum_p99_ms": MAXIMUM_P99_MS,
    }
    for key, value in expected_thresholds.items():
        if not math.isclose(
            thresholds.get(key, float("nan")),
            value,
            rel_tol=0.0,
            abs_tol=1.0e-12,
        ):
            raise ValueError("D106 registered threshold changed")
    if (
        report.get("warmup_iterations") != 10
        or report.get("measured_iterations") != 50
    ):
        raise ValueError("D106 timing sample count changed")
    for key in (
        "physical_endpoint_opened",
        "captures_audio",
        "release_calibrated",
    ):
        if report.get(key) is not False:
            raise ValueError(f"{key} must be False")
    if report.get("mixed_input_history_only") is not True:
        raise ValueError("D106 history semantics changed")

    source_environments = transition.get("environments", [])[:3]
    observed_environments = report.get("environments")
    if (
        not isinstance(observed_environments, list)
        or [item.get("name") for item in observed_environments]
        != list(ENVIRONMENTS)
        or len(source_environments) != len(ENVIRONMENTS)
    ):
        raise ValueError("D106 environment order changed")
    for source, observed in zip(source_environments, observed_environments):
        if source.get("wet_gain") != observed.get("wet_gain"):
            raise ValueError("D106 environment wet control detached")
        for band in ("low", "mid", "high"):
            if source.get("rt60_seconds", {}).get(band) != observed.get(
                "rt60_seconds", {}
            ).get(band):
                raise ValueError("D106 environment RT60 detached")

    occupied: list[tuple[int, int]] = []
    references_manifest = report.get("references")
    if (
        not isinstance(references_manifest, list)
        or [item.get("environment") for item in references_manifest]
        != list(ENVIRONMENTS)
        or any(
            item.get("pre_roll_ms") != REFERENCE_HISTORY_MS
            for item in references_manifest
        )
    ):
        raise ValueError("D106 reference manifest changed")
    references = {
        item["environment"]: _payload(item, sidecar, occupied)
        for item in references_manifest
    }
    cases = report.get("cases")
    if not isinstance(cases, list) or len(cases) != (
        len(ENVIRONMENTS) * len(PREROLL_MILLISECONDS)
    ):
        raise ValueError("D106 case matrix is incomplete")
    expected_order = [
        (environment, pre_roll)
        for environment in ENVIRONMENTS
        for pre_roll in PREROLL_MILLISECONDS
    ]
    if [
        (item.get("environment"), item.get("pre_roll_ms"))
        for item in cases
    ] != expected_order:
        raise ValueError("D106 case order changed")

    verified_cases = []
    for item in cases:
        candidate = _payload(item, sidecar, occupied)
        reference = references[item["environment"]]
        denominator = math.sqrt(
            float(np.dot(candidate, candidate))
            * float(np.dot(reference, reference))
        )
        correlation = float(np.dot(candidate, reference)) / denominator
        rms_ratio = _rms(candidate) / _rms(reference)
        normalized_rmse = _rms(candidate - reference) / _rms(reference)
        for key, value in (
            ("correlation", correlation),
            ("rms_ratio", rms_ratio),
            ("normalized_rmse", normalized_rmse),
        ):
            if not math.isclose(
                item.get(key, float("nan")),
                value,
                rel_tol=0.0,
                abs_tol=METRIC_TOLERANCE,
            ):
                raise ValueError(f"D106 {key} metric changed")
        p50 = item.get("p50_ms")
        p95 = item.get("p95_ms")
        p99 = item.get("p99_ms")
        if (
            not all(
                isinstance(value, (int, float))
                and math.isfinite(value)
                and value >= 0.0
                for value in (p50, p95, p99)
            )
            or not p50 <= p95 <= p99
        ):
            raise ValueError("D106 timing distribution is invalid")
        normalized = {
            **item,
            "correlation": correlation,
            "rms_ratio": rms_ratio,
            "normalized_rmse": normalized_rmse,
        }
        expected_pass = _passes(normalized)
        if item.get("passes") is not expected_pass:
            raise ValueError("D106 pass flag detached from metrics")
        if item["pre_roll_ms"] == REFERENCE_HISTORY_MS and not np.array_equal(
            candidate, reference
        ):
            raise ValueError("D106 1000-ms candidate differs from reference")
        verified_cases.append(normalized)
    if sorted(occupied) != [
        (offset, offset + SIGNAL_BYTES)
        for offset in range(0, EXPECTED_SIDECAR_BYTES, SIGNAL_BYTES)
    ]:
        raise ValueError("D106 sidecar coverage is not contiguous")

    selected = -1
    for pre_roll in PREROLL_MILLISECONDS:
        matches = [
            item
            for item in verified_cases
            if item["pre_roll_ms"] == pre_roll
        ]
        if len(matches) == len(ENVIRONMENTS) and all(
            _passes(item) for item in matches
        ):
            selected = pre_roll
            break
    if (
        selected < 0
        or report.get("selected_pre_roll_ms") != selected
        or report.get("selection_available") is not True
    ):
        raise ValueError("D106 selected pre-roll is not the minimum pass")
    selected_cases = [
        item
        for item in verified_cases
        if item["pre_roll_ms"] == selected
    ]
    maximum_selected_p99 = max(item["p99_ms"] for item in selected_cases)
    buffer_duration_ms = 2_048 / SAMPLE_RATE * 1_000
    return {
        "schema_version": 1,
        "status": "valid-fdn-history-preroll-sweep-verification",
        "source_report_sha256": report_sha256,
        "transition_report_sha256": transition_sha256,
        "trace_report_sha256": trace_sha256,
        "sidecar_sha256": _sha256(sidecar),
        "selected_pre_roll_ms": selected,
        "selected_minimum_correlation": min(
            item["correlation"] for item in selected_cases
        ),
        "selected_rms_ratio_range": [
            min(item["rms_ratio"] for item in selected_cases),
            max(item["rms_ratio"] for item in selected_cases),
        ],
        "selected_maximum_normalized_rmse": max(
            item["normalized_rmse"] for item in selected_cases
        ),
        "selected_maximum_p99_ms": maximum_selected_p99,
        "selected_maximum_p99_buffer_fraction": (
            maximum_selected_p99 / buffer_duration_ms
        ),
        "gates": {
            "production_inputs_and_controls_hash_bound": True,
            "complete_three_environment_sweep": True,
            "selected_is_minimum_all_environment_pass": True,
            "selected_quality_gates_passed": True,
            "selected_p99_below_25_percent_buffer": (
                maximum_selected_p99 <= MAXIMUM_P99_MS
            ),
            "physical_playback_or_capture_opened": False,
            "release_calibrated": False,
        },
        "captures_audio": False,
        "release_calibrated": False,
        "claim_boundary": (
            "Verifies a pure Java single-listener FDN pre-roll sweep. "
            "The selected duration is relative to a bounded 1000-ms "
            "reference and this host's in-process timing, not infinite "
            "warm-state equivalence, Minecraft scheduling, endpoint "
            "behavior, audibility, or release calibration."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--sidecar", type=Path, required=True)
    parser.add_argument("--transition-report", type=Path, required=True)
    parser.add_argument("--trace-report", type=Path, required=True)
    parser.add_argument("--trace-pcm", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    report_bytes = args.report.read_bytes()
    transition_bytes = args.transition_report.read_bytes()
    trace_bytes = args.trace_report.read_bytes()
    result = verify(
        json.loads(report_bytes),
        _sha256(report_bytes),
        args.sidecar.read_bytes(),
        json.loads(transition_bytes),
        _sha256(transition_bytes),
        json.loads(trace_bytes),
        _sha256(trace_bytes),
        args.trace_pcm.read_bytes(),
    )
    rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(rendered, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
