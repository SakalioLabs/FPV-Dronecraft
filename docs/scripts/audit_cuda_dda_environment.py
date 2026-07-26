#!/usr/bin/env python3
"""Audit CUDA DDA build prerequisites without claiming device parity."""

from __future__ import annotations

import argparse
import json
import os
import platform
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Any, Callable


GPU_QUERY = (
    "--query-gpu=name,driver_version,memory.total,compute_cap",
    "--format=csv,noheader,nounits",
)


def run_command(executable: str, arguments: tuple[str, ...]) -> str:
    try:
        completed = subprocess.run(
            (executable, *arguments),
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=30,
        )
    except subprocess.TimeoutExpired as error:
        raise RuntimeError(
            f"{Path(executable).name} timed out after 30 seconds"
        ) from error
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise RuntimeError(
            f"{Path(executable).name} exited {completed.returncode}: {detail}"
        )
    return completed.stdout.strip()


def parse_gpu_rows(output: str) -> list[dict[str, Any]]:
    devices = []
    for line_number, line in enumerate(output.splitlines(), start=1):
        if not line.strip():
            continue
        fields = [field.strip() for field in line.split(",")]
        if len(fields) != 4:
            raise ValueError(
                f"nvidia-smi row {line_number} must contain four CSV fields"
            )
        try:
            memory_mib = int(fields[2])
            major, minor = (int(item) for item in fields[3].split(".", 1))
        except (ValueError, TypeError) as error:
            raise ValueError(
                f"nvidia-smi row {line_number} has invalid numeric fields"
            ) from error
        if memory_mib <= 0 or major < 1 or not 0 <= minor <= 99:
            raise ValueError(
                f"nvidia-smi row {line_number} has out-of-range values"
            )
        devices.append(
            {
                "name": fields[0],
                "driver_version": fields[1],
                "memory_mib": memory_mib,
                "compute_capability": f"{major}.{minor}",
            }
        )
    return devices


def build_report(
    which: Callable[[str], str | None] = shutil.which,
    run: Callable[[str, tuple[str, ...]], str] = run_command,
) -> dict[str, Any]:
    nvidia_smi = which("nvidia-smi")
    nvcc = which("nvcc")
    cmake = which("cmake")
    host_compilers = {
        name: path
        for name in ("cl", "clang++", "g++")
        if (path := which(name)) is not None
    }
    devices = []
    gpu_probe_error = None
    if nvidia_smi is not None:
        try:
            devices = parse_gpu_rows(run(nvidia_smi, GPU_QUERY))
        except RuntimeError as error:
            gpu_probe_error = str(error)
    nvcc_version = None
    nvcc_probe_error = None
    if nvcc is not None:
        try:
            nvcc_version = run(nvcc, ("--version",))
        except RuntimeError as error:
            nvcc_probe_error = str(error)

    if gpu_probe_error is not None:
        status = "gpu-probe-failed"
    elif not devices:
        status = "no-cuda-device"
    elif nvcc is None:
        status = "toolchain-missing"
    elif nvcc_probe_error is not None:
        status = "toolchain-probe-failed"
    elif not host_compilers:
        status = "host-compiler-missing"
    else:
        status = "ready-for-kernel-build"

    return {
        "schema_version": 1,
        "platform": {
            "system": platform.system(),
            "release": platform.release(),
            "machine": platform.machine(),
            "python": platform.python_version(),
        },
        "gpu_probe": {
            "nvidia_smi_path": nvidia_smi,
            "devices": devices,
            "error": gpu_probe_error,
        },
        "cuda_toolkit": {
            "nvcc_path": nvcc,
            "nvcc_version_output": nvcc_version,
            "error": nvcc_probe_error,
        },
        "native_build": {
            "cmake_path": cmake,
            "host_compilers": host_compilers,
        },
        "production_contract": {
            "bundle_magic": "MCFPDDA1",
            "bundle_schema": 1,
            "cpu_reference": "Java VoxelDda/DirectPathSolver",
        },
        "decision": {
            "status": status,
            "hardware_feasible": bool(devices),
            "cuda_build_ready": status == "ready-for-kernel-build",
            "device_reader_implemented": False,
            "device_kernel_implemented": False,
            "device_parity_verified": False,
            "speedup_verified": False,
        },
    }


def json_bytes(value: Any) -> bytes:
    return (
        json.dumps(
            value,
            ensure_ascii=False,
            allow_nan=False,
            sort_keys=True,
            indent=2,
        )
        + "\n"
    ).encode("utf-8")


def write_atomic(path: Path, data: bytes) -> None:
    path = path.resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=path.name + ".",
        suffix=".tmp",
        dir=path.parent,
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Audit CUDA DDA hardware and build prerequisites."
    )
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    try:
        report = build_report()
        write_atomic(args.output_json, json_bytes(report))
    except (OSError, RuntimeError, ValueError) as error:
        parser.error(str(error))
    print(json.dumps(report["decision"], sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
