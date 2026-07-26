#!/usr/bin/env python3
"""Bind the Java AIR shoebox/voxel diagnostic to the measured RIR report."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any


EXPECTED_ROOMS = ("air-booth", "air-lecture")
BANDS = ("low", "mid", "high")


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def validate(
    air_report_path: Path,
    voxel_report_path: Path,
) -> dict[str, Any]:
    air_report = json.loads(air_report_path.read_text(encoding="utf-8"))
    voxel_report = json.loads(voxel_report_path.read_text(encoding="utf-8"))
    if air_report.get("schema_version") != 1:
        raise ValueError("AIR report schema must be 1")
    if air_report.get("status") != "valid-reference-diagnostic":
        raise ValueError("AIR report status is invalid")
    if voxel_report.get("schema_version") != 1:
        raise ValueError("voxel report schema must be 1")
    if voxel_report.get("status") != "valid-diagnostic":
        raise ValueError("voxel report status is invalid")

    air_hash = file_sha256(air_report_path)
    if voxel_report.get("source_air_report_sha256") != air_hash:
        raise ValueError("voxel report is not bound to the AIR report SHA-256")
    if voxel_report.get("release_calibrated") is not False:
        raise ValueError("voxel report cannot claim release calibration")

    gates = voxel_report.get("gates")
    if not isinstance(gates, dict):
        raise ValueError("voxel report gates are missing")
    required_true = (
        "probe_mfp_within_10_percent",
        "probe_rt60_within_10_percent",
        "current_stone_at_least_2x_measured",
    )
    if not all(gates.get(name) is True for name in required_true):
        raise ValueError("required shoebox/voxel gate failed")
    if gates.get("minecraft_release_calibrated") is not False:
        raise ValueError("Minecraft calibration gate must remain false")
    if float(gates["maximum_probe_mfp_relative_error"]) > 0.10:
        raise ValueError("probe MFP error exceeds 10%")
    if float(gates["maximum_probe_rt60_relative_error"]) > 0.10:
        raise ValueError("probe RT60 error exceeds 10%")
    if float(gates["minimum_current_stone_rt60_ratio"]) < 2.0:
        raise ValueError("current stone mismatch is below pinned boundary")

    air_rooms = {
        room["room"]: room
        for room in air_report.get("room_summary", [])
    }
    voxel_rooms = {
        room["id"]: room
        for room in voxel_report.get("rooms", [])
    }
    if tuple(voxel_rooms) != EXPECTED_ROOMS:
        raise ValueError("voxel report room identity/order changed")
    for voxel_id in EXPECTED_ROOMS:
        air_id = voxel_id.removeprefix("air-")
        if air_id not in air_rooms:
            raise ValueError(f"AIR report is missing room {air_id}")
        measured = voxel_rooms[voxel_id]["measured_rt60_s"]
        source = air_rooms[air_id]["mean_band_rt60_seconds"]
        for band in BANDS:
            if not math.isclose(
                float(measured[band]),
                float(source[band]),
                rel_tol=0.0,
                abs_tol=1.0e-15,
            ):
                raise ValueError(
                    f"{voxel_id} {band} RT60 is detached from AIR report"
                )
    return {
        "schema": 1,
        "status": "valid",
        "air_report_sha256": air_hash,
        "voxel_report_sha256": file_sha256(voxel_report_path),
        "rooms": len(EXPECTED_ROOMS),
        "maximum_probe_mfp_relative_error": gates[
            "maximum_probe_mfp_relative_error"
        ],
        "maximum_probe_rt60_relative_error": gates[
            "maximum_probe_rt60_relative_error"
        ],
        "minimum_current_stone_rt60_ratio": gates[
            "minimum_current_stone_rt60_ratio"
        ],
        "minecraft_release_calibrated": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--air-report", required=True, type=Path)
    parser.add_argument("--voxel-report", required=True, type=Path)
    args = parser.parse_args()
    print(
        json.dumps(
            validate(args.air_report, args.voxel_report),
            indent=2,
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
