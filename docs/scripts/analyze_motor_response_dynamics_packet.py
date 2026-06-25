"""Build a motor/ESC response dynamics calibration packet.

Outputs:
  docs/data/motor_response_dynamics_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category motor_response_packet_*

The source files used here were generated from public Betaflight PR #12562
blackbox RPM logs, APdrone urban eRPM Blackbox logs, RotorS/PX4 actuator lag
references, and current project configuration scans. This packet gathers those
separate rows into one handoff table for motor tau, braking, and command/RPM
lag decisions.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

OUTPUT = DATA / "motor_response_dynamics_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

ROTOR_TIME = DATA / "rotor_inflow_time_scale_reference.csv"
ESC_ELECTRICAL = DATA / "esc_electrical_dynamics_reference.csv"
BLACKBOX_RESPONSE = DATA / "blackbox_rpm_response_model_comparison.csv"
BLACKBOX_SUMMARY = DATA / "blackbox_rpm_decoded_summary.csv"
APDRONE_URBAN_RPM = DATA / "apdrone_urban_motor_rpm_reference.csv"
AIIO_LOW_DYNAMIC = DATA / "aiio_low_dynamic_rotor_rpm_reference.csv"

DRONE_CONFIG_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneConfig.java"
ROTOR_S_PX4_SOURCE = "RotorS/PX4 timeConstantUp/Down; DroneConfig; NASA Johnson VRS induced-velocity normalization"
BETAFLIGHT_PR12562_SOURCE = "https://github.com/betaflight/betaflight/pull/12562"
APDRONE_SOURCE = "https://data.mendeley.com/datasets/zgsvdtxnfh/2"
AIIO_SOURCE = "https://github.com/SJTU-ViSYS-team/AI-IO/releases/download/v1.0/AI-IO_dataset.tar.gz"

ROTOR_S_TIME_CONSTANT_UP_S = 0.0125
ROTOR_S_TIME_CONSTANT_DOWN_S = 0.025

# Current apDrone values read from DroneConfig.apDrone().
APDRONE_CURRENT_MOTOR_TAU_S = 0.015
APDRONE_CURRENT_ESC_FRAME_RATE_HZ = 480.0
APDRONE_CURRENT_ACTIVE_BRAKING_STRENGTH = 0.62


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


def percentile(values: Iterable[float], p: float) -> float:
    clean = sorted(value for value in values if math.isfinite(value))
    if not clean:
        return math.nan
    k = (len(clean) - 1) * p / 100.0
    lo = math.floor(k)
    hi = math.ceil(k)
    if lo == hi:
        return clean[lo]
    f = k - lo
    return clean[lo] * (1.0 - f) + clean[hi] * f


def require_one(rows: list[dict[str, str]], predicate) -> dict[str, str]:
    found = [row for row in rows if predicate(row)]
    if len(found) != 1:
        raise LookupError(f"expected one row, found {len(found)}")
    return found[0]


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


def add_source_inventory(packet: list[dict[str, str]]) -> None:
    sources = [
        (
            "RotorS_PX4_actuator_lag_reference",
            ROTOR_TIME,
            ROTOR_S_PX4_SOURCE,
            "open_simulator_actuator_lag",
            "RotorS/PX4 first-order actuator lag values, used only as simple open-source order-of-magnitude references.",
        ),
        (
            "Betaflight_PR12562_blackbox_RPM_logs",
            BLACKBOX_RESPONSE,
            BETAFLIGHT_PR12562_SOURCE,
            "real_betaflight_rpm_telemetry_slew",
            "Decoded public PR attachment logs; useful for observed 50 ms RPM slew, not controlled motor step response.",
        ),
        (
            "APdrone_urban_Betaflight_eRPM_logs",
            APDRONE_URBAN_RPM,
            APDRONE_SOURCE,
            "real_apdrone_motor_command_rpm_telemetry",
            "Urban Blackbox logs with motor commands and bidirectional-DShot eRPM; useful for command/RPM curve and lag diagnostics.",
        ),
        (
            "AIIO_low_dynamic_ESC_RPM_logs",
            AIIO_LOW_DYNAMIC,
            AIIO_SOURCE,
            "real_esc_rpm_hover_scale",
            "VICON/IMU/ESC telemetry filtered to low-dynamic flight; useful as hover-RPM scale, not response timing.",
        ),
        (
            "Current_project_ESC_electrical_model",
            ESC_ELECTRICAL,
            DRONE_CONFIG_SOURCE,
            "current_model_headroom_braking_slew_proxy",
            "Mirrors current voltage-headroom, braking-current, ESC slew, and command-frame formulas.",
        ),
    ]
    for name, source_file, source_url, role, note in sources:
        add_metric(
            packet,
            row_type="motor_response_packet_source_inventory",
            name=name,
            metric="source_file",
            value=repo_path(source_file),
            unit="path",
            source_file=source_file,
            source_url=source_url,
            evidence_role=role,
            note=note,
        )


def add_rotors_px4_current(packet: list[dict[str, str]], rotor_rows: list[dict[str, str]]) -> None:
    for preset in ["racingQuad", "cinewhoop", "heavyLift", "hexLift", "octoLift", "coaxialX8"]:
        row = require_one(
            rotor_rows,
            lambda r, preset=preset: r.get("row_type") == "current_preset_inflow_time_scale"
            and r.get("preset") == preset,
        )
        for metric, unit in [
            ("motor_tau_s", "s"),
            ("rotor_inflow_tau_s", "s"),
            ("motor_tau_vs_ref_up", "ratio"),
            ("motor_tau_vs_ref_down", "ratio"),
            ("inflow_tau_vs_ref_up", "ratio"),
            ("wake_transit_r_over_hover_vi_s", "s"),
            ("wake_transit_2r_over_hover_vi_s", "s"),
            ("inflow_tau_over_r_vi", "ratio"),
            ("inflow_tau_over_2r_vi", "ratio"),
        ]:
            add_metric(
                packet,
                row_type="motor_response_packet_open_sim_comparison",
                name=preset,
                metric=metric,
                value=row.get(metric, ""),
                unit=unit,
                source_file=ROTOR_TIME,
                source_url=ROTOR_S_PX4_SOURCE,
                evidence_role="open_simulator_and_induced_velocity_time_scale",
            )


def add_blackbox_response(packet: list[dict[str, str]], comparison_rows: list[dict[str, str]], decoded_rows: list[dict[str, str]]) -> None:
    motor_rows = [
        row
        for row in comparison_rows
        if row.get("row_type") == "decoded_motor_vs_current_model"
        and math.isfinite(to_float(row, "max_50ms_positive_slew_rpm_s"))
    ]
    log_rows = [
        row
        for row in comparison_rows
        if row.get("row_type") == "decoded_log_vs_current_model"
        and math.isfinite(to_float(row, "max_positive_slew_rpm_s"))
    ]
    current = require_one(comparison_rows, lambda r: r.get("row_type") == "current_model_reference")
    strongest_positive = max(motor_rows, key=lambda row: to_float(row, "max_50ms_positive_slew_rpm_s"))
    strongest_negative = max(motor_rows, key=lambda row: to_float(row, "max_50ms_negative_slew_rpm_s"))
    fastest_rpm = max(motor_rows, key=lambda row: to_float(row, "rpm_max"))
    valid_log_summaries = [
        row
        for row in decoded_rows
        if row.get("row_type") == "decoded_log_summary" and math.isfinite(to_float(row, "rpm_max_all_motors"))
    ]

    summary_metrics = [
        ("decoded_motor_row_count", len(motor_rows), "count"),
        ("decoded_log_count_with_valid_rpm", len(valid_log_summaries), "count"),
        ("current_racing_motor_tau_s", to_float(current, "current_motor_tau_s"), "s"),
        ("current_racing_braking_tau_proxy_s", to_float(current, "current_braking_tau_proxy_s"), "s"),
        ("current_racing_nominal_spinup_slew_rpm_s", to_float(current, "current_nominal_spinup_slew_rpm_s"), "rpm/s"),
        ("current_racing_braking_slew_proxy_rpm_s", to_float(current, "current_braking_slew_proxy_rpm_s"), "rpm/s"),
        ("observed_max_positive_50ms_slew_rpm_s", to_float(strongest_positive, "max_50ms_positive_slew_rpm_s"), "rpm/s"),
        ("observed_max_negative_50ms_slew_rpm_s", to_float(strongest_negative, "max_50ms_negative_slew_rpm_s"), "rpm/s"),
        ("observed_positive_slew_over_current_spinup_proxy", to_float(strongest_positive, "positive_slew_over_current_nominal_spinup"), "ratio"),
        ("observed_negative_slew_over_current_braking_proxy", to_float(strongest_negative, "negative_slew_over_current_braking_proxy"), "ratio"),
        ("observed_tau_equiv_strongest_positive_s", to_float(strongest_positive, "observed_tau_equiv_current_max_positive_s"), "s"),
        ("observed_tau_equiv_strongest_negative_s", to_float(strongest_negative, "observed_tau_equiv_current_max_negative_s"), "s"),
        ("decoded_rpm_max_across_motors", to_float(fastest_rpm, "rpm_max"), "rpm"),
        ("current_max_rpm_over_decoded_rpm_max", to_float(fastest_rpm, "current_max_rpm_over_decoded_rpm_max"), "ratio"),
        ("log_level_positive_slew_p50_rpm_s", percentile([to_float(row, "max_positive_slew_rpm_s") for row in log_rows], 50), "rpm/s"),
        ("log_level_positive_slew_max_rpm_s", max(to_float(row, "max_positive_slew_rpm_s") for row in log_rows), "rpm/s"),
        ("log_level_negative_slew_max_rpm_s", max(to_float(row, "max_negative_slew_rpm_s") for row in log_rows), "rpm/s"),
    ]
    for metric, value, unit in summary_metrics:
        add_metric(
            packet,
            row_type="motor_response_packet_blackbox_rpm_slew_summary",
            name="Betaflight_PR12562_RPM_slew",
            metric=metric,
            value=value,
            unit=unit,
            source_file=BLACKBOX_RESPONSE,
            source_url=BETAFLIGHT_PR12562_SOURCE,
            evidence_role="decoded_real_blackbox_rpm_slew",
            note="Observed from public Betaflight PR logs using 50 ms windows; not controlled bench step data.",
        )

    for source_row, label in [
        (strongest_positive, "strongest_positive_50ms_slew_motor"),
        (strongest_negative, "strongest_negative_50ms_slew_motor"),
        (fastest_rpm, "fastest_decoded_rpm_motor"),
    ]:
        for metric, unit in [
            ("decoded_log", "text"),
            ("motor_index", "index"),
            ("rpm_p95", "rpm"),
            ("rpm_max", "rpm"),
            ("max_50ms_positive_slew_rpm_s", "rpm/s"),
            ("max_50ms_negative_slew_rpm_s", "rpm/s"),
            ("observed_tau_equiv_current_max_positive_s", "s"),
            ("observed_tau_equiv_current_max_negative_s", "s"),
            ("positive_slew_over_current_nominal_spinup", "ratio"),
            ("negative_slew_over_current_braking_proxy", "ratio"),
            ("current_max_rpm_over_decoded_rpm_max", "ratio"),
        ]:
            add_metric(
                packet,
                row_type="motor_response_packet_blackbox_rpm_slew_extreme",
                name=label,
                metric=metric,
                value=source_row.get(metric, ""),
                unit=unit,
                source_file=BLACKBOX_RESPONSE,
                source_url=source_row.get("source", BETAFLIGHT_PR12562_SOURCE),
                evidence_role="decoded_real_blackbox_rpm_slew_extreme",
            )


def add_apdrone_urban_response(packet: list[dict[str, str]], urban_rows: list[dict[str, str]]) -> None:
    all_motor_rows = [row for row in urban_rows if row.get("row_type") == "apdrone_urban_motor_rpm_file_all_motors"]
    motor_rows = [row for row in urban_rows if row.get("row_type") == "apdrone_urban_motor_rpm_file_motor"]
    model_row = require_one(urban_rows, lambda r: r.get("row_type") == "current_project_apDrone_rpm_model")
    if len(all_motor_rows) != 5:
        raise RuntimeError(f"expected 5 APdrone all-motor rows, found {len(all_motor_rows)}")

    first_order_tau_p50 = [to_float(row, "first_order_tau_p50_ms") for row in all_motor_rows]
    first_order_tau_p90 = [to_float(row, "first_order_tau_p90_ms") for row in all_motor_rows]
    best_level_lag = [to_float(row, "best_level_lag_ms") for row in all_motor_rows]
    best_delta_lag = [to_float(row, "best_delta_lag_ms") for row in all_motor_rows]
    rpm_p95 = [to_float(row, "mechanical_rpm_p95") for row in all_motor_rows]
    rpm_max = [to_float(row, "mechanical_rpm_max_sampled") for row in all_motor_rows]
    linear_r2 = [to_float(row, "linear_fit_r2") for row in all_motor_rows]
    power_r2 = [to_float(row, "power_fit_r2_log") for row in all_motor_rows]

    summary_metrics = [
        ("source_file_count", len(all_motor_rows), "count"),
        ("current_apDrone_motor_tau_s", APDRONE_CURRENT_MOTOR_TAU_S, "s"),
        ("current_apDrone_esc_frame_rate_hz", APDRONE_CURRENT_ESC_FRAME_RATE_HZ, "Hz"),
        ("current_apDrone_active_braking_strength", APDRONE_CURRENT_ACTIVE_BRAKING_STRENGTH, "unitless"),
        ("current_apDrone_hover_rpm", to_float(model_row, "current_apDrone_hover_rpm"), "rpm"),
        ("current_apDrone_max_rpm", to_float(model_row, "current_apDrone_max_rpm"), "rpm"),
        ("current_apDrone_nominal_spinup_slew_rpm_s", to_float(model_row, "current_apDrone_max_rpm") / APDRONE_CURRENT_MOTOR_TAU_S, "rpm/s"),
        ("all_motor_valid_erpm_fraction_min", min(to_float(row, "valid_erpm_fraction") for row in all_motor_rows), "fraction"),
        ("all_motor_mechanical_rpm_p95_median", percentile(rpm_p95, 50), "rpm"),
        ("all_motor_mechanical_rpm_max_across_files", max(rpm_max), "rpm"),
        ("all_motor_rpm_over_current_hover_p95_median", percentile([to_float(row, "rpm_over_current_hover_p95") for row in all_motor_rows], 50), "ratio"),
        ("all_motor_linear_fit_r2_median", percentile(linear_r2, 50), "R2"),
        ("all_motor_power_fit_r2_median", percentile(power_r2, 50), "R2 log"),
        ("command_rpm_level_lag_p10_ms", percentile(best_level_lag, 10), "ms"),
        ("command_rpm_level_lag_p50_ms", percentile(best_level_lag, 50), "ms"),
        ("command_rpm_level_lag_p90_ms", percentile(best_level_lag, 90), "ms"),
        ("command_rpm_delta_lag_p10_ms", percentile(best_delta_lag, 10), "ms"),
        ("command_rpm_delta_lag_p50_ms", percentile(best_delta_lag, 50), "ms"),
        ("command_rpm_delta_lag_p90_ms", percentile(best_delta_lag, 90), "ms"),
        ("first_order_tau_p50_across_files_p10_ms", percentile(first_order_tau_p50, 10), "ms"),
        ("first_order_tau_p50_across_files_p50_ms", percentile(first_order_tau_p50, 50), "ms"),
        ("first_order_tau_p50_across_files_p90_ms", percentile(first_order_tau_p50, 90), "ms"),
        ("first_order_tau_p90_across_files_max_ms", max(first_order_tau_p90), "ms"),
        ("current_motor_tau_over_first_order_tau_p50_median", (APDRONE_CURRENT_MOTOR_TAU_S * 1000.0) / percentile(first_order_tau_p50, 50), "ratio"),
        ("current_motor_tau_over_level_lag_p50", (APDRONE_CURRENT_MOTOR_TAU_S * 1000.0) / percentile(best_level_lag, 50), "ratio"),
    ]
    for metric, value, unit in summary_metrics:
        add_metric(
            packet,
            row_type="motor_response_packet_apdrone_urban_summary",
            name="APdrone_urban_motor_command_rpm",
            metric=metric,
            value=value,
            unit=unit,
            source_file=APDRONE_URBAN_RPM,
            source_url=APDRONE_SOURCE,
            evidence_role="real_apdrone_bidir_dshot_erpm_command_lag",
            note="Correlation lags include closed-loop/log alignment; first-order tau candidates are the weaker motor-response clue.",
        )

    for row in all_motor_rows:
        name = f"APdrone_urban:{row.get('flight_filename', '')}:all_motors"
        for metric, unit in [
            ("duration_s", "s"),
            ("valid_erpm_fraction", "fraction"),
            ("mechanical_rpm_p50", "rpm"),
            ("mechanical_rpm_p95", "rpm"),
            ("mechanical_rpm_max_sampled", "rpm"),
            ("rpm_over_current_hover_p95", "ratio"),
            ("linear_fit_r2", "R2"),
            ("linear_fit_rpm_at_norm_1", "rpm"),
            ("power_fit_r2_log", "R2 log"),
            ("best_level_lag_ms", "ms"),
            ("best_level_correlation", "correlation"),
            ("best_delta_lag_ms", "ms"),
            ("best_delta_correlation", "correlation"),
            ("first_order_tau_p50_ms", "ms"),
            ("first_order_tau_p90_ms", "ms"),
        ]:
            add_metric(
                packet,
                row_type="motor_response_packet_apdrone_urban_file",
                name=name,
                metric=metric,
                value=row.get(metric, ""),
                unit=unit,
                source_file=APDRONE_URBAN_RPM,
                source_url=APDRONE_SOURCE,
                evidence_role="real_apdrone_bidir_dshot_erpm_file_summary",
            )


def add_aiio_hover_scale(packet: list[dict[str, str]], aiio_rows: list[dict[str, str]]) -> None:
    summary_rows = [row for row in aiio_rows if row.get("row_type") == "aiio_low_dynamic_summary"]
    if not summary_rows:
        return
    for row in summary_rows:
        name = row.get("name", "AIIO_low_dynamic")
        for metric, unit in [
            ("file_count", "count"),
            ("sample_count", "count"),
            ("duration_s", "s"),
            ("rotor_rpm_mean", "rpm"),
            ("rotor_rpm_p50", "rpm"),
            ("rotor_rpm_p95", "rpm"),
            ("rotor_rpm_mean_over_racingQuad_hover", "ratio"),
            ("rotor_rpm_p95_over_racingQuad_hover", "ratio"),
        ]:
            if metric in row:
                add_metric(
                    packet,
                    row_type="motor_response_packet_aiio_hover_scale",
                    name=name,
                    metric=metric,
                    value=row.get(metric, ""),
                    unit=unit,
                    source_file=AIIO_LOW_DYNAMIC,
                    source_url=AIIO_SOURCE,
                    evidence_role="real_esc_rpm_low_dynamic_hover_scale",
                    note="RPM scale evidence only; AI-IO HDF5 cadence is not a motor response bandwidth source.",
                )


def add_method(packet: list[dict[str, str]]) -> None:
    add_metric(
        packet,
        row_type="motor_response_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Use this packet to separate three timing layers: bare/open-sim actuator lag, real-flight command/RPM "
            "correlation lag, and short-window RPM slew. PR12562 and APdrone rows are real logs but not controlled bench "
            "steps, so they constrain order of magnitude and failure cases rather than exact motor transfer functions."
        ),
        unit="text",
        source_file=OUTPUT,
        source_url=BETAFLIGHT_PR12562_SOURCE,
        evidence_role="method_caveat",
    )


def build_packet() -> list[dict[str, str]]:
    rotor_rows = read_rows(ROTOR_TIME)
    esc_rows = read_rows(ESC_ELECTRICAL)
    blackbox_response_rows = read_rows(BLACKBOX_RESPONSE)
    blackbox_summary_rows = read_rows(BLACKBOX_SUMMARY)
    apdrone_rows = read_rows(APDRONE_URBAN_RPM)
    aiio_rows = read_rows(AIIO_LOW_DYNAMIC)

    packet: list[dict[str, str]] = []
    add_source_inventory(packet)
    add_rotors_px4_current(packet, rotor_rows)
    add_blackbox_response(packet, blackbox_response_rows, blackbox_summary_rows)
    add_apdrone_urban_response(packet, apdrone_rows)
    add_aiio_hover_scale(packet, aiio_rows)
    add_method(packet)
    return packet


def sync_summary(packet_rows: Iterable[dict[str, str]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("motor_response_packet_")]
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
