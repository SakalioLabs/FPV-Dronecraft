"""Analyze APdrone throttle logs against Betaflight and project curves.

Outputs:
  docs/data/apdrone_throttle_curve_reference.csv

The APdrone Blackbox exports expose throttle command fields for all logs and
motor/eRPM telemetry for the urban archive. This script separates Betaflight
throttle limit and thrust-linearization settings from the current project
power-law throttle curve, then scans the logs to compare throttle distributions,
project-implied thrust/RPM, and measured urban eRPM.
"""

from __future__ import annotations

import csv
import math
from dataclasses import dataclass, field
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "apdrone_zgsvdtxnfh_v2"
OUTPUT = DATA / "apdrone_throttle_curve_reference.csv"

SOURCE_PAGE = "https://data.mendeley.com/datasets/zgsvdtxnfh/2"
DOI = "10.17632/zgsvdtxnfh.2"
BETAFLIGHT_45_RC_SOURCE_URL = "https://raw.githubusercontent.com/betaflight/betaflight/4.5.0/src/main/fc/rc.c"
BETAFLIGHT_45_MIXER_SOURCE_URL = "https://raw.githubusercontent.com/betaflight/betaflight/4.5.0/src/main/flight/mixer.c"
BETAFLIGHT_45_PID_SOURCE_URL = "https://raw.githubusercontent.com/betaflight/betaflight/4.5.0/src/main/flight/pid.c"
BETAFLIGHT_45_PID_INIT_SOURCE_URL = "https://raw.githubusercontent.com/betaflight/betaflight/4.5.0/src/main/flight/pid_init.c"
DRONE_CONFIG = ROOT / "drone-sim-core" / "src" / "main" / "java" / "com" / "tenicana" / "dronecraft" / "sim" / "DroneConfig.java"

APDRONE_MASS_KG = 0.6284
G0 = 9.80665
APDRONE_MAX_ROTOR_THRUST_N = 13.5
APDRONE_ROTOR_COUNT = 4
APDRONE_TOTAL_MAX_THRUST_N = APDRONE_MAX_ROTOR_THRUST_N * APDRONE_ROTOR_COUNT
APDRONE_THRUST_COEFF_N_PER_RAD_S2 = 1.3918976015517363e-6
APDRONE_REFERENCE_THROTTLE = 0.5439609800526073
APDRONE_HOVER_THRUST_FRACTION = APDRONE_MASS_KG * G0 / APDRONE_TOTAL_MAX_THRUST_N
APDRONE_THROTTLE_EXPONENT = math.log(APDRONE_HOVER_THRUST_FRACTION) / math.log(APDRONE_REFERENCE_THROTTLE)
APDRONE_MAX_RPM = math.sqrt(APDRONE_MAX_ROTOR_THRUST_N / APDRONE_THRUST_COEFF_N_PER_RAD_S2) * 60.0 / (2.0 * math.pi)

PERCENTILE_SAMPLE_STRIDE = 20
STICK_SCAN = (0.0, 0.25, 0.4, 0.5, APDRONE_REFERENCE_THROTTLE, 0.6, 0.75, 1.0)


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def finite_or_blank(value: str | int | float) -> str | int | float:
    if isinstance(value, float) and not math.isfinite(value):
        return ""
    return value


def parse_float(raw: str | None, scale: float = 1.0) -> float:
    try:
        return float(raw) / scale  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return float("nan")


def parse_int(raw: str | None, default: int = -1) -> int:
    try:
        return int(float(raw))  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return default


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


def project_direct_thrust_fraction(throttle_fraction: float) -> float:
    if not math.isfinite(throttle_fraction):
        return float("nan")
    throttle = max(0.0, min(1.0, throttle_fraction))
    return max(0.0, min(1.0, throttle**APDRONE_THROTTLE_EXPONENT))


def project_total_thrust_n(throttle_fraction: float) -> float:
    return project_direct_thrust_fraction(throttle_fraction) * APDRONE_TOTAL_MAX_THRUST_N


def project_rpm(throttle_fraction: float) -> float:
    fraction = project_direct_thrust_fraction(throttle_fraction)
    return math.sqrt(fraction) * APDRONE_MAX_RPM if math.isfinite(fraction) else float("nan")


def betaflight_thrust_linear_compensation(throttle_fraction: float, thrust_linear_percent: float) -> float:
    throttle = max(0.0, min(1.0, throttle_fraction))
    tl = max(0.0, thrust_linear_percent / 100.0)
    if tl <= 0.0:
        return throttle
    amount = tl - 0.5 * tl * tl
    return throttle / (1.0 + amount * (1.0 - throttle) ** 2)


def betaflight_thrust_linear_motor_output(motor_output_fraction: float, thrust_linear_percent: float) -> float:
    motor = max(0.0, min(1.0, motor_output_fraction))
    tl = max(0.0, thrust_linear_percent / 100.0)
    if tl <= 0.0:
        return motor
    return motor * (1.0 + tl * (1.0 - motor) ** 2)


def betaflight_zero_mix_after_tl(throttle_fraction: float, thrust_linear_percent: float) -> float:
    compensated = betaflight_thrust_linear_compensation(throttle_fraction, thrust_linear_percent)
    return betaflight_thrust_linear_motor_output(compensated, thrust_linear_percent)


def throttle_limit_type_name(raw: str | None) -> str:
    return {
        0: "OFF",
        1: "SCALE",
        2: "CLIP",
    }.get(parse_int(raw), "unknown")


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


def motor_output_limits(header: dict[str, str]) -> tuple[float, float]:
    raw = header.get("motorOutput", "")
    parts = [parse_float(part.strip()) for part in raw.split(",") if part.strip()]
    if len(parts) >= 2 and math.isfinite(parts[0]) and math.isfinite(parts[1]) and parts[1] > parts[0]:
        return parts[0], parts[1]
    return float("nan"), float("nan")


def normalize_motor_output(raw_motor: float, low: float, high: float) -> float:
    if not (math.isfinite(raw_motor) and math.isfinite(low) and math.isfinite(high) and high > low):
        return float("nan")
    return (raw_motor - low) / (high - low)


def erpm100_to_mechanical_rpm(raw: float, motor_poles: float) -> float:
    if not (math.isfinite(raw) and raw > 0.0 and math.isfinite(motor_poles) and motor_poles > 0.0):
        return float("nan")
    return raw * 100.0 * 2.0 / motor_poles


@dataclass
class LogStats:
    source_files: set[str] = field(default_factory=set)
    sample_count: int = 0
    sampled_count: int = 0
    duration_s: float = 0.0
    first_time_s: float = float("nan")
    last_time_s: float = float("nan")
    sum_throttle_fraction: float = 0.0
    sum_setpoint_throttle_fraction: float = 0.0
    sum_project_thrust_fraction: float = 0.0
    sum_project_twr: float = 0.0
    max_throttle_fraction: float = 0.0
    sampled_throttle_fraction: list[float] = field(default_factory=list)
    sampled_project_thrust_fraction: list[float] = field(default_factory=list)
    sampled_project_twr: list[float] = field(default_factory=list)
    sampled_project_rpm: list[float] = field(default_factory=list)
    sampled_bf_zero_mix_after_tl: list[float] = field(default_factory=list)
    sampled_pre_limit_scale_estimate: list[float] = field(default_factory=list)
    rc_setpoint_match_count: int = 0
    motor_sample_count: int = 0
    sampled_motor_avg_normalized: list[float] = field(default_factory=list)
    sampled_motor_max_normalized: list[float] = field(default_factory=list)
    sampled_motor_min_normalized: list[float] = field(default_factory=list)
    erpm_sample_count: int = 0
    sampled_mechanical_rpm_avg: list[float] = field(default_factory=list)
    sampled_mechanical_rpm_max: list[float] = field(default_factory=list)
    sampled_mechanical_rpm_over_project_rpm: list[float] = field(default_factory=list)

    def add_time(self, time_s: float) -> None:
        if not math.isfinite(time_s):
            return
        if not math.isfinite(self.first_time_s):
            self.first_time_s = time_s
        self.last_time_s = time_s

    def finish_duration(self) -> None:
        if math.isfinite(self.first_time_s) and math.isfinite(self.last_time_s):
            self.duration_s += max(0.0, self.last_time_s - self.first_time_s)

    def merge(self, other: "LogStats", local_source_file: str) -> None:
        self.source_files.add(local_source_file)
        self.sample_count += other.sample_count
        self.sampled_count += other.sampled_count
        self.duration_s += other.duration_s
        self.sum_throttle_fraction += other.sum_throttle_fraction
        self.sum_setpoint_throttle_fraction += other.sum_setpoint_throttle_fraction
        self.sum_project_thrust_fraction += other.sum_project_thrust_fraction
        self.sum_project_twr += other.sum_project_twr
        self.max_throttle_fraction = max(self.max_throttle_fraction, other.max_throttle_fraction)
        self.sampled_throttle_fraction.extend(other.sampled_throttle_fraction)
        self.sampled_project_thrust_fraction.extend(other.sampled_project_thrust_fraction)
        self.sampled_project_twr.extend(other.sampled_project_twr)
        self.sampled_project_rpm.extend(other.sampled_project_rpm)
        self.sampled_bf_zero_mix_after_tl.extend(other.sampled_bf_zero_mix_after_tl)
        self.sampled_pre_limit_scale_estimate.extend(other.sampled_pre_limit_scale_estimate)
        self.rc_setpoint_match_count += other.rc_setpoint_match_count
        self.motor_sample_count += other.motor_sample_count
        self.sampled_motor_avg_normalized.extend(other.sampled_motor_avg_normalized)
        self.sampled_motor_max_normalized.extend(other.sampled_motor_max_normalized)
        self.sampled_motor_min_normalized.extend(other.sampled_motor_min_normalized)
        self.erpm_sample_count += other.erpm_sample_count
        self.sampled_mechanical_rpm_avg.extend(other.sampled_mechanical_rpm_avg)
        self.sampled_mechanical_rpm_max.extend(other.sampled_mechanical_rpm_max)
        self.sampled_mechanical_rpm_over_project_rpm.extend(other.sampled_mechanical_rpm_over_project_rpm)


def stats_to_metrics(stats: LogStats) -> dict[str, float | int]:
    sample_rate = (stats.sample_count - 1) / stats.duration_s if stats.duration_s > 0.0 and stats.sample_count > 1 else float("nan")
    mean_throttle = stats.sum_throttle_fraction / stats.sample_count if stats.sample_count else float("nan")
    mean_setpoint_throttle = stats.sum_setpoint_throttle_fraction / stats.sample_count if stats.sample_count else float("nan")
    mean_project_thrust_fraction = stats.sum_project_thrust_fraction / stats.sample_count if stats.sample_count else float("nan")
    mean_project_twr = stats.sum_project_twr / stats.sample_count if stats.sample_count else float("nan")
    return {
        "duration_s": stats.duration_s,
        "sample_count": stats.sample_count,
        "sampled_count_for_percentiles": stats.sampled_count,
        "sample_rate_hz": sample_rate,
        "throttle_fraction_mean": mean_throttle,
        "throttle_fraction_p50": percentile(stats.sampled_throttle_fraction, 0.50),
        "throttle_fraction_p95": percentile(stats.sampled_throttle_fraction, 0.95),
        "throttle_fraction_p99": percentile(stats.sampled_throttle_fraction, 0.99),
        "throttle_fraction_max_exact": stats.max_throttle_fraction,
        "setpoint_throttle_fraction_mean": mean_setpoint_throttle,
        "project_direct_thrust_fraction_mean": mean_project_thrust_fraction,
        "project_direct_thrust_fraction_p50": percentile(stats.sampled_project_thrust_fraction, 0.50),
        "project_direct_thrust_fraction_p95": percentile(stats.sampled_project_thrust_fraction, 0.95),
        "project_direct_thrust_fraction_p99": percentile(stats.sampled_project_thrust_fraction, 0.99),
        "project_total_thrust_n_mean": mean_project_thrust_fraction * APDRONE_TOTAL_MAX_THRUST_N if math.isfinite(mean_project_thrust_fraction) else float("nan"),
        "project_twr_mean": mean_project_twr,
        "project_twr_p50": percentile(stats.sampled_project_twr, 0.50),
        "project_twr_p95": percentile(stats.sampled_project_twr, 0.95),
        "project_twr_p99": percentile(stats.sampled_project_twr, 0.99),
        "project_rpm_p50": percentile(stats.sampled_project_rpm, 0.50),
        "project_rpm_p95": percentile(stats.sampled_project_rpm, 0.95),
        "betaflight_zero_mix_after_thrust_linear_p50": percentile(stats.sampled_bf_zero_mix_after_tl, 0.50),
        "betaflight_zero_mix_after_thrust_linear_p95": percentile(stats.sampled_bf_zero_mix_after_tl, 0.95),
        "estimated_pre_limit_stick_fraction_p50_if_scale": percentile(stats.sampled_pre_limit_scale_estimate, 0.50),
        "estimated_pre_limit_stick_fraction_p95_if_scale": percentile(stats.sampled_pre_limit_scale_estimate, 0.95),
        "rc_vs_setpoint_throttle_match_fraction": stats.rc_setpoint_match_count / stats.sample_count if stats.sample_count else float("nan"),
        "motor_sample_count": stats.motor_sample_count,
        "motor_avg_normalized_p50": percentile(stats.sampled_motor_avg_normalized, 0.50),
        "motor_avg_normalized_p95": percentile(stats.sampled_motor_avg_normalized, 0.95),
        "motor_min_normalized_p50": percentile(stats.sampled_motor_min_normalized, 0.50),
        "motor_max_normalized_p95": percentile(stats.sampled_motor_max_normalized, 0.95),
        "erpm_sample_count": stats.erpm_sample_count,
        "mechanical_rpm_avg_p50": percentile(stats.sampled_mechanical_rpm_avg, 0.50),
        "mechanical_rpm_avg_p95": percentile(stats.sampled_mechanical_rpm_avg, 0.95),
        "mechanical_rpm_max_p95": percentile(stats.sampled_mechanical_rpm_max, 0.95),
        "mechanical_rpm_avg_over_project_rpm_p50": percentile(stats.sampled_mechanical_rpm_over_project_rpm, 0.50),
        "mechanical_rpm_avg_over_project_rpm_p95": percentile(stats.sampled_mechanical_rpm_over_project_rpm, 0.95),
    }


def read_log_stats(path: Path) -> tuple[dict[str, str], LogStats]:
    header_line = find_header_line(path)
    stats = LogStats()
    if header_line is None:
        return {}, stats
    header = read_blackbox_header(path)
    thrust_linear = parse_float(header.get("thrust_linear"), 1.0)
    throttle_limit_percent = parse_float(header.get("throttle_limit_percent"))
    throttle_limit_factor = throttle_limit_percent / 100.0 if math.isfinite(throttle_limit_percent) and throttle_limit_percent > 0.0 else float("nan")
    motor_low, motor_high = motor_output_limits(header)
    motor_poles = parse_float(header.get("motor_poles"))
    with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
        for _ in range(header_line):
            next(handle)
        reader = csv.reader(handle)
        try:
            fieldnames = next(reader)
        except StopIteration:
            return header, stats
        index = {name: i for i, name in enumerate(fieldnames)}
        throttle_field = "rcCommands[3]" if "rcCommands[3]" in index else "rcCommand[3]" if "rcCommand[3]" in index else ""
        if not throttle_field or "time" not in index:
            return header, stats
        required = ["time", throttle_field]
        if "setpoint[3]" in index:
            required.append("setpoint[3]")
        motor_fields = [f"motor[{i}]" for i in range(4) if f"motor[{i}]" in index]
        erpm_fields = [f"eRPM[{i}]" for i in range(4) if f"eRPM[{i}]" in index]
        max_index = max(index[name] for name in required + motor_fields + erpm_fields)
        for row_number, row in enumerate(reader, start=1):
            if len(row) <= max_index:
                continue
            time_s = parse_float(row[index["time"]], 1.0e6)
            stats.add_time(time_s)
            if throttle_field == "rcCommands[3]":
                throttle_fraction = parse_float(row[index[throttle_field]], 100.0)
            else:
                throttle_fraction = (parse_float(row[index[throttle_field]]) - 1000.0) / 1000.0
            if not math.isfinite(throttle_fraction):
                continue
            throttle_fraction = max(0.0, min(1.0, throttle_fraction))
            setpoint_throttle_fraction = parse_float(row[index["setpoint[3]"]], 1000.0) if "setpoint[3]" in index else float("nan")
            project_fraction = project_direct_thrust_fraction(throttle_fraction)
            project_twr = project_total_thrust_n(throttle_fraction) / (APDRONE_MASS_KG * G0)
            project_rpm_value = project_rpm(throttle_fraction)
            stats.sample_count += 1
            stats.sum_throttle_fraction += throttle_fraction
            stats.sum_setpoint_throttle_fraction += setpoint_throttle_fraction if math.isfinite(setpoint_throttle_fraction) else 0.0
            stats.sum_project_thrust_fraction += project_fraction
            stats.sum_project_twr += project_twr
            stats.max_throttle_fraction = max(stats.max_throttle_fraction, throttle_fraction)
            if math.isfinite(setpoint_throttle_fraction) and abs(setpoint_throttle_fraction - throttle_fraction) <= 1.0e-9:
                stats.rc_setpoint_match_count += 1
            should_sample = row_number % PERCENTILE_SAMPLE_STRIDE == 0
            if should_sample:
                stats.sampled_count += 1
                stats.sampled_throttle_fraction.append(throttle_fraction)
                stats.sampled_project_thrust_fraction.append(project_fraction)
                stats.sampled_project_twr.append(project_twr)
                stats.sampled_project_rpm.append(project_rpm_value)
                stats.sampled_bf_zero_mix_after_tl.append(betaflight_zero_mix_after_tl(throttle_fraction, thrust_linear))
                if math.isfinite(throttle_limit_factor) and 0.0 < throttle_limit_factor < 1.0:
                    stats.sampled_pre_limit_scale_estimate.append(min(1.0, throttle_fraction / throttle_limit_factor))
                motor_values = [normalize_motor_output(parse_float(row[index[field]]), motor_low, motor_high) for field in motor_fields]
                motor_values = [value for value in motor_values if math.isfinite(value)]
                if motor_values:
                    stats.motor_sample_count += 1
                    stats.sampled_motor_avg_normalized.append(sum(motor_values) / len(motor_values))
                    stats.sampled_motor_min_normalized.append(min(motor_values))
                    stats.sampled_motor_max_normalized.append(max(motor_values))
                rpm_values = [erpm100_to_mechanical_rpm(parse_float(row[index[field]]), motor_poles) for field in erpm_fields]
                rpm_values = [value for value in rpm_values if math.isfinite(value)]
                if rpm_values:
                    stats.erpm_sample_count += 1
                    avg_rpm = sum(rpm_values) / len(rpm_values)
                    stats.sampled_mechanical_rpm_avg.append(avg_rpm)
                    stats.sampled_mechanical_rpm_max.append(max(rpm_values))
                    if math.isfinite(project_rpm_value) and project_rpm_value > 1.0:
                        stats.sampled_mechanical_rpm_over_project_rpm.append(avg_rpm / project_rpm_value)
    stats.finish_duration()
    return header, stats


def rows_for_references() -> list[dict[str, str | int | float]]:
    rows: list[dict[str, str | int | float]] = [
        {
            "row_type": "apdrone_throttle_curve_method",
            "source_page": SOURCE_PAGE,
            "doi": DOI,
            "local_source_file": repo_path(RAW),
            "percentile_sample_stride": PERCENTILE_SAMPLE_STRIDE,
            "note": "Percentiles use deterministic every-N-row samples; maxima, row counts, and durations are exact.",
        },
        {
            "row_type": "reference_formula",
            "source_page": BETAFLIGHT_45_RC_SOURCE_URL,
            "name": "Betaflight 4.5 throttle rcCommand",
            "formula": "rcCommand[THROTTLE]=rcLookupThrottle(tmp), where tmp maps stick mincheck..2000 to 0..1000 before mixer normalization.",
            "note": "APdrone CSV exports rcCommands[3] as percent and setpoint[3] as per-mille throttle; they match after /100 and /1000 scaling.",
        },
        {
            "row_type": "reference_formula",
            "source_page": BETAFLIGHT_45_MIXER_SOURCE_URL,
            "name": "Betaflight 4.5 throttle limit",
            "formula": "SCALE returns throttle*percent/100; CLIP returns min(throttle, percent/100); OFF leaves throttle unchanged.",
            "note": "The logged mixer throttle used for anti-gravity/TPA reflects this limit before thrust-linearization compensation.",
        },
        {
            "row_type": "reference_formula",
            "source_page": BETAFLIGHT_45_PID_SOURCE_URL,
            "name": "Betaflight 4.5 thrust linearization apply",
            "formula": "motorOutput *= 1 + thrustLinearization * (1 - motorOutput)^2",
            "note": "Betaflight applies this to normalized motor output after mixing.",
        },
        {
            "row_type": "reference_formula",
            "source_page": BETAFLIGHT_45_PID_INIT_SOURCE_URL,
            "name": "Betaflight 4.5 thrust linearization compensation",
            "formula": "throttleCompensateAmount = TL - 0.5*TL^2; throttle /= 1 + throttleCompensateAmount*(1-throttle)^2",
            "note": "This compensation is applied to throttle before motor mixing to keep zero-mix throttle approximately consistent.",
        },
        {
            "row_type": "current_project_apDrone_throttle_model",
            "source_page": repo_path(DRONE_CONFIG),
            "current_apDrone_mass_kg": APDRONE_MASS_KG,
            "current_apDrone_total_max_thrust_n": APDRONE_TOTAL_MAX_THRUST_N,
            "current_apDrone_hover_direct_thrust_fraction": APDRONE_HOVER_THRUST_FRACTION,
            "current_apDrone_reference_throttle": APDRONE_REFERENCE_THROTTLE,
            "current_apDrone_throttle_curve_exponent": APDRONE_THROTTLE_EXPONENT,
            "current_apDrone_max_rpm": APDRONE_MAX_RPM,
            "formula": "directThrustFraction = throttleCommand ^ throttleCommandCurveExponent",
            "note": "The current exponent is chosen so the normal-power reference throttle maps to hover thrust.",
        },
    ]
    for throttle in STICK_SCAN:
        rows.append(
            {
                "row_type": "current_project_apDrone_throttle_scan",
                "source_page": repo_path(DRONE_CONFIG),
                "throttle_fraction": throttle,
                "current_apDrone_project_direct_thrust_fraction": project_direct_thrust_fraction(throttle),
                "current_apDrone_project_total_thrust_n": project_total_thrust_n(throttle),
                "current_apDrone_project_twr": project_total_thrust_n(throttle) / (APDRONE_MASS_KG * G0),
                "current_apDrone_project_rpm": project_rpm(throttle),
            }
        )
    for thrust_linear in (20.0, 30.0, 40.0):
        for throttle in STICK_SCAN:
            rows.append(
                {
                    "row_type": "betaflight_thrust_linearization_scan",
                    "source_page": BETAFLIGHT_45_PID_SOURCE_URL,
                    "throttle_fraction": throttle,
                    "thrust_linear_percent": thrust_linear,
                    "betaflight_compensated_throttle_before_mix": betaflight_thrust_linear_compensation(throttle, thrust_linear),
                    "betaflight_zero_mix_output_after_thrust_linear": betaflight_zero_mix_after_tl(throttle, thrust_linear),
                }
            )
    return rows


def rows_for_logs() -> list[dict[str, str | int | float]]:
    rows: list[dict[str, str | int | float]] = []
    aggregates: dict[str, LogStats] = {name: LogStats() for name in ("selected_flight", "open_field", "urban_environment", "battery_max_power", "battery_normal_power", "all_archives_excluding_selected", "all_logs_including_selected")}
    profile_stats: dict[tuple[str, str, str], LogStats] = {}
    for path in apdrone_csv_paths():
        header, stats = read_log_stats(path)
        if stats.sample_count == 0:
            continue
        scenario = infer_scenario(path)
        local_source_file = repo_path(path)
        throttle_limit_type = header.get("throttle_limit_type", "")
        throttle_limit_percent = header.get("throttle_limit_percent", "")
        thrust_linear = header.get("thrust_linear", "")
        motor_low, motor_high = motor_output_limits(header)
        row: dict[str, str | int | float] = {
            "row_type": "apdrone_throttle_log_file",
            "source_page": SOURCE_PAGE,
            "doi": DOI,
            "scenario": scenario,
            "flight_filename": path.name,
            "local_source_file": local_source_file,
            "firmware_version": header.get("firmwareVersion", ""),
            "throttle_limit_type": throttle_limit_type,
            "throttle_limit_type_name": throttle_limit_type_name(throttle_limit_type),
            "throttle_limit_percent": parse_float(throttle_limit_percent),
            "thrust_linear_percent": parse_float(thrust_linear),
            "thr_mid": parse_float(header.get("thrMid")),
            "thr_expo": parse_float(header.get("thrExpo")),
            "motor_output_low": motor_low,
            "motor_output_high": motor_high,
            "motor_poles": parse_float(header.get("motor_poles")),
            "dshot_bidir": parse_float(header.get("dshot_bidir")),
            **stats_to_metrics(stats),
            "note": "rcCommands[3]/100 and setpoint[3]/1000 are treated as normalized throttle after Betaflight throttle limit.",
        }
        rows.append(row)
        aggregates[scenario].merge(stats, local_source_file)
        if scenario != "selected_flight":
            aggregates["all_archives_excluding_selected"].merge(stats, local_source_file)
        aggregates["all_logs_including_selected"].merge(stats, local_source_file)
        profile_key = (throttle_limit_type_name(throttle_limit_type), throttle_limit_percent, thrust_linear)
        profile_stats.setdefault(profile_key, LogStats()).merge(stats, local_source_file)

    for scenario, stats in sorted(aggregates.items()):
        if stats.sample_count == 0:
            continue
        rows.append(
            {
                "row_type": "apdrone_throttle_log_scenario_summary",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "scenario": scenario,
                "source_file_count": len(stats.source_files),
                **stats_to_metrics(stats),
                "note": "Scenario summaries combine sampled percentiles across files; all_archives_excluding_selected avoids double-counting the selected-flight/open-field duplicate.",
            }
        )
    for (limit_type_name, limit_percent, thrust_linear), stats in sorted(profile_stats.items()):
        rows.append(
            {
                "row_type": "apdrone_throttle_log_profile_summary",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "throttle_limit_type_name": limit_type_name,
                "throttle_limit_percent": parse_float(limit_percent),
                "thrust_linear_percent": parse_float(thrust_linear),
                "source_file_count": len(stats.source_files),
                **stats_to_metrics(stats),
            }
        )
    return rows


def write_rows(rows: list[dict[str, str | int | float]]) -> None:
    fieldnames: list[str] = []
    for row in rows:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    normalized = [{key: finite_or_blank(value) for key, value in row.items()} for row in rows]
    with OUTPUT.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(normalized)


def main() -> None:
    rows = rows_for_references()
    rows.extend(rows_for_logs())
    write_rows(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")


if __name__ == "__main__":
    main()
