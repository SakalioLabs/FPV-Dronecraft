#!/usr/bin/env python3
"""Tests for the D121 independent energy-ledger verifier."""

from __future__ import annotations

import copy
import json
import unittest
from pathlib import Path

from verify_minecraft_early_late_energy_ledger import sha256, verify


ROOT = Path(__file__).resolve().parents[2]
REPORT = (
    ROOT / "build/research/minecraft-early-late-energy-ledger-v1.json"
)
CONTRACT = (
    ROOT
    / "docs/acoustics/minecraft-early-late-energy-ledger-contract-v1.json"
)


class MinecraftEarlyLateEnergyLedgerVerifierTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        if not REPORT.is_file():
            raise unittest.SkipTest("generate the D121 Java report first")
        cls.report = json.loads(REPORT.read_text(encoding="utf-8"))

    def test_accepts_reference(self) -> None:
        result = verify(
            self.report, sha256(REPORT), sha256(CONTRACT)
        )
        self.assertEqual(
            result["status"],
            "verified-minecraft-early-late-energy-ledger-reference",
        )

    def test_rejects_double_spend(self) -> None:
        mutated = copy.deepcopy(self.report)
        mutated["scenarios"][0]["late_residual"][0] += 0.01
        with self.assertRaises(ValueError):
            verify(mutated, sha256(REPORT), sha256(CONTRACT))

    def test_rejects_live_integration_overclaim(self) -> None:
        mutated = copy.deepcopy(self.report)
        mutated["minecraft_integration_enabled"] = True
        with self.assertRaises(ValueError):
            verify(mutated, sha256(REPORT), sha256(CONTRACT))


if __name__ == "__main__":
    unittest.main()
