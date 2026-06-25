#!/usr/bin/env python3
"""
Download the open Mendeley LiPo ECM dataset to a temp file and summarize R0.

Output:
  docs/data/lipo_ecm_mendeley_r0_summary.csv
  docs/data/lipo_ecm_mendeley_soc_soh_lookup.csv
"""

from __future__ import annotations

import csv
import re
import statistics
import tempfile
import urllib.request
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs/data"
MENDELEY_ZIP_URL = "https://data.mendeley.com/public-api/zip/stcppt2r68/download/1"
ZIP_PATH = Path(tempfile.gettempdir()) / "mendeley_lipo_stcppt2r68_v1.zip"
OUTPUT = DATA / "lipo_ecm_mendeley_r0_summary.csv"
LOOKUP_OUTPUT = DATA / "lipo_ecm_mendeley_soc_soh_lookup.csv"
SOC_BINS = [0.10, 0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 1.00]
SOH_BANDS = [
    ("fresh_ge_0.95", 0.95, 1.01),
    ("used_0.85_0.95", 0.85, 0.95),
    ("aged_0.75_0.85", 0.75, 0.85),
    ("worn_lt_0.75", 0.00, 0.75),
]


def ensure_zip() -> Path:
    if ZIP_PATH.exists() and ZIP_PATH.stat().st_size > 100_000_000:
        return ZIP_PATH
    req = urllib.request.Request(MENDELEY_ZIP_URL, headers={"User-Agent": "fpv-dronecraft-validation/1.0"})
    with urllib.request.urlopen(req, timeout=60) as response, ZIP_PATH.open("wb") as handle:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            handle.write(chunk)
    return ZIP_PATH


def float_or_none(value: str) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def read_csv_rows(archive: zipfile.ZipFile, name: str) -> list[dict[str, str]]:
    text = archive.open(name).read().decode("utf-8-sig", "replace").splitlines()
    return list(csv.DictReader(text))


def discharge_capacity_ah(archive: zipfile.ZipFile, pack: str, cycle: int) -> float | None:
    name = f"LiPo Battery LP-503562-IS-3 EIS, Capacity, ECM Data/{pack}/Discharge_curve/{cycle}_Discharge_std.csv"
    if name not in archive.namelist():
        return None
    capacity_values: list[float] = []
    for line in archive.open(name).read().decode("utf-8-sig", "replace").splitlines():
        parts = re.split(r"[\t,; ]+", line.strip())
        if not parts:
            continue
        value = float_or_none(parts[0])
        if value is not None:
            capacity_values.append(value)
    return max(capacity_values) if capacity_values else None


def soc_bin_for(soc: float) -> float:
    return min(SOC_BINS, key=lambda value: abs(value - soc))


def soh_band_for(soh: float | None) -> str:
    if soh is None:
        return "unknown"
    for name, low, high in SOH_BANDS:
        if low <= soh < high:
            return name
    return "unknown"


def median(values: list[float]) -> float:
    return statistics.median(values) if values else float("nan")


def summarize() -> tuple[list[dict[str, float | int | str]], list[dict[str, float | int | str]]]:
    rows: list[dict[str, float | int | str]] = []
    lookup_rows: list[dict[str, float | int | str]] = []
    with zipfile.ZipFile(ensure_zip()) as archive:
        fit_names = [
            name
            for name in archive.namelist()
            if re.search(r"LiPO_\d+_fit/model_fit_cycle_\d+\.csv$", name)
        ]

        def sort_key(name: str) -> tuple[str, int]:
            match = re.search(r"(LiPO_\d+)_fit/model_fit_cycle_(\d+)\.csv$", name)
            return (match.group(1), int(match.group(2))) if match else ("", -1)

        capacity_by_pack_cycle: dict[tuple[str, int], float] = {}
        initial_capacity_by_pack: dict[str, float] = {}
        for name in fit_names:
            match = re.search(r"(LiPO_\d+)_fit/model_fit_cycle_(\d+)\.csv$", name)
            if not match:
                continue
            pack = match.group(1)
            cycle = int(match.group(2))
            capacity = discharge_capacity_ah(archive, pack, cycle)
            if capacity is None:
                continue
            capacity_by_pack_cycle[(pack, cycle)] = capacity
            if cycle == 0:
                initial_capacity_by_pack[pack] = capacity

        for name in sorted(fit_names, key=sort_key):
            match = re.search(r"(LiPO_\d+)_fit/model_fit_cycle_(\d+)\.csv$", name)
            if not match:
                continue
            pack = match.group(1)
            cycle = int(match.group(2))
            capacity_ah = capacity_by_pack_cycle.get((pack, cycle))
            initial_capacity_ah = initial_capacity_by_pack.get(pack)
            capacity_soh = capacity_ah / initial_capacity_ah if capacity_ah is not None and initial_capacity_ah else None
            relative_cycle = cycle / max(
                int(re.search(r"model_fit_cycle_(\d+)\.csv$", other).group(1))
                for other in fit_names
                if f"{pack}_fit/" in other and re.search(r"model_fit_cycle_(\d+)\.csv$", other)
            )
            reader = read_csv_rows(archive, name)
            samples: list[tuple[float, float]] = []
            for row in reader:
                soc = float_or_none(row.get("SOC", ""))
                r0 = float_or_none(row.get("RO", row.get("R_0", "")))
                if soc is None or r0 is None:
                    continue
                samples.append((soc, r0))
                lookup_rows.append(
                    {
                        "row_type": "fit_sample",
                        "pack": pack,
                        "cycle": cycle,
                        "relative_cycle": relative_cycle,
                        "capacity_ah": capacity_ah if capacity_ah is not None else "",
                        "capacity_soh": capacity_soh if capacity_soh is not None else "",
                        "soh_band": soh_band_for(capacity_soh),
                        "soc": soc,
                        "soc_bin": soc_bin_for(soc),
                        "r0_ohm": r0,
                        "r0_mohm": r0 * 1000.0,
                        "source_file": name,
                    }
                )
            if not samples:
                continue
            high_soc, high_r0 = max(samples, key=lambda item: item[0])
            low_soc, low_r0 = min(samples, key=lambda item: item[0])
            r0_values = [item[1] for item in samples]
            soc_values = [item[0] for item in samples]
            rows.append(
                {
                    "pack": pack,
                    "cycle": cycle,
                    "sample_count": len(samples),
                    "capacity_ah": capacity_ah if capacity_ah is not None else "",
                    "initial_capacity_ah": initial_capacity_ah if initial_capacity_ah is not None else "",
                    "capacity_soh": capacity_soh if capacity_soh is not None else "",
                    "relative_cycle": relative_cycle,
                    "soc_min": min(soc_values),
                    "soc_max": max(soc_values),
                    "r0_min_ohm": min(r0_values),
                    "r0_max_ohm": max(r0_values),
                    "r0_mean_ohm": statistics.fmean(r0_values),
                    "r0_high_soc_ohm": high_r0,
                    "r0_low_soc_ohm": low_r0,
                    "r0_low_soc_over_high_soc": low_r0 / high_r0 if high_r0 > 0.0 else float("nan"),
                    "source_file": name,
                }
            )

        baseline_candidates = [
            row
            for row in lookup_rows
            if row["row_type"] == "fit_sample"
            and row["cycle"] == 0
            and float(row["soc"]) >= 0.90
        ]
        baseline_r0 = median([float(row["r0_ohm"]) for row in baseline_candidates])
        for row in lookup_rows:
            if row["row_type"] != "fit_sample":
                continue
            row["r0_scale_vs_fresh_high_soc_median"] = float(row["r0_ohm"]) / baseline_r0 if baseline_r0 > 0.0 else float("nan")

        grouped: dict[tuple[str, float], list[dict[str, float | int | str]]] = {}
        for row in lookup_rows:
            if row["row_type"] != "fit_sample":
                continue
            grouped.setdefault((str(row["soh_band"]), float(row["soc_bin"])), []).append(row)
        for (soh_band, soc_bin), group in sorted(grouped.items()):
            r0_values = [float(row["r0_ohm"]) for row in group]
            scale_values = [float(row["r0_scale_vs_fresh_high_soc_median"]) for row in group]
            soh_values = [float(row["capacity_soh"]) for row in group if row["capacity_soh"] != ""]
            lookup_rows.append(
                {
                    "row_type": "soc_soh_bin_lookup",
                    "pack": "all",
                    "cycle": "",
                    "relative_cycle": "",
                    "capacity_ah": "",
                    "capacity_soh": median(soh_values) if soh_values else "",
                    "soh_band": soh_band,
                    "soc": soc_bin,
                    "soc_bin": soc_bin,
                    "sample_count": len(group),
                    "pack_count": len({row["pack"] for row in group}),
                    "r0_ohm_median": median(r0_values),
                    "r0_ohm_min": min(r0_values),
                    "r0_ohm_max": max(r0_values),
                    "r0_scale_vs_fresh_high_soc_median": median(scale_values),
                    "r0_scale_min": min(scale_values),
                    "r0_scale_max": max(scale_values),
                    "source_file": "Mendeley fitted ECM model_fit_cycle CSVs",
                }
            )

        for band_name, _, _ in SOH_BANDS:
            band_rows = [
                row
                for row in lookup_rows
                if row["row_type"] == "soc_soh_bin_lookup"
                and row["soh_band"] == band_name
            ]
            if not band_rows:
                continue
            for row in band_rows:
                runtime = dict(row)
                runtime["row_type"] = "runtime_scale_lookup"
                runtime["name"] = f"{band_name}_soc_{float(row['soc_bin']):.2f}"
                runtime["note"] = "Dimensionless R0 scale; multiply by a separately calibrated FPV absolute ESR."
                lookup_rows.append(runtime)

    return rows, lookup_rows


def main() -> None:
    rows, lookup_rows = summarize()
    DATA.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "pack",
                "cycle",
                "sample_count",
                "capacity_ah",
                "initial_capacity_ah",
                "capacity_soh",
                "relative_cycle",
                "soc_min",
                "soc_max",
                "r0_min_ohm",
                "r0_max_ohm",
                "r0_mean_ohm",
                "r0_high_soc_ohm",
                "r0_low_soc_ohm",
                "r0_low_soc_over_high_soc",
                "source_file",
            ],
        )
        writer.writeheader()
        writer.writerows(rows)
    lookup_fieldnames: list[str] = []
    for row in lookup_rows:
        for key in row:
            if key not in lookup_fieldnames:
                lookup_fieldnames.append(key)
    with LOOKUP_OUTPUT.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=lookup_fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(lookup_rows)
    print(f"Wrote {OUTPUT}")
    print(f"Wrote {LOOKUP_OUTPUT}")
    print(f"Rows: {len(rows)}")
    print(f"Lookup rows: {len(lookup_rows)}")


if __name__ == "__main__":
    main()
