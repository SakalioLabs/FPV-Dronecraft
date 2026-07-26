import tempfile
import unittest
from pathlib import Path

import sys

TOOLS = Path(__file__).resolve().parents[2] / "tools" / "acoustics"
sys.path.insert(0, str(TOOLS))

from plan_capture_session import (  # noqa: E402
    RESULT_FIELDS,
    generate_plan,
    initial_result_rows,
    load_results,
    materialize,
    validate_config,
    write_json_atomic,
    write_results_csv,
)
import analyze_recording as recording_analyzer  # noqa: E402


def configuration():
    return {
        "schema_version": 1,
        "session_id": "synthetic-5inch-session01",
        "hardware": {
            "airframe_id": "synthetic:single-rotor-rig",
            "source_configuration": "single_rotor_bench",
            "motor_id": "synthetic:2207-1750kv",
            "propeller_id": "synthetic:5x4.3x3",
            "blade_count": 3,
            "motor_pole_pairs": 7,
            "blade_pass_harmonics": 8,
            "microphone_id": "synthetic:measurement-mic",
            "signal_chain_id": "synthetic:48khz-24bit",
        },
        "acquisition": {
            "sample_rate_hz": 48000,
            "bit_depth": 24,
            "channel": 0,
            "microphone_distance_m": 1.0,
            "reference_level_correction_db": 0.0,
            "signal_start_s": 1.0,
            "signal_duration_s": 5.0,
            "background_start_s": 1.0,
            "background_duration_s": 5.0,
        },
        "calibration": {
            "wav": "raw/calibration.wav",
            "channel": 0,
            "level_db_spl": 94.0,
            "frequency_hz": 1000.0,
            "start_s": 1.0,
            "duration_s": 10.0,
            "tone_half_width_hz": 20.0,
        },
        "analysis": {
            "welch_resolution_hz": 5.0,
            "overlap_fraction": 0.75,
            "tonal_half_width_hz": 40.0,
            "maximum_rpm_cv": 0.025,
            "minimum_snr_db": 10.0,
            "maximum_peak_normalized": 0.99,
            "minimum_tone_prominence_db": 6.0,
        },
        "environment_defaults": {
            "voltage_v": 16.0,
            "ambient_temperature_c": 20.0,
            "relative_humidity_percent": 50.0,
            "ambient_pressure_kpa": 101.325,
        },
        "matrix": {
            "training_plane_rpm": [8000, 12000, 16000],
            "validation_plane_rpm": [10000, 14000],
            "directivity_reference_rpm": 12000,
            "training_elevation_deg": [-90, -60, -30, 30, 60, 90],
            "validation_elevation_deg": [-75, -45, -15, 15, 45, 75],
            "training_takes": 2,
            "validation_takes": 1,
        },
    }


class CaptureSessionPlanTest(unittest.TestCase):
    def test_generates_independent_rpm_angle_and_take_matrix(self):
        plan = generate_plan(configuration())
        self.assertEqual(plan["capture_count"], 26)
        self.assertEqual(len(plan["background_files"]), 13)
        train = [
            capture for capture in plan["captures"]
            if capture["split"] == "train"
        ]
        validation = [
            capture for capture in plan["captures"]
            if capture["split"] == "validation"
        ]
        self.assertEqual(len(train), 18)
        self.assertEqual(len(validation), 8)
        self.assertEqual(
            len({capture["capture_id"] for capture in plan["captures"]}),
            26,
        )

    def test_rejects_rpm_and_axis_angle_holdout_leakage(self):
        rpm_leak = configuration()
        rpm_leak["matrix"]["validation_plane_rpm"][0] = 12000
        with self.assertRaisesRegex(ValueError, "RPM must be unseen"):
            validate_config(rpm_leak)

        angle_leak = configuration()
        angle_leak["matrix"]["validation_elevation_deg"] = [
            -75,
            -45,
            -30,
            30,
            45,
            75,
        ]
        with self.assertRaisesRegex(ValueError, "axis cosines"):
            validate_config(angle_leak)

    def test_rejects_nyquist_guard_violation(self):
        invalid = configuration()
        invalid["hardware"]["blade_pass_harmonics"] = 64
        with self.assertRaisesRegex(ValueError, "Nyquist guard"):
            validate_config(invalid)

    def test_rejects_planned_tonal_window_overlap(self):
        invalid = configuration()
        invalid["hardware"]["blade_count"] = 2
        invalid["hardware"]["blade_pass_harmonics"] = 7
        with self.assertRaisesRegex(ValueError, "tonal windows overlap"):
            validate_config(invalid)

    def test_materializes_only_complete_existing_capture_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            plan_path = root / "capture-plan.json"
            results_path = root / "capture-results.csv"
            manifest_path = root / "recording-manifest.json"
            plan = generate_plan(configuration())
            write_json_atomic(plan_path, plan)
            rows = initial_result_rows(plan)
            write_results_csv(results_path, rows)
            raw = root / "raw"
            raw.mkdir()
            (root / "raw" / "calibration.wav").write_bytes(b"calibration")
            for background in plan["background_files"]:
                (root / background).write_bytes(b"background")
            with self.assertRaisesRegex(ValueError, "status must be captured"):
                materialize(
                    plan,
                    plan_path,
                    load_results(results_path),
                    results_path,
                    manifest_path,
                )

            for row in rows:
                row["status"] = "captured"
                row["thrust_n"] = "4.5"
                row["current_a"] = "8.0"
                (root / row["wav"]).write_bytes(b"wav")
                (root / row["rpm_csv"]).write_text(
                    "time_s,rpm\n0,12000\n1,12000\n",
                    encoding="utf-8",
                )
            write_results_csv(results_path, rows)
            manifest, report = materialize(
                plan,
                plan_path,
                load_results(results_path),
                results_path,
                manifest_path,
            )
            self.assertEqual(len(manifest["recordings"]), 26)
            self.assertEqual(report["training_rows"], 18)
            self.assertEqual(report["validation_rows"], 8)
            first = manifest["recordings"][0]
            self.assertEqual(first["airframe_id"], "synthetic:single-rotor-rig")
            self.assertEqual(
                first["source_configuration"],
                "single_rotor_bench",
            )
            self.assertEqual(
                set(first),
                set(recording_analyzer.RECORDING_FIELDS),
            )
            self.assertEqual(
                set(initial_result_rows(plan)[0]),
                set(RESULT_FIELDS),
            )


if __name__ == "__main__":
    unittest.main()
