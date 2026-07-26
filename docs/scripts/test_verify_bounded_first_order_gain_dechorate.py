#!/usr/bin/env python3
"""Policy tests for the D120 exploratory dEchorate verifier."""

from __future__ import annotations

import copy
import json
import unittest
from pathlib import Path

from verify_bounded_first_order_gain_dechorate import enforce


ROOT = Path(__file__).resolve().parents[2]
REPORT = (
    ROOT
    / "build/research/bounded-first-order-gain-dechorate-exploratory-v1.json"
)


class BoundedGainDechorateVerifierTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        if not REPORT.is_file():
            raise unittest.SkipTest("generate the D120 exploratory report first")
        cls.report = json.loads(REPORT.read_text(encoding="utf-8"))

    def test_accepts_preserved_exploratory_failure(self) -> None:
        enforce(self.report)

    def test_rejects_production_promotion(self) -> None:
        mutated = copy.deepcopy(self.report)
        mutated["gates"]["production_candidate_eligible"] = True
        with self.assertRaises(ValueError):
            enforce(mutated)

    def test_rejects_fallback_loss(self) -> None:
        mutated = copy.deepcopy(self.report)
        mutated["spatial_support"]["new_fallback"] = 14
        with self.assertRaises(ValueError):
            enforce(mutated)


if __name__ == "__main__":
    unittest.main()
