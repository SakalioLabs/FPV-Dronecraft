#!/usr/bin/env python3
"""Tests for guarded 2.5D wave golden promotion."""

from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from finalize_2p5d_spatial_gate import write_complete_report
from promote_2p5d_wave_golden import InvalidPromotion, golden_fixture


def eligible_report() -> dict[str, object]:
    return {
        "batch_run": {
            "remaining_solves": 0,
            "status": "complete",
            "target_solves": 384,
            "total_unique_helmholtz_solves": 384,
        },
        "candidate_summary": {
            "frequency_hz": 4000.0,
            "schema_version": 1,
        },
        "passed": True,
        "promotion_eligible": True,
        "spatial_gate": {
            "magnitude_delta_db": 0.1,
            "passed": True,
            "phase_delta_degrees": 2.0,
        },
        "status": "complete",
    }


class WaveGoldenPromotionTest(unittest.TestCase):
    def test_builds_deterministic_fixture_from_eligible_report(self) -> None:
        first = golden_fixture(eligible_report())
        second = golden_fixture(copy.deepcopy(eligible_report()))
        self.assertEqual(first, second)
        self.assertEqual(
            first["fixture_kind"],
            "2.5d-double-edge-wave-golden",
        )
        self.assertNotIn("candidate_report", first)

    def test_rejects_spatial_failure(self) -> None:
        report = eligible_report()
        report["passed"] = False
        report["promotion_eligible"] = False
        report["spatial_gate"]["passed"] = False
        with self.assertRaisesRegex(
            InvalidPromotion,
            "spatial gate did not pass",
        ):
            golden_fixture(report)

    def test_rejects_incomplete_batch_run(self) -> None:
        report = eligible_report()
        report["batch_run"]["remaining_solves"] = 1
        with self.assertRaisesRegex(
            InvalidPromotion,
            "batch run is not complete",
        ):
            golden_fixture(report)

    def test_fixture_can_be_atomically_persisted(self) -> None:
        fixture = golden_fixture(eligible_report())
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = Path(temporary_directory) / "golden.json"
            self.assertTrue(write_complete_report(fixture, output))
            self.assertEqual(
                json.loads(output.read_text(encoding="utf-8")),
                fixture,
            )


if __name__ == "__main__":
    unittest.main()
