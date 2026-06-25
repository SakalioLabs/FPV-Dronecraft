"""Build a RATM high-speed acceleration/drag residual packet.

Outputs:
  docs/data/ratm_accel_drag_residual_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category ratm_accel_drag_packet_*

This script reuses the already range-extracted Race Against the Machine 500 Hz
high-speed windows. It does not attempt a clean drag fit from aggressive powered
flight. Instead, it exposes the observed speed-rate envelope beside the current
`racingQuad` drag-deceleration demand, so the airframe-drag feasibility gap is
quantified without downloading the full 15.9 GiB release again.
"""

from __future__ import annotations

import csv
import math
from collections import defaultdict
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

OUTPUT = DATA / "ratm_accel_drag_residual_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"
WINDOW_REFERENCE = DATA / "ratm_high_speed_window_reference.csv"
FLIGHT_METRICS = DATA / "ratm_high_speed_flight_metrics.csv"
AIRFRAME_REFERENCE = DATA / "airframe_drag_reference.csv"

SOURCE_URL = "https://github.com/Drone-Racing/drone-racing-dataset/releases/tag/v3.0.0"
README_URL = "https://raw.githubusercontent.com/tii-racing/drone-racing-dataset/main/README.md"
PAPER_DOI_URL = "https://doi.org/10.1109/LRA.2024.3371288"
SYNC_RATE_HZ = 500.0
GRAVITY_M_S2 = 9.80665
HIGH_SPEED_THRESHOLD_M_S = 21.0
HELD_SPEED_RATE_THRESHOLD_M_S2 = 5.0
SAMPLE_OFFSETS_S = (-0.50, -0.25, 0.0, 0.25, 0.50)


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
        for row in row_list:
            writer.writerow({key: value_text(row.get(key, "")) for key in fieldnames})


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


def parse_float(row: dict[str, str], key: str) -> float:
    try:
        return float(row.get(key, ""))
    except (TypeError, ValueError):
        return math.nan


def clean(values: Iterable[float]) -> list[float]:
    return [value for value in values if math.isfinite(value)]


def percentile(values: Iterable[float], p: float) -> float:
    values = sorted(clean(values))
    if not values:
        return math.nan
    if len(values) == 1:
        return values[0]
    rank = (len(values) - 1) * p / 100.0
    low = math.floor(rank)
    high = math.ceil(rank)
    if low == high:
        return values[low]
    return values[low] + (values[high] - values[low]) * (rank - low)


def safe_ratio(numerator: float, denominator: float, min_denominator: float = 1.0e-9) -> float:
    if not math.isfinite(numerator) or not math.isfinite(denominator) or abs(denominator) < min_denominator:
        return math.nan
    return numerator / denominator


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path = OUTPUT,
    source_url: str = SOURCE_URL,
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
            "source_file": repo_path(source_file),
            "source_url": source_url,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def current_reference() -> dict[str, float]:
    for row in read_rows(AIRFRAME_REFERENCE):
        if (
            row.get("row_type") == "current_vs_ratm_speed_floor"
            and row.get("preset") == "racingQuad"
            and row.get("axis") == "x"
        ):
            weight_n = float(row["weight_n"])
            return {
                "mass_kg": weight_n / GRAVITY_M_S2,
                "weight_n": weight_n,
                "max_thrust_n": float(row["current_total_max_thrust_n"]),
                "horizontal_margin_n": float(row["current_horizontal_thrust_margin_n"]),
                "drag_c_n_per_m_s2": float(row["current_total_quadratic_c_n_per_m_s2"]),
            }
    raise LookupError("missing racingQuad current_vs_ratm_speed_floor row in airframe_drag_reference.csv")


def thrust_mean(row: dict[str, str]) -> float:
    values = [parse_float(row, f"thrust[{index}]") for index in range(4)]
    values = clean(values)
    if len(values) != 4:
        return math.nan
    return sum(values) / 4.0


def derived_rows_for_window(rows: list[dict[str, str]], current: dict[str, float]) -> list[dict[str, float | str]]:
    ordered = sorted(rows, key=lambda row: parse_float(row, "elapsed_time"))
    speed = [parse_float(row, "speed_m_s") for row in ordered]
    horizontal_speed = [parse_float(row, "horizontal_speed_m_s") for row in ordered]
    time = [parse_float(row, "elapsed_time") for row in ordered]
    derived: list[dict[str, float | str]] = []
    mass = current["mass_kg"]
    drag_c = current["drag_c_n_per_m_s2"]
    for index, row in enumerate(ordered):
        if index == 0 or index == len(ordered) - 1:
            speed_rate = math.nan
            horizontal_speed_rate = math.nan
        else:
            dt = time[index + 1] - time[index - 1]
            if dt > 0.0:
                speed_rate = (speed[index + 1] - speed[index - 1]) / dt
                horizontal_speed_rate = (horizontal_speed[index + 1] - horizontal_speed[index - 1]) / dt
            else:
                speed_rate = math.nan
                horizontal_speed_rate = math.nan
        drag_force = drag_c * speed[index] * speed[index] if math.isfinite(speed[index]) else math.nan
        drag_decel = drag_force / mass if math.isfinite(drag_force) else math.nan
        apparent_decel = max(0.0, -speed_rate) if math.isfinite(speed_rate) else math.nan
        apparent_decel_force = mass * apparent_decel if math.isfinite(apparent_decel) else math.nan
        required_total = math.hypot(current["weight_n"], drag_force) if math.isfinite(drag_force) else math.nan
        derived.append(
            {
                "archive": row["archive"],
                "flight_id": row["flight_id"],
                "window_rank": int(row["window_rank"]),
                "offset_from_vmax_s": parse_float(row, "offset_from_vmax_s"),
                "elapsed_time_s": time[index],
                "speed_m_s": speed[index],
                "horizontal_speed_m_s": horizontal_speed[index],
                "speed_rate_m_s2": speed_rate,
                "horizontal_speed_rate_m_s2": horizontal_speed_rate,
                "apparent_decel_m_s2": apparent_decel,
                "apparent_decel_force_n": apparent_decel_force,
                "current_drag_force_n": drag_force,
                "current_drag_decel_m_s2": drag_decel,
                "current_drag_decel_over_abs_speed_rate": safe_ratio(drag_decel, abs(speed_rate)),
                "current_drag_force_over_apparent_decel_force": safe_ratio(drag_force, apparent_decel_force),
                "current_required_total_thrust_over_max": safe_ratio(required_total, current["max_thrust_n"]),
                "thrust_mean": thrust_mean(row),
                "channels_thrust_us": parse_float(row, "channels_thrust"),
                "vbat_v": parse_float(row, "vbat"),
                "mocap_residual": parse_float(row, "drone_residual"),
                "is_vmax_sample": 1.0 if row.get("is_vmax_sample", "").lower() == "true" else 0.0,
            }
        )
    return derived


def add_source_inventory(rows: list[dict[str, object]], current: dict[str, float], windows: dict[str, list[dict[str, str]]]) -> None:
    source_metrics = [
        ("source_dataset", "Race Against the Machine drone racing dataset", "text", README_URL),
        ("paper_doi", PAPER_DOI_URL, "url", PAPER_DOI_URL),
        ("source_window_reference", repo_path(WINDOW_REFERENCE), "path", repo_path(WINDOW_REFERENCE)),
        ("source_flight_metrics", repo_path(FLIGHT_METRICS), "path", repo_path(FLIGHT_METRICS)),
        ("source_airframe_reference", repo_path(AIRFRAME_REFERENCE), "path", repo_path(AIRFRAME_REFERENCE)),
        ("window_count", len(windows), "count", SOURCE_URL),
        ("window_sample_count", sum(len(value) for value in windows.values()), "count", SOURCE_URL),
        ("sync_rate_hz", SYNC_RATE_HZ, "Hz", README_URL),
        ("high_speed_threshold_m_s", HIGH_SPEED_THRESHOLD_M_S, "m/s", README_URL),
        ("held_speed_rate_threshold_m_s2", HELD_SPEED_RATE_THRESHOLD_M_S2, "m/s^2", repo_path(OUTPUT)),
        ("current_racing_mass_kg", current["mass_kg"], "kg", repo_path(AIRFRAME_REFERENCE)),
        ("current_racing_drag_c_n_per_m_s2", current["drag_c_n_per_m_s2"], "N/(m/s)^2", repo_path(AIRFRAME_REFERENCE)),
        ("current_racing_horizontal_margin_n", current["horizontal_margin_n"], "N", repo_path(AIRFRAME_REFERENCE)),
    ]
    for metric, value, unit, source_url in source_metrics:
        add_metric(
            rows,
            row_type="ratm_accel_drag_packet_source_inventory",
            name="RATM_high_speed_window_residual_inputs",
            metric=metric,
            value=value,
            unit=unit,
            source_url=source_url,
            evidence_role="source_inventory",
            note="Inputs are existing local RATM range-extraction outputs plus current racingQuad airframe-drag reference rows.",
        )


def add_window_summary(
    rows: list[dict[str, object]],
    window_rows: list[dict[str, float | str]],
) -> dict[str, float | str]:
    name = f"{window_rows[0]['archive']}:{window_rows[0]['flight_id']}:rank{window_rows[0]['window_rank']}"
    speed = [float(row["speed_m_s"]) for row in window_rows]
    speed_rate = [float(row["speed_rate_m_s2"]) for row in window_rows]
    decel = [float(row["apparent_decel_m_s2"]) for row in window_rows]
    drag_decel = [float(row["current_drag_decel_m_s2"]) for row in window_rows]
    drag_force = [float(row["current_drag_force_n"]) for row in window_rows]
    required = [float(row["current_required_total_thrust_over_max"]) for row in window_rows]
    thrust = [float(row["thrust_mean"]) for row in window_rows]
    high_rows = [row for row in window_rows if float(row["speed_m_s"]) >= HIGH_SPEED_THRESHOLD_M_S]
    held_high_rows = [
        row
        for row in high_rows
        if math.isfinite(float(row["speed_rate_m_s2"]))
        and abs(float(row["speed_rate_m_s2"])) <= HELD_SPEED_RATE_THRESHOLD_M_S2
    ]
    vmax_row = max(window_rows, key=lambda row: float(row["speed_m_s"]))
    vmax_speed_rate = float(vmax_row["speed_rate_m_s2"])
    vmax_drag_decel = float(vmax_row["current_drag_decel_m_s2"])

    summary: dict[str, float | str] = {
        "window_sample_count": len(window_rows),
        "speed_max_m_s": max(speed),
        "speed_p95_m_s": percentile(speed, 95.0),
        "speed_rate_p05_m_s2": percentile(speed_rate, 5.0),
        "speed_rate_p50_m_s2": percentile(speed_rate, 50.0),
        "speed_rate_p95_m_s2": percentile(speed_rate, 95.0),
        "observed_decel_p95_m_s2": percentile(decel, 95.0),
        "observed_decel_max_m_s2": max(clean(decel)),
        "current_drag_decel_p50_m_s2": percentile(drag_decel, 50.0),
        "current_drag_decel_p95_m_s2": percentile(drag_decel, 95.0),
        "current_drag_force_p95_n": percentile(drag_force, 95.0),
        "current_required_total_thrust_over_max_p95": percentile(required, 95.0),
        "current_drag_decel_p95_over_observed_decel_p95": safe_ratio(percentile(drag_decel, 95.0), percentile(decel, 95.0)),
        "speed_rate_at_vmax_m_s2": vmax_speed_rate,
        "current_drag_decel_at_vmax_m_s2": vmax_drag_decel,
        "current_drag_decel_at_vmax_over_abs_speed_rate": safe_ratio(vmax_drag_decel, abs(vmax_speed_rate)),
        "thrust_mean_at_vmax": float(vmax_row["thrust_mean"]),
        "required_total_thrust_over_max_at_vmax": float(vmax_row["current_required_total_thrust_over_max"]),
        "high_speed_sample_count": len(high_rows),
        "high_speed_time_s": len(high_rows) / SYNC_RATE_HZ,
        "held_high_speed_time_s": len(held_high_rows) / SYNC_RATE_HZ,
        "held_high_speed_fraction_of_high_speed": safe_ratio(len(held_high_rows), len(high_rows), min_denominator=1.0),
        "high_speed_thrust_mean_median": percentile((float(row["thrust_mean"]) for row in high_rows), 50.0),
        "high_speed_required_total_over_max_median": percentile(
            (float(row["current_required_total_thrust_over_max"]) for row in high_rows), 50.0
        ),
    }
    for metric, value in summary.items():
        unit = "count"
        if "thrust_mean" in metric:
            unit = "normalized command"
        elif metric.endswith("_m_s"):
            unit = "m/s"
        elif metric.endswith("_m_s2"):
            unit = "m/s^2"
        elif metric.endswith("_n"):
            unit = "N"
        elif metric.endswith("_s"):
            unit = "s"
        elif "fraction" in metric or metric.endswith("_over_max") or metric.endswith("_over_abs_speed_rate"):
            unit = "x"
        add_metric(
            rows,
            row_type="ratm_accel_drag_packet_window_summary",
            name=name,
            metric=metric,
            value=value,
            unit=unit,
            source_file=WINDOW_REFERENCE,
            evidence_role="high_speed_window_residual_summary",
            note="Observed speed-rate comes from central differences over the local 500 Hz RATM high-speed window.",
        )
    return summary


def add_decimated_sample_rows(rows: list[dict[str, object]], window_rows: list[dict[str, float | str]]) -> None:
    name_prefix = f"{window_rows[0]['archive']}:{window_rows[0]['flight_id']}:rank{window_rows[0]['window_rank']}"
    sample_indices: set[int] = set()
    for target_offset in SAMPLE_OFFSETS_S:
        sample_indices.add(
            min(
                range(len(window_rows)),
                key=lambda index: abs(float(window_rows[index]["offset_from_vmax_s"]) - target_offset),
            )
        )
    sample_indices.add(max(range(len(window_rows)), key=lambda index: float(window_rows[index]["speed_m_s"])))
    metrics = [
        ("offset_from_vmax_s", "s"),
        ("speed_m_s", "m/s"),
        ("speed_rate_m_s2", "m/s^2"),
        ("apparent_decel_m_s2", "m/s^2"),
        ("current_drag_force_n", "N"),
        ("current_drag_decel_m_s2", "m/s^2"),
        ("current_drag_decel_over_abs_speed_rate", "x"),
        ("current_drag_force_over_apparent_decel_force", "x"),
        ("current_required_total_thrust_over_max", "x"),
        ("thrust_mean", "normalized command"),
        ("channels_thrust_us", "us"),
        ("vbat_v", "V"),
        ("mocap_residual", "mocap residual"),
        ("is_vmax_sample", "boolean"),
    ]
    for index in sorted(sample_indices):
        sample = window_rows[index]
        name = f"{name_prefix}:offset{float(sample['offset_from_vmax_s']):+.3f}s"
        for metric, unit in metrics:
            add_metric(
                rows,
                row_type="ratm_accel_drag_packet_decimated_sample",
                name=name,
                metric=metric,
                value=sample[metric],
                unit=unit,
                source_file=WINDOW_REFERENCE,
                evidence_role="decimated_high_speed_window_sample",
                note="Decimated sample from existing 1 s RATM high-speed window; use for traceability, not fitting alone.",
            )


def add_distributions(rows: list[dict[str, object]], summaries: list[dict[str, float | str]]) -> None:
    distribution_metrics = [
        ("speed_max_m_s", "m/s"),
        ("current_drag_decel_at_vmax_m_s2", "m/s^2"),
        ("speed_rate_at_vmax_m_s2", "m/s^2"),
        ("current_drag_decel_at_vmax_over_abs_speed_rate", "x"),
        ("observed_decel_p95_m_s2", "m/s^2"),
        ("current_drag_decel_p95_over_observed_decel_p95", "x"),
        ("high_speed_time_s", "s"),
        ("held_high_speed_time_s", "s"),
        ("high_speed_thrust_mean_median", "normalized command"),
        ("high_speed_required_total_over_max_median", "x"),
    ]
    for metric, unit in distribution_metrics:
        values = [float(summary[metric]) for summary in summaries]
        for suffix, p in [("min", 0.0), ("p25", 25.0), ("median", 50.0), ("p75", 75.0), ("max", 100.0)]:
            add_metric(
                rows,
                row_type="ratm_accel_drag_packet_distribution",
                name="six_high_speed_window_distribution",
                metric=f"{metric}_{suffix}",
                value=percentile(values, p),
                unit=unit,
                source_file=OUTPUT,
                evidence_role="window_distribution",
                note="Distribution across the six fastest RATM high-speed windows extracted by the existing packet.",
            )


def add_global_summary(rows: list[dict[str, object]], summaries: list[dict[str, float | str]]) -> None:
    high_time = sum(float(summary["high_speed_time_s"]) for summary in summaries)
    held_time = sum(float(summary["held_high_speed_time_s"]) for summary in summaries)
    metrics = [
        ("window_count", len(summaries), "count"),
        ("global_speed_max_m_s", max(float(summary["speed_max_m_s"]) for summary in summaries), "m/s"),
        (
            "median_current_drag_decel_at_vmax_m_s2",
            percentile((float(summary["current_drag_decel_at_vmax_m_s2"]) for summary in summaries), 50.0),
            "m/s^2",
        ),
        (
            "median_abs_speed_rate_at_vmax_m_s2",
            percentile((abs(float(summary["speed_rate_at_vmax_m_s2"])) for summary in summaries), 50.0),
            "m/s^2",
        ),
        (
            "median_current_drag_decel_at_vmax_over_abs_speed_rate",
            percentile((float(summary["current_drag_decel_at_vmax_over_abs_speed_rate"]) for summary in summaries), 50.0),
            "x",
        ),
        (
            "median_observed_decel_p95_m_s2",
            percentile((float(summary["observed_decel_p95_m_s2"]) for summary in summaries), 50.0),
            "m/s^2",
        ),
        (
            "median_current_drag_decel_p95_over_observed_decel_p95",
            percentile((float(summary["current_drag_decel_p95_over_observed_decel_p95"]) for summary in summaries), 50.0),
            "x",
        ),
        ("total_time_above_21m_s_s", high_time, "s"),
        ("total_held_time_above_21m_s_abs_rate_under_5_s", held_time, "s"),
        ("held_high_speed_fraction", safe_ratio(held_time, high_time), "x"),
        (
            "median_high_speed_thrust_mean",
            percentile((float(summary["high_speed_thrust_mean_median"]) for summary in summaries), 50.0),
            "normalized command",
        ),
        (
            "median_high_speed_required_total_over_max",
            percentile((float(summary["high_speed_required_total_over_max_median"]) for summary in summaries), 50.0),
            "x",
        ),
    ]
    for metric, value, unit in metrics:
        add_metric(
            rows,
            row_type="ratm_accel_drag_packet_global_summary",
            name="RATM_high_speed_accel_drag_residual_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            evidence_role="handoff_summary",
            note="Use as a feasibility/residual screen; aggressive powered flight, attitude, thrust calibration, and wind are coupled.",
        )

    add_metric(
        rows,
        row_type="ratm_accel_drag_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Central-difference speed rates from RATM high-speed windows are compared with the current racingQuad drag "
            "deceleration demand. This is not a wind-tunnel or passive coastdown drag fit: the windows are powered, "
            "aggressive maneuvers, and motor-thrust direction is not reconstructed here. Rows are intended to flag "
            "whether current drag magnitude is feasible beside observed high-speed kinematics."
        ),
        unit="text",
        source_file=OUTPUT,
        evidence_role="method",
    )


def build_packet() -> list[dict[str, object]]:
    current = current_reference()
    raw_windows: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in read_rows(WINDOW_REFERENCE):
        raw_windows[row["window_rank"]].append(row)

    packet: list[dict[str, object]] = []
    add_source_inventory(packet, current, raw_windows)
    summaries: list[dict[str, float | str]] = []
    for rank in sorted(raw_windows, key=lambda value: int(value)):
        derived = derived_rows_for_window(raw_windows[rank], current)
        summaries.append(add_window_summary(packet, derived))
        add_decimated_sample_rows(packet, derived)
    add_distributions(packet, summaries)
    add_global_summary(packet, summaries)
    return packet


def sync_summary(packet_rows: list[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("ratm_accel_drag_packet_")]
    added: list[dict[str, object]] = []
    for row in packet_rows:
        added.append(
            {
                "category": row["row_type"],
                "name": row["name"],
                "metric": row["metric"],
                "value": value_text(row["value"]),
                "unit": row["unit"],
                "source": row.get("source_url") or row.get("source_file", ""),
            }
        )
    write_csv(SUMMARY, kept + added)
    return len(added)


def main() -> None:
    packet_rows = build_packet()
    write_csv(OUTPUT, packet_rows)
    synced = sync_summary(packet_rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(packet_rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
