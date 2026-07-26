import copy
import unittest

import verify_audio_lab_protocol as verify


def state(java, efx, rebuilds):
    return {
        "active_drone_sound_sets": 1,
        "java_reverb_active": java,
        "efx_operational": efx,
        "efx_resources": efx,
        "efx_attached_sources": 2 if efx else 0,
        "efx_source_filters": 2 if efx else 0,
        "efx_context_rebuilds": rebuilds,
        "efx_al_error_code": 0,
    }


def timing_sample(host_ns, offset):
    return {
        "source_found": True,
        "instance_type": "DroneLoopSoundInstance",
        "thread_name": "Sound engine",
        "host_monotonic_ns": host_ns,
        "active_context": True,
        "device_name": "OpenAL Soft",
        "device_clock_supported": False,
        "source_latency_supported": True,
        "native_telemetry_available": True,
        "device_clock_ns": 0,
        "device_latency_ns": 0,
        "source_offset_seconds": offset,
        "source_latency_seconds": 0.051,
        "source_clock_offset_seconds": 0.0,
        "source_device_clock_seconds": 0.0,
        "al_error_code": 0,
        "alc_error_code": 0,
    }


def timing_pair(start_ns):
    elapsed = 200_000_000
    return {
        "first": timing_sample(start_ns, 0.08),
        "second": timing_sample(start_ns + elapsed, 0.28),
        "host_elapsed_ns": elapsed,
        "device_clock_elapsed_ns": 0,
        "device_to_host_clock_rate_ratio": 0.0,
        "source_offset_advance_seconds": 0.20,
        "source_offset_wrapped": False,
        "native_telemetry_validated": True,
    }


def native_timing():
    return {
        "probe": "OpenAlClockLatencyProbe",
        "read_only": True,
        "before_boundary": timing_pair(1_000_000_000),
        "after_boundary": timing_pair(2_000_000_000),
        "support_stable_across_boundary": True,
        "device_name_stable_across_boundary": True,
        "audio_path_changed": False,
        "end_to_end_latency_measured": False,
        "callback_underrun_counter_available": False,
    }


def report(backend, variant, before, after):
    reload_case = variant == "reload"
    if reload_case:
        events = [
            ("session_start_marker", 0, "STARTED@880.0Hz"),
            ("pre_boundary_marker", 40, "STARTED@1320.0Hz"),
            ("sound_engine_reload_requested", 50, None),
            ("sound_engine_reload_returned", 50, None),
            ("post_boundary_marker", 60, "STARTED@1760.0Hz"),
            ("session_complete", 160, None),
        ]
    else:
        events = [
            ("session_start_marker", 0, "STARTED@880.0Hz"),
            ("pre_boundary_marker", 40, "STARTED@1320.0Hz"),
            ("control_boundary_no_reload", 50, None),
            ("post_boundary_marker", 60, "STARTED@1760.0Hz"),
            ("session_complete", 160, None),
        ]
    return {
        "schema_version": 2,
        "status": "valid-audio-lab-timeline",
        "backend": backend,
        "variant": variant,
        "procedural_audio_required": True,
        "backend_override_default_before": True,
        "backend_override_restored_after": True,
        "marker_contract": {
            "duration_s": 0.08,
            "start_hz": 880.0,
            "pre_boundary_hz": 1320.0,
            "post_boundary_hz": 1760.0,
        },
        "sound_engine_reload_exercised": reload_case,
        "reload_call_duration_ns": 20_000_000 if reload_case else 0,
        "before_boundary": before,
        "after_boundary": after,
        "native_timing": native_timing(),
        "events": [
            {
                "name": name,
                "tick": tick,
                "relative_ns": (index + 1) * 100_000_000,
                "detail": detail,
            }
            for index, (name, tick, detail) in enumerate(events)
        ],
        "real_audio_capture": False,
        "physical_output_loopback_confirmed": False,
        "release_calibrated": False,
    }


def matrix():
    return (
        report("dry", "control", state(False, False, 2), state(False, False, 2)),
        report(
            "java-fdn",
            "control",
            state(True, False, 2),
            state(True, False, 2),
        ),
        report(
            "openal-efx",
            "reload",
            state(False, True, 2),
            state(False, True, 3),
        ),
    )


class AudioLabProtocolVerifyTest(unittest.TestCase):
    def test_accepts_representative_backend_matrix(self):
        result = verify.verify(*matrix(), {"dry": "a" * 64})
        self.assertTrue(result["gates"]["dry_backend_exclusive"])
        self.assertTrue(
            result["gates"]["native_timing_hash_bound_in_timelines"]
        )
        self.assertEqual(result["efx_context_rebuilds_after"], 3)

    def test_accepts_loaded_windows_reload_duration(self):
        dry, java, efx = matrix()
        efx["reload_call_duration_ns"] = 1_500_000_000
        result = verify.verify(dry, java, efx, {})
        self.assertEqual(result["efx_context_rebuilds_after"], 3)

    def test_rejects_excessive_reload_duration(self):
        dry, java, efx = matrix()
        efx["reload_call_duration_ns"] = (
            verify.MAXIMUM_RELOAD_CALL_DURATION_NS
        )
        with self.assertRaisesRegex(ValueError, "duration is out of bounds"):
            verify.verify(dry, java, efx, {})

    def test_rejects_double_reverb_backend(self):
        dry, java, efx = matrix()
        java["before_boundary"]["efx_operational"] = True
        with self.assertRaisesRegex(ValueError, "Java FDN"):
            verify.verify(dry, java, efx, {})

    def test_rejects_missing_context_rebuild(self):
        dry, java, efx = matrix()
        efx["after_boundary"]["efx_context_rebuilds"] = 2
        with self.assertRaisesRegex(ValueError, "did not rebuild"):
            verify.verify(dry, java, efx, {})

    def test_rejects_marker_failure(self):
        dry, java, efx = matrix()
        dry["events"][0]["detail"] = "NOT_STARTED@880.0Hz"
        with self.assertRaisesRegex(ValueError, "marker"):
            verify.verify(dry, java, efx, {})

    def test_rejects_nonmonotonic_timeline(self):
        dry, java, efx = matrix()
        dry["events"][2]["relative_ns"] = dry["events"][1]["relative_ns"]
        with self.assertRaisesRegex(ValueError, "monotonic"):
            verify.verify(dry, java, efx, {})

    def test_rejects_capture_overclaim(self):
        dry, java, efx = matrix()
        efx["real_audio_capture"] = True
        with self.assertRaisesRegex(ValueError, "overclaims"):
            verify.verify(dry, java, efx, {})

    def test_rejects_missing_native_timing(self):
        dry, java, efx = matrix()
        del dry["native_timing"]
        with self.assertRaisesRegex(ValueError, "timing evidence is missing"):
            verify.verify(dry, java, efx, {})

    def test_rejects_native_timing_end_to_end_overclaim(self):
        dry, java, efx = matrix()
        efx["native_timing"]["end_to_end_latency_measured"] = True
        with self.assertRaisesRegex(ValueError, "end_to_end"):
            verify.verify(dry, java, efx, {})

    def test_rejects_tampered_native_timing_elapsed(self):
        dry, java, efx = matrix()
        java["native_timing"]["before_boundary"]["host_elapsed_ns"] += 1
        with self.assertRaisesRegex(ValueError, "elapsed time is invalid"):
            verify.verify(dry, java, efx, {})


if __name__ == "__main__":
    unittest.main()
