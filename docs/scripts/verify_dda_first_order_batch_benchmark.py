#!/usr/bin/env python3
"""Verify D117 reusable first-order DDA parity and hot-path budget."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def enforce(report: dict[str, Any], d117_sha: str) -> None:
    parity = report["parity"]
    benchmark = report["benchmark"]
    gates = report["gates"]
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "valid-dda-first-order-batch-benchmark"
        or report.get("source_d117_report_sha256") != d117_sha
        or parity["pairs"] != 6
        or parity["paths"] != 42
        or parity["maximum_length_error_m"] > 1.0e-12
        or parity["maximum_reflection_point_error_m"] > 1.0e-12
        or parity["visited_cell_mismatches"] != 0
        or parity["visibility_mismatches"] != 0
    ):
        raise ValueError("D117 batch parity or source binding changed")
    allocation_windows = benchmark["allocation_windows_bytes"]
    if (
        benchmark["paths_per_scenario"] != 7
        or benchmark["allocation_scenarios_per_window"] < 100_000
        or len(allocation_windows) < 5
        or any(value != 0 for value in allocation_windows)
        or benchmark["minimum_allocated_bytes"] != 0
        or benchmark["median_allocated_bytes"] != 0
        or benchmark["maximum_allocated_bytes"] != 0
        or benchmark["minimum_allocated_bytes_per_scenario"] != 0.0
        or benchmark["latency_batches"] < 300
        or benchmark["p99_ns_per_scenario"] > 50_000.0
        or gates["object_reference_parity"] is not True
        or gates["zero_allocation_hot_path"] is not True
        or gates["p99_below_50_microseconds"] is not True
    ):
        raise ValueError("D117 batch allocation or latency gate changed")
    if (
        report["captures_audio"] is not False
        or report["physical_endpoint_opened"] is not False
        or report["cuda_executed"] is not False
        or report["release_calibrated"] is not False
    ):
        raise ValueError("D117 batch claim boundary changed")


def verify(
    report: dict[str, Any],
    report_sha: str,
    d117_path: Path,
) -> dict[str, Any]:
    enforce(report, sha256(d117_path))
    parity = report["parity"]
    benchmark = report["benchmark"]
    return {
        "schema_version": 1,
        "status": "verified-dda-first-order-batch-benchmark",
        "source_report_sha256": report_sha,
        "gates": {
            "java_object_reference_parity": True,
            "forty_two_paths_exact": True,
            "five_zero_allocation_windows": True,
            "local_p99_below_fifty_microseconds": True,
            "minecraft_integration_measured": False,
            "cuda_executed": False,
            "physical_endpoint_opened": False,
            "captures_audio": False,
            "release_calibrated": False,
        },
        "metrics": {
            "parity_paths": parity["paths"],
            "allocation_windows_bytes": benchmark[
                "allocation_windows_bytes"
            ],
            "p50_ns_per_scenario": benchmark["p50_ns_per_scenario"],
            "p95_ns_per_scenario": benchmark["p95_ns_per_scenario"],
            "p99_ns_per_scenario": benchmark["p99_ns_per_scenario"],
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--d117-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    result = verify(report, sha256(args.report), args.d117_report)
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
