#!/usr/bin/env python3
"""Promote an eligible 2.5D finalizer report to a stable golden fixture."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from finalize_2p5d_spatial_gate import write_complete_report


class InvalidPromotion(ValueError):
    """Raised when a finalizer report cannot be promoted."""


def read_finalizer_report(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise InvalidPromotion(f"cannot read {path}: {error}") from error
    if not isinstance(value, dict):
        raise InvalidPromotion("finalizer report must be a JSON object")
    return value


def golden_fixture(report: dict[str, Any]) -> dict[str, Any]:
    if report.get("status") != "complete":
        raise InvalidPromotion("finalizer report is not terminal")
    if report.get("passed") is not True:
        raise InvalidPromotion("spatial gate did not pass")
    if report.get("promotion_eligible") is not True:
        raise InvalidPromotion("finalizer report is not promotion eligible")
    batch_run = report.get("batch_run")
    if not isinstance(batch_run, dict):
        raise InvalidPromotion("batch_run must be an object")
    if (
        batch_run.get("status") != "complete"
        or batch_run.get("remaining_solves") != 0
        or batch_run.get("total_unique_helmholtz_solves")
        != batch_run.get("target_solves")
    ):
        raise InvalidPromotion("batch run is not complete")
    candidate = report.get("candidate_summary")
    if not isinstance(candidate, dict):
        raise InvalidPromotion("candidate_summary must be an object")
    if candidate.get("schema_version") != 1:
        raise InvalidPromotion("unsupported candidate schema")
    spatial_gate = report.get("spatial_gate")
    if not isinstance(spatial_gate, dict):
        raise InvalidPromotion("spatial_gate must be an object")
    if spatial_gate.get("passed") is not True:
        raise InvalidPromotion("embedded spatial gate did not pass")
    return {
        "candidate": candidate,
        "fixture_kind": "2.5d-double-edge-wave-golden",
        "provenance": {
            "generator": (
                "docs/scripts/finalize_2p5d_spatial_gate.py"
            ),
            "promotion_rule": (
                "terminal quadrature plus 12/18-cell complex spatial gate"
            ),
        },
        "schema_version": 1,
        "spatial_gate": spatial_gate,
        "status": "complete",
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Create a deterministic golden fixture only from an eligible "
            "terminal 2.5D finalizer report."
        )
    )
    parser.add_argument("--finalizer-json", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    arguments = parser.parse_args()
    try:
        report = read_finalizer_report(arguments.finalizer_json)
        fixture = golden_fixture(report)
        write_complete_report(fixture, arguments.output_json)
    except (InvalidPromotion, OSError) as error:
        print(
            json.dumps(
                {"status": "invalid", "error": str(error)},
                indent=2,
                sort_keys=True,
            )
        )
        return 2
    print(json.dumps(fixture, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
