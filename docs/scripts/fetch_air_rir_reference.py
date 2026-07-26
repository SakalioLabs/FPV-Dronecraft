#!/usr/bin/env python3
"""Fetch and verify the pinned RWTH Aachen AIR v1.4 RIR archive."""

from __future__ import annotations

import argparse
import hashlib
import os
import tempfile
import urllib.request
import zipfile
from pathlib import Path


ARCHIVE_URL = (
    "https://www.iks.rwth-aachen.de/fileadmin/user_upload/downloads/"
    "forschung/tools-downloads/air_database_release_1_4.zip"
)
ARCHIVE_FILENAME = "air_database_release_1_4.zip"
EXPECTED_BYTES = 202_650_373
EXPECTED_SHA256 = (
    "d2fd52767505c402e8aed9299dcd0464"
    "93c8254cfab2f769fcf480dc7c616a5f"
)
LICENSE_ENTRY = "AIR_1_4/license.txt"
EXPECTED_LICENSE_SHA256 = (
    "576e14bfd0215f50085d9980f8586f2c"
    "d3e69b0db43cbf1cb12aa49807406347"
)


def file_hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def validate(
    path: Path,
    *,
    expected_bytes: int = EXPECTED_BYTES,
    expected_sha256: str = EXPECTED_SHA256,
    expected_license_sha256: str = EXPECTED_LICENSE_SHA256,
) -> None:
    if path.stat().st_size != expected_bytes:
        raise ValueError(
            f"byte count {path.stat().st_size} != {expected_bytes}"
        )
    digest = file_hash(path)
    if digest != expected_sha256:
        raise ValueError(f"SHA-256 {digest} != {expected_sha256}")
    with zipfile.ZipFile(path) as archive:
        try:
            license_payload = archive.read(LICENSE_ENTRY)
        except KeyError as error:
            raise ValueError(f"missing embedded {LICENSE_ENTRY}") from error
    license_digest = hashlib.sha256(license_payload).hexdigest()
    if license_digest != expected_license_sha256:
        raise ValueError(
            "embedded license SHA-256 "
            f"{license_digest} != {expected_license_sha256}"
        )
    if b"MIT License" not in license_payload:
        raise ValueError("embedded license is not identified as MIT")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "output_directory",
        type=Path,
        help="Use external-data/computational-acoustics.",
    )
    args = parser.parse_args()
    args.output_directory.mkdir(parents=True, exist_ok=True)
    destination = args.output_directory / ARCHIVE_FILENAME
    if destination.exists():
        validate(destination)
        print(
            f"already valid: {destination} bytes={EXPECTED_BYTES} "
            f"sha256={EXPECTED_SHA256}"
        )
        return 0

    descriptor, temporary_name = tempfile.mkstemp(
        prefix=ARCHIVE_FILENAME + ".",
        suffix=".download",
        dir=args.output_directory,
    )
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        request = urllib.request.Request(
            ARCHIVE_URL,
            headers={"User-Agent": "MCFPV-acoustic-research/1.0"},
        )
        with urllib.request.urlopen(request, timeout=180) as response:
            if response.status != 200:
                raise RuntimeError(
                    f"unexpected HTTP status {response.status}"
                )
            with temporary.open("wb") as output:
                while chunk := response.read(1024 * 1024):
                    output.write(chunk)
        validate(temporary)
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)
    print(
        f"downloaded: {destination} bytes={EXPECTED_BYTES} "
        f"sha256={EXPECTED_SHA256}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
