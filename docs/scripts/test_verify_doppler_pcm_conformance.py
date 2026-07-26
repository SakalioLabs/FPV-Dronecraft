import copy
import math
import unittest

import verify_doppler_pcm_conformance as verify


def measurement(layer, kind, ratio):
    base = 1_000.0 if layer == "motor" else 500.0
    expected = base * ratio
    measured = expected + 0.002
    error = measured - expected
    return {
        "layer": layer,
        "tone_kind": kind,
        "rotor_index": 0,
        "order": 1,
        "base_frequency_hz": base,
        "doppler_frequency_ratio": ratio,
        "expected_frequency_hz": expected,
        "measured_frequency_hz": measured,
        "frequency_error_hz": error,
        "frequency_error_ppm": error / expected * 1_000_000.0,
        "sound_speed_mps": 343.42,
        "listener_radial_velocity_mps": 0.0,
        "source_radial_velocity_mps": -20.0,
        "sample_rate_hz": 48_000,
        "analyzed_samples": 38_400,
        "positive_crossings": 800,
        "clipped_samples": 0,
        "broadband_excluded": True,
        "pcm16_quantized": True,
    }


def fixture():
    ratio = 343.42 / (343.42 - 20.0)
    return {
        "schema_version": 1,
        "status": "valid-doppler-pcm-conformance",
        "capture": {
            "entity_id": 4,
            "simulation_time_nanos": 2_000_000_000,
            "source_position_m": [10.0, 0.0, 0.0],
            "source_velocity_mps": [-20.0, 0.0, 0.0],
            "listener_position_m": [0.0, 0.0, 0.0],
            "listener_velocity_mps": [0.0, 0.0, 0.0],
            "sound_speed_mps": 343.42,
            "render_state_doppler_ratio": ratio,
            "motor": measurement("motor", "shaft", ratio),
            "propeller": measurement("propeller", "blade_pass", ratio),
        },
        "synchronized_minecraft_entity_kinematics": True,
        "server_blackbox_csv_bound": False,
        "production_doppler_shift": True,
        "production_phase_continuous_synthesizer": True,
        "isolated_tone_measurement": True,
        "mixed_live_stream_measured": False,
        "openal_playback_capture": False,
        "real_audio_capture": False,
        "release_calibrated": False,
    }


class DopplerPcmConformanceVerifyTest(unittest.TestCase):
    def test_accepts_physical_pcm_measurement(self):
        result = verify.verify(fixture(), "c" * 64)
        self.assertTrue(result["gates"]["physical_ratio_recomputed"])
        self.assertLess(result["maximum_absolute_frequency_error_ppm"], 25.0)

    def test_rejects_detached_radial_velocity(self):
        changed = copy.deepcopy(fixture())
        changed["capture"]["motor"][
            "source_radial_velocity_mps"
        ] = -19.0
        with self.assertRaisesRegex(ValueError, "source radial"):
            verify.verify(changed, "c" * 64)

    def test_rejects_detached_render_ratio(self):
        changed = copy.deepcopy(fixture())
        changed["capture"]["render_state_doppler_ratio"] = 1.01
        with self.assertRaisesRegex(ValueError, "render ratio"):
            verify.verify(changed, "c" * 64)

    def test_rejects_large_pcm_frequency_error(self):
        changed = copy.deepcopy(fixture())
        motor = changed["capture"]["motor"]
        motor["measured_frequency_hz"] = motor["expected_frequency_hz"] + 1.0
        motor["frequency_error_hz"] = 1.0
        motor["frequency_error_ppm"] = (
            1.0 / motor["expected_frequency_hz"] * 1_000_000.0
        )
        with self.assertRaisesRegex(ValueError, "exceeds gate"):
            verify.verify(changed, "c" * 64)

    def test_rejects_broadband_measurement_claim(self):
        changed = copy.deepcopy(fixture())
        changed["capture"]["propeller"]["broadband_excluded"] = False
        with self.assertRaisesRegex(ValueError, "contract"):
            verify.verify(changed, "c" * 64)

    def test_rejects_blackbox_overclaim(self):
        changed = copy.deepcopy(fixture())
        changed["server_blackbox_csv_bound"] = True
        with self.assertRaisesRegex(ValueError, "must be false"):
            verify.verify(changed, "c" * 64)

    def test_rejects_openal_capture_overclaim(self):
        changed = copy.deepcopy(fixture())
        changed["openal_playback_capture"] = True
        with self.assertRaisesRegex(ValueError, "must be false"):
            verify.verify(changed, "c" * 64)

    def test_rejects_nonfinite_vector(self):
        changed = copy.deepcopy(fixture())
        changed["capture"]["source_velocity_mps"][0] = math.nan
        with self.assertRaisesRegex(ValueError, "finite 3-vector"):
            verify.verify(changed, "c" * 64)


if __name__ == "__main__":
    unittest.main()
