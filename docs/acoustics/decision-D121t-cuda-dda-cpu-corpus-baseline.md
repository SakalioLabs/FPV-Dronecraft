# D121t — 100k CUDA DDA corpus 的 CPU correctness 基线

日期：2026-07-27

状态：`verified-cpu-baseline / cuda-uncompiled / cuda-unexecuted`

## 决策

D121s 已冻结与 CUDA 共用的 `100,008 rays / 49,494 cells` production corpus，
但尚无可比较的 CPU 时间基线。单次运行会受桌面调度、频率和热状态影响，不能把一个
最好值当作 crossover。因此新增三轮重复基线：

- 每轮 5 次 warmup；
- 每轮 30 次 measured full-corpus batches；
- 每轮先复核同一 404,194,220-byte expected-results sidecar；
- 三轮强制 snapshot generation/hash、rays、cells 与 checksum 完全一致；
- 输出每轮原始值，并对 P50/P95/P99、parse 和 sidecar verify 报告
  minimum/median/maximum。

可重复入口：

```powershell
.\gradlew.bat --no-daemon benchmarkCudaDdaCpuProductionCorpus
```

该入口先完整运行 D121s 的 100k corpus gate，再执行基线。报告写入已忽略的
`build/research/cuda-dda-cpu-production-corpus-benchmark-v1.json`。

## 工作负载

执行器是 MSVC Release 构建的 `mcfpv_dda_cpu_reference`，单线程遍历全部
100,008 条射线，并为每条射线保留所有 visited segments。它使用与 CUDA
correctness executor 相同的 C++ CPU oracle，不是产品 Minecraft Java 直达声热路径。

本机 CPU：`Intel Core i7-14700KF / 20 cores / 28 logical processors`。报告内的
Windows processor identifier 为
`Intel64 Family 6 Model 183 Stepping 1, GenuineIntel`。未固定核心 affinity、进程
priority、CPU frequency、风扇或系统后台负载，所以这是当前桌面环境基线，不是隔离
实验室 benchmark。

## 结果

三个独立 repeat：

| repeat | batch P50 (ms) | batch P95 (ms) | batch P99 (ms) | P95 ns/ray |
|---:|---:|---:|---:|---:|
| 1 | 850.0525 | 1210.3220 | 1272.7716 | 12102.252 |
| 2 | 935.0673 | 1206.2000 | 1208.1123 | 12061.035 |
| 3 | 872.4689 | 1050.2913 | 1054.4735 | 10502.073 |

聚合：

- P50：`850.0525 / 872.4689 / 935.0673 ms`（min/median/max）；
- P95：`1050.2913 / 1206.2000 / 1210.3220 ms`；
- P99：`1054.4735 / 1208.1123 / 1272.7716 ms`；
- P95 median throughput：约 `82,912 rays/s`；
- P95 worst-repeat throughput：约 `82,629 rays/s`；
- P50 median throughput：约 `114,626 rays/s`；
- bundle parse：`51.5373 / 52.5540 / 55.8119 ms`；
- sidecar verify：`2694.1740 / 2865.4485 / 2933.6026 ms`，不计入 batch timing；
- 三轮 checksum：
  `508781072.167648`，完全一致。

报告为 3,060 bytes，SHA-256：
`cba8e030490809de620cecce5caa8100d5963450cff9584bacaea47b7a6799b5`。

## CUDA crossover 合同

后续 GPU 结果必须满足：

1. 使用完全相同的 bundle SHA-256
   `83d73cdc86d90dd5dbe3ed049c92c8bd226679f4612b9d6811399cba7b89bcf2`；
2. 使用完全相同的 expected-results SHA-256
   `5f8f538be276f3efdeafedfe0601982a5dc1fb044dd1bf45b19a8ae400a7994d`；
3. 每轮覆盖全部 24 个默认 batches 与全部 100,008 rays；
4. parity 必须覆盖每批 topology、segments、materials、flags、first hit 和三频带；
5. submit-to-result 必须包含所有 sub-batches，不能只报告单个 kernel；
6. 至少三轮独立 repeat，每轮 warmup/measurement 数与 CPU 相同；
7. 同时报告 P50/P95/P99 与每轮原始值，不选取单一最好值；
8. expected sidecar verify、文件 parse 与正式 batch timing 分开报告。

CPU P95 median `1206.2 ms` 是 correctness workload 的当前对照值，不是产品帧预算。
即使 CUDA 大幅快于该值，也不自动证明值得接入 Minecraft：还必须加上 native bridge、
snapshot delta upload、scheduler 与同步开销，并证明实际 early-reflection workload
稳定达到适合 GPU 的批量。

## 证据边界

- `cuda_compiled=false`；
- `cuda_executed=false`；
- 未测 H2D/kernel/D2H；
- 未建立 CPU↔CUDA crossover；
- CPU reference 单线程且保留全部 segments，不代表优化 CPU 上限；
- 未启动 Minecraft client，未打开 playback/capture endpoint；
- 报告在 `build/` 下，不提交大型生成证据；文档冻结哈希与统计量。
