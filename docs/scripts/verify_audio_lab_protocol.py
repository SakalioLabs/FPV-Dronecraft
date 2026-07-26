#!/usr/bin/env python3
"""Verify Minecraft dry/Java-FDN/OpenAL-EFX audio-lab timelines."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from verify_openal_clock_latency import verify_timing_pairs


MAXIMUM_RELOAD_CALL_DURATION_NS = 5_000_000_000


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path: Path) -> dict[str, object]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path}: report root must be an object")
    return value


def verify_common(
    report: dict[str, object],
    backend: str,
    variant: str,
) -> tuple[
    dict[str, object],
    dict[str, object],
    list[dict[str, object]],
    dict[str, object],
]:
    if report.get("schema_version") != 2:
        raise ValueError(f"{backend}: schema must be 2")
    if report.get("status") != "valid-audio-lab-timeline":
        raise ValueError(f"{backend}: status is invalid")
    if report.get("backend") != backend or report.get("variant") != variant:
        raise ValueError(f"{backend}: backend/variant mismatch")
    if report.get("procedural_audio_required") is not True:
        raise ValueError(f"{backend}: procedural audio was not required")
    if (
        report.get("backend_override_default_before") is not True
        or report.get("backend_override_restored_after") is not True
    ):
        raise ValueError(f"{backend}: backend override lifecycle failed")
    marker = report.get("marker_contract")
    before = report.get("before_boundary")
    after = report.get("after_boundary")
    events = report.get("events")
    if (
        not isinstance(marker, dict)
        or not isinstance(before, dict)
        or not isinstance(after, dict)
        or not isinstance(events, list)
        or not all(isinstance(event, dict) for event in events)
    ):
        raise ValueError(f"{backend}: report sections are malformed")
    if marker != {
        "duration_s": 0.08,
        "start_hz": 880.0,
        "pre_boundary_hz": 1320.0,
        "post_boundary_hz": 1760.0,
    }:
        raise ValueError(f"{backend}: marker contract changed")

    reload_expected = variant == "reload"
    if report.get("sound_engine_reload_exercised") is not reload_expected:
        raise ValueError(f"{backend}: reload claim mismatch")
    reload_duration = report.get("reload_call_duration_ns")
    if isinstance(reload_duration, bool) or not isinstance(
        reload_duration, int
    ):
        raise ValueError(f"{backend}: reload duration is invalid")
    if reload_expected:
        if not 0 < reload_duration < MAXIMUM_RELOAD_CALL_DURATION_NS:
            raise ValueError(f"{backend}: reload duration is out of bounds")
        expected_names = [
            "session_start_marker",
            "pre_boundary_marker",
            "sound_engine_reload_requested",
            "sound_engine_reload_returned",
            "post_boundary_marker",
            "session_complete",
        ]
        expected_prefix_ticks = [0, 40, 50, 50]
    else:
        if reload_duration != 0:
            raise ValueError(f"{backend}: control has reload duration")
        expected_names = [
            "session_start_marker",
            "pre_boundary_marker",
            "control_boundary_no_reload",
            "post_boundary_marker",
            "session_complete",
        ]
        expected_prefix_ticks = [0, 40, 50]
    if [event.get("name") for event in events] != expected_names:
        raise ValueError(f"{backend}: event sequence changed")
    ticks = [event.get("tick") for event in events]
    if (
        ticks[: len(expected_prefix_ticks)] != expected_prefix_ticks
        or not isinstance(ticks[-2], int)
        or not 60 <= ticks[-2] <= 200
        or ticks[-1] != ticks[-2] + 100
    ):
        raise ValueError(f"{backend}: event ticks changed")
    relative = [event.get("relative_ns") for event in events]
    if (
        not all(
            isinstance(value, int) and not isinstance(value, bool)
            for value in relative
        )
        or any(right <= left for left, right in zip(relative, relative[1:]))
    ):
        raise ValueError(f"{backend}: event timestamps are not monotonic")
    marker_details = [
        event.get("detail")
        for event in events
        if event.get("name", "").endswith("marker")
    ]
    if marker_details != [
        "STARTED@880.0Hz",
        "STARTED@1320.0Hz",
        "STARTED@1760.0Hz",
    ]:
        raise ValueError(f"{backend}: a marker did not start")
    if (
        before.get("active_drone_sound_sets", 0) < 1
        or after.get("active_drone_sound_sets", 0) < 1
        or before.get("efx_al_error_code") != 0
        or after.get("efx_al_error_code") != 0
    ):
        raise ValueError(f"{backend}: active drone/AL gate failed")
    if (
        report.get("real_audio_capture") is not False
        or report.get("physical_output_loopback_confirmed") is not False
        or report.get("release_calibrated") is not False
    ):
        raise ValueError(f"{backend}: report overclaims audio evidence")
    native = report.get("native_timing")
    if not isinstance(native, dict):
        raise ValueError(f"{backend}: native timing evidence is missing")
    for key, required in (
        ("probe", "OpenAlClockLatencyProbe"),
        ("read_only", True),
        ("support_stable_across_boundary", True),
        ("device_name_stable_across_boundary", True),
        ("audio_path_changed", False),
        ("end_to_end_latency_measured", False),
        ("callback_underrun_counter_available", False),
    ):
        if native.get(key) != required:
            raise ValueError(f"{backend}: native timing {key} is invalid")
    timing = verify_timing_pairs(
        native.get("before_boundary"),
        native.get("after_boundary"),
        first_name=f"{backend}.before_boundary",
        second_name=f"{backend}.after_boundary",
    )
    return before, after, events, timing


def verify(
    dry: dict[str, object],
    java: dict[str, object],
    efx: dict[str, object],
    hashes: dict[str, str],
) -> dict[str, object]:
    dry_before, dry_after, _, dry_timing = verify_common(
        dry, "dry", "control"
    )
    java_before, java_after, _, java_timing = verify_common(
        java, "java-fdn", "control"
    )
    efx_before, efx_after, _, efx_timing = verify_common(
        efx, "openal-efx", "reload"
    )
    timing_identity = {
        (
            item["device_name"],
            item["device_clock_supported"],
            item["source_latency_supported"],
        )
        for item in (dry_timing, java_timing, efx_timing)
    }
    if len(timing_identity) != 1:
        raise ValueError("native timing identity changed across lab cases")
    for state in (dry_before, dry_after):
        if (
            state.get("java_reverb_active") is not False
            or state.get("efx_operational") is not False
            or state.get("efx_resources") is not False
        ):
            raise ValueError("dry backend is not dry")
    for state in (java_before, java_after):
        if (
            state.get("java_reverb_active") is not True
            or state.get("efx_operational") is not False
            or state.get("efx_resources") is not False
        ):
            raise ValueError("Java FDN backend is not exclusive")
    for state in (efx_before, efx_after):
        if (
            state.get("java_reverb_active") is not False
            or state.get("efx_operational") is not True
            or state.get("efx_resources") is not True
            or state.get("efx_attached_sources", 0) < 2
            or state.get("efx_source_filters")
            != state.get("efx_attached_sources")
        ):
            raise ValueError("OpenAL EFX backend is not exclusive")
    if efx_after.get("efx_context_rebuilds", 0) <= efx_before.get(
        "efx_context_rebuilds", 0
    ):
        raise ValueError("EFX context did not rebuild in the lab")

    return {
        "schema_version": 1,
        "status": "valid-audio-lab-protocol",
        "source_report_sha256": hashes,
        "cases": ["dry-control", "java-fdn-control", "openal-efx-reload"],
        "efx_context_rebuilds_before": efx_before["efx_context_rebuilds"],
        "efx_context_rebuilds_after": efx_after["efx_context_rebuilds"],
        "efx_post_reload_ready_tick": efx["events"][-2]["tick"],
        "efx_post_reload_ready_latency_ticks": (
            efx["events"][-2]["tick"] - 50
        ),
        "native_timing": {
            "device_name": efx_timing["device_name"],
            "device_clock_supported": efx_timing[
                "device_clock_supported"
            ],
            "source_latency_supported": efx_timing[
                "source_latency_supported"
            ],
            "case_pairs_validated": 6,
        },
        "gates": {
            "three_distinct_markers_started": True,
            "event_timelines_monotonic": True,
            "dry_backend_exclusive": True,
            "java_fdn_backend_exclusive": True,
            "openal_efx_backend_exclusive": True,
            "sound_engine_reload_exercised": True,
            "efx_context_rebuilt_and_sources_reattached": True,
            "backend_override_restored": True,
            "native_timing_hash_bound_in_timelines": True,
            "native_timing_identity_stable": True,
            "end_to_end_latency_measured": False,
            "callback_underrun_counter_available": False,
            "real_audio_capture": False,
            "physical_output_loopback_confirmed": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Minecraft backend selection, marker scheduling, timeline, and "
            "read-only available OpenAL source/device timing telemetry only; "
            "no microphone or loopback recorder was opened, and renderer "
            "latency is not end-to-end latency."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-report", type=Path, required=True)
    parser.add_argument("--java-report", type=Path, required=True)
    parser.add_argument("--efx-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    paths = {
        "dry": args.dry_report,
        "java_fdn": args.java_report,
        "openal_efx": args.efx_report,
    }
    result = verify(
        load(args.dry_report),
        load(args.java_report),
        load(args.efx_report),
        {name: sha256(path) for name, path in paths.items()},
    )
    payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(payload, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
