#!/usr/bin/env python3
"""Independently recompute and verify the D120 bounded-gain reference."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any


MATERIALS = {
    "stone": ((0.03, 0.05, 0.08), 0.18),
    "soft": ((0.25, 0.55, 0.78), 0.65),
    "wood": ((0.12, 0.22, 0.35), 0.45),
    "glass": ((0.05, 0.08, 0.12), 0.08),
    "metal": ((0.02, 0.04, 0.06), 0.12),
    "foliage": ((0.10, 0.35, 0.65), 0.75),
}
MINIMUM_DISTANCE_M = 0.25
MINIMUM_GAIN = 1.0e-6
MAXIMUM_GAIN = 1.0
MINIMUM_CORRECTION_DB = -6.0
MAXIMUM_CORRECTION_DB = 3.0
SAMPLE_RATE = 48_000.0
SPEED_OF_SOUND = 343.0
CLUSTER_SEPARATION_SAMPLES = 16.0


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def image_source_lengths(
    room: list[float],
    source: list[float],
    listener: list[float],
) -> list[float]:
    direct = math.dist(source, listener)
    lengths = [direct]
    # Java facet order: z-min, z-max, x-min, y-min, x-max, y-max.
    for axis, maximum in (
        (2, False),
        (2, True),
        (0, False),
        (1, False),
        (0, True),
        (1, True),
    ):
        plane = room[axis] if maximum else 0.0
        image = list(source)
        image[axis] = 2.0 * plane - source[axis]
        lengths.append(math.dist(image, listener))
    return lengths


def bounded_gain(
    direct: float,
    reflected: float,
    absorption: float,
    scattering: float,
    correction_db: float,
) -> float:
    spreading = (
        max(direct, MINIMUM_DISTANCE_M)
        / max(reflected, MINIMUM_DISTANCE_M)
    ) ** 2
    correction = 10.0 ** (
        min(
            MAXIMUM_CORRECTION_DB,
            max(MINIMUM_CORRECTION_DB, correction_db),
        )
        / 10.0
    )
    raw = spreading * (1.0 - absorption) * (1.0 - scattering)
    raw *= correction
    if raw <= 0.0:
        return 0.0
    return min(MAXIMUM_GAIN, max(MINIMUM_GAIN, raw))


def recompute_gains(
    report: dict[str, Any],
    supported: bool,
) -> dict[str, list[float]]:
    lengths = report["path_length_m"]
    result = {"low": [1.0], "mid": [1.0], "high": [1.0]}
    for facet, material_id in enumerate(report["facet_material_ids"]):
        absorptions, scattering = MATERIALS[material_id]
        for band_index, band in enumerate(("low", "mid", "high")):
            correction = (
                report["correction_db"][band][facet] if supported else 0.0
            )
            result[band].append(
                bounded_gain(
                    lengths[0],
                    lengths[facet + 1],
                    absorptions[band_index],
                    scattering,
                    correction,
                )
            )
    return result


def recompute_clusters(
    lengths: list[float],
    gains: dict[str, list[float]],
) -> list[dict[str, float | int]]:
    paths = sorted(range(1, 7), key=lengths.__getitem__)
    clusters: list[dict[str, float | int]] = []
    last_arrival = -math.inf
    for path in paths:
        arrival = lengths[path] / SPEED_OF_SOUND * SAMPLE_RATE
        weight = sum(gains[band][path] for band in ("low", "mid", "high"))
        if not clusters or arrival - last_arrival > CLUSTER_SEPARATION_SAMPLES:
            clusters.append(
                {
                    "weighted_arrival": 0.0,
                    "weight": 0.0,
                    "path_count": 0,
                    "low": 0.0,
                    "mid": 0.0,
                    "high": 0.0,
                }
            )
        cluster = clusters[-1]
        cluster["weighted_arrival"] += arrival * weight
        cluster["weight"] += weight
        cluster["path_count"] += 1
        for band in ("low", "mid", "high"):
            cluster[band] += gains[band][path]
        last_arrival = arrival
    return [
        {
            "arrival_samples": cluster["weighted_arrival"]
            / cluster["weight"],
            "path_count": cluster["path_count"],
            "low": cluster["low"],
            "mid": cluster["mid"],
            "high": cluster["high"],
        }
        for cluster in clusters
    ]


def assert_close(left: float, right: float, label: str) -> None:
    if not math.isclose(left, right, rel_tol=1.0e-12, abs_tol=1.0e-12):
        raise ValueError(f"{label} differs: {left} != {right}")


def verify(
    report: dict[str, Any],
    report_sha: str,
    contract_sha: str,
) -> dict[str, Any]:
    if (
        report.get("schema_version") != 1
        or report.get("status")
        != "valid-bounded-first-order-gain-reference"
        or report.get("source_contract_sha256") != contract_sha
        or report.get("support_decision") is not True
        or report.get("topology_visible") != [True] * 7
    ):
        raise ValueError("D120 identity, support, or topology changed")
    expected_lengths = image_source_lengths(
        report["room_m"], report["source_m"], report["listener_m"]
    )
    for index, expected in enumerate(expected_lengths):
        assert_close(
            report["path_length_m"][index],
            expected,
            f"path length {index}",
        )
    supported = recompute_gains(report, True)
    unsupported = recompute_gains(report, False)
    for band in ("low", "mid", "high"):
        for path in range(7):
            assert_close(
                report["supported_gain"][band][path],
                supported[band][path],
                f"supported {band} path {path}",
            )
            assert_close(
                report["unsupported_gain"][band][path],
                unsupported[band][path],
                f"unsupported {band} path {path}",
            )
    clusters = recompute_clusters(report["path_length_m"], unsupported)
    if len(clusters) != len(report["unsupported_clusters"]):
        raise ValueError("D120 cluster count differs")
    for index, expected in enumerate(clusters):
        actual = report["unsupported_clusters"][index]
        if actual["path_count"] != expected["path_count"]:
            raise ValueError(f"D120 cluster {index} membership differs")
        for key in ("arrival_samples", "low", "mid", "high"):
            assert_close(actual[key], expected[key], f"cluster {index} {key}")
    benchmark = report["benchmark"]
    gates = report["gates"]
    if (
        benchmark["solves_per_allocation_window"] < 100_000
        or len(benchmark["allocation_windows_bytes"]) < 5
        or any(benchmark["allocation_windows_bytes"])
        or benchmark["median_allocated_bytes_per_solve"] != 0.0
        or benchmark["p99_ns_per_solve"] > 20_000.0
        or gates["support_decision_true"] is not True
        or gates["zero_allocation_hot_path"] is not True
        or gates["p99_below_20_microseconds"] is not True
    ):
        raise ValueError("D120 allocation or latency gate changed")
    if (
        report["captures_audio"] is not False
        or report["physical_endpoint_opened"] is not False
        or report["cuda_executed"] is not False
        or report["minecraft_integration_enabled"] is not False
        or report["release_calibrated"] is not False
    ):
        raise ValueError("D120 claim boundary changed")
    return {
        "schema_version": 1,
        "status": "verified-bounded-first-order-gain-reference",
        "source_report_sha256": report_sha,
        "source_contract_sha256": contract_sha,
        "metrics": {
            "paths": 7,
            "clusters": len(clusters),
            "allocation_windows_bytes": benchmark[
                "allocation_windows_bytes"
            ],
            "p99_ns_per_solve": benchmark["p99_ns_per_solve"],
        },
        "gates": {
            "independent_image_source_parity": True,
            "independent_gain_parity": True,
            "independent_cluster_parity": True,
            "bounded_output": True,
            "unsupported_fallback_recomputed": True,
            "zero_allocation_hot_path": True,
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
    result = verify(
        report,
        sha256(args.report),
        sha256(args.contract),
    )
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
