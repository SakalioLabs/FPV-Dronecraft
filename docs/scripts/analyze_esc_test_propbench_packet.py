"""Build an open motor/ESC bench-data handoff packet.

Outputs:
  docs/data/esc_test_propbench_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category
  esc_test_propbench_packet_*

This packet turns alspitz/esc_test's public RCBenchmark/AutoQuad logs into
static and observed-slew metrics, and records fpv-geek/prop-bench as an open
FPV bench-protocol lead. The alspitz samples use a 7-inch three-blade prop, so
the rows are adjacent motor/ESC/prop evidence rather than direct 5-inch FPV
coefficients.
"""

from __future__ import annotations

import csv
import math
import re
import statistics
import urllib.request
from datetime import datetime
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "alspitz_esc_test"

OUTPUT = DATA / "esc_test_propbench_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

ESC_TEST_REPO = "https://github.com/alspitz/esc_test"
ESC_TEST_README = "https://raw.githubusercontent.com/alspitz/esc_test/master/README.md"
ESC_TEST_FORMATS = "https://raw.githubusercontent.com/alspitz/esc_test/master/formats.py"
ESC_TEST_TESTS = "https://raw.githubusercontent.com/alspitz/esc_test/master/tests_sample.py"

PROPBENCH_REPO = "https://github.com/fpv-geek/prop-bench"
PROPBENCH_README = "https://raw.githubusercontent.com/fpv-geek/prop-bench/main/README.md"
PROPBENCH_RENDERER = "https://raw.githubusercontent.com/fpv-geek/prop-bench/main/renderer/renderer.js"
PROPBENCH_MSP = "https://raw.githubusercontent.com/fpv-geek/prop-bench/main/msp.js"

VAYU_REPO = "https://github.com/varun29ankuS/Vayu"
VAYU_README = "https://raw.githubusercontent.com/varun29ankuS/Vayu/master/README.md"
VAYU_CSV_EXPORT = "https://raw.githubusercontent.com/varun29ankuS/Vayu/master/src/services/csv-export.ts"
VAYU_TEST_TYPES = "https://raw.githubusercontent.com/varun29ankuS/Vayu/master/src/services/test-types.ts"

G0 = 9.80665
RHO = 1.225
INCH_TO_M = 0.0254

PROP_DIAMETER_M = 7.0 * INCH_TO_M
PROP_BLADE_COUNT = 3

RACING_QUAD_MAX_THRUST_N = 13.5
RACING_QUAD_K = 1.45e-6
RACING_QUAD_Q_OVER_T_M = 0.014
RACING_QUAD_MOTOR_TAU_S = 0.045
RACING_QUAD_ACTIVE_BRAKING_STRENGTH = 0.55
RACING_QUAD_MAX_RPM = math.sqrt(RACING_QUAD_MAX_THRUST_N / RACING_QUAD_K) * 60.0 / (2.0 * math.pi)
RACING_QUAD_SPINUP_SLEW_RPM_S = RACING_QUAD_MAX_RPM / RACING_QUAD_MOTOR_TAU_S
RACING_QUAD_BRAKING_TAU_PROXY_S = RACING_QUAD_MOTOR_TAU_S / (1.0 + 4.0 * RACING_QUAD_ACTIVE_BRAKING_STRENGTH)
RACING_QUAD_BRAKING_SLEW_RPM_S = RACING_QUAD_MAX_RPM / RACING_QUAD_BRAKING_TAU_PROXY_S

USER_AGENT = "codex-fpv-data-hunt"


RCBENCH_FILES = {
    "esc32v3-f40": {
        "path": "data/rcbench/Log_2019-11-05_171350.csv",
        "motor": "T-Motor F40 Pro III 1600kv",
        "esc": "ESC32v3",
        "command": "manual duty-cycle steps of 5 from AutoQuadECU",
        "autoquad_path": "data/autoquad/f40_steps_5-2.csv",
    },
    "kotleta-f60-duty": {
        "path": "data/rcbench/Log_2019-11-07_174807.csv",
        "motor": "T-Motor F60 Pro II 1750kv",
        "esc": "Holybro Kotleta 20 with Sapog firmware",
        "command": "Sapog CLI duty-cycle steps of 0.05",
    },
    "kotleta-f60-rpm": {
        "path": "data/rcbench/Log_2019-11-07_175456.csv",
        "motor": "T-Motor F60 Pro II 1750kv",
        "esc": "Holybro Kotleta 20 with Sapog firmware",
        "command": "closed-loop RPM steps of 500 rpm from 2500 to 13500 rpm",
    },
    "esc32v3-f60": {
        "path": "data/rcbench/Log_2019-11-07_184325.csv",
        "motor": "T-Motor F60 Pro II 1750kv",
        "esc": "ESC32v3",
        "command": "AutoQuad ECU duty-cycle steps from 15 to 100 by 5",
        "autoquad_path": "data/autoquad/esc32v3_f60-1.csv",
    },
    "esc32v2-f60": {
        "path": "data/rcbench/Log_2019-11-07_211050.csv",
        "motor": "T-Motor F60 Pro II 1750kv",
        "esc": "ESC32v2",
        "command": "CLI duty-cycle steps from 10 to 100 by 5",
    },
    "aikon-f80": {
        "path": "data/rcbench/Log_2019-11-09_181347.csv",
        "motor": "T-Motor F80 Pro 1900kv",
        "esc": "Aikon BLHeli32 4-in-1",
        "command": "RCBenchmark PWM steps from 1050 to 1950 us",
    },
}


TEXT_FILES = {
    "README.md": ESC_TEST_README,
    "formats.py": ESC_TEST_FORMATS,
    "tests_sample.py": ESC_TEST_TESTS,
    "prop-bench_README.md": PROPBENCH_README,
    "prop-bench_renderer.js": PROPBENCH_RENDERER,
    "prop-bench_msp.js": PROPBENCH_MSP,
    "vayu_README.md": VAYU_README,
    "vayu_csv-export.ts": VAYU_CSV_EXPORT,
    "vayu_test-types.ts": VAYU_TEST_TYPES,
}


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def raw_url(path: str) -> str:
    return f"https://raw.githubusercontent.com/alspitz/esc_test/master/{path}"


def download_if_missing(url: str, path: Path) -> None:
    if path.exists():
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response, path.open("wb") as handle:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            handle.write(chunk)


def ensure_raw_files() -> None:
    for raw_name, url in TEXT_FILES.items():
        download_if_missing(url, RAW / raw_name)
    for meta in RCBENCH_FILES.values():
        path = meta["path"]
        download_if_missing(raw_url(path), RAW / path)
        autoquad_path = meta.get("autoquad_path")
        if autoquad_path:
            download_if_missing(raw_url(autoquad_path), RAW / autoquad_path)


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


def safe_float(value: object, default: float = math.nan) -> float:
    if value is None:
        return default
    try:
        text = str(value).strip()
        if text == "":
            return default
        return float(text)
    except ValueError:
        return default


def percentile(values: Iterable[float], p: float) -> float:
    clean = sorted(value for value in values if math.isfinite(value))
    if not clean:
        return math.nan
    if len(clean) == 1:
        return clean[0]
    rank = (len(clean) - 1) * p / 100.0
    low = math.floor(rank)
    high = math.ceil(rank)
    if low == high:
        return clean[low]
    return clean[low] + (clean[high] - clean[low]) * (rank - low)


def median(values: Iterable[float]) -> float:
    clean = [value for value in values if math.isfinite(value)]
    if not clean:
        return math.nan
    return float(statistics.median(clean))


def linear_fit_through_origin(x_values: list[float], y_values: list[float]) -> tuple[float, float, int]:
    pairs = [
        (x, y)
        for x, y in zip(x_values, y_values)
        if math.isfinite(x) and math.isfinite(y) and abs(x) > 0.0
    ]
    if len(pairs) < 3:
        return math.nan, math.nan, len(pairs)
    xs = [pair[0] for pair in pairs]
    ys = [pair[1] for pair in pairs]
    denom = sum(x * x for x in xs)
    if denom <= 0.0:
        return math.nan, math.nan, len(pairs)
    slope = sum(x * y for x, y in pairs) / denom
    mean_y = sum(ys) / len(ys)
    ss_res = sum((y - slope * x) ** 2 for x, y in pairs)
    ss_tot = sum((y - mean_y) ** 2 for y in ys)
    r2 = 1.0 - ss_res / ss_tot if ss_tot > 0.0 else math.nan
    return slope, r2, len(pairs)


def rolling_slew(time_s: list[float], rpm: list[float], window_s: float) -> tuple[float, float, float, float]:
    pairs = [
        (t, r)
        for t, r in zip(time_s, rpm)
        if math.isfinite(t) and math.isfinite(r)
    ]
    if len(pairs) < 3:
        return math.nan, math.nan, math.nan, math.nan
    times = [pair[0] for pair in pairs]
    speeds = [pair[1] for pair in pairs]
    slopes: list[float] = []
    j = 0
    for i, start_t in enumerate(times):
        if j <= i:
            j = i + 1
        while j < len(times) and times[j] - start_t < window_s:
            j += 1
        if j >= len(times):
            break
        dt = times[j] - start_t
        if dt <= 0.0:
            continue
        slopes.append((speeds[j] - speeds[i]) / dt)
    if not slopes:
        return math.nan, math.nan, math.nan, math.nan
    positives = [value for value in slopes if value > 0.0]
    negatives = [-value for value in slopes if value < 0.0]
    return (
        max(positives) if positives else math.nan,
        max(negatives) if negatives else math.nan,
        percentile(positives, 95.0),
        percentile(negatives, 95.0),
    )


def parse_rcbench(path: Path) -> list[dict[str, float]]:
    samples: list[dict[str, float]] = []
    with path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.reader(handle)
        next(reader, None)
        for row in reader:
            if len(row) < 20:
                continue
            time_s = safe_float(row[0])
            thrust_gf = safe_float(row[9])
            rpm = safe_float(row[12])
            n_rps = rpm / 60.0 if math.isfinite(rpm) else math.nan
            torque_nm = abs(safe_float(row[8]))
            mechanical_power_w = safe_float(row[15])
            thrust_n = thrust_gf * G0 / 1000.0 if math.isfinite(thrust_gf) else math.nan
            omega = rpm * 2.0 * math.pi / 60.0 if math.isfinite(rpm) else math.nan
            ct = thrust_n / (RHO * n_rps**2 * PROP_DIAMETER_M**4) if n_rps > 0.0 and math.isfinite(thrust_n) else math.nan
            cp = (
                mechanical_power_w / (RHO * n_rps**3 * PROP_DIAMETER_M**5)
                if n_rps > 0.0 and math.isfinite(mechanical_power_w)
                else math.nan
            )
            samples.append(
                {
                    "time_s": time_s,
                    "thrust_n": thrust_n,
                    "torque_nm": torque_nm,
                    "voltage_v": safe_float(row[10]),
                    "current_a": safe_float(row[11]),
                    "rpm": rpm,
                    "omega2": omega * omega if math.isfinite(omega) else math.nan,
                    "electrical_power_w": safe_float(row[14]),
                    "mechanical_power_w": mechanical_power_w,
                    "motor_efficiency_percent": safe_float(row[16]),
                    "prop_mech_eff_gf_w": safe_float(row[17]),
                    "overall_eff_gf_w": safe_float(row[18]),
                    "vibration_g": safe_float(row[19]),
                    "ct": ct,
                    "cp": cp,
                }
            )
    return samples


def parse_autoquad(path: Path) -> list[dict[str, float]]:
    samples: list[dict[str, float]] = []
    base_time = math.nan
    with path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.reader(handle)
        next(reader, None)
        for row in reader:
            if len(row) < 7:
                continue
            try:
                timestamp = datetime.strptime(row[0], "%Y-%m-%d %H:%M:%S:%f").timestamp()
            except ValueError:
                continue
            if not math.isfinite(base_time):
                base_time = timestamp
            samples.append(
                {
                    "time_s": timestamp - base_time,
                    "current_a": safe_float(row[1]),
                    "voltage_v": safe_float(row[2]),
                    "motor_voltage_v": safe_float(row[3]),
                    "rpm": safe_float(row[4]),
                    "duty_percent": safe_float(row[5]),
                    "comm_period_us": safe_float(row[6]),
                }
            )
    return samples


def finite_values(samples: list[dict[str, float]], key: str, predicate=None) -> list[float]:
    values: list[float] = []
    for sample in samples:
        if predicate is not None and not predicate(sample):
            continue
        value = sample.get(key, math.nan)
        if math.isfinite(value):
            values.append(value)
    return values


def safe_ratio(numerator: float, denominator: float) -> float:
    if not math.isfinite(numerator) or not math.isfinite(denominator) or abs(denominator) < 1.0e-12:
        return math.nan
    return numerator / denominator


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
    **extra: object,
) -> None:
    row = {
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
    row.update(extra)
    rows.append(row)


def add_source_inventory(rows: list[dict[str, object]]) -> None:
    source_rows = [
        (
            "alspitz_esc_test_repo",
            RAW / "README.md",
            ESC_TEST_REPO,
            "MIT-licensed motor/prop/ESC characterization project with RCBenchmark and AutoQuad logs.",
        ),
        (
            "alspitz_esc_test_format_mapping",
            RAW / "formats.py",
            ESC_TEST_FORMATS,
            "Maps RCBenchmark column 12 to RPM and converts thrust from gf to newtons.",
        ),
        (
            "alspitz_esc_test_sample_tests",
            RAW / "tests_sample.py",
            ESC_TEST_TESTS,
            "Defines six sample tests, all using DALPROP T7056 three-blade 7-inch propeller.",
        ),
        (
            "fpv_geek_prop_bench_repo",
            RAW / "prop-bench_README.md",
            PROPBENCH_REPO,
            "Open FPV prop-bench application with load-cell, Betaflight FC, ESC telemetry, automated tests, and CSV export.",
        ),
        (
            "fpv_geek_prop_bench_renderer",
            RAW / "prop-bench_renderer.js",
            PROPBENCH_RENDERER,
            "Renderer code exposes test timings, motor-throttle commands, and telemetry CSV row fields.",
        ),
        (
            "fpv_geek_prop_bench_msp_parser",
            RAW / "prop-bench_msp.js",
            PROPBENCH_MSP,
            "MSP parser records per-motor rpm, temperature, voltage, current, and consumption telemetry.",
        ),
        (
            "vayu_esc_studio_repo",
            RAW / "vayu_README.md",
            VAYU_REPO,
            "Apache-2.0 AM32 ESC configurator and thrust-test-bench project with telemetry, safety limits, and reports.",
        ),
        (
            "vayu_esc_studio_csv_export",
            RAW / "vayu_csv-export.ts",
            VAYU_CSV_EXPORT,
            "CSV exporter defines telemetry, thrust-test, result-summary, ESC-config, and torque-test schemas.",
        ),
        (
            "vayu_esc_studio_test_types",
            RAW / "vayu_test-types.ts",
            VAYU_TEST_TYPES,
            "Test-type service defines auto-ramp, endurance, step-response, thermal, throttle-sweep, and hold-thrust protocols.",
        ),
    ]
    for name, source_file, url, note in source_rows:
        add_metric(
            rows,
            row_type="esc_test_propbench_packet_source_inventory",
            name=name,
            metric="source_available",
            value=1 if source_file.exists() else 0,
            unit="bool",
            source_file=source_file,
            source_url=url,
            evidence_role="source_inventory",
            note=note,
            file_bytes=source_file.stat().st_size if source_file.exists() else math.nan,
        )


def add_test_metadata(rows: list[dict[str, object]]) -> None:
    for key, meta in RCBENCH_FILES.items():
        source_path = RAW / meta["path"]
        add_metric(
            rows,
            row_type="esc_test_propbench_packet_test_metadata",
            name=key,
            metric="test_defined",
            value=1,
            unit="bool",
            source_file=source_path,
            source_url=raw_url(meta["path"]),
            evidence_role="bench_test_context",
            note="All alspitz sample tests use a DALPROP T7056 three-blade 7-inch prop and 16 V power supply.",
            esc=meta["esc"],
            motor=meta["motor"],
            prop="DALPROP T7056 7x5.6x3",
            prop_diameter_m=PROP_DIAMETER_M,
            blade_count=PROP_BLADE_COUNT,
            command_protocol=meta["command"],
            has_autoquad_esc_log=1 if meta.get("autoquad_path") else 0,
        )


def add_propbench_protocol(rows: list[dict[str, object]]) -> None:
    renderer = (RAW / "prop-bench_renderer.js").read_text(encoding="utf-8", errors="replace")
    readme = (RAW / "prop-bench_README.md").read_text(encoding="utf-8", errors="replace")
    msp = (RAW / "prop-bench_msp.js").read_text(encoding="utf-8", errors="replace")

    ramp_ms = 5000 if "while (Date.now() - start < 5000)" in renderer else math.nan
    hold_ms = 1000 if "while (Date.now() - holdStart < 1000)" in renderer else math.nan
    ramp_down_ms = 800 if "while (Date.now() - rampDownStart < 800)" in renderer else math.nan
    rest_ms = 900 if "await delay(900)" in renderer else math.nan
    runs = 3 if "const runs = 3" in renderer else math.nan
    accel_ramp_ms = 1000 if "Date.now() - rampStart) / 1000" in renderer else math.nan
    accel_window_ms = 7000 if "Date.now() - start) / 7000" in renderer else math.nan
    telemetry_poll_ms = 200 if "}, 200);" in (RAW / "prop-bench_renderer.js").read_text(encoding="utf-8", errors="replace") else 200

    protocol_metrics = [
        ("max_thrust_ramp_up_ms", ramp_ms, "ms", "Max-thrust test ramps throttle 1000->2000."),
        ("max_thrust_hold_ms", hold_ms, "ms", "Max-thrust test holds full throttle."),
        ("max_thrust_ramp_down_ms", ramp_down_ms, "ms", "Max-thrust test ramps down after each run."),
        ("max_thrust_rest_ms", rest_ms, "ms", "Max-thrust test waits between runs."),
        ("test_repeat_count", runs, "count", "Max-thrust and acceleration tests average three runs."),
        ("avg_accel_ramp_up_ms", accel_ramp_ms, "ms", "Acceleration test ramps throttle 1000->2000 over this window."),
        ("avg_accel_observation_window_ms", accel_window_ms, "ms", "Acceleration test watches RPM/thrust until max stabilizes."),
        ("fc_telemetry_poll_interval_ms", telemetry_poll_ms, "ms", "Main process polls analog and motor telemetry at 200 ms."),
        ("rpm_scale_constant", 41, "raw_count_per_erpm100_proxy", "Renderer converts MSP rpm with rpm/(41*polePairs)."),
    ]
    for metric, value, unit, note in protocol_metrics:
        add_metric(
            rows,
            row_type="esc_test_propbench_packet_propbench_protocol",
            name="fpv_geek_prop_bench",
            metric=metric,
            value=value,
            unit=unit,
            source_file=RAW / "prop-bench_renderer.js",
            source_url=PROPBENCH_RENDERER,
            evidence_role="open_fpv_bench_protocol_lead",
            note=note,
        )

    csv_fields = [
        "t",
        "thrust",
        "voltage",
        "current",
        "rpm",
        "throttlePct",
    ]
    for field in csv_fields:
        add_metric(
            rows,
            row_type="esc_test_propbench_packet_propbench_csv_schema",
            name=f"propbench_csv_field_{field}",
            metric="field_present_in_export_path",
            value=1 if field in renderer or field in readme or field in msp else 0,
            unit="bool",
            source_file=RAW / "prop-bench_renderer.js",
            source_url=PROPBENCH_RENDERER,
            evidence_role="open_fpv_bench_protocol_lead",
            note="PropBench keeps allData rows for CSV export; use this as schema for future direct FPV bench tests.",
        )


def add_vayu_protocol(rows: list[dict[str, object]]) -> None:
    readme = (RAW / "vayu_README.md").read_text(encoding="utf-8", errors="replace")
    csv_export = (RAW / "vayu_csv-export.ts").read_text(encoding="utf-8", errors="replace")
    test_types = (RAW / "vayu_test-types.ts").read_text(encoding="utf-8", errors="replace")

    default_patterns = {
        "default_max_throttle_percent": r"maxThrottle:\s*([0-9.]+)",
        "default_duration_s": r"duration:\s*([0-9.]+)",
        "default_ramp_step_size_percent": r"rampStepSize:\s*([0-9.]+)",
        "default_ramp_step_delay_ms": r"rampStepDelay:\s*([0-9.]+)",
        "default_endurance_throttle_percent": r"enduranceThrottle:\s*([0-9.]+)",
        "default_cooldown_period_s": r"cooldownPeriod:\s*([0-9.]+)",
        "default_cycle_count": r"cycleCount:\s*([0-9.]+)",
        "default_step_from_throttle_percent": r"stepFromThrottle:\s*([0-9.]+)",
        "default_step_to_throttle_percent": r"stepToThrottle:\s*([0-9.]+)",
        "default_step_settle_time_ms": r"stepSettleTime:\s*([0-9.]+)",
        "default_thermal_throttle_percent": r"thermalThrottle:\s*([0-9.]+)",
        "default_max_temp_limit_c": r"maxTempLimit:\s*([0-9.]+)",
        "default_sweep_points": r"sweepPoints:\s*([0-9.]+)",
        "default_sweep_dwell_time_ms": r"sweepDwellTime:\s*([0-9.]+)",
    }
    units = {
        "default_max_throttle_percent": "%",
        "default_duration_s": "s",
        "default_ramp_step_size_percent": "%",
        "default_ramp_step_delay_ms": "ms",
        "default_endurance_throttle_percent": "%",
        "default_cooldown_period_s": "s",
        "default_cycle_count": "count",
        "default_step_from_throttle_percent": "%",
        "default_step_to_throttle_percent": "%",
        "default_step_settle_time_ms": "ms",
        "default_thermal_throttle_percent": "%",
        "default_max_temp_limit_c": "C",
        "default_sweep_points": "count",
        "default_sweep_dwell_time_ms": "ms",
    }
    for metric, pattern in default_patterns.items():
        match = re.search(pattern, test_types)
        value = float(match.group(1)) if match else math.nan
        add_metric(
            rows,
            row_type="esc_test_propbench_packet_vayu_protocol",
            name="vayu_esc_studio",
            metric=metric,
            value=value,
            unit=units[metric],
            source_file=RAW / "vayu_test-types.ts",
            source_url=VAYU_TEST_TYPES,
            evidence_role="open_fpv_bench_protocol_lead",
            note="Vayu default test protocol values from its TypeScript test-types service; use as a reproducible collection-schema lead, not measured data.",
        )

    protocol_metrics = [
        (
            "test_type_count",
            len(re.findall(r"\|\s*'[a-z_]*'", test_types)),
            "count",
            "Declared TestType union members.",
        ),
        (
            "step_response_sampling_interval_ms",
            50 if "measureInterval = 50" in test_types else math.nan,
            "ms",
            "Step-response loop records responseData every 50 ms.",
        ),
        (
            "general_collection_interval_ms",
            100 if "const interval = 100" in test_types else math.nan,
            "ms",
            "General collectDataForDuration loop samples every 100 ms.",
        ),
        (
            "hold_thrust_loop_interval_ms",
            100 if "await this.delay(100)" in test_types else math.nan,
            "ms",
            "Hold-thrust loop uses a simple P controller and 100 ms delay.",
        ),
        (
            "thermal_loop_interval_ms",
            500 if "await this.delay(500)" in test_types else math.nan,
            "ms",
            "Thermal test checks telemetry every 500 ms.",
        ),
        (
            "public_raw_test_dataset_found",
            0,
            "bool",
            "Repository tree exposes protocol/source code and mock/test fixtures, but no real raw bench run CSVs were found.",
        ),
        (
            "readme_torque_measurement_integration_done",
            0 if "Torque measurement integration" in readme and "- [ ] Torque measurement integration" in readme else math.nan,
            "bool",
            "README roadmap lists torque measurement integration as unchecked.",
        ),
    ]
    for metric, value, unit, note in protocol_metrics:
        add_metric(
            rows,
            row_type="esc_test_propbench_packet_vayu_protocol",
            name="vayu_esc_studio",
            metric=metric,
            value=value,
            unit=unit,
            source_file=RAW / "vayu_test-types.ts",
            source_url=VAYU_REPO,
            evidence_role="open_fpv_bench_protocol_lead",
            note=note,
        )

    thrust_fields = [
        "Timestamp",
        "Throttle (%)",
        "Thrust (g)",
        "RPM",
        "Current (A)",
        "Voltage (V)",
        "Power (W)",
        "Efficiency (g/W)",
    ]
    torque_fields = [
        "Torque (mNm)",
        "Torque (Nm)",
        "Arm Length",
    ]
    for field in thrust_fields:
        add_metric(
            rows,
            row_type="esc_test_propbench_packet_vayu_csv_schema",
            name=f"vayu_thrust_csv_field_{field}",
            metric="field_present_in_export_path",
            value=1 if field in csv_export else 0,
            unit="bool",
            source_file=RAW / "vayu_csv-export.ts",
            source_url=VAYU_CSV_EXPORT,
            evidence_role="open_fpv_bench_protocol_lead",
            note="Vayu thrust-test CSV export schema; no public real run rows were found in the repository tree.",
        )
    for field in torque_fields:
        add_metric(
            rows,
            row_type="esc_test_propbench_packet_vayu_csv_schema",
            name=f"vayu_torque_csv_field_{field}",
            metric="field_present_in_export_path",
            value=1 if field in csv_export else 0,
            unit="bool",
            source_file=RAW / "vayu_csv-export.ts",
            source_url=VAYU_CSV_EXPORT,
            evidence_role="open_fpv_bench_protocol_lead",
            note="Vayu torque export computes torque from measured force and arm length; README still lists direct torque measurement integration as future work.",
        )


def add_rcbench_metrics(rows: list[dict[str, object]]) -> None:
    per_test: list[dict[str, float | str]] = []
    all_positive_slew: list[float] = []
    all_negative_slew: list[float] = []
    all_k: list[float] = []
    all_q_over_t: list[float] = []

    for key, meta in RCBENCH_FILES.items():
        source_path = RAW / meta["path"]
        source_url = raw_url(meta["path"])
        samples = parse_rcbench(source_path)
        duration_s = max(finite_values(samples, "time_s")) - min(finite_values(samples, "time_s"))
        valid_for_fit = [
            sample
            for sample in samples
            if sample["rpm"] >= 2500.0 and sample["thrust_n"] > 0.05 and math.isfinite(sample["omega2"])
        ]
        k_fit, k_r2, k_count = linear_fit_through_origin(
            [sample["omega2"] for sample in valid_for_fit],
            [sample["thrust_n"] for sample in valid_for_fit],
        )
        q_fit, q_r2, q_count = linear_fit_through_origin(
            [sample["thrust_n"] for sample in valid_for_fit if sample["torque_nm"] > 0.0],
            [sample["torque_nm"] for sample in valid_for_fit if sample["torque_nm"] > 0.0],
        )
        all_k.append(k_fit)
        all_q_over_t.append(q_fit)
        time_s = finite_values(samples, "time_s")
        rpm = finite_values(samples, "rpm")
        pos50, neg50, pos95_50, neg95_50 = rolling_slew(time_s, rpm, 0.050)
        pos100, neg100, pos95_100, neg95_100 = rolling_slew(time_s, rpm, 0.100)
        all_positive_slew.append(pos50)
        all_negative_slew.append(neg50)

        max_thrust = max(finite_values(samples, "thrust_n"))
        max_rpm = max(finite_values(samples, "rpm"))
        max_current = max(finite_values(samples, "current_a"))
        max_power = max(finite_values(samples, "electrical_power_w"))
        max_vibration = max(finite_values(samples, "vibration_g"))
        high_power_predicate = lambda sample: sample["rpm"] > 0.75 * max_rpm
        summary_metrics = [
            ("sample_count", len(samples), "count"),
            ("duration_s", duration_s, "s"),
            ("sample_rate_hz", len(samples) / duration_s if duration_s > 0.0 else math.nan, "Hz"),
            ("max_thrust_n", max_thrust, "N"),
            ("max_rpm", max_rpm, "rpm"),
            ("max_current_a", max_current, "A"),
            ("max_electrical_power_w", max_power, "W"),
            ("max_vibration_g", max_vibration, "g"),
            ("voltage_p50_v", median(finite_values(samples, "voltage_v")), "V"),
            ("thrust_coefficient_k_fit", k_fit, "N/(rad/s)^2"),
            ("thrust_coefficient_k_fit_r2", k_r2, "unitless"),
            ("thrust_coefficient_k_fit_sample_count", k_count, "count"),
            ("k_fit_over_current_racingQuad_k", safe_ratio(k_fit, RACING_QUAD_K), "ratio"),
            ("q_over_t_fit", q_fit, "m"),
            ("q_over_t_fit_r2", q_r2, "unitless"),
            ("q_over_t_fit_sample_count", q_count, "count"),
            ("q_over_t_fit_over_current_racingQuad", safe_ratio(q_fit, RACING_QUAD_Q_OVER_T_M), "ratio"),
            ("ct_high_rpm_p50", median(finite_values(samples, "ct", high_power_predicate)), "unitless"),
            ("cp_high_rpm_p50", median(finite_values(samples, "cp", high_power_predicate)), "unitless"),
            ("max_positive_slew_50ms_rpm_s", pos50, "rpm/s"),
            ("max_negative_slew_50ms_rpm_s", neg50, "rpm/s"),
            ("positive_slew_p95_50ms_rpm_s", pos95_50, "rpm/s"),
            ("negative_slew_p95_50ms_rpm_s", neg95_50, "rpm/s"),
            ("max_positive_slew_100ms_rpm_s", pos100, "rpm/s"),
            ("max_negative_slew_100ms_rpm_s", neg100, "rpm/s"),
            ("positive_slew_p95_100ms_rpm_s", pos95_100, "rpm/s"),
            ("negative_slew_p95_100ms_rpm_s", neg95_100, "rpm/s"),
            ("positive_50ms_slew_over_current_spinup_proxy", safe_ratio(pos50, RACING_QUAD_SPINUP_SLEW_RPM_S), "ratio"),
            ("negative_50ms_slew_over_current_braking_proxy", safe_ratio(neg50, RACING_QUAD_BRAKING_SLEW_RPM_S), "ratio"),
            ("tau_equiv_current_maxrpm_from_positive_50ms_slew", safe_ratio(RACING_QUAD_MAX_RPM, pos50), "s"),
            ("tau_equiv_current_maxrpm_from_negative_50ms_slew", safe_ratio(RACING_QUAD_MAX_RPM, neg50), "s"),
        ]
        for metric, value, unit in summary_metrics:
            add_metric(
                rows,
                row_type="esc_test_propbench_packet_rcbench_summary",
                name=key,
                metric=metric,
                value=value,
                unit=unit,
                source_file=source_path,
                source_url=source_url,
                evidence_role="open_rcbenchmark_bench_timeseries",
                note=(
                    "RCBenchmark column 12 is used as RPM following alspitz/esc_test formats.py; slew rows are inferred "
                    "from measured RPM traces, not logged command timestamps. Torque/mechanical-power fields are not "
                    "valid in the high-RPM thrust rows, so Q/T rows intentionally stay empty with sample_count=0."
                ),
                esc=meta["esc"],
                motor=meta["motor"],
                prop="DALPROP T7056 7x5.6x3",
            )

        per_test.append(
            {
                "key": key,
                "k_fit": k_fit,
                "q_fit": q_fit,
                "max_thrust": max_thrust,
                "max_rpm": max_rpm,
                "pos50": pos50,
                "neg50": neg50,
            }
        )

        binned: dict[int, list[dict[str, float]]] = {}
        for sample in samples:
            rpm_value = sample["rpm"]
            if not math.isfinite(rpm_value) or rpm_value < 1500.0 or not math.isfinite(sample["thrust_n"]):
                continue
            bin_key = int(round(rpm_value / 500.0) * 500)
            binned.setdefault(bin_key, []).append(sample)
        for bin_key, bin_samples in sorted(binned.items()):
            if len(bin_samples) < 20:
                continue
            rpm_p50 = median(finite_values(bin_samples, "rpm"))
            thrust_p50 = median(finite_values(bin_samples, "thrust_n"))
            torque_p50 = median(finite_values(bin_samples, "torque_nm"))
            omega = rpm_p50 * 2.0 * math.pi / 60.0 if math.isfinite(rpm_p50) else math.nan
            add_metric(
                rows,
                row_type="esc_test_propbench_packet_rcbench_rpm_bin",
                name=f"{key}_rpm_bin_{bin_key}",
                metric="rpm_bin_operating_point",
                value=thrust_p50,
                unit="N",
                source_file=source_path,
                source_url=source_url,
                evidence_role="open_rcbenchmark_bench_binned_curve",
                note=(
                    "RPM-binned operating point for fitting static thrust/current curves; 7-inch prop, static bench only. "
                    "Torque is present only if the source row reports it at this RPM bin."
                ),
                test_key=key,
                rpm_bin_center=bin_key,
                sample_count=len(bin_samples),
                rpm_p50=rpm_p50,
                thrust_n_p50=thrust_p50,
                torque_nm_p50=torque_p50,
                voltage_v_p50=median(finite_values(bin_samples, "voltage_v")),
                current_a_p50=median(finite_values(bin_samples, "current_a")),
                electrical_power_w_p50=median(finite_values(bin_samples, "electrical_power_w")),
                mechanical_power_w_p50=median(finite_values(bin_samples, "mechanical_power_w")),
                vibration_g_p50=median(finite_values(bin_samples, "vibration_g")),
                k_local=thrust_p50 / (omega * omega) if omega > 0.0 else math.nan,
                q_over_t_local=safe_ratio(torque_p50, thrust_p50),
                ct_p50=median(finite_values(bin_samples, "ct")),
                cp_p50=median(finite_values(bin_samples, "cp")),
            )

    summary_metrics = [
        ("rcbench_test_count", len(per_test), "count"),
        (
            "rcbench_sample_count_total",
            sum(
                int(row["value"])
                for row in rows
                if row.get("row_type") == "esc_test_propbench_packet_rcbench_summary"
                and row.get("metric") == "sample_count"
            ),
            "count",
        ),
        ("k_fit_p50", median(all_k), "N/(rad/s)^2"),
        ("k_fit_p10", percentile(all_k, 10.0), "N/(rad/s)^2"),
        ("k_fit_p90", percentile(all_k, 90.0), "N/(rad/s)^2"),
        ("k_fit_p50_over_current_racingQuad_k", safe_ratio(median(all_k), RACING_QUAD_K), "ratio"),
        ("q_over_t_fit_p50", median(all_q_over_t), "m"),
        ("q_over_t_fit_p10", percentile(all_q_over_t, 10.0), "m"),
        ("q_over_t_fit_p90", percentile(all_q_over_t, 90.0), "m"),
        ("q_over_t_fit_p50_over_current_racingQuad", safe_ratio(median(all_q_over_t), RACING_QUAD_Q_OVER_T_M), "ratio"),
        ("max_positive_slew_50ms_p50", median(all_positive_slew), "rpm/s"),
        ("max_positive_slew_50ms_max", max(value for value in all_positive_slew if math.isfinite(value)), "rpm/s"),
        ("max_positive_slew_50ms_max_over_current_spinup_proxy", safe_ratio(max(value for value in all_positive_slew if math.isfinite(value)), RACING_QUAD_SPINUP_SLEW_RPM_S), "ratio"),
        ("max_negative_slew_50ms_p50", median(all_negative_slew), "rpm/s"),
        ("max_negative_slew_50ms_max", max(value for value in all_negative_slew if math.isfinite(value)), "rpm/s"),
        ("max_negative_slew_50ms_max_over_current_braking_proxy", safe_ratio(max(value for value in all_negative_slew if math.isfinite(value)), RACING_QUAD_BRAKING_SLEW_RPM_S), "ratio"),
        ("current_racingQuad_max_rpm", RACING_QUAD_MAX_RPM, "rpm"),
        ("current_racingQuad_motor_tau_s", RACING_QUAD_MOTOR_TAU_S, "s"),
        ("current_racingQuad_braking_tau_proxy_s", RACING_QUAD_BRAKING_TAU_PROXY_S, "s"),
    ]
    for metric, value, unit in summary_metrics:
        add_metric(
            rows,
            row_type="esc_test_propbench_packet_overall_summary",
            name="alspitz_esc_test_rcbench_overall",
            metric=metric,
            value=value,
            unit=unit,
            source_file=RAW / "README.md",
            source_url=ESC_TEST_REPO,
            evidence_role="open_rcbenchmark_bench_summary",
            note=(
                "Overall statistics across six 7-inch static bench tests; use as adjacent ESC/motor/prop evidence, "
                "not a direct 5-inch coefficient transplant. The raw logs do not expose usable high-RPM torque rows."
            ),
        )


def add_autoquad_metrics(rows: list[dict[str, object]]) -> None:
    for key, meta in RCBENCH_FILES.items():
        autoquad_path = meta.get("autoquad_path")
        if not autoquad_path:
            continue
        source_path = RAW / autoquad_path
        samples = parse_autoquad(source_path)
        time_s = finite_values(samples, "time_s")
        rpm = finite_values(samples, "rpm")
        pos50, neg50, pos95_50, neg95_50 = rolling_slew(time_s, rpm, 0.050)
        duty_values = sorted(set(round(value, 3) for value in finite_values(samples, "duty_percent")))
        duty_transitions = 0
        prev = math.nan
        for sample in samples:
            duty = sample["duty_percent"]
            if not math.isfinite(duty):
                continue
            if math.isfinite(prev) and abs(duty - prev) >= 2.0:
                duty_transitions += 1
            prev = duty
        duration_s = max(time_s) - min(time_s) if time_s else math.nan
        metrics = [
            ("sample_count", len(samples), "count"),
            ("duration_s", duration_s, "s"),
            ("sample_rate_hz", len(samples) / duration_s if duration_s > 0.0 else math.nan, "Hz"),
            ("duty_level_count", len(duty_values), "count"),
            ("duty_transition_count_ge_2pct", duty_transitions, "count"),
            ("duty_min_percent", min(duty_values) if duty_values else math.nan, "%"),
            ("duty_max_percent", max(duty_values) if duty_values else math.nan, "%"),
            ("max_rpm", max(rpm) if rpm else math.nan, "rpm"),
            ("max_current_a", max(finite_values(samples, "current_a")), "A"),
            ("max_positive_slew_50ms_rpm_s", pos50, "rpm/s"),
            ("max_negative_slew_50ms_rpm_s", neg50, "rpm/s"),
            ("positive_slew_p95_50ms_rpm_s", pos95_50, "rpm/s"),
            ("negative_slew_p95_50ms_rpm_s", neg95_50, "rpm/s"),
            ("positive_50ms_slew_over_current_spinup_proxy", safe_ratio(pos50, RACING_QUAD_SPINUP_SLEW_RPM_S), "ratio"),
            ("negative_50ms_slew_over_current_braking_proxy", safe_ratio(neg50, RACING_QUAD_BRAKING_SLEW_RPM_S), "ratio"),
            ("comm_period_high_rpm_p50_us", median(finite_values(samples, "comm_period_us", lambda sample: sample["rpm"] > 0.75 * max(rpm))), "us"),
        ]
        for metric, value, unit in metrics:
            add_metric(
                rows,
                row_type="esc_test_propbench_packet_autoquad_esc_summary",
                name=key,
                metric=metric,
                value=value,
                unit=unit,
                source_file=source_path,
                source_url=raw_url(autoquad_path),
                evidence_role="open_autoquad_esc_side_timeseries",
                note="AutoQuad ECU log includes duty, current, voltage, RPM, and commutation period for the ESC side of selected tests.",
                esc=meta["esc"],
                motor=meta["motor"],
                prop="DALPROP T7056 7x5.6x3",
            )


def build_packet() -> list[dict[str, object]]:
    ensure_raw_files()
    rows: list[dict[str, object]] = []
    add_source_inventory(rows)
    add_test_metadata(rows)
    add_propbench_protocol(rows)
    add_vayu_protocol(rows)
    add_rcbench_metrics(rows)
    add_autoquad_metrics(rows)
    add_metric(
        rows,
        row_type="esc_test_propbench_packet_method",
        name="method_notes",
        metric="static_and_slew_packet_scope",
        value=1,
        unit="bool",
        source_file=OUTPUT,
        source_url=ESC_TEST_REPO,
        evidence_role="method",
        note=(
            "Downloads public raw logs, converts thrust gf to newtons, fits T=k*omega^2 and Q/T through origin, "
            "bins static operating points by 500 rpm, and estimates observed 50/100 ms RPM slew from measured traces. "
            "It does not claim direct command-lag for RCBenchmark-only logs because command timestamps are absent. "
            "It also does not fill yaw/Q-over-T calibration from this source because the high-RPM torque fields are empty."
        ),
    )
    return rows


def sync_summary(packet_rows: Iterable[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("esc_test_propbench_packet_")]
    added: list[dict[str, object]] = []
    for row in packet_rows:
        added.append(
            {
                "category": row["row_type"],
                "name": row["name"],
                "metric": row["metric"],
                "value": row["value"],
                "unit": row["unit"],
                "source_file": row["source_file"],
                "source_url": row["source_url"],
                "evidence_role": row["evidence_role"],
                "note": row["note"],
            }
        )
    write_csv(SUMMARY, kept + added)
    return len(added)


def main() -> None:
    rows = build_packet()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
