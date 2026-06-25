"""Build a compact airframe drag calibration packet.

Outputs:
  docs/data/airframe_drag_calibration_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category airframe_drag_packet_*

The detailed airframe drag CSV is intentionally broad. This packet keeps the
handoff narrower: source inventory, current racingQuad drag magnitudes,
wind-tunnel/paper targets, flight-envelope feasibility checks, passive
coastdown comparisons, and log-fit caveats. It distinguishes physical drag
evidence from gameplay/stability damping evidence.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Callable, Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
OUTPUT = DATA / "airframe_drag_calibration_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

AIRFRAME = DATA / "airframe_drag_reference.csv"
APDRONE_PACKET = DATA / "apdrone_drag_calibration_packet.csv"
AIIO_SAMPLE = DATA / "aiio_flight_log_sample_reference.csv"

PELICAN_README_URL = "https://raw.githubusercontent.com/wavelab/pelican_dataset/master/README.md"
PELICAN_DATA_URL = "http://wavelab.uwaterloo.ca/wp-content/uploads/2017/09/AscTec_Pelican_Flight_Dataset.mat"
PELICAN_DOC_URL = "https://github.com/wavelab/pelican_dataset/blob/master/dataset/Pelican_Dataset.pdf"
MANCHESTER_THESIS_URL = "https://research.manchester.ac.uk/en/studentTheses/theoretical-and-practical-limits-on-multi-rotor-manoeuvrability/"
MANCHESTER_THESIS_PDF_URL = "https://research.manchester.ac.uk/files/295567271/FULL_TEXT.PDF"


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def f(row: dict[str, str], key: str, default: float = math.nan) -> float:
    value = row.get(key, "")
    if value == "":
        return default
    return float(value)


def isclose(value: str, target: float, tol: float = 1e-9) -> bool:
    try:
        return abs(float(value) - target) <= tol
    except (TypeError, ValueError):
        return False


def value_text(value: str | float) -> str:
    if isinstance(value, str):
        return value
    if not math.isfinite(value):
        return ""
    return f"{value:.12g}"


def add_metric(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: str | float,
    unit: str,
    source_file: Path,
    source_url: str = "",
    source_row_type: str = "",
    evidence_level: str = "",
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
            "source_row_type": source_row_type,
            "evidence_level": evidence_level,
            "note": note,
        }
    )


def add_metrics_from_row(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    source_row: dict[str, str],
    source_file: Path,
    metrics: Iterable[tuple[str, str]],
    source_url_key: str = "source",
    evidence_level: str = "",
    note: str = "",
) -> None:
    for metric, unit in metrics:
        add_metric(
            rows,
            row_type=row_type,
            name=name,
            metric=metric,
            value=f(source_row, metric),
            unit=unit,
            source_file=source_file,
            source_url=source_row.get(source_url_key, "") or source_row.get("reference_source", ""),
            source_row_type=source_row.get("row_type", ""),
            evidence_level=evidence_level,
            note=note or source_row.get("note", ""),
        )


def require_one(rows: list[dict[str, str]], predicate: Callable[[dict[str, str]], bool]) -> dict[str, str]:
    for row in rows:
        if predicate(row):
            return row
    raise LookupError("required source row not found")


def add_source_inventory(rows: list[dict[str, str]], airframe_rows: list[dict[str, str]]) -> None:
    imav_rows = [r for r in airframe_rows if r.get("row_type") == "reference_imav_quad_linear_drag"]
    imav_masses = sorted({f(r, "mass_kg") for r in imav_rows})
    imav_speeds = [f(r, "speed_m_s") for r in imav_rows]
    for metric, value, unit in [
        ("mass_min_kg", min(imav_masses), "kg"),
        ("mass_max_kg", max(imav_masses), "kg"),
        ("speed_min_m_s", min(imav_speeds), "m/s"),
        ("speed_max_m_s", max(imav_speeds), "m/s"),
        ("row_count", len(imav_rows), "count"),
    ]:
        add_metric(
            rows,
            row_type="airframe_drag_packet_source_inventory",
            name="IMAV_2022_quad_linear_drag",
            metric=metric,
            value=value,
            unit=unit,
            source_file=AIRFRAME,
            source_url=imav_rows[0].get("source", ""),
            source_row_type="reference_imav_quad_linear_drag",
            evidence_level="paper_drag_fit",
            note="IMAV reports D=kV for 5-inch three-blade quads; converted into quadratic force targets by speed.",
        )

    for source_row_type, name, metrics, evidence in [
        (
            "reference_nasa_bare_airframe_dragq_summary",
            "NASA_Fig30_bare_airframe_small_quad",
            [
                ("sample_count", "count"),
                ("drag_over_q_ft2_median", "ft^2"),
                ("equivalent_cda_m2_median", "m^2"),
                ("drag_force_10m_s_n_median", "N"),
            ],
            "wind_tunnel_lower_bound",
        ),
        (
            "reference_nasa_powered_full_airframe_drag_summary",
            "NASA_powered_full_airframe_small_quad",
            [
                ("sample_count", "count"),
                ("drag_force_n_median", "N"),
                ("equivalent_cda_m2_median", "m^2"),
                ("drag_force_10m_s_n_median", "N"),
            ],
            "wind_tunnel_powered_drag",
        ),
    ]:
        row = require_one(airframe_rows, lambda r, rt=source_row_type: r.get("row_type") == rt)
        add_metrics_from_row(
            rows,
            row_type="airframe_drag_packet_source_inventory",
            name=name,
            source_row=row,
            source_file=AIRFRAME,
            metrics=metrics,
            evidence_level=evidence,
        )

    for source_row_type, name, metrics, evidence in [
        (
            "reference_uzh_fpv_dataset_context",
            "UZH_FPV_racing_dataset",
            [
                ("sequence_count_original", "count"),
                ("platform_mass_kg_paper", "kg"),
                ("paper_top_speed_outdoor_m_s", "m/s"),
                ("paper_top_speed_indoor_m_s", "m/s"),
            ],
            "flight_envelope",
        ),
        (
            "reference_ratm_dataset_context",
            "RATM_racing_dataset",
            [
                ("flight_count_total", "count"),
                ("reported_speed_floor_m_s", "m/s"),
                ("sync_csv_rate_hz", "Hz"),
                ("release_total_size_gib", "GiB"),
            ],
            "flight_log_identification_candidate",
        ),
        (
            "reference_blackbird_dataset_context",
            "Blackbird_aggressive_flight_dataset",
            [
                ("flight_count_total", "count"),
                ("trajectory_count", "count"),
                ("top_speed_m_s", "m/s"),
                ("motor_speed_rate_hz", "Hz"),
                ("motion_capture_rate_hz", "Hz"),
                ("full_dataset_size_tb", "TB"),
            ],
            "flight_log_identification_candidate",
        ),
        (
            "reference_aiio_dataset_context",
            "AIIO_high_speed_inertial_dataset",
            [
                ("sequence_count_total", "count"),
                ("duration_total_s", "s"),
                ("distance_total_m", "m"),
                ("manual_speed_high_m_s", "m/s"),
                ("release_dataset_size_mib", "MiB"),
            ],
            "flight_log_identification_candidate",
        ),
    ]:
        row = require_one(airframe_rows, lambda r, rt=source_row_type: r.get("row_type") == rt)
        add_metrics_from_row(
            rows,
            row_type="airframe_drag_packet_source_inventory",
            name=name,
            source_row=row,
            source_file=AIRFRAME,
            metrics=metrics,
            evidence_level=evidence,
        )

    pelican_metrics = [
        ("flight_count", 54.0, "count"),
        ("dataset_size_mb", 238.1, "MB"),
        ("has_vicon_position_orientation", 1.0, "boolean"),
        ("has_motor_actual_speed", 1.0, "boolean"),
        ("has_motor_commanded_speed", 1.0, "boolean"),
        ("has_numerical_velocity", 1.0, "boolean"),
    ]
    for metric, value, unit in pelican_metrics:
        add_metric(
            rows,
            row_type="airframe_drag_packet_source_inventory",
            name="WAVELab_AscTec_Pelican_system_id_dataset",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=PELICAN_README_URL,
            source_row_type="external_source_inventory",
            evidence_level="system_identification_candidate",
            note="Indoor MATLAB dataset with Vicon pose, velocity, motor speed, and motor commands. Useful for dynamics identification, not an FPV coastdown result.",
        )
    add_metric(
        rows,
        row_type="airframe_drag_packet_source_inventory",
        name="WAVELab_AscTec_Pelican_system_id_dataset",
        metric="download_url",
        value=PELICAN_DATA_URL,
        unit="url",
        source_file=OUTPUT,
        source_url=PELICAN_README_URL,
        source_row_type="external_source_inventory",
        evidence_level="system_identification_candidate",
        note=f"Signal documentation: {PELICAN_DOC_URL}",
    )

    manchester_metrics = [
        ("flight_drag_vs_wind_tunnel_accuracy_abs_percent", 6.0, "percent"),
        ("drag_build_up_model_ci_abs_percent", 20.0, "percent"),
        ("airspeed_model_overestimate_upper_percent", 80.0, "percent"),
        ("dataset_includes_drag_coefficients", 1.0, "boolean"),
        ("dataset_includes_airspeed_acceleration_powertrain", 1.0, "boolean"),
    ]
    for metric, value, unit in manchester_metrics:
        add_metric(
            rows,
            row_type="airframe_drag_packet_source_inventory",
            name="Manchester_multirotor_manoeuvrability_thesis",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=MANCHESTER_THESIS_URL,
            source_row_type="external_source_inventory",
            evidence_level="open_thesis_method_and_dataset",
            note="Open thesis abstract reports axial-flight drag testing, wind-tunnel validation, and a multi-rotor manoeuvrability dataset.",
        )
    add_metric(
        rows,
        row_type="airframe_drag_packet_source_inventory",
        name="Manchester_multirotor_manoeuvrability_thesis",
        metric="pdf_url",
        value=MANCHESTER_THESIS_PDF_URL,
        unit="url",
        source_file=OUTPUT,
        source_url=MANCHESTER_THESIS_URL,
        source_row_type="external_source_inventory",
        evidence_level="open_thesis_method_and_dataset",
        note="Use for method/uncertainty bounds before hunting the underlying machine-readable dataset.",
    )


def add_current_drag(rows: list[dict[str, str]], airframe_rows: list[dict[str, str]]) -> None:
    metrics = [
        ("speed_m_s", "m/s"),
        ("drag_force_n", "N"),
        ("drag_over_weight", "weight fraction"),
        ("total_quadratic_c_n_per_m_s2", "N/(m/s)^2"),
        ("effective_linear_k_n_per_m_s", "N/(m/s)"),
        ("current_force_over_imav_mass_fit", "ratio"),
        ("current_effective_linear_k_over_imav_mass_fit", "ratio"),
        ("current_cda_over_imav_mass_fit_at_speed", "ratio"),
    ]
    for row in [
        r
        for r in airframe_rows
        if r.get("row_type") == "current_preset_drag_scan"
        and r.get("preset") == "racingQuad"
        and r.get("axis") in {"x", "z"}
        and r.get("speed_m_s") in {"10.0", "20.0"}
    ]:
        add_metrics_from_row(
            rows,
            row_type="airframe_drag_packet_current_racing_drag",
            name=row["name"],
            source_row=row,
            source_file=AIRFRAME,
            metrics=metrics,
            evidence_level="current_project_model",
            note="Current racingQuad quadratic drag scan before separated-flow additions.",
        )


def add_reference_comparisons(rows: list[dict[str, str]], airframe_rows: list[dict[str, str]]) -> None:
    imav_metrics = [
        ("speed_m_s", "m/s"),
        ("current_total_quadratic_c_n_per_m_s2", "N/(m/s)^2"),
        ("imav_mass_fit_linear_k_n_per_m_s", "N/(m/s)"),
        ("imav_drag_force_n", "N"),
        ("target_total_quadratic_c_n_per_m_s2", "N/(m/s)^2"),
        ("target_body_drag_coefficient_axis_if_linear_unchanged", "N/(m/s)^2"),
        ("current_total_scale_to_match_imav", "ratio"),
        ("current_linear_scale_to_match_imav_if_body_zero", "ratio"),
        ("linear_only_force_over_imav", "ratio"),
        ("current_total_force_over_imav", "ratio"),
    ]
    for row in [
        r
        for r in airframe_rows
        if r.get("row_type") == "current_vs_imav_drag_calibration_target"
        and r.get("preset") == "racingQuad"
        and r.get("axis") in {"x", "z"}
        and r.get("speed_m_s") in {"10.0", "12.5"}
    ]:
        add_metrics_from_row(
            rows,
            row_type="airframe_drag_packet_imav_target",
            name=row["name"],
            source_row=row,
            source_file=AIRFRAME,
            metrics=imav_metrics,
            source_url_key="reference_source",
            evidence_level="paper_drag_fit_comparison",
            note="Direct target for matching IMAV mass-fit drag in this project's quadratic coefficient form.",
        )

    nasa_metrics = [
        ("reference_equivalent_cda_m2_median", "m^2"),
        ("current_equivalent_cda_m2", "m^2"),
        ("current_cda_over_nasa_small_quad_median", "ratio"),
        ("reference_drag_force_10m_s_n_median", "N"),
        ("current_drag_force_10m_s_n", "N"),
        ("current_drag_force_10m_s_over_nasa_small_quad_median", "ratio"),
    ]
    for row in [
        r
        for r in airframe_rows
        if r.get("row_type") == "current_vs_nasa_bare_airframe_drag_area"
        and r.get("preset") == "racingQuad"
        and r.get("axis") in {"x", "z"}
    ]:
        add_metrics_from_row(
            rows,
            row_type="airframe_drag_packet_nasa_bare_comparison",
            name=row["name"],
            source_row=row,
            source_file=AIRFRAME,
            metrics=nasa_metrics,
            source_url_key="reference_source",
            evidence_level="wind_tunnel_lower_bound_comparison",
        )

    powered_metrics = [
        ("reference_wind_speed_from_q_m_s", "m/s"),
        ("reference_drag_force_n_median", "N"),
        ("current_drag_force_at_reference_speed_n", "N"),
        ("current_drag_force_at_reference_speed_over_nasa_powered_median", "ratio"),
        ("reference_equivalent_cda_m2_median", "m^2"),
        ("current_equivalent_cda_m2", "m^2"),
        ("current_cda_over_nasa_powered_median", "ratio"),
    ]
    for row in [
        r
        for r in airframe_rows
        if r.get("row_type") == "current_vs_nasa_powered_full_airframe_drag"
        and r.get("preset") == "racingQuad"
        and r.get("axis") in {"x", "z"}
    ]:
        add_metrics_from_row(
            rows,
            row_type="airframe_drag_packet_nasa_powered_comparison",
            name=row["name"],
            source_row=row,
            source_file=AIRFRAME,
            metrics=powered_metrics,
            source_url_key="reference_source",
            evidence_level="wind_tunnel_powered_drag_comparison",
        )

    rpg_metrics = [
        ("speed_m_s", "m/s"),
        ("rpg_mass_normalized_drag_1_s", "1/s"),
        ("rpg_linear_drag_force_n", "N"),
        ("rpg_equivalent_quadratic_c_n_per_m_s2", "N/(m/s)^2"),
        ("current_drag_force_n", "N"),
        ("current_over_rpg_force", "ratio"),
        ("current_total_c_over_rpg_equiv_c", "ratio"),
    ]
    for row in [
        r
        for r in airframe_rows
        if r.get("row_type") == "current_vs_rpg_rotor_drag_equivalent"
        and r.get("preset") == "racingQuad"
        and r.get("current_axis") in {"x", "z"}
        and r.get("speed_m_s") in {"10.0", "20.0"}
    ]:
        add_metrics_from_row(
            rows,
            row_type="airframe_drag_packet_rpg_rotor_drag_comparison",
            name=row["name"],
            source_row=row,
            source_file=AIRFRAME,
            metrics=rpg_metrics,
            source_url_key="reference_source",
            evidence_level="rotor_drag_controller_comparison",
        )


def add_flight_envelope(rows: list[dict[str, str]], airframe_rows: list[dict[str, str]]) -> None:
    uzh_metrics = [
        ("reference_vmax_m_s", "m/s"),
        ("reference_vmax_km_h", "km/h"),
        ("current_drag_force_at_reference_vmax_n", "N"),
        ("current_drag_force_over_horizontal_thrust_margin", "ratio"),
        ("required_total_thrust_over_current_total_max", "ratio"),
        ("required_tilt_deg_for_drag_balance", "deg"),
    ]
    for row in [
        r
        for r in airframe_rows
        if r.get("row_type") == "current_vs_uzh_fpv_speed_envelope"
        and r.get("preset") == "racingQuad"
        and r.get("axis") == "x"
        and "SplitS1" in r.get("name", "")
    ]:
        add_metrics_from_row(
            rows,
            row_type="airframe_drag_packet_flight_envelope",
            name=row["name"],
            source_row=row,
            source_file=AIRFRAME,
            metrics=uzh_metrics,
            source_url_key="reference_source",
            evidence_level="flight_envelope_feasibility",
        )

    ratm_metrics = [
        ("reference_speed_floor_m_s", "m/s"),
        ("reference_speed_floor_km_h", "km/h"),
        ("current_drag_force_at_speed_floor_n", "N"),
        ("current_drag_force_over_horizontal_thrust_margin", "ratio"),
        ("required_total_thrust_over_current_total_max", "ratio"),
        ("required_tilt_deg_for_drag_balance", "deg"),
    ]
    for row in [
        r
        for r in airframe_rows
        if r.get("row_type") == "current_vs_ratm_speed_floor"
        and r.get("preset") == "racingQuad"
        and r.get("axis") == "x"
    ]:
        add_metrics_from_row(
            rows,
            row_type="airframe_drag_packet_flight_envelope",
            name=row["name"],
            source_row=row,
            source_file=AIRFRAME,
            metrics=ratm_metrics,
            source_url_key="reference_source",
            evidence_level="flight_envelope_feasibility",
        )

    log_metrics = [
        ("reference_speed_m_s", "m/s"),
        ("current_drag_force_at_reference_speed_n", "N"),
        ("current_drag_force_over_horizontal_thrust_margin", "ratio"),
        ("required_total_thrust_over_current_total_max", "ratio"),
        ("required_tilt_deg_for_drag_balance", "deg"),
    ]
    for row in [
        r
        for r in airframe_rows
        if r.get("row_type") == "current_vs_flight_log_dataset_speed_envelope"
        and r.get("preset") == "racingQuad"
        and r.get("axis") == "x"
        and r.get("dataset") in {"Blackbird", "AIIO"}
    ]:
        add_metrics_from_row(
            rows,
            row_type="airframe_drag_packet_flight_envelope",
            name=row["name"],
            source_row=row,
            source_file=AIRFRAME,
            metrics=log_metrics,
            source_url_key="reference_source",
            evidence_level="trajectory_log_feasibility",
        )


def add_coastdown_and_log_caveats(
    rows: list[dict[str, str]],
    airframe_rows: list[dict[str, str]],
    apdrone_rows: list[dict[str, str]],
    aiio_rows: list[dict[str, str]],
) -> None:
    coast_metrics = [
        ("start_speed_m_s", "m/s"),
        ("end_speed_m_s", "m/s"),
        ("imav_time_s", "s"),
        ("current_time_s", "s"),
        ("current_time_over_imav", "ratio"),
        ("imav_distance_m", "m"),
        ("current_distance_m", "m"),
        ("current_distance_over_imav", "ratio"),
        ("imav_initial_decel_m_s2", "m/s^2"),
        ("current_initial_decel_m_s2", "m/s^2"),
        ("current_initial_decel_over_imav", "ratio"),
    ]
    for row in [
        r
        for r in airframe_rows
        if r.get("row_type") == "current_vs_imav_coastdown"
        and r.get("preset") == "racingQuad"
        and r.get("axis") in {"x", "z"}
        and r.get("start_speed_m_s") == "20.0"
    ]:
        add_metrics_from_row(
            rows,
            row_type="airframe_drag_packet_imav_coastdown",
            name=row["name"],
            source_row=row,
            source_file=AIRFRAME,
            metrics=coast_metrics,
            source_url_key="reference_source",
            evidence_level="passive_coastdown_model_comparison",
        )

    rpg_coast_metrics = [
        ("start_speed_m_s", "m/s"),
        ("end_speed_m_s", "m/s"),
        ("rpg_time_s", "s"),
        ("current_time_s", "s"),
        ("current_time_over_rpg", "ratio"),
        ("rpg_distance_m", "m"),
        ("current_distance_m", "m"),
        ("current_distance_over_rpg", "ratio"),
        ("rpg_initial_decel_m_s2", "m/s^2"),
        ("current_initial_decel_m_s2", "m/s^2"),
        ("current_initial_decel_over_rpg", "ratio"),
    ]
    for row in [
        r
        for r in airframe_rows
        if r.get("row_type") == "current_vs_rpg_rotor_drag_coastdown"
        and r.get("preset") == "racingQuad"
        and r.get("current_axis") in {"x", "z"}
        and r.get("start_speed_m_s") == "20.0"
    ]:
        add_metrics_from_row(
            rows,
            row_type="airframe_drag_packet_rpg_coastdown",
            name=row["name"],
            source_row=row,
            source_file=AIRFRAME,
            metrics=rpg_coast_metrics,
            source_url_key="reference_source",
            evidence_level="passive_coastdown_model_comparison",
        )

    for row in [r for r in apdrone_rows if r.get("name") == "straight_decel_v_ge_16"]:
        add_metric(
            rows,
            row_type="airframe_drag_packet_apdrone_log_fit_caveat",
            name=row["name"],
            metric=row["metric"],
            value=row["value"],
            unit=row["unit"],
            source_file=APDRONE_PACKET,
            source_url=row.get("source", ""),
            source_row_type=row.get("row_type", ""),
            evidence_level="flight_log_negative_fit_result",
            note="APdrone high-speed deceleration filter is too thin and still powered/maneuvering; use as a caveat, not as a fitted CdA.",
        )

    speed_bin = [r for r in apdrone_rows if r.get("name") == "speed_bin_16-20"]
    for row in speed_bin:
        if row.get("metric") in {"event_count", "source_file_count", "decelerating_event_count", "accelerating_event_fraction"}:
            add_metric(
                rows,
                row_type="airframe_drag_packet_apdrone_log_fit_caveat",
                name=row["name"],
                metric=row["metric"],
                value=row["value"],
                unit=row["unit"],
                source_file=APDRONE_PACKET,
                source_url=row.get("source", ""),
                source_row_type=row.get("row_type", ""),
                evidence_level="flight_log_negative_fit_result",
                note="APdrone 16-20 m/s GPS bin is sparse and mixed acceleration/deceleration.",
            )

    fastest_aiio = max(aiio_rows, key=lambda r: f(r, "speed_max_m_s"))
    max_rpm_aiio = max(aiio_rows, key=lambda r: f(r, "rotor_spd_max_reported_units"))
    for source_row, name in [(fastest_aiio, "fastest_parsed_aiio_slice"), (max_rpm_aiio, "max_rotor_speed_aiio_slice")]:
        for metric, unit in [
            ("speed_max_m_s", "m/s"),
            ("rotor_spd_max_reported_units", "rpm"),
            ("current_racing_x_drag_at_sample_vmax_n", "N"),
            ("current_racing_required_total_thrust_over_max_at_sample_vmax", "ratio"),
        ]:
            add_metric(
                rows,
                row_type="airframe_drag_packet_aiio_sample_extreme",
                name=f"{name}:{source_row['name']}",
                metric=metric,
                value=f(source_row, metric),
                unit=unit,
                source_file=AIIO_SAMPLE,
                source_url=source_row.get("source", ""),
                source_row_type=source_row.get("row_type", ""),
                evidence_level="parsed_flight_log_envelope",
                note="Parsed AI-IO HDF5 test split extreme; useful for envelope/RPM checks, not a direct drag fit.",
            )


def add_summary(
    rows: list[dict[str, str]],
    airframe_rows: list[dict[str, str]],
    apdrone_rows: list[dict[str, str]],
    aiio_rows: list[dict[str, str]],
) -> None:
    current_10x = require_one(
        airframe_rows,
        lambda r: r.get("row_type") == "current_preset_drag_scan"
        and r.get("name") == "racingQuad_x_10.0m_s",
    )
    current_20x = require_one(
        airframe_rows,
        lambda r: r.get("row_type") == "current_preset_drag_scan"
        and r.get("name") == "racingQuad_x_20.0m_s",
    )
    imav_10x = require_one(
        airframe_rows,
        lambda r: r.get("row_type") == "current_vs_imav_drag_calibration_target"
        and r.get("name") == "racingQuad_x_10.0m_s_imav_target",
    )
    nasa_bare_x = require_one(
        airframe_rows,
        lambda r: r.get("row_type") == "current_vs_nasa_bare_airframe_drag_area"
        and r.get("name") == "racingQuad_x_vs_NASA_Fig30_small_quad_0deg_median",
    )
    nasa_powered_x = require_one(
        airframe_rows,
        lambda r: r.get("row_type") == "current_vs_nasa_powered_full_airframe_drag"
        and r.get("name") == "racingQuad_x_vs_NASA_Fig18_20_22_powered_0deg_mid_rpm_median",
    )
    rpg_10x = require_one(
        airframe_rows,
        lambda r: r.get("row_type") == "current_vs_rpg_rotor_drag_equivalent"
        and r.get("name") == "racingQuad_current_x_vs_RPG_x_10.0m_s",
    )
    rpg_20x = require_one(
        airframe_rows,
        lambda r: r.get("row_type") == "current_vs_rpg_rotor_drag_equivalent"
        and r.get("name") == "racingQuad_current_x_vs_RPG_x_20.0m_s",
    )
    uzh = require_one(
        airframe_rows,
        lambda r: r.get("row_type") == "current_vs_uzh_fpv_speed_envelope"
        and r.get("name") == "racingQuad_x_vs_UZH_FPV_SplitS1",
    )
    ratm = require_one(
        airframe_rows,
        lambda r: r.get("row_type") == "current_vs_ratm_speed_floor"
        and r.get("name") == "racingQuad_x_vs_RATM_21m_s_floor",
    )
    imav_coast = require_one(
        airframe_rows,
        lambda r: r.get("row_type") == "current_vs_imav_coastdown"
        and r.get("name") == "racingQuad_x_20.0to5.0m_s_coastdown",
    )
    rpg_coast = require_one(
        airframe_rows,
        lambda r: r.get("row_type") == "current_vs_rpg_rotor_drag_coastdown"
        and r.get("name") == "racingQuad_current_x_vs_RPG_x_20.0to5.0m_s_coastdown",
    )
    apdrone_decel_coeff = require_one(
        apdrone_rows,
        lambda r: r.get("name") == "straight_decel_v_ge_16"
        and r.get("metric") == "decel_only_drag_coeff_p50",
    )
    apdrone_decel_events = require_one(
        apdrone_rows,
        lambda r: r.get("name") == "straight_decel_v_ge_16"
        and r.get("metric") == "event_count",
    )
    apdrone_decel_over_rq = require_one(
        apdrone_rows,
        lambda r: r.get("name") == "straight_decel_v_ge_16"
        and r.get("metric") == "decel_only_coeff_p50_over_racingQuad_x",
    )
    fastest_aiio = max(aiio_rows, key=lambda r: f(r, "speed_max_m_s"))
    max_rpm_aiio = max(aiio_rows, key=lambda r: f(r, "rotor_spd_max_reported_units"))

    summary = {
        "racingQuad_x_drag_10m_s_n": (f(current_10x, "drag_force_n"), "N"),
        "racingQuad_x_drag_20m_s_n": (f(current_20x, "drag_force_n"), "N"),
        "racingQuad_x_drag_10m_s_over_imav_mass_fit": (
            f(imav_10x, "current_total_force_over_imav"),
            "ratio",
        ),
        "racingQuad_x_target_total_c_to_match_imav_10m_s": (
            f(imav_10x, "target_total_quadratic_c_n_per_m_s2"),
            "N/(m/s)^2",
        ),
        "racingQuad_x_current_total_scale_to_match_imav_10m_s": (
            f(imav_10x, "current_total_scale_to_match_imav"),
            "ratio",
        ),
        "racingQuad_x_current_over_nasa_bare_10m_s": (
            f(nasa_bare_x, "current_drag_force_10m_s_over_nasa_small_quad_median"),
            "ratio",
        ),
        "racingQuad_x_current_over_nasa_powered_reference_speed": (
            f(nasa_powered_x, "current_drag_force_at_reference_speed_over_nasa_powered_median"),
            "ratio",
        ),
        "racingQuad_x_current_over_rpg_10m_s": (f(rpg_10x, "current_over_rpg_force"), "ratio"),
        "racingQuad_x_current_over_rpg_20m_s": (f(rpg_20x, "current_over_rpg_force"), "ratio"),
        "uzh_splits1_vmax_m_s": (f(uzh, "reference_vmax_m_s"), "m/s"),
        "uzh_splits1_current_required_total_thrust_over_max": (
            f(uzh, "required_total_thrust_over_current_total_max"),
            "ratio",
        ),
        "ratm_speed_floor_m_s": (f(ratm, "reference_speed_floor_m_s"), "m/s"),
        "ratm_floor_current_required_total_thrust_over_max": (
            f(ratm, "required_total_thrust_over_current_total_max"),
            "ratio",
        ),
        "imav_20_to_5m_s_current_time_over_reference": (
            f(imav_coast, "current_time_over_imav"),
            "ratio",
        ),
        "imav_20_to_5m_s_current_distance_over_reference": (
            f(imav_coast, "current_distance_over_imav"),
            "ratio",
        ),
        "imav_20m_s_current_initial_decel_over_reference": (
            f(imav_coast, "current_initial_decel_over_imav"),
            "ratio",
        ),
        "rpg_20_to_5m_s_current_time_over_reference": (
            f(rpg_coast, "current_time_over_rpg"),
            "ratio",
        ),
        "rpg_20m_s_current_initial_decel_over_reference": (
            f(rpg_coast, "current_initial_decel_over_rpg"),
            "ratio",
        ),
        "apdrone_straight_decel_ge16_event_count": (float(apdrone_decel_events["value"]), "count"),
        "apdrone_straight_decel_ge16_coeff_p50": (float(apdrone_decel_coeff["value"]), "N/(m/s)^2"),
        "apdrone_straight_decel_ge16_coeff_over_racingQuad_x": (
            float(apdrone_decel_over_rq["value"]),
            "ratio",
        ),
        "aiio_fastest_parsed_speed_m_s": (f(fastest_aiio, "speed_max_m_s"), "m/s"),
        "aiio_fastest_parsed_current_drag_n": (
            f(fastest_aiio, "current_racing_x_drag_at_sample_vmax_n"),
            "N",
        ),
        "aiio_max_confirmed_rotor_speed_rpm": (f(max_rpm_aiio, "rotor_spd_max_reported_units"), "rpm"),
        "pelican_system_id_flight_count": (54.0, "count"),
        "manchester_flight_drag_vs_wind_tunnel_accuracy_abs_percent": (6.0, "percent"),
        "manchester_drag_build_up_model_ci_abs_percent": (20.0, "percent"),
    }
    for metric, (value, unit) in summary.items():
        add_metric(
            rows,
            row_type="airframe_drag_packet_summary",
            name="airframe_drag_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            evidence_level="compact_handoff_summary",
            note="Compact airframe-drag handoff summary.",
        )

    add_metric(
        rows,
        row_type="airframe_drag_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Do not fit a physical CdA from sparse powered GPS deceleration alone. Use wind-tunnel/paper rows for "
            "physical drag scale, flight-log rows for envelope feasibility, and keep any gameplay damping separate."
        ),
        unit="text",
        source_file=OUTPUT,
        evidence_level="method_caveat",
        note="Physical drag and stability/gameplay damping should remain separate model surfaces.",
    )


def build_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    airframe_rows = read_rows(AIRFRAME)
    apdrone_rows = read_rows(APDRONE_PACKET)
    aiio_rows = read_rows(AIIO_SAMPLE)

    add_source_inventory(rows, airframe_rows)
    add_current_drag(rows, airframe_rows)
    add_reference_comparisons(rows, airframe_rows)
    add_flight_envelope(rows, airframe_rows)
    add_coastdown_and_log_caveats(rows, airframe_rows, apdrone_rows, aiio_rows)
    add_summary(rows, airframe_rows, apdrone_rows, aiio_rows)
    return rows


def write_csv(path: Path, rows: Iterable[dict[str, str]]) -> None:
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
        writer.writerows(row_list)


def sync_summary(packet_rows: Iterable[dict[str, str]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("airframe_drag_packet_")]
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


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
