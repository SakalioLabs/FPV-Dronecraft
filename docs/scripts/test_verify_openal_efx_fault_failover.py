from __future__ import annotations

import copy
import unittest

import verify_openal_efx_fault_failover as verify


def _diagnostics(status, rebuilds, cleanup, error, stage, count):
    operational = status == "OPERATIONAL"
    return {
        "status": status,
        "operational": operational,
        "shared_resources_created": operational,
        "attached_sources": 2 if operational else 0,
        "allocated_source_filters": 2 if operational else 0,
        "context_rebuilds": rebuilds,
        "cleanup_count": cleanup,
        "al_error_code": error,
        "last_fault_stage": stage,
        "fault_injection_count": count,
    }


def _runtime(sequence, backend, status, rebuilds, error, stage, count):
    return {
        "sequence": sequence,
        "backend": backend,
        "efx_status": status,
        "context_rebuilds": rebuilds,
        "al_error_code": error,
        "last_fault_stage": stage,
        "fault_injection_count": count,
        "java_stream_active": backend == "JAVA_FDN",
        "efx_operational": backend == "OPENAL_EFX",
        "double_wet_path": False,
        "captures_audio": False,
    }


def fixture():
    cycles = []
    rebuilds = 3
    cleanup = 1
    previous_stage = "none"
    for index, stage in enumerate(verify.STAGES, start=1):
        cycles.append(
            {
                "stage": stage,
                "before": _diagnostics(
                    "OPERATIONAL",
                    rebuilds,
                    cleanup,
                    0,
                    previous_stage,
                    index - 1,
                ),
                "failed": _diagnostics(
                    "CONTEXT_FAILED",
                    rebuilds,
                    cleanup + 1,
                    40963,
                    stage,
                    index,
                ),
                "fallback": _runtime(
                    index * 10,
                    "JAVA_FDN",
                    "CONTEXT_FAILED",
                    rebuilds,
                    40963,
                    stage,
                    index,
                ),
                "same_context_stable": _runtime(
                    index * 10 + 3,
                    "JAVA_FDN",
                    "CONTEXT_FAILED",
                    rebuilds,
                    40963,
                    stage,
                    index,
                ),
                "recovered": _diagnostics(
                    "OPERATIONAL",
                    rebuilds + 1,
                    cleanup + 1,
                    0,
                    stage,
                    index,
                ),
                "recovery_runtime": _runtime(
                    index * 10 + 8,
                    "OPENAL_EFX",
                    "OPERATIONAL",
                    rebuilds + 1,
                    0,
                    stage,
                    index,
                ),
            }
        )
        rebuilds += 1
        cleanup += 1
        previous_stage = stage
    return {
        "status": "valid-openal-efx-fault-failover",
        "development_environment_required": True,
        "fault_property_cleared": True,
        "fault_cycles": cycles,
        "gates": {
            "all_three_stages_exercised": True,
            "resources_cleaned_before_fallback": True,
            "java_fallback_next_tick": True,
            "same_context_retry_suppressed": True,
            "new_context_recovery": True,
            "double_wet_path_never_observed": True,
        },
        "physical_endpoint_changed": False,
        "captures_audio": False,
        "release_calibrated": False,
    }


class EfxFaultFailoverVerifyTest(unittest.TestCase):
    def test_accepts_all_three_fault_cycles(self):
        result = verify.verify_report(fixture())
        self.assertTrue(result["all_failover_gates_passed"])

    def test_rejects_missing_stage(self):
        report = fixture()
        report["fault_cycles"].pop()
        with self.assertRaisesRegex(ValueError, "three ordered"):
            verify.verify_report(report)

    def test_rejects_resource_leak(self):
        report = fixture()
        report["fault_cycles"][0]["failed"][
            "shared_resources_created"
        ] = True
        with self.assertRaisesRegex(ValueError, "cleanup"):
            verify.verify_report(report)

    def test_rejects_same_context_retry(self):
        report = fixture()
        report["fault_cycles"][1]["same_context_stable"][
            "fault_injection_count"
        ] += 1
        with self.assertRaisesRegex(ValueError, "retried"):
            verify.verify_report(report)

    def test_rejects_double_wet_path(self):
        report = fixture()
        report["fault_cycles"][1]["fallback"]["double_wet_path"] = True
        with self.assertRaisesRegex(ValueError, "double wet"):
            verify.verify_report(report)

    def test_rejects_no_context_rebuild(self):
        report = fixture()
        cycle = report["fault_cycles"][2]
        cycle["recovered"]["context_rebuilds"] = cycle["failed"][
            "context_rebuilds"
        ]
        with self.assertRaisesRegex(ValueError, "recover"):
            verify.verify_report(report)

    def test_rejects_fault_property_leak(self):
        report = fixture()
        report["fault_property_cleared"] = False
        with self.assertRaisesRegex(ValueError, "left active"):
            verify.verify_report(report)

    def test_rejects_physical_failure_overclaim(self):
        report = fixture()
        report["physical_endpoint_changed"] = True
        with self.assertRaisesRegex(ValueError, "must be false"):
            verify.verify_report(report)


if __name__ == "__main__":
    unittest.main()
