#!/usr/bin/env python3
"""Verify D114 source bindings, facet snapshots, and RIR selection policy."""

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


def canonical_hash(value: Any) -> str:
    return hashlib.sha256(
        json.dumps(
            value, sort_keys=True, separators=(",", ":"), ensure_ascii=True
        ).encode("utf-8")
    ).hexdigest()


def load_entities(
    metadata_path: Path,
) -> tuple[dict[int, dict[str, Any]], dict[int, dict[str, Any]]]:
    with metadata_path.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    sources: dict[int, dict[str, Any]] = {}
    microphones: dict[int, dict[str, Any]] = {}
    for row in rows:
        if (
            row["src_id"]
            and row["src_type"] != "silence"
            and int(float(row["src_id"])) in {4, 6}
        ):
            entity_id = int(float(row["src_id"]))
            value = {
                "id": entity_id,
                "type": row["src_type"],
                "position_m": [
                    float(row["src_pos_x"]),
                    float(row["src_pos_y"]),
                    float(row["src_pos_z"]),
                ],
            }
            if entity_id in sources and sources[entity_id] != value:
                raise ValueError("D114 inconsistent source metadata")
            sources[entity_id] = value
        if row["mic_type"] == "capsule":
            entity_id = int(float(row["mic_id"]))
            value = {
                "id": entity_id,
                "type": "capsule",
                "position_m": [
                    float(row["mic_pos_x"]),
                    float(row["mic_pos_y"]),
                    float(row["mic_pos_z"]),
                ],
                "array_id": int(float(row["array_id"])),
            }
            if entity_id in microphones and microphones[entity_id] != value:
                raise ValueError("D114 inconsistent microphone metadata")
            microphones[entity_id] = value
    return sources, microphones


def surface_cells(dimensions: list[int], facet: str) -> int:
    x, y, z = dimensions
    return {
        "floor": x * y,
        "ceiling": x * y,
        "west": y * z,
        "east": y * z,
        "south": x * z,
        "north": x * z,
    }[facet]


def verify(
    report: dict[str, Any],
    report_sha: str,
    manifest_path: Path,
    metadata_path: Path,
    source_directory: Path,
    d113_path: Path,
) -> dict[str, Any]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "valid-dechorate-facet-rir-experiment"
        or report.get("source_manifest_sha256") != sha256(manifest_path)
        or report.get("source_metadata_sha256") != sha256(metadata_path)
        or report.get("source_d113_report_sha256") != sha256(d113_path)
    ):
        raise ValueError("D114 report shape or source binding changed")
    if (
        metadata_path.stat().st_size != manifest["metadata"]["bytes"]
        or sha256(metadata_path) != manifest["metadata"]["sha256"]
    ):
        raise ValueError("D114 metadata binding changed")
    expected_code_hashes = {}
    for spec in manifest["official_code"]["files"]:
        path = source_directory / Path(spec["path"]).name
        if (
            not path.is_file()
            or path.stat().st_size != spec["bytes"]
            or sha256(path) != spec["sha256"]
        ):
            raise ValueError("D114 official code binding changed")
        expected_code_hashes[spec["path"]] = spec["sha256"]
    if (
        report.get("official_code_commit")
        != "d3e664f1e7a7d46241d7f7b3b3761448686b9537"
        or report.get("official_code_hashes") != expected_code_hashes
    ):
        raise ValueError("D114 official code report changed")
    sources, microphones = load_entities(metadata_path)
    expected_source_types = {4: "invdirectional", 6: "omnidirectional"}
    expected_selected = []
    expected_source_reports = []
    ranks = [1, 15, 30]
    for source_id in (4, 6):
        source = sources[source_id]
        if source["type"] != expected_source_types[source_id]:
            raise ValueError("D114 selected source type changed")
        ordered = sorted(
            (
                math.dist(source["position_m"], mic["position_m"]),
                mic_id,
                mic,
            )
            for mic_id, mic in microphones.items()
        )
        chosen = []
        for rank in ranks:
            distance, _, microphone = ordered[rank - 1]
            chosen.append(
                {
                    **microphone,
                    "distance_rank": rank,
                    "distance_m": distance,
                    "direct_arrival_sample_at_published_c": (
                        distance / 346.98 * 48000
                    ),
                }
            )
        expected_source_reports.append({**source, "microphones": chosen})
        for room_code in manifest["room"]["room_codes"]:
            for microphone in chosen:
                expected_selected.append(
                    {
                        "room_code": room_code,
                        "hdf5_dataset": f"/rir/{room_code}/{source_id}",
                        "source_id": source_id,
                        "microphone_id": microphone["id"],
                        "hdf5_column": microphone["id"],
                        "source_type": source["type"],
                        "distance_rank": microphone["distance_rank"],
                    }
                )
    if (
        report.get("selected_sources") != expected_source_reports
        or report.get("rir_extraction_manifest") != expected_selected
        or len(expected_selected) != 66
    ):
        raise ValueError("D114 RIR selection changed")
    snapshots = report.get("snapshots")
    if (
        not isinstance(snapshots, list)
        or [entry.get("room_code") for entry in snapshots]
        != manifest["room"]["room_codes"]
    ):
        raise ValueError("D114 snapshot order changed")
    dimensions = [6, 6, 2]
    for entry in snapshots:
        special = entry["room_code"] == "020002"
        facet_code = "010001" if special else entry["room_code"]
        canonical = []
        for facet, bit in zip(FACETS, facet_code, strict=True):
            material = manifest["room"]["state_material"][bit]
            count = surface_cells(dimensions, facet)
            canonical.extend(
                f"{facet}:{index}:{material}" for index in range(count)
            )
        expected_faces = [
            {
                "facet": facet,
                "state": int(bit),
                "material": manifest["room"]["state_material"][bit],
                "surface_cells": surface_cells(dimensions, facet),
            }
            for facet, bit in zip(FACETS, facet_code, strict=True)
        ]
        if entry != {
            "room_code": entry["room_code"],
            "facet_code": facet_code,
            "furniture": special,
            "furniture_geometry_known": False,
            "interior_cells_xyz": dimensions,
            "surface_cell_count": 120,
            "faces": expected_faces,
            "snapshot_sha256": canonical_hash(canonical),
        }:
            raise ValueError("D114 facet snapshot changed")
    decision = report.get("decision", {})
    payload = report.get("rir_payload", {})
    if (
        decision.get("facet_snapshots_admitted") is not True
        or decision.get("furniture_voxel_geometry_admitted") is not False
        or decision.get("minimal_real_rir_subset_fixed") is not True
        or decision.get("production_material_fit_eligible") is not False
        or decision.get("production_change_required") is not False
        or payload.get("waveform_metrics_computed") is not False
        or report.get("physical_endpoint_opened") is not False
        or report.get("captures_audio") is not False
        or report.get("release_calibrated") is not False
    ):
        raise ValueError("D114 decision or claim boundary changed")
    if payload.get("present") is False:
        if (
            payload.get("md5") is not None
            or decision.get("real_waveform_comparison_eligible") is not False
            or decision.get("next_action")
            != "materialize-hash-pinned-rir-payload"
        ):
            raise ValueError("D114 absent-payload boundary changed")
    else:
        if (
            payload.get("md5")
            != manifest["rir_payload"]["published_md5"]
            or decision.get("real_waveform_comparison_eligible") is not True
            or decision.get("next_action") != "extract-and-score-66-real-rirs"
        ):
            raise ValueError("D114 present-payload boundary changed")
    return {
        "schema_version": 1,
        "status": "verified-dechorate-facet-rir-experiment",
        "source_report_sha256": report_sha,
        "gates": {
            "official_code_hash_bound": True,
            "metadata_hash_bound": True,
            "eleven_facet_snapshots_recomputed": True,
            "furniture_special_case_not_literalized": True,
            "two_source_three_distance_selection_recomputed": True,
            "rir_extraction_count_is_66": True,
            "real_waveform_metrics_pending_payload": not payload["present"],
            "production_material_fit_rejected": True,
            "physical_endpoint_opened": False,
            "captures_audio": False,
            "release_calibrated": False,
        },
        "metrics": {
            "snapshots": len(snapshots),
            "surface_cells_per_snapshot": 120,
            "selected_sources": 2,
            "selected_microphones_per_source": 3,
            "selected_rirs": 66,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--source-directory", type=Path, required=True)
    parser.add_argument("--d113-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    result = verify(
        report,
        sha256(args.report),
        args.manifest,
        args.metadata,
        args.source_directory,
        args.d113_report,
    )
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
