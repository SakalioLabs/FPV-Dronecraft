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
    / "fit_acoustic_profile.py"
)
SPEC = importlib.util.spec_from_file_location("fit_acoustic_profile", MODULE_PATH)
fit = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = fit
SPEC.loader.exec_module(fit)


HASH = "0123456789abcdef" * 4
ANALYSIS_HASH = "fedcba9876543210" * 4


class AcousticProfileFitTest(unittest.TestCase):
    def test_recovers_operating_points_and_directivity_with_unseen_holdouts(self):
        profile, report = fit.fit_profile(
            synthetic_rows(),
            metadata(HASH),
            HASH,
            order_model(),
        )

        self.assertTrue(report["passes_release_gate"])
        self.assertLess(report["validation"]["maximum_unseen_rpm_error_db"], 1e-7)
        self.assertLess(report["validation"]["maximum_unseen_angle_error_db"], 1e-7)
        self.assertEqual(
            [8000.0, 12000.0, 16000.0],
            [item["rpm"] for item in profile["source_model"]["operating_points"]],
        )
        self.assertAlmostEqual(
            -4.0,
            profile["directivity"]["low"]["c2_db"],
            places=7,
        )
        self.assertAlmostEqual(
            1.0,
            profile["directivity"]["low"]["c4_db"],
            places=7,
        )
        self.assertEqual(2, profile["validation"]["unseen_rpm_samples"])
        self.assertEqual(4, profile["validation"]["unseen_angle_samples"])

    def test_validation_error_above_three_db_blocks_profile_write(self):
        rows = synthetic_rows()
        bad = rows[-1]
        rows[-1] = fit.Measurement(
            **{
                **bad.__dict__,
                "broadband_high_db": bad.broadband_high_db + 3.1,
            }
        )

        _, report = fit.fit_profile(rows, metadata(HASH), HASH, order_model())

        self.assertFalse(report["passes_release_gate"])
        self.assertGreater(
            report["validation"]["maximum_unseen_angle_error_db"],
            3.0,
        )

    def test_run_is_byte_deterministic_and_writes_release_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            csv_path = root / "measurements.csv"
            write_csv(csv_path, synthetic_rows())
            csv_hash = fit.sha256_file(csv_path)
            metadata_path = root / "metadata.json"
            metadata_path.write_text(
                json.dumps(metadata(csv_hash), indent=2),
                encoding="utf-8",
            )
            order_model_path = root / "order-model.json"
            order_model_path.write_text(
                json.dumps(order_model(), indent=2),
                encoding="utf-8",
            )
            profile_path = root / "profile.json"
            report_path = root / "report.json"

            self.assertEqual(
                0,
                fit.run(
                    csv_path,
                    metadata_path,
                    order_model_path,
                    profile_path,
                    report_path,
                ),
            )
            first_profile = profile_path.read_bytes()
            first_report = report_path.read_bytes()
            self.assertEqual(
                0,
                fit.run(
                    csv_path,
                    metadata_path,
                    order_model_path,
                    profile_path,
                    report_path,
                ),
            )

            self.assertEqual(first_profile, profile_path.read_bytes())
            self.assertEqual(first_report, report_path.read_bytes())
            report = json.loads(first_report)
            self.assertTrue(report["profile_written"])
            self.assertEqual(csv_hash, report["measurement_csv_sha256"])
            self.assertEqual(
                fit.sha256_bytes(first_profile),
                report["profile_sha256"],
            )

    def test_failed_release_gate_writes_report_but_not_profile(self):
        rows = synthetic_rows()
        bad = rows[-1]
        rows[-1] = fit.Measurement(
            **{
                **bad.__dict__,
                "broadband_high_db": bad.broadband_high_db + 3.1,
            }
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            csv_path = root / "measurements.csv"
            write_csv(csv_path, rows)
            csv_hash = fit.sha256_file(csv_path)
            metadata_path = root / "metadata.json"
            metadata_path.write_text(
                json.dumps(metadata(csv_hash)),
                encoding="utf-8",
            )
            order_model_path = root / "order-model.json"
            order_model_path.write_text(
                json.dumps(order_model()),
                encoding="utf-8",
            )
            profile_path = root / "profile.json"
            report_path = root / "report.json"

            self.assertEqual(
                1,
                fit.run(
                    csv_path,
                    metadata_path,
                    order_model_path,
                    profile_path,
                    report_path,
                ),
            )

            self.assertFalse(profile_path.exists())
            report = json.loads(report_path.read_text(encoding="utf-8"))
            self.assertFalse(report["profile_written"])
            self.assertFalse(report["passes_release_gate"])

    def test_train_and_validation_cannot_share_recording_or_maneuver(self):
        rows = synthetic_rows()
        validation_index = next(
            index for index, row in enumerate(rows) if row.split == "validation"
        )
        original = rows[validation_index]
        rows[validation_index] = fit.Measurement(
            **{
                **original.__dict__,
                "recording_id": rows[0].recording_id,
            }
        )

        with self.assertRaisesRegex(ValueError, "recording_id"):
            fit.validate_independent_splits(rows)

    def test_evidence_must_bind_exact_measurement_csv_hash(self):
        with self.assertRaisesRegex(ValueError, "measurement CSV"):
            fit.fit_profile(
                synthetic_rows(),
                metadata("f" * 64),
                HASH,
                order_model(),
            )

    def test_order_model_must_bind_analysis_evidence_and_pass_holdout(self):
        unbound = metadata(HASH)
        unbound["evidence"] = unbound["evidence"][:1]
        with self.assertRaisesRegex(ValueError, "analysis report"):
            fit.fit_profile(synthetic_rows(), unbound, HASH, order_model())

        failed = order_model()
        failed["passes_release_gate"] = False
        with self.assertRaisesRegex(ValueError, "release gate"):
            fit.fit_profile(synthetic_rows(), metadata(HASH), HASH, failed)


def synthetic_rows():
    rows = []
    train_rpm = (8000.0, 12000.0, 16000.0)
    validation_rpm = (10000.0, 14000.0)
    coefficients = {
        "low": (-4.0, 1.0),
        "mid": (2.0, -3.0),
        "high": (-8.0, 3.0),
    }

    def make_row(split, rpm, angle, serial):
        log_ratio = math.log(rpm / 8000.0, 2.0)
        mu2 = math.sin(math.radians(angle)) ** 2

        def directed(base, band):
            c2, c4 = coefficients[band]
            return base + 4.0 * log_ratio + c2 * mu2 + c4 * mu2**2

        return fit.Measurement(
            recording_id=f"{split}-recording-{serial}",
            maneuver_id=f"{split}-maneuver-{serial}",
            split=split,
            rpm=rpm,
            elevation_deg=angle,
            rotor_tonal_db=80.0 + 6.0 * log_ratio,
            motor_tonal_db=75.0 + 3.0 * log_ratio,
            broadband_low_db=directed(70.0, "low"),
            broadband_mid_db=directed(72.0, "mid"),
            broadband_high_db=directed(68.0, "high"),
        )

    serial = 0
    for rpm in train_rpm:
        for angle in (0.0, 30.0, 60.0, 90.0):
            rows.append(make_row("train", rpm, angle, serial))
            serial += 1
    for rpm in validation_rpm:
        for angle in (0.0, 45.0, 75.0):
            rows.append(make_row("validation", rpm, angle, serial))
            serial += 1
    return rows


def metadata(csv_hash):
    return {
        "schema_version": 1,
        "id": "test:synthetic_five_inch",
        "key": {
            "airframe_preset": "racing_quad",
            "rotor_count": 4,
            "blade_count": 3,
            "rotor_radius_mm": 64,
            "motor_pole_pairs": 7,
        },
        "calibration": {
            "measured": True,
            "replaces_legacy_rpm_volume": True,
            "motor_playback_gain_db": -3.0,
            "propeller_playback_gain_db": -1.0,
        },
        "evidence": [
            {
                "citation": "Synthetic unit-test truth",
                "source": "https://example.invalid/synthetic",
                "license": "Test-only",
                "measurement_conditions": "Exact analytic source at one metre",
                "sha256": csv_hash,
            },
            {
                "citation": "Synthetic analyzer report",
                "source": "https://example.invalid/synthetic-analysis",
                "license": "Test-only",
                "measurement_conditions": "Independent order-spectrum holdout",
                "sha256": ANALYSIS_HASH,
            }
        ],
        "fit_settings": {
            "reference_rpm": 12000,
            "plane_max_elevation_deg": 2.0,
            "low_anchor_hz": 150,
            "mid_anchor_hz": 1000,
            "high_anchor_hz": 8000,
        },
    }


def order_model():
    return {
        "schema_version": 1,
        "analysis_report_sha256": ANALYSIS_HASH,
        "reference_rpm": 12000.0,
        "plane_max_elevation_deg": 2.0,
        "source_model": {
            "blade_pass_harmonics": 12,
            "harmonic_rolloff": 1.15,
            "shaft_amplitude": 0.05,
            "blade_pass_amplitude": 0.18,
            "electrical_amplitude": 0.035,
            "cogging_candidate_amplitude": 0.012,
            "broadband_energy": 0.08,
        },
        "maximum_analyzed_blade_pass_harmonic": 13,
        "motor_reference_kind": "electrical",
        "included_motor_kinds": [
            "shaft",
            "electrical",
            "twice_electrical_candidate",
        ],
        "training_order_samples": 20,
        "maximum_training_order_error_db": 0.5,
        "order_spectrum_samples": 8,
        "maximum_order_spectrum_error_db": 0.75,
        "release_gate_db": 3.0,
        "passes_release_gate": True,
    }


def write_csv(path, rows):
    import csv

    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fit.CSV_FIELDS)
        writer.writeheader()
        for row in rows:
            writer.writerow(
                {
                    field: getattr(row, field)
                    for field in fit.CSV_FIELDS
                }
            )


if __name__ == "__main__":
    unittest.main()
