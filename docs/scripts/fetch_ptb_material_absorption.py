#!/usr/bin/env python3
"""Fetch and verify the pinned PTB room-absorption workbook archive."""

from __future__ import annotations

import argparse
import hashlib
import os
import tempfile
import urllib.request
import zipfile
from pathlib import Path


ARCHIVE_URL = (
    "https://www.ptb.de/cms/fileadmin/internet/fachabteilungen/"
    "abteilung_1/1.6_schall/1.63/abstab_wf.zip"
)
ARCHIVE_FILENAME = "ptb-abstab-wf.zip"
EXPECTED_BYTES = 565_539
EXPECTED_SHA256 = (
    "d40814d54b90ed6cd22bb4a7584cef08"
    "2b758ec01faa6cabf05493da06fe92fe"
)
WORKBOOK_ENTRY = "abstab_wf.xls"
EXPECTED_WORKBOOK_SHA256 = (
    "1d6dab85024bfea8126cd2b0543ece60"
    "72c507004d8ac89a44fd82fa8dc75ff1"
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
    expected_workbook_sha256: str = EXPECTED_WORKBOOK_SHA256,
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
            workbook = archive.read(WORKBOOK_ENTRY)
        except KeyError as error:
            raise ValueError(f"missing {WORKBOOK_ENTRY}") from error
        if len(archive.infolist()) != 1:
            raise ValueError("PTB archive entry set changed")
    workbook_digest = hashlib.sha256(workbook).hexdigest()
    if workbook_digest != expected_workbook_sha256:
        raise ValueError(
            f"workbook SHA-256 {workbook_digest} "
            f"!= {expected_workbook_sha256}"
        )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output_directory", type=Path)
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
        with urllib.request.urlopen(request, timeout=60) as response:
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
