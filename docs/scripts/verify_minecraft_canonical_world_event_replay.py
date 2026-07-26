#!/usr/bin/env python3
"""Independent D121n canonical event ordering and boundary verifier."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


NAMES = [
    "world-1-ready",
    "block-apply",
    "exact-duplicate",
    "conflicting-duplicate",
    "outside-coverage",
    "prediction-rollback",
    "out-of-order",
    "chunk-unload",
    "chunk-replace",
    "outside-chunk-load",
    "dimension-change",
    "stale-old-world",
    "new-world-block",
]
OUTCOMES = [
    "WORLD_REPLACE",
    "ACCEPTED_DIRTY",
    "DUPLICATE_IGNORED",
    "CONFLICTING_SEQUENCE_REJECTED",
    "ACCEPTED_OUTSIDE_COVERAGE",
    "ACCEPTED_DIRTY",
    "OUT_OF_ORDER_REJECTED",
    "ACCEPTED_DIRTY",
    "ACCEPTED_DIRTY",
    "ACCEPTED_OUTSIDE_COVERAGE",
    "WORLD_REPLACE",
    "WORLD_MISMATCH_REJECTED",
    "ACCEPTED_DIRTY",
]
EPOCHS = [1] * 10 + [2] * 3
SEQUENCES = [0, 1, 1, 1, 2, 3, 3, 4, 5, 6, 0, 0, 1]
REVISIONS = [0, 1, 1, 1, 1, 2, 2, 4098, 8194, 8194, 0, 0, 1]
TOKENS = REVISIONS
BOUNDARIES = [
    "captures_audio",
    "physical_endpoint_opened",
    "minecraft_client_started",
    "client_level_read",
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
    trace = report["trace"]
    trace_identity = (
        len(trace) == len(NAMES)
        and [item["index"] for item in trace] == list(range(len(NAMES)))
        and [item["name"] for item in trace] == NAMES
        and [item["outcome"] for item in trace] == OUTCOMES
    )
    trace_state = (
        [item["world_epoch"] for item in trace] == EPOCHS
        and [item["sequence"] for item in trace] == SEQUENCES
        and [item["revision"] for item in trace] == REVISIONS
        and [item["coverage_token"] for item in trace] == TOKENS
    )
    expected = contract["expected"]
    diagnostics = report["diagnostics"]
    diagnostics_match = all(
        diagnostics[name] == value for name, value in expected.items()
    ) and diagnostics["active_coverage_count"] == 1
    independently_recomputed = {
        "counts_match_contract": diagnostics_match,
        "duplicate_did_not_dirty": REVISIONS[2] == REVISIONS[1],
        "conflict_did_not_dirty": REVISIONS[3] == REVISIONS[2],
        "out_of_order_did_not_dirty": REVISIONS[6] == REVISIONS[5],
        "rollback_dirtied": REVISIONS[5] > REVISIONS[4],
        "chunk_replace_dirtied": REVISIONS[8] > REVISIONS[7],
        "world_replaced_tracker_identity": (
            REVISIONS[9] > 0 and REVISIONS[10] == 0
        ),
        "stale_world_did_not_dirty": REVISIONS[11] == REVISIONS[10],
        "final_state_matches_contract": (
            diagnostics["world_epoch"] == 2
            and diagnostics["last_sequence"] == 1
            and diagnostics["final_tracker_revision"] == 1
        ),
    }
    reported_gates_match = (
        report["gates"] == independently_recomputed
        and all(independently_recomputed.values())
    )
    adapter_markers = [
        "ClientChunkEvents.CHUNK_LOAD.register",
        "ClientChunkEvents.CHUNK_UNLOAD.register",
        "ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register",
        "CanonicalAcousticWorldEventPort PORT",
        "PORT.acceptBlock(",
        "PORT.acceptChunk(",
        "PORT.replaceWorld(worldEpoch)",
        "nextSequence()",
    ]
    adapter_uses_port = all(
        marker in adapter_source for marker in adapter_markers
    ) and "tracker.markDirty(" not in adapter_source
    return {
        "contract_hash_matches": (
            report["source_contract_sha256"] == contract_hash
        ),
        "identity_matches": (
            report["status"] == "valid-canonical-world-event-replay"
        ),
        "trace_identity_matches": trace_identity,
        "trace_state_recomputed": trace_state,
        "diagnostics_match_contract": diagnostics_match,
        "reported_gates_recomputed": reported_gates_match,
        "fabric_adapter_uses_canonical_port": adapter_uses_port,
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
        "trace_identity_matches",
        "trace_state_recomputed",
        "diagnostics_match_contract",
        "reported_gates_recomputed",
        "fabric_adapter_uses_canonical_port",
    ]
    if not all(gates[name] for name in positive) or any(
        gates[name] for name in BOUNDARIES
    ):
        raise SystemExit(f"canonical world-event replay failed: {gates}")
    result = {
        "schema_version": 1,
        "status": "verified-minecraft-canonical-world-event-replay",
        "source_report_sha256": sha256(args.report),
        "source_contract_sha256": sha256(args.contract),
        "source_adapter_sha256": sha256(args.adapter_source),
        "gates": gates,
        "metrics": report["diagnostics"],
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
