from __future__ import annotations

import copy
import hashlib
import json
import unittest

import numpy as np

import verify_openal_backend_transition as verify


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fixture():
    frames = verify.SEGMENT_FRAMES
    chunks = []
    trace_pcm = bytearray()
    layer_payloads = {"motor": [], "propeller": []}
    for segment in range(verify.SEGMENT_COUNT):
        time = (np.arange(frames) + segment * frames) / verify.SAMPLE_RATE
        for layer, frequency, amplitude, phase in (
            ("propeller", 353.0, 800.0, 0.4),
            ("motor", 117.0, 1200.0, 0.0),
        ):
            signal = np.rint(
                amplitude * np.sin(2 * np.pi * frequency * time + phase)
            ).astype("<i2")
            payload = signal.tobytes()
            sequence = segment * 2 + (0 if layer == "propeller" else 1)
            chunks.append(
                {
                    "sequence": sequence,
                    "layer": layer,
                    "pcm_offset": len(trace_pcm),
                    "pcm_bytes": len(payload),
                    "pcm_sha256": _sha(payload),
                }
            )
            trace_pcm.extend(payload)
            layer_payloads[layer].append(payload)
    trace_bytes = bytes(trace_pcm)
    trace = {
        "status": "valid-doppler-production-chunk-trace",
        "pcm_sha256": _sha(trace_bytes),
        "chunks": chunks,
    }
    trace_sha = _sha(json.dumps(trace, sort_keys=True).encode())
    controls = [
        ("closed", (6.0, 4.0, 2.4), 0.39),
        ("partial", (3.2, 1.5, 0.8), 0.38),
        ("open", (2.0, 0.8, 0.4), 0.37),
    ]
    portal_controls = [
        {
            "name": name,
            "rt60_seconds": {
                "low": rt60[0],
                "mid": rt60[1],
                "high": rt60[2],
            },
            "wet_gain": wet,
        }
        for name, rt60, wet in controls
    ]
    portal = {
        "status": "valid-diagnostic",
        "backend_matrix_environments": portal_controls,
    }
    portal_sha = _sha(json.dumps(portal, sort_keys=True).encode())
    environments = []
    for segment, name in enumerate(verify.ENVIRONMENT_ORDER):
        source = next(item for item in portal_controls if item["name"] == name)
        environments.append(
            {
                "segment": segment,
                "name": name,
                "snapshot_generation": segment + 10,
                "rt60_seconds": copy.deepcopy(source["rt60_seconds"]),
                "wet_gain": source["wet_gain"],
                "transition_seconds": 0.0 if segment == 0 else 0.2,
            }
        )
    motor = np.frombuffer(
        b"".join(layer_payloads["motor"]), dtype="<i2"
    ).astype(np.float64)
    propeller = np.frombuffer(
        b"".join(layer_payloads["propeller"]), dtype="<i2"
    ).astype(np.float64)
    expected = motor + propeller
    dry = np.zeros(verify.RENDER_FRAMES, dtype=np.float64)
    dry[48:] = expected[:-48] * 0.5
    amplitudes = {
        "efx": [100.0, 70.0, 50.0, 90.0],
        "java_fdn": [150.0, 95.0, 70.0, 130.0],
    }
    payloads = {"dry": np.rint(dry).astype("<i2").tobytes()}
    time = np.arange(verify.RENDER_FRAMES) / verify.SAMPLE_RATE
    for backend, values in amplitudes.items():
        residual = np.zeros(verify.RENDER_FRAMES)
        for segment, amplitude in enumerate(values):
            start = segment * frames
            end = (segment + 1) * frames
            residual[start:end] = amplitude * np.sin(
                2 * np.pi
                * (700.0 if backend == "efx" else 420.0)
                * time[start:end]
            )
        payloads[backend] = np.rint(dry + residual).astype("<i2").tobytes()
    inputs = []
    for layer in ("motor", "propeller"):
        for chunk in sorted(
            [item for item in chunks if item["layer"] == layer],
            key=lambda item: item["sequence"],
        ):
            inputs.append(
                {
                    "layer": layer,
                    "sequence": chunk["sequence"],
                    "pcm_sha256": chunk["pcm_sha256"],
                }
            )
    report = {
        "schema_version": 1,
        "status": "valid-backend-dynamic-transition",
        "render_format": "s16le-mono-48000",
        "segment_frames": frames,
        "segment_count": verify.SEGMENT_COUNT,
        "render_frames": verify.RENDER_FRAMES,
        "boundary_frames": list(verify.BOUNDARIES),
        "doppler_trace_report_sha256": trace_sha,
        "portal_report_sha256": portal_sha,
        "motor_pcm_sha256": _sha(b"".join(layer_payloads["motor"])),
        "propeller_pcm_sha256": _sha(
            b"".join(layer_payloads["propeller"])
        ),
        "inputs": inputs,
        "environments": environments,
        "efx_parameter_update": "immediate-production-write",
        "java_fdn_parameter_update":
            "production-0.2-second-exponential-smoothing",
        "fdn_tail_cleared_at_boundary": False,
        "minecraft_context_unchanged": True,
        "minecraft_device_unchanged": True,
        "minecraft_sound_thread_unchanged": True,
        "minecraft_sound_thread": "Sound engine",
        "loopback_worker_thread": "Dronecraft backend transition probe",
        "physical_playback_device_opened": False,
        "capture_device_opened": False,
        "real_audio_capture": False,
        "release_calibrated": False,
    }
    for name, payload in payloads.items():
        prefix = "java_fdn" if name == "java_fdn" else name
        report[f"{prefix}_pcm_bytes"] = len(payload)
        report[f"{prefix}_pcm_sha256"] = _sha(payload)
    return (
        report,
        payloads,
        trace,
        trace_bytes,
        trace_sha,
        portal,
        portal_sha,
    )


def run_verify(values):
    report, payloads, trace, trace_pcm, trace_sha, portal, portal_sha = values
    return verify.verify(
        report,
        "a" * 64,
        payloads["dry"],
        payloads["efx"],
        payloads["java_fdn"],
        trace,
        trace_sha,
        trace_pcm,
        portal,
        portal_sha,
    )


def replace_payload(values, backend, samples):
    prefix = "java_fdn" if backend == "java_fdn" else backend
    payload = samples.astype("<i2").tobytes()
    values[1][backend] = payload
    values[0][f"{prefix}_pcm_sha256"] = _sha(payload)


class OpenAlBackendTransitionVerifyTest(unittest.TestCase):
    def test_accepts_bounded_continuous_parameter_updates(self):
        result = run_verify(fixture())
        self.assertTrue(
            result["gates"]["efx_no_resolved_software_boundary_click"]
        )

    def test_rejects_input_manifest_detachment(self):
        values = list(fixture())
        values[0]["inputs"][0]["sequence"] = 99
        with self.assertRaisesRegex(ValueError, "input detached"):
            run_verify(values)

    def test_rejects_portal_control_detachment(self):
        values = list(fixture())
        values[0]["environments"][1]["wet_gain"] -= 0.1
        with self.assertRaisesRegex(ValueError, "controls detached"):
            run_verify(values)

    def test_rejects_pcm_tamper(self):
        values = list(fixture())
        payload = bytearray(values[1]["efx"])
        payload[20] ^= 1
        values[1]["efx"] = bytes(payload)
        with self.assertRaisesRegex(ValueError, "PCM identity changed"):
            run_verify(values)

    def test_rejects_resolved_boundary_click(self):
        values = list(fixture())
        efx = np.frombuffer(values[1]["efx"], dtype="<i2").copy()
        efx[verify.BOUNDARIES[0]] += 5_000
        replace_payload(values, "efx", efx)
        with self.assertRaisesRegex(ValueError, "boundary click"):
            run_verify(values)

    def test_rejects_backend_without_environment_response(self):
        values = list(fixture())
        dry = np.frombuffer(values[1]["dry"], dtype="<i2").astype(np.float64)
        time = np.arange(verify.RENDER_FRAMES) / verify.SAMPLE_RATE
        unchanged = dry + 70 * np.sin(2 * np.pi * 700 * time)
        replace_payload(values, "efx", np.rint(unchanged))
        with self.assertRaisesRegex(ValueError, "transition sequence"):
            run_verify(values)

    def test_rejects_capture_overclaim(self):
        values = list(fixture())
        values[0]["real_audio_capture"] = True
        with self.assertRaisesRegex(ValueError, "must be False"):
            run_verify(values)


if __name__ == "__main__":
    unittest.main()
