#!/usr/bin/env python3
"""Tests for promoted 2.5D wave golden verification."""

from __future__ import annotations

import copy
import math
import unittest

from promote_2p5d_wave_golden import golden_fixture
from verify_2p5d_wave_golden import InvalidGolden, verify_fixture


def valid_fixture() -> dict[str, object]:
    geometry = {
        "receiver_x_m": 1.6125,
        "receiver_z_m": 0.6,
        "screen_back_x_m": 1.0125,
        "screen_front_x_m": 0.9875,
        "screen_thickness_m": 0.025,
        "screen_top_z_m": 0.8,
        "source_x_m": 0.3875,
        "source_z_m": 0.6,
    }
    raw = complex(0.05, 0.1)
    shortest_path = (
        math.hypot(0.9875 - 0.3875, 0.8 - 0.6)
        + 0.025
        + math.hypot(1.6125 - 1.0125, 0.8 - 0.6)
    )
    return {
        "candidate": {
            "complex_phase_convention": "outgoing exp(+i*k*r)",
            "domain": {
                "maximum_x_m": 1.8125,
                "maximum_z_m": 1.2125,
                "minimum_x_m": 0.1875,
                "minimum_z_m": 0.1875,
                "pml_width_m": 0.0625,
            },
            "frequency_hz": 4000.0,
            "geometry": geometry,
            "grid": {
                "cell_size_m": 0.025 / 18.0,
                "points_per_wavelength": 61.74,
                "thickness_cells": 18,
                "unknowns_per_solve": 853633,
            },
            "model": "2.5d-point-source-helmholtz-double-edge",
            "pressure": {
                "normalized_db": 20.0
                * math.log10(abs(raw) * shortest_path),
                "raw_imaginary": raw.imag,
                "raw_real": raw.real,
            },
            "quadrature": {
                "coarse_intervals_per_branch": 96,
                "magnitude_delta_db": 0.01,
                "phase_delta_degrees": 0.1,
                "refined_intervals_per_branch": 192,
            },
            "schema_version": 1,
            "speed_of_sound_m_per_s": 343.0,
            "unique_helmholtz_solves": 384,
        },
        "fixture_kind": "2.5d-double-edge-wave-golden",
        "provenance": {
            "generator": (
                "docs/scripts/finalize_2p5d_spatial_gate.py"
            ),
            "promotion_rule": (
                "terminal quadrature plus 12/18-cell complex spatial gate"
            ),
        },
        "schema_version": 1,
        "spatial_gate": {
            "candidate_cell_size_m": 0.025 / 18.0,
            "candidate_raw_imaginary": raw.imag,
            "candidate_raw_real": raw.real,
            "magnitude_delta_db": 0.2,
            "passed": True,
            "phase_delta_degrees": 3.0,
        },
        "status": "complete",
    }


class WaveGoldenVerificationTest(unittest.TestCase):
    def test_accepts_self_consistent_fixture(self) -> None:
        report = verify_fixture(valid_fixture())
        self.assertEqual(report["status"], "valid")
        self.assertEqual(report["frequency_hz"], 4000.0)

    def test_rejects_modified_normalized_level(self) -> None:
        fixture = valid_fixture()
        fixture["candidate"]["pressure"]["normalized_db"] += 0.01
        with self.assertRaisesRegex(
            InvalidGolden,
            "normalized_db",
        ):
            verify_fixture(fixture)

    def test_rejects_spatial_pressure_mismatch(self) -> None:
        fixture = valid_fixture()
        fixture["spatial_gate"]["candidate_raw_real"] += 0.01
        with self.assertRaisesRegex(
            InvalidGolden,
            "candidate_raw_real",
        ):
            verify_fixture(fixture)

    def test_rejects_nonconverged_spatial_gate(self) -> None:
        fixture = valid_fixture()
        fixture["spatial_gate"]["phase_delta_degrees"] = 5.1
        with self.assertRaisesRegex(
            InvalidGolden,
            "spatial gate exceeds",
        ):
            verify_fixture(fixture)

    def test_accepts_output_of_promotion_tool(self) -> None:
        fixture_data = valid_fixture()
        finalizer_report = {
            "batch_run": {
                "remaining_solves": 0,
                "status": "complete",
                "target_solves": 384,
                "total_unique_helmholtz_solves": 384,
            },
            "candidate_summary": fixture_data["candidate"],
            "passed": True,
            "promotion_eligible": True,
            "spatial_gate": fixture_data["spatial_gate"],
            "status": "complete",
        }
        promoted = golden_fixture(finalizer_report)
        self.assertEqual(verify_fixture(promoted)["status"], "valid")


if __name__ == "__main__":
    unittest.main()
