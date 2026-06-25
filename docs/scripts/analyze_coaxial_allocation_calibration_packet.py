"""Build a compact coaxial/X8 allocation calibration packet.

Outputs:
  docs/data/coaxial_allocation_calibration_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category coaxial_packet_*

The source coaxial CSVs already contain detailed scans and raw benchmark
summaries. This packet keeps the handoff surface narrow: current coaxialX8
geometry/wake scan, z/D=0.70 11-inch allocation rows, multi-size 60% load
allocation anchors, command-map envelope rows, runtime lookup/model rows, and
command-surface fit quality. It is a mixer/allocation prior, not a final
upper/lower rotor convention mapping.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Callable, Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
OUTPUT = DATA / "coaxial_allocation_calibration_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

INTERFERENCE = DATA / "coaxial_interference_reference.csv"
BENCH_11IN = DATA / "coaxial_benchmark_11in_target_efficiency.csv"
BENCH_MULTI = DATA / "coaxial_benchmark_multi_size_target_efficiency.csv"
COMMAND_ENVELOPE = DATA / "coaxial_benchmark_command_map_envelope.csv"
RUNTIME_LOOKUP = DATA / "coaxial_runtime_allocation_lookup.csv"
SURFACE_FIT = DATA / "coaxial_benchmark_surface_fit.csv"

LOAD_60 = 0.60
CURRENT_Z_OVER_D = 0.72


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
            "note": note,
        }
    )


def source(row: dict[str, str]) -> str:
    return row.get("source", "")


def add_metrics_from_row(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    source_row: dict[str, str],
    source_file: Path,
    metrics: Iterable[tuple[str, str]],
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
            source_url=source(source_row),
            source_row_type=source_row.get("row_type", ""),
            note=note or source_row.get("note", ""),
        )


def require_one(rows: list[dict[str, str]], predicate: Callable[[dict[str, str]], bool]) -> dict[str, str]:
    for row in rows:
        if predicate(row):
            return row
    raise LookupError("required source row not found")


def add_reference(rows: list[dict[str, str]], interference_rows: list[dict[str, str]]) -> None:
    platform = require_one(interference_rows, lambda r: r.get("row_type") == "reference_benchmark_platform")
    add_metrics_from_row(
        rows,
        row_type="coaxial_packet_reference_platform",
        name=platform["name"],
        source_row=platform,
        source_file=INTERFERENCE,
        metrics=[
            ("thrust_load_cell_capacity_kgf", "kgf"),
            ("thrust_precision_gf", "gf"),
            ("torque_capacity_nm", "N*m"),
            ("torque_precision_nm", "N*m"),
        ],
        note="Open coaxial benchmark rig measurement capability.",
    )
    add_metric(
        rows,
        row_type="coaxial_packet_reference_platform",
        name=platform["name"],
        metric="measured_channels",
        value=platform.get("measured_channels", ""),
        unit="text",
        source_file=INTERFERENCE,
        source_url=source(platform),
        source_row_type=platform.get("row_type", ""),
        note="Open coaxial benchmark rig measurement capability.",
    )

    spacing = require_one(interference_rows, lambda r: r.get("row_type") == "reference_spacing_scan")
    add_metrics_from_row(
        rows,
        row_type="coaxial_packet_reference_spacing_scan",
        name=spacing["name"],
        source_row=spacing,
        source_file=INTERFERENCE,
        metrics=[
            ("z_over_d_min", "z/D"),
            ("z_over_d_max", "z/D"),
            ("spacing_point_count", "count"),
            ("command_grid_points_per_spacing", "count"),
            ("points_per_rotor_set", "count"),
        ],
        note="Public result log spacing-sweep metadata.",
    )

    for region in [r for r in interference_rows if r.get("row_type") == "reference_efficiency_region"]:
        for metric, unit in [
            ("z_over_d_min", "z/D"),
            ("z_over_d_center", "z/D"),
            ("z_over_d_max", "z/D"),
        ]:
            add_metric(
                rows,
                row_type="coaxial_packet_reference_region",
                name=region["name"],
                metric=metric,
                value=f(region, metric),
                unit=unit,
                source_file=INTERFERENCE,
                source_url=source(region),
                source_row_type=region["row_type"],
                note=region.get("note", ""),
            )
        add_metric(
            rows,
            row_type="coaxial_packet_reference_region",
            name=region["name"],
            metric="metric",
            value=region.get("metric", ""),
            unit="text",
            source_file=INTERFERENCE,
            source_url=source(region),
            source_row_type=region["row_type"],
            note=region.get("note", ""),
        )

    claim = require_one(interference_rows, lambda r: r.get("row_type") == "reference_control_allocation_claim")
    add_metric(
        rows,
        row_type="coaxial_packet_reference_allocation_claim",
        name=claim["name"],
        metric="mechanical_efficiency_gain_percent",
        value=f(claim, "mechanical_efficiency_gain_percent"),
        unit="percent",
        source_file=INTERFERENCE,
        source_url=source(claim),
        source_row_type=claim["row_type"],
        note=claim.get("note", ""),
    )


def add_current_geometry_and_wake(rows: list[dict[str, str]], interference_rows: list[dict[str, str]]) -> None:
    geometry = require_one(interference_rows, lambda r: r.get("row_type") == "current_coaxial_geometry")
    add_metrics_from_row(
        rows,
        row_type="coaxial_packet_current_geometry",
        name="coaxialX8",
        source_row=geometry,
        source_file=INTERFERENCE,
        metrics=[
            ("radius_m", "m"),
            ("upper_lower_separation_m", "m"),
            ("separation_over_radius", "R"),
            ("separation_over_diameter", "D"),
        ],
        note="Current project coaxialX8 upper/lower rotor spacing.",
    )

    wake_metrics = [
        ("z_over_d", "z/D"),
        ("separation_m", "m"),
        ("spin_ratio", "ratio"),
        ("target_thrust_per_rotor_n", "N"),
        ("source_induced_velocity_m_s", "m/s"),
        ("wake_interference_intensity", "fraction"),
        ("wake_thrust_scale", "multiplier"),
        ("wake_thrust_loss_percent", "percent"),
        ("wake_swirl_velocity_m_s", "m/s"),
        ("wake_swirl_ratio_tip_speed", "ratio"),
        ("wake_interference_vibration", "fraction"),
        ("wake_swirl_load_factor", "fraction"),
    ]
    for wake in [r for r in interference_rows if r.get("row_type") == "current_coaxial_wake_scan"]:
        add_metrics_from_row(
            rows,
            row_type="coaxial_packet_current_wake_scan",
            name=wake["name"],
            source_row=wake,
            source_file=INTERFERENCE,
            metrics=wake_metrics,
            note="Current Java coaxial wake scan mirrored from the detailed source CSV.",
        )


def add_11in_allocation(rows: list[dict[str, str]], bench_rows: list[dict[str, str]]) -> None:
    metrics = [
        ("z_over_d", "z/D"),
        ("target_total_thrust_g", "g"),
        ("equal_pwm", "PWM"),
        ("optimal_pwm_left", "PWM"),
        ("optimal_pwm_right", "PWM"),
        ("optimal_pwm_ratio_right_over_left", "ratio"),
        ("equal_mechanical_efficiency_g_per_mech_w", "g/Wmech"),
        ("optimal_mechanical_efficiency_g_per_mech_w", "g/Wmech"),
        ("optimal_over_equal_mechanical_efficiency_percent", "percent"),
        ("equal_electrical_efficiency_g_per_w", "g/W"),
        ("optimal_electrical_efficiency_g_per_w", "g/W"),
        ("optimal_over_equal_electrical_efficiency_percent", "percent"),
    ]
    for row in [r for r in bench_rows if r.get("row_type") == "benchmark_11in_zd07_equal_vs_optimal_fit"]:
        add_metrics_from_row(
            rows,
            row_type="coaxial_packet_11in_zd07_allocation",
            name=row["name"],
            source_row=row,
            source_file=BENCH_11IN,
            metrics=metrics,
            note="11-inch z/D=0.70 equal-command vs paper-style optimal-fit allocation.",
        )


def add_multi_size_rows(rows: list[dict[str, str]], multi_rows: list[dict[str, str]]) -> None:
    shape_metrics = [
        ("target_fraction_common_max", "fraction"),
        ("target_total_thrust_g", "g"),
        ("best_z_over_d", "z/D"),
        ("worst_z_over_d", "z/D"),
        ("best_mechanical_efficiency_g_per_mech_w", "g/Wmech"),
        ("worst_mechanical_efficiency_g_per_mech_w", "g/Wmech"),
        ("mechanical_efficiency_spread_percent", "percent"),
        ("current_model_hover_loss_at_best_percent", "percent"),
        ("current_model_hover_loss_at_worst_percent", "percent"),
        ("current_model_hover_loss_best_minus_worst_percent", "percent"),
    ]
    for row in [
        r
        for r in multi_rows
        if r.get("row_type") == "benchmark_multi_equal_command_shape"
        and isclose(r.get("target_fraction_common_max", ""), LOAD_60)
    ]:
        add_metrics_from_row(
            rows,
            row_type="coaxial_packet_multi_shape_60pct",
            name=row["name"],
            source_row=row,
            source_file=BENCH_MULTI,
            metrics=shape_metrics,
            note="Equal-command spacing shape at 60% of each group's common equal-command max thrust.",
        )

    allocation_metrics = [
        ("target_fraction_common_max", "fraction"),
        ("target_total_thrust_g", "g"),
        ("z_over_d", "z/D"),
        ("equal_mechanical_efficiency_g_per_mech_w", "g/Wmech"),
        ("optimal_mechanical_efficiency_g_per_mech_w", "g/Wmech"),
        ("optimal_over_equal_mechanical_efficiency_percent", "percent"),
        ("equal_electrical_efficiency_g_per_w", "g/W"),
        ("optimal_electrical_efficiency_g_per_w", "g/W"),
        ("optimal_over_equal_electrical_efficiency_percent", "percent"),
        ("optimal_pwm_left", "PWM"),
        ("optimal_pwm_right", "PWM"),
        ("optimal_pwm_ratio_right_over_left", "ratio"),
    ]
    for row in [
        r
        for r in multi_rows
        if r.get("row_type") == "benchmark_multi_optimal_vs_equal_target"
        and isclose(r.get("target_fraction_common_max", ""), LOAD_60)
    ]:
        add_metrics_from_row(
            rows,
            row_type="coaxial_packet_multi_optimal_60pct",
            name=row["name"],
            source_row=row,
            source_file=BENCH_MULTI,
            metrics=allocation_metrics,
            note="Multi-size 60% equal-command vs optimal-fit allocation anchor.",
        )


def add_command_envelope(rows: list[dict[str, str]], command_rows: list[dict[str, str]]) -> None:
    summary_metrics = [
        ("target_fraction_common_max", "fraction"),
        ("target_total_thrust_g", "g"),
        ("best_gain_z_over_d", "z/D"),
        ("best_gain_mechanical_percent", "percent"),
        ("best_gain_electrical_percent", "percent"),
        ("best_gain_pwm_ratio_right_over_left", "ratio"),
        ("best_gain_thrust_error_percent", "percent"),
        ("best_absolute_z_over_d", "z/D"),
        ("best_absolute_mechanical_efficiency_g_per_mech_w", "g/Wmech"),
        ("best_absolute_pwm_ratio_right_over_left", "ratio"),
    ]
    for row in [
        r
        for r in command_rows
        if r.get("row_type") == "benchmark_command_map_mechanical_envelope_summary"
        and isclose(r.get("target_fraction_common_max", ""), LOAD_60)
    ]:
        add_metrics_from_row(
            rows,
            row_type="coaxial_packet_command_envelope_60pct",
            name=row["name"],
            source_row=row,
            source_file=COMMAND_ENVELOPE,
            metrics=summary_metrics,
            note="Measured 10x10 command-map envelope at 60% load within +/-5% target thrust.",
        )


def add_runtime_lookup(rows: list[dict[str, str]], runtime_rows: list[dict[str, str]]) -> None:
    raw_metrics = [
        ("lookup_z_over_d", "z/D"),
        ("target_fraction_common_max", "fraction"),
        ("target_total_thrust_g", "g"),
        ("recommended_pwm_ratio_right_over_left", "ratio"),
        ("recommended_left_pwm_scale_vs_equal", "ratio"),
        ("recommended_right_pwm_scale_vs_equal", "ratio"),
        ("actual_thrust_error_percent", "percent"),
        ("mechanical_gain_over_equal_percent", "percent"),
        ("electrical_gain_over_equal_percent", "percent"),
        ("current_model_hover_wake_loss_percent", "percent"),
        ("current_model_max_wake_loss_percent", "percent"),
    ]
    for row in [
        r
        for r in runtime_rows
        if r.get("row_type") == "coaxial_runtime_allocation_lookup"
        and isclose(r.get("target_fraction_common_max", ""), LOAD_60)
    ]:
        add_metrics_from_row(
            rows,
            row_type="coaxial_packet_runtime_raw_group_60pct",
            name=row["name"],
            source_row=row,
            source_file=RUNTIME_LOOKUP,
            metrics=raw_metrics,
            note="Raw group command-map envelope interpolated to coaxialX8 z/D=0.72 at 60% load.",
        )

    model_metrics = [
        ("lookup_z_over_d", "z/D"),
        ("target_fraction_common_max", "fraction"),
        ("reference_target_total_thrust_g", "g"),
        ("recommended_pwm_ratio_right_over_left", "ratio"),
        ("recommended_left_pwm_scale_vs_equal", "ratio"),
        ("recommended_right_pwm_scale_vs_equal", "ratio"),
        ("mechanical_gain_over_equal_percent", "percent"),
        ("electrical_gain_over_equal_percent", "percent"),
        ("current_coaxial_diameter_in", "in"),
        ("nearest_reference_prop_diameter_in", "in"),
        ("all_group_ratio_p10", "ratio"),
        ("all_group_ratio_median", "ratio"),
        ("all_group_ratio_p90", "ratio"),
        ("all_group_mechanical_gain_p10_percent", "percent"),
        ("all_group_mechanical_gain_median_percent", "percent"),
        ("all_group_mechanical_gain_p90_percent", "percent"),
        ("all_group_electrical_gain_p10_percent", "percent"),
        ("all_group_electrical_gain_median_percent", "percent"),
        ("all_group_electrical_gain_p90_percent", "percent"),
        ("group_sample_count", "count"),
    ]
    for row in [r for r in runtime_rows if r.get("row_type") == "coaxial_runtime_allocation_model_point"]:
        add_metrics_from_row(
            rows,
            row_type="coaxial_packet_runtime_model_point",
            name=row["name"],
            source_row=row,
            source_file=RUNTIME_LOOKUP,
            metrics=model_metrics,
            note="Smoothed runtime allocation prior for current coaxialX8 diameter; nearest measured group is central value.",
        )


def add_surface_fit_summary(rows: list[dict[str, str]], fit_rows: list[dict[str, str]]) -> None:
    metrics = [
        ("fit_count", "count"),
        ("median_rmse_over_range_percent", "percent"),
        ("max_rmse_over_range_percent", "percent"),
        ("median_cv_rmse_over_range_percent", "percent"),
        ("max_cv_rmse_over_range_percent", "percent"),
        ("median_r2", "R2"),
        ("min_r2", "R2"),
        ("median_cv_r2", "R2"),
        ("min_cv_r2", "R2"),
        ("max_abs_error_worst_fit", "target unit"),
        ("max_cv_abs_error_worst_fit", "target unit"),
    ]
    for row in [r for r in fit_rows if r.get("row_type") == "benchmark_surface_fit_summary"]:
        add_metrics_from_row(
            rows,
            row_type="coaxial_packet_surface_fit_summary",
            name=row["name"],
            source_row=row,
            source_file=SURFACE_FIT,
            metrics=metrics,
            note="Cubic normalized-command surface fit quality summary over raw 10x10 command maps.",
        )
        add_metric(
            rows,
            row_type="coaxial_packet_surface_fit_summary",
            name=row["name"],
            metric="worst_fit_name_by_cv_rmse_range",
            value=row.get("worst_fit_name_by_cv_rmse_range", ""),
            unit="text",
            source_file=SURFACE_FIT,
            source_url=source(row),
            source_row_type=row["row_type"],
            note="Worst cross-validation normalized RMSE fit for this target variable.",
        )


def add_summary(
    rows: list[dict[str, str]],
    interference_rows: list[dict[str, str]],
    bench_11in_rows: list[dict[str, str]],
    multi_rows: list[dict[str, str]],
    command_rows: list[dict[str, str]],
    runtime_rows: list[dict[str, str]],
    fit_rows: list[dict[str, str]],
) -> None:
    geometry = require_one(interference_rows, lambda r: r.get("row_type") == "current_coaxial_geometry")
    hover_072 = require_one(
        interference_rows,
        lambda r: r.get("row_type") == "current_coaxial_wake_scan"
        and r.get("operating_point") == "hover"
        and isclose(r.get("z_over_d", ""), CURRENT_Z_OVER_D),
    )
    max_072 = require_one(
        interference_rows,
        lambda r: r.get("row_type") == "current_coaxial_wake_scan"
        and r.get("operating_point") == "max"
        and isclose(r.get("z_over_d", ""), CURRENT_Z_OVER_D),
    )
    hover_055 = require_one(
        interference_rows,
        lambda r: r.get("row_type") == "current_coaxial_wake_scan"
        and r.get("operating_point") == "hover"
        and isclose(r.get("z_over_d", ""), 0.55),
    )
    max_055 = require_one(
        interference_rows,
        lambda r: r.get("row_type") == "current_coaxial_wake_scan"
        and r.get("operating_point") == "max"
        and isclose(r.get("z_over_d", ""), 0.55),
    )
    alloc_1000 = require_one(
        bench_11in_rows,
        lambda r: r.get("row_type") == "benchmark_11in_zd07_equal_vs_optimal_fit"
        and isclose(r.get("target_total_thrust_g", ""), 1000.0),
    )
    alloc_1500 = require_one(
        bench_11in_rows,
        lambda r: r.get("row_type") == "benchmark_11in_zd07_equal_vs_optimal_fit"
        and isclose(r.get("target_total_thrust_g", ""), 1500.0),
    )
    model_060 = require_one(
        runtime_rows,
        lambda r: r.get("row_type") == "coaxial_runtime_allocation_model_point"
        and isclose(r.get("target_fraction_common_max", ""), LOAD_60),
    )
    multi_60 = [
        r
        for r in multi_rows
        if r.get("row_type") == "benchmark_multi_optimal_vs_equal_target"
        and isclose(r.get("target_fraction_common_max", ""), LOAD_60)
    ]
    strongest_multi = max(multi_60, key=lambda r: f(r, "optimal_over_equal_mechanical_efficiency_percent"))
    command_60 = [
        r
        for r in command_rows
        if r.get("row_type") == "benchmark_command_map_mechanical_envelope_summary"
        and isclose(r.get("target_fraction_common_max", ""), LOAD_60)
    ]
    strongest_command = max(command_60, key=lambda r: f(r, "best_gain_mechanical_percent"))
    thrust_fit = require_one(
        fit_rows,
        lambda r: r.get("row_type") == "benchmark_surface_fit_summary"
        and r.get("target_variable") == "total_thrust_g",
    )
    mech_fit = require_one(
        fit_rows,
        lambda r: r.get("row_type") == "benchmark_surface_fit_summary"
        and r.get("target_variable") == "mechanical_power_w",
    )

    summary = {
        "coaxialX8_separation_over_diameter": (f(geometry, "separation_over_diameter"), "D"),
        "current_hover_wake_loss_zD072_percent": (f(hover_072, "wake_thrust_loss_percent"), "percent"),
        "current_max_wake_loss_zD072_percent": (f(max_072, "wake_thrust_loss_percent"), "percent"),
        "current_hover_wake_loss_zD055_minus_zD072_percent": (
            f(hover_055, "wake_thrust_loss_percent") - f(hover_072, "wake_thrust_loss_percent"),
            "percent",
        ),
        "current_max_wake_loss_zD055_minus_zD072_percent": (
            f(max_055, "wake_thrust_loss_percent") - f(max_072, "wake_thrust_loss_percent"),
            "percent",
        ),
        "eleven_in_zD070_1000g_pwm_ratio_right_over_left": (
            f(alloc_1000, "optimal_pwm_ratio_right_over_left"),
            "ratio",
        ),
        "eleven_in_zD070_1000g_mechanical_gain_percent": (
            f(alloc_1000, "optimal_over_equal_mechanical_efficiency_percent"),
            "percent",
        ),
        "eleven_in_zD070_1500g_pwm_ratio_right_over_left": (
            f(alloc_1500, "optimal_pwm_ratio_right_over_left"),
            "ratio",
        ),
        "eleven_in_zD070_1500g_mechanical_gain_percent": (
            f(alloc_1500, "optimal_over_equal_mechanical_efficiency_percent"),
            "percent",
        ),
        "runtime_model_60pct_pwm_ratio_right_over_left": (
            f(model_060, "recommended_pwm_ratio_right_over_left"),
            "ratio",
        ),
        "runtime_model_60pct_left_pwm_scale_vs_equal": (
            f(model_060, "recommended_left_pwm_scale_vs_equal"),
            "ratio",
        ),
        "runtime_model_60pct_right_pwm_scale_vs_equal": (
            f(model_060, "recommended_right_pwm_scale_vs_equal"),
            "ratio",
        ),
        "runtime_model_60pct_mechanical_gain_percent": (
            f(model_060, "mechanical_gain_over_equal_percent"),
            "percent",
        ),
        "runtime_model_60pct_electrical_gain_percent": (
            f(model_060, "electrical_gain_over_equal_percent"),
            "percent",
        ),
        "runtime_model_60pct_all_group_ratio_p10": (f(model_060, "all_group_ratio_p10"), "ratio"),
        "runtime_model_60pct_all_group_ratio_median": (f(model_060, "all_group_ratio_median"), "ratio"),
        "runtime_model_60pct_all_group_ratio_p90": (f(model_060, "all_group_ratio_p90"), "ratio"),
        "runtime_model_60pct_all_group_mech_gain_p10_percent": (
            f(model_060, "all_group_mechanical_gain_p10_percent"),
            "percent",
        ),
        "runtime_model_60pct_all_group_mech_gain_median_percent": (
            f(model_060, "all_group_mechanical_gain_median_percent"),
            "percent",
        ),
        "runtime_model_60pct_all_group_mech_gain_p90_percent": (
            f(model_060, "all_group_mechanical_gain_p90_percent"),
            "percent",
        ),
        "strongest_multi_optimal_60pct_mechanical_gain_percent": (
            f(strongest_multi, "optimal_over_equal_mechanical_efficiency_percent"),
            "percent",
        ),
        "strongest_multi_optimal_60pct_pwm_ratio_right_over_left": (
            f(strongest_multi, "optimal_pwm_ratio_right_over_left"),
            "ratio",
        ),
        "strongest_command_envelope_60pct_mechanical_gain_percent": (
            f(strongest_command, "best_gain_mechanical_percent"),
            "percent",
        ),
        "strongest_command_envelope_60pct_pwm_ratio_right_over_left": (
            f(strongest_command, "best_gain_pwm_ratio_right_over_left"),
            "ratio",
        ),
        "strongest_command_envelope_60pct_electrical_gain_percent": (
            f(strongest_command, "best_gain_electrical_percent"),
            "percent",
        ),
        "surface_fit_thrust_median_cv_rmse_over_range_percent": (
            f(thrust_fit, "median_cv_rmse_over_range_percent"),
            "percent",
        ),
        "surface_fit_thrust_median_cv_r2": (f(thrust_fit, "median_cv_r2"), "R2"),
        "surface_fit_mechanical_power_median_cv_rmse_over_range_percent": (
            f(mech_fit, "median_cv_rmse_over_range_percent"),
            "percent",
        ),
        "surface_fit_mechanical_power_median_cv_r2": (f(mech_fit, "median_cv_r2"), "R2"),
    }
    for metric, (value, unit) in summary.items():
        add_metric(
            rows,
            row_type="coaxial_packet_summary",
            name="coaxial_allocation_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            note="Compact coaxial/X8 allocation packet summary.",
        )

    add_metric(
        rows,
        row_type="coaxial_packet_summary",
        name="coaxial_allocation_summary",
        metric="strongest_multi_optimal_60pct_name",
        value=strongest_multi["name"],
        unit="text",
        source_file=OUTPUT,
        note="Strongest 60% load optimal-fit mechanical gain in the multi-size source table.",
    )
    add_metric(
        rows,
        row_type="coaxial_packet_summary",
        name="coaxial_allocation_summary",
        metric="strongest_command_envelope_60pct_name",
        value=strongest_command["name"],
        unit="text",
        source_file=OUTPUT,
        note="Strongest 60% load measured command-map envelope mechanical gain.",
    )
    add_metric(
        rows,
        row_type="coaxial_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Use this packet as an allocation/mixer prior. Benchmark right/left command direction must be mapped to "
            "the project's upper/lower rotor ordering and motor limits before runtime use."
        ),
        unit="text",
        source_file=OUTPUT,
        note="Do not directly treat the right/left PWM ratio as an implemented upper/lower rotor controller.",
    )


def build_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    interference_rows = read_rows(INTERFERENCE)
    bench_11in_rows = read_rows(BENCH_11IN)
    multi_rows = read_rows(BENCH_MULTI)
    command_rows = read_rows(COMMAND_ENVELOPE)
    runtime_rows = read_rows(RUNTIME_LOOKUP)
    fit_rows = read_rows(SURFACE_FIT)

    add_reference(rows, interference_rows)
    add_current_geometry_and_wake(rows, interference_rows)
    add_11in_allocation(rows, bench_11in_rows)
    add_multi_size_rows(rows, multi_rows)
    add_command_envelope(rows, command_rows)
    add_runtime_lookup(rows, runtime_rows)
    add_surface_fit_summary(rows, fit_rows)
    add_summary(rows, interference_rows, bench_11in_rows, multi_rows, command_rows, runtime_rows, fit_rows)
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
    kept = [row for row in existing if not row.get("category", "").startswith("coaxial_packet_")]
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
