#!/usr/bin/env python3
"""Build a sampled DronePropB defective-propeller vibration packet.

Outputs:
  docs/data/dronepropb_sample_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category dronepropb_sample_packet_*

The full DronePropB archive is about 0.5 GB. This script records the full
Mendeley file inventory, but downloads only a representative C3 sample subset:
healthy SP1/SP2/SP3 and F1/F2/F3 at severities 1..3 for SP2. Treat it as an
initial vibration-ratio packet, not a complete fault classifier.
"""

from __future__ import annotations

import csv
import json
import math
import re
import urllib.request
from pathlib import Path
from typing import Iterable

import numpy as np
from scipy.io import loadmat
from scipy.signal import welch


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw"

OUTPUT = DATA / "dronepropb_sample_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

SOURCE_ID = "xkvfjmm8zg"
SOURCE_VERSION = 1
SOURCE_NAME = "DronePropB: Ground Testing Dataset for Commercial Drones with Defective Propellers"
SOURCE_URL = f"https://data.mendeley.com/datasets/{SOURCE_ID}/{SOURCE_VERSION}"
SOURCE_DOI = "https://doi.org/10.17632/xkvfjmm8zg.1"
SOURCE_ZIP_URL = f"https://data.mendeley.com/public-api/zip/{SOURCE_ID}/download/{SOURCE_VERSION}"
PUBLIC_API_BASE = "https://data.mendeley.com/public-api"

SAMPLE_DIR = RAW / "dronepropb_xkvfjmm8zg_v1_sample"
INVENTORY_JSON = SAMPLE_DIR / "dronepropb_file_inventory.json"

FILENAME_RE = re.compile(
    r"F(?P<fault>\d+)_SV(?P<severity>\d+)_SP(?P<speed>\d+)_C(?P<channel>\d+)(?:_R(?P<repeat>\d+))?\.mat"
)

SELECTED_FILENAMES = [
    "F0_SV0_SP1_C3_R1.mat",
    "F0_SV0_SP2_C3_R1.mat",
    "F0_SV0_SP3_C3_R1.mat",
    "F1_SV1_SP2_C3.mat",
    "F1_SV2_SP2_C3.mat",
    "F1_SV3_SP2_C3.mat",
    "F2_SV1_SP2_C3.mat",
    "F2_SV2_SP2_C3.mat",
    "F2_SV3_SP2_C3.mat",
    "F3_SV1_SP2_C3.mat",
    "F3_SV2_SP2_C3.mat",
    "F3_SV3_SP2_C3.mat",
]
BASELINE_FILENAME = "F0_SV0_SP2_C3_R1.mat"


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


def request_json(url: str) -> object:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0",
            "Accept": "application/vnd.mendeley-public-dataset.1+json",
        },
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.loads(response.read().decode("utf-8"))


def download_if_missing(url: str, path: Path, expected_size: int | None = None) -> None:
    if path.exists() and path.stat().st_size > 0:
        if expected_size is None or path.stat().st_size == expected_size:
            return
    path.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(request, timeout=180) as response, path.open("wb") as handle:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            handle.write(chunk)


def parse_filename(filename: str) -> dict[str, int | str]:
    match = FILENAME_RE.match(filename)
    if not match:
        raise ValueError(f"Unexpected DronePropB filename: {filename}")
    parsed: dict[str, int | str] = {"filename": filename}
    for key, value in match.groupdict().items():
        parsed[key] = int(value) if value is not None else 0
    return parsed


def fetch_file_inventory() -> list[dict[str, object]]:
    SAMPLE_DIR.mkdir(parents=True, exist_ok=True)
    folders = request_json(f"{PUBLIC_API_BASE}/datasets/{SOURCE_ID}/folders/{SOURCE_VERSION}")
    folder_id = folders[0]["id"]
    files = request_json(
        f"{PUBLIC_API_BASE}/datasets/{SOURCE_ID}/files?folder_id={folder_id}&version={SOURCE_VERSION}"
    )
    INVENTORY_JSON.write_text(json.dumps({"folders": folders, "files": files}, indent=2), encoding="utf-8")
    return list(files)


def ensure_selected_files(files: list[dict[str, object]]) -> dict[str, Path]:
    by_name = {str(file["filename"]): file for file in files}
    paths: dict[str, Path] = {}
    for filename in SELECTED_FILENAMES:
        file = by_name[filename]
        details = file["content_details"]
        path = SAMPLE_DIR / filename
        download_if_missing(str(details["download_url"]), path, int(file["size"]))
        paths[filename] = path
    return paths


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


def percentile(values: Iterable[float], p: float) -> float:
    clean = np.asarray([float(value) for value in values if math.isfinite(float(value))], dtype=float)
    if clean.size == 0:
        return math.nan
    return float(np.percentile(clean, p))


def peak_frequency(values: np.ndarray, sample_rate_hz: float, min_hz: float = 5.0) -> float:
    if len(values) < 16:
        return math.nan
    freq, power = welch(values, fs=sample_rate_hz, nperseg=min(len(values), 8192))
    mask = (freq >= min_hz) & np.isfinite(power)
    if not np.any(mask):
        return math.nan
    freq = freq[mask]
    power = power[mask]
    return float(freq[int(np.argmax(power))])


def dynamic_rms(values: np.ndarray) -> float:
    centered = np.asarray(values, dtype=float) - float(np.mean(values))
    return float(np.sqrt(np.mean(np.square(centered))))


def add_source_inventory(rows: list[dict[str, object]], files: list[dict[str, object]]) -> None:
    parsed = [parse_filename(str(file["filename"])) for file in files]
    fault_counts: dict[int, int] = {}
    for item in parsed:
        fault = int(item["fault"])
        fault_counts[fault] = fault_counts.get(fault, 0) + 1

    for metric, value, unit, note in [
        ("dataset_name", SOURCE_NAME, "text", "Mendeley dataset title."),
        ("doi", SOURCE_DOI, "url", "Dataset DOI."),
        ("full_archive_url", SOURCE_ZIP_URL, "url", "Public full-archive endpoint; not downloaded by this sample script."),
        ("file_count", len(files), "files", "Mendeley public file tree count."),
        ("total_file_tree_size", sum(int(file["size"]) for file in files), "bytes", "Sum of public file sizes; S3 zip may be smaller due compression."),
        ("selected_sample_count", len(SELECTED_FILENAMES), "files", "Representative C3 sample subset downloaded locally."),
        ("fault_class_counts", "|".join(f"F{key}:{fault_counts[key]}" for key in sorted(fault_counts)), "text", "Counts decoded from filenames."),
        ("selected_channel", 3, "channel", "This first packet samples C3 only to keep local cache small."),
    ]:
        add_metric(
            rows,
            row_type="dronepropb_sample_packet_source_inventory",
            name="dronepropb_mendeley_dataset",
            metric=metric,
            value=value,
            unit=unit,
            source_file=INVENTORY_JSON,
            source_url=SOURCE_URL,
            evidence_role="source_inventory",
            note=note,
        )


def add_file_inventory_rows(rows: list[dict[str, object]], files: list[dict[str, object]]) -> None:
    for file in files:
        filename = str(file["filename"])
        parsed = parse_filename(filename)
        details = file["content_details"]
        for metric, value, unit in [
            ("fault_class", parsed["fault"], "class"),
            ("severity", parsed["severity"], "level"),
            ("speed", parsed["speed"], "level"),
            ("channel", parsed["channel"], "channel"),
            ("repeat", parsed["repeat"], "repeat"),
            ("file_size", int(file["size"]), "bytes"),
            ("sha256", details.get("sha256_hash", ""), "hash"),
            ("selected_for_local_sample", filename in SELECTED_FILENAMES, "bool"),
        ]:
            add_metric(
                rows,
                row_type="dronepropb_sample_packet_file_inventory",
                name=filename,
                metric=metric,
                value=value,
                unit=unit,
                source_file=INVENTORY_JSON,
                source_url=str(details.get("download_url", SOURCE_URL)),
                evidence_role="mendeley_file_inventory",
                note="Filename fields follow F=fault, SV=severity, SP=speed, C=channel, R=repeat where present.",
            )


def sample_metrics(path: Path) -> dict[str, float | int | str]:
    mat = loadmat(path, squeeze_me=True, struct_as_record=False)
    acc = np.asarray(mat["AccData"].Vib_Data, dtype=float)
    acc_rate = float(mat["AccData"].samplingRate)
    acc_t = np.asarray(mat["AccData"].t, dtype=float)
    acc_dyn = acc - float(np.mean(acc))

    imu = np.asarray(mat["IMUData"].Vib_Data, dtype=float)
    imu_rate = float(mat["IMUData"].samplingRate)
    imu_t = np.asarray(mat["IMUData"].t, dtype=float)
    imu_dyn = imu - np.mean(imu, axis=1, keepdims=True)
    imu_vec = np.linalg.norm(imu_dyn, axis=0)
    battery = np.asarray(mat["BatteryVolt"], dtype=float)

    return {
        "acc_sample_rate": acc_rate,
        "acc_samples": int(acc.size),
        "acc_duration": float(acc_t[-1] - acc_t[0]) if acc_t.size else math.nan,
        "acc_dynamic_rms": dynamic_rms(acc),
        "acc_abs_dynamic_p95": float(np.percentile(np.abs(acc_dyn), 95)),
        "acc_abs_dynamic_p99": float(np.percentile(np.abs(acc_dyn), 99)),
        "acc_peak_frequency": peak_frequency(acc_dyn, acc_rate),
        "imu_sample_rate": imu_rate,
        "imu_samples": int(imu.shape[-1]),
        "imu_duration": float(imu_t[-1] - imu_t[0]) if imu_t.size else math.nan,
        "imu_vector_dynamic_rms": float(np.sqrt(np.mean(np.square(imu_vec)))),
        "imu_vector_abs_p95": float(np.percentile(np.abs(imu_vec), 95)),
        "imu_peak_frequency_x": peak_frequency(imu_dyn[0], imu_rate),
        "imu_peak_frequency_y": peak_frequency(imu_dyn[1], imu_rate),
        "imu_peak_frequency_z": peak_frequency(imu_dyn[2], imu_rate),
        "battery_mean": float(np.mean(battery)),
        "battery_min": float(np.min(battery)),
        "battery_samples": int(battery.size),
    }


def add_sample_rows(rows: list[dict[str, object]], sample_paths: dict[str, Path]) -> list[dict[str, object]]:
    sample_rows: list[dict[str, object]] = []
    for filename, path in sample_paths.items():
        parsed = parse_filename(filename)
        metrics = sample_metrics(path)
        entry: dict[str, object] = {**parsed, **metrics}
        sample_rows.append(entry)

    baseline = next(row for row in sample_rows if row["filename"] == BASELINE_FILENAME)
    baseline_acc = float(baseline["acc_dynamic_rms"])
    baseline_imu = float(baseline["imu_vector_dynamic_rms"])
    for entry in sample_rows:
        filename = str(entry["filename"])
        path = sample_paths[filename]
        metrics = [
            ("fault_class", entry["fault"], "class"),
            ("severity", entry["severity"], "level"),
            ("speed", entry["speed"], "level"),
            ("channel", entry["channel"], "channel"),
            ("repeat", entry["repeat"], "repeat"),
            ("acc_sample_rate", entry["acc_sample_rate"], "Hz"),
            ("acc_samples", entry["acc_samples"], "samples"),
            ("acc_duration", entry["acc_duration"], "s"),
            ("acc_dynamic_rms", entry["acc_dynamic_rms"], "source_unit"),
            ("acc_abs_dynamic_p95", entry["acc_abs_dynamic_p95"], "source_unit"),
            ("acc_abs_dynamic_p99", entry["acc_abs_dynamic_p99"], "source_unit"),
            ("acc_peak_frequency", entry["acc_peak_frequency"], "Hz"),
            ("acc_dynamic_rms_over_healthy_sp2_c3", float(entry["acc_dynamic_rms"]) / baseline_acc, "x"),
            ("imu_sample_rate", entry["imu_sample_rate"], "Hz"),
            ("imu_samples", entry["imu_samples"], "samples"),
            ("imu_duration", entry["imu_duration"], "s"),
            ("imu_vector_dynamic_rms", entry["imu_vector_dynamic_rms"], "source_unit"),
            ("imu_vector_dynamic_rms_over_healthy_sp2_c3", float(entry["imu_vector_dynamic_rms"]) / baseline_imu, "x"),
            ("imu_peak_frequency_x", entry["imu_peak_frequency_x"], "Hz"),
            ("imu_peak_frequency_y", entry["imu_peak_frequency_y"], "Hz"),
            ("imu_peak_frequency_z", entry["imu_peak_frequency_z"], "Hz"),
            ("battery_mean", entry["battery_mean"], "V"),
            ("battery_min", entry["battery_min"], "V"),
            ("battery_samples", entry["battery_samples"], "samples"),
        ]
        for metric, value, unit in metrics:
            add_metric(
                rows,
                row_type="dronepropb_sample_packet_sample_metric",
                name=filename,
                metric=metric,
                value=value,
                unit=unit,
                source_file=path,
                source_url=SOURCE_URL,
                evidence_role="sample_mat_metric",
                note="Dynamic RMS subtracts per-file mean. Frequency peaks use Welch PSD on the selected C3 sample subset.",
            )
    return sample_rows


def add_distribution_rows(rows: list[dict[str, object]], sample_rows: list[dict[str, object]]) -> None:
    baseline = next(row for row in sample_rows if row["filename"] == BASELINE_FILENAME)
    baseline_acc = float(baseline["acc_dynamic_rms"])
    fault_rows = [row for row in sample_rows if int(row["fault"]) > 0]
    ratios = [float(row["acc_dynamic_rms"]) / baseline_acc for row in fault_rows]
    peaks = [float(row["acc_peak_frequency"]) for row in fault_rows]
    for prefix, values, unit, note in [
        ("fault_acc_dynamic_rms_ratio", ratios, "x", "Selected fault samples versus healthy F0/SV0/SP2/C3/R1."),
        ("fault_acc_peak_frequency", peaks, "Hz", "Welch PSD peak of selected fault samples."),
    ]:
        for p in [0, 10, 50, 90, 100]:
            add_metric(
                rows,
                row_type="dronepropb_sample_packet_distribution_summary",
                name="selected_fault_c3_sp2_samples",
                metric=f"{prefix}_p{p}",
                value=percentile(values, p),
                unit=unit,
                source_file=OUTPUT,
                source_url=SOURCE_URL,
                evidence_role="sample_distribution",
                note=note,
            )

    add_metric(
        rows,
        row_type="dronepropb_sample_packet_method",
        name="scope_caveat",
        metric="recommended_use",
        value=(
            "Use this as a lightweight DronePropB inventory and representative C3 vibration "
            "ratio packet. It is not the full 111-file archive and should not be used as a "
            "complete classifier or absolute accelerometer calibration without channel/unit "
            "documentation."
        ),
        unit="text",
        source_file=INVENTORY_JSON,
        source_url=SOURCE_URL,
        evidence_role="handoff_guidance",
        note="Full Mendeley zip is about 0.5 GB; this script intentionally downloads only 12 representative files.",
    )


def sync_summary(packet_rows: list[dict[str, object]]) -> None:
    existing = read_rows(SUMMARY) if SUMMARY.exists() else []
    kept = [row for row in existing if not row.get("category", "").startswith("dronepropb_sample_packet_")]
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
    files = fetch_file_inventory()
    sample_paths = ensure_selected_files(files)

    rows: list[dict[str, object]] = []
    add_source_inventory(rows, files)
    add_file_inventory_rows(rows, files)
    sample_rows = add_sample_rows(rows, sample_paths)
    add_distribution_rows(rows, sample_rows)

    write_csv(OUTPUT, rows)
    sync_summary(rows)
    print(f"Wrote {len(rows)} rows to {repo_path(OUTPUT)}")
    print("Synced dronepropb_sample_packet_* rows into fpv_model_validation_summary.csv")


if __name__ == "__main__":
    main()
