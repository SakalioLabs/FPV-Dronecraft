#!/usr/bin/env python3
"""Measure clicks, dropouts, and level continuity around an audio reload."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import sys
import wave
from pathlib import Path
from typing import Any, Sequence

import numpy as np


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def load_alignment_boundary(path: Path) -> tuple[float, str]:
    raw = path.read_bytes()
    value = json.loads(raw.decode("utf-8"))
    if not isinstance(value, dict):
        raise ValueError("alignment report root must be an object")
    if (
        value.get("schema_version") != 1
        or value.get("status")
        != "valid-audio-lab-alignment-diagnostic"
    ):
        raise ValueError("alignment report schema/status is invalid")
    alignment = value.get("alignment")
    if not isinstance(alignment, dict):
        raise ValueError("alignment report has no alignment section")
    boundary = alignment.get("boundary_audio_s")
    if (
        isinstance(boundary, bool)
        or not isinstance(boundary, (int, float))
        or not math.isfinite(float(boundary))
        or boundary < 0.0
    ):
        raise ValueError("alignment boundary_audio_s is invalid")
    return float(boundary), hashlib.sha256(raw).hexdigest()


def decode_pcm(raw: bytes, width: int) -> np.ndarray:
    if width == 1:
        return (
            np.frombuffer(raw, dtype=np.uint8).astype(np.float64) - 128.0
        ) / 128.0
    if width == 2:
        return np.frombuffer(raw, dtype="<i2").astype(np.float64) / 32768.0
    if width == 3:
        values = np.frombuffer(raw, dtype=np.uint8).reshape(-1, 3)
        signed = (
            values[:, 0].astype(np.int32)
            | (values[:, 1].astype(np.int32) << 8)
            | (values[:, 2].astype(np.int32) << 16)
        )
        signed = np.where(signed & 0x800000, signed - 0x1000000, signed)
        return signed.astype(np.float64) / 8388608.0
    if width == 4:
        return np.frombuffer(raw, dtype="<i4").astype(np.float64) / 2147483648.0
    raise ValueError(f"unsupported PCM width: {width} bytes")


def read_pcm_wav(path: Path, channel: int) -> tuple[int, np.ndarray, dict[str, Any]]:
    with wave.open(str(path), "rb") as source:
        if source.getcomptype() != "NONE":
            raise ValueError("WAV must contain uncompressed PCM")
        channels = source.getnchannels()
        sample_rate = source.getframerate()
        sample_width = source.getsampwidth()
        frame_count = source.getnframes()
        if not 0 <= channel < channels:
            raise ValueError(f"channel must be in [0, {channels})")
        raw = source.readframes(frame_count)
    if len(raw) != frame_count * channels * sample_width:
        raise ValueError("WAV PCM payload is truncated")
    interleaved = decode_pcm(raw, sample_width)
    samples = interleaved.reshape(frame_count, channels)[:, channel].copy()
    if not np.all(np.isfinite(samples)):
        raise ValueError("decoded WAV contains non-finite samples")
    return sample_rate, samples, {
        "channels": channels,
        "sample_rate_hz": sample_rate,
        "sample_width_bits": sample_width * 8,
        "frame_count": frame_count,
        "duration_s": frame_count / sample_rate,
        "wav_bytes": path.stat().st_size,
        "wav_sha256": sha256_file(path),
    }


def rms(samples: np.ndarray) -> float:
    if samples.size == 0:
        raise ValueError("RMS window must contain samples")
    return float(math.sqrt(float(np.mean(samples * samples))))


def db_ratio(numerator: float, denominator: float) -> float:
    floor = 1.0e-15
    return 20.0 * math.log10(max(numerator, floor) / max(denominator, floor))


def contiguous_events(indices: np.ndarray) -> list[tuple[int, int]]:
    if indices.size == 0:
        return []
    boundaries = np.flatnonzero(np.diff(indices) > 1)
    starts = np.concatenate(([indices[0]], indices[boundaries + 1]))
    ends = np.concatenate((indices[boundaries], [indices[-1]]))
    return [(int(start), int(end)) for start, end in zip(starts, ends)]


def analyze(
    sample_rate: int,
    samples: np.ndarray,
    reload_time_s: float,
    pre_window_s: float,
    post_window_s: float,
    guard_s: float,
    frame_ms: float,
    dropout_threshold_db: float,
    minimum_dropout_ms: float,
    click_sigma: float,
    click_minimum_step: float,
) -> dict[str, Any]:
    duration_s = samples.size / sample_rate
    values = (
        reload_time_s,
        pre_window_s,
        post_window_s,
        guard_s,
        frame_ms,
        dropout_threshold_db,
        minimum_dropout_ms,
        click_sigma,
        click_minimum_step,
    )
    if not all(math.isfinite(value) for value in values):
        raise ValueError("analysis parameters must be finite")
    if pre_window_s <= guard_s or post_window_s <= guard_s:
        raise ValueError("pre/post windows must be longer than guard")
    if reload_time_s - pre_window_s < 0.0:
        raise ValueError("pre window starts before the WAV")
    if reload_time_s + post_window_s > duration_s:
        raise ValueError("post window ends after the WAV")
    if not 1.0 <= frame_ms <= 100.0:
        raise ValueError("frame_ms must be in [1, 100]")
    if dropout_threshold_db <= 0.0 or minimum_dropout_ms < frame_ms:
        raise ValueError("dropout thresholds are invalid")
    if click_sigma < 3.0 or click_minimum_step <= 0.0:
        raise ValueError("click thresholds are invalid")

    def index(seconds: float) -> int:
        return int(round(seconds * sample_rate))

    analysis_start = index(reload_time_s - pre_window_s)
    analysis_end = index(reload_time_s + post_window_s)
    guard_start = index(reload_time_s - guard_s)
    guard_end = index(reload_time_s + guard_s)
    before = samples[analysis_start:guard_start]
    after = samples[guard_end:analysis_end]
    before_rms = rms(before)
    after_rms = rms(after)
    reference_rms = math.sqrt(before_rms * after_rms)
    if reference_rms <= 1.0e-8:
        raise ValueError("baseline signal is too quiet for continuity analysis")

    baseline_differences = np.concatenate((np.diff(before), np.diff(after)))
    absolute_baseline = np.abs(baseline_differences)
    median_step = float(np.median(absolute_baseline))
    mad_step = float(np.median(np.abs(absolute_baseline - median_step)))
    robust_sigma = max(1.4826 * mad_step, 1.0e-12)
    click_threshold = max(
        click_minimum_step,
        median_step + click_sigma * robust_sigma,
    )
    all_differences = np.abs(np.diff(samples))
    click_indices = np.flatnonzero(all_differences >= click_threshold)
    click_events = contiguous_events(click_indices)
    reload_events = [
        event
        for event in click_events
        if event[1] >= guard_start - 1 and event[0] < guard_end
    ]
    reload_peak_step = float(
        np.max(all_differences[max(0, guard_start - 1):guard_end])
    )

    frame_size = max(1, round(frame_ms * sample_rate / 1000.0))
    analysis = samples[analysis_start:analysis_end]
    frame_count = analysis.size // frame_size
    frames = analysis[: frame_count * frame_size].reshape(
        frame_count,
        frame_size,
    )
    frame_rms = np.sqrt(np.mean(frames * frames, axis=1))
    dropout_linear = reference_rms * 10.0 ** (-dropout_threshold_db / 20.0)
    low_frames = np.flatnonzero(frame_rms <= dropout_linear)
    minimum_frames = math.ceil(minimum_dropout_ms / frame_ms)
    dropout_events = [
        event
        for event in contiguous_events(low_frames)
        if event[1] - event[0] + 1 >= minimum_frames
    ]
    longest_dropout_frames = max(
        (end - start + 1 for start, end in dropout_events),
        default=0,
    )

    return {
        "reload_time_s": reload_time_s,
        "analysis_start_s": analysis_start / sample_rate,
        "analysis_end_s": analysis_end / sample_rate,
        "guard_start_s": guard_start / sample_rate,
        "guard_end_s": guard_end / sample_rate,
        "baseline_before_rms": before_rms,
        "baseline_after_rms": after_rms,
        "post_pre_level_delta_db": db_ratio(after_rms, before_rms),
        "reference_rms": reference_rms,
        "frame_ms": frame_ms,
        "dropout_threshold_db": dropout_threshold_db,
        "dropout_threshold_linear": dropout_linear,
        "minimum_dropout_ms": minimum_dropout_ms,
        "dropout_event_count": len(dropout_events),
        "longest_dropout_ms": longest_dropout_frames * frame_ms,
        "dropout_events": [
            {
                "start_s": (
                    analysis_start + start * frame_size
                ) / sample_rate,
                "end_s": (
                    analysis_start + (end + 1) * frame_size
                ) / sample_rate,
            }
            for start, end in dropout_events
        ],
        "click_sigma": click_sigma,
        "click_minimum_step": click_minimum_step,
        "click_threshold_step": click_threshold,
        "baseline_median_absolute_step": median_step,
        "baseline_robust_step_sigma": robust_sigma,
        "reload_guard_click_event_count": len(reload_events),
        "reload_guard_peak_step": reload_peak_step,
        "reload_guard_peak_over_threshold": (
            reload_peak_step / click_threshold
        ),
    }


def write_pcm24(path: Path, samples: np.ndarray, sample_rate: int) -> None:
    clipped = np.clip(samples, -1.0, 1.0 - 1.0 / 8388608.0)
    integers = np.rint(clipped * 8388608.0).astype(np.int32)
    packed = np.column_stack(
        (
            integers & 0xFF,
            (integers >> 8) & 0xFF,
            (integers >> 16) & 0xFF,
        )
    ).astype(np.uint8).tobytes()
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(3)
        output.setframerate(sample_rate)
        output.writeframes(packed)


def generate_fixture(path: Path) -> dict[str, Any]:
    sample_rate = 48_000
    duration_s = 6.0
    reload_time_s = 3.0
    times = np.arange(round(duration_s * sample_rate)) / sample_rate
    samples = (
        0.16 * np.sin(2.0 * math.pi * 233.0 * times)
        + 0.04 * np.sin(2.0 * math.pi * 701.0 * times)
    )
    click_index = round(reload_time_s * sample_rate)
    samples[click_index] = 0.92
    dropout_start_s = 3.65
    dropout_duration_s = 0.06
    dropout_start = round(dropout_start_s * sample_rate)
    dropout_end = round(
        (dropout_start_s + dropout_duration_s) * sample_rate
    )
    samples[dropout_start:dropout_end] = 0.0
    write_pcm24(path, samples, sample_rate)
    return {
        "kind": "deterministic-analyzer-fixture",
        "injected_reload_click": True,
        "injected_dropout": True,
        "injected_dropout_start_s": dropout_start_s,
        "injected_dropout_duration_s": dropout_duration_s,
        "real_minecraft_capture": False,
        "physical_output_loopback_confirmed": False,
    }


def atomic_write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + f".tmp-{os.getpid()}")
    try:
        temporary.write_text(
            json.dumps(value, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def report_for(
    wav: Path,
    channel: int,
    args: argparse.Namespace,
    fixture: dict[str, Any] | None,
) -> dict[str, Any]:
    sample_rate, samples, metadata = read_pcm_wav(wav, channel)
    alignment_path = getattr(args, "alignment_json", None)
    if alignment_path is not None:
        reload_time_s, alignment_sha256 = load_alignment_boundary(
            alignment_path
        )
        reload_time_source = "audio-lab-alignment-report"
    else:
        reload_time_s = args.reload_time_s
        alignment_sha256 = None
        reload_time_source = (
            "deterministic-fixture" if fixture is not None else "manual"
        )
    result = analyze(
        sample_rate,
        samples,
        reload_time_s,
        args.pre_window_s,
        args.post_window_s,
        args.guard_s,
        args.frame_ms,
        args.dropout_threshold_db,
        args.minimum_dropout_ms,
        args.click_sigma,
        args.click_minimum_step,
    )
    real_capture = args.real_minecraft_capture if fixture is None else False
    physical_loopback = (
        args.physical_output_loopback_confirmed if fixture is None else False
    )
    return {
        "schema_version": 1,
        "status": "valid-audio-continuity-diagnostic",
        "wav": metadata,
        "channel": channel,
        "analysis": result,
        "reload_time_source": reload_time_source,
        "alignment_report_sha256": alignment_sha256,
        "fixture": fixture,
        "real_minecraft_capture": real_capture,
        "physical_output_loopback_confirmed": physical_loopback,
        "openal_callback_underrun_counter_available": False,
        "release_calibrated": False,
        "claim_boundary": (
            "Waveform-domain click/dropout diagnostic. It does not identify "
            "OpenAL callback underruns, prove endpoint loopback semantics, "
            "or calibrate EFX/FDN parameters without a real synchronized "
            "capture protocol."
        ),
    }


def add_analysis_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--reload-time-s", type=float, default=3.0)
    parser.add_argument("--pre-window-s", type=float, default=1.5)
    parser.add_argument("--post-window-s", type=float, default=1.5)
    parser.add_argument("--guard-s", type=float, default=0.1)
    parser.add_argument("--frame-ms", type=float, default=5.0)
    parser.add_argument("--dropout-threshold-db", type=float, default=24.0)
    parser.add_argument("--minimum-dropout-ms", type=float, default=20.0)
    parser.add_argument("--click-sigma", type=float, default=12.0)
    parser.add_argument("--click-minimum-step", type=float, default=0.05)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    analyze_parser = subparsers.add_parser("analyze")
    analyze_parser.add_argument("--wav", type=Path, required=True)
    analyze_parser.add_argument("--channel", type=int, default=0)
    analyze_parser.add_argument("--output-json", type=Path, required=True)
    analyze_parser.add_argument("--alignment-json", type=Path)
    analyze_parser.add_argument("--real-minecraft-capture", action="store_true")
    analyze_parser.add_argument(
        "--physical-output-loopback-confirmed",
        action="store_true",
    )
    add_analysis_arguments(analyze_parser)
    fixture_parser = subparsers.add_parser("fixture")
    fixture_parser.add_argument("--output-wav", type=Path, required=True)
    fixture_parser.add_argument("--output-json", type=Path, required=True)
    fixture_parser.set_defaults(
        channel=0,
        real_minecraft_capture=False,
        physical_output_loopback_confirmed=False,
        alignment_json=None,
    )
    add_analysis_arguments(fixture_parser)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        if args.command == "fixture":
            fixture = generate_fixture(args.output_wav)
            report = report_for(args.output_wav, 0, args, fixture)
        else:
            if not args.wav.is_file():
                raise FileNotFoundError(f"WAV does not exist: {args.wav}")
            report = report_for(args.wav, args.channel, args, None)
        atomic_write_json(args.output_json, report)
        print(json.dumps(report, sort_keys=True))
        return 0
    except (FileNotFoundError, ValueError) as error:
        parser.exit(2, f"error: {error}\n")


if __name__ == "__main__":
    sys.exit(main())
