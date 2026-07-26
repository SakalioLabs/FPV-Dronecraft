#!/usr/bin/env python3
"""Independently verify D121f exact-coverage cache trajectories."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any


SOURCE_COUNTS = [1, 4, 8, 16]
FRAMES = 256


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def percentile(values: list[int], quantile: float) -> int:
    ordered = sorted(values)
    return ordered[math.ceil(quantile * len(ordered)) - 1]


def expected_schedule() -> dict[str, int]:
    inside = {frame for frame in range(FRAMES) if frame % 11 == 5}
    outside = {frame for frame in range(FRAMES) if frame % 7 == 3}
    bounds = {64, 128, 192}
    unload = {frame for frame in range(FRAMES) if frame % 53 == 17}
    reload = {frame for frame in range(FRAMES) if frame % 53 == 18}
    rebuild = {0} | inside | bounds | unload | reload
    return {
        "cache_hits": FRAMES - len(rebuild),
        "rebuilds": len(rebuild),
        "fallbacks": len(unload),
        "outside_dirty_events": len(outside),
        "inside_dirty_events": len(inside),
        "bounds_changes": len(bounds),
        "load_state_events": len(unload | reload),
    }


def verify_trajectory(case: dict[str, Any]) -> dict[str, bool]:
    expected = expected_schedule()
    elapsed = case["elapsed_ns"]
    return {
        "schedule_recomputed": all(
            case[name] == value for name, value in expected.items()
        ),
        "frame_accounting": case["frames"] == FRAMES
        and case["cache_hits"] + case["rebuilds"] == FRAMES,
        "raw_samples_present": len(elapsed) == FRAMES
        and all(value >= 0 for value in elapsed),
        "percentiles_recomputed": math.isclose(
            case["p50_ns"], percentile(elapsed, 0.50)
        )
        and math.isclose(case["p99_ns"], percentile(elapsed, 0.99)),
        "p99_below_10_ms": percentile(elapsed, 0.99) <= 10_000_000,
        "allocation_reported": math.isfinite(
            case["allocated_bytes_per_frame"]
        )
        and case["allocated_bytes_per_frame"] >= 0,
    }


def verify(report: dict[str, Any], contract_hash: str) -> dict[str, Any]:
    fixture = report["fixtures"]
    trajectories = {
        str(case["source_count"]): verify_trajectory(case)
        for case in report["trajectories"]
    }
    return {
        "contract_hash_matches": report["source_contract_sha256"]
        == contract_hash,
        "status_matches": report["status"]
        == "valid-coverage-aware-snapshot-cache-reference",
        "source_counts_exact": [
            case["source_count"] for case in report["trajectories"]
        ]
        == SOURCE_COUNTS,
        "outside_same_section_preserves_identity": fixture[
            "first_complete"
        ]
        and fixture["outside_cache_hit"]
        and fixture["outside_same_instance"],
        "inside_and_movement_rebuild": fixture["inside_rebuilt"]
        and fixture["moved_coverage_rebuilt"],
        "dirty_during_capture_discards_geometry": fixture[
            "dirty_during_capture"
        ]
        and not fixture["dirty_capture_complete"]
        and fixture["dirty_capture_published_boxes"] == 0,
        "bulk_unload_changes_overlap_token": fixture[
            "bulk_unload_token_before"
        ]
        == 0
        and fixture["bulk_unload_token_after"] > 0,
        "incomplete_never_cached": not fixture["unloaded_first_hit"]
        and not fixture["unloaded_second_hit"]
        and not fixture["unloaded_complete"],
        "trajectory_gates": trajectories,
        "all_trajectory_gates_pass": all(
            all(gates.values()) for gates in trajectories.values()
        ),
        "trajectory_cache_measured": report[
            "trajectory_cache_measured"
        ],
        "client_level_snapshot_producer_measured": report[
            "client_level_snapshot_producer_measured"
        ],
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
        "source_counts_exact",
        "outside_same_section_preserves_identity",
        "inside_and_movement_rebuild",
        "dirty_during_capture_discards_geometry",
        "bulk_unload_changes_overlap_token",
        "incomplete_never_cached",
        "all_trajectory_gates_pass",
        "trajectory_cache_measured",
    ]
    negative = [
        "client_level_snapshot_producer_measured",
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
        raise SystemExit(f"coverage cache verification failed: {gates}")
    result = {
        "schema_version": 1,
        "status": "verified-coverage-aware-snapshot-cache-reference",
        "source_report_sha256": sha256(args.report),
        "source_contract_sha256": sha256(args.contract),
        "gates": gates,
        "metrics": {
            "maximum_p99_ns": max(
                case["p99_ns"] for case in report["trajectories"]
            ),
            "source_16_cache_hits": report["trajectories"][-1][
                "cache_hits"
            ],
            "source_16_rebuilds": report["trajectories"][-1][
                "rebuilds"
            ],
            "source_16_fallbacks": report["trajectories"][-1][
                "fallbacks"
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
