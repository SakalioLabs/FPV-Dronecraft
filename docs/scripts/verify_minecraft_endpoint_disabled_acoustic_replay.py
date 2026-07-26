#!/usr/bin/env python3
"""Independent D121m event, wet-bus and boundary verifier."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any


BLOCK_SIZE = 256
EVENTS = [
    "initial-capture",
    "outside-coverage-block-update",
    "inside-coverage-block-update",
    "chunk-unload",
    "chunk-reload",
    "block-update-during-capture",
    "stable-retry",
    "no-world-change",
    "world-change",
]
BOUNDARIES = [
    "captures_audio",
    "physical_endpoint_opened",
    "minecraft_client_started",
    "client_level_read",
    "cuda_executed",
    "minecraft_integration_enabled",
    "live_early_renderer_enabled",
    "release_calibrated",
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def close(actual: float, expected: float) -> bool:
    return math.isclose(
        actual, expected, rel_tol=1.0e-12, abs_tol=1.0e-12
    )


def continuity(left: list[float], right: list[float]) -> float:
    maximum = 0.0
    for index in range(1, len(left)):
        maximum = max(maximum, abs(left[index] - left[index - 1]))
        maximum = max(maximum, abs(right[index] - right[index - 1]))
    return maximum


def block_rms(
    left: list[float], right: list[float], tick: int
) -> float:
    start = tick * BLOCK_SIZE
    energy = sum(
        left[index] * left[index] + right[index] * right[index]
        for index in range(start, start + BLOCK_SIZE)
    )
    return math.sqrt(energy / (2.0 * BLOCK_SIZE))


def checksum(left: list[float], right: list[float]) -> float:
    return sum(
        left[index] + right[index] for index in range(0, len(left), 17)
    )


def verify(
    report: dict[str, Any],
    contract: dict[str, Any],
    d121k: dict[str, Any],
    d121l: dict[str, Any],
    contract_hash: str,
) -> dict[str, Any]:
    events = report["events"]
    left = report["wet_left"]
    right = report["wet_right"]
    event_identity = (
        len(events) == len(EVENTS)
        and [event["tick"] for event in events] == list(range(len(EVENTS)))
        and [event["event"] for event in events] == EVENTS
    )
    expected_hits = [False, True, False, False, False, False, False, True, False]
    expected_complete = [True, True, True, False, True, False, True, True, True]
    expected_dirty_capture = [
        False,
        False,
        False,
        False,
        False,
        True,
        False,
        False,
        False,
    ]
    event_patterns = event_identity and all(
        event["cache_hit"] == expected_hits[index]
        and event["snapshot_complete"] == expected_complete[index]
        and event["worker_complete"] == expected_complete[index]
        and event["conservative_fallback"]
        == (not expected_complete[index])
        and event["dirty_during_capture"]
        == expected_dirty_capture[index]
        and (
            event["patch_count"] > 0
            and event["cluster_count"] > 0
            if expected_complete[index]
            else event["patch_count"] == 0
            and event["cluster_count"] == 0
        )
        for index, event in enumerate(events)
    )
    raw_wet = (
        len(left) == len(EVENTS) * BLOCK_SIZE
        and len(right) == len(left)
        and all(math.isfinite(value) for value in left + right)
    )
    recomputed_step = continuity(left, right)
    event_wet_metrics = raw_wet and all(
        close(event["wet_rms"], block_rms(left, right, index))
        and close(
            event["last_wet_magnitude"],
            abs(left[(index + 1) * BLOCK_SIZE - 1])
            + abs(right[(index + 1) * BLOCK_SIZE - 1]),
        )
        for index, event in enumerate(events)
    )
    cache_hits = sum(event["cache_hit"] for event in events)
    rebuilds = len(events) - cache_hits
    fallbacks = sum(event["conservative_fallback"] for event in events)
    complete_clusters = sum(
        event["worker_complete"] and event["cluster_count"] > 0
        for event in events
    )
    dirty_discards = sum(
        event["dirty_during_capture"] for event in events
    )
    incomplete_zero = all(
        event["last_wet_magnitude"] <= 1.0e-15
        for event in events
        if event["conservative_fallback"]
    )
    metrics = report["metrics"]
    latest = report["latest_generation_probe"]
    count_metrics = (
        metrics["trace_event_count"] == len(events)
        and metrics["cache_hits"] == cache_hits == 2
        and metrics["rebuilds"] == rebuilds == 7
        and metrics["incomplete_fallbacks"] == fallbacks == 2
        and metrics["complete_results_with_clusters"]
        == complete_clusters
        == 7
        and metrics["dirty_during_capture_discards"]
        == dirty_discards
        == 1
        and metrics["energy_endpoint_checks_passed"] == len(events)
    )
    wet_metrics = (
        close(metrics["maximum_adjacent_wet_step"], recomputed_step)
        and recomputed_step <= contract["gates"][
            "maximum_adjacent_wet_step"
        ]
        and metrics["all_wet_outputs_finite"]
        and metrics["incomplete_fades_reached_zero"]
        == incomplete_zero
        and incomplete_zero
        and close(metrics["wet_checksum"], checksum(left, right))
        and abs(metrics["wet_checksum"]) > 1.0e-12
    )
    allocation_zero = report[
        "steady_renderer_allocation_windows_bytes"
    ] == [0, 0, 0, 0, 0]
    latest_only = (
        latest["applied_generation"] == 2
        and latest["pending_overwrites"] >= 1
        and latest["published_results"] == 1
    )
    reported_gates = report["gates"]
    return {
        "contract_hash_matches": report["source_contract_sha256"]
        == contract_hash,
        "identity_matches": report["status"]
        == "valid-endpoint-disabled-acoustic-replay"
        and report["renderer_contract_version"] == 2
        and report["renderer_interpolation"]
        == "HIGH_BAND_KAISER_SINC8"
        and report["block_size"] == BLOCK_SIZE,
        "event_identity_matches": event_identity,
        "event_patterns_match": event_patterns,
        "raw_wet_present_and_finite": raw_wet,
        "event_wet_metrics_recomputed": event_wet_metrics,
        "count_metrics_recomputed": count_metrics,
        "wet_metrics_recomputed": wet_metrics,
        "latest_generation_only": latest_only,
        "zero_renderer_allocation": allocation_zero,
        "reported_gates_match": all(reported_gates.values())
        and reported_gates["trace_counts_match"] == count_metrics
        and reported_gates["latest_generation_only"] == latest_only
        and reported_gates["zero_renderer_allocation"]
        == allocation_zero,
        "prior_d121k_status_matches": d121k["status"]
        == contract["required_prior_statuses"]["d121k"],
        "prior_d121l_status_matches": d121l["status"]
        == contract["required_prior_statuses"]["d121l"],
        **{boundary: report[boundary] for boundary in BOUNDARIES},
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--d121k-verification", type=Path, required=True)
    parser.add_argument("--d121l-verification", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    contract = json.loads(args.contract.read_text(encoding="utf-8"))
    d121k = json.loads(
        args.d121k_verification.read_text(encoding="utf-8")
    )
    d121l = json.loads(
        args.d121l_verification.read_text(encoding="utf-8")
    )
    gates = verify(
        report,
        contract,
        d121k,
        d121l,
        sha256(args.contract),
    )
    positive = [
        "contract_hash_matches",
        "identity_matches",
        "event_identity_matches",
        "event_patterns_match",
        "raw_wet_present_and_finite",
        "event_wet_metrics_recomputed",
        "count_metrics_recomputed",
        "wet_metrics_recomputed",
        "latest_generation_only",
        "zero_renderer_allocation",
        "reported_gates_match",
        "prior_d121k_status_matches",
        "prior_d121l_status_matches",
    ]
    if not all(gates[name] for name in positive) or any(
        gates[name] for name in BOUNDARIES
    ):
        raise SystemExit(f"endpoint-disabled replay failed: {gates}")
    result = {
        "schema_version": 1,
        "status": "verified-minecraft-endpoint-disabled-acoustic-replay",
        "source_report_sha256": sha256(args.report),
        "source_contract_sha256": sha256(args.contract),
        "source_d121k_verification_sha256": sha256(
            args.d121k_verification
        ),
        "source_d121l_verification_sha256": sha256(
            args.d121l_verification
        ),
        "gates": gates,
        "metrics": {
            "maximum_adjacent_wet_step": report["metrics"][
                "maximum_adjacent_wet_step"
            ],
            "wet_checksum": report["metrics"]["wet_checksum"],
            "latest_generation": report["latest_generation_probe"][
                "applied_generation"
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
