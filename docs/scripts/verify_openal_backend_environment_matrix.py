#!/usr/bin/env python3
"""Verify the closed/partial/open steady-state OpenAL backend matrix."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any

import numpy as np


SAMPLE_RATE = 48_000
INPUT_FRAMES = 48_000
RENDER_FRAMES = 192_000
OUTPUT_BYTES = RENDER_FRAMES * 2
MAXIMUM_LAG = 1_024
ENVIRONMENT_ORDER = ("closed", "partial", "open")
BACKEND_ORDER = ("dry", "efx", "java_fdn")
BANDS = {
    "low": (40.0, 700.0),
    "mid": (700.0, 4_000.0),
    "high": (4_000.0, 20_000.0),
}


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _rms(signal: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(signal))))


def _band_rms(signal: np.ndarray) -> dict[str, float]:
    spectrum = np.fft.rfft(signal)
    frequencies = np.fft.rfftfreq(signal.size, 1.0 / SAMPLE_RATE)
    weights = np.full(spectrum.size, 2.0)
    weights[0] = 1.0
    if signal.size % 2 == 0:
        weights[-1] = 1.0
    return {
        name: float(
            np.sqrt(
                np.sum(
                    np.square(np.abs(spectrum[(
                        (frequencies >= low) & (frequencies < high)
                    )]))
                    * weights[(
                        (frequencies >= low) & (frequencies < high)
                    )]
                )
                / signal.size**2
            )
        )
        for name, (low, high) in BANDS.items()
    }


def _best_lag(
    output: np.ndarray, expected: np.ndarray
) -> tuple[float, int]:
    best = -1.0
    best_lag = 0
    for lag in range(-MAXIMUM_LAG, MAXIMUM_LAG + 1):
        if lag >= 0:
            observed = output[lag : expected.size]
            source = expected[: observed.size]
        else:
            observed = output[: expected.size + lag]
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


def _trace_input(
    trace: dict[str, Any], trace_pcm: bytes, layer: str
) -> tuple[dict[str, Any], np.ndarray]:
    chunks = [
        item
        for item in trace.get("chunks", [])
        if item.get("layer") == layer
    ]
    if not chunks:
        raise ValueError(f"trace has no {layer} input")
    chunk = min(chunks, key=lambda item: item.get("sequence", -1))
    offset = chunk.get("pcm_offset")
    count = chunk.get("pcm_bytes")
    if (
        isinstance(offset, bool)
        or not isinstance(offset, int)
        or isinstance(count, bool)
        or not isinstance(count, int)
        or offset < 0
        or count != INPUT_FRAMES * 2
        or offset + count > len(trace_pcm)
    ):
        raise ValueError(f"{layer} trace range is invalid")
    payload = trace_pcm[offset : offset + count]
    if _sha256(payload) != chunk.get("pcm_sha256"):
        raise ValueError(f"{layer} trace PCM hash changed")
    return chunk, np.frombuffer(payload, dtype="<i2").astype(np.float64)


def _require_flags(report: dict[str, Any]) -> None:
    for key in (
        "production_parameter_mapping_reused",
        "production_java_fdn_reused",
        "minecraft_context_unchanged",
        "minecraft_device_unchanged",
    ):
        if report.get(key) is not True:
            raise ValueError(f"{key} must be True")
    for key in (
        "physical_playback_device_opened",
        "capture_device_opened",
        "real_audio_capture",
        "release_calibrated",
    ):
        if report.get(key) is not False:
            raise ValueError(f"{key} must be False")


def _controls_match(
    observed: dict[str, Any], source: dict[str, Any]
) -> bool:
    if observed.get("name") != source.get("name"):
        return False
    for band in BANDS:
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


def verify(
    report: dict[str, Any],
    report_sha256: str,
    matrix_pcm: bytes,
    trace: dict[str, Any],
    trace_sha256: str,
    trace_pcm: bytes,
    portal: dict[str, Any],
    portal_sha256: str,
) -> dict[str, Any]:
    if report.get("schema_version") != 1:
        raise ValueError("unsupported backend-matrix schema")
    if report.get("status") != "valid-backend-environment-matrix":
        raise ValueError("backend-matrix status is invalid")
    if (
        report.get("render_format") != "s16le-mono-48000"
        or report.get("input_frames") != INPUT_FRAMES
        or report.get("tail_frames") != RENDER_FRAMES - INPUT_FRAMES
        or report.get("render_frames_per_output") != RENDER_FRAMES
    ):
        raise ValueError("backend-matrix render shape is invalid")
    _require_flags(report)
    expected_matrix_bytes = (
        len(ENVIRONMENT_ORDER) * len(BACKEND_ORDER) * OUTPUT_BYTES
    )
    if (
        len(matrix_pcm) != expected_matrix_bytes
        or report.get("matrix_pcm_bytes") != expected_matrix_bytes
        or report.get("matrix_pcm_sha256") != _sha256(matrix_pcm)
    ):
        raise ValueError("backend-matrix PCM identity changed")
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
        or portal.get("source") != "integrated-client-world"
    ):
        raise ValueError("Minecraft portal binding changed")

    environments = report.get("environments")
    portal_environments = portal.get("backend_matrix_environments")
    if (
        not isinstance(environments, list)
        or not isinstance(portal_environments, list)
        or [item.get("name") for item in environments]
        != list(ENVIRONMENT_ORDER)
        or [item.get("name") for item in portal_environments]
        != list(ENVIRONMENT_ORDER)
    ):
        raise ValueError("backend-matrix environment order is invalid")
    if not all(
        _controls_match(observed, source)
        for observed, source in zip(environments, portal_environments)
    ):
        raise ValueError("backend-matrix controls detached from portal")
    for band in BANDS:
        values = [
            item["rt60_seconds"][band] for item in environments
        ]
        if not values[0] > values[1] > values[2] > 0.0:
            raise ValueError(f"{band} RT60 is not strictly ordered")
    wet = [item["wet_gain"] for item in environments]
    if not wet[0] >= wet[1] >= wet[2] > 0.0:
        raise ValueError("wet gain is not nonincreasing")

    selected: dict[str, np.ndarray] = {}
    for layer in ("motor", "propeller"):
        chunk, signal = _trace_input(trace, trace_pcm, layer)
        if (
            report.get(f"{layer}_sequence") != chunk.get("sequence")
            or report.get(f"{layer}_pcm_sha256")
            != chunk.get("pcm_sha256")
        ):
            raise ValueError(f"{layer} production input detached")
        selected[layer] = signal
    expected = selected["motor"] + selected["propeller"]

    next_offset = 0
    signals: dict[str, dict[str, np.ndarray]] = {}
    metrics: dict[str, dict[str, Any]] = {}
    dry_hashes: list[str] = []
    for environment in environments:
        name = environment["name"]
        outputs = environment.get("outputs")
        if (
            not isinstance(outputs, list)
            or [item.get("backend") for item in outputs]
            != list(BACKEND_ORDER)
        ):
            raise ValueError(f"{name} backend order is invalid")
        signals[name] = {}
        metrics[name] = {}
        for output in outputs:
            backend = output["backend"]
            offset = output.get("pcm_offset")
            count = output.get("pcm_bytes")
            if offset != next_offset or count != OUTPUT_BYTES:
                raise ValueError("matrix PCM ranges are not contiguous")
            payload = matrix_pcm[offset : offset + count]
            next_offset += count
            if _sha256(payload) != output.get("pcm_sha256"):
                raise ValueError(f"{name}/{backend} PCM hash changed")
            signal = np.frombuffer(payload, dtype="<i2").astype(np.float64)
            signals[name][backend] = signal
            correlation, lag = _best_lag(signal[:INPUT_FRAMES], expected)
            if correlation < 0.50 or lag != 48:
                raise ValueError(f"{name}/{backend} input alignment failed")
            early = signal[INPUT_FRAMES + MAXIMUM_LAG : 72_000]
            middle = signal[96_000:120_000]
            late = signal[168_000:192_000]
            metrics[name][backend] = {
                "input_correlation": correlation,
                "lag_samples": lag,
                "early_tail_rms": _rms(early),
                "middle_tail_rms": _rms(middle),
                "late_tail_rms": _rms(late),
                "early_tail_band_rms": _band_rms(early),
            }
            if backend == "dry":
                dry_hashes.append(_sha256(payload))
    if next_offset != len(matrix_pcm):
        raise ValueError("matrix PCM has trailing bytes")
    if len(set(dry_hashes)) != 1:
        raise ValueError("dry controls changed across environments")

    for name in ENVIRONMENT_ORDER:
        dry_floor = max(1.0, metrics[name]["dry"]["early_tail_rms"])
        if metrics[name]["dry"]["early_tail_rms"] > 1.0:
            raise ValueError(f"{name} dry control has a tail")
        for backend in ("efx", "java_fdn"):
            item = metrics[name][backend]
            if item["early_tail_rms"] <= dry_floor * 4.0:
                raise ValueError(f"{name}/{backend} tail is unresolved")
            if item["late_tail_rms"] >= item["early_tail_rms"]:
                raise ValueError(f"{name}/{backend} tail did not decay")
    for backend in ("efx", "java_fdn"):
        early = [
            metrics[name][backend]["early_tail_rms"]
            for name in ENVIRONMENT_ORDER
        ]
        middle = [
            metrics[name][backend]["middle_tail_rms"]
            for name in ENVIRONMENT_ORDER
        ]
        if not early[0] > early[1] > early[2]:
            raise ValueError(f"{backend} early tail is not monotonic")
        if not middle[0] > middle[1] >= middle[2] - 0.02:
            raise ValueError(f"{backend} middle tail is not monotonic")

    return {
        "schema_version": 1,
        "status": "valid-backend-environment-matrix",
        "source_report_sha256": report_sha256,
        "doppler_trace_report_sha256": trace_sha256,
        "portal_report_sha256": portal_sha256,
        "matrix_pcm_sha256": _sha256(matrix_pcm),
        "environment_metrics": metrics,
        "gates": {
            "production_input_hash_bound": True,
            "minecraft_portal_controls_hash_bound": True,
            "all_band_rt60_strictly_decreases": True,
            "dry_control_identical_across_environments": True,
            "efx_tail_monotonic_with_portal_opening": True,
            "java_fdn_tail_monotonic_with_portal_opening": True,
            "steady_state_only": True,
            "dynamic_transition_measured": False,
            "physical_playback_or_capture_opened": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Three independently rendered steady states establish monotonic "
            "backend response to production-mapped Minecraft portal controls. "
            "They do not measure a live parameter switch, endpoint output, "
            "perceptual quality, or release calibration."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--matrix-pcm", type=Path, required=True)
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
        args.matrix_pcm.read_bytes(),
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
