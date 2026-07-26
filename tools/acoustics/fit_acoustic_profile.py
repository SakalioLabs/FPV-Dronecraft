#!/usr/bin/env python3
"""Fit a release-gated acoustic source profile from calibrated descriptors.

The input is intentionally a descriptor CSV, not raw audio. Raw recordings
must first be calibrated to 1 m, decomposed into rotor-tonal, motor-tonal and
three broadband-band levels, and assigned independent train/validation groups.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import os
import re
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable
from urllib.parse import urlparse


CSV_FIELDS = (
    "recording_id",
    "maneuver_id",
    "split",
    "rpm",
    "elevation_deg",
    "rotor_tonal_db",
    "motor_tonal_db",
    "broadband_low_db",
    "broadband_mid_db",
    "broadband_high_db",
)
COMPONENT_FIELDS = (
    "rotor_tonal_db",
    "motor_tonal_db",
    "broadband_total_db",
)
BAND_FIELDS = (
    "broadband_low_db",
    "broadband_mid_db",
    "broadband_high_db",
)
PROFILE_GATE_DB = 3.0
PROFILE_ID = re.compile(
    r"[a-z0-9][a-z0-9_.-]{0,63}:[a-z0-9][a-z0-9_./-]{0,127}"
)
AIRFRAME_ID = re.compile(r"[a-z0-9][a-z0-9_.-]{0,63}")
MOTOR_KINDS = (
    "shaft",
    "electrical",
    "twice_electrical_candidate",
)


@dataclass(frozen=True)
class Measurement:
    recording_id: str
    maneuver_id: str
    split: str
    rpm: float
    elevation_deg: float
    rotor_tonal_db: float
    motor_tonal_db: float
    broadband_low_db: float
    broadband_mid_db: float
    broadband_high_db: float

    @property
    def broadband_total_db(self) -> float:
        return energy_sum_db(
            (
                self.broadband_low_db,
                self.broadband_mid_db,
                self.broadband_high_db,
            )
        )

    def value(self, field: str) -> float:
        if field == "broadband_total_db":
            return self.broadband_total_db
        return float(getattr(self, field))


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


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


def require_exact_fields(
    value: dict[str, Any],
    expected: Iterable[str],
    path: str,
) -> None:
    expected_set = set(expected)
    actual_set = set(value)
    missing = sorted(expected_set - actual_set)
    unknown = sorted(actual_set - expected_set)
    if missing or unknown:
        raise ValueError(
            f"{path}: field mismatch; missing={missing}, unknown={unknown}"
        )


def require_finite_number(value: Any, path: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{path}: expected a number")
    number = float(value)
    if not math.isfinite(number):
        raise ValueError(f"{path}: expected a finite number")
    return number


def require_integer(value: Any, minimum: int, maximum: int, path: str) -> int:
    number = require_finite_number(value, path)
    if number != round(number) or not minimum <= number <= maximum:
        raise ValueError(f"{path}: expected an integer in [{minimum}, {maximum}]")
    return int(number)


def require_text(value: Any, path: str) -> str:
    if not isinstance(value, str) or not value.strip() or len(value.strip()) > 1024:
        raise ValueError(f"{path}: expected 1 to 1024 non-blank characters")
    return value.strip()


def load_measurements(path: Path) -> list[Measurement]:
    rows: list[Measurement] = []
    with path.open("r", encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != list(CSV_FIELDS):
            raise ValueError(
                f"{path}: CSV header must exactly equal {list(CSV_FIELDS)}"
            )
        for line_number, raw in enumerate(reader, start=2):
            text_values: dict[str, str] = {}
            for field in CSV_FIELDS:
                value = raw.get(field)
                if value is None or not value.strip():
                    raise ValueError(
                        f"{path}:{line_number}: {field} must be non-empty"
                    )
                text_values[field] = value.strip()
            split = text_values["split"].lower()
            if split not in {"train", "validation"}:
                raise ValueError(
                    f"{path}:{line_number}: split must be train or validation"
                )
            numeric: dict[str, float] = {}
            for field in CSV_FIELDS[3:]:
                try:
                    numeric[field] = float(text_values[field])
                except ValueError as error:
                    raise ValueError(
                        f"{path}:{line_number}: {field} must be numeric"
                    ) from error
                if not math.isfinite(numeric[field]):
                    raise ValueError(
                        f"{path}:{line_number}: {field} must be finite"
                    )
                if field.endswith("_db") and not -300.0 <= numeric[field] <= 300.0:
                    raise ValueError(
                        f"{path}:{line_number}: {field} must be in [-300, 300] dB"
                    )
            if numeric["rpm"] <= 0.0:
                raise ValueError(f"{path}:{line_number}: rpm must be positive")
            if not -90.0 <= numeric["elevation_deg"] <= 90.0:
                raise ValueError(
                    f"{path}:{line_number}: elevation_deg must be in [-90, 90]"
                )
            rows.append(
                Measurement(
                    recording_id=text_values["recording_id"],
                    maneuver_id=text_values["maneuver_id"],
                    split=split,
                    **numeric,
                )
            )
    if not rows:
        raise ValueError(f"{path}: measurement CSV is empty")
    validate_independent_splits(rows)
    return rows


def validate_independent_splits(rows: list[Measurement]) -> None:
    train = [row for row in rows if row.split == "train"]
    validation = [row for row in rows if row.split == "validation"]
    if not train or not validation:
        raise ValueError("both train and validation rows are required")
    for attribute in ("recording_id", "maneuver_id"):
        train_ids = {getattr(row, attribute) for row in train}
        validation_ids = {getattr(row, attribute) for row in validation}
        overlap = sorted(train_ids & validation_ids)
        if overlap:
            raise ValueError(
                f"{attribute} values occur in both train and validation: {overlap}"
            )


def energy_sum_db(levels_db: Iterable[float]) -> float:
    energies = [10.0 ** (level / 10.0) for level in levels_db]
    if not energies or not all(math.isfinite(value) and value > 0.0 for value in energies):
        raise ValueError("levels must produce finite positive energy")
    return 10.0 * math.log10(sum(energies))


def energy_mean_db(levels_db: Iterable[float]) -> float:
    levels = list(levels_db)
    if not levels:
        raise ValueError("cannot average an empty level set")
    return energy_sum_db(levels) - 10.0 * math.log10(len(levels))


def axis_cosine(elevation_deg: float) -> float:
    return abs(math.sin(math.radians(elevation_deg)))


def aggregate_plane_anchors(
    rows: list[Measurement],
    plane_max_elevation_deg: float,
) -> dict[float, dict[str, float]]:
    grouped: dict[float, list[Measurement]] = {}
    for row in rows:
        if row.split == "train" and abs(row.elevation_deg) <= plane_max_elevation_deg:
            grouped.setdefault(row.rpm, []).append(row)
    if len(grouped) < 2:
        raise ValueError("at least two distinct training rotor-plane RPM anchors are required")
    return {
        rpm: {
            field: energy_mean_db(item.value(field) for item in samples)
            for field in (*COMPONENT_FIELDS, *BAND_FIELDS)
        }
        for rpm, samples in sorted(grouped.items())
    }


def interpolate_log_rpm(
    anchors: dict[float, dict[str, float]],
    rpm: float,
    field: str,
) -> float:
    ordered = sorted(anchors)
    if rpm < ordered[0] or rpm > ordered[-1]:
        raise ValueError(
            f"RPM {rpm:g} is outside training anchor range "
            f"[{ordered[0]:g}, {ordered[-1]:g}]"
        )
    if rpm in anchors:
        return anchors[rpm][field]
    for lower, upper in zip(ordered, ordered[1:]):
        if lower < rpm < upper:
            fraction = math.log(rpm / lower) / math.log(upper / lower)
            return anchors[lower][field] + fraction * (
                anchors[upper][field] - anchors[lower][field]
            )
    raise AssertionError("log-RPM interpolation failed")


def fit_even_polynomial(samples: list[tuple[float, float]]) -> tuple[float, float]:
    if len(samples) < 3 or len({round(mu, 12) for mu, _ in samples}) < 3:
        raise ValueError("directivity fit requires at least three distinct angles")
    a = b = c = d = e = 0.0
    for mu, level in samples:
        x = mu * mu
        y = x * x
        a += x * x
        b += x * y
        c += y * y
        d += x * level
        e += y * level
    determinant = a * c - b * b
    if abs(determinant) <= 1.0e-12:
        raise ValueError("directivity fit is singular")
    return ((d * c - b * e) / determinant, (a * e - b * d) / determinant)


def polynomial_bounds(c2_db: float, c4_db: float) -> tuple[float, float]:
    candidates = [0.0, c2_db + c4_db]
    if c4_db != 0.0:
        stationary = -c2_db / (2.0 * c4_db)
        if 0.0 < stationary < 1.0:
            candidates.append(c2_db * stationary + c4_db * stationary**2)
    minimum = max(-120.0, min(candidates))
    maximum = min(60.0, max(candidates))
    return (minimum, maximum)


def rounded(value: float) -> float:
    result = round(float(value), 9)
    return 0.0 if result == -0.0 else result


def validate_metadata_contract(
    metadata: dict[str, Any],
    input_csv_sha256: str,
    analysis_report_sha256: str,
) -> tuple[dict[str, Any], list[dict[str, Any]], dict[str, Any]]:
    profile_id = metadata["id"]
    if not isinstance(profile_id, str) or PROFILE_ID.fullmatch(profile_id) is None:
        raise ValueError("$.id: expected a lowercase namespaced identifier")

    key = metadata["key"]
    if not isinstance(key, dict):
        raise ValueError("$.key: expected an object")
    require_exact_fields(
        key,
        (
            "airframe_preset",
            "rotor_count",
            "blade_count",
            "rotor_radius_mm",
            "motor_pole_pairs",
        ),
        "$.key",
    )
    airframe = key["airframe_preset"]
    if not isinstance(airframe, str) or AIRFRAME_ID.fullmatch(airframe) is None:
        raise ValueError("$.key.airframe_preset: invalid lowercase identifier")
    require_integer(key["rotor_count"], 1, 32, "$.key.rotor_count")
    require_integer(key["blade_count"], 1, 16, "$.key.blade_count")
    require_integer(key["rotor_radius_mm"], 5, 2000, "$.key.rotor_radius_mm")
    require_integer(key["motor_pole_pairs"], 1, 64, "$.key.motor_pole_pairs")

    evidence = metadata["evidence"]
    if not isinstance(evidence, list) or not evidence:
        raise ValueError("$.evidence: expected a non-empty array")
    normalized_evidence: list[dict[str, Any]] = []
    csv_hash_referenced = False
    analysis_hash_referenced = False
    for index, item in enumerate(evidence):
        path = f"$.evidence[{index}]"
        if not isinstance(item, dict):
            raise ValueError(f"{path}: expected an object")
        require_exact_fields(
            item,
            ("citation", "source", "license", "measurement_conditions", "sha256"),
            path,
        )
        normalized = {
            name: require_text(item[name], f"{path}.{name}")
            for name in ("citation", "source", "license", "measurement_conditions")
        }
        source = urlparse(normalized["source"])
        if not source.scheme:
            raise ValueError(f"{path}.source: expected an absolute URI")
        sha256 = str(item["sha256"]).lower()
        if len(sha256) != 64 or any(
            character not in "0123456789abcdef" for character in sha256
        ):
            raise ValueError(
                f"{path}.sha256: expected 64 lowercase hexadecimal characters"
            )
        csv_hash_referenced |= sha256 == input_csv_sha256
        analysis_hash_referenced |= sha256 == analysis_report_sha256
        normalized_evidence.append(dict(normalized, sha256=sha256))
    if not csv_hash_referenced:
        raise ValueError(
            "$.evidence: at least one SHA-256 must match the measurement CSV"
        )
    if not analysis_hash_referenced:
        raise ValueError(
            "$.evidence: at least one SHA-256 must match the analysis report"
        )

    calibration = metadata["calibration"]
    if not isinstance(calibration, dict):
        raise ValueError("$.calibration: expected an object")
    require_exact_fields(
        calibration,
        (
            "measured",
            "replaces_legacy_rpm_volume",
            "motor_playback_gain_db",
            "propeller_playback_gain_db",
        ),
        "$.calibration",
    )
    if (
        calibration["measured"] is not True
        or calibration["replaces_legacy_rpm_volume"] is not True
    ):
        raise ValueError(
            "$.calibration: fitted profiles must be measured and replace legacy RPM volume"
        )
    normalized_calibration = dict(calibration)
    for name in ("motor_playback_gain_db", "propeller_playback_gain_db"):
        value = require_finite_number(calibration[name], f"$.calibration.{name}")
        if not -120.0 <= value <= 24.0:
            raise ValueError(f"$.calibration.{name}: expected [-120, 24] dB")
        normalized_calibration[name] = value

    return (
        normalized_calibration,
        normalized_evidence,
        key,
    )


def validate_order_model(
    order_model: dict[str, Any],
) -> tuple[dict[str, Any], str, float, float, int, float]:
    require_exact_fields(
        order_model,
        (
            "schema_version",
            "analysis_report_sha256",
            "reference_rpm",
            "plane_max_elevation_deg",
            "source_model",
            "maximum_analyzed_blade_pass_harmonic",
            "motor_reference_kind",
            "included_motor_kinds",
            "training_order_samples",
            "maximum_training_order_error_db",
            "order_spectrum_samples",
            "maximum_order_spectrum_error_db",
            "release_gate_db",
            "passes_release_gate",
        ),
        "$order_model",
    )
    if order_model["schema_version"] != 1 or isinstance(
        order_model["schema_version"], bool
    ):
        raise ValueError("$order_model.schema_version: only schema 1 is supported")
    analysis_hash = str(order_model["analysis_report_sha256"]).lower()
    if len(analysis_hash) != 64 or any(
        character not in "0123456789abcdef" for character in analysis_hash
    ):
        raise ValueError(
            "$order_model.analysis_report_sha256: expected 64 lowercase hexadecimal characters"
        )
    reference_rpm = require_finite_number(
        order_model["reference_rpm"], "$order_model.reference_rpm"
    )
    if reference_rpm <= 0.0:
        raise ValueError("$order_model.reference_rpm must be positive")
    plane_limit = require_finite_number(
        order_model["plane_max_elevation_deg"],
        "$order_model.plane_max_elevation_deg",
    )
    if not 0.0 <= plane_limit <= 10.0:
        raise ValueError(
            "$order_model.plane_max_elevation_deg must be in [0, 10]"
        )
    training_samples = require_integer(
        order_model["training_order_samples"],
        1,
        1_000_000,
        "$order_model.training_order_samples",
    )
    validation_samples = require_integer(
        order_model["order_spectrum_samples"],
        1,
        1_000_000,
        "$order_model.order_spectrum_samples",
    )
    maximum_candidate_harmonic = require_integer(
        order_model["maximum_analyzed_blade_pass_harmonic"],
        2,
        64,
        "$order_model.maximum_analyzed_blade_pass_harmonic",
    )
    training_error = require_finite_number(
        order_model["maximum_training_order_error_db"],
        "$order_model.maximum_training_order_error_db",
    )
    validation_error = require_finite_number(
        order_model["maximum_order_spectrum_error_db"],
        "$order_model.maximum_order_spectrum_error_db",
    )
    gate = require_finite_number(
        order_model["release_gate_db"], "$order_model.release_gate_db"
    )
    if gate != PROFILE_GATE_DB:
        raise ValueError(
            f"$order_model.release_gate_db must exactly equal {PROFILE_GATE_DB}"
        )
    if (
        order_model["passes_release_gate"] is not True
        or not 0.0 <= training_error <= gate
        or not 0.0 <= validation_error <= gate
    ):
        raise ValueError("$order_model: order-spectrum release gate did not pass")

    included = order_model["included_motor_kinds"]
    if (
        not isinstance(included, list)
        or not included
        or len(set(included)) != len(included)
        or any(kind not in MOTOR_KINDS for kind in included)
    ):
        raise ValueError(
            "$order_model.included_motor_kinds: expected unique supported motor kinds"
        )
    reference_kind = order_model["motor_reference_kind"]
    if reference_kind not in included:
        raise ValueError(
            "$order_model.motor_reference_kind must occur in included_motor_kinds"
        )

    source_model = order_model["source_model"]
    if not isinstance(source_model, dict):
        raise ValueError("$order_model.source_model: expected an object")
    require_exact_fields(
        source_model,
        (
            "blade_pass_harmonics",
            "harmonic_rolloff",
            "shaft_amplitude",
            "blade_pass_amplitude",
            "electrical_amplitude",
            "cogging_candidate_amplitude",
            "broadband_energy",
        ),
        "$order_model.source_model",
    )
    normalized_source = dict(source_model)
    normalized_source["blade_pass_harmonics"] = require_integer(
        source_model["blade_pass_harmonics"],
        1,
        64,
        "$order_model.source_model.blade_pass_harmonics",
    )
    if normalized_source["blade_pass_harmonics"] >= maximum_candidate_harmonic:
        raise ValueError(
            "$order_model must analyze at least one BPF candidate above the "
            "fitted blade_pass_harmonics"
        )
    rolloff = require_finite_number(
        source_model["harmonic_rolloff"],
        "$order_model.source_model.harmonic_rolloff",
    )
    if rolloff <= 0.0:
        raise ValueError("$order_model.source_model.harmonic_rolloff must be positive")
    normalized_source["harmonic_rolloff"] = rolloff
    for name in (
        "shaft_amplitude",
        "blade_pass_amplitude",
        "electrical_amplitude",
        "cogging_candidate_amplitude",
        "broadband_energy",
    ):
        value = require_finite_number(
            source_model[name], f"$order_model.source_model.{name}"
        )
        if value < 0.0:
            raise ValueError(
                f"$order_model.source_model.{name} must be non-negative"
            )
        normalized_source[name] = value
    return (
        normalized_source,
        analysis_hash,
        reference_rpm,
        plane_limit,
        validation_samples,
        validation_error,
    )


def fit_profile(
    rows: list[Measurement],
    metadata: dict[str, Any],
    input_csv_sha256: str,
    order_model: dict[str, Any],
) -> tuple[dict[str, Any], dict[str, Any]]:
    require_exact_fields(
        metadata,
        (
            "schema_version",
            "id",
            "key",
            "calibration",
            "evidence",
            "fit_settings",
        ),
        "$",
    )
    if (
        isinstance(metadata["schema_version"], bool)
        or metadata["schema_version"] != 1
    ):
        raise ValueError("$.schema_version: only schema 1 is supported")
    (
        source_model,
        analysis_report_sha256,
        order_reference_rpm,
        order_plane_limit,
        order_spectrum_samples,
        maximum_order_spectrum_error,
    ) = validate_order_model(order_model)
    calibration, normalized_evidence, key = validate_metadata_contract(
        metadata,
        input_csv_sha256,
        analysis_report_sha256,
    )
    settings = metadata["fit_settings"]
    if not isinstance(settings, dict):
        raise ValueError("$.fit_settings: expected an object")
    require_exact_fields(
        settings,
        (
            "reference_rpm",
            "plane_max_elevation_deg",
            "low_anchor_hz",
            "mid_anchor_hz",
            "high_anchor_hz",
        ),
        "$.fit_settings",
    )
    reference_rpm = require_finite_number(
        settings["reference_rpm"], "$.fit_settings.reference_rpm"
    )
    if reference_rpm <= 0.0:
        raise ValueError("$.fit_settings.reference_rpm must be positive")
    plane_limit = require_finite_number(
        settings["plane_max_elevation_deg"],
        "$.fit_settings.plane_max_elevation_deg",
    )
    if not 0.0 <= plane_limit <= 10.0:
        raise ValueError("$.fit_settings.plane_max_elevation_deg must be in [0, 10]")
    if reference_rpm != order_reference_rpm:
        raise ValueError(
            "order-model reference_rpm must exactly match fit_settings reference_rpm"
        )
    if plane_limit != order_plane_limit:
        raise ValueError(
            "order-model plane_max_elevation_deg must exactly match fit_settings"
        )
    anchor_frequencies = [
        require_finite_number(settings[name], f"$.fit_settings.{name}")
        for name in ("low_anchor_hz", "mid_anchor_hz", "high_anchor_hz")
    ]
    if not (
        0.0 < anchor_frequencies[0]
        < anchor_frequencies[1]
        < anchor_frequencies[2]
    ):
        raise ValueError("$.fit_settings directivity anchors must be positive and increasing")

    anchors = aggregate_plane_anchors(rows, plane_limit)
    if reference_rpm not in anchors:
        raise ValueError("reference_rpm must exactly match a training plane anchor")
    reference = anchors[reference_rpm]

    operating_points = []
    operating_gain_anchors: dict[float, dict[str, float]] = {}
    for rpm, values in anchors.items():
        point = {
            "rpm": rounded(rpm),
            "rotor_tonal_gain_db": rounded(
                values["rotor_tonal_db"] - reference["rotor_tonal_db"]
            ),
            "motor_tonal_gain_db": rounded(
                values["motor_tonal_db"] - reference["motor_tonal_db"]
            ),
            "broadband_gain_db": rounded(
                values["broadband_total_db"] - reference["broadband_total_db"]
            ),
        }
        for name in (
            "rotor_tonal_gain_db",
            "motor_tonal_gain_db",
            "broadband_gain_db",
        ):
            if not -120.0 <= point[name] <= 60.0:
                raise ValueError(f"fitted {name} falls outside [-120, 60] dB")
        operating_points.append(point)
        operating_gain_anchors[rpm] = point

    reference_band_energies = {
        field: 10.0 ** (reference[field] / 10.0)
        for field in BAND_FIELDS
    }
    reference_broadband_energy = sum(reference_band_energies.values())
    low_fraction = rounded(
        reference_band_energies["broadband_low_db"]
        / reference_broadband_energy
    )
    mid_fraction = rounded(
        reference_band_energies["broadband_mid_db"]
        / reference_broadband_energy
    )
    high_fraction = rounded(1.0 - low_fraction - mid_fraction)
    broadband_distribution = {
        "low": low_fraction,
        "mid": mid_fraction,
        "high": high_fraction,
    }

    train_nonplane_angles = {
        round(axis_cosine(row.elevation_deg), 12)
        for row in rows
        if row.split == "train" and abs(row.elevation_deg) > plane_limit
    }
    validation_plane = [
        row
        for row in rows
        if row.split == "validation" and abs(row.elevation_deg) <= plane_limit
    ]
    validation_angle = [
        row
        for row in rows
        if row.split == "validation" and abs(row.elevation_deg) > plane_limit
    ]
    if not validation_plane or not validation_angle:
        raise ValueError("validation requires both rotor-plane RPM and off-plane angle rows")
    if any(row.rpm in anchors for row in validation_plane):
        raise ValueError("validation plane RPM values must be unseen in training")
    repeated_angles = sorted(
        {
            round(axis_cosine(row.elevation_deg), 12)
            for row in validation_angle
        }
        & train_nonplane_angles
    )
    if repeated_angles:
        raise ValueError(
            f"validation off-plane angles must be unseen in training: {repeated_angles}"
        )

    polynomials: dict[str, tuple[float, float]] = {}
    for field in BAND_FIELDS:
        samples: list[tuple[float, float]] = []
        for row in rows:
            if row.split != "train":
                continue
            broadband_gain = interpolate_log_rpm(
                operating_gain_anchors,
                row.rpm,
                "broadband_gain_db",
            )
            baseline = reference[field] + broadband_gain
            samples.append((axis_cosine(row.elevation_deg), row.value(field) - baseline))
        polynomials[field] = fit_even_polynomial(samples)

    directivity_models: dict[str, dict[str, float]] = {}
    for field, (c2_db, c4_db) in polynomials.items():
        c2_db = rounded(c2_db)
        c4_db = rounded(c4_db)
        minimum_db, maximum_db = polynomial_bounds(c2_db, c4_db)
        directivity_models[field] = {
            "c2_db": c2_db,
            "c4_db": c4_db,
            "minimum_db": rounded(minimum_db),
            "maximum_db": rounded(maximum_db),
        }

    rpm_errors: list[float] = []
    for row in validation_plane:
        rotor_gain = interpolate_log_rpm(
            operating_gain_anchors,
            row.rpm,
            "rotor_tonal_gain_db",
        )
        motor_gain = interpolate_log_rpm(
            operating_gain_anchors,
            row.rpm,
            "motor_tonal_gain_db",
        )
        broadband_gain = interpolate_log_rpm(
            operating_gain_anchors,
            row.rpm,
            "broadband_gain_db",
        )
        rpm_errors.extend(
            (
                abs(reference["rotor_tonal_db"] + rotor_gain - row.rotor_tonal_db),
                abs(reference["motor_tonal_db"] + motor_gain - row.motor_tonal_db),
            )
        )
        for field in BAND_FIELDS:
            prediction = reference[field] + broadband_gain
            rpm_errors.append(abs(prediction - row.value(field)))

    angle_errors: list[float] = []
    for row in validation_angle:
        mu = axis_cosine(row.elevation_deg)
        squared = mu * mu
        broadband_gain = interpolate_log_rpm(
            operating_gain_anchors,
            row.rpm,
            "broadband_gain_db",
        )
        for field in BAND_FIELDS:
            model = directivity_models[field]
            directivity_db = (
                model["c2_db"] * squared
                + model["c4_db"] * squared * squared
            )
            directivity_db = max(
                model["minimum_db"],
                min(model["maximum_db"], directivity_db),
            )
            prediction = reference[field] + broadband_gain + directivity_db
            angle_errors.append(abs(prediction - row.value(field)))

    maximum_rpm_error = max(rpm_errors)
    maximum_angle_error = max(angle_errors)
    passes = (
        maximum_rpm_error <= PROFILE_GATE_DB
        and maximum_angle_error <= PROFILE_GATE_DB
        and maximum_order_spectrum_error <= PROFILE_GATE_DB
    )
    band_names = {
        "low": "broadband_low_db",
        "mid": "broadband_mid_db",
        "high": "broadband_high_db",
    }
    directivity: dict[str, Any] = {
        "low_anchor_hz": settings["low_anchor_hz"],
        "mid_anchor_hz": settings["mid_anchor_hz"],
        "high_anchor_hz": settings["high_anchor_hz"],
    }
    for name, field in band_names.items():
        directivity[name] = directivity_models[field]

    validation = {
        "evaluated": True,
        "unseen_rpm_samples": len(validation_plane),
        "unseen_angle_samples": len(validation_angle),
        "order_spectrum_samples": order_spectrum_samples,
        "maximum_unseen_rpm_error_db": rounded(maximum_rpm_error),
        "maximum_unseen_angle_error_db": rounded(maximum_angle_error),
        "maximum_order_spectrum_error_db": rounded(
            maximum_order_spectrum_error
        ),
    }
    profile = {
        "schema_version": 1,
        "id": metadata["id"],
        "key": key,
        "calibration": calibration,
        "validation": validation,
        "evidence": normalized_evidence,
        "source_model": dict(
            source_model,
            broadband_distribution=broadband_distribution,
            operating_points=operating_points,
        ),
        "directivity": directivity,
    }
    report = {
        "schema_version": 1,
        "profile_id": metadata["id"],
        "measurement_csv_sha256": input_csv_sha256,
        "analysis_report_sha256": analysis_report_sha256,
        "training_rows": sum(row.split == "train" for row in rows),
        "validation_rows": sum(row.split == "validation" for row in rows),
        "training_rpm_anchors": [rounded(rpm) for rpm in anchors],
        "reference_rpm": rounded(reference_rpm),
        "validation": validation,
        "release_gate_db": PROFILE_GATE_DB,
        "passes_release_gate": passes,
    }
    return profile, report


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
    measurements_csv: Path,
    metadata_json: Path,
    order_model_json: Path,
    output_profile_json: Path,
    output_report_json: Path,
) -> int:
    csv_hash = sha256_file(measurements_csv)
    rows = load_measurements(measurements_csv)
    metadata = strict_json_load(metadata_json)
    order_model = strict_json_load(order_model_json)
    order_model_hash = sha256_file(order_model_json)
    profile, report = fit_profile(rows, metadata, csv_hash, order_model)
    report["order_model_sha256"] = order_model_hash
    profile_data = json_bytes(profile)
    report["profile_sha256"] = sha256_bytes(profile_data)
    report["profile_written"] = bool(report["passes_release_gate"])
    if report["passes_release_gate"]:
        write_atomic(output_profile_json, profile_data)
    write_atomic(output_report_json, json_bytes(report))
    return 0 if report["passes_release_gate"] else 1


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Fit and release-gate a v1 acoustic source profile."
    )
    parser.add_argument("--measurements-csv", type=Path, required=True)
    parser.add_argument("--metadata-json", type=Path, required=True)
    parser.add_argument("--order-model-json", type=Path, required=True)
    parser.add_argument("--output-profile-json", type=Path, required=True)
    parser.add_argument("--output-report-json", type=Path, required=True)
    args = parser.parse_args()
    try:
        return run(
            args.measurements_csv,
            args.metadata_json,
            args.order_model_json,
            args.output_profile_json,
            args.output_report_json,
        )
    except (OSError, ValueError, json.JSONDecodeError) as error:
        parser.error(str(error))
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
