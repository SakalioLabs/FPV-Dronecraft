#!/usr/bin/env python3
"""Screen the PTB raw corpus for broadband products without target leakage."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import statistics
import unicodedata
from collections import defaultdict
from pathlib import Path
from typing import Any

from analyze_ptb_material_absorption import (
    FREQUENCIES,
    load_selection_csv,
    parse_number,
    raw_rows_by_id,
    reduce_octaves,
)


BANDS = ("low", "mid", "high")
WIDEBAND_CODE = "2"
HOLDOUT_MODULUS = 5
HOLDOUT_BUCKET = 0
FIT_THRESHOLD = 0.10
TOP_COUNT = 10
LEAKAGE_ROW_ID = 1088


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def normalize_identity(value: str) -> str:
    ascii_value = (
        unicodedata.normalize("NFKD", value)
        .encode("ascii", "ignore")
        .decode("ascii")
        .lower()
    )
    return re.sub(r"[^a-z0-9]+", " ", ascii_value).strip()


def product_identity(row: dict[str, str]) -> tuple[str, str]:
    manufacturer_lines = row.get("manufacturer", "").splitlines()
    manufacturer = normalize_identity(
        manufacturer_lines[0] if manufacturer_lines else ""
    )
    label = next(
        (
            normalize_identity(row.get(field, ""))
            for field in ("trade name", "type", "description")
            if normalize_identity(row.get(field, ""))
        ),
        "",
    )
    if not label:
        raise ValueError("PTB product row has no usable identity")
    return f"{manufacturer}|{label}", label


def split_for_key(product_key: str) -> tuple[str, int, str]:
    key_hash = hashlib.sha256(product_key.encode("utf-8")).hexdigest()
    bucket = int(key_hash[:8], 16) % HOLDOUT_MODULUS
    return (
        "holdout" if bucket == HOLDOUT_BUCKET else "discovery",
        bucket,
        key_hash,
    )


def relative_metrics(
    candidate: list[float], target: list[float]
) -> tuple[float, float, list[float]]:
    relative = [
        abs(observed - expected) / expected
        for observed, expected in zip(candidate, target, strict=True)
    ]
    return max(relative), math.sqrt(statistics.mean(v * v for v in relative)), relative


def collect_products(
    selection_csv: Path,
    target: list[float],
) -> tuple[list[dict[str, Any]], dict[int, dict[str, Any]], dict[str, int]]:
    header, rows = load_selection_csv(selection_csv)
    raw = raw_rows_by_id(header, rows)
    grouped: dict[str, list[tuple[int, list[float], str]]] = defaultdict(list)
    eligible_rows: dict[int, dict[str, Any]] = {}
    for row_id, row in raw.items():
        if row.get("character of absorption", "").strip() != WIDEBAND_CODE:
            continue
        octave = [
            parse_number(row.get(str(frequency), ""))
            for frequency in FREQUENCIES
        ]
        if any(
            value is None
            or not math.isfinite(value)
            or value < 0.0
            or value > 1.0
            for value in octave
        ):
            continue
        product_key, label = product_identity(row)
        values = [float(value) for value in octave]
        grouped[product_key].append((row_id, values, label))
        eligible_rows[row_id] = {
            "product_key": product_key,
            "octave_absorption": values,
        }

    products = []
    for product_key, configurations in grouped.items():
        octave = [
            statistics.median(item[1][index] for item in configurations)
            for index in range(len(FREQUENCIES))
        ]
        runtime = reduce_octaves(octave, [1.0] * len(FREQUENCIES))
        candidate = [runtime[band] for band in BANDS]
        maximum, rmse, relative = relative_metrics(candidate, target)
        split, bucket, key_hash = split_for_key(product_key)
        products.append(
            {
                "product_key_sha256": key_hash,
                "product_label": configurations[0][2],
                "split": split,
                "split_bucket": bucket,
                "row_ids": sorted(item[0] for item in configurations),
                "configuration_count": len(configurations),
                "octave_median_absorption": dict(
                    zip((str(value) for value in FREQUENCIES), octave, strict=True)
                ),
                "runtime_absorption": dict(zip(BANDS, candidate, strict=True)),
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
    counts = {
        "raw_rows": len(raw),
        "eligible_wideband_rows": len(eligible_rows),
        "eligible_product_groups": len(products),
        "discovery_product_groups": sum(
            item["split"] == "discovery" for item in products
        ),
        "holdout_product_groups": sum(
            item["split"] == "holdout" for item in products
        ),
    }
    return products, eligible_rows, counts


def analyze(
    manifest_path: Path,
    selection_csv: Path,
    shoebox_path: Path,
    ptb_path: Path,
    d111_path: Path,
) -> dict[str, Any]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    shoebox = json.loads(shoebox_path.read_text(encoding="utf-8"))
    ptb = json.loads(ptb_path.read_text(encoding="utf-8"))
    d111 = json.loads(d111_path.read_text(encoding="utf-8"))
    if (
        shoebox.get("status") != "valid-diagnostic"
        or ptb.get("status") != "valid-diagnostic"
        or d111.get("status") != "valid-scene-composition-inverse"
    ):
        raise ValueError("D112 source report status changed")
    booth = next(
        (room for room in shoebox.get("rooms", []) if room.get("id") == "air-booth"),
        None,
    )
    if booth is None:
        raise ValueError("D112 AIR booth target is missing")
    target_map = booth.get("effective_eyring_absorption")
    if not isinstance(target_map, dict) or set(target_map) != set(BANDS):
        raise ValueError("D112 AIR booth target shape changed")
    target = [float(target_map[band]) for band in BANDS]

    products, rows, counts = collect_products(selection_csv, target)
    discovery = [item for item in products if item["split"] == "discovery"]
    holdout = [item for item in products if item["split"] == "holdout"]
    if not discovery or not holdout:
        raise ValueError("D112 product split is empty")
    leakage = rows.get(LEAKAGE_ROW_ID)
    if leakage is None:
        raise ValueError("D112 leakage sentinel row is not eligible")
    leakage_runtime = reduce_octaves(
        leakage["octave_absorption"], [1.0] * len(FREQUENCIES)
    )
    leakage_values = [leakage_runtime[band] for band in BANDS]
    row_maximum, row_rmse, row_relative = relative_metrics(
        leakage_values, target
    )
    product_hash = split_for_key(leakage["product_key"])[2]
    leakage_product = next(
        item for item in products
        if item["product_key_sha256"] == product_hash
    )

    discovery_pass = [
        item for item in discovery
        if item["maximum_relative_target_error"] <= FIT_THRESHOLD
    ]
    holdout_pass = [
        item for item in holdout
        if item["maximum_relative_target_error"] <= FIT_THRESHOLD
    ]
    return {
        "schema_version": 1,
        "status": "valid-ptb-broadband-product-screen",
        "source_manifest_sha256": sha256(manifest_path),
        "source_selection_csv_sha256": sha256(selection_csv),
        "source_air_shoebox_sha256": sha256(shoebox_path),
        "source_ptb_material_sha256": sha256(ptb_path),
        "source_d111_report_sha256": sha256(d111_path),
        "source_archive_sha256": manifest["source"]["archive_sha256"],
        "octave_centers_hz": list(FREQUENCIES),
        "target_room": "air-booth",
        "target_effective_absorption": dict(zip(BANDS, target, strict=True)),
        "filter": {
            "character_of_absorption": WIDEBAND_CODE,
            "meaning": "wide-band absorbent",
            "requires_complete_six_octaves": True,
            "coefficient_interval": [0.0, 1.0],
            "clamping": "forbidden",
        },
        "product_grouping": {
            "key": "normalized manufacturer first line + preferred label",
            "preferred_label_order": ["trade name", "type", "description"],
            "estimator": "per-octave median across all product configurations",
            "contact_data_emitted": False,
        },
        "split": {
            "algorithm": "uint32(sha256(product_key)[0:8]) modulo 5",
            "holdout_bucket": HOLDOUT_BUCKET,
            "discovery_buckets": [1, 2, 3, 4],
            "product_level": True,
            "target_independent": True,
        },
        "ranking": {
            "primary": "maximum relative target absorption error",
            "secondary": "relative RMSE",
            "threshold": FIT_THRESHOLD,
            "top_count": TOP_COUNT,
        },
        "counts": counts,
        "top_discovery_products": discovery[:TOP_COUNT],
        "top_holdout_products": holdout[:TOP_COUNT],
        "best_discovery_maximum_relative_error": discovery[0][
            "maximum_relative_target_error"
        ],
        "best_holdout_maximum_relative_error": holdout[0][
            "maximum_relative_target_error"
        ],
        "eligible_discovery_product_count": len(discovery_pass),
        "eligible_holdout_product_count": len(holdout_pass),
        "leakage_diagnostic": {
            "row_id": LEAKAGE_ROW_ID,
            "row_split": leakage_product["split"],
            "product_key_sha256": product_hash,
            "product_row_ids": list(leakage_product["row_ids"]),
            "single_row_runtime_absorption": dict(
                zip(BANDS, leakage_values, strict=True)
            ),
            "single_row_relative_target_error": dict(
                zip(BANDS, row_relative, strict=True)
            ),
            "single_row_maximum_relative_target_error": row_maximum,
            "single_row_relative_rmse": row_rmse,
            "product_median_runtime_absorption": dict(
                leakage_product["runtime_absorption"]
            ),
            "product_median_maximum_relative_target_error": leakage_product[
                "maximum_relative_target_error"
            ],
            "single_row_target_screen_forbidden": True,
            "reason": (
                "The row is in the product-level holdout and selecting one "
                "favourable configuration would leak the AIR target."
            ),
        },
        "decision": {
            "basis_extension_selected": False,
            "reason": (
                "No discovery product reaches the 10% broadband target; the "
                "independent holdout also has no qualifying product."
            ),
            "d111_four_material_basis_still_insufficient_for_booth": True,
            "next_parameter_level": (
                "measured-room furnishing inventory or a richer independently "
                "specified material taxonomy"
            ),
        },
        "production_change_required": False,
        "physical_endpoint_opened": False,
        "captures_audio": False,
        "release_calibrated": False,
        "claim_boundary": (
            "A product-level, target-independent screen of provisional PTB "
            "manufacturer/internet coefficients. It is not identification of "
            "AIR booth furnishings, Minecraft calibration, or perceptual proof."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--selection-csv", type=Path, required=True)
    parser.add_argument("--shoebox-report", type=Path, required=True)
    parser.add_argument("--ptb-report", type=Path, required=True)
    parser.add_argument("--d111-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    report = analyze(
        args.manifest,
        args.selection_csv,
        args.shoebox_report,
        args.ptb_report,
        args.d111_report,
    )
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(
        json.dumps(
            {
                "status": report["status"],
                "products": report["counts"]["eligible_product_groups"],
                "best_discovery_error": report[
                    "best_discovery_maximum_relative_error"
                ],
                "basis_extension_selected": False,
                "output": str(args.output_json),
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
