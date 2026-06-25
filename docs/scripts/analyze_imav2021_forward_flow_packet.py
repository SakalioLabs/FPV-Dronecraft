"""Create a compact IMAV 2021 5-inch forward-flow propeller source packet.

Outputs:
  docs/data/imav2021_forward_flow_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category imav2021_forward_flow_packet_*

The paper is valuable because it is one of the few public sources that tests
5-inch, 3-blade sport-multirotor propellers in forward/oblique wind-tunnel
flow. It does not publish raw CSV curves in the PDF, so this packet preserves
the test matrix, measurement schema, equations, and derived advance-ratio
coverage rather than pretending to be a CT/CP fit.
"""

from __future__ import annotations

import csv
import math
import urllib.request
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "imav2021_forward_flow"
PDF = RAW / "imav2021_21_propulsive_efficiency.pdf"
OUTPUT = DATA / "imav2021_forward_flow_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

SOURCE_URL = "https://www.imavs.org/papers/2021/21.pdf"
SOURCE_TITLE = "IMAV 2021-21 Propulsive efficiency of small multirotor propellers in fast forward flight"
PROPELLER_DIAMETER_IN = 5.0
PROPELLER_DIAMETER_M = PROPELLER_DIAMETER_IN * 0.0254
RACING_HOVER_RPM = 651.155 / 3.0 * 60.0
RACING_MAX_RPM = 29137.6327495


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


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
            writer.writerow({key: value_text(row.get(key)) for key in fieldnames})


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
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
            "source_file": repo_path(PDF),
            "source_url": SOURCE_URL,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def ensure_pdf() -> None:
    RAW.mkdir(parents=True, exist_ok=True)
    if PDF.exists() and PDF.stat().st_size > 500_000:
        return
    with urllib.request.urlopen(SOURCE_URL, timeout=60) as response, PDF.open("wb") as handle:
        handle.write(response.read())


def advance_ratio(speed_m_s: float, rpm: float, diameter_m: float = PROPELLER_DIAMETER_M) -> float:
    return speed_m_s / ((rpm / 60.0) * diameter_m)


def build_packet() -> list[dict[str, object]]:
    ensure_pdf()
    rows: list[dict[str, object]] = []
    add_metric(
        rows,
        row_type="imav2021_forward_flow_packet_source_inventory",
        name="paper",
        metric="pdf_size_bytes",
        value=PDF.stat().st_size,
        unit="bytes",
        evidence_role="public_pdf",
        note=SOURCE_TITLE,
    )
    add_metric(
        rows,
        row_type="imav2021_forward_flow_packet_source_inventory",
        name="paper",
        metric="raw_curve_csv_found_in_pdf",
        value=False,
        unit="boolean",
        evidence_role="data_availability",
        note="The PDF summarizes results in figures; no raw CSV/table curves were found in the paper text.",
    )

    for metric, value, unit, note in [
        ("diameter", PROPELLER_DIAMETER_IN, "inch", "All chosen propellers have 5-inch diameter."),
        ("blade_count", 3, "count", "All chosen propellers have 3 blades."),
        ("propeller_series", "HQProp 3 Blade V1S", "text", "Four advertised pitch variants were tested."),
        ("pitch_values", "4, 4.3, 4.8, 5", "inch", "Advertised geometric pitch values."),
        ("test_motor", "T-Motor F80 2500kv", "text", "Selected for relevant RPM range and thermal capacity."),
        ("esc", "T-Motor Flame 80A", "text", "ESC used to drive the motor."),
        ("nominal_voltage", 14.8, "V", "Motor operated at nominal voltage of recommended battery configuration."),
        ("force_torque_stand", "RCBenchmark 1580", "text", "Measures force and mechanical torque."),
        ("rpm_sensor", "RCBenchmark Back-emf Sensor", "text", "RPM source listed in equipment table."),
    ]:
        add_metric(
            rows,
            row_type="imav2021_forward_flow_packet_test_article",
            name="test_hardware",
            metric=metric,
            value=value,
            unit=unit,
            evidence_role="test_setup",
            note=note,
        )

    for metric, value, unit, note in [
        ("wind_speeds", "10, 15, 20", "m/s", "Wind speeds from the testing matrix."),
        ("flow_angles", "30, 35, 40, 45, 50, 90", "deg", "0 deg is hover convention; 90 deg is axial fixed-wing-like flow."),
        ("rpm_ramp_start", 10000, "rpm", "Each test ramped from 10,000 rpm."),
        ("rpm_ramp_end_approx", 30000, "rpm", "Maximum rpm varied by configuration, approximately 30,000 rpm."),
        ("ramp_duration", 45, "s", "Ramp duration for each test."),
        ("repeat_count", 3, "count", "Each ramp repeated three times."),
        ("high_quality_crop_start", 15000, "rpm", "Paper crops results to >=15,000 rpm for higher data quality."),
    ]:
        add_metric(
            rows,
            row_type="imav2021_forward_flow_packet_test_matrix",
            name="matrix",
            metric=metric,
            value=value,
            unit=unit,
            evidence_role="test_matrix",
            note=note,
        )

    for metric, unit, note in [
        ("thrust", "N", "Logged as FT."),
        ("mechanical_torque", "N*m", "Logged as M; used to compute mechanical power."),
        ("electrical_power", "W", "Logged as Pelec; motor/ESC efficiency was outside paper scope."),
        ("angular_velocity", "Hz", "Logged as n."),
        ("temperature", "K", "Used with pressure for density."),
        ("atmospheric_pressure", "Pa", "Used with temperature for density."),
        ("propeller_diameter", "m", "Used for nondimensional coefficients."),
    ]:
        add_metric(
            rows,
            row_type="imav2021_forward_flow_packet_logged_schema",
            name="logged_data",
            metric=metric,
            value=1,
            unit=unit,
            evidence_role="measurement_schema",
            note=note,
        )

    for metric, value, unit, note in [
        ("density", "rho = Patm / (R*T)", "equation", "Session density from atmospheric measurements."),
        ("mechanical_power", "Pin = M*n", "equation", "Paper uses mechanical torque times angular speed."),
        ("thrust_coefficient", "CT = FT / (rho*n^2*D^4)", "equation", "n is rotations per second."),
        ("power_coefficient", "CP = Pin / (rho*n^3*D^5)", "equation", "Uses mechanical power, not electrical power."),
        ("advance_ratio", "J = U / (n*D)", "equation", "Comparable to project rotorAdvanceRatio via J = pi*mu."),
        ("propulsive_efficiency", "eta = J*CT/CP", "equation", "Main result plotted in paper figures."),
    ]:
        add_metric(
            rows,
            row_type="imav2021_forward_flow_packet_formula",
            name="nondimensionalization",
            metric=metric,
            value=value,
            unit=unit,
            evidence_role="formula",
            note=note,
        )

    for speed in [10.0, 15.0, 20.0]:
        for rpm in [10000.0, 15000.0, 30000.0]:
            add_metric(
                rows,
                row_type="imav2021_forward_flow_packet_derived_j_point",
                name=f"{speed:g}m_s_{rpm:g}rpm",
                metric="advance_ratio_j",
                value=advance_ratio(speed, rpm),
                unit="J",
                evidence_role="derived_coverage",
                note="Computed from paper wind-speed/rpm matrix for 5-inch diameter.",
            )
        add_metric(
            rows,
            row_type="imav2021_forward_flow_packet_derived_j_range",
            name=f"{speed:g}m_s_high_quality_crop",
            metric="advance_ratio_j_range_15000_to_30000rpm",
            value=f"{advance_ratio(speed, 30000.0):.6g}..{advance_ratio(speed, 15000.0):.6g}",
            unit="J",
            evidence_role="derived_coverage",
            note="Approximate J range for the >=15,000 rpm cropped high-quality region.",
        )

    for speed in [10.0, 12.5, 15.0, 20.0]:
        add_metric(
            rows,
            row_type="imav2021_forward_flow_packet_current_overlap",
            name=f"racingQuad_hover_{speed:g}m_s",
            metric="advance_ratio_j",
            value=advance_ratio(speed, RACING_HOVER_RPM),
            unit="J",
            evidence_role="current_project_overlap",
            note="Uses current racingQuad hover blade-pass-derived RPM and 5-inch diameter.",
        )
        add_metric(
            rows,
            row_type="imav2021_forward_flow_packet_current_overlap",
            name=f"racingQuad_max_{speed:g}m_s",
            metric="advance_ratio_j",
            value=advance_ratio(speed, RACING_MAX_RPM),
            unit="J",
            evidence_role="current_project_overlap",
            note="Uses current racingQuad max RPM from existing model-validation rows.",
        )

    for metric, value, unit, note in [
        ("has_direct_5in_3blade_forward_flow", True, "boolean", "Direct 5-inch, 3-blade sport-multirotor propeller wind-tunnel source."),
        ("has_mechanical_torque_measurement", True, "boolean", "RCBenchmark 1580 logs mechanical torque."),
        ("has_electrical_power_measurement", True, "boolean", "Electrical power was logged but not used for propulsive efficiency curves."),
        ("has_raw_curve_csv_in_public_pdf", False, "boolean", "Figures 5-8 expose propulsive-efficiency curves only; raw logs are still needed for CT/CP."),
        ("fit_status", "do_not_fit_ct_cp_without_raw_logs", "text", "Use as source/coverage and eta digitization target until numeric thrust/torque curves are extracted."),
    ]:
        add_metric(
            rows,
            row_type="imav2021_forward_flow_packet_fit_caveat",
            name="fit_status",
            metric=metric,
            value=value,
            unit=unit,
            evidence_role="fit_caveat",
            note=note,
        )

    return rows


def sync_summary(packet_rows: Iterable[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("imav2021_forward_flow_packet_")]
    added: list[dict[str, object]] = []
    for row in packet_rows:
        added.append(
            {
                "category": row["row_type"],
                "name": row["name"],
                "metric": row["metric"],
                "value": value_text(row["value"]),
                "unit": row["unit"],
                "source": row["source_url"],
            }
        )
    write_csv(SUMMARY, kept + added)
    return len(added)


def main() -> None:
    packet = build_packet()
    write_csv(OUTPUT, packet)
    synced = sync_summary(packet)
    print(f"Wrote {repo_path(OUTPUT)} with {len(packet)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
