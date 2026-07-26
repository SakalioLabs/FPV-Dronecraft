import tempfile
import unittest
from pathlib import Path

import sys

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from verify_cuda_dda_source_contract import (  # noqa: E402
    REQUIRED_CMAKE_TOKENS,
    REQUIRED_CUDA_TOKENS,
    REQUIRED_NVRTC_KERNEL_TOKENS,
    REQUIRED_NVRTC_RUNNER_TOKENS,
    REQUIRED_NVRTC_BENCHMARK_TOKENS,
    REQUIRED_NVRTC_MATRIX_TOKENS,
    REQUIRED_CPU_PREFIX_TOKENS,
    REQUIRED_CPU_MATRIX_TOKENS,
    verify_tokens,
)


class CudaDdaSourceContractTest(unittest.TestCase):
    def test_repository_sources_contain_every_required_contract(self):
        root = SCRIPT_DIR.parents[1]
        self.assertEqual(
            [],
            verify_tokens(
                root / "native/cuda-dda/src/dda_cuda.cu",
                REQUIRED_CUDA_TOKENS,
            ),
        )
        self.assertEqual(
            [],
            verify_tokens(
                root / "native/cuda-dda/CMakeLists.txt",
                REQUIRED_CMAKE_TOKENS,
            ),
        )
        self.assertEqual(
            [],
            verify_tokens(
                root / "native/cuda-dda/src/dda_nvrtc_kernel.cu",
                REQUIRED_NVRTC_KERNEL_TOKENS,
            ),
        )
        self.assertEqual(
            [],
            verify_tokens(
                root / "docs/scripts/run_cuda_dda_nvrtc.py",
                REQUIRED_NVRTC_RUNNER_TOKENS,
            ),
        )
        self.assertEqual(
            [],
            verify_tokens(
                root / "docs/scripts/benchmark_cuda_dda_nvrtc_corpus.py",
                REQUIRED_NVRTC_BENCHMARK_TOKENS,
            ),
        )
        self.assertEqual(
            [],
            verify_tokens(
                root
                / "docs/scripts/benchmark_cuda_dda_nvrtc_scaling_matrix.py",
                REQUIRED_NVRTC_MATRIX_TOKENS,
            ),
        )
        self.assertEqual(
            [],
            verify_tokens(
                root / "native/cuda-dda/src/dda_cpu_reference.cpp",
                REQUIRED_CPU_PREFIX_TOKENS,
            ),
        )
        self.assertEqual(
            [],
            verify_tokens(
                root / "docs/scripts/benchmark_cuda_dda_cpu_prefix_matrix.py",
                REQUIRED_CPU_MATRIX_TOKENS,
            ),
        )

    def test_missing_contract_is_reported_by_name(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "empty.cu"
            path.write_text("// deliberately incomplete\n", encoding="utf-8")
            self.assertEqual(
                list(REQUIRED_CUDA_TOKENS),
                verify_tokens(path, REQUIRED_CUDA_TOKENS),
            )


if __name__ == "__main__":
    unittest.main()
