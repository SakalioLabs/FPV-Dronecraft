#!/usr/bin/env python3
"""Generate an A4MC local-voxel coupling response packet.

Outputs:
  docs/data/a4mc_local_voxel_coupling_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category
  a4mc_local_voxel_packet_*

This packet mirrors the fabric-side AerodynamicsWindCoupling formulas that turn
trusted A4MC L2 local voxel samples into duplicate-obstacle attenuation,
pressure-gradient disk-wind equivalents, and shelter-gradient side obstruction.
It complements the core disk-gradient packet by making the Minecraft sampling
preprocessor auditable before live A4MC tunnel-mouth traces are available.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
OUTPUT = DATA / "a4mc_local_voxel_coupling_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

A4MC_COUPLING_SOURCE = "fabric-mod/src/main/java/com/tenicana/dronecraft/integration/AerodynamicsWindCoupling.java"
DRONE_ENTITY_SOURCE = "fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java"
LOCAL_VOXEL_DISK_SAMPLING_SOURCES = f"{A4MC_COUPLING_SOURCE}; {DRONE_ENTITY_SOURCE}"
A4MC_GAMEPLAY_SAMPLE_SOURCE = "_reference/Aerodynamics4MC-Core/api/src/main/java/com/aerodynamics4mc/api/GameplayWindSample.java"

LOCAL_VOXEL_BASE_COVERAGE = 0.32
LOCAL_VOXEL_SHELTER_COVERAGE = 0.48
LOCAL_VOXEL_MIN_OBSTRUCTION_RESIDUAL = 0.28
LOCAL_VOXEL_SHELTER_GRADIENT_OBSTRUCTION_GAIN = 0.42
LOCAL_VOXEL_SHELTER_GRADIENT_MAX_OBSTRUCTION = 0.22
LOCAL_VOXEL_PRESSURE_GRADIENT_FULL_SCALE_PA = 1600.0
LOCAL_VOXEL_PRESSURE_GRADIENT_MAX_WIND_EQUIV_MPS = 2.4
MAX_LOCAL_VOXEL_PRESSURE_PA = 5000.0
SOURCE_FULL_TRUST_AGE_TICKS = 40
SOURCE_ZERO_TRUST_AGE_TICKS = 160

ROTOR_DISK_SURFACE_CENTER_WEIGHT = 0.36
ROTOR_DISK_SURFACE_CARDINAL_WEIGHT = 0.11
ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT = 0.05

CONFIDENCE_LEVELS = [1.0, 0.86, 0.5]
FRESHNESS_AGES_TICKS = [0, 40, 100, 160]
SHELTER_LEVELS = [0.0, 0.35, 0.74, 1.0]
PRESSURE_DELTAS_PA = [0.0, 220.0, 800.0, 1600.0, 3200.0]
PRESSURE_CENTER_CONTRASTS_PA = [0.0, 3200.0, 10000.0]
SHELTER_GRADIENT_DELTAS = [0.0, 0.15, 0.35, 0.74, 1.0]


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def clamp(value: float, lower: float, upper: float) -> float:
    return min(max(value, lower), upper)


def smooth_step(edge0: float, edge1: float, value: float) -> float:
    if edge1 <= edge0:
        return 1.0 if value >= edge1 else 0.0
    t = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def value_text(value: object) -> str:
    if isinstance(value, str):
        return value
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        if not math.isfinite(value):
            return ""
        if abs(value) < 1.0e-12:
            value = 0.0
        return f"{value:.12g}"
    return str(value)


def scenario_token(value: object) -> str:
    return value_text(value).replace("-", "neg").replace(".", "p")


def source_freshness_factor(age_ticks: int) -> float:
    if age_ticks < 0:
        return 1.0
    if age_ticks <= SOURCE_FULL_TRUST_AGE_TICKS:
        return 1.0
    if age_ticks >= SOURCE_ZERO_TRUST_AGE_TICKS:
        return 0.0
    t = (age_ticks - SOURCE_FULL_TRUST_AGE_TICKS) / (
        SOURCE_ZERO_TRUST_AGE_TICKS - SOURCE_FULL_TRUST_AGE_TICKS
    )
    return 1.0 - t * t * (3.0 - 2.0 * t)


def source_quality(trusted: bool, confidence: float, age_ticks: int) -> float:
    if not trusted:
        return 0.0
    return clamp(confidence, 0.0, 1.0) * source_freshness_factor(age_ticks)


def local_obstacle_residual(shelter: float, quality: float) -> float:
    coverage = quality * (
        LOCAL_VOXEL_BASE_COVERAGE + LOCAL_VOXEL_SHELTER_COVERAGE * clamp(shelter, 0.0, 1.0)
    )
    return clamp(1.0 - coverage, LOCAL_VOXEL_MIN_OBSTRUCTION_RESIDUAL, 1.0)


def disk_sample_gradient(edge_delta: float) -> float:
    # Single-sided edge contrast using DroneEntity's current disk quadrature.
    positive_x_weight = ROTOR_DISK_SURFACE_CARDINAL_WEIGHT
    positive_diagonal_weight = 2.0 * ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT
    gradient_weight = (
        4.0 * ROTOR_DISK_SURFACE_CARDINAL_WEIGHT
        + 4.0 * ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT
    )
    return edge_delta * (positive_x_weight + positive_diagonal_weight / math.sqrt(2.0)) / gradient_weight


def disk_mean(center_value: float, edge_delta: float) -> float:
    edge_weight = 4.0 * ROTOR_DISK_SURFACE_CARDINAL_WEIGHT + 4.0 * ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT
    total_weight = ROTOR_DISK_SURFACE_CENTER_WEIGHT + edge_weight
    affected_weight = ROTOR_DISK_SURFACE_CARDINAL_WEIGHT + 2.0 * ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT
    return center_value + edge_delta * affected_weight / total_weight


def disk_weighted_mean(center_value: float, affected_edge_value: float, unaffected_edge_value: float) -> float:
    edge_weight = 4.0 * ROTOR_DISK_SURFACE_CARDINAL_WEIGHT + 4.0 * ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT
    affected_weight = ROTOR_DISK_SURFACE_CARDINAL_WEIGHT + 2.0 * ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT
    unaffected_weight = edge_weight - affected_weight
    total_weight = ROTOR_DISK_SURFACE_CENTER_WEIGHT + edge_weight
    return (
        center_value * ROTOR_DISK_SURFACE_CENTER_WEIGHT
        + affected_edge_value * affected_weight
        + unaffected_edge_value * unaffected_weight
    ) / total_weight


def pressure_wind_equivalent(gradient_pa: float) -> float:
    magnitude = abs(gradient_pa)
    if magnitude <= 1.0e-6:
        return 0.0
    return clamp(
        magnitude / LOCAL_VOXEL_PRESSURE_GRADIENT_FULL_SCALE_PA
        * LOCAL_VOXEL_PRESSURE_GRADIENT_MAX_WIND_EQUIV_MPS,
        0.0,
        LOCAL_VOXEL_PRESSURE_GRADIENT_MAX_WIND_EQUIV_MPS,
    )


def adopted_pressure(pressure_pa: float, quality: float) -> float:
    return clamp(pressure_pa, -MAX_LOCAL_VOXEL_PRESSURE_PA, MAX_LOCAL_VOXEL_PRESSURE_PA) * quality


def adopted_shelter(shelter: float, quality: float, fallback_shelter: float) -> float:
    fallback = clamp(fallback_shelter, 0.0, 1.0)
    if quality <= 1.0e-9:
        return fallback
    return fallback * (1.0 - quality) + clamp(shelter, 0.0, 1.0) * quality


def shelter_obstruction(mean_shelter: float, gradient: float) -> float:
    magnitude = abs(gradient)
    if magnitude <= 1.0e-6:
        return 0.0
    shelter_gate = 0.35 + 0.65 * clamp(mean_shelter, 0.0, 1.0)
    return clamp(
        LOCAL_VOXEL_SHELTER_GRADIENT_OBSTRUCTION_GAIN * magnitude * shelter_gate,
        0.0,
        LOCAL_VOXEL_SHELTER_GRADIENT_MAX_OBSTRUCTION,
    )


def add_metric(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: str,
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
            "source_file": source_file,
            "source_url": source_url,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def add_source_inventory(rows: list[dict[str, str]]) -> None:
    sources = [
        (
            "fabric_a4mc_local_voxel_coupling",
            "AerodynamicsWindCoupling maps trusted local L2 A4MC samples into obstacle residual, pressure-gradient disk wind, and shelter-gradient side obstruction.",
            A4MC_COUPLING_SOURCE,
            "",
        ),
        (
            "drone_entity_disk_sampler",
            "DroneEntity samples rotor center plus cardinal/diagonal disk-edge points, then applies the local voxel coupling before calling drone-sim-core.",
            DRONE_ENTITY_SOURCE,
            "",
        ),
        (
            "a4mc_gameplay_sample",
            "GameplayWindSample exposes source confidence, freshness, pressure anomaly, shelter, and local voxel authority used by this bridge.",
            A4MC_GAMEPLAY_SAMPLE_SOURCE,
            "",
        ),
    ]
    for name, description, source_file, source_url in sources:
        add_metric(
            rows,
            row_type="a4mc_local_voxel_packet_source_inventory",
            name=name,
            metric="source_description",
            value=description,
            unit="text",
            source_file=source_file,
            source_url=source_url,
            evidence_role="local_voxel_coupling_source_inventory",
            note="Source inventory for the A4MC local-voxel coupling response packet.",
        )


def add_quality_residual_matrix(rows: list[dict[str, str]]) -> None:
    metric_units = {
        "input_confidence": "fraction",
        "input_freshness_age_ticks": "ticks",
        "input_shelter_factor": "fraction",
        "core_source_quality": "fraction",
        "core_local_voxel_obstacle_coverage": "fraction",
        "core_local_obstacle_residual_factor": "multiplier",
        "duplicated_geometric_obstruction_removed_fraction": "fraction",
    }
    for confidence in CONFIDENCE_LEVELS:
        for age in FRESHNESS_AGES_TICKS:
            for shelter in SHELTER_LEVELS:
                quality = source_quality(True, confidence, age)
                coverage = quality * (
                    LOCAL_VOXEL_BASE_COVERAGE + LOCAL_VOXEL_SHELTER_COVERAGE * shelter
                )
                residual = local_obstacle_residual(shelter, quality)
                metrics = {
                    "input_confidence": confidence,
                    "input_freshness_age_ticks": age,
                    "input_shelter_factor": shelter,
                    "core_source_quality": quality,
                    "core_local_voxel_obstacle_coverage": coverage,
                    "core_local_obstacle_residual_factor": residual,
                    "duplicated_geometric_obstruction_removed_fraction": 1.0 - residual,
                }
                name = (
                    f"confidence_{scenario_token(confidence)}"
                    f"_age_{age}_shelter_{scenario_token(shelter)}"
                )
                for metric, value in metrics.items():
                    add_metric(
                        rows,
                        row_type="a4mc_local_voxel_packet_quality_residual_matrix",
                        name=name,
                        metric=metric,
                        value=value,
                        unit=metric_units[metric],
                        source_file=A4MC_COUPLING_SOURCE,
                        evidence_role="local_voxel_obstacle_residual_matrix",
                        note="Source-quality-gated attenuation of duplicate geometric wall/tunnel obstruction.",
                    )


def local_obstacle_residual_or_body_fallback(
    *,
    body_residual: float,
    rotor_has_flow: bool,
    rotor_trusted: bool,
    rotor_local_voxel_flow: bool,
    rotor_confidence: float,
    rotor_shelter: float,
    rotor_age_ticks: int,
) -> float:
    fallback = clamp(body_residual, LOCAL_VOXEL_MIN_OBSTRUCTION_RESIDUAL, 1.0)
    rotor_quality = source_quality(rotor_trusted, rotor_confidence, rotor_age_ticks) if rotor_has_flow else 0.0
    if not rotor_has_flow or not rotor_local_voxel_flow or rotor_quality <= 1.0e-9:
        return fallback
    return local_obstacle_residual(rotor_shelter, rotor_quality)


def add_rotor_residual_fallback_matrix(rows: list[dict[str, str]]) -> None:
    metric_units = {
        "input_body_confidence": "fraction",
        "input_body_shelter_factor": "fraction",
        "body_local_obstacle_residual_factor": "multiplier",
        "input_rotor_has_flow": "bool",
        "input_rotor_trusted_for_gameplay": "bool",
        "input_rotor_local_voxel_flow": "bool",
        "input_rotor_confidence": "fraction",
        "input_rotor_freshness_age_ticks": "ticks",
        "input_rotor_shelter_factor": "fraction",
        "rotor_source_quality": "fraction",
        "adopted_rotor_local_obstacle_residual_factor": "multiplier",
    }
    body_confidence = 0.86
    body_shelter = 0.74
    body_quality = source_quality(True, body_confidence, 0)
    body_residual = local_obstacle_residual(body_shelter, body_quality)
    scenarios = [
        ("missing_rotor_sample", False, False, False, 0.0, body_shelter, -1),
        ("coarse_rotor_sample", True, True, False, 1.0, 1.0, 0),
        ("stale_rotor_sample", True, True, True, 1.0, 1.0, 200),
        ("untrusted_rotor_sample", True, False, True, 1.0, 1.0, 0),
        ("trusted_exposed_rotor_sample", True, True, True, 1.0, 0.0, 0),
        ("trusted_sheltered_rotor_sample", True, True, True, 1.0, 1.0, 0),
    ]
    for name, has_flow, trusted, local_voxel, confidence, shelter, age in scenarios:
        rotor_quality = source_quality(trusted, confidence, age) if has_flow else 0.0
        adopted_residual = local_obstacle_residual_or_body_fallback(
            body_residual=body_residual,
            rotor_has_flow=has_flow,
            rotor_trusted=trusted,
            rotor_local_voxel_flow=local_voxel,
            rotor_confidence=confidence,
            rotor_shelter=shelter,
            rotor_age_ticks=age,
        )
        metrics = {
            "input_body_confidence": body_confidence,
            "input_body_shelter_factor": body_shelter,
            "body_local_obstacle_residual_factor": body_residual,
            "input_rotor_has_flow": has_flow,
            "input_rotor_trusted_for_gameplay": trusted,
            "input_rotor_local_voxel_flow": local_voxel,
            "input_rotor_confidence": confidence,
            "input_rotor_freshness_age_ticks": age,
            "input_rotor_shelter_factor": shelter,
            "rotor_source_quality": rotor_quality,
            "adopted_rotor_local_obstacle_residual_factor": adopted_residual,
        }
        for metric, value in metrics.items():
            add_metric(
                rows,
                row_type="a4mc_local_voxel_packet_rotor_residual_fallback_matrix",
                name=name,
                metric=metric,
                value=value,
                unit=metric_units[metric],
                source_file=A4MC_COUPLING_SOURCE,
                evidence_role="local_voxel_rotor_residual_fallback_matrix",
                note="Rotor-center local-voxel residual fallback behavior when the rotor sample is missing, coarse, stale, untrusted, or usable.",
            )


def add_pressure_gradient_matrix(rows: list[dict[str, str]]) -> None:
    metric_units = {
        "input_confidence": "fraction",
        "input_freshness_age_ticks": "ticks",
        "input_edge_pressure_delta_pa": "Pa",
        "core_source_quality": "fraction",
        "disk_pressure_mean_pa": "Pa",
        "disk_pressure_gradient_body_x_pa": "Pa",
        "pressure_gradient_wind_equivalent_mps": "m/s",
        "quality_weighted_disk_gradient_mps": "m/s",
        "gradient_over_max_disk_gradient": "fraction",
    }
    for confidence in CONFIDENCE_LEVELS:
        for age in FRESHNESS_AGES_TICKS:
            quality = source_quality(True, confidence, age)
            for pressure_delta in PRESSURE_DELTAS_PA:
                edge_pressure = clamp(pressure_delta, -MAX_LOCAL_VOXEL_PRESSURE_PA, MAX_LOCAL_VOXEL_PRESSURE_PA)
                gradient_pa = disk_sample_gradient(edge_pressure)
                wind_equiv = pressure_wind_equivalent(gradient_pa)
                adopted_gradient_pa = disk_sample_gradient(adopted_pressure(edge_pressure, quality))
                weighted_wind = pressure_wind_equivalent(adopted_gradient_pa)
                metrics = {
                    "input_confidence": confidence,
                    "input_freshness_age_ticks": age,
                    "input_edge_pressure_delta_pa": pressure_delta,
                    "core_source_quality": quality,
                    "disk_pressure_mean_pa": disk_mean(0.0, edge_pressure),
                    "disk_pressure_gradient_body_x_pa": gradient_pa,
                    "pressure_gradient_wind_equivalent_mps": wind_equiv,
                    "quality_weighted_disk_gradient_mps": weighted_wind,
                    "gradient_over_max_disk_gradient": weighted_wind / 12.0,
                }
                name = (
                    f"confidence_{scenario_token(confidence)}"
                    f"_age_{age}_pressure_delta_{scenario_token(pressure_delta)}pa"
                )
                for metric, value in metrics.items():
                    add_metric(
                        rows,
                        row_type="a4mc_local_voxel_packet_pressure_gradient_matrix",
                        name=name,
                        metric=metric,
                        value=value,
                        unit=metric_units[metric],
                        source_file=LOCAL_VOXEL_DISK_SAMPLING_SOURCES,
                        evidence_role="local_voxel_pressure_gradient_matrix",
                        note="Single-sided rotor-disk pressure contrast converted into equivalent disk-wind gradient before core disk-gradient response.",
                    )


def add_pressure_contrast_matrix(rows: list[dict[str, str]]) -> None:
    metric_units = {
        "input_confidence": "fraction",
        "input_freshness_age_ticks": "ticks",
        "input_center_pressure_pa": "Pa",
        "input_edge_pressure_pa": "Pa",
        "input_pressure_contrast_pa": "Pa",
        "core_source_quality": "fraction",
        "raw_disk_pressure_gradient_body_x_pa": "Pa",
        "raw_pressure_gradient_wind_equivalent_mps": "m/s",
        "adopted_disk_pressure_gradient_body_x_pa": "Pa",
        "runtime_weighted_disk_gradient_mps": "m/s",
        "post_equivalent_quality_scaled_mps": "m/s",
        "quality_first_over_post_equivalent": "ratio",
    }
    for confidence in CONFIDENCE_LEVELS:
        for age in FRESHNESS_AGES_TICKS:
            quality = source_quality(True, confidence, age)
            for contrast in PRESSURE_CENTER_CONTRASTS_PA:
                center_pressure = -0.5 * contrast
                edge_pressure = 0.5 * contrast
                raw_center = clamp(center_pressure, -MAX_LOCAL_VOXEL_PRESSURE_PA, MAX_LOCAL_VOXEL_PRESSURE_PA)
                raw_edge = clamp(edge_pressure, -MAX_LOCAL_VOXEL_PRESSURE_PA, MAX_LOCAL_VOXEL_PRESSURE_PA)
                raw_gradient = disk_sample_gradient(raw_edge - raw_center)
                raw_equivalent = pressure_wind_equivalent(raw_gradient)
                adopted_center = adopted_pressure(center_pressure, quality)
                adopted_edge = adopted_pressure(edge_pressure, quality)
                adopted_gradient = disk_sample_gradient(adopted_edge - adopted_center)
                runtime_weighted = pressure_wind_equivalent(adopted_gradient)
                post_equivalent_scaled = raw_equivalent * quality
                metrics = {
                    "input_confidence": confidence,
                    "input_freshness_age_ticks": age,
                    "input_center_pressure_pa": center_pressure,
                    "input_edge_pressure_pa": edge_pressure,
                    "input_pressure_contrast_pa": contrast,
                    "core_source_quality": quality,
                    "raw_disk_pressure_gradient_body_x_pa": raw_gradient,
                    "raw_pressure_gradient_wind_equivalent_mps": raw_equivalent,
                    "adopted_disk_pressure_gradient_body_x_pa": adopted_gradient,
                    "runtime_weighted_disk_gradient_mps": runtime_weighted,
                    "post_equivalent_quality_scaled_mps": post_equivalent_scaled,
                    "quality_first_over_post_equivalent": (
                        runtime_weighted / post_equivalent_scaled
                        if post_equivalent_scaled > 1.0e-9
                        else 0.0
                    ),
                }
                name = (
                    f"confidence_{scenario_token(confidence)}"
                    f"_age_{age}_pressure_contrast_{scenario_token(contrast)}pa"
                )
                for metric, value in metrics.items():
                    add_metric(
                        rows,
                        row_type="a4mc_local_voxel_packet_pressure_contrast_matrix",
                        name=name,
                        metric=metric,
                        value=value,
                        unit=metric_units[metric],
                        source_file=LOCAL_VOXEL_DISK_SAMPLING_SOURCES,
                        evidence_role="local_voxel_pressure_contrast_matrix",
                        note="Symmetric center/edge pressure contrast verifies quality is applied before the pressure-to-wind saturation curve.",
                    )


def add_shelter_gradient_matrix(rows: list[dict[str, str]]) -> None:
    metric_units = {
        "input_confidence": "fraction",
        "input_freshness_age_ticks": "ticks",
        "input_center_shelter": "fraction",
        "input_edge_shelter_delta": "fraction",
        "sample_edge_shelter_factor": "fraction",
        "core_source_quality": "fraction",
        "runtime_center_shelter_factor": "fraction",
        "runtime_edge_shelter_factor": "fraction",
        "runtime_unaffected_edge_shelter_factor": "fraction",
        "disk_mean_shelter_factor": "fraction",
        "disk_shelter_gradient_body_x": "fraction",
        "runtime_shelter_gradient_obstruction": "fraction",
    }
    center_shelter = 0.35
    for confidence in CONFIDENCE_LEVELS:
        for age in FRESHNESS_AGES_TICKS:
            quality = source_quality(True, confidence, age)
            for edge_delta in SHELTER_GRADIENT_DELTAS:
                edge_shelter = clamp(center_shelter + edge_delta, 0.0, 1.0)
                runtime_center_shelter = adopted_shelter(center_shelter, quality, 0.0)
                runtime_edge_shelter = adopted_shelter(
                    edge_shelter,
                    quality,
                    runtime_center_shelter,
                )
                runtime_unaffected_shelter = adopted_shelter(
                    center_shelter,
                    quality,
                    runtime_center_shelter,
                )
                gradient = disk_sample_gradient(runtime_edge_shelter - runtime_unaffected_shelter)
                mean_shelter = clamp(
                    disk_weighted_mean(
                        runtime_center_shelter,
                        runtime_edge_shelter,
                        runtime_unaffected_shelter,
                    ),
                    0.0,
                    1.0,
                )
                obstruction = shelter_obstruction(mean_shelter, gradient)
                metrics = {
                    "input_confidence": confidence,
                    "input_freshness_age_ticks": age,
                    "input_center_shelter": center_shelter,
                    "input_edge_shelter_delta": edge_delta,
                    "sample_edge_shelter_factor": edge_shelter,
                    "core_source_quality": quality,
                    "runtime_center_shelter_factor": runtime_center_shelter,
                    "runtime_edge_shelter_factor": runtime_edge_shelter,
                    "runtime_unaffected_edge_shelter_factor": runtime_unaffected_shelter,
                    "disk_mean_shelter_factor": mean_shelter,
                    "disk_shelter_gradient_body_x": gradient,
                    "runtime_shelter_gradient_obstruction": obstruction,
                }
                name = (
                    f"confidence_{scenario_token(confidence)}"
                    f"_age_{age}_shelter_delta_{scenario_token(edge_delta)}"
                )
                for metric, value in metrics.items():
                    add_metric(
                        rows,
                        row_type="a4mc_local_voxel_packet_shelter_gradient_matrix",
                        name=name,
                        metric=metric,
                        value=value,
                        unit=metric_units[metric],
                        source_file=LOCAL_VOXEL_DISK_SAMPLING_SOURCES,
                        evidence_role="local_voxel_shelter_gradient_matrix",
                        note="Single-sided rotor-disk shelter contrast is source-quality blended per sample before side-flow obstruction.",
                    )


def find_metric(rows: list[dict[str, str]], row_type: str, name: str, metric: str) -> float:
    for row in rows:
        if row["row_type"] == row_type and row["name"] == name and row["metric"] == metric:
            try:
                return float(row["value"])
            except ValueError:
                return math.nan
    return math.nan


def add_summary(rows: list[dict[str, str]]) -> None:
    quality_name = "confidence_0p86_age_0_shelter_0p74"
    pressure_name = "confidence_0p86_age_0_pressure_delta_220pa"
    pressure_max_name = "confidence_1_age_0_pressure_delta_3200pa"
    pressure_saturated_name = "confidence_0p5_age_0_pressure_contrast_10000pa"
    shelter_name = "confidence_0p86_age_0_shelter_delta_0p74"
    stale_shelter_name = "confidence_0p86_age_160_shelter_delta_0p74"
    summary = {
        "quality_residual_scenario_count": (
            len(CONFIDENCE_LEVELS) * len(FRESHNESS_AGES_TICKS) * len(SHELTER_LEVELS),
            "count",
        ),
        "rotor_residual_fallback_scenario_count": (6, "count"),
        "pressure_gradient_scenario_count": (
            len(CONFIDENCE_LEVELS) * len(FRESHNESS_AGES_TICKS) * len(PRESSURE_DELTAS_PA),
            "count",
        ),
        "pressure_contrast_scenario_count": (
            len(CONFIDENCE_LEVELS) * len(FRESHNESS_AGES_TICKS) * len(PRESSURE_CENTER_CONTRASTS_PA),
            "count",
        ),
        "shelter_gradient_scenario_count": (
            len(CONFIDENCE_LEVELS) * len(FRESHNESS_AGES_TICKS) * len(SHELTER_GRADIENT_DELTAS),
            "count",
        ),
        "disk_sample_center_weight": (ROTOR_DISK_SURFACE_CENTER_WEIGHT, "weight"),
        "disk_sample_cardinal_weight": (ROTOR_DISK_SURFACE_CARDINAL_WEIGHT, "weight"),
        "disk_sample_diagonal_weight": (ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT, "weight"),
        "wall_skim_quality_0p86_shelter_0p74_residual": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_quality_residual_matrix",
                quality_name,
                "core_local_obstacle_residual_factor",
            ),
            "multiplier",
        ),
        "coarse_rotor_sample_body_fallback_residual": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_rotor_residual_fallback_matrix",
                "coarse_rotor_sample",
                "adopted_rotor_local_obstacle_residual_factor",
            ),
            "multiplier",
        ),
        "trusted_exposed_rotor_sample_residual": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_rotor_residual_fallback_matrix",
                "trusted_exposed_rotor_sample",
                "adopted_rotor_local_obstacle_residual_factor",
            ),
            "multiplier",
        ),
        "wall_skim_pressure_220pa_quality_weighted_gradient_mps": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_pressure_gradient_matrix",
                pressure_name,
                "quality_weighted_disk_gradient_mps",
            ),
            "m/s",
        ),
        "max_pressure_quality_weighted_gradient_mps": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_pressure_gradient_matrix",
                pressure_max_name,
                "quality_weighted_disk_gradient_mps",
            ),
            "m/s",
        ),
        "saturated_pressure_contrast_runtime_weighted_gradient_mps": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_pressure_contrast_matrix",
                pressure_saturated_name,
                "runtime_weighted_disk_gradient_mps",
            ),
            "m/s",
        ),
        "saturated_pressure_contrast_post_equivalent_scaled_mps": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_pressure_contrast_matrix",
                pressure_saturated_name,
                "post_equivalent_quality_scaled_mps",
            ),
            "m/s",
        ),
        "saturated_pressure_contrast_quality_first_ratio": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_pressure_contrast_matrix",
                pressure_saturated_name,
                "quality_first_over_post_equivalent",
            ),
            "ratio",
        ),
        "wall_skim_shelter_delta_0p74_obstruction": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_shelter_gradient_matrix",
                shelter_name,
                "runtime_shelter_gradient_obstruction",
            ),
            "fraction",
        ),
        "stale_shelter_delta_0p74_obstruction": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_shelter_gradient_matrix",
                stale_shelter_name,
                "runtime_shelter_gradient_obstruction",
            ),
            "fraction",
        ),
    }
    for metric, (value, unit) in summary.items():
        add_metric(
            rows,
            row_type="a4mc_local_voxel_packet_summary",
            name="local_voxel_coupling_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=repo_path(OUTPUT),
            evidence_role="local_voxel_coupling_summary",
            note="Compact summary for A4MC local-voxel coupling before core disk-gradient response.",
        )

    add_metric(
        rows,
        row_type="a4mc_local_voxel_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Synthetic packet mirrors current fabric-side A4MC local-voxel coupling formulas. "
            "It is a bridge audit for pressure/shelter preprocessing, not independent CFD validation."
        ),
        unit="text",
        source_file=repo_path(OUTPUT),
        evidence_role="method_caveat",
        note="Use with a4mc_disk_gradient_response_packet.csv to separate bridge preprocessing from core rotor response.",
    )


def build_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    add_source_inventory(rows)
    add_quality_residual_matrix(rows)
    add_rotor_residual_fallback_matrix(rows)
    add_pressure_gradient_matrix(rows)
    add_pressure_contrast_matrix(rows)
    add_shelter_gradient_matrix(rows)
    add_summary(rows)
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


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def sync_summary(packet_rows: Iterable[dict[str, str]]) -> int:
    summary_rows = read_rows(SUMMARY) if SUMMARY.exists() else []
    synced_rows = [
        {
            "category": row["row_type"],
            "name": row["name"],
            "metric": row["metric"],
            "value": row["value"],
            "unit": row["unit"],
            "source": row.get("source_url") or row.get("source_file", ""),
            "source_file": row.get("source_file", ""),
            "source_url": row.get("source_url", ""),
            "evidence_role": row.get("evidence_role", ""),
            "note": row.get("note", ""),
        }
        for row in packet_rows
    ]
    kept = [
        row for row in summary_rows
        if not row.get("category", "").startswith("a4mc_local_voxel_packet_")
    ]
    insert_at = max(
        (
            index + 1
            for index, row in enumerate(kept)
            if row.get("category", "").startswith("a4mc_source_quality_packet_")
            or row.get("category", "").startswith("a4mc_disk_gradient_packet_")
        ),
        default=len(kept),
    )
    write_csv(SUMMARY, kept[:insert_at] + synced_rows + kept[insert_at:])
    return len(synced_rows)


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
