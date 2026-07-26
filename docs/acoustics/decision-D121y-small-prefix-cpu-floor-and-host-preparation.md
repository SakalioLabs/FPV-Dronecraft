# D121y — 128→8192 CPU 基线与 CUDA host 预展平合同

日期：2026-07-27

状态：`cpu-small-prefix-matrix-verified /
host-preparation-contract-implemented / gpu-measurement-deferred`

## 决策

D121x 只证明 crossover 低于 8192 rays。D121y 先冻结未来 GPU 小批量实验必须击败
的 compiled CPU floor，并消除已知的 Python host orchestration 混杂项。

CPU prefixes：

`128 / 256 / 512 / 1024 / 2048 / 4096 / 8192`

每档仍为：

- MSVC Release C++20 单线程 oracle；
- 3 个独立进程；
- 每进程 `5 warmup + 30 measured`；
- 全量 expected-results sidecar 身份复核不计入 batch timing；
- 保留全部 actual segments；
- 三轮 checksum、snapshot 与 prefix identity 一致。

## CPU 结果

| rays | P50 median (ms) | P95 median (ms) | P99 median (ms) | P95 rays/s |
|---:|---:|---:|---:|---:|
| 128 | 0.8288 | 1.2792 | 1.3361 | 100,063 |
| 256 | 1.7433 | 2.5399 | 2.8074 | 100,791 |
| 512 | 3.7571 | 4.5508 | 4.6703 | 112,508 |
| 1,024 | 7.9196 | 11.2060 | 11.8674 | 91,380 |
| 2,048 | 17.5796 | 25.4101 | 26.2266 | 80,598 |
| 4,096 | 35.7294 | 49.5976 | 50.0555 | 82,585 |
| 8,192 | 73.0550 | 85.8645 | 102.6897 | 95,406 |

这些是未来 paired GPU submit P95 必须低于的同 prefix targets。不同 prefix 的
ray/material 分布不同，不能把吞吐变化全部解释为固定开销；尤其 1024–4096 档的
较低 rays/s 可能同时包含路径长度分布和共享桌面调度影响。

报告：

`build/research/cuda-dda-cpu-small-prefix-matrix-v1.json`

- `17,482 bytes`；
- SHA-256：
  `8c4b270a79314dc29c2780412a66478611c39ec156262a0e0cdd3bf1358e610e`；
- `gpu_matrix_sha256=null`，明确是 CPU-only floor，不伪造未运行的 GPU keys。

## Host preparation 合同

原 NVRTC runner 的 `submit-to-result` 每 pass 都用 Python 循环重建所有 batch ray
arrays 与 segment offsets。D121w 已证明当 D2H 被消除后，这部分会主导并产生巨大
跨轮方差。

新增：

```text
--host-preparation per-pass  # 历史 correctness workload，默认
--host-preparation once      # paired/product-style 诊断
```

`once` 模式在任何 measured pass 前：

- 按 batch plan 构造固定 NumPy ABI arrays；
- 为每批从零重基准 segment offsets；
- 复核 offsets 总和等于 batch reserved segment count；
- 后续 pass 只复用不可变 host arrays；
- 单独报告 `host_prepare_ms`，不混入 submit samples。

纯函数测试覆盖跨两个 batches 的 offset `0,3 / 0` 与 maximum-cells identity。
静态 source-contract 同时要求 preparation helper、CLI mode 与复用路径存在。

## 为什么本轮未运行 GPU 小矩阵

上一轮共享 RTX 3060 上有另一个工作区的 LoRA 训练。训练结束后：

- `nvidia-smi` 查询连续两次在 30 秒超时；
- 前一诊断 context 的 Windows process object 显示 `HasExited=true`，但驱动管理
  查询仍无响应；
- 新增的外部 Torch CUDA availability probe 也停留在驱动初始化。

继续创建 CUDA context 会增加驱动残留与无效数据风险。本轮没有终止或修改外部训练
任务，也没有把驱动异常期间的时间记入研究结论。

因此：

- CPU 小批量 floor 已验证；
- host-preparation implementation 已完成纯主机回归；
- `host-preparation=once` 的 CUDA device timing 仍为未验证；
- 128→8192 GPU crossover 仍未关闭。

## 下一步

驱动恢复后，D121z 应：

1. 先运行 1024-element smoke 和 canonical 3-ray full/aggregate parity；
2. 对 128→8192 每档在同一 context 使用预展平 rays；
3. 按 trial 交替 full/aggregate 顺序；
4. CPU 与 GPU 同一时间窗运行并记录环境快照；
5. 冻结第一个连续三档均通过 P95/P99 与 deadline miss gate 的 crossover；
6. 不把该 crossover 用于当前几十条 direct rays/update；它只决定 early-reflection
   shadow-mode 的最小 offload batch。

未启动 Minecraft，未访问 playback/capture endpoint，也未修改音频热路径。
