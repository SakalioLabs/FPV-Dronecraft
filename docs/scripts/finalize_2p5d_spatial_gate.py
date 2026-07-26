#!/usr/bin/env python3
"""Finalize a checkpointed 2.5D run and evaluate its spatial gate."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path
from typing import Any

from evaluate_2p5d_spatial_gate import (
    DOMAIN_FIELDS,
    GEOMETRY_FIELDS,
    InvalidReference,
    evaluate_pair,
    nested_number,
    number,
    read_report,
    text_field,
)
from inspect_2p5d_batch_run import InvalidBatchRun, inspect_run


def write_complete_report(
    report: dict[str, Any],
    output_path: Path | None,
) -> bool:
    if output_path is None or report.get("status") != "complete":
        return False
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = output_path.with_name(output_path.name + ".tmp")
    temporary_path.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    for attempt in range(20):
        try:
            os.replace(temporary_path, output_path)
            return True
        except PermissionError:
            if attempt == 19:
                raise
            time.sleep(0.05 * (attempt + 1))
    raise AssertionError("unreachable atomic replace loop")


def integer(
    report: dict[str, Any],
    field: str,
    report_name: str,
) -> int:
    value = report.get(field)
    if isinstance(value, bool) or not isinstance(value, int):
        raise InvalidReference(f"{report_name}.{field} must be an integer")
    return value


def nested_integer(
    report: dict[str, Any],
    group: str,
    field: str,
    report_name: str,
) -> int:
    nested = report.get(group)
    if not isinstance(nested, dict):
        raise InvalidReference(f"{report_name}.{group} must be an object")
    return integer(nested, field, f"{report_name}.{group}")


def candidate_summary(candidate: dict[str, Any]) -> dict[str, Any]:
    return {
        "complex_phase_convention": text_field(
            candidate,
            "complex_phase_convention",
            "candidate",
        ),
        "domain": {
            field: nested_number(
                candidate,
                "domain",
                field,
                "candidate",
            )
            for field in DOMAIN_FIELDS
        },
        "frequency_hz": number(candidate, "frequency_hz", "candidate"),
        "geometry": {
            field: nested_number(
                candidate,
                "geometry",
                field,
                "candidate",
            )
            for field in GEOMETRY_FIELDS
        },
        "grid": {
            "cell_size_m": nested_number(
                candidate,
                "grid",
                "cell_size_m",
                "candidate",
            ),
            "points_per_wavelength": nested_number(
                candidate,
                "grid",
                "points_per_wavelength",
                "candidate",
            ),
            "thickness_cells": nested_integer(
                candidate,
                "grid",
                "thickness_cells",
                "candidate",
            ),
            "unknowns_per_solve": nested_integer(
                candidate,
                "grid",
                "unknowns_per_solve",
                "candidate",
            ),
        },
        "model": text_field(candidate, "model", "candidate"),
        "pressure": {
            "normalized_db": number(
                candidate,
                "refined_normalized_db",
                "candidate",
            ),
            "raw_imaginary": number(
                candidate,
                "refined_raw_imaginary",
                "candidate",
            ),
            "raw_real": number(
                candidate,
                "refined_raw_real",
                "candidate",
            ),
        },
        "quadrature": {
            "coarse_intervals_per_branch": integer(
                candidate,
                "coarse_intervals_per_branch",
                "candidate",
            ),
            "magnitude_delta_db": number(
                candidate,
                "magnitude_delta_db",
                "candidate",
            ),
            "phase_delta_degrees": number(
                candidate,
                "phase_delta_degrees",
                "candidate",
            ),
            "refined_intervals_per_branch": integer(
                candidate,
                "refined_intervals_per_branch",
                "candidate",
            ),
        },
        "schema_version": candidate["schema_version"],
        "speed_of_sound_m_per_s": number(
            candidate,
            "speed_of_sound_m_per_s",
            "candidate",
        ),
        "unique_helmholtz_solves": integer(
            candidate,
            "unique_helmholtz_solves",
            "candidate",
        ),
    }


def finalize_run(
    reference_path: Path,
    candidate_directory: Path,
    candidate_label: str,
    target_solves: int,
    maximum_magnitude_delta_db: float,
    maximum_phase_delta_degrees: float,
) -> dict[str, Any]:
    batch_run = inspect_run(
        candidate_directory,
        candidate_label,
        target_solves,
    )
    if batch_run["status"] != "complete":
        return {
            "batch_run": batch_run,
            "passed": None,
            "status": "not-ready",
        }
    final_index = batch_run["last_completed_batch"]
    candidate_path = candidate_directory / (
        f"{candidate_label}-batch{final_index:02d}.json"
    )
    reference = read_report(reference_path)
    candidate = read_report(candidate_path)
    spatial_gate = evaluate_pair(
        reference,
        candidate,
        maximum_magnitude_delta_db,
        maximum_phase_delta_degrees,
    )
    return {
        "batch_run": batch_run,
        "candidate_summary": candidate_summary(candidate),
        "candidate_report": str(candidate_path),
        "passed": spatial_gate["passed"],
        "promotion_eligible": spatial_gate["passed"],
        "reference_report": str(reference_path),
        "spatial_gate": spatial_gate,
        "status": "complete",
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Require a complete checkpointed run, locate its terminal "
            "report, and evaluate spatial convergence."
        )
    )
    parser.add_argument("--reference-json", type=Path, required=True)
    parser.add_argument("--candidate-directory", type=Path, required=True)
    parser.add_argument("--candidate-label", required=True)
    parser.add_argument(
        "--output-json",
        type=Path,
        help=(
            "Atomically persist a terminal pass or spatial-failure report; "
            "not-ready and invalid runs never overwrite this path."
        ),
    )
    parser.add_argument("--target-solves", type=int, default=384)
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
    arguments = parser.parse_args()
    if arguments.target_solves < 1:
        parser.error("--target-solves must be positive")
    if arguments.maximum_magnitude_delta_db < 0.0:
        parser.error("--maximum-magnitude-delta-db must be non-negative")
    if arguments.maximum_phase_delta_degrees < 0.0:
        parser.error("--maximum-phase-delta-degrees must be non-negative")
    try:
        report = finalize_run(
            arguments.reference_json,
            arguments.candidate_directory,
            arguments.candidate_label,
            arguments.target_solves,
            arguments.maximum_magnitude_delta_db,
            arguments.maximum_phase_delta_degrees,
        )
    except (InvalidBatchRun, InvalidReference) as error:
        print(
            json.dumps(
                {"status": "invalid", "error": str(error)},
                indent=2,
                sort_keys=True,
            )
        )
        return 2
    try:
        persisted = write_complete_report(report, arguments.output_json)
    except OSError as error:
        print(
            json.dumps(
                {
                    "status": "invalid",
                    "error": f"cannot persist final report: {error}",
                },
                indent=2,
                sort_keys=True,
            )
        )
        return 2
    report["persisted"] = persisted
    print(json.dumps(report, indent=2, sort_keys=True))
    if report["status"] == "not-ready":
        return 3
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
