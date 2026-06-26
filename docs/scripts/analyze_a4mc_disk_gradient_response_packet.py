#!/usr/bin/env python3
"""Generate an A4MC rotor-disk wind-gradient response packet.

Outputs:
  docs/data/a4mc_disk_gradient_response_packet.csv

Aerodynamics4MC can expose local L2 mean-wind and pressure differences across
a rotor disk. FPV Dronecraft converts those differences into a bounded
`rotorDiskWindGradientBodyMetersPerSecond` signal, then the core model maps that
signal into thrust loss, aerodynamic load, vibration, dynamic-stall onset, and
flapping tilt. This packet mirrors the current Java formulas so blackbox
block-edge or tunnel-mouth traces can fit those coefficients later.
"""

from __future__ import annotations

import csv
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
OUTPUT = DATA / "a4mc_disk_gradient_response_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

DRONE_PHYSICS_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java"
DRONE_ENTITY_SOURCE = "fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java"
A4MC_GAMEPLAY_SAMPLE_SOURCE = "https://github.com/MozillaFiredoge/Aerodynamics4MC-Core/blob/main/api/src/main/java/com/aerodynamics4mc/api/GameplayWindSample.java"
A4MC_MINECRAFT_API_SOURCE = "https://github.com/MozillaFiredoge/Aerodynamics4MC-Core/blob/main/src/main/java/com/aerodynamics4mc/api/minecraft/AeroMinecraftWindApi.java"

G = 9.80665
MAX_DISK_GRADIENT_MPS = 12.0
MAX_GRADIENT_TILT_RADIANS = math.radians(4.5)
MAX_GRADIENT_THRUST_LOSS = 0.045
OFFLINE_WALL_SKIM_PRESSURE_GRADIENT_MPS = 0.33
OFFLINE_WALL_SKIM_SOURCE_QUALITY = 0.86

RAW_GRADIENT_SPEEDS_MPS = [0.0, 0.33, 1.0, 2.4, 6.0, 12.0]
SOURCE_QUALITY_LEVELS = [0.0, 0.50, 0.86, 1.0]
SPIN_RATIO_LABELS = ["idle", "hover", "cruise", "max"]
SPIN_RATIO_FALLBACKS = {
    "idle": 0.12,
    "cruise": 0.65,
    "max": 1.0,
}


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


PRESETS = [
    Preset("racingQuad", 1.1, 4, 0.0635, 13.5, 1.45e-6),
    Preset("apDrone", 0.6284, 4, 5.1 * 0.0254 * 0.5, 13.5, 1.3918976015517363e-6),
    Preset("cinewhoop", 0.95, 4, 0.038, 8.0, 1.15e-5),
    Preset("heavyLift", 4.5, 4, 0.127, 38.0, 4.5e-5),
]


def clamp(value: float, lo: float, hi: float) -> float:
    return min(hi, max(lo, value))


def smooth_step(edge0: float, edge1: float, value: float) -> float:
    if edge1 <= edge0:
        return 0.0 if value < edge0 else 1.0
    x = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0)
    return x * x * (3.0 - 2.0 * x)


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def value_text(value: object) -> str:
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, str):
        return value
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        if not math.isfinite(value):
            return ""
        return f"{value:.12g}"
    return str(value)


def scenario_token(value: object) -> str:
    return value_text(value).replace("-", "neg").replace(".", "p")


def spin_ratio_for_label(preset: Preset, label: str) -> float:
    if label == "hover":
        return preset.hover_spin_ratio
    return SPIN_RATIO_FALLBACKS[label]


def core_disk_gradient_response(
    preset: Preset,
    raw_gradient_mps: float,
    source_quality: float,
    spin_ratio: float,
) -> dict[str, float]:
    source_quality = clamp(source_quality, 0.0, 1.0)
    raw_gradient_mps = clamp(raw_gradient_mps, 0.0, MAX_DISK_GRADIENT_MPS)
    adopted_gradient = clamp(raw_gradient_mps * source_quality, 0.0, MAX_DISK_GRADIENT_MPS)
    spin_ratio = clamp(spin_ratio, 0.0, 1.0)
    max_omega = preset.max_omega_rad_s
    tip_speed = max_omega * spin_ratio * preset.rotor_radius_m
    thrust_fraction = clamp(spin_ratio * spin_ratio, 0.0, 1.0)
    thrust_n = preset.max_rotor_thrust_n * thrust_fraction

    if spin_ratio <= 0.06:
        thrust_scale = 1.0
    else:
        thrust_ratio = clamp(adopted_gradient / max(1.0, tip_speed * 0.14), 0.0, 1.0)
        loss = MAX_GRADIENT_THRUST_LOSS * smooth_step(0.04, 0.58, thrust_ratio) * smooth_step(0.10, 0.55, spin_ratio)
        thrust_scale = clamp(1.0 - loss, 1.0 - MAX_GRADIENT_THRUST_LOSS, 1.0)

    load_ratio = clamp(adopted_gradient / max(1.0, tip_speed * 0.16), 0.0, 1.0)
    load_factor = clamp(0.18 * (load_ratio**0.85) * smooth_step(0.10, 0.55, spin_ratio), 0.0, 0.18)

    vibration_ratio = clamp(adopted_gradient / max(1.0, tip_speed * 0.12), 0.0, 1.0)
    vibration = clamp(0.18 * (vibration_ratio**0.80) * smooth_step(0.08, 0.50, spin_ratio), 0.0, 0.18)

    if spin_ratio <= 0.08:
        stall_intensity = 0.0
    else:
        stall_ratio = clamp(adopted_gradient / max(1.0, tip_speed * 0.10), 0.0, 1.0)
        stall_intensity = clamp(0.14 * smooth_step(0.10, 0.48, stall_ratio) * smooth_step(0.12, 0.50, spin_ratio), 0.0, 0.14)

    if adopted_gradient <= 1.0e-6 or thrust_n <= 1.0e-6 or spin_ratio <= 0.06:
        tilt_radians = 0.0
    else:
        tilt_ratio = clamp(adopted_gradient / max(1.0, tip_speed * 0.12), 0.0, 1.0)
        tilt_radians = (
            MAX_GRADIENT_TILT_RADIANS
            * smooth_step(0.03, 0.42, tilt_ratio)
            * smooth_step(0.10, 0.55, spin_ratio)
            * clamp(0.55 + 0.45 * math.sqrt(thrust_fraction), 0.0, 1.0)
        )

    lateral_flapping_force = thrust_n * tilt_radians
    vertical_force_scale = thrust_scale * math.sqrt(max(0.0, 1.0 - tilt_radians * tilt_radians))
    return {
        "input_raw_a4mc_disk_gradient_mps": raw_gradient_mps,
        "input_source_quality_factor": source_quality,
        "input_spin_ratio": spin_ratio,
        "core_adopted_disk_gradient_mps": adopted_gradient,
        "core_tip_speed_mps": tip_speed,
        "core_gradient_over_tip_speed": adopted_gradient / max(1.0e-9, tip_speed),
        "core_steady_thrust_fraction_proxy": thrust_fraction,
        "core_steady_thrust_newtons_proxy": thrust_n,
        "core_disk_gradient_thrust_scale": thrust_scale,
        "core_disk_gradient_thrust_loss_fraction": 1.0 - thrust_scale,
        "core_disk_gradient_load_factor": load_factor,
        "core_disk_gradient_vibration": vibration,
        "core_disk_gradient_stall_intensity": stall_intensity,
        "core_disk_gradient_flapping_tilt_deg": math.degrees(tilt_radians),
        "core_disk_gradient_lateral_flapping_force_proxy_n": lateral_flapping_force,
        "core_disk_gradient_vertical_force_scale_proxy": vertical_force_scale,
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
            "a4mc_gameplay_sample",
            "GameplayWindSample exposes mean/effective/gust velocity, pressure, turbulence, shear, shelter, confidence, and ABL diagnostics used by FPV Dronecraft.",
            A4MC_GAMEPLAY_SAMPLE_SOURCE,
        ),
        (
            "a4mc_minecraft_api",
            "AeroMinecraftWindApi supplies runtime gameplay samples without adding Aerodynamics4MC classes to drone-sim-core.",
            A4MC_MINECRAFT_API_SOURCE,
        ),
        (
            "fpv_drone_entity_disk_sampler",
            "DroneEntity samples rotor-center and disk-edge A4MC flow, applies per-sample quality during wind and pressure-gradient blending, then passes adopted disk gradients into the core.",
            DRONE_ENTITY_SOURCE,
        ),
        (
            "fpv_core_disk_gradient_response",
            "DronePhysics maps rotor disk wind gradient into thrust loss, load, vibration, stall, and flapping proxies.",
            DRONE_PHYSICS_SOURCE,
        ),
    ]
    for name, description, source in sources:
        add_metric(
            rows,
            row_type="a4mc_disk_gradient_packet_source_inventory",
            name=name,
            metric="source_description",
            value=description,
            unit="text",
            source_file=source if source.endswith(".java") and not source.startswith("http") else "",
            source_url=source if source.startswith("http") else "",
            evidence_role="disk_gradient_source_inventory",
            note="Reference surface for A4MC disk-gradient calibration packet.",
        )


def add_response_matrix(rows: list[dict[str, str]]) -> None:
    metric_units = {
        "input_raw_a4mc_disk_gradient_mps": "m/s",
        "input_source_quality_factor": "fraction",
        "input_spin_ratio": "fraction",
        "core_adopted_disk_gradient_mps": "m/s",
        "core_tip_speed_mps": "m/s",
        "core_gradient_over_tip_speed": "ratio",
        "core_steady_thrust_fraction_proxy": "fraction",
        "core_steady_thrust_newtons_proxy": "N",
        "core_disk_gradient_thrust_scale": "multiplier",
        "core_disk_gradient_thrust_loss_fraction": "fraction",
        "core_disk_gradient_load_factor": "fraction",
        "core_disk_gradient_vibration": "fraction",
        "core_disk_gradient_stall_intensity": "fraction",
        "core_disk_gradient_flapping_tilt_deg": "degrees",
        "core_disk_gradient_lateral_flapping_force_proxy_n": "N",
        "core_disk_gradient_vertical_force_scale_proxy": "multiplier",
    }
    for preset in PRESETS:
        for spin_label in SPIN_RATIO_LABELS:
            spin_ratio = spin_ratio_for_label(preset, spin_label)
            for source_quality in SOURCE_QUALITY_LEVELS:
                for raw_gradient in RAW_GRADIENT_SPEEDS_MPS:
                    response = core_disk_gradient_response(preset, raw_gradient, source_quality, spin_ratio)
                    name = (
                        f"{preset.name}_{spin_label}_raw_{scenario_token(raw_gradient)}"
                        f"_quality_{scenario_token(source_quality)}"
                    )
                    for metric, value in response.items():
                        add_metric(
                            rows,
                            row_type="a4mc_disk_gradient_packet_response_matrix",
                            name=name,
                            metric=metric,
                            value=value,
                            unit=metric_units[metric],
                            source_file=DRONE_PHYSICS_SOURCE,
                            source_url=f"{DRONE_ENTITY_SOURCE}; {A4MC_GAMEPLAY_SAMPLE_SOURCE}",
                            evidence_role="current_core_disk_gradient_response_matrix",
                            note="Synthetic A4MC disk-gradient response matrix mirroring current core formulas before blackbox fitting.",
                        )


def add_wall_skim_rows(rows: list[dict[str, str]]) -> None:
    metric_units = {
        "input_raw_a4mc_disk_gradient_mps": "m/s",
        "input_source_quality_factor": "fraction",
        "input_spin_ratio": "fraction",
        "core_adopted_disk_gradient_mps": "m/s",
        "core_tip_speed_mps": "m/s",
        "core_gradient_over_tip_speed": "ratio",
        "core_steady_thrust_fraction_proxy": "fraction",
        "core_steady_thrust_newtons_proxy": "N",
        "core_disk_gradient_thrust_scale": "multiplier",
        "core_disk_gradient_thrust_loss_fraction": "fraction",
        "core_disk_gradient_load_factor": "fraction",
        "core_disk_gradient_vibration": "fraction",
        "core_disk_gradient_stall_intensity": "fraction",
        "core_disk_gradient_flapping_tilt_deg": "degrees",
        "core_disk_gradient_lateral_flapping_force_proxy_n": "N",
        "core_disk_gradient_vertical_force_scale_proxy": "multiplier",
    }
    for preset in PRESETS:
        response = core_disk_gradient_response(
            preset,
            OFFLINE_WALL_SKIM_PRESSURE_GRADIENT_MPS,
            OFFLINE_WALL_SKIM_SOURCE_QUALITY,
            preset.hover_spin_ratio,
        )
        name = f"{preset.name}_offline_wall_skim_hover_pressure_gradient"
        for metric, value in response.items():
            add_metric(
                rows,
                row_type="a4mc_disk_gradient_packet_wall_skim_reference",
                name=name,
                metric=metric,
                value=value,
                unit=metric_units[metric],
                source_file=DRONE_PHYSICS_SOURCE,
                source_url=f"{DRONE_ENTITY_SOURCE}; docs/data/a4mc_local_voxel_coupling_packet.csv",
                evidence_role="offline_a4mc_wall_skim_disk_gradient_reference",
                note="Direct core disk-gradient sensitivity probe; the Fabric local-voxel bridge output is audited separately before this response surface.",
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
    scenario_count = len(PRESETS) * len(SPIN_RATIO_LABELS) * len(SOURCE_QUALITY_LEVELS) * len(RAW_GRADIENT_SPEEDS_MPS)
    rq_wall_name = "racingQuad_offline_wall_skim_hover_pressure_gradient"
    rq_max_name = "racingQuad_max_raw_12_quality_1"
    rq_quality_zero_name = "racingQuad_hover_raw_12_quality_0"
    racing_quad = next(preset for preset in PRESETS if preset.name == "racingQuad")
    rq_hover_tip_speed = racing_quad.max_omega_rad_s * racing_quad.hover_spin_ratio * racing_quad.rotor_radius_m
    rq_hover_tilt_start_raw_gradient = (0.03 * max(1.0, rq_hover_tip_speed * 0.12)) / OFFLINE_WALL_SKIM_SOURCE_QUALITY
    rq_hover_thrust_loss_start_raw_gradient = (0.04 * max(1.0, rq_hover_tip_speed * 0.14)) / OFFLINE_WALL_SKIM_SOURCE_QUALITY
    summary = {
        "matrix_scenario_count": (scenario_count, "count"),
        "wall_skim_reference_raw_pressure_gradient_mps": (OFFLINE_WALL_SKIM_PRESSURE_GRADIENT_MPS, "m/s"),
        "wall_skim_reference_source_quality": (OFFLINE_WALL_SKIM_SOURCE_QUALITY, "fraction"),
        "racingQuad_hover_tilt_start_raw_gradient_at_wall_skim_quality_mps": (
            rq_hover_tilt_start_raw_gradient,
            "m/s",
        ),
        "racingQuad_hover_thrust_loss_start_raw_gradient_at_wall_skim_quality_mps": (
            rq_hover_thrust_loss_start_raw_gradient,
            "m/s",
        ),
        "racingQuad_wall_skim_adopted_gradient_mps": (
            find_metric(rows, "a4mc_disk_gradient_packet_wall_skim_reference", rq_wall_name, "core_adopted_disk_gradient_mps"),
            "m/s",
        ),
        "racingQuad_wall_skim_flapping_tilt_deg": (
            find_metric(rows, "a4mc_disk_gradient_packet_wall_skim_reference", rq_wall_name, "core_disk_gradient_flapping_tilt_deg"),
            "degrees",
        ),
        "racingQuad_wall_skim_thrust_loss_fraction": (
            find_metric(rows, "a4mc_disk_gradient_packet_wall_skim_reference", rq_wall_name, "core_disk_gradient_thrust_loss_fraction"),
            "fraction",
        ),
        "racingQuad_wall_skim_load_factor": (
            find_metric(rows, "a4mc_disk_gradient_packet_wall_skim_reference", rq_wall_name, "core_disk_gradient_load_factor"),
            "fraction",
        ),
        "racingQuad_wall_skim_vibration": (
            find_metric(rows, "a4mc_disk_gradient_packet_wall_skim_reference", rq_wall_name, "core_disk_gradient_vibration"),
            "fraction",
        ),
        "racingQuad_max_gradient_flapping_tilt_deg": (
            find_metric(rows, "a4mc_disk_gradient_packet_response_matrix", rq_max_name, "core_disk_gradient_flapping_tilt_deg"),
            "degrees",
        ),
        "racingQuad_max_gradient_thrust_loss_fraction": (
            find_metric(rows, "a4mc_disk_gradient_packet_response_matrix", rq_max_name, "core_disk_gradient_thrust_loss_fraction"),
            "fraction",
        ),
        "quality_zero_adopted_gradient_mps": (
            find_metric(rows, "a4mc_disk_gradient_packet_response_matrix", rq_quality_zero_name, "core_adopted_disk_gradient_mps"),
            "m/s",
        ),
        "quality_zero_thrust_loss_fraction": (
            find_metric(rows, "a4mc_disk_gradient_packet_response_matrix", rq_quality_zero_name, "core_disk_gradient_thrust_loss_fraction"),
            "fraction",
        ),
    }
    for metric, (value, unit) in summary.items():
        add_metric(
            rows,
            row_type="a4mc_disk_gradient_packet_summary",
            name="disk_gradient_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=repo_path(OUTPUT),
            evidence_role="current_a4mc_disk_gradient_summary",
            note="Compact response summary for A4MC rotor disk-gradient tuning.",
        )

    add_metric(
        rows,
        row_type="a4mc_disk_gradient_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Synthetic packet mirrors current Java response formulas; use it as a coefficient audit and "
            "blackbox fitting surface, not as independent CFD validation."
        ),
        unit="text",
        source_file=repo_path(OUTPUT),
        evidence_role="method_caveat",
        note="Live block-edge or tunnel-mouth traces are still needed before changing runtime constants.",
    )


def build_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    add_source_inventory(rows)
    add_response_matrix(rows)
    add_wall_skim_rows(rows)
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
    filtered = [
        row for row in summary_rows
        if not row.get("category", "").startswith("a4mc_disk_gradient_packet_")
    ]
    insert_at = max(
        (
            index + 1
            for index, row in enumerate(filtered)
            if row.get("category", "").startswith("a4mc_source_quality_packet_")
        ),
        default=len(filtered),
    )
    write_csv(SUMMARY, filtered[:insert_at] + synced_rows + filtered[insert_at:])
    return len(synced_rows)


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
