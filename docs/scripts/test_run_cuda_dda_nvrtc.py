import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from run_cuda_dda_nvrtc import (
    CELL_DTYPE,
    RAY_DTYPE,
    RESULT_DTYPE,
    SEGMENT_DTYPE,
    plan_batches,
)


class CudaDdaNvrtcTest(unittest.TestCase):
    def test_explicit_host_layouts_match_cuda_abi(self):
        self.assertEqual(24, CELL_DTYPE.itemsize)
        self.assertEqual(64, RAY_DTYPE.itemsize)
        self.assertEqual(32, SEGMENT_DTYPE.itemsize)
        self.assertEqual(72, RESULT_DTYPE.itemsize)
        self.assertEqual(16, CELL_DTYPE.fields["fill_fraction"][1])
        self.assertEqual(56, RAY_DTYPE.fields["segment_offset"][1])
        self.assertEqual(24, SEGMENT_DTYPE.fields["fill_fraction"][1])
        self.assertEqual(24, RESULT_DTYPE.fields["loss"][1])
        self.assertEqual(48, RESULT_DTYPE.fields["gain"][1])

    def test_batch_plan_matches_bounded_contiguous_contract(self):
        batches = plan_batches(
            [300, 400, 500, 100, 100],
            maximum_rays=3,
            maximum_segments=1_000,
        )
        self.assertEqual(
            [(0, 2, 700), (2, 3, 700)],
            [
                (batch.first_ray, batch.ray_count, batch.segment_count)
                for batch in batches
            ],
        )

    def test_single_ray_above_segment_limit_fails_closed(self):
        with self.assertRaisesRegex(
            ValueError,
            "single ray exceeds CUDA batch segment limit",
        ):
            plan_batches([1_001], maximum_rays=3, maximum_segments=1_000)


if __name__ == "__main__":
    unittest.main()
