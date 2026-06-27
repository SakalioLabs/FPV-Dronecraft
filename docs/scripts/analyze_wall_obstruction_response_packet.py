"""Build a wall-obstruction response packet for runtime sidewall tuning.

Outputs:
  docs/data/wall_obstruction_response_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category
  wall_obstruction_packet_*

The packet mirrors the current RotorFlowObstructionModel side-flow geometry
and DronePhysics steady wall-force formula. It keeps the next sidewall tuning
step auditable by separating three concepts that should not be fit as one
scalar: ideal disk overlap, dirty-air thrust loss, and wall attraction force.
"""

from __future__ import annotations

import csv
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
OUTPUT = DATA / "wall_obstruction_response_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

ROTOR_FLOW_MODEL_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/RotorFlowObstructionModel.java"
DRONE_PHYSICS_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java"
DRONE_ENTITY_SOURCE = "fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java"
OFFLINE_RECORDER_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/tools/OfflineFlightRecorder.java"
SURFACE_OBSTRUCTION_REFERENCE = "docs/data/surface_obstruction_geometry_reference.csv"

G = 9.80665
CLEARANCE_OVER_R = [0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 6.0]
OBSTRUCTION_LEVELS = [0.25, 0.50, 0.66, 0.80, 1.00]
TRANSVERSE_SPEEDS_MPS = [0.0, 6.0, 12.0]
SPIN_LABELS = ["hover", "max"]

ROTOR_PLANE_DIRECTIONS = [
    (1.0, 0.0, 0.0),
    (-1.0, 0.0, 0.0),
    (0.0, 0.0, 1.0),
    (0.0, 0.0, -1.0),
    (1.0, 0.0, 1.0),
    (1.0, 0.0, -1.0),
    (-1.0, 0.0, 1.0),
    (-1.0, 0.0, -1.0),
]


@dataclass(frozen=True)
class Preset:
    name: str
    mass_kg: float
    rotor_count: int
    rotor_radius_m: float
    max_rotor_thrust_n: float
    thrust_coefficient_n_per_rad2: float

    @property
    def hover_thrust_fraction(self) -> float:
        return (self.mass_kg * G / self.rotor_count) / self.max_rotor_thrust_n

    @property
    def hover_spin_ratio(self) -> float:
        return math.sqrt(max(0.0, self.hover_thrust_fraction))

    @property
    def max_omega_rad_s(self) -> float:
        return math.sqrt(self.max_rotor_thrust_n / self.thrust_coefficient_n_per_rad2)

    @property
    def weight_newtons(self) -> float:
        return self.mass_kg * G


PRESETS = [
    Preset("racingQuad", 1.1, 4, 0.0635, 13.5, 1.45e-6),
    Preset("apDrone", 0.6284, 4, 5.1 * 0.0254 * 0.5, 13.5, 1.3918976015517363e-6),
    Preset("cinewhoop", 0.95, 4, 0.038, 8.0, 1.15e-5),
    Preset("heavyLift", 4.5, 4, 0.127, 38.0, 4.5e-5),
]


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


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
    evidence_role: str,
    note: str,
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


def clamp(value: float, lower: float, upper: float) -> float:
    return min(max(value, lower), upper)


def normalize(vector: tuple[float, float, float]) -> tuple[float, float, float]:
    length = math.sqrt(sum(component * component for component in vector))
    if length <= 1.0e-12:
        return (0.0, 0.0, 0.0)
    return tuple(component / length for component in vector)


def dot(a: tuple[float, float, float], b: tuple[float, float, float]) -> float:
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def flat_wall_disk_blocked_fraction(clearance_over_radius: float) -> float:
    if not math.isfinite(clearance_over_radius):
        return 0.0
    x = clamp(clearance_over_radius, -1.0, 1.0)
    segment = math.acos(x) - x * math.sqrt(max(0.0, 1.0 - x * x))
    return clamp(segment / math.pi, 0.0, 1.0)


def proximity_from_distance(distance_m: float, max_distance_m: float) -> float:
    if not math.isfinite(distance_m):
        return 0.0
    return 1.0 - clamp(distance_m / max_distance_m, 0.0, 1.0)


def sample_weight(normalized_direction: tuple[float, float, float]) -> float:
    horizontal = math.hypot(normalized_direction[0], normalized_direction[2])
    if horizontal <= 1.0e-9:
        return 0.70
    major_axis = max(abs(normalized_direction[0]), abs(normalized_direction[2]))
    minor_axis = min(abs(normalized_direction[0]), abs(normalized_direction[2]))
    diagonal_mix = 0.0 if major_axis <= 1.0e-9 else minor_axis / major_axis
    return 1.0 - 0.30 * clamp(diagonal_mix, 0.0, 1.0)


def side_flow_sample_max_distance_m(preset: Preset) -> float:
    return clamp(preset.rotor_radius_m * 6.5, 0.32, 0.70)


def distances_to_body_wall(clearance_m: float, wall_direction: tuple[float, float, float]) -> list[float]:
    wall = normalize(wall_direction)
    distances: list[float] = []
    for raw_direction in ROTOR_PLANE_DIRECTIONS:
        sample_direction = normalize(raw_direction)
        projection = dot(sample_direction, wall)
        if projection > 1.0e-9:
            distances.append(max(0.0, clearance_m) / projection)
        else:
            distances.append(math.inf)
    return distances


def obstruction_from_single_wall(preset: Preset, clearance_over_radius: float) -> float:
    distances = distances_to_body_wall(
        clearance_over_radius * preset.rotor_radius_m,
        (1.0, 0.0, 0.0),
    )
    max_distance_m = side_flow_sample_max_distance_m(preset)
    peak_proximity = 0.0
    closest_distance_m = math.inf
    weighted_proximity = 0.0
    total_weight = 0.0

    for distance_m, raw_direction in zip(distances, ROTOR_PLANE_DIRECTIONS):
        normalized_direction = normalize(raw_direction)
        weight = sample_weight(normalized_direction)
        proximity = proximity_from_distance(distance_m, max_distance_m)
        shaped_proximity = proximity**1.12
        peak_proximity = max(peak_proximity, proximity)
        if math.isfinite(distance_m):
            closest_distance_m = min(closest_distance_m, max(0.0, distance_m))
        weighted_proximity += weight * shaped_proximity
        total_weight += weight

    if total_weight <= 1.0e-9 or peak_proximity <= 1.0e-6:
        return 0.0
    disk_coverage = weighted_proximity / total_weight
    segment_coverage = clamp(
        2.0 * flat_wall_disk_blocked_fraction(closest_distance_m / preset.rotor_radius_m),
        0.0,
        1.0,
    )
    return clamp(0.50 * peak_proximity + 0.27 * segment_coverage + 0.23 * disk_coverage, 0.0, 1.0)


def thrust_multiplier(obstruction_intensity: float) -> float:
    obstruction = clamp(obstruction_intensity, 0.0, 1.0)
    return clamp(1.0 - 0.10 * obstruction * obstruction * obstruction, 0.90, 1.0)


def wall_force_geometry_factor(clearance_over_radius: float, flat_wall_disk_coverage: float) -> float:
    if not math.isfinite(clearance_over_radius):
        return 0.0
    distance_lobe = math.exp(-0.45 * max(0.0, clearance_over_radius))
    disk_overlap_lobe = math.sqrt(clamp(flat_wall_disk_coverage, 0.0, 1.0))
    return clamp(max(distance_lobe, disk_overlap_lobe), 0.0, 1.0)


def spin_ratio_for_label(preset: Preset, spin_label: str) -> float:
    if spin_label == "hover":
        return preset.hover_spin_ratio
    if spin_label == "max":
        return 1.0
    raise ValueError(f"Unknown spin label {spin_label}")


def thrust_fraction_for_spin(preset: Preset, spin_ratio: float) -> float:
    return clamp(spin_ratio * spin_ratio, 0.0, 1.15)


def steady_wall_force_n(
    preset: Preset,
    *,
    obstruction: float,
    spin_ratio: float,
    thrust_fraction: float,
    transverse_speed_mps: float,
    wall_force_factor: float = 1.0,
) -> float:
    obstruction = clamp(obstruction, 0.0, 1.0)
    wall_force_factor = clamp(wall_force_factor, 0.0, 1.0)
    if obstruction <= 1.0e-6 or wall_force_factor <= 1.0e-6:
        return 0.0
    speed_washout = 1.0 - clamp(transverse_speed_mps / 12.0, 0.0, 0.78)
    blockage = obstruction**1.18
    wall_cushion = blockage * spin_ratio * (0.35 + 0.65 * thrust_fraction) * speed_washout
    thrust_n = preset.max_rotor_thrust_n * thrust_fraction
    disk_pressure_force_n = max(thrust_n, preset.max_rotor_thrust_n * spin_ratio * spin_ratio * 0.70)
    force_n = (
        disk_pressure_force_n
        * clamp(0.110 + 0.450 * wall_cushion, 0.0, 0.45)
        * blockage
        * speed_washout
        * wall_force_factor
    )
    return min(force_n, 4.0)


def two_affected_vehicle_thrust_loss_fraction(preset: Preset, obstruction: float) -> float:
    multiplier = thrust_multiplier(obstruction)
    affected = min(2, preset.rotor_count)
    vehicle_multiplier = (preset.rotor_count - affected + affected * multiplier) / preset.rotor_count
    return 1.0 - vehicle_multiplier


def scenario_token(value: float | str) -> str:
    if isinstance(value, str):
        return value
    text = f"{value:.3g}".replace("-", "m").replace(".", "p")
    return text


def add_source_inventory(rows: list[dict[str, str]]) -> None:
    sources = [
        (
            "rotor_flow_obstruction_model",
            "Maps directional side-flow distances into obstruction intensity, ideal disk-overlap geometry, and dirty-air thrust multiplier.",
            ROTOR_FLOW_MODEL_SOURCE,
        ),
        (
            "drone_physics_wall_force",
            "Maps obstruction intensity, spin, thrust fraction, and transverse speed into lagged lateral wall attraction force.",
            DRONE_PHYSICS_SOURCE,
        ),
        (
            "fabric_rotor_side_flow_sampler",
            "Samples side-flow distances around each rotor disk in Minecraft worlds before constructing DroneEnvironment obstruction telemetry.",
            DRONE_ENTITY_SOURCE,
        ),
        (
            "offline_wall_skim_proxy",
            "Mirrors runtime side-flow geometry for the synthetic wall_skim regression trace.",
            OFFLINE_RECORDER_SOURCE,
        ),
    ]
    for name, description, source in sources:
        add_metric(
            rows,
            row_type="wall_obstruction_packet_source_inventory",
            name=name,
            metric="source_description",
            value=description,
            unit="text",
            source_file=source,
            evidence_role="wall_obstruction_source_inventory",
            note="Formula source for wall-obstruction response packet.",
        )


def add_clearance_matrix(rows: list[dict[str, str]]) -> None:
    metric_units = {
        "input_clearance_over_radius": "R",
        "input_clearance_m": "m",
        "runtime_side_flow_sample_max_distance_m": "m",
        "ideal_flat_wall_disk_blocked_fraction": "fraction",
        "runtime_obstruction_intensity": "fraction",
        "wall_force_geometry_factor": "fraction",
        "dirty_air_thrust_multiplier_per_affected_rotor": "multiplier",
        "two_affected_rotors_vehicle_thrust_loss_fraction": "fraction",
        "two_affected_rotors_vehicle_thrust_loss_percent": "percent",
        "hover_wall_force_per_affected_rotor_n": "N",
        "hover_two_affected_rotors_wall_force_over_weight": "weight fraction",
        "hover_wall_force_over_two_rotor_thrust_loss_weight_fraction": "ratio",
    }
    for preset in PRESETS:
        spin_ratio = preset.hover_spin_ratio
        thrust_fraction = preset.hover_thrust_fraction
        for clearance_over_r in CLEARANCE_OVER_R:
            obstruction = obstruction_from_single_wall(preset, clearance_over_r)
            disk_overlap = flat_wall_disk_blocked_fraction(clearance_over_r)
            flat_wall_disk_coverage = 2.0 * disk_overlap
            wall_force_factor = wall_force_geometry_factor(clearance_over_r, flat_wall_disk_coverage)
            thrust_loss = two_affected_vehicle_thrust_loss_fraction(preset, obstruction)
            wall_force = steady_wall_force_n(
                preset,
                obstruction=obstruction,
                spin_ratio=spin_ratio,
                thrust_fraction=thrust_fraction,
                transverse_speed_mps=0.0,
                wall_force_factor=wall_force_factor,
            )
            two_rotor_force_over_weight = 2.0 * wall_force / preset.weight_newtons
            response = {
                "input_clearance_over_radius": clearance_over_r,
                "input_clearance_m": clearance_over_r * preset.rotor_radius_m,
                "runtime_side_flow_sample_max_distance_m": side_flow_sample_max_distance_m(preset),
                "ideal_flat_wall_disk_blocked_fraction": disk_overlap,
                "runtime_obstruction_intensity": obstruction,
                "wall_force_geometry_factor": wall_force_factor,
                "dirty_air_thrust_multiplier_per_affected_rotor": thrust_multiplier(obstruction),
                "two_affected_rotors_vehicle_thrust_loss_fraction": thrust_loss,
                "two_affected_rotors_vehicle_thrust_loss_percent": thrust_loss * 100.0,
                "hover_wall_force_per_affected_rotor_n": wall_force,
                "hover_two_affected_rotors_wall_force_over_weight": two_rotor_force_over_weight,
                "hover_wall_force_over_two_rotor_thrust_loss_weight_fraction": (
                    two_rotor_force_over_weight / thrust_loss if thrust_loss > 1.0e-9 else 0.0
                ),
            }
            name = f"{preset.name}_clearance_{scenario_token(clearance_over_r)}R"
            for metric, value in response.items():
                add_metric(
                    rows,
                    row_type="wall_obstruction_packet_clearance_matrix",
                    name=name,
                    metric=metric,
                    value=value,
                    unit=metric_units[metric],
                    source_file=ROTOR_FLOW_MODEL_SOURCE,
                    source_url=SURFACE_OBSTRUCTION_REFERENCE,
                    evidence_role="runtime_wall_clearance_response_matrix",
                    note="Single flat sidewall response from current side-flow geometry; wall force uses hover spin and zero transverse speed.",
                )


def add_obstruction_speed_matrix(rows: list[dict[str, str]]) -> None:
    metric_units = {
        "input_obstruction_intensity": "fraction",
        "input_spin_ratio": "fraction",
        "input_thrust_fraction": "fraction",
        "input_transverse_speed_mps": "m/s",
        "dirty_air_thrust_multiplier_per_affected_rotor": "multiplier",
        "two_affected_rotors_vehicle_thrust_loss_fraction": "fraction",
        "wall_force_per_affected_rotor_n": "N",
        "two_affected_rotors_wall_force_over_weight": "weight fraction",
        "wall_force_speed_washout": "fraction",
    }
    for preset in PRESETS:
        for spin_label in SPIN_LABELS:
            spin_ratio = spin_ratio_for_label(preset, spin_label)
            thrust_fraction = thrust_fraction_for_spin(preset, spin_ratio)
            for obstruction in OBSTRUCTION_LEVELS:
                for speed in TRANSVERSE_SPEEDS_MPS:
                    force_n = steady_wall_force_n(
                        preset,
                        obstruction=obstruction,
                        spin_ratio=spin_ratio,
                        thrust_fraction=thrust_fraction,
                        transverse_speed_mps=speed,
                    )
                    speed_washout = 1.0 - clamp(speed / 12.0, 0.0, 0.78)
                    response = {
                        "input_obstruction_intensity": obstruction,
                        "input_spin_ratio": spin_ratio,
                        "input_thrust_fraction": thrust_fraction,
                        "input_transverse_speed_mps": speed,
                        "dirty_air_thrust_multiplier_per_affected_rotor": thrust_multiplier(obstruction),
                        "two_affected_rotors_vehicle_thrust_loss_fraction": two_affected_vehicle_thrust_loss_fraction(preset, obstruction),
                        "wall_force_per_affected_rotor_n": force_n,
                        "two_affected_rotors_wall_force_over_weight": 2.0 * force_n / preset.weight_newtons,
                        "wall_force_speed_washout": speed_washout,
                    }
                    name = (
                        f"{preset.name}_{spin_label}_obstruction_{scenario_token(obstruction)}"
                        f"_speed_{scenario_token(speed)}"
                    )
                    for metric, value in response.items():
                        add_metric(
                            rows,
                            row_type="wall_obstruction_packet_obstruction_speed_matrix",
                            name=name,
                            metric=metric,
                            value=value,
                            unit=metric_units[metric],
                            source_file=DRONE_PHYSICS_SOURCE,
                            source_url=ROTOR_FLOW_MODEL_SOURCE,
                            evidence_role="runtime_wall_force_and_dirty_air_split_matrix",
                            note="Current wall response split: thrust multiplier is speed-independent dirty-air loss, wall attraction force washes out with transverse speed.",
                        )


def add_summary(rows: list[dict[str, str]]) -> None:
    racing = next(preset for preset in PRESETS if preset.name == "racingQuad")
    obstruction_1r = obstruction_from_single_wall(racing, 1.0)
    obstruction_025r = obstruction_from_single_wall(racing, 0.25)
    wall_factor_1r = wall_force_geometry_factor(1.0, 2.0 * flat_wall_disk_blocked_fraction(1.0))
    wall_factor_025r = wall_force_geometry_factor(0.25, 2.0 * flat_wall_disk_blocked_fraction(0.25))
    full_loss = two_affected_vehicle_thrust_loss_fraction(racing, 1.0)
    force_1r = steady_wall_force_n(
        racing,
        obstruction=obstruction_1r,
        spin_ratio=racing.hover_spin_ratio,
        thrust_fraction=racing.hover_thrust_fraction,
        transverse_speed_mps=0.0,
        wall_force_factor=wall_factor_1r,
    )
    force_025r = steady_wall_force_n(
        racing,
        obstruction=obstruction_025r,
        spin_ratio=racing.hover_spin_ratio,
        thrust_fraction=racing.hover_thrust_fraction,
        transverse_speed_mps=0.0,
        wall_force_factor=wall_factor_025r,
    )
    force_full_hover = steady_wall_force_n(
        racing,
        obstruction=1.0,
        spin_ratio=racing.hover_spin_ratio,
        thrust_fraction=racing.hover_thrust_fraction,
        transverse_speed_mps=0.0,
    )
    force_full_fast = steady_wall_force_n(
        racing,
        obstruction=1.0,
        spin_ratio=racing.hover_spin_ratio,
        thrust_fraction=racing.hover_thrust_fraction,
        transverse_speed_mps=12.0,
    )
    summary = {
        "preset_count": (len(PRESETS), "count"),
        "clearance_matrix_scenario_count": (len(PRESETS) * len(CLEARANCE_OVER_R), "count"),
        "obstruction_speed_matrix_scenario_count": (
            len(PRESETS) * len(SPIN_LABELS) * len(OBSTRUCTION_LEVELS) * len(TRANSVERSE_SPEEDS_MPS),
            "count",
        ),
        "racingQuad_1R_ideal_disk_overlap": (flat_wall_disk_blocked_fraction(1.0), "fraction"),
        "racingQuad_1R_runtime_obstruction": (obstruction_1r, "fraction"),
        "racingQuad_1R_wall_force_geometry_factor": (wall_factor_1r, "fraction"),
        "racingQuad_1R_two_rotor_vehicle_thrust_loss": (
            two_affected_vehicle_thrust_loss_fraction(racing, obstruction_1r),
            "fraction",
        ),
        "racingQuad_1R_two_rotor_wall_force_over_weight": (
            2.0 * force_1r / racing.weight_newtons,
            "weight fraction",
        ),
        "racingQuad_0p25R_ideal_disk_overlap": (flat_wall_disk_blocked_fraction(0.25), "fraction"),
        "racingQuad_0p25R_runtime_obstruction": (obstruction_025r, "fraction"),
        "racingQuad_0p25R_wall_force_geometry_factor": (wall_factor_025r, "fraction"),
        "racingQuad_0p25R_two_rotor_vehicle_thrust_loss": (
            two_affected_vehicle_thrust_loss_fraction(racing, obstruction_025r),
            "fraction",
        ),
        "racingQuad_0p25R_two_rotor_wall_force_over_weight": (
            2.0 * force_025r / racing.weight_newtons,
            "weight fraction",
        ),
        "racingQuad_full_obstruction_two_rotor_vehicle_thrust_loss": (full_loss, "fraction"),
        "racingQuad_full_obstruction_hover_two_rotor_wall_force_over_weight": (
            2.0 * force_full_hover / racing.weight_newtons,
            "weight fraction",
        ),
        "racingQuad_full_obstruction_12mps_force_over_hover_force": (
            force_full_fast / force_full_hover if force_full_hover > 1.0e-9 else 0.0,
            "ratio",
        ),
    }
    for metric, (value, unit) in summary.items():
        add_metric(
            rows,
            row_type="wall_obstruction_packet_summary",
            name="summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=f"{ROTOR_FLOW_MODEL_SOURCE}; {DRONE_PHYSICS_SOURCE}",
            source_url=SURFACE_OBSTRUCTION_REFERENCE,
            evidence_role="wall_obstruction_response_packet_summary",
            note="Compact summary of current wall response split for docs and calibration queue.",
        )


def sync_summary(rows: list[dict[str, str]]) -> int:
    summary_rows = read_rows(SUMMARY) if SUMMARY.exists() else []
    synced_rows = [
        {
            "category": row["row_type"],
            "name": row["name"],
            "metric": row["metric"],
            "value": row["value"],
            "unit": row["unit"],
            "source": row.get("source_url", ""),
            "source_file": row.get("source_file", ""),
            "source_url": row.get("source_url", ""),
            "evidence_role": row.get("evidence_role", ""),
            "note": row.get("note", ""),
        }
        for row in rows
    ]
    prefix = "wall_obstruction_packet_"
    filtered = [row for row in summary_rows if not row.get("category", "").startswith(prefix)]
    insert_at = len(filtered)
    for index, row in enumerate(filtered):
        category = row.get("category", "")
        if category.startswith("surface_nearfield_"):
            insert_at = index + 1
    write_csv(SUMMARY, filtered[:insert_at] + synced_rows + filtered[insert_at:])
    return len(synced_rows)


def main() -> None:
    rows: list[dict[str, str]] = []
    add_source_inventory(rows)
    add_clearance_matrix(rows)
    add_obstruction_speed_matrix(rows)
    add_summary(rows)
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {len(rows)} rows to {repo_path(OUTPUT)}")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
