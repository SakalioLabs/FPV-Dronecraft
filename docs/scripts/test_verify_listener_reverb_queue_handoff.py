from __future__ import annotations

import copy
import hashlib
import unittest

import verify_listener_reverb_queue_handoff as verify


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fixture():
    d107_bytes = b'{"fixture":"d107"}'
    failover_bytes = b'{"fixture":"failover"}'
    d107 = {
        "status": "valid-listener-reverb-history-handoff",
        "history_frames": verify.HISTORY_FRAMES,
        "all_cases_passed": True,
    }
    failover = {
        "status": "valid-openal-efx-fault-failover",
        "fault_cycles": [{"stage": stage} for stage in verify.STAGES],
    }
    cycles = []
    cursor = 1_000_000_000
    for cycle_index, stage in enumerate(verify.STAGES):
        armed = cursor + 10_000_000
        requested = armed + 50_000_000
        queued = []
        for index in range(verify.INITIAL_QUEUE_BUFFERS):
            queued_nanos = requested + (index + 1) * 8_000_000
            queued.append(
                {
                    "queue_index": index,
                    "stream_sequence": cycle_index + 1,
                    "source": 4,
                    "buffer": 10 + index,
                    "queued_nanos": queued_nanos,
                    "fallback_to_queue_nanos": (
                        queued_nanos - requested
                    ),
                    "pcm_bytes": verify.BUFFER_BYTES,
                    "pcm_sha256": f"{cycle_index * 4 + index + 1:064x}",
                    "first_nonzero_frame": 0,
                    "preroll_frames": verify.HISTORY_FRAMES,
                    "preroll_nanos": 1_000_000,
                    "buffer_frequency": verify.SAMPLE_RATE,
                    "buffer_bits": 16,
                    "buffer_channels": 1,
                    "buffer_bytes": verify.BUFFER_BYTES,
                    "source_state": verify.AL_INITIAL,
                    "source_type": verify.AL_STREAMING,
                    "buffers_queued": index + 1,
                    "buffers_processed": 0,
                    "sample_offset": 0,
                }
            )
        restarted = queued[-1]["queued_nanos"] + 100_000_000
        ready = restarted + 500_000_000
        cycles.append(
            {
                "stage": stage,
                "armed_nanos": armed,
                "history_frames_at_arm": verify.HISTORY_FRAMES,
                "fallback_requested_nanos": requested,
                "history_frames_at_fallback": verify.HISTORY_FRAMES,
                "synthesizers_at_fallback": 1,
                "shadow_active_at_fallback": False,
                "wet_active_at_fallback": False,
                "queued": queued,
                "shadow_restart_nanos": restarted,
                "history_frames_at_shadow_restart": 2_400,
                "synthesizers_at_shadow_restart": 1,
                "history_ready_nanos": ready,
            }
        )
        cursor = ready
    report = {
        "schema_version": 1,
        "status": "valid-listener-reverb-queue-handoff",
        "sample_rate_hz": verify.SAMPLE_RATE,
        "history_frames": verify.HISTORY_FRAMES,
        "minecraft_stream_buffer_seconds": 1,
        "minecraft_initial_queue_buffers": verify.INITIAL_QUEUE_BUFFERS,
        "source_d107_report_sha256": _sha(d107_bytes),
        "source_fault_failover_report_sha256": _sha(failover_bytes),
        "thresholds": {
            "maximum_first_queue_latency_ms": (
                verify.MAXIMUM_FIRST_QUEUE_LATENCY_MS
            ),
            "maximum_initial_queue_fill_ms": (
                verify.MAXIMUM_INITIAL_QUEUE_FILL_MS
            ),
            "maximum_preroll_ms": verify.MAXIMUM_PREROLL_MS,
        },
        "cycles": cycles,
        "openal_shadow_source_created": False,
        "active_source_restarted": True,
        "exclusive_wet_owner": True,
        "client_gametest_measured": True,
        "physical_endpoint_opened": True,
        "physical_output_captured": False,
        "captures_audio": False,
        "release_calibrated": False,
    }
    return {
        "report": report,
        "d107": d107,
        "d107_bytes": d107_bytes,
        "failover": failover,
        "failover_bytes": failover_bytes,
    }


def run(values):
    return verify.verify(
        values["report"],
        "3" * 64,
        values["d107"],
        values["d107_bytes"],
        values["failover"],
        values["failover_bytes"],
    )


class ListenerReverbQueueHandoffVerifyTest(unittest.TestCase):
    def test_accepts_three_live_fault_handoffs(self):
        result = run(fixture())
        self.assertEqual(
            result["status"],
            "verified-listener-reverb-queue-handoff",
        )

    def test_rejects_d107_detachment(self):
        values = fixture()
        values["report"]["source_d107_report_sha256"] = "f" * 64
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_fault_report_detachment(self):
        values = fixture()
        values["report"]["source_fault_failover_report_sha256"] = (
            "f" * 64
        )
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_missing_initial_buffer(self):
        values = fixture()
        values["report"]["cycles"][0]["queued"].pop()
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_reused_stream(self):
        values = fixture()
        for event in values["report"]["cycles"][1]["queued"]:
            event["stream_sequence"] = 1
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_silent_first_buffer_start(self):
        values = fixture()
        values["report"]["cycles"][0]["queued"][0][
            "first_nonzero_frame"
        ] = 1
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_short_preroll(self):
        values = fixture()
        values["report"]["cycles"][0]["queued"][0][
            "preroll_frames"
        ] -= 1
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_first_queue_budget_overrun(self):
        values = fixture()
        event = values["report"]["cycles"][0]["queued"][0]
        event["fallback_to_queue_nanos"] = int(
            (verify.MAXIMUM_FIRST_QUEUE_LATENCY_MS + 1.0) * 1.0e6
        )
        event["queued_nanos"] = (
            values["report"]["cycles"][0]["fallback_requested_nanos"]
            + event["fallback_to_queue_nanos"]
        )
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_wrong_native_format(self):
        values = fixture()
        values["report"]["cycles"][0]["queued"][0][
            "buffer_frequency"
        ] = 44_100
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_processed_initial_buffer(self):
        values = fixture()
        values["report"]["cycles"][0]["queued"][0][
            "buffers_processed"
        ] = 1
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_missing_shadow_reset(self):
        values = fixture()
        values["report"]["cycles"][0][
            "history_frames_at_shadow_restart"
        ] = verify.HISTORY_FRAMES
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_shadow_openal_source(self):
        values = fixture()
        values["report"]["openal_shadow_source_created"] = True
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_endpoint_capture_overclaim(self):
        values = fixture()
        values["report"]["physical_output_captured"] = True
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_endpoint_open_underclaim(self):
        values = fixture()
        values["report"]["physical_endpoint_opened"] = False
        with self.assertRaises(ValueError):
            run(values)


if __name__ == "__main__":
    unittest.main()
