#!/usr/bin/env python3
"""Pre-register and score participant-level Minecraft audio-lab ABX results."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import os
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any, Sequence


SCHEMA_VERSION = 1
RESPONSE_FIELDS = (
    "participant_id",
    "trial_id",
    "presentation_index",
    "x_response",
    "preference",
    "realism_a",
    "realism_b",
    "extra_replays",
)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


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


def validate_manifests(
    public_path: Path,
    private_path: Path,
) -> tuple[dict[str, Any], dict[str, Any]]:
    public = load_json(public_path)
    private = load_json(private_path)
    if (
        public.get("schema_version") != 1
        or public.get("status")
        != "valid-audio-lab-abx-public-manifest"
    ):
        raise ValueError("public ABX manifest schema/status is invalid")
    if (
        private.get("schema_version") != 1
        or private.get("status") != "valid-audio-lab-abx-private-key"
    ):
        raise ValueError("private ABX key schema/status is invalid")
    if private.get("public_manifest_sha256") != sha256_file(public_path):
        raise ValueError("private ABX key is detached from public manifest")
    public_trials = public.get("trials")
    private_trials = private.get("trials")
    if not isinstance(public_trials, list) or not isinstance(private_trials, list):
        raise ValueError("ABX trials must be arrays")
    if (
        len(public_trials) != public.get("trial_count")
        or len(private_trials) != private.get("trial_count")
        or len(public_trials) != len(private_trials)
    ):
        raise ValueError("public/private ABX trial counts differ")
    public_ids = {
        trial.get("trial_id")
        for trial in public_trials
        if isinstance(trial, dict)
    }
    private_ids = {
        trial.get("trial_id")
        for trial in private_trials
        if isinstance(trial, dict)
    }
    if (
        len(public_ids) != len(public_trials)
        or public_ids != private_ids
        or None in public_ids
    ):
        raise ValueError("public/private ABX trial IDs differ")
    for trial in private_trials:
        if not isinstance(trial, dict):
            raise ValueError("private ABX trial must be an object")
        if trial.get("x_is") not in {"A", "B"}:
            raise ValueError("private ABX X answer is invalid")
        a_backend = trial.get("a_backend")
        b_backend = trial.get("b_backend")
        if (
            not isinstance(a_backend, str)
            or not isinstance(b_backend, str)
            or a_backend == b_backend
        ):
            raise ValueError("private ABX backend pair is invalid")
    return public, private


def create_protocol(
    session_report_path: Path,
    public_path: Path,
    private_path: Path,
    minimum_participants: int,
    maximum_extra_replays: int,
    presentation_order_seed: str,
    alpha: float,
    minimum_majority_fraction: float,
) -> dict[str, Any]:
    session = load_json(session_report_path)
    if (
        session.get("schema_version") != 1
        or session.get("status") != "valid-audio-lab-session-evidence"
    ):
        raise ValueError("D088 session report schema/status is invalid")
    public, private = validate_manifests(public_path, private_path)
    abx = session.get("abx")
    if not isinstance(abx, dict):
        raise ValueError("D088 session report has no ABX section")
    if (
        abx.get("public_manifest_sha256") != sha256_file(public_path)
        or abx.get("private_answer_key_sha256") != sha256_file(private_path)
    ):
        raise ValueError("D088 session report is detached from ABX artifacts")
    if not 12 <= minimum_participants <= 200:
        raise ValueError("minimum participants must be in [12, 200]")
    if not 0 <= maximum_extra_replays <= 10:
        raise ValueError("maximum extra replays must be in [0, 10]")
    if not presentation_order_seed.strip():
        raise ValueError("presentation order seed must be non-blank")
    if not 0.001 <= alpha <= 0.1:
        raise ValueError("alpha must be in [0.001, 0.1]")
    if not 0.5 < minimum_majority_fraction <= 1.0:
        raise ValueError("minimum majority fraction must be in (0.5, 1]")
    return {
        "schema_version": SCHEMA_VERSION,
        "status": "valid-audio-lab-listening-preregistration",
        "session_report_sha256": sha256_file(session_report_path),
        "public_manifest_sha256": sha256_file(public_path),
        "private_answer_key_sha256": sha256_file(private_path),
        "trial_count_per_participant": public["trial_count"],
        "minimum_participants": minimum_participants,
        "maximum_extra_replays_per_trial": maximum_extra_replays,
        "presentation_order_seed": presentation_order_seed,
        "presentation_order_algorithm": (
            "ascending SHA-256(seed NUL participant_id NUL trial_id)"
        ),
        "familywise_alpha": alpha,
        "minimum_participant_majority_fraction": minimum_majority_fraction,
        "minimum_nontied_participants_per_comparison": math.ceil(
            minimum_participants * 0.8
        ),
        "discriminability_test": (
            "one-sided exact binomial on participant-majority correctness"
        ),
        "preference_test": (
            "two-sided exact binomial on participant-majority preference"
        ),
        "multiplicity_correction": (
            "Holm within six discriminability tests and separately within "
            "six preference tests"
        ),
        "participant_is_inference_unit": True,
        "response_fields": list(RESPONSE_FIELDS),
        "responses_observed_before_preregistration": False,
        "release_calibrated": False,
        "claim_boundary": (
            "Statistical and data-integrity preregistration only; this file "
            "contains no participant responses or listening result."
        ),
    }


def integer_field(value: str, path: str, minimum: int, maximum: int) -> int:
    try:
        parsed = int(value)
    except ValueError as error:
        raise ValueError(f"{path}: expected an integer") from error
    if str(parsed) != value.strip() or not minimum <= parsed <= maximum:
        raise ValueError(f"{path}: expected integer in [{minimum},{maximum}]")
    return parsed


def load_responses(
    path: Path,
    trial_ids: set[str],
    maximum_extra_replays: int,
    presentation_order_seed: str,
) -> dict[str, dict[str, dict[str, Any]]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if tuple(reader.fieldnames or ()) != RESPONSE_FIELDS:
            raise ValueError(
                f"{path}: response CSV fields must exactly equal "
                f"{list(RESPONSE_FIELDS)}"
            )
        participants: dict[str, dict[str, dict[str, Any]]] = defaultdict(dict)
        for row_number, row in enumerate(reader, start=2):
            participant = row["participant_id"].strip()
            if (
                not participant
                or len(participant) > 80
                or any(character.isspace() for character in participant)
            ):
                raise ValueError(
                    f"{path}:{row_number}: invalid pseudonymous participant id"
                )
            trial_id = row["trial_id"].strip()
            if trial_id not in trial_ids:
                raise ValueError(
                    f"{path}:{row_number}: unknown trial id {trial_id!r}"
                )
            if trial_id in participants[participant]:
                raise ValueError(
                    f"{path}:{row_number}: duplicate participant/trial response"
                )
            x_response = row["x_response"].strip().upper()
            preference = row["preference"].strip().upper()
            if x_response not in {"A", "B"}:
                raise ValueError(
                    f"{path}:{row_number}: x_response must be A or B"
                )
            if preference not in {"A", "B", "NONE"}:
                raise ValueError(
                    f"{path}:{row_number}: preference must be A, B, or NONE"
                )
            participants[participant][trial_id] = {
                "presentation_index": integer_field(
                    row["presentation_index"],
                    f"{path}:{row_number}:presentation_index",
                    1,
                    len(trial_ids),
                ),
                "x_response": x_response,
                "preference": preference,
                "realism_a": integer_field(
                    row["realism_a"], f"{path}:{row_number}:realism_a", 1, 5
                ),
                "realism_b": integer_field(
                    row["realism_b"], f"{path}:{row_number}:realism_b", 1, 5
                ),
                "extra_replays": integer_field(
                    row["extra_replays"],
                    f"{path}:{row_number}:extra_replays",
                    0,
                    maximum_extra_replays,
                ),
            }
    if not participants:
        raise ValueError(f"{path}: response CSV is empty")
    for participant, rows in participants.items():
        actual = set(rows)
        if actual != trial_ids:
            missing = sorted(trial_ids - actual)
            extra = sorted(actual - trial_ids)
            raise ValueError(
                f"{path}: participant {participant!r} trial coverage mismatch; "
                f"missing={missing}, extra={extra}"
            )
        expected = presentation_order(
            presentation_order_seed,
            participant,
            trial_ids,
        )
        actual_by_index = {
            row["presentation_index"]: trial_id
            for trial_id, row in rows.items()
        }
        if len(actual_by_index) != len(trial_ids):
            raise ValueError(
                f"{path}: participant {participant!r} has duplicate "
                "presentation indices"
            )
        actual = [actual_by_index[index] for index in range(1, len(trial_ids) + 1)]
        if actual != expected:
            raise ValueError(
                f"{path}: participant {participant!r} presentation order "
                "does not match preregistration"
            )
    return dict(participants)


def presentation_order(
    seed: str,
    participant_id: str,
    trial_ids: set[str],
) -> list[str]:
    return sorted(
        trial_ids,
        key=lambda trial_id: hashlib.sha256(
            f"{seed}\0{participant_id}\0{trial_id}".encode("utf-8")
        ).hexdigest(),
    )


def binomial_upper_tail(successes: int, trials: int) -> float:
    if not 0 <= successes <= trials:
        raise ValueError("binomial successes are outside trial count")
    return sum(
        math.comb(trials, value) for value in range(successes, trials + 1)
    ) / (2.0**trials)


def binomial_two_sided(successes: int, trials: int) -> float:
    if trials == 0:
        return 1.0
    extreme = min(successes, trials - successes)
    lower = sum(
        math.comb(trials, value) for value in range(0, extreme + 1)
    ) / (2.0**trials)
    return min(1.0, 2.0 * lower)


def holm_adjust(records: list[dict[str, Any]], alpha: float) -> None:
    ordered = sorted(
        enumerate(records),
        key=lambda item: (item[1]["p_value_raw"], item[1]["comparison_id"]),
    )
    running = 0.0
    count = len(records)
    for rank, (_, record) in enumerate(ordered):
        adjusted = min(1.0, (count - rank) * record["p_value_raw"])
        running = max(running, adjusted)
        record["p_value_holm"] = running
        record["statistically_significant"] = running <= alpha


def majority(values: list[bool]) -> bool | None:
    successes = sum(values)
    if successes * 2 == len(values):
        return None
    return successes * 2 > len(values)


def score(
    protocol_path: Path,
    session_report_path: Path,
    public_path: Path,
    private_path: Path,
    responses_path: Path,
) -> dict[str, Any]:
    protocol = load_json(protocol_path)
    if (
        protocol.get("schema_version") != 1
        or protocol.get("status")
        != "valid-audio-lab-listening-preregistration"
    ):
        raise ValueError("listening protocol schema/status is invalid")
    if protocol.get("responses_observed_before_preregistration") is not False:
        raise ValueError("protocol must predate participant responses")
    if protocol.get("participant_is_inference_unit") is not True:
        raise ValueError("protocol must use participant as inference unit")
    if protocol.get("session_report_sha256") != sha256_file(session_report_path):
        raise ValueError("protocol is detached from D088 session report")
    public, private = validate_manifests(public_path, private_path)
    if (
        protocol.get("public_manifest_sha256") != sha256_file(public_path)
        or protocol.get("private_answer_key_sha256")
        != sha256_file(private_path)
    ):
        raise ValueError("protocol is detached from ABX artifacts")
    alpha = float(protocol["familywise_alpha"])
    minimum_participants = int(protocol["minimum_participants"])
    minimum_nontied = int(
        protocol["minimum_nontied_participants_per_comparison"]
    )
    minimum_fraction = float(
        protocol["minimum_participant_majority_fraction"]
    )
    private_by_id = {
        trial["trial_id"]: trial for trial in private["trials"]
    }
    trial_ids = set(private_by_id)
    participants = load_responses(
        responses_path,
        trial_ids,
        int(protocol["maximum_extra_replays_per_trial"]),
        str(protocol["presentation_order_seed"]),
    )
    if len(participants) < minimum_participants:
        raise ValueError(
            f"participant count {len(participants)} is below preregistered "
            f"minimum {minimum_participants}"
        )

    grouped_trials: dict[tuple[str, str, str], list[str]] = defaultdict(list)
    for trial_id, trial in private_by_id.items():
        pair = tuple(sorted((trial["a_backend"], trial["b_backend"])))
        key = (trial["variant"], pair[0], pair[1])
        grouped_trials[key].append(trial_id)
    if len(grouped_trials) != 6:
        raise ValueError("ABX key must contain six variant/backend comparisons")

    discrimination: list[dict[str, Any]] = []
    preference: list[dict[str, Any]] = []
    realism_values: dict[tuple[str, str], list[float]] = defaultdict(list)
    total_correct = 0
    total_answers = 0
    total_extra_replays = 0
    for participant_rows in participants.values():
        for trial_id, response in participant_rows.items():
            trial = private_by_id[trial_id]
            total_correct += response["x_response"] == trial["x_is"]
            total_answers += 1
            total_extra_replays += response["extra_replays"]
            realism_values[
                (trial["variant"], trial["a_backend"])
            ].append(float(response["realism_a"]))
            realism_values[
                (trial["variant"], trial["b_backend"])
            ].append(float(response["realism_b"]))

    for (variant, left_backend, right_backend), ids in sorted(
        grouped_trials.items()
    ):
        majority_correct: list[bool] = []
        majority_preference: list[bool] = []
        for participant_rows in participants.values():
            correctness = [
                participant_rows[trial_id]["x_response"]
                == private_by_id[trial_id]["x_is"]
                for trial_id in ids
            ]
            correct_majority = majority(correctness)
            if correct_majority is not None:
                majority_correct.append(correct_majority)

            preference_votes = []
            for trial_id in ids:
                response = participant_rows[trial_id]
                selected = response["preference"]
                if selected == "NONE":
                    continue
                trial = private_by_id[trial_id]
                backend = (
                    trial["a_backend"]
                    if selected == "A"
                    else trial["b_backend"]
                )
                preference_votes.append(backend == right_backend)
            preferred_right = majority(preference_votes) if preference_votes else None
            if preferred_right is not None:
                majority_preference.append(preferred_right)

        correct_successes = sum(majority_correct)
        discrimination.append(
            {
                "comparison_id": (
                    f"{variant}:{left_backend}-vs-{right_backend}"
                ),
                "variant": variant,
                "backend_pair": [left_backend, right_backend],
                "trials_per_participant": len(ids),
                "nontied_participants": len(majority_correct),
                "participant_majority_correct": correct_successes,
                "participant_majority_fraction": (
                    correct_successes / len(majority_correct)
                    if majority_correct
                    else 0.0
                ),
                "p_value_raw": (
                    binomial_upper_tail(
                        correct_successes, len(majority_correct)
                    )
                    if majority_correct
                    else 1.0
                ),
            }
        )
        right_successes = sum(majority_preference)
        preferred_backend = None
        if majority_preference and right_successes * 2 != len(
            majority_preference
        ):
            preferred_backend = (
                right_backend
                if right_successes * 2 > len(majority_preference)
                else left_backend
            )
        preference.append(
            {
                "comparison_id": (
                    f"{variant}:{left_backend}-vs-{right_backend}"
                ),
                "variant": variant,
                "backend_pair": [left_backend, right_backend],
                "nontied_participants": len(majority_preference),
                "participants_preferring_right": right_successes,
                "right_preference_fraction": (
                    right_successes / len(majority_preference)
                    if majority_preference
                    else 0.5
                ),
                "preferred_backend_direction": preferred_backend,
                "p_value_raw": binomial_two_sided(
                    right_successes, len(majority_preference)
                ),
            }
        )
    holm_adjust(discrimination, alpha)
    holm_adjust(preference, alpha)
    for record in discrimination:
        record["effect_gate_passed"] = (
            record["nontied_participants"] >= minimum_nontied
            and record["participant_majority_fraction"] >= minimum_fraction
        )
        record["discriminability_detected"] = (
            record["statistically_significant"]
            and record["effect_gate_passed"]
        )
    for record in preference:
        directional_fraction = max(
            record["right_preference_fraction"],
            1.0 - record["right_preference_fraction"],
        )
        record["effect_gate_passed"] = (
            record["nontied_participants"] >= minimum_nontied
            and directional_fraction >= minimum_fraction
            and record["preferred_backend_direction"] is not None
        )
        record["preference_detected"] = (
            record["statistically_significant"]
            and record["effect_gate_passed"]
        )
    realism = [
        {
            "variant": variant,
            "backend": backend,
            "rating_count": len(values),
            "mean_realism_1_to_5": sum(values) / len(values),
            "minimum": min(values),
            "maximum": max(values),
            "inferential_claim": False,
        }
        for (variant, backend), values in sorted(realism_values.items())
    ]
    return {
        "schema_version": SCHEMA_VERSION,
        "status": "valid-audio-lab-listening-analysis",
        "protocol_sha256": sha256_file(protocol_path),
        "session_report_sha256": sha256_file(session_report_path),
        "public_manifest_sha256": sha256_file(public_path),
        "private_answer_key_sha256": sha256_file(private_path),
        "responses_csv_sha256": sha256_file(responses_path),
        "participant_count": len(participants),
        "participant_ids_disclosed": False,
        "complete_trials_per_participant": len(trial_ids),
        "total_abx_answers": total_answers,
        "overall_raw_accuracy": total_correct / total_answers,
        "total_extra_replays": total_extra_replays,
        "inference_unit": "participant-majority-per-comparison",
        "familywise_alpha": alpha,
        "discrimination": discrimination,
        "preference": preference,
        "realism_descriptive": realism,
        "listening_protocol_completed": True,
        "any_discriminability_detected": any(
            item["discriminability_detected"] for item in discrimination
        ),
        "any_preference_detected": any(
            item["preference_detected"] for item in preference
        ),
        "release_calibrated": False,
        "claim_boundary": (
            "Preregistered ABX discriminability and preference statistics. "
            "Realism ratings are descriptive ordinal summaries; this report "
            "does not acoustically calibrate a backend or prove matched-RIR truth."
        ),
    }


def write_fixture_responses(
    path: Path,
    public: dict[str, Any],
    private: dict[str, Any],
    presentation_order_seed: str,
) -> None:
    if path.exists():
        raise FileExistsError(f"refusing to overwrite existing output: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    private_by_id = {
        trial["trial_id"]: trial for trial in private["trials"]
    }
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=RESPONSE_FIELDS)
        writer.writeheader()
        for participant_index in range(1, 13):
            participant_id = f"fixture-p{participant_index:02d}"
            public_by_id = {
                trial["trial_id"]: trial for trial in public["trials"]
            }
            ordered_ids = presentation_order(
                presentation_order_seed,
                participant_id,
                set(public_by_id),
            )
            for trial_index, trial_id in enumerate(ordered_ids):
                public_trial = public_by_id[trial_id]
                trial = private_by_id[public_trial["trial_id"]]
                pair = {trial["a_backend"], trial["b_backend"]}
                dry_comparison = "dry" in pair
                should_be_correct = (
                    participant_index <= 11
                    if dry_comparison
                    else participant_index <= 6
                )
                x_response = (
                    trial["x_is"]
                    if should_be_correct
                    else ("B" if trial["x_is"] == "A" else "A")
                )
                if dry_comparison:
                    preferred_backend = next(
                        backend for backend in pair if backend != "dry"
                    )
                    preference = (
                        "A"
                        if trial["a_backend"] == preferred_backend
                        else "B"
                    )
                else:
                    preferred_backend = (
                        "java-fdn"
                        if participant_index <= 6
                        else "openal-efx"
                    )
                    preference = (
                        "A"
                        if trial["a_backend"] == preferred_backend
                        else "B"
                    )
                rating_a = 2 if trial["a_backend"] == "dry" else 4
                rating_b = 2 if trial["b_backend"] == "dry" else 4
                writer.writerow(
                    {
                        "participant_id": participant_id,
                        "trial_id": trial["trial_id"],
                        "presentation_index": trial_index + 1,
                        "x_response": x_response,
                        "preference": preference,
                        "realism_a": rating_a,
                        "realism_b": rating_b,
                        "extra_replays": (
                            participant_index + trial_index
                        )
                        % 4,
                    }
                )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    protocol = subparsers.add_parser("protocol")
    protocol.add_argument("--session-report", type=Path, required=True)
    protocol.add_argument("--public-manifest", type=Path, required=True)
    protocol.add_argument("--private-key", type=Path, required=True)
    protocol.add_argument("--minimum-participants", type=int, default=12)
    protocol.add_argument("--maximum-extra-replays", type=int, default=3)
    protocol.add_argument("--presentation-seed", required=True)
    protocol.add_argument("--alpha", type=float, default=0.05)
    protocol.add_argument(
        "--minimum-majority-fraction",
        type=float,
        default=0.75,
    )
    protocol.add_argument("--output-json", type=Path, required=True)
    score_parser = subparsers.add_parser("score")
    score_parser.add_argument("--protocol", type=Path, required=True)
    score_parser.add_argument("--session-report", type=Path, required=True)
    score_parser.add_argument("--public-manifest", type=Path, required=True)
    score_parser.add_argument("--private-key", type=Path, required=True)
    score_parser.add_argument("--responses-csv", type=Path, required=True)
    score_parser.add_argument("--output-json", type=Path, required=True)
    fixture = subparsers.add_parser("fixture")
    fixture.add_argument("--session-report", type=Path, required=True)
    fixture.add_argument("--public-manifest", type=Path, required=True)
    fixture.add_argument("--private-key", type=Path, required=True)
    fixture.add_argument("--output-protocol", type=Path, required=True)
    fixture.add_argument("--output-responses", type=Path, required=True)
    fixture.add_argument("--output-json", type=Path, required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        if args.command == "protocol":
            value = create_protocol(
                args.session_report,
                args.public_manifest,
                args.private_key,
                args.minimum_participants,
                args.maximum_extra_replays,
                args.presentation_seed,
                args.alpha,
                args.minimum_majority_fraction,
            )
            atomic_write_json(args.output_json, value)
        elif args.command == "score":
            value = score(
                args.protocol,
                args.session_report,
                args.public_manifest,
                args.private_key,
                args.responses_csv,
            )
            atomic_write_json(args.output_json, value)
        else:
            public, private = validate_manifests(
                args.public_manifest,
                args.private_key,
            )
            protocol_value = create_protocol(
                args.session_report,
                args.public_manifest,
                args.private_key,
                12,
                3,
                "d089-fixture-presentation-seed",
                0.05,
                0.75,
            )
            atomic_write_json(args.output_protocol, protocol_value)
            write_fixture_responses(
                args.output_responses,
                public,
                private,
                "d089-fixture-presentation-seed",
            )
            value = score(
                args.output_protocol,
                args.session_report,
                args.public_manifest,
                args.private_key,
                args.output_responses,
            )
            atomic_write_json(args.output_json, value)
        print(
            json.dumps(
                {
                    "status": value["status"],
                    "participants": value.get("participant_count"),
                    "trials_per_participant": value.get(
                        "complete_trials_per_participant",
                        value.get("trial_count_per_participant"),
                    ),
                    "listening_protocol_completed": value.get(
                        "listening_protocol_completed",
                        False,
                    ),
                    "any_discriminability_detected": value.get(
                        "any_discriminability_detected",
                        False,
                    ),
                    "any_preference_detected": value.get(
                        "any_preference_detected",
                        False,
                    ),
                    "release_calibrated": False,
                },
                sort_keys=True,
            )
        )
        return 0
    except (OSError, ValueError, KeyError) as error:
        parser.exit(2, f"error: {error}\n")


if __name__ == "__main__":
    sys.exit(main())
