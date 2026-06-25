"""Encode LiPo ESR temperature/SOC method rules and current preset checks.

Outputs:
  docs/data/lipo_esr_temperature_soc_method_reference.csv

This is a method/guardrail table, not a replacement for direct high-C FPV
ESR-vs-temperature lab data. It records public ESR-meter guidance about
temperature, SOC, and the common IR-based "realistic C" estimate, then applies
that conservative estimate to the current project battery fields.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
OUTPUT = DATA / "lipo_esr_temperature_soc_method_reference.csv"
HIGH_C = DATA / "high_c_lipo_reference.csv"
APDRONE_MODEL = DATA / "apdrone_flight_vs_model_reference.csv"

ESR_METER_GUIDE_URL = "https://www.baronerosso.it/forum/attachments/batterie-e-caricabatterie/386965d1652293513-calcolo-c-di-scarica-lipo-meter.pdf"
RC_HELICOPTER_FUN_URL = "https://www.rchelicopterfun.com/lipo-internal-resistance.html"
OSCAR_RETIRE_LIPO_URL = "https://oscarliang.com/when-retire-lipo-battery/"

TRUE_C_CONSTANT = 2500.0
TEMPERATURE_REFERENCE_C = 23.0
TEMPERATURE_REFERENCE_F = 72.0


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def value_text(value: float) -> str:
    if not math.isfinite(value):
        return ""
    return f"{value:.12g}"


def add_metric(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: float,
    unit: str,
    source: str,
    note: str,
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": value_text(value),
            "unit": unit,
            "source": source,
            "note": note,
        }
    )


def add_note(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    source: str,
    note: str,
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": "",
            "unit": "",
            "source": source,
            "note": note,
        }
    )


def true_c_from_ir(capacity_mah: float, highest_cell_ir_mohm: float) -> float:
    return TRUE_C_CONSTANT / math.sqrt(capacity_mah * highest_cell_ir_mohm)


def add_method_rows(rows: list[dict[str, str]]) -> None:
    add_metric(
        rows,
        row_type="lipo_esr_method_rule",
        name="esr_meter_reference_temperature",
        metric="reference_temperature_c",
        value=TEMPERATURE_REFERENCE_C,
        unit="C",
        source=ESR_METER_GUIDE_URL,
        note="ESR meter guidance standardizes practical pack comparisons near 72 F / 23 C.",
    )
    add_metric(
        rows,
        row_type="lipo_esr_method_rule",
        name="esr_meter_reference_temperature",
        metric="reference_temperature_f",
        value=TEMPERATURE_REFERENCE_F,
        unit="F",
        source=ESR_METER_GUIDE_URL,
        note="ESR meter guidance standardizes practical pack comparisons near 72 F / 23 C.",
    )
    add_metric(
        rows,
        row_type="lipo_esr_method_rule",
        name="temperature_drop_rule_of_thumb",
        metric="ir_ratio_for_10c_drop_about",
        value=2.0,
        unit="x",
        source=ESR_METER_GUIDE_URL,
        note="The guide states a 10 C temperature fall can nearly double internal resistance; use as a coarse cold-sag guardrail.",
    )
    add_metric(
        rows,
        row_type="lipo_esr_method_rule",
        name="temperature_20c_vs_30c_rule",
        metric="ir_difference_min",
        value=0.5,
        unit="fraction",
        source=ESR_METER_GUIDE_URL,
        note="The guide says the same LiPo cell can show more than a 50 percent IR difference between 20 C and 30 C.",
    )
    add_metric(
        rows,
        row_type="lipo_esr_method_rule",
        name="soc_rule_of_thumb",
        metric="low_soc_transition",
        value=0.10,
        unit="SOC fraction",
        source=ESR_METER_GUIDE_URL,
        note="The guide says IR changes only slightly down to about 10 percent charge and then rises rapidly, so SOC and temperature should not be conflated.",
    )
    add_metric(
        rows,
        row_type="lipo_esr_true_c_formula",
        name="ir_based_realistic_c_formula",
        metric="constant",
        value=TRUE_C_CONSTANT,
        unit="sqrt(mAh*mOhm)",
        source=RC_HELICOPTER_FUN_URL,
        note="Formula: true C = 2500 / sqrt(capacity_mAh * highest_cell_IR_mOhm). Use configured mean per-cell IR only as a proxy when max-cell IR is unavailable.",
    )
    add_note(
        rows,
        row_type="lipo_esr_method_caveat",
        name="scope",
        metric="guardrail_not_lab_curve",
        source=ESR_METER_GUIDE_URL,
        note="These rows provide measurement-method and conservative current guardrails. They do not fill the remaining need for direct high-C FPV pack ESR sweeps versus temperature and SOC.",
    )
    add_note(
        rows,
        row_type="lipo_esr_method_caveat",
        name="oscar_1500mah_health_bands",
        metric="cross_reference",
        source=OSCAR_RETIRE_LIPO_URL,
        note="Oscar Liang's FPV 1500 mAh per-cell IR health bands remain useful absolute room-condition anchors; this file adds temperature/SOC and IR-to-current method checks.",
    )


def current_battery_rows() -> list[dict[str, str | float]]:
    rows: list[dict[str, str | float]] = []
    for row in read_rows(HIGH_C):
        if row.get("row_type") != "current_preset_battery":
            continue
        rows.append(
            {
                "name": row["name"],
                "source": row["source"],
                "cells": float(row["cells"]),
                "capacity_ah": float(row["capacity_ah"]),
                "nominal_v": float(row["nominal_v"]),
                "pack_resistance_ohm": float(row["pack_resistance_ohm"]),
                "per_cell_ir_mohm": float(row["per_cell_ir_mohm"]),
                "max_current_a": float(row["max_current_a"]),
                "current_limit_c": float(row["current_limit_c"]),
            }
        )
    for row in read_rows(APDRONE_MODEL):
        if row.get("row_type") != "project_preset_model" or row.get("preset") != "apDrone":
            continue
        nominal_v = float(row["nominal_battery_voltage_v"])
        cells = round(nominal_v / 4.2)
        pack_r = float(row["battery_internal_resistance_ohm"])
        capacity = float(row["battery_capacity_ah"])
        max_current = float(row["max_battery_current_a"])
        rows.append(
            {
                "name": "apDrone",
                "source": row.get("source_page", repo_path(APDRONE_MODEL)),
                "cells": float(cells),
                "capacity_ah": capacity,
                "nominal_v": nominal_v,
                "pack_resistance_ohm": pack_r,
                "per_cell_ir_mohm": pack_r * 1000.0 / cells,
                "max_current_a": max_current,
                "current_limit_c": max_current / capacity,
            }
        )
    return rows


def add_formula_checks(rows: list[dict[str, str]], batteries: Iterable[dict[str, str | float]]) -> None:
    for battery in batteries:
        name = str(battery["name"])
        cells = float(battery["cells"])
        capacity_ah = float(battery["capacity_ah"])
        capacity_mah = capacity_ah * 1000.0
        nominal_v = float(battery["nominal_v"])
        pack_r = float(battery["pack_resistance_ohm"])
        per_cell_ir = float(battery["per_cell_ir_mohm"])
        max_current = float(battery["max_current_a"])
        configured_c = float(battery["current_limit_c"])
        true_c = true_c_from_ir(capacity_mah, per_cell_ir)
        true_current = true_c * capacity_ah
        cold_true_c = true_c / math.sqrt(2.0)
        cold_true_current = cold_true_c * capacity_ah
        sag_at_true = true_current * pack_r
        sag_at_config = max_current * pack_r
        source = str(battery["source"])
        note = "IR-based C formula uses configured mean per-cell IR as a max-cell proxy; real packs should use the highest measured cell IR at matched temperature."
        metrics = {
            "cells": (cells, "count"),
            "capacity_mah": (capacity_mah, "mAh"),
            "configured_per_cell_ir": (per_cell_ir, "mOhm/cell"),
            "configured_current_limit_c": (configured_c, "C"),
            "ir_formula_true_c": (true_c, "C"),
            "ir_formula_true_current_a": (true_current, "A"),
            "configured_current_over_ir_formula_current": (max_current / true_current, "x"),
            "sag_at_ir_formula_current_v": (sag_at_true, "V"),
            "sag_at_ir_formula_current_percent_nominal": (100.0 * sag_at_true / nominal_v, "percent"),
            "sag_at_configured_current_v": (sag_at_config, "V"),
            "sag_at_configured_current_percent_nominal": (100.0 * sag_at_config / nominal_v, "percent"),
            "cold_10c_drop_ir_doubled_true_current_a": (cold_true_current, "A"),
            "configured_current_over_cold_10c_drop_current": (max_current / cold_true_current, "x"),
        }
        for metric, (value, unit) in metrics.items():
            add_metric(
                rows,
                row_type="lipo_esr_current_preset_formula_check",
                name=name,
                metric=metric,
                value=value,
                unit=unit,
                source=source,
                note=note,
            )


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
    rows: list[dict[str, str]] = []
    add_method_rows(rows)
    add_formula_checks(rows, current_battery_rows())
    write_csv(OUTPUT, rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")


if __name__ == "__main__":
    main()
