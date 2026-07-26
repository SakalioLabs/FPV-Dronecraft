#!/usr/bin/env python3
"""Independent D121o bounded native callback trace verifier."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


EXPECTED = [
    (5, 1, 4, "CHUNK_UNLOAD", "ACCEPTED_DIRTY", [0, 0, 0, 15, 0, 15]),
    (
        6,
        1,
        5,
        "CHUNK_REPLACE",
        "ACCEPTED_DIRTY",
        [16, 0, 16, 31, 0, 31],
    ),
    (
        7,
        1,
        5,
        "CHUNK_REPLACE",
        "DUPLICATE_IGNORED",
        [16, 0, 16, 31, 0, 31],
    ),
    (
        8,
        1,
        5,
        "BLOCK_APPLY",
        "CONFLICTING_SEQUENCE_REJECTED",
        [5, 2, 4, 5, 2, 4],
    ),
    (
        9,
        1,
        4,
        "CHUNK_LOAD",
        "OUT_OF_ORDER_REJECTED",
        [48, 0, 48, 63, 0, 63],
    ),
    (10, 2, 0, "WORLD_REPLACE", "WORLD_REPLACED", [0, 0, 0, 0, 0, 0]),
    (
        11,
        2,
        0,
        "BLOCK_APPLY",
        "ADAPTER_WORLD_MISMATCH_REJECTED",
        [1, 1, 1, 1, 1, 1],
    ),
    (12, 2, 1, "BLOCK_APPLY", "ACCEPTED_DIRTY", [1, 1, 1, 1, 1, 1]),
]
BOUNDARIES = [
    "captures_audio",
    "physical_endpoint_opened",
    "minecraft_client_started",
    "client_level_read",
    "native_callback_delivery_measured",
    "cuda_executed",
    "release_calibrated",
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(
    report: dict[str, Any],
    contract: dict[str, Any],
    contract_hash: str,
    adapter_source: str,
) -> dict[str, bool]:
    entries = report["retained_entries"]
    expected_fields = [
        (
            item["ordinal"],
            item["active_world_epoch"],
            item["local_sequence"],
            item["kind"],
            item["disposition"],
            item["bounds"],
        )
        for item in entries
    ]
    retained_matches = expected_fields == EXPECTED
    thread_ids = [item["thread_id"] for item in entries]
    one_thread = (
        bool(thread_ids)
        and all(value > 0 for value in thread_ids)
        and len(set(thread_ids)) == 1
        and thread_ids[0] == report["producer_thread_id"]
    )
    counters_match = (
        report["capacity"] == contract["reference_capacity"] == 8
        and report["recorded"] == contract["reference_record_count"] == 12
        and report["retained"] == contract["reference_retained_count"] == 8
        and report["overwritten"]
        == contract["reference_overwritten_count"]
        == 4
    )
    disabled = report["disabled_probe"]
    disabled_noop = (
        disabled
        == {
            "enabled": False,
            "recorded": 0,
            "retained": 0,
            "overwritten": 0,
        }
    )
    allocations = report["steady_record_allocation_windows_bytes"]
    zero_allocation = (
        allocations
        == contract["gates"]["steady_record_allocation_windows_bytes"]
        == [0, 0, 0, 0, 0]
    )
    independently_recomputed = {
        "capacity_bounded": report["capacity"] == report["retained"] == 8,
        "overwrites_oldest": (
            report["recorded"] == 12
            and report["overwritten"] == 4
            and entries[0]["ordinal"] == 5
            and entries[-1]["ordinal"] == 12
        ),
        "chronological_export": (
            [item["ordinal"] for item in entries] == list(range(5, 13))
        ),
        "one_producer_thread": one_thread,
        "disabled_is_strict_noop": disabled_noop,
        "zero_steady_record_allocation": zero_allocation,
    }
    adapter_markers = [
        "NATIVE_EVENT_TRACE_CAPACITY = 256",
        'Boolean.getBoolean(',
        '"fpvdrone.acoustics.nativeEventTrace"',
        "NATIVE_EVENT_TRACE.enabled()",
        "Thread.currentThread().threadId()",
        "copyNativeEventTrace(",
        "ADAPTER_WORLD_MISMATCH_REJECTED",
    ]
    adapter_contract = all(
        marker in adapter_source for marker in adapter_markers
    )
    return {
        "contract_hash_matches": (
            report["source_contract_sha256"] == contract_hash
        ),
        "identity_matches": (
            report["status"] == "valid-native-world-event-trace-replay"
            and report["trace_enabled"] is True
        ),
        "retained_entries_recomputed": retained_matches,
        "counters_match_contract": counters_match,
        "single_thread_recomputed": one_thread,
        "disabled_noop_recomputed": disabled_noop,
        "zero_allocation_recomputed": zero_allocation,
        "reported_gates_recomputed": (
            report["gates"] == independently_recomputed
            and all(independently_recomputed.values())
        ),
        "fabric_adapter_trace_contract_present": adapter_contract,
        **{boundary: bool(report[boundary]) for boundary in BOUNDARIES},
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--adapter-source", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    contract = json.loads(args.contract.read_text(encoding="utf-8"))
    gates = verify(
        report,
        contract,
        sha256(args.contract),
        args.adapter_source.read_text(encoding="utf-8"),
    )
    positive = [
        "contract_hash_matches",
        "identity_matches",
        "retained_entries_recomputed",
        "counters_match_contract",
        "single_thread_recomputed",
        "disabled_noop_recomputed",
        "zero_allocation_recomputed",
        "reported_gates_recomputed",
        "fabric_adapter_trace_contract_present",
    ]
    if not all(gates[name] for name in positive) or any(
        gates[name] for name in BOUNDARIES
    ):
        raise SystemExit(f"native world-event trace failed: {gates}")
    result = {
        "schema_version": 1,
        "status": "verified-minecraft-native-world-event-trace",
        "source_report_sha256": sha256(args.report),
        "source_contract_sha256": sha256(args.contract),
        "source_adapter_sha256": sha256(args.adapter_source),
        "gates": gates,
        "metrics": {
            "recorded": report["recorded"],
            "retained": report["retained"],
            "overwritten": report["overwritten"],
            "allocation_windows": report[
                "steady_record_allocation_windows_bytes"
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
