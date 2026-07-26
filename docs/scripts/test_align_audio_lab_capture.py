import importlib.util
import math
import tempfile
import unittest
from pathlib import Path

import numpy as np


MODULE_PATH = (
    Path(__file__).resolve().parents[2]
    / "tools"
    / "acoustics"
    / "align_audio_lab_capture.py"
)
SPEC = importlib.util.spec_from_file_location(
    "align_audio_lab_capture",
    MODULE_PATH,
)
alignment = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(alignment)


class AlignAudioLabCaptureTest(unittest.TestCase):
    def test_detects_marker_amid_tonal_interference(self):
        sample_rate = 48_000
        times = np.arange(3 * sample_rate) / sample_rate
        samples = 0.08 * np.sin(2.0 * math.pi * 233.0 * times)
        marker = alignment.marker_wave(sample_rate, 1320.0, 0.08)
        start = round(1.234 * sample_rate)
        samples[start : start + marker.size] += marker
        detected = alignment.detect_marker(
            samples,
            sample_rate,
            1320.0,
            0.08,
            1.0,
        )
        self.assertLess(abs(detected["start_s"] - 1.234), 0.0011)
        self.assertGreater(detected["score"], 0.95)

    def test_fixture_recovers_boundary_within_one_hop(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            wav = root / "fixture.wav"
            timeline_path = root / "timeline.json"
            expected = alignment.generate_fixture(wav, timeline_path)
            sample_rate, samples, _ = alignment.read_pcm_wav(wav, 0)
            result = alignment.align(
                sample_rate,
                samples,
                alignment.load_timeline(timeline_path),
                hop_ms=2.0,
                minimum_score=0.35,
            )
            self.assertLess(
                abs(
                    result["boundary_audio_s"]
                    - expected["expected_boundary_audio_s"]
                ),
                0.002,
            )
            self.assertLess(
                result["maximum_absolute_alignment_residual_ms"],
                2.0,
            )

    def test_rejects_missing_markers(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            wav = root / "fixture.wav"
            timeline_path = root / "timeline.json"
            alignment.generate_fixture(wav, timeline_path)
            sample_rate, samples, _ = alignment.read_pcm_wav(wav, 0)
            with self.assertRaisesRegex(ValueError, "score gate"):
                alignment.align(
                    sample_rate,
                    np.zeros_like(samples),
                    alignment.load_timeline(timeline_path),
                    hop_ms=2.0,
                    minimum_score=0.35,
                )

    def test_rejects_missing_boundary_event(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            wav = root / "fixture.wav"
            timeline_path = root / "timeline.json"
            alignment.generate_fixture(wav, timeline_path)
            timeline = alignment.load_timeline(timeline_path)
            timeline["events"] = [
                event
                for event in timeline["events"]
                if event["name"] != "sound_engine_reload_requested"
            ]
            sample_rate, samples, _ = alignment.read_pcm_wav(wav, 0)
            with self.assertRaisesRegex(ValueError, "expected one"):
                alignment.align(
                    sample_rate,
                    samples,
                    timeline,
                    hop_ms=2.0,
                    minimum_score=0.35,
                )


if __name__ == "__main__":
    unittest.main()
