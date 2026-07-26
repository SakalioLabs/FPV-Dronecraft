#!/usr/bin/env python3
"""Independent D121k analysis and executed-renderer verifier."""

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
PASSBANDS = [12_000, 16_000, 20_000]
NAMES = [
    "lagrange3",
    "lagrange5",
    "lagrange7",
    "kaiser_sinc8",
    "kaiser_sinc8_phase1024",
    "lanczos_sinc12_bound",
    "ideal_2x_lagrange3_bound",
]
DYNAMIC_NAMES = NAMES[:-1]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def i0(value: float) -> float:
    y = value * value / 4.0
    term = total = 1.0
    for index in range(1, 25):
        term *= y / (index * index)
        total += term
    return total


def polynomial(nodes: list[int], mu: float) -> list[float]:
    result: list[float] = []
    for node in nodes:
        value = 1.0
        for other in nodes:
            if other != node:
                value *= (mu - other) / (node - other)
        result.append(value)
    return result


def kaiser(mu: float) -> list[float]:
    result: list[float] = []
    normalization = i0(2.0)
    for node in range(-3, 5):
        distance = node - mu
        cardinal = (
            1.0
            if distance == 0.0
            else math.sin(math.pi * distance) / (math.pi * distance)
        )
        radius = distance / 4.0
        window = (
            i0(2.0 * math.sqrt(max(0.0, 1.0 - radius * radius)))
            / normalization
            if abs(radius) <= 1.0
            else 0.0
        )
        result.append(cardinal * window)
    total = sum(result)
    return [value / total for value in result]


def lanczos12(mu: float) -> list[float]:
    result: list[float] = []
    for node in range(-5, 7):
        distance = mu - node
        if distance == 0.0:
            value = 1.0
        elif abs(distance) >= 6.0:
            value = 0.0
        else:
            value = (
                math.sin(math.pi * distance)
                / (math.pi * distance)
                * math.sin(math.pi * distance / 6.0)
                / (math.pi * distance / 6.0)
            )
        result.append(value)
    total = sum(result)
    return [value / total for value in result]


def kernel(name: str, mu: float) -> tuple[list[int], list[float]]:
    if name.startswith("lagrange"):
        order = int(name[-1])
        first = -(order // 2)
        nodes = list(range(first, first + order + 1))
        return nodes, polynomial(nodes, mu)
    if name == "kaiser_sinc8":
        return list(range(-3, 5)), kaiser(mu)
    if name == "kaiser_sinc8_phase1024":
        phase = math.floor(mu * 1_024 + 0.5)
        return list(range(-3, 5)), kaiser(phase / 1_024.0)
    if name == "lanczos_sinc12_bound":
        return list(range(-5, 7)), lanczos12(mu)
    raise ValueError(name)


def transfer(name: str, mu: float, omega: float) -> complex:
    if name == "ideal_2x_lagrange3_bound":
        delay = 2.0 * mu
        integer = math.floor(delay)
        remainder = delay - integer
        nodes = [-1, 0, 1, 2]
        coefficients = polynomial(nodes, remainder)
        return sum(
            coefficient
            * cmath.exp(1j * omega * (integer + node) / 2.0)
            for node, coefficient in zip(nodes, coefficients)
        )
    nodes, coefficients = kernel(name, mu)
    return sum(
        coefficient * cmath.exp(1j * omega * node)
        for node, coefficient in zip(nodes, coefficients)
    )


def static_case(name: str, maximum_hz: int) -> dict[str, float]:
    magnitude = 0.0
    phase = 0.0
    for mu in MUS:
        for point in range(1, 401):
            omega = (
                2.0 * math.pi * maximum_hz * point / 400.0 / RATE
            )
            actual = transfer(name, mu, omega)
            ideal = cmath.exp(1j * omega * mu)
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


def signal(length: int) -> list[float]:
    frequencies = [180, 360, 720, 1440, 2880, 5760, 9000, 12000]
    state = 0x1234ABCD
    output: list[float] = []
    for index in range(length):
        harmonic = sum(
            math.sin(2.0 * math.pi * frequency * index / RATE)
            for frequency in frequencies
        ) / 8.0
        state ^= (state << 13) & 0xFFFFFFFF
        state ^= state >> 17
        state ^= (state << 5) & 0xFFFFFFFF
        noise = (state & 0xFFFF) / 32767.5 - 1.0
        output.append(0.7 * harmonic + 0.3 * noise)
    return output


def moving_fraction(index: int) -> float:
    phase = (index % 4_000) / 4_000.0
    triangle = 2.0 * phase if phase < 0.5 else 2.0 * (1.0 - phase)
    value = 0.05 + 0.90 * triangle
    return 1.0 - value if index >= 12_000 else value


def reference_sample(samples: list[float], position: float) -> float:
    center = math.floor(position)
    output = 0.0
    normalization = 0.0
    for index in range(center - 15, center + 17):
        distance = position - index
        if distance == 0.0:
            weight = 1.0
        elif abs(distance) >= 16.0:
            weight = 0.0
        else:
            weight = (
                math.sin(math.pi * distance)
                / (math.pi * distance)
                * math.sin(math.pi * distance / 16.0)
                / (math.pi * distance / 16.0)
            )
        output += samples[index] * weight
        normalization += weight
    return output / normalization


def candidate_sample(
    samples: list[float], position: float, name: str
) -> float:
    lower = math.floor(position)
    nodes, coefficients = kernel(name, position - lower)
    return sum(
        samples[lower + node] * coefficient
        for node, coefficient in zip(nodes, coefficients)
    )


def dynamic_cases() -> dict[str, dict[str, float | bool]]:
    samples = signal(25_000)
    reference: list[float] = []
    output = {name: [] for name in DYNAMIC_NAMES}
    for index in range(1_000, 24_000):
        position = index - (100 + moving_fraction(index))
        reference.append(reference_sample(samples, position))
        for name in DYNAMIC_NAMES:
            output[name].append(candidate_sample(samples, position, name))
    result: dict[str, dict[str, float | bool]] = {}
    for name, values in output.items():
        errors = [
            value - expected
            for value, expected in zip(values, reference)
        ]
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


def close_map(actual: dict[str, Any], expected: dict[str, Any]) -> bool:
    return all(
        actual[key] == value
        if isinstance(value, bool)
        else math.isclose(
            actual[key], value, rel_tol=1.0e-12, abs_tol=1.0e-12
        )
        for key, value in expected.items()
    )


def percentile(values: list[float], quantile: float) -> float:
    return sorted(values)[math.ceil(quantile * len(values)) - 1]


def verify(
    report: dict[str, Any],
    benchmark: dict[str, Any],
    contract_hash: str,
) -> dict[str, Any]:
    dynamic = dynamic_cases()
    algorithm_cases: dict[str, dict[str, bool]] = {}
    for name in NAMES:
        actual = report["algorithms"][name]
        gates = {
            f"static_{passband // 1000}khz_recomputed": close_map(
                actual[f"static_{passband // 1000}khz"],
                static_case(name, passband),
            )
            for passband in PASSBANDS
        }
        if name in dynamic:
            gates["dynamic_recomputed"] = close_map(
                actual["time_varying"], dynamic[name]
            )
        else:
            gates["runtime_exclusion_preserved"] = (
                actual["time_varying"] is None
                and actual["runtime_eligible"] is False
            )
        algorithm_cases[name] = gates

    benchmark_cases: dict[str, dict[str, bool]] = {}
    for name in [
        "lagrange3",
        "high_band_kaiser_sinc8_phase1024",
    ]:
        actual = benchmark[name]
        elapsed = actual["elapsed_ns_per_frame"]
        trial_p99 = actual["trial_p99_ns"]
        trial_allocations = actual["trial_allocation_windows_bytes"]
        benchmark_cases[name] = {
            "raw_samples_present": len(elapsed) == benchmark["blocks"]
            and all(value >= 0.0 for value in elapsed),
            "percentiles_recomputed": math.isclose(
                actual["p50_ns_per_frame"], percentile(elapsed, 0.50)
            )
            and math.isclose(
                actual["p99_ns_per_frame"], percentile(elapsed, 0.99)
            ),
            "zero_allocation": actual["allocation_windows_bytes"]
            == [0, 0, 0, 0, 0]
            and len(trial_allocations) == benchmark["trials"]
            and all(
                window == [0, 0, 0, 0, 0]
                for window in trial_allocations
            ),
            "all_trial_p99_present": len(trial_p99)
            == benchmark["trials"],
            "reported_case_is_worst_trial": math.isclose(
                actual["p99_ns_per_frame"], max(trial_p99)
            ),
            "checksum_finite": math.isfinite(actual["checksum"]),
        }
    candidate = report["algorithms"]["kaiser_sinc8_phase1024"]
    baseline = report["algorithms"]["lagrange3"]
    boundaries = [
        "captures_audio",
        "physical_endpoint_opened",
        "minecraft_client_started",
        "client_level_read",
        "cuda_executed",
        "minecraft_integration_enabled",
        "live_early_renderer_enabled",
        "release_calibrated",
    ]
    return {
        "report_contract_hash_matches": report["source_contract_sha256"]
        == contract_hash,
        "benchmark_contract_hash_matches": benchmark[
            "source_contract_sha256"
        ]
        == contract_hash,
        "identities_match": report["status"]
        == "valid-minecraft-high-band-fractional-delay"
        and benchmark["status"]
        == "valid-high-band-fractional-delay-benchmark"
        and benchmark["trials"] == 3,
        "algorithm_cases": algorithm_cases,
        "all_algorithm_cases_pass": all(
            all(case.values()) for case in algorithm_cases.values()
        ),
        "benchmark_cases": benchmark_cases,
        "all_benchmark_cases_pass": all(
            all(case.values()) for case in benchmark_cases.values()
        ),
        "selection_is_high_band_kaiser_sinc8_phase1024": report[
            "selection"
        ]["selected_for_runtime_benchmark"]
        == "high_band_kaiser_sinc8_phase1024",
        "candidate_20khz_magnitude_below_1db": candidate[
            "static_20khz"
        ]["maximum_abs_magnitude_error_db"]
        <= 1.0,
        "candidate_dynamic_rmse_below_lagrange3": candidate[
            "time_varying"
        ]["rmse"]
        < baseline["time_varying"]["rmse"],
        "candidate_runtime_p99_below_750ns": benchmark[
            "high_band_kaiser_sinc8_phase1024"
        ]["p99_ns_per_frame"]
        <= 750.0,
        "coefficient_table_below_70000_bytes": report[
            "coefficient_table"
        ]["bytes"]
        == benchmark["shared_coefficient_table_bytes"]
        <= 70_000,
        "oversampling_remains_unmeasured_bound": report["selection"][
            "oversampling_rejected_as_unmeasured_bound"
        ]
        and report["algorithms"]["ideal_2x_lagrange3_bound"][
            "runtime_eligible"
        ]
        is False,
        **{
            boundary: report[boundary] or benchmark[boundary]
            for boundary in boundaries
        },
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
        "selection_is_high_band_kaiser_sinc8_phase1024",
        "candidate_20khz_magnitude_below_1db",
        "candidate_dynamic_rmse_below_lagrange3",
        "coefficient_table_below_70000_bytes",
        "oversampling_remains_unmeasured_bound",
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
        raise SystemExit(f"high-band verification failed: {gates}")
    candidate = report["algorithms"]["kaiser_sinc8_phase1024"]
    result = {
        "schema_version": 1,
        "status": (
            "verified-minecraft-high-band-fractional-delay"
            if gates["candidate_runtime_p99_below_750ns"]
            else "verified-minecraft-high-band-fractional-delay-"
            "runtime-gate-failed"
        ),
        "source_report_sha256": sha256(args.report),
        "source_benchmark_sha256": sha256(args.benchmark),
        "source_contract_sha256": sha256(args.contract),
        "gates": gates,
        "metrics": {
            "selected": report["selection"][
                "selected_for_runtime_benchmark"
            ],
            "candidate_20khz_magnitude_error_db": candidate[
                "static_20khz"
            ]["maximum_abs_magnitude_error_db"],
            "candidate_dynamic_rmse": candidate["time_varying"]["rmse"],
            "lagrange3_dynamic_rmse": report["algorithms"][
                "lagrange3"
            ]["time_varying"]["rmse"],
            "candidate_p99_ns": benchmark[
                "high_band_kaiser_sinc8_phase1024"
            ]["p99_ns_per_frame"],
            "candidate_trial_p99_ns": benchmark[
                "high_band_kaiser_sinc8_phase1024"
            ]["trial_p99_ns"],
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
