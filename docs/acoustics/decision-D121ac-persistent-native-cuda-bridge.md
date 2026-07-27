# D121ac — 常驻 native CUDA aggregate bridge

日期：2026-07-27

状态：`real-native-cuda-bridge-verified / 256-ray-gate-passed /
in-process-product-integration-rejected`

## 决策

真实 Java→native→CUDA aggregate 路径在 Windows RTX 3060 和 Minecraft 自带
Java 21.0.7 runtime 上技术可行，且从 256 rays 起明显快于当前 Java CPU DDA。

但是，本轮也复现了 NVIDIA 驱动在健康预检之后失去响应、使 native call 与同机
`nvidia-smi` 一起无限等待的故障。进程内 JNI/LWJGL 无法安全取消卡在驱动中的 native
thread。因此：

- 接受 native bridge 的 correctness 与 performance 证据；
- 保持最小已证明 offload gate 为 **256 rays**；
- 不接受把该 DLL 直接加载到 Minecraft JVM 的产品方案；
- 当前几十条 direct rays/update 继续使用 Java CPU；
- 下一阶段必须改为可被 watchdog 强制回收的 **out-of-process CUDA worker**；
- worker 超时或退出时 Minecraft 只消费 CPU fallback，不等待 GPU。

## 实现

`native/cuda-dda-boundary` 新增 ABI version 1 的真实 bridge：

- 动态加载 `nvcuda.dll`，不需要 CUDA Toolkit 或 `cuda.h`；
- 动态加载固定 `nvrtc64_130_0.dll` 及同目录 builtins；
- 查询 Driver API、compute capability 与 NVRTC version；
- NVRTC 编译现有 `dda_nvrtc_kernel.cu`；
- 创建并常驻 CUDA context/module；
- 一次上传 49,494-cell snapshot 与 8×3 transmission table；
- 常驻最大 ray/result device buffers；
- 每次 submit 只执行 ray H2D、aggregate kernel、result D2H；
- 返回独立 H2D/kernel/D2H/native-total timings；
- 所有 Driver/NVRTC 操作 fail closed，并通过 thread-local error string 返回细节；
- destroy 释放 device allocations、module、context 与动态库。

Java research CLI：

- 读取 current production bundle；
- 固定 24-byte cell、64-byte ray、72-byte result ABI；
- 使用 direct `ByteBuffer` 和 LWJGL `JNI.invokePPI`；
- 每个 prefix 首 pass 由 Java `DirectPathSolver` 重新计算并验证：
  segment count、material cells、reached/truncated flags、first material packed cell、
  三频带 loss/gain；
- measured iteration 按 CPU→GPU / GPU→CPU 交替；
- 分别记录 Java pack、JNI/native call、native H2D/kernel/D2H、unpack、完整
  Java total 与 Java CPU batch；
- 输出明确声明 Minecraft/audio endpoint 未启动。

## Java 21 正式矩阵

环境：

- Minecraft runtime OpenJDK `21.0.7+6-LTS`；
- LWJGL `3.3.3+5`；
- RTX 3060 / compute capability 8.6；
- Driver API 13030；
- NVRTC 13.3；
- production bundle `100,008 rays / 49,494 cells`；
- 3 个独立 JVM；
- 每档 `5 warmup + 30 measured`；
- 每档 90 个 GPU 与 90 个 Java CPU samples。

三轮 P95/P99 median：

| rays | Java CPU P95/P99 | complete CUDA bridge P95/P99 | CPU/GPU P95 | gate |
|---:|---:|---:|---:|:---:|
| 256 | 6.385 / 8.126 ms | 1.463 / 1.487 ms | 4.36× | pass |
| 512 | 6.551 / 6.947 ms | 1.439 / 1.670 ms | 4.55× | pass |
| 1,024 | 10.674 / 11.160 ms | 1.950 / 2.037 ms | 5.47× | pass |
| 2,048 | 22.372 / 22.531 ms | 1.440 / 1.880 ms | 15.53× | pass |

完整 CUDA bridge 包含 Java pack、JNI call、ray H2D、kernel、result D2H 与 Java
unpack。四档 GPU max 分别为 `1.625/1.728/2.247/1.995 ms`，全部 360 个
GPU samples 对 50 ms deadline 零 miss；Java CPU 也零 miss。

2048-ray P95 较 1024-ray 更低，主要来自 ascending-prefix JIT/tiering 与共享桌面
调度，不能解释为负 scaling。raw samples 全部保留，不从四点拟合 throughput 模型。

256/512/1024/2048 的阶段 P95 median：

| rays | pack | H2D | kernel | D2H | native total | unpack |
|---:|---:|---:|---:|---:|---:|---:|
| 256 | 0.119 ms | 0.116 ms | 1.016 ms | 0.086 ms | 1.131 ms | 0.066 ms |
| 512 | 0.209 ms | 0.146 ms | 1.073 ms | 0.095 ms | 1.173 ms | 0.072 ms |
| 1,024 | 0.433 ms | 0.146 ms | 0.947 ms | 0.116 ms | 1.171 ms | 0.159 ms |
| 2,048 | 0.161 ms | 0.156 ms | 1.038 ms | 0.126 ms | 1.220 ms | 0.088 ms |

bridge create（动态加载、NVRTC compile、context/module、resident upload）median
为 `408.366 ms`。它只能异步执行一次，不能出现在 tick、render 或 audio hot path。

正式忽略报告：

- run 1：15,285 bytes，SHA-256
  `62262e60ff300c091a946c5b88f5fd1ec444cb71a642a6e561e55795a02d4a94`
- run 2：15,266 bytes，SHA-256
  `3ee4dc8d3471f4010b2f330ebe06ca37fb1d229979241d0865c409e1c95a7dff`
- run 3：15,337 bytes，SHA-256
  `562b68904fbfbf974dd408a617734eada2054bbcf580fe49abe6aeab07a8c099`
- 三轮 checksum：
  `7962870113440342783`
- bundle SHA-256：
  `83d73cdc86d90dd5dbe3ed049c92c8bd226679f4612b9d6811399cba7b89bcf2`
- snapshot SHA-256：
  `7952f0f752371a2d72149312b634312d666c0909961f78237f1c528f10d26a9d`
- kernel SHA-256：
  `871db055260987b9efb923b1a4473717d6c22ac95303f9e111b466460d28d1c8`

## JDK 25 对照

相同三轮在 Gradle JDK 25.0.1 也完成并通过 parity，但 LWJGL 3.3.3 打印
`Unsupported JNI version`。Java 21 canonical runs 没有该警告。因此产品支持面
继续冻结在 Minecraft 实际 Java 21 runtime；JDK 25 结果只作诊断，不作 release
证据。

## 驱动挂起负证据

在完成上述 Java 21 正式矩阵后，尝试定位 32/64/128-ray crossover：

1. `nvidia-smi -L` 的 5 秒前置探针先成功；
2. 第一 JVM 随后在 native CUDA 阶段停止进展；
3. 180 秒外层命令超时；
4. 同机新 `nvidia-smi` 查询超过 30 秒；
5. 没有任何 small-matrix JSON 被写出；
6. 只终止了本任务创建的 Java/Gradle PID，没有触碰外部 GPU workload。

这不是 performance sample，也没有纳入 gate。它证明 preflight 只能避免在已知坏
状态创建 context，不能处理 context 创建后或 submit 中发生的驱动挂起。Java
`Thread.interrupt`、future timeout 或 executor cancellation 都不能安全解开卡在
native driver 的线程；只有终止独立 worker process 才能恢复 Minecraft 调度控制。

因此 32/64/128 未解析；最小已证明 gate 仍为 256。当前约 30 条 direct rays 不得
使用 CUDA。

## 下一步

D121ad 应把相同 ABI 移入独立 executable：

1. Minecraft JVM 与 worker 之间使用版本化 shared-memory ring 或 bounded named
   pipe；不得让 render/audio thread 做阻塞 I/O；
2. worker 独占 CUDA context、NVRTC module 与 resident snapshot；
3. request 带 generation、ray count、deadline 和 checksum；
4. response 带 generation、parity telemetry、timings 和 result checksum；
5. watchdog 超时直接终止 worker，指数退避重启；
6. 超时、crash、generation mismatch 或 parity mismatch 全部消费 CPU fallback；
7. 先做 Minecraft 外 256→2048 IPC matrix；
8. 通过后才能启动 Minecraft shadow mode，音频仍只消费 CPU；
9. Linux NVIDIA backend 复用同一 protocol；macOS 永久 CPU fallback。

本轮未启动 Minecraft，未访问 playback/capture endpoint，也未修改 production
音频热路径。
