#!/usr/bin/env python3
"""Pair two validation-only spectral-reference reports by RPM and angle."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import tempfile
from collections import defaultdict
from pathlib import Path
from typing import Any

import numpy as np

from analyze_spectral_reference import RELEASE_CONSTRAINTS, validate_report


SCHEMA_VERSION = 1
COMPARISON_CLASS = "paired_processed_autopower_spectral_delta"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def summarize(values: list[float]) -> dict[str, float | int]:
    if not values or not all(math.isfinite(value) for value in values):
        raise ValueError("summary values must be non-empty and finite")
    array = np.asarray(values, dtype=np.float64)
    return {
        "count": int(array.size),
        "mean_db": float(np.mean(array)),
        "minimum_db": float(np.min(array)),
        "maximum_db": float(np.max(array)),
        "rms_db": float(np.sqrt(np.mean(array**2))),
    }


def measurement_key(measurement: dict[str, Any]) -> tuple[float, float]:
    return float(measurement["rpm"]), float(measurement["theta_deg"])


def indexed_measurements(
    report: dict[str, Any],
) -> dict[tuple[float, float], dict[str, Any]]:
    indexed = {
        measurement_key(measurement): measurement
        for measurement in report["measurements"]
    }
    if len(indexed) != len(report["measurements"]):
        raise ValueError("spectral report contains duplicate RPM/theta pairs")
    return indexed


def coordinate_contract(report: dict[str, Any]) -> dict[str, Any]:
    return {
        "coordinates": report["coordinates"],
        "dimensions": report["dimensions"],
        "parameters": report["parameters"],
    }


def harmonic_map(
    measurement: dict[str, Any],
) -> dict[int, dict[str, Any]]:
    result = {
        int(harmonic["harmonic"]): harmonic
        for harmonic in measurement["harmonics"]
    }
    if len(result) != len(measurement["harmonics"]):
        raise ValueError("measurement contains duplicate harmonic numbers")
    return result


def directivity_map(
    report: dict[str, Any],
) -> dict[tuple[int, float], dict[str, Any]]:
    result = {
        (int(item["harmonic"]), float(item["rpm"])): item
        for item in report["complete_detected_harmonic_directivity"]
    }
    if len(result) != len(
        report["complete_detected_harmonic_directivity"]
    ):
        raise ValueError("report contains duplicate harmonic/RPM directivity")
    return result


def compare_reports(
    *,
    baseline: dict[str, Any],
    candidate: dict[str, Any],
    baseline_label: str,
    candidate_label: str,
    relationship: str,
    baseline_source: dict[str, Any],
    candidate_source: dict[str, Any],
) -> dict[str, Any]:
    validate_report(baseline)
    validate_report(candidate)
    if coordinate_contract(baseline) != coordinate_contract(candidate):
        raise ValueError(
            "baseline and candidate coordinate/parameter contracts differ"
        )
    if not baseline_label or not candidate_label or not relationship:
        raise ValueError("labels and relationship must be non-empty")

    baseline_measurements = indexed_measurements(baseline)
    candidate_measurements = indexed_measurements(candidate)
    if set(baseline_measurements) != set(candidate_measurements):
        raise ValueError("baseline and candidate RPM/theta pairs differ")

    paired = []
    inclusive_by_band: dict[str, list[float]] = defaultdict(list)
    residual_by_band: dict[str, list[float]] = defaultdict(list)
    harmonic_deltas: dict[int, list[float]] = defaultdict(list)
    inclusive_by_rpm: dict[float, dict[str, list[float]]] = defaultdict(
        lambda: defaultdict(list)
    )
    residual_by_rpm: dict[float, dict[str, list[float]]] = defaultdict(
        lambda: defaultdict(list)
    )
    harmonic_by_rpm: dict[float, dict[int, list[float]]] = defaultdict(
        lambda: defaultdict(list)
    )
    for key in sorted(baseline_measurements):
        base = baseline_measurements[key]
        test = candidate_measurements[key]
        base_inclusive = base[
            "band_levels_db_spl_at_reference_distance"
        ]
        test_inclusive = test[
            "band_levels_db_spl_at_reference_distance"
        ]
        base_residual = base[
            "bpf_removed_band_levels_db_spl_at_reference_distance"
        ]
        test_residual = test[
            "bpf_removed_band_levels_db_spl_at_reference_distance"
        ]
        if set(base_inclusive) != set(test_inclusive):
            raise ValueError(f"{key}: inclusive band names differ")
        if set(base_residual) != set(test_residual):
            raise ValueError(f"{key}: BPF-removed band names differ")
        inclusive_delta = {
            name: float(test_inclusive[name] - base_inclusive[name])
            for name in sorted(base_inclusive)
        }
        residual_delta = {
            name: float(test_residual[name] - base_residual[name])
            for name in sorted(base_residual)
        }
        for name, value in inclusive_delta.items():
            inclusive_by_band[name].append(value)
            inclusive_by_rpm[key[0]][name].append(value)
        for name, value in residual_delta.items():
            residual_by_band[name].append(value)
            residual_by_rpm[key[0]][name].append(value)

        base_harmonics = harmonic_map(base)
        test_harmonics = harmonic_map(test)
        if set(base_harmonics) != set(test_harmonics):
            raise ValueError(f"{key}: harmonic sets differ")
        orders = []
        for harmonic in sorted(base_harmonics):
            base_order = base_harmonics[harmonic]
            test_order = test_harmonics[harmonic]
            comparable = bool(
                base_order["detected"] and test_order["detected"]
            )
            delta = (
                float(
                    test_order["level_db_spl_at_reference_distance"]
                    - base_order["level_db_spl_at_reference_distance"]
                )
                if comparable
                else None
            )
            if delta is not None:
                harmonic_deltas[harmonic].append(delta)
                harmonic_by_rpm[key[0]][harmonic].append(delta)
            orders.append(
                {
                    "harmonic": harmonic,
                    "both_detected": comparable,
                    "level_delta_db": delta,
                    "baseline_prominence_db": float(
                        base_order["local_prominence_db"]
                    ),
                    "candidate_prominence_db": float(
                        test_order["local_prominence_db"]
                    ),
                }
            )
        paired.append(
            {
                "rpm": key[0],
                "theta_deg": key[1],
                "inclusive_band_delta_db": inclusive_delta,
                "bpf_removed_band_delta_db": residual_delta,
                "harmonic_delta": orders,
            }
        )

    baseline_directivity = directivity_map(baseline)
    candidate_directivity = directivity_map(candidate)
    common_directivity = sorted(
        set(baseline_directivity) & set(candidate_directivity)
    )
    directivity_deltas = []
    for key in common_directivity:
        base_fit = baseline_directivity[key]["fit"]
        test_fit = candidate_directivity[key]["fit"]
        directivity_deltas.append(
            {
                "harmonic": key[0],
                "rpm": key[1],
                "c2_delta_db": float(
                    test_fit["c2_db"] - base_fit["c2_db"]
                ),
                "c4_delta_db": float(
                    test_fit["c4_db"] - base_fit["c4_db"]
                ),
                "axis_delta_db": float(
                    test_fit["axis_db"] - base_fit["axis_db"]
                ),
                "axis_is_extrapolated": bool(
                    base_fit["axis_is_extrapolated"]
                    or test_fit["axis_is_extrapolated"]
                ),
            }
        )

    report = {
        "schema_version": SCHEMA_VERSION,
        "comparison_class": COMPARISON_CLASS,
        "relationship": relationship,
        "baseline": {
            "label": baseline_label,
            **baseline_source,
        },
        "candidate": {
            "label": candidate_label,
            **candidate_source,
        },
        "release_constraints": dict(RELEASE_CONSTRAINTS),
        "processing_limits": {
            "paired_inputs_are_processed_autopower": True,
            "deltas_do_not_recover_time_history_or_phase": True,
            "causal_attribution_requires_matched_geometry_metadata": True,
            "release_profile_fitting_remains_forbidden": True,
        },
        "coordinate_contract": coordinate_contract(baseline),
        "paired_measurements": paired,
        "summary": {
            "inclusive_band_delta_db": {
                name: summarize(values)
                for name, values in sorted(inclusive_by_band.items())
            },
            "bpf_removed_band_delta_db": {
                name: summarize(values)
                for name, values in sorted(residual_by_band.items())
            },
            "harmonic_level_delta_db": {
                str(harmonic): summarize(values)
                for harmonic, values in sorted(harmonic_deltas.items())
                if values
            },
            "by_rpm": {
                str(speed): {
                    "inclusive_band_delta_db": {
                        name: summarize(values)
                        for name, values in sorted(
                            inclusive_by_rpm[speed].items()
                        )
                    },
                    "bpf_removed_band_delta_db": {
                        name: summarize(values)
                        for name, values in sorted(
                            residual_by_rpm[speed].items()
                        )
                    },
                    "harmonic_level_delta_db": {
                        str(harmonic): summarize(values)
                        for harmonic, values in sorted(
                            harmonic_by_rpm[speed].items()
                        )
                        if values
                    },
                }
                for speed in sorted(inclusive_by_rpm)
            },
            "common_complete_directivity_fits": len(
                common_directivity
            ),
        },
        "directivity_fit_delta": directivity_deltas,
    }
    validate_comparison(report)
    return report


def validate_comparison(report: dict[str, Any]) -> None:
    if report.get("schema_version") != SCHEMA_VERSION:
        raise ValueError("spectral comparison schema version mismatch")
    if report.get("comparison_class") != COMPARISON_CLASS:
        raise ValueError("spectral comparison class mismatch")
    if report.get("release_constraints") != RELEASE_CONSTRAINTS:
        raise ValueError("release constraints cannot be weakened or changed")
    limits = report.get("processing_limits")
    if not isinstance(limits, dict) or not all(limits.values()):
        raise ValueError("all paired-autopower limitations must be explicit")
    if not report.get("paired_measurements"):
        raise ValueError("spectral comparison has no paired measurements")


def load_report(path: Path) -> dict[str, Any]:
    report = json.loads(path.read_text(encoding="utf-8"))
    validate_report(report)
    return report


def write_json_atomic(path: Path, report: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(
        report,
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


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-report", type=Path, required=True)
    parser.add_argument("--candidate-report", type=Path, required=True)
    parser.add_argument("--baseline-label", required=True)
    parser.add_argument("--candidate-label", required=True)
    parser.add_argument("--relationship", required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()

    report = compare_reports(
        baseline=load_report(args.baseline_report),
        candidate=load_report(args.candidate_report),
        baseline_label=args.baseline_label,
        candidate_label=args.candidate_label,
        relationship=args.relationship,
        baseline_source={
            "report_path": str(args.baseline_report),
            "report_sha256": sha256_file(args.baseline_report),
        },
        candidate_source={
            "report_path": str(args.candidate_report),
            "report_sha256": sha256_file(args.candidate_report),
        },
    )
    write_json_atomic(args.output_json, report)
    print(
        json.dumps(
            {
                "status": "valid",
                "comparison_class": COMPARISON_CLASS,
                "paired_measurements": len(report["paired_measurements"]),
                "common_complete_directivity_fits": report["summary"][
                    "common_complete_directivity_fits"
                ],
                "release_profile_eligible": False,
                "output_json": str(args.output_json),
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
