import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from benchmark_cuda_dda_nvrtc_scaling_matrix import (
    entry_from_formal,
    entry_from_run,
)


class CudaDdaNvrtcScalingMatrixTest(unittest.TestCase):
    def test_prefix_entry_reports_both_throughputs(self):
        run = {
            "status": "valid",
            "cuda_executed": True,
            "rays": 100,
            "verified_rays": 100,
            "verified_segments": 200,
            "batches": 2,
            "peak_batch_rays": 60,
            "peak_batch_segments": 300,
            "peak_segment_bytes": 9600,
            "warmup_passes": 3,
            "measured_passes": 10,
            "parity_ms": 1.0,
        }
        for stage in ("h2d", "kernel", "d2h", "total"):
            for quantile in ("p50", "p95", "p99"):
                run[f"{stage}_{quantile}_ms"] = 10.0
        entry = entry_from_run(run)
        self.assertEqual(10_000.0, entry["submit_p95_rays_per_second"])
        self.assertEqual(10_000.0, entry["kernel_p95_rays_per_second"])

    def test_formal_entry_uses_repeat_medians(self):
        formal = {
            "status": "valid",
            "cuda_executed": True,
            "rays_per_pass": 100,
            "verified_segments_per_repeat": 200,
            "batches_per_pass": 2,
            "peak_batch_rays": 60,
            "peak_batch_segments": 300,
            "peak_segment_bytes": 9600,
            "warmup_passes_per_repeat": 5,
            "measured_passes_per_repeat": 30,
            "repeats": 3,
            "metrics": {},
        }
        for stage in ("h2d", "kernel", "d2h", "total"):
            for quantile in ("p50", "p95", "p99"):
                formal["metrics"][f"{stage}_{quantile}_ms"] = {
                    "minimum": 9.0,
                    "median": 10.0,
                    "maximum": 11.0,
                }
        entry = entry_from_formal(formal)
        self.assertEqual("formal-repeat-median", entry["source"])
        self.assertEqual(10.0, entry["metrics"]["total_p95_ms"])

    def test_invalid_prefix_parity_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "verify every ray"):
            entry_from_run(
                {
                    "status": "valid",
                    "cuda_executed": True,
                    "rays": 10,
                    "verified_rays": 9,
                }
            )


if __name__ == "__main__":
    unittest.main()
