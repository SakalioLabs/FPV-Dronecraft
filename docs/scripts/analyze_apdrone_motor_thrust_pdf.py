"""Transcribe and analyze APdrone's image-only 2507 motor datasheet.

Outputs:
  docs/data/apdrone_motor_thrust_pdf_reference.csv

The APdrone Mendeley component PDF is image-only. The raw images are cached
under docs/data/raw/apdrone_zgsvdtxnfh_v2/component_datasheets/, and the
numeric rows below are a manual transcription of the visible specification
and thrust-test tables from pages 1, 3, and 4.
"""

from __future__ import annotations

import csv
import math
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "apdrone_zgsvdtxnfh_v2"
PDF = RAW / "component_datasheets" / "P03_2507 1800KV Brushless Motor.pdf"
IMAGE_DIR = RAW / "component_datasheets" / "extracted_motor_pdf_images"
YSIDO_HTML = DATA / "raw" / "ysido_2507_motor" / "rcdrone_ysido_2507_1800kv.html"
OUTPUT = DATA / "apdrone_motor_thrust_pdf_reference.csv"
BETAF_CURRENT_REF = DATA / "betaflight_apdrone_current_unit_reference.csv"
DRONE_CONFIG = ROOT / "drone-sim-core" / "src" / "main" / "java" / "com" / "tenicana" / "dronecraft" / "sim" / "DroneConfig.java"
DRONE_CONFIG_NUMERIC_CONSTANT_FILES = [
    DRONE_CONFIG.parent / "RateEnvelopeCalibration.java",
    DRONE_CONFIG.parent / "SensorNoiseCalibration.java",
    DRONE_CONFIG,
]

SOURCE_PAGE = "https://data.mendeley.com/datasets/zgsvdtxnfh/2"
DOI = "10.17632/zgsvdtxnfh.2"
YSIDO_PRODUCT_PAGE = "https://rcdrone.top/products/ysido-2507-1800kv-brushless-motor"
G0 = 9.80665
RAD_PER_SEC_TO_RPM = 60.0 / (2.0 * math.pi)
RPM_TO_RAD_PER_SEC = 2.0 * math.pi / 60.0


SPEC_ROWS = [
    ("page_1", "brand", "YSIDO", "", "Spanish product/spec page in APdrone PDF."),
    ("page_1", "article_name", "2507 1800KV 3-5S brushless motor", "", "Spanish page heading/specification text."),
    ("page_1", "kv", 1800.0, "rpm_per_v", "Page 1 and page 3 list 1800KV."),
    ("page_1", "lipo_cell_support", "3-5S", "LiPo cells", "Page 1/page 3 specification."),
    ("page_1", "height_mm", 20.5, "mm", "Page 1 specification line."),
    ("page_1", "width_mm", 30.5, "mm", "Page 1 specification line."),
    ("page_1", "shaft_diameter_mm", 5.0, "mm", "Page 1 specification line."),
    ("page_1", "cable_length_mm", 115.0, "mm", "Page 1 specification line."),
    ("page_1", "weight_g_page1", 43.2, "g", "Page 1 specification line."),
    ("page_1", "max_thrust_g_page1", 1488.0, "g", "Page 1 Spanish page lists maximum thrust 1488G with 4S/5S note."),
    ("page_3", "idle_current_10v_a", 0.89, "A", "Page 3 table."),
    ("page_3", "weight_g_page3", 39.0, "g", "Page 3 table; conflicts with page 1 43.2 g."),
    ("page_3", "max_continuous_current_a", 42.0, "A", "Page 3 table."),
    ("page_3", "max_continuous_power_w", 840.0, "W", "Page 3 table."),
    ("page_3", "max_thrust_g_page3", 1488.0, "g", "Page 3 table."),
    ("page_3", "configuration", "12N14P", "", "Page 3 table."),
    ("page_3", "motor_resistance_ohm", 0.0586, "ohm", "Page 3 table lists RM."),
    ("page_3", "stator_diameter_mm", 25.0, "mm", "Page 3 table."),
    ("page_3", "stator_thickness_mm", 7.0, "mm", "Page 3 table."),
    ("page_3", "motor_diameter_mm", 30.8, "mm", "Page 3 table and page 2 drawing."),
    ("page_3", "motor_body_length_mm", 20.0, "mm", "Page 3 table."),
    ("page_3", "overall_shaft_length_mm", 35.0, "mm", "Page 3 table."),
    ("page_3", "prop_adapter_shaft", "M5", "", "Page 3 table."),
    ("page_3", "bolt_hole_spacing_mm", 16.0, "mm", "Page 3 table."),
    ("page_3", "bolt_thread", "M3", "", "Page 3 table."),
    ("page_3", "recommended_prop_in", 7.0, "in", "Page 3 table lists propeller 7 inch."),
]

YSIDO_WEB_ROWS = [
    ("web_model", "2507 1800KV", "", "RCDrone product page repeats the YSIDO 2507 1800KV model name."),
    ("web_kv", 1800.0, "rpm_per_v", "RCDrone structured product description lists KV Rating 1800KV."),
    ("web_input_voltage", "3-6S LiPo", "LiPo cells", "RCDrone structured product description lists 3-6S LiPo input."),
    ("web_weight_g", 43.2, "g", "RCDrone structured product description lists 43.2 g."),
    ("web_application", "5-inch FPV racing drones", "", "RCDrone overview says the motor is designed for high-performance 5-inch FPV racing drones."),
    ("web_test_table_presence", "throttle_voltage_current_power_thrust_efficiency_temperature", "", "RCDrone image alt/text says the YSIDO motor test data includes throttle, voltage, current, power, thrust, efficiency, and temperature."),
]


THRUST_TEST_ROWS = [
    # page, item, prop, throttle, volts, amps, watts, thrust_g, eff_g_w, temp_c
    ("page_3", "R2507_1800kv", "7056 3R", 60, 16.00, 10.8, 172.8, 675, 3.91, 58),
    ("page_3", "R2507_1800kv", "7056 3R", 70, 16.00, 15.2, 243.2, 834, 3.43, 58),
    ("page_3", "R2507_1800kv", "7056 3R", 80, 16.00, 20.1, 321.6, 991, 3.08, 58),
    ("page_3", "R2507_1800kv", "7056 3R", 90, 16.00, 25.5, 408.0, 1123, 2.75, 58),
    ("page_3", "R2507_1800kv", "7056 3R", 100, 16.00, 33.3, 532.8, 1308, 2.45, 58),
    ("page_4", "5043", "5043", 50, 25.01, 12.43, 310.6, 711, 2.29, 47),
    ("page_4", "5043", "5043", 60, 24.98, 16.08, 402.2, 829, 2.06, 47),
    ("page_4", "5043", "5043", 70, 24.91, 20.34, 506.4, 933, 1.84, 47),
    ("page_4", "5043", "5043", 80, 24.73, 23.92, 591.5, 1121, 1.89, 47),
    ("page_4", "5043", "5043", 90, 24.48, 29.81, 729.3, 1319, 1.81, 47),
    ("page_4", "5043", "5043", 100, 24.39, 33.04, 805.9, 1394, 1.73, 47),
    ("page_4", "5045", "5045", 50, 25.04, 12.43, 311.3, 734, 2.36, 49),
    ("page_4", "5045", "5045", 60, 24.98, 17.23, 430.4, 847, 1.96, 49),
    ("page_4", "5045", "5045", 70, 24.87, 19.05, 473.9, 951, 2.01, 49),
    ("page_4", "5045", "5045", 80, 24.71, 22.97, 567.5, 1140, 2.01, 49),
    ("page_4", "5045", "5045", 90, 24.48, 28.92, 707.8, 1351, 1.91, 49),
    ("page_4", "5045", "5045", 100, 24.39, 32.16, 784.5, 1444, 1.84, 49),
    ("page_4", "6045R", "6045R", 50, 17.24, 9.53, 164.2, 570, 3.47, 43),
    ("page_4", "6045R", "6045R", 60, 17.12, 12.09, 207.1, 678, 3.27, 43),
    ("page_4", "6045R", "6045R", 70, 16.98, 15.74, 267.4, 824, 3.08, 43),
    ("page_4", "6045R", "6045R", 80, 16.90, 20.81, 351.7, 990, 2.82, 43),
    ("page_4", "6045R", "6045R", 90, 16.67, 26.49, 441.7, 1147, 2.60, 43),
    ("page_4", "6045R", "6045R", 100, 16.59, 29.66, 492.1, 1227, 2.49, 43),
]


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def finite_or_blank(value: str | int | float) -> str | int | float:
    if isinstance(value, float) and not math.isfinite(value):
        return ""
    return value


def csv_float(raw: str | int | float | None) -> float:
    try:
        return float(raw)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return float("nan")


def computed_thrust_points() -> list[dict[str, str | int | float]]:
    points: list[dict[str, str | int | float]] = []
    for page, item, prop, throttle, volts, amps, watts, thrust_g, eff_g_w, temp_c in THRUST_TEST_ROWS:
        points.append(
            {
                "page": page,
                "item": item,
                "prop": prop,
                "throttle_percent": throttle,
                "voltage_v": volts,
                "current_a": amps,
                "power_w": watts,
                "thrust_g": thrust_g,
                "thrust_n": thrust_g / 1000.0 * G0,
                "efficiency_g_per_w": eff_g_w,
                "operating_temperature_c": temp_c,
            }
        )
    return points


def power_law_fit(
    points: list[dict[str, str | int | float]],
    y_key: str,
) -> dict[str, float]:
    valid = [
        (csv_float(point["thrust_n"]), csv_float(point[y_key]))
        for point in points
        if csv_float(point["thrust_n"]) > 0.0 and csv_float(point[y_key]) > 0.0
    ]
    if len(valid) < 2:
        return {
            "sample_count": len(valid),
            "coefficient": float("nan"),
            "exponent": float("nan"),
            "log_r2": float("nan"),
            "rms_relative_error": float("nan"),
            "thrust_min_n": float("nan"),
            "thrust_max_n": float("nan"),
            "observed_min": float("nan"),
            "observed_max": float("nan"),
        }

    xs = [math.log(x) for x, _ in valid]
    ys = [math.log(y) for _, y in valid]
    x_mean = sum(xs) / len(xs)
    y_mean = sum(ys) / len(ys)
    sxx = sum((x - x_mean) ** 2 for x in xs)
    sxy = sum((x - x_mean) * (y - y_mean) for x, y in zip(xs, ys))
    exponent = sxy / sxx if sxx > 0.0 else float("nan")
    intercept = y_mean - exponent * x_mean
    coefficient = math.exp(intercept)
    predictions = [coefficient * (x ** exponent) for x, _ in valid]
    observed = [y for _, y in valid]
    log_predictions = [math.log(prediction) for prediction in predictions]
    ss_res = sum((y - y_hat) ** 2 for y, y_hat in zip(ys, log_predictions))
    ss_tot = sum((y - y_mean) ** 2 for y in ys)
    log_r2 = 1.0 - ss_res / ss_tot if ss_tot > 0.0 else float("nan")
    rms_relative_error = math.sqrt(
        sum(((prediction - y) / y) ** 2 for prediction, y in zip(predictions, observed)) / len(observed)
    )

    return {
        "sample_count": len(valid),
        "coefficient": coefficient,
        "exponent": exponent,
        "log_r2": log_r2,
        "rms_relative_error": rms_relative_error,
        "thrust_min_n": min(x for x, _ in valid),
        "thrust_max_n": max(x for x, _ in valid),
        "observed_min": min(observed),
        "observed_max": max(observed),
    }


def predict_power_law(fit: dict[str, float], x: float) -> float:
    coefficient = fit["coefficient"]
    exponent = fit["exponent"]
    if not (math.isfinite(coefficient) and math.isfinite(exponent) and x > 0.0):
        return float("nan")
    return coefficient * (x ** exponent)


def invert_power_law(fit: dict[str, float], y: float) -> float:
    coefficient = fit["coefficient"]
    exponent = fit["exponent"]
    if not (math.isfinite(coefficient) and math.isfinite(exponent) and y > 0.0 and coefficient > 0.0 and exponent != 0.0):
        return float("nan")
    return (y / coefficient) ** (1.0 / exponent)


def range_context(value: float, lower: float, upper: float, label: str) -> str:
    if not (math.isfinite(value) and math.isfinite(lower) and math.isfinite(upper)):
        return "unknown"
    if value < lower:
        return f"below_{label}_fit_range"
    if value > upper:
        return f"above_{label}_fit_range"
    return f"inside_{label}_fit_range"


def evaluate_java_double_expression(expression: str, variables: dict[str, float]) -> float:
    clean = expression.strip()
    clean = re.sub(r"//.*", "", clean)
    clean = clean.replace("Math.sqrt", "sqrt")
    clean = clean.replace("Math.toRadians", "radians")
    clean = clean.replace("Math.log", "log")
    clean = clean.replace("Math.PI", "pi")
    clean = re.sub(r"\b(?:RateEnvelopeCalibration|SensorNoiseCalibration|DroneConfig)\.", "", clean)
    allowed = {"sqrt": math.sqrt, "radians": math.radians, "log": math.log, "pi": math.pi, **variables}
    try:
        return float(eval(clean, {"__builtins__": {}}, allowed))
    except Exception as exc:
        raise RuntimeError(f"Could not evaluate Java double expression {expression!r}") from exc


def parse_java_numeric_constants() -> dict[str, float]:
    constants: dict[str, float] = {}
    for path in DRONE_CONFIG_NUMERIC_CONSTANT_FILES:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        for match in re.finditer(
            r"public\s+static\s+final\s+(?:double|int)\s+(?P<name>\w+)\s*=\s*(?P<expr>[^;]+);",
            text,
        ):
            try:
                constants[match.group("name")] = evaluate_java_double_expression(match.group("expr"), constants)
            except RuntimeError:
                continue
    return constants


def parse_apdrone_project_values() -> dict[str, float]:
    text = DRONE_CONFIG.read_text(encoding="utf-8", errors="ignore")
    match = re.search(r"public static DroneConfig apDrone\(\) \{(?P<body>.*?)\n\t\}", text, re.DOTALL)
    if not match:
        raise RuntimeError("Could not find apDrone() block in DroneConfig.java")
    body = match.group("body")
    variables = parse_java_numeric_constants()
    for found in re.finditer(r"\bdouble\s+(?P<name>\w+)\s*=\s*(?P<expr>[^;]+);", body):
        try:
            variables[found.group("name")] = evaluate_java_double_expression(found.group("expr"), variables)
        except RuntimeError:
            continue

    def get_double(name: str) -> float:
        value = variables.get(name)
        if value is None:
            raise RuntimeError(f"Could not find apDrone double {name}")
        return value

    return {
        "mass_kg": 0.6284,
        "max_rotor_thrust_n": get_double("maxRotorThrust"),
        "thrust_coefficient": get_double("thrustCoefficient"),
        "rotor_radius_m": get_double("rotorRadius"),
        "nominal_battery_voltage_v": 16.8,
        "max_battery_current_a": 150.0,
    }


def add_spec_rows(rows: list[dict[str, str | int | float]]) -> None:
    for page, metric, value, unit, note in SPEC_ROWS:
        rows.append(
            {
                "row_type": "apdrone_motor_pdf_spec",
                "source_scope": "apdrone_image_only_motor_pdf_manual_transcription",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "local_source_file": repo_path(PDF),
                "local_image_file": repo_path(IMAGE_DIR / f"{page}_image_1.png")
                if page != "page_3"
                else repo_path(IMAGE_DIR / "page_3_image_1.jpg"),
                "metric": metric,
                "value": value,
                "unit": unit,
                "note": note,
            }
        )

    for metric, value, unit, note in YSIDO_WEB_ROWS:
        rows.append(
            {
                "row_type": "ysido_web_product_corroboration",
                "source_scope": "rcdrone_ysido_2507_product_page",
                "source_page": YSIDO_PRODUCT_PAGE,
                "local_source_file": repo_path(YSIDO_HTML) if YSIDO_HTML.exists() else "",
                "metric": metric,
                "value": value,
                "unit": unit,
                "note": note,
            }
        )


def add_thrust_rows(rows: list[dict[str, str | int | float]], project: dict[str, float]) -> None:
    k_project = project["thrust_coefficient"]
    project_max_thrust = project["max_rotor_thrust_n"]
    motor_kv = 1800.0

    grouped: dict[str, list[dict[str, str | int | float]]] = {}
    for page, item, prop, throttle, volts, amps, watts, thrust_g, eff_g_w, temp_c in THRUST_TEST_ROWS:
        thrust_n = thrust_g / 1000.0 * G0
        ideal_no_load_rpm = motor_kv * volts
        project_required_rpm = math.sqrt(thrust_n / k_project) * RAD_PER_SEC_TO_RPM
        no_load_k_lower_bound = thrust_n / ((ideal_no_load_rpm * RPM_TO_RAD_PER_SEC) ** 2)
        current_share_of_apdrone_pack = amps / project["max_battery_current_a"]
        row = {
            "row_type": "apdrone_motor_pdf_thrust_test_point",
            "source_scope": "apdrone_image_only_motor_pdf_manual_transcription",
            "source_page": SOURCE_PAGE,
            "doi": DOI,
            "local_source_file": repo_path(PDF),
            "local_image_file": repo_path(IMAGE_DIR / "page_3_image_1.jpg")
            if page == "page_3"
            else repo_path(IMAGE_DIR / "page_4_image_1.png"),
            "item": item,
            "prop": prop,
            "throttle_percent": throttle,
            "voltage_v": volts,
            "current_a": amps,
            "power_w": watts,
            "thrust_g": thrust_g,
            "thrust_n": thrust_n,
            "efficiency_g_per_w": eff_g_w,
            "operating_temperature_c": temp_c,
            "motor_kv_rpm_per_v": motor_kv,
            "ideal_no_load_rpm_at_measured_voltage": ideal_no_load_rpm,
            "project_required_rpm_if_k_1p45e_minus_6": project_required_rpm,
            "project_required_rpm_over_no_load_rpm": project_required_rpm / ideal_no_load_rpm,
            "no_load_rpm_based_k_lower_bound": no_load_k_lower_bound,
            "apdrone_project_max_rotor_thrust_n": project_max_thrust,
            "thrust_over_project_max_rotor_thrust": thrust_n / project_max_thrust,
            "current_over_project_apdrone_pack_limit": current_share_of_apdrone_pack,
            "note": "PDF thrust table has volts/current/power/thrust but no measured RPM; no-load RPM gives only a lower-bound k, while project-required RPM tests whether current k is plausible.",
        }
        rows.append(row)
        grouped.setdefault(item, []).append(row)

    for item, item_rows in grouped.items():
        best = max(item_rows, key=lambda r: float(r["thrust_n"]))
        rows.append(
            {
                "row_type": "apdrone_motor_pdf_prop_summary",
                "source_scope": "apdrone_image_only_motor_pdf_manual_transcription",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "local_source_file": repo_path(PDF),
                "item": item,
                "prop": best["prop"],
                "max_table_thrust_g": best["thrust_g"],
                "max_table_thrust_n": best["thrust_n"],
                "max_table_voltage_v": best["voltage_v"],
                "max_table_current_a": best["current_a"],
                "max_table_power_w": best["power_w"],
                "max_table_efficiency_g_per_w": best["efficiency_g_per_w"],
                "project_max_rotor_thrust_n": project_max_thrust,
                "project_max_over_table_max": project_max_thrust / float(best["thrust_n"]),
                "table_max_over_project_max": float(best["thrust_n"]) / project_max_thrust,
                "project_required_rpm_if_k_1p45e_minus_6": best["project_required_rpm_if_k_1p45e_minus_6"],
                "project_required_rpm_over_no_load_rpm": best["project_required_rpm_over_no_load_rpm"],
                "note": "Best visible point for each prop group in the APdrone motor PDF.",
            }
        )


def selected_fit_scopes(points: list[dict[str, str | int | float]]) -> list[tuple[str, str, list[dict[str, str | int | float]]]]:
    def by_prop(prop_name: str) -> list[dict[str, str | int | float]]:
        return [point for point in points if str(point["prop"]) == prop_name]

    return [
        ("all_visible_pdf_points", "All visible APdrone YSIDO 2507 thrust-table rows across 4S/6S and 5-7 inch props.", points),
        ("four_s_visible_points", "Only visible rows near 4S voltage: 16 V 7056 3R and 16.6-17.2 V 6045R.", [point for point in points if csv_float(point["voltage_v"]) <= 18.0]),
        ("five_inch_six_s_points", "Only 6S 5-inch 5043/5045 rows; closest diameter/pitch family to Foxeer Donut 5145 but at higher voltage.", [point for point in points if str(point["prop"]) in {"5043", "5045"}]),
        ("prop_7056_3r_4s", "Page 3 4S/16 V 7056 3R rows.", by_prop("7056 3R")),
        ("prop_6045r_4s", "Page 4 4S/6045R rows.", by_prop("6045R")),
        ("prop_5045_6s", "Page 4 6S/5045 rows.", by_prop("5045")),
    ]


def add_fit_rows(rows: list[dict[str, str | int | float]], project: dict[str, float]) -> None:
    points = computed_thrust_points()
    hover_thrust_n = project["mass_kg"] * G0 / 4.0
    project_max_thrust_n = project["max_rotor_thrust_n"]
    pdf_claim_thrust_n = 1488.0 / 1000.0 * G0
    vehicle_weight_n = project["mass_kg"] * G0

    operating_points = [
        ("project_hover_per_motor", hover_thrust_n, "Current apDrone mass divided across four rotors."),
        ("project_max_rotor_thrust", project_max_thrust_n, "Current apDrone maxRotorThrust."),
        ("pdf_claim_max_thrust", pdf_claim_thrust_n, "Headline 1488 g max-thrust claim in the APdrone motor PDF."),
    ]

    current_fits: dict[str, dict[str, float]] = {}
    power_fits: dict[str, dict[str, float]] = {}

    for fit_scope, fit_note, scope_points in selected_fit_scopes(points):
        current_fit = power_law_fit(scope_points, "current_a")
        power_fit = power_law_fit(scope_points, "power_w")
        current_fits[fit_scope] = current_fit
        power_fits[fit_scope] = power_fit
        for target_name, target_label, fit in [
            ("current_a", "I = a * thrust_N^b", current_fit),
            ("power_w", "P = a * thrust_N^b", power_fit),
        ]:
            rows.append(
                {
                    "row_type": "apdrone_motor_pdf_power_current_fit",
                    "source_scope": "apdrone_motor_pdf_power_law_fit",
                    "source_page": SOURCE_PAGE,
                    "doi": DOI,
                    "local_source_file": repo_path(PDF),
                    "fit_scope": fit_scope,
                    "fit_target": target_name,
                    "fit_model": target_label,
                    "fit_coefficient_a": fit["coefficient"],
                    "fit_exponent_b": fit["exponent"],
                    "fit_log_r2": fit["log_r2"],
                    "fit_rms_relative_error": fit["rms_relative_error"],
                    "fit_sample_count": fit["sample_count"],
                    "fit_thrust_min_n": fit["thrust_min_n"],
                    "fit_thrust_max_n": fit["thrust_max_n"],
                    "fit_observed_min": fit["observed_min"],
                    "fit_observed_max": fit["observed_max"],
                    "note": fit_note,
                }
            )

        for operating_point, thrust_n, operating_note in operating_points:
            predicted_current_per_motor = predict_power_law(current_fit, thrust_n)
            predicted_power_per_motor = predict_power_law(power_fit, thrust_n)
            rows.append(
                {
                    "row_type": "apdrone_motor_pdf_operating_point_projection",
                    "source_scope": "current_apdrone_operating_point_vs_motor_pdf_fit",
                    "source_page": SOURCE_PAGE,
                    "doi": DOI,
                    "local_source_file": repo_path(PDF),
                    "fit_scope": fit_scope,
                    "operating_point": operating_point,
                    "per_motor_thrust_n": thrust_n,
                    "total_thrust_n": thrust_n * 4.0,
                    "total_thrust_over_apdrone_weight": thrust_n * 4.0 / vehicle_weight_n,
                    "predicted_current_per_motor_a": predicted_current_per_motor,
                    "predicted_current_total_a": predicted_current_per_motor * 4.0,
                    "predicted_power_per_motor_w": predicted_power_per_motor,
                    "predicted_power_total_w": predicted_power_per_motor * 4.0,
                    "current_over_project_pack_limit": (predicted_current_per_motor * 4.0) / project["max_battery_current_a"],
                    "thrust_range_context": range_context(thrust_n, current_fit["thrust_min_n"], current_fit["thrust_max_n"], "thrust"),
                    "note": f"{operating_note} Projection uses the {fit_scope} current/power fit; check thrust_range_context before treating it as interpolation.",
                }
            )

    if not BETAF_CURRENT_REF.exists():
        return

    with BETAF_CURRENT_REF.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            if row.get("row_type") != "apdrone_battery_scenario_current_scale_summary":
                continue
            scenario = row.get("scenario", "")
            for current_label, column in [
                ("capacity_match_mean", "current_mean_a_at_capacity_match"),
                ("capacity_match_p95", "current_p95_a_at_capacity_match"),
                ("raw_per_amp_20_mean", "current_mean_a_if_raw_per_amp_20"),
                ("raw_per_amp_20_p95", "current_p95_a_if_raw_per_amp_20"),
            ]:
                total_current = csv_float(row.get(column))
                per_motor_current = total_current / 4.0
                for fit_scope, current_fit in current_fits.items():
                    inferred_thrust_n = invert_power_law(current_fit, per_motor_current)
                    rows.append(
                        {
                            "row_type": "apdrone_motor_pdf_log_current_inversion",
                            "source_scope": "apdrone_battery_log_current_vs_motor_pdf_fit",
                            "source_page": SOURCE_PAGE,
                            "doi": DOI,
                            "local_source_file": repo_path(PDF),
                            "fit_scope": fit_scope,
                            "scenario": scenario,
                            "current_observation": current_label,
                            "observed_current_total_a": total_current,
                            "observed_current_per_motor_a": per_motor_current,
                            "inferred_per_motor_thrust_n_from_current_fit": inferred_thrust_n,
                            "inferred_total_thrust_n_from_current_fit": inferred_thrust_n * 4.0,
                            "inferred_total_thrust_over_apdrone_weight": inferred_thrust_n * 4.0 / vehicle_weight_n,
                            "current_range_context": range_context(per_motor_current, current_fit["observed_min"], current_fit["observed_max"], "current"),
                            "note": "Inverts I=a*T^b from the motor PDF thrust table using APdrone battery-log current. Mean log currents are often below the bench-table current range, so range context matters.",
                        }
                    )


def add_electrical_rpm_estimate_rows(rows: list[dict[str, str | int | float]], project: dict[str, float]) -> None:
    motor_resistance_ohm = 0.0586
    kv_sources = [
        ("pdf_1800kv", 1800.0, "KV printed in the APdrone/YSIDO motor PDF."),
        ("betaflight_config_1960kv", 1960.0, "APdrone Betaflight CLI dump motor_kv setting."),
    ]
    grouped: dict[tuple[str, str], list[dict[str, str | int | float]]] = {}

    for point in computed_thrust_points():
        throttle = csv_float(point["throttle_percent"])
        if throttle < 99.5:
            continue
        voltage_v = csv_float(point["voltage_v"])
        current_a = csv_float(point["current_a"])
        thrust_n = csv_float(point["thrust_n"])
        voltage_drop_v = current_a * motor_resistance_ohm
        back_emf_v = voltage_v - voltage_drop_v
        for kv_source, kv_rpm_per_v, kv_note in kv_sources:
            loaded_rpm = back_emf_v * kv_rpm_per_v
            loaded_omega = loaded_rpm * RPM_TO_RAD_PER_SEC
            estimated_k = thrust_n / (loaded_omega * loaded_omega) if loaded_omega > 0.0 else float("nan")
            row = {
                "row_type": "apdrone_motor_pdf_loaded_rpm_k_estimate",
                "source_scope": "full_throttle_back_emf_estimate_from_motor_pdf",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "local_source_file": repo_path(PDF),
                "item": point["item"],
                "prop": point["prop"],
                "throttle_percent": throttle,
                "voltage_v": voltage_v,
                "current_a": current_a,
                "thrust_n": thrust_n,
                "motor_resistance_ohm": motor_resistance_ohm,
                "voltage_drop_i_times_r_v": voltage_drop_v,
                "estimated_back_emf_v": back_emf_v,
                "kv_source": kv_source,
                "kv_rpm_per_v": kv_rpm_per_v,
                "estimated_loaded_rpm": loaded_rpm,
                "estimated_loaded_omega_rad_s": loaded_omega,
                "estimated_thrust_coefficient_n_per_rad_s2": estimated_k,
                "project_apdrone_thrust_coefficient": project["thrust_coefficient"],
                "project_k_over_estimated_k": project["thrust_coefficient"] / estimated_k if estimated_k > 0.0 else float("nan"),
                "estimated_k_over_project_k": estimated_k / project["thrust_coefficient"] if project["thrust_coefficient"] > 0.0 else float("nan"),
                "note": f"{kv_note} RPM is estimated as KV*(V-I*R) at the visible 100% throttle points; this is not measured RPM and is sensitive to motor-resistance convention and ESC duty behavior.",
            }
            rows.append(row)
            grouped.setdefault((str(point["prop"]), kv_source), []).append(row)

    for (prop, kv_source), group_rows in grouped.items():
        estimates = [csv_float(row["estimated_thrust_coefficient_n_per_rad_s2"]) for row in group_rows]
        estimates = [value for value in estimates if math.isfinite(value)]
        if not estimates:
            continue
        mean_estimate = sum(estimates) / len(estimates)
        rows.append(
            {
                "row_type": "apdrone_motor_pdf_loaded_rpm_k_summary",
                "source_scope": "full_throttle_back_emf_estimate_from_motor_pdf",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "local_source_file": repo_path(PDF),
                "prop": prop,
                "kv_source": kv_source,
                "sample_count": len(estimates),
                "estimated_k_mean": mean_estimate,
                "estimated_k_min": min(estimates),
                "estimated_k_max": max(estimates),
                "project_apdrone_thrust_coefficient": project["thrust_coefficient"],
                "project_k_over_estimated_k_mean": project["thrust_coefficient"] / mean_estimate,
                "estimated_k_mean_over_project_k": mean_estimate / project["thrust_coefficient"],
                "note": "Summary of full-throttle back-EMF thrust-coefficient estimates by prop and KV convention.",
            }
        )

    for kv_source in {key[1] for key in grouped}:
        for scope_name, props, scope_note in [
            ("four_s_full_throttle_points", {"7056 3R", "6045R"}, "Full-throttle points at 16-17 V, closest to APdrone's 4S pack voltage but not to the exact Foxeer Donut 5145 prop."),
            ("five_inch_six_s_full_throttle_points", {"5043", "5045"}, "Full-throttle 5-inch prop points, closest diameter family to Foxeer Donut 5145 but tested at 6S voltage."),
        ]:
            scope_values: list[float] = []
            for (prop, source), group_rows in grouped.items():
                if source != kv_source or prop not in props:
                    continue
                scope_values.extend(
                    csv_float(row["estimated_thrust_coefficient_n_per_rad_s2"])
                    for row in group_rows
                    if math.isfinite(csv_float(row["estimated_thrust_coefficient_n_per_rad_s2"]))
                )
            if not scope_values:
                continue
            mean_estimate = sum(scope_values) / len(scope_values)
            rows.append(
                {
                    "row_type": "apdrone_motor_pdf_loaded_rpm_k_scope_summary",
                    "source_scope": "full_throttle_back_emf_estimate_from_motor_pdf",
                    "source_page": SOURCE_PAGE,
                    "doi": DOI,
                    "local_source_file": repo_path(PDF),
                    "summary_scope": scope_name,
                    "kv_source": kv_source,
                    "sample_count": len(scope_values),
                    "estimated_k_mean": mean_estimate,
                    "estimated_k_min": min(scope_values),
                    "estimated_k_max": max(scope_values),
                    "project_apdrone_thrust_coefficient": project["thrust_coefficient"],
                    "project_k_over_estimated_k_mean": project["thrust_coefficient"] / mean_estimate,
                    "estimated_k_mean_over_project_k": mean_estimate / project["thrust_coefficient"],
                    "note": scope_note,
                }
            )


def add_project_comparison_rows(rows: list[dict[str, str | int | float]], project: dict[str, float]) -> None:
    max_claim_n = 1488.0 / 1000.0 * G0
    hover_thrust_n = project["mass_kg"] * G0 / 4.0
    project_max_rpm = math.sqrt(project["max_rotor_thrust_n"] / project["thrust_coefficient"]) * RAD_PER_SEC_TO_RPM
    project_hover_rpm = math.sqrt(hover_thrust_n / project["thrust_coefficient"]) * RAD_PER_SEC_TO_RPM
    ideal_1800kv_4s_full_rpm = 1800.0 * 16.8
    ideal_1800kv_4s_nominal_rpm = 1800.0 * 14.8
    ideal_1960kv_4s_full_rpm = 1960.0 * 16.8
    ideal_1960kv_4s_nominal_rpm = 1960.0 * 14.8
    motor_max_continuous_current_a = 42.0
    project_max_pack_current_per_motor_a = project["max_battery_current_a"] / 4.0

    for metric, value, unit, note in [
        ("pdf_claim_max_thrust_n", max_claim_n, "N", "1488 g maximum thrust claim visible on pages 1 and 3."),
        ("project_apdrone_max_rotor_thrust_n", project["max_rotor_thrust_n"], "N", "Current apDrone() maxRotorThrust."),
        ("project_max_over_pdf_claim_max", project["max_rotor_thrust_n"] / max_claim_n, "ratio", "Current max thrust is below the PDF headline maximum-thrust claim."),
        ("project_hover_thrust_per_rotor_n", hover_thrust_n, "N", "Using apDrone mass and four rotors."),
        ("project_hover_rpm_from_k", project_hover_rpm, "rpm", "Derived from current thrustCoefficient."),
        ("project_max_rpm_from_k", project_max_rpm, "rpm", "Derived from current max thrust and thrustCoefficient."),
        ("project_max_rpm_over_1800kv_4s_full_charge_no_load", project_max_rpm / ideal_1800kv_4s_full_rpm, "ratio", "Compares current max-RPM implication with PDF motor KV at 16.8 V no-load."),
        ("project_max_rpm_over_1800kv_4s_nominal_no_load", project_max_rpm / ideal_1800kv_4s_nominal_rpm, "ratio", "Compares current max-RPM implication with PDF motor KV at 14.8 V no-load."),
        ("project_max_rpm_over_betaflight_1960kv_4s_full_charge_no_load", project_max_rpm / ideal_1960kv_4s_full_rpm, "ratio", "Compares current max-RPM implication with APdrone Betaflight motor_kv=1960 at 16.8 V no-load."),
        ("project_max_rpm_over_betaflight_1960kv_4s_nominal_no_load", project_max_rpm / ideal_1960kv_4s_nominal_rpm, "ratio", "Compares current max-RPM implication with APdrone Betaflight motor_kv=1960 at 14.8 V no-load."),
        ("project_max_pack_current_per_motor_a", project_max_pack_current_per_motor_a, "A", "apDrone maxBatteryCurrent divided by four motors."),
        ("project_current_per_motor_over_pdf_motor_continuous_current", project_max_pack_current_per_motor_a / motor_max_continuous_current_a, "ratio", "Compares apDrone per-motor pack-current share with the PDF's 42 A max continuous current."),
    ]:
        rows.append(
            {
                "row_type": "apdrone_motor_pdf_project_comparison",
                "source_scope": "current_apdrone_preset_vs_motor_pdf",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "local_source_file": repo_path(PDF),
                "metric": metric,
                "value": value,
                "unit": unit,
                "note": note,
            }
        )


def write_csv(path: Path, rows: list[dict[str, str | int | float]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames: list[str] = []
    for row in rows:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    project = parse_apdrone_project_values()
    rows: list[dict[str, str | int | float]] = []
    add_spec_rows(rows)
    add_thrust_rows(rows, project)
    add_project_comparison_rows(rows, project)
    add_fit_rows(rows, project)
    add_electrical_rpm_estimate_rows(rows, project)
    rows = [{key: finite_or_blank(value) for key, value in row.items()} for row in rows]
    write_csv(OUTPUT, rows)
    print(f"Wrote {repo_path(OUTPUT)}")


if __name__ == "__main__":
    main()
