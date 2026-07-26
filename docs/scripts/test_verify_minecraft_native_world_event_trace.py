from __future__ import annotations

import copy
import importlib.util
from pathlib import Path


SCRIPT = Path(__file__).with_name(
    "verify_minecraft_native_world_event_trace.py"
)
SPEC = importlib.util.spec_from_file_location("d121o_verifier", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def fixture() -> tuple[dict, dict, str]:
    entries = [
        {
            "ordinal": ordinal,
            "active_world_epoch": epoch,
            "local_sequence": sequence,
            "thread_id": 17,
            "kind": kind,
            "disposition": disposition,
            "bounds": bounds,
        }
        for ordinal, epoch, sequence, kind, disposition, bounds
        in MODULE.EXPECTED
    ]
    contract = {
        "reference_capacity": 8,
        "reference_record_count": 12,
        "reference_retained_count": 8,
        "reference_overwritten_count": 4,
        "gates": {
            "steady_record_allocation_windows_bytes": [0, 0, 0, 0, 0]
        },
    }
    report = {
        "status": "valid-native-world-event-trace-replay",
        "source_contract_sha256": "contract-hash",
        "trace_enabled": True,
        "capacity": 8,
        "recorded": 12,
        "retained": 8,
        "overwritten": 4,
        "producer_thread_id": 17,
        "retained_entries": entries,
        "disabled_probe": {
            "enabled": False,
            "recorded": 0,
            "retained": 0,
            "overwritten": 0,
        },
        "steady_record_allocation_windows_bytes": [0, 0, 0, 0, 0],
        "gates": {
            "capacity_bounded": True,
            "overwrites_oldest": True,
            "chronological_export": True,
            "one_producer_thread": True,
            "disabled_is_strict_noop": True,
            "zero_steady_record_allocation": True,
        },
        **{boundary: False for boundary in MODULE.BOUNDARIES},
    }
    adapter = "\n".join(
        [
            "NATIVE_EVENT_TRACE_CAPACITY = 256",
            "Boolean.getBoolean(",
            '"fpvdrone.acoustics.nativeEventTrace"',
            "NATIVE_EVENT_TRACE.enabled()",
            "Thread.currentThread().threadId()",
            "copyNativeEventTrace(",
            "ADAPTER_WORLD_MISMATCH_REJECTED",
        ]
    )
    return report, contract, adapter


def test_accepts_consistent_fixture() -> None:
    report, contract, adapter = fixture()
    gates = MODULE.verify(report, contract, "contract-hash", adapter)
    assert all(
        value is False if name in MODULE.BOUNDARIES else value is True
        for name, value in gates.items()
    )


def test_rejects_reordered_export() -> None:
    report, contract, adapter = fixture()
    tampered = copy.deepcopy(report)
    tampered["retained_entries"][0], tampered["retained_entries"][1] = (
        tampered["retained_entries"][1],
        tampered["retained_entries"][0],
    )
    gates = MODULE.verify(tampered, contract, "contract-hash", adapter)
    assert not gates["retained_entries_recomputed"]
    assert not gates["reported_gates_recomputed"]


def test_rejects_allocation_or_enabled_boundary() -> None:
    report, contract, adapter = fixture()
    tampered = copy.deepcopy(report)
    tampered["steady_record_allocation_windows_bytes"][3] = 16
    gates = MODULE.verify(tampered, contract, "contract-hash", adapter)
    assert not gates["zero_allocation_recomputed"]
