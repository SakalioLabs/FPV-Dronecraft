#!/usr/bin/env python3
"""Verify controlled dry, OpenAL EFX, and Java FDN loopback transfers."""

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
TAIL_FRAMES = 144_000
RENDER_FRAMES = INPUT_FRAMES + TAIL_FRAMES
MAXIMUM_LAG = 1_024
BANDS = {
    "low": (40.0, 700.0),
    "mid": (700.0, 4_000.0),
    "high": (4_000.0, 20_000.0),
}


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _pcm16(data: bytes) -> np.ndarray:
    if len(data) != RENDER_FRAMES * 2:
        raise ValueError("backend PCM sidecar has an invalid byte count")
    return np.frombuffer(data, dtype="<i2").astype(np.float64)


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


def _rms(signal: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(signal))))


def _band_rms(signal: np.ndarray) -> dict[str, float]:
    spectrum = np.fft.rfft(signal)
    frequencies = np.fft.rfftfreq(signal.size, 1.0 / SAMPLE_RATE)
    weights = np.full(spectrum.size, 2.0)
    weights[0] = 1.0
    if signal.size % 2 == 0:
        weights[-1] = 1.0
    result: dict[str, float] = {}
    for name, (low, high) in BANDS.items():
        mask = (frequencies >= low) & (frequencies < high)
        result[name] = float(
            np.sqrt(
                np.sum(np.square(np.abs(spectrum[mask])) * weights[mask])
                / signal.size**2
            )
        )
    return result


def _selected_trace_input(
    trace: dict[str, Any], trace_pcm: bytes, layer: str
) -> tuple[dict[str, Any], np.ndarray]:
    candidates = [
        chunk
        for chunk in trace.get("chunks", [])
        if chunk.get("layer") == layer
    ]
    if not candidates:
        raise ValueError(f"trace has no {layer} input")
    chunk = min(candidates, key=lambda item: item.get("sequence", -1))
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


def verify(
    report: dict[str, Any],
    report_sha256: str,
    dry_pcm: bytes,
    efx_pcm: bytes,
    java_pcm: bytes,
    trace: dict[str, Any],
    trace_sha256: str,
    trace_pcm: bytes,
) -> dict[str, Any]:
    if report.get("schema_version") != 1:
        raise ValueError("unsupported backend-transfer schema")
    if report.get("status") != "valid-controlled-backend-transfer":
        raise ValueError("backend-transfer status is invalid")
    if (
        report.get("render_format") != "s16le-mono-48000"
        or report.get("input_frames") != INPUT_FRAMES
        or report.get("tail_frames") != TAIL_FRAMES
        or report.get("render_frames") != RENDER_FRAMES
    ):
        raise ValueError("backend-transfer render shape is invalid")
    for key in (
        "minecraft_context_unchanged",
        "minecraft_device_unchanged",
        "minecraft_sound_thread_unchanged",
        "production_parameter_mapping_reused",
        "production_java_fdn_reused",
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
    if (
        report.get("minecraft_sound_thread") != "Sound engine"
        or report.get("loopback_worker_thread")
        != "Dronecraft backend transfer probe"
    ):
        raise ValueError("backend-transfer context ownership is invalid")
    if (
        trace.get("status")
        != "valid-doppler-production-chunk-trace"
        or report.get("doppler_trace_report_sha256") != trace_sha256
        or _sha256(trace_pcm) != trace.get("pcm_sha256")
    ):
        raise ValueError("Doppler trace binding changed")

    sidecars = {
        "dry": dry_pcm,
        "efx": efx_pcm,
        "java_fdn": java_pcm,
    }
    signals: dict[str, np.ndarray] = {}
    for name, data in sidecars.items():
        prefix = "java_fdn" if name == "java_fdn" else name
        if (
            report.get(f"{prefix}_pcm_bytes") != len(data)
            or report.get(f"{prefix}_pcm_sha256") != _sha256(data)
        ):
            raise ValueError(f"{name} PCM identity changed")
        signals[name] = _pcm16(data)
    if len({_sha256(data) for data in sidecars.values()}) != 3:
        raise ValueError("backend outputs are not distinct")

    selected: dict[str, np.ndarray] = {}
    for layer in ("motor", "propeller"):
        chunk, signal = _selected_trace_input(trace, trace_pcm, layer)
        if (
            report.get(f"{layer}_sequence") != chunk.get("sequence")
            or report.get(f"{layer}_pcm_sha256")
            != chunk.get("pcm_sha256")
        ):
            raise ValueError(f"{layer} production input detached")
        selected[layer] = signal
    expected = selected["motor"] + selected["propeller"]

    alignment: dict[str, dict[str, float | int]] = {}
    for name, signal in signals.items():
        correlation, lag = _best_lag(signal[:INPUT_FRAMES], expected)
        alignment[name] = {"correlation": correlation, "lag_samples": lag}
        if correlation < 0.50 or abs(lag) > MAXIMUM_LAG:
            raise ValueError(f"{name} input alignment failed")

    # Exclude the bounded OpenAL mixer latency at the input/tail boundary.
    early_slice = slice(
        INPUT_FRAMES + MAXIMUM_LAG,
        INPUT_FRAMES + SAMPLE_RATE // 2,
    )
    late_slice = slice(RENDER_FRAMES - SAMPLE_RATE // 2, RENDER_FRAMES)
    tail: dict[str, dict[str, Any]] = {}
    for name, signal in signals.items():
        early = signal[early_slice]
        late = signal[late_slice]
        tail[name] = {
            "early_rms": _rms(early),
            "late_rms": _rms(late),
            "early_band_rms": _band_rms(early),
            "late_band_rms": _band_rms(late),
        }
    dry_floor = max(1.0, tail["dry"]["early_rms"])
    for name in ("efx", "java_fdn"):
        if tail[name]["early_rms"] <= dry_floor * 4.0:
            raise ValueError(f"{name} has no resolved post-input tail")
        if tail[name]["late_rms"] >= tail[name]["early_rms"]:
            raise ValueError(f"{name} tail did not decay")
    if tail["dry"]["early_rms"] > 1.0:
        raise ValueError("dry control contains an unexpected tail")

    return {
        "schema_version": 1,
        "status": "valid-controlled-backend-transfer",
        "source_report_sha256": report_sha256,
        "doppler_trace_report_sha256": trace_sha256,
        "pcm_sha256": {
            name: _sha256(data) for name, data in sidecars.items()
        },
        "environment": {
            "snapshot_generation": report.get("snapshot_generation"),
            "rt60_seconds": report.get("rt60_seconds"),
            "wet_gain": report.get("wet_gain"),
            "transition_seconds": report.get("transition_seconds"),
        },
        "alignment": alignment,
        "tail": tail,
        "gates": {
            "identical_production_input_hash_bound": True,
            "three_distinct_outputs_hash_bound": True,
            "dry_control_has_no_resolved_tail": True,
            "efx_tail_resolved_and_decaying": True,
            "java_fdn_tail_resolved_and_decaying": True,
            "production_parameter_mapping_reused": True,
            "production_java_fdn_reused": True,
            "minecraft_context_and_device_unchanged": True,
            "physical_playback_or_capture_opened": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "This is an isolated software-renderer transfer comparison using "
            "identical production PCM and controls mapped from an integrated "
            "Minecraft closed-room snapshot. It does not establish perceptual "
            "equivalence, main-context output, endpoint behavior, or release "
            "calibration."
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
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    report_bytes = args.report.read_bytes()
    trace_bytes = args.trace_report.read_bytes()
    result = verify(
        json.loads(report_bytes),
        _sha256(report_bytes),
        args.dry_pcm.read_bytes(),
        args.efx_pcm.read_bytes(),
        args.java_pcm.read_bytes(),
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
