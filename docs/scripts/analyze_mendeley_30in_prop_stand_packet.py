#!/usr/bin/env python3
"""Build a Mendeley/Tyto 30-inch hover thrust-stand packet.

Outputs:
  docs/data/mendeley_30in_prop_stand_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category mendeley_30in_prop_packet_*

The source is a 30-inch propeller hover dataset collected with a Tyto Robotics
Flight Stand 50. It is too large to calibrate 5-inch FPV coefficients directly,
but it is a clean public thrust-stand source for column semantics, 100 Hz
measurement noise, static T ~= k*omega^2 fits, torque/thrust ratio, and
electrical-versus-mechanical power checks.
"""

from __future__ import annotations

import csv
import math
import re
import urllib.request
import zipfile
from pathlib import Path
from typing import Iterable

import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw"

OUTPUT = DATA / "mendeley_30in_prop_stand_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

SOURCE_ID = "69hhwc3fd3"
SOURCE_VERSION = 1
SOURCE_TITLE = "Dataset of 30-Inch Propellers Performance for UAVs: A Comprehensive Comparison in Hover Condition"
SOURCE_URL = f"https://data.mendeley.com/datasets/{SOURCE_ID}/{SOURCE_VERSION}"
SOURCE_DOI = "https://doi.org/10.17632/69hhwc3fd3.1"
SOURCE_ZIP_URL = f"https://data.mendeley.com/public-api/zip/{SOURCE_ID}/download/{SOURCE_VERSION}"

OUTER_ZIP = RAW / "mendeley_30in_propellers_hover_69hhwc3fd3_v1.zip"
EXTRACT_DIR = RAW / "mendeley_30in_propellers_hover_69hhwc3fd3_v1"
INNER_DIR = EXTRACT_DIR / "Data_30_inch_propeller"
DATA_DIR = INNER_DIR / "Data 30 inch propeller"

DIAMETER_M = 0.762
RADIUS_M = DIAMETER_M / 2.0
DISK_AREA_M2 = math.pi * RADIUS_M * RADIUS_M
RHO_KG_M3 = 1.225
SPEED_OF_SOUND_M_S = 340.29
EXPECTED_SAMPLE_RATE_HZ = 100.0
EXPECTED_DURATION_S = 60.0

RACINGQUAD_K_N_PER_RAD2 = 1.45e-6
RACINGQUAD_DIAMETER_M = 0.127
HEAVYLIFT_K_N_PER_RAD2 = 4.5e-5
HEAVYLIFT_DIAMETER_M = 0.254

SUMMARY_KINDS = ["AVG", "STD", "MIN", "MAX"]
SOURCE_COLUMNS = [
    "Time (s)",
    "ESC Signal (us)",
    "Voltage (V)",
    "Current (A)",
    "Thrust (N)",
    "Torque (N*m)",
    "Rotation speed (RPM)",
    "Electrical power (W)",
]


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def value_text(value: object) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        if not math.isfinite(value):
            return ""
        return f"{value:.12g}"
    return str(value)


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
        for row in row_list:
            writer.writerow({key: value_text(row.get(key, "")) for key in fieldnames})


def download_if_missing(url: str, path: Path) -> None:
    if path.exists() and path.stat().st_size > 0:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(request, timeout=180) as response, path.open("wb") as handle:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            handle.write(chunk)


def extract_if_missing(zip_path: Path, target_dir: Path) -> None:
    if target_dir.exists() and any(target_dir.iterdir()):
        return
    target_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path) as archive:
        archive.extractall(target_dir)


def ensure_dataset() -> None:
    download_if_missing(SOURCE_ZIP_URL, OUTER_ZIP)
    extract_if_missing(OUTER_ZIP, EXTRACT_DIR)
    if not DATA_DIR.exists():
        inner = next(EXTRACT_DIR.rglob("Data 30 inch propeller.zip"), None)
        if inner is None:
            raise FileNotFoundError("Could not find nested Data 30 inch propeller.zip")
        extract_if_missing(inner, INNER_DIR)
    if not DATA_DIR.exists():
        raise FileNotFoundError(f"Expected extracted data at {DATA_DIR}")


def normalized_columns(df: pd.DataFrame) -> pd.DataFrame:
    rename: dict[object, str] = {}
    for column in df.columns:
        text = str(column).strip().lower()
        if text.startswith("time"):
            rename[column] = "time_s"
        elif text.startswith("esc"):
            rename[column] = "esc_us"
        elif text.startswith("voltage"):
            rename[column] = "voltage_v"
        elif text.startswith("current"):
            rename[column] = "current_a"
        elif text.startswith("thrust"):
            rename[column] = "thrust_n"
        elif "torque" in text:
            rename[column] = "torque_nm"
        elif text.startswith("rotation"):
            rename[column] = "rpm"
        elif text.startswith("electrical"):
            rename[column] = "elec_power_w"
    return df.rename(columns=rename)


def read_excel_table(path: Path) -> pd.DataFrame:
    df = normalized_columns(pd.read_excel(path))
    required = {"voltage_v", "current_a", "thrust_n", "torque_nm", "rpm", "elec_power_w"}
    missing = sorted(required - set(df.columns))
    if missing:
        raise ValueError(f"{path} missing columns {missing}")
    return df


def raw_files() -> list[Path]:
    ensure_dataset()
    return sorted(DATA_DIR.glob("P*/P*_* RPM.xls"))


def summary_file(prop: str, kind: str) -> Path:
    return DATA_DIR / prop / f"{prop}_{kind}.xlsx"


def parse_raw_file(path: Path) -> tuple[str, int]:
    match = re.match(r"(P\d+)_(\d+) RPM\.xls$", path.name)
    if not match:
        raise ValueError(f"Unexpected raw filename: {path.name}")
    return match.group(1), int(match.group(2))


def setpoint_from_rpm(rpm: float) -> int:
    return int(round(float(rpm) / 500.0) * 500)


def percentile(values: Iterable[float], p: float) -> float:
    clean = np.asarray([float(value) for value in values if math.isfinite(float(value))], dtype=float)
    if clean.size == 0:
        return math.nan
    return float(np.percentile(clean, p))


def r2_score(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    ss_res = float(np.sum(np.square(y_true - y_pred)))
    ss_tot = float(np.sum(np.square(y_true - float(np.mean(y_true)))))
    if ss_tot <= 0.0:
        return math.nan
    return 1.0 - ss_res / ss_tot


def rmse(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(y_true - y_pred))))


def fit_no_intercept(x: np.ndarray, y: np.ndarray) -> dict[str, float]:
    x = np.asarray(x, dtype=float)
    y = np.asarray(y, dtype=float)
    mask = np.isfinite(x) & np.isfinite(y)
    x = x[mask]
    y = y[mask]
    denom = float(np.dot(x, x))
    if x.size < 3 or denom <= 0.0:
        return {"k": math.nan, "rmse": math.nan, "r2": math.nan, "samples": float(x.size)}
    k = float(np.dot(x, y) / denom)
    pred = k * x
    return {"k": k, "rmse": rmse(y, pred), "r2": r2_score(y, pred), "samples": float(x.size)}


def fit_with_intercept(x: np.ndarray, y: np.ndarray) -> dict[str, float]:
    x = np.asarray(x, dtype=float)
    y = np.asarray(y, dtype=float)
    mask = np.isfinite(x) & np.isfinite(y)
    x = x[mask]
    y = y[mask]
    if x.size < 3:
        return {"k": math.nan, "b": math.nan, "rmse": math.nan, "r2": math.nan, "samples": float(x.size)}
    design = np.vstack([x, np.ones_like(x)]).T
    k, b = np.linalg.lstsq(design, y, rcond=None)[0]
    pred = design @ np.asarray([k, b], dtype=float)
    return {"k": float(k), "b": float(b), "rmse": rmse(y, pred), "r2": r2_score(y, pred), "samples": float(x.size)}


def derived(df: pd.DataFrame) -> pd.DataFrame:
    out = df.copy()
    out["omega_rad_s"] = out["rpm"] * 2.0 * math.pi / 60.0
    out["omega2"] = np.square(out["omega_rad_s"])
    out["omega3"] = np.power(out["omega_rad_s"], 3)
    out["n_hz"] = out["rpm"] / 60.0
    out["tip_speed_m_s"] = out["omega_rad_s"] * RADIUS_M
    out["tip_mach"] = out["tip_speed_m_s"] / SPEED_OF_SOUND_M_S
    out["thrust_k_n_per_rad2"] = out["thrust_n"] / out["omega2"]
    out["torque_k_nm_per_rad2"] = out["torque_nm"] / out["omega2"]
    out["torque_per_thrust_m"] = out["torque_nm"] / out["thrust_n"]
    out["mechanical_power_w"] = out["torque_nm"] * out["omega_rad_s"]
    out["mechanical_over_electrical_power"] = out["mechanical_power_w"] / out["elec_power_w"]
    out["ct"] = out["thrust_n"] / (RHO_KG_M3 * np.square(out["n_hz"]) * DIAMETER_M**4)
    out["cq"] = out["torque_nm"] / (RHO_KG_M3 * np.square(out["n_hz"]) * DIAMETER_M**5)
    out["cp"] = out["mechanical_power_w"] / (RHO_KG_M3 * np.power(out["n_hz"], 3) * DIAMETER_M**5)
    ideal_power = np.power(out["thrust_n"], 1.5) / math.sqrt(2.0 * RHO_KG_M3 * DISK_AREA_M2)
    out["hover_figure_of_merit"] = ideal_power / out["mechanical_power_w"]
    return out


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path | str,
    source_url: str,
    evidence_role: str,
    note: str = "",
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": value,
            "unit": unit,
            "source_file": repo_path(source_file) if isinstance(source_file, Path) else source_file,
            "source_url": source_url,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def add_distribution(
    rows: list[dict[str, object]],
    *,
    name: str,
    metric_prefix: str,
    values: Iterable[float],
    unit: str,
    source_file: Path | str,
    source_url: str,
    evidence_role: str,
    note: str,
) -> None:
    for p in [0, 10, 50, 90, 95, 99, 100]:
        add_metric(
            rows,
            row_type="mendeley_30in_prop_packet_distribution_summary",
            name=name,
            metric=f"{metric_prefix}_p{p}",
            value=percentile(values, p),
            unit=unit,
            source_file=source_file,
            source_url=source_url,
            evidence_role=evidence_role,
            note=note,
        )


def add_source_inventory(rows: list[dict[str, object]], raw_paths: list[Path]) -> None:
    prop_names = sorted({path.parent.name for path in raw_paths})
    setpoints = sorted({parse_raw_file(path)[1] for path in raw_paths})
    summary_paths = [summary_file(prop, kind) for prop in prop_names for kind in SUMMARY_KINDS]

    source_metrics = [
        ("dataset_title", SOURCE_TITLE, "text", "Mendeley dataset title."),
        ("doi", SOURCE_DOI, "url", "Dataset DOI."),
        ("outer_zip_size", OUTER_ZIP.stat().st_size if OUTER_ZIP.exists() else math.nan, "bytes", "Downloaded Mendeley public zip."),
        ("propeller_count", len(prop_names), "count", "P1..P5 source folders."),
        ("raw_xls_file_count", len(raw_paths), "files", "Raw 100 Hz Flight Stand files."),
        ("summary_xlsx_file_count", sum(1 for path in summary_paths if path.exists()), "files", "AVG/STD/MIN/MAX workbooks across P1..P5."),
        ("rpm_setpoints", "|".join(str(value) for value in setpoints), "rpm", "Filename setpoints available in raw XLS files."),
        ("rpm_setpoint_min", min(setpoints), "rpm", "Actual files include lower RPM than the page text summary."),
        ("rpm_setpoint_max", max(setpoints), "rpm", "Maximum raw file setpoint."),
        ("sample_rate", EXPECTED_SAMPLE_RATE_HZ, "Hz", "Mendeley description says data were collected at 100 Hz."),
        ("case_duration", EXPECTED_DURATION_S, "s", "Mendeley description says 60 seconds per case."),
        ("prop_diameter", DIAMETER_M, "m", "30 inch propeller diameter converted to SI."),
        ("assumed_air_density", RHO_KG_M3, "kg/m^3", "Used only for CT/CP/Figure-of-Merit derived rows."),
    ]
    for metric, value, unit, note in source_metrics:
        add_metric(
            rows,
            row_type="mendeley_30in_prop_packet_source_inventory",
            name="mendeley_30in_hover_dataset",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTER_ZIP,
            source_url=SOURCE_URL,
            evidence_role="source_inventory",
            note=note,
        )

    for column_index, column in enumerate(SOURCE_COLUMNS):
        add_metric(
            rows,
            row_type="mendeley_30in_prop_packet_column_schema",
            name=column,
            metric="column_index",
            value=column_index,
            unit="source_column",
            source_file=raw_paths[0],
            source_url=SOURCE_URL,
            evidence_role="source_schema",
            note="Column names normalized from the source Excel workbooks; micro sign and torque dot are ASCII-normalized in this packet.",
        )


def add_raw_file_rows(rows: list[dict[str, object]], raw_paths: list[Path]) -> list[dict[str, float | str]]:
    summaries: list[dict[str, float | str]] = []
    for path in raw_paths:
        prop, setpoint = parse_raw_file(path)
        df = derived(read_excel_table(path))
        name = f"{prop}_{setpoint}rpm_raw"
        t = df["time_s"].to_numpy(dtype=float)
        dt = np.diff(t)
        metrics = [
            ("sample_count", len(df), "samples"),
            ("duration", float(t[-1] - t[0]), "s"),
            ("dt_median", percentile(dt, 50), "s"),
            ("dt_p99", percentile(dt, 99), "s"),
            ("rpm_setpoint", setpoint, "rpm"),
            ("esc_signal_median", percentile(df["esc_us"], 50), "us"),
            ("voltage_mean", float(df["voltage_v"].mean()), "V"),
            ("voltage_std", float(df["voltage_v"].std(ddof=0)), "V"),
            ("current_mean", float(df["current_a"].mean()), "A"),
            ("current_std", float(df["current_a"].std(ddof=0)), "A"),
            ("rpm_mean", float(df["rpm"].mean()), "rpm"),
            ("rpm_std", float(df["rpm"].std(ddof=0)), "rpm"),
            ("rpm_cv_percent", float(df["rpm"].std(ddof=0) / df["rpm"].mean() * 100.0), "percent"),
            ("thrust_mean", float(df["thrust_n"].mean()), "N"),
            ("thrust_std", float(df["thrust_n"].std(ddof=0)), "N"),
            ("thrust_cv_percent", float(df["thrust_n"].std(ddof=0) / df["thrust_n"].mean() * 100.0), "percent"),
            ("torque_mean", float(df["torque_nm"].mean()), "N*m"),
            ("torque_std", float(df["torque_nm"].std(ddof=0)), "N*m"),
            ("torque_cv_percent", float(df["torque_nm"].std(ddof=0) / df["torque_nm"].mean() * 100.0), "percent"),
            ("electrical_power_mean", float(df["elec_power_w"].mean()), "W"),
            ("mechanical_power_mean", float(df["mechanical_power_w"].mean()), "W"),
            ("mechanical_over_electrical_power_mean", float(df["mechanical_over_electrical_power"].mean()), "x"),
            ("thrust_k_mean", float(df["thrust_k_n_per_rad2"].mean()), "N/(rad/s)^2"),
            ("thrust_k_std", float(df["thrust_k_n_per_rad2"].std(ddof=0)), "N/(rad/s)^2"),
            ("torque_per_thrust_mean", float(df["torque_per_thrust_m"].mean()), "m"),
            ("ct_mean", float(df["ct"].mean()), "CT"),
            ("cp_mean", float(df["cp"].mean()), "CP"),
            ("hover_figure_of_merit_mean", float(df["hover_figure_of_merit"].mean()), "x"),
            ("tip_mach_mean", float(df["tip_mach"].mean()), "Mach"),
        ]
        summary_entry: dict[str, float | str] = {"prop": prop, "setpoint_rpm": float(setpoint)}
        for metric, value, unit in metrics:
            add_metric(
                rows,
                row_type="mendeley_30in_prop_packet_raw_file_summary",
                name=name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=path,
                source_url=SOURCE_ZIP_URL,
                evidence_role="raw_100hz_file_summary",
                note="Computed from a 60 s 100 Hz Flight Stand XLS source file.",
            )
            summary_entry[metric] = float(value) if isinstance(value, (int, float, np.floating)) else value
        summaries.append(summary_entry)
    return summaries


def add_summary_workbook_rows(rows: list[dict[str, object]], props: list[str]) -> dict[str, pd.DataFrame]:
    avg_by_prop: dict[str, pd.DataFrame] = {}
    metric_units = {
        "esc_us": "us",
        "voltage_v": "V",
        "current_a": "A",
        "thrust_n": "N",
        "torque_nm": "N*m",
        "rpm": "rpm",
        "elec_power_w": "W",
        "omega_rad_s": "rad/s",
        "thrust_k_n_per_rad2": "N/(rad/s)^2",
        "torque_k_nm_per_rad2": "N*m/(rad/s)^2",
        "torque_per_thrust_m": "m",
        "mechanical_power_w": "W",
        "mechanical_over_electrical_power": "x",
        "ct": "CT",
        "cp": "CP",
        "hover_figure_of_merit": "x",
        "tip_mach": "Mach",
    }
    for prop in props:
        for kind in SUMMARY_KINDS:
            path = summary_file(prop, kind)
            df = derived(read_excel_table(path))
            if kind == "AVG":
                avg_by_prop[prop] = df
            for _, point in df.iterrows():
                setpoint = setpoint_from_rpm(float(point["rpm"]))
                name = f"{prop}_{setpoint}rpm_{kind.lower()}"
                for metric, unit in metric_units.items():
                    if metric not in df.columns:
                        continue
                    add_metric(
                        rows,
                        row_type="mendeley_30in_prop_packet_summary_point",
                        name=name,
                        metric=metric,
                        value=float(point[metric]),
                        unit=unit,
                        source_file=path,
                        source_url=SOURCE_ZIP_URL,
                        evidence_role=f"{kind.lower()}_workbook_point",
                        note="Derived rows use 30 inch diameter and sea-level density only for CT/CP/Figure-of-Merit calculations.",
                    )
    return avg_by_prop


def add_fit_metric_rows(
    rows: list[dict[str, object]],
    *,
    name: str,
    fit: dict[str, float],
    coefficient_metric: str,
    coefficient_unit: str,
    target_unit: str,
    source_file: Path,
    note: str,
) -> None:
    for metric, value, unit in [
        (coefficient_metric, fit["k"], coefficient_unit),
        ("rmse", fit["rmse"], target_unit),
        ("r2", fit["r2"], "R2"),
        ("samples", fit["samples"], "points"),
    ]:
        add_metric(
            rows,
            row_type="mendeley_30in_prop_packet_curve_fit",
            name=name,
            metric=metric,
            value=value,
            unit=unit,
            source_file=source_file,
            source_url=SOURCE_ZIP_URL,
            evidence_role="static_hover_curve_fit",
            note=note,
        )
    if "b" in fit:
        add_metric(
            rows,
            row_type="mendeley_30in_prop_packet_curve_fit",
            name=name,
            metric="intercept",
            value=fit["b"],
            unit=target_unit,
            source_file=source_file,
            source_url=SOURCE_ZIP_URL,
            evidence_role="static_hover_curve_fit",
            note=note,
        )


def add_fit_rows(rows: list[dict[str, object]], avg_by_prop: dict[str, pd.DataFrame]) -> None:
    thrust_fits: list[float] = []
    torque_fits: list[float] = []
    qt_medians: list[float] = []
    fm_peaks: list[float] = []
    for prop, df in avg_by_prop.items():
        path = summary_file(prop, "AVG")
        omega2 = df["omega2"].to_numpy(dtype=float)
        omega3 = df["omega3"].to_numpy(dtype=float)
        thrust = df["thrust_n"].to_numpy(dtype=float)
        torque = df["torque_nm"].to_numpy(dtype=float)
        mech_power = df["mechanical_power_w"].to_numpy(dtype=float)
        elec_power = df["elec_power_w"].to_numpy(dtype=float)

        thrust_no = fit_no_intercept(omega2, thrust)
        thrust_with = fit_with_intercept(omega2, thrust)
        torque_no = fit_no_intercept(omega2, torque)
        power_no = fit_no_intercept(omega3, mech_power)
        elec_power_no = fit_no_intercept(omega3, elec_power)

        thrust_fits.append(thrust_no["k"])
        torque_fits.append(torque_no["k"])
        qt_medians.append(float(np.median(df["torque_per_thrust_m"])))
        fm_peaks.append(float(np.max(df["hover_figure_of_merit"])))

        add_fit_metric_rows(
            rows,
            name=f"{prop}_thrust_vs_omega2_no_intercept",
            fit=thrust_no,
            coefficient_metric="thrust_coefficient",
            coefficient_unit="N/(rad/s)^2",
            target_unit="N",
            source_file=path,
            note="Static hover fit from the AVG workbook: T = k*omega^2.",
        )
        add_fit_metric_rows(
            rows,
            name=f"{prop}_thrust_vs_omega2_with_intercept",
            fit=thrust_with,
            coefficient_metric="thrust_coefficient",
            coefficient_unit="N/(rad/s)^2",
            target_unit="N",
            source_file=path,
            note="Static hover fit from the AVG workbook: T = k*omega^2 + b.",
        )
        add_fit_metric_rows(
            rows,
            name=f"{prop}_torque_vs_omega2_no_intercept",
            fit=torque_no,
            coefficient_metric="torque_coefficient",
            coefficient_unit="N*m/(rad/s)^2",
            target_unit="N*m",
            source_file=path,
            note="Static hover fit from the AVG workbook: Q = kq*omega^2.",
        )
        add_fit_metric_rows(
            rows,
            name=f"{prop}_mechanical_power_vs_omega3_no_intercept",
            fit=power_no,
            coefficient_metric="power_coefficient",
            coefficient_unit="W/(rad/s)^3",
            target_unit="W",
            source_file=path,
            note="Mechanical power is torque*omega; fit checks expected cubic speed scaling.",
        )
        add_fit_metric_rows(
            rows,
            name=f"{prop}_electrical_power_vs_omega3_no_intercept",
            fit=elec_power_no,
            coefficient_metric="electrical_power_coefficient",
            coefficient_unit="W/(rad/s)^3",
            target_unit="W",
            source_file=path,
            note="Electrical power includes motor/ESC losses; fit is a gross cubic scaling check.",
        )

    add_distribution(
        rows,
        name="five_propeller_fit_distribution",
        metric_prefix="thrust_coefficient",
        values=thrust_fits,
        unit="N/(rad/s)^2",
        source_file=OUTPUT,
        source_url=SOURCE_URL,
        evidence_role="fit_distribution",
        note="Distribution across P1..P5 no-intercept T=k*omega^2 fits.",
    )
    add_distribution(
        rows,
        name="five_propeller_fit_distribution",
        metric_prefix="torque_coefficient",
        values=torque_fits,
        unit="N*m/(rad/s)^2",
        source_file=OUTPUT,
        source_url=SOURCE_URL,
        evidence_role="fit_distribution",
        note="Distribution across P1..P5 no-intercept Q=kq*omega^2 fits.",
    )
    add_distribution(
        rows,
        name="five_propeller_fit_distribution",
        metric_prefix="torque_per_thrust_median",
        values=qt_medians,
        unit="m",
        source_file=OUTPUT,
        source_url=SOURCE_URL,
        evidence_role="fit_distribution",
        note="Distribution of each propeller median Q/T over RPM points.",
    )
    add_distribution(
        rows,
        name="five_propeller_fit_distribution",
        metric_prefix="hover_figure_of_merit_peak",
        values=fm_peaks,
        unit="x",
        source_file=OUTPUT,
        source_url=SOURCE_URL,
        evidence_role="fit_distribution",
        note="Figure of merit uses sea-level density and 30 inch disk area.",
    )

    kt_min = percentile(thrust_fits, 0)
    kt_max = percentile(thrust_fits, 100)
    comparison_metrics = [
        ("thrust_k_min_over_racingQuad_k", kt_min / RACINGQUAD_K_N_PER_RAD2, "x", "30-inch hover coefficients are not transferable to 5-inch racing props."),
        ("thrust_k_max_over_racingQuad_k", kt_max / RACINGQUAD_K_N_PER_RAD2, "x", "30-inch hover coefficients are not transferable to 5-inch racing props."),
        ("thrust_k_min_over_heavyLift_k", kt_min / HEAVYLIFT_K_N_PER_RAD2, "x", "30-inch props are still much larger than the current 10-inch heavyLift preset."),
        ("thrust_k_max_over_heavyLift_k", kt_max / HEAVYLIFT_K_N_PER_RAD2, "x", "30-inch props are still much larger than the current 10-inch heavyLift preset."),
        ("diameter_over_racingQuad", DIAMETER_M / RACINGQUAD_DIAMETER_M, "x", "Diameter scale only."),
        ("diameter_over_heavyLift", DIAMETER_M / HEAVYLIFT_DIAMETER_M, "x", "Diameter scale only."),
        ("racingQuad_thrust_k", RACINGQUAD_K_N_PER_RAD2, "N/(rad/s)^2", "Current documented 5-inch FPV static scale."),
        ("heavyLift_thrust_k", HEAVYLIFT_K_N_PER_RAD2, "N/(rad/s)^2", "Current documented 10-inch heavyLift static scale."),
    ]
    for metric, value, unit, note in comparison_metrics:
        add_metric(
            rows,
            row_type="mendeley_30in_prop_packet_current_model_comparison",
            name="current_preset_scale_context",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=SOURCE_URL,
            evidence_role="current_model_context",
            note=note,
        )


def add_noise_rows(rows: list[dict[str, object]], raw_summaries: list[dict[str, float | str]]) -> None:
    for prefix, key, unit, note in [
        ("rpm_cv_percent", "rpm_cv_percent", "percent", "Closed-loop setpoint stability in each 60 s raw file."),
        ("thrust_cv_percent", "thrust_cv_percent", "percent", "Raw thrust measurement/operating variation per 60 s file."),
        ("torque_cv_percent", "torque_cv_percent", "percent", "Raw torque measurement/operating variation per 60 s file."),
        ("mechanical_over_electrical_power_mean", "mechanical_over_electrical_power_mean", "x", "Mechanical power = torque*omega divided by measured electrical power."),
        ("thrust_k_mean", "thrust_k_mean", "N/(rad/s)^2", "Raw-file mean T/omega^2 values."),
        ("torque_per_thrust_mean", "torque_per_thrust_mean", "m", "Raw-file mean Q/T values."),
    ]:
        add_distribution(
            rows,
            name="raw_file_distribution",
            metric_prefix=prefix,
            values=[float(row[key]) for row in raw_summaries],
            unit=unit,
            source_file=OUTPUT,
            source_url=SOURCE_URL,
            evidence_role="raw_file_noise_distribution",
            note=note,
        )

    add_metric(
        rows,
        row_type="mendeley_30in_prop_packet_method",
        name="scope_caveat",
        metric="recommended_use",
        value=(
            "Use this packet as an open Tyto/Flight Stand hover-data schema, 100 Hz "
            "measurement-noise, static T~omega^2, Q/T, and power-consistency source. "
            "Do not use its 30-inch propeller coefficients to tune 5-inch FPV or 10-inch "
            "heavyLift presets without explicit prop-scale conversion."
        ),
        unit="text",
        source_file=OUTER_ZIP,
        source_url=SOURCE_URL,
        evidence_role="handoff_guidance",
        note="The dataset is single-prop hover only; it contains no wind-on, forward-flow, or motor-step dynamics.",
    )


def sync_summary(packet_rows: list[dict[str, object]]) -> None:
    existing = read_rows(SUMMARY) if SUMMARY.exists() else []
    kept = [row for row in existing if not row.get("category", "").startswith("mendeley_30in_prop_packet_")]
    for row in packet_rows:
        kept.append(
            {
                "category": row["row_type"],
                "name": row["name"],
                "metric": row["metric"],
                "value": value_text(row["value"]),
                "unit": row["unit"],
                "source": row["source_url"] or row["source_file"],
            }
        )
    write_csv(SUMMARY, kept)


def main() -> None:
    raw_paths = raw_files()
    props = sorted({path.parent.name for path in raw_paths})

    rows: list[dict[str, object]] = []
    add_source_inventory(rows, raw_paths)
    raw_summaries = add_raw_file_rows(rows, raw_paths)
    avg_by_prop = add_summary_workbook_rows(rows, props)
    add_fit_rows(rows, avg_by_prop)
    add_noise_rows(rows, raw_summaries)

    write_csv(OUTPUT, rows)
    sync_summary(rows)
    print(f"Wrote {len(rows)} rows to {repo_path(OUTPUT)}")
    print("Synced mendeley_30in_prop_packet_* rows into fpv_model_validation_summary.csv")


if __name__ == "__main__":
    main()
