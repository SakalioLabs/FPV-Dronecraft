import unittest

from compare_neaptide_directivity import compare_analyses


def synthetic_analysis(aircraft: str, coefficient: float) -> dict:
    measurements = []
    for axis_cosine in (0.0, 0.2, 0.45, 0.7, 0.85, 0.95, 1.0):
        level = coefficient * axis_cosine**2
        measurements.append(
            {
                "axis_cosine": axis_cosine,
                "relative_to_plane_db": {
                    metric: level
                    for metric in ("overall", "low", "mid", "high")
                },
            }
        )
    return {
        "schema": "neaptide-directivity-analysis-v1",
        "source": {"aircraft_prefix": aircraft},
        "bands_hz": {
            "low": [20.0, 300.0],
            "mid": [300.0, 3_200.0],
            "high": [3_200.0, 20_000.0],
        },
        "measurements": measurements,
    }


class NeaptideDirectivityTransferTest(unittest.TestCase):
    def test_identical_aircraft_models_pass_transfer_gate(self) -> None:
        result = compare_analyses(
            [
                synthetic_analysis("aircraft-a", -10.0),
                synthetic_analysis("aircraft-b", -10.0),
                synthetic_analysis("aircraft-c", -10.0),
            ]
        )

        self.assertTrue(result["research_gate"]["all_metrics_transfer"])
        for metric in result["metrics"].values():
            self.assertLess(metric["maximum_leave_one_aircraft_out_rmse_db"], 1.0e-10)

    def test_opposite_axis_trends_fail_transfer_gate(self) -> None:
        result = compare_analyses(
            [
                synthetic_analysis("axis-quiet", -15.0),
                synthetic_analysis("axis-loud", 15.0),
            ]
        )

        self.assertFalse(result["research_gate"]["all_metrics_transfer"])
        for metric in result["metrics"].values():
            self.assertGreater(metric["maximum_leave_one_aircraft_out_rmse_db"], 3.0)


if __name__ == "__main__":
    unittest.main()
