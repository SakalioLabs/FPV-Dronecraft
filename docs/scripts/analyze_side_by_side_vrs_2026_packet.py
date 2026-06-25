"""Build a side-by-side propeller VRS interaction packet.

Outputs:
  docs/data/side_by_side_vrs_2026_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category side_by_side_vrs_packet_*

The source is a 2026 Physics of Fluids experiment on side-by-side
propellers in axial descent. The packet keeps the data as a VRS interaction
trend/upper-bound source: it does not provide raw time histories or direct
FPV propwash torque coefficients.
"""

from __future__ import annotations

import csv
import math
import re
import statistics
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
OUTPUT = DATA / "side_by_side_vrs_2026_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"
DRONE_CONFIG = ROOT / "drone-sim-core" / "src" / "main" / "java" / "com" / "tenicana" / "dronecraft" / "sim" / "DroneConfig.java"

SOURCE_TITLE = "Experimental investigation of the vortex ring state for side-by-side propellers in descent flight"
SOURCE_AUTHORS = "Bucherelli, Granata, Savino, and Zanotti"
SOURCE_JOURNAL = "Physics of Fluids 38, 035136 (2026)"
SOURCE_DOI = "https://doi.org/10.1063/5.0311688"
SOURCE_FULL_TEXT = "https://www.researchgate.net/publication/402690466_Experimental_investigation_of_the_vortex_ring_state_for_side-by-side_propellers_in_descent_flight"


FIELDNAMES = [
    "row_type",
    "name",
    "metric",
    "value",
    "unit",
    "source_file",
    "source_url",
    "evidence_role",
    "note",
    "configuration",
    "rotation_sense",
    "gap_over_radius",
    "center_spacing_over_radius",
    "descent_ratio",
    "vortex_radius_over_radius",
    "normalized_circulation",
    "isolated_vortex_radius_ratio",
    "isolated_circulation_ratio",
    "current_preset",
    "current_gap_over_radius",
]


EXPERIMENT_METRICS = [
    ("publication_year", 2026, "year", "Published after the older Shetty/Selig and NASA Johnson anchors."),
    ("propeller_diameter_m", 0.300, "m", "Three-bladed Varioprop 12C nominal diameter."),
    ("propeller_radius_m", 0.150, "m", "Derived from the reported 0.300 m nominal diameter."),
    ("blade_count", 3, "count", "Three-bladed propeller."),
    ("pitch_at_75_percent_radius_deg", 12.0, "deg", "Pitch angle set at 75% radius."),
    ("rotational_speed_rpm", 7000.0, "rpm", "Fixed propeller speed for the experiment."),
    ("reynolds_number_75r", 1.16e5, "Re", "Chord Reynolds number at 75% radius."),
    ("wind_tunnel_section_width_m", 1.5, "m", "Politecnico di Milano large wind tunnel open-jet section width."),
    ("wind_tunnel_section_height_m", 1.0, "m", "Politecnico di Milano large wind tunnel open-jet section height."),
    ("wind_tunnel_max_speed_m_s", 58.0, "m/s", "Reported maximum wind-tunnel speed."),
    ("wind_tunnel_turbulence_fraction", 0.001, "fraction", "Reported turbulence intensity below 0.1%."),
    ("load_cell_thrust_range_n", 22.4, "N", "FUTEK MBA500 thrust range."),
    ("load_cell_torque_range_nm", 5.65, "N*m", "FUTEK MBA500 torque range."),
    ("load_cell_nonlinearity_fraction", 0.0025, "fraction", "Reported nonlinearity, full-scale fraction."),
    ("load_cell_nonrepeatability_fraction", 0.0005, "fraction", "Reported non-repeatability, full-scale fraction."),
    ("sample_rate_hz", 25000.0, "Hz", "Load-cell acquisition sample rate."),
    ("runs_per_operating_point", 4, "count", "Independent runs per operating condition."),
    ("piv_double_frame_count", 500, "count", "PIV double frames per measurement point."),
    ("piv_resolution_m", 0.003, "m", "PIV spatial resolution."),
    ("piv_max_in_plane_velocity_error_fraction", 0.02, "fraction", "Reported maximum in-plane velocity error below 2%."),
    ("public_machine_readable_raw_data", 0, "boolean", "The article states data are available from the corresponding author upon reasonable request."),
]


VORTEX_TABLE = [
    {
        "configuration": "isolated_propeller",
        "rotation_sense": "single",
        "gap_over_radius": None,
        "descent_ratio": 1.1,
        "vortex_radius_over_radius": 0.78,
        "normalized_circulation": 5.38,
        "note": "Isolated propeller VRS reference from the article's vortex-property table.",
    },
    {
        "configuration": "sbs_gap_0p1_co",
        "rotation_sense": "co_rotating",
        "gap_over_radius": 0.1,
        "descent_ratio": 0.9,
        "vortex_radius_over_radius": 0.54,
        "normalized_circulation": 3.53,
        "note": "Tight side-by-side co-rotating case.",
    },
    {
        "configuration": "sbs_gap_0p1_counter",
        "rotation_sense": "counter_rotating",
        "gap_over_radius": 0.1,
        "descent_ratio": 0.9,
        "vortex_radius_over_radius": 0.59,
        "normalized_circulation": 4.40,
        "note": "Tight side-by-side counter-rotating case.",
    },
    {
        "configuration": "sbs_gap_0p3_co",
        "rotation_sense": "co_rotating",
        "gap_over_radius": 0.3,
        "descent_ratio": 0.9,
        "vortex_radius_over_radius": 0.62,
        "normalized_circulation": 4.15,
        "note": "Intermediate side-by-side co-rotating case.",
    },
    {
        "configuration": "sbs_gap_0p5_co",
        "rotation_sense": "co_rotating",
        "gap_over_radius": 0.5,
        "descent_ratio": 0.9,
        "vortex_radius_over_radius": 0.65,
        "normalized_circulation": 4.73,
        "note": "Intermediate side-by-side co-rotating case.",
    },
    {
        "configuration": "sbs_gap_1p0_co",
        "rotation_sense": "co_rotating",
        "gap_over_radius": 1.0,
        "descent_ratio": 0.9,
        "vortex_radius_over_radius": 0.74,
        "normalized_circulation": 5.56,
        "note": "Widest side-by-side co-rotating case in the table.",
    },
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
        if math.isnan(value):
            return ""
        return f"{value:.12g}"
    return str(value)


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: str | Path = "",
    source_url: str = SOURCE_DOI,
    evidence_role: str,
    note: str,
    **context: object,
) -> None:
    row: dict[str, object] = {
        "row_type": row_type,
        "name": name,
        "metric": metric,
        "value": value,
        "unit": unit,
        "source_file": repo_path(source_file) if isinstance(source_file, Path) else source_file,
        "source_url": source_url,
        "evidence_role": evidence_role,
        "note": note,
    }
    for key in FIELDNAMES:
        row.setdefault(key, "")
    for key, context_value in context.items():
        row[key] = context_value
    rows.append(row)


def write_csv(path: Path, rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDNAMES, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow({key: value_text(row.get(key, "")) for key in FIELDNAMES})


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def eval_java_double(expr: str) -> float:
    cleaned = expr.strip().replace("Math.sqrt", "math.sqrt")
    return float(eval(cleaned, {"__builtins__": {}}, {"math": math}))


def current_racing_quad_geometry() -> dict[str, float]:
    text = DRONE_CONFIG.read_text(encoding="utf-8")
    match = re.search(
        r"public static DroneConfig racingQuad\(\) \{(?P<body>.*?)return new DroneConfig",
        text,
        re.DOTALL,
    )
    if not match:
        raise RuntimeError("Could not locate DroneConfig.racingQuad()")
    body = match.group("body")
    arm_match = re.search(r"double\s+arm\s*=\s*(?P<expr>[^;]+);", body)
    radius_match = re.search(r"double\s+rotorRadius\s*=\s*(?P<expr>[^;]+);", body)
    if not arm_match or not radius_match:
        raise RuntimeError("Could not parse racingQuad arm or rotorRadius")
    arm = eval_java_double(arm_match.group("expr"))
    radius = eval_java_double(radius_match.group("expr"))
    adjacent_center_spacing = 2.0 * arm
    disk_gap = adjacent_center_spacing - 2.0 * radius
    return {
        "arm_coordinate_m": arm,
        "motor_center_radius_m": math.sqrt(2.0) * arm,
        "rotor_radius_m": radius,
        "adjacent_center_spacing_m": adjacent_center_spacing,
        "adjacent_center_spacing_over_radius": adjacent_center_spacing / radius,
        "adjacent_disk_gap_m": disk_gap,
        "adjacent_disk_gap_over_radius": disk_gap / radius,
    }


def linear_fit(points: list[tuple[float, float]]) -> dict[str, float]:
    xs = [point[0] for point in points]
    ys = [point[1] for point in points]
    x_mean = statistics.mean(xs)
    y_mean = statistics.mean(ys)
    sxx = sum((x - x_mean) ** 2 for x in xs)
    sxy = sum((x - x_mean) * (y - y_mean) for x, y in points)
    slope = sxy / sxx
    intercept = y_mean - slope * x_mean
    predicted = [intercept + slope * x for x in xs]
    ss_res = sum((y - y_hat) ** 2 for y, y_hat in zip(ys, predicted))
    ss_tot = sum((y - y_mean) ** 2 for y in ys)
    r2 = 1.0 - ss_res / ss_tot if ss_tot > 0.0 else math.nan
    return {"slope": slope, "intercept": intercept, "r2": r2}


def add_source_rows(rows: list[dict[str, object]]) -> None:
    for metric, value, unit, note in [
        ("title", SOURCE_TITLE, "text", "Article title."),
        ("authors", SOURCE_AUTHORS, "text", "Article authors."),
        ("journal", SOURCE_JOURNAL, "text", "Journal citation."),
        ("doi", SOURCE_DOI, "url", "Official DOI."),
        ("full_text_mirror", SOURCE_FULL_TEXT, "url", "Accessible full-text mirror used for table transcription when the AIP PDF was protected by browser checks."),
    ]:
        add_metric(
            rows,
            row_type="side_by_side_vrs_packet_source",
            name="source",
            metric=metric,
            value=value,
            unit=unit,
            source_url=SOURCE_DOI,
            evidence_role="source_traceability",
            note=note,
        )

    for metric, value, unit, note in EXPERIMENT_METRICS:
        add_metric(
            rows,
            row_type="side_by_side_vrs_packet_experiment",
            name="experiment_setup",
            metric=metric,
            value=value,
            unit=unit,
            source_url=SOURCE_DOI,
            evidence_role="experiment_setup",
            note=note,
        )


def add_vortex_rows(rows: list[dict[str, object]]) -> None:
    isolated = VORTEX_TABLE[0]
    isolated_rv = isolated["vortex_radius_over_radius"]
    isolated_gamma = isolated["normalized_circulation"]
    for item in VORTEX_TABLE:
        gap = item["gap_over_radius"]
        center_spacing = None if gap is None else gap + 2.0
        rv = item["vortex_radius_over_radius"]
        gamma = item["normalized_circulation"]
        context = {
            "configuration": item["configuration"],
            "rotation_sense": item["rotation_sense"],
            "gap_over_radius": gap,
            "center_spacing_over_radius": center_spacing,
            "descent_ratio": item["descent_ratio"],
            "vortex_radius_over_radius": rv,
            "normalized_circulation": gamma,
            "isolated_vortex_radius_ratio": rv / isolated_rv,
            "isolated_circulation_ratio": gamma / isolated_gamma,
        }
        for metric, value, unit, note in [
            ("descent_ratio_dr", item["descent_ratio"], "DR", "Descent ratio where the vortex properties were tabulated."),
            ("vortex_radius_over_prop_radius", rv, "R", "Outer toroidal vortex radius normalized by propeller radius."),
            ("normalized_circulation", gamma, "Gamma/(Vinf*R)", "Outer toroidal vortex circulation normalized by free-stream speed and propeller radius."),
            ("vortex_radius_ratio_vs_isolated", rv / isolated_rv, "ratio", "Vortex-radius ratio relative to isolated propeller table value."),
            ("circulation_ratio_vs_isolated", gamma / isolated_gamma, "ratio", "Circulation ratio relative to isolated propeller table value."),
        ]:
            add_metric(
                rows,
                row_type="side_by_side_vrs_packet_vortex_table",
                name=item["configuration"],
                metric=metric,
                value=value,
                unit=unit,
                source_url=SOURCE_DOI,
                evidence_role="vortex_ring_table_ii",
                note=f"{item['note']} {note}",
                **context,
            )


def add_fit_and_current_rows(rows: list[dict[str, object]]) -> None:
    isolated = VORTEX_TABLE[0]
    isolated_rv = isolated["vortex_radius_over_radius"]
    isolated_gamma = isolated["normalized_circulation"]
    co_rows = [item for item in VORTEX_TABLE if item["rotation_sense"] == "co_rotating"]
    radius_fit = linear_fit([(float(item["gap_over_radius"]), float(item["vortex_radius_over_radius"])) for item in co_rows])
    gamma_fit = linear_fit([(float(item["gap_over_radius"]), float(item["normalized_circulation"])) for item in co_rows])

    for metric, value, unit, note in [
        ("co_rotating_radius_slope_per_gap_R", radius_fit["slope"], "(Rv/R)/gapR", "Linear fit over co-rotating table gaps 0.1..1.0R."),
        ("co_rotating_radius_intercept", radius_fit["intercept"], "Rv/R", "Linear fit intercept; do not extrapolate below 0.1R without a physical model."),
        ("co_rotating_radius_fit_r2", radius_fit["r2"], "R2", "Fit quality for the four co-rotating spacing points."),
        ("co_rotating_circulation_slope_per_gap_R", gamma_fit["slope"], "Gamma/(Vinf*R)/gapR", "Linear fit over co-rotating table gaps 0.1..1.0R."),
        ("co_rotating_circulation_intercept", gamma_fit["intercept"], "Gamma/(Vinf*R)", "Linear fit intercept; do not extrapolate below 0.1R without a physical model."),
        ("co_rotating_circulation_fit_r2", gamma_fit["r2"], "R2", "Fit quality for the four co-rotating spacing points."),
    ]:
        add_metric(
            rows,
            row_type="side_by_side_vrs_packet_spacing_fit",
            name="co_rotating_gap_fit",
            metric=metric,
            value=value,
            unit=unit,
            source_url=SOURCE_DOI,
            evidence_role="spacing_trend_fit",
            note=note,
        )

    geometry = current_racing_quad_geometry()
    current_gap = geometry["adjacent_disk_gap_over_radius"]
    current_radius_prediction = radius_fit["intercept"] + radius_fit["slope"] * current_gap
    current_gamma_prediction = gamma_fit["intercept"] + gamma_fit["slope"] * current_gap
    bounded_radius = min(isolated_rv, current_radius_prediction)
    bounded_gamma = min(isolated_gamma, current_gamma_prediction)
    geometry_rows = [
        ("arm_coordinate_m", geometry["arm_coordinate_m"], "m", "Parsed from DroneConfig.racingQuad()."),
        ("motor_center_radius_m", geometry["motor_center_radius_m"], "m", "Parsed from DroneConfig.racingQuad() geometry."),
        ("rotor_radius_m", geometry["rotor_radius_m"], "m", "Parsed from DroneConfig.racingQuad()."),
        ("adjacent_center_spacing_m", geometry["adjacent_center_spacing_m"], "m", "Adjacent motor center spacing for the square X layout."),
        ("adjacent_center_spacing_over_radius", geometry["adjacent_center_spacing_over_radius"], "R", "Adjacent motor center spacing divided by current rotor radius."),
        ("adjacent_disk_gap_m", geometry["adjacent_disk_gap_m"], "m", "Current adjacent disk-tip gap."),
        ("adjacent_disk_gap_over_radius", current_gap, "R", "Current adjacent disk-tip gap divided by current rotor radius."),
        ("current_gap_over_tested_max_gap", current_gap / 1.0, "ratio", "The paper's largest tested side-by-side disk gap is 1.0R."),
        ("current_inside_tested_gap_range", 1 if 0.1 <= current_gap <= 1.0 else 0, "boolean", "Whether the current adjacent disk gap is inside the paper's 0.1..1.0R tested range."),
        ("co_fit_predicted_radius_at_current_gap", current_radius_prediction, "Rv/R", "Linear co-rotating fit extrapolated to the current gap; kept as diagnostic only."),
        ("co_fit_predicted_circulation_at_current_gap", current_gamma_prediction, "Gamma/(Vinf*R)", "Linear co-rotating fit extrapolated to the current gap; kept as diagnostic only."),
        ("bounded_radius_at_current_gap", bounded_radius, "Rv/R", "Prediction capped at the isolated-propeller table value."),
        ("bounded_circulation_at_current_gap", bounded_gamma, "Gamma/(Vinf*R)", "Prediction capped at the isolated-propeller table value."),
        ("bounded_radius_ratio_vs_isolated", bounded_radius / isolated_rv, "ratio", "Capped current-gap radius ratio relative to isolated propeller."),
        ("bounded_circulation_ratio_vs_isolated", bounded_gamma / isolated_gamma, "ratio", "Capped current-gap circulation ratio relative to isolated propeller."),
    ]
    for metric, value, unit, note in geometry_rows:
        add_metric(
            rows,
            row_type="side_by_side_vrs_packet_current_geometry",
            name="racingQuad_adjacent_rotors",
            metric=metric,
            value=value,
            unit=unit,
            source_file=DRONE_CONFIG,
            source_url="drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneConfig.java",
            evidence_role="current_geometry_mapping",
            note=note,
            current_preset="racingQuad",
            current_gap_over_radius=current_gap,
        )


def add_summary_rows(rows: list[dict[str, object]]) -> None:
    isolated = VORTEX_TABLE[0]
    tight_co = next(item for item in VORTEX_TABLE if item["configuration"] == "sbs_gap_0p1_co")
    tight_counter = next(item for item in VORTEX_TABLE if item["configuration"] == "sbs_gap_0p1_counter")
    one_r = next(item for item in VORTEX_TABLE if item["configuration"] == "sbs_gap_1p0_co")
    geometry = current_racing_quad_geometry()
    current_gap = geometry["adjacent_disk_gap_over_radius"]
    specs = [
        ("table_condition_count", len(VORTEX_TABLE), "count", "Number of vortex-property table conditions encoded."),
        ("tested_gap_min_over_radius", 0.1, "R", "Minimum side-by-side disk-tip gap in the table."),
        ("tested_gap_max_over_radius", 1.0, "R", "Maximum side-by-side disk-tip gap in the table."),
        ("isolated_onset_descent_ratio_dr", isolated["descent_ratio"], "DR", "Isolated-propeller VRS onset/table descent ratio."),
        ("side_by_side_onset_descent_ratio_dr", tight_co["descent_ratio"], "DR", "Side-by-side table descent ratio used for all encoded SBS cases."),
        ("side_by_side_onset_reduction_dr", isolated["descent_ratio"] - tight_co["descent_ratio"], "DR", "Onset shift from isolated to SBS table conditions."),
        ("side_by_side_onset_reduction_fraction", (isolated["descent_ratio"] - tight_co["descent_ratio"]) / isolated["descent_ratio"], "fraction", "Relative onset shift from isolated to SBS table conditions."),
        ("tight_co_radius_ratio_vs_isolated", tight_co["vortex_radius_over_radius"] / isolated["vortex_radius_over_radius"], "ratio", "Tight 0.1R co-rotating vortex radius relative to isolated."),
        ("tight_co_circulation_ratio_vs_isolated", tight_co["normalized_circulation"] / isolated["normalized_circulation"], "ratio", "Tight 0.1R co-rotating circulation relative to isolated."),
        ("tight_counter_radius_over_tight_co", tight_counter["vortex_radius_over_radius"] / tight_co["vortex_radius_over_radius"], "ratio", "Counter-rotating 0.1R radius divided by co-rotating 0.1R radius."),
        ("tight_counter_circulation_over_tight_co", tight_counter["normalized_circulation"] / tight_co["normalized_circulation"], "ratio", "Counter-rotating 0.1R circulation divided by co-rotating 0.1R circulation."),
        ("one_R_co_radius_ratio_vs_isolated", one_r["vortex_radius_over_radius"] / isolated["vortex_radius_over_radius"], "ratio", "1.0R co-rotating vortex radius relative to isolated."),
        ("one_R_co_circulation_ratio_vs_isolated", one_r["normalized_circulation"] / isolated["normalized_circulation"], "ratio", "1.0R co-rotating circulation relative to isolated."),
        ("racingQuad_adjacent_gap_over_radius", current_gap, "R", "Current racingQuad adjacent disk-tip gap divided by rotor radius."),
        ("racingQuad_gap_over_tested_max_gap", current_gap / 1.0, "ratio", "Current adjacent disk gap divided by the paper's maximum tested gap."),
        ("racingQuad_inside_tested_gap_range", 1 if 0.1 <= current_gap <= 1.0 else 0, "boolean", "Whether current adjacent disk gap is in the experimental side-by-side spacing range."),
    ]
    for metric, value, unit, note in specs:
        add_metric(
            rows,
            row_type="side_by_side_vrs_packet_summary",
            name="side_by_side_vrs_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=SOURCE_DOI,
            evidence_role="compact_vrs_interaction_handoff",
            note=note,
        )

    add_metric(
        rows,
        row_type="side_by_side_vrs_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Use the 2026 side-by-side propeller VRS rows for adjacent-rotor VRS interaction onset and vortex-strength "
            "trend checks. The current racingQuad adjacent disk gap is outside the tested 0.1..1.0R range, so the rows "
            "are an upper-bound/trend source rather than a direct force or torque fit."
        ),
        unit="text",
        source_file=OUTPUT,
        source_url=SOURCE_DOI,
        evidence_role="method",
        note="No public raw CSV/time history was found; the article states data are available by request.",
    )


def sync_summary(packet_rows: list[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY) if SUMMARY.exists() else []
    kept = [row for row in existing if not row.get("category", "").startswith("side_by_side_vrs_packet_")]
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
    with SUMMARY.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(added[0].keys()))
        writer.writeheader()
        writer.writerows(kept + added)
    return len(added)


def build_rows() -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    add_source_rows(rows)
    add_vortex_rows(rows)
    add_fit_and_current_rows(rows)
    add_summary_rows(rows)
    return rows


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
