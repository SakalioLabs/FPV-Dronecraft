#!/usr/bin/env python3
"""Independently verify D121c worker timelines, budgets and generations."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import statistics
from pathlib import Path
from typing import Any


SOURCE_COUNTS = [1, 4, 8, 16]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def percentile(values: list[int], quantile: float) -> int:
    ordered = sorted(values)
    index = math.ceil(quantile * len(ordered)) - 1
    return ordered[max(0, min(len(ordered) - 1, index))]


def close(left: float, right: float) -> bool:
    return math.isclose(left, right, rel_tol=1.0e-12, abs_tol=1.0e-9)


def verify_benchmark(entry: dict[str, Any]) -> dict[str, bool]:
    count = entry["source_count"]
    frames = entry["measured_frames"]
    samples = entry["samples"]
    batch = samples["batch_ns"]
    queue = samples["queue_ns"]
    solve = samples["solve_ns"]
    publish = samples["publish_ns"]
    end_to_end = samples["end_to_end_ns"]
    apply = samples["apply_ns"]
    scene_samples = frames * count
    lengths = (
        len(batch) == frames
        and len(queue) == scene_samples
        and len(solve) == scene_samples
        and len(publish) == scene_samples
        and len(end_to_end) == scene_samples
        and len(apply) == scene_samples
    )
    nonnegative = all(
        value >= 0
        for group in [batch, queue, solve, publish, end_to_end, apply]
        for value in group
    )
    timeline = all(
        end_to_end[index]
        >= queue[index] + solve[index] + publish[index]
        for index in range(scene_samples)
    )
    aggregates = (
        close(entry["p50_batch_ns"], percentile(batch, 0.50))
        and close(entry["p95_batch_ns"], percentile(batch, 0.95))
        and close(entry["p99_batch_ns"], percentile(batch, 0.99))
        and close(entry["p99_queue_ns"], percentile(queue, 0.99))
        and close(entry["p99_solve_ns"], percentile(solve, 0.99))
        and close(entry["p99_publish_ns"], percentile(publish, 0.99))
        and close(entry["p99_end_to_end_ns"], percentile(end_to_end, 0.99))
        and close(entry["p99_apply_ns"], percentile(apply, 0.99))
    )
    budgets = (
        percentile(batch, 0.99) <= 25_000_000
        and percentile(solve, 0.99) <= 100_000
        and percentile(apply, 0.99) <= 100_000
    )
    return {
        "sample_lengths_match": lengths,
        "durations_nonnegative": nonnegative,
        "timeline_decomposition_valid": timeline,
        "reported_percentiles_recomputed": aggregates,
        "timing_budgets_pass": budgets,
    }


def verify(report: dict[str, Any], contract_hash: str) -> dict[str, Any]:
    functional = report["functional"]
    pending = functional["pending_overwrite"]
    stale = functional["stale_solve"]
    incomplete = functional["incomplete"]
    benchmarks = report["source_count_benchmarks"]
    benchmark_gates = {
        str(entry["source_count"]): verify_benchmark(entry)
        for entry in benchmarks
    }
    allocation = report["allocation"]
    submit_windows = allocation["submit_windows_bytes"]
    submit_poll_windows = allocation["submit_poll_windows_bytes"]
    operations = allocation["operations_per_window"]
    allocations_match = (
        len(submit_windows) == 5
        and len(submit_poll_windows) == 5
        and close(
            allocation["submit_median_bytes_per_operation"],
            statistics.median(submit_windows) / operations,
        )
        and close(
            allocation["submit_poll_median_bytes_per_operation"],
            statistics.median(submit_poll_windows) / operations,
        )
    )
    return {
        "contract_hash_matches": report["source_contract_sha256"]
        == contract_hash,
        "status_matches": report["status"]
        == "valid-minecraft-local-plane-worker-handoff-reference",
        "pending_latest_generation_wins": pending["published_generation"] == 2
        and pending["pending_overwrites"] >= 1,
        "stale_solved_result_discarded": stale["published_generation"] == 2
        and stale["stale_solved_results"] >= 1,
        "incomplete_has_no_early_paths": incomplete
        == {"conservative_fallback": True, "selected_count": 0},
        "source_counts_exact": [entry["source_count"] for entry in benchmarks]
        == SOURCE_COUNTS,
        "benchmark_gates": benchmark_gates,
        "all_benchmark_gates_pass": all(
            all(gates.values()) for gates in benchmark_gates.values()
        ),
        "allocation_aggregates_recomputed": allocations_match,
        "zero_allocation_windows": all(value == 0 for value in submit_windows)
        and all(value == 0 for value in submit_poll_windows),
        "worker_handoff_measured": report["worker_handoff_measured"],
        "captures_audio": report["captures_audio"],
        "physical_endpoint_opened": report["physical_endpoint_opened"],
        "minecraft_client_started": report["minecraft_client_started"],
        "cuda_executed": report["cuda_executed"],
        "minecraft_integration_enabled": report["minecraft_integration_enabled"],
        "live_early_renderer_enabled": report["live_early_renderer_enabled"],
        "release_calibrated": report["release_calibrated"],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    gates = verify(report, sha256(args.contract))
    positive = [
        "contract_hash_matches",
        "status_matches",
        "pending_latest_generation_wins",
        "stale_solved_result_discarded",
        "incomplete_has_no_early_paths",
        "source_counts_exact",
        "all_benchmark_gates_pass",
        "allocation_aggregates_recomputed",
        "zero_allocation_windows",
        "worker_handoff_measured",
    ]
    negative = [
        "captures_audio",
        "physical_endpoint_opened",
        "minecraft_client_started",
        "cuda_executed",
        "minecraft_integration_enabled",
        "live_early_renderer_enabled",
        "release_calibrated",
    ]
    if not all(gates[name] for name in positive) or any(
        gates[name] for name in negative
    ):
        raise SystemExit(f"worker handoff verification failed: {gates}")
    result = {
        "schema_version": 1,
        "status": "verified-minecraft-local-plane-worker-handoff-reference",
        "source_report_sha256": sha256(args.report),
        "source_contract_sha256": sha256(args.contract),
        "gates": gates,
        "metrics": {
            "source_counts": SOURCE_COUNTS,
            "p99_batch_ns": {
                str(entry["source_count"]): entry["p99_batch_ns"]
                for entry in report["source_count_benchmarks"]
            },
            "p99_solve_ns": {
                str(entry["source_count"]): entry["p99_solve_ns"]
                for entry in report["source_count_benchmarks"]
            },
            "p99_apply_ns": {
                str(entry["source_count"]): entry["p99_apply_ns"]
                for entry in report["source_count_benchmarks"]
            },
            "submit_windows_bytes": report["allocation"][
                "submit_windows_bytes"
            ],
            "submit_poll_windows_bytes": report["allocation"][
                "submit_poll_windows_bytes"
            ],
        },
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
