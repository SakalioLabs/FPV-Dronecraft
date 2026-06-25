"""Build a Tyto Robotics 5-inch static prop/motor packet.

Outputs:
  docs/data/tyto_5in_static_prop_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category
  tyto_5in_static_packet_*

Tyto's public database is useful for 5-inch FPV static RPM/thrust/current/
torque scale checks. The site itself labels the data as static performance
data, so this packet is not a forward-flight wind-tunnel substitute. Use it to
calibrate static propulsion scale, current draw, torque, and T/omega^2 ranges
beside UIUC/Mejzlik forward-flow evidence.
"""

from __future__ import annotations

import csv
import html
import json
import math
import re
import time
from pathlib import Path
from statistics import median
from typing import Iterable
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

OUTPUT = DATA / "tyto_5in_static_prop_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

BASE_URL = "https://database.tytorobotics.com"
HOME_URL = f"{BASE_URL}/"
PROPELLERS_URL = f"{BASE_URL}/propellers"
TEST_SEARCH_URL = f"{BASE_URL}/tests/search"
JAVA_CONFIG_SOURCE = "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneConfig.java"

USER_AGENT = "Mozilla/5.0"
RHO_ISA_KG_M3 = 1.225
G0 = 9.80665
SPEED_OF_SOUND_ISA_M_S = 340.292

RACING_QUAD_RADIUS_M = 0.0635
RACING_QUAD_DIAMETER_M = 2.0 * RACING_QUAD_RADIUS_M
RACING_QUAD_MAX_THRUST_N = 13.5
RACING_QUAD_THRUST_K = 1.45e-6
RACING_QUAD_MAX_RPM = math.sqrt(RACING_QUAD_MAX_THRUST_N / RACING_QUAD_THRUST_K) * 60.0 / (2.0 * math.pi)
RACING_QUAD_HOVER_THRUST_PER_MOTOR_N = 1.1 * G0 / 4.0
RACING_QUAD_PER_MOTOR_CURRENT_LIMIT_A = 22.5


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def request_text(url: str, retries: int = 2) -> str:
    headers = {"User-Agent": USER_AGENT, "X-Requested-With": "XMLHttpRequest"}
    last_error: Exception | None = None
    for attempt in range(retries + 1):
        try:
            with urlopen(Request(url, headers=headers), timeout=60) as response:
                return response.read().decode("utf-8", errors="replace")
        except Exception as exc:  # pragma: no cover - network resilience path
            last_error = exc
            if attempt < retries:
                time.sleep(0.5 * (attempt + 1))
    raise RuntimeError(f"failed to fetch {url}") from last_error


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
) -> None:
    rows.append(
        {
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
    )


def normalized_inches(value: object) -> float:
    if value is None:
        return math.nan
    try:
        number = float(value)
    except (TypeError, ValueError):
        return math.nan
    if 20.0 < number < 100.0:
        number /= 10.0
    return number


def extract_json_attr(html_text: str, attr_name: str) -> object:
    match = re.search(rf"{re.escape(attr_name)}='(.*?)'", html_text, re.S)
    if not match:
        raise LookupError(f"missing {attr_name}")
    return json.loads(html.unescape(match.group(1)))


def tyto_home_counts(home_html: str) -> dict[str, float]:
    counts = {}
    match = re.search(r"<h2>(\d+) electric propulsion systems tested</h2>", home_html)
    if match:
        counts["public_test_count"] = float(match.group(1))
    match = re.search(r"total of (\d+) data samples", home_html)
    if match:
        counts["public_sample_count"] = float(match.group(1))
    for name, key in [("electric brushless motors", "motor_count"), ("propellers for drone", "propeller_count"), ("electronic speed controllers", "esc_count")]:
        match = re.search(rf"-\s*(\d+)\s*<a [^>]*>{re.escape(name)}</a>", home_html)
        if match:
            counts[key] = float(match.group(1))
    return counts


def propeller_candidates(propellers_html: str) -> list[dict[str, object]]:
    components = extract_json_attr(propellers_html, ":components")
    candidates = []
    for component in components:
        measures = component.get("measures", {})
        diameter = normalized_inches(measures.get("diameter", {}).get("value"))
        pitch = normalized_inches(measures.get("pitch", {}).get("value"))
        benchmarks = component.get("benchmarks_count") or 0
        if benchmarks <= 0 or not math.isfinite(diameter) or not (4.8 <= diameter <= 5.2):
            continue
        candidates.append(
            {
                "propeller_hash": component.get("hash", ""),
                "propeller_title": component.get("title", ""),
                "propeller_brand": component.get("brand") or "",
                "propeller_name": component.get("name", ""),
                "propeller_link": component.get("link", ""),
                "propeller_diameter_in": diameter,
                "propeller_pitch_in": pitch,
                "propeller_weight_g": measures.get("weight", {}).get("value"),
                "propeller_material": component.get("stringAttributes", {}).get("material", {}).get("value") or "",
                "benchmarks_count": int(benchmarks),
            }
        )
    candidates.sort(key=lambda row: (str(row["propeller_title"]).lower(), row["propeller_hash"]))
    return candidates


def search_tests_for_propeller(propeller_hash: str) -> list[dict[str, object]]:
    params = {
        "draw": 1,
        "per_page": 100,
        "page": 1,
        "filters": json.dumps(
            {
                "conjunction": "AND",
                "filters": [
                    {
                        "field": "powertrains.propeller.common.hash",
                        "condition": {"operator": "=", "value": propeller_hash},
                    }
                ],
            }
        ),
        "relations": json.dumps(["creator", "powertrains.motor", "powertrains.propeller", "powertrains.esc"]),
        "aggregates": json.dumps({}),
        "order_by": json.dumps([["created_at", "desc"]]),
    }
    payload = json.loads(request_text(f"{TEST_SEARCH_URL}?{urlencode(params)}"))
    return list(payload.get("data", []))


def first_value(values: list[object], index: int) -> float:
    if index >= len(values):
        return math.nan
    value = values[index]
    if value is None:
        return math.nan
    try:
        return float(value)
    except (TypeError, ValueError):
        return math.nan


def parse_test_tables(test_html: str) -> list[dict[str, object]]:
    tables = []
    for match in re.finditer(r"<benchmark-data-table\b.*?:data='(.*?)'", test_html, re.S):
        data = json.loads(html.unescape(match.group(1)))
        sample_count = max((len(field.get("values", [])) for field in data.values()), default=0)
        tables.append({"data": data, "sample_count": sample_count})
    return tables


def component_value(powertrain: dict[str, object], component: str, *keys: str) -> object:
    obj: object = powertrain.get(component) or {}
    for key in keys:
        if not isinstance(obj, dict):
            return ""
        obj = obj.get(key, {})
    return obj if obj != {} else ""


def rows_for_test_sample(
    *,
    test: dict[str, object],
    propeller: dict[str, object],
    table: dict[str, object],
    table_index: int,
) -> list[dict[str, object]]:
    powertrains = list(test.get("powertrains") or [])
    powertrain = powertrains[0] if powertrains else {}
    data = table["data"]
    sample_count = int(table["sample_count"])
    rows = []
    diameter_m = float(propeller["propeller_diameter_in"]) * 0.0254
    radius_m = 0.5 * diameter_m
    for index in range(sample_count):
        values = {key: field.get("values", []) for key, field in data.items()}
        time_s = first_value(values.get("time", []), index)
        throttle = first_value(values.get("throttle", []), index)
        rpm = first_value(values.get("rotation_speed", []), index)
        thrust_kgf = first_value(values.get("thrust", []), index)
        torque_nm = first_value(values.get("torque", []), index)
        voltage_v = first_value(values.get("voltage", []), index)
        current_a = first_value(values.get("current", []), index)
        epower_w = first_value(values.get("epower", []), index)
        mpower_w = first_value(values.get("mpower", []), index)
        moteff_percent = first_value(values.get("moteff", []), index)
        propeff_gf_w = first_value(values.get("propeff", []), index)
        overalleff_gf_w = first_value(values.get("overalleff", []), index)

        omega = rpm * 2.0 * math.pi / 60.0 if math.isfinite(rpm) else math.nan
        rev_hz = rpm / 60.0 if math.isfinite(rpm) else math.nan
        thrust_n = thrust_kgf * G0 if math.isfinite(thrust_kgf) else math.nan
        derived_mpower_w = torque_nm * omega if math.isfinite(torque_nm) and math.isfinite(omega) else math.nan
        mpower_for_coeff = mpower_w if math.isfinite(mpower_w) else derived_mpower_w
        thrust_k = safe_ratio(thrust_n, omega * omega)
        tip_speed = omega * radius_m if math.isfinite(omega) else math.nan
        ct = safe_ratio(thrust_n, RHO_ISA_KG_M3 * rev_hz * rev_hz * diameter_m**4)
        cp = safe_ratio(mpower_for_coeff, RHO_ISA_KG_M3 * rev_hz**3 * diameter_m**5)
        torque_coeff = safe_ratio(torque_nm, RHO_ISA_KG_M3 * rev_hz * rev_hz * diameter_m**5)
        rows.append(
            {
                "row_type": "tyto_5in_static_packet_sample",
                "name": f"{test.get('hash')}_pt{table_index}_sample_{index:03d}",
                "test_hash": test.get("hash", ""),
                "test_title": test.get("title", ""),
                "test_link": test.get("link", ""),
                "created_at": test.get("created_at", ""),
                "creator_name": (test.get("creator") or {}).get("name", ""),
                "device": test.get("device", ""),
                "powertrain_index": table_index,
                "propeller_hash": propeller["propeller_hash"],
                "propeller_title": propeller["propeller_title"],
                "propeller_diameter_in": propeller["propeller_diameter_in"],
                "propeller_pitch_in": propeller["propeller_pitch_in"],
                "motor_title": component_value(powertrain, "motor", "title"),
                "motor_kv_rpm_per_v": component_value(powertrain, "motor", "measures", "kv_value", "value"),
                "esc_title": component_value(powertrain, "esc", "title"),
                "sample_index": index,
                "time_s": time_s,
                "throttle_us": throttle,
                "rotation_speed_rpm": rpm,
                "thrust_kgf": thrust_kgf,
                "thrust_n": thrust_n,
                "torque_nm": torque_nm,
                "voltage_v": voltage_v,
                "current_a": current_a,
                "electrical_power_w": epower_w,
                "mechanical_power_w": mpower_w,
                "motor_esc_efficiency_percent": moteff_percent,
                "propeller_efficiency_gf_per_w": propeff_gf_w,
                "overall_efficiency_gf_per_w": overalleff_gf_w,
                "omega_rad_s": omega,
                "tip_speed_m_s": tip_speed,
                "tip_mach_isa": safe_ratio(tip_speed, SPEED_OF_SOUND_ISA_M_S),
                "static_thrust_k_n_per_rad_s2": thrust_k,
                "static_ct": ct,
                "static_cp_from_mechanical_power": cp,
                "static_torque_coefficient": torque_coeff,
                "thrust_over_current_racing_max_per_rotor": safe_ratio(thrust_n, RACING_QUAD_MAX_THRUST_N),
                "rpm_over_current_racing_max": safe_ratio(rpm, RACING_QUAD_MAX_RPM),
                "current_over_current_racing_per_motor_limit": safe_ratio(current_a, RACING_QUAD_PER_MOTOR_CURRENT_LIMIT_A),
                "source_url": test.get("link", ""),
            }
        )
    return rows


def summarize_samples(sample_rows: list[dict[str, object]]) -> dict[str, float | str]:
    numeric_keys = [
        "thrust_n",
        "rotation_speed_rpm",
        "current_a",
        "voltage_v",
        "electrical_power_w",
        "mechanical_power_w",
        "torque_nm",
        "static_thrust_k_n_per_rad_s2",
        "static_ct",
        "static_cp_from_mechanical_power",
        "tip_mach_isa",
        "thrust_over_current_racing_max_per_rotor",
        "rpm_over_current_racing_max",
        "current_over_current_racing_per_motor_limit",
    ]
    out: dict[str, float | str] = {"sample_count": float(len(sample_rows))}
    for key in numeric_keys:
        values = [float(row[key]) for row in sample_rows if isinstance(row.get(key), (float, int)) and math.isfinite(float(row[key]))]
        if values:
            out[f"{key}_max"] = max(values)
            out[f"{key}_p50"] = median(values)
    positive_k = [
        float(row["static_thrust_k_n_per_rad_s2"])
        for row in sample_rows
        if isinstance(row.get("static_thrust_k_n_per_rad_s2"), (float, int))
        and math.isfinite(float(row["static_thrust_k_n_per_rad_s2"]))
        and float(row.get("thrust_n", math.nan)) > 0.25
        and float(row.get("rotation_speed_rpm", math.nan)) > 1000.0
    ]
    if positive_k:
        out["positive_static_thrust_k_p50"] = median(positive_k)
        out["positive_static_thrust_k_p10"] = percentile(positive_k, 10)
        out["positive_static_thrust_k_p90"] = percentile(positive_k, 90)
        out["positive_static_thrust_k_p50_over_current_racing_k"] = safe_ratio(median(positive_k), RACING_QUAD_THRUST_K)
    return out


def test_summary_rows(test_samples: dict[str, list[dict[str, object]]]) -> list[dict[str, object]]:
    rows = []
    for test_hash, samples in sorted(test_samples.items()):
        if not samples:
            continue
        first = samples[0]
        summary = summarize_samples(samples)
        for metric, value in summary.items():
            unit = {
                "sample_count": "count",
                "thrust_n_max": "N",
                "thrust_n_p50": "N",
                "rotation_speed_rpm_max": "rpm",
                "rotation_speed_rpm_p50": "rpm",
                "current_a_max": "A",
                "current_a_p50": "A",
                "voltage_v_max": "V",
                "voltage_v_p50": "V",
                "electrical_power_w_max": "W",
                "electrical_power_w_p50": "W",
                "mechanical_power_w_max": "W",
                "mechanical_power_w_p50": "W",
                "torque_nm_max": "N*m",
                "torque_nm_p50": "N*m",
                "static_thrust_k_n_per_rad_s2_max": "N/(rad/s)^2",
                "static_thrust_k_n_per_rad_s2_p50": "N/(rad/s)^2",
                "static_ct_max": "CT",
                "static_ct_p50": "CT",
                "static_cp_from_mechanical_power_max": "CP",
                "static_cp_from_mechanical_power_p50": "CP",
                "tip_mach_isa_max": "Mach",
                "tip_mach_isa_p50": "Mach",
                "thrust_over_current_racing_max_per_rotor_max": "ratio",
                "thrust_over_current_racing_max_per_rotor_p50": "ratio",
                "rpm_over_current_racing_max_max": "ratio",
                "rpm_over_current_racing_max_p50": "ratio",
                "current_over_current_racing_per_motor_limit_max": "ratio",
                "current_over_current_racing_per_motor_limit_p50": "ratio",
                "positive_static_thrust_k_p50": "N/(rad/s)^2",
                "positive_static_thrust_k_p10": "N/(rad/s)^2",
                "positive_static_thrust_k_p90": "N/(rad/s)^2",
                "positive_static_thrust_k_p50_over_current_racing_k": "ratio",
            }.get(metric, "")
            rows.append(
                {
                    "row_type": "tyto_5in_static_packet_test_summary",
                    "name": f"{first.get('test_title')} ({test_hash})",
                    "metric": metric,
                    "value": value,
                    "unit": unit,
                    "source_file": repo_path(OUTPUT),
                    "source_url": first.get("source_url", ""),
                    "evidence_role": "tyto_public_static_test_summary",
                    "note": "Tyto public database static test page parsed from embedded benchmark-data-table JSON.",
                }
            )
    return rows


def source_inventory_rows(
    home_counts: dict[str, float],
    candidates: list[dict[str, object]],
    searched_tests: list[dict[str, object]],
    parsed_samples: list[dict[str, object]],
) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    metrics = {
        **home_counts,
        "candidate_5in_propeller_count": float(len(candidates)),
        "candidate_5in_benchmark_count_from_propeller_list": float(sum(int(row["benchmarks_count"]) for row in candidates)),
        "search_api_unique_test_count": float(len({test.get("hash") for test in searched_tests})),
        "parsed_sample_count": float(len(parsed_samples)),
        "has_airspeed_or_wind_speed_column": 0.0,
        "static_only": 1.0,
        "current_racingQuad_max_thrust_per_rotor_n": RACING_QUAD_MAX_THRUST_N,
        "current_racingQuad_max_rpm": RACING_QUAD_MAX_RPM,
        "current_racingQuad_thrust_k_n_per_rad_s2": RACING_QUAD_THRUST_K,
    }
    for metric, value in metrics.items():
        unit = {
            "public_test_count": "count",
            "public_sample_count": "count",
            "motor_count": "count",
            "propeller_count": "count",
            "esc_count": "count",
            "candidate_5in_propeller_count": "count",
            "candidate_5in_benchmark_count_from_propeller_list": "count",
            "search_api_unique_test_count": "count",
            "parsed_sample_count": "count",
            "has_airspeed_or_wind_speed_column": "boolean",
            "static_only": "boolean",
            "current_racingQuad_max_thrust_per_rotor_n": "N",
            "current_racingQuad_max_rpm": "rpm",
            "current_racingQuad_thrust_k_n_per_rad_s2": "N/(rad/s)^2",
        }.get(metric, "")
        add_metric(
            rows,
            row_type="tyto_5in_static_packet_source_inventory",
            name="Tyto Robotics public static database",
            metric=metric,
            value=value,
            unit=unit,
            source_url=HOME_URL if metric.startswith("public_") else PROPELLERS_URL,
            evidence_role="source_inventory",
            note="Tyto pages expose static propeller test data; no wind-speed/airspeed column was parsed in this packet.",
        )
    return rows


def propeller_inventory_rows(candidates: list[dict[str, object]]) -> list[dict[str, object]]:
    rows = []
    for prop in candidates:
        rows.append(
            {
                "row_type": "tyto_5in_static_packet_propeller_inventory",
                "name": prop["propeller_title"],
                "propeller_hash": prop["propeller_hash"],
                "propeller_title": prop["propeller_title"],
                "propeller_brand": prop["propeller_brand"],
                "propeller_name": prop["propeller_name"],
                "propeller_diameter_in": prop["propeller_diameter_in"],
                "propeller_pitch_in": prop["propeller_pitch_in"],
                "propeller_weight_g": prop["propeller_weight_g"],
                "propeller_material": prop["propeller_material"],
                "benchmarks_count": prop["benchmarks_count"],
                "source_url": prop["propeller_link"],
                "source_file": repo_path(OUTPUT),
                "evidence_role": "tyto_5in_candidate_propeller",
                "note": "Propeller list candidate normalized to 4.8..5.2 inches diameter; pitch values above 20 are interpreted as shorthand tenths.",
            }
        )
    return rows


def summary_rows(
    *,
    candidates: list[dict[str, object]],
    test_samples: dict[str, list[dict[str, object]]],
    parsed_samples: list[dict[str, object]],
) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    per_test = []
    for samples in test_samples.values():
        if samples:
            summary = summarize_samples(samples)
            summary["test_title"] = samples[0].get("test_title", "")
            summary["test_hash"] = samples[0].get("test_hash", "")
            summary["source_url"] = samples[0].get("source_url", "")
            per_test.append(summary)
    max_thrusts = [float(row["thrust_n_max"]) for row in per_test if "thrust_n_max" in row]
    max_currents = [float(row["current_a_max"]) for row in per_test if "current_a_max" in row]
    k_p50s = [float(row["positive_static_thrust_k_p50"]) for row in per_test if "positive_static_thrust_k_p50" in row]
    reaching_current = [row for row in per_test if float(row.get("thrust_n_max", 0.0)) >= RACING_QUAD_MAX_THRUST_N]
    top = max(per_test, key=lambda row: float(row.get("thrust_n_max", -1.0))) if per_test else {}
    closest_k = min(
        per_test,
        key=lambda row: abs(float(row.get("positive_static_thrust_k_p50", math.inf)) - RACING_QUAD_THRUST_K),
    ) if k_p50s else {}
    metrics = {
        "candidate_5in_propeller_count": (float(len(candidates)), "count"),
        "unique_test_count": (float(len(test_samples)), "count"),
        "parsed_sample_count": (float(len(parsed_samples)), "count"),
        "tests_reaching_current_racingQuad_max_thrust_count": (float(len(reaching_current)), "count"),
        "test_max_thrust_n_p50": (percentile(max_thrusts, 50), "N"),
        "test_max_thrust_n_p90": (percentile(max_thrusts, 90), "N"),
        "test_max_current_a_p50": (percentile(max_currents, 50), "A"),
        "test_max_current_a_p90": (percentile(max_currents, 90), "A"),
        "positive_static_thrust_k_p50_of_test_medians": (percentile(k_p50s, 50), "N/(rad/s)^2"),
        "positive_static_thrust_k_p50_over_current_racing_k": (
            safe_ratio(percentile(k_p50s, 50), RACING_QUAD_THRUST_K),
            "ratio",
        ),
        "highest_test_max_thrust_n": (float(top.get("thrust_n_max", math.nan)) if top else math.nan, "N"),
        "highest_test_max_thrust_over_current_racing": (
            safe_ratio(float(top.get("thrust_n_max", math.nan)), RACING_QUAD_MAX_THRUST_N) if top else math.nan,
            "ratio",
        ),
        "highest_test_title": (top.get("test_title", "") if top else "", "text"),
        "highest_test_source_url": (top.get("source_url", "") if top else "", "url"),
        "closest_k_test_title": (closest_k.get("test_title", "") if closest_k else "", "text"),
        "closest_k_positive_static_thrust_k_p50": (
            float(closest_k.get("positive_static_thrust_k_p50", math.nan)) if closest_k else math.nan,
            "N/(rad/s)^2",
        ),
        "static_only_no_airspeed_or_wind_speed": (1.0, "boolean"),
    }
    for metric, (value, unit) in metrics.items():
        add_metric(
            rows,
            row_type="tyto_5in_static_packet_summary",
            name="Tyto 5-inch static prop handoff",
            metric=metric,
            value=value,
            unit=unit,
            source_url=str(value) if metric in {"highest_test_source_url"} else "",
            evidence_role="compact_tyto_5in_static_handoff",
            note="Use as static RPM/thrust/current/torque scale evidence; do not use as a wind-on-thrust-stand or forward-flight dataset.",
        )
    add_metric(
        rows,
        row_type="tyto_5in_static_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Tyto test pages are static performance tables. This packet has no airspeed/wind-speed column, so it "
            "can calibrate static thrust, current, torque, CT/CP scale, and T/omega^2 only. Use UIUC/Mejzlik/APC "
            "rows for advance-ratio shape and continue searching for true 5-inch wind-on-thrust-stand data."
        ),
        unit="text",
        source_url=HOME_URL,
        evidence_role="method_caveat",
        note="Static data are adjacent evidence, not a substitute for forward-flow calibration.",
    )
    return rows


def build_rows() -> list[dict[str, object]]:
    home_html = request_text(HOME_URL)
    propellers_html = request_text(PROPELLERS_URL)
    home_counts = tyto_home_counts(home_html)
    candidates = propeller_candidates(propellers_html)
    searched_tests: list[dict[str, object]] = []
    tests_by_hash: dict[str, dict[str, object]] = {}
    prop_by_hash = {str(row["propeller_hash"]): row for row in candidates}
    test_prop_hash: dict[str, str] = {}

    for prop in candidates:
        tests = search_tests_for_propeller(str(prop["propeller_hash"]))
        for test in tests:
            searched_tests.append(test)
            test_hash = str(test.get("hash", ""))
            tests_by_hash[test_hash] = test
            test_prop_hash[test_hash] = str(prop["propeller_hash"])

    parsed_samples: list[dict[str, object]] = []
    test_samples: dict[str, list[dict[str, object]]] = {}
    for test_hash, test in sorted(tests_by_hash.items()):
        source_url = str(test.get("link", ""))
        test_html = request_text(source_url)
        tables = parse_test_tables(test_html)
        propeller = prop_by_hash[test_prop_hash[test_hash]]
        samples_for_test: list[dict[str, object]] = []
        for table_index, table in enumerate(tables):
            table_rows = rows_for_test_sample(test=test, propeller=propeller, table=table, table_index=table_index)
            samples_for_test.extend(table_rows)
        parsed_samples.extend(samples_for_test)
        test_samples[test_hash] = samples_for_test

    rows: list[dict[str, object]] = []
    rows.extend(source_inventory_rows(home_counts, candidates, searched_tests, parsed_samples))
    rows.extend(propeller_inventory_rows(candidates))
    rows.extend(test_summary_rows(test_samples))
    rows.extend(parsed_samples)
    rows.extend(summary_rows(candidates=candidates, test_samples=test_samples, parsed_samples=parsed_samples))
    return rows


def sync_summary(packet_rows: Iterable[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("tyto_5in_static_packet_")]
    added: list[dict[str, str]] = []
    for row in packet_rows:
        added.append(
            {
                "category": str(row["row_type"]),
                "name": str(row["name"]),
                "metric": str(row.get("metric", row.get("sample_index", ""))),
                "value": value_text(row.get("value", row.get("thrust_n", ""))),
                "unit": str(row.get("unit", "N" if row.get("row_type") == "tyto_5in_static_packet_sample" else "")),
                "source": str(row.get("source_url") or row.get("test_link") or row.get("source_file", "")),
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
