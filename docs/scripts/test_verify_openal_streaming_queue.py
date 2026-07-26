from __future__ import annotations

import copy
import hashlib
import unittest

import verify_openal_streaming_queue as verify


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fixture() -> tuple[dict, dict]:
    chunks = []
    events = []
    sequence = 0
    for layer, source_id, buffer_base in (
        ("motor", 11, 100),
        ("propeller", 12, 200),
    ):
        for layer_sequence in range(4):
            payload = (
                f"{layer}-{layer_sequence}".encode("ascii") * 12_000
            )[:96_000]
            pcm_hash = _sha(payload)
            simulation_nanos = (20 + layer_sequence * 20) * 50_000_000
            ratio = 1.0002 - layer_sequence * 0.0001
            chunk = {
                "layer": layer,
                "layer_sequence": layer_sequence,
                "entity_id": 3,
                "simulation_time_nanos": simulation_nanos,
                "doppler_frequency_ratio": ratio,
                "pcm_bytes": 96_000,
                "pcm_sha256": pcm_hash,
            }
            chunks.append(chunk)
            sample_offset = 960 + layer_sequence * 120
            events.append(
                {
                    "sequence": sequence,
                    "layer_sequence": layer_sequence,
                    "layer": layer,
                    "entity_id": 3,
                    "simulation_time_nanos": simulation_nanos,
                    "doppler_frequency_ratio": ratio,
                    "pcm_bytes": 96_000,
                    "pcm_sha256": pcm_hash,
                    "source_id": source_id,
                    "buffer_id": buffer_base + layer_sequence,
                    "thread_name": "Sound engine",
                    "host_monotonic_ns": (
                        1_000_000_000
                        + layer_sequence * 1_000_000_000
                        + (0 if layer == "motor" else 20_000_000)
                    ),
                    "buffer_valid": True,
                    "buffer_frequency_hz": 48_000,
                    "buffer_bits": 16,
                    "buffer_channels": 1,
                    "buffer_bytes": 96_000,
                    "source_state": verify.AL_PLAYING,
                    "source_type": verify.AL_STREAMING,
                    "buffers_queued": 4,
                    "buffers_processed": 0,
                    "sample_offset": sample_offset,
                    "source_latency_supported": True,
                    "source_offset_seconds": sample_offset / 48_000,
                    "source_latency_seconds": 0.051,
                    "events_supported": True,
                    "event_callback_registered": False,
                    "al_error": 0,
                }
            )
            sequence += 1
    pcm_report = {
        "schema_version": 1,
        "status": "valid-doppler-production-chunk-trace",
        "chunks": chunks,
    }
    pcm_bytes = (
        __import__("json").dumps(pcm_report, sort_keys=True).encode("utf-8")
    )
    report = {
        "schema_version": 1,
        "status": "valid-openal-streaming-queue-trace",
        "target_events_per_layer": 4,
        "motor_events": 4,
        "propeller_events": 4,
        "pcm_report_sha256": _sha(pcm_bytes),
        "events": events,
        "minecraft_streaming_buffer_seconds": 1,
        "minecraft_initial_queue_target": 4,
        "production_pcm_to_al_buffer_id_bound": True,
        "openal_queue_state_observed": True,
        "openal_source_state_observed": True,
        "openal_events_extension_supported": True,
        "openal_event_callback_registered": False,
        "continuous_underrun_observation": False,
        "callback_underrun_counter_available": False,
        "openal_playback_capture": False,
        "real_audio_capture": False,
        "release_calibrated": False,
    }
    return report, pcm_report


def run_verify(report: dict, pcm_report: dict) -> dict:
    pcm_bytes = (
        __import__("json").dumps(pcm_report, sort_keys=True).encode("utf-8")
    )
    report["pcm_report_sha256"] = _sha(pcm_bytes)
    return verify.verify(report, "a" * 64, pcm_report, _sha(pcm_bytes))


class OpenAlStreamingQueueVerifyTest(unittest.TestCase):
    def test_accepts_hash_bound_post_queue_trace(self):
        report, pcm = fixture()
        result = run_verify(report, pcm)
        self.assertEqual("valid-openal-streaming-queue-trace", result["status"])
        self.assertEqual(8, result["distinct_buffers"])

    def test_rejects_pcm_hash_detachment(self):
        report, pcm = fixture()
        report["events"][0]["pcm_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "pcm_sha256"):
            run_verify(report, pcm)

    def test_rejects_wrong_buffer_format(self):
        report, pcm = fixture()
        report["events"][0]["buffer_frequency_hz"] = 44_100
        with self.assertRaisesRegex(ValueError, "buffer format"):
            run_verify(report, pcm)

    def test_rejects_queue_depth_loss(self):
        report, pcm = fixture()
        report["events"][0]["buffers_queued"] = 3
        with self.assertRaisesRegex(ValueError, "queue depth"):
            run_verify(report, pcm)

    def test_rejects_stopped_source(self):
        report, pcm = fixture()
        report["events"][0]["source_state"] = 0x1014
        with self.assertRaisesRegex(ValueError, "not playing"):
            run_verify(report, pcm)

    def test_rejects_sample_offset_detachment(self):
        report, pcm = fixture()
        report["events"][0]["source_offset_seconds"] += 0.1
        with self.assertRaisesRegex(ValueError, "inconsistent"):
            run_verify(report, pcm)

    def test_rejects_nonmonotonic_host_timestamp(self):
        report, pcm = fixture()
        report["events"][2]["host_monotonic_ns"] = report["events"][0][
            "host_monotonic_ns"
        ]
        with self.assertRaisesRegex(ValueError, "host timestamp"):
            run_verify(report, pcm)

    def test_accepts_consistent_event_callback_registration(self):
        report, pcm = fixture()
        for event in report["events"]:
            event["event_callback_registered"] = True
        report["openal_event_callback_registered"] = True
        result = run_verify(report, pcm)
        self.assertTrue(result["openal_event_callback_registered"])

    def test_rejects_event_callback_summary_detachment(self):
        report, pcm = fixture()
        report["events"][0]["event_callback_registered"] = True
        with self.assertRaisesRegex(ValueError, "changed within trace"):
            run_verify(report, pcm)

    def test_rejects_continuous_underrun_overclaim(self):
        report, pcm = fixture()
        report["continuous_underrun_observation"] = True
        with self.assertRaisesRegex(ValueError, "must be false"):
            run_verify(report, pcm)

    def test_rejects_openal_error(self):
        report, pcm = fixture()
        report["events"][0]["al_error"] = 0xA003
        with self.assertRaisesRegex(ValueError, "reported an error"):
            run_verify(report, pcm)


if __name__ == "__main__":
    unittest.main()
