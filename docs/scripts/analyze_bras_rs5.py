#!/usr/bin/env python3
"""Extract a reproducible first-arrival diffraction reference from BRAS RS5.

The script intentionally uses only the Python standard library. It reads the
mono IEEE-float WAV exports included with BRAS, detects the first arrival, and
compares the shadowed LS1/MP1 response with the visible LS3/MP4 response.

The short 1 ms gate ends before the first floor reflection predicted for the
shadowed geometry. The 4 ms gate is reported as a sensitivity check, not as a
pure edge-diffraction measurement.

RS5 distributes already-deconvolved RIRs but not the excitation sweep or
inverse filter. The all-path analysis therefore learns a two-sided energy
template from visible direct paths, matches it near the geometry-predicted
arrival, and uses a symmetric gate that retains inverse-filter pre-ringing.
This is deconvolution-aware timing/gating, not a claim that the original
measurement can be deconvolved again.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import statistics
import struct
import sys
from pathlib import Path
from typing import Iterable


EXPECTED_ARCHIVE_SHA256 = (
    "b9cb03c945fcf46bf742135108d1acc5246b576e8afb39090547e0f3093f58b5"
)
SPEED_OF_SOUND_M_S = 343.0
FREQUENCIES_HZ = (1000.0, 2000.0, 4000.0, 8000.0, 12000.0)
UDFA_PARAMETERS = {
    "fractional_order_alpha": 0.5,
    "rolloff_shape_b": 1.44,
    "quality_q": 0.2,
    "transition_shape_r": 1.6,
}

# Coordinates transcribed from BRAS RS5_RIR.png, converted from mm to m.
SOURCE_VISIBLE = (2.487, 2.985, 3.000)  # LS3
RECEIVER_VISIBLE = (8.512, 2.985, 3.000)  # MP4
SOURCE_SHADOW = (2.487, 2.985, 1.235)  # LS1
RECEIVER_SHADOW = (8.512, 2.985, 1.235)  # MP1
EDGE_POINT = (5.487, 2.985, 2.066)  # front/top edge of partition
SECOND_EDGE_POINT = (5.512, 2.985, 2.066)
RS5_SOURCES = {
    "LS1": (2.487, 2.985, 1.235),
    "LS2": (2.487, 2.985, 2.000),
    "LS3": (2.487, 2.985, 3.000),
    "LS4": (2.487, 2.985, 0.135),
}
RS5_RECEIVERS = {
    "MP1": (8.512, 2.985, 1.235),
    "MP2": (8.512, 2.985, 0.006),
    "MP3": (8.512, 2.985, 2.000),
    "MP4": (8.512, 2.985, 3.000),
}
RS5_SOURCE_TILT_DEGREES = {
    "LS1": 0.0,
    "LS2": 0.0,
    "LS3": 0.0,
    "LS4": 34.6,
}
GENELEC_MPS_SHA256 = (
    "b368fa00951ac92f942ef13244d4f1039e8310f4931baa2765085d8a22cdd45d"
)
AIR_DENSITY_KG_M3 = 1.204
MDF25B_SURFACE_MASS_KG_M2 = 18.56
THIRD_OCTAVE_FREQUENCIES_HZ = (
    20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0,
    200.0, 250.0, 315.0, 400.0, 500.0, 630.0, 800.0, 1000.0,
    1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0, 5000.0,
    6300.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0,
)
MDF25B_ABSORPTION = (
    0.010, 0.010, 0.010, 0.010, 0.010, 0.010, 0.010, 0.018,
    0.022, 0.023, 0.022, 0.021, 0.024, 0.026, 0.028, 0.030,
    0.032, 0.033, 0.029, 0.032, 0.031, 0.049, 0.062, 0.054,
    0.037, 0.038, 0.038, 0.039, 0.038, 0.038, 0.038,
)
TILE_ABSORPTION = (
    0.002, 0.002, 0.002, 0.003, 0.005, 0.005, 0.006, 0.008,
    0.010, 0.011, 0.010, 0.011, 0.014, 0.013, 0.020, 0.021,
    0.022, 0.021, 0.024, 0.023, 0.030, 0.030, 0.026, 0.023,
    0.029, 0.032, 0.030, 0.031, 0.029, 0.031, 0.037,
)
TILE_SCATTERING = (
    0.010, 0.010, 0.010, 0.010, 0.010, 0.010, 0.010, 0.010,
    0.010, 0.010, 0.013, 0.015, 0.018, 0.020, 0.022, 0.025,
    0.028, 0.030, 0.035, 0.040, 0.045, 0.050, 0.055, 0.060,
    0.120, 0.240, 0.295, 0.350, 0.355, 0.360, 0.380,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Analyze the BRAS RS5 infinite-wedge first arrivals."
    )
    parser.add_argument(
        "dataset_root",
        type=Path,
        help=(
            "Extracted RS5 directory, normally the directory named "
            "'RS5 diffraction (infinite wedge)'"
        ),
    )
    parser.add_argument(
        "--archive",
        type=Path,
        help="Optional original RS5 ZIP; its SHA-256 is verified when supplied.",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emit machine-readable JSON instead of a compact text report.",
    )
    parser.add_argument(
        "--genelec-mps",
        type=Path,
        help=(
            "Optional official Genelec 8020c MPS MAT file. When supplied, "
            "all-path spectra are corrected for measured source directivity. "
            "This optional path requires scipy."
        ),
    )
    return parser.parse_args()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_genelec_mps(path: Path) -> dict[str, object]:
    file_hash = sha256(path)
    if file_hash.lower() != GENELEC_MPS_SHA256:
        raise ValueError(
            f"{path}: SHA-256 {file_hash} does not match the documented "
            "official Genelec 8020c MPS entry"
        )
    try:
        from scipy.io import loadmat
    except ImportError as error:
        raise ValueError(
            "--genelec-mps requires scipy (scipy.io.loadmat)"
        ) from error
    data = loadmat(path)
    required = {"MPS", "Phi", "Theta", "Frequency"}
    if not required.issubset(data):
        raise ValueError(f"{path}: missing MAT variables {sorted(required)}")
    mps = data["MPS"]
    phi = data["Phi"].reshape(-1)
    theta = data["Theta"].reshape(-1)
    frequencies = data["Frequency"].reshape(-1)
    if mps.shape != (len(frequencies), len(phi)) or len(theta) != len(phi):
        raise ValueError(f"{path}: unexpected Genelec directivity dimensions")
    direction_index = {
        (int(phi[index]), int(theta[index])): index
        for index in range(len(phi))
    }
    if (0, 0) not in direction_index:
        raise ValueError(f"{path}: Genelec profile has no on-axis sample")
    return {
        "mps": mps,
        "frequencies": frequencies,
        "direction_index": direction_index,
        "sha256": file_hash,
    }


def genelec_relative_directivity_db(
    profile: dict[str, object],
    frequency_hz: float,
    phi_degrees: float,
    theta_degrees: float,
) -> float:
    """Interpolate magnitude relative to the measured on-axis response."""

    frequencies = profile["frequencies"]
    mps = profile["mps"]
    direction_index = profile["direction_index"]
    phi = int(round(phi_degrees)) % 360
    theta = min(180, max(0, int(round(theta_degrees))))
    if theta in {0, 180}:
        phi = 0
    target_index = direction_index.get((phi, theta))
    if target_index is None:
        raise ValueError(
            f"Genelec profile has no direction Phi={phi}, Theta={theta}"
        )
    on_axis_index = direction_index[(0, 0)]

    def relative_db(frequency_index: int) -> float:
        target = abs(mps[frequency_index, target_index])
        on_axis = abs(mps[frequency_index, on_axis_index])
        if target <= 0.0 or on_axis <= 0.0:
            raise ValueError("Genelec MPS contains zero magnitude")
        return 20.0 * math.log10(target / on_axis)

    if frequency_hz <= frequencies[0]:
        return relative_db(0)
    if frequency_hz >= frequencies[-1]:
        return relative_db(len(frequencies) - 1)
    upper = next(
        index
        for index, value in enumerate(frequencies)
        if value >= frequency_hz
    )
    if frequencies[upper] == frequency_hz:
        return relative_db(upper)
    lower = upper - 1
    fraction = math.log(frequency_hz / frequencies[lower]) / math.log(
        frequencies[upper] / frequencies[lower]
    )
    return (
        relative_db(lower) * (1.0 - fraction)
        + relative_db(upper) * fraction
    )


def genelec_relative_directivity_complex(
    profile: dict[str, object],
    frequency_hz: float,
    phi_degrees: float,
    theta_degrees: float,
) -> complex:
    """Log-frequency interpolate the complex off-axis/on-axis ratio."""

    frequencies = profile["frequencies"]
    mps = profile["mps"]
    direction_index = profile["direction_index"]
    phi = int(round(phi_degrees)) % 360
    theta = min(180, max(0, int(round(theta_degrees))))
    if theta in {0, 180}:
        phi = 0
    target_index = direction_index.get((phi, theta))
    if target_index is None:
        raise ValueError(
            f"Genelec profile has no direction Phi={phi}, Theta={theta}"
        )
    on_axis_index = direction_index[(0, 0)]

    def ratio(index: int) -> complex:
        on_axis = complex(mps[index, on_axis_index])
        if abs(on_axis) <= 0.0:
            raise ValueError("Genelec MPS contains zero on-axis magnitude")
        return complex(mps[index, target_index]) / on_axis

    if frequency_hz <= frequencies[0]:
        return ratio(0)
    if frequency_hz >= frequencies[-1]:
        return ratio(len(frequencies) - 1)
    upper = next(
        index
        for index, value in enumerate(frequencies)
        if value >= frequency_hz
    )
    if frequencies[upper] == frequency_hz:
        return ratio(upper)
    lower = upper - 1
    fraction = math.log(frequency_hz / frequencies[lower]) / math.log(
        frequencies[upper] / frequencies[lower]
    )
    first = ratio(lower)
    second = ratio(upper)
    log_magnitude = (
        math.log(abs(first)) * (1.0 - fraction)
        + math.log(abs(second)) * fraction
    )
    first_phase = math.atan2(first.imag, first.real)
    second_phase = math.atan2(second.imag, second.real)
    phase_delta = math.atan2(
        math.sin(second_phase - first_phase),
        math.cos(second_phase - first_phase),
    )
    phase = first_phase + fraction * phase_delta
    magnitude = math.exp(log_magnitude)
    return complex(magnitude * math.cos(phase), magnitude * math.sin(phase))


def log_frequency_interpolate(
    frequency_hz: float,
    values: tuple[float, ...],
) -> float:
    if len(values) != len(THIRD_OCTAVE_FREQUENCIES_HZ):
        raise ValueError("third-octave value count mismatch")
    if frequency_hz <= THIRD_OCTAVE_FREQUENCIES_HZ[0]:
        return values[0]
    if frequency_hz >= THIRD_OCTAVE_FREQUENCIES_HZ[-1]:
        return values[-1]
    upper = next(
        index
        for index, value in enumerate(THIRD_OCTAVE_FREQUENCIES_HZ)
        if value >= frequency_hz
    )
    if THIRD_OCTAVE_FREQUENCIES_HZ[upper] == frequency_hz:
        return values[upper]
    lower = upper - 1
    fraction = math.log(
        frequency_hz / THIRD_OCTAVE_FREQUENCIES_HZ[lower]
    ) / math.log(
        THIRD_OCTAVE_FREQUENCIES_HZ[upper]
        / THIRD_OCTAVE_FREQUENCIES_HZ[lower]
    )
    return values[lower] * (1.0 - fraction) + values[upper] * fraction


def limp_sheet_transmission_db(frequency_hz: float) -> float:
    """Normal-incidence pressure transmission of an ideal limp mass sheet."""

    reactance_ratio = (
        math.pi
        * frequency_hz
        * MDF25B_SURFACE_MASS_KG_M2
        / (AIR_DENSITY_KG_M3 * SPEED_OF_SOUND_M_S)
    )
    return -10.0 * math.log10(1.0 + reactance_ratio * reactance_ratio)


def first_floor_double_edge_path_m(
    source: tuple[float, float, float],
    receiver: tuple[float, float, float],
    first_edge: tuple[float, float, float],
    second_edge: tuple[float, float, float],
) -> float:
    """Shortest one-floor-bounce path that also clears both screen edges."""

    source_image = (source[0], source[1], -source[2])
    receiver_image = (receiver[0], receiver[1], -receiver[2])
    return min(
        distance(source_image, first_edge)
        + distance(first_edge, second_edge)
        + distance(second_edge, receiver),
        distance(source, first_edge)
        + distance(first_edge, second_edge)
        + distance(second_edge, receiver_image),
    )


def read_float_wav(path: Path) -> tuple[int, list[float]]:
    raw = path.read_bytes()
    if raw[:4] != b"RIFF" or raw[8:12] != b"WAVE":
        raise ValueError(f"{path}: not a little-endian RIFF/WAVE file")

    fmt: bytes | None = None
    data: bytes | None = None
    offset = 12
    while offset + 8 <= len(raw):
        chunk_id = raw[offset : offset + 4]
        chunk_size = struct.unpack_from("<I", raw, offset + 4)[0]
        chunk = raw[offset + 8 : offset + 8 + chunk_size]
        if len(chunk) != chunk_size:
            raise ValueError(f"{path}: truncated {chunk_id!r} chunk")
        if chunk_id == b"fmt ":
            fmt = chunk
        elif chunk_id == b"data":
            data = chunk
        offset += 8 + chunk_size + (chunk_size & 1)

    if fmt is None or data is None or len(fmt) < 16:
        raise ValueError(f"{path}: missing fmt or data chunk")
    audio_format, channels, sample_rate, _, block_align, bits = struct.unpack_from(
        "<HHIIHH", fmt
    )
    if (audio_format, channels, bits, block_align) != (3, 1, 32, 4):
        raise ValueError(
            f"{path}: expected mono PCM_F32LE, got format={audio_format}, "
            f"channels={channels}, bits={bits}, block_align={block_align}"
        )
    if len(data) % 4:
        raise ValueError(f"{path}: float data is not sample aligned")
    return sample_rate, list(struct.unpack(f"<{len(data) // 4}f", data))


def first_arrival(samples: list[float]) -> tuple[int, float, float]:
    if len(samples) < 64:
        raise ValueError("RIR is too short")
    peak = max(abs(value) for value in samples)
    noise_count = min(256, len(samples) // 8)
    noise_rms = math.sqrt(
        sum(value * value for value in samples[:noise_count]) / noise_count
    )
    threshold = max(peak * 1.0e-4, noise_rms * 12.0)

    # An eight-sample energy detector avoids triggering on one corrupt sample
    # while remaining insensitive to the phase of the loudspeaker impulse.
    window = 8
    threshold_rms = threshold
    running = sum(value * value for value in samples[:window])
    for start in range(0, len(samples) - window):
        if math.sqrt(running / window) >= threshold_rms:
            return start, peak, noise_rms
        running -= samples[start] * samples[start]
        running += samples[start + window] * samples[start + window]
    raise ValueError("could not detect a first arrival")


def gated_magnitude(
    samples: list[float],
    sample_rate: int,
    onset: int,
    gate_seconds: float,
    frequency_hz: float,
) -> float:
    count = max(8, round(gate_seconds * sample_rate))
    segment = samples[onset : onset + count]
    if len(segment) != count:
        raise ValueError("RIR ends inside analysis gate")

    # Preserve the causal leading edge and fade only the last 25% to suppress
    # the artificial truncation discontinuity.
    fade_start = count * 3 // 4
    real = 0.0
    imaginary = 0.0
    for index, sample in enumerate(segment):
        if index < fade_start:
            weight = 1.0
        else:
            phase = (index - fade_start) / max(1, count - fade_start - 1)
            weight = 0.5 * (1.0 + math.cos(math.pi * phase))
        angle = -2.0 * math.pi * frequency_hz * index / sample_rate
        real += weight * sample * math.cos(angle)
        imaginary += weight * sample * math.sin(angle)
    return math.hypot(real, imaginary)


def full_rir_magnitude(
    samples: list[float],
    sample_rate: int,
    frequency_hz: float,
) -> float:
    """Magnitude of the complete published RIR with a 5% end taper."""

    fade_start = len(samples) * 95 // 100
    real = 0.0
    imaginary = 0.0
    for index, sample in enumerate(samples):
        if index < fade_start:
            weight = 1.0
        else:
            phase = (index - fade_start) / max(
                1, len(samples) - fade_start - 1
            )
            weight = 0.5 * (1.0 + math.cos(math.pi * phase))
        angle = -2.0 * math.pi * frequency_hz * index / sample_rate
        real += weight * sample * math.cos(angle)
        imaginary += weight * sample * math.sin(angle)
    return math.hypot(real, imaginary)


def normalized_energy_packet(
    samples: list[float],
    landmark: int,
    radius_samples: int,
) -> list[float]:
    """Return a unit-energy, lightly smoothed two-sided squared packet."""

    first = landmark - radius_samples
    last = landmark + radius_samples + 1
    if first < 2 or last + 2 > len(samples):
        raise ValueError("energy packet exceeds RIR")
    energy = []
    for index in range(first, last):
        local = sum(samples[index + offset] ** 2 for offset in (-2, -1, 0, 1, 2))
        energy.append(local / 5.0)
    mean = sum(energy) / len(energy)
    centered = [value - mean for value in energy]
    norm = math.sqrt(sum(value * value for value in centered))
    if norm <= 0.0:
        raise ValueError("zero-energy matched packet")
    return [value / norm for value in centered]


def median_energy_template(
    packets: list[list[float]],
) -> list[float]:
    if not packets or any(len(packet) != len(packets[0]) for packet in packets):
        raise ValueError("matched packets must be non-empty and equal length")
    template = [
        statistics.median(packet[index] for packet in packets)
        for index in range(len(packets[0]))
    ]
    mean = sum(template) / len(template)
    centered = [value - mean for value in template]
    norm = math.sqrt(sum(value * value for value in centered))
    if norm <= 0.0:
        raise ValueError("zero-energy direct-path template")
    return [value / norm for value in centered]


def matched_energy_landmark(
    samples: list[float],
    predicted_landmark: float,
    template: list[float],
    search_radius_samples: int,
) -> tuple[int, float]:
    """Match a learned deconvolution packet near a physical arrival prediction."""

    packet_radius = len(template) // 2
    center = round(predicted_landmark)
    best_landmark = center
    best_score = -math.inf
    for landmark in range(
        center - search_radius_samples,
        center + search_radius_samples + 1,
    ):
        packet = normalized_energy_packet(samples, landmark, packet_radius)
        score = sum(a * b for a, b in zip(template, packet))
        if score > best_score:
            best_score = score
            best_landmark = landmark
    return best_landmark, best_score


def distance(first: tuple[float, ...], second: tuple[float, ...]) -> float:
    return math.sqrt(sum((a - b) ** 2 for a, b in zip(first, second)))


def udfa_infinite_knife_edge_magnitude(
    frequency_hz: float,
    source_distance_m: float,
    receiver_distance_m: float,
    bending_angle_rad: float,
) -> float:
    """Port of the published 2023 UDFA equations (2)-(6).

    This is deliberately kept beside the data extraction rather than imported
    from game code, so the external-data oracle can detect implementation
    drift. The Java reference cross-checks these values in its own tests.
    """

    alpha = UDFA_PARAMETERS["fractional_order_alpha"]
    b = UDFA_PARAMETERS["rolloff_shape_b"]
    quality = UDFA_PARAMETERS["quality_q"]
    transition = UDFA_PARAMETERS["transition_shape_r"]
    wedge_index = 0.5  # pi / (2 pi), an ideal infinitely thin screen
    characteristic_distance = (
        2.0
        * source_distance_m
        * receiver_distance_m
        / (source_distance_m + receiver_distance_m)
    )

    def sign(value: float) -> float:
        return -1.0 if value < 0.0 else 1.0 if value > 0.0 else 0.0

    def term(theta: float) -> complex:
        numerator = math.sin(wedge_index * math.pi)
        radicand = 1.0 - math.cos(wedge_index * math.pi) * math.cos(
            wedge_index * theta
        )
        angular_gain = numerator / math.sqrt(max(radicand, 1.0e-12))
        cutoff_factor = (
            wedge_index
            * math.sqrt(max(0.0, radicand))
            / (
                math.cos(wedge_index * math.pi)
                - math.cos(wedge_index * theta)
            )
        )
        cutoff_hz = (
            2.0
            * SPEED_OF_SOUND_M_S
            * cutoff_factor
            * cutoff_factor
            / (math.pi * math.pi * characteristic_distance)
        )
        normalized = frequency_hz / cutoff_hz
        response = (
            (1j * normalized) ** (2.0 / b)
            + (1j * normalized / quality) ** (1.0 / (b * transition))
            + 1.0
        ) ** (-alpha * b / 2.0)
        return angular_gain * response

    source_azimuth = 0.0
    receiver_azimuth = math.pi + bending_angle_rad
    theta_plus = receiver_azimuth + source_azimuth
    theta_minus = receiver_azimuth - source_azimuth
    pressure = 0.5 * (
        sign(theta_plus - math.pi) * term(theta_plus)
        + sign(theta_minus - math.pi) * term(theta_minus)
    )
    return abs(pressure)


def udfa_2024_double_edge_magnitude(frequency_hz: float) -> float:
    """Evaluate equations (8)-(12) for the symmetric BRAS RS5 top edge.

    Edge 1 is the front face at x=5.487 m and edge 2 is 25 mm behind it.
    Both are square 3*pi/2 exterior wedges. The implementation is independent
    of the Java reference and intentionally uses no UDFA toolbox code.
    """

    return abs(
        udfa_2024_double_edge_complex(
            frequency_hz,
            SOURCE_SHADOW,
            RECEIVER_SHADOW,
        )
    )


def udfa_2024_double_edge_complex(
    frequency_hz: float,
    source: tuple[float, float, float],
    receiver: tuple[float, float, float],
) -> complex:
    """Complex 2024 UDFA response for arbitrary RS5 source/receiver heights."""

    alpha_order = UDFA_PARAMETERS["fractional_order_alpha"]
    rolloff = UDFA_PARAMETERS["rolloff_shape_b"]
    quality = UDFA_PARAMETERS["quality_q"]
    transition = UDFA_PARAMETERS["transition_shape_r"]
    first_edge_x = 5.487
    second_edge_x = 5.512
    source_x = source[0]
    receiver_x = receiver[0]
    source_vertical = 2.066 - source[2]
    receiver_vertical = 2.066 - receiver[2]
    width = second_edge_x - first_edge_x
    wedge_angle = 3.0 * math.pi / 2.0
    source_first = math.hypot(first_edge_x - source_x, source_vertical)
    source_second = math.hypot(second_edge_x - source_x, source_vertical)
    receiver_first = math.hypot(
        receiver_x - first_edge_x, receiver_vertical
    )
    receiver_second = math.hypot(
        receiver_x - second_edge_x, receiver_vertical
    )
    source_near_angle = math.atan2(
        first_edge_x - source_x, source_vertical
    )
    receiver_near_angle = math.atan2(
        receiver_x - second_edge_x, receiver_vertical
    )
    source_far_angle = 2.0 * math.pi - math.atan2(
        second_edge_x - source_x, source_vertical
    )
    receiver_far_angle = 2.0 * math.pi - math.atan2(
        receiver_x - first_edge_x, receiver_vertical
    )

    def component(
        source_distance: float,
        receiver_distance: float,
        theta: float,
        exterior_angle: float,
    ) -> tuple[float, float, complex]:
        wedge_index = math.pi / exterior_angle
        radicand = 1.0 - math.cos(wedge_index * math.pi) * math.cos(
            wedge_index * theta
        )
        gain = math.sin(wedge_index * math.pi) / math.sqrt(
            max(radicand, 1.0e-12)
        )
        cutoff_factor = (
            wedge_index
            * math.sqrt(max(0.0, radicand))
            / (
                math.cos(wedge_index * math.pi)
                - math.cos(wedge_index * theta)
            )
        )
        characteristic_distance = (
            2.0
            * source_distance
            * receiver_distance
            / (source_distance + receiver_distance)
        )
        cutoff = (
            2.0
            * SPEED_OF_SOUND_M_S
            * cutoff_factor
            * cutoff_factor
            / (math.pi * math.pi * characteristic_distance)
        )
        normalized = frequency_hz / cutoff
        response = (
            (1j * normalized) ** (2.0 / rolloff)
            + (1j * normalized / quality)
            ** (1.0 / (rolloff * transition))
            + 1.0
        ) ** (-alpha_order * rolloff / 2.0)
        return gain, cutoff, response

    def two_term(
        source_distance: float,
        receiver_distance: float,
        source_angle: float,
        receiver_angle: float,
        exterior_angle: float,
    ) -> complex:
        theta_minus = receiver_angle - source_angle
        theta_plus = receiver_angle + source_angle
        minus = component(
            source_distance, receiver_distance, theta_minus, exterior_angle
        )
        plus = component(
            source_distance, receiver_distance, theta_plus, exterior_angle
        )

        def sign(value: float) -> float:
            return -1.0 if value < 0.0 else 1.0 if value > 0.0 else 0.0

        return 0.5 * (
            sign(theta_plus - math.pi) * plus[0] * plus[2]
            + sign(theta_minus - math.pi) * minus[0] * minus[2]
        )

    def single_term(
        source_distance: float,
        receiver_distance: float,
        source_angle: float,
        receiver_angle: float,
        exterior_angle: float,
    ) -> tuple[complex, float]:
        minus = component(
            source_distance,
            receiver_distance,
            receiver_angle - source_angle,
            exterior_angle,
        )
        plus = component(
            source_distance,
            receiver_distance,
            receiver_angle + source_angle,
            exterior_angle,
        )
        cutoff = (
            (minus[0] * math.sqrt(minus[1]) + plus[0] * math.sqrt(plus[1]))
            / 2.0
        ) ** 2
        normalized = frequency_hz / cutoff
        response = (
            (1j * normalized) ** (2.0 / rolloff)
            + (1j * normalized / quality)
            ** (1.0 / (rolloff * transition))
            + 1.0
        ) ** (-alpha_order * rolloff / 2.0)
        return response, cutoff

    def modified_angle(path_distance: float) -> float:
        distance_squared = path_distance * path_distance
        return wedge_angle + (
            distance_squared / (width + distance_squared)
        ) * (wedge_angle - math.pi)

    first = two_term(
        source_first,
        width + receiver_second,
        source_near_angle,
        receiver_far_angle,
        modified_angle(source_first + receiver_first),
    )
    second = two_term(
        receiver_second,
        width + source_first,
        receiver_near_angle,
        source_far_angle,
        modified_angle(source_second + receiver_second),
    )
    first_to_second, first_to_second_cutoff = single_term(
        width,
        receiver_second,
        0.0,
        receiver_near_angle,
        wedge_angle,
    )
    second_to_first, second_to_first_cutoff = single_term(
        width,
        source_first,
        0.0,
        source_near_angle,
        wedge_angle,
    )
    mixing = second_to_first_cutoff / (
        first_to_second_cutoff + second_to_first_cutoff
    )
    pressure = (
        (1.0 - mixing) * first * first_to_second
        + mixing * second * second_to_first
    )
    return pressure


def spectral_comparison(
    shadow: list[float],
    visible: list[float],
    sample_rate: int,
    shadow_onset: int,
    visible_onset: int,
    gate_seconds: float,
    shadow_path_m: float,
    visible_path_m: float,
    source_edge_distance_m: float,
    receiver_edge_distance_m: float,
    bending_angle_rad: float,
    genelec_profile: dict[str, object] | None,
) -> list[dict[str, float]]:
    rows = []
    for frequency in FREQUENCIES_HZ:
        shadow_magnitude = gated_magnitude(
            shadow, sample_rate, shadow_onset, gate_seconds, frequency
        )
        visible_magnitude = gated_magnitude(
            visible, sample_rate, visible_onset, gate_seconds, frequency
        )
        if shadow_magnitude <= 0.0 or visible_magnitude <= 0.0:
            raise ValueError("zero spectral magnitude in comparison")
        normalized = shadow_magnitude / visible_magnitude
        normalized *= shadow_path_m / visible_path_m
        measured_db = 20.0 * math.log10(normalized)
        source_phi, source_theta = source_departure_direction(
            "LS1", "MP1", "double_edge"
        )
        source_directivity_db = (
            genelec_relative_directivity_db(
                genelec_profile,
                frequency,
                source_phi,
                source_theta,
            )
            if genelec_profile is not None
            else 0.0
        )
        corrected_measured_db = measured_db - source_directivity_db
        udfa_magnitude = udfa_infinite_knife_edge_magnitude(
            frequency,
            source_edge_distance_m,
            receiver_edge_distance_m,
            bending_angle_rad,
        )
        udfa_db = 20.0 * math.log10(udfa_magnitude)
        double_edge_magnitude = udfa_2024_double_edge_magnitude(frequency)
        double_edge_db = 20.0 * math.log10(double_edge_magnitude)
        rows.append(
            {
                "frequency_hz": frequency,
                "distance_normalized_shadow_db": measured_db,
                "source_directivity_db": source_directivity_db,
                "directivity_corrected_shadow_db": corrected_measured_db,
                "udfa_ideal_knife_edge_db": udfa_db,
                "udfa_error_db": udfa_db - measured_db,
                "udfa_directivity_corrected_error_db": (
                    udfa_db - corrected_measured_db
                ),
                "udfa_2024_double_edge_db": double_edge_db,
                "udfa_2024_double_edge_error_db": double_edge_db - measured_db,
                "udfa_2024_double_edge_directivity_corrected_error_db": (
                    double_edge_db - corrected_measured_db
                ),
            }
        )
    return rows


def height_at_x(
    first: tuple[float, float, float],
    second: tuple[float, float, float],
    x: float,
) -> float:
    fraction = (x - first[0]) / (second[0] - first[0])
    return first[2] + fraction * (second[2] - first[2])


def shortest_top_path(
    source: tuple[float, float, float],
    receiver: tuple[float, float, float],
) -> tuple[str, float]:
    """Visibility-graph solution around the rectangular RS5 barrier top."""

    top = EDGE_POINT[2]
    clear_front = height_at_x(source, receiver, EDGE_POINT[0]) >= top
    clear_back = height_at_x(source, receiver, SECOND_EDGE_POINT[0]) >= top
    if clear_front and clear_back:
        return "direct", distance(source, receiver)

    candidates: list[tuple[str, float]] = []
    # A ray from the front edge to a receiver above the top plane clears the
    # back edge; the reciprocal statement holds for a source above the plane.
    if receiver[2] >= top:
        candidates.append(
            (
                "front_edge",
                distance(source, EDGE_POINT) + distance(EDGE_POINT, receiver),
            )
        )
    if source[2] >= top:
        candidates.append(
            (
                "back_edge",
                distance(source, SECOND_EDGE_POINT)
                + distance(SECOND_EDGE_POINT, receiver),
            )
        )
    candidates.append(
        (
            "double_edge",
            distance(source, EDGE_POINT)
            + distance(EDGE_POINT, SECOND_EDGE_POINT)
            + distance(SECOND_EDGE_POINT, receiver),
        )
    )
    return min(candidates, key=lambda candidate: candidate[1])


def source_departure_direction(
    source_name: str,
    receiver_name: str,
    path_class: str,
) -> tuple[float, float]:
    source = RS5_SOURCES[source_name]
    if path_class == "direct":
        target = RS5_RECEIVERS[receiver_name]
    elif path_class == "back_edge":
        target = SECOND_EDGE_POINT
    else:
        target = EDGE_POINT
    global_elevation = math.degrees(
        math.atan2(target[2] - source[2], target[0] - source[0])
    )
    local_elevation = (
        global_elevation - RS5_SOURCE_TILT_DEGREES[source_name]
    )
    phi = 0.0 if local_elevation >= 0.0 else 180.0
    return phi, abs(local_elevation)


def floor_reflection_departure_direction(
    source_name: str,
    edge: tuple[float, float, float],
) -> tuple[float, float]:
    """Physical source direction for the image ray that first hits z=0."""

    source = RS5_SOURCES[source_name]
    fraction = source[2] / (source[2] + edge[2])
    reflection_x = source[0] + fraction * (edge[0] - source[0])
    global_elevation = math.degrees(
        math.atan2(-source[2], reflection_x - source[0])
    )
    local_elevation = (
        global_elevation - RS5_SOURCE_TILT_DEGREES[source_name]
    )
    phi = 0.0 if local_elevation >= 0.0 else 180.0
    return phi, abs(local_elevation)


def phase_delay(frequency_hz: float, excess_distance_m: float) -> complex:
    angle = (
        -2.0
        * math.pi
        * frequency_hz
        * excess_distance_m
        / SPEED_OF_SOUND_M_S
    )
    return complex(math.cos(angle), math.sin(angle))


def coherent_ls1_mp1_path_budget(
    frequency_hz: float,
    genelec_profile: dict[str, object] | None,
) -> dict[str, object]:
    """Direct, first-floor and mass-law paths normalized to direct edge range."""

    source = SOURCE_SHADOW
    receiver = RECEIVER_SHADOW
    source_image = (source[0], source[1], -source[2])
    receiver_image = (receiver[0], receiver[1], -receiver[2])
    width = distance(EDGE_POINT, SECOND_EDGE_POINT)
    direct_length = (
        distance(source, EDGE_POINT)
        + width
        + distance(SECOND_EDGE_POINT, receiver)
    )
    before_floor_length = (
        distance(source_image, EDGE_POINT)
        + width
        + distance(SECOND_EDGE_POINT, receiver)
    )
    after_floor_length = (
        distance(source, EDGE_POINT)
        + width
        + distance(SECOND_EDGE_POINT, receiver_image)
    )
    transmission_length = distance(source, receiver)

    direct_phi, direct_theta = source_departure_direction(
        "LS1", "MP1", "double_edge"
    )
    floor_phi, floor_theta = floor_reflection_departure_direction(
        "LS1", EDGE_POINT
    )

    def directivity(phi: float, theta: float) -> complex:
        if genelec_profile is None:
            return 1.0 + 0.0j
        return genelec_relative_directivity_complex(
            genelec_profile, frequency_hz, phi, theta
        )

    direct = (
        directivity(direct_phi, direct_theta)
        * udfa_2024_double_edge_complex(frequency_hz, source, receiver)
    )
    floor_specular = math.sqrt(
        (
            1.0
            - log_frequency_interpolate(
                frequency_hz, TILE_ABSORPTION
            )
        )
        * (
            1.0
            - log_frequency_interpolate(
                frequency_hz, TILE_SCATTERING
            )
        )
    )
    floor_before = (
        directivity(floor_phi, floor_theta)
        * udfa_2024_double_edge_complex(
            frequency_hz, source_image, receiver
        )
        * floor_specular
        * direct_length
        / before_floor_length
        * phase_delay(
            frequency_hz, before_floor_length - direct_length
        )
    )
    floor_after = (
        directivity(direct_phi, direct_theta)
        * udfa_2024_double_edge_complex(
            frequency_hz, source, receiver_image
        )
        * floor_specular
        * direct_length
        / after_floor_length
        * phase_delay(
            frequency_hz, after_floor_length - direct_length
        )
    )
    reactance_ratio = (
        math.pi
        * frequency_hz
        * MDF25B_SURFACE_MASS_KG_M2
        / (AIR_DENSITY_KG_M3 * SPEED_OF_SOUND_M_S)
    )
    sheet_transmission = 1.0 / complex(1.0, reactance_ratio)
    transmission = (
        sheet_transmission
        * direct_length
        / transmission_length
        * phase_delay(
            frequency_hz, transmission_length - direct_length
        )
    )
    return {
        "direct": direct,
        "floor_before": floor_before,
        "floor_after": floor_after,
        "transmission": transmission,
        "direct_plus_floor": direct + floor_before + floor_after,
        "all_paths": direct + floor_before + floor_after + transmission,
        "lengths_m": {
            "direct_double_edge": direct_length,
            "floor_before": before_floor_length,
            "floor_after": after_floor_length,
            "transmission": transmission_length,
        },
        "floor_specular_pressure": floor_specular,
    }


def analyze_all_arrivals(
    wav_root: Path,
    genelec_profile: dict[str, object] | None,
) -> dict[str, object]:
    rows: list[dict[str, object]] = []
    rir_samples: dict[tuple[str, str], list[float]] = {}
    sample_rate = None
    for source_name, source in RS5_SOURCES.items():
        for receiver_name, receiver in RS5_RECEIVERS.items():
            path = wav_root / f"RS5_RIR_{source_name}_{receiver_name}.wav"
            rate, samples = read_float_wav(path)
            if sample_rate is None:
                sample_rate = rate
            elif rate != sample_rate:
                raise ValueError("RS5 RIR sample rates differ")
            onset, _, _ = first_arrival(samples)
            rir_samples[(source_name, receiver_name)] = samples
            path_class, path_distance = shortest_top_path(source, receiver)
            rows.append(
                {
                    "source": source_name,
                    "receiver": receiver_name,
                    "path_class": path_class,
                    "path_distance_m": path_distance,
                    "onset_sample": onset,
                    "onset_time_ms": onset / rate * 1000.0,
                }
            )

    assert sample_rate is not None
    direct_offsets = [
        row["onset_sample"] / sample_rate
        - row["path_distance_m"] / SPEED_OF_SOUND_M_S
        for row in rows
        if row["path_class"] == "direct"
    ]
    if not direct_offsets:
        raise ValueError("RS5 matrix contains no direct calibration paths")
    system_delay = statistics.median(direct_offsets)

    # Learn the shape left by the unpublished measurement/deconvolution chain
    # from all visible direct paths. A two-millisecond packet contains the
    # complete direct-system lobe; shorter packets put its energy maximum on
    # the analysis boundary and cannot be valid matched templates.
    packet_radius = max(8, round(sample_rate * 0.001))
    initial_direct_packets = []
    for row in rows:
        if row["path_class"] != "direct":
            continue
        predicted = (
            row["path_distance_m"] / SPEED_OF_SOUND_M_S + system_delay
        ) * sample_rate
        samples = rir_samples[(str(row["source"]), str(row["receiver"]))]
        initial_direct_packets.append(
            normalized_energy_packet(samples, round(predicted), packet_radius)
        )
    template = median_energy_template(initial_direct_packets)
    template_peak_offset = max(
        range(len(template)), key=lambda index: template[index]
    ) - packet_radius
    search_radius = max(8, round(sample_rate * 0.001))

    # First pass refines the shared system delay using template landmarks
    # instead of an amplitude threshold, then a second pass reports all paths.
    refined_direct_offsets = []
    for row in rows:
        if row["path_class"] != "direct":
            continue
        predicted = (
            row["path_distance_m"] / SPEED_OF_SOUND_M_S + system_delay
        ) * sample_rate
        samples = rir_samples[(str(row["source"]), str(row["receiver"]))]
        landmark, _ = matched_energy_landmark(
            samples, predicted, template, search_radius
        )
        refined_direct_offsets.append(
            landmark / sample_rate
            - row["path_distance_m"] / SPEED_OF_SOUND_M_S
        )
    matched_system_delay = statistics.median(refined_direct_offsets)

    squared_error = 0.0
    maximum_error = 0.0
    matched_squared_error = 0.0
    matched_maximum_error = 0.0
    counts: dict[str, int] = {}
    for row in rows:
        predicted = row["path_distance_m"] / SPEED_OF_SOUND_M_S + system_delay
        error_ms = (row["onset_sample"] / sample_rate - predicted) * 1000.0
        row["arrival_error_ms"] = error_ms
        squared_error += error_ms * error_ms
        maximum_error = max(maximum_error, abs(error_ms))
        path_class = str(row["path_class"])
        counts[path_class] = counts.get(path_class, 0) + 1
        matched_prediction = (
            row["path_distance_m"] / SPEED_OF_SOUND_M_S
            + matched_system_delay
        )
        samples = rir_samples[(str(row["source"]), str(row["receiver"]))]
        matched_landmark, matched_score = matched_energy_landmark(
            samples,
            matched_prediction * sample_rate,
            template,
            search_radius,
        )
        matched_error_ms = (
            matched_landmark / sample_rate - matched_prediction
        ) * 1000.0
        row["matched_landmark_sample"] = matched_landmark
        row["matched_packet_peak_sample"] = (
            matched_landmark + template_peak_offset
        )
        row["matched_score"] = matched_score
        row["matched_arrival_error_ms"] = matched_error_ms
        matched_squared_error += matched_error_ms * matched_error_ms
        matched_maximum_error = max(
            matched_maximum_error, abs(matched_error_ms)
        )
    standard_height_rows = [
        row
        for row in rows
        if row["source"] in {"LS1", "LS2", "LS3"}
        and row["receiver"] in {"MP1", "MP3", "MP4"}
    ]
    standard_squared_error = sum(
        row["arrival_error_ms"] * row["arrival_error_ms"]
        for row in standard_height_rows
    )
    standard_matched_squared_error = sum(
        row["matched_arrival_error_ms"] * row["matched_arrival_error_ms"]
        for row in standard_height_rows
    )

    # Absolute RIR calibration lets all 16 paths share a source-spectrum
    # estimate. Geometry-predicted leading edges avoid the near-floor
    # pre-ringing false triggers. When the official directivity profile is
    # available, remove the measured off-axis source response before deriving
    # propagation attenuation.
    direct_calibration_by_frequency: dict[float, float] = {}
    path_spectra: dict[tuple[str, str], list[dict[str, float]]] = {}
    raw_by_frequency: dict[
        float, list[tuple[dict[str, object], float, float]]
    ] = {}
    for frequency in FREQUENCIES_HZ:
        raw_rows = []
        for row in rows:
            source_name = str(row["source"])
            receiver_name = str(row["receiver"])
            samples = rir_samples[(source_name, receiver_name)]
            predicted_onset = (
                row["onset_sample"]
                if row["path_class"] == "direct"
                else (
                    row["path_distance_m"] / SPEED_OF_SOUND_M_S
                    + system_delay
                )
                * sample_rate
            )
            magnitude = gated_magnitude(
                samples,
                sample_rate,
                round(predicted_onset),
                0.001,
                frequency,
            )
            phi, theta = source_departure_direction(
                source_name,
                receiver_name,
                str(row["path_class"]),
            )
            directivity_db = (
                genelec_relative_directivity_db(
                    genelec_profile, frequency, phi, theta
                )
                if genelec_profile is not None
                else 0.0
            )
            directivity_gain = 10.0 ** (directivity_db / 20.0)
            distance_normalized = (
                magnitude * float(row["path_distance_m"]) / directivity_gain
            )
            raw_rows.append((row, distance_normalized, directivity_db))
        raw_by_frequency[frequency] = raw_rows
        direct_values = [
            value
            for row, value, _ in raw_rows
            if row["path_class"] == "direct"
        ]
        direct_calibration_by_frequency[frequency] = statistics.median(
            direct_values
        )

    for frequency, raw_rows in raw_by_frequency.items():
        calibration = direct_calibration_by_frequency[frequency]
        for row, normalized, directivity_db in raw_rows:
            key = (str(row["source"]), str(row["receiver"]))
            path_spectra.setdefault(key, []).append(
                {
                    "frequency_hz": frequency,
                    "source_directivity_db": directivity_db,
                    "propagation_attenuation_db": 20.0
                    * math.log10(normalized / calibration),
                }
            )
    for row in rows:
        row["geometry_anchored_one_ms_spectrum"] = path_spectra[
            (str(row["source"]), str(row["receiver"]))
        ]

    direct_spread = []
    for frequency, raw_rows in raw_by_frequency.items():
        calibration = direct_calibration_by_frequency[frequency]
        direct_db = [
            20.0 * math.log10(value / calibration)
            for row, value, _ in raw_rows
            if row["path_class"] == "direct"
        ]
        direct_spread.append(
            {
                "frequency_hz": frequency,
                "rms_db": math.sqrt(
                    sum(value * value for value in direct_db)
                    / len(direct_db)
                ),
                "max_abs_db": max(abs(value) for value in direct_db),
            }
        )
    return {
        "system_delay_ms_from_direct_median": system_delay * 1000.0,
        "matched_system_delay_ms_from_direct_median": (
            matched_system_delay * 1000.0
        ),
        "matched_template": {
            "source": "median normalized energy packet from five direct paths",
            "packet_radius_samples": packet_radius,
            "search_radius_samples": search_radius,
            "peak_offset_samples": template_peak_offset,
            "raw_sweep_or_inverse_filter_available": False,
        },
        "geometry_anchored_one_ms_spectra": {
            "calibration": (
                "median distance-normalized spectrum of five direct paths"
            ),
            "source_directivity_correction": (
                "official Genelec 8020c MPS"
                if genelec_profile is not None
                else "not applied"
            ),
            "direct_calibration_spread": direct_spread,
            "warning": (
                "A 1 ms gate is below reliable one-third-octave resolution "
                "near 1 kHz. Use these spectra as diagnostics, not as an "
                "absolute model-selection golden."
            ),
        },
        "arrival_rms_error_ms": math.sqrt(squared_error / len(rows)),
        "arrival_max_abs_error_ms": maximum_error,
        "matched_arrival_rms_error_ms": math.sqrt(
            matched_squared_error / len(rows)
        ),
        "matched_arrival_max_abs_error_ms": matched_maximum_error,
        "standard_height_subset": {
            "definition": "LS1-LS3 x MP1,MP3,MP4; excludes near-floor LS4/MP2",
            "count": len(standard_height_rows),
            "arrival_rms_error_ms": math.sqrt(
                standard_squared_error / len(standard_height_rows)
            ),
            "arrival_max_abs_error_ms": max(
                abs(row["arrival_error_ms"]) for row in standard_height_rows
            ),
            "matched_arrival_rms_error_ms": math.sqrt(
                standard_matched_squared_error / len(standard_height_rows)
            ),
            "matched_arrival_max_abs_error_ms": max(
                abs(row["matched_arrival_error_ms"])
                for row in standard_height_rows
            ),
        },
        "path_class_counts": counts,
        "paths": rows,
    }


def analyze(
    root: Path,
    archive: Path | None,
    genelec_mps: Path | None,
) -> dict[str, object]:
    wav_root = root / "RIRs" / "wav"
    shadow_path = wav_root / "RS5_RIR_LS1_MP1.wav"
    visible_path = wav_root / "RS5_RIR_LS3_MP4.wav"
    if not shadow_path.is_file() or not visible_path.is_file():
        raise FileNotFoundError(
            "dataset_root must contain RIRs/wav/RS5_RIR_LS1_MP1.wav and "
            "RS5_RIR_LS3_MP4.wav"
        )

    archive_hash = None
    if archive is not None:
        archive_hash = sha256(archive)
        if archive_hash.lower() != EXPECTED_ARCHIVE_SHA256:
            raise ValueError(
                f"{archive}: SHA-256 {archive_hash} does not match the "
                f"documented BRAS RS5 archive"
            )

    shadow_rate, shadow = read_float_wav(shadow_path)
    visible_rate, visible = read_float_wav(visible_path)
    if shadow_rate != visible_rate:
        raise ValueError("shadow and visible WAV sample rates differ")

    shadow_onset, shadow_peak, shadow_noise = first_arrival(shadow)
    visible_onset, visible_peak, visible_noise = first_arrival(visible)
    visible_distance = distance(SOURCE_VISIBLE, RECEIVER_VISIBLE)
    source_edge_distance = distance(SOURCE_SHADOW, EDGE_POINT)
    receiver_edge_distance = distance(EDGE_POINT, RECEIVER_SHADOW)
    shadow_distance = source_edge_distance + receiver_edge_distance
    double_edge_distance = (
        distance(SOURCE_SHADOW, EDGE_POINT)
        + distance(EDGE_POINT, SECOND_EDGE_POINT)
        + distance(SECOND_EDGE_POINT, RECEIVER_SHADOW)
    )
    floor_double_edge_distance = first_floor_double_edge_path_m(
        SOURCE_SHADOW,
        RECEIVER_SHADOW,
        EDGE_POINT,
        SECOND_EDGE_POINT,
    )
    source_edge_vector = tuple(
        source - edge for source, edge in zip(SOURCE_SHADOW, EDGE_POINT)
    )
    receiver_edge_vector = tuple(
        receiver - edge for receiver, edge in zip(RECEIVER_SHADOW, EDGE_POINT)
    )
    ray_angle = math.acos(
        sum(a * b for a, b in zip(source_edge_vector, receiver_edge_vector))
        / (source_edge_distance * receiver_edge_distance)
    )
    bending_angle = math.pi - ray_angle
    predicted_delay = (shadow_distance - visible_distance) / SPEED_OF_SOUND_M_S
    measured_delay = (shadow_onset - visible_onset) / shadow_rate

    genelec_profile = (
        load_genelec_mps(genelec_mps)
        if genelec_mps is not None
        else None
    )
    all_arrivals = analyze_all_arrivals(wav_root, genelec_profile)
    gates = {}
    for milliseconds in (1.0, 4.0):
        gates[f"{milliseconds:.1f}_ms"] = spectral_comparison(
            shadow,
            visible,
            shadow_rate,
            shadow_onset,
            visible_onset,
            milliseconds / 1000.0,
            shadow_distance,
            visible_distance,
            source_edge_distance,
            receiver_edge_distance,
            bending_angle,
            genelec_profile,
        )
    udfa_error_key = (
        "udfa_directivity_corrected_error_db"
        if genelec_profile is not None
        else "udfa_error_db"
    )
    one_ms_errors = [row[udfa_error_key] for row in gates["1.0_ms"]]
    udfa_rms_error = math.sqrt(
        sum(error * error for error in one_ms_errors) / len(one_ms_errors)
    )
    double_error_key = (
        "udfa_2024_double_edge_directivity_corrected_error_db"
        if genelec_profile is not None
        else "udfa_2024_double_edge_error_db"
    )
    double_edge_errors = [
        row[double_error_key] for row in gates["1.0_ms"]
    ]
    double_edge_rms_error = math.sqrt(
        sum(error * error for error in double_edge_errors)
        / len(double_edge_errors)
    )
    coherent_rows = []
    for frequency in FREQUENCIES_HZ:
        shadow_full = full_rir_magnitude(shadow, shadow_rate, frequency)
        visible_full = full_rir_magnitude(visible, visible_rate, frequency)
        measured_full_db = 20.0 * math.log10(
            shadow_full
            / visible_full
            * double_edge_distance
            / visible_distance
        )
        budget = coherent_ls1_mp1_path_budget(
            frequency, genelec_profile
        )

        def pressure_db(name: str) -> float:
            return 20.0 * math.log10(abs(budget[name]))

        coherent_rows.append(
            {
                "frequency_hz": frequency,
                "measured_full_rir_db": measured_full_db,
                "direct_double_edge_db": pressure_db("direct"),
                "floor_before_db": pressure_db("floor_before"),
                "floor_after_db": pressure_db("floor_after"),
                "transmission_db": pressure_db("transmission"),
                "direct_plus_floor_db": pressure_db(
                    "direct_plus_floor"
                ),
                "all_paths_db": pressure_db("all_paths"),
                "direct_error_db": (
                    pressure_db("direct") - measured_full_db
                ),
                "direct_plus_floor_error_db": (
                    pressure_db("direct_plus_floor")
                    - measured_full_db
                ),
                "all_paths_error_db": (
                    pressure_db("all_paths") - measured_full_db
                ),
                "floor_specular_pressure": budget[
                    "floor_specular_pressure"
                ],
            }
        )

    def coherent_rms(error_key: str) -> float:
        return math.sqrt(
            sum(row[error_key] ** 2 for row in coherent_rows)
            / len(coherent_rows)
        )
    return {
        "dataset": {
            "name": "BRAS RS5 diffraction (infinite wedge)",
            "archive_sha256": archive_hash or EXPECTED_ARCHIVE_SHA256,
            "license": "CC BY-SA 4.0",
        },
        "genelec_directivity": (
            {
                "applied": True,
                "sha256": genelec_profile["sha256"],
                "entry": (
                    "Genelec8020_DAF_2016_1x1_64442_MPS_front_pole.mat"
                ),
            }
            if genelec_profile is not None
            else {
                "applied": False,
                "expected_sha256": GENELEC_MPS_SHA256,
            }
        ),
        "sample_rate_hz": shadow_rate,
        "geometry": {
            "visible_path_m": visible_distance,
            "shadow_edge_path_m": shadow_distance,
            "source_edge_distance_m": source_edge_distance,
            "receiver_edge_distance_m": receiver_edge_distance,
            "bending_angle_rad": bending_angle,
            "predicted_excess_delay_ms": predicted_delay * 1000.0,
        },
        "scene_boundary_reference": {
            "mdf25b": {
                "density_kg_m3": 742.4,
                "surface_mass_kg_m2": MDF25B_SURFACE_MASS_KG_M2,
                "normal_incidence_limp_sheet_transmission": [
                    {
                        "frequency_hz": frequency,
                        "pressure_db": limp_sheet_transmission_db(frequency),
                        "absorption": log_frequency_interpolate(
                            frequency, MDF25B_ABSORPTION
                        ),
                    }
                    for frequency in FREQUENCIES_HZ
                ],
            },
            "tile_floor": {
                "absorption": [
                    {
                        "frequency_hz": frequency,
                        "coefficient": log_frequency_interpolate(
                            frequency, TILE_ABSORPTION
                        ),
                    }
                    for frequency in FREQUENCIES_HZ
                ],
                "ls1_mp1_first_floor_double_edge_excess_delay_ms": (
                    floor_double_edge_distance - double_edge_distance
                )
                / SPEED_OF_SOUND_M_S
                * 1000.0,
            },
        },
        "first_arrival": {
            "visible_sample": visible_onset,
            "shadow_sample": shadow_onset,
            "measured_excess_delay_ms": measured_delay * 1000.0,
            "delay_error_ms": (measured_delay - predicted_delay) * 1000.0,
            "visible_peak": visible_peak,
            "shadow_peak": shadow_peak,
            "visible_pre_noise_rms": visible_noise,
            "shadow_pre_noise_rms": shadow_noise,
        },
        "spectral_gates": gates,
        "coherent_full_rir_budget": {
            "normalization": (
                "LS1-MP1 / LS3-MP4, normalized by direct double-edge "
                "and visible free-field distances"
            ),
            "rows": coherent_rows,
            "direct_rms_error_db": coherent_rms("direct_error_db"),
            "direct_plus_floor_rms_error_db": coherent_rms(
                "direct_plus_floor_error_db"
            ),
            "all_paths_rms_error_db": coherent_rms(
                "all_paths_error_db"
            ),
            "caveat": (
                "The complete measured RIR also contains chamber and fixture "
                "residuals. The mass-law path is an upper-bound reference, "
                "not a panel-resonance model."
            ),
        },
        "all_16_first_arrivals": all_arrivals,
        "udfa": {
            "geometry": "ideal 2pi knife edge",
            "parameters": UDFA_PARAMETERS,
            "one_ms_gate_rms_error_db": udfa_rms_error,
        },
        "udfa_2024_double_edge": {
            "geometry": "two 3pi/2 edges separated by 0.025 m",
            "equations": "Kirsch and Ewert 2024, equations (8)-(12)",
            "one_ms_gate_rms_error_db": double_edge_rms_error,
        },
        "caveat": (
            "The 1 ms gate is the diffraction reference. The 4 ms gate includes "
            "early floor/fixture energy and is only a truncation-sensitivity check. "
            "The physical BRAS partition is 25 mm thick, while the first model "
            "comparison intentionally uses an ideal 2pi knife edge."
        ),
    }


def print_text(result: dict[str, object]) -> None:
    geometry = result["geometry"]
    arrival = result["first_arrival"]
    print(f"sample_rate_hz={result['sample_rate_hz']}")
    print(
        "genelec_directivity_applied="
        f"{result['genelec_directivity']['applied']}"
    )
    boundary = result["scene_boundary_reference"]
    print(
        "mdf25b_limp_sheet_transmission "
        + " ".join(
            f"{row['frequency_hz']:.0f}Hz={row['pressure_db']:.3f}dB"
            for row in boundary["mdf25b"][
                "normal_incidence_limp_sheet_transmission"
            ]
        )
    )
    print(
        "ls1_mp1_first_floor_double_edge_excess_delay_ms="
        f"{boundary['tile_floor']['ls1_mp1_first_floor_double_edge_excess_delay_ms']:.6f}"
    )
    print(
        "delay_ms "
        f"predicted={geometry['predicted_excess_delay_ms']:.6f} "
        f"measured={arrival['measured_excess_delay_ms']:.6f} "
        f"error={arrival['delay_error_ms']:.6f}"
    )
    for gate, rows in result["spectral_gates"].items():
        values = " ".join(
            f"{row['frequency_hz']:.0f}Hz="
            f"{row['distance_normalized_shadow_db']:.3f}dB"
            f"(corrected {row['directivity_corrected_shadow_db']:.3f},"
            f" source {row['source_directivity_db']:+.3f};"
            f" UDFA {row['udfa_ideal_knife_edge_db']:.3f},"
            " corrected_err "
            f"{row['udfa_directivity_corrected_error_db']:+.3f};"
            f" double {row['udfa_2024_double_edge_db']:.3f},"
            " corrected_err "
            f"{row['udfa_2024_double_edge_directivity_corrected_error_db']:+.3f})"
            for row in rows
        )
        print(f"gate={gate} {values}")
    print(
        "udfa_1ms_rms_error_db="
        f"{result['udfa']['one_ms_gate_rms_error_db']:.6f}"
    )
    print(
        "udfa_2024_double_edge_1ms_rms_error_db="
        f"{result['udfa_2024_double_edge']['one_ms_gate_rms_error_db']:.6f}"
    )
    coherent = result["coherent_full_rir_budget"]
    print(
        "coherent_full_rir "
        + " ".join(
            f"{row['frequency_hz']:.0f}Hz="
            f"measured:{row['measured_full_rir_db']:.3f},"
            f"direct:{row['direct_double_edge_db']:.3f},"
            f"+floor:{row['direct_plus_floor_db']:.3f},"
            f"+transmission:{row['all_paths_db']:.3f}"
            for row in coherent["rows"]
        )
    )
    print(
        "coherent_full_rir_rms_error_db "
        f"direct={coherent['direct_rms_error_db']:.6f} "
        f"direct_plus_floor={coherent['direct_plus_floor_rms_error_db']:.6f} "
        f"all_paths={coherent['all_paths_rms_error_db']:.6f}"
    )
    arrivals = result["all_16_first_arrivals"]
    print(
        "all_16_arrivals "
        f"rms_error_ms={arrivals['arrival_rms_error_ms']:.6f} "
        f"max_abs_error_ms={arrivals['arrival_max_abs_error_ms']:.6f} "
        f"matched_rms_error_ms={arrivals['matched_arrival_rms_error_ms']:.6f} "
        "matched_max_abs_error_ms="
        f"{arrivals['matched_arrival_max_abs_error_ms']:.6f} "
        f"classes={arrivals['path_class_counts']}"
    )
    standard = arrivals["standard_height_subset"]
    print(
        "standard_height_arrivals "
        f"count={standard['count']} "
        f"rms_error_ms={standard['arrival_rms_error_ms']:.6f} "
        f"max_abs_error_ms={standard['arrival_max_abs_error_ms']:.6f} "
        "matched_rms_error_ms="
        f"{standard['matched_arrival_rms_error_ms']:.6f} "
        "matched_max_abs_error_ms="
        f"{standard['matched_arrival_max_abs_error_ms']:.6f}"
    )
    spread = arrivals["geometry_anchored_one_ms_spectra"][
        "direct_calibration_spread"
    ]
    print(
        "direct_calibration_spread "
        + " ".join(
            f"{row['frequency_hz']:.0f}Hz="
            f"{row['rms_db']:.3f}dB_rms/"
            f"{row['max_abs_db']:.3f}dB_max"
            for row in spread
        )
    )
    ls1_mp1 = next(
        row
        for row in arrivals["paths"]
        if row["source"] == "LS1" and row["receiver"] == "MP1"
    )
    print(
        "all_path_geometry_gate_LS1_MP1 "
        + " ".join(
            f"{row['frequency_hz']:.0f}Hz="
            f"{row['propagation_attenuation_db']:.3f}dB"
            f"(source {row['source_directivity_db']:+.3f})"
            for row in ls1_mp1["geometry_anchored_one_ms_spectrum"]
        )
    )


def main() -> int:
    args = parse_args()
    try:
        result = analyze(args.dataset_root, args.archive, args.genelec_mps)
    except (OSError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    if args.json:
        json.dump(result, sys.stdout, indent=2, sort_keys=True)
        print()
    else:
        print_text(result)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
