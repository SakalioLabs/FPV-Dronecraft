#!/usr/bin/env python3
"""Generate the original mono Ogg assets used by the drone sound simulation.

The loops are built from periodic spectra so their first and last samples join
without a click.  Keeping the generator in the repository makes the decoded
audio content reproducible and avoids depending on third-party recordings.
Requires NumPy, SciPy, and an ffmpeg executable with libvorbis support.
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np
from scipy import signal


SAMPLE_RATE = 48_000
DEFAULT_OUTPUT = (
    Path(__file__).resolve().parents[2]
    / "fabric-mod"
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "fpvdrone"
    / "sounds"
    / "drone"
)


def periodic_noise(
    duration_seconds: float,
    rng: np.random.Generator,
    low_hz: float,
    high_hz: float,
    spectral_slope: float,
) -> np.ndarray:
    """Return deterministic, exactly periodic, band-limited noise."""
    sample_count = round(duration_seconds * SAMPLE_RATE)
    frequencies = np.fft.rfftfreq(sample_count, 1.0 / SAMPLE_RATE)
    spectrum = np.zeros(frequencies.size, dtype=np.complex128)
    active = (frequencies >= low_hz) & (frequencies <= high_hz)
    active_frequencies = np.maximum(frequencies[active], low_hz)
    magnitude = np.power(active_frequencies / low_hz, spectral_slope)
    phases = rng.uniform(0.0, 2.0 * np.pi, active.sum())
    spectrum[active] = magnitude * np.exp(1j * phases)
    samples = np.fft.irfft(spectrum, n=sample_count)
    rms = float(np.sqrt(np.mean(samples * samples)))
    return samples / max(rms, 1.0e-9)


def soft_limit(samples: np.ndarray, peak: float = 0.92) -> np.ndarray:
    shaped = np.tanh(samples)
    shaped_peak = float(np.max(np.abs(shaped)))
    return shaped * (peak / max(shaped_peak, 1.0e-9))


def motor_loop() -> np.ndarray:
    duration = 2.0
    count = round(duration * SAMPLE_RATE)
    time = np.arange(count, dtype=np.float64) / SAMPLE_RATE
    rng = np.random.default_rng(0x4D4F544F52)

    # Integer-Hz partials remain phase-continuous at the two-second boundary.
    tone = (
        0.48 * np.sin(2.0 * np.pi * 180.0 * time)
        + 0.27 * np.sin(2.0 * np.pi * 360.0 * time + 0.31)
        + 0.16 * np.sin(2.0 * np.pi * 720.0 * time + 1.17)
        + 0.08 * np.sin(2.0 * np.pi * 1_440.0 * time + 0.73)
        + 0.045 * np.sin(2.0 * np.pi * 2_880.0 * time + 2.03)
    )
    commutation = periodic_noise(duration, rng, 900.0, 6_500.0, -0.42)
    bearing = periodic_noise(duration, rng, 120.0, 2_200.0, -0.72)
    amplitude_flutter = 1.0 + 0.035 * np.sin(2.0 * np.pi * 11.0 * time)
    return soft_limit(amplitude_flutter * tone + 0.075 * commutation + 0.035 * bearing)


def propeller_loop() -> np.ndarray:
    duration = 2.0
    count = round(duration * SAMPLE_RATE)
    time = np.arange(count, dtype=np.float64) / SAMPLE_RATE
    rng = np.random.default_rng(0x50524F50)

    air = periodic_noise(duration, rng, 55.0, 9_000.0, -0.68)
    edge = periodic_noise(duration, rng, 700.0, 11_500.0, -0.28)
    blade_pulse = (
        0.24 * np.sin(2.0 * np.pi * 96.0 * time)
        + 0.10 * np.sin(2.0 * np.pi * 192.0 * time + 0.62)
        + 0.045 * np.sin(2.0 * np.pi * 288.0 * time + 1.44)
    )
    load_modulation = 0.82 + 0.12 * np.sin(2.0 * np.pi * 7.0 * time + 0.2)
    load_modulation += 0.06 * np.sin(2.0 * np.pi * 13.0 * time + 1.1)
    return soft_limit(load_modulation * (0.48 * air + blade_pulse) + 0.13 * edge)


def impact_variant(seed: int, body_hz: float, shell_hz: float) -> np.ndarray:
    duration = 0.34
    count = round(duration * SAMPLE_RATE)
    time = np.arange(count, dtype=np.float64) / SAMPLE_RATE
    rng = np.random.default_rng(seed)

    impulse = rng.normal(0.0, 1.0, count)
    impulse *= np.exp(-time * 42.0)
    b, a = signal.butter(2, [110.0, 4_800.0], btype="bandpass", fs=SAMPLE_RATE)
    crack = signal.lfilter(b, a, impulse)
    body = np.sin(2.0 * np.pi * body_hz * time + rng.uniform(0.0, 1.0)) * np.exp(-time * 22.0)
    shell = np.sin(2.0 * np.pi * shell_hz * time + rng.uniform(0.0, 1.0)) * np.exp(-time * 34.0)
    samples = 0.52 * crack + 0.32 * body + 0.17 * shell
    samples *= np.minimum(time / 0.0015, 1.0)
    samples[-256:] *= np.linspace(1.0, 0.0, 256)
    return soft_limit(samples, peak=0.88)


def write_wave(path: Path, samples: np.ndarray) -> None:
    pcm = np.clip(samples, -1.0, 1.0)
    pcm = np.round(pcm * 32_767.0).astype("<i2")
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(pcm.tobytes())


def encode_ogg(source: Path, destination: Path) -> None:
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        raise RuntimeError("ffmpeg is required to encode the generated Ogg assets")
    subprocess.run(
        [
            ffmpeg,
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            str(source),
            "-c:a",
            "libvorbis",
            "-q:a",
            "5",
            str(destination),
        ],
        check=True,
    )


def generate(output_directory: Path) -> None:
    output_directory.mkdir(parents=True, exist_ok=True)
    assets = {
        "motor_loop.ogg": motor_loop(),
        "propeller_loop.ogg": propeller_loop(),
        "impact_1.ogg": impact_variant(0x494D5031, 176.0, 730.0),
        "impact_2.ogg": impact_variant(0x494D5032, 193.0, 840.0),
        "impact_3.ogg": impact_variant(0x494D5033, 158.0, 660.0),
    }
    with tempfile.TemporaryDirectory(prefix="fpvdrone-audio-") as temporary:
        temporary_directory = Path(temporary)
        for name, samples in assets.items():
            wave_path = temporary_directory / f"{Path(name).stem}.wav"
            write_wave(wave_path, samples)
            encode_ogg(wave_path, output_directory / name)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    generate(args.output.resolve())


if __name__ == "__main__":
    main()
