"""Condense APdrone open-field drag evidence into a narrow calibration packet.

Inputs:
  docs/data/apdrone_drag_speed_envelope_reference.csv
  docs/data/apdrone_open_field_speed_dynamics_reference.csv
  docs/data/apdrone_open_field_trim_candidate_reference.csv

Output:
  docs/data/apdrone_drag_calibration_packet.csv
"""

from __future__ import annotations

import csv
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
OUT = DATA / "apdrone_drag_calibration_packet.csv"
APDRONE_SOURCE_PAGE = "https://data.mendeley.com/datasets/zgsvdtxnfh/2"
APDRONE_DOI = "10.17632/zgsvdtxnfh.2"


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def write_rows(rows: list[dict[str, object]]) -> None:
    fieldnames: list[str] = []
    for row in rows:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def f(value: str | float | int) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return float("nan")


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source: str,
    note: str = "",
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": value,
            "unit": unit,
            "source": source,
            "doi": APDRONE_DOI if source == APDRONE_SOURCE_PAGE else "",
            "note": note,
        }
    )


def main() -> None:
    envelope = read_rows(DATA / "apdrone_drag_speed_envelope_reference.csv")
    dynamics = read_rows(DATA / "apdrone_open_field_speed_dynamics_reference.csv")
    trim = read_rows(DATA / "apdrone_open_field_trim_candidate_reference.csv")

    rows: list[dict[str, object]] = []
    add_metric(
        rows,
        row_type="apdrone_drag_packet_method",
        name="method",
        metric="source_tables",
        value="apdrone_drag_speed_envelope_reference.csv; apdrone_open_field_speed_dynamics_reference.csv; apdrone_open_field_trim_candidate_reference.csv",
        unit="",
        source=APDRONE_SOURCE_PAGE,
        note="APdrone open-field GPS rows lack attitude and wind; this packet is a drag sanity/envelope table, not a final CdA fit.",
    )

    for row in envelope:
        if row.get("row_type") == "project_preset_drag_model":
            name = row["preset"]
            for metric, unit in (
                ("mass_kg", "kg"),
                ("max_total_thrust_n", "N"),
                ("level_horizontal_thrust_margin_n", "N"),
                ("total_drag_x_n_per_mps2", "N/(m/s)^2"),
                ("total_drag_z_n_per_mps2", "N/(m/s)^2"),
                ("equivalent_cda_x_m2", "m^2"),
                ("equivalent_cda_z_m2", "m^2"),
                ("drag_limited_level_speed_x_m_s", "m/s"),
                ("drag_limited_level_speed_z_m_s", "m/s"),
            ):
                add_metric(
                    rows,
                    row_type="apdrone_drag_project_model",
                    name=name,
                    metric=metric,
                    value=row.get(metric, ""),
                    unit=unit,
                    source=row.get("source_page", ""),
                    note="Current project quadratic drag model parsed by APdrone Mendeley analysis.",
                )

    for row in envelope:
        if row.get("row_type") == "apdrone_speed_reference_point":
            add_metric(
                rows,
                row_type="apdrone_drag_speed_reference_point",
                name=row["speed_point"],
                metric="speed_m_s",
                value=row.get("speed_m_s", ""),
                unit="m/s",
                source=row.get("source_page", APDRONE_SOURCE_PAGE),
                note=row.get("note", ""),
            )

    for row in envelope:
        if row.get("row_type") != "apdrone_drag_speed_constraint":
            continue
        if row.get("preset") != "apDrone":
            continue
        if row.get("axis") != "x":
            continue
        speed_point = row.get("speed_point", "")
        if speed_point not in {
            "selected_flight_gps_speed_max",
            "open_field_max_gps_speed_across_files",
            "open_field_p95_gps_speed_across_files",
            "open_field_mean_gps_speed_across_files",
        }:
            continue
        name = f"apDrone_x_{speed_point}"
        for metric, unit in (
            ("speed_m_s", "m/s"),
            ("total_drag_coefficient_n_per_mps2", "N/(m/s)^2"),
            ("required_drag_force_n", "N"),
            ("drag_over_level_margin", "fraction"),
            ("max_allowable_drag_coefficient_n_per_mps2", "N/(m/s)^2"),
            ("coefficient_over_max_allowable", "x"),
            ("drag_limited_level_speed_m_s", "m/s"),
            ("speed_over_drag_limited", "x"),
        ):
            add_metric(
                rows,
                row_type="apdrone_drag_speed_constraint_apdrone_x",
                name=name,
                metric=metric,
                value=row.get(metric, ""),
                unit=unit,
                source=row.get("speed_source_page") or APDRONE_SOURCE_PAGE,
                note="Full-thrust level-flight upper-bound check using GPS ground speed as airspeed proxy.",
            )

    for row in dynamics:
        if row.get("row_type") != "apdrone_open_field_gps_dynamic_speed_bin_summary":
            continue
        name = f"speed_bin_{row['speed_bin_m_s']}"
        for metric, unit in (
            ("event_count", "count"),
            ("flight_count", "count"),
            ("speed_mean_m_s", "m/s"),
            ("speed_p50_m_s", "m/s"),
            ("speed_p90_m_s", "m/s"),
            ("speed_max_m_s", "m/s"),
            ("along_track_accel_p50_m_s2", "m/s^2"),
            ("along_track_accel_p90_m_s2", "m/s^2"),
            ("accelerating_fraction", "fraction"),
            ("quasi_steady_fraction", "fraction"),
            ("decelerating_fraction", "fraction"),
            ("throttle_mean", "%"),
            ("apparent_decel_coeff_count", "count"),
            ("apparent_decel_coeff_p50", "N/(m/s)^2"),
            ("apparent_decel_coeff_p50_over_apDrone_x", "x"),
            ("apparent_decel_coeff_p50_over_racingQuad_x", "x"),
        ):
            add_metric(
                rows,
                row_type="apdrone_drag_gps_dynamic_speed_bin",
                name=name,
                metric=metric,
                value=row.get(metric, ""),
                unit=unit,
                source=row.get("source_page", APDRONE_SOURCE_PAGE),
                note="GPS speed/course dynamics; apparent decel coefficient assumes zero horizontal thrust and is diagnostic only.",
            )

    for row in trim:
        if row.get("row_type") != "apdrone_open_field_trim_filter_summary":
            continue
        name = row["filter_name"]
        for metric, unit in (
            ("event_count", "count"),
            ("source_file_count", "count"),
            ("min_speed_m_s", "m/s"),
            ("speed_p50_m_s", "m/s"),
            ("speed_p90_m_s", "m/s"),
            ("speed_max_m_s", "m/s"),
            ("throttle_p50", "%"),
            ("along_accel_p50_m_s2", "m/s^2"),
            ("abs_cross_accel_p50_m_s2", "m/s^2"),
            ("abs_turn_rate_p50_deg_s", "deg/s"),
            ("decelerating_event_count", "count"),
            ("decel_only_drag_coeff_p50", "N/(m/s)^2"),
            ("decel_only_coeff_p50_over_apDrone_x", "x"),
            ("decel_only_coeff_p50_over_racingQuad_x", "x"),
        ):
            add_metric(
                rows,
                row_type="apdrone_drag_trim_filter_summary",
                name=name,
                metric=metric,
                value=row.get(metric, ""),
                unit=unit,
                source=row.get("source_page", APDRONE_SOURCE_PAGE),
                note=row.get("note", ""),
            )

    write_rows(rows)
    print(f"Wrote {OUT.relative_to(ROOT).as_posix()} with {len(rows)} rows")


if __name__ == "__main__":
    main()
