from __future__ import annotations

import copy
import importlib.util
from pathlib import Path


SCRIPT = Path(__file__).with_name(
    "verify_minecraft_native_trace_launch_gate.py"
)
SPEC = importlib.util.spec_from_file_location("d121p_verifier", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def fixture() -> tuple[dict, dict, str, str]:
    cases = []
    for index, (name, state, reason) in enumerate(MODULE.EXPECTED):
        cases.append(
            {
                "name": name,
                "trace_requested": index != 0,
                "trace_implementation_enabled": index not in (0, 2),
                "openal_drivers": (
                    None
                    if index in (0, 3)
                    else "null,"
                    if index == 4
                    else "wasapi"
                    if index == 5
                    else "Null"
                    if index == 6
                    else "null"
                ),
                "local_plane_scheduler_requested": index == 7,
                "drone_sound_manager_suppressed": index not in (0, 8),
                "general_acoustic_commands_suppressed": index not in (0, 9),
                "state": state,
                "reason": reason,
            }
        )
    report = {
        "status": "valid-native-trace-launch-gate",
        "source_contract_sha256": "contract-hash",
        "cases": cases,
        "counts": {"total": 10, "normal": 1, "armed": 1, "rejected": 8},
        "gates": {
            "counts_match": True,
            "only_exact_null_arms": True,
            "normal_launch_unchanged": True,
            "every_unsuppressed_writer_rejected": True,
        },
        **{boundary: False for boundary in MODULE.BOUNDARIES},
    }
    contract = {
        "primary_sources": [
            {"url": MODULE.CONFIG_URL},
            {"url": MODULE.NULL_URL},
        ]
    }
    mode = "\n".join(
        [
            'System.getenv("ALSOFT_DRIVERS")',
            "MinecraftLocalPlaneSceneScheduler.ENABLE_PROPERTY",
            "NativeEventTraceDiagnosticGate.evaluate(",
        ]
    )
    initializer = "\n".join(
        [
            "NativeEventTraceDiagnosticGate.State.REJECTED",
            "if (traceMode.armed())",
            "} else {",
            "AcousticDiagnosticClientCommands.initialize()",
            "DroneSoundManager.initialize()",
        ]
    )
    return report, contract, mode, initializer


def test_accepts_consistent_fixture() -> None:
    report, contract, mode, initializer = fixture()
    gates = MODULE.verify(
        report, contract, "contract-hash", mode, initializer
    )
    assert all(
        value is False if name in MODULE.BOUNDARIES else value is True
        for name, value in gates.items()
    )


def test_rejects_driver_fallback_becoming_armed() -> None:
    report, contract, mode, initializer = fixture()
    tampered = copy.deepcopy(report)
    tampered["cases"][4]["state"] = "ARMED"
    gates = MODULE.verify(
        tampered, contract, "contract-hash", mode, initializer
    )
    assert not gates["truth_table_recomputed"]
    assert not gates["exact_null_only_recomputed"]


def test_rejects_initializer_without_suppression_branch() -> None:
    report, contract, mode, initializer = fixture()
    gates = MODULE.verify(
        report,
        contract,
        "contract-hash",
        mode,
        initializer.replace("} else {", ""),
    )
    assert not gates["fabric_fail_closed_mapping_present"]
