#!/usr/bin/env python3
"""Fit a low-order, band-dependent source directivity model to NEAPTIDE hover audio.

The NEAPTIDE files are already microphone-calibrated, ground-reflection corrected,
and normalized to a 1 m reference distance. The published PCM convention stores
20 times the pressure in micro-pascals, so 20,000,000 integer units equal 1 Pa.

This script is for research validation only. NEAPTIDE is CC BY-NC 4.0 and its
audio or fitted values must not silently become a commercial product profile.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import wave
from io import BytesIO
from pathlib import Path
from typing import BinaryIO
from zipfile import ZipFile

import numpy as np


REFERENCE_PRESSURE_PA = 20.0e-6
PCM_UNITS_PER_PASCAL = 20_000_000.0
NEAPTIDE_RECORD_URL = "https://zenodo.org/records/10512044"
NEAPTIDE_LICENSE = "CC BY-NC 4.0"
MICROPHONE_ELEVATION_DEGREES = (90.0, 71.6, 56.4, 45.0, 26.6, 0.0, -3.5)
BANDS_HZ = {
    "low": (20.0, 300.0),
    "mid": (300.0, 3_200.0),
    "high": (3_200.0, 20_000.0),
}
PLANE_REFERENCE_MICROPHONES = (6, 7)
FIT_RMSE_GATE_DB = 2.5
LOO_RMSE_GATE_DB = 3.0


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def read_pcm32_mono(stream: BinaryIO) -> tuple[int, np.ndarray]:
    with wave.open(stream, "rb") as source:
        if source.getnchannels() != 1:
            raise ValueError("expected mono WAV")
        if source.getsampwidth() != 4:
            raise ValueError("expected 32-bit PCM WAV")
        if source.getcomptype() != "NONE":
            raise ValueError("expected uncompressed PCM WAV")
        sample_rate = source.getframerate()
        samples = np.frombuffer(
            source.readframes(source.getnframes()),
            dtype="<i4",
        ).astype(np.float64)
    if sample_rate <= 0 or samples.size == 0:
        raise ValueError("WAV must contain samples at a positive sample rate")
    return sample_rate, samples


def pcm_to_pressure_pa(samples: np.ndarray) -> np.ndarray:
    return samples / PCM_UNITS_PER_PASCAL


def spl_db(power_pa2: float) -> float:
    if not math.isfinite(power_pa2) or power_pa2 <= 0.0:
        raise ValueError("pressure power must be finite and positive")
    return 10.0 * math.log10(power_pa2 / (REFERENCE_PRESSURE_PA**2))


def welch_band_powers(
    pressure_pa: np.ndarray,
    sample_rate: int,
    segment_samples: int = 8_192,
) -> dict[str, float]:
    if pressure_pa.ndim != 1 or pressure_pa.size < segment_samples:
        raise ValueError("audio must be mono and at least one Welch segment long")
    hop = segment_samples // 2
    window = np.hanning(segment_samples)
    normalization = sample_rate * float(np.sum(window * window))
    accumulated = np.zeros(segment_samples // 2 + 1, dtype=np.float64)
    segment_count = 0
    for start in range(0, pressure_pa.size - segment_samples + 1, hop):
        segment = pressure_pa[start : start + segment_samples]
        windowed = (segment - float(np.mean(segment))) * window
        spectrum = np.fft.rfft(windowed)
        density = np.abs(spectrum) ** 2 / normalization
        density[1:-1] *= 2.0
        accumulated += density
        segment_count += 1
    if segment_count == 0:
        raise ValueError("no complete Welch segments")
    density = accumulated / segment_count
    frequencies = np.fft.rfftfreq(segment_samples, 1.0 / sample_rate)
    bin_width_hz = sample_rate / segment_samples
    result: dict[str, float] = {}
    for name, (minimum_hz, maximum_hz) in BANDS_HZ.items():
        effective_maximum = min(maximum_hz, sample_rate / 2.0)
        selected = (frequencies >= minimum_hz) & (frequencies < effective_maximum)
        if not np.any(selected):
            raise ValueError(f"no FFT bins in {name} band")
        result[name] = float(np.sum(density[selected]) * bin_width_hz)
    return result


def axis_cosine_from_elevation(elevation_degrees: float) -> float:
    """Return |normal dot listener_direction| for a level hovering rotor disk."""
    return abs(math.sin(math.radians(elevation_degrees)))


def fit_even_polynomial(
    axis_cosines: np.ndarray,
    relative_levels_db: np.ndarray,
) -> dict[str, object]:
    """Fit D(mu)=c2*mu^2+c4*mu^4, anchored to 0 dB in the rotor plane."""
    design = np.column_stack((axis_cosines**2, axis_cosines**4))
    coefficients, _, _, _ = np.linalg.lstsq(
        design,
        relative_levels_db,
        rcond=None,
    )
    predicted = design @ coefficients
    residual = predicted - relative_levels_db
    loo_residuals = []
    for held_out in range(axis_cosines.size):
        retained = np.arange(axis_cosines.size) != held_out
        held_coefficients, _, _, _ = np.linalg.lstsq(
            design[retained],
            relative_levels_db[retained],
            rcond=None,
        )
        loo_residuals.append(
            float(design[held_out] @ held_coefficients - relative_levels_db[held_out])
        )
    fit_rmse = float(np.sqrt(np.mean(residual**2)))
    loo_rmse = float(np.sqrt(np.mean(np.asarray(loo_residuals) ** 2)))
    return {
        "c2_db": float(coefficients[0]),
        "c4_db": float(coefficients[1]),
        "axis_db": float(coefficients.sum()),
        "fit_rmse_db": fit_rmse,
        "leave_one_out_rmse_db": loo_rmse,
        "passes_internal_fit_gate": (
            fit_rmse <= FIT_RMSE_GATE_DB and loo_rmse <= LOO_RMSE_GATE_DB
        ),
        "predicted_relative_db": [float(value) for value in predicted],
    }


def energy_mean_db(levels_db: list[float]) -> float:
    mean_power = sum(10.0 ** (level / 10.0) for level in levels_db) / len(levels_db)
    return 10.0 * math.log10(mean_power)


def analyze(
    archive_path: Path,
    calibration_path: Path,
    aircraft_prefix: str,
) -> dict[str, object]:
    with calibration_path.open("rb") as calibration_stream:
        calibration_rate, calibration_pcm = read_pcm32_mono(calibration_stream)
    calibration_pressure = pcm_to_pressure_pa(calibration_pcm)
    calibration_pressure -= float(np.mean(calibration_pressure))
    calibration_level_db = spl_db(float(np.mean(calibration_pressure**2)))
    if abs(calibration_level_db - 94.0) > 0.25:
        raise ValueError(
            "calibration WAV is inconsistent with the published 94 dB convention: "
            f"{calibration_level_db:.3f} dB SPL"
        )

    measurements = []
    with ZipFile(archive_path) as archive:
        member_names = archive.namelist()
        for microphone, elevation in enumerate(MICROPHONE_ELEVATION_DEGREES, 1):
            suffix = f"/{aircraft_prefix}_Hover_{microphone}.wav"
            matches = [name for name in member_names if name.endswith(suffix)]
            if len(matches) != 1:
                raise ValueError(
                    f"expected exactly one archive member ending {suffix}, "
                    f"found {len(matches)}"
                )
            member = matches[0]
            payload = archive.read(member)
            sample_rate, pcm = read_pcm32_mono(BytesIO(payload))
            if sample_rate != calibration_rate:
                raise ValueError(
                    f"{member}: sample rate {sample_rate} differs from calibration "
                    f"{calibration_rate}"
                )
            pressure = pcm_to_pressure_pa(pcm)
            pressure -= float(np.mean(pressure))
            band_powers = welch_band_powers(pressure, sample_rate)
            levels = {
                "overall": spl_db(float(np.mean(pressure**2))),
                **{
                    name: spl_db(power)
                    for name, power in band_powers.items()
                },
            }
            measurements.append(
                {
                    "microphone": microphone,
                    "elevation_degrees": elevation,
                    "axis_cosine": axis_cosine_from_elevation(elevation),
                    "sample_count": int(pressure.size),
                    "levels_db_spl_at_1m": levels,
                }
            )

    metric_names = ("overall", *BANDS_HZ.keys())
    reference_levels = {}
    fits = {}
    axis_cosines = np.asarray(
        [row["axis_cosine"] for row in measurements],
        dtype=np.float64,
    )
    for metric in metric_names:
        reference = energy_mean_db(
            [
                measurements[microphone - 1]["levels_db_spl_at_1m"][metric]
                for microphone in PLANE_REFERENCE_MICROPHONES
            ]
        )
        reference_levels[metric] = reference
        relative = np.asarray(
            [
                row["levels_db_spl_at_1m"][metric] - reference
                for row in measurements
            ],
            dtype=np.float64,
        )
        for row, value in zip(measurements, relative):
            row.setdefault("relative_to_plane_db", {})[metric] = float(value)
        fits[metric] = fit_even_polynomial(axis_cosines, relative)

    return {
        "schema": "neaptide-directivity-analysis-v1",
        "source": {
            "record_url": NEAPTIDE_RECORD_URL,
            "license": NEAPTIDE_LICENSE,
            "archive": str(archive_path),
            "archive_sha256": sha256_file(archive_path),
            "calibration": str(calibration_path),
            "calibration_sha256": sha256_file(calibration_path),
            "aircraft_prefix": aircraft_prefix,
            "maneuver": "Hover",
        },
        "pcm_convention": {
            "pcm_units_per_pascal": PCM_UNITS_PER_PASCAL,
            "calibration_expected_db_spl": 94.0,
            "calibration_measured_db_spl": calibration_level_db,
            "sample_rate_hz": calibration_rate,
        },
        "bands_hz": {
            name: [minimum, maximum]
            for name, (minimum, maximum) in BANDS_HZ.items()
        },
        "plane_reference_microphones": list(PLANE_REFERENCE_MICROPHONES),
        "plane_reference_levels_db_spl_at_1m": reference_levels,
        "measurements": measurements,
        "even_polynomial_fits": fits,
        "research_gate": {
            "all_internal_fits_pass": all(
                fit["passes_internal_fit_gate"] for fit in fits.values()
            ),
            "eligible_for_product_default": False,
            "blocking_reasons": [
                "CC BY-NC 4.0 source requires distribution/licensing review",
                "single non-FPV aircraft is not a 5-inch FPV calibration set",
                "seven elevations do not establish full azimuthal directivity",
            ],
        },
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--calibration", required=True, type=Path)
    parser.add_argument(
        "--aircraft-prefix",
        default="DJIMavic2Enterprise",
    )
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = analyze(
        args.archive.resolve(),
        args.calibration.resolve(),
        args.aircraft_prefix,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(args.output.name + ".tmp")
    temporary.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary.replace(args.output)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
