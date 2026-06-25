"""Build a compact VRS/propwash calibration packet.

Outputs:
  docs/data/vrs_propwash_calibration_packet.csv

The detailed VRS CSV already mirrors the current Java scan and carries
low-precision Shetty/Selig figure digitization. This packet keeps the handoff
surface narrow and explicit: regime anchors, digitized fluctuation envelopes,
current scan rows, current-vs-reference comparisons, and summary warnings.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
SOURCE = DATA / "vrs_propwash_reference.csv"
OUTPUT = DATA / "vrs_propwash_calibration_packet.csv"


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def f(row: dict[str, str], key: str) -> float:
    value = row.get(key, "")
    if value == "":
        return math.nan
    return float(value)


def value_text(value: str | float) -> str:
    if isinstance(value, str):
        return value
    if not math.isfinite(value):
        return ""
    return f"{value:.12g}"


def add_metric(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: str | float,
    unit: str,
    source_file: Path = SOURCE,
    source_url: str = "",
    source_row_type: str = "",
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
            "source_row_type": source_row_type,
            "note": note,
        }
    )


def add_reference_rows(rows: list[dict[str, str]], source_rows: list[dict[str, str]]) -> None:
    reference_metrics = {
        "reference_vrs_peak_band": [
            ("descent_ratio_vi_low", "vi ratio"),
            ("descent_ratio_vi_high", "vi ratio"),
            ("max_reported_thrust_loss_fraction", "fraction"),
            ("rotor_diameter_in_low", "in"),
            ("rotor_diameter_in_high", "in"),
        ],
        "reference_vrs_broad_regime": [
            ("descent_ratio_vi_low", "vi ratio"),
            ("descent_ratio_vi_high", "vi ratio"),
        ],
        "reference_small_prop_vrs_dataset": [
            ("tested_prop_count", "count"),
            ("prop_diameter_in_low", "in"),
            ("prop_diameter_in_high", "in"),
            ("advance_ratio_low", "J"),
            ("advance_ratio_high", "J"),
            ("rotational_speed_rpm", "rpm"),
            ("wind_tunnel_speed_low_m_s", "m/s"),
            ("wind_tunnel_speed_high_m_s", "m/s"),
            ("time_history_duration_s", "s"),
            ("time_history_sample_rate_hz", "Hz"),
            ("time_history_lpf_hz", "Hz"),
            ("max_thrust_fluctuation_half_amplitude_fraction", "fraction"),
        ],
        "reference_multirotor_descent_wind_tunnel": [
            ("vertical_descent_speed_m_s", "m/s"),
            ("net_thrust_loss_fraction_low", "fraction"),
            ("net_thrust_loss_fraction_high", "fraction"),
            ("windmilling_speed_m_s", "m/s"),
            ("windmilling_rotor_speed_rpm", "rpm"),
        ],
        "reference_downwash_formula": [
            ("prop_radius_m", "m"),
            ("downwash_coeff_1", "coefficient"),
            ("downwash_coeff_2", "coefficient"),
            ("downwash_coeff_3", "coefficient"),
        ],
    }
    for row in source_rows:
        if row.get("row_type") not in reference_metrics:
            continue
        for metric, unit in reference_metrics[row["row_type"]]:
            add_metric(
                rows,
                row_type="vrs_packet_reference_anchor",
                name=row["name"],
                metric=metric,
                value=f(row, metric),
                unit=unit,
                source_url=row.get("source", ""),
                source_row_type=row["row_type"],
                note=row.get("note", ""),
            )


def add_shetty_digitization(rows: list[dict[str, str]], source_rows: list[dict[str, str]]) -> None:
    metrics = [
        ("advance_ratio_j", "J"),
        ("hover_ct_digitized", "CT"),
        ("mean_ct_digitized", "CT"),
        ("lower_measured_ct_digitized", "CT"),
        ("upper_measured_ct_digitized", "CT"),
        ("measured_half_amplitude_over_mean_fraction", "fraction"),
        ("descent_ratio_vi_proxy", "vi ratio"),
    ]
    for row in source_rows:
        if row.get("row_type") != "shetty_selig_fig11_13_manual_digitization":
            continue
        name = f"{row['prop']} {row['figure']} J={row['advance_ratio_j']}"
        for metric, unit in metrics:
            add_metric(
                rows,
                row_type="vrs_packet_shetty_digitized_envelope",
                name=name,
                metric=metric,
                value=f(row, metric),
                unit=unit,
                source_url=row.get("source", ""),
                source_row_type=row["row_type"],
                note="Low-precision manual digitization of Shetty/Selig Figs. 11-13 measured CT envelopes.",
            )


def add_current_scan(rows: list[dict[str, str]], source_rows: list[dict[str, str]]) -> None:
    metrics = [
        ("hover_induced_velocity_m_s", "m/s"),
        ("descent_ratio_vi", "vi ratio"),
        ("descent_speed_m_s", "m/s"),
        ("current_vrs_intensity_hover_spin_no_crossflow", "fraction"),
        ("current_vrs_entry_component", "fraction"),
        ("current_vrs_exit_component", "fraction"),
        ("current_vrs_base_thrust_loss_percent_hover_spin", "percent"),
        ("current_vrs_buffet_thrust_amplitude_percent_max_spin", "percent"),
        ("current_vrs_lateral_force_bound_percent_max_thrust", "percent"),
        ("current_propwash_descent_factor", "fraction"),
        ("propwash_max_torque_nm", "N*m"),
    ]
    for row in source_rows:
        if row.get("row_type") != "current_vrs_scan":
            continue
        name = f"{row['preset']}_descent_ratio_vi_{row['descent_ratio_vi']}"
        for metric, unit in metrics:
            add_metric(
                rows,
                row_type="vrs_packet_current_scan",
                name=name,
                metric=metric,
                value=f(row, metric),
                unit=unit,
                source_url=row.get("source", ""),
                source_row_type=row["row_type"],
                note="Current Java VRS/propwash scan mirrored from detailed CSV.",
            )


def add_current_vs_shetty(rows: list[dict[str, str]], source_rows: list[dict[str, str]]) -> None:
    metrics = [
        ("reference_advance_ratio_j", "J"),
        ("descent_ratio_vi_proxy", "vi ratio"),
        ("reference_measured_half_amplitude_fraction", "fraction"),
        ("current_vrs_buffet_half_amplitude_fraction_max_spin", "fraction"),
        ("current_buffet_over_reference_measured_half_amplitude", "ratio"),
        ("current_base_loss_over_cambridge_peak_loss_33pct", "ratio"),
    ]
    for row in source_rows:
        if row.get("row_type") != "current_vs_shetty_selig_digitized_fluctuation":
            continue
        name = f"{row['preset']} vs {row['reference_prop']} J={row['reference_advance_ratio_j']}"
        for metric, unit in metrics:
            add_metric(
                rows,
                row_type="vrs_packet_current_vs_shetty",
                name=name,
                metric=metric,
                value=f(row, metric),
                unit=unit,
                source_url=row.get("source", ""),
                source_row_type=row["row_type"],
                note="Current max-spin buffet envelope compared with low-precision Shetty/Selig measured CT envelope.",
            )


def find_source_row(source_rows: list[dict[str, str]], predicate) -> dict[str, str]:
    for row in source_rows:
        if predicate(row):
            return row
    raise LookupError("source row not found")


def add_summary(rows: list[dict[str, str]], source_rows: list[dict[str, str]]) -> None:
    reference_peak = find_source_row(source_rows, lambda r: r.get("row_type") == "reference_vrs_peak_band")
    shetty_rows = [r for r in source_rows if r.get("row_type") == "shetty_selig_fig11_13_manual_digitization"]
    max_shetty = max(shetty_rows, key=lambda r: f(r, "measured_half_amplitude_over_mean_fraction"))
    rq_peak = find_source_row(
        source_rows,
        lambda r: r.get("row_type") == "current_vrs_scan"
        and r.get("preset") == "racingQuad"
        and r.get("descent_ratio_vi") == "1.2",
    )
    comparisons = [r for r in source_rows if r.get("row_type") == "current_vs_shetty_selig_digitized_fluctuation"]
    rq_comparisons = [r for r in comparisons if r.get("preset") == "racingQuad"]
    max_rq_comparison = max(rq_comparisons, key=lambda r: f(r, "current_buffet_over_reference_measured_half_amplitude"))
    exact_max_shetty_rq = find_source_row(
        rq_comparisons,
        lambda r: r.get("reference_prop") == max_shetty.get("prop")
        and r.get("reference_advance_ratio_j") == max_shetty.get("advance_ratio_j"),
    )
    scan_rows = [r for r in source_rows if r.get("row_type") == "current_vrs_scan" and r.get("preset") == "racingQuad"]
    propwash_full_start = min(f(r, "descent_ratio_vi") for r in scan_rows if f(r, "current_propwash_descent_factor") >= 1.0)
    vrs_active_ratios = [f(r, "descent_ratio_vi") for r in scan_rows if f(r, "current_vrs_intensity_hover_spin_no_crossflow") > 0.0]

    summary = {
        "cambridge_peak_band_low_vi": (f(reference_peak, "descent_ratio_vi_low"), "vi ratio"),
        "cambridge_peak_band_high_vi": (f(reference_peak, "descent_ratio_vi_high"), "vi ratio"),
        "cambridge_peak_loss_fraction": (f(reference_peak, "max_reported_thrust_loss_fraction"), "fraction"),
        "shetty_max_digitized_half_amplitude_fraction": (f(max_shetty, "measured_half_amplitude_over_mean_fraction"), "fraction"),
        "shetty_max_digitized_descent_ratio_vi_proxy": (f(max_shetty, "descent_ratio_vi_proxy"), "vi ratio"),
        "racingQuad_peak_hover_spin_loss_percent": (f(rq_peak, "current_vrs_base_thrust_loss_percent_hover_spin"), "percent"),
        "racingQuad_peak_max_spin_buffet_half_amplitude_percent": (f(rq_peak, "current_vrs_buffet_thrust_amplitude_percent_max_spin"), "percent"),
        "racingQuad_buffet_over_largest_shetty_digitized": (f(exact_max_shetty_rq, "current_buffet_over_reference_measured_half_amplitude"), "ratio"),
        "racingQuad_max_current_vs_shetty_ratio": (f(max_rq_comparison, "current_buffet_over_reference_measured_half_amplitude"), "ratio"),
        "racingQuad_base_loss_over_cambridge_peak_at_best_shetty_match": (f(max_rq_comparison, "current_base_loss_over_cambridge_peak_loss_33pct"), "ratio"),
        "racingQuad_vrs_active_low_vi": (min(vrs_active_ratios), "vi ratio"),
        "racingQuad_vrs_active_high_vi": (max(vrs_active_ratios), "vi ratio"),
        "racingQuad_propwash_full_from_vi": (propwash_full_start, "vi ratio"),
        "racingQuad_propwash_max_torque_nm": (f(rq_peak, "propwash_max_torque_nm"), "N*m"),
    }
    for metric, (value, unit) in summary.items():
        add_metric(
            rows,
            row_type="vrs_packet_summary",
            name="vrs_propwash_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            note="Compact VRS/propwash handoff summary.",
        )

    add_metric(
        rows,
        row_type="vrs_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Keep VRS mean thrust loss, buffet half-amplitude, lateral disturbance, and propwash torque as separate "
            "calibration surfaces. Shetty/Selig digitization is low precision and provides envelope/bounds, not an RMS statistical fit."
        ),
        unit="text",
        source_file=OUTPUT,
        note="Do not tune propwash torque/noise only from mean thrust-loss anchors.",
    )


def build_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    source_rows = read_rows(SOURCE)
    add_reference_rows(rows, source_rows)
    add_shetty_digitization(rows, source_rows)
    add_current_scan(rows, source_rows)
    add_current_vs_shetty(rows, source_rows)
    add_summary(rows, source_rows)
    return rows


def write_csv(path: Path, rows: Iterable[dict[str, str]]) -> None:
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


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")


if __name__ == "__main__":
    main()
