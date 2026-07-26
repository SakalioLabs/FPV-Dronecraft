import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from benchmark_cuda_dda_cpu_corpus import build_report, validate_runs


def run_fixture(p50: float, p95: float, p99: float) -> dict[str, object]:
    return {
        "status": "valid",
        "backend": "cpu-reference",
        "snapshot_generation": 7,
        "snapshot_sha256": "a" * 64,
        "rays": 100_008,
        "cells": 49_494,
        "warmup_batches": 5,
        "measured_batches": 30,
        "parse_ms": 50.0,
        "expected_verify_ms": 2_500.0,
        "batch_p50_ms": p50,
        "batch_p95_ms": p95,
        "batch_p99_ms": p99,
        "p95_ns_per_ray": p95 * 1.0e6 / 100_008,
        "checksum": 508_781_072.16764802,
    }


class CudaDdaCpuCorpusBenchmarkTest(unittest.TestCase):
    def test_report_preserves_repeat_variability(self):
        runs = [
            run_fixture(800.0, 900.0, 920.0),
            run_fixture(810.0, 950.0, 970.0),
            run_fixture(790.0, 850.0, 880.0),
        ]
        report = build_report(
            runs,
            warmup=5,
            iterations=30,
            cpu_label="fixture-cpu",
        )
        self.assertEqual(3, report["repeats"])
        self.assertEqual(
            {"minimum": 850.0, "median": 900.0, "maximum": 950.0},
            report["batch_p95_ms"],
        )
        self.assertEqual(runs, report["runs"])
        self.assertTrue(report["single_threaded"])
        self.assertTrue(report["retains_every_segment"])
        self.assertFalse(report["cuda_executed"])

    def test_changed_workload_identity_is_rejected(self):
        first = run_fixture(800.0, 900.0, 920.0)
        second = run_fixture(810.0, 950.0, 970.0)
        second["checksum"] = 1.0
        with self.assertRaisesRegex(ValueError, "identity changed"):
            validate_runs([first, second], 5, 30)

    def test_wrong_iteration_contract_is_rejected(self):
        run = run_fixture(800.0, 900.0, 920.0)
        run["measured_batches"] = 29
        with self.assertRaisesRegex(ValueError, "iteration count"):
            validate_runs([run], 5, 30)


if __name__ == "__main__":
    unittest.main()
