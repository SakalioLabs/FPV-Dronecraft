#!/usr/bin/env python3
"""Verify D103 live capability, simulated policy, and metadata timeline."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any


EXPECTED_POLICY = {
    "actual-device": (False, "OPERATIONAL", "OPENAL_EFX"),
    "simulated-efx-unavailable-with-java": (
        True,
        "EXTENSION_UNAVAILABLE",
        "JAVA_FDN",
    ),
    "simulated-efx-unavailable-without-java": (
        True,
        "EXTENSION_UNAVAILABLE",
        "CLEAN",
    ),
    "simulated-efx-pending-with-java": (
        True,
        "WAITING_SOURCES",
        "OPENAL_EFX_PENDING",
    ),
    "simulated-explicit-efx-failure": (
        True,
        "CONTEXT_FAILED",
        "OPENAL_EFX_PENDING",
    ),
    "simulated-procedural-audio-disabled": (
        True,
        "OPERATIONAL",
        "CLEAN",
    ),
}


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def verify(
    report: dict[str, Any],
    capability: dict[str, Any],
    capability_bytes: bytes,
    failover: dict[str, Any],
    failover_bytes: bytes,
) -> dict[str, Any]:
    if report.get("status") != "valid-backend-capability-policy-timeline":
        raise ValueError("invalid D103 status")
    if report.get("capability_report_sha256") != _sha256(capability_bytes):
        raise ValueError("capability report hash is detached")
    if report.get("fault_failover_report_sha256") != _sha256(failover_bytes):
        raise ValueError("failover report hash is detached")
    if capability.get("status") != "valid-capability-probe":
        raise ValueError("invalid live capability source")
    if failover.get("status") != "valid-openal-efx-fault-failover":
        raise ValueError("invalid fault failover source")
    actual = report.get("actual_device", {})
    native_eligible = (
        capability.get("active_context") is True
        and capability.get("efx_supported") is True
        and capability.get("maximum_auxiliary_sends", 0) >= 1
        and capability.get("efx_resources_created") is True
        and capability.get("efx_resources_released") is True
        and capability.get("al_error_code") == 0
    )
    if (
        actual.get("efx_extension_supported")
        is not capability.get("efx_supported")
        or actual.get("native_efx_eligible") is not native_eligible
        or actual.get("maximum_auxiliary_sends")
        != capability.get("maximum_auxiliary_sends")
    ):
        raise ValueError("actual capability policy is detached")
    if not native_eligible:
        raise ValueError("this D103 run requires the measured EFX device")

    cases = report.get("policy_cases")
    if not isinstance(cases, list):
        raise ValueError("policy cases are missing")
    by_name = {item.get("name"): item for item in cases}
    if set(by_name) != set(EXPECTED_POLICY):
        raise ValueError("policy matrix is incomplete")
    for name, (simulated, status, backend) in EXPECTED_POLICY.items():
        case = by_name[name]
        if (
            case.get("simulated") is not simulated
            or case.get("efx_status") != status
            or case.get("resolved_backend") != backend
        ):
            raise ValueError(f"policy case {name} is invalid")
    if report.get("no_efx_hardware_exercised") is not False:
        raise ValueError("no-EFX hardware was falsely claimed")
    if report.get("policy_simulation_exercised") is not True:
        raise ValueError("counterfactual policy simulation is missing")

    events = report.get("timeline_events")
    capacity = report.get("timeline_capacity")
    if (
        not isinstance(events, list)
        or not events
        or not isinstance(capacity, int)
        or len(events) > capacity
    ):
        raise ValueError("timeline capacity/content is invalid")
    previous_sequence = 0
    previous_nanos = 0
    previous_tick = -1
    for event in events:
        sequence = event.get("telemetry_sequence")
        nanos = event.get("host_monotonic_nanos")
        tick = event.get("minecraft_tick")
        if (
            not isinstance(sequence, int)
            or sequence <= previous_sequence
            or not isinstance(nanos, int)
            or nanos <= previous_nanos
            or not isinstance(tick, int)
            or tick < previous_tick
        ):
            raise ValueError("timeline clocks are not monotonic")
        previous_sequence = sequence
        previous_nanos = nanos
        previous_tick = tick
        if event.get("double_wet_path") is not False:
            raise ValueError("timeline observed a double wet path")
        if event.get("captures_audio") is not False:
            raise ValueError("timeline captured audio")
        rt60 = event.get("rt60_seconds", {})
        for band in ("low", "mid", "high"):
            value = rt60.get(band)
            if not isinstance(value, (int, float)) or not math.isfinite(value):
                raise ValueError("timeline RT60 is non-finite")
        for key in ("wet_gain", "transition_seconds"):
            value = event.get(key)
            if not isinstance(value, (int, float)) or not math.isfinite(value):
                raise ValueError("timeline control is non-finite")

    for cycle in failover.get("fault_cycles", []):
        stage = cycle["stage"]
        count = cycle["failed"]["fault_injection_count"]
        failed_rebuild = cycle["failed"]["context_rebuilds"]
        recovered_rebuild = cycle["recovered"]["context_rebuilds"]
        fallbacks = [
            event
            for event in events
            if event.get("last_fault_stage") == stage
            and event.get("fault_injection_count") == count
            and event.get("backend") == "JAVA_FDN"
            and event.get("efx_status") == "CONTEXT_FAILED"
            and event.get("context_rebuilds") == failed_rebuild
        ]
        recoveries = [
            event
            for event in events
            if event.get("last_fault_stage") == stage
            and event.get("fault_injection_count") == count
            and event.get("backend") == "OPENAL_EFX"
            and event.get("efx_status") == "OPERATIONAL"
            and event.get("context_rebuilds") == recovered_rebuild
        ]
        if not fallbacks or not recoveries:
            raise ValueError(f"timeline misses {stage} fallback/recovery")
        if min(item["telemetry_sequence"] for item in recoveries) <= min(
            item["telemetry_sequence"] for item in fallbacks
        ):
            raise ValueError("timeline recovery precedes fallback")

    for key in (
        "physical_endpoint_changed",
        "captures_audio",
        "release_calibrated",
    ):
        if report.get(key) is not False:
            raise ValueError(f"{key} must be false")
    return {
        "schema_version": 1,
        "status": "valid-backend-capability-policy-timeline-verification",
        "capability_report_sha256": _sha256(capability_bytes),
        "fault_failover_report_sha256": _sha256(failover_bytes),
        "policy_cases": len(cases),
        "timeline_events": len(events),
        "fault_cycles_bound": len(failover.get("fault_cycles", [])),
        "actual_native_efx_eligible": True,
        "no_efx_hardware_exercised": False,
        "policy_simulation_exercised": True,
        "timeline_captures_audio": False,
        "release_calibrated": False,
        "claim_boundary": (
            "Live capability and metadata transitions are measured; "
            "unsupported-EFX cases are policy simulations, not a second "
            "hardware or OpenAL implementation."
        ),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--capability-report", type=Path, required=True)
    parser.add_argument("--failover-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    capability_bytes = args.capability_report.read_bytes()
    failover_bytes = args.failover_report.read_bytes()
    result = verify(
        json.loads(args.report.read_bytes()),
        json.loads(capability_bytes),
        capability_bytes,
        json.loads(failover_bytes),
        failover_bytes,
    )
    result["source_report_sha256"] = _sha256(args.report.read_bytes())
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(result, separators=(",", ":")))


if __name__ == "__main__":
    main()
