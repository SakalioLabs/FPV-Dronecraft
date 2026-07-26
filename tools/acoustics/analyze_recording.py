#!/usr/bin/env python3
"""Extract calibrated RPM/order descriptors from steady acoustic recordings."""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import math
import os
import tempfile
import wave
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterable

import numpy as np


REFERENCE_PRESSURE_PA = 20.0e-6
NYQUIST_GUARD_RATIO = 0.45
DESCRIPTOR_FIELDS = (
    "recording_id",
    "maneuver_id",
    "split",
    "rpm",
    "elevation_deg",
    "rotor_tonal_db",
    "motor_tonal_db",
    "broadband_low_db",
    "broadband_mid_db",
    "broadband_high_db",
)
BANDS_HZ = {
    "broadband_low_db": (20.0, 300.0),
    "broadband_mid_db": (300.0, 3_200.0),
    "broadband_high_db": (3_200.0, 20_000.0),
}
RECORDING_FIELDS = (
    "recording_id",
    "maneuver_id",
    "split",
    "wav",
    "background_wav",
    "rpm_csv",
    "channel",
    "start_s",
    "duration_s",
    "background_start_s",
    "background_duration_s",
    "elevation_deg",
    "microphone_distance_m",
    "reference_level_correction_db",
    "blade_count",
    "motor_pole_pairs",
    "blade_pass_harmonics",
    "airframe_id",
    "source_configuration",
    "motor_id",
    "propeller_id",
    "microphone_id",
    "signal_chain_id",
    "thrust_n",
    "voltage_v",
    "current_a",
    "ambient_temperature_c",
    "relative_humidity_percent",
    "ambient_pressure_kpa",
)


@dataclass(frozen=True)
class Descriptor:
    recording_id: str
    maneuver_id: str
    split: str
    rpm: float
    elevation_deg: float
    rotor_tonal_db: float
    motor_tonal_db: float
    broadband_low_db: float
    broadband_mid_db: float
    broadband_high_db: float


def strict_json_load(path: Path) -> dict[str, Any]:
    def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"{path}: duplicate JSON field {key!r}")
            result[key] = value
        return result

    with path.open("r", encoding="utf-8") as stream:
        value = json.load(
            stream,
            object_pairs_hook=unique_object,
            parse_constant=lambda token: (_ for _ in ()).throw(
                ValueError(f"{path}: non-finite JSON number {token}")
            ),
        )
    if not isinstance(value, dict):
        raise ValueError(f"{path}: root must be an object")
    return value


def exact_fields(value: dict[str, Any], expected: Iterable[str], path: str) -> None:
    expected_set = set(expected)
    actual_set = set(value)
    missing = sorted(expected_set - actual_set)
    unknown = sorted(actual_set - expected_set)
    if missing or unknown:
        raise ValueError(
            f"{path}: field mismatch; missing={missing}, unknown={unknown}"
        )


def finite(value: Any, path: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{path}: expected a number")
    result = float(value)
    if not math.isfinite(result):
        raise ValueError(f"{path}: expected a finite number")
    return result


def integer(value: Any, minimum: int, maximum: int, path: str) -> int:
    number = finite(value, path)
    if number != round(number) or not minimum <= number <= maximum:
        raise ValueError(f"{path}: expected an integer in [{minimum}, {maximum}]")
    return int(number)


def text(value: Any, path: str) -> str:
    if not isinstance(value, str) or not value.strip() or len(value.strip()) > 1024:
        raise ValueError(f"{path}: expected 1 to 1024 non-blank characters")
    return value.strip()


def resolve_input(base: Path, value: Any, path: str) -> Path:
    raw = Path(text(value, path))
    resolved = raw if raw.is_absolute() else base / raw
    resolved = resolved.resolve()
    if not resolved.is_file():
        raise ValueError(f"{path}: file does not exist: {resolved}")
    return resolved


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def decode_pcm(raw: bytes, sample_width: int) -> np.ndarray:
    if sample_width == 1:
        return (
            np.frombuffer(raw, dtype=np.uint8).astype(np.float64) - 128.0
        ) / 128.0
    if sample_width == 2:
        return np.frombuffer(raw, dtype="<i2").astype(np.float64) / 32768.0
    if sample_width == 3:
        bytes_view = np.frombuffer(raw, dtype=np.uint8).reshape(-1, 3)
        values = (
            bytes_view[:, 0].astype(np.int32)
            | (bytes_view[:, 1].astype(np.int32) << 8)
            | (bytes_view[:, 2].astype(np.int32) << 16)
        )
        values = np.where(values & 0x800000, values - 0x1000000, values)
        return values.astype(np.float64) / 8388608.0
    if sample_width == 4:
        return np.frombuffer(raw, dtype="<i4").astype(np.float64) / 2147483648.0
    raise ValueError(f"unsupported PCM sample width {sample_width} bytes")


def read_wav_segment(
    path: Path,
    channel: int,
    start_seconds: float,
    duration_seconds: float,
) -> tuple[int, np.ndarray, dict[str, int]]:
    if start_seconds < 0.0 or duration_seconds <= 0.0:
        raise ValueError("WAV start must be non-negative and duration positive")
    with wave.open(str(path), "rb") as source:
        if source.getcomptype() != "NONE":
            raise ValueError(f"{path}: WAV must be uncompressed PCM")
        sample_rate = source.getframerate()
        channels = source.getnchannels()
        sample_width = source.getsampwidth()
        if channel < 0 or channel >= channels:
            raise ValueError(f"{path}: channel {channel} is outside [0, {channels})")
        start_frame = round(start_seconds * sample_rate)
        frame_count = round(duration_seconds * sample_rate)
        if start_frame + frame_count > source.getnframes():
            raise ValueError(f"{path}: requested segment exceeds WAV duration")
        source.setpos(start_frame)
        raw = source.readframes(frame_count)
    decoded = decode_pcm(raw, sample_width)
    if decoded.size != frame_count * channels:
        raise ValueError(f"{path}: truncated PCM data")
    samples = decoded.reshape(frame_count, channels)[:, channel].copy()
    return sample_rate, samples, {
        "channels": channels,
        "sample_width_bits": sample_width * 8,
        "start_frame": start_frame,
        "frame_count": frame_count,
    }


def welch_psd(
    samples: np.ndarray,
    sample_rate: int,
    resolution_hz: float,
    overlap_fraction: float,
) -> tuple[np.ndarray, np.ndarray, int]:
    exact_segment = sample_rate / resolution_hz
    segment_samples = round(exact_segment)
    if abs(segment_samples - exact_segment) > 1.0e-9:
        raise ValueError(
            f"sample rate {sample_rate} cannot represent {resolution_hz:g} Hz "
            "Welch resolution with an integer segment"
        )
    hop = round(segment_samples * (1.0 - overlap_fraction))
    if hop < 1 or samples.size < segment_samples:
        raise ValueError("audio segment is too short for the configured Welch analysis")
    window = np.hanning(segment_samples)
    normalization = sample_rate * float(np.sum(window * window))
    accumulated = np.zeros(segment_samples // 2 + 1, dtype=np.float64)
    count = 0
    for start in range(0, samples.size - segment_samples + 1, hop):
        segment = samples[start : start + segment_samples]
        windowed = (segment - float(np.mean(segment))) * window
        spectrum = np.fft.rfft(windowed)
        density = np.abs(spectrum) ** 2 / normalization
        density[1:-1] *= 2.0
        accumulated += density
        count += 1
    if count < 2:
        raise ValueError("at least two Welch frames are required")
    return (
        np.fft.rfftfreq(segment_samples, 1.0 / sample_rate),
        accumulated / count,
        count,
    )


def integrate_psd(
    frequencies: np.ndarray,
    density: np.ndarray,
    selected: np.ndarray,
) -> float:
    if frequencies.size < 2 or density.shape != frequencies.shape:
        raise ValueError("invalid PSD arrays")
    if not np.any(selected):
        raise ValueError("PSD selection contains no bins")
    bin_width = frequencies[1] - frequencies[0]
    result = float(np.sum(density[selected]) * bin_width)
    if not math.isfinite(result) or result <= 0.0:
        raise ValueError("selected pressure power is not finite and positive")
    return result


def integrate_psd_allow_zero(
    frequencies: np.ndarray,
    density: np.ndarray,
    selected: np.ndarray,
) -> float:
    if frequencies.size < 2 or density.shape != frequencies.shape:
        raise ValueError("invalid PSD arrays")
    if not np.any(selected):
        raise ValueError("PSD selection contains no bins")
    result = float(
        np.sum(density[selected]) * (frequencies[1] - frequencies[0])
    )
    if not math.isfinite(result) or result < 0.0:
        raise ValueError("selected pressure power is not finite and non-negative")
    return result


def level_db(power_pa2: float) -> float:
    if not math.isfinite(power_pa2) or power_pa2 <= 0.0:
        raise ValueError("pressure power must be finite and positive")
    return 10.0 * math.log10(power_pa2 / (REFERENCE_PRESSURE_PA**2))


def load_rpm(
    path: Path,
    start_seconds: float,
    duration_seconds: float,
) -> tuple[float, float, int]:
    values = []
    previous_time = -math.inf
    with path.open("r", encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != ["time_s", "rpm"]:
            raise ValueError(f"{path}: RPM CSV header must be time_s,rpm")
        for line_number, row in enumerate(reader, start=2):
            try:
                time_seconds = float(row["time_s"])
                rpm = float(row["rpm"])
            except (TypeError, ValueError) as error:
                raise ValueError(f"{path}:{line_number}: invalid RPM sample") from error
            if not math.isfinite(time_seconds) or not math.isfinite(rpm) or rpm <= 0.0:
                raise ValueError(f"{path}:{line_number}: RPM samples must be finite and positive")
            if time_seconds <= previous_time:
                raise ValueError(
                    f"{path}:{line_number}: RPM timestamps must be strictly increasing"
                )
            previous_time = time_seconds
            if start_seconds <= time_seconds <= start_seconds + duration_seconds:
                values.append(rpm)
    if len(values) < 5:
        raise ValueError(f"{path}: at least five RPM samples are required in the segment")
    array = np.asarray(values, dtype=np.float64)
    mean = float(np.mean(array))
    coefficient_of_variation = float(np.std(array) / mean)
    return float(np.median(array)), coefficient_of_variation, len(values)


def tone_masks(
    frequencies: np.ndarray,
    rpm: float,
    blade_count: int,
    motor_pole_pairs: int,
    blade_pass_harmonics: int,
    half_width_hz: float,
    sample_rate: int,
) -> tuple[np.ndarray, np.ndarray, list[float], list[float]]:
    shaft_hz = rpm / 60.0
    rotor_centers = [
        shaft_hz * blade_count * harmonic
        for harmonic in range(1, blade_pass_harmonics + 1)
        if shaft_hz * blade_count * harmonic < sample_rate * NYQUIST_GUARD_RATIO
    ]
    motor_centers = [
        shaft_hz,
        shaft_hz * motor_pole_pairs,
        shaft_hz * motor_pole_pairs * 2.0,
    ]
    motor_centers = [
        center
        for center in motor_centers
        if center < sample_rate * NYQUIST_GUARD_RATIO
    ]
    labeled_centers = [
        *(("rotor", center) for center in rotor_centers),
        *(("motor", center) for center in motor_centers),
    ]
    for index, (left_kind, left_center) in enumerate(labeled_centers):
        for right_kind, right_center in labeled_centers[index + 1 :]:
            if abs(left_center - right_center) <= 2.0 * half_width_hz:
                raise ValueError(
                    f"{left_kind} and {right_kind} tonal windows overlap; "
                    "reduce the window or use a component-aware spectral estimator"
                )

    def combined(centers: list[float]) -> np.ndarray:
        mask = np.zeros(frequencies.shape, dtype=bool)
        for center in centers:
            mask |= np.abs(frequencies - center) <= half_width_hz
        return mask

    return (
        combined(rotor_centers),
        combined(motor_centers),
        rotor_centers,
        motor_centers,
    )


def tone_detail(
    frequencies: np.ndarray,
    signal_psd: np.ndarray,
    background_psd: np.ndarray,
    residual_psd: np.ndarray,
    all_tone_mask: np.ndarray,
    center_hz: float,
    half_width_hz: float,
    minimum_prominence_db: float,
    correction_db: float,
    kind: str,
    order: int,
) -> tuple[dict[str, Any], float]:
    distance = np.abs(frequencies - center_hz)
    tone = distance <= half_width_hz
    sideband = (
        (distance > 1.5 * half_width_hz)
        & (distance <= 3.5 * half_width_hz)
        & ~all_tone_mask
        & (frequencies >= 20.0)
    )
    if np.count_nonzero(sideband) < 4:
        raise ValueError(
            f"not enough clean sideband bins around {kind} order {order}"
        )
    signal_power = integrate_psd_allow_zero(
        frequencies, signal_psd, tone
    )
    background_power = integrate_psd_allow_zero(
        frequencies, background_psd, tone
    )
    residual_window_power = integrate_psd_allow_zero(
        frequencies, residual_psd, tone
    )
    bin_width = frequencies[1] - frequencies[0]
    local_density = float(np.median(residual_psd[sideband]))
    local_floor_power = (
        local_density * np.count_nonzero(tone) * bin_width
    )
    isolated_power = max(0.0, residual_window_power - local_floor_power)
    tiny = np.finfo(np.float64).tiny
    background_snr_db = 10.0 * math.log10(
        max(signal_power, tiny) / max(background_power, tiny)
    )
    prominence_db = 10.0 * math.log10(
        max(residual_window_power, tiny) / max(local_floor_power, tiny)
    )
    detected = (
        isolated_power > 0.0
        and prominence_db >= minimum_prominence_db
    )
    isolated_level = (
        level_db(isolated_power) + correction_db
        if detected
        else None
    )
    local_floor_level = (
        level_db(local_floor_power) + correction_db
        if local_floor_power > 0.0
        else None
    )
    return {
        "kind": kind,
        "order": order,
        "center_hz": center_hz,
        "detected": detected,
        "background_snr_db": background_snr_db,
        "local_prominence_db": prominence_db,
        "local_floor_level_db_1m": local_floor_level,
        "isolated_level_db_1m": isolated_level,
    }, isolated_power if detected else 0.0


def analyze_recording(
    recording: dict[str, Any],
    path: str,
    base: Path,
    calibration_pa_per_normalized: float,
    settings: dict[str, float],
    hash_cache: dict[Path, str],
) -> tuple[Descriptor, dict[str, Any]]:
    exact_fields(
        recording,
        RECORDING_FIELDS,
        path,
    )
    recording_id = text(recording["recording_id"], path + ".recording_id")
    maneuver_id = text(recording["maneuver_id"], path + ".maneuver_id")
    split = text(recording["split"], path + ".split").lower()
    if split not in {"train", "validation"}:
        raise ValueError(f"{path}.split must be train or validation")
    wav_path = resolve_input(base, recording["wav"], path + ".wav")
    background_path = resolve_input(
        base, recording["background_wav"], path + ".background_wav"
    )
    rpm_path = resolve_input(base, recording["rpm_csv"], path + ".rpm_csv")
    channel = integer(recording["channel"], 0, 63, path + ".channel")
    start = finite(recording["start_s"], path + ".start_s")
    duration = finite(recording["duration_s"], path + ".duration_s")
    background_start = finite(
        recording["background_start_s"], path + ".background_start_s"
    )
    background_duration = finite(
        recording["background_duration_s"], path + ".background_duration_s"
    )
    elevation = finite(recording["elevation_deg"], path + ".elevation_deg")
    if not -90.0 <= elevation <= 90.0:
        raise ValueError(f"{path}.elevation_deg must be in [-90, 90]")
    distance = finite(
        recording["microphone_distance_m"], path + ".microphone_distance_m"
    )
    if distance <= 0.0 or distance > 1000.0:
        raise ValueError(f"{path}.microphone_distance_m must be in (0, 1000]")
    level_correction = finite(
        recording["reference_level_correction_db"],
        path + ".reference_level_correction_db",
    )
    if not -60.0 <= level_correction <= 60.0:
        raise ValueError(f"{path}.reference_level_correction_db must be in [-60, 60]")
    blade_count = integer(recording["blade_count"], 1, 16, path + ".blade_count")
    pole_pairs = integer(
        recording["motor_pole_pairs"], 1, 64, path + ".motor_pole_pairs"
    )
    harmonics = integer(
        recording["blade_pass_harmonics"],
        1,
        64,
        path + ".blade_pass_harmonics",
    )
    airframe_id = text(recording["airframe_id"], path + ".airframe_id")
    source_configuration = text(
        recording["source_configuration"],
        path + ".source_configuration",
    ).lower()
    if source_configuration not in {
        "single_rotor_bench",
        "full_airframe_bench",
        "free_flight",
    }:
        raise ValueError(
            f"{path}.source_configuration must be single_rotor_bench, "
            "full_airframe_bench, or free_flight"
        )
    motor_id = text(recording["motor_id"], path + ".motor_id")
    propeller_id = text(recording["propeller_id"], path + ".propeller_id")
    microphone_id = text(recording["microphone_id"], path + ".microphone_id")
    signal_chain_id = text(
        recording["signal_chain_id"], path + ".signal_chain_id"
    )
    thrust_n = finite(recording["thrust_n"], path + ".thrust_n")
    voltage_v = finite(recording["voltage_v"], path + ".voltage_v")
    current_a = finite(recording["current_a"], path + ".current_a")
    temperature_c = finite(
        recording["ambient_temperature_c"], path + ".ambient_temperature_c"
    )
    humidity_percent = finite(
        recording["relative_humidity_percent"],
        path + ".relative_humidity_percent",
    )
    pressure_kpa = finite(
        recording["ambient_pressure_kpa"], path + ".ambient_pressure_kpa"
    )
    if thrust_n < 0.0 or voltage_v < 0.0 or current_a < 0.0:
        raise ValueError(f"{path}: thrust, voltage and current must be non-negative")
    if not -80.0 <= temperature_c <= 80.0:
        raise ValueError(f"{path}.ambient_temperature_c must be in [-80, 80]")
    if not 0.0 <= humidity_percent <= 100.0:
        raise ValueError(f"{path}.relative_humidity_percent must be in [0, 100]")
    if not 20.0 <= pressure_kpa <= 120.0:
        raise ValueError(f"{path}.ambient_pressure_kpa must be in [20, 120]")

    sample_rate, signal, wav_info = read_wav_segment(
        wav_path, channel, start, duration
    )
    background_rate, background, background_info = read_wav_segment(
        background_path,
        channel,
        background_start,
        background_duration,
    )
    if background_rate != sample_rate:
        raise ValueError(f"{path}: signal and background sample rates differ")
    signal_peak = float(np.max(np.abs(signal)))
    background_peak = float(np.max(np.abs(background)))
    if signal_peak > settings["maximum_peak_normalized"]:
        raise ValueError(
            f"{path}: signal peak {signal_peak:.6f} exceeds "
            f"{settings['maximum_peak_normalized']:.6f}"
        )
    if background_peak > settings["maximum_peak_normalized"]:
        raise ValueError(
            f"{path}: background peak {background_peak:.6f} exceeds "
            f"{settings['maximum_peak_normalized']:.6f}"
        )
    frequencies, signal_psd_normalized, signal_frames = welch_psd(
        signal,
        sample_rate,
        settings["welch_resolution_hz"],
        settings["overlap_fraction"],
    )
    background_frequencies, background_psd_normalized, background_frames = welch_psd(
        background,
        sample_rate,
        settings["welch_resolution_hz"],
        settings["overlap_fraction"],
    )
    if not np.array_equal(frequencies, background_frequencies):
        raise ValueError(f"{path}: signal/background PSD frequency grids differ")
    scale_squared = calibration_pa_per_normalized**2
    signal_psd = signal_psd_normalized * scale_squared
    background_psd = background_psd_normalized * scale_squared
    audible = (frequencies >= 20.0) & (
        frequencies < min(20_000.0, sample_rate / 2.0)
    )
    signal_power = integrate_psd(frequencies, signal_psd, audible)
    try:
        background_power = integrate_psd(frequencies, background_psd, audible)
    except ValueError:
        background_power = np.finfo(np.float64).tiny
    snr_db = 10.0 * math.log10(signal_power / background_power)
    if snr_db < settings["minimum_snr_db"]:
        raise ValueError(
            f"{path}: broadband SNR {snr_db:.3f} dB is below "
            f"{settings['minimum_snr_db']:.3f} dB"
        )
    residual_psd = np.maximum(signal_psd - background_psd, 0.0)

    rpm, rpm_cv, rpm_samples = load_rpm(rpm_path, start, duration)
    if rpm_cv > settings["maximum_rpm_cv"]:
        raise ValueError(
            f"{path}: RPM coefficient of variation {rpm_cv:.6f} exceeds "
            f"{settings['maximum_rpm_cv']:.6f}"
        )
    rotor_mask, motor_mask, rotor_centers, motor_centers = tone_masks(
        frequencies,
        rpm,
        blade_count,
        pole_pairs,
        harmonics,
        settings["tonal_half_width_hz"],
        sample_rate,
    )
    all_tones = rotor_mask | motor_mask
    correction_db = 20.0 * math.log10(distance) + level_correction
    rotor_tones = []
    rotor_powers = []
    for harmonic, center in enumerate(rotor_centers, start=1):
        detail, power = tone_detail(
            frequencies,
            signal_psd,
            background_psd,
            residual_psd,
            all_tones,
            center,
            settings["tonal_half_width_hz"],
            settings["minimum_tone_prominence_db"],
            correction_db,
            "blade_pass",
            harmonic,
        )
        rotor_tones.append(detail)
        rotor_powers.append(power)
    motor_kinds = ("shaft", "electrical", "twice_electrical_candidate")
    motor_tones = []
    motor_powers = []
    for order, (kind, center) in enumerate(
        zip(motor_kinds, motor_centers),
        start=1,
    ):
        detail, power = tone_detail(
            frequencies,
            signal_psd,
            background_psd,
            residual_psd,
            all_tones,
            center,
            settings["tonal_half_width_hz"],
            settings["minimum_tone_prominence_db"],
            correction_db,
            kind,
            order,
        )
        motor_tones.append(detail)
        motor_powers.append(power)
    rotor_power = sum(rotor_powers)
    motor_power = sum(motor_powers)
    if rotor_power <= 0.0:
        raise ValueError(f"{path}: no blade-pass order passed prominence gate")
    if motor_power <= 0.0:
        raise ValueError(f"{path}: no motor order passed prominence gate")
    broadband_levels = {}
    for name, (minimum_hz, maximum_hz) in BANDS_HZ.items():
        selected = (
            (frequencies >= minimum_hz)
            & (frequencies < min(maximum_hz, sample_rate / 2.0))
            & ~all_tones
        )
        broadband_levels[name] = level_db(
            integrate_psd(frequencies, residual_psd, selected)
        ) + correction_db

    descriptor = Descriptor(
        recording_id=recording_id,
        maneuver_id=maneuver_id,
        split=split,
        rpm=rpm,
        elevation_deg=elevation,
        rotor_tonal_db=level_db(rotor_power) + correction_db,
        motor_tonal_db=level_db(motor_power) + correction_db,
        **broadband_levels,
    )
    for file_path in (wav_path, background_path, rpm_path):
        hash_cache.setdefault(file_path, sha256_file(file_path))
    detail = {
        "recording_id": recording_id,
        "maneuver_id": maneuver_id,
        "split": split,
        "sample_rate_hz": sample_rate,
        "signal_welch_frames": signal_frames,
        "background_welch_frames": background_frames,
        "rpm": rpm,
        "rpm_samples": rpm_samples,
        "rpm_coefficient_of_variation": rpm_cv,
        "snr_db": snr_db,
        "distance_normalization_db": 20.0 * math.log10(distance),
        "reference_level_correction_db": level_correction,
        "airframe_id": airframe_id,
        "source_configuration": source_configuration,
        "motor_id": motor_id,
        "propeller_id": propeller_id,
        "microphone_id": microphone_id,
        "signal_chain_id": signal_chain_id,
        "thrust_n": thrust_n,
        "voltage_v": voltage_v,
        "current_a": current_a,
        "ambient_temperature_c": temperature_c,
        "relative_humidity_percent": humidity_percent,
        "ambient_pressure_kpa": pressure_kpa,
        "signal_peak_normalized": signal_peak,
        "background_peak_normalized": background_peak,
        "rotor_tone_centers_hz": rotor_centers,
        "motor_tone_centers_hz": motor_centers,
        "rotor_tones": rotor_tones,
        "motor_tones": motor_tones,
        "wav": str(wav_path),
        "wav_sha256": hash_cache[wav_path],
        "background_wav": str(background_path),
        "background_wav_sha256": hash_cache[background_path],
        "rpm_csv": str(rpm_path),
        "rpm_csv_sha256": hash_cache[rpm_path],
        "wav_format": wav_info,
        "background_wav_format": background_info,
        "descriptor": asdict(descriptor),
    }
    return descriptor, detail


def calibration_scale(
    calibration: dict[str, Any],
    base: Path,
    settings: dict[str, float],
) -> tuple[float, dict[str, Any], Path]:
    exact_fields(
        calibration,
        (
            "wav",
            "channel",
            "level_db_spl",
            "frequency_hz",
            "start_s",
            "duration_s",
            "tone_half_width_hz",
        ),
        "$.calibration",
    )
    wav_path = resolve_input(base, calibration["wav"], "$.calibration.wav")
    channel = integer(calibration["channel"], 0, 63, "$.calibration.channel")
    known_level = finite(
        calibration["level_db_spl"], "$.calibration.level_db_spl"
    )
    frequency = finite(
        calibration["frequency_hz"], "$.calibration.frequency_hz"
    )
    start = finite(calibration["start_s"], "$.calibration.start_s")
    duration = finite(calibration["duration_s"], "$.calibration.duration_s")
    half_width = finite(
        calibration["tone_half_width_hz"],
        "$.calibration.tone_half_width_hz",
    )
    if frequency <= 0.0 or half_width <= 0.0:
        raise ValueError("$.calibration frequency and tone width must be positive")
    sample_rate, samples, wav_info = read_wav_segment(
        wav_path, channel, start, duration
    )
    peak = float(np.max(np.abs(samples)))
    if peak > settings["maximum_peak_normalized"]:
        raise ValueError(
            f"$.calibration: peak {peak:.6f} exceeds "
            f"{settings['maximum_peak_normalized']:.6f}"
        )
    frequencies, density, frames = welch_psd(
        samples,
        sample_rate,
        settings["welch_resolution_hz"],
        settings["overlap_fraction"],
    )
    selected = np.abs(frequencies - frequency) <= half_width
    normalized_tone_power = integrate_psd(frequencies, density, selected)
    known_pressure_rms = REFERENCE_PRESSURE_PA * 10.0 ** (known_level / 20.0)
    scale = known_pressure_rms / math.sqrt(normalized_tone_power)
    if not math.isfinite(scale) or scale <= 0.0:
        raise ValueError("calibration produced an invalid Pa/normalized scale")
    return scale, {
        "wav": str(wav_path),
        "wav_sha256": sha256_file(wav_path),
        "sample_rate_hz": sample_rate,
        "welch_frames": frames,
        "known_level_db_spl": known_level,
        "frequency_hz": frequency,
        "normalized_tone_rms": math.sqrt(normalized_tone_power),
        "peak_normalized": peak,
        "pascals_per_normalized_sample": scale,
        "wav_format": wav_info,
    }, wav_path


def parse_settings(value: dict[str, Any]) -> dict[str, float]:
    exact_fields(
        value,
        (
            "welch_resolution_hz",
            "overlap_fraction",
            "tonal_half_width_hz",
            "maximum_rpm_cv",
            "minimum_snr_db",
            "maximum_peak_normalized",
            "minimum_tone_prominence_db",
        ),
        "$.analysis",
    )
    result = {
        name: finite(item, f"$.analysis.{name}")
        for name, item in value.items()
    }
    if result["welch_resolution_hz"] <= 0.0:
        raise ValueError("$.analysis.welch_resolution_hz must be positive")
    if not 0.0 <= result["overlap_fraction"] < 1.0:
        raise ValueError("$.analysis.overlap_fraction must be in [0, 1)")
    if result["tonal_half_width_hz"] <= 0.0:
        raise ValueError("$.analysis.tonal_half_width_hz must be positive")
    if not 0.0 <= result["maximum_rpm_cv"] <= 0.25:
        raise ValueError("$.analysis.maximum_rpm_cv must be in [0, 0.25]")
    if not -20.0 <= result["minimum_snr_db"] <= 80.0:
        raise ValueError("$.analysis.minimum_snr_db must be in [-20, 80]")
    if not 0.0 < result["maximum_peak_normalized"] <= 1.0:
        raise ValueError("$.analysis.maximum_peak_normalized must be in (0, 1]")
    if not 0.0 <= result["minimum_tone_prominence_db"] <= 40.0:
        raise ValueError(
            "$.analysis.minimum_tone_prominence_db must be in [0, 40]"
        )
    return result


def descriptor_csv_bytes(descriptors: list[Descriptor]) -> bytes:
    output = io.StringIO(newline="")
    writer = csv.DictWriter(
        output,
        fieldnames=DESCRIPTOR_FIELDS,
        lineterminator="\n",
    )
    writer.writeheader()
    for descriptor in descriptors:
        row = asdict(descriptor)
        for name in DESCRIPTOR_FIELDS[3:]:
            row[name] = format(float(row[name]), ".9f").rstrip("0").rstrip(".")
        writer.writerow(row)
    return output.getvalue().encode("utf-8")


def json_bytes(value: Any) -> bytes:
    return (
        json.dumps(
            value,
            ensure_ascii=False,
            allow_nan=False,
            sort_keys=True,
            indent=2,
        )
        + "\n"
    ).encode("utf-8")


def write_atomic(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=path.name + ".",
        suffix=".tmp",
        dir=path.parent,
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


def run(manifest_path: Path, output_csv: Path, output_report: Path) -> int:
    manifest_path = manifest_path.resolve()
    manifest = strict_json_load(manifest_path)
    exact_fields(
        manifest,
        ("schema_version", "calibration", "analysis", "recordings"),
        "$",
    )
    if isinstance(manifest["schema_version"], bool) or manifest["schema_version"] != 1:
        raise ValueError("$.schema_version: only schema 1 is supported")
    if not isinstance(manifest["calibration"], dict):
        raise ValueError("$.calibration: expected an object")
    if not isinstance(manifest["analysis"], dict):
        raise ValueError("$.analysis: expected an object")
    if not isinstance(manifest["recordings"], list) or not manifest["recordings"]:
        raise ValueError("$.recordings: expected a non-empty array")
    settings = parse_settings(manifest["analysis"])
    scale, calibration_report, calibration_path = calibration_scale(
        manifest["calibration"],
        manifest_path.parent,
        settings,
    )
    descriptors = []
    details = []
    hash_cache = {calibration_path: calibration_report["wav_sha256"]}
    for index, recording in enumerate(manifest["recordings"]):
        if not isinstance(recording, dict):
            raise ValueError(f"$.recordings[{index}]: expected an object")
        descriptor, detail = analyze_recording(
            recording,
            f"$.recordings[{index}]",
            manifest_path.parent,
            scale,
            settings,
            hash_cache,
        )
        descriptors.append(descriptor)
        details.append(detail)
    csv_data = descriptor_csv_bytes(descriptors)
    report = {
        "schema_version": 1,
        "manifest": str(manifest_path),
        "manifest_sha256": sha256_file(manifest_path),
        "analysis": settings,
        "calibration": calibration_report,
        "recordings": details,
        "descriptor_rows": len(descriptors),
        "descriptor_csv_sha256": hashlib.sha256(csv_data).hexdigest(),
    }
    write_atomic(output_csv, csv_data)
    write_atomic(output_report, json_bytes(report))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Extract calibrated acoustic descriptors from steady recordings."
    )
    parser.add_argument("--manifest-json", type=Path, required=True)
    parser.add_argument("--output-csv", type=Path, required=True)
    parser.add_argument("--output-report-json", type=Path, required=True)
    args = parser.parse_args()
    try:
        return run(args.manifest_json, args.output_csv, args.output_report_json)
    except (OSError, ValueError, wave.Error, json.JSONDecodeError) as error:
        parser.error(str(error))
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
