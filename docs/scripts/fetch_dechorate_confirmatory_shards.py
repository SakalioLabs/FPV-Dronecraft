#!/usr/bin/env python3
"""Fetch D118 SOFA shards and create or enforce a byte-hash inventory."""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import os
import urllib.request
from pathlib import Path
from typing import Any

from verify_dechorate_confirmatory_drive_manifest import expected_entries


MAGIC = b"\x89HDF\r\n\x1a\n"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def valid_hdf5(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size < 1024:
        return False
    with path.open("rb") as stream:
        return stream.read(len(MAGIC)) == MAGIC


def matches_pin(path: Path, pin: dict[str, Any]) -> bool:
    return (
        valid_hdf5(path)
        and path.stat().st_size == pin["bytes"]
        and sha256(path) == pin["sha256"]
    )


def download(
    entry: dict[str, Any],
    output_directory: Path,
    pin: dict[str, Any] | None,
) -> Path:
    output = output_directory / entry["filename"]
    if pin is not None and matches_pin(output, pin):
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
            while chunk := response.read(1024 * 1024):
                stream.write(chunk)
    if not valid_hdf5(temporary):
        temporary.unlink(missing_ok=True)
        raise ValueError(f"D118 non-HDF5 response: {entry['filename']}")
    if pin is not None and not matches_pin(temporary, pin):
        temporary.unlink(missing_ok=True)
        raise ValueError(f"D118 immutable pin mismatch: {entry['filename']}")
    os.replace(temporary, output)
    return output


def inventory_for(
    preregistration_path: Path,
    manifest_path: Path,
    entries: list[dict[str, Any]],
    output_directory: Path,
) -> dict[str, Any]:
    files = []
    for entry in entries:
        path = output_directory / entry["filename"]
        if not valid_hdf5(path):
            raise ValueError(f"D118 invalid local HDF5: {entry['filename']}")
        files.append(
            {
                **entry,
                "bytes": path.stat().st_size,
                "sha256": sha256(path),
            }
        )
    return {
        "schema_version": 1,
        "status": "hash-pinned-dechorate-confirmatory-inventory",
        "source_preregistration_sha256": sha256(preregistration_path),
        "source_drive_manifest_sha256": sha256(manifest_path),
        "files": files,
        "aggregate_sha256": hashlib.sha256(
            "".join(item["sha256"] for item in files).encode("ascii")
        ).hexdigest(),
        "total_bytes": sum(item["bytes"] for item in files),
        "sofa_payload_opened": False,
        "waveforms_accessed": False,
        "claim_boundary": (
            "Only file bytes, HDF5 magic, sizes and SHA-256 values were "
            "inspected. No HDF5 dataset or RIR waveform was opened."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preregistration", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--pins", type=Path, required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    parser.add_argument("--inventory-json", type=Path, required=True)
    parser.add_argument("--workers", type=int, default=6)
    args = parser.parse_args()
    if args.workers < 1 or args.workers > 8:
        raise ValueError("D118 workers must be in [1,8]")
    preregistration = json.loads(
        args.preregistration.read_text(encoding="utf-8")
    )
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    immutable_pins = json.loads(args.pins.read_text(encoding="utf-8"))
    if (
        manifest["source_preregistration_sha256"]
        != sha256(args.preregistration)
        or immutable_pins["source_preregistration_sha256"]
        != sha256(args.preregistration)
        or immutable_pins["source_drive_manifest_sha256"]
        != sha256(args.manifest)
        or immutable_pins["status"]
        != "byte-pins-fixed-before-sofa-payload-or-waveform-access"
    ):
        raise ValueError("D118 fetch inputs are detached")
    selected = expected_entries(preregistration, manifest)
    if set(immutable_pins["files"]) != {
        item["filename"] for item in selected
    }:
        raise ValueError("D118 immutable pin filename set changed")
    previous = None
    if args.inventory_json.is_file():
        previous = json.loads(
            args.inventory_json.read_text(encoding="utf-8")
        )
        if (
            previous["source_preregistration_sha256"]
            != sha256(args.preregistration)
            or previous["source_drive_manifest_sha256"]
            != sha256(args.manifest)
            or len(previous["files"]) != 33
        ):
            raise ValueError("D118 existing inventory binding changed")
    args.output_directory.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(
        max_workers=args.workers
    ) as executor:
        futures = [
            executor.submit(
                download,
                entry,
                args.output_directory,
                {
                    "bytes": immutable_pins["files"][
                        entry["filename"]
                    ][0],
                    "sha256": immutable_pins["files"][
                        entry["filename"]
                    ][1],
                },
            )
            for entry in selected
        ]
        for future in concurrent.futures.as_completed(futures):
            future.result()
    current = inventory_for(
        args.preregistration,
        args.manifest,
        selected,
        args.output_directory,
    )
    if (
        current["total_bytes"] != immutable_pins["total_bytes"]
        or current["aggregate_sha256"]
        != immutable_pins["aggregate_sha256"]
        or any(
            immutable_pins["files"][item["filename"]]
            != [item["bytes"], item["sha256"]]
            for item in current["files"]
        )
    ):
        raise ValueError("D118 immutable byte inventory mismatch")
    if previous is not None:
        if current != previous:
            raise ValueError("D118 immutable inventory changed")
    else:
        args.inventory_json.parent.mkdir(parents=True, exist_ok=True)
        args.inventory_json.write_text(
            json.dumps(current, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
    print(
        json.dumps(
            {
                "status": current["status"],
                "files": len(current["files"]),
                "total_bytes": current["total_bytes"],
                "aggregate_sha256": current["aggregate_sha256"],
                "waveforms_accessed": False,
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
