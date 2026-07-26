import copy
import unittest

import verify_ptb_path_mixing_time as verify


def report():
    runs = []
    for room, dimensions in verify.ROOMS.items():
        common = {
            "room": room,
            "dimensions_m": dict(
                zip(("length", "width", "height"), dimensions)
            ),
            "analytic_mfp_m": 4.0,
            "probe_mfp_m": 4.01,
            "maximum_absolute_hit_fraction_error": 0.005,
            "normal_material_axis_mismatches": 0,
            "hits": 4096 * 48,
        }
        runs.append(
            {
                **common,
                "mode": "configured",
                "path_threshold_mfp_multiplier": None,
                "path_threshold_m": None,
                "path_threshold_s": None,
                "maximum_relative_error_vs_diffuse_formula": 0.25,
            }
        )
        runs.append(
            {
                **common,
                "mode": "fixed-two-hit",
                "path_threshold_mfp_multiplier": None,
                "path_threshold_m": None,
                "path_threshold_s": None,
                "maximum_relative_error_vs_diffuse_formula": 0.01,
            }
        )
        for multiplier in verify.MULTIPLIERS:
            meters = multiplier * 4.0
            runs.append(
                {
                    **common,
                    "mode": "path-scaled",
                    "path_threshold_mfp_multiplier": multiplier,
                    "path_threshold_m": meters,
                    "path_threshold_s": meters / verify.SOUND_SPEED_MPS,
                    "maximum_relative_error_vs_diffuse_formula": 0.02,
                }
            )
    return {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "ray_count": 4096,
        "maximum_bounces": 48,
        "path_threshold_mfp_multipliers": verify.MULTIPLIERS,
        "runs": runs,
    }


class PtbPathMixingTimeVerifyTest(unittest.TestCase):
    def test_accepts_complete_multi_room_matrix(self):
        result = verify.verify(report())
        self.assertTrue(result["gates"]["two_mfp_within_3_percent"])
        self.assertTrue(
            result["gates"]["configured_exposes_late_field_bias"]
        )
        self.assertFalse(result["release_calibrated"])

    def test_rejects_threshold_detached_from_mfp(self):
        changed = copy.deepcopy(report())
        candidate = next(
            row
            for row in changed["runs"]
            if row["mode"] == "path-scaled"
            and row["path_threshold_mfp_multiplier"] == 2.0
        )
        candidate["path_threshold_m"] += 0.25
        with self.assertRaisesRegex(ValueError, "detached from 4V/S"):
            verify.verify(changed)


if __name__ == "__main__":
    unittest.main()
