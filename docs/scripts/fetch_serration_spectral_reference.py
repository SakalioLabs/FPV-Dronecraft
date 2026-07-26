#!/usr/bin/env python3
"""Fetch and verify the pinned Recherche Data Gouv spectral-reference file."""

from __future__ import annotations

import argparse
import hashlib
import os
import tempfile
import urllib.request
from pathlib import Path


EXPECTED_BYTES = 4_332_048
FILES = (
    {
        "datafile_id": 709433,
        "filename": "straight_B_acoustic_autopower.h5",
        "md5": "9987760e4146aa8d6f4374ba2ba7e5fc",
        "sha256": (
            "5ce7411ccaaee7d13a32a5ad8fec0bb1"
            "6c720ad335b0540a7d3af17cd90d0faf"
        ),
    },
    {
        "datafile_id": 709441,
        "filename": "straight_tripped_BT_acoustic_autopower.h5",
        "md5": "f402f09880c7b59457e36882c90bad27",
        "sha256": (
            "a56a9fa7dd3b6019686c32ac426b9630"
            "228a287dc8d17db5fff825c767afdbfb"
        ),
    },
    {
        "datafile_id": 709440,
        "filename": "straight_plate_tripped_BPT_acoustic_autopower.h5",
        "md5": "bc0b9c815bdca9f2e073303d315eed7b",
        "sha256": (
            "ce86109218a1cb871205a3abfbfa3b2d"
            "74e69634757266dc03eb73ba762fdffe"
        ),
    },
)


def file_hash(path: Path, algorithm: str) -> str:
    digest = hashlib.new(algorithm)
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def validate(
    path: Path,
    expected_bytes: int = EXPECTED_BYTES,
    expected_md5: str = FILES[-1]["md5"],
    expected_sha256: str = FILES[-1]["sha256"],
) -> None:
    if path.stat().st_size != expected_bytes:
        raise ValueError(
            f"byte count {path.stat().st_size} != {expected_bytes}"
        )
    md5 = file_hash(path, "md5")
    if md5 != expected_md5:
        raise ValueError(f"MD5 {md5} != {expected_md5}")
    sha256 = file_hash(path, "sha256")
    if sha256 != expected_sha256:
        raise ValueError(f"SHA-256 {sha256} != {expected_sha256}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "output_directory",
        type=Path,
        help="Use external-data/computational-acoustics/serration-reference.",
    )
    args = parser.parse_args()
    args.output_directory.mkdir(parents=True, exist_ok=True)
    for specification in FILES:
        filename = str(specification["filename"])
        destination = args.output_directory / filename
        validation = {
            "expected_bytes": EXPECTED_BYTES,
            "expected_md5": str(specification["md5"]),
            "expected_sha256": str(specification["sha256"]),
        }
        if destination.exists():
            validate(destination, **validation)
            print(
                f"already valid: {destination} bytes={EXPECTED_BYTES} "
                f"sha256={specification['sha256']}"
            )
            continue

        descriptor, temporary_name = tempfile.mkstemp(
            prefix=filename + ".",
            suffix=".download",
            dir=args.output_directory,
        )
        os.close(descriptor)
        temporary = Path(temporary_name)
        try:
            url = (
                "https://entrepot.recherche.data.gouv.fr/api/access/"
                f"datafile/{specification['datafile_id']}"
            )
            request = urllib.request.Request(
                url,
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
            validate(temporary, **validation)
            os.replace(temporary, destination)
        finally:
            temporary.unlink(missing_ok=True)
        print(
            f"downloaded: {destination} bytes={EXPECTED_BYTES} "
            f"sha256={specification['sha256']}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
