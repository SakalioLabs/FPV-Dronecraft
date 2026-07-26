from __future__ import annotations

import copy
import hashlib
import json
import unittest

import numpy as np

import analyze_efx_recovery_slew as analyze
import verify_efx_recovery_slew as verify


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fixture():
    frames = verify.RENDER_FRAMES
    time = np.arange(frames, dtype=np.float64) / verify.SAMPLE_RATE
    dry = np.rint(
        3_000.0 * np.sin(2.0 * np.pi * 173.0 * time)
        + 1_000.0 * np.sin(2.0 * np.pi * 431.0 * time + 0.3)
    ).astype("<i2")
    wet = np.zeros(frames, dtype=np.int32)
    for boundary in verify.RECOVERY_FRAMES:
        wet[boundary - 100 : boundary + 500] = 600
    cold = np.clip(
        dry.astype(np.int32) + wet,
        -32_768,
        32_767,
    ).astype("<i2")
    warm = np.clip(
        dry.astype(np.int32) + wet * 2,
        -32_768,
        32_767,
    ).astype("<i2")
    java_wet = np.zeros(frames, dtype="<i2")
    cold_bytes = cold.tobytes()
    dry_bytes = dry.tobytes()
    java_wet_bytes = java_wet.tobytes()
    warm_bytes = warm.tobytes()
    d105 = {
        "status": "valid-backend-cold-start-render",
        "sample_rate_hz": verify.SAMPLE_RATE,
        "render_frames": frames,
        "cold_pcm_sha256": _sha(cold_bytes),
        "dry_pcm_sha256": _sha(dry_bytes),
        "java_wet_pcm_sha256": _sha(java_wet_bytes),
    }
    d104 = {
        "status": "valid-backend-failover-continuity-diagnostic",
        "output_pcm_sha256": _sha(warm_bytes),
    }
    d105_bytes = (
        json.dumps(d105, sort_keys=True) + "\n"
    ).encode()
    d104_bytes = (
        json.dumps(d104, sort_keys=True) + "\n"
    ).encode()
    report = analyze.analyze(
        d105,
        d105_bytes,
        cold_bytes,
        dry_bytes,
        java_wet_bytes,
        d104,
        d104_bytes,
        warm_bytes,
    )
    return {
        "report": report,
        "d105": d105,
        "d105_sha": _sha(d105_bytes),
        "cold": cold_bytes,
        "dry": dry_bytes,
        "java_wet": java_wet_bytes,
        "d104": d104,
        "d104_sha": _sha(d104_bytes),
        "warm": warm_bytes,
    }


def run_verify(values):
    return verify.verify(
        values["report"],
        "9" * 64,
        values["d105"],
        values["d105_sha"],
        values["cold"],
        values["dry"],
        values["java_wet"],
        values["d104"],
        values["d104_sha"],
        values["warm"],
    )


class EfxRecoverySlewVerifyTest(unittest.TestCase):
    def test_accepts_direct_reset_selection(self):
        result = run_verify(fixture())
        self.assertEqual(result["selected_slew_ms"], 0)

    def test_rejects_source_report_detachment(self):
        values = fixture()
        values["report"]["source_d105_report_sha256"] = "f" * 64
        with self.assertRaisesRegex(ValueError, "source report binding"):
            run_verify(values)

    def test_rejects_cold_pcm_tamper(self):
        values = fixture()
        payload = bytearray(values["cold"])
        payload[12] ^= 1
        values["cold"] = bytes(payload)
        with self.assertRaisesRegex(ValueError, "cold PCM binding"):
            run_verify(values)

    def test_rejects_warm_pcm_tamper(self):
        values = fixture()
        payload = bytearray(values["warm"])
        payload[20] ^= 1
        values["warm"] = bytes(payload)
        with self.assertRaisesRegex(ValueError, "warm PCM binding"):
            run_verify(values)

    def test_rejects_java_wet_in_recovery(self):
        values = fixture()
        wet = np.frombuffer(values["java_wet"], dtype="<i2").copy()
        wet[verify.RECOVERY_FRAMES[0] + 10] = 1
        values["java_wet"] = wet.tobytes()
        digest = _sha(values["java_wet"])
        values["d105"]["java_wet_pcm_sha256"] = digest
        values["report"]["source_pcm_sha256"]["java_wet"] = digest
        with self.assertRaisesRegex(ValueError, "Java wet escaped"):
            run_verify(values)

    def test_rejects_candidate_order_change(self):
        values = fixture()
        values["report"]["cases"][1]["slew_ms"] = 6
        with self.assertRaisesRegex(ValueError, "candidate order"):
            run_verify(values)

    def test_rejects_boundary_manifest_change(self):
        values = fixture()
        values["report"]["cases"][0]["boundaries"][0]["frame"] += 1
        with self.assertRaisesRegex(ValueError, "boundary manifest"):
            run_verify(values)

    def test_rejects_boundary_metric_tamper(self):
        values = fixture()
        values["report"]["cases"][0]["boundaries"][0][
            "step_to_p99_ratio"
        ] += 0.01
        with self.assertRaisesRegex(ValueError, "step_to_p99_ratio"):
            run_verify(values)

    def test_rejects_aggregate_metric_tamper(self):
        values = fixture()
        values["report"]["cases"][0][
            "maximum_step_to_p99_ratio"
        ] += 0.01
        with self.assertRaisesRegex(
            ValueError, "maximum_step_to_p99_ratio"
        ):
            run_verify(values)

    def test_rejects_threshold_change(self):
        values = fixture()
        values["report"]["thresholds"][
            "minimum_wet_retention_ratio"
        ] = 0.8
        with self.assertRaisesRegex(ValueError, "threshold changed"):
            run_verify(values)

    def test_rejects_slew_flag_tamper(self):
        values = fixture()
        values["report"]["cases"][1]["slew_justified"] = True
        with self.assertRaisesRegex(ValueError, "slew_justified"):
            run_verify(values)

    def test_rejects_selection_tamper(self):
        values = fixture()
        values["report"]["selected_slew_ms"] = 5
        with self.assertRaisesRegex(ValueError, "selection policy"):
            run_verify(values)

    def test_rejects_double_wet_overclaim(self):
        values = fixture()
        values["report"]["double_wet_crossfade"] = True
        with self.assertRaisesRegex(
            ValueError, "double_wet_crossfade must be False"
        ):
            run_verify(values)

    def test_rejects_capture_overclaim(self):
        values = fixture()
        values["report"]["captures_audio"] = True
        with self.assertRaisesRegex(
            ValueError, "captures_audio must be False"
        ):
            run_verify(values)


if __name__ == "__main__":
    unittest.main()
