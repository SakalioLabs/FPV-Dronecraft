import sys
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from benchmark_cuda_dda_nvrtc_paired_matrix import (
    first_three_prefix_gate,
    summarize_prefix,
)


def gpu_mode(p95, p99, samples, topology):
    return {
        "total_p50_ms": p95 - 0.1,
        "total_p95_ms": p95,
        "total_p99_ms": p99,
        "segment_topology_verified": topology,
        "samples_ms": {"submit_to_result": samples},
    }


class PairedCudaDdaMatrixTest(unittest.TestCase):
    def test_summary_uses_repeat_medians_and_raw_deadline_misses(self):
        trials = []
        for offset in (0.0, 0.1, 0.2):
            trials.append(
                {
                    "cpu": {
                        "batch_p50_ms": 2.0 + offset,
                        "batch_p95_ms": 3.0 + offset,
                        "batch_p99_ms": 4.0 + offset,
                    },
                    "gpu": {
                        "mode_reports": {
                            "full": gpu_mode(
                                2.0 + offset,
                                2.5 + offset,
                                [1.0, 51.0],
                                True,
                            ),
                            "aggregate": gpu_mode(
                                1.0 + offset,
                                1.5 + offset,
                                [0.8, 1.2],
                                False,
                            ),
                        }
                    },
                }
            )

        entry = summarize_prefix(128, trials, 50.0)

        self.assertEqual(3.1, entry["cpu_p95_median_ms"])
        self.assertFalse(entry["modes"]["full"]["passes_deadline_gate"])
        self.assertFalse(entry["modes"]["full"]["passes_all_gates"])
        self.assertTrue(entry["modes"]["aggregate"]["passes_all_gates"])

    def test_crossover_requires_three_consecutive_prefixes(self):
        entries = [
            {
                "rays": rays,
                "modes": {
                    "aggregate": {
                        "passes_all_gates": passed,
                    }
                },
            }
            for rays, passed in (
                (128, False),
                (256, True),
                (512, True),
                (1024, True),
            )
        ]

        self.assertEqual(
            256,
            first_three_prefix_gate(entries, "aggregate"),
        )


if __name__ == "__main__":
    unittest.main()
