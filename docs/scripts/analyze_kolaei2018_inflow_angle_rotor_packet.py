"""Create a source packet for Kolaei et al. 2018 inflow-angle rotor data.

Outputs:
  docs/data/kolaei2018_inflow_angle_rotor_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category kolaei2018_inflow_angle_rotor_packet_*

This paper is useful for the FPV simulator because it reports wind-tunnel
rotor coefficients versus advance ratio and inflow angle, including power and
roll-moment coefficient trends. The rotors are larger than 5-inch FPV props, so
the packet is a nondimensional shape and sign/threshold source, not a direct
coefficient transplant.
"""

from __future__ import annotations

import csv
import math
import urllib.request
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "kolaei2018_inflow_angles"
PDF = RAW / "kolaei2018_experimental_analysis_small_scale_rotor_inflow_angles.pdf"
OUTPUT = DATA / "kolaei2018_inflow_angle_rotor_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

SOURCE_URL = "https://doi.org/10.1155/2018/2560370"
SOURCE_TITLE = "Experimental Analysis of a Small-Scale Rotor at Various Inflow Angles"
PDF_URLS = [
    "https://pdfs.semanticscholar.org/1000/b57764bdc8c6ffc30cbd406fcef41b60fbb8.pdf",
    "https://downloads.hindawi.com/journals/ijae/2018/2560370.pdf",
]

RACING_RADIUS_M = 0.127 / 2.0
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


def ensure_pdf() -> None:
    RAW.mkdir(parents=True, exist_ok=True)
    if PDF.exists() and PDF.stat().st_size > 500_000:
        return
    errors: list[str] = []
    for url in PDF_URLS:
        request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                data = response.read()
            if len(data) < 500_000:
                raise RuntimeError(f"download too small: {len(data)} bytes")
            PDF.write_bytes(data)
            return
        except Exception as exc:
            errors.append(f"{url}: {exc}")
    raise RuntimeError("Could not download Kolaei 2018 PDF: " + " | ".join(errors))


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
    source_file: str | None = None,
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": value,
            "unit": unit,
            "source_file": source_file or repo_path(PDF),
            "source_url": SOURCE_URL,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def code_mu(speed_m_s: float, rpm: float, radius_m: float = RACING_RADIUS_M) -> float:
    omega = rpm / 60.0 * 2.0 * math.pi
    return speed_m_s / (omega * radius_m)


def build_packet() -> list[dict[str, object]]:
    ensure_pdf()
    rows: list[dict[str, object]] = []

    add_metric(
        rows,
        row_type="kolaei2018_inflow_angle_rotor_packet_source_inventory",
        name="paper",
        metric="pdf_size_bytes",
        value=PDF.stat().st_size,
        unit="bytes",
        evidence_role="public_pdf",
        note=SOURCE_TITLE,
    )
    for metric, value, unit, note in [
        ("license", "Creative Commons Attribution License", "text", "Paper states open access under CC BY."),
        ("journal", "International Journal of Aerospace Engineering", "text", "Hindawi/Wiley article."),
        ("vehicle_context", "small unmanned multirotor aerial vehicles", "text", "Motivated by multirotor rotors in horizontal flight."),
    ]:
        add_metric(
            rows,
            row_type="kolaei2018_inflow_angle_rotor_packet_source_inventory",
            name="paper",
            metric=metric,
            value=value,
            unit=unit,
            evidence_role="source_context",
            note=note,
        )

    for name, diameter_in, pitch_in, rpm_values, note in [
        ("MAE 11x7", 11.0, 7.0, "3000, 4000, 5000", "Measured rotor; model-aircraft electric propeller class."),
        ("T-Motor 18x6.1", 18.0, 6.1, "3000, 4000", "Measured rotor; geometry obtained by 3D scanning."),
    ]:
        add_metric(
            rows,
            row_type="kolaei2018_inflow_angle_rotor_packet_rotor",
            name=name,
            metric="diameter",
            value=diameter_in,
            unit="inch",
            evidence_role="test_hardware",
            note=note,
        )
        add_metric(
            rows,
            row_type="kolaei2018_inflow_angle_rotor_packet_rotor",
            name=name,
            metric="pitch",
            value=pitch_in,
            unit="inch",
            evidence_role="test_hardware",
            note=note,
        )
        add_metric(
            rows,
            row_type="kolaei2018_inflow_angle_rotor_packet_rotor",
            name=name,
            metric="rpm_values",
            value=rpm_values,
            unit="rpm",
            evidence_role="test_matrix",
            note=note,
        )

    for metric, value, unit, note in [
        ("inflow_angle_range", "-90..90", "deg", "Sign convention covers descent/side/upward-like inflow orientations."),
        ("advance_ratio_mu_range", "0..0.30", "mu", "Reported operating range in the article; same definition form as project code."),
        ("angles_with_full_trends", "90, 60, 30, 15, 5, 0, -5, -15, -30, -60, -90", "deg", "Figures include dense near-zero-angle curves, not only coarse 30-degree steps."),
        ("rpm_sweep", "3000, 4000, 5000", "rpm", "5000 rpm reported for the 11-inch rotor only in the summarized packet."),
        ("measured_coefficients", "CT, CP, CMx", "text", "Thrust, power, and roll-moment coefficients versus advance ratio and angle."),
        ("wall_interference_angle_change", "<3", "deg", "Paper reports estimated angle change below 3 deg for advance ratios above 0.1."),
        ("axial_wall_interference_angle_change", "<0.3", "deg", "Paper reports axial-flow angle change below 0.3 deg."),
    ]:
        add_metric(
            rows,
            row_type="kolaei2018_inflow_angle_rotor_packet_test_matrix",
            name="wind_tunnel_matrix",
            metric=metric,
            value=value,
            unit=unit,
            evidence_role="test_matrix",
            note=note,
        )

    for metric, value, unit, note in [
        ("advance_ratio", "mu = V / (Omega*R)", "equation", "This matches the code's rotorAdvanceRatio definition."),
        ("thrust_coefficient", "CT = T / (rho*A*(Omega*R)^2)", "equation", "Rotorcraft-style disk-area normalization."),
        ("power_coefficient", "CP = P / (rho*A*(Omega*R)^3)", "equation", "Power uses P = Omega*Q."),
        ("roll_moment_coefficient", "CMx = Mx / (rho*A*(Omega*R)^2*R)", "equation", "Useful for transverse-flow/roll-moment sign and scale checks."),
    ]:
        add_metric(
            rows,
            row_type="kolaei2018_inflow_angle_rotor_packet_formula",
            name="nondimensionalization",
            metric=metric,
            value=value,
            unit=unit,
            evidence_role="formula",
            note=note,
        )

    for metric, value, unit, note in [
        ("advance_ratio_mu_uncertainty", 1.11, "percent", "Reported experimental uncertainty."),
        ("ct_uncertainty", 1.78, "percent", "Reported experimental uncertainty."),
        ("cp_uncertainty", 2.15, "percent", "Reported experimental uncertainty."),
        ("cm_uncertainty", 1.57, "percent", "Reported experimental uncertainty."),
    ]:
        add_metric(
            rows,
            row_type="kolaei2018_inflow_angle_rotor_packet_uncertainty",
            name="reported_uncertainty",
            metric=metric,
            value=value,
            unit=unit,
            evidence_role="uncertainty",
            note=note,
        )

    for metric, value, unit, note in [
        ("same_definition_as_project_mu", True, "boolean", "No J-to-mu pi conversion is needed for this source."),
        ("direct_5in_fpv_fit", False, "boolean", "Rotors are 11 and 18 inches, so use shape/sign, not direct coefficient scale."),
        ("has_roll_moment_coefficient", True, "boolean", "Useful for transverse-flow roll moment and asymmetry checks."),
        ("has_ct_cp_vs_mu_and_angle", True, "boolean", "Figures give coefficient trends across inflow angles."),
    ]:
        add_metric(
            rows,
            row_type="kolaei2018_inflow_angle_rotor_packet_fit_caveat",
            name="fit_status",
            metric=metric,
            value=value,
            unit=unit,
            evidence_role="fit_caveat",
            note=note,
        )

    for speed in [5.0, 10.0, 12.5, 15.0, 20.0, 30.0, 40.0]:
        add_metric(
            rows,
            row_type="kolaei2018_inflow_angle_rotor_packet_current_overlap",
            name=f"racingQuad_hover_{speed:g}m_s",
            metric="code_mu",
            value=code_mu(speed, RACING_HOVER_RPM),
            unit="mu",
            evidence_role="current_project_overlap",
            note="Uses current racingQuad hover blade-pass-derived RPM and 5-inch radius.",
        )
        add_metric(
            rows,
            row_type="kolaei2018_inflow_angle_rotor_packet_current_overlap",
            name=f"racingQuad_max_{speed:g}m_s",
            metric="code_mu",
            value=code_mu(speed, RACING_MAX_RPM),
            unit="mu",
            evidence_role="current_project_overlap",
            note="Uses current racingQuad max RPM and 5-inch radius.",
        )

    for metric, value, unit, note in [
        ("hover_12_5m_s_mu_over_source_max", code_mu(12.5, RACING_HOVER_RPM) / 0.30, "ratio", "Shows current 12.5 m/s hover-RPM point is inside the source mu range."),
        ("hover_30m_s_mu_over_source_max", code_mu(30.0, RACING_HOVER_RPM) / 0.30, "ratio", "30 m/s hover-RPM proxy slightly exceeds the source range."),
        ("max_40m_s_mu_over_source_max", code_mu(40.0, RACING_MAX_RPM) / 0.30, "ratio", "40 m/s max-RPM proxy remains inside the source range."),
    ]:
        add_metric(
            rows,
            row_type="kolaei2018_inflow_angle_rotor_packet_overlap_summary",
            name="source_range_overlap",
            metric=metric,
            value=value,
            unit=unit,
            evidence_role="current_project_overlap",
            note=note,
        )

    for name, metric, value, unit, note in [
        ("power_trend", "cp_at_low_angles", "approximately_constant_with_mu", "text", "Paper reports power coefficient is relatively constant at low inflow angles."),
        ("thrust_trend", "ct_low_angle_behavior", "constant_or_increasing_with_mu", "text", "At lower angles, CT stays nearly constant or grows as mu increases."),
        ("windmill_brake", "high_angle_state", "observed_at_larger_inflow_angles", "text", "At larger angles, thrust and torque trends enter windmill-brake-like behavior."),
        ("roll_moment", "cmx_importance", "significant_in_edgewise_flow", "text", "Roll moment can be a trimming concern in horizontal flight."),
    ]:
        add_metric(
            rows,
            row_type="kolaei2018_inflow_angle_rotor_packet_qualitative_trend",
            name=name,
            metric=metric,
            value=value,
            unit=unit,
            evidence_role="trend_summary",
            note=note,
        )

    for figure, page, coefficient, note in [
        ("Figure 9", 8, "CT", "Thrust coefficient versus freestream advance ratio."),
        ("Figure 10", 10, "CP", "Power coefficient versus freestream advance ratio."),
        ("Figure 11", 11, "CMx", "Roll-moment coefficient versus freestream advance ratio."),
    ]:
        figure_name = f"{figure}_{coefficient}"
        for metric, value, unit, metric_note in [
            ("pdf_page", page, "page", "1-based PDF page number from pypdf extraction."),
            ("coefficient", coefficient, "text", note),
            ("source_rotor", "T-Motor 18x6.1", "text", "Primary inflow-angle coefficient figures use this rotor."),
            ("rpm_panels", "3000, 4000, 5000", "rpm", "Each figure is split by rotational speed."),
            ("x_axis", "freestream_advance_ratio_mu", "mu", "Same definition as project rotorAdvanceRatio."),
            ("x_range", "0..0.30", "mu", "Approximate plotted/source range."),
            ("angle_curves", "90, 60, 30, 15, 5, 0, -5, -15, -30, -60, -90", "deg", "Curves visible across the paper figures; verify exact legend labels during digitization."),
            ("digitization_status", "pending_vector_or_page_render", "text", "pypdf did not expose these figures as whole raster images; render pages or use vector-aware digitization."),
        ]:
            add_metric(
                rows,
                row_type="kolaei2018_inflow_angle_rotor_packet_digitization_figure",
                name=figure_name,
                metric=metric,
                value=value,
                unit=unit,
                evidence_role="digitization_target",
                note=metric_note,
            )

    return rows


def sync_summary(packet_rows: Iterable[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("kolaei2018_inflow_angle_rotor_packet_")]
    added = [
        {
            "category": row["row_type"],
            "name": row["name"],
            "metric": row["metric"],
            "value": value_text(row["value"]),
            "unit": row["unit"],
            "source": row["source_url"],
        }
        for row in packet_rows
    ]
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
