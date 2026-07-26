#!/usr/bin/env python3
"""Verify integrated snapshot/probe and six-source wet-stream performance."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def load(path: Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def verify(
    minecraft: dict[str, object], audio: dict[str, object]
) -> dict[str, object]:
    if minecraft.get("schema_version") != 1:
        raise ValueError("Minecraft performance schema must be 1")
    if minecraft.get("status") != "valid-benchmark":
        raise ValueError("Minecraft performance status is invalid")
    if audio.get("schema_version") != 1:
        raise ValueError("audio performance schema must be 1")
    if audio.get("status") != "valid-benchmark":
        raise ValueError("audio performance status is invalid")
    if minecraft.get("snapshot_cells") != 1521:
        raise ValueError("Minecraft snapshot extent changed")
    if minecraft.get("ray_count") != 128:
        raise ValueError("Minecraft runtime ray count changed")
    if minecraft.get("maximum_bounces") != 8:
        raise ValueError("Minecraft runtime bounce count changed")
    if minecraft.get("measured_iterations", 0) < 200:
        raise ValueError("Minecraft performance sample is too small")
    if audio.get("source_count") != 6:
        raise ValueError("audio source count changed")
    if audio.get("tones_per_rotor") != 15:
        raise ValueError("audio tone load changed")
    if audio.get("measured_iterations", 0) < 500:
        raise ValueError("audio performance sample is too small")
    cases = {case["requested_bytes"]: case for case in audio["cases"]}
    if set(cases) != {4096, 16384}:
        raise ValueError("audio buffer matrix changed")
    lifecycle = all(
        case["synthesizers_after_source_removal"] == 0
        and case["stream_closed"]
        and case["closed_read_empty"]
        and case["close_released_buffers"]
        for case in cases.values()
    )
    maximum_audio_fraction = max(
        case["p99_buffer_fraction"] for case in cases.values()
    )
    return {
        "schema_version": 1,
        "status": "valid-benchmark",
        "minecraft_capture_p99_ms": minecraft["capture_p99_ms"],
        "minecraft_probe_p99_ms": minecraft["probe_p99_ms"],
        "maximum_audio_p99_buffer_fraction": maximum_audio_fraction,
        "minimum_reported_thread_allocation_bytes_per_read": min(
            case["thread_allocated_bytes_per_read"]
            for case in cases.values()
        ),
        "gates": {
            "snapshots_complete": (
                minecraft["incomplete_snapshots"] == 0
            ),
            "capture_p99_at_most_4_ms": (
                minecraft["capture_p99_ms"] <= 4.0
            ),
            "worker_probe_p99_at_most_2_ms": (
                minecraft["probe_p99_ms"] <= 2.0
            ),
            "audio_p99_below_25_percent_of_buffer": (
                maximum_audio_fraction <= 0.25
            ),
            "wet_stream_lifecycle_cleanup": lifecycle,
            "openal_end_to_end_measured": False,
            "minecraft_release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Thread allocation counters exclude native bytes owned by direct "
            "ByteBuffers. OpenAL scheduling and real device replacement "
            "latency remain unmeasured."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft-report", type=Path, required=True)
    parser.add_argument("--audio-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    result = verify(
        load(args.minecraft_report), load(args.audio_report)
    )
    payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(payload, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
