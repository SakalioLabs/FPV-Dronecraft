#!/usr/bin/env python3
"""Recompute D115 measured-SOFA analysis and enforce claim boundaries."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

import analyze_dechorate_sofa_subset as analyzer


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify(
    report: dict[str, Any],
    report_sha: str,
    subset_manifest_path: Path,
    pins_path: Path,
    inventory_path: Path,
    d114_path: Path,
    sofa_directory: Path,
) -> dict[str, Any]:
    bindings = {
        "source_subset_manifest_sha256": sha256(subset_manifest_path),
        "source_pins_sha256": sha256(pins_path),
        "source_inventory_sha256": sha256(inventory_path),
        "source_d114_report_sha256": sha256(d114_path),
    }
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "valid-dechorate-measured-sofa-analysis"
        or any(report.get(key) != value for key, value in bindings.items())
    ):
        raise ValueError("D115 report shape or source binding changed")
    reconstructed = analyzer.analyze(
        subset_manifest_path,
        pins_path,
        inventory_path,
        d114_path,
        sofa_directory,
    )
    if report != reconstructed:
        changed = sorted(
            key
            for key in set(report) | set(reconstructed)
            if report.get(key) != reconstructed.get(key)
        )
        raise ValueError(f"D115 reconstructed report changed: {changed}")
    geometry = report["geometry_summary"]
    direct = report["direct_path_summary"]
    identity = report["sofa_identity_audit"]
    correlations = report["room_correlations"]
    room = report["room_summary"]
    echo = report["echo_summary"]
    decisions = report["decision"]
    if (
        report["measured_rirs"] != 66
        or report["measured_audio_bytes"] != 115271864
        or identity["title_matches_filename"] != 66
        or identity["stale_room_description_count"] != 66
        or identity["room_identity_basis"]
        != "hash-pinned public-folder filename and Drive id"
    ):
        raise ValueError("D115 measured corpus or identity audit changed")
    if (
        geometry["paths"] != 21
        or geometry["physical_absolute_residual_max_samples"] > 5.0
        or geometry["voxel_absolute_residual_max_samples"] < 50.0
        or direct[
            "absolute_shift_vs_absorptive_baseline_max_samples"
        ] > 8
    ):
        raise ValueError("D115 geometry or direct-path evidence changed")
    for center in ("500", "1000", "2000"):
        correlation = correlations[center]
        absorptive = room["000000"]["bands"][center]["t20_median_s"]
        reflective = room["011111"]["bands"][center]["t20_median_s"]
        if (
            correlation["reflective_count_t20_spearman"] < 0.8
            or absorptive is None
            or reflective is None
            or reflective <= absorptive
        ):
            raise ValueError("D115 measured decay trend changed")
    separable = [
        value["reflective_vs_absorptive_auc"]
        for value in echo.values()
        if value["reflective_vs_absorptive_auc"] is not None
    ]
    if (
        echo["ceiling"]["reflective_vs_absorptive_auc"] < 0.95
        or all(value >= 0.6 for value in separable)
    ):
        raise ValueError("D115 facet identifiability evidence changed")
    if (
        decisions.get("measured_rir_subset_materialized") is not True
        or decisions.get("measured_rir_metrics_admitted") is not True
        or decisions.get("sofa_room_description_admitted") is not False
        or decisions.get(
            "filename_and_drive_id_room_identity_admitted"
        ) is not True
        or decisions.get("furniture_causal_effect_identified") is not False
        or decisions.get("production_material_fit_eligible") is not False
        or decisions.get("production_change_required") is not False
        or report.get("physical_endpoint_opened") is not False
        or report.get("captures_audio") is not False
        or report.get("release_calibrated") is not False
    ):
        raise ValueError("D115 decision or claim boundary changed")
    return {
        "schema_version": 1,
        "status": "verified-dechorate-measured-sofa-analysis",
        "source_report_sha256": report_sha,
        "gates": {
            "sixty_six_sofa_files_hash_bound": True,
            "measured_waveforms_recomputed": True,
            "stale_room_descriptions_rejected": True,
            "continuous_geometry_within_five_samples": True,
            "one_metre_voxel_error_exceeds_fifty_samples": True,
            "direct_path_shift_bounded_to_eight_samples": True,
            "reflective_count_decay_trend_present": True,
            "facet_amplitude_not_universally_identifiable": True,
            "furniture_causal_effect_not_identified": True,
            "production_material_fit_rejected": True,
            "physical_endpoint_opened": False,
            "captures_audio": False,
            "release_calibrated": False,
        },
        "metrics": {
            "measured_rirs": report["measured_rirs"],
            "measured_audio_bytes": report["measured_audio_bytes"],
            "physical_geometry_max_residual_samples": geometry[
                "physical_absolute_residual_max_samples"
            ],
            "voxel_geometry_max_residual_samples": geometry[
                "voxel_absolute_residual_max_samples"
            ],
            "direct_shift_max_samples": direct[
                "absolute_shift_vs_absorptive_baseline_max_samples"
            ],
            "t20_spearman": {
                center: correlations[center][
                    "reflective_count_t20_spearman"
                ]
                for center in ("500", "1000", "2000")
            },
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--subset-manifest", type=Path, required=True)
    parser.add_argument("--pins", type=Path, required=True)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--d114-report", type=Path, required=True)
    parser.add_argument("--sofa-directory", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    result = verify(
        report,
        sha256(args.report),
        args.subset_manifest,
        args.pins,
        args.inventory,
        args.d114_report,
        args.sofa_directory,
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
