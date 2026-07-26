#!/usr/bin/env python3
"""Generate the first independent 25 mm rigid double-edge wave reference.

The screen is a solid rectangular half-plane with an exactly aligned 25 mm
width and top face.  Two spatial grids use two and three cells through the
thickness (dx=12.5/8.333... mm), while every axial-wavenumber node is solved
with the 2.5D Helmholtz/PML operator.  Both quadrature and spatial convergence
must pass before the fine-grid result is emitted as a candidate oracle.
"""

from __future__ import annotations

import argparse
import gc
import json
import math
import time
from dataclasses import dataclass

import numpy as np
from numpy.polynomial.legendre import leggauss

from validate_2p5d_helmholtz import (
    HelmholtzGrid,
    PhysicalPoint,
    PmlHelmholtz2d,
    SPEED_OF_SOUND_METERS_PER_SECOND,
)
from analyze_bras_rs5 import udfa_2024_double_edge_complex


DEFAULT_FREQUENCY_HZ = 1_000.0
SCREEN_THICKNESS_M = 0.025
SCREEN_FRONT_X_M = 0.9875
SCREEN_BACK_X_M = SCREEN_FRONT_X_M + SCREEN_THICKNESS_M
SCREEN_TOP_Z_M = 0.8
SOURCE = PhysicalPoint(0.3875, 0.6)
RECEIVER = PhysicalPoint(1.6125, 0.6)
EVANESCENT_DECAY_ARGUMENT = 40.0


@dataclass(frozen=True)
class DomainBounds:
    minimum_x_m: float = 0.0
    maximum_x_m: float = 2.0
    minimum_z_m: float = 0.0
    maximum_z_m: float = 1.4
    pml_width_m: float = 0.25

    def __post_init__(self):
        if not self.maximum_x_m > self.minimum_x_m:
            raise ValueError("domain maximum x must exceed minimum x")
        if not self.maximum_z_m > self.minimum_z_m:
            raise ValueError("domain maximum z must exceed minimum z")
        if not self.pml_width_m > 0.0:
            raise ValueError("PML width must be positive")
        if not (
            self.minimum_x_m < SOURCE.x_m < self.maximum_x_m
            and self.minimum_x_m < RECEIVER.x_m < self.maximum_x_m
            and self.minimum_z_m < SOURCE.z_m < self.maximum_z_m
            and self.minimum_z_m < RECEIVER.z_m < self.maximum_z_m
        ):
            raise ValueError("source and receiver must lie inside the domain")
        if not (
            self.minimum_x_m < SCREEN_FRONT_X_M
            and SCREEN_BACK_X_M < self.maximum_x_m
            and self.minimum_z_m < SCREEN_TOP_Z_M < self.maximum_z_m
        ):
            raise ValueError("screen top and thickness must lie inside the domain")


def grid_for_thickness_cells(
    thickness_cells: int,
    frequency_hz: float,
    domain: DomainBounds,
) -> tuple[HelmholtzGrid, np.ndarray, dict]:
    if thickness_cells < 1:
        raise ValueError("thickness_cells must be positive")
    cell_size_m = SCREEN_THICKNESS_M / thickness_cells
    points_per_wavelength = (
        SPEED_OF_SOUND_METERS_PER_SECOND
        / frequency_hz
        / cell_size_m
    )
    if points_per_wavelength < 24.0:
        raise ValueError(
            "grid must provide at least 24 points per wavelength"
        )
    width_intervals = round(
        (domain.maximum_x_m - domain.minimum_x_m) / cell_size_m
    )
    height_intervals = round(
        (domain.maximum_z_m - domain.minimum_z_m) / cell_size_m
    )
    pml_width_cells = round(domain.pml_width_m / cell_size_m)
    screen_start_x = round(
        (SCREEN_FRONT_X_M - domain.minimum_x_m) / cell_size_m + 0.5
    )
    screen_end_x = screen_start_x + thickness_cells
    origin_x_m = SCREEN_FRONT_X_M - (
        screen_start_x - 0.5
    ) * cell_size_m
    screen_top_last_z = round(
        (SCREEN_TOP_Z_M - domain.minimum_z_m) / cell_size_m - 0.5
    )
    origin_z_m = SCREEN_TOP_Z_M - (
        screen_top_last_z + 0.5
    ) * cell_size_m
    grid = HelmholtzGrid(
        width_cells=width_intervals + 1,
        height_cells=height_intervals + 1,
        cell_size_m=cell_size_m,
        pml_width_cells=pml_width_cells,
        pml_target_nepers=8.0,
        origin_x_m=origin_x_m,
        origin_z_m=origin_z_m,
    )
    solid = np.zeros(
        (grid.height_cells, grid.width_cells),
        dtype=bool,
    )
    solid[
        : screen_top_last_z + 1,
        screen_start_x:screen_end_x,
    ] = True
    effective_front_x_m = (
        origin_x_m + (screen_start_x - 0.5) * cell_size_m
    )
    effective_back_x_m = (
        origin_x_m + (screen_end_x - 0.5) * cell_size_m
    )
    effective_top_z_m = (
        origin_z_m + (screen_top_last_z + 0.5) * cell_size_m
    )
    alignment = {
        "thickness_cells": thickness_cells,
        "cell_size_m": cell_size_m,
        "points_per_wavelength": (
            SPEED_OF_SOUND_METERS_PER_SECOND
            / frequency_hz
            / cell_size_m
        ),
        "screen_front_error_m": (
            effective_front_x_m - SCREEN_FRONT_X_M
        ),
        "screen_back_error_m": (
            effective_back_x_m - SCREEN_BACK_X_M
        ),
        "screen_top_error_m": effective_top_z_m - SCREEN_TOP_Z_M,
        "origin_x_m": origin_x_m,
        "origin_z_m": origin_z_m,
        "width_cells": grid.width_cells,
        "height_cells": grid.height_cells,
        "pml_width_cells": grid.pml_width_cells,
        "requested_pml_width_m": domain.pml_width_m,
        "effective_pml_width_m": (
            grid.pml_width_cells * grid.cell_size_m
        ),
        "effective_domain_minimum_x_m": grid.origin_x_m,
        "effective_domain_maximum_x_m": (
            grid.origin_x_m
            + (grid.width_cells - 1) * grid.cell_size_m
        ),
        "effective_domain_minimum_z_m": grid.origin_z_m,
        "effective_domain_maximum_z_m": (
            grid.origin_z_m
            + (grid.height_cells - 1) * grid.cell_size_m
        ),
        "unknowns_per_solve": int(
            np.count_nonzero(~solid[1:-1, 1:-1])
        ),
    }
    return grid, solid, alignment


def transformed_double_edge_pressure(
    frequency_hz: float,
    thickness_cells: int,
    nodes_per_branch: int,
    domain: DomainBounds,
) -> tuple[complex, dict]:
    grid, solid, alignment = grid_for_thickness_cells(
        thickness_cells,
        frequency_hz,
        domain,
    )
    wave_number = (
        2.0
        * math.pi
        * frequency_hz
        / SPEED_OF_SOUND_METERS_PER_SECOND
    )
    source_edge_distance_m = math.hypot(
        SCREEN_FRONT_X_M - SOURCE.x_m,
        SCREEN_TOP_Z_M - SOURCE.z_m,
    )
    receiver_edge_distance_m = math.hypot(
        RECEIVER.x_m - SCREEN_BACK_X_M,
        SCREEN_TOP_Z_M - RECEIVER.z_m,
    )
    shortest_path_m = (
        source_edge_distance_m
        + SCREEN_THICKNESS_M
        + receiver_edge_distance_m
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
        solution = solver.solve_physical([SOURCE])
        value = solver.value_physical(solution, RECEIVER)
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
        propagating += (
            theta_weight
            * wave_number
            * math.cos(theta)
            * solve_at_axial_wavenumber(axial_wavenumber)
        )

    maximum_u = math.asinh(
        EVANESCENT_DECAY_ARGUMENT
        / (
            wave_number
            * min(source_edge_distance_m, receiver_edge_distance_m)
        )
    )
    evanescent = 0.0j
    for abscissa, weight in zip(abscissas, weights, strict=True):
        u = 0.5 * maximum_u * (abscissa + 1.0)
        u_weight = 0.5 * maximum_u * weight
        axial_wavenumber = wave_number * math.cosh(u)
        evanescent += (
            u_weight
            * wave_number
            * math.sinh(u)
            * solve_at_axial_wavenumber(axial_wavenumber)
        )

    physical_green = (propagating + evanescent) / math.pi
    one_over_r_normalized = 4.0 * math.pi * physical_green
    diagnostics = {
        **alignment,
        "frequency_hz": frequency_hz,
        "nodes_per_branch": nodes_per_branch,
        "solve_count": solve_count,
        "solve_seconds": solve_seconds,
        "mean_seconds_per_solve": solve_seconds / solve_count,
        "source_edge_distance_m": source_edge_distance_m,
        "receiver_edge_distance_m": receiver_edge_distance_m,
        "shortest_path_m": shortest_path_m,
        "raw_real": one_over_r_normalized.real,
        "raw_imaginary": one_over_r_normalized.imag,
        "raw_magnitude": abs(one_over_r_normalized),
        "normalized_db": 20.0
        * math.log10(abs(one_over_r_normalized) * shortest_path_m),
        "maximum_evanescent_u": maximum_u,
    }
    return one_over_r_normalized, diagnostics


def complex_delta(primary: complex, comparison: complex) -> tuple[float, float]:
    ratio = primary / comparison
    return (
        20.0 * math.log10(abs(ratio)),
        math.degrees(float(np.angle(ratio))),
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Generate a converged 25 mm double-edge wave reference."
    )
    parser.add_argument(
        "--frequency-hz",
        type=float,
        default=DEFAULT_FREQUENCY_HZ,
    )
    parser.add_argument("--coarse-thickness-cells", type=int, default=2)
    parser.add_argument("--fine-thickness-cells", type=int, default=3)
    parser.add_argument("--nodes", type=int, default=18)
    parser.add_argument("--refined-nodes", type=int, default=24)
    parser.add_argument("--domain-minimum-x-m", type=float, default=0.0)
    parser.add_argument("--domain-maximum-x-m", type=float, default=2.0)
    parser.add_argument("--domain-minimum-z-m", type=float, default=0.0)
    parser.add_argument("--domain-maximum-z-m", type=float, default=1.4)
    parser.add_argument("--pml-width-m", type=float, default=0.25)
    parser.add_argument(
        "--single-thickness-cells",
        type=int,
        help=(
            "Run one grid/quadrature sample instead of the full convergence "
            "matrix; requires --single-nodes."
        ),
    )
    parser.add_argument(
        "--single-nodes",
        type=int,
        help=(
            "Nodes per propagating/evanescent branch for --single-thickness-cells."
        ),
    )
    parser.add_argument("--json", action="store_true")
    arguments = parser.parse_args()
    try:
        domain = DomainBounds(
            minimum_x_m=arguments.domain_minimum_x_m,
            maximum_x_m=arguments.domain_maximum_x_m,
            minimum_z_m=arguments.domain_minimum_z_m,
            maximum_z_m=arguments.domain_maximum_z_m,
            pml_width_m=arguments.pml_width_m,
        )
    except ValueError as error:
        parser.error(str(error))
    if (
        (arguments.single_thickness_cells is None)
        != (arguments.single_nodes is None)
    ):
        parser.error(
            "--single-thickness-cells and --single-nodes must be used together"
        )
    if arguments.single_thickness_cells is not None:
        if arguments.single_thickness_cells < 1:
            parser.error("--single-thickness-cells must be positive")
        if arguments.single_nodes < 4:
            parser.error("--single-nodes must be at least 4")
        if not arguments.frequency_hz > 0.0:
            parser.error("--frequency-hz must be positive")
        try:
            _, diagnostics = transformed_double_edge_pressure(
                arguments.frequency_hz,
                arguments.single_thickness_cells,
                arguments.single_nodes,
                domain,
            )
        except ValueError as error:
            parser.error(str(error))
        if arguments.json:
            print(json.dumps(diagnostics, indent=2, sort_keys=True))
        else:
            print(
                "2.5D 25 mm double-edge sample: "
                f"frequency={arguments.frequency_hz:.3f} Hz "
                f"thickness_cells={arguments.single_thickness_cells} "
                f"nodes={arguments.single_nodes} "
                f"normalized={diagnostics['normalized_db']:+.6f} dB "
                f"seconds={diagnostics['solve_seconds']:.3f}"
            )
        return 0
    if arguments.coarse_thickness_cells < 1:
        parser.error("--coarse-thickness-cells must be positive")
    if arguments.fine_thickness_cells <= arguments.coarse_thickness_cells:
        parser.error(
            "--fine-thickness-cells must exceed --coarse-thickness-cells"
        )
    if arguments.nodes < 4:
        parser.error("--nodes must be at least 4")
    if arguments.refined_nodes <= arguments.nodes:
        parser.error("--refined-nodes must exceed --nodes")
    if not arguments.frequency_hz > 0.0:
        parser.error("--frequency-hz must be positive")

    try:
        coarse_grid_coarse_quad, coarse_grid_coarse_diagnostics = (
            transformed_double_edge_pressure(
                arguments.frequency_hz,
                arguments.coarse_thickness_cells,
                arguments.nodes,
                domain,
            )
        )
        coarse_grid_refined_quad, coarse_grid_refined_diagnostics = (
            transformed_double_edge_pressure(
                arguments.frequency_hz,
                arguments.coarse_thickness_cells,
                arguments.refined_nodes,
                domain,
            )
        )
        fine_grid_coarse_quad, fine_grid_coarse_diagnostics = (
            transformed_double_edge_pressure(
                arguments.frequency_hz,
                arguments.fine_thickness_cells,
                arguments.nodes,
                domain,
            )
        )
        fine_grid_refined_quad, fine_grid_refined_diagnostics = (
            transformed_double_edge_pressure(
                arguments.frequency_hz,
                arguments.fine_thickness_cells,
                arguments.refined_nodes,
                domain,
            )
        )
    except ValueError as error:
        parser.error(str(error))
    coarse_quadrature_delta = complex_delta(
        coarse_grid_refined_quad,
        coarse_grid_coarse_quad,
    )
    fine_quadrature_delta = complex_delta(
        fine_grid_refined_quad,
        fine_grid_coarse_quad,
    )
    spatial_delta = complex_delta(
        fine_grid_refined_quad,
        coarse_grid_refined_quad,
    )
    udfa_pressure = udfa_2024_double_edge_complex(
        arguments.frequency_hz,
        (4.887, 0.0, 1.866),
        (6.112, 0.0, 1.866),
    )
    udfa_normalized_db = 20.0 * math.log10(abs(udfa_pressure))
    udfa_error_db = (
        udfa_normalized_db
        - fine_grid_refined_diagnostics["normalized_db"]
    )
    udfa_single_point_gate_db = 4.0
    maximum_quadrature_magnitude_delta_db = max(
        abs(coarse_quadrature_delta[0]),
        abs(fine_quadrature_delta[0]),
    )
    maximum_quadrature_phase_delta_degrees = max(
        abs(coarse_quadrature_delta[1]),
        abs(fine_quadrature_delta[1]),
    )
    passed = (
        maximum_quadrature_magnitude_delta_db <= 0.5
        and maximum_quadrature_phase_delta_degrees <= 5.0
        and abs(spatial_delta[0]) <= 0.5
        and abs(spatial_delta[1]) <= 5.0
    )
    report = {
        "geometry": {
            "description": "25 mm rigid rectangular half-plane",
            "frequency_hz": arguments.frequency_hz,
            "screen_thickness_m": SCREEN_THICKNESS_M,
            "screen_front_x_m": SCREEN_FRONT_X_M,
            "screen_back_x_m": SCREEN_BACK_X_M,
            "screen_top_z_m": SCREEN_TOP_Z_M,
            "source": {"x_m": SOURCE.x_m, "z_m": SOURCE.z_m},
            "receiver": {"x_m": RECEIVER.x_m, "z_m": RECEIVER.z_m},
        },
        "coarse_grid": {
            "coarse_quadrature": coarse_grid_coarse_diagnostics,
            "refined_quadrature": coarse_grid_refined_diagnostics,
            "quadrature_magnitude_delta_db": coarse_quadrature_delta[0],
            "quadrature_phase_delta_degrees": coarse_quadrature_delta[1],
        },
        "fine_grid": {
            "coarse_quadrature": fine_grid_coarse_diagnostics,
            "refined_quadrature": fine_grid_refined_diagnostics,
            "quadrature_magnitude_delta_db": fine_quadrature_delta[0],
            "quadrature_phase_delta_degrees": fine_quadrature_delta[1],
        },
        "spatial_convergence": {
            "magnitude_delta_db": spatial_delta[0],
            "phase_delta_degrees": spatial_delta[1],
        },
        "gates": {
            "maximum_quadrature_magnitude_delta_db": 0.5,
            "maximum_quadrature_phase_delta_degrees": 5.0,
            "spatial_magnitude_delta_db": 0.5,
            "spatial_phase_delta_degrees": 5.0,
        },
        "candidate_reference": (
            fine_grid_refined_diagnostics if passed else None
        ),
        "udfa_2024_comparison": {
            "python_oracle": (
                "analyze_bras_rs5.udfa_2024_double_edge_complex"
            ),
            "normalized_db": udfa_normalized_db,
            "wave_reference_error_db": udfa_error_db,
            "single_point_gate_db": udfa_single_point_gate_db,
            "passed": abs(udfa_error_db) <= udfa_single_point_gate_db,
        },
        "passed": passed,
    }
    if arguments.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        total_seconds = sum(
            diagnostics["solve_seconds"]
            for diagnostics in (
                coarse_grid_coarse_diagnostics,
                coarse_grid_refined_diagnostics,
                fine_grid_coarse_diagnostics,
                fine_grid_refined_diagnostics,
            )
        )
        print(
            "2.5D 25 mm double edge: "
            f"passed={passed} "
            f"quadrature={maximum_quadrature_magnitude_delta_db:.6f} dB/"
            f"{maximum_quadrature_phase_delta_degrees:.6f} deg "
            f"spatial={spatial_delta[0]:+.6f} dB/"
            f"{spatial_delta[1]:+.6f} deg "
            f"candidate={fine_grid_refined_diagnostics['normalized_db']:+.6f} dB "
            f"seconds={total_seconds:.3f}"
        )
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
