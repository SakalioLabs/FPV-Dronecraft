from __future__ import annotations

import copy
import hashlib
import unittest

import numpy as np

import verify_fdn_history_preroll_sweep as verify


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _metrics(candidate: np.ndarray, reference: np.ndarray):
    candidate_float = candidate.astype(np.float64)
    reference_float = reference.astype(np.float64)
    correlation = float(
        np.dot(candidate_float, reference_float)
        / np.sqrt(
            np.dot(candidate_float, candidate_float)
            * np.dot(reference_float, reference_float)
        )
    )
    rms = lambda value: float(np.sqrt(np.mean(np.square(value))))
    return (
        correlation,
        rms(candidate_float) / rms(reference_float),
        rms(candidate_float - reference_float) / rms(reference_float),
    )


def fixture():
    trace_pcm = bytearray()
    trace_chunks = []
    inputs = []
    combined = {"motor": bytearray(), "propeller": bytearray()}
    for segment in range(verify.SEGMENT_COUNT):
        for layer, frequency, amplitude in (
            ("motor", 117.0, 700.0),
            ("propeller", 353.0, 500.0),
        ):
            time = (
                np.arange(verify.SEGMENT_FRAMES)
                + segment * verify.SEGMENT_FRAMES
            ) / verify.SAMPLE_RATE
            payload = np.rint(
                amplitude * np.sin(2.0 * np.pi * frequency * time)
            ).astype("<i2").tobytes()
            sequence = segment * 2 + (0 if layer == "motor" else 1)
            chunk = {
                "layer": layer,
                "sequence": sequence,
                "pcm_offset": len(trace_pcm),
                "pcm_bytes": len(payload),
                "pcm_sha256": _sha(payload),
            }
            trace_chunks.append(chunk)
            inputs.append(
                {
                    "layer": layer,
                    "sequence": sequence,
                    "pcm_sha256": _sha(payload),
                }
            )
            trace_pcm.extend(payload)
            combined[layer].extend(payload)
    trace_pcm = bytes(trace_pcm)
    trace = {
        "status": "valid-doppler-production-chunk-trace",
        "pcm_sha256": _sha(trace_pcm),
        "chunks": trace_chunks,
    }
    environments = []
    for index in range(verify.SEGMENT_COUNT):
        environments.append(
            {
                "segment": index,
                "snapshot_generation": 10 + index,
                "rt60_seconds": {
                    "low": 6.0 - index,
                    "mid": 4.0 - 0.5 * index,
                    "high": 2.0 - 0.25 * index,
                },
                "wet_gain": 0.39 - 0.01 * index,
            }
        )
    transition = {
        "status": "valid-backend-dynamic-transition",
        "inputs": inputs,
        "motor_pcm_sha256": _sha(bytes(combined["motor"])),
        "propeller_pcm_sha256": _sha(bytes(combined["propeller"])),
        "environments": environments,
    }

    sidecar = bytearray()
    references = []
    cases = []
    scales = {0: 0.5, 50: 0.6, 100: 0.7, 250: 0.85, 500: 1.0, 1000: 1.0}
    for env_index, environment in enumerate(verify.ENVIRONMENTS):
        time = np.arange(verify.EVALUATION_FRAMES) / verify.SAMPLE_RATE
        reference = np.rint(
            (300.0 + env_index * 40.0)
            * np.sin(2.0 * np.pi * (180.0 + env_index * 30.0) * time + 0.2)
        ).astype("<i2")
        payload = reference.tobytes()
        references.append(
            {
                "environment": environment,
                "pre_roll_ms": verify.REFERENCE_HISTORY_MS,
                "pcm_offset": len(sidecar),
                "pcm_bytes": len(payload),
                "pcm_sha256": _sha(payload),
            }
        )
        sidecar.extend(payload)
        for pre_roll in verify.PREROLL_MILLISECONDS:
            candidate = np.rint(reference.astype(np.float64) * scales[pre_roll]).astype(
                "<i2"
            )
            candidate_payload = candidate.tobytes()
            correlation, rms_ratio, normalized_rmse = _metrics(
                candidate, reference
            )
            p99 = 1.0 + pre_roll / 1_000.0
            item = {
                "environment": environment,
                "pre_roll_ms": pre_roll,
                "pcm_offset": len(sidecar),
                "pcm_bytes": len(candidate_payload),
                "pcm_sha256": _sha(candidate_payload),
                "correlation": correlation,
                "rms_ratio": rms_ratio,
                "normalized_rmse": normalized_rmse,
                "p50_ms": p99 * 0.7,
                "p95_ms": p99 * 0.9,
                "p99_ms": p99,
            }
            item["passes"] = verify._passes(item)
            cases.append(item)
            sidecar.extend(candidate_payload)
    sidecar = bytes(sidecar)
    transition_sha = "1" * 64
    trace_sha = "2" * 64
    report = {
        "schema_version": 1,
        "status": "valid-fdn-history-preroll-sweep",
        "sample_rate_hz": verify.SAMPLE_RATE,
        "history_end_frame": verify.HISTORY_END_FRAME,
        "evaluation_frames": verify.EVALUATION_FRAMES,
        "reference_history_ms": verify.REFERENCE_HISTORY_MS,
        "source_transition_report_sha256": transition_sha,
        "source_trace_report_sha256": trace_sha,
        "source_trace_pcm_sha256": _sha(trace_pcm),
        "motor_pcm_sha256": transition["motor_pcm_sha256"],
        "propeller_pcm_sha256": transition["propeller_pcm_sha256"],
        "sidecar_sha256": _sha(sidecar),
        "sidecar_format": "s16le-mono-48000",
        "sidecar_bytes": len(sidecar),
        "warmup_iterations": 10,
        "measured_iterations": 50,
        "thresholds": {
            "minimum_correlation": verify.MINIMUM_CORRELATION,
            "minimum_rms_ratio": verify.MINIMUM_RMS_RATIO,
            "maximum_rms_ratio": verify.MAXIMUM_RMS_RATIO,
            "maximum_normalized_rmse": verify.MAXIMUM_NORMALIZED_RMSE,
            "maximum_p99_ms": verify.MAXIMUM_P99_MS,
        },
        "environments": [
            {
                "name": name,
                "rt60_seconds": copy.deepcopy(
                    environments[index]["rt60_seconds"]
                ),
                "wet_gain": environments[index]["wet_gain"],
            }
            for index, name in enumerate(verify.ENVIRONMENTS)
        ],
        "references": references,
        "cases": cases,
        "selected_pre_roll_ms": 500,
        "selection_available": True,
        "mixed_input_history_only": True,
        "physical_endpoint_opened": False,
        "captures_audio": False,
        "release_calibrated": False,
    }
    return {
        "report": report,
        "sidecar": sidecar,
        "transition": transition,
        "transition_sha": transition_sha,
        "trace": trace,
        "trace_sha": trace_sha,
        "trace_pcm": trace_pcm,
    }


def run_verify(values):
    return verify.verify(
        values["report"],
        "3" * 64,
        values["sidecar"],
        values["transition"],
        values["transition_sha"],
        values["trace"],
        values["trace_sha"],
        values["trace_pcm"],
    )


class FdnHistoryPrerollSweepVerifyTest(unittest.TestCase):
    def test_accepts_minimum_500_ms_selection(self):
        result = run_verify(fixture())
        self.assertEqual(result["selected_pre_roll_ms"], 500)

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
        values["trace"]["pcm_sha256"] = _sha(values["trace_pcm"])
        values["report"]["source_trace_pcm_sha256"] = _sha(
            values["trace_pcm"]
        )
        with self.assertRaisesRegex(ValueError, "trace PCM hash"):
            run_verify(values)

    def test_rejects_environment_detachment(self):
        values = fixture()
        values["report"]["environments"][0]["wet_gain"] -= 0.1
        with self.assertRaisesRegex(ValueError, "wet control detached"):
            run_verify(values)

    def test_rejects_sidecar_tamper(self):
        values = fixture()
        payload = bytearray(values["sidecar"])
        payload[100] ^= 1
        values["sidecar"] = bytes(payload)
        with self.assertRaisesRegex(ValueError, "sidecar identity"):
            run_verify(values)

    def test_rejects_overlapping_range(self):
        values = fixture()
        values["report"]["cases"][0]["pcm_offset"] = values["report"][
            "references"
        ][0]["pcm_offset"]
        with self.assertRaisesRegex(ValueError, "ranges overlap"):
            run_verify(values)

    def test_rejects_metric_tamper(self):
        values = fixture()
        values["report"]["cases"][0]["correlation"] -= 0.1
        with self.assertRaisesRegex(ValueError, "correlation metric"):
            run_verify(values)

    def test_rejects_pass_flag_detachment(self):
        values = fixture()
        values["report"]["cases"][0]["passes"] = True
        with self.assertRaisesRegex(ValueError, "pass flag"):
            run_verify(values)

    def test_rejects_nonminimum_selection(self):
        values = fixture()
        values["report"]["selected_pre_roll_ms"] = 1000
        with self.assertRaisesRegex(ValueError, "minimum pass"):
            run_verify(values)

    def test_rejects_reduced_timing_samples(self):
        values = fixture()
        values["report"]["measured_iterations"] = 5
        with self.assertRaisesRegex(ValueError, "timing sample count"):
            run_verify(values)

    def test_rejects_invalid_timing_order(self):
        values = fixture()
        values["report"]["cases"][0]["p95_ms"] = 5.0
        with self.assertRaisesRegex(ValueError, "timing distribution"):
            run_verify(values)

    def test_rejects_capture_overclaim(self):
        values = fixture()
        values["report"]["captures_audio"] = True
        with self.assertRaisesRegex(ValueError, "must be False"):
            run_verify(values)

    def test_rejects_changed_threshold(self):
        values = fixture()
        values["report"]["thresholds"]["minimum_correlation"] = 0.5
        with self.assertRaisesRegex(ValueError, "threshold changed"):
            run_verify(values)

    def test_rejects_1000_ms_reference_mismatch(self):
        values = fixture()
        item = next(
            case
            for case in values["report"]["cases"]
            if case["environment"] == "closed"
            and case["pre_roll_ms"] == 1000
        )
        payload = np.frombuffer(values["sidecar"], dtype="<i2").copy()
        sample = item["pcm_offset"] // 2 + 10
        payload[sample] += 1
        values["sidecar"] = payload.tobytes()
        item["pcm_sha256"] = _sha(
            values["sidecar"][
                item["pcm_offset"] : item["pcm_offset"] + item["pcm_bytes"]
            ]
        )
        values["report"]["sidecar_sha256"] = _sha(values["sidecar"])
        with self.assertRaisesRegex(ValueError, "1000-ms candidate"):
            run_verify(values)


if __name__ == "__main__":
    unittest.main()
