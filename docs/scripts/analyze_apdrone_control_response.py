"""Estimate APdrone setpoint-to-gyro response lag from Blackbox CSV logs.

Outputs:
  docs/data/apdrone_control_response_reference.csv

This is a correlation-based response diagnostic, not a closed-loop system ID.
The APdrone Blackbox CSVs expose Betaflight setpoint[0..2] and gyroADC[0..2].
We downsample each log to roughly 500 Hz, scan +/-80 ms of lag, and record the
lag that maximizes setpoint/gyro correlation on active samples.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "apdrone_zgsvdtxnfh_v2"
OUTPUT = DATA / "apdrone_control_response_reference.csv"

SOURCE_PAGE = "https://data.mendeley.com/datasets/zgsvdtxnfh/2"
DOI = "10.17632/zgsvdtxnfh.2"

TARGET_RATE_HZ = 500.0
MAX_LAG_SECONDS = 0.080
MIN_DYNAMIC_SAMPLES = 250
ACTIVITY_THRESHOLD_DEG_S = 30.0
RELIABLE_CORRELATION_THRESHOLD = 0.35

# Current DroneConfig.apDrone() control/timing values.
APDRONE_CONTROL_LATENCY_S = 0.010
APDRONE_RC_SMOOTHING_TAU_S = 0.012
APDRONE_RC_LATENCY_S = 0.010
APDRONE_RC_FRAME_RATE_HZ = 150.0
APDRONE_ESC_FRAME_RATE_HZ = 480.0
APDRONE_MAX_RATE_DEG_S = 1998.0

AXES = [
    ("roll", 0),
    ("pitch", 1),
    ("yaw", 2),
]


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


def read_control_arrays(path: Path) -> dict[str, np.ndarray] | None:
    header_line = find_header_line(path)
    if header_line is None:
        return None
    times: list[float] = []
    setpoints = [[], [], []]
    gyros = [[], [], []]
    throttle: list[float] = []
    with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
        for _ in range(header_line):
            next(handle)
        reader = csv.DictReader(handle)
        required = ["time", "rcCommands[3]"] + [f"setpoint[{axis}]" for axis in range(3)] + [f"gyroADC[{axis}]" for axis in range(3)]
        if not reader.fieldnames or any(field not in reader.fieldnames for field in required):
            return None
        for row in reader:
            time_s = parse_float(row.get("time"), 1e6)
            if not math.isfinite(time_s):
                continue
            values = [parse_float(row.get(field)) for field in required[1:]]
            if not all(math.isfinite(value) for value in values):
                continue
            times.append(time_s)
            throttle.append(values[0])
            for axis in range(3):
                setpoints[axis].append(values[1 + axis])
                gyros[axis].append(values[4 + axis])
    if len(times) < 2:
        return None
    arrays: dict[str, np.ndarray] = {
        "time_s": np.asarray(times, dtype=np.float64),
        "throttle": np.asarray(throttle, dtype=np.float64),
    }
    for axis in range(3):
        arrays[f"setpoint_{axis}"] = np.asarray(setpoints[axis], dtype=np.float64)
        arrays[f"gyro_{axis}"] = np.asarray(gyros[axis], dtype=np.float64)
    return arrays


def sample_rate_hz(times: np.ndarray) -> float:
    if len(times) < 3:
        return float("nan")
    deltas = np.diff(times)
    deltas = deltas[np.isfinite(deltas) & (deltas > 0.0)]
    if len(deltas) == 0:
        return float("nan")
    return 1.0 / float(np.median(deltas))


def corr_and_gain(x: np.ndarray, y: np.ndarray, mask: np.ndarray) -> tuple[float, float, int]:
    selected = mask & np.isfinite(x) & np.isfinite(y)
    count = int(np.count_nonzero(selected))
    if count < MIN_DYNAMIC_SAMPLES:
        return float("nan"), float("nan"), count
    xs = x[selected]
    ys = y[selected]
    x_center = xs - float(xs.mean())
    y_center = ys - float(ys.mean())
    x_var = float(np.dot(x_center, x_center))
    y_var = float(np.dot(y_center, y_center))
    if x_var <= 1.0e-12 or y_var <= 1.0e-12:
        return float("nan"), float("nan"), count
    corr = float(np.dot(x_center, y_center) / math.sqrt(x_var * y_var))
    gain = float(np.dot(x_center, y_center) / x_var)
    return corr, gain, count


def shifted_pair(setpoint: np.ndarray, gyro: np.ndarray, activity: np.ndarray, lag_samples: int) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    if lag_samples > 0:
        return setpoint[:-lag_samples], gyro[lag_samples:], activity[:-lag_samples] | activity[lag_samples:]
    if lag_samples < 0:
        lag = -lag_samples
        return setpoint[lag:], gyro[:-lag], activity[lag:] | activity[:-lag]
    return setpoint, gyro, activity


def best_lag_metrics(setpoint: np.ndarray, gyro: np.ndarray, sample_rate: float) -> dict[str, float | int]:
    activity = (np.abs(setpoint) >= ACTIVITY_THRESHOLD_DEG_S) | (np.abs(gyro) >= ACTIVITY_THRESHOLD_DEG_S)
    max_lag_samples = max(1, int(round(MAX_LAG_SECONDS * sample_rate)))
    best: dict[str, float | int] = {
        "best_lag_samples": 0,
        "best_lag_ms": 0.0,
        "best_correlation": float("nan"),
        "best_gain_gyro_per_setpoint": float("nan"),
        "dynamic_pair_count": 0,
    }
    best_abs_corr = -1.0
    for lag_samples in range(-max_lag_samples, max_lag_samples + 1):
        x, y, mask = shifted_pair(setpoint, gyro, activity, lag_samples)
        corr, gain, count = corr_and_gain(x, y, mask)
        if not math.isfinite(corr):
            continue
        abs_corr = abs(corr)
        if abs_corr > best_abs_corr:
            best_abs_corr = abs_corr
            best = {
                "best_lag_samples": lag_samples,
                "best_lag_ms": 1000.0 * lag_samples / sample_rate,
                "best_correlation": corr,
                "best_gain_gyro_per_setpoint": gain,
                "dynamic_pair_count": count,
            }
    x0, y0, mask0 = shifted_pair(setpoint, gyro, activity, 0)
    zero_corr, zero_gain, zero_count = corr_and_gain(x0, y0, mask0)
    best_lag_samples = int(best["best_lag_samples"])
    xb, yb, maskb = shifted_pair(setpoint, gyro, activity, best_lag_samples)
    valid = maskb & np.isfinite(xb) & np.isfinite(yb)
    if int(np.count_nonzero(valid)) >= MIN_DYNAMIC_SAMPLES:
        err = yb[valid] - xb[valid]
        abs_err = np.abs(err)
        mae = float(np.mean(abs_err))
        p95_abs_error = float(np.percentile(abs_err, 95.0))
        median_abs_error = float(np.percentile(abs_err, 50.0))
    else:
        mae = float("nan")
        p95_abs_error = float("nan")
        median_abs_error = float("nan")
    return {
        **best,
        "zero_lag_correlation": zero_corr,
        "zero_lag_gain_gyro_per_setpoint": zero_gain,
        "zero_lag_dynamic_pair_count": zero_count,
        "best_lag_mae_deg_s": mae,
        "best_lag_median_abs_error_deg_s": median_abs_error,
        "best_lag_p95_abs_error_deg_s": p95_abs_error,
    }


def rows_for_file(path: Path) -> list[dict[str, str | int | float]]:
    arrays = read_control_arrays(path)
    if arrays is None:
        return []
    header = read_blackbox_header(path)
    times = arrays["time_s"]
    full_rate = sample_rate_hz(times)
    if not math.isfinite(full_rate) or full_rate <= 0.0:
        return []
    stride = max(1, int(round(full_rate / TARGET_RATE_HZ)))
    sampled = {key: value[::stride] for key, value in arrays.items()}
    downsampled_rate = sample_rate_hz(sampled["time_s"])
    duration = float(times[-1] - times[0])
    scenario = infer_scenario(path)
    rows: list[dict[str, str | int | float]] = []
    for axis_name, axis_index in AXES:
        setpoint = sampled[f"setpoint_{axis_index}"]
        gyro = sampled[f"gyro_{axis_index}"]
        metrics = best_lag_metrics(setpoint, gyro, downsampled_rate)
        setpoint_range = float(np.nanmax(setpoint) - np.nanmin(setpoint)) if len(setpoint) else float("nan")
        gyro_range = float(np.nanmax(gyro) - np.nanmin(gyro)) if len(gyro) else float("nan")
        reliable = (
            int(metrics["dynamic_pair_count"]) >= MIN_DYNAMIC_SAMPLES
            and math.isfinite(float(metrics["best_correlation"]))
            and abs(float(metrics["best_correlation"])) >= RELIABLE_CORRELATION_THRESHOLD
        )
        rows.append(
            {
                "row_type": "apdrone_control_response_axis",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "scenario": scenario,
                "local_source_file": repo_path(path),
                "flight_filename": path.name,
                "firmware_version": header.get("firmwareVersion", ""),
                "axis": axis_name,
                "axis_index": axis_index,
                "duration_s": duration,
                "raw_sample_count": len(times),
                "raw_sample_rate_hz": full_rate,
                "analysis_stride": stride,
                "analysis_sample_count": len(sampled["time_s"]),
                "analysis_sample_rate_hz": downsampled_rate,
                "setpoint_range_deg_s": setpoint_range,
                "gyro_range_deg_s": gyro_range,
                "setpoint_std_deg_s": float(np.nanstd(setpoint)),
                "gyro_std_deg_s": float(np.nanstd(gyro)),
                **metrics,
                "best_lag_over_current_control_latency": float(metrics["best_lag_ms"]) / (APDRONE_CONTROL_LATENCY_S * 1000.0),
                "best_lag_over_control_plus_rc_latency": float(metrics["best_lag_ms"]) / ((APDRONE_CONTROL_LATENCY_S + APDRONE_RC_LATENCY_S) * 1000.0),
                "reliable_for_lag_summary": int(reliable),
                "activity_threshold_deg_s": ACTIVITY_THRESHOLD_DEG_S,
                "current_apDrone_control_latency_ms": APDRONE_CONTROL_LATENCY_S * 1000.0,
                "current_apDrone_rc_smoothing_tau_ms": APDRONE_RC_SMOOTHING_TAU_S * 1000.0,
                "current_apDrone_rc_latency_ms": APDRONE_RC_LATENCY_S * 1000.0,
                "current_apDrone_rc_frame_interval_ms": 1000.0 / APDRONE_RC_FRAME_RATE_HZ,
                "current_apDrone_esc_frame_interval_ms": 1000.0 / APDRONE_ESC_FRAME_RATE_HZ,
                "current_apDrone_max_rate_deg_s": APDRONE_MAX_RATE_DEG_S,
                "note": "Positive lag means gyroADC best matches setpoint delayed by that many milliseconds. Correlation lag is a closed-loop diagnostic, not pure motor/control latency.",
            }
        )
    return rows


def summary_rows(axis_rows: list[dict[str, str | int | float]]) -> list[dict[str, str | int | float]]:
    rows: list[dict[str, str | int | float]] = [
        {
            "row_type": "apdrone_control_response_method",
            "source_page": SOURCE_PAGE,
            "doi": DOI,
            "axis_mapping": "setpoint[0]/gyroADC[0]=roll, [1]=pitch, [2]=yaw",
            "unit_assumption": "Betaflight Blackbox setpoint and gyroADC are degrees/s for these CSV exports.",
            "target_analysis_rate_hz": TARGET_RATE_HZ,
            "max_lag_seconds": MAX_LAG_SECONDS,
            "activity_threshold_deg_s": ACTIVITY_THRESHOLD_DEG_S,
            "min_dynamic_samples": MIN_DYNAMIC_SAMPLES,
            "reliable_correlation_threshold": RELIABLE_CORRELATION_THRESHOLD,
            "current_apDrone_control_latency_ms": APDRONE_CONTROL_LATENCY_S * 1000.0,
            "current_apDrone_rc_smoothing_tau_ms": APDRONE_RC_SMOOTHING_TAU_S * 1000.0,
            "current_apDrone_rc_latency_ms": APDRONE_RC_LATENCY_S * 1000.0,
            "note": "Best-lag statistics use active samples only and maximize absolute correlation over +/-80 ms.",
        }
    ]
    for scenario in sorted({str(row["scenario"]) for row in axis_rows} | {"all"}):
        scenario_rows = axis_rows if scenario == "all" else [row for row in axis_rows if row["scenario"] == scenario]
        if not scenario_rows:
            continue
        for axis_name, _ in AXES:
            bucket = [row for row in scenario_rows if row["axis"] == axis_name]
            reliable = [row for row in bucket if int(row["reliable_for_lag_summary"]) == 1]
            if not bucket:
                continue
            lags = [float(row["best_lag_ms"]) for row in reliable]
            corrs = [abs(float(row["best_correlation"])) for row in reliable]
            gains = [float(row["best_gain_gyro_per_setpoint"]) for row in reliable if math.isfinite(float(row["best_gain_gyro_per_setpoint"]))]
            mae = [float(row["best_lag_mae_deg_s"]) for row in reliable if math.isfinite(float(row["best_lag_mae_deg_s"]))]
            rows.append(
                {
                    "row_type": "apdrone_control_response_axis_summary",
                    "source_page": SOURCE_PAGE,
                    "doi": DOI,
                    "scenario": scenario,
                    "axis": axis_name,
                    "file_axis_row_count": len(bucket),
                    "reliable_row_count": len(reliable),
                    "best_lag_ms_p10": percentile(lags, 0.10),
                    "best_lag_ms_p50": percentile(lags, 0.50),
                    "best_lag_ms_p90": percentile(lags, 0.90),
                    "abs_correlation_p50": percentile(corrs, 0.50),
                    "abs_correlation_p90": percentile(corrs, 0.90),
                    "gain_p50": percentile(gains, 0.50),
                    "mae_deg_s_p50": percentile(mae, 0.50),
                    "current_apDrone_control_latency_ms": APDRONE_CONTROL_LATENCY_S * 1000.0,
                    "current_apDrone_rc_smoothing_tau_ms": APDRONE_RC_SMOOTHING_TAU_S * 1000.0,
                    "current_apDrone_rc_latency_ms": APDRONE_RC_LATENCY_S * 1000.0,
                    "note": "Summary excludes low-correlation/low-activity rows. Lags are not direct plant delay because Betaflight setpoint and gyro are both closed-loop log fields.",
                }
            )
    return rows


def build_rows() -> list[dict[str, str | int | float]]:
    axis_rows: list[dict[str, str | int | float]] = []
    for path in apdrone_csv_paths():
        axis_rows.extend(rows_for_file(path))
    rows = summary_rows(axis_rows) + axis_rows
    return [{key: finite_or_blank(value) for key, value in row.items()} for row in rows]


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
    rows = build_rows()
    write_csv(OUTPUT, rows)
    print(f"Wrote {repo_path(OUTPUT)}")


if __name__ == "__main__":
    main()
