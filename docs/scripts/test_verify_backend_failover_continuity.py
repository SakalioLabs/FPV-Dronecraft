from __future__ import annotations

import copy
import hashlib
import unittest

import numpy as np

import analyze_backend_failover_continuity as analyze
import verify_backend_failover_continuity as verify


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fixture():
    frames = verify.RENDER_FRAMES
    time = np.arange(frames) / verify.SAMPLE_RATE
    dry_signal = 1_200.0 * np.sin(2.0 * np.pi * 300.0 * time)
    efx_signal = dry_signal + 80.0 * np.sin(2.0 * np.pi * 200.0 * time)
    java_signal = dry_signal + 120.0 * np.sin(2.0 * np.pi * 100.0 * time)
    payloads = {
        "dry": np.rint(dry_signal).astype("<i2").tobytes(),
        "efx": np.rint(efx_signal).astype("<i2").tobytes(),
        "java_fdn": np.rint(java_signal).astype("<i2").tobytes(),
    }
    transition = {
        "schema_version": 1,
        "status": "valid-backend-dynamic-transition",
        "render_format": "s16le-mono-48000",
        "render_frames": frames,
    }
    for name, payload in payloads.items():
        transition[f"{name}_pcm_bytes"] = len(payload)
        transition[f"{name}_pcm_sha256"] = _sha(payload)
    cycles = []
    for stage in verify.FAULT_STAGES:
        cycles.append(
            {
                "stage": stage,
                "fallback": {
                    "backend": "JAVA_FDN",
                    "double_wet_path": False,
                },
                "recovery_runtime": {
                    "backend": "OPENAL_EFX",
                    "double_wet_path": False,
                },
            }
        )
    failover = {
        "schema_version": 1,
        "status": "valid-openal-efx-fault-failover",
        "fault_cycles": cycles,
        "captures_audio": False,
        "physical_endpoint_changed": False,
    }
    transition_sha = "1" * 64
    failover_sha = "2" * 64
    report, output = analyze.materialize(
        transition,
        transition_sha,
        payloads["dry"],
        payloads["efx"],
        payloads["java_fdn"],
        failover,
        failover_sha,
    )
    return (
        report,
        output,
        transition,
        payloads,
        failover,
        transition_sha,
        failover_sha,
    )


def run_verify(values):
    (
        report,
        output,
        transition,
        payloads,
        failover,
        transition_sha,
        failover_sha,
    ) = values
    return verify.verify(
        report,
        "3" * 64,
        output,
        transition,
        transition_sha,
        payloads["dry"],
        payloads["efx"],
        payloads["java_fdn"],
        failover,
        failover_sha,
    )


class BackendFailoverContinuityVerifyTest(unittest.TestCase):
    def test_accepts_exclusive_owner_fallback_splice(self):
        result = run_verify(fixture())
        self.assertEqual(result["backend_boundaries"], 6)
        self.assertTrue(result["exclusive_wet_owner"])

    def test_rejects_transition_report_detachment(self):
        values = list(fixture())
        values[0]["source_transition_report_sha256"] = "f" * 64
        with self.assertRaisesRegex(ValueError, "source report binding"):
            run_verify(values)

    def test_rejects_source_pcm_tamper(self):
        values = list(fixture())
        payload = bytearray(values[3]["efx"])
        payload[100] ^= 1
        values[3]["efx"] = bytes(payload)
        with self.assertRaisesRegex(ValueError, "source PCM binding"):
            run_verify(values)

    def test_rejects_fault_cycle_reordering(self):
        values = list(fixture())
        values[4]["fault_cycles"].reverse()
        with self.assertRaisesRegex(ValueError, "fault-cycle order"):
            run_verify(values)

    def test_rejects_double_wet_source_evidence(self):
        values = list(fixture())
        values[4]["fault_cycles"][0]["fallback"]["double_wet_path"] = True
        with self.assertRaisesRegex(ValueError, "exclusive ownership"):
            run_verify(values)

    def test_rejects_nonexclusive_output(self):
        values = list(fixture())
        output = np.frombuffer(values[1], dtype="<i2").copy()
        output[verify.FALLBACK_WINDOWS[0][0] + 10] += 1
        values[1] = output.tobytes()
        values[0]["output_pcm_sha256"] = _sha(values[1])
        with self.assertRaisesRegex(ValueError, "exclusive-owner splice"):
            run_verify(values)

    def test_rejects_boundary_metric_tamper(self):
        values = list(fixture())
        values[0]["boundaries"][0]["step_to_p99_ratio"] += 0.1
        with self.assertRaisesRegex(ValueError, "boundary metric"):
            run_verify(values)

    def test_rejects_capture_overclaim(self):
        values = list(fixture())
        values[0]["captures_audio"] = True
        with self.assertRaisesRegex(ValueError, "must be False"):
            run_verify(values)

    def test_rejects_cold_start_overclaim(self):
        values = list(fixture())
        values[0]["cold_start_measured"] = True
        with self.assertRaisesRegex(ValueError, "splice semantics"):
            run_verify(values)

    def test_rejects_disabled_positive_gate(self):
        values = list(fixture())
        values[0]["gates"]["exclusive_wet_owner"] = False
        with self.assertRaisesRegex(ValueError, "positive gate"):
            run_verify(values)


if __name__ == "__main__":
    unittest.main()
