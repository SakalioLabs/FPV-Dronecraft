#!/usr/bin/env python3
"""Verify exact-boundary EFX and Java FDN parameter updates."""

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
BOUNDARIES = (48_000, 96_000, 144_000)
ENVIRONMENT_ORDER = ("closed", "partial", "open", "closed")
MAXIMUM_LAG = 1_024


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _rms(signal: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(signal))))


def _best_lag(
    output: np.ndarray, expected: np.ndarray
) -> tuple[float, int]:
    best = -1.0
    best_lag = 0
    for lag in range(-MAXIMUM_LAG, MAXIMUM_LAG + 1):
        if lag >= 0:
            observed = output[lag:]
            source = expected[: expected.size - lag]
        else:
            observed = output[: output.size + lag]
            source = expected[-lag:]
        denominator = math.sqrt(
            float(np.dot(observed, observed))
            * float(np.dot(source, source))
        )
        correlation = (
            0.0
            if denominator == 0.0
            else float(np.dot(observed, source)) / denominator
        )
        if correlation > best:
            best = correlation
            best_lag = lag
    return best, best_lag


def _require_flags(report: dict[str, Any]) -> None:
    for key in (
        "fdn_tail_cleared_at_boundary",
        "physical_playback_device_opened",
        "capture_device_opened",
        "real_audio_capture",
        "release_calibrated",
    ):
        if report.get(key) is not False:
            raise ValueError(f"{key} must be False")
    for key in (
        "minecraft_context_unchanged",
        "minecraft_device_unchanged",
        "minecraft_sound_thread_unchanged",
    ):
        if report.get(key) is not True:
            raise ValueError(f"{key} must be True")


def _portal_controls(portal: dict[str, Any]) -> dict[str, dict[str, Any]]:
    environments = portal.get("backend_matrix_environments")
    if not isinstance(environments, list):
        raise ValueError("portal matrix controls are missing")
    result = {item.get("name"): item for item in environments}
    if set(result) != {"closed", "partial", "open"}:
        raise ValueError("portal matrix controls are incomplete")
    return result


def _controls_match(
    observed: dict[str, Any], source: dict[str, Any]
) -> bool:
    for band in ("low", "mid", "high"):
        if not math.isclose(
            observed.get("rt60_seconds", {}).get(band, -1.0),
            source.get("rt60_seconds", {}).get(band, -2.0),
            rel_tol=0.0,
            abs_tol=1.0e-8,
        ):
            return False
    return math.isclose(
        observed.get("wet_gain", -1.0),
        source.get("wet_gain", -2.0),
        rel_tol=0.0,
        abs_tol=1.0e-8,
    )


def _trace_chunks(
    report: dict[str, Any],
    trace: dict[str, Any],
    trace_pcm: bytes,
) -> tuple[np.ndarray, np.ndarray]:
    observed_inputs = report.get("inputs")
    if not isinstance(observed_inputs, list) or len(observed_inputs) != 8:
        raise ValueError("transition input manifest is invalid")
    combined: dict[str, list[np.ndarray]] = {"motor": [], "propeller": []}
    combined_bytes: dict[str, bytearray] = {
        "motor": bytearray(),
        "propeller": bytearray(),
    }
    for layer in ("motor", "propeller"):
        chunks = sorted(
            [
                item
                for item in trace.get("chunks", [])
                if item.get("layer") == layer
            ],
            key=lambda item: item.get("sequence", -1),
        )[:SEGMENT_COUNT]
        observed = [
            item for item in observed_inputs if item.get("layer") == layer
        ]
        if len(chunks) != SEGMENT_COUNT or len(observed) != SEGMENT_COUNT:
            raise ValueError(f"{layer} transition chunks are incomplete")
        for chunk, identity in zip(chunks, observed):
            offset = chunk.get("pcm_offset")
            count = chunk.get("pcm_bytes")
            if (
                identity.get("sequence") != chunk.get("sequence")
                or identity.get("pcm_sha256") != chunk.get("pcm_sha256")
                or not isinstance(offset, int)
                or not isinstance(count, int)
                or offset < 0
                or count != SEGMENT_FRAMES * 2
                or offset + count > len(trace_pcm)
            ):
                raise ValueError(f"{layer} transition input detached")
            payload = trace_pcm[offset : offset + count]
            if _sha256(payload) != chunk.get("pcm_sha256"):
                raise ValueError(f"{layer} transition PCM hash changed")
            combined_bytes[layer].extend(payload)
            combined[layer].append(
                np.frombuffer(payload, dtype="<i2").astype(np.float64)
            )
        if (
            _sha256(bytes(combined_bytes[layer]))
            != report.get(f"{layer}_pcm_sha256")
        ):
            raise ValueError(f"{layer} combined PCM hash changed")
    return (
        np.concatenate(combined["motor"]),
        np.concatenate(combined["propeller"]),
    )


def _boundary_metrics(residual: np.ndarray, boundary: int) -> dict[str, float]:
    differences = np.diff(residual)
    local = np.concatenate(
        (
            differences[boundary - 1_000 : boundary - 100],
            differences[boundary + 100 : boundary + 1_000],
        )
    )
    step = float(abs(differences[boundary - 1]))
    local_p99 = float(np.percentile(np.abs(local), 99.0))
    window = 240
    before_rms = _rms(residual[boundary - window : boundary])
    after_rms = _rms(residual[boundary : boundary + window])
    ratio = after_rms / before_rms
    return {
        "frame": boundary,
        "residual_step_absolute": step,
        "local_difference_p99": local_p99,
        "five_ms_before_rms": before_rms,
        "five_ms_after_rms": after_rms,
        "five_ms_rms_ratio": ratio,
    }


def verify(
    report: dict[str, Any],
    report_sha256: str,
    dry_pcm: bytes,
    efx_pcm: bytes,
    java_pcm: bytes,
    trace: dict[str, Any],
    trace_sha256: str,
    trace_pcm: bytes,
    portal: dict[str, Any],
    portal_sha256: str,
) -> dict[str, Any]:
    if report.get("schema_version") != 1:
        raise ValueError("unsupported transition schema")
    if report.get("status") != "valid-backend-dynamic-transition":
        raise ValueError("transition status is invalid")
    if (
        report.get("render_format") != "s16le-mono-48000"
        or report.get("segment_frames") != SEGMENT_FRAMES
        or report.get("segment_count") != SEGMENT_COUNT
        or report.get("render_frames") != RENDER_FRAMES
        or report.get("boundary_frames") != list(BOUNDARIES)
    ):
        raise ValueError("transition render shape is invalid")
    if (
        report.get("efx_parameter_update")
        != "immediate-production-write"
        or report.get("java_fdn_parameter_update")
        != "production-0.2-second-exponential-smoothing"
    ):
        raise ValueError("transition update semantics changed")
    _require_flags(report)
    if (
        report.get("minecraft_sound_thread") != "Sound engine"
        or report.get("loopback_worker_thread")
        != "Dronecraft backend transition probe"
    ):
        raise ValueError("transition context ownership is invalid")
    if (
        report.get("doppler_trace_report_sha256") != trace_sha256
        or trace.get("status")
        != "valid-doppler-production-chunk-trace"
        or _sha256(trace_pcm) != trace.get("pcm_sha256")
    ):
        raise ValueError("Doppler trace binding changed")
    if (
        report.get("portal_report_sha256") != portal_sha256
        or portal.get("status") != "valid-diagnostic"
    ):
        raise ValueError("portal report binding changed")

    environments = report.get("environments")
    if (
        not isinstance(environments, list)
        or [item.get("name") for item in environments]
        != list(ENVIRONMENT_ORDER)
        or [item.get("segment") for item in environments]
        != list(range(SEGMENT_COUNT))
    ):
        raise ValueError("transition environment order is invalid")
    sources = _portal_controls(portal)
    for index, observed in enumerate(environments):
        if not _controls_match(observed, sources[observed["name"]]):
            raise ValueError("transition controls detached from portal")
        expected_transition = 0.0 if index == 0 else 0.2
        if not math.isclose(
            observed.get("transition_seconds", -1.0),
            expected_transition,
            abs_tol=1.0e-9,
        ):
            raise ValueError("transition smoothing duration changed")

    motor, propeller = _trace_chunks(report, trace, trace_pcm)
    expected = motor + propeller
    payloads = {"dry": dry_pcm, "efx": efx_pcm, "java_fdn": java_pcm}
    signals: dict[str, np.ndarray] = {}
    alignment: dict[str, dict[str, float | int]] = {}
    for name, payload in payloads.items():
        prefix = "java_fdn" if name == "java_fdn" else name
        if (
            len(payload) != OUTPUT_BYTES
            or report.get(f"{prefix}_pcm_bytes") != OUTPUT_BYTES
            or report.get(f"{prefix}_pcm_sha256") != _sha256(payload)
        ):
            raise ValueError(f"{name} transition PCM identity changed")
        signal = np.frombuffer(payload, dtype="<i2").astype(np.float64)
        signals[name] = signal
        correlation, lag = _best_lag(signal, expected)
        alignment[name] = {"correlation": correlation, "lag_samples": lag}
        if correlation < 0.50 or lag != 48:
            raise ValueError(f"{name} transition alignment failed")

    boundary_results: dict[str, list[dict[str, float]]] = {}
    segment_rms: dict[str, list[float]] = {}
    for backend in ("efx", "java_fdn"):
        residual = signals[backend] - signals["dry"]
        boundary_results[backend] = [
            _boundary_metrics(residual, boundary)
            for boundary in BOUNDARIES
        ]
        for item in boundary_results[backend]:
            if item["residual_step_absolute"] > max(
                4.0, item["local_difference_p99"]
            ):
                raise ValueError(f"{backend} has a resolved boundary click")
            if not 0.5 <= item["five_ms_rms_ratio"] <= 2.0:
                raise ValueError(
                    f"{backend} has an abrupt five-ms energy jump"
                )
        segment_rms[backend] = [
            _rms(
                residual[
                    index * SEGMENT_FRAMES + 4_800 :
                    (index + 1) * SEGMENT_FRAMES
                ]
            )
            for index in range(SEGMENT_COUNT)
        ]
        values = segment_rms[backend]
        if not (
            values[0] > values[1] > values[2]
            and values[3] > values[2]
        ):
            raise ValueError(
                f"{backend} did not respond to the transition sequence"
            )

    return {
        "schema_version": 1,
        "status": "valid-backend-dynamic-transition",
        "source_report_sha256": report_sha256,
        "doppler_trace_report_sha256": trace_sha256,
        "portal_report_sha256": portal_sha256,
        "pcm_sha256": {
            name: _sha256(payload) for name, payload in payloads.items()
        },
        "alignment": alignment,
        "boundary_metrics": boundary_results,
        "steady_segment_residual_rms": segment_rms,
        "gates": {
            "four_continuous_production_chunks_hash_bound": True,
            "production_portal_controls_hash_bound": True,
            "efx_no_resolved_software_boundary_click": True,
            "java_fdn_no_resolved_software_boundary_click": True,
            "five_ms_energy_ratio_bounded": True,
            "both_backends_respond_to_open_and_close": True,
            "fdn_tail_not_cleared": True,
            "physical_playback_or_capture_opened": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "No boundary click was resolved in this isolated OpenAL Soft "
            "render for the tested parameter sequence. This is not endpoint "
            "continuity, an audible threshold, or release calibration."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--dry-pcm", type=Path, required=True)
    parser.add_argument("--efx-pcm", type=Path, required=True)
    parser.add_argument("--java-pcm", type=Path, required=True)
    parser.add_argument("--trace-report", type=Path, required=True)
    parser.add_argument("--trace-pcm", type=Path, required=True)
    parser.add_argument("--portal-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    report_bytes = args.report.read_bytes()
    trace_bytes = args.trace_report.read_bytes()
    portal_bytes = args.portal_report.read_bytes()
    result = verify(
        json.loads(report_bytes),
        _sha256(report_bytes),
        args.dry_pcm.read_bytes(),
        args.efx_pcm.read_bytes(),
        args.java_pcm.read_bytes(),
        json.loads(trace_bytes),
        _sha256(trace_bytes),
        args.trace_pcm.read_bytes(),
        json.loads(portal_bytes),
        _sha256(portal_bytes),
    )
    rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(rendered, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
