#!/usr/bin/env python3
"""Verify the offline D118 filename/Drive-ID manifest contract."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def expected_entries(
    preregistration: dict[str, Any],
    manifest: dict[str, Any],
) -> list[dict[str, Any]]:
    arrays = {
        item["sofa_array_id"]: item
        for item in preregistration["arrays"]
    }
    if manifest["array_order"] != [1, 2, 6] or set(arrays) != {1, 2, 6}:
        raise ValueError("D118 array identity changed")
    entries: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    for room in preregistration["room_codes"]:
        ids = manifest["google_drive_ids"].get(room)
        if not isinstance(ids, list) or len(ids) != 3:
            raise ValueError(f"D118 Drive IDs missing for room {room}")
        for array_id, drive_id in zip(
            manifest["array_order"], ids, strict=True
        ):
            if (
                not isinstance(drive_id, str)
                or not drive_id
                or drive_id in seen_ids
            ):
                raise ValueError("D118 Drive IDs are invalid or duplicated")
            seen_ids.add(drive_id)
            definition = arrays[array_id]
            entries.append(
                {
                    "room_code": room,
                    "source_id": preregistration["source_id"],
                    "sofa_array_id": array_id,
                    "microphone_ids": definition["microphone_ids"],
                    "filename": definition["filename_template"].format(
                        room_code=room
                    ),
                    "google_drive_id": drive_id,
                }
            )
    return entries


def verify(
    preregistration_path: Path,
    manifest_path: Path,
) -> dict[str, Any]:
    preregistration = json.loads(
        preregistration_path.read_text(encoding="utf-8")
    )
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    preregistration_hash = sha256(preregistration_path)
    entries = expected_entries(preregistration, manifest)
    gates = {
        "preregistered_before_discovery": (
            preregistration["status"]
            == "preregistered-before-drive-id-discovery-or-waveform-access"
            and manifest["status"]
            == (
                "drive-ids-discovered-after-preregistration-"
                "before-waveform-access"
            )
            and manifest["source_preregistration_sha256"]
            == preregistration_hash
        ),
        "official_folder_unchanged": (
            manifest["official_folder"]
            == preregistration["official_folder"]
        ),
        "exact_file_count": (
            len(entries)
            == manifest["expected_files"]
            == preregistration["expected_files"]
            == 33
        ),
        "exact_rir_count": (
            sum(len(item["microphone_ids"]) for item in entries)
            == manifest["expected_rirs"]
            == preregistration["expected_rirs"]
            == 165
        ),
        "no_missing_or_duplicate_filenames": (
            manifest["missing_filenames"] == []
            and manifest["duplicate_filenames"] == []
            and len({item["filename"] for item in entries}) == 33
        ),
        "unique_drive_ids": (
            len({item["google_drive_id"] for item in entries}) == 33
        ),
        "waveforms_accessed": False,
    }
    if not all(value for key, value in gates.items() if key != "waveforms_accessed"):
        raise ValueError("D118 Drive manifest verification failed")
    return {
        "schema_version": 1,
        "status": "verified-dechorate-confirmatory-drive-manifest",
        "source_preregistration_sha256": preregistration_hash,
        "source_drive_manifest_sha256": sha256(manifest_path),
        "entries": entries,
        "gates": gates,
        "claim_boundary": (
            "Offline structural verification of the 33 public Drive "
            "identities; no SOFA bytes or waveforms are accessed."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preregistration", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = verify(args.preregistration, args.manifest)
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(
        json.dumps(
            {
                "status": report["status"],
                "files": len(report["entries"]),
                "waveforms_accessed": False,
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
