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

Run the repository fixture gate:

```powershell
.\gradlew.bat --no-daemon verifyCudaDdaResearch
```

When `nvcc` is unavailable, this command must say that the CUDA target is
disabled and run the CPU reference and host batch-plan CTests. That is a valid host/toolchain audit,
not CUDA parity evidence.

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

The current CUDA correctness executor still implements one device batch. It
uses the shared host planner before any CUDA allocation and rejects a corpus
that needs more than one bounded batch. This closes the unbounded-allocation
failure mode, but it does not yet execute a 100k-ray corpus. The next CUDA
implementation step is to execute every planned batch (or use two-pass
count/prefix-sum) while preserving the same CPU oracle and timing contract.

See
[`decision-D069-conditional-cuda-dda-prototype.md`](../../docs/acoustics/decision-D069-conditional-cuda-dda-prototype.md)
for the claim boundary and stop conditions.
