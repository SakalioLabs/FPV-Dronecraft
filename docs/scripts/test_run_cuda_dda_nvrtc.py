import subprocess
import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from run_cuda_dda_nvrtc import (
    CELL_DTYPE,
    RAY_DTYPE,
    RESULT_DTYPE,
    SEGMENT_DTYPE,
    plan_batches,
    prepare_host_batches,
    probe_nvidia_driver,
)


class CudaDdaNvrtcTest(unittest.TestCase):
    def test_driver_probe_precedes_cuda_import_and_environment_cuinit(self):
        runner_source = (
            SCRIPT_DIR / "run_cuda_dda_nvrtc.py"
        ).read_text(encoding="utf-8")
        execute_source = runner_source.split(
            "def execute(arguments: argparse.Namespace) -> dict:",
            maxsplit=1,
        )[1]
        self.assertLess(
            execute_source.index("driver_probe = probe_nvidia_driver("),
            execute_source.index("driver, nvrtc, check = _cuda_imports()"),
        )
        self.assertLess(
            execute_source.index("driver_probe = probe_nvidia_driver("),
            execute_source.index("check(driver.cuInit(0))"),
        )

        environment_source = (
            SCRIPT_DIR / "ensure_cuda_dda_nvrtc_environment.py"
        ).read_text(encoding="utf-8")
        self.assertNotIn("driver.cuInit", environment_source)

    def test_driver_probe_requires_a_reported_device(self):
        class Completed:
            returncode = 0
            stdout = "GPU 0: NVIDIA Test GPU\n"
            stderr = ""

        observed = {}

        def fake_run(command, **kwargs):
            observed["command"] = command
            observed["timeout"] = kwargs["timeout"]
            return Completed()

        self.assertEqual(
            "GPU 0: NVIDIA Test GPU",
            probe_nvidia_driver(
                timeout_seconds=3.5,
                executable="nvidia-smi",
                run=fake_run,
            ),
        )
        self.assertEqual(["nvidia-smi", "-L"], observed["command"])
        self.assertEqual(3.5, observed["timeout"])

    def test_driver_probe_timeout_fails_closed(self):
        def fake_run(command, **kwargs):
            raise subprocess.TimeoutExpired(command, kwargs["timeout"])

        with self.assertRaisesRegex(
            RuntimeError,
            "timed out after 2 seconds",
        ):
            probe_nvidia_driver(
                timeout_seconds=2.0,
                executable="nvidia-smi",
                run=fake_run,
            )

    def test_explicit_host_layouts_match_cuda_abi(self):
        self.assertEqual(24, CELL_DTYPE.itemsize)
        self.assertEqual(64, RAY_DTYPE.itemsize)
        self.assertEqual(32, SEGMENT_DTYPE.itemsize)
        self.assertEqual(72, RESULT_DTYPE.itemsize)
        self.assertEqual(16, CELL_DTYPE.fields["fill_fraction"][1])
        self.assertEqual(56, RAY_DTYPE.fields["segment_offset"][1])
        self.assertEqual(24, SEGMENT_DTYPE.fields["fill_fraction"][1])
        self.assertEqual(24, RESULT_DTYPE.fields["loss"][1])
        self.assertEqual(48, RESULT_DTYPE.fields["gain"][1])

    def test_batch_plan_matches_bounded_contiguous_contract(self):
        batches = plan_batches(
            [300, 400, 500, 100, 100],
            maximum_rays=3,
            maximum_segments=1_000,
        )
        self.assertEqual(
            [(0, 2, 700), (2, 3, 700)],
            [
                (batch.first_ray, batch.ray_count, batch.segment_count)
                for batch in batches
            ],
        )

    def test_single_ray_above_segment_limit_fails_closed(self):
        with self.assertRaisesRegex(
            ValueError,
            "single ray exceeds CUDA batch segment limit",
        ):
            plan_batches([1_001], maximum_rays=3, maximum_segments=1_000)

    def test_prepared_batches_rebase_segment_offsets_once(self):
        rays = [
            ((0.0, 0.0, 0.0, 1.0, 0.0, 0.0), 3),
            ((1.0, 0.0, 0.0, 2.0, 0.0, 0.0), 5),
            ((2.0, 0.0, 0.0, 3.0, 0.0, 0.0), 7),
        ]
        batches = plan_batches(
            [3, 5, 7],
            maximum_rays=2,
            maximum_segments=10,
        )
        prepared = prepare_host_batches(rays, batches)
        self.assertEqual([0, 3], list(prepared[0]["segment_offset"]))
        self.assertEqual([0], list(prepared[1]["segment_offset"]))
        self.assertEqual([3, 5], list(prepared[0]["maximum_cells"]))


if __name__ == "__main__":
    unittest.main()
