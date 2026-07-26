import copy
import unittest

from analyze_spectral_reference import analyze_arrays
from compare_spectral_references import (
    compare_reports,
    validate_comparison,
)
from test_analyze_spectral_reference import synthetic_reference


def report_for_power(power):
    _, rpm, frequencies, radius, theta = synthetic_reference()
    return analyze_arrays(
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


class SpectralReferenceComparisonTest(unittest.TestCase):
    def test_pairs_identical_coordinates_and_recovers_uniform_delta(self):
        baseline_power, _, _, _, _ = synthetic_reference()
        candidate_power = baseline_power * 2.0
        comparison = compare_reports(
            baseline=report_for_power(baseline_power),
            candidate=report_for_power(candidate_power),
            baseline_label="baseline",
            candidate_label="candidate",
            relationship="uniform-pressure-power-scale",
            baseline_source={"report_sha256": "0" * 64},
            candidate_source={"report_sha256": "1" * 64},
        )
        expected_delta = 3.010299956639812
        self.assertEqual(len(comparison["paired_measurements"]), 15)
        self.assertAlmostEqual(
            comparison["summary"]["harmonic_level_delta_db"]["1"][
                "mean_db"
            ],
            expected_delta,
            delta=1.0e-9,
        )
        self.assertAlmostEqual(
            comparison["summary"]["bpf_removed_band_delta_db"]["mid"][
                "mean_db"
            ],
            expected_delta,
            delta=1.0e-9,
        )
        self.assertAlmostEqual(
            comparison["summary"]["by_rpm"]["4000.0"][
                "bpf_removed_band_delta_db"
            ]["mid"]["mean_db"],
            expected_delta,
            delta=1.0e-9,
        )
        self.assertTrue(
            all(
                abs(item["c2_delta_db"]) < 1.0e-9
                for item in comparison["directivity_fit_delta"]
            )
        )

    def test_rejects_coordinate_mismatch(self):
        baseline_power, _, _, _, _ = synthetic_reference()
        baseline = report_for_power(baseline_power)
        candidate = copy.deepcopy(baseline)
        candidate["coordinates"]["theta_deg"][0] = 55.0
        with self.assertRaisesRegex(ValueError, "contracts differ"):
            compare_reports(
                baseline=baseline,
                candidate=candidate,
                baseline_label="baseline",
                candidate_label="candidate",
                relationship="invalid",
                baseline_source={},
                candidate_source={},
            )

    def test_comparison_release_constraints_cannot_be_weakened(self):
        baseline_power, _, _, _, _ = synthetic_reference()
        comparison = compare_reports(
            baseline=report_for_power(baseline_power),
            candidate=report_for_power(baseline_power),
            baseline_label="baseline",
            candidate_label="candidate",
            relationship="identity",
            baseline_source={},
            candidate_source={},
        )
        modified = copy.deepcopy(comparison)
        modified["release_constraints"]["pcm_synthesis_allowed"] = True
        with self.assertRaisesRegex(ValueError, "cannot be weakened"):
            validate_comparison(modified)


if __name__ == "__main__":
    unittest.main()
