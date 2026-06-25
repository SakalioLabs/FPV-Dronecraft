"""Build a selective Race Against the Machine FPV log packet.

Outputs:
  docs/data/ratm_500hz_sync_file_inventory.csv
  docs/data/ratm_high_speed_flight_metrics.csv
  docs/data/ratm_high_speed_window_reference.csv
  docs/data/ratm_high_speed_flight_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category ratm_packet_*

The upstream RATM release is split into large zip chunks. This script avoids a
full 15.9 GiB checkout by reading the Zip64 central directory and selected CSV
members with HTTP Range requests. It keeps only compact derived metrics plus a
small window around the fastest samples.
"""

from __future__ import annotations

import argparse
import binascii
import csv
import io
import math
import re
import struct
import time
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

FILE_INVENTORY = DATA / "ratm_500hz_sync_file_inventory.csv"
FLIGHT_METRICS = DATA / "ratm_high_speed_flight_metrics.csv"
WINDOW_REFERENCE = DATA / "ratm_high_speed_window_reference.csv"
PACKET = DATA / "ratm_high_speed_flight_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"
AIRFRAME_REFERENCE = DATA / "airframe_drag_reference.csv"

RELEASE_BASE_URL = "https://github.com/Drone-Racing/drone-racing-dataset/releases/download/v3.0.0/"
README_URL = "https://raw.githubusercontent.com/tii-racing/drone-racing-dataset/main/README.md"
DOWNLOADER_URL = "https://raw.githubusercontent.com/tii-racing/drone-racing-dataset/main/data_downloader.sh"
BOM_URL = "https://raw.githubusercontent.com/tii-racing/drone-racing-dataset/main/quadrotor/bom.md"
BETAFIGHT_URL = "https://raw.githubusercontent.com/tii-racing/drone-racing-dataset/main/quadrotor/BTFL_cli_backup.txt"
PAPER_DOI_URL = "https://doi.org/10.1109/LRA.2024.3371288"
RELEASE_URL = "https://github.com/Drone-Racing/drone-racing-dataset/releases/tag/v3.0.0"

ZIP64_EOCD = b"PK\x06\x06"
ZIP64_LOCATOR = b"PK\x06\x07"
EOCD = b"PK\x05\x06"
CENTRAL_DIRECTORY = b"PK\x01\x02"
LOCAL_FILE_HEADER = b"PK\x03\x04"

README_SPEED_FLOOR_M_S = 21.0
RATM_BATTERY_NOMINAL_V = 22.2
RATM_BATTERY_CAPACITY_AH = 1.4
RATM_BATTERY_C_RATING = 150.0
RATM_LISTED_PACK_CURRENT_A = RATM_BATTERY_CAPACITY_AH * RATM_BATTERY_C_RATING
RATM_ESC_CURRENT_A = 55.0
RATM_MOTOR_KV = 2020.0
RATM_PROP_RADIUS_M = 0.0648
SYNC_RATE_HZ = 500.0
WINDOW_SECONDS = 1.0

WINDOW_COLUMNS = [
    "elapsed_time",
    "timestamp",
    "accel_x",
    "accel_y",
    "accel_z",
    "gyro_x",
    "gyro_y",
    "gyro_z",
    "thrust[0]",
    "thrust[1]",
    "thrust[2]",
    "thrust[3]",
    "channels_roll",
    "channels_pitch",
    "channels_thrust",
    "channels_yaw",
    "vbat",
    "drone_x",
    "drone_y",
    "drone_z",
    "drone_roll",
    "drone_pitch",
    "drone_yaw",
    "drone_velocity_linear_x",
    "drone_velocity_linear_y",
    "drone_velocity_linear_z",
    "drone_velocity_angular_x",
    "drone_velocity_angular_y",
    "drone_velocity_angular_z",
    "drone_residual",
]


@dataclass(frozen=True)
class ArchiveSpec:
    name: str
    chunk_sizes: tuple[int, ...]

    @property
    def total_size(self) -> int:
        return sum(self.chunk_sizes)

    @property
    def chunk_names(self) -> tuple[str, ...]:
        return tuple(f"{self.name}_zipchunk{i:02d}" for i in range(1, len(self.chunk_sizes) + 1))

    @property
    def chunk_bounds(self) -> tuple[tuple[str, int, int], ...]:
        bounds: list[tuple[str, int, int]] = []
        start = 0
        for name, size in zip(self.chunk_names, self.chunk_sizes):
            bounds.append((name, start, start + size))
            start += size
        return tuple(bounds)


ARCHIVES = {
    "autonomous": ArchiveSpec(
        name="autonomous",
        chunk_sizes=(1_992_294_400, 1_992_294_400, 966_098_609),
    ),
    "piloted": ArchiveSpec(
        name="piloted",
        chunk_sizes=(
            1_992_294_400,
            1_992_294_400,
            1_992_294_400,
            1_992_294_400,
            1_992_294_400,
            1_992_294_400,
            193_406_873,
        ),
    ),
}


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def write_csv(path: Path, rows: Iterable[dict[str, object]]) -> None:
    rows = list(rows)
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames: list[str] = []
    for row in rows:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


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


def fetch_url_range(url: str, start: int, end: int, *, timeout_s: int = 180) -> bytes:
    if end < start:
        return b""
    headers = {
        "Range": f"bytes={start}-{end}",
        "User-Agent": "codex-ratm-range-reader",
    }
    last_error: Exception | None = None
    for attempt in range(1, 5):
        try:
            request = Request(url, headers=headers)
            with urlopen(request, timeout=timeout_s) as response:
                data = response.read()
            expected = end - start + 1
            if len(data) != expected:
                raise RuntimeError(f"range length mismatch for {url}: expected {expected}, got {len(data)}")
            return data
        except Exception as exc:  # pragma: no cover - network retry path
            last_error = exc
            if attempt == 4:
                break
            time.sleep(min(2**attempt, 8))
    raise RuntimeError(f"failed to fetch range {start}-{end} from {url}") from last_error


def fetch_archive_range(spec: ArchiveSpec, full_start: int, length: int) -> bytes:
    output = bytearray()
    cursor = full_start
    remaining = length
    while remaining > 0:
        for chunk_name, chunk_start, chunk_end in spec.chunk_bounds:
            if chunk_start <= cursor < chunk_end:
                take = min(remaining, chunk_end - cursor)
                relative_start = cursor - chunk_start
                relative_end = relative_start + take - 1
                output.extend(fetch_url_range(RELEASE_BASE_URL + chunk_name, relative_start, relative_end))
                cursor += take
                remaining -= take
                break
        else:
            raise ValueError(f"archive offset {cursor} outside {spec.name} zip size {spec.total_size}")
    return bytes(output)


def locate_central_directory(spec: ArchiveSpec) -> tuple[int, int, int]:
    last_chunk = spec.chunk_names[-1]
    last_size = spec.chunk_sizes[-1]
    tail_size = min(2 * 1024 * 1024, last_size)
    tail_start = last_size - tail_size
    tail = fetch_url_range(RELEASE_BASE_URL + last_chunk, tail_start, last_size - 1)

    eocd_offset = tail.rfind(EOCD)
    locator_offset = tail.rfind(ZIP64_LOCATOR)
    zip64_offset = tail.rfind(ZIP64_EOCD)
    if eocd_offset < 0 or locator_offset < 0 or zip64_offset < 0:
        raise RuntimeError(f"could not find Zip64 directory records in {spec.name}")

    _, record_size, _, _, _, _, disk_entries, total_entries, cd_size, cd_offset = struct.unpack_from(
        "<4sQ2H2L4Q", tail, zip64_offset
    )
    if record_size != 44 or disk_entries != total_entries:
        raise RuntimeError(f"unexpected Zip64 directory shape for {spec.name}")
    return int(cd_offset), int(cd_size), int(total_entries)


def parse_zip64_extra(
    *,
    extra: bytes,
    compressed_size: int,
    uncompressed_size: int,
    local_header_offset: int,
) -> tuple[int, int, int]:
    cursor = 0
    z_comp = compressed_size
    z_uncomp = uncompressed_size
    z_offset = local_header_offset
    while cursor + 4 <= len(extra):
        header_id, data_size = struct.unpack_from("<HH", extra, cursor)
        cursor += 4
        body = extra[cursor : cursor + data_size]
        cursor += data_size
        if header_id != 0x0001:
            continue
        body_cursor = 0
        if uncompressed_size == 0xFFFFFFFF:
            z_uncomp = struct.unpack_from("<Q", body, body_cursor)[0]
            body_cursor += 8
        if compressed_size == 0xFFFFFFFF:
            z_comp = struct.unpack_from("<Q", body, body_cursor)[0]
            body_cursor += 8
        if local_header_offset == 0xFFFFFFFF:
            z_offset = struct.unpack_from("<Q", body, body_cursor)[0]
    return int(z_comp), int(z_uncomp), int(z_offset)


def parse_central_directory(spec: ArchiveSpec) -> list[dict[str, object]]:
    cd_offset, cd_size, total_entries = locate_central_directory(spec)
    cd = fetch_archive_range(spec, cd_offset, cd_size)
    rows: list[dict[str, object]] = []
    cursor = 0
    while cursor < len(cd):
        if cd[cursor : cursor + 4] != CENTRAL_DIRECTORY:
            raise RuntimeError(f"bad central directory signature in {spec.name} at {cursor}")
        (
            _,
            version_made,
            version_needed,
            flags,
            method,
            mod_time,
            mod_date,
            crc32,
            compressed_size,
            uncompressed_size,
            filename_length,
            extra_length,
            comment_length,
            disk_start,
            internal_attr,
            external_attr,
            local_header_offset,
        ) = struct.unpack_from("<4s6H3L5H2L", cd, cursor)
        name_start = cursor + 46
        name_end = name_start + filename_length
        extra_end = name_end + extra_length
        filename = cd[name_start:name_end].decode("utf-8")
        extra = cd[name_end:extra_end]
        z_comp, z_uncomp, z_offset = parse_zip64_extra(
            extra=extra,
            compressed_size=compressed_size,
            uncompressed_size=uncompressed_size,
            local_header_offset=local_header_offset,
        )
        rows.append(
            {
                "archive": spec.name,
                "member_path": filename,
                "compression_method": method,
                "compressed_size_bytes": z_comp,
                "uncompressed_size_bytes": z_uncomp,
                "crc32": f"{crc32:08x}",
                "local_header_offset_bytes": z_offset,
                "general_purpose_flags": flags,
                "version_needed": version_needed,
                "version_made": version_made,
                "disk_start": disk_start,
                "internal_attr": internal_attr,
                "external_attr": external_attr,
                "mod_time": mod_time,
                "mod_date": mod_date,
                "central_directory_offset_bytes": cd_offset,
                "central_directory_size_bytes": cd_size,
                "central_directory_entry_count": total_entries,
            }
        )
        cursor = extra_end + comment_length
    if len(rows) != total_entries:
        raise RuntimeError(f"{spec.name} central directory expected {total_entries}, got {len(rows)}")
    return rows


def extract_member(spec: ArchiveSpec, entry: dict[str, object]) -> bytes:
    offset = int(entry["local_header_offset_bytes"])
    local = fetch_archive_range(spec, offset, 30)
    if local[:4] != LOCAL_FILE_HEADER:
        raise RuntimeError(f"bad local header for {entry['member_path']}")
    (
        _,
        _version_needed,
        _flags,
        method,
        _mod_time,
        _mod_date,
        _crc32,
        _compressed_size,
        _uncompressed_size,
        filename_length,
        extra_length,
    ) = struct.unpack_from("<4s5H3L2H", local, 0)
    compressed_start = offset + 30 + filename_length + extra_length
    compressed = fetch_archive_range(spec, compressed_start, int(entry["compressed_size_bytes"]))
    if method == 8:
        raw = zlib.decompress(compressed, -15)
    elif method == 0:
        raw = compressed
    else:
        raise RuntimeError(f"unsupported compression method {method} for {entry['member_path']}")
    expected_size = int(entry["uncompressed_size_bytes"])
    if len(raw) != expected_size:
        raise RuntimeError(f"uncompressed size mismatch for {entry['member_path']}: {len(raw)} vs {expected_size}")
    expected_crc = int(str(entry["crc32"]), 16)
    actual_crc = binascii.crc32(raw) & 0xFFFFFFFF
    if actual_crc != expected_crc:
        raise RuntimeError(f"CRC mismatch for {entry['member_path']}: {actual_crc:08x} vs {expected_crc:08x}")
    return raw


def percentile(values: list[float], p: float) -> float:
    clean = sorted(v for v in values if math.isfinite(v))
    if not clean:
        return math.nan
    k = (len(clean) - 1) * p / 100.0
    lower = int(math.floor(k))
    upper = int(math.ceil(k))
    if lower == upper:
        return clean[lower]
    fraction = k - lower
    return clean[lower] * (1.0 - fraction) + clean[upper] * fraction


def parse_float(row: dict[str, str], key: str, default: float = math.nan) -> float:
    try:
        return float(row.get(key, ""))
    except (TypeError, ValueError):
        return default


def vector_norm(row: dict[str, str], keys: tuple[str, str, str]) -> float:
    values = [parse_float(row, key) for key in keys]
    if any(not math.isfinite(value) for value in values):
        return math.nan
    return math.sqrt(sum(value * value for value in values))


def member_flight_id(member_path: str) -> tuple[str, str]:
    parts = member_path.split("/")
    if len(parts) < 2:
        return member_path, ""
    flight_id = parts[1]
    match = re.match(r"flight-\d+[ap]-(.+)", flight_id)
    return flight_id, match.group(1) if match else ""


def load_current_drag_reference() -> dict[str, float]:
    rows = read_rows(AIRFRAME_REFERENCE)
    for row in rows:
        if (
            row.get("row_type") == "current_vs_ratm_speed_floor"
            and row.get("preset") == "racingQuad"
            and row.get("axis") == "x"
        ):
            return {
                "weight_n": float(row["weight_n"]),
                "max_thrust_n": float(row["current_total_max_thrust_n"]),
                "horizontal_margin_n": float(row["current_horizontal_thrust_margin_n"]),
                "quadratic_c_n_per_m_s2": float(row["current_total_quadratic_c_n_per_m_s2"]),
                "readme_floor_drag_n": float(row["current_drag_force_at_speed_floor_n"]),
                "readme_floor_required_total_over_max": float(row["required_total_thrust_over_current_total_max"]),
            }
    raise LookupError("missing racingQuad RATM drag reference row")


def current_drag_metrics(speed_m_s: float, current: dict[str, float]) -> dict[str, float]:
    drag = current["quadratic_c_n_per_m_s2"] * speed_m_s * speed_m_s
    required_total = math.hypot(current["weight_n"], drag)
    return {
        "current_racing_x_drag_at_vmax_n": drag,
        "current_racing_x_drag_at_vmax_over_margin": drag / current["horizontal_margin_n"],
        "current_racing_required_total_thrust_over_max_at_vmax": required_total / current["max_thrust_n"],
        "current_racing_tilt_deg_for_drag_balance_at_vmax": math.degrees(math.atan2(drag, current["weight_n"])),
    }


def analyze_csv_member(
    spec: ArchiveSpec,
    entry: dict[str, object],
    current: dict[str, float],
) -> dict[str, object]:
    raw = extract_member(spec, entry)
    text = io.TextIOWrapper(io.BytesIO(raw), encoding="utf-8", newline="")
    reader = csv.DictReader(text)

    speeds: list[float] = []
    horizontal_speeds: list[float] = []
    vbat_values: list[float] = []
    thrust_sums: list[float] = []
    thrust_means: list[float] = []
    channel_thrust_values: list[float] = []
    gyro_norms: list[float] = []
    imu_accel_norms: list[float] = []
    residuals: list[float] = []
    velocity_accel_norms: list[float] = []

    row_count = 0
    valid_velocity_rows = 0
    first_elapsed = math.nan
    last_elapsed = math.nan
    max_speed = -math.inf
    max_row: dict[str, str] = {}
    previous_velocity: tuple[float, float, float, float] | None = None

    for row in reader:
        row_count += 1
        elapsed = parse_float(row, "elapsed_time")
        if math.isfinite(elapsed):
            if not math.isfinite(first_elapsed):
                first_elapsed = elapsed
            last_elapsed = elapsed

        vx = parse_float(row, "drone_velocity_linear_x")
        vy = parse_float(row, "drone_velocity_linear_y")
        vz = parse_float(row, "drone_velocity_linear_z")
        if all(math.isfinite(value) for value in (vx, vy, vz)):
            valid_velocity_rows += 1
            speed = math.sqrt(vx * vx + vy * vy + vz * vz)
            horizontal_speed = math.sqrt(vx * vx + vy * vy)
            speeds.append(speed)
            horizontal_speeds.append(horizontal_speed)
            if previous_velocity and math.isfinite(elapsed):
                previous_elapsed, previous_vx, previous_vy, previous_vz = previous_velocity
                dt = elapsed - previous_elapsed
                if dt > 0:
                    ax = (vx - previous_vx) / dt
                    ay = (vy - previous_vy) / dt
                    az = (vz - previous_vz) / dt
                    velocity_accel_norms.append(math.sqrt(ax * ax + ay * ay + az * az))
            if math.isfinite(elapsed):
                previous_velocity = (elapsed, vx, vy, vz)
            if speed > max_speed:
                max_speed = speed
                max_row = dict(row)
                max_row["_speed_m_s"] = f"{speed:.12g}"
                max_row["_horizontal_speed_m_s"] = f"{horizontal_speed:.12g}"

        vbat = parse_float(row, "vbat")
        if math.isfinite(vbat):
            vbat_values.append(vbat)

        thrust = [parse_float(row, f"thrust[{idx}]") for idx in range(4)]
        if all(math.isfinite(value) for value in thrust):
            total = sum(thrust)
            thrust_sums.append(total)
            thrust_means.append(total / 4.0)

        channel_thrust = parse_float(row, "channels_thrust")
        if math.isfinite(channel_thrust):
            channel_thrust_values.append(channel_thrust)

        gyro_norm = vector_norm(row, ("gyro_x", "gyro_y", "gyro_z"))
        if math.isfinite(gyro_norm):
            gyro_norms.append(gyro_norm)

        accel_norm = vector_norm(row, ("accel_x", "accel_y", "accel_z"))
        if math.isfinite(accel_norm):
            imu_accel_norms.append(accel_norm)

        residual = parse_float(row, "drone_residual")
        if math.isfinite(residual):
            residuals.append(residual)

    if max_speed <= 0 or not max_row:
        raise RuntimeError(f"no velocity samples in {entry['member_path']}")

    duration = last_elapsed - first_elapsed if math.isfinite(first_elapsed) and math.isfinite(last_elapsed) else math.nan
    sample_rate = (row_count - 1) / duration if duration and duration > 0 else math.nan
    flight_id, profile = member_flight_id(str(entry["member_path"]))
    comparison = current_drag_metrics(max_speed, current)

    metrics: dict[str, object] = {
        "row_type": "ratm_flight_metric",
        "archive": spec.name,
        "flight_id": flight_id,
        "profile": profile,
        "member_path": entry["member_path"],
        "source_url": RELEASE_URL,
        "compressed_size_bytes": entry["compressed_size_bytes"],
        "uncompressed_size_bytes": entry["uncompressed_size_bytes"],
        "row_count": row_count,
        "valid_velocity_row_count": valid_velocity_rows,
        "duration_s": duration,
        "sample_rate_hz_est": sample_rate,
        "speed_max_m_s": max_speed,
        "speed_p99_m_s": percentile(speeds, 99),
        "speed_p95_m_s": percentile(speeds, 95),
        "speed_p50_m_s": percentile(speeds, 50),
        "horizontal_speed_max_m_s": max(horizontal_speeds),
        "vbat_min_v": min(vbat_values),
        "vbat_p05_v": percentile(vbat_values, 5),
        "vbat_p50_v": percentile(vbat_values, 50),
        "vbat_at_vmax_v": parse_float(max_row, "vbat"),
        "vbat_sag_from_nominal_at_vmax_v": RATM_BATTERY_NOMINAL_V - parse_float(max_row, "vbat"),
        "thrust_sum_p50": percentile(thrust_sums, 50),
        "thrust_sum_p95": percentile(thrust_sums, 95),
        "thrust_sum_max": max(thrust_sums),
        "thrust_mean_p50": percentile(thrust_means, 50),
        "thrust_mean_p95": percentile(thrust_means, 95),
        "thrust_mean_at_vmax": sum(parse_float(max_row, f"thrust[{idx}]") for idx in range(4)) / 4.0,
        "channels_thrust_p50_us": percentile(channel_thrust_values, 50),
        "channels_thrust_p95_us": percentile(channel_thrust_values, 95),
        "channels_thrust_at_vmax_us": parse_float(max_row, "channels_thrust"),
        "gyro_norm_p95_rad_s": percentile(gyro_norms, 95),
        "gyro_norm_max_rad_s": max(gyro_norms),
        "imu_accel_norm_p95_m_s2": percentile(imu_accel_norms, 95),
        "imu_accel_norm_max_m_s2": max(imu_accel_norms),
        "velocity_derived_accel_norm_p95_m_s2": percentile(velocity_accel_norms, 95),
        "velocity_derived_accel_norm_max_m_s2": max(velocity_accel_norms) if velocity_accel_norms else math.nan,
        "drone_residual_p50": percentile(residuals, 50),
        "drone_residual_p95": percentile(residuals, 95),
        "elapsed_time_at_vmax_s": parse_float(max_row, "elapsed_time"),
        "vx_at_vmax_m_s": parse_float(max_row, "drone_velocity_linear_x"),
        "vy_at_vmax_m_s": parse_float(max_row, "drone_velocity_linear_y"),
        "vz_at_vmax_m_s": parse_float(max_row, "drone_velocity_linear_z"),
        "roll_at_vmax_rad": parse_float(max_row, "drone_roll"),
        "pitch_at_vmax_rad": parse_float(max_row, "drone_pitch"),
        "yaw_at_vmax_rad": parse_float(max_row, "drone_yaw"),
        "speed_max_over_readme_floor": max_speed / README_SPEED_FLOOR_M_S,
        **comparison,
    }
    for idx in range(4):
        metrics[f"thrust_{idx}_at_vmax"] = parse_float(max_row, f"thrust[{idx}]")
    return metrics


def build_file_inventory(archive_entries: dict[str, list[dict[str, object]]]) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for spec in ARCHIVES.values():
        if spec.name not in archive_entries:
            continue
        rows.append(
            {
                "row_type": "ratm_archive",
                "archive": spec.name,
                "chunk_count": len(spec.chunk_sizes),
                "chunk_names": ";".join(spec.chunk_names),
                "chunk_sizes_bytes": ";".join(str(size) for size in spec.chunk_sizes),
                "total_size_bytes": spec.total_size,
                "total_size_gib": spec.total_size / 1024**3,
                "release_url": RELEASE_URL,
                "downloader_url": DOWNLOADER_URL,
            }
        )
        for entry in sorted(archive_entries[spec.name], key=lambda row: str(row["member_path"])):
            if not str(entry["member_path"]).endswith("_500hz_freq_sync.csv"):
                continue
            flight_id, profile = member_flight_id(str(entry["member_path"]))
            chunk_index = ""
            for idx, (_chunk_name, start, end) in enumerate(spec.chunk_bounds, start=1):
                if start <= int(entry["local_header_offset_bytes"]) < end:
                    chunk_index = idx
                    break
            rows.append(
                {
                    "row_type": "ratm_500hz_csv",
                    "archive": spec.name,
                    "flight_id": flight_id,
                    "profile": profile,
                    "member_path": entry["member_path"],
                    "compression_method": entry["compression_method"],
                    "compressed_size_bytes": entry["compressed_size_bytes"],
                    "compressed_size_mb": int(entry["compressed_size_bytes"]) / 1_000_000,
                    "uncompressed_size_bytes": entry["uncompressed_size_bytes"],
                    "uncompressed_size_mb": int(entry["uncompressed_size_bytes"]) / 1_000_000,
                    "local_header_offset_bytes": entry["local_header_offset_bytes"],
                    "local_header_chunk_index": chunk_index,
                    "crc32": entry["crc32"],
                    "release_url": RELEASE_URL,
                }
            )
    return rows


def write_window_reference(
    specs_by_name: dict[str, ArchiveSpec],
    entries_by_member: dict[str, dict[str, object]],
    metrics: list[dict[str, object]],
    current: dict[str, float],
    top_window_count: int,
) -> list[dict[str, object]]:
    selected = sorted(metrics, key=lambda row: float(row["speed_max_m_s"]), reverse=True)[:top_window_count]
    rows: list[dict[str, object]] = []
    for rank, metric in enumerate(selected, start=1):
        archive = str(metric["archive"])
        member_path = str(metric["member_path"])
        raw = extract_member(specs_by_name[archive], entries_by_member[member_path])
        target_time = float(metric["elapsed_time_at_vmax_s"])
        start_time = target_time - WINDOW_SECONDS
        end_time = target_time + WINDOW_SECONDS
        text = io.TextIOWrapper(io.BytesIO(raw), encoding="utf-8", newline="")
        reader = csv.DictReader(text)
        for row in reader:
            elapsed = parse_float(row, "elapsed_time")
            if not math.isfinite(elapsed) or elapsed < start_time or elapsed > end_time:
                continue
            vx = parse_float(row, "drone_velocity_linear_x")
            vy = parse_float(row, "drone_velocity_linear_y")
            vz = parse_float(row, "drone_velocity_linear_z")
            speed = math.sqrt(vx * vx + vy * vy + vz * vz)
            horizontal_speed = math.sqrt(vx * vx + vy * vy)
            drag = current_drag_metrics(speed, current)
            flight_id, profile = member_flight_id(member_path)
            out: dict[str, object] = {
                "row_type": "ratm_vmax_window_sample",
                "window_rank": rank,
                "archive": archive,
                "flight_id": flight_id,
                "profile": profile,
                "member_path": member_path,
                "offset_from_vmax_s": elapsed - target_time,
                "speed_m_s": speed,
                "horizontal_speed_m_s": horizontal_speed,
                "current_racing_x_drag_n": drag["current_racing_x_drag_at_vmax_n"],
                "current_racing_required_total_thrust_over_max": drag[
                    "current_racing_required_total_thrust_over_max_at_vmax"
                ],
                "is_vmax_sample": abs(elapsed - target_time) <= (0.5 / SYNC_RATE_HZ),
            }
            for column in WINDOW_COLUMNS:
                out[column] = row.get(column, "")
            rows.append(out)
    write_csv(WINDOW_REFERENCE, rows)
    return rows


def add_metric(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path,
    source_url: str = "",
    evidence_role: str = "",
    note: str = "",
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": value_text(value),
            "unit": unit,
            "source_file": repo_path(source_file),
            "source_url": source_url,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def add_source_inventory(packet: list[dict[str, str]], inventory_rows: list[dict[str, object]]) -> None:
    source_metrics = [
        ("flight_count_total", 36, "count"),
        ("flight_count_autonomous", 18, "count"),
        ("flight_count_piloted", 18, "count"),
        ("sync_csv_rate_hz", SYNC_RATE_HZ, "Hz"),
        ("has_thrust_commands", 1, "boolean"),
        ("has_vbat", 1, "boolean"),
        ("has_imu", 1, "boolean"),
        ("has_mocap_pose_velocity", 1, "boolean"),
        ("readme_speed_floor_m_s", README_SPEED_FLOOR_M_S, "m/s"),
        ("release_total_size_gib", sum(spec.total_size for spec in ARCHIVES.values()) / 1024**3, "GiB"),
        ("license", "CC BY 4.0", "text"),
        ("paper_doi", PAPER_DOI_URL, "url"),
        ("bom_url", BOM_URL, "url"),
        ("betaflight_config_url", BETAFIGHT_URL, "url"),
    ]
    for metric, value, unit in source_metrics:
        add_metric(
            packet,
            row_type="ratm_packet_source_inventory",
            name="Race_Against_the_Machine_dataset",
            metric=metric,
            value=value,
            unit=unit,
            source_file=PACKET,
            source_url=README_URL,
            evidence_role="open_high_speed_fpv_log_dataset",
            note="Open 5-inch FPV racing dataset with synchronized commands, battery voltage, IMU, and mocap pose/velocity.",
        )

    hardware_metrics = [
        ("motor_kv_rpm_per_v", RATM_MOTOR_KV, "rpm/V"),
        ("esc_current_a", RATM_ESC_CURRENT_A, "A"),
        ("battery_cells", 6, "count"),
        ("battery_nominal_v", RATM_BATTERY_NOMINAL_V, "V"),
        ("battery_capacity_ah", RATM_BATTERY_CAPACITY_AH, "Ah"),
        ("battery_c_rating", RATM_BATTERY_C_RATING, "C"),
        ("battery_listed_pack_current_a", RATM_LISTED_PACK_CURRENT_A, "A"),
        ("prop_radius_m_from_model_name", RATM_PROP_RADIUS_M, "m"),
    ]
    for metric, value, unit in hardware_metrics:
        add_metric(
            packet,
            row_type="ratm_packet_source_inventory",
            name="RATM_open_design_hardware",
            metric=metric,
            value=value,
            unit=unit,
            source_file=PACKET,
            source_url=BOM_URL,
            evidence_role="hardware_context",
            note="Use as platform context for comparing current, voltage, motor KV, and prop size, not as a fitted model by itself.",
        )

    for row in inventory_rows:
        if row.get("row_type") != "ratm_archive":
            continue
        name = f"RATM_{row['archive']}_release_archive"
        for metric, unit in [
            ("chunk_count", "count"),
            ("total_size_bytes", "bytes"),
            ("total_size_gib", "GiB"),
        ]:
            add_metric(
                packet,
                row_type="ratm_packet_release_inventory",
                name=name,
                metric=metric,
                value=row[metric],
                unit=unit,
                source_file=FILE_INVENTORY,
                source_url=RELEASE_URL,
                evidence_role="release_zip_inventory",
            )


def add_flight_metric_rows(
    packet: list[dict[str, str]],
    metrics: list[dict[str, object]],
) -> None:
    metric_units = [
        ("row_count", "count"),
        ("duration_s", "s"),
        ("sample_rate_hz_est", "Hz"),
        ("speed_max_m_s", "m/s"),
        ("speed_p99_m_s", "m/s"),
        ("speed_p95_m_s", "m/s"),
        ("speed_p50_m_s", "m/s"),
        ("horizontal_speed_max_m_s", "m/s"),
        ("vbat_min_v", "V"),
        ("vbat_p05_v", "V"),
        ("vbat_p50_v", "V"),
        ("vbat_at_vmax_v", "V"),
        ("vbat_sag_from_nominal_at_vmax_v", "V"),
        ("thrust_sum_p95", "normalized command sum"),
        ("thrust_mean_p95", "normalized command"),
        ("thrust_mean_at_vmax", "normalized command"),
        ("channels_thrust_p95_us", "us"),
        ("channels_thrust_at_vmax_us", "us"),
        ("gyro_norm_p95_rad_s", "rad/s"),
        ("imu_accel_norm_p95_m_s2", "m/s^2"),
        ("velocity_derived_accel_norm_p95_m_s2", "m/s^2"),
        ("drone_residual_p95", "mocap residual"),
        ("elapsed_time_at_vmax_s", "s"),
        ("vx_at_vmax_m_s", "m/s"),
        ("vy_at_vmax_m_s", "m/s"),
        ("vz_at_vmax_m_s", "m/s"),
        ("roll_at_vmax_rad", "rad"),
        ("pitch_at_vmax_rad", "rad"),
        ("yaw_at_vmax_rad", "rad"),
        ("speed_max_over_readme_floor", "ratio"),
        ("current_racing_x_drag_at_vmax_n", "N"),
        ("current_racing_x_drag_at_vmax_over_margin", "ratio"),
        ("current_racing_required_total_thrust_over_max_at_vmax", "ratio"),
        ("current_racing_tilt_deg_for_drag_balance_at_vmax", "deg"),
    ]
    for row in metrics:
        name = f"{row['archive']}:{row['flight_id']}"
        for metric, unit in metric_units:
            add_metric(
                packet,
                row_type="ratm_packet_flight_metric",
                name=name,
                metric=metric,
                value=row.get(metric, ""),
                unit=unit,
                source_file=FLIGHT_METRICS,
                source_url=RELEASE_URL,
                evidence_role="range_extracted_500hz_csv_metric",
                note="Derived from the RATM 500Hz synchronized CSV using HTTP Range extraction; not a wind-tunnel drag fit.",
            )


def add_group_summary(
    packet: list[dict[str, str]],
    metrics: list[dict[str, object]],
    window_rows: list[dict[str, object]],
) -> None:
    def group_rows(group: str) -> list[dict[str, object]]:
        if group == "all":
            return metrics
        return [row for row in metrics if row["archive"] == group]

    for group in ["all", "autonomous", "piloted"]:
        rows = group_rows(group)
        if not rows:
            continue
        fastest = max(rows, key=lambda row: float(row["speed_max_m_s"]))
        count_over_floor = sum(1 for row in rows if float(row["speed_max_m_s"]) >= README_SPEED_FLOOR_M_S)
        summary_metrics = [
            ("flight_count", len(rows), "count"),
            ("total_row_count", sum(int(row["row_count"]) for row in rows), "count"),
            ("fastest_member", fastest["member_path"], "path"),
            ("speed_max_m_s", fastest["speed_max_m_s"], "m/s"),
            ("speed_p99_max_m_s", max(float(row["speed_p99_m_s"]) for row in rows), "m/s"),
            ("flight_count_ge_21m_s", count_over_floor, "count"),
            ("flight_fraction_ge_21m_s", count_over_floor / len(rows), "ratio"),
            ("vbat_min_across_group_v", min(float(row["vbat_min_v"]) for row in rows), "V"),
            ("current_drag_at_fastest_vmax_n", fastest["current_racing_x_drag_at_vmax_n"], "N"),
            (
                "current_required_total_thrust_over_max_at_fastest_vmax",
                fastest["current_racing_required_total_thrust_over_max_at_vmax"],
                "ratio",
            ),
            (
                "current_drag_over_margin_at_fastest_vmax",
                fastest["current_racing_x_drag_at_vmax_over_margin"],
                "ratio",
            ),
        ]
        for metric, value, unit in summary_metrics:
            add_metric(
                packet,
                row_type="ratm_packet_summary",
                name=f"ratm_{group}_summary",
                metric=metric,
                value=value,
                unit=unit,
                source_file=FLIGHT_METRICS,
                source_url=RELEASE_URL,
                evidence_role="compact_handoff_summary",
            )

    add_metric(
        packet,
        row_type="ratm_packet_summary",
        name="ratm_vmax_window_summary",
        metric="window_sample_row_count",
        value=len(window_rows),
        unit="count",
        source_file=WINDOW_REFERENCE,
        source_url=RELEASE_URL,
        evidence_role="compact_handoff_summary",
        note=f"Rows cover +/-{WINDOW_SECONDS:g} s around the fastest sample in the selected flights.",
    )
    add_metric(
        packet,
        row_type="ratm_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "RATM 500Hz CSVs are trajectory/control/voltage flight logs. Use them to constrain speed envelope, "
            "control commands, voltage sag shape, and feasibility of drag/thrust models. Do not treat peak-speed "
            "comparisons as isolated drag coefficients without wind, attitude, and thrust reconstruction."
        ),
        unit="text",
        source_file=PACKET,
        source_url=README_URL,
        evidence_role="method_caveat",
    )


def build_packet(
    inventory_rows: list[dict[str, object]],
    metrics: list[dict[str, object]],
    window_rows: list[dict[str, object]],
) -> list[dict[str, str]]:
    packet: list[dict[str, str]] = []
    add_source_inventory(packet, inventory_rows)
    add_flight_metric_rows(packet, metrics)
    add_group_summary(packet, metrics, window_rows)
    return packet


def sync_summary(packet_rows: Iterable[dict[str, str]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("ratm_packet_")]
    added: list[dict[str, str]] = []
    for row in packet_rows:
        added.append(
            {
                "category": row["row_type"],
                "name": row["name"],
                "metric": row["metric"],
                "value": row["value"],
                "unit": row["unit"],
                "source": row.get("source_url") or row.get("source_file", ""),
            }
        )
    write_csv(SUMMARY, kept + added)
    return len(added)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--archives",
        default="autonomous,piloted",
        help="Comma-separated archive names to process. Default: autonomous,piloted.",
    )
    parser.add_argument(
        "--top-window-count",
        type=int,
        default=6,
        help="Number of fastest flights for which to save +/-1 s windows.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    archive_names = [name.strip() for name in args.archives.split(",") if name.strip()]
    unknown = sorted(set(archive_names) - set(ARCHIVES))
    if unknown:
        raise ValueError(f"unknown archive name(s): {', '.join(unknown)}")

    current = load_current_drag_reference()
    archive_entries: dict[str, list[dict[str, object]]] = {}
    csv_entries: list[dict[str, object]] = []
    for archive_name in archive_names:
        spec = ARCHIVES[archive_name]
        entries = parse_central_directory(spec)
        archive_entries[archive_name] = entries
        csv_entries.extend(
            sorted(
                [entry for entry in entries if str(entry["member_path"]).endswith("_500hz_freq_sync.csv")],
                key=lambda entry: str(entry["member_path"]),
            )
        )

    inventory_rows = build_file_inventory(archive_entries)
    specs_by_name = {name: ARCHIVES[name] for name in archive_names}
    entries_by_member = {str(entry["member_path"]): entry for entry in csv_entries}

    flight_metrics: list[dict[str, object]] = []
    for entry in csv_entries:
        archive = str(entry["archive"])
        member = str(entry["member_path"])
        print(f"Extracting {member}")
        flight_metrics.append(analyze_csv_member(specs_by_name[archive], entry, current))

    window_rows = write_window_reference(
        specs_by_name=specs_by_name,
        entries_by_member=entries_by_member,
        metrics=flight_metrics,
        current=current,
        top_window_count=args.top_window_count,
    )
    packet_rows = build_packet(inventory_rows, flight_metrics, window_rows)

    write_csv(FILE_INVENTORY, inventory_rows)
    write_csv(FLIGHT_METRICS, flight_metrics)
    write_csv(PACKET, packet_rows)
    synced = sync_summary(packet_rows)

    print(f"Wrote {repo_path(FILE_INVENTORY)} with {len(inventory_rows)} rows")
    print(f"Wrote {repo_path(FLIGHT_METRICS)} with {len(flight_metrics)} rows")
    print(f"Wrote {repo_path(WINDOW_REFERENCE)} with {len(window_rows)} rows")
    print(f"Wrote {repo_path(PACKET)} with {len(packet_rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
