#!/usr/bin/env python3
"""Fetch the two small, hash-pinned D113 construction-evidence files."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import tempfile
import urllib.request
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def valid(path: Path, size: int, digest: str) -> bool:
    return path.is_file() and path.stat().st_size == size and sha256(path) == digest


def fetch(url: str, output: Path, size: int, digest: str) -> str:
    if valid(output, size, digest):
        return "already-valid"
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=output.name + ".", suffix=".part", dir=output.parent
    )
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        request = urllib.request.Request(
            url, headers={"User-Agent": "MCFPV-acoustic-research/1"}
        )
        with urllib.request.urlopen(request, timeout=120) as response:
            with temporary.open("wb") as stream:
                while chunk := response.read(1024 * 1024):
                    stream.write(chunk)
        if not valid(temporary, size, digest):
            raise ValueError(f"downloaded evidence hash/size mismatch: {url}")
        os.replace(temporary, output)
    finally:
        temporary.unlink(missing_ok=True)
    return "downloaded"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    paper = manifest["air_paper"]
    metadata = manifest["dechorate_metadata"]
    results = {
        "air_paper": fetch(
            paper["official_url"],
            args.output_directory / "jeub09a.pdf",
            paper["bytes"],
            paper["sha256"],
        ),
        "dechorate_metadata": fetch(
            metadata["file_url"],
            args.output_directory / metadata["filename"],
            metadata["bytes"],
            metadata["sha256"],
        ),
    }
    print(json.dumps({"status": "valid", "resources": results}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
