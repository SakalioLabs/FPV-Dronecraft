"""Extract NeuroBEM residual-aerodynamics summary rows.

Outputs:
  docs/data/neurobem_drag_residual_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category neurobem_residual_packet_*

The source archive is large but public. It is cached under docs/data/raw, which
is ignored by git in this project. The generated CSV keeps compact summaries
only: per-file residual/speed metrics, speed-bin rows, and simple drag-like
least-squares coefficients.
"""

from __future__ import annotations

import csv
import io
import math
import re
import tarfile
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "neurobem"
ARCHIVE = RAW / "predictions.tar.xz"
FLIGHTS = RAW / "Flights.txt"
TESTSET = RAW / "testset.txt"
OUTPUT = DATA / "neurobem_drag_residual_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"
AIRFRAME_PACKET = DATA / "airframe_drag_calibration_packet.csv"
DRONE_CONFIG_SOURCE = ROOT / "drone-sim-core" / "src" / "main" / "java" / "com" / "tenicana" / "dronecraft" / "sim" / "DroneConfig.java"

BASE_URL = "https://download.ifi.uzh.ch/rpg/NeuroBEM/"
PREDICTIONS_URL = BASE_URL + "predictions.tar.xz"
README_URL = BASE_URL + "Readme.md"
PROJECT_URL = "https://rpg.ifi.uzh.ch/NeuroBEM.html"

SAMPLE_STRIDE = 25
NEUROBEM_MASS_KG = 0.772
NEUROBEM_INERTIA_KG_M2 = (0.0025, 0.0021, 0.0043)
GRAVITY_M_S2 = 9.80665
NEUROBEM_WEIGHT_N = NEUROBEM_MASS_KG * GRAVITY_M_S2
MIN_SPEED_FOR_DRAG_LIKE_M_S = 0.15
MIN_ANGULAR_SPEED_FOR_TORQUE_DAMPING_RAD_S = 0.10
CURRENT_RACING_PROPWASH_MAX_TORQUE_NM = 0.035
CURRENT_RACING_ANGULAR_DRAG_COEFFICIENT = 0.018
SPEED_BINS = [
    (0.0, 0.5),
    (0.5, 1.0),
    (1.0, 1.5),
    (1.5, 2.0),
    (2.0, 2.5),
    (2.5, 3.0),
    (3.0, 4.0),
    (4.0, 6.0),
    (6.0, math.inf),
]
DATASET_RE = re.compile(r'dataset\s*=\s*"([^"]+)";\s*%\s*(.*)')
MEMBER_DATASET_RE = re.compile(r"(?P<dataset>\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2})_seg_(?P<segment>\d+)")
TARGET_VELOCITY_RE = re.compile(r"\bvel\s*=\s*([0-9]+(?:\.[0-9]+)?)")
TWR_RE = re.compile(r"([0-9]+(?:\.[0-9]+)?)\s*TWR", re.IGNORECASE)
RADIUS_RE = re.compile(r"(?:\bradius\b|\br\b)\s*(?:=|>)\s*([0-9]+(?:\.[0-9]+)?)")


@dataclass(frozen=True)
class FlightMetadata:
    dataset_id: str
    comment: str
    family: str
    target_velocity_m_s: float
    twr: float
    radius_m: float
    is_ccw: bool


class RunningStats:
    def __init__(self) -> None:
        self.count = 0
        self.total = 0.0
        self.total_sq = 0.0
        self.min_value = math.inf
        self.max_value = -math.inf

    def add(self, value: float) -> None:
        if not math.isfinite(value):
            return
        self.count += 1
        self.total += value
        self.total_sq += value * value
        self.min_value = min(self.min_value, value)
        self.max_value = max(self.max_value, value)

    @property
    def mean(self) -> float:
        return self.total / self.count if self.count else math.nan

    @property
    def rms(self) -> float:
        return math.sqrt(self.total_sq / self.count) if self.count else math.nan


class SampledStats(RunningStats):
    def __init__(self) -> None:
        super().__init__()
        self.samples: list[float] = []

    def add_sample(self, value: float) -> None:
        if math.isfinite(value):
            self.samples.append(value)

    def percentile(self, fraction: float) -> float:
        if not self.samples:
            return math.nan
        values = sorted(self.samples)
        index = max(0, min(len(values) - 1, round((len(values) - 1) * fraction)))
        return values[index]


class DragLikeFit:
    def __init__(self) -> None:
        self.count = 0
        self.sum_v_y = 0.0
        self.sum_v2 = 0.0
        self.sum_v2_y = 0.0
        self.sum_v4 = 0.0
        self.sum_v3 = 0.0

    def add(self, speed: float, drag_like: float) -> None:
        if speed <= MIN_SPEED_FOR_DRAG_LIKE_M_S or not math.isfinite(drag_like):
            return
        v2 = speed * speed
        self.count += 1
        self.sum_v_y += speed * drag_like
        self.sum_v2 += v2
        self.sum_v2_y += v2 * drag_like
        self.sum_v3 += v2 * speed
        self.sum_v4 += v2 * v2

    def linear_k(self) -> float:
        return self.sum_v_y / self.sum_v2 if self.sum_v2 else math.nan

    def quadratic_c(self) -> float:
        return self.sum_v2_y / self.sum_v4 if self.sum_v4 else math.nan

    def linear_quadratic(self) -> tuple[float, float]:
        determinant = self.sum_v2 * self.sum_v4 - self.sum_v3 * self.sum_v3
        if determinant == 0.0:
            return (math.nan, math.nan)
        linear = (self.sum_v_y * self.sum_v4 - self.sum_v2_y * self.sum_v3) / determinant
        quadratic = (self.sum_v2 * self.sum_v2_y - self.sum_v3 * self.sum_v_y) / determinant
        return (linear, quadratic)


class AxisQuadFit:
    def __init__(self) -> None:
        self.count = 0
        self.numerator = 0.0
        self.denominator = 0.0

    def add(self, velocity_axis: float, force_axis: float) -> None:
        phi = -velocity_axis * abs(velocity_axis)
        if not math.isfinite(phi) or not math.isfinite(force_axis):
            return
        self.count += 1
        self.numerator += phi * force_axis
        self.denominator += phi * phi

    def coefficient(self) -> float:
        return self.numerator / self.denominator if self.denominator else math.nan


class BinStats:
    def __init__(self, low: float, high: float) -> None:
        self.low = low
        self.high = high
        self.speed = SampledStats()
        self.residual_norm = SampledStats()
        self.drag_like = SampledStats()
        self.equivalent_quad = SampledStats()
        self.angular_speed = SampledStats()
        self.residual_torque_norm = SampledStats()
        self.torque_damping_like = SampledStats()
        self.equivalent_angular_damping = SampledStats()

    @property
    def name(self) -> str:
        high = "inf" if math.isinf(self.high) else f"{self.high:g}"
        return f"{self.low:g}_{high}_m_s"

    def contains(self, speed: float) -> bool:
        return self.low <= speed < self.high


class GroupStats:
    def __init__(self, name: str) -> None:
        self.name = name
        self.segment_count = 0
        self.test_segment_count = 0
        self.row_count = 0
        self.target_velocity = SampledStats()
        self.speed = SampledStats()
        self.residual_norm = SampledStats()
        self.drag_like = SampledStats()
        self.equivalent_quad = SampledStats()
        self.angular_speed = SampledStats()
        self.residual_torque_norm = SampledStats()
        self.torque_damping_like = SampledStats()
        self.equivalent_angular_damping = SampledStats()
        self.motor_rpm = SampledStats()
        self.vbat = SampledStats()

    def add_segment(self, row_count: int, metadata: FlightMetadata | None, is_testset: bool) -> None:
        self.segment_count += 1
        self.row_count += row_count
        if is_testset:
            self.test_segment_count += 1
        if metadata is not None and math.isfinite(metadata.target_velocity_m_s):
            self.target_velocity.add(metadata.target_velocity_m_s)
            self.target_velocity.add_sample(metadata.target_velocity_m_s)


def slug_text(value: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "_", value.lower()).strip("_")
    return slug or "unknown"


def trajectory_family(comment: str) -> str:
    lower = comment.lower().strip()
    if lower.startswith("wobbly circle"):
        return "wobbly_circle"
    if lower.startswith("3d circle"):
        return "three_d_circle"
    if lower.startswith("circle") or "short circle" in lower:
        return "circle"
    if lower.startswith("linear oscillation"):
        return "linear_oscillation"
    if lower.startswith("vertical oscillation"):
        return "vertical_oscillation"
    if lower.startswith("lemniscate"):
        return "lemniscate"
    if lower.startswith("cpc"):
        return "cpc"
    if lower.startswith("battery test"):
        return "battery_test"
    if lower.startswith("random points"):
        return "random_points"
    if lower.startswith("satellite"):
        return "satellite"
    if lower.startswith("ellipse"):
        return "ellipse"
    return slug_text(lower.split(",", 1)[0])


def parse_optional_float(pattern: re.Pattern[str], text: str) -> float:
    match = pattern.search(text)
    if not match:
        return math.nan
    return float(match.group(1))


def parse_flight_metadata() -> dict[str, FlightMetadata]:
    metadata: dict[str, FlightMetadata] = {}
    if not FLIGHTS.exists():
        return metadata
    for line in FLIGHTS.read_text(encoding="utf-8").splitlines():
        match = DATASET_RE.search(line)
        if not match:
            continue
        dataset_id = match.group(1)
        comment = match.group(2).strip()
        metadata[dataset_id] = FlightMetadata(
            dataset_id=dataset_id,
            comment=comment,
            family=trajectory_family(comment),
            target_velocity_m_s=parse_optional_float(TARGET_VELOCITY_RE, comment),
            twr=parse_optional_float(TWR_RE, comment),
            radius_m=parse_optional_float(RADIUS_RE, comment),
            is_ccw="ccw" in comment.lower(),
        )
    return metadata


def parse_testset_segments() -> set[str]:
    if not TESTSET.exists():
        return set()
    return {line.strip() for line in TESTSET.read_text(encoding="utf-8").splitlines() if line.strip()}


def member_segment_identity(name: str) -> tuple[str, str]:
    match = MEMBER_DATASET_RE.search(name)
    if not match:
        return ("", name.removesuffix(".csv").replace("bem+nn/", "").removeprefix("bem+nn_"))
    dataset_id = match.group("dataset")
    segment_id = f"{dataset_id}_seg_{match.group('segment')}"
    return (dataset_id, segment_id)


def target_velocity_group_name(value: float) -> str:
    if not math.isfinite(value):
        return "unknown_target_velocity"
    return f"{value:g}_m_s"


def safe_ratio(numerator: float, denominator: float) -> float:
    if not math.isfinite(numerator) or not math.isfinite(denominator) or abs(denominator) <= 1.0e-12:
        return math.nan
    return numerator / denominator


def add_observation_to_group(
    group: GroupStats,
    *,
    speed: float,
    residual_norm: float,
    drag_like: float,
    equivalent_quad: float,
    angular_speed: float,
    residual_torque_norm: float,
    torque_damping_like: float,
    equivalent_angular_damping: float,
    motor_rpm: float,
    vbat: float,
    sampled: bool,
) -> None:
    group.speed.add(speed)
    group.residual_norm.add(residual_norm)
    group.drag_like.add(drag_like)
    group.equivalent_quad.add(equivalent_quad)
    group.angular_speed.add(angular_speed)
    group.residual_torque_norm.add(residual_torque_norm)
    group.torque_damping_like.add(torque_damping_like)
    group.equivalent_angular_damping.add(equivalent_angular_damping)
    group.motor_rpm.add(motor_rpm)
    group.vbat.add(vbat)
    if sampled:
        group.speed.add_sample(speed)
        group.residual_norm.add_sample(residual_norm)
        group.drag_like.add_sample(drag_like)
        group.equivalent_quad.add_sample(equivalent_quad)
        group.angular_speed.add_sample(angular_speed)
        group.residual_torque_norm.add_sample(residual_torque_norm)
        group.torque_damping_like.add_sample(torque_damping_like)
        group.equivalent_angular_damping.add_sample(equivalent_angular_damping)
        group.motor_rpm.add_sample(motor_rpm)
        group.vbat.add_sample(vbat)


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


def add_metric(
    rows: list[dict[str, str]],
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
            "value": value_text(value),
            "unit": unit,
            "source_file": repo_path(source_file),
            "source_url": source_url,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def ensure_archive() -> None:
    RAW.mkdir(parents=True, exist_ok=True)
    if ARCHIVE.exists() and ARCHIVE.stat().st_size > 200_000_000:
        return
    with urllib.request.urlopen(PREDICTIONS_URL, timeout=60) as response, ARCHIVE.open("wb") as handle:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            handle.write(chunk)


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        return math.nan
    sorted_values = sorted(values)
    index = max(0, min(len(sorted_values) - 1, round((len(sorted_values) - 1) * fraction)))
    return sorted_values[index]


def racing_current_coefficients() -> tuple[float, float]:
    rows = read_rows(AIRFRAME_PACKET)
    x_force = next(
        float(row["value"])
        for row in rows
        if row.get("row_type") == "airframe_drag_packet_current_racing_drag"
        and row.get("name") == "racingQuad_x_10.0m_s"
        and row.get("metric") == "drag_force_n"
    )
    z_force = next(
        float(row["value"])
        for row in rows
        if row.get("row_type") == "airframe_drag_packet_current_racing_drag"
        and row.get("name") == "racingQuad_z_10.0m_s"
        and row.get("metric") == "drag_force_n"
    )
    return (x_force / 100.0, z_force / 100.0)


def add_stat_metrics(
    packet: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    stats: SampledStats,
    metric_prefix: str,
    unit: str,
    source_file: Path,
    source_url: str,
    evidence_role: str,
    note: str = "",
) -> None:
    for metric, value in [
        (f"{metric_prefix}_count", stats.count),
        (f"{metric_prefix}_mean", stats.mean),
        (f"{metric_prefix}_rms", stats.rms),
        (f"{metric_prefix}_sample_p50", stats.percentile(0.50)),
        (f"{metric_prefix}_sample_p95", stats.percentile(0.95)),
        (f"{metric_prefix}_max", stats.max_value),
    ]:
        add_metric(
            packet,
            row_type=row_type,
            name=name,
            metric=metric,
            value=value,
            unit="count" if metric.endswith("_count") else unit,
            source_file=source_file,
            source_url=source_url,
            evidence_role=evidence_role,
            note=note,
        )


def add_group_summary(
    packet: list[dict[str, str]],
    *,
    row_type: str,
    group: GroupStats,
    source_file: Path,
    source_url: str,
    evidence_role: str,
    note: str,
) -> None:
    for metric, value, unit in [
        ("segment_count", group.segment_count, "count"),
        ("testset_segment_count", group.test_segment_count, "count"),
        ("row_count", group.row_count, "count"),
        ("target_velocity_segment_count", group.target_velocity.count, "count"),
        ("target_velocity_sample_p50_m_s", group.target_velocity.percentile(0.50), "m/s"),
        ("target_velocity_sample_p95_m_s", group.target_velocity.percentile(0.95), "m/s"),
        ("body_speed_sample_p50_m_s", group.speed.percentile(0.50), "m/s"),
        ("body_speed_sample_p95_m_s", group.speed.percentile(0.95), "m/s"),
        ("residual_force_sample_p50_n", group.residual_norm.percentile(0.50), "N"),
        ("residual_force_sample_p95_n", group.residual_norm.percentile(0.95), "N"),
        ("drag_like_force_sample_p50_n", group.drag_like.percentile(0.50), "N"),
        ("drag_like_force_sample_p95_n", group.drag_like.percentile(0.95), "N"),
        ("equivalent_quad_coeff_sample_p50", group.equivalent_quad.percentile(0.50), "N/(m/s)^2"),
        ("angular_speed_sample_p50_rad_s", group.angular_speed.percentile(0.50), "rad/s"),
        ("angular_speed_sample_p95_rad_s", group.angular_speed.percentile(0.95), "rad/s"),
        ("residual_torque_sample_p50_nm", group.residual_torque_norm.percentile(0.50), "N*m"),
        ("residual_torque_sample_p95_nm", group.residual_torque_norm.percentile(0.95), "N*m"),
        ("torque_damping_like_sample_p50_nm", group.torque_damping_like.percentile(0.50), "N*m"),
        ("torque_damping_like_sample_p95_nm", group.torque_damping_like.percentile(0.95), "N*m"),
        (
            "equivalent_angular_damping_sample_p50_nm_per_rad_s",
            group.equivalent_angular_damping.percentile(0.50),
            "N*m/(rad/s)",
        ),
        (
            "equivalent_angular_damping_sample_p95_nm_per_rad_s",
            group.equivalent_angular_damping.percentile(0.95),
            "N*m/(rad/s)",
        ),
        ("motor_rpm_sample_p50", group.motor_rpm.percentile(0.50), "rpm"),
        ("vbat_scaled_0p1_sample_p50", group.vbat.percentile(0.50), "V"),
    ]:
        add_metric(
            packet,
            row_type=row_type,
            name=group.name,
            metric=metric,
            value=value,
            unit=unit,
            source_file=source_file,
            source_url=source_url,
            evidence_role=evidence_role,
            note=note,
        )


def iter_prediction_members(tar: tarfile.TarFile) -> list[tarfile.TarInfo]:
    return sorted(
        [member for member in tar.getmembers() if member.isfile() and member.name.lower().endswith(".csv")],
        key=lambda member: member.name,
    )


def safe_float_cells(row: list[str]) -> list[float] | None:
    if len(row) < 41:
        return None
    try:
        return [float(cell) for cell in row[:41]]
    except ValueError:
        return None


def build_packet() -> list[dict[str, str]]:
    ensure_archive()
    packet: list[dict[str, str]] = []
    flight_metadata = parse_flight_metadata()
    testset_segments = parse_testset_segments()
    family_groups: dict[str, GroupStats] = {}
    target_velocity_groups: dict[str, GroupStats] = {}
    bins = [BinStats(low, high) for low, high in SPEED_BINS]
    global_speed = SampledStats()
    global_residual_norm = SampledStats()
    global_drag_like = SampledStats()
    global_equivalent_quad = SampledStats()
    global_angular_speed = SampledStats()
    global_predicted_torque_norm = SampledStats()
    global_residual_torque_norm = SampledStats()
    global_torque_damping_like = SampledStats()
    global_equivalent_angular_damping = SampledStats()
    global_residual_torque_axis_abs = [SampledStats(), SampledStats(), SampledStats()]
    global_residual_angular_accel_axis_abs = [SampledStats(), SampledStats(), SampledStats()]
    global_motor_rpm = SampledStats()
    global_vbat = SampledStats()
    drag_fit = DragLikeFit()
    axis_fits = [AxisQuadFit(), AxisQuadFit(), AxisQuadFit()]

    file_count = 0
    total_rows = 0
    total_duration_s = 0.0
    invalid_rows = 0
    metadata_matched_files = 0
    testset_matched_files = 0
    archive_size_mb = ARCHIVE.stat().st_size / (1024.0 * 1024.0)

    x_current_coeff, z_current_coeff = racing_current_coefficients()

    add_metric(
        packet,
        row_type="neurobem_residual_packet_source_inventory",
        name="predictions_archive",
        metric="archive_size_mb",
        value=archive_size_mb,
        unit="MB",
        source_file=ARCHIVE,
        source_url=PREDICTIONS_URL,
        evidence_role="public_prediction_archive",
        note="Local raw cache is ignored by git; generated packet stores compact summaries only.",
    )
    add_metric(
        packet,
        row_type="neurobem_residual_packet_source_inventory",
        name="sampling",
        metric="sample_stride",
        value=SAMPLE_STRIDE,
        unit="rows",
        source_file=OUTPUT,
        source_url=PREDICTIONS_URL,
        evidence_role="method",
        note="Means, maxima, and least-squares sums scan all rows; percentile columns use this deterministic stride sample.",
    )
    add_metric(
        packet,
        row_type="neurobem_residual_packet_source_inventory",
        name="vehicle",
        metric="mass_kg",
        value=NEUROBEM_MASS_KG,
        unit="kg",
        source_file=OUTPUT,
        source_url=README_URL,
        evidence_role="dataset_metadata",
        note="Vehicle mass from NeuroBEM Readme.",
    )
    add_metric(
        packet,
        row_type="neurobem_residual_packet_source_inventory",
        name="battery_voltage_column",
        metric="raw_to_volts_scale",
        value=0.1,
        unit="scale",
        source_file=OUTPUT,
        source_url=README_URL,
        evidence_role="unit_audit",
        note=(
            "Readme labels column 29 as battery voltage [V], but CSV values are around 140..165; "
            "multiplying by 0.1 gives a plausible 4S pack range."
        ),
    )
    add_metric(
        packet,
        row_type="neurobem_residual_packet_source_inventory",
        name="flight_metadata",
        metric="flight_comment_count",
        value=len(flight_metadata),
        unit="count",
        source_file=FLIGHTS,
        source_url=BASE_URL + "Flights.txt",
        evidence_role="trajectory_metadata",
        note="Human-readable trajectory comments parsed for family, target velocity, TWR, radius, and ccw tags.",
    )
    add_metric(
        packet,
        row_type="neurobem_residual_packet_source_inventory",
        name="testset_segments",
        metric="segment_count",
        value=len(testset_segments),
        unit="count",
        source_file=TESTSET,
        source_url=BASE_URL + "testset.txt",
        evidence_role="official_test_split",
    )
    add_metric(
        packet,
        row_type="neurobem_residual_packet_source_inventory",
        name="prediction_columns",
        metric="residual_torque_columns",
        value="39..41",
        unit="1-based columns",
        source_file=RAW / "Readme.md",
        source_url=README_URL,
        evidence_role="column_schema",
        note="Readme defines columns 39..41 as residual torque body x/y/z [Nm].",
    )
    add_metric(
        packet,
        row_type="neurobem_residual_packet_current_model_anchor",
        name="racingQuad",
        metric="propwash_max_torque_nm",
        value=CURRENT_RACING_PROPWASH_MAX_TORQUE_NM,
        unit="N*m",
        source_file=DRONE_CONFIG_SOURCE,
        source_url=repo_path(DRONE_CONFIG_SOURCE),
        evidence_role="current_project_model_scale",
        note="Used only as a scale comparison against NeuroBEM residual torque norms.",
    )
    add_metric(
        packet,
        row_type="neurobem_residual_packet_current_model_anchor",
        name="racingQuad",
        metric="angular_drag_coefficient",
        value=CURRENT_RACING_ANGULAR_DRAG_COEFFICIENT,
        unit="N*m/(rad/s)",
        source_file=DRONE_CONFIG_SOURCE,
        source_url=repo_path(DRONE_CONFIG_SOURCE),
        evidence_role="current_project_model_scale",
        note="DronePhysics applies this as a baseline linear angular-rate damping coefficient.",
    )

    with tarfile.open(ARCHIVE, "r:xz") as tar:
        members = iter_prediction_members(tar)
        file_count = len(members)
        add_metric(
            packet,
            row_type="neurobem_residual_packet_source_inventory",
            name="predictions_archive",
            metric="csv_file_count",
            value=file_count,
            unit="count",
            source_file=ARCHIVE,
            source_url=PREDICTIONS_URL,
            evidence_role="public_prediction_archive",
        )
        for member in members:
            extracted = tar.extractfile(member)
            if extracted is None:
                continue
            text = io.TextIOWrapper(extracted, encoding="utf-8", newline="")
            reader = csv.reader(text)
            name = member.name.removesuffix(".csv").replace("bem+nn/", "")
            dataset_id, segment_id = member_segment_identity(name)
            metadata = flight_metadata.get(dataset_id)
            is_testset = segment_id in testset_segments
            if metadata is not None:
                metadata_matched_files += 1
            if is_testset:
                testset_matched_files += 1
            family_name = metadata.family if metadata is not None else "unknown"
            family_group = family_groups.setdefault(family_name, GroupStats(family_name))
            target_name = target_velocity_group_name(metadata.target_velocity_m_s if metadata is not None else math.nan)
            target_group = target_velocity_groups.setdefault(target_name, GroupStats(target_name))

            row_count = 0
            first_t = math.nan
            last_t = math.nan
            file_speed = SampledStats()
            file_residual_norm = SampledStats()
            file_drag_like = SampledStats()
            file_angular_speed = SampledStats()
            file_residual_torque_norm = SampledStats()
            file_torque_damping_like = SampledStats()
            file_equivalent_angular_damping = SampledStats()
            file_motor_rpm = SampledStats()
            file_vbat = SampledStats()

            for raw_row in reader:
                cells = safe_float_cells(raw_row)
                if cells is None:
                    invalid_rows += 1
                    continue
                row_count += 1
                total_rows += 1
                t = cells[0]
                if row_count == 1:
                    first_t = t
                last_t = t
                angular_velocity = cells[4:7]
                velocity = cells[14:17]
                motor_omega = cells[20:24]
                predicted_torque = cells[32:35]
                residual_force = cells[35:38]
                residual_torque = cells[38:41]
                vbat = cells[28] * 0.1
                speed = math.sqrt(sum(value * value for value in velocity))
                angular_speed = math.sqrt(sum(value * value for value in angular_velocity))
                predicted_torque_norm = math.sqrt(sum(value * value for value in predicted_torque))
                residual_norm = math.sqrt(sum(value * value for value in residual_force))
                residual_torque_norm = math.sqrt(sum(value * value for value in residual_torque))
                dot = sum(v * f for v, f in zip(velocity, residual_force))
                drag_like = -dot / speed if speed > MIN_SPEED_FOR_DRAG_LIKE_M_S else math.nan
                equivalent_quad = drag_like / (speed * speed) if speed > MIN_SPEED_FOR_DRAG_LIKE_M_S else math.nan
                torque_dot = sum(w * tau for w, tau in zip(angular_velocity, residual_torque))
                torque_damping_like = (
                    -torque_dot / angular_speed
                    if angular_speed > MIN_ANGULAR_SPEED_FOR_TORQUE_DAMPING_RAD_S
                    else math.nan
                )
                equivalent_angular_damping = (
                    torque_damping_like / angular_speed
                    if angular_speed > MIN_ANGULAR_SPEED_FOR_TORQUE_DAMPING_RAD_S
                    else math.nan
                )
                motor_rpm = sum(motor_omega) / len(motor_omega) * 60.0 / (2.0 * math.pi)

                file_speed.add(speed)
                file_residual_norm.add(residual_norm)
                file_drag_like.add(drag_like)
                file_angular_speed.add(angular_speed)
                file_residual_torque_norm.add(residual_torque_norm)
                file_torque_damping_like.add(torque_damping_like)
                file_equivalent_angular_damping.add(equivalent_angular_damping)
                file_motor_rpm.add(motor_rpm)
                file_vbat.add(vbat)
                global_speed.add(speed)
                global_residual_norm.add(residual_norm)
                global_drag_like.add(drag_like)
                global_equivalent_quad.add(equivalent_quad)
                global_angular_speed.add(angular_speed)
                global_predicted_torque_norm.add(predicted_torque_norm)
                global_residual_torque_norm.add(residual_torque_norm)
                global_torque_damping_like.add(torque_damping_like)
                global_equivalent_angular_damping.add(equivalent_angular_damping)
                global_motor_rpm.add(motor_rpm)
                global_vbat.add(vbat)
                drag_fit.add(speed, drag_like)
                for axis in range(3):
                    axis_fits[axis].add(velocity[axis], residual_force[axis])
                    global_residual_torque_axis_abs[axis].add(abs(residual_torque[axis]))
                    global_residual_angular_accel_axis_abs[axis].add(abs(residual_torque[axis]) / NEUROBEM_INERTIA_KG_M2[axis])

                sampled = total_rows % SAMPLE_STRIDE == 0
                if sampled:
                    file_speed.add_sample(speed)
                    file_residual_norm.add_sample(residual_norm)
                    file_drag_like.add_sample(drag_like)
                    file_angular_speed.add_sample(angular_speed)
                    file_residual_torque_norm.add_sample(residual_torque_norm)
                    file_torque_damping_like.add_sample(torque_damping_like)
                    file_equivalent_angular_damping.add_sample(equivalent_angular_damping)
                    file_motor_rpm.add_sample(motor_rpm)
                    file_vbat.add_sample(vbat)
                    global_speed.add_sample(speed)
                    global_residual_norm.add_sample(residual_norm)
                    global_drag_like.add_sample(drag_like)
                    global_equivalent_quad.add_sample(equivalent_quad)
                    global_angular_speed.add_sample(angular_speed)
                    global_predicted_torque_norm.add_sample(predicted_torque_norm)
                    global_residual_torque_norm.add_sample(residual_torque_norm)
                    global_torque_damping_like.add_sample(torque_damping_like)
                    global_equivalent_angular_damping.add_sample(equivalent_angular_damping)
                    for axis in range(3):
                        global_residual_torque_axis_abs[axis].add_sample(abs(residual_torque[axis]))
                        global_residual_angular_accel_axis_abs[axis].add_sample(
                            abs(residual_torque[axis]) / NEUROBEM_INERTIA_KG_M2[axis]
                        )
                    global_motor_rpm.add_sample(motor_rpm)
                    global_vbat.add_sample(vbat)
                    for bin_stats in bins:
                        if bin_stats.contains(speed):
                            bin_stats.speed.add_sample(speed)
                            bin_stats.residual_norm.add_sample(residual_norm)
                            bin_stats.drag_like.add_sample(drag_like)
                            bin_stats.equivalent_quad.add_sample(equivalent_quad)
                            bin_stats.angular_speed.add_sample(angular_speed)
                            bin_stats.residual_torque_norm.add_sample(residual_torque_norm)
                            bin_stats.torque_damping_like.add_sample(torque_damping_like)
                            bin_stats.equivalent_angular_damping.add_sample(equivalent_angular_damping)
                            break
                add_observation_to_group(
                    family_group,
                    speed=speed,
                    residual_norm=residual_norm,
                    drag_like=drag_like,
                    equivalent_quad=equivalent_quad,
                    angular_speed=angular_speed,
                    residual_torque_norm=residual_torque_norm,
                    torque_damping_like=torque_damping_like,
                    equivalent_angular_damping=equivalent_angular_damping,
                    motor_rpm=motor_rpm,
                    vbat=vbat,
                    sampled=sampled,
                )
                add_observation_to_group(
                    target_group,
                    speed=speed,
                    residual_norm=residual_norm,
                    drag_like=drag_like,
                    equivalent_quad=equivalent_quad,
                    angular_speed=angular_speed,
                    residual_torque_norm=residual_torque_norm,
                    torque_damping_like=torque_damping_like,
                    equivalent_angular_damping=equivalent_angular_damping,
                    motor_rpm=motor_rpm,
                    vbat=vbat,
                    sampled=sampled,
                )
                for bin_stats in bins:
                    if bin_stats.contains(speed):
                        bin_stats.speed.add(speed)
                        bin_stats.residual_norm.add(residual_norm)
                        bin_stats.drag_like.add(drag_like)
                        bin_stats.equivalent_quad.add(equivalent_quad)
                        bin_stats.angular_speed.add(angular_speed)
                        bin_stats.residual_torque_norm.add(residual_torque_norm)
                        bin_stats.torque_damping_like.add(torque_damping_like)
                        bin_stats.equivalent_angular_damping.add(equivalent_angular_damping)
                        break

            duration_s = (last_t - first_t) if math.isfinite(first_t) and math.isfinite(last_t) else math.nan
            if math.isfinite(duration_s):
                total_duration_s += duration_s
            family_group.add_segment(row_count, metadata, is_testset)
            target_group.add_segment(row_count, metadata, is_testset)
            for metric, value, unit in [
                ("dataset_id", dataset_id, "text"),
                ("segment_id", segment_id, "text"),
                ("trajectory_family", family_name, "text"),
                (
                    "target_velocity_m_s",
                    metadata.target_velocity_m_s if metadata is not None else math.nan,
                    "m/s",
                ),
                ("twr", metadata.twr if metadata is not None else math.nan, "ratio"),
                ("radius_m", metadata.radius_m if metadata is not None else math.nan, "m"),
                ("is_ccw", metadata.is_ccw if metadata is not None else False, "boolean"),
                ("is_testset_segment", is_testset, "boolean"),
                ("flight_comment", metadata.comment if metadata is not None else "", "text"),
            ]:
                add_metric(
                    packet,
                    row_type="neurobem_residual_packet_file_metadata",
                    name=name,
                    metric=metric,
                    value=value,
                    unit=unit,
                    source_file=FLIGHTS if metric != "is_testset_segment" else TESTSET,
                    source_url=BASE_URL + ("Flights.txt" if metric != "is_testset_segment" else "testset.txt"),
                    evidence_role="trajectory_metadata_join",
                    note="Metadata joined by dataset timestamp parsed from the prediction CSV filename.",
                )
            add_metric(
                packet,
                row_type="neurobem_residual_packet_file_summary",
                name=name,
                metric="row_count",
                value=row_count,
                unit="count",
                source_file=ARCHIVE,
                source_url=PREDICTIONS_URL,
                evidence_role="per_file_residual_summary",
            )
            add_metric(
                packet,
                row_type="neurobem_residual_packet_file_summary",
                name=name,
                metric="duration_s",
                value=duration_s,
                unit="s",
                source_file=ARCHIVE,
                source_url=PREDICTIONS_URL,
                evidence_role="per_file_residual_summary",
            )
            add_stat_metrics(
                packet,
                row_type="neurobem_residual_packet_file_summary",
                name=name,
                stats=file_speed,
                metric_prefix="body_speed_m_s",
                unit="m/s",
                source_file=ARCHIVE,
                source_url=PREDICTIONS_URL,
                evidence_role="per_file_residual_summary",
            )
            add_stat_metrics(
                packet,
                row_type="neurobem_residual_packet_file_summary",
                name=name,
                stats=file_residual_norm,
                metric_prefix="residual_force_norm_n",
                unit="N",
                source_file=ARCHIVE,
                source_url=PREDICTIONS_URL,
                evidence_role="per_file_residual_summary",
            )
            add_stat_metrics(
                packet,
                row_type="neurobem_residual_packet_file_summary",
                name=name,
                stats=file_drag_like,
                metric_prefix="drag_like_force_n",
                unit="N",
                source_file=ARCHIVE,
                source_url=PREDICTIONS_URL,
                evidence_role="per_file_residual_summary",
                note="Positive drag_like_force_n means residual force opposes body velocity.",
            )
            add_stat_metrics(
                packet,
                row_type="neurobem_residual_packet_file_summary",
                name=name,
                stats=file_angular_speed,
                metric_prefix="angular_speed_rad_s",
                unit="rad/s",
                source_file=ARCHIVE,
                source_url=PREDICTIONS_URL,
                evidence_role="per_file_residual_summary",
            )
            add_stat_metrics(
                packet,
                row_type="neurobem_residual_packet_file_summary",
                name=name,
                stats=file_residual_torque_norm,
                metric_prefix="residual_torque_norm_nm",
                unit="N*m",
                source_file=ARCHIVE,
                source_url=PREDICTIONS_URL,
                evidence_role="per_file_residual_summary",
            )
            add_stat_metrics(
                packet,
                row_type="neurobem_residual_packet_file_summary",
                name=name,
                stats=file_torque_damping_like,
                metric_prefix="torque_damping_like_nm",
                unit="N*m",
                source_file=ARCHIVE,
                source_url=PREDICTIONS_URL,
                evidence_role="per_file_residual_summary",
                note="Positive torque_damping_like means residual torque opposes angular velocity.",
            )
            add_stat_metrics(
                packet,
                row_type="neurobem_residual_packet_file_summary",
                name=name,
                stats=file_equivalent_angular_damping,
                metric_prefix="equivalent_angular_damping_nm_per_rad_s",
                unit="N*m/(rad/s)",
                source_file=ARCHIVE,
                source_url=PREDICTIONS_URL,
                evidence_role="per_file_residual_summary",
                note="Computed only when angular speed is above the script threshold.",
            )
            add_stat_metrics(
                packet,
                row_type="neurobem_residual_packet_file_summary",
                name=name,
                stats=file_motor_rpm,
                metric_prefix="motor_rpm_mean",
                unit="rpm",
                source_file=ARCHIVE,
                source_url=PREDICTIONS_URL,
                evidence_role="per_file_residual_summary",
            )
            add_stat_metrics(
                packet,
                row_type="neurobem_residual_packet_file_summary",
                name=name,
                stats=file_vbat,
                metric_prefix="vbat_scaled_0p1_v",
                unit="V",
                source_file=ARCHIVE,
                source_url=PREDICTIONS_URL,
                evidence_role="per_file_residual_summary",
            )

    linear_k, quadratic_c = drag_fit.linear_quadratic()
    global_speed_p95 = global_speed.percentile(0.95)
    global_residual_p95 = global_residual_norm.percentile(0.95)
    global_residual_torque_p95 = global_residual_torque_norm.percentile(0.95)
    global_equivalent_angular_damping_p95 = global_equivalent_angular_damping.percentile(0.95)
    current_x_drag_10m_s = x_current_coeff * 10.0 * 10.0
    current_z_drag_10m_s = z_current_coeff * 10.0 * 10.0
    current_x_drag_at_neurobem_p95_speed = x_current_coeff * global_speed_p95 * global_speed_p95
    current_z_drag_at_neurobem_p95_speed = z_current_coeff * global_speed_p95 * global_speed_p95

    global_metrics = [
        ("csv_file_count", file_count, "count"),
        ("total_row_count", total_rows, "count"),
        ("invalid_row_count", invalid_rows, "count"),
        ("metadata_matched_file_count", metadata_matched_files, "count"),
        ("testset_matched_file_count", testset_matched_files, "count"),
        ("total_duration_s", total_duration_s, "s"),
        ("total_duration_min", total_duration_s / 60.0, "min"),
        ("body_speed_sample_p50_m_s", global_speed.percentile(0.50), "m/s"),
        ("body_speed_sample_p95_m_s", global_speed_p95, "m/s"),
        ("body_speed_max_m_s", global_speed.max_value, "m/s"),
        ("residual_force_sample_p50_n", global_residual_norm.percentile(0.50), "N"),
        ("residual_force_sample_p95_n", global_residual_p95, "N"),
        ("residual_force_max_n", global_residual_norm.max_value, "N"),
        ("residual_force_sample_p95_over_weight", global_residual_p95 / NEUROBEM_WEIGHT_N, "ratio"),
        ("angular_speed_sample_p50_rad_s", global_angular_speed.percentile(0.50), "rad/s"),
        ("angular_speed_sample_p95_rad_s", global_angular_speed.percentile(0.95), "rad/s"),
        ("predicted_torque_sample_p50_nm", global_predicted_torque_norm.percentile(0.50), "N*m"),
        ("predicted_torque_sample_p95_nm", global_predicted_torque_norm.percentile(0.95), "N*m"),
        ("residual_torque_sample_p50_nm", global_residual_torque_norm.percentile(0.50), "N*m"),
        ("residual_torque_sample_p95_nm", global_residual_torque_p95, "N*m"),
        ("residual_torque_max_nm", global_residual_torque_norm.max_value, "N*m"),
        (
            "residual_torque_sample_p95_over_current_racing_propwash_max_torque",
            safe_ratio(global_residual_torque_p95, CURRENT_RACING_PROPWASH_MAX_TORQUE_NM),
            "ratio",
        ),
        ("residual_torque_abs_x_sample_p95_nm", global_residual_torque_axis_abs[0].percentile(0.95), "N*m"),
        ("residual_torque_abs_y_sample_p95_nm", global_residual_torque_axis_abs[1].percentile(0.95), "N*m"),
        ("residual_torque_abs_z_sample_p95_nm", global_residual_torque_axis_abs[2].percentile(0.95), "N*m"),
        (
            "residual_torque_abs_x_equiv_angular_accel_sample_p95_rad_s2",
            global_residual_angular_accel_axis_abs[0].percentile(0.95),
            "rad/s^2",
        ),
        (
            "residual_torque_abs_y_equiv_angular_accel_sample_p95_rad_s2",
            global_residual_angular_accel_axis_abs[1].percentile(0.95),
            "rad/s^2",
        ),
        (
            "residual_torque_abs_z_equiv_angular_accel_sample_p95_rad_s2",
            global_residual_angular_accel_axis_abs[2].percentile(0.95),
            "rad/s^2",
        ),
        ("torque_damping_like_sample_p50_nm", global_torque_damping_like.percentile(0.50), "N*m"),
        ("torque_damping_like_sample_p95_nm", global_torque_damping_like.percentile(0.95), "N*m"),
        (
            "equivalent_angular_damping_sample_p50_nm_per_rad_s",
            global_equivalent_angular_damping.percentile(0.50),
            "N*m/(rad/s)",
        ),
        (
            "equivalent_angular_damping_sample_p95_nm_per_rad_s",
            global_equivalent_angular_damping_p95,
            "N*m/(rad/s)",
        ),
        (
            "equivalent_angular_damping_sample_p95_over_current_racing_angular_drag_coeff",
            safe_ratio(global_equivalent_angular_damping_p95, CURRENT_RACING_ANGULAR_DRAG_COEFFICIENT),
            "ratio",
        ),
        ("drag_like_force_sample_p50_n", global_drag_like.percentile(0.50), "N"),
        ("drag_like_force_sample_p95_n", global_drag_like.percentile(0.95), "N"),
        ("equivalent_quad_coeff_sample_p50", global_equivalent_quad.percentile(0.50), "N/(m/s)^2"),
        ("equivalent_quad_coeff_sample_p95", global_equivalent_quad.percentile(0.95), "N/(m/s)^2"),
        ("drag_like_linear_fit_k", drag_fit.linear_k(), "N/(m/s)"),
        ("drag_like_quadratic_fit_c", drag_fit.quadratic_c(), "N/(m/s)^2"),
        ("drag_like_linear_plus_quad_fit_k", linear_k, "N/(m/s)"),
        ("drag_like_linear_plus_quad_fit_c", quadratic_c, "N/(m/s)^2"),
        ("axis_x_quadratic_residual_coeff", axis_fits[0].coefficient(), "N/(m/s)^2"),
        ("axis_y_quadratic_residual_coeff", axis_fits[1].coefficient(), "N/(m/s)^2"),
        ("axis_z_quadratic_residual_coeff", axis_fits[2].coefficient(), "N/(m/s)^2"),
        ("current_racing_x_quadratic_coeff", x_current_coeff, "N/(m/s)^2"),
        ("current_racing_z_quadratic_coeff", z_current_coeff, "N/(m/s)^2"),
        ("current_racing_x_drag_10m_s_n", current_x_drag_10m_s, "N"),
        ("current_racing_z_drag_10m_s_n", current_z_drag_10m_s, "N"),
        ("current_racing_x_drag_10m_s_over_neurobem_residual_p95", current_x_drag_10m_s / global_residual_p95, "ratio"),
        ("current_racing_z_drag_10m_s_over_neurobem_residual_p95", current_z_drag_10m_s / global_residual_p95, "ratio"),
        ("current_racing_x_drag_at_neurobem_p95_speed_n", current_x_drag_at_neurobem_p95_speed, "N"),
        ("current_racing_z_drag_at_neurobem_p95_speed_n", current_z_drag_at_neurobem_p95_speed, "N"),
        (
            "current_racing_x_drag_at_neurobem_p95_speed_over_neurobem_residual_p95",
            current_x_drag_at_neurobem_p95_speed / global_residual_p95,
            "ratio",
        ),
        (
            "current_racing_z_drag_at_neurobem_p95_speed_over_neurobem_residual_p95",
            current_z_drag_at_neurobem_p95_speed / global_residual_p95,
            "ratio",
        ),
        ("current_racing_x_over_neurobem_drag_like_quad_fit", x_current_coeff / drag_fit.quadratic_c(), "ratio"),
        ("current_racing_z_over_neurobem_drag_like_quad_fit", z_current_coeff / drag_fit.quadratic_c(), "ratio"),
        ("motor_rpm_sample_p50", global_motor_rpm.percentile(0.50), "rpm"),
        ("motor_rpm_sample_p95", global_motor_rpm.percentile(0.95), "rpm"),
        ("vbat_scaled_0p1_sample_p50", global_vbat.percentile(0.50), "V"),
        ("vbat_scaled_0p1_sample_p05", global_vbat.percentile(0.05), "V"),
    ]
    for metric, value, unit in global_metrics:
        add_metric(
            packet,
            row_type="neurobem_residual_packet_global_summary",
            name="global",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=PREDICTIONS_URL,
            evidence_role="global_residual_summary",
            note="Percentile metrics are deterministic stride-sampled; fit and mean metrics scan all rows.",
        )

    for bin_stats in bins:
        for metric, value, unit in [
            ("row_count", bin_stats.speed.count, "count"),
            ("speed_mean_m_s", bin_stats.speed.mean, "m/s"),
            ("speed_sample_p50_m_s", bin_stats.speed.percentile(0.50), "m/s"),
            ("residual_force_mean_n", bin_stats.residual_norm.mean, "N"),
            ("residual_force_sample_p95_n", bin_stats.residual_norm.percentile(0.95), "N"),
            ("drag_like_force_mean_n", bin_stats.drag_like.mean, "N"),
            ("drag_like_force_sample_p50_n", bin_stats.drag_like.percentile(0.50), "N"),
            ("drag_like_force_sample_p95_n", bin_stats.drag_like.percentile(0.95), "N"),
            ("equivalent_quad_coeff_mean", bin_stats.equivalent_quad.mean, "N/(m/s)^2"),
            ("equivalent_quad_coeff_sample_p50", bin_stats.equivalent_quad.percentile(0.50), "N/(m/s)^2"),
            ("equivalent_quad_coeff_sample_p95", bin_stats.equivalent_quad.percentile(0.95), "N/(m/s)^2"),
            ("angular_speed_sample_p50_rad_s", bin_stats.angular_speed.percentile(0.50), "rad/s"),
            ("angular_speed_sample_p95_rad_s", bin_stats.angular_speed.percentile(0.95), "rad/s"),
            ("residual_torque_sample_p50_nm", bin_stats.residual_torque_norm.percentile(0.50), "N*m"),
            ("residual_torque_sample_p95_nm", bin_stats.residual_torque_norm.percentile(0.95), "N*m"),
            ("torque_damping_like_sample_p50_nm", bin_stats.torque_damping_like.percentile(0.50), "N*m"),
            ("torque_damping_like_sample_p95_nm", bin_stats.torque_damping_like.percentile(0.95), "N*m"),
            (
                "equivalent_angular_damping_sample_p50_nm_per_rad_s",
                bin_stats.equivalent_angular_damping.percentile(0.50),
                "N*m/(rad/s)",
            ),
            (
                "equivalent_angular_damping_sample_p95_nm_per_rad_s",
                bin_stats.equivalent_angular_damping.percentile(0.95),
                "N*m/(rad/s)",
            ),
        ]:
            add_metric(
                packet,
                row_type="neurobem_residual_packet_speed_bin",
                name=bin_stats.name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=OUTPUT,
                source_url=PREDICTIONS_URL,
                evidence_role="speed_binned_residual_summary",
                note="Positive drag_like_force means residual force opposes body velocity.",
            )

    for group in sorted(family_groups.values(), key=lambda item: item.name):
        add_group_summary(
            packet,
            row_type="neurobem_residual_packet_trajectory_family_summary",
            group=group,
            source_file=OUTPUT,
            source_url=f"{PREDICTIONS_URL}; {BASE_URL}Flights.txt",
            evidence_role="trajectory_family_residual_summary",
            note=(
                "Family labels come from Flights.txt comments; residuals are NeuroBEM model residuals, "
                "not isolated aerodynamic coefficients."
            ),
        )

    for group in sorted(target_velocity_groups.values(), key=lambda item: item.name):
        add_group_summary(
            packet,
            row_type="neurobem_residual_packet_target_velocity_summary",
            group=group,
            source_file=OUTPUT,
            source_url=f"{PREDICTIONS_URL}; {BASE_URL}Flights.txt",
            evidence_role="target_velocity_residual_summary",
            note="Target velocity is parsed from human-readable flight comments; measured speeds can be much higher.",
        )

    add_metric(
        packet,
        row_type="neurobem_residual_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "NeuroBEM residual forces/torques are model residuals for a 0.772 kg quadrotor at mostly low target speeds. "
            "Use drag_like_force = -dot(residual_force_body, velocity_body)/|velocity_body| as a compact "
            "opposes-motion proxy, and torque_damping_like = -dot(residual_torque_body, angular_velocity_body)/|omega| "
            "as an angular damping proxy. They are not isolated wind-tunnel CdA or moment-coefficient data. "
            "Trajectory-family labels and target velocities are parsed from Flights.txt comments."
        ),
        unit="text",
        source_file=OUTPUT,
        source_url=f"{PROJECT_URL}; {PREDICTIONS_URL}",
        evidence_role="method",
        note="Keep this separate from RATM/UZH high-speed envelope evidence.",
    )
    return packet


def sync_summary(packet_rows: Iterable[dict[str, str]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("neurobem_residual_packet_")]
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
    packet = build_packet()
    write_csv(OUTPUT, packet)
    synced = sync_summary(packet)
    print(f"Wrote {repo_path(OUTPUT)} with {len(packet)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
