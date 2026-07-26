import copy
import unittest

import verify_ptb_material_mixture_convergence as verify


def report(mode, area_error, fraction_error):
    runs = []
    for bounces in verify.BOUNCES:
        for rays in verify.RAYS:
            runs.append(
                {
                    "rays": rays,
                    "bounces": bounces,
                    "normal_material_axis_mismatches": 0,
                    "maximum_absolute_hit_fraction_error": fraction_error,
                    "relative_error_vs_area_mean_log": {
                        "low": area_error,
                        "mid": area_error,
                        "high": area_error,
                    },
                    "relative_error_vs_largest_budget": {
                        "low": 0.01,
                        "mid": 0.01,
                        "high": 0.01,
                    },
                }
            )
    runs[-1]["relative_error_vs_largest_budget"] = {
        "low": 0.0,
        "mid": 0.0,
        "high": 0.0,
    }
    return {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "scattering_mode": mode,
        "source_manifest_sha256": "a" * 64,
        "area_mean_log_absorption": {
            "low": 0.2,
            "mid": 0.3,
            "high": 0.4,
        },
        "room_interior_cells": {"length": 11, "width": 11, "height": 3},
        "runs": runs,
    }


class PtbMixtureConvergenceVerifyTest(unittest.TestCase):
    def test_diffuse_control_is_allowed_to_pass_while_configured_fails(self):
        result = verify.verify(
            report("configured", 0.30, 0.10),
            report("diffuse", 0.07, 0.04),
            report("late-diffuse-2", 0.08, 0.04),
        )
        self.assertFalse(
            result["gates"][
                "configured_scattering_within_10_percent_of_diffuse_formula"
            ]
        )
        self.assertTrue(
            result["gates"][
                "diffuse_control_within_10_percent_of_diffuse_formula"
            ]
        )
        self.assertTrue(
            result["gates"][
                "late_diffuse_2_within_10_percent_of_diffuse_formula"
            ]
        )

    def test_rejects_material_normal_mismatch(self):
        configured = report("configured", 0.30, 0.10)
        changed = copy.deepcopy(configured)
        changed["runs"][0]["normal_material_axis_mismatches"] = 1
        with self.assertRaisesRegex(
            ValueError, "material/normal mismatch"
        ):
            verify.verify(
                changed,
                report("diffuse", 0.07, 0.04),
                report("late-diffuse-2", 0.08, 0.04),
            )


if __name__ == "__main__":
    unittest.main()
