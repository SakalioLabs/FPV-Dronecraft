import copy
import unittest

import numpy as np

from analyze_air_rir_reference import (
    direct_arrival_index,
    direct_to_reverberant_ratio_db,
    fit_decay,
    validate_report,
)


class AirRirReferenceAnalysisTest(unittest.TestCase):
    def test_t20_recovers_synthetic_exponential_decay(self):
        sample_rate = 48_000
        target_rt60 = 0.75
        delay_samples = 480
        time = np.arange(sample_rate * 2, dtype=np.float64) / sample_rate
        generator = np.random.default_rng(417)
        tail = generator.standard_normal(time.size)
        tail *= np.exp(-3.0 * np.log(10.0) * time / target_rt60)
        signal = np.concatenate((np.zeros(delay_samples), tail))
        fit = fit_decay(signal, sample_rate, -5.0, -25.0)
        self.assertAlmostEqual(
            fit.extrapolated_decay_seconds,
            target_rt60,
            delta=0.025,
        )
        self.assertAlmostEqual(
            fit.extrapolated_from_ir_start_seconds,
            target_rt60 + delay_samples / sample_rate,
            delta=0.035,
        )
        self.assertGreater(fit.r_squared, 0.995)

    def test_direct_window_reports_known_energy_ratio(self):
        sample_rate = 48_000
        signal = np.zeros(4_000, dtype=np.float64)
        signal[480] = 2.0
        signal[700] = 1.0
        signal[900] = 1.0
        self.assertEqual(direct_arrival_index(signal), 480)
        self.assertAlmostEqual(
            direct_to_reverberant_ratio_db(signal, sample_rate),
            10.0 * np.log10(2.0),
            places=12,
        )

    def test_report_validation_preserves_non_release_boundary(self):
        report = {
            "schema_version": 1,
            "status": "valid-reference-diagnostic",
            "measurements": [{}] * 17,
            "gates": {
                "archive_and_embedded_license_pinned": True,
                "all_fullband_t20_fits_r_squared_at_least_0_90": True,
                "median_published_rt60_error_at_most_15_percent": True,
                "every_published_rt60_error_at_most_15_percent": False,
                "room_mean_order_booth_meeting_office_lecture": True,
                "minecraft_release_calibrated": False,
            },
        }
        validate_report(report)
        modified = copy.deepcopy(report)
        modified["gates"]["minecraft_release_calibrated"] = True
        with self.assertRaisesRegex(ValueError, "cannot claim"):
            validate_report(modified)


if __name__ == "__main__":
    unittest.main()
