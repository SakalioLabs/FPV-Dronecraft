from __future__ import annotations

import copy
import csv
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import analyze_ptb_broadband_product_screen as analyze
import verify_ptb_broadband_product_screen as verify


class PtbBroadbandProductScreenVerifyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        cls.manifest = cls.root / "manifest.json"
        cls.selection = cls.root / "selection.csv"
        cls.shoebox = cls.root / "shoebox.json"
        cls.ptb = cls.root / "ptb.json"
        cls.d111 = cls.root / "d111.json"
        cls.manifest.write_text(
            json.dumps({"source": {"archive_sha256": "b" * 64}}),
            encoding="utf-8",
        )
        target = {"low": 0.4, "mid": 0.4, "high": 0.4}
        cls.shoebox.write_text(
            json.dumps(
                {
                    "status": "valid-diagnostic",
                    "rooms": [
                        {
                            "id": "air-booth",
                            "effective_eyring_absorption": target,
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )
        cls.ptb.write_text(
            json.dumps({"status": "valid-diagnostic"}),
            encoding="utf-8",
        )
        cls.d111.write_text(
            json.dumps(
                {
                    "status": "valid-scene-composition-inverse",
                    "rooms": [
                        {
                            "id": "air-booth",
                            "inverse_maximum_relative_error": 0.25,
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )
        header = [
            "No.",
            "description",
            "type",
            "trade name",
            "manufacturer",
            "character of absorption",
            "125",
            "250",
            "500",
            "1000",
            "2000",
            "4000",
        ]
        labels = []
        split_counts = {"discovery": 0, "holdout": 0}
        index = 0
        while len(labels) < 12 or min(split_counts.values()) < 2:
            label = f"fixture product {index}"
            key = f"fixture maker|{label}"
            digest = hashlib.sha256(key.encode()).hexdigest()
            split = (
                "holdout"
                if int(digest[:8], 16) % 5 == 0
                else "discovery"
            )
            if len(labels) < 12 or split_counts[split] < 2:
                labels.append((label, split))
                split_counts[split] += 1
            index += 1
        sentinel_label = next(
            label for label, split in labels if split == "holdout"
        )
        rows = []
        row_id = 100
        for position, (label, _) in enumerate(labels):
            if label == sentinel_label:
                continue
            value = 0.05 + 0.01 * (position % 8)
            rows.append(
                [row_id, label, "", label, "Fixture Maker", "2"]
                + [value] * 6
            )
            row_id += 1
        rows.extend(
            [
                [1088, sentinel_label, "", sentinel_label,
                 "Fixture Maker", "2"] + [0.30] * 6,
                [1089, sentinel_label, "", sentinel_label,
                 "Fixture Maker", "2"] + [0.10] * 6,
            ]
        )
        with cls.selection.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(header)
            writer.writerows(rows)
        cls.base = analyze.analyze(
            cls.manifest,
            cls.selection,
            cls.shoebox,
            cls.ptb,
            cls.d111,
        )
        cls.expected_counts = {
            key: cls.base["counts"][key]
            for key in verify.EXPECTED_COUNTS
        }

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def values(self):
        return copy.deepcopy(self.base)

    def run_verify(self, report):
        return verify.verify(
            report,
            "a" * 64,
            self.manifest,
            self.selection,
            self.shoebox,
            self.ptb,
            self.d111,
            self.expected_counts,
        )

    def test_accepts_pinned_product_screen(self):
        result = self.run_verify(self.values())
        self.assertTrue(
            result["gates"]["single_row_1088_rejected_as_leakage"]
        )
        self.assertEqual(
            result["metrics"]["eligible_product_groups"],
            self.expected_counts["eligible_product_groups"],
        )

    def test_rejects_status_change(self):
        values = self.values()
        values["status"] = "draft"
        with self.assertRaisesRegex(ValueError, "shape or source binding"):
            self.run_verify(values)

    def test_rejects_source_hash_change(self):
        values = self.values()
        values["source_selection_csv_sha256"] = "f" * 64
        with self.assertRaisesRegex(ValueError, "source binding"):
            self.run_verify(values)

    def test_rejects_target_detachment(self):
        values = self.values()
        values["target_effective_absorption"]["low"] += 0.01
        with self.assertRaisesRegex(ValueError, "target.low"):
            self.run_verify(values)

    def test_rejects_product_count_change(self):
        values = self.values()
        values["counts"]["eligible_product_groups"] -= 1
        with self.assertRaisesRegex(ValueError, "counts"):
            self.run_verify(values)

    def test_rejects_discovery_winner_change(self):
        values = self.values()
        values["top_discovery_products"][0][
            "maximum_relative_target_error"
        ] += 0.01
        with self.assertRaisesRegex(ValueError, "top_discovery"):
            self.run_verify(values)

    def test_rejects_holdout_winner_change(self):
        values = self.values()
        values["top_holdout_products"][0]["row_ids"] = [999]
        with self.assertRaisesRegex(ValueError, "top_holdout"):
            self.run_verify(values)

    def test_rejects_discovery_best_metric_change(self):
        values = self.values()
        values["best_discovery_maximum_relative_error"] += 0.01
        with self.assertRaisesRegex(ValueError, "best_discovery"):
            self.run_verify(values)

    def test_rejects_holdout_best_metric_change(self):
        values = self.values()
        values["best_holdout_maximum_relative_error"] += 0.01
        with self.assertRaisesRegex(ValueError, "best_holdout"):
            self.run_verify(values)

    def test_rejects_eligible_count_change(self):
        values = self.values()
        values["eligible_discovery_product_count"] = 1
        with self.assertRaisesRegex(ValueError, "eligible_discovery"):
            self.run_verify(values)

    def test_rejects_leakage_row_change(self):
        values = self.values()
        values["leakage_diagnostic"]["row_id"] = 1087
        with self.assertRaisesRegex(ValueError, "leakage_diagnostic.row_id"):
            self.run_verify(values)

    def test_rejects_leakage_split_change(self):
        values = self.values()
        values["leakage_diagnostic"]["row_split"] = "discovery"
        with self.assertRaisesRegex(ValueError, "row_split"):
            self.run_verify(values)

    def test_rejects_single_row_selection_permission(self):
        values = self.values()
        values["leakage_diagnostic"][
            "single_row_target_screen_forbidden"
        ] = False
        with self.assertRaisesRegex(ValueError, "forbidden"):
            self.run_verify(values)

    def test_rejects_product_median_detachment(self):
        values = self.values()
        values["leakage_diagnostic"][
            "product_median_runtime_absorption"
        ]["mid"] += 0.01
        with self.assertRaisesRegex(ValueError, "product_median"):
            self.run_verify(values)

    def test_rejects_filter_change(self):
        values = self.values()
        values["filter"]["clamping"] = "allowed"
        with self.assertRaisesRegex(ValueError, "screening contract"):
            self.run_verify(values)

    def test_rejects_row_level_split(self):
        values = self.values()
        values["split"]["product_level"] = False
        with self.assertRaisesRegex(ValueError, "screening contract"):
            self.run_verify(values)

    def test_rejects_target_dependent_split(self):
        values = self.values()
        values["split"]["target_independent"] = False
        with self.assertRaisesRegex(ValueError, "screening contract"):
            self.run_verify(values)

    def test_rejects_split_algorithm_change(self):
        values = self.values()
        values["split"]["algorithm"] = "row number modulo 5"
        with self.assertRaisesRegex(ValueError, "screening contract"):
            self.run_verify(values)

    def test_rejects_contact_data_emission(self):
        values = self.values()
        values["product_grouping"]["contact_data_emitted"] = True
        with self.assertRaisesRegex(ValueError, "screening contract"):
            self.run_verify(values)

    def test_rejects_threshold_change(self):
        values = self.values()
        values["ranking"]["threshold"] = 0.20
        with self.assertRaisesRegex(ValueError, "screening contract"):
            self.run_verify(values)

    def test_rejects_basis_selection(self):
        values = self.values()
        values["decision"]["basis_extension_selected"] = True
        with self.assertRaisesRegex(ValueError, "decision"):
            self.run_verify(values)

    def test_rejects_d111_basis_claim_change(self):
        values = self.values()
        values["decision"][
            "d111_four_material_basis_still_insufficient_for_booth"
        ] = False
        with self.assertRaisesRegex(ValueError, "decision"):
            self.run_verify(values)

    def test_rejects_production_change(self):
        values = self.values()
        values["production_change_required"] = True
        with self.assertRaisesRegex(ValueError, "decision"):
            self.run_verify(values)

    def test_rejects_endpoint_capture_claim(self):
        values = self.values()
        values["captures_audio"] = True
        with self.assertRaisesRegex(ValueError, "claim boundary"):
            self.run_verify(values)

    def test_rejects_release_calibration_claim(self):
        values = self.values()
        values["release_calibrated"] = True
        with self.assertRaisesRegex(ValueError, "claim boundary"):
            self.run_verify(values)


if __name__ == "__main__":
    unittest.main()
