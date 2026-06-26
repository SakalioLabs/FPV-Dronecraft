"""Build a RATM high-speed thrust-vector feasibility packet.

Outputs:
  docs/data/ratm_thrust_vector_feasibility_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category ratm_thrust_vector_packet_*

This packet complements the RATM acceleration/drag residual packet. It uses
the already range-extracted 500 Hz windows to compare logged attitude and motor
command proxies against the current racingQuad drag-balance demand near
21 m/s. It is not an isolated CdA fit; it is a feasibility and sign/scale
handoff for the other simulator agent.
"""

from __future__ import annotations

import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path
from typing import Iterable

from airframe_runtime_drag_law import drag_force


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
WINDOW_REFERENCE = DATA / "ratm_high_speed_window_reference.csv"
ACCEL_PACKET = DATA / "ratm_accel_drag_residual_packet.csv"
OUTPUT = DATA / "ratm_thrust_vector_feasibility_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

SOURCE_URL = "https://github.com/tii-racing/drone-racing-dataset"
RELEASE_URL = "https://github.com/Drone-Racing/drone-racing-dataset/releases/tag/v3.0.0"
PAPER_DOI_URL = "https://doi.org/10.1109/LRA.2024.3371288"
SYNC_RATE_HZ = 500.0
GRAVITY_M_S2 = 9.80665
HIGH_SPEED_THRESHOLD_M_S = 21.0
SAMPLE_OFFSETS_S = (-0.50, -0.25, 0.0, 0.25, 0.50)


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


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


def to_float(value: object) -> float:
    try:
        result = float(str(value))
    except (TypeError, ValueError):
        return math.nan
    return result if math.isfinite(result) else math.nan


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def write_csv(path: Path, rows: Iterable[dict[str, object]]) -> None:
    row_list = list(rows)
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames: list[str] = []
    for row in row_list:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in row_list:
            writer.writerow({key: value_text(row.get(key)) for key in fieldnames})


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path | str,
    source_url: str = RELEASE_URL,
    evidence_role: str,
    note: str = "",
    **extra: object,
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": value,
            "unit": unit,
            "source_file": repo_path(source_file) if isinstance(source_file, Path) else source_file,
            "source_url": source_url,
            "evidence_role": evidence_role,
            "note": note,
            **extra,
        }
    )


def finite(values: Iterable[float]) -> list[float]:
    return [value for value in values if math.isfinite(value)]


def median(values: Iterable[float]) -> float:
    vals = finite(values)
    return statistics.median(vals) if vals else math.nan


def mean(values: Iterable[float]) -> float:
    vals = finite(values)
    return statistics.fmean(vals) if vals else math.nan


def percentile(values: Iterable[float], q: float) -> float:
    vals = sorted(finite(values))
    if not vals:
        return math.nan
    if len(vals) == 1:
        return vals[0]
    pos = (len(vals) - 1) * q
    lo = math.floor(pos)
    hi = math.ceil(pos)
    if lo == hi:
        return vals[int(pos)]
    return vals[lo] * (hi - pos) + vals[hi] * (pos - lo)


def safe_ratio(numerator: float, denominator: float, min_denominator: float = 1.0e-12) -> float:
    if not math.isfinite(numerator) or not math.isfinite(denominator) or abs(denominator) <= min_denominator:
        return math.nan
    return numerator / denominator


def clamp(value: float, low: float, high: float) -> float:
    return min(max(value, low), high)


def current_reference() -> dict[str, float]:
    packet_rows = read_rows(ACCEL_PACKET)
    metrics: dict[str, float] = {}
    for row in packet_rows:
        if row.get("row_type") != "ratm_accel_drag_packet_source_inventory":
            continue
        metrics[row.get("metric", "")] = to_float(row.get("value"))

    mass = metrics["current_racing_mass_kg"]
    weight = mass * GRAVITY_M_S2
    horizontal_margin = metrics["current_racing_horizontal_margin_n"]
    return {
        "mass_kg": mass,
        "weight_n": weight,
        "linear_drag_k_n_per_m_s": metrics.get("current_racing_linear_drag_k_n_per_m_s", 0.0),
        "body_drag_c_n_per_m_s2": metrics.get(
            "current_racing_body_drag_c_n_per_m_s2",
            metrics["current_racing_drag_c_n_per_m_s2"],
        ),
        "drag_c_n_per_m_s2": metrics["current_racing_drag_c_n_per_m_s2"],
        "horizontal_margin_n": horizontal_margin,
        "max_thrust_n": math.hypot(horizontal_margin, weight),
    }


def thrust_values(row: dict[str, str]) -> list[float]:
    return finite(to_float(row.get(f"thrust[{index}]")) for index in range(4))


def rotation_columns_body_to_world(roll: float, pitch: float, yaw: float) -> tuple[tuple[float, float, float], ...]:
    """Return body X/Y/Z axes in world coordinates for a ZYX Euler convention."""

    cr = math.cos(roll)
    sr = math.sin(roll)
    cp = math.cos(pitch)
    sp = math.sin(pitch)
    cy = math.cos(yaw)
    sy = math.sin(yaw)
    body_x = (cy * cp, sy * cp, -sp)
    body_y = (cy * sp * sr - sy * cr, sy * sp * sr + cy * cr, cp * sr)
    body_z = (cy * sp * cr + sy * sr, sy * sp * cr - cy * sr, cp * cr)
    return body_x, body_y, body_z


def dot(a: tuple[float, float, float], b: tuple[float, float, float]) -> float:
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def vector_norm(vec: tuple[float, float, float]) -> float:
    return math.sqrt(dot(vec, vec))


def analyze_sample(row: dict[str, str], current: dict[str, float]) -> dict[str, object]:
    speed = to_float(row.get("speed_m_s"))
    velocity = (
        to_float(row.get("drone_velocity_linear_x")),
        to_float(row.get("drone_velocity_linear_y")),
        to_float(row.get("drone_velocity_linear_z")),
    )
    roll = to_float(row.get("drone_roll"))
    pitch = to_float(row.get("drone_pitch"))
    yaw = to_float(row.get("drone_yaw"))
    body_x, body_y, body_z = rotation_columns_body_to_world(roll, pitch, yaw)
    speed_norm = vector_norm(velocity)
    velocity_hat = tuple(component / speed_norm for component in velocity) if speed_norm > 0.0 else (math.nan, math.nan, math.nan)
    projection_signed = dot(body_z, velocity_hat) if all(math.isfinite(value) for value in velocity_hat) else math.nan
    projection_abs = abs(projection_signed) if math.isfinite(projection_signed) else math.nan
    angle_to_velocity = math.degrees(math.acos(clamp(projection_abs, -1.0, 1.0))) if math.isfinite(projection_abs) else math.nan
    world_z_projection = body_z[2]
    tilt_from_world_z = math.degrees(math.acos(clamp(abs(world_z_projection), -1.0, 1.0))) if math.isfinite(world_z_projection) else math.nan

    thrusts = thrust_values(row)
    thrust_mean = sum(thrusts) / len(thrusts) if len(thrusts) == 4 else math.nan
    thrust_sum = sum(thrusts) if len(thrusts) == 4 else math.nan
    forward_command_proxy = thrust_mean * projection_abs if math.isfinite(thrust_mean) and math.isfinite(projection_abs) else math.nan
    total_command_proxy = thrust_sum / 4.0 if math.isfinite(thrust_sum) else math.nan

    current_drag = (
        drag_force(current["linear_drag_k_n_per_m_s"], current["body_drag_c_n_per_m_s2"], speed)
        if math.isfinite(speed)
        else math.nan
    )
    current_drag_over_max = safe_ratio(current_drag, current["max_thrust_n"])
    required_total_over_max = safe_ratio(math.hypot(current["weight_n"], current_drag), current["max_thrust_n"]) if math.isfinite(current_drag) else math.nan
    required_tilt_deg = math.degrees(math.atan2(current_drag, current["weight_n"])) if math.isfinite(current_drag) else math.nan
    body_velocity = (
        dot(velocity, body_x),
        dot(velocity, body_y),
        dot(velocity, body_z),
    )

    return {
        "archive": row.get("archive", ""),
        "flight_id": row.get("flight_id", ""),
        "profile": row.get("profile", ""),
        "window_rank": int(to_float(row.get("window_rank"))),
        "offset_from_vmax_s": to_float(row.get("offset_from_vmax_s")),
        "elapsed_time_s": to_float(row.get("elapsed_time")),
        "speed_m_s": speed,
        "horizontal_speed_m_s": to_float(row.get("horizontal_speed_m_s")),
        "roll_rad": roll,
        "pitch_rad": pitch,
        "yaw_rad": yaw,
        "body_velocity_x_m_s": body_velocity[0],
        "body_velocity_y_m_s": body_velocity[1],
        "body_velocity_z_m_s": body_velocity[2],
        "body_z_forward_projection_signed": projection_signed,
        "body_z_forward_projection_abs": projection_abs,
        "body_z_angle_to_velocity_deg": angle_to_velocity,
        "body_z_tilt_from_world_z_abs_deg": tilt_from_world_z,
        "thrust_mean_command": thrust_mean,
        "thrust_sum_command": thrust_sum,
        "channels_thrust_us": to_float(row.get("channels_thrust")),
        "vbat_v": to_float(row.get("vbat")),
        "mean_thrust_forward_command_proxy": forward_command_proxy,
        "mean_thrust_forward_proxy_over_current_drag_over_max": safe_ratio(forward_command_proxy, current_drag_over_max),
        "mean_thrust_command_over_required_total_over_max": safe_ratio(thrust_mean, required_total_over_max),
        "total_command_proxy": total_command_proxy,
        "current_drag_force_n": current_drag,
        "current_drag_over_max_thrust": current_drag_over_max,
        "current_required_total_thrust_over_max": required_total_over_max,
        "current_required_tilt_deg_for_drag_balance": required_tilt_deg,
        "is_vmax_sample": 1 if row.get("is_vmax_sample", "").lower() == "true" else 0,
    }


def group_by_window(samples: list[dict[str, object]]) -> dict[int, list[dict[str, object]]]:
    grouped: dict[int, list[dict[str, object]]] = defaultdict(list)
    for sample in samples:
        grouped[int(sample["window_rank"])].append(sample)
    return grouped


def add_source_rows(rows: list[dict[str, object]], source_samples: list[dict[str, str]], current: dict[str, float]) -> None:
    for metric, value, unit, url, note in (
        ("source_dataset", "Race Against the Machine drone racing dataset", "text", SOURCE_URL, "Open-design high-speed FPV dataset."),
        ("paper_doi", PAPER_DOI_URL, "url", PAPER_DOI_URL, "Companion RA-L paper DOI."),
        ("source_window_reference", repo_path(WINDOW_REFERENCE), "path", repo_path(WINDOW_REFERENCE), "Existing local 500 Hz high-speed window reference."),
        ("source_accel_packet", repo_path(ACCEL_PACKET), "path", repo_path(ACCEL_PACKET), "Existing acceleration/drag feasibility packet used for current model constants."),
        ("window_sample_count", len(source_samples), "rows", RELEASE_URL, "Rows from the selected high-speed windows."),
        ("sync_rate_hz", SYNC_RATE_HZ, "Hz", SOURCE_URL, "RATM 500 Hz synchronized CSV rate."),
        ("high_speed_threshold_m_s", HIGH_SPEED_THRESHOLD_M_S, "m/s", SOURCE_URL, "Threshold used for high-speed sample distributions."),
        ("current_racing_mass_kg", current["mass_kg"], "kg", repo_path(ACCEL_PACKET), "Current racingQuad mass from the existing drag packet."),
        ("current_racing_max_thrust_n", current["max_thrust_n"], "N", repo_path(ACCEL_PACKET), "Derived from current weight and horizontal thrust margin."),
        ("current_racing_drag_c_n_per_m_s2", current["drag_c_n_per_m_s2"], "N/(m/s)^2", repo_path(ACCEL_PACKET), "Current racingQuad X-axis drag demand used for feasibility comparisons."),
    ):
        add_metric(
            rows,
            row_type="ratm_thrust_vector_packet_source_inventory",
            name="RATM_thrust_vector_inputs",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=url,
            evidence_role="source_inventory",
            note=note,
        )


def add_window_summaries(rows: list[dict[str, object]], grouped: dict[int, list[dict[str, object]]]) -> None:
    metric_specs = [
        ("vmax_speed_m_s", "m/s", "Fastest speed inside the selected window."),
        ("vmax_body_z_projection_abs", "projection", "Absolute projection of body Z axis onto velocity direction at vmax."),
        ("vmax_body_z_angle_to_velocity_deg", "deg", "Angle between body Z axis and velocity direction at vmax, using absolute thrust-axis sign."),
        ("vmax_body_z_tilt_from_world_z_abs_deg", "deg", "Angle between body Z axis and world Z at vmax, using absolute sign."),
        ("vmax_thrust_mean_command", "normalized command", "Mean of RATM thrust[0..3] at vmax."),
        ("vmax_mean_thrust_forward_command_proxy", "normalized command", "Mean thrust command times absolute body-Z/velocity projection at vmax."),
        ("vmax_current_drag_over_max_thrust", "ratio", "Current racingQuad drag force divided by current max thrust at vmax speed."),
        ("vmax_current_required_total_thrust_over_max", "ratio", "Current drag-balance total thrust requirement divided by current max thrust."),
        ("vmax_current_required_tilt_deg_for_drag_balance", "deg", "Tilt angle needed for current drag balance at the logged vmax speed."),
        ("vmax_proxy_over_current_drag_demand", "ratio", "Command-projection proxy divided by current drag/max demand; command semantics are not a force calibration."),
        ("high_speed_sample_count", "rows", "Samples in this window at or above the high-speed threshold."),
        ("high_speed_duration_s", "s", "High-speed sample count divided by 500 Hz."),
        ("high_speed_projection_abs_p50", "projection", "Median absolute body-Z/velocity projection above threshold."),
        ("high_speed_angle_to_velocity_p50_deg", "deg", "Median body-Z/velocity angle above threshold."),
        ("high_speed_thrust_mean_p50", "normalized command", "Median thrust mean above threshold."),
        ("high_speed_forward_command_proxy_p50", "normalized command", "Median mean-thrust-forward-projection proxy above threshold."),
        ("high_speed_proxy_over_current_drag_p50", "ratio", "Median command-projection proxy divided by current drag/max demand."),
    ]

    for rank, samples in sorted(grouped.items()):
        vmax = max(samples, key=lambda sample: float(sample["speed_m_s"]))
        high = [sample for sample in samples if float(sample["speed_m_s"]) >= HIGH_SPEED_THRESHOLD_M_S]
        values = {
            "vmax_speed_m_s": vmax["speed_m_s"],
            "vmax_body_z_projection_abs": vmax["body_z_forward_projection_abs"],
            "vmax_body_z_angle_to_velocity_deg": vmax["body_z_angle_to_velocity_deg"],
            "vmax_body_z_tilt_from_world_z_abs_deg": vmax["body_z_tilt_from_world_z_abs_deg"],
            "vmax_thrust_mean_command": vmax["thrust_mean_command"],
            "vmax_mean_thrust_forward_command_proxy": vmax["mean_thrust_forward_command_proxy"],
            "vmax_current_drag_over_max_thrust": vmax["current_drag_over_max_thrust"],
            "vmax_current_required_total_thrust_over_max": vmax["current_required_total_thrust_over_max"],
            "vmax_current_required_tilt_deg_for_drag_balance": vmax["current_required_tilt_deg_for_drag_balance"],
            "vmax_proxy_over_current_drag_demand": vmax["mean_thrust_forward_proxy_over_current_drag_over_max"],
            "high_speed_sample_count": len(high),
            "high_speed_duration_s": len(high) / SYNC_RATE_HZ,
            "high_speed_projection_abs_p50": median(float(sample["body_z_forward_projection_abs"]) for sample in high),
            "high_speed_angle_to_velocity_p50_deg": median(float(sample["body_z_angle_to_velocity_deg"]) for sample in high),
            "high_speed_thrust_mean_p50": median(float(sample["thrust_mean_command"]) for sample in high),
            "high_speed_forward_command_proxy_p50": median(float(sample["mean_thrust_forward_command_proxy"]) for sample in high),
            "high_speed_proxy_over_current_drag_p50": median(float(sample["mean_thrust_forward_proxy_over_current_drag_over_max"]) for sample in high),
        }
        for metric, unit, note in metric_specs:
            add_metric(
                rows,
                row_type="ratm_thrust_vector_packet_window_summary",
                name=f"window_rank_{rank}_{vmax['flight_id']}",
                metric=metric,
                value=values[metric],
                unit=unit,
                source_file=WINDOW_REFERENCE,
                source_url=RELEASE_URL,
                evidence_role="window_summary",
                note=note,
                window_rank=rank,
                flight_id=vmax["flight_id"],
                archive=vmax["archive"],
            )


def add_distribution_rows(rows: list[dict[str, object]], samples: list[dict[str, object]]) -> None:
    high = [sample for sample in samples if float(sample["speed_m_s"]) >= HIGH_SPEED_THRESHOLD_M_S]
    variables = [
        ("speed_m_s", "m/s"),
        ("body_z_forward_projection_abs", "projection"),
        ("body_z_angle_to_velocity_deg", "deg"),
        ("body_z_tilt_from_world_z_abs_deg", "deg"),
        ("thrust_mean_command", "normalized command"),
        ("mean_thrust_forward_command_proxy", "normalized command"),
        ("current_drag_over_max_thrust", "ratio"),
        ("current_required_total_thrust_over_max", "ratio"),
        ("current_required_tilt_deg_for_drag_balance", "deg"),
        ("mean_thrust_forward_proxy_over_current_drag_over_max", "ratio"),
    ]
    for field, unit in variables:
        values = [float(sample[field]) for sample in high]
        for suffix, value in (
            ("min", min(finite(values)) if finite(values) else math.nan),
            ("p25", percentile(values, 0.25)),
            ("p50", percentile(values, 0.50)),
            ("p75", percentile(values, 0.75)),
            ("max", max(finite(values)) if finite(values) else math.nan),
        ):
            add_metric(
                rows,
                row_type="ratm_thrust_vector_packet_distribution",
                name="RATM_high_speed_thrust_vector_distribution",
                metric=f"{field}_{suffix}",
                value=value,
                unit=unit,
                source_file=WINDOW_REFERENCE,
                source_url=RELEASE_URL,
                evidence_role="high_speed_distribution",
                note="Distribution over selected RATM window samples at or above 21 m/s.",
            )


def add_decimated_samples(rows: list[dict[str, object]], grouped: dict[int, list[dict[str, object]]]) -> None:
    for rank, samples in sorted(grouped.items()):
        for target_offset in SAMPLE_OFFSETS_S:
            sample = min(samples, key=lambda item: abs(float(item["offset_from_vmax_s"]) - target_offset))
            add_metric(
                rows,
                row_type="ratm_thrust_vector_packet_decimated_sample",
                name=f"window_rank_{rank}_offset_{target_offset:+.2f}s",
                metric="sample",
                value="",
                unit="",
                source_file=WINDOW_REFERENCE,
                source_url=RELEASE_URL,
                evidence_role="traceability_sample",
                note="Nearest selected 500 Hz sample to the requested offset from vmax.",
                target_offset_s=target_offset,
                **sample,
            )


def add_global_summary(rows: list[dict[str, object]], samples: list[dict[str, object]], grouped: dict[int, list[dict[str, object]]]) -> None:
    high = [sample for sample in samples if float(sample["speed_m_s"]) >= HIGH_SPEED_THRESHOLD_M_S]
    vmax_rows = [max(window_samples, key=lambda sample: float(sample["speed_m_s"])) for window_samples in grouped.values()]
    specs = [
        ("window_count", len(grouped), "count", "Selected fastest windows analyzed."),
        ("high_speed_sample_count", len(high), "rows", "Samples at or above 21 m/s."),
        ("high_speed_duration_s", len(high) / SYNC_RATE_HZ, "s", "High-speed sample count divided by 500 Hz."),
        ("vmax_speed_m_s_max", max(float(sample["speed_m_s"]) for sample in vmax_rows), "m/s", "Fastest vmax among selected windows."),
        ("vmax_body_z_angle_to_velocity_deg_p50", median(float(sample["body_z_angle_to_velocity_deg"]) for sample in vmax_rows), "deg", "Median body-Z/velocity angle at window vmax."),
        ("vmax_body_z_projection_abs_p50", median(float(sample["body_z_forward_projection_abs"]) for sample in vmax_rows), "projection", "Median absolute body-Z/velocity projection at window vmax."),
        ("vmax_thrust_mean_command_p50", median(float(sample["thrust_mean_command"]) for sample in vmax_rows), "normalized command", "Median mean thrust command at window vmax."),
        ("vmax_forward_command_proxy_p50", median(float(sample["mean_thrust_forward_command_proxy"]) for sample in vmax_rows), "normalized command", "Median command-projection proxy at window vmax."),
        ("vmax_current_drag_over_max_thrust_p50", median(float(sample["current_drag_over_max_thrust"]) for sample in vmax_rows), "ratio", "Median current drag force divided by current max thrust at vmax."),
        ("vmax_required_total_thrust_over_max_p50", median(float(sample["current_required_total_thrust_over_max"]) for sample in vmax_rows), "ratio", "Median current drag-balance total thrust over current max thrust at vmax."),
        ("vmax_proxy_over_current_drag_demand_p50", median(float(sample["mean_thrust_forward_proxy_over_current_drag_over_max"]) for sample in vmax_rows), "ratio", "Median command-projection proxy divided by current drag/max demand at vmax."),
        ("high_speed_proxy_over_current_drag_demand_p50", median(float(sample["mean_thrust_forward_proxy_over_current_drag_over_max"]) for sample in high), "ratio", "Median command-projection proxy divided by current drag/max demand across high-speed samples."),
    ]
    for metric, value, unit, note in specs:
        add_metric(
            rows,
            row_type="ratm_thrust_vector_packet_global_summary",
            name="RATM_thrust_vector_feasibility_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=RELEASE_URL,
            evidence_role="compact_handoff",
            note=note,
        )

    add_metric(
        rows,
        row_type="ratm_thrust_vector_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Uses ZYX Euler attitude from RATM 500 Hz synchronized CSVs to project the body-Z axis onto the "
            "velocity direction. The absolute projection is a sign-convention-safe thrust-axis proxy, not a "
            "calibrated force. Compare it with current drag/max-thrust demand only as a feasibility guard."
        ),
        unit="text",
        source_file=OUTPUT,
        source_url=SOURCE_URL,
        evidence_role="method",
        note="Do not fit CdA directly from these powered racing windows without reconstructing actual thrust magnitude, prop unloading, wind, and coordinate conventions.",
    )


def sync_summary(packet_rows: list[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY) if SUMMARY.exists() else []
    kept = [row for row in existing if not row.get("category", "").startswith("ratm_thrust_vector_packet_")]
    added: list[dict[str, str]] = []
    for row in packet_rows:
        added.append(
            {
                "category": str(row["row_type"]),
                "name": str(row["name"]),
                "metric": str(row["metric"]),
                "value": value_text(row["value"]),
                "unit": str(row["unit"]),
                "source": str(row.get("source_url") or row.get("source_file", "")),
                "source_file": str(row.get("source_file", "")),
                "source_url": str(row.get("source_url", "")),
                "evidence_role": str(row.get("evidence_role", "")),
                "note": str(row.get("note", "")),
            }
        )
    write_csv(SUMMARY, kept + added)
    return len(added)


def build_rows() -> list[dict[str, object]]:
    source_samples = [row for row in read_rows(WINDOW_REFERENCE) if row.get("row_type") == "ratm_vmax_window_sample"]
    current = current_reference()
    samples = [analyze_sample(row, current) for row in source_samples]
    grouped = group_by_window(samples)

    rows: list[dict[str, object]] = []
    add_source_rows(rows, source_samples, current)
    add_window_summaries(rows, grouped)
    add_distribution_rows(rows, samples)
    add_decimated_samples(rows, grouped)
    add_global_summary(rows, samples, grouped)
    return rows


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
