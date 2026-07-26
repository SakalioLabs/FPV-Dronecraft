import copy
import math
import unittest

import verify_fdn_door_transition as verify


def doorway():
    closed = {
        "rt60_s": {"low": 6.0, "mid": 4.0, "high": 2.0},
        "wet_gain": 0.4,
    }
    opened = {
        "rt60_s": {"low": 1.5, "mid": 1.2, "high": 1.0},
        "wet_gain": 0.01,
    }
    return {
        "status": "valid-diagnostic",
        "steps": [closed, opened],
    }


def rows(start, target, decreasing):
    values = []
    for samples in verify.CHECKPOINTS:
        progress = 1.0 - math.exp(-samples / 9600)
        current = start + progress * (target - start)
        feedback_start = 0.9 if decreasing else 0.7
        feedback_target = 0.7 if decreasing else 0.9
        feedback = feedback_start + progress * (
            feedback_target - feedback_start
        )
        values.append(
            {
                "samples": samples,
                "current_wet_gain": current,
                "target_wet_gain": target,
                "current_feedback_gain": {
                    band: feedback for band in verify.BANDS
                },
            }
        )
    return values


def transition():
    source = doorway()
    return {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "sample_rate_hz": 48000,
        "environment_update_ticks": 20,
        "transition_seconds": 0.2,
        "closed_endpoint": source["steps"][0],
        "open_endpoint": source["steps"][-1],
        "opening": rows(0.4, 0.01, True),
        "closing": rows(0.01, 0.4, False),
    }


class FdnDoorTransitionVerifyTest(unittest.TestCase):
    def test_accepts_exponential_bidirectional_transition(self):
        result = verify.verify(transition(), doorway())
        self.assertTrue(
            result["gates"][
                "progress_at_200_ms_matches_one_time_constant"
            ]
        )
        self.assertFalse(result["release_calibrated"])

    def test_rejects_detached_endpoint(self):
        changed = copy.deepcopy(transition())
        changed["open_endpoint"]["wet_gain"] = 0.2
        with self.assertRaisesRegex(ValueError, "detached from D078"):
            verify.verify(changed, doorway())


if __name__ == "__main__":
    unittest.main()
