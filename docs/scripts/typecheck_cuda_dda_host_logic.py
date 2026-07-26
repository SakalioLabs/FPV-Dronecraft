#!/usr/bin/env python3
"""Type-check the CUDA DDA host logic without a CUDA toolchain.

The repository's CUDA executor cannot be compiled on hosts without `nvcc`.
This gate rewrites the device-specific syntax into a host translation unit and
compiles it with the ordinary C++ compiler, so that host-side regressions in the
bounded multi-batch loop are caught even when no GPU toolchain exists.

This is not CUDA compilation and is not device parity or performance evidence.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

STUB = """#pragma once
#include <cstddef>
#include <cstdint>
#include <cmath>
#include <algorithm>
typedef int cudaError_t;
#define cudaSuccess 0
#define CUDART_INF (HUGE_VAL)
struct dim3 { unsigned int x, y, z; dim3(unsigned int a=1,unsigned int b=1,unsigned int c=1):x(a),y(b),z(c){} };
struct cudaDeviceProp { char name[256]; int major; int minor; };
typedef struct CUevent_st* cudaEvent_t;
enum cudaMemcpyKind { cudaMemcpyHostToDevice, cudaMemcpyDeviceToHost };
inline const char* cudaGetErrorString(cudaError_t){return "";}
inline cudaError_t cudaMalloc(void**,std::size_t){return 0;}
inline cudaError_t cudaFree(void*){return 0;}
inline cudaError_t cudaMemcpy(void*,const void*,std::size_t,cudaMemcpyKind){return 0;}
inline cudaError_t cudaMemcpyAsync(void*,const void*,std::size_t,cudaMemcpyKind){return 0;}
inline cudaError_t cudaEventCreate(cudaEvent_t*){return 0;}
inline cudaError_t cudaEventRecord(cudaEvent_t){return 0;}
inline cudaError_t cudaEventSynchronize(cudaEvent_t){return 0;}
inline cudaError_t cudaEventElapsedTime(float* ms,cudaEvent_t,cudaEvent_t){*ms=0.0F;return 0;}
inline cudaError_t cudaEventDestroy(cudaEvent_t){return 0;}
inline cudaError_t cudaGetLastError(){return 0;}
inline cudaError_t cudaGetDevice(int* d){*d=0;return 0;}
inline cudaError_t cudaGetDeviceProperties(cudaDeviceProp*,int){return 0;}
template <typename T> inline cudaError_t cudaMemcpyToSymbol(T&,const void*,std::size_t){return 0;}
struct McfpvUint3 { unsigned int x, y, z; };
static const McfpvUint3 blockIdx{0u,0u,0u};
static const McfpvUint3 blockDim{1u,1u,1u};
static const McfpvUint3 threadIdx{0u,0u,0u};
"""


def host_translation_unit(source: str) -> str:
    text = source.replace("#include <cuda_runtime.h>", '#include "cuda_stub.hpp"')
    text = text.replace("__constant__ ", "")
    text = text.replace("__device__ ", "inline ")
    text = text.replace(
        "__global__ void trace_kernel(", "inline void trace_kernel_impl("
    )
    text = text.replace(
        "trace_kernel<<<grid, block>>>(",
        "static_cast<void>(grid); static_cast<void>(block);\n\t\t\ttrace_kernel_impl(",
    )
    return text


def find_vcvars() -> Path | None:
    roots = [
        Path(os.environ.get("ProgramFiles", "C:/Program Files")),
        Path(os.environ.get("ProgramFiles(x86)", "C:/Program Files (x86)")),
    ]
    for root in roots:
        for edition in ("Community", "Professional", "Enterprise", "BuildTools"):
            candidate = (
                root
                / "Microsoft Visual Studio"
                / "2022"
                / edition
                / "VC"
                / "Auxiliary"
                / "Build"
                / "vcvars64.bat"
            )
            if candidate.is_file():
                return candidate
    return None


def compile_msvc(vcvars: Path, work: Path, unit: Path, includes: list[Path]) -> subprocess.CompletedProcess:
    include_flags = " ".join(f'/I "{path}"' for path in includes)
    batch = work / "typecheck.bat"
    batch.write_text(
        "@echo off\r\n"
        f'call "{vcvars}" >nul\r\n'
        f'cl /c /nologo /std:c++20 /EHsc /W4 /WX /permissive- {include_flags} '
        f'"{unit}" /Fo"{work / "out.obj"}"\r\n',
        encoding="utf-8",
    )
    return subprocess.run(
        ["cmd.exe", "/d", "/c", str(batch)],
        capture_output=True,
        text=True,
        timeout=600,
    )


def compile_posix(compiler: str, work: Path, unit: Path, includes: list[Path]) -> subprocess.CompletedProcess:
    command = [
        compiler,
        "-c",
        "-std=c++20",
        "-Wall",
        "-Wextra",
        "-Werror",
        str(unit),
        "-o",
        str(work / "out.o"),
    ]
    for path in includes:
        command.extend(["-I", str(path)])
    return subprocess.run(command, capture_output=True, text=True, timeout=600)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--cuda-source",
        type=Path,
        default=Path("native/cuda-dda/src/dda_cuda.cu"),
    )
    arguments = parser.parse_args()

    source = arguments.cuda_source.read_text(encoding="utf-8")
    includes_root = arguments.cuda_source.resolve().parent

    with tempfile.TemporaryDirectory() as directory:
        work = Path(directory)
        (work / "cuda_stub.hpp").write_text(STUB, encoding="utf-8")
        unit = work / "dda_cuda_hostcheck.cpp"
        unit.write_text(host_translation_unit(source), encoding="utf-8")
        includes = [work, includes_root]

        vcvars = find_vcvars() if sys.platform == "win32" else None
        if vcvars is not None:
            completed = compile_msvc(vcvars, work, unit, includes)
            toolchain = "msvc"
        else:
            compiler = shutil.which("g++") or shutil.which("clang++")
            if compiler is None:
                print(
                    json.dumps(
                        {
                            "status": "skipped",
                            "reason": "no host C++ compiler available",
                            "cuda_compiled": False,
                            "cuda_executed": False,
                        },
                        indent=2,
                        sort_keys=True,
                    )
                )
                return 0
            completed = compile_posix(compiler, work, unit, includes)
            toolchain = Path(compiler).name

    report = {
        "status": "valid" if completed.returncode == 0 else "invalid",
        "schema": 1,
        "toolchain": toolchain,
        "cuda_source": str(arguments.cuda_source),
        "host_typecheck": completed.returncode == 0,
        "cuda_compiled": False,
        "cuda_executed": False,
        "claim_boundary": (
            "Host translation-unit type checking is not CUDA compilation, "
            "device parity, or performance evidence."
        ),
    }
    print(json.dumps(report, indent=2, sort_keys=True))
    if completed.returncode != 0:
        sys.stderr.write(completed.stdout + completed.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
