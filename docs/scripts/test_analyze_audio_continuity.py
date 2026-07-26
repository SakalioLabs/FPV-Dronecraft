import importlib.util
import json
import math
import tempfile
import unittest
from pathlib import Path

import numpy as np


MODULE_PATH = (
    Path(__file__).resolve().parents[2]
    / "tools"
    / "acoustics"
    / "analyze_audio_continuity.py"
)
SPEC = importlib.util.spec_from_file_location(
    "analyze_audio_continuity",
    MODULE_PATH,
)
continuity = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(continuity)


def base_signal(sample_rate=48_000, duration_s=6.0):
    times = np.arange(round(sample_rate * duration_s)) / sample_rate
    return (
        0.16 * np.sin(2.0 * math.pi * 233.0 * times)
        + 0.04 * np.sin(2.0 * math.pi * 701.0 * times)
    )


def analyze(samples):
    return continuity.analyze(
        48_000,
        samples,
        reload_time_s=3.0,
        pre_window_s=1.5,
        post_window_s=1.5,
        guard_s=0.1,
        frame_ms=5.0,
        dropout_threshold_db=24.0,
        minimum_dropout_ms=20.0,
        click_sigma=12.0,
        click_minimum_step=0.05,
    )


class AnalyzeAudioContinuityTest(unittest.TestCase):
    def test_clean_continuous_signal_has_no_events(self):
        result = analyze(base_signal())
        self.assertEqual(result["dropout_event_count"], 0)
        self.assertEqual(result["reload_guard_click_event_count"], 0)
        self.assertLess(abs(result["post_pre_level_delta_db"]), 0.001)

    def test_detects_reload_click(self):
        samples = base_signal()
        samples[3 * 48_000] = 0.92
        result = analyze(samples)
        self.assertGreaterEqual(result["reload_guard_click_event_count"], 1)
        self.assertGreater(result["reload_guard_peak_over_threshold"], 1.0)

    def test_detects_bounded_dropout(self):
        samples = base_signal()
        start = round(3.65 * 48_000)
        samples[start : start + round(0.06 * 48_000)] = 0.0
        result = analyze(samples)
        self.assertEqual(result["dropout_event_count"], 1)
        self.assertGreaterEqual(result["longest_dropout_ms"], 55.0)

    def test_rejects_quiet_baseline(self):
        with self.assertRaisesRegex(ValueError, "too quiet"):
            analyze(np.zeros(6 * 48_000))

    def test_rejects_out_of_bounds_window(self):
        samples = base_signal()
        with self.assertRaisesRegex(ValueError, "starts before"):
            continuity.analyze(
                48_000,
                samples,
                reload_time_s=1.0,
                pre_window_s=1.5,
                post_window_s=1.5,
                guard_s=0.1,
                frame_ms=5.0,
                dropout_threshold_db=24.0,
                minimum_dropout_ms=20.0,
                click_sigma=12.0,
                click_minimum_step=0.05,
            )

    def test_fixture_round_trip_is_pcm24(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.wav"
            fixture = continuity.generate_fixture(path)
            sample_rate, samples, metadata = continuity.read_pcm_wav(path, 0)
            self.assertEqual(sample_rate, 48_000)
            self.assertEqual(metadata["sample_width_bits"], 24)
            self.assertTrue(fixture["injected_reload_click"])
            result = analyze(samples)
            self.assertGreaterEqual(result["reload_guard_click_event_count"], 1)
            self.assertEqual(result["dropout_event_count"], 1)

    def test_loads_boundary_from_alignment_report(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "alignment.json"
            path.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "status": (
                            "valid-audio-lab-alignment-diagnostic"
                        ),
                        "alignment": {"boundary_audio_s": 3.45563},
                    }
                ),
                encoding="utf-8",
            )
            boundary, digest = continuity.load_alignment_boundary(path)
            self.assertAlmostEqual(boundary, 3.45563)
            self.assertEqual(len(digest), 64)

    def test_rejects_invalid_alignment_status(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "alignment.json"
            path.write_text(
                '{"schema_version":1,"status":"invalid"}',
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "schema/status"):
                continuity.load_alignment_boundary(path)


if __name__ == "__main__":
    unittest.main()
