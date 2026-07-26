#!/usr/bin/env python3
"""Measure CUDA DDA scaling over deterministic prefixes of the 100k corpus."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path

from benchmark_cuda_dda_nvrtc_corpus import gpu_snapshot, write_atomic


STAGES = ("h2d", "kernel", "d2h", "total")
QUANTILES = ("p50", "p95", "p99")


def entry_from_run(run: dict[str, object]) -> dict[str, object]:
    if run.get("status") != "valid" or not run.get("cuda_executed"):
        raise ValueError("scaling run did not execute valid CUDA")
    rays = int(run["rays"])
    if int(run["verified_rays"]) != rays:
        raise ValueError("scaling run did not verify every ray")
    metrics = {
        f"{stage}_{quantile}_ms": float(
            run[f"{stage}_{quantile}_ms"]
        )
        for stage in STAGES
        for quantile in QUANTILES
    }
    return {
        "source": "prefix-run",
        "rays": rays,
        "verified_segments": int(run["verified_segments"]),
        "batches": int(run["batches"]),
        "peak_batch_rays": int(run["peak_batch_rays"]),
        "peak_batch_segments": int(run["peak_batch_segments"]),
        "peak_segment_bytes": int(run["peak_segment_bytes"]),
        "warmup_passes": int(run["warmup_passes"]),
        "measured_passes": int(run["measured_passes"]),
        "parity_ms": float(run["parity_ms"]),
        "metrics": metrics,
        "submit_p95_rays_per_second": (
            rays * 1000.0 / metrics["total_p95_ms"]
        ),
        "kernel_p95_rays_per_second": (
            rays * 1000.0 / metrics["kernel_p95_ms"]
        ),
        "raw_run": run,
    }


def entry_from_formal(formal: dict[str, object]) -> dict[str, object]:
    if formal.get("status") != "valid" or not formal.get("cuda_executed"):
        raise ValueError("formal full-corpus report is not valid CUDA evidence")
    rays = int(formal["rays_per_pass"])
    formal_metrics = formal["metrics"]
    metrics = {
        field: float(formal_metrics[field]["median"])
        for field in (
            f"{stage}_{quantile}_ms"
            for stage in STAGES
            for quantile in QUANTILES
        )
    }
    return {
        "source": "formal-repeat-median",
        "rays": rays,
        "verified_segments": int(formal["verified_segments_per_repeat"]),
        "batches": int(formal["batches_per_pass"]),
        "peak_batch_rays": int(formal["peak_batch_rays"]),
        "peak_batch_segments": int(formal["peak_batch_segments"]),
        "peak_segment_bytes": int(formal["peak_segment_bytes"]),
        "warmup_passes": int(formal["warmup_passes_per_repeat"]),
        "measured_passes": int(formal["measured_passes_per_repeat"]),
        "repeats": int(formal["repeats"]),
        "metrics": metrics,
        "submit_p95_rays_per_second": (
            rays * 1000.0 / metrics["total_p95_ms"]
        ),
        "kernel_p95_rays_per_second": (
            rays * 1000.0 / metrics["kernel_p95_ms"]
        ),
    }


def run_prefix(
    runner: Path,
    bundle: Path,
    kernel: Path,
    *,
    ray_limit: int,
    device: int,
    warmup: int,
    iterations: int,
) -> dict[str, object]:
    completed = subprocess.run(
        [
            sys.executable,
            str(runner),
            "--bundle",
            str(bundle),
            "--kernel",
            str(kernel),
            "--ray-limit",
            str(ray_limit),
            "--device",
            str(device),
            "--warmup",
            str(warmup),
            "--iterations",
            str(iterations),
        ],
        check=True,
        capture_output=True,
        text=True,
        timeout=1800,
    )
    return json.loads(completed.stdout)


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
    parser.add_argument("--formal-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    parser.add_argument("--device", type=int, default=0)
    parser.add_argument("--warmup", type=int, default=3)
    parser.add_argument("--iterations", type=int, default=10)
    parser.add_argument(
        "--ray-limits",
        default="8192,16384,32768,65536,100008",
    )
    arguments = parser.parse_args()
    limits = [int(value) for value in arguments.ray_limits.split(",")]
    if (
        not limits
        or any(value < 1 for value in limits)
        or limits != sorted(set(limits))
    ):
        raise ValueError("--ray-limits must be unique ascending positives")
    if arguments.warmup < 1 or arguments.iterations < 2:
        raise ValueError("warmup and iterations are too small")

    formal_payload = arguments.formal_report.read_bytes()
    formal = json.loads(formal_payload)
    full_rays = int(formal["rays_per_pass"])
    if limits[-1] != full_rays:
        raise ValueError("last scaling limit must match formal full corpus")

    snapshots = [gpu_snapshot(arguments.device)]
    entries = []
    for limit in limits[:-1]:
        run = run_prefix(
            arguments.runner,
            arguments.bundle,
            arguments.kernel,
            ray_limit=limit,
            device=arguments.device,
            warmup=arguments.warmup,
            iterations=arguments.iterations,
        )
        if run.get("bundle_sha256") != formal.get("bundle_sha256"):
            raise ValueError("prefix run bundle identity changed")
        if int(run.get("rays", 0)) != limit:
            raise ValueError("prefix run ray limit changed")
        entries.append(entry_from_run(run))
    entries.append(entry_from_formal(formal))
    snapshots.append(gpu_snapshot(arguments.device))

    report = {
        "status": "valid",
        "schema": 1,
        "backend": "cuda-driver-nvrtc-prefix-scaling",
        "device": formal["device"],
        "compute_capability": formal["compute_capability"],
        "architecture": formal["architecture"],
        "bundle_sha256": formal["bundle_sha256"],
        "snapshot_sha256": formal["snapshot_sha256"],
        "formal_report_sha256": hashlib.sha256(formal_payload).hexdigest(),
        "prefix_warmup_passes": arguments.warmup,
        "prefix_measured_passes": arguments.iterations,
        "ray_limits": limits,
        "entries": entries,
        "gpu_snapshots": snapshots,
        "cuda_executed": True,
        "nvrtc_compiled": True,
        "nvcc_compiled": False,
        "claim_boundary": (
            "Prefix entries are single-process diagnostics with raw samples; "
            "only the 100008-ray entry is the median of the formal three-repeat "
            "benchmark. This matrix is not a Minecraft native bridge result."
        ),
    }
    write_atomic(arguments.output_json, report)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
