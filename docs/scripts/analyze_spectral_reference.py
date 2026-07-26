#!/usr/bin/env python3
"""Analyze processed rotor autopower as validation-only spectral evidence.

This adapter intentionally cannot create PCM or a release AcousticProfile. It
accepts calibrated frequency-bin autopower in Pa^2, extracts order/directivity
statistics, and preserves the missing-time-history limitations in its report.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import tempfile
from pathlib import Path
from typing import Any

import numpy as np


REFERENCE_PRESSURE_PA = 20.0e-6
SCHEMA_VERSION = 1
EVIDENCE_CLASS = "processed_autopower_spectral_reference"
RELEASE_CONSTRAINTS = {
    "release_profile_eligible": False,
    "raw_pressure_time_history_available": False,
    "phase_available": False,
    "pcm_synthesis_allowed": False,
    "release_profile_fitting_allowed": False,
}
REQUIRED_DATASETS = (
    "Autopower",
    "RPM",
    "frequency_Hz",
    "radius_m",
    "theta_deg",
)
DEFAULT_BANDS_HZ = {
    "low": (20.0, 300.0),
    "mid": (300.0, 3_200.0),
    "high": (3_200.0, 20_000.0),
}


def hash_file(path: Path, algorithm: str) -> str:
    digest = hashlib.new(algorithm)
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def spl_db(power_pa2: float) -> float:
    if not math.isfinite(power_pa2) or power_pa2 <= 0.0:
        raise ValueError("pressure power must be finite and positive")
    return 10.0 * math.log10(power_pa2 / REFERENCE_PRESSURE_PA**2)


def normalize_level_to_distance(
    level_db: float,
    measured_distance_m: float,
    reference_distance_m: float,
) -> float:
    if measured_distance_m <= 0.0 or reference_distance_m <= 0.0:
        raise ValueError("distances must be positive")
    return level_db + 20.0 * math.log10(
        measured_distance_m / reference_distance_m
    )


def axis_cosine(theta_degrees: np.ndarray) -> np.ndarray:
    return np.abs(np.sin(np.radians(theta_degrees)))


def fit_even_directivity(
    theta_degrees: np.ndarray,
    relative_levels_db: np.ndarray,
) -> dict[str, Any]:
    mu = axis_cosine(theta_degrees)
    design = np.column_stack((mu**2, mu**4))
    coefficients, _, _, _ = np.linalg.lstsq(
        design,
        relative_levels_db,
        rcond=None,
    )
    predicted = design @ coefficients
    residual = predicted - relative_levels_db
    positive = theta_degrees > 0.0
    asymmetry = []
    for theta in theta_degrees[positive]:
        positive_index = int(np.flatnonzero(theta_degrees == theta)[0])
        negative_candidates = np.flatnonzero(theta_degrees == -theta)
        if negative_candidates.size == 1:
            negative_index = int(negative_candidates[0])
            asymmetry.append(
                float(
                    relative_levels_db[positive_index]
                    - relative_levels_db[negative_index]
                )
            )
    return {
        "c2_db": float(coefficients[0]),
        "c4_db": float(coefficients[1]),
        "axis_db": float(coefficients.sum()),
        "axis_is_extrapolated": bool(float(np.max(mu)) < 1.0 - 1.0e-9),
        "maximum_measured_axis_cosine": float(np.max(mu)),
        "fit_rmse_db": float(np.sqrt(np.mean(residual**2))),
        "maximum_hemisphere_asymmetry_db": (
            max((abs(value) for value in asymmetry), default=0.0)
        ),
        "predicted_relative_db": [float(value) for value in predicted],
    }


def validate_arrays(
    autopower_pa2: np.ndarray,
    rpm: np.ndarray,
    frequency_hz: np.ndarray,
    radius_m: np.ndarray,
    theta_deg: np.ndarray,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, float, np.ndarray]:
    power = np.asarray(autopower_pa2, dtype=np.float64)
    speeds = np.asarray(rpm, dtype=np.float64).reshape(-1)
    frequencies = np.asarray(frequency_hz, dtype=np.float64).reshape(-1)
    radii = np.asarray(radius_m, dtype=np.float64).reshape(-1)
    angles = np.asarray(theta_deg, dtype=np.float64).reshape(-1)

    expected_shape = (frequencies.size, angles.size, speeds.size)
    if power.shape != expected_shape:
        raise ValueError(
            f"Autopower shape {power.shape} does not match "
            f"(frequency, theta, RPM) {expected_shape}"
        )
    if frequencies.size < 3 or not np.all(np.isfinite(frequencies)):
        raise ValueError("frequency_Hz must contain at least three finite bins")
    differences = np.diff(frequencies)
    if not np.all(differences > 0.0):
        raise ValueError("frequency_Hz must be strictly increasing")
    bin_width = float(differences[0])
    if not np.allclose(differences, bin_width, rtol=0.0, atol=1.0e-9):
        raise ValueError("frequency_Hz must be uniformly spaced")
    if frequencies[0] < 0.0:
        raise ValueError("frequency_Hz cannot start below zero")
    if (
        speeds.size < 2
        or not np.all(np.isfinite(speeds))
        or not np.all(speeds > 0.0)
        or not np.all(np.diff(speeds) > 0.0)
    ):
        raise ValueError("RPM must contain at least two positive increasing values")
    if (
        angles.size < 3
        or not np.all(np.isfinite(angles))
        or np.unique(angles).size != angles.size
        or not np.any(np.isclose(angles, 0.0, rtol=0.0, atol=1.0e-9))
    ):
        raise ValueError("theta_deg must be unique, finite, and include zero")
    if radii.size != 1 or not math.isfinite(float(radii[0])) or radii[0] <= 0.0:
        raise ValueError("radius_m must contain exactly one positive value")
    if not np.all(np.isfinite(power)) or np.any(power < 0.0):
        raise ValueError("Autopower must be finite and nonnegative")
    if not np.any(power > 0.0):
        raise ValueError("Autopower cannot be entirely zero")
    return power, speeds, frequencies, float(radii[0]), angles


def isolated_tone(
    frequencies_hz: np.ndarray,
    spectrum_pa2: np.ndarray,
    center_hz: float,
    half_width_hz: float,
) -> dict[str, float | bool | int]:
    distance = np.abs(frequencies_hz - center_hz)
    tone = distance <= half_width_hz
    sideband = (distance >= 2.0 * half_width_hz) & (
        distance <= 4.0 * half_width_hz
    )
    tone_bins = int(np.count_nonzero(tone))
    sideband_bins = int(np.count_nonzero(sideband))
    if tone_bins == 0 or sideband_bins < 2:
        raise ValueError(f"insufficient bins around tone {center_hz} Hz")
    window_power = float(np.sum(spectrum_pa2[tone]))
    floor_per_bin = float(np.median(spectrum_pa2[sideband]))
    estimated_floor = floor_per_bin * tone_bins
    isolated_power = window_power - estimated_floor
    prominence_db = (
        math.inf
        if estimated_floor == 0.0 and window_power > 0.0
        else 10.0 * math.log10(
            max(window_power, np.finfo(np.float64).tiny)
            / max(estimated_floor, np.finfo(np.float64).tiny)
        )
    )
    detected = isolated_power > 0.0 and prominence_db >= 6.0
    return {
        "center_hz": center_hz,
        "tone_bin_count": tone_bins,
        "sideband_bin_count": sideband_bins,
        "window_power_pa2": window_power,
        "estimated_floor_power_pa2": estimated_floor,
        "isolated_power_pa2": max(
            isolated_power,
            np.finfo(np.float64).tiny,
        ),
        "local_prominence_db": prominence_db,
        "detected": detected,
    }


def band_level(
    frequencies_hz: np.ndarray,
    spectrum_pa2: np.ndarray,
    minimum_hz: float,
    maximum_hz: float,
    radius_m: float,
    reference_distance_m: float,
) -> float:
    selected = (frequencies_hz >= minimum_hz) & (
        frequencies_hz < maximum_hz
    )
    if not np.any(selected):
        raise ValueError(
            f"no bins in requested band [{minimum_hz}, {maximum_hz})"
        )
    return normalize_level_to_distance(
        spl_db(float(np.sum(spectrum_pa2[selected]))),
        radius_m,
        reference_distance_m,
    )


def fit_rpm_trend(
    rpm: np.ndarray,
    levels_db: np.ndarray,
) -> dict[str, Any]:
    x = np.log2(rpm / rpm[0])
    design = np.column_stack((np.ones_like(x), x))
    coefficients, _, _, _ = np.linalg.lstsq(design, levels_db, rcond=None)
    predicted = design @ coefficients
    residual = predicted - levels_db
    return {
        "reference_rpm": float(rpm[0]),
        "reference_level_db": float(coefficients[0]),
        "slope_db_per_rpm_doubling": float(coefficients[1]),
        "fit_rmse_db": float(np.sqrt(np.mean(residual**2))),
        "maximum_absolute_error_db": float(np.max(np.abs(residual))),
    }


def analyze_arrays(
    *,
    autopower_pa2: np.ndarray,
    rpm: np.ndarray,
    frequency_hz: np.ndarray,
    radius_m: np.ndarray,
    theta_deg: np.ndarray,
    blade_count: int,
    maximum_harmonics: int,
    tone_half_width_hz: float,
    reference_distance_m: float,
    dataset_doi: str,
    source_file: dict[str, Any],
) -> dict[str, Any]:
    if blade_count <= 0 or maximum_harmonics <= 0:
        raise ValueError("blade_count and maximum_harmonics must be positive")
    if tone_half_width_hz <= 0.0:
        raise ValueError("tone_half_width_hz must be positive")
    power, speeds, frequencies, radius, angles = validate_arrays(
        autopower_pa2,
        rpm,
        frequency_hz,
        radius_m,
        theta_deg,
    )
    if tone_half_width_hz < float(np.diff(frequencies)[0]):
        raise ValueError("tone_half_width_hz must cover at least one bin width")
    plane_candidates = np.flatnonzero(
        np.isclose(angles, 0.0, rtol=0.0, atol=1.0e-9)
    )
    plane_index = int(plane_candidates[0])

    measurements: list[dict[str, Any]] = []
    harmonic_levels = np.full(
        (maximum_harmonics, angles.size, speeds.size),
        np.nan,
        dtype=np.float64,
    )
    harmonic_detected = np.zeros(
        (maximum_harmonics, angles.size, speeds.size),
        dtype=bool,
    )
    for rpm_index, speed in enumerate(speeds):
        bpf_hz = float(speed * blade_count / 60.0)
        for angle_index, angle in enumerate(angles):
            spectrum = power[:, angle_index, rpm_index]
            inclusive_bands = {
                name: band_level(
                    frequencies,
                    spectrum,
                    limits[0],
                    min(limits[1], float(frequencies[-1])),
                    radius,
                    reference_distance_m,
                )
                for name, limits in DEFAULT_BANDS_HZ.items()
                if limits[0] < frequencies[-1]
            }
            harmonics = []
            bpf_removed_spectrum = np.array(spectrum, copy=True)
            for harmonic in range(1, maximum_harmonics + 1):
                center = bpf_hz * harmonic
                if center + 4.0 * tone_half_width_hz > frequencies[-1]:
                    break
                tone = isolated_tone(
                    frequencies,
                    spectrum,
                    center,
                    tone_half_width_hz,
                )
                level = normalize_level_to_distance(
                    spl_db(float(tone["isolated_power_pa2"])),
                    radius,
                    reference_distance_m,
                )
                tone["harmonic"] = harmonic
                tone["level_db_spl_at_reference_distance"] = level
                harmonics.append(tone)
                tone_bins = (
                    np.abs(frequencies - center) <= tone_half_width_hz
                )
                bpf_removed_spectrum[tone_bins] = (
                    float(tone["estimated_floor_power_pa2"])
                    / int(tone["tone_bin_count"])
                )
                harmonic_levels[
                    harmonic - 1,
                    angle_index,
                    rpm_index,
                ] = level
                harmonic_detected[
                    harmonic - 1,
                    angle_index,
                    rpm_index,
                ] = bool(tone["detected"])
            bpf_removed_bands = {
                name: band_level(
                    frequencies,
                    bpf_removed_spectrum,
                    limits[0],
                    min(limits[1], float(frequencies[-1])),
                    radius,
                    reference_distance_m,
                )
                for name, limits in DEFAULT_BANDS_HZ.items()
                if limits[0] < frequencies[-1]
            }
            measurements.append(
                {
                    "rpm": float(speed),
                    "blade_pass_frequency_hz": bpf_hz,
                    "theta_deg": float(angle),
                    "axis_cosine": float(
                        axis_cosine(np.asarray([angle]))[0]
                    ),
                    "band_levels_db_spl_at_reference_distance": (
                        inclusive_bands
                    ),
                    "bpf_removed_band_levels_db_spl_at_reference_distance": (
                        bpf_removed_bands
                    ),
                    "harmonics": harmonics,
                }
            )

    directivity = []
    rpm_trends = []
    for harmonic in range(1, maximum_harmonics + 1):
        for rpm_index, speed in enumerate(speeds):
            levels = harmonic_levels[harmonic - 1, :, rpm_index]
            detected = harmonic_detected[harmonic - 1, :, rpm_index]
            if np.all(np.isfinite(levels)) and np.all(detected):
                relative = levels - levels[plane_index]
                directivity.append(
                    {
                        "harmonic": harmonic,
                        "rpm": float(speed),
                        "plane_level_db_spl_at_reference_distance": float(
                            levels[plane_index]
                        ),
                        "measured_relative_db": [
                            float(value) for value in relative
                        ],
                        "fit": fit_even_directivity(angles, relative),
                    }
                )
        plane_levels = harmonic_levels[harmonic - 1, plane_index, :]
        plane_detected = harmonic_detected[harmonic - 1, plane_index, :]
        if np.all(np.isfinite(plane_levels)) and np.all(plane_detected):
            rpm_trends.append(
                {
                    "harmonic": harmonic,
                    **fit_rpm_trend(speeds, plane_levels),
                }
            )

    report = {
        "schema_version": SCHEMA_VERSION,
        "evidence_class": EVIDENCE_CLASS,
        "dataset_doi": dataset_doi,
        "source_file": source_file,
        "release_constraints": dict(RELEASE_CONSTRAINTS),
        "processing_limits": {
            "input_is_processed_autopower_pa2": True,
            "time_domain_amplitude_modulation_not_recoverable": True,
            "phase_not_recoverable": True,
            "transients_not_recoverable": True,
            "synchronized_tach_trace_not_available": True,
        },
        "parameters": {
            "blade_count": blade_count,
            "maximum_harmonics": maximum_harmonics,
            "tone_half_width_hz": tone_half_width_hz,
            "minimum_tone_prominence_db": 6.0,
            "reference_distance_m": reference_distance_m,
            "pressure_reference_pa": REFERENCE_PRESSURE_PA,
        },
        "dimensions": {
            "frequency_bins": int(frequencies.size),
            "angles": int(angles.size),
            "operating_points": int(speeds.size),
            "autopower_shape": [int(value) for value in power.shape],
        },
        "coordinates": {
            "rpm": [float(value) for value in speeds],
            "theta_deg": [float(value) for value in angles],
            "radius_m": radius,
            "frequency_min_hz": float(frequencies[0]),
            "frequency_max_hz": float(frequencies[-1]),
            "frequency_bin_width_hz": float(np.diff(frequencies)[0]),
        },
        "measurements": measurements,
        "complete_detected_harmonic_directivity": directivity,
        "complete_detected_harmonic_rpm_trends": rpm_trends,
    }
    validate_report(report)
    return report


def validate_report(report: dict[str, Any]) -> None:
    if report.get("schema_version") != SCHEMA_VERSION:
        raise ValueError("spectral-reference schema version mismatch")
    if report.get("evidence_class") != EVIDENCE_CLASS:
        raise ValueError("spectral-reference evidence class mismatch")
    constraints = report.get("release_constraints")
    if constraints != RELEASE_CONSTRAINTS:
        raise ValueError("release constraints cannot be weakened or changed")
    limits = report.get("processing_limits")
    if not isinstance(limits, dict) or not all(limits.values()):
        raise ValueError("all processed-autopower limitations must be explicit")
    if not report.get("measurements"):
        raise ValueError("spectral-reference report has no measurements")


def load_hdf5(
    path: Path,
    group_name: str | None,
) -> tuple[str, dict[str, np.ndarray]]:
    try:
        import h5py
    except ModuleNotFoundError as error:
        raise RuntimeError(
            "HDF5 input requires h5py; install with "
            "'python -m pip install h5py==3.12.1'"
        ) from error
    with h5py.File(path, "r") as source:
        roots = list(source.keys())
        selected = group_name
        if selected is None:
            if len(roots) != 1:
                raise ValueError(
                    "HDF5 must contain one root group or --group must be set"
                )
            selected = roots[0]
        if selected not in source:
            raise ValueError(f"HDF5 group not found: {selected}")
        group = source[selected]
        missing = [name for name in REQUIRED_DATASETS if name not in group]
        if missing:
            raise ValueError(f"HDF5 group is missing datasets: {missing}")
        arrays = {name: np.asarray(group[name]) for name in REQUIRED_DATASETS}
    return selected, arrays


def write_json_atomic(path: Path, report: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(
        report,
        ensure_ascii=False,
        indent=2,
        sort_keys=True,
        allow_nan=False,
    ) + "\n"
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=path.name + ".",
        suffix=".tmp",
        dir=path.parent,
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(payload)
        os.replace(temporary_name, path)
    except BaseException:
        try:
            os.unlink(temporary_name)
        except FileNotFoundError:
            pass
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--h5", type=Path, required=True)
    parser.add_argument("--group")
    parser.add_argument("--blade-count", type=int, required=True)
    parser.add_argument("--maximum-harmonics", type=int, default=8)
    parser.add_argument("--tone-half-width-hz", type=float, default=6.25)
    parser.add_argument("--reference-distance-m", type=float, default=1.0)
    parser.add_argument("--dataset-doi", required=True)
    parser.add_argument("--expected-md5")
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()

    if not args.h5.is_file():
        parser.error(f"HDF5 file not found: {args.h5}")
    md5 = hash_file(args.h5, "md5")
    if args.expected_md5 and md5.lower() != args.expected_md5.lower():
        parser.error(
            f"HDF5 MD5 mismatch: expected {args.expected_md5}, got {md5}"
        )
    group_name, arrays = load_hdf5(args.h5, args.group)
    source_file = {
        "path": str(args.h5),
        "bytes": args.h5.stat().st_size,
        "md5": md5,
        "sha256": hash_file(args.h5, "sha256"),
        "hdf5_group": group_name,
    }
    report = analyze_arrays(
        autopower_pa2=arrays["Autopower"],
        rpm=arrays["RPM"],
        frequency_hz=arrays["frequency_Hz"],
        radius_m=arrays["radius_m"],
        theta_deg=arrays["theta_deg"],
        blade_count=args.blade_count,
        maximum_harmonics=args.maximum_harmonics,
        tone_half_width_hz=args.tone_half_width_hz,
        reference_distance_m=args.reference_distance_m,
        dataset_doi=args.dataset_doi,
        source_file=source_file,
    )
    write_json_atomic(args.output_json, report)
    print(
        json.dumps(
            {
                "status": "valid",
                "evidence_class": report["evidence_class"],
                "rpm_points": report["dimensions"]["operating_points"],
                "angles": report["dimensions"]["angles"],
                "frequency_bins": report["dimensions"]["frequency_bins"],
                "complete_directivity_fits": len(
                    report["complete_detected_harmonic_directivity"]
                ),
                "complete_rpm_trends": len(
                    report["complete_detected_harmonic_rpm_trends"]
                ),
                "release_profile_eligible": False,
                "output_json": str(args.output_json),
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
