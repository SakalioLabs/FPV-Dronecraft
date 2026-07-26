#!/usr/bin/env python3
"""Verify Java CPU DDA expected-results sidecars independently in Python."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path

from verify_dda_production_bundle import (
    BundleError,
    MATERIALS,
    Reader,
    verify_file,
)


MAGIC = b"MCFPREF1"
SCHEMA = 1
FLAG_REACHED = 1
FLAG_STOPPED = 2
FLAG_TRUNCATED = 4


def pack_cell(x: int, y: int, z: int) -> int:
    return (
        ((x & 0x3FFFFFF) << 38)
        | ((z & 0x3FFFFFF) << 12)
        | (y & 0xFFF)
    )


def _initial_t(start: float, delta: float, cell: int, step: int) -> float:
    if step == 0:
        return math.inf
    boundary = cell + 1.0 if step > 0 else float(cell)
    return (boundary - start) / delta


def trace(ray: tuple, cells: dict[int, tuple[int, float]]) -> dict:
    coordinates, maximum_cells = ray
    start = coordinates[:3]
    end = coordinates[3:]
    delta = tuple(end[index] - start[index] for index in range(3))
    total_length = math.sqrt(sum(value * value for value in delta))
    segments = []
    loss = [0.0, 0.0, 0.0]
    material_cells = 0
    first_material = None

    def visit(x: int, y: int, z: int, length: float) -> None:
        nonlocal material_cells, first_material
        packed = pack_cell(x, y, z)
        material_id, fill = cells.get(packed, (0, 0.0))
        transmission = MATERIALS[material_id][1]
        effective_length = length * fill
        for band in range(3):
            loss[band] += transmission[band] * effective_length
        if material_id != 0 and effective_length > 0.0:
            material_cells += 1
            if first_material is None:
                first_material = packed
        segments.append((packed, length, material_id, fill))

    if total_length <= 1.0e-12:
        visit(
            math.floor(start[0]),
            math.floor(start[1]),
            math.floor(start[2]),
            0.0,
        )
        reached = True
        stopped = False
    else:
        current = [math.floor(value) for value in start]
        target = [math.floor(value) for value in end]
        step = [(value > 0.0) - (value < 0.0) for value in delta]
        t_delta = [
            math.inf if axis_step == 0 else abs(1.0 / delta[index])
            for index, axis_step in enumerate(step)
        ]
        t_max = [
            _initial_t(start[index], delta[index], current[index], step[index])
            for index in range(3)
        ]
        entry_t = 0.0
        reached = False
        stopped = False
        for _ in range(maximum_cells):
            exit_t = min(1.0, *t_max)
            segment_length = max(0.0, exit_t - entry_t) * total_length
            visit(*current, segment_length)
            if current == target:
                reached = True
                break
            crossing = min(t_max)
            epsilon = max(1.0e-12, abs(crossing) * 1.0e-12)
            for axis in range(3):
                if abs(t_max[axis] - crossing) <= epsilon:
                    current[axis] += step[axis]
                    t_max[axis] += t_delta[axis]
            entry_t = min(1.0, crossing)
    gain = tuple(10.0 ** (-value / 10.0) for value in loss)
    return {
        "segments": segments,
        "visited": len(segments),
        "material_cells": material_cells,
        "reached": reached,
        "stopped": stopped,
        "truncated": not reached and not stopped,
        "loss": tuple(loss),
        "gain": gain,
        "first_material": first_material,
    }


def _close(first: float, second: float) -> bool:
    return math.isclose(first, second, rel_tol=1.0e-12, abs_tol=1.0e-12)


def verify_expected_results(bundle_path: Path, sidecar_path: Path) -> dict:
    bundle = verify_file(
        bundle_path,
        require_complete=True,
        include_details=True,
    )
    cell_map = {
        cell["packed"]: (cell["material_id"], cell["fill_fraction"])
        for cell in bundle["_cell_records"]
    }
    payload = sidecar_path.read_bytes()
    reader = Reader(payload)
    if reader.take(len(MAGIC)) != MAGIC:
        raise BundleError("invalid expected-results magic")
    schema = reader.i32()
    if schema != SCHEMA:
        raise BundleError(f"unsupported expected-results schema {schema}")
    if reader.take(32).hex() != bundle["file_sha256"]:
        raise BundleError("input bundle hash mismatch")
    if reader.take(32).hex() != bundle["snapshot_sha256"]:
        raise BundleError("snapshot hash mismatch")
    ray_count = reader.i32()
    if ray_count != bundle["rays"]:
        raise BundleError("ray count does not match bundle")
    total_segments = 0
    for expected_ray_id, ray in enumerate(bundle["_ray_records"]):
        ray_id = reader.i32()
        segment_count = reader.i32()
        visited = reader.i32()
        material_cells = reader.i32()
        flags = reader.take(1)[0]
        if flags & ~(FLAG_REACHED | FLAG_STOPPED | FLAG_TRUNCATED):
            raise BundleError("unknown expected-result flags")
        loss = tuple(reader.f64() for _ in range(3))
        gain = tuple(reader.f64() for _ in range(3))
        first_flag = reader.take(1)
        if first_flag not in (b"\x00", b"\x01"):
            raise BundleError("first-material flag is not boolean")
        first_material = reader.u64() if first_flag == b"\x01" else None
        stored_segments = []
        if segment_count < 1 or segment_count > ray[1]:
            raise BundleError("segment count is out of range")
        for _ in range(segment_count):
            packed = reader.u64()
            length = reader.f64()
            material_id = reader.i32()
            fill = reader.f64()
            if not math.isfinite(length) or length < 0.0:
                raise BundleError("segment length is out of range")
            if material_id < 0 or material_id >= len(MATERIALS):
                raise BundleError("material id is out of range")
            if not math.isfinite(fill) or not 0.0 <= fill <= 1.0:
                raise BundleError("fill fraction is out of range")
            stored_segments.append((packed, length, material_id, fill))
        actual = trace(ray, cell_map)
        actual_flags = (
            (FLAG_REACHED if actual["reached"] else 0)
            | (FLAG_STOPPED if actual["stopped"] else 0)
            | (FLAG_TRUNCATED if actual["truncated"] else 0)
        )
        if (
            ray_id != expected_ray_id
            or visited != actual["visited"]
            or visited != segment_count
            or material_cells != actual["material_cells"]
            or flags != actual_flags
            or first_material != actual["first_material"]
        ):
            raise BundleError(f"expected result mismatch for ray {ray_id}")
        if not all(_close(loss[index], actual["loss"][index]) for index in range(3)):
            raise BundleError(f"loss mismatch for ray {ray_id}")
        if not all(_close(gain[index], actual["gain"][index]) for index in range(3)):
            raise BundleError(f"gain mismatch for ray {ray_id}")
        if len(stored_segments) != len(actual["segments"]):
            raise BundleError(f"segment mismatch for ray {ray_id}")
        for stored, computed in zip(stored_segments, actual["segments"]):
            if (
                stored[0] != computed[0]
                or stored[2] != computed[2]
                or stored[3] != computed[3]
                or not _close(stored[1], computed[1])
            ):
                raise BundleError(f"segment mismatch for ray {ray_id}")
        total_segments += segment_count
    if reader.offset != len(payload):
        raise BundleError("trailing bytes after expected results")
    return {
        "status": "valid",
        "schema": schema,
        "bundle_sha256": bundle["file_sha256"],
        "snapshot_sha256": bundle["snapshot_sha256"],
        "rays": ray_count,
        "segments": total_segments,
        "expected_results_sha256": hashlib.sha256(payload).hexdigest(),
        "bytes": len(payload),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--expected-results", type=Path, required=True)
    arguments = parser.parse_args()
    try:
        summary = verify_expected_results(
            arguments.bundle,
            arguments.expected_results,
        )
    except (OSError, BundleError) as error:
        print(json.dumps({"status": "invalid", "error": str(error)}))
        return 1
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
