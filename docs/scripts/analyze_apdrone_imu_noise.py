"""Estimate APdrone Blackbox IMU noise from static/low-motion windows.

Outputs:
  docs/data/apdrone_imu_noise_log_reference.csv

The APdrone logs expose Betaflight Blackbox gyroADC and accSmooth fields.
This script finds strict zero-command static windows plus a broader
zero-throttle/low-motion set, computes per-axis standard deviations, converts
gyro degrees/s to rad/s and accelerometer counts using the Blackbox acc_1G
header, and compares the result with the current apDrone() sensor-noise fields.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "apdrone_zgsvdtxnfh_v2"
OUTPUT = DATA / "apdrone_imu_noise_log_reference.csv"

SOURCE_PAGE = "https://data.mendeley.com/datasets/zgsvdtxnfh/2"
DOI = "10.17632/zgsvdtxnfh.2"
G0 = 9.80665
DEG_TO_RAD = math.pi / 180.0

# Current DroneConfig.apDrone() values at the time of this data packet.
APDRONE_GYRO_LPF_HZ = 125.0
APDRONE_GYRO_NOISE_RAD_S = 0.025
APDRONE_ACCEL_LPF_HZ = 80.0
APDRONE_ACCEL_NOISE_M_S2 = 0.20

MAX_SEGMENT_GAP_S = 0.02
MIN_SEGMENT_DURATION_S = 0.25
MIN_SEGMENT_SAMPLES = 100
RELAXED_GYRO_NORM_MAX_DEG_S = 15.0
RELAXED_ACC_NORM_TOL_COUNTS = 250.0


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
            if not line.startswith('"') and "," not in line:
                continue
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
        "gyro": [[], [], []],
        "acc": [[], [], []],
        "gyro_norm": [],
        "acc_norm": [],
    }


def add_sample(segment: dict[str, object], time_s: float, gyro: list[float], acc: list[float]) -> None:
    segment["times"].append(time_s)  # type: ignore[index, union-attr]
    for axis in range(3):
        segment["gyro"][axis].append(gyro[axis])  # type: ignore[index, union-attr]
        segment["acc"][axis].append(acc[axis])  # type: ignore[index, union-attr]
    segment["gyro_norm"].append(math.sqrt(sum(value * value for value in gyro)))  # type: ignore[index, union-attr]
    segment["acc_norm"].append(math.sqrt(sum(value * value for value in acc)))  # type: ignore[index, union-attr]


def segment_duration(segment: dict[str, object]) -> float:
    times = segment["times"]  # type: ignore[assignment]
    if len(times) < 2:
        return 0.0
    return float(times[-1]) - float(times[0])


def finalize_segment(segment: dict[str, object]) -> dict[str, str | int | float] | None:
    times: list[float] = segment["times"]  # type: ignore[assignment]
    if len(times) < MIN_SEGMENT_SAMPLES:
        return None
    duration = segment_duration(segment)
    if duration < MIN_SEGMENT_DURATION_S:
        return None

    source_file: Path = segment["source_file"]  # type: ignore[assignment]
    header: dict[str, str] = segment["header"]  # type: ignore[assignment]
    acc_1g = parse_float(header.get("acc_1G")) or 2048.0
    high_resolution = parse_float(header.get("blackbox_high_resolution"))
    acc_scale = G0 / acc_1g if acc_1g > 0.0 else float("nan")

    gyro: list[list[float]] = segment["gyro"]  # type: ignore[assignment]
    acc: list[list[float]] = segment["acc"]  # type: ignore[assignment]
    gyro_stds_deg_s = [stddev(axis_values) for axis_values in gyro]
    acc_stds_counts = [stddev(axis_values) for axis_values in acc]
    acc_stds_m_s2 = [value * acc_scale for value in acc_stds_counts]
    gyro_vector_rms_deg_s = math.sqrt(mean([value * value for value in gyro_stds_deg_s]))
    gyro_vector_rms_rad_s = gyro_vector_rms_deg_s * DEG_TO_RAD
    acc_vector_rms_m_s2 = math.sqrt(mean([value * value for value in acc_stds_m_s2]))

    row: dict[str, str | int | float] = {
        "row_type": "apdrone_imu_noise_segment",
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
        "acc_1g_counts": acc_1g,
        "blackbox_high_resolution": high_resolution,
        "gyro_norm_mean_deg_s": mean(segment["gyro_norm"]),  # type: ignore[arg-type]
        "gyro_norm_p95_deg_s": percentile(segment["gyro_norm"], 0.95),  # type: ignore[arg-type]
        "acc_norm_mean_counts": mean(segment["acc_norm"]),  # type: ignore[arg-type]
        "acc_norm_p95_abs_error_counts": percentile([abs(value - acc_1g) for value in segment["acc_norm"]], 0.95),  # type: ignore[union-attr]
        "gyro_std_x_deg_s": gyro_stds_deg_s[0],
        "gyro_std_y_deg_s": gyro_stds_deg_s[1],
        "gyro_std_z_deg_s": gyro_stds_deg_s[2],
        "gyro_vector_rms_deg_s": gyro_vector_rms_deg_s,
        "gyro_vector_rms_rad_s": gyro_vector_rms_rad_s,
        "gyro_vector_rms_over_current_apDrone": gyro_vector_rms_rad_s / APDRONE_GYRO_NOISE_RAD_S,
        "acc_std_x_counts": acc_stds_counts[0],
        "acc_std_y_counts": acc_stds_counts[1],
        "acc_std_z_counts": acc_stds_counts[2],
        "acc_std_x_m_s2": acc_stds_m_s2[0],
        "acc_std_y_m_s2": acc_stds_m_s2[1],
        "acc_std_z_m_s2": acc_stds_m_s2[2],
        "acc_vector_rms_m_s2": acc_vector_rms_m_s2,
        "acc_vector_rms_over_current_apDrone": acc_vector_rms_m_s2 / APDRONE_ACCEL_NOISE_M_S2,
        "current_apDrone_gyro_lpf_hz": APDRONE_GYRO_LPF_HZ,
        "current_apDrone_gyro_noise_rad_s": APDRONE_GYRO_NOISE_RAD_S,
        "current_apDrone_accel_lpf_hz": APDRONE_ACCEL_LPF_HZ,
        "current_apDrone_accel_noise_m_s2": APDRONE_ACCEL_NOISE_M_S2,
        "note": "gyroADC is interpreted as Betaflight gyroADCf degrees/s; accSmooth is converted with the Blackbox acc_1G header.",
    }
    return row


def selectors(row: dict[str, str], acc_1g: float) -> dict[str, bool]:
    throttle = parse_float(row.get("rcCommands[3]"))
    setpoints = [parse_float(row.get(f"setpoint[{axis}]")) for axis in range(3)]
    gyro = [parse_float(row.get(f"gyroADC[{axis}]")) for axis in range(3)]
    acc = [parse_float(row.get(f"accSmooth[{axis}]")) for axis in range(3)]
    if not all(math.isfinite(value) for value in [throttle, *setpoints, *gyro, *acc]):
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
            gyro = [parse_float(row.get(f"gyroADC[{axis}]")) for axis in range(3)]
            acc = [parse_float(row.get(f"accSmooth[{axis}]")) for axis in range(3)]
            if not (math.isfinite(time_s) and all(math.isfinite(value) for value in [*gyro, *acc])):
                continue
            selected = selectors(row, acc_1g)
            for selection, is_selected in selected.items():
                previous_time = last_time[selection]
                if not is_selected:
                    close(selection)
                    continue
                if active[selection] is None or (previous_time is not None and time_s - previous_time > MAX_SEGMENT_GAP_S):
                    close(selection)
                    active[selection] = new_segment(selection, path, header)
                add_sample(active[selection], time_s, gyro, acc)  # type: ignore[arg-type]
                last_time[selection] = time_s
    for selection in active:
        close(selection)
    return rows


def summary_rows(segment_rows: list[dict[str, str | int | float]]) -> list[dict[str, str | int | float]]:
    rows: list[dict[str, str | int | float]] = [
        {
            "row_type": "apdrone_imu_noise_method",
            "source_page": SOURCE_PAGE,
            "doi": DOI,
            "strict_static_definition": "rcCommands[3] == 0 and setpoint[0..2] == 0",
            "zero_throttle_low_motion_definition": f"rcCommands[3] == 0, gyro norm <= {RELAXED_GYRO_NORM_MAX_DEG_S:g} deg/s, abs(acc norm - acc_1G) <= {RELAXED_ACC_NORM_TOL_COUNTS:g} counts",
            "min_segment_duration_s": MIN_SEGMENT_DURATION_S,
            "min_segment_samples": MIN_SEGMENT_SAMPLES,
            "max_segment_gap_s": MAX_SEGMENT_GAP_S,
            "current_apDrone_gyro_lpf_hz": APDRONE_GYRO_LPF_HZ,
            "current_apDrone_gyro_noise_rad_s": APDRONE_GYRO_NOISE_RAD_S,
            "current_apDrone_accel_lpf_hz": APDRONE_ACCEL_LPF_HZ,
            "current_apDrone_accel_noise_m_s2": APDRONE_ACCEL_NOISE_M_S2,
            "note": "Segment standard deviations include electronics noise plus any residual frame vibration and small motion that pass the selector.",
        }
    ]
    for selection in ("strict_static", "zero_throttle_low_motion"):
        bucket = [row for row in segment_rows if row["selection"] == selection]
        if not bucket:
            continue
        gyro = [float(row["gyro_vector_rms_rad_s"]) for row in bucket]
        accel = [float(row["acc_vector_rms_m_s2"]) for row in bucket]
        rows.append(
            {
                "row_type": "apdrone_imu_noise_selection_summary",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "selection": selection,
                "segment_count": len(bucket),
                "source_file_count": len({str(row["local_source_file"]) for row in bucket}),
                "sample_count_total": sum(int(row["sample_count"]) for row in bucket),
                "duration_total_s": sum(float(row["duration_s"]) for row in bucket),
                "gyro_vector_rms_rad_s_p50": percentile(gyro, 0.50),
                "gyro_vector_rms_rad_s_p90": percentile(gyro, 0.90),
                "gyro_vector_rms_rad_s_min": min(gyro),
                "gyro_vector_rms_rad_s_max": max(gyro),
                "gyro_p50_over_current_apDrone": percentile(gyro, 0.50) / APDRONE_GYRO_NOISE_RAD_S,
                "gyro_p90_over_current_apDrone": percentile(gyro, 0.90) / APDRONE_GYRO_NOISE_RAD_S,
                "acc_vector_rms_m_s2_p50": percentile(accel, 0.50),
                "acc_vector_rms_m_s2_p90": percentile(accel, 0.90),
                "acc_vector_rms_m_s2_min": min(accel),
                "acc_vector_rms_m_s2_max": max(accel),
                "acc_p50_over_current_apDrone": percentile(accel, 0.50) / APDRONE_ACCEL_NOISE_M_S2,
                "acc_p90_over_current_apDrone": percentile(accel, 0.90) / APDRONE_ACCEL_NOISE_M_S2,
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
