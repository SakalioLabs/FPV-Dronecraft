"""Build APdrone battery-resistance envelope rows from existing public data.

Inputs:
  docs/data/apdrone_battery_autonomy_reference.csv
  docs/data/apdrone_preset_source_match_reference.csv
  docs/data/high_c_lipo_reference.csv

Output:
  docs/data/apdrone_battery_resistance_envelope.csv
"""

from __future__ import annotations

import csv
import math
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
OUT = DATA / "apdrone_battery_resistance_envelope.csv"
APDRONE_SOURCE = "https://data.mendeley.com/datasets/zgsvdtxnfh/2"
DRONE_CONFIG_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneConfig.java"
FOXEER_REAPER_SOURCE = "https://www.foxeer.com/foxeer-reaper-f4-128k-65a-bl32-4in1-9-40v-esc-30-5-30-5mm-m3-g-420"
FULL_4S_CHARGE_V = 16.8
APDRONE_CELL_COUNT = 4
APDRONE_LOGGED_CURRENT_METER_SCALE = 400.0
FOXEER_OFFICIAL_CURRENT_SCALE = 70.0
BETAF_CENTIAMPS_PER_AMP = 100.0


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def f(value: str | float | int) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return float("nan")


def health_band(per_cell_ir_mohm: float) -> str:
    if per_cell_ir_mohm < 10.0:
        return "oscar_1500mah_great"
    if per_cell_ir_mohm < 15.0:
        return "oscar_1500mah_fine"
    if per_cell_ir_mohm < 20.0:
        return "oscar_1500mah_old"
    return "oscar_1500mah_retire"


def append_row(rows: list[dict[str, object]], **kwargs: object) -> None:
    rows.append(kwargs)


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


def source_match_value(rows: list[dict[str, str]], field: str) -> float:
    for row in rows:
        if row.get("field") == field:
            return f(row.get("project_value", ""))
    raise KeyError(field)


def scenario_summaries(rows: list[dict[str, str]]) -> dict[str, dict[str, str]]:
    summaries = {
        row["scenario"]: row
        for row in rows
        if row.get("row_type") == "apdrone_battery_autonomy_scenario_summary"
    }
    missing = {"normal_power", "max_power"} - set(summaries)
    if missing:
        raise RuntimeError(f"missing APdrone battery scenario summaries: {sorted(missing)}")
    return summaries


def current_from_raw_mean(summary: dict[str, str], raw_per_amp: float) -> float:
    return f(summary["amperage_raw_mean_mean"]) / raw_per_amp


def current_from_raw_p95(summary: dict[str, str], raw_per_amp: float) -> float:
    return f(summary["amperage_raw_p95_mean"]) / raw_per_amp


def main() -> None:
    autonomy = read_rows(DATA / "apdrone_battery_autonomy_reference.csv")
    source_match = read_rows(DATA / "apdrone_preset_source_match_reference.csv")
    high_c_lipo = read_rows(DATA / "high_c_lipo_reference.csv")
    summaries = scenario_summaries(autonomy)
    normal = summaries["normal_power"]
    max_power = summaries["max_power"]

    battery_r = source_match_value(source_match, "battery_internal_resistance_ohm")
    capacity_ah = source_match_value(source_match, "battery_capacity_ah")
    max_current_a = source_match_value(source_match, "max_battery_current_a")
    per_cell_ir_mohm = battery_r * 1000.0 / APDRONE_CELL_COUNT
    configured_sag_limit_v = battery_r * max_current_a
    rows: list[dict[str, object]] = []

    append_row(
        rows,
        row_type="apdrone_config_battery_resistance",
        source=DRONE_CONFIG_SOURCE,
        source_page=APDRONE_SOURCE,
        preset="apDrone",
        battery_pack_resistance_ohm=battery_r,
        cell_count=APDRONE_CELL_COUNT,
        per_cell_ir_mohm=per_cell_ir_mohm,
        capacity_ah=capacity_ah,
        max_current_a=max_current_a,
        current_limit_c=max_current_a / capacity_ah,
        sag_at_max_current_v=configured_sag_limit_v,
        sag_at_max_current_percent_full_4s=configured_sag_limit_v / FULL_4S_CHARGE_V * 100.0,
        oscar_1500mah_health_band=health_band(per_cell_ir_mohm),
        note="Current apDrone DroneConfig pack resistance expressed as FPV 4S per-cell IR and 1500mAh health band.",
    )

    measured_rows = [
        row
        for row in high_c_lipo
        if row.get("row_type") == "reference_fpv_lipo_measured_ir" and int(f(row.get("cells", ""))) == APDRONE_CELL_COUNT
    ]
    for measured in measured_rows:
        measured_pack_r = f(measured["pack_ir_ohm"])
        measured_sag_at_apdrone_limit = measured_pack_r * max_current_a
        measured_per_cell_ir = f(measured["per_cell_ir_mean_mohm"])
        append_row(
            rows,
            row_type="external_fpv_4s_measured_ir_anchor",
            source=measured["source"],
            reference_pack=measured["name"],
            capacity_ah=f(measured["capacity_ah"]),
            listed_c=f(measured["listed_c"]),
            listed_current_a=f(measured["listed_current_a"]),
            measured_per_cell_ir_mean_mohm=measured_per_cell_ir,
            measured_pack_resistance_ohm=measured_pack_r,
            measured_sag_at_apdrone_150a_v=measured_sag_at_apdrone_limit,
            measured_sag_at_apdrone_150a_percent_full_4s=measured_sag_at_apdrone_limit / FULL_4S_CHARGE_V * 100.0,
            apdrone_config_ir_over_measured_mean=per_cell_ir_mohm / measured_per_cell_ir if measured_per_cell_ir > 0.0 else "",
            apdrone_config_sag_minus_measured_sag_at_150a_v=configured_sag_limit_v - measured_sag_at_apdrone_limit,
            oscar_1500mah_health_band=measured.get("fpv_1500mah_ir_health_band", ""),
            note="Oscar Liang charger-IR row reused as an external FPV 4S absolute-resistance anchor. Charger IR is not lab DCIR.",
        )

    raw_per_amp_foxeer = BETAF_CENTIAMPS_PER_AMP * FOXEER_OFFICIAL_CURRENT_SCALE / APDRONE_LOGGED_CURRENT_METER_SCALE
    candidates = [
        {
            "name": "betaflight_literal_logged_centiamps",
            "normal_raw_per_amp": BETAF_CENTIAMPS_PER_AMP,
            "max_raw_per_amp": BETAF_CENTIAMPS_PER_AMP,
            "source": "https://github.com/betaflight/betaflight/blob/4.5.0/src/main/sensors/current.c#L115-L131",
            "note": "Literal Betaflight current unit, using logged amperageLatest/100 A. APdrone capacity integration shows this is too low for the 1500mAh battery logs.",
        },
        {
            "name": "foxeer_official_scale70_from_logged_scale400",
            "normal_raw_per_amp": raw_per_amp_foxeer,
            "max_raw_per_amp": raw_per_amp_foxeer,
            "source": FOXEER_REAPER_SOURCE,
            "note": "If the logs were recorded with currentMeterScale=400 but the Foxeer ESC hardware scale is 70, physical current is larger by 400/70; equivalent raw_per_amp is 17.5.",
        },
        {
            "name": "round_capacity_candidate_raw_per_amp20",
            "normal_raw_per_amp": 20.0,
            "max_raw_per_amp": 20.0,
            "source": APDRONE_SOURCE,
            "note": "Round APdrone capacity-consistency candidate used by the existing data packet.",
        },
        {
            "name": "scenario_capacity_match_1500mah",
            "normal_raw_per_amp": f(normal["raw_per_amp_to_integrate_1500mah_mean"]),
            "max_raw_per_amp": f(max_power["raw_per_amp_to_integrate_1500mah_mean"]),
            "source": APDRONE_SOURCE,
            "note": "Uses each scenario's mean raw_per_amp needed to integrate approximately one 1500mAh pack per flight.",
        },
    ]

    mean_v_normal = f(normal["vbat_mean_v_mean"])
    mean_v_max = f(max_power["vbat_mean_v_mean"])
    delta_v = mean_v_normal - mean_v_max

    for candidate in candidates:
        normal_raw_per_amp = float(candidate["normal_raw_per_amp"])
        max_raw_per_amp = float(candidate["max_raw_per_amp"])
        normal_current = current_from_raw_mean(normal, normal_raw_per_amp)
        max_current = current_from_raw_mean(max_power, max_raw_per_amp)
        delta_current = max_current - normal_current
        inferred_r = delta_v / delta_current if delta_current > 0.0 else float("nan")
        inferred_per_cell_ir = inferred_r * 1000.0 / APDRONE_CELL_COUNT if math.isfinite(inferred_r) else float("nan")
        append_row(
            rows,
            row_type="apdrone_log_cross_scenario_resistance_proxy",
            source=candidate["source"],
            source_page=APDRONE_SOURCE,
            candidate=candidate["name"],
            normal_raw_per_amp=normal_raw_per_amp,
            max_raw_per_amp=max_raw_per_amp,
            normal_power_mean_current_a=normal_current,
            max_power_mean_current_a=max_current,
            delta_current_a=delta_current,
            normal_power_mean_v=mean_v_normal,
            max_power_mean_v=mean_v_max,
            delta_mean_v=delta_v,
            inferred_pack_resistance_ohm=inferred_r,
            inferred_per_cell_ir_mohm=inferred_per_cell_ir,
            inferred_oscar_1500mah_health_band=health_band(inferred_per_cell_ir) if math.isfinite(inferred_per_cell_ir) else "",
            apdrone_config_resistance_over_inferred=battery_r / inferred_r if inferred_r > 0.0 else "",
            apdrone_config_predicted_delta_sag_v=battery_r * delta_current,
            observed_delta_v_over_config_predicted_delta_sag=delta_v / (battery_r * delta_current) if battery_r * delta_current > 0.0 else "",
            note=candidate["note"] + " Cross-scenario proxy is not lab ESR; it couples SOC, heating, and different flight profiles.",
        )

        for scenario_name, summary, raw_per_amp in (
            ("normal_power", normal, normal_raw_per_amp),
            ("max_power", max_power, max_raw_per_amp),
        ):
            mean_current = current_from_raw_mean(summary, raw_per_amp)
            p95_current = current_from_raw_p95(summary, raw_per_amp)
            start_drop = FULL_4S_CHARGE_V - f(summary["vbat_start_v_mean"])
            start_proxy_r = start_drop / mean_current if mean_current > 0.0 else float("nan")
            start_proxy_per_cell_ir = start_proxy_r * 1000.0 / APDRONE_CELL_COUNT if math.isfinite(start_proxy_r) else float("nan")
            append_row(
                rows,
                row_type="apdrone_log_start_voltage_drop_proxy",
                source=candidate["source"],
                source_page=APDRONE_SOURCE,
                candidate=candidate["name"],
                scenario=scenario_name,
                raw_per_amp=raw_per_amp,
                mean_current_a=mean_current,
                p95_current_a=p95_current,
                vbat_start_mean_v=f(summary["vbat_start_v_mean"]),
                full_4s_minus_start_v=start_drop,
                start_drop_pack_resistance_proxy_ohm=start_proxy_r,
                start_drop_per_cell_ir_proxy_mohm=start_proxy_per_cell_ir,
                start_drop_oscar_1500mah_health_band=health_band(start_proxy_per_cell_ir) if math.isfinite(start_proxy_per_cell_ir) else "",
                apdrone_config_resistance_over_start_drop_proxy=battery_r / start_proxy_r if start_proxy_r > 0.0 else "",
                config_sag_at_mean_current_v=battery_r * mean_current,
                config_sag_at_p95_current_v=battery_r * p95_current,
                note="Start-voltage proxy divides full-charge-minus-start voltage by scenario mean current; it is very sensitive to initial SOC and current sensor scale.",
            )

    write_rows(rows)
    print(f"Wrote {OUT.relative_to(ROOT).as_posix()} with {len(rows)} rows")


if __name__ == "__main__":
    main()
