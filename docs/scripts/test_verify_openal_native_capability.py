import copy
import unittest

import verify_openal_native_capability as verify


def fixture():
    return {
        "schema_version": 1,
        "status": "valid-capability-probe",
        "thread_name": "Sound engine",
        "active_context": True,
        "device_name": "OpenAL Soft",
        "vendor": "OpenAL Community",
        "renderer": "OpenAL Soft",
        "version": "1.1 ALSOFT",
        "openal_soft": True,
        "efx_supported": True,
        "maximum_auxiliary_sends": 2,
        "hrtf_extension_supported": True,
        "hrtf_enabled": False,
        "efx_resources_created": True,
        "efx_resources_released": True,
        "al_error_code": 0,
        "default_audio_path_changed": False,
        "release_calibrated": False,
    }


class OpenAlNativeCapabilityVerifyTest(unittest.TestCase):
    def test_accepts_live_efx_lifecycle_without_enabling_hrtf(self):
        result = verify.verify(fixture(), "b" * 64)
        self.assertTrue(result["gates"]["native_efx_eligible"])
        self.assertFalse(result["gates"]["hrtf_enabled"])

    def test_accepts_consistent_no_efx_fallback(self):
        changed = copy.deepcopy(fixture())
        changed.update(
            {
                "efx_supported": False,
                "maximum_auxiliary_sends": 0,
                "efx_resources_created": False,
                "efx_resources_released": False,
            }
        )
        result = verify.verify(changed, "b" * 64)
        self.assertFalse(result["gates"]["native_efx_eligible"])

    def test_rejects_advertised_efx_without_cleanup(self):
        changed = copy.deepcopy(fixture())
        changed["efx_resources_released"] = False
        with self.assertRaisesRegex(ValueError, "lifecycle"):
            verify.verify(changed, "b" * 64)

    def test_rejects_hrtf_without_extension(self):
        changed = copy.deepcopy(fixture())
        changed["hrtf_extension_supported"] = False
        changed["hrtf_enabled"] = True
        with self.assertRaisesRegex(ValueError, "HRTF"):
            verify.verify(changed, "b" * 64)


if __name__ == "__main__":
    unittest.main()
