import copy
import unittest

import verify_two_room_portal_coupling as verify


def report():
    steps = []
    for index, aperture in enumerate(verify.APERTURES):
        decay = 4.0 - index * 0.4
        steps.append(
            {
                "aperture_cells": aperture,
                "room_b_hit_fraction": index * 0.07,
                "rays_entering_room_b": index * 680,
                "rays_returning_to_room_a": index * 660,
                "effective_mfp_m": 4.0 + index * 0.1,
                "escaped_rays": 0,
                "openness": 0.0,
                "rt60_s": {band: decay for band in verify.BANDS},
                "edt_s": {band: decay * 0.9 for band in verify.BANDS},
                "wet_gain": 0.4 - index * 0.002,
            }
        )
    steps[-1]["rays_entering_room_b"] = 4080
    steps[-1]["rays_returning_to_room_a"] = 4000
    return {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "room_interior_cells_each": {
            "length": 9,
            "width": 9,
            "height": 5,
        },
        "ray_count": 4096,
        "maximum_bounces": 48,
        "steps": steps,
    }


class TwoRoomPortalCouplingVerifyTest(unittest.TestCase):
    def test_accepts_monotonic_transport_and_return(self):
        result = verify.verify(report())
        self.assertTrue(
            result["gates"]["portal_transport_not_mislabeled_as_escape"]
        )
        self.assertFalse(result["release_calibrated"])

    def test_rejects_portal_as_snapshot_escape(self):
        changed = copy.deepcopy(report())
        changed["steps"][2]["escaped_rays"] = 1
        with self.assertRaisesRegex(ValueError, "escaped"):
            verify.verify(changed)


if __name__ == "__main__":
    unittest.main()
