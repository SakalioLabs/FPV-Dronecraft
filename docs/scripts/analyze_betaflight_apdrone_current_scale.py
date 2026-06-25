"""Audit APdrone Blackbox current units against Betaflight source code.

Outputs:
  docs/data/betaflight_apdrone_current_unit_reference.csv

This script keeps two separate questions visible:

1. What unit does Betaflight 4.5 write for the Blackbox `amperageLatest`
   field?
2. What current scaling makes APdrone's battery-autonomy logs consume a
   1500 mAh pack?

The first is source-code evidence. The second is a data-derived calibration
check, and it should not be treated as a direct sensor-unit definition.
"""

from __future__ import annotations

import csv
import math
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
OUTPUT = DATA / "betaflight_apdrone_current_unit_reference.csv"

APDRONE_RAW = DATA / "raw" / "apdrone_zgsvdtxnfh_v2"
BETAF_ROOT = DATA / "raw" / "betaflight_current_units"
FOXEER_RAW = DATA / "raw" / "foxeer_reaper_esc"
FOXEER_REAPER_ESC_HTML = FOXEER_RAW / "foxeer_reaper_f4_128k_65a_esc.html"

APDRONE_SOURCE_PAGE = "https://data.mendeley.com/datasets/zgsvdtxnfh/2"
APDRONE_DOI = "10.17632/zgsvdtxnfh.2"
FOXEER_REAPER_ESC_PAGE = "https://www.foxeer.com/foxeer-reaper-f4-128k-65a-bl32-4in1-9-40v-esc-30-5-30-5mm-m3-g-420"
PACK_CAPACITY_MAH = 1500.0
BETAF_CENTIAMP_RAW_PER_AMP = 100.0

BETAF_SOURCE_ROWS = [
    {
        "row_type": "betaflight_source_evidence",
        "source_scope": "current_meter_struct_unit",
        "source_name": "Betaflight 4.5.0 current.h",
        "source_url": "https://github.com/betaflight/betaflight/blob/4.5.0/src/main/sensors/current.h#L35-L78",
        "local_source_file": "docs/data/raw/betaflight_current_units/betaflight_betaflight_4.5.0_src_main_sensors_current.h",
        "line_hint": "39,72,76",
        "metric": "amperageLatest_unit",
        "value": "centiampere",
        "unit": "1/100 A",
        "note": "Betaflight declares current-meter amperage and amperageLatest as centiamps; ADC scale is documented as mV per 10 A.",
    },
    {
        "row_type": "betaflight_source_evidence",
        "source_scope": "adc_scale_formula",
        "source_name": "Betaflight 4.5.0 current.c",
        "source_url": "https://github.com/betaflight/betaflight/blob/4.5.0/src/main/sensors/current.c#L115-L131",
        "local_source_file": "docs/data/raw/betaflight_current_units/betaflight_betaflight_4.5.0_src_main_sensors_current.c",
        "line_hint": "115-130",
        "metric": "adc_to_centiamps_formula",
        "value": "centiAmps=(millivolts*10000/scale+offset)/10",
        "unit": "centiamp",
        "note": "For a fixed ADC voltage and offset, reported current is inversely proportional to ibata_scale/currentMeterScale.",
    },
    {
        "row_type": "betaflight_source_evidence",
        "source_scope": "mah_integration_formula",
        "source_name": "Betaflight 4.5.0 current.c",
        "source_url": "https://github.com/betaflight/betaflight/blob/4.5.0/src/main/sensors/current.c#L128-L131",
        "local_source_file": "docs/data/raw/betaflight_current_units/betaflight_betaflight_4.5.0_src_main_sensors_current.c",
        "line_hint": "128-131",
        "metric": "mah_drawn_formula",
        "value": "mAhDrawnF += amperageLatest*dt_us/(100*1000*3600)",
        "unit": "mAh",
        "note": "The denominator is consistent with amperageLatest in centiamps and dt in microseconds.",
    },
    {
        "row_type": "betaflight_source_evidence",
        "source_scope": "blackbox_field_definition",
        "source_name": "Betaflight 4.5.0 blackbox.c",
        "source_url": "https://github.com/betaflight/betaflight/blob/4.5.0/src/main/blackbox/blackbox.c#L206-L214",
        "local_source_file": "docs/data/raw/betaflight_current_units/betaflight_betaflight_4.5.0_src_main_blackbox_blackbox.c",
        "line_hint": "213",
        "metric": "blackbox_field",
        "value": "amperageLatest",
        "unit": "centiamp from current meter",
        "note": "Blackbox defines an amperageLatest field when ADC current metering is enabled.",
    },
    {
        "row_type": "betaflight_source_evidence",
        "source_scope": "blackbox_assignment",
        "source_name": "Betaflight 4.5.0 blackbox.c",
        "source_url": "https://github.com/betaflight/betaflight/blob/4.5.0/src/main/blackbox/blackbox.c#L1170-L1176",
        "local_source_file": "docs/data/raw/betaflight_current_units/betaflight_betaflight_4.5.0_src_main_blackbox_blackbox.c",
        "line_hint": "1175",
        "metric": "blackbox_value_source",
        "value": "getAmperageLatest()",
        "unit": "centiamp",
        "note": "The logged field is assigned directly from Betaflight's current-meter getter.",
    },
    {
        "row_type": "betaflight_source_evidence",
        "source_scope": "blackbox_header_scale",
        "source_name": "Betaflight 4.5.0 blackbox.c",
        "source_url": "https://github.com/betaflight/betaflight/blob/4.5.0/src/main/blackbox/blackbox.c#L1390-L1396",
        "local_source_file": "docs/data/raw/betaflight_current_units/betaflight_betaflight_4.5.0_src_main_blackbox_blackbox.c",
        "line_hint": "1395",
        "metric": "currentSensor_header",
        "value": "offset,scale",
        "unit": "ADC current-meter config",
        "note": "Blackbox writes the current-sensor offset and scale in the log header for ADC current metering.",
    },
    {
        "row_type": "betaflight_source_evidence",
        "source_scope": "blackbox_viewer_formatting",
        "source_name": "Betaflight Blackbox Log Viewer fields_presenter.js",
        "source_url": "https://github.com/betaflight/blackbox-log-viewer/blob/master/src/flightlog_fields_presenter.js#L1769-L1784",
        "local_source_file": "docs/data/raw/betaflight_current_units/betaflight_blackbox-log-viewer_master_src_flightlog_fields_presenter.js",
        "line_hint": "1769-1784",
        "metric": "viewer_display_formula_for_bf_3_1_7_plus",
        "value": "amps=value/100",
        "unit": "A",
        "note": "For Betaflight logs at or after 3.1.7, the viewer displays amperageLatest by dividing the logged value by 100.",
    },
]

BATTERY_SCENARIOS = [
    {
        "scenario": "max_power",
        "label": "Max Power Time",
        "archive_filename": "Max Power Time Flights.rar",
        "folder": APDRONE_RAW / "battery_autonomy" / "max_power_time_flights",
    },
    {
        "scenario": "normal_power",
        "label": "Normal Power Time",
        "archive_filename": "Normal Power Time Flights.rar",
        "folder": APDRONE_RAW / "battery_autonomy" / "normal_power_time_flights",
    },
]


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def finite_or_blank(value: str | int | float) -> str | int | float:
    if isinstance(value, float) and not math.isfinite(value):
        return ""
    return value


def percentile(values: list[float], q: float) -> float:
    if not values:
        return float("nan")
    ordered = sorted(values)
    position = (len(ordered) - 1) * q
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[int(position)]
    weight = position - lower
    return ordered[lower] * (1.0 - weight) + ordered[upper] * weight


def mean(values: list[float]) -> float:
    return sum(values) / len(values) if values else float("nan")


def strip_html(text: str) -> str:
    clean = re.sub(r"<[^>]+>", " ", text)
    clean = re.sub(r"\s+", " ", clean)
    return clean.strip()


def normalize_label(label: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", label.lower()).strip("_")


def parse_foxeer_reaper_esc_specs() -> dict[str, str]:
    if not FOXEER_REAPER_ESC_HTML.exists():
        return {}

    html = FOXEER_REAPER_ESC_HTML.read_text(encoding="utf-8", errors="ignore")
    specs: dict[str, str] = {}
    for match in re.finditer(
        r"<tr>\s*<td[^>]*>(.*?)</td>\s*<td[^>]*>(.*?)</td>\s*</tr>",
        html,
        re.IGNORECASE | re.DOTALL,
    ):
        label = strip_html(match.group(1))
        value = strip_html(match.group(2))
        if label and value:
            specs[normalize_label(label)] = value
    return specs


def build_foxeer_spec_rows(specs: dict[str, str]) -> list[dict[str, str | int | float]]:
    rows: list[dict[str, str | int | float]] = []
    metric_units = {
        "firmware": "",
        "continuous_burst_current": "A",
        "input_voltage": "V / cell count",
        "telemetry": "",
        "esc_programming": "",
        "current_scaling": "Betaflight ADC current scale",
    }
    metric_notes = {
        "current_scaling": "Official Foxeer ESC page lists this value; it is the direct hardware-scale anchor for APdrone's Reaper F4 65A ESC.",
        "continuous_burst_current": "Official page lists per-channel continuous and burst current.",
    }
    for metric, unit in metric_units.items():
        if metric not in specs:
            continue
        rows.append(
            {
                "row_type": "foxeer_esc_spec_evidence",
                "source_scope": "foxeer_reaper_f4_128k_65a_esc_official_page",
                "source_name": "Foxeer Reaper F4 128K 65A BL32 4in1 ESC",
                "source_url": FOXEER_REAPER_ESC_PAGE,
                "local_source_file": repo_path(FOXEER_REAPER_ESC_HTML),
                "metric": metric,
                "value": specs[metric],
                "unit": unit,
                "note": metric_notes.get(metric, "Official Foxeer product-page specification."),
            }
        )
    return rows


def parse_blackbox_csv(path: Path) -> tuple[dict[str, str], list[dict[str, str]]]:
    header_line = -1
    metadata: dict[str, str] = {}

    with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
        for line_no, line in enumerate(handle):
            if line.startswith('"loopIteration"') or line.startswith("loopIteration"):
                header_line = line_no
                break
            parsed = next(csv.reader([line]))
            if len(parsed) >= 2:
                metadata[parsed[0]] = parsed[1]

    if header_line < 0:
        raise RuntimeError(f"Could not find Blackbox data header in {path}")

    with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
        for _ in range(header_line):
            next(handle)
        rows = list(csv.DictReader(handle))

    return metadata, rows


def summarize_rows(
    rows: list[dict[str, str]],
) -> dict[str, float | int]:
    times_s: list[float] = []
    voltage_v: list[float] = []
    current_raw: list[float] = []
    raw_current_seconds = 0.0
    raw_voltage_current_seconds = 0.0
    last_t: float | None = None

    for row in rows:
        try:
            t_s = float(row["time"]) / 1e6
        except (KeyError, ValueError):
            continue

        try:
            vbat = float(row.get("vbatLatest", "nan")) / 100.0
        except ValueError:
            vbat = float("nan")
        try:
            amp_raw = float(row.get("amperageLatest", "nan"))
        except ValueError:
            amp_raw = float("nan")

        if math.isfinite(vbat):
            voltage_v.append(vbat)
        if math.isfinite(amp_raw):
            current_raw.append(amp_raw)

        if last_t is not None and math.isfinite(t_s):
            dt = t_s - last_t
            if 0.0 < dt < 2.0 and math.isfinite(amp_raw):
                raw_current_seconds += amp_raw * dt
                if math.isfinite(vbat):
                    raw_voltage_current_seconds += vbat * amp_raw * dt
        last_t = t_s
        times_s.append(t_s)

    duration_s = max(times_s) - min(times_s) if len(times_s) > 1 else float("nan")
    raw_capacity_mah = raw_current_seconds / 3.6
    raw_energy_wh = raw_voltage_current_seconds / 3600.0

    return {
        "sample_count": len(times_s),
        "duration_s": duration_s,
        "vbat_start_v": voltage_v[0] if voltage_v else float("nan"),
        "vbat_end_v": voltage_v[-1] if voltage_v else float("nan"),
        "vbat_mean_v": mean(voltage_v),
        "amperage_raw_mean": mean(current_raw),
        "amperage_raw_p95": percentile(current_raw, 0.95),
        "amperage_raw_max": max(current_raw) if current_raw else float("nan"),
        "raw_current_seconds": raw_current_seconds,
        "raw_voltage_current_seconds": raw_voltage_current_seconds,
        "raw_capacity_mah": raw_capacity_mah,
        "raw_energy_wh": raw_energy_wh,
    }


def add_scaling_fields(
    row: dict[str, str | int | float],
    summary: dict[str, float | int],
    current_meter_scale: float,
    target_capacity_mah: float,
    target_flight_count: int = 1,
) -> None:
    raw_capacity_mah = float(summary["raw_capacity_mah"])
    raw_energy_wh = float(summary["raw_energy_wh"])
    raw_mean = float(summary["amperage_raw_mean"])
    raw_p95 = float(summary["amperage_raw_p95"])
    raw_max = float(summary["amperage_raw_max"])

    target_total_capacity = target_capacity_mah * target_flight_count
    raw_per_amp_capacity_match = (
        raw_capacity_mah / target_total_capacity
        if target_total_capacity > 0.0 and raw_capacity_mah > 0.0
        else float("nan")
    )
    physical_to_bf_ratio = (
        BETAF_CENTIAMP_RAW_PER_AMP / raw_per_amp_capacity_match
        if raw_per_amp_capacity_match > 0.0
        else float("nan")
    )
    estimated_scale = (
        current_meter_scale / physical_to_bf_ratio
        if current_meter_scale > 0.0 and physical_to_bf_ratio > 0.0
        else float("nan")
    )

    for raw_per_amp in (10.0, 20.0, BETAF_CENTIAMP_RAW_PER_AMP):
        suffix = str(int(raw_per_amp))
        capacity_mah = raw_capacity_mah / raw_per_amp
        energy_wh = raw_energy_wh / raw_per_amp
        row[f"current_mean_a_if_raw_per_amp_{suffix}"] = raw_mean / raw_per_amp
        row[f"current_p95_a_if_raw_per_amp_{suffix}"] = raw_p95 / raw_per_amp
        row[f"current_max_a_if_raw_per_amp_{suffix}"] = raw_max / raw_per_amp
        row[f"capacity_mah_if_raw_per_amp_{suffix}"] = capacity_mah
        row[f"energy_wh_if_raw_per_amp_{suffix}"] = energy_wh
        row[f"capacity_mah_per_flight_if_raw_per_amp_{suffix}"] = capacity_mah / target_flight_count
        row[f"energy_wh_per_flight_if_raw_per_amp_{suffix}"] = energy_wh / target_flight_count

    row["capacity_energy_scope"] = "single_flight" if target_flight_count == 1 else f"total_of_{target_flight_count}_flights"
    row["target_pack_capacity_mah"] = target_capacity_mah
    row["raw_per_amp_to_match_1500mah_each_flight"] = raw_per_amp_capacity_match
    row["current_mean_a_at_capacity_match"] = raw_mean / raw_per_amp_capacity_match if raw_per_amp_capacity_match > 0.0 else float("nan")
    row["current_p95_a_at_capacity_match"] = raw_p95 / raw_per_amp_capacity_match if raw_per_amp_capacity_match > 0.0 else float("nan")
    row["betaflight_expected_raw_per_amp"] = BETAF_CENTIAMP_RAW_PER_AMP
    row["physical_to_betaflight_reported_current_ratio"] = physical_to_bf_ratio
    row["configured_current_meter_scale"] = current_meter_scale
    row["estimated_ibata_scale_for_capacity_match"] = estimated_scale
    row["configured_scale_over_estimated_scale"] = (
        current_meter_scale / estimated_scale if estimated_scale > 0.0 else float("nan")
    )


def build_apdrone_header_evidence(all_metadata: list[dict[str, str]]) -> dict[str, str | int | float]:
    firmware_versions = sorted({meta.get("firmwareVersion", "") for meta in all_metadata if meta.get("firmwareVersion")})
    firmware_revisions = sorted({meta.get("Firmware revision", "") for meta in all_metadata if meta.get("Firmware revision")})
    scales = sorted({meta.get("currentMeterScale", "") for meta in all_metadata if meta.get("currentMeterScale")})
    offsets = sorted({meta.get("currentMeterOffset", "") for meta in all_metadata if meta.get("currentMeterOffset")})

    return {
        "row_type": "apdrone_blackbox_header_evidence",
        "source_scope": "apdrone_battery_autonomy_blackbox_headers",
        "source_name": "APdrone Mendeley v2 battery-autonomy CSV headers",
        "source_url": APDRONE_SOURCE_PAGE,
        "doi": APDRONE_DOI,
        "checked_file_count": len(all_metadata),
        "firmware_versions": ";".join(firmware_versions),
        "firmware_revisions": ";".join(firmware_revisions),
        "unique_current_meter_scale": ";".join(scales),
        "unique_current_meter_offset": ";".join(offsets),
        "metric": "apdrone_logged_current_meter_scale",
        "value": ";".join(scales),
        "unit": "Betaflight currentMeterScale / ibata_scale",
        "note": "All parsed APdrone battery-autonomy Blackbox CSV headers report the same current-meter scale and offset.",
    }


def build_config_evidence() -> dict[str, str | int | float]:
    config_path = APDRONE_RAW / "betaflight_configuration_for_f722.txt"
    value = ""
    line_hint = ""
    for line_no, line in enumerate(config_path.read_text(encoding="utf-8", errors="ignore").splitlines(), start=1):
        stripped = line.strip()
        if stripped.startswith("set ibata_scale = "):
            value = stripped.rsplit("=", 1)[1].strip()
            line_hint = str(line_no)
            break

    return {
        "row_type": "apdrone_config_evidence",
        "source_scope": "apdrone_betaflight_cli_dump",
        "source_name": "APdrone Betaflight Configuration for F722.txt",
        "source_url": APDRONE_SOURCE_PAGE,
        "doi": APDRONE_DOI,
        "local_source_file": repo_path(config_path),
        "line_hint": line_hint,
        "metric": "ibata_scale",
        "value": value,
        "unit": "mV/10A Betaflight ADC scale",
        "note": "APdrone's Betaflight CLI dump uses the same current-sensor scale as the Blackbox CSV headers.",
    }


def summarize_apdrone_battery_logs() -> list[dict[str, str | int | float]]:
    rows: list[dict[str, str | int | float]] = []
    all_metadata: list[dict[str, str]] = []
    foxeer_specs = parse_foxeer_reaper_esc_specs()
    try:
        foxeer_current_scaling = float(foxeer_specs.get("current_scaling", "nan"))
    except ValueError:
        foxeer_current_scaling = float("nan")
    rows.extend(build_foxeer_spec_rows(foxeer_specs))

    for scenario in BATTERY_SCENARIOS:
        flight_rows: list[dict[str, str | int | float]] = []
        combined = {
            "sample_count": 0,
            "duration_s": 0.0,
            "raw_current_seconds": 0.0,
            "raw_voltage_current_seconds": 0.0,
            "raw_capacity_mah": 0.0,
            "raw_energy_wh": 0.0,
            "amperage_raw_mean": 0.0,
            "amperage_raw_p95": 0.0,
            "amperage_raw_max": 0.0,
        }
        weighted_raw_sum = 0.0
        weighted_p95_sum = 0.0
        max_raw = 0.0
        flight_count = 0
        current_meter_scales: list[float] = []
        current_meter_offsets: list[float] = []

        for path in sorted(Path(scenario["folder"]).glob("*.csv")):
            metadata, data_rows = parse_blackbox_csv(path)
            all_metadata.append(metadata)
            summary = summarize_rows(data_rows)
            flight_count += 1
            sample_count = int(summary["sample_count"])
            current_meter_scale = float(metadata.get("currentMeterScale", "nan"))
            current_meter_offset = float(metadata.get("currentMeterOffset", "nan"))
            if math.isfinite(current_meter_scale):
                current_meter_scales.append(current_meter_scale)
            if math.isfinite(current_meter_offset):
                current_meter_offsets.append(current_meter_offset)

            row: dict[str, str | int | float] = {
                "row_type": "apdrone_battery_flight_current_scale_check",
                "scenario": str(scenario["scenario"]),
                "label": str(scenario["label"]),
                "filename": path.name,
                "archive_filename": str(scenario["archive_filename"]),
                "source_url": APDRONE_SOURCE_PAGE,
                "doi": APDRONE_DOI,
                "local_source_file": repo_path(path),
                "firmware_version": metadata.get("firmwareVersion", ""),
                "firmware_revision": metadata.get("Firmware revision", ""),
                "current_meter_offset": metadata.get("currentMeterOffset", ""),
                "current_meter_scale": metadata.get("currentMeterScale", ""),
                "sample_count": sample_count,
                "duration_s": summary["duration_s"],
                "vbat_start_v": summary["vbat_start_v"],
                "vbat_end_v": summary["vbat_end_v"],
                "vbat_mean_v": summary["vbat_mean_v"],
                "amperage_raw_mean": summary["amperage_raw_mean"],
                "amperage_raw_p95": summary["amperage_raw_p95"],
                "amperage_raw_max": summary["amperage_raw_max"],
                "note": "Betaflight source says raw/100 A for amperageLatest; the capacity-match columns show the larger physical current needed to consume a 1500 mAh pack.",
            }
            add_scaling_fields(row, summary, current_meter_scale, PACK_CAPACITY_MAH, 1)
            rows.append({key: finite_or_blank(value) for key, value in row.items()})
            flight_rows.append(row)

            combined["sample_count"] += sample_count
            combined["duration_s"] += float(summary["duration_s"])
            combined["raw_current_seconds"] += float(summary["raw_current_seconds"])
            combined["raw_voltage_current_seconds"] += float(summary["raw_voltage_current_seconds"])
            combined["raw_capacity_mah"] += float(summary["raw_capacity_mah"])
            combined["raw_energy_wh"] += float(summary["raw_energy_wh"])
            weighted_raw_sum += float(summary["amperage_raw_mean"]) * sample_count
            weighted_p95_sum += float(summary["amperage_raw_p95"]) * sample_count
            max_raw = max(max_raw, float(summary["amperage_raw_max"]))

        if flight_count == 0:
            continue

        combined["amperage_raw_mean"] = weighted_raw_sum / combined["sample_count"] if combined["sample_count"] else float("nan")
        combined["amperage_raw_p95"] = weighted_p95_sum / combined["sample_count"] if combined["sample_count"] else float("nan")
        combined["amperage_raw_max"] = max_raw
        current_meter_scale = mean(current_meter_scales)
        current_meter_offset = mean(current_meter_offsets)

        row = {
            "row_type": "apdrone_battery_scenario_current_scale_summary",
            "scenario": str(scenario["scenario"]),
            "label": str(scenario["label"]),
            "archive_filename": str(scenario["archive_filename"]),
            "source_url": APDRONE_SOURCE_PAGE,
            "doi": APDRONE_DOI,
            "flight_count": flight_count,
            "current_meter_offset": current_meter_offset,
            "current_meter_scale": current_meter_scale,
            "sample_count": combined["sample_count"],
            "duration_s_total": combined["duration_s"],
            "duration_s_mean": combined["duration_s"] / flight_count,
            "amperage_raw_mean_weighted": combined["amperage_raw_mean"],
            "amperage_raw_p95_weighted_mean_of_flights": combined["amperage_raw_p95"],
            "amperage_raw_max": combined["amperage_raw_max"],
            "raw_capacity_mah_total_if_raw_per_amp_1": combined["raw_capacity_mah"],
            "raw_energy_wh_total_if_raw_per_amp_1": combined["raw_energy_wh"],
            "note": "Scenario row sums five separate battery flights; capacity-match assumes each flight corresponds to one 1500 mAh pack.",
        }
        add_scaling_fields(row, combined, current_meter_scale, PACK_CAPACITY_MAH, flight_count)
        rows.append({key: finite_or_blank(value) for key, value in row.items()})

        estimated_scale = float(row["estimated_ibata_scale_for_capacity_match"])
        if math.isfinite(foxeer_current_scaling) and foxeer_current_scaling > 0.0:
            comparison = {
                "row_type": "apdrone_foxeer_esc_scale_comparison",
                "source_scope": "apdrone_config_vs_foxeer_official_esc_spec",
                "source_name": "Foxeer Reaper F4 128K 65A BL32 4in1 ESC",
                "source_url": FOXEER_REAPER_ESC_PAGE,
                "local_source_file": repo_path(FOXEER_REAPER_ESC_HTML),
                "scenario": str(scenario["scenario"]),
                "label": str(scenario["label"]),
                "apdrone_configured_current_meter_scale": current_meter_scale,
                "foxeer_official_current_scaling": foxeer_current_scaling,
                "capacity_match_estimated_ibata_scale": estimated_scale,
                "configured_to_foxeer_scale_ratio": current_meter_scale / foxeer_current_scaling,
                "capacity_match_to_foxeer_scale_ratio": estimated_scale / foxeer_current_scaling,
                "physical_to_betaflight_reported_current_ratio": row["physical_to_betaflight_reported_current_ratio"],
                "raw_per_amp_to_match_1500mah_each_flight": row["raw_per_amp_to_match_1500mah_each_flight"],
                "metric": "current_meter_scale_consistency",
                "value": estimated_scale / foxeer_current_scaling,
                "unit": "capacity_match_scale / Foxeer_official_scale",
                "note": "Foxeer's official current scaling of 70 nearly matches the APdrone normal-power capacity-fit scale and is close to the max-power capacity-fit scale; APdrone's configured 400 is about 5.7x the official ESC scale.",
            }
            rows.append({key: finite_or_blank(value) for key, value in comparison.items()})

    rows.insert(0, build_config_evidence())
    rows.insert(1, build_apdrone_header_evidence(all_metadata))
    return rows


def write_csv(path: Path, rows: list[dict[str, str | int | float]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames: list[str] = []
    for row in rows:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)

    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    rows: list[dict[str, str | int | float]] = []
    rows.extend(BETAF_SOURCE_ROWS)
    rows.extend(summarize_apdrone_battery_logs())
    write_csv(OUTPUT, rows)
    print(f"Wrote {repo_path(OUTPUT)}")


if __name__ == "__main__":
    main()
