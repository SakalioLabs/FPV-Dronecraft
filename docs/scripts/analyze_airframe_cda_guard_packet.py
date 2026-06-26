"""Build an airframe CdA guard packet for FPV drag calibration.

Outputs:
  docs/data/airframe_cda_guard_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category
  airframe_cda_guard_packet_*

The broader airframe drag tables already contain wind-tunnel digitization,
paper fits, open-source model coefficients, and flight-envelope checks. This
packet is narrower: it keeps the force-law/CdA scale guardrails that should be
checked before treating the current linear damping plus quadratic body drag as
physical airframe drag.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Callable, Iterable

from airframe_runtime_drag_law import corrected_rows


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

OUTPUT = DATA / "airframe_cda_guard_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"
AIRFRAME = DATA / "airframe_drag_reference.csv"
AIRFRAME_PACKET = DATA / "airframe_drag_calibration_packet.csv"
RATM_PACKET = DATA / "ratm_accel_drag_residual_packet.csv"

JAVA_CONFIG_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneConfig.java"
IMAV_URL = "https://www.imavs.org/papers/2022/4.pdf"
NASA_MULTICOPTER_URL = "https://rotorcraft.arc.nasa.gov/Publications/files/72-2016-374.pdf"
ROTORPY_URL = "https://raw.githubusercontent.com/spencerfolk/rotorpy/main/rotorpy/vehicles/hummingbird_params.py"
RPG_URL = "https://raw.githubusercontent.com/uzh-rpg/rpg_quadrotor_control/master/control/position_controller/parameters/fpv.yaml"
ICAS_URL = "https://www.icas.org/icas_archive/ICAS2020/data/papers/ICAS2020_0482_paper.pdf"
MANCHESTER_URL = "https://research.manchester.ac.uk/en/studentTheses/theoretical-and-practical-limits-on-multi-rotor-manoeuvrability/"

SUMMARY_NAME = "airframe_cda_guard_summary"
RACING_AXES = ("x", "z")
RACING_SPEEDS = (5.0, 9.0, 10.0, 20.0)
SCALE_GUARD_SPEED_MPS = 10.0


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


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
            writer.writerow({key: value_text(row.get(key, "")) for key in fieldnames})


def f(row: dict[str, str], key: str, default: float = math.nan) -> float:
    value = row.get(key, "")
    if value == "":
        return default
    return float(value)


def safe_ratio(numerator: float, denominator: float, min_denominator: float = 1.0e-12) -> float:
    if not math.isfinite(numerator) or not math.isfinite(denominator) or abs(denominator) < min_denominator:
        return math.nan
    return numerator / denominator


def runtime_drag_force(linear_k_n_per_m_s: float, quadratic_c_n_per_m_s2: float, speed_m_s: float) -> float:
    speed = max(0.0, speed_m_s)
    return max(0.0, linear_k_n_per_m_s) * speed + max(0.0, quadratic_c_n_per_m_s2) * speed * speed


def require_one(rows: list[dict[str, str]], predicate: Callable[[dict[str, str]], bool]) -> dict[str, str]:
    for row in rows:
        if predicate(row):
            return row
    raise LookupError("required row not found")


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path,
    source_url: str = "",
    evidence_role: str,
    note: str = "",
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": value,
            "unit": unit,
            "source_file": repo_path(source_file),
            "source_url": source_url,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def add_metrics_from_source_row(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    source_row: dict[str, str],
    source_file: Path,
    metrics: Iterable[tuple[str, str]],
    evidence_role: str,
    source_url_key: str = "source",
    note: str = "",
) -> None:
    source_url = source_row.get(source_url_key, "") or source_row.get("reference_source", "")
    for metric, unit in metrics:
        add_metric(
            rows,
            row_type=row_type,
            name=name,
            metric=metric,
            value=source_row.get(metric, ""),
            unit=unit,
            source_file=source_file,
            source_url=source_url,
            evidence_role=evidence_role,
            note=note or source_row.get("note", ""),
        )


def packet_metric(packet_rows: list[dict[str, str]], name: str, metric: str) -> dict[str, str]:
    return require_one(packet_rows, lambda row: row.get("name") == name and row.get("metric") == metric)


def find_current_scan(airframe_rows: list[dict[str, str]], axis: str, speed: float) -> dict[str, str]:
    return require_one(
        airframe_rows,
        lambda row: row.get("row_type") == "current_preset_drag_scan"
        and row.get("preset") == "racingQuad"
        and row.get("axis") == axis
        and abs(f(row, "speed_m_s") - speed) < 1.0e-9,
    )


def find_imav_target(airframe_rows: list[dict[str, str]], axis: str, speed: float) -> dict[str, str]:
    return require_one(
        airframe_rows,
        lambda row: row.get("row_type") == "current_vs_imav_drag_calibration_target"
        and row.get("preset") == "racingQuad"
        and row.get("axis") == axis
        and abs(f(row, "speed_m_s") - speed) < 1.0e-9,
    )


def add_source_inventory(rows: list[dict[str, object]], airframe_rows: list[dict[str, str]], packet_rows: list[dict[str, str]]) -> None:
    imav_rows = [row for row in airframe_rows if row.get("row_type") == "reference_imav_quad_linear_drag"]
    metrics = [
        ("row_count", len(imav_rows), "count"),
        ("mass_min_kg", min(f(row, "mass_kg") for row in imav_rows), "kg"),
        ("mass_max_kg", max(f(row, "mass_kg") for row in imav_rows), "kg"),
        ("speed_min_m_s", min(f(row, "speed_m_s") for row in imav_rows), "m/s"),
        ("speed_max_m_s", max(f(row, "speed_m_s") for row in imav_rows), "m/s"),
        ("linear_drag_k_min_n_per_m_s", min(f(row, "linear_drag_k_n_per_m_s") for row in imav_rows), "N/(m/s)"),
        ("linear_drag_k_max_n_per_m_s", max(f(row, "linear_drag_k_n_per_m_s") for row in imav_rows), "N/(m/s)"),
    ]
    for metric, value, unit in metrics:
        add_metric(
            rows,
            row_type="airframe_cda_guard_packet_source_inventory",
            name="IMAV_2022_5in_linear_drag",
            metric=metric,
            value=value,
            unit=unit,
            source_file=AIRFRAME,
            source_url=IMAV_URL,
            evidence_role="5in_quad_low_speed_paper_fit",
            note="IMAV reports D=kV for 5-inch three-blade quads over 2.5..12.5 m/s; this packet converts it into speed-specific quadratic guardrails.",
        )

    for source_row_type, name, source_url, metric_list, role in [
        (
            "reference_nasa_bare_airframe_dragq_summary",
            "NASA_Fig30_bare_airframe_small_quad_0deg_median",
            NASA_MULTICOPTER_URL,
            [
                ("sample_count", "count"),
                ("drag_over_q_ft2_median", "ft^2"),
                ("equivalent_cda_m2_median", "m^2"),
                ("equivalent_quadratic_c_n_per_m_s2_median", "N/(m/s)^2"),
                ("drag_force_10m_s_n_median", "N"),
            ],
            "wind_tunnel_bare_airframe_lower_bound",
        ),
        (
            "reference_nasa_powered_full_airframe_drag_summary",
            "NASA_Figs18_20_22_powered_full_airframe_small_quad",
            NASA_MULTICOPTER_URL,
            [
                ("sample_count", "count"),
                ("wind_speed_from_q_m_s", "m/s"),
                ("drag_force_n_median", "N"),
                ("equivalent_cda_m2_median", "m^2"),
                ("equivalent_quadratic_c_n_per_m_s2_median", "N/(m/s)^2"),
                ("drag_force_10m_s_n_median", "N"),
            ],
            "wind_tunnel_powered_airframe_anchor",
        ),
    ]:
        source_row = require_one(airframe_rows, lambda row, rt=source_row_type: row.get("row_type") == rt)
        add_metrics_from_source_row(
            rows,
            row_type="airframe_cda_guard_packet_source_inventory",
            name=name,
            source_row=source_row,
            source_file=AIRFRAME,
            metrics=metric_list,
            evidence_role=role,
            note=source_row.get("note", ""),
        )

    rotorpy_rows = [row for row in airframe_rows if row.get("row_type") == "reference_open_model_quadratic_drag"]
    for row in rotorpy_rows:
        add_metrics_from_source_row(
            rows,
            row_type="airframe_cda_guard_packet_source_inventory",
            name=row["name"],
            source_row=row,
            source_file=AIRFRAME,
            metrics=[("quadratic_c_n_per_m_s2", "N/(m/s)^2"), ("equivalent_cda_m2", "m^2")],
            evidence_role="open_source_sim_model_quadratic_drag",
            note="RotorPy Hummingbird c_D uses the same F=c*v^2 unit form as this project's body-drag path.",
        )

    rpg_rows = [row for row in airframe_rows if row.get("row_type") == "reference_rpg_rotor_drag_linear_accel"]
    for row in rpg_rows:
        add_metrics_from_source_row(
            rows,
            row_type="airframe_cda_guard_packet_source_inventory",
            name=row["name"],
            source_row=row,
            source_file=AIRFRAME,
            metrics=[("mass_normalized_drag_1_s", "1/s")],
            evidence_role="fpv_controller_rotor_drag_prior",
            note="RPG rotor-drag is a linear-in-velocity controller model, so it is a shape/prior check rather than a CdA target.",
        )

    icas_forward = require_one(airframe_rows, lambda row: row.get("row_type") == "reference_icas_forward_flight_drag")
    add_metrics_from_source_row(
        rows,
        row_type="airframe_cda_guard_packet_source_inventory",
        name=icas_forward["name"],
        source_row=icas_forward,
        source_file=AIRFRAME,
        metrics=[("speed_m_s", "m/s"), ("drag_force_n", "N"), ("equivalent_cda_m2", "m^2")],
        evidence_role="cfd_forward_flight_scale_check",
        note="ICAS CFD vehicle is not a 5-inch FPV quad, but it is useful for order-of-magnitude drag sanity.",
    )

    for name, metric in [
        ("Manchester_multirotor_manoeuvrability_thesis", "flight_drag_vs_wind_tunnel_accuracy_abs_percent"),
        ("Manchester_multirotor_manoeuvrability_thesis", "drag_build_up_model_ci_abs_percent"),
        ("WAVELab_AscTec_Pelican_system_id_dataset", "flight_count"),
    ]:
        row = packet_metric(packet_rows, name, metric)
        add_metric(
            rows,
            row_type="airframe_cda_guard_packet_source_inventory",
            name=name,
            metric=metric,
            value=row["value"],
            unit=row["unit"],
            source_file=AIRFRAME_PACKET,
            source_url=row.get("source_url", MANCHESTER_URL),
            evidence_role=row.get("evidence_level", "external_method_context"),
            note=row.get("note", ""),
        )


def add_current_drag_rows(rows: list[dict[str, object]], airframe_rows: list[dict[str, str]]) -> None:
    metrics = [
        ("speed_m_s", "m/s"),
        ("linear_drag_coefficient", "N/(m/s)"),
        ("body_drag_coefficient_axis", "N/(m/s)^2"),
        ("total_quadratic_c_n_per_m_s2", "N/(m/s)^2"),
        ("drag_force_n", "N"),
        ("drag_over_weight", "ratio"),
        ("effective_linear_k_n_per_m_s", "N/(m/s)"),
        ("equivalent_cda_m2", "m^2"),
        ("current_force_over_imav_mass_fit", "ratio"),
    ]
    for axis in RACING_AXES:
        for speed in RACING_SPEEDS:
            row = find_current_scan(airframe_rows, axis, speed)
            add_metrics_from_source_row(
                rows,
                row_type="airframe_cda_guard_packet_current_drag",
                name=row["name"],
                source_row=row,
                source_file=AIRFRAME,
                metrics=metrics,
                evidence_role="current_project_drag_coefficient_projection",
                note="Current racingQuad runtime linear-plus-quadratic base drag before extra separated-flow terms.",
            )


def add_imav_guard_rows(rows: list[dict[str, object]], airframe_rows: list[dict[str, str]]) -> None:
    metrics = [
        ("speed_m_s", "m/s"),
        ("imav_drag_force_n", "N"),
        ("target_total_quadratic_c_n_per_m_s2", "N/(m/s)^2"),
        ("current_linear_drag_coefficient", "N/(m/s)"),
        ("current_body_drag_coefficient_axis", "N/(m/s)^2"),
        ("target_body_drag_coefficient_axis_if_linear_unchanged", "N/(m/s)^2"),
        ("target_body_drag_nonnegative_possible", "boolean"),
        ("current_total_scale_to_match_imav", "ratio"),
        ("current_linear_scale_to_match_imav_if_body_zero", "ratio"),
        ("current_body_scale_to_match_imav_if_linear_zero", "ratio"),
        ("linear_only_force_over_imav", "ratio"),
        ("current_total_force_over_imav", "ratio"),
    ]
    speeds = sorted(
        {
            f(row, "speed_m_s")
            for row in airframe_rows
            if row.get("row_type") == "current_vs_imav_drag_calibration_target"
            and row.get("preset") == "racingQuad"
            and row.get("axis") in RACING_AXES
        }
    )
    for axis in RACING_AXES:
        for speed in speeds:
            row = find_imav_target(airframe_rows, axis, speed)
            add_metrics_from_source_row(
                rows,
                row_type="airframe_cda_guard_packet_imav_target",
                name=row["name"],
                source_row=row,
                source_file=AIRFRAME,
                metrics=metrics,
                source_url_key="reference_source",
                evidence_role="5in_imav_mass_fit_guard",
                note="Speed-specific conversion of the IMAV D=kV fit into the project's F=k*v+c*v^2 coefficient form.",
            )


def add_wind_tunnel_comparison_rows(rows: list[dict[str, object]], airframe_rows: list[dict[str, str]]) -> None:
    comparison_specs = [
        (
            "current_vs_nasa_bare_airframe_drag_area",
            "airframe_cda_guard_packet_nasa_bare_comparison",
            [
                ("current_equivalent_cda_m2", "m^2"),
                ("reference_equivalent_cda_m2_median", "m^2"),
                ("current_cda_over_nasa_small_quad_median", "ratio"),
                ("current_drag_force_10m_s_n", "N"),
                ("reference_drag_force_10m_s_n_median", "N"),
                ("current_drag_force_10m_s_over_nasa_small_quad_median", "ratio"),
            ],
            "current_vs_bare_airframe_wind_tunnel",
        ),
        (
            "current_vs_nasa_powered_full_airframe_drag",
            "airframe_cda_guard_packet_nasa_powered_comparison",
            [
                ("reference_wind_speed_from_q_m_s", "m/s"),
                ("current_equivalent_cda_m2", "m^2"),
                ("reference_equivalent_cda_m2_median", "m^2"),
                ("current_cda_over_nasa_powered_median", "ratio"),
                ("current_drag_force_at_reference_speed_n", "N"),
                ("reference_drag_force_n_median", "N"),
                ("current_drag_force_at_reference_speed_over_nasa_powered_median", "ratio"),
                ("current_drag_force_10m_s_over_nasa_powered_median", "ratio"),
            ],
            "current_vs_powered_airframe_wind_tunnel",
        ),
    ]
    for source_row_type, row_type, metrics, role in comparison_specs:
        for row in airframe_rows:
            if row.get("row_type") != source_row_type or row.get("preset") != "racingQuad" or row.get("axis") not in RACING_AXES:
                continue
            add_metrics_from_source_row(
                rows,
                row_type=row_type,
                name=row["name"],
                source_row=row,
                source_file=AIRFRAME,
                metrics=metrics,
                source_url_key="reference_source",
                evidence_role=role,
                note="Direct current racingQuad comparison against low-precision NASA digitization anchor.",
            )


def add_external_comparison_rows(rows: list[dict[str, object]], airframe_rows: list[dict[str, str]]) -> None:
    for row in airframe_rows:
        if (
            row.get("row_type") == "current_vs_rpg_rotor_drag_equivalent"
            and row.get("preset") == "racingQuad"
            and row.get("current_axis") in RACING_AXES
            and f(row, "speed_m_s") in (10.0, 20.0)
        ):
            add_metrics_from_source_row(
                rows,
                row_type="airframe_cda_guard_packet_rpg_comparison",
                name=row["name"],
                source_row=row,
                source_file=AIRFRAME,
                metrics=[
                    ("speed_m_s", "m/s"),
                    ("rpg_linear_drag_force_n", "N"),
                    ("rpg_equivalent_quadratic_c_n_per_m_s2", "N/(m/s)^2"),
                    ("current_drag_force_n", "N"),
                    ("current_total_quadratic_c_n_per_m_s2", "N/(m/s)^2"),
                    ("current_over_rpg_force", "ratio"),
                ],
                source_url_key="reference_source",
                evidence_role="current_vs_fpv_rotor_drag_controller_prior",
                note=row.get("axis_mapping_note", ""),
            )

    for row in airframe_rows:
        if row.get("row_type") == "current_vs_icas_forward_flight_drag" and row.get("preset") == "racingQuad":
            add_metrics_from_source_row(
                rows,
                row_type="airframe_cda_guard_packet_icas_comparison",
                name=row["name"],
                source_row=row,
                source_file=AIRFRAME,
                metrics=[
                    ("speed_m_s", "m/s"),
                    ("reference_drag_force_n", "N"),
                    ("reference_equivalent_quadratic_c_n_per_m_s2", "N/(m/s)^2"),
                    ("current_drag_force_n", "N"),
                    ("current_over_icas_drag_force", "ratio"),
                    ("current_c_over_icas_equiv_c", "ratio"),
                ],
                source_url_key="reference_source",
                evidence_role="current_vs_cfd_forward_flight_scale_check",
                note="Not an FPV vehicle match; useful only as an order-of-magnitude drag check.",
            )

    for row in airframe_rows:
        if (
            row.get("row_type") == "current_vs_icas_freefall_terminal_drag"
            and row.get("preset") == "racingQuad"
            and row.get("axis") == "y"
        ):
            add_metrics_from_source_row(
                rows,
                row_type="airframe_cda_guard_packet_icas_freefall_comparison",
                name=row["name"],
                source_row=row,
                source_file=AIRFRAME,
                metrics=[
                    ("reference_terminal_velocity_m_s", "m/s"),
                    ("current_terminal_velocity_m_s", "m/s"),
                    ("current_terminal_velocity_over_icas", "ratio"),
                    ("current_c_over_icas_terminal_equiv_c", "ratio"),
                ],
                source_url_key="reference_source",
                evidence_role="vertical_drag_terminal_speed_scale_check",
                note="Vertical freefall is a separate regime, but it catches extreme drag magnitudes.",
            )


def add_flight_envelope_rows(
    rows: list[dict[str, object]],
    airframe_rows: list[dict[str, str]],
    ratm_rows: list[dict[str, str]],
) -> None:
    for row in airframe_rows:
        if row.get("row_type") == "current_vs_uzh_fpv_speed_envelope" and row.get("preset") == "racingQuad" and row.get("axis") == "x":
            add_metrics_from_source_row(
                rows,
                row_type="airframe_cda_guard_packet_flight_envelope",
                name=row["name"],
                source_row=row,
                source_file=AIRFRAME,
                metrics=[
                    ("reference_vmax_m_s", "m/s"),
                    ("current_drag_force_at_reference_vmax_n", "N"),
                    ("current_drag_force_over_horizontal_thrust_margin", "ratio"),
                    ("required_total_thrust_over_current_total_max", "ratio"),
                    ("required_tilt_deg_for_drag_balance", "deg"),
                ],
                source_url_key="reference_source",
                evidence_role="public_fpv_racing_speed_envelope_guard",
                note="Speed-envelope feasibility check; not a drag fit.",
            )

    for row in airframe_rows:
        if row.get("row_type") == "current_vs_ratm_speed_floor" and row.get("preset") == "racingQuad":
            add_metrics_from_source_row(
                rows,
                row_type="airframe_cda_guard_packet_flight_envelope",
                name=row["name"],
                source_row=row,
                source_file=AIRFRAME,
                metrics=[
                    ("reference_speed_floor_m_s", "m/s"),
                    ("current_drag_force_at_speed_floor_n", "N"),
                    ("current_drag_force_over_horizontal_thrust_margin", "ratio"),
                    ("required_total_thrust_over_current_total_max", "ratio"),
                    ("required_tilt_deg_for_drag_balance", "deg"),
                ],
                source_url_key="reference_source",
                evidence_role="ratm_high_speed_floor_guard",
                note="README-level RATM speed floor feasibility check.",
            )

    for metric in [
        "global_speed_max_m_s",
        "median_abs_speed_rate_at_vmax_m_s2",
        "median_current_drag_decel_at_vmax_m_s2",
        "median_current_drag_decel_at_vmax_over_abs_speed_rate",
    ]:
        row = packet_metric(ratm_rows, "RATM_high_speed_accel_drag_residual_summary", metric)
        add_metric(
            rows,
            row_type="airframe_cda_guard_packet_flight_envelope",
            name="RATM_accel_drag_residual_packet",
            metric=metric,
            value=row["value"],
            unit=row["unit"],
            source_file=RATM_PACKET,
            source_url=row.get("source_url", ""),
            evidence_role="powered_flight_acceleration_feasibility_guard",
            note="Observed speed-rate versus current drag-deceleration demand in the fastest RATM windows.",
        )


def add_scale_guard_rows(rows: list[dict[str, object]], airframe_rows: list[dict[str, str]]) -> None:
    nasa_bare = require_one(airframe_rows, lambda row: row.get("row_type") == "reference_nasa_bare_airframe_dragq_summary")
    nasa_powered = require_one(airframe_rows, lambda row: row.get("row_type") == "reference_nasa_powered_full_airframe_drag_summary")
    current_x10 = find_current_scan(airframe_rows, "x", 10.0)
    imav_x10 = find_imav_target(airframe_rows, "x", 10.0)

    targets = [
        (
            "IMAV_5in_mass_fit_10m_s",
            f(imav_x10, "target_total_quadratic_c_n_per_m_s2"),
            f(imav_x10, "imav_drag_force_n"),
            IMAV_URL,
            "5in_paper_fit_target",
            "IMAV D=kV fit converted at 10 m/s for current racingQuad mass.",
        ),
        (
            "NASA_bare_Fig30_small_quad_median_10m_s",
            f(nasa_bare, "equivalent_quadratic_c_n_per_m_s2_median"),
            f(nasa_bare, "drag_force_10m_s_n_median"),
            NASA_MULTICOPTER_URL,
            "bare_airframe_wind_tunnel_lower_bound",
            "Bare-frame reference excludes powered rotor/body interaction.",
        ),
        (
            "NASA_powered_Figs18_20_22_small_quad_median_10m_s",
            f(nasa_powered, "equivalent_quadratic_c_n_per_m_s2_median"),
            f(nasa_powered, "drag_force_10m_s_n_median"),
            NASA_MULTICOPTER_URL,
            "powered_airframe_wind_tunnel_anchor",
            "Powered small-quad median converted to equivalent 10 m/s force.",
        ),
    ]
    current_c = f(current_x10, "total_quadratic_c_n_per_m_s2")
    current_linear = f(current_x10, "linear_drag_coefficient")
    current_body = f(current_x10, "body_drag_coefficient_axis")
    current_drag_10 = f(current_x10, "drag_force_n")
    for name, target_c, target_force, source_url, role, note in targets:
        linear_force = runtime_drag_force(current_linear, 0.0, SCALE_GUARD_SPEED_MPS)
        target_body_if_linear_unchanged = (
            (target_force - linear_force) / (SCALE_GUARD_SPEED_MPS * SCALE_GUARD_SPEED_MPS)
        )
        derived = {
            "target_total_quadratic_c_n_per_m_s2": target_c,
            "target_drag_force_10m_s_n": target_force,
            "current_total_quadratic_c_n_per_m_s2": current_c,
            "current_linear_drag_coefficient": current_linear,
            "current_body_drag_coefficient_axis": current_body,
            "current_drag_force_10m_s_n": current_drag_10,
            "current_total_scale_to_target": safe_ratio(target_c, current_c),
            "current_drag_force_over_target": safe_ratio(current_drag_10, target_force),
            "target_body_if_linear_unchanged": target_body_if_linear_unchanged,
            "target_body_nonnegative_possible": 1.0 if target_body_if_linear_unchanged >= 0.0 else 0.0,
            "linear_only_force_over_target_force": safe_ratio(linear_force, target_force),
            "body_only_scale_if_linear_zero": safe_ratio(target_c, current_body),
        }
        for metric, value in derived.items():
            unit = {
                "target_total_quadratic_c_n_per_m_s2": "N/(m/s)^2",
                "target_drag_force_10m_s_n": "N",
                "current_total_quadratic_c_n_per_m_s2": "N/(m/s)^2",
                "current_linear_drag_coefficient": "N/(m/s)",
                "current_body_drag_coefficient_axis": "N/(m/s)^2",
                "current_drag_force_10m_s_n": "N",
                "current_total_scale_to_target": "ratio",
                "current_drag_force_over_target": "ratio",
                "target_body_if_linear_unchanged": "N/(m/s)^2",
                "target_body_nonnegative_possible": "boolean",
                "linear_only_force_over_target_force": "ratio",
                "body_only_scale_if_linear_zero": "ratio",
            }[metric]
            add_metric(
                rows,
                row_type="airframe_cda_guard_packet_scale_guard",
                name=name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=AIRFRAME,
                source_url=source_url,
                evidence_role=role,
                note=note,
            )


def add_summary_rows(
    rows: list[dict[str, object]],
    airframe_rows: list[dict[str, str]],
    packet_rows: list[dict[str, str]],
    ratm_rows: list[dict[str, str]],
) -> None:
    current_x10 = find_current_scan(airframe_rows, "x", 10.0)
    current_z10 = find_current_scan(airframe_rows, "z", 10.0)
    current_x20 = find_current_scan(airframe_rows, "x", 20.0)
    imav_x10 = find_imav_target(airframe_rows, "x", 10.0)
    imav_x12p5 = find_imav_target(airframe_rows, "x", 12.5)
    nasa_bare_x = require_one(
        airframe_rows,
        lambda row: row.get("row_type") == "current_vs_nasa_bare_airframe_drag_area"
        and row.get("name") == "racingQuad_x_vs_NASA_Fig30_small_quad_0deg_median",
    )
    nasa_powered_x = require_one(
        airframe_rows,
        lambda row: row.get("row_type") == "current_vs_nasa_powered_full_airframe_drag"
        and row.get("name") == "racingQuad_x_vs_NASA_Fig18_20_22_powered_0deg_mid_rpm_median",
    )
    rpg_10x = require_one(
        airframe_rows,
        lambda row: row.get("row_type") == "current_vs_rpg_rotor_drag_equivalent"
        and row.get("name") == "racingQuad_current_x_vs_RPG_x_10.0m_s",
    )
    rpg_20x = require_one(
        airframe_rows,
        lambda row: row.get("row_type") == "current_vs_rpg_rotor_drag_equivalent"
        and row.get("name") == "racingQuad_current_x_vs_RPG_x_20.0m_s",
    )
    icas_forward_x = require_one(
        airframe_rows,
        lambda row: row.get("row_type") == "current_vs_icas_forward_flight_drag"
        and row.get("name") == "racingQuad_x_vs_ICAS_forward_10.7m_s",
    )
    uzh = require_one(
        airframe_rows,
        lambda row: row.get("row_type") == "current_vs_uzh_fpv_speed_envelope"
        and row.get("name") == "racingQuad_x_vs_UZH_FPV_SplitS1",
    )
    ratm_floor = require_one(
        airframe_rows,
        lambda row: row.get("row_type") == "current_vs_ratm_speed_floor"
        and row.get("name") == "racingQuad_x_vs_RATM_21m_s_floor",
    )
    ratm_drag_decel_p50 = packet_metric(
        ratm_rows,
        "RATM_high_speed_accel_drag_residual_summary",
        "median_current_drag_decel_at_vmax_m_s2",
    )
    ratm_obs_rate_p50 = packet_metric(
        ratm_rows,
        "RATM_high_speed_accel_drag_residual_summary",
        "median_abs_speed_rate_at_vmax_m_s2",
    )
    apdrone_coeff = packet_metric(packet_rows, "airframe_drag_summary", "apdrone_straight_decel_ge16_coeff_p50")
    apdrone_ratio = packet_metric(packet_rows, "airframe_drag_summary", "apdrone_straight_decel_ge16_coeff_over_racingQuad_x")
    manchester_flight = packet_metric(packet_rows, "airframe_drag_summary", "manchester_flight_drag_vs_wind_tunnel_accuracy_abs_percent")
    manchester_build = packet_metric(packet_rows, "airframe_drag_summary", "manchester_drag_build_up_model_ci_abs_percent")

    summary = {
        "racingQuad_x_current_total_c_n_per_m_s2": (f(current_x10, "total_quadratic_c_n_per_m_s2"), "N/(m/s)^2"),
        "racingQuad_x_current_cda_m2": (f(current_x10, "equivalent_cda_m2"), "m^2"),
        "racingQuad_z_current_cda_m2": (f(current_z10, "equivalent_cda_m2"), "m^2"),
        "racingQuad_x_drag_10m_s_n": (f(current_x10, "drag_force_n"), "N"),
        "racingQuad_x_drag_20m_s_n": (f(current_x20, "drag_force_n"), "N"),
        "imav_target_total_c_10m_s": (f(imav_x10, "target_total_quadratic_c_n_per_m_s2"), "N/(m/s)^2"),
        "imav_target_total_c_12p5m_s": (f(imav_x12p5, "target_total_quadratic_c_n_per_m_s2"), "N/(m/s)^2"),
        "imav_target_body_if_linear_unchanged_10m_s": (
            f(imav_x10, "target_body_drag_coefficient_axis_if_linear_unchanged"),
            "N/(m/s)^2",
        ),
        "imav_target_body_nonnegative_possible_10m_s": (f(imav_x10, "target_body_drag_nonnegative_possible"), "boolean"),
        "racingQuad_x_current_total_scale_to_match_imav_10m_s": (
            f(imav_x10, "current_total_scale_to_match_imav"),
            "ratio",
        ),
        "racingQuad_x_linear_only_over_imav_10m_s": (f(imav_x10, "linear_only_force_over_imav"), "ratio"),
        "racingQuad_x_current_over_nasa_bare_10m_s": (
            f(nasa_bare_x, "current_drag_force_10m_s_over_nasa_small_quad_median"),
            "ratio",
        ),
        "racingQuad_x_current_total_scale_to_match_nasa_bare": (
            1.0 / f(nasa_bare_x, "current_drag_force_10m_s_over_nasa_small_quad_median"),
            "ratio",
        ),
        "racingQuad_x_current_over_nasa_powered_reference_speed": (
            f(nasa_powered_x, "current_drag_force_at_reference_speed_over_nasa_powered_median"),
            "ratio",
        ),
        "racingQuad_x_current_total_scale_to_match_nasa_powered": (
            1.0 / f(nasa_powered_x, "current_cda_over_nasa_powered_median"),
            "ratio",
        ),
        "racingQuad_x_current_over_rpg_10m_s": (f(rpg_10x, "current_over_rpg_force"), "ratio"),
        "racingQuad_x_current_over_rpg_20m_s": (f(rpg_20x, "current_over_rpg_force"), "ratio"),
        "racingQuad_x_current_over_icas_forward_10p7m_s": (f(icas_forward_x, "current_over_icas_drag_force"), "ratio"),
        "uzh_splits1_required_total_thrust_over_current_max": (
            f(uzh, "required_total_thrust_over_current_total_max"),
            "ratio",
        ),
        "ratm_21m_s_floor_required_total_thrust_over_current_max": (
            f(ratm_floor, "required_total_thrust_over_current_total_max"),
            "ratio",
        ),
        "ratm_vmax_current_drag_decel_p50_m_s2": (float(ratm_drag_decel_p50["value"]), "m/s^2"),
        "ratm_vmax_observed_abs_speed_rate_p50_m_s2": (float(ratm_obs_rate_p50["value"]), "m/s^2"),
        "apdrone_straight_decel_ge16_coeff_p50": (float(apdrone_coeff["value"]), "N/(m/s)^2"),
        "apdrone_straight_decel_ge16_coeff_over_racingQuad_x": (float(apdrone_ratio["value"]), "ratio"),
        "manchester_flight_drag_vs_wind_tunnel_accuracy_abs_percent": (float(manchester_flight["value"]), "percent"),
        "manchester_drag_build_up_model_ci_abs_percent": (float(manchester_build["value"]), "percent"),
    }
    for metric, (value, unit) in summary.items():
        add_metric(
            rows,
            row_type="airframe_cda_guard_packet_summary",
            name=SUMMARY_NAME,
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url="",
            evidence_role="compact_cda_guard_handoff",
            note="Compact CdA/drag-coefficient guard summary for the coding agent.",
        )

    add_metric(
        rows,
        row_type="airframe_cda_guard_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Treat the current racingQuad linearDragCoefficient as runtime linear damping in N/(m/s), not as a "
            "quadratic CdA coefficient. The speed-specific equivalent CdA from linear damping plus body drag "
            "should be compared against wind-tunnel targets, while the old linear-as-quadratic projection remains "
            "only a guardrail for spotting unit mistakes."
        ),
        unit="text",
        source_file=OUTPUT,
        source_url=IMAV_URL,
        evidence_role="method_caveat",
        note="Keep physical CdA, rotor drag, residual powered-flight forces, and gameplay damping as separate model surfaces.",
    )


def build_rows() -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    airframe_rows = corrected_rows(read_rows(AIRFRAME))
    packet_rows = read_rows(AIRFRAME_PACKET)
    ratm_rows = read_rows(RATM_PACKET)
    add_source_inventory(rows, airframe_rows, packet_rows)
    add_current_drag_rows(rows, airframe_rows)
    add_imav_guard_rows(rows, airframe_rows)
    add_wind_tunnel_comparison_rows(rows, airframe_rows)
    add_external_comparison_rows(rows, airframe_rows)
    add_flight_envelope_rows(rows, airframe_rows, ratm_rows)
    add_scale_guard_rows(rows, airframe_rows)
    add_summary_rows(rows, airframe_rows, packet_rows, ratm_rows)
    return rows


def sync_summary(packet_rows: Iterable[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("airframe_cda_guard_packet_")]
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
            }
        )
    write_csv(SUMMARY, kept + added)
    return len(added)


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
