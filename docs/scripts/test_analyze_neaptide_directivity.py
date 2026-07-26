import math
import unittest

import numpy as np

from analyze_neaptide_directivity import (
    axis_cosine_from_elevation,
    fit_even_polynomial,
    welch_band_powers,
)


class NeaptideDirectivityAnalysisTest(unittest.TestCase):
    def test_array_elevation_maps_to_rotor_axis_cosine(self) -> None:
        self.assertAlmostEqual(axis_cosine_from_elevation(90.0), 1.0)
        self.assertAlmostEqual(axis_cosine_from_elevation(0.0), 0.0)
        self.assertAlmostEqual(
            axis_cosine_from_elevation(-3.5),
            math.sin(math.radians(3.5)),
        )

    def test_even_polynomial_fit_recovers_known_coefficients(self) -> None:
        axis_cosines = np.asarray([0.0, 0.1, 0.35, 0.6, 0.8, 0.95, 1.0])
        relative_db = -12.0 * axis_cosines**2 + 3.5 * axis_cosines**4

        fit = fit_even_polynomial(axis_cosines, relative_db)

        self.assertAlmostEqual(fit["c2_db"], -12.0, places=10)
        self.assertAlmostEqual(fit["c4_db"], 3.5, places=10)
        self.assertLess(fit["fit_rmse_db"], 1.0e-10)
        self.assertLess(fit["leave_one_out_rmse_db"], 1.0e-10)
        self.assertTrue(fit["passes_internal_fit_gate"])

    def test_welch_places_tones_in_the_expected_bands(self) -> None:
        sample_rate = 48_000
        time = np.arange(sample_rate, dtype=np.float64) / sample_rate
        for frequency, expected_band in (
            (120.0, "low"),
            (1_000.0, "mid"),
            (8_000.0, "high"),
        ):
            signal = np.sin(2.0 * math.pi * frequency * time)
            powers = welch_band_powers(signal, sample_rate)
            self.assertEqual(max(powers, key=powers.get), expected_band)


if __name__ == "__main__":
    unittest.main()
