"""Build a WAVELab Pelican motor-command response calibration packet.

Outputs:
  docs/data/wavelab_pelican_motor_response_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category pelican_motor_packet_*

The WAVELab dataset is not an FPV 5-inch platform and its motor speed unit is
AscTec's integer unit, not mechanical RPM. The useful calibration signal here is
relative command-to-actual timing and response shape from a clean Vicon/IMU/motor
system-identification dataset.
"""

from __future__ import annotations

import csv
import math
import urllib.request
from pathlib import Path
from typing import Iterable

import numpy as np
from scipy.io import loadmat


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw"

MAT_PATH = RAW / "AscTec_Pelican_Flight_Dataset.mat"
PDF_PATH = RAW / "wavelab_pelican_dataset_doc.pdf"

OUTPUT = DATA / "wavelab_pelican_motor_response_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

README_URL = "https://raw.githubusercontent.com/wavelab/pelican_dataset/master/README.md"
PDF_URL = "https://raw.githubusercontent.com/wavelab/pelican_dataset/master/dataset/Pelican_Dataset.pdf"
MAT_URL = "http://wavelab.uwaterloo.ca/wp-content/uploads/2017/09/AscTec_Pelican_Flight_Dataset.mat"

SAMPLE_RATE_HZ = 100.0
DT_S = 1.0 / SAMPLE_RATE_HZ
MOTOR_UNIT_MIN = 0.0
MOTOR_UNIT_MAX = 218.0
SMOOTHING_WINDOW_SAMPLES = 5.0
PDF_TOTAL_SAMPLES = 1_388_410

RACINGQUAD_MOTOR_TAU_S = 0.045
APDRONE_MOTOR_TAU_S = 0.015


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def download_if_missing(url: str, path: Path) -> None:
    if path.exists():
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url, timeout=60) as response, path.open("wb") as handle:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            handle.write(chunk)


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
            writer.writerow({key: value_text(row.get(key, "")) for key in fieldnames})


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def percentile(values: Iterable[float], p: float) -> float:
    clean = np.asarray([value for value in values if math.isfinite(float(value))], dtype=float)
    if clean.size == 0:
        return math.nan
    return float(np.percentile(clean, p))


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path,
    source_url: str,
    evidence_role: str,
    note: str = "",
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": value,
            "unit": unit,
            "source_file": repo_path(source_file),
            "source_url": source_url,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def best_derivative_lag(cmd: np.ndarray, actual: np.ndarray, max_lag_samples: int = 50) -> tuple[int, float]:
    d_cmd = np.diff(np.asarray(cmd, dtype=float))
    d_actual = np.diff(np.asarray(actual, dtype=float))
    d_cmd = d_cmd - float(np.mean(d_cmd))
    d_actual = d_actual - float(np.mean(d_actual))

    best_lag = 0
    best_corr = -math.inf
    for lag in range(-max_lag_samples, max_lag_samples + 1):
        if lag >= 0:
            x = d_cmd[: len(d_cmd) - lag] if lag else d_cmd
            y = d_actual[lag:]
        else:
            x = d_cmd[-lag:]
            y = d_actual[: len(d_actual) + lag]
        if len(x) < 200 or float(np.std(x)) < 1e-9 or float(np.std(y)) < 1e-9:
            continue
        corr = float(np.corrcoef(x, y)[0, 1])
        if corr > best_corr:
            best_lag = lag
            best_corr = corr
    return best_lag, best_corr


def aligned_series(cmd: np.ndarray, actual: np.ndarray, lag_samples: int) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    n = min(len(cmd), len(actual))
    cmd = np.asarray(cmd[:n], dtype=float)
    actual = np.asarray(actual[:n], dtype=float)

    start = max(0, lag_samples)
    stop = min(n - 1, n - 1 + lag_samples)
    idx = np.arange(start, stop)
    return cmd[idx - lag_samples], actual[idx], actual[idx + 1]


def response_fit(cmd: np.ndarray, actual: np.ndarray, lag_samples: int) -> dict[str, float]:
    cmd_aligned, actual_now, actual_next = aligned_series(cmd, actual, lag_samples)
    if len(actual_now) < 200:
        return {}

    design = np.vstack([cmd_aligned, np.ones_like(cmd_aligned)]).T
    beta, *_ = np.linalg.lstsq(design, actual_now, rcond=None)
    target = design @ beta
    residual = target - actual_now
    rmse = float(np.sqrt(np.mean(np.square(residual))))

    delta = actual_next - actual_now
    mask = np.abs(residual) > 0.5
    alpha = math.nan
    tau_s = math.nan
    if int(np.sum(mask)) >= 200:
        alpha = float(np.dot(residual[mask], delta[mask]) / np.dot(residual[mask], residual[mask]))
        if 0.0 < alpha < 1.0:
            tau_s = -DT_S / math.log(1.0 - alpha)

    return {
        "static_gain_actual_unit_per_cmd_unit": float(beta[0]),
        "static_offset_actual_unit": float(beta[1]),
        "static_map_rmse_actual_unit": rmse,
        "first_order_alpha_per_sample": alpha,
        "first_order_tau_s": tau_s,
        "fit_samples_used": float(np.sum(mask)),
    }


def add_summary_percentiles(
    rows: list[dict[str, object]],
    *,
    name: str,
    metric_prefix: str,
    values: Iterable[float],
    unit: str,
    source_file: Path,
    source_url: str,
    evidence_role: str,
    note: str,
) -> None:
    for p in [0, 10, 50, 90, 95, 99, 100]:
        add_metric(
            rows,
            row_type="pelican_motor_packet_distribution_summary",
            name=name,
            metric=f"{metric_prefix}_p{p}",
            value=percentile(values, p),
            unit=unit,
            source_file=source_file,
            source_url=source_url,
            evidence_role=evidence_role,
            note=note,
        )


def sync_summary(packet_rows: list[dict[str, object]]) -> None:
    existing = read_rows(SUMMARY) if SUMMARY.exists() else []
    kept = [row for row in existing if not row.get("category", "").startswith("pelican_motor_packet_")]
    for row in packet_rows:
        kept.append(
            {
                "category": row["row_type"],
                "name": row["name"],
                "metric": row["metric"],
                "value": value_text(row["value"]),
                "unit": row["unit"],
                "source": row["source_url"] or row["source_file"],
            }
        )
    write_csv(SUMMARY, kept)


def main() -> None:
    download_if_missing(PDF_URL, PDF_PATH)
    download_if_missing(MAT_URL, MAT_PATH)

    mat = loadmat(MAT_PATH, squeeze_me=True, struct_as_record=False)
    flights = list(mat["flights"])

    rows: list[dict[str, object]] = []
    source_note = "WAVELab/Waterloo AscTec Pelican system-identification dataset; motor speed is an AscTec integer unit, not mechanical RPM."

    add_metric(
        rows,
        row_type="pelican_motor_packet_source_inventory",
        name="wavelab_pelican_readme",
        metric="dataset_format",
        value="MATLAB .mat",
        unit="text",
        source_file=MAT_PATH,
        source_url=README_URL,
        evidence_role="dataset_metadata",
        note=source_note,
    )
    add_metric(
        rows,
        row_type="pelican_motor_packet_source_inventory",
        name="wavelab_pelican_readme",
        metric="published_dataset_size_mb",
        value=238.1,
        unit="MB",
        source_file=MAT_PATH,
        source_url=README_URL,
        evidence_role="dataset_metadata",
        note=source_note,
    )
    add_metric(
        rows,
        row_type="pelican_motor_packet_source_inventory",
        name="wavelab_pelican_pdf",
        metric="sample_rate_hz",
        value=SAMPLE_RATE_HZ,
        unit="Hz",
        source_file=PDF_PATH,
        source_url=PDF_URL,
        evidence_role="measurement_metadata",
        note="PDF states all listed quantities are measured at 100 Hz.",
    )
    add_metric(
        rows,
        row_type="pelican_motor_packet_source_inventory",
        name="wavelab_pelican_pdf",
        metric="motor_unit_range",
        value=f"{int(MOTOR_UNIT_MIN)}..{int(MOTOR_UNIT_MAX)}",
        unit="AscTec_unit",
        source_file=PDF_PATH,
        source_url=PDF_URL,
        evidence_role="measurement_metadata",
        note="PDF table describes motor speeds as integer values in [0, 218].",
    )
    add_metric(
        rows,
        row_type="pelican_motor_packet_source_inventory",
        name="wavelab_pelican_pdf",
        metric="smoothing_window_samples",
        value=SMOOTHING_WINDOW_SAMPLES,
        unit="samples",
        source_file=PDF_PATH,
        source_url=PDF_URL,
        evidence_role="preprocessing_metadata",
        note="PDF says a local-regression smoothing filter with window size 5 is applied; motor speeds use the robust version.",
    )

    lengths = [int(flight.len) for flight in flights]
    total_samples = sum(lengths)
    add_metric(
        rows,
        row_type="pelican_motor_packet_dataset_summary",
        name="loaded_dataset",
        metric="flight_count",
        value=len(flights),
        unit="count",
        source_file=MAT_PATH,
        source_url=MAT_URL,
        evidence_role="loaded_mat_summary",
        note=source_note,
    )
    add_metric(
        rows,
        row_type="pelican_motor_packet_dataset_summary",
        name="loaded_dataset",
        metric="total_samples",
        value=total_samples,
        unit="samples",
        source_file=MAT_PATH,
        source_url=MAT_URL,
        evidence_role="loaded_mat_summary",
        note=source_note,
    )
    add_metric(
        rows,
        row_type="pelican_motor_packet_dataset_summary",
        name="loaded_dataset",
        metric="total_duration_h",
        value=total_samples / SAMPLE_RATE_HZ / 3600.0,
        unit="h",
        source_file=MAT_PATH,
        source_url=MAT_URL,
        evidence_role="loaded_mat_summary",
        note=source_note,
    )
    add_metric(
        rows,
        row_type="pelican_motor_packet_dataset_summary",
        name="loaded_dataset",
        metric="loaded_total_samples_over_pdf_total_samples",
        value=total_samples / PDF_TOTAL_SAMPLES,
        unit="ratio",
        source_file=MAT_PATH,
        source_url=PDF_URL,
        evidence_role="traceability_check",
        note="Confirms the MAT sample count matches the PDF description.",
    )

    all_actual: list[float] = []
    all_cmd: list[float] = []
    actual_slew_fullscale_per_s: list[float] = []
    cmd_slew_fullscale_per_s: list[float] = []
    lags: list[float] = []
    correlations: list[float] = []
    gains: list[float] = []
    offsets: list[float] = []
    rmses: list[float] = []
    alphas: list[float] = []
    taus: list[float] = []

    for flight_index, flight in enumerate(flights, start=1):
        actual = np.asarray(flight.Motors, dtype=float)
        cmd = np.asarray(flight.Motors_CMD, dtype=float)
        velocity = np.asarray(flight.Vel, dtype=float)
        pqr = np.asarray(flight.pqr, dtype=float)
        name = f"flight_{flight_index:02d}"

        all_actual.extend(actual.ravel().tolist())
        all_cmd.extend(cmd.ravel().tolist())

        flight_actual_slew = np.abs(np.diff(actual, axis=0)) * SAMPLE_RATE_HZ / MOTOR_UNIT_MAX
        flight_cmd_slew = np.abs(np.diff(cmd, axis=0)) * SAMPLE_RATE_HZ / MOTOR_UNIT_MAX
        actual_slew_fullscale_per_s.extend(flight_actual_slew.ravel().tolist())
        cmd_slew_fullscale_per_s.extend(flight_cmd_slew.ravel().tolist())

        for metric, value, unit in [
            ("samples", int(flight.len), "samples"),
            ("duration_s", int(flight.len) / SAMPLE_RATE_HZ, "s"),
            ("actual_motor_unit_min", float(np.nanmin(actual)), "AscTec_unit"),
            ("actual_motor_unit_max", float(np.nanmax(actual)), "AscTec_unit"),
            ("cmd_motor_unit_min", float(np.nanmin(cmd)), "AscTec_unit"),
            ("cmd_motor_unit_max", float(np.nanmax(cmd)), "AscTec_unit"),
            ("actual_slew_fullscale_per_s_p95", percentile(flight_actual_slew.ravel(), 95), "fullscale/s"),
            ("cmd_slew_fullscale_per_s_p95", percentile(flight_cmd_slew.ravel(), 95), "fullscale/s"),
            ("velocity_norm_max_m_s", float(np.nanmax(np.linalg.norm(velocity, axis=1))), "m/s"),
            ("body_rate_norm_max_rad_s", float(np.nanmax(np.linalg.norm(pqr, axis=1))), "rad/s"),
        ]:
            add_metric(
                rows,
                row_type="pelican_motor_packet_flight_summary",
                name=name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=MAT_PATH,
                source_url=MAT_URL,
                evidence_role="flight_level_summary",
                note=source_note,
            )

        for motor_index in range(4):
            lag_samples, corr = best_derivative_lag(cmd[:, motor_index], actual[:, motor_index])
            fit = response_fit(cmd[:, motor_index], actual[:, motor_index], lag_samples)
            motor_name = f"{name}_motor_{motor_index + 1}"
            metrics = {
                "best_derivative_lag_samples_positive_actual_lags_cmd": lag_samples,
                "best_derivative_lag_ms_positive_actual_lags_cmd": lag_samples * DT_S * 1000.0,
                "best_derivative_lag_correlation": corr,
                **fit,
            }
            for metric, value in metrics.items():
                unit = "samples"
                if metric.endswith("_ms_positive_actual_lags_cmd"):
                    unit = "ms"
                elif metric.endswith("_correlation"):
                    unit = "corr"
                elif metric.endswith("_s"):
                    unit = "s"
                elif "gain" in metric:
                    unit = "actual_unit/cmd_unit"
                elif "offset" in metric or "rmse" in metric:
                    unit = "AscTec_unit"
                elif metric == "fit_samples_used":
                    unit = "samples"
                elif "alpha" in metric:
                    unit = "fraction/sample"
                add_metric(
                    rows,
                    row_type="pelican_motor_packet_motor_lag_fit",
                    name=motor_name,
                    metric=metric,
                    value=value,
                    unit=unit,
                    source_file=MAT_PATH,
                    source_url=MAT_URL,
                    evidence_role="command_actual_response_fit",
                    note="Lag uses derivative cross-correlation. First-order tau is fitted after a static actual ~= gain*command + offset map; treat it as system-ID response, not bare ESC tau.",
                )
            lags.append(float(lag_samples))
            correlations.append(float(corr))
            gains.append(float(fit.get("static_gain_actual_unit_per_cmd_unit", math.nan)))
            offsets.append(float(fit.get("static_offset_actual_unit", math.nan)))
            rmses.append(float(fit.get("static_map_rmse_actual_unit", math.nan)))
            alphas.append(float(fit.get("first_order_alpha_per_sample", math.nan)))
            taus.append(float(fit.get("first_order_tau_s", math.nan)))

    for name, metric_prefix, values, unit, note in [
        ("loaded_dataset", "actual_motor_unit", all_actual, "AscTec_unit", source_note),
        ("loaded_dataset", "cmd_motor_unit", all_cmd, "AscTec_unit", source_note),
        ("loaded_dataset", "actual_slew_fullscale_per_s", actual_slew_fullscale_per_s, "fullscale/s", source_note),
        ("loaded_dataset", "cmd_slew_fullscale_per_s", cmd_slew_fullscale_per_s, "fullscale/s", source_note),
        ("motor_fit_distribution", "lag_samples", lags, "samples", "Positive lag means actual motor-speed changes trail commanded-speed changes."),
        ("motor_fit_distribution", "lag_ms", [lag * DT_S * 1000.0 for lag in lags], "ms", "Positive lag means actual motor-speed changes trail commanded-speed changes."),
        ("motor_fit_distribution", "lag_correlation", correlations, "corr", "Derivative cross-correlation quality; low values should not be overfit."),
        ("motor_fit_distribution", "static_gain", gains, "actual_unit/cmd_unit", "Static fit after lag alignment; confirms command and actual fields are not one-to-one absolute RPM."),
        ("motor_fit_distribution", "static_offset", offsets, "AscTec_unit", "Static fit after lag alignment."),
        ("motor_fit_distribution", "static_rmse", rmses, "AscTec_unit", "Static fit residual after lag alignment."),
        ("motor_fit_distribution", "first_order_alpha", alphas, "fraction/sample", "Alpha fitted against static-map target; includes platform/data smoothing effects."),
        ("motor_fit_distribution", "first_order_tau", taus, "s", "Tau fitted against static-map target; use as system-ID response envelope, not bare ESC motor tau."),
    ]:
        add_summary_percentiles(
            rows,
            name=name,
            metric_prefix=metric_prefix,
            values=values,
            unit=unit,
            source_file=MAT_PATH,
            source_url=MAT_URL,
            evidence_role="distribution_summary",
            note=note,
        )

    tau_p50 = percentile(taus, 50)
    tau_p10 = percentile(taus, 10)
    tau_p90 = percentile(taus, 90)
    for metric, value, unit, note in [
        ("tau_p50_over_racingQuad_motor_tau", tau_p50 / RACINGQUAD_MOTOR_TAU_S, "ratio", "Pelican response is slower and smoothed; do not tune FPV ESC tau directly to this ratio."),
        ("tau_p10_over_racingQuad_motor_tau", tau_p10 / RACINGQUAD_MOTOR_TAU_S, "ratio", "Lower-bound system-ID response versus current racingQuad motor_tau."),
        ("tau_p90_over_racingQuad_motor_tau", tau_p90 / RACINGQUAD_MOTOR_TAU_S, "ratio", "Upper envelope includes platform/autopilot/smoothing effects."),
        ("tau_p50_over_apDrone_motor_tau", tau_p50 / APDRONE_MOTOR_TAU_S, "ratio", "Pelican response is slower and smoothed; do not tune FPV ESC tau directly to this ratio."),
        ("lag_p50_ms", percentile([lag * DT_S * 1000.0 for lag in lags], 50), "ms", "Median command-to-actual derivative lag."),
        ("lag_p90_ms", percentile([lag * DT_S * 1000.0 for lag in lags], 90), "ms", "Most fitted lags are within the 5-sample smoothing window."),
    ]:
        add_metric(
            rows,
            row_type="pelican_motor_packet_current_model_comparison",
            name="current_model_context",
            metric=metric,
            value=value,
            unit=unit,
            source_file=MAT_PATH,
            source_url=MAT_URL,
            evidence_role="current_model_context",
            note=note,
        )

    add_metric(
        rows,
        row_type="pelican_motor_packet_method",
        name="scope_caveat",
        metric="recommended_use",
        value=(
            "Use WAVELab Pelican as a clean in-flight command-to-actual response and "
            "system-ID prior. Do not use its AscTec motor-speed units as FPV mechanical "
            "RPM, and do not replace Betaflight/FPV blackbox or bench-step data with it."
        ),
        unit="text",
        source_file=MAT_PATH,
        source_url=README_URL,
        evidence_role="handoff_guidance",
        note=source_note,
    )

    write_csv(OUTPUT, rows)
    sync_summary(rows)
    print(f"Wrote {len(rows)} rows to {repo_path(OUTPUT)}")
    print("Synced pelican_motor_packet_* rows into fpv_model_validation_summary.csv")


if __name__ == "__main__":
    main()
