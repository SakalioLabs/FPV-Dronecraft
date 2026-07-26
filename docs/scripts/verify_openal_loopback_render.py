#!/usr/bin/env python3
"""Verify isolated ALC_SOFT_loopback rendering of production drone PCM."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any

import numpy as np


AL_PLAYING = 0x1012
AL_STOPPED = 0x1014
MAXIMUM_ABSOLUTE_LAG = 1_024


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _pcm16(data: bytes) -> np.ndarray:
    if not data or len(data) % 2:
        raise ValueError("PCM16 sidecar must be non-empty and even-sized")
    return np.frombuffer(data, dtype="<i2").astype(np.float64)


def _best_lag_correlation(
    output: np.ndarray,
    expected: np.ndarray,
    maximum_lag: int = MAXIMUM_ABSOLUTE_LAG,
) -> tuple[float, int]:
    if output.shape != expected.shape or output.size <= maximum_lag:
        raise ValueError("correlation arrays have an invalid shape")
    best = -1.0
    best_lag = 0
    for lag in range(-maximum_lag, maximum_lag + 1):
        if lag >= 0:
            rendered = output[lag:]
            source = expected[: expected.size - lag]
        else:
            rendered = output[: output.size + lag]
            source = expected[-lag:]
        denominator = math.sqrt(
            float(np.dot(rendered, rendered))
            * float(np.dot(source, source))
        )
        correlation = (
            0.0
            if denominator == 0.0
            else float(np.dot(rendered, source)) / denominator
        )
        if correlation > best:
            best = correlation
            best_lag = lag
    return best, best_lag


def _require_flag(report: dict[str, Any], key: str, expected: bool) -> None:
    if report.get(key) is not expected:
        raise ValueError(f"{key} must be {expected}")


def verify(
    report: dict[str, Any],
    report_sha256: str,
    rendered_pcm: bytes,
    trace: dict[str, Any],
    trace_sha256: str,
    trace_pcm: bytes,
) -> dict[str, Any]:
    if report.get("schema_version") != 1:
        raise ValueError("unsupported loopback report schema")
    if report.get("status") != "valid-openal-loopback-render":
        raise ValueError("loopback report status is invalid")
    if report.get("extension") != "ALC_SOFT_loopback":
        raise ValueError("loopback extension identity is invalid")
    if (
        report.get("thread_context_extension")
        != "ALC_EXT_thread_local_context"
    ):
        raise ValueError("thread-context extension identity is invalid")
    for key in (
        "loopback_supported",
        "thread_context_supported",
        "format_supported",
        "minecraft_context_unchanged",
        "minecraft_device_unchanged",
        "minecraft_sound_thread_unchanged",
        "silence_quantization_dither_bounded",
    ):
        _require_flag(report, key, True)
    for key in (
        "minecraft_audio_path_changed",
        "physical_playback_device_opened",
        "capture_device_opened",
        "real_audio_capture",
        "release_calibrated",
    ):
        _require_flag(report, key, False)
    if (
        report.get("render_format") != "s16le-mono-48000"
        or report.get("render_frames") != 48_000
        or report.get("rendered_pcm_bytes") != 96_000
    ):
        raise ValueError("loopback render format is invalid")
    if (
        len(rendered_pcm) != report["rendered_pcm_bytes"]
        or _sha256(rendered_pcm) != report.get("rendered_pcm_sha256")
    ):
        raise ValueError("rendered PCM hash or byte count changed")
    if (
        trace.get("status")
        != "valid-doppler-production-chunk-trace"
        or report.get("doppler_trace_report_sha256") != trace_sha256
    ):
        raise ValueError("Doppler trace identity changed")
    if _sha256(trace_pcm) != trace.get("pcm_sha256"):
        raise ValueError("Doppler trace PCM sidecar changed")

    chunks = trace.get("chunks")
    inputs = report.get("inputs")
    if not isinstance(chunks, list) or not isinstance(inputs, list):
        raise ValueError("trace chunks or loopback inputs are missing")
    if len(inputs) != 2 or {item.get("layer") for item in inputs} != {
        "motor",
        "propeller",
    }:
        raise ValueError("loopback must render motor and propeller")
    selected_pcm: dict[str, np.ndarray] = {}
    for layer in ("motor", "propeller"):
        candidates = [
            chunk for chunk in chunks if chunk.get("layer") == layer
        ]
        if not candidates:
            raise ValueError(f"trace has no {layer} chunk")
        chunk = min(candidates, key=lambda item: item.get("sequence", -1))
        observed = next(item for item in inputs if item["layer"] == layer)
        if (
            observed.get("sequence") != chunk.get("sequence")
            or observed.get("pcm_bytes") != chunk.get("pcm_bytes")
            or observed.get("pcm_sha256") != chunk.get("pcm_sha256")
        ):
            raise ValueError(f"{layer} input detached from trace")
        offset = chunk.get("pcm_offset")
        byte_count = chunk.get("pcm_bytes")
        if (
            isinstance(offset, bool)
            or not isinstance(offset, int)
            or isinstance(byte_count, bool)
            or not isinstance(byte_count, int)
            or offset < 0
            or byte_count != 96_000
            or offset + byte_count > len(trace_pcm)
        ):
            raise ValueError(f"{layer} trace PCM range is invalid")
        payload = trace_pcm[offset : offset + byte_count]
        if _sha256(payload) != observed["pcm_sha256"]:
            raise ValueError(f"{layer} input PCM hash changed")
        selected_pcm[layer] = _pcm16(payload)

    silence_frames = report.get("silence_control_frames")
    silence_nonzero = report.get("silence_nonzero_samples")
    silence_peak = report.get("silence_peak_absolute_sample")
    if (
        silence_frames != 256
        or isinstance(silence_nonzero, bool)
        or not isinstance(silence_nonzero, int)
        or not 0 <= silence_nonzero <= silence_frames
        or isinstance(silence_peak, bool)
        or not isinstance(silence_peak, int)
        or not 0 <= silence_peak <= 1
    ):
        raise ValueError("silence/dither control is invalid")
    if report.get("wall_clock_hold_millis") < 50:
        raise ValueError("wall-clock hold is too short")
    if report.get("offsets_before_hold") != [0, 0]:
        raise ValueError("source advanced before the wall-clock hold")
    if report.get("offsets_after_hold") != [0, 0]:
        raise ValueError("loopback source advanced with wall-clock time")
    midpoint = report["render_frames"] // 2
    midpoint_offsets = report.get("midpoint_offsets")
    if (
        not isinstance(midpoint_offsets, list)
        or len(midpoint_offsets) != 2
        or any(abs(value - midpoint) > 1 for value in midpoint_offsets)
    ):
        raise ValueError("explicit render did not advance both sources")
    if report.get("midpoint_states") != [AL_PLAYING, AL_PLAYING]:
        raise ValueError("sources were not playing at the midpoint")
    if report.get("final_states") != [AL_STOPPED, AL_STOPPED]:
        raise ValueError("sources did not stop after exact render length")
    if report.get("al_error_code") != 0 or report.get("alc_error_code") != 0:
        raise ValueError("OpenAL reported an error")
    if (
        report.get("minecraft_sound_thread") != "Sound engine"
        or report.get("loopback_worker_thread")
        != "Dronecraft OpenAL loopback probe"
    ):
        raise ValueError("context ownership threads are invalid")

    output = _pcm16(rendered_pcm)
    nonzero = int(np.count_nonzero(output))
    peak = int(np.max(np.abs(output)))
    rms = float(np.sqrt(np.mean(np.square(output))))
    expected = selected_pcm["motor"] + selected_pcm["propeller"]
    correlation, lag = _best_lag_correlation(output, expected)
    if (
        report.get("rendered_nonzero_samples") != nonzero
        or report.get("rendered_peak_absolute_sample") != peak
        or not math.isclose(
            report.get("rendered_rms"),
            rms,
            rel_tol=1e-9,
            abs_tol=1e-6,
        )
        or not math.isclose(
            report.get("summed_input_correlation"),
            correlation,
            rel_tol=1e-8,
            abs_tol=1e-8,
        )
        or report.get("summed_input_lag_samples") != lag
    ):
        raise ValueError("rendered signal metrics are detached")
    if nonzero < midpoint or peak <= 0 or rms <= 1.0:
        raise ValueError("rendered production signal is missing")
    if correlation < 0.90 or abs(lag) > MAXIMUM_ABSOLUTE_LAG:
        raise ValueError("rendered PCM is not correlated with production PCM")

    return {
        "schema_version": 1,
        "status": "valid-openal-loopback-render",
        "source_report_sha256": report_sha256,
        "doppler_trace_report_sha256": trace_sha256,
        "rendered_pcm_sha256": _sha256(rendered_pcm),
        "render_frames": output.size,
        "rendered_nonzero_samples": nonzero,
        "rendered_peak_absolute_sample": peak,
        "rendered_rms": rms,
        "summed_input_correlation": correlation,
        "summed_input_lag_samples": lag,
        "silence_nonzero_samples": silence_nonzero,
        "silence_peak_absolute_sample": silence_peak,
        "gates": {
            "official_loopback_extension_exercised": True,
            "thread_local_context_isolated": True,
            "minecraft_context_and_device_unchanged": True,
            "wall_clock_does_not_advance_sources": True,
            "explicit_render_advances_sources": True,
            "production_motor_and_propeller_pcm_hash_bound": True,
            "rendered_pcm_sidecar_hash_bound": True,
            "rendered_signal_correlates_after_bounded_lag": True,
            "silence_quantization_dither_bounded_to_one_lsb": True,
            "physical_playback_or_capture_opened": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "An isolated ALC_SOFT_loopback context rendered exact production "
            "motor and propeller chunks. This validates software-renderer "
            "readback only, not Minecraft main-context output, a physical "
            "endpoint, continuity, underruns, or listening quality."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--rendered-pcm", type=Path, required=True)
    parser.add_argument("--trace-report", type=Path, required=True)
    parser.add_argument("--trace-pcm", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    report_bytes = args.report.read_bytes()
    trace_bytes = args.trace_report.read_bytes()
    result = verify(
        json.loads(report_bytes),
        _sha256(report_bytes),
        args.rendered_pcm.read_bytes(),
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
