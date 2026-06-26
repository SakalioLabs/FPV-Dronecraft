"""Build a near-surface calibration packet for ground, ceiling, and wall effects.

Outputs:
  docs/data/surface_nearfield_calibration_packet.csv

The source CSVs already contain detailed scans. This packet keeps the tuning
surface compact: selected h/R rows for ground/ceiling effect, selected wall
clearances/obstruction values, and published sidewall anchors that distinguish
wall attraction/moment from total-thrust loss.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
OUTPUT = DATA / "surface_nearfield_calibration_packet.csv"

ZJU = DATA / "zju_ground_effect_model_reference.csv"
OBSTRUCTION = DATA / "surface_obstruction_geometry_reference.csv"
PROXIMITY = DATA / "surface_proximity_effect_reference.csv"

SELECTED_H_OVER_R = {0.5, 1.0, 2.0, 4.0, 6.0}
SELECTED_WALL_CLEARANCE_OVER_R = {0.0, 0.25, 0.5, 1.0, 2.0, 4.0, 6.0, 10.0}
SELECTED_OBSTRUCTION = {0.25, 0.5, 0.75, 1.0}
SELECTED_TRANSVERSE_SPEED = {0.0, 12.0}


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def try_float(value: str) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return math.nan


def selected(value: str, choices: set[float], tol: float = 1e-9) -> bool:
    parsed = try_float(value)
    return math.isfinite(parsed) and any(abs(parsed - choice) <= tol for choice in choices)


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
    source_file: Path,
    source_url: str = "",
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
            "note": note,
        }
    )


def add_ground_reference(rows: list[dict[str, str]], proximity_rows: list[dict[str, str]]) -> None:
    for row in proximity_rows:
        if row.get("row_type") != "reference_ground_curve" or not selected(row.get("h_over_r", ""), SELECTED_H_OVER_R):
            continue
        name = f"h_over_r_{row['h_over_r']}"
        for metric, unit in [
            ("cheeseman_bennett_ground_boost", "multiplier"),
            ("kan_aau_hover_boost_proxy", "multiplier"),
        ]:
            add_metric(
                rows,
                row_type="surface_nearfield_ground_reference_curve",
                name=name,
                metric=metric,
                value=try_float(row[metric]),
                unit=unit,
                source_file=PROXIMITY,
                source_url=row.get("source", ""),
                note="Reference curve for expected hover ground-effect order of magnitude; not a direct runtime formula.",
            )


def add_zju_ground(rows: list[dict[str, str]], zju_rows: list[dict[str, str]]) -> None:
    for row in zju_rows:
        if row.get("row_type") != "current_vs_zju_ground_thrust" or not selected(row.get("h_over_r", ""), SELECTED_H_OVER_R):
            continue
        name = f"{row['preset']}_h_over_r_{row['h_over_r']}"
        for metric, unit in [
            ("current_thrust_multiplier", "multiplier"),
            ("zju_formula_thrust_multiplier", "multiplier"),
            ("current_extra_thrust_fraction", "fraction"),
            ("zju_extra_thrust_fraction", "fraction"),
            ("current_extra_over_zju_extra", "ratio"),
        ]:
            add_metric(
                rows,
                row_type="surface_nearfield_zju_ground_check",
                name=name,
                metric=metric,
                value=try_float(row[metric]),
                unit=unit,
                source_file=ZJU,
                source_url=row.get("source", ""),
                note="Current ground-effect extra thrust compared with ZJU formula-level ground-effect model.",
            )

    for row in zju_rows:
        if row.get("row_type") == "zju_rotor_drag_low_altitude_observation":
            for metric, unit in [
                ("low_height_m", "m"),
                ("high_height_m", "m"),
                ("predicted_drag_ratio_from_sqrt_thrust", "ratio"),
                ("measured_drag_x_low_over_high", "ratio"),
                ("measured_drag_y_low_over_high", "ratio"),
                ("measured_x_over_predicted", "ratio"),
                ("measured_y_over_predicted", "ratio"),
            ]:
                add_metric(
                    rows,
                    row_type="surface_nearfield_zju_drag_observation",
                    name="zju_low_altitude_rotor_drag",
                    metric=metric,
                    value=try_float(row[metric]),
                    unit=unit,
                    source_file=ZJU,
                    source_url=row.get("source", ""),
                    note="ZJU reports low-altitude rotor drag falls more than sqrt(thrust) scaling predicts.",
                )
        elif row.get("row_type") == "zju_mixing_matrix_ground_sensitivity":
            for metric, unit in [
                ("thrust_coefficient_change_percent_approx", "percent"),
                ("torque_coefficient_change_percent_upper_bound", "percent"),
            ]:
                add_metric(
                    rows,
                    row_type="surface_nearfield_zju_mixing_sensitivity",
                    name="zju_mixing_matrix_ground_sensitivity",
                    metric=metric,
                    value=try_float(row[metric]),
                    unit=unit,
                    source_file=ZJU,
                    source_url=row.get("source", ""),
                    note="ZJU reports thrust coefficient changes dominate torque coefficient changes near ground.",
                )


def add_current_ground_ceiling(rows: list[dict[str, str]], proximity_rows: list[dict[str, str]]) -> None:
    for row in proximity_rows:
        if row.get("row_type") != "current_ground_ceiling_scan" or not selected(row.get("h_over_r", ""), SELECTED_H_OVER_R):
            continue
        name = f"{row['preset']}_h_over_r_{row['h_over_r']}"
        for metric, unit in [
            ("current_ground_multiplier", "multiplier"),
            ("current_ceiling_multiplier", "multiplier"),
            ("ground_multiplier_over_cheeseman", "ratio"),
            ("ceiling_multiplier_over_ground_multiplier", "ratio"),
        ]:
            add_metric(
                rows,
                row_type="surface_nearfield_current_ground_ceiling",
                name=name,
                metric=metric,
                value=try_float(row[metric]),
                unit=unit,
                source_file=PROXIMITY,
                source_url=row.get("source", ""),
                note="Current Java ground/ceiling scan at selected normalized clearances.",
            )


def add_wall_mapping(rows: list[dict[str, str]], obstruction_rows: list[dict[str, str]]) -> None:
    for row in obstruction_rows:
        if row.get("row_type") != "current_flat_wall_runtime_mapping" or not selected(row.get("clearance_over_r", ""), SELECTED_WALL_CLEARANCE_OVER_R):
            continue
        name = f"{row['preset']}_clearance_over_r_{row['clearance_over_r']}"
        for metric, unit in [
            ("disk_segment_blocked_fraction", "fraction"),
            ("current_runtime_obstruction", "fraction"),
            ("current_runtime_thrust_multiplier_per_affected_rotor", "multiplier"),
            ("two_affected_rotors_vehicle_thrust_multiplier", "multiplier"),
            ("two_affected_rotors_wall_force_over_weight", "weight fraction"),
        ]:
            add_metric(
                rows,
                row_type="surface_nearfield_wall_runtime_mapping",
                name=name,
                metric=metric,
                value=try_float(row[metric]),
                unit=unit,
                source_file=OBSTRUCTION,
                source_url=row.get("source_url", ""),
                note="Current flat-wall geometry-to-obstruction mapping; disk overlap is an ideal geometry reference, not the runtime formula.",
            )

    for row in obstruction_rows:
        if row.get("row_type") != "current_offline_wall_skim_closest_rotor":
            continue
        name = f"{row['preset']}_offline_wall_skim_closest_rotor"
        for metric, unit in [
            ("current_offline_wall_skim_geometric_obstruction", "fraction"),
            ("current_offline_wall_skim_local_obstacle_residual", "multiplier"),
            ("current_offline_wall_skim_residual_obstruction", "fraction"),
            ("current_offline_wall_skim_a4mc_shelter_obstruction", "fraction"),
            ("current_offline_wall_skim_obstruction", "fraction"),
            ("current_offline_wall_skim_thrust_multiplier_per_affected_rotor", "multiplier"),
        ]:
            add_metric(
                rows,
                row_type="surface_nearfield_offline_a4mc_wall_skim",
                name=name,
                metric=metric,
                value=try_float(row[metric]),
                unit=unit,
                source_file=OBSTRUCTION,
                source_url=row.get("source_url", ""),
                note="Offline wall_skim synthetic A4MC L2 profile decomposes duplicated geometry residual and shelter-gradient obstruction.",
            )

    for row in obstruction_rows:
        if row.get("row_type") != "published_wall_effect_anchor":
            continue
        add_metric(
            rows,
            row_type="surface_nearfield_published_wall_anchor",
            name=row.get("published_metric", ""),
            metric="published_value",
            value=row.get("published_value", ""),
            unit=row.get("published_units", ""),
            source_file=OBSTRUCTION,
            source_url=row.get("source_url", ""),
            note=row.get("note", ""),
        )


def add_wall_force_scan(rows: list[dict[str, str]], proximity_rows: list[dict[str, str]]) -> None:
    for row in proximity_rows:
        if row.get("row_type") != "current_wall_force_scan":
            continue
        if not selected(row.get("obstruction", ""), SELECTED_OBSTRUCTION):
            continue
        if not selected(row.get("transverse_speed_m_s", ""), SELECTED_TRANSVERSE_SPEED):
            continue
        name = f"{row['preset']}_obstruction_{row['obstruction']}_speed_{row['transverse_speed_m_s']}"
        for metric, unit in [
            ("wall_force_per_rotor_n", "N"),
            ("two_rotor_wall_force_over_weight", "weight fraction"),
            ("four_rotor_wall_force_over_weight", "weight fraction"),
            ("speed_washout", "multiplier"),
            ("wall_cushion", "fraction"),
        ]:
            add_metric(
                rows,
                row_type="surface_nearfield_current_wall_force",
                name=name,
                metric=metric,
                value=try_float(row[metric]),
                unit=unit,
                source_file=PROXIMITY,
                source_url=row.get("source", ""),
                note="Current wall-force scan; speed washout shows how lateral speed weakens the hover wall-attraction proxy.",
            )


def find_metric(rows: list[dict[str, str]], row_type: str, name: str, metric: str) -> float:
    for row in rows:
        if row["row_type"] == row_type and row["name"] == name and row["metric"] == metric:
            return try_float(row["value"])
    return math.nan


def add_summary(rows: list[dict[str, str]]) -> None:
    rq_h1_current = find_metric(rows, "surface_nearfield_zju_ground_check", "racingQuad_h_over_r_1.0", "current_thrust_multiplier")
    rq_h1_zju = find_metric(rows, "surface_nearfield_zju_ground_check", "racingQuad_h_over_r_1.0", "zju_formula_thrust_multiplier")
    rq_h1_extra_ratio = find_metric(rows, "surface_nearfield_zju_ground_check", "racingQuad_h_over_r_1.0", "current_extra_over_zju_extra")
    rq_h1_cb_ratio = find_metric(rows, "surface_nearfield_current_ground_ceiling", "racingQuad_h_over_r_1.0", "ground_multiplier_over_cheeseman")
    rq_wall_025_vehicle = find_metric(rows, "surface_nearfield_wall_runtime_mapping", "racingQuad_clearance_over_r_0.25", "two_affected_rotors_vehicle_thrust_multiplier")
    rq_wall_025_force = find_metric(rows, "surface_nearfield_wall_runtime_mapping", "racingQuad_clearance_over_r_0.25", "two_affected_rotors_wall_force_over_weight")
    rq_wall_1_vehicle = find_metric(rows, "surface_nearfield_wall_runtime_mapping", "racingQuad_clearance_over_r_1.0", "two_affected_rotors_vehicle_thrust_multiplier")
    rq_full_obstruction_force = find_metric(rows, "surface_nearfield_current_wall_force", "racingQuad_obstruction_1.0_speed_0.0", "two_rotor_wall_force_over_weight")
    rq_offline_wall_skim_geometry = find_metric(rows, "surface_nearfield_offline_a4mc_wall_skim", "racingQuad_offline_wall_skim_closest_rotor", "current_offline_wall_skim_geometric_obstruction")
    rq_offline_wall_skim_combined = find_metric(rows, "surface_nearfield_offline_a4mc_wall_skim", "racingQuad_offline_wall_skim_closest_rotor", "current_offline_wall_skim_obstruction")
    rq_offline_wall_skim_ratio = rq_offline_wall_skim_combined / rq_offline_wall_skim_geometry if rq_offline_wall_skim_geometry > 1.0e-9 else math.nan

    summary = {
        "racingQuad_hR1_current_ground_multiplier": (rq_h1_current, "multiplier"),
        "racingQuad_hR1_zju_ground_multiplier": (rq_h1_zju, "multiplier"),
        "racingQuad_hR1_current_extra_over_zju_extra": (rq_h1_extra_ratio, "ratio"),
        "racingQuad_hR1_ground_over_cheeseman": (rq_h1_cb_ratio, "ratio"),
        "racingQuad_wall_0p25R_two_rotor_vehicle_thrust_loss": (1.0 - rq_wall_025_vehicle, "fraction"),
        "racingQuad_wall_0p25R_two_rotor_wall_force_over_weight": (rq_wall_025_force, "weight fraction"),
        "racingQuad_wall_1R_two_rotor_vehicle_thrust_loss": (1.0 - rq_wall_1_vehicle, "fraction"),
        "racingQuad_full_obstruction_two_rotor_wall_force_over_weight": (rq_full_obstruction_force, "weight fraction"),
        "racingQuad_offline_a4mc_wall_skim_combined_obstruction": (rq_offline_wall_skim_combined, "fraction"),
        "racingQuad_offline_a4mc_wall_skim_combined_over_geometry": (rq_offline_wall_skim_ratio, "ratio"),
    }
    for metric, (value, unit) in summary.items():
        add_metric(
            rows,
            row_type="surface_nearfield_packet_summary",
            name="surface_nearfield_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            note="Compact handoff summary for near-surface tuning.",
        )

    add_metric(
        rows,
        row_type="surface_nearfield_packet_method",
        name="method",
        metric="scope_and_caveat",
        value="ground/ceiling/wall packet from existing detailed scans; wall anchors indicate sidewall total-thrust changes are small while force/moment toward the wall can matter",
        unit="text",
        source_file=OUTPUT,
        note="Use ground-effect rows for thrust multiplier tuning and wall rows for attraction/moment/dirty-air tuning; avoid treating sidewall proximity as a large clean total-thrust loss without stronger data.",
    )


def build_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    zju_rows = read_rows(ZJU)
    obstruction_rows = read_rows(OBSTRUCTION)
    proximity_rows = read_rows(PROXIMITY)
    add_ground_reference(rows, proximity_rows)
    add_zju_ground(rows, zju_rows)
    add_current_ground_ceiling(rows, proximity_rows)
    add_wall_mapping(rows, obstruction_rows)
    add_wall_force_scan(rows, proximity_rows)
    add_summary(rows)
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
