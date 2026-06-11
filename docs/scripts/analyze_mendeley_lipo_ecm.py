#!/usr/bin/env python3
"""
Download the open Mendeley LiPo ECM dataset to a temp file and summarize R0.

Output:
  docs/data/lipo_ecm_mendeley_r0_summary.csv
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


def summarize() -> list[dict[str, float | int | str]]:
    rows: list[dict[str, float | int | str]] = []
    with zipfile.ZipFile(ensure_zip()) as archive:
        fit_names = [
            name
            for name in archive.namelist()
            if re.search(r"LiPO_\d+_fit/model_fit_cycle_\d+\.csv$", name)
        ]
        def sort_key(name: str) -> tuple[str, int]:
            match = re.search(r"(LiPO_\d+)_fit/model_fit_cycle_(\d+)\.csv$", name)
            return (match.group(1), int(match.group(2))) if match else ("", -1)

        for name in sorted(fit_names, key=sort_key):
            match = re.search(r"(LiPO_\d+)_fit/model_fit_cycle_(\d+)\.csv$", name)
            if not match:
                continue
            pack = match.group(1)
            cycle = int(match.group(2))
            text = archive.open(name).read().decode("utf-8-sig", "replace").splitlines()
            reader = csv.DictReader(text)
            samples: list[tuple[float, float]] = []
            for row in reader:
                soc = float_or_none(row.get("SOC", ""))
                r0 = float_or_none(row.get("RO", row.get("R_0", "")))
                if soc is None or r0 is None:
                    continue
                samples.append((soc, r0))
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
    return rows


def main() -> None:
    rows = summarize()
    DATA.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "pack",
                "cycle",
                "sample_count",
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
    print(f"Wrote {OUTPUT}")
    print(f"Rows: {len(rows)}")


if __name__ == "__main__":
    main()
