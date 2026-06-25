"""Build a Tyto wind-tunnel forward-flow lead packet.

Outputs:
  docs/data/tyto_wind_tunnel_lead_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category
  tyto_wind_tunnel_lead_packet_*

Tyto's public 5-inch database pages are static, but Tyto also published a
wind-tunnel article with controlled airspeeds, RPM/thrust/power trends, and
test-stand instrumentation. This packet captures the article-level numbers and
maps them into J/mu terms so the coding agent can use it as a forward-flow lead
without mistaking it for raw 5-inch CSV data.
"""

from __future__ import annotations

import csv
import math
import re
import urllib.request
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

OUTPUT = DATA / "tyto_wind_tunnel_lead_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

ARTICLE_URL = "https://www.tytorobotics.com/blogs/articles/propeller-efficiency-testing-wind-speed"
UST_URL = "https://www.unmannedsystemstechnology.com/feature/wind-tunnel-study-on-drone-propeller-thrust-and-efficiency/"

AIR_DENSITY_KG_M3 = 1.225
RACING_5IN_DIAMETER_M = 0.127
RACING_5IN_RADIUS_M = RACING_5IN_DIAMETER_M / 2.0
RACING_HOVER_RPM = 13023.0
RACING_MAX_RPM = 29137.6327495
REFERENCE_LEVEL_SPEED_MPS = 12.5

TYTO_PROP_DIAMETER_M = 9.0 * 0.0254
TYTO_ARTICLE_RPM = 9000.0
TYTO_ARTICLE_N_REV_S = TYTO_ARTICLE_RPM / 60.0
TYTO_ARTICLE_WIND_SPEEDS_MPS = (0.0, 4.2, 7.5, 10.7, 14.0, 17.0)
TYTO_ARTICLE_THROTTLE_STEPS_US = (1550, 1700, 1850, 2000)
TYTO_THRUST_DECLINE_0_TO_17_AT_9000_RPM = 0.75
TYTO_POWER_DECLINE_0_TO_17_AT_9000_RPM = 0.19


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


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


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def request_text(url: str, timeout_s: float = 30.0) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": "codex-fpv-data-lead"})
    with urllib.request.urlopen(request, timeout=timeout_s) as response:
        return response.read().decode("utf-8", errors="replace")


def safe_ratio(numerator: float, denominator: float, min_denominator: float = 1.0e-12) -> float:
    if not math.isfinite(numerator) or not math.isfinite(denominator) or abs(denominator) < min_denominator:
        return math.nan
    return numerator / denominator


def advance_ratio_j(speed_mps: float, rpm: float, diameter_m: float) -> float:
    n_rev_s = rpm / 60.0
    return safe_ratio(speed_mps, n_rev_s * diameter_m)


def code_mu_from_j(j_value: float) -> float:
    return j_value / math.pi if math.isfinite(j_value) else math.nan


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_url: str,
    evidence_role: str,
    note: str = "",
    **extra: object,
) -> None:
    row = {
        "row_type": row_type,
        "name": name,
        "metric": metric,
        "value": value,
        "unit": unit,
        "source_file": repo_path(OUTPUT),
        "source_url": source_url,
        "evidence_role": evidence_role,
        "note": note,
    }
    row.update(extra)
    rows.append(row)


def add_source_and_setup_rows(rows: list[dict[str, object]], article_text: str) -> None:
    string_checks = {
        "article_mentions_0_to_38_mph": "0 to 38 mph" in article_text,
        "article_mentions_0_to_17_mps": "0 - 17 m/s" in article_text,
        "article_mentions_four_throttle_steps": "four throttle steps" in article_text,
        "article_mentions_thrust_declines_75_percent": bool(re.search(r"thrust declines by\s+75%", article_text)),
        "article_mentions_power_declines_19_percent": bool(re.search(r"power only declines by\s+19%", article_text)),
    }
    for metric, value in string_checks.items():
        add_metric(
            rows,
            row_type="tyto_wind_tunnel_lead_packet_source_check",
            name="Tyto wind-speed propeller article",
            metric=metric,
            value=1 if value else 0,
            unit="boolean",
            source_url=ARTICLE_URL,
            evidence_role="source_text_check",
            note="Boolean text check against the current Tyto article HTML.",
        )
    setup_metrics = {
        "propeller_diameter": (9.0, "inch"),
        "propeller_distance_from_windshaper": (2.10, "m"),
        "windshaper_fan_grid": ("2x2", "text"),
        "windshaper_fan_count": (36, "count"),
        "airspeed_condition_count": (len(TYTO_ARTICLE_WIND_SPEEDS_MPS), "count"),
        "throttle_step_count": (len(TYTO_ARTICLE_THROTTLE_STEPS_US), "count"),
        "comparison_rpm": (TYTO_ARTICLE_RPM, "rpm"),
        "measured_fields_include_thrust_torque_rpm_current_voltage_airspeed": (1, "boolean"),
    }
    for metric, (value, unit) in setup_metrics.items():
        add_metric(
            rows,
            row_type="tyto_wind_tunnel_lead_packet_setup",
            name="Tyto wind-speed test setup",
            metric=metric,
            value=value,
            unit=unit,
            source_url=ARTICLE_URL,
            evidence_role="article_setup",
            note="Article-level setup value; raw per-point table is not exposed.",
        )


def add_condition_rows(rows: list[dict[str, object]]) -> None:
    for speed in TYTO_ARTICLE_WIND_SPEEDS_MPS:
        tyto_j = advance_ratio_j(speed, TYTO_ARTICLE_RPM, TYTO_PROP_DIAMETER_M)
        racing_hover_j = advance_ratio_j(speed, RACING_HOVER_RPM, RACING_5IN_DIAMETER_M)
        racing_max_j = advance_ratio_j(speed, RACING_MAX_RPM, RACING_5IN_DIAMETER_M)
        add_metric(
            rows,
            row_type="tyto_wind_tunnel_lead_packet_speed_condition",
            name=f"wind_{speed:.1f}_mps",
            metric="wind_speed",
            value=speed,
            unit="m/s",
            source_url=ARTICLE_URL,
            evidence_role="article_speed_condition",
            note="One of six constant airspeed cases reported in the Tyto article.",
            wind_speed_mps=speed,
            wind_speed_mph=speed * 2.236936292,
            tyto_9in_j_at_9000rpm=tyto_j,
            tyto_9in_code_mu_at_9000rpm=code_mu_from_j(tyto_j),
            racing5in_j_at_hover_rpm=racing_hover_j,
            racing5in_code_mu_at_hover_rpm=code_mu_from_j(racing_hover_j),
            racing5in_j_at_max_rpm=racing_max_j,
            racing5in_code_mu_at_max_rpm=code_mu_from_j(racing_max_j),
        )


def add_throttle_rows(rows: list[dict[str, object]]) -> None:
    for index, throttle_us in enumerate(TYTO_ARTICLE_THROTTLE_STEPS_US):
        add_metric(
            rows,
            row_type="tyto_wind_tunnel_lead_packet_throttle_step",
            name=f"throttle_{throttle_us}_us",
            metric="throttle_step",
            value=throttle_us,
            unit="us",
            source_url=ARTICLE_URL,
            evidence_role="article_throttle_step",
            note="Automated step-test command in the article; no raw thrust/RPM row is exposed.",
            step_index=index,
        )


def add_summary_rows(rows: list[dict[str, object]]) -> None:
    max_speed = max(TYTO_ARTICLE_WIND_SPEEDS_MPS)
    tyto_j_17 = advance_ratio_j(max_speed, TYTO_ARTICLE_RPM, TYTO_PROP_DIAMETER_M)
    racing_hover_j_17 = advance_ratio_j(max_speed, RACING_HOVER_RPM, RACING_5IN_DIAMETER_M)
    racing_hover_j_12p5 = advance_ratio_j(REFERENCE_LEVEL_SPEED_MPS, RACING_HOVER_RPM, RACING_5IN_DIAMETER_M)
    thrust_retention = 1.0 - TYTO_THRUST_DECLINE_0_TO_17_AT_9000_RPM
    power_retention = 1.0 - TYTO_POWER_DECLINE_0_TO_17_AT_9000_RPM
    summary_metrics = {
        "wind_speed_condition_count": (len(TYTO_ARTICLE_WIND_SPEEDS_MPS), "count"),
        "max_article_wind_speed": (max_speed, "m/s"),
        "max_article_wind_speed_over_12p5_mps_reference": (max_speed / REFERENCE_LEVEL_SPEED_MPS, "ratio"),
        "tyto_9in_j_at_17mps_9000rpm": (tyto_j_17, "J"),
        "tyto_9in_code_mu_at_17mps_9000rpm": (code_mu_from_j(tyto_j_17), "mu"),
        "racing5in_j_at_12p5mps_hover_rpm": (racing_hover_j_12p5, "J"),
        "racing5in_j_at_17mps_hover_rpm": (racing_hover_j_17, "J"),
        "racing5in_code_mu_at_17mps_hover_rpm": (code_mu_from_j(racing_hover_j_17), "mu"),
        "article_thrust_retention_0_to_17mps_at_9000rpm": (thrust_retention, "ratio"),
        "article_power_retention_0_to_17mps_at_9000rpm": (power_retention, "ratio"),
        "article_thrust_power_ratio_retention_0_to_17mps_at_9000rpm": (
            safe_ratio(thrust_retention, power_retention),
            "ratio",
        ),
        "article_raw_numeric_table_available": (0, "boolean"),
        "needs_figure_digitization_or_raw_export": (1, "boolean"),
    }
    for metric, (value, unit) in summary_metrics.items():
        add_metric(
            rows,
            row_type="tyto_wind_tunnel_lead_packet_summary",
            name="Tyto wind tunnel forward-flow lead",
            metric=metric,
            value=value,
            unit=unit,
            source_url="" if metric not in {"article_raw_numeric_table_available"} else ARTICLE_URL,
            evidence_role="compact_tyto_wind_handoff",
            note="Use as dynamic forward-flow lead and ratio sanity check; raw graph/table digitization is still needed before fitting curves.",
        )
    add_metric(
        rows,
        row_type="tyto_wind_tunnel_lead_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Article reports a 9-inch propeller, six airspeeds from 0 to 17 m/s, four throttle steps, "
            "and 9000-rpm thrust/power trend ratios. It does not expose raw curve points or 5-inch "
            "FPV data, so use this packet as a forward-flow lead and digitization target."
        ),
        unit="text",
        source_url=ARTICLE_URL,
        evidence_role="method_caveat",
        note="Do not replace UIUC/APC/Mejzlik or true wind-on-thrust-stand CSV rows with this article-level packet.",
    )
    add_metric(
        rows,
        row_type="tyto_wind_tunnel_lead_packet_source_check",
        name="UST republication",
        metric="ust_article_present",
        value=1,
        unit="boolean",
        source_url=UST_URL,
        evidence_role="secondary_source",
        note="UST article mirrors the same 0..38 mph / 0..17 m/s Tyto wind-tunnel study lead.",
    )


def build_rows() -> list[dict[str, object]]:
    article_text = request_text(ARTICLE_URL)
    rows: list[dict[str, object]] = []
    add_source_and_setup_rows(rows, article_text)
    add_condition_rows(rows)
    add_throttle_rows(rows)
    add_summary_rows(rows)
    return rows


def sync_summary(packet_rows: Iterable[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("tyto_wind_tunnel_lead_packet_")]
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
