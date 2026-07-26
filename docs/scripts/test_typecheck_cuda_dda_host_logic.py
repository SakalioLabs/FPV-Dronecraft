import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from typecheck_cuda_dda_host_logic import host_translation_unit


class CudaDdaHostTypecheckTest(unittest.TestCase):
    def test_cuda_only_syntax_is_rewritten_for_host_compiler(self):
        source = """
#include <cuda_runtime.h>
__constant__ double table[3];
__device__ int sample() { return 1; }
__global__ void trace_kernel(int value) {}
void run() {
    dim3 grid(1U);
    dim3 block(1U);
    trace_kernel<<<grid, block>>>(sample());
}
"""
        rewritten = host_translation_unit(source)
        self.assertIn('#include "cuda_stub.hpp"', rewritten)
        self.assertIn("inline int sample()", rewritten)
        self.assertIn("inline void trace_kernel_impl(", rewritten)
        self.assertIn("trace_kernel_impl(sample());", rewritten)
        self.assertNotIn("__constant__", rewritten)
        self.assertNotIn("__device__", rewritten)
        self.assertNotIn("__global__", rewritten)
        self.assertNotIn("<<<", rewritten)

    def test_multi_batch_host_logic_is_preserved(self):
        source = """
for (const Batch& batch : batch_plan) {
    ray.segment_offset = rebased_offset;
    compare_batch(batch.first_ray);
}
"""
        self.assertEqual(source, host_translation_unit(source))


if __name__ == "__main__":
    unittest.main()
