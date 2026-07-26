#!/usr/bin/env python3
"""Independently verify D113 AIR inventory and dEchorate screening."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any


EXPECTED_AIR = {
    "air-booth": {
        "paper_label": "Studio booth",
        "dimensions_m": [3.0, 1.8, 2.2],
        "wall_surface": ["custom-made low-reflective panels"],
        "wall_surface_counts": None,
        "floor_cover": "carpet",
        "furniture": [],
        "furniture_absence_explicit": True,
        "semantic_material_classes": ["engineered-acoustic-panel", "carpet"],
        "missing": [
            "ceiling_surface",
            "octave_absorption_coefficients",
            "octave_scattering_coefficients",
            "complete_surface_area_fractions",
        ],
    },
    "air-lecture": {
        "paper_label": "Lecture room",
        "dimensions_m": [10.8, 10.9, 3.15],
        "wall_surface": ["glass windows", "concrete wall"],
        "wall_surface_counts": [3, 1],
        "floor_cover": "parquet",
        "furniture": ["wooden tables", "chairs"],
        "furniture_absence_explicit": False,
        "semantic_material_classes": [
            "glass",
            "concrete",
            "wood-floor",
            "table",
            "chair",
        ],
        "missing": [
            "ceiling_surface",
            "octave_absorption_coefficients",
            "octave_scattering_coefficients",
            "complete_surface_area_fractions",
            "furniture_count_and_exposed_area",
        ],
    },
}
REFLECTIVITY_COLUMNS = [
    "room_rfl_floor",
    "room_rfl_ceiling",
    "room_rfl_west",
    "room_rfl_south",
    "room_rfl_east",
    "room_rfl_north",
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def metadata_metrics(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    counts = Counter(row["room_code"] for row in rows)
    return {
        "rows": len(rows),
        "room_codes": sorted(counts),
        "rows_per_room_code": dict(sorted(counts.items())),
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
        "facet_state_columns": REFLECTIVITY_COLUMNS,
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
        "loopback_rows_without_spatial_coordinates": sum(
            row["mic_type"] == "loopback"
            and not any(
                row[field]
                for field in ("mic_pos_x", "mic_pos_y", "mic_pos_z")
            )
            for row in rows
        ),
        "all_physical_microphones_have_coordinates": all(
            row["mic_type"] == "loopback"
            or all(
                row[field]
                for field in ("mic_pos_x", "mic_pos_y", "mic_pos_z")
            )
            for row in rows
        ),
    }


def verify(
    report: dict[str, Any],
    report_sha: str,
    manifest_path: Path,
    paper_path: Path,
    metadata_path: Path,
    shoebox_path: Path,
    d112_path: Path,
) -> dict[str, Any]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    shoebox = json.loads(shoebox_path.read_text(encoding="utf-8"))
    d112 = json.loads(d112_path.read_text(encoding="utf-8"))
    bindings = {
        "source_manifest_sha256": sha256(manifest_path),
        "source_air_paper_sha256": sha256(paper_path),
        "source_dechorate_metadata_sha256": sha256(metadata_path),
        "source_air_shoebox_sha256": sha256(shoebox_path),
        "source_d112_report_sha256": sha256(d112_path),
    }
    if (
        report.get("schema_version") != 1
        or report.get("status") != "valid-air-room-construction-evidence"
        or any(report.get(key) != value for key, value in bindings.items())
    ):
        raise ValueError("D113 report shape or source binding changed")
    if (
        manifest.get("air_paper", {}).get("sha256") != sha256(paper_path)
        or manifest.get("air_paper", {}).get("bytes") != paper_path.stat().st_size
        or manifest.get("dechorate_metadata", {}).get("sha256")
        != sha256(metadata_path)
        or manifest.get("dechorate_metadata", {}).get("bytes")
        != metadata_path.stat().st_size
    ):
        raise ValueError("D113 pinned external evidence changed")
    if (
        shoebox.get("status") != "valid-diagnostic"
        or d112.get("status") != "valid-ptb-broadband-product-screen"
        or d112.get("decision", {}).get("basis_extension_selected") is not False
    ):
        raise ValueError("D113 source policy changed")
    paper = report.get("air_paper")
    if paper != {
        "doi": "10.1109/ICDSP.2009.5201259",
        "table": 1,
        "evidence_page_1_based": 3,
        "figure": 3,
        "figure_page_1_based": 4,
        "visual_page_reviewed": True,
    }:
        raise ValueError("D113 paper evidence locator changed")
    rooms = report.get("air_rooms")
    if (
        not isinstance(rooms, list)
        or [room.get("id") for room in rooms]
        != ["air-booth", "air-lecture"]
    ):
        raise ValueError("D113 AIR room order changed")
    source_rooms = {room["id"]: room for room in shoebox["rooms"]}
    for room in rooms:
        expected = EXPECTED_AIR[room["id"]]
        for key in (
            "paper_label",
            "dimensions_m",
            "wall_surface",
            "wall_surface_counts",
            "floor_cover",
            "furniture",
            "furniture_absence_explicit",
            "semantic_material_classes",
        ):
            if room.get(key) != expected[key]:
                raise ValueError(f"D113 {room['id']} {key} changed")
        expected_dimensions = dict(
            zip(
                ("length", "width", "height"),
                expected["dimensions_m"],
                strict=True,
            )
        )
        if source_rooms[room["id"]]["physical_dimensions_m"] != expected_dimensions:
            raise ValueError(f"D113 {room['id']} source dimensions changed")
        if room.get("missing_for_material_calibration") != expected["missing"]:
            raise ValueError(f"D113 {room['id']} missing evidence changed")
        if (
            room.get("semantic_scene_constraint_eligible") is not True
            or room.get("material_parameter_fit_eligible") is not False
            or room.get("known") != {
                "room_dimensions": True,
                "wall_material_semantics": True,
                "floor_material_semantics": True,
                "furniture_semantics_or_explicit_absence": True,
            }
        ):
            raise ValueError(f"D113 {room['id']} eligibility changed")
    expected_metadata = metadata_metrics(metadata_path)
    if report.get("dechorate_metadata") != expected_metadata:
        raise ValueError("D113 dEchorate metadata metrics changed")
    metadata_spec = manifest["dechorate_metadata"]
    if (
        expected_metadata["rows"] != metadata_spec["expected_rows"]
        or expected_metadata["room_codes"]
        != metadata_spec["expected_room_codes"]
        or any(
            count != metadata_spec["expected_rows_per_room_code"]
            for count in expected_metadata["rows_per_room_code"].values()
        )
        or expected_metadata["furniture_states"] != ["False", "True"]
        or not expected_metadata["all_rows_have_room_temperature"]
        or not expected_metadata[
            "all_non_silence_sources_have_coordinates"
        ]
        or not expected_metadata[
            "all_physical_microphones_have_coordinates"
        ]
        or expected_metadata["loopback_rows_without_spatial_coordinates"]
        != metadata_spec[
            "expected_loopback_rows_without_spatial_coordinates"
        ]
    ):
        raise ValueError("D113 dEchorate eligibility gate failed")
    decision = report.get("decision", {})
    if (
        decision.get("air_semantic_scene_constraints_eligible") is not True
        or decision.get("air_material_parameter_fit_eligible") is not False
        or decision.get(
            "d112_unconstrained_product_search_remains_rejected"
        ) is not True
        or decision.get("next_controlled_reference")
        != "dechorate-v2-metadata-first"
        or decision.get("full_dechorate_payload_required_now") is not False
        or decision.get("production_change_required") is not False
        or report.get("physical_endpoint_opened") is not False
        or report.get("captures_audio") is not False
        or report.get("release_calibrated") is not False
    ):
        raise ValueError("D113 decision or claim boundary changed")
    return {
        "schema_version": 1,
        "status": "verified-air-room-construction-evidence",
        "source_report_sha256": report_sha,
        "gates": {
            "air_table_1_inventory_hash_bound": True,
            "air_dimensions_cross_checked": True,
            "semantic_constraints_admitted": True,
            "material_fit_rejected_as_underidentified": True,
            "d112_target_search_remains_rejected": True,
            "dechorate_metadata_recomputed": True,
            "dechorate_selected_metadata_first": True,
            "full_dechorate_payload_not_required": True,
            "production_change_required": False,
            "physical_endpoint_opened": False,
            "captures_audio": False,
            "release_calibrated": False,
        },
        "metrics": {
            "air_rooms": 2,
            "dechorate_rows": expected_metadata["rows"],
            "dechorate_room_codes": len(expected_metadata["room_codes"]),
            "dechorate_microphone_ids": len(
                expected_metadata["unique_microphone_ids"]
            ),
            "dechorate_source_ids": len(expected_metadata["unique_source_ids"]),
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--paper", type=Path, required=True)
    parser.add_argument("--dechorate-metadata", type=Path, required=True)
    parser.add_argument("--shoebox-report", type=Path, required=True)
    parser.add_argument("--d112-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    result = verify(
        report,
        sha256(args.report),
        args.manifest,
        args.paper,
        args.dechorate_metadata,
        args.shoebox_report,
        args.d112_report,
    )
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
