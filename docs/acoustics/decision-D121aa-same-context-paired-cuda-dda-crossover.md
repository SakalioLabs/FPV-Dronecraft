# D121aa — 同 context paired CUDA DDA 小批量 crossover

日期：2026-07-27

状态：`paired-device-matrix-verified / aggregate-gate=256-rays /
full-debug-gate=512-rays / minecraft-integration-rejected`

## 决策

在当前 research runner、RTX 3060、共享 WDDM 桌面与 current production corpus
条件下：

- aggregate-only CUDA 的最小研究 offload gate 冻结为 **256 rays**；
- full-topology/debug CUDA 的最小研究 offload gate 冻结为 **512 rays**；
- 当前约几十条 direct rays/update 仍使用 Java CPU DDA；
- 这两个 gate 只允许未来 early-reflection shadow mode 进入下一阶段，不允许同步
  接入 Minecraft audio/render thread。

gate 不是单档首次胜出，而是从该档开始连续三档同时满足：

1. 三轮 GPU submit P95 median 小于同时间窗 CPU P95 median；
2. 三轮 GPU submit P99 median 小于同时间窗 CPU P99 median；
3. 全部 90 个 GPU measured submit samples 对 50 ms research deadline 零 miss。

## 实验结构

`run_cuda_dda_nvrtc.py --output-mode paired` 现在：

- 只创建一个 CUDA context；
- 同一 module 同时加载 `trace_kernel` 与 `trace_aggregate_kernel`；
- 复用 cells、transmission、预展平 ray arrays、events、results 与 device
  allocations；
- full mode 独占 segment allocation/D2H，aggregate 传空 segment pointer；
- 每个 pass 按
  `full→aggregate / aggregate→full` 交替，消除固定先后偏差；
- 两个 mode 在首 pass 各自运行 CPU oracle parity；
- 报告 schema 2 保留每个 mode 的全部 raw stage/submit samples 与实际执行顺序。

formal wrapper 对
`128/256/512/1024/2048/4096/8192` 每档运行 3 个独立 paired GPU trials，
每 trial 为 `5 warmup + 30 measured`。CPU 使用同一 production bundle、
expected-results sidecar 与 MSVC Release C++20 单线程 executor。trial 1/3 为
GPU→CPU，trial 2 为 CPU→GPU。每个 trial 前后均保存有界 `nvidia-smi` 快照。

## 结果

| rays | CPU P95/P99 (ms) | full submit P95/P99 (ms) | full gate | aggregate submit P95/P99 (ms) | aggregate gate |
|---:|---:|---:|:---:|---:|:---:|
| 128 | 0.842 / 0.863 | 1.484 / 1.746 | fail | 1.144 / 1.487 | fail |
| 256 | 1.518 / 1.815 | 1.833 / 2.006 | fail | 1.157 / 1.379 | pass |
| 512 | 4.377 / 4.554 | 2.707 / 2.786 | pass | 1.356 / 1.408 | pass |
| 1,024 | 9.653 / 9.840 | 4.511 / 4.774 | pass | 2.115 / 2.139 | pass |
| 2,048 | 20.984 / 21.853 | 8.232 / 8.847 | pass | 1.962 / 2.250 | pass |
| 4,096 | 45.519 / 47.839 | 14.867 / 15.673 | pass | 2.433 / 2.438 | pass |
| 8,192 | 91.440 / 96.614 | 24.444 / 25.498 | pass | 4.521 / 5.209 | pass |

在 8192 rays，CPU P95 / aggregate submit P95 为 `20.23×`；full 为 `3.74×`。
full 的 D2H P95 median 随 prefix 从 128 档约 0.718 ms 上升到 8192 档约
21.560 ms；aggregate 同两档约 0.080→0.793 ms，继续确认 segment records 回读是
full 路径的主导成本。

所有 21 个 GPU trials 都满足：

- `shared_cuda_context=true`；
- strict alternating mode order；
- full 逐段 topology parity；
- aggregate counts/flags/first-hit/三频带 parity；
- corpus/snapshot/expected sidecar identity 不变；
- 前后 GPU snapshot 均为 valid；
- 每个 mode 共 630 measured submit samples，全矩阵 50 ms deadline 零 miss。

正式报告：

`build/research/cuda-dda-nvrtc-paired-small-prefix-matrix-v1.json`

- 401,872 bytes；
- SHA-256
  `c4aa48e761dde7879b7e296f7a8948991727499df96f44577ce3e49c5febb13a`；
- bundle SHA-256
  `83d73cdc86d90dd5dbe3ed049c92c8bd226679f4612b9d6811399cba7b89bcf2`；
- snapshot SHA-256
  `7952f0f752371a2d72149312b634312d666c0909961f78237f1c528f10d26a9d`。

## 负载与解释边界

显卡快照显示实验前段显存约 3.7–4.3 GiB，后段上升到约 7.0 GiB；GPU utilization
后段可达 90%，说明共享桌面仍有并发 GPU 活动。温度约 42–57°C，所有探针均及时
返回。CPU/GPU 紧邻执行、两级交替顺序和同-context full/aggregate 降低了跨时段
偏差，但不能把该结果称为独占 GPU、Minecraft native bridge 或 audio-thread
deadline 测量。

128-ray full 的 90 个 raw samples 中存在 9.615 ms raw P99 离群，但仍小于 50 ms；
该档同时在 median P95/P99 上输给 CPU，因此不会影响 gate。冻结阈值使用预先声明的
三档连续规则，没有根据单个离群点事后移动。

## 下一步

D121ab 应实现 Minecraft 外的 native boundary microbenchmark：

1. 比较 JNI、JNA 与现有 LWJGL CUDA Driver API 可行性；
2. 对 256/512/1024/2048 rays 测量 Java direct buffer
   pack→native submit→aggregate result unpack；
3. 同时测试 resident cell snapshot 与增量 chunk update，不能每 update 传 49,494
   cells；
4. 以 aggregate 256-rays gate 为下限，但把 native boundary 全成本纳入；
5. 只有 256/512/1024 连续三档仍胜过 Java CPU 且 50 ms 零 miss，才允许
   Minecraft shadow mode；音频仍消费 CPU 输出。

未启动 Minecraft，未打开 playback/capture endpoint，未修改音频热路径。
