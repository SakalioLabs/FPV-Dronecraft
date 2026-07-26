import copy
import unittest

import verify_doorway_reverb_continuity as verify


def report():
    volume = 11 * 11 * 5
    surface = 2 * (11 * 11 + 11 * 5 + 11 * 5)
    threshold = 2.0 * 4.0 * volume / surface
    steps = []
    for index, aperture in enumerate(verify.APERTURES):
        decreasing = 4.0 - index * 0.25
        increasing = 0.1 + index * 0.1
        steps.append(
            {
                "aperture_cells": aperture,
                "escaped_rays": index * 400,
                "openness": index / 8,
                "rt60_s": {band: decreasing for band in verify.BANDS},
                "edt_s": {band: decreasing * 0.9 for band in verify.BANDS},
                "drr_db": {band: increasing for band in verify.BANDS},
                "wet_gain": 0.4 - index * 0.049,
            }
        )
    steps[-1]["openness"] = 0.99
    steps[-1]["wet_gain"] = 0.005
    return {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "room_interior_cells": {
            "length": 11,
            "width": 11,
            "height": 5,
        },
        "ray_count": 4096,
        "maximum_bounces": 48,
        "late_diffuse_path_threshold_m": threshold,
        "late_diffuse_path_threshold_s": threshold / verify.SOUND_SPEED_MPS,
        "steps": steps,
    }


class DoorwayReverbContinuityVerifyTest(unittest.TestCase):
    def test_accepts_monotonic_nested_doorway(self):
        result = verify.verify(report())
        self.assertTrue(result["gates"]["wet_gain_nonincreasing"])
        self.assertFalse(result["release_calibrated"])

    def test_rejects_rt60_reversal(self):
        changed = copy.deepcopy(report())
        changed["steps"][4]["rt60_s"]["mid"] = 9.0
        with self.assertRaisesRegex(ValueError, "not monotonic"):
            verify.verify(changed)


if __name__ == "__main__":
    unittest.main()
