#!/usr/bin/env python3
"""Independently verify the D121 early/late energy ledger reference."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(
    report: dict[str, Any],
    report_sha: str,
    contract_sha: str,
) -> dict[str, Any]:
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "valid-minecraft-early-late-energy-ledger-reference"
        or report.get("source_contract_sha256") != contract_sha
        or len(report.get("scenarios", [])) != 3
    ):
        raise ValueError("D121 report identity changed")
    for index, scenario in enumerate(report["scenarios"]):
        budget = scenario["environment_budget"]
        candidate = scenario["explicit_candidate"]
        expected_allocated = [
            min(candidate[band], budget[band]) for band in range(3)
        ]
        expected_rejected = [
            max(0.0, candidate[band] - expected_allocated[band])
            for band in range(3)
        ]
        expected_residual = [
            max(0.0, budget[band] - expected_allocated[band])
            for band in range(3)
        ]
        for field, expected in (
            ("explicit_allocated", expected_allocated),
            ("explicit_rejected", expected_rejected),
            ("late_residual", expected_residual),
        ):
            for band in range(3):
                if not math.isclose(
                    scenario[field][band],
                    expected[band],
                    rel_tol=1.0e-12,
                    abs_tol=1.0e-12,
                ):
                    raise ValueError(
                        f"D121 scenario {index} {field} differs"
                    )
        expected_wet = min(
            0.45,
            math.sqrt(sum(expected_residual) / 3.0)
            * 0.4
            * (1.0 - scenario["openness"]),
        )
        if not math.isclose(
            scenario["late_wet_gain"],
            expected_wet,
            rel_tol=1.0e-12,
            abs_tol=1.0e-12,
        ):
            raise ValueError(f"D121 scenario {index} wet gain differs")
        for band in range(3):
            if not math.isclose(
                scenario["explicit_allocated"][band]
                + scenario["late_residual"][band],
                budget[band],
                rel_tol=1.0e-12,
                abs_tol=1.0e-12,
            ):
                raise ValueError("D121 per-band conservation failed")
    benchmark = report["benchmark"]
    gates = report["gates"]
    if (
        benchmark["partitions_per_window"] < 100_000
        or benchmark["allocation_windows_bytes"] != [0, 0, 0, 0, 0]
        or benchmark["median_allocated_bytes_per_partition"] != 0.0
        or benchmark["p99_ns_per_partition"] > 10_000.0
        or gates["three_scenarios"] is not True
        or gates["zero_allocation_hot_path"] is not True
        or gates["p99_below_10_microseconds"] is not True
    ):
        raise ValueError("D121 allocation or latency gate changed")
    if (
        report["captures_audio"] is not False
        or report["physical_endpoint_opened"] is not False
        or report["cuda_executed"] is not False
        or report["minecraft_integration_enabled"] is not False
        or report["release_calibrated"] is not False
    ):
        raise ValueError("D121 claim boundary changed")
    return {
        "schema_version": 1,
        "status": "verified-minecraft-early-late-energy-ledger-reference",
        "source_report_sha256": report_sha,
        "source_contract_sha256": contract_sha,
        "metrics": {
            "scenarios": 3,
            "allocation_windows_bytes": benchmark[
                "allocation_windows_bytes"
            ],
            "p99_ns_per_partition": benchmark["p99_ns_per_partition"],
        },
        "gates": {
            "independent_partition_recomputed": True,
            "per_band_conservation": True,
            "zero_allocation_hot_path": True,
            "minecraft_integration_enabled": False,
            "physical_endpoint_opened": False,
            "captures_audio": False,
            "cuda_executed": False,
            "release_calibrated": False,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    result = verify(report, sha256(args.report), sha256(args.contract))
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
