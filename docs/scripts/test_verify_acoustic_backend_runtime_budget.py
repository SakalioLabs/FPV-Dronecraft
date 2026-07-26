from __future__ import annotations

import copy
import hashlib
import unittest

import verify_acoustic_backend_runtime_budget as verify


def fixture():
    pcm = b"D094-runtime-fixture"
    pcm_hash = hashlib.sha256(pcm).hexdigest()
    trace = {"pcm_sha256": pcm_hash}
    efx = {
        "status": "valid-openal-efx-runtime-benchmark",
        "source_count": 12,
        "shared_effects": 1,
        "shared_auxiliary_slots": 1,
        "per_source_send_filters": 12,
        "d094_sidecar_sha256": pcm_hash,
        "physical_endpoint_opened": False,
        "captures_audio": False,
        "release_calibrated": False,
        "incremental_efx_p99_ms": 0.4,
        "incremental_efx_p99_buffer_fraction": 0.005,
        "cases": [
            {
                "backend": "dry",
                "p99_ms": 0.2,
                "p99_buffer_fraction": 0.002,
                "al_error": 0,
                "alc_error": 0,
            },
            {
                "backend": "openal-efx",
                "p99_ms": 0.6,
                "p99_buffer_fraction": 0.007,
                "al_error": 0,
                "alc_error": 0,
            },
        ],
    }
    java = {
        "status": "valid-benchmark",
        "source_count": 6,
        "rotors_per_source": 4,
        "tones_per_rotor": 15,
        "release_calibrated": False,
        "cases": [
            {"p99_buffer_fraction": 0.08},
            {"p99_buffer_fraction": 0.09},
        ],
    }
    return efx, java, trace, pcm


class RuntimeBudgetVerifyTest(unittest.TestCase):
    def test_accepts_bounded_production_workloads(self):
        result = verify.verify_reports(*fixture())
        self.assertTrue(result["all_runtime_gates_passed"])

    def test_rejects_unbound_d094_pcm(self):
        efx, java, trace, pcm = fixture()
        efx["d094_sidecar_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "not bound"):
            verify.verify_reports(efx, java, trace, pcm)

    def test_rejects_wrong_shared_efx_topology(self):
        efx, java, trace, pcm = fixture()
        efx["shared_auxiliary_slots"] = 2
        with self.assertRaisesRegex(ValueError, "sharing"):
            verify.verify_reports(efx, java, trace, pcm)

    def test_rejects_java_workload_reduction(self):
        efx, java, trace, pcm = fixture()
        java["source_count"] = 5
        with self.assertRaisesRegex(ValueError, "six drones"):
            verify.verify_reports(efx, java, trace, pcm)

    def test_rejects_budget_overrun(self):
        efx, java, trace, pcm = fixture()
        java["cases"][0]["p99_buffer_fraction"] = 0.251
        with self.assertRaisesRegex(ValueError, "exceeds"):
            verify.verify_reports(efx, java, trace, pcm)

    def test_rejects_audio_capture_claim(self):
        efx, java, trace, pcm = fixture()
        efx["captures_audio"] = True
        with self.assertRaisesRegex(ValueError, "capture audio"):
            verify.verify_reports(efx, java, trace, pcm)

    def test_rejects_inconsistent_incremental_cost(self):
        efx, java, trace, pcm = fixture()
        efx["incremental_efx_p99_ms"] = 0.1
        with self.assertRaisesRegex(ValueError, "inconsistent"):
            verify.verify_reports(efx, java, trace, pcm)


if __name__ == "__main__":
    unittest.main()
