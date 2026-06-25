"""Analyze APdrone Betaflight rate configuration and logged rate envelope.

Outputs:
  docs/data/apdrone_rate_envelope_reference.csv

The APdrone files use Betaflight 4.5 Actual Rates. In that mode the
full-stick target comes from rates * 10 deg/s; rate_limit is a final clamp.
This script separates those two concepts, compares the current apDrone()
rate profile with the Betaflight configuration, and scans Blackbox setpoint
and gyro fields to show how much of the configured envelope appears in logs.
"""

from __future__ import annotations

import csv
import math
import re
from dataclasses import dataclass, field
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "apdrone_zgsvdtxnfh_v2"
OUTPUT = DATA / "apdrone_rate_envelope_reference.csv"
CONFIG_DUMP = RAW / "betaflight_configuration_for_f722.txt"

SOURCE_PAGE = "https://data.mendeley.com/datasets/zgsvdtxnfh/2"
DOI = "10.17632/zgsvdtxnfh.2"
BETAFLIGHT_45_RC_SOURCE_URL = "https://raw.githubusercontent.com/betaflight/betaflight/4.5.0/src/main/fc/rc.c"
BETAFLIGHT_45_CONTROL_RATE_HEADER_URL = "https://raw.githubusercontent.com/betaflight/betaflight/4.5.0/src/main/fc/controlrate_profile.h"
DRONE_CONFIG = ROOT / "drone-sim-core" / "src" / "main" / "java" / "com" / "tenicana" / "dronecraft" / "sim" / "DroneConfig.java"
DRONE_PHYSICS = ROOT / "drone-sim-core" / "src" / "main" / "java" / "com" / "tenicana" / "dronecraft" / "sim" / "DronePhysics.java"

APDRONE_PROJECT_MAX_RATE_DPS = 670.0
APDRONE_PROJECT_RATE_EXPO = 0.50
APDRONE_PROJECT_RATE_SUPER = 0.791044776119403
APDRONE_PROJECT_AXIS_ORDER = ("pitch", "yaw", "roll")

AXES = [
    ("roll", 0),
    ("pitch", 1),
    ("yaw", 2),
]

RATES_TYPE_NAMES = {
    0: "BETAFLIGHT",
    1: "RACEFLIGHT",
    2: "KISS",
    3: "ACTUAL",
    4: "QUICK",
}

# Large APdrone logs exceed 1 GB in total. Percentiles are computed from this
# deterministic row sample; maxima and sample counts are exact.
PERCENTILE_SAMPLE_STRIDE = 20
STICK_SCAN = (0.0, 0.25, 0.5, 0.75, 1.0)


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def finite_or_blank(value: str | int | float) -> str | int | float:
    if isinstance(value, float) and not math.isfinite(value):
        return ""
    return value


def parse_float(raw: str | None, default: float = float("nan")) -> float:
    try:
        return float(raw)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return default


def parse_int(raw: str | None, default: int = -1) -> int:
    try:
        return int(float(raw))  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return default


def parse_triplet(raw: str | None) -> list[float]:
    if not raw:
        return [float("nan"), float("nan"), float("nan")]
    values = [parse_float(part.strip()) for part in raw.split(",")]
    while len(values) < 3:
        values.append(float("nan"))
    return values[:3]


def percentile(values: list[float], q: float) -> float:
    clean = sorted(value for value in values if math.isfinite(value))
    if not clean:
        return float("nan")
    pos = (len(clean) - 1) * q
    lo = math.floor(pos)
    hi = math.ceil(pos)
    if lo == hi:
        return clean[int(pos)]
    return clean[lo] * (hi - pos) + clean[hi] * (pos - lo)


def mean(values: list[float]) -> float:
    clean = [value for value in values if math.isfinite(value)]
    return sum(clean) / len(clean) if clean else float("nan")


def current_project_rate_deg_s(stick: float, max_rate_dps: float, expo: float, super_rate: float) -> float:
    command = max(-1.0, min(1.0, stick))
    expo_clamped = max(0.0, min(1.0, expo))
    super_clamped = max(0.0, min(0.95, super_rate))
    center_fraction = (1.0 - expo_clamped) * (1.0 - super_clamped)
    command_abs = abs(command)
    command_squared = command * command
    command_fifth = command_squared * command_squared * command
    expo_curve = command_abs * (command_fifth * expo_clamped + command * (1.0 - expo_clamped))
    stick_movement_fraction = max(0.0, 1.0 - center_fraction)
    shaped = command * center_fraction + stick_movement_fraction * expo_curve
    return max_rate_dps * max(-1.0, min(1.0, shaped))


def betaflight_actual_rate_deg_s(stick: float, max_rate_dps: float, center_sensitivity_dps: float, expo: float) -> float:
    command = max(-1.0, min(1.0, stick))
    command_abs = abs(command)
    expof = command_abs * (command**5 * expo + command * (1.0 - expo))
    stick_movement = max(0.0, max_rate_dps - center_sensitivity_dps)
    return command * center_sensitivity_dps + stick_movement * expof


def infer_actual_stick_fraction(abs_rate_dps: float, max_rate_dps: float, center_sensitivity_dps: float, expo: float) -> float:
    if not math.isfinite(abs_rate_dps) or not math.isfinite(max_rate_dps) or max_rate_dps <= 0.0:
        return float("nan")
    if abs_rate_dps <= 0.0:
        return 0.0
    if abs_rate_dps >= max_rate_dps:
        return abs_rate_dps / max_rate_dps
    lo = 0.0
    hi = 1.0
    for _ in range(42):
        mid = (lo + hi) * 0.5
        rate = betaflight_actual_rate_deg_s(mid, max_rate_dps, center_sensitivity_dps, expo)
        if rate < abs_rate_dps:
            lo = mid
        else:
            hi = mid
    return (lo + hi) * 0.5


def project_super_for_actual_profile(max_rate_dps: float, center_sensitivity_dps: float, expo: float) -> float:
    if max_rate_dps <= 0.0 or (1.0 - expo) <= 1.0e-9:
        return float("nan")
    return 1.0 - (center_sensitivity_dps / max_rate_dps) / (1.0 - expo)


def find_header_line(path: Path) -> int | None:
    with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
        for index, line in enumerate(handle):
            if line.startswith('"loopIteration"') or line.startswith("loopIteration"):
                return index
    return None


def read_blackbox_header(path: Path) -> dict[str, str]:
    header: dict[str, str] = {}
    with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
        for line in handle:
            if line.startswith('"loopIteration"') or line.startswith("loopIteration"):
                break
            try:
                cells = next(csv.reader([line]))
            except csv.Error:
                continue
            if len(cells) >= 2:
                header[cells[0]] = cells[1]
    return header


def apdrone_csv_paths() -> list[Path]:
    paths = []
    for path in RAW.rglob("*.csv"):
        if path.name.startswith("RESULT_"):
            continue
        paths.append(path)
    return sorted(paths)


def infer_scenario(path: Path) -> str:
    rel = repo_path(path)
    if "flight_archives/open_field" in rel:
        return "open_field"
    if "flight_archives/urban_environment" in rel:
        return "urban_environment"
    if "battery_autonomy/max_power_time_flights" in rel:
        return "battery_max_power"
    if "battery_autonomy/normal_power_time_flights" in rel:
        return "battery_normal_power"
    if path.name == "selected_flight.csv":
        return "selected_flight"
    return "unknown"


def parse_betaflight_dump(path: Path) -> dict[str, str]:
    settings: dict[str, str] = {}
    pattern = re.compile(r"^set\s+([A-Za-z0-9_]+)\s*=\s*(.*)$")
    with path.open(encoding="utf-8-sig", errors="ignore") as handle:
        for line in handle:
            match = pattern.match(line.strip())
            if match:
                settings[match.group(1)] = match.group(2).strip()
    return settings


def rate_config_from_dump(settings: dict[str, str], axis: str) -> dict[str, str | float]:
    rc_rate = parse_float(settings.get(f"{axis}_rc_rate"))
    expo = parse_float(settings.get(f"{axis}_expo"))
    rates = parse_float(settings.get(f"{axis}_srate"))
    rate_limit = parse_float(settings.get(f"{axis}_rate_limit"))
    center = rc_rate * 10.0
    max_rate = rates * 10.0
    return {
        "rates_type": settings.get("rates_type", ""),
        "rates_type_name": settings.get("rates_type", ""),
        "rc_rate": rc_rate,
        "expo": expo,
        "expo_fraction": expo / 100.0,
        "rates": rates,
        "rate_limit_deg_s": rate_limit,
        "betaflight_actual_center_sensitivity_deg_s": center,
        "betaflight_actual_max_rate_deg_s": max_rate,
    }


def rate_config_from_header(header: dict[str, str], axis_index: int) -> dict[str, str | float | int]:
    rc_rates = parse_triplet(header.get("rc_rates"))
    rc_expo = parse_triplet(header.get("rc_expo"))
    rates = parse_triplet(header.get("rates"))
    rate_limits = parse_triplet(header.get("rate_limits"))
    rates_type_value = parse_int(header.get("rates_type"))
    rc_rate = rc_rates[axis_index]
    expo = rc_expo[axis_index]
    rate = rates[axis_index]
    center = rc_rate * 10.0
    max_rate = rate * 10.0
    return {
        "rates_type": rates_type_value,
        "rates_type_name": RATES_TYPE_NAMES.get(rates_type_value, "unknown"),
        "rc_rate": rc_rate,
        "expo": expo,
        "expo_fraction": expo / 100.0,
        "rates": rate,
        "rate_limit_deg_s": rate_limits[axis_index],
        "betaflight_actual_center_sensitivity_deg_s": center,
        "betaflight_actual_max_rate_deg_s": max_rate,
    }


@dataclass
class AxisStats:
    sample_count: int = 0
    sampled_count: int = 0
    first_time_s: float = float("nan")
    last_time_s: float = float("nan")
    setpoint_min: float = float("inf")
    setpoint_max: float = float("-inf")
    gyro_min: float = float("inf")
    gyro_max: float = float("-inf")
    sum_abs_setpoint: float = 0.0
    sum_abs_gyro: float = 0.0
    sampled_abs_setpoint: list[float] = field(default_factory=list)
    sampled_abs_gyro: list[float] = field(default_factory=list)
    sampled_abs_setpoint_over_bf: list[float] = field(default_factory=list)
    sampled_abs_gyro_over_bf: list[float] = field(default_factory=list)
    exact_max_abs_setpoint_over_bf: float = float("nan")
    exact_max_abs_gyro_over_bf: float = float("nan")
    setpoint_rc_match_count: int = 0

    def add_time(self, time_s: float) -> None:
        if not math.isfinite(time_s):
            return
        if not math.isfinite(self.first_time_s):
            self.first_time_s = time_s
        self.last_time_s = time_s

    def add(self, setpoint: float, gyro: float, rc_command: float, bf_max_rate: float, should_sample: bool) -> None:
        if not (math.isfinite(setpoint) and math.isfinite(gyro)):
            return
        self.sample_count += 1
        self.setpoint_min = min(self.setpoint_min, setpoint)
        self.setpoint_max = max(self.setpoint_max, setpoint)
        self.gyro_min = min(self.gyro_min, gyro)
        self.gyro_max = max(self.gyro_max, gyro)
        abs_setpoint = abs(setpoint)
        abs_gyro = abs(gyro)
        self.sum_abs_setpoint += abs_setpoint
        self.sum_abs_gyro += abs_gyro
        if math.isfinite(rc_command) and abs(rc_command - setpoint) <= 1.0e-9:
            self.setpoint_rc_match_count += 1
        if math.isfinite(bf_max_rate) and bf_max_rate > 0.0:
            sp_ratio = abs_setpoint / bf_max_rate
            gyro_ratio = abs_gyro / bf_max_rate
            if not math.isfinite(self.exact_max_abs_setpoint_over_bf):
                self.exact_max_abs_setpoint_over_bf = sp_ratio
                self.exact_max_abs_gyro_over_bf = gyro_ratio
            else:
                self.exact_max_abs_setpoint_over_bf = max(self.exact_max_abs_setpoint_over_bf, sp_ratio)
                self.exact_max_abs_gyro_over_bf = max(self.exact_max_abs_gyro_over_bf, gyro_ratio)
        if should_sample:
            self.sampled_count += 1
            self.sampled_abs_setpoint.append(abs_setpoint)
            self.sampled_abs_gyro.append(abs_gyro)
            if math.isfinite(bf_max_rate) and bf_max_rate > 0.0:
                self.sampled_abs_setpoint_over_bf.append(abs_setpoint / bf_max_rate)
                self.sampled_abs_gyro_over_bf.append(abs_gyro / bf_max_rate)

    def duration_s(self) -> float:
        if not (math.isfinite(self.first_time_s) and math.isfinite(self.last_time_s)):
            return float("nan")
        return max(0.0, self.last_time_s - self.first_time_s)

    def exact_max_abs_setpoint(self) -> float:
        values = [abs(self.setpoint_min), abs(self.setpoint_max)]
        return max(value for value in values if math.isfinite(value))

    def exact_max_abs_gyro(self) -> float:
        values = [abs(self.gyro_min), abs(self.gyro_max)]
        return max(value for value in values if math.isfinite(value))


@dataclass
class AggregateStats:
    source_files: set[str] = field(default_factory=set)
    sample_count: int = 0
    sampled_count: int = 0
    duration_s: float = 0.0
    sum_abs_setpoint: float = 0.0
    sum_abs_gyro: float = 0.0
    exact_max_abs_setpoint: float = 0.0
    exact_max_abs_gyro: float = 0.0
    exact_max_abs_setpoint_over_bf: float = 0.0
    exact_max_abs_gyro_over_bf: float = 0.0
    sampled_abs_setpoint: list[float] = field(default_factory=list)
    sampled_abs_gyro: list[float] = field(default_factory=list)
    sampled_abs_setpoint_over_bf: list[float] = field(default_factory=list)
    sampled_abs_gyro_over_bf: list[float] = field(default_factory=list)

    def add_file_axis(self, local_source_file: str, stats: AxisStats) -> None:
        self.source_files.add(local_source_file)
        self.sample_count += stats.sample_count
        self.sampled_count += stats.sampled_count
        duration = stats.duration_s()
        if math.isfinite(duration):
            self.duration_s += duration
        self.sum_abs_setpoint += stats.sum_abs_setpoint
        self.sum_abs_gyro += stats.sum_abs_gyro
        self.exact_max_abs_setpoint = max(self.exact_max_abs_setpoint, stats.exact_max_abs_setpoint())
        self.exact_max_abs_gyro = max(self.exact_max_abs_gyro, stats.exact_max_abs_gyro())
        if math.isfinite(stats.exact_max_abs_setpoint_over_bf):
            self.exact_max_abs_setpoint_over_bf = max(self.exact_max_abs_setpoint_over_bf, stats.exact_max_abs_setpoint_over_bf)
        if math.isfinite(stats.exact_max_abs_gyro_over_bf):
            self.exact_max_abs_gyro_over_bf = max(self.exact_max_abs_gyro_over_bf, stats.exact_max_abs_gyro_over_bf)
        self.sampled_abs_setpoint.extend(stats.sampled_abs_setpoint)
        self.sampled_abs_gyro.extend(stats.sampled_abs_gyro)
        self.sampled_abs_setpoint_over_bf.extend(stats.sampled_abs_setpoint_over_bf)
        self.sampled_abs_gyro_over_bf.extend(stats.sampled_abs_gyro_over_bf)


def stats_to_metrics(stats: AxisStats | AggregateStats) -> dict[str, float | int]:
    sample_count = stats.sample_count
    duration = stats.duration_s() if isinstance(stats, AxisStats) else stats.duration_s
    sample_rate = (sample_count - 1) / duration if duration > 0.0 and sample_count > 1 else float("nan")
    mean_abs_setpoint = stats.sum_abs_setpoint / sample_count if sample_count else float("nan")
    mean_abs_gyro = stats.sum_abs_gyro / sample_count if sample_count else float("nan")
    return {
        "duration_s": duration,
        "sample_count": sample_count,
        "sampled_count_for_percentiles": stats.sampled_count,
        "sample_rate_hz": sample_rate,
        "abs_setpoint_mean_deg_s": mean_abs_setpoint,
        "abs_setpoint_p50_deg_s": percentile(stats.sampled_abs_setpoint, 0.50),
        "abs_setpoint_p95_deg_s": percentile(stats.sampled_abs_setpoint, 0.95),
        "abs_setpoint_p99_deg_s": percentile(stats.sampled_abs_setpoint, 0.99),
        "abs_setpoint_p999_deg_s": percentile(stats.sampled_abs_setpoint, 0.999),
        "abs_setpoint_max_exact_deg_s": stats.exact_max_abs_setpoint() if isinstance(stats, AxisStats) else stats.exact_max_abs_setpoint,
        "abs_gyro_mean_deg_s": mean_abs_gyro,
        "abs_gyro_p50_deg_s": percentile(stats.sampled_abs_gyro, 0.50),
        "abs_gyro_p95_deg_s": percentile(stats.sampled_abs_gyro, 0.95),
        "abs_gyro_p99_deg_s": percentile(stats.sampled_abs_gyro, 0.99),
        "abs_gyro_p999_deg_s": percentile(stats.sampled_abs_gyro, 0.999),
        "abs_gyro_max_exact_deg_s": stats.exact_max_abs_gyro() if isinstance(stats, AxisStats) else stats.exact_max_abs_gyro,
        "setpoint_p99_over_current_project_max": percentile(stats.sampled_abs_setpoint, 0.99) / APDRONE_PROJECT_MAX_RATE_DPS,
        "setpoint_max_exact_over_current_project_max": (stats.exact_max_abs_setpoint() if isinstance(stats, AxisStats) else stats.exact_max_abs_setpoint) / APDRONE_PROJECT_MAX_RATE_DPS,
        "gyro_p99_over_current_project_max": percentile(stats.sampled_abs_gyro, 0.99) / APDRONE_PROJECT_MAX_RATE_DPS,
        "gyro_max_exact_over_current_project_max": (stats.exact_max_abs_gyro() if isinstance(stats, AxisStats) else stats.exact_max_abs_gyro) / APDRONE_PROJECT_MAX_RATE_DPS,
        "setpoint_p99_over_betaflight_actual_max": percentile(stats.sampled_abs_setpoint_over_bf, 0.99),
        "setpoint_max_exact_over_betaflight_actual_max": stats.exact_max_abs_setpoint_over_bf,
        "gyro_p99_over_betaflight_actual_max": percentile(stats.sampled_abs_gyro_over_bf, 0.99),
        "gyro_max_exact_over_betaflight_actual_max": stats.exact_max_abs_gyro_over_bf,
    }


def rows_for_references() -> list[dict[str, str | int | float]]:
    rows: list[dict[str, str | int | float]] = [
        {
            "row_type": "apdrone_rate_envelope_method",
            "source_page": SOURCE_PAGE,
            "doi": DOI,
            "local_source_file": repo_path(RAW),
            "percentile_sample_stride": PERCENTILE_SAMPLE_STRIDE,
            "note": "Percentiles use deterministic every-N-row samples; maxima, row counts, and file durations are exact.",
        },
        {
            "row_type": "reference_formula",
            "source_page": BETAFLIGHT_45_CONTROL_RATE_HEADER_URL,
            "name": "Betaflight 4.5 rates_type enum",
            "rates_type": 3,
            "rates_type_name": "ACTUAL",
            "note": "controlrate_profile.h defines RATES_TYPE_ACTUAL after Betaflight/Raceflight/KISS, so APdrone Blackbox rates_type=3 is Actual Rates.",
        },
        {
            "row_type": "reference_formula",
            "source_page": BETAFLIGHT_45_RC_SOURCE_URL,
            "name": "Betaflight 4.5 Actual Rates",
            "formula": "centerSensitivity=rcRates*10; maxRate=rates*10; angleRate=stick*centerSensitivity+max(0,maxRate-centerSensitivity)*expof; rawSetpoint is then constrained by rate_limit",
            "note": "This separates the Actual Rates full-stick target from rate_limit, which is only a final clamp.",
        },
        {
            "row_type": "reference_formula",
            "source_page": repo_path(DRONE_PHYSICS),
            "name": "Current DronePhysics shapeRateInput",
            "formula": "centerFraction=(1-expo)*(1-superRate); target=maxRate*(stick*centerFraction+(1-centerFraction)*expoCurve)",
            "note": "The current project shape is Actual-Rates-like, but the configured maxRate determines the full-stick target.",
        },
    ]
    return rows


def rows_for_dump_config() -> list[dict[str, str | int | float]]:
    settings = parse_betaflight_dump(CONFIG_DUMP)
    rows: list[dict[str, str | int | float]] = []
    for axis, _ in AXES:
        config = rate_config_from_dump(settings, axis)
        max_rate = float(config["betaflight_actual_max_rate_deg_s"])
        center = float(config["betaflight_actual_center_sensitivity_deg_s"])
        expo_fraction = float(config["expo_fraction"])
        project_equiv_super = project_super_for_actual_profile(max_rate, center, expo_fraction)
        rows.append(
            {
                "row_type": "apdrone_betaflight_dump_rate_profile",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "local_source_file": repo_path(CONFIG_DUMP),
                "axis": axis,
                **config,
                "project_equivalent_rate_expo": expo_fraction,
                "project_equivalent_rate_super_for_same_center_and_max": project_equiv_super,
                "current_project_max_rate_deg_s": APDRONE_PROJECT_MAX_RATE_DPS,
                "current_project_rate_expo": APDRONE_PROJECT_RATE_EXPO,
                "current_project_rate_super": APDRONE_PROJECT_RATE_SUPER,
                "current_project_max_over_betaflight_actual_max": APDRONE_PROJECT_MAX_RATE_DPS / max_rate if max_rate > 0.0 else float("nan"),
                "note": "The dump's rate_limit is 1998 deg/s, but current apDrone maxRate follows the selected 670 deg/s Actual Rates target used by urban/battery logs.",
            }
        )
    return rows


def rows_for_current_project() -> list[dict[str, str | int | float]]:
    rows: list[dict[str, str | int | float]] = []
    center = APDRONE_PROJECT_MAX_RATE_DPS * (1.0 - APDRONE_PROJECT_RATE_EXPO) * (1.0 - APDRONE_PROJECT_RATE_SUPER)
    for axis in ("roll", "pitch", "yaw"):
        rows.append(
            {
                "row_type": "current_project_apDrone_rate_axis",
                "source_page": repo_path(DRONE_CONFIG),
                "axis": axis,
                "current_project_max_rate_deg_s": APDRONE_PROJECT_MAX_RATE_DPS,
                "current_project_rate_expo": APDRONE_PROJECT_RATE_EXPO,
                "current_project_rate_super": APDRONE_PROJECT_RATE_SUPER,
                "current_project_center_sensitivity_deg_s": center,
                "current_project_center_over_dump_center": center / 70.0,
                "current_project_max_over_dump_actual_300": APDRONE_PROJECT_MAX_RATE_DPS / 300.0,
                "current_project_max_over_urban_or_battery_actual_670": APDRONE_PROJECT_MAX_RATE_DPS / 670.0,
                "note": "Center sensitivity matches APdrone's rc_rate=7 -> 70 deg/s and full-stick max matches the 670 deg/s Actual Rates target used by urban/battery logs, not the 1998 deg/s rate_limit clamp.",
            }
        )
        for stick in STICK_SCAN:
            rows.append(
                {
                    "row_type": "current_project_apDrone_curve_scan",
                    "source_page": repo_path(DRONE_PHYSICS),
                    "axis": axis,
                    "stick": stick,
                    "current_project_max_rate_deg_s": APDRONE_PROJECT_MAX_RATE_DPS,
                    "current_project_rate_expo": APDRONE_PROJECT_RATE_EXPO,
                    "current_project_rate_super": APDRONE_PROJECT_RATE_SUPER,
                    "current_project_target_rate_deg_s": current_project_rate_deg_s(
                        stick,
                        APDRONE_PROJECT_MAX_RATE_DPS,
                        APDRONE_PROJECT_RATE_EXPO,
                        APDRONE_PROJECT_RATE_SUPER,
                    ),
                }
            )
    return rows


def rows_for_profile_curve(profile_name: str, max_rate: float, center: float, expo_fraction: float, source_file: str, note: str) -> list[dict[str, str | int | float]]:
    rows: list[dict[str, str | int | float]] = []
    project_super = project_super_for_actual_profile(max_rate, center, expo_fraction)
    for stick in STICK_SCAN:
        rows.append(
            {
                "row_type": "betaflight_actual_curve_scan",
                "name": profile_name,
                "source_page": BETAFLIGHT_45_RC_SOURCE_URL,
                "local_source_file": source_file,
                "stick": stick,
                "betaflight_actual_max_rate_deg_s": max_rate,
                "betaflight_actual_center_sensitivity_deg_s": center,
                "expo_fraction": expo_fraction,
                "betaflight_actual_target_rate_deg_s": betaflight_actual_rate_deg_s(stick, max_rate, center, expo_fraction),
                "project_equivalent_rate_expo": expo_fraction,
                "project_equivalent_rate_super_for_same_center_and_max": project_super,
                "note": note,
            }
        )
    return rows


def stream_file_stats(path: Path) -> tuple[dict[str, str], dict[str, AxisStats]]:
    header_line = find_header_line(path)
    if header_line is None:
        return {}, {}
    header = read_blackbox_header(path)
    stats = {axis: AxisStats() for axis, _ in AXES}
    with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
        for _ in range(header_line):
            next(handle)
        reader = csv.reader(handle)
        try:
            fieldnames = next(reader)
        except StopIteration:
            return header, {}
        index = {name: idx for idx, name in enumerate(fieldnames)}
        required = ["time"] + [f"setpoint[{i}]" for i in range(3)] + [f"gyroADC[{i}]" for i in range(3)] + [f"rcCommands[{i}]" for i in range(3)]
        if any(name not in index for name in required):
            return header, {}
        max_index = max(index[name] for name in required)
        bf_max_rates = {
            axis: float(rate_config_from_header(header, axis_index)["betaflight_actual_max_rate_deg_s"])
            for axis, axis_index in AXES
        }
        for row_number, row in enumerate(reader, start=1):
            if len(row) <= max_index:
                continue
            time_s = parse_float(row[index["time"]]) / 1.0e6
            should_sample = row_number % PERCENTILE_SAMPLE_STRIDE == 0
            for axis, axis_index in AXES:
                setpoint = parse_float(row[index[f"setpoint[{axis_index}]"]])
                gyro = parse_float(row[index[f"gyroADC[{axis_index}]"]])
                rc_command = parse_float(row[index[f"rcCommands[{axis_index}]"]])
                stats[axis].add_time(time_s)
                stats[axis].add(setpoint, gyro, rc_command, bf_max_rates[axis], should_sample)
    return header, stats


def rows_for_file(path: Path, aggregates: dict[tuple[str, str], AggregateStats]) -> tuple[list[dict[str, str | int | float]], set[tuple[float, float, float, str]]]:
    header, stats_by_axis = stream_file_stats(path)
    if not stats_by_axis:
        return [], set()
    scenario = infer_scenario(path)
    local_source_file = repo_path(path)
    profile_keys: set[tuple[float, float, float, str]] = set()
    rows: list[dict[str, str | int | float]] = []
    for axis, axis_index in AXES:
        stats = stats_by_axis[axis]
        config = rate_config_from_header(header, axis_index)
        max_rate = float(config["betaflight_actual_max_rate_deg_s"])
        center = float(config["betaflight_actual_center_sensitivity_deg_s"])
        expo_fraction = float(config["expo_fraction"])
        profile_key = (max_rate, center, expo_fraction, scenario)
        profile_keys.add(profile_key)
        metrics = stats_to_metrics(stats)
        p99_setpoint = float(metrics["abs_setpoint_p99_deg_s"])
        max_setpoint = float(metrics["abs_setpoint_max_exact_deg_s"])
        row: dict[str, str | int | float] = {
            "row_type": "apdrone_rate_log_file_axis",
            "source_page": SOURCE_PAGE,
            "doi": DOI,
            "local_source_file": local_source_file,
            "scenario": scenario,
            "flight_filename": path.name,
            "firmware_version": header.get("firmwareVersion", ""),
            "firmware_revision": header.get("Firmware revision", ""),
            "axis": axis,
            **config,
            **metrics,
            "setpoint_rc_command_exact_match_fraction": stats.setpoint_rc_match_count / stats.sample_count if stats.sample_count else float("nan"),
            "inferred_betaflight_actual_stick_fraction_at_setpoint_p99": infer_actual_stick_fraction(p99_setpoint, max_rate, center, expo_fraction),
            "inferred_betaflight_actual_stick_fraction_at_setpoint_max": infer_actual_stick_fraction(max_setpoint, max_rate, center, expo_fraction),
            "current_project_rate_at_inferred_p99_stick_deg_s": current_project_rate_deg_s(
                min(1.0, infer_actual_stick_fraction(p99_setpoint, max_rate, center, expo_fraction)),
                APDRONE_PROJECT_MAX_RATE_DPS,
                APDRONE_PROJECT_RATE_EXPO,
                APDRONE_PROJECT_RATE_SUPER,
            ),
            "note": "setpoint and gyro units are interpreted as deg/s from Betaflight Blackbox CSV export; rcCommands[0..2] match setpoint in these APdrone exports.",
        }
        rows.append(row)
        aggregates[(scenario, axis)].add_file_axis(local_source_file, stats)
        if scenario != "selected_flight":
            aggregates[("all_archives_excluding_selected", axis)].add_file_axis(local_source_file, stats)
        aggregates[("all_logs_including_selected", axis)].add_file_axis(local_source_file, stats)
    return rows, profile_keys


def rows_for_aggregates(aggregates: dict[tuple[str, str], AggregateStats]) -> list[dict[str, str | int | float]]:
    rows: list[dict[str, str | int | float]] = []
    for (scenario, axis), stats in sorted(aggregates.items()):
        if stats.sample_count == 0:
            continue
        metrics = stats_to_metrics(stats)
        rows.append(
            {
                "row_type": "apdrone_rate_log_scenario_axis_summary",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "scenario": scenario,
                "axis": axis,
                "source_file_count": len(stats.source_files),
                **metrics,
                "note": "Scenario summaries combine sampled percentiles across source files. all_archives_excluding_selected avoids double-counting the selected_flight/open_field duplicate.",
            }
        )
    return rows


def normalize_rows(rows: list[dict[str, str | int | float]]) -> list[dict[str, str | int | float]]:
    normalized: list[dict[str, str | int | float]] = []
    for row in rows:
        normalized.append({key: finite_or_blank(value) for key, value in row.items()})
    return normalized


def write_rows(rows: list[dict[str, str | int | float]]) -> None:
    fieldnames: list[str] = []
    for row in rows:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with OUTPUT.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(normalize_rows(rows))


def main() -> None:
    rows = rows_for_references()
    rows.extend(rows_for_dump_config())
    rows.extend(rows_for_current_project())
    rows.extend(
        rows_for_profile_curve(
            "APdrone dump/open-field Actual profile",
            300.0,
            70.0,
            0.50,
            repo_path(CONFIG_DUMP),
            "Dump and open-field logs use rc_rate=7, expo=50, rates=30.",
        )
    )
    rows.extend(
        rows_for_profile_curve(
            "APdrone battery Actual profile",
            670.0,
            70.0,
            0.00,
            repo_path(RAW),
            "Battery-autonomy logs use rc_rate=7, expo=0, rates=67.",
        )
    )
    rows.extend(
        rows_for_profile_curve(
            "APdrone urban Actual profile",
            670.0,
            70.0,
            0.50,
            repo_path(RAW),
            "Urban logs use rc_rate=7, expo=50, rates=67.",
        )
    )

    aggregates: dict[tuple[str, str], AggregateStats] = {}
    for scenario in ("selected_flight", "open_field", "urban_environment", "battery_max_power", "battery_normal_power", "all_archives_excluding_selected", "all_logs_including_selected"):
        for axis, _ in AXES:
            aggregates[(scenario, axis)] = AggregateStats()

    discovered_profiles: set[tuple[float, float, float, str]] = set()
    for path in apdrone_csv_paths():
        file_rows, profile_keys = rows_for_file(path, aggregates)
        rows.extend(file_rows)
        discovered_profiles.update(profile_keys)

    for max_rate, center, expo, scenario in sorted(discovered_profiles):
        rows.append(
            {
                "row_type": "apdrone_discovered_log_rate_profile",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "scenario": scenario,
                "betaflight_actual_max_rate_deg_s": max_rate,
                "betaflight_actual_center_sensitivity_deg_s": center,
                "expo_fraction": expo,
                "project_equivalent_rate_expo": expo,
                "project_equivalent_rate_super_for_same_center_and_max": project_super_for_actual_profile(max_rate, center, expo),
                "current_project_max_over_betaflight_actual_max": APDRONE_PROJECT_MAX_RATE_DPS / max_rate if max_rate > 0.0 else float("nan"),
            }
        )

    rows.extend(rows_for_aggregates(aggregates))
    write_rows(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")


if __name__ == "__main__":
    main()
