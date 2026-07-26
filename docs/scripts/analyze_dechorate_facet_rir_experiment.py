#!/usr/bin/env python3
"""Build D114 facet snapshots and a minimal real-RIR extraction manifest."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
from typing import Any


FACETS = ("floor", "ceiling", "west", "south", "east", "north")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def md5_stream(path: Path) -> str:
    digest = hashlib.md5(usedforsecurity=False)
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_file(path: Path, spec: dict[str, Any], label: str) -> None:
    if (
        not path.is_file()
        or path.stat().st_size != spec["bytes"]
        or sha256(path) != spec["sha256"]
    ):
        raise ValueError(f"D114 {label} hash or size changed")


def canonical_hash(value: Any) -> str:
    payload = json.dumps(
        value, sort_keys=True, separators=(",", ":"), ensure_ascii=True
    ).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def unique_entities(
    rows: list[dict[str, str]],
    kind: str,
    selected_ids: set[int] | None = None,
) -> dict[int, dict[str, Any]]:
    if kind == "source":
        id_field, type_field, prefix = "src_id", "src_type", "src_pos"
        selected = [
            row for row in rows
            if row["src_id"] and row["src_type"] != "silence"
        ]
    else:
        id_field, type_field, prefix = "mic_id", "mic_type", "mic_pos"
        selected = [row for row in rows if row["mic_type"] == "capsule"]
    entities: dict[int, dict[str, Any]] = {}
    for row in selected:
        entity_id = int(float(row[id_field]))
        if selected_ids is not None and entity_id not in selected_ids:
            continue
        value = {
            "id": entity_id,
            "type": row[type_field],
            "position_m": [
                float(row[f"{prefix}_x"]),
                float(row[f"{prefix}_y"]),
                float(row[f"{prefix}_z"]),
            ],
        }
        if kind == "microphone":
            value["array_id"] = int(float(row["array_id"]))
        previous = entities.setdefault(entity_id, value)
        if previous != value:
            raise ValueError(f"D114 inconsistent {kind} {entity_id}")
    return entities


def facet_cells(dimensions: list[int], facet: str) -> int:
    x, y, z = dimensions
    return {
        "floor": x * y,
        "ceiling": x * y,
        "west": y * z,
        "east": y * z,
        "south": x * z,
        "north": x * z,
    }[facet]


def snapshot(
    room_code: str, room: dict[str, Any]
) -> dict[str, Any]:
    special = room["special_conditions"].get(room_code)
    facet_code = special["facet_code"] if special else room_code
    if len(facet_code) != 6 or any(bit not in "01" for bit in facet_code):
        raise ValueError(f"D114 invalid facet code {room_code}")
    dimensions = room["voxelization"]["interior_cells_xyz"]
    faces = []
    canonical_faces = []
    for facet, bit in zip(FACETS, facet_code, strict=True):
        material = room["state_material"][bit]
        cells = facet_cells(dimensions, facet)
        face = {
            "facet": facet,
            "state": int(bit),
            "material": material,
            "surface_cells": cells,
        }
        faces.append(face)
        canonical_faces.extend(
            f"{facet}:{index}:{material}" for index in range(cells)
        )
    return {
        "room_code": room_code,
        "facet_code": facet_code,
        "furniture": bool(special and special["furniture"]),
        "furniture_geometry_known": bool(
            special and special["furniture_geometry_known"]
        ),
        "interior_cells_xyz": dimensions,
        "surface_cell_count": len(canonical_faces),
        "faces": faces,
        "snapshot_sha256": canonical_hash(canonical_faces),
    }


def geometry(room: dict[str, Any]) -> dict[str, Any]:
    x, y, z = room["physical_dimensions_m"]
    vx, vy, vz = room["voxelization"]["interior_cells_xyz"]
    physical_areas = {
        "floor": x * y,
        "ceiling": x * y,
        "west": y * z,
        "east": y * z,
        "south": x * z,
        "north": x * z,
    }
    voxel_areas = {
        facet: float(facet_cells([vx, vy, vz], facet)) for facet in FACETS
    }
    physical_volume = x * y * z
    voxel_volume = float(vx * vy * vz)
    physical_surface = sum(physical_areas.values())
    voxel_surface = sum(voxel_areas.values())
    relative = lambda observed, reference: (observed - reference) / reference
    return {
        "physical_volume_m3": physical_volume,
        "voxel_volume_m3": voxel_volume,
        "voxel_volume_relative_error": relative(
            voxel_volume, physical_volume
        ),
        "physical_surface_m2": physical_surface,
        "voxel_surface_m2": voxel_surface,
        "voxel_surface_relative_error": relative(
            voxel_surface, physical_surface
        ),
        "facet_area_relative_error": {
            facet: relative(voxel_areas[facet], physical_areas[facet])
            for facet in FACETS
        },
    }


def analyze(
    manifest_path: Path,
    metadata_path: Path,
    source_directory: Path,
    d113_path: Path,
    rir_payload_path: Path | None = None,
) -> dict[str, Any]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    d113 = json.loads(d113_path.read_text(encoding="utf-8"))
    if manifest.get("schema_version") != 1:
        raise ValueError("D114 manifest schema changed")
    validate_file(metadata_path, manifest["metadata"], "metadata")
    source_hashes = {}
    for spec in manifest["official_code"]["files"]:
        path = source_directory / Path(spec["path"]).name
        validate_file(path, spec, spec["path"])
        source_hashes[spec["path"]] = sha256(path)
    if (
        d113.get("status") != "valid-air-room-construction-evidence"
        or d113.get("decision", {}).get("next_controlled_reference")
        != "dechorate-v2-metadata-first"
    ):
        raise ValueError("D114 D113 handoff changed")
    with metadata_path.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    sources = unique_entities(
        rows, "source", set(manifest["selection"]["source_ids"])
    )
    microphones = unique_entities(rows, "microphone")
    selected = []
    source_summaries = []
    for source_id in manifest["selection"]["source_ids"]:
        source = sources[source_id]
        distances = []
        for microphone in microphones.values():
            distance = math.dist(
                source["position_m"], microphone["position_m"]
            )
            distances.append((distance, microphone["id"], microphone))
        distances.sort(key=lambda item: (item[0], item[1]))
        chosen = []
        for rank in manifest["selection"]["microphone_distance_ranks"]:
            distance, _, microphone = distances[rank - 1]
            chosen.append(
                {
                    **microphone,
                    "distance_rank": rank,
                    "distance_m": distance,
                    "direct_arrival_sample_at_published_c": (
                        distance
                        / 346.98
                        * manifest["rir_payload"]["sampling_rate_hz"]
                    ),
                }
            )
        source_summaries.append({**source, "microphones": chosen})
        for room_code in manifest["room"]["room_codes"]:
            for microphone in chosen:
                selected.append(
                    {
                        "room_code": room_code,
                        "hdf5_dataset": manifest["rir_payload"][
                            "dataset_path_template"
                        ].format(room_code=room_code, source_id=source_id),
                        "source_id": source_id,
                        "microphone_id": microphone["id"],
                        "hdf5_column": microphone["id"],
                        "source_type": source["type"],
                        "distance_rank": microphone["distance_rank"],
                    }
                )
    if len(selected) != manifest["selection"]["expected_rir_count"]:
        raise ValueError("D114 selected RIR count changed")
    payload_present = bool(rir_payload_path and rir_payload_path.is_file())
    payload_md5 = None
    if payload_present:
        payload_md5 = md5_stream(rir_payload_path)
        if payload_md5 != manifest["rir_payload"]["published_md5"]:
            raise ValueError("D114 RIR payload MD5 changed")
    snapshots = [
        snapshot(code, manifest["room"])
        for code in manifest["room"]["room_codes"]
    ]
    return {
        "schema_version": 1,
        "status": "valid-dechorate-facet-rir-experiment",
        "source_manifest_sha256": sha256(manifest_path),
        "source_metadata_sha256": sha256(metadata_path),
        "source_d113_report_sha256": sha256(d113_path),
        "official_code_commit": manifest["official_code"]["commit"],
        "official_code_hashes": source_hashes,
        "room": {
            "physical_dimensions_m": manifest["room"][
                "physical_dimensions_m"
            ],
            "voxelization": manifest["room"]["voxelization"],
            "facet_code_order": list(FACETS),
            "geometry_error": geometry(manifest["room"]),
        },
        "snapshots": snapshots,
        "selected_sources": source_summaries,
        "rir_extraction_manifest": selected,
        "rir_payload": {
            "present": payload_present,
            "md5": payload_md5,
            "waveform_metrics_computed": False,
        },
        "decision": {
            "facet_snapshots_admitted": True,
            "furniture_voxel_geometry_admitted": False,
            "minimal_real_rir_subset_fixed": True,
            "real_waveform_comparison_eligible": payload_present,
            "production_material_fit_eligible": False,
            "production_change_required": False,
            "next_action": (
                "extract-and-score-66-real-rirs"
                if payload_present
                else "materialize-hash-pinned-rir-payload"
            ),
        },
        "physical_endpoint_opened": False,
        "captures_audio": False,
        "release_calibrated": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--source-directory", type=Path, required=True)
    parser.add_argument("--d113-report", type=Path, required=True)
    parser.add_argument("--rir-payload", type=Path)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    result = analyze(
        args.manifest,
        args.metadata,
        args.source_directory,
        args.d113_report,
        args.rir_payload,
    )
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(
        json.dumps(
            {
                "status": result["status"],
                "snapshots": len(result["snapshots"]),
                "selected_rirs": len(result["rir_extraction_manifest"]),
                "rir_payload_present": result["rir_payload"]["present"],
                "output": str(args.output_json),
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
