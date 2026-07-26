#!/usr/bin/env python3
"""Independently verify the deterministic D088 session/ABX fixture."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Any, Sequence


BACKENDS = {"dry", "java-fdn", "openal-efx"}
VARIANTS = {"control", "reload"}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path}: root must be an object")
    return value


def atomic_write(path: Path, value: dict[str, Any]) -> None:
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


def verify(
    report_path: Path,
    plan_path: Path,
    blind_directory: Path,
) -> dict[str, Any]:
    report = load_json(report_path)
    plan = load_json(plan_path)
    public_path = blind_directory / "public-manifest.json"
    private_path = blind_directory / "private-answer-key.json"
    public = load_json(public_path)
    private = load_json(private_path)
    if (
        report.get("schema_version") != 1
        or report.get("status") != "valid-audio-lab-session-evidence"
    ):
        raise ValueError("session report schema/status is invalid")
    if (
        plan.get("schema_version") != 1
        or plan.get("status") != "valid-audio-lab-session-plan"
    ):
        raise ValueError("session plan schema/status is invalid")
    if report.get("plan_sha256") != sha256_file(plan_path):
        raise ValueError("session report is detached from the plan")
    if report.get("capture_count") != 18:
        raise ValueError("fixture must contain exactly 18 captures")
    if report.get("takes_per_condition") != 3:
        raise ValueError("fixture must contain three takes per condition")
    if report.get("condition_count") != 6:
        raise ValueError("fixture must contain six conditions")
    if report.get("evidence_complete") is not True:
        raise ValueError("fixture evidence must be complete")
    if report.get("same_capture_chain") is not True:
        raise ValueError("fixture must use one capture chain")
    if report.get("real_loopback_evidence_complete") is not False:
        raise ValueError("fixture cannot claim real loopback evidence")
    if report.get("continuity_gate_passed") is not False:
        raise ValueError("positive-click fixture must fail the continuity gate")
    if report.get("native_timing_evidence_complete") is not True:
        raise ValueError("fixture native timing evidence must be complete")
    timing_identity = report.get("native_timing_identity")
    if not isinstance(timing_identity, dict):
        raise ValueError("fixture native timing identity is missing")
    if (
        timing_identity.get("device_name") != "OpenAL Soft fixture"
        or timing_identity.get("device_clock_supported") is not False
        or timing_identity.get("source_latency_supported") is not True
        or timing_identity.get("end_to_end_latency_measured") is not False
        or timing_identity.get("callback_underrun_counter_available")
        is not False
    ):
        raise ValueError("fixture native timing identity is invalid")
    if report.get("release_calibrated") is not False:
        raise ValueError("fixture cannot claim release calibration")

    cases = report.get("cases")
    if not isinstance(cases, list) or len(cases) != 18:
        raise ValueError("fixture case array is incomplete")
    matrix = Counter()
    for case in cases:
        if not isinstance(case, dict):
            raise ValueError("fixture case must be an object")
        backend = case.get("backend")
        variant = case.get("variant")
        take = case.get("take")
        if backend not in BACKENDS or variant not in VARIANTS:
            raise ValueError("fixture contains an invalid condition")
        if take not in {1, 2, 3}:
            raise ValueError("fixture contains an invalid take")
        matrix[(backend, variant)] += 1
        if case.get("real_loopback_evidence") is not False:
            raise ValueError("fixture case overclaims loopback evidence")
        timing = case.get("native_timing")
        if not isinstance(timing, dict):
            raise ValueError("fixture case native timing is missing")
        if (
            timing.get("device_name") != timing_identity["device_name"]
            or timing.get("device_clock_supported")
            is not timing_identity["device_clock_supported"]
            or timing.get("source_latency_supported")
            is not timing_identity["source_latency_supported"]
            or timing.get("end_to_end_latency_measured") is not False
            or timing.get("callback_underrun_counter_available") is not False
        ):
            raise ValueError("fixture case native timing identity is invalid")
        pairs = timing.get("pairs")
        if not isinstance(pairs, dict) or set(pairs) != {
            "before_boundary",
            "after_boundary",
        }:
            raise ValueError("fixture case native timing pairs are incomplete")
        for pair in pairs.values():
            if (
                not isinstance(pair, dict)
                or not isinstance(pair.get("host_elapsed_ns"), int)
                or pair.get("host_elapsed_ns", 0) <= 0
                or pair.get("source_latency_seconds") != [0.051, 0.051]
            ):
                raise ValueError("fixture case native timing pair is invalid")
    expected_matrix = {
        (backend, variant): 3
        for backend in BACKENDS
        for variant in VARIANTS
    }
    if dict(matrix) != expected_matrix:
        raise ValueError("fixture condition matrix is not balanced")

    abx = report.get("abx")
    if not isinstance(abx, dict) or abx.get("trial_count") != 18:
        raise ValueError("session report ABX summary is invalid")
    if abx.get("answers_collected") is not False:
        raise ValueError("fixture cannot claim listening answers")
    if abx.get("listening_gate_passed") is not False:
        raise ValueError("fixture cannot claim a listening gate")
    if (
        public.get("status") != "valid-audio-lab-abx-public-manifest"
        or public.get("trial_count") != 18
        or public.get("cryptographically_blind") is not False
    ):
        raise ValueError("public ABX manifest contract is invalid")
    if (
        private.get("status") != "valid-audio-lab-abx-private-key"
        or private.get("trial_count") != 18
        or private.get("release_calibrated") is not False
    ):
        raise ValueError("private ABX key contract is invalid")
    public_trials = public.get("trials")
    private_trials = private.get("trials")
    if not isinstance(public_trials, list) or not isinstance(private_trials, list):
        raise ValueError("ABX trials must be arrays")
    private_by_id = {
        trial.get("trial_id"): trial
        for trial in private_trials
        if isinstance(trial, dict)
    }
    if len(private_by_id) != 18:
        raise ValueError("private ABX trial IDs are not unique")
    x_sides = Counter()
    pair_a_counts: Counter[tuple[str, str]] = Counter()
    for trial in public_trials:
        if not isinstance(trial, dict):
            raise ValueError("public ABX trial must be an object")
        if set(trial) != {
            "trial_id",
            "variant",
            "a_file",
            "b_file",
            "x_file",
        }:
            raise ValueError("public trial leaks fields or is incomplete")
        trial_id = trial["trial_id"]
        private_trial = private_by_id.get(trial_id)
        if not isinstance(private_trial, dict):
            raise ValueError("public/private ABX trial mismatch")
        if trial.get("variant") not in VARIANTS:
            raise ValueError("public trial variant is invalid")
        filenames = {
            side: trial[f"{side.lower()}_file"]
            for side in ("A", "B", "X")
        }
        for filename in filenames.values():
            if not isinstance(filename, str) or not re.fullmatch(
                r"[0-9a-f]{16}-[abx]\.wav", filename
            ):
                raise ValueError("public ABX filename is not opaque/canonical")
            if not (blind_directory / filename).is_file():
                raise ValueError("public ABX audio file is missing")
        x_side = private_trial.get("x_is")
        if x_side not in {"A", "B"}:
            raise ValueError("private X answer is invalid")
        x_sides[x_side] += 1
        a_backend = private_trial.get("a_backend")
        b_backend = private_trial.get("b_backend")
        if a_backend not in BACKENDS or b_backend not in BACKENDS:
            raise ValueError("private backend answer is invalid")
        pair_a_counts[(a_backend, b_backend)] += 1
        hashes = private_trial.get("source_wav_sha256")
        if not isinstance(hashes, dict):
            raise ValueError("private source hashes are missing")
        if hashes.get("X") != hashes.get(x_side):
            raise ValueError("private X answer does not bind its source hash")
        for side, filename in filenames.items():
            if sha256_file(blind_directory / filename) != hashes.get(side):
                raise ValueError("blind audio copy is detached from source hash")
    if x_sides != {"A": 9, "B": 9}:
        raise ValueError("X answers are not exactly balanced")
    unordered_pairs = Counter()
    for (a_backend, b_backend), count in pair_a_counts.items():
        unordered_pairs[tuple(sorted((a_backend, b_backend)))] += count
    if len(unordered_pairs) != 3 or set(unordered_pairs.values()) != {6}:
        raise ValueError("backend pair trials are not balanced")
    if private.get("public_manifest_sha256") != sha256_file(public_path):
        raise ValueError("private key is detached from public manifest")
    if abx.get("public_manifest_sha256") != sha256_file(public_path):
        raise ValueError("public ABX manifest hash is detached")
    if abx.get("private_answer_key_sha256") != sha256_file(private_path):
        raise ValueError("private ABX key hash is detached")

    return {
        "schema_version": 1,
        "status": "valid-audio-lab-session-fixture",
        "source_report_sha256": sha256_file(report_path),
        "plan_sha256": sha256_file(plan_path),
        "public_manifest_sha256": sha256_file(public_path),
        "private_answer_key_sha256": sha256_file(private_path),
        "captures": 18,
        "conditions": 6,
        "takes_per_condition": 3,
        "abx_trials": 18,
        "x_answer_balance": {"A": 9, "B": 9},
        "gates": {
            "hash_chain_complete": True,
            "condition_matrix_complete": True,
            "same_capture_chain": True,
            "native_timing_hash_bound": True,
            "native_timing_identity_stable": True,
            "end_to_end_latency_measured": False,
            "callback_underrun_counter_available": False,
            "abx_pairs_balanced": True,
            "real_loopback_evidence_complete": False,
            "continuity_gate_passed": False,
            "listening_gate_passed": False,
            "release_calibrated": False,
        },
        "claim_boundary": (
            "Deterministic synthetic session fixture with hash-bound timing "
            "shape only; no endpoint was opened, no audio was recorded, and "
            "no listener answered an ABX trial."
        ),
    }


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--blind-directory", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        result = verify(
            args.report,
            args.plan,
            args.blind_directory,
        )
        atomic_write(args.output_json, result)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0
    except (OSError, ValueError, KeyError) as error:
        parser.exit(2, f"error: {error}\n")


if __name__ == "__main__":
    sys.exit(main())
