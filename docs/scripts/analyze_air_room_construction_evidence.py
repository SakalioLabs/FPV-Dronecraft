#!/usr/bin/env python3
"""Audit AIR construction identifiability and screen dEchorate metadata."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any


REQUIRED_DECHORATE_COLUMNS = {
    "filename",
    "src_id",
    "src_type",
    "src_pos_x",
    "src_pos_y",
    "src_pos_z",
    "room_code",
    "room_rfl_floor",
    "room_rfl_ceiling",
    "room_rfl_west",
    "room_rfl_south",
    "room_rfl_east",
    "room_rfl_north",
    "room_fornitures",
    "room_temperature",
    "mic_id",
    "mic_pos_x",
    "mic_pos_y",
    "mic_pos_z",
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_file(path: Path, specification: dict[str, Any], label: str) -> None:
    if (
        not path.is_file()
        or path.stat().st_size != specification["bytes"]
        or sha256(path) != specification["sha256"]
    ):
        raise ValueError(f"D113 {label} hash or size changed")


def analyze_metadata(path: Path, specification: dict[str, Any]) -> dict[str, Any]:
    with path.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames is None or not REQUIRED_DECHORATE_COLUMNS.issubset(
            reader.fieldnames
        ):
            raise ValueError("D113 dEchorate metadata columns changed")
        rows = list(reader)
    room_counts = Counter(row["room_code"] for row in rows)
    room_codes = sorted(room_counts)
    if (
        len(rows) != specification["expected_rows"]
        or room_codes != specification["expected_room_codes"]
        or any(
            count != specification["expected_rows_per_room_code"]
            for count in room_counts.values()
        )
    ):
        raise ValueError("D113 dEchorate corpus shape changed")
    reflectivity_columns = [
        "room_rfl_floor",
        "room_rfl_ceiling",
        "room_rfl_west",
        "room_rfl_south",
        "room_rfl_east",
        "room_rfl_north",
    ]
    if any(
        row[column] not in {"0.0", "1.0", "2.0"}
        for row in rows
        for column in reflectivity_columns
    ):
        raise ValueError("D113 dEchorate facet state changed")
    loopback_rows = sum(
        row["mic_type"] == "loopback"
        and not any(
            row[field]
            for field in ("mic_pos_x", "mic_pos_y", "mic_pos_z")
        )
        for row in rows
    )
    if (
        loopback_rows
        != specification[
            "expected_loopback_rows_without_spatial_coordinates"
        ]
    ):
        raise ValueError("D113 dEchorate loopback contract changed")
    return {
        "rows": len(rows),
        "room_codes": room_codes,
        "rows_per_room_code": dict(sorted(room_counts.items())),
        "unique_source_ids": sorted(
            {row["src_id"] for row in rows if row["src_id"]}
        ),
        "unique_microphone_ids": sorted(
            {row["mic_id"] for row in rows if row["mic_id"]}
        ),
        "source_types": sorted(
            {row["src_type"] for row in rows if row["src_type"]}
        ),
        "furniture_states": sorted(
            {row["room_fornitures"] for row in rows}
        ),
        "facet_state_columns": reflectivity_columns,
        "all_rows_have_room_temperature": all(
            bool(row["room_temperature"]) for row in rows
        ),
        "all_non_silence_sources_have_coordinates": all(
            row["src_type"] == "silence"
            or all(row[field] for field in ("src_pos_x", "src_pos_y", "src_pos_z"))
            for row in rows
        ),
        "physical_microphone_rows": sum(
            row["mic_type"] != "loopback" for row in rows
        ),
        "loopback_rows_without_spatial_coordinates": loopback_rows,
        "all_physical_microphones_have_coordinates": all(
            row["mic_type"] == "loopback"
            or all(
                row[field]
                for field in ("mic_pos_x", "mic_pos_y", "mic_pos_z")
            )
            for row in rows
        ),
    }


def analyze(
    manifest_path: Path,
    paper_path: Path,
    metadata_path: Path,
    shoebox_path: Path,
    d112_path: Path,
) -> dict[str, Any]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    shoebox = json.loads(shoebox_path.read_text(encoding="utf-8"))
    d112 = json.loads(d112_path.read_text(encoding="utf-8"))
    if manifest.get("schema_version") != 1:
        raise ValueError("D113 manifest schema changed")
    validate_file(paper_path, manifest["air_paper"], "AIR paper")
    validate_file(metadata_path, manifest["dechorate_metadata"], "metadata")
    if (
        shoebox.get("status") != "valid-diagnostic"
        or d112.get("status") != "valid-ptb-broadband-product-screen"
        or d112.get("decision", {}).get("basis_extension_selected") is not False
    ):
        raise ValueError("D113 source policy changed")
    source_rooms = {room["id"]: room for room in shoebox["rooms"]}
    rooms = []
    for room in manifest["air_rooms"]:
        source = source_rooms.get(room["id"])
        if source is None:
            raise ValueError(f"D113 missing AIR source room {room['id']}")
        observed_dimensions = source["physical_dimensions_m"]
        expected_dimensions = dict(
            zip(("length", "width", "height"), room["dimensions_m"], strict=True)
        )
        if observed_dimensions != expected_dimensions:
            raise ValueError(f"D113 {room['id']} dimensions changed")
        missing = [
            "ceiling_surface",
            "octave_absorption_coefficients",
            "octave_scattering_coefficients",
            "complete_surface_area_fractions",
        ]
        if room["furniture"] and not room["furniture_absence_explicit"]:
            missing.append("furniture_count_and_exposed_area")
        rooms.append(
            {
                "id": room["id"],
                "paper_label": room["paper_label"],
                "dimensions_m": room["dimensions_m"],
                "wall_surface": room["wall_surface"],
                "wall_surface_counts": room["wall_surface_counts"],
                "floor_cover": room["floor_cover"],
                "furniture": room["furniture"],
                "furniture_absence_explicit": room[
                    "furniture_absence_explicit"
                ],
                "semantic_material_classes": room[
                    "semantic_material_classes"
                ],
                "known": {
                    "room_dimensions": True,
                    "wall_material_semantics": True,
                    "floor_material_semantics": True,
                    "furniture_semantics_or_explicit_absence": True,
                },
                "missing_for_material_calibration": missing,
                "semantic_scene_constraint_eligible": True,
                "material_parameter_fit_eligible": False,
            }
        )
    metadata = analyze_metadata(metadata_path, manifest["dechorate_metadata"])
    return {
        "schema_version": 1,
        "status": "valid-air-room-construction-evidence",
        "source_manifest_sha256": sha256(manifest_path),
        "source_air_paper_sha256": sha256(paper_path),
        "source_dechorate_metadata_sha256": sha256(metadata_path),
        "source_air_shoebox_sha256": sha256(shoebox_path),
        "source_d112_report_sha256": sha256(d112_path),
        "air_paper": {
            "doi": manifest["air_paper"]["doi"],
            "table": manifest["air_paper"]["table"],
            "evidence_page_1_based": manifest["air_paper"][
                "evidence_page_1_based"
            ],
            "figure": manifest["air_paper"]["figure"],
            "figure_page_1_based": manifest["air_paper"][
                "figure_page_1_based"
            ],
            "visual_page_reviewed": True,
        },
        "air_rooms": rooms,
        "dechorate_metadata": metadata,
        "decision": {
            "air_semantic_scene_constraints_eligible": True,
            "air_material_parameter_fit_eligible": False,
            "d112_unconstrained_product_search_remains_rejected": True,
            "next_controlled_reference": "dechorate-v2-metadata-first",
            "next_action": (
                "Use the 2.9 MB metadata to construct exact facet-state "
                "snapshots, then fetch only the minimum RIR subset needed for "
                "real-vs-voxel calibration."
            ),
            "full_dechorate_payload_required_now": False,
            "production_change_required": False,
        },
        "physical_endpoint_opened": False,
        "captures_audio": False,
        "release_calibrated": False,
        "claim_boundary": manifest["claim_boundary"],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--paper", type=Path, required=True)
    parser.add_argument("--dechorate-metadata", type=Path, required=True)
    parser.add_argument("--shoebox-report", type=Path, required=True)
    parser.add_argument("--d112-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = analyze(
        args.manifest,
        args.paper,
        args.dechorate_metadata,
        args.shoebox_report,
        args.d112_report,
    )
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(
        json.dumps(
            {
                "status": report["status"],
                "air_rooms": len(report["air_rooms"]),
                "dechorate_rows": report["dechorate_metadata"]["rows"],
                "air_material_parameter_fit_eligible": False,
                "output": str(args.output_json),
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
