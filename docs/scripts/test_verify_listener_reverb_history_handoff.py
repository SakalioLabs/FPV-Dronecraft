from __future__ import annotations

import copy
import hashlib
import unittest

import verify_listener_reverb_history_handoff as verify


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fixture():
    environments = [
        {
            "name": name,
            "rt60_seconds": {
                "low": 6.0 - index,
                "mid": 4.0 - index,
                "high": 2.0 - index * 0.25,
            },
            "wet_gain": 0.39 - index * 0.01,
        }
        for index, name in enumerate(verify.ENVIRONMENTS)
    ]
    d106 = {
        "schema_version": 1,
        "status": "valid-fdn-history-preroll-sweep",
        "selected_pre_roll_ms": verify.HISTORY_MILLISECONDS,
        "selection_available": True,
        "environments": copy.deepcopy(environments),
    }
    d106_sha = "1" * 64
    sidecar = bytearray()
    cases = []
    for index, environment in enumerate(verify.ENVIRONMENTS):
        payload = (
            (100 + index).to_bytes(2, "little", signed=True)
            + bytes(verify.OUTPUT_BYTES - 2)
        )
        cases.append(
            {
                "environment": environment,
                "shadow_p50_ms": 0.2,
                "shadow_p95_ms": 0.4,
                "shadow_p99_ms": 0.5,
                "handoff_p50_ms": 1.0,
                "handoff_p95_ms": 1.2,
                "handoff_p99_ms": 1.5,
                "fdn_preroll_p99_ms": 1.4,
                "history_frames_before": verify.HISTORY_FRAMES,
                "preroll_frames": verify.HISTORY_FRAMES,
                "synthesizers_before": verify.SOURCE_COUNT,
                "synthesizers_during": verify.SOURCE_COUNT,
                "first_wet_frame": 0,
                "invalidated_owner_silent": True,
                "closing_old_owner_preserved_replacement": True,
                "shadow_restarted": True,
                "wet_owner_released": True,
                "history_cleared_on_restart": True,
                "phase_state_cleared_on_restart": True,
                "pcm_offset": len(sidecar),
                "pcm_bytes": len(payload),
                "pcm_sha256": _sha(payload),
                "passes": True,
            }
        )
        sidecar.extend(payload)
    sidecar = bytes(sidecar)
    report = {
        "schema_version": 1,
        "status": "valid-listener-reverb-history-handoff",
        "sample_rate_hz": verify.SAMPLE_RATE,
        "minecraft_stream_buffer_seconds": 1,
        "minecraft_initial_queue_buffers": 4,
        "history_frames": verify.HISTORY_FRAMES,
        "history_milliseconds": verify.HISTORY_MILLISECONDS,
        "shadow_tick_frames": verify.SHADOW_TICK_FRAMES,
        "source_count": verify.SOURCE_COUNT,
        "warmup_iterations": 10,
        "measured_iterations": 50,
        "source_d106_report_sha256": d106_sha,
        "sidecar_sha256": _sha(sidecar),
        "sidecar_format": "s16le-mono-48000",
        "sidecar_bytes": len(sidecar),
        "thresholds": {
            "maximum_shadow_p99_ms": verify.MAXIMUM_SHADOW_P99_MS,
            "maximum_handoff_p99_ms": verify.MAXIMUM_HANDOFF_P99_MS,
            "maximum_first_wet_frame": 0,
        },
        "environments": environments,
        "cases": cases,
        "all_cases_passed": True,
        "openal_shadow_source_created": False,
        "active_source_restart_required": True,
        "listener_shared_phase_owner": True,
        "exclusive_wet_owner": True,
        "captures_audio": False,
        "physical_endpoint_opened": False,
        "client_gametest_measured": False,
        "release_calibrated": False,
        "claim_boundary": "fixture",
    }
    return {
        "report": report,
        "sidecar": sidecar,
        "d106": d106,
        "d106_sha": d106_sha,
    }


def run(values):
    return verify.verify(
        values["report"],
        "2" * 64,
        values["sidecar"],
        values["d106"],
        values["d106_sha"],
    )


class ListenerReverbHistoryHandoffVerifyTest(unittest.TestCase):
    def test_accepts_valid_handoff(self):
        result = run(fixture())
        self.assertEqual(
            result["status"],
            "verified-listener-reverb-history-handoff",
        )

    def test_rejects_d106_detachment(self):
        values = fixture()
        values["report"]["source_d106_report_sha256"] = "f" * 64
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_queue_contract_change(self):
        values = fixture()
        values["report"]["minecraft_initial_queue_buffers"] = 3
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_short_history(self):
        values = fixture()
        values["report"]["cases"][0]["history_frames_before"] -= 1
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_phase_owner_reset(self):
        values = fixture()
        values["report"]["cases"][0]["synthesizers_during"] = 0
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_overlapping_pcm(self):
        values = fixture()
        values["report"]["cases"][1]["pcm_offset"] = 0
        values["report"]["cases"][1]["pcm_sha256"] = (
            values["report"]["cases"][0]["pcm_sha256"]
        )
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_silent_first_frame(self):
        values = fixture()
        sidecar = bytearray(values["sidecar"])
        sidecar[:2] = b"\x00\x00"
        payload = bytes(sidecar[: verify.OUTPUT_BYTES])
        values["sidecar"] = bytes(sidecar)
        values["report"]["sidecar_sha256"] = _sha(values["sidecar"])
        values["report"]["cases"][0]["pcm_sha256"] = _sha(payload)
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_shadow_budget_overrun(self):
        values = fixture()
        values["report"]["cases"][0]["shadow_p99_ms"] = (
            verify.MAXIMUM_SHADOW_P99_MS + 0.1
        )
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_handoff_budget_overrun(self):
        values = fixture()
        values["report"]["cases"][0]["handoff_p99_ms"] = (
            verify.MAXIMUM_HANDOFF_P99_MS + 0.1
        )
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_nonexclusive_wet_owner(self):
        values = fixture()
        values["report"]["cases"][0][
            "invalidated_owner_silent"
        ] = False
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_shadow_openal_source(self):
        values = fixture()
        values["report"]["openal_shadow_source_created"] = True
        with self.assertRaises(ValueError):
            run(values)

    def test_rejects_client_gametest_overclaim(self):
        values = fixture()
        values["report"]["client_gametest_measured"] = True
        with self.assertRaises(ValueError):
            run(values)


if __name__ == "__main__":
    unittest.main()
