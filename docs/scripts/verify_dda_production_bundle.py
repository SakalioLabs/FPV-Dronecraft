#!/usr/bin/env python3
"""Independent schema-v1 verifier for Java production DDA bundles."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import struct
from pathlib import Path


BUNDLE_MAGIC = b"MCFPDDA1"
BUNDLE_SCHEMA = 1
MATERIAL_MAGIC = b"MCFMAT01"
MATERIAL_SCHEMA = 1
SNAPSHOT_MAGIC = b"MCFPV-SparseMaterialSnapshot-v1"
MAX_CELLS = 16_777_216
MAX_RAYS = 1_000_000
MAX_STRING_BYTES = 1 << 20
FIXTURE_SNAPSHOT_SHA256 = (
    "d7fbeb8e26df3c85bb937c91c59d993a55726310f18e5c22273a2ac85871a990"
)
FIXTURE_FILE_SHA256 = (
    "f905c3039d4e0815f4ece5e1fc91aed9b6ab07ff907cdcc889da9fd3b159e9ff"
)

MATERIALS = (
    ("air", (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), 0.0),
    ("foliage", (0.3, 1.2, 3.0), (0.10, 0.35, 0.65), 0.75),
    ("wood", (4.0, 9.0, 15.0), (0.12, 0.22, 0.35), 0.45),
    ("glass", (3.0, 8.0, 14.0), (0.05, 0.08, 0.12), 0.08),
    ("stone", (12.0, 24.0, 36.0), (0.03, 0.05, 0.08), 0.18),
    ("metal", (8.0, 20.0, 35.0), (0.02, 0.04, 0.06), 0.12),
    ("water", (6.0, 18.0, 30.0), (0.08, 0.20, 0.42), 0.05),
    ("soft", (2.0, 7.0, 13.0), (0.25, 0.55, 0.78), 0.65),
)


class BundleError(ValueError):
    """Raised when a bundle violates the schema or identity contract."""


class Reader:
    def __init__(self, payload: bytes):
        self.payload = payload
        self.offset = 0

    def take(self, count: int) -> bytes:
        if count < 0 or self.offset + count > len(self.payload):
            raise BundleError("truncated production bundle")
        result = self.payload[self.offset : self.offset + count]
        self.offset += count
        return result

    def unpack(self, fmt: str):
        size = struct.calcsize(fmt)
        values = struct.unpack(fmt, self.take(size))
        return values[0] if len(values) == 1 else values

    def i32(self) -> int:
        return self.unpack(">i")

    def i64(self) -> int:
        return self.unpack(">q")

    def u64(self) -> int:
        return self.unpack(">Q")

    def f64(self) -> float:
        return self.unpack(">d")

    def text(self) -> str:
        length = self.i32()
        if length < 0 or length > MAX_STRING_BYTES:
            raise BundleError("string byte count is out of range")
        try:
            return self.take(length).decode("utf-8", errors="strict")
        except UnicodeDecodeError as error:
            raise BundleError("metadata string is not valid UTF-8") from error


def material_table_payload() -> bytes:
    payload = bytearray(MATERIAL_MAGIC)
    payload += struct.pack(">ii", MATERIAL_SCHEMA, len(MATERIALS))
    for material_id, transmission, absorption, scattering in MATERIALS:
        encoded_id = material_id.encode("utf-8")
        payload += struct.pack(">i", len(encoded_id))
        payload += encoded_id
        payload += struct.pack(
            ">7d", *transmission, *absorption, scattering
        )
    return bytes(payload)


def material_table_sha256() -> str:
    return hashlib.sha256(material_table_payload()).hexdigest()


def _signed(value: int, bits: int) -> int:
    sign = 1 << (bits - 1)
    return value - (1 << bits) if value & sign else value


def unpack_cell(packed: int) -> tuple[int, int, int]:
    return (
        _signed((packed >> 38) & 0x3FFFFFF, 26),
        _signed(packed & 0xFFF, 12),
        _signed((packed >> 12) & 0x3FFFFFF, 26),
    )


def snapshot_sha256(complete: bool, cells: list[dict]) -> str:
    payload = bytearray(struct.pack(">i", len(SNAPSHOT_MAGIC)))
    payload += SNAPSHOT_MAGIC
    payload += b"\x01" if complete else b"\x00"
    payload += struct.pack(">i", len(cells))
    for cell in cells:
        material_id, transmission, absorption, scattering = MATERIALS[
            cell["material_id"]
        ]
        encoded_id = material_id.encode("utf-8")
        payload += struct.pack(">Q", cell["packed"])
        payload += struct.pack(">i", len(encoded_id))
        payload += encoded_id
        payload += struct.pack(
            ">8d",
            *transmission,
            *absorption,
            scattering,
            cell["fill_fraction"],
        )
    return hashlib.sha256(payload).hexdigest()


def verify_bytes(
    payload: bytes,
    expect_fixture: bool = False,
    require_complete: bool = False,
    include_details: bool = False,
) -> dict:
    reader = Reader(payload)
    if reader.take(len(BUNDLE_MAGIC)) != BUNDLE_MAGIC:
        raise BundleError("invalid production bundle magic")
    schema = reader.i32()
    if schema != BUNDLE_SCHEMA:
        raise BundleError(f"unsupported production bundle schema {schema}")
    mapping_version = reader.i32()
    if mapping_version < 1:
        raise BundleError("mapping algorithm version must be positive")
    material_schema = reader.i32()
    if material_schema != MATERIAL_SCHEMA:
        raise BundleError(
            f"unsupported material table schema {material_schema}"
        )
    stored_material_hash = reader.take(32).hex()
    expected_material_hash = material_table_sha256()
    if stored_material_hash != expected_material_hash:
        raise BundleError("material table hash changed")
    minecraft_version = reader.text()
    mod_version = reader.text()
    if not minecraft_version or not mod_version:
        raise BundleError("version strings must not be blank")
    content_fingerprint = reader.take(32).hex()
    generation = reader.i64()
    if generation < 0:
        raise BundleError("snapshot generation must be non-negative")
    complete_byte = reader.take(1)
    if complete_byte not in (b"\x00", b"\x01"):
        raise BundleError("snapshot complete flag is not boolean")
    complete = complete_byte == b"\x01"
    if require_complete and not complete:
        raise BundleError("production snapshot is incomplete")
    stored_snapshot_hash = reader.take(32).hex()
    cell_count = reader.i32()
    if cell_count < 1 or cell_count > MAX_CELLS:
        raise BundleError("cell count is out of range")
    cells = []
    previous = None
    for _ in range(cell_count):
        packed = reader.u64()
        if previous is not None and packed <= previous:
            raise BundleError("snapshot cells must be strictly ordered")
        previous = packed
        material_id = reader.i32()
        if material_id < 0 or material_id >= len(MATERIALS):
            raise BundleError("material id is out of range")
        fill_fraction = reader.f64()
        if not math.isfinite(fill_fraction) or not 0.0 <= fill_fraction <= 1.0:
            raise BundleError("fill fraction is out of range")
        cells.append(
            {
                "packed": packed,
                "coordinates": unpack_cell(packed),
                "material_id": material_id,
                "fill_fraction": fill_fraction,
            }
        )
    actual_snapshot_hash = snapshot_sha256(complete, cells)
    if stored_snapshot_hash != actual_snapshot_hash:
        raise BundleError("snapshot hash mismatch")
    ray_count = reader.i32()
    if ray_count < 1 or ray_count > MAX_RAYS:
        raise BundleError("ray count is out of range")
    rays = []
    for _ in range(ray_count):
        coordinates = tuple(reader.f64() for _ in range(6))
        if not all(math.isfinite(value) for value in coordinates):
            raise BundleError("ray coordinates must be finite")
        maximum_cells = reader.i32()
        if maximum_cells < 1:
            raise BundleError("maximum cells must be positive")
        rays.append((coordinates, maximum_cells))
    if reader.offset != len(payload):
        raise BundleError("trailing bytes after production bundle")
    file_hash = hashlib.sha256(payload).hexdigest()
    if expect_fixture:
        expected_fingerprint = hashlib.sha256(b"test").hexdigest()
        expected = (
            mapping_version == 1
            and minecraft_version == "fixture-1.21.11"
            and mod_version == "fixture-v1"
            and content_fingerprint == expected_fingerprint
            and generation == 42
            and complete
            and cell_count == 8
            and ray_count == 3
            and stored_snapshot_hash == FIXTURE_SNAPSHOT_SHA256
            and file_hash == FIXTURE_FILE_SHA256
        )
        if not expected:
            raise BundleError("bundle is not the canonical Java fixture")
    summary = {
        "status": "valid",
        "schema": schema,
        "mapping_algorithm_version": mapping_version,
        "material_table_schema": material_schema,
        "material_table_sha256": stored_material_hash,
        "minecraft_version": minecraft_version,
        "mod_version": mod_version,
        "content_fingerprint_sha256": content_fingerprint,
        "snapshot_generation": generation,
        "snapshot_complete": complete,
        "snapshot_sha256": stored_snapshot_hash,
        "cells": cell_count,
        "rays": ray_count,
        "first_cell": cells[0]["coordinates"],
        "last_cell": cells[-1]["coordinates"],
        "file_sha256": file_hash,
        "bytes": len(payload),
    }
    if include_details:
        summary["_cell_records"] = cells
        summary["_ray_records"] = rays
    return summary


def verify_file(
    path: Path,
    expect_fixture: bool = False,
    require_complete: bool = False,
    include_details: bool = False,
) -> dict:
    return verify_bytes(
        path.read_bytes(),
        expect_fixture=expect_fixture,
        require_complete=require_complete,
        include_details=include_details,
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--expect-fixture", action="store_true")
    parser.add_argument("--require-complete", action="store_true")
    arguments = parser.parse_args()
    try:
        summary = verify_file(
            arguments.bundle,
            expect_fixture=arguments.expect_fixture,
            require_complete=arguments.require_complete,
        )
    except (OSError, BundleError) as error:
        print(json.dumps({"status": "invalid", "error": str(error)}))
        return 1
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
