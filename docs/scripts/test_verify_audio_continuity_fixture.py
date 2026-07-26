import copy
import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name(
    "verify_audio_continuity_fixture.py"
)
SPEC = importlib.util.spec_from_file_location(
    "verify_audio_continuity_fixture",
    MODULE_PATH,
)
verify = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(verify)


def fixture():
    return {
        "schema_version": 1,
        "status": "valid-audio-continuity-diagnostic",
        "fixture": {
            "kind": "deterministic-analyzer-fixture",
            "injected_reload_click": True,
            "injected_dropout": True,
            "real_minecraft_capture": False,
            "physical_output_loopback_confirmed": False,
        },
        "wav": {
            "sample_rate_hz": 48_000,
            "channels": 1,
            "sample_width_bits": 24,
            "frame_count": 288_000,
            "duration_s": 6.0,
            "wav_sha256": "f" * 64,
        },
        "analysis": {
            "reload_guard_click_event_count": 1,
            "reload_guard_peak_over_threshold": 18.0,
            "dropout_event_count": 1,
            "longest_dropout_ms": 60.0,
            "dropout_events": [{"start_s": 3.65, "end_s": 3.71}],
        },
        "real_minecraft_capture": False,
        "physical_output_loopback_confirmed": False,
        "openal_callback_underrun_counter_available": False,
        "release_calibrated": False,
    }


class AudioContinuityFixtureVerifyTest(unittest.TestCase):
    def test_accepts_detected_fixture(self):
        result = verify.verify(fixture(), "a" * 64)
        self.assertTrue(result["gates"]["injected_reload_click_detected"])
        self.assertTrue(result["gates"]["injected_dropout_detected"])

    def test_rejects_missed_click(self):
        changed = copy.deepcopy(fixture())
        changed["analysis"]["reload_guard_click_event_count"] = 0
        with self.assertRaisesRegex(ValueError, "click"):
            verify.verify(changed, "a" * 64)

    def test_rejects_missed_dropout(self):
        changed = copy.deepcopy(fixture())
        changed["analysis"]["dropout_event_count"] = 0
        with self.assertRaisesRegex(ValueError, "dropout"):
            verify.verify(changed, "a" * 64)

    def test_rejects_loopback_overclaim(self):
        changed = copy.deepcopy(fixture())
        changed["physical_output_loopback_confirmed"] = True
        with self.assertRaisesRegex(ValueError, "overclaims"):
            verify.verify(changed, "a" * 64)


if __name__ == "__main__":
    unittest.main()
