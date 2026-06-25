#!/usr/bin/env python3
"""Encode a public Mejzlik/AirShaper propeller wind-tunnel table.

Outputs:
  docs/data/mejzlik_wind_tunnel_prop_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category mejzlik_prop_packet_*

The source page publishes CT, CP, and efficiency at J=0.2..0.8 for a Mejzlik
propeller, including wind-tunnel values and AirShaper Basic/Regular simulation
comparisons. This packet is a high-J axial-flow sanity anchor; it is not a
direct 5-inch FPV edgewise retreating-blade-stall calibration.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
OUTPUT = DATA / "mejzlik_wind_tunnel_prop_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

SOURCE_URL = "https://www.airshaper.com/research/propeller-study"

WIND_TUNNEL_RPM = 4900.0
WIND_TUNNEL_SPEED_MIN_M_S = 0.0
WIND_TUNNEL_SPEED_MAX_M_S = 35.0
CFD_MAX_SPEED_M_S = 60.0
CFD_MAX_J = 1.38

CURRENT_LIFT_DISSYMMETRY_MU_END = 0.34
CURRENT_RETREATING_STALL_MU_START = 0.42
CURRENT_HIGH_ADVANCE_LOSS_MU_START = 0.46

RACING_MASS_KG = 1.1
RACING_ROTOR_K_N_PER_RAD2 = 1.45e-6
RACING_ROTOR_DIAMETER_M = 0.127
G = 9.80665


TABLE = [
    {
        "j": 0.2,
        "ct": {"mejzlik": 0.1003, "airshaper_basic": 0.1136, "airshaper_regular": 0.0990, "wind_tunnel": 0.0918},
        "cp": {"mejzlik": 0.0441, "airshaper_basic": 0.0492, "airshaper_regular": 0.0444, "wind_tunnel": 0.0417},
        "eta": {"mejzlik": 0.4546, "airshaper_basic": 0.4612, "airshaper_regular": 0.4455, "wind_tunnel": 0.4452},
    },
    {
        "j": 0.4,
        "ct": {"mejzlik": 0.0782, "airshaper_basic": 0.0908, "airshaper_regular": 0.0733, "wind_tunnel": 0.0711},
        "cp": {"mejzlik": 0.0437, "airshaper_basic": 0.0495, "airshaper_regular": 0.0426, "wind_tunnel": 0.0417},
        "eta": {"mejzlik": 0.7161, "airshaper_basic": 0.7334, "airshaper_regular": 0.6887, "wind_tunnel": 0.6810},
    },
    {
        "j": 0.6,
        "ct": {"mejzlik": 0.0457, "airshaper_basic": 0.0567, "airshaper_regular": 0.0411, "wind_tunnel": 0.0411},
        "cp": {"mejzlik": 0.0333, "airshaper_basic": 0.0390, "airshaper_regular": 0.0306, "wind_tunnel": 0.0321},
        "eta": {"mejzlik": 0.8242, "airshaper_basic": 0.8721, "airshaper_regular": 0.8054, "wind_tunnel": 0.7745},
    },
    {
        "j": 0.8,
        "ct": {"mejzlik": -0.0027, "airshaper_basic": 0.0075, "airshaper_regular": -0.0025, "wind_tunnel": -0.0035},
        "cp": {"mejzlik": 0.0095, "airshaper_basic": 0.0168, "airshaper_regular": 0.0064, "wind_tunnel": 0.0081},
        "eta": {"mejzlik": -0.2267, "airshaper_basic": 0.3540, "airshaper_regular": -0.3136, "wind_tunnel": 0.2132},
    },
]


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
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        if not math.isfinite(value):
            return ""
        return f"{value:.12g}"
    return str(value)


def add_metric(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    evidence_role: str,
    note: str,
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": value_text(value),
            "unit": unit,
            "source_file": repo_path(OUTPUT),
            "source_url": SOURCE_URL,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def wind_tunnel_rows() -> list[dict[str, object]]:
    return [
        {
            "j": entry["j"],
            "ct": entry["ct"]["wind_tunnel"],
            "cp": entry["cp"]["wind_tunnel"],
            "eta": entry["eta"]["wind_tunnel"],
        }
        for entry in TABLE
    ]


def zero_crossing_j() -> float:
    rows = wind_tunnel_rows()
    for low, high in zip(rows, rows[1:]):
        if float(low["ct"]) >= 0.0 and float(high["ct"]) <= 0.0:
            low_j = float(low["j"])
            high_j = float(high["j"])
            low_ct = float(low["ct"])
            high_ct = float(high["ct"])
            return low_j + (0.0 - low_ct) * (high_j - low_j) / (high_ct - low_ct)
    return math.nan


def racing_hover_speed_for_j(j: float) -> float:
    hover_thrust_per_rotor = RACING_MASS_KG * G / 4.0
    hover_omega = math.sqrt(hover_thrust_per_rotor / RACING_ROTOR_K_N_PER_RAD2)
    hover_n_hz = hover_omega / (2.0 * math.pi)
    return j * hover_n_hz * RACING_ROTOR_DIAMETER_M


def build_packet() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    source_metrics = [
        ("wind_tunnel_rpm", WIND_TUNNEL_RPM, "rpm"),
        ("wind_tunnel_speed_min_m_s", WIND_TUNNEL_SPEED_MIN_M_S, "m/s"),
        ("wind_tunnel_speed_max_m_s", WIND_TUNNEL_SPEED_MAX_M_S, "m/s"),
        ("cfd_max_speed_m_s", CFD_MAX_SPEED_M_S, "m/s"),
        ("cfd_max_advance_ratio_j", CFD_MAX_J, "J"),
        ("published_table_j_count", len(TABLE), "count"),
    ]
    for metric, value, unit in source_metrics:
        add_metric(
            rows,
            row_type="mejzlik_prop_packet_source_inventory",
            name="airshaper_mejzlik_propeller_study",
            metric=metric,
            value=value,
            unit=unit,
            evidence_role="wind_tunnel_and_cfd_source_inventory",
            note="Source page publishes Mejzlik propeller wind-tunnel data and AirShaper CFD/BEM comparisons.",
        )

    for entry in TABLE:
        j = float(entry["j"])
        for quantity, unit in [("ct", "CT"), ("cp", "CP"), ("eta", "efficiency")]:
            for model, value in entry[quantity].items():
                add_metric(
                    rows,
                    row_type="mejzlik_prop_packet_table_value",
                    name=f"{model}_j_{j:.1f}",
                    metric=quantity,
                    value=value,
                    unit=unit,
                    evidence_role="published_table_value",
                    note="Published table value from the AirShaper Mejzlik propeller study.",
                )
        add_metric(
            rows,
            row_type="mejzlik_prop_packet_table_value",
            name=f"wind_tunnel_j_{j:.1f}",
            metric="code_mu_equivalent_j_over_pi",
            value=j / math.pi,
            unit="mu",
            evidence_role="unit_mapping",
            note="Project rotorAdvanceRatio mu equals published propeller J divided by pi.",
        )

        wind_ct = float(entry["ct"]["wind_tunnel"])
        wind_cp = float(entry["cp"]["wind_tunnel"])
        wind_eta = float(entry["eta"]["wind_tunnel"])
        for model in ["mejzlik", "airshaper_basic", "airshaper_regular"]:
            model_ct = float(entry["ct"][model])
            model_cp = float(entry["cp"][model])
            model_eta = float(entry["eta"][model])
            comparisons = [
                ("ct_over_wind_tunnel", model_ct / wind_ct if wind_ct else math.nan, "x"),
                ("cp_over_wind_tunnel", model_cp / wind_cp if wind_cp else math.nan, "x"),
                ("eta_minus_wind_tunnel", model_eta - wind_eta, "efficiency"),
                ("ct_abs_error", abs(model_ct - wind_ct), "CT"),
                ("cp_abs_error", abs(model_cp - wind_cp), "CP"),
            ]
            for metric, value, unit in comparisons:
                add_metric(
                    rows,
                    row_type="mejzlik_prop_packet_model_vs_tunnel",
                    name=f"{model}_vs_wind_tunnel_j_{j:.1f}",
                    metric=metric,
                    value=value,
                    unit=unit,
                    evidence_role="model_vs_wind_tunnel_comparison",
                    note="Comparison is against the source page wind-tunnel table; near-zero CT makes ratios unstable.",
                )

    j_zero = zero_crossing_j()
    mu_zero = j_zero / math.pi
    thresholds = [
        ("ct_zero_crossing_j_linear_0p6_to_0p8", j_zero, "J"),
        ("ct_zero_crossing_mu_equivalent", mu_zero, "mu"),
        ("racing_hover_speed_at_ct_zero_m_s", racing_hover_speed_for_j(j_zero), "m/s"),
        ("current_lift_dissymmetry_end_j", math.pi * CURRENT_LIFT_DISSYMMETRY_MU_END, "J"),
        ("current_retreating_stall_start_j", math.pi * CURRENT_RETREATING_STALL_MU_START, "J"),
        ("current_high_advance_loss_start_j", math.pi * CURRENT_HIGH_ADVANCE_LOSS_MU_START, "J"),
        (
            "current_lift_dissymmetry_end_over_ct_zero_j",
            (math.pi * CURRENT_LIFT_DISSYMMETRY_MU_END) / j_zero,
            "x",
        ),
        (
            "current_retreating_stall_start_over_ct_zero_j",
            (math.pi * CURRENT_RETREATING_STALL_MU_START) / j_zero,
            "x",
        ),
        (
            "current_high_advance_loss_start_over_ct_zero_j",
            (math.pi * CURRENT_HIGH_ADVANCE_LOSS_MU_START) / j_zero,
            "x",
        ),
        (
            "racing_hover_speed_at_current_high_advance_loss_start_m_s",
            racing_hover_speed_for_j(math.pi * CURRENT_HIGH_ADVANCE_LOSS_MU_START),
            "m/s",
        ),
    ]
    for metric, value, unit in thresholds:
        add_metric(
            rows,
            row_type="mejzlik_prop_packet_summary",
            name="wind_tunnel_ct_zero_boundary",
            metric=metric,
            value=value,
            unit=unit,
            evidence_role="high_j_windmilling_boundary",
            note="Use as an axial-prop high-J/windmilling sanity anchor, not as direct edgewise retreating-blade-stall calibration.",
        )

    add_metric(
        rows,
        row_type="mejzlik_prop_packet_method",
        name="scope_caveat",
        metric="recommended_use",
        value=(
            "Use the wind-tunnel CT/CP table as a measured axial high-J sanity check. "
            "Keep it separate from UIUC low-J 5-inch data, APC prediction rows, and NASA edgewise high-mu rotor data."
        ),
        unit="text",
        evidence_role="method_caveat",
        note="The Mejzlik propeller and wind-tunnel setup are not the current 5-inch FPV quad prop/airframe.",
    )
    return rows


def sync_summary(packet_rows: Iterable[dict[str, str]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("mejzlik_prop_packet_")]
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
    packet_rows = build_packet()
    write_csv(OUTPUT, packet_rows)
    synced = sync_summary(packet_rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(packet_rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
