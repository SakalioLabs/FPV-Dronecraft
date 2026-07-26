# Standalone CUDA voxel DDA research executor

This directory is an offline correctness/performance experiment. It is not
linked into Fabric, Minecraft, or the audio hot path.

## Targets

- `mcfpv_dda_cpu_reference`: always compiled as C++20. It strictly reads an
  `MCFPDDA1` bundle, optionally verifies its Java `MCFPREF1` sidecar, and
  reports CPU batch latency.
- `mcfpv_dda_batch_plan`: always compiled as C++20. It computes deterministic
  contiguous batches without allocating CUDA memory. Defaults are 8192 rays
  and 1,048,576 segment records (32 MiB at the frozen 32-byte record layout).
- `mcfpv_dda_cuda`: compiled only when CMake finds a CUDA compiler. Its FP64
  kernel emits every visited segment and compares topology, material data,
  flags, first hit, and three-band accumulation against the same CPU oracle.
- `dda_nvrtc_kernel.cu` plus `docs/scripts/run_cuda_dda_nvrtc.py`: an
  independent device-only NVRTC/Driver API path for hosts that have a
  compatible NVIDIA driver but no full CUDA Toolkit or `nvcc`.

Run the repository fixture gate:

```powershell
.\gradlew.bat --no-daemon verifyCudaDdaResearch
```

When `nvcc` is unavailable, this command must say that the CUDA target is
disabled and run the CPU reference and host batch-plan CTests. That is a valid host/toolchain audit,
not CUDA parity evidence.

Run the real CUDA device fixture through the pinned, gitignored Python/NVRTC
environment:

```powershell
.\gradlew.bat --no-daemon verifyCudaDdaNvrtcFixture
```

This gate creates or verifies `build/cuda-python-env`, compiles the
device-only kernel to the detected `sm_XX` cubin, loads it through the CUDA
Driver API, and compares every fixture segment and aggregate with the Python
CPU oracle. A passing report may claim `nvrtc_compiled=true` and
`cuda_executed=true`; it does not claim that the CMake CUDA target was built
with `nvcc`.

With a CUDA compiler and device available, the same command also builds and
runs `mcfpv_dda_cuda_java_fixture`. The executable accepts:

```text
mcfpv_dda_cuda
  [--warmup N]
  [--iterations N]
  [--maximum-rays-per-batch N]
  [--maximum-segments-per-batch N]
  [--expected-results <MCFPREF1 sidecar>]
  <MCFPDDA1 bundle>
```

The CUDA correctness executor consumes every batch from the shared host plan.
Device and host buffers are sized to the peak planned batch, ray segment
offsets are rebased for each batch, and every completed batch is compared
against the same CPU oracle before its buffers are reused. Stage timings are
reported as sums over all batches in one complete corpus pass.

Generate the current production-format 100,008-ray corpus and its Java CPU
sidecar:

```powershell
.\gradlew.bat --no-daemon verifyCudaDdaProductionCorpus
```

This single gate generates the corpus and sidecar, verifies both with Python
and compiled C++20 readers, runs the native regression tests, host-typechecks
the CUDA source, and prints the bounded batch plan. Generated artifacts stay
under ignored `build/research`. The reference corpus contains 49,494 cells and
100,008 rays. With the default 32 MiB segment budget it plans as 24 batches
instead of one 799,190,304-byte segment allocation. Java, Python, and compiled
C++20 readers agree on the bundle and expected-results hashes.

On hosts without `nvcc`, `typecheckCudaDdaHostLogic` rewrites CUDA-only syntax
to a host translation unit and compiles the host execution logic with the
ordinary C++ compiler using warnings as errors. This catches host-side
multi-batch type errors but is not CUDA compilation, device parity, or
performance evidence.

Record the repeat-aware CPU correctness baseline used by the future CUDA
crossover:

```powershell
.\gradlew.bat --no-daemon benchmarkCudaDdaCpuProductionCorpus
```

The task runs three repeats of 5 warmups plus 30 measured full-corpus batches,
requires identical workload identity and checksum in every repeat, and reports
the raw runs plus min/median/max. This is a single-threaded correctness-oracle
baseline that retains every segment, not the optimized Minecraft hot path.

See
[`decision-D069-conditional-cuda-dda-prototype.md`](../../docs/acoustics/decision-D069-conditional-cuda-dda-prototype.md)
for the original claim boundary, and
[`decision-D121s-cuda-multi-batch-corpus.md`](../../docs/acoustics/decision-D121s-cuda-multi-batch-corpus.md)
for the production corpus and bounded multi-batch implementation, and
[`decision-D121u-nvrtc-cuda-dda-fixture-parity.md`](../../docs/acoustics/decision-D121u-nvrtc-cuda-dda-fixture-parity.md)
for the first real RTX 3060 device parity evidence.
