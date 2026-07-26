from __future__ import annotations

import copy
import hashlib
import json
import unittest

import analyze_acoustic_calibration_admissibility as analyze
import verify_acoustic_calibration_admissibility as verify


def _bytes(value):
    return (json.dumps(value, sort_keys=True) + "\n").encode()


def _sha(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def fixture():
    air = {
        "schema_version": 1,
        "status": "valid-reference-diagnostic",
        "source": {"measured_rir": True, "license": "MIT"},
        "gates": {
            "minecraft_release_calibrated": False,
            "every_published_rt60_error_at_most_15_percent": False,
        },
        "published_rt60_reproduction": {
            "position_count": 17,
            "positions_within_15_percent": 13,
        },
    }
    air_bytes = _bytes(air)
    current = {"low": 0.03, "mid": 0.05, "high": 0.08}
    rooms = []
    for name, scale in (("air-booth", 1.0), ("air-lecture", 4.0)):
        rooms.append(
            {
                "id": name,
                "measured_rt60_s": {
                    "low": 0.15 * scale,
                    "mid": 0.14 * scale,
                    "high": 0.11 * scale,
                },
                "effective_eyring_absorption": {
                    "low": 0.38 / scale**0.3,
                    "mid": 0.35 / scale**0.3,
                    "high": 0.42 / scale**0.3,
                },
                "current_stone_absorption": copy.deepcopy(current),
                "current_stone_rt60_ratio_vs_measured": {
                    "low": 15.0 / scale,
                    "mid": 8.0 / scale,
                    "high": 6.0 / scale,
                },
            }
        )
    shoebox = {
        "status": "valid-diagnostic",
        "source_air_report_sha256": _sha(air_bytes),
        "release_calibrated": False,
        "rooms": rooms,
    }
    ptb = {
        "status": "valid-diagnostic",
        "raw_selection_csv_verified": True,
        "release_calibrated": False,
        "categories": {
            "stone_dense": {
                "current_runtime_absorption": copy.deepcopy(current),
                "runtime_candidate_absorption": {
                    "low": 0.027,
                    "mid": 0.035,
                    "high": 0.07,
                },
            }
        },
    }
    matrix = {
        "status": "valid-backend-environment-matrix",
        "release_calibrated": False,
        "environments": [
            {
                "name": "closed",
                "rt60_seconds": {
                    "low": 6.0,
                    "mid": 4.0,
                    "high": 2.0,
                },
                "wet_gain": 0.39,
            },
            {
                "name": "partial",
                "rt60_seconds": {
                    "low": 3.0,
                    "mid": 1.5,
                    "high": 0.8,
                },
                "wet_gain": 0.38,
            },
            {
                "name": "open",
                "rt60_seconds": {
                    "low": 2.0,
                    "mid": 0.8,
                    "high": 0.4,
                },
                "wet_gain": 0.37,
            },
        ],
    }
    shoebox_bytes = _bytes(shoebox)
    ptb_bytes = _bytes(ptb)
    matrix_bytes = _bytes(matrix)
    report = analyze.analyze(
        air,
        air_bytes,
        shoebox,
        shoebox_bytes,
        ptb,
        ptb_bytes,
        matrix,
        matrix_bytes,
    )
    return {
        "report": report,
        "air": air,
        "air_sha": _sha(air_bytes),
        "shoebox": shoebox,
        "shoebox_sha": _sha(shoebox_bytes),
        "ptb": ptb,
        "ptb_sha": _sha(ptb_bytes),
        "matrix": matrix,
        "matrix_sha": _sha(matrix_bytes),
    }


def run_verify(values):
    return verify.verify(
        values["report"],
        "a" * 64,
        values["air"],
        values["air_sha"],
        values["shoebox"],
        values["shoebox_sha"],
        values["ptb"],
        values["ptb_sha"],
        values["matrix"],
        values["matrix_sha"],
    )


class AcousticCalibrationAdmissibilityVerifyTest(unittest.TestCase):
    def test_accepts_separated_room_and_material_semantics(self):
        result = run_verify(fixture())
        self.assertTrue(
            result["gates"]["runtime_stone_override_rejected"]
        )

    def test_rejects_source_hash_detachment(self):
        values = fixture()
        values["report"]["source_report_sha256"]["air_rir"] = "f" * 64
        with self.assertRaisesRegex(ValueError, "source report binding"):
            run_verify(values)

    def test_rejects_nonmeasured_air_source(self):
        values = fixture()
        values["air"]["source"]["measured_rir"] = False
        with self.assertRaisesRegex(ValueError, "source evidence status"):
            run_verify(values)

    def test_rejects_shoebox_air_detachment(self):
        values = fixture()
        values["shoebox"]["source_air_report_sha256"] = "f" * 64
        with self.assertRaisesRegex(ValueError, "source evidence status"):
            run_verify(values)

    def test_rejects_release_overclaim(self):
        values = fixture()
        values["report"]["release_calibrated"] = True
        with self.assertRaisesRegex(
            ValueError, "release_calibrated must be False"
        ):
            run_verify(values)

    def test_rejects_air_room_order_change(self):
        values = fixture()
        values["report"]["air_rooms"].reverse()
        with self.assertRaisesRegex(ValueError, "AIR room order"):
            run_verify(values)

    def test_rejects_room_metric_tamper(self):
        values = fixture()
        values["report"]["air_rooms"][0][
            "effective_room_absorption"
        ]["low"] += 0.1
        with self.assertRaisesRegex(
            ValueError, "effective room absorption.low"
        ):
            run_verify(values)

    def test_rejects_stone_control_detachment(self):
        values = fixture()
        values["shoebox"]["rooms"][0][
            "current_stone_absorption"
        ]["low"] = 0.04
        with self.assertRaisesRegex(
            ValueError, "runtime stone controls detached"
        ):
            run_verify(values)

    def test_rejects_ptb_metric_tamper(self):
        values = fixture()
        values["report"]["ptb_dense_stone"][
            "maximum_relative_delta"
        ] += 0.1
        with self.assertRaisesRegex(
            ValueError, "PTB maximum relative delta"
        ):
            run_verify(values)

    def test_rejects_minecraft_environment_order(self):
        values = fixture()
        values["report"]["minecraft_environment_rt60"].reverse()
        with self.assertRaisesRegex(
            ValueError, "Minecraft environment order"
        ):
            run_verify(values)

    def test_rejects_nonmonotonic_minecraft_source(self):
        values = fixture()
        values["matrix"]["environments"][1][
            "rt60_seconds"
        ]["low"] = 7.0
        values["report"]["minecraft_environment_rt60"][1][
            "rt60_seconds"
        ]["low"] = 7.0
        with self.assertRaisesRegex(ValueError, "lost monotonicity"):
            run_verify(values)

    def test_rejects_summary_metric_tamper(self):
        values = fixture()
        values["report"]["summary"][
            "minimum_current_stone_rt60_to_air_measured_ratio"
        ] += 1.0
        with self.assertRaisesRegex(
            ValueError, "minimum stone RT60 ratio"
        ):
            run_verify(values)

    def test_rejects_block_override_eligibility(self):
        values = fixture()
        values["report"]["eligibility"][
            "air_room_effective_absorption_for_block_override"
        ] = True
        with self.assertRaisesRegex(ValueError, "eligibility matrix"):
            run_verify(values)

    def test_rejects_runtime_stone_replacement(self):
        values = fixture()
        values["report"]["decision"][
            "replace_runtime_stone_absorption"
        ] = True
        with self.assertRaisesRegex(ValueError, "production decision"):
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
