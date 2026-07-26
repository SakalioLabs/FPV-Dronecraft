#!/usr/bin/env python3
"""Plan and audit repeated Minecraft audio-lab captures without recording.

The tool has no recorder integration.  ``plan`` only creates a deterministic
six-condition schedule.  ``materialize`` accepts already-created D085-D087
artifacts, binds them by SHA-256, summarizes continuity gates, and creates an
opaque ABX listening package plus a separate private answer key.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
import shutil
import sys
import wave
from pathlib import Path
from typing import Any, Sequence


SCHEMA_VERSION = 1
BACKENDS = ("dry", "java-fdn", "openal-efx")
VARIANTS = ("control", "reload")
BACKEND_PAIRS = (
    ("dry", "java-fdn"),
    ("dry", "openal-efx"),
    ("java-fdn", "openal-efx"),
)
MINIMUM_TAKES = 3


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def load_json(path: Path) -> dict[str, Any]:
    def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"{path}: duplicate field {key!r}")
            result[key] = value
        return result

    with path.open("r", encoding="utf-8") as stream:
        value = json.load(
            stream,
            object_pairs_hook=unique_object,
            parse_constant=lambda token: (_ for _ in ()).throw(
                ValueError(f"{path}: non-finite number {token}")
            ),
        )
    if not isinstance(value, dict):
        raise ValueError(f"{path}: root must be an object")
    return value


def atomic_write_json(path: Path, value: dict[str, Any]) -> None:
    if path.exists():
        raise FileExistsError(f"refusing to overwrite existing output: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + f".tmp-{os.getpid()}")
    try:
        temporary.write_text(
            json.dumps(value, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def require_status(
    value: dict[str, Any],
    path: Path,
    expected: str,
    schema_version: int = SCHEMA_VERSION,
) -> None:
    if value.get("schema_version") != schema_version:
        raise ValueError(
            f"{path}: schema_version must be {schema_version}"
        )
    if value.get("status") != expected:
        raise ValueError(f"{path}: expected status {expected!r}")


def require_boolean(value: Any, path: str) -> bool:
    if not isinstance(value, bool):
        raise ValueError(f"{path}: expected a boolean")
    return value


def require_number(value: Any, path: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{path}: expected a number")
    result = float(value)
    if not math.isfinite(result):
        raise ValueError(f"{path}: expected a finite number")
    return result


def artifact_paths(case_id: str) -> dict[str, str]:
    return {
        "wav": f"raw/{case_id}.wav",
        "capture_report": f"capture/{case_id}.json",
        "timeline": f"timeline/{case_id}.json",
        "alignment": f"alignment/{case_id}.json",
        "continuity": f"continuity/{case_id}.json",
    }


def generate_plan(session_id: str, takes: int, seed: str) -> dict[str, Any]:
    if not re.fullmatch(r"[a-z0-9][a-z0-9._-]{2,79}", session_id):
        raise ValueError("session id must be 3-80 lowercase file-safe characters")
    if not MINIMUM_TAKES <= takes <= 20:
        raise ValueError("takes must be in [3, 20]")
    if not seed.strip():
        raise ValueError("seed must be non-blank")

    entries: list[dict[str, Any]] = []
    for take in range(1, takes + 1):
        block: list[dict[str, Any]] = []
        for backend in BACKENDS:
            for variant in VARIANTS:
                case_id = (
                    f"{session_id}-{backend}-{variant}-take{take:02d}"
                )
                rank = sha256_text(
                    f"{seed}\0schedule\0{take}\0{backend}\0{variant}"
                )
                block.append(
                    {
                        "case_id": case_id,
                        "backend": backend,
                        "variant": variant,
                        "take": take,
                        "schedule_rank": rank,
                        "artifacts": artifact_paths(case_id),
                    }
                )
        block.sort(key=lambda item: item["schedule_rank"])
        entries.extend(block)
    for index, entry in enumerate(entries, start=1):
        entry["schedule_index"] = index

    return {
        "schema_version": SCHEMA_VERSION,
        "status": "valid-audio-lab-session-plan",
        "session_id": session_id,
        "schedule_seed": seed,
        "takes_per_condition": takes,
        "condition_count": len(BACKENDS) * len(VARIANTS),
        "capture_count": len(entries),
        "conditions": [
            {"backend": backend, "variant": variant}
            for backend in BACKENDS
            for variant in VARIANTS
        ],
        "captures": entries,
        "recording_started": False,
        "capture_authorized": False,
        "release_calibrated": False,
        "claim_boundary": (
            "Deterministic capture schedule only; generating this plan does "
            "not enumerate an endpoint, authorize recording, or capture audio."
        ),
    }


def validate_plan(value: dict[str, Any], path: Path) -> None:
    require_status(value, path, "valid-audio-lab-session-plan")
    takes = value.get("takes_per_condition")
    if isinstance(takes, bool) or not isinstance(takes, int):
        raise ValueError(f"{path}: takes_per_condition must be an integer")
    if not MINIMUM_TAKES <= takes <= 20:
        raise ValueError(f"{path}: takes_per_condition is outside [3,20]")
    captures = value.get("captures")
    if not isinstance(captures, list):
        raise ValueError(f"{path}: captures must be an array")
    expected = len(BACKENDS) * len(VARIANTS) * takes
    if len(captures) != expected or value.get("capture_count") != expected:
        raise ValueError(f"{path}: capture matrix is incomplete")
    keys: set[tuple[str, str, int]] = set()
    ids: set[str] = set()
    for entry in captures:
        if not isinstance(entry, dict):
            raise ValueError(f"{path}: capture entry must be an object")
        backend = entry.get("backend")
        variant = entry.get("variant")
        take = entry.get("take")
        case_id = entry.get("case_id")
        if backend not in BACKENDS or variant not in VARIANTS:
            raise ValueError(f"{path}: unsupported backend/variant")
        if isinstance(take, bool) or not isinstance(take, int):
            raise ValueError(f"{path}: take must be an integer")
        if not isinstance(case_id, str) or not case_id:
            raise ValueError(f"{path}: case_id must be non-blank")
        key = (backend, variant, take)
        if key in keys or case_id in ids:
            raise ValueError(f"{path}: duplicate capture condition or id")
        keys.add(key)
        ids.add(case_id)
        if entry.get("artifacts") != artifact_paths(case_id):
            raise ValueError(f"{path}: artifact paths are not canonical")
    expected_keys = {
        (backend, variant, take)
        for backend in BACKENDS
        for variant in VARIANTS
        for take in range(1, takes + 1)
    }
    if keys != expected_keys:
        raise ValueError(f"{path}: condition/take matrix is incomplete")
    if value.get("recording_started") is not False:
        raise ValueError(f"{path}: a plan cannot claim recording started")
    if value.get("capture_authorized") is not False:
        raise ValueError(f"{path}: a plan cannot claim recording authorization")


def nested_hash(value: dict[str, Any], section: str, field: str, path: Path) -> str:
    nested = value.get(section)
    if not isinstance(nested, dict):
        raise ValueError(f"{path}: missing {section}")
    result = nested.get(field)
    if not isinstance(result, str) or not re.fullmatch(r"[0-9a-f]{64}", result):
        raise ValueError(f"{path}: invalid {section}.{field}")
    return result


def validate_native_timing(
    timeline: dict[str, Any],
    path: Path,
) -> dict[str, Any]:
    native = timeline.get("native_timing")
    if not isinstance(native, dict):
        raise ValueError(f"{path}: native timing evidence is missing")
    for key, expected in (
        ("probe", "OpenAlClockLatencyProbe"),
        ("read_only", True),
        ("support_stable_across_boundary", True),
        ("device_name_stable_across_boundary", True),
        ("audio_path_changed", False),
        ("end_to_end_latency_measured", False),
        ("callback_underrun_counter_available", False),
    ):
        if native.get(key) != expected:
            raise ValueError(f"{path}: native timing {key} is invalid")

    identity: tuple[str, bool, bool] | None = None
    pair_summaries: dict[str, Any] = {}
    for pair_name in ("before_boundary", "after_boundary"):
        pair = native.get(pair_name)
        if not isinstance(pair, dict):
            raise ValueError(f"{path}: native timing {pair_name} is missing")
        samples = []
        for sample_name in ("first", "second"):
            sample = pair.get(sample_name)
            if not isinstance(sample, dict):
                raise ValueError(
                    f"{path}: {pair_name}.{sample_name} is missing"
                )
            label = f"{path}:{pair_name}.{sample_name}"
            if (
                sample.get("source_found") is not True
                or sample.get("instance_type") != "DroneLoopSoundInstance"
                or sample.get("thread_name") != "Sound engine"
                or sample.get("active_context") is not True
            ):
                raise ValueError(f"{label}: source/context/thread is invalid")
            device = sample.get("device_name")
            clock_supported = sample.get("device_clock_supported")
            latency_supported = sample.get("source_latency_supported")
            if (
                not isinstance(device, str)
                or not device
                or not isinstance(clock_supported, bool)
                or not isinstance(latency_supported, bool)
            ):
                raise ValueError(f"{label}: timing identity is invalid")
            sample_identity = (device, clock_supported, latency_supported)
            if identity is None:
                identity = sample_identity
            elif sample_identity != identity:
                raise ValueError(f"{path}: native timing identity changed")
            if (
                sample.get("native_telemetry_available")
                is not latency_supported
            ):
                raise ValueError(f"{label}: telemetry availability is invalid")
            if (
                sample.get("al_error_code") != 0
                or sample.get("alc_error_code") != 0
            ):
                raise ValueError(f"{label}: OpenAL error is nonzero")
            host_ns = sample.get("host_monotonic_ns")
            if (
                isinstance(host_ns, bool)
                or not isinstance(host_ns, int)
                or host_ns < 0
            ):
                raise ValueError(f"{label}: host timestamp is invalid")
            fields = {
                key: require_number(sample.get(key), f"{label}:{key}")
                for key in (
                    "device_clock_ns",
                    "device_latency_ns",
                    "source_offset_seconds",
                    "source_latency_seconds",
                    "source_clock_offset_seconds",
                    "source_device_clock_seconds",
                )
            }
            if any(value < 0.0 for value in fields.values()):
                raise ValueError(f"{label}: timing value is negative")
            if latency_supported:
                if fields["source_latency_seconds"] >= 1.0:
                    raise ValueError(f"{label}: source latency is implausible")
            elif (
                fields["source_offset_seconds"] != 0.0
                or fields["source_latency_seconds"] != 0.0
            ):
                raise ValueError(f"{label}: unsupported source overclaim")
            if not clock_supported and any(
                fields[key] != 0.0
                for key in (
                    "device_clock_ns",
                    "device_latency_ns",
                    "source_clock_offset_seconds",
                    "source_device_clock_seconds",
                )
            ):
                raise ValueError(f"{label}: unsupported clock overclaim")
            samples.append((sample, fields))

        elapsed = pair.get("host_elapsed_ns")
        computed = (
            samples[1][0]["host_monotonic_ns"]
            - samples[0][0]["host_monotonic_ns"]
        )
        if (
            isinstance(elapsed, bool)
            or not isinstance(elapsed, int)
            or elapsed <= 0
            or elapsed != computed
        ):
            raise ValueError(f"{path}: {pair_name} elapsed is invalid")
        clock_elapsed = pair.get("device_clock_elapsed_ns")
        ratio = require_number(
            pair.get("device_to_host_clock_rate_ratio"),
            f"{path}:{pair_name}.clock_rate_ratio",
        )
        reported_offset_advance = require_number(
            pair.get("source_offset_advance_seconds"),
            f"{path}:{pair_name}.source_offset_advance",
        )
        reported_offset_wrapped = pair.get("source_offset_wrapped")
        if (
            reported_offset_advance < 0.0
            or not isinstance(reported_offset_wrapped, bool)
        ):
            raise ValueError(
                f"{path}: {pair_name} source progress fields are invalid"
            )
        assert identity is not None
        _, clock_supported, latency_supported = identity
        if clock_supported:
            expected_clock = int(
                samples[1][1]["device_clock_ns"]
                - samples[0][1]["device_clock_ns"]
            )
            if (
                not isinstance(clock_elapsed, int)
                or isinstance(clock_elapsed, bool)
                or clock_elapsed <= 0
                or clock_elapsed != expected_clock
                or not math.isclose(
                    ratio,
                    clock_elapsed / elapsed,
                    rel_tol=1e-12,
                    abs_tol=0.0,
                )
            ):
                raise ValueError(f"{path}: {pair_name} clock is invalid")
        elif clock_elapsed != 0 or ratio != 0.0:
            raise ValueError(f"{path}: {pair_name} clock overclaim")
        if latency_supported:
            if pair.get("native_telemetry_validated") is not True:
                raise ValueError(
                    f"{path}: {pair_name} telemetry was not validated"
                )
            raw_offset_advance = (
                samples[1][1]["source_offset_seconds"]
                - samples[0][1]["source_offset_seconds"]
            )
            expected_wrapped = raw_offset_advance <= 0.0
            expected_offset_advance = raw_offset_advance + (
                1.0 if expected_wrapped else 0.0
            )
            host_elapsed_seconds = elapsed / 1e9
            tolerance = max(0.1, host_elapsed_seconds * 0.5)
            if (
                host_elapsed_seconds >= 0.8
                or reported_offset_wrapped is not expected_wrapped
                or not math.isclose(
                    reported_offset_advance,
                    expected_offset_advance,
                    rel_tol=1e-12,
                    abs_tol=1e-12,
                )
                or expected_offset_advance <= 0.0
                or abs(
                    expected_offset_advance - host_elapsed_seconds
                ) > tolerance
            ):
                raise ValueError(
                    f"{path}: {pair_name} source offset advance is inconsistent"
                )
        else:
            if pair.get("native_telemetry_validated") is not False:
                raise ValueError(f"{path}: {pair_name} telemetry overclaim")
            if reported_offset_advance != 0.0 or reported_offset_wrapped:
                raise ValueError(
                    f"{path}: {pair_name} source progress overclaim"
                )
        pair_summaries[pair_name] = {
            "host_elapsed_ns": elapsed,
            "source_offset_advance_seconds": reported_offset_advance,
            "source_offset_wrapped": reported_offset_wrapped,
            "source_latency_seconds": [
                samples[0][1]["source_latency_seconds"],
                samples[1][1]["source_latency_seconds"],
            ],
        }

    assert identity is not None
    return {
        "device_name": identity[0],
        "device_clock_supported": identity[1],
        "source_latency_supported": identity[2],
        "pairs": pair_summaries,
        "end_to_end_latency_measured": False,
        "callback_underrun_counter_available": False,
    }


def inspect_case(
    root: Path,
    entry: dict[str, Any],
) -> dict[str, Any]:
    paths = {
        name: root / relative
        for name, relative in entry["artifacts"].items()
    }
    for path in paths.values():
        if not path.is_file():
            raise FileNotFoundError(f"missing case artifact: {path}")

    wav_hash = sha256_file(paths["wav"])
    capture = load_json(paths["capture_report"])
    timeline = load_json(paths["timeline"])
    alignment = load_json(paths["alignment"])
    continuity = load_json(paths["continuity"])
    require_status(
        capture,
        paths["capture_report"],
        "valid-bounded-audio-capture",
    )
    require_status(
        timeline,
        paths["timeline"],
        "valid-audio-lab-timeline",
        schema_version=2,
    )
    require_status(
        alignment,
        paths["alignment"],
        "valid-audio-lab-alignment-diagnostic",
    )
    require_status(
        continuity,
        paths["continuity"],
        "valid-audio-continuity-diagnostic",
    )

    backend = entry["backend"]
    variant = entry["variant"]
    if timeline.get("backend") != backend or timeline.get("variant") != variant:
        raise ValueError(f"{paths['timeline']}: backend/variant mismatch")
    expected_reload = variant == "reload"
    if timeline.get("sound_engine_reload_exercised") is not expected_reload:
        raise ValueError(f"{paths['timeline']}: reload contract mismatch")
    native_timing = validate_native_timing(timeline, paths["timeline"])
    if (
        alignment.get("timeline_backend") != backend
        or alignment.get("timeline_variant") != variant
    ):
        raise ValueError(f"{paths['alignment']}: backend/variant mismatch")

    capture_wav_hash = nested_hash(
        capture, "audio", "wav_sha256", paths["capture_report"]
    )
    alignment_wav_hash = nested_hash(
        alignment, "wav", "wav_sha256", paths["alignment"]
    )
    continuity_wav_hash = nested_hash(
        continuity, "wav", "wav_sha256", paths["continuity"]
    )
    if {wav_hash, capture_wav_hash, alignment_wav_hash, continuity_wav_hash} != {
        wav_hash
    }:
        raise ValueError(f"{entry['case_id']}: WAV hash binding failed")
    timeline_hash = sha256_file(paths["timeline"])
    if alignment.get("timeline_sha256") != timeline_hash:
        raise ValueError(f"{paths['alignment']}: timeline hash binding failed")
    alignment_hash = sha256_file(paths["alignment"])
    if continuity.get("alignment_report_sha256") != alignment_hash:
        raise ValueError(
            f"{paths['continuity']}: alignment hash binding failed"
        )
    if continuity.get("reload_time_source") != "audio-lab-alignment-report":
        raise ValueError(
            f"{paths['continuity']}: alignment must supply boundary time"
        )

    flags = []
    for document, document_path, real_key in (
        (capture, paths["capture_report"], "real_minecraft_capture"),
        (alignment, paths["alignment"], "real_audio_capture"),
        (continuity, paths["continuity"], "real_minecraft_capture"),
    ):
        flags.append(require_boolean(document.get(real_key), f"{document_path}:{real_key}"))
        flags.append(
            require_boolean(
                document.get("physical_output_loopback_confirmed"),
                f"{document_path}:physical_output_loopback_confirmed",
            )
        )
        if document.get("release_calibrated") is not False:
            raise ValueError(
                f"{document_path}: input cannot claim release calibration"
            )
    all_real_loopback = all(flags)

    align_metrics = alignment.get("alignment")
    analysis = continuity.get("analysis")
    if not isinstance(align_metrics, dict) or not isinstance(analysis, dict):
        raise ValueError(f"{entry['case_id']}: analysis metrics are missing")
    marker_score = require_number(
        align_metrics.get("minimum_marker_score"),
        f"{paths['alignment']}:minimum_marker_score",
    )
    residual_ms = require_number(
        align_metrics.get("maximum_absolute_alignment_residual_ms"),
        f"{paths['alignment']}:alignment residual",
    )
    click_count = require_number(
        analysis.get("reload_guard_click_event_count"),
        f"{paths['continuity']}:click count",
    )
    dropout_count = require_number(
        analysis.get("dropout_event_count"),
        f"{paths['continuity']}:dropout count",
    )
    level_delta = require_number(
        analysis.get("post_pre_level_delta_db"),
        f"{paths['continuity']}:level delta",
    )
    continuity_passed = (
        marker_score >= 0.35
        and residual_ms <= 2.0
        and click_count == 0.0
        and dropout_count == 0.0
        and abs(level_delta) <= 3.0
    )
    return {
        "case_id": entry["case_id"],
        "backend": backend,
        "variant": variant,
        "take": entry["take"],
        "wav_path": str(paths["wav"]),
        "wav_sha256": wav_hash,
        "capture_report_sha256": sha256_file(paths["capture_report"]),
        "timeline_sha256": timeline_hash,
        "alignment_sha256": alignment_hash,
        "continuity_sha256": sha256_file(paths["continuity"]),
        "native_timing": native_timing,
        "device_name": capture.get("device_name"),
        "sample_rate_hz": capture.get("audio", {}).get("sample_rate_hz"),
        "sample_width_bits": capture.get("audio", {}).get(
            "sample_width_bits"
        ),
        "channels": capture.get("audio", {}).get("channels"),
        "minimum_marker_score": marker_score,
        "maximum_alignment_residual_ms": residual_ms,
        "click_event_count": int(click_count),
        "dropout_event_count": int(dropout_count),
        "post_pre_level_delta_db": level_delta,
        "real_loopback_evidence": all_real_loopback,
        "continuity_gate_passed": continuity_passed,
    }


def deterministic_bit(seed: str, label: str) -> int:
    return int(sha256_text(f"{seed}\0{label}")[:2], 16) & 1


def create_blind_package(
    cases: list[dict[str, Any]],
    seed: str,
    output: Path,
) -> tuple[dict[str, Any], dict[str, Any]]:
    if output.exists():
        raise FileExistsError(
            f"refusing to overwrite blind package directory: {output}"
        )
    by_key = {
        (case["backend"], case["variant"], case["take"]): case
        for case in cases
    }
    temporary = output.with_name(output.name + f".tmp-{os.getpid()}")
    if temporary.exists():
        raise FileExistsError(f"temporary blind directory exists: {temporary}")
    temporary.mkdir(parents=True)
    public_trials = []
    private_trials = []
    try:
        trial_number = 0
        pair_occurrences = {pair: 0 for pair in BACKEND_PAIRS}
        x_balance_offset = deterministic_bit(seed, "x-balance")
        for variant in VARIANTS:
            takes = sorted(
                {
                    case["take"]
                    for case in cases
                    if case["variant"] == variant
                }
            )
            for pair in BACKEND_PAIRS:
                for take in takes:
                    trial_number += 1
                    pair_occurrences[pair] += 1
                    token = sha256_text(
                        f"{seed}\0trial\0{variant}\0{pair}\0{take}"
                    )[:16]
                    pair_offset = deterministic_bit(
                        seed, f"pair-balance\0{pair}"
                    )
                    swap = (
                        pair_occurrences[pair] - 1 + pair_offset
                    ) & 1
                    left, right = pair if swap == 0 else pair[::-1]
                    x_side = (
                        "A"
                        if ((trial_number - 1 + x_balance_offset) & 1) == 0
                        else "B"
                    )
                    sources = {
                        "A": by_key[(left, variant, take)],
                        "B": by_key[(right, variant, take)],
                    }
                    sources["X"] = sources[x_side]
                    filenames = {
                        side: f"{token}-{side.lower()}.wav"
                        for side in ("A", "B", "X")
                    }
                    for side, filename in filenames.items():
                        shutil.copyfile(
                            sources[side]["wav_path"],
                            temporary / filename,
                        )
                    trial_id = f"trial-{trial_number:03d}-{token}"
                    public_trials.append(
                        {
                            "trial_id": trial_id,
                            "variant": variant,
                            "a_file": filenames["A"],
                            "b_file": filenames["B"],
                            "x_file": filenames["X"],
                        }
                    )
                    private_trials.append(
                        {
                            "trial_id": trial_id,
                            "variant": variant,
                            "take": take,
                            "a_backend": left,
                            "b_backend": right,
                            "x_is": x_side,
                            "source_case_ids": {
                                side: sources[side]["case_id"]
                                for side in ("A", "B", "X")
                            },
                            "source_wav_sha256": {
                                side: sources[side]["wav_sha256"]
                                for side in ("A", "B", "X")
                            },
                        }
                    )
        public = {
            "schema_version": SCHEMA_VERSION,
            "status": "valid-audio-lab-abx-public-manifest",
            "trial_count": len(public_trials),
            "trials": public_trials,
            "cryptographically_blind": False,
            "claim_boundary": (
                "Opaque filenames for controlled listening only. A participant "
                "with filesystem/hash access can recover that X equals A or B."
            ),
        }
        public_path = temporary / "public-manifest.json"
        public_path.write_text(
            json.dumps(public, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        private = {
            "schema_version": SCHEMA_VERSION,
            "status": "valid-audio-lab-abx-private-key",
            "trial_count": len(private_trials),
            "public_manifest_sha256": sha256_file(public_path),
            "trials": private_trials,
            "release_calibrated": False,
        }
        (temporary / "private-answer-key.json").write_text(
            json.dumps(private, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        os.replace(temporary, output)
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)
    return public, private


def materialize(
    plan_path: Path,
    session_root: Path,
    output_report: Path,
    blind_directory: Path,
) -> dict[str, Any]:
    if output_report.exists():
        raise FileExistsError(
            f"refusing to overwrite existing output: {output_report}"
        )
    plan = load_json(plan_path)
    validate_plan(plan, plan_path)
    cases = [inspect_case(session_root, entry) for entry in plan["captures"]]
    devices = sorted({str(case["device_name"]) for case in cases})
    formats = sorted(
        {
            (
                case["sample_rate_hz"],
                case["sample_width_bits"],
                case["channels"],
            )
            for case in cases
        },
        key=str,
    )
    evidence_complete = len(cases) == plan["capture_count"]
    same_capture_chain = len(devices) == 1 and len(formats) == 1
    real_loopback_complete = all(
        case["real_loopback_evidence"] for case in cases
    )
    continuity_passed = all(
        case["continuity_gate_passed"] for case in cases
    )
    timing_identities = {
        (
            case["native_timing"]["device_name"],
            case["native_timing"]["device_clock_supported"],
            case["native_timing"]["source_latency_supported"],
        )
        for case in cases
    }
    if len(timing_identities) != 1:
        raise ValueError("native timing identity changed across session")
    timing_identity = next(iter(timing_identities))
    public, private = create_blind_package(
        cases,
        plan["schedule_seed"],
        blind_directory,
    )
    public_path = blind_directory / "public-manifest.json"
    private_path = blind_directory / "private-answer-key.json"
    report = {
        "schema_version": SCHEMA_VERSION,
        "status": "valid-audio-lab-session-evidence",
        "session_id": plan["session_id"],
        "plan_sha256": sha256_file(plan_path),
        "capture_count": len(cases),
        "takes_per_condition": plan["takes_per_condition"],
        "condition_count": plan["condition_count"],
        "evidence_complete": evidence_complete,
        "same_capture_chain": same_capture_chain,
        "real_loopback_evidence_complete": real_loopback_complete,
        "continuity_gate_passed": continuity_passed,
        "native_timing_evidence_complete": True,
        "native_timing_identity": {
            "device_name": timing_identity[0],
            "device_clock_supported": timing_identity[1],
            "source_latency_supported": timing_identity[2],
            "end_to_end_latency_measured": False,
            "callback_underrun_counter_available": False,
        },
        "devices": devices,
        "formats": [
            {
                "sample_rate_hz": item[0],
                "sample_width_bits": item[1],
                "channels": item[2],
            }
            for item in formats
        ],
        "cases": cases,
        "abx": {
            "trial_count": public["trial_count"],
            "public_manifest_sha256": sha256_file(public_path),
            "private_answer_key_sha256": sha256_file(private_path),
            "answers_collected": False,
            "listening_gate_passed": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Artifact completeness, hash-bound read-only OpenAL timing, "
            "continuity metrics, and ABX packaging only. Renderer latency is "
            "not end-to-end latency. No listening answers, acoustic "
            "calibration, callback-underrun counter, physical device-switch "
            "test, or matched-RIR validation is supplied by this report."
        ),
    }
    atomic_write_json(output_report, report)
    return report


def write_pcm24_fixture(path: Path, key: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    seed = int(sha256_text(key)[:8], 16)
    frames = bytearray()
    for index in range(96):
        value = ((seed + index * 104729) % (1 << 22)) - (1 << 21)
        if value < 0:
            value += 1 << 24
        frames.extend((value & 0xFF, (value >> 8) & 0xFF, (value >> 16) & 0xFF))
    with wave.open(str(path), "wb") as target:
        target.setnchannels(1)
        target.setsampwidth(3)
        target.setframerate(48_000)
        target.writeframes(bytes(frames))


def fixture_native_timing(case_id: str) -> dict[str, Any]:
    base = 1_000_000_000 + int(sha256_text(case_id)[:8], 16)
    elapsed = 200_000_000

    def sample(host_ns: int, offset: float) -> dict[str, Any]:
        return {
            "source_found": True,
            "instance_type": "DroneLoopSoundInstance",
            "thread_name": "Sound engine",
            "host_monotonic_ns": host_ns,
            "active_context": True,
            "device_name": "OpenAL Soft fixture",
            "device_clock_supported": False,
            "source_latency_supported": True,
            "native_telemetry_available": True,
            "device_clock_ns": 0,
            "device_latency_ns": 0,
            "source_offset_seconds": offset,
            "source_latency_seconds": 0.051,
            "source_clock_offset_seconds": 0.0,
            "source_device_clock_seconds": 0.0,
            "al_error_code": 0,
            "alc_error_code": 0,
        }

    def pair(start_ns: int) -> dict[str, Any]:
        return {
            "first": sample(start_ns, 0.08),
            "second": sample(start_ns + elapsed, 0.28),
            "host_elapsed_ns": elapsed,
            "device_clock_elapsed_ns": 0,
            "device_to_host_clock_rate_ratio": 0.0,
            "source_offset_advance_seconds": 0.20,
            "source_offset_wrapped": False,
            "native_telemetry_validated": True,
        }

    return {
        "probe": "OpenAlClockLatencyProbe",
        "read_only": True,
        "before_boundary": pair(base),
        "after_boundary": pair(base + 1_000_000_000),
        "support_stable_across_boundary": True,
        "device_name_stable_across_boundary": True,
        "audio_path_changed": False,
        "end_to_end_latency_measured": False,
        "callback_underrun_counter_available": False,
    }


def generate_fixture(
    root: Path,
    plan_path: Path,
    report_path: Path,
    blind_directory: Path,
) -> dict[str, Any]:
    if root.exists():
        raise FileExistsError(f"refusing to overwrite fixture root: {root}")
    root.mkdir(parents=True)
    plan = generate_plan("audio-lab-fixture", 3, "d088-fixture-seed")
    atomic_write_json(plan_path, plan)
    for entry in plan["captures"]:
        paths = {
            name: root / relative
            for name, relative in entry["artifacts"].items()
        }
        wav = paths["wav"]
        write_pcm24_fixture(wav, entry["case_id"])
        wav_hash = sha256_file(wav)
        timeline = {
            "schema_version": 2,
            "status": "valid-audio-lab-timeline",
            "backend": entry["backend"],
            "variant": entry["variant"],
            "sound_engine_reload_exercised": entry["variant"] == "reload",
            "native_timing": fixture_native_timing(entry["case_id"]),
            "real_audio_capture": False,
            "physical_output_loopback_confirmed": False,
            "release_calibrated": False,
        }
        atomic_write_json(paths["timeline"], timeline)
        capture = {
            "schema_version": 1,
            "status": "valid-bounded-audio-capture",
            "device_name": "deterministic-fixture-endpoint",
            "audio": {
                "sample_rate_hz": 48_000,
                "sample_width_bits": 24,
                "channels": 1,
                "wav_sha256": wav_hash,
            },
            "real_minecraft_capture": False,
            "physical_output_loopback_confirmed": False,
            "release_calibrated": False,
        }
        atomic_write_json(paths["capture_report"], capture)
        alignment = {
            "schema_version": 1,
            "status": "valid-audio-lab-alignment-diagnostic",
            "timeline_backend": entry["backend"],
            "timeline_variant": entry["variant"],
            "timeline_sha256": sha256_file(paths["timeline"]),
            "wav": {"wav_sha256": wav_hash},
            "alignment": {
                "minimum_marker_score": 0.98,
                "maximum_absolute_alignment_residual_ms": 0.4,
            },
            "real_audio_capture": False,
            "physical_output_loopback_confirmed": False,
            "release_calibrated": False,
        }
        atomic_write_json(paths["alignment"], alignment)
        click_count = 1 if entry["variant"] == "reload" else 0
        continuity = {
            "schema_version": 1,
            "status": "valid-audio-continuity-diagnostic",
            "alignment_report_sha256": sha256_file(paths["alignment"]),
            "reload_time_source": "audio-lab-alignment-report",
            "wav": {"wav_sha256": wav_hash},
            "analysis": {
                "reload_guard_click_event_count": click_count,
                "dropout_event_count": 0,
                "post_pre_level_delta_db": 0.1,
            },
            "real_minecraft_capture": False,
            "physical_output_loopback_confirmed": False,
            "release_calibrated": False,
        }
        atomic_write_json(paths["continuity"], continuity)
    return materialize(plan_path, root, report_path, blind_directory)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    plan = subparsers.add_parser("plan")
    plan.add_argument("--session-id", required=True)
    plan.add_argument("--takes", type=int, default=3)
    plan.add_argument("--seed", required=True)
    plan.add_argument("--output-json", type=Path, required=True)
    material = subparsers.add_parser("materialize")
    material.add_argument("--plan-json", type=Path, required=True)
    material.add_argument("--session-root", type=Path, required=True)
    material.add_argument("--output-json", type=Path, required=True)
    material.add_argument("--blind-directory", type=Path, required=True)
    fixture = subparsers.add_parser("fixture")
    fixture.add_argument("--output-root", type=Path, required=True)
    fixture.add_argument("--output-plan", type=Path, required=True)
    fixture.add_argument("--output-json", type=Path, required=True)
    fixture.add_argument("--blind-directory", type=Path, required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        if args.command == "plan":
            value = generate_plan(args.session_id, args.takes, args.seed)
            atomic_write_json(args.output_json, value)
        elif args.command == "materialize":
            value = materialize(
                args.plan_json,
                args.session_root,
                args.output_json,
                args.blind_directory,
            )
        else:
            value = generate_fixture(
                args.output_root,
                args.output_plan,
                args.output_json,
                args.blind_directory,
            )
        summary = {
            "status": value["status"],
            "session_id": value.get("session_id"),
            "captures": value.get("capture_count"),
            "conditions": value.get("condition_count"),
            "takes_per_condition": value.get("takes_per_condition"),
            "recording_started": value.get("recording_started", False),
            "real_loopback_evidence_complete": value.get(
                "real_loopback_evidence_complete",
                False,
            ),
            "continuity_gate_passed": value.get(
                "continuity_gate_passed",
                False,
            ),
            "release_calibrated": False,
        }
        print(json.dumps(summary, ensure_ascii=False, sort_keys=True))
        return 0
    except (OSError, ValueError, KeyError) as error:
        parser.exit(2, f"error: {error}\n")


if __name__ == "__main__":
    sys.exit(main())
