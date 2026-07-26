#!/usr/bin/env python3
"""Verify D111 constrained material inverse and spatial voxel holdouts."""

from __future__ import annotations

import argparse
import functools
import hashlib
import json
import math
from pathlib import Path
from typing import Any

import numpy as np


BANDS = ("low", "mid", "high")
MATERIAL_ORDER = ("stone", "wood", "wool", "glass")
ROOM_ORDER = ("air-booth", "air-lecture")
GRID_DENOMINATOR = 200
LAYOUT_COUNT = 8
RAY_COUNT = 256
MAXIMUM_BOUNCES = 12
INVERSE_PASS = 0.10
VOXEL_PASS = 0.15
LAYOUT_SPAN_PASS = 0.20
METRIC_TOLERANCE = 5.0e-12


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _positive(value: Any, name: str) -> float:
    if (
        not isinstance(value, (int, float))
        or not math.isfinite(value)
        or value <= 0.0
    ):
        raise ValueError(f"D111 invalid {name}")
    return float(value)


def _bands(value: Any, name: str) -> np.ndarray:
    if not isinstance(value, dict) or set(value) != set(BANDS):
        raise ValueError(f"D111 {name} shape changed")
    return np.array(
        [_positive(value[band], f"{name}.{band}") for band in BANDS],
        dtype=np.float64,
    )


def _close(actual: Any, expected: float, name: str) -> None:
    if (
        not isinstance(actual, (int, float))
        or not math.isfinite(actual)
        or not math.isclose(
            actual,
            expected,
            rel_tol=0.0,
            abs_tol=METRIC_TOLERANCE,
        )
    ):
        raise ValueError(f"D111 {name} changed")


def _same_bands(actual: Any, expected: np.ndarray, name: str) -> None:
    observed = _bands(actual, name)
    if not np.allclose(
        observed,
        expected,
        rtol=0.0,
        atol=METRIC_TOLERANCE,
    ):
        raise ValueError(f"D111 {name} changed")


@functools.lru_cache(maxsize=16)
def _fit_cached(
    material_tuple: tuple[float, ...],
    target_tuple: tuple[float, ...],
) -> tuple[tuple[float, ...], tuple[float, ...], tuple[float, ...], int]:
    materials = np.array(material_tuple, dtype=np.float64).reshape(4, 3)
    target = np.array(target_tuple, dtype=np.float64)
    log_retention = np.log1p(-materials)
    best_key = (math.inf, math.inf)
    best_weights = None
    best_predicted = None
    count = 0
    denominator = GRID_DENOMINATOR
    for stone in range(denominator + 1):
        for wood in range(denominator - stone + 1):
            wool = np.arange(
                denominator - stone - wood + 1,
                dtype=np.float64,
            )
            glass = denominator - stone - wood - wool
            weights = np.column_stack(
                (
                    np.full_like(wool, stone),
                    np.full_like(wool, wood),
                    wool,
                    glass,
                )
            ) / denominator
            predicted = 1.0 - np.exp(weights @ log_retention)
            relative = np.abs(predicted - target) / target
            maximum = np.max(relative, axis=1)
            squared = np.sum(relative * relative, axis=1)
            order = np.lexsort((squared, maximum))
            index = int(order[0])
            key = (float(maximum[index]), float(squared[index]))
            count += len(wool)
            if key[0] < best_key[0] - 1.0e-15 or (
                abs(key[0] - best_key[0]) <= 1.0e-15
                and key[1] < best_key[1] - 1.0e-15
            ):
                best_key = key
                best_weights = weights[index]
                best_predicted = predicted[index]
    if best_weights is None or best_predicted is None:
        raise ValueError("D111 inverse grid is empty")
    relative = np.abs(best_predicted - target) / target
    return (
        tuple(float(value) for value in best_weights),
        tuple(float(value) for value in best_predicted),
        tuple(float(value) for value in relative),
        count,
    )


def verify(
    report: dict[str, Any],
    report_sha256: str,
    d110: dict[str, Any],
    d110_sha256: str,
    shoebox: dict[str, Any],
    shoebox_sha256: str,
    ptb: dict[str, Any],
    ptb_sha256: str,
) -> dict[str, Any]:
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "valid-scene-composition-inverse"
        or report.get("source_d110_report_sha256") != d110_sha256
        or report.get("source_air_shoebox_sha256") != shoebox_sha256
        or report.get("source_ptb_material_sha256") != ptb_sha256
    ):
        raise ValueError("D111 report shape or source binding changed")
    if (
        d110.get("status")
        != "valid-acoustic-calibration-admissibility"
        or d110.get("decision", {}).get("next_parameter_level")
        != "scene-composition-and-interior-treatment"
        or d110.get("decision", {}).get(
            "replace_runtime_stone_absorption"
        )
        is not False
        or shoebox.get("status") != "valid-diagnostic"
        or ptb.get("status") != "valid-diagnostic"
    ):
        raise ValueError("D111 source policy or status changed")
    if (
        report.get("material_order") != list(MATERIAL_ORDER)
        or report.get("grid_denominator") != GRID_DENOMINATOR
        or report.get("layouts_per_room") != LAYOUT_COUNT
        or report.get("ray_count") != RAY_COUNT
        or report.get("maximum_bounces") != MAXIMUM_BOUNCES
        or report.get("inverse_model")
        != (
            "simplex-grid-minimax-relative-absorption-error-"
            "mean-log-retention"
        )
    ):
        raise ValueError("D111 inverse contract changed")

    categories = ptb.get("categories", {})
    category_order = (
        "stone_dense",
        "wood_solid_panel",
        "porous_wool",
        "glass_window",
    )
    expected_materials = np.vstack(
        [
            _bands(
                categories.get(category, {}).get(
                    "runtime_candidate_absorption"
                ),
                f"PTB {category}",
            )
            for category in category_order
        ]
    )
    observed_materials = report.get("material_absorption")
    if (
        not isinstance(observed_materials, list)
        or len(observed_materials) != 4
    ):
        raise ValueError("D111 material manifest changed")
    for index, item in enumerate(observed_materials):
        _same_bands(
            item,
            expected_materials[index],
            f"material {index}",
        )

    source_rooms = shoebox.get("rooms")
    rooms = report.get("rooms")
    if (
        not isinstance(source_rooms, list)
        or not isinstance(rooms, list)
        or [item.get("id") for item in source_rooms]
        != list(ROOM_ORDER)
        or [item.get("id") for item in rooms] != list(ROOM_ORDER)
    ):
        raise ValueError("D111 room order changed")

    room_results = []
    candidate_count = None
    for source, room in zip(source_rooms, rooms):
        dimensions = source.get("voxel_interior_cells")
        if (
            not isinstance(dimensions, dict)
            or room.get("voxel_interior_cells") != dimensions
        ):
            raise ValueError("D111 voxel dimensions detached")
        measured = _bands(
            source.get("measured_rt60_s"),
            "source measured RT60",
        )
        target = _bands(
            source.get("effective_eyring_absorption"),
            "source effective absorption",
        )
        _same_bands(room.get("measured_rt60_s"), measured, "measured RT60")
        _same_bands(
            room.get("target_effective_absorption"),
            target,
            "target absorption",
        )
        fit = _fit_cached(
            tuple(expected_materials.ravel()),
            tuple(target),
        )
        weights = np.array(fit[0])
        predicted = np.array(fit[1])
        relative = np.array(fit[2])
        candidate_count = fit[3]
        observed_weights = room.get("selected_weights")
        if (
            not isinstance(observed_weights, list)
            or len(observed_weights) != 4
            or not np.allclose(
                observed_weights,
                weights,
                rtol=0.0,
                atol=METRIC_TOLERANCE,
            )
        ):
            raise ValueError("D111 selected simplex weights changed")
        _same_bands(
            room.get("predicted_mean_log_absorption"),
            predicted,
            "predicted absorption",
        )
        _same_bands(
            room.get("inverse_relative_absorption_error"),
            relative,
            "inverse relative error",
        )
        _close(
            room.get("inverse_maximum_relative_error"),
            float(np.max(relative)),
            "inverse maximum relative error",
        )

        length = dimensions.get("length")
        width = dimensions.get("width")
        height = dimensions.get("height")
        if not all(
            isinstance(value, int) and value > 0
            for value in (length, width, height)
        ):
            raise ValueError("D111 voxel dimensions are invalid")
        surface_cells = (
            (length + 2) * (width + 2) * (height + 2)
            - length * width * height
        )
        layouts = room.get("layouts")
        if (
            not isinstance(layouts, list)
            or [item.get("layout") for item in layouts]
            != list(range(LAYOUT_COUNT))
        ):
            raise ValueError("D111 layout order changed")
        layout_rt60 = []
        for layout in layouts:
            counts = layout.get("surface_cell_counts")
            if (
                not isinstance(counts, list)
                or len(counts) != 4
                or any(
                    not isinstance(value, int) or value < 0
                    for value in counts
                )
                or sum(counts) != surface_cells
                or any(
                    abs(count / surface_cells - weight)
                    > 1.0 / surface_cells + 1.0e-12
                    for count, weight in zip(counts, weights)
                )
            ):
                raise ValueError("D111 surface-cell mixture changed")
            rt60 = _bands(layout.get("rt60_s"), "layout RT60")
            expected_error = np.abs(rt60 - measured) / measured
            _same_bands(
                layout.get("relative_error"),
                expected_error,
                "layout relative error",
            )
            if (
                layout.get("surface_hits")
                != RAY_COUNT * MAXIMUM_BOUNCES
                or layout.get("escaped_rays") != 0
                or layout.get("truncated_legs") != 0
            ):
                raise ValueError("D111 voxel probe completeness changed")
            layout_rt60.append(rt60)
        values = np.vstack(layout_rt60)
        median = np.median(values, axis=0)
        minimum = np.min(values, axis=0)
        maximum = np.max(values, axis=0)
        median_error = np.abs(median - measured) / measured
        span_ratio = (maximum - minimum) / median
        _same_bands(room.get("voxel_median_rt60_s"), median, "median RT60")
        _same_bands(room.get("voxel_minimum_rt60_s"), minimum, "minimum RT60")
        _same_bands(room.get("voxel_maximum_rt60_s"), maximum, "maximum RT60")
        _same_bands(
            room.get("voxel_median_relative_error"),
            median_error,
            "median relative error",
        )
        _close(
            room.get("voxel_maximum_median_relative_error"),
            float(np.max(median_error)),
            "maximum median relative error",
        )
        room_results.append(
            {
                "id": room["id"],
                "inverse_maximum_relative_error": float(
                    np.max(relative)
                ),
                "voxel_maximum_median_relative_error": float(
                    np.max(median_error)
                ),
                "maximum_layout_span_to_median": float(
                    np.max(span_ratio)
                ),
            }
        )
    if report.get("candidates_per_room") != candidate_count:
        raise ValueError("D111 candidate count changed")

    booth, lecture = room_results
    lecture_pass = (
        lecture["inverse_maximum_relative_error"] <= INVERSE_PASS
        and lecture["voxel_maximum_median_relative_error"] <= VOXEL_PASS
        and lecture["maximum_layout_span_to_median"]
        <= LAYOUT_SPAN_PASS
    )
    booth_pass = (
        booth["inverse_maximum_relative_error"] <= INVERSE_PASS
        and booth["voxel_maximum_median_relative_error"] <= VOXEL_PASS
        and booth["maximum_layout_span_to_median"] <= LAYOUT_SPAN_PASS
    )
    if not lecture_pass or booth_pass:
        raise ValueError("D111 room feasibility classification changed")
    for key in (
        "production_change_required",
        "physical_endpoint_opened",
        "captures_audio",
        "release_calibrated",
    ):
        if report.get(key) is not False:
            raise ValueError(f"D111 {key} must be False")

    return {
        "schema_version": 1,
        "status": "valid-air-scene-composition-inverse-verification",
        "source_report_sha256": report_sha256,
        "source_evidence_sha256": {
            "d110": d110_sha256,
            "air_shoebox": shoebox_sha256,
            "ptb_material": ptb_sha256,
        },
        "candidates_per_room": candidate_count,
        "rooms": room_results,
        "gates": {
            "global_simplex_minima_recomputed": True,
            "spatial_voxel_layouts_complete": True,
            "layout_span_below_20_percent": all(
                item["maximum_layout_span_to_median"]
                <= LAYOUT_SPAN_PASS
                for item in room_results
            ),
            "lecture_inverse_and_voxel_holdout_pass": lecture_pass,
            "booth_four_material_basis_insufficient": not booth_pass,
            "both_room_profile_rejected": True,
            "production_change_required": False,
            "physical_playback_or_capture_opened": False,
            "release_calibrated": False,
        },
        "captures_audio": False,
        "release_calibrated": False,
        "claim_boundary": (
            "Independently recomputes the complete 1/200 simplex grid and "
            "all reported voxel-layout statistics. A feasible lecture "
            "mixture is an inverse explanation, not an identification of "
            "AIR furnishings or a production/release preset."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--d110-report", type=Path, required=True)
    parser.add_argument("--shoebox-report", type=Path, required=True)
    parser.add_argument("--ptb-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    paths = (
        args.report,
        args.d110_report,
        args.shoebox_report,
        args.ptb_report,
    )
    payloads = [path.read_bytes() for path in paths]
    documents = [json.loads(payload) for payload in payloads]
    result = verify(
        documents[0],
        _sha256(payloads[0]),
        documents[1],
        _sha256(payloads[1]),
        documents[2],
        _sha256(payloads[2]),
        documents[3],
        _sha256(payloads[3]),
    )
    rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(rendered, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
