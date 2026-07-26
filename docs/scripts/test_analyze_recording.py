import importlib.util
import json
import math
import sys
import tempfile
import unittest
import wave
from pathlib import Path

import numpy as np


MODULE_PATH = (
    Path(__file__).resolve().parents[2]
    / "tools"
    / "acoustics"
    / "analyze_recording.py"
)
SPEC = importlib.util.spec_from_file_location("analyze_recording", MODULE_PATH)
analysis = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = analysis
SPEC.loader.exec_module(analysis)


class AnalyzeRecordingTest(unittest.TestCase):
    def test_calibrates_extracts_orders_normalizes_distance_and_is_deterministic(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest_path = make_fixture(root)
            output_csv = root / "descriptors.csv"
            output_report = root / "report.json"

            self.assertEqual(
                0,
                analysis.run(manifest_path, output_csv, output_report),
            )
            first_csv = output_csv.read_bytes()
            first_report = output_report.read_bytes()
            self.assertEqual(
                0,
                analysis.run(manifest_path, output_csv, output_report),
            )

            self.assertEqual(first_csv, output_csv.read_bytes())
            self.assertEqual(first_report, output_report.read_bytes())
            rows = list(
                __import__("csv").DictReader(
                    output_csv.read_text(encoding="utf-8").splitlines()
                )
            )
            self.assertEqual(1, len(rows))
            row = rows[0]
            self.assertAlmostEqual(12000.0, float(row["rpm"]), places=6)
            expected_distance_and_correction = 20.0 * math.log10(2.0) - 1.0
            self.assertAlmostEqual(
                74.0 + expected_distance_and_correction,
                float(row["rotor_tonal_db"]),
                delta=0.20,
            )
            self.assertAlmostEqual(
                67.9794 + expected_distance_and_correction,
                float(row["motor_tonal_db"]),
                delta=0.25,
            )
            for name in (
                "broadband_low_db",
                "broadband_mid_db",
                "broadband_high_db",
            ):
                self.assertTrue(math.isfinite(float(row[name])))
            report = json.loads(first_report)
            self.assertEqual(1, report["descriptor_rows"])
            self.assertEqual(
                analysis.sha256_file(output_csv),
                report["descriptor_csv_sha256"],
            )
            self.assertEqual(
                [600.0, 1200.0],
                report["recordings"][0]["rotor_tone_centers_hz"],
            )
            self.assertEqual(
                [200.0, 1400.0, 2800.0],
                report["recordings"][0]["motor_tone_centers_hz"],
            )
            self.assertEqual(
                [True, False],
                [
                    tone["detected"]
                    for tone in report["recordings"][0]["rotor_tones"]
                ],
            )
            self.assertEqual(
                [False, True, False],
                [
                    tone["detected"]
                    for tone in report["recordings"][0]["motor_tones"]
                ],
            )
            self.assertLess(
                report["recordings"][0]["rpm_coefficient_of_variation"],
                1e-12,
            )

    def test_rejects_unsteady_rpm(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest_path = make_fixture(root, unsteady_rpm=True)

            with self.assertRaisesRegex(ValueError, "RPM coefficient"):
                analysis.run(
                    manifest_path,
                    root / "descriptors.csv",
                    root / "report.json",
                )

    def test_rejects_recording_below_snr_gate(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest_path = make_fixture(root)
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["analysis"]["minimum_snr_db"] = 80.0
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "SNR"):
                analysis.run(
                    manifest_path,
                    root / "descriptors.csv",
                    root / "report.json",
                )

    def test_rejects_peak_above_capture_gate(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest_path = make_fixture(root)
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["analysis"]["maximum_peak_normalized"] = 0.005
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "peak"):
                analysis.run(
                    manifest_path,
                    root / "descriptors.csv",
                    root / "report.json",
                )

    def test_rejects_nonmonotonic_rpm_timestamps(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest_path = make_fixture(root)
            (root / "rpm.csv").write_text(
                "time_s,rpm\n"
                "0.0,12000\n"
                "0.5,12000\n"
                "0.4,12000\n"
                "1.0,12000\n"
                "1.5,12000\n"
                "2.0,12000\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "strictly increasing"):
                analysis.run(
                    manifest_path,
                    root / "descriptors.csv",
                    root / "report.json",
                )

    def test_decodes_signed_24_bit_pcm_endpoints(self):
        values = np.asarray(
            (-8388608, -1, 0, 1, 8388607),
            dtype=np.int32,
        )
        raw = pcm24_bytes(values)

        decoded = analysis.decode_pcm(raw, 3)

        np.testing.assert_allclose(
            decoded,
            values.astype(np.float64) / 8388608.0,
            rtol=0.0,
            atol=0.0,
        )

    def test_rejects_overlapping_rotor_and_motor_windows(self):
        frequencies = np.arange(0.0, 24001.0, 5.0)

        with self.assertRaisesRegex(ValueError, "overlap"):
            analysis.tone_masks(
                frequencies,
                rpm=12000.0,
                blade_count=7,
                motor_pole_pairs=7,
                blade_pass_harmonics=1,
                half_width_hz=40.0,
                sample_rate=48000,
            )

    def test_analyzer_output_passes_independent_profile_holdouts(self):
        from docs.scripts.test_fit_acoustic_profile import (
            metadata,
            synthetic_rows,
        )
        from docs.scripts.test_fit_acoustic_profile import fit as profile_fit
        from docs.scripts.test_fit_order_source_model import order_fit

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest_path = make_profile_fixture(root, synthetic_rows())
            descriptors = root / "descriptors.csv"
            analysis_report = root / "analysis-report.json"

            self.assertEqual(
                0,
                analysis.run(manifest_path, descriptors, analysis_report),
            )
            descriptor_hash = analysis.sha256_file(descriptors)
            analysis_hash = analysis.sha256_file(analysis_report)
            order_model_path = root / "order-model.json"
            self.assertEqual(
                0,
                order_fit.run(
                    analysis_report,
                    12000.0,
                    2.0,
                    order_model_path,
                ),
            )
            profile_metadata = metadata(descriptor_hash)
            profile_metadata["evidence"][1]["sha256"] = analysis_hash
            metadata_path = root / "profile-metadata.json"
            metadata_path.write_text(
                json.dumps(profile_metadata),
                encoding="utf-8",
            )

            profile_path = root / "profile.json"
            fit_report_path = root / "fit-report.json"
            self.assertEqual(
                0,
                profile_fit.run(
                    descriptors,
                    metadata_path,
                    order_model_path,
                    profile_path,
                    fit_report_path,
                ),
            )

            fit_report = json.loads(fit_report_path.read_text(encoding="utf-8"))
            self.assertTrue(fit_report["passes_release_gate"])
            self.assertLess(
                fit_report["validation"]["maximum_unseen_rpm_error_db"],
                0.15,
            )
            self.assertLess(
                fit_report["validation"]["maximum_unseen_angle_error_db"],
                0.15,
            )
            self.assertEqual(
                descriptor_hash,
                fit_report["measurement_csv_sha256"],
            )
            self.assertEqual(
                analysis_hash,
                fit_report["analysis_report_sha256"],
            )
            profile = json.loads(profile_path.read_text(encoding="utf-8"))
            self.assertEqual(4, profile["source_model"]["blade_pass_harmonics"])
            self.assertAlmostEqual(
                1.2,
                profile["source_model"]["harmonic_rolloff"],
                delta=0.02,
            )


def make_fixture(root, unsteady_rpm=False):
    sample_rate = 48000
    duration = 2.0
    time = np.arange(round(sample_rate * duration), dtype=np.float64) / sample_rate
    rng = np.random.default_rng(0x465056)

    calibration = 0.1 * np.sin(2.0 * np.pi * 1000.0 * time)
    background = rng.normal(0.0, 2.0e-5, size=time.size)
    source_noise = rng.normal(0.0, 2.0e-4, size=time.size)
    signal = (
        0.01 * np.sin(2.0 * np.pi * 600.0 * time)
        + 0.005 * np.sin(2.0 * np.pi * 1400.0 * time)
        + source_noise
        + background
    )

    write_wav24(root / "calibration.wav", calibration, sample_rate)
    write_wav24(root / "background.wav", background, sample_rate)
    write_wav24(root / "measurement.wav", signal, sample_rate)
    rpm_values = np.linspace(10800.0, 13200.0, 21) if unsteady_rpm else np.full(21, 12000.0)
    with (root / "rpm.csv").open("w", encoding="utf-8", newline="") as stream:
        stream.write("time_s,rpm\n")
        for timestamp, rpm in zip(np.linspace(0.0, duration, 21), rpm_values):
            stream.write(f"{timestamp:.3f},{rpm:.6f}\n")

    manifest = {
        "schema_version": 1,
        "calibration": {
            "wav": "calibration.wav",
            "channel": 0,
            "level_db_spl": 94.0,
            "frequency_hz": 1000.0,
            "start_s": 0.0,
            "duration_s": duration,
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
        "recordings": [
            {
                "recording_id": "synthetic-recording",
                "maneuver_id": "synthetic-steady",
                "split": "train",
                "wav": "measurement.wav",
                "background_wav": "background.wav",
                "rpm_csv": "rpm.csv",
                "channel": 0,
                "start_s": 0.0,
                "duration_s": duration,
                "background_start_s": 0.0,
                "background_duration_s": duration,
                "elevation_deg": 0.0,
                "microphone_distance_m": 2.0,
                "reference_level_correction_db": -1.0,
                "blade_count": 3,
                "motor_pole_pairs": 7,
                "blade_pass_harmonics": 2,
                "airframe_id": "synthetic-5inch-quad",
                "source_configuration": "full_airframe_bench",
                "motor_id": "synthetic-2207",
                "propeller_id": "synthetic-5x3",
                "microphone_id": "synthetic-reference-mic",
                "signal_chain_id": "synthetic-24bit-chain",
                "thrust_n": 4.0,
                "voltage_v": 16.0,
                "current_a": 8.0,
                "ambient_temperature_c": 20.0,
                "relative_humidity_percent": 50.0,
                "ambient_pressure_kpa": 101.325,
            }
        ],
    }
    manifest_path = root / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    return manifest_path


def make_profile_fixture(root, target_rows):
    sample_rate = 48000
    duration = 1.0
    time = np.arange(sample_rate, dtype=np.float64) / sample_rate
    calibration = 0.1 * np.sin(2.0 * np.pi * 1000.0 * time)
    background = np.random.default_rng(42).normal(
        0.0,
        2.0e-6,
        size=time.size,
    )
    write_wav24(root / "calibration.wav", calibration, sample_rate)
    write_wav24(root / "background.wav", background, sample_rate)

    known_pressure_rms = analysis.REFERENCE_PRESSURE_PA * 10.0 ** (94.0 / 20.0)
    ideal_pa_per_normalized = known_pressure_rms / (0.1 / math.sqrt(2.0))

    def peak_for_level(level_db):
        pressure_rms = analysis.REFERENCE_PRESSURE_PA * 10.0 ** (level_db / 20.0)
        return pressure_rms * math.sqrt(2.0) / ideal_pa_per_normalized

    recordings = []
    for index, target in enumerate(target_rows):
        shaft_hz = target.rpm / 60.0
        signal = background.copy()
        tones = [
            (
                shaft_hz * 3.0 * harmonic,
                target.rotor_tonal_db
                - 20.0 * 1.2 * math.log10(harmonic),
            )
            for harmonic in range(1, 5)
        ]
        tones.extend(
            (
                (shaft_hz, target.motor_tonal_db - 6.0),
                (shaft_hz * 7.0, target.motor_tonal_db),
                (shaft_hz * 14.0, target.motor_tonal_db - 12.0),
            )
        )
        tones.extend(
            (
            (50.0, target.broadband_low_db),
            (2150.0, target.broadband_mid_db),
            (11000.0, target.broadband_high_db),
            )
        )
        for frequency, level in tones:
            signal += peak_for_level(level) * np.sin(
                2.0 * np.pi * frequency * time
            )
        wav_name = f"measurement-{index:02d}.wav"
        rpm_name = f"rpm-{index:02d}.csv"
        write_wav24(root / wav_name, signal, sample_rate)
        with (root / rpm_name).open("w", encoding="utf-8", newline="") as stream:
            stream.write("time_s,rpm\n")
            for timestamp in np.linspace(0.0, duration, 11):
                stream.write(f"{timestamp:.3f},{target.rpm:.9f}\n")
        recordings.append(
            {
                "recording_id": target.recording_id,
                "maneuver_id": target.maneuver_id,
                "split": target.split,
                "wav": wav_name,
                "background_wav": "background.wav",
                "rpm_csv": rpm_name,
                "channel": 0,
                "start_s": 0.0,
                "duration_s": duration,
                "background_start_s": 0.0,
                "background_duration_s": duration,
                "elevation_deg": target.elevation_deg,
                "microphone_distance_m": 1.0,
                "reference_level_correction_db": 0.0,
                "blade_count": 3,
                "motor_pole_pairs": 7,
                "blade_pass_harmonics": 5,
                "airframe_id": "synthetic-5inch-quad",
                "source_configuration": "full_airframe_bench",
                "motor_id": "synthetic-2207",
                "propeller_id": "synthetic-5x3",
                "microphone_id": "synthetic-reference-mic",
                "signal_chain_id": "synthetic-24bit-chain",
                "thrust_n": 4.0,
                "voltage_v": 16.0,
                "current_a": 8.0,
                "ambient_temperature_c": 20.0,
                "relative_humidity_percent": 50.0,
                "ambient_pressure_kpa": 101.325,
            }
        )
    manifest = {
        "schema_version": 1,
        "calibration": {
            "wav": "calibration.wav",
            "channel": 0,
            "level_db_spl": 94.0,
            "frequency_hz": 1000.0,
            "start_s": 0.0,
            "duration_s": duration,
            "tone_half_width_hz": 20.0,
        },
        "analysis": {
            "welch_resolution_hz": 5.0,
            "overlap_fraction": 0.75,
            "tonal_half_width_hz": 40.0,
            "maximum_rpm_cv": 0.025,
            "minimum_snr_db": 10.0,
            "maximum_peak_normalized": 0.99,
            "minimum_tone_prominence_db": 20.0,
        },
        "recordings": recordings,
    }
    manifest_path = root / "profile-recording-manifest.json"
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
    return manifest_path


def write_wav24(path, samples, sample_rate):
    clipped = np.clip(samples, -1.0, 1.0 - 1.0 / 8388608.0)
    integers = np.rint(clipped * 8388608.0).astype(np.int32)
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(3)
        output.setframerate(sample_rate)
        output.writeframes(pcm24_bytes(integers))


def pcm24_bytes(values):
    unsigned = np.asarray(values, dtype=np.int32) & 0xFFFFFF
    packed = np.empty((unsigned.size, 3), dtype=np.uint8)
    packed[:, 0] = unsigned & 0xFF
    packed[:, 1] = (unsigned >> 8) & 0xFF
    packed[:, 2] = (unsigned >> 16) & 0xFF
    return packed.tobytes()


if __name__ == "__main__":
    unittest.main()
