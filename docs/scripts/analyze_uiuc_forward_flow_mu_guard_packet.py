"""Build a UIUC/APC/Mejzlik forward-flow J-vs-mu guard packet.

Outputs:
  docs/data/uiuc_forward_flow_mu_guard_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category
  uiuc_mu_guard_packet_*

The simulation code's rotor advance ratio is mu = V / (omega R), while
propeller data typically publish J = V / (nD). Because omega = 2*pi*n and
D = 2R, J = pi * mu. This packet makes that conversion explicit across the
measured UIUC 5-inch rows, current operating points, APC high-J axial rows, and
the measured AirShaper/Mejzlik wind-tunnel table.
"""

from __future__ import annotations

import csv
import math
import re
from collections import defaultdict
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

OUTPUT = DATA / "uiuc_forward_flow_mu_guard_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"
FORWARD_REFERENCE = DATA / "rotor_forward_flow_reference.csv"
HIGH_ADVANCE_PACKET = DATA / "high_advance_rotor_prop_packet.csv"
MEJZLIK_PACKET = DATA / "mejzlik_wind_tunnel_prop_packet.csv"

UIUC_URL = "https://m-selig.ae.illinois.edu/props/propDB.html"
APC_URL = "https://www.apcprop.com/technical-information/performance-data/"
MEJZLIK_URL = "https://www.airshaper.com/research/propeller-study"
JAVA_PHYSICS_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java"
JAVA_CONFIG_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneConfig.java"

UIUC_SELECTED_5IN_EXPERIMENTAL_J_MAX = 0.571

CURRENT_THRESHOLDS = [
    ("lift_dissymmetry_start", 0.08, "smoothStep lower edge for lift-dissymmetry effects"),
    ("lift_dissymmetry_full", 0.34, "smoothStep upper edge for lift-dissymmetry effects"),
    ("retreating_blade_stall_start", 0.42, "smoothStep lower edge for retreating-blade stall"),
    ("high_advance_loss_start", 0.46, "nearby high-advance loss start"),
    ("retreating_blade_stall_full", 0.82, "smoothStep upper edge for retreating-blade stall"),
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


def f(row: dict[str, str], key: str, default: float = math.nan) -> float:
    value = row.get(key, "")
    if value == "":
        return default
    return float(value)


def safe_ratio(numerator: float, denominator: float, min_denominator: float = 1.0e-12) -> float:
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
    source_file: Path,
    source_url: str = "",
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


def first_metric(rows: list[dict[str, str]], row_type: str, name: str, metric: str) -> float:
    for row in rows:
        if row.get("row_type") == row_type and row.get("name") == name and row.get("metric") == metric:
            return float(row["value"])
    raise LookupError(f"missing {row_type}/{name}/{metric}")


def grouped_packet_metrics(rows: list[dict[str, str]], row_type: str) -> dict[str, dict[str, dict[str, str]]]:
    grouped: dict[str, dict[str, dict[str, str]]] = defaultdict(dict)
    for row in rows:
        if row.get("row_type") == row_type:
            grouped[row["name"]][row["metric"]] = row
    return grouped


def add_formula_rows(rows: list[dict[str, object]], forward_rows: list[dict[str, str]]) -> None:
    uiuc_rows = [row for row in forward_rows if row.get("row_type") == "uiuc_5in_forward_fit"]
    current_rows = [row for row in forward_rows if row.get("row_type") == "current_preset_level_forward"]
    metrics = [
        ("published_propeller_advance_ratio_j", "V/(nD)", "text"),
        ("project_rotor_advance_ratio_mu", "V/(omega R)", "text"),
        ("conversion_j_equals_pi_mu", math.pi, "J/mu"),
        ("conversion_mu_equals_j_over_pi", 1.0 / math.pi, "mu/J"),
        ("pi_factor_error_if_j_is_used_as_mu", math.pi, "x"),
        ("uiuc_forward_fit_row_count", len(uiuc_rows), "count"),
        ("current_level_forward_row_count", len(current_rows), "count"),
        ("uiuc_selected_5in_experimental_j_max", UIUC_SELECTED_5IN_EXPERIMENTAL_J_MAX, "J"),
        ("uiuc_selected_5in_experimental_mu_equivalent_max", UIUC_SELECTED_5IN_EXPERIMENTAL_J_MAX / math.pi, "mu"),
    ]
    for metric, value, unit in metrics:
        add_metric(
            rows,
            row_type="uiuc_mu_guard_packet_source",
            name="J/mu conversion guard",
            metric=metric,
            value=value,
            unit=unit,
            source_file=FORWARD_REFERENCE,
            source_url=UIUC_URL,
            evidence_role="unit_mapping_guard",
            note="Propeller data use J = V/(nD); project rotorAdvanceRatio uses mu = V/(omega R), so J = pi*mu.",
        )


def add_uiuc_rows(rows: list[dict[str, object]], forward_rows: list[dict[str, str]]) -> None:
    for row in forward_rows:
        if row.get("row_type") not in {"uiuc_5in_forward_fit", "uiuc_5in_forward_mean"}:
            continue
        j = f(row, "uiuc_j")
        mu = f(row, "code_mu_equivalent")
        source_url = row.get("source", "")
        common_metrics: list[tuple[str, object, str]] = [
            ("published_j", j, "J"),
            ("code_mu_equivalent", mu, "mu"),
            ("recomputed_mu_j_over_pi", j / math.pi, "mu"),
            ("mu_delta_recomputed_minus_source", j / math.pi - mu, "mu"),
            ("j_if_mu_misread_as_published_j", math.pi * j, "J"),
            ("misread_j_as_mu_over_correct_mu", safe_ratio(j, mu), "x"),
        ]
        if row["row_type"] == "uiuc_5in_forward_fit":
            row_type = "uiuc_mu_guard_packet_uiuc_fit_point"
            common_metrics.extend(
                [
                    ("ct_ratio_static_fit", f(row, "ct_ratio_static_fit"), "ratio"),
                    ("cp_ratio_static_fit", f(row, "cp_ratio_static_fit"), "ratio"),
                    ("qt_ratio_static_fit", f(row, "qt_ratio_static_fit"), "ratio"),
                    ("fit_within_source_j_range", f(row, "fit_within_source_j_range"), "boolean"),
                ]
            )
            evidence = "uiuc_5in_measured_fit_point"
        else:
            row_type = "uiuc_mu_guard_packet_uiuc_5in_mean"
            common_metrics.extend(
                [
                    ("ct_ratio_static_fit_mean", f(row, "ct_ratio_static_fit_mean"), "ratio"),
                    ("ct_ratio_static_fit_min", f(row, "ct_ratio_static_fit_min"), "ratio"),
                    ("ct_ratio_static_fit_max", f(row, "ct_ratio_static_fit_max"), "ratio"),
                    ("cp_ratio_static_fit_mean", f(row, "cp_ratio_static_fit_mean"), "ratio"),
                    ("cp_ratio_static_fit_min", f(row, "cp_ratio_static_fit_min"), "ratio"),
                    ("cp_ratio_static_fit_max", f(row, "cp_ratio_static_fit_max"), "ratio"),
                    ("qt_ratio_static_fit_mean", f(row, "qt_ratio_static_fit_mean"), "ratio"),
                    ("qt_ratio_static_fit_min", f(row, "qt_ratio_static_fit_min"), "ratio"),
                    ("qt_ratio_static_fit_max", f(row, "qt_ratio_static_fit_max"), "ratio"),
                ]
            )
            evidence = "uiuc_5in_mean_fit_point"
        for metric, value, unit in common_metrics:
            add_metric(
                rows,
                row_type=row_type,
                name=row["name"],
                metric=metric,
                value=value,
                unit=unit,
                source_file=FORWARD_REFERENCE,
                source_url=source_url,
                evidence_role=evidence,
                note="UIUC low-to-mid-J measured propeller rows with explicit J-to-mu conversion.",
            )


def add_current_operating_rows(rows: list[dict[str, object]], forward_rows: list[dict[str, str]]) -> None:
    metrics = [
        ("speed_m_s", "m/s"),
        ("hover_rpm", "rpm"),
        ("tip_speed_m_s", "m/s"),
        ("code_advance_ratio_mu", "mu"),
        ("uiuc_equivalent_j", "J"),
        ("uiuc_5in_ct_ratio_fit_mean", "ratio"),
        ("uiuc_5in_cp_ratio_fit_mean", "ratio"),
        ("uiuc_5in_qt_ratio_fit_mean", "ratio"),
        ("airflow_thrust_multiplier_code", "ratio"),
        ("code_multiplier_over_uiuc_5in_mean", "ratio"),
        ("current_same_rpm_constant_qt_power_ratio_proxy", "ratio"),
        ("current_power_proxy_over_uiuc_cp_mean", "ratio"),
        ("current_qt_proxy_over_uiuc_qt_mean", "ratio"),
        ("high_advance_loss_code", "ratio"),
        ("rotor_stall_intensity_code", "ratio"),
        ("disk_drag_force_per_rotor_n", "N"),
        ("flapping_tilt_deg_code", "deg"),
    ]
    for row in forward_rows:
        if row.get("row_type") != "current_preset_level_forward":
            continue
        speed = f(row, "speed_m_s")
        preset = row["name"]
        mu = f(row, "code_advance_ratio_mu")
        j = f(row, "uiuc_equivalent_j")
        name = f"{preset}_{speed:.1f}m_s"
        for metric, unit in metrics:
            add_metric(
                rows,
                row_type="uiuc_mu_guard_packet_current_point",
                name=name,
                metric=metric,
                value=f(row, metric),
                unit=unit,
                source_file=FORWARD_REFERENCE,
                source_url=JAVA_CONFIG_SOURCE,
                evidence_role="current_model_vs_uiuc_forward_fit",
                note="Current hover-RPM level-forward operating point with equivalent UIUC propeller J.",
            )
        for metric, value, unit in [
            ("recomputed_j_pi_times_mu", math.pi * mu, "J"),
            ("j_delta_recomputed_minus_source", math.pi * mu - j, "J"),
            ("mu_if_uiuc_j_misread_as_code_mu", j, "mu"),
            ("misread_mu_over_correct_mu", safe_ratio(j, mu), "x"),
            ("inside_uiuc_selected_experimental_j_max", 1.0 if j <= UIUC_SELECTED_5IN_EXPERIMENTAL_J_MAX else 0.0, "boolean"),
            ("uiuc_j_over_selected_experimental_j_max", safe_ratio(j, UIUC_SELECTED_5IN_EXPERIMENTAL_J_MAX), "x"),
        ]:
            add_metric(
                rows,
                row_type="uiuc_mu_guard_packet_current_point",
                name=name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=FORWARD_REFERENCE,
                source_url=JAVA_PHYSICS_SOURCE,
                evidence_role="j_mu_unit_guard_for_current_point",
                note="Derived conversion guard; if UIUC J is accidentally used as code mu, advance ratio is too high by pi.",
            )


def add_threshold_rows(rows: list[dict[str, object]], high_rows: list[dict[str, str]], mejzlik_rows: list[dict[str, str]]) -> None:
    mejzlik_zero_j = first_metric(
        mejzlik_rows,
        "mejzlik_prop_packet_summary",
        "wind_tunnel_ct_zero_boundary",
        "ct_zero_crossing_j_linear_0p6_to_0p8",
    )
    moderate_mu_limit = first_metric(
        high_rows,
        "high_advance_packet_summary",
        "high_advance_rotor_prop_summary",
        "moderate_5x4p5_positive_ct_mu_limit",
    )
    fpv_three_blade_mu_limit = first_metric(
        high_rows,
        "high_advance_packet_summary",
        "high_advance_rotor_prop_summary",
        "three_blade_5p1x5_positive_ct_mu_limit",
    )
    apc_max_mu = first_metric(
        high_rows,
        "high_advance_packet_summary",
        "high_advance_rotor_prop_summary",
        "selected_apc_max_mu_equivalent",
    )
    for threshold_name, mu, note in CURRENT_THRESHOLDS:
        j = math.pi * mu
        metrics = [
            ("code_mu", mu, "mu"),
            ("equivalent_axial_propeller_j", j, "J"),
            ("within_uiuc_selected_5in_experimental_j_max", 1.0 if j <= UIUC_SELECTED_5IN_EXPERIMENTAL_J_MAX else 0.0, "boolean"),
            ("j_over_uiuc_selected_5in_experimental_j_max", safe_ratio(j, UIUC_SELECTED_5IN_EXPERIMENTAL_J_MAX), "x"),
            ("j_over_mejzlik_wind_tunnel_ct_zero_j", safe_ratio(j, mejzlik_zero_j), "x"),
            ("mu_over_apc_5x4p5_positive_ct_limit", safe_ratio(mu, moderate_mu_limit), "x"),
            ("mu_over_apc_5p1x5_3blade_positive_ct_limit", safe_ratio(mu, fpv_three_blade_mu_limit), "x"),
            ("mu_over_selected_apc_max_mu", safe_ratio(mu, apc_max_mu), "x"),
        ]
        for metric, value, unit in metrics:
            add_metric(
                rows,
                row_type="uiuc_mu_guard_packet_current_threshold",
                name=threshold_name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=HIGH_ADVANCE_PACKET,
                source_url=JAVA_PHYSICS_SOURCE,
                evidence_role="current_threshold_j_mu_mapping",
                note=note,
            )


def add_apc_rows(rows: list[dict[str, object]], high_rows: list[dict[str, str]]) -> None:
    prop_metrics = grouped_packet_metrics(high_rows, "high_advance_packet_apc_prop_summary")
    selected = {
        "row_count": "count",
        "diameter_in": "in",
        "pitch_in": "in",
        "pitch_to_diameter": "ratio",
        "blade_count": "count",
        "j_max": "J",
        "code_mu_equivalent_max": "mu",
        "highest_positive_ct_j": "J",
        "highest_positive_ct_mu_equivalent": "mu",
        "nearest_zero_ct_j": "J",
        "max_efficiency_pe": "efficiency",
    }
    for name, metrics in sorted(prop_metrics.items()):
        for metric, unit in selected.items():
            if metric not in metrics:
                continue
            row = metrics[metric]
            add_metric(
                rows,
                row_type="uiuc_mu_guard_packet_apc_axial_prop_summary",
                name=name,
                metric=metric,
                value=row["value"],
                unit=unit,
                source_file=HIGH_ADVANCE_PACKET,
                source_url=row.get("source_url", APC_URL),
                evidence_role="apc_axial_high_j_boundary",
                note="APC axial propeller prediction; useful for J/mu range checks, not direct edgewise rotor-stall fitting.",
            )

    for row in high_rows:
        if row.get("row_type") != "high_advance_packet_apc_target_point":
            continue
        if row.get("metric") not in {
            "target_j",
            "target_mu_equivalent",
            "nearest_j",
            "nearest_mu_equivalent",
            "target_delta_j",
            "target_within_file_j_range",
            "ct",
            "cp",
            "ct_over_same_rpm_static_ct",
            "cp_over_same_rpm_static_cp",
            "q_over_t_over_same_rpm_static_q_over_t",
        }:
            continue
        add_metric(
            rows,
            row_type="uiuc_mu_guard_packet_apc_threshold_point",
            name=row["name"],
            metric=row["metric"],
            value=row["value"],
            unit=row["unit"],
            source_file=HIGH_ADVANCE_PACKET,
            source_url=row.get("source_url", APC_URL),
            evidence_role="apc_axial_nearest_point_to_project_threshold",
            note=row.get("note", "Current code threshold mapped to nearest APC axial propeller point."),
        )


def parse_j_from_table_name(name: str) -> float:
    match = re.search(r"_j_([0-9]+(?:\.[0-9]+)?)$", name)
    if not match:
        raise ValueError(f"cannot parse J from {name!r}")
    return float(match.group(1))


def add_mejzlik_rows(rows: list[dict[str, object]], mejzlik_rows: list[dict[str, str]]) -> None:
    table_metrics = grouped_packet_metrics(mejzlik_rows, "mejzlik_prop_packet_table_value")
    for name, metrics in sorted(table_metrics.items()):
        j = parse_j_from_table_name(name)
        for metric, value, unit in [
            ("published_j", j, "J"),
            ("code_mu_equivalent_j_over_pi", j / math.pi, "mu"),
            ("j_if_mu_misread_as_published_j", math.pi * j, "J"),
            ("misread_j_as_mu_over_correct_mu", math.pi, "x"),
        ]:
            add_metric(
                rows,
                row_type="uiuc_mu_guard_packet_mejzlik_table_point",
                name=name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=MEJZLIK_PACKET,
                source_url=MEJZLIK_URL,
                evidence_role="mejzlik_wind_tunnel_j_mu_guard",
                note="Published table value from AirShaper/Mejzlik study with explicit J-to-mu conversion.",
            )
        for metric in ["ct", "cp", "eta"]:
            if metric not in metrics:
                continue
            row = metrics[metric]
            add_metric(
                rows,
                row_type="uiuc_mu_guard_packet_mejzlik_table_point",
                name=name,
                metric=metric,
                value=row["value"],
                unit=row["unit"],
                source_file=MEJZLIK_PACKET,
                source_url=row.get("source_url", MEJZLIK_URL),
                evidence_role="mejzlik_wind_tunnel_table_value",
                note="Published AirShaper/Mejzlik table value; not a 5-inch FPV prop or edgewise rotor test.",
            )

    for row in mejzlik_rows:
        if row.get("row_type") != "mejzlik_prop_packet_summary":
            continue
        add_metric(
            rows,
            row_type="uiuc_mu_guard_packet_mejzlik_summary",
            name=row["name"],
            metric=row["metric"],
            value=row["value"],
            unit=row["unit"],
            source_file=MEJZLIK_PACKET,
            source_url=row.get("source_url", MEJZLIK_URL),
            evidence_role=row.get("evidence_role", "mejzlik_high_j_windmilling_boundary"),
            note=row.get("note", "Measured axial high-J windmilling sanity check."),
        )


def find_forward_current(forward_rows: list[dict[str, str]], preset: str, speed: float) -> dict[str, str]:
    for row in forward_rows:
        if row.get("row_type") == "current_preset_level_forward" and row.get("name") == preset and abs(f(row, "speed_m_s") - speed) < 1.0e-9:
            return row
    raise LookupError(f"missing current row {preset} {speed}")


def find_uiuc_mean(forward_rows: list[dict[str, str]], j: float) -> dict[str, str]:
    for row in forward_rows:
        if row.get("row_type") == "uiuc_5in_forward_mean" and abs(f(row, "uiuc_j") - j) < 1.0e-9:
            return row
    raise LookupError(f"missing UIUC mean J={j}")


def add_summary_rows(
    rows: list[dict[str, object]],
    forward_rows: list[dict[str, str]],
    high_rows: list[dict[str, str]],
    mejzlik_rows: list[dict[str, str]],
) -> None:
    uiuc_j045 = find_uiuc_mean(forward_rows, 0.45)
    racing_12p5 = find_forward_current(forward_rows, "racingQuad", 12.5)
    racing_20 = find_forward_current(forward_rows, "racingQuad", 20.0)
    mejzlik_zero_j = first_metric(
        mejzlik_rows,
        "mejzlik_prop_packet_summary",
        "wind_tunnel_ct_zero_boundary",
        "ct_zero_crossing_j_linear_0p6_to_0p8",
    )
    mejzlik_zero_mu = first_metric(
        mejzlik_rows,
        "mejzlik_prop_packet_summary",
        "wind_tunnel_ct_zero_boundary",
        "ct_zero_crossing_mu_equivalent",
    )
    apc_max_mu = first_metric(
        high_rows,
        "high_advance_packet_summary",
        "high_advance_rotor_prop_summary",
        "selected_apc_max_mu_equivalent",
    )
    apc_moderate_mu = first_metric(
        high_rows,
        "high_advance_packet_summary",
        "high_advance_rotor_prop_summary",
        "moderate_5x4p5_positive_ct_mu_limit",
    )
    metrics = [
        ("pi_factor_j_over_mu", math.pi, "x"),
        ("uiuc_5in_mean_j045_mu_equivalent", f(uiuc_j045, "code_mu_equivalent"), "mu"),
        ("uiuc_5in_mean_j045_ct_ratio", f(uiuc_j045, "ct_ratio_static_fit_mean"), "ratio"),
        ("uiuc_5in_mean_j045_cp_ratio", f(uiuc_j045, "cp_ratio_static_fit_mean"), "ratio"),
        ("uiuc_5in_mean_j045_qt_ratio", f(uiuc_j045, "qt_ratio_static_fit_mean"), "ratio"),
        ("racingQuad_12p5m_s_code_mu", f(racing_12p5, "code_advance_ratio_mu"), "mu"),
        ("racingQuad_12p5m_s_equivalent_j", f(racing_12p5, "uiuc_equivalent_j"), "J"),
        ("racingQuad_12p5m_s_uiuc_ct_ratio", f(racing_12p5, "uiuc_5in_ct_ratio_fit_mean"), "ratio"),
        ("racingQuad_12p5m_s_code_multiplier_over_uiuc_ct", f(racing_12p5, "code_multiplier_over_uiuc_5in_mean"), "x"),
        ("racingQuad_20m_s_equivalent_j", f(racing_20, "uiuc_equivalent_j"), "J"),
        ("racingQuad_20m_s_uiuc_ct_ratio", f(racing_20, "uiuc_5in_ct_ratio_fit_mean"), "ratio"),
        ("racingQuad_20m_s_code_multiplier_over_uiuc_ct", f(racing_20, "code_multiplier_over_uiuc_5in_mean"), "x"),
        ("uiuc_selected_5in_experimental_j_max", UIUC_SELECTED_5IN_EXPERIMENTAL_J_MAX, "J"),
        ("uiuc_selected_5in_experimental_mu_max", UIUC_SELECTED_5IN_EXPERIMENTAL_J_MAX / math.pi, "mu"),
        ("mejzlik_wind_tunnel_ct_zero_j", mejzlik_zero_j, "J"),
        ("mejzlik_wind_tunnel_ct_zero_mu", mejzlik_zero_mu, "mu"),
        ("current_lift_dissymmetry_full_j", math.pi * 0.34, "J"),
        ("current_retreating_stall_start_j", math.pi * 0.42, "J"),
        ("current_high_advance_loss_start_j", math.pi * 0.46, "J"),
        ("current_high_advance_loss_start_over_mejzlik_ct_zero_j", safe_ratio(math.pi * 0.46, mejzlik_zero_j), "x"),
        ("apc_5x4p5_positive_ct_mu_limit", apc_moderate_mu, "mu"),
        ("selected_apc_max_mu_equivalent", apc_max_mu, "mu"),
    ]
    for metric, value, unit in metrics:
        add_metric(
            rows,
            row_type="uiuc_mu_guard_packet_summary",
            name="forward_flow_j_mu_guard_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=UIUC_URL,
            evidence_role="compact_forward_flow_j_mu_handoff",
            note="Compact summary for avoiding J/mu pi-factor mistakes and source-domain mixups.",
        )
    add_metric(
        rows,
        row_type="uiuc_mu_guard_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Use UIUC measured 5-inch rows for low-to-mid axial J CT/CP rolloff, Mejzlik wind-tunnel rows for a "
            "measured high-J axial windmilling sanity check, and APC rows for wider axial prediction coverage. "
            "Do not fit edgewise retreating-blade-stall coefficients directly from axial propeller J rows."
        ),
        unit="text",
        source_file=OUTPUT,
        source_url=UIUC_URL,
        evidence_role="method_caveat",
        note="The packet is a unit/source guard, not a full aerodynamic model fit.",
    )


def build_rows() -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    forward_rows = read_rows(FORWARD_REFERENCE)
    high_rows = read_rows(HIGH_ADVANCE_PACKET)
    mejzlik_rows = read_rows(MEJZLIK_PACKET)
    add_formula_rows(rows, forward_rows)
    add_uiuc_rows(rows, forward_rows)
    add_current_operating_rows(rows, forward_rows)
    add_threshold_rows(rows, high_rows, mejzlik_rows)
    add_apc_rows(rows, high_rows)
    add_mejzlik_rows(rows, mejzlik_rows)
    add_summary_rows(rows, forward_rows, high_rows, mejzlik_rows)
    return rows


def sync_summary(packet_rows: Iterable[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("uiuc_mu_guard_packet_")]
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


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
