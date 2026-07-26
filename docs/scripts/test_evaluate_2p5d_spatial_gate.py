#!/usr/bin/env python3
"""Regression tests for the 2.5D spatial-convergence evaluator."""

from __future__ import annotations

import copy
import math
import unittest

from evaluate_2p5d_spatial_gate import InvalidReference, evaluate_pair


def reference_report(
    cell_size_m: float,
    thickness_cells: int,
    pressure: complex,
) -> dict[str, object]:
    return {
        "complex_phase_convention": "outgoing exp(+i*k*r)",
        "domain": {
            "maximum_x_m": 1.8125,
            "maximum_z_m": 1.2125,
            "minimum_x_m": 0.1875,
            "minimum_z_m": 0.1875,
            "pml_width_m": 0.0625,
        },
        "frequency_hz": 4000.0,
        "geometry": {
            "receiver_x_m": 1.6125,
            "receiver_z_m": 0.6,
            "screen_back_x_m": 1.0125,
            "screen_front_x_m": 0.9875,
            "screen_thickness_m": 0.025,
            "screen_top_z_m": 0.8,
            "source_x_m": 0.3875,
            "source_z_m": 0.6,
        },
        "grid": {
            "cell_size_m": cell_size_m,
            "thickness_cells": thickness_cells,
        },
        "model": "2.5d-point-source-helmholtz-double-edge",
        "passed": True,
        "refined_intervals_per_branch": 192,
        "refined_raw_imaginary": pressure.imag,
        "refined_raw_real": pressure.real,
        "unique_helmholtz_solves": 384,
        "schema_version": 1,
        "speed_of_sound_m_per_s": 343.0,
    }


class SpatialGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.reference = reference_report(0.003125, 8, 1.0 + 0.0j)

    def candidate(self, magnitude_db: float, phase_degrees: float):
        magnitude = 10.0 ** (magnitude_db / 20.0)
        phase = math.radians(phase_degrees)
        pressure = magnitude * complex(math.cos(phase), math.sin(phase))
        return reference_report(
            0.025 / 12.0,
            12,
            pressure,
        )

    def test_accepts_magnitude_and_phase_inside_gate(self) -> None:
        report = evaluate_pair(
            self.reference,
            self.candidate(0.25, -4.0),
            0.5,
            5.0,
        )
        self.assertTrue(report["passed"])
        self.assertAlmostEqual(report["magnitude_delta_db"], 0.25)
        self.assertAlmostEqual(report["phase_delta_degrees"], -4.0)

    def test_rejects_phase_even_when_magnitude_passes(self) -> None:
        report = evaluate_pair(
            self.reference,
            self.candidate(0.0141563632, -6.2083804136),
            0.5,
            5.0,
        )
        self.assertFalse(report["passed"])
        self.assertAlmostEqual(
            report["magnitude_delta_db"],
            0.0141563632,
        )
        self.assertAlmostEqual(
            report["phase_delta_degrees"],
            -6.2083804136,
        )

    def test_rejects_quadrature_failure(self) -> None:
        candidate = self.candidate(0.0, 0.0)
        candidate["passed"] = False
        with self.assertRaisesRegex(
            InvalidReference,
            "internal quadrature gate",
        ):
            evaluate_pair(self.reference, candidate, 0.5, 5.0)

    def test_rejects_different_physical_thickness(self) -> None:
        candidate = self.candidate(0.0, 0.0)
        candidate_grid = copy.deepcopy(candidate["grid"])
        candidate_grid["thickness_cells"] = 11
        candidate["grid"] = candidate_grid
        with self.assertRaisesRegex(
            InvalidReference,
            "physical screen thickness",
        ):
            evaluate_pair(self.reference, candidate, 0.5, 5.0)

    def test_rejects_different_geometry(self) -> None:
        candidate = self.candidate(0.0, 0.0)
        candidate_geometry = copy.deepcopy(candidate["geometry"])
        candidate_geometry["source_x_m"] = 0.4
        candidate["geometry"] = candidate_geometry
        with self.assertRaisesRegex(
            InvalidReference,
            "geometry.source_x_m",
        ):
            evaluate_pair(self.reference, candidate, 0.5, 5.0)

    def test_rejects_different_phase_convention(self) -> None:
        candidate = self.candidate(0.0, 0.0)
        candidate["complex_phase_convention"] = "outgoing exp(-i*k*r)"
        with self.assertRaisesRegex(
            InvalidReference,
            "complex_phase_convention differs",
        ):
            evaluate_pair(self.reference, candidate, 0.5, 5.0)

    def test_rejects_boolean_numeric_field(self) -> None:
        candidate = self.candidate(0.0, 0.0)
        candidate["frequency_hz"] = True
        with self.assertRaisesRegex(
            InvalidReference,
            "frequency_hz must be a finite number",
        ):
            evaluate_pair(self.reference, candidate, 0.5, 5.0)

    def test_rejects_coarser_candidate(self) -> None:
        candidate = copy.deepcopy(self.reference)
        candidate_grid = copy.deepcopy(candidate["grid"])
        candidate_grid["cell_size_m"] = 0.00625
        candidate_grid["thickness_cells"] = 4
        candidate["grid"] = candidate_grid
        with self.assertRaisesRegex(
            InvalidReference,
            "candidate grid must be finer",
        ):
            evaluate_pair(self.reference, candidate, 0.5, 5.0)


if __name__ == "__main__":
    unittest.main()
