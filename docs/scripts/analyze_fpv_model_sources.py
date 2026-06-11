#!/usr/bin/env python3
"""
Fetch open FPV/multirotor data sources and compare them with DroneConfig presets.

Outputs:
  docs/fpv-sim-model-validation.md
  docs/data/fpv_model_validation_summary.csv
  docs/data/blackbox_log_header_summary.csv
  docs/data/imu_noise_reference_summary.csv
  docs/data/wind_gust_dryden_reference.csv
  docs/data/barometer_reference_summary.csv
  docs/data/battery_temperature_derating_summary.csv
  docs/data/atmosphere_reynolds_mach_summary.csv
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
MENDELEY_ECM_SUMMARY_CSV = DATA / "lipo_ecm_mendeley_r0_summary.csv"

RHO = 1.225
G = 9.80665
SPEED_OF_SOUND_25C = 346.1
STANDARD_SEA_LEVEL_TEMPERATURE_KELVIN = 288.15
STANDARD_LAPSE_RATE_KELVIN_PER_METER = 0.0065
STANDARD_PRESSURE_EXPONENT = 5.255
AIR_GAMMA = 1.4
AIR_GAS_CONSTANT_J_PER_KG_K = 287.05
REFERENCE_AIR_TEMPERATURE_KELVIN = 298.15
AIR_SUTHERLAND_CONSTANT_KELVIN = 110.4
AIR_SUTHERLAND_BETA = 1.458e-6
_JAVA_NUMERIC_CONSTANTS: dict[str, float] | None = None


def repo_path(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


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
ROTORPY_HUMMINGBIRD_URL = "https://raw.githubusercontent.com/spencerfolk/rotorpy/main/rotorpy/vehicles/hummingbird_params.py"
COAXIAL_BENCHMARK_URL = "https://raw.githubusercontent.com/newdexterity/Coaxial-Benchmarking-Platform/master/README.md"
COAXIAL_RESULTS_URL = "https://hackaday.io/project/181977/log/199225-some-results-of-coaxial-rotor-experiments-on-the-benchmarking-platform"
CAMBRIDGE_VRS_URL = "https://www.cambridge.org/core/journals/flow/article/effects-of-rotor-separation-on-the-axial-descent-performance-of-dualrotor-configurations/BE7FE0D2E732E777CBD43F8E65CA0692"
LIPO_EIS_DATASET_URL = "https://pmc.ncbi.nlm.nih.gov/articles/PMC10518458/"
MENDELEY_LIPO_DATASET_URL = "https://data.mendeley.com/datasets/stcppt2r68/1"
NASA_BATTERY_DATASET_URL = "https://data.nasa.gov/dataset/li-ion-battery-aging-datasets"
NASA_BATTERY_ZIP_URL = "https://phm-datasets.s3.amazonaws.com/NASA/5.+Battery+Data+Set.zip"
CHL_LIPO_IR_URL = "https://chinahobbyline.com/blogs/news/lipo-internal-resistance-explained"
BATTERY_UNIVERSITY_TEMPERATURE_URL = "https://batteryuniversity.com/article/bu-502-discharging-at-high-and-low-temperatures"
BATTERY_UNIVERSITY_IR_URL = "https://batteryuniversity.com/article/bu-802a-how-does-rising-internal-resistance-affect-performance"
NASA_ATMOSPHERE_URL = "https://www.grc.nasa.gov/www/k-12/airplane/atmosmet.html"
NASA_SOUND_URL = "https://www.grc.nasa.gov/www/BGH/sound.html"
NASA_VISCOSITY_URL = "https://www.grc.nasa.gov/www/BGH/viscosity.html"
US_STANDARD_ATMOSPHERE_URL = "https://ntrs.nasa.gov/citations/19770009539"
PYFLY_DRYDEN_URL = "https://raw.githubusercontent.com/eivindeb/pyfly/master/pyfly/dryden.py"
UAV_WIND_MODELING_URL = "https://arxiv.org/abs/1905.09954"
U8_DYNO_REPO_URL = "https://github.com/thhsieh/U8-Kv100-Dyno-Data"
U8_DYNO_PAPER_URL = "https://www.mdpi.com/2076-0825/12/8/318"
U8_DYNO_RAW_BASE = "https://raw.githubusercontent.com/thhsieh/U8-Kv100-Dyno-Data/main/Processed%20Data/"
COPPER_TEMP_COEFF_SOURCE_URL = "https://www.copper.org/resources/properties/129_8/"
NEMA_MOTOR_INSULATION_URL = "https://www.nema.org/docs/default-source/technical-document-library/motors-and-generators-standard-mg-1.pdf"
INFINEON_MOSFET_THERMAL_URL = "https://www.infineon.com/dgdl/Infineon-IRL40SC228-DataSheet-v01_00-EN.pdf?fileId=5546d46269e1c019016a04b42abd1c9a"
BETAFLIGHT_RPM_FILTER_URL = "https://betaflight.com/docs/wiki/guides/current/DSHOT-RPM-Filtering"
BETAFLIGHT_PID_TUNING_URL = "https://betaflight.com/docs/wiki/guides/current/PID-Tuning-Guide"
EXPRESSLRS_PACKET_URL = "https://www.expresslrs.org/software/switch-config/"
BETAFLIGHT_BLACKBOX_SOURCE_URL = "https://raw.githubusercontent.com/betaflight/betaflight/master/src/main/blackbox/blackbox.c"
BLACKBOX_LIBRARY_URL = "https://github.com/maxlaverse/blackbox-library"
BLACKBOX_LIBRARY_FIXTURE_URL = "https://raw.githubusercontent.com/maxlaverse/blackbox-library/master/fixtures/normal.bfl"
BETAFLIGHT_PUBLIC_LOG_URL = "https://github.com/betaflight/betaflight/files/5507542/LOG00078.TXT"
MPU6000_DATASHEET_URL = "https://www.cdiweb.com/datasheets/invensense/mpu-6050_datasheet_v3%204.pdf"
ICM20602_PRODUCT_URL = "https://bluerobotics.com/wp-content/uploads/2022/05/ICM20602-DATASHEET.pdf"
BMI270_DATASHEET_URL = "https://www.bosch-sensortec.com/media/boschsensortec/downloads/datasheets/bst-bmi270-ds000.pdf"
ICM42688P_PRODUCT_URL = "https://invensense.tdk.com/en-us/products/6-axis/icm-42688-p"
BMP280_DATASHEET_URL = "https://www.bosch-sensortec.com/media/boschsensortec/downloads/datasheets/bst-bmp280-ds001.pdf"
BMP388_DATASHEET_URL = "https://www.bosch-sensortec.com/media/boschsensortec/downloads/datasheets/bst-bmp388-ds001.pdf"
DPS310_DATASHEET_URL = "https://www.infineon.com/dgdl/Infineon-DPS310-DataSheet-v01_02-EN.pdf?fileId=5546d462576f34750157750826c42242"
MS5611_DATASHEET_URL = "https://www.hpinfotech.ro/MS5611-01BA03.pdf"

BLACKBOX_LOG_SOURCES = [
    {
        "name": "Betaflight issue LOG00078",
        "url": BETAFLIGHT_PUBLIC_LOG_URL,
        "context": "public Betaflight 4.2.4 issue attachment with DShot bidirectional/RPM-filter headers",
    },
    {
        "name": "blackbox-library normal.bfl",
        "url": BLACKBOX_LIBRARY_FIXTURE_URL,
        "context": "small open parser fixture for Blackbox field layout",
    },
]

IMU_SENSOR_REFERENCES = [
    {
        "name": "MPU-6000/MPU-6050",
        "gyro_noise_dps_sqrt_hz": 0.005,
        "accel_noise_ug_sqrt_hz": 400.0,
        "source": MPU6000_DATASHEET_URL,
    },
    {
        "name": "ICM-20602",
        "gyro_noise_dps_sqrt_hz": 0.004,
        "accel_noise_ug_sqrt_hz": 100.0,
        "source": ICM20602_PRODUCT_URL,
    },
    {
        "name": "BMI270",
        "gyro_noise_dps_sqrt_hz": 0.008,
        "accel_noise_ug_sqrt_hz": 160.0,
        "source": BMI270_DATASHEET_URL,
    },
    {
        "name": "ICM-42688-P",
        "gyro_noise_dps_sqrt_hz": 0.0028,
        "accel_noise_ug_sqrt_hz": 70.0,
        "source": ICM42688P_PRODUCT_URL,
    },
]

BAROMETER_SENSOR_REFERENCES = [
    {
        "name": "Bosch BMP280",
        "pressure_noise_pa": 1.3,
        "relative_accuracy_pa": 12.0,
        "source": BMP280_DATASHEET_URL,
        "note": "RMS pressure noise typical at ultra-high-resolution setting; relative accuracy from datasheet operating range.",
    },
    {
        "name": "Bosch BMP388",
        "pressure_noise_pa": 0.64,
        "relative_accuracy_pa": 8.0,
        "source": BMP388_DATASHEET_URL,
        "note": "Datasheet pressure noise/relative accuracy used as sensor-only floor.",
    },
    {
        "name": "Infineon DPS310",
        "pressure_noise_pa": 0.2,
        "relative_accuracy_pa": 6.0,
        "source": DPS310_DATASHEET_URL,
        "note": "High-precision pressure mode; relative accuracy used for slow altitude bias floor.",
    },
    {
        "name": "TE MS5611-01BA03",
        "pressure_noise_pa": 1.2,
        "relative_accuracy_pa": 10.0,
        "source": MS5611_DATASHEET_URL,
        "note": "0.012 mbar pressure resolution converted to Pa; relative accuracy is approximate absolute/temperature residual scale.",
    },
]

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
    {
        "name": "RotorPy Hummingbird",
        "vehicle": "rotorpy_hummingbird",
        "url": ROTORPY_HUMMINGBIRD_URL,
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

ATMOSPHERE_SCENARIOS = [
    {
        "scenario": "sea_level_isa",
        "altitude_m": 0.0,
        "ambient_temperature_c": 15.0,
        "note": "ISA sea-level reference used by the standard-atmosphere pressure law.",
    },
    {
        "scenario": "project_default_25c",
        "altitude_m": 0.0,
        "ambient_temperature_c": 25.0,
        "note": "Current DroneEnvironment.calm temperature; standard-atmosphere density ratio is below 1 because ISA sea level is 15 C.",
    },
    {
        "scenario": "cold_sea_level_-10c",
        "altitude_m": 0.0,
        "ambient_temperature_c": -10.0,
        "note": "Cold dense air and lower speed of sound; useful for max-tip-Mach checks.",
    },
    {
        "scenario": "hot_sea_level_38c",
        "altitude_m": 0.0,
        "ambient_temperature_c": 38.0,
        "note": "Hot sea-level day used by existing environment tests.",
    },
    {
        "scenario": "mountain_1500m_20c",
        "altitude_m": 1500.0,
        "ambient_temperature_c": 20.0,
        "note": "Warm mountain field; common high-density-altitude operating case.",
    },
    {
        "scenario": "mountain_3000m_isa",
        "altitude_m": 3000.0,
        "ambient_temperature_c": -4.5,
        "note": "Existing code-test mountain case near ISA lapse from 15 C.",
    },
    {
        "scenario": "mountain_3000m_hot_30c",
        "altitude_m": 3000.0,
        "ambient_temperature_c": 30.0,
        "note": "High-density-altitude stress case for thrust margin and Reynolds scaling.",
    },
]


def cache_name(url: str) -> str:
    tail = re.sub(r"[^A-Za-z0-9._-]+", "_", url.split("//", 1)[-1])
    return tail[-150:]


def fetch_text(url: str) -> str:
    RAW.mkdir(parents=True, exist_ok=True)
    path = RAW / cache_name(url)
    if not path.exists():
        last_error: Exception | None = None
        for _ in range(3):
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "fpv-dronecraft-validation/1.0"})
                with urllib.request.urlopen(req, timeout=120) as response:
                    path.write_bytes(response.read())
                break
            except Exception as exc:
                last_error = exc
        else:
            assert last_error is not None
            raise last_error
    return path.read_text(encoding="utf-8", errors="replace")


def java_numeric_constants() -> dict[str, float]:
    global _JAVA_NUMERIC_CONSTANTS
    if _JAVA_NUMERIC_CONSTANTS is not None:
        return _JAVA_NUMERIC_CONSTANTS
    constants: dict[str, float] = {}
    if DRONE_CONFIG.exists():
        src = DRONE_CONFIG.read_text(encoding="utf-8")
        env: dict[str, object] = {"Math": math, "math": math}
        for _, name, expr in re.findall(r"public static final (double|int)\s+(\w+)\s*=\s*([^;]+);", src):
            try:
                constants[name] = float(eval(expr.strip(), {"__builtins__": {}}, {**env, **constants}))
            except Exception:
                continue
    _JAVA_NUMERIC_CONSTANTS = constants
    return constants


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
    env = {"Math": math, "math": math, **java_numeric_constants()}
    return float(eval(expr, {"__builtins__": {}}, env))


def safe_eval_number_with_locals(expr: str, locals_map: dict[str, float]) -> float:
    expr = expr.strip()
    env = {"Math": math, "math": math, **java_numeric_constants(), **locals_map}
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


def vec3_components(expr: str) -> tuple[float, float, float]:
    match = re.search(r"new\s+Vec3\((.*)\)", expr, re.S)
    if not match:
        return (float("nan"), float("nan"), float("nan"))
    parts = split_top_level_args(match.group(1))
    if len(parts) != 3:
        return (float("nan"), float("nan"), float("nan"))
    try:
        return tuple(safe_eval_number(part) for part in parts)  # type: ignore[return-value]
    except Exception:
        return (float("nan"), float("nan"), float("nan"))


def arg_vec3(args: list[str], index: int) -> tuple[float, float, float]:
    if index < 0 or index >= len(args):
        return (float("nan"), float("nan"), float("nan"))
    return vec3_components(args[index])


def preset_rotor_blade_count(body: str, locals_map: dict[str, float]) -> float:
    match = re.search(r"\.withRotorBladeCount\(([^)]+)\)", body)
    if not match:
        return 2.0
    try:
        return safe_eval_number_with_locals(match.group(1), locals_map)
    except Exception:
        return 2.0


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
        var = {
            key: safe_eval_number(value)
            for _, key, value in re.findall(r"(double|int)\s+(\w+)\s*=\s*([^;]+);", body)
        }
        args = constructor_args(body)
        mass = arg_number(args, 0)
        inertia = arg_vec3(args, 1)
        body_drag = arg_vec3(args, 8)
        rotor_count = body.count("new RotorSpec(") + body.count("rotorAtDegrees(")
        blade_count = preset_rotor_blade_count(body, var)
        presets.append(
            {
                "preset": name,
                "mass_kg": mass,
                "inertia_x_kg_m2": inertia[0],
                "inertia_y_kg_m2": inertia[1],
                "inertia_z_kg_m2": inertia[2],
                "rotor_count": rotor_count,
                "max_thrust_n": var.get("maxRotorThrust", float("nan")),
                "k_n_per_rad2": var.get("thrustCoefficient", float("nan")),
                "yaw_qt_m": var.get("yawTorquePerThrust", float("nan")),
                "radius_m": var.get("rotorRadius", float("nan")),
                "rotor_blade_count": blade_count,
                "rotor_inertia_kg_m2": var.get("rotorInertia", float("nan")),
                "inflow_tau_s": var.get("inflowTimeConstant", float("nan")),
                "linear_drag_coefficient": arg_number(args, 7),
                "body_drag_x": body_drag[0],
                "body_drag_y": body_drag[1],
                "body_drag_z": body_drag[2],
                "ground_effect_height_m": arg_number(args, 9),
                "ground_effect_max_boost": arg_number(args, 10),
                "propwash_start_m_s": arg_number(args, 11),
                "propwash_full_m_s": arg_number(args, 12),
                "propwash_torque_nm": arg_number(args, 13),
                "angular_drag_coefficient": arg_number(args, 14),
                "motor_tau_s": arg_number(args, 15),
                "motor_thermal_rise_c_s": arg_number(args, 22),
                "motor_cooling_rate_s": arg_number(args, 23),
                "motor_thermal_limit_c": arg_number(args, 24),
                "motor_thermal_cutoff_c": arg_number(args, 25),
                "gyro_low_pass_hz": arg_number(args, 28),
                "gyro_noise_stddev_rad_s": arg_number(args, 29),
                "accelerometer_low_pass_hz": arg_number(args, 30),
                "accelerometer_noise_stddev_m_s2": arg_number(args, 31),
                "control_latency_s": arg_number(args, 32),
                "battery_nominal_v": arg_number(args, 33),
                "battery_empty_v": arg_number(args, 34),
                "battery_resistance_ohm": arg_number(args, 35),
                "battery_capacity_ah": arg_number(args, 36),
                "max_battery_current_a": arg_number(args, 37),
                "rc_smoothing_tau_s": arg_number(args, 46),
                "rc_command_latency_s": arg_number(args, 47),
                "rc_failsafe_timeout_s": arg_number(args, 48),
                "rc_frame_rate_hz": arg_number(args, 56),
                "rc_channel_resolution_steps": arg_number(args, 57),
                "esc_command_frame_rate_hz": arg_number(args, 58),
                "esc_command_resolution_steps": arg_number(args, 59),
            }
        )

    octo = next((preset for preset in presets if preset["preset"] == "octoLift"), None)
    if octo is not None:
        coax = dict(octo)
        coax["preset"] = "coaxialX8"
        coax["mass_kg"] = 7.2
        coax["inertia_x_kg_m2"] = 0.245
        coax["inertia_y_kg_m2"] = 0.430
        coax["inertia_z_kg_m2"] = 0.255
        coax["body_drag_x"] = 0.50
        coax["body_drag_y"] = 0.34
        coax["body_drag_z"] = 0.58
        coax["angular_drag_coefficient"] = 1.25
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


def first_inertia_attrs(text: str) -> tuple[float, float, float]:
    match = re.search(r"<inertia\b([^>]*)>", text)
    if not match:
        return (float("nan"), float("nan"), float("nan"))
    attrs = dict(re.findall(r'([A-Za-z0-9_]+)="([^"]+)"', match.group(1)))
    return (
        float(attrs.get("ixx", "nan")),
        float(attrs.get("iyy", "nan")),
        float(attrs.get("izz", "nan")),
    )


def py_dict_value(text: str, name: str) -> float:
    match = re.search(rf"['\"]{re.escape(name)}['\"]\s*:\s*([^,\n}}]+)", text)
    return safe_eval_number(match.group(1)) if match else float("nan")


def py_dict_list(text: str, name: str) -> list[float]:
    match = re.search(rf"['\"]{re.escape(name)}['\"]\s*:\s*np\.array\(\[([^\]]+)\]\)", text)
    if not match:
        match = re.search(rf"['\"]{re.escape(name)}['\"]\s*:\s*\[([^\]]+)\]", text)
    if not match:
        return []
    return [safe_eval_number(part.strip()) for part in match.group(1).split(",") if part.strip()]


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
            inertia = first_inertia_attrs(text)
            rows.append(
                {
                    "name": source["name"],
                    "url": source["url"],
                    "mass_kg": mass,
                    "inertia_x_kg_m2": inertia[0],
                    "inertia_y_kg_m2": inertia[1],
                    "inertia_z_kg_m2": inertia[2],
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
                    "inertia_x_kg_m2": first_xml_value(text, "ixx"),
                    "inertia_y_kg_m2": first_xml_value(text, "iyy"),
                    "inertia_z_kg_m2": first_xml_value(text, "izz"),
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
            inertia = first_inertia_attrs(text)
            rows.append(
                {
                    "name": source["name"],
                    "url": source["url"],
                    "mass_kg": xml_attr_value(text, "mass", "value"),
                    "inertia_x_kg_m2": inertia[0],
                    "inertia_y_kg_m2": inertia[1],
                    "inertia_z_kg_m2": inertia[2],
                    "arm_m": props.get("arm", float("nan")),
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
        elif vehicle == "rotorpy_hummingbird":
            inertia = [
                py_dict_value(text, "Ixx"),
                py_dict_value(text, "Iyy"),
                py_dict_value(text, "Izz"),
            ]
            c_drag = py_dict_list(text, "c_D")
            rows.append(
                {
                    "name": source["name"],
                    "url": source["url"],
                    "mass_kg": py_dict_value(text, "mass"),
                    "inertia_x_kg_m2": inertia[0],
                    "inertia_y_kg_m2": inertia[1],
                    "inertia_z_kg_m2": inertia[2],
                    "arm_m": py_dict_value(text, "arm_length"),
                    "radius_m": float("nan"),
                    "k_n_per_rad2": py_dict_value(text, "k_eta"),
                    "qt_m": py_dict_value(text, "k_m") / py_dict_value(text, "k_eta"),
                    "max_omega_rad_s": py_dict_value(text, "rotor_speed_max"),
                    "tau_up_s": float("nan"),
                    "tau_down_s": float("nan"),
                    "normalized_ct": float("nan"),
                    "body_drag_x": c_drag[0] if len(c_drag) > 0 else py_dict_value(text, "c_Dx"),
                    "body_drag_y": c_drag[1] if len(c_drag) > 1 else py_dict_value(text, "c_Dy"),
                    "body_drag_z": c_drag[2] if len(c_drag) > 2 else py_dict_value(text, "c_Dz"),
                }
            )
    return rows


def default_blade_pitch_m(radius_m: float) -> float:
    return max(0.01, radius_m * 1.70)


def summarize_presets(presets: list[dict[str, float | str]]) -> list[dict[str, float | str]]:
    rows = []
    for preset in presets:
        max_thrust = float(preset["max_thrust_n"])
        k = float(preset["k_n_per_rad2"])
        radius = float(preset["radius_m"])
        blade_pitch = default_blade_pitch_m(radius)
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
                "blade_pitch_m": blade_pitch,
                "thrust_to_weight": total_max / weight,
                "hover_throttle_linear": weight / total_max,
                "hover_disk_loading_n_m2": weight / (rotors * math.pi * radius * radius),
                "hover_thrust_per_rotor_n": weight / rotors,
                "hover_induced_velocity_m_s": math.sqrt((weight / rotors) / (2.0 * RHO * math.pi * radius * radius)),
            }
        )
    return rows


def standard_atmosphere_pressure_ratio(altitude_m: float) -> float:
    altitude = min(18000.0, max(-1000.0, altitude_m))
    base = 1.0 - STANDARD_LAPSE_RATE_KELVIN_PER_METER * altitude / STANDARD_SEA_LEVEL_TEMPERATURE_KELVIN
    return min(1.25, max(0.15, base)) ** STANDARD_PRESSURE_EXPONENT


def standard_atmosphere_density_ratio(altitude_m: float, temperature_c: float) -> float:
    temperature_k = min(338.15, max(233.15, temperature_c + 273.15))
    ratio = standard_atmosphere_pressure_ratio(altitude_m) * STANDARD_SEA_LEVEL_TEMPERATURE_KELVIN / temperature_k
    return min(1.35, max(0.35, ratio))


def speed_of_sound_m_s(temperature_c: float) -> float:
    temperature_k = min(338.15, max(233.15, temperature_c + 273.15))
    return math.sqrt(AIR_GAMMA * AIR_GAS_CONSTANT_J_PER_KG_K * temperature_k)


def air_dynamic_viscosity_pa_s(temperature_c: float) -> float:
    temperature_k = min(338.15, max(233.15, temperature_c + 273.15))
    return AIR_SUTHERLAND_BETA * temperature_k**1.5 / (temperature_k + AIR_SUTHERLAND_CONSTANT_KELVIN)


def air_dynamic_viscosity_ratio(temperature_c: float) -> float:
    mu = air_dynamic_viscosity_pa_s(temperature_c)
    reference_mu = air_dynamic_viscosity_pa_s(REFERENCE_AIR_TEMPERATURE_KELVIN - 273.15)
    return min(1.20, max(0.70, mu / reference_mu))


def rotor_blade_pitch_ratio(radius_m: float, blade_pitch_m: float) -> float:
    return min(3.50, max(0.35, blade_pitch_m / max(1.0e-6, default_blade_pitch_m(radius_m))))


def rotor_low_reynolds_index(
    preset: dict[str, float | str],
    omega_rad_s: float,
    density_ratio: float,
    temperature_c: float,
) -> float:
    radius = float(preset["radius_m"])
    blade_pitch = float(preset.get("blade_pitch_m", default_blade_pitch_m(radius)))
    tip_speed = abs(omega_rad_s) * radius
    density_viscosity_ratio = min(1.90, max(0.20, max(0.0, density_ratio) / air_dynamic_viscosity_ratio(temperature_c)))
    radius_scale = min(3.0, max(0.32, radius / 0.0635))
    pitch_chord_proxy = min(1.18, max(0.78, 0.70 + 0.30 * math.sqrt(rotor_blade_pitch_ratio(radius, blade_pitch))))
    return (
        density_viscosity_ratio
        * radius_scale
        * pitch_chord_proxy
        * min(2.8, max(0.0, tip_speed / 34.0))
    )


def rotor_low_reynolds_loss_proxy(
    preset: dict[str, float | str],
    omega_rad_s: float,
    density_ratio: float,
    temperature_c: float,
) -> float:
    radius = float(preset["radius_m"])
    spin_ratio = min(1.10, max(0.0, abs(omega_rad_s) / (float(preset["max_rpm_from_k"]) * 2.0 * math.pi / 60.0)))
    if spin_ratio <= 0.08:
        return 0.0
    radius_scale = min(3.0, max(0.32, radius / 0.0635))
    small_prop_factor = 1.0 - smooth_step(0.62, 0.96, radius_scale)
    if small_prop_factor <= 1.0e-6:
        return 0.0
    reynolds_index = rotor_low_reynolds_index(preset, omega_rad_s, density_ratio, temperature_c)
    low_reynolds = 1.0 - smooth_step(0.52, 1.05, reynolds_index)
    return min(1.0, max(0.0, low_reynolds * small_prop_factor * smooth_step(0.10, 0.34, spin_ratio)))


def rotor_compressibility_intensity(tip_mach: float) -> float:
    return smooth_step(0.46, 0.82, tip_mach)


def rotor_compressibility_thrust_scale(tip_mach: float) -> float:
    return min(1.0, max(0.74, 1.0 - 0.20 * rotor_compressibility_intensity(tip_mach)))


def proxy_blade_chord_m(radius_m: float, blade_pitch_m: float) -> float:
    pitch_chord_proxy = min(1.18, max(0.78, 0.70 + 0.30 * math.sqrt(rotor_blade_pitch_ratio(radius_m, blade_pitch_m))))
    return 0.12 * radius_m * pitch_chord_proxy


def summarize_atmosphere_reynolds(presets: list[dict[str, float | str]]) -> list[dict[str, float | str]]:
    fetch_text(NASA_ATMOSPHERE_URL)
    fetch_text(NASA_SOUND_URL)
    fetch_text(NASA_VISCOSITY_URL)
    fetch_text(US_STANDARD_ATMOSPHERE_URL)
    rows: list[dict[str, float | str]] = []
    for scenario in ATMOSPHERE_SCENARIOS:
        altitude = float(scenario["altitude_m"])
        temperature = float(scenario["ambient_temperature_c"])
        pressure_ratio = standard_atmosphere_pressure_ratio(altitude)
        density_ratio = standard_atmosphere_density_ratio(altitude, temperature)
        density = RHO * density_ratio
        sound = speed_of_sound_m_s(temperature)
        mu = air_dynamic_viscosity_pa_s(temperature)
        viscosity_ratio = air_dynamic_viscosity_ratio(temperature)
        same_thrust_rpm_scale = 1.0 / math.sqrt(max(1.0e-6, density_ratio))
        for preset in presets:
            radius = float(preset["radius_m"])
            blade_pitch = float(preset.get("blade_pitch_m", default_blade_pitch_m(radius)))
            chord = proxy_blade_chord_m(radius, blade_pitch)
            k = float(preset["k_n_per_rad2"])
            hover_thrust = float(preset["hover_thrust_per_rotor_n"])
            max_thrust = float(preset["max_thrust_n"])
            hover_omega = math.sqrt(hover_thrust / k)
            max_omega = math.sqrt(max_thrust / k)
            hover_tip_speed = hover_omega * radius
            max_tip_speed = max_omega * radius
            hover_re_75 = density * (0.75 * hover_tip_speed) * chord / mu
            max_re_75 = density * (0.75 * max_tip_speed) * chord / mu
            hover_tip_mach = hover_tip_speed / sound
            max_tip_mach = max_tip_speed / sound
            rows.append(
                {
                    "scenario": scenario["scenario"],
                    "altitude_m": altitude,
                    "ambient_temperature_c": temperature,
                    "pressure_ratio": pressure_ratio,
                    "density_ratio": density_ratio,
                    "density_kg_m3": density,
                    "speed_of_sound_m_s": sound,
                    "dynamic_viscosity_pa_s": mu,
                    "viscosity_ratio_25c": viscosity_ratio,
                    "same_thrust_rpm_scale": same_thrust_rpm_scale,
                    "preset": preset["preset"],
                    "radius_m": radius,
                    "blade_pitch_m": blade_pitch,
                    "proxy_chord_m": chord,
                    "hover_tip_speed_m_s": hover_tip_speed,
                    "max_tip_speed_m_s": max_tip_speed,
                    "hover_tip_mach": hover_tip_mach,
                    "max_tip_mach": max_tip_mach,
                    "hover_reynolds_75r_proxy": hover_re_75,
                    "max_reynolds_75r_proxy": max_re_75,
                    "hover_code_reynolds_index": rotor_low_reynolds_index(preset, hover_omega, density_ratio, temperature),
                    "max_code_reynolds_index": rotor_low_reynolds_index(preset, max_omega, density_ratio, temperature),
                    "hover_low_reynolds_loss_proxy": rotor_low_reynolds_loss_proxy(preset, hover_omega, density_ratio, temperature),
                    "max_low_reynolds_loss_proxy": rotor_low_reynolds_loss_proxy(preset, max_omega, density_ratio, temperature),
                    "hover_compressibility_thrust_scale": rotor_compressibility_thrust_scale(hover_tip_mach),
                    "max_compressibility_thrust_scale": rotor_compressibility_thrust_scale(max_tip_mach),
                    "source": NASA_ATMOSPHERE_URL,
                    "note": scenario["note"],
                }
            )
    path = DATA / "atmosphere_reynolds_mach_summary.csv"
    DATA.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        fieldnames = list(rows[0].keys())
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    return rows


def radius_of_gyration(inertia: float, mass: float) -> float:
    if inertia <= 0.0 or mass <= 0.0 or not math.isfinite(inertia) or not math.isfinite(mass):
        return float("nan")
    return math.sqrt(inertia / mass)


def summarize_inertia_geometry(
    presets: list[dict[str, float | str]],
    open_models: list[dict[str, float | str]],
) -> list[dict[str, float | str]]:
    rows = []
    for source_name, source_rows, source_ref in (
        ("current_preset", presets, repo_path(DRONE_CONFIG)),
        ("open_source_model", open_models, ""),
    ):
        for row in source_rows:
            mass = float(row.get("mass_kg", float("nan")))
            ix = float(row.get("inertia_x_kg_m2", float("nan")))
            iy = float(row.get("inertia_y_kg_m2", float("nan")))
            iz = float(row.get("inertia_z_kg_m2", float("nan")))
            if not all(math.isfinite(value) for value in (mass, ix, iy, iz)):
                continue
            if source_name == "current_preset":
                yaw_inertia = iy
                roll_pitch_inertia = (ix + iz) * 0.5
                yaw_axis = "project Y"
            else:
                yaw_inertia = iz
                roll_pitch_inertia = (ix + iy) * 0.5
                yaw_axis = "source Z"
            rows.append(
                {
                    "kind": source_name,
                    "name": row.get("preset", row.get("name", "")),
                    "mass_kg": mass,
                    "inertia_x_kg_m2": ix,
                    "inertia_y_kg_m2": iy,
                    "inertia_z_kg_m2": iz,
                    "rg_x_m": radius_of_gyration(ix, mass),
                    "rg_y_m": radius_of_gyration(iy, mass),
                    "rg_z_m": radius_of_gyration(iz, mass),
                    "yaw_axis": yaw_axis,
                    "yaw_to_roll_pitch_inertia_ratio": yaw_inertia / roll_pitch_inertia if roll_pitch_inertia > 0.0 else float("nan"),
                    "source": row.get("url", source_ref),
                }
            )
    return rows


def summarize_body_drag(
    presets: list[dict[str, float | str]],
    open_models: list[dict[str, float | str]],
) -> list[dict[str, float | str]]:
    rows = []
    for source_name, source_rows, source_ref in (
        ("current_preset", presets, repo_path(DRONE_CONFIG)),
        ("open_source_model", open_models, ""),
    ):
        for row in source_rows:
            body_x = float(row.get("body_drag_x", float("nan")))
            body_y = float(row.get("body_drag_y", float("nan")))
            body_z = float(row.get("body_drag_z", float("nan")))
            if not any(math.isfinite(value) for value in (body_x, body_y, body_z)):
                continue
            linear = float(row.get("linear_drag_coefficient", 0.0))
            if not math.isfinite(linear):
                linear = 0.0
            mass = float(row.get("mass_kg", float("nan")))
            weight = mass * G if math.isfinite(mass) and mass > 0.0 else float("nan")
            for speed in (10.0, 20.0):
                v2 = speed * speed
                axis_x = linear + body_x if math.isfinite(body_x) else float("nan")
                axis_y = linear + body_y if math.isfinite(body_y) else float("nan")
                axis_z = linear + body_z if math.isfinite(body_z) else float("nan")
                rows.append(
                    {
                        "kind": source_name,
                        "name": row.get("preset", row.get("name", "")),
                        "speed_m_s": speed,
                        "linear_drag_coefficient": linear,
                        "body_drag_x": body_x,
                        "body_drag_y": body_y,
                        "body_drag_z": body_z,
                        "axis_x_drag_force_n": axis_x * v2 if math.isfinite(axis_x) else float("nan"),
                        "axis_y_drag_force_n": axis_y * v2 if math.isfinite(axis_y) else float("nan"),
                        "axis_z_drag_force_n": axis_z * v2 if math.isfinite(axis_z) else float("nan"),
                        "axis_x_drag_over_weight": axis_x * v2 / weight if weight > 0.0 and math.isfinite(axis_x) else float("nan"),
                        "axis_z_drag_over_weight": axis_z * v2 / weight if weight > 0.0 and math.isfinite(axis_z) else float("nan"),
                        "axis_x_equiv_cda_m2": 2.0 * axis_x / RHO if math.isfinite(axis_x) else float("nan"),
                        "axis_z_equiv_cda_m2": 2.0 * axis_z / RHO if math.isfinite(axis_z) else float("nan"),
                        "source": row.get("url", source_ref),
                    }
                )
    return rows


def interval_ms(rate_hz: float) -> float:
    return 1000.0 / rate_hz if rate_hz > 0.0 and math.isfinite(rate_hz) else float("nan")


def summarize_timing_vibration(presets: list[dict[str, float | str]]) -> list[dict[str, float | str]]:
    rows = []
    for preset in presets:
        blade_count = float(preset.get("rotor_blade_count", 2.0))
        hover_thrust = float(preset["hover_thrust_per_rotor_n"])
        k = float(preset["k_n_per_rad2"])
        hover_rpm = rpm_from_k_thrust(k, hover_thrust)
        max_rpm = float(preset["max_rpm_from_k"])
        hover_motor_hz = hover_rpm / 60.0
        max_motor_hz = max_rpm / 60.0
        gyro_lpf = float(preset.get("gyro_low_pass_hz", float("nan")))
        rc_rate = float(preset.get("rc_frame_rate_hz", float("nan")))
        esc_rate = float(preset.get("esc_command_frame_rate_hz", float("nan")))
        control_latency = float(preset.get("control_latency_s", float("nan")))
        rc_latency = float(preset.get("rc_command_latency_s", float("nan")))
        rc_smoothing = float(preset.get("rc_smoothing_tau_s", float("nan")))
        rows.append(
            {
                "preset": preset["preset"],
                "configured_blade_count": blade_count,
                "reference_3blade_count": 3.0,
                "hover_rpm": hover_rpm,
                "max_rpm": max_rpm,
                "hover_motor_hz": hover_motor_hz,
                "max_motor_hz": max_motor_hz,
                "hover_blade_pass_hz": hover_motor_hz * blade_count,
                "max_blade_pass_hz": max_motor_hz * blade_count,
                "hover_3blade_pass_hz": hover_motor_hz * 3.0,
                "max_3blade_pass_hz": max_motor_hz * 3.0,
                "blade_pass_3blade_to_configured_ratio": 3.0 / blade_count if blade_count > 0.0 else float("nan"),
                "gyro_low_pass_hz": gyro_lpf,
                "gyro_lpf_over_hover_blade_pass": gyro_lpf / (hover_motor_hz * blade_count) if hover_motor_hz * blade_count > 0.0 else float("nan"),
                "gyro_lpf_over_hover_3blade_pass": gyro_lpf / (hover_motor_hz * 3.0) if hover_motor_hz > 0.0 else float("nan"),
                "rc_frame_rate_hz": rc_rate,
                "rc_frame_interval_ms": interval_ms(rc_rate),
                "esc_command_frame_rate_hz": esc_rate,
                "esc_command_frame_interval_ms": interval_ms(esc_rate),
                "control_latency_ms": control_latency * 1000.0 if math.isfinite(control_latency) else float("nan"),
                "rc_command_latency_ms": rc_latency * 1000.0 if math.isfinite(rc_latency) else float("nan"),
                "rc_smoothing_tau_ms": rc_smoothing * 1000.0 if math.isfinite(rc_smoothing) else float("nan"),
                "source": repo_path(DRONE_CONFIG),
            }
        )
    return rows


def one_pole_enbw_hz(cutoff_hz: float) -> float:
    return math.pi * cutoff_hz / 2.0 if cutoff_hz > 0.0 and math.isfinite(cutoff_hz) else float("nan")


def summarize_imu_noise(presets: list[dict[str, float | str]]) -> list[dict[str, float | str]]:
    rows: list[dict[str, float | str]] = []
    for preset in presets:
        gyro_lpf = float(preset.get("gyro_low_pass_hz", float("nan")))
        accel_lpf = float(preset.get("accelerometer_low_pass_hz", float("nan")))
        configured_gyro = float(preset.get("gyro_noise_stddev_rad_s", float("nan")))
        configured_accel = float(preset.get("accelerometer_noise_stddev_m_s2", float("nan")))
        gyro_enbw = one_pole_enbw_hz(gyro_lpf)
        accel_enbw = one_pole_enbw_hz(accel_lpf)
        for sensor in IMU_SENSOR_REFERENCES:
            gyro_density = float(sensor["gyro_noise_dps_sqrt_hz"]) * math.pi / 180.0
            accel_density = float(sensor["accel_noise_ug_sqrt_hz"]) * 1.0e-6 * G
            gyro_rms = gyro_density * math.sqrt(gyro_enbw) if gyro_enbw > 0.0 else float("nan")
            accel_rms = accel_density * math.sqrt(accel_enbw) if accel_enbw > 0.0 else float("nan")
            gyro_equiv_bw = (configured_gyro / gyro_density) ** 2 if gyro_density > 0.0 and configured_gyro > 0.0 else float("nan")
            accel_equiv_bw = (configured_accel / accel_density) ** 2 if accel_density > 0.0 and configured_accel > 0.0 else float("nan")
            rows.append(
                {
                    "preset": preset["preset"],
                    "sensor": sensor["name"],
                    "source": sensor["source"],
                    "gyro_noise_dps_sqrt_hz": sensor["gyro_noise_dps_sqrt_hz"],
                    "accel_noise_ug_sqrt_hz": sensor["accel_noise_ug_sqrt_hz"],
                    "gyro_lpf_hz": gyro_lpf,
                    "accel_lpf_hz": accel_lpf,
                    "gyro_enbw_hz": gyro_enbw,
                    "accel_enbw_hz": accel_enbw,
                    "sensor_gyro_rms_rad_s": gyro_rms,
                    "sensor_accel_rms_m_s2": accel_rms,
                    "configured_gyro_noise_rad_s": configured_gyro,
                    "configured_accel_noise_m_s2": configured_accel,
                    "configured_over_sensor_gyro": configured_gyro / gyro_rms if gyro_rms > 0.0 else float("nan"),
                    "configured_over_sensor_accel": configured_accel / accel_rms if accel_rms > 0.0 else float("nan"),
                    "gyro_equivalent_white_noise_bandwidth_hz": gyro_equiv_bw,
                    "accel_equivalent_white_noise_bandwidth_hz": accel_equiv_bw,
                    "gyro_equivalent_one_pole_cutoff_hz": gyro_equiv_bw / (math.pi / 2.0) if gyro_equiv_bw > 0.0 else float("nan"),
                    "accel_equivalent_one_pole_cutoff_hz": accel_equiv_bw / (math.pi / 2.0) if accel_equiv_bw > 0.0 else float("nan"),
                }
            )
    path = DATA / "imu_noise_reference_summary.csv"
    DATA.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        fieldnames = [
            "preset",
            "sensor",
            "source",
            "gyro_noise_dps_sqrt_hz",
            "accel_noise_ug_sqrt_hz",
            "gyro_lpf_hz",
            "accel_lpf_hz",
            "gyro_enbw_hz",
            "accel_enbw_hz",
            "sensor_gyro_rms_rad_s",
            "sensor_accel_rms_m_s2",
            "configured_gyro_noise_rad_s",
            "configured_accel_noise_m_s2",
            "configured_over_sensor_gyro",
            "configured_over_sensor_accel",
            "gyro_equivalent_white_noise_bandwidth_hz",
            "accel_equivalent_white_noise_bandwidth_hz",
            "gyro_equivalent_one_pole_cutoff_hz",
            "accel_equivalent_one_pole_cutoff_hz",
        ]
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    return rows


def dryden_low_altitude_reference(altitude_m: float, wind20_m_s: float) -> dict[str, float]:
    altitude_ft = altitude_m * 3.280839895
    wind20_ft_s = wind20_m_s * 3.280839895
    denom = 0.177 + 0.000823 * max(1.0, altitude_ft)
    lu_ft = altitude_ft / (denom**1.2)
    lv_ft = lu_ft
    lw_ft = altitude_ft
    sigma_w_ft_s = 0.1 * wind20_ft_s
    sigma_u_ft_s = sigma_w_ft_s / (denom**0.4)
    sigma_v_ft_s = sigma_u_ft_s
    return {
        "altitude_m": altitude_m,
        "wind20_m_s": wind20_m_s,
        "lu_m": lu_ft / 3.280839895,
        "lv_m": lv_ft / 3.280839895,
        "lw_m": lw_ft / 3.280839895,
        "sigma_u_m_s": sigma_u_ft_s / 3.280839895,
        "sigma_v_m_s": sigma_v_ft_s / 3.280839895,
        "sigma_w_m_s": sigma_w_ft_s / 3.280839895,
        "source": PYFLY_DRYDEN_URL,
    }


def current_gust_reference_row(dirty_air: float, wind_speed_m_s: float, altitude_m: float = 6.0) -> dict[str, float | str]:
    gust_scale = min(4.5, max(0.0, dirty_air * (0.32 + 0.070 * wind_speed_m_s)))
    burble_scale = 0.32
    burble_x_peak = 1.60 * gust_scale * burble_scale
    burble_z_peak = 1.57 * gust_scale * burble_scale
    burble_y_peak = 0.50 * gust_scale * burble_scale
    burble_x_rms = math.sqrt((1.0**2 + 0.42**2 + 0.18**2) / 2.0) * gust_scale * burble_scale
    burble_z_rms = math.sqrt((1.0**2 + 0.35**2 + 0.22**2) / 2.0) * gust_scale * burble_scale
    burble_y_rms = math.sqrt((0.34**2 + 0.16**2) / 2.0) * gust_scale * burble_scale
    phase_a = 1.35 + 0.16 * wind_speed_m_s + 1.25 * dirty_air
    phase_b = 1.95 + 0.11 * wind_speed_m_s + 0.95 * dirty_air
    phase_c = 0.85 + 0.09 * wind_speed_m_s + 1.55 * dirty_air
    gust_tau = min(0.260, max(0.055, 0.070 + 0.085 / (0.35 + dirty_air)))
    mean_tau = min(0.620, max(0.045, 0.055 + 0.018 * wind_speed_m_s + 0.140 * dirty_air))
    dryden = dryden_low_altitude_reference(altitude_m, max(wind_speed_m_s, 0.1))
    dryden_intensity_scale = min(1.0, max(0.0, dirty_air / 1.8))
    dryden_x_rms = dryden["sigma_u_m_s"] * dryden_intensity_scale
    dryden_z_rms = dryden["sigma_v_m_s"] * dryden_intensity_scale
    dryden_y_rms = dryden["sigma_w_m_s"] * dryden_intensity_scale
    x_rms = math.sqrt(burble_x_rms**2 + dryden_x_rms**2)
    z_rms = math.sqrt(burble_z_rms**2 + dryden_z_rms**2)
    y_rms = math.sqrt(burble_y_rms**2 + dryden_y_rms**2)
    x_peak = burble_x_peak + 2.0 * dryden_x_rms
    z_peak = burble_z_peak + 2.0 * dryden_z_rms
    y_peak = burble_y_peak + 2.0 * dryden_y_rms
    dryden_longitudinal_time_s = dryden["lu_m"] / max(0.1, wind_speed_m_s)
    dryden_vertical_time_s = dryden["lw_m"] / max(0.1, wind_speed_m_s)
    return {
        "dirty_air": dirty_air,
        "wind_speed_m_s": wind_speed_m_s,
        "altitude_m": altitude_m,
        "current_gust_scale": gust_scale,
        "current_burble_scale": burble_scale,
        "dryden_intensity_scale": dryden_intensity_scale,
        "current_gust_rms_x_m_s": x_rms,
        "current_gust_rms_y_m_s": y_rms,
        "current_gust_rms_z_m_s": z_rms,
        "current_burble_rms_x_m_s": burble_x_rms,
        "current_burble_rms_y_m_s": burble_y_rms,
        "current_burble_rms_z_m_s": burble_z_rms,
        "dryden_target_rms_x_m_s": dryden_x_rms,
        "dryden_target_rms_y_m_s": dryden_y_rms,
        "dryden_target_rms_z_m_s": dryden_z_rms,
        "current_gust_peak_x_m_s": x_peak,
        "current_gust_peak_y_m_s": y_peak,
        "current_gust_peak_z_m_s": z_peak,
        "phase_a_rad_s": phase_a,
        "phase_b_rad_s": phase_b,
        "phase_c_rad_s": phase_c,
        "phase_a_period_s": 2.0 * math.pi / phase_a,
        "phase_b_period_s": 2.0 * math.pi / phase_b,
        "phase_c_period_s": 2.0 * math.pi / phase_c,
        "gust_time_constant_s": gust_tau,
        "mean_wind_time_constant_s": mean_tau,
        "dryden_sigma_u_m_s": dryden["sigma_u_m_s"],
        "dryden_sigma_v_m_s": dryden["sigma_v_m_s"],
        "dryden_sigma_w_m_s": dryden["sigma_w_m_s"],
        "current_x_rms_over_dryden_u": x_rms / dryden["sigma_u_m_s"] if dryden["sigma_u_m_s"] > 0.0 else float("nan"),
        "current_y_rms_over_dryden_w": y_rms / dryden["sigma_w_m_s"] if dryden["sigma_w_m_s"] > 0.0 else float("nan"),
        "dryden_lu_m": dryden["lu_m"],
        "dryden_lw_m": dryden["lw_m"],
        "dryden_longitudinal_time_s": dryden_longitudinal_time_s,
        "dryden_vertical_time_s": dryden_vertical_time_s,
        "source": PYFLY_DRYDEN_URL,
    }


def summarize_wind_gust() -> list[dict[str, float | str]]:
    fetch_text(PYFLY_DRYDEN_URL)
    fetch_text(UAV_WIND_MODELING_URL)
    rows = []
    for wind_speed in (5.0, 10.0, 15.0):
        for dirty_air in (0.25, 0.75, 1.50, 1.80):
            rows.append(current_gust_reference_row(dirty_air, wind_speed))
    path = DATA / "wind_gust_dryden_reference.csv"
    DATA.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        fieldnames = list(rows[0].keys())
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    return rows


def smooth_step(edge0: float, edge1: float, value: float) -> float:
    if edge1 <= edge0:
        return 1.0 if value >= edge1 else 0.0
    x = min(1.0, max(0.0, (value - edge0) / (edge1 - edge0)))
    return x * x * (3.0 - 2.0 * x)


def pressure_pa_to_altitude_m(pressure_pa: float, rho: float = RHO) -> float:
    return pressure_pa / (rho * G)


def barometer_current_noise_metrics(preset: dict[str, float | str]) -> dict[str, float | str]:
    accel_noise = float(preset.get("accelerometer_noise_stddev_m_s2", float("nan")))
    base_amplitude = 0.035 * accel_noise
    sine_rms_scale = math.sqrt((1.0**2 + 0.35**2 + 0.18**2) / 2.0)
    return {
        "preset": preset["preset"],
        "accelerometer_noise_stddev_m_s2": accel_noise,
        "quiet_barometer_noise_amplitude_m": base_amplitude,
        "quiet_barometer_noise_rms_m": base_amplitude * sine_rms_scale,
        "barometer_altitude_tau_s": 0.090,
        "barometer_vertical_speed_tau_s": 0.180,
        "source": repo_path(ROOT / "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java"),
    }


def barometer_dynamic_pressure_error(airspeed_m_s: float, mode: str) -> float:
    dynamic_scale = smooth_step(4.0, 22.0, airspeed_m_s)
    if mode == "aligned":
        return -0.0026 * airspeed_m_s * airspeed_m_s * dynamic_scale
    if mode == "broadside_separated":
        return 0.0018 * airspeed_m_s * airspeed_m_s * dynamic_scale
    raise ValueError(mode)


def summarize_barometer(presets: list[dict[str, float | str]]) -> dict[str, object]:
    sensor_rows: list[dict[str, float | str]] = []
    for sensor in BAROMETER_SENSOR_REFERENCES:
        noise_pa = float(sensor["pressure_noise_pa"])
        accuracy_pa = float(sensor["relative_accuracy_pa"])
        sensor_rows.append(
            {
                "sensor": sensor["name"],
                "pressure_noise_pa": noise_pa,
                "pressure_noise_altitude_m": pressure_pa_to_altitude_m(noise_pa),
                "relative_accuracy_pa": accuracy_pa,
                "relative_accuracy_altitude_m": pressure_pa_to_altitude_m(accuracy_pa),
                "source": sensor["source"],
                "note": sensor["note"],
            }
        )
    preset_noise_rows = [barometer_current_noise_metrics(preset) for preset in presets]
    dynamic_rows: list[dict[str, float | str]] = []
    for speed in (5.0, 10.0, 20.0, 30.0):
        dynamic_head_m = speed * speed / (2.0 * G)
        for mode in ("aligned", "broadside_separated"):
            error_m = barometer_dynamic_pressure_error(speed, mode)
            dynamic_rows.append(
                {
                    "airspeed_m_s": speed,
                    "mode": mode,
                    "dynamic_pressure_head_m": dynamic_head_m,
                    "code_altitude_error_m": error_m,
                    "static_port_pressure_coefficient_equivalent": error_m / dynamic_head_m if dynamic_head_m > 0.0 else float("nan"),
                    "source": repo_path(ROOT / "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java"),
                }
            )
    path = DATA / "barometer_reference_summary.csv"
    DATA.mkdir(parents=True, exist_ok=True)
    rows_for_csv: list[dict[str, str | float]] = []
    for row in sensor_rows:
        rows_for_csv.append({"kind": "sensor", **row})
    for row in preset_noise_rows:
        rows_for_csv.append({"kind": "current_preset_noise", **row})
    for row in dynamic_rows:
        rows_for_csv.append({"kind": "current_dynamic_pressure", **row})
    fieldnames = sorted({key for row in rows_for_csv for key in row.keys()})
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows_for_csv)
    return {
        "summary_csv": repo_path(path),
        "sensor_rows": sensor_rows,
        "preset_noise_rows": preset_noise_rows,
        "dynamic_rows": dynamic_rows,
    }


def blackbox_header_value(headers: dict[str, str], key: str, default: float = float("nan")) -> float:
    raw = headers.get(key)
    if raw is None or raw == "":
        return default
    try:
        return float(raw.split(",", 1)[0])
    except ValueError:
        return default


def parse_blackbox_headers(text: str) -> dict[str, str]:
    headers: dict[str, str] = {}
    for line in text.splitlines():
        if not line.startswith("H ") or ":" not in line:
            continue
        key, value = line[2:].split(":", 1)
        headers[key.strip()] = value.strip()
    return headers


def blackbox_field_names(headers: dict[str, str]) -> list[str]:
    raw = headers.get("Field I name", "")
    return [field.strip() for field in raw.split(",") if field.strip()]


def summarize_blackbox_sources(timing_rows: list[dict[str, float | str]]) -> dict[str, object]:
    source_text = fetch_text(BETAFLIGHT_BLACKBOX_SOURCE_URL)
    erpm_logged_divisor = 100.0 if "eRPM / 100" in source_text else float("nan")
    header_rows: list[dict[str, float | str]] = []
    for source in BLACKBOX_LOG_SOURCES:
        text = fetch_text(source["url"])
        headers = parse_blackbox_headers(text)
        fields = blackbox_field_names(headers)
        looptime_us = blackbox_header_value(headers, "looptime")
        pid_denom = blackbox_header_value(headers, "pid_process_denom", 1.0)
        rate_num = blackbox_header_value(headers, "blackbox_rate_num", 1.0)
        rate_denom = blackbox_header_value(headers, "blackbox_rate_denom", 1.0)
        interval_us = looptime_us * pid_denom * rate_denom / rate_num if looptime_us > 0.0 and rate_num > 0.0 else float("nan")
        sample_hz = 1_000_000.0 / interval_us if interval_us > 0.0 else float("nan")
        header_rows.append(
            {
                "name": source["name"],
                "url": source["url"],
                "context": source["context"],
                "firmware_type": headers.get("Firmware type", ""),
                "firmware_revision": headers.get("Firmware revision", ""),
                "firmware_date": headers.get("Firmware date", ""),
                "field_count": len(fields),
                "has_time": int("time" in fields),
                "has_gyro_adc": int(any(field.startswith("gyroADC") for field in fields)),
                "has_acc_smooth": int(any(field.startswith("accSmooth") for field in fields)),
                "has_motor_command": int(any(field.startswith("motor[") for field in fields)),
                "has_erpm": int(any(field.startswith("eRPM") for field in fields)),
                "looptime_us": looptime_us,
                "pid_process_denom": pid_denom,
                "blackbox_rate_num": rate_num,
                "blackbox_rate_denom": rate_denom,
                "estimated_main_interval_us": interval_us,
                "estimated_main_sample_hz": sample_hz,
                "dshot_bidir": blackbox_header_value(headers, "dshot_bidir"),
                "gyro_rpm_notch_harmonics": blackbox_header_value(headers, "gyro_rpm_notch_harmonics"),
                "gyro_rpm_notch_min_hz": blackbox_header_value(headers, "gyro_rpm_notch_min"),
                "rpm_notch_lpf_hz": blackbox_header_value(headers, "rpm_notch_lpf"),
                "gyro_lowpass_hz": blackbox_header_value(headers, "gyro_lowpass_hz"),
                "gyro_lowpass_dyn_hz_low": blackbox_header_value(headers, "gyro_lowpass_dyn_hz"),
            }
        )

    motor_poles = 14.0
    mechanical_rpm_per_logged_erpm100 = erpm_logged_divisor * 2.0 / motor_poles
    erpm_rows: list[dict[str, float | str]] = []
    for row in timing_rows:
        hover_rpm = float(row["hover_rpm"])
        max_rpm = float(row["max_rpm"])
        hover_logged = hover_rpm * motor_poles / 2.0 / erpm_logged_divisor
        max_logged = max_rpm * motor_poles / 2.0 / erpm_logged_divisor
        erpm_rows.append(
            {
                "preset": row["preset"],
                "motor_poles": motor_poles,
                "erpm_logged_divisor": erpm_logged_divisor,
                "mechanical_rpm_per_logged_erpm100": mechanical_rpm_per_logged_erpm100,
                "hover_mechanical_rpm": hover_rpm,
                "max_mechanical_rpm": max_rpm,
                "hover_logged_erpm100": hover_logged,
                "max_logged_erpm100": max_logged,
                "hover_3blade_pass_hz": row["hover_3blade_pass_hz"],
                "max_3blade_pass_hz": row["max_3blade_pass_hz"],
                "source": BETAFLIGHT_BLACKBOX_SOURCE_URL,
            }
        )

    path = DATA / "blackbox_log_header_summary.csv"
    DATA.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        fieldnames = [
            "name",
            "url",
            "context",
            "firmware_type",
            "firmware_revision",
            "firmware_date",
            "field_count",
            "has_time",
            "has_gyro_adc",
            "has_acc_smooth",
            "has_motor_command",
            "has_erpm",
            "looptime_us",
            "pid_process_denom",
            "blackbox_rate_num",
            "blackbox_rate_denom",
            "estimated_main_interval_us",
            "estimated_main_sample_hz",
            "dshot_bidir",
            "gyro_rpm_notch_harmonics",
            "gyro_rpm_notch_min_hz",
            "rpm_notch_lpf_hz",
            "gyro_lowpass_hz",
            "gyro_lowpass_dyn_hz_low",
        ]
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(header_rows)
    return {
        "source": BETAFLIGHT_BLACKBOX_SOURCE_URL,
        "library": BLACKBOX_LIBRARY_URL,
        "summary_csv": repo_path(path),
        "header_rows": header_rows,
        "erpm_rows": erpm_rows,
        "erpm_logged_divisor": erpm_logged_divisor,
        "motor_poles": motor_poles,
        "mechanical_rpm_per_logged_erpm100": mechanical_rpm_per_logged_erpm100,
    }


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


def clamp(value: float, low: float, high: float) -> float:
    return min(high, max(low, value))


def battery_temperature_resistance_scale(battery_temperature_c: float, ambient_c: float = 25.0) -> float:
    electrical_temperature = 0.78 * battery_temperature_c + 0.22 * ambient_c
    cold_rise = max(0.0, 25.0 - electrical_temperature)
    heat_rise = max(0.0, electrical_temperature - 45.0)
    return clamp(1.0 + 0.024 * cold_rise + 0.0045 * heat_rise, 0.72, 2.85)


def battery_temperature_current_scale(battery_temperature_c: float) -> float:
    cold_loss = max(0.0, 25.0 - battery_temperature_c) * 0.011
    heat_loss = max(0.0, battery_temperature_c - 42.0) * 0.006
    return clamp(1.0 - cold_loss - heat_loss, 0.52, 1.0)


def battery_thermal_limit_scale(battery_temperature_c: float) -> float:
    if battery_temperature_c <= 58.0:
        return 1.0
    if battery_temperature_c >= 86.0:
        return 0.45
    t = (battery_temperature_c - 58.0) / 28.0
    smooth = t * t * (3.0 - 2.0 * t)
    return 1.0 - (1.0 - 0.45) * smooth


def summarize_battery_temperature_derating(presets: list[dict[str, float | str]]) -> list[dict[str, float | str]]:
    rows: list[dict[str, float | str]] = []
    racing = next((preset for preset in presets if preset["preset"] == "racingQuad"), presets[0])
    base_resistance = float(racing.get("battery_resistance_ohm", float("nan")))
    max_current = float(racing.get("max_battery_current_a", float("nan")))
    for temp_c in (-20.0, 0.0, 10.0, 25.0, 45.0, 58.0, 70.0, 86.0, 100.0):
        resistance_scale = battery_temperature_resistance_scale(temp_c)
        current_scale = battery_temperature_current_scale(temp_c)
        thermal_limit = battery_thermal_limit_scale(temp_c)
        effective_resistance = base_resistance * resistance_scale
        effective_current_limit = max_current * current_scale
        rows.append(
            {
                "preset": "racingQuad",
                "battery_temperature_c": temp_c,
                "ambient_temperature_c": 25.0,
                "resistance_scale": resistance_scale,
                "current_scale": current_scale,
                "thermal_power_limit": thermal_limit,
                "effective_resistance_ohm": effective_resistance,
                "effective_current_limit_a": effective_current_limit,
                "sag_at_nominal_limit_v": max_current * effective_resistance,
                "sag_at_temperature_scaled_limit_v": effective_current_limit * effective_resistance,
                "source": repo_path(ROOT / "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java"),
            }
        )
    path = DATA / "battery_temperature_derating_summary.csv"
    DATA.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        fieldnames = list(rows[0].keys())
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    return rows


def csv_matrix_from_url(url: str) -> list[list[float]]:
    text = fetch_text(url)
    rows: list[list[float]] = []
    for row in csv.reader(text.splitlines()):
        values: list[float] = []
        for value in row:
            try:
                number = float(value)
            except ValueError:
                continue
            if math.isfinite(number):
                values.append(number)
        if values:
            rows.append(values)
    return rows


def matrix_values(matrix: list[list[float]], positive: bool = False) -> list[float]:
    values = [value for row in matrix for value in row if math.isfinite(value)]
    if positive:
        values = [value for value in values if value > 0.0]
    return values


def matrix_stats(matrix: list[list[float]], positive: bool = False) -> tuple[float, float, float, int]:
    values = matrix_values(matrix, positive)
    if not values:
        return (float("nan"), float("nan"), float("nan"), 0)
    return (min(values), statistics.fmean(values), max(values), len(values))


def matrix_max_position(matrix: list[list[float]], positive: bool = False) -> tuple[int, int, float]:
    best_i = -1
    best_j = -1
    best_value = float("-inf")
    for i, row in enumerate(matrix):
        for j, value in enumerate(row):
            if positive and value <= 0.0:
                continue
            if math.isfinite(value) and value > best_value:
                best_i = i
                best_j = j
                best_value = value
    return best_i, best_j, best_value


def matrix_lookup(matrix: list[list[float]], i: int, j: int) -> float:
    if i < 0 or j < 0 or i >= len(matrix) or j >= len(matrix[i]):
        return float("nan")
    return matrix[i][j]


def copper_winding_resistance_scale(temperature_c: float) -> float:
    return clamp(1.0 + 0.0039 * (temperature_c - 25.0), 0.72, 1.90)


def thermal_limit_scale(temperature_c: float, limit_c: float, cutoff_c: float) -> float:
    if temperature_c <= limit_c:
        return 1.0
    if temperature_c >= cutoff_c:
        return 0.45
    t = (temperature_c - limit_c) / (cutoff_c - limit_c)
    smooth = t * t * (3.0 - 2.0 * t)
    return 1.0 - (1.0 - 0.45) * smooth


def current_motor_cooling_factor_proxy(power: float, esc_output: float, density_ratio: float = 1.0, airspeed_m_s: float = 0.0) -> float:
    freestream_cooling = clamp(airspeed_m_s / 18.0, 0.0, 1.8)
    rotor_wash = 0.92 * power * (0.45 + 0.55 * esc_output)
    return clamp((1.0 + freestream_cooling + rotor_wash) * density_ratio, 0.20, 4.0)


def current_esc_cooling_factor_proxy(motor_cooling_factor: float, power: float, esc_output: float, density_ratio: float = 1.0) -> float:
    rotor_wash = 0.45 * power * (0.35 + 0.65 * esc_output)
    board_airflow = 0.58 + 0.42 * motor_cooling_factor + rotor_wash
    return clamp(board_airflow * density_ratio, 0.20, 4.0)


def summarize_u8_dyno_thermal() -> list[dict[str, float | str]]:
    fetch_text(U8_DYNO_REPO_URL)
    rows: list[dict[str, float | str]] = []
    for voltage in (24, 36):
        url_base = U8_DYNO_RAW_BASE
        temp = csv_matrix_from_url(f"{url_base}max_termperature{voltage}.csv")
        loss = csv_matrix_from_url(f"{url_base}loss_map{voltage}.csv")
        motor_eff = csv_matrix_from_url(f"{url_base}motor_eff{voltage}.csv")
        driver_eff = csv_matrix_from_url(f"{url_base}driver_eff{voltage}.csv")
        rpm = csv_matrix_from_url(f"{url_base}avg_rpm{voltage}.csv")
        torque = csv_matrix_from_url(f"{url_base}avg_trqNm{voltage}.csv")
        current = csv_matrix_from_url(f"{url_base}curr_setpoints{voltage}.csv")
        temp_min, temp_mean, temp_max, temp_count = matrix_stats(temp, positive=True)
        loss_min, loss_mean, loss_max, loss_count = matrix_stats(loss, positive=True)
        motor_eff_min, motor_eff_mean, motor_eff_max, motor_eff_count = matrix_stats(motor_eff, positive=True)
        driver_eff_min, driver_eff_mean, driver_eff_max, driver_eff_count = matrix_stats(driver_eff, positive=True)
        rpm_min, rpm_mean, rpm_max, rpm_count = matrix_stats(rpm, positive=True)
        torque_min, torque_mean, torque_max, torque_count = matrix_stats(torque, positive=True)
        current_min, current_mean, current_max, current_count = matrix_stats(current, positive=True)
        max_i, max_j, max_temperature = matrix_max_position(temp, positive=True)
        rows.append(
            {
                "row_type": "u8_dyno_processed",
                "name": f"U8_Kv100_{voltage}V",
                "source": U8_DYNO_REPO_URL,
                "voltage_v": voltage,
                "temperature_min_c": temp_min,
                "temperature_mean_c": temp_mean,
                "temperature_max_c": temp_max,
                "temperature_sample_count": temp_count,
                "loss_min_w": loss_min,
                "loss_mean_w": loss_mean,
                "loss_max_w": loss_max,
                "loss_sample_count": loss_count,
                "motor_efficiency_min": motor_eff_min,
                "motor_efficiency_mean": motor_eff_mean,
                "motor_efficiency_max": motor_eff_max,
                "motor_efficiency_sample_count": motor_eff_count,
                "driver_efficiency_min": driver_eff_min,
                "driver_efficiency_mean": driver_eff_mean,
                "driver_efficiency_max": driver_eff_max,
                "driver_efficiency_sample_count": driver_eff_count,
                "rpm_min": rpm_min,
                "rpm_mean": rpm_mean,
                "rpm_max": rpm_max,
                "rpm_sample_count": rpm_count,
                "torque_min_nm": torque_min,
                "torque_mean_nm": torque_mean,
                "torque_max_nm": torque_max,
                "torque_sample_count": torque_count,
                "current_min_a": current_min,
                "current_mean_a": current_mean,
                "current_max_a": current_max,
                "current_sample_count": current_count,
                "max_temp_current_a": matrix_lookup(current, max_i, max_j),
                "max_temp_rpm": matrix_lookup(rpm, max_i, max_j),
                "max_temp_torque_nm": matrix_lookup(torque, max_i, max_j),
                "note": "Processed U8/Kv100 dyno maps; max_termperature filename spelling follows upstream repository.",
            }
        )
    return rows


def summarize_current_motor_esc_thermal(presets: list[dict[str, float | str]]) -> list[dict[str, float | str]]:
    rows: list[dict[str, float | str]] = []
    for preset in presets:
        rise = float(preset.get("motor_thermal_rise_c_s", float("nan")))
        cooling = float(preset.get("motor_cooling_rate_s", float("nan")))
        limit = float(preset.get("motor_thermal_limit_c", float("nan")))
        cutoff = float(preset.get("motor_thermal_cutoff_c", float("nan")))
        hover = float(preset.get("hover_throttle_linear", float("nan")))
        rotor_count = max(1, int(preset.get("rotor_count", 1)))
        per_motor_max_current = float(preset.get("max_battery_current_a", float("nan"))) / rotor_count
        stall_current = max(1.0, per_motor_max_current * 3.20)
        inferred_winding_resistance = clamp(float(preset.get("battery_nominal_v", float("nan"))) / stall_current, 0.025, 2.5)
        hover_motor_cooling = current_motor_cooling_factor_proxy(hover, hover)
        full_motor_cooling = current_motor_cooling_factor_proxy(1.0, 1.0)
        full_airspeed_motor_cooling = current_motor_cooling_factor_proxy(1.0, 1.0, airspeed_m_s=10.0)
        full_esc_cooling = current_esc_cooling_factor_proxy(full_motor_cooling, 1.0, 1.0)
        hover_esc_cooling = current_esc_cooling_factor_proxy(hover_motor_cooling, hover, hover)
        esc_limit = limit - 5.0
        esc_cutoff = cutoff - 5.0
        rows.append(
            {
                "row_type": "current_preset_thermal",
                "name": preset["preset"],
                "source": repo_path(ROOT / "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneConfig.java"),
                "thermal_rise_c_s": rise,
                "cooling_rate_s": cooling,
                "motor_limit_c": limit,
                "motor_cutoff_c": cutoff,
                "esc_limit_c": esc_limit,
                "esc_cutoff_c": esc_cutoff,
                "min_thermal_thrust_limit": 0.45,
                "hover_power_proxy": hover,
                "hover_motor_cooling_factor_proxy": hover_motor_cooling,
                "full_motor_cooling_factor_proxy": full_motor_cooling,
                "full_10m_s_motor_cooling_factor_proxy": full_airspeed_motor_cooling,
                "hover_esc_cooling_factor_proxy": hover_esc_cooling,
                "full_esc_cooling_factor_proxy": full_esc_cooling,
                "motor_base_time_constant_s": 1.0 / cooling if cooling > 0.0 else float("nan"),
                "motor_full_wash_time_constant_s": 1.0 / (cooling * full_motor_cooling) if cooling > 0.0 else float("nan"),
                "motor_full_10m_s_time_constant_s": 1.0 / (cooling * full_airspeed_motor_cooling) if cooling > 0.0 else float("nan"),
                "motor_full_steady_rise_c": rise / (cooling * full_motor_cooling) if cooling > 0.0 else float("nan"),
                "motor_hover_steady_rise_proxy_c": rise * hover * hover / (cooling * hover_motor_cooling) if cooling > 0.0 else float("nan"),
                "esc_full_current_steady_rise_proxy_c": (rise * 0.72 * 0.62) / (cooling * 0.90 * full_esc_cooling) if cooling > 0.0 else float("nan"),
                "inferred_winding_resistance_25c_ohm": inferred_winding_resistance,
                "winding_resistance_scale_at_limit": copper_winding_resistance_scale(limit),
                "winding_resistance_scale_at_cutoff": copper_winding_resistance_scale(cutoff),
                "motor_limit_scale_at_midpoint": thermal_limit_scale((limit + cutoff) * 0.5, limit, cutoff),
                "esc_limit_scale_at_midpoint": thermal_limit_scale((esc_limit + esc_cutoff) * 0.5, esc_limit, esc_cutoff),
                "note": "Steady-rise values are current-code proxies with no obstruction/recirculation and sea-level density.",
            }
        )
    return rows


def summarize_copper_resistance_temperature() -> list[dict[str, float | str]]:
    rows: list[dict[str, float | str]] = []
    for temperature in (0.0, 25.0, 60.0, 95.0, 125.0, 150.0, 180.0, 220.0, 260.0):
        rows.append(
            {
                "row_type": "copper_resistance_scale",
                "name": f"copper_{fmt(temperature, 0)}C",
                "source": COPPER_TEMP_COEFF_SOURCE_URL,
                "temperature_c": temperature,
                "resistance_scale_vs_25c": copper_winding_resistance_scale(temperature),
                "note": "Uses current model coefficient 0.0039 per C for copper winding resistance.",
            }
        )
    return rows


def summarize_motor_esc_thermal(presets: list[dict[str, float | str]]) -> dict[str, object]:
    u8_rows = summarize_u8_dyno_thermal()
    current_rows = summarize_current_motor_esc_thermal(presets)
    copper_rows = summarize_copper_resistance_temperature()
    rows = [*u8_rows, *current_rows, *copper_rows]
    path = DATA / "motor_esc_thermal_reference.csv"
    DATA.mkdir(parents=True, exist_ok=True)
    fieldnames: list[str] = []
    for row in rows:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
    return {
        "summary_csv": repo_path(path),
        "u8_rows": u8_rows,
        "current_rows": current_rows,
        "copper_rows": copper_rows,
        "source": U8_DYNO_REPO_URL,
    }


def summarize_mendeley_ecm() -> dict[str, object]:
    if not MENDELEY_ECM_SUMMARY_CSV.exists():
        return {"available": False, "pack_rows": []}
    rows: list[dict[str, float | str]] = []
    with MENDELEY_ECM_SUMMARY_CSV.open(encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            parsed: dict[str, float | str] = {"pack": row["pack"]}
            for key, value in row.items():
                if key in ("pack", "source_file"):
                    parsed[key] = value
                else:
                    parsed[key] = float(value)
            rows.append(parsed)
    pack_rows = []
    for pack in sorted({str(row["pack"]) for row in rows}):
        pack_samples = sorted((row for row in rows if row["pack"] == pack), key=lambda row: float(row["cycle"]))
        first = pack_samples[0]
        last = pack_samples[-1]
        pack_rows.append(
            {
                "pack": pack,
                "file_count": len(pack_samples),
                "first_cycle": first["cycle"],
                "last_cycle": last["cycle"],
                "first_r0_mean_ohm": first["r0_mean_ohm"],
                "last_r0_mean_ohm": last["r0_mean_ohm"],
                "cycle_growth_ratio": float(last["r0_mean_ohm"]) / float(first["r0_mean_ohm"]),
            }
        )
    def values(metric: str) -> list[float]:
        return [float(row[metric]) for row in rows]
    return {
        "available": True,
        "source": MENDELEY_LIPO_DATASET_URL,
        "summary_csv": repo_path(MENDELEY_ECM_SUMMARY_CSV),
        "row_count": len(rows),
        "pack_count": len(pack_rows),
        "r0_mean_min_ohm": min(values("r0_mean_ohm")),
        "r0_mean_avg_ohm": statistics.fmean(values("r0_mean_ohm")),
        "r0_mean_max_ohm": max(values("r0_mean_ohm")),
        "low_soc_over_high_soc_avg": statistics.fmean(values("r0_low_soc_over_high_soc")),
        "low_soc_over_high_soc_min": min(values("r0_low_soc_over_high_soc")),
        "low_soc_over_high_soc_max": max(values("r0_low_soc_over_high_soc")),
        "pack_rows": pack_rows,
    }


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
    battery_temp_rows: list[dict[str, float | str]],
    motor_thermal_summary: dict[str, object],
    coaxial_rows: list[dict[str, float | str]],
    motor_response_rows: list[dict[str, float | str]],
    inertia_rows: list[dict[str, float | str]],
    drag_rows: list[dict[str, float | str]],
    timing_rows: list[dict[str, float | str]],
    imu_rows: list[dict[str, float | str]],
    wind_rows: list[dict[str, float | str]],
    atmosphere_rows: list[dict[str, float | str]],
    barometer_summary: dict[str, object],
    blackbox_summary: dict[str, object],
    mendeley_ecm: dict[str, object],
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
            ("inertia_x_kg_m2", "kg*m^2"),
            ("inertia_y_kg_m2", "kg*m^2"),
            ("inertia_z_kg_m2", "kg*m^2"),
            ("body_drag_x", "N/(m/s)^2"),
            ("body_drag_y", "N/(m/s)^2"),
            ("body_drag_z", "N/(m/s)^2"),
        ):
            if metric in item:
                rows.append({"category": "open_source_model", "name": item["name"], "metric": metric, "value": item[metric], "unit": unit, "source": item["url"]})
    for item in presets:
        for metric in ("max_rpm_from_k", "tip_mach_at_max", "thrust_to_weight", "hover_throttle_linear", "hover_disk_loading_n_m2", "hover_induced_velocity_m_s"):
            rows.append({"category": "current_preset", "name": item["preset"], "metric": metric, "value": item[metric], "unit": "", "source": repo_path(DRONE_CONFIG)})
    for item in comparisons:
        rows.append({"category": "comparison", "name": item["name"], "metric": item["metric"], "value": item["value"], "unit": item["unit"], "source": item["source"]})
    for item in zju_ground:
        rows.append({"category": "zju_ground_effect", "name": item["name"], "metric": "k_n_per_rad2", "value": item["k_n_per_rad2"], "unit": "N/(rad/s)^2", "source": item["url"]})
        rows.append({"category": "zju_ground_effect", "name": item["name"], "metric": "qt_m", "value": item["qt_m"], "unit": "m", "source": item["url"]})
    for item in ground_rows:
        rows.append({"category": "ground_effect_shape", "name": item["preset"], "metric": f"current_multiplier_at_{item['h_over_r']}_R", "value": item["current_multiplier"], "unit": "x", "source": repo_path(DRONE_CONFIG)})
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
    for item in battery_temp_rows:
        name = f"{item['preset']}_{fmt(float(item['battery_temperature_c']), 0)}C"
        for metric, unit in (
            ("resistance_scale", "x"),
            ("current_scale", "x"),
            ("thermal_power_limit", "x"),
            ("effective_resistance_ohm", "ohm"),
            ("effective_current_limit_a", "A"),
            ("sag_at_nominal_limit_v", "V"),
            ("sag_at_temperature_scaled_limit_v", "V"),
        ):
            rows.append({"category": "battery_temperature_derating", "name": name, "metric": metric, "value": item[metric], "unit": unit, "source": item["source"]})
    for item in motor_thermal_summary.get("u8_rows", []):
        u8_row = item  # type: ignore[assignment]
        for metric, unit in (
            ("temperature_min_c", "C"),
            ("temperature_mean_c", "C"),
            ("temperature_max_c", "C"),
            ("loss_mean_w", "W"),
            ("loss_max_w", "W"),
            ("motor_efficiency_max", "x"),
            ("driver_efficiency_max", "x"),
            ("rpm_max", "rpm"),
            ("torque_max_nm", "N*m"),
            ("current_max_a", "A"),
            ("max_temp_current_a", "A"),
            ("max_temp_rpm", "rpm"),
            ("max_temp_torque_nm", "N*m"),
        ):
            rows.append({"category": "motor_esc_thermal_u8_dyno", "name": u8_row["name"], "metric": metric, "value": u8_row[metric], "unit": unit, "source": u8_row["source"]})
    for item in motor_thermal_summary.get("current_rows", []):
        thermal_row = item  # type: ignore[assignment]
        for metric, unit in (
            ("thermal_rise_c_s", "C/s"),
            ("cooling_rate_s", "1/s"),
            ("motor_limit_c", "C"),
            ("motor_cutoff_c", "C"),
            ("esc_limit_c", "C"),
            ("esc_cutoff_c", "C"),
            ("motor_base_time_constant_s", "s"),
            ("motor_full_wash_time_constant_s", "s"),
            ("motor_full_steady_rise_c", "C"),
            ("motor_hover_steady_rise_proxy_c", "C"),
            ("esc_full_current_steady_rise_proxy_c", "C"),
            ("inferred_winding_resistance_25c_ohm", "ohm"),
            ("winding_resistance_scale_at_limit", "x"),
            ("winding_resistance_scale_at_cutoff", "x"),
        ):
            rows.append({"category": "motor_esc_thermal_current", "name": thermal_row["name"], "metric": metric, "value": thermal_row[metric], "unit": unit, "source": thermal_row["source"]})
    for item in motor_thermal_summary.get("copper_rows", []):
        copper_row = item  # type: ignore[assignment]
        rows.append({"category": "motor_copper_resistance_temperature", "name": copper_row["name"], "metric": "resistance_scale_vs_25c", "value": copper_row["resistance_scale_vs_25c"], "unit": "x", "source": copper_row["source"]})
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
    for item in inertia_rows:
        for metric, unit in (
            ("rg_x_m", "m"),
            ("rg_y_m", "m"),
            ("rg_z_m", "m"),
            ("yaw_to_roll_pitch_inertia_ratio", "ratio"),
        ):
            rows.append({"category": "inertia_geometry", "name": item["name"], "metric": metric, "value": item[metric], "unit": unit, "source": item["source"]})
    for item in drag_rows:
        for metric, unit in (
            ("axis_x_drag_force_n", "N"),
            ("axis_y_drag_force_n", "N"),
            ("axis_z_drag_force_n", "N"),
            ("axis_x_drag_over_weight", "weight"),
            ("axis_z_drag_over_weight", "weight"),
            ("axis_x_equiv_cda_m2", "m^2"),
            ("axis_z_equiv_cda_m2", "m^2"),
        ):
            rows.append(
                {
                    "category": "body_drag_sanity",
                    "name": item["name"],
                    "metric": f"{metric}_at_{fmt(float(item['speed_m_s']), 0)}m_s",
                    "value": item[metric],
                    "unit": unit,
                    "source": item["source"],
                }
            )
    for item in timing_rows:
        for metric, unit in (
            ("configured_blade_count", "blades"),
            ("hover_rpm", "rpm"),
            ("max_rpm", "rpm"),
            ("hover_blade_pass_hz", "Hz"),
            ("max_blade_pass_hz", "Hz"),
            ("hover_3blade_pass_hz", "Hz"),
            ("max_3blade_pass_hz", "Hz"),
            ("blade_pass_3blade_to_configured_ratio", "x"),
            ("gyro_low_pass_hz", "Hz"),
            ("gyro_lpf_over_hover_blade_pass", "ratio"),
            ("gyro_lpf_over_hover_3blade_pass", "ratio"),
            ("rc_frame_rate_hz", "Hz"),
            ("rc_frame_interval_ms", "ms"),
            ("esc_command_frame_rate_hz", "Hz"),
            ("esc_command_frame_interval_ms", "ms"),
            ("control_latency_ms", "ms"),
            ("rc_command_latency_ms", "ms"),
            ("rc_smoothing_tau_ms", "ms"),
        ):
            rows.append({"category": "timing_vibration", "name": item["preset"], "metric": metric, "value": item[metric], "unit": unit, "source": item["source"]})
    for item in imu_rows:
        for metric, unit in (
            ("sensor_gyro_rms_rad_s", "rad/s"),
            ("sensor_accel_rms_m_s2", "m/s^2"),
            ("configured_gyro_noise_rad_s", "rad/s"),
            ("configured_accel_noise_m_s2", "m/s^2"),
            ("configured_over_sensor_gyro", "x"),
            ("configured_over_sensor_accel", "x"),
            ("gyro_equivalent_one_pole_cutoff_hz", "Hz"),
            ("accel_equivalent_one_pole_cutoff_hz", "Hz"),
        ):
            rows.append({"category": "imu_noise_reference", "name": f"{item['preset']} {item['sensor']}", "metric": metric, "value": item[metric], "unit": unit, "source": item["source"]})
    for item in wind_rows:
        name = f"dirty_{fmt(float(item['dirty_air']), 2)}_wind_{fmt(float(item['wind_speed_m_s']), 0)}m_s"
        for metric, unit in (
            ("current_gust_rms_x_m_s", "m/s"),
            ("current_gust_rms_y_m_s", "m/s"),
            ("current_gust_peak_x_m_s", "m/s"),
            ("phase_a_period_s", "s"),
            ("gust_time_constant_s", "s"),
            ("mean_wind_time_constant_s", "s"),
            ("dryden_sigma_u_m_s", "m/s"),
            ("dryden_sigma_w_m_s", "m/s"),
            ("current_x_rms_over_dryden_u", "x"),
            ("current_y_rms_over_dryden_w", "x"),
            ("dryden_longitudinal_time_s", "s"),
            ("dryden_vertical_time_s", "s"),
        ):
            rows.append({"category": "wind_gust_dryden", "name": name, "metric": metric, "value": item[metric], "unit": unit, "source": item["source"]})
    for item in atmosphere_rows:
        name = f"{item['preset']}_{item['scenario']}"
        for metric, unit in (
            ("pressure_ratio", "x"),
            ("density_ratio", "x"),
            ("speed_of_sound_m_s", "m/s"),
            ("dynamic_viscosity_pa_s", "Pa*s"),
            ("viscosity_ratio_25c", "x"),
            ("same_thrust_rpm_scale", "x"),
            ("hover_tip_mach", "Mach"),
            ("max_tip_mach", "Mach"),
            ("hover_reynolds_75r_proxy", "Re"),
            ("max_reynolds_75r_proxy", "Re"),
            ("hover_code_reynolds_index", "index"),
            ("max_code_reynolds_index", "index"),
            ("hover_low_reynolds_loss_proxy", "x"),
            ("max_low_reynolds_loss_proxy", "x"),
            ("max_compressibility_thrust_scale", "x"),
        ):
            rows.append({"category": "atmosphere_reynolds_mach", "name": name, "metric": metric, "value": item[metric], "unit": unit, "source": item["source"]})
    for item in barometer_summary.get("sensor_rows", []):
        sensor_row = item  # type: ignore[assignment]
        for metric, unit in (
            ("pressure_noise_pa", "Pa"),
            ("pressure_noise_altitude_m", "m"),
            ("relative_accuracy_pa", "Pa"),
            ("relative_accuracy_altitude_m", "m"),
        ):
            rows.append({"category": "barometer_sensor", "name": sensor_row["sensor"], "metric": metric, "value": sensor_row[metric], "unit": unit, "source": sensor_row["source"]})
    for item in barometer_summary.get("preset_noise_rows", []):
        noise_row = item  # type: ignore[assignment]
        for metric, unit in (
            ("quiet_barometer_noise_amplitude_m", "m"),
            ("quiet_barometer_noise_rms_m", "m"),
            ("barometer_altitude_tau_s", "s"),
            ("barometer_vertical_speed_tau_s", "s"),
        ):
            rows.append({"category": "barometer_current_noise", "name": noise_row["preset"], "metric": metric, "value": noise_row[metric], "unit": unit, "source": noise_row["source"]})
    for item in barometer_summary.get("dynamic_rows", []):
        dyn_row = item  # type: ignore[assignment]
        name = f"{dyn_row['mode']}_{fmt(float(dyn_row['airspeed_m_s']), 0)}m_s"
        for metric, unit in (
            ("dynamic_pressure_head_m", "m"),
            ("code_altitude_error_m", "m"),
            ("static_port_pressure_coefficient_equivalent", "Cp"),
        ):
            rows.append({"category": "barometer_dynamic_pressure", "name": name, "metric": metric, "value": dyn_row[metric], "unit": unit, "source": dyn_row["source"]})
    for item in blackbox_summary.get("header_rows", []):
        header_row = item  # type: ignore[assignment]
        for metric, unit in (
            ("field_count", "fields"),
            ("has_gyro_adc", "bool"),
            ("has_motor_command", "bool"),
            ("has_erpm", "bool"),
            ("estimated_main_sample_hz", "Hz"),
            ("dshot_bidir", "bool"),
            ("gyro_rpm_notch_harmonics", "harmonics"),
            ("rpm_notch_lpf_hz", "Hz"),
        ):
            rows.append({"category": "blackbox_log_header", "name": header_row["name"], "metric": metric, "value": header_row[metric], "unit": unit, "source": header_row["url"]})
    for item in blackbox_summary.get("erpm_rows", []):
        erpm_row = item  # type: ignore[assignment]
        for metric, unit in (
            ("mechanical_rpm_per_logged_erpm100", "rpm/count"),
            ("hover_logged_erpm100", "logged eRPM/100"),
            ("max_logged_erpm100", "logged eRPM/100"),
            ("hover_3blade_pass_hz", "Hz"),
            ("max_3blade_pass_hz", "Hz"),
        ):
            rows.append({"category": "blackbox_erpm_conversion", "name": erpm_row["preset"], "metric": metric, "value": erpm_row[metric], "unit": unit, "source": erpm_row["source"]})
    if mendeley_ecm.get("available"):
        for metric, unit in (
            ("row_count", "files"),
            ("pack_count", "packs"),
            ("r0_mean_min_ohm", "ohm"),
            ("r0_mean_avg_ohm", "ohm"),
            ("r0_mean_max_ohm", "ohm"),
            ("low_soc_over_high_soc_min", "x"),
            ("low_soc_over_high_soc_avg", "x"),
            ("low_soc_over_high_soc_max", "x"),
        ):
            rows.append({"category": "mendeley_lipo_ecm", "name": "LP-503562-IS-3", "metric": metric, "value": mendeley_ecm[metric], "unit": unit, "source": mendeley_ecm["source"]})
        for item in mendeley_ecm.get("pack_rows", []):
            pack_row = item  # type: ignore[assignment]
            for metric, unit in (
                ("first_r0_mean_ohm", "ohm"),
                ("last_r0_mean_ohm", "ohm"),
                ("cycle_growth_ratio", "x"),
            ):
                rows.append({"category": "mendeley_lipo_ecm", "name": pack_row["pack"], "metric": metric, "value": pack_row[metric], "unit": unit, "source": mendeley_ecm["source"]})
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
    battery_temp_rows: list[dict[str, float | str]],
    motor_thermal_summary: dict[str, object],
    coaxial_rows: list[dict[str, float | str]],
    motor_response_rows: list[dict[str, float | str]],
    inertia_rows: list[dict[str, float | str]],
    drag_rows: list[dict[str, float | str]],
    timing_rows: list[dict[str, float | str]],
    imu_rows: list[dict[str, float | str]],
    wind_rows: list[dict[str, float | str]],
    atmosphere_rows: list[dict[str, float | str]],
    barometer_summary: dict[str, object],
    blackbox_summary: dict[str, object],
    mendeley_ecm: dict[str, object],
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
    racing_inertia = next(row for row in inertia_rows if row["name"] == "racingQuad")
    racing_drag_10 = next(row for row in drag_rows if row["name"] == "racingQuad" and float(row["speed_m_s"]) == 10.0)
    rotorpy_drag_10 = next((row for row in drag_rows if row["name"] == "RotorPy Hummingbird" and float(row["speed_m_s"]) == 10.0), None)
    racing_timing = next(row for row in timing_rows if row["preset"] == "racingQuad")
    racing_imu_mpu6000 = next(row for row in imu_rows if row["preset"] == "racingQuad" and row["sensor"] == "MPU-6000/MPU-6050")
    racing_imu_icm42688 = next(row for row in imu_rows if row["preset"] == "racingQuad" and row["sensor"] == "ICM-42688-P")
    wind_case = next(row for row in wind_rows if float(row["dirty_air"]) == 1.5 and float(row["wind_speed_m_s"]) == 10.0)
    racing_atmos_cold = next(row for row in atmosphere_rows if row["preset"] == "racingQuad" and row["scenario"] == "cold_sea_level_-10c")
    racing_atmos_hot_high = next(row for row in atmosphere_rows if row["preset"] == "racingQuad" and row["scenario"] == "mountain_3000m_hot_30c")
    cine_atmos_hot_high = next(row for row in atmosphere_rows if row["preset"] == "cinewhoop" and row["scenario"] == "mountain_3000m_hot_30c")
    racing_baro_noise = next(row for row in barometer_summary.get("preset_noise_rows", []) if row["preset"] == "racingQuad")  # type: ignore[index]
    baro_aligned_20 = next(row for row in barometer_summary.get("dynamic_rows", []) if row["mode"] == "aligned" and float(row["airspeed_m_s"]) == 20.0)  # type: ignore[index]
    baro_best_sensor = min(barometer_summary.get("sensor_rows", []), key=lambda row: float(row["pressure_noise_altitude_m"]))  # type: ignore[arg-type]
    battery_0c = next(row for row in battery_temp_rows if float(row["battery_temperature_c"]) == 0.0)
    battery_70c = next(row for row in battery_temp_rows if float(row["battery_temperature_c"]) == 70.0)
    u8_36v_thermal = next(row for row in motor_thermal_summary.get("u8_rows", []) if row["name"] == "U8_Kv100_36V")  # type: ignore[index]
    racing_thermal = next(row for row in motor_thermal_summary.get("current_rows", []) if row["name"] == "racingQuad")  # type: ignore[index]
    copper_125c = next(row for row in motor_thermal_summary.get("copper_rows", []) if float(row["temperature_c"]) == 125.0)  # type: ignore[index]
    copper_180c = next(row for row in motor_thermal_summary.get("copper_rows", []) if float(row["temperature_c"]) == 180.0)  # type: ignore[index]
    racing_erpm = next(row for row in blackbox_summary.get("erpm_rows", []) if row["preset"] == "racingQuad")  # type: ignore[index]
    betaflight_log = next((row for row in blackbox_summary.get("header_rows", []) if row["name"] == "Betaflight issue LOG00078"), None)  # type: ignore[index]
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
    lines.append(f"- In cold sea-level air (`-10 C`), the same `racingQuad` max RPM reaches tip Mach `{fmt(float(racing_atmos_cold['max_tip_mach']), 2)}` because speed of sound falls to `{fmt(float(racing_atmos_cold['speed_of_sound_m_s']))}` m/s. In a hot 3000 m case (`30 C`), standard-atmosphere density ratio is `{fmt(float(racing_atmos_hot_high['density_ratio']), 3)}`, so the same-thrust RPM scale is `{fmt(float(racing_atmos_hot_high['same_thrust_rpm_scale']), 3)}x` before battery/motor limits.")
    lines.append(f"- The low-Reynolds proxy is currently a small-prop feature: `racingQuad` max low-Re loss remains `{fmt(float(racing_atmos_hot_high['max_low_reynolds_loss_proxy']), 3)}` in the hot 3000 m case because the 5-inch radius gates it out, while `cinewhoop` reaches `{fmt(float(cine_atmos_hot_high['max_low_reynolds_loss_proxy']), 3)}` max-loss proxy under the same density/temperature.")
    lines.append(f"- For the HQ v1s 5x4x3 bench rows, fitted motor current is approximately `I = {fmt(battery['current_a'])} * T^{fmt(battery['current_b'])}` where `T` is per-motor thrust in newtons. This estimates racing hover current near `{fmt(battery['hover_total_current_a'])}` A for four motors before avionics.")
    lines.append(f"- At the current `racingQuad` battery resistance of 0.018 ohm, the fitted hover current implies about `{fmt(battery['hover_sag_v'])}` V pack sag. The 90 A current limit implies `{fmt(battery['limit_per_motor_thrust_n'])}` N per rotor on the fitted HQ prop curve, below the configured `{fmt(float(racing['max_thrust_n']))}` N per rotor.")
    lines.append(f"- The ZJU ground-effect/motor-calibration source reports single-rotor `k_T = {fmt(float(zju_single['k_t_n_per_rpm2']))}` N/rpm^2, which converts to `{fmt(float(zju_single['k_n_per_rad2']))}` N/(rad/s)^2, with `Q/T = {fmt(float(zju_single['qt_m']), 4)}` m. The `Q/T` value is close to this project's 5-inch yaw torque order of magnitude.")
    lines.append(f"- For `racingQuad`, hover induced velocity is `{fmt(float(racing_vrs['hover_induced_velocity_m_s']))}` m/s. The code's VRS intensity band covers roughly `{fmt(float(racing_vrs['current_vrs_entry_m_s']))}-{fmt(float(racing_vrs['current_vrs_exit_end_m_s']))}` m/s descent, while the Cambridge dual-rotor paper reports strongest loss around `1.2-1.3 vi` (`{fmt(float(racing_vrs['paper_peak_loss_low_m_s']))}-{fmt(float(racing_vrs['paper_peak_loss_high_m_s']))}` m/s for this preset).")
    lines.append(f"- `racingQuad` battery IR is `{fmt(float(racing_battery_ir['per_cell_resistance_mohm']))}` mOhm/cell by the inferred `{int(racing_battery_ir['estimated_cells'])}S` pack. That sits in the high-C LiPo plausibility range, but max-current sag still reaches `{fmt(float(racing_battery_ir['current_limit_pack_sag_v']))}` V at the configured limit.")
    lines.append(f"- The current battery temperature model raises `racingQuad` pack resistance to `{fmt(float(battery_0c['resistance_scale']), 2)}x` and cuts max-current scale to `{fmt(float(battery_0c['current_scale']), 2)}x` at 0 C. At 70 C, resistance is `{fmt(float(battery_70c['resistance_scale']), 2)}x`, current scale `{fmt(float(battery_70c['current_scale']), 2)}x`, and thermal power limit `{fmt(float(battery_70c['thermal_power_limit']), 2)}x`; this matches the qualitative Li-ion/LiPo pattern that cold mainly hurts power delivery and heat drives protection.")
    if mendeley_ecm.get("available"):
        lines.append(f"- The extracted Mendeley LP-503562-IS-3 ECM fit summary has `{int(float(mendeley_ecm['row_count']))}` fitted-cycle files across `{int(float(mendeley_ecm['pack_count']))}` packs. Mean fitted `RO` spans `{fmt(float(mendeley_ecm['r0_mean_min_ohm']) * 1000.0)}-{fmt(float(mendeley_ecm['r0_mean_max_ohm']) * 1000.0)}` mOhm/cell, and low-SOC `RO` averages `{fmt(float(mendeley_ecm['low_soc_over_high_soc_avg']), 3)}x` high-SOC `RO`; use this for SOC/SOH shape, not FPV high-C absolute ESR.")
    lines.append(f"- `racingQuad.motor_tau` is `{fmt(float(racing_motor_response['motor_tau_s']), 4)}` s, about `{fmt(float(racing_motor_response['motor_tau_vs_ref_up']), 2)}x` RotorS/PX4 `timeConstantUp` and `{fmt(float(racing_motor_response['motor_tau_vs_ref_down']), 2)}x` `timeConstantDown`. That is defensible if it includes ESC/load/voltage effects, but it is slower than the simple open-source actuator lag reference.")
    lines.append(f"- The U8/Kv100 open dyno processed maps reach `{fmt(float(u8_36v_thermal['temperature_max_c']))}` C max reported temperature and `{fmt(float(u8_36v_thermal['loss_max_w']))}` W max loss in the 36 V map. Current `racingQuad` starts motor limiting at `{fmt(float(racing_thermal['motor_limit_c']), 0)}` C and cuts to the minimum thermal scale by `{fmt(float(racing_thermal['motor_cutoff_c']), 0)}` C; its full-power no-airspeed proxy steady motor rise is `{fmt(float(racing_thermal['motor_full_steady_rise_c']))}` C above ambient, so sustained full throttle should thermally limit.")
    lines.append(f"- The current copper winding coefficient `0.0039 / C` gives resistance scales of `{fmt(float(copper_125c['resistance_scale_vs_25c']), 2)}x` at 125 C and `{fmt(float(copper_180c['resistance_scale_vs_25c']), 2)}x` at 180 C. That supports the direction of hot-winding torque/current loss, though actual FPV motor winding temperature needs telemetry or bench data.")
    lines.append(f"- `racingQuad` radius of gyration is `rx/ry/rz = {fmt(float(racing_inertia['rg_x_m']))}/{fmt(float(racing_inertia['rg_y_m']))}/{fmt(float(racing_inertia['rg_z_m']))}` m, with yaw-axis inertia about `{fmt(float(racing_inertia['yaw_to_roll_pitch_inertia_ratio']), 2)}x` the roll/pitch-axis mean. That is close to RotorS Hummingbird/PX4 Iris scale after mass normalization, so the base inertia order looks plausible.")
    drag_note = f"`racingQuad` base drag at 10 m/s is `{fmt(float(racing_drag_10['axis_x_drag_force_n']))}` N on body X and `{fmt(float(racing_drag_10['axis_z_drag_force_n']))}` N on body Z before separated-flow additions, equal to `{fmt(float(racing_drag_10['axis_x_drag_over_weight']), 2)}x` and `{fmt(float(racing_drag_10['axis_z_drag_over_weight']), 2)}x` vehicle weight."
    if rotorpy_drag_10 is not None:
        drag_note += f" RotorPy Hummingbird's comparable 10 m/s body-drag-only X/Z forces are `{fmt(float(rotorpy_drag_10['axis_x_drag_force_n']))}`/`{fmt(float(rotorpy_drag_10['axis_z_drag_force_n']))}` N."
    lines.append(f"- {drag_note} Treat the current drag coefficients as very strong unless intentionally gameplay-scaled.")
    lines.append(f"- In the current hybrid gust model, a representative `wind=10 m/s, dirtyAir=1.5` case gives target gust RMS of `{fmt(float(wind_case['current_gust_rms_x_m_s']))}` m/s horizontal X and `{fmt(float(wind_case['current_gust_rms_y_m_s']))}` m/s vertical after combining a scaled dirty-air burble with the low-altitude Dryden target. Against a Dryden reference at 6 m using the same 10 m/s wind, those are `{fmt(float(wind_case['current_x_rms_over_dryden_u']), 2)}x` longitudinal and `{fmt(float(wind_case['current_y_rms_over_dryden_w']), 2)}x` vertical turbulence intensity.")
    lines.append(f"- `racingQuad` now carries `RotorSpec.bladeCount = {fmt(float(racing_timing['configured_blade_count']), 0)}` for blade-pass vibration and gyro notch telemetry. Its configured hover/max blade-pass frequencies are `{fmt(float(racing_timing['hover_blade_pass_hz']))}/{fmt(float(racing_timing['max_blade_pass_hz']))}` Hz, matching the three-blade FPV prop references at the same RPM.")
    lines.append(f"- `racingQuad` configured gyro noise `{fmt(float(racing_imu_mpu6000['configured_gyro_noise_rad_s']))}` rad/s is about `{fmt(float(racing_imu_mpu6000['configured_over_sensor_gyro']), 1)}x` an MPU-6000/6050 one-pole 120 Hz RMS estimate and `{fmt(float(racing_imu_icm42688['configured_over_sensor_gyro']), 1)}x` an ICM-42688-P estimate. Treat the configured noise as frame vibration plus electronics, not bare IMU noise.")
    lines.append(f"- `racingQuad` quiet barometer model noise is about `{fmt(float(racing_baro_noise['quiet_barometer_noise_rms_m']), 4)}` m RMS from the accelerometer-noise coupling term, comparable to good MEMS pressure-sensor noise floors such as `{baro_best_sensor['sensor']}` at `{fmt(float(baro_best_sensor['pressure_noise_altitude_m']), 4)}` m. But the modeled aligned-flow dynamic-pressure error at 20 m/s is `{fmt(float(baro_aligned_20['code_altitude_error_m']))}` m, so barometer realism is dominated by static-port/propwash pressure error rather than silicon pressure noise.")
    if betaflight_log is not None:
        lines.append(f"- The public Betaflight 4.2.4 blackbox log header has `looptime={fmt(float(betaflight_log['looptime_us']), 0)} us`, `pid_process_denom={fmt(float(betaflight_log['pid_process_denom']), 0)}`, `dshot_bidir={fmt(float(betaflight_log['dshot_bidir']), 0)}`, and an estimated main-log rate of `{fmt(float(betaflight_log['estimated_main_sample_hz']), 0)}` Hz. Its field list has gyro, accelerometer, and motor command data, but no `eRPM` field, so it is a format/timing anchor rather than a motor-RPM validation log.")
    lines.append(f"- Betaflight's current blackbox source logs `eRPM / {fmt(float(blackbox_summary['erpm_logged_divisor']), 0)}` when DShot telemetry fields are present. For a 14-pole motor, one logged count equals `{fmt(float(blackbox_summary['mechanical_rpm_per_logged_erpm100']), 2)}` mechanical rpm; the current `racingQuad` hover/max RPM would appear as about `{fmt(float(racing_erpm['hover_logged_erpm100']), 0)}/{fmt(float(racing_erpm['max_logged_erpm100']), 0)}` in an `eRPM[]` blackbox column.")
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
    lines.append("| Source | mass | inertia Ix/Iy/Iz | radius | k | Q/T or kappa | max omega | tau up/down | normalized CT | drag |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for row in open_models:
        tau = f"{fmt(float(row.get('tau_up_s', float('nan'))), 4)}/{fmt(float(row.get('tau_down_s', float('nan'))), 4)}"
        inertia = f"{fmt(float(row.get('inertia_x_kg_m2', float('nan'))))}/{fmt(float(row.get('inertia_y_kg_m2', float('nan'))))}/{fmt(float(row.get('inertia_z_kg_m2', float('nan'))))}"
        drag = f"{fmt(float(row.get('body_drag_x', row.get('rotor_drag_coefficient', float('nan')))))}/{fmt(float(row.get('body_drag_y', row.get('rotor_drag_coefficient', float('nan')))))}/{fmt(float(row.get('body_drag_z', row.get('rotor_drag_coefficient', float('nan')))))}"
        lines.append(f"| [{row['name']}]({row['url']}) | {fmt(float(row.get('mass_kg', float('nan'))))} kg | {inertia} | {fmt(float(row.get('radius_m', float('nan'))), 4)} m | {fmt(float(row.get('k_n_per_rad2', float('nan'))))} | {fmt(float(row.get('qt_m', float('nan'))), 4)} | {fmt(float(row.get('max_omega_rad_s', float('nan'))), 0)} | {tau} s | {fmt(float(row.get('normalized_ct', float('nan'))), 3)} | {drag} |")
    lines.append("")
    lines.append("RotorS and PX4 use the same simple thrust form as this project, `T = k * omega^2`. Their normalized CT values are useful as a sanity range, but their vehicle scale and prop geometry differ from a 5-inch FPV racing quad.")
    lines.append("")
    lines.append("gym-pybullet-drones also exposes simple ground-effect and multi-drone downwash formulas in `BaseAviary.py`: extra ground-effect force is proportional to `rpm^2 * kf * gnd_eff_coeff * (R / (4h))^2`, and downwash uses `alpha = dw1 * (R / (4 dz))^2`, `beta = dw2 * dz + dw3`, then a Gaussian lateral falloff. Those formulas are useful references for this project's ground, propwash, and nearby-drone wake terms.")
    lines.append("")
    lines.append("## Airframe inertia sanity")
    lines.append("")
    lines.append("The table compares radius of gyration `r = sqrt(I/m)` instead of raw inertia. This makes a 27 g Crazyflie, a 1.5 kg Iris, and the current presets comparable on geometry rather than mass. For the yaw ratio, current project presets use their vertical `Y` axis, while URDF/SDF/Python open models use their source `Z` axis.")
    lines.append("")
    lines.append("| Kind | Name | mass | Ixx/Iyy/Izz | rg x/y/z | yaw axis | yaw inertia ratio | Source |")
    lines.append("|---|---|---:|---:|---:|---:|---:|---|")
    for row in inertia_rows:
        source = row["source"]
        source_text = f"[source]({source})" if isinstance(source, str) and source.startswith("http") else str(source)
        lines.append(
            f"| {row['kind']} | {row['name']} | {fmt(float(row['mass_kg']))} kg | "
            f"{fmt(float(row['inertia_x_kg_m2']))}/{fmt(float(row['inertia_y_kg_m2']))}/{fmt(float(row['inertia_z_kg_m2']))} | "
            f"{fmt(float(row['rg_x_m']))}/{fmt(float(row['rg_y_m']))}/{fmt(float(row['rg_z_m']))} m | "
            f"{row['yaw_axis']} | {fmt(float(row['yaw_to_roll_pitch_inertia_ratio']), 2)} | {source_text} |"
        )
    lines.append("")
    lines.append("Current 5-inch and small-lift presets are in the same mass-normalized geometry range as RotorS Hummingbird and PX4 Iris. The larger lift presets have larger radii of gyration, as expected from longer arms and battery/payload mass farther from the center.")
    lines.append("")
    lines.append("## Body drag sanity")
    lines.append("")
    lines.append(f"Current code path: `linearDragCoefficient` contributes `F = -c * |v| * v` in world axes, while `bodyDragCoefficients` contribute per-axis `F_i = -c_i * v_i * |v_i|` in body axes before separated-flow additions. The values below assume sea-level density ratio and do not include the separated-flow drag term. Comparable open-source anchor: [RotorPy Hummingbird]({ROTORPY_HUMMINGBIRD_URL}) uses `c_D` in `N/(m/s)^2`; [gym-pybullet-drones BaseAviary.py]({GYMPYB_BASEAVIARY_URL}) uses a rotor-speed-scaled linear drag model and attributes it to Forster's Crazyflie system identification.")
    lines.append("")
    lines.append("| Kind | Name | speed | linear c | body c x/y/z | drag force x/y/z | x drag / weight | x equiv CdA | Source |")
    lines.append("|---|---|---:|---:|---:|---:|---:|---:|---|")
    for row in drag_rows:
        if float(row["speed_m_s"]) not in (10.0, 20.0):
            continue
        source = row["source"]
        source_text = f"[source]({source})" if isinstance(source, str) and source.startswith("http") else str(source)
        lines.append(
            f"| {row['kind']} | {row['name']} | {fmt(float(row['speed_m_s']), 0)} m/s | "
            f"{fmt(float(row['linear_drag_coefficient']))} | "
            f"{fmt(float(row['body_drag_x']))}/{fmt(float(row['body_drag_y']))}/{fmt(float(row['body_drag_z']))} | "
            f"{fmt(float(row['axis_x_drag_force_n']))}/{fmt(float(row['axis_y_drag_force_n']))}/{fmt(float(row['axis_z_drag_force_n']))} N | "
            f"{fmt(float(row['axis_x_drag_over_weight']), 2)} | "
            f"{fmt(float(row['axis_x_equiv_cda_m2']))} m^2 | {source_text} |"
        )
    lines.append("")
    lines.append("The important warning is formula-level: the current linear coefficient is already a quadratic force term, not a small Stokes-like linear damping term. For `racingQuad`, `linear + body-X` gives an equivalent `CdA` above half a square meter and several vehicle weights of drag by 10 m/s, before separated-flow additions. If this is meant to model real aerodynamic body drag, it is very high; if it is a gameplay stability damper, it should be documented as such and kept separate from physical CdA claims.")
    lines.append("")
    lines.append("## Standard atmosphere, Reynolds, and tip Mach sanity")
    lines.append("")
    lines.append(f"References: [NASA standard atmosphere]({NASA_ATMOSPHERE_URL}), [NASA speed of sound]({NASA_SOUND_URL}), [NASA Sutherland viscosity model]({NASA_VISCOSITY_URL}), [U.S. Standard Atmosphere 1976]({US_STANDARD_ATMOSPHERE_URL}), and the UIUC Reynolds-number propeller reference listed in the source packet. Generated atmosphere CSV: `docs/data/atmosphere_reynolds_mach_summary.csv`.")
    lines.append("")
    lines.append("The table mirrors the current Java formulas for standard-atmosphere pressure ratio, density ratio, speed of sound, and Sutherland-law dynamic viscosity. The `Re75` column is a proxy using 75% span speed and a representative chord of `0.12R` scaled by the project's pitch/chord proxy; the `code Re index` and `low-Re loss` columns mirror the current low-Reynolds model path more directly.")
    lines.append("")
    lines.append("| Scenario | Preset | altitude/temp | density ratio | sound | mu ratio | RPM scale | max Mach | max Re75 proxy | max code Re index | max low-Re loss | max compressibility thrust |")
    lines.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for row in atmosphere_rows:
        if row["preset"] not in ("racingQuad", "cinewhoop", "heavyLift"):
            continue
        if row["scenario"] not in ("sea_level_isa", "project_default_25c", "cold_sea_level_-10c", "hot_sea_level_38c", "mountain_3000m_isa", "mountain_3000m_hot_30c"):
            continue
        lines.append(
            f"| {row['scenario']} | {row['preset']} | "
            f"{fmt(float(row['altitude_m']), 0)} m / {fmt(float(row['ambient_temperature_c']), 1)} C | "
            f"{fmt(float(row['density_ratio']), 3)} | {fmt(float(row['speed_of_sound_m_s']))} m/s | "
            f"{fmt(float(row['viscosity_ratio_25c']), 3)} | {fmt(float(row['same_thrust_rpm_scale']), 3)}x | "
            f"{fmt(float(row['max_tip_mach']), 3)} | {fmt(float(row['max_reynolds_75r_proxy']), 0)} | "
            f"{fmt(float(row['max_code_reynolds_index']), 3)} | {fmt(float(row['max_low_reynolds_loss_proxy']), 3)} | "
            f"{fmt(float(row['max_compressibility_thrust_scale']), 3)}x |"
        )
    lines.append("")
    lines.append("Two modeling notes fall out of this: first, a density ratio below 1 raises required RPM roughly by `1/sqrt(rho_ratio)` for the same thrust, which quickly eats motor/battery headroom at high density altitude. Second, cold air lowers speed of sound, so a fixed max RPM can move closer to the compressibility onset even though cold dense air helps thrust.")
    lines.append("")
    lines.append("## Wind and turbulence sanity")
    lines.append("")
    lines.append(f"References: [pyfly Dryden implementation]({PYFLY_DRYDEN_URL}) and [open UAV wind modeling survey]({UAV_WIND_MODELING_URL}). Generated wind CSV: `docs/data/wind_gust_dryden_reference.csv`.")
    lines.append("")
    lines.append("The Dryden rows use the common low-altitude formulas at 6 m altitude and take `wind20` equal to the scenario wind speed. The current model rows estimate target-gust RMS from the scaled dirty-air burble plus Dryden target before vehicle response and first-order filtering; the runtime implementation drives the Dryden part with a reproducible colored-noise process.")
    lines.append("")
    lines.append("| wind | dirtyAir | current gust RMS X/Y/Z | current peak X/Y/Z | phase periods A/B/C | tau gust/mean | Dryden sigma u/w | RMS ratio X/u Y/w | Dryden time u/w |")
    lines.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for row in wind_rows:
        if float(row["dirty_air"]) not in (0.75, 1.50) or float(row["wind_speed_m_s"]) not in (5.0, 10.0, 15.0):
            continue
        lines.append(
            f"| {fmt(float(row['wind_speed_m_s']), 0)} m/s | {fmt(float(row['dirty_air']), 2)} | "
            f"{fmt(float(row['current_gust_rms_x_m_s']))}/{fmt(float(row['current_gust_rms_y_m_s']))}/{fmt(float(row['current_gust_rms_z_m_s']))} m/s | "
            f"{fmt(float(row['current_gust_peak_x_m_s']))}/{fmt(float(row['current_gust_peak_y_m_s']))}/{fmt(float(row['current_gust_peak_z_m_s']))} m/s | "
            f"{fmt(float(row['phase_a_period_s']))}/{fmt(float(row['phase_b_period_s']))}/{fmt(float(row['phase_c_period_s']))} s | "
            f"{fmt(float(row['gust_time_constant_s']))}/{fmt(float(row['mean_wind_time_constant_s']))} s | "
            f"{fmt(float(row['dryden_sigma_u_m_s']))}/{fmt(float(row['dryden_sigma_w_m_s']))} m/s | "
            f"{fmt(float(row['current_x_rms_over_dryden_u']), 2)}/{fmt(float(row['current_y_rms_over_dryden_w']), 2)} | "
            f"{fmt(float(row['dryden_longitudinal_time_s']))}/{fmt(float(row['dryden_vertical_time_s']))} s |"
        )
    lines.append("")
    lines.append("The Dryden component now carries the physical low-altitude length scales and sigma targets as colored noise, while the remaining deterministic burble is deliberately reduced and kept as obstacle/dirty-air feel. A future full Dryden/Von Karman shaping filter would be a cleaner spectral target, but the wind field is no longer driven only by sine excitation.")
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
    lines.append("## Motor, ESC, and winding thermal sanity")
    lines.append("")
    lines.append(f"Sources: [U8/Kv100 processed dyno data]({U8_DYNO_REPO_URL}), [related Actuators paper page]({U8_DYNO_PAPER_URL}), [copper temperature-coefficient reference]({COPPER_TEMP_COEFF_SOURCE_URL}), [NEMA MG-1 insulation-temperature context]({NEMA_MOTOR_INSULATION_URL}), and [Infineon power MOSFET junction-temperature context]({INFINEON_MOSFET_THERMAL_URL}). Generated thermal CSV: `{motor_thermal_summary['summary_csv']}`.")
    lines.append("")
    lines.append("The U8/Kv100 data is a larger motor/driver dyno map, not an FPV 2306 motor. It is useful here as an open processed BLDC motor/driver efficiency, loss, and maximum-temperature reference. The current-preset table mirrors this project's thermal equations using sea-level density and no obstruction/recirculation; steady rises are model proxies, not lab measurements.")
    lines.append("")
    lines.append("| U8 map | voltage | max temp | loss mean/max | motor eff max | driver eff max | rpm max | torque max | current max | max-temp point current/rpm/torque |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for row in motor_thermal_summary.get("u8_rows", []):
        item = row  # type: ignore[assignment]
        lines.append(
            f"| {item['name']} | {fmt(float(item['voltage_v']), 0)} V | "
            f"{fmt(float(item['temperature_max_c']))} C | "
            f"{fmt(float(item['loss_mean_w']))}/{fmt(float(item['loss_max_w']))} W | "
            f"{fmt(float(item['motor_efficiency_max']), 3)} | {fmt(float(item['driver_efficiency_max']), 3)} | "
            f"{fmt(float(item['rpm_max']), 0)} | {fmt(float(item['torque_max_nm']))} N*m | "
            f"{fmt(float(item['current_max_a']))} A | "
            f"{fmt(float(item['max_temp_current_a']))} A / {fmt(float(item['max_temp_rpm']), 0)} rpm / {fmt(float(item['max_temp_torque_nm']))} N*m |"
        )
    lines.append("")
    lines.append("| Preset | rise/cooling | motor limit/cutoff | ESC limit/cutoff | tau base/full | hover/full steady motor rise | full ESC rise proxy | inferred winding R | R scale limit/cutoff |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|")
    for row in motor_thermal_summary.get("current_rows", []):
        item = row  # type: ignore[assignment]
        lines.append(
            f"| {item['name']} | {fmt(float(item['thermal_rise_c_s']))} C/s / {fmt(float(item['cooling_rate_s']), 3)} 1/s | "
            f"{fmt(float(item['motor_limit_c']), 0)}/{fmt(float(item['motor_cutoff_c']), 0)} C | "
            f"{fmt(float(item['esc_limit_c']), 0)}/{fmt(float(item['esc_cutoff_c']), 0)} C | "
            f"{fmt(float(item['motor_base_time_constant_s']))}/{fmt(float(item['motor_full_wash_time_constant_s']))} s | "
            f"{fmt(float(item['motor_hover_steady_rise_proxy_c']))}/{fmt(float(item['motor_full_steady_rise_c']))} C | "
            f"{fmt(float(item['esc_full_current_steady_rise_proxy_c']))} C | "
            f"{fmt(float(item['inferred_winding_resistance_25c_ohm']))} ohm | "
            f"{fmt(float(item['winding_resistance_scale_at_limit']), 2)}x/{fmt(float(item['winding_resistance_scale_at_cutoff']), 2)}x |"
        )
    lines.append("")
    lines.append("| Copper temp | resistance scale vs 25 C |")
    lines.append("|---:|---:|")
    for row in motor_thermal_summary.get("copper_rows", []):
        item = row  # type: ignore[assignment]
        lines.append(f"| {fmt(float(item['temperature_c']), 0)} C | {fmt(float(item['resistance_scale_vs_25c']), 3)}x |")
    lines.append("")
    lines.append("The copper coefficient validates the sign and order of the hot-winding resistance model: by common motor temperature limits, winding resistance is already tens of percent above its 25 C value. The exact thermal rise/cooling constants remain gameplay/model coefficients until matched to FPV motor temperature telemetry or a 2306/2806 bench with thermocouple/IR measurements.")
    lines.append("")
    lines.append("## RPM, filtering, and command timing sanity")
    lines.append("")
    lines.append(f"References: [Betaflight DShot/RPM filtering]({BETAFLIGHT_RPM_FILTER_URL}), [Betaflight PID/filter tuning]({BETAFLIGHT_PID_TUNING_URL}), and [ExpressLRS packet-rate context]({EXPRESSLRS_PACKET_URL}). Betaflight-style RPM filtering is normally motor-RPM telemetry aware; this project's current gyro vibration telemetry uses an average motor notch plus a blade-pass notch derived from each preset's `RotorSpec.bladeCount`.")
    lines.append("")
    lines.append("| Preset | blades | hover RPM | max RPM | configured blade-pass hover/max | 3-blade hover/max | gyro LPF | RC frame | ESC frame | latency/smoothing |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for row in timing_rows:
        lines.append(
            f"| {row['preset']} | {fmt(float(row['configured_blade_count']), 0)} | {fmt(float(row['hover_rpm']), 0)} | {fmt(float(row['max_rpm']), 0)} | "
            f"{fmt(float(row['hover_blade_pass_hz']), 0)}/{fmt(float(row['max_blade_pass_hz']), 0)} Hz | "
            f"{fmt(float(row['hover_3blade_pass_hz']), 0)}/{fmt(float(row['max_3blade_pass_hz']), 0)} Hz | "
            f"{fmt(float(row['gyro_low_pass_hz']), 0)} Hz | "
            f"{fmt(float(row['rc_frame_rate_hz']), 0)} Hz / {fmt(float(row['rc_frame_interval_ms']))} ms | "
            f"{fmt(float(row['esc_command_frame_rate_hz']), 0)} Hz / {fmt(float(row['esc_command_frame_interval_ms']))} ms | "
            f"control {fmt(float(row['control_latency_ms']))} ms, RC {fmt(float(row['rc_command_latency_ms']))} ms, smooth {fmt(float(row['rc_smoothing_tau_ms']))} ms |"
        )
    lines.append("")
    lines.append("For 5-inch FPV three-blade props, `racingQuad` and `cinewhoop` now use a physical three-blade blade-pass frequency. Keep `RotorSpec.bladeCount` aligned with the prop family used for each preset, especially before using blackbox RPM spectra for validation.")
    lines.append("")
    lines.append("## IMU noise and LPF sanity")
    lines.append("")
    lines.append(f"Sources: [MPU-6000/6050 datasheet]({MPU6000_DATASHEET_URL}), [ICM-20602 product page]({ICM20602_PRODUCT_URL}), [BMI270 datasheet]({BMI270_DATASHEET_URL}), and [ICM-42688-P product page]({ICM42688P_PRODUCT_URL}). The generated IMU CSV is `docs/data/imu_noise_reference_summary.csv`.")
    lines.append("")
    lines.append("The calculation treats datasheet noise density as white noise and estimates RMS after a one-pole low-pass with `ENBW = pi/2 * cutoff`. This is a sanity check for electronics noise scale; real FPV gyro noise also includes frame resonance, motor/prop vibration, aliasing, mounting, and filter topology.")
    lines.append("")
    lines.append("| Preset | sensor | gyro LPF | gyro RMS ref | configured gyro | gyro ratio | accel LPF | accel RMS ref | configured accel | accel ratio |")
    lines.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|")
    for row in imu_rows:
        if row["sensor"] not in ("MPU-6000/MPU-6050", "ICM-42688-P"):
            continue
        lines.append(
            f"| {row['preset']} | [{row['sensor']}]({row['source']}) | "
            f"{fmt(float(row['gyro_lpf_hz']), 0)} Hz | {fmt(float(row['sensor_gyro_rms_rad_s']))} rad/s | "
            f"{fmt(float(row['configured_gyro_noise_rad_s']))} rad/s | {fmt(float(row['configured_over_sensor_gyro']), 1)}x | "
            f"{fmt(float(row['accel_lpf_hz']), 0)} Hz | {fmt(float(row['sensor_accel_rms_m_s2']))} m/s^2 | "
            f"{fmt(float(row['configured_accel_noise_m_s2']))} m/s^2 | {fmt(float(row['configured_over_sensor_accel']), 1)}x |"
        )
    lines.append("")
    lines.append("The current sensor-noise values are consistently several times to tens of times above bare IMU electronics noise. That can be reasonable for a gameplay/FPV feel model if these parameters intentionally represent residual vibration after filtering; if they are meant as electronics-only noise, they are high.")
    lines.append("")
    lines.append("## Barometer pressure and altitude sanity")
    lines.append("")
    lines.append(f"Sources: [BMP280 datasheet]({BMP280_DATASHEET_URL}), [BMP388 datasheet]({BMP388_DATASHEET_URL}), [DPS310 datasheet]({DPS310_DATASHEET_URL}), [MS5611 datasheet]({MS5611_DATASHEET_URL}), and [NASA dynamic pressure/atmosphere context]({NASA_ATMOSPHERE_URL}). Generated barometer CSV: `{barometer_summary['summary_csv']}`.")
    lines.append("")
    lines.append("Near sea level, `1 Pa` pressure error is about `0.083 m` altitude error by `dh = dp / (rho g)`. The table separates sensor pressure noise from the project's simulated pressure-port/propwash/dynamic-pressure error.")
    lines.append("")
    lines.append("| Sensor | pressure noise | altitude noise | relative accuracy | relative altitude | note |")
    lines.append("|---|---:|---:|---:|---:|---|")
    for row in barometer_summary.get("sensor_rows", []):
        item = row  # type: ignore[assignment]
        lines.append(
            f"| [{item['sensor']}]({item['source']}) | {fmt(float(item['pressure_noise_pa']))} Pa | "
            f"{fmt(float(item['pressure_noise_altitude_m']), 4)} m | "
            f"{fmt(float(item['relative_accuracy_pa']))} Pa | {fmt(float(item['relative_accuracy_altitude_m']))} m | "
            f"{item['note']} |"
        )
    lines.append("")
    lines.append("| Preset | accel noise | quiet baro amplitude | quiet baro RMS | altitude tau | vertical-speed tau |")
    lines.append("|---|---:|---:|---:|---:|---:|")
    for row in barometer_summary.get("preset_noise_rows", []):
        item = row  # type: ignore[assignment]
        lines.append(
            f"| {item['preset']} | {fmt(float(item['accelerometer_noise_stddev_m_s2']))} m/s^2 | "
            f"{fmt(float(item['quiet_barometer_noise_amplitude_m']), 4)} m | "
            f"{fmt(float(item['quiet_barometer_noise_rms_m']), 4)} m | "
            f"{fmt(float(item['barometer_altitude_tau_s']), 3)} s | {fmt(float(item['barometer_vertical_speed_tau_s']), 3)} s |"
        )
    lines.append("")
    lines.append("| Airspeed | flow mode | dynamic pressure head | code altitude error | equivalent pressure coefficient |")
    lines.append("|---:|---|---:|---:|---:|")
    for row in barometer_summary.get("dynamic_rows", []):
        item = row  # type: ignore[assignment]
        lines.append(
            f"| {fmt(float(item['airspeed_m_s']), 0)} m/s | {item['mode']} | "
            f"{fmt(float(item['dynamic_pressure_head_m']))} m | "
            f"{fmt(float(item['code_altitude_error_m']))} m | "
            f"{fmt(float(item['static_port_pressure_coefficient_equivalent']), 3)} |"
        )
    lines.append("")
    lines.append("Sensor-only altitude noise is centimeters to decimeters, while pressure-port aerodynamic bias can easily be meters at FPV speed. The current dynamic-pressure coefficients are equivalent to a few percent of dynamic pressure in aligned flow, which is plausible as a static-port placement/propwash abstraction but should not be described as raw barometer sensor noise.")
    lines.append("")
    lines.append("## Betaflight blackbox timing and RPM telemetry anchors")
    lines.append("")
    lines.append(f"Sources: [Betaflight blackbox source]({BETAFLIGHT_BLACKBOX_SOURCE_URL}), [blackbox-library parser fixture]({BLACKBOX_LIBRARY_URL}), and [public Betaflight issue log]({BETAFLIGHT_PUBLIC_LOG_URL}). The generated header CSV is `{blackbox_summary['summary_csv']}`.")
    lines.append("")
    lines.append("| Log source | firmware | fields | gyro | motor cmd | eRPM | looptime | PID denom | estimated main rate | DShot bidir | RPM notch |")
    lines.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for row in blackbox_summary.get("header_rows", []):
        item = row  # type: ignore[assignment]
        lines.append(
            f"| [{item['name']}]({item['url']}) | {item['firmware_revision']} | "
            f"{int(item['field_count'])} | {int(item['has_gyro_adc'])} | {int(item['has_motor_command'])} | {int(item['has_erpm'])} | "
            f"{fmt(float(item['looptime_us']), 0)} us | {fmt(float(item['pid_process_denom']), 0)} | "
            f"{fmt(float(item['estimated_main_sample_hz']), 0)} Hz | {fmt(float(item['dshot_bidir']), 0)} | "
            f"{fmt(float(item['gyro_rpm_notch_harmonics']), 0)} harmonics, LPF {fmt(float(item['rpm_notch_lpf_hz']), 0)} Hz |"
        )
    lines.append("")
    lines.append("The public Betaflight issue log is useful because it is a real Betaflight 4.2.4 header with DShot bidirectional telemetry and RPM-filter settings enabled, but it does not expose `eRPM[]` fields in the main field list. For actual motor-RPM validation, the next log target should explicitly include `eRPM[0..3]` or an exported CSV from a viewer/parser that preserves those columns.")
    lines.append("")
    lines.append(f"Betaflight's current blackbox field table comments `eRPM / {fmt(float(blackbox_summary['erpm_logged_divisor']), 0)}`. With the common 14-pole FPV motor setting, mechanical rpm is `logged_eRPM100 * {fmt(float(blackbox_summary['mechanical_rpm_per_logged_erpm100']), 2)}`. For blade-pass checks, multiply mechanical rpm by blade count and divide by 60.")
    lines.append("")
    lines.append("| Preset | motor poles | hover RPM | max RPM | expected logged eRPM/100 hover/max | 3-blade blade-pass hover/max |")
    lines.append("|---|---:|---:|---:|---:|---:|")
    for row in blackbox_summary.get("erpm_rows", []):
        item = row  # type: ignore[assignment]
        lines.append(
            f"| {item['preset']} | {fmt(float(item['motor_poles']), 0)} | "
            f"{fmt(float(item['hover_mechanical_rpm']), 0)} | {fmt(float(item['max_mechanical_rpm']), 0)} | "
            f"{fmt(float(item['hover_logged_erpm100']), 0)}/{fmt(float(item['max_logged_erpm100']), 0)} | "
            f"{fmt(float(item['hover_3blade_pass_hz']), 0)}/{fmt(float(item['max_3blade_pass_hz']), 0)} Hz |"
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
    lines.append(f"Temperature references: [Battery University low/high-temperature discharge]({BATTERY_UNIVERSITY_TEMPERATURE_URL}), [Battery University internal-resistance performance]({BATTERY_UNIVERSITY_IR_URL}), [CHL LiPo IR explainer]({CHL_LIPO_IR_URL}), and the Mendeley LiPo ECM dataset above. Generated temperature CSV: `docs/data/battery_temperature_derating_summary.csv`.")
    lines.append("")
    lines.append("| Battery temp | resistance scale | current scale | thermal power limit | effective R | effective current limit | sag at nominal/scaled limit |")
    lines.append("|---:|---:|---:|---:|---:|---:|---:|")
    for row in battery_temp_rows:
        lines.append(
            f"| {fmt(float(row['battery_temperature_c']), 0)} C | "
            f"{fmt(float(row['resistance_scale']), 2)}x | {fmt(float(row['current_scale']), 2)}x | "
            f"{fmt(float(row['thermal_power_limit']), 2)}x | {fmt(float(row['effective_resistance_ohm']))} ohm | "
            f"{fmt(float(row['effective_current_limit_a']))} A | "
            f"{fmt(float(row['sag_at_nominal_limit_v']))}/{fmt(float(row['sag_at_temperature_scaled_limit_v']))} V |"
        )
    lines.append("")
    lines.append("The current temperature model is qualitatively aligned with Li-ion behavior: cold packs get higher effective resistance and lower current capability, while high temperature gradually reduces allowable power before hard thermal limiting. The exact coefficients are still heuristic; measured FPV high-C pack ESR versus temperature would be the best next calibration target.")
    lines.append("")
    lines.append("The Mendeley LiPo dataset exposes raw capacity, partial-discharge, EIS, and fitted ECM CSVs. The fitted model files provide columns `SOC, R_0, R_1, Q_1, a_1, R_2, Q_2, a_2, Q, L`, so `R_0(SOC, SOH)` is a direct candidate for replacing a constant internal resistance with a state-dependent lookup. Caveat: the cells are 3.7 V, 1.1 Ah BAK LP-503562-IS-3 packs measured at 25 C, with 1 A standard discharge and 3 A stress discharge, so the dataset informs SOC/SOH shape more than FPV high-C absolute resistance.")
    lines.append("")
    if mendeley_ecm.get("available"):
        lines.append(f"Extracted ECM summary CSV: `{mendeley_ecm['summary_csv']}`. The archive download endpoint is `{MENDELEY_LIPO_DATASET_URL}` -> `/public-api/zip/stcppt2r68/download/1`; the large zip is not stored in this repo. Note: the actual fitted CSV header uses `RO` for ohmic resistance.")
        lines.append("")
        lines.append(f"Across `{int(float(mendeley_ecm['row_count']))}` fitted-cycle files, mean `RO` spans `{fmt(float(mendeley_ecm['r0_mean_min_ohm']) * 1000.0)}-{fmt(float(mendeley_ecm['r0_mean_max_ohm']) * 1000.0)}` mOhm/cell, and low-SOC `RO` is `{fmt(float(mendeley_ecm['low_soc_over_high_soc_min']), 3)}-{fmt(float(mendeley_ecm['low_soc_over_high_soc_max']), 3)}x` high-SOC `RO` with average `{fmt(float(mendeley_ecm['low_soc_over_high_soc_avg']), 3)}x`.")
        lines.append("")
        lines.append("| Pack | fitted files | cycle span | first mean RO | last mean RO | growth |")
        lines.append("|---|---:|---:|---:|---:|---:|")
        for row in mendeley_ecm.get("pack_rows", []):
            item = row  # type: ignore[assignment]
            lines.append(
                f"| {item['pack']} | {int(item['file_count'])} | "
                f"{fmt(float(item['first_cycle']), 0)}-{fmt(float(item['last_cycle']), 0)} | "
                f"{fmt(float(item['first_r0_mean_ohm']) * 1000.0)} mOhm | "
                f"{fmt(float(item['last_r0_mean_ohm']) * 1000.0)} mOhm | "
                f"{fmt(float(item['cycle_growth_ratio']), 3)}x |"
            )
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
    lines.append("5. Treat the current drag coefficients as gameplay damping unless a real FPV airframe drag data source supports multi-weight drag at 10 m/s; RotorPy's comparable Hummingbird body-drag coefficients are much smaller.")
    lines.append("6. Keep `RotorSpec.bladeCount` aligned with the prop family used for calibration; three-blade FPV presets should keep blade-pass vibration and gyro notch telemetry at 3x mechanical motor frequency.")
    lines.append("7. Label sensor-noise parameters as electronics-only or residual vibration/noise-after-filtering. Current values are much larger than bare IMU datasheet RMS at the configured LPF bandwidths, which is plausible for FPV vibration but not for pure sensor electronics.")
    lines.append("8. Keep physical turbulence and FPV dirty-air feel separate. The wind model now has a low-altitude Dryden-scaled turbulence component plus a reduced deterministic burble component for obstacle/wake feel.")
    lines.append("9. Separate barometer silicon pressure noise from pressure-port/propwash/dynamic-pressure altitude bias. The former is centimeters-to-decimeters; the current high-speed flow model is meters and should be documented as aerodynamic/static-port error.")
    lines.append("10. Keep battery temperature effects separate from SOC/SOH effects. Current low-temperature resistance/current scaling is directionally plausible but still needs high-C FPV pack ESR versus temperature data for coefficient calibration.")
    lines.append("11. Treat public blackbox logs without `eRPM[]` as timing/gyro/motor-command anchors only. For RPM-filter validation, require logs or exports that explicitly include `eRPM[0..3]`, and convert Betaflight logged `eRPM/100` through motor pole count before comparing with mechanical RPM.")
    lines.append("12. Next data targets: digitized coaxial thrust/efficiency curves versus `z/D`, FPV high-C pack absolute ESR versus SOC/temperature, FPV airframe coast-down/log-fit drag, and Betaflight blackbox logs with RPM telemetry for propwash recovery validation.")
    lines.append("")
    while lines and lines[-1] == "":
        lines.pop()
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
        ROTORPY_HUMMINGBIRD_URL,
        COAXIAL_BENCHMARK_URL,
        COAXIAL_RESULTS_URL,
        CAMBRIDGE_VRS_URL,
        LIPO_EIS_DATASET_URL,
        MENDELEY_LIPO_DATASET_URL,
        NASA_BATTERY_DATASET_URL,
        CHL_LIPO_IR_URL,
        BATTERY_UNIVERSITY_TEMPERATURE_URL,
        BATTERY_UNIVERSITY_IR_URL,
        NASA_ATMOSPHERE_URL,
        NASA_SOUND_URL,
        NASA_VISCOSITY_URL,
        US_STANDARD_ATMOSPHERE_URL,
        PYFLY_DRYDEN_URL,
        UAV_WIND_MODELING_URL,
        U8_DYNO_REPO_URL,
        BETAFLIGHT_RPM_FILTER_URL,
        BETAFLIGHT_PID_TUNING_URL,
        EXPRESSLRS_PACKET_URL,
        BETAFLIGHT_BLACKBOX_SOURCE_URL,
        BLACKBOX_LIBRARY_FIXTURE_URL,
        BETAFLIGHT_PUBLIC_LOG_URL,
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
    battery_temp_rows = summarize_battery_temperature_derating(presets)
    motor_thermal_summary = summarize_motor_esc_thermal(presets)
    coaxial_rows = summarize_coaxial_spacing(presets)
    motor_response_rows = summarize_motor_response(presets, open_models)
    inertia_rows = summarize_inertia_geometry(presets, open_models)
    drag_rows = summarize_body_drag(presets, open_models)
    timing_rows = summarize_timing_vibration(presets)
    imu_rows = summarize_imu_noise(presets)
    wind_rows = summarize_wind_gust()
    atmosphere_rows = summarize_atmosphere_reynolds(presets)
    barometer_summary = summarize_barometer(presets)
    blackbox_summary = summarize_blackbox_sources(timing_rows)
    mendeley_ecm = summarize_mendeley_ecm()

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

    write_summary_csv(static, forward, mqtb, open_models, presets, comparisons, zju_ground, ground_rows, vrs_rows, battery_ir_rows, battery_temp_rows, motor_thermal_summary, coaxial_rows, motor_response_rows, inertia_rows, drag_rows, timing_rows, imu_rows, wind_rows, atmosphere_rows, barometer_summary, blackbox_summary, mendeley_ecm)
    write_markdown(static, forward, mqtb, command_rows, open_models, presets, comparisons, battery, zju_ground, ground_rows, vrs_rows, battery_ir_rows, battery_temp_rows, motor_thermal_summary, coaxial_rows, motor_response_rows, inertia_rows, drag_rows, timing_rows, imu_rows, wind_rows, atmosphere_rows, barometer_summary, blackbox_summary, mendeley_ecm)
    print("Wrote docs/fpv-sim-model-validation.md")
    print("Wrote docs/data/fpv_model_validation_summary.csv")
    print("Wrote docs/data/blackbox_log_header_summary.csv")
    print("Wrote docs/data/imu_noise_reference_summary.csv")
    print("Wrote docs/data/wind_gust_dryden_reference.csv")
    print("Wrote docs/data/barometer_reference_summary.csv")
    print("Wrote docs/data/battery_temperature_derating_summary.csv")
    print("Wrote docs/data/atmosphere_reynolds_mach_summary.csv")
    print("Wrote docs/data/motor_esc_thermal_reference.csv")
    print(f"Cached raw sources in {RAW}")


if __name__ == "__main__":
    main()
