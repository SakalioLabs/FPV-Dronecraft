#!/usr/bin/env python3
"""Verify exact production PCM chunks and server-blackbox Doppler binding."""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import math
import struct
from pathlib import Path


LAYERS = ("motor", "propeller")
FLOAT_TOLERANCE = 1.0e-8


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _number(mapping: dict[str, object], key: str) -> float:
    value = mapping.get(key)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{key} must be numeric")
    result = float(value)
    if not math.isfinite(result):
        raise ValueError(f"{key} must be finite")
    return result


def _close(actual: float, expected: float, label: str) -> None:
    scale = max(1.0, abs(actual), abs(expected))
    if abs(actual - expected) > FLOAT_TOLERANCE * scale:
        raise ValueError(f"{label} is inconsistent")


def _percentile(values: list[int], fraction: float) -> int:
    if not values:
        raise ValueError("PCM step distribution is empty")
    ordered = sorted(values)
    index = min(len(ordered) - 1, int(fraction * len(ordered)))
    return ordered[index]


def _decode_pcm(chunk: bytes) -> tuple[int, ...]:
    if len(chunk) % 2:
        raise ValueError("PCM chunk byte length must be even")
    return struct.unpack(f"<{len(chunk) // 2}h", chunk)


def _parse_blackbox(data: bytes) -> tuple[list[str], list[dict[str, str]]]:
    text = data.decode("utf-8")
    reader = csv.DictReader(io.StringIO(text))
    if reader.fieldnames is None or len(reader.fieldnames) < 100:
        raise ValueError("server blackbox header is incomplete")
    rows = list(reader)
    if len(rows) < 2:
        raise ValueError("server blackbox has too few rows")
    required = {"tick", "avg_motor_rpm", "x", "y", "z"}
    if not required.issubset(reader.fieldnames):
        raise ValueError("server blackbox identity columns are missing")
    return reader.fieldnames, rows


def verify(
    report: dict[str, object],
    report_sha256: str,
    pcm_data: bytes,
    blackbox_data: bytes,
) -> dict[str, object]:
    if report.get("schema_version") != 1:
        raise ValueError("Doppler chunk trace schema must be 1")
    if report.get("status") != "valid-doppler-production-chunk-trace":
        raise ValueError("Doppler chunk trace status is invalid")
    if report.get("pcm_encoding") != "s16le-mono-48000":
        raise ValueError("unexpected production PCM encoding")
    if report.get("pcm_bytes") != len(pcm_data):
        raise ValueError("production PCM byte count changed")
    if report.get("pcm_sha256") != _sha256(pcm_data):
        raise ValueError("production PCM hash changed")
    if report.get("blackbox_sha256") != _sha256(blackbox_data):
        raise ValueError("server blackbox hash changed")

    fieldnames, blackbox_rows = _parse_blackbox(blackbox_data)
    if (
        report.get("blackbox_rows") != len(blackbox_rows)
        or report.get("blackbox_columns") != len(fieldnames)
    ):
        raise ValueError("server blackbox shape changed")
    ticks = [int(row["tick"]) for row in blackbox_rows]
    if (
        report.get("blackbox_minimum_tick") != min(ticks)
        or report.get("blackbox_maximum_tick") != max(ticks)
        or max(float(row["avg_motor_rpm"]) for row in blackbox_rows)
        <= 1_000.0
    ):
        raise ValueError("server blackbox flight coverage failed")

    chunks = report.get("chunks")
    if not isinstance(chunks, list) or not chunks:
        raise ValueError("production chunk list is missing")
    target = report.get("target_chunks_per_layer")
    if isinstance(target, bool) or not isinstance(target, int) or target < 4:
        raise ValueError("target chunk count is invalid")
    if [chunk.get("sequence") for chunk in chunks] != list(
        range(len(chunks))
    ):
        raise ValueError("global chunk sequence is not contiguous")

    expected_offset = 0
    layer_chunks: dict[str, list[tuple[dict[str, object], tuple[int, ...]]]] = {
        layer: [] for layer in LAYERS
    }
    rpm_matches = 0
    ratio_values: list[float] = []
    emission_hashes: dict[str, set[str]] = {
        layer: set() for layer in LAYERS
    }
    for chunk in chunks:
        if not isinstance(chunk, dict):
            raise ValueError("chunk metadata must be an object")
        layer = chunk.get("layer")
        if layer not in LAYERS:
            raise ValueError("unexpected production layer")
        if chunk.get("entity_id") != report.get("entity_id"):
            raise ValueError("chunk entity identity changed")
        if chunk.get("pcm_offset") != expected_offset:
            raise ValueError("PCM chunk offsets are not contiguous")
        byte_count = chunk.get("pcm_bytes")
        if (
            isinstance(byte_count, bool)
            or not isinstance(byte_count, int)
            or byte_count <= 0
        ):
            raise ValueError("PCM chunk byte count is invalid")
        payload = pcm_data[expected_offset : expected_offset + byte_count]
        if len(payload) != byte_count:
            raise ValueError("PCM chunk exceeds sidecar")
        if chunk.get("pcm_sha256") != _sha256(payload):
            raise ValueError("PCM chunk hash changed")
        samples = _decode_pcm(payload)
        if chunk.get("pcm_samples") != len(samples) or len(samples) < 1_920:
            raise ValueError("PCM sample count cannot exercise smoothing")
        if max(abs(value) for value in samples) >= 32_767:
            raise ValueError("production PCM chunk clipped")
        rms = math.sqrt(sum(value * value for value in samples) / len(samples))
        if rms <= 1.0:
            raise ValueError("production PCM chunk is effectively silent")
        expected_offset += byte_count

        simulation_nanos = chunk.get("simulation_time_nanos")
        if (
            isinstance(simulation_nanos, bool)
            or not isinstance(simulation_nanos, int)
            or simulation_nanos < 0
            or simulation_nanos % 50_000_000
        ):
            raise ValueError("chunk simulation timestamp is invalid")
        if chunk.get("simulation_tick") != simulation_nanos // 50_000_000:
            raise ValueError("chunk simulation tick is detached")
        if not (
            min(ticks)
            <= int(chunk["simulation_tick"])
            <= max(ticks)
        ):
            raise ValueError("chunk tick is outside server blackbox")

        ratio = _number(chunk, "doppler_frequency_ratio")
        target_hz = _number(chunk, "frame_target_frequency_hz")
        rotor_rpm = _number(chunk, "source_rotor_rpm")
        blade_count = chunk.get("source_rotor_blade_count")
        order = chunk.get("tracked_order")
        if (
            rotor_rpm <= 0.0
            or isinstance(blade_count, bool)
            or not isinstance(blade_count, int)
            or not 1 <= blade_count <= 16
            or order != 1
        ):
            raise ValueError("tracked rotor metadata is invalid")
        kind = chunk.get("tracked_tone_kind")
        if layer == "motor":
            if kind != "shaft":
                raise ValueError("motor trace must track shaft order 1")
            base_hz = rotor_rpm / 60.0
        else:
            if kind != "blade_pass":
                raise ValueError("propeller trace must track blade-pass order 1")
            base_hz = rotor_rpm * blade_count / 60.0
        _close(target_hz, base_hz * ratio, "frame target frequency")

        if (
            chunk.get("frequency_smoothing_samples") != 1_920
            or chunk.get("smoothing_checkpoint_samples") != 1_920
            or chunk.get("checkpoint_ramp_samples_remaining") != 0
            or chunk.get("after_ramp_samples_remaining") != 0
        ):
            raise ValueError("40 ms frequency smoothing contract failed")
        checkpoint_current = _number(
            chunk, "checkpoint_current_frequency_hz"
        )
        checkpoint_target = _number(
            chunk, "checkpoint_target_frequency_hz"
        )
        after_current = _number(chunk, "after_current_frequency_hz")
        after_target = _number(chunk, "after_target_frequency_hz")
        _close(checkpoint_target, target_hz, "40 ms checkpoint target")
        _close(checkpoint_current, target_hz, "40 ms checkpoint frequency")
        _close(after_target, target_hz, "after target frequency")
        _close(after_current, target_hz, "completed ramp frequency")
        if chunk.get("target_changed") is True:
            before_target = _number(chunk, "before_target_frequency_hz")
            _number(chunk, "before_current_frequency_hz")
            if abs(before_target - target_hz) <= 1.0e-9:
                raise ValueError("changed chunk retained the old target")

        rpm_column = f"motor_{chunk.get('tracked_rotor_index')}_rpm"
        if rpm_column not in fieldnames:
            raise ValueError("tracked rotor is absent from blackbox")
        matching_rows = [
            row
            for row in blackbox_rows
            if abs(int(row["tick"]) - int(chunk["simulation_tick"])) <= 2
        ]
        if matching_rows and min(
            abs(float(row[rpm_column]) - rotor_rpm)
            for row in matching_rows
        ) <= 0.2:
            rpm_matches += 1

        emission_hash = chunk.get("emission_sha256")
        if (
            not isinstance(emission_hash, str)
            or len(emission_hash) != 64
        ):
            raise ValueError("emission hash is invalid")
        emission_hashes[layer].add(emission_hash)
        ratio_values.append(ratio)
        layer_chunks[layer].append((chunk, samples))

    if expected_offset != len(pcm_data):
        raise ValueError("PCM sidecar has trailing bytes")
    if rpm_matches * 2 < len(chunks):
        raise ValueError("fewer than half of chunks match server blackbox RPM")
    if not min(ratio_values) < 1.0 < max(ratio_values):
        raise ValueError("trace does not cross unity Doppler ratio")

    maximum_boundary_step = 0
    boundary_thresholds: dict[str, int] = {}
    for layer in LAYERS:
        entries = layer_chunks[layer]
        if len(entries) < target:
            raise ValueError(f"{layer} has too few chunks")
        if [entry[0].get("layer_sequence") for entry in entries] != list(
            range(len(entries))
        ):
            raise ValueError(f"{layer} chunk sequence is not contiguous")
        if len(emission_hashes[layer]) < 3:
            raise ValueError(f"{layer} emission did not change")
        internal_steps: list[int] = []
        for _, samples in entries:
            internal_steps.extend(
                abs(samples[index] - samples[index - 1])
                for index in range(1, len(samples))
            )
        threshold = max(64, int(_percentile(internal_steps, 0.99) * 1.5))
        boundary_thresholds[layer] = threshold
        for index in range(1, len(entries)):
            step = abs(entries[index][1][0] - entries[index - 1][1][-1])
            maximum_boundary_step = max(maximum_boundary_step, step)
            if step > threshold:
                raise ValueError(f"{layer} PCM boundary is discontinuous")

    required_true = (
        "production_stream_read_tapped",
        "exact_returned_pcm_bytes",
        "mixed_live_stream_measured",
        "frequency_smoothing_state_captured",
        "frequency_smoothing_checkpoint_captured",
        "server_blackbox_csv_bound",
    )
    for key in required_true:
        if report.get(key) is not True:
            raise ValueError(f"{key} must be true")
    required_false = (
        "openal_source_queue_observed",
        "openal_playback_capture",
        "callback_underrun_counter_available",
        "real_audio_capture",
        "release_calibrated",
    )
    for key in required_false:
        if report.get(key) is not False:
            raise ValueError(f"{key} must be false")

    return {
        "schema_version": 1,
        "status": "valid-doppler-production-chunk-trace",
        "source_report_sha256": report_sha256,
        "pcm_sha256": _sha256(pcm_data),
        "blackbox_sha256": _sha256(blackbox_data),
        "chunks": len(chunks),
        "motor_chunks": len(layer_chunks["motor"]),
        "propeller_chunks": len(layer_chunks["propeller"]),
        "server_blackbox_rpm_matches": rpm_matches,
        "maximum_pcm_boundary_step": maximum_boundary_step,
        "boundary_thresholds": boundary_thresholds,
        "minimum_doppler_ratio": min(ratio_values),
        "maximum_doppler_ratio": max(ratio_values),
        "gates": {
            "exact_pcm_sidecar_hash_bound": True,
            "every_chunk_hash_bound": True,
            "mixed_stream_non_silent_and_unclipped": True,
            "phase_continuous_chunk_boundaries": True,
            "frequency_smoothing_40_ms_completed": True,
            "frequency_smoothing_40_ms_checkpoint_reached": True,
            "doppler_ratio_crosses_unity": True,
            "server_blackbox_tick_coverage": True,
            "server_blackbox_rpm_crosscheck_at_least_half": True,
            "openal_source_queue_observed": False,
            "openal_playback_capture": False,
            "real_audio_capture": False,
            "release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "Exact buffers and before/40-ms/after oscillator checkpoints from "
            "the production AudioStream plus integrated-server blackbox only; "
            "OpenAL queuing/playback, callback underruns, physical output, and "
            "loopback remain unobserved."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--pcm", type=Path, required=True)
    parser.add_argument("--blackbox", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    report_raw = args.report.read_bytes()
    result = verify(
        json.loads(report_raw.decode("utf-8")),
        _sha256(report_raw),
        args.pcm.read_bytes(),
        args.blackbox.read_bytes(),
    )
    payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(payload, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
