#!/usr/bin/env python3
"""Independently verify D110 acoustic calibration admissibility."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any


BANDS = ("low", "mid", "high")
AIR_ROOM_IDS = ("air-booth", "air-lecture")
MINECRAFT_ENVIRONMENTS = ("closed", "partial", "open")
MAXIMUM_PTB_STONE_RELATIVE_DELTA = 0.50
TOLERANCE = 1.0e-12


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _number(value: Any, name: str) -> float:
    if (
        not isinstance(value, (int, float))
        or not math.isfinite(value)
        or value <= 0.0
    ):
        raise ValueError(f"D110 invalid {name}")
    return float(value)


def _bands(value: Any, name: str) -> dict[str, float]:
    if not isinstance(value, dict):
        raise ValueError(f"D110 invalid {name}")
    return {
        band: _number(value.get(band), f"{name}.{band}")
        for band in BANDS
    }


def _close(actual: Any, expected: float, name: str) -> None:
    if (
        not isinstance(actual, (int, float))
        or not math.isfinite(actual)
        or not math.isclose(
            actual,
            expected,
            rel_tol=0.0,
            abs_tol=TOLERANCE,
        )
    ):
        raise ValueError(f"D110 {name} changed")


def _same_bands(
    actual: Any,
    expected: dict[str, float],
    name: str,
) -> None:
    if not isinstance(actual, dict) or set(actual) != set(BANDS):
        raise ValueError(f"D110 {name} shape changed")
    for band in BANDS:
        _close(actual.get(band), expected[band], f"{name}.{band}")


def verify(
    report: dict[str, Any],
    report_sha256: str,
    air: dict[str, Any],
    air_sha256: str,
    shoebox: dict[str, Any],
    shoebox_sha256: str,
    ptb: dict[str, Any],
    ptb_sha256: str,
    matrix: dict[str, Any],
    matrix_sha256: str,
) -> dict[str, Any]:
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "valid-acoustic-calibration-admissibility"
        or report.get("bands") != list(BANDS)
    ):
        raise ValueError("D110 report shape or status is invalid")
    expected_hashes = {
        "air_rir": air_sha256,
        "air_shoebox": shoebox_sha256,
        "ptb_material": ptb_sha256,
        "minecraft_backend_matrix": matrix_sha256,
    }
    if report.get("source_report_sha256") != expected_hashes:
        raise ValueError("D110 source report binding changed")
    if (
        air.get("status") != "valid-reference-diagnostic"
        or air.get("source", {}).get("measured_rir") is not True
        or air.get("source", {}).get("license") != "MIT"
        or shoebox.get("status") != "valid-diagnostic"
        or shoebox.get("source_air_report_sha256") != air_sha256
        or ptb.get("status") != "valid-diagnostic"
        or ptb.get("raw_selection_csv_verified") is not True
        or matrix.get("status") != "valid-backend-environment-matrix"
    ):
        raise ValueError("D110 source evidence status changed")
    if any(
        value is not False
        for value in (
            air.get("gates", {}).get(
                "minecraft_release_calibrated"
            ),
            shoebox.get("release_calibrated"),
            ptb.get("release_calibrated"),
            matrix.get("release_calibrated"),
        )
    ):
        raise ValueError("D110 source release boundary changed")

    reproduction = air.get("published_rt60_reproduction", {})
    position_count = reproduction.get("position_count")
    positions_within = reproduction.get("positions_within_15_percent")
    every_within = air.get("gates", {}).get(
        "every_published_rt60_error_at_most_15_percent"
    )
    if (
        not isinstance(position_count, int)
        or not isinstance(positions_within, int)
        or position_count <= 0
        or not 0 <= positions_within <= position_count
        or every_within is not False
        or report.get("air_position_count") != position_count
        or report.get("air_positions_within_15_percent")
        != positions_within
        or report.get("air_every_position_within_15_percent")
        is not False
    ):
        raise ValueError("D110 AIR reproduction boundary changed")

    source_rooms = shoebox.get("rooms")
    observed_rooms = report.get("air_rooms")
    if (
        not isinstance(source_rooms, list)
        or not isinstance(observed_rooms, list)
        or [item.get("id") for item in source_rooms]
        != list(AIR_ROOM_IDS)
        or [item.get("id") for item in observed_rooms]
        != list(AIR_ROOM_IDS)
    ):
        raise ValueError("D110 AIR room order changed")
    ptb_stone = ptb.get("categories", {}).get("stone_dense")
    if not isinstance(ptb_stone, dict):
        raise ValueError("D110 PTB stone prior missing")
    ptb_current = _bands(
        ptb_stone.get("current_runtime_absorption"),
        "PTB current stone",
    )
    ptb_candidate = _bands(
        ptb_stone.get("runtime_candidate_absorption"),
        "PTB candidate stone",
    )
    all_rt60_ratios = []
    all_absorption_ratios = []
    for source, observed in zip(source_rooms, observed_rooms):
        measured = _bands(
            source.get("measured_rt60_s"),
            "AIR measured RT60",
        )
        effective = _bands(
            source.get("effective_eyring_absorption"),
            "AIR effective absorption",
        )
        current = _bands(
            source.get("current_stone_absorption"),
            "AIR current stone",
        )
        rt60_ratio = _bands(
            source.get("current_stone_rt60_ratio_vs_measured"),
            "AIR stone RT60 ratio",
        )
        if current != ptb_current:
            raise ValueError("D110 runtime stone controls detached")
        expected_absorption_ratio = {
            band: effective[band] / current[band]
            for band in BANDS
        }
        _same_bands(
            observed.get("measured_rt60_s"),
            measured,
            "measured RT60",
        )
        _same_bands(
            observed.get("effective_room_absorption"),
            effective,
            "effective room absorption",
        )
        _same_bands(
            observed.get("current_stone_absorption"),
            current,
            "current stone absorption",
        )
        _same_bands(
            observed.get("effective_to_stone_absorption_ratio"),
            expected_absorption_ratio,
            "effective-to-stone ratio",
        )
        _same_bands(
            observed.get("current_stone_rt60_to_measured_ratio"),
            rt60_ratio,
            "stone RT60 ratio",
        )
        all_rt60_ratios.extend(rt60_ratio.values())
        all_absorption_ratios.extend(
            expected_absorption_ratio.values()
        )

    relative_delta = {
        band: abs(ptb_candidate[band] - ptb_current[band])
        / ptb_current[band]
        for band in BANDS
    }
    maximum_delta = max(relative_delta.values())
    consistent_prior = (
        maximum_delta <= MAXIMUM_PTB_STONE_RELATIVE_DELTA
    )
    observed_stone = report.get("ptb_dense_stone")
    if not isinstance(observed_stone, dict):
        raise ValueError("D110 PTB result missing")
    _same_bands(
        observed_stone.get("current_runtime_absorption"),
        ptb_current,
        "PTB current absorption",
    )
    _same_bands(
        observed_stone.get("runtime_candidate_absorption"),
        ptb_candidate,
        "PTB candidate absorption",
    )
    _same_bands(
        observed_stone.get("candidate_relative_delta"),
        relative_delta,
        "PTB relative delta",
    )
    _close(
        observed_stone.get("maximum_relative_delta"),
        maximum_delta,
        "PTB maximum relative delta",
    )
    _close(
        observed_stone.get("maximum_prior_delta_gate"),
        MAXIMUM_PTB_STONE_RELATIVE_DELTA,
        "PTB prior gate",
    )
    if (
        observed_stone.get("consistent_as_material_prior")
        is not consistent_prior
    ):
        raise ValueError("D110 PTB prior flag changed")

    source_environments = matrix.get("environments")
    observed_environments = report.get(
        "minecraft_environment_rt60"
    )
    if (
        not isinstance(source_environments, list)
        or not isinstance(observed_environments, list)
        or [item.get("name") for item in source_environments]
        != list(MINECRAFT_ENVIRONMENTS)
        or [item.get("name") for item in observed_environments]
        != list(MINECRAFT_ENVIRONMENTS)
    ):
        raise ValueError("D110 Minecraft environment order changed")
    computed_rt60 = []
    for source, observed in zip(
        source_environments, observed_environments
    ):
        rt60 = _bands(
            source.get("rt60_seconds"),
            "Minecraft RT60",
        )
        _same_bands(
            observed.get("rt60_seconds"),
            rt60,
            "Minecraft observed RT60",
        )
        wet = _number(source.get("wet_gain"), "Minecraft wet gain")
        _close(observed.get("wet_gain"), wet, "Minecraft wet gain")
        computed_rt60.append(rt60)
    monotonic = all(
        computed_rt60[index][band]
        > computed_rt60[index + 1][band]
        for index in range(len(computed_rt60) - 1)
        for band in BANDS
    )
    if not monotonic:
        raise ValueError("D110 Minecraft response lost monotonicity")

    summary = report.get("summary", {})
    _close(
        summary.get(
            "minimum_current_stone_rt60_to_air_measured_ratio"
        ),
        min(all_rt60_ratios),
        "minimum stone RT60 ratio",
    )
    _close(
        summary.get(
            "maximum_air_effective_to_stone_absorption_ratio"
        ),
        max(all_absorption_ratios),
        "maximum effective absorption ratio",
    )
    if (
        summary.get(
            "minecraft_environment_response_monotonic"
        )
        is not True
    ):
        raise ValueError("D110 monotonic summary flag changed")

    expected_eligibility = {
        "air_measured_rir_for_scene_level_rt60_targets": True,
        "air_room_effective_absorption_for_block_override": False,
        "ptb_dense_stone_for_material_prior": consistent_prior,
        "ptb_dense_stone_for_release_override": False,
        "minecraft_matrix_for_relative_environment_validation": True,
        "cross_dataset_absolute_release_calibration": False,
    }
    if report.get("eligibility") != expected_eligibility:
        raise ValueError("D110 eligibility matrix changed")
    decision = report.get("decision", {})
    if (
        decision.get("replace_runtime_stone_absorption") is not False
        or decision.get("production_change_required") is not False
        or decision.get("next_parameter_level")
        != "scene-composition-and-interior-treatment"
    ):
        raise ValueError("D110 production decision changed")
    for key in (
        "physical_endpoint_opened",
        "captures_audio",
        "release_calibrated",
    ):
        if report.get(key) is not False:
            raise ValueError(f"D110 {key} must be False")

    return {
        "schema_version": 1,
        "status": "valid-acoustic-calibration-admissibility-verification",
        "source_report_sha256": report_sha256,
        "source_evidence_sha256": expected_hashes,
        "minimum_current_stone_rt60_to_air_measured_ratio": min(
            all_rt60_ratios
        ),
        "maximum_air_effective_to_stone_absorption_ratio": max(
            all_absorption_ratios
        ),
        "maximum_ptb_stone_relative_delta": maximum_delta,
        "gates": {
            "source_reports_hash_bound": True,
            "air_room_metrics_recomputed": True,
            "ptb_material_prior_recomputed": True,
            "minecraft_relative_response_recomputed": True,
            "room_material_semantics_separated": True,
            "runtime_stone_override_rejected": True,
            "physical_playback_or_capture_opened": False,
            "release_calibrated": False,
        },
        "captures_audio": False,
        "release_calibrated": False,
        "claim_boundary": (
            "Independent numeric and policy verification of D110 source "
            "reports. This preserves the distinction between whole-room "
            "effective absorption and material-specimen absorption; it "
            "does not perform an endpoint or perceptual calibration."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--air-report", type=Path, required=True)
    parser.add_argument("--shoebox-report", type=Path, required=True)
    parser.add_argument("--ptb-report", type=Path, required=True)
    parser.add_argument("--matrix-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    paths = (
        args.report,
        args.air_report,
        args.shoebox_report,
        args.ptb_report,
        args.matrix_report,
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
        documents[4],
        _sha256(payloads[4]),
    )
    rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(rendered, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
