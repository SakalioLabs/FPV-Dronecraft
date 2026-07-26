import copy
import unittest

import verify_openal_efx_controller as verify


def fixture():
    return {
        "schema_version": 1,
        "status": "valid-controller-diagnostic",
        "feature_property": "fpvdrone.openalEfx",
        "feature_default": False,
        "test_feature_enabled": True,
        "test_java_reverb_enabled": True,
        "java_wet_bus_suppressed": True,
        "active": {
            "operational": True,
            "shared_resources_created": True,
            "attached_sources": 2,
            "allocated_source_filters": 2,
            "context_rebuilds": 1,
            "cleanup_count": 0,
            "al_error_code": 0,
        },
        "after_sound_engine_reload": {
            "operational": True,
            "shared_resources_created": True,
            "attached_sources": 2,
            "allocated_source_filters": 2,
            "context_rebuilds": 2,
            "cleanup_count": 0,
            "al_error_code": 0,
        },
        "after_source_removal": {
            "operational": False,
            "shared_resources_created": False,
            "attached_sources": 0,
            "allocated_source_filters": 0,
            "context_rebuilds": 2,
            "cleanup_count": 1,
            "al_error_code": 0,
        },
        "sound_engine_reload_exercised": True,
        "physical_device_switch_exercised": False,
        "release_calibrated": False,
    }


class OpenAlEfxControllerVerifyTest(unittest.TestCase):
    def test_accepts_shared_slot_and_source_cleanup(self):
        result = verify.verify(fixture(), "d" * 64)
        self.assertEqual(result["active_attached_sources"], 2)
        self.assertTrue(
            result["gates"]["source_removal_releases_every_resource"]
        )
        self.assertTrue(result["gates"]["sound_engine_reload_recovers"])
        self.assertFalse(
            result["gates"]["physical_device_switch_exercised"]
        )

    def test_rejects_default_on_feature(self):
        changed = copy.deepcopy(fixture())
        changed["feature_default"] = True
        with self.assertRaisesRegex(ValueError, "default-off"):
            verify.verify(changed, "d" * 64)

    def test_rejects_filter_leak(self):
        changed = copy.deepcopy(fixture())
        changed["after_source_removal"]["allocated_source_filters"] = 1
        with self.assertRaisesRegex(ValueError, "cleanup"):
            verify.verify(changed, "d" * 64)

    def test_rejects_missing_context_rebuild(self):
        changed = copy.deepcopy(fixture())
        changed["after_sound_engine_reload"]["context_rebuilds"] = 1
        with self.assertRaisesRegex(ValueError, "reload"):
            verify.verify(changed, "d" * 64)

    def test_rejects_double_reverb_backend(self):
        changed = copy.deepcopy(fixture())
        changed["java_wet_bus_suppressed"] = False
        with self.assertRaisesRegex(ValueError, "suppress"):
            verify.verify(changed, "d" * 64)


if __name__ == "__main__":
    unittest.main()
