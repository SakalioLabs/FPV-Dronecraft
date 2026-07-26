from __future__ import annotations

import copy
import unittest

import numpy as np

import verify_air_scene_composition_inverse as verify


def _dict(values):
    return {
        band: float(value)
        for band, value in zip(verify.BANDS, values)
    }


def fixture():
    materials = np.array(
        [
            [0.02666666666666667, 0.035, 0.07],
            [0.12, 0.09, 0.10],
            [0.40, 0.775, 0.85],
            [0.17666666666666667, 0.06, 0.03],
        ]
    )
    room_values = [
        (
            "air-booth",
            {"length": 3, "width": 2, "height": 2},
            np.array(
                [0.1268693589758476, 0.13715287006894902,
                 0.10821303407466658]
            ),
            np.array(
                [0.3766449844102459, 0.3541586048896441,
                 0.425424509351638]
            ),
            np.array([1.5, 0.7, 0.75]),
        ),
        (
            "air-lecture",
            {"length": 11, "width": 11, "height": 3},
            np.array(
                [0.857164910011111, 0.9030624220156351,
                 0.6915336132873643]
            ),
            np.array(
                [0.17079555900769963, 0.16286483520119263,
                 0.20717010229065597]
            ),
            np.array([1.05, 0.90, 0.98]),
        ),
    ]
    source_rooms = []
    report_rooms = []
    candidate_count = None
    for name, dimensions, measured, target, factor in room_values:
        fit = verify._fit_cached(
            tuple(materials.ravel()),
            tuple(target),
        )
        weights = np.array(fit[0])
        predicted = np.array(fit[1])
        relative = np.array(fit[2])
        candidate_count = fit[3]
        source_rooms.append(
            {
                "id": name,
                "voxel_interior_cells": copy.deepcopy(dimensions),
                "measured_rt60_s": _dict(measured),
                "effective_eyring_absorption": _dict(target),
            }
        )
        surface_cells = (
            (dimensions["length"] + 2)
            * (dimensions["width"] + 2)
            * (dimensions["height"] + 2)
            - dimensions["length"]
            * dimensions["width"]
            * dimensions["height"]
        )
        raw = weights * surface_cells
        counts = np.floor(raw).astype(int)
        for index in np.argsort(-(raw - counts))[
            : surface_cells - int(np.sum(counts))
        ]:
            counts[index] += 1
        rt60 = measured * factor
        rt60_error = np.abs(rt60 - measured) / measured
        layouts = [
            {
                "layout": layout,
                "surface_cell_counts": counts.tolist(),
                "rt60_s": _dict(rt60),
                "relative_error": _dict(rt60_error),
                "surface_hits": (
                    verify.RAY_COUNT * verify.MAXIMUM_BOUNCES
                ),
                "escaped_rays": 0,
                "truncated_legs": 0,
            }
            for layout in range(verify.LAYOUT_COUNT)
        ]
        report_rooms.append(
            {
                "id": name,
                "voxel_interior_cells": copy.deepcopy(dimensions),
                "measured_rt60_s": _dict(measured),
                "target_effective_absorption": _dict(target),
                "selected_weights": weights.tolist(),
                "predicted_mean_log_absorption": _dict(predicted),
                "inverse_relative_absorption_error": _dict(relative),
                "inverse_maximum_relative_error": float(
                    np.max(relative)
                ),
                "voxel_median_rt60_s": _dict(rt60),
                "voxel_minimum_rt60_s": _dict(rt60),
                "voxel_maximum_rt60_s": _dict(rt60),
                "voxel_median_relative_error": _dict(rt60_error),
                "voxel_maximum_median_relative_error": float(
                    np.max(rt60_error)
                ),
                "layouts": layouts,
            }
        )
    d110_sha = "1" * 64
    shoebox_sha = "2" * 64
    ptb_sha = "3" * 64
    d110 = {
        "status": "valid-acoustic-calibration-admissibility",
        "decision": {
            "next_parameter_level":
                "scene-composition-and-interior-treatment",
            "replace_runtime_stone_absorption": False,
        },
    }
    shoebox = {"status": "valid-diagnostic", "rooms": source_rooms}
    categories = {}
    for category, values in zip(
        (
            "stone_dense",
            "wood_solid_panel",
            "porous_wool",
            "glass_window",
        ),
        materials,
    ):
        categories[category] = {
            "runtime_candidate_absorption": _dict(values)
        }
    ptb = {"status": "valid-diagnostic", "categories": categories}
    report = {
        "schema_version": 1,
        "status": "valid-scene-composition-inverse",
        "source_d110_report_sha256": d110_sha,
        "source_air_shoebox_sha256": shoebox_sha,
        "source_ptb_material_sha256": ptb_sha,
        "material_order": list(verify.MATERIAL_ORDER),
        "material_absorption": [_dict(row) for row in materials],
        "inverse_model": (
            "simplex-grid-minimax-relative-absorption-error-"
            "mean-log-retention"
        ),
        "grid_denominator": verify.GRID_DENOMINATOR,
        "candidates_per_room": candidate_count,
        "layouts_per_room": verify.LAYOUT_COUNT,
        "ray_count": verify.RAY_COUNT,
        "maximum_bounces": verify.MAXIMUM_BOUNCES,
        "rooms": report_rooms,
        "production_change_required": False,
        "physical_endpoint_opened": False,
        "captures_audio": False,
        "release_calibrated": False,
    }
    return {
        "report": report,
        "d110": d110,
        "d110_sha": d110_sha,
        "shoebox": shoebox,
        "shoebox_sha": shoebox_sha,
        "ptb": ptb,
        "ptb_sha": ptb_sha,
    }


def run_verify(values):
    return verify.verify(
        values["report"],
        "4" * 64,
        values["d110"],
        values["d110_sha"],
        values["shoebox"],
        values["shoebox_sha"],
        values["ptb"],
        values["ptb_sha"],
    )


class AirSceneCompositionInverseVerifyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base = fixture()

    def values(self):
        return copy.deepcopy(self.base)

    def test_accepts_lecture_and_rejects_booth_profile(self):
        result = run_verify(self.values())
        self.assertTrue(
            result["gates"]["lecture_inverse_and_voxel_holdout_pass"]
        )
        self.assertTrue(
            result["gates"]["booth_four_material_basis_insufficient"]
        )

    def test_rejects_source_binding_change(self):
        values = self.values()
        values["report"]["source_d110_report_sha256"] = "f" * 64
        with self.assertRaisesRegex(ValueError, "source binding"):
            run_verify(values)

    def test_rejects_source_policy_change(self):
        values = self.values()
        values["d110"]["decision"][
            "replace_runtime_stone_absorption"
        ] = True
        with self.assertRaisesRegex(ValueError, "source policy"):
            run_verify(values)

    def test_rejects_material_order_change(self):
        values = self.values()
        values["report"]["material_order"].reverse()
        with self.assertRaisesRegex(ValueError, "inverse contract"):
            run_verify(values)

    def test_rejects_material_detachment(self):
        values = self.values()
        values["report"]["material_absorption"][0]["low"] += 0.01
        with self.assertRaisesRegex(ValueError, "material 0"):
            run_verify(values)

    def test_rejects_room_order_change(self):
        values = self.values()
        values["report"]["rooms"].reverse()
        with self.assertRaisesRegex(ValueError, "room order"):
            run_verify(values)

    def test_rejects_target_detachment(self):
        values = self.values()
        values["report"]["rooms"][0][
            "target_effective_absorption"
        ]["low"] += 0.01
        with self.assertRaisesRegex(ValueError, "target absorption"):
            run_verify(values)

    def test_rejects_nonoptimal_weights(self):
        values = self.values()
        values["report"]["rooms"][0]["selected_weights"] = [
            0.25, 0.25, 0.25, 0.25
        ]
        with self.assertRaisesRegex(ValueError, "simplex weights"):
            run_verify(values)

    def test_rejects_inverse_metric_tamper(self):
        values = self.values()
        values["report"]["rooms"][0][
            "inverse_maximum_relative_error"
        ] += 0.01
        with self.assertRaisesRegex(
            ValueError, "inverse maximum relative error"
        ):
            run_verify(values)

    def test_rejects_layout_order_change(self):
        values = self.values()
        values["report"]["rooms"][0]["layouts"].reverse()
        with self.assertRaisesRegex(ValueError, "layout order"):
            run_verify(values)

    def test_rejects_surface_count_change(self):
        values = self.values()
        values["report"]["rooms"][0]["layouts"][0][
            "surface_cell_counts"
        ][0] += 1
        with self.assertRaisesRegex(ValueError, "surface-cell mixture"):
            run_verify(values)

    def test_rejects_layout_metric_tamper(self):
        values = self.values()
        values["report"]["rooms"][0]["layouts"][0][
            "relative_error"
        ]["low"] += 0.01
        with self.assertRaisesRegex(ValueError, "layout relative error"):
            run_verify(values)

    def test_rejects_incomplete_probe(self):
        values = self.values()
        values["report"]["rooms"][0]["layouts"][0][
            "truncated_legs"
        ] = 1
        with self.assertRaisesRegex(ValueError, "probe completeness"):
            run_verify(values)

    def test_rejects_median_tamper(self):
        values = self.values()
        values["report"]["rooms"][1][
            "voxel_median_rt60_s"
        ]["mid"] += 0.01
        with self.assertRaisesRegex(ValueError, "median RT60"):
            run_verify(values)

    def test_rejects_production_overclaim(self):
        values = self.values()
        values["report"]["production_change_required"] = True
        with self.assertRaisesRegex(
            ValueError, "production_change_required must be False"
        ):
            run_verify(values)

    def test_rejects_capture_overclaim(self):
        values = self.values()
        values["report"]["captures_audio"] = True
        with self.assertRaisesRegex(
            ValueError, "captures_audio must be False"
        ):
            run_verify(values)


if __name__ == "__main__":
    unittest.main()
