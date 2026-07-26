from __future__ import annotations

import copy
import csv
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import analyze_air_room_construction_evidence as analyze
import verify_air_room_construction_evidence as verify


ROOT = Path(__file__).resolve().parents[2]
SOURCE_MANIFEST = (
    ROOT / "docs/acoustics/air-room-construction-evidence-v1.json"
)


class AirRoomConstructionEvidenceVerifyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        cls.manifest = cls.root / "manifest.json"
        cls.paper = cls.root / "paper.pdf"
        cls.metadata = cls.root / "metadata.csv"
        cls.shoebox = cls.root / "shoebox.json"
        cls.d112 = cls.root / "d112.json"
        cls.paper.write_bytes(b"fixture-pdf")
        header = [
            "",
            "filename",
            "src_id",
            "src_type",
            "src_pos_x",
            "src_pos_y",
            "src_pos_z",
            "room_code",
            *verify.REFLECTIVITY_COLUMNS,
            "room_fornitures",
            "room_temperature",
            "mic_type",
            "mic_id",
            "mic_pos_x",
            "mic_pos_y",
            "mic_pos_z",
        ]
        rows = []
        for index, code in enumerate(("000000", "010000")):
            rows.append(
                [
                    index * 2,
                    f"capture-{index}",
                    "0.0",
                    "omnidirectional",
                    "1",
                    "2",
                    "3",
                    code,
                    "0.0",
                    "1.0",
                    "0.0",
                    "0.0",
                    "0.0",
                    "0.0",
                    "False" if index == 0 else "True",
                    "24.0",
                    "capsule",
                    "0.0",
                    "1",
                    "2",
                    "1",
                ]
            )
            rows.append(
                [
                    index * 2 + 1,
                    f"capture-{index}",
                    "99.0",
                    "silence",
                    "",
                    "",
                    "",
                    code,
                    "0.0",
                    "1.0",
                    "0.0",
                    "0.0",
                    "0.0",
                    "0.0",
                    "False" if index == 0 else "True",
                    "24.0",
                    "loopback",
                    "30.0",
                    "",
                    "",
                    "",
                ]
            )
        with cls.metadata.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(header)
            writer.writerows(rows)
        manifest = json.loads(SOURCE_MANIFEST.read_text(encoding="utf-8"))
        manifest["air_paper"]["bytes"] = cls.paper.stat().st_size
        manifest["air_paper"]["sha256"] = hashlib.sha256(
            cls.paper.read_bytes()
        ).hexdigest()
        spec = manifest["dechorate_metadata"]
        spec["bytes"] = cls.metadata.stat().st_size
        spec["sha256"] = hashlib.sha256(cls.metadata.read_bytes()).hexdigest()
        spec["expected_rows"] = 4
        spec["expected_rows_per_room_code"] = 2
        spec["expected_loopback_rows_without_spatial_coordinates"] = 2
        spec["expected_room_codes"] = ["000000", "010000"]
        cls.manifest.write_text(json.dumps(manifest), encoding="utf-8")
        cls.shoebox.write_text(
            json.dumps(
                {
                    "status": "valid-diagnostic",
                    "rooms": [
                        {
                            "id": "air-booth",
                            "physical_dimensions_m": {
                                "length": 3.0,
                                "width": 1.8,
                                "height": 2.2,
                            },
                        },
                        {
                            "id": "air-lecture",
                            "physical_dimensions_m": {
                                "length": 10.8,
                                "width": 10.9,
                                "height": 3.15,
                            },
                        },
                    ],
                }
            ),
            encoding="utf-8",
        )
        cls.d112.write_text(
            json.dumps(
                {
                    "status": "valid-ptb-broadband-product-screen",
                    "decision": {"basis_extension_selected": False},
                }
            ),
            encoding="utf-8",
        )
        cls.base = analyze.analyze(
            cls.manifest,
            cls.paper,
            cls.metadata,
            cls.shoebox,
            cls.d112,
        )

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
            self.paper,
            self.metadata,
            self.shoebox,
            self.d112,
        )

    def test_accepts_inventory_and_metadata_boundary(self):
        result = self.run_verify(self.values())
        self.assertTrue(result["gates"]["semantic_constraints_admitted"])
        self.assertTrue(
            result["gates"]["material_fit_rejected_as_underidentified"]
        )

    def test_rejects_source_binding_change(self):
        values = self.values()
        values["source_air_paper_sha256"] = "f" * 64
        with self.assertRaisesRegex(ValueError, "source binding"):
            self.run_verify(values)

    def test_rejects_paper_locator_change(self):
        values = self.values()
        values["air_paper"]["table"] = 2
        with self.assertRaisesRegex(ValueError, "locator"):
            self.run_verify(values)

    def test_rejects_room_order_change(self):
        values = self.values()
        values["air_rooms"].reverse()
        with self.assertRaisesRegex(ValueError, "room order"):
            self.run_verify(values)

    def test_rejects_booth_panel_change(self):
        values = self.values()
        values["air_rooms"][0]["wall_surface"] = ["concrete"]
        with self.assertRaisesRegex(ValueError, "wall_surface"):
            self.run_verify(values)

    def test_rejects_booth_furniture_change(self):
        values = self.values()
        values["air_rooms"][0]["furniture"] = ["chair"]
        with self.assertRaisesRegex(ValueError, "furniture"):
            self.run_verify(values)

    def test_rejects_lecture_wall_count_change(self):
        values = self.values()
        values["air_rooms"][1]["wall_surface_counts"] = [2, 2]
        with self.assertRaisesRegex(ValueError, "wall_surface_counts"):
            self.run_verify(values)

    def test_rejects_lecture_floor_change(self):
        values = self.values()
        values["air_rooms"][1]["floor_cover"] = "carpet"
        with self.assertRaisesRegex(ValueError, "floor_cover"):
            self.run_verify(values)

    def test_rejects_missing_ceiling_becoming_known(self):
        values = self.values()
        values["air_rooms"][0]["missing_for_material_calibration"].remove(
            "ceiling_surface"
        )
        with self.assertRaisesRegex(ValueError, "missing evidence"):
            self.run_verify(values)

    def test_rejects_material_fit_eligibility(self):
        values = self.values()
        values["air_rooms"][0]["material_parameter_fit_eligible"] = True
        with self.assertRaisesRegex(ValueError, "eligibility"):
            self.run_verify(values)

    def test_rejects_metadata_metric_change(self):
        values = self.values()
        values["dechorate_metadata"]["rows"] += 1
        with self.assertRaisesRegex(ValueError, "metadata metrics"):
            self.run_verify(values)

    def test_rejects_air_material_fit_decision(self):
        values = self.values()
        values["decision"]["air_material_parameter_fit_eligible"] = True
        with self.assertRaisesRegex(ValueError, "decision"):
            self.run_verify(values)

    def test_rejects_unconstrained_search_revival(self):
        values = self.values()
        values["decision"][
            "d112_unconstrained_product_search_remains_rejected"
        ] = False
        with self.assertRaisesRegex(ValueError, "decision"):
            self.run_verify(values)

    def test_rejects_full_payload_requirement(self):
        values = self.values()
        values["decision"]["full_dechorate_payload_required_now"] = True
        with self.assertRaisesRegex(ValueError, "decision"):
            self.run_verify(values)

    def test_rejects_production_change(self):
        values = self.values()
        values["decision"]["production_change_required"] = True
        with self.assertRaisesRegex(ValueError, "decision"):
            self.run_verify(values)

    def test_rejects_capture_overclaim(self):
        values = self.values()
        values["captures_audio"] = True
        with self.assertRaisesRegex(ValueError, "claim boundary"):
            self.run_verify(values)

    def test_rejects_release_overclaim(self):
        values = self.values()
        values["release_calibrated"] = True
        with self.assertRaisesRegex(ValueError, "claim boundary"):
            self.run_verify(values)


if __name__ == "__main__":
    unittest.main()
