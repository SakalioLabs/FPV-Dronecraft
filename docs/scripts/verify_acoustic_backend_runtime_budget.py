#!/usr/bin/env python3
"""Verify D101 mutually bounded EFX and Java-FDN runtime evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


MAXIMUM_BUFFER_FRACTION = 0.25


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def verify_reports(
    efx: dict[str, Any],
    java: dict[str, Any],
    trace: dict[str, Any],
    trace_pcm: bytes,
) -> dict[str, Any]:
    if efx.get("status") != "valid-openal-efx-runtime-benchmark":
        raise ValueError("invalid EFX runtime status")
    if java.get("status") != "valid-benchmark":
        raise ValueError("invalid Java FDN runtime status")
    trace_hash = _sha256(trace_pcm)
    if trace_hash != trace.get("pcm_sha256"):
        raise ValueError("D094 trace PCM hash mismatch")
    if efx.get("d094_sidecar_sha256") != trace_hash:
        raise ValueError("EFX benchmark is not bound to D094")
    if efx.get("source_count") != 12:
        raise ValueError("EFX benchmark must exercise 12 source layers")
    if (
        efx.get("shared_effects") != 1
        or efx.get("shared_auxiliary_slots") != 1
        or efx.get("per_source_send_filters") != 12
    ):
        raise ValueError("EFX production sharing contract mismatch")
    if java.get("source_count") != 6:
        raise ValueError("Java benchmark must exercise six drones")
    if java.get("rotors_per_source") != 4:
        raise ValueError("Java benchmark rotor workload mismatch")
    if java.get("tones_per_rotor") != 15:
        raise ValueError("Java benchmark tone workload mismatch")
    if efx.get("physical_endpoint_opened") is not False:
        raise ValueError("EFX benchmark must not open a physical endpoint")
    if efx.get("captures_audio") is not False:
        raise ValueError("EFX benchmark must not capture audio")
    if efx.get("release_calibrated") is not False:
        raise ValueError("EFX release calibration claim is forbidden")
    if java.get("release_calibrated") is not False:
        raise ValueError("Java release calibration claim is forbidden")

    efx_cases = efx.get("cases")
    java_cases = java.get("cases")
    if not isinstance(efx_cases, list) or {
        item.get("backend") for item in efx_cases
    } != {"dry", "openal-efx"}:
        raise ValueError("EFX dry/wet cases are incomplete")
    if not isinstance(java_cases, list) or not java_cases:
        raise ValueError("Java runtime cases are missing")
    all_cases = efx_cases + java_cases
    for item in all_cases:
        fraction = item.get("p99_buffer_fraction")
        if not isinstance(fraction, (float, int)):
            raise ValueError("runtime case lacks P99 buffer fraction")
        if fraction < 0.0 or fraction > MAXIMUM_BUFFER_FRACTION:
            raise ValueError("runtime P99 exceeds 25% buffer budget")
    for item in efx_cases:
        if item.get("al_error") != 0 or item.get("alc_error") != 0:
            raise ValueError("OpenAL benchmark reported an error")

    dry = next(item for item in efx_cases if item["backend"] == "dry")
    wet = next(
        item for item in efx_cases if item["backend"] == "openal-efx"
    )
    incremental = max(0.0, wet["p99_ms"] - dry["p99_ms"])
    if abs(incremental - efx.get("incremental_efx_p99_ms", -1.0)) > 1e-9:
        raise ValueError("incremental EFX P99 is inconsistent")
    return {
        "schema_version": 1,
        "status": "valid-acoustic-backend-runtime-budget",
        "d094_sidecar_sha256": trace_hash,
        "efx_report_sha256": None,
        "java_report_sha256": None,
        "efx_p99_buffer_fraction": wet["p99_buffer_fraction"],
        "incremental_efx_p99_buffer_fraction": efx[
            "incremental_efx_p99_buffer_fraction"
        ],
        "java_maximum_p99_buffer_fraction": max(
            item["p99_buffer_fraction"] for item in java_cases
        ),
        "maximum_allowed_buffer_fraction": MAXIMUM_BUFFER_FRACTION,
        "all_runtime_gates_passed": True,
        "physical_endpoint_opened": False,
        "captures_audio": False,
        "release_calibrated": False,
        "claim_boundary": (
            "EFX is an in-process OpenAL Soft loopback wall-time benchmark; "
            "Java is a single-process wet-stream benchmark. They validate "
            "separate worst-case production workloads, not identical DSP, "
            "physical endpoint callbacks, drivers, underruns, or frame time."
        ),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--efx-report", type=Path, required=True)
    parser.add_argument("--java-report", type=Path, required=True)
    parser.add_argument("--trace-report", type=Path, required=True)
    parser.add_argument("--trace-pcm", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    efx_bytes = args.efx_report.read_bytes()
    java_bytes = args.java_report.read_bytes()
    result = verify_reports(
        json.loads(efx_bytes),
        json.loads(java_bytes),
        json.loads(args.trace_report.read_bytes()),
        args.trace_pcm.read_bytes(),
    )
    result["efx_report_sha256"] = _sha256(efx_bytes)
    result["java_report_sha256"] = _sha256(java_bytes)
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(result, separators=(",", ":")))


if __name__ == "__main__":
    main()
