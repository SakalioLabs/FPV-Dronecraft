#!/usr/bin/env python3
"""Independently verify D121h cluster-slew fixtures and timing."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def percentile(values: list[int], quantile: float) -> int:
    ordered = sorted(values)
    return ordered[math.ceil(quantile * len(ordered)) - 1]


def verify(report: dict[str, Any], contract_hash: str) -> dict[str, bool]:
    fixture = report["fixture"]
    benchmark = report["benchmark"]
    elapsed = benchmark["elapsed_ns"]
    allocations = benchmark["allocation_windows_bytes"]
    gates = report["gates"]
    return {
        "contract_hash_matches": report["source_contract_sha256"]
        == contract_hash,
        "status_matches": report["status"]
        == "valid-early-reflection-cluster-slew-reference",
        "energy_to_amplitude_recomputed": math.isclose(
            fixture["fade_in_target_mid"], math.sqrt(0.36)
        ),
        "nearby_delay_fixture": fixture["nearby_target_delay"] == 150.0,
        "topology_crossfade_fixture": fixture[
            "replacement_target_delay"
        ]
        == 400.0
        and fixture["replacement_slot_count"] == 2,
        "retirement_fixture": fixture["retired_slot_count"] == 0,
        "ramp_is_50_ms_at_48khz": fixture["ramp_samples"] == 2400,
        "java_fixture_gates": gates["fade_in"]
        and gates["nearby_same_slot"]
        and gates["topology_crossfade"]
        and gates["incomplete_fade_and_retire"],
        "raw_samples_present": benchmark["iterations"] == 100_000
        and len(elapsed) == 100_000
        and all(value >= 0 for value in elapsed),
        "percentiles_recomputed": math.isclose(
            benchmark["p50_ns"], percentile(elapsed, 0.50)
        )
        and math.isclose(
            benchmark["p99_ns"], percentile(elapsed, 0.99)
        ),
        "p99_below_10_us": percentile(elapsed, 0.99) <= 10_000,
        "five_zero_allocation_windows": allocations == [0, 0, 0, 0, 0],
        "checksum_positive": benchmark["checksum"] > 0,
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
        "energy_to_amplitude_recomputed",
        "nearby_delay_fixture",
        "topology_crossfade_fixture",
        "retirement_fixture",
        "ramp_is_50_ms_at_48khz",
        "java_fixture_gates",
        "raw_samples_present",
        "percentiles_recomputed",
        "p99_below_10_us",
        "five_zero_allocation_windows",
        "checksum_positive",
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
        raise SystemExit(f"cluster slew verification failed: {gates}")
    result = {
        "schema_version": 1,
        "status": "verified-early-reflection-cluster-slew-reference",
        "source_report_sha256": sha256(args.report),
        "source_contract_sha256": sha256(args.contract),
        "gates": gates,
        "metrics": {
            "p50_ns": report["benchmark"]["p50_ns"],
            "p99_ns": report["benchmark"]["p99_ns"],
            "iterations": report["benchmark"]["iterations"],
            "ramp_samples": report["fixture"]["ramp_samples"],
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
