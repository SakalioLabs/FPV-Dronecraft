import copy
import unittest

import verify_audio_lab_alignment_fixture as verify


def fixture():
    return {
        "schema_version": 1,
        "status": "valid-audio-lab-alignment-diagnostic",
        "fixture": {
            "kind": "deterministic-audio-lab-alignment-fixture",
            "expected_marker_audio_s": [0.9504, 2.9544, 4.3572],
            "expected_boundary_audio_s": 3.4554,
            "expected_clock_slope": 1.002,
            "real_audio_capture": False,
            "physical_output_loopback_confirmed": False,
        },
        "wav": {
            "sample_rate_hz": 48_000,
            "channels": 1,
            "sample_width_bits": 24,
            "frame_count": 384_000,
            "duration_s": 8.0,
            "wav_sha256": "a" * 64,
        },
        "alignment": {
            "marker_detections": [
                {"start_s": 0.95},
                {"start_s": 2.954},
                {"start_s": 4.358},
            ],
            "minimum_marker_score": 0.98,
            "maximum_absolute_alignment_residual_ms": 0.47,
            "boundary_audio_s": 3.45563,
            "recorder_seconds_per_minecraft_second": 1.00233,
        },
        "real_audio_capture": False,
        "physical_output_loopback_confirmed": False,
        "release_calibrated": False,
    }


class AudioLabAlignmentFixtureVerifyTest(unittest.TestCase):
    def test_accepts_alignment_fixture(self):
        result = verify.verify(fixture(), "b" * 64)
        self.assertTrue(result["gates"]["three_markers_detected"])
        self.assertLess(result["boundary_error_ms"], 2.0)

    def test_rejects_weak_marker(self):
        changed = copy.deepcopy(fixture())
        changed["alignment"]["minimum_marker_score"] = 0.5
        with self.assertRaisesRegex(ValueError, "score"):
            verify.verify(changed, "b" * 64)

    def test_rejects_boundary_error(self):
        changed = copy.deepcopy(fixture())
        changed["alignment"]["boundary_audio_s"] = 3.47
        with self.assertRaisesRegex(ValueError, "boundary"):
            verify.verify(changed, "b" * 64)

    def test_rejects_loopback_overclaim(self):
        changed = copy.deepcopy(fixture())
        changed["physical_output_loopback_confirmed"] = True
        with self.assertRaisesRegex(ValueError, "overclaims"):
            verify.verify(changed, "b" * 64)


if __name__ == "__main__":
    unittest.main()
