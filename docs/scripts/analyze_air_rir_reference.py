#!/usr/bin/env python3
"""Analyze pinned measured AIR RIRs without promoting them as calibration."""

from __future__ import annotations

import argparse
import io
import json
import math
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np
import scipy.io
from scipy.signal import butter, sosfilt

import fetch_air_rir_reference as air_fetch


SAMPLE_RATE = 48_000
DIRECT_PRE_SECONDS = 0.001
DIRECT_POST_SECONDS = 0.0025
MODEL_BANDS_HZ = (
    ("low", 125.0, 700.0),
    ("mid", 700.0, 4_000.0),
    ("high", 4_000.0, 20_000.0),
)
PUBLISHED_CASES = (
    ("booth", 1, 0.50, 0.08),
    ("booth", 2, 1.00, 0.11),
    ("booth", 3, 1.50, 0.18),
    ("office", 1, 1.00, 0.37),
    ("office", 2, 2.00, 0.44),
    ("office", 3, 3.00, 0.48),
    ("meeting", 1, 1.45, 0.21),
    ("meeting", 2, 1.70, 0.22),
    ("meeting", 3, 1.90, 0.21),
    ("meeting", 4, 2.25, 0.24),
    ("meeting", 5, 2.80, 0.25),
    ("lecture", 1, 2.25, 0.70),
    ("lecture", 2, 4.00, 0.72),
    ("lecture", 3, 5.56, 0.79),
    ("lecture", 4, 7.10, 0.80),
    ("lecture", 5, 8.68, 0.81),
    ("lecture", 6, 10.20, 0.83),
)


@dataclass(frozen=True)
class DecayFit:
    slope_db_per_second: float
    intercept_db: float
    r_squared: float
    extrapolated_decay_seconds: float
    extrapolated_from_ir_start_seconds: float


def _finite_float(value: float) -> float:
    result = float(value)
    if not math.isfinite(result):
        raise ValueError(f"non-finite result {result}")
    return result


def schroeder_decay_db(samples: np.ndarray) -> np.ndarray:
    signal = np.asarray(samples, dtype=np.float64).reshape(-1)
    if signal.size < 16 or not np.all(np.isfinite(signal)):
        raise ValueError("RIR must contain at least 16 finite samples")
    energy = signal * signal
    total = float(np.sum(energy))
    if not total > 0.0:
        raise ValueError("RIR energy must be positive")
    integrated = np.cumsum(energy[::-1], dtype=np.float64)[::-1]
    return 10.0 * np.log10(np.maximum(integrated / total, 1.0e-15))


def fit_decay(
    samples: np.ndarray,
    sample_rate: int,
    upper_db: float,
    lower_db: float,
) -> DecayFit:
    if sample_rate <= 0:
        raise ValueError("sample rate must be positive")
    if not (0.0 >= upper_db > lower_db >= -60.0):
        raise ValueError("invalid decay regression interval")
    decay_db = schroeder_decay_db(samples)
    mask = (decay_db <= upper_db) & (decay_db >= lower_db)
    indices = np.flatnonzero(mask)
    if indices.size < 32:
        raise ValueError(
            f"only {indices.size} samples in {upper_db}..{lower_db} dB fit"
        )
    time_seconds = indices.astype(np.float64) / float(sample_rate)
    levels = decay_db[indices]
    slope, intercept = np.polyfit(time_seconds, levels, 1)
    if not slope < 0.0:
        raise ValueError(f"decay slope must be negative, got {slope}")
    predicted = slope * time_seconds + intercept
    residual = float(np.sum((levels - predicted) ** 2))
    centered = float(np.sum((levels - np.mean(levels)) ** 2))
    r_squared = 1.0 if centered == 0.0 else 1.0 - residual / centered
    return DecayFit(
        slope_db_per_second=_finite_float(slope),
        intercept_db=_finite_float(intercept),
        r_squared=_finite_float(r_squared),
        extrapolated_decay_seconds=_finite_float(-60.0 / slope),
        extrapolated_from_ir_start_seconds=_finite_float(
            (-60.0 - intercept) / slope
        ),
    )


def direct_arrival_index(samples: np.ndarray) -> int:
    signal = np.abs(np.asarray(samples, dtype=np.float64).reshape(-1))
    peak_index = int(np.argmax(signal))
    peak = float(signal[peak_index])
    if peak <= 0.0:
        raise ValueError("RIR peak must be positive")
    candidates = np.flatnonzero(signal[: peak_index + 1] >= peak * 0.1)
    if candidates.size == 0:
        raise ValueError("direct arrival was not found")
    return int(candidates[0])


def direct_to_reverberant_ratio_db(
    samples: np.ndarray,
    sample_rate: int,
) -> float:
    signal = np.asarray(samples, dtype=np.float64).reshape(-1)
    onset = direct_arrival_index(signal)
    first = max(0, onset - round(DIRECT_PRE_SECONDS * sample_rate))
    last = min(
        signal.size,
        onset + round(DIRECT_POST_SECONDS * sample_rate) + 1,
    )
    energy = signal * signal
    direct = float(np.sum(energy[first:last]))
    reverberant = float(np.sum(energy[last:]))
    if direct <= 0.0 or reverberant <= 0.0:
        raise ValueError("DRR windows must both contain energy")
    return _finite_float(10.0 * math.log10(direct / reverberant))


def bandpass(
    samples: np.ndarray,
    sample_rate: int,
    low_hz: float,
    high_hz: float,
) -> np.ndarray:
    nyquist = sample_rate * 0.5
    if not 0.0 < low_hz < high_hz < nyquist:
        raise ValueError("band must lie strictly inside Nyquist")
    sections = butter(
        4,
        (low_hz, high_hz),
        btype="bandpass",
        fs=sample_rate,
        output="sos",
    )
    return np.asarray(sosfilt(sections, samples), dtype=np.float64)


def _fit_json(fit: DecayFit) -> dict[str, float]:
    return {
        "slope_db_per_second": fit.slope_db_per_second,
        "intercept_db": fit.intercept_db,
        "r_squared": fit.r_squared,
        "extrapolated_decay_seconds": fit.extrapolated_decay_seconds,
        "extrapolated_from_ir_start_seconds": (
            fit.extrapolated_from_ir_start_seconds
        ),
    }


def analyze_channel(samples: np.ndarray, sample_rate: int) -> dict[str, Any]:
    t20 = fit_decay(samples, sample_rate, -5.0, -25.0)
    edt = fit_decay(samples, sample_rate, 0.0, -10.0)
    bands: dict[str, Any] = {}
    for name, low_hz, high_hz in MODEL_BANDS_HZ:
        filtered = bandpass(samples, sample_rate, low_hz, high_hz)
        bands[name] = {
            "low_hz": low_hz,
            "high_hz": high_hz,
            "t20": _fit_json(
                fit_decay(filtered, sample_rate, -5.0, -25.0)
            ),
            "edt": _fit_json(
                fit_decay(filtered, sample_rate, 0.0, -10.0)
            ),
            "drr_db": direct_to_reverberant_ratio_db(
                filtered, sample_rate
            ),
        }
    return {
        "sample_count": int(np.asarray(samples).size),
        "direct_arrival_sample": direct_arrival_index(samples),
        "t20": _fit_json(t20),
        "edt": _fit_json(edt),
        "drr_db": direct_to_reverberant_ratio_db(samples, sample_rate),
        "bands": bands,
    }


def _load_channel(
    archive: zipfile.ZipFile,
    room: str,
    rir_number: int,
    channel: int,
    expected_distance_m: float,
) -> tuple[np.ndarray, str]:
    entry = (
        f"AIR_1_4/air_binaural_{room}_{channel}_0_{rir_number}.mat"
    )
    try:
        payload = archive.read(entry)
    except KeyError as error:
        raise ValueError(f"missing RIR entry {entry}") from error
    mat = scipy.io.loadmat(
        io.BytesIO(payload),
        squeeze_me=True,
        struct_as_record=False,
    )
    samples = np.asarray(mat["h_air"], dtype=np.float64).reshape(-1)
    info = mat["air_info"]
    if int(info.fs) != SAMPLE_RATE:
        raise ValueError(f"{entry}: sample rate {info.fs} != {SAMPLE_RATE}")
    if str(info.room) != room:
        raise ValueError(f"{entry}: room metadata mismatch")
    if int(info.head) != 0 or int(info.channel) != channel:
        raise ValueError(f"{entry}: head/channel metadata mismatch")
    distance_m = float(info.distance) / 100.0
    if not math.isclose(distance_m, expected_distance_m, abs_tol=1.0e-9):
        raise ValueError(
            f"{entry}: distance {distance_m} != {expected_distance_m}"
        )
    return samples, entry


def _mean(values: list[float]) -> float:
    return _finite_float(float(np.mean(np.asarray(values, dtype=np.float64))))


def analyze_archive(path: Path) -> dict[str, Any]:
    air_fetch.validate(path)
    measurements: list[dict[str, Any]] = []
    with zipfile.ZipFile(path) as archive:
        for room, rir_number, distance_m, published_rt60 in PUBLISHED_CASES:
            channels = []
            for channel in (0, 1):
                samples, entry = _load_channel(
                    archive,
                    room,
                    rir_number,
                    channel,
                    distance_m,
                )
                analysis = analyze_channel(samples, SAMPLE_RATE)
                analysis["channel"] = channel
                analysis["archive_entry"] = entry
                channels.append(analysis)
            measured_rt60 = _mean(
                [
                    channel["t20"][
                        "extrapolated_from_ir_start_seconds"
                    ]
                    for channel in channels
                ]
            )
            relative_error = abs(measured_rt60 - published_rt60) / (
                published_rt60
            )
            averaged_bands = {}
            for band_name, _, _ in MODEL_BANDS_HZ:
                averaged_bands[band_name] = {
                    "rt60_seconds": _mean(
                        [
                            channel["bands"][band_name]["t20"][
                                "extrapolated_decay_seconds"
                            ]
                            for channel in channels
                        ]
                    ),
                    "edt_seconds": _mean(
                        [
                            channel["bands"][band_name]["edt"][
                                "extrapolated_decay_seconds"
                            ]
                            for channel in channels
                        ]
                    ),
                    "drr_db": _mean(
                        [
                            channel["bands"][band_name]["drr_db"]
                            for channel in channels
                        ]
                    ),
                }
            measurements.append(
                {
                    "room": room,
                    "rir_number": rir_number,
                    "distance_m": distance_m,
                    "published_rt60_seconds": published_rt60,
                    "t20_rt60_from_ir_start_seconds": measured_rt60,
                    "published_relative_error": relative_error,
                    "drr_db": _mean(
                        [channel["drr_db"] for channel in channels]
                    ),
                    "bands": averaged_bands,
                    "channels": channels,
                }
            )

    errors = [
        measurement["published_relative_error"]
        for measurement in measurements
    ]
    room_summary = []
    for room in ("booth", "meeting", "office", "lecture"):
        cases = [
            measurement
            for measurement in measurements
            if measurement["room"] == room
        ]
        published_mean = _mean(
            [case["published_rt60_seconds"] for case in cases]
        )
        analyzed_mean = _mean(
            [case["t20_rt60_from_ir_start_seconds"] for case in cases]
        )
        room_summary.append(
            {
                "room": room,
                "published_mean_rt60_seconds": published_mean,
                "analyzed_mean_rt60_seconds": analyzed_mean,
                "relative_error": abs(analyzed_mean - published_mean)
                / published_mean,
                "mean_drr_db": _mean([case["drr_db"] for case in cases]),
                "mean_band_rt60_seconds": {
                    band_name: _mean(
                        [
                            case["bands"][band_name]["rt60_seconds"]
                            for case in cases
                        ]
                    )
                    for band_name, _, _ in MODEL_BANDS_HZ
                },
            }
        )
    room_rt60 = {
        summary["room"]: summary["analyzed_mean_rt60_seconds"]
        for summary in room_summary
    }
    all_r_squared = [
        channel["t20"]["r_squared"]
        for measurement in measurements
        for channel in measurement["channels"]
    ]
    report = {
        "schema_version": 1,
        "status": "valid-reference-diagnostic",
        "source": {
            "dataset": "RWTH Aachen Impulse Response Database",
            "release": "1.4",
            "primary_url": (
                "https://www.iks.rwth-aachen.de/en/research/"
                "tools-downloads/databases/"
                "aachen-impulse-response-database/"
            ),
            "paper_doi": "10.1109/ICDSP.2009.5201259",
            "archive_bytes": air_fetch.EXPECTED_BYTES,
            "archive_sha256": air_fetch.EXPECTED_SHA256,
            "license": "MIT",
            "embedded_license_sha256": (
                air_fetch.EXPECTED_LICENSE_SHA256
            ),
            "measured_rir": True,
        },
        "method": {
            "sample_rate_hz": SAMPLE_RATE,
            "fullband_rt60": (
                "Schroeder reverse integration; linear T20 regression "
                "from -5 to -25 dB; published comparison extrapolates "
                "the -60 dB crossing on the original IR time axis "
                "because the paper explicitly retains propagation delay"
            ),
            "edt": (
                "Schroeder reverse integration; 0 to -10 dB regression; "
                "-60 dB slope extrapolation"
            ),
            "drr": (
                "first sample within 20 dB of peak; direct window "
                "-1.0 to +2.5 ms; remaining energy is reverberant; "
                "project diagnostic convention, not published AIR truth"
            ),
            "model_bands_hz": [
                {"name": name, "low": low, "high": high}
                for name, low, high in MODEL_BANDS_HZ
            ],
            "dummy_head": False,
            "channels": [0, 1],
        },
        "published_rt60_reproduction": {
            "position_count": len(measurements),
            "mean_absolute_relative_error": _mean(errors),
            "median_absolute_relative_error": _finite_float(
                float(np.median(errors))
            ),
            "p95_absolute_relative_error": _finite_float(
                float(np.quantile(errors, 0.95))
            ),
            "maximum_absolute_relative_error": max(errors),
            "positions_within_15_percent": sum(
                error <= 0.15 for error in errors
            ),
            "minimum_t20_fit_r_squared": min(all_r_squared),
        },
        "room_summary": room_summary,
        "measurements": measurements,
        "gates": {
            "archive_and_embedded_license_pinned": True,
            "all_fullband_t20_fits_r_squared_at_least_0_90": (
                min(all_r_squared) >= 0.90
            ),
            "median_published_rt60_error_at_most_15_percent": (
                float(np.median(errors)) <= 0.15
            ),
            "every_published_rt60_error_at_most_15_percent": (
                max(errors) <= 0.15
            ),
            "room_mean_order_booth_meeting_office_lecture": (
                room_rt60["booth"]
                < room_rt60["meeting"]
                < room_rt60["office"]
                < room_rt60["lecture"]
            ),
            "minecraft_release_calibrated": False,
        },
        "claim_boundary": (
            "AIR supplies measured propagation references and a published "
            "fullband RT60 table. It does not provide a matching Minecraft "
            "voxel/material scene, published DRR truth, or FPV source data. "
            "No estimator or FDN parameter is release-calibrated by this "
            "report."
        ),
    }
    validate_report(report)
    return report


def validate_report(report: dict[str, Any]) -> None:
    if report.get("schema_version") != 1:
        raise ValueError("RIR report schema must be 1")
    if report.get("status") != "valid-reference-diagnostic":
        raise ValueError("RIR report status is invalid")
    gates = report.get("gates")
    if not isinstance(gates, dict):
        raise ValueError("RIR report gates are missing")
    required_true = (
        "archive_and_embedded_license_pinned",
        "all_fullband_t20_fits_r_squared_at_least_0_90",
        "median_published_rt60_error_at_most_15_percent",
        "room_mean_order_booth_meeting_office_lecture",
    )
    if not all(gates.get(name) is True for name in required_true):
        raise ValueError("required RIR reference gate failed")
    if gates.get("minecraft_release_calibrated") is not False:
        raise ValueError("RIR report cannot claim Minecraft calibration")
    if gates.get("every_published_rt60_error_at_most_15_percent") is True:
        raise ValueError(
            "schema-v1 evidence must preserve the short-room mismatch"
        )
    if len(report.get("measurements", [])) != len(PUBLISHED_CASES):
        raise ValueError("RIR report measurement count mismatch")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--output-json", required=True, type=Path)
    args = parser.parse_args()
    report = analyze_archive(args.archive)
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(
        report,
        indent=2,
        sort_keys=True,
        allow_nan=False,
    ) + "\n"
    args.output_json.write_text(payload, encoding="utf-8")
    summary = report["published_rt60_reproduction"]
    print(
        json.dumps(
            {
                "status": report["status"],
                "positions": summary["position_count"],
                "median_relative_error": summary[
                    "median_absolute_relative_error"
                ],
                "maximum_relative_error": summary[
                    "maximum_absolute_relative_error"
                ],
                "output": str(args.output_json.resolve()),
            },
            separators=(",", ":"),
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
