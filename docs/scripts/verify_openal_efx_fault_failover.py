#!/usr/bin/env python3
"""Verify D102 sound-thread EFX fault/fallback/recovery evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


STAGES = ("resource-create", "parameter-write", "source-route")


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _require_runtime(
    runtime: dict[str, Any], backend: str, efx_operational: bool
) -> None:
    if runtime.get("backend") != backend:
        raise ValueError(f"runtime backend must be {backend}")
    if runtime.get("efx_operational") is not efx_operational:
        raise ValueError("runtime EFX ownership mismatch")
    if runtime.get("java_stream_active") is not (backend == "JAVA_FDN"):
        raise ValueError("Java stream ownership mismatch")
    if runtime.get("double_wet_path") is not False:
        raise ValueError("double wet path was observed")
    if runtime.get("captures_audio") is not False:
        raise ValueError("runtime telemetry captured audio")


def verify_report(report: dict[str, Any]) -> dict[str, Any]:
    if report.get("status") != "valid-openal-efx-fault-failover":
        raise ValueError("invalid fault failover status")
    if report.get("development_environment_required") is not True:
        raise ValueError("fault injection must be development-only")
    if report.get("fault_property_cleared") is not True:
        raise ValueError("fault property was left active")
    for key in (
        "physical_endpoint_changed",
        "captures_audio",
        "release_calibrated",
    ):
        if report.get(key) is not False:
            raise ValueError(f"{key} must be false")
    cycles = report.get("fault_cycles")
    if not isinstance(cycles, list) or [
        item.get("stage") for item in cycles
    ] != list(STAGES):
        raise ValueError("three ordered fault stages are required")

    previous_count = 0
    for cycle in cycles:
        stage = cycle["stage"]
        before = cycle.get("before", {})
        failed = cycle.get("failed", {})
        fallback = cycle.get("fallback", {})
        stable = cycle.get("same_context_stable", {})
        recovered = cycle.get("recovered", {})
        recovery_runtime = cycle.get("recovery_runtime", {})
        if (
            before.get("status") != "OPERATIONAL"
            or before.get("operational") is not True
            or before.get("attached_sources", 0) < 2
            or before.get("al_error_code") != 0
        ):
            raise ValueError(f"{stage} lacks operational baseline")
        if (
            failed.get("status") != "CONTEXT_FAILED"
            or failed.get("operational") is not False
            or failed.get("shared_resources_created") is not False
            or failed.get("attached_sources") != 0
            or failed.get("allocated_source_filters") != 0
            or failed.get("al_error_code", 0) == 0
            or failed.get("last_fault_stage") != stage
        ):
            raise ValueError(f"{stage} failure cleanup is invalid")
        if failed.get("fault_injection_count") != previous_count + 1:
            raise ValueError("fault injection count did not increment once")
        if failed.get("cleanup_count", -1) <= before.get(
            "cleanup_count", -1
        ):
            raise ValueError("resources were not cleaned before fallback")
        _require_runtime(fallback, "JAVA_FDN", False)
        _require_runtime(stable, "JAVA_FDN", False)
        if fallback.get("efx_status") != "CONTEXT_FAILED":
            raise ValueError("fallback did not preserve terminal failure")
        if stable.get("sequence", 0) < fallback.get("sequence", 0) + 3:
            raise ValueError("same-context stability window is too short")
        for key in (
            "context_rebuilds",
            "fault_injection_count",
            "al_error_code",
        ):
            if stable.get(key) != fallback.get(key):
                raise ValueError("same context retried a failed EFX path")
        if (
            recovered.get("status") != "OPERATIONAL"
            or recovered.get("operational") is not True
            or recovered.get("context_rebuilds")
            <= failed.get("context_rebuilds")
            or recovered.get("fault_injection_count")
            != failed.get("fault_injection_count")
            or recovered.get("al_error_code") != 0
        ):
            raise ValueError("new context did not recover cleanly")
        _require_runtime(recovery_runtime, "OPENAL_EFX", True)
        if recovery_runtime.get("context_rebuilds") != recovered.get(
            "context_rebuilds"
        ):
            raise ValueError("recovery telemetry is detached")
        previous_count += 1

    gates = report.get("gates", {})
    required_gates = (
        "all_three_stages_exercised",
        "resources_cleaned_before_fallback",
        "java_fallback_next_tick",
        "same_context_retry_suppressed",
        "new_context_recovery",
        "double_wet_path_never_observed",
    )
    if any(gates.get(key) is not True for key in required_gates):
        raise ValueError("a required failover gate is false")
    return {
        "schema_version": 1,
        "status": "valid-openal-efx-fault-failover-verification",
        "stages": list(STAGES),
        "fault_cycles": len(cycles),
        "final_fault_injection_count": previous_count,
        "all_failover_gates_passed": True,
        "physical_endpoint_changed": False,
        "captures_audio": False,
        "release_calibrated": False,
        "claim_boundary": (
            "Validates development-only OpenAL error injection and real "
            "Minecraft sound-thread recovery evidence, not a physical "
            "device, driver, or audible failure."
        ),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report_bytes = args.report.read_bytes()
    result = verify_report(json.loads(report_bytes))
    result["source_report_sha256"] = _sha256(report_bytes)
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(result, separators=(",", ":")))


if __name__ == "__main__":
    main()
