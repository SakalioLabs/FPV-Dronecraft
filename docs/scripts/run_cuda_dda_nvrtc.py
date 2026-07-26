#!/usr/bin/env python3
"""Compile and execute the production DDA kernel with NVRTC and Driver API."""

from __future__ import annotations

import argparse
import json
import math
import sys
import time
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from verify_dda_production_bundle import MATERIALS, verify_file
from verify_dda_production_expected_results import (
    FLAG_REACHED,
    FLAG_STOPPED,
    FLAG_TRUNCATED,
    trace,
)


CELL_DTYPE = np.dtype(
    [
        ("packed", "<u8"),
        ("material_id", "<i4"),
        ("_padding", "V4"),
        ("fill_fraction", "<f8"),
    ]
)
RAY_DTYPE = np.dtype(
    [
        ("start", "<f8", (3,)),
        ("end", "<f8", (3,)),
        ("maximum_cells", "<i4"),
        ("_padding", "V4"),
        ("segment_offset", "<u8"),
    ]
)
SEGMENT_DTYPE = np.dtype(
    [
        ("packed", "<u8"),
        ("length", "<f8"),
        ("material_id", "<i4"),
        ("_padding", "V4"),
        ("fill_fraction", "<f8"),
    ]
)
RESULT_DTYPE = np.dtype(
    [
        ("segment_count", "<i4"),
        ("material_cells", "<i4"),
        ("flags", "u1"),
        ("has_first_material", "u1"),
        ("reserved", "<u2"),
        ("_padding", "V4"),
        ("first_material", "<u8"),
        ("loss", "<f8", (3,)),
        ("gain", "<f8", (3,)),
    ]
)


@dataclass(frozen=True)
class Batch:
    first_ray: int
    ray_count: int
    segment_count: int


def plan_batches(
    maximum_cells: list[int],
    maximum_rays: int,
    maximum_segments: int,
) -> list[Batch]:
    if maximum_rays < 1 or maximum_segments < 1:
        raise ValueError("CUDA batch limits must be positive")
    batches: list[Batch] = []
    first_ray = 0
    ray_count = 0
    segment_count = 0
    for ray_index, segments in enumerate(maximum_cells):
        if segments < 1:
            raise ValueError("ray maximum_cells must be positive")
        if segments > maximum_segments:
            raise ValueError("single ray exceeds CUDA batch segment limit")
        if ray_count and (
            ray_count == maximum_rays
            or segment_count > maximum_segments - segments
        ):
            batches.append(Batch(first_ray, ray_count, segment_count))
            first_ray = ray_index
            ray_count = 0
            segment_count = 0
        if ray_count == 0:
            first_ray = ray_index
        ray_count += 1
        segment_count += segments
    if ray_count:
        batches.append(Batch(first_ray, ray_count, segment_count))
    return batches


def percentile(samples: list[float], quantile: float) -> float:
    ordered = sorted(samples)
    index = max(0, math.ceil(quantile * len(ordered)) - 1)
    return ordered[min(index, len(ordered) - 1)]


def _check_layouts() -> None:
    expected = {
        "DeviceCell": (CELL_DTYPE.itemsize, 24),
        "DeviceRay": (RAY_DTYPE.itemsize, 64),
        "DeviceSegment": (SEGMENT_DTYPE.itemsize, 32),
        "DeviceResult": (RESULT_DTYPE.itemsize, 72),
    }
    mismatches = [
        f"{name}={actual}, expected={wanted}"
        for name, (actual, wanted) in expected.items()
        if actual != wanted
    ]
    if mismatches:
        raise RuntimeError("host/device layout mismatch: " + ", ".join(mismatches))


def _cuda_imports():
    try:
        from cuda.bindings import driver, nvrtc
        from cuda.bindings._example_helpers.helper_cuda import check_cuda_errors
    except ImportError as error:
        raise RuntimeError(
            "CUDA Python dependencies are missing; install "
            "native/cuda-dda/cuda-python-requirements.txt in an isolated venv"
        ) from error
    return driver, nvrtc, check_cuda_errors


def _compile_kernel(source: str, architecture: str):
    driver, nvrtc, check = _cuda_imports()
    program = check(
        nvrtc.nvrtcCreateProgram(
            source.encode(),
            b"dda_nvrtc_kernel.cu",
            0,
            None,
            None,
        )
    )
    options = [
        f"--gpu-architecture={architecture}".encode(),
        b"--std=c++17",
        b"--fmad=true",
    ]
    result = nvrtc.nvrtcCompileProgram(program, len(options), options)
    if result[0] != nvrtc.nvrtcResult.NVRTC_SUCCESS:
        log_size = check(nvrtc.nvrtcGetProgramLogSize(program))
        log = bytearray(log_size)
        check(nvrtc.nvrtcGetProgramLog(program, log))
        check(nvrtc.nvrtcDestroyProgram(program))
        raise RuntimeError(log.rstrip(b"\0").decode(errors="replace"))
    size = check(nvrtc.nvrtcGetCUBINSize(program))
    cubin = bytearray(size)
    check(nvrtc.nvrtcGetCUBIN(program, cubin))
    check(nvrtc.nvrtcDestroyProgram(program))
    return cubin


def _device_pointer_argument(pointer: object) -> np.ndarray:
    return np.array([int(pointer)], dtype=np.uint64)


def _scalar_argument(value: int) -> np.ndarray:
    return np.array(value, dtype=np.int32)


def _verify_batch(
    rays: list[tuple],
    cell_map: dict[int, tuple[int, float]],
    batch: Batch,
    ray_array: np.ndarray,
    segments: np.ndarray | None,
    results: np.ndarray,
) -> int:
    verified_segments = 0
    for offset in range(batch.ray_count):
        ray_index = batch.first_ray + offset
        expected = trace(rays[ray_index], cell_map)
        actual = results[offset]
        expected_flags = (
            (FLAG_REACHED if expected["reached"] else 0)
            | (FLAG_STOPPED if expected["stopped"] else 0)
            | (FLAG_TRUNCATED if expected["truncated"] else 0)
        )
        aggregate = (
            int(actual["segment_count"]),
            int(actual["material_cells"]),
            int(actual["flags"]),
            bool(actual["has_first_material"]),
            int(actual["first_material"])
            if actual["has_first_material"]
            else None,
        )
        expected_aggregate = (
            len(expected["segments"]),
            expected["material_cells"],
            expected_flags,
            expected["first_material"] is not None,
            expected["first_material"],
        )
        if aggregate != expected_aggregate:
            raise RuntimeError(
                f"CUDA aggregate mismatch for ray {ray_index}: "
                f"{aggregate!r} != {expected_aggregate!r}"
            )
        if not np.allclose(
            actual["loss"],
            expected["loss"],
            rtol=0.0,
            atol=1.0e-5,
        ) or not np.allclose(
            actual["gain"],
            expected["gain"],
            rtol=0.0,
            atol=1.0e-5,
        ):
            raise RuntimeError(f"CUDA band mismatch for ray {ray_index}")
        if segments is not None:
            base = int(ray_array[offset]["segment_offset"])
            for segment_index, expected_segment in enumerate(
                expected["segments"]
            ):
                actual_segment = segments[base + segment_index]
                topology = (
                    int(actual_segment["packed"]),
                    int(actual_segment["material_id"]),
                    float(actual_segment["fill_fraction"]),
                )
                expected_topology = (
                    expected_segment[0],
                    expected_segment[2],
                    expected_segment[3],
                )
                if topology != expected_topology or not math.isclose(
                    float(actual_segment["length"]),
                    expected_segment[1],
                    rel_tol=1.0e-12,
                    abs_tol=1.0e-12,
                ):
                    raise RuntimeError(
                        f"CUDA segment mismatch for ray {ray_index}, "
                        f"segment {segment_index}"
                    )
        verified_segments += len(expected["segments"])
    return verified_segments


def execute(arguments: argparse.Namespace) -> dict:
    _check_layouts()
    driver, nvrtc, check = _cuda_imports()
    bundle = verify_file(
        arguments.bundle,
        require_complete=True,
        include_details=True,
    )
    cells = bundle.pop("_cell_records")
    rays = bundle.pop("_ray_records")
    bundle_ray_count = len(rays)
    if arguments.ray_limit is not None:
        if arguments.ray_limit < 1 or arguments.ray_limit > bundle_ray_count:
            raise ValueError(
                f"--ray-limit must be within 1..{bundle_ray_count}"
            )
        rays = rays[: arguments.ray_limit]
    cell_map = {
        cell["packed"]: (cell["material_id"], cell["fill_fraction"])
        for cell in cells
    }
    batches = plan_batches(
        [ray[1] for ray in rays],
        arguments.maximum_rays_per_batch,
        arguments.maximum_segments_per_batch,
    )

    check(driver.cuInit(0))
    device = check(driver.cuDeviceGet(arguments.device))
    major = check(
        driver.cuDeviceGetAttribute(
            driver.CUdevice_attribute.CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MAJOR,
            device,
        )
    )
    minor = check(
        driver.cuDeviceGetAttribute(
            driver.CUdevice_attribute.CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MINOR,
            device,
        )
    )
    device_name = check(driver.cuDeviceGetName(256, device)).split(b"\0", 1)[0].decode()
    architecture = f"sm_{major}{minor}"

    compile_start = time.perf_counter()
    cubin = _compile_kernel(arguments.kernel.read_text(encoding="utf-8"), architecture)
    compile_ms = (time.perf_counter() - compile_start) * 1000.0

    context = check(driver.cuCtxCreate(None, 0, device))
    allocations: list[object] = []
    module = None
    start_event = None
    end_event = None
    try:
        module = check(
            driver.cuModuleLoadData(np.frombuffer(cubin, dtype=np.uint8))
        )
        function_name = (
            b"trace_kernel"
            if arguments.output_mode == "full"
            else b"trace_aggregate_kernel"
        )
        function = check(driver.cuModuleGetFunction(module, function_name))

        host_cells = np.zeros(len(cells), dtype=CELL_DTYPE)
        for index, cell in enumerate(cells):
            host_cells[index]["packed"] = cell["packed"]
            host_cells[index]["material_id"] = cell["material_id"]
            host_cells[index]["fill_fraction"] = cell["fill_fraction"]
        transmission = np.array(
            [
                coefficient
                for material in MATERIALS
                for coefficient in material[1]
            ],
            dtype=np.float64,
        )
        peak_rays = max(batch.ray_count for batch in batches)
        peak_segments = max(batch.segment_count for batch in batches)
        full_topology = arguments.output_mode == "full"
        host_segments = (
            np.empty(peak_segments, dtype=SEGMENT_DTYPE)
            if full_topology
            else None
        )
        host_results = np.empty(peak_rays, dtype=RESULT_DTYPE)

        def allocate(byte_count: int):
            pointer = check(driver.cuMemAlloc(byte_count))
            allocations.append(pointer)
            return pointer

        device_cells = allocate(host_cells.nbytes)
        device_rays = allocate(peak_rays * RAY_DTYPE.itemsize)
        device_transmission = allocate(transmission.nbytes)
        device_segments = (
            allocate(peak_segments * SEGMENT_DTYPE.itemsize)
            if full_topology
            else None
        )
        device_results = allocate(peak_rays * RESULT_DTYPE.itemsize)
        check(
            driver.cuMemcpyHtoD(
                device_cells,
                host_cells.ctypes.data,
                host_cells.nbytes,
            )
        )
        check(
            driver.cuMemcpyHtoD(
                device_transmission,
                transmission.ctypes.data,
                transmission.nbytes,
            )
        )
        start_event = check(driver.cuEventCreate(0))
        end_event = check(driver.cuEventCreate(0))

        h2d_samples: list[float] = []
        kernel_samples: list[float] = []
        d2h_samples: list[float] = []
        total_samples: list[float] = []
        verified_rays = 0
        verified_segments = 0
        parity_ms = 0.0
        total_passes = arguments.warmup + arguments.iterations
        for pass_index in range(total_passes):
            pass_start = time.perf_counter()
            pass_h2d = 0.0
            pass_kernel = 0.0
            pass_d2h = 0.0
            pass_parity = 0.0
            for batch in batches:
                host_rays = np.zeros(batch.ray_count, dtype=RAY_DTYPE)
                segment_offset = 0
                for offset in range(batch.ray_count):
                    coordinates, maximum_cells = rays[batch.first_ray + offset]
                    host_rays[offset]["start"] = coordinates[:3]
                    host_rays[offset]["end"] = coordinates[3:]
                    host_rays[offset]["maximum_cells"] = maximum_cells
                    host_rays[offset]["segment_offset"] = segment_offset
                    segment_offset += maximum_cells

                stage_start = time.perf_counter()
                check(
                    driver.cuMemcpyHtoD(
                        device_rays,
                        host_rays.ctypes.data,
                        host_rays.nbytes,
                    )
                )
                pass_h2d += (time.perf_counter() - stage_start) * 1000.0

                cell_pointer = _device_pointer_argument(device_cells)
                cell_count = _scalar_argument(len(cells))
                ray_pointer = _device_pointer_argument(device_rays)
                ray_count = _scalar_argument(batch.ray_count)
                transmission_pointer = _device_pointer_argument(
                    device_transmission
                )
                segment_pointer = _device_pointer_argument(
                    device_segments if device_segments is not None else 0
                )
                result_pointer = _device_pointer_argument(device_results)
                kernel_arguments = (
                    cell_pointer,
                    cell_count,
                    ray_pointer,
                    ray_count,
                    transmission_pointer,
                    segment_pointer,
                    result_pointer,
                )
                kernel_params = np.array(
                    [argument.ctypes.data for argument in kernel_arguments],
                    dtype=np.uintp,
                )
                check(driver.cuEventRecord(start_event, 0))
                check(
                    driver.cuLaunchKernel(
                        function,
                        (batch.ray_count + 127) // 128,
                        1,
                        1,
                        128,
                        1,
                        1,
                        0,
                        0,
                        kernel_params,
                        0,
                    )
                )
                check(driver.cuEventRecord(end_event, 0))
                check(driver.cuEventSynchronize(end_event))
                pass_kernel += check(
                    driver.cuEventElapsedTime(start_event, end_event)
                )

                stage_start = time.perf_counter()
                if full_topology:
                    check(
                        driver.cuMemcpyDtoH(
                            host_segments.ctypes.data,
                            device_segments,
                            batch.segment_count * SEGMENT_DTYPE.itemsize,
                        )
                    )
                check(
                    driver.cuMemcpyDtoH(
                        host_results.ctypes.data,
                        device_results,
                        batch.ray_count * RESULT_DTYPE.itemsize,
                    )
                )
                pass_d2h += (time.perf_counter() - stage_start) * 1000.0

                if pass_index == 0:
                    parity_start = time.perf_counter()
                    verified_segments += _verify_batch(
                        rays,
                        cell_map,
                        batch,
                        host_rays,
                        host_segments,
                        host_results,
                    )
                    batch_parity_ms = (
                        time.perf_counter() - parity_start
                    ) * 1000.0
                    pass_parity += batch_parity_ms
                    parity_ms += batch_parity_ms
                    verified_rays += batch.ray_count
            if pass_index >= arguments.warmup:
                h2d_samples.append(pass_h2d)
                kernel_samples.append(pass_kernel)
                d2h_samples.append(pass_d2h)
                total_samples.append(
                    (time.perf_counter() - pass_start) * 1000.0
                    - pass_parity
                )

        nvrtc_major, nvrtc_minor = check(nvrtc.nvrtcVersion())
        driver_version = check(driver.cuDriverGetVersion())
        return {
            "status": "valid",
            "backend": "cuda-driver-nvrtc",
            "schema": 1,
            "cuda_executed": True,
            "nvrtc_compiled": True,
            "nvcc_compiled": False,
            "device": device_name,
            "compute_capability": f"{major}.{minor}",
            "architecture": architecture,
            "output_mode": arguments.output_mode,
            "driver_version": driver_version,
            "nvrtc_version": f"{nvrtc_major}.{nvrtc_minor}",
            "bundle_sha256": bundle["file_sha256"],
            "snapshot_sha256": bundle["snapshot_sha256"],
            "cells": len(cells),
            "bundle_rays": bundle_ray_count,
            "rays": len(rays),
            "batches": len(batches),
            "peak_batch_rays": peak_rays,
            "peak_batch_segments": peak_segments,
            "peak_segment_bytes": (
                peak_segments * SEGMENT_DTYPE.itemsize
                if full_topology
                else 0
            ),
            "segment_topology_verified": full_topology,
            "aggregate_parity_verified": True,
            "d2h_reserved_segment_bytes_per_pass": (
                sum(ray[1] for ray in rays) * SEGMENT_DTYPE.itemsize
                if full_topology
                else 0
            ),
            "d2h_result_bytes_per_pass": len(rays) * RESULT_DTYPE.itemsize,
            "warmup_passes": arguments.warmup,
            "measured_passes": arguments.iterations,
            "verified_rays": verified_rays,
            "verified_segments": verified_segments,
            "parity_ms": parity_ms,
            "compile_ms": compile_ms,
            "h2d_p50_ms": percentile(h2d_samples, 0.50),
            "h2d_p95_ms": percentile(h2d_samples, 0.95),
            "h2d_p99_ms": percentile(h2d_samples, 0.99),
            "kernel_p50_ms": percentile(kernel_samples, 0.50),
            "kernel_p95_ms": percentile(kernel_samples, 0.95),
            "kernel_p99_ms": percentile(kernel_samples, 0.99),
            "d2h_p50_ms": percentile(d2h_samples, 0.50),
            "d2h_p95_ms": percentile(d2h_samples, 0.95),
            "d2h_p99_ms": percentile(d2h_samples, 0.99),
            "total_p50_ms": percentile(total_samples, 0.50),
            "total_p95_ms": percentile(total_samples, 0.95),
            "total_p99_ms": percentile(total_samples, 0.99),
            "samples_ms": {
                "h2d": h2d_samples,
                "kernel": kernel_samples,
                "d2h": d2h_samples,
                "submit_to_result": total_samples,
            },
        }
    finally:
        if end_event is not None:
            check(driver.cuEventDestroy(end_event))
        if start_event is not None:
            check(driver.cuEventDestroy(start_event))
        for pointer in reversed(allocations):
            check(driver.cuMemFree(pointer))
        if module is not None:
            check(driver.cuModuleUnload(module))
        check(driver.cuCtxDestroy(context))


def parse_arguments() -> argparse.Namespace:
    repository = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser()
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument(
        "--kernel",
        type=Path,
        default=repository / "native/cuda-dda/src/dda_nvrtc_kernel.cu",
    )
    parser.add_argument("--device", type=int, default=0)
    parser.add_argument("--ray-limit", type=int)
    parser.add_argument(
        "--output-mode",
        choices=("full", "aggregate"),
        default="full",
    )
    parser.add_argument("--warmup", type=int, default=2)
    parser.add_argument("--iterations", type=int, default=10)
    parser.add_argument("--maximum-rays-per-batch", type=int, default=8192)
    parser.add_argument(
        "--maximum-segments-per-batch",
        type=int,
        default=1_048_576,
    )
    parser.add_argument("--output-json", type=Path)
    result = parser.parse_args()
    if result.warmup < 0 or result.iterations < 1:
        parser.error("--warmup must be non-negative and --iterations positive")
    return result


def main() -> int:
    arguments = parse_arguments()
    try:
        report = execute(arguments)
    except (OSError, RuntimeError, ValueError) as error:
        print(json.dumps({"status": "invalid", "error": str(error)}))
        return 1
    payload = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if arguments.output_json:
        arguments.output_json.parent.mkdir(parents=True, exist_ok=True)
        arguments.output_json.write_text(payload, encoding="utf-8")
    print(payload, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
