#!/usr/bin/env python3
"""Unit tests for D117 multichannel identity and policy gates."""

from __future__ import annotations

import copy
import unittest

import numpy as np

import analyze_dechorate_multichannel_generalization as analyzer
import verify_dechorate_multichannel_generalization as verifier


class DechorateMultichannelGeneralizationTest(unittest.TestCase):
    def test_array_channel_identity(self) -> None:
        self.assertEqual(10, analyzer.microphone_id(3, 0))
        self.assertEqual(14, analyzer.microphone_id(3, 4))
        self.assertEqual(29, analyzer.microphone_id(6, 4))

    def test_zero_surface_state_has_zero_model_features(self) -> None:
        source = np.array([1.5, 0.7, 1.2])
        microphone = np.array([2.2, 1.7, 1.3])
        for family in analyzer.MODEL_FAMILIES:
            features = analyzer.feature_vector(
                "000000",
                source,
                microphone,
                "ceiling",
                family,
            )
            np.testing.assert_array_equal(features, np.zeros_like(features))

    def test_ridge_recovers_scaled_synthetic_mapping(self) -> None:
        design = np.array(
            [[1.0, 0.0], [0.0, 2.0], [1.0, 2.0], [2.0, 1.0]]
        )
        target = design @ np.array([2.0, -1.0])
        coefficients, scale = analyzer.fit_ridge(
            design, target, 1.0e-10
        )
        predicted = analyzer.predict(design, coefficients, scale)
        np.testing.assert_allclose(predicted, target, atol=1.0e-9)

    def test_enforcer_rejects_confirmatory_overclaim(self) -> None:
        report = valid_gate_report()
        verifier.enforce(report)
        changed = copy.deepcopy(report)
        changed["decision"]["joint_result_confirmatory"] = True
        with self.assertRaises(ValueError):
            verifier.enforce(changed)

    def test_enforcer_rejects_per_microphone_coefficients(self) -> None:
        report = valid_gate_report()
        changed = copy.deepcopy(report)
        changed["model_screen"]["per_microphone_coefficients"] = True
        with self.assertRaises(ValueError):
            verifier.enforce(changed)


def valid_gate_report() -> dict:
    return {
        "schema_version": 1,
        "status": "valid-dechorate-multichannel-generalization",
        "channel_audit": {
            "sofa_files": 66,
            "channels_per_file": 5,
            "measured_rirs": 330,
            "unique_waveform_hashes": 330,
            "metadata_coordinate_bindings": 330,
            "stale_room_descriptions": 66,
            "direct_shift_max_samples": 8,
        },
        "split_contract": {
            "confirmatory_holdout_eligible": False,
            "contamination_reason": "preliminary aggregate inspection",
            "position_discovery": [10, 12, 14, 16, 18, 20, 22, 24],
            "position_validation": [11, 13, 15, 17, 19, 21, 23],
        },
        "model_screen": {
            "selected_family": "geometry-interaction",
            "selected_regularization": 0.1,
            "selected_feature_count": 420,
            "frozen_model": {
                "coefficients_normalized": [0.0] * 420,
                "feature_scale": [1.0] * 420,
            },
            "frozen_model_sha256": "0" * 64,
            "zero_state_max_abs_prediction_db": 0.0,
            "per_microphone_coefficients": False,
            "production_generalizable": False,
            "partition_metrics": {
                "fit": {"rows": 384},
                "position_validation": {
                    "rows": 336,
                    "mean_top4_overlap": 0.87,
                },
                "room_validation": {"rows": 144},
                "joint_exploratory": {
                    "rows": 126,
                    "mean_top4_overlap": 0.96,
                    "top1_capture_rate": 1.0,
                    "rmse_db": 4.4,
                },
            },
        },
        "decision": {
            "three_hundred_thirty_rirs_admitted": True,
            "position_general_features_admitted_for_further_study": True,
            "joint_result_confirmatory": False,
            "amplitude_rmse_gate_passed": False,
            "production_candidate_model_eligible": False,
            "production_change_required": False,
        },
        "captures_audio": False,
        "physical_endpoint_opened": False,
        "release_calibrated": False,
    }


if __name__ == "__main__":
    unittest.main()
