#!/usr/bin/env python3
"""Unit tests for the post-D118 extrapolation diagnosis policy."""

from __future__ import annotations

import copy
import unittest

import verify_dechorate_echo_window_generalization as verifier


class DechorateEchoWindowGeneralizationTest(unittest.TestCase):
    def test_enforcer_accepts_failure_diagnosis(self) -> None:
        verifier.enforce(valid_report())

    def test_enforcer_rejects_fit(self) -> None:
        changed = valid_report()
        changed["fit_performed"] = True
        with self.assertRaises(ValueError):
            verifier.enforce(changed)

    def test_enforcer_rejects_hidden_extrapolation(self) -> None:
        changed = valid_report()
        changed["new_positions"]["frozen_model_extrapolation"][
            "predicted_abs_db"
        ]["maximum"] = 20.0
        with self.assertRaises(ValueError):
            verifier.enforce(changed)

    def test_enforcer_rejects_unstable_target_overclaim(self) -> None:
        changed = valid_report()
        changed["new_positions"]["window_rank_stability"]["16"][
            "mean_top4_overlap"
        ] = 0.7
        with self.assertRaises(ValueError):
            verifier.enforce(changed)


def valid_report() -> dict:
    position = {
        "groups": 165,
        "window_rank_stability": {
            "16": {"mean_top4_overlap": 0.88}
        },
        "absolute_observation_db": {"maximum": 28.0},
        "frozen_model_extrapolation": {
            "predicted_abs_db": {"maximum": 20.0},
            "maximum_abs_error_db": 22.0,
            "rows_with_feature_outside_d117_fit_range": 60,
        },
    }
    new_position = copy.deepcopy(position)
    new_position["frozen_model_extrapolation"] = {
        "predicted_abs_db": {"maximum": 161.0},
        "maximum_abs_error_db": 155.0,
        "rows_with_feature_outside_d117_fit_range": 660,
    }
    return {
        "schema_version": 1,
        "status": "valid-dechorate-echo-window-failure-diagnostic",
        "source4_rirs": 330,
        "fit_performed": False,
        "old_positions": position,
        "new_positions": new_position,
    }


if __name__ == "__main__":
    unittest.main()
