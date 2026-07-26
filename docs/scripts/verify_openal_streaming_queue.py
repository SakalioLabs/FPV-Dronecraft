#!/usr/bin/env python3
"""Verify production PCM-to-OpenAL streaming-buffer queue evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any

AL_NO_ERROR = 0
AL_PLAYING = 0x1012
AL_STREAMING = 0x1029
SAMPLE_RATE = 48_000
PCM_BYTES_PER_SAMPLE = 2


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _number(value: Any, label: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{label} must be numeric")
    result = float(value)
    if not math.isfinite(result):
        raise ValueError(f"{label} must be finite")
    return result


def verify(
    report: dict[str, Any],
    report_sha256: str,
    pcm_report: dict[str, Any],
    pcm_report_sha256: str,
) -> dict[str, Any]:
    if report.get("schema_version") != 1:
        raise ValueError("unsupported OpenAL streaming queue schema")
    if report.get("status") != "valid-openal-streaming-queue-trace":
        raise ValueError("OpenAL streaming queue status is invalid")
    if report.get("pcm_report_sha256") != pcm_report_sha256:
        raise ValueError("PCM report hash changed")
    if pcm_report.get("status") != "valid-doppler-production-chunk-trace":
        raise ValueError("bound PCM report status is invalid")

    target = report.get("target_events_per_layer")
    if isinstance(target, bool) or not isinstance(target, int) or target < 4:
        raise ValueError("target event count is invalid")
    events = report.get("events")
    chunks = pcm_report.get("chunks")
    if not isinstance(events, list) or not isinstance(chunks, list):
        raise ValueError("event or PCM chunk list is missing")
    by_pcm_key = {
        (chunk.get("layer"), chunk.get("layer_sequence")): chunk
        for chunk in chunks
    }
    layer_counts = {"motor": 0, "propeller": 0}
    source_ids: dict[str, set[int]] = {
        "motor": set(),
        "propeller": set(),
    }
    buffer_ids: set[int] = set()
    minimum_queued = 2**31 - 1
    maximum_processed = 0
    maximum_sample_offset = 0
    latency_values: list[float] = []
    events_supported_values: set[bool] = set()
    callback_registered_values: set[bool] = set()
    previous_host_ns_by_source: dict[int, int] = {}

    for sequence, event in enumerate(events):
        if event.get("sequence") != sequence:
            raise ValueError("queue event sequence is not contiguous")
        layer = event.get("layer")
        if layer not in layer_counts:
            raise ValueError("queue event layer is invalid")
        layer_sequence = event.get("layer_sequence")
        if layer_sequence != layer_counts[layer]:
            raise ValueError("queue event layer sequence is not contiguous")
        layer_counts[layer] += 1

        chunk = by_pcm_key.get((layer, layer_sequence))
        if chunk is None:
            raise ValueError("queue event is detached from PCM chunk")
        for field in (
            "entity_id",
            "simulation_time_nanos",
            "pcm_bytes",
            "pcm_sha256",
        ):
            if event.get(field) != chunk.get(field):
                raise ValueError(f"queued buffer detached from PCM {field}")
        ratio = _number(event.get("doppler_frequency_ratio"), "Doppler ratio")
        chunk_ratio = _number(
            chunk.get("doppler_frequency_ratio"), "PCM Doppler ratio"
        )
        if abs(ratio - chunk_ratio) > 1.0e-12:
            raise ValueError("queued buffer detached from PCM Doppler ratio")

        if event.get("thread_name") != "Sound engine":
            raise ValueError("OpenAL queue query left the sound thread")
        source_id = event.get("source_id")
        buffer_id = event.get("buffer_id")
        if (
            isinstance(source_id, bool)
            or not isinstance(source_id, int)
            or source_id <= 0
            or isinstance(buffer_id, bool)
            or not isinstance(buffer_id, int)
            or buffer_id <= 0
        ):
            raise ValueError("OpenAL source or buffer id is invalid")
        source_ids[layer].add(source_id)
        if buffer_id in buffer_ids:
            raise ValueError("OpenAL buffer id was reused within the trace")
        buffer_ids.add(buffer_id)
        host_monotonic_ns = event.get("host_monotonic_ns")
        if (
            isinstance(host_monotonic_ns, bool)
            or not isinstance(host_monotonic_ns, int)
            or host_monotonic_ns <= previous_host_ns_by_source.get(
                source_id, 0
            )
        ):
            raise ValueError("OpenAL queue host timestamp is invalid")
        previous_host_ns_by_source[source_id] = host_monotonic_ns

        if (
            event.get("buffer_valid") is not True
            or event.get("buffer_frequency_hz") != SAMPLE_RATE
            or event.get("buffer_bits") != 16
            or event.get("buffer_channels") != 1
            or event.get("buffer_bytes") != event.get("pcm_bytes")
            or event.get("buffer_bytes") != SAMPLE_RATE * PCM_BYTES_PER_SAMPLE
        ):
            raise ValueError("queued OpenAL buffer format is invalid")
        if event.get("source_state") != AL_PLAYING:
            raise ValueError("queued OpenAL source is not playing")
        if event.get("source_type") != AL_STREAMING:
            raise ValueError("queued OpenAL source is not streaming")
        queued = event.get("buffers_queued")
        processed = event.get("buffers_processed")
        sample_offset = event.get("sample_offset")
        if (
            isinstance(queued, bool)
            or not isinstance(queued, int)
            or queued != 4
            or isinstance(processed, bool)
            or not isinstance(processed, int)
            or processed != 0
            or isinstance(sample_offset, bool)
            or not isinstance(sample_offset, int)
            or sample_offset < 0
            or sample_offset >= SAMPLE_RATE
        ):
            raise ValueError("post-queue depth or sample offset is invalid")
        minimum_queued = min(minimum_queued, queued)
        maximum_processed = max(maximum_processed, processed)
        maximum_sample_offset = max(maximum_sample_offset, sample_offset)

        source_latency_supported = event.get("source_latency_supported")
        source_offset = _number(
            event.get("source_offset_seconds"), "source offset"
        )
        source_latency = _number(
            event.get("source_latency_seconds"), "source latency"
        )
        if source_latency_supported is True:
            if (
                abs(source_offset - sample_offset / SAMPLE_RATE)
                > 1.0 / SAMPLE_RATE
                or source_latency < 0.0
                or source_latency > 0.5
            ):
                raise ValueError("source-latency telemetry is inconsistent")
            latency_values.append(source_latency)
        elif source_latency_supported is False:
            if source_offset != 0.0 or source_latency != 0.0:
                raise ValueError("unsupported source latency was populated")
        else:
            raise ValueError("source latency support flag is invalid")

        events_supported = event.get("events_supported")
        if not isinstance(events_supported, bool):
            raise ValueError("OpenAL events support flag is invalid")
        events_supported_values.add(events_supported)
        callback_registered = event.get("event_callback_registered")
        if not isinstance(callback_registered, bool):
            raise ValueError("event callback registration flag is invalid")
        callback_registered_values.add(callback_registered)
        if event.get("al_error") != AL_NO_ERROR:
            raise ValueError("OpenAL queue query reported an error")

    if layer_counts != {"motor": target, "propeller": target}:
        raise ValueError("OpenAL queue layer coverage is incomplete")
    if (
        report.get("motor_events") != target
        or report.get("propeller_events") != target
    ):
        raise ValueError("reported OpenAL queue event counts changed")
    if any(len(values) != 1 for values in source_ids.values()):
        raise ValueError("a layer changed OpenAL source id")
    if source_ids["motor"] == source_ids["propeller"]:
        raise ValueError("motor and propeller shared one OpenAL source")
    if len(events_supported_values) != 1:
        raise ValueError("OpenAL events support changed within the trace")
    events_supported = events_supported_values.pop()
    if report.get("openal_events_extension_supported") is not events_supported:
        raise ValueError("OpenAL events capability summary changed")
    if len(callback_registered_values) != 1:
        raise ValueError("OpenAL callback registration changed within trace")
    callback_registered = callback_registered_values.pop()
    if (
        report.get("openal_event_callback_registered")
        is not callback_registered
        or callback_registered and not events_supported
    ):
        raise ValueError("OpenAL callback registration summary changed")

    required_true = (
        "production_pcm_to_al_buffer_id_bound",
        "openal_queue_state_observed",
        "openal_source_state_observed",
    )
    for key in required_true:
        if report.get(key) is not True:
            raise ValueError(f"{key} must be true")
    required_false = (
        "continuous_underrun_observation",
        "callback_underrun_counter_available",
        "openal_playback_capture",
        "real_audio_capture",
        "release_calibrated",
    )
    for key in required_false:
        if report.get(key) is not False:
            raise ValueError(f"{key} must be false")
    if (
        report.get("minecraft_streaming_buffer_seconds") != 1
        or report.get("minecraft_initial_queue_target") != 4
    ):
        raise ValueError("Minecraft streaming queue contract changed")

    return {
        "schema_version": 1,
        "status": "valid-openal-streaming-queue-trace",
        "source_report_sha256": report_sha256,
        "pcm_report_sha256": pcm_report_sha256,
        "events": len(events),
        "motor_events": layer_counts["motor"],
        "propeller_events": layer_counts["propeller"],
        "distinct_sources": 2,
        "distinct_buffers": len(buffer_ids),
        "minimum_buffers_queued": minimum_queued,
        "maximum_buffers_processed_after_refill": maximum_processed,
        "maximum_sample_offset": maximum_sample_offset,
        "source_latency_supported": bool(latency_values),
        "minimum_source_latency_seconds": (
            min(latency_values) if latency_values else 0.0
        ),
        "maximum_source_latency_seconds": (
            max(latency_values) if latency_values else 0.0
        ),
        "openal_events_extension_supported": events_supported,
        "openal_event_callback_registered": callback_registered,
        "gates": {
            "production_pcm_hash_to_buffer_id_bound": True,
            "buffer_format_matches_pcm16_mono_48000": True,
            "four_buffers_queued_after_every_refill": True,
            "source_playing_and_streaming_after_every_refill": True,
            "source_latency_consistent_with_sample_offset": True,
            "callback_registration_state_consistent": True,
            "continuous_underrun_observation": False,
            "callback_underrun_counter_available": False,
            "openal_playback_capture": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Production PCM hashes are bound to OpenAL buffer ids and "
            "post-queue source state. Discrete post-refill observations do not "
            "prove continuous underrun freedom or rendered physical output."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--pcm-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()

    report_bytes = args.report.read_bytes()
    pcm_report_bytes = args.pcm_report.read_bytes()
    result = verify(
        json.loads(report_bytes),
        _sha256(report_bytes),
        json.loads(pcm_report_bytes),
        _sha256(pcm_report_bytes),
    )
    rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(rendered, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
