#!/usr/bin/env python3
"""Fetch the small hash-pinned dEchorate source-code evidence set."""

from __future__ import annotations

import argparse
import hashlib
import json
import urllib.request
from pathlib import Path


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    args.output_directory.mkdir(parents=True, exist_ok=True)
    for item in manifest["official_code"]["files"]:
        output = args.output_directory / Path(item["path"]).name
        if (
            output.is_file()
            and output.stat().st_size == item["bytes"]
            and sha256(output) == item["sha256"]
        ):
            print(f"verified {output.name}")
            continue
        with urllib.request.urlopen(item["url"], timeout=60) as response:
            data = response.read()
        if (
            len(data) != item["bytes"]
            or hashlib.sha256(data).hexdigest() != item["sha256"]
        ):
            raise ValueError(f"hash or size mismatch for {item['path']}")
        output.write_bytes(data)
        print(f"fetched {output.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
