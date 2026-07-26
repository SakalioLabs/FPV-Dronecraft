#!/usr/bin/env python3
"""D110 separate room-level RIR targets from block-material calibration."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


BANDS = ("low", "mid", "high")
AIR_ROOM_IDS = ("air-booth", "air-lecture")
MINECRAFT_ENVIRONMENTS = ("closed", "partial", "open")
MAXIMUM_PTB_STONE_RELATIVE_DELTA = 0.50


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _bands(value: dict[str, Any]) -> dict[str, float]:
    result = {}
    for band in BANDS:
        number = value.get(band)
        if not isinstance(number, (int, float)) or number <= 0.0:
            raise ValueError(f"invalid {band} band")
        result[band] = float(number)
    return result


def analyze(
    air: dict[str, Any],
    air_bytes: bytes,
    shoebox: dict[str, Any],
    shoebox_bytes: bytes,
    ptb: dict[str, Any],
    ptb_bytes: bytes,
    matrix: dict[str, Any],
    matrix_bytes: bytes,
) -> dict[str, Any]:
    if (
        air.get("schema_version") != 1
        or air.get("status") != "valid-reference-diagnostic"
        or air.get("source", {}).get("measured_rir") is not True
        or air.get("source", {}).get("license") != "MIT"
        or shoebox.get("status") != "valid-diagnostic"
        or shoebox.get("source_air_report_sha256")
        != _sha256(air_bytes)
        or ptb.get("status") != "valid-diagnostic"
        or ptb.get("raw_selection_csv_verified") is not True
        or matrix.get("status") != "valid-backend-environment-matrix"
    ):
        raise ValueError("D110 source evidence status changed")
    if (
        air.get("gates", {}).get("minecraft_release_calibrated")
        is not False
        or shoebox.get("release_calibrated") is not False
        or ptb.get("release_calibrated") is not False
        or matrix.get("release_calibrated") is not False
    ):
        raise ValueError("D110 source release boundary changed")

    rooms = shoebox.get("rooms")
    if (
        not isinstance(rooms, list)
        or [item.get("id") for item in rooms] != list(AIR_ROOM_IDS)
    ):
        raise ValueError("D110 AIR room controls changed")
    stone = ptb.get("categories", {}).get("stone_dense")
    if not isinstance(stone, dict):
        raise ValueError("D110 PTB stone prior missing")
    ptb_current = _bands(stone.get("current_runtime_absorption", {}))
    ptb_candidate = _bands(
        stone.get("runtime_candidate_absorption", {})
    )

    room_rows = []
    for room in rooms:
        measured = _bands(room.get("measured_rt60_s", {}))
        effective = _bands(room.get("effective_eyring_absorption", {}))
        current_stone = _bands(room.get("current_stone_absorption", {}))
        ratios = _bands(
            room.get("current_stone_rt60_ratio_vs_measured", {})
        )
        if current_stone != ptb_current:
            raise ValueError("D110 runtime stone controls detached")
        room_rows.append(
            {
                "id": room["id"],
                "measured_rt60_s": measured,
                "effective_room_absorption": effective,
                "current_stone_absorption": current_stone,
                "effective_to_stone_absorption_ratio": {
                    band: effective[band] / current_stone[band]
                    for band in BANDS
                },
                "current_stone_rt60_to_measured_ratio": ratios,
            }
        )

    ptb_relative_delta = {
        band: abs(ptb_candidate[band] - ptb_current[band])
        / ptb_current[band]
        for band in BANDS
    }
    environments = matrix.get("environments")
    if (
        not isinstance(environments, list)
        or [item.get("name") for item in environments]
        != list(MINECRAFT_ENVIRONMENTS)
    ):
        raise ValueError("D110 Minecraft environment order changed")
    minecraft_rt60 = [
        {
            "name": item["name"],
            "rt60_seconds": _bands(item.get("rt60_seconds", {})),
            "wet_gain": float(item.get("wet_gain")),
        }
        for item in environments
    ]
    monotonic = all(
        minecraft_rt60[index]["rt60_seconds"][band]
        > minecraft_rt60[index + 1]["rt60_seconds"][band]
        for index in range(len(minecraft_rt60) - 1)
        for band in BANDS
    )
    if not monotonic:
        raise ValueError("D110 Minecraft relative response is not monotonic")

    minimum_stone_rt60_ratio = min(
        value
        for room in room_rows
        for value in room[
            "current_stone_rt60_to_measured_ratio"
        ].values()
    )
    maximum_effective_to_stone = max(
        value
        for room in room_rows
        for value in room[
            "effective_to_stone_absorption_ratio"
        ].values()
    )
    maximum_ptb_delta = max(ptb_relative_delta.values())
    ptb_prior_consistent = (
        maximum_ptb_delta <= MAXIMUM_PTB_STONE_RELATIVE_DELTA
    )
    return {
        "schema_version": 1,
        "status": "valid-acoustic-calibration-admissibility",
        "source_report_sha256": {
            "air_rir": _sha256(air_bytes),
            "air_shoebox": _sha256(shoebox_bytes),
            "ptb_material": _sha256(ptb_bytes),
            "minecraft_backend_matrix": _sha256(matrix_bytes),
        },
        "bands": list(BANDS),
        "air_position_count": air.get(
            "published_rt60_reproduction", {}
        ).get("position_count"),
        "air_positions_within_15_percent": air.get(
            "published_rt60_reproduction", {}
        ).get("positions_within_15_percent"),
        "air_every_position_within_15_percent": air.get(
            "gates", {}
        ).get("every_published_rt60_error_at_most_15_percent"),
        "air_rooms": room_rows,
        "ptb_dense_stone": {
            "current_runtime_absorption": ptb_current,
            "runtime_candidate_absorption": ptb_candidate,
            "candidate_relative_delta": ptb_relative_delta,
            "maximum_relative_delta": maximum_ptb_delta,
            "maximum_prior_delta_gate": (
                MAXIMUM_PTB_STONE_RELATIVE_DELTA
            ),
            "consistent_as_material_prior": ptb_prior_consistent,
        },
        "minecraft_environment_rt60": minecraft_rt60,
        "summary": {
            "minimum_current_stone_rt60_to_air_measured_ratio": (
                minimum_stone_rt60_ratio
            ),
            "maximum_air_effective_to_stone_absorption_ratio": (
                maximum_effective_to_stone
            ),
            "minecraft_environment_response_monotonic": monotonic,
        },
        "eligibility": {
            "air_measured_rir_for_scene_level_rt60_targets": True,
            "air_room_effective_absorption_for_block_override": False,
            "ptb_dense_stone_for_material_prior": ptb_prior_consistent,
            "ptb_dense_stone_for_release_override": False,
            "minecraft_matrix_for_relative_environment_validation": True,
            "cross_dataset_absolute_release_calibration": False,
        },
        "decision": {
            "replace_runtime_stone_absorption": False,
            "production_change_required": False,
            "next_parameter_level": (
                "scene-composition-and-interior-treatment"
            ),
            "reason": (
                "AIR effective absorption is a furnished whole-room "
                "quantity while PTB stone is a material-specimen prior. "
                "The current stone bands remain close enough to the PTB "
                "dense-stone candidate for research use, so AIR room "
                "values must not be assigned to a Minecraft stone block."
            ),
        },
        "physical_endpoint_opened": False,
        "captures_audio": False,
        "release_calibrated": False,
        "claim_boundary": (
            "Hash-bound admissibility audit of measured AIR room RIR "
            "statistics, AIR-matched voxel diagnostics, PTB specimen "
            "priors, and Minecraft software-render controls. It does not "
            "fit a release profile, identify AIR furnishings, record an "
            "endpoint, or prove perceptual realism."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--air-report", type=Path, required=True)
    parser.add_argument("--shoebox-report", type=Path, required=True)
    parser.add_argument("--ptb-report", type=Path, required=True)
    parser.add_argument("--matrix-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    payloads = [
        path.read_bytes()
        for path in (
            args.air_report,
            args.shoebox_report,
            args.ptb_report,
            args.matrix_report,
        )
    ]
    result = analyze(
        json.loads(payloads[0]),
        payloads[0],
        json.loads(payloads[1]),
        payloads[1],
        json.loads(payloads[2]),
        payloads[2],
        json.loads(payloads[3]),
        payloads[3],
    )
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
