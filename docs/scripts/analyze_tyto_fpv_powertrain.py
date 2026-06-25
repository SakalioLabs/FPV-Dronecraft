"""Extract selected Tyto Robotics static FPV powertrain tests.

Outputs:
  docs/data/tyto_fpv_static_powertrain_reference.csv
  docs/data/tyto_fpv_static_torque_ratio_reference.csv

The Tyto Robotics Database pages embed public benchmark data in the rendered
HTML. This script caches a small selected set of FPV-relevant static tests,
normalizes thrust/RPM/current/voltage rows, fits T = k * omega^2, and compares
the fit against the current apDrone preset when that generated CSV is present.
It also derives measured torque/thrust ratio Q/T for yaw torque coefficient
sanity checks.
"""

from __future__ import annotations

import csv
import html
import json
import math
import re
import time
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "tyto_fpv_static_powertrain"
OUTPUT = DATA / "tyto_fpv_static_powertrain_reference.csv"
TORQUE_OUTPUT = DATA / "tyto_fpv_static_torque_ratio_reference.csv"
SOURCE_HOME = "https://database.tytorobotics.com"
HEADERS = {"User-Agent": "Mozilla/5.0 fpv-sim-data-validation"}

SELECTED_TESTS = [
    {
        "hash": "x3nm",
        "slug": "five33-2207-azure-vanover",
        "url": "https://database.tytorobotics.com/tests/x3nm/five33-2207-azure-vanover",
        "class_note": "Strongest selected FPV-like source: 2207 1980KV motor and Azure Vanover prop on about 6S voltage.",
    },
    {
        "hash": "dnq",
        "slug": "lf40-2305-with-5040-prop",
        "url": "https://database.tytorobotics.com/tests/dnq/lf40-2305-with-5040-prop",
        "class_note": "5-inch-class 2305/5040 static test; lower voltage and lower thrust than APdrone-sized 2507.",
    },
    {
        "hash": "69k7",
        "slug": "tmotor-f80",
        "url": "https://database.tytorobotics.com/tests/69k7/tmotor-f80",
        "class_note": "FPV motor test page with complete torque/current data but missing explicit component metadata.",
    },
    {
        "hash": "q3xn",
        "slug": "1700kvlumenierfolding",
        "url": "https://database.tytorobotics.com/tests/q3xn/1700kvlumenierfolding",
        "class_note": "1700KV static test with complete torque/current data; prop and motor metadata are not explicit on the page.",
    },
]


def repo_path(path: Path) -> str:
    return str(path.relative_to(ROOT)).replace("\\", "/")


def fetch_text(url: str, destination: Path) -> str:
    if destination.exists():
        return destination.read_text(encoding="utf-8", errors="ignore")
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers=HEADERS)
    last_error: Exception | None = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                text = response.read().decode("utf-8", errors="replace")
            destination.write_text(text, encoding="utf-8")
            return text
        except Exception as exc:  # pragma: no cover - network failure path
            last_error = exc
            time.sleep(0.75 * (attempt + 1))
    assert last_error is not None
    raise last_error


def strip_tags(value: str) -> str:
    value = re.sub(r"<[^>]+>", " ", value)
    value = html.unescape(value)
    return re.sub(r"\s+", " ", value).strip()


def parse_component(page_html: str, label: str) -> str:
    match = re.search(
        rf"{re.escape(label)}:&nbsp;(?P<body>.*?)(?:</div>\s*</div>)",
        page_html,
        re.DOTALL,
    )
    if not match:
        return ""
    return strip_tags(match.group("body"))


def parse_benchmark_table(page_html: str) -> tuple[str, str, dict[str, object]]:
    table_match = re.search(
        r"<benchmark-data-table\b(?P<attrs>.*?)</benchmark-data-table>",
        page_html,
        re.DOTALL,
    )
    if not table_match:
        raise RuntimeError("Could not locate benchmark-data-table")
    attrs = table_match.group("attrs")
    title_match = re.search(r'benchmark-title="(?P<title>[^"]*)"', attrs)
    creator_match = re.search(r'benchmark-creator="(?P<creator>[^"]*)"', attrs)
    data_match = re.search(r":data='(?P<data>\{.*?\})'", attrs, re.DOTALL)
    if not data_match:
        raise RuntimeError("Could not locate :data JSON")
    data = json.loads(html.unescape(data_match.group("data")))
    return (
        html.unescape(title_match.group("title")) if title_match else "",
        html.unescape(creator_match.group("creator")) if creator_match else "",
        data,
    )


def series_values(data: dict[str, object], key: str) -> list[float | None]:
    if key not in data:
        return []
    values = data[key]["values"]  # type: ignore[index]
    return [None if value is None else float(value) for value in values]  # type: ignore[arg-type]


def safe_get(values: list[float | None], index: int) -> float:
    if index >= len(values) or values[index] is None:
        return float("nan")
    return float(values[index])


def finite_or_blank(value: object) -> object:
    if isinstance(value, float) and not math.isfinite(value):
        return ""
    return value


def fit_thrust_coefficient(samples: list[dict[str, str | float | int]]) -> dict[str, float]:
    points = []
    for sample in samples:
        thrust = float(sample["thrust_n"])
        rpm = float(sample["rotation_speed_rpm"])
        if thrust > 0.0 and rpm > 0.0:
            omega = rpm * 2.0 * math.pi / 60.0
            points.append((omega * omega, thrust))
    if not points:
        return {}
    numerator = sum(x * y for x, y in points)
    denominator = sum(x * x for x, _ in points)
    k = numerator / denominator if denominator > 0.0 else float("nan")
    residuals = [y - k * x for x, y in points]
    mean_y = sum(y for _, y in points) / len(points)
    ss_res = sum(value * value for value in residuals)
    ss_tot = sum((y - mean_y) ** 2 for _, y in points)
    return {
        "fit_k_n_per_rad2_s2": k,
        "fit_point_count": float(len(points)),
        "fit_r2": 1.0 - ss_res / ss_tot if ss_tot > 0.0 else float("nan"),
        "fit_residual_rms_n": math.sqrt(ss_res / len(points)),
    }


def fit_torque_per_thrust(samples: list[dict[str, str | float | int]]) -> dict[str, float]:
    points = []
    for sample in samples:
        thrust = float(sample["thrust_n"])
        torque = abs(float(sample["torque_n_m"]))
        rpm = float(sample["rotation_speed_rpm"])
        if thrust > 0.05 and torque > 0.0 and rpm > 0.0:
            points.append((thrust, torque))
    if not points:
        return {}
    numerator = sum(thrust * torque for thrust, torque in points)
    denominator = sum(thrust * thrust for thrust, _ in points)
    ratio = numerator / denominator if denominator > 0.0 else float("nan")
    residuals = [torque - ratio * thrust for thrust, torque in points]
    mean_torque = sum(torque for _, torque in points) / len(points)
    ss_res = sum(value * value for value in residuals)
    ss_tot = sum((torque - mean_torque) ** 2 for _, torque in points)
    q_over_t_values = [torque / thrust for thrust, torque in points if thrust > 0.0]
    return {
        "fit_q_over_t_m": ratio,
        "fit_point_count": float(len(points)),
        "fit_r2": 1.0 - ss_res / ss_tot if ss_tot > 0.0 else float("nan"),
        "fit_residual_rms_n_m": math.sqrt(ss_res / len(points)),
        "sample_q_over_t_min_m": min(q_over_t_values),
        "sample_q_over_t_mean_m": sum(q_over_t_values) / len(q_over_t_values),
        "sample_q_over_t_max_m": max(q_over_t_values),
    }


def load_apdrone_model() -> dict[str, float]:
    path = DATA / "apdrone_flight_vs_model_reference.csv"
    if not path.exists():
        return {}
    with path.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            if row.get("row_type") == "project_preset_model" and row.get("preset") == "apDrone":
                return {
                    "max_rotor_thrust_n": float(row["max_rotor_thrust_n"]),
                    "thrust_coefficient": float(row["thrust_coefficient_n_per_rad2_s2"]),
                }
    return {}


def load_project_yaw_models() -> dict[str, dict[str, float]]:
    path = DATA / "apdrone_flight_vs_model_reference.csv"
    if not path.exists():
        return {}
    models: dict[str, dict[str, float]] = {}
    with path.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            if row.get("row_type") != "project_preset_model":
                continue
            preset = str(row.get("preset", ""))
            if preset not in {"racingQuad", "apDrone"}:
                continue
            models[preset] = {
                "yaw_torque_per_thrust_m": float(row["yaw_torque_per_thrust_m"]),
                "max_rotor_thrust_n": float(row["max_rotor_thrust_n"]),
                "thrust_coefficient": float(row["thrust_coefficient_n_per_rad2_s2"]),
            }
    return models


def summarize_test(test: dict[str, str], model: dict[str, float]) -> list[dict[str, str | float | int]]:
    html_path = RAW / f"{test['hash']}_{test['slug']}.html"
    page_html = fetch_text(test["url"], html_path)
    title, creator, data = parse_benchmark_table(page_html)
    motor = parse_component(page_html, "Motor")
    propeller = parse_component(page_html, "Propeller")
    esc = parse_component(page_html, "ESC")

    times = series_values(data, "time")
    throttle = series_values(data, "throttle")
    rpm = series_values(data, "rotation_speed")
    thrust_kgf = series_values(data, "thrust")
    torque = series_values(data, "torque")
    voltage = series_values(data, "voltage")
    current = series_values(data, "current")
    epower = series_values(data, "epower")
    mpower = series_values(data, "mpower")
    overalleff = series_values(data, "overalleff")

    row_count = max(len(values) for values in [times, throttle, rpm, thrust_kgf, voltage, current, epower] if values)
    samples: list[dict[str, str | float | int]] = []
    for index in range(row_count):
        thrust_value_kgf = safe_get(thrust_kgf, index)
        rpm_value = safe_get(rpm, index)
        omega = rpm_value * 2.0 * math.pi / 60.0 if rpm_value > 0.0 else float("nan")
        samples.append(
            {
                "row_type": "tyto_static_powertrain_sample",
                "test_hash": test["hash"],
                "title": title,
                "creator": creator,
                "source_page": test["url"],
                "local_source_file": repo_path(html_path),
                "motor": motor,
                "propeller": propeller,
                "esc": esc,
                "sample_index": index,
                "time_s": safe_get(times, index),
                "throttle_us": safe_get(throttle, index),
                "rotation_speed_rpm": rpm_value,
                "rotation_speed_rad_s": omega,
                "thrust_kgf": thrust_value_kgf,
                "thrust_n": thrust_value_kgf * 9.80665,
                "torque_n_m": safe_get(torque, index),
                "voltage_v": safe_get(voltage, index),
                "current_a": safe_get(current, index),
                "electrical_power_w": safe_get(epower, index),
                "mechanical_power_w": safe_get(mpower, index),
                "overall_efficiency_gf_per_w": safe_get(overalleff, index),
                "note": test["class_note"],
            }
        )

    fit = fit_thrust_coefficient(samples)
    finite_samples = [sample for sample in samples if float(sample["thrust_n"]) > 0.0]
    max_thrust = max(finite_samples, key=lambda sample: float(sample["thrust_n"])) if finite_samples else {}
    summary = {
        "row_type": "tyto_static_powertrain_summary",
        "test_hash": test["hash"],
        "title": title,
        "creator": creator,
        "source_page": test["url"],
        "local_source_file": repo_path(html_path),
        "motor": motor,
        "propeller": propeller,
        "esc": esc,
        "sample_count": row_count,
        "max_thrust_n": max_thrust.get("thrust_n", ""),
        "max_thrust_kgf": max_thrust.get("thrust_kgf", ""),
        "rpm_at_max_thrust": max_thrust.get("rotation_speed_rpm", ""),
        "current_a_at_max_thrust": max_thrust.get("current_a", ""),
        "voltage_v_at_max_thrust": max_thrust.get("voltage_v", ""),
        "electrical_power_w_at_max_thrust": max_thrust.get("electrical_power_w", ""),
        "torque_n_m_at_max_thrust": max_thrust.get("torque_n_m", ""),
        "fit_k_n_per_rad2_s2": fit.get("fit_k_n_per_rad2_s2", ""),
        "fit_r2": fit.get("fit_r2", ""),
        "fit_point_count": fit.get("fit_point_count", ""),
        "fit_residual_rms_n": fit.get("fit_residual_rms_n", ""),
        "apdrone_configured_max_rotor_thrust_n": model.get("max_rotor_thrust_n", ""),
        "apdrone_configured_thrust_coefficient_n_per_rad2_s2": model.get("thrust_coefficient", ""),
        "apdrone_max_thrust_over_tyto_max_thrust": (
            model["max_rotor_thrust_n"] / float(max_thrust["thrust_n"])
            if model and max_thrust
            else ""
        ),
        "apdrone_thrust_coefficient_over_tyto_fit": (
            model["thrust_coefficient"] / fit["fit_k_n_per_rad2_s2"]
            if model and fit and fit["fit_k_n_per_rad2_s2"] > 0.0
            else ""
        ),
        "note": "Tyto public static test data. Use as comparable FPV static propulsion evidence, not APdrone exact motor/prop proof.",
    }
    return [summary, *samples]


def row_float(row: dict[str, str | float | int], key: str) -> float:
    try:
        value = float(row.get(key, ""))
    except (TypeError, ValueError):
        return float("nan")
    return value if math.isfinite(value) else float("nan")


def mean(values: list[float]) -> float:
    finite = [value for value in values if math.isfinite(value)]
    return sum(finite) / len(finite) if finite else float("nan")


def median(values: list[float]) -> float:
    finite = sorted(value for value in values if math.isfinite(value))
    if not finite:
        return float("nan")
    index = len(finite) // 2
    if len(finite) % 2:
        return finite[index]
    return 0.5 * (finite[index - 1] + finite[index])


def summarize_torque_ratios(
    powertrain_rows: list[dict[str, str | float | int]],
    project_models: dict[str, dict[str, float]],
) -> list[dict[str, str | float | int]]:
    rows: list[dict[str, str | float | int]] = [
        {
            "row_type": "tyto_torque_ratio_metadata",
            "source_page": SOURCE_HOME,
            "note": "Derives propeller torque/thrust ratio Q/T from selected Tyto static tests. Torque sign is discarded because the Java yaw coefficient uses magnitude with motor spin direction handled separately.",
        }
    ]

    samples_by_hash: dict[str, list[dict[str, str | float | int]]] = {}
    summaries_by_hash: dict[str, dict[str, str | float | int]] = {}
    for row in powertrain_rows:
        if row.get("row_type") == "tyto_static_powertrain_sample":
            samples_by_hash.setdefault(str(row.get("test_hash", "")), []).append(row)
        elif row.get("row_type") == "tyto_static_powertrain_summary":
            summaries_by_hash[str(row.get("test_hash", ""))] = row

    for test_hash, samples in samples_by_hash.items():
        summary = summaries_by_hash.get(test_hash, {})
        torque_fit = fit_torque_per_thrust(samples)
        max_thrust_sample = max(samples, key=lambda row: row_float(row, "thrust_n"))
        max_thrust = row_float(max_thrust_sample, "thrust_n")
        high_samples = [
            sample
            for sample in samples
            if row_float(sample, "thrust_n") >= 0.5 * max_thrust
            and row_float(sample, "thrust_n") > 0.05
            and abs(row_float(sample, "torque_n_m")) > 0.0
        ]
        high_q_over_t = [
            abs(row_float(sample, "torque_n_m")) / row_float(sample, "thrust_n")
            for sample in high_samples
            if row_float(sample, "thrust_n") > 0.0
        ]
        q_over_t_at_max = (
            abs(row_float(max_thrust_sample, "torque_n_m")) / max_thrust
            if max_thrust > 0.0
            else float("nan")
        )
        summary_row: dict[str, str | float | int] = {
            "row_type": "tyto_torque_ratio_summary",
            "test_hash": test_hash,
            "title": str(summary.get("title", "")),
            "creator": str(summary.get("creator", "")),
            "source_page": str(summary.get("source_page", "")),
            "local_source_file": str(summary.get("local_source_file", "")),
            "motor": str(summary.get("motor", "")),
            "propeller": str(summary.get("propeller", "")),
            "esc": str(summary.get("esc", "")),
            "sample_count": len(samples),
            "fit_q_over_t_m": torque_fit.get("fit_q_over_t_m", ""),
            "fit_r2": torque_fit.get("fit_r2", ""),
            "fit_point_count": torque_fit.get("fit_point_count", ""),
            "fit_residual_rms_n_m": torque_fit.get("fit_residual_rms_n_m", ""),
            "sample_q_over_t_min_m": torque_fit.get("sample_q_over_t_min_m", ""),
            "sample_q_over_t_mean_m": torque_fit.get("sample_q_over_t_mean_m", ""),
            "sample_q_over_t_max_m": torque_fit.get("sample_q_over_t_max_m", ""),
            "high_thrust_sample_count": len(high_samples),
            "high_thrust_q_over_t_mean_m": mean(high_q_over_t),
            "high_thrust_q_over_t_median_m": median(high_q_over_t),
            "q_over_t_at_max_thrust_m": q_over_t_at_max,
            "max_thrust_n": max_thrust,
            "torque_n_m_at_max_thrust": abs(row_float(max_thrust_sample, "torque_n_m")),
            "rpm_at_max_thrust": row_float(max_thrust_sample, "rotation_speed_rpm"),
            "note": "Fit uses positive-thrust samples above 0.05 N. High-thrust rows use samples at >=50% of that test's max thrust.",
        }
        fit_q_over_t = row_float(summary_row, "fit_q_over_t_m")
        for preset_name, model in project_models.items():
            configured = model["yaw_torque_per_thrust_m"]
            summary_row[f"{preset_name}_configured_yaw_torque_per_thrust_m"] = configured
            summary_row[f"{preset_name}_configured_over_fit_q_over_t"] = configured / fit_q_over_t if fit_q_over_t > 0.0 else ""
            summary_row[f"{preset_name}_fit_q_over_t_over_configured"] = fit_q_over_t / configured if configured > 0.0 and fit_q_over_t > 0.0 else ""
            summary_row[f"{preset_name}_max_thrust_q_over_t_over_configured"] = q_over_t_at_max / configured if configured > 0.0 and math.isfinite(q_over_t_at_max) else ""
        rows.append(summary_row)

        for sample in samples:
            thrust = row_float(sample, "thrust_n")
            torque = abs(row_float(sample, "torque_n_m"))
            rpm = row_float(sample, "rotation_speed_rpm")
            omega = rpm * 2.0 * math.pi / 60.0 if rpm > 0.0 else float("nan")
            q_over_t = torque / thrust if thrust > 0.0 else float("nan")
            sample_row: dict[str, str | float | int] = {
                "row_type": "tyto_torque_ratio_sample",
                "test_hash": test_hash,
                "title": str(sample.get("title", "")),
                "source_page": str(sample.get("source_page", "")),
                "local_source_file": str(sample.get("local_source_file", "")),
                "sample_index": int(sample.get("sample_index", 0)),
                "throttle_us": row_float(sample, "throttle_us"),
                "rotation_speed_rpm": rpm,
                "thrust_n": thrust,
                "torque_n_m_abs": torque,
                "q_over_t_m": q_over_t,
                "torque_coefficient_n_m_per_rad2_s2": torque / (omega * omega) if omega > 0.0 else "",
                "mechanical_power_from_torque_w": torque * omega if omega > 0.0 else "",
                "mechanical_power_w_from_tyto": row_float(sample, "mechanical_power_w"),
                "mechanical_power_ratio_recomputed_over_tyto": (
                    (torque * omega) / row_float(sample, "mechanical_power_w")
                    if omega > 0.0 and row_float(sample, "mechanical_power_w") > 0.0
                    else ""
                ),
                "note": "Per-sample torque/thrust ratio; low-thrust samples are noisier and should not dominate yaw coefficient calibration.",
            }
            for preset_name, model in project_models.items():
                configured = model["yaw_torque_per_thrust_m"]
                sample_row[f"{preset_name}_sample_q_over_t_over_configured"] = q_over_t / configured if configured > 0.0 and math.isfinite(q_over_t) else ""
            rows.append(sample_row)

    if project_models:
        for preset_name, model in project_models.items():
            rows.append(
                {
                    "row_type": "project_yaw_torque_reference",
                    "preset": preset_name,
                    "source_page": "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneConfig.java",
                    "yaw_torque_per_thrust_m": model["yaw_torque_per_thrust_m"],
                    "max_rotor_thrust_n": model["max_rotor_thrust_n"],
                    "thrust_coefficient_n_per_rad2_s2": model["thrust_coefficient"],
                    "note": "Parsed from apdrone_flight_vs_model_reference.csv generated from DroneConfig.java.",
                }
            )

    return rows


def write_csv(path: Path, rows: list[dict[str, str | float | int]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames: list[str] = []
    for row in rows:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows([{key: finite_or_blank(value) for key, value in row.items()} for row in rows])


def main() -> None:
    model = load_apdrone_model()
    project_yaw_models = load_project_yaw_models()
    rows: list[dict[str, str | float | int]] = [
        {
            "row_type": "tyto_dataset_metadata",
            "source_page": SOURCE_HOME,
            "note": "Tyto Robotics Database reports public static propulsion tests and embeds benchmark-data-table arrays in each selected test page.",
        }
    ]
    for test in SELECTED_TESTS:
        rows.extend(summarize_test(test, model))
    torque_rows = summarize_torque_ratios(rows, project_yaw_models)
    write_csv(OUTPUT, rows)
    write_csv(TORQUE_OUTPUT, torque_rows)
    print(f"Wrote {repo_path(OUTPUT)}")
    print(f"Wrote {repo_path(TORQUE_OUTPUT)}")


if __name__ == "__main__":
    main()
