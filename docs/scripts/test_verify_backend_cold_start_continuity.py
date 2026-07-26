from __future__ import annotations

import copy
import hashlib
import unittest

import numpy as np

import verify_backend_cold_start_continuity as verify


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fixture():
    frames = verify.RENDER_FRAMES
    time = np.arange(frames) / verify.SAMPLE_RATE
    dry_values = 1_200.0 * np.sin(2.0 * np.pi * 300.0 * time)
    warm_values = dry_values + 200.0 * np.sin(
        2.0 * np.pi * 100.0 * time
    )
    cold_values = dry_values + 50.0 * np.sin(
        2.0 * np.pi * 100.0 * time
    )
    payloads = {
        "dry": np.rint(dry_values).astype("<i2").tobytes(),
        "warm": np.rint(warm_values).astype("<i2").tobytes(),
        "cold": np.rint(cold_values).astype("<i2").tobytes(),
    }
    wet_values = np.zeros(frames, dtype=np.int16)
    windows = []
    for index, (start, end) in enumerate(verify.FALLBACK_WINDOWS):
        first = start + verify.EXPECTED_FDN_DELAYS[0]
        wet_time = np.arange(end - first) / verify.SAMPLE_RATE
        wet_values[first:end] = np.rint(
            40.0 * np.sin(2.0 * np.pi * 200.0 * wet_time + 0.2)
        ).astype(np.int16)
        if wet_values[first] == 0:
            wet_values[first] = 1
        windows.append(
            {
                "fault_stage": verify.FAULT_STAGES[index],
                "start_frame": start,
                "end_frame": end,
                "first_nonzero_wet_frame": first,
                "onset_delay_samples": verify.EXPECTED_FDN_DELAYS[0],
                "fdn_delay_samples": list(verify.EXPECTED_FDN_DELAYS),
            }
        )
    payloads["java_wet"] = wet_values.astype("<i2").tobytes()

    trace_pcm = bytearray()
    trace_chunks = []
    combined = {"motor": bytearray(), "propeller": bytearray()}
    inputs = []
    for segment in range(verify.SEGMENT_COUNT):
        for layer, amplitude, frequency in (
            ("motor", 700.0, 117.0),
            ("propeller", 500.0, 353.0),
        ):
            chunk_time = (
                np.arange(verify.SEGMENT_FRAMES)
                + segment * verify.SEGMENT_FRAMES
            ) / verify.SAMPLE_RATE
            chunk = np.rint(
                amplitude
                * np.sin(2.0 * np.pi * frequency * chunk_time)
            ).astype("<i2").tobytes()
            sequence = segment * 2 + (0 if layer == "motor" else 1)
            item = {
                "layer": layer,
                "sequence": sequence,
                "pcm_offset": len(trace_pcm),
                "pcm_bytes": len(chunk),
                "pcm_sha256": _sha(chunk),
            }
            trace_chunks.append(item)
            inputs.append(
                {
                    "layer": layer,
                    "sequence": sequence,
                    "pcm_sha256": _sha(chunk),
                }
            )
            trace_pcm.extend(chunk)
            combined[layer].extend(chunk)
    trace_pcm = bytes(trace_pcm)
    trace = {
        "status": "valid-doppler-production-chunk-trace",
        "pcm_sha256": _sha(trace_pcm),
        "chunks": trace_chunks,
    }
    environments = []
    for segment in range(verify.SEGMENT_COUNT):
        environments.append(
            {
                "segment": segment,
                "snapshot_generation": 10 + segment,
                "rt60_seconds": {
                    "low": 6.0 - segment,
                    "mid": 4.0 - 0.5 * segment,
                    "high": 2.0 - 0.25 * segment,
                },
                "wet_gain": 0.39 - 0.01 * segment,
            }
        )
    transition = {
        "status": "valid-backend-dynamic-transition",
        "inputs": inputs,
        "motor_pcm_sha256": _sha(bytes(combined["motor"])),
        "propeller_pcm_sha256": _sha(bytes(combined["propeller"])),
        "dry_pcm_sha256": _sha(payloads["dry"]),
        "environments": copy.deepcopy(environments),
    }
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
        "status": "valid-openal-efx-fault-failover",
        "fault_cycles": cycles,
    }
    transition_sha = "1" * 64
    trace_sha = "2" * 64
    failover_sha = "3" * 64
    warm_sha = "4" * 64
    warm_report = {
        "status": "valid-backend-failover-continuity-diagnostic",
        "warm_state_source_renders": True,
        "cold_start_measured": False,
        "output_pcm_sha256": _sha(payloads["warm"]),
        "source_transition_report_sha256": transition_sha,
        "source_failover_report_sha256": failover_sha,
    }
    report = {
        "schema_version": 1,
        "status": "valid-backend-cold-start-render",
        "render_format": "s16le-mono-48000",
        "sample_rate_hz": verify.SAMPLE_RATE,
        "render_frames": frames,
        "source_transition_report_sha256": transition_sha,
        "source_trace_report_sha256": trace_sha,
        "source_trace_pcm_sha256": _sha(trace_pcm),
        "source_failover_report_sha256": failover_sha,
        "motor_pcm_sha256": transition["motor_pcm_sha256"],
        "propeller_pcm_sha256": transition["propeller_pcm_sha256"],
        "cold_pcm_bytes": len(payloads["cold"]),
        "cold_pcm_sha256": _sha(payloads["cold"]),
        "dry_pcm_bytes": len(payloads["dry"]),
        "dry_pcm_sha256": _sha(payloads["dry"]),
        "java_wet_pcm_bytes": len(payloads["java_wet"]),
        "java_wet_pcm_sha256": _sha(payloads["java_wet"]),
        "environments": copy.deepcopy(environments),
        "java_fallback_windows": windows,
        "efx_resources_created_at_recovery": True,
        "java_fdn_created_at_fallback": True,
        "backend_state_preheated": False,
        "splice_method": "cold-start-exclusive-owner-no-crossfade",
        "al_error": 0,
        "alc_error": 0,
        "dry_al_error": 0,
        "dry_alc_error": 0,
        "exclusive_wet_owner": True,
        "physical_endpoint_opened": False,
        "captures_audio": False,
        "release_calibrated": False,
    }
    return {
        "report": report,
        "payloads": payloads,
        "transition": transition,
        "transition_sha": transition_sha,
        "trace": trace,
        "trace_sha": trace_sha,
        "trace_pcm": trace_pcm,
        "failover": failover,
        "failover_sha": failover_sha,
        "warm_report": warm_report,
        "warm_sha": warm_sha,
    }


def run_verify(values):
    return verify.verify(
        values["report"],
        "5" * 64,
        values["payloads"]["cold"],
        values["payloads"]["dry"],
        values["payloads"]["java_wet"],
        values["transition"],
        values["transition_sha"],
        values["payloads"]["dry"],
        values["trace"],
        values["trace_sha"],
        values["trace_pcm"],
        values["failover"],
        values["failover_sha"],
        values["warm_report"],
        values["warm_sha"],
        values["payloads"]["warm"],
    )


class BackendColdStartContinuityVerifyTest(unittest.TestCase):
    def test_accepts_cold_start_and_requires_preroll_research(self):
        result = run_verify(fixture())
        self.assertTrue(
            result["gates"]["java_history_preroll_research_required"]
        )
        self.assertTrue(
            result["gates"]["efx_recovery_tail_policy_required"]
        )
        self.assertFalse(
            result["gates"]["cold_and_warm_tail_equivalent"]
        )

    def test_rejects_source_report_detachment(self):
        values = fixture()
        values["report"]["source_trace_report_sha256"] = "f" * 64
        with self.assertRaisesRegex(ValueError, "source report binding"):
            run_verify(values)

    def test_rejects_trace_pcm_tamper(self):
        values = fixture()
        payload = bytearray(values["trace_pcm"])
        payload[20] ^= 1
        values["trace_pcm"] = bytes(payload)
        values["report"]["source_trace_pcm_sha256"] = _sha(
            values["trace_pcm"]
        )
        values["trace"]["pcm_sha256"] = _sha(values["trace_pcm"])
        with self.assertRaisesRegex(ValueError, "source PCM hash"):
            run_verify(values)

    def test_rejects_dry_control_detachment(self):
        values = fixture()
        dry = bytearray(values["payloads"]["dry"])
        dry[30] ^= 1
        values["payloads"]["dry"] = bytes(dry)
        values["report"]["dry_pcm_sha256"] = _sha(values["payloads"]["dry"])
        with self.assertRaisesRegex(ValueError, "dry control detached"):
            run_verify(values)

    def test_rejects_cold_pcm_tamper(self):
        values = fixture()
        cold = bytearray(values["payloads"]["cold"])
        cold[40] ^= 1
        values["payloads"]["cold"] = bytes(cold)
        with self.assertRaisesRegex(ValueError, "cold PCM identity"):
            run_verify(values)

    def test_rejects_java_wet_outside_fallback(self):
        values = fixture()
        wet = np.frombuffer(
            values["payloads"]["java_wet"], dtype="<i2"
        ).copy()
        wet[100] = 5
        values["payloads"]["java_wet"] = wet.tobytes()
        values["report"]["java_wet_pcm_sha256"] = _sha(
            values["payloads"]["java_wet"]
        )
        with self.assertRaisesRegex(ValueError, "escaped fallback"):
            run_verify(values)

    def test_rejects_java_onset_detachment(self):
        values = fixture()
        values["report"]["java_fallback_windows"][0][
            "onset_delay_samples"
        ] += 1
        with self.assertRaisesRegex(ValueError, "wet onset detached"):
            run_verify(values)

    def test_rejects_double_wet_failover_evidence(self):
        values = fixture()
        values["failover"]["fault_cycles"][0]["fallback"][
            "double_wet_path"
        ] = True
        with self.assertRaisesRegex(ValueError, "exclusive ownership"):
            run_verify(values)

    def test_rejects_warm_control_detachment(self):
        values = fixture()
        values["warm_report"]["output_pcm_sha256"] = "f" * 64
        with self.assertRaisesRegex(ValueError, "warm-state control"):
            run_verify(values)

    def test_rejects_capture_overclaim(self):
        values = fixture()
        values["report"]["captures_audio"] = True
        with self.assertRaisesRegex(ValueError, "must be False"):
            run_verify(values)

    def test_rejects_openal_error(self):
        values = fixture()
        values["report"]["al_error"] = 1
        with self.assertRaisesRegex(ValueError, "OpenAL error"):
            run_verify(values)

    def test_rejects_total_output_dropout(self):
        values = fixture()
        cold = np.frombuffer(values["payloads"]["cold"], dtype="<i2").copy()
        boundary = verify.BOUNDARIES[0]
        cold[boundary : boundary + 960] = 0
        values["payloads"]["cold"] = cold.tobytes()
        values["report"]["cold_pcm_sha256"] = _sha(
            values["payloads"]["cold"]
        )
        with self.assertRaisesRegex(ValueError, "total-output dropout"):
            run_verify(values)


if __name__ == "__main__":
    unittest.main()
