#!/usr/bin/env python3
"""Validate a complete 2.5D rigid-obstacle point-source reconstruction.

Every axial-wavenumber quadrature node is solved with the sparse Helmholtz/PML
operator from validate_2p5d_helmholtz.py.  The reconstructed three-dimensional
field for an infinite rigid plane is compared with the analytic point-source
image solution.  This is the first end-to-end obstacle transform; it is still
not the 25 mm double-edge scene.
"""

from __future__ import annotations

import argparse
import gc
import json
import math
import time

import numpy as np
from numpy.polynomial.legendre import leggauss

from validate_2p5d_helmholtz import (
    HelmholtzGrid,
    PhysicalPoint,
    PmlHelmholtz2d,
    SPEED_OF_SOUND_METERS_PER_SECOND,
)


EVANESCENT_DECAY_ARGUMENT = 40.0


def transformed_rigid_wall_pressure(
    frequency_hz: float,
    points_per_wavelength: int,
    nodes_per_branch: int,
) -> tuple[complex, dict]:
    base_cell_size_m = (
        SPEED_OF_SOUND_METERS_PER_SECOND / frequency_hz / 16.0
    )
    cell_size_m = (
        SPEED_OF_SOUND_METERS_PER_SECOND
        / frequency_hz
        / points_per_wavelength
    )
    scale = points_per_wavelength / 16.0

    def scaled(base_cells: int) -> int:
        value = base_cells * scale
        rounded = round(value)
        if abs(value - rounded) > 1.0e-12:
            raise ValueError(
                "points_per_wavelength must preserve the validation geometry"
            )
        return int(rounded)

    wall_start_x = scaled(120)
    intended_wall_face_x_m = (120.0 - 0.5) * base_cell_size_m
    origin_x_m = intended_wall_face_x_m - (
        wall_start_x - 0.5
    ) * cell_size_m
    grid = HelmholtzGrid(
        width_cells=scaled(200) + 1,
        height_cells=scaled(160) + 1,
        cell_size_m=cell_size_m,
        pml_width_cells=scaled(20),
        pml_target_nepers=8.0,
        origin_x_m=origin_x_m,
    )
    solid = np.zeros(
        (grid.height_cells, grid.width_cells),
        dtype=bool,
    )
    solid[:, wall_start_x:] = True
    source = PhysicalPoint(
        70.0 * base_cell_size_m + 0.003,
        80.0 * base_cell_size_m + 0.004,
    )
    receiver = PhysicalPoint(
        90.0 * base_cell_size_m + 0.002,
        90.0 * base_cell_size_m + 0.006,
    )
    direct_distance_m = math.hypot(
        receiver.x_m - source.x_m,
        receiver.z_m - source.z_m,
    )
    image_source_x_m = 2.0 * intended_wall_face_x_m - source.x_m
    image_distance_m = math.hypot(
        image_source_x_m - receiver.x_m,
        receiver.z_m - source.z_m,
    )
    wave_number = (
        2.0
        * math.pi
        * frequency_hz
        / SPEED_OF_SOUND_METERS_PER_SECOND
    )
    abscissas, weights = leggauss(nodes_per_branch)
    solve_count = 0
    solve_seconds = 0.0

    def solve_at_axial_wavenumber(axial_wavenumber: float) -> complex:
        nonlocal solve_count, solve_seconds
        started = time.perf_counter()
        solver = PmlHelmholtz2d(
            grid,
            frequency_hz,
            axial_wavenumber,
            solid,
        )
        solution = solver.solve_physical([source])
        value = solver.value_physical(solution, receiver)
        solve_seconds += time.perf_counter() - started
        solve_count += 1
        del solution
        del solver
        gc.collect()
        return value

    propagating = 0.0j
    for abscissa, weight in zip(abscissas, weights, strict=True):
        theta = 0.25 * math.pi * (abscissa + 1.0)
        theta_weight = 0.25 * math.pi * weight
        axial_wavenumber = wave_number * math.sin(theta)
        jacobian = wave_number * math.cos(theta)
        propagating += (
            theta_weight
            * jacobian
            * solve_at_axial_wavenumber(axial_wavenumber)
        )

    minimum_transverse_distance_m = min(
        direct_distance_m,
        image_distance_m,
    )
    maximum_u = math.asinh(
        EVANESCENT_DECAY_ARGUMENT
        / (wave_number * minimum_transverse_distance_m)
    )
    evanescent = 0.0j
    for abscissa, weight in zip(abscissas, weights, strict=True):
        u = 0.5 * maximum_u * (abscissa + 1.0)
        u_weight = 0.5 * maximum_u * weight
        axial_wavenumber = wave_number * math.cosh(u)
        jacobian = wave_number * math.sinh(u)
        evanescent += (
            u_weight
            * jacobian
            * solve_at_axial_wavenumber(axial_wavenumber)
        )

    reconstructed = (propagating + evanescent) / math.pi
    diagnostics = {
        "frequency_hz": frequency_hz,
        "points_per_wavelength": points_per_wavelength,
        "nodes_per_branch": nodes_per_branch,
        "solve_count": solve_count,
        "solve_seconds": solve_seconds,
        "mean_seconds_per_solve": solve_seconds / solve_count,
        "unknowns_per_solve": int(np.count_nonzero(~solid[1:-1, 1:-1])),
        "direct_distance_m": direct_distance_m,
        "image_distance_m": image_distance_m,
        "maximum_evanescent_u": maximum_u,
        "wall_face_x_m": intended_wall_face_x_m,
        "origin_x_m": origin_x_m,
    }
    return reconstructed, diagnostics


def analytic_rigid_wall_pressure(
    frequency_hz: float,
    direct_distance_m: float,
    image_distance_m: float,
) -> complex:
    wave_number = (
        2.0
        * math.pi
        * frequency_hz
        / SPEED_OF_SOUND_METERS_PER_SECOND
    )

    def point_source(distance_m: float) -> complex:
        return np.exp(1j * wave_number * distance_m) / (
            4.0 * math.pi * distance_m
        )

    return complex(
        point_source(direct_distance_m)
        + point_source(image_distance_m)
    )


def complex_error(actual: complex, expected: complex) -> tuple[float, float]:
    ratio = actual / expected
    return (
        20.0 * math.log10(abs(ratio)),
        math.degrees(float(np.angle(ratio))),
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate end-to-end 2.5D rigid-wall reconstruction."
    )
    parser.add_argument("--frequency-hz", type=float, default=1_000.0)
    parser.add_argument(
        "--points-per-wavelength",
        type=int,
        default=24,
    )
    parser.add_argument("--nodes", type=int, default=18)
    parser.add_argument("--refined-nodes", type=int, default=24)
    parser.add_argument("--json", action="store_true")
    arguments = parser.parse_args()
    if not arguments.frequency_hz > 0.0:
        parser.error("--frequency-hz must be positive")
    if arguments.points_per_wavelength < 16:
        parser.error("--points-per-wavelength must be at least 16")
    if arguments.nodes < 4:
        parser.error("--nodes must be at least 4")
    if arguments.refined_nodes <= arguments.nodes:
        parser.error("--refined-nodes must exceed --nodes")

    try:
        reconstructed, diagnostics = transformed_rigid_wall_pressure(
            arguments.frequency_hz,
            arguments.points_per_wavelength,
            arguments.nodes,
        )
        refined, refined_diagnostics = transformed_rigid_wall_pressure(
            arguments.frequency_hz,
            arguments.points_per_wavelength,
            arguments.refined_nodes,
        )
    except ValueError as error:
        parser.error(str(error))
    analytic = analytic_rigid_wall_pressure(
        arguments.frequency_hz,
        diagnostics["direct_distance_m"],
        diagnostics["image_distance_m"],
    )
    magnitude_error_db, phase_error_degrees = complex_error(
        refined,
        analytic,
    )
    refinement_magnitude_delta_db, refinement_phase_delta_degrees = (
        complex_error(refined, reconstructed)
    )
    passed = (
        abs(magnitude_error_db) <= 1.0
        and abs(phase_error_degrees) <= 10.0
        and abs(refinement_magnitude_delta_db) <= 0.5
        and abs(refinement_phase_delta_degrees) <= 5.0
    )
    report = {
        "method": {
            "description": (
                "actual Helmholtz solve at every propagating and "
                "evanescent axial-wavenumber node"
            ),
            "propagating_substitution": "ky = k sin(theta)",
            "evanescent_substitution": "ky = k cosh(u)",
            "geometry": "infinite rigid plane, source/receiver same axial plane",
        },
        "coarse": diagnostics,
        "refined": refined_diagnostics,
        "result": {
            "magnitude_error_db": magnitude_error_db,
            "phase_error_degrees": phase_error_degrees,
            "refinement_magnitude_delta_db": (
                refinement_magnitude_delta_db
            ),
            "refinement_phase_delta_degrees": (
                refinement_phase_delta_degrees
            ),
        },
        "gates": {
            "magnitude_error_db": 1.0,
            "phase_error_degrees": 10.0,
            "refinement_magnitude_delta_db": 0.5,
            "refinement_phase_delta_degrees": 5.0,
        },
        "passed": passed,
    }
    if arguments.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print(
            "2.5D rigid wall: "
            f"passed={passed} "
            f"error={magnitude_error_db:+.6f} dB/"
            f"{phase_error_degrees:+.6f} deg "
            f"refinement={refinement_magnitude_delta_db:+.6f} dB/"
            f"{refinement_phase_delta_degrees:+.6f} deg "
            f"solves={diagnostics['solve_count'] + refined_diagnostics['solve_count']} "
            f"seconds={diagnostics['solve_seconds'] + refined_diagnostics['solve_seconds']:.3f}"
        )
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
