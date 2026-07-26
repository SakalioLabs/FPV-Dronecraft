#!/usr/bin/env python3
"""Validate one 2.5D frequency/wavenumber Helmholtz solve.

This is the obstacle-solver layer below the axial inverse transform in
validate_2p5d_free_field.py.  It discretizes

    d/dx(sy/sx dp/dx) + d/dz(sx/sy dp/dz)
        + sx*sy*k_perp^2*p = -delta

with a five-point flux stencil.  sx/sy are complex coordinate stretches in
the outer PML.  A missing face next to a solid cell implements a rigid
zero-normal-gradient boundary.  This script is an offline research oracle,
not a Minecraft runtime dependency.
"""

from __future__ import annotations

import argparse
import json
import math
from dataclasses import asdict, dataclass

import numpy as np
from scipy.sparse import coo_matrix
from scipy.sparse.linalg import splu
from scipy.special import hankel1, k0


SPEED_OF_SOUND_METERS_PER_SECOND = 343.0


@dataclass(frozen=True)
class GridPoint:
    x: int
    z: int


@dataclass(frozen=True)
class PhysicalPoint:
    x_m: float
    z_m: float


@dataclass(frozen=True)
class HelmholtzGrid:
    width_cells: int
    height_cells: int
    cell_size_m: float
    pml_width_cells: int
    pml_target_nepers: float
    origin_x_m: float = 0.0
    origin_z_m: float = 0.0

    def __post_init__(self):
        if self.width_cells < 5 or self.height_cells < 5:
            raise ValueError("grid dimensions must each be at least five")
        if not self.cell_size_m > 0.0:
            raise ValueError("cell_size_m must be positive")
        if not 0 < self.pml_width_cells * 2 < min(
            self.width_cells, self.height_cells
        ):
            raise ValueError("PML must leave an interior region")
        if not self.pml_target_nepers > 0.0:
            raise ValueError("pml_target_nepers must be positive")
        if not math.isfinite(self.origin_x_m) or not math.isfinite(
            self.origin_z_m
        ):
            raise ValueError("grid origin must be finite")


@dataclass(frozen=True)
class ValidationSummary:
    free_field_max_magnitude_error_db: float
    free_field_max_phase_error_degrees: float
    free_field_relative_spreading_error_db: float
    evanescent_max_magnitude_error_db: float
    evanescent_max_phase_error_degrees: float
    reciprocity_relative_complex_error: float
    rigid_wall_magnitude_error_db: float
    rigid_wall_phase_error_degrees: float
    passed: bool


class PmlHelmholtz2d:
    def __init__(
        self,
        grid: HelmholtzGrid,
        frequency_hz: float,
        axial_wavenumber_per_m: float,
        solid: np.ndarray | None = None,
        blocked_x_faces: np.ndarray | None = None,
        blocked_z_faces: np.ndarray | None = None,
    ):
        if not frequency_hz > 0.0:
            raise ValueError("frequency_hz must be positive")
        self.grid = grid
        self.frequency_hz = frequency_hz
        self.angular_frequency = 2.0 * math.pi * frequency_hz
        total_wavenumber = (
            self.angular_frequency / SPEED_OF_SOUND_METERS_PER_SECOND
        )
        self.transverse_wavenumber_squared = (
            total_wavenumber * total_wavenumber
            - axial_wavenumber_per_m * axial_wavenumber_per_m
            + 0.0j
        )
        expected_shape = (grid.height_cells, grid.width_cells)
        if solid is None:
            self.solid = np.zeros(expected_shape, dtype=bool)
        else:
            if solid.shape != expected_shape:
                raise ValueError("solid mask shape does not match grid")
            self.solid = np.asarray(solid, dtype=bool).copy()
        expected_x_faces = (
            grid.height_cells,
            grid.width_cells - 1,
        )
        expected_z_faces = (
            grid.height_cells - 1,
            grid.width_cells,
        )
        self.blocked_x_faces = self._copy_face_mask(
            blocked_x_faces,
            expected_x_faces,
            "blocked_x_faces",
        )
        self.blocked_z_faces = self._copy_face_mask(
            blocked_z_faces,
            expected_z_faces,
            "blocked_z_faces",
        )
        self.unknown = self._create_unknown_map()
        self.matrix = self._assemble_matrix()
        self.factorization = splu(self.matrix.tocsc())

    def solve(self, sources: list[GridPoint]) -> np.ndarray:
        if not sources:
            raise ValueError("at least one source is required")
        right_hand_side = np.zeros(
            (self.matrix.shape[0], len(sources)),
            dtype=np.complex128,
        )
        source_scale = -1.0 / (self.grid.cell_size_m**2)
        for column, source in enumerate(sources):
            source_index = self._unknown_index(source)
            right_hand_side[source_index, column] = source_scale
        return self.factorization.solve(right_hand_side)

    def solve_physical(self, sources: list[PhysicalPoint]) -> np.ndarray:
        if not sources:
            raise ValueError("at least one source is required")
        right_hand_side = np.zeros(
            (self.matrix.shape[0], len(sources)),
            dtype=np.complex128,
        )
        source_scale = -1.0 / (self.grid.cell_size_m**2)
        for column, source in enumerate(sources):
            for index, weight in self._interpolation_weights(source):
                right_hand_side[index, column] += source_scale * weight
        return self.factorization.solve(right_hand_side)

    def value(
        self,
        solution: np.ndarray,
        point: GridPoint,
        source_column: int = 0,
    ) -> complex:
        return complex(solution[self._unknown_index(point), source_column])

    def value_physical(
        self,
        solution: np.ndarray,
        point: PhysicalPoint,
        source_column: int = 0,
    ) -> complex:
        return sum(
            weight * solution[index, source_column]
            for index, weight in self._interpolation_weights(point)
        )

    def _create_unknown_map(self) -> np.ndarray:
        unknown = np.full(self.solid.shape, -1, dtype=np.int64)
        next_index = 0
        for z in range(1, self.grid.height_cells - 1):
            for x in range(1, self.grid.width_cells - 1):
                if not self.solid[z, x]:
                    unknown[z, x] = next_index
                    next_index += 1
        if next_index == 0:
            raise ValueError("grid has no interior fluid cells")
        return unknown

    def _assemble_matrix(self):
        stretch_x = self._coordinate_stretch(self.grid.width_cells)
        stretch_z = self._coordinate_stretch(self.grid.height_cells)
        inverse_dx_squared = 1.0 / (self.grid.cell_size_m**2)
        rows: list[int] = []
        columns: list[int] = []
        values: list[complex] = []
        for z in range(1, self.grid.height_cells - 1):
            for x in range(1, self.grid.width_cells - 1):
                row = int(self.unknown[z, x])
                if row < 0:
                    continue
                left = stretch_z[z] / (
                    0.5 * (stretch_x[x - 1] + stretch_x[x])
                )
                right = stretch_z[z] / (
                    0.5 * (stretch_x[x] + stretch_x[x + 1])
                )
                down = stretch_x[x] / (
                    0.5 * (stretch_z[z - 1] + stretch_z[z])
                )
                up = stretch_x[x] / (
                    0.5 * (stretch_z[z] + stretch_z[z + 1])
                )
                diagonal = (
                    stretch_x[x]
                    * stretch_z[z]
                    * self.transverse_wavenumber_squared
                )
                for neighbour_x, neighbour_z, coefficient, blocked in (
                    (
                        x - 1,
                        z,
                        left,
                        self.blocked_x_faces[z, x - 1],
                    ),
                    (
                        x + 1,
                        z,
                        right,
                        self.blocked_x_faces[z, x],
                    ),
                    (
                        x,
                        z - 1,
                        down,
                        self.blocked_z_faces[z - 1, x],
                    ),
                    (
                        x,
                        z + 1,
                        up,
                        self.blocked_z_faces[z, x],
                    ),
                ):
                    if blocked or self.solid[neighbour_z, neighbour_x]:
                        # Zero normal derivative at a rigid face.
                        continue
                    diagonal -= coefficient * inverse_dx_squared
                    neighbour = int(
                        self.unknown[neighbour_z, neighbour_x]
                    )
                    if neighbour >= 0:
                        rows.append(row)
                        columns.append(neighbour)
                        values.append(coefficient * inverse_dx_squared)
                    # Outer cells are fixed Dirichlet zero behind the PML.
                rows.append(row)
                columns.append(row)
                values.append(diagonal)
        size = int(self.unknown.max()) + 1
        return coo_matrix(
            (values, (rows, columns)),
            shape=(size, size),
            dtype=np.complex128,
        ).tocsr()

    def _coordinate_stretch(self, count: int) -> np.ndarray:
        pml_length_m = (
            self.grid.pml_width_cells * self.grid.cell_size_m
        )
        maximum_sigma_per_second = (
            SPEED_OF_SOUND_METERS_PER_SECOND
            * 3.0
            * self.grid.pml_target_nepers
            / pml_length_m
        )
        result = np.ones(count, dtype=np.complex128)
        for coordinate in range(count):
            boundary_distance = min(coordinate, count - 1 - coordinate)
            if boundary_distance < self.grid.pml_width_cells:
                normalized_depth = (
                    self.grid.pml_width_cells - boundary_distance
                ) / self.grid.pml_width_cells
                sigma = (
                    maximum_sigma_per_second
                    * normalized_depth
                    * normalized_depth
                )
                result[coordinate] += 1j * sigma / self.angular_frequency
        return result

    @staticmethod
    def _copy_face_mask(
        mask: np.ndarray | None,
        expected_shape: tuple[int, int],
        name: str,
    ) -> np.ndarray:
        if mask is None:
            return np.zeros(expected_shape, dtype=bool)
        if mask.shape != expected_shape:
            raise ValueError(f"{name} shape does not match grid")
        return np.asarray(mask, dtype=bool).copy()

    def _unknown_index(self, point: GridPoint) -> int:
        if not (
            0 <= point.x < self.grid.width_cells
            and 0 <= point.z < self.grid.height_cells
        ):
            raise ValueError("point lies outside grid")
        index = int(self.unknown[point.z, point.x])
        if index < 0:
            raise ValueError("point must lie in an interior fluid cell")
        return index

    def _interpolation_weights(
        self,
        point: PhysicalPoint,
    ) -> list[tuple[int, float]]:
        grid_x = (
            point.x_m - self.grid.origin_x_m
        ) / self.grid.cell_size_m
        grid_z = (
            point.z_m - self.grid.origin_z_m
        ) / self.grid.cell_size_m
        lower_x = math.floor(grid_x)
        lower_z = math.floor(grid_z)
        fraction_x = grid_x - lower_x
        fraction_z = grid_z - lower_z
        result: list[tuple[int, float]] = []
        for x, x_weight in (
            (lower_x, 1.0 - fraction_x),
            (lower_x + 1, fraction_x),
        ):
            for z, z_weight in (
                (lower_z, 1.0 - fraction_z),
                (lower_z + 1, fraction_z),
            ):
                weight = x_weight * z_weight
                if weight <= 1.0e-15:
                    continue
                index = self._unknown_index(GridPoint(x, z))
                result.append((index, weight))
        return result


def line_source_green(
    frequency_hz: float,
    distance_m: float,
) -> complex:
    wavenumber = (
        2.0
        * math.pi
        * frequency_hz
        / SPEED_OF_SOUND_METERS_PER_SECOND
    )
    return complex(0.25j * hankel1(0, wavenumber * distance_m))


def evanescent_line_source_green(
    decay_wavenumber_per_m: float,
    distance_m: float,
) -> complex:
    return complex(k0(decay_wavenumber_per_m * distance_m) / (2.0 * math.pi))


def distance(first: GridPoint, second: GridPoint, cell_size_m: float) -> float:
    return math.hypot(first.x - second.x, first.z - second.z) * cell_size_m


def physical_distance(first: PhysicalPoint, second: PhysicalPoint) -> float:
    return math.hypot(first.x_m - second.x_m, first.z_m - second.z_m)


def complex_error(actual: complex, expected: complex) -> tuple[float, float]:
    ratio = actual / expected
    return (
        20.0 * math.log10(abs(ratio)),
        math.degrees(float(np.angle(ratio))),
    )


def run_validation(points_per_wavelength: int) -> dict:
    frequency_hz = 1_000.0
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
    # Deliberately off-grid coordinates exercise bilinear source injection
    # and receiver interpolation while remaining identical across refinements.
    source = PhysicalPoint(
        70.0 * base_cell_size_m + 0.003,
        80.0 * base_cell_size_m + 0.004,
    )
    near = PhysicalPoint(
        98.0 * base_cell_size_m + 0.006,
        80.0 * base_cell_size_m + 0.002,
    )
    far = PhysicalPoint(
        126.0 * base_cell_size_m + 0.004,
        80.0 * base_cell_size_m + 0.005,
    )
    free_field = PmlHelmholtz2d(grid, frequency_hz, 0.0)
    free_solutions = free_field.solve_physical([source, near])
    near_actual = free_field.value_physical(free_solutions, near, 0)
    far_actual = free_field.value_physical(free_solutions, far, 0)
    near_expected = line_source_green(
        frequency_hz, physical_distance(source, near)
    )
    far_expected = line_source_green(
        frequency_hz, physical_distance(source, far)
    )
    near_magnitude_error_db, near_phase_error_degrees = complex_error(
        near_actual, near_expected
    )
    far_magnitude_error_db, far_phase_error_degrees = complex_error(
        far_actual, far_expected
    )
    relative_spreading_error_db = 20.0 * math.log10(
        abs(near_actual / far_actual)
        / abs(near_expected / far_expected)
    )
    reverse = free_field.value_physical(free_solutions, source, 1)
    reciprocity_error = abs(near_actual - reverse) / abs(near_actual)

    decay_wavenumber_per_m = 5.0
    total_wavenumber = (
        2.0
        * math.pi
        * frequency_hz
        / SPEED_OF_SOUND_METERS_PER_SECOND
    )
    evanescent_axial_wavenumber = math.hypot(
        total_wavenumber,
        decay_wavenumber_per_m,
    )
    evanescent = PmlHelmholtz2d(
        grid,
        frequency_hz,
        evanescent_axial_wavenumber,
    )
    evanescent_solution = evanescent.solve_physical([source])
    evanescent_errors = []
    for receiver in (near, far):
        actual = evanescent.value_physical(evanescent_solution, receiver)
        expected = evanescent_line_source_green(
            decay_wavenumber_per_m,
            physical_distance(source, receiver),
        )
        evanescent_errors.append(complex_error(actual, expected))
    evanescent_max_magnitude_error_db = max(
        abs(error[0]) for error in evanescent_errors
    )
    evanescent_max_phase_error_degrees = max(
        abs(error[1]) for error in evanescent_errors
    )

    solid = np.zeros(
        (grid.height_cells, grid.width_cells),
        dtype=bool,
    )
    solid[:, wall_start_x:] = True
    wall = PmlHelmholtz2d(grid, frequency_hz, 0.0, solid)
    wall_receiver = PhysicalPoint(
        90.0 * base_cell_size_m + 0.002,
        90.0 * base_cell_size_m + 0.006,
    )
    wall_solution = wall.solve_physical([source])
    wall_actual = wall.value_physical(wall_solution, wall_receiver)
    wall_face_x_m = (
        grid.origin_x_m + (wall_start_x - 0.5) * cell_size_m
    )
    source_x_m = source.x_m
    image_source_x_m = 2.0 * wall_face_x_m - source_x_m
    receiver_x_m = wall_receiver.x_m
    axial_separation_m = wall_receiver.z_m - source.z_m
    direct_distance_m = physical_distance(source, wall_receiver)
    image_distance_m = math.hypot(
        image_source_x_m - receiver_x_m,
        axial_separation_m,
    )
    wall_expected = line_source_green(
        frequency_hz, direct_distance_m
    ) + line_source_green(frequency_hz, image_distance_m)
    wall_magnitude_error_db, wall_phase_error_degrees = complex_error(
        wall_actual, wall_expected
    )
    intended_image_source_x_m = (
        2.0 * intended_wall_face_x_m - source_x_m
    )
    intended_image_distance_m = math.hypot(
        intended_image_source_x_m - receiver_x_m,
        axial_separation_m,
    )
    intended_wall_expected = line_source_green(
        frequency_hz, direct_distance_m
    ) + line_source_green(frequency_hz, intended_image_distance_m)
    (
        intended_wall_magnitude_error_db,
        intended_wall_phase_error_degrees,
    ) = complex_error(wall_actual, intended_wall_expected)

    summary = ValidationSummary(
        free_field_max_magnitude_error_db=max(
            abs(near_magnitude_error_db),
            abs(far_magnitude_error_db),
        ),
        free_field_max_phase_error_degrees=max(
            abs(near_phase_error_degrees),
            abs(far_phase_error_degrees),
        ),
        free_field_relative_spreading_error_db=abs(
            relative_spreading_error_db
        ),
        evanescent_max_magnitude_error_db=evanescent_max_magnitude_error_db,
        evanescent_max_phase_error_degrees=evanescent_max_phase_error_degrees,
        reciprocity_relative_complex_error=reciprocity_error,
        rigid_wall_magnitude_error_db=abs(wall_magnitude_error_db),
        rigid_wall_phase_error_degrees=abs(wall_phase_error_degrees),
        passed=(
            max(abs(near_magnitude_error_db), abs(far_magnitude_error_db))
            <= 0.25
            and max(
                abs(near_phase_error_degrees),
                abs(far_phase_error_degrees),
            )
            <= 5.0
            and abs(relative_spreading_error_db) <= 0.05
            and evanescent_max_magnitude_error_db <= 0.25
            and evanescent_max_phase_error_degrees <= 1.0
            and reciprocity_error <= 1.0e-10
            and abs(wall_magnitude_error_db) <= 1.0
            and abs(wall_phase_error_degrees) <= 10.0
        ),
    )
    return {
        "method": {
            "equation": "2.5D transformed scalar Helmholtz",
            "boundary": "complex-coordinate PML plus outer Dirichlet",
            "rigid_obstacle": "zero normal flux",
            "frequency_hz": frequency_hz,
            "axial_wavenumber_per_m": 0.0,
            "points_per_wavelength": points_per_wavelength,
            "cell_size_m": cell_size_m,
            "origin_x_m": grid.origin_x_m,
            "origin_z_m": grid.origin_z_m,
            "unknowns_free_field": free_field.matrix.shape[0],
            "unknowns_evanescent": evanescent.matrix.shape[0],
            "unknowns_rigid_wall": wall.matrix.shape[0],
        },
        "free_field": {
            "near": {
                "distance_m": physical_distance(source, near),
                "magnitude_error_db": near_magnitude_error_db,
                "phase_error_degrees": near_phase_error_degrees,
            },
            "far": {
                "distance_m": physical_distance(source, far),
                "magnitude_error_db": far_magnitude_error_db,
                "phase_error_degrees": far_phase_error_degrees,
            },
            "relative_spreading_error_db": relative_spreading_error_db,
            "reciprocity_relative_complex_error": reciprocity_error,
        },
        "evanescent": {
            "axial_wavenumber_per_m": evanescent_axial_wavenumber,
            "transverse_decay_wavenumber_per_m": decay_wavenumber_per_m,
            "near": {
                "magnitude_error_db": evanescent_errors[0][0],
                "phase_error_degrees": evanescent_errors[0][1],
            },
            "far": {
                "magnitude_error_db": evanescent_errors[1][0],
                "phase_error_degrees": evanescent_errors[1][1],
            },
        },
        "rigid_wall": {
            "intended_wall_face_x_m": intended_wall_face_x_m,
            "effective_wall_face_x_m": wall_face_x_m,
            "boundary_location_error_m": (
                wall_face_x_m - intended_wall_face_x_m
            ),
            "direct_distance_m": direct_distance_m,
            "image_distance_m": image_distance_m,
            "intended_image_distance_m": intended_image_distance_m,
            "magnitude_error_db": wall_magnitude_error_db,
            "phase_error_degrees": wall_phase_error_degrees,
            "intended_geometry_magnitude_error_db": (
                intended_wall_magnitude_error_db
            ),
            "intended_geometry_phase_error_degrees": (
                intended_wall_phase_error_degrees
            ),
        },
        "gates": {
            "free_field_max_magnitude_error_db": 0.25,
            "free_field_max_phase_error_degrees": 5.0,
            "free_field_relative_spreading_error_db": 0.05,
            "evanescent_max_magnitude_error_db": 0.25,
            "evanescent_max_phase_error_degrees": 1.0,
            "reciprocity_relative_complex_error": 1.0e-10,
            "rigid_wall_magnitude_error_db": 1.0,
            "rigid_wall_phase_error_degrees": 10.0,
        },
        "summary": asdict(summary),
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate one complex 2.5D Helmholtz/PML solve."
    )
    parser.add_argument(
        "--points-per-wavelength",
        type=int,
        default=24,
    )
    parser.add_argument(
        "--compare-points-per-wavelength",
        type=int,
        help=(
            "Also solve a second grid and report fixed-physical-geometry "
            "magnitude/phase changes."
        ),
    )
    parser.add_argument("--json", action="store_true")
    arguments = parser.parse_args()
    if arguments.points_per_wavelength < 16:
        parser.error("--points-per-wavelength must be at least 16")
    try:
        report = run_validation(arguments.points_per_wavelength)
        if arguments.compare_points_per_wavelength is not None:
            if arguments.compare_points_per_wavelength < 16:
                parser.error(
                    "--compare-points-per-wavelength must be at least 16"
                )
            comparison = run_validation(
                arguments.compare_points_per_wavelength
            )
            magnitude_deltas = {
                "free_near_db": (
                    report["free_field"]["near"]["magnitude_error_db"]
                    - comparison["free_field"]["near"]["magnitude_error_db"]
                ),
                "free_far_db": (
                    report["free_field"]["far"]["magnitude_error_db"]
                    - comparison["free_field"]["far"]["magnitude_error_db"]
                ),
                "evanescent_near_db": (
                    report["evanescent"]["near"]["magnitude_error_db"]
                    - comparison["evanescent"]["near"]["magnitude_error_db"]
                ),
                "evanescent_far_db": (
                    report["evanescent"]["far"]["magnitude_error_db"]
                    - comparison["evanescent"]["far"]["magnitude_error_db"]
                ),
                "rigid_wall_db": (
                    report["rigid_wall"][
                        "intended_geometry_magnitude_error_db"
                    ]
                    - comparison["rigid_wall"][
                        "intended_geometry_magnitude_error_db"
                    ]
                ),
            }
            phase_deltas = {
                "free_near_degrees": (
                    report["free_field"]["near"]["phase_error_degrees"]
                    - comparison["free_field"]["near"]["phase_error_degrees"]
                ),
                "free_far_degrees": (
                    report["free_field"]["far"]["phase_error_degrees"]
                    - comparison["free_field"]["far"]["phase_error_degrees"]
                ),
                "evanescent_near_degrees": (
                    report["evanescent"]["near"]["phase_error_degrees"]
                    - comparison["evanescent"]["near"]["phase_error_degrees"]
                ),
                "evanescent_far_degrees": (
                    report["evanescent"]["far"]["phase_error_degrees"]
                    - comparison["evanescent"]["far"]["phase_error_degrees"]
                ),
                "rigid_wall_degrees": (
                    report["rigid_wall"][
                        "intended_geometry_phase_error_degrees"
                    ]
                    - comparison["rigid_wall"][
                        "intended_geometry_phase_error_degrees"
                    ]
                ),
            }
            maximum_magnitude_delta_db = max(
                abs(value) for value in magnitude_deltas.values()
            )
            maximum_phase_delta_degrees = max(
                abs(value) for value in phase_deltas.values()
            )
            convergence_passed = (
                maximum_magnitude_delta_db <= 0.5
                and maximum_phase_delta_degrees <= 5.0
            )
            report["spatial_comparison"] = {
                "primary_points_per_wavelength": (
                    arguments.points_per_wavelength
                ),
                "comparison_points_per_wavelength": (
                    arguments.compare_points_per_wavelength
                ),
                "fixed_physical_source_receiver_coordinates": True,
                "magnitude_deltas": magnitude_deltas,
                "phase_deltas": phase_deltas,
                "maximum_magnitude_delta_db": maximum_magnitude_delta_db,
                "maximum_phase_delta_degrees": maximum_phase_delta_degrees,
                "gates": {
                    "maximum_magnitude_delta_db": 0.5,
                    "maximum_phase_delta_degrees": 5.0,
                },
                "passed": convergence_passed,
            }
            report["summary"]["passed"] = bool(
                report["summary"]["passed"] and convergence_passed
            )
    except ValueError as error:
        parser.error(str(error))
    if arguments.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        summary = report["summary"]
        print(
            "2.5D Helmholtz: "
            f"passed={summary['passed']} "
            f"free|max|={summary['free_field_max_magnitude_error_db']:.6f} dB "
            f"free|phase|={summary['free_field_max_phase_error_degrees']:.6f} deg "
            f"spread={summary['free_field_relative_spreading_error_db']:.6f} dB "
            f"evanescent|max|={summary['evanescent_max_magnitude_error_db']:.6f} dB "
            f"reciprocity={summary['reciprocity_relative_complex_error']:.3e} "
            f"wall|max|={summary['rigid_wall_magnitude_error_db']:.6f} dB "
            f"wall|phase|={summary['rigid_wall_phase_error_degrees']:.6f} deg"
        )
    return 0 if report["summary"]["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
