#!/usr/bin/env python3
"""Evaluate whether one NEAPTIDE directivity fit transfers across aircraft."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np


EXPECTED_SCHEMA = "neaptide-directivity-analysis-v1"
METRICS = ("overall", "low", "mid", "high")
TRANSFER_RMSE_GATE_DB = 3.0


def fit_coefficients(
    axis_cosines: np.ndarray,
    relative_levels_db: np.ndarray,
) -> np.ndarray:
    design = np.column_stack((axis_cosines**2, axis_cosines**4))
    coefficients, _, _, _ = np.linalg.lstsq(
        design,
        relative_levels_db,
        rcond=None,
    )
    return coefficients


def collect_metric(
    analysis: dict[str, object],
    metric: str,
) -> tuple[np.ndarray, np.ndarray]:
    measurements = analysis["measurements"]
    return (
        np.asarray(
            [row["axis_cosine"] for row in measurements],
            dtype=np.float64,
        ),
        np.asarray(
            [row["relative_to_plane_db"][metric] for row in measurements],
            dtype=np.float64,
        ),
    )


def rmse_for(
    coefficients: np.ndarray,
    axis_cosines: np.ndarray,
    relative_levels_db: np.ndarray,
) -> float:
    design = np.column_stack((axis_cosines**2, axis_cosines**4))
    return float(
        np.sqrt(np.mean((design @ coefficients - relative_levels_db) ** 2))
    )


def compare_analyses(analyses: list[dict[str, object]]) -> dict[str, object]:
    if len(analyses) < 2:
        raise ValueError("at least two aircraft analyses are required")
    ordered = sorted(
        analyses,
        key=lambda analysis: analysis["source"]["aircraft_prefix"],
    )
    aircraft = [analysis["source"]["aircraft_prefix"] for analysis in ordered]
    if len(set(aircraft)) != len(aircraft):
        raise ValueError("aircraft_prefix values must be unique")
    expected_bands = ordered[0]["bands_hz"]
    for analysis in ordered:
        if analysis.get("schema") != EXPECTED_SCHEMA:
            raise ValueError("unexpected analysis schema")
        if analysis.get("bands_hz") != expected_bands:
            raise ValueError("all analyses must use identical bands")

    metrics = {}
    for metric in METRICS:
        all_axis_cosines = []
        all_levels = []
        axis_measurements = {}
        for analysis in ordered:
            axis_cosines, levels = collect_metric(analysis, metric)
            all_axis_cosines.append(axis_cosines)
            all_levels.append(levels)
            axis_measurements[analysis["source"]["aircraft_prefix"]] = float(levels[0])
        pooled_axis_cosines = np.concatenate(all_axis_cosines)
        pooled_levels = np.concatenate(all_levels)
        pooled_coefficients = fit_coefficients(
            pooled_axis_cosines,
            pooled_levels,
        )

        holdouts = []
        for held_out_index, held_out in enumerate(ordered):
            training_axis_cosines = np.concatenate(
                [
                    values
                    for index, values in enumerate(all_axis_cosines)
                    if index != held_out_index
                ]
            )
            training_levels = np.concatenate(
                [
                    values
                    for index, values in enumerate(all_levels)
                    if index != held_out_index
                ]
            )
            held_out_coefficients = fit_coefficients(
                training_axis_cosines,
                training_levels,
            )
            holdouts.append(
                {
                    "aircraft_prefix": held_out["source"]["aircraft_prefix"],
                    "rmse_db": rmse_for(
                        held_out_coefficients,
                        all_axis_cosines[held_out_index],
                        all_levels[held_out_index],
                    ),
                }
            )
        maximum_holdout_rmse = max(row["rmse_db"] for row in holdouts)
        metrics[metric] = {
            "axis_measurements_relative_db": axis_measurements,
            "pooled_c2_db": float(pooled_coefficients[0]),
            "pooled_c4_db": float(pooled_coefficients[1]),
            "pooled_fit_rmse_db": rmse_for(
                pooled_coefficients,
                pooled_axis_cosines,
                pooled_levels,
            ),
            "leave_one_aircraft_out": holdouts,
            "mean_leave_one_aircraft_out_rmse_db": float(
                np.mean([row["rmse_db"] for row in holdouts])
            ),
            "maximum_leave_one_aircraft_out_rmse_db": maximum_holdout_rmse,
            "passes_transfer_gate": maximum_holdout_rmse <= TRANSFER_RMSE_GATE_DB,
        }

    return {
        "schema": "neaptide-directivity-transfer-v1",
        "aircraft_prefixes": aircraft,
        "bands_hz": expected_bands,
        "transfer_rmse_gate_db": TRANSFER_RMSE_GATE_DB,
        "metrics": metrics,
        "research_gate": {
            "all_metrics_transfer": all(
                result["passes_transfer_gate"] for result in metrics.values()
            ),
            "eligible_for_product_default": False,
            "blocking_reasons": [
                "overall and low-band directivity do not transfer across aircraft",
                "CC BY-NC 4.0 source requires distribution/licensing review",
                "NEAPTIDE does not contain a 5-inch FPV aircraft",
            ],
        },
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", action="append", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    analyses = [
        json.loads(path.read_text(encoding="utf-8"))
        for path in args.input
    ]
    result = compare_analyses(analyses)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(args.output.name + ".tmp")
    temporary.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary.replace(args.output)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
