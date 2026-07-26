#!/usr/bin/env python3
"""Unit tests for D118 confirmatory metric and policy gates."""

from __future__ import annotations

import copy
import unittest

import analyze_dechorate_confirmatory_holdout as analyzer
import verify_dechorate_confirmatory_holdout as verifier


class DechorateConfirmatoryHoldoutTest(unittest.TestCase):
    def test_threshold_boundaries_are_inclusive(self) -> None:
        metrics = {
            "mean_top4_overlap": 0.8,
            "top1_capture_rate": 0.9,
            "rmse_db": 3.0,
        }
        thresholds = {
            "top4_overlap_minimum": 0.8,
            "top1_capture_rate_minimum": 0.9,
            "amplitude_rmse_db_maximum": 3.0,
        }
        self.assertTrue(all(analyzer.gate_metrics(metrics, thresholds).values()))

    def test_enforcer_accepts_pass_or_failure_when_metrics_agree(self) -> None:
        passed = valid_gate_report()
        verifier.enforce(passed)
        failed = copy.deepcopy(passed)
        failed["confirmatory_metrics"]["mean_top4_overlap"] = 0.79
        failed["gates"] = analyzer.gate_metrics(
            failed["confirmatory_metrics"], failed["thresholds"]
        )
        failed["decision"][
            "rank_model_admitted_for_further_integration"
        ] = False
        verifier.enforce(failed)

    def test_enforcer_rejects_metric_gate_tampering(self) -> None:
        changed = valid_gate_report()
        changed["gates"]["rank_gate_passed"] = False
        with self.assertRaises(ValueError):
            verifier.enforce(changed)

    def test_enforcer_rejects_refit(self) -> None:
        changed = valid_gate_report()
        changed["frozen_model"]["refit_performed"] = True
        with self.assertRaises(ValueError):
            verifier.enforce(changed)

    def test_enforcer_rejects_threshold_change(self) -> None:
        changed = valid_gate_report()
        changed["thresholds"]["top4_overlap_minimum"] = 0.79
        with self.assertRaises(ValueError):
            verifier.enforce(changed)

    def test_enforcer_rejects_production_overclaim(self) -> None:
        changed = valid_gate_report()
        changed["decision"]["production_candidate_model_eligible"] = True
        with self.assertRaises(ValueError):
            verifier.enforce(changed)


def valid_gate_report() -> dict:
    metrics = {
        "rows": 990,
        "groups": 165,
        "mean_top4_overlap": 0.85,
        "top1_capture_rate": 0.95,
        "rmse_db": 2.5,
    }
    thresholds = {
        "top4_overlap_minimum": 0.8,
        "top1_capture_rate_minimum": 0.9,
        "amplitude_rmse_db_maximum": 3.0,
    }
    gates = analyzer.gate_metrics(metrics, thresholds)
    return {
        "schema_version": 1,
        "status": "preserved-dechorate-confirmatory-result",
        "channel_audit": {
            "sofa_files": 33,
            "channels_per_file": 5,
            "measured_rirs": 165,
            "unique_waveform_hashes": 165,
            "waveform_hash_overlap_with_d117": 0,
            "metadata_coordinate_bindings": 165,
            "microphone_ids": list(range(15)),
        },
        "unblinding_contract": {
            "drive_ids_discovered_after_preregistration": True,
            "byte_pins_fixed_before_sofa_payload_access": True,
            "frozen_model_loaded_without_refit": True,
            "thresholds_loaded_without_change": True,
            "result_must_be_preserved_on_failure": True,
            "new_model_requires_new_version_and_unseen_data": True,
        },
        "frozen_model": {
            "family": "geometry-interaction",
            "regularization": 0.1,
            "feature_count": 420,
            "frozen_model_sha256": "0" * 64,
            "per_microphone_coefficients": False,
            "refit_performed": False,
        },
        "thresholds": thresholds,
        "confirmatory_metrics": metrics,
        "gates": gates,
        "decision": {
            "confirmatory_result_eligible": True,
            "rank_model_admitted_for_further_integration": True,
            "amplitude_model_admitted": True,
            "production_candidate_model_eligible": False,
            "production_change_required": False,
        },
        "cuda_executed": False,
        "captures_audio": False,
        "physical_endpoint_opened": False,
        "release_calibrated": False,
    }


if __name__ == "__main__":
    unittest.main()
