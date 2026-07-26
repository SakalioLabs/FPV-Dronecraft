#!/usr/bin/env python3
"""Tests for checkpointed 2.5D batch artifact inspection."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from inspect_2p5d_batch_run import InvalidBatchRun, inspect_run


class BatchRunInspectionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.directory = Path(self.temporary_directory.name)
        self.label = "reference"

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def write_batch(
        self,
        index: int,
        report: dict[str, object] | None,
        stderr: str = "",
    ) -> None:
        output = self.directory / f"{self.label}-batch{index:02d}.json"
        output.write_text(
            "" if report is None else json.dumps(report),
            encoding="utf-8",
        )
        output.with_name(
            f"{self.label}-batch{index:02d}.stderr.txt"
        ).write_text(stderr, encoding="utf-8")

    def incomplete_report(self, total: int) -> dict[str, object]:
        return {
            "new_helmholtz_solves": 8,
            "solve_seconds": 400.0,
            "status": "incomplete",
            "total_unique_helmholtz_solves": total,
        }

    def test_reports_healthy_active_run_and_eta(self) -> None:
        self.write_batch(1, self.incomplete_report(9))
        self.write_batch(2, self.incomplete_report(17))
        self.write_batch(3, None)
        report = inspect_run(self.directory, self.label, 25)
        self.assertEqual(report["status"], "healthy-incomplete")
        self.assertEqual(report["active_batch"], 3)
        self.assertEqual(report["total_unique_helmholtz_solves"], 17)
        self.assertEqual(report["remaining_solves"], 8)
        self.assertEqual(report["seconds_per_solve"], 50.0)
        self.assertEqual(report["estimated_remaining_seconds"], 400.0)

    def test_accepts_terminal_report(self) -> None:
        self.write_batch(1, self.incomplete_report(9))
        self.write_batch(
            2,
            {
                "passed": True,
                "status": "complete",
                "unique_helmholtz_solves": 12,
            },
        )
        report = inspect_run(self.directory, self.label, 12)
        self.assertEqual(report["status"], "complete")
        self.assertIsNone(report["active_batch"])
        self.assertEqual(report["remaining_solves"], 0)

    def test_reports_paused_incomplete_run(self) -> None:
        self.write_batch(1, self.incomplete_report(9))
        report = inspect_run(self.directory, self.label, 384)
        self.assertEqual(report["status"], "paused-incomplete")
        self.assertIsNone(report["active_batch"])

    def test_rejects_terminal_count_below_target(self) -> None:
        self.write_batch(
            1,
            {
                "passed": True,
                "status": "complete",
                "unique_helmholtz_solves": 12,
            },
        )
        with self.assertRaisesRegex(
            InvalidBatchRun,
            "does not equal target",
        ):
            inspect_run(self.directory, self.label, 384)

    def test_rejects_nonempty_stderr(self) -> None:
        self.write_batch(
            1,
            self.incomplete_report(9),
            stderr="solver failed",
        )
        with self.assertRaisesRegex(InvalidBatchRun, "not empty"):
            inspect_run(self.directory, self.label, 384)

    def test_rejects_boolean_unique_solve_count(self) -> None:
        report = self.incomplete_report(9)
        report["total_unique_helmholtz_solves"] = True
        self.write_batch(1, report)
        with self.assertRaisesRegex(
            InvalidBatchRun,
            "invalid unique solve count",
        ):
            inspect_run(self.directory, self.label, 384)

    def test_rejects_batch_gap(self) -> None:
        self.write_batch(1, self.incomplete_report(9))
        self.write_batch(3, None)
        with self.assertRaisesRegex(InvalidBatchRun, "sequence has gaps"):
            inspect_run(self.directory, self.label, 384)


if __name__ == "__main__":
    unittest.main()
