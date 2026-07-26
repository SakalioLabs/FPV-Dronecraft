#!/usr/bin/env python3
"""D121k wideband fractional-delay candidate analysis."""

from __future__ import annotations

import argparse
import cmath
import hashlib
import json
import math
from pathlib import Path
from typing import Callable

from analyze_minecraft_fractional_delay_selection import (
    dynamic_metrics,
    excitation,
    fraction_at,
    sample_sinc,
)


SAMPLE_RATE = 48_000
FRACTIONS = [0.1, 0.25, 0.5, 0.75, 0.9]
PASSBANDS = [12_000, 16_000, 20_000]
PHASE_INTERVALS = 1_024
KAISER_BETA = 2.0
DYNAMIC_NAMES = [
    "lagrange3",
    "lagrange5",
    "lagrange7",
    "kaiser_sinc8",
    "kaiser_sinc8_phase1024",
    "lanczos_sinc12_bound",
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def bessel_i0(value: float) -> float:
    scaled = value * value * 0.25
    term = 1.0
    total = 1.0
    for order in range(1, 25):
        term *= scaled / (order * order)
        total += term
    return total


def lagrange_coefficients(nodes: list[int], fraction: float) -> list[float]:
    coefficients: list[float] = []
    for node in nodes:
        coefficient = 1.0
        for other in nodes:
            if other != node:
                coefficient *= (fraction - other) / (node - other)
        coefficients.append(coefficient)
    return coefficients


def kaiser_sinc8_coefficients(fraction: float) -> list[float]:
    nodes = list(range(-3, 5))
    denominator = bessel_i0(KAISER_BETA)
    coefficients: list[float] = []
    for node in nodes:
        distance = node - fraction
        sinc = (
            1.0
            if distance == 0.0
            else math.sin(math.pi * distance) / (math.pi * distance)
        )
        radius = distance / 4.0
        window = (
            bessel_i0(
                KAISER_BETA * math.sqrt(max(0.0, 1.0 - radius * radius))
            )
            / denominator
            if abs(radius) <= 1.0
            else 0.0
        )
        coefficients.append(sinc * window)
    total = sum(coefficients)
    return [value / total for value in coefficients]


def quantized_kaiser_sinc8_coefficients(
    fraction: float,
) -> list[float]:
    phase = math.floor(fraction * PHASE_INTERVALS + 0.5)
    return kaiser_sinc8_coefficients(phase / PHASE_INTERVALS)


def lanczos_sinc12_coefficients(fraction: float) -> list[float]:
    coefficients: list[float] = []
    for node in range(-5, 7):
        distance = fraction - node
        if distance == 0.0:
            coefficient = 1.0
        elif abs(distance) >= 6.0:
            coefficient = 0.0
        else:
            coefficient = (
                math.sin(math.pi * distance)
                / (math.pi * distance)
                * math.sin(math.pi * distance / 6.0)
                / (math.pi * distance / 6.0)
            )
        coefficients.append(coefficient)
    total = sum(coefficients)
    return [value / total for value in coefficients]


def definition(
    name: str,
) -> tuple[list[int], Callable[[float], list[float]]]:
    if name == "lagrange3":
        nodes = list(range(-1, 3))
        return nodes, lambda fraction: lagrange_coefficients(nodes, fraction)
    if name == "lagrange5":
        nodes = list(range(-2, 4))
        return nodes, lambda fraction: lagrange_coefficients(nodes, fraction)
    if name == "lagrange7":
        nodes = list(range(-3, 5))
        return nodes, lambda fraction: lagrange_coefficients(nodes, fraction)
    if name == "kaiser_sinc8":
        return list(range(-3, 5)), kaiser_sinc8_coefficients
    if name == "kaiser_sinc8_phase1024":
        return list(range(-3, 5)), quantized_kaiser_sinc8_coefficients
    if name == "lanczos_sinc12_bound":
        return list(range(-5, 7)), lanczos_sinc12_coefficients
    raise ValueError(name)


def response(name: str, fraction: float, omega: float) -> complex:
    if name == "ideal_2x_lagrange3_bound":
        doubled = 2.0 * fraction
        integer = math.floor(doubled)
        remainder = doubled - integer
        nodes = list(range(-1, 3))
        coefficients = lagrange_coefficients(nodes, remainder)
        return sum(
            coefficient
            * cmath.exp(1j * omega * 0.5 * (integer + node))
            for node, coefficient in zip(nodes, coefficients)
        )
    nodes, coefficient_function = definition(name)
    coefficients = coefficient_function(fraction)
    return sum(
        coefficient * cmath.exp(1j * omega * node)
        for node, coefficient in zip(nodes, coefficients)
    )


def static_metrics(name: str, maximum_hz: int) -> dict[str, float]:
    maximum_magnitude = 0.0
    maximum_phase = 0.0
    for fraction in FRACTIONS:
        for index in range(1, 401):
            frequency = maximum_hz * index / 400.0
            omega = 2.0 * math.pi * frequency / SAMPLE_RATE
            actual = response(name, fraction, omega)
            ideal = cmath.exp(1j * omega * fraction)
            magnitude_db = 20.0 * math.log10(max(abs(actual), 1.0e-15))
            phase = math.degrees(cmath.phase(actual / ideal))
            maximum_magnitude = max(maximum_magnitude, abs(magnitude_db))
            maximum_phase = max(maximum_phase, abs(phase))
    return {
        "maximum_abs_magnitude_error_db": maximum_magnitude,
        "maximum_abs_wrapped_phase_error_degrees": maximum_phase,
    }


def sample_candidate(
    samples: list[float], position: float, name: str
) -> float:
    lower = math.floor(position)
    fraction = position - lower
    nodes, coefficient_function = definition(name)
    coefficients = coefficient_function(fraction)
    return sum(
        samples[lower + node] * coefficient
        for node, coefficient in zip(nodes, coefficients)
    )


def dynamic_outputs(
    samples: list[float],
) -> tuple[list[float], dict[str, list[float]]]:
    reference: list[float] = []
    outputs = {name: [] for name in DYNAMIC_NAMES}
    for index in range(1_000, 24_000):
        fraction = fraction_at(index)
        position = index - (100 + fraction)
        reference.append(sample_sinc(samples, position))
        for name in DYNAMIC_NAMES:
            outputs[name].append(sample_candidate(samples, position, name))
    return reference, outputs


def analyze() -> dict[str, object]:
    samples = excitation(25_000)
    reference, outputs = dynamic_outputs(samples)
    names = DYNAMIC_NAMES + ["ideal_2x_lagrange3_bound"]
    algorithms: dict[str, object] = {}
    for name in names:
        entry: dict[str, object] = {
            f"static_{passband // 1000}khz": static_metrics(name, passband)
            for passband in PASSBANDS
        }
        if name in outputs:
            entry["time_varying"] = dynamic_metrics(
                reference, outputs[name]
            )
        else:
            entry["time_varying"] = None
            entry["runtime_eligible"] = False
            entry["exclusion_reason"] = (
                "ideal 2x bound excludes interpolation filters, "
                "alias rejection, latency and runtime cost"
            )
        algorithms[name] = entry

    candidate = algorithms["kaiser_sinc8_phase1024"]
    baseline = algorithms["lagrange3"]
    selected = (
        "high_band_kaiser_sinc8_phase1024"
        if candidate["static_20khz"][
            "maximum_abs_magnitude_error_db"
        ]
        <= 1.0
        and candidate["time_varying"]["rmse"]
        < baseline["time_varying"]["rmse"]
        else "none"
    )
    return {
        "algorithms": algorithms,
        "selection": {
            "selected_for_runtime_benchmark": selected,
            "low_mid_interpolation": "lagrange3",
            "high_interpolation": "kaiser_sinc8_phase1024",
            "oversampling_rejected_as_unmeasured_bound": True,
        },
        "reference": {
            "samples": len(reference),
            "windowed_sinc_taps": 32,
            "base_delay_samples": 100,
        },
        "coefficient_table": {
            "phase_intervals": PHASE_INTERVALS,
            "rows": PHASE_INTERVALS + 1,
            "taps": 8,
            "bytes": (PHASE_INTERVALS + 1) * 8 * 8,
            "shared_immutable": True,
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    result = {
        "schema_version": 1,
        "status": "valid-minecraft-high-band-fractional-delay",
        "source_contract_sha256": sha256(args.contract),
        **analyze(),
        "captures_audio": False,
        "physical_endpoint_opened": False,
        "minecraft_client_started": False,
        "client_level_read": False,
        "cuda_executed": False,
        "minecraft_integration_enabled": False,
        "live_early_renderer_enabled": False,
        "release_calibrated": False,
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
