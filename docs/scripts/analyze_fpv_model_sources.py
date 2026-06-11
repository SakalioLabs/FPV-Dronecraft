#!/usr/bin/env python3
"""
Fetch open FPV/multirotor data sources and compare them with DroneConfig presets.

Outputs:
  docs/fpv-sim-model-validation.md
  docs/data/fpv_model_validation_summary.csv
  docs/data/raw/* cached source files
"""

from __future__ import annotations

import csv
import datetime as dt
import math
import re
import statistics
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
DATA = DOCS / "data"
RAW = DATA / "raw"
DRONE_CONFIG = ROOT / "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneConfig.java"

RHO = 1.225
G = 9.80665
SPEED_OF_SOUND_25C = 346.1


UIUC_STATIC = [
    {
        "name": "DA4052 5x3.75 3-blade",
        "family": "da4052_5x3.75_3b",
        "diameter_m": 0.127,
        "url": "https://m-selig.ae.illinois.edu/props/volume-2/data/da4052_5x3.75_3b_static_1204ga.txt",
    },
    {
        "name": "NR640 5in 15deg 3-blade",
        "family": "nr640_5_15deg_3b",
        "diameter_m": 0.127,
        "url": "https://m-selig.ae.illinois.edu/props/volume-2/data/nr640_5_15deg_3b_static_0696md.txt",
    },
    {
        "name": "MicroInvent 5x4 3-blade",
        "family": "mit_5x4",
        "diameter_m": 0.127,
        "url": "https://m-selig.ae.illinois.edu/props/volume-2/data/mit_5x4_static_0362rd.txt",
    },
    {
        "name": "APC SF 10x4.7",
        "family": "apcsf_10x4.7",
        "diameter_m": 0.254,
        "url": "https://m-selig.ae.illinois.edu/props/volume-1/data/apcsf_10x4.7_static_kt0835.txt",
    },
]

UIUC_FORWARD = [
    {
        "name": "DA4052 5x3.75 3-blade 7044 rpm",
        "family": "da4052_5x3.75_3b",
        "url": "https://m-selig.ae.illinois.edu/props/volume-2/data/da4052_5x3.75_3b_1209ga_7044.txt",
    },
    {
        "name": "NR640 5in 15deg 3-blade 9547 rpm",
        "family": "nr640_5_15deg_3b",
        "url": "https://m-selig.ae.illinois.edu/props/volume-2/data/nr640_5_15deg_3b_0702rd_9547.txt",
    },
    {
        "name": "MicroInvent 5x4 3-blade 6057 rpm",
        "family": "mit_5x4",
        "url": "https://m-selig.ae.illinois.edu/props/volume-2/data/mit_5x4_0366rd_6057.txt",
    },
    {
        "name": "APC SF 10x4.7 6513 rpm",
        "family": "apcsf_10x4.7",
        "url": "https://m-selig.ae.illinois.edu/props/volume-1/data/apcsf_10x4.7_rd0842_6513.txt",
    },
]

MQTB_URL = "https://www.miniquadtestbench.com/assets/components/motordata/motorinfo.php?uid=259"
ZJU_GROUND_EFFECT_URL = "https://raw.githubusercontent.com/ZJU-FAST-Lab/Ground-effect-controller/master/README.md"
ZJU_GROUND_EFFECT_PAPER_URL = "https://arxiv.org/abs/2506.19424"
GYMPYB_BASEAVIARY_URL = "https://raw.githubusercontent.com/utiasDSL/gym-pybullet-drones/main/gym_pybullet_drones/envs/BaseAviary.py"
COAXIAL_BENCHMARK_URL = "https://raw.githubusercontent.com/newdexterity/Coaxial-Benchmarking-Platform/master/README.md"
COAXIAL_RESULTS_URL = "https://hackaday.io/project/181977-omnirotor-an-agile-coaxial-all-terrain-vehicle/log/199225-some-results-of-coaxial-rotor-experiments-on-the-benchmarking-platform"
CAMBRIDGE_VRS_URL = "https://www.cambridge.org/core/journals/flow/article/effects-of-rotor-separation-on-the-axial-descent-performance-of-dualrotor-configurations/BE7FE0D2E732E777CBD43F8E65CA0692"
LIPO_EIS_DATASET_URL = "https://pmc.ncbi.nlm.nih.gov/articles/PMC10518458/"
MENDELEY_LIPO_DATASET_URL = "https://data.mendeley.com/datasets/stcppt2r68/1"
NASA_BATTERY_DATASET_URL = "https://data.nasa.gov/dataset/li-ion-battery-aging-datasets"
NASA_BATTERY_ZIP_URL = "https://phm-datasets.s3.amazonaws.com/NASA/5.+Battery+Data+Set.zip"
CHL_LIPO_IR_URL = "https://chinahobbyline.com/blogs/news/lipo-internal-resistance-explained"

OPEN_SOURCE_PARAMS = [
    {
        "name": "RotorS Firefly",
        "vehicle": "firefly",
        "url": "https://raw.githubusercontent.com/ethz-asl/rotors_simulator/master/rotors_description/urdf/firefly.xacro",
    },
    {
        "name": "RotorS Hummingbird",
        "vehicle": "hummingbird",
        "url": "https://raw.githubusercontent.com/ethz-asl/rotors_simulator/master/rotors_description/urdf/hummingbird.xacro",
    },
    {
        "name": "PX4 Gazebo Iris",
        "vehicle": "iris",
        "url": "https://raw.githubusercontent.com/PX4/PX4-SITL_gazebo-classic/main/models/iris/iris.sdf.jinja",
    },
    {
        "name": "gym-pybullet-drones Crazyflie",
        "vehicle": "cf2x",
        "url": "https://raw.githubusercontent.com/utiasDSL/gym-pybullet-drones/main/gym_pybullet_drones/assets/cf2x.urdf",
    },
    {
        "name": "Flightmare quadrotor",
        "vehicle": "flightmare",
        "url": "https://raw.githubusercontent.com/uzh-rpg/flightmare/master/flightlib/configs/quadrotor_env.yaml",
    },
]

ZJU_GROUND_EFFECT_PARAMS = [
    {
        "name": "ZJU single-rotor platform kT",
        "k_t_n_per_rpm2": 4.0083e-8,
        "source_context": "single-rotor platform",
    },
    {
        "name": "ZJU quadrotor platform kT",
        "k_t_n_per_rpm2": 3.7840e-8,
        "source_context": "quadrotor platform",
    },
    {
        "name": "ZJU real-flight hover kT",
        "k_t_n_per_rpm2": 4.2958e-8,
        "source_context": "real flight by hovering",
    },
]

ZJU_GROUND_EFFECT_COEFFICIENTS = {
    "k_i_nm_per_rpm2": 6.3859e-10,
    "rotor_inertia_kg_m2_text": 1.0556e-4,
    "g1": 1.804e-2,
    "g2": 7.339e-3,
    "g3": -3.365e-1,
    "drag_x_n_per_m_s": 0.3970,
    "drag_y_n_per_m_s": 0.3300,
    "mass_kg": 1.696,
    "inertia_x_kg_m2": 0.00745220,
    "inertia_y_kg_m2": 0.00792752,
    "inertia_z_kg_m2": 0.01249522,
}


def cache_name(url: str) -> str:
    tail = re.sub(r"[^A-Za-z0-9._-]+", "_", url.split("//", 1)[-1])
    return tail[-150:]


def fetch_text(url: str) -> str:
    RAW.mkdir(parents=True, exist_ok=True)
    path = RAW / cache_name(url)
    if not path.exists():
        req = urllib.request.Request(url, headers={"User-Agent": "fpv-dronecraft-validation/1.0"})
        with urllib.request.urlopen(req, timeout=45) as response:
            path.write_bytes(response.read())
    return path.read_text(encoding="utf-8", errors="replace")


def numeric_rows(text: str) -> list[list[float]]:
    rows: list[list[float]] = []
    for line in text.splitlines():
        parts = line.strip().split()
        if len(parts) < 3:
            continue
        try:
            rows.append([float(part) for part in parts])
        except ValueError:
            continue
    return rows


def k_from_ct(ct: float, diameter_m: float, rho: float = RHO) -> float:
    return ct * rho * diameter_m**4 / (4.0 * math.pi**2)


def qt_from_ct_cp(ct: float, cp: float, diameter_m: float) -> float:
    return (cp / ct) * diameter_m / (2.0 * math.pi) if ct else float("nan")


def thrust_n_from_grams(grams: float) -> float:
    return grams / 1000.0 * G


def k_from_thrust_rpm(thrust_n: float, rpm: float) -> float:
    omega = rpm * 2.0 * math.pi / 60.0
    return thrust_n / (omega * omega)


def k_rpm2_to_k_rad2(k_n_per_rpm2: float) -> float:
    return k_n_per_rpm2 * (60.0 / (2.0 * math.pi)) ** 2


def rpm_from_k_thrust(k: float, thrust_n: float) -> float:
    if k <= 0.0 or thrust_n <= 0.0:
        return float("nan")
    return math.sqrt(thrust_n / k) * 60.0 / (2.0 * math.pi)


def linear_fit(xs: list[float], ys: list[float]) -> tuple[float, float, float]:
    xbar = statistics.fmean(xs)
    ybar = statistics.fmean(ys)
    sxx = sum((x - xbar) ** 2 for x in xs)
    sxy = sum((x - xbar) * (y - ybar) for x, y in zip(xs, ys))
    slope = sxy / sxx if sxx else 0.0
    intercept = ybar - slope * xbar
    ss_tot = sum((y - ybar) ** 2 for y in ys)
    ss_res = sum((y - (intercept + slope * x)) ** 2 for x, y in zip(xs, ys))
    r2 = 1.0 - ss_res / ss_tot if ss_tot else 1.0
    return intercept, slope, r2


def nearest_row(rows: list[list[float]], target: float, column: int = 0) -> list[float]:
    return min(rows, key=lambda row: abs(row[column] - target))


def summarize_uiuc_static() -> list[dict[str, float | str]]:
    summaries = []
    for source in UIUC_STATIC:
        rows = numeric_rows(fetch_text(source["url"]))
        rpm = [row[0] for row in rows]
        ct = [row[1] for row in rows]
        cp = [row[2] for row in rows]
        avg_ct = statistics.fmean(ct)
        avg_cp = statistics.fmean(cp)
        diameter = float(source["diameter_m"])
        summaries.append(
            {
                "name": source["name"],
                "family": source["family"],
                "url": source["url"],
                "diameter_m": diameter,
                "rows": len(rows),
                "rpm_min": min(rpm),
                "rpm_max": max(rpm),
                "ct_avg": avg_ct,
                "cp_avg": avg_cp,
                "k_n_per_rad2": k_from_ct(avg_ct, diameter),
                "qt_m": qt_from_ct_cp(avg_ct, avg_cp, diameter),
            }
        )
    return summaries


def summarize_uiuc_forward(static_by_family: dict[str, dict[str, float | str]]) -> list[dict[str, float | str]]:
    summaries = []
    for source in UIUC_FORWARD:
        rows = [row for row in numeric_rows(fetch_text(source["url"])) if row[1] > 0.0]
        xs = [row[0] for row in rows]
        cts = [row[1] for row in rows]
        cps = [row[2] for row in rows]
        intercept, slope, r2 = linear_fit(xs, cts)
        zero_ct_j = -intercept / slope if slope < 0.0 else float("nan")
        near_045 = nearest_row(rows, 0.45)
        static_ct = float(static_by_family[source["family"]]["ct_avg"])
        summaries.append(
            {
                "name": source["name"],
                "family": source["family"],
                "url": source["url"],
                "j_min": min(xs),
                "j_max": max(xs),
                "ct_min": min(cts),
                "ct_max": max(cts),
                "cp_min": min(cps),
                "cp_max": max(cps),
                "linear_ct_intercept": intercept,
                "linear_ct_slope": slope,
                "linear_r2": r2,
                "zero_ct_j": zero_ct_j,
                "nearest_j_045": near_045[0],
                "ct_at_near_045": near_045[1],
                "ct_ratio_near_045_vs_static": near_045[1] / static_ct,
            }
        )
    return summaries


def parse_mqtb() -> tuple[list[dict[str, float | str]], list[dict[str, float | str]]]:
    html = fetch_text(MQTB_URL)
    row_re = re.compile(
        r"<td>(?P<prop>[^<]+)</td><td class=\"thrust\">(?P<thrust_max>[0-9.]+)</td>\s*"
        r"<td class=\"thrust\">(?P<thrust_avg>[0-9.]+)</td>\s*"
        r"<td class=\"amps\">(?P<amps_max>[0-9.]+)</td>\s*"
        r"<td class=\"amps\">(?P<amps_avg>[0-9.]+)</td>\s*"
        r"<td class=\"volts\">(?P<volts_min>[0-9.]+)</td>\s*"
        r"<td class=\"volts\">(?P<volts_max>[0-9.]+)</td>\s*"
        r"<td class=\"rpms\">(?P<rpm_max>[0-9.]+)</td>\s*"
        r"<td class=\"rpms\">(?P<rpm_min>[0-9.]+)</td>\s*"
        r"<td class=\"rpms\">(?P<rpm_avg>[0-9.]+)</td>",
        re.S,
    )
    summary = []
    for match in row_re.finditer(html):
        prop = re.sub(r"\s+", " ", match.group("prop")).strip()
        thrust_avg = float(match.group("thrust_avg"))
        rpm_avg = float(match.group("rpm_avg"))
        thrust_n = thrust_n_from_grams(thrust_avg)
        summary.append(
            {
                "prop": prop,
                "thrust_max_g": float(match.group("thrust_max")),
                "thrust_avg_g": thrust_avg,
                "amps_avg": float(match.group("amps_avg")),
                "volts_min_avg": float(match.group("volts_min")),
                "rpm_avg": rpm_avg,
                "k_n_per_rad2": k_from_thrust_rpm(thrust_n, rpm_avg),
            }
        )

    command_rows = []
    command_re = re.compile(
        r"<td>(?P<prop>[^<]+)</td><td class=\"thrust\">(?P<thrust>[0-9.]+)</td>"
        r"<td class=\"amps\">(?P<amps>[0-9.]+)</td>"
        r"<td class=\"volts\">(?P<volts>[0-9.]+)</td>"
        r"<td class=\"rpms\">(?P<rpm>[0-9.]+)</td>",
        re.S,
    )
    table_re = re.compile(
        r"<td class=\"title\">(?P<label>75%|50%|25%|IDLE)</td>(?P<table>.*?</table>)",
        re.S,
    )
    throttle_fraction = {"IDLE": 0.0, "25%": 0.25, "50%": 0.50, "75%": 0.75}
    for table in table_re.finditer(html):
        label = table.group("label")
        for match in command_re.finditer(table.group("table")):
            prop = re.sub(r"\s+", " ", match.group("prop")).strip()
            if prop == "HQ v1s 5x4x3":
                thrust_g = float(match.group("thrust"))
                rpm = float(match.group("rpm"))
                command_rows.append(
                    {
                        "command_label": label,
                        "command_fraction": throttle_fraction[label],
                        "prop": prop,
                        "thrust_g": thrust_g,
                        "thrust_n": thrust_n_from_grams(thrust_g),
                        "amps": float(match.group("amps")),
                        "volts": float(match.group("volts")),
                        "rpm": rpm,
                        "k_n_per_rad2": k_from_thrust_rpm(thrust_n_from_grams(thrust_g), rpm),
                    }
                )
    return summary, command_rows


def safe_eval_number(expr: str) -> float:
    expr = expr.strip()
    env = {"Math": math, "math": math}
    return float(eval(expr, {"__builtins__": {}}, env))


def extract_balanced_call(text: str, marker: str) -> str:
    start = text.find(marker)
    if start < 0:
        return ""
    index = text.find("(", start)
    if index < 0:
        return ""
    depth = 0
    for pos in range(index, len(text)):
        char = text[pos]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return text[index + 1 : pos]
    return ""


def split_top_level_args(text: str) -> list[str]:
    args: list[str] = []
    start = 0
    depth = 0
    for index, char in enumerate(text):
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        elif char == "," and depth == 0:
            args.append(text[start:index].strip())
            start = index + 1
    tail = text[start:].strip()
    if tail:
        args.append(tail)
    return args


def constructor_args(body: str) -> list[str]:
    return split_top_level_args(extract_balanced_call(body, "return new DroneConfig"))


def arg_number(args: list[str], index: int) -> float:
    if index < 0 or index >= len(args):
        return float("nan")
    try:
        return safe_eval_number(args[index])
    except Exception:
        return float("nan")


def parse_drone_presets() -> list[dict[str, float | str]]:
    src = DRONE_CONFIG.read_text(encoding="utf-8")
    presets = []
    for name in ("racingQuad", "cinewhoop", "heavyLift", "hexLift", "octoLift"):
        match = re.search(
            rf"public static DroneConfig {name}\(\) \{{(?P<body>.*?)(?=\n\tpublic static|\n\tprivate static)",
            src,
            re.S,
        )
        if not match:
            continue
        body = match.group("body")
        var = {key: safe_eval_number(value) for key, value in re.findall(r"double\s+(\w+)\s*=\s*([^;]+);", body)}
        args = constructor_args(body)
        mass = arg_number(args, 0)
        rotor_count = body.count("new RotorSpec(") + body.count("rotorAtDegrees(")
        presets.append(
            {
                "preset": name,
                "mass_kg": mass,
                "rotor_count": rotor_count,
                "max_thrust_n": var.get("maxRotorThrust", float("nan")),
                "k_n_per_rad2": var.get("thrustCoefficient", float("nan")),
                "yaw_qt_m": var.get("yawTorquePerThrust", float("nan")),
                "radius_m": var.get("rotorRadius", float("nan")),
                "rotor_inertia_kg_m2": var.get("rotorInertia", float("nan")),
                "inflow_tau_s": var.get("inflowTimeConstant", float("nan")),
                "ground_effect_height_m": arg_number(args, 9),
                "ground_effect_max_boost": arg_number(args, 10),
                "propwash_start_m_s": arg_number(args, 11),
                "propwash_full_m_s": arg_number(args, 12),
                "propwash_torque_nm": arg_number(args, 13),
                "motor_tau_s": arg_number(args, 15),
                "battery_nominal_v": arg_number(args, 33),
                "battery_empty_v": arg_number(args, 34),
                "battery_resistance_ohm": arg_number(args, 35),
                "battery_capacity_ah": arg_number(args, 36),
                "max_battery_current_a": arg_number(args, 37),
            }
        )

    octo = next((preset for preset in presets if preset["preset"] == "octoLift"), None)
    if octo is not None:
        coax = dict(octo)
        coax["preset"] = "coaxialX8"
        coax["mass_kg"] = 7.2
        coax["rotor_count"] = 8
        presets.append(coax)
    return presets


def prop_value(text: str, name: str) -> float:
    match = re.search(rf'name="{re.escape(name)}"\s+value="([^"]+)"', text)
    return safe_eval_number(match.group(1)) if match else float("nan")


def first_xml_value(text: str, tag: str) -> float:
    match = re.search(rf"<{re.escape(tag)}>([^<]+)</{re.escape(tag)}>", text)
    return float(match.group(1)) if match else float("nan")


def xml_attr_value(text: str, tag: str, attr: str) -> float:
    match = re.search(rf"<{re.escape(tag)}\b[^>]*\b{re.escape(attr)}=\"([^\"]+)\"", text)
    return float(match.group(1)) if match else float("nan")


def yaml_value(text: str, name: str) -> float:
    match = re.search(rf"^\s*{re.escape(name)}:\s*([^\s#]+)", text, re.M)
    return float(match.group(1)) if match else float("nan")


def parse_open_source_params() -> list[dict[str, float | str]]:
    rows = []
    for source in OPEN_SOURCE_PARAMS:
        text = fetch_text(source["url"])
        vehicle = source["vehicle"]
        if vehicle in ("firefly", "hummingbird"):
            mass = prop_value(text, "mass")
            radius = prop_value(text, "radius_rotor")
            k = prop_value(text, "motor_constant")
            qt = prop_value(text, "moment_constant")
            rows.append(
                {
                    "name": source["name"],
                    "url": source["url"],
                    "mass_kg": mass,
                    "radius_m": radius,
                    "k_n_per_rad2": k,
                    "qt_m": qt,
                    "max_omega_rad_s": prop_value(text, "max_rot_velocity"),
                    "tau_up_s": prop_value(text, "time_constant_up"),
                    "tau_down_s": prop_value(text, "time_constant_down"),
                    "normalized_ct": k * 4.0 * math.pi**2 / (RHO * (2.0 * radius) ** 4),
                    "rotor_drag_coefficient": prop_value(text, "rotor_drag_coefficient"),
                }
            )
        elif vehicle == "iris":
            radius_match = re.search(r"<cylinder>\s*<length>[^<]+</length>\s*<radius>([^<]+)</radius>", text, re.S)
            radius = float(radius_match.group(1)) if radius_match else float("nan")
            k = first_xml_value(text, "motorConstant")
            qt = first_xml_value(text, "momentConstant")
            rows.append(
                {
                    "name": source["name"],
                    "url": source["url"],
                    "mass_kg": first_xml_value(text, "mass"),
                    "radius_m": radius,
                    "k_n_per_rad2": k,
                    "qt_m": qt,
                    "max_omega_rad_s": first_xml_value(text, "maxRotVelocity"),
                    "tau_up_s": first_xml_value(text, "timeConstantUp"),
                    "tau_down_s": first_xml_value(text, "timeConstantDown"),
                    "normalized_ct": k * 4.0 * math.pi**2 / (RHO * (2.0 * radius) ** 4),
                    "rotor_drag_coefficient": first_xml_value(text, "rotorDragCoefficient"),
                }
            )
        elif vehicle == "cf2x":
            props = {}
            prop_match = re.search(r"<properties\s+([^>]+)>", text)
            if prop_match:
                props = {key: float(value) for key, value in re.findall(r'([A-Za-z0-9_]+)="([^"]+)"', prop_match.group(1))}
            rows.append(
                {
                    "name": source["name"],
                    "url": source["url"],
                    "mass_kg": xml_attr_value(text, "mass", "value"),
                    "radius_m": props.get("prop_radius", float("nan")),
                    "k_n_per_rad2": props.get("kf", float("nan")),
                    "qt_m": props.get("km", float("nan")) / props.get("kf", float("nan")),
                    "max_omega_rad_s": float("nan"),
                    "tau_up_s": float("nan"),
                    "tau_down_s": float("nan"),
                    "normalized_ct": float("nan"),
                    "rotor_drag_coefficient": props.get("drag_coeff_xy", float("nan")),
                    "ground_effect_coeff": props.get("gnd_eff_coeff", float("nan")),
                    "downwash_coeff_1": props.get("dw_coeff_1", float("nan")),
                    "downwash_coeff_2": props.get("dw_coeff_2", float("nan")),
                    "downwash_coeff_3": props.get("dw_coeff_3", float("nan")),
                }
            )
        elif vehicle == "flightmare":
            thrust_map_match = re.search(r"thrust_map:\s*\[([^\]]+)\]", text)
            thrust_map = [float(part.strip()) for part in thrust_map_match.group(1).split(",")] if thrust_map_match else [float("nan")]
            rows.append(
                {
                    "name": source["name"],
                    "url": source["url"],
                    "mass_kg": yaml_value(text, "mass"),
                    "radius_m": float("nan"),
                    "k_n_per_rad2": thrust_map[0],
                    "qt_m": yaml_value(text, "kappa"),
                    "max_omega_rad_s": yaml_value(text, "motor_omega_max"),
                    "tau_up_s": yaml_value(text, "motor_tau"),
                    "tau_down_s": yaml_value(text, "motor_tau"),
                    "normalized_ct": float("nan"),
                    "rotor_drag_coefficient": yaml_value(text, "kappa"),
                    "arm_m": yaml_value(text, "arm_l"),
                }
            )
    return rows


def summarize_presets(presets: list[dict[str, float | str]]) -> list[dict[str, float | str]]:
    rows = []
    for preset in presets:
        max_thrust = float(preset["max_thrust_n"])
        k = float(preset["k_n_per_rad2"])
        radius = float(preset["radius_m"])
        rotors = int(preset["rotor_count"])
        mass = float(preset["mass_kg"])
        rpm = rpm_from_k_thrust(k, max_thrust)
        omega = rpm * 2.0 * math.pi / 60.0
        total_max = rotors * max_thrust
        weight = mass * G
        rows.append(
            {
                **preset,
                "max_rpm_from_k": rpm,
                "tip_mach_at_max": omega * radius / SPEED_OF_SOUND_25C,
                "thrust_to_weight": total_max / weight,
                "hover_throttle_linear": weight / total_max,
                "hover_disk_loading_n_m2": weight / (rotors * math.pi * radius * radius),
                "hover_thrust_per_rotor_n": weight / rotors,
                "hover_induced_velocity_m_s": math.sqrt((weight / rotors) / (2.0 * RHO * math.pi * radius * radius)),
            }
        )
    return rows


def summarize_zju_ground_effect() -> list[dict[str, float | str]]:
    fetch_text(ZJU_GROUND_EFFECT_URL)
    rows = []
    for item in ZJU_GROUND_EFFECT_PARAMS:
        k_rpm = float(item["k_t_n_per_rpm2"])
        k_rad = k_rpm2_to_k_rad2(k_rpm)
        k_i_rpm = ZJU_GROUND_EFFECT_COEFFICIENTS["k_i_nm_per_rpm2"]
        rows.append(
            {
                "name": item["name"],
                "source_context": item["source_context"],
                "k_t_n_per_rpm2": k_rpm,
                "k_n_per_rad2": k_rad,
                "qt_m": k_i_rpm / k_rpm,
                "url": ZJU_GROUND_EFFECT_URL,
            }
        )
    return rows


def ground_multiplier_current(preset: dict[str, float | str], h_over_r: float) -> float:
    height = float(preset.get("ground_effect_height_m", float("nan")))
    boost = float(preset.get("ground_effect_max_boost", float("nan")))
    radius = float(preset["radius_m"])
    h = h_over_r * radius
    if not math.isfinite(height) or not math.isfinite(boost) or height <= 1.0e-6 or h >= height:
        return 1.0
    proximity = 1.0 - h / height
    return 1.0 + boost * proximity * proximity


def ground_multiplier_gympyb(h_over_r: float, coeff: float = 11.36859) -> float:
    if h_over_r <= 0.0:
        return float("nan")
    return 1.0 + coeff * (1.0 / (4.0 * h_over_r)) ** 2


def summarize_ground_effect(presets: list[dict[str, float | str]]) -> list[dict[str, float | str]]:
    rows = []
    for preset in presets:
        for h_over_r in (1.0, 2.0, 4.0):
            rows.append(
                {
                    "preset": preset["preset"],
                    "h_over_r": h_over_r,
                    "current_multiplier": ground_multiplier_current(preset, h_over_r),
                    "gympyb_cf2_multiplier": ground_multiplier_gympyb(h_over_r),
                    "source": GYMPYB_BASEAVIARY_URL,
                }
            )
    return rows


def summarize_vrs(presets: list[dict[str, float | str]]) -> list[dict[str, float | str]]:
    rows = []
    for preset in presets:
        vi = float(preset["hover_induced_velocity_m_s"])
        rows.append(
            {
                "preset": preset["preset"],
                "hover_induced_velocity_m_s": vi,
                "current_vrs_entry_m_s": 0.45 * vi,
                "current_vrs_full_entry_m_s": 0.95 * vi,
                "paper_peak_loss_low_m_s": 1.20 * vi,
                "paper_peak_loss_high_m_s": 1.30 * vi,
                "current_vrs_exit_start_m_s": 1.55 * vi,
                "current_vrs_exit_end_m_s": 2.25 * vi,
                "propwash_start_ratio_vi": float(preset.get("propwash_start_m_s", float("nan"))) / vi,
                "propwash_full_ratio_vi": float(preset.get("propwash_full_m_s", float("nan"))) / vi,
                "source": CAMBRIDGE_VRS_URL,
            }
        )
    return rows


def estimate_lipo_cell_count(nominal_v: float, empty_v: float) -> int:
    best_cells = 1
    best_score = float("inf")
    for cells in range(1, 13):
        nominal_per_cell = nominal_v / cells
        empty_per_cell = empty_v / cells if math.isfinite(empty_v) else 3.2
        nominal_score = min(abs(nominal_per_cell - 3.7) / 3.7, abs(nominal_per_cell - 4.2) / 4.2)
        empty_score = min(abs(empty_per_cell - 3.0) / 3.0, abs(empty_per_cell - 3.3) / 3.3)
        score = nominal_score * 0.70 + empty_score * 0.30
        if score < best_score:
            best_score = score
            best_cells = cells
    return best_cells


def summarize_battery_ir(presets: list[dict[str, float | str]]) -> list[dict[str, float | str]]:
    rows = []
    for preset in presets:
        nominal = float(preset.get("battery_nominal_v", float("nan")))
        empty = float(preset.get("battery_empty_v", float("nan")))
        resistance = float(preset.get("battery_resistance_ohm", float("nan")))
        capacity = float(preset.get("battery_capacity_ah", float("nan")))
        current_limit = float(preset.get("max_battery_current_a", float("nan")))
        cells = estimate_lipo_cell_count(nominal, empty) if math.isfinite(nominal) else 0
        rows.append(
            {
                "preset": preset["preset"],
                "estimated_cells": cells,
                "pack_resistance_ohm": resistance,
                "per_cell_resistance_mohm": resistance / cells * 1000.0 if cells else float("nan"),
                "current_limit_c": current_limit / capacity if capacity > 0.0 else float("nan"),
                "current_limit_pack_sag_v": current_limit * resistance,
                "current_limit_cell_sag_v": current_limit * resistance / cells if cells else float("nan"),
                "source": CHL_LIPO_IR_URL,
            }
        )
    return rows


def summarize_coaxial_spacing(presets: list[dict[str, float | str]]) -> list[dict[str, float | str]]:
    rows = []
    coax = next((preset for preset in presets if preset["preset"] == "coaxialX8"), None)
    if coax is not None:
        radius = float(coax["radius_m"])
        vertical_offset = radius * 0.72
        separation = vertical_offset * 2.0
        rows.append(
            {
                "preset": "coaxialX8",
                "radius_m": radius,
                "upper_lower_separation_m": separation,
                "separation_over_radius": separation / radius,
                "separation_over_diameter": separation / (2.0 * radius),
                "source": COAXIAL_BENCHMARK_URL,
            }
        )
    return rows


def summarize_motor_response(
    presets: list[dict[str, float | str]],
    open_models: list[dict[str, float | str]],
) -> list[dict[str, float | str]]:
    tau_up_refs = [float(row["tau_up_s"]) for row in open_models if math.isfinite(float(row.get("tau_up_s", float("nan")))) and float(row["tau_up_s"]) > 0.001]
    tau_down_refs = [float(row["tau_down_s"]) for row in open_models if math.isfinite(float(row.get("tau_down_s", float("nan")))) and float(row["tau_down_s"]) > 0.001]
    ref_up = statistics.fmean(tau_up_refs) if tau_up_refs else float("nan")
    ref_down = statistics.fmean(tau_down_refs) if tau_down_refs else float("nan")
    rows = []
    for preset in presets:
        motor_tau = float(preset.get("motor_tau_s", float("nan")))
        inflow_tau = float(preset.get("inflow_tau_s", float("nan")))
        rows.append(
            {
                "preset": preset["preset"],
                "motor_tau_s": motor_tau,
                "rotor_inflow_tau_s": inflow_tau,
                "open_ref_tau_up_s": ref_up,
                "open_ref_tau_down_s": ref_down,
                "motor_tau_vs_ref_up": motor_tau / ref_up if ref_up > 0.0 else float("nan"),
                "motor_tau_vs_ref_down": motor_tau / ref_down if ref_down > 0.0 else float("nan"),
                "inflow_tau_vs_ref_up": inflow_tau / ref_up if ref_up > 0.0 else float("nan"),
                "source": "RotorS/PX4 timeConstantUp/Down",
            }
        )
    return rows


def power_law_fit(points: list[tuple[float, float]]) -> tuple[float, float]:
    xs = [math.log(x) for x, _ in points if x > 0.0]
    ys = [math.log(y) for x, y in points if x > 0.0 and y > 0.0]
    intercept, slope, _ = linear_fit(xs, ys)
    return math.exp(intercept), slope


def pct_delta(a: float, b: float) -> float:
    return (a / b - 1.0) * 100.0 if b else float("nan")


def fmt(value: float | str, digits: int = 3) -> str:
    if isinstance(value, str):
        return value
    if not math.isfinite(value):
        return "n/a"
    if abs(value) >= 1000.0:
        return f"{value:.0f}"
    if abs(value) < 0.001 and value != 0.0:
        return f"{value:.3e}"
    return f"{value:.{digits}f}"


def write_summary_csv(
    static: list[dict[str, float | str]],
    forward: list[dict[str, float | str]],
    mqtb: list[dict[str, float | str]],
    open_models: list[dict[str, float | str]],
    presets: list[dict[str, float | str]],
    comparisons: list[dict[str, float | str]],
    zju_ground: list[dict[str, float | str]],
    ground_rows: list[dict[str, float | str]],
    vrs_rows: list[dict[str, float | str]],
    battery_ir_rows: list[dict[str, float | str]],
    coaxial_rows: list[dict[str, float | str]],
    motor_response_rows: list[dict[str, float | str]],
) -> None:
    path = DATA / "fpv_model_validation_summary.csv"
    DATA.mkdir(parents=True, exist_ok=True)
    rows: list[dict[str, str | float]] = []
    for item in static:
        rows.append({"category": "uiuc_static", "name": item["name"], "metric": "k_n_per_rad2", "value": item["k_n_per_rad2"], "unit": "N/(rad/s)^2", "source": item["url"]})
        rows.append({"category": "uiuc_static", "name": item["name"], "metric": "qt_m", "value": item["qt_m"], "unit": "m", "source": item["url"]})
    for item in forward:
        rows.append({"category": "uiuc_forward", "name": item["name"], "metric": "zero_ct_j_linear_fit", "value": item["zero_ct_j"], "unit": "advance ratio", "source": item["url"]})
        rows.append({"category": "uiuc_forward", "name": item["name"], "metric": "ct_ratio_near_j_0.45", "value": item["ct_ratio_near_045_vs_static"], "unit": "ratio", "source": item["url"]})
    for item in mqtb:
        rows.append({"category": "mqtb_100pct", "name": item["prop"], "metric": "k_n_per_rad2", "value": item["k_n_per_rad2"], "unit": "N/(rad/s)^2", "source": MQTB_URL})
        rows.append({"category": "mqtb_100pct", "name": item["prop"], "metric": "avg_max_current", "value": item["amps_avg"], "unit": "A", "source": MQTB_URL})
    for item in open_models:
        for metric, unit in (
            ("mass_kg", "kg"),
            ("radius_m", "m"),
            ("k_n_per_rad2", "N/(rad/s)^2"),
            ("qt_m", "m"),
            ("normalized_ct", "CT"),
            ("max_omega_rad_s", "rad/s"),
        ):
            if metric in item:
                rows.append({"category": "open_source_model", "name": item["name"], "metric": metric, "value": item[metric], "unit": unit, "source": item["url"]})
    for item in presets:
        for metric in ("max_rpm_from_k", "tip_mach_at_max", "thrust_to_weight", "hover_throttle_linear", "hover_disk_loading_n_m2", "hover_induced_velocity_m_s"):
            rows.append({"category": "current_preset", "name": item["preset"], "metric": metric, "value": item[metric], "unit": "", "source": str(DRONE_CONFIG)})
    for item in comparisons:
        rows.append({"category": "comparison", "name": item["name"], "metric": item["metric"], "value": item["value"], "unit": item["unit"], "source": item["source"]})
    for item in zju_ground:
        rows.append({"category": "zju_ground_effect", "name": item["name"], "metric": "k_n_per_rad2", "value": item["k_n_per_rad2"], "unit": "N/(rad/s)^2", "source": item["url"]})
        rows.append({"category": "zju_ground_effect", "name": item["name"], "metric": "qt_m", "value": item["qt_m"], "unit": "m", "source": item["url"]})
    for item in ground_rows:
        rows.append({"category": "ground_effect_shape", "name": item["preset"], "metric": f"current_multiplier_at_{item['h_over_r']}_R", "value": item["current_multiplier"], "unit": "x", "source": str(DRONE_CONFIG)})
        rows.append({"category": "ground_effect_shape", "name": item["preset"], "metric": f"gympyb_cf2_multiplier_at_{item['h_over_r']}_R", "value": item["gympyb_cf2_multiplier"], "unit": "x", "source": item["source"]})
    for item in vrs_rows:
        for metric, unit in (
            ("hover_induced_velocity_m_s", "m/s"),
            ("current_vrs_entry_m_s", "m/s"),
            ("current_vrs_full_entry_m_s", "m/s"),
            ("paper_peak_loss_low_m_s", "m/s"),
            ("paper_peak_loss_high_m_s", "m/s"),
            ("propwash_start_ratio_vi", "vi"),
            ("propwash_full_ratio_vi", "vi"),
        ):
            rows.append({"category": "vrs_reference", "name": item["preset"], "metric": metric, "value": item[metric], "unit": unit, "source": item["source"]})
    for item in battery_ir_rows:
        for metric, unit in (
            ("estimated_cells", "S"),
            ("per_cell_resistance_mohm", "mohm/cell"),
            ("current_limit_c", "C"),
            ("current_limit_pack_sag_v", "V"),
            ("current_limit_cell_sag_v", "V/cell"),
        ):
            rows.append({"category": "battery_ir", "name": item["preset"], "metric": metric, "value": item[metric], "unit": unit, "source": item["source"]})
    for item in coaxial_rows:
        for metric, unit in (
            ("upper_lower_separation_m", "m"),
            ("separation_over_radius", "R"),
            ("separation_over_diameter", "D"),
        ):
            rows.append({"category": "coaxial_spacing", "name": item["preset"], "metric": metric, "value": item[metric], "unit": unit, "source": item["source"]})
    for item in motor_response_rows:
        for metric, unit in (
            ("motor_tau_s", "s"),
            ("rotor_inflow_tau_s", "s"),
            ("motor_tau_vs_ref_up", "x"),
            ("motor_tau_vs_ref_down", "x"),
            ("inflow_tau_vs_ref_up", "x"),
        ):
            rows.append({"category": "motor_response", "name": item["preset"], "metric": metric, "value": item[metric], "unit": unit, "source": item["source"]})
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=["category", "name", "metric", "value", "unit", "source"])
        writer.writeheader()
        writer.writerows(rows)


def write_markdown(
    static: list[dict[str, float | str]],
    forward: list[dict[str, float | str]],
    mqtb: list[dict[str, float | str]],
    command_rows: list[dict[str, float | str]],
    open_models: list[dict[str, float | str]],
    presets: list[dict[str, float | str]],
    comparisons: list[dict[str, float | str]],
    battery: dict[str, float],
    zju_ground: list[dict[str, float | str]],
    ground_rows: list[dict[str, float | str]],
    vrs_rows: list[dict[str, float | str]],
    battery_ir_rows: list[dict[str, float | str]],
    coaxial_rows: list[dict[str, float | str]],
    motor_response_rows: list[dict[str, float | str]],
) -> None:
    path = DOCS / "fpv-sim-model-validation.md"
    five_in = [row for row in mqtb if "5x" in str(row["prop"]) or "5.1x" in str(row["prop"])]
    mqtb_k_values = [float(row["k_n_per_rad2"]) for row in five_in[:8]]
    racing = next(row for row in presets if row["preset"] == "racingQuad")
    uiuc_5_k = statistics.fmean(float(row["k_n_per_rad2"]) for row in static if float(row["diameter_m"]) == 0.127)
    mqtb_5_k = statistics.fmean(mqtb_k_values)
    racing_to_mqtb = float(racing["k_n_per_rad2"]) / mqtb_5_k
    racing_to_uiuc = float(racing["k_n_per_rad2"]) / uiuc_5_k
    zju_single = next(row for row in zju_ground if "single-rotor" in str(row["name"]))
    racing_vrs = next(row for row in vrs_rows if row["preset"] == "racingQuad")
    racing_ground = [row for row in ground_rows if row["preset"] == "racingQuad"]
    racing_battery_ir = next(row for row in battery_ir_rows if row["preset"] == "racingQuad")
    racing_motor_response = next(row for row in motor_response_rows if row["preset"] == "racingQuad")
    lines: list[str] = []
    lines.append("# FPV simulation model validation calculations")
    lines.append("")
    lines.append(f"Generated: {dt.date.today().isoformat()}")
    lines.append("")
    lines.append("This report is generated by `docs/scripts/analyze_fpv_model_sources.py`. It fetches open internet sources, caches the raw files under `docs/data/raw`, and compares them with the current `DroneConfig` presets.")
    lines.append("")
    lines.append("## High-signal findings")
    lines.append("")
    lines.append(f"- The current `racingQuad` rotor coefficient is `{fmt(float(racing['k_n_per_rad2']))}` N/(rad/s)^2. UIUC 5-inch three-blade prop data averages `{fmt(uiuc_5_k)}`, and Mini Quad Test Bench 5-inch FPV motor/prop rows average `{fmt(mqtb_5_k)}`. That puts the current coefficient at `{fmt(racing_to_mqtb, 2)}x` of the FPV bench mean and `{fmt(racing_to_uiuc, 2)}x` of the UIUC 5-inch mean.")
    lines.append(f"- With the current coefficient, `racingQuad` reaches `{fmt(float(racing['max_thrust_n']))}` N per rotor at `{fmt(float(racing['max_rpm_from_k']))}` rpm. Using the Mini Quad Test Bench mean coefficient, the same thrust would require about `{fmt(rpm_from_k_thrust(mqtb_5_k, float(racing['max_thrust_n'])))}` rpm.")
    lines.append(f"- The current `racingQuad` tip Mach at max thrust is `{fmt(float(racing['tip_mach_at_max']), 2)}`. Using FPV bench k for the same thrust and radius would put tip Mach near `{fmt((rpm_from_k_thrust(mqtb_5_k, float(racing['max_thrust_n'])) * 2.0 * math.pi / 60.0) * float(racing['radius_m']) / SPEED_OF_SOUND_25C, 2)}`.")
    lines.append(f"- For the HQ v1s 5x4x3 bench rows, fitted motor current is approximately `I = {fmt(battery['current_a'])} * T^{fmt(battery['current_b'])}` where `T` is per-motor thrust in newtons. This estimates racing hover current near `{fmt(battery['hover_total_current_a'])}` A for four motors before avionics.")
    lines.append(f"- At the current `racingQuad` battery resistance of 0.018 ohm, the fitted hover current implies about `{fmt(battery['hover_sag_v'])}` V pack sag. The 90 A current limit implies `{fmt(battery['limit_per_motor_thrust_n'])}` N per rotor on the fitted HQ prop curve, below the configured `{fmt(float(racing['max_thrust_n']))}` N per rotor.")
    lines.append(f"- The ZJU ground-effect/motor-calibration source reports single-rotor `k_T = {fmt(float(zju_single['k_t_n_per_rpm2']))}` N/rpm^2, which converts to `{fmt(float(zju_single['k_n_per_rad2']))}` N/(rad/s)^2, with `Q/T = {fmt(float(zju_single['qt_m']), 4)}` m. The `Q/T` value is close to this project's 5-inch yaw torque order of magnitude.")
    lines.append(f"- For `racingQuad`, hover induced velocity is `{fmt(float(racing_vrs['hover_induced_velocity_m_s']))}` m/s. The code's VRS intensity band covers roughly `{fmt(float(racing_vrs['current_vrs_entry_m_s']))}-{fmt(float(racing_vrs['current_vrs_exit_end_m_s']))}` m/s descent, while the Cambridge dual-rotor paper reports strongest loss around `1.2-1.3 vi` (`{fmt(float(racing_vrs['paper_peak_loss_low_m_s']))}-{fmt(float(racing_vrs['paper_peak_loss_high_m_s']))}` m/s for this preset).")
    lines.append(f"- `racingQuad` battery IR is `{fmt(float(racing_battery_ir['per_cell_resistance_mohm']))}` mOhm/cell by the inferred `{int(racing_battery_ir['estimated_cells'])}S` pack. That sits in the high-C LiPo plausibility range, but max-current sag still reaches `{fmt(float(racing_battery_ir['current_limit_pack_sag_v']))}` V at the configured limit.")
    lines.append(f"- `racingQuad.motor_tau` is `{fmt(float(racing_motor_response['motor_tau_s']), 4)}` s, about `{fmt(float(racing_motor_response['motor_tau_vs_ref_up']), 2)}x` RotorS/PX4 `timeConstantUp` and `{fmt(float(racing_motor_response['motor_tau_vs_ref_down']), 2)}x` `timeConstantDown`. That is defensible if it includes ESC/load/voltage effects, but it is slower than the simple open-source actuator lag reference.")
    lines.append("")
    lines.append("## UIUC static propeller coefficients")
    lines.append("")
    lines.append("| Source | RPM range | avg CT | avg CP | k, N/(rad/s)^2 | Q/T, m |")
    lines.append("|---|---:|---:|---:|---:|---:|")
    for row in static:
        lines.append(f"| [{row['name']}]({row['url']}) | {fmt(float(row['rpm_min']), 0)}-{fmt(float(row['rpm_max']), 0)} | {fmt(float(row['ct_avg']), 4)} | {fmt(float(row['cp_avg']), 4)} | {fmt(float(row['k_n_per_rad2']))} | {fmt(float(row['qt_m']), 4)} |")
    lines.append("")
    lines.append("## UIUC forward-flow fits")
    lines.append("")
    lines.append("| Source | J range | CT range | linear zero-CT J | R^2 | CT near J=0.45 / static CT |")
    lines.append("|---|---:|---:|---:|---:|---:|")
    for row in forward:
        lines.append(f"| [{row['name']}]({row['url']}) | {fmt(float(row['j_min']), 3)}-{fmt(float(row['j_max']), 3)} | {fmt(float(row['ct_min']), 4)}-{fmt(float(row['ct_max']), 4)} | {fmt(float(row['zero_ct_j']), 3)} | {fmt(float(row['linear_r2']), 3)} | {fmt(float(row['ct_ratio_near_045_vs_static']), 3)} |")
    lines.append("")
    lines.append("These curves are useful for validating `rotorAdvanceRatio`, `axialFlowThrustLossCoefficient`, and any forward-flight CT rolloff. Around `J = 0.45`, these props retain roughly half to two-thirds of static CT.")
    lines.append("")
    lines.append("## Open-source simulator parameter checks")
    lines.append("")
    lines.append("| Source | mass | radius | k | Q/T or kappa | max omega | tau up/down | normalized CT |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|---:|")
    for row in open_models:
        tau = f"{fmt(float(row.get('tau_up_s', float('nan'))), 4)}/{fmt(float(row.get('tau_down_s', float('nan'))), 4)}"
        lines.append(f"| [{row['name']}]({row['url']}) | {fmt(float(row.get('mass_kg', float('nan'))))} kg | {fmt(float(row.get('radius_m', float('nan'))), 4)} m | {fmt(float(row.get('k_n_per_rad2', float('nan'))))} | {fmt(float(row.get('qt_m', float('nan'))), 4)} | {fmt(float(row.get('max_omega_rad_s', float('nan'))), 0)} | {tau} s | {fmt(float(row.get('normalized_ct', float('nan'))), 3)} |")
    lines.append("")
    lines.append("RotorS and PX4 use the same simple thrust form as this project, `T = k * omega^2`. Their normalized CT values are useful as a sanity range, but their vehicle scale and prop geometry differ from a 5-inch FPV racing quad.")
    lines.append("")
    lines.append("gym-pybullet-drones also exposes simple ground-effect and multi-drone downwash formulas in `BaseAviary.py`: extra ground-effect force is proportional to `rpm^2 * kf * gnd_eff_coeff * (R / (4h))^2`, and downwash uses `alpha = dw1 * (R / (4 dz))^2`, `beta = dw2 * dz + dw3`, then a Gaussian lateral falloff. Those formulas are useful references for this project's ground, propwash, and nearby-drone wake terms.")
    lines.append("")
    lines.append("## ZJU ground-effect and motor calibration anchor")
    lines.append("")
    lines.append(f"Source: [Ground-effect-controller supplementary material]({ZJU_GROUND_EFFECT_URL}); paper page: [arXiv]({ZJU_GROUND_EFFECT_PAPER_URL})")
    lines.append("")
    lines.append("| Parameter row | Source context | k_T, N/rpm^2 | k, N/(rad/s)^2 | Q/T, m |")
    lines.append("|---|---|---:|---:|---:|")
    for row in zju_ground:
        lines.append(f"| {row['name']} | {row['source_context']} | {fmt(float(row['k_t_n_per_rpm2']))} | {fmt(float(row['k_n_per_rad2']))} | {fmt(float(row['qt_m']), 4)} |")
    lines.append("")
    lines.append(
        "The same ZJU table reports ground-effect coefficients "
        f"`g1={fmt(ZJU_GROUND_EFFECT_COEFFICIENTS['g1'])}`, "
        f"`g2={fmt(ZJU_GROUND_EFFECT_COEFFICIENTS['g2'])}`, "
        f"`g3={fmt(ZJU_GROUND_EFFECT_COEFFICIENTS['g3'])}`, "
        f"rotor drag `dx={fmt(ZJU_GROUND_EFFECT_COEFFICIENTS['drag_x_n_per_m_s'])}` and `dy={fmt(ZJU_GROUND_EFFECT_COEFFICIENTS['drag_y_n_per_m_s'])}` N/(m/s), "
        f"mass `{fmt(ZJU_GROUND_EFFECT_COEFFICIENTS['mass_kg'])}` kg, and inertia "
        f"`Ix/Iy/Iz={fmt(ZJU_GROUND_EFFECT_COEFFICIENTS['inertia_x_kg_m2'])}/{fmt(ZJU_GROUND_EFFECT_COEFFICIENTS['inertia_y_kg_m2'])}/{fmt(ZJU_GROUND_EFFECT_COEFFICIENTS['inertia_z_kg_m2'])}` kg*m^2."
    )
    lines.append("")
    lines.append("## Ground-effect shape comparison")
    lines.append("")
    lines.append(f"Reference formula source: [gym-pybullet-drones BaseAviary.py]({GYMPYB_BASEAVIARY_URL})")
    lines.append("")
    lines.append("| Preset | height | boost | current @1R | current @2R | current @4R | gym-pybullet CF2 @1R/@2R/@4R |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|")
    for preset in presets:
        rows = [row for row in ground_rows if row["preset"] == preset["preset"]]
        current_by_h = {float(row["h_over_r"]): float(row["current_multiplier"]) for row in rows}
        gym_by_h = {float(row["h_over_r"]): float(row["gympyb_cf2_multiplier"]) for row in rows}
        lines.append(
            f"| {preset['preset']} | {fmt(float(preset.get('ground_effect_height_m', float('nan'))))} m | "
            f"{fmt(float(preset.get('ground_effect_max_boost', float('nan'))))} | "
            f"{fmt(current_by_h[1.0])} | {fmt(current_by_h[2.0])} | {fmt(current_by_h[4.0])} | "
            f"{fmt(gym_by_h[1.0])}/{fmt(gym_by_h[2.0])}/{fmt(gym_by_h[4.0])} |"
        )
    lines.append("")
    lines.append("The current model is intentionally capped and smooth. Compared with the CF2 reference shape, it is much softer at `h=R` but similar by `h=4R` for the racing preset. Treat this as a shape sanity check, not a direct coefficient transplant, because Crazyflie-scale props and FPV 5-inch props operate at different Reynolds number and disk loading.")
    lines.append("")
    lines.append("## Motor and inflow response sanity")
    lines.append("")
    lines.append("RotorS Firefly/Hummingbird and PX4 Iris use first-order motor lag values of `timeConstantUp = 0.0125 s` and `timeConstantDown = 0.0250 s`. This project's `motor_tau` likely includes more than a bare motor command lag, while `rotor_inflow_tau` is an aerodynamic wake term; the table below keeps both visible.")
    lines.append("")
    lines.append("| Preset | motor tau | inflow tau | motor/up ref | motor/down ref | inflow/up ref |")
    lines.append("|---|---:|---:|---:|---:|---:|")
    for row in motor_response_rows:
        lines.append(
            f"| {row['preset']} | {fmt(float(row['motor_tau_s']), 4)} s | "
            f"{fmt(float(row['rotor_inflow_tau_s']), 4)} s | "
            f"{fmt(float(row['motor_tau_vs_ref_up']), 2)}x | "
            f"{fmt(float(row['motor_tau_vs_ref_down']), 2)}x | "
            f"{fmt(float(row['inflow_tau_vs_ref_up']), 2)}x |"
        )
    lines.append("")
    lines.append("## VRS and propwash descent-speed anchors")
    lines.append("")
    lines.append(f"Reference: [Cambridge Flow dual-rotor axial-descent paper]({CAMBRIDGE_VRS_URL}). It reports strongest fixed-RPM thrust loss around `1.2-1.3 vi` and losses up to about one third in the fully developed VRS region.")
    lines.append("")
    lines.append("| Preset | hover vi | code VRS entry-full | paper peak-loss band | code VRS exit | propwash start/full as vi |")
    lines.append("|---|---:|---:|---:|---:|---:|")
    for row in vrs_rows:
        lines.append(
            f"| {row['preset']} | {fmt(float(row['hover_induced_velocity_m_s']))} m/s | "
            f"{fmt(float(row['current_vrs_entry_m_s']))}-{fmt(float(row['current_vrs_full_entry_m_s']))} m/s | "
            f"{fmt(float(row['paper_peak_loss_low_m_s']))}-{fmt(float(row['paper_peak_loss_high_m_s']))} m/s | "
            f"{fmt(float(row['current_vrs_exit_start_m_s']))}-{fmt(float(row['current_vrs_exit_end_m_s']))} m/s | "
            f"{fmt(float(row['propwash_start_ratio_vi']), 2)}/{fmt(float(row['propwash_full_ratio_vi']), 2)} |"
        )
    lines.append("")
    lines.append("The VRS intensity thresholds in the code line up with the literature-normalized descent band. The separate `propwash_start/full` torque disturbance begins earlier than the VRS peak, so it should be treated as dirty-air handling feel rather than a pure thrust-loss lookup table.")
    lines.append("")
    lines.append("## Coaxial X8 spacing anchor")
    lines.append("")
    lines.append(f"Open hardware/data source: [New Dexterity Coaxial Benchmarking Platform]({COAXIAL_BENCHMARK_URL}); result log: [coaxial experiment notes]({COAXIAL_RESULTS_URL}).")
    lines.append("")
    lines.append("| Preset | radius | upper-lower separation | separation/R | separation/D |")
    lines.append("|---|---:|---:|---:|---:|")
    for row in coaxial_rows:
        lines.append(f"| {row['preset']} | {fmt(float(row['radius_m']), 4)} m | {fmt(float(row['upper_lower_separation_m']), 4)} m | {fmt(float(row['separation_over_radius']), 2)} | {fmt(float(row['separation_over_diameter']), 2)} |")
    lines.append("")
    lines.append("The current `coaxialX8` geometry uses `verticalOffset = 0.72R` above and below the arm plane, so the actual rotor-to-rotor separation is `1.44R = 0.72D`. That is inside the open benchmarking platform's reported tested distance range, making it a good next target for lower-rotor efficiency and wake-swirl calibration.")
    lines.append("")
    lines.append("The Hackaday result log reports tests from `z/D = 0.1..1.0` in seven spacing points and 700 data points per rotor set. Its 11-inch rotor set shows mechanical-efficiency local maxima around `0.25 < z/D < 0.4` and `0.7 < z/D < 0.85`, with a local minimum near `z/D = 0.55`; the current X8 spacing of `z/D = 0.72` lands near the second local maximum. The same log notes that equal motor commands usually sit below the maximum-efficiency boundary, so a future X8 model should not assume upper/lower rotors are optimally efficient at equal command.")
    lines.append("")
    lines.append("## Battery internal-resistance anchors")
    lines.append("")
    lines.append(f"Open aging/impedance datasets: [LiPo EIS dataset article]({LIPO_EIS_DATASET_URL}), [Mendeley direct dataset]({MENDELEY_LIPO_DATASET_URL}), [NASA Li-ion aging datasets]({NASA_BATTERY_DATASET_URL}), and [NASA battery zip]({NASA_BATTERY_ZIP_URL}). FPV high-C context: [CHL LiPo IR explainer]({CHL_LIPO_IR_URL}).")
    lines.append("")
    lines.append("| Preset | inferred cells | pack R | per-cell R | current limit | sag at limit |")
    lines.append("|---|---:|---:|---:|---:|---:|")
    for row in battery_ir_rows:
        lines.append(f"| {row['preset']} | {int(row['estimated_cells'])}S | {fmt(float(row['pack_resistance_ohm']))} ohm | {fmt(float(row['per_cell_resistance_mohm']))} mOhm | {fmt(float(row['current_limit_c']))} C | {fmt(float(row['current_limit_pack_sag_v']))} V |")
    lines.append("")
    lines.append("The CHL high-C LiPo guide gives practical per-cell IR bands: many new high-performance packs are around `2-5 mOhm`, below `10 mOhm` is a strong fresh-pack target, `10-20 mOhm` is still usable/healthy, and above `20 mOhm` is tired for high-performance use. The current presets land in the plausible high-current range, but the larger aircraft still show large pack-level sag at their configured current limits.")
    lines.append("")
    lines.append("The Mendeley LiPo dataset exposes raw capacity, partial-discharge, EIS, and fitted ECM CSVs. The fitted model files provide columns `SOC, R_0, R_1, Q_1, a_1, R_2, Q_2, a_2, Q, L`, so `R_0(SOC, SOH)` is a direct candidate for replacing a constant internal resistance with a state-dependent lookup. Caveat: the cells are 3.7 V, 1.1 Ah BAK LP-503562-IS-3 packs measured at 25 C, with 1 A standard discharge and 3 A stress discharge, so the dataset informs SOC/SOH shape more than FPV high-C absolute resistance.")
    lines.append("")
    lines.append("The NASA/DASHlink battery set has a direct public zip of roughly 210 MB. This script links but does not cache that large binary by default. Its impedance records include `Battery_impedance`, `Rectified_impedance`, `Re`, and `Rct`, making it a second candidate for aging/SOH resistance-curve fitting.")
    lines.append("")
    lines.append("## Mini Quad Test Bench 2306/5-inch results")
    lines.append("")
    lines.append(f"Source: [Mini Quad Test Bench Emax Eco 2306 2400kv]({MQTB_URL})")
    lines.append("")
    lines.append("| Prop | avg max thrust | avg max current | avg max RPM | derived k |")
    lines.append("|---|---:|---:|---:|---:|")
    for row in mqtb[:12]:
        lines.append(f"| {row['prop']} | {fmt(float(row['thrust_avg_g']), 0)} g | {fmt(float(row['amps_avg']), 2)} A | {fmt(float(row['rpm_avg']), 0)} | {fmt(float(row['k_n_per_rad2']))} |")
    lines.append("")
    lines.append("HQ v1s 5x4x3 throttle rows used for current fit:")
    lines.append("")
    lines.append("| Throttle row | Thrust | Current | Voltage | RPM | derived k |")
    lines.append("|---:|---:|---:|---:|---:|---:|")
    for row in sorted(command_rows, key=lambda item: float(item["command_fraction"])):
        lines.append(f"| {row['command_label']} | {fmt(float(row['thrust_n']))} N | {fmt(float(row['amps']))} A | {fmt(float(row['volts']))} V | {fmt(float(row['rpm']), 0)} | {fmt(float(row['k_n_per_rad2']))} |")
    lines.append("")
    lines.append("## Current preset derived metrics")
    lines.append("")
    lines.append("| Preset | rotors | mass | radius | k | max RPM | tip Mach | T/W | hover throttle | disk loading | hover vi |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for row in presets:
        lines.append(f"| {row['preset']} | {int(row['rotor_count'])} | {fmt(float(row['mass_kg']))} kg | {fmt(float(row['radius_m']), 4)} m | {fmt(float(row['k_n_per_rad2']))} | {fmt(float(row['max_rpm_from_k']), 0)} | {fmt(float(row['tip_mach_at_max']), 2)} | {fmt(float(row['thrust_to_weight']), 2)} | {fmt(float(row['hover_throttle_linear']), 3)} | {fmt(float(row['hover_disk_loading_n_m2']), 0)} N/m^2 | {fmt(float(row['hover_induced_velocity_m_s']))} m/s |")
    lines.append("")
    lines.append("## Direct comparison metrics")
    lines.append("")
    lines.append("| Name | Metric | Value | Unit | Source |")
    lines.append("|---|---|---:|---|---|")
    for row in comparisons:
        source = row["source"]
        label = "source"
        if isinstance(source, str) and source.startswith("http"):
            source_text = f"[{label}]({source})"
        else:
            source_text = str(source)
        lines.append(f"| {row['name']} | {row['metric']} | {fmt(float(row['value']), 3)} | {row['unit']} | {source_text} |")
    lines.append("")
    lines.append("## Interpretation queue")
    lines.append("")
    lines.append("1. Keep `racingQuad.thrustCoefficient` in the measured 5-inch FPV range unless an explicit unit-conversion layer is added; it changes RPM, blade-pass frequency, tip Mach, gyro notch placement, motor current mapping, and all vibration terms.")
    lines.append("2. Re-run this report after changing rotor constants so telemetry labels such as RPM, tip Mach, blade-pass notch, and motor electrical estimates stay tied to physical units.")
    lines.append("3. Keep `yawTorquePerThrustMeter` separate from thrust-coefficient tuning. The current 5-inch yaw value is close to RotorS/UIUC magnitude, while PX4 Iris uses a much larger 0.06 m constant.")
    lines.append("4. Keep the VRS thrust-loss path tied to induced velocity ratio; the current code thresholds are close to the Cambridge axial-descent reference, while `propwash_start/full` should remain a separate handling-disturbance tune.")
    lines.append("5. Next data targets: digitized coaxial thrust/efficiency curves versus `z/D`, FPV pack IR versus SOC/temperature, and Betaflight blackbox logs with RPM telemetry for propwash recovery validation.")
    lines.append("")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    static = summarize_uiuc_static()
    static_by_family = {str(row["family"]): row for row in static}
    forward = summarize_uiuc_forward(static_by_family)
    mqtb, command_rows = parse_mqtb()
    mqtb = [row for row in mqtb if row["prop"]]
    presets = summarize_presets(parse_drone_presets())

    for source in OPEN_SOURCE_PARAMS:
        fetch_text(source["url"])
    for url in (
        ZJU_GROUND_EFFECT_URL,
        GYMPYB_BASEAVIARY_URL,
        COAXIAL_BENCHMARK_URL,
        COAXIAL_RESULTS_URL,
        CAMBRIDGE_VRS_URL,
        LIPO_EIS_DATASET_URL,
        MENDELEY_LIPO_DATASET_URL,
        NASA_BATTERY_DATASET_URL,
        CHL_LIPO_IR_URL,
    ):
        fetch_text(url)

    five_in_rows = [row for row in mqtb if "5x" in str(row["prop"]) or "5.1x" in str(row["prop"])]
    five_in_k = statistics.fmean(float(row["k_n_per_rad2"]) for row in five_in_rows[:8])
    uiuc_5_static = [row for row in static if abs(float(row["diameter_m"]) - 0.127) < 1.0e-6]
    uiuc_5_k = statistics.fmean(float(row["k_n_per_rad2"]) for row in uiuc_5_static)
    uiuc_5_qt = statistics.fmean(float(row["qt_m"]) for row in uiuc_5_static)
    apc_10 = next(row for row in static if row["family"] == "apcsf_10x4.7")
    racing = next(row for row in presets if row["preset"] == "racingQuad")
    heavy = next(row for row in presets if row["preset"] == "heavyLift")

    comparisons = [
        {
            "name": "racingQuad vs MiniQuadTestBench 5in mean",
            "metric": "k ratio current/source",
            "value": float(racing["k_n_per_rad2"]) / five_in_k,
            "unit": "x",
            "source": MQTB_URL,
        },
        {
            "name": "racingQuad vs UIUC 5in mean",
            "metric": "k ratio current/source",
            "value": float(racing["k_n_per_rad2"]) / uiuc_5_k,
            "unit": "x",
            "source": "UIUC 5in static rows",
        },
        {
            "name": "racingQuad yaw torque",
            "metric": "Q/T ratio current/UIUC_5in_mean",
            "value": float(racing["yaw_qt_m"]) / uiuc_5_qt,
            "unit": "x",
            "source": "UIUC 5in static rows",
        },
        {
            "name": "heavyLift vs APC SF 10x4.7",
            "metric": "k ratio current/source",
            "value": float(heavy["k_n_per_rad2"]) / float(apc_10["k_n_per_rad2"]),
            "unit": "x",
            "source": str(apc_10["url"]),
        },
        {
            "name": "heavyLift yaw torque vs APC SF 10x4.7",
            "metric": "Q/T ratio current/source",
            "value": float(heavy["yaw_qt_m"]) / float(apc_10["qt_m"]),
            "unit": "x",
            "source": str(apc_10["url"]),
        },
    ]

    hq_points = [(float(row["thrust_n"]), float(row["amps"])) for row in command_rows]
    hq_100 = next((row for row in mqtb if row["prop"] == "HQ v1s 5x4x3"), None)
    if hq_100 is not None:
        hq_points.append((thrust_n_from_grams(float(hq_100["thrust_avg_g"])), float(hq_100["amps_avg"])))
    current_a, current_b = power_law_fit(hq_points)
    racing_hover_per_motor = float(racing["mass_kg"]) * G / int(racing["rotor_count"])
    hover_current_per_motor = current_a * racing_hover_per_motor**current_b
    hover_total_current = hover_current_per_motor * int(racing["rotor_count"])
    pack_r = 0.018
    current_limit = 90.0
    limit_per_motor_current = current_limit / int(racing["rotor_count"])
    limit_per_motor_thrust = (limit_per_motor_current / current_a) ** (1.0 / current_b)
    battery = {
        "current_a": current_a,
        "current_b": current_b,
        "hover_total_current_a": hover_total_current,
        "hover_sag_v": hover_total_current * pack_r,
        "limit_per_motor_thrust_n": limit_per_motor_thrust,
    }

    comparisons.extend(
        [
            {
                "name": "racingQuad fitted HQ5x4x3 hover current",
                "metric": "total_motor_current",
                "value": hover_total_current,
                "unit": "A",
                "source": MQTB_URL,
            },
            {
                "name": "racingQuad fitted HQ5x4x3 current-limit thrust",
                "metric": "per_motor_thrust_at_90A_pack",
                "value": limit_per_motor_thrust,
                "unit": "N",
                "source": MQTB_URL,
            },
        ]
    )

    open_models = parse_open_source_params()
    zju_ground = summarize_zju_ground_effect()
    ground_rows = summarize_ground_effect(presets)
    vrs_rows = summarize_vrs(presets)
    battery_ir_rows = summarize_battery_ir(presets)
    coaxial_rows = summarize_coaxial_spacing(presets)
    motor_response_rows = summarize_motor_response(presets, open_models)

    zju_single = next(row for row in zju_ground if "single-rotor" in str(row["name"]))
    comparisons.extend(
        [
            {
                "name": "racingQuad yaw torque vs ZJU single-rotor kI/kT",
                "metric": "Q/T ratio current/source",
                "value": float(racing["yaw_qt_m"]) / float(zju_single["qt_m"]),
                "unit": "x",
                "source": ZJU_GROUND_EFFECT_URL,
            },
            {
                "name": "coaxialX8 geometry spacing",
                "metric": "upper_lower_separation_over_diameter",
                "value": float(coaxial_rows[0]["separation_over_diameter"]) if coaxial_rows else float("nan"),
                "unit": "D",
                "source": COAXIAL_BENCHMARK_URL,
            },
        ]
    )

    write_summary_csv(static, forward, mqtb, open_models, presets, comparisons, zju_ground, ground_rows, vrs_rows, battery_ir_rows, coaxial_rows, motor_response_rows)
    write_markdown(static, forward, mqtb, command_rows, open_models, presets, comparisons, battery, zju_ground, ground_rows, vrs_rows, battery_ir_rows, coaxial_rows, motor_response_rows)
    print("Wrote docs/fpv-sim-model-validation.md")
    print("Wrote docs/data/fpv_model_validation_summary.csv")
    print(f"Cached raw sources in {RAW}")


if __name__ == "__main__":
    main()
