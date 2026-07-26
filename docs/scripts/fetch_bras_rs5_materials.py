#!/usr/bin/env python3
"""Range-fetch the official BRAS RS5 MDF and tile boundary descriptions."""

from __future__ import annotations

import argparse
import hashlib
import struct
import urllib.request
import zlib
from pathlib import Path


ARCHIVE_URL = (
    "https://api-depositonce.tu-berlin.de/server/api/core/bitstreams/"
    "57c47134-def5-40a9-a761-970e82c4a8ea/content"
)
ENTRIES = (
    (
        53,
        361_558,
        "MaterialOverview.pdf",
        "03dd3d0800b1f5bf04730800f9516cfca76882b129ebb6524edb9f722f59d926",
    ),
    (
        370_138,
        370_399,
        "mat_MDF25mmB_plane_00deg.csv",
        "3ceee95b28fc2a010bc6b2b7399d35d2fe28ee6d3d064f43e5d33d5f3e120019",
    ),
    (
        381_520,
        382_248,
        "mat_MDF25mmB_plane.txt",
        "9edb838b4b8fde2a0c13834e6d3241ed4b9fc08e951a2b44bb982beb58496c5f",
    ),
    (
        371_769,
        372_023,
        "mat_Tiles.csv",
        "9b5709ed38d9d8333ad070e32f3d11cd15677fdb79fdc5d9e4eb2feaa59db2e0",
    ),
    (
        383_260,
        383_791,
        "mat_Tiles.txt",
        "352166b1e88834d25485552c742d467cb631cb301601951ac60dd4e401cee50c",
    ),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Fetch the small BRAS RS5 material subset by HTTP range."
    )
    parser.add_argument("output_directory", type=Path)
    return parser.parse_args()


def fetch_entry(first: int, last: int) -> bytes:
    request = urllib.request.Request(
        ARCHIVE_URL,
        headers={
            "Range": f"bytes={first}-{last}",
            "User-Agent": "MCFPV-BRAS-research/1.0",
        },
    )
    with urllib.request.urlopen(request, timeout=180) as response:
        if response.status != 206:
            raise RuntimeError(
                f"server ignored byte range {first}-{last}: "
                f"HTTP {response.status}"
            )
        entry = response.read()
    fields = struct.unpack_from("<4s5H3L2H", entry, 0)
    if fields[0] != b"PK\x03\x04":
        raise ValueError("invalid ZIP local entry")
    method = fields[3]
    compressed_size = fields[7]
    uncompressed_size = fields[8]
    name_length = fields[9]
    extra_length = fields[10]
    data_start = 30 + name_length + extra_length
    compressed = entry[data_start : data_start + compressed_size]
    if len(compressed) != compressed_size:
        raise ValueError("truncated ZIP local entry")
    if method == 0:
        output = compressed
    elif method == 8:
        output = zlib.decompress(compressed, -15)
    else:
        raise ValueError(f"unsupported ZIP compression method {method}")
    if len(output) != uncompressed_size:
        raise ValueError("decompressed material size mismatch")
    return output


def main() -> int:
    args = parse_args()
    args.output_directory.mkdir(parents=True, exist_ok=True)
    for first, last, filename, expected_hash in ENTRIES:
        output = fetch_entry(first, last)
        digest = hashlib.sha256(output).hexdigest()
        if digest != expected_hash:
            raise ValueError(
                f"{filename}: SHA-256 {digest} does not match {expected_hash}"
            )
        destination = args.output_directory / filename
        destination.write_bytes(output)
        print(f"{filename} bytes={len(output)} sha256={digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
