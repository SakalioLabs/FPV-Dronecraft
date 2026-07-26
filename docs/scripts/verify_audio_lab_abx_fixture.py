#!/usr/bin/env python3
"""Verify the D089 participant-level ABX statistical fixture."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import sys
from pathlib import Path
from typing import Any, Sequence


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
    protocol_path: Path,
    responses_path: Path,
    report_path: Path,
) -> dict[str, Any]:
    protocol = load_json(protocol_path)
    report = load_json(report_path)
    if (
        protocol.get("schema_version") != 1
        or protocol.get("status")
        != "valid-audio-lab-listening-preregistration"
    ):
        raise ValueError("fixture protocol schema/status is invalid")
    if protocol.get("minimum_participants") != 12:
        raise ValueError("fixture must preregister 12 participants")
    if protocol.get("trial_count_per_participant") != 18:
        raise ValueError("fixture protocol must contain 18 trials")
    if protocol.get("participant_is_inference_unit") is not True:
        raise ValueError("fixture must use participant as inference unit")
    if protocol.get("presentation_order_algorithm") != (
        "ascending SHA-256(seed NUL participant_id NUL trial_id)"
    ):
        raise ValueError("fixture presentation order algorithm is invalid")
    if protocol.get("responses_observed_before_preregistration") is not False:
        raise ValueError("fixture protocol must predate responses")
    if protocol.get("release_calibrated") is not False:
        raise ValueError("fixture protocol cannot claim release calibration")
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "valid-audio-lab-listening-analysis"
    ):
        raise ValueError("fixture report schema/status is invalid")
    if report.get("protocol_sha256") != sha256_file(protocol_path):
        raise ValueError("fixture report is detached from protocol")
    if report.get("responses_csv_sha256") != sha256_file(responses_path):
        raise ValueError("fixture report is detached from responses")
    if report.get("participant_count") != 12:
        raise ValueError("fixture report participant count is invalid")
    if report.get("participant_ids_disclosed") is not False:
        raise ValueError("aggregate report must not disclose participant IDs")
    if report.get("complete_trials_per_participant") != 18:
        raise ValueError("fixture report trial coverage is invalid")
    if report.get("total_abx_answers") != 216:
        raise ValueError("fixture report total answer count is invalid")
    if not math.isclose(
        report.get("overall_raw_accuracy", -1.0),
        168 / 216,
        abs_tol=1.0e-12,
    ):
        raise ValueError("fixture raw accuracy changed")
    if report.get("inference_unit") != "participant-majority-per-comparison":
        raise ValueError("fixture report uses the wrong inference unit")
    if report.get("listening_protocol_completed") is not True:
        raise ValueError("fixture must complete the synthetic protocol")
    if report.get("release_calibrated") is not False:
        raise ValueError("fixture cannot claim release calibration")

    discrimination = report.get("discrimination")
    preference = report.get("preference")
    realism = report.get("realism_descriptive")
    if (
        not isinstance(discrimination, list)
        or not isinstance(preference, list)
        or not isinstance(realism, list)
    ):
        raise ValueError("fixture statistical sections must be arrays")
    if len(discrimination) != 6 or len(preference) != 6:
        raise ValueError("fixture must contain six tests per family")
    dry_discrimination = [
        item
        for item in discrimination
        if "dry" in item.get("backend_pair", [])
    ]
    wet_discrimination = [
        item
        for item in discrimination
        if "dry" not in item.get("backend_pair", [])
    ]
    if len(dry_discrimination) != 4 or len(wet_discrimination) != 2:
        raise ValueError("fixture discrimination matrix is invalid")
    for item in dry_discrimination:
        if item.get("participant_majority_correct") != 11:
            raise ValueError("dry comparison majority count changed")
        if item.get("discriminability_detected") is not True:
            raise ValueError("dry comparison should pass synthetic gate")
        if item.get("p_value_holm", 1.0) > 0.05:
            raise ValueError("dry comparison Holm result changed")
    for item in wet_discrimination:
        if item.get("participant_majority_correct") != 6:
            raise ValueError("wet/wet comparison majority count changed")
        if item.get("discriminability_detected") is not False:
            raise ValueError("wet/wet comparison must not pass synthetic gate")

    dry_preferences = [
        item for item in preference if "dry" in item.get("backend_pair", [])
    ]
    wet_preferences = [
        item for item in preference if "dry" not in item.get("backend_pair", [])
    ]
    for item in dry_preferences:
        if item.get("preference_detected") is not True:
            raise ValueError("synthetic wet-over-dry preference must pass")
        if item.get("preferred_backend_direction") == "dry":
            raise ValueError("synthetic preference direction changed")
    for item in wet_preferences:
        if item.get("preference_detected") is not False:
            raise ValueError("balanced wet/wet preference must not pass")
    expected_realism = {"dry": 2.0, "java-fdn": 4.0, "openal-efx": 4.0}
    if len(realism) != 6:
        raise ValueError("fixture realism matrix is incomplete")
    for item in realism:
        if item.get("inferential_claim") is not False:
            raise ValueError("ordinal realism summary cannot be inferential")
        expected = expected_realism.get(item.get("backend"))
        if item.get("mean_realism_1_to_5") != expected:
            raise ValueError("fixture realism mean changed")

    return {
        "schema_version": 1,
        "status": "valid-audio-lab-abx-statistics-fixture",
        "protocol_sha256": sha256_file(protocol_path),
        "responses_csv_sha256": sha256_file(responses_path),
        "source_report_sha256": sha256_file(report_path),
        "participants": 12,
        "trials_per_participant": 18,
        "total_answers": 216,
        "discriminability_tests": 6,
        "preference_tests": 6,
        "synthetic_discriminability_detections": 4,
        "synthetic_preference_detections": 4,
        "gates": {
            "preregistered_before_responses": True,
            "participant_order_randomized": True,
            "participant_is_inference_unit": True,
            "complete_trial_matrix": True,
            "holm_familywise_correction": True,
            "real_participants": False,
            "release_calibrated": False,
        },
        "claim_boundary": (
            "Synthetic response fixture only; no person listened to audio and "
            "the planted effects are not evidence about Minecraft backends."
        ),
    }


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--protocol", type=Path, required=True)
    parser.add_argument("--responses-csv", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        result = verify(args.protocol, args.responses_csv, args.report)
        atomic_write(args.output_json, result)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0
    except (OSError, ValueError, KeyError) as error:
        parser.exit(2, f"error: {error}\n")


if __name__ == "__main__":
    sys.exit(main())
