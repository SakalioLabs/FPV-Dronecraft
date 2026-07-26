import copy
import unittest

import verify_openal_clock_latency as verify


def sample(host_ns, offset, *, device_clock=False):
    return {
        "source_found": True,
        "instance_type": "DroneLoopSoundInstance",
        "thread_name": "Sound engine",
        "host_monotonic_ns": host_ns,
        "active_context": True,
        "device_name": "OpenAL Soft",
        "device_clock_supported": device_clock,
        "source_latency_supported": True,
        "native_telemetry_available": True,
        "device_clock_ns": host_ns if device_clock else 0,
        "device_latency_ns": 51_000_000 if device_clock else 0,
        "source_offset_seconds": offset,
        "source_latency_seconds": 0.051,
        "source_clock_offset_seconds": offset if device_clock else 0.0,
        "source_device_clock_seconds": host_ns / 1e9 if device_clock else 0.0,
        "al_error_code": 0,
        "alc_error_code": 0,
    }


def pair(start_ns, *, device_clock=False):
    elapsed = 240_000_000
    return {
        "first": sample(start_ns, 0.08, device_clock=device_clock),
        "second": sample(
            start_ns + elapsed, 0.32, device_clock=device_clock
        ),
        "host_elapsed_ns": elapsed,
        "device_clock_elapsed_ns": elapsed if device_clock else 0,
        "device_to_host_clock_rate_ratio": 1.0 if device_clock else 0.0,
        "source_offset_advance_seconds": 0.24,
        "source_offset_wrapped": False,
        "native_telemetry_validated": True,
    }


def fixture(*, device_clock=False):
    return {
        "schema_version": 1,
        "status": "valid-openal-clock-latency-diagnostic",
        "extension": "ALC_SOFT_device_clock",
        "source_extension": "AL_SOFT_source_latency",
        "before_reload": pair(1_000_000_000, device_clock=device_clock),
        "after_reload": pair(2_000_000_000, device_clock=device_clock),
        "extension_support_stable": True,
        "device_name_stable": True,
        "sound_engine_reload_exercised": True,
        "physical_device_switch_exercised": False,
        "callback_underrun_counter_available": False,
        "audio_path_changed": False,
        "release_calibrated": False,
    }


class OpenAlClockLatencyVerifyTest(unittest.TestCase):
    def test_accepts_source_latency_without_device_clock(self):
        result = verify.verify(fixture(), "a" * 64)
        self.assertFalse(result["device_clock_supported"])
        self.assertTrue(result["source_latency_supported"])
        self.assertEqual(
            result["pairs"]["before_reload"][
                "source_offset_advance_seconds"
            ],
            0.24,
        )
        self.assertFalse(
            result["gates"]["end_to_end_latency_measured"]
        )

    def test_accepts_full_device_clock_support(self):
        result = verify.verify(fixture(device_clock=True), "b" * 64)
        self.assertTrue(
            result["gates"]["device_clock_telemetry_validated"]
        )
        self.assertEqual(
            result["pairs"]["after_reload"][
                "device_to_host_clock_rate_ratio"
            ],
            1.0,
        )

    def test_rejects_device_clock_overclaim_when_unsupported(self):
        changed = copy.deepcopy(fixture())
        changed["before_reload"]["first"]["device_clock_ns"] = 7
        with self.assertRaisesRegex(ValueError, "overclaims unsupported"):
            verify.verify(changed, "a" * 64)

    def test_rejects_callback_underrun_overclaim(self):
        changed = copy.deepcopy(fixture())
        changed["callback_underrun_counter_available"] = True
        with self.assertRaisesRegex(ValueError, "must be false"):
            verify.verify(changed, "a" * 64)

    def test_rejects_nonprogressing_source_offset(self):
        changed = copy.deepcopy(fixture())
        changed["after_reload"]["second"]["source_offset_seconds"] = 0.08
        with self.assertRaisesRegex(ValueError, "advance is inconsistent"):
            verify.verify(changed, "a" * 64)

    def test_accepts_one_second_stream_buffer_rollover(self):
        changed = copy.deepcopy(fixture())
        changed["after_reload"]["first"]["source_offset_seconds"] = 0.90
        changed["after_reload"]["second"]["source_offset_seconds"] = 0.14
        changed["after_reload"]["source_offset_wrapped"] = True
        result = verify.verify(changed, "c" * 64)
        self.assertAlmostEqual(
            result["pairs"]["after_reload"][
                "source_offset_advance_seconds"
            ],
            0.24,
        )
        self.assertTrue(
            result["pairs"]["after_reload"]["source_offset_wrapped"]
        )

    def test_rejects_detached_wrap_flag(self):
        changed = copy.deepcopy(fixture())
        changed["before_reload"]["source_offset_wrapped"] = True
        with self.assertRaisesRegex(ValueError, "advance is inconsistent"):
            verify.verify(changed, "a" * 64)

    def test_rejects_openal_error(self):
        changed = copy.deepcopy(fixture())
        changed["before_reload"]["second"]["al_error_code"] = 1
        with self.assertRaisesRegex(ValueError, "OpenAL error"):
            verify.verify(changed, "a" * 64)


if __name__ == "__main__":
    unittest.main()
