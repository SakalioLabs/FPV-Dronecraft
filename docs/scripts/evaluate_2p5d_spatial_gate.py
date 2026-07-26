#!/usr/bin/env python3
"""Evaluate spatial convergence between two completed 2.5D references."""

from __future__ import annotations

import argparse
import cmath
import json
import math
import sys
from pathlib import Path
from typing import Any


DOMAIN_FIELDS = (
    "minimum_x_m",
    "maximum_x_m",
    "minimum_z_m",
    "maximum_z_m",
    "pml_width_m",
)
GEOMETRY_FIELDS = (
    "receiver_x_m",
    "receiver_z_m",
    "screen_back_x_m",
    "screen_front_x_m",
    "screen_thickness_m",
    "screen_top_z_m",
    "source_x_m",
    "source_z_m",
)


class InvalidReference(ValueError):
    """Raised when two reports cannot form a spatial-convergence pair."""


def read_report(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise InvalidReference(f"cannot read {path}: {error}") from error
    if not isinstance(value, dict):
        raise InvalidReference(f"{path} does not contain a JSON object")
    return value


def number(
    report: dict[str, Any],
    field: str,
    report_name: str,
) -> float:
    value = report.get(field)
    if (
        isinstance(value, bool)
        or not isinstance(value, (int, float))
        or not math.isfinite(value)
    ):
        raise InvalidReference(
            f"{report_name}.{field} must be a finite number"
        )
    return float(value)


def text_field(
    report: dict[str, Any],
    field: str,
    report_name: str,
) -> str:
    value = report.get(field)
    if not isinstance(value, str) or not value:
        raise InvalidReference(
            f"{report_name}.{field} must be non-empty text"
        )
    return value


def nested_number(
    report: dict[str, Any],
    group: str,
    field: str,
    report_name: str,
) -> float:
    nested = report.get(group)
    if not isinstance(nested, dict):
        raise InvalidReference(f"{report_name}.{group} must be an object")
    return number(nested, field, f"{report_name}.{group}")


def close(
    first: float,
    second: float,
    *,
    absolute_tolerance: float = 1.0e-9,
) -> bool:
    return math.isclose(
        first,
        second,
        rel_tol=1.0e-9,
        abs_tol=absolute_tolerance,
    )


def validate_pair(
    reference: dict[str, Any],
    candidate: dict[str, Any],
) -> tuple[float, float, float]:
    for name, report in (("reference", reference), ("candidate", candidate)):
        if report.get("passed") is not True:
            raise InvalidReference(
                f"{name} did not pass its internal quadrature gate"
            )
        unique_solves = number(report, "unique_helmholtz_solves", name)
        if unique_solves <= 0:
            raise InvalidReference(
                f"{name}.unique_helmholtz_solves must be positive"
            )

    reference_frequency = number(reference, "frequency_hz", "reference")
    candidate_frequency = number(candidate, "frequency_hz", "candidate")
    if not close(reference_frequency, candidate_frequency):
        raise InvalidReference("frequency_hz differs between reports")

    for field in ("model", "complex_phase_convention"):
        reference_text = text_field(reference, field, "reference")
        candidate_text = text_field(candidate, field, "candidate")
        if reference_text != candidate_text:
            raise InvalidReference(f"{field} differs between reports")
    for name, report in (("reference", reference), ("candidate", candidate)):
        schema_version = report.get("schema_version")
        if schema_version != 1:
            raise InvalidReference(
                f"{name}.schema_version must equal 1"
            )
    reference_speed = number(
        reference,
        "speed_of_sound_m_per_s",
        "reference",
    )
    candidate_speed = number(
        candidate,
        "speed_of_sound_m_per_s",
        "candidate",
    )
    if not close(reference_speed, candidate_speed):
        raise InvalidReference(
            "speed_of_sound_m_per_s differs between reports"
        )

    reference_intervals = number(
        reference,
        "refined_intervals_per_branch",
        "reference",
    )
    candidate_intervals = number(
        candidate,
        "refined_intervals_per_branch",
        "candidate",
    )
    if reference_intervals != candidate_intervals:
        raise InvalidReference(
            "refined quadrature order differs between reports"
        )

    for field in DOMAIN_FIELDS:
        reference_value = nested_number(
            reference,
            "domain",
            field,
            "reference",
        )
        candidate_value = nested_number(
            candidate,
            "domain",
            field,
            "candidate",
        )
        if not close(reference_value, candidate_value):
            raise InvalidReference(f"domain.{field} differs between reports")

    for field in GEOMETRY_FIELDS:
        reference_value = nested_number(
            reference,
            "geometry",
            field,
            "reference",
        )
        candidate_value = nested_number(
            candidate,
            "geometry",
            field,
            "candidate",
        )
        if not close(reference_value, candidate_value):
            raise InvalidReference(
                f"geometry.{field} differs between reports"
            )

    reference_cell_size = nested_number(
        reference,
        "grid",
        "cell_size_m",
        "reference",
    )
    candidate_cell_size = nested_number(
        candidate,
        "grid",
        "cell_size_m",
        "candidate",
    )
    if candidate_cell_size >= reference_cell_size:
        raise InvalidReference(
            "candidate grid must be finer than the reference grid"
        )

    reference_cells = nested_number(
        reference,
        "grid",
        "thickness_cells",
        "reference",
    )
    candidate_cells = nested_number(
        candidate,
        "grid",
        "thickness_cells",
        "candidate",
    )
    reference_thickness = reference_cell_size * reference_cells
    candidate_thickness = candidate_cell_size * candidate_cells
    if not close(
        reference_thickness,
        candidate_thickness,
        absolute_tolerance=1.0e-12,
    ):
        raise InvalidReference(
            "physical screen thickness differs between reports"
        )
    return (
        reference_frequency,
        reference_cell_size,
        candidate_cell_size,
    )


def complex_pressure(
    report: dict[str, Any],
    report_name: str,
) -> complex:
    pressure = complex(
        number(report, "refined_raw_real", report_name),
        number(report, "refined_raw_imaginary", report_name),
    )
    if abs(pressure) == 0.0:
        raise InvalidReference(f"{report_name} pressure is zero")
    return pressure


def evaluate_pair(
    reference: dict[str, Any],
    candidate: dict[str, Any],
    maximum_magnitude_delta_db: float,
    maximum_phase_delta_degrees: float,
) -> dict[str, Any]:
    frequency, reference_dx, candidate_dx = validate_pair(
        reference,
        candidate,
    )
    reference_pressure = complex_pressure(reference, "reference")
    candidate_pressure = complex_pressure(candidate, "candidate")
    ratio = candidate_pressure / reference_pressure
    magnitude_delta_db = 20.0 * math.log10(abs(ratio))
    phase_delta_degrees = math.degrees(cmath.phase(ratio))
    passed = (
        abs(magnitude_delta_db) <= maximum_magnitude_delta_db
        and abs(phase_delta_degrees) <= maximum_phase_delta_degrees
    )
    return {
        "candidate_cell_size_m": candidate_dx,
        "candidate_raw_imaginary": candidate_pressure.imag,
        "candidate_raw_real": candidate_pressure.real,
        "frequency_hz": frequency,
        "gates": {
            "maximum_magnitude_delta_db": maximum_magnitude_delta_db,
            "maximum_phase_delta_degrees": (
                maximum_phase_delta_degrees
            ),
        },
        "magnitude_delta_db": magnitude_delta_db,
        "passed": passed,
        "phase_delta_degrees": phase_delta_degrees,
        "reference_cell_size_m": reference_dx,
        "reference_raw_imaginary": reference_pressure.imag,
        "reference_raw_real": reference_pressure.real,
        "status": "complete",
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Compare a finer completed 2.5D reference with a coarser "
            "completed reference using complex pressure."
        )
    )
    parser.add_argument("--reference-json", type=Path, required=True)
    parser.add_argument("--candidate-json", type=Path, required=True)
    parser.add_argument(
        "--maximum-magnitude-delta-db",
        type=float,
        default=0.5,
    )
    parser.add_argument(
        "--maximum-phase-delta-degrees",
        type=float,
        default=5.0,
    )
    parser.add_argument("--json", action="store_true")
    arguments = parser.parse_args()
    if arguments.maximum_magnitude_delta_db < 0.0:
        parser.error("--maximum-magnitude-delta-db must be non-negative")
    if arguments.maximum_phase_delta_degrees < 0.0:
        parser.error("--maximum-phase-delta-degrees must be non-negative")

    try:
        reference = read_report(arguments.reference_json)
        candidate = read_report(arguments.candidate_json)
        report = evaluate_pair(
            reference,
            candidate,
            arguments.maximum_magnitude_delta_db,
            arguments.maximum_phase_delta_degrees,
        )
    except InvalidReference as error:
        print(
            json.dumps(
                {"status": "invalid", "error": str(error)},
                indent=2 if arguments.json else None,
                sort_keys=True,
            )
        )
        return 2

    print(
        json.dumps(
            report,
            indent=2 if arguments.json else None,
            sort_keys=True,
        )
    )
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
