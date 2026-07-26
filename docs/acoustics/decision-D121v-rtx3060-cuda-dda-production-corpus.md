# D121v — RTX 3060 CUDA DDA 100k 全语料一致性与性能矩阵

日期：2026-07-27

状态：`production-corpus-device-parity-verified /
repeat-benchmark-complete / synchronous-hot-path-rejected`

## 决策

D121u 只关闭了 3-ray fixture 的 device execution 风险。D121v 使用 D121s/t
冻结的完全相同 production corpus，在 RTX 3060 上执行全部有界 batches：

- bundle SHA-256：
  `83d73cdc86d90dd5dbe3ed049c92c8bd226679f4612b9d6811399cba7b89bcf2`；
- snapshot SHA-256：
  `7952f0f752371a2d72149312b634312d666c0909961f78237f1c528f10d26a9d`；
- `49,494 cells / 100,008 rays`；
- `24,974,697 reserved segments / 14,172,009 actual segments`；
- 默认 `24 batches / peak 4,200 rays / peak 1,048,497 segments`；
- peak segment buffer `33,551,904 bytes`。

每个独立 repeat 都重新创建进程和 CUDA context、由 NVRTC 编译 `sm_86` cubin，
执行一次完整 CPU↔GPU parity，再做 `5 warmup + 30 measured` full-corpus passes。
parity 不计入 timing；每轮仍保留 30 个 H2D/kernel/D2H/submit-to-result 原始样本。

可重复入口：

```powershell
.\gradlew.bat --no-daemon benchmarkCudaDdaNvrtcProductionCorpus
```

## 正确性结果

三轮均得到：

- `verified_rays=100008`；
- `verified_segments=14172009`；
- 逐 segment packed topology、length、material id 与 fill fraction 一致；
- reached/stopped/truncated、material cell count 与 first material 一致；
- 三频带 loss/gain 在冻结容差内一致；
- workload、device、bundle、snapshot、batch count 与 peak allocation identity
  跨轮完全相同。

三轮 timing 外 CPU parity 分别耗时约
`54.082 / 52.527 / 53.062 s`。这不是 GPU latency；它是 Python oracle 重算
1,417 万段并逐项比较的证据成本。

因此当前精确状态为：

- `nvrtc_compiled=true`；
- `cuda_executed=true`；
- `production_corpus_device_parity_verified=true`；
- `nvcc_compiled=false`。

## 三轮正式性能

本机为 NVIDIA GeForce RTX 3060 12 GiB、compute capability 8.6、Driver API
13030、NVRTC 13.3。下表给出三个独立 repeat 的 full-corpus P50/P95/P99：

| repeat | kernel (ms) | D2H (ms) | submit-to-result (ms) |
|---:|---:|---:|---:|
| 1 | 26.451 / 37.439 / 50.281 | 139.256 / 153.154 / 165.275 | 346.571 / 363.906 / 366.040 |
| 2 | 28.088 / 44.194 / 45.070 | 138.070 / 155.607 / 160.405 | 344.477 / 373.223 / 379.400 |
| 3 | 32.126 / 51.136 / 57.336 | 144.604 / 171.313 / 172.402 | 351.091 / 404.050 / 405.835 |

跨轮 min/median/max：

| metric | P50 (ms) | P95 (ms) | P99 (ms) |
|---|---:|---:|---:|
| H2D | 3.117 / 3.253 / 3.293 | 3.771 / 3.911 / 5.050 | 4.235 / 4.258 / 5.074 |
| kernel | 26.451 / 28.088 / 32.126 | 37.439 / 44.194 / 51.136 | 45.070 / 50.281 / 57.336 |
| D2H | 138.070 / 139.256 / 144.604 | 153.154 / 155.607 / 171.313 | 160.405 / 165.275 / 172.402 |
| submit | 344.477 / 346.571 / 351.091 | 363.906 / 373.223 / 404.050 | 366.040 / 379.400 / 405.835 |

median P95 throughput：

- kernel-only：约 `2,262,909 rays/s`；
- Python Driver API submit-to-result：约 `267,958 rays/s`。

D121t compiled single-threaded CPU correctness P95 median 为 `1206.2 ms`。同一
segment-retaining correctness 场景下，CPU P95 / GPU submit P95 median 为
`3.232×`。即便采用 CPU 最快 repeat P95 `1050.291 ms` 与 GPU 最慢 repeat P95
`404.050 ms` 的保守交叉比较，仍约为 `2.60×`。

这不是产品热路径 speedup：CPU reference 单线程；GPU submit 由 Python 编排；
两者都为了 correctness 保留全部 segments。

## 为什么当前实现仍不能进同步热路径

当前 D2H 每批复制其 reserved segment capacity，而不是只复制实际 1,417 万段；
完整 pass 的 segment reservation 为 `799,190,304 bytes`，另有约 7.2 MB result
records。D2H P95 median `155.607 ms`，是 kernel P95 median `44.194 ms` 的约
3.52 倍，已经成为主瓶颈。

此外：

- kernel P95 median `44.194 ms` 虽略低于 50 ms Minecraft tick，但最慢 repeat
  P95 为 `51.136 ms`；
- submit P95 median `373.223 ms`，约为 50 ms tick 的 `7.46×`；
- Python host flatten、24 次 launch 与同步也包含在 submit-to-result；
- 当前桌面并非隔离 benchmark。GPU 快照观察到约 `5.45–6.91 GiB` 已用显存、
  `28–92%` 瞬时 utilization、`41–53 °C` 与 `18–91 W`，后台图形负载未受控。

因此拒绝把这个 executor 同步接入每 tick Minecraft audio hot path。正确下一步
不是先做 JNI，而是先减少输出：

1. production kernel 只回传直达声所需 aggregate、first hit 与少量诊断；
2. full segment topology 仅在 correctness/debug gate 中启用；
3. 若必须保留变长 segments，使用 count pass + prefix sum/compaction，只 D2H
   actual records；
4. cells 常驻 GPU，只上传 chunk/material delta；
5. 使用异步 stream 与双缓冲，测量 deadline/queue age，而不是同步 wall time。

## 8k→100k 前缀缩放矩阵

`8192/16384/32768/65536` 为确定性 corpus prefixes，各执行一次
`3 warmup + 10 measured` 并完成逐段 parity；100,008 项使用上面的三轮 formal
median：

| rays | actual segments | batches | kernel P95 (ms) | D2H P95 (ms) | submit P95 (ms) | submit rays/s |
|---:|---:|---:|---:|---:|---:|---:|
| 8,192 | 1,160,233 | 2 | 2.590 | 13.091 | 28.878 | 283,679 |
| 16,384 | 2,307,809 | 4 | 3.729 | 26.285 | 57.688 | 284,011 |
| 32,768 | 4,652,901 | 8 | 7.457 | 46.459 | 110.730 | 295,928 |
| 65,536 | 9,278,113 | 16 | 34.062 | 104.717 | 243.103 | 269,581 |
| 100,008 | 14,172,009 | 24 | 44.194 | 155.607 | 373.223 | 267,958 |

submit throughput 在 8k 已约 284k rays/s，32k 单次诊断达到约 296k rays/s，继续增大
并没有提升端到端吞吐。65k 的 kernel P95 跳升来自单进程诊断且可能受桌面负载影响，
不能解释为稳定架构断点。矩阵只说明 `>=8192` 已足够摊薄固定开销；实际 crossover
仍需同样 prefix 的 compiled CPU 测量。

## 证据文件

生成证据位于 gitignored `build/research`：

- 正式三轮报告：
  `cuda-dda-nvrtc-production-corpus-benchmark-v1.json`，
  `20,127 bytes`，SHA-256
  `390130fbf5eeb1d223f3f580f7bbc337f4f268b871f68aed58b664dc5428e1fe`；
- scaling matrix：
  `cuda-dda-nvrtc-scaling-matrix-v1.json`，
  `18,194 bytes`，SHA-256
  `b3dcc5a67326e6b781e7a443ace8ec57409413508c911430843d114ba452bd19`。

## 边界

- 未构建 `nvcc`/CMake CUDA target；
- 未做 Nsight profile、pinned host memory、async copy、compaction 或 SoA 优化；
- 未测 JNI/JNA/native bridge；
- 未接入 Fabric scheduler、Minecraft world snapshot delta 或 audio backend；
- 未启动 Minecraft client，未打开 playback/capture endpoint，未捕获音频；
- 没有 release calibration 或 NVIDIA-only backend 发布决策。

当前产品决策仍是：小规模直达声使用 Java CPU DDA；CUDA 只保留为大批量 early-
reflection/shadow-mode 候选。D121w 应先补同样五个 prefix 的 compiled CPU
baseline，并实现 aggregate-only/compacted-output A/B，之后才决定 native bridge
是否值得。
