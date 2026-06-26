"""Analyze APdrone open-field GPS speed dynamics for drag sanity checks.

Outputs:
  docs/data/apdrone_open_field_speed_dynamics_reference.csv

This is deliberately a diagnostic, not a direct CdA fit. The APdrone
open-field CSVs expose scalar GPS ground speed, GPS ground course, and commands,
but not attitude, wind, or measured horizontal thrust. We therefore use local
GPS-speed slopes to label acceleration/deceleration/quasi-steady segments, and
GPS velocity-vector slopes to flag turning/high-lateral-acceleration segments.
The "apparent coastdown" coefficient is only a lower-bound-style diagnostic
under the artificial no-propulsive-force assumption.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path

from airframe_runtime_drag_law import drag_force, equivalent_quadratic_c


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "apdrone_zgsvdtxnfh_v2"
OPEN_FIELD_DIR = RAW / "flight_archives" / "open_field"
DRAG_ENVELOPE = DATA / "apdrone_drag_speed_envelope_reference.csv"
OUTPUT = DATA / "apdrone_open_field_speed_dynamics_reference.csv"

SOURCE_PAGE = "https://data.mendeley.com/datasets/zgsvdtxnfh/2"
DOI = "10.17632/zgsvdtxnfh.2"
AIR_DENSITY = 1.225
APDRONE_MASS_KG = 0.6284

SPEED_BINS = [
    (0.0, 2.0),
    (2.0, 5.0),
    (5.0, 8.0),
    (8.0, 12.0),
    (12.0, 16.0),
    (16.0, 20.0),
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
    if not values:
        return float("nan")
    ordered = sorted(values)
    pos = (len(ordered) - 1) * q
    lo = math.floor(pos)
    hi = math.ceil(pos)
    if lo == hi:
        return ordered[int(pos)]
    weight = pos - lo
    return ordered[lo] * (1.0 - weight) + ordered[hi] * weight


def mean(values: list[float]) -> float:
    return sum(values) / len(values) if values else float("nan")


def stats(values: list[float]) -> dict[str, float]:
    clean = [value for value in values if math.isfinite(value)]
    if not clean:
        return {"count": 0, "mean": float("nan"), "p10": float("nan"), "p50": float("nan"), "p90": float("nan"), "min": float("nan"), "max": float("nan")}
    return {
        "count": len(clean),
        "mean": mean(clean),
        "p10": percentile(clean, 0.10),
        "p50": percentile(clean, 0.50),
        "p90": percentile(clean, 0.90),
        "min": min(clean),
        "max": max(clean),
    }


def find_header_line(path: Path) -> int:
    with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
        for index, line in enumerate(handle):
            if line.startswith('"loopIteration"') or line.startswith("loopIteration"):
                return index
    raise RuntimeError(f"Could not find Blackbox header in {path}")


def read_gps_events(path: Path) -> list[dict[str, str | int | float]]:
    header_line = find_header_line(path)
    events: list[dict[str, str | int | float]] = []
    last_key: tuple[str | None, str | None, str | None, str | None] | None = None

    with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
        for _ in range(header_line):
            next(handle)
        reader = csv.DictReader(handle)
        for row in reader:
            time_s = parse_float(row.get("time"), 1e6)
            speed_m_s = parse_float(row.get("GPS_speed"), 100.0)
            sats = parse_float(row.get("GPS_numSat"))
            if not (math.isfinite(time_s) and math.isfinite(speed_m_s) and sats > 0.0):
                continue
            key = (
                row.get("GPS_speed"),
                row.get("GPS_coord[0]"),
                row.get("GPS_coord[1]"),
                row.get("GPS_altitude"),
            )
            if key == last_key:
                continue
            last_key = key
            course_deg = parse_float(row.get("GPS_ground_course"), 10.0)
            course_rad = math.radians(course_deg) if math.isfinite(course_deg) else float("nan")
            velocity_east = speed_m_s * math.sin(course_rad) if math.isfinite(course_rad) else float("nan")
            velocity_north = speed_m_s * math.cos(course_rad) if math.isfinite(course_rad) else float("nan")
            events.append(
                {
                    "flight_filename": path.name,
                    "local_source_file": repo_path(path),
                    "time_s": time_s,
                    "speed_m_s": speed_m_s,
                    "gps_num_sat": sats,
                    "gps_altitude_m": parse_float(row.get("GPS_altitude"), 100.0),
                    "gps_ground_course_deg": course_deg,
                    "gps_velocity_east_m_s": velocity_east,
                    "gps_velocity_north_m_s": velocity_north,
                    "throttle_command": parse_float(row.get("rcCommands[3]")),
                    "current_a_raw_per_amp_20": parse_float(row.get("amperageLatest"), 20.0),
                    "vbat_v": parse_float(row.get("vbatLatest"), 100.0),
                    "gyro_norm_raw": math.sqrt(
                        parse_float(row.get("gyroADC[0]")) ** 2
                        + parse_float(row.get("gyroADC[1]")) ** 2
                        + parse_float(row.get("gyroADC[2]")) ** 2
                    ),
                    "acc_norm_raw": math.sqrt(
                        parse_float(row.get("accSmooth[0]")) ** 2
                        + parse_float(row.get("accSmooth[1]")) ** 2
                        + parse_float(row.get("accSmooth[2]")) ** 2
                    ),
                }
            )

    return events


def local_field_slope(events: list[dict[str, str | int | float]], index: int, half_width_s: float, field: str) -> tuple[float, int, float]:
    center_t = float(events[index]["time_s"])
    window = [
        event
        for event in events
        if abs(float(event["time_s"]) - center_t) <= half_width_s
        and math.isfinite(float(event.get(field, float("nan"))))
    ]
    if len(window) < 5:
        return float("nan"), len(window), float("nan")
    t_mean = mean([float(event["time_s"]) for event in window])
    value_mean = mean([float(event[field]) for event in window])
    denom = sum((float(event["time_s"]) - t_mean) ** 2 for event in window)
    if denom <= 0.0:
        return float("nan"), len(window), float("nan")
    slope = sum((float(event["time_s"]) - t_mean) * (float(event[field]) - value_mean) for event in window) / denom
    span = max(float(event["time_s"]) for event in window) - min(float(event["time_s"]) for event in window)
    return slope, len(window), span


def local_slope(events: list[dict[str, str | int | float]], index: int, half_width_s: float) -> tuple[float, int, float]:
    return local_field_slope(events, index, half_width_s, "speed_m_s")


def annotate_dynamics(events: list[dict[str, str | int | float]]) -> None:
    for index, event in enumerate(events):
        accel_1s, n_1s, span_1s = local_slope(events, index, 0.5)
        accel_2s, n_2s, span_2s = local_slope(events, index, 1.0)
        accel_east_1s, _, _ = local_field_slope(events, index, 0.5, "gps_velocity_east_m_s")
        accel_north_1s, _, _ = local_field_slope(events, index, 0.5, "gps_velocity_north_m_s")
        velocity_east = float(event.get("gps_velocity_east_m_s", float("nan")))
        velocity_north = float(event.get("gps_velocity_north_m_s", float("nan")))
        speed = float(event["speed_m_s"])
        if (
            speed > 0.5
            and math.isfinite(velocity_east)
            and math.isfinite(velocity_north)
            and math.isfinite(accel_east_1s)
            and math.isfinite(accel_north_1s)
        ):
            along_track_accel = (accel_east_1s * velocity_east + accel_north_1s * velocity_north) / speed
            cross_track_accel = (velocity_east * accel_north_1s - velocity_north * accel_east_1s) / speed
            vector_accel = math.hypot(accel_east_1s, accel_north_1s)
            turn_rate = math.degrees(cross_track_accel / speed)
        else:
            along_track_accel = float("nan")
            cross_track_accel = float("nan")
            vector_accel = float("nan")
            turn_rate = float("nan")
        apparent_signed_coeff = -APDRONE_MASS_KG * accel_1s / (speed * speed) if speed > 0.5 and math.isfinite(accel_1s) else float("nan")
        event.update(
            {
                "gps_accel_m_s2_1s_fit": accel_1s,
                "gps_accel_fit_window_count_1s": n_1s,
                "gps_accel_fit_window_span_s_1s": span_1s,
                "gps_accel_m_s2_2s_fit": accel_2s,
                "gps_accel_fit_window_count_2s": n_2s,
                "gps_accel_fit_window_span_s_2s": span_2s,
                "gps_accel_east_m_s2_1s_fit": accel_east_1s,
                "gps_accel_north_m_s2_1s_fit": accel_north_1s,
                "gps_vector_accel_m_s2_1s_fit": vector_accel,
                "gps_along_track_accel_m_s2_1s_fit": along_track_accel,
                "gps_cross_track_accel_m_s2_1s_fit": cross_track_accel,
                "gps_turn_rate_deg_s_1s_fit": turn_rate,
                "apparent_signed_drag_coeff_from_speed_slope": apparent_signed_coeff,
                "apparent_signed_cda_from_speed_slope": 2.0 * apparent_signed_coeff / AIR_DENSITY if math.isfinite(apparent_signed_coeff) else float("nan"),
                "dynamic_label_1s": dynamic_label(accel_1s),
            }
        )


def dynamic_label(accel: float) -> str:
    if not math.isfinite(accel):
        return "unresolved"
    if accel > 0.5:
        return "accelerating"
    if accel < -0.5:
        return "decelerating"
    return "quasi_steady"


def load_drag_models() -> dict[tuple[str, str], dict[str, float]]:
    models: dict[tuple[str, str], dict[str, float]] = {}
    if not DRAG_ENVELOPE.exists():
        return models
    with DRAG_ENVELOPE.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            if row.get("row_type") != "apdrone_drag_speed_constraint":
                continue
            preset = row.get("preset", "")
            axis = row.get("axis", "")
            linear = parse_float(row.get("linear_drag_coefficient_n_per_mps"))
            body = parse_float(row.get("body_drag_coefficient_n_per_mps2"))
            legacy_coeff = parse_float(row.get("total_drag_coefficient_n_per_mps2"))
            if not math.isfinite(linear):
                linear = 0.0
            if not math.isfinite(body) and math.isfinite(legacy_coeff):
                body = legacy_coeff
            if preset and axis and math.isfinite(body):
                models.setdefault((preset, axis), {"linear": linear, "body": body})
    return models


def add_method_rows(rows: list[dict[str, str | int | float]]) -> None:
    rows.append(
        {
            "row_type": "apdrone_speed_dynamics_method",
            "source_page": SOURCE_PAGE,
            "doi": DOI,
            "equation": "gps_accel=d(GPS_speed)/dt by local linear fit; vector_accel=d(GPS ground velocity)/dt from speed/course; apparent_coeff=-mass*gps_accel/speed^2",
            "window_1s_half_width_s": 0.5,
            "window_2s_half_width_s": 1.0,
            "mass_kg": APDRONE_MASS_KG,
            "air_density_kg_m3": AIR_DENSITY,
            "note": "GPS ground-speed/course dynamics only. Wind, attitude, vertical motion, and horizontal propulsive force are unknown; apparent drag coefficients are diagnostic, not fitted CdA. Project drag columns use runtime F=kV+cV^2 and speed-specific equivalent coefficients.",
        }
    )


def refresh_drag_model_columns(rows: list[dict[str, str | int | float]], models: dict[tuple[str, str], dict[str, float]]) -> None:
    for row in rows:
        row_type = row.get("row_type")
        if row_type == "apdrone_open_field_gps_dynamic_speed_bin_summary":
            speed = parse_float(str(row.get("speed_mean_m_s", "")))
            apparent = parse_float(str(row.get("apparent_decel_coeff_p50", "")))
            for preset in ("apDrone", "racingQuad"):
                for axis in ("x", "z"):
                    model = models.get((preset, axis), {})
                    linear = model.get("linear", float("nan"))
                    body = model.get("body", float("nan"))
                    coeff = equivalent_quadratic_c(linear, body, speed)
                    row[f"{preset}_{axis}_drag_coeff"] = coeff
                    row[f"{preset}_{axis}_drag_force_at_mean_speed_n"] = (
                        drag_force(linear, body, speed) if math.isfinite(coeff) else float("nan")
                    )
                    row[f"apparent_decel_coeff_p50_over_{preset}_{axis}"] = (
                        apparent / coeff if math.isfinite(apparent) and coeff > 0.0 else float("nan")
                    )
        elif row_type == "apdrone_open_field_gps_dynamic_event":
            speed = parse_float(str(row.get("speed_m_s", "")))
            apparent = parse_float(str(row.get("apparent_signed_drag_coeff_from_speed_slope", "")))
            for preset in ("apDrone", "racingQuad"):
                for axis in ("x", "z"):
                    model = models.get((preset, axis), {})
                    linear = model.get("linear", float("nan"))
                    body = model.get("body", float("nan"))
                    coeff = equivalent_quadratic_c(linear, body, speed)
                    row[f"{preset}_{axis}_drag_force_n_at_speed"] = (
                        drag_force(linear, body, speed) if math.isfinite(coeff) else float("nan")
                    )
                    row[f"apparent_coeff_over_{preset}_{axis}"] = (
                        apparent / coeff if math.isfinite(apparent) and coeff > 0.0 else float("nan")
                    )


def add_event_rows(rows: list[dict[str, str | int | float]], events: list[dict[str, str | int | float]], models: dict[tuple[str, str], dict[str, float]]) -> None:
    # Keep high-speed and deceleration rows; low-speed rows are summarized in bins.
    selected = [
        event
        for event in events
        if float(event["speed_m_s"]) >= 8.0
        or str(event.get("dynamic_label_1s")) == "decelerating" and float(event["speed_m_s"]) >= 5.0
    ]
    for event in selected:
        speed = float(event["speed_m_s"])
        row = {
            "row_type": "apdrone_open_field_gps_dynamic_event",
            "source_page": SOURCE_PAGE,
            "doi": DOI,
            **event,
        }
        for preset in ("apDrone", "racingQuad"):
            for axis in ("x", "z"):
                model = models.get((preset, axis), {})
                linear = model.get("linear", float("nan"))
                body = model.get("body", float("nan"))
                coeff = equivalent_quadratic_c(linear, body, speed)
                row[f"{preset}_{axis}_drag_force_n_at_speed"] = (
                    drag_force(linear, body, speed) if math.isfinite(coeff) else float("nan")
                )
                row[f"apparent_coeff_over_{preset}_{axis}"] = (
                    float(event["apparent_signed_drag_coeff_from_speed_slope"]) / coeff
                    if math.isfinite(float(event["apparent_signed_drag_coeff_from_speed_slope"])) and coeff > 0.0
                    else float("nan")
                )
        rows.append(row)


def add_flight_summary_rows(rows: list[dict[str, str | int | float]], events_by_file: dict[str, list[dict[str, str | int | float]]]) -> None:
    for filename, events in events_by_file.items():
        speeds = [float(event["speed_m_s"]) for event in events]
        accels = [float(event["gps_accel_m_s2_1s_fit"]) for event in events if math.isfinite(float(event["gps_accel_m_s2_1s_fit"]))]
        vector_accels = [float(event["gps_vector_accel_m_s2_1s_fit"]) for event in events if math.isfinite(float(event["gps_vector_accel_m_s2_1s_fit"]))]
        cross_accels = [abs(float(event["gps_cross_track_accel_m_s2_1s_fit"])) for event in events if math.isfinite(float(event["gps_cross_track_accel_m_s2_1s_fit"]))]
        rows.append(
            {
                "row_type": "apdrone_open_field_gps_dynamic_flight_summary",
                "source_page": SOURCE_PAGE,
                "doi": DOI,
                "flight_filename": filename,
                "local_source_file": str(events[0].get("local_source_file", "")) if events else "",
                "gps_event_count": len(events),
                "gps_event_duration_s": max(float(event["time_s"]) for event in events) - min(float(event["time_s"]) for event in events) if len(events) > 1 else "",
                "speed_mean_m_s": mean(speeds),
                "speed_p95_m_s": percentile(speeds, 0.95),
                "speed_max_m_s": max(speeds) if speeds else "",
                "accel_mean_m_s2": mean(accels),
                "accel_p10_m_s2": percentile(accels, 0.10),
                "accel_p50_m_s2": percentile(accels, 0.50),
                "accel_p90_m_s2": percentile(accels, 0.90),
                "vector_accel_p50_m_s2": percentile(vector_accels, 0.50),
                "vector_accel_p90_m_s2": percentile(vector_accels, 0.90),
                "abs_cross_track_accel_p50_m_s2": percentile(cross_accels, 0.50),
                "abs_cross_track_accel_p90_m_s2": percentile(cross_accels, 0.90),
                "accelerating_event_count": sum(1 for event in events if event.get("dynamic_label_1s") == "accelerating"),
                "quasi_steady_event_count": sum(1 for event in events if event.get("dynamic_label_1s") == "quasi_steady"),
                "decelerating_event_count": sum(1 for event in events if event.get("dynamic_label_1s") == "decelerating"),
            }
        )


def add_bin_summary_rows(rows: list[dict[str, str | int | float]], events: list[dict[str, str | int | float]], models: dict[tuple[str, str], dict[str, float]]) -> None:
    for lo, hi in SPEED_BINS:
        bucket = [
            event
            for event in events
            if lo <= float(event["speed_m_s"]) < hi and math.isfinite(float(event["gps_accel_m_s2_1s_fit"]))
        ]
        if not bucket:
            continue
        speeds = [float(event["speed_m_s"]) for event in bucket]
        accels = [float(event["gps_accel_m_s2_1s_fit"]) for event in bucket]
        vector_accels = [float(event["gps_vector_accel_m_s2_1s_fit"]) for event in bucket if math.isfinite(float(event["gps_vector_accel_m_s2_1s_fit"]))]
        along_accels = [float(event["gps_along_track_accel_m_s2_1s_fit"]) for event in bucket if math.isfinite(float(event["gps_along_track_accel_m_s2_1s_fit"]))]
        cross_accels = [abs(float(event["gps_cross_track_accel_m_s2_1s_fit"])) for event in bucket if math.isfinite(float(event["gps_cross_track_accel_m_s2_1s_fit"]))]
        turn_rates = [abs(float(event["gps_turn_rate_deg_s_1s_fit"])) for event in bucket if math.isfinite(float(event["gps_turn_rate_deg_s_1s_fit"]))]
        throttles = [float(event["throttle_command"]) for event in bucket if math.isfinite(float(event["throttle_command"]))]
        currents = [float(event["current_a_raw_per_amp_20"]) for event in bucket if math.isfinite(float(event["current_a_raw_per_amp_20"]))]
        decel_coeffs = [
            float(event["apparent_signed_drag_coeff_from_speed_slope"])
            for event in bucket
            if float(event["gps_accel_m_s2_1s_fit"]) < -0.5 and math.isfinite(float(event["apparent_signed_drag_coeff_from_speed_slope"]))
        ]
        row: dict[str, str | int | float] = {
            "row_type": "apdrone_open_field_gps_dynamic_speed_bin_summary",
            "source_page": SOURCE_PAGE,
            "doi": DOI,
            "speed_bin_m_s": f"{lo:g}-{hi:g}",
            "event_count": len(bucket),
            "flight_count": len({str(event["flight_filename"]) for event in bucket}),
            "speed_mean_m_s": mean(speeds),
            "speed_p50_m_s": percentile(speeds, 0.50),
            "speed_p90_m_s": percentile(speeds, 0.90),
            "speed_max_m_s": max(speeds),
            "accel_mean_m_s2": mean(accels),
            "accel_p10_m_s2": percentile(accels, 0.10),
            "accel_p50_m_s2": percentile(accels, 0.50),
            "accel_p90_m_s2": percentile(accels, 0.90),
            "vector_accel_p50_m_s2": percentile(vector_accels, 0.50),
            "vector_accel_p90_m_s2": percentile(vector_accels, 0.90),
            "along_track_accel_p50_m_s2": percentile(along_accels, 0.50),
            "along_track_accel_p90_m_s2": percentile(along_accels, 0.90),
            "abs_cross_track_accel_p50_m_s2": percentile(cross_accels, 0.50),
            "abs_cross_track_accel_p90_m_s2": percentile(cross_accels, 0.90),
            "abs_turn_rate_p50_deg_s": percentile(turn_rates, 0.50),
            "abs_turn_rate_p90_deg_s": percentile(turn_rates, 0.90),
            "accelerating_fraction": sum(1 for event in bucket if event["dynamic_label_1s"] == "accelerating") / len(bucket),
            "quasi_steady_fraction": sum(1 for event in bucket if event["dynamic_label_1s"] == "quasi_steady") / len(bucket),
            "decelerating_fraction": sum(1 for event in bucket if event["dynamic_label_1s"] == "decelerating") / len(bucket),
            "throttle_mean": mean(throttles),
            "throttle_p90": percentile(throttles, 0.90),
            "current_mean_a_raw_per_amp_20": mean(currents),
            "current_p90_a_raw_per_amp_20": percentile(currents, 0.90),
            "apparent_decel_coeff_count": len(decel_coeffs),
            "apparent_decel_coeff_p50": percentile(decel_coeffs, 0.50),
            "apparent_decel_coeff_p90": percentile(decel_coeffs, 0.90),
            "apparent_decel_cda_p50_m2": 2.0 * percentile(decel_coeffs, 0.50) / AIR_DENSITY if decel_coeffs else "",
            "note": "Acceleration is scalar GPS speed slope; vector fields use GPS speed/course. Apparent decel coefficient assumes no horizontal propulsive force and is not a physical drag fit.",
        }
        mean_speed = float(row["speed_mean_m_s"])
        for preset in ("apDrone", "racingQuad"):
            for axis in ("x", "z"):
                model = models.get((preset, axis), {})
                linear = model.get("linear", float("nan"))
                body = model.get("body", float("nan"))
                coeff = equivalent_quadratic_c(linear, body, mean_speed)
                row[f"{preset}_{axis}_drag_coeff"] = coeff
                row[f"{preset}_{axis}_drag_force_at_mean_speed_n"] = (
                    drag_force(linear, body, mean_speed) if math.isfinite(coeff) else float("nan")
                )
                row[f"apparent_decel_coeff_p50_over_{preset}_{axis}"] = (
                    percentile(decel_coeffs, 0.50) / coeff if decel_coeffs and coeff > 0.0 else float("nan")
                )
        rows.append(row)


def build_rows() -> list[dict[str, str | int | float]]:
    rows: list[dict[str, str | int | float]] = []
    add_method_rows(rows)
    models = load_drag_models()
    raw_paths = sorted(OPEN_FIELD_DIR.glob("Flight_*.csv"))
    if not raw_paths and OUTPUT.exists():
        with OUTPUT.open(newline="", encoding="utf-8") as handle:
            existing_rows: list[dict[str, str | int | float]] = [dict(row) for row in csv.DictReader(handle)]
        refresh_drag_model_columns(existing_rows, models)
        return [{key: finite_or_blank(value) for key, value in row.items()} for row in existing_rows]
    events_by_file: dict[str, list[dict[str, str | int | float]]] = {}
    for path in raw_paths:
        events = read_gps_events(path)
        annotate_dynamics(events)
        events_by_file[path.name] = events
    events = [event for file_events in events_by_file.values() for event in file_events]
    add_flight_summary_rows(rows, events_by_file)
    add_bin_summary_rows(rows, events, models)
    add_event_rows(rows, events, models)
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
