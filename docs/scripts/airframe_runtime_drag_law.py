"""Runtime airframe drag force-law helpers for generated calibration packets."""

from __future__ import annotations

import math
from typing import Mapping


G = 9.80665
RHO = 1.225
EPSILON = 1.0e-12


def f(row: Mapping[str, object], key: str, default: float = math.nan) -> float:
    value = row.get(key, "")
    if value == "":
        return default
    return float(value)


def safe_ratio(numerator: float, denominator: float, min_denominator: float = EPSILON) -> float:
    if not math.isfinite(numerator) or not math.isfinite(denominator) or abs(denominator) < min_denominator:
        return math.nan
    return numerator / denominator


def drag_force(linear_k_n_per_m_s: float, quadratic_c_n_per_m_s2: float, speed_m_s: float) -> float:
    speed = max(0.0, speed_m_s)
    return max(0.0, linear_k_n_per_m_s) * speed + max(0.0, quadratic_c_n_per_m_s2) * speed * speed


def equivalent_linear_k(linear_k_n_per_m_s: float, quadratic_c_n_per_m_s2: float, speed_m_s: float) -> float:
    speed = max(0.0, speed_m_s)
    return math.nan if speed <= EPSILON else drag_force(linear_k_n_per_m_s, quadratic_c_n_per_m_s2, speed) / speed


def equivalent_quadratic_c(linear_k_n_per_m_s: float, quadratic_c_n_per_m_s2: float, speed_m_s: float) -> float:
    speed = max(0.0, speed_m_s)
    return math.nan if speed <= EPSILON else drag_force(linear_k_n_per_m_s, quadratic_c_n_per_m_s2, speed) / (speed * speed)


def equivalent_cda(linear_k_n_per_m_s: float, quadratic_c_n_per_m_s2: float, speed_m_s: float) -> float:
    c_equiv = equivalent_quadratic_c(linear_k_n_per_m_s, quadratic_c_n_per_m_s2, speed_m_s)
    return math.nan if not math.isfinite(c_equiv) else 2.0 * c_equiv / RHO


def linear_drag_coastdown(
    mass_kg: float,
    linear_k_n_per_m_s: float,
    start_speed_m_s: float,
    end_speed_m_s: float,
) -> dict[str, float]:
    if mass_kg <= 0.0 or linear_k_n_per_m_s <= 0.0 or start_speed_m_s <= end_speed_m_s or end_speed_m_s <= 0.0:
        return invalid_coastdown()
    mass_over_k = mass_kg / linear_k_n_per_m_s
    return {
        "time_s": mass_over_k * math.log(start_speed_m_s / end_speed_m_s),
        "distance_m": mass_over_k * (start_speed_m_s - end_speed_m_s),
        "initial_decel_m_s2": linear_k_n_per_m_s * start_speed_m_s / mass_kg,
        "final_decel_m_s2": linear_k_n_per_m_s * end_speed_m_s / mass_kg,
    }


def quadratic_drag_coastdown(
    mass_kg: float,
    quadratic_c_n_per_m_s2: float,
    start_speed_m_s: float,
    end_speed_m_s: float,
) -> dict[str, float]:
    if mass_kg <= 0.0 or quadratic_c_n_per_m_s2 <= 0.0 or start_speed_m_s <= end_speed_m_s or end_speed_m_s <= 0.0:
        return invalid_coastdown()
    mass_over_c = mass_kg / quadratic_c_n_per_m_s2
    return {
        "time_s": mass_over_c * (1.0 / end_speed_m_s - 1.0 / start_speed_m_s),
        "distance_m": mass_over_c * math.log(start_speed_m_s / end_speed_m_s),
        "initial_decel_m_s2": quadratic_c_n_per_m_s2 * start_speed_m_s * start_speed_m_s / mass_kg,
        "final_decel_m_s2": quadratic_c_n_per_m_s2 * end_speed_m_s * end_speed_m_s / mass_kg,
    }


def linear_quadratic_coastdown(
    mass_kg: float,
    linear_k_n_per_m_s: float,
    quadratic_c_n_per_m_s2: float,
    start_speed_m_s: float,
    end_speed_m_s: float,
) -> dict[str, float]:
    if (
        mass_kg <= 0.0
        or start_speed_m_s <= end_speed_m_s
        or end_speed_m_s <= 0.0
        or (linear_k_n_per_m_s <= 0.0 and quadratic_c_n_per_m_s2 <= 0.0)
    ):
        return invalid_coastdown()
    linear = max(0.0, linear_k_n_per_m_s)
    quadratic = max(0.0, quadratic_c_n_per_m_s2)
    if linear <= EPSILON:
        return quadratic_drag_coastdown(mass_kg, quadratic, start_speed_m_s, end_speed_m_s)
    if quadratic <= EPSILON:
        return linear_drag_coastdown(mass_kg, linear, start_speed_m_s, end_speed_m_s)
    return {
        "time_s": mass_kg
        / linear
        * math.log(
            start_speed_m_s
            * (linear + quadratic * end_speed_m_s)
            / (end_speed_m_s * (linear + quadratic * start_speed_m_s))
        ),
        "distance_m": mass_kg
        / quadratic
        * math.log((linear + quadratic * start_speed_m_s) / (linear + quadratic * end_speed_m_s)),
        "initial_decel_m_s2": drag_force(linear, quadratic, start_speed_m_s) / mass_kg,
        "final_decel_m_s2": drag_force(linear, quadratic, end_speed_m_s) / mass_kg,
    }


def terminal_speed_m_s(force_n: float, linear_k_n_per_m_s: float, quadratic_c_n_per_m_s2: float) -> float:
    if force_n <= 0.0:
        return 0.0
    linear = max(0.0, linear_k_n_per_m_s)
    quadratic = max(0.0, quadratic_c_n_per_m_s2)
    if quadratic <= EPSILON:
        return force_n / linear if linear > EPSILON else math.nan
    if linear <= EPSILON:
        return math.sqrt(force_n / quadratic)
    return (-linear + math.sqrt(linear * linear + 4.0 * quadratic * force_n)) / (2.0 * quadratic)


def corrected_rows(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    return [corrected_row(row) for row in rows]


def corrected_row(source: dict[str, str]) -> dict[str, str]:
    row: dict[str, str] = dict(source)
    row_type = row.get("row_type", "")
    if row_type == "current_preset_drag_scan":
        correct_current_scan(row)
    elif row_type == "current_vs_imav_drag_calibration_target":
        correct_imav_target(row)
    elif row_type == "current_vs_nasa_bare_airframe_drag_area":
        correct_nasa_bare(row)
    elif row_type == "current_vs_nasa_powered_full_airframe_drag":
        correct_nasa_powered(row)
    elif row_type == "current_vs_icas_forward_flight_drag":
        correct_icas_forward(row)
    elif row_type == "current_vs_icas_freefall_terminal_drag":
        correct_icas_freefall(row)
    elif row_type == "current_vs_rpg_rotor_drag_equivalent":
        correct_rpg_force(row)
    elif row_type in {
        "current_vs_uzh_fpv_speed_envelope",
        "current_vs_ratm_speed_floor",
        "current_vs_flight_log_dataset_speed_envelope",
    }:
        correct_speed_envelope(row)
    elif row_type == "current_vs_imav_coastdown":
        correct_imav_coastdown(row)
    elif row_type == "current_vs_rpg_rotor_drag_coastdown":
        correct_rpg_coastdown(row)
    return row


def correct_current_scan(row: dict[str, str]) -> None:
    speed = f(row, "speed_m_s")
    linear = f(row, "linear_drag_coefficient", 0.0)
    body = f(row, "body_drag_coefficient_axis", 0.0)
    force = drag_force(linear, body, speed)
    mass = f(row, "mass_kg")
    weight = mass * G if mass > 0.0 else math.nan
    imav_force = f(row, "imav_mass_fit_drag_force_n")
    imav_k = f(row, "imav_mass_fit_linear_k_n_per_m_s")
    imav_equiv_c = f(row, "imav_mass_fit_equivalent_quadratic_c_at_speed")
    set_runtime_force_fields(row, linear, body, speed, force)
    row["drag_over_weight"] = value(force / weight if weight > 0.0 else math.nan)
    row["current_force_over_imav_mass_fit"] = value(safe_ratio(force, imav_force))
    row["current_effective_linear_k_over_imav_mass_fit"] = value(safe_ratio(equivalent_linear_k(linear, body, speed), imav_k))
    row["current_cda_over_imav_mass_fit_at_speed"] = value(safe_ratio(equivalent_quadratic_c(linear, body, speed), imav_equiv_c))
    row["note"] = (
        "Current code uses linear damping plus quadratic body drag. The total quadratic field is the "
        "speed-specific equivalent c = F/v^2; IMAV fit is a mass-matched low-speed drag reference."
    )


def correct_imav_target(row: dict[str, str]) -> None:
    speed = f(row, "speed_m_s")
    linear = f(row, "current_linear_drag_coefficient", 0.0)
    body = f(row, "current_body_drag_coefficient_axis", 0.0)
    imav_force = f(row, "imav_drag_force_n")
    imav_k = f(row, "imav_mass_fit_linear_k_n_per_m_s")
    current_force = drag_force(linear, body, speed)
    target_total_c = imav_force / (speed * speed) if speed > EPSILON else math.nan
    target_body = (imav_force - linear * speed) / (speed * speed) if speed > EPSILON else math.nan
    row["target_total_quadratic_c_n_per_m_s2"] = value(target_total_c)
    row["current_total_quadratic_c_n_per_m_s2"] = value(equivalent_quadratic_c(linear, body, speed))
    row["target_body_drag_coefficient_axis_if_linear_unchanged"] = value(target_body)
    row["target_body_drag_nonnegative_possible"] = value(1 if target_body >= 0.0 else 0)
    row["current_total_scale_to_match_imav"] = value(safe_ratio(target_total_c, equivalent_quadratic_c(linear, body, speed)))
    row["current_linear_scale_to_match_imav_if_body_zero"] = value(safe_ratio(imav_k, linear))
    row["current_body_scale_to_match_imav_if_linear_zero"] = value(safe_ratio(target_total_c, body))
    row["linear_only_force_over_imav"] = value(safe_ratio(linear * speed, imav_force))
    row["current_total_force_over_imav"] = value(safe_ratio(current_force, imav_force))
    row["note"] = (
        "Direct tuning target for matching the IMAV mass-fit linear drag with this project's "
        "linear-plus-quadratic drag form."
    )


def correct_nasa_bare(row: dict[str, str]) -> None:
    speed = 10.0
    linear = f(row, "current_linear_drag_coefficient", 0.0)
    body = f(row, "current_body_drag_coefficient_axis", 0.0)
    reference_c = f(row, "reference_equivalent_quadratic_c_n_per_m_s2_median")
    reference_cda = f(row, "reference_equivalent_cda_m2_median")
    reference_force = f(row, "reference_drag_force_10m_s_n_median")
    force = drag_force(linear, body, speed)
    row["current_total_quadratic_c_n_per_m_s2"] = value(equivalent_quadratic_c(linear, body, speed))
    row["current_equivalent_cda_m2"] = value(equivalent_cda(linear, body, speed))
    row["current_cda_over_nasa_small_quad_median"] = value(safe_ratio(equivalent_cda(linear, body, speed), reference_cda))
    row["current_c_over_nasa_small_quad_median"] = value(safe_ratio(equivalent_quadratic_c(linear, body, speed), reference_c))
    row["current_drag_force_10m_s_n"] = value(force)
    row["current_drag_force_10m_s_over_nasa_small_quad_median"] = value(safe_ratio(force, reference_force))


def correct_nasa_powered(row: dict[str, str]) -> None:
    speed = f(row, "reference_wind_speed_from_q_m_s")
    linear = f(row, "current_linear_drag_coefficient", 0.0)
    body = f(row, "current_body_drag_coefficient_axis", 0.0)
    reference_c = f(row, "reference_equivalent_quadratic_c_n_per_m_s2_median")
    reference_cda = f(row, "reference_equivalent_cda_m2_median")
    reference_force = f(row, "reference_drag_force_n_median")
    reference_force_10 = f(row, "reference_drag_force_10m_s_n_median")
    force = drag_force(linear, body, speed)
    force_10 = drag_force(linear, body, 10.0)
    row["current_total_quadratic_c_n_per_m_s2"] = value(equivalent_quadratic_c(linear, body, speed))
    row["current_equivalent_cda_m2"] = value(equivalent_cda(linear, body, speed))
    row["current_drag_force_at_reference_speed_n"] = value(force)
    row["current_drag_force_at_reference_speed_over_nasa_powered_median"] = value(safe_ratio(force, reference_force))
    row["current_cda_over_nasa_powered_median"] = value(safe_ratio(equivalent_cda(linear, body, speed), reference_cda))
    row["current_c_over_nasa_powered_median"] = value(safe_ratio(equivalent_quadratic_c(linear, body, speed), reference_c))
    row["current_drag_force_10m_s_n"] = value(force_10)
    row["current_drag_force_10m_s_over_nasa_powered_median"] = value(safe_ratio(force_10, reference_force_10))


def correct_icas_forward(row: dict[str, str]) -> None:
    speed = f(row, "speed_m_s")
    linear = f(row, "current_linear_drag_coefficient", 0.0)
    body = f(row, "current_body_drag_coefficient_axis", 0.0)
    reference_force = f(row, "reference_drag_force_n")
    reference_c = f(row, "reference_equivalent_quadratic_c_n_per_m_s2")
    force = drag_force(linear, body, speed)
    row["current_total_quadratic_c_n_per_m_s2"] = value(equivalent_quadratic_c(linear, body, speed))
    row["current_drag_force_n"] = value(force)
    row["current_over_icas_drag_force"] = value(safe_ratio(force, reference_force))
    row["current_c_over_icas_equiv_c"] = value(safe_ratio(equivalent_quadratic_c(linear, body, speed), reference_c))


def correct_icas_freefall(row: dict[str, str]) -> None:
    terminal = f(row, "reference_terminal_velocity_m_s")
    linear = f(row, "current_linear_drag_coefficient", 0.0)
    body = f(row, "current_body_drag_coefficient_axis", 0.0)
    mass = f(row, "mass_kg")
    weight = mass * G if mass > 0.0 else math.nan
    reference_c = f(row, "reference_equivalent_quadratic_c_n_per_m_s2")
    force = drag_force(linear, body, terminal)
    row["current_total_quadratic_c_n_per_m_s2"] = value(equivalent_quadratic_c(linear, body, terminal))
    row["current_terminal_velocity_m_s"] = value(terminal_speed_m_s(weight, linear, body))
    row["current_terminal_velocity_over_icas"] = value(safe_ratio(terminal_speed_m_s(weight, linear, body), terminal))
    row["current_force_at_icas_terminal_speed_n"] = value(force)
    row["current_force_at_icas_terminal_speed_over_preset_weight"] = value(safe_ratio(force, weight))
    row["current_c_over_icas_terminal_equiv_c"] = value(safe_ratio(equivalent_quadratic_c(linear, body, terminal), reference_c))


def correct_rpg_force(row: dict[str, str]) -> None:
    speed = f(row, "speed_m_s")
    linear = f(row, "current_linear_drag_coefficient", 0.0)
    body = f(row, "current_body_drag_coefficient_axis", 0.0)
    rpg_force = f(row, "rpg_linear_drag_force_n")
    rpg_c = f(row, "rpg_equivalent_quadratic_c_n_per_m_s2")
    force = drag_force(linear, body, speed)
    row["current_total_quadratic_c_n_per_m_s2"] = value(equivalent_quadratic_c(linear, body, speed))
    row["current_drag_force_n"] = value(force)
    row["current_over_rpg_force"] = value(safe_ratio(force, rpg_force))
    row["current_total_c_over_rpg_equiv_c"] = value(safe_ratio(equivalent_quadratic_c(linear, body, speed), rpg_c))


def correct_speed_envelope(row: dict[str, str]) -> None:
    speed = first_finite(
        f(row, "reference_vmax_m_s"),
        f(row, "reference_speed_floor_m_s"),
        f(row, "reference_speed_m_s"),
    )
    linear = f(row, "current_linear_drag_coefficient", 0.0)
    body = f(row, "current_body_drag_coefficient_axis", 0.0)
    force = drag_force(linear, body, speed)
    weight = f(row, "weight_n")
    max_thrust = f(row, "current_total_max_thrust_n")
    margin = f(row, "current_horizontal_thrust_margin_n")
    required_total = math.hypot(weight, force) if math.isfinite(weight) else math.nan
    field = {
        "current_vs_uzh_fpv_speed_envelope": "current_drag_force_at_reference_vmax_n",
        "current_vs_ratm_speed_floor": "current_drag_force_at_speed_floor_n",
        "current_vs_flight_log_dataset_speed_envelope": "current_drag_force_at_reference_speed_n",
    }.get(row.get("row_type", ""), "current_drag_force_n")
    row["current_total_quadratic_c_n_per_m_s2"] = value(equivalent_quadratic_c(linear, body, speed))
    row[field] = value(force)
    row["current_drag_force_over_weight"] = value(safe_ratio(force, weight))
    row["current_drag_force_over_horizontal_thrust_margin"] = value(safe_ratio(force, margin))
    for key in (
        "required_total_thrust_to_hold_level_at_vmax_n",
        "required_total_thrust_to_hold_level_at_speed_floor_n",
        "required_total_thrust_to_hold_level_at_reference_speed_n",
    ):
        if key in row:
            row[key] = value(required_total)
    row["required_total_thrust_over_current_total_max"] = value(safe_ratio(required_total, max_thrust))
    row["required_tilt_deg_for_drag_balance"] = value(math.degrees(math.atan2(force, weight)) if weight > 0.0 else math.nan)


def correct_imav_coastdown(row: dict[str, str]) -> None:
    start = f(row, "start_speed_m_s")
    end = f(row, "end_speed_m_s")
    mass = f(row, "mass_kg")
    linear = f(row, "linear_drag_coefficient", 0.0)
    body = f(row, "body_drag_coefficient_axis", 0.0)
    imav_time = f(row, "imav_time_s")
    imav_distance = f(row, "imav_distance_m")
    imav_initial = f(row, "imav_initial_decel_m_s2")
    imav_final = f(row, "imav_final_decel_m_s2")
    current = linear_quadratic_coastdown(mass, linear, body, start, end)
    row["total_quadratic_c_n_per_m_s2"] = value(equivalent_quadratic_c(linear, body, start))
    row["current_equivalent_linear_k_at_start_n_per_m_s"] = value(equivalent_linear_k(linear, body, start))
    row["current_equivalent_linear_k_at_end_n_per_m_s"] = value(equivalent_linear_k(linear, body, end))
    set_coastdown_fields(row, current, imav_time, imav_distance, imav_initial, imav_final, "imav")


def correct_rpg_coastdown(row: dict[str, str]) -> None:
    start = f(row, "start_speed_m_s")
    end = f(row, "end_speed_m_s")
    mass = f(row, "mass_kg")
    linear = f(row, "current_linear_drag_coefficient", 0.0)
    body = f(row, "current_body_drag_coefficient_axis", 0.0)
    rpg_time = f(row, "rpg_time_s")
    rpg_distance = f(row, "rpg_distance_m")
    rpg_initial = f(row, "rpg_initial_decel_m_s2")
    rpg_final = f(row, "rpg_final_decel_m_s2")
    current = linear_quadratic_coastdown(mass, linear, body, start, end)
    row["current_total_quadratic_c_n_per_m_s2"] = value(equivalent_quadratic_c(linear, body, start))
    set_coastdown_fields(row, current, rpg_time, rpg_distance, rpg_initial, rpg_final, "rpg")


def set_runtime_force_fields(
    row: dict[str, str],
    linear: float,
    body: float,
    speed: float,
    force: float,
) -> None:
    row["total_quadratic_c_n_per_m_s2"] = value(equivalent_quadratic_c(linear, body, speed))
    row["drag_force_n"] = value(force)
    row["effective_linear_k_n_per_m_s"] = value(equivalent_linear_k(linear, body, speed))
    row["equivalent_cda_m2"] = value(equivalent_cda(linear, body, speed))


def set_coastdown_fields(
    row: dict[str, str],
    current: dict[str, float],
    reference_time: float,
    reference_distance: float,
    reference_initial: float,
    reference_final: float,
    prefix: str,
) -> None:
    row["current_time_s"] = value(current["time_s"])
    row[f"current_time_over_{prefix}"] = value(safe_ratio(current["time_s"], reference_time))
    row["current_distance_m"] = value(current["distance_m"])
    row[f"current_distance_over_{prefix}"] = value(safe_ratio(current["distance_m"], reference_distance))
    row["current_initial_decel_m_s2"] = value(current["initial_decel_m_s2"])
    row[f"current_initial_decel_over_{prefix}"] = value(safe_ratio(current["initial_decel_m_s2"], reference_initial))
    row["current_final_decel_m_s2"] = value(current["final_decel_m_s2"])
    row[f"current_final_decel_over_{prefix}"] = value(safe_ratio(current["final_decel_m_s2"], reference_final))


def first_finite(*values: float) -> float:
    for item in values:
        if math.isfinite(item):
            return item
    return math.nan


def invalid_coastdown() -> dict[str, float]:
    return {
        "time_s": math.nan,
        "distance_m": math.nan,
        "initial_decel_m_s2": math.nan,
        "final_decel_m_s2": math.nan,
    }


def value(item: object) -> str:
    if isinstance(item, bool):
        return "1" if item else "0"
    if isinstance(item, int):
        return str(item)
    if isinstance(item, float):
        if not math.isfinite(item):
            return ""
        if abs(item) < 1.0e-12:
            item = 0.0
        return f"{item:.12g}"
    return str(item)
