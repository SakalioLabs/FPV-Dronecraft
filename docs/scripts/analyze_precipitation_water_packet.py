"""Build a rain, wet-prop, and water-immersion calibration packet.

Outputs:
  docs/data/precipitation_water_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category precip_water_packet_*

The source CSV is generated from NWS rain-rate examples, Met Office rainfall
measurement context, NIST water-density context, CIRES vapor-pressure guidance,
ICAS 2020 heavy-rain CFD rows, and current project water/rain formulas. This
packet narrows those rows into one handoff table and adds a live source audit
for the current Java precipitation thrust-loss formula, because that formula may
move independently while the wide generated CSV awaits regeneration.
"""

from __future__ import annotations

import csv
import math
import re
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

OUTPUT = DATA / "precipitation_water_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

PRECIP_REFERENCE = DATA / "precipitation_water_effect_reference.csv"
DRONE_PHYSICS = ROOT / "drone-sim-core" / "src" / "main" / "java" / "com" / "tenicana" / "dronecraft" / "sim" / "DronePhysics.java"
DRONE_ENVIRONMENT = ROOT / "drone-sim-core" / "src" / "main" / "java" / "com" / "tenicana" / "dronecraft" / "sim" / "DroneEnvironment.java"

NWS_RAIN_URL = "https://www.weather.gov/lox/rainrate"
MET_OFFICE_RAIN_URL = "https://www.metoffice.gov.uk/weather/guides/observations/how-we-measure-rainfall"
NIST_WATER_URL = "https://webbook.nist.gov/cgi/fluid.cgi?ID=C7732185&Action=Page"
CIRES_VAPOR_URL = "https://cires1.colorado.edu/~voemel/vp.html"
ICAS_GUST_RAIN_PDF_URL = "https://www.icas.org/icas_archive/ICAS2020/data/papers/ICAS2020_0482_paper.pdf"


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def write_csv(path: Path, rows: Iterable[dict[str, object]]) -> None:
    rows = list(rows)
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames: list[str] = []
    for row in rows:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


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


def to_float(row: dict[str, str], key: str, default: float = math.nan) -> float:
    raw = row.get(key, "")
    if raw == "":
        return default
    try:
        return float(raw)
    except ValueError:
        return default


def clean(values: Iterable[float]) -> list[float]:
    return [value for value in values if math.isfinite(value)]


def add_metric(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path,
    source_url: str = "",
    evidence_role: str = "",
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
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def require_one(rows: list[dict[str, str]], predicate) -> dict[str, str]:
    found = [row for row in rows if predicate(row)]
    if len(found) != 1:
        raise LookupError(f"expected one row, found {len(found)}")
    return found[0]


def parse_java_precipitation_formula() -> dict[str, float | str]:
    source = DRONE_PHYSICS.read_text(encoding="utf-8")
    match = re.search(
        r"return\s+MathUtil\.clamp\(1\.0\s*-\s*([0-9.]+)\s*\*\s*Math\.pow\(wetness,\s*([0-9.]+)\),\s*([0-9.]+),\s*1\.0\);",
        source,
    )
    if not match:
        raise LookupError("could not parse precipitationThrustScale formula from DronePhysics.java")
    coefficient = float(match.group(1))
    exponent = float(match.group(2))
    min_scale = float(match.group(3))
    full_scale = min(max(1.0 - coefficient, min_scale), 1.0)
    return {
        "loss_coefficient": coefficient,
        "loss_exponent": exponent,
        "min_thrust_scale": min_scale,
        "full_wetness_thrust_scale": full_scale,
        "full_wetness_thrust_loss_percent": (1.0 - full_scale) * 100.0,
        "formula": f"clamp(1 - {coefficient:g} * wetness^{exponent:g}, {min_scale:g}, 1)",
    }


def java_precipitation_loss_percent(wetness: float, formula: dict[str, float | str]) -> float:
    wetness = min(max(wetness, 0.0), 1.0)
    coefficient = float(formula["loss_coefficient"])
    exponent = float(formula["loss_exponent"])
    min_scale = float(formula["min_thrust_scale"])
    scale = min(max(1.0 - coefficient * wetness**exponent, min_scale), 1.0)
    return (1.0 - scale) * 100.0


def add_source_inventory(packet: list[dict[str, str]]) -> None:
    sources = [
        ("NWS_LOX_rain_rate_examples", NWS_RAIN_URL, "rain_rate_examples", "Rain-rate examples used for 0.05..1.50 in/h scenario labels."),
        ("Met_Office_rainfall_measurement", MET_OFFICE_RAIN_URL, "rainfall_depth_measurement", "Measurement context for converting rainfall depth rate to water flux."),
        ("NIST_water_thermophysical_data", NIST_WATER_URL, "water_density_context", "Liquid water density context; generated rows use 997 kg/m^3 near room temperature."),
        ("CIRES_vapor_pressure_formula_summary", CIRES_VAPOR_URL, "moist_air_density_context", "Vapor-pressure context for moist-air density multiplier rows."),
        ("ICAS_2020_heavy_rain_CFD", ICAS_GUST_RAIN_PDF_URL, "heavy_rain_ct_loss", "Heavy-rain CFD CT loss at LWC=19 g/m3 for 4319 and 6528 rpm."),
        ("Current_project_rain_water_runtime", repo_path(DRONE_PHYSICS), "current_runtime_formula", "Current Java rain/water thrust, load, vibration, and water-drag formulas."),
        ("Generated_precipitation_reference", repo_path(PRECIP_REFERENCE), "wide_source_table", "Wide generated reference table with rain scan, moist-air, ICAS, and water-immersion rows."),
    ]
    for name, source_url, role, note in sources:
        add_metric(
            packet,
            row_type="precip_water_packet_source_inventory",
            name=name,
            metric="source_file",
            value=repo_path(PRECIP_REFERENCE),
            unit="path",
            source_file=PRECIP_REFERENCE,
            source_url=source_url,
            evidence_role=role,
            note=note,
        )


def add_icas_reference(packet: list[dict[str, str]], rows: list[dict[str, str]]) -> None:
    metrics = [
        ("rpm", "rpm"),
        ("liquid_water_content_g_m3", "g/m^3"),
        ("droplet_mean_diameter_m", "m"),
        ("droplet_terminal_velocity_m_s", "m/s"),
        ("water_mass_flux_kg_m2_s", "kg/m^2/s"),
        ("equivalent_rain_rate_mm_h", "mm/h"),
        ("ct_no_rain", "CT"),
        ("ct_with_rain", "CT"),
        ("ct_loss_percent", "%"),
    ]
    for row in rows:
        if row.get("row_type") != "reference_icas_heavy_rain_ct_loss":
            continue
        for metric, unit in metrics:
            add_metric(
                packet,
                row_type="precip_water_packet_icas_heavy_rain_ct",
                name=row["name"],
                metric=metric,
                value=row.get(metric, ""),
                unit=unit,
                source_file=PRECIP_REFERENCE,
                source_url=row.get("source", ICAS_GUST_RAIN_PDF_URL),
                evidence_role="reference_heavy_rain_ct_loss",
                note=row.get("note", ""),
            )


def add_rain_scan(packet: list[dict[str, str]], rows: list[dict[str, str]], formula: dict[str, float | str]) -> None:
    metrics = [
        ("scenario", "label"),
        ("rain_rate_mm_h", "mm/h"),
        ("rain_rate_in_h", "in/h"),
        ("water_mass_flux_kg_m2_s", "kg/m^2/s"),
        ("rotor_disk_area_m2", "m^2"),
        ("all_rotors_water_g_s", "g/s"),
        ("precipitation_wetness_proxy", "input"),
        ("precipitation_thrust_loss_percent", "% generated"),
        ("java_source_precipitation_thrust_loss_percent", "% current Java"),
        ("rotor_precipitation_load_factor", "factor"),
        ("rotor_precipitation_vibration_hover", "fraction"),
        ("rain_impact_force_all_rotors_over_weight_at_8m_s", "weight ratio"),
    ]
    for row in rows:
        if row.get("row_type") != "precipitation_rain_scan":
            continue
        java_loss = java_precipitation_loss_percent(to_float(row, "precipitation_wetness_proxy"), formula)
        values = {**row, "java_source_precipitation_thrust_loss_percent": java_loss}
        name = f"{row['preset']}_{row['scenario']}"
        for metric, unit in metrics:
            add_metric(
                packet,
                row_type="precip_water_packet_rain_scan",
                name=name,
                metric=metric,
                value=values.get(metric, ""),
                unit=unit,
                source_file=PRECIP_REFERENCE,
                source_url=f"{row.get('source', NWS_RAIN_URL)}; {repo_path(DRONE_PHYSICS)}",
                evidence_role="rain_rate_to_wet_prop_proxy",
                note="Generated rows keep legacy wide-CSV thrust loss; java_source_* uses current DronePhysics precipitationThrustScale.",
            )


def add_moist_air(packet: list[dict[str, str]], rows: list[dict[str, str]]) -> None:
    metrics = [
        ("ambient_temperature_c", "C"),
        ("precipitation_wetness", "input"),
        ("moist_air_density_multiplier", "multiplier"),
    ]
    for row in rows:
        if row.get("row_type") != "moist_air_density":
            continue
        for metric, unit in metrics:
            add_metric(
                packet,
                row_type="precip_water_packet_moist_air_density",
                name=row["name"],
                metric=metric,
                value=row.get(metric, ""),
                unit=unit,
                source_file=PRECIP_REFERENCE,
                source_url=f"{row.get('source', CIRES_VAPOR_URL)}; {repo_path(DRONE_ENVIRONMENT)}",
                evidence_role="moist_air_density_multiplier",
                note=row.get("note", ""),
            )


def add_current_vs_icas(packet: list[dict[str, str]], rows: list[dict[str, str]], formula: dict[str, float | str]) -> None:
    metrics = [
        ("rpm", "rpm"),
        ("liquid_water_content_g_m3", "g/m^3"),
        ("equivalent_rain_rate_mm_h", "mm/h"),
        ("icas_ct_loss_percent", "%"),
        ("current_full_wetness_thrust_loss_percent", "% generated"),
        ("java_source_full_wetness_thrust_loss_percent", "% current Java"),
        ("current_loss_over_icas_loss", "ratio generated"),
        ("java_source_loss_over_icas_loss", "ratio current Java"),
        ("all_rotors_water_g_s_at_icas_lwc", "g/s"),
        ("rain_impact_force_all_rotors_over_weight_at_8m_s", "weight ratio"),
    ]
    java_loss = float(formula["full_wetness_thrust_loss_percent"])
    for row in rows:
        if row.get("row_type") != "current_vs_icas_heavy_rain_ct_loss":
            continue
        icas_loss = to_float(row, "icas_ct_loss_percent")
        values = {
            **row,
            "java_source_full_wetness_thrust_loss_percent": java_loss,
            "java_source_loss_over_icas_loss": java_loss / icas_loss if icas_loss > 0.0 else math.nan,
        }
        for metric, unit in metrics:
            add_metric(
                packet,
                row_type="precip_water_packet_current_vs_icas",
                name=row["name"],
                metric=metric,
                value=values.get(metric, ""),
                unit=unit,
                source_file=PRECIP_REFERENCE,
                source_url=f"{row.get('reference_source', ICAS_GUST_RAIN_PDF_URL)}; {repo_path(DRONE_PHYSICS)}",
                evidence_role="current_rain_loss_vs_icas_cfd",
                note="Generated current_* fields come from the wide CSV; java_source_* fields are parsed from current DronePhysics.java.",
            )


def add_water_immersion(packet: list[dict[str, str]], rows: list[dict[str, str]]) -> None:
    metrics = [
        ("water_immersion", "input"),
        ("speed_m_s", "m/s"),
        ("water_immersion_thrust_loss_percent", "%"),
        ("rotor_water_load_factor", "factor"),
        ("rotor_water_ingestion_vibration_hover", "fraction"),
        ("current_water_drag_force_n", "N"),
        ("current_water_drag_over_weight", "weight ratio"),
        ("current_water_drag_coefficient_proxy", "N/(m/s)^2"),
    ]
    for row in rows:
        if row.get("row_type") != "water_immersion_scan":
            continue
        name = f"{row['preset']}_water_{row['water_immersion']}_speed_{row['speed_m_s']}"
        for metric, unit in metrics:
            add_metric(
                packet,
                row_type="precip_water_packet_water_immersion_scan",
                name=name,
                metric=metric,
                value=row.get(metric, ""),
                unit=unit,
                source_file=PRECIP_REFERENCE,
                source_url=repo_path(DRONE_PHYSICS),
                evidence_role="current_water_immersion_runtime_proxy",
                note=row.get("note", ""),
            )


def add_java_formula_audit(packet: list[dict[str, str]], rows: list[dict[str, str]], formula: dict[str, float | str]) -> None:
    generated_full = clean(
        to_float(row, "current_full_wetness_thrust_loss_percent")
        for row in rows
        if row.get("row_type") == "current_vs_icas_heavy_rain_ct_loss"
    )
    generated_full_loss = generated_full[0] if generated_full else math.nan
    icas_refs = [row for row in rows if row.get("row_type") == "reference_icas_heavy_rain_ct_loss"]
    java_full_loss = float(formula["full_wetness_thrust_loss_percent"])
    metrics = [
        ("java_formula", formula["formula"], "text"),
        ("java_loss_coefficient", formula["loss_coefficient"], "coefficient"),
        ("java_loss_exponent", formula["loss_exponent"], "exponent"),
        ("java_min_thrust_scale", formula["min_thrust_scale"], "scale"),
        ("java_full_wetness_thrust_loss_percent", java_full_loss, "%"),
        ("generated_reference_full_wetness_thrust_loss_percent", generated_full_loss, "%"),
        ("java_loss_over_generated_reference_loss", java_full_loss / generated_full_loss if generated_full_loss > 0.0 else math.nan, "ratio"),
    ]
    for wetness in [0.25, 0.5, 0.75, 1.0]:
        metrics.append((f"java_wetness_{wetness:g}_thrust_loss_percent", java_precipitation_loss_percent(wetness, formula), "%"))
    for ref in icas_refs:
        rpm = to_float(ref, "rpm")
        loss = to_float(ref, "ct_loss_percent")
        metrics.append((f"java_loss_over_icas_{rpm:.0f}rpm_loss", java_full_loss / loss if loss > 0.0 else math.nan, "ratio"))
    for metric, value, unit in metrics:
        add_metric(
            packet,
            row_type="precip_water_packet_java_formula_audit",
            name="DronePhysics_precipitationThrustScale",
            metric=metric,
            value=value,
            unit=unit,
            source_file=DRONE_PHYSICS,
            source_url=repo_path(DRONE_PHYSICS),
            evidence_role="current_source_formula_audit",
            note="Parsed from current Java source; use this to detect stale generated precipitation CSV rows.",
        )


def add_crosscheck(packet: list[dict[str, str]], rows: list[dict[str, str]], formula: dict[str, float | str]) -> None:
    rain = [row for row in rows if row.get("row_type") == "precipitation_rain_scan"]
    water = [row for row in rows if row.get("row_type") == "water_immersion_scan"]
    moist = [row for row in rows if row.get("row_type") == "moist_air_density"]
    current_icas = [row for row in rows if row.get("row_type") == "current_vs_icas_heavy_rain_ct_loss"]
    icas_refs = [row for row in rows if row.get("row_type") == "reference_icas_heavy_rain_ct_loss"]

    racing_full = require_one(rain, lambda row: row.get("preset") == "racingQuad" and row.get("scenario") == "nws_1.50_in_h_full_wetness")
    racing_stress = require_one(rain, lambda row: row.get("preset") == "racingQuad" and row.get("scenario") == "stress_100_mm_h")
    racing_water_half_5 = require_one(
        water,
        lambda row: row.get("preset") == "racingQuad"
        and abs(to_float(row, "water_immersion") - 0.5) <= 1.0e-9
        and abs(to_float(row, "speed_m_s") - 5.0) <= 1.0e-9,
    )
    moist_35_full = require_one(
        moist,
        lambda row: abs(to_float(row, "ambient_temperature_c") - 35.0) <= 1.0e-9
        and abs(to_float(row, "precipitation_wetness") - 1.0) <= 1.0e-9,
    )
    generated_full_loss = to_float(current_icas[0], "current_full_wetness_thrust_loss_percent") if current_icas else math.nan
    java_full_loss = float(formula["full_wetness_thrust_loss_percent"])
    icas_loss_values = clean(to_float(row, "ct_loss_percent") for row in icas_refs)

    metrics = [
        ("rain_scan_row_count", len(rain), "count"),
        ("water_immersion_scan_row_count", len(water), "count"),
        ("moist_air_density_row_count", len(moist), "count"),
        ("current_vs_icas_row_count", len(current_icas), "count"),
        ("icas_heavy_rain_reference_count", len(icas_refs), "count"),
        ("racing_nws_1p5in_all_rotors_water_g_s", to_float(racing_full, "all_rotors_water_g_s"), "g/s"),
        ("racing_100mmh_all_rotors_water_g_s", to_float(racing_stress, "all_rotors_water_g_s"), "g/s"),
        ("racing_100mmh_impact_over_weight_8m_s", to_float(racing_stress, "rain_impact_force_all_rotors_over_weight_at_8m_s"), "weight ratio"),
        ("icas_equivalent_rain_rate_mm_h", to_float(icas_refs[0], "equivalent_rain_rate_mm_h") if icas_refs else math.nan, "mm/h"),
        ("icas_ct_loss_percent_min", min(icas_loss_values), "%"),
        ("icas_ct_loss_percent_max", max(icas_loss_values), "%"),
        ("generated_full_wetness_thrust_loss_percent", generated_full_loss, "%"),
        ("java_full_wetness_thrust_loss_percent", java_full_loss, "%"),
        ("java_loss_over_generated_loss", java_full_loss / generated_full_loss if generated_full_loss > 0.0 else math.nan, "ratio"),
        ("racing_water_0p5_5m_s_thrust_loss_percent", to_float(racing_water_half_5, "water_immersion_thrust_loss_percent"), "%"),
        ("racing_water_0p5_5m_s_drag_over_weight", to_float(racing_water_half_5, "current_water_drag_over_weight"), "weight ratio"),
        ("moist_air_density_35c_full_wet_multiplier", to_float(moist_35_full, "moist_air_density_multiplier"), "multiplier"),
    ]
    for ref in icas_refs:
        rpm = to_float(ref, "rpm")
        loss = to_float(ref, "ct_loss_percent")
        metrics.append((f"java_loss_over_icas_{rpm:.0f}rpm_loss", java_full_loss / loss if loss > 0.0 else math.nan, "ratio"))
        metrics.append((f"generated_loss_over_icas_{rpm:.0f}rpm_loss", generated_full_loss / loss if loss > 0.0 else math.nan, "ratio"))
    for metric, value, unit in metrics:
        add_metric(
            packet,
            row_type="precip_water_packet_crosscheck",
            name="summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=PRECIP_REFERENCE,
            source_url=f"{NWS_RAIN_URL}; {MET_OFFICE_RAIN_URL}; {NIST_WATER_URL}; {ICAS_GUST_RAIN_PDF_URL}; {repo_path(DRONE_PHYSICS)}",
            evidence_role="handoff_summary",
            note="Use these rows to separate rain mass flux, wet-prop thrust loss, moist-air density, and water-immersion drag.",
        )


def add_method(packet: list[dict[str, str]]) -> None:
    add_metric(
        packet,
        row_type="precip_water_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Use this packet as a rain/water handoff. Rainfall depth rates are converted to water mass flux, ICAS heavy-rain "
            "rows provide CT-loss anchors, moist-air rows are density multipliers, and water immersion is an intentionally "
            "severe separate exposure model. The java_source_* fields audit the current runtime formula and can differ from "
            "the wide generated CSV until that CSV is regenerated."
        ),
        unit="text",
        source_file=PRECIP_REFERENCE,
        source_url=f"{NWS_RAIN_URL}; {MET_OFFICE_RAIN_URL}; {ICAS_GUST_RAIN_PDF_URL}",
        evidence_role="method",
    )


def build_packet() -> list[dict[str, str]]:
    source_rows = read_rows(PRECIP_REFERENCE)
    formula = parse_java_precipitation_formula()
    packet: list[dict[str, str]] = []
    add_source_inventory(packet)
    add_icas_reference(packet, source_rows)
    add_rain_scan(packet, source_rows, formula)
    add_moist_air(packet, source_rows)
    add_current_vs_icas(packet, source_rows, formula)
    add_water_immersion(packet, source_rows)
    add_java_formula_audit(packet, source_rows, formula)
    add_crosscheck(packet, source_rows, formula)
    add_method(packet)
    return packet


def sync_summary(packet_rows: Iterable[dict[str, str]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("precip_water_packet_")]
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
