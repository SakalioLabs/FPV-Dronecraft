import copy
import unittest

import numpy as np

from analyze_spectral_reference import (
    RELEASE_CONSTRAINTS,
    analyze_arrays,
    validate_arrays,
    validate_report,
)


def synthetic_reference():
    frequencies = np.arange(0.0, 2_003.125, 3.125)
    rpm = np.asarray([4_000.0, 5_000.0, 6_000.0])
    theta = np.asarray([60.0, 30.0, 0.0, -30.0, -60.0])
    power = np.full(
        (frequencies.size, theta.size, rpm.size),
        1.0e-11,
        dtype=np.float64,
    )
    mu = np.abs(np.sin(np.radians(theta)))
    directivity_db = -4.0 * mu**2 - 2.0 * mu**4
    for rpm_index, speed in enumerate(rpm):
        bpf = speed * 2.0 / 60.0
        for angle_index, level in enumerate(directivity_db):
            for harmonic in range(1, 4):
                center = bpf * harmonic
                bin_index = int(np.argmin(np.abs(frequencies - center)))
                tone_power = (
                    1.0e-4
                    * (speed / rpm[0]) ** 4
                    * harmonic**-2
                    * 10.0 ** (level / 10.0)
                )
                power[bin_index, angle_index, rpm_index] += tone_power
    return power, rpm, frequencies, np.asarray([[1.62]]), theta


class SpectralReferenceAnalysisTest(unittest.TestCase):
    def test_extracts_orders_directivity_and_keeps_release_gate_closed(self):
        power, rpm, frequencies, radius, theta = synthetic_reference()
        report = analyze_arrays(
            autopower_pa2=power,
            rpm=rpm,
            frequency_hz=frequencies,
            radius_m=radius,
            theta_deg=theta,
            blade_count=2,
            maximum_harmonics=3,
            tone_half_width_hz=6.25,
            reference_distance_m=1.0,
            dataset_doi="doi:test/synthetic",
            source_file={"sha256": "0" * 64},
        )
        self.assertEqual(report["release_constraints"], RELEASE_CONSTRAINTS)
        self.assertEqual(len(report["measurements"]), 15)
        self.assertIn(
            "bpf_removed_band_levels_db_spl_at_reference_distance",
            report["measurements"][0],
        )
        self.assertEqual(
            len(report["complete_detected_harmonic_directivity"]),
            9,
        )
        first_fit = report["complete_detected_harmonic_directivity"][0]["fit"]
        self.assertAlmostEqual(first_fit["c2_db"], -4.0, delta=0.05)
        self.assertAlmostEqual(first_fit["c4_db"], -2.0, delta=0.05)
        self.assertTrue(first_fit["axis_is_extrapolated"])
        self.assertAlmostEqual(
            first_fit["maximum_measured_axis_cosine"],
            np.sin(np.radians(60.0)),
        )
        self.assertEqual(
            len(report["complete_detected_harmonic_rpm_trends"]),
            3,
        )

    def test_rejects_nonuniform_frequency_axis(self):
        power, rpm, frequencies, radius, theta = synthetic_reference()
        frequencies[10] += 0.1
        with self.assertRaisesRegex(ValueError, "uniformly spaced"):
            validate_arrays(power, rpm, frequencies, radius, theta)

    def test_rejects_shape_mismatch_and_negative_power(self):
        power, rpm, frequencies, radius, theta = synthetic_reference()
        with self.assertRaisesRegex(ValueError, "shape"):
            validate_arrays(power[:-1], rpm, frequencies, radius, theta)
        power[0, 0, 0] = -1.0
        with self.assertRaisesRegex(ValueError, "nonnegative"):
            validate_arrays(power, rpm, frequencies, radius, theta)

    def test_release_constraints_cannot_be_weakened(self):
        power, rpm, frequencies, radius, theta = synthetic_reference()
        report = analyze_arrays(
            autopower_pa2=power,
            rpm=rpm,
            frequency_hz=frequencies,
            radius_m=radius,
            theta_deg=theta,
            blade_count=2,
            maximum_harmonics=3,
            tone_half_width_hz=6.25,
            reference_distance_m=1.0,
            dataset_doi="doi:test/synthetic",
            source_file={"sha256": "0" * 64},
        )
        modified = copy.deepcopy(report)
        modified["release_constraints"]["release_profile_eligible"] = True
        with self.assertRaisesRegex(ValueError, "cannot be weakened"):
            validate_report(modified)


if __name__ == "__main__":
    unittest.main()
