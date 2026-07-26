#!/usr/bin/env python3
"""Independently verify the D112 PTB product-level broadband screen."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import re
import statistics
import unicodedata
from collections import defaultdict
from pathlib import Path
from typing import Any


FREQUENCIES = (125, 250, 500, 1000, 2000, 4000)
BANDS = ("low", "mid", "high")
BAND_INDEXES = ((0, 3), (3, 5), (5, 6))
THRESHOLD = 0.10
TOP_COUNT = 10
EXPECTED_COUNTS = {
    "eligible_wideband_rows": 1384,
    "eligible_product_groups": 868,
    "discovery_product_groups": 683,
    "holdout_product_groups": 185,
}
TOLERANCE = 1.0e-12


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def normalize(value: str) -> str:
    value = (
        unicodedata.normalize("NFKD", value)
        .encode("ascii", "ignore")
        .decode("ascii")
        .lower()
    )
    return re.sub(r"[^a-z0-9]+", " ", value).strip()


def load_rows(path: Path) -> dict[int, dict[str, str]]:
    rows = None
    for encoding in ("utf-8-sig", "cp1252"):
        try:
            with path.open(encoding=encoding, newline="") as stream:
                rows = list(csv.reader(stream))
            break
        except UnicodeDecodeError:
            continue
    if rows is None:
        raise ValueError("D112 selection CSV cannot be decoded")
    header_index = next(
        (
            index for index, row in enumerate(rows)
            if len(row) >= 2 and row[0] == "No." and row[1] == "description"
        ),
        None,
    )
    if header_index is None:
        raise ValueError("D112 selection CSV header is missing")
    header = rows[header_index]
    result = {}
    for row in rows[header_index + 1:]:
        if not row or not row[0].strip().isdigit():
            continue
        padded = row + [""] * (len(header) - len(row))
        result[int(row[0])] = dict(zip(header, padded, strict=False))
    return result


def number(value: str) -> float | None:
    try:
        return float(value.strip().replace(",", "."))
    except ValueError:
        return None


def key_and_label(row: dict[str, str]) -> tuple[str, str]:
    maker_lines = row.get("manufacturer", "").splitlines()
    maker = normalize(maker_lines[0] if maker_lines else "")
    labels = [
        normalize(row.get(field, ""))
        for field in ("trade name", "type", "description")
    ]
    label = next((value for value in labels if value), "")
    if not label:
        raise ValueError("D112 empty product identity")
    return f"{maker}|{label}", label


def reduce(values: list[float]) -> list[float]:
    return [
        statistics.mean(values[start:end])
        for start, end in BAND_INDEXES
    ]


def metrics(values: list[float], target: list[float]) -> tuple[float, float, list[float]]:
    relative = [
        abs(value - expected) / expected
        for value, expected in zip(values, target, strict=True)
    ]
    return max(relative), math.sqrt(statistics.mean(v * v for v in relative)), relative


def recompute(selection_csv: Path, target: list[float]) -> dict[str, Any]:
    raw = load_rows(selection_csv)
    groups: dict[str, list[tuple[int, list[float], str]]] = defaultdict(list)
    eligible: dict[int, tuple[str, list[float]]] = {}
    for row_id, row in raw.items():
        if row.get("character of absorption", "").strip() != "2":
            continue
        octave = [number(row.get(str(value), "")) for value in FREQUENCIES]
        if any(
            value is None or not math.isfinite(value)
            or value < 0.0 or value > 1.0
            for value in octave
        ):
            continue
        key, label = key_and_label(row)
        values = [float(value) for value in octave]
        groups[key].append((row_id, values, label))
        eligible[row_id] = (key, values)
    products = []
    for key, configurations in groups.items():
        octave = [
            statistics.median(item[1][index] for item in configurations)
            for index in range(6)
        ]
        runtime = reduce(octave)
        maximum, rmse, relative = metrics(runtime, target)
        key_hash = hashlib.sha256(key.encode("utf-8")).hexdigest()
        bucket = int(key_hash[:8], 16) % 5
        products.append(
            {
                "product_key_sha256": key_hash,
                "product_label": configurations[0][2],
                "split": "holdout" if bucket == 0 else "discovery",
                "split_bucket": bucket,
                "row_ids": sorted(item[0] for item in configurations),
                "configuration_count": len(configurations),
                "octave_median_absorption": dict(
                    zip((str(value) for value in FREQUENCIES), octave, strict=True)
                ),
                "runtime_absorption": dict(zip(BANDS, runtime, strict=True)),
                "relative_target_error": dict(zip(BANDS, relative, strict=True)),
                "maximum_relative_target_error": maximum,
                "relative_rmse": rmse,
            }
        )
    products.sort(
        key=lambda item: (
            item["maximum_relative_target_error"],
            item["relative_rmse"],
            item["product_key_sha256"],
        )
    )
    discovery = [item for item in products if item["split"] == "discovery"]
    holdout = [item for item in products if item["split"] == "holdout"]
    sentinel_key, sentinel_octave = eligible[1088]
    sentinel_runtime = reduce(sentinel_octave)
    sentinel_max, sentinel_rmse, sentinel_relative = metrics(
        sentinel_runtime, target
    )
    sentinel_hash = hashlib.sha256(sentinel_key.encode("utf-8")).hexdigest()
    sentinel_product = next(
        item for item in products
        if item["product_key_sha256"] == sentinel_hash
    )
    return {
        "counts": {
            "raw_rows": len(raw),
            "eligible_wideband_rows": len(eligible),
            "eligible_product_groups": len(products),
            "discovery_product_groups": len(discovery),
            "holdout_product_groups": len(holdout),
        },
        "top_discovery_products": discovery[:TOP_COUNT],
        "top_holdout_products": holdout[:TOP_COUNT],
        "best_discovery_maximum_relative_error": discovery[0][
            "maximum_relative_target_error"
        ],
        "best_holdout_maximum_relative_error": holdout[0][
            "maximum_relative_target_error"
        ],
        "eligible_discovery_product_count": sum(
            item["maximum_relative_target_error"] <= THRESHOLD
            for item in discovery
        ),
        "eligible_holdout_product_count": sum(
            item["maximum_relative_target_error"] <= THRESHOLD
            for item in holdout
        ),
        "leakage_diagnostic": {
            "row_id": 1088,
            "row_split": sentinel_product["split"],
            "product_key_sha256": sentinel_hash,
            "product_row_ids": sentinel_product["row_ids"],
            "single_row_runtime_absorption": dict(
                zip(BANDS, sentinel_runtime, strict=True)
            ),
            "single_row_relative_target_error": dict(
                zip(BANDS, sentinel_relative, strict=True)
            ),
            "single_row_maximum_relative_target_error": sentinel_max,
            "single_row_relative_rmse": sentinel_rmse,
            "product_median_runtime_absorption": sentinel_product[
                "runtime_absorption"
            ],
            "product_median_maximum_relative_target_error": sentinel_product[
                "maximum_relative_target_error"
            ],
            "single_row_target_screen_forbidden": True,
            "reason": (
                "The row is in the product-level holdout and selecting one "
                "favourable configuration would leak the AIR target."
            ),
        },
    }


def close_json(actual: Any, expected: Any, name: str) -> None:
    if isinstance(expected, float):
        if (
            not isinstance(actual, (int, float))
            or not math.isfinite(actual)
            or not math.isclose(actual, expected, rel_tol=0.0, abs_tol=TOLERANCE)
        ):
            raise ValueError(f"D112 {name} changed")
    elif isinstance(expected, dict):
        if not isinstance(actual, dict) or set(actual) != set(expected):
            raise ValueError(f"D112 {name} shape changed")
        for key, value in expected.items():
            close_json(actual[key], value, f"{name}.{key}")
    elif isinstance(expected, list):
        if not isinstance(actual, list) or len(actual) != len(expected):
            raise ValueError(f"D112 {name} shape changed")
        for index, value in enumerate(expected):
            close_json(actual[index], value, f"{name}[{index}]")
    elif actual != expected:
        raise ValueError(f"D112 {name} changed")


def verify(
    report: dict[str, Any],
    report_sha: str,
    manifest_path: Path,
    selection_csv: Path,
    shoebox_path: Path,
    ptb_path: Path,
    d111_path: Path,
    expected_counts: dict[str, int] | None = None,
) -> dict[str, Any]:
    shoebox = json.loads(shoebox_path.read_text(encoding="utf-8"))
    ptb = json.loads(ptb_path.read_text(encoding="utf-8"))
    d111 = json.loads(d111_path.read_text(encoding="utf-8"))
    bindings = {
        "source_manifest_sha256": sha256(manifest_path),
        "source_selection_csv_sha256": sha256(selection_csv),
        "source_air_shoebox_sha256": sha256(shoebox_path),
        "source_ptb_material_sha256": sha256(ptb_path),
        "source_d111_report_sha256": sha256(d111_path),
    }
    if (
        report.get("schema_version") != 1
        or report.get("status") != "valid-ptb-broadband-product-screen"
        or any(report.get(key) != value for key, value in bindings.items())
    ):
        raise ValueError("D112 report shape or source binding changed")
    if (
        shoebox.get("status") != "valid-diagnostic"
        or ptb.get("status") != "valid-diagnostic"
        or d111.get("status") != "valid-scene-composition-inverse"
        or d111.get("rooms", [{}])[0].get("id") != "air-booth"
        or d111.get("rooms", [{}])[0].get(
            "inverse_maximum_relative_error", 0.0
        ) <= THRESHOLD
    ):
        raise ValueError("D112 source policy changed")
    booth = next(
        room for room in shoebox["rooms"] if room.get("id") == "air-booth"
    )
    target_map = booth["effective_eyring_absorption"]
    target = [float(target_map[band]) for band in BANDS]
    close_json(
        report.get("target_effective_absorption"),
        dict(zip(BANDS, target, strict=True)),
        "target",
    )
    expected = recompute(selection_csv, target)
    for key, value in expected.items():
        close_json(report.get(key), value, key)
    pinned_counts = EXPECTED_COUNTS if expected_counts is None else expected_counts
    for key, value in pinned_counts.items():
        if expected["counts"].get(key) != value:
            raise ValueError(f"D112 pinned corpus {key} changed")
    if expected["best_discovery_maximum_relative_error"] <= THRESHOLD:
        raise ValueError("D112 discovery gate unexpectedly passed")
    if expected["best_holdout_maximum_relative_error"] <= THRESHOLD:
        raise ValueError("D112 holdout gate unexpectedly passed")
    if report.get("filter") != {
            "character_of_absorption": "2",
            "meaning": "wide-band absorbent",
            "requires_complete_six_octaves": True,
            "coefficient_interval": [0.0, 1.0],
            "clamping": "forbidden",
        }:
        raise ValueError("D112 screening contract changed")
    if report.get("product_grouping") != {
        "key": "normalized manufacturer first line + preferred label",
        "preferred_label_order": ["trade name", "type", "description"],
        "estimator": "per-octave median across all product configurations",
        "contact_data_emitted": False,
    }:
        raise ValueError("D112 screening contract changed")
    if report.get("split") != {
        "algorithm": "uint32(sha256(product_key)[0:8]) modulo 5",
        "holdout_bucket": 0,
        "discovery_buckets": [1, 2, 3, 4],
        "product_level": True,
        "target_independent": True,
    }:
        raise ValueError("D112 screening contract changed")
    if report.get("ranking") != {
        "primary": "maximum relative target absorption error",
        "secondary": "relative RMSE",
        "threshold": THRESHOLD,
        "top_count": TOP_COUNT,
    }:
        raise ValueError("D112 screening contract changed")
    decision = report.get("decision", {})
    if (
        decision.get("basis_extension_selected") is not False
        or decision.get(
            "d111_four_material_basis_still_insufficient_for_booth"
        ) is not True
        or report.get("production_change_required") is not False
        or report.get("physical_endpoint_opened") is not False
        or report.get("captures_audio") is not False
        or report.get("release_calibrated") is not False
    ):
        raise ValueError("D112 decision or claim boundary changed")
    return {
        "schema_version": 1,
        "status": "verified-ptb-broadband-product-screen",
        "source_report_sha256": report_sha,
        "gates": {
            "raw_product_screen_recomputed": True,
            "product_level_split_target_independent": True,
            "discovery_has_no_10_percent_candidate": True,
            "holdout_has_no_10_percent_candidate": True,
            "single_row_1088_rejected_as_leakage": True,
            "basis_extension_rejected": True,
        },
        "metrics": {
            "eligible_product_groups": expected["counts"][
                "eligible_product_groups"
            ],
            "discovery_product_groups": expected["counts"][
                "discovery_product_groups"
            ],
            "holdout_product_groups": expected["counts"][
                "holdout_product_groups"
            ],
            "best_discovery_maximum_relative_error": expected[
                "best_discovery_maximum_relative_error"
            ],
            "best_holdout_maximum_relative_error": expected[
                "best_holdout_maximum_relative_error"
            ],
            "single_row_1088_maximum_relative_error": expected[
                "leakage_diagnostic"
            ]["single_row_maximum_relative_target_error"],
            "row_1088_product_median_maximum_relative_error": expected[
                "leakage_diagnostic"
            ]["product_median_maximum_relative_target_error"],
        },
        "production_change_required": False,
        "physical_endpoint_opened": False,
        "captures_audio": False,
        "release_calibrated": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--selection-csv", type=Path, required=True)
    parser.add_argument("--shoebox-report", type=Path, required=True)
    parser.add_argument("--ptb-report", type=Path, required=True)
    parser.add_argument("--d111-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    result = verify(
        report,
        sha256(args.report),
        args.manifest,
        args.selection_csv,
        args.shoebox_report,
        args.ptb_report,
        args.d111_report,
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
