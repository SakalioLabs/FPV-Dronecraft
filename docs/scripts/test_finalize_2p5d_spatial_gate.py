#!/usr/bin/env python3
"""Tests for checkpointed 2.5D spatial-gate finalization."""

from __future__ import annotations

import json
import math
import tempfile
import unittest
from pathlib import Path

from finalize_2p5d_spatial_gate import finalize_run, write_complete_report


def wave_report(
    cell_size_m: float,
    thickness_cells: int,
    pressure: complex,
    *,
    status: str | None = None,
) -> dict[str, object]:
    report: dict[str, object] = {
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
            "points_per_wavelength": 41.16,
            "thickness_cells": thickness_cells,
            "unknowns_per_solve": 378961,
        },
        "coarse_intervals_per_branch": 96,
        "magnitude_delta_db": 0.0039,
        "model": "2.5d-point-source-helmholtz-double-edge",
        "passed": True,
        "phase_delta_degrees": -0.1085,
        "refined_intervals_per_branch": 192,
        "refined_normalized_db": -16.2857,
        "refined_raw_imaginary": pressure.imag,
        "refined_raw_real": pressure.real,
        "unique_helmholtz_solves": 384,
        "schema_version": 1,
        "speed_of_sound_m_per_s": 343.0,
    }
    if status is not None:
        report["status"] = status
    return report


class SpatialGateFinalizationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.directory = Path(self.temporary_directory.name)
        self.reference_path = self.directory / "reference.json"
        self.reference_path.write_text(
            json.dumps(wave_report(0.003125, 8, 1.0 + 0.0j)),
            encoding="utf-8",
        )
        self.batch_directory = self.directory / "batches"
        self.batch_directory.mkdir()
        self.label = "candidate"

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def write_candidate(
        self,
        pressure: complex,
        *,
        terminal: bool,
    ) -> None:
        output = self.batch_directory / f"{self.label}-batch01.json"
        if terminal:
            output.write_text(
                json.dumps(
                    wave_report(
                        0.025 / 12.0,
                        12,
                        pressure,
                        status="complete",
                    )
                ),
                encoding="utf-8",
            )
        else:
            output.write_text("", encoding="utf-8")
        output.with_name(
            f"{self.label}-batch01.stderr.txt"
        ).write_text("", encoding="utf-8")

    def test_reports_not_ready_for_active_batch(self) -> None:
        self.write_candidate(1.0 + 0.0j, terminal=False)
        report = finalize_run(
            self.reference_path,
            self.batch_directory,
            self.label,
            384,
            0.5,
            5.0,
        )
        self.assertEqual(report["status"], "not-ready")
        self.assertIsNone(report["passed"])

    def test_accepts_completed_spatial_gate(self) -> None:
        phase = math.radians(2.0)
        self.write_candidate(
            complex(math.cos(phase), math.sin(phase)),
            terminal=True,
        )
        report = finalize_run(
            self.reference_path,
            self.batch_directory,
            self.label,
            384,
            0.5,
            5.0,
        )
        self.assertEqual(report["status"], "complete")
        self.assertTrue(report["passed"])
        self.assertTrue(report["promotion_eligible"])
        self.assertEqual(
            report["candidate_summary"]["schema_version"],
            1,
        )
        self.assertIsInstance(
            report["candidate_summary"]["grid"]["thickness_cells"],
            int,
        )
        self.assertIsInstance(
            report["candidate_summary"]["unique_helmholtz_solves"],
            int,
        )
        self.assertEqual(
            report["candidate_summary"]["pressure"]["raw_real"],
            math.cos(phase),
        )
        self.assertAlmostEqual(
            report["spatial_gate"]["phase_delta_degrees"],
            2.0,
        )

    def test_preserves_completed_spatial_failure(self) -> None:
        phase = math.radians(6.0)
        self.write_candidate(
            complex(math.cos(phase), math.sin(phase)),
            terminal=True,
        )
        report = finalize_run(
            self.reference_path,
            self.batch_directory,
            self.label,
            384,
            0.5,
            5.0,
        )
        self.assertEqual(report["status"], "complete")
        self.assertFalse(report["passed"])
        self.assertFalse(report["promotion_eligible"])
        self.assertAlmostEqual(
            report["spatial_gate"]["phase_delta_degrees"],
            6.0,
        )

    def test_atomically_persists_complete_report(self) -> None:
        output_path = self.directory / "evidence" / "final.json"
        report = {"status": "complete", "passed": False}
        self.assertTrue(write_complete_report(report, output_path))
        self.assertEqual(
            json.loads(output_path.read_text(encoding="utf-8")),
            report,
        )
        self.assertFalse(output_path.with_name("final.json.tmp").exists())

    def test_not_ready_does_not_overwrite_prior_evidence(self) -> None:
        output_path = self.directory / "final.json"
        output_path.write_text('{"prior": true}\n', encoding="utf-8")
        report = {"status": "not-ready", "passed": None}
        self.assertFalse(write_complete_report(report, output_path))
        self.assertEqual(
            json.loads(output_path.read_text(encoding="utf-8")),
            {"prior": True},
        )


if __name__ == "__main__":
    unittest.main()
