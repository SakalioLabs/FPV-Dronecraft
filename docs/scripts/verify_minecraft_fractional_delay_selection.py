#!/usr/bin/env python3
"""Independent D121j fractional-delay selection and Java benchmark verifier."""

from __future__ import annotations

import argparse
import cmath
import hashlib
import json
import math
from pathlib import Path
from typing import Any


RATE = 48_000
MUS = [0.1, 0.25, 0.5, 0.75, 0.9]
NAMES = ["linear", "lagrange3", "thiran1", "thiran2"]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fd_response(name: str, mu: float, w: float) -> complex:
    delay = cmath.exp(-1j * w)
    if name == "linear":
        return 1.0 - mu + mu * delay
    if name == "lagrange3":
        u = 1.0 - mu
        coefficients = [
            -u * (u - 1) * (u - 2) / 6,
            (u + 1) * (u - 1) * (u - 2) / 2,
            -(u + 1) * u * (u - 2) / 2,
            (u + 1) * u * (u - 1) / 6,
        ]
        return (
            coefficients[0] * delay**2
            + coefficients[1] * delay
            + coefficients[2]
            + coefficients[3] / delay
        )
    if name == "thiran1":
        a = (1.0 - mu) / (1.0 + mu)
        return (a + delay) / (1.0 + a * delay)
    total = 1.0 + mu
    a1 = -2.0 * (total - 2.0) / (total + 1.0)
    a2 = (
        (total - 2.0)
        * (total - 1.0)
        / ((total + 1.0) * (total + 2.0))
    )
    full = (a2 + a1 * delay + delay**2) / (
        1.0 + a1 * delay + a2 * delay**2
    )
    return full / delay


def recompute_static(name: str, high_hz: float) -> dict[str, float]:
    magnitude = 0.0
    phase = 0.0
    for mu in MUS:
        for step in range(1, 401):
            w = 2.0 * math.pi * high_hz * step / 400.0 / RATE
            actual = fd_response(name, mu, w)
            ideal = cmath.exp(-1j * w * mu)
            magnitude = max(
                magnitude,
                abs(20.0 * math.log10(max(abs(actual), 1.0e-15))),
            )
            phase = max(
                phase,
                abs(math.degrees(cmath.phase(actual / ideal))),
            )
    return {
        "maximum_abs_magnitude_error_db": magnitude,
        "maximum_abs_wrapped_phase_error_degrees": phase,
    }


def make_signal(length: int) -> list[float]:
    frequencies = [180, 360, 720, 1440, 2880, 5760, 9000, 12000]
    state = 0x1234ABCD
    result: list[float] = []
    for n in range(length):
        tone = sum(
            math.sin(2.0 * math.pi * f * n / RATE)
            for f in frequencies
        ) / 8.0
        state ^= (state << 13) & 0xFFFFFFFF
        state ^= state >> 17
        state ^= (state << 5) & 0xFFFFFFFF
        noise = (state & 0xFFFF) / 32767.5 - 1.0
        result.append(0.7 * tone + 0.3 * noise)
    return result


def fractional_position(n: int) -> float:
    phase = (n % 4000) / 4000.0
    triangle = 2 * phase if phase < 0.5 else 2 * (1 - phase)
    mu = 0.05 + 0.90 * triangle
    return 1.0 - mu if n >= 12_000 else mu


def linear_sample(signal: list[float], position: float) -> float:
    lower = math.floor(position)
    part = position - lower
    return signal[lower] + (signal[lower + 1] - signal[lower]) * part


def cubic_sample(signal: list[float], position: float) -> float:
    lower = math.floor(position)
    u = position - lower
    c0 = -u * (u - 1) * (u - 2) / 6
    c1 = (u + 1) * (u - 1) * (u - 2) / 2
    c2 = -(u + 1) * u * (u - 2) / 2
    c3 = (u + 1) * u * (u - 1) / 6
    return (
        signal[lower - 1] * c0
        + signal[lower] * c1
        + signal[lower + 1] * c2
        + signal[lower + 2] * c3
    )


def sinc_sample(signal: list[float], position: float) -> float:
    center = math.floor(position)
    total = 0.0
    normalization = 0.0
    for index in range(center - 15, center + 17):
        distance = position - index
        if distance == 0:
            weight = 1.0
        elif abs(distance) >= 16:
            weight = 0.0
        else:
            weight = (
                math.sin(math.pi * distance)
                / (math.pi * distance)
                * math.sin(math.pi * distance / 16)
                / (math.pi * distance / 16)
            )
        total += signal[index] * weight
        normalization += weight
    return total / normalization


def recompute_dynamic() -> dict[str, dict[str, float | bool]]:
    signal = make_signal(25_000)
    reference: list[float] = []
    output = {name: [] for name in NAMES}
    t1_x = t1_y = 0.0
    t2_x1 = t2_x2 = t2_y1 = t2_y2 = 0.0
    for n in range(1_000, 24_000):
        mu = fractional_position(n)
        position = n - (100 + mu)
        reference.append(sinc_sample(signal, position))
        output["linear"].append(linear_sample(signal, position))
        output["lagrange3"].append(cubic_sample(signal, position))
        x1 = signal[n - 100]
        a = (1 - mu) / (1 + mu)
        y1 = a * x1 + t1_x - a * t1_y
        output["thiran1"].append(y1)
        t1_x, t1_y = x1, y1
        total = 1 + mu
        a1 = -2 * (total - 2) / (total + 1)
        a2 = (
            (total - 2)
            * (total - 1)
            / ((total + 1) * (total + 2))
        )
        x2 = signal[n - 99]
        y2 = (
            a2 * x2
            + a1 * t2_x1
            + t2_x2
            - a1 * t2_y1
            - a2 * t2_y2
        )
        output["thiran2"].append(y2)
        t2_x2, t2_x1 = t2_x1, x2
        t2_y2, t2_y1 = t2_y1, y2
    result: dict[str, dict[str, float | bool]] = {}
    for name, values in output.items():
        errors = [a - b for a, b in zip(values, reference)]
        result[name] = {
            "rmse": math.sqrt(
                sum(value * value for value in errors) / len(errors)
            ),
            "maximum_abs_error": max(abs(value) for value in errors),
            "maximum_adjacent_step": max(
                abs(values[index] - values[index - 1])
                for index in range(1, len(values))
            ),
            "all_finite": all(math.isfinite(value) for value in values),
        }
    return result


def close_mapping(
    actual: dict[str, Any], expected: dict[str, Any]
) -> bool:
    return all(
        actual[key] == value
        if isinstance(value, bool)
        else math.isclose(actual[key], value, rel_tol=1.0e-12, abs_tol=1.0e-12)
        for key, value in expected.items()
    )


def percentile(values: list[float], q: float) -> float:
    return sorted(values)[math.ceil(q * len(values)) - 1]


def verify(
    report: dict[str, Any],
    benchmark: dict[str, Any],
    contract_hash: str,
) -> dict[str, Any]:
    dynamic = recompute_dynamic()
    cases: dict[str, dict[str, bool]] = {}
    for name in NAMES:
        case = report["algorithms"][name]
        cases[name] = {
            "static_12khz_recomputed": close_mapping(
                case["static_12khz"], recompute_static(name, 12_000)
            ),
            "static_20khz_recomputed": close_mapping(
                case["static_20khz"], recompute_static(name, 20_000)
            ),
            "dynamic_recomputed": close_mapping(
                case["time_varying"], dynamic[name]
            ),
        }
    benchmark_cases: dict[str, dict[str, bool]] = {}
    for name in ["linear", "lagrange3"]:
        case = benchmark[name]
        elapsed = case["elapsed_ns_per_frame"]
        benchmark_cases[name] = {
            "raw_samples_present": len(elapsed) == benchmark["blocks"]
            and all(value >= 0 for value in elapsed),
            "percentiles_recomputed": math.isclose(
                case["p50_ns_per_frame"], percentile(elapsed, 0.50)
            )
            and math.isclose(
                case["p99_ns_per_frame"], percentile(elapsed, 0.99)
            ),
            "p99_below_500_ns": percentile(elapsed, 0.99) <= 500,
            "zero_allocation": case["allocation_windows_bytes"]
            == [0, 0, 0, 0, 0],
            "checksum_finite": math.isfinite(case["checksum"]),
        }
    return {
        "report_contract_hash_matches": report["source_contract_sha256"]
        == contract_hash,
        "benchmark_contract_hash_matches": benchmark[
            "source_contract_sha256"
        ]
        == contract_hash,
        "identities_match": report["status"]
        == "valid-minecraft-fractional-delay-selection"
        and benchmark["status"]
        == "valid-fractional-delay-renderer-benchmark",
        "algorithm_cases": cases,
        "all_algorithm_cases_pass": all(
            all(case.values()) for case in cases.values()
        ),
        "benchmark_cases": benchmark_cases,
        "all_benchmark_cases_pass": all(
            all(case.values()) for case in benchmark_cases.values()
        ),
        "selection_is_lagrange3": report["selection"]["selected"]
        == "lagrange3",
        "lagrange_improves_12khz_magnitude": report["algorithms"][
            "lagrange3"
        ]["static_12khz"]["maximum_abs_magnitude_error_db"]
        < report["algorithms"]["linear"]["static_12khz"][
            "maximum_abs_magnitude_error_db"
        ],
        "lagrange_improves_dynamic_rmse": report["algorithms"][
            "lagrange3"
        ]["time_varying"]["rmse"]
        < report["algorithms"]["linear"]["time_varying"]["rmse"],
        "thiran_runtime_rejected": report["selection"][
            "thiran_runtime_rejected"
        ],
        "captures_audio": report["captures_audio"]
        or benchmark["captures_audio"],
        "physical_endpoint_opened": report["physical_endpoint_opened"]
        or benchmark["physical_endpoint_opened"],
        "minecraft_client_started": report["minecraft_client_started"]
        or benchmark["minecraft_client_started"],
        "client_level_read": report["client_level_read"]
        or benchmark["client_level_read"],
        "cuda_executed": report["cuda_executed"]
        or benchmark["cuda_executed"],
        "minecraft_integration_enabled": report[
            "minecraft_integration_enabled"
        ]
        or benchmark["minecraft_integration_enabled"],
        "live_early_renderer_enabled": report[
            "live_early_renderer_enabled"
        ]
        or benchmark["live_early_renderer_enabled"],
        "release_calibrated": report["release_calibrated"]
        or benchmark["release_calibrated"],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--benchmark", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    benchmark = json.loads(args.benchmark.read_text(encoding="utf-8"))
    gates = verify(report, benchmark, sha256(args.contract))
    positive = [
        "report_contract_hash_matches",
        "benchmark_contract_hash_matches",
        "identities_match",
        "all_algorithm_cases_pass",
        "all_benchmark_cases_pass",
        "selection_is_lagrange3",
        "lagrange_improves_12khz_magnitude",
        "lagrange_improves_dynamic_rmse",
        "thiran_runtime_rejected",
    ]
    negative = [
        "captures_audio",
        "physical_endpoint_opened",
        "minecraft_client_started",
        "client_level_read",
        "cuda_executed",
        "minecraft_integration_enabled",
        "live_early_renderer_enabled",
        "release_calibrated",
    ]
    if not all(gates[name] for name in positive) or any(
        gates[name] for name in negative
    ):
        raise SystemExit(f"fractional delay verification failed: {gates}")
    result = {
        "schema_version": 1,
        "status": "verified-minecraft-fractional-delay-selection",
        "source_report_sha256": sha256(args.report),
        "source_benchmark_sha256": sha256(args.benchmark),
        "source_contract_sha256": sha256(args.contract),
        "gates": gates,
        "metrics": {
            "selected": report["selection"]["selected"],
            "linear_dynamic_rmse": report["algorithms"]["linear"][
                "time_varying"
            ]["rmse"],
            "lagrange3_dynamic_rmse": report["algorithms"][
                "lagrange3"
            ]["time_varying"]["rmse"],
            "linear_p99_ns": benchmark["linear"][
                "p99_ns_per_frame"
            ],
            "lagrange3_p99_ns": benchmark["lagrange3"][
                "p99_ns_per_frame"
            ],
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
