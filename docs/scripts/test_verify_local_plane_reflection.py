#!/usr/bin/env python3
"""Tests for the D121 local-plane reflection verifier."""

from __future__ import annotations

import copy
import json
import unittest
from pathlib import Path

from verify_local_plane_reflection import sha256, verify


ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "build/research/local-plane-reflection-v1.json"
CONTRACT = ROOT / "docs/acoustics/local-plane-reflection-contract-v1.json"


class LocalPlaneReflectionVerifierTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        if not REPORT.is_file():
            raise unittest.SkipTest("generate the D121 local-plane report first")
        cls.report = json.loads(REPORT.read_text(encoding="utf-8"))

    def test_accepts_reference(self) -> None:
        result = verify(
            self.report, sha256(REPORT), sha256(CONTRACT)
        )
        self.assertEqual(
            result["status"],
            "verified-local-plane-reflection-reference",
        )

    def test_rejects_subvoxel_plane_mutation(self) -> None:
        mutated = copy.deepcopy(self.report)
        mutated["scenarios"][0]["patch"]["coordinate_m"] = 1.0
        with self.assertRaises(ValueError):
            verify(mutated, sha256(REPORT), sha256(CONTRACT))

    def test_rejects_occlusion_mutation(self) -> None:
        mutated = copy.deepcopy(self.report)
        mutated["scenarios"][1]["result"]["topology_visible"] = True
        with self.assertRaises(ValueError):
            verify(mutated, sha256(REPORT), sha256(CONTRACT))


if __name__ == "__main__":
    unittest.main()
