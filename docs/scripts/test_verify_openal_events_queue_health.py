from __future__ import annotations

import copy
import hashlib
import unittest

import verify_openal_events_queue_health as verify


def _sha(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def fixture() -> tuple[dict, dict]:
    registered = 1_000_000_000
    cleanup = 5_500_000_000
    queue_events = []
    sequence = 0
    for index in range(4):
        for layer, source, offset in (
            ("motor", 11, 50_000_000),
            ("propeller", 12, 70_000_000),
        ):
            queue_events.append(
                {
                    "sequence": sequence,
                    "layer_sequence": index,
                    "layer": layer,
                    "source_id": source,
                    "buffer_id": 100 + sequence,
                    "event_callback_registered": True,
                    "host_monotonic_ns": (
                        registered
                        + (index + 1) * 1_000_000_000
                        + offset
                    ),
                }
            )
            sequence += 1
    queue_report = {
        "schema_version": 1,
        "status": "valid-openal-streaming-queue-trace",
        "openal_event_callback_registered": True,
        "events": queue_events,
    }
    queue_bytes = __import__("json").dumps(
        queue_report, sort_keys=True
    ).encode()

    events = []

    def add(kind, code, source, parameter, timestamp):
        events.append(
            {
                "sequence": len(events),
                "event_type": kind,
                "event_type_code": code,
                "source_id": source,
                "parameter": parameter,
                "message_length": 18,
                "host_monotonic_ns": timestamp,
                "callback_thread": "fixture-callback",
                "user_parameter_zero": True,
            }
        )

    add(
        "source_state_changed",
        verify.AL_EVENT_TYPE_SOURCE_STATE_CHANGED_SOFT,
        13,
        verify.AL_PLAYING,
        registered + 1_000_000,
    )
    add(
        "buffer_completed",
        verify.AL_EVENT_TYPE_BUFFER_COMPLETED_SOFT,
        13,
        1,
        registered + 20_000_000,
    )
    add(
        "source_state_changed",
        verify.AL_EVENT_TYPE_SOURCE_STATE_CHANGED_SOFT,
        13,
        verify.AL_STOPPED,
        registered + 21_000_000,
    )
    for index in range(4):
        add(
            "buffer_completed",
            verify.AL_EVENT_TYPE_BUFFER_COMPLETED_SOFT,
            11,
            1,
            registered + (index + 1) * 1_000_000_000,
        )
        add(
            "buffer_completed",
            verify.AL_EVENT_TYPE_BUFFER_COMPLETED_SOFT,
            12,
            1,
            registered + (index + 1) * 1_000_000_000 + 20_000_000,
        )
    report = {
        "schema_version": 1,
        "status": "valid-openal-events-queue-health-trace",
        "queue_report_sha256": _sha(queue_bytes),
        "registration": {
            "active_context": True,
            "extension_supported": True,
            "thread_name": "Sound engine",
            "existing_callback_pointer_zero": True,
            "existing_user_pointer_zero": True,
            "callback_registered": True,
            "callback_pointer_matches_owned": True,
            "buffer_completed_events_enabled": True,
            "source_state_events_enabled": True,
            "registered_ns": registered,
            "al_error": 0,
        },
        "positive_control": {
            "silent": True,
            "source_id": 13,
            "buffer_id": 200,
            "sample_rate_hz": 48_000,
            "sample_width_bits": 16,
            "channels": 1,
            "sample_frames": 960,
            "buffer_completed_count": 1,
            "stopped_event_count": 1,
        },
        "production_sources": {"motor": 11, "propeller": 12},
        "production_buffer_completed_count": {
            "motor": 4,
            "propeller": 4,
        },
        "production_source_stopped_events": 0,
        "events": events,
        "callback_thread_count": 1,
        "maximum_events": 256,
        "dropped_events": 0,
        "cleanup": {
            "thread_name": "Sound engine",
            "cleanup_ns": cleanup,
            "event_types_disabled": True,
            "callback_unregistered": True,
            "callback_pointer_zero": True,
            "user_pointer_zero": True,
            "control_source_deleted": True,
            "control_buffer_deleted": True,
            "events_at_cleanup": len(events),
            "quiet_window_ns": 200_000_000,
            "events_after_quiet_window": len(events),
            "no_events_after_cleanup": True,
            "al_error": 0,
        },
        "bounded_callback_observation": True,
        "underrun_event_defined_by_extension": False,
        "continuous_underrun_observation": False,
        "callback_underrun_counter_available": False,
        "openal_playback_capture": False,
        "real_audio_capture": False,
        "release_calibrated": False,
    }
    return report, queue_report


def run_verify(report, queue):
    queue_bytes = __import__("json").dumps(
        queue, sort_keys=True
    ).encode()
    report["queue_report_sha256"] = _sha(queue_bytes)
    return verify.verify(
        report,
        "a" * 64,
        queue,
        _sha(queue_bytes),
    )


class OpenAlEventsQueueHealthVerifyTest(unittest.TestCase):
    def test_accepts_owned_callback_and_completion_coverage(self):
        report, queue = fixture()
        result = run_verify(report, queue)
        self.assertEqual(11, result["callback_events"])
        self.assertEqual(
            {"motor": 4, "propeller": 4},
            result["production_buffer_completed_count"],
        )

    def test_rejects_detached_queue_hash(self):
        report, queue = fixture()
        report["queue_report_sha256"] = "0" * 64
        queue_bytes = __import__("json").dumps(
            queue, sort_keys=True
        ).encode()
        with self.assertRaisesRegex(ValueError, "hash changed"):
            verify.verify(report, "a" * 64, queue, _sha(queue_bytes))

    def test_accepts_context_global_transient_completion(self):
        report, queue = fixture()
        report["events"].append(
            {
                "sequence": len(report["events"]),
                "event_type": "source_state_changed",
                "event_type_code": (
                    verify.AL_EVENT_TYPE_SOURCE_STATE_CHANGED_SOFT
                ),
                "source_id": 99,
                "parameter": verify.AL_PLAYING,
                "message_length": 18,
                "host_monotonic_ns": 5_000_000_000,
                "callback_thread": "fixture-callback",
                "user_parameter_zero": True,
            }
        )
        report["events"].append(
            {
                "sequence": len(report["events"]),
                "event_type": "buffer_completed",
                "event_type_code": (
                    verify.AL_EVENT_TYPE_BUFFER_COMPLETED_SOFT
                ),
                "source_id": 99,
                "parameter": 1,
                "message_length": 18,
                "host_monotonic_ns": 5_100_000_000,
                "callback_thread": "fixture-callback",
                "user_parameter_zero": True,
            }
        )
        report["cleanup"]["events_at_cleanup"] = len(report["events"])
        report["cleanup"]["events_after_quiet_window"] = len(
            report["events"]
        )
        result = run_verify(report, queue)
        self.assertEqual(1, result["other_buffer_completed_count"])

    def test_rejects_untracked_transient_completion(self):
        report, queue = fixture()
        report["events"].append(
            {
                "sequence": len(report["events"]),
                "event_type": "buffer_completed",
                "event_type_code": (
                    verify.AL_EVENT_TYPE_BUFFER_COMPLETED_SOFT
                ),
                "source_id": 99,
                "parameter": 1,
                "message_length": 18,
                "host_monotonic_ns": 5_100_000_000,
                "callback_thread": "fixture-callback",
                "user_parameter_zero": True,
            }
        )
        report["cleanup"]["events_at_cleanup"] = len(report["events"])
        report["cleanup"]["events_after_quiet_window"] = len(
            report["events"]
        )
        with self.assertRaisesRegex(ValueError, "without a state event"):
            run_verify(report, queue)

    def test_rejects_existing_callback_replacement(self):
        report, queue = fixture()
        report["registration"]["existing_callback_pointer_zero"] = False
        with self.assertRaisesRegex(ValueError, "must be True"):
            run_verify(report, queue)

    def test_rejects_audible_positive_control(self):
        report, queue = fixture()
        report["positive_control"]["silent"] = False
        with self.assertRaisesRegex(ValueError, "must be True"):
            run_verify(report, queue)

    def test_rejects_production_source_detachment(self):
        report, queue = fixture()
        report["production_sources"]["motor"] = 99
        with self.assertRaisesRegex(ValueError, "identity is detached"):
            run_verify(report, queue)

    def test_rejects_queue_refill_without_callback_pointer(self):
        report, queue = fixture()
        queue["events"][0]["event_callback_registered"] = False
        with self.assertRaisesRegex(ValueError, "refill did not observe"):
            run_verify(report, queue)

    def test_rejects_completion_after_refill(self):
        report, queue = fixture()
        report["events"][3]["host_monotonic_ns"] += 100_000_000
        with self.assertRaisesRegex(ValueError, "preceded its completion"):
            run_verify(report, queue)

    def test_rejects_production_stop(self):
        report, queue = fixture()
        report["events"].append(
            {
                "sequence": len(report["events"]),
                "event_type": "source_state_changed",
                "event_type_code": (
                    verify.AL_EVENT_TYPE_SOURCE_STATE_CHANGED_SOFT
                ),
                "source_id": 11,
                "parameter": verify.AL_STOPPED,
                "message_length": 1,
                "host_monotonic_ns": 5_000_000_000,
                "callback_thread": "fixture-callback",
                "user_parameter_zero": True,
            }
        )
        report["cleanup"]["events_at_cleanup"] += 1
        report["cleanup"]["events_after_quiet_window"] += 1
        report["production_source_stopped_events"] = 1
        with self.assertRaisesRegex(ValueError, "reported AL_STOPPED"):
            run_verify(report, queue)

    def test_rejects_dropped_event(self):
        report, queue = fixture()
        report["dropped_events"] = 1
        with self.assertRaisesRegex(ValueError, "dropped"):
            run_verify(report, queue)

    def test_rejects_incomplete_cleanup(self):
        report, queue = fixture()
        report["cleanup"]["callback_pointer_zero"] = False
        with self.assertRaisesRegex(ValueError, "must be True"):
            run_verify(report, queue)

    def test_rejects_underrun_overclaim(self):
        report, queue = fixture()
        report["continuous_underrun_observation"] = True
        with self.assertRaisesRegex(ValueError, "must be False"):
            run_verify(report, queue)

    def test_rejects_missing_control_stop(self):
        report, queue = fixture()
        report["events"] = [
            event
            for event in report["events"]
            if not (
                event["event_type"] == "source_state_changed"
                and event["source_id"] == 13
                and event["parameter"] == verify.AL_STOPPED
            )
        ]
        for index, event in enumerate(report["events"]):
            event["sequence"] = index
        report["positive_control"]["stopped_event_count"] = 0
        report["cleanup"]["events_at_cleanup"] -= 1
        report["cleanup"]["events_after_quiet_window"] -= 1
        with self.assertRaisesRegex(ValueError, "not observed"):
            run_verify(report, queue)


if __name__ == "__main__":
    unittest.main()
