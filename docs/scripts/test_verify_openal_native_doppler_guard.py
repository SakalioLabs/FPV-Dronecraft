import copy
import unittest

import verify_openal_native_doppler_guard as verify


def state(context_rebuilds):
    return {
        "enabled": True,
        "operational": True,
        "guarded_sources": 2,
        "context_rebuilds": context_rebuilds,
        "restored_sources": 0,
        "doppler_factor": 1.0,
        "speed_of_sound_meters_per_second": 343.3,
        "distance_model": 53250,
        "listener_velocity_magnitude": 0.0,
        "maximum_guard_velocity_difference": 0.0,
        "maximum_native_ratio_deviation_before": 0.0,
        "maximum_native_ratio_deviation_after": 0.0,
        "minimum_internal_doppler_ratio": 0.98,
        "maximum_internal_doppler_ratio": 1.02,
        "internally_shifted_sources": 2,
        "minimum_source_pitch": 1.0,
        "maximum_source_pitch": 1.0,
        "al_error_code": 0,
    }


def fixture():
    return {
        "schema_version": 1,
        "status": "valid-openal-native-doppler-guard",
        "before_reload": state(1),
        "after_reload": state(2),
        "minecraft_channel_velocity_api_available": False,
        "minecraft_listener_velocity_api_available": False,
        "procedural_pcm_tonal_doppler": True,
        "procedural_pcm_broadband_doppler": False,
        "native_complete_stream_resampling_rejected": True,
        "native_doppler_neutralized": True,
        "other_sources_modified": False,
        "sound_engine_reload_exercised": True,
        "physical_device_switch_exercised": False,
        "real_audio_capture": False,
        "release_calibrated": False,
    }


class OpenAlNativeDopplerGuardVerifyTest(unittest.TestCase):
    def test_accepts_guard_and_context_rebuild(self):
        result = verify.verify(fixture(), "d" * 64)
        self.assertEqual(result["guarded_sources_after"], 2)
        self.assertTrue(
            result["gates"]["native_relative_doppler_neutralized"]
        )
        self.assertFalse(result["gates"]["audible_doppler_validated"])

    def test_rejects_non_neutral_native_ratio(self):
        changed = copy.deepcopy(fixture())
        changed["after_reload"][
            "maximum_native_ratio_deviation_after"
        ] = 0.01
        with self.assertRaisesRegex(ValueError, "not neutralized"):
            verify.verify(changed, "d" * 64)

    def test_rejects_non_unity_source_pitch(self):
        changed = copy.deepcopy(fixture())
        changed["before_reload"]["maximum_source_pitch"] = 1.01
        with self.assertRaisesRegex(ValueError, "pitch"):
            verify.verify(changed, "d" * 64)

    def test_rejects_broadband_doppler_claim(self):
        changed = copy.deepcopy(fixture())
        changed["procedural_pcm_broadband_doppler"] = True
        with self.assertRaisesRegex(ValueError, "must be false"):
            verify.verify(changed, "d" * 64)

    def test_rejects_complete_stream_native_doppler(self):
        changed = copy.deepcopy(fixture())
        changed["native_complete_stream_resampling_rejected"] = False
        with self.assertRaisesRegex(ValueError, "must be true"):
            verify.verify(changed, "d" * 64)

    def test_rejects_other_source_mutation(self):
        changed = copy.deepcopy(fixture())
        changed["other_sources_modified"] = True
        with self.assertRaisesRegex(ValueError, "must be false"):
            verify.verify(changed, "d" * 64)

    def test_rejects_no_context_rebuild(self):
        changed = copy.deepcopy(fixture())
        changed["after_reload"]["context_rebuilds"] = 1
        with self.assertRaisesRegex(ValueError, "not rebuilt"):
            verify.verify(changed, "d" * 64)

    def test_rejects_release_overclaim(self):
        changed = copy.deepcopy(fixture())
        changed["release_calibrated"] = True
        with self.assertRaisesRegex(ValueError, "must be false"):
            verify.verify(changed, "d" * 64)


if __name__ == "__main__":
    unittest.main()
