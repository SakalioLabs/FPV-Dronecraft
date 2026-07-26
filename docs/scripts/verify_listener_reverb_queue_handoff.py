#!/usr/bin/env python3
"""Verify D108 live Minecraft listener-reverb queue handoff evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
from pathlib import Path
from typing import Any


SAMPLE_RATE = 48_000
HISTORY_FRAMES = 24_000
BUFFER_BYTES = SAMPLE_RATE * 2
INITIAL_QUEUE_BUFFERS = 4
STAGES = ("resource-create", "parameter-write", "source-route")
AL_INITIAL = 4113
AL_STREAMING = 4137
MAXIMUM_FIRST_QUEUE_LATENCY_MS = (2_048 / SAMPLE_RATE) * 1_000
MAXIMUM_INITIAL_QUEUE_FILL_MS = 50.0
MAXIMUM_PREROLL_MS = MAXIMUM_FIRST_QUEUE_LATENCY_MS * 0.25
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def verify(
    report: dict[str, Any],
    report_sha256: str,
    d107: dict[str, Any],
    d107_bytes: bytes,
    failover: dict[str, Any],
    failover_bytes: bytes,
) -> dict[str, Any]:
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "valid-listener-reverb-queue-handoff"
        or report.get("sample_rate_hz") != SAMPLE_RATE
        or report.get("history_frames") != HISTORY_FRAMES
        or report.get("minecraft_stream_buffer_seconds") != 1
        or report.get("minecraft_initial_queue_buffers")
        != INITIAL_QUEUE_BUFFERS
    ):
        raise ValueError("D108 report shape or queue contract changed")
    if (
        d107.get("status")
        != "valid-listener-reverb-history-handoff"
        or d107.get("history_frames") != HISTORY_FRAMES
        or d107.get("all_cases_passed") is not True
        or report.get("source_d107_report_sha256")
        != _sha256(d107_bytes)
    ):
        raise ValueError("D108 is detached from D107")
    if (
        failover.get("status") != "valid-openal-efx-fault-failover"
        or report.get("source_fault_failover_report_sha256")
        != _sha256(failover_bytes)
    ):
        raise ValueError("D108 is detached from the live fault cycles")

    thresholds = report.get("thresholds", {})
    expected_thresholds = {
        "maximum_first_queue_latency_ms": MAXIMUM_FIRST_QUEUE_LATENCY_MS,
        "maximum_initial_queue_fill_ms": MAXIMUM_INITIAL_QUEUE_FILL_MS,
        "maximum_preroll_ms": MAXIMUM_PREROLL_MS,
    }
    for key, expected in expected_thresholds.items():
        value = thresholds.get(key)
        if not isinstance(value, (int, float)) or not math.isclose(
            value, expected, rel_tol=0.0, abs_tol=1.0e-12
        ):
            raise ValueError("D108 registered threshold changed")

    cycles = report.get("cycles")
    fault_cycles = failover.get("fault_cycles")
    if (
        not isinstance(cycles, list)
        or [item.get("stage") for item in cycles] != list(STAGES)
        or not isinstance(fault_cycles, list)
        or [item.get("stage") for item in fault_cycles] != list(STAGES)
    ):
        raise ValueError("D108 fault cycle order changed")
    stream_sequences: list[int] = []
    verified_cycles = []
    previous_ready_nanos = 0
    for cycle in cycles:
        armed = cycle.get("armed_nanos")
        requested = cycle.get("fallback_requested_nanos")
        restarted = cycle.get("shadow_restart_nanos")
        ready = cycle.get("history_ready_nanos")
        if (
            not all(
                isinstance(value, int)
                for value in (armed, requested, restarted, ready)
            )
            or not (
                previous_ready_nanos < armed < requested < restarted < ready
            )
        ):
            raise ValueError("D108 lifecycle clocks are not ordered")
        previous_ready_nanos = ready
        if (
            cycle.get("history_frames_at_arm") != HISTORY_FRAMES
            or cycle.get("history_frames_at_fallback") != HISTORY_FRAMES
            or cycle.get("synthesizers_at_fallback", 0) < 1
            or cycle.get("shadow_active_at_fallback") is not False
            or cycle.get("wet_active_at_fallback") is not False
            or cycle.get("history_frames_at_shadow_restart") != 2_400
            or cycle.get("synthesizers_at_shadow_restart", 0) < 1
        ):
            raise ValueError("D108 history/phase ownership changed")
        queued = cycle.get("queued")
        if (
            not isinstance(queued, list)
            or len(queued) != INITIAL_QUEUE_BUFFERS
            or [item.get("queue_index") for item in queued]
            != list(range(INITIAL_QUEUE_BUFFERS))
        ):
            raise ValueError("D108 initial queue is incomplete")
        stream_sequence = queued[0].get("stream_sequence")
        source = queued[0].get("source")
        if (
            not isinstance(stream_sequence, int)
            or stream_sequence < 1
            or any(
                item.get("stream_sequence") != stream_sequence
                or item.get("source") != source
                for item in queued
            )
        ):
            raise ValueError("D108 stream/source ownership changed")
        stream_sequences.append(stream_sequence)
        previous_queue_nanos = requested
        hashes: set[str] = set()
        for index, event in enumerate(queued):
            queued_nanos = event.get("queued_nanos")
            latency_nanos = event.get("fallback_to_queue_nanos")
            if (
                not isinstance(queued_nanos, int)
                or queued_nanos <= previous_queue_nanos
                or not isinstance(latency_nanos, int)
                or latency_nanos != queued_nanos - requested
            ):
                raise ValueError("D108 queue clocks are detached")
            previous_queue_nanos = queued_nanos
            pcm_hash = event.get("pcm_sha256")
            if (
                event.get("pcm_bytes") != BUFFER_BYTES
                or event.get("buffer_frequency") != SAMPLE_RATE
                or event.get("buffer_bits") != 16
                or event.get("buffer_channels") != 1
                or event.get("buffer_bytes") != BUFFER_BYTES
                or event.get("source_state") != AL_INITIAL
                or event.get("source_type") != AL_STREAMING
                or event.get("buffers_queued") != index + 1
                or event.get("buffers_processed") != 0
                or event.get("sample_offset") != 0
                or event.get("preroll_frames") != HISTORY_FRAMES
                or not isinstance(event.get("preroll_nanos"), int)
                or event.get("preroll_nanos") <= 0
                or not isinstance(pcm_hash, str)
                or not SHA256_PATTERN.fullmatch(pcm_hash)
                or pcm_hash in hashes
            ):
                raise ValueError("D108 native buffer/PCM contract changed")
            hashes.add(pcm_hash)
        first_queue_ms = queued[0]["fallback_to_queue_nanos"] / 1.0e6
        fill_ms = queued[-1]["fallback_to_queue_nanos"] / 1.0e6
        preroll_ms = queued[0]["preroll_nanos"] / 1.0e6
        if (
            queued[0].get("first_nonzero_frame") != 0
            or first_queue_ms > MAXIMUM_FIRST_QUEUE_LATENCY_MS
            or fill_ms > MAXIMUM_INITIAL_QUEUE_FILL_MS
            or preroll_ms > MAXIMUM_PREROLL_MS
        ):
            raise ValueError("D108 first-wet queue budget failed")
        verified_cycles.append(
            {
                "stage": cycle["stage"],
                "stream_sequence": stream_sequence,
                "source": source,
                "first_queue_latency_ms": first_queue_ms,
                "initial_queue_fill_ms": fill_ms,
                "preroll_ms": preroll_ms,
                "first_nonzero_frame": queued[0][
                    "first_nonzero_frame"
                ],
            }
        )
    if stream_sequences != sorted(set(stream_sequences)):
        raise ValueError("D108 did not create one new stream per fault")

    required_true = (
        "active_source_restarted",
        "exclusive_wet_owner",
        "client_gametest_measured",
        "physical_endpoint_opened",
    )
    required_false = (
        "openal_shadow_source_created",
        "physical_output_captured",
        "captures_audio",
        "release_calibrated",
    )
    if any(report.get(key) is not True for key in required_true):
        raise ValueError("D108 measured ownership flag changed")
    if any(report.get(key) is not False for key in required_false):
        raise ValueError("D108 claim-boundary flag changed")

    return {
        "schema_version": 1,
        "status": "verified-listener-reverb-queue-handoff",
        "source_report_sha256": report_sha256,
        "source_d107_report_sha256": _sha256(d107_bytes),
        "source_fault_failover_report_sha256": _sha256(failover_bytes),
        "cycles": verified_cycles,
        "maximum_first_queue_latency_ms": max(
            item["first_queue_latency_ms"] for item in verified_cycles
        ),
        "maximum_initial_queue_fill_ms": max(
            item["initial_queue_fill_ms"] for item in verified_cycles
        ),
        "maximum_preroll_ms": max(
            item["preroll_ms"] for item in verified_cycles
        ),
        "openal_shadow_source_created": False,
        "active_source_restarted": True,
        "exclusive_wet_owner": True,
        "client_gametest_measured": True,
        "physical_endpoint_opened": True,
        "physical_output_captured": False,
        "captures_audio": False,
        "release_calibrated": False,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--d107-report", type=Path, required=True)
    parser.add_argument("--fault-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report_bytes = args.report.read_bytes()
    d107_bytes = args.d107_report.read_bytes()
    failover_bytes = args.fault_report.read_bytes()
    result = verify(
        json.loads(report_bytes),
        _sha256(report_bytes),
        json.loads(d107_bytes),
        d107_bytes,
        json.loads(failover_bytes),
        failover_bytes,
    )
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
