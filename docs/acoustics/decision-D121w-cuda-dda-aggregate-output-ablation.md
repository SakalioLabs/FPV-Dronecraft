# D121w — CUDA DDA aggregate-only 输出消融

日期：2026-07-27

状态：`aggregate-parity-verified / d2h-bottleneck-removed /
end-to-end-gate-inconclusive`

## 决策

D121v 证明 full-topology CUDA DDA 正确，但每 pass 回读约 799 MB reserved segment
records，D2H P95 median `155.607 ms`。这类逐段输出只用于 correctness/debug，
产品直达声实际只需要：

- segment count 与 material cell count；
- reached/stopped/truncated；
- first effective material；
- 三频带 loss/gain。

D121w 因而保留 `trace_kernel` 作为 full-topology gate，并增加独立
`trace_aggregate_kernel`。二者使用同一 templated FP64 traversal；编译期
`if constexpr` 让 aggregate specialization 从 cubin 中完全删除 segment store，
而不是在每次 visit 做运行时空指针判断。

可重复入口：

```powershell
.\gradlew.bat --no-daemon benchmarkCudaDdaNvrtcAggregateCorpus
```

## 正确性合同

aggregate mode：

- 仍按同一 24-batch planner 遍历全部 cells/rays；
- 不分配 `DeviceSegment` buffer；
- `peak_segment_bytes=0`；
- `d2h_reserved_segment_bytes_per_pass=0`；
- 只回读 `100,008 × 72 = 7,200,576 bytes` result records；
- CPU oracle 仍重算全部隐式 segments，用于核对 segment count、material count、
  flags、first material 与三频带；
- 明确报告 `segment_topology_verified=false`，不冒充逐段 topology gate。

逐段 topology 的证明继续由 D121v full mode 提供。两种 mode 的 canonical fixture
均通过；aggregate 正式三轮每轮均验证
`100,008 rays / 14,172,009 implicit segments / 24 batches`，aggregate mismatch
为零。

## 正式三轮结果

合同仍为每轮一次 timing-excluded aggregate parity，加
`5 warmup + 30 measured` passes。

| metric | P50 min/median/max (ms) | P95 min/median/max (ms) | P99 min/median/max (ms) |
|---|---:|---:|---:|
| H2D | 3.950 / 4.861 / 5.523 | 4.997 / 5.874 / 6.511 | 6.137 / 7.012 / 7.117 |
| kernel | 29.613 / 33.236 / 88.887 | 31.779 / 35.086 / 130.100 | 33.416 / 35.623 / 134.302 |
| D2H | 3.392 / 4.202 / 5.708 | 4.113 / 5.847 / 7.152 | 4.761 / 6.673 / 7.426 |
| submit | 256.359 / 373.612 / 412.835 | 283.902 / 449.393 / 469.385 | 289.087 / 495.093 / 513.508 |

与 D121v full mode 的 P95 median 比较：

| stage | full (ms) | aggregate (ms) | change |
|---|---:|---:|---:|
| kernel | 44.194 | 35.086 | `-20.6%` |
| D2H | 155.607 | 5.847 | `-96.2%`，约 `26.6×` |
| submit-to-result | 373.223 | 449.393 | `+20.4%` |

前两项证明输出消融达到了目标：segment allocation 与大规模 D2H 已被删除。最后一项
没有通过端到端 gate，且不能用最好的一轮 `283.902 ms` 覆盖。

## 为什么 submit 反而没有稳定改善

三轮 aggregate kernel P95 分别约：

- `130.100 ms`；
- `31.779 ms`；
- `35.086 ms`。

第一轮是显著离群。GPU 快照同时观察到本轮环境约 `9.25–9.30 GiB` 显存占用，
而 D121v full run 多数约 `5.45 GiB`；快照功耗最高约 119 W、利用率最高 92%。
这说明当前共享桌面 WDDM GPU 状态发生了重大变化。

submit 还包含 Python 每 pass 重建 24 个 host ray arrays、参数对象、24 次 launch/
synchronize，以及操作系统 CPU scheduling。D2H 消失后，这些主机成本成为主要项；
不同 repeat 的 submit P95 为 `469.385 / 449.393 / 283.902 ms`，波动远大于传输
收益。当前两组实验不是同 context、同 P-state 的交替 paired A/B，不能从
`373.223→449.393 ms` 推导 aggregate 固有更慢。

因此：

- 接受“aggregate 消除了 segment D2H 瓶颈”；
- 接受“aggregate 三频带/flags/first hit 正确”；
- 拒绝“aggregate 已改善稳定端到端 latency”；
- 不与 D121t segment-retaining CPU baseline 计算 speedup，报告明确输出
  `cpu_comparison_workload_equivalent=false` 与 ratio `null`。

## 证据

正式报告：

`build/research/cuda-dda-nvrtc-aggregate-corpus-benchmark-v1.json`

- `20,711 bytes`；
- SHA-256：
  `db83bcb9e039c3de2e9229c53cf7ca3a208746ac5f0d9669f414f6a1aad93dc7`；
- `output_mode=aggregate`；
- `repeats=3 / warmup=5 / measured=30`；
- `nvrtc_compiled=true / cuda_executed=true / nvcc_compiled=false`。

## 下一步

D121x 应建立真正 paired 的同 context A/B：

1. cells/rays 预展平并常驻 host/device；
2. 同一 CUDA context 内按 trial 交替
   `full→aggregate` 与 `aggregate→full`；
3. full/aggregate 使用同一 ray order、batch plan、warmup、GPU clock state；
4. 分开报告 launch、kernel、D2H 与 host orchestration；
5. 同时补 compiled CPU aggregate-only prefix matrix；
6. 只有 paired P95/P99 与 deadline miss rate 通过，才实现异步 native bridge。

当前仍拒绝同步 Minecraft hot-path 接入，也未修改 Fabric/audio backend、启动
Minecraft client 或打开 playback/capture endpoint。
