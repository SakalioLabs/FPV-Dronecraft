import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from benchmark_cuda_dda_cpu_prefix_matrix import (
    gpu_entries_by_rays,
    summary,
    validate_group,
)


def run(ray_count: int = 10) -> dict:
    return {
        "status": "valid",
        "backend": "cpu-reference",
        "snapshot_generation": 1,
        "snapshot_sha256": "snapshot",
        "cells": 4,
        "bundle_rays": 100,
        "rays": ray_count,
        "checksum": 12.0,
        "warmup_batches": 1,
        "measured_batches": 2,
        "batch_p50_ms": 3.0,
        "batch_p95_ms": 4.0,
        "batch_p99_ms": 5.0,
    }


class CpuPrefixMatrixTest(unittest.TestCase):
    def test_prefix_group_requires_stable_identity(self):
        validate_group(
            [run(), run()],
            ray_limit=10,
            warmup=1,
            iterations=2,
        )

    def test_changed_prefix_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "ray limit changed"):
            validate_group(
                [run(), run(9)],
                ray_limit=10,
                warmup=1,
                iterations=2,
            )

    def test_summary_and_gpu_keying(self):
        second = run()
        second["batch_p95_ms"] = 6.0
        self.assertEqual(
            {"minimum": 4.0, "median": 5.0, "maximum": 6.0},
            summary([run(), second], "batch_p95_ms"),
        )
        self.assertEqual(
            {10: {"rays": 10}, 20: {"rays": 20}},
            gpu_entries_by_rays(
                {"entries": [{"rays": 10}, {"rays": 20}]}
            ),
        )


if __name__ == "__main__":
    unittest.main()
