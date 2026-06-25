#!/usr/bin/env python3
"""Build a compact FPV LiPo ESR calibration handoff packet.

Outputs:
  docs/data/fpv_lipo_esr_calibration_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category fpv_lipo_esr_packet_*

This script intentionally does not download new large files. It combines the
existing public-data products into one handoff layer for runtime battery tuning:

* FPV charger-IR anchors from high_c_lipo_reference.csv
* Mendeley LiPo ECM R0(SOC,SOH) shape projected onto project pack ESR
* C-rate/temperature shape rows from the Mendeley discharge dataset packet
* RC LiPo temperature-ratio guardrails and current Java temperature rows
* conservative IR-based "true C" checks from the ESR method table
"""

from __future__ import annotations

import csv
import math
import statistics
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

HIGH_C = DATA / "high_c_lipo_reference.csv"
METHOD = DATA / "lipo_esr_temperature_soc_method_reference.csv"
PROJECTION = DATA / "lipo_ecm_mendeley_runtime_esr_projection.csv"
CRATE_PACKET = DATA / "lipo_c_rate_temperature_calibration_packet.csv"
TEMPERATURE = DATA / "battery_temperature_derating_summary.csv"
PACKET = DATA / "fpv_lipo_esr_calibration_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

MENDELEY_ECM_URL = "https://data.mendeley.com/datasets/stcppt2r68/1"
MENDELEY_CRATE_URL = "https://data.mendeley.com/datasets/kxsbr4x3j2/2"
PMC_LIPO_EIS_URL = "https://pmc.ncbi.nlm.nih.gov/articles/PMC10518458/"

SCENARIOS = {
    ("fresh_ge_0.95", 1.0): "fresh_full",
    ("fresh_ge_0.95", 0.1): "fresh_10pct",
    ("used_0.85_0.95", 0.5): "used_50pct",
    ("aged_0.75_0.85", 0.5): "aged_50pct",
    ("worn_lt_0.75", 1.0): "worn_full",
    ("worn_lt_0.75", 0.1): "worn_10pct",
}

PROJECTION_METRICS = [
    ("mendeley_r0_scale_median", "x"),
    ("current_code_total_scale_vs_fresh_high_soc", "x"),
    ("current_code_total_over_mendeley_median", "x"),
    ("projected_pack_resistance_ohm", "ohm"),
    ("projected_per_cell_resistance_mohm", "mOhm/cell"),
    ("config_current_sag_v", "V"),
    ("config_current_sag_percent_nominal", "percent"),
    ("loaded_voltage_at_config_current_v", "V"),
    ("config_current_for_20pct_nominal_sag_a", "A"),
    ("config_current_over_20pct_sag_current", "x"),
    ("usable_current_before_empty_voltage_a", "A"),
]

PRESET_METRICS = [
    ("cells", "count"),
    ("nominal_v", "V"),
    ("capacity_ah", "Ah"),
    ("max_current_a", "A"),
    ("current_limit_c", "C"),
    ("pack_resistance_ohm", "ohm"),
    ("per_cell_ir_mohm", "mOhm/cell"),
    ("sag_at_current_limit_v", "V"),
    ("sag_at_current_limit_percent_nominal", "percent"),
]

MEASURED_FPV_METRICS = [
    ("cells", "count"),
    ("capacity_ah", "Ah"),
    ("listed_c", "C"),
    ("listed_current_a", "A"),
    ("per_cell_ir_mean_mohm", "mOhm/cell"),
    ("per_cell_ir_min_mohm", "mOhm/cell"),
    ("per_cell_ir_max_mohm", "mOhm/cell"),
    ("cell_ir_spread_mohm", "mOhm/cell"),
    ("cell_ir_cv_percent", "percent"),
    ("pack_ir_mohm", "mOhm"),
    ("pack_ir_ohm", "ohm"),
    ("sag_at_90a_v", "V"),
]

VS_MEASURED_METRICS = [
    ("preset_per_cell_ir_mohm", "mOhm/cell"),
    ("measured_per_cell_ir_mean_mohm", "mOhm/cell"),
    ("preset_ir_over_measured_mean", "x"),
    ("measured_pack_ir_mohm", "mOhm"),
    ("preset_pack_sag_at_limit_v", "V"),
    ("measured_pack_sag_at_preset_limit_v", "V"),
    ("measured_minus_preset_sag_v", "V"),
    ("measured_sag_at_preset_limit_percent_nominal", "percent"),
]

FORMULA_METRICS = [
    ("configured_per_cell_ir", "mOhm/cell"),
    ("configured_current_limit_c", "C"),
    ("ir_formula_true_c", "C"),
    ("ir_formula_true_current_a", "A"),
    ("configured_current_over_ir_formula_current", "x"),
    ("sag_at_ir_formula_current_v", "V"),
    ("sag_at_configured_current_v", "V"),
    ("sag_at_configured_current_percent_nominal", "percent"),
    ("cold_10c_drop_ir_doubled_true_current_a", "A"),
    ("configured_current_over_cold_10c_drop_current", "x"),
]

CRATE_SUMMARY_METRICS = {
    "selected_curve_count",
    "selected_file_size_mb",
    "max_selected_c_rate_nca_nmc",
    "max_selected_c_rate_lfp",
    "racingQuad_configured_c_over_nca_nmc_selected_max",
    "racingQuad_configured_c_over_lfp_selected_max",
    "max_surface_temp_rise_c",
    "max_surface_temp_rise_curve",
    "min_capacity_ratio_vs_25c",
    "min_capacity_ratio_curve",
    "max_5c_initial_resistance_ratio_vs_25c",
    "max_5c_initial_resistance_ratio_curve",
}

TEMPERATURE_MODEL_POINTS = {-20.0, 0.0, 25.0, 70.0, 100.0}
TEMPERATURE_MODEL_METRICS = [
    ("resistance_scale", "x"),
    ("current_scale", "x"),
    ("thermal_power_limit", "x"),
    ("effective_resistance_ohm", "ohm"),
    ("effective_current_limit_a", "A"),
    ("sag_at_temperature_scaled_limit_v", "V"),
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


def as_float(value: object) -> float:
    try:
        if value is None or value == "":
            return math.nan
        return float(value)
    except (TypeError, ValueError):
        return math.nan


def value_text(value: object) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    if isinstance(value, (int, float)):
        number = float(value)
        if not math.isfinite(number):
            return ""
        return f"{number:.12g}"
    return str(value)


def add_metric(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: str,
    source_url: str = "",
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
            "source_file": source_file,
            "source_url": source_url,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def source_or_file(row: dict[str, str], path: Path) -> tuple[str, str]:
    source = row.get("source", "")
    if source.startswith("http"):
        return repo_path(path), source
    return source or repo_path(path), ""


def add_source_inventory(rows: list[dict[str, str]]) -> None:
    sources = [
        (
            "high_c_lipo_reference",
            HIGH_C,
            "",
            "FPV pack product specs, FPV charger-IR anchors, and current preset battery fields.",
        ),
        (
            "mendeley_lipo_ecm_projection",
            PROJECTION,
            MENDELEY_ECM_URL,
            "Projected R0(SOC,SOH) shape onto current project pack resistance.",
        ),
        (
            "mendeley_lipo_ecm_article",
            Path("external"),
            PMC_LIPO_EIS_URL,
            "Article describing the LiPo EIS/capacity/ECM dataset; shape source, not FPV absolute ESR.",
        ),
        (
            "lipo_esr_method_reference",
            METHOD,
            "",
            "ESR temperature/SOC method guardrails and conservative IR-based current formula.",
        ),
        (
            "mendeley_c_rate_temperature_packet",
            CRATE_PACKET,
            MENDELEY_CRATE_URL,
            "C-rate/temperature discharge-shape packet; cylindrical cells, not FPV pouch absolute ESR.",
        ),
        (
            "battery_temperature_derating_summary",
            TEMPERATURE,
            "",
            "Current Java battery-temperature model plus RC LiPo cold/warm field ratios.",
        ),
    ]
    for name, path, url, note in sources:
        source_file = repo_path(path) if path != Path("external") else ""
        add_metric(
            rows,
            row_type="fpv_lipo_esr_packet_source_inventory",
            name=name,
            metric="source",
            value=source_file or url,
            unit="text",
            source_file=source_file,
            source_url=url,
            evidence_role="source_inventory",
            note=note,
        )


def add_fpv_measured_anchors(rows: list[dict[str, str]], high_c_rows: list[dict[str, str]]) -> None:
    measured = [row for row in high_c_rows if row.get("row_type") == "reference_fpv_lipo_measured_ir"]
    for row in measured:
        source_file, source_url = source_or_file(row, HIGH_C)
        for metric, unit in MEASURED_FPV_METRICS:
            add_metric(
                rows,
                row_type="fpv_lipo_esr_packet_measured_ir_anchor",
                name=row["name"],
                metric=metric,
                value=row.get(metric, ""),
                unit=unit,
                source_file=source_file,
                source_url=source_url,
                evidence_role="absolute_fpv_room_condition_ir_anchor",
                note="Charger-reported 4S FPV pack IR after charge; useful absolute scale anchor, not lab EIS or a SOC/temperature sweep.",
            )

    per_cell = [as_float(row.get("per_cell_ir_mean_mohm")) for row in measured]
    per_cell = [value for value in per_cell if math.isfinite(value)]
    pack_ir = [as_float(row.get("pack_ir_mohm")) for row in measured]
    pack_ir = [value for value in pack_ir if math.isfinite(value)]
    sag_90 = [as_float(row.get("sag_at_90a_v")) for row in measured]
    sag_90 = [value for value in sag_90 if math.isfinite(value)]

    summary_metrics = [
        ("measured_pack_count", len(measured), "count"),
        ("measured_per_cell_ir_min_mohm", min(per_cell), "mOhm/cell"),
        ("measured_per_cell_ir_median_mohm", statistics.median(per_cell), "mOhm/cell"),
        ("measured_per_cell_ir_max_mohm", max(per_cell), "mOhm/cell"),
        ("measured_pack_ir_min_mohm", min(pack_ir), "mOhm"),
        ("measured_pack_ir_max_mohm", max(pack_ir), "mOhm"),
        ("measured_sag_at_90a_min_v", min(sag_90), "V"),
        ("measured_sag_at_90a_max_v", max(sag_90), "V"),
    ]
    for metric, value, unit in summary_metrics:
        add_metric(
            rows,
            row_type="fpv_lipo_esr_packet_summary",
            name="fpv_measured_4s_room_ir_range",
            metric=metric,
            value=value,
            unit=unit,
            source_file=repo_path(HIGH_C),
            source_url="https://oscarliang.com/acehe-formula-4s-95c-lipo-batteries/",
            evidence_role="absolute_fpv_ir_range_summary",
            note="Range over the local FPV 4S charger-IR rows.",
        )


def add_current_preset_checks(rows: list[dict[str, str]], high_c_rows: list[dict[str, str]]) -> None:
    for row in high_c_rows:
        if row.get("row_type") != "current_preset_battery":
            continue
        source_file, source_url = source_or_file(row, HIGH_C)
        for metric, unit in PRESET_METRICS:
            add_metric(
                rows,
                row_type="fpv_lipo_esr_packet_current_preset_absolute_check",
                name=row["name"],
                metric=metric,
                value=row.get(metric, ""),
                unit=unit,
                source_file=source_file,
                source_url=source_url,
                evidence_role="current_project_absolute_esr",
                note="Current DroneConfig battery fields converted to pack/cell IR and max-current sag.",
            )

    for row in high_c_rows:
        if row.get("row_type") != "current_vs_measured_fpv_lipo_ir":
            continue
        source_file, source_url = source_or_file(row, HIGH_C)
        for metric, unit in VS_MEASURED_METRICS:
            add_metric(
                rows,
                row_type="fpv_lipo_esr_packet_current_vs_measured_ir",
                name=row["name"],
                metric=metric,
                value=row.get(metric, ""),
                unit=unit,
                source_file=source_file,
                source_url=source_url,
                evidence_role="current_vs_fpv_absolute_ir_anchor",
                note="Current preset pack ESR compared with a public FPV charger-IR row at the same cell count.",
            )


def add_formula_guardrails(rows: list[dict[str, str]], method_rows: list[dict[str, str]]) -> None:
    for row in method_rows:
        row_type = row.get("row_type", "")
        if row_type == "lipo_esr_current_preset_formula_check" and row.get("metric") in {m for m, _ in FORMULA_METRICS}:
            unit = next(unit for metric, unit in FORMULA_METRICS if metric == row["metric"])
            source_file, source_url = source_or_file(row, METHOD)
            add_metric(
                rows,
                row_type="fpv_lipo_esr_packet_current_formula_guardrail",
                name=row["name"],
                metric=row["metric"],
                value=row.get("value", ""),
                unit=unit,
                source_file=source_file,
                source_url=source_url,
                evidence_role="conservative_ir_based_current_guardrail",
                note="IR-based C formula uses configured mean cell IR as a proxy; real packs should use max measured cell IR at matched temperature.",
            )
        elif row_type in {"lipo_esr_method_rule", "lipo_esr_true_c_formula", "lipo_esr_method_caveat"}:
            source_file, source_url = source_or_file(row, METHOD)
            add_metric(
                rows,
                row_type="fpv_lipo_esr_packet_method_guardrail",
                name=row["name"],
                metric=row["metric"],
                value=row.get("value", ""),
                unit=row.get("unit", ""),
                source_file=source_file,
                source_url=source_url,
                evidence_role="measurement_method_guardrail",
                note=row.get("note", ""),
            )


def scenario_name(row: dict[str, str]) -> str | None:
    soh = row.get("soh_band", "")
    soc = round(as_float(row.get("soc_bin")), 1)
    return SCENARIOS.get((soh, soc))


def add_projection_scenarios(rows: list[dict[str, str]], projection_rows: list[dict[str, str]]) -> None:
    for row in projection_rows:
        scenario = scenario_name(row)
        if scenario is None:
            continue
        name = f"{row['preset']}_{scenario}"
        for metric, unit in PROJECTION_METRICS:
            add_metric(
                rows,
                row_type="fpv_lipo_esr_packet_soc_soh_runtime_projection",
                name=name,
                metric=metric,
                value=row.get(metric, ""),
                unit=unit,
                source_file=repo_path(PROJECTION),
                source_url=row.get("source", MENDELEY_ECM_URL),
                evidence_role="soc_soh_shape_projected_to_current_esr",
                note="Selected high-signal SOC/SOH scenario from the full Mendeley R0 shape projection.",
            )

    wanted = {
        ("racingQuad", "fresh_full"),
        ("racingQuad", "worn_10pct"),
        ("cinewhoop", "fresh_full"),
        ("cinewhoop", "worn_10pct"),
    }
    by_key = {
        (row["preset"], scenario_name(row)): row
        for row in projection_rows
        if scenario_name(row) is not None
    }
    for preset, scenario in sorted(wanted):
        row = by_key.get((preset, scenario))
        if not row:
            continue
        for metric in [
            "projected_pack_resistance_ohm",
            "projected_per_cell_resistance_mohm",
            "config_current_sag_v",
            "config_current_for_20pct_nominal_sag_a",
            "config_current_over_20pct_sag_current",
        ]:
            unit = next(unit for name, unit in PROJECTION_METRICS if name == metric)
            add_metric(
                rows,
                row_type="fpv_lipo_esr_packet_summary",
                name=f"{preset}_{scenario}_projection",
                metric=metric,
                value=row.get(metric, ""),
                unit=unit,
                source_file=repo_path(PROJECTION),
                source_url=row.get("source", MENDELEY_ECM_URL),
                evidence_role="projection_summary",
                note="Compact selected projection; use full projection CSV for all SOC bins.",
            )


def add_temperature_guardrails(
    rows: list[dict[str, str]],
    crate_rows: list[dict[str, str]],
    temperature_rows: list[dict[str, str]],
) -> None:
    for row in crate_rows:
        if row.get("row_type") != "lipo_crate_packet_summary":
            continue
        if row.get("metric") not in CRATE_SUMMARY_METRICS:
            continue
        add_metric(
            rows,
            row_type="fpv_lipo_esr_packet_c_rate_temperature_shape",
            name=row["name"],
            metric=row["metric"],
            value=row.get("value", ""),
            unit=row.get("unit", ""),
            source_file=repo_path(CRATE_PACKET),
            source_url=row.get("source_url", MENDELEY_CRATE_URL),
            evidence_role="c_rate_temperature_shape_guardrail",
            note="Cylindrical-cell discharge-shape summary; do not use as FPV pouch-pack absolute ESR.",
        )

    for row in temperature_rows:
        if row.get("preset") == "racingQuad" and row.get("row_type", "") == "":
            temp = as_float(row.get("battery_temperature_c"))
            if temp not in TEMPERATURE_MODEL_POINTS:
                continue
            name = f"racingQuad_current_temperature_model_{temp:g}C"
            for metric, unit in TEMPERATURE_MODEL_METRICS:
                add_metric(
                    rows,
                    row_type="fpv_lipo_esr_packet_temperature_model_check",
                    name=name,
                    metric=metric,
                    value=row.get(metric, ""),
                    unit=unit,
                    source_file=repo_path(TEMPERATURE),
                    source_url=row.get("source", ""),
                    evidence_role="current_temperature_derating_check",
                    note="Current Java temperature derating scan for racingQuad.",
                )
        elif row.get("row_type") == "current_vs_lipo_esr_temperature_test":
            for metric, unit in [
                ("reference_cold_over_warm_ir_ratio", "x"),
                ("current_cold_over_warm_resistance_scale", "x"),
                ("current_over_reference_ratio", "x"),
            ]:
                add_metric(
                    rows,
                    row_type="fpv_lipo_esr_packet_temperature_reference_check",
                    name=row["name"],
                    metric=metric,
                    value=row.get(metric, ""),
                    unit=unit,
                    source_file=repo_path(TEMPERATURE),
                    source_url=row.get("source", ""),
                    evidence_role="rc_lipo_temperature_ratio_anchor",
                    note="Jeffco RC LiPo field temperature ratio compared with the current model; not lab EIS.",
                )

    ratios = [
        as_float(row.get("reference_cold_over_warm_ir_ratio"))
        for row in temperature_rows
        if row.get("row_type") == "current_vs_lipo_esr_temperature_test"
    ]
    ratios = [value for value in ratios if math.isfinite(value)]
    current_over = [
        as_float(row.get("current_over_reference_ratio"))
        for row in temperature_rows
        if row.get("row_type") == "current_vs_lipo_esr_temperature_test"
    ]
    current_over = [value for value in current_over if math.isfinite(value)]
    for metric, value, unit in [
        ("jeffco_reference_cold_over_warm_ir_min", min(ratios), "x"),
        ("jeffco_reference_cold_over_warm_ir_max", max(ratios), "x"),
        ("current_model_over_jeffco_min", min(current_over), "x"),
        ("current_model_over_jeffco_max", max(current_over), "x"),
    ]:
        add_metric(
            rows,
            row_type="fpv_lipo_esr_packet_summary",
            name="temperature_ratio_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=repo_path(TEMPERATURE),
            source_url="https://jeffcoaeromodlers.com/wp-content/uploads/2019/05/Lithium-Polymer-Batteries-96-website.pdf",
            evidence_role="temperature_guardrail_summary",
            note="Cold/warm total-IR ratio summary across Jeffco RC LiPo rows.",
        )


def add_high_level_summaries(
    rows: list[dict[str, str]],
    high_c_rows: list[dict[str, str]],
    method_rows: list[dict[str, str]],
) -> None:
    measured = [row for row in high_c_rows if row.get("row_type") == "reference_fpv_lipo_measured_ir"]
    racing = next(row for row in high_c_rows if row.get("row_type") == "current_preset_battery" and row["name"] == "racingQuad")
    measured_min = min(as_float(row.get("per_cell_ir_mean_mohm")) for row in measured)
    racing_cell_ir = as_float(racing.get("per_cell_ir_mohm"))
    add_metric(
        rows,
        row_type="fpv_lipo_esr_packet_summary",
        name="racingQuad_absolute_ir_position",
        metric="configured_per_cell_ir_over_lowest_measured_fpv_anchor",
        value=racing_cell_ir / measured_min,
        unit="x",
        source_file=repo_path(HIGH_C),
        source_url="https://oscarliang.com/acehe-formula-4s-95c-lipo-batteries/",
        evidence_role="absolute_ir_position_summary",
        note="Values below 1 mean the configured fresh-pack ESR is lower than the lowest local public FPV charger-IR anchor.",
    )

    formula: dict[tuple[str, str], str] = {}
    for row in method_rows:
        if row.get("row_type") == "lipo_esr_current_preset_formula_check":
            formula[(row["name"], row["metric"])] = row.get("value", "")
    for preset in ["racingQuad", "apDrone"]:
        for metric, unit in [
            ("ir_formula_true_current_a", "A"),
            ("configured_current_over_ir_formula_current", "x"),
            ("configured_current_over_cold_10c_drop_current", "x"),
        ]:
            add_metric(
                rows,
                row_type="fpv_lipo_esr_packet_summary",
                name=f"{preset}_ir_formula_guardrail",
                metric=metric,
                value=formula.get((preset, metric), ""),
                unit=unit,
                source_file=repo_path(METHOD),
                source_url="https://www.rchelicopterfun.com/lipo-internal-resistance.html",
                evidence_role="formula_guardrail_summary",
                note="Conservative sustained-current warning; not a hard burst-current cap.",
            )

    add_metric(
        rows,
        row_type="fpv_lipo_esr_packet_method",
        name="calibration_policy",
        metric="recommended_use",
        value=(
            "Use FPV charger-IR rows to anchor absolute room-condition pack ESR; use Mendeley LiPo ECM rows "
            "only for SOC/SOH shape; use C-rate/temperature and Jeffco rows only for limiter/temperature shape. "
            "Do not infer ESR directly from manufacturer C-rating."
        ),
        unit="text",
        source_file=repo_path(PACKET),
        source_url="",
        evidence_role="handoff_policy",
        note="This row is intentionally prescriptive for the coding agent.",
    )


def sync_summary(packet_rows: Iterable[dict[str, str]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("fpv_lipo_esr_packet_")]
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
    high_c_rows = read_rows(HIGH_C)
    method_rows = read_rows(METHOD)
    projection_rows = read_rows(PROJECTION)
    crate_rows = read_rows(CRATE_PACKET)
    temperature_rows = read_rows(TEMPERATURE)

    packet_rows: list[dict[str, str]] = []
    add_source_inventory(packet_rows)
    add_fpv_measured_anchors(packet_rows, high_c_rows)
    add_current_preset_checks(packet_rows, high_c_rows)
    add_formula_guardrails(packet_rows, method_rows)
    add_projection_scenarios(packet_rows, projection_rows)
    add_temperature_guardrails(packet_rows, crate_rows, temperature_rows)
    add_high_level_summaries(packet_rows, high_c_rows, method_rows)

    write_csv(PACKET, packet_rows)
    synced = sync_summary(packet_rows)
    print(f"Wrote {repo_path(PACKET)} with {len(packet_rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
