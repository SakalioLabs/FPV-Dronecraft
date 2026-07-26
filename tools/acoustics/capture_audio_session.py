#!/usr/bin/env python3
"""Enumerate or explicitly capture a bounded Windows audio session with FFmpeg.

Recording never starts from the default invocation.  The ``capture`` command
requires an exact enumerated DirectShow audio-device name and the literal
``--consent-to-record RECORD_AUDIO`` acknowledgement.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
import shutil
import subprocess
import sys
import time
import wave
from pathlib import Path
from typing import Any, Sequence

import numpy as np


CONSENT_TOKEN = "RECORD_AUDIO"
MAXIMUM_DURATION_SECONDS = 600.0


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


def inspect_pcm_wav(path: Path) -> dict[str, Any]:
    with wave.open(str(path), "rb") as source:
        if source.getcomptype() != "NONE":
            raise ValueError("capture WAV must contain uncompressed PCM")
        channels = source.getnchannels()
        sample_rate = source.getframerate()
        sample_width = source.getsampwidth()
        frame_count = source.getnframes()
        raw = source.readframes(frame_count)
    expected_bytes = frame_count * channels * sample_width
    if len(raw) != expected_bytes:
        raise ValueError("capture WAV PCM payload is truncated")
    samples = decode_pcm(raw, sample_width)
    if samples.size != frame_count * channels:
        raise ValueError("capture WAV decoded sample count is inconsistent")
    peak = float(np.max(np.abs(samples))) if samples.size else 0.0
    rms = (
        float(math.sqrt(float(np.mean(samples * samples))))
        if samples.size
        else 0.0
    )
    full_scale = 1.0 - 1.0 / float(1 << (sample_width * 8 - 1))
    clipped = int(np.count_nonzero(np.abs(samples) >= full_scale))
    return {
        "channels": channels,
        "sample_rate_hz": sample_rate,
        "sample_width_bits": sample_width * 8,
        "frame_count": frame_count,
        "duration_s": frame_count / sample_rate,
        "peak_linear": peak,
        "rms_linear": rms,
        "clipped_sample_count": clipped,
        "wav_bytes": path.stat().st_size,
        "wav_sha256": sha256_file(path),
    }


def parse_dshow_audio_devices(output: str) -> list[str]:
    devices: list[str] = []
    pattern = re.compile(r'^\s*(?:\[[^\]]+\]\s*)?"(.+)" \(audio\)\s*$')
    for line in output.splitlines():
        match = pattern.match(line)
        if match and match.group(1) not in devices:
            devices.append(match.group(1))
    return devices


def enumerate_dshow_audio_devices(ffmpeg: Path) -> tuple[list[str], str]:
    result = subprocess.run(
        [
            str(ffmpeg),
            "-hide_banner",
            "-list_devices",
            "true",
            "-f",
            "dshow",
            "-i",
            "dummy",
        ],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
        timeout=30,
    )
    combined = result.stdout + "\n" + result.stderr
    devices = parse_dshow_audio_devices(combined)
    if not devices:
        raise RuntimeError("FFmpeg did not enumerate any DirectShow audio device")
    return devices, hashlib.sha256(combined.encode("utf-8")).hexdigest()


def ffmpeg_version(ffmpeg: Path) -> str:
    result = subprocess.run(
        [str(ffmpeg), "-hide_banner", "-version"],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=True,
        timeout=30,
    )
    first = result.stdout.splitlines()[0].strip()
    if not first:
        raise RuntimeError("FFmpeg returned an empty version string")
    return first


def resolve_executable(value: Path) -> Path:
    raw = str(value)
    if value.parent == Path("."):
        discovered = shutil.which(raw)
        if discovered is not None:
            return Path(discovered).resolve()
    return value.expanduser().resolve()


def capture_command(
    ffmpeg: Path,
    device: str,
    duration_s: float,
    sample_rate_hz: int,
    channels: int,
    output: Path,
) -> list[str]:
    return [
        str(ffmpeg),
        "-hide_banner",
        "-nostdin",
        "-f",
        "dshow",
        "-i",
        f"audio={device}",
        "-t",
        f"{duration_s:.6f}",
        "-vn",
        "-ar",
        str(sample_rate_hz),
        "-ac",
        str(channels),
        "-c:a",
        "pcm_s24le",
        "-rf64",
        "auto",
        "-y",
        str(output),
    ]


def validate_capture_arguments(args: argparse.Namespace) -> None:
    if args.consent_to_record != CONSENT_TOKEN:
        raise ValueError(
            f"recording requires --consent-to-record {CONSENT_TOKEN}"
        )
    if not args.device.strip():
        raise ValueError("--device must be an exact non-blank device name")
    if not math.isfinite(args.duration_s) or not (
        0.1 <= args.duration_s <= MAXIMUM_DURATION_SECONDS
    ):
        raise ValueError(
            f"--duration-s must be in [0.1, {MAXIMUM_DURATION_SECONDS}]"
        )
    if not 8_000 <= args.sample_rate_hz <= 192_000:
        raise ValueError("--sample-rate-hz must be in [8000, 192000]")
    if args.channels not in (1, 2):
        raise ValueError("--channels must be 1 or 2")
    for path in (args.output_wav, args.output_report):
        if path.exists():
            raise FileExistsError(f"refusing to overwrite existing output: {path}")


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


def run_capture(args: argparse.Namespace) -> dict[str, Any]:
    validate_capture_arguments(args)
    ffmpeg = resolve_executable(args.ffmpeg)
    if not ffmpeg.is_file():
        raise FileNotFoundError(f"FFmpeg does not exist: {ffmpeg}")
    devices, enumeration_sha256 = enumerate_dshow_audio_devices(ffmpeg)
    if args.device not in devices:
        raise ValueError(
            "--device must exactly match an enumerated audio device; "
            f"available={devices}"
        )
    version = ffmpeg_version(ffmpeg)
    binary_sha256 = sha256_file(ffmpeg)

    args.output_wav.parent.mkdir(parents=True, exist_ok=True)
    temporary_wav = args.output_wav.with_name(
        args.output_wav.stem + f".tmp-{os.getpid()}.wav"
    )
    command = capture_command(
        ffmpeg,
        args.device,
        args.duration_s,
        args.sample_rate_hz,
        args.channels,
        temporary_wav,
    )
    started_ns = time.monotonic_ns()
    try:
        result = subprocess.run(
            command,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=False,
            timeout=args.duration_s + 30.0,
        )
        elapsed_s = (time.monotonic_ns() - started_ns) / 1.0e9
        stderr_sha256 = hashlib.sha256(
            result.stderr.encode("utf-8")
        ).hexdigest()
        if result.returncode != 0:
            raise RuntimeError(
                "FFmpeg capture failed with exit code "
                f"{result.returncode}; stderr_sha256={stderr_sha256}"
            )
        metadata = inspect_pcm_wav(temporary_wav)
        if metadata["sample_rate_hz"] != args.sample_rate_hz:
            raise ValueError("captured WAV sample rate does not match request")
        if metadata["channels"] != args.channels:
            raise ValueError("captured WAV channel count does not match request")
        tolerance_s = max(0.1, args.duration_s * 0.02)
        if abs(metadata["duration_s"] - args.duration_s) > tolerance_s:
            raise ValueError("captured WAV duration exceeds tolerance")
        os.replace(temporary_wav, args.output_wav)
    finally:
        temporary_wav.unlink(missing_ok=True)

    report = {
        "schema_version": 1,
        "status": "valid-bounded-audio-capture",
        "capture_authorized": True,
        "capture_backend": "ffmpeg-dshow",
        "device_name": args.device,
        "requested_duration_s": args.duration_s,
        "elapsed_wall_s": elapsed_s,
        "ffmpeg": {
            "path": str(ffmpeg),
            "version": version,
            "binary_sha256": binary_sha256,
            "enumeration_output_sha256": enumeration_sha256,
            "stderr_sha256": stderr_sha256,
        },
        "audio": inspect_pcm_wav(args.output_wav),
        "real_minecraft_capture": args.real_minecraft_capture,
        "physical_output_loopback_confirmed": (
            args.physical_output_loopback_confirmed
        ),
        "release_calibrated": False,
        "claim_boundary": (
            "Bounded PCM capture from an explicitly selected DirectShow "
            "endpoint; endpoint semantics, Minecraft stimulus timing, "
            "physical loopback, and acoustic calibration require separate "
            "evidence."
        ),
    }
    atomic_write_json(args.output_report, report)
    return report


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--ffmpeg",
        type=Path,
        default=Path("ffmpeg"),
        help="Path to an FFmpeg executable.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser(
        "list-devices",
        help="List DirectShow audio devices without recording.",
    )
    capture = subparsers.add_parser(
        "capture",
        help="Record a bounded PCM WAV after explicit acknowledgement.",
    )
    capture.add_argument("--device", required=True)
    capture.add_argument("--duration-s", type=float, required=True)
    capture.add_argument("--sample-rate-hz", type=int, default=48_000)
    capture.add_argument("--channels", type=int, default=2)
    capture.add_argument("--output-wav", type=Path, required=True)
    capture.add_argument("--output-report", type=Path, required=True)
    capture.add_argument("--consent-to-record", required=True)
    capture.add_argument(
        "--real-minecraft-capture",
        action="store_true",
    )
    capture.add_argument(
        "--physical-output-loopback-confirmed",
        action="store_true",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        ffmpeg = resolve_executable(args.ffmpeg)
        if args.command == "list-devices":
            devices, enumeration_sha256 = enumerate_dshow_audio_devices(ffmpeg)
            print(
                json.dumps(
                    {
                        "schema_version": 1,
                        "status": "devices-enumerated-no-recording",
                        "devices": devices,
                        "enumeration_output_sha256": enumeration_sha256,
                    },
                    indent=2,
                    ensure_ascii=False,
                    sort_keys=True,
                )
            )
            return 0
        report = run_capture(args)
        print(json.dumps(report, ensure_ascii=False, sort_keys=True))
        return 0
    except (
        OSError,
        subprocess.SubprocessError,
        RuntimeError,
        ValueError,
    ) as error:
        parser.exit(2, f"error: {error}\n")


if __name__ == "__main__":
    sys.exit(main())
