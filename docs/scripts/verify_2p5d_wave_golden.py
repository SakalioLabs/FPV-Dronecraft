#!/usr/bin/env python3
"""Verify a promoted 4 kHz 2.5D double-edge golden fixture."""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path
from typing import Any


class InvalidGolden(ValueError):
    """Raised when a promoted wave fixture violates its contract."""


def finite_number(value: Any, path: str) -> float:
    if (
        isinstance(value, bool)
        or not isinstance(value, (int, float))
        or not math.isfinite(value)
    ):
        raise InvalidGolden(f"{path} must be a finite number")
    return float(value)


def integer(value: Any, path: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise InvalidGolden(f"{path} must be an integer")
    return value


def object_field(
    value: dict[str, Any],
    field: str,
    path: str,
) -> dict[str, Any]:
    nested = value.get(field)
    if not isinstance(nested, dict):
        raise InvalidGolden(f"{path}.{field} must be an object")
    return nested


def require_close(
    actual: float,
    expected: float,
    path: str,
    *,
    absolute_tolerance: float = 1.0e-9,
) -> None:
    if not math.isclose(
        actual,
        expected,
        rel_tol=1.0e-9,
        abs_tol=absolute_tolerance,
    ):
        raise InvalidGolden(
            f"{path} is {actual!r}, expected {expected!r}"
        )


def verify_fixture(fixture: dict[str, Any]) -> dict[str, Any]:
    if fixture.get("status") != "complete":
        raise InvalidGolden("status must be complete")
    if fixture.get("fixture_kind") != "2.5d-double-edge-wave-golden":
        raise InvalidGolden("fixture_kind is not recognized")
    if fixture.get("schema_version") != 1:
        raise InvalidGolden("fixture schema_version must equal 1")
    provenance = object_field(fixture, "provenance", "fixture")
    if provenance.get("generator") != (
        "docs/scripts/finalize_2p5d_spatial_gate.py"
    ):
        raise InvalidGolden("fixture provenance generator changed")
    if provenance.get("promotion_rule") != (
        "terminal quadrature plus 12/18-cell complex spatial gate"
    ):
        raise InvalidGolden("fixture promotion rule changed")
    candidate = object_field(fixture, "candidate", "fixture")
    if candidate.get("schema_version") != 1:
        raise InvalidGolden("candidate.schema_version must equal 1")
    if candidate.get("model") != (
        "2.5d-point-source-helmholtz-double-edge"
    ):
        raise InvalidGolden("candidate.model is not recognized")
    if candidate.get("complex_phase_convention") != (
        "outgoing exp(+i*k*r)"
    ):
        raise InvalidGolden("complex phase convention changed")
    require_close(
        finite_number(
            candidate.get("speed_of_sound_m_per_s"),
            "candidate.speed_of_sound_m_per_s",
        ),
        343.0,
        "candidate.speed_of_sound_m_per_s",
    )
    require_close(
        finite_number(
            candidate.get("frequency_hz"),
            "candidate.frequency_hz",
        ),
        4000.0,
        "candidate.frequency_hz",
    )

    geometry = object_field(candidate, "geometry", "candidate")
    expected_geometry = {
        "receiver_x_m": 1.6125,
        "receiver_z_m": 0.6,
        "screen_back_x_m": 1.0125,
        "screen_front_x_m": 0.9875,
        "screen_thickness_m": 0.025,
        "screen_top_z_m": 0.8,
        "source_x_m": 0.3875,
        "source_z_m": 0.6,
    }
    for field, expected in expected_geometry.items():
        require_close(
            finite_number(
                geometry.get(field),
                f"candidate.geometry.{field}",
            ),
            expected,
            f"candidate.geometry.{field}",
        )

    domain = object_field(candidate, "domain", "candidate")
    expected_domain = {
        "maximum_x_m": 1.8125,
        "maximum_z_m": 1.2125,
        "minimum_x_m": 0.1875,
        "minimum_z_m": 0.1875,
        "pml_width_m": 0.0625,
    }
    for field, expected in expected_domain.items():
        require_close(
            finite_number(
                domain.get(field),
                f"candidate.domain.{field}",
            ),
            expected,
            f"candidate.domain.{field}",
        )

    grid = object_field(candidate, "grid", "candidate")
    thickness_cells = integer(
        grid.get("thickness_cells"),
        "candidate.grid.thickness_cells",
    )
    if thickness_cells != 18:
        raise InvalidGolden(
            "candidate.grid.thickness_cells must equal 18"
        )
    cell_size = finite_number(
        grid.get("cell_size_m"),
        "candidate.grid.cell_size_m",
    )
    require_close(
        cell_size * thickness_cells,
        expected_geometry["screen_thickness_m"],
        "candidate physical screen thickness",
        absolute_tolerance=1.0e-12,
    )
    if integer(
        grid.get("unknowns_per_solve"),
        "candidate.grid.unknowns_per_solve",
    ) <= 0:
        raise InvalidGolden("unknowns_per_solve must be positive")
    if finite_number(
        grid.get("points_per_wavelength"),
        "candidate.grid.points_per_wavelength",
    ) < 24.0:
        raise InvalidGolden("points_per_wavelength is below 24")

    quadrature = object_field(candidate, "quadrature", "candidate")
    if integer(
        quadrature.get("coarse_intervals_per_branch"),
        "candidate.quadrature.coarse_intervals_per_branch",
    ) != 96:
        raise InvalidGolden("coarse quadrature order must equal 96")
    if integer(
        quadrature.get("refined_intervals_per_branch"),
        "candidate.quadrature.refined_intervals_per_branch",
    ) != 192:
        raise InvalidGolden("refined quadrature order must equal 192")
    if abs(
        finite_number(
            quadrature.get("magnitude_delta_db"),
            "candidate.quadrature.magnitude_delta_db",
        )
    ) > 0.25:
        raise InvalidGolden("candidate quadrature magnitude gate failed")
    if abs(
        finite_number(
            quadrature.get("phase_delta_degrees"),
            "candidate.quadrature.phase_delta_degrees",
        )
    ) > 2.0:
        raise InvalidGolden("candidate quadrature phase gate failed")
    if integer(
        candidate.get("unique_helmholtz_solves"),
        "candidate.unique_helmholtz_solves",
    ) != 384:
        raise InvalidGolden("candidate unique solves must equal 384")

    pressure = object_field(candidate, "pressure", "candidate")
    raw = complex(
        finite_number(
            pressure.get("raw_real"),
            "candidate.pressure.raw_real",
        ),
        finite_number(
            pressure.get("raw_imaginary"),
            "candidate.pressure.raw_imaginary",
        ),
    )
    source_edge = math.hypot(
        expected_geometry["screen_front_x_m"]
        - expected_geometry["source_x_m"],
        expected_geometry["screen_top_z_m"]
        - expected_geometry["source_z_m"],
    )
    receiver_edge = math.hypot(
        expected_geometry["receiver_x_m"]
        - expected_geometry["screen_back_x_m"],
        expected_geometry["screen_top_z_m"]
        - expected_geometry["receiver_z_m"],
    )
    shortest_path = (
        source_edge
        + expected_geometry["screen_thickness_m"]
        + receiver_edge
    )
    expected_normalized_db = 20.0 * math.log10(
        abs(raw) * shortest_path
    )
    require_close(
        finite_number(
            pressure.get("normalized_db"),
            "candidate.pressure.normalized_db",
        ),
        expected_normalized_db,
        "candidate.pressure.normalized_db",
        absolute_tolerance=1.0e-10,
    )

    spatial = object_field(fixture, "spatial_gate", "fixture")
    if spatial.get("passed") is not True:
        raise InvalidGolden("spatial_gate.passed must be true")
    magnitude_delta = finite_number(
        spatial.get("magnitude_delta_db"),
        "spatial_gate.magnitude_delta_db",
    )
    phase_delta = finite_number(
        spatial.get("phase_delta_degrees"),
        "spatial_gate.phase_delta_degrees",
    )
    if abs(magnitude_delta) > 0.5 or abs(phase_delta) > 5.0:
        raise InvalidGolden("spatial gate exceeds fixed thresholds")
    require_close(
        finite_number(
            spatial.get("candidate_cell_size_m"),
            "spatial_gate.candidate_cell_size_m",
        ),
        cell_size,
        "spatial_gate.candidate_cell_size_m",
    )
    require_close(
        finite_number(
            spatial.get("candidate_raw_real"),
            "spatial_gate.candidate_raw_real",
        ),
        raw.real,
        "spatial_gate.candidate_raw_real",
    )
    require_close(
        finite_number(
            spatial.get("candidate_raw_imaginary"),
            "spatial_gate.candidate_raw_imaginary",
        ),
        raw.imag,
        "spatial_gate.candidate_raw_imaginary",
    )
    return {
        "frequency_hz": 4000.0,
        "magnitude_delta_db": magnitude_delta,
        "normalized_db": expected_normalized_db,
        "phase_delta_degrees": phase_delta,
        "status": "valid",
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify the promoted 4 kHz double-edge wave golden."
    )
    parser.add_argument("--fixture-json", type=Path, required=True)
    arguments = parser.parse_args()
    if not arguments.fixture_json.exists():
        print(
            json.dumps(
                {
                    "status": "not-present",
                    "fixture": str(arguments.fixture_json),
                },
                indent=2,
                sort_keys=True,
            )
        )
        return 3
    try:
        fixture = json.loads(
            arguments.fixture_json.read_text(encoding="utf-8")
        )
        if not isinstance(fixture, dict):
            raise InvalidGolden("fixture must be a JSON object")
        report = verify_fixture(fixture)
    except (OSError, json.JSONDecodeError, InvalidGolden) as error:
        print(
            json.dumps(
                {"status": "invalid", "error": str(error)},
                indent=2,
                sort_keys=True,
            )
        )
        return 2
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
