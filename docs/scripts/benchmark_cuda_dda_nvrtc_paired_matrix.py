#!/usr/bin/env python3
"""Run paired CPU/full/aggregate CUDA DDA prefix trials in one time window."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import statistics
import subprocess
import sys
from pathlib import Path


def percentile(samples: list[float], quantile: float) -> float:
    ordered = sorted(samples)
    index = max(0, math.ceil(quantile * len(ordered)) - 1)
    return ordered[min(index, len(ordered) - 1)]


def run_json(command: list[str], timeout_seconds: float) -> dict:
    completed = subprocess.run(
        command,
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout_seconds,
    )
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise RuntimeError(
            f"{Path(command[0]).name} exited {completed.returncode}: {detail}"
        )
    try:
        return json.loads(completed.stdout)
    except json.JSONDecodeError as error:
        raise RuntimeError(
            f"{Path(command[0]).name} returned non-JSON output"
        ) from error


def gpu_snapshot(timeout_seconds: float) -> dict:
    command = [
        "nvidia-smi",
        (
            "--query-gpu=timestamp,name,driver_version,memory.used,"
            "memory.total,utilization.gpu,utilization.memory,"
            "temperature.gpu,pstate"
        ),
        "--format=csv,noheader,nounits",
    ]
    try:
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout_seconds,
        )
    except subprocess.TimeoutExpired:
        return {"status": "timeout", "timeout_seconds": timeout_seconds}
    if completed.returncode != 0:
        return {
            "status": "invalid",
            "returncode": completed.returncode,
            "detail": completed.stderr.strip() or completed.stdout.strip(),
        }
    return {"status": "valid", "csv": completed.stdout.strip()}


def validate_gpu_run(
    report: dict,
    *,
    ray_limit: int,
    warmup: int,
    iterations: int,
) -> None:
    if (
        report.get("status") != "valid"
        or report.get("output_mode") != "paired"
        or report.get("paired_execution") is not True
        or report.get("shared_cuda_context") is not True
        or report.get("host_preparation") != "once"
    ):
        raise ValueError("GPU run did not use the paired shared-context path")
    if report.get("rays") != ray_limit:
        raise ValueError("GPU run used the wrong prefix")
    if (
        report.get("warmup_passes") != warmup
        or report.get("measured_passes") != iterations
    ):
        raise ValueError("GPU run used the wrong pass counts")
    expected_orders = [
        ["full", "aggregate"] if index % 2 == 0 else ["aggregate", "full"]
        for index in range(warmup + iterations)
    ]
    if report.get("alternating_execution_order") != expected_orders:
        raise ValueError("GPU run did not alternate mode order")
    modes = report.get("mode_reports", {})
    if set(modes) != {"full", "aggregate"}:
        raise ValueError("GPU paired mode reports are incomplete")
    for mode in ("full", "aggregate"):
        mode_report = modes[mode]
        if (
            mode_report.get("verified_rays") != ray_limit
            or mode_report.get("aggregate_parity_verified") is not True
            or len(
                mode_report.get("samples_ms", {}).get(
                    "submit_to_result",
                    [],
                )
            )
            != iterations
        ):
            raise ValueError(f"GPU {mode} evidence is incomplete")
    if modes["full"].get("segment_topology_verified") is not True:
        raise ValueError("GPU full topology was not verified")
    if modes["aggregate"].get("segment_topology_verified") is not False:
        raise ValueError("GPU aggregate mode overclaimed topology")


def validate_cpu_run(
    report: dict,
    *,
    ray_limit: int,
    warmup: int,
    iterations: int,
) -> None:
    if (
        report.get("status") != "valid"
        or report.get("backend") != "cpu-reference"
        or report.get("rays") != ray_limit
        or report.get("warmup_batches") != warmup
        or report.get("measured_batches") != iterations
    ):
        raise ValueError("CPU paired-window evidence is incomplete")


def summarize_prefix(
    ray_limit: int,
    trials: list[dict],
    deadline_ms: float,
) -> dict:
    cpu_p95 = statistics.median(
        trial["cpu"]["batch_p95_ms"] for trial in trials
    )
    cpu_p99 = statistics.median(
        trial["cpu"]["batch_p99_ms"] for trial in trials
    )
    modes = {}
    for mode in ("full", "aggregate"):
        mode_runs = [
            trial["gpu"]["mode_reports"][mode]
            for trial in trials
        ]
        submit_samples = [
            sample
            for run in mode_runs
            for sample in run["samples_ms"]["submit_to_result"]
        ]
        mode_p95 = statistics.median(
            run["total_p95_ms"] for run in mode_runs
        )
        mode_p99 = statistics.median(
            run["total_p99_ms"] for run in mode_runs
        )
        deadline_misses = sum(
            sample > deadline_ms for sample in submit_samples
        )
        modes[mode] = {
            "submit_p50_median_ms": statistics.median(
                run["total_p50_ms"] for run in mode_runs
            ),
            "submit_p95_median_ms": mode_p95,
            "submit_p99_median_ms": mode_p99,
            "raw_submit_p95_ms": percentile(submit_samples, 0.95),
            "raw_submit_p99_ms": percentile(submit_samples, 0.99),
            "deadline_ms": deadline_ms,
            "deadline_misses": deadline_misses,
            "measured_passes": len(submit_samples),
            "cpu_p95_to_gpu_submit_p95_ratio": cpu_p95 / mode_p95,
            "cpu_p99_to_gpu_submit_p99_ratio": cpu_p99 / mode_p99,
            "passes_p95_gate": mode_p95 < cpu_p95,
            "passes_p99_gate": mode_p99 < cpu_p99,
            "passes_deadline_gate": deadline_misses == 0,
        }
        modes[mode]["passes_all_gates"] = all(
            modes[mode][key]
            for key in (
                "passes_p95_gate",
                "passes_p99_gate",
                "passes_deadline_gate",
            )
        )
    return {
        "rays": ray_limit,
        "cpu_p50_median_ms": statistics.median(
            trial["cpu"]["batch_p50_ms"] for trial in trials
        ),
        "cpu_p95_median_ms": cpu_p95,
        "cpu_p99_median_ms": cpu_p99,
        "modes": modes,
        "trials": trials,
    }


def first_three_prefix_gate(entries: list[dict], mode: str) -> int | None:
    for index in range(len(entries) - 2):
        window = entries[index : index + 3]
        if all(entry["modes"][mode]["passes_all_gates"] for entry in window):
            return entries[index]["rays"]
    return None


def main() -> int:
    repository = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--runner",
        type=Path,
        default=repository / "docs/scripts/run_cuda_dda_nvrtc.py",
    )
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--expected-results", type=Path, required=True)
    parser.add_argument("--cpu-executor", type=Path, required=True)
    parser.add_argument(
        "--kernel",
        type=Path,
        default=repository / "native/cuda-dda/src/dda_nvrtc_kernel.cu",
    )
    parser.add_argument("--output-json", type=Path, required=True)
    parser.add_argument("--ray-limits", default="128,256,512,1024,2048,4096,8192")
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--warmup", type=int, default=5)
    parser.add_argument("--iterations", type=int, default=30)
    parser.add_argument("--deadline-ms", type=float, default=50.0)
    parser.add_argument("--driver-probe-timeout", type=float, default=5.0)
    parser.add_argument("--process-timeout", type=float, default=300.0)
    parser.add_argument("--cpu-label", default="unspecified")
    arguments = parser.parse_args()

    limits = [int(value) for value in arguments.ray_limits.split(",")]
    if limits != sorted(set(limits)) or any(value < 1 for value in limits):
        raise ValueError("--ray-limits must be unique ascending positives")
    if arguments.repeats < 3:
        raise ValueError("--repeats must be at least 3")
    if arguments.warmup < 1 or arguments.iterations < 2:
        raise ValueError("warmup and iterations are too small")
    if arguments.deadline_ms <= 0.0:
        raise ValueError("--deadline-ms must be positive")

    entries = []
    identity = None
    for ray_limit in limits:
        trials = []
        for trial_index in range(arguments.repeats):
            gpu_command = [
                sys.executable,
                str(arguments.runner),
                "--bundle",
                str(arguments.bundle),
                "--kernel",
                str(arguments.kernel),
                "--ray-limit",
                str(ray_limit),
                "--output-mode",
                "paired",
                "--host-preparation",
                "once",
                "--warmup",
                str(arguments.warmup),
                "--iterations",
                str(arguments.iterations),
                "--driver-probe-timeout",
                str(arguments.driver_probe_timeout),
            ]
            cpu_command = [
                str(arguments.cpu_executor),
                "--ray-limit",
                str(ray_limit),
                "--warmup",
                str(arguments.warmup),
                "--iterations",
                str(arguments.iterations),
                "--expected-results",
                str(arguments.expected_results),
                str(arguments.bundle),
            ]
            before = gpu_snapshot(arguments.driver_probe_timeout)
            if trial_index % 2 == 0:
                gpu = run_json(gpu_command, arguments.process_timeout)
                cpu = run_json(cpu_command, arguments.process_timeout)
                pair_order = ["gpu", "cpu"]
            else:
                cpu = run_json(cpu_command, arguments.process_timeout)
                gpu = run_json(gpu_command, arguments.process_timeout)
                pair_order = ["cpu", "gpu"]
            after = gpu_snapshot(arguments.driver_probe_timeout)
            validate_gpu_run(
                gpu,
                ray_limit=ray_limit,
                warmup=arguments.warmup,
                iterations=arguments.iterations,
            )
            validate_cpu_run(
                cpu,
                ray_limit=ray_limit,
                warmup=arguments.warmup,
                iterations=arguments.iterations,
            )
            current_identity = (
                gpu["bundle_sha256"],
                gpu["snapshot_sha256"],
                cpu["snapshot_sha256"],
                gpu["cells"],
                gpu["bundle_rays"],
            )
            if identity is None:
                identity = current_identity
            elif current_identity != identity:
                raise ValueError("paired corpus identity changed")
            trials.append(
                {
                    "trial": trial_index + 1,
                    "pair_order": pair_order,
                    "gpu_snapshot_before": before,
                    "gpu": gpu,
                    "cpu": cpu,
                    "gpu_snapshot_after": after,
                }
            )
        entries.append(
            summarize_prefix(ray_limit, trials, arguments.deadline_ms)
        )

    payload = {
        "status": "valid",
        "schema": 1,
        "experiment": "same-context-paired-cuda-dda-prefix-crossover",
        "claim_boundary": (
            "Research runner timing on one shared WDDM GPU; not a Minecraft "
            "native bridge or audio-thread deadline measurement."
        ),
        "cpu_label": arguments.cpu_label,
        "ray_limits": limits,
        "repeats": arguments.repeats,
        "warmup_passes_per_trial": arguments.warmup,
        "measured_passes_per_trial": arguments.iterations,
        "deadline_ms": arguments.deadline_ms,
        "bundle_sha256": identity[0],
        "snapshot_sha256": identity[1],
        "expected_snapshot_sha256": identity[2],
        "entries": entries,
        "first_three_prefix_full_gate": first_three_prefix_gate(
            entries,
            "full",
        ),
        "first_three_prefix_aggregate_gate": first_three_prefix_gate(
            entries,
            "aggregate",
        ),
    }
    encoded = json.dumps(payload, indent=2, sort_keys=True) + "\n"
    arguments.output_json.parent.mkdir(parents=True, exist_ok=True)
    arguments.output_json.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    print(
        "report_sha256="
        + hashlib.sha256(encoded.encode("utf-8")).hexdigest(),
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
