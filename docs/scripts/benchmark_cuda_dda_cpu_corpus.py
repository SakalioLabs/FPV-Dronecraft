#!/usr/bin/env python3
"""Benchmark the CPU oracle on the production-scale CUDA DDA corpus."""

from __future__ import annotations

import argparse
import json
import statistics
import subprocess
import tempfile
from pathlib import Path


def validate_runs(
    runs: list[dict[str, object]],
    warmup: int,
    iterations: int,
) -> None:
    if not runs:
        raise ValueError("at least one benchmark run is required")
    identity = (
        runs[0]["snapshot_generation"],
        runs[0]["snapshot_sha256"],
        runs[0]["rays"],
        runs[0]["cells"],
        runs[0]["checksum"],
    )
    for index, run in enumerate(runs):
        if run.get("status") != "valid":
            raise ValueError(f"run {index} is not valid")
        if run.get("backend") != "cpu-reference":
            raise ValueError(f"run {index} used an unexpected backend")
        if run.get("warmup_batches") != warmup:
            raise ValueError(f"run {index} has the wrong warmup count")
        if run.get("measured_batches") != iterations:
            raise ValueError(f"run {index} has the wrong iteration count")
        current = (
            run.get("snapshot_generation"),
            run.get("snapshot_sha256"),
            run.get("rays"),
            run.get("cells"),
            run.get("checksum"),
        )
        if current != identity:
            raise ValueError(f"run {index} workload identity changed")
        for field in (
            "parse_ms",
            "expected_verify_ms",
            "batch_p50_ms",
            "batch_p95_ms",
            "batch_p99_ms",
            "p95_ns_per_ray",
        ):
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


def build_report(
    runs: list[dict[str, object]],
    *,
    warmup: int,
    iterations: int,
    cpu_label: str,
) -> dict[str, object]:
    validate_runs(runs, warmup, iterations)
    first = runs[0]
    return {
        "status": "valid",
        "schema": 1,
        "backend": "compiled-cpp20-cpu-reference",
        "cpu_label": cpu_label,
        "repeats": len(runs),
        "warmup_batches_per_repeat": warmup,
        "measured_batches_per_repeat": iterations,
        "snapshot_generation": first["snapshot_generation"],
        "snapshot_sha256": first["snapshot_sha256"],
        "rays_per_batch": first["rays"],
        "cells": first["cells"],
        "checksum": first["checksum"],
        "parse_ms": metric_summary(runs, "parse_ms"),
        "expected_verify_ms": metric_summary(runs, "expected_verify_ms"),
        "batch_p50_ms": metric_summary(runs, "batch_p50_ms"),
        "batch_p95_ms": metric_summary(runs, "batch_p95_ms"),
        "batch_p99_ms": metric_summary(runs, "batch_p99_ms"),
        "p95_ns_per_ray": metric_summary(runs, "p95_ns_per_ray"),
        "runs": runs,
        "single_threaded": True,
        "retains_every_segment": True,
        "cuda_compiled": False,
        "cuda_executed": False,
        "claim_boundary": (
            "This is a single-threaded compiled C++ correctness-oracle "
            "baseline that retains every visited segment. It is not the "
            "optimized Minecraft Java hot path and is not CUDA evidence."
        ),
    }


def run_once(
    executor: Path,
    bundle: Path,
    expected_results: Path,
    *,
    warmup: int,
    iterations: int,
) -> dict[str, object]:
    completed = subprocess.run(
        [
            str(executor),
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
    parser = argparse.ArgumentParser()
    parser.add_argument("--executor", type=Path, required=True)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--expected-results", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--warmup", type=int, default=5)
    parser.add_argument("--iterations", type=int, default=30)
    parser.add_argument("--cpu-label", default="unspecified")
    arguments = parser.parse_args()
    if arguments.repeats < 2:
        raise ValueError("repeats must be at least 2")
    if arguments.warmup < 1 or arguments.iterations < 2:
        raise ValueError("warmup and iterations are too small")

    runs = [
        run_once(
            arguments.executor,
            arguments.bundle,
            arguments.expected_results,
            warmup=arguments.warmup,
            iterations=arguments.iterations,
        )
        for _ in range(arguments.repeats)
    ]
    report = build_report(
        runs,
        warmup=arguments.warmup,
        iterations=arguments.iterations,
        cpu_label=arguments.cpu_label,
    )
    write_atomic(arguments.output_json, report)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
