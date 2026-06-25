"""Build a propeller-damage and vibration calibration packet.

Outputs:
  docs/data/propeller_damage_vibration_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category prop_damage_packet_*

The source CSV is generated from the UAV Realistic Fault Dataset, DJI Mini 2
propeller-damage Figshare data, PADRE UAV measurement data, and current project
rotor-health/blade-pass alias scans. This packet narrows those rows into one
handoff table for damage vibration, fault-feature scale, and sample-rate alias
decisions.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

OUTPUT = DATA / "propeller_damage_vibration_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

PROP_DAMAGE = DATA / "propeller_damage_vibration_reference.csv"

DRONE_CONFIG_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneConfig.java"
DRONE_PHYSICS_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java"
UAV_REALISTIC_URL = "https://github.com/tiiuae/UAV-Realistic-Fault-Dataset"
UAV_REALISTIC_PAPER = "https://secplab.ppgia.pucpr.br/files/papers/2023icra.pdf"
DJI_MINI2_FIGSHARE = "https://doi.org/10.6084/m9.figshare.28765640"
DJI_MINI2_PAPER = "https://www.nature.com/articles/s41597-025-05692-4"
PADRE_URL = "https://github.com/AeroLabPUT/UAV_measurement_data"
PADRE_PAPER = "https://link.springer.com/article/10.1007/s10846-024-02101-7"


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def write_csv(path: Path, rows: Iterable[dict[str, object]]) -> None:
    rows = list(rows)
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames: list[str] = []
    for row in rows:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


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


def to_float(row: dict[str, str], key: str, default: float = math.nan) -> float:
    raw = row.get(key, "")
    if raw == "":
        return default
    try:
        return float(raw)
    except ValueError:
        return default


def clean(values: Iterable[float]) -> list[float]:
    return [value for value in values if math.isfinite(value)]


def add_metric(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path,
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
            "source_file": repo_path(source_file),
            "source_url": source_url,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def require_one(rows: list[dict[str, str]], predicate) -> dict[str, str]:
    found = [row for row in rows if predicate(row)]
    if len(found) != 1:
        raise LookupError(f"expected one row, found {len(found)}")
    return found[0]


def add_source_inventory(packet: list[dict[str, str]]) -> None:
    sources = [
        ("UAV_Realistic_Fault_Dataset", UAV_REALISTIC_URL, "broken_propeller_jsonl_logs", "ROS bag exports by 0-4 broken-propeller classes; sampled SensorCombined, raw Imu, and AudioBuffer JSONL rows."),
        ("UAV_Realistic_ICRA_2023_paper", UAV_REALISTIC_PAPER, "dataset_vehicle_sensor_context", "Paper context for Holybro X500, ReSpeaker audio, 100 flights, and fault classes."),
        ("DJI_Mini_2_propeller_damage_Figshare", DJI_MINI2_FIGSHARE, "raw_accelerometer_fault_vibration", "Five labeled Excel time-series files with triaxial acceleration and repeatable hover conditions."),
        ("DJI_Mini_2_Scientific_Data_paper", DJI_MINI2_PAPER, "dataset_method_context", "Paper context for the DJI Mini 2 damaged/unbalanced propeller measurement dataset."),
        ("PADRE_UAV_measurement_data", PADRE_URL, "actuator_fault_feature_repository", "3DR Solo and Parrot Bebop 2 fault data; packet keeps a sampled 3DR Solo FFT accelerometer feature subset."),
        ("PADRE_JINT_paper", PADRE_PAPER, "actuator_fault_method_context", "Paper context for the PADRE public actuator-fault measurement data."),
        ("Current_project_rotor_damage_model", f"{DRONE_CONFIG_SOURCE}; {DRONE_PHYSICS_SOURCE}", "current_runtime_damage_vibration_alias", "Current rotor-health vibration scan and blade-pass alias checks."),
        ("Generated_propeller_damage_reference", repo_path(PROP_DAMAGE), "wide_source_table", "Wide generated source table with all inventory, dataset, alias, and current damage-scan rows."),
    ]
    for name, source_url, role, note in sources:
        add_metric(
            packet,
            row_type="prop_damage_packet_source_inventory",
            name=name,
            metric="source_file",
            value=repo_path(PROP_DAMAGE),
            unit="path",
            source_file=PROP_DAMAGE,
            source_url=source_url,
            evidence_role=role,
            note=note,
        )


def add_rows_by_type(
    packet: list[dict[str, str]],
    rows: list[dict[str, str]],
    *,
    input_row_type: str,
    output_row_type: str,
    metrics: list[tuple[str, str]],
    role: str,
    note: str = "",
) -> None:
    for row in rows:
        if row.get("row_type") != input_row_type:
            continue
        for metric, unit in metrics:
            add_metric(
                packet,
                row_type=output_row_type,
                name=row["name"],
                metric=metric,
                value=row.get(metric, ""),
                unit=unit,
                source_file=PROP_DAMAGE,
                source_url=row.get("source", ""),
                evidence_role=role,
                note=note or row.get("note", ""),
            )


def add_dataset_rows(packet: list[dict[str, str]], rows: list[dict[str, str]]) -> None:
    add_rows_by_type(
        packet,
        rows,
        input_row_type="uav_realistic_fault_dataset_inventory",
        output_row_type="prop_damage_packet_uav_realistic_inventory",
        metrics=[
            ("class_count", "count"),
            ("data_file_count", "count"),
            ("sensorcombined_file_count", "count"),
            ("audiobuffer_file_count", "count"),
            ("imu_file_count", "count"),
            ("sensorcombined_sample_count_per_class", "count"),
            ("audiobuffer_sample_count_per_class", "count"),
            ("imu_sample_count_per_class", "count"),
            ("total_size_mb", "MB"),
        ],
        role="uav_realistic_dataset_inventory",
    )
    add_rows_by_type(
        packet,
        rows,
        input_row_type="uav_realistic_fault_class_inventory",
        output_row_type="prop_damage_packet_uav_realistic_class_inventory",
        metrics=[
            ("fault_class", "class"),
            ("broken_propeller_count", "count"),
            ("mission_count_sensorcombined", "count"),
            ("audio_file_count", "count"),
            ("imu_file_count", "count"),
            ("total_class_size_mb", "MB"),
            ("sampled_sensorcombined_count", "count"),
            ("sampled_audiobuffer_count", "count"),
            ("sampled_imu_count", "count"),
        ],
        role="uav_realistic_class_inventory",
    )
    add_rows_by_type(
        packet,
        rows,
        input_row_type="uav_realistic_fault_class_aggregate",
        output_row_type="prop_damage_packet_uav_sensorcombined_aggregate",
        metrics=[
            ("fault_class", "class"),
            ("sample_count", "count"),
            ("duration_s_total", "s"),
            ("timestamp_sample_rate_hz_median", "Hz"),
            ("gyro_integral_rate_hz_median", "Hz"),
            ("accel_integral_rate_hz_median", "Hz"),
            ("gyro_vector_dynamic_rms_rad_s_median", "rad/s"),
            ("accel_vector_dynamic_rms_m_s2_median", "m/s^2"),
            ("gyro_vector_dynamic_rms_ratio_vs_class0_median", "ratio"),
            ("accel_vector_dynamic_rms_ratio_vs_class0_median", "ratio"),
            ("accelerometer_clipping_count_total", "count"),
        ],
        role="uav_realistic_sensorcombined_fault_envelope",
    )
    add_rows_by_type(
        packet,
        rows,
        input_row_type="uav_realistic_fault_imu_class_aggregate",
        output_row_type="prop_damage_packet_uav_raw_imu_aggregate",
        metrics=[
            ("fault_class", "class"),
            ("sample_count", "count"),
            ("valid_imu_rows_total", "rows"),
            ("frame_id_count_max", "count"),
            ("gyro_two_axis_dynamic_rms_rad_s_median", "rad/s"),
            ("accel_two_axis_dynamic_rms_m_s2_median", "m/s^2"),
            ("gyro_two_axis_dynamic_rms_ratio_vs_class0_median", "ratio"),
            ("accel_two_axis_dynamic_rms_ratio_vs_class0_median", "ratio"),
            ("strongest_gyro_frame_dynamic_rms_ratio_vs_class0_max", "ratio"),
            ("strongest_accel_frame_dynamic_rms_ratio_vs_class0_max", "ratio"),
        ],
        role="uav_realistic_raw_imu_local_vibration",
    )
    add_rows_by_type(
        packet,
        rows,
        input_row_type="uav_realistic_fault_audiobuffer_summary",
        output_row_type="prop_damage_packet_uav_audio_sample",
        metrics=[
            ("fault_class", "class"),
            ("audio_buffer_count", "count"),
            ("payload_rms", "dataset units"),
            ("payload_rms_ratio_vs_class0", "ratio"),
            ("payload_abs_p99_ratio_vs_class0", "ratio"),
            ("block_rms_median_ratio_vs_class0", "ratio"),
            ("payload_near_clip_16bit_fraction", "fraction"),
        ],
        role="uav_realistic_audio_feature_scale",
    )

    add_rows_by_type(
        packet,
        rows,
        input_row_type="dataset_inventory",
        output_row_type="prop_damage_packet_dji_dataset_inventory",
        metrics=[
            ("license", "text"),
            ("zip_size_bytes", "bytes"),
            ("figshare_size_bytes", "bytes"),
            ("zip_size_matches_api", "bool"),
            ("excel_case_count", "count"),
        ],
        role="dji_mini2_dataset_inventory",
    )
    add_rows_by_type(
        packet,
        rows,
        input_row_type="dataset_signal_summary",
        output_row_type="prop_damage_packet_dji_signal_summary",
        metrics=[
            ("condition", "label"),
            ("sample_rate_hz", "Hz"),
            ("nyquist_hz", "Hz"),
            ("duration_s", "s"),
            ("vector_dynamic_rms_m_s2", "m/s^2"),
            ("vector_dynamic_rms_ratio_vs_healthy", "ratio"),
            ("dominant_freq_hz", "Hz"),
            ("dominant_psd_ratio_vs_healthy", "ratio"),
            ("band_120_240hz_rms_ratio_vs_healthy", "ratio"),
            ("band_240_500hz_rms_ratio_vs_healthy", "ratio"),
            ("dominant_freq_delta_vs_healthy_hz", "Hz"),
        ],
        role="dji_mini2_raw_accel_fault_vibration",
    )
    add_rows_by_type(
        packet,
        rows,
        input_row_type="current_bladepass_vs_dataset_bandwidth",
        output_row_type="prop_damage_packet_bladepass_bandwidth_check",
        metrics=[
            ("dataset_sample_rate_hz", "Hz"),
            ("dataset_nyquist_hz", "Hz"),
            ("dataset_dominant_freq_hz", "Hz"),
            ("dataset_two_blade_estimated_rpm", "rpm"),
            ("racing_hover_blade_pass_hz", "Hz"),
            ("racing_max_blade_pass_hz", "Hz"),
            ("racing_hover_bpf_over_dataset_nyquist", "ratio"),
            ("racing_max_bpf_over_dataset_nyquist", "ratio"),
        ],
        role="dji_sample_rate_vs_current_racing_bpf",
    )
    add_rows_by_type(
        packet,
        rows,
        input_row_type="current_bladepass_dataset_alias",
        output_row_type="prop_damage_packet_bladepass_alias",
        metrics=[
            ("preset", "label"),
            ("configured_blade_count", "count"),
            ("hover_motor_hz", "Hz"),
            ("max_motor_hz", "Hz"),
            ("hover_blade_pass_hz", "Hz"),
            ("max_blade_pass_hz", "Hz"),
            ("hover_blade_pass_alias_hz", "Hz"),
            ("max_blade_pass_alias_hz", "Hz"),
            ("hover_blade_pass_directly_observable", "bool"),
            ("max_blade_pass_directly_observable", "bool"),
            ("hover_blade_pass_over_dataset_nyquist", "ratio"),
            ("max_blade_pass_over_dataset_nyquist", "ratio"),
            ("hover_alias_delta_to_dataset_peak_hz", "Hz"),
            ("max_alias_delta_to_dataset_peak_hz", "Hz"),
        ],
        role="current_bpf_alias_at_dji_sample_rate",
    )

    add_rows_by_type(
        packet,
        rows,
        input_row_type="padre_dataset_inventory",
        output_row_type="prop_damage_packet_padre_inventory",
        metrics=[
            ("vehicle_count", "count"),
            ("vehicles", "text"),
            ("csv_file_count", "count"),
            ("csv_size_mb", "MB"),
            ("readme_file_count", "count"),
            ("sampled_subset", "text"),
        ],
        role="padre_fault_dataset_inventory",
    )
    add_rows_by_type(
        packet,
        rows,
        input_row_type="padre_fft_feature_condition_summary",
        output_row_type="prop_damage_packet_padre_fft_condition",
        metrics=[
            ("label", "label"),
            ("condition", "label"),
            ("fault_summary", "text"),
            ("xyz_feature_rms", "feature RMS"),
            ("xyz_feature_rms_ratio_vs_healthy", "ratio"),
            ("x_feature_rms_ratio_vs_healthy", "ratio"),
            ("y_feature_rms_ratio_vs_healthy", "ratio"),
            ("z_feature_rms_ratio_vs_healthy", "ratio"),
            ("feature_max", "feature units"),
        ],
        role="padre_3dr_solo_fft_fault_feature_scale",
    )
    add_rows_by_type(
        packet,
        rows,
        input_row_type="current_rotor_damage_vibration_scan",
        output_row_type="prop_damage_packet_current_rotor_damage_scan",
        metrics=[
            ("rotor_health", "health"),
            ("spin_ratio", "ratio"),
            ("rotor_health_thrust_scale", "scale"),
            ("effective_imbalance_intensity", "intensity"),
            ("rotor_damage_vibration", "intensity"),
        ],
        role="current_dimensionless_damage_vibration_scan",
    )


def add_crosscheck(packet: list[dict[str, str]], rows: list[dict[str, str]]) -> None:
    uav_inventory = require_one(rows, lambda row: row.get("row_type") == "uav_realistic_fault_dataset_inventory")
    sensor_agg = [row for row in rows if row.get("row_type") == "uav_realistic_fault_class_aggregate"]
    imu_agg = [row for row in rows if row.get("row_type") == "uav_realistic_fault_imu_class_aggregate"]
    audio = [row for row in rows if row.get("row_type") == "uav_realistic_fault_audiobuffer_summary"]
    dji_signals = [row for row in rows if row.get("row_type") == "dataset_signal_summary"]
    padre_conditions = [row for row in rows if row.get("row_type") == "padre_fft_feature_condition_summary"]
    current_damage = [row for row in rows if row.get("row_type") == "current_rotor_damage_vibration_scan"]
    alias_rows = [row for row in rows if row.get("row_type") == "current_bladepass_dataset_alias"]
    bandwidth = require_one(rows, lambda row: row.get("row_type") == "current_bladepass_vs_dataset_bandwidth")
    racing_alias = require_one(alias_rows, lambda row: row.get("preset") == "racingQuad")
    dji_healthy = require_one(dji_signals, lambda row: row.get("condition") == "healthy")

    strongest_sensor_gyro = max(sensor_agg, key=lambda row: to_float(row, "gyro_vector_dynamic_rms_ratio_vs_class0_median"))
    strongest_sensor_accel = max(sensor_agg, key=lambda row: to_float(row, "accel_vector_dynamic_rms_ratio_vs_class0_median"))
    strongest_imu_gyro = max(imu_agg, key=lambda row: to_float(row, "gyro_two_axis_dynamic_rms_ratio_vs_class0_median"))
    strongest_imu_accel = max(imu_agg, key=lambda row: to_float(row, "accel_two_axis_dynamic_rms_ratio_vs_class0_median"))
    strongest_audio = max(audio, key=lambda row: to_float(row, "payload_rms_ratio_vs_class0"))
    strongest_padre = max(padre_conditions, key=lambda row: to_float(row, "xyz_feature_rms_ratio_vs_healthy"))
    dji_ratios = clean(to_float(row, "vector_dynamic_rms_ratio_vs_healthy") for row in dji_signals)
    current_vibration = clean(to_float(row, "rotor_damage_vibration") for row in current_damage)
    current_imbalance = clean(to_float(row, "effective_imbalance_intensity") for row in current_damage)

    metrics = [
        ("uav_realistic_class_count", to_float(uav_inventory, "class_count"), "count"),
        ("uav_realistic_sensorcombined_file_count", to_float(uav_inventory, "sensorcombined_file_count"), "count"),
        ("uav_realistic_audiobuffer_file_count", to_float(uav_inventory, "audiobuffer_file_count"), "count"),
        ("uav_realistic_imu_file_count", to_float(uav_inventory, "imu_file_count"), "count"),
        ("uav_realistic_total_size_mb", to_float(uav_inventory, "total_size_mb"), "MB"),
        ("sensorcombined_strongest_gyro_fault_class", to_float(strongest_sensor_gyro, "fault_class"), "class"),
        ("sensorcombined_strongest_gyro_ratio", to_float(strongest_sensor_gyro, "gyro_vector_dynamic_rms_ratio_vs_class0_median"), "ratio"),
        ("sensorcombined_strongest_accel_fault_class", to_float(strongest_sensor_accel, "fault_class"), "class"),
        ("sensorcombined_strongest_accel_ratio", to_float(strongest_sensor_accel, "accel_vector_dynamic_rms_ratio_vs_class0_median"), "ratio"),
        ("raw_imu_strongest_gyro_fault_class", to_float(strongest_imu_gyro, "fault_class"), "class"),
        ("raw_imu_strongest_gyro_ratio", to_float(strongest_imu_gyro, "gyro_two_axis_dynamic_rms_ratio_vs_class0_median"), "ratio"),
        ("raw_imu_strongest_accel_fault_class", to_float(strongest_imu_accel, "fault_class"), "class"),
        ("raw_imu_strongest_accel_ratio", to_float(strongest_imu_accel, "accel_two_axis_dynamic_rms_ratio_vs_class0_median"), "ratio"),
        ("audio_strongest_payload_fault_class", to_float(strongest_audio, "fault_class"), "class"),
        ("audio_strongest_payload_rms_ratio", to_float(strongest_audio, "payload_rms_ratio_vs_class0"), "ratio"),
        ("dji_sample_rate_hz", to_float(dji_healthy, "sample_rate_hz"), "Hz"),
        ("dji_nyquist_hz", to_float(dji_healthy, "nyquist_hz"), "Hz"),
        ("dji_healthy_dominant_freq_hz", to_float(dji_healthy, "dominant_freq_hz"), "Hz"),
        ("dji_vector_rms_ratio_min", min(dji_ratios), "ratio"),
        ("dji_vector_rms_ratio_max", max(dji_ratios), "ratio"),
        ("racing_hover_blade_pass_hz", to_float(bandwidth, "racing_hover_blade_pass_hz"), "Hz"),
        ("racing_max_blade_pass_hz", to_float(bandwidth, "racing_max_blade_pass_hz"), "Hz"),
        ("racing_hover_bpf_over_dji_nyquist", to_float(bandwidth, "racing_hover_bpf_over_dataset_nyquist"), "ratio"),
        ("racing_max_bpf_over_dji_nyquist", to_float(bandwidth, "racing_max_bpf_over_dataset_nyquist"), "ratio"),
        ("racing_hover_bpf_alias_hz", to_float(racing_alias, "hover_blade_pass_alias_hz"), "Hz"),
        ("racing_max_bpf_alias_hz", to_float(racing_alias, "max_blade_pass_alias_hz"), "Hz"),
        ("padre_strongest_label", strongest_padre.get("label", ""), "label"),
        ("padre_strongest_xyz_feature_rms_ratio", to_float(strongest_padre, "xyz_feature_rms_ratio_vs_healthy"), "ratio"),
        ("current_damage_vibration_min", min(current_vibration), "intensity"),
        ("current_damage_vibration_max", max(current_vibration), "intensity"),
        ("current_effective_imbalance_max", max(current_imbalance), "intensity"),
    ]
    for metric, value, unit in metrics:
        add_metric(
            packet,
            row_type="prop_damage_packet_crosscheck",
            name="summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=PROP_DAMAGE,
            source_url=f"{UAV_REALISTIC_URL}; {DJI_MINI2_FIGSHARE}; {PADRE_URL}; {DRONE_CONFIG_SOURCE}; {DRONE_PHYSICS_SOURCE}",
            evidence_role="handoff_summary",
            note="Use these rows to separate flight-phase fault amplitudes, raw vibration spectra, normalized feature matrices, and current dimensionless damage intensity.",
        )


def add_method(packet: list[dict[str, str]]) -> None:
    add_metric(
        packet,
        row_type="prop_damage_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Use this packet for prop-damage and vibration handoff only. UAV Realistic rows are flight-phase-inclusive "
            "fault envelopes, DJI Mini 2 rows are raw accelerometer spectra for a different two-blade vehicle, PADRE rows "
            "are normalized FFT feature matrices, and current damage-vibration rows are dimensionless runtime intensities. "
            "Do not calibrate a physical blade-pass notch from DJI Mini 2 without applying the alias rows."
        ),
        unit="text",
        source_file=PROP_DAMAGE,
        source_url=f"{UAV_REALISTIC_URL}; {DJI_MINI2_FIGSHARE}; {PADRE_URL}",
        evidence_role="method",
    )


def build_packet() -> list[dict[str, str]]:
    source_rows = read_rows(PROP_DAMAGE)
    packet: list[dict[str, str]] = []
    add_source_inventory(packet)
    add_dataset_rows(packet, source_rows)
    add_crosscheck(packet, source_rows)
    add_method(packet)
    return packet


def sync_summary(packet_rows: Iterable[dict[str, str]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("prop_damage_packet_")]
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
    packet_rows = build_packet()
    write_csv(OUTPUT, packet_rows)
    synced = sync_summary(packet_rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(packet_rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
