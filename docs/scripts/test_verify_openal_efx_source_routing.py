import copy
import unittest

import verify_openal_efx_source_routing as verify


def fixture():
    return {
        "schema_version": 1,
        "status": "valid-routing-probe",
        "thread_name": "Sound engine",
        "source_found": True,
        "instance_type": "DroneLoopSoundInstance",
        "resources_created": True,
        "direct_filter_and_aux_send_attached": True,
        "vanilla_routing_restored": True,
        "resources_released": True,
        "al_error_code": 0,
        "persistent_efx_enabled": False,
        "release_calibrated": False,
    }


class OpenAlEfxSourceRoutingVerifyTest(unittest.TestCase):
    def test_accepts_transient_routing_and_restoration(self):
        result = verify.verify(fixture(), "c" * 64)
        self.assertTrue(result["gates"]["vanilla_routing_restored"])
        self.assertFalse(result["gates"]["persistent_efx_enabled"])

    def test_rejects_missing_drone_source(self):
        changed = copy.deepcopy(fixture())
        changed["source_found"] = False
        with self.assertRaisesRegex(ValueError, "source"):
            verify.verify(changed, "c" * 64)

    def test_rejects_attached_state_left_behind(self):
        changed = copy.deepcopy(fixture())
        changed["vanilla_routing_restored"] = False
        with self.assertRaisesRegex(ValueError, "lifecycle"):
            verify.verify(changed, "c" * 64)


if __name__ == "__main__":
    unittest.main()
