#!/usr/bin/env python3
"""Align an external WAV to a Minecraft audio-lab timeline using three tones."""

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


MARKER_NAMES = (
    "session_start_marker",
    "pre_boundary_marker",
    "post_boundary_marker",
)
BOUNDARY_NAMES = (
    "sound_engine_reload_requested",
    "control_boundary_no_reload",
)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


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
        width = source.getsampwidth()
        frames = source.getnframes()
        if not 0 <= channel < channels:
            raise ValueError(f"channel must be in [0, {channels})")
        raw = source.readframes(frames)
    if len(raw) != frames * channels * width:
        raise ValueError("WAV PCM payload is truncated")
    samples = decode_pcm(raw, width).reshape(frames, channels)[:, channel].copy()
    return sample_rate, samples, {
        "channels": channels,
        "sample_rate_hz": sample_rate,
        "sample_width_bits": width * 8,
        "frame_count": frames,
        "duration_s": frames / sample_rate,
        "wav_sha256": sha256_file(path),
    }


def load_timeline(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("timeline root must be an object")
    if (
        value.get("schema_version") not in (1, 2)
        or value.get("status") != "valid-audio-lab-timeline"
    ):
        raise ValueError("timeline schema/status is invalid")
    marker = value.get("marker_contract")
    events = value.get("events")
    if not isinstance(marker, dict) or not isinstance(events, list):
        raise ValueError("timeline marker/events are missing")
    return value


def marker_template(
    sample_rate: int,
    frequency_hz: float,
    duration_s: float,
) -> tuple[np.ndarray, np.ndarray]:
    count = round(duration_s * sample_rate)
    if count < 32:
        raise ValueError("marker is too short")
    fade = max(1, round(0.005 * sample_rate))
    indices = np.arange(count, dtype=np.float64)
    envelope = np.minimum(
        1.0,
        np.minimum(
            (indices + 1.0) / fade,
            (count - indices) / fade,
        ),
    )
    phase = 2.0 * math.pi * frequency_hz * indices / sample_rate
    return envelope * np.cos(phase), envelope * np.sin(phase)


def detect_marker(
    samples: np.ndarray,
    sample_rate: int,
    frequency_hz: float,
    duration_s: float,
    hop_ms: float,
) -> dict[str, float]:
    cosine, sine = marker_template(sample_rate, frequency_hz, duration_s)
    window_size = cosine.size
    hop = max(1, round(hop_ms * sample_rate / 1000.0))
    if samples.size < window_size:
        raise ValueError("WAV is shorter than one marker")
    cosine_energy = float(np.dot(cosine, cosine))
    sine_energy = float(np.dot(sine, sine))
    best_score = -1.0
    best_start = 0
    for start in range(0, samples.size - window_size + 1, hop):
        window = samples[start : start + window_size]
        energy = float(np.dot(window, window))
        if energy <= 1.0e-15:
            score = 0.0
        else:
            fitted = (
                float(np.dot(window, cosine)) ** 2 / cosine_energy
                + float(np.dot(window, sine)) ** 2 / sine_energy
            )
            score = math.sqrt(max(0.0, fitted / energy))
        if score > best_score:
            best_score = score
            best_start = start
    return {
        "frequency_hz": frequency_hz,
        "start_frame": best_start,
        "start_s": best_start / sample_rate,
        "score": best_score,
        "hop_ms": hop * 1000.0 / sample_rate,
    }


def event_by_name(timeline: dict[str, Any], names: Sequence[str]) -> dict[str, Any]:
    matches = [
        event
        for event in timeline["events"]
        if isinstance(event, dict) and event.get("name") in names
    ]
    if len(matches) != 1:
        raise ValueError(f"expected one timeline event from {tuple(names)}")
    event = matches[0]
    relative_ns = event.get("relative_ns")
    if (
        isinstance(relative_ns, bool)
        or not isinstance(relative_ns, int)
        or relative_ns < 0
    ):
        raise ValueError("timeline relative_ns is invalid")
    return event


def align(
    sample_rate: int,
    samples: np.ndarray,
    timeline: dict[str, Any],
    hop_ms: float,
    minimum_score: float,
) -> dict[str, Any]:
    marker = timeline["marker_contract"]
    duration_s = float(marker["duration_s"])
    frequencies = [
        float(marker["start_hz"]),
        float(marker["pre_boundary_hz"]),
        float(marker["post_boundary_hz"]),
    ]
    detections = [
        detect_marker(
            samples,
            sample_rate,
            frequency,
            duration_s,
            hop_ms,
        )
        for frequency in frequencies
    ]
    if any(item["score"] < minimum_score for item in detections):
        raise ValueError("one or more audio-lab markers are below score gate")
    audio_seconds = np.array(
        [item["start_s"] for item in detections],
        dtype=np.float64,
    )
    if not np.all(np.diff(audio_seconds) > 0.0):
        raise ValueError("detected marker order is invalid")
    marker_events = [
        event_by_name(timeline, (name,)) for name in MARKER_NAMES
    ]
    minecraft_seconds = np.array(
        [event["relative_ns"] / 1.0e9 for event in marker_events],
        dtype=np.float64,
    )
    if not np.all(np.diff(minecraft_seconds) > 0.0):
        raise ValueError("timeline marker order is invalid")
    design = np.column_stack(
        (minecraft_seconds, np.ones(minecraft_seconds.size))
    )
    slope, offset = np.linalg.lstsq(
        design,
        audio_seconds,
        rcond=None,
    )[0]
    fitted = slope * minecraft_seconds + offset
    residual = audio_seconds - fitted
    boundary = event_by_name(timeline, BOUNDARY_NAMES)
    boundary_minecraft_s = boundary["relative_ns"] / 1.0e9
    boundary_audio_s = slope * boundary_minecraft_s + offset
    return {
        "marker_detections": detections,
        "timeline_marker_relative_s": minecraft_seconds.tolist(),
        "recorder_seconds_per_minecraft_second": float(slope),
        "recorder_clock_offset_s": float(offset),
        "recorder_clock_delta_ppm": float((slope - 1.0) * 1.0e6),
        "alignment_residual_ms": (residual * 1000.0).tolist(),
        "maximum_absolute_alignment_residual_ms": float(
            np.max(np.abs(residual)) * 1000.0
        ),
        "boundary_event": boundary["name"],
        "boundary_minecraft_relative_s": boundary_minecraft_s,
        "boundary_audio_s": float(boundary_audio_s),
        "minimum_marker_score": float(
            min(item["score"] for item in detections)
        ),
    }


def write_pcm24(path: Path, samples: np.ndarray, sample_rate: int) -> None:
    clipped = np.clip(samples, -1.0, 1.0 - 1.0 / 8388608.0)
    integers = np.rint(clipped * 8388608.0).astype(np.int32)
    raw = np.column_stack(
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
        output.writeframes(raw)


def marker_wave(sample_rate: int, frequency_hz: float, duration_s: float) -> np.ndarray:
    cosine, sine = marker_template(sample_rate, frequency_hz, duration_s)
    del cosine
    return 0.35 * sine


def generate_fixture(wav: Path, timeline_path: Path) -> dict[str, Any]:
    sample_rate = 48_000
    duration_s = 8.0
    marker_times = [0.2, 2.2, 3.6]
    boundary_time = 2.7
    clock_slope = 1.002
    clock_offset = 0.75
    audio_marker_times = [
        clock_slope * value + clock_offset for value in marker_times
    ]
    boundary_audio_s = clock_slope * boundary_time + clock_offset
    timeline = {
        "schema_version": 1,
        "status": "valid-audio-lab-timeline",
        "backend": "openal-efx",
        "variant": "reload",
        "marker_contract": {
            "duration_s": 0.08,
            "start_hz": 880.0,
            "pre_boundary_hz": 1320.0,
            "post_boundary_hz": 1760.0,
        },
        "events": [
            {
                "name": "session_start_marker",
                "relative_ns": round(marker_times[0] * 1.0e9),
            },
            {
                "name": "pre_boundary_marker",
                "relative_ns": round(marker_times[1] * 1.0e9),
            },
            {
                "name": "sound_engine_reload_requested",
                "relative_ns": round(boundary_time * 1.0e9),
            },
            {
                "name": "post_boundary_marker",
                "relative_ns": round(marker_times[2] * 1.0e9),
            },
        ],
    }
    timeline_path.parent.mkdir(parents=True, exist_ok=True)
    timeline_path.write_text(
        json.dumps(timeline, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    rng = np.random.default_rng(0xD087)
    times = np.arange(round(duration_s * sample_rate)) / sample_rate
    samples = (
        0.055 * np.sin(2.0 * math.pi * 233.0 * times)
        + 0.025 * np.sin(2.0 * math.pi * 701.0 * times)
        + 0.008 * rng.standard_normal(times.size)
    )
    for start_s, frequency in zip(
        audio_marker_times,
        (880.0, 1320.0, 1760.0),
    ):
        marker = marker_wave(sample_rate, frequency, 0.08)
        start = round(start_s * sample_rate)
        samples[start : start + marker.size] += marker
    write_pcm24(wav, samples, sample_rate)
    return {
        "kind": "deterministic-audio-lab-alignment-fixture",
        "expected_clock_slope": clock_slope,
        "expected_clock_offset_s": clock_offset,
        "expected_boundary_audio_s": boundary_audio_s,
        "expected_marker_audio_s": audio_marker_times,
        "real_audio_capture": False,
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


def make_report(
    wav: Path,
    timeline_path: Path,
    channel: int,
    hop_ms: float,
    minimum_score: float,
    fixture: dict[str, Any] | None,
    real_audio_capture: bool,
    physical_loopback: bool,
) -> dict[str, Any]:
    sample_rate, samples, metadata = read_pcm_wav(wav, channel)
    timeline = load_timeline(timeline_path)
    alignment = align(
        sample_rate,
        samples,
        timeline,
        hop_ms,
        minimum_score,
    )
    return {
        "schema_version": 1,
        "status": "valid-audio-lab-alignment-diagnostic",
        "wav": metadata,
        "timeline_sha256": sha256_file(timeline_path),
        "timeline_backend": timeline.get("backend"),
        "timeline_variant": timeline.get("variant"),
        "channel": channel,
        "alignment": alignment,
        "fixture": fixture,
        "real_audio_capture": real_audio_capture if fixture is None else False,
        "physical_output_loopback_confirmed": (
            physical_loopback if fixture is None else False
        ),
        "release_calibrated": False,
        "claim_boundary": (
            "Three-tone recorder-to-Minecraft time alignment only; marker "
            "detection does not prove physical loopback, absence of audio "
            "dropouts, or calibrated acoustic output."
        ),
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    analyze_parser = subparsers.add_parser("analyze")
    analyze_parser.add_argument("--wav", type=Path, required=True)
    analyze_parser.add_argument("--timeline-json", type=Path, required=True)
    analyze_parser.add_argument("--channel", type=int, default=0)
    analyze_parser.add_argument("--output-json", type=Path, required=True)
    analyze_parser.add_argument("--hop-ms", type=float, default=2.0)
    analyze_parser.add_argument("--minimum-score", type=float, default=0.35)
    analyze_parser.add_argument("--real-audio-capture", action="store_true")
    analyze_parser.add_argument(
        "--physical-output-loopback-confirmed",
        action="store_true",
    )
    fixture_parser = subparsers.add_parser("fixture")
    fixture_parser.add_argument("--output-wav", type=Path, required=True)
    fixture_parser.add_argument("--output-timeline", type=Path, required=True)
    fixture_parser.add_argument("--output-json", type=Path, required=True)
    fixture_parser.add_argument("--hop-ms", type=float, default=2.0)
    fixture_parser.add_argument("--minimum-score", type=float, default=0.35)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        if args.command == "fixture":
            fixture = generate_fixture(
                args.output_wav,
                args.output_timeline,
            )
            report = make_report(
                args.output_wav,
                args.output_timeline,
                0,
                args.hop_ms,
                args.minimum_score,
                fixture,
                False,
                False,
            )
        else:
            report = make_report(
                args.wav,
                args.timeline_json,
                args.channel,
                args.hop_ms,
                args.minimum_score,
                None,
                args.real_audio_capture,
                args.physical_output_loopback_confirmed,
            )
        atomic_write_json(args.output_json, report)
        print(json.dumps(report, sort_keys=True))
        return 0
    except (FileNotFoundError, ValueError) as error:
        parser.exit(2, f"error: {error}\n")


if __name__ == "__main__":
    sys.exit(main())
