#!/usr/bin/env python3
"""Tests for the D120 independent bounded-gain verifier."""

from __future__ import annotations

import copy
import json
import unittest
from pathlib import Path

from verify_bounded_first_order_gain import verify


ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "build/research/bounded-first-order-gain-reference-v1.json"
CONTRACT = ROOT / "docs/acoustics/bounded-first-order-gain-contract-v1.json"


class BoundedFirstOrderGainVerifierTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        if not REPORT.is_file():
            raise unittest.SkipTest("generate the D120 Java report first")
        cls.report = json.loads(REPORT.read_text(encoding="utf-8"))

    def test_accepts_reference(self) -> None:
        from verify_bounded_first_order_gain import sha256

        result = verify(
            self.report,
            sha256(REPORT),
            sha256(CONTRACT),
        )
        self.assertEqual(
            result["status"],
            "verified-bounded-first-order-gain-reference",
        )

    def test_rejects_gain_mutation(self) -> None:
        from verify_bounded_first_order_gain import sha256

        mutated = copy.deepcopy(self.report)
        mutated["unsupported_gain"]["mid"][3] += 0.01
        with self.assertRaises(ValueError):
            verify(mutated, sha256(REPORT), sha256(CONTRACT))

    def test_rejects_claim_boundary_mutation(self) -> None:
        from verify_bounded_first_order_gain import sha256

        mutated = copy.deepcopy(self.report)
        mutated["cuda_executed"] = True
        with self.assertRaises(ValueError):
            verify(mutated, sha256(REPORT), sha256(CONTRACT))


if __name__ == "__main__":
    unittest.main()
