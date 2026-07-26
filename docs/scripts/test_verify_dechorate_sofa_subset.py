from __future__ import annotations

import copy
import hashlib
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import numpy as np

import analyze_dechorate_sofa_subset as analyze
import verify_dechorate_sofa_subset as verify


class DechorateSofaMetricTest(unittest.TestCase):
    def test_special_furniture_code_maps_to_physical_facets(self):
        self.assertEqual(analyze.effective_code("020002"), "010001")
        self.assertEqual(analyze.effective_code("011110"), "011110")

    def test_auc_handles_wins_losses_and_ties(self):
        self.assertEqual(analyze.auc([2.0], [1.0]), 1.0)
        self.assertEqual(analyze.auc([1.0], [2.0]), 0.0)
        self.assertEqual(analyze.auc([1.0], [1.0]), 0.5)

    def test_t20_recovers_synthetic_exponential_decay(self):
        time = np.arange(analyze.FS, dtype=float) / analyze.FS
        expected_rt60 = 0.4
        signal = np.sin(2 * np.pi * 1000 * time) * np.exp(
            -np.log(1000) * time / expected_rt60
        )
        result = analyze.decay_fit(signal, 0, 1000, -5.0, -25.0)
        self.assertIsNotNone(result)
        self.assertAlmostEqual(
            result["rt60_s"], expected_rt60, delta=0.05
        )


class DechorateSofaVerifyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.paths = [
            self.root / "manifest.json",
            self.root / "pins.json",
            self.root / "inventory.json",
            self.root / "d114.json",
        ]
        for index, path in enumerate(self.paths):
            path.write_text(f"fixture-{index}\n", encoding="utf-8")
        bindings = {
            "source_subset_manifest_sha256": self.digest(self.paths[0]),
            "source_pins_sha256": self.digest(self.paths[1]),
            "source_inventory_sha256": self.digest(self.paths[2]),
            "source_d114_report_sha256": self.digest(self.paths[3]),
        }
        correlations = {
            center: {
                "reflective_count_t20_spearman": 0.9,
            }
            for center in ("500", "1000", "2000")
        }
        bands_absorptive = {
            center: {"t20_median_s": 0.1}
            for center in ("500", "1000", "2000")
        }
        bands_reflective = {
            center: {"t20_median_s": 0.5}
            for center in ("500", "1000", "2000")
        }
        self.base = {
            "schema_version": 1,
            "status": "valid-dechorate-measured-sofa-analysis",
            **bindings,
            "measured_rirs": 66,
            "measured_audio_bytes": 115271864,
            "sofa_identity_audit": {
                "title_matches_filename": 66,
                "stale_room_description_count": 66,
                "room_identity_basis":
                    "hash-pinned public-folder filename and Drive id",
            },
            "geometry_summary": {
                "paths": 21,
                "physical_absolute_residual_max_samples": 4.0,
                "voxel_absolute_residual_max_samples": 80.0,
            },
            "direct_path_summary": {
                "absolute_shift_vs_absorptive_baseline_max_samples": 8,
            },
            "room_correlations": correlations,
            "room_summary": {
                "000000": {"bands": bands_absorptive},
                "011111": {"bands": bands_reflective},
            },
            "echo_summary": {
                "ceiling": {"reflective_vs_absorptive_auc": 1.0},
                "south": {"reflective_vs_absorptive_auc": 0.4},
            },
            "decision": {
                "measured_rir_subset_materialized": True,
                "measured_rir_metrics_admitted": True,
                "sofa_room_description_admitted": False,
                "filename_and_drive_id_room_identity_admitted": True,
                "furniture_causal_effect_identified": False,
                "production_material_fit_eligible": False,
                "production_change_required": False,
            },
            "physical_endpoint_opened": False,
            "captures_audio": False,
            "release_calibrated": False,
        }

    def tearDown(self):
        self.temp.cleanup()

    @staticmethod
    def digest(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def values(self):
        return copy.deepcopy(self.base)

    def run_verify(self, values):
        with mock.patch.object(
            verify.analyzer, "analyze", return_value=copy.deepcopy(values)
        ):
            return verify.verify(
                values,
                "a" * 64,
                self.paths[0],
                self.paths[1],
                self.paths[2],
                self.paths[3],
                self.root,
            )

    def test_accepts_measured_but_not_release_calibrated_report(self):
        result = self.run_verify(self.values())
        self.assertTrue(result["gates"]["measured_waveforms_recomputed"])
        self.assertTrue(
            result["gates"]["production_material_fit_rejected"]
        )

    def test_rejects_stale_description_as_identity(self):
        values = self.values()
        values["decision"]["sofa_room_description_admitted"] = True
        with self.assertRaisesRegex(ValueError, "claim boundary"):
            self.run_verify(values)

    def test_rejects_voxel_error_suppression(self):
        values = self.values()
        values["geometry_summary"][
            "voxel_absolute_residual_max_samples"
        ] = 10.0
        with self.assertRaisesRegex(ValueError, "geometry"):
            self.run_verify(values)

    def test_rejects_decay_trend_reversal(self):
        values = self.values()
        values["room_summary"]["011111"]["bands"]["1000"][
            "t20_median_s"
        ] = 0.05
        with self.assertRaisesRegex(ValueError, "decay trend"):
            self.run_verify(values)

    def test_rejects_universal_facet_identification_claim(self):
        values = self.values()
        values["echo_summary"]["south"][
            "reflective_vs_absorptive_auc"
        ] = 0.7
        with self.assertRaisesRegex(ValueError, "identifiability"):
            self.run_verify(values)

    def test_rejects_production_fit_overclaim(self):
        values = self.values()
        values["decision"]["production_material_fit_eligible"] = True
        with self.assertRaisesRegex(ValueError, "claim boundary"):
            self.run_verify(values)

    def test_rejects_capture_overclaim(self):
        values = self.values()
        values["captures_audio"] = True
        with self.assertRaisesRegex(ValueError, "claim boundary"):
            self.run_verify(values)


if __name__ == "__main__":
    unittest.main()
