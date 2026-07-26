# D121x — 8k→100k compiled CPU / CUDA DDA 前缀 crossover 筛查

日期：2026-07-27

状态：`cpu-prefix-matrix-verified / gpu-faster-at-all-screened-prefixes /
crossover-below-8192-unresolved`

## 决策

D121v 的 GPU prefix matrix 缺少相同 ray keys 的 compiled CPU 对照。D121x 给
`mcfpv_dda_cpu_reference` 增加 fail-closed `--ray-limit`：

- 只执行 production bundle 的确定性前缀；
- `ray_limit > bundle_rays` 拒绝；
- 输出同时保存 `bundle_rays` 与实际执行 `rays`；
- checksum、P50/P95/P99 与 ns/ray 都只覆盖该前缀；
- expected-results sidecar 仍全量复核，但 verify 时间不计入 batch timing。

CMake 增加 `ray-limit=2` 正控与 `ray-limit=4` 超 fixture 负控。当前 native CTest
为 `6/6` 通过。

可重复入口：

```powershell
.\gradlew.bat --no-daemon benchmarkCudaDdaCpuPrefixMatrix
```

该任务要求先存在 D121v GPU scaling report，并以 SHA-256 绑定它；不会在 CPU
benchmark 中重新运行 CUDA。

## 合同

每个 prefix：

- compiled MSVC Release C++20；
- 单线程 CPU oracle；
- 保留每条射线的全部 actual segments；
- 3 个独立进程；
- 每进程 5 warmup + 30 measured passes；
- 三轮 snapshot、bundle ray count、executed ray count 与 checksum 完全一致；
- 保存每轮原始输出与跨轮 min/median/max。

CPU：Intel Core i7-14700KF。没有固定 affinity、priority、frequency 或关闭后台
进程，因此仍是共享 Windows desktop 筛查。

## 结果

| rays | CPU P50 median (ms) | CPU P95 median (ms) | CPU P99 median (ms) | CPU P95 rays/s | GPU submit P95 (ms) | CPU/GPU P95 |
|---:|---:|---:|---:|---:|---:|---:|
| 8,192 | 61.148 | 64.074 | 64.464 | 127,852 | 28.878 | 2.219× |
| 16,384 | 128.418 | 133.308 | 135.744 | 122,903 | 57.688 | 2.311× |
| 32,768 | 262.232 | 268.599 | 281.202 | 121,996 | 110.730 | 2.426× |
| 65,536 | 508.772 | 532.197 | 540.944 | 123,142 | 243.103 | 2.189× |
| 100,008 | 796.681 | 813.781 | 828.330 | 122,893 | 373.223 | 2.180× |

CPU P95 min/median/max：

| rays | P95 (ms) |
|---:|---:|
| 8,192 | 60.738 / 64.074 / 66.654 |
| 16,384 | 130.001 / 133.308 / 135.180 |
| 32,768 | 266.509 / 268.599 / 276.676 |
| 65,536 | 518.743 / 532.197 / 537.324 |
| 100,008 | 813.414 / 813.781 / 819.455 |

CPU throughput 从 16k 到 100k 基本稳定在约 122k–123k rays/s，8k 略高，说明
compiled CPU correctness oracle 在本区间近似线性。对应 GPU full-topology
submit 在所有五档都更快，筛查比值为 `2.18×–2.43×`。因此：

- 没有在 `8192..100008` 范围内观察到 crossover；
- crossover 若存在，只能低于 8192 rays；
- 当前不能把 8192 当作 GPU 最小有效批量，只能说它已经位于 GPU 优势区。

## 环境差异

D121t 同一 100,008-ray CPU P95 median 为 `1206.2 ms`；本轮为 `813.781 ms`，
本轮快约 32.5%。算法、bundle 与编译器 workload 没有改变，差异来自不受控桌面
调度/频率/后台负载。

GPU prefix 的 8k/16k/32k/65k 行是 D121v 单进程诊断，只有 100,008 行来自三轮
formal median；CPU 与 GPU 也不是同时交替运行。因此表中的 ratio 是筛查证据，
不是 release crossover threshold。D121w 中外部 LoRA 训练导致的 GPU 大离群也再次
说明必须使用 paired trial。

## 证据

报告：

`build/research/cuda-dda-cpu-prefix-matrix-v1.json`

- `13,434 bytes`；
- SHA-256：
  `b1a39571886a0e948ae335a5d7832c280f65f80c4f6290a9859cf6face2415df`；
- 绑定 GPU matrix SHA-256：
  `b3dcc5a67326e6b781e7a443ace8ec57409413508c911430843d114ba452bd19`。

## 下一步

D121y 应在 GPU driver 恢复且无外部 compute workload 后：

1. 扩展 prefix 到 `128/256/512/1024/2048/4096/8192`；
2. 同一进程/context 中交替 CPU/full/aggregate；
3. host rays 只预展平一次，分开报告 prep 与 submit；
4. 每个 trial 记录 GPU utilization/memory/P-state proxy；
5. 以 deadline miss rate 决定 asynchronous shadow-mode 最小批量；
6. 当前实际直达声约几十条 rays/update，仍坚持 Java CPU DDA，不因大批量结果改为
   CUDA。

本轮没有运行 Minecraft、没有访问音频 endpoint，也没有修改 Fabric/audio backend。
