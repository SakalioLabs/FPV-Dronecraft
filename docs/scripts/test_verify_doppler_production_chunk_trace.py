import copy
import csv
import hashlib
import io
import math
import struct
import unittest

import verify_doppler_production_chunk_trace as verify


def sha(data):
    return hashlib.sha256(data).hexdigest()


def fixture():
    ticks = [10, 20, 30, 40]
    rpms = [6_000.0, 6_300.0, 5_900.0, 6_100.0]
    ratios = [1.001, 1.0002, 0.9998, 1.0001]
    phase = {"motor": 0.0, "propeller": 0.0}
    pcm = bytearray()
    chunks = []
    sequence = 0
    for layer_sequence, (tick, rpm, ratio) in enumerate(
        zip(ticks, rpms, ratios)
    ):
        for layer in ("propeller", "motor"):
            frequency = rpm / 60.0 * (3 if layer == "propeller" else 1)
            samples = []
            for _ in range(2_400):
                samples.append(int(round(1_000.0 * math.sin(phase[layer]))))
                phase[layer] += 2.0 * math.pi * frequency / 48_000.0
            payload = struct.pack(f"<{len(samples)}h", *samples)
            offset = len(pcm)
            pcm.extend(payload)
            target = frequency * ratio
            chunks.append(
                {
                    "sequence": sequence,
                    "layer_sequence": layer_sequence,
                    "layer": layer,
                    "entity_id": 3,
                    "simulation_time_nanos": tick * 50_000_000,
                    "simulation_tick": tick,
                    "doppler_frequency_ratio": ratio,
                    "emission_sha256": sha(
                        f"{layer}-{layer_sequence}".encode()
                    ),
                    "tracked_tone_kind": (
                        "blade_pass" if layer == "propeller" else "shaft"
                    ),
                    "tracked_rotor_index": 0,
                    "tracked_order": 1,
                    "source_rotor_rpm": rpm,
                    "source_rotor_blade_count": 3,
                    "frame_target_frequency_hz": target,
                    "frequency_smoothing_samples": 1_920,
                    "before_current_frequency_hz": target - 4.0,
                    "before_target_frequency_hz": target - 4.0,
                    "before_ramp_samples_remaining": 0,
                    "smoothing_checkpoint_samples": 1_920,
                    "checkpoint_current_frequency_hz": target,
                    "checkpoint_target_frequency_hz": target,
                    "checkpoint_ramp_samples_remaining": 0,
                    "after_current_frequency_hz": target,
                    "after_target_frequency_hz": target,
                    "after_ramp_samples_remaining": 0,
                    "target_changed": True,
                    "pcm_offset": offset,
                    "pcm_bytes": len(payload),
                    "pcm_samples": len(samples),
                    "pcm_sha256": sha(payload),
                }
            )
            sequence += 1

    fieldnames = [
        "game_time",
        "tick",
        "avg_motor_rpm",
        "motor_0_rpm",
        "x",
        "y",
        "z",
    ] + [f"diagnostic_{index}" for index in range(100)]
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=fieldnames, lineterminator="\n")
    writer.writeheader()
    for tick in range(1, 42):
        rpm = 6_000.0
        for trace_tick, trace_rpm in zip(ticks, rpms):
            if tick == trace_tick - 1:
                rpm = trace_rpm
        row = {key: "0" for key in fieldnames}
        row.update(
            {
                "game_time": str(tick),
                "tick": str(tick),
                "avg_motor_rpm": str(rpm),
                "motor_0_rpm": str(rpm),
                "x": "1",
                "y": "2",
                "z": "3",
            }
        )
        writer.writerow(row)
    blackbox = output.getvalue().encode()
    report = {
        "schema_version": 1,
        "status": "valid-doppler-production-chunk-trace",
        "entity_id": 3,
        "target_chunks_per_layer": 4,
        "motor_chunks": 4,
        "propeller_chunks": 4,
        "changed_motor_chunks": 4,
        "changed_propeller_chunks": 4,
        "minimum_trace_tick": 10,
        "maximum_trace_tick": 40,
        "pcm_encoding": "s16le-mono-48000",
        "pcm_bytes": len(pcm),
        "pcm_sha256": sha(pcm),
        "blackbox_rows": 41,
        "blackbox_columns": len(fieldnames),
        "blackbox_minimum_tick": 1,
        "blackbox_maximum_tick": 41,
        "blackbox_sha256": sha(blackbox),
        "chunks": chunks,
        "production_stream_read_tapped": True,
        "exact_returned_pcm_bytes": True,
        "mixed_live_stream_measured": True,
        "frequency_smoothing_state_captured": True,
        "frequency_smoothing_checkpoint_captured": True,
        "server_blackbox_csv_bound": True,
        "openal_source_queue_observed": False,
        "openal_playback_capture": False,
        "callback_underrun_counter_available": False,
        "real_audio_capture": False,
        "release_calibrated": False,
    }
    return report, bytes(pcm), blackbox


class DopplerProductionChunkTraceVerifyTest(unittest.TestCase):
    def test_accepts_hash_bound_continuous_trace(self):
        report, pcm, blackbox = fixture()
        result = verify.verify(report, "a" * 64, pcm, blackbox)
        self.assertTrue(result["gates"]["phase_continuous_chunk_boundaries"])
        self.assertTrue(result["gates"]["doppler_ratio_crosses_unity"])
        self.assertEqual(result["server_blackbox_rpm_matches"], 8)

    def test_rejects_pcm_hash_change(self):
        report, pcm, blackbox = fixture()
        changed = bytearray(pcm)
        changed[0] ^= 1
        with self.assertRaisesRegex(ValueError, "PCM hash"):
            verify.verify(report, "a" * 64, bytes(changed), blackbox)

    def test_rejects_discontinuous_chunk_with_updated_hashes(self):
        report, pcm, blackbox = fixture()
        changed = bytearray(pcm)
        chunk = report["chunks"][3]
        struct.pack_into("<h", changed, chunk["pcm_offset"], 10_000)
        payload = bytes(
            changed[
                chunk["pcm_offset"] : chunk["pcm_offset"] + chunk["pcm_bytes"]
            ]
        )
        chunk["pcm_sha256"] = sha(payload)
        report["pcm_sha256"] = sha(changed)
        with self.assertRaisesRegex(ValueError, "boundary"):
            verify.verify(report, "a" * 64, bytes(changed), blackbox)

    def test_rejects_wrong_smoothing_duration(self):
        report, pcm, blackbox = fixture()
        report["chunks"][0]["frequency_smoothing_samples"] = 1_000
        with self.assertRaisesRegex(ValueError, "40 ms"):
            verify.verify(report, "a" * 64, pcm, blackbox)

    def test_rejects_incomplete_smoothing_checkpoint(self):
        report, pcm, blackbox = fixture()
        report["chunks"][0]["checkpoint_ramp_samples_remaining"] = 1
        with self.assertRaisesRegex(ValueError, "40 ms"):
            verify.verify(report, "a" * 64, pcm, blackbox)

    def test_rejects_detached_rotor_target(self):
        report, pcm, blackbox = fixture()
        report["chunks"][0]["frame_target_frequency_hz"] += 2.0
        with self.assertRaisesRegex(ValueError, "frame target"):
            verify.verify(report, "a" * 64, pcm, blackbox)

    def test_rejects_blackbox_rpm_detachment(self):
        report, pcm, blackbox = fixture()
        text = blackbox.decode()
        for rpm in ("6000.0", "6300.0", "5900.0", "6100.0"):
            text = text.replace(rpm, "2000.0")
        changed = text.encode()
        report["blackbox_sha256"] = sha(changed)
        with self.assertRaisesRegex(ValueError, "half"):
            verify.verify(report, "a" * 64, pcm, changed)

    def test_rejects_no_ratio_crossing(self):
        report, pcm, blackbox = fixture()
        for chunk in report["chunks"]:
            ratio = 1.001
            chunk["doppler_frequency_ratio"] = ratio
            rpm = chunk["source_rotor_rpm"]
            multiplier = (
                chunk["source_rotor_blade_count"]
                if chunk["layer"] == "propeller"
                else 1
            )
            target = rpm / 60.0 * multiplier * ratio
            chunk["frame_target_frequency_hz"] = target
            chunk["checkpoint_current_frequency_hz"] = target
            chunk["checkpoint_target_frequency_hz"] = target
            chunk["after_current_frequency_hz"] = target
            chunk["after_target_frequency_hz"] = target
        with self.assertRaisesRegex(ValueError, "cross unity"):
            verify.verify(report, "a" * 64, pcm, blackbox)

    def test_rejects_openal_queue_overclaim(self):
        report, pcm, blackbox = fixture()
        report["openal_source_queue_observed"] = True
        with self.assertRaisesRegex(ValueError, "must be false"):
            verify.verify(report, "a" * 64, pcm, blackbox)


if __name__ == "__main__":
    unittest.main()
