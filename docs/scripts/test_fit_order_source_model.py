import importlib.util
import json
import math
import sys
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = (
    Path(__file__).resolve().parents[2]
    / "tools"
    / "acoustics"
    / "fit_order_source_model.py"
)
SPEC = importlib.util.spec_from_file_location("fit_order_source_model", MODULE_PATH)
order_fit = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = order_fit
SPEC.loader.exec_module(order_fit)


HASH = "0123456789abcdef" * 4


class FitOrderSourceModelTest(unittest.TestCase):
    def test_recovers_harmonic_rolloff_motor_ratios_and_holdout(self):
        fitted = order_fit.fit_order_model(
            synthetic_report(),
            HASH,
            reference_rpm=12000.0,
            plane_max_elevation_deg=2.0,
        )

        self.assertTrue(fitted["passes_release_gate"])
        self.assertEqual(4, fitted["source_model"]["blade_pass_harmonics"])
        self.assertAlmostEqual(
            1.2,
            fitted["source_model"]["harmonic_rolloff"],
            places=8,
        )
        self.assertEqual(
            ["shaft", "electrical", "twice_electrical_candidate"],
            fitted["included_motor_kinds"],
        )
        self.assertLess(fitted["maximum_order_spectrum_error_db"], 1e-8)
        self.assertGreater(fitted["source_model"]["electrical_amplitude"], 0.0)
        self.assertGreater(fitted["source_model"]["broadband_energy"], 0.0)

    def test_holdout_order_error_above_three_db_fails_release(self):
        report = synthetic_report()
        validation = next(
            record
            for record in report["recordings"]
            if record["descriptor"]["split"] == "validation"
        )
        validation["rotor_tones"][2]["isolated_level_db_1m"] += 3.2

        fitted = order_fit.fit_order_model(
            report,
            HASH,
            reference_rpm=12000.0,
            plane_max_elevation_deg=2.0,
        )

        self.assertFalse(fitted["passes_release_gate"])
        self.assertGreater(fitted["maximum_order_spectrum_error_db"], 3.0)

    def test_inconsistent_training_motor_detection_is_rejected(self):
        report = synthetic_report()
        training = [
            record
            for record in report["recordings"]
            if record["descriptor"]["split"] == "train"
        ]
        training[0]["motor_tones"][0]["detected"] = False
        training[0]["motor_tones"][0]["isolated_level_db_1m"] = None

        with self.assertRaisesRegex(ValueError, "inconsistent"):
            order_fit.fit_order_model(
                report,
                HASH,
                reference_rpm=12000.0,
                plane_max_elevation_deg=2.0,
            )

    def test_inconsistent_training_higher_bpf_detection_is_rejected(self):
        report = synthetic_report()
        training = [
            item
            for item in report["recordings"]
            if item["descriptor"]["split"] == "train"
        ]
        training[0]["rotor_tones"][4] = tone(
            "blade_pass",
            5,
            training[0]["rotor_tones"][3]["isolated_level_db_1m"] - 2.0,
            order=5,
        )

        with self.assertRaisesRegex(ValueError, "BPF detection"):
            order_fit.fit_order_model(
                report,
                HASH,
                reference_rpm=12000.0,
                plane_max_elevation_deg=2.0,
            )

    def test_new_validation_harmonic_fails_release(self):
        report = synthetic_report()
        validation = next(
            item
            for item in report["recordings"]
            if item["descriptor"]["split"] == "validation"
        )
        validation["rotor_tones"][4] = tone(
            "blade_pass",
            5,
            validation["rotor_tones"][3]["isolated_level_db_1m"] - 2.0,
            order=5,
        )

        fitted = order_fit.fit_order_model(
            report,
            HASH,
            reference_rpm=12000.0,
            plane_max_elevation_deg=2.0,
        )

        self.assertFalse(fitted["passes_release_gate"])
        self.assertEqual(120.0, fitted["maximum_order_spectrum_error_db"])

    def test_requires_analyzed_candidate_above_fitted_harmonics(self):
        report = synthetic_report()
        for record in report["recordings"]:
            fifth = record["rotor_tones"][4]
            fifth["detected"] = True
            fifth["isolated_level_db_1m"] = (
                record["rotor_tones"][0]["isolated_level_db_1m"]
                - 1.2 * 20.0 * math.log10(5)
            )

        with self.assertRaisesRegex(ValueError, "candidate above"):
            order_fit.fit_order_model(
                report,
                HASH,
                reference_rpm=12000.0,
                plane_max_elevation_deg=2.0,
            )

    def test_run_is_byte_deterministic_and_binds_analysis_report_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report_path = root / "analysis-report.json"
            report_path.write_text(
                json.dumps(synthetic_report(), sort_keys=True),
                encoding="utf-8",
            )
            output = root / "order-model.json"

            self.assertEqual(
                0,
                order_fit.run(report_path, 12000.0, 2.0, output),
            )
            first = output.read_bytes()
            self.assertEqual(
                0,
                order_fit.run(report_path, 12000.0, 2.0, output),
            )

            self.assertEqual(first, output.read_bytes())
            fitted = json.loads(first)
            self.assertEqual(
                order_fit.sha256_file(report_path),
                fitted["analysis_report_sha256"],
            )


def synthetic_report():
    records = []
    serial = 0
    for split, rpms in (
        ("train", (8000.0, 12000.0, 16000.0)),
        ("validation", (10000.0, 14000.0)),
    ):
        for rpm in rpms:
            log_ratio = math.log(rpm / 8000.0, 2.0)
            fundamental = 80.0 + 6.0 * log_ratio
            electrical = 75.0 + 3.0 * log_ratio
            rotor_tones = [
                tone(
                    "blade_pass",
                    harmonic,
                    fundamental - 1.2 * 20.0 * math.log10(harmonic),
                    order=harmonic,
                )
                for harmonic in range(1, 5)
            ]
            rotor_tones.append(
                {
                    **tone(
                        "blade_pass",
                        5,
                        fundamental - 1.2 * 20.0 * math.log10(5),
                        order=5,
                    ),
                    "detected": False,
                    "isolated_level_db_1m": None,
                }
            )
            motor_tones = [
                tone("shaft", 1, electrical - 6.0, order=1),
                tone("electrical", 1, electrical, order=2),
                tone(
                    "twice_electrical_candidate",
                    1,
                    electrical - 12.0,
                    order=3,
                ),
            ]
            records.append(
                {
                    "recording_id": f"{split}-recording-{serial}",
                    "maneuver_id": f"{split}-maneuver-{serial}",
                    "rotor_tones": rotor_tones,
                    "motor_tones": motor_tones,
                    "descriptor": {
                        "recording_id": f"{split}-recording-{serial}",
                        "maneuver_id": f"{split}-maneuver-{serial}",
                        "split": split,
                        "rpm": rpm,
                        "elevation_deg": 0.0,
                        "rotor_tonal_db": fundamental,
                        "motor_tonal_db": electrical,
                        "broadband_low_db": 70.0 + 4.0 * log_ratio,
                        "broadband_mid_db": 72.0 + 4.0 * log_ratio,
                        "broadband_high_db": 68.0 + 4.0 * log_ratio,
                    },
                }
            )
            serial += 1
    return {
        "schema_version": 1,
        "recordings": records,
    }


def tone(kind, center_multiplier, level, order):
    return {
        "kind": kind,
        "order": order,
        "center_hz": float(center_multiplier),
        "detected": True,
        "background_snr_db": 30.0,
        "local_prominence_db": 20.0,
        "local_floor_level_db_1m": level - 20.0,
        "isolated_level_db_1m": level,
    }


if __name__ == "__main__":
    unittest.main()
