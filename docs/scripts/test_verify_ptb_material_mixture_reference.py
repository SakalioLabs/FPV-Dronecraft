import copy
import unittest

import verify_ptb_material_mixture_reference as verify


def fixture():
    categories = {}
    mappings = {
        "stone_dense": "floor",
        "porous_wool": "ceiling",
        "wood_solid_panel": "x_walls",
        "glass_window": "z_walls",
    }
    candidate_surfaces = {}
    current_surfaces = {}
    for index, category in enumerate(mappings, start=1):
        candidate = {
            "low": 0.1 * index,
            "mid": 0.1 * index,
            "high": 0.1 * index,
        }
        current = {"low": 0.05, "mid": 0.05, "high": 0.05}
        categories[category] = {
            "runtime_candidate_absorption": candidate,
            "current_runtime_absorption": current,
        }
        candidate_surfaces[mappings[category]] = candidate
        current_surfaces[mappings[category]] = current
    material = {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "manifest_sha256": "a" * 64,
        "raw_selection_csv_verified": True,
        "raw_corpus_audit": {
            "rows": 2574,
            "rows_with_coefficient_above_one": 518,
        },
        "categories": categories,
    }
    mixture = {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "source_manifest_sha256": "a" * 64,
        "cases": [
            {
                "id": "current-hypotheses",
                "surface_absorption": current_surfaces,
            },
            {
                "id": "ptb-research-candidates",
                "surface_absorption": candidate_surfaces,
                "voxel_relative_error_vs_arithmetic": {
                    "low": 0.3,
                    "mid": 0.4,
                    "high": 0.5,
                },
                "voxel_relative_error_vs_mean_log": {
                    "low": 0.1,
                    "mid": 0.2,
                    "high": 0.25,
                },
            },
        ],
    }
    return material, mixture


class PtbMaterialMixtureVerifyTest(unittest.TestCase):
    def test_binds_reports_and_preserves_failed_release_gate(self):
        material, mixture = fixture()
        result = verify.verify(material, mixture)
        self.assertTrue(result["candidate_probe_closer_to_mean_log"])
        self.assertFalse(
            result["gates"][
                "candidate_probe_within_10_percent_of_mean_log"
            ]
        )
        self.assertFalse(result["release_calibrated"])

    def test_rejects_detached_candidate(self):
        material, mixture = fixture()
        changed = copy.deepcopy(mixture)
        changed["cases"][1]["surface_absorption"]["floor"]["low"] = 0.9
        with self.assertRaisesRegex(ValueError, "candidate is detached"):
            verify.verify(material, changed)


if __name__ == "__main__":
    unittest.main()
