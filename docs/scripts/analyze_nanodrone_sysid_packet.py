#!/usr/bin/env python3
"""Build a Nano-Quadrotor system-identification handoff packet.

Outputs:
  docs/data/nanodrone_sysid_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category nanodrone_sysid_packet_*

The IDSIA benchmark is a Crazyflie 2.1 Brushless nano-quadrotor dataset. It is
not a 5-inch FPV airframe, so the useful signal here is model semantics and
coefficient sanity: motor angular velocities are in rad/s, the reference model
uses omega^2 thrust/torque maps, and the dataset provides synchronized
full-state trajectories at 100 Hz.
"""

from __future__ import annotations

import csv
import math
import urllib.request
import zipfile
from pathlib import Path
from typing import Iterable

import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw"

ZIP_PATH = RAW / "nanodrone-sysid-benchmark-main.zip"
EXTRACT_PARENT = RAW / "nanodrone-sysid-benchmark-main"
REPO_DIR = EXTRACT_PARENT / "nanodrone-sysid-benchmark-main"
REPO_DATA = REPO_DIR / "data"
README = REPO_DIR / "README.md"
MODEL_SOURCE = REPO_DIR / "models" / "models.py"
DATASET_SOURCE = REPO_DIR / "dataset" / "dataset.py"

OUTPUT = DATA / "nanodrone_sysid_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

REPO_URL = "https://github.com/idsia-robotics/nanodrone-sysid-benchmark"
README_URL = "https://raw.githubusercontent.com/idsia-robotics/nanodrone-sysid-benchmark/main/README.md"
CODELOAD_URL = "https://codeload.github.com/idsia-robotics/nanodrone-sysid-benchmark/zip/refs/heads/main"
MEDIA_BASE_URL = "https://media.githubusercontent.com/media/idsia-robotics/nanodrone-sysid-benchmark/main/data"
SCIENCEDIRECT_URL = "https://www.sciencedirect.com/science/article/pii/S0967066126001152"
ARXIV_URL = "https://arxiv.org/abs/2512.14450"

EXPECTED_COLUMNS = [
    "t",
    "x",
    "y",
    "z",
    "qx",
    "qy",
    "qz",
    "qw",
    "vx",
    "vy",
    "vz",
    "wx",
    "wy",
    "wz",
    "m1_rads",
    "m2_rads",
    "m3_rads",
    "m4_rads",
    "ax_body",
    "ay_body",
    "az_body",
]

DT_S = 0.01
SAMPLE_RATE_HZ = 100.0
SOURCE_SAMPLE_CLAIM = 75_000
BENCHMARK_HORIZON_STEPS = 50.0
BENCHMARK_HORIZON_S = 0.5

MASS_KG = 0.045
G_M_S2 = 9.81
ARM_M = 0.0353
SOURCE_KT_N_PER_RAD2 = 3.72e-08
SOURCE_KC_NM_PER_RAD2 = 7.74e-12
SOURCE_THRUST_TO_WEIGHT = 2.0
SOURCE_MAX_TORQUE_NM = np.asarray([1e-2, 1e-2, 3e-3], dtype=float)
SOURCE_J_KG_M2 = np.asarray([2.3951e-5, 2.3951e-5, 3.2347e-6], dtype=float)

RACINGQUAD_MASS_KG = 1.1
RACINGQUAD_ROTOR_K_N_PER_RAD2 = 1.45e-6
RACINGQUAD_MAX_RPM_PROXY = 29138.0
RACINGQUAD_MAX_RAD_S_PROXY = RACINGQUAD_MAX_RPM_PROXY * 2.0 * math.pi / 60.0
RACINGQUAD_HOVER_RAD_S_PROXY = math.sqrt(
    RACINGQUAD_MASS_KG * G_M_S2 / (4.0 * RACINGQUAD_ROTOR_K_N_PER_RAD2)
)


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


def download_if_missing(url: str, path: Path) -> None:
    if path.exists() and path.stat().st_size > 0:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url, timeout=120) as response, path.open("wb") as handle:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            handle.write(chunk)


def ensure_repo() -> None:
    if not REPO_DIR.exists():
        download_if_missing(CODELOAD_URL, ZIP_PATH)
        EXTRACT_PARENT.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(ZIP_PATH) as archive:
            archive.extractall(EXTRACT_PARENT)
    if not REPO_DIR.exists():
        raise FileNotFoundError(f"Expected extracted repo at {REPO_DIR}")


def pointer_csv_files() -> list[Path]:
    return sorted(path for path in REPO_DATA.rglob("*.csv") if not path.name.endswith(".actual.csv"))


def is_lfs_pointer(path: Path) -> bool:
    head = path.read_text(encoding="utf-8", errors="replace")[:120]
    return head.startswith("version https://git-lfs.github.com/spec")


def ensure_actual_csvs() -> list[Path]:
    ensure_repo()
    actual_paths: list[Path] = []
    for pointer in pointer_csv_files():
        actual = pointer.with_name(pointer.stem + ".actual.csv")
        if not actual.exists() or actual.stat().st_size < 10_000:
            if not is_lfs_pointer(pointer):
                actual = pointer
            else:
                rel = pointer.relative_to(REPO_DATA).as_posix()
                download_if_missing(f"{MEDIA_BASE_URL}/{rel}", actual)
        actual_paths.append(actual)
    actual_paths = [path for path in actual_paths if path.exists() and path.stat().st_size > 10_000]
    if not actual_paths:
        raise FileNotFoundError("No downloaded Nano-Quadrotor actual CSV files found.")
    return sorted(actual_paths)


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path | str,
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
            "source_file": repo_path(source_file) if isinstance(source_file, Path) else source_file,
            "source_url": source_url,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def percentile(values: Iterable[float], p: float) -> float:
    clean = np.asarray([float(value) for value in values if math.isfinite(float(value))], dtype=float)
    if clean.size == 0:
        return math.nan
    return float(np.percentile(clean, p))


def trajectory_from_file(path: Path) -> str:
    return path.stem.split("_")[0]


def split_from_file(path: Path) -> str:
    rel = path.relative_to(REPO_DATA)
    return rel.parts[0]


def source_url_for_file(path: Path) -> str:
    original = path.with_name(path.name.replace(".actual.csv", ".csv"))
    rel = original.relative_to(REPO_DATA).as_posix()
    return f"{MEDIA_BASE_URL}/{rel}"


def motor_array(df: pd.DataFrame) -> np.ndarray:
    return df[["m1_rads", "m2_rads", "m3_rads", "m4_rads"]].to_numpy(dtype=float)


def speed_array(df: pd.DataFrame) -> np.ndarray:
    return np.linalg.norm(df[["vx", "vy", "vz"]].to_numpy(dtype=float), axis=1)


def omega_norm_array(df: pd.DataFrame) -> np.ndarray:
    return np.linalg.norm(df[["wx", "wy", "wz"]].to_numpy(dtype=float), axis=1)


def smooth(values: np.ndarray, window: int = 11) -> np.ndarray:
    if window <= 1:
        return np.asarray(values, dtype=float)
    return (
        pd.Series(np.asarray(values, dtype=float))
        .rolling(window, center=True, min_periods=1)
        .mean()
        .to_numpy(dtype=float)
    )


def derivative(values: np.ndarray, t: np.ndarray, window: int = 11) -> np.ndarray:
    values_smooth = smooth(values, window=window)
    return np.gradient(values_smooth, t)


def r2_score(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    residual = y_true - y_pred
    ss_res = float(np.sum(np.square(residual)))
    ss_tot = float(np.sum(np.square(y_true - float(np.mean(y_true)))))
    if ss_tot <= 0.0:
        return math.nan
    return 1.0 - ss_res / ss_tot


def rmse(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(y_true - y_pred))))


def fit_no_intercept(x: np.ndarray, y: np.ndarray) -> dict[str, float]:
    x = np.asarray(x, dtype=float)
    y = np.asarray(y, dtype=float)
    mask = np.isfinite(x) & np.isfinite(y)
    x = x[mask]
    y = y[mask]
    denom = float(np.dot(x, x))
    if x.size < 10 or denom <= 0.0:
        return {"k": math.nan, "rmse": math.nan, "r2": math.nan, "samples": float(x.size)}
    k = float(np.dot(x, y) / denom)
    pred = k * x
    return {"k": k, "rmse": rmse(y, pred), "r2": r2_score(y, pred), "samples": float(x.size)}


def fit_with_intercept(x: np.ndarray, y: np.ndarray) -> dict[str, float]:
    x = np.asarray(x, dtype=float)
    y = np.asarray(y, dtype=float)
    mask = np.isfinite(x) & np.isfinite(y)
    x = x[mask]
    y = y[mask]
    if x.size < 10:
        return {"k": math.nan, "b": math.nan, "rmse": math.nan, "r2": math.nan, "samples": float(x.size)}
    design = np.vstack([x, np.ones_like(x)]).T
    k, b = np.linalg.lstsq(design, y, rcond=None)[0]
    pred = design @ np.asarray([k, b], dtype=float)
    return {"k": float(k), "b": float(b), "rmse": rmse(y, pred), "r2": r2_score(y, pred), "samples": float(x.size)}


def eval_fixed_coefficient(x: np.ndarray, y: np.ndarray, k: float, b: float = 0.0) -> dict[str, float]:
    x = np.asarray(x, dtype=float)
    y = np.asarray(y, dtype=float)
    mask = np.isfinite(x) & np.isfinite(y)
    x = x[mask]
    y = y[mask]
    if x.size == 0:
        return {"rmse": math.nan, "r2": math.nan, "bias": math.nan, "samples": 0.0}
    pred = k * x + b
    return {
        "rmse": rmse(y, pred),
        "r2": r2_score(y, pred),
        "bias": float(np.mean(pred - y)),
        "samples": float(x.size),
    }


def add_distribution(
    rows: list[dict[str, object]],
    *,
    name: str,
    metric_prefix: str,
    values: Iterable[float],
    unit: str,
    source_file: Path | str,
    source_url: str,
    evidence_role: str,
    note: str,
) -> None:
    for p in [0, 10, 50, 90, 95, 99, 100]:
        add_metric(
            rows,
            row_type="nanodrone_sysid_packet_distribution_summary",
            name=name,
            metric=f"{metric_prefix}_p{p}",
            value=percentile(values, p),
            unit=unit,
            source_file=source_file,
            source_url=source_url,
            evidence_role=evidence_role,
            note=note,
        )


def load_frames(files: list[Path]) -> list[dict[str, object]]:
    frames: list[dict[str, object]] = []
    for path in files:
        df = pd.read_csv(path)
        missing = [column for column in EXPECTED_COLUMNS if column not in df.columns]
        if missing:
            raise ValueError(f"{path} is missing columns: {missing}")
        frames.append(
            {
                "path": path,
                "split": split_from_file(path),
                "trajectory": trajectory_from_file(path),
                "df": df,
            }
        )
    return frames


def add_source_inventory(rows: list[dict[str, object]], frames: list[dict[str, object]]) -> None:
    total_rows = sum(len(frame["df"]) for frame in frames)
    total_duration_s = sum((len(frame["df"]) - 1) * DT_S for frame in frames)
    train_rows = sum(len(frame["df"]) for frame in frames if frame["split"] == "train")
    test_rows = sum(len(frame["df"]) for frame in frames if frame["split"] == "test")
    train_files = sum(1 for frame in frames if frame["split"] == "train")
    test_files = sum(1 for frame in frames if frame["split"] == "test")
    trajectories = sorted({str(frame["trajectory"]) for frame in frames})

    for metric, value, unit, note in [
        ("source_platform", "Crazyflie 2.1 Brushless nano-quadrotor", "text", "README dataset summary."),
        ("claimed_sample_count", SOURCE_SAMPLE_CLAIM, "samples", "README reports approximately 75k samples."),
        ("actual_loaded_sample_count", total_rows, "samples", "Rows loaded from downloaded Git LFS CSV data."),
        ("actual_loaded_duration", total_duration_s, "s", "Sum of per-file (n-1)*0.01 s durations."),
        ("sample_rate", SAMPLE_RATE_HZ, "Hz", "README/model code use 100 Hz data."),
        ("dt", DT_S, "s", "models.BaseQuadModel default dt and CSV spacing."),
        ("benchmark_open_loop_horizon", BENCHMARK_HORIZON_STEPS, "steps", "README standardized multi-step benchmark."),
        ("benchmark_open_loop_horizon_s", BENCHMARK_HORIZON_S, "s", "50 steps at 100 Hz."),
        ("actual_csv_file_count", len(frames), "files", "Downloaded Git LFS actual CSV files."),
        ("train_csv_file_count", train_files, "files", "Square, Random, and Chirp trajectories are train split."),
        ("test_csv_file_count", test_files, "files", "Melon is trajectory-held-out test split."),
        ("train_sample_count", train_rows, "samples", "Loaded train rows."),
        ("test_sample_count", test_rows, "samples", "Loaded test rows."),
        ("trajectory_names", "|".join(trajectories), "text", "Loaded trajectory families."),
        ("paper_title", "Nonlinear System Identification for a Nano-drone Benchmark", "text", "README publication title."),
    ]:
        add_metric(
            rows,
            row_type="nanodrone_sysid_packet_source_inventory",
            name="idsia_nanodrone_sysid_benchmark",
            metric=metric,
            value=value,
            unit=unit,
            source_file=README,
            source_url=README_URL,
            evidence_role="source_inventory",
            note=note,
        )

    for name, url, note in [
        ("github_repository", REPO_URL, "Project repository."),
        ("github_codeload_zip", CODELOAD_URL, "Used as a repo download fallback when git clone/API access is unavailable."),
        ("github_lfs_media_base", MEDIA_BASE_URL, "Used to fetch actual CSV contents behind Git LFS pointers."),
        ("science_direct_article", SCIENCEDIRECT_URL, "Published article landing page."),
        ("arxiv_preprint", ARXIV_URL, "Preprint link listed by the repository."),
    ]:
        add_metric(
            rows,
            row_type="nanodrone_sysid_packet_source_inventory",
            name=name,
            metric="url",
            value=url,
            unit="url",
            source_file=README,
            source_url=README_URL,
            evidence_role="source_url",
            note=note,
        )

    model_constants = [
        ("mass", MASS_KG, "kg", "models.py phys_params m."),
        ("gravity", G_M_S2, "m/s^2", "models.py phys_params g."),
        ("arm_length", ARM_M, "m", "PhysQuadModel default arm_length."),
        ("source_Kt", SOURCE_KT_N_PER_RAD2, "N/(rad/s)^2", "PhysQuadModel default Kt."),
        ("source_Kc", SOURCE_KC_NM_PER_RAD2, "N*m/(rad/s)^2", "PhysQuadModel default Kc."),
        ("source_thrust_to_weight", SOURCE_THRUST_TO_WEIGHT, "x", "models.py phys_params thrust_to_weight."),
        ("source_Tmax", SOURCE_THRUST_TO_WEIGHT * MASS_KG * G_M_S2, "N", "T_max = thrust_to_weight*m*g."),
        ("source_Jxx", SOURCE_J_KG_M2[0], "kg*m^2", "models.py phys_params J."),
        ("source_Jyy", SOURCE_J_KG_M2[1], "kg*m^2", "models.py phys_params J."),
        ("source_Jzz", SOURCE_J_KG_M2[2], "kg*m^2", "models.py phys_params J."),
        ("source_max_torque_roll", SOURCE_MAX_TORQUE_NM[0], "N*m", "models.py phys_params max_torque."),
        ("source_max_torque_pitch", SOURCE_MAX_TORQUE_NM[1], "N*m", "models.py phys_params max_torque."),
        ("source_max_torque_yaw", SOURCE_MAX_TORQUE_NM[2], "N*m", "models.py phys_params max_torque."),
    ]
    for metric, value, unit, note in model_constants:
        add_metric(
            rows,
            row_type="nanodrone_sysid_packet_model_constant",
            name="reference_physquad_model",
            metric=metric,
            value=value,
            unit=unit,
            source_file=MODEL_SOURCE,
            source_url=f"{REPO_URL}/blob/main/models/models.py",
            evidence_role="source_model_constant",
            note=note,
        )

    for idx, column in enumerate(EXPECTED_COLUMNS):
        if column == "t":
            unit = "s"
            role = "time"
        elif column.startswith("m") and column.endswith("_rads"):
            unit = "rad/s"
            role = "motor_angular_velocity_input"
        elif column in {"wx", "wy", "wz"}:
            unit = "rad/s"
            role = "body_angular_velocity_output"
        elif column in {"vx", "vy", "vz"}:
            unit = "m/s"
            role = "world_linear_velocity_output"
        elif column in {"ax_body", "ay_body", "az_body"}:
            unit = "m/s^2"
            role = "body_acceleration_channel"
        elif column in {"qx", "qy", "qz", "qw"}:
            unit = "quaternion"
            role = "world_orientation_output"
        else:
            unit = "m"
            role = "world_position_output"
        add_metric(
            rows,
            row_type="nanodrone_sysid_packet_column_schema",
            name=column,
            metric="column_index",
            value=idx,
            unit=unit,
            source_file=DATASET_SOURCE,
            source_url=f"{REPO_URL}/blob/main/dataset/dataset.py",
            evidence_role=role,
            note="CSV column consumed by the repository dataset/model code.",
        )


def per_file_metrics(rows: list[dict[str, object]], frames: list[dict[str, object]]) -> None:
    for frame in frames:
        path = frame["path"]
        df = frame["df"]
        rel_name = path.relative_to(REPO_DATA).as_posix().replace(".actual.csv", "")
        t = df["t"].to_numpy(dtype=float)
        dt = np.diff(t)
        motors = motor_array(df)
        speed = speed_array(df)
        omega_norm = omega_norm_array(df)
        sum_omega2 = np.square(motors).sum(axis=1)
        thrust = SOURCE_KT_N_PER_RAD2 * sum_omega2
        thrust_to_weight = thrust / (MASS_KG * G_M_S2)
        motor_slew = np.abs(np.diff(motors, axis=0)) / DT_S

        metrics = [
            ("samples", len(df), "samples"),
            ("duration", t[-1] - t[0], "s"),
            ("dt_median", percentile(dt, 50), "s"),
            ("dt_p95", percentile(dt, 95), "s"),
            ("speed_p50", percentile(speed, 50), "m/s"),
            ("speed_p95", percentile(speed, 95), "m/s"),
            ("speed_max", percentile(speed, 100), "m/s"),
            ("omega_norm_p95", percentile(omega_norm, 95), "rad/s"),
            ("omega_norm_max", percentile(omega_norm, 100), "rad/s"),
            ("motor_rad_s_min", percentile(motors.ravel(), 0), "rad/s"),
            ("motor_rad_s_p50", percentile(motors.ravel(), 50), "rad/s"),
            ("motor_rad_s_p95", percentile(motors.ravel(), 95), "rad/s"),
            ("motor_rad_s_p99", percentile(motors.ravel(), 99), "rad/s"),
            ("motor_rad_s_max", percentile(motors.ravel(), 100), "rad/s"),
            ("motor_rpm_p95", percentile(motors.ravel(), 95) * 60.0 / (2.0 * math.pi), "rpm"),
            ("motor_rpm_max", percentile(motors.ravel(), 100) * 60.0 / (2.0 * math.pi), "rpm"),
            ("motor_slew_abs_p95", percentile(motor_slew.ravel(), 95), "rad/s^2"),
            ("motor_slew_abs_p99", percentile(motor_slew.ravel(), 99), "rad/s^2"),
            ("sum_omega2_mean", float(np.mean(sum_omega2)), "(rad/s)^2"),
            ("sum_omega2_max", float(np.max(sum_omega2)), "(rad/s)^2"),
            ("source_Kt_thrust_mean", float(np.mean(thrust)), "N"),
            ("source_Kt_thrust_p95", percentile(thrust, 95), "N"),
            ("source_Kt_thrust_max", float(np.max(thrust)), "N"),
            ("source_Kt_thrust_to_weight_mean", float(np.mean(thrust_to_weight)), "x"),
            ("source_Kt_thrust_to_weight_p95", percentile(thrust_to_weight, 95), "x"),
            ("az_body_p50", percentile(df["az_body"], 50), "m/s^2"),
            ("az_body_p95", percentile(df["az_body"], 95), "m/s^2"),
        ]
        for metric, value, unit in metrics:
            add_metric(
                rows,
                row_type="nanodrone_sysid_packet_file_summary",
                name=rel_name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=path,
                source_url=source_url_for_file(path),
                evidence_role=f"{frame['split']}_{frame['trajectory']}_file_summary",
                note="Per-file summary from downloaded Git LFS CSV.",
            )


def group_metrics(rows: list[dict[str, object]], frames: list[dict[str, object]]) -> None:
    groups: dict[str, list[dict[str, object]]] = {"all": frames}
    for split in sorted({str(frame["split"]) for frame in frames}):
        groups[f"split_{split}"] = [frame for frame in frames if frame["split"] == split]
    for trajectory in sorted({str(frame["trajectory"]) for frame in frames}):
        groups[f"trajectory_{trajectory}"] = [frame for frame in frames if frame["trajectory"] == trajectory]

    for group_name, group_frames in groups.items():
        speeds: list[float] = []
        omega_norms: list[float] = []
        motors: list[float] = []
        az_values: list[float] = []
        sum_omega2: list[float] = []
        ttw: list[float] = []
        slews: list[float] = []
        rows_count = 0
        duration_s = 0.0
        for frame in group_frames:
            df = frame["df"]
            rows_count += len(df)
            duration_s += (len(df) - 1) * DT_S
            m = motor_array(df)
            sum_w2 = np.square(m).sum(axis=1)
            speeds.extend(speed_array(df))
            omega_norms.extend(omega_norm_array(df))
            motors.extend(m.ravel())
            az_values.extend(df["az_body"].to_numpy(dtype=float))
            sum_omega2.extend(sum_w2)
            ttw.extend(SOURCE_KT_N_PER_RAD2 * sum_w2 / (MASS_KG * G_M_S2))
            slews.extend((np.abs(np.diff(m, axis=0)) / DT_S).ravel())

        add_metric(
            rows,
            row_type="nanodrone_sysid_packet_group_summary",
            name=group_name,
            metric="sample_count",
            value=rows_count,
            unit="samples",
            source_file=OUTPUT,
            source_url=REPO_URL,
            evidence_role="group_summary",
            note="Aggregated from loaded actual CSV files.",
        )
        add_metric(
            rows,
            row_type="nanodrone_sysid_packet_group_summary",
            name=group_name,
            metric="duration",
            value=duration_s,
            unit="s",
            source_file=OUTPUT,
            source_url=REPO_URL,
            evidence_role="group_summary",
            note="Aggregated as sum of per-file (n-1)*0.01 s durations.",
        )
        for prefix, values, unit, note in [
            ("speed", speeds, "m/s", "World-frame speed magnitude."),
            ("omega_norm", omega_norms, "rad/s", "Body angular-rate magnitude."),
            ("motor_rad_s", motors, "rad/s", "Motor angular velocity inputs."),
            ("az_body", az_values, "m/s^2", "Body z acceleration channel."),
            ("sum_omega2", sum_omega2, "(rad/s)^2", "Sum of squared motor angular velocities."),
            ("source_Kt_thrust_to_weight", ttw, "x", "T/(m*g) using repository PhysQuadModel Kt."),
            ("motor_slew_abs", slews, "rad/s^2", "Absolute one-step motor angular-velocity slew."),
        ]:
            add_distribution(
                rows,
                name=group_name,
                metric_prefix=prefix,
                values=values,
                unit=unit,
                source_file=OUTPUT,
                source_url=REPO_URL,
                evidence_role="group_distribution",
                note=note,
            )


def build_fit_arrays(frames: list[dict[str, object]]) -> dict[str, dict[str, np.ndarray]]:
    out: dict[str, dict[str, list[np.ndarray]]] = {}
    for key in ["all", "train", "test"]:
        out[key] = {
            "sumw2": [],
            "force_body_z": [],
            "roll_feature": [],
            "pitch_feature": [],
            "yaw_feature": [],
            "tau_x": [],
            "tau_y": [],
            "tau_z": [],
        }

    for frame in frames:
        key = str(frame["split"])
        df = frame["df"]
        t = df["t"].to_numpy(dtype=float)
        motors = motor_array(df)
        omega2 = np.square(motors)
        sumw2 = omega2.sum(axis=1)
        force_body_z = MASS_KG * df["az_body"].to_numpy(dtype=float)

        roll_feature = ARM_M * ((omega2[:, 2] + omega2[:, 3]) - (omega2[:, 0] + omega2[:, 1]))
        pitch_feature = ARM_M * ((omega2[:, 1] + omega2[:, 2]) - (omega2[:, 0] + omega2[:, 3]))
        yaw_feature = (omega2[:, 0] + omega2[:, 2]) - (omega2[:, 1] + omega2[:, 3])

        omega = df[["wx", "wy", "wz"]].to_numpy(dtype=float)
        omega_smooth = np.column_stack([smooth(omega[:, axis], window=11) for axis in range(3)])
        omega_dot = np.column_stack([derivative(omega[:, axis], t, window=11) for axis in range(3)])
        j_omega = omega_smooth * SOURCE_J_KG_M2
        gyro_cross = np.cross(omega_smooth, j_omega)
        tau = omega_dot * SOURCE_J_KG_M2 + gyro_cross

        for dest in ["all", key]:
            out[dest]["sumw2"].append(sumw2)
            out[dest]["force_body_z"].append(force_body_z)
            out[dest]["roll_feature"].append(roll_feature)
            out[dest]["pitch_feature"].append(pitch_feature)
            out[dest]["yaw_feature"].append(yaw_feature)
            out[dest]["tau_x"].append(tau[:, 0])
            out[dest]["tau_y"].append(tau[:, 1])
            out[dest]["tau_z"].append(tau[:, 2])

    return {
        group: {name: np.concatenate(parts) if parts else np.asarray([], dtype=float) for name, parts in values.items()}
        for group, values in out.items()
    }


def add_fixed_eval_metrics(
    rows: list[dict[str, object]],
    *,
    name: str,
    source_file: Path | str,
    source_url: str,
    coefficient_metric: str,
    coefficient_value: float,
    coefficient_unit: str,
    target_unit: str,
    evaluation: dict[str, float],
    note: str,
) -> None:
    for metric, value, unit in [
        (coefficient_metric, coefficient_value, coefficient_unit),
        ("rmse", evaluation["rmse"], target_unit),
        ("r2", evaluation["r2"], "R2"),
        ("mean_prediction_minus_measurement", evaluation["bias"], target_unit),
        ("samples", evaluation["samples"], "samples"),
    ]:
        add_metric(
            rows,
            row_type="nanodrone_sysid_packet_fixed_model_eval",
            name=name,
            metric=metric,
            value=value,
            unit=unit,
            source_file=source_file,
            source_url=source_url,
            evidence_role="source_constant_evaluation",
            note=note,
        )


def add_fit_rows(rows: list[dict[str, object]], frames: list[dict[str, object]]) -> None:
    arrays = build_fit_arrays(frames)

    fit_specs = [
        ("thrust_body_z_specific_force", "sumw2", "force_body_z", SOURCE_KT_N_PER_RAD2, "N/(rad/s)^2", "N", "Source Kt predicts body-z force as Kt*sum(omega^2); measured proxy is mass*az_body."),
        ("roll_torque", "roll_feature", "tau_x", SOURCE_KT_N_PER_RAD2, "N/(rad/s)^2", "N*m", "Roll feature follows repository motor_to_phys convention; measured proxy is J*omega_dot + omega x Jomega."),
        ("pitch_torque", "pitch_feature", "tau_y", SOURCE_KT_N_PER_RAD2, "N/(rad/s)^2", "N*m", "Pitch feature follows repository motor_to_phys convention; measured proxy is J*omega_dot + omega x Jomega."),
        ("yaw_torque", "yaw_feature", "tau_z", SOURCE_KC_NM_PER_RAD2, "N*m/(rad/s)^2", "N*m", "Yaw feature follows repository motor_to_phys convention; measured proxy is J*omega_dot + omega x Jomega."),
    ]

    for group in ["all", "train", "test"]:
        for name, x_key, y_key, source_k, coefficient_unit, target_unit, note in fit_specs:
            x = arrays[group][x_key]
            y = arrays[group][y_key]
            source_eval = eval_fixed_coefficient(x, y, source_k)
            add_fixed_eval_metrics(
                rows,
                name=f"{name}_{group}_source_constant",
                source_file=MODEL_SOURCE,
                source_url=f"{REPO_URL}/blob/main/models/models.py",
                coefficient_metric="source_coefficient",
                coefficient_value=source_k,
                coefficient_unit=coefficient_unit,
                target_unit=target_unit,
                evaluation=source_eval,
                note=note,
            )

            no_intercept = fit_no_intercept(x, y)
            with_intercept = fit_with_intercept(x, y)
            for fit_name, fit in [("no_intercept", no_intercept), ("with_intercept", with_intercept)]:
                for metric, value, metric_unit in [
                    ("fit_coefficient", fit["k"], coefficient_unit),
                    ("fit_coefficient_over_source", fit["k"] / source_k if source_k else math.nan, "x"),
                    ("rmse", fit["rmse"], target_unit),
                    ("r2", fit["r2"], "R2"),
                    ("samples", fit["samples"], "samples"),
                ]:
                    add_metric(
                        rows,
                        row_type="nanodrone_sysid_packet_coefficient_fit",
                        name=f"{name}_{group}_{fit_name}",
                        metric=metric,
                        value=value,
                        unit=metric_unit,
                        source_file=OUTPUT,
                        source_url=REPO_URL,
                        evidence_role="coefficient_fit",
                        note=note,
                    )
                if "b" in fit:
                    add_metric(
                        rows,
                        row_type="nanodrone_sysid_packet_coefficient_fit",
                        name=f"{name}_{group}_{fit_name}",
                        metric="fit_intercept",
                        value=fit["b"],
                        unit=target_unit,
                        source_file=OUTPUT,
                        source_url=REPO_URL,
                        evidence_role="coefficient_fit",
                        note=note,
                    )

    for name, x_key, y_key, source_k, coefficient_unit, target_unit, note in fit_specs:
        train_fit = fit_no_intercept(arrays["train"][x_key], arrays["train"][y_key])
        for eval_group in ["train", "test", "all"]:
            evaluation = eval_fixed_coefficient(arrays[eval_group][x_key], arrays[eval_group][y_key], train_fit["k"])
            for metric, value, metric_unit in [
                ("train_fit_coefficient", train_fit["k"], coefficient_unit),
                ("train_fit_coefficient_over_source", train_fit["k"] / source_k if source_k else math.nan, "x"),
                ("eval_rmse", evaluation["rmse"], target_unit),
                ("eval_r2", evaluation["r2"], "R2"),
                ("eval_bias", evaluation["bias"], target_unit),
                ("eval_samples", evaluation["samples"], "samples"),
            ]:
                add_metric(
                    rows,
                    row_type="nanodrone_sysid_packet_train_to_test_eval",
                    name=f"{name}_train_fit_on_{eval_group}",
                    metric=metric,
                    value=value,
                    unit=metric_unit,
                    source_file=OUTPUT,
                    source_url=REPO_URL,
                    evidence_role="trajectory_generalization_check",
                    note=note,
                )

    all_motors = np.concatenate([motor_array(frame["df"]).ravel() for frame in frames])
    all_sumw2 = arrays["all"]["sumw2"]
    all_ttw = SOURCE_KT_N_PER_RAD2 * all_sumw2 / (MASS_KG * G_M_S2)
    comparison_metrics = [
        ("source_Kt_over_racingQuad_rotor_K", SOURCE_KT_N_PER_RAD2 / RACINGQUAD_ROTOR_K_N_PER_RAD2, "x", "Nano-drone thrust coefficient is not transferable to 5-inch FPV scale."),
        ("motor_rad_s_p95_over_racingQuad_hover_rad_s", percentile(all_motors, 95) / RACINGQUAD_HOVER_RAD_S_PROXY, "x", "Motor angular-speed scale comparison only."),
        ("motor_rad_s_max_over_racingQuad_max_rad_s", percentile(all_motors, 100) / RACINGQUAD_MAX_RAD_S_PROXY, "x", "Motor angular-speed scale comparison only."),
        ("racingQuad_hover_rad_s_proxy", RACINGQUAD_HOVER_RAD_S_PROXY, "rad/s", "Derived from current documented 5-inch thrust coefficient proxy."),
        ("racingQuad_max_rad_s_proxy", RACINGQUAD_MAX_RAD_S_PROXY, "rad/s", "Derived from current documented 29138 rpm proxy."),
        ("nanodrone_motor_rad_s_p95", percentile(all_motors, 95), "rad/s", "Loaded dataset motor input distribution."),
        ("nanodrone_motor_rad_s_max", percentile(all_motors, 100), "rad/s", "Loaded dataset motor input distribution."),
        ("nanodrone_source_Kt_TW_p50", percentile(all_ttw, 50), "x", "T/(m*g) using repository Kt."),
        ("nanodrone_source_Kt_TW_p95", percentile(all_ttw, 95), "x", "T/(m*g) using repository Kt."),
        ("nanodrone_source_Kt_TW_max", percentile(all_ttw, 100), "x", "T/(m*g) using repository Kt."),
    ]
    for metric, value, unit, note in comparison_metrics:
        add_metric(
            rows,
            row_type="nanodrone_sysid_packet_current_model_comparison",
            name="current_racingQuad_scale_context",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=REPO_URL,
            evidence_role="current_model_context",
            note=note,
        )

    add_metric(
        rows,
        row_type="nanodrone_sysid_packet_method",
        name="scope_caveat",
        metric="recommended_use",
        value=(
            "Use this packet to validate motor-rad/s input semantics, omega^2 force/torque "
            "mapping, 100 Hz open-loop system-ID evaluation, and train/test trajectory "
            "generalization. Do not transplant the Crazyflie 2.1 Brushless coefficients "
            "directly into 5-inch FPV presets."
        ),
        unit="text",
        source_file=README,
        source_url=README_URL,
        evidence_role="handoff_guidance",
        note="Nano-scale vehicle, lower speeds, and different prop/motor scale than racingQuad.",
    )
    add_metric(
        rows,
        row_type="nanodrone_sysid_packet_method",
        name="source_code_caveat",
        metric="reference_phys_model_rotational_dynamics",
        value="models.py currently multiplies omega_dot by 0.0 in _step_from_phys; coefficient rows here audit motor_to_phys semantics, not that implementation quirk.",
        unit="text",
        source_file=MODEL_SOURCE,
        source_url=f"{REPO_URL}/blob/main/models/models.py",
        evidence_role="source_code_caveat",
        note="This packet fits torque proxies from CSV body rates independently of the source forward-step implementation.",
    )


def sync_summary(packet_rows: list[dict[str, object]]) -> None:
    existing = read_rows(SUMMARY) if SUMMARY.exists() else []
    kept = [row for row in existing if not row.get("category", "").startswith("nanodrone_sysid_packet_")]
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
    files = ensure_actual_csvs()
    frames = load_frames(files)

    rows: list[dict[str, object]] = []
    add_source_inventory(rows, frames)
    per_file_metrics(rows, frames)
    group_metrics(rows, frames)
    add_fit_rows(rows, frames)

    write_csv(OUTPUT, rows)
    sync_summary(rows)
    print(f"Wrote {len(rows)} rows to {repo_path(OUTPUT)}")
    print("Synced nanodrone_sysid_packet_* rows into fpv_model_validation_summary.csv")


if __name__ == "__main__":
    main()
