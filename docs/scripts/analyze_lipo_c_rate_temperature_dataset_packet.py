"""Analyze a public C-rate/temperature battery discharge dataset subset.

Outputs:
  docs/data/mendeley_c_rate_temperature_file_inventory.csv
  docs/data/lipo_c_rate_temperature_subset_reference.csv
  docs/data/lipo_c_rate_temperature_calibration_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category lipo_crate_packet_*

The Mendeley kxsbr4x3j2 dataset is not an FPV LiPo pack dataset. It is useful
because it provides directly measured galvanostatic discharge curves at
multiple C-rates and chamber temperatures. This script caches a small,
reproducible subset rather than downloading the full ~638 MB public dataset.
"""

from __future__ import annotations

import csv
import json
import math
import re
import time
import urllib.request
from pathlib import Path
from typing import Callable, Iterable

import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "mendeley_kxsbr4x3j2_subset"
FILE_INVENTORY = DATA / "mendeley_c_rate_temperature_file_inventory.csv"
SUBSET_REFERENCE = DATA / "lipo_c_rate_temperature_subset_reference.csv"
PACKET = DATA / "lipo_c_rate_temperature_calibration_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

DATASET_ID = "kxsbr4x3j2"
DATASET_VERSION = 2
DATASET_DOI = "10.17632/kxsbr4x3j2.2"
DATASET_PAGE = f"https://data.mendeley.com/datasets/{DATASET_ID}/{DATASET_VERSION}"
PUBLIC_API_BASE = "https://data.mendeley.com/public-api"
DATASET_TITLE = "Experimental data of three lithium-ion batteries under galvanostatic discharge tests at different C-rates and operating temperatures"
LICENSE = "CC BY 4.0"

SELECTED_SERIES = "k1"
SELECTED_TEMPS = {"05degC": 5.0, "25degC": 25.0, "35degC": 35.0}
SELECTED_RATES_BY_CHEMISTRY = {
    "NCA": {0.05, 1.0, 2.0},
    "NMC": {0.05, 1.0, 2.0},
    "LFP": {0.05, 1.0, 2.0, 10.0, 20.0},
}
REFERENCE_RATE_C = 0.05
FRACTIONS = (0.10, 0.50, 0.90)
CURRENT_THRESHOLD_A = 0.01

FILENAME_RE = re.compile(
    r"^(?P<chemistry>LFP|NCA|NMC)_(?P<series>k\d+)_(?P<rate>0_05|\d+)C_(?P<temp>\d+)degC\.xlsx$"
)


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def write_csv(path: Path, rows: Iterable[dict[str, object]]) -> None:
    row_list = list(rows)
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames: list[str] = []
    for row in row_list:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(row_list)


def value_text(value: object) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    if isinstance(value, (int, np.integer)):
        return str(int(value))
    if isinstance(value, (float, np.floating)):
        if not math.isfinite(float(value)):
            return ""
        return f"{float(value):.12g}"
    return str(value)


def add_metric(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path,
    source_url: str = DATASET_PAGE,
    evidence_role: str = "",
    note: str = "",
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": value_text(value),
            "unit": unit,
            "source_file": repo_path(source_file),
            "source_url": source_url,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def get_json(url: str) -> object:
    headers = {
        "Accept": "application/vnd.mendeley-public-dataset.1+json",
        "User-Agent": "Mozilla/5.0",
    }
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def download_file(url: str, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.stat().st_size > 0:
        return
    tmp_path = path.with_suffix(path.suffix + ".tmp")
    last_error: Exception | None = None
    for attempt in range(1, 5):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(request, timeout=240) as response, tmp_path.open("wb") as handle:
                handle.write(response.read())
            tmp_path.replace(path)
            return
        except Exception as exc:  # network/CDN retries only
            last_error = exc
            if tmp_path.exists():
                tmp_path.unlink()
            time.sleep(2.0 * attempt)
    raise RuntimeError(f"failed to download {url}") from last_error


def parse_filename(filename: str) -> dict[str, object] | None:
    match = FILENAME_RE.match(filename)
    if not match:
        return None
    rate_token = match.group("rate")
    rate_c = 0.05 if rate_token == "0_05" else float(rate_token)
    return {
        "chemistry": match.group("chemistry"),
        "series": match.group("series"),
        "c_rate": rate_c,
        "temperature_c": float(match.group("temp")),
    }


def fetch_inventory() -> tuple[list[dict[str, object]], list[dict[str, object]]]:
    folders_raw = get_json(f"{PUBLIC_API_BASE}/datasets/{DATASET_ID}/folders/{DATASET_VERSION}")
    if not isinstance(folders_raw, list):
        raise RuntimeError("Mendeley folder API did not return a list")
    name_by_id = {row["id"]: row["name"] for row in folders_raw}
    folders: list[dict[str, object]] = []
    for row in folders_raw:
        folders.append(
            {
                "row_type": "mendeley_folder",
                "dataset_id": DATASET_ID,
                "dataset_version": DATASET_VERSION,
                "folder_id": row["id"],
                "folder_name": row["name"],
                "parent_id": row.get("parent_id", ""),
                "parent_name": name_by_id.get(row.get("parent_id", ""), ""),
                "created_date": row.get("created_date", ""),
                "source": DATASET_PAGE,
            }
        )

    files: list[dict[str, object]] = []
    for folder in folders:
        folder_id = str(folder["folder_id"])
        items = get_json(
            f"{PUBLIC_API_BASE}/datasets/{DATASET_ID}/files?folder_id={folder_id}&version={DATASET_VERSION}"
        )
        if not isinstance(items, list):
            continue
        for item in items:
            details = item.get("content_details", {})
            parsed = parse_filename(item.get("filename", ""))
            file_row: dict[str, object] = {
                "row_type": "mendeley_file",
                "dataset_id": DATASET_ID,
                "dataset_version": DATASET_VERSION,
                "doi": DATASET_DOI,
                "filename": item.get("filename", ""),
                "file_id": item.get("id", ""),
                "folder_id": folder_id,
                "folder_name": folder.get("folder_name", ""),
                "parent_id": folder.get("parent_id", ""),
                "parent_name": folder.get("parent_name", ""),
                "size_bytes": item.get("size", 0),
                "size_mb": float(item.get("size", 0)) / 1_000_000.0,
                "content_type": details.get("content_type", ""),
                "sha256_hash": details.get("sha256_hash", ""),
                "download_url": details.get("download_url", ""),
                "source": DATASET_PAGE,
            }
            if parsed:
                file_row.update(parsed)
            files.append(file_row)
    return folders, files


def is_selected_file(row: dict[str, object]) -> bool:
    chemistry = row.get("chemistry")
    series = row.get("series")
    c_rate = row.get("c_rate")
    folder_name = row.get("folder_name")
    return (
        chemistry in SELECTED_RATES_BY_CHEMISTRY
        and series == SELECTED_SERIES
        and folder_name in SELECTED_TEMPS
        and isinstance(c_rate, float)
        and c_rate in SELECTED_RATES_BY_CHEMISTRY[str(chemistry)]
    )


def local_path_for(row: dict[str, object]) -> Path:
    chemistry = str(row.get("chemistry", "misc"))
    folder_name = str(row.get("folder_name", "root"))
    filename = str(row["filename"])
    return RAW / chemistry / folder_name / filename


def detect_discharge_segment(frame: pd.DataFrame) -> pd.DataFrame:
    grouped = frame.groupby("Step_Index")["Current(A)"].mean()
    discharge_step = grouped.idxmin()
    segment = frame[(frame["Step_Index"] == discharge_step) & (frame["Current(A)"] < -CURRENT_THRESHOLD_A)].copy()
    if segment.empty:
        raise RuntimeError("could not identify discharge segment")
    return segment.sort_values("Test_Time(s)")


def interp_at_fraction(fractions: np.ndarray, values: np.ndarray, target: float) -> float:
    order = np.argsort(fractions)
    x = fractions[order]
    y = values[order]
    unique_x, unique_index = np.unique(x, return_index=True)
    unique_y = y[unique_index]
    if target < unique_x[0] or target > unique_x[-1]:
        return math.nan
    return float(np.interp(target, unique_x, unique_y))


def analyze_workbook(path: Path, row: dict[str, object]) -> dict[str, object]:
    frame = pd.read_excel(path, engine="openpyxl")
    discharge = detect_discharge_segment(frame)
    t = discharge["Test_Time(s)"].to_numpy(dtype=float)
    elapsed = t - t[0]
    dt = np.diff(t, prepend=t[0])
    dt[dt < 0] = 0.0
    current = -discharge["Current(A)"].to_numpy(dtype=float)
    voltage = discharge["Voltage(V)"].to_numpy(dtype=float)
    temp = discharge["Surface_Temp(degC)"].to_numpy(dtype=float)
    charge_ah_cumulative = np.cumsum(current * dt) / 3600.0
    energy_wh_cumulative = np.cumsum(current * voltage * dt) / 3600.0
    capacity_ah = float(charge_ah_cumulative[-1])
    energy_wh = float(energy_wh_cumulative[-1])
    fractions = charge_ah_cumulative / capacity_ah

    start_time = float(t[0])
    rest = frame[
        (frame["Test_Time(s)"] < start_time)
        & (frame["Test_Time(s)"] >= start_time - 30.0)
        & (frame["Current(A)"].abs() < CURRENT_THRESHOLD_A)
    ]
    rest_voltage = float(rest["Voltage(V)"].median()) if not rest.empty else math.nan
    rest_temp = float(rest["Surface_Temp(degC)"].median()) if not rest.empty else math.nan
    voltage_after_5s = float(np.interp(5.0, elapsed, voltage)) if elapsed[-1] >= 5.0 else float(voltage[0])
    current_mean = float(np.mean(current))
    initial_sag_v = rest_voltage - voltage_after_5s if math.isfinite(rest_voltage) else math.nan
    initial_r_ohm = initial_sag_v / current_mean if current_mean > 0 and math.isfinite(initial_sag_v) else math.nan
    c_rate = float(row["c_rate"])
    implied_nominal_capacity_ah = current_mean / c_rate if c_rate > 0 else math.nan

    result: dict[str, object] = {
        "row_type": "selected_discharge_curve",
        "name": Path(str(row["filename"])).stem,
        "dataset_id": DATASET_ID,
        "dataset_version": DATASET_VERSION,
        "doi": DATASET_DOI,
        "source": DATASET_PAGE,
        "local_source_file": repo_path(path),
        "filename": row["filename"],
        "chemistry": row["chemistry"],
        "series": row["series"],
        "temperature_c": row["temperature_c"],
        "c_rate": c_rate,
        "file_size_mb": row["size_mb"],
        "sample_count": len(discharge),
        "discharge_duration_s": float(elapsed[-1]),
        "mean_current_a": current_mean,
        "capacity_ah": capacity_ah,
        "implied_nominal_capacity_ah_from_current_over_c": implied_nominal_capacity_ah,
        "delivered_capacity_over_implied_nominal": capacity_ah / implied_nominal_capacity_ah
        if implied_nominal_capacity_ah > 0
        else math.nan,
        "energy_wh": energy_wh,
        "average_voltage_v": energy_wh / capacity_ah if capacity_ah > 0 else math.nan,
        "rest_voltage_before_discharge_v": rest_voltage,
        "rest_temperature_before_discharge_c": rest_temp,
        "loaded_voltage_after_5s_v": voltage_after_5s,
        "initial_sag_v": initial_sag_v,
        "initial_resistance_proxy_ohm": initial_r_ohm,
        "initial_resistance_proxy_mohm": initial_r_ohm * 1000.0 if math.isfinite(initial_r_ohm) else math.nan,
        "start_surface_temp_c": float(temp[0]),
        "max_surface_temp_c": float(np.max(temp)),
        "surface_temp_rise_c": float(np.max(temp) - temp[0]),
        "end_voltage_v": float(voltage[-1]),
        "end_surface_temp_c": float(temp[-1]),
    }
    for fraction in FRACTIONS:
        pct = int(round(fraction * 100))
        result[f"voltage_at_{pct}pct_delivered_v"] = interp_at_fraction(fractions, voltage, fraction)
        result[f"surface_temp_at_{pct}pct_delivered_c"] = interp_at_fraction(fractions, temp, fraction)
    return result


def add_baseline_comparisons(rows: list[dict[str, object]]) -> None:
    baseline_by_key = {
        (row["chemistry"], row["temperature_c"]): row
        for row in rows
        if float(row["c_rate"]) == REFERENCE_RATE_C
    }
    for row in rows:
        baseline = baseline_by_key.get((row["chemistry"], row["temperature_c"]))
        if not baseline or row is baseline:
            continue
        current_delta = float(row["mean_current_a"]) - float(baseline["mean_current_a"])
        row["baseline_c_rate"] = REFERENCE_RATE_C
        row["baseline_curve_name"] = baseline["name"]
        row["current_delta_vs_baseline_a"] = current_delta
        for fraction in FRACTIONS:
            pct = int(round(fraction * 100))
            row_voltage = float(row[f"voltage_at_{pct}pct_delivered_v"])
            baseline_voltage = float(baseline[f"voltage_at_{pct}pct_delivered_v"])
            deficit = baseline_voltage - row_voltage
            row[f"voltage_deficit_vs_0_05c_at_{pct}pct_v"] = deficit
            row[f"resistance_proxy_vs_0_05c_at_{pct}pct_mohm"] = (
                deficit / current_delta * 1000.0 if current_delta > 0 and math.isfinite(deficit) else math.nan
            )


def add_temperature_ratios(rows: list[dict[str, object]]) -> list[dict[str, object]]:
    by_key = {
        (row["chemistry"], row["c_rate"], row["temperature_c"]): row
        for row in rows
    }
    ratio_rows: list[dict[str, object]] = []
    for chemistry in sorted(SELECTED_RATES_BY_CHEMISTRY):
        for c_rate in sorted(SELECTED_RATES_BY_CHEMISTRY[chemistry]):
            base = by_key.get((chemistry, c_rate, 25.0))
            if not base:
                continue
            for temp in [5.0, 35.0]:
                row = by_key.get((chemistry, c_rate, temp))
                if not row:
                    continue
                ratio_rows.append(
                    {
                        "row_type": "temperature_ratio_vs_25c",
                        "name": f"{chemistry}_{c_rate:g}C_{temp:g}C_vs_25C",
                        "dataset_id": DATASET_ID,
                        "dataset_version": DATASET_VERSION,
                        "doi": DATASET_DOI,
                        "source": DATASET_PAGE,
                        "chemistry": chemistry,
                        "c_rate": c_rate,
                        "temperature_c": temp,
                        "reference_temperature_c": 25.0,
                        "capacity_ratio_vs_25c": float(row["capacity_ah"]) / float(base["capacity_ah"]),
                        "energy_ratio_vs_25c": float(row["energy_wh"]) / float(base["energy_wh"]),
                        "voltage_50pct_delta_vs_25c_v": float(row["voltage_at_50pct_delivered_v"])
                        - float(base["voltage_at_50pct_delivered_v"]),
                        "initial_resistance_proxy_ratio_vs_25c": float(row["initial_resistance_proxy_mohm"])
                        / float(base["initial_resistance_proxy_mohm"]),
                        "surface_temp_rise_delta_vs_25c_c": float(row["surface_temp_rise_c"])
                        - float(base["surface_temp_rise_c"]),
                    }
                )
    return ratio_rows


def require_one(rows: list[dict[str, object]], predicate: Callable[[dict[str, object]], bool]) -> dict[str, object]:
    for row in rows:
        if predicate(row):
            return row
    raise LookupError("required row not found")


def build_packet(
    *,
    file_rows: list[dict[str, object]],
    selected_rows: list[dict[str, object]],
    ratio_rows: list[dict[str, object]],
) -> list[dict[str, str]]:
    packet: list[dict[str, str]] = []
    total_size_mb = sum(float(row.get("size_mb", 0.0)) for row in file_rows)
    xlsx_rows = [row for row in file_rows if str(row.get("filename", "")).endswith(".xlsx")]
    selected_size_mb = sum(float(row.get("file_size_mb", 0.0)) for row in selected_rows)
    all_rates = [float(row["c_rate"]) for row in file_rows if "c_rate" in row and row.get("c_rate") != ""]

    for metric, value, unit in [
        ("dataset_version", DATASET_VERSION, "version"),
        ("file_count_total", len(file_rows), "count"),
        ("xlsx_file_count", len(xlsx_rows), "count"),
        ("total_dataset_size_mb", total_size_mb, "MB"),
        ("selected_curve_count", len(selected_rows), "count"),
        ("selected_download_size_mb", selected_size_mb, "MB"),
        ("chemistry_count", len({row["chemistry"] for row in selected_rows}), "count"),
        ("temperature_count", len({row["temperature_c"] for row in selected_rows}), "count"),
        ("all_file_c_rate_min", min(all_rates), "C"),
        ("all_file_c_rate_max", max(all_rates), "C"),
    ]:
        add_metric(
            packet,
            row_type="lipo_crate_packet_source_inventory",
            name="Mendeley kxsbr4x3j2",
            metric=metric,
            value=value,
            unit=unit,
            source_file=FILE_INVENTORY,
            evidence_role="open_discharge_curve_dataset",
            note="Public Mendeley dataset metadata. Full dataset is inventoried; only the selected subset is cached locally.",
        )
    add_metric(
        packet,
        row_type="lipo_crate_packet_source_inventory",
        name="Mendeley kxsbr4x3j2",
        metric="dataset_title",
        value=DATASET_TITLE,
        unit="text",
        source_file=FILE_INVENTORY,
        evidence_role="open_discharge_curve_dataset",
        note="Not an FPV LiPo pack dataset; use for C-rate/temperature shape, not absolute FPV ESR.",
    )
    add_metric(
        packet,
        row_type="lipo_crate_packet_source_inventory",
        name="Mendeley kxsbr4x3j2",
        metric="license",
        value=LICENSE,
        unit="text",
        source_file=FILE_INVENTORY,
        evidence_role="open_discharge_curve_dataset",
        note="Dataset page lists CC BY 4.0.",
    )

    selected_metrics = [
        ("temperature_c", "C"),
        ("c_rate", "C"),
        ("mean_current_a", "A"),
        ("capacity_ah", "Ah"),
        ("delivered_capacity_over_implied_nominal", "ratio"),
        ("average_voltage_v", "V"),
        ("initial_resistance_proxy_mohm", "mOhm"),
        ("voltage_at_50pct_delivered_v", "V"),
        ("surface_temp_rise_c", "C"),
        ("voltage_deficit_vs_0_05c_at_50pct_v", "V"),
        ("resistance_proxy_vs_0_05c_at_50pct_mohm", "mOhm"),
    ]
    for row in selected_rows:
        for metric, unit in selected_metrics:
            add_metric(
                packet,
                row_type="lipo_crate_packet_selected_curve",
                name=str(row["name"]),
                metric=metric,
                value=row.get(metric, math.nan),
                unit=unit,
                source_file=SUBSET_REFERENCE,
                evidence_role="selected_curve_summary",
                note="Selected k1 curve from the Mendeley C-rate/temperature dataset.",
            )

    for row in ratio_rows:
        for metric, unit in [
            ("capacity_ratio_vs_25c", "ratio"),
            ("energy_ratio_vs_25c", "ratio"),
            ("voltage_50pct_delta_vs_25c_v", "V"),
            ("initial_resistance_proxy_ratio_vs_25c", "ratio"),
            ("surface_temp_rise_delta_vs_25c_c", "C"),
        ]:
            add_metric(
                packet,
                row_type="lipo_crate_packet_temperature_ratio",
                name=str(row["name"]),
                metric=metric,
                value=row.get(metric, math.nan),
                unit=unit,
                source_file=SUBSET_REFERENCE,
                evidence_role="temperature_shape_summary",
                note="Same chemistry/rate selected k1 curve compared with the 25 C selected curve.",
            )

    max_temp_rise = max(selected_rows, key=lambda row: float(row["surface_temp_rise_c"]))
    min_capacity_ratio = min(ratio_rows, key=lambda row: float(row["capacity_ratio_vs_25c"]))
    max_initial_r_ratio_5c = max(
        (row for row in ratio_rows if float(row["temperature_c"]) == 5.0),
        key=lambda row: float(row["initial_resistance_proxy_ratio_vs_25c"]),
    )
    nca_2c_25 = require_one(
        selected_rows,
        lambda row: row["chemistry"] == "NCA" and float(row["temperature_c"]) == 25.0 and float(row["c_rate"]) == 2.0,
    )
    nmc_2c_25 = require_one(
        selected_rows,
        lambda row: row["chemistry"] == "NMC" and float(row["temperature_c"]) == 25.0 and float(row["c_rate"]) == 2.0,
    )
    lfp_20c_25 = require_one(
        selected_rows,
        lambda row: row["chemistry"] == "LFP" and float(row["temperature_c"]) == 25.0 and float(row["c_rate"]) == 20.0,
    )
    racing_configured_c = 60.0

    summary_metrics = [
        ("selected_curve_count", len(selected_rows), "count"),
        ("selected_file_size_mb", selected_size_mb, "MB"),
        ("max_selected_c_rate_nca_nmc", 2.0, "C"),
        ("max_selected_c_rate_lfp", 20.0, "C"),
        ("racingQuad_configured_current_c", racing_configured_c, "C"),
        ("racingQuad_configured_c_over_nca_nmc_selected_max", racing_configured_c / 2.0, "ratio"),
        ("racingQuad_configured_c_over_lfp_selected_max", racing_configured_c / 20.0, "ratio"),
        ("nca_2c_25c_mid_resistance_proxy_vs_0_05c_mohm", nca_2c_25.get("resistance_proxy_vs_0_05c_at_50pct_mohm"), "mOhm"),
        ("nmc_2c_25c_mid_resistance_proxy_vs_0_05c_mohm", nmc_2c_25.get("resistance_proxy_vs_0_05c_at_50pct_mohm"), "mOhm"),
        ("lfp_20c_25c_mid_resistance_proxy_vs_0_05c_mohm", lfp_20c_25.get("resistance_proxy_vs_0_05c_at_50pct_mohm"), "mOhm"),
        ("max_surface_temp_rise_c", max_temp_rise["surface_temp_rise_c"], "C"),
        ("max_surface_temp_rise_curve", max_temp_rise["name"], "text"),
        ("min_capacity_ratio_vs_25c", min_capacity_ratio["capacity_ratio_vs_25c"], "ratio"),
        ("min_capacity_ratio_curve", min_capacity_ratio["name"], "text"),
        ("max_5c_initial_resistance_ratio_vs_25c", max_initial_r_ratio_5c["initial_resistance_proxy_ratio_vs_25c"], "ratio"),
        ("max_5c_initial_resistance_ratio_curve", max_initial_r_ratio_5c["name"], "text"),
    ]
    for metric, value, unit in summary_metrics:
        add_metric(
            packet,
            row_type="lipo_crate_packet_summary",
            name="lipo_c_rate_temperature_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=PACKET,
            evidence_role="compact_handoff_summary",
            note="Compact C-rate/temperature handoff summary. Do not use these cylindrical-cell values as FPV LiPo absolute ESR.",
        )

    add_metric(
        packet,
        row_type="lipo_crate_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Use this dataset for measured C-rate/temperature voltage-shape checks only. It is NCA/NMC/LFP cylindrical "
            "cell data, not high-C FPV LiPo pouch-pack ESR. Normalize absolute resistance to FPV pack measurements before runtime use."
        ),
        unit="text",
        source_file=PACKET,
        evidence_role="method_caveat",
        note="The selected subset is intentionally small and reproducible; the full dataset remains external.",
    )
    return packet


def sync_summary(packet_rows: Iterable[dict[str, str]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("lipo_crate_packet_")]
    added: list[dict[str, str]] = []
    for row in packet_rows:
        added.append(
            {
                "category": row["row_type"],
                "name": row["name"],
                "metric": row["metric"],
                "value": row["value"],
                "unit": row["unit"],
                "source": row.get("source_url") or row.get("source_file", ""),
            }
        )
    write_csv(SUMMARY, kept + added)
    return len(added)


def main() -> None:
    folders, file_rows = fetch_inventory()
    selected_files = [row for row in file_rows if is_selected_file(row)]
    if len(selected_files) != 33:
        raise RuntimeError(f"expected 33 selected files, found {len(selected_files)}")

    for row in selected_files:
        download_file(str(row["download_url"]), local_path_for(row))

    selected_rows = [analyze_workbook(local_path_for(row), row) for row in selected_files]
    add_baseline_comparisons(selected_rows)
    ratio_rows = add_temperature_ratios(selected_rows)
    packet_rows = build_packet(file_rows=file_rows, selected_rows=selected_rows, ratio_rows=ratio_rows)

    source_rows: list[dict[str, object]] = [
        {
            "row_type": "mendeley_source",
            "dataset_id": DATASET_ID,
            "dataset_version": DATASET_VERSION,
            "doi": DATASET_DOI,
            "title": DATASET_TITLE,
            "license": LICENSE,
            "source": DATASET_PAGE,
        }
    ]
    write_csv(FILE_INVENTORY, source_rows + folders + file_rows)
    write_csv(SUBSET_REFERENCE, selected_rows + ratio_rows)
    write_csv(PACKET, packet_rows)
    synced = sync_summary(packet_rows)

    print(f"Wrote {repo_path(FILE_INVENTORY)} with {1 + len(folders) + len(file_rows)} rows")
    print(f"Wrote {repo_path(SUBSET_REFERENCE)} with {len(selected_rows) + len(ratio_rows)} rows")
    print(f"Wrote {repo_path(PACKET)} with {len(packet_rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
