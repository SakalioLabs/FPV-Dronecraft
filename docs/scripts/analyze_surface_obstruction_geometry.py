#!/usr/bin/env python3
"""
Compare published near-wall effect magnitudes with the current rotor obstruction
geometry used by the simulator.

Outputs:
  docs/data/surface_obstruction_geometry_reference.csv
"""

from __future__ import annotations

import csv
import math
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/data/surface_obstruction_geometry_reference.csv"
G = 9.80665

SPRINGER_JIRS_2024 = "https://link.springer.com/article/10.1007/s10846-024-02155-7"
CARTER_AIAA_2020 = "https://par.nsf.gov/servlets/purl/10267925"
CURRENT_FLOW_MODEL = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/RotorFlowObstructionModel.java"
CURRENT_ENTITY_SAMPLER = "fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java"
CURRENT_PHYSICS = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java"
CURRENT_OFFLINE_RECORDER = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/tools/OfflineFlightRecorder.java"
OFFLINE_A4MC_LOCAL_OBSTACLE_RESIDUAL = 0.62
OFFLINE_A4MC_WALL_SIDE_SHELTER_OBSTRUCTION = 0.15
OFFLINE_A4MC_PRESSURE_ANOMALY_PA = -220.0
OFFLINE_A4MC_PRESSURE_GRADIENT_FULL_SCALE_PA = 1600.0
OFFLINE_A4MC_PRESSURE_GRADIENT_MAX_WIND_EQUIVALENT_MPS = 2.4


@dataclass(frozen=True)
class Preset:
    name: str
    mass_kg: float
    rotor_count: int
    rotor_radius_m: float
    max_rotor_thrust_n: float

    @property
    def weight_n(self) -> float:
        return self.mass_kg * G

    @property
    def hover_thrust_per_rotor_n(self) -> float:
        return self.weight_n / self.rotor_count

    @property
    def hover_thrust_fraction(self) -> float:
        return self.hover_thrust_per_rotor_n / self.max_rotor_thrust_n

    @property
    def hover_spin_ratio(self) -> float:
        return math.sqrt(max(0.0, self.hover_thrust_fraction))


@dataclass(frozen=True)
class OfflineWallSkimA4mcProfile:
    geometric_obstruction: float
    local_obstacle_residual: float
    residual_obstruction: float
    shelter_gradient_obstruction: float
    pressure_gradient_disk_wind_mps: float
    combined_obstruction: float

    @property
    def thrust_multiplier(self) -> float:
        return obstruction_thrust_multiplier(self.combined_obstruction)


PRESETS = [
    Preset("racingQuad", 1.1, 4, 0.0635, 13.5),
    Preset("apDrone", 0.6284, 4, 5.1 * 0.0254 * 0.5, 13.5),
    Preset("cinewhoop", 0.95, 4, 0.038, 8.0),
    Preset("heavyLift", 4.5, 4, 0.127, 38.0),
]

SAMPLE_DIRECTIONS = [
    (1.0, 0.0),
    (-1.0, 0.0),
    (0.0, 1.0),
    (0.0, -1.0),
    (math.sqrt(0.5), math.sqrt(0.5)),
    (math.sqrt(0.5), -math.sqrt(0.5)),
    (-math.sqrt(0.5), math.sqrt(0.5)),
    (-math.sqrt(0.5), -math.sqrt(0.5)),
]

CLEARANCE_OVER_R = [0.0, 0.1, 0.2, 0.25, 0.3, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0, 10.0]


def clamp(value: float, lo: float, hi: float) -> float:
    return min(hi, max(lo, value))


def sample_weight(direction: tuple[float, float]) -> float:
    x, z = direction
    major_axis = max(abs(x), abs(z))
    minor_axis = min(abs(x), abs(z))
    diagonal_mix = 0.0 if major_axis <= 1.0e-9 else minor_axis / major_axis
    return 1.0 - 0.30 * clamp(diagonal_mix, 0.0, 1.0)


def proximity_from_distance(distance_m: float, max_distance_m: float) -> float:
    if not math.isfinite(distance_m):
        return 0.0
    return 1.0 - clamp(distance_m / max_distance_m, 0.0, 1.0)


def obstruction_intensity_for_flat_wall(clearance_m: float, max_distance_m: float) -> float:
    peak_proximity = 0.0
    weighted_proximity = 0.0
    total_weight = 0.0
    for direction in SAMPLE_DIRECTIONS:
        projection = direction[0]
        distance_m = math.inf if projection <= 1.0e-9 else max(0.0, clearance_m) / projection
        proximity = proximity_from_distance(distance_m, max_distance_m)
        shaped_proximity = proximity**1.12
        weight = sample_weight(direction)
        peak_proximity = max(peak_proximity, proximity)
        weighted_proximity += weight * shaped_proximity
        total_weight += weight
    if total_weight <= 1.0e-9 or peak_proximity <= 1.0e-6:
        return 0.0
    disk_coverage = weighted_proximity / total_weight
    return clamp(0.70 * peak_proximity + 0.30 * disk_coverage, 0.0, 1.0)


def runtime_side_flow_scan_distance_m(radius_m: float) -> float:
    return clamp(radius_m * 2.4 + 0.22, 0.32, 0.82)


def offline_wall_skim_scan_distance_m(radius_m: float) -> float:
    return clamp(radius_m * 6.5, 0.32, 0.70)


def disk_segment_blocked_fraction(clearance_over_r: float) -> float:
    if clearance_over_r <= -1.0:
        return 1.0
    if clearance_over_r >= 1.0:
        return 0.0
    x = clamp(clearance_over_r, -1.0, 1.0)
    area_over_r2 = math.acos(x) - x * math.sqrt(max(0.0, 1.0 - x * x))
    return area_over_r2 / math.pi


def wall_effect_force_n(
    preset: Preset,
    obstruction: float,
    transverse_speed_m_s: float = 0.0,
) -> float:
    obstruction = clamp(obstruction, 0.0, 1.0)
    if obstruction <= 1.0e-6:
        return 0.0
    thrust = preset.hover_thrust_per_rotor_n
    spin_ratio = preset.hover_spin_ratio
    thrust_fraction = preset.hover_thrust_fraction
    speed_washout = 1.0 - clamp(transverse_speed_m_s / 12.0, 0.0, 0.78)
    blockage = obstruction**1.18
    wall_cushion = blockage * spin_ratio * (0.35 + 0.65 * thrust_fraction) * speed_washout
    disk_pressure_force = max(thrust, preset.max_rotor_thrust_n * spin_ratio * spin_ratio * 0.70)
    force = disk_pressure_force * clamp(0.110 + 0.450 * wall_cushion, 0.0, 0.45) * blockage * speed_washout
    return clamp(force, 0.0, 4.0)


def obstruction_thrust_multiplier(obstruction: float) -> float:
    return clamp(1.0 - 0.24 * obstruction * obstruction, 0.72, 1.0)


def combine_obstruction_intensity(first: float, second: float) -> float:
    return clamp(1.0 - (1.0 - clamp(first, 0.0, 1.0)) * (1.0 - clamp(second, 0.0, 1.0)), 0.0, 1.0)


def offline_wall_skim_a4mc_profile(
    geometric_obstruction: float,
    shelter_gradient_obstruction: float = OFFLINE_A4MC_WALL_SIDE_SHELTER_OBSTRUCTION,
) -> OfflineWallSkimA4mcProfile:
    residual = clamp(geometric_obstruction, 0.0, 1.0) * OFFLINE_A4MC_LOCAL_OBSTACLE_RESIDUAL
    shelter = clamp(shelter_gradient_obstruction, 0.0, 1.0)
    pressure_delta_pa = abs(OFFLINE_A4MC_PRESSURE_ANOMALY_PA)
    pressure_gradient_disk_wind_mps = clamp(
        pressure_delta_pa / OFFLINE_A4MC_PRESSURE_GRADIENT_FULL_SCALE_PA
        * OFFLINE_A4MC_PRESSURE_GRADIENT_MAX_WIND_EQUIVALENT_MPS,
        0.0,
        OFFLINE_A4MC_PRESSURE_GRADIENT_MAX_WIND_EQUIVALENT_MPS,
    )
    return OfflineWallSkimA4mcProfile(
        geometric_obstruction=clamp(geometric_obstruction, 0.0, 1.0),
        local_obstacle_residual=OFFLINE_A4MC_LOCAL_OBSTACLE_RESIDUAL,
        residual_obstruction=residual,
        shelter_gradient_obstruction=shelter,
        pressure_gradient_disk_wind_mps=pressure_gradient_disk_wind_mps,
        combined_obstruction=combine_obstruction_intensity(residual, shelter),
    )


def row(row_type: str, **values: object) -> dict[str, object]:
    base: dict[str, object] = {
        "row_type": row_type,
        "source": "",
        "source_url": "",
        "preset": "",
        "clearance_over_r": "",
        "clearance_m": "",
        "scan_distance_m": "",
        "scan_distance_over_r": "",
        "disk_segment_blocked_fraction": "",
        "current_runtime_obstruction": "",
        "current_offline_wall_skim_obstruction": "",
        "current_offline_wall_skim_geometric_obstruction": "",
        "current_offline_wall_skim_local_obstacle_residual": "",
        "current_offline_wall_skim_residual_obstruction": "",
        "current_offline_wall_skim_a4mc_shelter_obstruction": "",
        "current_offline_wall_skim_a4mc_pressure_disk_gradient_mps": "",
        "current_runtime_thrust_multiplier_per_affected_rotor": "",
        "current_offline_wall_skim_thrust_multiplier_per_affected_rotor": "",
        "two_affected_rotors_vehicle_thrust_multiplier": "",
        "four_affected_rotors_vehicle_thrust_multiplier": "",
        "wall_force_per_affected_rotor_n": "",
        "two_affected_rotors_wall_force_n": "",
        "two_affected_rotors_wall_force_over_weight": "",
        "hover_thrust_per_rotor_n": "",
        "hover_spin_ratio": "",
        "published_metric": "",
        "published_value": "",
        "published_units": "",
        "note": "",
    }
    base.update(values)
    return base


def source_rows() -> list[dict[str, object]]:
    return [
        row(
            "published_wall_effect_anchor",
            source="Carter/Bouchard/Quinn AIAA 2020 Crazyflie sidewall",
            source_url=CARTER_AIAA_2020,
            published_metric="sidewall_total_lift_change",
            published_value="<5",
            published_units="percent",
            note="Crazyflie sidewall lift decreases slightly; sidewall effect is much smaller than ground/ceiling effect.",
        ),
        row(
            "published_wall_effect_anchor",
            source="Carter/Bouchard/Quinn AIAA 2020 Crazyflie sidewall",
            source_url=CARTER_AIAA_2020,
            clearance_over_r="<0.3",
            published_metric="noticeable_sidewall_lift_change_threshold",
            published_value=">2",
            published_units="percent absolute lift change",
            note="The paper says |Delta L| exceeds about 2% only for z/R < 0.3.",
        ),
        row(
            "published_wall_effect_anchor",
            source="Carter/Bouchard/Quinn AIAA 2020 Crazyflie sidewall",
            source_url=CARTER_AIAA_2020,
            published_metric="differential_lift_near_wall",
            published_value="0.03",
            published_units="L_infinity",
            note="Differential rotor lift close to the wall can drive roll torque even when total lift change is small.",
        ),
        row(
            "published_wall_effect_anchor",
            source="Carter/Bouchard/Quinn AIAA 2020 Crazyflie sidewall",
            source_url=CARTER_AIAA_2020,
            published_metric="estimated_roll_torque",
            published_value="0.2",
            published_units="N mm",
            note="Crazyflie estimate; not a direct 5-inch FPV force target.",
        ),
        row(
            "published_wall_effect_anchor",
            source="JIRS 2024 pressure-controlled quadcopter wall tests",
            source_url=SPRINGER_JIRS_2024,
            clearance_over_r="1,1.5,2,2.5,3,~10",
            published_metric="tested_wall_distances",
            published_value="1..10",
            published_units="D/R",
            note="Paper tests wall effect distances normalized by propeller radius and treats D > 10R as negligible wall effect.",
        ),
        row(
            "published_wall_effect_anchor",
            source="JIRS 2024 pressure-controlled quadcopter wall tests",
            source_url=SPRINGER_JIRS_2024,
            published_metric="terraXcube_wall_force_coefficient_range",
            published_value="0..0.01",
            published_units="cF",
            note="Force coefficients rose as wall distance decreased, but values stayed small and uncertainty was important.",
        ),
        row(
            "published_wall_effect_anchor",
            source="JIRS 2024 pressure-controlled quadcopter wall tests",
            source_url=SPRINGER_JIRS_2024,
            published_metric="terraXcube_wall_torque_coefficient_max",
            published_value="0.018",
            published_units="cQ",
            note="Reported for the n_infinity=4000 RPM case with 12x5 propellers.",
        ),
        row(
            "published_wall_effect_anchor",
            source="JIRS 2024 pressure-controlled quadcopter wall tests",
            source_url=SPRINGER_JIRS_2024,
            published_metric="cfd_reference_force_at_1p2R",
            published_value="0.098",
            published_units="N",
            note="The paper quotes a 15x5.5 quadrotor CFD reference force at 1.2R from the wall.",
        ),
        row(
            "published_wall_effect_anchor",
            source="JIRS 2024 pressure-controlled quadcopter wall tests",
            source_url=SPRINGER_JIRS_2024,
            published_metric="cfd_reference_torque_at_1p2R",
            published_value="0.075",
            published_units="N m",
            note="The same quoted CFD reference torque has high relative uncertainty for small force/torque sensors.",
        ),
        row(
            "published_wall_effect_anchor",
            source="JIRS 2024 pressure-controlled quadcopter wall tests",
            source_url=SPRINGER_JIRS_2024,
            published_metric="total_thrust_wall_effect",
            published_value="negligible",
            published_units="qualitative",
            note="The paper concludes wall proximity has negligible impact on total thrust, while force/moment toward the wall grows near the wall.",
        ),
    ]


def main() -> None:
    rows: list[dict[str, object]] = source_rows()

    for clearance_over_r in CLEARANCE_OVER_R:
        blocked = disk_segment_blocked_fraction(clearance_over_r)
        rows.append(
            row(
                "ideal_flat_wall_disk_overlap",
                source="circle-segment geometry",
                source_url="",
                clearance_over_r=clearance_over_r,
                disk_segment_blocked_fraction=blocked,
                note="Geometric disk area cut by a planar wall. At d/R=1 the disk is tangent and overlap is zero; at d/R=0 the wall passes through the disk center and overlap is 0.5.",
            )
        )

    for preset in PRESETS:
        runtime_scan = runtime_side_flow_scan_distance_m(preset.rotor_radius_m)
        offline_scan = offline_wall_skim_scan_distance_m(preset.rotor_radius_m)
        for clearance_over_r in CLEARANCE_OVER_R:
            clearance_m = clearance_over_r * preset.rotor_radius_m
            blocked = disk_segment_blocked_fraction(clearance_over_r)
            runtime_obstruction = obstruction_intensity_for_flat_wall(clearance_m, runtime_scan)
            offline_geometric = obstruction_intensity_for_flat_wall(clearance_m, offline_scan)
            offline_a4mc = offline_wall_skim_a4mc_profile(offline_geometric)
            thrust_multiplier = obstruction_thrust_multiplier(runtime_obstruction)
            two_rotor_vehicle_multiplier = 1.0 - 2.0 / preset.rotor_count * (1.0 - thrust_multiplier)
            four_rotor_vehicle_multiplier = thrust_multiplier
            wall_force = wall_effect_force_n(preset, runtime_obstruction)
            rows.append(
                row(
                    "current_flat_wall_runtime_mapping",
                    source="current Java formulas",
                    source_url=f"{CURRENT_FLOW_MODEL}; {CURRENT_ENTITY_SAMPLER}; {CURRENT_PHYSICS}",
                    preset=preset.name,
                    clearance_over_r=clearance_over_r,
                    clearance_m=clearance_m,
                    scan_distance_m=runtime_scan,
                    scan_distance_over_r=runtime_scan / preset.rotor_radius_m,
                    disk_segment_blocked_fraction=blocked,
                    current_runtime_obstruction=runtime_obstruction,
                    current_offline_wall_skim_obstruction=offline_a4mc.combined_obstruction,
                    current_offline_wall_skim_geometric_obstruction=offline_a4mc.geometric_obstruction,
                    current_offline_wall_skim_local_obstacle_residual=offline_a4mc.local_obstacle_residual,
                    current_offline_wall_skim_residual_obstruction=offline_a4mc.residual_obstruction,
                    current_offline_wall_skim_a4mc_shelter_obstruction=offline_a4mc.shelter_gradient_obstruction,
                    current_offline_wall_skim_a4mc_pressure_disk_gradient_mps=offline_a4mc.pressure_gradient_disk_wind_mps,
                    current_runtime_thrust_multiplier_per_affected_rotor=thrust_multiplier,
                    current_offline_wall_skim_thrust_multiplier_per_affected_rotor=offline_a4mc.thrust_multiplier,
                    two_affected_rotors_vehicle_thrust_multiplier=two_rotor_vehicle_multiplier,
                    four_affected_rotors_vehicle_thrust_multiplier=four_rotor_vehicle_multiplier,
                    wall_force_per_affected_rotor_n=wall_force,
                    two_affected_rotors_wall_force_n=2.0 * wall_force,
                    two_affected_rotors_wall_force_over_weight=2.0 * wall_force / preset.weight_n,
                    hover_thrust_per_rotor_n=preset.hover_thrust_per_rotor_n,
                    hover_spin_ratio=preset.hover_spin_ratio,
                    note="Runtime uses an obstacle sampling horizon much larger than rotor radius; offline wall_skim now records the A4MC local-residual, shelter-gradient, and pressure-gradient disk-wind proxies separately from geometric obstruction.",
                )
            )

    for preset in PRESETS:
        closest_clearance_m = 0.04
        runtime_scan = runtime_side_flow_scan_distance_m(preset.rotor_radius_m)
        offline_scan = offline_wall_skim_scan_distance_m(preset.rotor_radius_m)
        runtime_obstruction = obstruction_intensity_for_flat_wall(closest_clearance_m, runtime_scan)
        offline_geometric = obstruction_intensity_for_flat_wall(closest_clearance_m, offline_scan)
        offline_a4mc = offline_wall_skim_a4mc_profile(offline_geometric)
        wall_force = wall_effect_force_n(preset, runtime_obstruction)
        rows.append(
            row(
                "current_offline_wall_skim_closest_rotor",
                source="current Java formulas",
                source_url=CURRENT_OFFLINE_RECORDER,
                preset=preset.name,
                clearance_over_r=closest_clearance_m / preset.rotor_radius_m,
                clearance_m=closest_clearance_m,
                scan_distance_m=offline_scan,
                scan_distance_over_r=offline_scan / preset.rotor_radius_m,
                disk_segment_blocked_fraction=disk_segment_blocked_fraction(closest_clearance_m / preset.rotor_radius_m),
                current_runtime_obstruction=runtime_obstruction,
                current_offline_wall_skim_obstruction=offline_a4mc.combined_obstruction,
                current_offline_wall_skim_geometric_obstruction=offline_a4mc.geometric_obstruction,
                current_offline_wall_skim_local_obstacle_residual=offline_a4mc.local_obstacle_residual,
                current_offline_wall_skim_residual_obstruction=offline_a4mc.residual_obstruction,
                current_offline_wall_skim_a4mc_shelter_obstruction=offline_a4mc.shelter_gradient_obstruction,
                current_offline_wall_skim_a4mc_pressure_disk_gradient_mps=offline_a4mc.pressure_gradient_disk_wind_mps,
                current_runtime_thrust_multiplier_per_affected_rotor=obstruction_thrust_multiplier(runtime_obstruction),
                current_offline_wall_skim_thrust_multiplier_per_affected_rotor=offline_a4mc.thrust_multiplier,
                wall_force_per_affected_rotor_n=wall_force,
                two_affected_rotors_wall_force_n=2.0 * wall_force,
                two_affected_rotors_wall_force_over_weight=2.0 * wall_force / preset.weight_n,
                hover_thrust_per_rotor_n=preset.hover_thrust_per_rotor_n,
                hover_spin_ratio=preset.hover_spin_ratio,
                note="Offline diagnostic wall_skim keeps the closest rotor about 0.04 m from the wall, attenuates duplicated geometry by an A4MC local obstacle residual, then adds wall-side shelter-gradient obstruction and pressure-gradient disk wind.",
            )
        )

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    print(f"Wrote {len(rows)} rows to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
