"""Build a stratified DronePropB vibration handoff packet.

Outputs:
  docs/data/dronepropb_stratified_vibration_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category
  dronepropb_stratified_packet_*

The older DronePropB packet intentionally sampled only C3. This script keeps a
small cache budget but extends the sample across speed, channel, fault class,
and severity so damage/vibration tuning is less dependent on one sensor channel.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Iterable

import numpy as np

from analyze_dronepropb_sample_packet import (
    INVENTORY_JSON,
    PUBLIC_API_BASE,
    RAW,
    SOURCE_ID,
    SOURCE_NAME,
    SOURCE_URL,
    SOURCE_VERSION,
    download_if_missing,
    parse_filename,
    request_json,
    sample_metrics,
)


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

OUTPUT = DATA / "dronepropb_stratified_vibration_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"
SAMPLE_DIR = RAW / "dronepropb_xkvfjmm8zg_v1_sample"

SELECTED_FILENAMES = [
    # Healthy baselines: C3 speed sweep plus C1/C2/C3 at speed 2.
    "F0_SV0_SP1_C3_R1.mat",
    "F0_SV0_SP2_C1_R1.mat",
    "F0_SV0_SP2_C2_R1.mat",
    "F0_SV0_SP2_C3_R1.mat",
    "F0_SV0_SP3_C3_R1.mat",
    # Severity sweep at SP2/C3.
    "F1_SV1_SP2_C3.mat",
    "F1_SV2_SP2_C3.mat",
    "F1_SV3_SP2_C3.mat",
    "F2_SV1_SP2_C3.mat",
    "F2_SV2_SP2_C3.mat",
    "F2_SV3_SP2_C3.mat",
    "F3_SV1_SP2_C3.mat",
    "F3_SV2_SP2_C3.mat",
    "F3_SV3_SP2_C3.mat",
    # Speed sweep at SV2/C3.
    "F1_SV2_SP1_C3.mat",
    "F1_SV2_SP3_C3.mat",
    "F2_SV2_SP1_C3.mat",
    "F2_SV2_SP3_C3.mat",
    "F3_SV2_SP1_C3.mat",
    "F3_SV2_SP3_C3.mat",
    # Channel sweep at SV2/SP2.
    "F1_SV2_SP2_C1.mat",
    "F1_SV2_SP2_C2.mat",
    "F2_SV2_SP2_C1.mat",
    "F2_SV2_SP2_C2.mat",
    "F3_SV2_SP2_C1.mat",
    "F3_SV2_SP2_C2.mat",
]


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


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


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def percentile(values: Iterable[float], p: float) -> float:
    clean = np.asarray([float(value) for value in values if math.isfinite(float(value))], dtype=float)
    if clean.size == 0:
        return math.nan
    return float(np.percentile(clean, p))


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
    **extra: object,
) -> None:
    row = {
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
    row.update(extra)
    rows.append(row)


def fetch_file_inventory() -> list[dict[str, object]]:
    if INVENTORY_JSON.exists():
        import json

        data = json.loads(INVENTORY_JSON.read_text(encoding="utf-8"))
        return list(data["files"])
    folders = request_json(f"{PUBLIC_API_BASE}/datasets/{SOURCE_ID}/folders/{SOURCE_VERSION}")
    folder_id = folders[0]["id"]
    files = request_json(
        f"{PUBLIC_API_BASE}/datasets/{SOURCE_ID}/files?folder_id={folder_id}&version={SOURCE_VERSION}"
    )
    SAMPLE_DIR.mkdir(parents=True, exist_ok=True)
    import json

    INVENTORY_JSON.write_text(json.dumps({"folders": folders, "files": files}, indent=2), encoding="utf-8")
    return list(files)


def ensure_selected_files(files: list[dict[str, object]]) -> dict[str, Path]:
    by_name = {str(file["filename"]): file for file in files}
    paths: dict[str, Path] = {}
    missing = [filename for filename in SELECTED_FILENAMES if filename not in by_name]
    if missing:
        raise KeyError(f"Selected DronePropB files missing from inventory: {missing}")
    for filename in SELECTED_FILENAMES:
        file = by_name[filename]
        details = file["content_details"]
        path = SAMPLE_DIR / filename
        download_if_missing(str(details["download_url"]), path, int(file["size"]))
        paths[filename] = path
    return paths


def selected_file_inventory(files: list[dict[str, object]]) -> dict[str, dict[str, object]]:
    by_name = {str(file["filename"]): file for file in files}
    return {filename: by_name[filename] for filename in SELECTED_FILENAMES}


def sample_row(filename: str, path: Path) -> dict[str, object]:
    return {**parse_filename(filename), **sample_metrics(path)}


def baseline_key(row: dict[str, object]) -> tuple[int, int]:
    return int(row["speed"]), int(row["channel"])


def add_source_rows(
    rows: list[dict[str, object]],
    files: list[dict[str, object]],
    selected_files: dict[str, dict[str, object]],
) -> None:
    selected_size = sum(int(file["size"]) for file in selected_files.values())
    cached_size = sum((SAMPLE_DIR / filename).stat().st_size for filename in SELECTED_FILENAMES)
    for metric, value, unit, note in [
        ("dataset_name", SOURCE_NAME, "text", "Mendeley dataset title."),
        ("file_count", len(files), "files", "Full public inventory file count."),
        ("full_inventory_size", sum(int(file["size"]) for file in files), "bytes", "Full public inventory size; full zip is not required for this stratified sample."),
        ("selected_file_count", len(SELECTED_FILENAMES), "files", "Stratified sample count across speed, channel, fault class, and severity."),
        ("selected_file_size", selected_size, "bytes", "Total size of selected public files."),
        ("cached_selected_file_size", cached_size, "bytes", "Total local size after download/cache reuse."),
        ("selected_size_over_full_inventory", selected_size / sum(int(file["size"]) for file in files), "ratio", "Cache budget fraction versus full public file tree."),
    ]:
        add_metric(
            rows,
            row_type="dronepropb_stratified_packet_source_inventory",
            name="dronepropb_stratified_sample",
            metric=metric,
            value=value,
            unit=unit,
            source_file=INVENTORY_JSON,
            source_url=SOURCE_URL,
            evidence_role="source_inventory",
            note=note,
        )


def add_selected_file_rows(
    rows: list[dict[str, object]],
    selected_files: dict[str, dict[str, object]],
) -> None:
    for filename in SELECTED_FILENAMES:
        file = selected_files[filename]
        details = file["content_details"]
        parsed = parse_filename(filename)
        for metric, value, unit in [
            ("fault_class", parsed["fault"], "class"),
            ("severity", parsed["severity"], "level"),
            ("speed", parsed["speed"], "level"),
            ("channel", parsed["channel"], "channel"),
            ("repeat", parsed["repeat"], "repeat"),
            ("file_size", int(file["size"]), "bytes"),
        ]:
            add_metric(
                rows,
                row_type="dronepropb_stratified_packet_selected_file",
                name=filename,
                metric=metric,
                value=value,
                unit=unit,
                source_file=INVENTORY_JSON,
                source_url=str(details.get("download_url", SOURCE_URL)),
                evidence_role="selected_file_inventory",
                note="Filename fields follow F=fault, SV=severity, SP=speed, C=channel, R=repeat.",
            )


def add_sample_metric_rows(rows: list[dict[str, object]], sample_rows: list[dict[str, object]], paths: dict[str, Path]) -> None:
    healthy_by_key = {
        baseline_key(row): row
        for row in sample_rows
        if int(row["fault"]) == 0 and int(row["severity"]) == 0
    }
    for row in sample_rows:
        filename = str(row["filename"])
        healthy = healthy_by_key.get(baseline_key(row))
        acc_ratio = (
            float(row["acc_dynamic_rms"]) / float(healthy["acc_dynamic_rms"])
            if healthy is not None and float(healthy["acc_dynamic_rms"]) > 0.0
            else math.nan
        )
        imu_ratio = (
            float(row["imu_vector_dynamic_rms"]) / float(healthy["imu_vector_dynamic_rms"])
            if healthy is not None and float(healthy["imu_vector_dynamic_rms"]) > 0.0
            else math.nan
        )
        metric_rows = [
            ("fault_class", row["fault"], "class"),
            ("severity", row["severity"], "level"),
            ("speed", row["speed"], "level"),
            ("channel", row["channel"], "channel"),
            ("repeat", row["repeat"], "repeat"),
            ("acc_sample_rate", row["acc_sample_rate"], "Hz"),
            ("acc_duration", row["acc_duration"], "s"),
            ("acc_dynamic_rms", row["acc_dynamic_rms"], "source_unit"),
            ("acc_abs_dynamic_p95", row["acc_abs_dynamic_p95"], "source_unit"),
            ("acc_peak_frequency", row["acc_peak_frequency"], "Hz"),
            ("acc_dynamic_rms_over_healthy_same_speed_channel", acc_ratio, "x"),
            ("imu_sample_rate", row["imu_sample_rate"], "Hz"),
            ("imu_duration", row["imu_duration"], "s"),
            ("imu_vector_dynamic_rms", row["imu_vector_dynamic_rms"], "source_unit"),
            ("imu_vector_dynamic_rms_over_healthy_same_speed_channel", imu_ratio, "x"),
            ("imu_peak_frequency_x", row["imu_peak_frequency_x"], "Hz"),
            ("imu_peak_frequency_y", row["imu_peak_frequency_y"], "Hz"),
            ("imu_peak_frequency_z", row["imu_peak_frequency_z"], "Hz"),
            ("battery_mean", row["battery_mean"], "V"),
            ("battery_min", row["battery_min"], "V"),
        ]
        for metric, value, unit in metric_rows:
            add_metric(
                rows,
                row_type="dronepropb_stratified_packet_sample_metric",
                name=filename,
                metric=metric,
                value=value,
                unit=unit,
                source_file=paths[filename],
                source_url=SOURCE_URL,
                evidence_role="sample_mat_metric",
                note="Dynamic RMS subtracts per-file mean. Ratios use healthy sample with same speed/channel when available.",
                fault_class=row["fault"],
                severity=row["severity"],
                speed=row["speed"],
                channel=row["channel"],
            )


def add_factor_summary_rows(rows: list[dict[str, object]], sample_rows: list[dict[str, object]]) -> None:
    healthy_by_key = {
        baseline_key(row): row
        for row in sample_rows
        if int(row["fault"]) == 0 and int(row["severity"]) == 0
    }
    fault_rows = [row for row in sample_rows if int(row["fault"]) > 0]
    for row in fault_rows:
        healthy = healthy_by_key.get(baseline_key(row))
        row["acc_ratio"] = (
            float(row["acc_dynamic_rms"]) / float(healthy["acc_dynamic_rms"])
            if healthy is not None and float(healthy["acc_dynamic_rms"]) > 0.0
            else math.nan
        )
        row["imu_ratio"] = (
            float(row["imu_vector_dynamic_rms"]) / float(healthy["imu_vector_dynamic_rms"])
            if healthy is not None and float(healthy["imu_vector_dynamic_rms"]) > 0.0
            else math.nan
        )

    groups: list[tuple[str, str, list[dict[str, object]], str]] = []
    groups.append(("all_fault_samples_with_same_baseline", "all", [row for row in fault_rows if math.isfinite(float(row["acc_ratio"]))], "All fault rows with healthy same speed/channel baseline."))
    for severity in (1, 2, 3):
        groups.append((f"severity_{severity}_sp2_c3", "severity", [row for row in fault_rows if int(row["severity"]) == severity and int(row["speed"]) == 2 and int(row["channel"]) == 3], "Severity sweep at SP2/C3."))
    for speed in (1, 2, 3):
        groups.append((f"speed_{speed}_sv2_c3", "speed", [row for row in fault_rows if int(row["speed"]) == speed and int(row["severity"]) == 2 and int(row["channel"]) == 3], "Speed sweep at SV2/C3."))
    for channel in (1, 2, 3):
        groups.append((f"channel_{channel}_sv2_sp2", "channel", [row for row in fault_rows if int(row["channel"]) == channel and int(row["severity"]) == 2 and int(row["speed"]) == 2], "Channel sweep at SV2/SP2."))
    for fault in (1, 2, 3):
        groups.append((f"fault_{fault}_sv2_sp2_c123", "fault", [row for row in fault_rows if int(row["fault"]) == fault and int(row["severity"]) == 2 and int(row["speed"]) == 2 and int(row["channel"]) in (1, 2, 3)], "Fault-class sweep at SV2/SP2/C1-C3."))

    for name, group_kind, group_rows, note in groups:
        acc_ratios = [float(row["acc_ratio"]) for row in group_rows]
        imu_ratios = [float(row["imu_ratio"]) for row in group_rows]
        peak_freqs = [float(row["acc_peak_frequency"]) for row in group_rows]
        metrics = [
            ("sample_count", len(group_rows), "count"),
            ("acc_ratio_p50", percentile(acc_ratios, 50), "x"),
            ("acc_ratio_p90", percentile(acc_ratios, 90), "x"),
            ("acc_ratio_max", percentile(acc_ratios, 100), "x"),
            ("imu_ratio_p50", percentile(imu_ratios, 50), "x"),
            ("acc_peak_frequency_p50", percentile(peak_freqs, 50), "Hz"),
        ]
        for metric, value, unit in metrics:
            add_metric(
                rows,
                row_type="dronepropb_stratified_packet_factor_summary",
                name=name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=OUTPUT,
                source_url=SOURCE_URL,
                evidence_role="factor_summary",
                note=note,
                factor=group_kind,
            )

    strongest = max(fault_rows, key=lambda row: float(row.get("acc_ratio", -math.inf)))
    for metric, value, unit in [
        ("strongest_fault_sample", strongest["filename"], "text"),
        ("strongest_fault_acc_ratio", strongest["acc_ratio"], "x"),
        ("strongest_fault_class", strongest["fault"], "class"),
        ("strongest_fault_severity", strongest["severity"], "level"),
        ("strongest_fault_speed", strongest["speed"], "level"),
        ("strongest_fault_channel", strongest["channel"], "channel"),
    ]:
        add_metric(
            rows,
            row_type="dronepropb_stratified_packet_summary",
            name="dronepropb_stratified_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=SOURCE_URL,
            evidence_role="compact_vibration_handoff",
            note="Strongest selected fault row by external accelerometer RMS ratio over healthy same speed/channel.",
        )

    all_acc = [float(row["acc_ratio"]) for row in fault_rows if math.isfinite(float(row["acc_ratio"]))]
    all_imu = [float(row["imu_ratio"]) for row in fault_rows if math.isfinite(float(row["imu_ratio"]))]
    for metric, value, unit in [
        ("selected_fault_sample_count", len(fault_rows), "count"),
        ("fault_rows_with_same_baseline_count", len(all_acc), "count"),
        ("selected_fault_acc_ratio_p50", percentile(all_acc, 50), "x"),
        ("selected_fault_acc_ratio_p90", percentile(all_acc, 90), "x"),
        ("selected_fault_acc_ratio_max", percentile(all_acc, 100), "x"),
        ("selected_fault_imu_ratio_p50", percentile(all_imu, 50), "x"),
    ]:
        add_metric(
            rows,
            row_type="dronepropb_stratified_packet_summary",
            name="dronepropb_stratified_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=SOURCE_URL,
            evidence_role="compact_vibration_handoff",
            note="Summary across selected stratified fault rows; ratios use healthy same speed/channel baselines.",
        )

    add_metric(
        rows,
        row_type="dronepropb_stratified_packet_method",
        name="scope_caveat",
        metric="recommended_use",
        value=(
            "Use this as a stratified fault-vibration ratio packet across selected DronePropB speed/channel/fault/severity "
            "rows. It remains a ground-test dataset in source units; do not use it as absolute in-flight FPV IMU noise or "
            "blade-pass notch frequency calibration without sensor-unit and mounting documentation."
        ),
        unit="text",
        source_file=INVENTORY_JSON,
        source_url=SOURCE_URL,
        evidence_role="handoff_guidance",
        note="Selected files are about 15% of the public file tree by size; full classifier work still needs the full archive.",
    )


def sync_summary(packet_rows: list[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY) if SUMMARY.exists() else []
    kept = [row for row in existing if not row.get("category", "").startswith("dronepropb_stratified_packet_")]
    added: list[dict[str, str]] = []
    for row in packet_rows:
        added.append(
            {
                "category": str(row["row_type"]),
                "name": str(row["name"]),
                "metric": str(row["metric"]),
                "value": value_text(row["value"]),
                "unit": str(row["unit"]),
                "source": str(row.get("source_url") or row.get("source_file", "")),
            }
        )
    write_csv(SUMMARY, kept + added)
    return len(added)


def build_rows() -> list[dict[str, object]]:
    files = fetch_file_inventory()
    selected_files = selected_file_inventory(files)
    paths = ensure_selected_files(files)
    sample_rows = [sample_row(filename, paths[filename]) for filename in SELECTED_FILENAMES]

    rows: list[dict[str, object]] = []
    add_source_rows(rows, files, selected_files)
    add_selected_file_rows(rows, selected_files)
    add_sample_metric_rows(rows, sample_rows, paths)
    add_factor_summary_rows(rows, sample_rows)
    return rows


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
