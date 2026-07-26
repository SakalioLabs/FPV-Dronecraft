#!/usr/bin/env python3
"""Fit order-domain source parameters from analyzer per-order evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import tempfile
from pathlib import Path
from typing import Any, Iterable


RELEASE_GATE_DB = 3.0
MOTOR_KINDS = (
    "shaft",
    "electrical",
    "twice_electrical_candidate",
)


def strict_json_load(path: Path) -> dict[str, Any]:
    def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"{path}: duplicate JSON field {key!r}")
            result[key] = value
        return result

    with path.open("r", encoding="utf-8") as stream:
        value = json.load(
            stream,
            object_pairs_hook=unique_object,
            parse_constant=lambda token: (_ for _ in ()).throw(
                ValueError(f"{path}: non-finite JSON number {token}")
            ),
        )
    if not isinstance(value, dict):
        raise ValueError(f"{path}: root must be an object")
    return value


def exact_fields(value: dict[str, Any], expected: Iterable[str], path: str) -> None:
    expected_set = set(expected)
    actual_set = set(value)
    missing = sorted(expected_set - actual_set)
    unknown = sorted(actual_set - expected_set)
    if missing or unknown:
        raise ValueError(
            f"{path}: field mismatch; missing={missing}, unknown={unknown}"
        )


def finite(value: Any, path: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{path}: expected a number")
    result = float(value)
    if not math.isfinite(result):
        raise ValueError(f"{path}: expected a finite number")
    return result


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def rounded(value: float) -> float:
    result = round(float(value), 9)
    return 0.0 if result == -0.0 else result


def energy_sum_db(levels_db: Iterable[float]) -> float:
    energies = [10.0 ** (level / 10.0) for level in levels_db]
    if not energies:
        raise ValueError("cannot sum an empty level set")
    return 10.0 * math.log10(sum(energies))


def energy_mean_power(levels_db: Iterable[float]) -> float:
    levels = list(levels_db)
    if not levels:
        raise ValueError("cannot average an empty level set")
    return sum(10.0 ** (level / 10.0) for level in levels) / len(levels)


def tone_map(record: dict[str, Any], field: str) -> dict[Any, dict[str, Any]]:
    tones = record.get(field)
    if not isinstance(tones, list):
        raise ValueError(f"record {record.get('recording_id')}: {field} must be an array")
    key_name = "order" if field == "rotor_tones" else "kind"
    result = {}
    for tone in tones:
        if not isinstance(tone, dict) or key_name not in tone:
            raise ValueError(f"record {record.get('recording_id')}: malformed {field}")
        key = tone[key_name]
        if key in result:
            raise ValueError(f"record {record.get('recording_id')}: duplicate tone {key}")
        result[key] = tone
    return result


def detected_level(tone: dict[str, Any], path: str) -> float | None:
    detected = tone.get("detected")
    if not isinstance(detected, bool):
        raise ValueError(f"{path}.detected must be boolean")
    value = tone.get("isolated_level_db_1m")
    if detected:
        return finite(value, path + ".isolated_level_db_1m")
    if value is not None:
        raise ValueError(f"{path}: nondetected tone must have null isolated level")
    return None


def descriptor(record: dict[str, Any]) -> dict[str, Any]:
    value = record.get("descriptor")
    if not isinstance(value, dict):
        raise ValueError(
            f"record {record.get('recording_id')}: descriptor must be an object"
        )
    return value


def validate_splits(records: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    train = [record for record in records if descriptor(record).get("split") == "train"]
    validation = [
        record for record in records if descriptor(record).get("split") == "validation"
    ]
    if not train or not validation:
        raise ValueError("analysis report requires train and validation records")
    for name in ("recording_id", "maneuver_id"):
        train_ids = {str(record.get(name)) for record in train}
        validation_ids = {str(record.get(name)) for record in validation}
        overlap = sorted(train_ids & validation_ids)
        if overlap:
            raise ValueError(f"{name} occurs in train and validation: {overlap}")
    return train, validation


def plane_records(
    records: list[dict[str, Any]],
    maximum_elevation_degrees: float,
) -> list[dict[str, Any]]:
    return [
        record
        for record in records
        if abs(
            finite(
                descriptor(record).get("elevation_deg"),
                f"{record.get('recording_id')}.descriptor.elevation_deg",
            )
        )
        <= maximum_elevation_degrees
    ]


def highest_common_training_harmonic(records: list[dict[str, Any]]) -> int:
    common: set[int] | None = None
    for record in records:
        tones = tone_map(record, "rotor_tones")
        detected = {
            int(order)
            for order, tone in tones.items()
            if detected_level(
                tone,
                f"{record.get('recording_id')}.rotor_tones[{order}]",
            )
            is not None
        }
        common = detected if common is None else common & detected
    maximum = 0
    while common is not None and maximum + 1 in common:
        maximum += 1
    if maximum < 2:
        raise ValueError(
            "at least two consecutive BPF harmonics must be detected in every "
            "training plane record"
        )
    for record in records:
        tones = tone_map(record, "rotor_tones")
        for order, tone in tones.items():
            if order <= maximum:
                continue
            if detected_level(
                tone,
                f"{record.get('recording_id')}.rotor_tones[{order}]",
            ) is not None:
                raise ValueError(
                    "training BPF detection above the fitted consecutive "
                    "harmonic count must be consistently absent"
                )
    return maximum


def validate_rotor_candidate_orders(
    records: list[dict[str, Any]],
) -> int:
    expected: set[int] | None = None
    for record in records:
        orders = set(tone_map(record, "rotor_tones"))
        if not orders or orders != set(range(1, max(orders) + 1)):
            raise ValueError(
                "rotor_tones must contain consecutive candidate orders from 1"
            )
        if expected is None:
            expected = orders
        elif orders != expected:
            raise ValueError(
                "all train and validation plane records must analyze the same "
                "BPF candidate orders"
            )
    assert expected is not None
    return max(expected)


def fit_harmonic_rolloff(
    records: list[dict[str, Any]],
    harmonic_count: int,
) -> tuple[float, list[float]]:
    numerator = 0.0
    denominator = 0.0
    observations = []
    for record in records:
        tones = tone_map(record, "rotor_tones")
        fundamental = detected_level(
            tones[1],
            f"{record.get('recording_id')}.rotor_tones[1]",
        )
        assert fundamental is not None
        for harmonic in range(2, harmonic_count + 1):
            level = detected_level(
                tones[harmonic],
                f"{record.get('recording_id')}.rotor_tones[{harmonic}]",
            )
            assert level is not None
            x = 20.0 * math.log10(harmonic)
            relative = level - fundamental
            numerator += x * relative
            denominator += x * x
            observations.append((x, relative))
    rolloff = -numerator / denominator
    if not math.isfinite(rolloff) or rolloff <= 0.0:
        raise ValueError("fitted harmonic rolloff must be finite and positive")
    errors = [abs(relative + rolloff * x) for x, relative in observations]
    return rolloff, errors


def motor_detection_model(
    records: list[dict[str, Any]],
) -> tuple[list[str], str]:
    included = []
    for kind in MOTOR_KINDS:
        states = []
        for record in records:
            tones = tone_map(record, "motor_tones")
            if kind not in tones:
                raise ValueError(f"record {record.get('recording_id')}: missing {kind}")
            states.append(
                detected_level(
                    tones[kind],
                    f"{record.get('recording_id')}.motor_tones[{kind}]",
                )
                is not None
            )
        if all(states):
            included.append(kind)
        elif any(states):
            raise ValueError(
                f"training detection for {kind} is inconsistent; fixed amplitude "
                "model cannot represent the data"
            )
    if not included:
        raise ValueError("at least one motor order must be detected in training")
    reference = (
        "electrical"
        if "electrical" in included
        else included[0]
    )
    return included, reference


def fit_motor_ratios(
    records: list[dict[str, Any]],
    included: list[str],
    reference_kind: str,
) -> tuple[dict[str, float], list[float]]:
    relative_samples = {kind: [] for kind in included}
    for record in records:
        tones = tone_map(record, "motor_tones")
        reference = detected_level(
            tones[reference_kind],
            f"{record.get('recording_id')}.motor_tones[{reference_kind}]",
        )
        assert reference is not None
        for kind in included:
            level = detected_level(
                tones[kind],
                f"{record.get('recording_id')}.motor_tones[{kind}]",
            )
            assert level is not None
            relative_samples[kind].append(level - reference)
    fitted = {
        kind: sum(values) / len(values)
        for kind, values in relative_samples.items()
    }
    errors = [
        abs(value - fitted[kind])
        for kind, values in relative_samples.items()
        for value in values
    ]
    return fitted, errors


def validation_order_errors(
    records: list[dict[str, Any]],
    harmonic_count: int,
    rolloff: float,
    included_motor: list[str],
    reference_motor: str,
    motor_ratios_db: dict[str, float],
) -> list[float]:
    errors = []
    for record in records:
        rotor = tone_map(record, "rotor_tones")
        fundamental = detected_level(
            rotor[1],
            f"{record.get('recording_id')}.rotor_tones[1]",
        )
        if fundamental is None:
            errors.append(120.0)
        else:
            for harmonic in range(2, harmonic_count + 1):
                tone = rotor.get(harmonic)
                if tone is None:
                    errors.append(120.0)
                    continue
                level = detected_level(
                    tone,
                    f"{record.get('recording_id')}.rotor_tones[{harmonic}]",
                )
                predicted = fundamental - rolloff * 20.0 * math.log10(harmonic)
                if level is None:
                    floor = tone.get("local_floor_level_db_1m")
                    if floor is None:
                        errors.append(120.0)
                    else:
                        errors.append(max(0.0, predicted - finite(
                            floor,
                            f"{record.get('recording_id')}.rotor_tones[{harmonic}].local_floor",
                        )))
                else:
                    errors.append(abs(predicted - level))
            for harmonic, tone in rotor.items():
                if harmonic <= harmonic_count:
                    continue
                if detected_level(
                    tone,
                    f"{record.get('recording_id')}.rotor_tones[{harmonic}]",
                ) is not None:
                    errors.append(120.0)

        motor = tone_map(record, "motor_tones")
        reference_level = detected_level(
            motor[reference_motor],
            f"{record.get('recording_id')}.motor_tones[{reference_motor}]",
        )
        if reference_level is None:
            errors.append(120.0)
        else:
            for kind in included_motor:
                level = detected_level(
                    motor[kind],
                    f"{record.get('recording_id')}.motor_tones[{kind}]",
                )
                errors.append(
                    120.0
                    if level is None
                    else abs(
                        reference_level + motor_ratios_db[kind] - level
                    )
                )
        for kind in MOTOR_KINDS:
            if kind in included_motor:
                continue
            if detected_level(
                motor[kind],
                f"{record.get('recording_id')}.motor_tones[{kind}]",
            ) is not None:
                errors.append(120.0)
    return errors


def source_model_at_reference(
    records: list[dict[str, Any]],
    reference_rpm: float,
    harmonic_count: int,
    rolloff: float,
    included_motor: list[str],
) -> dict[str, Any]:
    reference_records = [
        record
        for record in records
        if finite(
            descriptor(record).get("rpm"),
            f"{record.get('recording_id')}.descriptor.rpm",
        )
        == reference_rpm
    ]
    if not reference_records:
        raise ValueError("reference RPM must exactly match a training plane record")
    fundamental_levels = []
    motor_levels = {kind: [] for kind in included_motor}
    broadband_levels = []
    for record in reference_records:
        rotor = tone_map(record, "rotor_tones")
        fundamental = detected_level(
            rotor[1],
            f"{record.get('recording_id')}.rotor_tones[1]",
        )
        assert fundamental is not None
        fundamental_levels.append(fundamental)
        motor = tone_map(record, "motor_tones")
        for kind in included_motor:
            level = detected_level(
                motor[kind],
                f"{record.get('recording_id')}.motor_tones[{kind}]",
            )
            assert level is not None
            motor_levels[kind].append(level)
        item = descriptor(record)
        broadband_levels.append(
            energy_sum_db(
                (
                    finite(item.get("broadband_low_db"), "broadband_low_db"),
                    finite(item.get("broadband_mid_db"), "broadband_mid_db"),
                    finite(item.get("broadband_high_db"), "broadband_high_db"),
                )
            )
        )
    fundamental_power = energy_mean_power(fundamental_levels)
    amplitudes = {
        kind: math.sqrt(
            energy_mean_power(levels) / fundamental_power
        )
        for kind, levels in motor_levels.items()
    }
    broadband_energy = energy_mean_power(broadband_levels) / fundamental_power
    return {
        "blade_pass_harmonics": harmonic_count,
        "harmonic_rolloff": rounded(rolloff),
        "shaft_amplitude": rounded(amplitudes.get("shaft", 0.0)),
        "blade_pass_amplitude": 1.0,
        "electrical_amplitude": rounded(amplitudes.get("electrical", 0.0)),
        "cogging_candidate_amplitude": rounded(
            amplitudes.get("twice_electrical_candidate", 0.0)
        ),
        "broadband_energy": rounded(broadband_energy),
    }


def fit_order_model(
    report: dict[str, Any],
    report_sha256: str,
    reference_rpm: float,
    plane_max_elevation_deg: float,
) -> dict[str, Any]:
    if report.get("schema_version") != 1:
        raise ValueError("analysis report schema_version must be 1")
    records = report.get("recordings")
    if not isinstance(records, list) or not records:
        raise ValueError("analysis report recordings must be non-empty")
    train, validation = validate_splits(records)
    train_plane = plane_records(train, plane_max_elevation_deg)
    validation_plane = plane_records(validation, plane_max_elevation_deg)
    if not train_plane or not validation_plane:
        raise ValueError("order fitting requires train and validation plane records")
    maximum_candidate_harmonic = validate_rotor_candidate_orders(
        train_plane + validation_plane
    )
    train_rpm = {
        finite(descriptor(record).get("rpm"), "train rpm")
        for record in train_plane
    }
    validation_rpm = {
        finite(descriptor(record).get("rpm"), "validation rpm")
        for record in validation_plane
    }
    if train_rpm & validation_rpm:
        raise ValueError("validation RPM values must be unseen in training")

    harmonic_count = highest_common_training_harmonic(train_plane)
    if maximum_candidate_harmonic <= harmonic_count:
        raise ValueError(
            "order fitting requires at least one analyzed BPF candidate above "
            "the fitted consecutive harmonic count"
        )
    rolloff, harmonic_training_errors = fit_harmonic_rolloff(
        train_plane,
        harmonic_count,
    )
    included_motor, reference_motor = motor_detection_model(train_plane)
    motor_ratios, motor_training_errors = fit_motor_ratios(
        train_plane,
        included_motor,
        reference_motor,
    )
    validation_errors = validation_order_errors(
        validation_plane,
        harmonic_count,
        rolloff,
        included_motor,
        reference_motor,
        motor_ratios,
    )
    if not validation_errors:
        raise ValueError("order-spectrum validation produced no samples")
    training_errors = harmonic_training_errors + motor_training_errors
    maximum_training_error = max(training_errors, default=0.0)
    maximum_validation_error = max(validation_errors)
    passes = (
        maximum_training_error <= RELEASE_GATE_DB
        and maximum_validation_error <= RELEASE_GATE_DB
    )
    return {
        "schema_version": 1,
        "analysis_report_sha256": report_sha256,
        "reference_rpm": rounded(reference_rpm),
        "plane_max_elevation_deg": rounded(plane_max_elevation_deg),
        "source_model": source_model_at_reference(
            train_plane,
            reference_rpm,
            harmonic_count,
            rolloff,
            included_motor,
        ),
        "maximum_analyzed_blade_pass_harmonic": maximum_candidate_harmonic,
        "motor_reference_kind": reference_motor,
        "included_motor_kinds": included_motor,
        "training_order_samples": len(training_errors),
        "maximum_training_order_error_db": rounded(maximum_training_error),
        "order_spectrum_samples": len(validation_errors),
        "maximum_order_spectrum_error_db": rounded(maximum_validation_error),
        "release_gate_db": RELEASE_GATE_DB,
        "passes_release_gate": passes,
    }


def json_bytes(value: Any) -> bytes:
    return (
        json.dumps(
            value,
            ensure_ascii=False,
            allow_nan=False,
            sort_keys=True,
            indent=2,
        )
        + "\n"
    ).encode("utf-8")


def write_atomic(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=path.name + ".",
        suffix=".tmp",
        dir=path.parent,
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


def run(
    analysis_report_json: Path,
    reference_rpm: float,
    plane_max_elevation_deg: float,
    output_json: Path,
) -> int:
    report = strict_json_load(analysis_report_json)
    fitted = fit_order_model(
        report,
        sha256_file(analysis_report_json),
        reference_rpm,
        plane_max_elevation_deg,
    )
    write_atomic(output_json, json_bytes(fitted))
    return 0 if fitted["passes_release_gate"] else 1


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Fit harmonic and motor-order source parameters."
    )
    parser.add_argument("--analysis-report-json", type=Path, required=True)
    parser.add_argument("--reference-rpm", type=float, required=True)
    parser.add_argument("--plane-max-elevation-deg", type=float, default=2.0)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    if not math.isfinite(args.reference_rpm) or args.reference_rpm <= 0.0:
        parser.error("--reference-rpm must be finite and positive")
    if (
        not math.isfinite(args.plane_max_elevation_deg)
        or not 0.0 <= args.plane_max_elevation_deg <= 10.0
    ):
        parser.error("--plane-max-elevation-deg must be in [0, 10]")
    try:
        return run(
            args.analysis_report_json,
            args.reference_rpm,
            args.plane_max_elevation_deg,
            args.output_json,
        )
    except (OSError, ValueError, json.JSONDecodeError) as error:
        parser.error(str(error))
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
