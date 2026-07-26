import copy
import unittest

import verify_minecraft_portal_gametest as verify


def fixture():
    return {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "source": "integrated-client-world",
        "sequence": ["closed", "open", "closed"],
        "portal_aperture_cells": 12,
        "ray_count": 4096,
        "maximum_bounces": 48,
        "closed_room_b_hits": 0,
        "open_room_b_hits": 500,
        "open_rays_entering_room_b": 100,
        "open_rays_returning_room_a": 50,
        "closed_mid_rt60_s": 3.9,
        "open_mid_rt60_s": 0.9,
        "closed_wet_gain": 0.38,
        "open_wet_gain": 0.37,
        "closed_openness": 0.0,
        "open_openness": 0.0,
        "closed_endpoint_repeatable": True,
        "release_calibrated": False,
    }


class MinecraftPortalGameTestVerifyTest(unittest.TestCase):
    def test_accepts_integrated_transport_and_repeatable_endpoint(self):
        result = verify.verify(fixture(), "a" * 64)
        self.assertTrue(result["gates"]["open_portal_returns_rays"])
        self.assertFalse(
            result["gates"]["minecraft_release_calibrated"]
        )

    def test_rejects_portal_reported_as_snapshot_escape(self):
        changed = copy.deepcopy(fixture())
        changed["open_openness"] = 0.1
        with self.assertRaisesRegex(ValueError, "gates failed"):
            verify.verify(changed, "a" * 64)

    def test_rejects_missing_return_path(self):
        changed = copy.deepcopy(fixture())
        changed["open_rays_returning_room_a"] = 0
        with self.assertRaisesRegex(ValueError, "gates failed"):
            verify.verify(changed, "a" * 64)


if __name__ == "__main__":
    unittest.main()
