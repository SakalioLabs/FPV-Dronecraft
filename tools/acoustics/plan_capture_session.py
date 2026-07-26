#!/usr/bin/env python3
"""Plan and materialize an independent-holdout FPV acoustic capture session."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import os
import re
import tempfile
from pathlib import Path
from typing import Any, Iterable


PLAN_SCHEMA_VERSION = 1
RESULT_FIELDS = (
    "capture_id",
    "status",
    "wav",
    "rpm_csv",
    "start_s",
    "duration_s",
    "background_start_s",
    "background_duration_s",
    "thrust_n",
    "voltage_v",
    "current_a",
    "ambient_temperature_c",
    "relative_humidity_percent",
    "ambient_pressure_kpa",
    "notes",
)
SOURCE_CONFIGURATIONS = {
    "single_rotor_bench",
    "full_airframe_bench",
    "free_flight",
}


def exact_fields(value: dict[str, Any], expected: Iterable[str], path: str) -> None:
    expected_set = set(expected)
    actual_set = set(value)
    missing = sorted(expected_set - actual_set)
    unknown = sorted(actual_set - expected_set)
    if missing or unknown:
        raise ValueError(
            f"{path}: field mismatch; missing={missing}, unknown={unknown}"
        )


def text(value: Any, path: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{path}: expected non-blank text")
    return value.strip()


def number(value: Any, path: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{path}: expected a number")
    result = float(value)
    if not math.isfinite(result):
        raise ValueError(f"{path}: expected a finite number")
    return result


def integer(
    value: Any,
    path: str,
    minimum: int = 0,
    maximum: int = 1_000_000,
) -> int:
    result = number(value, path)
    if result != round(result) or not minimum <= result <= maximum:
        raise ValueError(
            f"{path}: expected integer in [{minimum}, {maximum}]"
        )
    return int(result)


def numeric_list(
    value: Any,
    path: str,
    minimum_count: int,
) -> list[float]:
    if not isinstance(value, list) or len(value) < minimum_count:
        raise ValueError(
            f"{path}: expected at least {minimum_count} numeric values"
        )
    result = [number(item, f"{path}[{index}]") for index, item in enumerate(value)]
    if len(set(result)) != len(result):
        raise ValueError(f"{path}: duplicate values are not allowed")
    return result


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


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def angle_token(angle: float) -> str:
    sign = "p" if angle >= 0.0 else "m"
    magnitude = abs(angle)
    if magnitude == round(magnitude):
        value = f"{int(round(magnitude)):03d}"
    else:
        value = f"{magnitude:06.2f}".replace(".", "p")
    return sign + value


def axis_cosine(angle: float) -> float:
    return round(abs(math.sin(math.radians(angle))), 12)


def require_symmetric_angles(angles: list[float], path: str) -> None:
    values = set(angles)
    for angle in angles:
        if angle == 0.0:
            raise ValueError(f"{path}: zero belongs to the plane RPM matrix")
        if -angle not in values:
            raise ValueError(
                f"{path}: {angle} degrees is missing its {-angle} counterpart"
            )


def validate_config(config: dict[str, Any]) -> dict[str, Any]:
    exact_fields(
        config,
        (
            "schema_version",
            "session_id",
            "hardware",
            "acquisition",
            "calibration",
            "analysis",
            "environment_defaults",
            "matrix",
        ),
        "$",
    )
    if config["schema_version"] != 1 or isinstance(
        config["schema_version"],
        bool,
    ):
        raise ValueError("$.schema_version must be 1")
    session_id = text(config["session_id"], "$.session_id")
    if not re.fullmatch(r"[a-z0-9][a-z0-9._-]{2,79}", session_id):
        raise ValueError(
            "$.session_id must be a 3-80 character lowercase file-safe id"
        )

    hardware = config["hardware"]
    if not isinstance(hardware, dict):
        raise ValueError("$.hardware must be an object")
    exact_fields(
        hardware,
        (
            "airframe_id",
            "source_configuration",
            "motor_id",
            "propeller_id",
            "blade_count",
            "motor_pole_pairs",
            "blade_pass_harmonics",
            "microphone_id",
            "signal_chain_id",
        ),
        "$.hardware",
    )
    for field in (
        "airframe_id",
        "motor_id",
        "propeller_id",
        "microphone_id",
        "signal_chain_id",
    ):
        text(hardware[field], f"$.hardware.{field}")
    source_configuration = text(
        hardware["source_configuration"],
        "$.hardware.source_configuration",
    ).lower()
    if source_configuration not in SOURCE_CONFIGURATIONS:
        raise ValueError(
            "$.hardware.source_configuration is not a supported value"
        )
    integer(hardware["blade_count"], "$.hardware.blade_count", 1, 16)
    integer(
        hardware["motor_pole_pairs"],
        "$.hardware.motor_pole_pairs",
        1,
        64,
    )
    integer(
        hardware["blade_pass_harmonics"],
        "$.hardware.blade_pass_harmonics",
        1,
        64,
    )

    acquisition = config["acquisition"]
    if not isinstance(acquisition, dict):
        raise ValueError("$.acquisition must be an object")
    exact_fields(
        acquisition,
        (
            "sample_rate_hz",
            "bit_depth",
            "channel",
            "microphone_distance_m",
            "reference_level_correction_db",
            "signal_start_s",
            "signal_duration_s",
            "background_start_s",
            "background_duration_s",
        ),
        "$.acquisition",
    )
    sample_rate = integer(
        acquisition["sample_rate_hz"],
        "$.acquisition.sample_rate_hz",
        8_000,
        384_000,
    )
    bit_depth = integer(
        acquisition["bit_depth"],
        "$.acquisition.bit_depth",
        8,
        32,
    )
    if bit_depth not in {8, 16, 24, 32}:
        raise ValueError("$.acquisition.bit_depth must be 8, 16, 24, or 32")
    integer(acquisition["channel"], "$.acquisition.channel", 0, 63)
    distance = number(
        acquisition["microphone_distance_m"],
        "$.acquisition.microphone_distance_m",
    )
    if distance <= 0.0:
        raise ValueError("$.acquisition.microphone_distance_m must be positive")
    for field in (
        "reference_level_correction_db",
        "signal_start_s",
        "signal_duration_s",
        "background_start_s",
        "background_duration_s",
    ):
        value = number(acquisition[field], f"$.acquisition.{field}")
        if field != "reference_level_correction_db" and value < 0.0:
            raise ValueError(f"$.acquisition.{field} cannot be negative")
    if acquisition["signal_duration_s"] <= 0.0:
        raise ValueError("$.acquisition.signal_duration_s must be positive")
    if acquisition["background_duration_s"] <= 0.0:
        raise ValueError("$.acquisition.background_duration_s must be positive")

    calibration = config["calibration"]
    if not isinstance(calibration, dict):
        raise ValueError("$.calibration must be an object")
    exact_fields(
        calibration,
        (
            "wav",
            "channel",
            "level_db_spl",
            "frequency_hz",
            "start_s",
            "duration_s",
            "tone_half_width_hz",
        ),
        "$.calibration",
    )
    text(calibration["wav"], "$.calibration.wav")
    integer(calibration["channel"], "$.calibration.channel", 0, 63)
    for field in (
        "level_db_spl",
        "frequency_hz",
        "start_s",
        "duration_s",
        "tone_half_width_hz",
    ):
        value = number(calibration[field], f"$.calibration.{field}")
        if field != "start_s" and value <= 0.0:
            raise ValueError(f"$.calibration.{field} must be positive")

    analysis = config["analysis"]
    if not isinstance(analysis, dict):
        raise ValueError("$.analysis must be an object")
    exact_fields(
        analysis,
        (
            "welch_resolution_hz",
            "overlap_fraction",
            "tonal_half_width_hz",
            "maximum_rpm_cv",
            "minimum_snr_db",
            "maximum_peak_normalized",
            "minimum_tone_prominence_db",
        ),
        "$.analysis",
    )
    parsed_analysis = {
        field: number(raw_value, f"$.analysis.{field}")
        for field, raw_value in analysis.items()
    }
    if parsed_analysis["welch_resolution_hz"] <= 0.0:
        raise ValueError("$.analysis.welch_resolution_hz must be positive")
    if not 0.0 <= parsed_analysis["overlap_fraction"] < 1.0:
        raise ValueError("$.analysis.overlap_fraction must be in [0,1)")
    if parsed_analysis["tonal_half_width_hz"] <= 0.0:
        raise ValueError("$.analysis.tonal_half_width_hz must be positive")
    if not 0.0 <= parsed_analysis["maximum_rpm_cv"] <= 0.25:
        raise ValueError("$.analysis.maximum_rpm_cv must be in [0,0.25]")
    if not -20.0 <= parsed_analysis["minimum_snr_db"] <= 80.0:
        raise ValueError("$.analysis.minimum_snr_db must be in [-20,80]")
    if not 0.0 < parsed_analysis["maximum_peak_normalized"] <= 1.0:
        raise ValueError(
            "$.analysis.maximum_peak_normalized must be in (0,1]"
        )
    if not 0.0 <= parsed_analysis["minimum_tone_prominence_db"] <= 40.0:
        raise ValueError(
            "$.analysis.minimum_tone_prominence_db must be in [0,40]"
        )

    environment = config["environment_defaults"]
    if not isinstance(environment, dict):
        raise ValueError("$.environment_defaults must be an object")
    exact_fields(
        environment,
        (
            "voltage_v",
            "ambient_temperature_c",
            "relative_humidity_percent",
            "ambient_pressure_kpa",
        ),
        "$.environment_defaults",
    )
    voltage = number(
        environment["voltage_v"],
        "$.environment_defaults.voltage_v",
    )
    temperature = number(
        environment["ambient_temperature_c"],
        "$.environment_defaults.ambient_temperature_c",
    )
    humidity = number(
        environment["relative_humidity_percent"],
        "$.environment_defaults.relative_humidity_percent",
    )
    pressure = number(
        environment["ambient_pressure_kpa"],
        "$.environment_defaults.ambient_pressure_kpa",
    )
    if voltage < 0.0 or not -80.0 <= temperature <= 80.0:
        raise ValueError("$.environment_defaults voltage/temperature invalid")
    if not 0.0 <= humidity <= 100.0 or not 20.0 <= pressure <= 120.0:
        raise ValueError("$.environment_defaults humidity/pressure invalid")

    matrix = config["matrix"]
    if not isinstance(matrix, dict):
        raise ValueError("$.matrix must be an object")
    exact_fields(
        matrix,
        (
            "training_plane_rpm",
            "validation_plane_rpm",
            "directivity_reference_rpm",
            "training_elevation_deg",
            "validation_elevation_deg",
            "training_takes",
            "validation_takes",
        ),
        "$.matrix",
    )
    train_rpm = sorted(
        numeric_list(
            matrix["training_plane_rpm"],
            "$.matrix.training_plane_rpm",
            3,
        )
    )
    validation_rpm = sorted(
        numeric_list(
            matrix["validation_plane_rpm"],
            "$.matrix.validation_plane_rpm",
            2,
        )
    )
    if min(train_rpm) <= 0.0:
        raise ValueError("$.matrix training RPM must be positive")
    overlap = set(train_rpm) & set(validation_rpm)
    if overlap:
        raise ValueError(f"validation plane RPM must be unseen: {sorted(overlap)}")
    if not all(min(train_rpm) < value < max(train_rpm) for value in validation_rpm):
        raise ValueError(
            "validation plane RPM must lie strictly inside training range"
        )
    reference_rpm = number(
        matrix["directivity_reference_rpm"],
        "$.matrix.directivity_reference_rpm",
    )
    if reference_rpm not in train_rpm:
        raise ValueError(
            "directivity_reference_rpm must be a training plane anchor"
        )
    train_angles = sorted(
        numeric_list(
            matrix["training_elevation_deg"],
            "$.matrix.training_elevation_deg",
            6,
        )
    )
    validation_angles = sorted(
        numeric_list(
            matrix["validation_elevation_deg"],
            "$.matrix.validation_elevation_deg",
            6,
        )
    )
    for path, angles in (
        ("$.matrix.training_elevation_deg", train_angles),
        ("$.matrix.validation_elevation_deg", validation_angles),
    ):
        if not all(-90.0 <= value <= 90.0 for value in angles):
            raise ValueError(f"{path}: values must be in [-90,90]")
        require_symmetric_angles(angles, path)
        if len({axis_cosine(value) for value in angles}) < 3:
            raise ValueError(f"{path}: at least three distinct axis angles required")
    repeated_mu = sorted(
        {axis_cosine(value) for value in train_angles}
        & {axis_cosine(value) for value in validation_angles}
    )
    if repeated_mu:
        raise ValueError(
            "validation directivity axis cosines must be unseen in training: "
            f"{repeated_mu}"
        )
    integer(matrix["training_takes"], "$.matrix.training_takes", 2, 20)
    integer(matrix["validation_takes"], "$.matrix.validation_takes", 1, 20)

    maximum_rpm = max(train_rpm)
    highest_bpf = (
        maximum_rpm
        * int(hardware["blade_count"])
        * int(hardware["blade_pass_harmonics"])
        / 60.0
    )
    if highest_bpf >= 0.45 * sample_rate:
        raise ValueError(
            "highest planned BPF harmonic violates analyzer Nyquist guard: "
            f"{highest_bpf:.3f} >= {0.45 * sample_rate:.3f} Hz"
        )
    half_width = float(analysis["tonal_half_width_hz"])
    blade_count = int(hardware["blade_count"])
    pole_pairs = int(hardware["motor_pole_pairs"])
    harmonics = int(hardware["blade_pass_harmonics"])
    for speed in sorted(set(train_rpm) | set(validation_rpm)):
        shaft_hz = speed / 60.0
        centers = [
            *(
                (f"bpf-{harmonic}", shaft_hz * blade_count * harmonic)
                for harmonic in range(1, harmonics + 1)
                if shaft_hz * blade_count * harmonic < 0.45 * sample_rate
            ),
            *(
                (label, shaft_hz * coefficient)
                for label, coefficient in (
                    ("shaft", 1),
                    ("electrical", pole_pairs),
                    ("twice-electrical", 2 * pole_pairs),
                )
                if shaft_hz * coefficient < 0.45 * sample_rate
            ),
        ]
        for index, (left_label, left_center) in enumerate(centers):
            for right_label, right_center in centers[index + 1 :]:
                if abs(left_center - right_center) <= 2.0 * half_width:
                    raise ValueError(
                        f"planned tonal windows overlap at {speed:g} RPM: "
                        f"{left_label}={left_center:.3f} Hz and "
                        f"{right_label}={right_center:.3f} Hz"
                    )
    return config


def capture_entry(
    session_id: str,
    family: str,
    split: str,
    rpm: float,
    elevation: float,
    take: int,
) -> dict[str, Any]:
    rpm_token = f"{int(round(rpm)):05d}"
    condition = (
        f"{session_id}-{family}-{split}-rpm{rpm_token}"
        f"-elev{angle_token(elevation)}"
    )
    capture_id = f"{condition}-take{take:02d}"
    return {
        "capture_id": capture_id,
        "maneuver_id": condition,
        "family": family,
        "split": split,
        "target_rpm": rpm,
        "elevation_deg": elevation,
        "take": take,
        "expected_wav": f"raw/{capture_id}.wav",
        "expected_rpm_csv": f"raw/{capture_id}-rpm.csv",
        "background_wav": (
            f"raw/{session_id}-background-elev{angle_token(elevation)}.wav"
        ),
    }


def generate_plan(config: dict[str, Any]) -> dict[str, Any]:
    validate_config(config)
    session_id = config["session_id"]
    matrix = config["matrix"]
    captures = []
    for rpm in sorted(float(value) for value in matrix["training_plane_rpm"]):
        for take in range(1, int(matrix["training_takes"]) + 1):
            captures.append(
                capture_entry(
                    session_id,
                    "plane",
                    "train",
                    rpm,
                    0.0,
                    take,
                )
            )
    reference_rpm = float(matrix["directivity_reference_rpm"])
    for angle in sorted(
        float(value) for value in matrix["training_elevation_deg"]
    ):
        for take in range(1, int(matrix["training_takes"]) + 1):
            captures.append(
                capture_entry(
                    session_id,
                    "directivity",
                    "train",
                    reference_rpm,
                    angle,
                    take,
                )
            )
    for rpm in sorted(float(value) for value in matrix["validation_plane_rpm"]):
        for take in range(1, int(matrix["validation_takes"]) + 1):
            captures.append(
                capture_entry(
                    session_id,
                    "plane",
                    "validation",
                    rpm,
                    0.0,
                    take,
                )
            )
    for angle in sorted(
        float(value) for value in matrix["validation_elevation_deg"]
    ):
        for take in range(1, int(matrix["validation_takes"]) + 1):
            captures.append(
                capture_entry(
                    session_id,
                    "directivity",
                    "validation",
                    reference_rpm,
                    angle,
                    take,
                )
            )
    ids = [entry["capture_id"] for entry in captures]
    if len(ids) != len(set(ids)):
        raise AssertionError("generated duplicate capture ids")
    return {
        "schema_version": PLAN_SCHEMA_VERSION,
        "session_id": session_id,
        "purpose": (
            "calibrated single-source order/directivity profile with "
            "independent RPM and axis-angle holdouts"
        ),
        "configuration": config,
        "capture_count": len(captures),
        "background_files": sorted(
            {entry["background_wav"] for entry in captures}
        ),
        "captures": captures,
        "release_state": "planned_not_measured",
    }


def initial_result_rows(plan: dict[str, Any]) -> list[dict[str, str]]:
    acquisition = plan["configuration"]["acquisition"]
    environment = plan["configuration"]["environment_defaults"]
    rows = []
    for capture in plan["captures"]:
        rows.append(
            {
                "capture_id": capture["capture_id"],
                "status": "planned",
                "wav": capture["expected_wav"],
                "rpm_csv": capture["expected_rpm_csv"],
                "start_s": str(acquisition["signal_start_s"]),
                "duration_s": str(acquisition["signal_duration_s"]),
                "background_start_s": str(
                    acquisition["background_start_s"]
                ),
                "background_duration_s": str(
                    acquisition["background_duration_s"]
                ),
                "thrust_n": "",
                "voltage_v": str(environment["voltage_v"]),
                "current_a": "",
                "ambient_temperature_c": str(
                    environment["ambient_temperature_c"]
                ),
                "relative_humidity_percent": str(
                    environment["relative_humidity_percent"]
                ),
                "ambient_pressure_kpa": str(
                    environment["ambient_pressure_kpa"]
                ),
                "notes": "",
            }
        )
    return rows


def write_json_atomic(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(
        value,
        ensure_ascii=False,
        indent=2,
        sort_keys=True,
        allow_nan=False,
    ) + "\n"
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=path.name + ".",
        suffix=".tmp",
        dir=path.parent,
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(payload)
        os.replace(temporary_name, path)
    except BaseException:
        try:
            os.unlink(temporary_name)
        except FileNotFoundError:
            pass
        raise


def write_results_csv(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=path.name + ".",
        suffix=".tmp",
        dir=path.parent,
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(
                stream,
                fieldnames=RESULT_FIELDS,
                lineterminator="\n",
            )
            writer.writeheader()
            writer.writerows(rows)
        os.replace(temporary_name, path)
    except BaseException:
        try:
            os.unlink(temporary_name)
        except FileNotFoundError:
            pass
        raise


def load_results(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != list(RESULT_FIELDS):
            raise ValueError(
                f"{path}: expected exact header {list(RESULT_FIELDS)}"
            )
        rows = list(reader)
    if not rows:
        raise ValueError(f"{path}: no result rows")
    return rows


def csv_number(value: str, path: str) -> float:
    if not value.strip():
        raise ValueError(f"{path}: value has not been recorded")
    try:
        result = float(value)
    except ValueError as error:
        raise ValueError(f"{path}: expected a number") from error
    if not math.isfinite(result):
        raise ValueError(f"{path}: expected a finite number")
    return result


def resolve_existing(base: Path, relative: str, path: str) -> Path:
    raw = Path(text(relative, path))
    resolved = (raw if raw.is_absolute() else base / raw).resolve()
    if not resolved.is_file():
        raise ValueError(f"{path}: file does not exist: {resolved}")
    return resolved


def relative_path(path: Path, base: Path) -> str:
    return Path(os.path.relpath(path, base)).as_posix()


def materialize(
    plan: dict[str, Any],
    plan_path: Path,
    result_rows: list[dict[str, str]],
    results_path: Path,
    output_manifest_path: Path,
) -> tuple[dict[str, Any], dict[str, Any]]:
    if plan.get("schema_version") != PLAN_SCHEMA_VERSION:
        raise ValueError("capture plan schema version mismatch")
    config = plan.get("configuration")
    if not isinstance(config, dict):
        raise ValueError("capture plan is missing configuration")
    validate_config(config)
    captures = plan.get("captures")
    if not isinstance(captures, list) or not captures:
        raise ValueError("capture plan has no captures")
    capture_by_id = {
        text(item.get("capture_id"), "$.captures[].capture_id"): item
        for item in captures
    }
    if len(capture_by_id) != len(captures):
        raise ValueError("capture plan has duplicate capture ids")
    rows_by_id = {}
    for index, row in enumerate(result_rows):
        capture_id = text(row["capture_id"], f"results[{index}].capture_id")
        if capture_id in rows_by_id:
            raise ValueError(f"duplicate result capture_id: {capture_id}")
        rows_by_id[capture_id] = row
    if set(rows_by_id) != set(capture_by_id):
        missing = sorted(set(capture_by_id) - set(rows_by_id))
        extra = sorted(set(rows_by_id) - set(capture_by_id))
        raise ValueError(f"result capture ids differ; missing={missing}, extra={extra}")

    source_base = plan_path.parent.resolve()
    manifest_base = output_manifest_path.parent.resolve()
    calibration = config["calibration"]
    calibration_file = resolve_existing(
        source_base,
        calibration["wav"],
        "$.configuration.calibration.wav",
    )
    hardware = config["hardware"]
    acquisition = config["acquisition"]
    recordings = []
    for capture_id in sorted(capture_by_id):
        capture = capture_by_id[capture_id]
        row = rows_by_id[capture_id]
        if row["status"].strip().lower() != "captured":
            raise ValueError(f"{capture_id}: status must be captured")
        if row["wav"] != capture["expected_wav"]:
            raise ValueError(f"{capture_id}: WAV path differs from capture plan")
        if row["rpm_csv"] != capture["expected_rpm_csv"]:
            raise ValueError(f"{capture_id}: RPM path differs from capture plan")
        wav = resolve_existing(source_base, row["wav"], f"{capture_id}.wav")
        rpm_csv = resolve_existing(
            source_base,
            row["rpm_csv"],
            f"{capture_id}.rpm_csv",
        )
        background = resolve_existing(
            source_base,
            capture["background_wav"],
            f"{capture_id}.background_wav",
        )
        numeric = {
            field: csv_number(row[field], f"{capture_id}.{field}")
            for field in (
                "start_s",
                "duration_s",
                "background_start_s",
                "background_duration_s",
                "thrust_n",
                "voltage_v",
                "current_a",
                "ambient_temperature_c",
                "relative_humidity_percent",
                "ambient_pressure_kpa",
            )
        }
        if numeric["duration_s"] <= 0.0 or numeric["background_duration_s"] <= 0.0:
            raise ValueError(f"{capture_id}: durations must be positive")
        if any(
            numeric[field] < 0.0
            for field in ("thrust_n", "voltage_v", "current_a")
        ):
            raise ValueError(f"{capture_id}: load/electrical values cannot be negative")
        recordings.append(
            {
                "recording_id": capture_id,
                "maneuver_id": capture["maneuver_id"],
                "split": capture["split"],
                "wav": relative_path(wav, manifest_base),
                "background_wav": relative_path(background, manifest_base),
                "rpm_csv": relative_path(rpm_csv, manifest_base),
                "channel": acquisition["channel"],
                "start_s": numeric["start_s"],
                "duration_s": numeric["duration_s"],
                "background_start_s": numeric["background_start_s"],
                "background_duration_s": numeric["background_duration_s"],
                "elevation_deg": capture["elevation_deg"],
                "microphone_distance_m": acquisition[
                    "microphone_distance_m"
                ],
                "reference_level_correction_db": acquisition[
                    "reference_level_correction_db"
                ],
                "blade_count": hardware["blade_count"],
                "motor_pole_pairs": hardware["motor_pole_pairs"],
                "blade_pass_harmonics": hardware[
                    "blade_pass_harmonics"
                ],
                "airframe_id": hardware["airframe_id"],
                "source_configuration": hardware[
                    "source_configuration"
                ],
                "motor_id": hardware["motor_id"],
                "propeller_id": hardware["propeller_id"],
                "microphone_id": hardware["microphone_id"],
                "signal_chain_id": hardware["signal_chain_id"],
                "thrust_n": numeric["thrust_n"],
                "voltage_v": numeric["voltage_v"],
                "current_a": numeric["current_a"],
                "ambient_temperature_c": numeric[
                    "ambient_temperature_c"
                ],
                "relative_humidity_percent": numeric[
                    "relative_humidity_percent"
                ],
                "ambient_pressure_kpa": numeric[
                    "ambient_pressure_kpa"
                ],
            }
        )
    manifest = {
        "schema_version": 1,
        "calibration": {
            **calibration,
            "wav": relative_path(calibration_file, manifest_base),
        },
        "analysis": config["analysis"],
        "recordings": recordings,
    }
    report = {
        "schema_version": 1,
        "session_id": config["session_id"],
        "capture_count": len(recordings),
        "training_rows": sum(
            recording["split"] == "train" for recording in recordings
        ),
        "validation_rows": sum(
            recording["split"] == "validation" for recording in recordings
        ),
        "plan": str(plan_path),
        "plan_sha256": sha256_file(plan_path),
        "results": str(results_path),
        "results_sha256": sha256_file(results_path),
        "manifest": str(output_manifest_path),
        "release_state": "captured_not_yet_analyzed",
    }
    return manifest, report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    generate = subparsers.add_parser("generate")
    generate.add_argument("--config-json", type=Path, required=True)
    generate.add_argument("--output-plan-json", type=Path, required=True)
    generate.add_argument("--output-results-csv", type=Path, required=True)
    materialize_parser = subparsers.add_parser("materialize")
    materialize_parser.add_argument("--plan-json", type=Path, required=True)
    materialize_parser.add_argument("--results-csv", type=Path, required=True)
    materialize_parser.add_argument(
        "--output-manifest-json",
        type=Path,
        required=True,
    )
    materialize_parser.add_argument(
        "--output-report-json",
        type=Path,
        required=True,
    )
    args = parser.parse_args()

    if args.command == "generate":
        config = strict_json_load(args.config_json)
        plan = generate_plan(config)
        write_json_atomic(args.output_plan_json, plan)
        write_results_csv(
            args.output_results_csv,
            initial_result_rows(plan),
        )
        print(
            json.dumps(
                {
                    "status": "planned",
                    "session_id": plan["session_id"],
                    "captures": plan["capture_count"],
                    "background_files": len(plan["background_files"]),
                    "release_state": plan["release_state"],
                },
                sort_keys=True,
            )
        )
        return 0

    plan = strict_json_load(args.plan_json)
    results = load_results(args.results_csv)
    manifest, report = materialize(
        plan,
        args.plan_json,
        results,
        args.results_csv,
        args.output_manifest_json,
    )
    write_json_atomic(args.output_manifest_json, manifest)
    report["manifest_sha256"] = sha256_file(args.output_manifest_json)
    write_json_atomic(args.output_report_json, report)
    print(
        json.dumps(
            {
                "status": "materialized",
                "session_id": report["session_id"],
                "captures": report["capture_count"],
                "release_state": report["release_state"],
                "manifest_sha256": report["manifest_sha256"],
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
