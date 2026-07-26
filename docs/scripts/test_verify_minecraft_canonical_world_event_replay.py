from __future__ import annotations

import copy
import importlib.util
import json
from pathlib import Path


SCRIPT = Path(__file__).with_name(
    "verify_minecraft_canonical_world_event_replay.py"
)
SPEC = importlib.util.spec_from_file_location("d121n_verifier", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def fixture() -> tuple[dict, dict, str]:
    contract = {
        "expected": {
            "world_replacements": 2,
            "accepted_events": 7,
            "dirty_events": 5,
            "duplicate_events": 1,
            "conflicting_sequence_events": 1,
            "out_of_order_events": 1,
            "world_mismatch_events": 1,
            "marked_cells": 8195,
            "world_epoch": 2,
            "last_sequence": 1,
            "final_tracker_revision": 1,
        }
    }
    trace = [
        {
            "index": index,
            "name": MODULE.NAMES[index],
            "outcome": MODULE.OUTCOMES[index],
            "world_epoch": MODULE.EPOCHS[index],
            "sequence": MODULE.SEQUENCES[index],
            "revision": MODULE.REVISIONS[index],
            "coverage_token": MODULE.TOKENS[index],
        }
        for index in range(len(MODULE.NAMES))
    ]
    diagnostics = {
        **contract["expected"],
        "active_coverage_count": 1,
    }
    report = {
        "status": "valid-canonical-world-event-replay",
        "source_contract_sha256": "contract-hash",
        "trace": trace,
        "diagnostics": diagnostics,
        "gates": {
            "counts_match_contract": True,
            "duplicate_did_not_dirty": True,
            "conflict_did_not_dirty": True,
            "out_of_order_did_not_dirty": True,
            "rollback_dirtied": True,
            "chunk_replace_dirtied": True,
            "world_replaced_tracker_identity": True,
            "stale_world_did_not_dirty": True,
            "final_state_matches_contract": True,
        },
        **{boundary: False for boundary in MODULE.BOUNDARIES},
    }
    adapter = "\n".join(
        [
            "ClientChunkEvents.CHUNK_LOAD.register",
            "ClientChunkEvents.CHUNK_UNLOAD.register",
            "ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register",
            "CanonicalAcousticWorldEventPort PORT",
            "PORT.acceptBlock(",
            "PORT.acceptChunk(",
            "PORT.replaceWorld(worldEpoch)",
            "nextSequence()",
        ]
    )
    return report, contract, adapter


def test_accepts_consistent_fixture() -> None:
    report, contract, adapter = fixture()
    gates = MODULE.verify(
        report, contract, "contract-hash", adapter
    )
    assert all(
        value is False if name in MODULE.BOUNDARIES else value is True
        for name, value in gates.items()
    )


def test_rejects_false_duplicate_evidence() -> None:
    report, contract, adapter = fixture()
    tampered = copy.deepcopy(report)
    tampered["trace"][2]["revision"] = 2
    gates = MODULE.verify(
        tampered, contract, "contract-hash", adapter
    )
    assert not gates["trace_state_recomputed"]


def test_rejects_direct_adapter_dirty_call() -> None:
    report, contract, adapter = fixture()
    gates = MODULE.verify(
        report,
        contract,
        "contract-hash",
        adapter + "\ntracker.markDirty(",
    )
    assert not gates["fabric_adapter_uses_canonical_port"]
