#!/usr/bin/env python3
"""Run repeat-aware RTX CUDA DDA correctness/performance measurements."""

from __future__ import annotations

import argparse
import json
import shutil
import statistics
import subprocess
import sys
import tempfile
from pathlib import Path


STAGES = ("h2d", "kernel", "d2h", "total")
QUANTILES = ("p50", "p95", "p99")


def validate_runs(
    runs: list[dict[str, object]],
    *,
    warmup: int,
    iterations: int,
    expected_rays: int,
) -> None:
    if len(runs) < 2:
        raise ValueError("at least two CUDA benchmark runs are required")
    first = runs[0]
    identity = (
        first.get("bundle_sha256"),
        first.get("snapshot_sha256"),
        first.get("cells"),
        first.get("rays"),
        first.get("batches"),
        first.get("peak_batch_segments"),
        first.get("verified_segments"),
        first.get("device"),
        first.get("compute_capability"),
    )
    for index, run in enumerate(runs):
        if run.get("status") != "valid":
            raise ValueError(f"run {index} is not valid")
        if run.get("backend") != "cuda-driver-nvrtc":
            raise ValueError(f"run {index} used an unexpected backend")
        if not run.get("cuda_executed") or not run.get("nvrtc_compiled"):
            raise ValueError(f"run {index} did not execute compiled CUDA")
        if run.get("nvcc_compiled") is not False:
            raise ValueError(f"run {index} has an invalid nvcc boundary")
        if run.get("warmup_passes") != warmup:
            raise ValueError(f"run {index} has the wrong warmup count")
        if run.get("measured_passes") != iterations:
            raise ValueError(f"run {index} has the wrong iteration count")
        if run.get("rays") != expected_rays:
            raise ValueError(f"run {index} has the wrong ray count")
        if run.get("verified_rays") != expected_rays:
            raise ValueError(f"run {index} did not verify every ray")
        current = (
            run.get("bundle_sha256"),
            run.get("snapshot_sha256"),
            run.get("cells"),
            run.get("rays"),
            run.get("batches"),
            run.get("peak_batch_segments"),
            run.get("verified_segments"),
            run.get("device"),
            run.get("compute_capability"),
        )
        if current != identity:
            raise ValueError(f"run {index} workload or device identity changed")
        samples = run.get("samples_ms")
        if not isinstance(samples, dict):
            raise ValueError(f"run {index} has no raw samples")
        for stage in ("h2d", "kernel", "d2h", "submit_to_result"):
            values = samples.get(stage)
            if not isinstance(values, list) or len(values) != iterations:
                raise ValueError(
                    f"run {index} has the wrong {stage} sample count"
                )
            if any(
                not isinstance(value, (int, float)) or value < 0
                for value in values
            ):
                raise ValueError(f"run {index} has invalid {stage} samples")
        for stage in STAGES:
            for quantile in QUANTILES:
                field = f"{stage}_{quantile}_ms"
                value = run.get(field)
                if not isinstance(value, (int, float)) or value < 0:
                    raise ValueError(f"run {index} has invalid {field}")


def metric_summary(
    runs: list[dict[str, object]],
    field: str,
) -> dict[str, float]:
    values = [float(run[field]) for run in runs]
    return {
        "minimum": min(values),
        "median": statistics.median(values),
        "maximum": max(values),
    }


def gpu_snapshot(device: int) -> dict[str, object]:
    executable = shutil.which("nvidia-smi")
    if executable is None:
        return {"status": "unavailable", "error": "nvidia-smi not found"}
    query = (
        "name,uuid,utilization.gpu,memory.used,memory.total,"
        "temperature.gpu,power.draw"
    )
    completed = subprocess.run(
        [
            executable,
            f"--query-gpu={query}",
            "--format=csv,noheader,nounits",
            "--id",
            str(device),
        ],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )
    if completed.returncode != 0:
        return {
            "status": "failed",
            "error": completed.stderr.strip() or completed.stdout.strip(),
        }
    fields = [item.strip() for item in completed.stdout.strip().split(",")]
    if len(fields) != 7:
        return {"status": "failed", "error": "unexpected nvidia-smi row"}
    return {
        "status": "valid",
        "name": fields[0],
        "uuid": fields[1],
        "utilization_percent": fields[2],
        "memory_used_mib": fields[3],
        "memory_total_mib": fields[4],
        "temperature_c": fields[5],
        "power_w": fields[6],
    }


def build_report(
    runs: list[dict[str, object]],
    *,
    warmup: int,
    iterations: int,
    expected_rays: int,
    cpu_reference_p95_ms: float,
    gpu_snapshots: list[dict[str, object]],
) -> dict[str, object]:
    validate_runs(
        runs,
        warmup=warmup,
        iterations=iterations,
        expected_rays=expected_rays,
    )
    first = runs[0]
    metrics = {
        f"{stage}_{quantile}_ms": metric_summary(
            runs,
            f"{stage}_{quantile}_ms",
        )
        for stage in STAGES
        for quantile in QUANTILES
    }
    gpu_submit_p95 = metrics["total_p95_ms"]["median"]
    return {
        "status": "valid",
        "schema": 1,
        "backend": "cuda-driver-nvrtc-repeat-benchmark",
        "device": first["device"],
        "compute_capability": first["compute_capability"],
        "driver_version": first["driver_version"],
        "nvrtc_version": first["nvrtc_version"],
        "architecture": first["architecture"],
        "bundle_sha256": first["bundle_sha256"],
        "snapshot_sha256": first["snapshot_sha256"],
        "cells": first["cells"],
        "rays_per_pass": first["rays"],
        "verified_segments_per_repeat": first["verified_segments"],
        "batches_per_pass": first["batches"],
        "peak_batch_rays": first["peak_batch_rays"],
        "peak_batch_segments": first["peak_batch_segments"],
        "peak_segment_bytes": first["peak_segment_bytes"],
        "repeats": len(runs),
        "warmup_passes_per_repeat": warmup,
        "measured_passes_per_repeat": iterations,
        "metrics": metrics,
        "cpu_reference_p95_ms": cpu_reference_p95_ms,
        "cpu_p95_to_gpu_submit_p95_ratio": (
            cpu_reference_p95_ms / gpu_submit_p95
        ),
        "gpu_submit_p95_rays_per_second": (
            expected_rays * 1000.0 / gpu_submit_p95
        ),
        "gpu_kernel_p95_rays_per_second": (
            expected_rays
            * 1000.0
            / metrics["kernel_p95_ms"]["median"]
        ),
        "gpu_snapshots": gpu_snapshots,
        "runs": runs,
        "retains_every_segment": True,
        "cuda_executed": True,
        "nvrtc_compiled": True,
        "nvcc_compiled": False,
        "claim_boundary": (
            "This compares the repeat-aware, segment-retaining correctness "
            "workload with its compiled single-threaded CPU oracle. The "
            "Python Driver API submit time is not a Minecraft native bridge "
            "measurement and does not establish product hot-path crossover."
        ),
    }


def run_once(
    runner: Path,
    bundle: Path,
    kernel: Path,
    *,
    device: int,
    warmup: int,
    iterations: int,
    maximum_rays_per_batch: int,
    maximum_segments_per_batch: int,
) -> dict[str, object]:
    completed = subprocess.run(
        [
            sys.executable,
            str(runner),
            "--bundle",
            str(bundle),
            "--kernel",
            str(kernel),
            "--device",
            str(device),
            "--warmup",
            str(warmup),
            "--iterations",
            str(iterations),
            "--maximum-rays-per-batch",
            str(maximum_rays_per_batch),
            "--maximum-segments-per-batch",
            str(maximum_segments_per_batch),
        ],
        check=True,
        capture_output=True,
        text=True,
        timeout=1800,
    )
    return json.loads(completed.stdout)


def write_atomic(path: Path, report: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        newline="\n",
        dir=path.parent,
        prefix=path.name + ".",
        suffix=".tmp",
        delete=False,
    ) as temporary:
        json.dump(report, temporary, indent=2, sort_keys=True)
        temporary.write("\n")
        temporary_path = Path(temporary.name)
    temporary_path.replace(path)


def main() -> int:
    repository = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--runner",
        type=Path,
        default=repository / "docs/scripts/run_cuda_dda_nvrtc.py",
    )
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument(
        "--kernel",
        type=Path,
        default=repository / "native/cuda-dda/src/dda_nvrtc_kernel.cu",
    )
    parser.add_argument("--output-json", type=Path, required=True)
    parser.add_argument("--device", type=int, default=0)
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--warmup", type=int, default=5)
    parser.add_argument("--iterations", type=int, default=30)
    parser.add_argument("--expected-rays", type=int, default=100_008)
    parser.add_argument("--maximum-rays-per-batch", type=int, default=8192)
    parser.add_argument(
        "--maximum-segments-per-batch",
        type=int,
        default=1_048_576,
    )
    parser.add_argument(
        "--cpu-reference-p95-ms",
        type=float,
        default=1206.2,
    )
    arguments = parser.parse_args()
    if arguments.repeats < 2:
        raise ValueError("--repeats must be at least 2")
    if arguments.warmup < 1 or arguments.iterations < 2:
        raise ValueError("warmup and iterations are too small")
    if arguments.expected_rays < 1:
        raise ValueError("--expected-rays must be positive")
    if arguments.cpu_reference_p95_ms <= 0.0:
        raise ValueError("--cpu-reference-p95-ms must be positive")

    runs = []
    snapshots = []
    for _ in range(arguments.repeats):
        snapshots.append(gpu_snapshot(arguments.device))
        runs.append(
            run_once(
                arguments.runner,
                arguments.bundle,
                arguments.kernel,
                device=arguments.device,
                warmup=arguments.warmup,
                iterations=arguments.iterations,
                maximum_rays_per_batch=arguments.maximum_rays_per_batch,
                maximum_segments_per_batch=(
                    arguments.maximum_segments_per_batch
                ),
            )
        )
        snapshots.append(gpu_snapshot(arguments.device))
    report = build_report(
        runs,
        warmup=arguments.warmup,
        iterations=arguments.iterations,
        expected_rays=arguments.expected_rays,
        cpu_reference_p95_ms=arguments.cpu_reference_p95_ms,
        gpu_snapshots=snapshots,
    )
    write_atomic(arguments.output_json, report)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
