#!/usr/bin/env python3
"""Unit tests for D116 CPU-DDA analysis and policy gates."""

from __future__ import annotations

import copy
import unittest

import numpy as np

import analyze_dechorate_cpu_dda_experiment as analyzer
import verify_dechorate_cpu_dda_experiment as verifier


class DechorateCpuDdaExperimentTest(unittest.TestCase):
    def test_effective_furniture_code(self) -> None:
        self.assertEqual("010001", analyzer.effective_code("020002"))
        self.assertEqual("011110", analyzer.effective_code("011110"))

    def test_rank_tie_uses_facet_order(self) -> None:
        ranked = analyzer.ranked_facets(
            {facet: 0.0 for facet in analyzer.FACETS}
        )
        self.assertEqual(list(analyzer.FACETS[:4]), ranked)

    def test_controlled_fit_recovers_additive_surface_response(self) -> None:
        observations = {}
        expected = np.array([0.25, 1.0, 2.0, 3.0, 4.0, 5.0])
        for room in analyzer.DISCOVERY:
            value = float(analyzer.feature_vector(room) @ expected)
            for microphone in (10, 19, 20):
                for facet in analyzer.FACETS:
                    observations[(room, microphone, facet)] = value
        coefficients = analyzer.fit_surface_response(observations)
        for vector in coefficients.values():
            np.testing.assert_allclose(vector, expected, atol=1.0e-10)

    def test_enforcer_rejects_production_generalization_overclaim(self) -> None:
        report = valid_gate_report()
        verifier.enforce(report)
        changed = copy.deepcopy(report)
        changed["controlled_surface_response"][
            "production_generalizable"
        ] = True
        with self.assertRaises(ValueError):
            verifier.enforce(changed)

    def test_enforcer_rejects_cuda_overclaim(self) -> None:
        report = valid_gate_report()
        changed = copy.deepcopy(report)
        changed["decision"]["cuda_executed"] = True
        with self.assertRaises(ValueError):
            verifier.enforce(changed)


def valid_gate_report() -> dict:
    return {
        "schema_version": 1,
        "status": "valid-dechorate-cpu-dda-analysis",
        "coordinate_binding": {"verified_scenarios": 66},
        "java_cpu_dda": {
            "paths_recomputed": 924,
            "all_topology_visible": True,
        },
        "geometry_timing": {
            "annotation_paths": 21,
            "physical_absolute_residual_max_samples": 4.4,
            "voxel_absolute_residual_max_samples": 84.0,
            "java_vs_d115_max_absolute_difference_samples": 1.0e-12,
        },
        "unfitted_candidate_heuristic": {
            "holdout": {
                "mean_top4_overlap": 0.69,
                "random_expected_top4_overlap": 2.0 / 3.0,
            }
        },
        "controlled_surface_response": {
            "production_generalizable": False,
            "metrics": {
                "holdout": {
                    "mean_top4_overlap": 1.0,
                    "top1_captured": 9,
                }
            },
        },
        "runtime_benchmark": {
            "p99_ns_per_scenario": 3_000.0,
            "allocation_supported": True,
            "p95_allocated_bytes_per_scenario": 2_000.0,
        },
        "decision": {
            "continuous_boundary_timing_admitted": True,
            "one_metre_boundary_timing_rejected": True,
            "real_java_cpu_dda_topology_admitted": True,
            "latency_research_gate_passed": True,
            "allocation_free_gate_passed": False,
            "unfitted_material_distance_ranking_admitted": False,
            "controlled_surface_response_holdout_rank_admitted": True,
            "controlled_coefficients_production_eligible": False,
            "cuda_executed": False,
            "production_change_required": False,
        },
        "captures_audio": False,
        "physical_endpoint_opened": False,
        "release_calibrated": False,
    }


if __name__ == "__main__":
    unittest.main()
