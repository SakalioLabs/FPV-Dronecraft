#!/usr/bin/env python3
"""Fetch the 66-file dEchorate SOFA subset and emit a hash inventory."""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import os
import urllib.request
from pathlib import Path
from typing import Any


MAGIC = b"\x89HDF\r\n\x1a\n"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def entries(manifest: dict[str, Any]) -> list[dict[str, Any]]:
    result = []
    for room_code, sources in manifest["google_drive_ids"].items():
        for source_key, drive_ids in sources.items():
            source_id = int(source_key)
            selections = manifest["source_selection"][source_key]
            if len(drive_ids) != len(selections):
                raise ValueError("D115 Drive ID/selection count changed")
            for selection, drive_id in zip(
                selections, drive_ids, strict=True
            ):
                array_id = selection["sofa_array_id"]
                first = (array_id - 1) * 5 + 1
                last = array_id * 5
                filename = (
                    f"dEchorate_room{room_code}_src{source_id + 1}_"
                    f"arr{array_id}_mics{first}-{last}.sofa"
                )
                result.append(
                    {
                        "room_code": room_code,
                        "source_id": source_id,
                        **selection,
                        "google_drive_id": drive_id,
                        "filename": filename,
                    }
                )
    if len(result) != manifest["expected_files"]:
        raise ValueError("D115 expected file count changed")
    return result


def valid_hdf5(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size < 1024:
        return False
    with path.open("rb") as stream:
        return stream.read(len(MAGIC)) == MAGIC


def matches_pin(path: Path, pin: list[Any] | dict[str, Any]) -> bool:
    if isinstance(pin, dict):
        expected_bytes = pin["bytes"]
        expected_sha = pin["sha256"]
    else:
        expected_bytes, expected_sha = pin
    return (
        valid_hdf5(path)
        and path.stat().st_size == expected_bytes
        and sha256(path) == expected_sha
    )


def fetch(
    entry: dict[str, Any],
    output_directory: Path,
    pin: list[Any] | dict[str, Any],
) -> Path:
    output = output_directory / entry["filename"]
    if matches_pin(output, pin):
        return output
    temporary = output.with_suffix(output.suffix + ".part")
    url = (
        "https://drive.usercontent.google.com/download"
        f"?id={entry['google_drive_id']}&export=download&confirm=t"
    )
    request = urllib.request.Request(
        url, headers={"User-Agent": "MCFPV-acoustic-research/1"}
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        with temporary.open("wb") as stream:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                stream.write(chunk)
    if not valid_hdf5(temporary):
        raise ValueError(f"D115 non-HDF5 response for {entry['filename']}")
    if not matches_pin(temporary, pin):
        raise ValueError(f"D115 pin mismatch for {entry['filename']}")
    os.replace(temporary, output)
    return output


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--pins", type=Path, required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    parser.add_argument("--inventory-json", type=Path, required=True)
    parser.add_argument("--workers", type=int, default=6)
    args = parser.parse_args()
    if args.workers < 1 or args.workers > 8:
        raise ValueError("D115 workers must be in [1,8]")
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    pins = json.loads(args.pins.read_text(encoding="utf-8"))
    if (
        pins["source_manifest_sha256"] != sha256(args.manifest)
        or len(pins["files"]) != manifest["expected_files"]
    ):
        raise ValueError("D115 pins are detached from the source manifest")
    selected = entries(manifest)
    args.output_directory.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(
        max_workers=args.workers
    ) as executor:
        futures = {
            executor.submit(
                fetch,
                entry,
                args.output_directory,
                pins["files"][entry["filename"]],
            ): entry
            for entry in selected
        }
        for future in concurrent.futures.as_completed(futures):
            future.result()
    annotation = pins["annotations"]
    fetch(annotation, args.output_directory, annotation)
    files = []
    for entry in selected:
        path = args.output_directory / entry["filename"]
        files.append(
            {
                **entry,
                "bytes": path.stat().st_size,
                "sha256": sha256(path),
            }
        )
    inventory = {
        "schema_version": 1,
        "status": "valid-dechorate-sofa-subset-inventory",
        "source_manifest_sha256": sha256(args.manifest),
        "source_pins_sha256": sha256(args.pins),
        "files": files,
        "aggregate_sha256": hashlib.sha256(
            "".join(item["sha256"] for item in files).encode("ascii")
        ).hexdigest(),
        "total_bytes": sum(item["bytes"] for item in files),
        "annotations": {
            **annotation,
            "verified": matches_pin(
                args.output_directory / annotation["filename"], annotation
            ),
        },
    }
    if (
        inventory["aggregate_sha256"] != pins["aggregate_sha256"]
        or inventory["total_bytes"] != pins["total_bytes"]
        or not inventory["annotations"]["verified"]
    ):
        raise ValueError("D115 aggregate pin mismatch")
    args.inventory_json.parent.mkdir(parents=True, exist_ok=True)
    args.inventory_json.write_text(
        json.dumps(inventory, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(
        json.dumps(
            {
                "status": inventory["status"],
                "files": len(files),
                "total_bytes": inventory["total_bytes"],
                "aggregate_sha256": inventory["aggregate_sha256"],
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
