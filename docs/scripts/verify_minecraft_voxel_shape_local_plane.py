#!/usr/bin/env python3
"""Verify the executed native Minecraft VoxelShape local-plane fixture."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

from verify_local_plane_reflection import extract_union


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(
    report: dict[str, Any],
    report_sha: str,
    contract_sha: str,
) -> dict[str, Any]:
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "valid-minecraft-voxel-shape-local-plane-reference"
        or report.get("minecraft_version") != "1.21.11"
        or report.get("source_contract_sha256") != contract_sha
        or len(report.get("fixtures", [])) != 4
    ):
        raise ValueError("Minecraft VoxelShape fixture identity changed")
    fixtures = {fixture["name"]: fixture for fixture in report["fixtures"]}
    for name, fixture in fixtures.items():
        expected = extract_union(fixture["optimized_aabbs"])
        if (
            fixture["patch_count"] != len(expected)
            or sorted(map(tuple, fixture["patches"]))
            != sorted(map(tuple, expected))
        ):
            raise ValueError(f"{name} union surface differs")
    if fixtures["slab"]["optimized_aabbs"] != [
        [0.0, 0.0, 0.0, 1.0, 0.5, 1.0]
    ]:
        raise ValueError("native slab subvoxel geometry changed")
    if not any(
        patch[0] == 1 and patch[1] == 1 and patch[2] == 0.5
        for patch in fixtures["slab"]["patches"]
    ):
        raise ValueError("slab y=0.5 reflection plane missing")
    if any(
        patch[0] == 0 and patch[2] == 1.0
        for patch in fixtures["adjacent-full-blocks"]["patches"]
    ):
        raise ValueError("adjacent block internal face was emitted")
    gates = report["gates"]
    if not all(gates.values()):
        raise ValueError("native VoxelShape gate changed")
    if (
        report["captures_audio"] is not False
        or report["physical_endpoint_opened"] is not False
        or report["cuda_executed"] is not False
        or report["minecraft_client_started"] is not False
        or report["minecraft_integration_enabled"] is not False
        or report["release_calibrated"] is not False
    ):
        raise ValueError("native VoxelShape claim boundary changed")
    return {
        "schema_version": 1,
        "status": "verified-minecraft-voxel-shape-local-plane-reference",
        "source_report_sha256": report_sha,
        "source_contract_sha256": contract_sha,
        "metrics": {
            "fixtures": 4,
            "slab_aabbs": len(fixtures["slab"]["optimized_aabbs"]),
            "stair_aabbs": len(fixtures["stair"]["optimized_aabbs"]),
            "adjacent_internal_faces": 0,
        },
        "gates": {
            "native_voxel_shape_executed": True,
            "independent_union_boundary_recomputed": True,
            "subvoxel_slab_preserved": True,
            "adjacent_internal_face_rejected": True,
            "minecraft_client_started": False,
            "physical_endpoint_opened": False,
            "captures_audio": False,
            "cuda_executed": False,
            "release_calibrated": False,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    result = verify(report, sha256(args.report), sha256(args.contract))
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
