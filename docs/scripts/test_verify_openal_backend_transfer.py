from __future__ import annotations

import copy
import hashlib
import json
import unittest

import numpy as np

import verify_openal_backend_transfer as verify


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fixture():
    sample_rate = verify.SAMPLE_RATE
    input_frames = verify.INPUT_FRAMES
    render_frames = verify.RENDER_FRAMES
    time = np.arange(input_frames) / sample_rate
    motor = np.rint(1200 * np.sin(2 * np.pi * 117 * time)).astype("<i2")
    propeller = np.rint(
        800 * np.sin(2 * np.pi * 353 * time + 0.4)
    ).astype("<i2")
    motor_pcm = motor.tobytes()
    propeller_pcm = propeller.tobytes()
    trace_pcm = propeller_pcm + motor_pcm
    chunks = [
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
    ]
    trace = {
        "status": "valid-doppler-production-chunk-trace",
        "pcm_sha256": _sha(trace_pcm),
        "chunks": chunks,
    }
    trace_sha = _sha(json.dumps(trace, sort_keys=True).encode())
    direct = np.zeros(render_frames, dtype=np.float64)
    direct[48 : input_frames] = (
        motor.astype(np.float64) + propeller.astype(np.float64)
    )[: input_frames - 48] * 0.5
    tail_time = np.arange(render_frames - input_frames) / sample_rate
    efx = direct.copy()
    java = direct.copy()
    efx[input_frames:] = 300 * np.exp(-tail_time / 0.8) * np.sin(
        2 * np.pi * 700 * tail_time
    )
    java[input_frames:] = 240 * np.exp(-tail_time / 1.1) * np.sin(
        2 * np.pi * 420 * tail_time
    )
    sidecars = {
        "dry": np.rint(direct).astype("<i2").tobytes(),
        "efx": np.rint(efx).astype("<i2").tobytes(),
        "java_fdn": np.rint(java).astype("<i2").tobytes(),
    }
    report = {
        "schema_version": 1,
        "status": "valid-controlled-backend-transfer",
        "render_format": "s16le-mono-48000",
        "input_frames": input_frames,
        "tail_frames": verify.TAIL_FRAMES,
        "render_frames": render_frames,
        "doppler_trace_report_sha256": trace_sha,
        "motor_sequence": 1,
        "motor_pcm_sha256": _sha(motor_pcm),
        "propeller_sequence": 0,
        "propeller_pcm_sha256": _sha(propeller_pcm),
        "snapshot_generation": 4,
        "rt60_seconds": {"low": 1.4, "mid": 1.0, "high": 0.6},
        "wet_gain": 0.2,
        "transition_seconds": 0.2,
        "minecraft_context_unchanged": True,
        "minecraft_device_unchanged": True,
        "minecraft_sound_thread_unchanged": True,
        "production_parameter_mapping_reused": True,
        "production_java_fdn_reused": True,
        "physical_playback_device_opened": False,
        "capture_device_opened": False,
        "real_audio_capture": False,
        "release_calibrated": False,
        "minecraft_sound_thread": "Sound engine",
        "loopback_worker_thread": "Dronecraft backend transfer probe",
    }
    for name, data in sidecars.items():
        report[f"{name}_pcm_bytes"] = len(data)
        report[f"{name}_pcm_sha256"] = _sha(data)
    return report, sidecars, trace, trace_pcm, trace_sha


def run_verify(report, sidecars, trace, trace_pcm, trace_sha):
    return verify.verify(
        report,
        "a" * 64,
        sidecars["dry"],
        sidecars["efx"],
        sidecars["java_fdn"],
        trace,
        trace_sha,
        trace_pcm,
    )


class OpenAlBackendTransferVerifyTest(unittest.TestCase):
    def test_accepts_distinct_decaying_backend_tails(self):
        report, sidecars, trace, trace_pcm, trace_sha = fixture()
        result = run_verify(
            report, sidecars, trace, trace_pcm, trace_sha
        )
        self.assertTrue(result["gates"]["efx_tail_resolved_and_decaying"])

    def test_rejects_detached_input_hash(self):
        report, sidecars, trace, trace_pcm, trace_sha = fixture()
        report["motor_pcm_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "input detached"):
            run_verify(report, sidecars, trace, trace_pcm, trace_sha)

    def test_rejects_tampered_sidecar(self):
        report, sidecars, trace, trace_pcm, trace_sha = fixture()
        changed = dict(sidecars)
        payload = bytearray(changed["efx"])
        payload[20] ^= 1
        changed["efx"] = bytes(payload)
        with self.assertRaisesRegex(ValueError, "PCM identity changed"):
            run_verify(report, changed, trace, trace_pcm, trace_sha)

    def test_rejects_missing_efx_tail(self):
        report, sidecars, trace, trace_pcm, trace_sha = fixture()
        changed = dict(sidecars)
        changed["efx"] = changed["dry"]
        report["efx_pcm_sha256"] = _sha(changed["efx"])
        with self.assertRaisesRegex(ValueError, "not distinct"):
            run_verify(report, changed, trace, trace_pcm, trace_sha)

    def test_rejects_nondecaying_java_tail(self):
        report, sidecars, trace, trace_pcm, trace_sha = fixture()
        java = np.frombuffer(sidecars["java_fdn"], dtype="<i2").copy()
        java[verify.INPUT_FRAMES :] = 200
        changed = dict(sidecars)
        changed["java_fdn"] = java.tobytes()
        report["java_fdn_pcm_sha256"] = _sha(changed["java_fdn"])
        with self.assertRaisesRegex(ValueError, "tail did not decay"):
            run_verify(report, changed, trace, trace_pcm, trace_sha)

    def test_rejects_capture_overclaim(self):
        report, sidecars, trace, trace_pcm, trace_sha = fixture()
        report["real_audio_capture"] = True
        with self.assertRaisesRegex(ValueError, "must be False"):
            run_verify(report, sidecars, trace, trace_pcm, trace_sha)

    def test_rejects_trace_report_detachment(self):
        report, sidecars, trace, trace_pcm, trace_sha = fixture()
        report["doppler_trace_report_sha256"] = "f" * 64
        with self.assertRaisesRegex(ValueError, "trace binding"):
            run_verify(report, sidecars, trace, trace_pcm, trace_sha)


if __name__ == "__main__":
    unittest.main()
