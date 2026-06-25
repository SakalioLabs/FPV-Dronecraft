"""Estimate APdrone Blackbox barometer noise/drift from low-motion windows.

Outputs:
  docs/data/apdrone_baro_noise_log_reference.csv

The APdrone Blackbox CSVs include baroAlt. This script reuses the same
static/zero-throttle low-motion selectors used for IMU-noise checks and
computes raw and detrended baro altitude variation. The result is a log-derived
anchor for separating centimeter/decimeter MEMS barometer noise from meter-scale
dynamic-pressure and propwash errors in the simulator.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "apdrone_zgsvdtxnfh_v2"
OUTPUT = DATA / "apdrone_baro_noise_log_reference.csv"

SOURCE_PAGE = "https://data.mendeley.com/datasets/zgsvdtxnfh/2"
DOI = "10.17632/zgsvdtxnfh.2"

G0 = 9.80665
MAX_SEGMENT_GAP_S = 0.02
MIN_SEGMENT_DURATION_S = 0.25
MIN_SEGMENT_SAMPLES = 100
RELAXED_GYRO_NORM_MAX_DEG_S = 15.0
RELAXED_ACC_NORM_TOL_COUNTS = 250.0

# Current DronePhysics quiet-noise proxy for apDrone:
# barometerNoiseMeters amplitude = 0.035 * accelerometerNoiseStdDev.
APDRONE_ACCEL_NOISE_M_S2 = 0.20
APDRONE_QUIET_BARO_AMPLITUDE_M = 0.035 * APDRONE_ACCEL_NOISE_M_S2
APDRONE_QUIET_BARO_RMS_M = APDRONE_QUIET_BARO_AMPLITUDE_M * math.sqrt((1.0 + 0.35**2 + 0.18**2) / 2.0)
APDRONE_BARO_ALTITUDE_TAU_S = 0.090
APDRONE_BARO_VSPEED_TAU_S = 0.180

# Existing barometer_reference_summary DPS310 row: 0.2 Pa -> about 0.01665 m.
DPS310_PRESSURE_NOISE_ALTITUDE_M = 0.016648427966986585


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


def mean(values: list[float]) -> float:
    return sum(values) / len(values) if values else float("nan")


def stddev(values: list[float]) -> float:
    if len(values) < 2:
        return float("nan")
    center = mean(values)
    return math.sqrt(sum((value - center) ** 2 for value in values) / len(values))


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


def linear_fit(xs: list[float], ys: list[float]) -> tuple[float, float]:
    if len(xs) < 2:
        return float("nan"), float("nan")
    x0 = xs[0]
    shifted = [x - x0 for x in xs]
    x_mean = mean(shifted)
    y_mean = mean(ys)
    denom = sum((x - x_mean) ** 2 for x in shifted)
    if denom <= 0.0:
        return float("nan"), float("nan")
    slope = sum((x - x_mean) * (y - y_mean) for x, y in zip(shifted, ys)) / denom
    intercept = y_mean - slope * x_mean
    return slope, intercept


def detrended_std(xs: list[float], ys: list[float]) -> float:
    slope, intercept = linear_fit(xs, ys)
    if not (math.isfinite(slope) and math.isfinite(intercept)):
        return float("nan")
    x0 = xs[0]
    residuals = [y - (intercept + slope * (x - x0)) for x, y in zip(xs, ys)]
    return stddev(residuals)


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


def new_segment(selection: str, source_file: Path, header: dict[str, str]) -> dict[str, object]:
    return {
        "selection": selection,
        "source_file": source_file,
        "header": header,
        "times": [],
        "baro_m": [],
        "gyro_norm": [],
        "acc_norm": [],
        "throttle": [],
        "gps_speed_m_s": [],
    }


def add_sample(
    segment: dict[str, object],
    time_s: float,
    baro_m: float,
    gyro_norm: float,
    acc_norm: float,
    throttle: float,
    gps_speed_m_s: float,
) -> None:
    segment["times"].append(time_s)  # type: ignore[index, union-attr]
    segment["baro_m"].append(baro_m)  # type: ignore[index, union-attr]
    segment["gyro_norm"].append(gyro_norm)  # type: ignore[index, union-attr]
    segment["acc_norm"].append(acc_norm)  # type: ignore[index, union-attr]
    segment["throttle"].append(throttle)  # type: ignore[index, union-attr]
    if math.isfinite(gps_speed_m_s):
        segment["gps_speed_m_s"].append(gps_speed_m_s)  # type: ignore[index, union-attr]


def segment_duration(segment: dict[str, object]) -> float:
    times = segment["times"]  # type: ignore[assignment]
    if len(times) < 2:
        return 0.0
    return float(times[-1]) - float(times[0])


def finalize_segment(segment: dict[str, object]) -> dict[str, str | int | float] | None:
    times: list[float] = segment["times"]  # type: ignore[assignment]
    baro: list[float] = segment["baro_m"]  # type: ignore[assignment]
    if len(times) < MIN_SEGMENT_SAMPLES:
        return None
    duration = segment_duration(segment)
    if duration < MIN_SEGMENT_DURATION_S:
        return None
    slope, _ = linear_fit(times, baro)
    raw_std = stddev(baro)
    det_std = detrended_std(times, baro)
    source_file: Path = segment["source_file"]  # type: ignore[assignment]
    header: dict[str, str] = segment["header"]  # type: ignore[assignment]
    return {
        "row_type": "apdrone_baro_noise_segment",
        "source_page": SOURCE_PAGE,
        "doi": DOI,
        "selection": str(segment["selection"]),
        "local_source_file": repo_path(source_file),
        "flight_filename": source_file.name,
        "start_time_s": times[0],
        "end_time_s": times[-1],
        "duration_s": duration,
        "sample_count": len(times),
        "sample_rate_hz": (len(times) - 1) / duration if duration > 0.0 else float("nan"),
        "firmware_version": header.get("firmwareVersion", ""),
        "baro_hardware": header.get("baro_hardware", ""),
        "acc_1g_counts": parse_float(header.get("acc_1G")),
        "baro_alt_mean_m": mean(baro),
        "baro_alt_std_m": raw_std,
        "baro_alt_detrended_std_m": det_std,
        "baro_alt_min_m": min(baro),
        "baro_alt_max_m": max(baro),
        "baro_alt_peak_to_peak_m": max(baro) - min(baro),
        "baro_alt_linear_slope_m_s": slope,
        "baro_alt_linear_drift_over_segment_m": slope * duration if math.isfinite(slope) else float("nan"),
        "baro_alt_std_over_current_quiet_rms": raw_std / APDRONE_QUIET_BARO_RMS_M,
        "baro_alt_detrended_std_over_current_quiet_rms": det_std / APDRONE_QUIET_BARO_RMS_M,
        "baro_alt_detrended_std_over_dps310_pressure_noise": det_std / DPS310_PRESSURE_NOISE_ALTITUDE_M,
        "gyro_norm_mean_deg_s": mean(segment["gyro_norm"]),  # type: ignore[arg-type]
        "gyro_norm_p95_deg_s": percentile(segment["gyro_norm"], 0.95),  # type: ignore[arg-type]
        "acc_norm_mean_counts": mean(segment["acc_norm"]),  # type: ignore[arg-type]
        "throttle_mean": mean(segment["throttle"]),  # type: ignore[arg-type]
        "gps_speed_mean_m_s": mean(segment["gps_speed_m_s"]),  # type: ignore[arg-type]
        "current_apDrone_quiet_baro_noise_rms_m": APDRONE_QUIET_BARO_RMS_M,
        "dps310_pressure_noise_altitude_m": DPS310_PRESSURE_NOISE_ALTITUDE_M,
        "current_apDrone_baro_altitude_tau_s": APDRONE_BARO_ALTITUDE_TAU_S,
        "current_apDrone_baro_vertical_speed_tau_s": APDRONE_BARO_VSPEED_TAU_S,
        "note": "baroAlt is interpreted as centimeters; detrended std removes linear drift within the accepted window.",
    }


def selectors(row: dict[str, str], acc_1g: float) -> dict[str, bool]:
    throttle = parse_float(row.get("rcCommands[3]"))
    setpoints = [parse_float(row.get(f"setpoint[{axis}]")) for axis in range(3)]
    gyro = [parse_float(row.get(f"gyroADC[{axis}]")) for axis in range(3)]
    acc = [parse_float(row.get(f"accSmooth[{axis}]")) for axis in range(3)]
    baro = parse_float(row.get("baroAlt"), 100.0)
    if not all(math.isfinite(value) for value in [throttle, baro, *setpoints, *gyro, *acc]):
        return {"strict_static": False, "zero_throttle_low_motion": False}
    gyro_norm = math.sqrt(sum(value * value for value in gyro))
    acc_norm = math.sqrt(sum(value * value for value in acc))
    strict_static = throttle == 0.0 and all(abs(value) < 1.0e-9 for value in setpoints)
    zero_throttle_low_motion = (
        throttle == 0.0
        and gyro_norm <= RELAXED_GYRO_NORM_MAX_DEG_S
        and abs(acc_norm - acc_1g) <= RELAXED_ACC_NORM_TOL_COUNTS
    )
    return {"strict_static": strict_static, "zero_throttle_low_motion": zero_throttle_low_motion}


def segment_rows_for_file(path: Path) -> list[dict[str, str | int | float]]:
    header_line = find_header_line(path)
    if header_line is None:
        return []
    header = read_blackbox_header(path)
    acc_1g = parse_float(header.get("acc_1G"))
    if not math.isfinite(acc_1g) or acc_1g <= 0.0:
        acc_1g = 2048.0

    rows: list[dict[str, str | int | float]] = []
    active: dict[str, dict[str, object] | None] = {
        "strict_static": None,
        "zero_throttle_low_motion": None,
    }
    last_time: dict[str, float | None] = {
        "strict_static": None,
        "zero_throttle_low_motion": None,
    }

    def close(selection: str) -> None:
        segment = active[selection]
        if segment is not None:
            row = finalize_segment(segment)
            if row is not None:
                rows.append(row)
        active[selection] = None
        last_time[selection] = None

    with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
        for _ in range(header_line):
            next(handle)
        reader = csv.DictReader(handle)
        for row in reader:
            time_s = parse_float(row.get("time"), 1e6)
            baro_m = parse_float(row.get("baroAlt"), 100.0)
            throttle = parse_float(row.get("rcCommands[3]"))
            gps_speed_m_s = parse_float(row.get("GPS_speed"), 100.0)
            gyro = [parse_float(row.get(f"gyroADC[{axis}]")) for axis in range(3)]
            acc = [parse_float(row.get(f"accSmooth[{axis}]")) for axis in range(3)]
            if not all(math.isfinite(value) for value in [time_s, baro_m, throttle, *gyro, *acc]):
                continue
            gyro_norm = math.sqrt(sum(value * value for value in gyro))
            acc_norm = math.sqrt(sum(value * value for value in acc))
            selected = selectors(row, acc_1g)
            for selection, is_selected in selected.items():
                previous_time = last_time[selection]
                if not is_selected:
                    close(selection)
                    continue
                if active[selection] is None or (previous_time is not None and time_s - previous_time > MAX_SEGMENT_GAP_S):
                    close(selection)
                    active[selection] = new_segment(selection, path, header)
                add_sample(active[selection], time_s, baro_m, gyro_norm, acc_norm, throttle, gps_speed_m_s)  # type: ignore[arg-type]
                last_time[selection] = time_s
    for selection in active:
        close(selection)
    return rows


def summary_rows(segment_rows: list[dict[str, str | int | float]]) -> list[dict[str, str | int | float]]:
    rows: list[dict[str, str | int | float]] = [
        {
            "row_type": "apdrone_baro_noise_method",
            "source_page": SOURCE_PAGE,
            "doi": DOI,
            "strict_static_definition": "rcCommands[3] == 0 and setpoint[0..2] == 0",
            "zero_throttle_low_motion_definition": f"rcCommands[3] == 0, gyro norm <= {RELAXED_GYRO_NORM_MAX_DEG_S:g} deg/s, abs(acc norm - acc_1G) <= {RELAXED_ACC_NORM_TOL_COUNTS:g} counts",
            "baro_alt_unit_assumption": "baroAlt / 100 = meters",
            "min_segment_duration_s": MIN_SEGMENT_DURATION_S,
            "min_segment_samples": MIN_SEGMENT_SAMPLES,
            "max_segment_gap_s": MAX_SEGMENT_GAP_S,
            "current_apDrone_quiet_baro_noise_rms_m": APDRONE_QUIET_BARO_RMS_M,
            "current_apDrone_quiet_baro_noise_amplitude_m": APDRONE_QUIET_BARO_AMPLITUDE_M,
            "dps310_pressure_noise_altitude_m": DPS310_PRESSURE_NOISE_ALTITUDE_M,
            "current_apDrone_baro_altitude_tau_s": APDRONE_BARO_ALTITUDE_TAU_S,
            "current_apDrone_baro_vertical_speed_tau_s": APDRONE_BARO_VSPEED_TAU_S,
            "note": "Segment standard deviations include sensor noise, filter quantization, residual vibration, and local pressure changes; detrended std removes only a linear drift inside each segment.",
        }
    ]
    for selection in ("strict_static", "zero_throttle_low_motion"):
        bucket = [row for row in segment_rows if row["selection"] == selection]
        if not bucket:
            continue
        raw_std = [float(row["baro_alt_std_m"]) for row in bucket]
        det_std = [float(row["baro_alt_detrended_std_m"]) for row in bucket]
        peak_to_peak = [float(row["baro_alt_peak_to_peak_m"]) for row in bucket]
        slopes = [abs(float(row["baro_alt_linear_slope_m_s"])) for row in bucket]
        rows.append(
            {
                "row_type": "apdrone_baro_noise_selection_summary",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "selection": selection,
                "segment_count": len(bucket),
                "source_file_count": len({str(row["local_source_file"]) for row in bucket}),
                "sample_count_total": sum(int(row["sample_count"]) for row in bucket),
                "duration_total_s": sum(float(row["duration_s"]) for row in bucket),
                "baro_alt_std_m_p50": percentile(raw_std, 0.50),
                "baro_alt_std_m_p90": percentile(raw_std, 0.90),
                "baro_alt_detrended_std_m_p50": percentile(det_std, 0.50),
                "baro_alt_detrended_std_m_p90": percentile(det_std, 0.90),
                "baro_alt_peak_to_peak_m_p50": percentile(peak_to_peak, 0.50),
                "baro_alt_peak_to_peak_m_p90": percentile(peak_to_peak, 0.90),
                "abs_baro_alt_slope_m_s_p50": percentile(slopes, 0.50),
                "abs_baro_alt_slope_m_s_p90": percentile(slopes, 0.90),
                "detrended_p50_over_current_quiet_rms": percentile(det_std, 0.50) / APDRONE_QUIET_BARO_RMS_M,
                "detrended_p90_over_current_quiet_rms": percentile(det_std, 0.90) / APDRONE_QUIET_BARO_RMS_M,
                "detrended_p50_over_dps310_pressure_noise": percentile(det_std, 0.50) / DPS310_PRESSURE_NOISE_ALTITUDE_M,
                "detrended_p90_over_dps310_pressure_noise": percentile(det_std, 0.90) / DPS310_PRESSURE_NOISE_ALTITUDE_M,
                "note": "P50/P90 are over contiguous accepted segments, not over all samples.",
            }
        )
    return rows


def build_rows() -> list[dict[str, str | int | float]]:
    segments: list[dict[str, str | int | float]] = []
    for path in apdrone_csv_paths():
        segments.extend(segment_rows_for_file(path))
    rows = summary_rows(segments) + segments
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
