#!/usr/bin/env python3
"""Verify bounded AL_SOFT_events queue-health evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import defaultdict
from pathlib import Path
from typing import Any


AL_EVENT_TYPE_BUFFER_COMPLETED_SOFT = 0x19A4
AL_EVENT_TYPE_SOURCE_STATE_CHANGED_SOFT = 0x19A5
AL_INITIAL = 0x1011
AL_PLAYING = 0x1012
AL_PAUSED = 0x1013
AL_STOPPED = 0x1014
VALID_SOURCE_STATES = {AL_INITIAL, AL_PLAYING, AL_PAUSED, AL_STOPPED}


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _positive_integer(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer")
    return value


def _required_flags(
    value: Any,
    label: str,
    expected: dict[str, bool],
) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"{label} is missing")
    for key, required in expected.items():
        if value.get(key) is not required:
            raise ValueError(f"{label}.{key} must be {required}")
    return value


def verify(
    report: dict[str, Any],
    report_sha256: str,
    queue_report: dict[str, Any],
    queue_report_sha256: str,
) -> dict[str, Any]:
    if report.get("schema_version") != 1:
        raise ValueError("unsupported OpenAL events schema")
    if (
        report.get("status")
        != "valid-openal-events-queue-health-trace"
    ):
        raise ValueError("OpenAL events status is invalid")
    if report.get("queue_report_sha256") != queue_report_sha256:
        raise ValueError("queue report hash changed")
    if (
        queue_report.get("status")
        != "valid-openal-streaming-queue-trace"
    ):
        raise ValueError("bound queue report status is invalid")
    if queue_report.get("openal_event_callback_registered") is not True:
        raise ValueError("bound queue report did not observe the callback")

    registration = _required_flags(
        report.get("registration"),
        "registration",
        {
            "active_context": True,
            "extension_supported": True,
            "existing_callback_pointer_zero": True,
            "existing_user_pointer_zero": True,
            "callback_registered": True,
            "callback_pointer_matches_owned": True,
            "buffer_completed_events_enabled": True,
            "source_state_events_enabled": True,
        },
    )
    if (
        registration.get("thread_name") != "Sound engine"
        or registration.get("al_error") != 0
    ):
        raise ValueError("registration thread or AL error is invalid")
    registered_ns = _positive_integer(
        registration.get("registered_ns"), "registered_ns"
    )

    queue_events = queue_report.get("events")
    if not isinstance(queue_events, list) or not queue_events:
        raise ValueError("bound queue events are missing")
    queue_sources: dict[str, set[int]] = {
        "motor": set(),
        "propeller": set(),
    }
    queue_buffers: set[int] = set()
    queue_times: dict[int, list[int]] = defaultdict(list)
    for queue_event in queue_events:
        if not isinstance(queue_event, dict):
            raise ValueError("bound queue event is malformed")
        if queue_event.get("event_callback_registered") is not True:
            raise ValueError("a queue refill did not observe the callback")
        layer = queue_event.get("layer")
        if layer not in queue_sources:
            raise ValueError("bound queue layer is invalid")
        source = _positive_integer(
            queue_event.get("source_id"), "queue source"
        )
        buffer_id = _positive_integer(
            queue_event.get("buffer_id"), "queue buffer"
        )
        host_ns = _positive_integer(
            queue_event.get("host_monotonic_ns"), "queue host time"
        )
        queue_sources[layer].add(source)
        queue_buffers.add(buffer_id)
        queue_times[source].append(host_ns)
    if any(len(sources) != 1 for sources in queue_sources.values()):
        raise ValueError("bound queue source identity is unstable")
    motor_source = next(iter(queue_sources["motor"]))
    propeller_source = next(iter(queue_sources["propeller"]))
    if motor_source == propeller_source:
        raise ValueError("production layers share one source")

    reported_sources = report.get("production_sources")
    if reported_sources != {
        "motor": motor_source,
        "propeller": propeller_source,
    }:
        raise ValueError("production source identity is detached")

    control = _required_flags(
        report.get("positive_control"),
        "positive_control",
        {"silent": True},
    )
    control_source = _positive_integer(
        control.get("source_id"), "control source"
    )
    control_buffer = _positive_integer(
        control.get("buffer_id"), "control buffer"
    )
    if (
        control_source in {motor_source, propeller_source}
        or control_buffer in queue_buffers
        or control.get("sample_rate_hz") != 48_000
        or control.get("sample_width_bits") != 16
        or control.get("channels") != 1
        or control.get("sample_frames") != 960
    ):
        raise ValueError("silent positive-control contract is invalid")

    cleanup = _required_flags(
        report.get("cleanup"),
        "cleanup",
        {
            "event_types_disabled": True,
            "callback_unregistered": True,
            "callback_pointer_zero": True,
            "user_pointer_zero": True,
            "control_source_deleted": True,
            "control_buffer_deleted": True,
            "no_events_after_cleanup": True,
        },
    )
    cleanup_ns = _positive_integer(
        cleanup.get("cleanup_ns"), "cleanup_ns"
    )
    quiet_window_ns = _positive_integer(
        cleanup.get("quiet_window_ns"), "quiet_window_ns"
    )
    if (
        cleanup.get("thread_name") != "Sound engine"
        or cleanup.get("al_error") != 0
        or cleanup_ns <= registered_ns
        or quiet_window_ns < 150_000_000
    ):
        raise ValueError("cleanup timing, thread, or AL error is invalid")

    events = report.get("events")
    if not isinstance(events, list) or not events:
        raise ValueError("callback event list is missing")
    maximum_events = _positive_integer(
        report.get("maximum_events"), "maximum_events"
    )
    if len(events) > maximum_events or report.get("dropped_events") != 0:
        raise ValueError("bounded callback dropped or overflowed events")
    if (
        cleanup.get("events_at_cleanup") != len(events)
        or cleanup.get("events_after_quiet_window") != len(events)
    ):
        raise ValueError("callback events changed after cleanup")

    completed_tokens: dict[int, list[int]] = defaultdict(list)
    source_states: dict[int, list[int]] = defaultdict(list)
    callback_threads: set[str] = set()
    for sequence, event in enumerate(events):
        if not isinstance(event, dict) or event.get("sequence") != sequence:
            raise ValueError("callback sequence is not contiguous")
        event_type = event.get("event_type")
        event_type_code = event.get("event_type_code")
        source = _positive_integer(event.get("source_id"), "event source")
        parameter = event.get("parameter")
        message_length = event.get("message_length")
        host_ns = _positive_integer(
            event.get("host_monotonic_ns"), "callback host time"
        )
        callback_thread = event.get("callback_thread")
        if (
            isinstance(message_length, bool)
            or not isinstance(message_length, int)
            or message_length < 0
            or not isinstance(callback_thread, str)
            or not callback_thread
            or event.get("user_parameter_zero") is not True
            or not registered_ns <= host_ns <= cleanup_ns
        ):
            raise ValueError("callback event metadata is invalid")
        callback_threads.add(callback_thread)
        if event_type == "buffer_completed":
            if event_type_code != AL_EVENT_TYPE_BUFFER_COMPLETED_SOFT:
                raise ValueError("buffer event code is invalid")
            count = _positive_integer(parameter, "completed buffer count")
            completed_tokens[source].extend([host_ns] * count)
        elif event_type == "source_state_changed":
            if (
                event_type_code
                != AL_EVENT_TYPE_SOURCE_STATE_CHANGED_SOFT
                or parameter not in VALID_SOURCE_STATES
            ):
                raise ValueError("source-state event is invalid")
            source_states[source].append(parameter)
        else:
            raise ValueError("unexpected callback event type")

    if report.get("callback_thread_count") != len(callback_threads):
        raise ValueError("callback thread summary is detached")

    known_sources = {motor_source, propeller_source, control_source}
    other_completed_sources = set(completed_tokens) - known_sources
    if not other_completed_sources.issubset(source_states):
        raise ValueError(
            "an untracked source completed a buffer without a state event"
        )
    other_completed_count = sum(
        len(completed_tokens[source])
        for source in other_completed_sources
    )

    expected_completed = {
        "motor": len(completed_tokens[motor_source]),
        "propeller": len(completed_tokens[propeller_source]),
    }
    if report.get("production_buffer_completed_count") != expected_completed:
        raise ValueError("production completion summary is detached")
    if any(value < 3 for value in expected_completed.values()):
        raise ValueError("production completion coverage is insufficient")

    for source in (motor_source, propeller_source):
        tokens = sorted(completed_tokens[source])
        refills = sorted(queue_times[source])
        if len(tokens) < len(refills):
            raise ValueError("a queue refill lacks completed-buffer coverage")
        for index, refill_ns in enumerate(refills):
            completed_by_refill = sum(
                token_ns <= refill_ns for token_ns in tokens
            )
            if completed_by_refill < index + 1:
                raise ValueError(
                    "a queue refill preceded its completion callback"
                )
        if any(not registered_ns <= value <= cleanup_ns for value in refills):
            raise ValueError("bound queue refill is outside callback lifetime")

    control_completed = len(completed_tokens[control_source])
    control_stopped = source_states[control_source].count(AL_STOPPED)
    if (
        control.get("buffer_completed_count") != control_completed
        or control.get("stopped_event_count") != control_stopped
        or control_completed < 1
        or control_stopped < 1
    ):
        raise ValueError("silent positive control was not observed")

    production_stopped = sum(
        source_states[source].count(AL_STOPPED)
        for source in (motor_source, propeller_source)
    )
    if (
        production_stopped != 0
        or report.get("production_source_stopped_events") != 0
    ):
        raise ValueError("a production source reported AL_STOPPED")

    for key, required in (
        ("bounded_callback_observation", True),
        ("underrun_event_defined_by_extension", False),
        ("continuous_underrun_observation", False),
        ("callback_underrun_counter_available", False),
        ("openal_playback_capture", False),
        ("real_audio_capture", False),
        ("release_calibrated", False),
    ):
        if report.get(key) is not required:
            raise ValueError(f"{key} must be {required}")

    return {
        "schema_version": 1,
        "status": "valid-openal-events-queue-health-trace",
        "source_report_sha256": report_sha256,
        "queue_report_sha256": queue_report_sha256,
        "callback_events": len(events),
        "callback_threads": sorted(callback_threads),
        "observation_duration_seconds": (
            cleanup_ns - registered_ns
        ) / 1e9,
        "production_buffer_completed_count": expected_completed,
        "production_source_stopped_events": production_stopped,
        "positive_control_buffer_completed_count": control_completed,
        "positive_control_stopped_events": control_stopped,
        "other_buffer_completed_count": other_completed_count,
        "dropped_events": 0,
        "quiet_window_seconds": quiet_window_ns / 1e9,
        "gates": {
            "existing_callback_ownership_preserved": True,
            "silent_positive_control_observed": True,
            "both_production_sources_observed": True,
            "each_traced_refill_has_cumulative_completion_coverage": True,
            "callback_pointer_observed_at_every_refill": True,
            "no_callback_reported_production_stop": True,
            "bounded_probe_dropped_no_events": True,
            "callback_unregistered_and_resources_deleted": True,
            "no_events_after_cleanup": True,
            "underrun_event_defined_by_extension": False,
            "continuous_underrun_observation": False,
            "openal_playback_capture": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "A bounded, temporarily owned AL_SOFT_events callback observed "
            "buffer completion for both production sources and a silent "
            "positive control. AL_SOFT_events defines no underrun event; "
            "absence of a callback-reported source stop is not rendered or "
            "physical-output continuity proof."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--queue-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    report_bytes = args.report.read_bytes()
    queue_bytes = args.queue_report.read_bytes()
    result = verify(
        json.loads(report_bytes),
        _sha256(report_bytes),
        json.loads(queue_bytes),
        _sha256(queue_bytes),
    )
    rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(rendered, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
