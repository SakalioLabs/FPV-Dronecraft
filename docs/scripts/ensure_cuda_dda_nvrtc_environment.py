#!/usr/bin/env python3
"""Create and verify the pinned CUDA Python/NVRTC research environment."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path


EXPECTED = {
    "cuda-python": "13.3.1",
    "cuda-bindings": "13.3.1",
    "nvidia-cuda-nvrtc": "13.3.33",
    "numpy": "2.5.1",
}


def environment_python(environment: Path) -> Path:
    if os.name == "nt":
        return environment / "Scripts/python.exe"
    return environment / "bin/python"


def usable(interpreter: Path) -> bool:
    if not interpreter.is_file():
        return False
    probe = """
import importlib.metadata
from cuda.bindings import nvrtc
expected = {
    "cuda-python": "13.3.1",
    "cuda-bindings": "13.3.1",
    "nvidia-cuda-nvrtc": "13.3.33",
    "numpy": "2.5.1",
}
for package, version in expected.items():
    assert importlib.metadata.version(package) == version
assert nvrtc.nvrtcVersion()[0] == nvrtc.nvrtcResult.NVRTC_SUCCESS
"""
    result = subprocess.run(
        [str(interpreter), "-c", probe],
        check=False,
        capture_output=True,
        text=True,
        timeout=60,
    )
    return result.returncode == 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--environment", type=Path, required=True)
    parser.add_argument("--requirements", type=Path, required=True)
    arguments = parser.parse_args()
    interpreter = environment_python(arguments.environment)
    if not interpreter.is_file():
        subprocess.run(
            [sys.executable, "-m", "venv", str(arguments.environment)],
            check=True,
        )
    if not usable(interpreter):
        subprocess.run(
            [
                str(interpreter),
                "-m",
                "pip",
                "install",
                "--disable-pip-version-check",
                "--requirement",
                str(arguments.requirements),
            ],
            check=True,
        )
    if not usable(interpreter):
        raise RuntimeError("pinned CUDA Python/NVRTC environment is unavailable")
    print(f"verified CUDA Python/NVRTC environment: {interpreter.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
