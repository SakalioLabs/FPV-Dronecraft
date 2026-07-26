#!/usr/bin/env python3
"""Fetch only the 29.6 MB Genelec MPS entry from the 3.32 GB BRAS ZIP.

The official DepositOnce server supports HTTP byte ranges. This script reads
the ZIP central directory, downloads the single third-octave directivity MAT
entry needed by the RS5 analysis, decompresses it, and verifies its SHA-256.
It intentionally does not download the 2.0 GB MAT or 1.2 GB CSV impulse-
response directivity entries.
"""

from __future__ import annotations

import argparse
import hashlib
import struct
import urllib.request
import zlib
from pathlib import Path


ARCHIVE_URL = (
    "https://api-depositonce.tu-berlin.de/server/api/core/bitstreams/"
    "8fa1e2c1-e1ef-4225-9f1f-197758c591fa/content"
)
TARGET_SUFFIX = "Genelec8020_DAF_2016_1x1_64442_MPS_front_pole.mat"
EXPECTED_SHA256 = (
    "b368fa00951ac92f942ef13244d4f1039e8310f4931baa2765085d8a22cdd45d"
)
TAIL_BYTES = 131_072


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Range-fetch the official BRAS Genelec 8020c MPS MAT file."
    )
    parser.add_argument("output", type=Path, help="Output MAT path.")
    return parser.parse_args()


def fetch_range(byte_range: str) -> bytes:
    request = urllib.request.Request(
        ARCHIVE_URL,
        headers={
            "Range": f"bytes={byte_range}",
            "User-Agent": "MCFPV-BRAS-research/1.0",
        },
    )
    with urllib.request.urlopen(request, timeout=180) as response:
        if response.status != 206:
            raise RuntimeError(
                f"server ignored byte range {byte_range}: HTTP {response.status}"
            )
        return response.read()


def find_target_entry(
    tail: bytes,
) -> tuple[int, int, int, int, str]:
    eocd = tail.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise ValueError("ZIP end-of-central-directory record not found")
    (
        _,
        _disk,
        _central_disk,
        disk_entries,
        total_entries,
        central_size,
        central_offset,
        comment_length,
    ) = struct.unpack_from("<4s4H2LH", tail, eocd)
    if disk_entries != total_entries or comment_length != 0:
        raise ValueError("unsupported multi-disk or commented ZIP")
    archive_size = central_offset + central_size + len(tail) - eocd
    tail_start = archive_size - len(tail)
    cursor = central_offset - tail_start
    central_end = cursor + central_size
    entries = []
    while cursor < central_end:
        fields = struct.unpack_from("<4s6H3L5H2L", tail, cursor)
        if fields[0] != b"PK\x01\x02":
            raise ValueError("invalid ZIP central-directory entry")
        compressed_size = fields[8]
        uncompressed_size = fields[9]
        name_length = fields[10]
        extra_length = fields[11]
        comment_length = fields[12]
        local_offset = fields[-1]
        name = tail[
            cursor + 46 : cursor + 46 + name_length
        ].decode("utf-8")
        entries.append(
            (
                local_offset,
                compressed_size,
                uncompressed_size,
                name,
            )
        )
        cursor += 46 + name_length + extra_length + comment_length
    for index, entry in enumerate(entries):
        if entry[3].endswith(TARGET_SUFFIX):
            next_offset = (
                entries[index + 1][0]
                if index + 1 < len(entries)
                else central_offset
            )
            return entry[0], next_offset, entry[1], entry[2], entry[3]
    raise ValueError(f"{TARGET_SUFFIX} not found in official ZIP")


def extract_local_entry(
    entry_bytes: bytes,
    compressed_size: int,
    uncompressed_size: int,
) -> bytes:
    fields = struct.unpack_from("<4s5H3L2H", entry_bytes, 0)
    if fields[0] != b"PK\x03\x04":
        raise ValueError("invalid ZIP local entry")
    method = fields[3]
    header_compressed_size = fields[7]
    header_uncompressed_size = fields[8]
    name_length = fields[9]
    extra_length = fields[10]
    if (
        header_compressed_size != compressed_size
        or header_uncompressed_size != uncompressed_size
    ):
        raise ValueError("central and local ZIP sizes disagree")
    data_start = 30 + name_length + extra_length
    compressed = entry_bytes[data_start : data_start + compressed_size]
    if len(compressed) != compressed_size:
        raise ValueError("truncated ZIP entry")
    if method == 0:
        output = compressed
    elif method == 8:
        output = zlib.decompress(compressed, -15)
    else:
        raise ValueError(f"unsupported ZIP compression method {method}")
    if len(output) != uncompressed_size:
        raise ValueError("decompressed MAT size mismatch")
    return output


def main() -> int:
    args = parse_args()
    tail = fetch_range(f"-{TAIL_BYTES}")
    local_offset, next_offset, compressed_size, uncompressed_size, name = (
        find_target_entry(tail)
    )
    entry = fetch_range(f"{local_offset}-{next_offset - 1}")
    output = extract_local_entry(entry, compressed_size, uncompressed_size)
    digest = hashlib.sha256(output).hexdigest()
    if digest != EXPECTED_SHA256:
        raise ValueError(
            f"MAT SHA-256 {digest} does not match {EXPECTED_SHA256}"
        )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(output)
    print(
        f"entry={name}\nbytes={len(output)}\nsha256={digest}\n"
        f"output={args.output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
