import importlib.util
import subprocess
import sys
import unittest
from pathlib import Path
from unittest import mock


MODULE_PATH = Path(__file__).with_name("audit_cuda_dda_environment.py")
SPEC = importlib.util.spec_from_file_location(
    "audit_cuda_dda_environment",
    MODULE_PATH,
)
audit = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = audit
SPEC.loader.exec_module(audit)


class CudaDdaEnvironmentAuditTest(unittest.TestCase):
    @mock.patch.object(audit.subprocess, "run")
    def test_command_timeout_becomes_bounded_probe_error(self, run):
        run.side_effect = subprocess.TimeoutExpired(["nvidia-smi"], 30)

        with self.assertRaisesRegex(
            RuntimeError,
            "nvidia-smi timed out after 30 seconds",
        ):
            audit.run_command("nvidia-smi", ("-L",))

    def test_reports_ready_only_with_device_nvcc_and_host_compiler(self):
        paths = {
            "nvidia-smi": "/bin/nvidia-smi",
            "nvcc": "/bin/nvcc",
            "cmake": "/bin/cmake",
            "clang++": "/bin/clang++",
        }

        report = audit.build_report(
            paths.get,
            lambda executable, arguments: (
                "NVIDIA Test GPU, 610.47, 12288, 8.6"
                if executable.endswith("nvidia-smi")
                else "Cuda compilation tools, release 13.0"
            ),
        )

        self.assertEqual(
            "ready-for-kernel-build",
            report["decision"]["status"],
        )
        self.assertTrue(report["decision"]["hardware_feasible"])
        self.assertTrue(report["decision"]["cuda_build_ready"])
        self.assertFalse(report["decision"]["speedup_verified"])

    def test_visible_gpu_without_nvcc_is_toolchain_missing(self):
        paths = {"nvidia-smi": "nvidia-smi", "cmake": "cmake"}
        report = audit.build_report(
            paths.get,
            lambda executable, arguments: (
                "NVIDIA Test GPU, 610.47, 12288, 8.6"
            ),
        )

        self.assertEqual("toolchain-missing", report["decision"]["status"])
        self.assertTrue(report["decision"]["hardware_feasible"])
        self.assertFalse(report["decision"]["cuda_build_ready"])

    def test_no_nvidia_device_is_not_hardware_feasible(self):
        report = audit.build_report(lambda name: None, lambda *args: "")

        self.assertEqual("no-cuda-device", report["decision"]["status"])
        self.assertFalse(report["decision"]["hardware_feasible"])

    def test_rejects_malformed_gpu_query_instead_of_guessing(self):
        with self.assertRaisesRegex(ValueError, "four CSV fields"):
            audit.parse_gpu_rows("GPU, 610.47, 12288")

    def test_probe_failure_is_evidence_not_a_crash(self):
        report = audit.build_report(
            lambda name: "nvidia-smi" if name == "nvidia-smi" else None,
            lambda executable, arguments: (_ for _ in ()).throw(
                RuntimeError("driver unavailable")
            ),
        )

        self.assertEqual("gpu-probe-failed", report["decision"]["status"])
        self.assertIn("driver unavailable", report["gpu_probe"]["error"])


if __name__ == "__main__":
    unittest.main()
