from __future__ import annotations

import copy
import json
import unittest

import verify_backend_capability_policy_timeline as verify


def fixture():
    capability = {
        "status": "valid-capability-probe",
        "active_context": True,
        "efx_supported": True,
        "maximum_auxiliary_sends": 2,
        "efx_resources_created": True,
        "efx_resources_released": True,
        "al_error_code": 0,
    }
    capability_bytes = json.dumps(capability).encode()
    cycles = []
    events = []
    sequence = 10
    for count, stage in enumerate(
        ("resource-create", "parameter-write", "source-route"), start=1
    ):
        failed_rebuild = count + 2
        recovered_rebuild = failed_rebuild + 1
        cycles.append(
            {
                "stage": stage,
                "failed": {
                    "fault_injection_count": count,
                    "context_rebuilds": failed_rebuild,
                },
                "recovered": {"context_rebuilds": recovered_rebuild},
            }
        )
        events.extend(
            [
                _event(
                    sequence,
                    stage,
                    count,
                    "JAVA_FDN",
                    "CONTEXT_FAILED",
                    failed_rebuild,
                ),
                _event(
                    sequence + 4,
                    stage,
                    count,
                    "OPENAL_EFX",
                    "OPERATIONAL",
                    recovered_rebuild,
                ),
            ]
        )
        sequence += 10
    failover = {
        "status": "valid-openal-efx-fault-failover",
        "fault_cycles": cycles,
    }
    failover_bytes = json.dumps(failover).encode()
    policy_cases = []
    for name, (simulated, status, backend) in verify.EXPECTED_POLICY.items():
        policy_cases.append(
            {
                "name": name,
                "simulated": simulated,
                "efx_status": status,
                "resolved_backend": backend,
            }
        )
    report = {
        "status": "valid-backend-capability-policy-timeline",
        "capability_report_sha256": verify._sha256(capability_bytes),
        "fault_failover_report_sha256": verify._sha256(failover_bytes),
        "actual_device": {
            "efx_extension_supported": True,
            "native_efx_eligible": True,
            "maximum_auxiliary_sends": 2,
        },
        "policy_cases": policy_cases,
        "timeline_capacity": 256,
        "timeline_events": events,
        "no_efx_hardware_exercised": False,
        "policy_simulation_exercised": True,
        "physical_endpoint_changed": False,
        "captures_audio": False,
        "release_calibrated": False,
    }
    return report, capability, capability_bytes, failover, failover_bytes


def _event(sequence, stage, count, backend, status, rebuilds):
    return {
        "telemetry_sequence": sequence,
        "host_monotonic_nanos": sequence * 1000,
        "minecraft_tick": sequence,
        "backend": backend,
        "efx_status": status,
        "environment_generation": sequence,
        "rt60_seconds": {"low": 1.0, "mid": 0.8, "high": 0.4},
        "wet_gain": 0.2,
        "transition_seconds": 0.2,
        "context_rebuilds": rebuilds,
        "al_error_code": 0 if backend == "OPENAL_EFX" else 40963,
        "last_fault_stage": stage,
        "fault_injection_count": count,
        "java_stream_active": backend == "JAVA_FDN",
        "efx_operational": backend == "OPENAL_EFX",
        "double_wet_path": False,
        "captures_audio": False,
    }


class BackendCapabilityPolicyTimelineVerifyTest(unittest.TestCase):
    def test_accepts_live_capability_and_simulated_policy(self):
        result = verify.verify(*fixture())
        self.assertEqual(6, result["policy_cases"])

    def test_rejects_capability_detachment(self):
        args = list(fixture())
        args[0]["capability_report_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "detached"):
            verify.verify(*args)

    def test_rejects_counterfactual_as_measured(self):
        args = list(fixture())
        args[0]["policy_cases"][1]["simulated"] = False
        with self.assertRaisesRegex(ValueError, "policy case"):
            verify.verify(*args)

    def test_rejects_wrong_fallback_route(self):
        args = list(fixture())
        args[0]["policy_cases"][1]["resolved_backend"] = "OPENAL_EFX"
        with self.assertRaisesRegex(ValueError, "policy case"):
            verify.verify(*args)

    def test_rejects_nonmonotonic_host_clock(self):
        args = list(fixture())
        args[0]["timeline_events"][1]["host_monotonic_nanos"] = 1
        with self.assertRaisesRegex(ValueError, "not monotonic"):
            verify.verify(*args)

    def test_rejects_double_wet_path(self):
        args = list(fixture())
        args[0]["timeline_events"][0]["double_wet_path"] = True
        with self.assertRaisesRegex(ValueError, "double wet"):
            verify.verify(*args)

    def test_rejects_missing_fault_recovery(self):
        args = list(fixture())
        args[0]["timeline_events"] = [
            event
            for event in args[0]["timeline_events"]
            if not (
                event["last_fault_stage"] == "source-route"
                and event["backend"] == "OPENAL_EFX"
            )
        ]
        with self.assertRaisesRegex(ValueError, "misses source-route"):
            verify.verify(*args)

    def test_rejects_audio_capture_overclaim(self):
        args = list(fixture())
        args[0]["timeline_events"][0]["captures_audio"] = True
        with self.assertRaisesRegex(ValueError, "captured audio"):
            verify.verify(*args)

    def test_rejects_no_efx_hardware_overclaim(self):
        args = list(fixture())
        args[0]["no_efx_hardware_exercised"] = True
        with self.assertRaisesRegex(ValueError, "falsely claimed"):
            verify.verify(*args)


if __name__ == "__main__":
    unittest.main()
