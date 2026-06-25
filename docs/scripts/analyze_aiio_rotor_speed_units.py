"""Validate AI-IO rotor_spd units and summarize RPM/frequency ranges.

Outputs:
  docs/data/aiio_rotor_speed_unit_reference.csv
  docs/data/aiio_low_dynamic_rotor_rpm_reference.csv

This script uses the AI-IO repository preprocessing code as unit provenance:
`rotor_spd` is copied from MAVROS ESCStatus `esc_status[i].rpm` into the
HDF5 `rotor_spd` dataset. It then summarizes the already extracted AI-IO
test HDF5 files and compares the RPM scale with current project presets.
"""

from __future__ import annotations

import csv
import math
import statistics
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
AIIO_RAW = DATA / "raw" / "aiio"
AIIO_REPO = AIIO_RAW / "repo"
AIIO_SAMPLE_CSV = DATA / "aiio_flight_log_sample_reference.csv"
APDRONE_MODEL_CSV = DATA / "apdrone_flight_vs_model_reference.csv"
OUTPUT_CSV = DATA / "aiio_rotor_speed_unit_reference.csv"
LOW_DYNAMIC_OUTPUT_CSV = DATA / "aiio_low_dynamic_rotor_rpm_reference.csv"

AIIO_REPO_URL = "https://github.com/SJTU-ViSYS-team/AI-IO"
AIIO_PREP_SOURCE = (
    "https://raw.githubusercontent.com/SJTU-ViSYS-team/AI-IO/main/"
    "src/learning/data_management/prepare_datasets/our2.py"
)
AIIO_MODEL_SOURCE = (
    "https://raw.githubusercontent.com/SJTU-ViSYS-team/AI-IO/main/"
    "src/learning/network/model.py"
)
AIIO_RELEASE_DATASET_URL = (
    "https://github.com/SJTU-ViSYS-team/AI-IO/releases/download/v1.0/"
    "AI-IO_dataset.tar.gz"
)
AIIO_ARXIV_HTML_URL = "https://arxiv.org/html/2603.00597v1"

THREE_BLADE_COUNT = 3.0
LOW_DYNAMIC_CRITERIA = [
    {
        "name": "strict_low_motion",
        "max_speed_m_s": 1.0,
        "max_ground_accel_m_s2": 1.5,
        "max_gyro_norm_rad_s": 0.5,
        "note": "Slow, low-acceleration, low-rotation samples; closest automatic proxy for hover-like steady flight in the extracted AI-IO test slices.",
    },
    {
        "name": "relaxed_low_motion",
        "max_speed_m_s": 2.0,
        "max_ground_accel_m_s2": 3.0,
        "max_gyro_norm_rad_s": 1.0,
        "note": "Broader slow-flight proxy that keeps more samples while still excluding the most aggressive translational and rotational motion.",
    },
]


def repo_path(path: Path) -> str:
    try:
        return path.resolve().relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


def finite_or_blank(value: object) -> object:
    if isinstance(value, float) and not math.isfinite(value):
        return ""
    return value


def csv_float(value: object) -> float:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return float("nan")
    return number if math.isfinite(number) else float("nan")


def percentile(values: list[float], pct: float) -> float:
    clean = sorted(value for value in values if math.isfinite(value))
    if not clean:
        return float("nan")
    index = (len(clean) - 1) * pct / 100.0
    lo = math.floor(index)
    hi = math.ceil(index)
    if lo == hi:
        return clean[lo]
    return clean[lo] * (hi - index) + clean[hi] * (index - lo)


def read_csv(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def hdf5_modules() -> tuple[object | None, object | None, str]:
    try:
        import h5py  # type: ignore[import-not-found]
        import numpy as np  # type: ignore[import-not-found]

        return h5py, np, ""
    except Exception as first_error:
        pydeps = AIIO_RAW / "pydeps"
        if pydeps.exists():
            pydeps_text = str(pydeps.resolve())
            if pydeps_text not in sys.path:
                sys.path.insert(0, pydeps_text)
            try:
                import h5py  # type: ignore[import-not-found,no-redef]
                import numpy as np  # type: ignore[import-not-found,no-redef]

                return h5py, np, ""
            except Exception as second_error:
                return None, None, f"Could not import h5py/numpy from {repo_path(pydeps)}: {second_error}"
        return None, None, f"Could not import h5py/numpy: {first_error}"


def project_preset_rpm_rows() -> dict[str, dict[str, float]]:
    rows: dict[str, dict[str, float]] = {}
    for row in read_csv(APDRONE_MODEL_CSV):
        if row.get("row_type") != "project_preset_model":
            continue
        preset = row.get("preset", "")
        thrust_coeff = csv_float(row.get("thrust_coefficient_n_per_rad2_s2"))
        max_thrust = csv_float(row.get("max_rotor_thrust_n"))
        hover_thrust = csv_float(row.get("hover_thrust_per_motor_n"))
        blade_count = csv_float(row.get("rotor_blade_count"))
        if thrust_coeff <= 0.0:
            continue

        def rpm_for_thrust(thrust_n: float) -> float:
            if thrust_n <= 0.0:
                return float("nan")
            omega = math.sqrt(thrust_n / thrust_coeff)
            return omega * 60.0 / (2.0 * math.pi)

        max_rpm = rpm_for_thrust(max_thrust)
        hover_rpm = rpm_for_thrust(hover_thrust)
        rows[preset] = {
            "hover_rpm": hover_rpm,
            "max_rpm": max_rpm,
            "blade_count": blade_count,
            "hover_blade_pass_hz": hover_rpm * blade_count / 60.0 if blade_count > 0.0 else float("nan"),
            "max_blade_pass_hz": max_rpm * blade_count / 60.0 if blade_count > 0.0 else float("nan"),
        }
    return rows


def add_preset_comparisons(row: dict[str, object], rotor_rpm: float, presets: dict[str, dict[str, float]]) -> None:
    for preset_name, preset in presets.items():
        max_rpm = preset.get("max_rpm", float("nan"))
        hover_rpm = preset.get("hover_rpm", float("nan"))
        blade_count = preset.get("blade_count", float("nan"))
        row[f"{preset_name}_hover_rpm"] = hover_rpm
        row[f"{preset_name}_max_rpm"] = max_rpm
        row[f"{preset_name}_rotor_rpm_over_hover_rpm"] = rotor_rpm / hover_rpm if hover_rpm > 0.0 else ""
        row[f"{preset_name}_rotor_rpm_over_max_rpm"] = rotor_rpm / max_rpm if max_rpm > 0.0 else ""
        row[f"{preset_name}_blade_count"] = blade_count
        row[f"{preset_name}_same_blade_count_bpf_hz"] = rotor_rpm * blade_count / 60.0 if blade_count > 0.0 else ""


def add_low_dynamic_preset_comparisons(
    row: dict[str, object],
    *,
    rpm_mean: float,
    rpm_p50: float,
    rpm_p95: float,
    presets: dict[str, dict[str, float]],
) -> None:
    for preset_name, preset in presets.items():
        hover_rpm = preset.get("hover_rpm", float("nan"))
        max_rpm = preset.get("max_rpm", float("nan"))
        blade_count = preset.get("blade_count", float("nan"))
        row[f"{preset_name}_hover_rpm"] = hover_rpm
        row[f"{preset_name}_max_rpm"] = max_rpm
        row[f"{preset_name}_low_dynamic_mean_over_hover_rpm"] = rpm_mean / hover_rpm if hover_rpm > 0.0 else ""
        row[f"{preset_name}_low_dynamic_p50_over_hover_rpm"] = rpm_p50 / hover_rpm if hover_rpm > 0.0 else ""
        row[f"{preset_name}_low_dynamic_p95_over_max_rpm"] = rpm_p95 / max_rpm if max_rpm > 0.0 else ""
        row[f"{preset_name}_blade_count"] = blade_count
        row[f"{preset_name}_low_dynamic_mean_bpf_hz"] = rpm_mean * blade_count / 60.0 if blade_count > 0.0 else ""


def summarize_hdf5_file(path: Path, h5py: object, np: object) -> dict[str, float | int | str]:
    with h5py.File(path, "r") as handle:  # type: ignore[attr-defined]
        rotor_spd = np.asarray(handle["rotor_spd"][()], dtype=float)  # type: ignore[attr-defined]
        ts = np.asarray(handle["ts"][()], dtype=float)  # type: ignore[attr-defined]

    finite = rotor_spd.reshape(-1)
    finite = finite[np.isfinite(finite)]  # type: ignore[index]
    nonzero = finite[finite > 0.0]  # type: ignore[index]
    basis = nonzero if nonzero.size else finite
    duration_s = float(ts[-1] - ts[0]) if ts.size > 1 else float("nan")
    median_dt = float(np.median(np.diff(ts))) if ts.size > 1 else float("nan")  # type: ignore[attr-defined]
    sample_rate = 1.0 / median_dt if median_dt > 0.0 else float("nan")
    return {
        "hdf5_shape": "x".join(str(part) for part in rotor_spd.shape),
        "rotor_spd_column_count": int(rotor_spd.shape[1]) if rotor_spd.ndim == 2 else "",
        "hdf5_sample_count": int(rotor_spd.shape[0]) if rotor_spd.ndim >= 1 else 0,
        "duration_s_from_hdf5": duration_s,
        "sample_rate_hz_from_hdf5": sample_rate,
        "zero_fraction": float(np.sum(finite == 0.0) / finite.size) if finite.size else float("nan"),  # type: ignore[attr-defined]
        "rpm_p50": float(np.percentile(basis, 50.0)) if basis.size else float("nan"),  # type: ignore[attr-defined]
        "rpm_p95": float(np.percentile(basis, 95.0)) if basis.size else float("nan"),  # type: ignore[attr-defined]
        "rpm_p99": float(np.percentile(basis, 99.0)) if basis.size else float("nan"),  # type: ignore[attr-defined]
        "rpm_max": float(np.max(basis)) if basis.size else float("nan"),  # type: ignore[attr-defined]
    }


def np_stats(values: object, np: object) -> dict[str, float | int]:
    finite = values[np.isfinite(values)]  # type: ignore[index,operator,attr-defined]
    if not getattr(finite, "size", 0):
        return {
            "count": 0,
            "mean": float("nan"),
            "p50": float("nan"),
            "p95": float("nan"),
            "max": float("nan"),
            "min": float("nan"),
        }
    return {
        "count": int(finite.size),
        "mean": float(np.mean(finite)),  # type: ignore[attr-defined]
        "p50": float(np.percentile(finite, 50.0)),  # type: ignore[attr-defined]
        "p95": float(np.percentile(finite, 95.0)),  # type: ignore[attr-defined]
        "max": float(np.max(finite)),  # type: ignore[attr-defined]
        "min": float(np.min(finite)),  # type: ignore[attr-defined]
    }


def summarize_low_dynamic_file(path: Path, h5py: object, np: object) -> dict[str, object]:
    with h5py.File(path, "r") as handle:  # type: ignore[attr-defined]
        ts = np.asarray(handle["ts"][()], dtype=float)  # type: ignore[attr-defined]
        traj = np.asarray(handle["traj_target"][()], dtype=float)  # type: ignore[attr-defined]
        gyro = np.asarray(handle["gyro_calib"][()], dtype=float)  # type: ignore[attr-defined]
        rotor_spd = np.asarray(handle["rotor_spd"][()], dtype=float)  # type: ignore[attr-defined]
        throttle = np.asarray(handle["throttle"][()], dtype=float).reshape(-1)  # type: ignore[attr-defined]

    if ts.size > 1:
        positive_dt = np.diff(ts)[np.diff(ts) > 0.0]
        median_dt_s = float(np.median(positive_dt)) if positive_dt.size else float("nan")  # type: ignore[attr-defined]
    else:
        median_dt_s = float("nan")
    velocity = traj[:, 7:10] if traj.ndim == 2 and traj.shape[1] >= 10 else np.zeros((ts.size, 3))
    speed = np.linalg.norm(velocity, axis=1)  # type: ignore[attr-defined]
    if ts.size > 2 and velocity.shape[0] == ts.size:
        ground_accel = np.linalg.norm(np.gradient(velocity, ts, axis=0), axis=1)  # type: ignore[attr-defined]
    else:
        ground_accel = np.full(ts.shape, float("nan"))  # type: ignore[attr-defined]
    gyro_norm = np.linalg.norm(gyro, axis=1) if gyro.ndim == 2 and gyro.shape[0] == ts.size else np.full(ts.shape, float("nan"))  # type: ignore[attr-defined]
    rotor_valid = np.where(np.isfinite(rotor_spd) & (rotor_spd > 0.0), rotor_spd, np.nan)  # type: ignore[attr-defined]
    valid_rotor_count = np.sum(np.isfinite(rotor_valid), axis=1)  # type: ignore[attr-defined]
    rotor_sum = np.nansum(rotor_valid, axis=1)  # type: ignore[attr-defined]
    rotor_mean = np.divide(
        rotor_sum,
        valid_rotor_count,
        out=np.full(valid_rotor_count.shape, np.nan, dtype=float),  # type: ignore[attr-defined]
        where=valid_rotor_count > 0,
    )

    return {
        "ts": ts,
        "median_dt_s": median_dt_s,
        "speed": speed,
        "ground_accel": ground_accel,
        "gyro_norm": gyro_norm,
        "rotor_mean": rotor_mean,
        "rotor_valid": rotor_valid,
        "throttle": throttle,
    }


def append_selected(
    bucket: dict[str, list[float]],
    key: str,
    values: object,
    np: object,
) -> None:
    finite = values[np.isfinite(values)]  # type: ignore[index,operator,attr-defined]
    bucket[key].extend(float(value) for value in finite.tolist())


def summarize_low_dynamic_rotor_rpm(
    sample_rows: list[dict[str, str]],
    presets: dict[str, dict[str, float]],
    h5py: object | None,
    np: object | None,
    hdf5_import_note: str,
) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = [
        {
            "row_type": "aiio_low_dynamic_rotor_rpm_method",
            "name": "AI-IO low-dynamic rotor RPM selection",
            "source": AIIO_RELEASE_DATASET_URL,
            "repo_source": AIIO_REPO_URL,
            "paper_source": AIIO_ARXIV_HTML_URL,
            "unit_source": AIIO_PREP_SOURCE,
            "rotor_spd_unit": "mechanical_rpm",
            "note": "Low-dynamic rows select samples by ground-truth speed, ground-truth velocity derivative magnitude, and calibrated gyro norm. These are slow-flight proxies, not controlled hover thrust-stand measurements.",
        }
    ]
    for criterion in LOW_DYNAMIC_CRITERIA:
        rows.append(
            {
                "row_type": "aiio_low_dynamic_selection_criterion",
                "criterion": criterion["name"],
                "max_speed_m_s": criterion["max_speed_m_s"],
                "max_ground_accel_m_s2": criterion["max_ground_accel_m_s2"],
                "max_gyro_norm_rad_s": criterion["max_gyro_norm_rad_s"],
                "note": criterion["note"],
            }
        )

    if h5py is None or np is None:
        rows.append(
            {
                "row_type": "aiio_low_dynamic_unavailable",
                "name": "hdf5_import_unavailable",
                "source": AIIO_RELEASE_DATASET_URL,
                "note": hdf5_import_note,
            }
        )
        return rows

    aggregate: dict[str, dict[str, list[float]]] = {
        str(criterion["name"]): {
            "speed": [],
            "ground_accel": [],
            "gyro_norm": [],
            "rotor_mean": [],
            "throttle": [],
        }
        for criterion in LOW_DYNAMIC_CRITERIA
    }
    aggregate_duration = {str(criterion["name"]): 0.0 for criterion in LOW_DYNAMIC_CRITERIA}
    aggregate_file_count = {str(criterion["name"]): 0 for criterion in LOW_DYNAMIC_CRITERIA}

    for source_row in sample_rows:
        hdf5_path = ROOT / str(source_row.get("hdf5_path", ""))
        if not hdf5_path.exists():
            continue
        summary = summarize_low_dynamic_file(hdf5_path, h5py, np)
        speed = summary["speed"]
        ground_accel = summary["ground_accel"]
        gyro_norm = summary["gyro_norm"]
        rotor_mean = summary["rotor_mean"]
        throttle = summary["throttle"]
        median_dt_s = csv_float(summary["median_dt_s"])
        total_samples = int(getattr(speed, "size", 0))

        for criterion in LOW_DYNAMIC_CRITERIA:
            name = str(criterion["name"])
            mask = (
                np.isfinite(speed)  # type: ignore[attr-defined]
                & np.isfinite(ground_accel)  # type: ignore[attr-defined]
                & np.isfinite(gyro_norm)  # type: ignore[attr-defined]
                & np.isfinite(rotor_mean)  # type: ignore[attr-defined]
                & (speed <= float(criterion["max_speed_m_s"]))  # type: ignore[operator]
                & (ground_accel <= float(criterion["max_ground_accel_m_s2"]))  # type: ignore[operator]
                & (gyro_norm <= float(criterion["max_gyro_norm_rad_s"]))  # type: ignore[operator]
            )
            count = int(np.sum(mask))  # type: ignore[attr-defined]
            if count <= 0:
                continue
            duration_s = count * median_dt_s if median_dt_s > 0.0 else float("nan")
            selected_speed = speed[mask]  # type: ignore[index]
            selected_accel = ground_accel[mask]  # type: ignore[index]
            selected_gyro = gyro_norm[mask]  # type: ignore[index]
            selected_rpm = rotor_mean[mask]  # type: ignore[index]
            selected_throttle = throttle[mask] if getattr(throttle, "shape", (0,))[0] == total_samples else np.asarray([], dtype=float)  # type: ignore[index,attr-defined]
            rpm_stats = np_stats(selected_rpm, np)
            speed_stats = np_stats(selected_speed, np)
            accel_stats = np_stats(selected_accel, np)
            gyro_stats = np_stats(selected_gyro, np)
            throttle_stats = np_stats(selected_throttle, np)

            per_motor_means: list[float] = []
            rotor_valid = summary["rotor_valid"]
            if getattr(rotor_valid, "ndim", 0) == 2:
                for motor_index in range(min(4, rotor_valid.shape[1])):  # type: ignore[attr-defined]
                    motor_values = rotor_valid[mask, motor_index]  # type: ignore[index]
                    motor_stats = np_stats(motor_values, np)
                    per_motor_means.append(float(motor_stats["mean"]))

            rpm_mean = float(rpm_stats["mean"])
            rpm_p50 = float(rpm_stats["p50"])
            rpm_p95 = float(rpm_stats["p95"])
            row: dict[str, object] = {
                "row_type": "aiio_low_dynamic_sample",
                "name": source_row.get("name", ""),
                "criterion": name,
                "source": AIIO_RELEASE_DATASET_URL,
                "repo_source": AIIO_REPO_URL,
                "paper_source": AIIO_ARXIV_HTML_URL,
                "hdf5_path": source_row.get("hdf5_path", ""),
                "mode": source_row.get("mode", ""),
                "trajectory": source_row.get("trajectory", ""),
                "sequence": source_row.get("sequence", ""),
                "reference_speed_label": source_row.get("reference_speed_label", ""),
                "selected_sample_count": count,
                "total_sample_count": total_samples,
                "selected_fraction": count / total_samples if total_samples > 0 else "",
                "selected_duration_s": duration_s,
                "speed_mean_m_s": speed_stats["mean"],
                "speed_p95_m_s": speed_stats["p95"],
                "ground_accel_mean_m_s2": accel_stats["mean"],
                "ground_accel_p95_m_s2": accel_stats["p95"],
                "gyro_norm_mean_rad_s": gyro_stats["mean"],
                "gyro_norm_p95_rad_s": gyro_stats["p95"],
                "throttle_mean": throttle_stats["mean"],
                "throttle_p95": throttle_stats["p95"],
                "rotor_rpm_mean": rpm_mean,
                "rotor_rpm_p50": rpm_p50,
                "rotor_rpm_p95": rpm_p95,
                "rotor_rpm_max": rpm_stats["max"],
                "rotor_rpm_min": rpm_stats["min"],
                "three_blade_bpf_hz_at_mean": rpm_mean * THREE_BLADE_COUNT / 60.0,
                "motor_mean_rpm_imbalance_fraction": (max(per_motor_means) - min(per_motor_means)) / rpm_mean if per_motor_means and rpm_mean > 0.0 else "",
                "note": "Selected from AI-IO test HDF5 by low speed, low ground-acceleration, and low gyro norm.",
            }
            for motor_index, motor_mean in enumerate(per_motor_means):
                row[f"motor{motor_index}_rpm_mean"] = motor_mean
            add_low_dynamic_preset_comparisons(
                row,
                rpm_mean=rpm_mean,
                rpm_p50=rpm_p50,
                rpm_p95=rpm_p95,
                presets=presets,
            )
            rows.append(row)

            append_selected(aggregate[name], "speed", selected_speed, np)
            append_selected(aggregate[name], "ground_accel", selected_accel, np)
            append_selected(aggregate[name], "gyro_norm", selected_gyro, np)
            append_selected(aggregate[name], "rotor_mean", selected_rpm, np)
            append_selected(aggregate[name], "throttle", selected_throttle, np)
            if math.isfinite(duration_s):
                aggregate_duration[name] += duration_s
            aggregate_file_count[name] += 1

    for criterion in LOW_DYNAMIC_CRITERIA:
        name = str(criterion["name"])
        bucket = aggregate[name]
        selected_rpm = np.asarray(bucket["rotor_mean"], dtype=float)  # type: ignore[attr-defined]
        rpm_stats = np_stats(selected_rpm, np)
        if int(rpm_stats["count"]) <= 0:
            continue
        speed_stats = np_stats(np.asarray(bucket["speed"], dtype=float), np)  # type: ignore[attr-defined]
        accel_stats = np_stats(np.asarray(bucket["ground_accel"], dtype=float), np)  # type: ignore[attr-defined]
        gyro_stats = np_stats(np.asarray(bucket["gyro_norm"], dtype=float), np)  # type: ignore[attr-defined]
        throttle_stats = np_stats(np.asarray(bucket["throttle"], dtype=float), np)  # type: ignore[attr-defined]
        rpm_mean = float(rpm_stats["mean"])
        rpm_p50 = float(rpm_stats["p50"])
        rpm_p95 = float(rpm_stats["p95"])
        row = {
            "row_type": "aiio_low_dynamic_dataset_summary",
            "name": f"AI-IO {name}",
            "criterion": name,
            "source": AIIO_RELEASE_DATASET_URL,
            "repo_source": AIIO_REPO_URL,
            "paper_source": AIIO_ARXIV_HTML_URL,
            "sample_file_count_with_selected_rows": aggregate_file_count[name],
            "selected_sample_count": rpm_stats["count"],
            "selected_duration_s": aggregate_duration[name],
            "speed_mean_m_s": speed_stats["mean"],
            "speed_p95_m_s": speed_stats["p95"],
            "ground_accel_mean_m_s2": accel_stats["mean"],
            "ground_accel_p95_m_s2": accel_stats["p95"],
            "gyro_norm_mean_rad_s": gyro_stats["mean"],
            "gyro_norm_p95_rad_s": gyro_stats["p95"],
            "throttle_mean": throttle_stats["mean"],
            "throttle_p95": throttle_stats["p95"],
            "rotor_rpm_mean": rpm_mean,
            "rotor_rpm_p50": rpm_p50,
            "rotor_rpm_p95": rpm_p95,
            "rotor_rpm_max": rpm_stats["max"],
            "rotor_rpm_min": rpm_stats["min"],
            "three_blade_bpf_hz_at_mean": rpm_mean * THREE_BLADE_COUNT / 60.0,
            "note": "Dataset summary concatenates selected sample rows across extracted AI-IO test HDF5 files.",
        }
        add_low_dynamic_preset_comparisons(
            row,
            rpm_mean=rpm_mean,
            rpm_p50=rpm_p50,
            rpm_p95=rpm_p95,
            presets=presets,
        )
        rows.append(row)

    return rows


def write_csv(path: Path, rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames: list[str] = []
    for row in rows:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows({key: finite_or_blank(value) for key, value in row.items()} for row in rows)


def main() -> None:
    rows: list[dict[str, object]] = []
    sample_rows = [
        row
        for row in read_csv(AIIO_SAMPLE_CSV)
        if row.get("row_type") == "reference_aiio_hdf5_sample"
    ]
    presets = project_preset_rpm_rows()
    h5py, np, hdf5_import_note = hdf5_modules()
    low_dynamic_rows = summarize_low_dynamic_rotor_rpm(
        sample_rows,
        presets,
        h5py,
        np,
        hdf5_import_note,
    )

    rows.extend(
        [
            {
                "row_type": "aiio_rotor_speed_unit_source",
                "name": "AI-IO preprocessing source",
                "source": AIIO_PREP_SOURCE,
                "repo_source": AIIO_REPO_URL,
                "paper_source": AIIO_ARXIV_HTML_URL,
                "local_source_file": repo_path(AIIO_REPO / "src/learning/data_management/prepare_datasets/our2.py"),
                "source_lines": "41, 86, 93-94, 139, 199",
                "unit_interpretation": "mechanical_rpm",
                "evidence": "The preprocessing script reads /mavros/esc, copies ros_msg.esc_status[i].rpm for motors 0-3, interpolates ESC samples onto IMU time, then writes HDF5 dataset rotor_spd from those columns.",
            },
            {
                "row_type": "aiio_rotor_speed_unit_source",
                "name": "AI-IO model feature source",
                "source": AIIO_MODEL_SOURCE,
                "repo_source": AIIO_REPO_URL,
                "paper_source": AIIO_ARXIV_HTML_URL,
                "local_source_file": repo_path(AIIO_REPO / "src/learning/network/model.py"),
                "source_lines": "153, 166-170, 249, 296-300",
                "unit_interpretation": "mechanical_rpm_squared_feature",
                "evidence": "The learning model takes rotor_spd as four channels, squares it, then applies empirical normalization. There is no RPM-to-rad/s conversion in the visible data path.",
            },
            {
                "row_type": "aiio_rotor_speed_dataset_context",
                "name": "AI-IO v1.0 extracted test HDF5 set",
                "source": AIIO_RELEASE_DATASET_URL,
                "repo_source": AIIO_REPO_URL,
                "paper_source": AIIO_ARXIV_HTML_URL,
                "local_source_file": repo_path(AIIO_SAMPLE_CSV),
                "sample_file_count": len(sample_rows),
                "hdf5_import_note": hdf5_import_note,
                "note": "All rows below interpret rotor_spd as ESC telemetry mechanical RPM based on the AI-IO preprocessing code.",
            },
        ]
    )

    aggregate_rpm: list[float] = []
    total_samples = 0
    total_duration = 0.0
    for source_row in sample_rows:
        sample_name = str(source_row.get("name", ""))
        hdf5_path = ROOT / str(source_row.get("hdf5_path", ""))
        hdf5_summary: dict[str, float | int | str] = {}
        if h5py is not None and np is not None and hdf5_path.exists():
            hdf5_summary = summarize_hdf5_file(hdf5_path, h5py, np)
            total_samples += int(hdf5_summary.get("hdf5_sample_count", 0) or 0)
            duration = csv_float(hdf5_summary.get("duration_s_from_hdf5"))
            if math.isfinite(duration):
                total_duration += duration
            rpm_max_for_aggregate = csv_float(hdf5_summary.get("rpm_max"))
            rpm_p95_for_aggregate = csv_float(hdf5_summary.get("rpm_p95"))
        else:
            rpm_max_for_aggregate = csv_float(source_row.get("rotor_spd_max_reported_units"))
            rpm_p95_for_aggregate = csv_float(source_row.get("rotor_spd_p95_reported_units"))
        if math.isfinite(rpm_max_for_aggregate):
            aggregate_rpm.append(rpm_max_for_aggregate)
        if math.isfinite(rpm_p95_for_aggregate):
            aggregate_rpm.append(rpm_p95_for_aggregate)

        rpm_p95 = csv_float(hdf5_summary.get("rpm_p95", source_row.get("rotor_spd_p95_reported_units")))
        rpm_max = csv_float(hdf5_summary.get("rpm_max", source_row.get("rotor_spd_max_reported_units")))
        sample_rate = csv_float(hdf5_summary.get("sample_rate_hz_from_hdf5", source_row.get("sample_rate_hz")))
        max_three_blade_bpf = rpm_max * THREE_BLADE_COUNT / 60.0
        row: dict[str, object] = {
            "row_type": "aiio_rotor_speed_sample_summary",
            "name": sample_name,
            "source": source_row.get("source", AIIO_RELEASE_DATASET_URL),
            "repo_source": AIIO_REPO_URL,
            "paper_source": AIIO_ARXIV_HTML_URL,
            "hdf5_path": source_row.get("hdf5_path", ""),
            "mode": source_row.get("mode", ""),
            "trajectory": source_row.get("trajectory", ""),
            "sequence": source_row.get("sequence", ""),
            "reference_speed_label": source_row.get("reference_speed_label", ""),
            "speed_max_m_s": source_row.get("speed_max_m_s", ""),
            "sample_rate_hz": sample_rate,
            "telemetry_nyquist_hz": sample_rate / 2.0 if sample_rate > 0.0 else "",
            "rotor_spd_unit": "mechanical_rpm",
            "rotor_rpm_p50": hdf5_summary.get("rpm_p50", source_row.get("rotor_spd_p50_reported_units", "")),
            "rotor_rpm_p95": rpm_p95,
            "rotor_rpm_p99": hdf5_summary.get("rpm_p99", source_row.get("rotor_spd_p99_reported_units", "")),
            "rotor_rpm_max": rpm_max,
            "motor_fundamental_hz_at_p95": rpm_p95 / 60.0,
            "motor_fundamental_hz_at_max": rpm_max / 60.0,
            "three_blade_bpf_hz_at_p95": rpm_p95 * THREE_BLADE_COUNT / 60.0,
            "three_blade_bpf_hz_at_max": max_three_blade_bpf,
            "three_blade_bpf_max_over_telemetry_nyquist": max_three_blade_bpf / (sample_rate / 2.0) if sample_rate > 0.0 else "",
            "hdf5_shape": hdf5_summary.get("hdf5_shape", ""),
            "hdf5_sample_count": hdf5_summary.get("hdf5_sample_count", source_row.get("hdf5_sample_count", "")),
            "rotor_spd_column_count": hdf5_summary.get("rotor_spd_column_count", ""),
            "zero_fraction": hdf5_summary.get("zero_fraction", source_row.get("rotor_spd_zero_fraction", "")),
            "note": "RPM comes from MAVROS ESCStatus.rpm. The 100 Hz HDF5 sample rate is telemetry/feature cadence, not a vibration bandwidth capable of resolving kHz blade-pass content directly.",
        }
        add_preset_comparisons(row, rpm_max, presets)
        rows.append(row)

    by_reference: dict[str, list[dict[str, str]]] = {}
    for source_row in sample_rows:
        by_reference.setdefault(str(source_row.get("reference_speed_label", "")), []).append(source_row)
    for label, group in sorted(by_reference.items()):
        rpm_max_values = [csv_float(row.get("rotor_spd_max_reported_units")) for row in group]
        rpm_p95_values = [csv_float(row.get("rotor_spd_p95_reported_units")) for row in group]
        speed_max_values = [csv_float(row.get("speed_max_m_s")) for row in group]
        rpm_max = max((value for value in rpm_max_values if math.isfinite(value)), default=float("nan"))
        rpm_p95_mean = statistics.fmean(value for value in rpm_p95_values if math.isfinite(value))
        row = {
            "row_type": "aiio_rotor_speed_group_summary",
            "name": label or "unlabeled",
            "source": AIIO_RELEASE_DATASET_URL,
            "repo_source": AIIO_REPO_URL,
            "paper_source": AIIO_ARXIV_HTML_URL,
            "sample_file_count": len(group),
            "speed_max_m_s_max": max((value for value in speed_max_values if math.isfinite(value)), default=float("nan")),
            "rotor_spd_unit": "mechanical_rpm",
            "rotor_rpm_p95_mean_of_samples": rpm_p95_mean,
            "rotor_rpm_max_across_samples": rpm_max,
            "three_blade_bpf_hz_at_group_max": rpm_max * THREE_BLADE_COUNT / 60.0,
            "note": "Group summary uses the per-file rows from aiio_flight_log_sample_reference.csv.",
        }
        add_preset_comparisons(row, rpm_max, presets)
        rows.append(row)

    if aggregate_rpm:
        rpm_max = max(aggregate_rpm)
        row = {
            "row_type": "aiio_rotor_speed_dataset_summary",
            "name": "AI-IO extracted test HDF5 rotor speed envelope",
            "source": AIIO_RELEASE_DATASET_URL,
            "repo_source": AIIO_REPO_URL,
            "paper_source": AIIO_ARXIV_HTML_URL,
            "sample_file_count": len(sample_rows),
            "hdf5_total_sample_rows": total_samples,
            "hdf5_total_duration_s": total_duration,
            "rotor_spd_unit": "mechanical_rpm",
            "rotor_rpm_sample_p95_of_file_p95_or_max_values": percentile(aggregate_rpm, 95.0),
            "rotor_rpm_max_across_extracted_samples": rpm_max,
            "three_blade_bpf_hz_at_dataset_max": rpm_max * THREE_BLADE_COUNT / 60.0,
            "note": "Dataset-level row summarizes extracted test HDF5 files, not the entire unextracted ROS bag corpus.",
        }
        add_preset_comparisons(row, rpm_max, presets)
        rows.append(row)

    write_csv(OUTPUT_CSV, rows)
    write_csv(LOW_DYNAMIC_OUTPUT_CSV, low_dynamic_rows)
    print(f"Wrote {repo_path(OUTPUT_CSV)}")
    print(f"Wrote {repo_path(LOW_DYNAMIC_OUTPUT_CSV)}")


if __name__ == "__main__":
    main()
