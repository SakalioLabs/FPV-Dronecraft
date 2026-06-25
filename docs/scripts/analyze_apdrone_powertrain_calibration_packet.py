"""Build an APdrone powertrain calibration packet from public source rows.

Outputs:
  docs/data/apdrone_powertrain_calibration_packet.csv

The packet is intentionally a narrow metric table. It gathers direct APdrone
YSIDO PDF thrust rows, the public Foxeer Donut 5145/Tyto screenshot max point,
selected Tyto public static test pages, and the MQTB HQ 5x4x3 current fit into
one handoff file for model tuning. It does not claim an exact APdrone
YSIDO 2507 + Foxeer Donut 5145 bench curve; that exact public curve is still
missing.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
OUTPUT = DATA / "apdrone_powertrain_calibration_packet.csv"

APDRONE_PDF = DATA / "apdrone_motor_thrust_pdf_reference.csv"
FOXEER = DATA / "foxeer_donut_5145_thrust_image_reference.csv"
TYTO_POWER = DATA / "tyto_fpv_static_powertrain_reference.csv"
TYTO_TORQUE = DATA / "tyto_fpv_static_torque_ratio_reference.csv"
MQTB = DATA / "mqtb_hq5x4x3_current_model_reference.csv"
MODEL = DATA / "apdrone_flight_vs_model_reference.csv"


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def f(row: dict[str, str], key: str, default: float = math.nan) -> float:
    value = row.get(key, "")
    if value == "":
        return default
    return float(value)


def finite(value: float) -> bool:
    return math.isfinite(value)


def value_text(value: float) -> str:
    if not finite(value):
        return ""
    return f"{value:.12g}"


def join_nonempty(parts: Iterable[str], separator: str) -> str:
    return separator.join(part.strip() for part in parts if part and part.strip())


def add_metric(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: float,
    unit: str,
    source_file: Path,
    source_url: str = "",
    evidence_level: str = "",
    hardware_scope: str = "",
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
            "evidence_level": evidence_level,
            "hardware_scope": hardware_scope,
            "source_row_type": source_row_type,
            "note": note,
        }
    )


def add_note(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    source_file: Path,
    note: str,
    source_url: str = "",
    evidence_level: str = "",
    hardware_scope: str = "",
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": "",
            "unit": "",
            "source_file": repo_path(source_file),
            "source_url": source_url,
            "evidence_level": evidence_level,
            "hardware_scope": hardware_scope,
            "source_row_type": "",
            "note": note,
        }
    )


def project_model_rows(rows: list[dict[str, str]], model_rows: list[dict[str, str]]) -> dict[str, float]:
    model = next(row for row in model_rows if row.get("row_type") == "project_preset_model" and row.get("preset") == "apDrone")
    max_thrust = f(model, "max_rotor_thrust_n")
    k = f(model, "thrust_coefficient_n_per_rad2_s2")
    hover_thrust = f(model, "hover_thrust_per_motor_n")
    max_rpm = math.sqrt(max_thrust / k) * 60.0 / (2.0 * math.pi)
    hover_rpm = math.sqrt(hover_thrust / k) * 60.0 / (2.0 * math.pi)
    source = model.get("source_page", "")
    metrics = {
        "max_rotor_thrust_n": max_thrust,
        "thrust_coefficient_n_per_rad2_s2": k,
        "hover_thrust_per_motor_n": hover_thrust,
        "max_rotor_speed_rpm": max_rpm,
        "hover_rotor_speed_rpm": hover_rpm,
        "yaw_torque_per_thrust_m": f(model, "yaw_torque_per_thrust_m"),
        "battery_current_limit_a": f(model, "max_battery_current_a"),
        "nominal_battery_voltage_v": f(model, "nominal_battery_voltage_v"),
    }
    units = {
        "max_rotor_thrust_n": "N",
        "thrust_coefficient_n_per_rad2_s2": "N/(rad/s)^2",
        "hover_thrust_per_motor_n": "N",
        "max_rotor_speed_rpm": "rpm",
        "hover_rotor_speed_rpm": "rpm",
        "yaw_torque_per_thrust_m": "m",
        "battery_current_limit_a": "A",
        "nominal_battery_voltage_v": "V",
    }
    for metric, value in metrics.items():
        add_metric(
            rows,
            row_type="apdrone_powertrain_project_model",
            name="apDrone",
            metric=metric,
            value=value,
            unit=units[metric],
            source_file=MODEL,
            source_url=source,
            evidence_level="current_project_model",
            hardware_scope="APdrone preset",
            source_row_type="project_preset_model",
        )
    return metrics


def add_pdf_rows(rows: list[dict[str, str]], pdf_rows: list[dict[str, str]]) -> None:
    for row in pdf_rows:
        if row.get("row_type") == "apdrone_motor_pdf_prop_summary":
            name = str(row["prop"])
            metrics = {
                "max_table_thrust_n": (f(row, "max_table_thrust_n"), "N"),
                "max_table_current_a": (f(row, "max_table_current_a"), "A"),
                "max_table_power_w": (f(row, "max_table_power_w"), "W"),
                "project_max_over_table_max": (f(row, "project_max_over_table_max"), "ratio"),
                "table_max_over_project_max": (f(row, "table_max_over_project_max"), "ratio"),
            }
            for metric, (value, unit) in metrics.items():
                add_metric(
                    rows,
                    row_type="apdrone_powertrain_pdf_prop_max",
                    name=name,
                    metric=metric,
                    value=value,
                    unit=unit,
                    source_file=APDRONE_PDF,
                    source_url=row.get("source_page", ""),
                    evidence_level="direct_APdrone_motor_pdf_visible_table",
                    hardware_scope="YSIDO 2507 1800KV PDF, visible prop table",
                    source_row_type=row["row_type"],
                    note="Visible PDF maxima; prop is from the APdrone motor PDF table, not necessarily the exact installed Foxeer Donut 5145.",
                )
        elif row.get("row_type") == "apdrone_motor_pdf_operating_point_projection":
            name = f"{row.get('fit_scope')}:{row.get('operating_point')}"
            metrics = {
                "per_motor_thrust_n": (f(row, "per_motor_thrust_n"), "N"),
                "predicted_current_total_a": (f(row, "predicted_current_total_a"), "A"),
                "current_over_project_pack_limit": (f(row, "current_over_project_pack_limit"), "ratio"),
            }
            for metric, (value, unit) in metrics.items():
                add_metric(
                    rows,
                    row_type="apdrone_powertrain_pdf_current_projection",
                    name=name,
                    metric=metric,
                    value=value,
                    unit=unit,
                    source_file=APDRONE_PDF,
                    source_url=row.get("source_page", ""),
                    evidence_level="direct_APdrone_motor_pdf_fit_projection",
                    hardware_scope="YSIDO 2507 PDF current/power fit",
                    source_row_type=row["row_type"],
                    note="Power-law current projection from visible APdrone motor PDF rows; hover and high-thrust endpoints can be extrapolations outside a specific prop table range.",
                )
        elif row.get("row_type") == "apdrone_motor_pdf_loaded_rpm_k_summary":
            name = f"{row.get('prop')}:{row.get('kv_source')}"
            metrics = {
                "estimated_k_mean": (f(row, "estimated_k_mean"), "N/(rad/s)^2"),
                "project_k_over_estimated_k_mean": (f(row, "project_k_over_estimated_k_mean"), "ratio"),
                "estimated_k_mean_over_project_k": (f(row, "estimated_k_mean_over_project_k"), "ratio"),
            }
            for metric, (value, unit) in metrics.items():
                add_metric(
                    rows,
                    row_type="apdrone_powertrain_pdf_loaded_k_anchor",
                    name=name,
                    metric=metric,
                    value=value,
                    unit=unit,
                    source_file=APDRONE_PDF,
                    source_url=row.get("source_page", ""),
                    evidence_level="direct_APdrone_motor_pdf_back_emf_estimate",
                    hardware_scope="YSIDO 2507 PDF full-throttle rows with assumed KV",
                    source_row_type=row["row_type"],
                    note="Loaded-RPM estimate from motor resistance and KV, not measured RPM.",
                )


def add_foxeer_rows(rows: list[dict[str, str]], foxeer_rows: list[dict[str, str]]) -> None:
    for row in foxeer_rows:
        if row.get("row_type") == "foxeer_donut_5145_public_test_max_point":
            name = str(row["propeller"])
            metrics = {
                "thrust_n": (f(row, "thrust_n"), "N"),
                "current_a": (f(row, "current_a"), "A"),
                "electric_power_w": (f(row, "electric_power_w"), "W"),
                "rpm_14_poles": (f(row, "rpm_14_poles"), "rpm"),
                "derived_thrust_coefficient_n_per_rad_s2": (f(row, "derived_thrust_coefficient_n_per_rad_s2"), "N/(rad/s)^2"),
                "derived_torque_per_thrust_m": (f(row, "derived_torque_per_thrust_m"), "m"),
                "apDrone_max_thrust_ratio": (f(row, "apDrone_max_thrust_ratio"), "ratio"),
                "apDrone_thrust_coeff_ratio": (f(row, "apDrone_thrust_coeff_ratio"), "ratio"),
                "apDrone_max_rpm_ratio": (f(row, "apDrone_max_rpm_ratio"), "ratio"),
            }
            for metric, (value, unit) in metrics.items():
                add_metric(
                    rows,
                    row_type="apdrone_powertrain_foxeer_image_anchor",
                    name=name,
                    metric=metric,
                    value=value,
                    unit=unit,
                    source_file=FOXEER,
                    source_url=row.get("source_page", ""),
                    evidence_level="adjacent_Foxeer_Donut_public_image",
                    hardware_scope=row.get("motor", "Flash 2207 1850KV") + " / visible prop max point",
                    source_row_type=row["row_type"],
                    note="Public Tyto screenshot/blog max point; adjacent prop-family anchor, not the exact APdrone YSIDO 2507 bench curve.",
                )


def add_tyto_rows(rows: list[dict[str, str]], power_rows: list[dict[str, str]], torque_rows: list[dict[str, str]]) -> None:
    for row in power_rows:
        if row.get("row_type") != "tyto_static_powertrain_summary":
            continue
        name = f"{row.get('test_hash')}:{row.get('title')}"
        metrics = {
            "max_thrust_n": (f(row, "max_thrust_n"), "N"),
            "rpm_at_max_thrust": (f(row, "rpm_at_max_thrust"), "rpm"),
            "current_a_at_max_thrust": (f(row, "current_a_at_max_thrust"), "A"),
            "voltage_v_at_max_thrust": (f(row, "voltage_v_at_max_thrust"), "V"),
            "fit_k_n_per_rad2_s2": (f(row, "fit_k_n_per_rad2_s2"), "N/(rad/s)^2"),
            "fit_r2": (f(row, "fit_r2"), "ratio"),
            "apdrone_max_thrust_over_tyto_max_thrust": (f(row, "apdrone_max_thrust_over_tyto_max_thrust"), "ratio"),
            "apdrone_thrust_coefficient_over_tyto_fit": (f(row, "apdrone_thrust_coefficient_over_tyto_fit"), "ratio"),
        }
        for metric, (value, unit) in metrics.items():
            add_metric(
                rows,
                row_type="apdrone_powertrain_tyto_static_anchor",
                name=name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=TYTO_POWER,
                source_url=row.get("source_page", ""),
                evidence_level="adjacent_public_Tyto_static_test",
                hardware_scope=join_nonempty([row.get("motor", ""), row.get("propeller", "")], " / "),
                source_row_type=row["row_type"],
                note="Public Tyto test page with full static samples; useful as FPV-class scale, not exact APdrone hardware.",
            )

    for row in torque_rows:
        if row.get("row_type") != "tyto_torque_ratio_summary":
            continue
        name = f"{row.get('test_hash')}:{row.get('title')}"
        metrics = {
            "fit_q_over_t_m": (f(row, "fit_q_over_t_m"), "m"),
            "fit_r2": (f(row, "fit_r2"), "ratio"),
            "q_over_t_at_max_thrust_m": (f(row, "q_over_t_at_max_thrust_m"), "m"),
            "apDrone_configured_over_fit_q_over_t": (f(row, "apDrone_configured_over_fit_q_over_t"), "ratio"),
            "apDrone_fit_q_over_t_over_configured": (f(row, "apDrone_fit_q_over_t_over_configured"), "ratio"),
            "apDrone_max_thrust_q_over_t_over_configured": (f(row, "apDrone_max_thrust_q_over_t_over_configured"), "ratio"),
        }
        for metric, (value, unit) in metrics.items():
            add_metric(
                rows,
                row_type="apdrone_powertrain_tyto_yaw_anchor",
                name=name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=TYTO_TORQUE,
                source_url=row.get("source_page", ""),
                evidence_level="adjacent_public_Tyto_torque_test",
                hardware_scope=join_nonempty([row.get("motor", ""), row.get("propeller", "")], " / "),
                source_row_type=row["row_type"],
                note="Measured static torque/thrust ratio anchor for yaw torque scale.",
            )


def add_mqtb_rows(rows: list[dict[str, str]], mqtb_rows: list[dict[str, str]]) -> None:
    for row in mqtb_rows:
        if row.get("row_type") == "reference_mqtb_hq5x4x3_current_fit_summary":
            metrics = {
                "current_fit_a": (f(row, "current_fit_a"), "A/N^b"),
                "current_fit_b": (f(row, "current_fit_b"), "exponent"),
                "current_fit_log_r2": (f(row, "current_fit_log_r2"), "ratio"),
                "current_residual_rms_pct": (f(row, "current_residual_rms_pct"), "percent"),
            }
            for metric, (value, unit) in metrics.items():
                add_metric(
                    rows,
                    row_type="apdrone_powertrain_mqtb_current_anchor",
                    name=row.get("name", "MQTB_HQ_v1s_5x4x3_current_power_law_fit"),
                    metric=metric,
                    value=value,
                    unit=unit,
                    source_file=MQTB,
                    source_url=row.get("source", ""),
                    evidence_level="adjacent_MQTB_FPV_current_fit",
                    hardware_scope="HQ v1s 5x4x3 representative FPV prop curve",
                    source_row_type=row["row_type"],
                    note="FPV current-vs-thrust shape anchor; not APdrone-specific.",
                )
        elif row.get("row_type") == "current_vs_mqtb_hq5x4x3_current_model":
            metrics = {
                "target_thrust_per_motor_n": (f(row, "target_thrust_per_motor_n"), "N"),
                "mqtb_fit_total_motor_current_a": (f(row, "mqtb_fit_total_motor_current_a"), "A"),
                "mqtb_fit_total_current_over_pack_limit": (f(row, "mqtb_fit_total_current_over_pack_limit"), "ratio"),
                "current_pack_sag_v_at_mqtb_fit_current": (f(row, "current_pack_sag_v_at_mqtb_fit_current"), "V"),
            }
            for metric, (value, unit) in metrics.items():
                add_metric(
                    rows,
                    row_type="apdrone_powertrain_mqtb_current_anchor",
                    name=row.get("name", ""),
                    metric=metric,
                    value=value,
                    unit=unit,
                    source_file=MQTB,
                    source_url=row.get("source", ""),
                    evidence_level="adjacent_MQTB_FPV_current_fit",
                    hardware_scope=f"{row.get('preset')} {row.get('operating_point')}",
                    source_row_type=row["row_type"],
                    note="Current project preset compared through the MQTB representative current-power fit.",
                )


def add_summary_rows(
    rows: list[dict[str, str]],
    *,
    pdf_rows: list[dict[str, str]],
    foxeer_rows: list[dict[str, str]],
    power_rows: list[dict[str, str]],
    torque_rows: list[dict[str, str]],
) -> None:
    prop_max = [row for row in pdf_rows if row.get("row_type") == "apdrone_motor_pdf_prop_summary"]
    project_over_pdf = [f(row, "project_max_over_table_max") for row in prop_max]
    add_metric(
        rows,
        row_type="apdrone_powertrain_bound_summary",
        name="pdf_visible_prop_max",
        metric="project_max_over_pdf_visible_table_min",
        value=min(project_over_pdf),
        unit="ratio",
        source_file=APDRONE_PDF,
        evidence_level="direct_APdrone_motor_pdf_visible_table",
        hardware_scope="YSIDO PDF visible prop maxima",
        note="Current max rotor thrust is within this range against the four visible PDF prop maxima.",
    )
    add_metric(
        rows,
        row_type="apdrone_powertrain_bound_summary",
        name="pdf_visible_prop_max",
        metric="project_max_over_pdf_visible_table_max",
        value=max(project_over_pdf),
        unit="ratio",
        source_file=APDRONE_PDF,
        evidence_level="direct_APdrone_motor_pdf_visible_table",
        hardware_scope="YSIDO PDF visible prop maxima",
        note="Current max rotor thrust is within this range against the four visible PDF prop maxima.",
    )

    loaded_4s = [
        row
        for row in pdf_rows
        if row.get("row_type") == "apdrone_motor_pdf_loaded_rpm_k_summary"
        and row.get("prop") in {"7056 3R", "6045R"}
    ]
    k_ratios = [f(row, "project_k_over_estimated_k_mean") for row in loaded_4s]
    add_metric(
        rows,
        row_type="apdrone_powertrain_bound_summary",
        name="pdf_4s_loaded_k_estimate",
        metric="project_k_over_pdf_4s_loaded_k_min",
        value=min(k_ratios),
        unit="ratio",
        source_file=APDRONE_PDF,
        evidence_level="direct_APdrone_motor_pdf_back_emf_estimate",
        hardware_scope="YSIDO PDF 4S rows with PDF/BetaFlight KV assumptions",
        note="Loaded-RPM estimate uses motor resistance and assumed KV; this brackets the current k against 4S visible rows.",
    )
    add_metric(
        rows,
        row_type="apdrone_powertrain_bound_summary",
        name="pdf_4s_loaded_k_estimate",
        metric="project_k_over_pdf_4s_loaded_k_max",
        value=max(k_ratios),
        unit="ratio",
        source_file=APDRONE_PDF,
        evidence_level="direct_APdrone_motor_pdf_back_emf_estimate",
        hardware_scope="YSIDO PDF 4S rows with PDF/BetaFlight KV assumptions",
        note="Loaded-RPM estimate uses motor resistance and assumed KV; this brackets the current k against 4S visible rows.",
    )

    donut = [
        row
        for row in foxeer_rows
        if row.get("row_type") == "foxeer_donut_5145_public_test_max_point"
        and row.get("propeller") == "Foxeer Donut 5145"
    ][0]
    for metric in ["apDrone_thrust_coeff_ratio", "apDrone_max_rpm_ratio", "apDrone_max_thrust_ratio"]:
        add_metric(
            rows,
            row_type="apdrone_powertrain_bound_summary",
            name="foxeer_donut_image_max_point",
            metric=metric,
            value=f(donut, metric),
            unit="ratio",
            source_file=FOXEER,
            source_url=donut.get("source_page", ""),
            evidence_level="adjacent_Foxeer_Donut_public_image",
            hardware_scope="Flash 2207 1850KV / Foxeer Donut 5145 screenshot max point",
            note="This is the closest public prop-specific anchor, but the motor and voltage differ from APdrone.",
        )

    tyto_k = [f(row, "apdrone_thrust_coefficient_over_tyto_fit") for row in power_rows if row.get("row_type") == "tyto_static_powertrain_summary"]
    add_metric(
        rows,
        row_type="apdrone_powertrain_bound_summary",
        name="tyto_public_static_k_range",
        metric="apdrone_k_over_tyto_fit_min",
        value=min(tyto_k),
        unit="ratio",
        source_file=TYTO_POWER,
        evidence_level="adjacent_public_Tyto_static_test",
        hardware_scope="selected FPV-class static tests",
        note="Wide range is expected because these are adjacent powertrains, not APdrone hardware.",
    )
    add_metric(
        rows,
        row_type="apdrone_powertrain_bound_summary",
        name="tyto_public_static_k_range",
        metric="apdrone_k_over_tyto_fit_max",
        value=max(tyto_k),
        unit="ratio",
        source_file=TYTO_POWER,
        evidence_level="adjacent_public_Tyto_static_test",
        hardware_scope="selected FPV-class static tests",
        note="Wide range is expected because these are adjacent powertrains, not APdrone hardware.",
    )

    tyto_q = [f(row, "apDrone_configured_over_fit_q_over_t") for row in torque_rows if row.get("row_type") == "tyto_torque_ratio_summary"]
    add_metric(
        rows,
        row_type="apdrone_powertrain_bound_summary",
        name="tyto_public_yaw_q_over_t_range",
        metric="apdrone_q_over_t_over_tyto_fit_min",
        value=min(tyto_q),
        unit="ratio",
        source_file=TYTO_TORQUE,
        evidence_level="adjacent_public_Tyto_torque_test",
        hardware_scope="selected FPV-class static tests",
        note="Current APdrone yaw torque per thrust sits near the high side of the selected Tyto range.",
    )
    add_metric(
        rows,
        row_type="apdrone_powertrain_bound_summary",
        name="tyto_public_yaw_q_over_t_range",
        metric="apdrone_q_over_t_over_tyto_fit_max",
        value=max(tyto_q),
        unit="ratio",
        source_file=TYTO_TORQUE,
        evidence_level="adjacent_public_Tyto_torque_test",
        hardware_scope="selected FPV-class static tests",
        note="Current APdrone yaw torque per thrust sits near the high side of the selected Tyto range.",
    )

    add_note(
        rows,
        row_type="apdrone_powertrain_packet_method",
        name="method",
        metric="scope_and_caveat",
        source_file=OUTPUT,
        evidence_level="handoff_metadata",
        hardware_scope="APdrone powertrain calibration packet",
        note="Exact YSIDO 2507 1800KV plus Foxeer Donut 5145 thrust/RPM/current curve was not found in the public sources already indexed here. Use direct APdrone PDF rows for max-thrust/current plausibility, Foxeer Donut image rows for prop-family k and Q/T sanity, and Tyto/MQTB rows as adjacent FPV-class envelopes.",
    )


def build_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    pdf_rows = read_rows(APDRONE_PDF)
    foxeer_rows = read_rows(FOXEER)
    tyto_power_rows = read_rows(TYTO_POWER)
    tyto_torque_rows = read_rows(TYTO_TORQUE)
    mqtb_rows = read_rows(MQTB)
    model_rows = read_rows(MODEL)

    project_model_rows(rows, model_rows)
    add_pdf_rows(rows, pdf_rows)
    add_foxeer_rows(rows, foxeer_rows)
    add_tyto_rows(rows, tyto_power_rows, tyto_torque_rows)
    add_mqtb_rows(rows, mqtb_rows)
    add_summary_rows(
        rows,
        pdf_rows=pdf_rows,
        foxeer_rows=foxeer_rows,
        power_rows=tyto_power_rows,
        torque_rows=tyto_torque_rows,
    )
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
