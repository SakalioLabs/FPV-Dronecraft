#!/usr/bin/env python3
"""Recompute and gate the D117 330-channel exploratory analysis."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

import analyze_dechorate_multichannel_generalization as analyzer


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def enforce(report: dict[str, Any]) -> None:
    audit = report["channel_audit"]
    split = report["split_contract"]
    model = report["model_screen"]
    metrics = model["partition_metrics"]
    decision = report["decision"]
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "valid-dechorate-multichannel-generalization"
        or audit["sofa_files"] != 66
        or audit["channels_per_file"] != 5
        or audit["measured_rirs"] != 330
        or audit["unique_waveform_hashes"] != 330
        or audit["metadata_coordinate_bindings"] != 330
        or audit["stale_room_descriptions"] != 66
        or audit["direct_shift_max_samples"] > 8
    ):
        raise ValueError("D117 multichannel identity gate changed")
    if (
        split["confirmatory_holdout_eligible"] is not False
        or not split["contamination_reason"]
        or len(split["position_discovery"]) != 8
        or len(split["position_validation"]) != 7
        or set(split["position_discovery"])
        & set(split["position_validation"])
    ):
        raise ValueError("D117 split or contamination disclosure changed")
    if (
        model["selected_family"] != "geometry-interaction"
        or model["selected_regularization"] != 0.1
        or model["selected_feature_count"] != 420
        or len(model["frozen_model"]["coefficients_normalized"]) != 420
        or len(model["frozen_model"]["feature_scale"]) != 420
        or len(model["frozen_model_sha256"]) != 64
        or model["zero_state_max_abs_prediction_db"] != 0.0
        or model["per_microphone_coefficients"] is not False
        or model["production_generalizable"] is not False
        or metrics["fit"]["rows"] != 384
        or metrics["position_validation"]["rows"] != 336
        or metrics["room_validation"]["rows"] != 144
        or metrics["joint_exploratory"]["rows"] != 126
        or metrics["position_validation"]["mean_top4_overlap"] < 0.8
        or metrics["joint_exploratory"]["mean_top4_overlap"] < 0.8
        or metrics["joint_exploratory"]["top1_capture_rate"] < 0.9
        or metrics["joint_exploratory"]["rmse_db"] <= 3.0
    ):
        raise ValueError("D117 model screen or exploratory result changed")
    if (
        decision["three_hundred_thirty_rirs_admitted"] is not True
        or decision[
            "position_general_features_admitted_for_further_study"
        ] is not True
        or decision["joint_result_confirmatory"] is not False
        or decision["amplitude_rmse_gate_passed"] is not False
        or decision["production_candidate_model_eligible"] is not False
        or decision["production_change_required"] is not False
        or report["captures_audio"] is not False
        or report["physical_endpoint_opened"] is not False
        or report["release_calibrated"] is not False
    ):
        raise ValueError("D117 decision or claim boundary changed")


def verify(
    report: dict[str, Any],
    report_sha: str,
    subset_path: Path,
    pins_path: Path,
    inventory_path: Path,
    metadata_path: Path,
    sofa_directory: Path,
    d116_path: Path,
) -> dict[str, Any]:
    bindings = {
        "source_subset_manifest_sha256": sha256(subset_path),
        "source_pins_sha256": sha256(pins_path),
        "source_inventory_sha256": sha256(inventory_path),
        "source_metadata_sha256": sha256(metadata_path),
        "source_d116_report_sha256": sha256(d116_path),
    }
    if any(report.get(key) != value for key, value in bindings.items()):
        raise ValueError("D117 source binding changed")
    reconstructed = analyzer.analyze(
        subset_path,
        pins_path,
        inventory_path,
        metadata_path,
        sofa_directory,
        d116_path,
    )
    if reconstructed != report:
        changed = sorted(
            key
            for key in set(report) | set(reconstructed)
            if report.get(key) != reconstructed.get(key)
        )
        raise ValueError(f"D117 reconstructed report changed: {changed}")
    enforce(report)
    metrics = report["model_screen"]["partition_metrics"]
    return {
        "schema_version": 1,
        "status": "verified-dechorate-multichannel-generalization",
        "source_report_sha256": report_sha,
        "gates": {
            "three_hundred_thirty_channels_hash_bound": True,
            "all_waveforms_unique": True,
            "metadata_coordinates_recomputed": True,
            "no_per_microphone_coefficients": True,
            "position_validation_rank_gate_passed": True,
            "joint_exploratory_rank_gate_passed": True,
            "joint_amplitude_rmse_gate_failed": True,
            "contaminated_holdout_disclosed": True,
            "confirmatory_claim_rejected": True,
            "production_candidate_rejected": True,
            "physical_endpoint_opened": False,
            "captures_audio": False,
            "release_calibrated": False,
        },
        "metrics": {
            "measured_rirs": report["channel_audit"]["measured_rirs"],
            "position_validation_top4_overlap": metrics[
                "position_validation"
            ]["mean_top4_overlap"],
            "joint_exploratory_top4_overlap": metrics[
                "joint_exploratory"
            ]["mean_top4_overlap"],
            "joint_exploratory_top1_capture_rate": metrics[
                "joint_exploratory"
            ]["top1_capture_rate"],
            "joint_exploratory_rmse_db": metrics["joint_exploratory"][
                "rmse_db"
            ],
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--subset-manifest", type=Path, required=True)
    parser.add_argument("--pins", type=Path, required=True)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--sofa-directory", type=Path, required=True)
    parser.add_argument("--d116-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    result = verify(
        report,
        sha256(args.report),
        args.subset_manifest,
        args.pins,
        args.inventory,
        args.metadata,
        args.sofa_directory,
        args.d116_report,
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
