import copy
import unittest

import verify_listener_reverb_performance as verify


def fixtures():
    minecraft = {
        "schema_version": 1,
        "status": "valid-benchmark",
        "snapshot_cells": 1521,
        "ray_count": 128,
        "maximum_bounces": 8,
        "measured_iterations": 200,
        "incomplete_snapshots": 0,
        "capture_p99_ms": 1.0,
        "probe_p99_ms": 1.5,
    }
    common = {
        "synthesizers_after_source_removal": 0,
        "stream_closed": True,
        "closed_read_empty": True,
        "close_released_buffers": True,
        "thread_allocated_bytes_per_read": 800,
    }
    audio = {
        "schema_version": 1,
        "status": "valid-benchmark",
        "source_count": 6,
        "tones_per_rotor": 15,
        "measured_iterations": 500,
        "cases": [
            {
                **common,
                "requested_bytes": 4096,
                "p99_buffer_fraction": 0.08,
            },
            {
                **common,
                "requested_bytes": 16384,
                "p99_buffer_fraction": 0.09,
            },
        ],
    }
    return minecraft, audio


class ListenerReverbPerformanceVerifyTest(unittest.TestCase):
    def test_accepts_bounded_performance_and_cleanup(self):
        result = verify.verify(*fixtures())
        self.assertTrue(result["gates"]["capture_p99_at_most_4_ms"])
        self.assertTrue(
            result["gates"]["audio_p99_below_25_percent_of_buffer"]
        )
        self.assertFalse(result["gates"]["openal_end_to_end_measured"])

    def test_rejects_reduced_audio_load(self):
        minecraft, audio = fixtures()
        changed = copy.deepcopy(audio)
        changed["source_count"] = 5
        with self.assertRaisesRegex(ValueError, "source count changed"):
            verify.verify(minecraft, changed)


if __name__ == "__main__":
    unittest.main()
