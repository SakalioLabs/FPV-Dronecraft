from __future__ import annotations

import copy
import csv
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import analyze_dechorate_facet_rir_experiment as analyze
import verify_dechorate_facet_rir_experiment as verify


ROOT = Path(__file__).resolve().parents[2]
SOURCE_MANIFEST = (
    ROOT / "docs/acoustics/dechorate-facet-rir-experiment-v1.json"
)


class DechorateFacetRirExperimentVerifyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        cls.manifest_path = cls.root / "manifest.json"
        cls.metadata_path = cls.root / "metadata.csv"
        cls.source_dir = cls.root / "source"
        cls.source_dir.mkdir()
        cls.d113_path = cls.root / "d113.json"
        fields = [
            "src_id",
            "src_type",
            "src_pos_x",
            "src_pos_y",
            "src_pos_z",
            "mic_id",
            "mic_type",
            "mic_pos_x",
            "mic_pos_y",
            "mic_pos_z",
            "array_id",
        ]
        with cls.metadata_path.open(
            "w", encoding="utf-8", newline=""
        ) as stream:
            writer = csv.DictWriter(stream, fieldnames=fields)
            writer.writeheader()
            for source_id, source_type, source_x in (
                (4, "invdirectional", 0.0),
                (6, "omnidirectional", 40.0),
            ):
                for microphone_id in range(30):
                    writer.writerow(
                        {
                            "src_id": source_id,
                            "src_type": source_type,
                            "src_pos_x": source_x,
                            "src_pos_y": 0,
                            "src_pos_z": 1,
                            "mic_id": microphone_id,
                            "mic_type": "capsule",
                            "mic_pos_x": microphone_id + 1,
                            "mic_pos_y": 1,
                            "mic_pos_z": 1,
                            "array_id": microphone_id // 5,
                        }
                    )
        manifest = json.loads(SOURCE_MANIFEST.read_text(encoding="utf-8"))
        manifest["metadata"]["bytes"] = cls.metadata_path.stat().st_size
        manifest["metadata"]["sha256"] = hashlib.sha256(
            cls.metadata_path.read_bytes()
        ).hexdigest()
        for index, spec in enumerate(manifest["official_code"]["files"]):
            path = cls.source_dir / Path(spec["path"]).name
            path.write_text(f"fixture-{index}\n", encoding="utf-8")
            spec["bytes"] = path.stat().st_size
            spec["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
        cls.manifest_path.write_text(
            json.dumps(manifest), encoding="utf-8"
        )
        cls.d113_path.write_text(
            json.dumps(
                {
                    "status": "valid-air-room-construction-evidence",
                    "decision": {
                        "next_controlled_reference":
                            "dechorate-v2-metadata-first"
                    },
                }
            ),
            encoding="utf-8",
        )
        cls.base = analyze.analyze(
            cls.manifest_path,
            cls.metadata_path,
            cls.source_dir,
            cls.d113_path,
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
            self.manifest_path,
            self.metadata_path,
            self.source_dir,
            self.d113_path,
        )

    def test_accepts_hash_bound_metadata_only_contract(self):
        result = self.run_verify(self.values())
        self.assertEqual(result["metrics"]["selected_rirs"], 66)
        self.assertTrue(
            result["gates"]["real_waveform_metrics_pending_payload"]
        )

    def test_rejects_furniture_code_literalization(self):
        values = self.values()
        values["snapshots"][-1]["facet_code"] = "020002"
        with self.assertRaisesRegex(ValueError, "facet snapshot"):
            self.run_verify(values)

    def test_rejects_selection_change(self):
        values = self.values()
        values["rir_extraction_manifest"].pop()
        with self.assertRaisesRegex(ValueError, "RIR selection"):
            self.run_verify(values)

    def test_rejects_waveform_overclaim(self):
        values = self.values()
        values["rir_payload"]["waveform_metrics_computed"] = True
        with self.assertRaisesRegex(ValueError, "claim boundary"):
            self.run_verify(values)

    def test_rejects_production_fit(self):
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
