import csv
import tempfile
import unittest
from pathlib import Path

import analyze_ptb_material_absorption as analyze


class PtbMaterialAnalysisTest(unittest.TestCase):
    def test_reduction_requires_explicit_valid_energy(self):
        result = analyze.reduce_octaves(
            [0.1, 0.2, 0.3, 0.4, 0.6, 0.8],
            [1.0, 2.0, 1.0, 3.0, 1.0, 5.0],
        )
        self.assertAlmostEqual(result["low"], 0.2)
        self.assertAlmostEqual(result["mid"], 0.45)
        self.assertAlmostEqual(result["high"], 0.8)
        with self.assertRaisesRegex(ValueError, "mid band"):
            analyze.reduce_octaves(
                [0.1] * 6, [1.0, 1.0, 1.0, 0.0, 0.0, 1.0]
            )
        with self.assertRaisesRegex(ValueError, r"in \[0, 1\]"):
            analyze.reduce_octaves([0.1] * 5 + [1.1], [1.0] * 6)

    def test_raw_csv_binding_and_candidate_median(self):
        manifest = {
            "source": {"archive_sha256": "a" * 64},
            "runtime_bands_hz": {
                "low": [125, 700],
                "mid": [700, 4000],
                "high": [4000, 20000],
            },
            "selection_policy": {"claim_boundary": "test only"},
            "rows": [
                {
                    "row_id": 1,
                    "category": "stone_dense",
                    "split": "train",
                    "description": "Stone A",
                    "material_code": "6",
                    "absorption": [0.01, 0.02, 0.03, 0.04, 0.05, 0.06],
                },
                {
                    "row_id": 2,
                    "category": "stone_dense",
                    "split": "train",
                    "description": "Stone B",
                    "material_code": "6",
                    "absorption": [0.03, 0.04, 0.05, 0.06, 0.07, 0.08],
                },
                {
                    "row_id": 3,
                    "category": "stone_dense",
                    "split": "holdout",
                    "description": "Stone C",
                    "material_code": "6",
                    "absorption": [0.02, 0.03, 0.04, 0.05, 0.06, 0.07],
                },
            ],
        }
        header = [
            "No.",
            "description",
            *[str(value) for value in analyze.FREQUENCIES],
            "material criteria",
            "primary reference",
            "diffuse field measurement",
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "selection.csv"
            with path.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.writer(stream)
                writer.writerow(["preface"])
                writer.writerow(header)
                for row in manifest["rows"]:
                    writer.writerow(
                        [
                            row["row_id"],
                            row["description"],
                            *row["absorption"],
                            row["material_code"],
                            "primary",
                            "x",
                        ]
                    )
            report = analyze.analyze(manifest, [1.0] * 6, path)
        category = report["categories"]["stone_dense"]
        self.assertTrue(report["raw_selection_csv_verified"])
        self.assertEqual(
            category["octave_median_absorption"]["125"], 0.02
        )
        self.assertAlmostEqual(category["holdout"]["mean_rmse"], 0.0)


if __name__ == "__main__":
    unittest.main()
