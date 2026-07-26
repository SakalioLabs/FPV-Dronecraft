from __future__ import annotations

import copy
import importlib.util
import json
from pathlib import Path


SCRIPT = Path(__file__).with_name(
    "verify_minecraft_native_trace_alc_evidence.py"
)
SPEC = importlib.util.spec_from_file_location("d121q_verifier", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def fixture() -> tuple[dict, dict, str, str, str, str]:
    report_path = Path("build/research/"
                       "minecraft-native-trace-alc-evidence-reference-v1.json")
    contract_path = Path("docs/acoustics/"
                         "minecraft-native-trace-alc-evidence-contract-v1.json")
    report = json.loads(report_path.read_text(encoding="utf-8"))
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    exporter = Path(
        "computational-acoustics-core/src/main/java/com/tenicana/"
        "dronecraft/acoustics/propagation/"
        "NativeEventTraceEvidenceExporter.java"
    ).read_text(encoding="utf-8")
    probe = Path(
        "fabric-mod/src/client/java/com/tenicana/dronecraft/client/"
        "sound/MinecraftNativeEventTraceEvidence.java"
    ).read_text(encoding="utf-8")
    command = Path(
        "fabric-mod/src/client/java/com/tenicana/dronecraft/client/"
        "command/NativeEventTraceDiagnosticClientCommands.java"
    ).read_text(encoding="utf-8")
    initializer = Path(
        "fabric-mod/src/client/java/com/tenicana/dronecraft/client/"
        "FpvDronecraftClient.java"
    ).read_text(encoding="utf-8")
    return report, contract, exporter, probe, command, initializer


def test_accepts_generated_reference() -> None:
    report, contract, exporter, probe, command, initializer = fixture()
    gates = MODULE.verify(
        report,
        contract,
        report["source_contract_sha256"],
        exporter,
        probe,
        command,
        initializer,
    )
    assert all(
        value is False if name in MODULE.BOUNDARIES else value is True
        for name, value in gates.items()
    )


def test_rejects_physical_device_fixture() -> None:
    report, contract, exporter, probe, command, initializer = fixture()
    tampered = copy.deepcopy(report)
    tampered["evidence"]["alc"]["device_name"] = "Speakers"
    gates = MODULE.verify(
        tampered,
        contract,
        report["source_contract_sha256"],
        exporter,
        probe,
        command,
        initializer,
    )
    assert not gates["alc_fixture_recomputed"]


def test_rejects_probe_that_opens_capture() -> None:
    report, contract, exporter, probe, command, initializer = fixture()
    gates = MODULE.verify(
        report,
        contract,
        report["source_contract_sha256"],
        exporter,
        probe + "\nalcCaptureOpenDevice",
        command,
        initializer,
    )
    assert not gates["minecraft_probe_is_read_only"]
