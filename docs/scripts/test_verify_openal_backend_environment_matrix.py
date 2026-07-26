from __future__ import annotations

import copy
import hashlib
import json
import unittest

import numpy as np

import verify_openal_backend_environment_matrix as verify


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fixture():
    time = np.arange(verify.INPUT_FRAMES) / verify.SAMPLE_RATE
    motor = np.rint(
        1200 * np.sin(2 * np.pi * 117 * time)
    ).astype("<i2")
    propeller = np.rint(
        800 * np.sin(2 * np.pi * 353 * time + 0.4)
    ).astype("<i2")
    motor_pcm = motor.tobytes()
    propeller_pcm = propeller.tobytes()
    trace_pcm = propeller_pcm + motor_pcm
    trace = {
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
    trace_sha = _sha(json.dumps(trace, sort_keys=True).encode())
    controls = [
        ("closed", (6.0, 4.0, 2.4), 0.39),
        ("partial", (3.2, 1.5, 0.8), 0.38),
        ("open", (2.0, 0.8, 0.4), 0.37),
    ]
    portal_environments = [
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
        "source": "integrated-client-world",
        "backend_matrix_environments": portal_environments,
    }
    portal_sha = _sha(json.dumps(portal, sort_keys=True).encode())
    direct = np.zeros(verify.RENDER_FRAMES, dtype=np.float64)
    direct[48 : verify.INPUT_FRAMES] = (
        motor.astype(np.float64) + propeller.astype(np.float64)
    )[: verify.INPUT_FRAMES - 48] * 0.5
    tail_time = np.arange(
        verify.RENDER_FRAMES - verify.INPUT_FRAMES
    ) / verify.SAMPLE_RATE
    environments = []
    matrix = bytearray()
    for index, (name, rt60, wet) in enumerate(controls):
        outputs = []
        for backend in verify.BACKEND_ORDER:
            signal = direct.copy()
            if backend != "dry":
                scale = 300.0 if backend == "efx" else 450.0
                decay = rt60[1] / 5.0
                signal[verify.INPUT_FRAMES :] = (
                    scale
                    * wet
                    * np.exp(-tail_time / decay)
                    * np.sin(
                        2 * np.pi
                        * (700 if backend == "efx" else 420)
                        * tail_time
                    )
                )
            payload = np.rint(signal).astype("<i2").tobytes()
            outputs.append(
                {
                    "backend": backend,
                    "pcm_offset": len(matrix),
                    "pcm_bytes": len(payload),
                    "pcm_sha256": _sha(payload),
                }
            )
            matrix.extend(payload)
        environments.append(
            {
                **copy.deepcopy(portal_environments[index]),
                "snapshot_generation": index + 10,
                "transition_seconds": 0.2,
                "outputs": outputs,
            }
        )
    matrix_bytes = bytes(matrix)
    report = {
        "schema_version": 1,
        "status": "valid-backend-environment-matrix",
        "render_format": "s16le-mono-48000",
        "input_frames": verify.INPUT_FRAMES,
        "tail_frames": verify.RENDER_FRAMES - verify.INPUT_FRAMES,
        "render_frames_per_output": verify.RENDER_FRAMES,
        "doppler_trace_report_sha256": trace_sha,
        "portal_report_sha256": portal_sha,
        "motor_sequence": 1,
        "motor_pcm_sha256": _sha(motor_pcm),
        "propeller_sequence": 0,
        "propeller_pcm_sha256": _sha(propeller_pcm),
        "matrix_pcm_bytes": len(matrix_bytes),
        "matrix_pcm_sha256": _sha(matrix_bytes),
        "environments": environments,
        "production_parameter_mapping_reused": True,
        "production_java_fdn_reused": True,
        "minecraft_context_unchanged": True,
        "minecraft_device_unchanged": True,
        "physical_playback_device_opened": False,
        "capture_device_opened": False,
        "real_audio_capture": False,
        "release_calibrated": False,
    }
    return (
        report,
        matrix_bytes,
        trace,
        trace_pcm,
        trace_sha,
        portal,
        portal_sha,
    )


def run_verify(values):
    report, matrix, trace, trace_pcm, trace_sha, portal, portal_sha = values
    return verify.verify(
        report,
        "a" * 64,
        matrix,
        trace,
        trace_sha,
        trace_pcm,
        portal,
        portal_sha,
    )


class OpenAlBackendEnvironmentMatrixVerifyTest(unittest.TestCase):
    def test_accepts_monotonic_three_environment_matrix(self):
        result = run_verify(fixture())
        self.assertTrue(
            result["gates"]["efx_tail_monotonic_with_portal_opening"]
        )

    def test_rejects_portal_control_detachment(self):
        values = list(fixture())
        values[0]["environments"][1]["rt60_seconds"]["mid"] += 0.1
        with self.assertRaisesRegex(ValueError, "controls detached"):
            run_verify(values)

    def test_rejects_pcm_tamper(self):
        values = list(fixture())
        changed = bytearray(values[1])
        changed[100] ^= 1
        values[1] = bytes(changed)
        with self.assertRaisesRegex(ValueError, "PCM identity"):
            run_verify(values)

    def test_rejects_noncontiguous_output(self):
        values = list(fixture())
        values[0]["environments"][1]["outputs"][0]["pcm_offset"] += 2
        with self.assertRaisesRegex(ValueError, "not contiguous"):
            run_verify(values)

    def test_rejects_environment_order_change(self):
        values = list(fixture())
        values[0]["environments"][0]["name"] = "open"
        with self.assertRaisesRegex(ValueError, "environment order"):
            run_verify(values)

    def test_rejects_missing_java_tail(self):
        values = list(fixture())
        report, matrix = values[0], bytearray(values[1])
        output = report["environments"][2]["outputs"][2]
        dry = report["environments"][2]["outputs"][0]
        dry_payload = matrix[
            dry["pcm_offset"] : dry["pcm_offset"] + dry["pcm_bytes"]
        ]
        start = output["pcm_offset"]
        matrix[start : start + output["pcm_bytes"]] = dry_payload
        output["pcm_sha256"] = _sha(dry_payload)
        values[1] = bytes(matrix)
        report["matrix_pcm_sha256"] = _sha(values[1])
        with self.assertRaisesRegex(ValueError, "tail is unresolved"):
            run_verify(values)

    def test_rejects_dynamic_transition_overclaim(self):
        values = list(fixture())
        values[0]["real_audio_capture"] = True
        with self.assertRaisesRegex(ValueError, "must be False"):
            run_verify(values)


if __name__ == "__main__":
    unittest.main()
