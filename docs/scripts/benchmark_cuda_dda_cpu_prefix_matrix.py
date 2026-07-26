#!/usr/bin/env python3
"""Benchmark compiled CPU DDA over the CUDA scaling corpus prefixes."""

from __future__ import annotations

import argparse
import hashlib
import json
import statistics
import subprocess
from pathlib import Path

from benchmark_cuda_dda_cpu_corpus import write_atomic


def run_once(
    executor: Path,
    bundle: Path,
    expected_results: Path,
    *,
    ray_limit: int,
    warmup: int,
    iterations: int,
) -> dict[str, object]:
    completed = subprocess.run(
        [
            str(executor),
            "--ray-limit",
            str(ray_limit),
            "--warmup",
            str(warmup),
            "--iterations",
            str(iterations),
            "--expected-results",
            str(expected_results),
            str(bundle),
        ],
        check=True,
        capture_output=True,
        text=True,
        timeout=1800,
    )
    lines = [line for line in completed.stdout.splitlines() if line.strip()]
    if len(lines) != 1:
        raise ValueError("CPU reference must emit exactly one JSON record")
    return json.loads(lines[0])


def validate_group(
    runs: list[dict[str, object]],
    *,
    ray_limit: int,
    warmup: int,
    iterations: int,
) -> None:
    if len(runs) < 2:
        raise ValueError("each CPU prefix requires at least two repeats")
    identity = (
        runs[0].get("snapshot_generation"),
        runs[0].get("snapshot_sha256"),
        runs[0].get("cells"),
        runs[0].get("bundle_rays"),
        runs[0].get("rays"),
        runs[0].get("checksum"),
    )
    for index, run in enumerate(runs):
        if run.get("status") != "valid":
            raise ValueError(f"ray limit {ray_limit} run {index} is invalid")
        if run.get("backend") != "cpu-reference":
            raise ValueError("unexpected CPU backend")
        if run.get("rays") != ray_limit:
            raise ValueError("CPU prefix ray limit changed")
        if run.get("warmup_batches") != warmup:
            raise ValueError("CPU prefix warmup count changed")
        if run.get("measured_batches") != iterations:
            raise ValueError("CPU prefix iteration count changed")
        current = (
            run.get("snapshot_generation"),
            run.get("snapshot_sha256"),
            run.get("cells"),
            run.get("bundle_rays"),
            run.get("rays"),
            run.get("checksum"),
        )
        if current != identity:
            raise ValueError("CPU prefix workload identity changed")


def summary(runs: list[dict[str, object]], field: str) -> dict[str, float]:
    values = [float(run[field]) for run in runs]
    return {
        "minimum": min(values),
        "median": statistics.median(values),
        "maximum": max(values),
    }


def gpu_entries_by_rays(report: dict[str, object]) -> dict[int, dict]:
    return {int(entry["rays"]): entry for entry in report["entries"]}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--executor", type=Path, required=True)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--expected-results", type=Path, required=True)
    parser.add_argument("--gpu-matrix", type=Path)
    parser.add_argument("--output-json", type=Path, required=True)
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--warmup", type=int, default=5)
    parser.add_argument("--iterations", type=int, default=30)
    parser.add_argument(
        "--ray-limits",
        default="8192,16384,32768,65536,100008",
    )
    parser.add_argument("--cpu-label", default="unspecified")
    arguments = parser.parse_args()
    limits = [int(value) for value in arguments.ray_limits.split(",")]
    if (
        not limits
        or any(value < 1 for value in limits)
        or limits != sorted(set(limits))
    ):
        raise ValueError("--ray-limits must be unique ascending positives")
    if arguments.repeats < 2:
        raise ValueError("--repeats must be at least 2")
    if arguments.warmup < 1 or arguments.iterations < 2:
        raise ValueError("warmup and iterations are too small")

    gpu_payload = None
    gpu_entries = None
    if arguments.gpu_matrix is not None:
        gpu_payload = arguments.gpu_matrix.read_bytes()
        gpu_report = json.loads(gpu_payload)
        if gpu_report.get("status") != "valid":
            raise ValueError("GPU matrix is not valid")
        gpu_entries = gpu_entries_by_rays(gpu_report)
        if set(gpu_entries) != set(limits):
            raise ValueError("CPU and GPU matrix ray limits differ")

    entries = []
    for ray_limit in limits:
        runs = [
            run_once(
                arguments.executor,
                arguments.bundle,
                arguments.expected_results,
                ray_limit=ray_limit,
                warmup=arguments.warmup,
                iterations=arguments.iterations,
            )
            for _ in range(arguments.repeats)
        ]
        validate_group(
            runs,
            ray_limit=ray_limit,
            warmup=arguments.warmup,
            iterations=arguments.iterations,
        )
        p50 = summary(runs, "batch_p50_ms")
        p95 = summary(runs, "batch_p95_ms")
        p99 = summary(runs, "batch_p99_ms")
        entry = {
                "rays": ray_limit,
                "batch_p50_ms": p50,
                "batch_p95_ms": p95,
                "batch_p99_ms": p99,
                "p95_rays_per_second": ray_limit * 1000.0 / p95["median"],
                "runs": runs,
            }
        if gpu_entries is not None:
            gpu_submit_p95 = float(
                gpu_entries[ray_limit]["metrics"]["total_p95_ms"]
            )
            entry["gpu_submit_p95_ms"] = gpu_submit_p95
            entry["cpu_p95_to_gpu_submit_p95_ratio"] = (
                p95["median"] / gpu_submit_p95
            )
        entries.append(entry)

    report = {
        "status": "valid",
        "schema": 1,
        "backend": "compiled-cpp20-cpu-prefix-matrix",
        "cpu_label": arguments.cpu_label,
        "ray_limits": limits,
        "repeats": arguments.repeats,
        "warmup_batches_per_repeat": arguments.warmup,
        "measured_batches_per_repeat": arguments.iterations,
        "gpu_matrix_sha256": (
            hashlib.sha256(gpu_payload).hexdigest()
            if gpu_payload is not None
            else None
        ),
        "entries": entries,
        "single_threaded": True,
        "retains_every_segment": True,
        "cuda_compiled": False,
        "cuda_executed": False,
        "claim_boundary": (
            "This is a single-threaded segment-retaining correctness prefix "
            "matrix. When a GPU matrix is supplied, GPU prefix rows below "
            "100008 rays are single-process diagnostics, so their crossover "
            "ratios are screening evidence."
        ),
    }
    write_atomic(arguments.output_json, report)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
