#!/usr/bin/env python3
"""Tests for the native Minecraft VoxelShape fixture verifier."""

from __future__ import annotations

import copy
import json
import unittest
from pathlib import Path

from verify_minecraft_voxel_shape_local_plane import sha256, verify


ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "build/research/minecraft-voxel-shape-local-plane-v1.json"
CONTRACT = ROOT / "docs/acoustics/local-plane-reflection-contract-v1.json"


class MinecraftVoxelShapeLocalPlaneVerifierTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        if not REPORT.is_file():
            raise unittest.SkipTest("generate the native VoxelShape report first")
        cls.report = json.loads(REPORT.read_text(encoding="utf-8"))

    def test_accepts_native_fixture(self) -> None:
        result = verify(
            self.report, sha256(REPORT), sha256(CONTRACT)
        )
        self.assertEqual(
            result["status"],
            "verified-minecraft-voxel-shape-local-plane-reference",
        )

    def test_rejects_slab_quantization(self) -> None:
        mutated = copy.deepcopy(self.report)
        mutated["fixtures"][1]["optimized_aabbs"][0][4] = 1.0
        with self.assertRaises(ValueError):
            verify(mutated, sha256(REPORT), sha256(CONTRACT))

    def test_rejects_client_start_overclaim(self) -> None:
        mutated = copy.deepcopy(self.report)
        mutated["minecraft_client_started"] = True
        with self.assertRaises(ValueError):
            verify(mutated, sha256(REPORT), sha256(CONTRACT))


if __name__ == "__main__":
    unittest.main()
