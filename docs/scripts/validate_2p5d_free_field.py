#!/usr/bin/env python3
"""Validate the 2.5D point-source inverse transform in homogeneous air.

The three-dimensional medium is assumed invariant along y.  For each axial
wavenumber ky, the transformed free-field Green function is the two-dimensional
line-source solution i/4 H_0^(1)(k_perp*rho).  Integrating all ky components
must recover exp(i*k*R)/(4*pi*R), the three-dimensional point-source solution.

The propagating branch uses ky = k*sin(theta).  The evanescent branch uses
ky = k*cosh(u).  These substitutions remove the critical-wavenumber endpoint
from the quadrature and make node refinement auditable.
"""

from __future__ import annotations

import argparse
import json
import math
from dataclasses import asdict, dataclass

import numpy as np
from numpy.polynomial.legendre import leggauss
from scipy.special import hankel1


SPEED_OF_SOUND_METERS_PER_SECOND = 343.0
EVANESCENT_DECAY_ARGUMENT = 40.0


@dataclass(frozen=True)
class CaseResult:
    frequency_hz: float
    transverse_distance_m: float
    axial_offset_m: float
    nodes_per_branch: int
    refined_nodes_per_branch: int
    magnitude_error_db: float
    phase_error_degrees: float
    refinement_delta_db: float


def gauss_legendre_complex(function, lower: float, upper: float, nodes: int):
    abscissas, weights = leggauss(nodes)
    samples = 0.5 * (upper - lower) * abscissas + 0.5 * (lower + upper)
    return 0.5 * (upper - lower) * np.sum(weights * function(samples))


def reconstructed_point_source(
    frequency_hz: float,
    transverse_distance_m: float,
    axial_offset_m: float,
    nodes_per_branch: int,
) -> complex:
    wave_number = (
        2.0
        * math.pi
        * frequency_hz
        / SPEED_OF_SOUND_METERS_PER_SECOND
    )

    def propagating(theta):
        transverse_wave_number = wave_number * np.cos(theta)
        axial_wave_number = wave_number * np.sin(theta)
        line_source = (
            0.25j
            * hankel1(
                0,
                transverse_wave_number * transverse_distance_m,
            )
        )
        jacobian = wave_number * np.cos(theta)
        return (
            line_source
            * np.cos(axial_wave_number * axial_offset_m)
            * jacobian
        )

    maximum_u = np.arcsinh(
        EVANESCENT_DECAY_ARGUMENT
        / (wave_number * transverse_distance_m)
    )

    def evanescent(u):
        decay_wave_number = wave_number * np.sinh(u)
        axial_wave_number = wave_number * np.cosh(u)
        line_source = (
            0.25j
            * hankel1(
                0,
                1j * decay_wave_number * transverse_distance_m,
            )
        )
        jacobian = wave_number * np.sinh(u)
        return (
            line_source
            * np.cos(axial_wave_number * axial_offset_m)
            * jacobian
        )

    propagating_integral = gauss_legendre_complex(
        propagating,
        0.0,
        0.5 * math.pi,
        nodes_per_branch,
    )
    evanescent_integral = gauss_legendre_complex(
        evanescent,
        0.0,
        float(maximum_u),
        nodes_per_branch,
    )
    return complex((propagating_integral + evanescent_integral) / math.pi)


def analytic_point_source(
    frequency_hz: float,
    transverse_distance_m: float,
    axial_offset_m: float,
) -> complex:
    distance = math.hypot(transverse_distance_m, axial_offset_m)
    wave_number = (
        2.0
        * math.pi
        * frequency_hz
        / SPEED_OF_SOUND_METERS_PER_SECOND
    )
    return np.exp(1j * wave_number * distance) / (4.0 * math.pi * distance)


def analyze_case(
    frequency_hz: float,
    transverse_distance_m: float,
    axial_offset_m: float,
    nodes_per_branch: int,
) -> CaseResult:
    refined_nodes = math.ceil(1.5 * nodes_per_branch)
    reconstructed = reconstructed_point_source(
        frequency_hz,
        transverse_distance_m,
        axial_offset_m,
        nodes_per_branch,
    )
    refined = reconstructed_point_source(
        frequency_hz,
        transverse_distance_m,
        axial_offset_m,
        refined_nodes,
    )
    analytic = analytic_point_source(
        frequency_hz,
        transverse_distance_m,
        axial_offset_m,
    )
    ratio = reconstructed / analytic
    return CaseResult(
        frequency_hz=frequency_hz,
        transverse_distance_m=transverse_distance_m,
        axial_offset_m=axial_offset_m,
        nodes_per_branch=nodes_per_branch,
        refined_nodes_per_branch=refined_nodes,
        magnitude_error_db=20.0 * math.log10(abs(ratio)),
        phase_error_degrees=math.degrees(float(np.angle(ratio))),
        refinement_delta_db=20.0
        * math.log10(abs(reconstructed) / abs(refined)),
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate homogeneous-air 2.5D point-source reconstruction."
    )
    parser.add_argument(
        "--nodes",
        type=int,
        default=64,
        help="Gauss-Legendre nodes per propagating/evanescent branch.",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emit machine-readable JSON.",
    )
    arguments = parser.parse_args()
    if arguments.nodes < 8:
        parser.error("--nodes must be at least 8")

    cases = [
        (frequency, transverse, axial)
        for frequency in (1_000.0, 2_000.0, 4_000.0)
        for transverse, axial in ((0.6, 0.0), (0.6, 0.5), (0.6, 1.0))
    ]
    results = [
        analyze_case(frequency, transverse, axial, arguments.nodes)
        for frequency, transverse, axial in cases
    ]
    maximum_magnitude_error_db = max(
        abs(result.magnitude_error_db) for result in results
    )
    maximum_phase_error_degrees = max(
        abs(result.phase_error_degrees) for result in results
    )
    maximum_refinement_delta_db = max(
        abs(result.refinement_delta_db) for result in results
    )
    passed = (
        maximum_magnitude_error_db <= 0.01
        and maximum_phase_error_degrees <= 0.01
        and maximum_refinement_delta_db <= 0.01
    )
    report = {
        "method": {
            "description": "2.5D axial-wavenumber inverse transform",
            "propagating_substitution": "ky = k sin(theta)",
            "evanescent_substitution": "ky = k cosh(u)",
            "evanescent_decay_argument": EVANESCENT_DECAY_ARGUMENT,
            "speed_of_sound_m_s": SPEED_OF_SOUND_METERS_PER_SECOND,
        },
        "gates": {
            "maximum_magnitude_error_db": 0.01,
            "maximum_phase_error_degrees": 0.01,
            "maximum_refinement_delta_db": 0.01,
        },
        "summary": {
            "passed": passed,
            "maximum_magnitude_error_db": maximum_magnitude_error_db,
            "maximum_phase_error_degrees": maximum_phase_error_degrees,
            "maximum_refinement_delta_db": maximum_refinement_delta_db,
        },
        "cases": [asdict(result) for result in results],
    }
    if arguments.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print(
            "2.5D free field: "
            f"passed={passed} "
            f"max|magnitude|={maximum_magnitude_error_db:.9f} dB "
            f"max|phase|={maximum_phase_error_degrees:.9f} deg "
            f"max|refinement|={maximum_refinement_delta_db:.9f} dB"
        )
        for result in results:
            print(
                f"{result.frequency_hz:7.1f} Hz "
                f"rho={result.transverse_distance_m:.3f} m "
                f"y={result.axial_offset_m:.3f} m "
                f"mag={result.magnitude_error_db:+.9f} dB "
                f"phase={result.phase_error_degrees:+.9f} deg "
                f"refine={result.refinement_delta_db:+.9f} dB"
            )
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
