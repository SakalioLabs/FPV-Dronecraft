#!/usr/bin/env python3
"""Validate nested axial-wavenumber quadrature for the 25 mm screen.

The existing generator uses Gauss-Legendre rules, whose nodes are not reused
when the order changes.  This harness uses nested Clenshaw-Curtis nodes for
both transformed branches.  Doubling the interval count retains every old
Helmholtz sample and evaluates only the new odd-index nodes.
"""

from __future__ import annotations

import argparse
import gc
import json
import math
import os
import time
from dataclasses import asdict, dataclass
from fractions import Fraction
from pathlib import Path

import numpy as np

from generate_2p5d_double_edge_reference import (
    EVANESCENT_DECAY_ARGUMENT,
    RECEIVER,
    SCREEN_BACK_X_M,
    SCREEN_FRONT_X_M,
    SCREEN_THICKNESS_M,
    SCREEN_TOP_Z_M,
    SOURCE,
    DomainBounds,
    complex_delta,
    grid_for_thickness_cells,
)
from validate_2p5d_helmholtz import (
    PmlHelmholtz2d,
    SPEED_OF_SOUND_METERS_PER_SECOND,
)


@dataclass(frozen=True)
class NestedQuadratureSummary:
    frequency_hz: float
    thickness_cells: int
    coarse_intervals_per_branch: int
    refined_intervals_per_branch: int
    coarse_normalized_db: float
    refined_normalized_db: float
    magnitude_delta_db: float
    phase_delta_degrees: float
    refined_raw_real: float
    refined_raw_imaginary: float
    unique_helmholtz_solves: int
    reused_integrand_samples: int
    solve_seconds: float
    unknowns_per_solve: int
    passed: bool


class SolveBudgetExhausted(RuntimeError):
    """Raised after a bounded batch has persisted all completed samples."""


def clenshaw_curtis_weights(intervals: int) -> np.ndarray:
    """Return weights on [-1, 1] for N+1 cosine-spaced nodes."""
    if intervals < 2 or intervals % 2:
        raise ValueError("Clenshaw-Curtis intervals must be positive and even")
    angles = math.pi * np.arange(intervals + 1) / intervals
    interior = np.arange(1, intervals)
    weights = np.zeros(intervals + 1, dtype=np.float64)
    accumulator = np.ones(intervals - 1, dtype=np.float64)
    weights[0] = 1.0 / (intervals * intervals - 1.0)
    weights[-1] = weights[0]
    for harmonic in range(1, intervals // 2):
        accumulator -= (
            2.0
            * np.cos(2.0 * harmonic * angles[interior])
            / (4.0 * harmonic * harmonic - 1.0)
        )
    accumulator -= (
        np.cos(intervals * angles[interior])
        / (intervals * intervals - 1.0)
    )
    weights[interior] = 2.0 * accumulator / intervals
    return weights


class NestedDoubleEdgeTransform:
    def __init__(
        self,
        frequency_hz: float,
        thickness_cells: int,
        domain: DomainBounds,
        checkpoint_path: Path | None = None,
        maximum_new_solves: int | None = None,
    ):
        self.frequency_hz = frequency_hz
        self.grid, self.solid, self.grid_diagnostics = (
            grid_for_thickness_cells(
                thickness_cells,
                frequency_hz,
                domain,
            )
        )
        self.wave_number = (
            2.0
            * math.pi
            * frequency_hz
            / SPEED_OF_SOUND_METERS_PER_SECOND
        )
        self.source_edge_distance_m = math.hypot(
            SCREEN_FRONT_X_M - SOURCE.x_m,
            SCREEN_TOP_Z_M - SOURCE.z_m,
        )
        self.receiver_edge_distance_m = math.hypot(
            RECEIVER.x_m - SCREEN_BACK_X_M,
            SCREEN_TOP_Z_M - RECEIVER.z_m,
        )
        self.shortest_path_m = (
            self.source_edge_distance_m
            + SCREEN_THICKNESS_M
            + self.receiver_edge_distance_m
        )
        self.maximum_u = math.asinh(
            EVANESCENT_DECAY_ARGUMENT
            / (
                self.wave_number
                * min(
                    self.source_edge_distance_m,
                    self.receiver_edge_distance_m,
                )
            )
        )
        self.checkpoint_path = checkpoint_path
        self.maximum_new_solves = maximum_new_solves
        self.cache: dict[tuple[str, Fraction], complex] = {}
        self.solved_keys: set[tuple[str, Fraction]] = set()
        self.solve_count = 0
        self.reuse_count = 0
        self.solve_seconds = 0.0
        self.checkpoint_metadata = {
            "schema": 1,
            "frequency_hz": frequency_hz,
            "thickness_cells": thickness_cells,
            "domain": asdict(domain),
            "cell_size_m": self.grid.cell_size_m,
            "width_cells": self.grid.width_cells,
            "height_cells": self.grid.height_cells,
            "origin_x_m": self.grid.origin_x_m,
            "origin_z_m": self.grid.origin_z_m,
        }
        self._load_checkpoint()

    def pressure(self, intervals: int) -> complex:
        propagating = self._integrate_branch(
            "propagating",
            intervals,
            0.0,
            0.5 * math.pi,
        )
        evanescent = self._integrate_branch(
            "evanescent",
            intervals,
            0.0,
            self.maximum_u,
        )
        physical_green = (propagating + evanescent) / math.pi
        return 4.0 * math.pi * physical_green

    def _integrate_branch(
        self,
        branch: str,
        intervals: int,
        lower: float,
        upper: float,
    ) -> complex:
        weights = clenshaw_curtis_weights(intervals)
        half_span = 0.5 * (upper - lower)
        midpoint = 0.5 * (upper + lower)
        result = 0.0j
        for index, weight in enumerate(weights):
            fraction = Fraction(index, intervals)
            key = (branch, fraction)
            if key in self.cache:
                integrand = self.cache[key]
                self.reuse_count += 1
            else:
                cosine_node = math.cos(math.pi * index / intervals)
                coordinate = midpoint + half_span * cosine_node
                integrand, used_solver = self._integrand(
                    branch,
                    coordinate,
                )
                self.cache[key] = integrand
                if used_solver:
                    self.solved_keys.add(key)
                self._write_checkpoint()
            result += half_span * weight * integrand
        return result

    def _integrand(
        self,
        branch: str,
        coordinate: float,
    ) -> tuple[complex, bool]:
        if branch == "propagating":
            jacobian = self.wave_number * math.cos(coordinate)
            if abs(jacobian) < 1.0e-14:
                return 0.0j, False
            axial_wavenumber = self.wave_number * math.sin(coordinate)
        elif branch == "evanescent":
            jacobian = self.wave_number * math.sinh(coordinate)
            if abs(jacobian) < 1.0e-14:
                return 0.0j, False
            axial_wavenumber = self.wave_number * math.cosh(coordinate)
        else:
            raise ValueError(f"unknown branch: {branch}")
        if (
            self.maximum_new_solves is not None
            and self.solve_count >= self.maximum_new_solves
        ):
            raise SolveBudgetExhausted
        started = time.perf_counter()
        solver = PmlHelmholtz2d(
            self.grid,
            self.frequency_hz,
            axial_wavenumber,
            self.solid,
        )
        solution = solver.solve_physical([SOURCE])
        value = solver.value_physical(solution, RECEIVER)
        self.solve_seconds += time.perf_counter() - started
        self.solve_count += 1
        del solution
        del solver
        gc.collect()
        return jacobian * value, True

    @staticmethod
    def _serialize_key(key: tuple[str, Fraction]) -> str:
        branch, fraction = key
        return (
            f"{branch}:{fraction.numerator}/{fraction.denominator}"
        )

    @staticmethod
    def _deserialize_key(value: str) -> tuple[str, Fraction]:
        branch, fraction = value.split(":", maxsplit=1)
        numerator, denominator = fraction.split("/", maxsplit=1)
        return branch, Fraction(int(numerator), int(denominator))

    def _load_checkpoint(self) -> None:
        if self.checkpoint_path is None or not self.checkpoint_path.exists():
            return
        checkpoint = json.loads(
            self.checkpoint_path.read_text(encoding="utf-8")
        )
        if checkpoint.get("metadata") != self.checkpoint_metadata:
            raise ValueError("checkpoint metadata does not match this run")
        for serialized_key, sample in checkpoint["samples"].items():
            key = self._deserialize_key(serialized_key)
            self.cache[key] = complex(sample["real"], sample["imaginary"])
            if sample["used_solver"]:
                self.solved_keys.add(key)

    def _write_checkpoint(self) -> None:
        if self.checkpoint_path is None:
            return
        self.checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
        samples = {}
        for key, value in sorted(
            self.cache.items(),
            key=lambda item: self._serialize_key(item[0]),
        ):
            samples[self._serialize_key(key)] = {
                "real": value.real,
                "imaginary": value.imag,
                "used_solver": key in self.solved_keys,
            }
        checkpoint = {
            "metadata": self.checkpoint_metadata,
            "samples": samples,
        }
        temporary_path = self.checkpoint_path.with_suffix(
            self.checkpoint_path.suffix + ".tmp"
        )
        temporary_path.write_text(
            json.dumps(checkpoint, indent=2, sort_keys=True),
            encoding="utf-8",
        )
        for attempt in range(20):
            try:
                os.replace(temporary_path, self.checkpoint_path)
                return
            except PermissionError:
                if attempt == 19:
                    raise
                # Windows can briefly deny replace while a read-only
                # progress monitor or virus scanner holds the destination.
                time.sleep(0.05 * (attempt + 1))


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate nested quadrature for the 25 mm screen."
    )
    parser.add_argument("--frequency-hz", type=float, default=2_000.0)
    parser.add_argument("--thickness-cells", type=int, default=6)
    parser.add_argument("--intervals", type=int, default=24)
    parser.add_argument("--refined-intervals", type=int, default=48)
    parser.add_argument("--domain-minimum-x-m", type=float, default=0.125)
    parser.add_argument("--domain-maximum-x-m", type=float, default=1.875)
    parser.add_argument("--domain-minimum-z-m", type=float, default=0.125)
    parser.add_argument("--domain-maximum-z-m", type=float, default=1.275)
    parser.add_argument("--pml-width-m", type=float, default=0.125)
    parser.add_argument(
        "--checkpoint",
        type=Path,
        help="Atomically persist and resume transformed integrand samples.",
    )
    parser.add_argument(
        "--maximum-new-solves",
        type=int,
        help="Stop cleanly after this many new Helmholtz solves.",
    )
    parser.add_argument("--json", action="store_true")
    arguments = parser.parse_args()
    if not arguments.frequency_hz > 0.0:
        parser.error("--frequency-hz must be positive")
    if arguments.thickness_cells < 1:
        parser.error("--thickness-cells must be positive")
    if arguments.intervals < 2 or arguments.intervals % 2:
        parser.error("--intervals must be positive and even")
    if arguments.refined_intervals != 2 * arguments.intervals:
        parser.error("--refined-intervals must equal twice --intervals")
    if (
        arguments.maximum_new_solves is not None
        and arguments.maximum_new_solves < 1
    ):
        parser.error("--maximum-new-solves must be positive")
    try:
        domain = DomainBounds(
            minimum_x_m=arguments.domain_minimum_x_m,
            maximum_x_m=arguments.domain_maximum_x_m,
            minimum_z_m=arguments.domain_minimum_z_m,
            maximum_z_m=arguments.domain_maximum_z_m,
            pml_width_m=arguments.pml_width_m,
        )
        transform = NestedDoubleEdgeTransform(
            arguments.frequency_hz,
            arguments.thickness_cells,
            domain,
            checkpoint_path=arguments.checkpoint,
            maximum_new_solves=arguments.maximum_new_solves,
        )
    except ValueError as error:
        parser.error(str(error))
    try:
        coarse = transform.pressure(arguments.intervals)
        refined = transform.pressure(arguments.refined_intervals)
    except SolveBudgetExhausted:
        partial_report = {
            "status": "incomplete",
            "checkpoint": (
                str(arguments.checkpoint)
                if arguments.checkpoint is not None
                else None
            ),
            "cached_integrand_samples": len(transform.cache),
            "total_unique_helmholtz_solves": len(
                transform.solved_keys
            ),
            "new_helmholtz_solves": transform.solve_count,
            "reused_integrand_samples": transform.reuse_count,
            "solve_seconds": transform.solve_seconds,
        }
        if arguments.json:
            print(json.dumps(partial_report, indent=2, sort_keys=True))
        else:
            print(
                "nested double-edge quadrature: status=incomplete "
                f"cached={len(transform.cache)} "
                f"total_solves={len(transform.solved_keys)} "
                f"new_solves={transform.solve_count}"
            )
        return 3
    magnitude_delta_db, phase_delta_degrees = complex_delta(
        refined,
        coarse,
    )
    coarse_normalized_db = 20.0 * math.log10(
        abs(coarse) * transform.shortest_path_m
    )
    refined_normalized_db = 20.0 * math.log10(
        abs(refined) * transform.shortest_path_m
    )
    passed = (
        abs(magnitude_delta_db) <= 0.25
        and abs(phase_delta_degrees) <= 2.0
    )
    summary = NestedQuadratureSummary(
        frequency_hz=arguments.frequency_hz,
        thickness_cells=arguments.thickness_cells,
        coarse_intervals_per_branch=arguments.intervals,
        refined_intervals_per_branch=arguments.refined_intervals,
        coarse_normalized_db=coarse_normalized_db,
        refined_normalized_db=refined_normalized_db,
        magnitude_delta_db=magnitude_delta_db,
        phase_delta_degrees=phase_delta_degrees,
        refined_raw_real=refined.real,
        refined_raw_imaginary=refined.imag,
        unique_helmholtz_solves=len(transform.solved_keys),
        reused_integrand_samples=transform.reuse_count,
        solve_seconds=transform.solve_seconds,
        unknowns_per_solve=transform.grid_diagnostics[
            "unknowns_per_solve"
        ],
        passed=passed,
    )
    report = {
        **asdict(summary),
        "complex_phase_convention": "outgoing exp(+i*k*r)",
        "domain": asdict(domain),
        "geometry": {
            "receiver_x_m": RECEIVER.x_m,
            "receiver_z_m": RECEIVER.z_m,
            "screen_back_x_m": SCREEN_BACK_X_M,
            "screen_front_x_m": SCREEN_FRONT_X_M,
            "screen_thickness_m": SCREEN_THICKNESS_M,
            "screen_top_z_m": SCREEN_TOP_Z_M,
            "source_x_m": SOURCE.x_m,
            "source_z_m": SOURCE.z_m,
        },
        "grid": transform.grid_diagnostics,
        "model": "2.5d-point-source-helmholtz-double-edge",
        "schema_version": 1,
        "speed_of_sound_m_per_s": SPEED_OF_SOUND_METERS_PER_SECOND,
        "gates": {
            "magnitude_delta_db": 0.25,
            "phase_delta_degrees": 2.0,
        },
    }
    if arguments.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print(
            "nested double-edge quadrature: "
            f"passed={passed} "
            f"delta={magnitude_delta_db:+.6f} dB/"
            f"{phase_delta_degrees:+.6f} deg "
            f"refined={refined_normalized_db:+.6f} dB "
            f"solves={transform.solve_count} "
            f"reused={transform.reuse_count} "
            f"seconds={transform.solve_seconds:.3f}"
        )
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
