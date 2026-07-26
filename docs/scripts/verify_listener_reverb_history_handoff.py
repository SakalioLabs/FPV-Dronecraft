#!/usr/bin/env python3
"""Verify D107 listener-shared history and exclusive wet-owner handoff."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any


SAMPLE_RATE = 48_000
HISTORY_FRAMES = 24_000
HISTORY_MILLISECONDS = 500
SHADOW_TICK_FRAMES = 2_400
SOURCE_COUNT = 6
OUTPUT_FRAMES = 2_048
OUTPUT_BYTES = OUTPUT_FRAMES * 2
ENVIRONMENTS = ("closed", "partial", "open")
EXPECTED_SIDECAR_BYTES = len(ENVIRONMENTS) * OUTPUT_BYTES
MAXIMUM_SHADOW_P99_MS = 12.5
MAXIMUM_HANDOFF_P99_MS = (
    OUTPUT_FRAMES / SAMPLE_RATE
) * 1_000 * 0.25


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _finite_nonnegative(value: Any) -> bool:
    return (
        isinstance(value, (int, float))
        and not isinstance(value, bool)
        and math.isfinite(value)
        and value >= 0.0
    )


def verify(
    report: dict[str, Any],
    report_sha256: str,
    sidecar: bytes,
    d106: dict[str, Any],
    d106_sha256: str,
) -> dict[str, Any]:
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "valid-listener-reverb-history-handoff"
        or report.get("sample_rate_hz") != SAMPLE_RATE
        or report.get("minecraft_stream_buffer_seconds") != 1
        or report.get("minecraft_initial_queue_buffers") != 4
        or report.get("history_frames") != HISTORY_FRAMES
        or report.get("history_milliseconds") != HISTORY_MILLISECONDS
        or report.get("shadow_tick_frames") != SHADOW_TICK_FRAMES
        or report.get("source_count") != SOURCE_COUNT
        or report.get("warmup_iterations") != 10
        or report.get("measured_iterations") != 50
    ):
        raise ValueError("D107 report shape or queue/history contract changed")
    if (
        d106.get("schema_version") != 1
        or d106.get("status") != "valid-fdn-history-preroll-sweep"
        or d106.get("selected_pre_roll_ms") != HISTORY_MILLISECONDS
        or d106.get("selection_available") is not True
        or report.get("source_d106_report_sha256") != d106_sha256
    ):
        raise ValueError("D107 is detached from the selected D106 history")
    if (
        len(sidecar) != EXPECTED_SIDECAR_BYTES
        or report.get("sidecar_bytes") != EXPECTED_SIDECAR_BYTES
        or report.get("sidecar_format") != "s16le-mono-48000"
        or report.get("sidecar_sha256") != _sha256(sidecar)
    ):
        raise ValueError("D107 sidecar identity changed")

    thresholds = report.get("thresholds", {})
    expected_thresholds = {
        "maximum_shadow_p99_ms": MAXIMUM_SHADOW_P99_MS,
        "maximum_handoff_p99_ms": MAXIMUM_HANDOFF_P99_MS,
        "maximum_first_wet_frame": 0,
    }
    for key, expected in expected_thresholds.items():
        observed = thresholds.get(key)
        if not isinstance(observed, (int, float)) or not math.isclose(
            observed, expected, rel_tol=0.0, abs_tol=1.0e-12
        ):
            raise ValueError("D107 registered threshold changed")

    environments = report.get("environments")
    d106_environments = d106.get("environments")
    if (
        not isinstance(environments, list)
        or not isinstance(d106_environments, list)
        or [item.get("name") for item in environments]
        != list(ENVIRONMENTS)
        or len(d106_environments) != len(ENVIRONMENTS)
    ):
        raise ValueError("D107 environment matrix changed")
    for observed, source in zip(environments, d106_environments):
        if observed != source:
            raise ValueError("D107 environment controls detached from D106")

    cases = report.get("cases")
    if (
        not isinstance(cases, list)
        or [item.get("environment") for item in cases]
        != list(ENVIRONMENTS)
    ):
        raise ValueError("D107 case order changed")
    occupied: list[tuple[int, int]] = []
    verified_cases = []
    for item in cases:
        for prefix in ("shadow", "handoff"):
            values = [
                item.get(f"{prefix}_p50_ms"),
                item.get(f"{prefix}_p95_ms"),
                item.get(f"{prefix}_p99_ms"),
            ]
            if (
                not all(_finite_nonnegative(value) for value in values)
                or values != sorted(values)
            ):
                raise ValueError("D107 timing distribution is invalid")
        if not _finite_nonnegative(item.get("fdn_preroll_p99_ms")):
            raise ValueError("D107 FDN pre-roll timing is invalid")
        offset = item.get("pcm_offset")
        count = item.get("pcm_bytes")
        if (
            not isinstance(offset, int)
            or not isinstance(count, int)
            or offset < 0
            or count != OUTPUT_BYTES
            or offset + count > len(sidecar)
        ):
            raise ValueError("D107 PCM range is invalid")
        end = offset + count
        if any(
            offset < other_end and end > other_start
            for other_start, other_end in occupied
        ):
            raise ValueError("D107 PCM ranges overlap")
        occupied.append((offset, end))
        payload = sidecar[offset:end]
        if _sha256(payload) != item.get("pcm_sha256"):
            raise ValueError("D107 PCM range hash changed")
        first_sample = int.from_bytes(
            payload[:2], byteorder="little", signed=True
        )
        passes = (
            item.get("shadow_p99_ms") <= MAXIMUM_SHADOW_P99_MS
            and item.get("handoff_p99_ms") <= MAXIMUM_HANDOFF_P99_MS
            and item.get("history_frames_before") == HISTORY_FRAMES
            and item.get("preroll_frames") == HISTORY_FRAMES
            and item.get("synthesizers_before") == SOURCE_COUNT
            and item.get("synthesizers_during") == SOURCE_COUNT
            and item.get("first_wet_frame") == 0
            and first_sample != 0
            and item.get("invalidated_owner_silent") is True
            and item.get("closing_old_owner_preserved_replacement") is True
            and item.get("shadow_restarted") is True
            and item.get("wet_owner_released") is True
            and item.get("history_cleared_on_restart") is True
            and item.get("phase_state_cleared_on_restart") is True
        )
        if item.get("passes") is not passes or not passes:
            raise ValueError("D107 case contract or pass flag changed")
        verified_cases.append(
            {
                "environment": item["environment"],
                "shadow_p99_ms": item["shadow_p99_ms"],
                "handoff_p99_ms": item["handoff_p99_ms"],
                "first_wet_frame": item["first_wet_frame"],
                "pcm_sha256": item["pcm_sha256"],
            }
        )
    if sorted(occupied) != [
        (index * OUTPUT_BYTES, (index + 1) * OUTPUT_BYTES)
        for index in range(len(ENVIRONMENTS))
    ]:
        raise ValueError("D107 PCM coverage is not exact")
    if report.get("all_cases_passed") is not True:
        raise ValueError("D107 aggregate pass flag changed")
    required_true = (
        "active_source_restart_required",
        "listener_shared_phase_owner",
        "exclusive_wet_owner",
    )
    required_false = (
        "openal_shadow_source_created",
        "captures_audio",
        "physical_endpoint_opened",
        "client_gametest_measured",
        "release_calibrated",
    )
    if any(report.get(key) is not True for key in required_true):
        raise ValueError("D107 production ownership flag changed")
    if any(report.get(key) is not False for key in required_false):
        raise ValueError("D107 claim-boundary flag changed")
    if not isinstance(report.get("claim_boundary"), str):
        raise ValueError("D107 claim boundary is missing")

    return {
        "schema_version": 1,
        "status": "verified-listener-reverb-history-handoff",
        "source_report_sha256": report_sha256,
        "source_d106_report_sha256": d106_sha256,
        "sidecar_sha256": _sha256(sidecar),
        "maximum_shadow_p99_ms": max(
            item["shadow_p99_ms"] for item in cases
        ),
        "maximum_handoff_p99_ms": max(
            item["handoff_p99_ms"] for item in cases
        ),
        "maximum_first_wet_frame": max(
            item["first_wet_frame"] for item in cases
        ),
        "cases": verified_cases,
        "openal_shadow_source_created": False,
        "active_source_restart_required": True,
        "exclusive_wet_owner": True,
        "captures_audio": False,
        "physical_endpoint_opened": False,
        "client_gametest_measured": False,
        "release_calibrated": False,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--pcm", type=Path, required=True)
    parser.add_argument("--d106-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report_bytes = args.report.read_bytes()
    sidecar = args.pcm.read_bytes()
    d106_bytes = args.d106_report.read_bytes()
    result = verify(
        json.loads(report_bytes),
        _sha256(report_bytes),
        sidecar,
        json.loads(d106_bytes),
        _sha256(d106_bytes),
    )
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
