#!/usr/bin/env python3
"""Generate an A4MC local-voxel coupling response packet.

Outputs:
  docs/data/a4mc_local_voxel_coupling_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category
  a4mc_local_voxel_packet_*

This packet mirrors the fabric-side AerodynamicsWindCoupling formulas that turn
trusted A4MC L2 local voxel samples into duplicate-obstacle attenuation,
pressure-gradient disk-wind equivalents, and shelter-gradient side obstruction.
It also mirrors the core pressure-center offset that consumes those adopted
per-rotor local-voxel signals. It complements the core disk-gradient packet by
making the Minecraft sampling preprocessor and its body-scale moment path
auditable before live A4MC tunnel-mouth traces are available.
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
DRONE_PHYSICS_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java"
LOCAL_VOXEL_DISK_SAMPLING_SOURCES = f"{A4MC_COUPLING_SOURCE}; {DRONE_ENTITY_SOURCE}"
A4MC_GAMEPLAY_SAMPLE_SOURCE = "_reference/Aerodynamics4MC-Core/api/src/main/java/com/aerodynamics4mc/api/GameplayWindSample.java"

LOCAL_VOXEL_BASE_COVERAGE = 0.32
LOCAL_VOXEL_SHELTER_COVERAGE = 0.48
LOCAL_VOXEL_MIN_OBSTRUCTION_RESIDUAL = 0.28
LOCAL_VOXEL_SHELTER_GRADIENT_OBSTRUCTION_GAIN = 0.42
LOCAL_VOXEL_SHELTER_GRADIENT_MAX_OBSTRUCTION = 0.22
LOCAL_VOXEL_PRECIPITATION_SHELTER_RELIEF = 0.72
LOCAL_VOXEL_PRECIPITATION_MIN_EXPOSURE = 0.24
LOCAL_VOXEL_ROTOR_VENTILATION_BODY_SHELTER_LOSS = 0.20
LOCAL_VOXEL_ROTOR_VENTILATION_COVERAGE_LOSS = 0.12
LOCAL_VOXEL_ROTOR_VENTILATION_SHELTER_OBSTRUCTION_LOSS = 0.18
LOCAL_VOXEL_ROTOR_MIN_VENTILATION_EFFICIENCY = 0.72
LOCAL_VOXEL_PACK_VENTILATION_BODY_SHELTER_LOSS = 0.14
LOCAL_VOXEL_PACK_VENTILATION_COVERAGE_LOSS = 0.08
LOCAL_VOXEL_PACK_VENTILATION_SHELTER_OBSTRUCTION_LOSS = 0.10
LOCAL_VOXEL_PACK_MIN_VENTILATION_EFFICIENCY = 0.78
LOCAL_VOXEL_PRESSURE_GRADIENT_FULL_SCALE_PA = 1600.0
LOCAL_VOXEL_PRESSURE_GRADIENT_MAX_WIND_EQUIV_MPS = 2.4
MAX_LOCAL_VOXEL_PRESSURE_PA = 5000.0
RACING_QUAD_ARM_M = 0.18 / math.sqrt(2.0)
PRESSURE_CENTER_MAX_OFFSET_M = 0.024
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
PRESSURE_CENTER_AIRSPEEDS_MPS = [0.0, 6.0, 18.0]
PRESSURE_CENTER_SOURCE_QUALITY_LEVELS = [0.0, 0.5, 1.0]
PRESSURE_CENTER_ROTOR_POSITIONS_XZ_M = [
    (RACING_QUAD_ARM_M, RACING_QUAD_ARM_M),
    (-RACING_QUAD_ARM_M, RACING_QUAD_ARM_M),
    (-RACING_QUAD_ARM_M, -RACING_QUAD_ARM_M),
    (RACING_QUAD_ARM_M, -RACING_QUAD_ARM_M),
]
VENTILATION_SCENARIOS = [
    (
        "exposed_open_air",
        True,
        1.0,
        0,
        0.0,
        [1.0, 1.0, 1.0, 1.0],
        [0.0, 0.0, 0.0, 0.0],
    ),
    (
        "coarse_wall_skim",
        False,
        1.0,
        0,
        0.85,
        [0.50, 0.50, 0.80, 0.80],
        [0.16, 0.16, 0.08, 0.08],
    ),
    (
        "stale_wall_skim",
        True,
        0.86,
        160,
        0.85,
        [0.50, 0.50, 0.80, 0.80],
        [0.16, 0.16, 0.08, 0.08],
    ),
    (
        "wall_skim_full_quality",
        True,
        1.0,
        0,
        0.85,
        [0.50, 0.50, 0.80, 0.80],
        [0.16, 0.16, 0.08, 0.08],
    ),
    (
        "wall_skim_half_quality",
        True,
        0.50,
        0,
        0.85,
        [0.75, 0.75, 0.90, 0.90],
        [0.08, 0.08, 0.04, 0.04],
    ),
    (
        "full_tunnel_clamped",
        True,
        1.0,
        0,
        1.0,
        [0.0, 0.0, 0.0, 0.0],
        [1.0, 1.0, 1.0, 1.0],
    ),
]
PRESSURE_CENTER_ROTOR_SCENARIOS = [
    (
        "symmetric_clean",
        [1.0, 1.0, 1.0, 1.0],
        [0.0, 0.0, 0.0, 0.0],
        [0.0, 0.0, 0.0, 0.0],
    ),
    (
        "offline_wall_skim_proxy",
        [0.62, 0.62, 0.62, 0.62],
        [0.04239, 0.01696, 0.01696, 0.04239],
        [0.08013, 0.03205, 0.03205, 0.08013],
    ),
    (
        "right_shelter_wall",
        [0.35, 0.94, 0.94, 0.35],
        [0.18, 0.02, 0.02, 0.18],
        [0.0, 0.0, 0.0, 0.0],
    ),
    (
        "right_pressure_step",
        [1.0, 1.0, 1.0, 1.0],
        [0.0, 0.0, 0.0, 0.0],
        [2.4, 0.0, 0.0, 2.4],
    ),
    (
        "right_combined_wall_pressure",
        [0.35, 0.94, 0.94, 0.35],
        [0.18, 0.02, 0.02, 0.18],
        [2.4, 0.0, 0.0, 2.4],
    ),
    (
        "front_combined_wall_pressure",
        [0.35, 0.35, 0.94, 0.94],
        [0.18, 0.18, 0.02, 0.02],
        [2.4, 2.4, 0.0, 0.0],
    ),
]


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


def precipitation_exposure_factor(shelter: float, quality: float) -> float:
    adopted_shelter_factor = clamp(shelter, 0.0, 1.0) * quality
    return clamp(
        1.0 - adopted_shelter_factor * LOCAL_VOXEL_PRECIPITATION_SHELTER_RELIEF,
        LOCAL_VOXEL_PRECIPITATION_MIN_EXPOSURE,
        1.0,
    )


def rotor_ventilation_efficiency(
    *,
    local_voxel_flow: bool,
    source_quality_value: float,
    body_shelter_factor: float,
    rotor_residual: float,
    rotor_shelter_obstruction: float,
) -> float:
    if not local_voxel_flow or source_quality_value <= 1.0e-9:
        return 1.0
    body_shelter = clamp(body_shelter_factor * source_quality_value, 0.0, 1.0)
    local_voxel_coverage = clamp(1.0 - rotor_residual, 0.0, 1.0)
    shelter_obstruction_value = clamp(rotor_shelter_obstruction, 0.0, 1.0)
    ventilation_loss = (
        LOCAL_VOXEL_ROTOR_VENTILATION_BODY_SHELTER_LOSS * body_shelter
        + LOCAL_VOXEL_ROTOR_VENTILATION_COVERAGE_LOSS * local_voxel_coverage
        + LOCAL_VOXEL_ROTOR_VENTILATION_SHELTER_OBSTRUCTION_LOSS * shelter_obstruction_value
    )
    return clamp(
        1.0 - ventilation_loss,
        LOCAL_VOXEL_ROTOR_MIN_VENTILATION_EFFICIENCY,
        1.0,
    )


def pack_ventilation_efficiency(
    *,
    local_voxel_flow: bool,
    source_quality_value: float,
    body_shelter_factor: float,
    rotor_residuals: list[float],
    rotor_shelter_obstructions: list[float],
) -> float:
    if not local_voxel_flow or source_quality_value <= 1.0e-9:
        return 1.0
    sample_count = max(1, len(rotor_residuals))
    average_coverage = sum(clamp(1.0 - residual, 0.0, 1.0) for residual in rotor_residuals) / sample_count
    average_shelter_obstruction = (
        sum(clamp(value, 0.0, 1.0) for value in rotor_shelter_obstructions) / sample_count
    )
    body_shelter = clamp(body_shelter_factor * source_quality_value, 0.0, 1.0)
    ventilation_loss = (
        LOCAL_VOXEL_PACK_VENTILATION_BODY_SHELTER_LOSS * body_shelter
        + LOCAL_VOXEL_PACK_VENTILATION_COVERAGE_LOSS * average_coverage
        + LOCAL_VOXEL_PACK_VENTILATION_SHELTER_OBSTRUCTION_LOSS * average_shelter_obstruction
    )
    return clamp(
        1.0 - ventilation_loss,
        LOCAL_VOXEL_PACK_MIN_VENTILATION_EFFICIENCY,
        1.0,
    )


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


def pressure_center_signal(
    local_voxel_residual: float,
    shelter_gradient_obstruction: float,
    pressure_gradient_wind_mps: float,
) -> float:
    local_voxel_coverage = clamp(1.0 - local_voxel_residual, 0.0, 1.0)
    shelter_obstruction = clamp(shelter_gradient_obstruction, 0.0, 1.0)
    pressure_gradient_signal = smooth_step(0.15, 1.80, abs(pressure_gradient_wind_mps))
    return local_voxel_coverage + 0.65 * shelter_obstruction + 0.85 * pressure_gradient_signal


def pressure_center_response(
    residuals: list[float],
    shelter_obstructions: list[float],
    pressure_gradient_winds_mps: list[float],
    airspeed_mps: float,
    source_quality: float,
) -> dict[str, float]:
    signals = [
        pressure_center_signal(residuals[i], shelter_obstructions[i], pressure_gradient_winds_mps[i])
        for i in range(4)
    ]
    mean_signal = sum(signals) / len(signals)
    speed_gate = smooth_step(2.0, 12.0, airspeed_mps)
    source_available = source_quality > 1.0e-9
    if not source_available or mean_signal <= 1.0e-6:
        strength = 0.0
        centroid_x = 0.0
        centroid_z = 0.0
        offset_x = 0.0
        offset_z = 0.0
    else:
        centroid_x = 0.0
        centroid_z = 0.0
        for i, signal in enumerate(signals):
            anomaly = signal - mean_signal
            position_x, position_z = PRESSURE_CENTER_ROTOR_POSITIONS_XZ_M[i]
            centroid_x += position_x * anomaly
            centroid_z += position_z * anomaly
        centroid_x /= len(signals)
        centroid_z /= len(signals)
        strength = clamp(mean_signal * speed_gate, 0.0, 1.0)
        offset_x = clamp(-centroid_x * 0.68 * strength, -PRESSURE_CENTER_MAX_OFFSET_M, PRESSURE_CENTER_MAX_OFFSET_M)
        offset_z = clamp(-centroid_z * 0.44 * strength, -PRESSURE_CENTER_MAX_OFFSET_M, PRESSURE_CENTER_MAX_OFFSET_M)
    offset_magnitude = math.hypot(offset_x, offset_z)
    return {
        "input_source_quality_factor": source_quality,
        "input_airspeed_mps": airspeed_mps,
        "source_available": source_available,
        "rotor0_pressure_center_signal": signals[0],
        "rotor1_pressure_center_signal": signals[1],
        "rotor2_pressure_center_signal": signals[2],
        "rotor3_pressure_center_signal": signals[3],
        "mean_pressure_center_signal": mean_signal if source_available else 0.0,
        "pressure_center_weighted_centroid_x_m": centroid_x,
        "pressure_center_weighted_centroid_z_m": centroid_z,
        "pressure_center_speed_gate": speed_gate if source_available else 0.0,
        "pressure_center_strength": strength,
        "pressure_center_offset_x_m": offset_x,
        "pressure_center_offset_z_m": offset_z,
        "pressure_center_offset_magnitude_m": offset_magnitude,
        "pressure_center_offset_cap_fraction": offset_magnitude / PRESSURE_CENTER_MAX_OFFSET_M,
        "source_quality_is_availability_gate": 1.0 if source_available else 0.0,
    }


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
            "AerodynamicsWindCoupling maps trusted local L2 A4MC samples into obstacle residual, precipitation exposure, pressure-gradient disk wind, and shelter-gradient side obstruction.",
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
        (
            "fpv_core_pressure_center_response",
            "DronePhysics maps adopted asymmetric local-voxel residual, shelter, and pressure-gradient rotor signals into a bounded airframe pressure-center offset and local-voxel thermal ventilation loss.",
            DRONE_PHYSICS_SOURCE,
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


def add_precipitation_exposure_matrix(rows: list[dict[str, str]]) -> None:
    metric_units = {
        "input_confidence": "fraction",
        "input_freshness_age_ticks": "ticks",
        "input_shelter_factor": "fraction",
        "core_source_quality": "fraction",
        "adopted_precipitation_shelter_factor": "fraction",
        "precipitation_exposure_factor": "multiplier",
        "direct_precipitation_removed_fraction": "fraction",
    }
    for confidence in CONFIDENCE_LEVELS:
        for age in FRESHNESS_AGES_TICKS:
            for shelter in SHELTER_LEVELS:
                quality = source_quality(True, confidence, age)
                adopted_shelter_factor = clamp(shelter, 0.0, 1.0) * quality
                exposure = precipitation_exposure_factor(shelter, quality)
                metrics = {
                    "input_confidence": confidence,
                    "input_freshness_age_ticks": age,
                    "input_shelter_factor": shelter,
                    "core_source_quality": quality,
                    "adopted_precipitation_shelter_factor": adopted_shelter_factor,
                    "precipitation_exposure_factor": exposure,
                    "direct_precipitation_removed_fraction": 1.0 - exposure,
                }
                name = (
                    f"confidence_{scenario_token(confidence)}"
                    f"_age_{age}_shelter_{scenario_token(shelter)}"
                )
                for metric, value in metrics.items():
                    add_metric(
                        rows,
                        row_type="a4mc_local_voxel_packet_precipitation_exposure_matrix",
                        name=name,
                        metric=metric,
                        value=value,
                        unit=metric_units[metric],
                        source_file=A4MC_COUPLING_SOURCE,
                        evidence_role="local_voxel_precipitation_exposure_matrix",
                        note="Source-quality-gated A4MC shelter attenuation of direct rain exposure before wet-prop film loading.",
                    )


def add_ventilation_matrix(rows: list[dict[str, str]]) -> None:
    metric_units = {
        "input_confidence": "fraction",
        "input_freshness_age_ticks": "ticks",
        "input_local_voxel_flow": "bool",
        "input_body_shelter_factor": "fraction",
        "core_source_quality": "fraction",
        "adopted_body_shelter_factor": "fraction",
        "average_local_voxel_coverage": "fraction",
        "average_shelter_obstruction": "fraction",
        "rotor0_local_voxel_coverage": "fraction",
        "rotor0_shelter_obstruction": "fraction",
        "rotor0_ventilation_efficiency": "multiplier",
        "rotor1_local_voxel_coverage": "fraction",
        "rotor1_shelter_obstruction": "fraction",
        "rotor1_ventilation_efficiency": "multiplier",
        "rotor2_local_voxel_coverage": "fraction",
        "rotor2_shelter_obstruction": "fraction",
        "rotor2_ventilation_efficiency": "multiplier",
        "rotor3_local_voxel_coverage": "fraction",
        "rotor3_shelter_obstruction": "fraction",
        "rotor3_ventilation_efficiency": "multiplier",
        "minimum_rotor_ventilation_efficiency": "multiplier",
        "pack_ventilation_efficiency": "multiplier",
        "pack_minus_min_rotor_efficiency": "multiplier",
    }
    for (
        name,
        local_voxel_flow,
        confidence,
        age,
        body_shelter,
        rotor_residuals,
        rotor_shelter_obstructions,
    ) in VENTILATION_SCENARIOS:
        quality = source_quality(True, confidence, age)
        local_voxel_coverages = [
            clamp(1.0 - residual, 0.0, 1.0)
            for residual in rotor_residuals
        ]
        rotor_efficiencies = [
            rotor_ventilation_efficiency(
                local_voxel_flow=local_voxel_flow,
                source_quality_value=quality,
                body_shelter_factor=body_shelter,
                rotor_residual=rotor_residuals[i],
                rotor_shelter_obstruction=rotor_shelter_obstructions[i],
            )
            for i in range(4)
        ]
        average_coverage = sum(local_voxel_coverages) / len(local_voxel_coverages)
        average_shelter_obstruction = (
            sum(clamp(value, 0.0, 1.0) for value in rotor_shelter_obstructions)
            / len(rotor_shelter_obstructions)
        )
        pack_efficiency = pack_ventilation_efficiency(
            local_voxel_flow=local_voxel_flow,
            source_quality_value=quality,
            body_shelter_factor=body_shelter,
            rotor_residuals=rotor_residuals,
            rotor_shelter_obstructions=rotor_shelter_obstructions,
        )
        metrics = {
            "input_confidence": confidence,
            "input_freshness_age_ticks": age,
            "input_local_voxel_flow": local_voxel_flow,
            "input_body_shelter_factor": body_shelter,
            "core_source_quality": quality,
            "adopted_body_shelter_factor": (
                clamp(body_shelter * quality, 0.0, 1.0)
                if local_voxel_flow and quality > 1.0e-9
                else 0.0
            ),
            "average_local_voxel_coverage": average_coverage,
            "average_shelter_obstruction": average_shelter_obstruction,
            "minimum_rotor_ventilation_efficiency": min(rotor_efficiencies),
            "pack_ventilation_efficiency": pack_efficiency,
            "pack_minus_min_rotor_efficiency": pack_efficiency - min(rotor_efficiencies),
        }
        for rotor_index in range(4):
            metrics[f"rotor{rotor_index}_local_voxel_coverage"] = local_voxel_coverages[rotor_index]
            metrics[f"rotor{rotor_index}_shelter_obstruction"] = clamp(
                rotor_shelter_obstructions[rotor_index],
                0.0,
                1.0,
            )
            metrics[f"rotor{rotor_index}_ventilation_efficiency"] = rotor_efficiencies[rotor_index]
        for metric, value in metrics.items():
            add_metric(
                rows,
                row_type="a4mc_local_voxel_packet_ventilation_matrix",
                name=name,
                metric=metric,
                value=value,
                unit=metric_units[metric],
                source_file=DRONE_PHYSICS_SOURCE,
                evidence_role="local_voxel_thermal_ventilation_matrix",
                note="A4MC local-voxel shelter reduces motor/ESC rotor ventilation and whole-pack cooling only when trusted fresh local L2 flow is available.",
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


def add_pressure_center_matrix(rows: list[dict[str, str]]) -> None:
    metric_units = {
        "input_source_quality_factor": "fraction",
        "input_airspeed_mps": "m/s",
        "source_available": "bool",
        "rotor0_pressure_center_signal": "fraction",
        "rotor1_pressure_center_signal": "fraction",
        "rotor2_pressure_center_signal": "fraction",
        "rotor3_pressure_center_signal": "fraction",
        "mean_pressure_center_signal": "fraction",
        "pressure_center_weighted_centroid_x_m": "m",
        "pressure_center_weighted_centroid_z_m": "m",
        "pressure_center_speed_gate": "fraction",
        "pressure_center_strength": "fraction",
        "pressure_center_offset_x_m": "m",
        "pressure_center_offset_z_m": "m",
        "pressure_center_offset_magnitude_m": "m",
        "pressure_center_offset_cap_fraction": "fraction",
        "source_quality_is_availability_gate": "bool",
    }
    for scenario_name, residuals, shelter_obstructions, pressure_gradient_winds in PRESSURE_CENTER_ROTOR_SCENARIOS:
        for source_quality_value in PRESSURE_CENTER_SOURCE_QUALITY_LEVELS:
            for airspeed in PRESSURE_CENTER_AIRSPEEDS_MPS:
                response = pressure_center_response(
                    residuals,
                    shelter_obstructions,
                    pressure_gradient_winds,
                    airspeed,
                    source_quality_value,
                )
                name = (
                    f"{scenario_name}"
                    f"_quality_{scenario_token(source_quality_value)}"
                    f"_airspeed_{scenario_token(airspeed)}"
                )
                for metric, value in response.items():
                    add_metric(
                        rows,
                        row_type="a4mc_local_voxel_packet_pressure_center_matrix",
                        name=name,
                        metric=metric,
                        value=value,
                        unit=metric_units[metric],
                        source_file=DRONE_PHYSICS_SOURCE,
                        source_url="docs/data/a4mc_local_voxel_coupling_packet.csv",
                        evidence_role="local_voxel_pressure_center_matrix",
                        note="Adopted asymmetric local-voxel rotor signals move the bounded core airframe pressure center; source quality only gates availability.",
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
    full_shelter_name = "confidence_1_age_0_shelter_1"
    stale_shelter_precipitation_name = "confidence_0p86_age_160_shelter_0p74"
    pressure_name = "confidence_0p86_age_0_pressure_delta_220pa"
    pressure_max_name = "confidence_1_age_0_pressure_delta_3200pa"
    pressure_saturated_name = "confidence_0p5_age_0_pressure_contrast_10000pa"
    shelter_name = "confidence_0p86_age_0_shelter_delta_0p74"
    stale_shelter_name = "confidence_0p86_age_160_shelter_delta_0p74"
    pressure_center_wall_skim_name = "offline_wall_skim_proxy_quality_1_airspeed_18"
    pressure_center_right_combined_name = "right_combined_wall_pressure_quality_1_airspeed_18"
    pressure_center_right_combined_half_quality_name = "right_combined_wall_pressure_quality_0p5_airspeed_18"
    pressure_center_front_combined_name = "front_combined_wall_pressure_quality_1_airspeed_18"
    pressure_center_quality_zero_name = "right_combined_wall_pressure_quality_0_airspeed_18"
    ventilation_wall_skim_name = "wall_skim_full_quality"
    ventilation_half_quality_name = "wall_skim_half_quality"
    ventilation_coarse_name = "coarse_wall_skim"
    ventilation_stale_name = "stale_wall_skim"
    ventilation_clamped_tunnel_name = "full_tunnel_clamped"
    summary = {
        "quality_residual_scenario_count": (
            len(CONFIDENCE_LEVELS) * len(FRESHNESS_AGES_TICKS) * len(SHELTER_LEVELS),
            "count",
        ),
        "precipitation_exposure_scenario_count": (
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
        "pressure_center_scenario_count": (
            len(PRESSURE_CENTER_ROTOR_SCENARIOS)
            * len(PRESSURE_CENTER_SOURCE_QUALITY_LEVELS)
            * len(PRESSURE_CENTER_AIRSPEEDS_MPS),
            "count",
        ),
        "ventilation_scenario_count": (len(VENTILATION_SCENARIOS), "count"),
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
        "wall_skim_quality_0p86_shelter_0p74_precipitation_exposure": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_precipitation_exposure_matrix",
                quality_name,
                "precipitation_exposure_factor",
            ),
            "multiplier",
        ),
        "trusted_full_shelter_precipitation_exposure": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_precipitation_exposure_matrix",
                full_shelter_name,
                "precipitation_exposure_factor",
            ),
            "multiplier",
        ),
        "stale_wall_skim_precipitation_exposure": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_precipitation_exposure_matrix",
                stale_shelter_precipitation_name,
                "precipitation_exposure_factor",
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
        "wall_skim_pressure_center_offset_magnitude_m": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_pressure_center_matrix",
                pressure_center_wall_skim_name,
                "pressure_center_offset_magnitude_m",
            ),
            "m",
        ),
        "right_combined_pressure_center_offset_x_m": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_pressure_center_matrix",
                pressure_center_right_combined_name,
                "pressure_center_offset_x_m",
            ),
            "m",
        ),
        "right_combined_half_quality_offset_x_m": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_pressure_center_matrix",
                pressure_center_right_combined_half_quality_name,
                "pressure_center_offset_x_m",
            ),
            "m",
        ),
        "front_combined_pressure_center_offset_z_m": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_pressure_center_matrix",
                pressure_center_front_combined_name,
                "pressure_center_offset_z_m",
            ),
            "m",
        ),
        "quality_zero_pressure_center_offset_magnitude_m": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_pressure_center_matrix",
                pressure_center_quality_zero_name,
                "pressure_center_offset_magnitude_m",
            ),
            "m",
        ),
        "wall_skim_rotor0_ventilation_efficiency": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_ventilation_matrix",
                ventilation_wall_skim_name,
                "rotor0_ventilation_efficiency",
            ),
            "multiplier",
        ),
        "wall_skim_pack_ventilation_efficiency": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_ventilation_matrix",
                ventilation_wall_skim_name,
                "pack_ventilation_efficiency",
            ),
            "multiplier",
        ),
        "half_quality_wall_skim_rotor0_ventilation_efficiency": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_ventilation_matrix",
                ventilation_half_quality_name,
                "rotor0_ventilation_efficiency",
            ),
            "multiplier",
        ),
        "half_quality_wall_skim_pack_ventilation_efficiency": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_ventilation_matrix",
                ventilation_half_quality_name,
                "pack_ventilation_efficiency",
            ),
            "multiplier",
        ),
        "coarse_wall_skim_rotor0_ventilation_efficiency": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_ventilation_matrix",
                ventilation_coarse_name,
                "rotor0_ventilation_efficiency",
            ),
            "multiplier",
        ),
        "stale_wall_skim_pack_ventilation_efficiency": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_ventilation_matrix",
                ventilation_stale_name,
                "pack_ventilation_efficiency",
            ),
            "multiplier",
        ),
        "clamped_tunnel_rotor0_ventilation_efficiency": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_ventilation_matrix",
                ventilation_clamped_tunnel_name,
                "rotor0_ventilation_efficiency",
            ),
            "multiplier",
        ),
        "clamped_tunnel_pack_ventilation_efficiency": (
            find_metric(
                rows,
                "a4mc_local_voxel_packet_ventilation_matrix",
                ventilation_clamped_tunnel_name,
                "pack_ventilation_efficiency",
            ),
            "multiplier",
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
            "It is a bridge and thermal-path audit for pressure/shelter preprocessing, not independent CFD validation."
        ),
        unit="text",
        source_file=repo_path(OUTPUT),
        evidence_role="method_caveat",
        note="Use with a4mc_disk_gradient_response_packet.csv to separate bridge preprocessing, rotor response, and thermal ventilation coupling.",
    )


def build_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    add_source_inventory(rows)
    add_quality_residual_matrix(rows)
    add_precipitation_exposure_matrix(rows)
    add_ventilation_matrix(rows)
    add_rotor_residual_fallback_matrix(rows)
    add_pressure_gradient_matrix(rows)
    add_pressure_contrast_matrix(rows)
    add_shelter_gradient_matrix(rows)
    add_pressure_center_matrix(rows)
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
