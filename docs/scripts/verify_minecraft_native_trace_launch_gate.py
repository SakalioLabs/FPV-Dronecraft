#!/usr/bin/env python3
"""Independent D121p native trace launch-gate verifier."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


EXPECTED = [
    ("normal", "NORMAL", "TRACE_NOT_REQUESTED"),
    ("armed", "ARMED", "SAFE_TO_START_RUNTIME_VERIFICATION"),
    (
        "implementation-disabled",
        "REJECTED",
        "TRACE_IMPLEMENTATION_DISABLED",
    ),
    (
        "drivers-missing",
        "REJECTED",
        "OPENAL_NULL_BACKEND_NOT_EXCLUSIVE",
    ),
    (
        "drivers-fallback-list",
        "REJECTED",
        "OPENAL_NULL_BACKEND_NOT_EXCLUSIVE",
    ),
    (
        "drivers-physical",
        "REJECTED",
        "OPENAL_NULL_BACKEND_NOT_EXCLUSIVE",
    ),
    (
        "drivers-wrong-case",
        "REJECTED",
        "OPENAL_NULL_BACKEND_NOT_EXCLUSIVE",
    ),
    (
        "local-plane-requested",
        "REJECTED",
        "LOCAL_PLANE_SCHEDULER_REQUESTED",
    ),
    (
        "sound-manager-not-suppressed",
        "REJECTED",
        "DRONE_SOUND_MANAGER_NOT_SUPPRESSED",
    ),
    (
        "commands-not-suppressed",
        "REJECTED",
        "GENERAL_ACOUSTIC_COMMANDS_NOT_SUPPRESSED",
    ),
]
BOUNDARIES = [
    "minecraft_client_started",
    "alc_device_opened",
    "physical_endpoint_opened",
    "captures_audio",
    "native_callback_delivery_measured",
    "cuda_executed",
    "release_calibrated",
]
CONFIG_URL = (
    "https://raw.githubusercontent.com/kcat/openal-soft/"
    "a81b7e61ba30c8330fd0c1990c8008ca364ab072/alsoftrc.sample"
)
NULL_URL = (
    "https://raw.githubusercontent.com/kcat/openal-soft/"
    "a81b7e61ba30c8330fd0c1990c8008ca364ab072/"
    "alc/backends/null.cpp"
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(
    report: dict[str, Any],
    contract: dict[str, Any],
    contract_hash: str,
    mode_source: str,
    initializer_source: str,
) -> dict[str, bool]:
    cases = report["cases"]
    identities = [
        (case["name"], case["state"], case["reason"]) for case in cases
    ]
    matrix_matches = identities == EXPECTED
    counts = {
        "total": len(cases),
        "normal": sum(case["state"] == "NORMAL" for case in cases),
        "armed": sum(case["state"] == "ARMED" for case in cases),
        "rejected": sum(case["state"] == "REJECTED" for case in cases),
    }
    counts_match = (
        counts == report["counts"]
        and counts
        == {
            "total": 10,
            "normal": 1,
            "armed": 1,
            "rejected": 8,
        }
    )
    armed = [case for case in cases if case["state"] == "ARMED"]
    exact_null_only = (
        len(armed) == 1
        and armed[0]["openal_drivers"] == "null"
        and armed[0]["local_plane_scheduler_requested"] is False
        and armed[0]["drone_sound_manager_suppressed"] is True
        and armed[0]["general_acoustic_commands_suppressed"] is True
    )
    reported_gates = {
        "counts_match": counts_match,
        "only_exact_null_arms": exact_null_only,
        "normal_launch_unchanged": (
            cases[0]["state"] == "NORMAL"
            and cases[0]["trace_requested"] is False
        ),
        "every_unsuppressed_writer_rejected": all(
            cases[index]["state"] == "REJECTED" for index in (7, 8, 9)
        ),
    }
    sources = contract["primary_sources"]
    source_pins = (
        len(sources) == 2
        and sources[0]["url"] == CONFIG_URL
        and sources[1]["url"] == NULL_URL
    )
    mode_markers = [
        'System.getenv("ALSOFT_DRIVERS")',
        "MinecraftLocalPlaneSceneScheduler.ENABLE_PROPERTY",
        "NativeEventTraceDiagnosticGate.evaluate(",
    ]
    initializer_markers = [
        "NativeEventTraceDiagnosticGate.State.REJECTED",
        "if (traceMode.armed())",
        "} else {",
        "AcousticDiagnosticClientCommands.initialize()",
        "DroneSoundManager.initialize()",
    ]
    initializer_order = [
        initializer_source.find(marker) for marker in initializer_markers
    ]
    fabric_mapping = (
        all(marker in mode_source for marker in mode_markers)
        and all(index >= 0 for index in initializer_order)
        and initializer_order == sorted(initializer_order)
    )
    return {
        "contract_hash_matches": (
            report["source_contract_sha256"] == contract_hash
        ),
        "identity_matches": (
            report["status"] == "valid-native-trace-launch-gate"
        ),
        "truth_table_recomputed": matrix_matches,
        "counts_recomputed": counts_match,
        "exact_null_only_recomputed": exact_null_only,
        "reported_gates_recomputed": (
            report["gates"] == reported_gates
            and all(reported_gates.values())
        ),
        "primary_sources_commit_pinned": source_pins,
        "fabric_fail_closed_mapping_present": fabric_mapping,
        **{boundary: bool(report[boundary]) for boundary in BOUNDARIES},
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--mode-source", type=Path, required=True)
    parser.add_argument("--initializer-source", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    contract = json.loads(args.contract.read_text(encoding="utf-8"))
    gates = verify(
        report,
        contract,
        sha256(args.contract),
        args.mode_source.read_text(encoding="utf-8"),
        args.initializer_source.read_text(encoding="utf-8"),
    )
    positive = [
        "contract_hash_matches",
        "identity_matches",
        "truth_table_recomputed",
        "counts_recomputed",
        "exact_null_only_recomputed",
        "reported_gates_recomputed",
        "primary_sources_commit_pinned",
        "fabric_fail_closed_mapping_present",
    ]
    if not all(gates[name] for name in positive) or any(
        gates[name] for name in BOUNDARIES
    ):
        raise SystemExit(f"native trace launch gate failed: {gates}")
    result = {
        "schema_version": 1,
        "status": "verified-minecraft-native-trace-launch-gate",
        "source_report_sha256": sha256(args.report),
        "source_contract_sha256": sha256(args.contract),
        "source_mode_adapter_sha256": sha256(args.mode_source),
        "source_client_initializer_sha256": sha256(
            args.initializer_source
        ),
        "gates": gates,
        "metrics": report["counts"],
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
