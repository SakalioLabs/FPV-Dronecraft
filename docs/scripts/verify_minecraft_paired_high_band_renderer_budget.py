#!/usr/bin/env python3
"""Independent D121l paired CPU/wall and multi-source budget verifier."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any


SOURCE_COUNTS = [1, 4, 6]
TRIALS = 3
WALL_SAMPLES = 10
CPU_RATIO_LIMIT = 1.25
SIX_SOURCE_LIMIT_NS = 4_166.666666666667
BOUNDARIES = [
    "captures_audio",
    "physical_endpoint_opened",
    "minecraft_client_started",
    "client_level_read",
    "cuda_executed",
    "minecraft_integration_enabled",
    "live_early_renderer_enabled",
    "release_calibrated",
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def percentile(values: list[float], quantile: float) -> float:
    return sorted(values)[math.ceil(quantile * len(values)) - 1]


def close(actual: float, expected: float) -> bool:
    return math.isclose(
        actual, expected, rel_tol=1.0e-12, abs_tol=1.0e-12
    )


def trial_gates(trial: dict[str, Any]) -> dict[str, bool]:
    baseline_cpu = trial["baseline_cpu_ns_per_frame"]
    candidate_cpu = trial["candidate_cpu_ns_per_frame"]
    baseline_wall = trial["baseline_wall_ns_per_frame"]
    candidate_wall = trial["candidate_wall_ns_per_frame"]
    return {
        "cpu_raw_aggregate_present": len(baseline_cpu) == 1
        and len(candidate_cpu) == 1
        and baseline_cpu[0] > 0.0
        and candidate_cpu[0] > 0.0,
        "wall_raw_samples_present": len(baseline_wall) == WALL_SAMPLES
        and len(candidate_wall) == WALL_SAMPLES
        and all(value >= 0.0 for value in baseline_wall + candidate_wall),
        "baseline_cpu_percentiles_recomputed": close(
            trial["baseline_cpu_p50"], percentile(baseline_cpu, 0.50)
        )
        and close(
            trial["baseline_cpu_p95"], percentile(baseline_cpu, 0.95)
        )
        and close(
            trial["baseline_cpu_p99"], percentile(baseline_cpu, 0.99)
        ),
        "candidate_cpu_percentiles_recomputed": close(
            trial["candidate_cpu_p50"], percentile(candidate_cpu, 0.50)
        )
        and close(
            trial["candidate_cpu_p95"], percentile(candidate_cpu, 0.95)
        )
        and close(
            trial["candidate_cpu_p99"], percentile(candidate_cpu, 0.99)
        ),
        "wall_p99_recomputed": close(
            trial["baseline_wall_p99"],
            percentile(baseline_wall, 0.99),
        )
        and close(
            trial["candidate_wall_p99"],
            percentile(candidate_wall, 0.99),
        ),
        "median_cpu_ratio_recomputed": close(
            trial["median_cpu_ratio"],
            trial["candidate_cpu_p50"] / trial["baseline_cpu_p50"],
        ),
        "zero_allocation": trial["allocation_windows_bytes"]
        == [0, 0, 0, 0, 0],
        "checksums_finite": math.isfinite(trial["baseline_checksum"])
        and math.isfinite(trial["candidate_checksum"]),
    }


def verify(
    report: dict[str, Any],
    d121k: dict[str, Any],
    contract: dict[str, Any],
    contract_hash: str,
) -> dict[str, Any]:
    case_gates: dict[str, Any] = {}
    maximum_ratio = 0.0
    all_trials_valid = True
    for source_count in SOURCE_COUNTS:
        name = f"source_{source_count}"
        case = report["cases"][name]
        trials = case["trials"]
        gates = [trial_gates(trial) for trial in trials]
        all_valid = len(trials) == TRIALS and all(
            all(item.values()) for item in gates
        )
        all_trials_valid &= all_valid
        ratios = [trial["median_cpu_ratio"] for trial in trials]
        recomputed_maximum = max(ratios)
        maximum_ratio = max(maximum_ratio, recomputed_maximum)
        worst_index = max(
            range(len(trials)),
            key=lambda index: trials[index]["candidate_cpu_p50"],
        )
        case_gates[name] = {
            "identity_matches": case["source_count"] == source_count,
            "trial_count_matches": len(trials) == TRIALS,
            "trials": gates,
            "all_trials_valid": all_valid,
            "maximum_ratio_recomputed": close(
                case["maximum_median_cpu_ratio"],
                recomputed_maximum,
            ),
            "worst_trial_recomputed": case[
                "worst_candidate_median_cpu_trial_index"
            ]
            == worst_index,
        }

    six = report["cases"]["source_6"]
    six_worst = six["trials"][
        six["worst_candidate_median_cpu_trial_index"]
    ]
    six_cpu = six_worst["candidate_cpu_p95"]
    sample_period = 1_000_000_000.0 / report["sample_rate_hz"]
    ratio_gate = maximum_ratio <= CPU_RATIO_LIMIT
    six_gate = six_cpu <= SIX_SOURCE_LIMIT_NS
    allocation_gate = all(
        trial["allocation_windows_bytes"] == [0, 0, 0, 0, 0]
        for case in report["cases"].values()
        for trial in case["trials"]
    )
    checksum_gate = all(
        math.isfinite(trial["baseline_checksum"])
        and math.isfinite(trial["candidate_checksum"])
        for case in report["cases"].values()
        for trial in case["trials"]
    )
    return {
        "report_contract_hash_matches": report["source_contract_sha256"]
        == contract_hash,
        "identities_match": report["status"]
        == "valid-paired-high-band-renderer-budget"
        and report["sample_rate_hz"] == 48_000
        and report["block_size"] == contract["block_size"]
        and report["measured_pairs_per_trial"]
        == contract["measured_pairs_per_trial"]
        and report["trials"] == contract["trials"],
        "case_gates": case_gates,
        "all_case_evidence_valid": all_trials_valid
        and all(
            gate["identity_matches"]
            and gate["trial_count_matches"]
            and gate["maximum_ratio_recomputed"]
            and gate["worst_trial_recomputed"]
            for gate in case_gates.values()
        ),
        "maximum_median_cpu_ratio": maximum_ratio,
        "candidate_median_cpu_ratio_below_1_25": ratio_gate,
        "six_source_candidate_cpu_p95_ns": six_cpu,
        "six_source_cpu_share": six_cpu / sample_period,
        "six_source_candidate_cpu_p95_below_20_percent": six_gate
        and six_cpu / sample_period <= 0.20,
        "all_zero_allocation": allocation_gate,
        "all_checksums_finite": checksum_gate,
        "reported_gates_recomputed": report["gates"][
            "candidate_median_cpu_ratio_below_1_25"
        ]
        == ratio_gate
        and report["gates"][
            "six_source_candidate_cpu_p95_below_20_percent"
        ]
        == six_gate
        and report["gates"]["all_zero_allocation"] == allocation_gate
        and report["gates"]["all_checksums_finite"] == checksum_gate,
        "d121k_failure_preserved": d121k["status"]
        == contract["d121k_required_verification_status"]
        and report["gates"]["d121k_failure_preserved"] is True,
        **{boundary: report[boundary] for boundary in BOUNDARIES},
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--d121k-verification", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    d121k = json.loads(
        args.d121k_verification.read_text(encoding="utf-8")
    )
    contract = json.loads(args.contract.read_text(encoding="utf-8"))
    gates = verify(report, d121k, contract, sha256(args.contract))
    positive = [
        "report_contract_hash_matches",
        "identities_match",
        "all_case_evidence_valid",
        "candidate_median_cpu_ratio_below_1_25",
        "six_source_candidate_cpu_p95_below_20_percent",
        "all_zero_allocation",
        "all_checksums_finite",
        "reported_gates_recomputed",
        "d121k_failure_preserved",
    ]
    if not all(gates[name] for name in positive) or any(
        gates[name] for name in BOUNDARIES
    ):
        raise SystemExit(f"paired renderer verification failed: {gates}")
    result = {
        "schema_version": 1,
        "status": "verified-minecraft-paired-high-band-renderer-budget",
        "source_report_sha256": sha256(args.report),
        "source_d121k_verification_sha256": sha256(
            args.d121k_verification
        ),
        "source_contract_sha256": sha256(args.contract),
        "gates": gates,
        "selection": {
            "high_band_candidate_budget_eligible": True,
            "live_renderer_enablement_authorized": False,
            "endpoint_disabled_replay_research_authorized": True,
        },
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
