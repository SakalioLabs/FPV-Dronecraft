#!/usr/bin/env python3
"""Build provisional Minecraft material candidates from a pinned PTB subset."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import statistics
from collections import defaultdict
from pathlib import Path


FREQUENCIES = (125, 250, 500, 1000, 2000, 4000)
BAND_SLICES = {"low": (0, 3), "mid": (3, 5), "high": (5, 6)}
CURRENT_RUNTIME = {
    "stone_dense": (0.03, 0.05, 0.08),
    "wood_solid_panel": (0.12, 0.22, 0.35),
    "glass_window": (0.05, 0.08, 0.12),
    "porous_wool": (0.25, 0.55, 0.78),
}
SOUND_SPEED_M_PER_S = 343.0
ROOM_DIMENSIONS_M = (11.0, 11.0, 3.0)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def parse_number(value: str) -> float | None:
    text = value.strip().replace(",", ".")
    try:
        return float(text)
    except ValueError:
        return None


def load_selection_csv(path: Path) -> tuple[list[str], list[list[str]]]:
    last_error: UnicodeDecodeError | None = None
    for encoding in ("utf-8-sig", "cp1252"):
        try:
            with path.open(encoding=encoding, newline="") as stream:
                rows = list(csv.reader(stream))
            break
        except UnicodeDecodeError as error:
            last_error = error
    else:
        assert last_error is not None
        raise last_error
    header_index = next(
        (
            index
            for index, row in enumerate(rows)
            if len(row) >= 2 and row[0] == "No."
            and row[1] == "description"
        ),
        None,
    )
    if header_index is None:
        raise ValueError("selection_table header was not found")
    return rows[header_index], rows[header_index + 1 :]


def raw_rows_by_id(
    header: list[str], rows: list[list[str]]
) -> dict[int, dict[str, str]]:
    result = {}
    for row in rows:
        if not row or not row[0].strip().isdigit():
            continue
        padded = row + [""] * (len(header) - len(row))
        result[int(row[0])] = dict(zip(header, padded, strict=False))
    return result


def audit_raw_corpus(
    header: list[str], rows: list[list[str]]
) -> dict[str, int]:
    raw = raw_rows_by_id(header, rows)
    complete = 0
    above_one = 0
    for row in raw.values():
        values = [parse_number(row.get(str(frequency), "")) for frequency in FREQUENCIES]
        if all(value is not None for value in values):
            complete += 1
        if any(value is not None and value > 1.0 for value in values):
            above_one += 1
    return {
        "rows": len(raw),
        "complete_six_octave_rows": complete,
        "rows_with_coefficient_above_one": above_one,
        "rows_with_primary_reference": sum(
            bool(row.get("primary reference", "").strip())
            for row in raw.values()
        ),
        "rows_marked_diffuse_field": sum(
            bool(row.get("diffuse field measurement", "").strip())
            for row in raw.values()
        ),
    }


def validate_raw_rows(
    manifest_rows: list[dict[str, object]],
    raw: dict[int, dict[str, str]],
) -> None:
    for expected in manifest_rows:
        row_id = int(expected["row_id"])
        if row_id not in raw:
            raise ValueError(f"PTB row {row_id} is missing")
        actual = raw[row_id]
        if actual["description"].strip() != expected["description"]:
            raise ValueError(f"PTB row {row_id} description changed")
        if actual.get("material criteria", "").strip() != expected["material_code"]:
            raise ValueError(f"PTB row {row_id} material code changed")
        actual_absorption = [
            parse_number(actual.get(str(frequency), ""))
            for frequency in FREQUENCIES
        ]
        if any(value is None for value in actual_absorption):
            raise ValueError(f"PTB row {row_id} has a missing octave value")
        for index, (observed, pinned) in enumerate(
            zip(actual_absorption, expected["absorption"], strict=True)
        ):
            assert observed is not None
            if not math.isclose(observed, float(pinned), abs_tol=1.0e-12):
                raise ValueError(
                    f"PTB row {row_id} octave {FREQUENCIES[index]} changed"
                )


def reduce_octaves(
    absorption: list[float] | tuple[float, ...],
    energy_weights: list[float] | tuple[float, ...],
) -> dict[str, float]:
    if len(absorption) != 6 or len(energy_weights) != 6:
        raise ValueError("absorption and weights must contain six bands")
    if any(not math.isfinite(value) or value < 0.0 or value > 1.0 for value in absorption):
        raise ValueError("absorption must be finite and in [0, 1]")
    if any(not math.isfinite(value) or value < 0.0 for value in energy_weights):
        raise ValueError("energy weights must be finite and non-negative")
    result = {}
    for name, (start, end) in BAND_SLICES.items():
        denominator = sum(energy_weights[start:end])
        if denominator <= 0.0:
            raise ValueError(f"{name} band has no source energy")
        result[name] = sum(
            absorption[index] * energy_weights[index]
            for index in range(start, end)
        ) / denominator
    return result


def eyring_rt60(absorption: float) -> float:
    length, width, height = ROOM_DIMENSIONS_M
    volume = length * width * height
    surface = 2.0 * (
        length * width + length * height + width * height
    )
    mean_free_path = 4.0 * volume / surface
    return (
        -6.0
        * math.log(10.0)
        * mean_free_path
        / (SOUND_SPEED_M_PER_S * math.log1p(-absorption))
    )


def analyze(
    manifest: dict[str, object],
    energy_weights: list[float],
    raw_csv: Path | None = None,
) -> dict[str, object]:
    rows = list(manifest["rows"])
    raw_audit = None
    raw_verified = False
    if raw_csv is not None:
        header, raw_rows = load_selection_csv(raw_csv)
        by_id = raw_rows_by_id(header, raw_rows)
        validate_raw_rows(rows, by_id)
        raw_audit = audit_raw_corpus(header, raw_rows)
        raw_verified = True

    grouped: dict[str, dict[str, list[dict[str, object]]]] = defaultdict(
        lambda: defaultdict(list)
    )
    for row in rows:
        grouped[str(row["category"])][str(row["split"])].append(row)

    categories = {}
    for category in sorted(grouped):
        train = grouped[category]["train"]
        holdout = grouped[category]["holdout"]
        octave_candidate = [
            statistics.median(
                float(row["absorption"][index]) for row in train
            )
            for index in range(6)
        ]
        runtime_candidate = reduce_octaves(
            octave_candidate, energy_weights
        )
        holdout_errors = []
        for row in holdout:
            squared = [
                (
                    float(row["absorption"][index])
                    - octave_candidate[index]
                )
                ** 2
                for index in range(6)
            ]
            holdout_errors.append(
                {
                    "row_id": row["row_id"],
                    "rmse": math.sqrt(statistics.mean(squared)),
                }
            )
        current = dict(zip(("low", "mid", "high"), CURRENT_RUNTIME[category]))
        categories[category] = {
            "train_row_ids": [row["row_id"] for row in train],
            "holdout_row_ids": [row["row_id"] for row in holdout],
            "octave_median_absorption": dict(
                zip((str(value) for value in FREQUENCIES), octave_candidate)
            ),
            "runtime_candidate_absorption": runtime_candidate,
            "current_runtime_absorption": current,
            "candidate_minus_current": {
                band: runtime_candidate[band] - current[band]
                for band in BAND_SLICES
            },
            "holdout": {
                "per_row": holdout_errors,
                "mean_rmse": statistics.mean(
                    item["rmse"] for item in holdout_errors
                ),
                "maximum_rmse": max(
                    item["rmse"] for item in holdout_errors
                ),
            },
            "uniform_11x11x3_room_rt60_seconds": {
                "candidate": {
                    band: eyring_rt60(runtime_candidate[band])
                    for band in BAND_SLICES
                },
                "current": {
                    band: eyring_rt60(current[band])
                    for band in BAND_SLICES
                },
            },
        }

    return {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "manifest_sha256": None,
        "source_archive_sha256": manifest["source"]["archive_sha256"],
        "raw_selection_csv_verified": raw_verified,
        "raw_corpus_audit": raw_audit,
        "octave_centers_hz": list(FREQUENCIES),
        "runtime_bands_hz": manifest["runtime_bands_hz"],
        "source_energy_weights": {
            "label": "flat-energy-per-octave-diagnostic",
            "values": energy_weights,
        },
        "release_calibrated": False,
        "claim_boundary": manifest["selection_policy"]["claim_boundary"],
        "categories": categories,
        "gates": {
            "all_selected_coefficients_in_unit_interval": True,
            "explicit_energy_weights": True,
            "raw_rows_match_manifest": raw_verified,
            "minecraft_release_calibrated": False,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--selection-csv", type=Path)
    parser.add_argument("--output-json", type=Path, required=True)
    parser.add_argument(
        "--energy-weights",
        default="1,1,1,1,1,1",
        help="Six explicit source-energy weights at 125..4000 Hz.",
    )
    args = parser.parse_args()
    weights = [float(value) for value in args.energy_weights.split(",")]
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    report = analyze(manifest, weights, args.selection_csv)
    report["manifest_sha256"] = sha256(args.manifest)
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(
        json.dumps(
            {
                "status": report["status"],
                "categories": len(report["categories"]),
                "raw_verified": report["raw_selection_csv_verified"],
                "release_calibrated": False,
                "output": str(args.output_json),
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
