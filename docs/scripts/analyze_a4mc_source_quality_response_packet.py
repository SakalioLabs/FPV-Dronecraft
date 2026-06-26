"""Build an A4MC source-quality response calibration packet.

Outputs:
  docs/data/a4mc_source_quality_response_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category
  a4mc_source_quality_packet_*

This packet mirrors the current A4MC trust/confidence/freshness gate used by
DroneEnvironment, DronePhysics, and the fabric bridge. It keeps the next
wall-skim/tunnel-mouth tuning step auditable by showing how stale or low
confidence A4MC source diagnostics fade before they drive source gust,
terrain-shear, local ventilation, and duplicated local-obstacle attenuation.
It also tracks raw A4MC source turbulence as a separate input so the generated
packet mirrors the blackbox `wind_source_turbulence` telemetry and the core
quality-gated natural turbulence floor. A separate ABL matrix mirrors the core
Dryden intensity and time-scale modifiers for stable/unstable mixed layers.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
OUTPUT = DATA / "a4mc_source_quality_response_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

DRONE_ENVIRONMENT_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneEnvironment.java"
DRONE_PHYSICS_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java"
DRONE_ENTITY_SOURCE = "fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java"
A4MC_GAMEPLAY_SAMPLE_SOURCE = "_reference/Aerodynamics4MC-Core/api/src/main/java/com/aerodynamics4mc/api/GameplayWindSample.java"

CONFIDENCE_LEVELS = [1.0, 0.86, 0.5]
FRESHNESS_AGES_TICKS = [0, 40, 100, 160]
SHELTER_FACTORS = [0.0, 0.74]
SHEAR_LEVELS_PER_BLOCK = [0.0, 0.58, 1.35]
SOURCE_TURBULENCE_LEVELS = [0.0, 0.24, 0.65]
ABL_STABILITY_LEVELS = [-0.90, 0.0, 0.90]
ABL_MIXING_LEVELS = [0.0, 0.64, 0.90]

SOURCE_FULL_TRUST_AGE_TICKS = 40
SOURCE_ZERO_TRUST_AGE_TICKS = 160
SOURCE_GUST_TURBULENCE_GAIN_PER_MPS = 0.065
MAX_SOURCE_GUST_TURBULENCE_BOOST = 0.26
REFERENCE_MEAN_WIND_MPS = 7.0
REFERENCE_SOURCE_GUST_MPS = 4.0
REFERENCE_SOURCE_GUST_VECTOR_Y_MPS = 3.0
REFERENCE_UPDRAFT_MPS = 0.18
REFERENCE_DIRTY_AIR = 0.0
REFERENCE_ABL_TURBULENCE_INTENSITY = 0.55

LOCAL_VOXEL_BASE_COVERAGE = 0.32
LOCAL_VOXEL_SHELTER_COVERAGE = 0.48
LOCAL_VOXEL_MIN_OBSTRUCTION_RESIDUAL = 0.28


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
        return f"{value:.12g}"
    return str(value)


def clamp(value: float, lower: float, upper: float) -> float:
    return min(max(value, lower), upper)


def smooth_step(edge0: float, edge1: float, value: float) -> float:
    if edge1 <= edge0:
        return 1.0 if value >= edge1 else 0.0
    t = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


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


def a4mc_source_gust_y_mps(quality: float) -> float:
    source_gust_vector_y = REFERENCE_SOURCE_GUST_VECTOR_Y_MPS * quality
    source_gust_vector_speed = abs(source_gust_vector_y)
    source_gust_speed = max(REFERENCE_SOURCE_GUST_MPS * quality, source_gust_vector_speed)
    if source_gust_vector_speed <= 1.0e-6 or source_gust_speed <= 1.0e-6:
        return 0.0
    wind_gate = smooth_step(0.3, 5.0, max(REFERENCE_MEAN_WIND_MPS, source_gust_speed))
    dirty_gain = clamp(0.72 + 0.10 * REFERENCE_DIRTY_AIR, 0.72, 0.94)
    vector_scale = clamp(source_gust_speed / max(source_gust_vector_speed, 1.0e-6), 0.0, 1.0)
    return source_gust_vector_y * 0.22 * wind_gate * dirty_gain * vector_scale


def terrain_shear_response(shear: float, shelter: float, quality: float) -> dict[str, float]:
    adopted_shear = clamp(shear * quality, 0.0, 5.0)
    adopted_shelter = clamp(shelter * quality, 0.0, 1.0)
    adopted_updraft = clamp(REFERENCE_UPDRAFT_MPS * quality, -12.0, 12.0)
    shelter_wind_gate = smooth_step(0.8, 7.0, REFERENCE_MEAN_WIND_MPS)
    if adopted_shear <= 1.0e-6 and abs(adopted_updraft) <= 1.0e-6 and (
        adopted_shelter <= 1.0e-6 or shelter_wind_gate <= 1.0e-6
    ):
        terrain_signal = 0.0
    else:
        shelter_signal = adopted_shelter * shelter_wind_gate * (
            0.20 + 0.12 * smooth_step(0.25, 2.0, adopted_shear)
        )
        terrain_signal = clamp(
            0.42
            * adopted_shear
            * (0.50 + 0.50 * smooth_step(0.6, 7.0, REFERENCE_MEAN_WIND_MPS))
            + 0.10 * abs(adopted_updraft)
            + shelter_signal,
            0.0,
            2.40,
        )
    dirty_gain = clamp(
        0.72 + 0.18 * REFERENCE_DIRTY_AIR + 0.10 * adopted_shelter * shelter_wind_gate,
        0.72,
        1.10,
    )
    vertical_gain = 0.22 + 0.16 * smooth_step(0.25, 5.0, abs(adopted_updraft))
    along_peak = terrain_signal * 0.35 * dirty_gain
    cross_peak = terrain_signal * 0.55 * dirty_gain
    vertical_peak = terrain_signal * vertical_gain * dirty_gain
    return {
        "adopted_shear_per_block": adopted_shear,
        "adopted_shelter_factor": adopted_shelter,
        "adopted_updraft_mps": adopted_updraft,
        "terrain_signal": terrain_signal,
        "terrain_shear_along_peak_mps": along_peak,
        "terrain_shear_cross_peak_mps": cross_peak,
        "terrain_shear_vertical_peak_mps": vertical_peak,
        "terrain_shear_vector_peak_proxy_mps": math.sqrt(
            along_peak * along_peak + cross_peak * cross_peak + vertical_peak * vertical_peak
        ),
    }


def natural_turbulence_response(source_turbulence: float, shear: float, shelter: float, quality: float) -> dict[str, float]:
    adopted_source_turbulence = clamp(source_turbulence, 0.0, 1.5) * quality
    shear_boost = quality * clamp(shear * 0.45, 0.0, 0.35)
    shelter_boost = quality * clamp(shelter * 0.20, 0.0, 0.20)
    updraft_boost = quality * clamp(abs(REFERENCE_UPDRAFT_MPS) * 0.025, 0.0, 0.18)
    source_gust_boost = quality * clamp(
        REFERENCE_SOURCE_GUST_MPS * SOURCE_GUST_TURBULENCE_GAIN_PER_MPS,
        0.0,
        MAX_SOURCE_GUST_TURBULENCE_BOOST,
    )
    return {
        "core_adopted_source_turbulence_intensity": adopted_source_turbulence,
        "core_natural_turbulence_intensity_proxy": clamp(
            max(REFERENCE_DIRTY_AIR, adopted_source_turbulence)
            + shear_boost
            + shelter_boost
            + updraft_boost
            + source_gust_boost,
            0.0,
            1.5,
        ),
    }


def dryden_abl_response(base_turbulence: float, abl_stability: float, abl_mixing: float, quality: float) -> dict[str, float]:
    adopted_stability = clamp(abl_stability * quality, -1.0, 1.0)
    adopted_mixing = clamp(abl_mixing * quality, 0.0, 1.0)
    safe_base_turbulence = clamp(base_turbulence, 0.0, 1.8)
    if adopted_mixing <= 1.0e-6 and abs(adopted_stability) <= 1.0e-6:
        return {
            "core_adopted_abl_stability": adopted_stability,
            "core_adopted_abl_mixing_strength": adopted_mixing,
            "core_dryden_intensity_proxy": safe_base_turbulence,
            "core_dryden_horizontal_time_scale_multiplier": 1.0,
            "core_dryden_vertical_time_scale_multiplier": 1.0,
        }

    unstable = max(0.0, adopted_stability)
    stable = max(0.0, -adopted_stability)
    mixing_multiplier = (
        1.0
        + 0.18 * adopted_mixing
        + 0.42 * unstable * adopted_mixing
        - 0.34 * stable * (0.85 + 0.15 * (1.0 - adopted_mixing))
    )
    convective_floor = (
        0.22
        * unstable
        * adopted_mixing
        * smooth_step(1.0, 8.0, REFERENCE_MEAN_WIND_MPS)
    )
    mixed_unstable = unstable * adopted_mixing
    stable_persistence = stable * (0.75 + 0.25 * (1.0 - adopted_mixing))
    return {
        "core_adopted_abl_stability": adopted_stability,
        "core_adopted_abl_mixing_strength": adopted_mixing,
        "core_dryden_intensity_proxy": clamp(
            safe_base_turbulence * clamp(mixing_multiplier, 0.55, 1.70) + convective_floor,
            0.0,
            1.8,
        ),
        "core_dryden_horizontal_time_scale_multiplier": clamp(
            1.0 - 0.16 * adopted_mixing - 0.24 * mixed_unstable + 0.45 * stable_persistence,
            0.55,
            1.55,
        ),
        "core_dryden_vertical_time_scale_multiplier": clamp(
            1.0 - 0.25 * adopted_mixing - 0.30 * mixed_unstable + 0.70 * stable_persistence,
            0.45,
            1.80,
        ),
    }


def ventilation_efficiency(shelter: float, quality: float) -> float:
    adopted_shelter = clamp(shelter * quality, 0.0, 1.0)
    return clamp(1.0 - 0.20 * adopted_shelter, 0.80, 1.0)


def local_obstacle_residual(shelter: float, quality: float) -> float:
    coverage = quality * (LOCAL_VOXEL_BASE_COVERAGE + LOCAL_VOXEL_SHELTER_COVERAGE * clamp(shelter, 0.0, 1.0))
    return clamp(1.0 - coverage, LOCAL_VOXEL_MIN_OBSTRUCTION_RESIDUAL, 1.0)


def scenario_name(
    trusted: bool,
    confidence: float,
    age_ticks: int,
    shelter: float,
    shear: float,
    source_turbulence: float,
) -> str:
    trust = "trusted" if trusted else "untrusted"
    return (
        f"{trust}_conf_{confidence:.2f}_age_{age_ticks}"
        f"_shelter_{shelter:.2f}_shear_{shear:.2f}_turb_{source_turbulence:.2f}"
    ).replace(".", "p")


def abl_scenario_name(
    trusted: bool,
    confidence: float,
    age_ticks: int,
    abl_stability: float,
    abl_mixing: float,
) -> str:
    trust = "trusted" if trusted else "untrusted"
    return (
        f"{trust}_conf_{confidence:.2f}_age_{age_ticks}"
        f"_abl_{abl_stability:.2f}_mix_{abl_mixing:.2f}"
    ).replace(".", "p").replace("-", "neg")


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
            "DroneEnvironment_source_quality_gate",
            DRONE_ENVIRONMENT_SOURCE,
            "current_core_quality_gate",
            "Defines the trust/confidence/freshness source-quality factor used by core physics.",
        ),
        (
            "DronePhysics_a4mc_transients",
            DRONE_PHYSICS_SOURCE,
            "current_core_transient_response",
            "Consumes quality-gated A4MC source gust, terrain shear, ABL, and local-voxel ventilation diagnostics.",
        ),
        (
            "DroneEntity_a4mc_boundary",
            DRONE_ENTITY_SOURCE,
            "current_fabric_boundary",
            "Passes raw A4MC transient diagnostics while keeping pressure, temperature, and humidity preweighted as environment scalars.",
        ),
        (
            "A4MC_GameplayWindSample_API",
            A4MC_GAMEPLAY_SAMPLE_SOURCE,
            "reference_mod_api",
            "Reference API exposes trusted gameplay wind samples with confidence, shelter, shear, pressure, turbulence, and freshness inputs.",
        ),
    ]
    for name, source_file, role, note in sources:
        add_metric(
            rows,
            row_type="a4mc_source_quality_packet_source_inventory",
            name=name,
            metric="source_file",
            value=source_file,
            unit="path",
            source_file=source_file,
            evidence_role=role,
            note=note,
        )


def add_response_matrix(rows: list[dict[str, str]]) -> None:
    scenarios = [
        (True, confidence, age, shelter, shear, source_turbulence)
        for confidence in CONFIDENCE_LEVELS
        for age in FRESHNESS_AGES_TICKS
        for shelter in SHELTER_FACTORS
        for shear in SHEAR_LEVELS_PER_BLOCK
        for source_turbulence in SOURCE_TURBULENCE_LEVELS
    ]
    scenarios.append((False, 1.0, 0, 0.74, 1.35, 0.65))
    for trusted, confidence, age, shelter, shear, source_turbulence in scenarios:
        quality = source_quality(trusted, confidence, age)
        terrain = terrain_shear_response(shear, shelter, quality)
        turbulence = natural_turbulence_response(source_turbulence, shear, shelter, quality)
        metrics = {
            "input_trusted_for_gameplay": (trusted, "bool"),
            "input_confidence": (confidence, "fraction"),
            "input_freshness_age_ticks": (age, "ticks"),
            "input_shelter_factor": (shelter, "fraction"),
            "input_shear_per_block": (shear, "1/block"),
            "input_source_turbulence_intensity": (source_turbulence, "fraction"),
            "source_freshness_factor": (source_freshness_factor(age), "fraction"),
            "source_quality_factor": (quality, "fraction"),
            "core_source_gust_y_peak_mps": (a4mc_source_gust_y_mps(quality), "m/s"),
            "core_adopted_source_turbulence_intensity": (
                turbulence["core_adopted_source_turbulence_intensity"],
                "fraction",
            ),
            "core_natural_turbulence_intensity_proxy": (
                turbulence["core_natural_turbulence_intensity_proxy"],
                "fraction",
            ),
            "core_local_voxel_ventilation_efficiency": (ventilation_efficiency(shelter, quality), "multiplier"),
            "core_local_obstacle_residual_factor": (local_obstacle_residual(shelter, quality), "multiplier"),
            "core_adopted_shear_per_block": (terrain["adopted_shear_per_block"], "1/block"),
            "core_adopted_shelter_factor": (terrain["adopted_shelter_factor"], "fraction"),
            "core_adopted_updraft_mps": (terrain["adopted_updraft_mps"], "m/s"),
            "core_terrain_signal": (terrain["terrain_signal"], "m/s"),
            "core_terrain_shear_along_peak_mps": (terrain["terrain_shear_along_peak_mps"], "m/s"),
            "core_terrain_shear_cross_peak_mps": (terrain["terrain_shear_cross_peak_mps"], "m/s"),
            "core_terrain_shear_vertical_peak_mps": (terrain["terrain_shear_vertical_peak_mps"], "m/s"),
            "core_terrain_shear_vector_peak_proxy_mps": (terrain["terrain_shear_vector_peak_proxy_mps"], "m/s"),
        }
        name = scenario_name(trusted, confidence, age, shelter, shear, source_turbulence)
        for metric, (value, unit) in metrics.items():
            add_metric(
                rows,
                row_type="a4mc_source_quality_packet_response_matrix",
                name=name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=DRONE_PHYSICS_SOURCE,
                source_url=f"{DRONE_ENVIRONMENT_SOURCE}; {DRONE_ENTITY_SOURCE}; {A4MC_GAMEPLAY_SAMPLE_SOURCE}",
                evidence_role="current_a4mc_quality_response_matrix",
                note="Synthetic A4MC wall-skim/tunnel-mouth response matrix for confidence, freshness, shelter, shear, and source-turbulence tuning.",
            )


def add_abl_response_matrix(rows: list[dict[str, str]]) -> None:
    scenarios = [
        (True, confidence, age, abl_stability, abl_mixing)
        for confidence in CONFIDENCE_LEVELS
        for age in FRESHNESS_AGES_TICKS
        for abl_stability in ABL_STABILITY_LEVELS
        for abl_mixing in ABL_MIXING_LEVELS
    ]
    scenarios.append((False, 1.0, 0, 0.90, 0.90))
    for trusted, confidence, age, abl_stability, abl_mixing in scenarios:
        quality = source_quality(trusted, confidence, age)
        dryden = dryden_abl_response(
            REFERENCE_ABL_TURBULENCE_INTENSITY,
            abl_stability,
            abl_mixing,
            quality,
        )
        metrics = {
            "input_trusted_for_gameplay": (trusted, "bool"),
            "input_confidence": (confidence, "fraction"),
            "input_freshness_age_ticks": (age, "ticks"),
            "input_abl_stability": (abl_stability, "fraction"),
            "input_abl_mixing_strength": (abl_mixing, "fraction"),
            "input_base_turbulence_intensity": (REFERENCE_ABL_TURBULENCE_INTENSITY, "fraction"),
            "source_freshness_factor": (source_freshness_factor(age), "fraction"),
            "source_quality_factor": (quality, "fraction"),
            "core_adopted_abl_stability": (dryden["core_adopted_abl_stability"], "fraction"),
            "core_adopted_abl_mixing_strength": (dryden["core_adopted_abl_mixing_strength"], "fraction"),
            "core_dryden_intensity_proxy": (dryden["core_dryden_intensity_proxy"], "fraction"),
            "core_dryden_horizontal_time_scale_multiplier": (
                dryden["core_dryden_horizontal_time_scale_multiplier"],
                "multiplier",
            ),
            "core_dryden_vertical_time_scale_multiplier": (
                dryden["core_dryden_vertical_time_scale_multiplier"],
                "multiplier",
            ),
        }
        name = abl_scenario_name(trusted, confidence, age, abl_stability, abl_mixing)
        for metric, (value, unit) in metrics.items():
            add_metric(
                rows,
                row_type="a4mc_source_quality_packet_abl_response_matrix",
                name=name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=DRONE_PHYSICS_SOURCE,
                source_url=f"{DRONE_ENVIRONMENT_SOURCE}; {DRONE_ENTITY_SOURCE}; {A4MC_GAMEPLAY_SAMPLE_SOURCE}",
                evidence_role="current_a4mc_abl_quality_response_matrix",
                note="Synthetic A4MC ABL response matrix for source-quality-gated Dryden intensity and time-scale tuning.",
            )


def add_summary(rows: list[dict[str, str]]) -> None:
    trusted_wall_skim_quality = source_quality(True, 0.86, 0)
    trusted_wall_skim = terrain_shear_response(0.58, 0.74, trusted_wall_skim_quality)
    trusted_wall_skim_turbulence = natural_turbulence_response(0.24, 0.58, 0.74, trusted_wall_skim_quality)
    trusted_unstable_abl = dryden_abl_response(REFERENCE_ABL_TURBULENCE_INTENSITY, 0.90, 0.90, trusted_wall_skim_quality)
    trusted_stable_abl = dryden_abl_response(REFERENCE_ABL_TURBULENCE_INTENSITY, -0.90, 0.90, trusted_wall_skim_quality)
    half_quality = source_quality(True, 0.5, 0)
    stale_quality = source_quality(True, 1.0, 160)
    summary_metrics = {
        "matrix_scenario_count": len(CONFIDENCE_LEVELS) * len(FRESHNESS_AGES_TICKS) * len(SHELTER_FACTORS) * len(SHEAR_LEVELS_PER_BLOCK) * len(SOURCE_TURBULENCE_LEVELS) + 1,
        "abl_matrix_scenario_count": len(CONFIDENCE_LEVELS) * len(FRESHNESS_AGES_TICKS) * len(ABL_STABILITY_LEVELS) * len(ABL_MIXING_LEVELS) + 1,
        "wall_skim_reference_source_quality": trusted_wall_skim_quality,
        "wall_skim_reference_adopted_source_turbulence": trusted_wall_skim_turbulence["core_adopted_source_turbulence_intensity"],
        "wall_skim_reference_natural_turbulence_proxy": trusted_wall_skim_turbulence["core_natural_turbulence_intensity_proxy"],
        "wall_skim_reference_source_gust_y_peak_mps": a4mc_source_gust_y_mps(trusted_wall_skim_quality),
        "wall_skim_reference_terrain_signal": trusted_wall_skim["terrain_signal"],
        "wall_skim_reference_terrain_vector_peak_proxy_mps": trusted_wall_skim["terrain_shear_vector_peak_proxy_mps"],
        "wall_skim_reference_ventilation_efficiency": ventilation_efficiency(0.74, trusted_wall_skim_quality),
        "unstable_mixed_abl_dryden_intensity_proxy": trusted_unstable_abl["core_dryden_intensity_proxy"],
        "unstable_mixed_abl_vertical_time_scale_multiplier": trusted_unstable_abl["core_dryden_vertical_time_scale_multiplier"],
        "stable_mixed_abl_dryden_intensity_proxy": trusted_stable_abl["core_dryden_intensity_proxy"],
        "stable_mixed_abl_vertical_time_scale_multiplier": trusted_stable_abl["core_dryden_vertical_time_scale_multiplier"],
        "half_confidence_source_quality": half_quality,
        "half_confidence_source_gust_y_peak_mps": a4mc_source_gust_y_mps(half_quality),
        "stale_source_quality": stale_quality,
        "stale_source_gust_y_peak_mps": a4mc_source_gust_y_mps(stale_quality),
    }
    for metric, value in summary_metrics.items():
        unit = "count" if metric.endswith("_count") else "m/s" if metric.endswith("_mps") else "fraction"
        add_metric(
            rows,
            row_type="a4mc_source_quality_packet_summary",
            name="summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT.relative_to(ROOT).as_posix(),
            evidence_role="handoff_summary",
            note="Compact handoff summary for A4MC source-quality transient tuning.",
        )


def add_method(rows: list[dict[str, str]]) -> None:
    add_metric(
        rows,
        row_type="a4mc_source_quality_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "This packet mirrors current source-quality formulas rather than field-test data. "
            "Use it to verify that confidence/freshness changes fade A4MC transient forcing before fitting "
            "source-turbulence floors, disk-gradient thrust loss, terrain-shear gust strength, "
            "ABL Dryden response, or local-voxel ventilation coefficients."
        ),
        unit="text",
        source_file=DRONE_PHYSICS_SOURCE,
        source_url=A4MC_GAMEPLAY_SAMPLE_SOURCE,
        evidence_role="method",
    )


def build_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    add_source_inventory(rows)
    add_response_matrix(rows)
    add_abl_response_matrix(rows)
    add_summary(rows)
    add_method(rows)
    return rows


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
        if not row.get("category", "").startswith("a4mc_source_quality_packet_")
    ]
    write_csv(SUMMARY, kept + synced_rows)
    return len(synced_rows)


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
