#!/usr/bin/env python3
"""Unit tests for D117 reusable DDA batch policy gates."""

from __future__ import annotations

import copy
import unittest

import verify_dda_first_order_batch_benchmark as verifier


class DdaFirstOrderBatchBenchmarkTest(unittest.TestCase):
    def test_accepts_exact_zero_allocation_report(self) -> None:
        verifier.enforce(valid_report(), "abc")

    def test_rejects_one_allocating_window(self) -> None:
        changed = copy.deepcopy(valid_report())
        changed["benchmark"]["allocation_windows_bytes"][3] = 8
        with self.assertRaises(ValueError):
            verifier.enforce(changed, "abc")

    def test_rejects_cuda_overclaim(self) -> None:
        changed = copy.deepcopy(valid_report())
        changed["cuda_executed"] = True
        with self.assertRaises(ValueError):
            verifier.enforce(changed, "abc")


def valid_report() -> dict:
    return {
        "schema_version": 1,
        "status": "valid-dda-first-order-batch-benchmark",
        "source_d117_report_sha256": "abc",
        "parity": {
            "pairs": 6,
            "paths": 42,
            "maximum_length_error_m": 0.0,
            "maximum_reflection_point_error_m": 0.0,
            "visited_cell_mismatches": 0,
            "visibility_mismatches": 0,
        },
        "benchmark": {
            "paths_per_scenario": 7,
            "allocation_scenarios_per_window": 250_000,
            "allocation_windows_bytes": [0, 0, 0, 0, 0],
            "minimum_allocated_bytes": 0,
            "median_allocated_bytes": 0,
            "maximum_allocated_bytes": 0,
            "minimum_allocated_bytes_per_scenario": 0.0,
            "latency_batches": 500,
            "p99_ns_per_scenario": 500.0,
        },
        "gates": {
            "object_reference_parity": True,
            "zero_allocation_hot_path": True,
            "p99_below_50_microseconds": True,
        },
        "captures_audio": False,
        "physical_endpoint_opened": False,
        "cuda_executed": False,
        "release_calibrated": False,
    }


if __name__ == "__main__":
    unittest.main()
