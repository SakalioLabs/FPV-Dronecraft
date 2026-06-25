"""Build a focused Shetty/Selig VRS digitization handoff packet.

Outputs:
  docs/data/vrs_shetty_digitization_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category
  vrs_digitization_packet_*

The older VRS packet already stores low-precision manual digitization from
Shetty/Selig Figs. 11-13 and a separate source/timing inventory. This script
turns those scattered rows into a model-calibration handoff: CT mean loss,
measured-envelope half-amplitude, J-to-V/vi/speed conversion, current-model
comparison, and conservative frequency proxies.
"""

from __future__ import annotations

import csv
import math
import re
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

OUTPUT = DATA / "vrs_shetty_digitization_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"
VRS_REFERENCE = DATA / "vrs_propwash_reference.csv"
VRS_PACKET = DATA / "vrs_propwash_calibration_packet.csv"
VRS_TIME = DATA / "vrs_time_history_source_inventory.csv"

SHETTY_PAPER_URL = "https://m-selig.ae.illinois.edu/pubs/ShettySelig-2011-AIAA-2011-1254-LRN-VSR-Props.pdf"
NASA_STACK_NTRS_URL = "https://ntrs.nasa.gov/citations/20040000835"
JAVA_PHYSICS_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java"

INCH_TO_M = 0.0254
TEXT_HALF_AMPLITUDE_FRACTION = 0.30
STACK_INTERVAL_REV_LOW = 20.0
STACK_INTERVAL_REV_HIGH = 50.0


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


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
        for row in row_list:
            writer.writerow({key: value_text(row.get(key, "")) for key in fieldnames})


def f(row: dict[str, str], key: str, default: float = math.nan) -> float:
    value = row.get(key, "")
    if value == "":
        return default
    return float(value)


def safe_ratio(numerator: float, denominator: float, min_denominator: float = 1.0e-12) -> float:
    if not math.isfinite(numerator) or not math.isfinite(denominator) or abs(denominator) < min_denominator:
        return math.nan
    return numerator / denominator


def percentile(values: Iterable[float], p: float) -> float:
    cleaned = sorted(value for value in values if math.isfinite(value))
    if not cleaned:
        return math.nan
    if len(cleaned) == 1:
        return cleaned[0]
    rank = (len(cleaned) - 1) * p / 100.0
    low = math.floor(rank)
    high = math.ceil(rank)
    if low == high:
        return cleaned[low]
    return cleaned[low] + (cleaned[high] - cleaned[low]) * (rank - low)


def require_one(rows: list[dict[str, str]], row_type: str, name: str | None = None, metric: str | None = None) -> dict[str, str]:
    for row in rows:
        if row.get("row_type") != row_type:
            continue
        if name is not None and row.get("name") != name:
            continue
        if metric is not None and row.get("metric") != metric:
            continue
        return row
    raise LookupError(f"missing row_type={row_type!r} name={name!r} metric={metric!r}")


def time_metric(rows: list[dict[str, str]], metric: str) -> float:
    row = require_one(rows, "vrs_time_shetty_time_history_metadata", "Shetty/Selig time-history metadata", metric)
    return float(row["value"])


def summary_metric(rows: list[dict[str, str]], metric: str) -> float:
    row = require_one(rows, "vrs_time_summary", "vrs_time_history_summary", metric)
    return float(row["value"])


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path,
    source_url: str = "",
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


def prop_diameter_in(prop_name: str) -> float:
    match = re.search(r"(\d+(?:\.\d+)?)x", prop_name)
    if not match:
        raise ValueError(f"cannot parse prop diameter from {prop_name!r}")
    return float(match.group(1))


def source_context_metrics(reference_rows: list[dict[str, str]], time_rows: list[dict[str, str]]) -> list[tuple[str, object, str]]:
    shetty = require_one(reference_rows, "reference_small_prop_vrs_dataset")
    digitized_rows = [row for row in reference_rows if row.get("row_type") == "shetty_selig_fig11_13_manual_digitization"]
    figures = sorted({row["figure"] for row in digitized_rows})
    props = sorted({row["prop"] for row in digitized_rows})
    return [
        ("source_tested_prop_count", f(shetty, "tested_prop_count"), "count"),
        ("source_prop_diameter_low_in", f(shetty, "prop_diameter_in_low"), "in"),
        ("source_prop_diameter_high_in", f(shetty, "prop_diameter_in_high"), "in"),
        ("source_advance_ratio_low_j", f(shetty, "advance_ratio_low"), "J"),
        ("source_advance_ratio_high_j", f(shetty, "advance_ratio_high"), "J"),
        ("source_rotational_speed_rpm", f(shetty, "rotational_speed_rpm"), "rpm"),
        ("source_wind_tunnel_speed_low_m_s", f(shetty, "wind_tunnel_speed_low_m_s"), "m/s"),
        ("source_wind_tunnel_speed_high_m_s", f(shetty, "wind_tunnel_speed_high_m_s"), "m/s"),
        ("source_steady_replicates_per_condition", f(shetty, "steady_replicates_per_condition"), "count"),
        ("source_steady_data_points_per_run", f(shetty, "steady_data_points_per_run"), "count"),
        ("source_time_history_duration_s", time_metric(time_rows, "time_history_duration_s"), "s"),
        ("source_time_history_sample_rate_hz", time_metric(time_rows, "time_history_sample_rate_hz"), "Hz"),
        ("source_time_history_lpf_hz", time_metric(time_rows, "time_history_lpf_hz"), "Hz"),
        ("source_text_half_amplitude_fraction", time_metric(time_rows, "text_max_half_amplitude_fraction"), "fraction"),
        ("digitized_figure_count", len(figures), "count"),
        ("digitized_point_count", len(digitized_rows), "count"),
        ("digitized_prop_count", len(props), "count"),
        ("digitized_figures", "; ".join(figures), "text"),
        ("digitized_props", "; ".join(props), "text"),
    ]


def add_source_context(
    rows: list[dict[str, object]],
    reference_rows: list[dict[str, str]],
    time_rows: list[dict[str, str]],
) -> None:
    for metric, value, unit in source_context_metrics(reference_rows, time_rows):
        add_metric(
            rows,
            row_type="vrs_digitization_packet_source",
            name="Shetty/Selig VRS source context",
            metric=metric,
            value=value,
            unit=unit,
            source_file=VRS_REFERENCE,
            source_url=SHETTY_PAPER_URL,
            evidence_role="direct_small_prop_vrs_source_context",
            note="Public small-prop VRS source context and current low-precision figure coverage.",
        )


def derived_digitization_row(row: dict[str, str], source_rpm: float) -> dict[str, float | str]:
    diameter_in = prop_diameter_in(row["prop"])
    diameter_m = diameter_in * INCH_TO_M
    rev_hz = source_rpm / 60.0
    n_d_m_s = rev_hz * diameter_m
    j = f(row, "advance_ratio_j")
    hover_ct = f(row, "hover_ct_digitized")
    mean_ct = f(row, "mean_ct_digitized")
    lower_ct = f(row, "lower_measured_ct_digitized")
    upper_ct = f(row, "upper_measured_ct_digitized")
    half_amp_ct = (upper_ct - lower_ct) / 2.0
    vi_over_nd = math.sqrt(2.0 * hover_ct / math.pi)
    hover_vi_m_s = vi_over_nd * n_d_m_s
    tunnel_speed_m_s = abs(j) * n_d_m_s
    mean_loss_fraction = safe_ratio(hover_ct - mean_ct, hover_ct)
    lower_loss_fraction = safe_ratio(hover_ct - lower_ct, hover_ct)
    upper_excursion_fraction = safe_ratio(upper_ct - hover_ct, hover_ct)
    half_over_mean = safe_ratio(half_amp_ct, mean_ct)
    peak_to_peak_over_mean = safe_ratio(upper_ct - lower_ct, mean_ct)
    return {
        "figure": row["figure"],
        "prop": row["prop"],
        "pitch_in": f(row, "pitch_in"),
        "diameter_in": diameter_in,
        "diameter_m": diameter_m,
        "advance_ratio_j": j,
        "advance_ratio_abs": abs(j),
        "source_rotational_speed_rpm": source_rpm,
        "rotor_revolution_frequency_hz": rev_hz,
        "nD_m_s": n_d_m_s,
        "tunnel_speed_m_s": tunnel_speed_m_s,
        "hover_vi_m_s": hover_vi_m_s,
        "vi_over_nD": vi_over_nd,
        "descent_ratio_vi_proxy": safe_ratio(tunnel_speed_m_s, hover_vi_m_s),
        "hover_ct_digitized": hover_ct,
        "mean_ct_digitized": mean_ct,
        "lower_measured_ct_digitized": lower_ct,
        "upper_measured_ct_digitized": upper_ct,
        "mean_ct_over_hover": safe_ratio(mean_ct, hover_ct),
        "mean_ct_loss_fraction_vs_hover": mean_loss_fraction,
        "lower_ct_loss_fraction_vs_hover": lower_loss_fraction,
        "upper_ct_excursion_fraction_vs_hover": upper_excursion_fraction,
        "measured_half_amplitude_ct": half_amp_ct,
        "measured_half_amplitude_over_hover_fraction": safe_ratio(half_amp_ct, hover_ct),
        "measured_half_amplitude_over_mean_fraction": half_over_mean,
        "measured_peak_to_peak_over_mean_fraction": peak_to_peak_over_mean,
        "measured_half_amplitude_over_text_30pct": safe_ratio(half_over_mean, TEXT_HALF_AMPLITUDE_FRACTION),
        "measured_peak_to_peak_over_text_60pct": safe_ratio(peak_to_peak_over_mean, TEXT_HALF_AMPLITUDE_FRACTION * 2.0),
    }


def add_digitized_envelope(
    rows: list[dict[str, object]],
    reference_rows: list[dict[str, str]],
) -> list[dict[str, float | str]]:
    source_rpm = f(require_one(reference_rows, "reference_small_prop_vrs_dataset"), "rotational_speed_rpm")
    derived: list[dict[str, float | str]] = []
    metric_units = [
        ("figure", "text"),
        ("prop", "text"),
        ("diameter_in", "in"),
        ("pitch_in", "in"),
        ("advance_ratio_j", "J"),
        ("advance_ratio_abs", "J"),
        ("tunnel_speed_m_s", "m/s"),
        ("hover_vi_m_s", "m/s"),
        ("vi_over_nD", "ratio"),
        ("descent_ratio_vi_proxy", "vi ratio"),
        ("hover_ct_digitized", "CT"),
        ("mean_ct_digitized", "CT"),
        ("lower_measured_ct_digitized", "CT"),
        ("upper_measured_ct_digitized", "CT"),
        ("mean_ct_over_hover", "ratio"),
        ("mean_ct_loss_fraction_vs_hover", "fraction"),
        ("lower_ct_loss_fraction_vs_hover", "fraction"),
        ("upper_ct_excursion_fraction_vs_hover", "fraction"),
        ("measured_half_amplitude_ct", "CT"),
        ("measured_half_amplitude_over_hover_fraction", "fraction"),
        ("measured_half_amplitude_over_mean_fraction", "fraction"),
        ("measured_peak_to_peak_over_mean_fraction", "fraction"),
        ("measured_half_amplitude_over_text_30pct", "ratio"),
        ("measured_peak_to_peak_over_text_60pct", "ratio"),
    ]
    for row in reference_rows:
        if row.get("row_type") != "shetty_selig_fig11_13_manual_digitization":
            continue
        item = derived_digitization_row(row, source_rpm)
        derived.append(item)
        name = f"{item['prop']} {item['figure']} J={item['advance_ratio_j']:.2f}"
        for metric, unit in metric_units:
            add_metric(
                rows,
                row_type="vrs_digitization_packet_envelope_point",
                name=name,
                metric=metric,
                value=item[metric],
                unit=unit,
                source_file=VRS_REFERENCE,
                source_url=SHETTY_PAPER_URL,
                evidence_role="low_precision_ct_envelope_digitization",
                note=(
                    "Derived from existing low-precision manual digitization of Shetty/Selig Figs. 11-13. "
                    "Use as measured-envelope bounds, not RMS or a standardized uncertainty interval."
                ),
            )
    return derived


def add_envelope_summary(rows: list[dict[str, object]], derived: list[dict[str, float | str]]) -> None:
    half_values = [float(row["measured_half_amplitude_over_mean_fraction"]) for row in derived]
    mean_losses = [float(row["mean_ct_loss_fraction_vs_hover"]) for row in derived]
    vrs_band = [
        row
        for row in derived
        if 1.0 <= float(row["descent_ratio_vi_proxy"]) <= 1.5
    ]
    max_half = max(derived, key=lambda row: float(row["measured_half_amplitude_over_mean_fraction"]))
    max_mean_loss = max(derived, key=lambda row: float(row["mean_ct_loss_fraction_vs_hover"]))
    max_lower_loss = max(derived, key=lambda row: float(row["lower_ct_loss_fraction_vs_hover"]))
    max_vrs_band_half = max(vrs_band, key=lambda row: float(row["measured_half_amplitude_over_mean_fraction"]))

    metrics: list[tuple[str, object, str]] = [
        ("digitized_point_count", len(derived), "count"),
        ("digitized_half_amplitude_fraction_min", min(half_values), "fraction"),
        ("digitized_half_amplitude_fraction_median", percentile(half_values, 50.0), "fraction"),
        ("digitized_half_amplitude_fraction_p90", percentile(half_values, 90.0), "fraction"),
        ("digitized_half_amplitude_fraction_max", float(max_half["measured_half_amplitude_over_mean_fraction"]), "fraction"),
        ("digitized_half_amplitude_max_row", f"{max_half['prop']} {max_half['figure']} J={max_half['advance_ratio_j']:.2f}", "text"),
        ("digitized_half_amplitude_max_v_over_vi", float(max_half["descent_ratio_vi_proxy"]), "vi ratio"),
        ("digitized_half_amplitude_max_tunnel_speed_m_s", float(max_half["tunnel_speed_m_s"]), "m/s"),
        ("digitized_half_amplitude_max_over_text_30pct", float(max_half["measured_half_amplitude_over_text_30pct"]), "ratio"),
        ("digitized_peak_to_peak_max_fraction", float(max_half["measured_peak_to_peak_over_mean_fraction"]), "fraction"),
        ("digitized_mean_ct_loss_fraction_min", min(mean_losses), "fraction"),
        ("digitized_mean_ct_loss_fraction_median", percentile(mean_losses, 50.0), "fraction"),
        ("digitized_mean_ct_loss_fraction_max", float(max_mean_loss["mean_ct_loss_fraction_vs_hover"]), "fraction"),
        ("digitized_mean_loss_max_row", f"{max_mean_loss['prop']} {max_mean_loss['figure']} J={max_mean_loss['advance_ratio_j']:.2f}", "text"),
        ("digitized_lower_ct_loss_fraction_max", float(max_lower_loss["lower_ct_loss_fraction_vs_hover"]), "fraction"),
        ("digitized_lower_loss_max_row", f"{max_lower_loss['prop']} {max_lower_loss['figure']} J={max_lower_loss['advance_ratio_j']:.2f}", "text"),
        ("points_in_1p0_to_1p5_vi_band", len(vrs_band), "count"),
        (
            "half_amplitude_median_in_1p0_to_1p5_vi_band",
            percentile([float(row["measured_half_amplitude_over_mean_fraction"]) for row in vrs_band], 50.0),
            "fraction",
        ),
        (
            "half_amplitude_max_in_1p0_to_1p5_vi_band",
            float(max_vrs_band_half["measured_half_amplitude_over_mean_fraction"]),
            "fraction",
        ),
        (
            "half_amplitude_max_row_in_1p0_to_1p5_vi_band",
            f"{max_vrs_band_half['prop']} {max_vrs_band_half['figure']} J={max_vrs_band_half['advance_ratio_j']:.2f}",
            "text",
        ),
        (
            "half_amplitude_above_text_30pct_count",
            sum(1 for value in half_values if value > TEXT_HALF_AMPLITUDE_FRACTION),
            "count",
        ),
    ]
    for metric, value, unit in metrics:
        add_metric(
            rows,
            row_type="vrs_digitization_packet_envelope_summary",
            name="Shetty/Selig digitized CT envelope summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=SHETTY_PAPER_URL,
            evidence_role="digitized_envelope_handoff_summary",
            note="Summary of low-precision measured-envelope digitization and derived nondimensional descent mapping.",
        )


def add_current_comparison(
    rows: list[dict[str, object]],
    reference_rows: list[dict[str, str]],
    time_rows: list[dict[str, str]],
) -> None:
    comparison_metrics = [
        ("reference_prop", "text"),
        ("reference_figure", "text"),
        ("reference_advance_ratio_j", "J"),
        ("descent_ratio_vi_proxy", "vi ratio"),
        ("reference_measured_half_amplitude_fraction", "fraction"),
        ("current_vrs_shape_no_crossflow_max_spin", "fraction"),
        ("current_vrs_base_thrust_loss_percent_max_spin", "percent"),
        ("current_vrs_buffet_half_amplitude_fraction_max_spin", "fraction"),
        ("current_buffet_over_reference_measured_half_amplitude", "ratio"),
        ("current_base_loss_over_cambridge_peak_loss_33pct", "ratio"),
    ]
    for row in reference_rows:
        if row.get("row_type") != "current_vs_shetty_selig_digitized_fluctuation":
            continue
        name = f"{row['preset']} vs {row['reference_prop']} {row['reference_figure']} J={row['reference_advance_ratio_j']}"
        reference_half = f(row, "reference_measured_half_amplitude_fraction")
        current_half = f(row, "current_vrs_buffet_half_amplitude_fraction_max_spin")
        for metric, unit in comparison_metrics:
            value: object
            if metric in {"reference_prop", "reference_figure"}:
                value = row[metric]
            else:
                value = f(row, metric)
            add_metric(
                rows,
                row_type="vrs_digitization_packet_current_comparison",
                name=name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=VRS_REFERENCE,
                source_url=JAVA_PHYSICS_SOURCE,
                evidence_role="current_model_vs_digitized_envelope",
                note="Current Java max-spin buffet envelope compared with the Shetty/Selig measured-envelope row at matching V/vi proxy.",
            )
        for metric, value, unit in [
            ("current_minus_reference_half_amplitude_fraction", current_half - reference_half, "fraction"),
            ("reference_over_current_half_amplitude", safe_ratio(reference_half, current_half), "ratio"),
            (
                "current_over_shetty_text_half_amplitude",
                safe_ratio(current_half, time_metric(time_rows, "text_max_half_amplitude_fraction")),
                "ratio",
            ),
        ]:
            add_metric(
                rows,
                row_type="vrs_digitization_packet_current_comparison",
                name=name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=VRS_REFERENCE,
                source_url=JAVA_PHYSICS_SOURCE,
                evidence_role="current_model_vs_digitized_envelope",
                note="Derived comparison metric; zero-current rows outside the VRS model band intentionally produce blank ratios.",
            )


def add_frequency_proxies(rows: list[dict[str, object]], time_rows: list[dict[str, str]]) -> None:
    rpm = time_metric(time_rows, "rotational_speed_rpm")
    rev_hz = rpm / 60.0
    stack_20_period = STACK_INTERVAL_REV_LOW / rev_hz
    stack_50_period = STACK_INTERVAL_REV_HIGH / rev_hz
    metrics = [
        ("shetty_sample_rate_hz", time_metric(time_rows, "time_history_sample_rate_hz"), "Hz", SHETTY_PAPER_URL),
        ("shetty_raw_nyquist_hz", time_metric(time_rows, "time_history_raw_nyquist_hz"), "Hz", SHETTY_PAPER_URL),
        ("shetty_lpf_hz", time_metric(time_rows, "time_history_lpf_hz"), "Hz", SHETTY_PAPER_URL),
        ("shetty_record_duration_s", time_metric(time_rows, "time_history_duration_s"), "s", SHETTY_PAPER_URL),
        ("shetty_record_samples", time_metric(time_rows, "time_history_record_samples"), "samples", SHETTY_PAPER_URL),
        ("shetty_rotor_rev_frequency_hz_at_4000rpm", rev_hz, "Hz", SHETTY_PAPER_URL),
        ("shetty_samples_per_rotor_revolution", time_metric(time_rows, "samples_per_rotor_revolution"), "samples/rev", SHETTY_PAPER_URL),
        ("stack_20rev_period_at_shetty_4000rpm_s", stack_20_period, "s", NASA_STACK_NTRS_URL),
        ("stack_50rev_period_at_shetty_4000rpm_s", stack_50_period, "s", NASA_STACK_NTRS_URL),
        ("stack_20rev_frequency_at_shetty_4000rpm_hz", 1.0 / stack_20_period, "Hz", NASA_STACK_NTRS_URL),
        ("stack_50rev_frequency_at_shetty_4000rpm_hz", 1.0 / stack_50_period, "Hz", NASA_STACK_NTRS_URL),
        ("shetty_lpf_over_stack_20rev_frequency", time_metric(time_rows, "time_history_lpf_hz") / (1.0 / stack_20_period), "ratio", NASA_STACK_NTRS_URL),
        ("shetty_sample_rate_over_stack_20rev_frequency", time_metric(time_rows, "time_history_sample_rate_hz") / (1.0 / stack_20_period), "ratio", NASA_STACK_NTRS_URL),
        ("racingQuad_hover_stack_20rev_period_s", summary_metric(time_rows, "racingQuad_hover_stack_20rev_period_s"), "s", repo_path(VRS_TIME)),
        ("racingQuad_hover_stack_50rev_period_s", summary_metric(time_rows, "racingQuad_hover_stack_50rev_period_s"), "s", repo_path(VRS_TIME)),
        ("racingQuad_max_stack_20rev_period_s", summary_metric(time_rows, "racingQuad_max_stack_20rev_period_s"), "s", repo_path(VRS_TIME)),
        ("racingQuad_max_stack_50rev_period_s", summary_metric(time_rows, "racingQuad_max_stack_50rev_period_s"), "s", repo_path(VRS_TIME)),
        ("racingQuad_hover_stack_frequency_low_hz", summary_metric(time_rows, "racingQuad_hover_stack_interval_frequency_low_hz"), "Hz", repo_path(VRS_TIME)),
        ("racingQuad_hover_stack_frequency_high_hz", summary_metric(time_rows, "racingQuad_hover_stack_interval_frequency_high_hz"), "Hz", repo_path(VRS_TIME)),
        ("racingQuad_max_stack_frequency_low_hz", summary_metric(time_rows, "racingQuad_max_stack_interval_frequency_low_hz"), "Hz", repo_path(VRS_TIME)),
        ("racingQuad_max_stack_frequency_high_hz", summary_metric(time_rows, "racingQuad_max_stack_interval_frequency_high_hz"), "Hz", repo_path(VRS_TIME)),
    ]
    for metric, value, unit, url in metrics:
        add_metric(
            rows,
            row_type="vrs_digitization_packet_frequency_proxy",
            name="VRS low-frequency timing proxy",
            metric=metric,
            value=value,
            unit=unit,
            source_file=VRS_TIME,
            source_url=url,
            evidence_role="time_history_and_rotor_revolution_frequency_proxy",
            note=(
                "Frequency proxy only: Shetty/Selig gives sampling/filtering metadata, while Stack/Leishman gives "
                "20-50 rotor-revolution VRS timing. Do not treat these as a fitted spectral peak."
            ),
        )


def add_packet_summary(
    rows: list[dict[str, object]],
    derived: list[dict[str, float | str]],
    time_rows: list[dict[str, str]],
) -> None:
    max_half = max(derived, key=lambda row: float(row["measured_half_amplitude_over_mean_fraction"]))
    current_half = summary_metric(time_rows, "racingQuad_current_half_amplitude_fraction")
    stack_half = 0.475
    metrics = [
        ("handoff_digitized_rows", len(derived), "count"),
        ("handoff_max_digitized_half_amplitude_fraction", float(max_half["measured_half_amplitude_over_mean_fraction"]), "fraction"),
        ("handoff_max_digitized_v_over_vi", float(max_half["descent_ratio_vi_proxy"]), "vi ratio"),
        ("handoff_max_digitized_row", f"{max_half['prop']} {max_half['figure']} J={max_half['advance_ratio_j']:.2f}", "text"),
        ("handoff_text_half_amplitude_fraction", TEXT_HALF_AMPLITUDE_FRACTION, "fraction"),
        ("handoff_current_racingQuad_half_amplitude_fraction", current_half, "fraction"),
        ("handoff_current_over_text_half_amplitude", current_half / TEXT_HALF_AMPLITUDE_FRACTION, "ratio"),
        (
            "handoff_current_over_largest_digitized_envelope",
            current_half / float(max_half["measured_half_amplitude_over_mean_fraction"]),
            "ratio",
        ),
        ("handoff_current_over_stack_half_upper_bound", current_half / stack_half, "ratio"),
        ("handoff_frequency_proxy_racingQuad_hover_low_hz", summary_metric(time_rows, "racingQuad_hover_stack_interval_frequency_low_hz"), "Hz"),
        ("handoff_frequency_proxy_racingQuad_hover_high_hz", summary_metric(time_rows, "racingQuad_hover_stack_interval_frequency_high_hz"), "Hz"),
        ("handoff_frequency_proxy_racingQuad_max_low_hz", summary_metric(time_rows, "racingQuad_max_stack_interval_frequency_low_hz"), "Hz"),
        ("handoff_frequency_proxy_racingQuad_max_high_hz", summary_metric(time_rows, "racingQuad_max_stack_interval_frequency_high_hz"), "Hz"),
    ]
    for metric, value, unit in metrics:
        add_metric(
            rows,
            row_type="vrs_digitization_packet_summary",
            name="Shetty/Selig VRS digitization handoff",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=SHETTY_PAPER_URL,
            evidence_role="compact_vrs_digitization_handoff",
            note="Compact handoff for model calibration and review.",
        )

    add_metric(
        rows,
        row_type="vrs_digitization_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Use the digitized Shetty/Selig rows to split VRS mean CT loss, measured-envelope half-amplitude, "
            "and J/V/vi mapping. Use the timing rows only as low-frequency proxies. The packet does not provide "
            "raw time histories, RMS statistics, or direct FPV propwash torque coefficients."
        ),
        unit="text",
        source_file=OUTPUT,
        source_url=SHETTY_PAPER_URL,
        evidence_role="method_caveat",
        note="Keeps thrust-loss, buffet amplitude, frequency, and propwash torque semantics separate.",
    )


def build_rows() -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    reference_rows = read_rows(VRS_REFERENCE)
    time_rows = read_rows(VRS_TIME)
    # Keep this dependency explicit; it must exist because the time packet derives
    # the current racingQuad amplitude from it.
    if not VRS_PACKET.exists():
        raise FileNotFoundError(repo_path(VRS_PACKET))

    add_source_context(rows, reference_rows, time_rows)
    derived = add_digitized_envelope(rows, reference_rows)
    add_envelope_summary(rows, derived)
    add_current_comparison(rows, reference_rows, time_rows)
    add_frequency_proxies(rows, time_rows)
    add_packet_summary(rows, derived, time_rows)
    return rows


def sync_summary(packet_rows: Iterable[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("vrs_digitization_packet_")]
    added: list[dict[str, str]] = []
    for row in packet_rows:
        added.append(
            {
                "category": str(row["row_type"]),
                "name": str(row["name"]),
                "metric": str(row["metric"]),
                "value": value_text(row["value"]),
                "unit": str(row["unit"]),
                "source": str(row.get("source_url") or row.get("source_file", "")),
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
