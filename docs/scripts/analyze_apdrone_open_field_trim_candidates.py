"""Filter APdrone open-field GPS dynamics into trim/drag candidate rows.

Outputs:
  docs/data/apdrone_open_field_trim_candidate_reference.csv

This script consumes docs/data/apdrone_open_field_speed_dynamics_reference.csv,
which already collapses APdrone Blackbox rows to unique GPS events and computes
local speed/course acceleration. The filters here deliberately avoid claiming a
full drag fit because the open-field logs do not expose attitude or wind.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
INPUT = DATA / "apdrone_open_field_speed_dynamics_reference.csv"
OUTPUT = DATA / "apdrone_open_field_trim_candidate_reference.csv"

SOURCE_PAGE = "https://data.mendeley.com/datasets/zgsvdtxnfh/2"
DOI = "10.17632/zgsvdtxnfh.2"
MASS_KG = 0.6284

FILTERS = [
    {
        "name": "strict_trim_v_ge_8",
        "family": "trim_candidate",
        "min_speed_m_s": 8.0,
        "max_abs_along_accel_m_s2": 0.5,
        "max_abs_cross_accel_m_s2": 0.75,
        "max_abs_turn_rate_deg_s": 5.0,
        "min_throttle_command": 20.0,
        "max_event_rows": 20,
        "note": "Strict near-steady, low-turning GPS-event filter.",
    },
    {
        "name": "relaxed_trim_v_ge_8",
        "family": "trim_candidate",
        "min_speed_m_s": 8.0,
        "max_abs_along_accel_m_s2": 1.0,
        "max_abs_cross_accel_m_s2": 1.5,
        "max_abs_turn_rate_deg_s": 8.0,
        "min_throttle_command": 20.0,
        "max_event_rows": 40,
        "note": "Relaxed near-steady filter for sparse high-speed data.",
    },
    {
        "name": "strict_trim_v_ge_12",
        "family": "trim_candidate",
        "min_speed_m_s": 12.0,
        "max_abs_along_accel_m_s2": 0.5,
        "max_abs_cross_accel_m_s2": 0.75,
        "max_abs_turn_rate_deg_s": 5.0,
        "min_throttle_command": 20.0,
        "max_event_rows": 20,
        "note": "Strict candidate filter above 12 m/s.",
    },
    {
        "name": "relaxed_trim_v_ge_12",
        "family": "trim_candidate",
        "min_speed_m_s": 12.0,
        "max_abs_along_accel_m_s2": 1.0,
        "max_abs_cross_accel_m_s2": 1.5,
        "max_abs_turn_rate_deg_s": 8.0,
        "min_throttle_command": 20.0,
        "max_event_rows": 40,
        "note": "Relaxed candidate filter above 12 m/s.",
    },
    {
        "name": "straight_fast_v_ge_16",
        "family": "straight_fast",
        "min_speed_m_s": 16.0,
        "max_abs_along_accel_m_s2": 3.0,
        "max_abs_cross_accel_m_s2": 2.5,
        "max_abs_turn_rate_deg_s": 8.0,
        "min_throttle_command": 20.0,
        "max_event_rows": 40,
        "note": "High-speed straight-ish events; not steady trim because along-track acceleration is allowed up to 3 m/s^2.",
    },
    {
        "name": "straight_decel_v_ge_8",
        "family": "straight_deceleration",
        "min_speed_m_s": 8.0,
        "min_deceleration_m_s2": 0.5,
        "max_abs_cross_accel_m_s2": 1.5,
        "max_abs_turn_rate_deg_s": 8.0,
        "min_throttle_command": 20.0,
        "max_event_rows": 40,
        "note": "Straight-ish deceleration events; apparent drag assumes zero horizontal thrust and is only diagnostic.",
    },
    {
        "name": "straight_decel_v_ge_12",
        "family": "straight_deceleration",
        "min_speed_m_s": 12.0,
        "min_deceleration_m_s2": 0.5,
        "max_abs_cross_accel_m_s2": 1.5,
        "max_abs_turn_rate_deg_s": 8.0,
        "min_throttle_command": 20.0,
        "max_event_rows": 40,
        "note": "Straight-ish deceleration events above 12 m/s.",
    },
    {
        "name": "straight_decel_v_ge_16",
        "family": "straight_deceleration",
        "min_speed_m_s": 16.0,
        "min_deceleration_m_s2": 0.5,
        "max_abs_cross_accel_m_s2": 2.5,
        "max_abs_turn_rate_deg_s": 8.0,
        "min_throttle_command": 20.0,
        "max_event_rows": 40,
        "note": "Fastest straight-ish deceleration events above 16 m/s.",
    },
]


def finite_or_blank(value: str | int | float) -> str | int | float:
    if isinstance(value, float) and not math.isfinite(value):
        return ""
    return value


def parse_float(value: str | None) -> float:
    try:
        return float(value)  # type: ignore[arg-type]
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


def ratio(numerator: float, denominator: float) -> float:
    if not (math.isfinite(numerator) and math.isfinite(denominator)) or abs(denominator) < 1.0e-12:
        return float("nan")
    return numerator / denominator


def read_events() -> list[dict[str, str]]:
    with INPUT.open(newline="", encoding="utf-8") as handle:
        return [
            row for row in csv.DictReader(handle)
            if row.get("row_type") == "apdrone_open_field_gps_dynamic_event"
        ]


def event_value(row: dict[str, str], field: str) -> float:
    return parse_float(row.get(field))


def matches_filter(row: dict[str, str], spec: dict[str, float | int | str]) -> bool:
    speed = event_value(row, "speed_m_s")
    along = event_value(row, "gps_along_track_accel_m_s2_1s_fit")
    cross = event_value(row, "gps_cross_track_accel_m_s2_1s_fit")
    turn = event_value(row, "gps_turn_rate_deg_s_1s_fit")
    throttle = event_value(row, "throttle_command")
    if not all(math.isfinite(value) for value in (speed, along, cross, turn, throttle)):
        return False
    if speed < float(spec.get("min_speed_m_s", 0.0)):
        return False
    if throttle < float(spec.get("min_throttle_command", 0.0)):
        return False
    max_abs_along = spec.get("max_abs_along_accel_m_s2")
    if max_abs_along is not None and abs(along) > float(max_abs_along):
        return False
    min_deceleration = spec.get("min_deceleration_m_s2")
    if min_deceleration is not None and along > -float(min_deceleration):
        return False
    if abs(cross) > float(spec.get("max_abs_cross_accel_m_s2", float("inf"))):
        return False
    if abs(turn) > float(spec.get("max_abs_turn_rate_deg_s", float("inf"))):
        return False
    return True


def summarize(filter_spec: dict[str, float | int | str], selected: list[dict[str, str]]) -> dict[str, str | int | float]:
    speeds = [event_value(row, "speed_m_s") for row in selected]
    along = [event_value(row, "gps_along_track_accel_m_s2_1s_fit") for row in selected]
    abs_along = [abs(value) for value in along if math.isfinite(value)]
    cross = [abs(event_value(row, "gps_cross_track_accel_m_s2_1s_fit")) for row in selected]
    turn = [abs(event_value(row, "gps_turn_rate_deg_s_1s_fit")) for row in selected]
    throttle = [event_value(row, "throttle_command") for row in selected]
    signed_coeff = [event_value(row, "apparent_signed_drag_coeff_from_speed_slope") for row in selected]
    decel_coeff = []
    decel_apdrone_ratio = []
    decel_racing_ratio = []
    for row, coeff in zip(selected, signed_coeff):
        if math.isfinite(coeff) and coeff > 0.0:
            decel_coeff.append(coeff)
            decel_apdrone_ratio.append(event_value(row, "apparent_coeff_over_apDrone_x"))
            decel_racing_ratio.append(event_value(row, "apparent_coeff_over_racingQuad_x"))
    flights = sorted({row.get("flight_filename", "") for row in selected if row.get("flight_filename")})
    return {
        "row_type": "apdrone_open_field_trim_filter_summary",
        "source_page": SOURCE_PAGE,
        "doi": DOI,
        "local_source_file": str(INPUT.relative_to(ROOT)).replace("\\", "/"),
        "filter_name": filter_spec["name"],
        "filter_family": filter_spec["family"],
        "min_speed_m_s": filter_spec.get("min_speed_m_s", ""),
        "max_abs_along_accel_m_s2": filter_spec.get("max_abs_along_accel_m_s2", ""),
        "min_deceleration_m_s2": filter_spec.get("min_deceleration_m_s2", ""),
        "max_abs_cross_accel_m_s2": filter_spec.get("max_abs_cross_accel_m_s2", ""),
        "max_abs_turn_rate_deg_s": filter_spec.get("max_abs_turn_rate_deg_s", ""),
        "min_throttle_command": filter_spec.get("min_throttle_command", ""),
        "event_count": len(selected),
        "source_file_count": len(flights),
        "speed_mean_m_s": mean(speeds),
        "speed_p50_m_s": percentile(speeds, 0.50),
        "speed_p90_m_s": percentile(speeds, 0.90),
        "speed_max_m_s": max((value for value in speeds if math.isfinite(value)), default=float("nan")),
        "throttle_mean": mean(throttle),
        "throttle_p50": percentile(throttle, 0.50),
        "throttle_p90": percentile(throttle, 0.90),
        "along_accel_p10_m_s2": percentile(along, 0.10),
        "along_accel_p50_m_s2": percentile(along, 0.50),
        "along_accel_p90_m_s2": percentile(along, 0.90),
        "abs_along_accel_p90_m_s2": percentile(abs_along, 0.90),
        "abs_cross_accel_p50_m_s2": percentile(cross, 0.50),
        "abs_cross_accel_p90_m_s2": percentile(cross, 0.90),
        "abs_turn_rate_p50_deg_s": percentile(turn, 0.50),
        "abs_turn_rate_p90_deg_s": percentile(turn, 0.90),
        "signed_apparent_drag_coeff_p10": percentile(signed_coeff, 0.10),
        "signed_apparent_drag_coeff_p50": percentile(signed_coeff, 0.50),
        "signed_apparent_drag_coeff_p90": percentile(signed_coeff, 0.90),
        "decelerating_event_count": len(decel_coeff),
        "decel_only_drag_coeff_p50": percentile(decel_coeff, 0.50),
        "decel_only_drag_coeff_p90": percentile(decel_coeff, 0.90),
        "decel_only_cda_p50_m2": 2.0 * percentile(decel_coeff, 0.50) / 1.225 if decel_coeff else float("nan"),
        "decel_only_coeff_p50_over_apDrone_x": percentile(decel_apdrone_ratio, 0.50),
        "decel_only_coeff_p50_over_racingQuad_x": percentile(decel_racing_ratio, 0.50),
        "note": filter_spec["note"],
    }


def event_output(filter_spec: dict[str, float | int | str], row: dict[str, str], rank: int) -> dict[str, str | int | float]:
    speed = event_value(row, "speed_m_s")
    along = event_value(row, "gps_along_track_accel_m_s2_1s_fit")
    signed_coeff = event_value(row, "apparent_signed_drag_coeff_from_speed_slope")
    return {
        "row_type": "apdrone_open_field_trim_candidate_event",
        "source_page": SOURCE_PAGE,
        "doi": DOI,
        "local_source_file": row.get("local_source_file", ""),
        "filter_name": filter_spec["name"],
        "filter_family": filter_spec["family"],
        "candidate_rank_by_speed": rank,
        "flight_filename": row.get("flight_filename", ""),
        "time_s": row.get("time_s", ""),
        "speed_m_s": speed,
        "throttle_command": event_value(row, "throttle_command"),
        "current_a_raw_per_amp_20": event_value(row, "current_a_raw_per_amp_20"),
        "vbat_v": event_value(row, "vbat_v"),
        "gps_ground_course_deg": event_value(row, "gps_ground_course_deg"),
        "gps_along_track_accel_m_s2_1s_fit": along,
        "gps_cross_track_accel_m_s2_1s_fit": event_value(row, "gps_cross_track_accel_m_s2_1s_fit"),
        "gps_turn_rate_deg_s_1s_fit": event_value(row, "gps_turn_rate_deg_s_1s_fit"),
        "apparent_signed_drag_coeff_from_speed_slope": signed_coeff,
        "apparent_signed_cda_from_speed_slope": event_value(row, "apparent_signed_cda_from_speed_slope"),
        "apparent_drag_force_from_signed_coeff_n": signed_coeff * speed * speed if math.isfinite(signed_coeff) and math.isfinite(speed) else float("nan"),
        "apparent_drag_force_from_accel_n": -MASS_KG * along if math.isfinite(along) else float("nan"),
        "apDrone_x_drag_force_n_at_speed": event_value(row, "apDrone_x_drag_force_n_at_speed"),
        "apparent_coeff_over_apDrone_x": event_value(row, "apparent_coeff_over_apDrone_x"),
        "racingQuad_x_drag_force_n_at_speed": event_value(row, "racingQuad_x_drag_force_n_at_speed"),
        "apparent_coeff_over_racingQuad_x": event_value(row, "apparent_coeff_over_racingQuad_x"),
    }


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
        for row in rows:
            writer.writerow({key: finite_or_blank(row.get(key, "")) for key in fieldnames})


def main() -> None:
    events = read_events()
    rows: list[dict[str, str | int | float]] = [
        {
            "row_type": "apdrone_open_field_trim_filter_method",
            "source_page": SOURCE_PAGE,
            "doi": DOI,
            "local_source_file": str(INPUT.relative_to(ROOT)).replace("\\", "/"),
            "note": "Filters use existing GPS-event speed/course dynamics. APdrone open-field logs lack attitude/wind, so rows identify trim/drag candidates and diagnostic bounds rather than fitting a final CdA.",
        }
    ]
    for filter_spec in FILTERS:
        selected = [row for row in events if matches_filter(row, filter_spec)]
        rows.append(summarize(filter_spec, selected))
        max_event_rows = int(filter_spec.get("max_event_rows", 0))
        for rank, row in enumerate(
            sorted(selected, key=lambda item: event_value(item, "speed_m_s"), reverse=True)[:max_event_rows],
            start=1,
        ):
            rows.append(event_output(filter_spec, row, rank))
    write_csv(OUTPUT, rows)
    print(f"Wrote {OUTPUT.relative_to(ROOT)} with {len(rows)} rows")


if __name__ == "__main__":
    main()
