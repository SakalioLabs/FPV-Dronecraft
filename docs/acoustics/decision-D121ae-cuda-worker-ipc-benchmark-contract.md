# D121ae — CUDA worker IPC parity/performance contract

日期：2026-07-27

状态：`benchmark-harness-ready / cuda-execution-deferred /
no-performance-claim`

## 决策

D121ad 已证明独立 worker 可以在 timeout、crash、worker error 和 generation
mismatch 后 fail closed 到 CPU。D121ae 补齐下一阶段真实 GPU 验证所需的可执行
benchmark contract，但不在已知不稳定的驱动状态上启动 CUDA。

新增 Gradle task：

```text
:fabric-mod:cudaDdaWorkerBenchmark
```

它只运行离线 Java CLI，不启动 Minecraft，也不打开 playback/capture endpoint。
task 依赖：

- MSVC `/W4 /WX /permissive-` 构建的 `mcfpv_cuda_worker.exe`；
- 固定 NVRTC 13.3 runtime；
- 当前 `dda_nvrtc_kernel.cu`；
- 当前 production snapshot/ray bundle。

## 执行合同

默认正式矩阵为：

- prefixes：`256 / 512 / 1024 / 2048 rays`；
- `5 warmup + 30 measured`；
- CPU→worker / worker→CPU 逐次交替；
- driver preflight deadline：5 s；
- worker initialization deadline：10 s；
- warm submit deadline：50 ms。

初始化 payload 包含完整 ordered sparse snapshot、material IDs、fill fractions、
三频带 transmission table 和 maximum rays。worker 回传的 resident cell count 与
maximum rays 必须与请求一致，否则立即拒绝。

每个 prefix 在 timing 前执行完整 parity：

- visited cell count；
- material cell count；
- reached/truncated flags；
- first-material packed cell；
- low/mid/high transmission loss；
- low/mid/high transmission energy gain。

oracle 为当前 Java `DirectPathSolver` 与 `VoxelDda`。任何 watchdog failure、非零
worker status、payload 长度错误或 parity mismatch 都终止运行，不写
`status=valid` 报告。

## 计时与报告

每次 measured worker submit 分别记录：

- Java ray packing；
- framed stdio request/response round trip；
- native H2D；
- CUDA aggregate kernel；
- native D2H；
- native total；
- Java result unpack；
- pack + IPC round trip + unpack 的完整 Java total。

每个阶段保留所有 samples，并报告 P50/P95/P99。报告还冻结 worker、NVRTC、
kernel、bundle 与 snapshot hashes、Driver/NVRTC/compute-capability、resident
counts、deadline、parity 状态和 checksum。

这里的 IPC round trip 已包含：

- Java frame/header/checksum；
- anonymous pipe write/read；
- worker dispatch；
- native bridge call；
- response frame/checksum；
- Java payload allocation。

因此它比 D121ac 的进程内 JNI bridge 更接近产品边界，但仍不包含 Minecraft
scheduler、snapshot delta transport、latest-wins queue 或音频消费。

## 当前验证

- `CudaDdaWorkerBenchmark` 已通过 Java 编译；
- benchmark Gradle task graph 已通过 `--dry-run`；
- Fabric tests 已通过；
- contract test 确认 driver preflight、initialize/submit、CPU oracle parity、
  paired order、完整 stage timing 和 no-Minecraft/no-endpoint 声明存在；
- per-request deadline 现可与 client default deadline 分离；
- zero、negative 或无法装入 protocol millisecond 字段的 deadline 会在启动
  worker 前 fail closed。

本轮没有运行 `cudaDdaWorkerBenchmark`，所以没有 CUDA、parity 或 performance
结果，也没有生成正式 IPC JSON。不得从“harness 已准备”推断 CUDA worker 已达到
50 ms deadline 或优于 Java CPU。

## 下一门槛

真实执行前需要确认系统经历了能够清除 D121ac driver hang 的恢复事件，并先通过
bounded driver/fixture smoke。之后按三个独立 Java 21 JVM 运行完整矩阵。只有三轮
都满足以下条件才能冻结 gate：

1. 所有 prefix parity 全通过；
2. 所有 360 个 measured worker submits 对 50 ms deadline 零 miss；
3. P95 与 P99 相对同窗 Java CPU 均有正 margin；
4. 无 worker restart、protocol failure 或 checksum mismatch；
5. 三轮 hashes、resident counts 和 checksum 一致。

通过后仍只允许 Minecraft 外 shadow-mode adapter 的开发；production 音频消费
需要后续 bounded/latest-wins scheduler 与真实 Minecraft telemetry。
