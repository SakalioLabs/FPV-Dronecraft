"""Build an MDPI hovering-rotor icing degradation packet.

Outputs:
  docs/data/icing_rotor_mdpi_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category icing_rotor_packet_*

The source paper is Villeneuve et al., "An Experimental Apparatus for
Icing Tests of Low Altitude Hovering Drones", Drones 2022, 6, 68.
This packet deliberately keeps icing rows separate from ordinary rain/wet-prop
rows: the published data describe frozen contamination accumulating over time.
"""

from __future__ import annotations

import csv
import math
import re
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

OUTPUT = DATA / "icing_rotor_mdpi_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"
DRONE_PHYSICS = ROOT / "drone-sim-core" / "src" / "main" / "java" / "com" / "tenicana" / "dronecraft" / "sim" / "DronePhysics.java"

SOURCE_TITLE = "An Experimental Apparatus for Icing Tests of Low Altitude Hovering Drones"
SOURCE_URL = "https://www.mdpi.com/2504-446X/6/3/68"
SOURCE_DOI = "https://doi.org/10.3390/drones6030068"
SOURCE_BOOK_PDF = "https://mdpi-res.com/bookfiles/book/7901/Unconventional_DroneBased_Surveying.pdf"

TEST_RPM = 4950.0
TEST_PITCH_DEG = 11.7
TEST_LAMBDA_G_DM2_H = 80.0
ROTOR_DIAMETER_M = 0.66
ROTOR_BLADE_COUNT = 4
ROTOR_MAX_TIP_SPEED_M_S = 208.0
ROTATION_HZ = TEST_RPM / 60.0
TEST_TIP_SPEED_M_S = math.pi * ROTOR_DIAMETER_M * ROTATION_HZ


TABLE1_CONDITIONS = [
    {
        "lambda_g_dm2_h": 5.0,
        "mvd_um": 120.0,
        "lwc_g_m3": 0.47,
        "rationale": "0.5 g/m3 FAA/AR-09/45 freezing-drizzle-style condition",
    },
    {
        "lambda_g_dm2_h": 25.0,
        "mvd_um": 120.0,
        "lwc_g_m3": 2.35,
        "rationale": "typical ground icing plus light rain, 2 L/h/m2",
    },
    {
        "lambda_g_dm2_h": 25.0,
        "mvd_um": 800.0,
        "lwc_g_m3": 0.19,
        "rationale": "typical ground icing plus light rain, 2 L/h/m2",
    },
    {
        "lambda_g_dm2_h": 67.0,
        "mvd_um": 120.0,
        "lwc_g_m3": 6.31,
        "rationale": "APT70 0.25 in/h requirement plus moderate rain, 6 L/h/m2",
    },
    {
        "lambda_g_dm2_h": 67.0,
        "mvd_um": 800.0,
        "lwc_g_m3": 0.50,
        "rationale": "APT70 0.25 in/h requirement; FAA 0.5 g/m3; moderate rain",
    },
    {
        "lambda_g_dm2_h": 80.0,
        "mvd_um": 120.0,
        "lwc_g_m3": 7.53,
        "rationale": "typical ground icing plus moderate rain, 8 L/h/m2",
    },
    {
        "lambda_g_dm2_h": 80.0,
        "mvd_um": 800.0,
        "lwc_g_m3": 0.59,
        "rationale": "typical ground icing plus moderate rain, 8 L/h/m2",
    },
]


TABLE4_CASES = [
    {
        "mvd_um": 120.0,
        "temperature_c": -5.0,
        "height_m": 2.0,
        "ct_star_percent_s": -0.124,
        "cq_star_percent_s": 0.430,
        "cq_plus_percent_s": 0.491,
        "p_plus_percent_s": 0.524,
        "icing_time_s": 169.0,
    },
    {
        "mvd_um": 120.0,
        "temperature_c": -5.0,
        "height_m": 4.0,
        "ct_star_percent_s": -0.195,
        "cq_star_percent_s": 0.565,
        "cq_plus_percent_s": 0.703,
        "p_plus_percent_s": 0.785,
        "icing_time_s": 114.0,
    },
    {
        "mvd_um": 120.0,
        "temperature_c": -15.0,
        "height_m": 2.0,
        "ct_star_percent_s": -0.136,
        "cq_star_percent_s": 0.317,
        "cq_plus_percent_s": 0.367,
        "p_plus_percent_s": 0.394,
        "icing_time_s": 162.0,
    },
    {
        "mvd_um": 120.0,
        "temperature_c": -15.0,
        "height_m": 4.0,
        "ct_star_percent_s": -0.226,
        "cq_star_percent_s": 0.400,
        "cq_plus_percent_s": 0.548,
        "p_plus_percent_s": 0.642,
        "icing_time_s": 106.0,
    },
    {
        "mvd_um": 800.0,
        "temperature_c": -5.0,
        "height_m": 2.0,
        "ct_star_percent_s": -0.036,
        "cq_star_percent_s": 0.061,
        "cq_plus_percent_s": 0.063,
        "p_plus_percent_s": 0.065,
        "icing_time_s": 321.0,
    },
    {
        "mvd_um": 800.0,
        "temperature_c": -5.0,
        "height_m": 4.0,
        "ct_star_percent_s": -0.012,
        "cq_star_percent_s": 0.047,
        "cq_plus_percent_s": 0.048,
        "p_plus_percent_s": 0.048,
        "icing_time_s": 761.0,
    },
    {
        "mvd_um": 800.0,
        "temperature_c": -15.0,
        "height_m": 2.0,
        "ct_star_percent_s": -0.049,
        "cq_star_percent_s": 0.081,
        "cq_plus_percent_s": 0.085,
        "p_plus_percent_s": 0.087,
        "icing_time_s": 326.0,
    },
    {
        "mvd_um": 800.0,
        "temperature_c": -15.0,
        "height_m": 4.0,
        "ct_star_percent_s": -0.081,
        "cq_star_percent_s": 0.121,
        "cq_plus_percent_s": 0.132,
        "p_plus_percent_s": 0.138,
        "icing_time_s": 220.0,
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
        if not math.isfinite(value):
            return ""
        return f"{value:.12g}"
    return str(value)


def read_rows(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
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
            writer.writerow({key: value_text(row.get(key, "")) for key in fieldnames})


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_url: str = SOURCE_URL,
    evidence_role: str,
    note: str = "",
    source_file: Path = OUTPUT,
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


def percentile(values: Iterable[float], p: float) -> float:
    clean = sorted(float(value) for value in values if math.isfinite(float(value)))
    if not clean:
        return math.nan
    if len(clean) == 1:
        return clean[0]
    rank = (len(clean) - 1) * p / 100.0
    low = math.floor(rank)
    high = math.ceil(rank)
    if low == high:
        return clean[low]
    return clean[low] + (clean[high] - clean[low]) * (rank - low)


def parse_current_precipitation_loss_percent() -> dict[str, float | str]:
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


def lambda_to_mm_h(lambda_g_dm2_h: float) -> float:
    return lambda_g_dm2_h * 0.1


def condition_lwc(mvd_um: float, lambda_g_dm2_h: float = TEST_LAMBDA_G_DM2_H) -> float:
    for row in TABLE1_CONDITIONS:
        if abs(row["mvd_um"] - mvd_um) < 1.0e-9 and abs(row["lambda_g_dm2_h"] - lambda_g_dm2_h) < 1.0e-9:
            return row["lwc_g_m3"]
    return math.nan


def derived_case(case: dict[str, float]) -> dict[str, float]:
    projected_ct_loss = abs(case["ct_star_percent_s"]) * case["icing_time_s"]
    projected_cq_increase = case["cq_star_percent_s"] * case["icing_time_s"]
    projected_cq_plus = case["cq_plus_percent_s"] * case["icing_time_s"]
    projected_p_plus = case["p_plus_percent_s"] * case["icing_time_s"]
    return {
        **case,
        "rpm": TEST_RPM,
        "pitch_deg": TEST_PITCH_DEG,
        "lambda_g_dm2_h": TEST_LAMBDA_G_DM2_H,
        "equivalent_rain_mm_h": lambda_to_mm_h(TEST_LAMBDA_G_DM2_H),
        "lwc_g_m3": condition_lwc(case["mvd_um"]),
        "rotation_hz": ROTATION_HZ,
        "tip_speed_m_s": TEST_TIP_SPEED_M_S,
        "projected_total_ct_loss_percent": projected_ct_loss,
        "projected_total_cq_increase_percent": projected_cq_increase,
        "projected_total_cq_required_increase_percent": projected_cq_plus,
        "projected_total_power_required_increase_percent": projected_p_plus,
        "projected_end_ct_scale": max(0.0, 1.0 - projected_ct_loss / 100.0),
    }


def case_key(case: dict[str, float]) -> tuple[float, float, float]:
    return (case["mvd_um"], case["temperature_c"], case["height_m"])


def case_name(case: dict[str, float]) -> str:
    return f"MVD{case['mvd_um']:.0f}_T{case['temperature_c']:.0f}_h{case['height_m']:.0f}m"


def add_source_inventory(rows: list[dict[str, object]]) -> None:
    source_metrics = [
        ("paper_title", SOURCE_TITLE, "text", SOURCE_URL, "Paper title."),
        ("doi", SOURCE_DOI, "url", SOURCE_DOI, "Formal DOI."),
        ("mdpi_article_url", SOURCE_URL, "url", SOURCE_URL, "Open article HTML source."),
        ("book_pdf_mirror_used_for_table_headers", SOURCE_BOOK_PDF, "url", SOURCE_BOOK_PDF, "Open MDPI book PDF was used to verify math-rendered Table 4 headers."),
        ("license", "CC BY 4.0", "text", SOURCE_URL, "The article states a Creative Commons Attribution license."),
        ("rotor_blade_count", ROTOR_BLADE_COUNT, "count", SOURCE_URL, "Abstract: the tested drone rotor has four blades."),
        ("rotor_diameter_m", ROTOR_DIAMETER_M, "m", SOURCE_URL, "Abstract and setup text report a 0.66 m rotor diameter."),
        ("rotor_airfoil", "NACA 4412", "text", SOURCE_URL, "Rotor setup text describes a NACA 4412 aerodynamic profile."),
        ("rotor_max_tip_speed_m_s", ROTOR_MAX_TIP_SPEED_M_S, "m/s", SOURCE_URL, "Abstract reports maximum tip speed of 208 m/s."),
        ("table1_condition_rows", len(TABLE1_CONDITIONS), "count", SOURCE_URL, "Icing parameter grid rows transcribed from Table 1."),
        ("table4_test_case_rows", len(TABLE4_CASES), "count", SOURCE_URL, "Icing aerodynamic-rate rows transcribed from Table 4."),
        ("table4_test_rpm", TEST_RPM, "rpm", SOURCE_URL, "Table 4: all cases are at 4950 RPM."),
        ("table4_test_pitch_deg", TEST_PITCH_DEG, "deg", SOURCE_URL, "Table 4: all cases are at theta = 11.7 degrees."),
        ("table4_test_lambda_g_dm2_h", TEST_LAMBDA_G_DM2_H, "g/dm^2/h", SOURCE_URL, "Table 4: all cases use lambda = 80 g dm^-2 h^-1."),
        ("dry_height_curve_fit_error_percent", 1.28, "%", SOURCE_URL, "Text reports average error between dry CT-vs-CQ curve fits at h=2 m and h=4 m."),
        ("h4_cloud_intensity_multiplier_low", 1.3, "x", SOURCE_URL, "Text attributes most h=4 m icing-rate increases to 1.3..1.4x higher precipitation intensity."),
        ("h4_cloud_intensity_multiplier_high", 1.4, "x", SOURCE_URL, "Text attributes most h=4 m icing-rate increases to 1.3..1.4x higher precipitation intensity."),
    ]
    for metric, value, unit, source_url, note in source_metrics:
        add_metric(
            rows,
            row_type="icing_rotor_packet_source_inventory",
            name="mdpi_drones_2022_6_68",
            metric=metric,
            value=value,
            unit=unit,
            source_url=source_url,
            evidence_role="source_inventory",
            note=note,
        )


def add_condition_grid(rows: list[dict[str, object]]) -> None:
    for condition in TABLE1_CONDITIONS:
        name = f"lambda{condition['lambda_g_dm2_h']:.0f}_MVD{condition['mvd_um']:.0f}"
        metrics = [
            ("lambda_g_dm2_h", condition["lambda_g_dm2_h"], "g/dm^2/h"),
            ("equivalent_rain_mm_h", lambda_to_mm_h(condition["lambda_g_dm2_h"]), "mm/h"),
            ("mvd_um", condition["mvd_um"], "um"),
            ("lwc_g_m3", condition["lwc_g_m3"], "g/m^3"),
            ("rationale", condition["rationale"], "text"),
        ]
        for metric, value, unit in metrics:
            add_metric(
                rows,
                row_type="icing_rotor_packet_condition_grid",
                name=name,
                metric=metric,
                value=value,
                unit=unit,
                evidence_role="icing_condition_table1",
                note="Transcribed from Table 1; lambda converts to mm/h using 1 g/dm^2/h = 0.1 mm/h water depth.",
            )


def add_test_case_rows(rows: list[dict[str, object]], current_rain_loss_percent: float) -> list[dict[str, float]]:
    cases = [derived_case(case) for case in TABLE4_CASES]
    for case in cases:
        metrics = [
            ("mvd_um", case["mvd_um"], "um"),
            ("temperature_c", case["temperature_c"], "C"),
            ("height_m", case["height_m"], "m"),
            ("rpm", case["rpm"], "rpm"),
            ("pitch_deg", case["pitch_deg"], "deg"),
            ("lambda_g_dm2_h", case["lambda_g_dm2_h"], "g/dm^2/h"),
            ("equivalent_rain_mm_h", case["equivalent_rain_mm_h"], "mm/h"),
            ("lwc_g_m3", case["lwc_g_m3"], "g/m^3"),
            ("rotation_hz", case["rotation_hz"], "Hz"),
            ("tip_speed_m_s", case["tip_speed_m_s"], "m/s"),
            ("ct_star_percent_s", case["ct_star_percent_s"], "%/s"),
            ("cq_star_percent_s", case["cq_star_percent_s"], "%/s"),
            ("cq_plus_percent_s", case["cq_plus_percent_s"], "%/s"),
            ("p_plus_percent_s", case["p_plus_percent_s"], "%/s"),
            ("icing_time_s", case["icing_time_s"], "s"),
            ("projected_total_ct_loss_percent", case["projected_total_ct_loss_percent"], "%"),
            ("projected_total_cq_increase_percent", case["projected_total_cq_increase_percent"], "%"),
            ("projected_total_cq_required_increase_percent", case["projected_total_cq_required_increase_percent"], "%"),
            ("projected_total_power_required_increase_percent", case["projected_total_power_required_increase_percent"], "%"),
            ("projected_end_ct_scale", case["projected_end_ct_scale"], "x"),
            (
                "projected_ct_loss_over_current_full_rain_loss",
                case["projected_total_ct_loss_percent"] / current_rain_loss_percent if current_rain_loss_percent > 0.0 else math.nan,
                "x",
            ),
        ]
        for metric, value, unit in metrics:
            add_metric(
                rows,
                row_type="icing_rotor_packet_table4_case",
                name=case_name(case),
                metric=metric,
                value=value,
                unit=unit,
                evidence_role="icing_degradation_rate",
                note=(
                    "Table 4 values are linear rates during icing. Endpoint projections multiply the published rate by "
                    "published icing time; use as an order-of-magnitude degradation envelope, not as ordinary rain wetness."
                ),
            )
    return cases


def add_height_ratio_rows(rows: list[dict[str, object]], cases: list[dict[str, float]]) -> None:
    by_key = {case_key(case): case for case in cases}
    metrics = [
        ("abs_ct_star_rate_h4_over_h2", "ct_star_percent_s", True),
        ("cq_star_rate_h4_over_h2", "cq_star_percent_s", False),
        ("cq_plus_rate_h4_over_h2", "cq_plus_percent_s", False),
        ("p_plus_rate_h4_over_h2", "p_plus_percent_s", False),
        ("icing_time_h4_over_h2", "icing_time_s", False),
        ("projected_ct_loss_h4_over_h2", "projected_total_ct_loss_percent", False),
        ("projected_power_required_h4_over_h2", "projected_total_power_required_increase_percent", False),
    ]
    for mvd in sorted({case["mvd_um"] for case in cases}):
        for temp in sorted({case["temperature_c"] for case in cases if case["mvd_um"] == mvd}):
            h2 = by_key[(mvd, temp, 2.0)]
            h4 = by_key[(mvd, temp, 4.0)]
            name = f"MVD{mvd:.0f}_T{temp:.0f}_h4_over_h2"
            for metric, key, use_abs in metrics:
                numerator = abs(h4[key]) if use_abs else h4[key]
                denominator = abs(h2[key]) if use_abs else h2[key]
                add_metric(
                    rows,
                    row_type="icing_rotor_packet_height_ratio",
                    name=name,
                    metric=metric,
                    value=numerator / denominator if denominator else math.nan,
                    unit="x",
                    evidence_role="height_sensitivity",
                    note=(
                        "Derived h=4 m / h=2 m ratio from Table 4. The paper attributes most faster h=4 m icing "
                        "cases to higher effective precipitation intensity near the nozzles, not to dry ground effect."
                    ),
                )


def add_temperature_ratio_rows(rows: list[dict[str, object]], cases: list[dict[str, float]]) -> None:
    by_key = {case_key(case): case for case in cases}
    metrics = [
        ("abs_ct_star_rate_minus15_over_minus5", "ct_star_percent_s", True),
        ("cq_star_rate_minus15_over_minus5", "cq_star_percent_s", False),
        ("p_plus_rate_minus15_over_minus5", "p_plus_percent_s", False),
        ("icing_time_minus15_over_minus5", "icing_time_s", False),
        ("projected_ct_loss_minus15_over_minus5", "projected_total_ct_loss_percent", False),
        ("projected_power_required_minus15_over_minus5", "projected_total_power_required_increase_percent", False),
    ]
    for mvd in sorted({case["mvd_um"] for case in cases}):
        for height in sorted({case["height_m"] for case in cases if case["mvd_um"] == mvd}):
            minus5 = by_key[(mvd, -5.0, height)]
            minus15 = by_key[(mvd, -15.0, height)]
            name = f"MVD{mvd:.0f}_h{height:.0f}m_minus15_over_minus5"
            for metric, key, use_abs in metrics:
                numerator = abs(minus15[key]) if use_abs else minus15[key]
                denominator = abs(minus5[key]) if use_abs else minus5[key]
                add_metric(
                    rows,
                    row_type="icing_rotor_packet_temperature_ratio",
                    name=name,
                    metric=metric,
                    value=numerator / denominator if denominator else math.nan,
                    unit="x",
                    evidence_role="temperature_sensitivity",
                    note="Derived from Table 4 by comparing -15 C against -5 C at the same droplet size and rotor height.",
                )


def add_mvd_ratio_rows(rows: list[dict[str, object]], cases: list[dict[str, float]]) -> None:
    by_key = {case_key(case): case for case in cases}
    metrics = [
        ("abs_ct_star_rate_120_over_800", "ct_star_percent_s", True),
        ("cq_star_rate_120_over_800", "cq_star_percent_s", False),
        ("p_plus_rate_120_over_800", "p_plus_percent_s", False),
        ("icing_time_120_over_800", "icing_time_s", False),
        ("projected_ct_loss_120_over_800", "projected_total_ct_loss_percent", False),
        ("projected_power_required_120_over_800", "projected_total_power_required_increase_percent", False),
        ("table1_lwc_120_over_800", "lwc_g_m3", False),
    ]
    for temp in sorted({case["temperature_c"] for case in cases}):
        for height in sorted({case["height_m"] for case in cases}):
            mvd120 = by_key[(120.0, temp, height)]
            mvd800 = by_key[(800.0, temp, height)]
            name = f"T{temp:.0f}_h{height:.0f}m_MVD120_over_800"
            for metric, key, use_abs in metrics:
                numerator = abs(mvd120[key]) if use_abs else mvd120[key]
                denominator = abs(mvd800[key]) if use_abs else mvd800[key]
                add_metric(
                    rows,
                    row_type="icing_rotor_packet_mvd_ratio",
                    name=name,
                    metric=metric,
                    value=numerator / denominator if denominator else math.nan,
                    unit="x",
                    evidence_role="droplet_size_sensitivity",
                    note=(
                        "Derived from Table 4 and Table 1. At the same lambda=80 g/dm^2/h, the paper's LWC conversion "
                        "makes 120 um cases much higher LWC than 800 um cases."
                    ),
                )


def add_distributions(rows: list[dict[str, object]], cases: list[dict[str, float]]) -> None:
    distributions = [
        ("abs_ct_star_rate", [abs(case["ct_star_percent_s"]) for case in cases], "%/s"),
        ("cq_star_rate", [case["cq_star_percent_s"] for case in cases], "%/s"),
        ("cq_plus_rate", [case["cq_plus_percent_s"] for case in cases], "%/s"),
        ("p_plus_rate", [case["p_plus_percent_s"] for case in cases], "%/s"),
        ("icing_time", [case["icing_time_s"] for case in cases], "s"),
        ("projected_total_ct_loss", [case["projected_total_ct_loss_percent"] for case in cases], "%"),
        ("projected_total_cq_increase", [case["projected_total_cq_increase_percent"] for case in cases], "%"),
        ("projected_total_cq_required", [case["projected_total_cq_required_increase_percent"] for case in cases], "%"),
        ("projected_total_power_required", [case["projected_total_power_required_increase_percent"] for case in cases], "%"),
    ]
    for prefix, values, unit in distributions:
        for suffix, p in [("min", 0.0), ("p25", 25.0), ("median", 50.0), ("p75", 75.0), ("max", 100.0)]:
            add_metric(
                rows,
                row_type="icing_rotor_packet_distribution",
                name="table4_case_distribution",
                metric=f"{prefix}_{suffix}",
                value=percentile(values, p),
                unit=unit,
                evidence_role="table4_distribution",
                note="Distribution across the 8 published Table 4 icing cases.",
            )

    strongest_ct = max(cases, key=lambda case: case["projected_total_ct_loss_percent"])
    weakest_ct = min(cases, key=lambda case: case["projected_total_ct_loss_percent"])
    strongest_power = max(cases, key=lambda case: case["projected_total_power_required_increase_percent"])
    longest = max(cases, key=lambda case: case["icing_time_s"])
    for metric, case, key, unit, note in [
        ("strongest_projected_ct_loss_case", strongest_ct, "projected_total_ct_loss_percent", "%", "Largest projected Table 4 CT loss."),
        ("weakest_projected_ct_loss_case", weakest_ct, "projected_total_ct_loss_percent", "%", "Smallest projected Table 4 CT loss."),
        ("strongest_projected_power_required_case", strongest_power, "projected_total_power_required_increase_percent", "%", "Largest projected P+ endpoint."),
        ("longest_icing_time_case", longest, "icing_time_s", "s", "Longest published icing time before stopping condition."),
    ]:
        add_metric(
            rows,
            row_type="icing_rotor_packet_extreme_case",
            name=case_name(case),
            metric=metric,
            value=case[key],
            unit=unit,
            evidence_role="table4_extreme_case",
            note=note,
        )


def add_current_model_comparison(
    rows: list[dict[str, object]],
    cases: list[dict[str, float]],
    formula: dict[str, float | str],
) -> None:
    rain_loss = float(formula["full_wetness_thrust_loss_percent"])
    projected_losses = [case["projected_total_ct_loss_percent"] for case in cases]
    p_plus = [case["projected_total_power_required_increase_percent"] for case in cases]
    comparisons = [
        ("current_precipitation_formula", formula["formula"], "text"),
        ("current_full_wetness_thrust_loss_percent", rain_loss, "%"),
        ("icing_projected_ct_loss_min_percent", min(projected_losses), "%"),
        ("icing_projected_ct_loss_median_percent", percentile(projected_losses, 50.0), "%"),
        ("icing_projected_ct_loss_max_percent", max(projected_losses), "%"),
        ("icing_projected_ct_loss_min_over_current_rain_loss", min(projected_losses) / rain_loss, "x"),
        ("icing_projected_ct_loss_median_over_current_rain_loss", percentile(projected_losses, 50.0) / rain_loss, "x"),
        ("icing_projected_ct_loss_max_over_current_rain_loss", max(projected_losses) / rain_loss, "x"),
        ("icing_projected_power_required_min_percent", min(p_plus), "%"),
        ("icing_projected_power_required_median_percent", percentile(p_plus, 50.0), "%"),
        ("icing_projected_power_required_max_percent", max(p_plus), "%"),
        ("ordinary_rain_vs_icing_recommendation", "keep separate; icing is time-accumulating frozen contamination", "text"),
    ]
    for metric, value, unit in comparisons:
        add_metric(
            rows,
            row_type="icing_rotor_packet_current_model_comparison",
            name="current_rain_formula_vs_mdpi_icing",
            metric=metric,
            value=value,
            unit=unit,
            source_url=f"{SOURCE_URL}; {repo_path(DRONE_PHYSICS)}",
            evidence_role="current_model_context",
            note="Current precipitationThrustScale is ordinary rain/wetness context; MDPI rows are icing-only.",
        )


def add_method(rows: list[dict[str, object]]) -> None:
    add_metric(
        rows,
        row_type="icing_rotor_packet_method",
        name="handoff_guidance",
        metric="recommended_use",
        value=(
            "Use this packet as an icing-only degradation-rate and severity source. Table 4 provides CT/CQ/C+Q/P+ "
            "linear rates at 4950 rpm, 11.7 deg, and lambda=80 g/dm^2/h; endpoint rows are simple rate*time "
            "projections. Do not merge these coefficients into the ordinary rain wetness model without a distinct "
            "ice-accretion state, accumulation time, temperature gate, and de-icing/shedding behavior."
        ),
        unit="text",
        evidence_role="handoff_guidance",
        note="The source is a 0.66 m Bell APT70-style hover rotor, not a 5-inch FPV propeller.",
    )


def build_packet() -> list[dict[str, object]]:
    formula = parse_current_precipitation_loss_percent()
    rows: list[dict[str, object]] = []
    add_source_inventory(rows)
    add_condition_grid(rows)
    cases = add_test_case_rows(rows, float(formula["full_wetness_thrust_loss_percent"]))
    add_height_ratio_rows(rows, cases)
    add_temperature_ratio_rows(rows, cases)
    add_mvd_ratio_rows(rows, cases)
    add_distributions(rows, cases)
    add_current_model_comparison(rows, cases, formula)
    add_method(rows)
    return rows


def sync_summary(packet_rows: list[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("icing_rotor_packet_")]
    added: list[dict[str, object]] = []
    for row in packet_rows:
        added.append(
            {
                "category": row["row_type"],
                "name": row["name"],
                "metric": row["metric"],
                "value": value_text(row["value"]),
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
