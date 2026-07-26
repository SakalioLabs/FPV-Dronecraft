#!/usr/bin/env python3
"""Independent D121q trace/ALC evidence-bundle verifier."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


EXPECTED_ENTRIES = [
    (
        1,
        1,
        0,
        41,
        "WORLD_REPLACE",
        "WORLD_REPLACED",
        [0, 0, 0, 0, 0, 0],
    ),
    (
        2,
        1,
        1,
        41,
        "BLOCK_APPLY",
        "ACCEPTED_DIRTY",
        [1, 2, 3, 1, 2, 3],
    ),
    (
        3,
        1,
        2,
        41,
        "BLOCK_APPLY",
        "ACCEPTED_OUTSIDE_COVERAGE",
        [40, 2, 40, 40, 2, 40],
    ),
    (
        4,
        1,
        3,
        41,
        "CHUNK_UNLOAD",
        "ACCEPTED_DIRTY",
        [0, 0, 0, 15, 15, 15],
    ),
]
BOUNDARIES = [
    "minecraft_client_started",
    "alc_read_performed",
    "physical_endpoint_opened",
    "captures_audio",
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
    exporter_source: str,
    minecraft_probe_source: str,
    command_source: str,
    initializer_source: str,
) -> dict[str, bool]:
    evidence = report["evidence"]
    alc = evidence["alc"]
    trace = evidence["trace"]
    entries = trace["entries"]
    entry_fields = [
        (
            entry["ordinal"],
            entry["active_world_epoch"],
            entry["local_sequence"],
            entry["thread_id"],
            entry["kind"],
            entry["disposition"],
            entry["bounds"],
        )
        for entry in entries
    ]
    entries_match = entry_fields == EXPECTED_ENTRIES
    alc_matches = (
        alc
        == {
            "active_context": True,
            "active_device": True,
            "device_name": "No Output",
            "capture_device_specifier": "",
        }
        and evidence["launch_gate"]
        == {
            "state": "ARMED",
            "reason": "SAFE_TO_START_RUNTIME_VERIFICATION",
        }
    )
    trace_matches = (
        trace["retained"] == len(entries) == 4
        and trace["overwritten"] == 2
        and [entry["ordinal"] for entry in entries] == [1, 2, 3, 4]
        and len({entry["thread_id"] for entry in entries}) == 1
    )
    reference_boundaries = (
        evidence["reference_fixture"] is True
        and evidence["minecraft_client_started"] is False
        and evidence["alc_read_performed"] is False
        and evidence["alc_device_opened"] is False
        and evidence["physical_endpoint_opened"] is False
        and evidence["captures_audio"] is False
        and evidence["cuda_executed"] is False
        and evidence["release_calibrated"] is False
    )
    rejection_probes = report["rejection_probes"]
    all_rejections = (
        set(rejection_probes)
        == {
            "unarmed_launch",
            "physical_device_name",
            "capture_device_present",
            "inactive_context",
            "nonchronological_trace",
        }
        and all(rejection_probes.values())
    )
    exporter_markers = [
        'REQUIRED_DEVICE_NAME = "No Output"',
        "if (!launchDecision.armed())",
        "if (!alc.activeContext() || !alc.activeDevice())",
        "if (!alc.captureDeviceSpecifier().isEmpty())",
        "trace entries are not chronological",
        "Files.createDirectories(parent)",
    ]
    validate_before_write = (
        all(marker in exporter_source for marker in exporter_markers)
        and exporter_source.index(
            "validate(launchDecision, alc, entries, count, overwritten)"
        )
        < exporter_source.index("Files.createDirectories(parent)")
    )
    probe_markers = [
        "fpvdrone$getExecutor()",
        "executor.schedule(() ->",
        "ALC10.alcGetCurrentContext()",
        "ALC10.alcGetContextsDevice(context)",
        "ALC10.ALC_DEVICE_SPECIFIER",
        "ALC11.ALC_CAPTURE_DEVICE_SPECIFIER",
        "thenApplyAsync(alc ->",
        "nativeEventTraceCapacity()",
        "NativeEventTraceEvidenceExporter.write(",
    ]
    read_only_probe = (
        all(marker in minecraft_probe_source for marker in probe_markers)
        and "alcOpenDevice" not in minecraft_probe_source
        and "alcCaptureOpenDevice" not in minecraft_probe_source
        and "DroneSoundManager" not in minecraft_probe_source
    )
    command_isolated = (
        "MinecraftNativeEventTraceEvidence.export(" in command_source
        and "DroneSoundManager" not in command_source
    )
    armed = initializer_source.index("if (traceMode.armed())")
    dedicated = initializer_source.index(
        "NativeEventTraceDiagnosticClientCommands.initialize()"
    )
    fallback = initializer_source.index("} else {", armed)
    armed_only_command = armed < dedicated < fallback
    contract_matches = (
        contract["required_alc"]
        == {
            "active_context": True,
            "active_device": True,
            "device_name": "No Output",
            "capture_device_specifier": "",
        }
        and contract["reference"]["ordinals"] == [1, 2, 3, 4]
    )
    return {
        "contract_hash_matches": (
            report["source_contract_sha256"] == contract_hash
        ),
        "identity_matches": (
            report["status"]
            == "valid-native-trace-alc-evidence-reference"
            and evidence["status"] == "valid-native-trace-alc-evidence"
        ),
        "contract_values_match": contract_matches,
        "alc_fixture_recomputed": alc_matches,
        "trace_entries_recomputed": entries_match,
        "trace_counts_recomputed": trace_matches,
        "reference_boundaries_match": reference_boundaries,
        "all_rejection_probes_passed": all_rejections,
        "validate_before_write_present": validate_before_write,
        "minecraft_probe_is_read_only": read_only_probe,
        "dedicated_command_isolated": command_isolated,
        "dedicated_command_armed_only": armed_only_command,
        **{boundary: bool(report[boundary]) for boundary in BOUNDARIES},
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--exporter-source", type=Path, required=True)
    parser.add_argument("--minecraft-probe-source", type=Path, required=True)
    parser.add_argument("--command-source", type=Path, required=True)
    parser.add_argument("--initializer-source", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    contract = json.loads(args.contract.read_text(encoding="utf-8"))
    gates = verify(
        report,
        contract,
        sha256(args.contract),
        args.exporter_source.read_text(encoding="utf-8"),
        args.minecraft_probe_source.read_text(encoding="utf-8"),
        args.command_source.read_text(encoding="utf-8"),
        args.initializer_source.read_text(encoding="utf-8"),
    )
    positive = [
        "contract_hash_matches",
        "identity_matches",
        "contract_values_match",
        "alc_fixture_recomputed",
        "trace_entries_recomputed",
        "trace_counts_recomputed",
        "reference_boundaries_match",
        "all_rejection_probes_passed",
        "validate_before_write_present",
        "minecraft_probe_is_read_only",
        "dedicated_command_isolated",
        "dedicated_command_armed_only",
    ]
    if not all(gates[name] for name in positive) or any(
        gates[name] for name in BOUNDARIES
    ):
        raise SystemExit(f"native trace/ALC evidence failed: {gates}")
    result = {
        "schema_version": 1,
        "status": "verified-minecraft-native-trace-alc-evidence",
        "source_report_sha256": sha256(args.report),
        "source_contract_sha256": sha256(args.contract),
        "source_exporter_sha256": sha256(args.exporter_source),
        "source_minecraft_probe_sha256": sha256(
            args.minecraft_probe_source
        ),
        "source_command_sha256": sha256(args.command_source),
        "source_initializer_sha256": sha256(args.initializer_source),
        "gates": gates,
        "metrics": {
            "retained": report["evidence"]["trace"]["retained"],
            "overwritten": report["evidence"]["trace"]["overwritten"],
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
