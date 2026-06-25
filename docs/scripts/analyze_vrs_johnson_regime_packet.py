"""Build a NASA Johnson VRS regime-reference packet.

Outputs:
  docs/data/vrs_johnson_regime_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category vrs_johnson_packet_*

Johnson TP-2005-213477 is a broad rotorcraft mean-inflow/VRS model, not an
FPV propwash-torque source. This packet preserves the Table 4 normalized
model parameters, converts them to current preset descent/forward speeds, and
checks where the current VRS scan sits relative to the Johnson boundaries.
"""

from __future__ import annotations

import csv
import math
import statistics
import urllib.request
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "vrs_johnson"
SOURCE = DATA / "vrs_propwash_reference.csv"
OUTPUT = DATA / "vrs_johnson_regime_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"
EXISTING_PDF = DATA / "raw" / "rotorcraft.arc.nasa.gov_Publications_files_Johnson_TP-2005-213477.pdf"
PDF = RAW / "johnson_tp_2005_213477.pdf"

SOURCE_URL = "https://rotorcraft.arc.nasa.gov/Publications/files/Johnson_TP-2005-213477.pdf"
SOURCE_TITLE = "Model for Vortex Ring State Influence on Rotorcraft Flight Dynamics"
SOURCE_REPORT = "NASA/TP-2005-213477"


JOHNSON_PARAMS = [
    ("baseline", "A", "VzA", -1.5, "vh", "Baseline model axial descent point on momentum-theory branch."),
    ("baseline", "B", "VzB", -2.1, "vh", "Baseline model axial descent point on momentum-theory branch."),
    ("baseline", "C", "VxC", 0.75, "vh", "Forward-speed cutoff where baseline model uses momentum theory."),
    ("vrs", "D", "VzD", -0.2, "vh", "Upper VRS-model join to the baseline curve."),
    ("vrs", "N", "VzN", -0.45, "vh", "Zero-damping/stability-boundary point in vertical descent."),
    ("vrs", "N", "Vplusv_N", 0.85, "vh", "Specified inflow value Vz+v at point N."),
    ("vrs", "X", "VzX", -1.5, "vh", "Second zero-damping/stability-boundary point in vertical descent."),
    ("vrs", "X", "Vplusv_X", 1.25, "vh", "Specified inflow value Vz+v at point X."),
    ("vrs", "E", "VzE", -2.0, "vh", "Lower VRS-model join to the baseline curve."),
    ("vrs", "M", "VxM", 0.95, "vh", "Forward-speed cutoff for applying the VRS increment."),
]

JOHNSON_BOUNDARIES = {
    "D_join_upper": ("VzD", 0.2, "descent", "VRS-model upper join D, abs(Vz)/vh."),
    "N_zero_damping_low": ("VzN", 0.45, "descent", "Lower zero-damping/stability-boundary point N, abs(Vz)/vh."),
    "X_zero_damping_high": ("VzX", 1.5, "descent", "Upper zero-damping/stability-boundary point X, abs(Vz)/vh."),
    "E_join_lower": ("VzE", 2.0, "descent", "VRS-model lower join E, abs(Vz)/vh."),
    "C_baseline_forward_cutoff": ("VxC", 0.75, "forward", "Baseline momentum-theory forward-speed cutoff C, Vx/vh."),
    "M_vrs_forward_cutoff": ("VxM", 0.95, "forward", "VRS increment forward-speed cutoff M, Vx/vh."),
}

INTERP_METRICS = [
    ("current_vrs_intensity_hover_spin_no_crossflow", "fraction"),
    ("current_vrs_entry_component", "fraction"),
    ("current_vrs_exit_component", "fraction"),
    ("current_vrs_base_thrust_loss_percent_hover_spin", "percent"),
    ("current_vrs_buffet_thrust_amplitude_percent_max_spin", "percent"),
    ("current_propwash_descent_factor", "fraction"),
]


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


def to_float(value: object) -> float:
    try:
        result = float(str(value))
    except (TypeError, ValueError):
        return math.nan
    return result if math.isfinite(result) else math.nan


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
        for row in row_list:
            writer.writerow({key: value_text(row.get(key)) for key in fieldnames})


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path | str,
    source_url: str = SOURCE_URL,
    evidence_role: str,
    note: str = "",
    **extra: object,
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": value,
            "unit": unit,
            "source_file": repo_path(source_file) if isinstance(source_file, Path) else source_file,
            "source_url": source_url,
            "evidence_role": evidence_role,
            "note": note,
            **extra,
        }
    )


def download_pdf() -> Path:
    if EXISTING_PDF.exists():
        return EXISTING_PDF
    RAW.mkdir(parents=True, exist_ok=True)
    if not PDF.exists():
        with urllib.request.urlopen(SOURCE_URL, timeout=60) as response:
            PDF.write_bytes(response.read())
    return PDF


def finite(values: Iterable[float]) -> list[float]:
    return [value for value in values if math.isfinite(value)]


def median(values: Iterable[float]) -> float:
    vals = finite(values)
    return statistics.median(vals) if vals else math.nan


def group_current_scan(rows: list[dict[str, str]]) -> dict[str, list[dict[str, str]]]:
    grouped: dict[str, list[dict[str, str]]] = {}
    for row in rows:
        if row.get("row_type") != "current_vrs_scan":
            continue
        grouped.setdefault(row.get("preset", ""), []).append(row)
    for preset_rows in grouped.values():
        preset_rows.sort(key=lambda row: to_float(row.get("descent_ratio_vi")))
    return grouped


def interp(preset_rows: list[dict[str, str]], x: float, metric: str) -> float:
    points = [(to_float(row.get("descent_ratio_vi")), to_float(row.get(metric))) for row in preset_rows]
    points = [(px, py) for px, py in points if math.isfinite(px) and math.isfinite(py)]
    if not points:
        return math.nan
    if x <= points[0][0]:
        return points[0][1]
    if x >= points[-1][0]:
        return points[-1][1]
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        if x0 <= x <= x1:
            if abs(x1 - x0) <= 1e-12:
                return y0
            t = (x - x0) / (x1 - x0)
            return y0 + (y1 - y0) * t
    return math.nan


def hover_vi(preset_rows: list[dict[str, str]]) -> float:
    return to_float(preset_rows[0].get("hover_induced_velocity_m_s")) if preset_rows else math.nan


def sample_band(preset_rows: list[dict[str, str]]) -> tuple[float, float]:
    active = [
        to_float(row.get("descent_ratio_vi"))
        for row in preset_rows
        if to_float(row.get("current_vrs_intensity_hover_spin_no_crossflow")) > 0.0
    ]
    active = finite(active)
    if not active:
        return math.nan, math.nan
    return min(active), max(active)


def peak_intensity(preset_rows: list[dict[str, str]]) -> tuple[float, float]:
    best_ratio = math.nan
    best_value = -math.inf
    for row in preset_rows:
        value = to_float(row.get("current_vrs_intensity_hover_spin_no_crossflow"))
        if value > best_value:
            best_value = value
            best_ratio = to_float(row.get("descent_ratio_vi"))
    return best_ratio, best_value if best_value > -math.inf else math.nan


def add_source_rows(rows: list[dict[str, object]], pdf_path: Path) -> None:
    source_metrics = [
        ("source_title", SOURCE_TITLE, "text", "NASA report title."),
        ("source_report", SOURCE_REPORT, "text", "NASA technical publication identifier."),
        ("source_url", SOURCE_URL, "url", "Official NASA PDF URL."),
        ("source_pdf_cache", repo_path(pdf_path), "path", "Local PDF cache used as source evidence."),
        ("source_table", "Table 4: VRS model parameters; velocities scaled with vh", "text", "Parameter table used for numeric rows."),
    ]
    for metric, value, unit, note in source_metrics:
        add_metric(
            rows,
            row_type="vrs_johnson_packet_source",
            name="NASA_Johnson_TP_2005_213477",
            metric=metric,
            value=value,
            unit=unit,
            source_file=pdf_path,
            evidence_role="source_inventory",
            note=note,
        )


def add_parameter_rows(rows: list[dict[str, object]], pdf_path: Path) -> None:
    for group, point, parameter, value, unit, note in JOHNSON_PARAMS:
        add_metric(
            rows,
            row_type="vrs_johnson_packet_table4_parameter",
            name=f"{group}_{point}_{parameter}",
            metric=parameter,
            value=value,
            unit=unit,
            source_file=pdf_path,
            evidence_role="johnson_table4_parameter",
            note=note,
            parameter_group=group,
            point=point,
            parameter=parameter,
        )

    derived = [
        ("vrs_model_abs_descent_join_low", 0.2, "abs(Vz)/vh", "Point D upper join to VRS increment."),
        ("vrs_zero_damping_abs_descent_low", 0.45, "abs(Vz)/vh", "Point N lower zero-damping/stability-boundary value."),
        ("vrs_zero_damping_abs_descent_high", 1.5, "abs(Vz)/vh", "Point X upper zero-damping/stability-boundary value."),
        ("vrs_model_abs_descent_join_high", 2.0, "abs(Vz)/vh", "Point E lower join to VRS increment."),
        ("baseline_forward_cutoff", 0.75, "Vx/vh", "Point C baseline forward-speed cutoff."),
        ("vrs_forward_cutoff", 0.95, "Vx/vh", "Point M VRS increment forward-speed cutoff."),
    ]
    for metric, value, unit, note in derived:
        add_metric(
            rows,
            row_type="vrs_johnson_packet_regime_boundary",
            name="johnson_table4_derived_boundaries",
            metric=metric,
            value=value,
            unit=unit,
            source_file=pdf_path,
            evidence_role="johnson_regime_boundary",
            note=note,
        )


def add_preset_rows(rows: list[dict[str, object]], grouped: dict[str, list[dict[str, str]]]) -> None:
    for preset, preset_rows in sorted(grouped.items()):
        vi = hover_vi(preset_rows)
        active_low, active_high = sample_band(preset_rows)
        peak_ratio, peak_value = peak_intensity(preset_rows)
        for boundary_name, (parameter, ratio, axis, note) in JOHNSON_BOUNDARIES.items():
            speed = ratio * vi
            add_metric(
                rows,
                row_type="vrs_johnson_packet_preset_boundary_speed",
                name=f"{preset}_{boundary_name}",
                metric="boundary_speed_m_s",
                value=speed,
                unit="m/s",
                source_file=SOURCE,
                source_url=SOURCE_URL,
                evidence_role="preset_boundary_conversion",
                note=note,
                preset=preset,
                johnson_parameter=parameter,
                ratio_over_vh=ratio,
                axis=axis,
                hover_induced_velocity_m_s=vi,
            )

            if axis == "descent":
                for metric, unit in INTERP_METRICS:
                    add_metric(
                        rows,
                        row_type="vrs_johnson_packet_current_at_boundary",
                        name=f"{preset}_{boundary_name}",
                        metric=metric,
                        value=interp(preset_rows, ratio, metric),
                        unit=unit,
                        source_file=SOURCE,
                        source_url="drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java",
                        evidence_role="current_model_at_johnson_boundary",
                        note="Current VRS/propwash scan linearly interpolated at a NASA Johnson Table 4 descent boundary.",
                        preset=preset,
                        johnson_parameter=parameter,
                        ratio_over_vh=ratio,
                        descent_speed_m_s=speed,
                    )

        summary_metrics = [
            ("hover_induced_velocity_m_s", vi, "m/s", "Current preset hover induced velocity used for Johnson speed conversion."),
            ("current_sampled_vrs_active_low_vi", active_low, "vi ratio", "Lowest sampled descent ratio with nonzero current VRS intensity."),
            ("current_sampled_vrs_active_high_vi", active_high, "vi ratio", "Highest sampled descent ratio with nonzero current VRS intensity."),
            ("current_sampled_active_low_over_johnson_N", active_low / 0.45 if math.isfinite(active_low) else math.nan, "ratio", "Sampled current VRS-active lower bound divided by Johnson point N."),
            ("current_sampled_active_high_over_johnson_E", active_high / 2.0 if math.isfinite(active_high) else math.nan, "ratio", "Sampled current VRS-active upper bound divided by Johnson point E."),
            ("current_peak_vrs_ratio_vi", peak_ratio, "vi ratio", "Sampled descent ratio of peak current VRS intensity."),
            ("current_peak_vrs_intensity", peak_value, "fraction", "Peak current VRS intensity in the scan."),
            ("current_peak_inside_johnson_zero_damping_band", 1 if 0.45 <= peak_ratio <= 1.5 else 0, "boolean", "Whether current peak intensity lies between Johnson N and X."),
            ("current_intensity_at_johnson_N", interp(preset_rows, 0.45, "current_vrs_intensity_hover_spin_no_crossflow"), "fraction", "Current VRS intensity at Johnson point N."),
            ("current_intensity_at_johnson_X", interp(preset_rows, 1.5, "current_vrs_intensity_hover_spin_no_crossflow"), "fraction", "Current VRS intensity at Johnson point X."),
        ]
        for metric, value, unit, note in summary_metrics:
            add_metric(
                rows,
                row_type="vrs_johnson_packet_preset_summary",
                name=preset,
                metric=metric,
                value=value,
                unit=unit,
                source_file=SOURCE,
                source_url="drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java",
                evidence_role="preset_vrs_regime_summary",
                note=note,
                preset=preset,
            )


def add_global_summary(rows: list[dict[str, object]], grouped: dict[str, list[dict[str, str]]]) -> None:
    racing = grouped["racingQuad"]
    all_peak_ratios = [peak_intensity(preset_rows)[0] for preset_rows in grouped.values()]
    specs = [
        ("table4_parameter_count", len(JOHNSON_PARAMS), "count", "Number of NASA Johnson Table 4 parameter rows encoded."),
        ("preset_count", len(grouped), "count", "Current presets with VRS scan rows converted to Johnson boundary speeds."),
        ("johnson_zero_damping_band_low_vi", 0.45, "vi ratio", "Johnson point N, lower zero-damping/stability boundary."),
        ("johnson_zero_damping_band_high_vi", 1.5, "vi ratio", "Johnson point X, upper zero-damping/stability boundary."),
        ("johnson_vrs_increment_forward_cutoff_vx_over_vh", 0.95, "Vx/vh", "Johnson point M, VRS increment cutoff in forward speed."),
        ("racingQuad_johnson_N_descent_m_s", 0.45 * hover_vi(racing), "m/s", "racingQuad descent speed at Johnson point N."),
        ("racingQuad_johnson_X_descent_m_s", 1.5 * hover_vi(racing), "m/s", "racingQuad descent speed at Johnson point X."),
        ("racingQuad_current_intensity_at_johnson_N", interp(racing, 0.45, "current_vrs_intensity_hover_spin_no_crossflow"), "fraction", "Current racingQuad VRS intensity at Johnson point N."),
        ("racingQuad_current_intensity_at_johnson_X", interp(racing, 1.5, "current_vrs_intensity_hover_spin_no_crossflow"), "fraction", "Current racingQuad VRS intensity at Johnson point X."),
        ("current_peak_ratio_vi_p50_across_presets", median(all_peak_ratios), "vi ratio", "Median sampled current VRS peak ratio across current presets."),
    ]
    for metric, value, unit, note in specs:
        add_metric(
            rows,
            row_type="vrs_johnson_packet_summary",
            name="vrs_johnson_regime_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=SOURCE_URL,
            evidence_role="compact_regime_handoff",
            note=note,
        )

    add_metric(
        rows,
        row_type="vrs_johnson_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Johnson TP-2005-213477 is used as a normalized VRS regime and mean-inflow boundary source only. "
            "Do not use these rows as FPV-specific thrust-buffet, lateral-force, or propwash-torque amplitudes."
        ),
        unit="text",
        source_file=OUTPUT,
        source_url=SOURCE_URL,
        evidence_role="method",
        note="Current-model comparisons interpolate the existing vrs_propwash_reference.csv scan; no Java constants are changed here.",
    )


def sync_summary(packet_rows: list[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY) if SUMMARY.exists() else []
    kept = [row for row in existing if not row.get("category", "").startswith("vrs_johnson_packet_")]
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
                "source_file": str(row.get("source_file", "")),
                "source_url": str(row.get("source_url", "")),
                "evidence_role": str(row.get("evidence_role", "")),
                "note": str(row.get("note", "")),
            }
        )
    write_csv(SUMMARY, kept + added)
    return len(added)


def build_rows() -> list[dict[str, object]]:
    pdf_path = download_pdf()
    source_rows = read_rows(SOURCE)
    grouped = group_current_scan(source_rows)
    rows: list[dict[str, object]] = []
    add_source_rows(rows, pdf_path)
    add_parameter_rows(rows, pdf_path)
    add_preset_rows(rows, grouped)
    add_global_summary(rows, grouped)
    return rows


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
