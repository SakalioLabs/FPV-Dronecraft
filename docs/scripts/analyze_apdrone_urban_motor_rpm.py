"""Analyze APdrone urban motor command and eRPM telemetry.

Outputs:
  docs/data/apdrone_urban_motor_rpm_reference.csv

The APdrone urban Blackbox CSVs include motor[0..3] and eRPM[0..3].
This script converts Betaflight eRPM/100 to mechanical RPM with the log's
motor_poles header, compares motor command to measured RPM and effective KV,
and estimates command-to-RPM lag from downsampled motor/RPM changes.
"""

from __future__ import annotations

import csv
import math
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "apdrone_zgsvdtxnfh_v2"
URBAN_DIR = RAW / "flight_archives" / "urban_environment" / "Flight Data in Urban Environment"
OUTPUT = DATA / "apdrone_urban_motor_rpm_reference.csv"

SOURCE_PAGE = "https://data.mendeley.com/datasets/zgsvdtxnfh/2"
DOI = "10.17632/zgsvdtxnfh.2"
BETAFLIGHT_45_BLACKBOX_SOURCE_URL = "https://raw.githubusercontent.com/betaflight/betaflight/4.5.0/src/main/blackbox/blackbox.c"
BETAFLIGHT_45_MIXER_SOURCE_URL = "https://raw.githubusercontent.com/betaflight/betaflight/4.5.0/src/main/flight/mixer.c"
DRONE_CONFIG = ROOT / "drone-sim-core" / "src" / "main" / "java" / "com" / "tenicana" / "dronecraft" / "sim" / "DroneConfig.java"

APDRONE_MASS_KG = 0.6284
G0 = 9.80665
APDRONE_MAX_ROTOR_THRUST_N = 13.5
APDRONE_ROTOR_COUNT = 4
APDRONE_TOTAL_MAX_THRUST_N = APDRONE_MAX_ROTOR_THRUST_N * APDRONE_ROTOR_COUNT
APDRONE_THRUST_COEFF_N_PER_RAD_S2 = 1.3918976015517363e-6
APDRONE_MAX_RPM = math.sqrt(APDRONE_MAX_ROTOR_THRUST_N / APDRONE_THRUST_COEFF_N_PER_RAD_S2) * 60.0 / (2.0 * math.pi)
APDRONE_HOVER_RPM = math.sqrt((APDRONE_MASS_KG * G0 / APDRONE_ROTOR_COUNT) / APDRONE_THRUST_COEFF_N_PER_RAD_S2) * 60.0 / (2.0 * math.pi)

STATIC_SAMPLE_STRIDE = 20
DYNAMIC_SAMPLE_STRIDE = 8
MAX_LAG_SECONDS = 0.080
MIN_CORR_SAMPLES = 200


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


def stddev(values: list[float]) -> float:
    clean = [value for value in values if math.isfinite(value)]
    if len(clean) < 2:
        return float("nan")
    center = mean(clean)
    return math.sqrt(sum((value - center) ** 2 for value in clean) / len(clean))


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


def urban_csv_paths() -> list[Path]:
    return sorted(URBAN_DIR.glob("*.csv"))


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


def rpm_to_thrust_n(rpm: float) -> float:
    omega = rpm * 2.0 * math.pi / 60.0
    return APDRONE_THRUST_COEFF_N_PER_RAD_S2 * omega * omega


def sample_rate_hz(times: list[float]) -> float:
    if len(times) < 3:
        return float("nan")
    arr = np.asarray(times, dtype=np.float64)
    dt = np.diff(arr)
    dt = dt[np.isfinite(dt) & (dt > 0.0)]
    if len(dt) == 0:
        return float("nan")
    return 1.0 / float(np.median(dt))


def linear_fit(x_values: list[float], y_values: list[float]) -> dict[str, float | int]:
    pairs = [
        (x, y)
        for x, y in zip(x_values, y_values)
        if math.isfinite(x) and math.isfinite(y) and x > 0.03 and y > 500.0
    ]
    if len(pairs) < 20:
        return {
            "linear_fit_count": len(pairs),
            "linear_fit_slope_rpm_per_norm": float("nan"),
            "linear_fit_intercept_rpm": float("nan"),
            "linear_fit_r2": float("nan"),
            "linear_fit_rmse_rpm": float("nan"),
            "linear_fit_rpm_at_norm_1": float("nan"),
            "linear_fit_norm_at_hover_rpm": float("nan"),
        }
    x = np.asarray([pair[0] for pair in pairs], dtype=np.float64)
    y = np.asarray([pair[1] for pair in pairs], dtype=np.float64)
    A = np.vstack([x, np.ones_like(x)]).T
    slope, intercept = np.linalg.lstsq(A, y, rcond=None)[0]
    pred = slope * x + intercept
    residual = y - pred
    ss_res = float(np.dot(residual, residual))
    centered = y - float(y.mean())
    ss_tot = float(np.dot(centered, centered))
    r2 = 1.0 - ss_res / ss_tot if ss_tot > 0.0 else float("nan")
    rmse = math.sqrt(ss_res / len(y))
    rpm_at_norm_1 = float(slope + intercept)
    norm_at_hover = (APDRONE_HOVER_RPM - intercept) / slope if abs(float(slope)) > 1.0e-9 else float("nan")
    return {
        "linear_fit_count": len(pairs),
        "linear_fit_slope_rpm_per_norm": float(slope),
        "linear_fit_intercept_rpm": float(intercept),
        "linear_fit_r2": r2,
        "linear_fit_rmse_rpm": rmse,
        "linear_fit_rpm_at_norm_1": rpm_at_norm_1,
        "linear_fit_norm_at_hover_rpm": float(norm_at_hover),
    }


def power_fit(x_values: list[float], y_values: list[float]) -> dict[str, float | int]:
    pairs = [
        (x, y / APDRONE_MAX_RPM)
        for x, y in zip(x_values, y_values)
        if math.isfinite(x) and math.isfinite(y) and x > 0.03 and y > 500.0
    ]
    if len(pairs) < 20:
        return {
            "power_fit_count": len(pairs),
            "power_fit_scale_rpm_fraction_at_norm_1": float("nan"),
            "power_fit_exponent": float("nan"),
            "power_fit_r2_log": float("nan"),
            "power_fit_rmse_rpm_fraction": float("nan"),
        }
    x = np.asarray([math.log(pair[0]) for pair in pairs], dtype=np.float64)
    y = np.asarray([math.log(pair[1]) for pair in pairs], dtype=np.float64)
    A = np.vstack([x, np.ones_like(x)]).T
    exponent, log_scale = np.linalg.lstsq(A, y, rcond=None)[0]
    pred_log = exponent * x + log_scale
    residual_log = y - pred_log
    ss_res = float(np.dot(residual_log, residual_log))
    centered = y - float(y.mean())
    ss_tot = float(np.dot(centered, centered))
    r2 = 1.0 - ss_res / ss_tot if ss_tot > 0.0 else float("nan")
    scale = math.exp(float(log_scale))
    y_linear = np.asarray([pair[1] for pair in pairs], dtype=np.float64)
    pred_linear = scale * np.asarray([pair[0] for pair in pairs], dtype=np.float64) ** float(exponent)
    rmse = math.sqrt(float(np.dot(y_linear - pred_linear, y_linear - pred_linear)) / len(y_linear))
    return {
        "power_fit_count": len(pairs),
        "power_fit_scale_rpm_fraction_at_norm_1": scale,
        "power_fit_exponent": float(exponent),
        "power_fit_r2_log": r2,
        "power_fit_rmse_rpm_fraction": rmse,
    }


def shifted_pair(x: np.ndarray, y: np.ndarray, lag_samples: int) -> tuple[np.ndarray, np.ndarray]:
    if lag_samples > 0:
        return x[:-lag_samples], y[lag_samples:]
    if lag_samples < 0:
        lag = -lag_samples
        return x[lag:], y[:-lag]
    return x, y


def correlation(x: np.ndarray, y: np.ndarray, mask: np.ndarray) -> tuple[float, int]:
    selected = mask & np.isfinite(x) & np.isfinite(y)
    count = int(np.count_nonzero(selected))
    if count < MIN_CORR_SAMPLES:
        return float("nan"), count
    xs = x[selected] - float(np.mean(x[selected]))
    ys = y[selected] - float(np.mean(y[selected]))
    xv = float(np.dot(xs, xs))
    yv = float(np.dot(ys, ys))
    if xv <= 1.0e-12 or yv <= 1.0e-12:
        return float("nan"), count
    return float(np.dot(xs, ys) / math.sqrt(xv * yv)), count


def best_lag(times: list[float], motor_norm: list[float], rpm: list[float]) -> dict[str, float | int]:
    if len(times) < MIN_CORR_SAMPLES:
        return {
            "dynamic_sample_rate_hz": float("nan"),
            "best_level_lag_ms": float("nan"),
            "best_level_correlation": float("nan"),
            "best_delta_lag_ms": float("nan"),
            "best_delta_correlation": float("nan"),
            "best_delta_pair_count": 0,
        }
    rate = sample_rate_hz(times)
    if not math.isfinite(rate) or rate <= 0.0:
        return {
            "dynamic_sample_rate_hz": float("nan"),
            "best_level_lag_ms": float("nan"),
            "best_level_correlation": float("nan"),
            "best_delta_lag_ms": float("nan"),
            "best_delta_correlation": float("nan"),
            "best_delta_pair_count": 0,
        }
    x = np.asarray(motor_norm, dtype=np.float64)
    y = np.asarray(rpm, dtype=np.float64)
    level_active = (x > 0.03) & (y > 500.0)
    dx = np.diff(x)
    dy = np.diff(y)
    delta_active = (np.abs(dx) > 0.0025) | (np.abs(dy) > 40.0)
    max_lag_samples = max(1, int(round(MAX_LAG_SECONDS * rate)))
    best_level = (float("nan"), 0, 0)
    best_delta = (float("nan"), 0, 0)
    best_level_abs = -1.0
    best_delta_abs = -1.0
    for lag in range(-max_lag_samples, max_lag_samples + 1):
        xs, ys = shifted_pair(x, y, lag)
        mask_level = shifted_pair(level_active.astype(float), level_active.astype(float), lag)[0] > 0.5
        corr_level, level_count = correlation(xs, ys, mask_level)
        if math.isfinite(corr_level) and abs(corr_level) > best_level_abs:
            best_level_abs = abs(corr_level)
            best_level = (corr_level, lag, level_count)
        dxs, dys = shifted_pair(dx, dy, lag)
        mask_delta = shifted_pair(delta_active.astype(float), delta_active.astype(float), lag)[0] > 0.5
        corr_delta, delta_count = correlation(dxs, dys, mask_delta)
        if math.isfinite(corr_delta) and abs(corr_delta) > best_delta_abs:
            best_delta_abs = abs(corr_delta)
            best_delta = (corr_delta, lag, delta_count)
    return {
        "dynamic_sample_rate_hz": rate,
        "best_level_lag_ms": 1000.0 * best_level[1] / rate,
        "best_level_correlation": best_level[0],
        "best_level_pair_count": best_level[2],
        "best_delta_lag_ms": 1000.0 * best_delta[1] / rate,
        "best_delta_correlation": best_delta[0],
        "best_delta_pair_count": best_delta[2],
    }


def tau_metrics(times: list[float], motor_norm: list[float], rpm: list[float], fit: dict[str, float | int]) -> dict[str, float | int]:
    slope = float(fit.get("linear_fit_slope_rpm_per_norm", float("nan")))
    intercept = float(fit.get("linear_fit_intercept_rpm", float("nan")))
    if not (math.isfinite(slope) and math.isfinite(intercept)):
        return {"first_order_tau_sample_count": 0, "first_order_tau_p50_ms": float("nan"), "first_order_tau_p10_ms": float("nan"), "first_order_tau_p90_ms": float("nan")}
    t = np.asarray(times, dtype=np.float64)
    x = np.asarray(motor_norm, dtype=np.float64)
    y = np.asarray(rpm, dtype=np.float64)
    if len(t) < 3:
        return {"first_order_tau_sample_count": 0, "first_order_tau_p50_ms": float("nan"), "first_order_tau_p10_ms": float("nan"), "first_order_tau_p90_ms": float("nan")}
    dt = np.diff(t)
    dy = np.diff(y)
    valid_step = dt > 0.0
    target = slope * x[:-1] + intercept
    error = target - y[:-1]
    rpm_dot = np.full_like(dy, np.nan)
    rpm_dot[valid_step] = dy[valid_step] / dt[valid_step]
    candidates = []
    for err, derivative, step_dt in zip(error, rpm_dot, dt):
        if not (math.isfinite(err) and math.isfinite(derivative) and math.isfinite(step_dt) and step_dt > 0.0):
            continue
        if abs(err) < 150.0 or abs(derivative) < 500.0:
            continue
        tau = err / derivative
        if 0.002 <= tau <= 0.500:
            candidates.append(tau)
    return {
        "first_order_tau_sample_count": len(candidates),
        "first_order_tau_p10_ms": percentile(candidates, 0.10) * 1000.0,
        "first_order_tau_p50_ms": percentile(candidates, 0.50) * 1000.0,
        "first_order_tau_p90_ms": percentile(candidates, 0.90) * 1000.0,
    }


@dataclass
class MotorStats:
    sample_count: int = 0
    valid_count: int = 0
    sampled_count: int = 0
    time_first_s: float = float("nan")
    time_last_s: float = float("nan")
    motor_norm: list[float] = field(default_factory=list)
    rpm: list[float] = field(default_factory=list)
    rpm_fraction: list[float] = field(default_factory=list)
    effective_kv: list[float] = field(default_factory=list)
    rpm_over_linear_command: list[float] = field(default_factory=list)
    rpm_over_thrust_command: list[float] = field(default_factory=list)
    inferred_thrust_n: list[float] = field(default_factory=list)
    inferred_twr_per_motor_scaled_to_quad: list[float] = field(default_factory=list)
    dynamic_time_s: list[float] = field(default_factory=list)
    dynamic_motor_norm: list[float] = field(default_factory=list)
    dynamic_rpm: list[float] = field(default_factory=list)

    def add_time(self, time_s: float) -> None:
        if not math.isfinite(time_s):
            return
        if not math.isfinite(self.time_first_s):
            self.time_first_s = time_s
        self.time_last_s = time_s

    def add_valid(self, time_s: float, norm: float, rpm_value: float, vbat_v: float, row_number: int) -> None:
        self.valid_count += 1
        if row_number % STATIC_SAMPLE_STRIDE == 0:
            self.sampled_count += 1
            self.motor_norm.append(norm)
            self.rpm.append(rpm_value)
            rpm_fraction = rpm_value / APDRONE_MAX_RPM
            self.rpm_fraction.append(rpm_fraction)
            thrust_n = rpm_to_thrust_n(rpm_value)
            self.inferred_thrust_n.append(thrust_n)
            self.inferred_twr_per_motor_scaled_to_quad.append((thrust_n * APDRONE_ROTOR_COUNT) / (APDRONE_MASS_KG * G0))
            if math.isfinite(vbat_v) and vbat_v > 1.0 and norm > 0.03:
                self.effective_kv.append(rpm_value / (vbat_v * norm))
            if norm > 0.03:
                self.rpm_over_linear_command.append(rpm_value / (APDRONE_MAX_RPM * norm))
                self.rpm_over_thrust_command.append(rpm_value / (APDRONE_MAX_RPM * math.sqrt(norm)))
        if row_number % DYNAMIC_SAMPLE_STRIDE == 0:
            self.dynamic_time_s.append(time_s)
            self.dynamic_motor_norm.append(norm)
            self.dynamic_rpm.append(rpm_value)

    def merge(self, other: "MotorStats") -> None:
        self.sample_count += other.sample_count
        self.valid_count += other.valid_count
        self.sampled_count += other.sampled_count
        if not math.isfinite(self.time_first_s) or (math.isfinite(other.time_first_s) and other.time_first_s < self.time_first_s):
            self.time_first_s = other.time_first_s
        if not math.isfinite(self.time_last_s) or (math.isfinite(other.time_last_s) and other.time_last_s > self.time_last_s):
            self.time_last_s = other.time_last_s
        self.motor_norm.extend(other.motor_norm)
        self.rpm.extend(other.rpm)
        self.rpm_fraction.extend(other.rpm_fraction)
        self.effective_kv.extend(other.effective_kv)
        self.rpm_over_linear_command.extend(other.rpm_over_linear_command)
        self.rpm_over_thrust_command.extend(other.rpm_over_thrust_command)
        self.inferred_thrust_n.extend(other.inferred_thrust_n)
        self.inferred_twr_per_motor_scaled_to_quad.extend(other.inferred_twr_per_motor_scaled_to_quad)


def stats_metrics(stats: MotorStats) -> dict[str, float | int]:
    duration = max(0.0, stats.time_last_s - stats.time_first_s) if math.isfinite(stats.time_first_s) and math.isfinite(stats.time_last_s) else float("nan")
    sample_rate = (stats.sample_count - 1) / duration if math.isfinite(duration) and duration > 0.0 and stats.sample_count > 1 else float("nan")
    valid_fraction = stats.valid_count / stats.sample_count if stats.sample_count else float("nan")
    fit = linear_fit(stats.motor_norm, stats.rpm)
    power = power_fit(stats.motor_norm, stats.rpm)
    lag = best_lag(stats.dynamic_time_s, stats.dynamic_motor_norm, stats.dynamic_rpm)
    tau = tau_metrics(stats.dynamic_time_s, stats.dynamic_motor_norm, stats.dynamic_rpm, fit)
    return {
        "duration_s": duration,
        "sample_count": stats.sample_count,
        "valid_erpm_sample_count": stats.valid_count,
        "valid_erpm_fraction": valid_fraction,
        "sampled_count_for_percentiles": stats.sampled_count,
        "estimated_sample_rate_hz": sample_rate,
        "motor_norm_p50": percentile(stats.motor_norm, 0.50),
        "motor_norm_p95": percentile(stats.motor_norm, 0.95),
        "motor_norm_p99": percentile(stats.motor_norm, 0.99),
        "mechanical_rpm_p50": percentile(stats.rpm, 0.50),
        "mechanical_rpm_p95": percentile(stats.rpm, 0.95),
        "mechanical_rpm_p99": percentile(stats.rpm, 0.99),
        "mechanical_rpm_max_sampled": max(stats.rpm) if stats.rpm else float("nan"),
        "rpm_fraction_of_current_max_p50": percentile(stats.rpm_fraction, 0.50),
        "rpm_fraction_of_current_max_p95": percentile(stats.rpm_fraction, 0.95),
        "rpm_over_current_hover_p50": percentile([value / APDRONE_HOVER_RPM for value in stats.rpm], 0.50),
        "rpm_over_current_hover_p95": percentile([value / APDRONE_HOVER_RPM for value in stats.rpm], 0.95),
        "effective_kv_rpm_per_v_p50": percentile(stats.effective_kv, 0.50),
        "effective_kv_rpm_per_v_p95": percentile(stats.effective_kv, 0.95),
        "rpm_over_linear_motor_command_p50": percentile(stats.rpm_over_linear_command, 0.50),
        "rpm_over_linear_motor_command_p95": percentile(stats.rpm_over_linear_command, 0.95),
        "rpm_over_sqrt_motor_command_p50": percentile(stats.rpm_over_thrust_command, 0.50),
        "rpm_over_sqrt_motor_command_p95": percentile(stats.rpm_over_thrust_command, 0.95),
        "inferred_thrust_n_p50": percentile(stats.inferred_thrust_n, 0.50),
        "inferred_thrust_n_p95": percentile(stats.inferred_thrust_n, 0.95),
        "inferred_quad_twr_from_same_rpm_p50": percentile(stats.inferred_twr_per_motor_scaled_to_quad, 0.50),
        "inferred_quad_twr_from_same_rpm_p95": percentile(stats.inferred_twr_per_motor_scaled_to_quad, 0.95),
        **fit,
        **power,
        **lag,
        **tau,
    }


def rows_for_references() -> list[dict[str, str | int | float]]:
    return [
        {
            "row_type": "apdrone_urban_motor_rpm_method",
            "source_page": SOURCE_PAGE,
            "doi": DOI,
            "local_source_file": repo_path(URBAN_DIR),
            "static_sample_stride": STATIC_SAMPLE_STRIDE,
            "dynamic_sample_stride": DYNAMIC_SAMPLE_STRIDE,
            "max_lag_seconds": MAX_LAG_SECONDS,
            "note": "Static percentiles/fits use deterministic sampled rows; response lag uses downsampled command/RPM arrays.",
        },
        {
            "row_type": "reference_formula",
            "source_page": BETAFLIGHT_45_BLACKBOX_SOURCE_URL,
            "name": "Betaflight eRPM blackbox conversion",
            "formula": "mechanical_rpm = logged_eRPM100 * 100 * 2 / motor_poles",
            "note": "APdrone urban logs report motor_poles=14 and dshot_bidir=1.",
        },
        {
            "row_type": "reference_formula",
            "source_page": BETAFLIGHT_45_MIXER_SOURCE_URL,
            "name": "Betaflight motor command normalization",
            "formula": "normalized_motor_command = (motor[i] - motorOutputLow) / (motorOutputHigh - motorOutputLow)",
            "note": "APdrone urban headers report motorOutput=158,2047.",
        },
        {
            "row_type": "current_project_apDrone_rpm_model",
            "source_page": repo_path(DRONE_CONFIG),
            "current_apDrone_max_rotor_thrust_n": APDRONE_MAX_ROTOR_THRUST_N,
            "current_apDrone_thrust_coefficient": APDRONE_THRUST_COEFF_N_PER_RAD_S2,
            "current_apDrone_hover_rpm": APDRONE_HOVER_RPM,
            "current_apDrone_max_rpm": APDRONE_MAX_RPM,
            "current_apDrone_hover_logged_erpm100_14_pole": APDRONE_HOVER_RPM * 14.0 / 200.0,
            "current_apDrone_max_logged_erpm100_14_pole": APDRONE_MAX_RPM * 14.0 / 200.0,
            "formula": "T = k * omega^2; omega = rpm * 2*pi/60",
        },
    ]


def rows_for_file(path: Path) -> tuple[list[dict[str, str | int | float]], dict[int | str, MotorStats]]:
    header_line = find_header_line(path)
    if header_line is None:
        return [], {}
    header = read_blackbox_header(path)
    low, high = motor_output_limits(header)
    motor_poles = parse_float(header.get("motor_poles"))
    stats_by_motor: dict[int | str, MotorStats] = {motor: MotorStats() for motor in range(4)}
    stats_by_motor["all"] = MotorStats()

    with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
        for _ in range(header_line):
            next(handle)
        reader = csv.reader(handle)
        try:
            fieldnames = next(reader)
        except StopIteration:
            return [], {}
        index = {name: i for i, name in enumerate(fieldnames)}
        required = ["time", "vbatLatest"] + [f"motor[{i}]" for i in range(4)] + [f"eRPM[{i}]" for i in range(4)]
        if any(field not in index for field in required):
            return [], {}
        max_index = max(index[field] for field in required)
        for row_number, row in enumerate(reader, start=1):
            if len(row) <= max_index:
                continue
            time_s = parse_float(row[index["time"]], 1.0e6)
            vbat_v = parse_float(row[index["vbatLatest"]], 100.0)
            for motor in range(4):
                stats = stats_by_motor[motor]
                stats.sample_count += 1
                stats.add_time(time_s)
                raw_motor = parse_float(row[index[f"motor[{motor}]"]])
                raw_erpm = parse_float(row[index[f"eRPM[{motor}]"]])
                norm = normalize_motor_output(raw_motor, low, high)
                rpm_value = erpm100_to_mechanical_rpm(raw_erpm, motor_poles)
                if math.isfinite(norm) and math.isfinite(rpm_value):
                    stats.add_valid(time_s, norm, rpm_value, vbat_v, row_number)
                    stats_by_motor["all"].add_valid(time_s, norm, rpm_value, vbat_v, row_number)
                stats_by_motor["all"].sample_count += 1
                stats_by_motor["all"].add_time(time_s)

    rows: list[dict[str, str | int | float]] = []
    for motor, stats in stats_by_motor.items():
        metrics = stats_metrics(stats)
        rows.append(
            {
                "row_type": "apdrone_urban_motor_rpm_file_motor" if motor != "all" else "apdrone_urban_motor_rpm_file_all_motors",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "local_source_file": repo_path(path),
                "flight_filename": path.name,
                "motor_index": motor,
                "firmware_version": header.get("firmwareVersion", ""),
                "motor_output_low": low,
                "motor_output_high": high,
                "motor_poles": motor_poles,
                "dshot_bidir": parse_float(header.get("dshot_bidir")),
                "thrust_linear_percent": parse_float(header.get("thrust_linear")),
                **metrics,
                "note": "Positive lag means RPM follows motor command. Dynamic lag is a correlation diagnostic, not a controlled ESC step test.",
            }
        )
    return rows, stats_by_motor


def rows_for_all_files() -> list[dict[str, str | int | float]]:
    rows = rows_for_references()
    aggregate: dict[int | str, MotorStats] = {motor: MotorStats() for motor in range(4)}
    aggregate["all"] = MotorStats()
    file_count = 0
    for path in urban_csv_paths():
        file_rows, stats_by_motor = rows_for_file(path)
        if not file_rows:
            continue
        file_count += 1
        rows.extend(file_rows)
        for motor, stats in stats_by_motor.items():
            aggregate[motor].merge(stats)

    for motor, stats in aggregate.items():
        if stats.sample_count == 0:
            continue
        rows.append(
            {
                "row_type": "apdrone_urban_motor_rpm_summary_motor" if motor != "all" else "apdrone_urban_motor_rpm_summary_all_motors",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "local_source_file": repo_path(URBAN_DIR),
                "source_file_count": file_count,
                "motor_index": motor,
                **stats_metrics(stats),
                "note": "Aggregate static metrics combine sampled rows across all urban files. Dynamic lag is omitted for aggregate rows because files are not time-contiguous.",
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
    rows = rows_for_all_files()
    write_rows(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")


if __name__ == "__main__":
    main()
