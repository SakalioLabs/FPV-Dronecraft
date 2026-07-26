#!/usr/bin/env python3
"""D121j static and time-varying fractional-delay comparison."""

from __future__ import annotations

import argparse
import cmath
import hashlib
import json
import math
from pathlib import Path
from typing import Callable


SAMPLE_RATE = 48_000
FRACTIONS = [0.1, 0.25, 0.5, 0.75, 0.9]
ALGORITHMS = ["linear", "lagrange3", "thiran1", "thiran2"]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def response(name: str, fraction: float, omega: float) -> complex:
    z = cmath.exp(-1j * omega)
    if name == "linear":
        return (1.0 - fraction) + fraction * z
    if name == "lagrange3":
        q = 1.0 - fraction
        hm1 = -q * (q - 1.0) * (q - 2.0) / 6.0
        h0 = (q + 1.0) * (q - 1.0) * (q - 2.0) / 2.0
        h1 = -(q + 1.0) * q * (q - 2.0) / 2.0
        h2 = (q + 1.0) * q * (q - 1.0) / 6.0
        return hm1 * z**2 + h0 * z + h1 + h2 / z
    if name == "thiran1":
        coefficient = (1.0 - fraction) / (1.0 + fraction)
        return (coefficient + z) / (1.0 + coefficient * z)
    if name == "thiran2":
        delay = 1.0 + fraction
        a1 = -2.0 * (delay - 2.0) / (delay + 1.0)
        a2 = (
            (delay - 2.0)
            * (delay - 1.0)
            / ((delay + 1.0) * (delay + 2.0))
        )
        full = (a2 + a1 * z + z * z) / (
            1.0 + a1 * z + a2 * z * z
        )
        return full / z
    raise ValueError(name)


def static_metrics(name: str, maximum_hz: float) -> dict[str, float]:
    maximum_magnitude = 0.0
    maximum_phase = 0.0
    for fraction in FRACTIONS:
        for index in range(1, 401):
            frequency = maximum_hz * index / 400.0
            omega = 2.0 * math.pi * frequency / SAMPLE_RATE
            actual = response(name, fraction, omega)
            ideal = cmath.exp(-1j * omega * fraction)
            magnitude_db = 20.0 * math.log10(max(abs(actual), 1.0e-15))
            phase = math.degrees(cmath.phase(actual / ideal))
            maximum_magnitude = max(
                maximum_magnitude, abs(magnitude_db)
            )
            maximum_phase = max(maximum_phase, abs(phase))
    return {
        "maximum_abs_magnitude_error_db": maximum_magnitude,
        "maximum_abs_wrapped_phase_error_degrees": maximum_phase,
    }


def excitation(length: int) -> list[float]:
    output: list[float] = []
    noise = 0x1234ABCD
    frequencies = [180, 360, 720, 1440, 2880, 5760, 9000, 12000]
    for index in range(length):
        harmonic = sum(
            math.sin(2.0 * math.pi * frequency * index / SAMPLE_RATE)
            for frequency in frequencies
        ) / len(frequencies)
        noise ^= (noise << 13) & 0xFFFFFFFF
        noise ^= noise >> 17
        noise ^= (noise << 5) & 0xFFFFFFFF
        signed_noise = ((noise & 0xFFFF) / 32767.5) - 1.0
        output.append(0.7 * harmonic + 0.3 * signed_noise)
    return output


def fraction_at(index: int) -> float:
    period = 4_000
    phase = (index % period) / period
    triangle = 2.0 * phase if phase < 0.5 else 2.0 * (1.0 - phase)
    value = 0.05 + 0.90 * triangle
    if index >= 12_000:
        value = 1.0 - value
    return value


def sample_linear(samples: list[float], position: float) -> float:
    lower = math.floor(position)
    fraction = position - lower
    return (
        samples[lower] * (1.0 - fraction)
        + samples[lower + 1] * fraction
    )


def sample_lagrange(samples: list[float], position: float) -> float:
    lower = math.floor(position)
    q = position - lower
    hm1 = -q * (q - 1.0) * (q - 2.0) / 6.0
    h0 = (q + 1.0) * (q - 1.0) * (q - 2.0) / 2.0
    h1 = -(q + 1.0) * q * (q - 2.0) / 2.0
    h2 = (q + 1.0) * q * (q - 1.0) / 6.0
    return (
        samples[lower - 1] * hm1
        + samples[lower] * h0
        + samples[lower + 1] * h1
        + samples[lower + 2] * h2
    )


def sample_sinc(samples: list[float], position: float) -> float:
    center = math.floor(position)
    weighted = 0.0
    weight_sum = 0.0
    for sample_index in range(center - 15, center + 17):
        distance = position - sample_index
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
        weighted += samples[sample_index] * weight
        weight_sum += weight
    return weighted / weight_sum


def time_varying_outputs(
    samples: list[float],
) -> tuple[list[float], dict[str, list[float]]]:
    base = 100
    start = 1_000
    stop = 24_000
    reference: list[float] = []
    outputs = {name: [] for name in ALGORITHMS}
    thiran1_previous_input = 0.0
    thiran1_previous_output = 0.0
    thiran2_x1 = 0.0
    thiran2_x2 = 0.0
    thiran2_y1 = 0.0
    thiran2_y2 = 0.0
    for index in range(start, stop):
        fraction = fraction_at(index)
        position = index - (base + fraction)
        reference.append(sample_sinc(samples, position))
        outputs["linear"].append(sample_linear(samples, position))
        outputs["lagrange3"].append(sample_lagrange(samples, position))

        x1 = samples[index - base]
        a = (1.0 - fraction) / (1.0 + fraction)
        y1 = (
            a * x1
            + thiran1_previous_input
            - a * thiran1_previous_output
        )
        outputs["thiran1"].append(y1)
        thiran1_previous_input = x1
        thiran1_previous_output = y1

        delay = 1.0 + fraction
        a1 = -2.0 * (delay - 2.0) / (delay + 1.0)
        a2 = (
            (delay - 2.0)
            * (delay - 1.0)
            / ((delay + 1.0) * (delay + 2.0))
        )
        x2 = samples[index - (base - 1)]
        y2 = (
            a2 * x2
            + a1 * thiran2_x1
            + thiran2_x2
            - a1 * thiran2_y1
            - a2 * thiran2_y2
        )
        outputs["thiran2"].append(y2)
        thiran2_x2 = thiran2_x1
        thiran2_x1 = x2
        thiran2_y2 = thiran2_y1
        thiran2_y1 = y2
    return reference, outputs


def dynamic_metrics(
    reference: list[float], actual: list[float]
) -> dict[str, float | bool]:
    errors = [
        value - expected for value, expected in zip(actual, reference)
    ]
    rmse = math.sqrt(sum(value * value for value in errors) / len(errors))
    maximum_error = max(abs(value) for value in errors)
    maximum_step = max(
        abs(actual[index] - actual[index - 1])
        for index in range(1, len(actual))
    )
    return {
        "rmse": rmse,
        "maximum_abs_error": maximum_error,
        "maximum_adjacent_step": maximum_step,
        "all_finite": all(math.isfinite(value) for value in actual),
    }


def analyze() -> dict[str, object]:
    samples = excitation(25_000)
    reference, outputs = time_varying_outputs(samples)
    algorithms: dict[str, object] = {}
    for name in ALGORITHMS:
        algorithms[name] = {
            "static_12khz": static_metrics(name, 12_000.0),
            "static_20khz": static_metrics(name, 20_000.0),
            "time_varying": dynamic_metrics(reference, outputs[name]),
        }
    linear = algorithms["linear"]
    lagrange = algorithms["lagrange3"]
    selected = (
        "lagrange3"
        if lagrange["static_12khz"][
            "maximum_abs_magnitude_error_db"
        ]
        < linear["static_12khz"]["maximum_abs_magnitude_error_db"]
        and lagrange["time_varying"]["rmse"]
        <= linear["time_varying"]["rmse"]
        else "none"
    )
    return {
        "algorithms": algorithms,
        "selection": {
            "selected": selected,
            "reason": (
                "third-order Lagrange improves the 12 kHz magnitude "
                "screen and moving-delay RMSE without recursive state"
                if selected == "lagrange3"
                else "no FIR candidate satisfied the frozen selection rule"
            ),
            "thiran_runtime_rejected": True,
            "thiran_runtime_rejection_reason": (
                "time-varying recursive coefficients require explicit "
                "state correction and topology-state lifecycle handling"
            ),
        },
        "reference": {
            "samples": len(reference),
            "windowed_sinc_taps": 32,
            "base_delay_samples": 100,
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    result = {
        "schema_version": 1,
        "status": "valid-minecraft-fractional-delay-selection",
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
