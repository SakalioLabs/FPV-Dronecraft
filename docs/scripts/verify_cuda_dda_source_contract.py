#!/usr/bin/env python3
"""Fail closed when the uncompiled CUDA research source loses required gates."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


REQUIRED_CUDA_TOKENS = {
    "fp64_kernel": "__global__ void trace_kernel",
    "simultaneous_tie_policy": "fabs(t_max[axis] - crossing) <= epsilon",
    "tie_epsilon_absolute": "1.0e-12",
    "tie_epsilon_relative": "fabs(crossing) * 1.0e-12",
    "sparse_snapshot_lookup": "__device__ DeviceCell device_sample_cell",
    "segment_topology_output": "struct DeviceSegment",
    "visited_segment_comparison": "CUDA segment mismatch for ray",
    "first_material_comparison": "actual.first_material",
    "three_band_tolerance": "loss_error > 1.0e-5 || gain_error > 1.0e-5",
    "resident_cell_upload": "upload resident cells",
    "h2d_timing": 'stage_json("h2d"',
    "kernel_timing": 'stage_json("kernel"',
    "d2h_timing": 'stage_json("d2h"',
    "end_to_end_timing": 'stage_json("submit_to_result"',
    "cpu_oracle_reuse": "trace_ray(",
    "strict_bundle_reader_reuse": "verify(read_file(arguments.bundle))",
    "expected_sidecar_gate": "verify_expected_results(",
    "shared_bounded_batch_planner": "mcfpv::cuda_dda::plan_batches(",
    "bounded_ray_limit": "maximum_rays_per_batch",
    "bounded_segment_limit": "maximum_segments_per_batch",
    "multi_batch_execution_loop": "for (const mcfpv::cuda_dda::Batch& batch : batch_plan)",
    "per_batch_rebased_offsets": "ray.segment_offset = rebased_offset;",
    "batch_local_parity_offsets": (
        "rays[static_cast<std::size_t>(offset)]"
    ),
    "peak_bounded_device_allocation": "peak_batch_segments,",
    "checked_allocation_bytes": "mcfpv::cuda_dda::checked_bytes(",
    "per_batch_parity": "compare_batch(",
    "batch_plan_reported": '",\\"batch_count\\":"',
    "segment_record_size_contract": "sizeof(DeviceSegment) == 32U",
}

REQUIRED_CMAKE_TOKENS = {
    "optional_cuda_compiler": "check_language(CUDA)",
    "cuda_target_guard": "MCFPV_ENABLE_CUDA AND CMAKE_CUDA_COMPILER",
    "cpu_reference_always_built": "mcfpv_dda_cpu_reference",
    "cuda_target": "mcfpv_dda_cuda",
    "java_fixture_cpu_test": "mcfpv_dda_cpu_reference_java_fixture",
    "java_fixture_cuda_test": "mcfpv_dda_cuda_java_fixture",
    "host_batch_plan_target": "mcfpv_dda_batch_plan",
    "host_batch_plan_test": "mcfpv_dda_batch_plan_self_test",
}

REQUIRED_NVRTC_KERNEL_TOKENS = {
    "device_entry_point": 'extern "C" __global__ void trace_kernel',
    "simultaneous_tie_policy": "fabs(t_max[axis] - crossing) <= epsilon",
    "tie_epsilon_absolute": "1.0e-12",
    "tie_epsilon_relative": "fabs(crossing) * 1.0e-12",
    "sparse_snapshot_lookup": "__device__ DeviceCell sample_cell",
    "segment_topology_output": "struct DeviceSegment",
    "first_material_output": "result.first_material = packed",
    "three_band_accumulation": "result.loss[band] +=",
    "truncation_flag": "result.flags = TRUNCATED_FLAG",
}

REQUIRED_NVRTC_RUNNER_TOKENS = {
    "strict_bundle_reader": "require_complete=True",
    "bounded_batch_planner": "def plan_batches(",
    "explicit_cell_layout": "CELL_DTYPE = np.dtype(",
    "explicit_ray_layout": "RAY_DTYPE = np.dtype(",
    "explicit_segment_layout": "SEGMENT_DTYPE = np.dtype(",
    "explicit_result_layout": "RESULT_DTYPE = np.dtype(",
    "nvrtc_cubin": "nvrtc.nvrtcGetCUBIN(",
    "driver_module_load": "driver.cuModuleLoadData(",
    "driver_kernel_launch": "driver.cuLaunchKernel(",
    "per_batch_offset_rebase": 'host_rays[offset]["segment_offset"] = segment_offset',
    "segment_parity": "CUDA segment mismatch for ray",
    "band_parity": "CUDA band mismatch for ray",
    "device_execution_claim": '"cuda_executed": True',
    "nvrtc_claim": '"nvrtc_compiled": True',
    "nvcc_boundary": '"nvcc_compiled": False',
    "raw_stage_samples": '"samples_ms": {',
    "p99_timing": '"total_p99_ms":',
    "parity_excluded_from_timing": "- pass_parity",
}

REQUIRED_NVRTC_BENCHMARK_TOKENS = {
    "repeat_validation": "def validate_runs(",
    "raw_sample_validation": 'for stage in ("h2d", "kernel", "d2h", "submit_to_result")',
    "workload_identity": '"verified_segments"',
    "p50_p95_p99": 'QUANTILES = ("p50", "p95", "p99")',
    "gpu_state_snapshot": "def gpu_snapshot(",
    "cpu_reference_comparison": '"cpu_p95_to_gpu_submit_p95_ratio"',
    "raw_runs_retained": '"runs": runs',
    "nvcc_boundary": '"nvcc_compiled": False',
    "minecraft_boundary": "does not establish product hot-path crossover",
}

REQUIRED_NVRTC_MATRIX_TOKENS = {
    "deterministic_prefixes": "ray_limits",
    "prefix_device_parity": "entry_from_run(",
    "formal_report_binding": '"formal_report_sha256"',
    "formal_repeat_median": '"formal-repeat-median"',
    "kernel_throughput": '"kernel_p95_rays_per_second"',
    "submit_throughput": '"submit_p95_rays_per_second"',
    "raw_prefix_run": '"raw_run": run',
    "minecraft_boundary": "not a Minecraft native bridge result",
}


def verify_tokens(path: Path, tokens: dict[str, str]) -> list[str]:
    text = path.read_text(encoding="utf-8")
    return [name for name, token in tokens.items() if token not in text]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--cuda-source",
        type=Path,
        default=Path("native/cuda-dda/src/dda_cuda.cu"),
    )
    parser.add_argument(
        "--cmake",
        type=Path,
        default=Path("native/cuda-dda/CMakeLists.txt"),
    )
    parser.add_argument(
        "--nvrtc-kernel",
        type=Path,
        default=Path("native/cuda-dda/src/dda_nvrtc_kernel.cu"),
    )
    parser.add_argument(
        "--nvrtc-runner",
        type=Path,
        default=Path("docs/scripts/run_cuda_dda_nvrtc.py"),
    )
    parser.add_argument(
        "--nvrtc-benchmark",
        type=Path,
        default=Path("docs/scripts/benchmark_cuda_dda_nvrtc_corpus.py"),
    )
    parser.add_argument(
        "--nvrtc-matrix",
        type=Path,
        default=Path("docs/scripts/benchmark_cuda_dda_nvrtc_scaling_matrix.py"),
    )
    arguments = parser.parse_args()

    missing_cuda = verify_tokens(arguments.cuda_source, REQUIRED_CUDA_TOKENS)
    missing_cmake = verify_tokens(arguments.cmake, REQUIRED_CMAKE_TOKENS)
    missing_nvrtc_kernel = verify_tokens(
        arguments.nvrtc_kernel,
        REQUIRED_NVRTC_KERNEL_TOKENS,
    )
    missing_nvrtc_runner = verify_tokens(
        arguments.nvrtc_runner,
        REQUIRED_NVRTC_RUNNER_TOKENS,
    )
    missing_nvrtc_benchmark = verify_tokens(
        arguments.nvrtc_benchmark,
        REQUIRED_NVRTC_BENCHMARK_TOKENS,
    )
    missing_nvrtc_matrix = verify_tokens(
        arguments.nvrtc_matrix,
        REQUIRED_NVRTC_MATRIX_TOKENS,
    )
    valid = not (
        missing_cuda
        or missing_cmake
        or missing_nvrtc_kernel
        or missing_nvrtc_runner
        or missing_nvrtc_benchmark
        or missing_nvrtc_matrix
    )
    report = {
        "status": "valid" if valid else "invalid",
        "schema": 2,
        "cuda_source": str(arguments.cuda_source),
        "cmake": str(arguments.cmake),
        "nvrtc_kernel": str(arguments.nvrtc_kernel),
        "nvrtc_runner": str(arguments.nvrtc_runner),
        "nvrtc_benchmark": str(arguments.nvrtc_benchmark),
        "nvrtc_matrix": str(arguments.nvrtc_matrix),
        "required_cuda_contracts": len(REQUIRED_CUDA_TOKENS),
        "required_cmake_contracts": len(REQUIRED_CMAKE_TOKENS),
        "required_nvrtc_kernel_contracts": len(REQUIRED_NVRTC_KERNEL_TOKENS),
        "required_nvrtc_runner_contracts": len(REQUIRED_NVRTC_RUNNER_TOKENS),
        "required_nvrtc_benchmark_contracts": len(
            REQUIRED_NVRTC_BENCHMARK_TOKENS
        ),
        "required_nvrtc_matrix_contracts": len(REQUIRED_NVRTC_MATRIX_TOKENS),
        "missing_cuda_contracts": missing_cuda,
        "missing_cmake_contracts": missing_cmake,
        "missing_nvrtc_kernel_contracts": missing_nvrtc_kernel,
        "missing_nvrtc_runner_contracts": missing_nvrtc_runner,
        "missing_nvrtc_benchmark_contracts": missing_nvrtc_benchmark,
        "missing_nvrtc_matrix_contracts": missing_nvrtc_matrix,
        "cuda_compiled": False,
        "cuda_executed": False,
        "claim_boundary": (
            "This static source validation is not the separate NVRTC device "
            "execution gate and provides no device parity or performance evidence."
        ),
    }
    print(json.dumps(report, indent=2, sort_keys=True))
    if report["status"] != "valid":
        raise SystemExit("CUDA DDA source contract is incomplete")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
