import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from benchmark_cuda_dda_nvrtc_corpus import build_report, validate_runs


def valid_run(total_p95: float = 400.0) -> dict:
    samples = [100.0, 110.0]
    result = {
        "status": "valid",
        "backend": "cuda-driver-nvrtc",
        "cuda_executed": True,
        "nvrtc_compiled": True,
        "nvcc_compiled": False,
        "warmup_passes": 1,
        "measured_passes": 2,
        "bundle_sha256": "bundle",
        "snapshot_sha256": "snapshot",
        "cells": 10,
        "bundle_rays": 20,
        "rays": 20,
        "batches": 2,
        "peak_batch_rays": 10,
        "peak_batch_segments": 100,
        "peak_segment_bytes": 3200,
        "verified_rays": 20,
        "verified_segments": 150,
        "device": "GPU",
        "compute_capability": "8.6",
        "driver_version": 13030,
        "nvrtc_version": "13.3",
        "architecture": "sm_86",
        "output_mode": "full",
        "samples_ms": {
            "h2d": samples,
            "kernel": samples,
            "d2h": samples,
            "submit_to_result": samples,
        },
    }
    for stage in ("h2d", "kernel", "d2h", "total"):
        result[f"{stage}_p50_ms"] = 100.0
        result[f"{stage}_p95_ms"] = (
            total_p95 if stage == "total" else 110.0
        )
        result[f"{stage}_p99_ms"] = 110.0
    return result


class CudaDdaNvrtcBenchmarkTest(unittest.TestCase):
    def test_repeat_report_preserves_identity_and_crossover_boundary(self):
        report = build_report(
            [valid_run(400.0), valid_run(500.0)],
            warmup=1,
            iterations=2,
            expected_rays=20,
            cpu_reference_p95_ms=900.0,
            gpu_snapshots=[],
            output_mode="full",
        )
        self.assertEqual("valid", report["status"])
        self.assertEqual(450.0, report["metrics"]["total_p95_ms"]["median"])
        self.assertEqual(2.0, report["cpu_p95_to_gpu_submit_p95_ratio"])
        self.assertTrue(report["cuda_executed"])
        self.assertFalse(report["nvcc_compiled"])

    def test_changed_segment_identity_fails_closed(self):
        first = valid_run()
        second = valid_run()
        second["verified_segments"] = 149
        with self.assertRaisesRegex(ValueError, "identity changed"):
            validate_runs(
                [first, second],
                warmup=1,
                iterations=2,
                expected_rays=20,
                output_mode="full",
            )

    def test_missing_raw_sample_fails_closed(self):
        first = valid_run()
        second = valid_run()
        second["samples_ms"]["kernel"] = [1.0]
        with self.assertRaisesRegex(ValueError, "sample count"):
            validate_runs(
                [first, second],
                warmup=1,
                iterations=2,
                expected_rays=20,
                output_mode="full",
            )

    def test_aggregate_output_does_not_claim_cpu_crossover(self):
        first = valid_run()
        second = valid_run()
        first["output_mode"] = "aggregate"
        second["output_mode"] = "aggregate"
        report = build_report(
            [first, second],
            warmup=1,
            iterations=2,
            expected_rays=20,
            cpu_reference_p95_ms=900.0,
            gpu_snapshots=[],
            output_mode="aggregate",
        )
        self.assertFalse(report["cpu_comparison_workload_equivalent"])
        self.assertIsNone(report["cpu_p95_to_gpu_submit_p95_ratio"])
        self.assertFalse(report["retains_every_segment"])


if __name__ == "__main__":
    unittest.main()
