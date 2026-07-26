#!/usr/bin/env python3
"""Reconstruct and verify the preserved D118 confirmatory result."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import analyze_dechorate_confirmatory_holdout as analyzer


def enforce(report: dict[str, Any]) -> None:
    audit = report["channel_audit"]
    model = report["frozen_model"]
    metrics = report["confirmatory_metrics"]
    thresholds = report["thresholds"]
    expected_gates = analyzer.gate_metrics(metrics, thresholds)
    gates = report["gates"]
    decision = report["decision"]
    contract = report["unblinding_contract"]
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "preserved-dechorate-confirmatory-result"
        or audit["sofa_files"] != 33
        or audit["channels_per_file"] != 5
        or audit["measured_rirs"] != 165
        or audit["unique_waveform_hashes"] != 165
        or audit["waveform_hash_overlap_with_d117"] != 0
        or audit["metadata_coordinate_bindings"] != 165
        or len(audit["microphone_ids"]) != 15
        or metrics["rows"] != 990
        or metrics["groups"] != 165
    ):
        raise ValueError("D118 channel identity gate changed")
    if (
        model["family"] != "geometry-interaction"
        or model["regularization"] != 0.1
        or model["feature_count"] != 420
        or len(model["frozen_model_sha256"]) != 64
        or model["per_microphone_coefficients"] is not False
        or model["refit_performed"] is not False
        or thresholds
        != {
            "top4_overlap_minimum": 0.8,
            "top1_capture_rate_minimum": 0.9,
            "amplitude_rmse_db_maximum": 3.0,
        }
    ):
        raise ValueError("D118 frozen model or threshold changed")
    if gates != expected_gates:
        raise ValueError("D118 metric gate decision is inconsistent")
    if (
        not all(contract.values())
        or decision["confirmatory_result_eligible"] is not True
        or decision["rank_model_admitted_for_further_integration"]
        is not gates["rank_gate_passed"]
        or decision["amplitude_model_admitted"]
        is not gates["amplitude_rmse_gate_passed"]
        or decision["production_candidate_model_eligible"] is not False
        or decision["production_change_required"] is not False
        or report["cuda_executed"] is not False
        or report["captures_audio"] is not False
        or report["physical_endpoint_opened"] is not False
        or report["release_calibrated"] is not False
    ):
        raise ValueError("D118 policy or claim boundary changed")


def verify(
    report: dict[str, Any],
    report_sha: str,
    preregistration_path: Path,
    drive_manifest_path: Path,
    inventory_pins_path: Path,
    metadata_path: Path,
    sofa_directory: Path,
    d117_path: Path,
    annotation_pins_path: Path,
    annotation_directory: Path,
) -> dict[str, Any]:
    inventory_pins = json.loads(
        inventory_pins_path.read_text(encoding="utf-8")
    )
    bindings = {
        "source_preregistration_sha256": analyzer.sha256(
            preregistration_path
        ),
        "source_drive_manifest_sha256": analyzer.sha256(
            drive_manifest_path
        ),
        "source_inventory_pins_sha256": analyzer.sha256(
            inventory_pins_path
        ),
        "source_metadata_sha256": analyzer.sha256(metadata_path),
        "source_d117_report_sha256": inventory_pins[
            "source_d117_report_sha256"
        ],
        "source_annotation_pins_sha256": analyzer.sha256(
            annotation_pins_path
        ),
    }
    if any(report.get(key) != value for key, value in bindings.items()):
        raise ValueError("D118 report source binding changed")
    reconstructed = analyzer.analyze(
        preregistration_path,
        drive_manifest_path,
        inventory_pins_path,
        metadata_path,
        sofa_directory,
        d117_path,
        annotation_pins_path,
        annotation_directory,
    )
    if reconstructed != report:
        changed = sorted(
            key
            for key in set(report) | set(reconstructed)
            if report.get(key) != reconstructed.get(key)
        )
        raise ValueError(f"D118 reconstructed report changed: {changed}")
    enforce(report)
    return {
        "schema_version": 1,
        "status": "verified-dechorate-confirmatory-result",
        "source_report_sha256": report_sha,
        "gates": {
            "one_hundred_sixty_five_unseen_rirs_hash_bound": True,
            "all_waveforms_unique": True,
            "no_d117_waveform_overlap": True,
            "metadata_coordinates_recomputed": True,
            "frozen_model_identity_verified": True,
            "no_refit": True,
            "thresholds_unchanged": True,
            "rank_gate_passed": report["gates"]["rank_gate_passed"],
            "amplitude_rmse_gate_passed": report["gates"][
                "amplitude_rmse_gate_passed"
            ],
            "production_candidate_rejected": True,
            "cuda_executed": False,
            "physical_endpoint_opened": False,
            "captures_audio": False,
            "release_calibrated": False,
        },
        "metrics": report["confirmatory_metrics"],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--preregistration", type=Path, required=True)
    parser.add_argument("--drive-manifest", type=Path, required=True)
    parser.add_argument("--inventory-pins", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--sofa-directory", type=Path, required=True)
    parser.add_argument("--d117-report", type=Path, required=True)
    parser.add_argument("--annotation-pins", type=Path, required=True)
    parser.add_argument("--annotation-directory", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    result = verify(
        report,
        analyzer.sha256(args.report),
        args.preregistration,
        args.drive_manifest,
        args.inventory_pins,
        args.metadata,
        args.sofa_directory,
        args.d117_report,
        args.annotation_pins,
        args.annotation_directory,
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
