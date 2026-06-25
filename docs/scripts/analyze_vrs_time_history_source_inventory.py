"""Build a VRS time-history source inventory and timing packet.

Outputs:
  docs/data/vrs_time_history_source_inventory.csv
  docs/data/fpv_model_validation_summary.csv rows with category vrs_time_*

The existing VRS packet checks the normalized descent-rate band and thrust
loss/buffet amplitude. This packet adds the missing time dimension: public
source inventory, Shetty/Selig time-history metadata, Stack/Leishman model
rotor fluctuation timing, and conversions from "rotor revolutions" into the
current project's preset RPM ranges.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Callable, Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
OUTPUT = DATA / "vrs_time_history_source_inventory.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"
VRS_REFERENCE = DATA / "vrs_propwash_reference.csv"
VRS_PACKET = DATA / "vrs_propwash_calibration_packet.csv"

SHETTY_PAPER_URL = "https://m-selig.ae.illinois.edu/pubs/ShettySelig-2011-AIAA-2011-1254-LRN-VSR-Props.pdf"
SHETTY_IDEALS_URL = "https://www.ideals.illinois.edu/items/18490"
SHETTY_AIAA_ABSTRACT_URL = "https://experts.illinois.edu/en/publications/propeller-performance-in-vortex-ring-state/"
CAMBRIDGE_FLOW_URL = "https://www.cambridge.org/core/journals/flow/article/effects-of-rotor-separation-on-the-axial-descent-performance-of-dualrotor-configurations/BE7FE0D2E732E777CBD43F8E65CA0692"
NASA_STACK_NTRS_URL = "https://ntrs.nasa.gov/citations/20040000835"
NASA_STACK_PDF_URL = "https://ntrs.nasa.gov/api/citations/20040000835/downloads/20040000835.pdf"
NASA_BETZINA_NTRS_URL = "https://ntrs.nasa.gov/citations/20010062358"
NASA_BETZINA_PDF_URL = "https://ntrs.nasa.gov/api/citations/20010062358/downloads/20010062358.pdf"

# Stack/Leishman/Caradonna/Savas, NASA model-rotor VRS report.
STACK_ACQUISITION_MAX_REV = 500.0
STACK_PEAK_TO_PEAK_THRUST_FLUCTUATION_FRACTION = 0.95
STACK_CHARACTERISTIC_INTERVAL_REV_LOW = 20.0
STACK_CHARACTERISTIC_INTERVAL_REV_HIGH = 50.0


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def f(row: dict[str, str], key: str, default: float = math.nan) -> float:
    value = row.get(key, "")
    if value == "":
        return default
    return float(value)


def value_text(value: str | float) -> str:
    if isinstance(value, str):
        return value
    if not math.isfinite(value):
        return ""
    return f"{value:.12g}"


def add_metric(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: str | float,
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


def require_one(rows: list[dict[str, str]], predicate: Callable[[dict[str, str]], bool]) -> dict[str, str]:
    for row in rows:
        if predicate(row):
            return row
    raise LookupError("required source row not found")


def summary_value(summary_rows: list[dict[str, str]], category: str, name: str, metric: str) -> float:
    row = require_one(
        summary_rows,
        lambda r: r.get("category") == category and r.get("name") == name and r.get("metric") == metric,
    )
    return float(row["value"])


def packet_value(packet_rows: list[dict[str, str]], row_type: str, name: str, metric: str) -> float:
    row = require_one(
        packet_rows,
        lambda r: r.get("row_type") == row_type and r.get("name") == name and r.get("metric") == metric,
    )
    return float(row["value"])


def add_source_inventory(rows: list[dict[str, str]]) -> None:
    sources = [
        (
            "Shetty/Selig AIAA small-prop VRS paper",
            SHETTY_PAPER_URL,
            "direct_small_prop_amplitude",
            "Most relevant public prop-scale source found so far; paper carries steady curves, plotted time histories, and text amplitude bounds.",
        ),
        (
            "Shetty IDEALS thesis repository",
            SHETTY_IDEALS_URL,
            "direct_small_prop_time_history_metadata",
            "Open thesis/repository page confirms time-history coverage; no machine-readable raw time-series CSV was found in the public page.",
        ),
        (
            "Shetty/Selig AIAA abstract page",
            SHETTY_AIAA_ABSTRACT_URL,
            "direct_small_prop_public_metadata",
            "Publication landing page for title, authorship, tested prop scale, and reported fluctuation statement.",
        ),
        (
            "Cambridge Flow dual-rotor axial descent",
            CAMBRIDGE_FLOW_URL,
            "small_rotor_descent_band",
            "Small-rotor axial-descent VRS peak-band anchor for descent ratio and mean loss; not a time-history raw-data source.",
        ),
        (
            "NASA Stack model rotor VRS",
            NASA_STACK_NTRS_URL,
            "rotorcraft_time_scale_upper_bound",
            "Model rotor source with aperiodic VRS loads, acquisition length in rotor revolutions, characteristic intervals, and a large peak-to-peak upper bound.",
        ),
        (
            "NASA Betzina tiltrotor VRS",
            NASA_BETZINA_NTRS_URL,
            "tiltrotor_vrs_regime_context",
            "Tiltrotor small-scale wind-tunnel VRS context. Useful for regime and unsteady-load awareness, not direct FPV prop coefficient fitting.",
        ),
    ]
    for name, url, role, note in sources:
        for metric, value in [
            ("source_url", url),
            ("evidence_role", role),
            ("raw_machine_readable_time_series_located", "no" if "Shetty" in name else "not_applicable"),
        ]:
            add_metric(
                rows,
                row_type="vrs_time_source_inventory",
                name=name,
                metric=metric,
                value=value,
                unit="text",
                source_file=OUTPUT,
                source_url=url,
                evidence_role=role,
                note=note,
            )
    add_metric(
        rows,
        row_type="vrs_time_source_inventory",
        name="NASA Stack model rotor VRS",
        metric="pdf_url",
        value=NASA_STACK_PDF_URL,
        unit="text",
        source_file=OUTPUT,
        source_url=NASA_STACK_PDF_URL,
        evidence_role="rotorcraft_time_scale_upper_bound",
        note="PDF URL recorded for reproducible follow-up extraction.",
    )
    add_metric(
        rows,
        row_type="vrs_time_source_inventory",
        name="NASA Betzina tiltrotor VRS",
        metric="pdf_url",
        value=NASA_BETZINA_PDF_URL,
        unit="text",
        source_file=OUTPUT,
        source_url=NASA_BETZINA_PDF_URL,
        evidence_role="tiltrotor_vrs_regime_context",
        note="PDF URL recorded for follow-up extraction; this packet does not digitize Betzina figures.",
    )


def add_shetty_metadata(rows: list[dict[str, str]], reference_rows: list[dict[str, str]]) -> None:
    shetty = require_one(reference_rows, lambda r: r.get("row_type") == "reference_small_prop_vrs_dataset")
    duration_s = f(shetty, "time_history_duration_s")
    sample_rate_hz = f(shetty, "time_history_sample_rate_hz")
    lpf_hz = f(shetty, "time_history_lpf_hz")
    rpm = f(shetty, "rotational_speed_rpm")
    rev_hz = rpm / 60.0
    record_samples = duration_s * sample_rate_hz
    record_revolutions = duration_s * rev_hz
    max_half_amp = f(shetty, "max_thrust_fluctuation_half_amplitude_fraction")

    metrics = [
        ("tested_prop_count", f(shetty, "tested_prop_count"), "count"),
        ("prop_diameter_in_low", f(shetty, "prop_diameter_in_low"), "in"),
        ("prop_diameter_in_high", f(shetty, "prop_diameter_in_high"), "in"),
        ("advance_ratio_low", f(shetty, "advance_ratio_low"), "J"),
        ("advance_ratio_high", f(shetty, "advance_ratio_high"), "J"),
        ("rotational_speed_rpm", rpm, "rpm"),
        ("time_history_prop_count", f(shetty, "time_history_prop_count"), "count"),
        ("time_history_advance_ratio_count", f(shetty, "time_history_advance_ratio_count"), "count"),
        ("time_history_duration_s", duration_s, "s"),
        ("time_history_sample_rate_hz", sample_rate_hz, "Hz"),
        ("time_history_lpf_hz", lpf_hz, "Hz"),
        ("time_history_record_samples", record_samples, "samples"),
        ("time_history_raw_nyquist_hz", sample_rate_hz / 2.0, "Hz"),
        ("rotor_revolution_frequency_hz_at_4000rpm", rev_hz, "Hz"),
        ("samples_per_rotor_revolution", sample_rate_hz / rev_hz, "samples/rev"),
        ("time_history_record_revolutions", record_revolutions, "rev"),
        ("lpf_over_rotor_revolution_frequency", lpf_hz / rev_hz, "ratio"),
        ("text_max_half_amplitude_fraction", max_half_amp, "fraction"),
        ("text_peak_to_peak_if_symmetric_fraction", max_half_amp * 2.0, "fraction"),
    ]
    for metric, value, unit in metrics:
        add_metric(
            rows,
            row_type="vrs_time_shetty_time_history_metadata",
            name="Shetty/Selig time-history metadata",
            metric=metric,
            value=value,
            unit=unit,
            source_file=VRS_REFERENCE,
            source_url=SHETTY_PAPER_URL,
            evidence_role="direct_small_prop_time_history_metadata",
            note="Public small-prop VRS source; raw time-history arrays have not been located as open machine-readable files.",
        )


def add_stack_anchor(rows: list[dict[str, str]]) -> None:
    half_amp = STACK_PEAK_TO_PEAK_THRUST_FLUCTUATION_FRACTION / 2.0
    metrics = [
        ("acquisition_max_revolutions", STACK_ACQUISITION_MAX_REV, "rev"),
        ("peak_to_peak_thrust_fluctuation_over_mean", STACK_PEAK_TO_PEAK_THRUST_FLUCTUATION_FRACTION, "fraction"),
        ("equivalent_half_amplitude_if_symmetric", half_amp, "fraction"),
        ("characteristic_interval_revolutions_low", STACK_CHARACTERISTIC_INTERVAL_REV_LOW, "rev"),
        ("characteristic_interval_revolutions_high", STACK_CHARACTERISTIC_INTERVAL_REV_HIGH, "rev"),
    ]
    for metric, value, unit in metrics:
        add_metric(
            rows,
            row_type="vrs_time_stack_model_rotor_anchor",
            name="Stack/Leishman model rotor VRS",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=NASA_STACK_NTRS_URL,
            evidence_role="rotorcraft_time_scale_upper_bound",
            note="Rotorcraft-scale aperiodic VRS load source. Use for time-scale and upper-bound checks, not direct FPV prop coefficient fitting.",
        )


def add_current_amplitude_comparison(rows: list[dict[str, str]], packet_rows: list[dict[str, str]]) -> None:
    rq_buffet_percent = packet_value(
        packet_rows,
        "vrs_packet_summary",
        "vrs_propwash_summary",
        "racingQuad_peak_max_spin_buffet_half_amplitude_percent",
    )
    largest_digitized = packet_value(
        packet_rows,
        "vrs_packet_summary",
        "vrs_propwash_summary",
        "shetty_max_digitized_half_amplitude_fraction",
    )
    current_over_largest_digitized = packet_value(
        packet_rows,
        "vrs_packet_summary",
        "vrs_propwash_summary",
        "racingQuad_buffet_over_largest_shetty_digitized",
    )
    current_half_fraction = rq_buffet_percent / 100.0
    current_peak_to_peak = 2.0 * current_half_fraction
    shetty_text_half = 0.30
    stack_half = STACK_PEAK_TO_PEAK_THRUST_FLUCTUATION_FRACTION / 2.0
    metrics = [
        ("current_racingQuad_half_amplitude_fraction", current_half_fraction, "fraction"),
        ("current_racingQuad_peak_to_peak_if_symmetric_fraction", current_peak_to_peak, "fraction"),
        ("current_over_shetty_text_half_amplitude", current_half_fraction / shetty_text_half, "ratio"),
        ("current_over_shetty_text_peak_to_peak", current_peak_to_peak / (2.0 * shetty_text_half), "ratio"),
        ("current_over_largest_digitized_shetty_envelope", current_over_largest_digitized, "ratio"),
        ("largest_digitized_shetty_half_amplitude_fraction", largest_digitized, "fraction"),
        ("current_over_stack_half_amplitude_upper_bound", current_half_fraction / stack_half, "ratio"),
        (
            "current_over_stack_peak_to_peak_upper_bound",
            current_peak_to_peak / STACK_PEAK_TO_PEAK_THRUST_FLUCTUATION_FRACTION,
            "ratio",
        ),
    ]
    for metric, value, unit in metrics:
        add_metric(
            rows,
            row_type="vrs_time_current_amplitude_comparison",
            name="racingQuad VRS buffet amplitude",
            metric=metric,
            value=value,
            unit=unit,
            source_file=VRS_PACKET,
            source_url=SHETTY_PAPER_URL,
            evidence_role="current_vs_public_amplitude_bounds",
            note="Compares current VRS buffet amplitude against direct small-prop text/digitized anchors and a rotorcraft-scale upper bound.",
        )


def add_revolution_interval_conversions(rows: list[dict[str, str]], summary_rows: list[dict[str, str]]) -> None:
    preset_names = sorted(
        {
            row["name"]
            for row in summary_rows
            if row.get("category") == "timing_vibration" and row.get("metric") == "hover_rpm"
        }
    )
    for preset in preset_names:
        hover_rpm = summary_value(summary_rows, "timing_vibration", preset, "hover_rpm")
        max_rpm = summary_value(summary_rows, "timing_vibration", preset, "max_rpm")
        for state, rpm in [("hover", hover_rpm), ("max", max_rpm)]:
            rev_hz = rpm / 60.0
            for rev_count in [STACK_CHARACTERISTIC_INTERVAL_REV_LOW, STACK_CHARACTERISTIC_INTERVAL_REV_HIGH]:
                period_s = rev_count / rev_hz
                frequency_hz = 1.0 / period_s
                name = f"{preset}_{state}_{rev_count:.0f}rev_vrs_interval"
                for metric, value, unit in [
                    ("rotor_speed_rpm", rpm, "rpm"),
                    ("interval_revolutions", rev_count, "rev"),
                    ("interval_period_s", period_s, "s"),
                    ("interval_frequency_hz", frequency_hz, "Hz"),
                ]:
                    add_metric(
                        rows,
                        row_type="vrs_time_current_rev_interval_conversion",
                        name=name,
                        metric=metric,
                        value=value,
                        unit=unit,
                        source_file=SUMMARY,
                        source_url="drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneConfig.java",
                        evidence_role="current_rpm_time_scale_conversion",
                        note="Converts Stack/Leishman 20-50 rotor-revolution VRS intervals into current preset time/frequency ranges.",
                    )


def add_summary(rows: list[dict[str, str]], packet_rows: list[dict[str, str]], summary_rows: list[dict[str, str]]) -> None:
    rq_hover_rpm = summary_value(summary_rows, "timing_vibration", "racingQuad", "hover_rpm")
    rq_max_rpm = summary_value(summary_rows, "timing_vibration", "racingQuad", "max_rpm")
    rq_hover_rev_hz = rq_hover_rpm / 60.0
    rq_max_rev_hz = rq_max_rpm / 60.0
    current_half = packet_value(
        packet_rows,
        "vrs_packet_summary",
        "vrs_propwash_summary",
        "racingQuad_peak_max_spin_buffet_half_amplitude_percent",
    ) / 100.0
    stack_half = STACK_PEAK_TO_PEAK_THRUST_FLUCTUATION_FRACTION / 2.0
    shetty_half = 0.30
    hover_period_low_rev = STACK_CHARACTERISTIC_INTERVAL_REV_LOW / rq_hover_rev_hz
    hover_period_high_rev = STACK_CHARACTERISTIC_INTERVAL_REV_HIGH / rq_hover_rev_hz
    max_period_low_rev = STACK_CHARACTERISTIC_INTERVAL_REV_LOW / rq_max_rev_hz
    max_period_high_rev = STACK_CHARACTERISTIC_INTERVAL_REV_HIGH / rq_max_rev_hz

    metrics = [
        ("shetty_time_history_duration_s", 90.0, "s"),
        ("shetty_time_history_sample_rate_hz", 120.0, "Hz"),
        ("shetty_time_history_lpf_hz", 10.0, "Hz"),
        ("shetty_text_half_amplitude_fraction", shetty_half, "fraction"),
        ("stack_peak_to_peak_upper_bound_fraction", STACK_PEAK_TO_PEAK_THRUST_FLUCTUATION_FRACTION, "fraction"),
        ("stack_characteristic_interval_low_rev", STACK_CHARACTERISTIC_INTERVAL_REV_LOW, "rev"),
        ("stack_characteristic_interval_high_rev", STACK_CHARACTERISTIC_INTERVAL_REV_HIGH, "rev"),
        ("racingQuad_current_half_amplitude_fraction", current_half, "fraction"),
        ("racingQuad_current_peak_to_peak_fraction", current_half * 2.0, "fraction"),
        ("racingQuad_current_over_shetty_text_half", current_half / shetty_half, "ratio"),
        ("racingQuad_current_over_stack_half_upper_bound", current_half / stack_half, "ratio"),
        ("racingQuad_hover_stack_20rev_period_s", hover_period_low_rev, "s"),
        ("racingQuad_hover_stack_50rev_period_s", hover_period_high_rev, "s"),
        ("racingQuad_max_stack_20rev_period_s", max_period_low_rev, "s"),
        ("racingQuad_max_stack_50rev_period_s", max_period_high_rev, "s"),
        ("racingQuad_hover_stack_interval_frequency_high_hz", 1.0 / hover_period_low_rev, "Hz"),
        ("racingQuad_hover_stack_interval_frequency_low_hz", 1.0 / hover_period_high_rev, "Hz"),
        ("racingQuad_max_stack_interval_frequency_high_hz", 1.0 / max_period_low_rev, "Hz"),
        ("racingQuad_max_stack_interval_frequency_low_hz", 1.0 / max_period_high_rev, "Hz"),
    ]
    for metric, value, unit in metrics:
        add_metric(
            rows,
            row_type="vrs_time_summary",
            name="vrs_time_history_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            evidence_role="compact_handoff_summary",
            note="Compact VRS time-history handoff summary.",
        )

    add_metric(
        rows,
        row_type="vrs_time_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Treat VRS buffet as low-frequency aperiodic thrust/attitude disturbance tied to descent ratio and rotor "
            "revolution time-scale. Do not merge it with blade-pass motor vibration or with the earlier dirty-air "
            "propwash torque tune without FPV blackbox/wind-tunnel evidence."
        ),
        unit="text",
        source_file=OUTPUT,
        evidence_role="method_caveat",
        note="This packet adds timing and source traceability; it does not replace direct FPV propwash torque data.",
    )


def build_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    reference_rows = read_rows(VRS_REFERENCE)
    packet_rows = read_rows(VRS_PACKET)
    summary_rows = read_rows(SUMMARY)

    add_source_inventory(rows)
    add_shetty_metadata(rows, reference_rows)
    add_stack_anchor(rows)
    add_current_amplitude_comparison(rows, packet_rows)
    add_revolution_interval_conversions(rows, summary_rows)
    add_summary(rows, packet_rows, summary_rows)
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


def sync_summary(packet_rows: Iterable[dict[str, str]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("vrs_time_")]
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
    rows = build_rows()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
