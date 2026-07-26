#!/usr/bin/env python3
"""Independently verify D121i null early-renderer evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def percentile(values: list[float], quantile: float) -> float:
    ordered = sorted(values)
    return ordered[math.ceil(quantile * len(ordered)) - 1]


def verify(report: dict[str, Any], contract_hash: str) -> dict[str, bool]:
    fixture = report["fixture"]
    benchmark = report["benchmark"]
    elapsed = benchmark["elapsed_ns_per_frame"]
    pan = math.sqrt(0.5)
    return {
        "contract_hash_matches": report["source_contract_sha256"]
        == contract_hash,
        "status_matches": report["status"]
        == "valid-null-early-reflection-renderer-reference",
        "integer_impulse_exact": fixture["integer_peak_index"] == 4
        and math.isclose(
            fixture["integer_peak_value"], pan, abs_tol=1.0e-10
        ),
        "fractional_impulse_exact": fixture[
            "fractional_first_index"
        ]
        == 4
        and fixture["fractional_second_index"] == 5
        and math.isclose(
            fixture["fractional_first_value"],
            pan * 0.5,
            abs_tol=1.0e-10,
        )
        and math.isclose(
            fixture["fractional_second_value"],
            pan * 0.5,
            abs_tol=1.0e-10,
        ),
        "moving_continuity": 0.0
        <= fixture["moving_maximum_step"]
        <= 0.25,
        "topology_continuity": 0.0
        <= fixture["topology_maximum_step"]
        <= 0.25,
        "all_outputs_finite": fixture["all_outputs_finite"],
        "over_budget_rejected": fixture["over_budget_rejected"],
        "raw_timing_samples_present": benchmark["timing_blocks"] == 10_000
        and benchmark["block_size"] == 256
        and benchmark["active_slots"] == 12
        and len(elapsed) == 10_000
        and all(math.isfinite(value) and value >= 0 for value in elapsed),
        "percentiles_recomputed": math.isclose(
            benchmark["p50_ns_per_frame"], percentile(elapsed, 0.50)
        )
        and math.isclose(
            benchmark["p99_ns_per_frame"], percentile(elapsed, 0.99)
        ),
        "p99_below_500_ns_per_frame": percentile(elapsed, 0.99)
        <= 500.0,
        "five_zero_allocation_windows": benchmark[
            "allocation_windows_bytes"
        ]
        == [0, 0, 0, 0, 0],
        "checksum_finite_nonzero": math.isfinite(benchmark["checksum"])
        and benchmark["checksum"] != 0.0,
        "captures_audio": report["captures_audio"],
        "physical_endpoint_opened": report["physical_endpoint_opened"],
        "minecraft_client_started": report["minecraft_client_started"],
        "client_level_read": report["client_level_read"],
        "cuda_executed": report["cuda_executed"],
        "minecraft_integration_enabled": report[
            "minecraft_integration_enabled"
        ],
        "live_early_renderer_enabled": report[
            "live_early_renderer_enabled"
        ],
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
        "integer_impulse_exact",
        "fractional_impulse_exact",
        "moving_continuity",
        "topology_continuity",
        "all_outputs_finite",
        "over_budget_rejected",
        "raw_timing_samples_present",
        "percentiles_recomputed",
        "p99_below_500_ns_per_frame",
        "five_zero_allocation_windows",
        "checksum_finite_nonzero",
    ]
    negative = [
        "captures_audio",
        "physical_endpoint_opened",
        "minecraft_client_started",
        "client_level_read",
        "cuda_executed",
        "minecraft_integration_enabled",
        "live_early_renderer_enabled",
        "release_calibrated",
    ]
    if not all(gates[name] for name in positive) or any(
        gates[name] for name in negative
    ):
        raise SystemExit(f"null renderer verification failed: {gates}")
    result = {
        "schema_version": 1,
        "status": "verified-null-early-reflection-renderer-reference",
        "source_report_sha256": sha256(args.report),
        "source_contract_sha256": sha256(args.contract),
        "gates": gates,
        "metrics": {
            "p50_ns_per_frame": report["benchmark"][
                "p50_ns_per_frame"
            ],
            "p99_ns_per_frame": report["benchmark"][
                "p99_ns_per_frame"
            ],
            "moving_maximum_step": report["fixture"][
                "moving_maximum_step"
            ],
            "topology_maximum_step": report["fixture"][
                "topology_maximum_step"
            ],
        },
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
