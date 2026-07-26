from __future__ import annotations

import copy
import hashlib
import json
import unittest

import numpy as np

import verify_openal_loopback_render as verify


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _pcm(samples: np.ndarray) -> bytes:
    return samples.astype("<i2").tobytes()


def fixture() -> tuple[dict, bytes, dict, bytes, str]:
    frames = 48_000
    time = np.arange(frames, dtype=np.float64) / 48_000.0
    motor = np.rint(
        1_200.0 * np.sin(2.0 * np.pi * 117.0 * time)
    ).astype(np.int16)
    propeller = np.rint(
        800.0 * np.sin(2.0 * np.pi * 353.0 * time + 0.4)
    ).astype(np.int16)
    motor_pcm = _pcm(motor)
    propeller_pcm = _pcm(propeller)
    trace_pcm = propeller_pcm + motor_pcm
    trace = {
        "schema_version": 1,
        "status": "valid-doppler-production-chunk-trace",
        "pcm_sha256": _sha(trace_pcm),
        "chunks": [
            {
                "sequence": 0,
                "layer": "propeller",
                "pcm_offset": 0,
                "pcm_bytes": len(propeller_pcm),
                "pcm_sha256": _sha(propeller_pcm),
            },
            {
                "sequence": 1,
                "layer": "motor",
                "pcm_offset": len(propeller_pcm),
                "pcm_bytes": len(motor_pcm),
                "pcm_sha256": _sha(motor_pcm),
            },
        ],
    }
    trace_bytes = json.dumps(trace, sort_keys=True).encode()
    trace_sha = _sha(trace_bytes)
    expected = motor.astype(np.int32) + propeller.astype(np.int32)
    lag = 48
    rendered = np.zeros(frames, dtype=np.int16)
    rendered[lag:] = np.rint(expected[:-lag] * 0.5).astype(np.int16)
    rendered_pcm = _pcm(rendered)
    rendered_float = rendered.astype(np.float64)
    correlation, measured_lag = verify._best_lag_correlation(
        rendered_float,
        expected.astype(np.float64),
    )
    report = {
        "schema_version": 1,
        "status": "valid-openal-loopback-render",
        "extension": "ALC_SOFT_loopback",
        "thread_context_extension": "ALC_EXT_thread_local_context",
        "loopback_supported": True,
        "thread_context_supported": True,
        "format_supported": True,
        "render_format": "s16le-mono-48000",
        "render_frames": frames,
        "rendered_pcm_bytes": len(rendered_pcm),
        "rendered_pcm_sha256": _sha(rendered_pcm),
        "doppler_trace_report_sha256": trace_sha,
        "inputs": [
            {
                "layer": "motor",
                "sequence": 1,
                "pcm_bytes": len(motor_pcm),
                "pcm_sha256": _sha(motor_pcm),
            },
            {
                "layer": "propeller",
                "sequence": 0,
                "pcm_bytes": len(propeller_pcm),
                "pcm_sha256": _sha(propeller_pcm),
            },
        ],
        "silence_control_frames": 256,
        "silence_nonzero_samples": 65,
        "silence_peak_absolute_sample": 1,
        "silence_quantization_dither_bounded": True,
        "wall_clock_hold_millis": 50,
        "offsets_before_hold": [0, 0],
        "offsets_after_hold": [0, 0],
        "midpoint_offsets": [24_000, 24_000],
        "midpoint_states": [verify.AL_PLAYING, verify.AL_PLAYING],
        "final_states": [verify.AL_STOPPED, verify.AL_STOPPED],
        "rendered_nonzero_samples": int(np.count_nonzero(rendered)),
        "rendered_peak_absolute_sample": int(
            np.max(np.abs(rendered_float))
        ),
        "rendered_rms": float(
            np.sqrt(np.mean(np.square(rendered_float)))
        ),
        "summed_input_correlation": correlation,
        "summed_input_lag_samples": measured_lag,
        "minecraft_context_unchanged": True,
        "minecraft_device_unchanged": True,
        "minecraft_sound_thread_unchanged": True,
        "minecraft_sound_thread": "Sound engine",
        "loopback_worker_thread": "Dronecraft OpenAL loopback probe",
        "al_error_code": 0,
        "alc_error_code": 0,
        "minecraft_audio_path_changed": False,
        "physical_playback_device_opened": False,
        "capture_device_opened": False,
        "real_audio_capture": False,
        "release_calibrated": False,
    }
    return report, rendered_pcm, trace, trace_pcm, trace_sha


def run_verify(report, rendered_pcm, trace, trace_pcm, trace_sha):
    report["doppler_trace_report_sha256"] = trace_sha
    return verify.verify(
        report,
        "a" * 64,
        rendered_pcm,
        trace,
        trace_sha,
        trace_pcm,
    )


class OpenAlLoopbackRenderVerifyTest(unittest.TestCase):
    def test_accepts_isolated_production_render(self):
        report, pcm, trace, trace_pcm, trace_sha = fixture()
        result = run_verify(report, pcm, trace, trace_pcm, trace_sha)
        self.assertGreater(result["summed_input_correlation"], 0.99)
        self.assertEqual(48, result["summed_input_lag_samples"])

    def test_rejects_detached_trace_hash(self):
        report, pcm, trace, trace_pcm, trace_sha = fixture()
        report["doppler_trace_report_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "trace identity changed"):
            verify.verify(
                report, "a" * 64, pcm, trace, trace_sha, trace_pcm
            )

    def test_rejects_tampered_trace_pcm(self):
        report, pcm, trace, trace_pcm, trace_sha = fixture()
        changed = bytearray(trace_pcm)
        changed[10] ^= 1
        with self.assertRaisesRegex(ValueError, "sidecar changed"):
            run_verify(report, pcm, trace, bytes(changed), trace_sha)

    def test_rejects_input_detachment(self):
        report, pcm, trace, trace_pcm, trace_sha = fixture()
        report["inputs"][0]["sequence"] = 99
        with self.assertRaisesRegex(ValueError, "detached"):
            run_verify(report, pcm, trace, trace_pcm, trace_sha)

    def test_rejects_physical_playback_overclaim(self):
        report, pcm, trace, trace_pcm, trace_sha = fixture()
        report["physical_playback_device_opened"] = True
        with self.assertRaisesRegex(ValueError, "must be False"):
            run_verify(report, pcm, trace, trace_pcm, trace_sha)

    def test_rejects_wall_clock_progress(self):
        report, pcm, trace, trace_pcm, trace_sha = fixture()
        report["offsets_after_hold"] = [1, 0]
        with self.assertRaisesRegex(ValueError, "wall-clock"):
            run_verify(report, pcm, trace, trace_pcm, trace_sha)

    def test_rejects_midpoint_detachment(self):
        report, pcm, trace, trace_pcm, trace_sha = fixture()
        report["midpoint_offsets"] = [20_000, 24_000]
        with self.assertRaisesRegex(ValueError, "explicit render"):
            run_verify(report, pcm, trace, trace_pcm, trace_sha)

    def test_rejects_unbounded_silence_noise(self):
        report, pcm, trace, trace_pcm, trace_sha = fixture()
        report["silence_peak_absolute_sample"] = 2
        with self.assertRaisesRegex(ValueError, "dither control"):
            run_verify(report, pcm, trace, trace_pcm, trace_sha)

    def test_rejects_rendered_pcm_hash_change(self):
        report, pcm, trace, trace_pcm, trace_sha = fixture()
        changed = bytearray(pcm)
        changed[100] ^= 1
        with self.assertRaisesRegex(ValueError, "rendered PCM hash"):
            run_verify(report, bytes(changed), trace, trace_pcm, trace_sha)

    def test_rejects_signal_metric_detachment(self):
        report, pcm, trace, trace_pcm, trace_sha = fixture()
        report["summed_input_lag_samples"] += 1
        with self.assertRaisesRegex(ValueError, "metrics are detached"):
            run_verify(report, pcm, trace, trace_pcm, trace_sha)


if __name__ == "__main__":
    unittest.main()
