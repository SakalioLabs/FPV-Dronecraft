import copy
import unittest

import verify_openal_efx_reload as verify


def fixture():
    return {
        "schema_version": 1,
        "status": "valid-sound-engine-reload",
        "reload_entrypoint": "Minecraft.SoundManager.reload",
        "before": {
            "operational": True,
            "shared_resources_created": True,
            "attached_sources": 2,
            "allocated_source_filters": 2,
            "context_rebuilds": 1,
            "cleanup_count": 0,
            "al_error_code": 0,
        },
        "after": {
            "operational": True,
            "shared_resources_created": True,
            "attached_sources": 2,
            "allocated_source_filters": 2,
            "context_rebuilds": 2,
            "cleanup_count": 0,
            "al_error_code": 0,
        },
        "java_wet_bus_suppressed": True,
        "sound_engine_reload_exercised": True,
        "physical_device_switch_exercised": False,
        "release_calibrated": False,
    }


class OpenAlEfxReloadVerifyTest(unittest.TestCase):
    def test_accepts_context_rebuild(self):
        result = verify.verify(fixture(), "e" * 64)
        self.assertEqual(result["context_rebuilds_before"], 1)
        self.assertEqual(result["context_rebuilds_after"], 2)
        self.assertTrue(result["gates"]["drone_sources_reattached"])

    def test_rejects_no_context_change(self):
        changed = copy.deepcopy(fixture())
        changed["after"]["context_rebuilds"] = 1
        with self.assertRaisesRegex(ValueError, "not rebuilt"):
            verify.verify(changed, "e" * 64)

    def test_rejects_source_filter_mismatch(self):
        changed = copy.deepcopy(fixture())
        changed["after"]["allocated_source_filters"] = 1
        with self.assertRaisesRegex(ValueError, "after"):
            verify.verify(changed, "e" * 64)

    def test_rejects_physical_device_overclaim(self):
        changed = copy.deepcopy(fixture())
        changed["physical_device_switch_exercised"] = True
        with self.assertRaisesRegex(ValueError, "overclaims"):
            verify.verify(changed, "e" * 64)


if __name__ == "__main__":
    unittest.main()
