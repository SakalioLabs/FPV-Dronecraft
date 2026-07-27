# D121ad — 进程外 CUDA worker 与 fail-closed watchdog

日期：2026-07-27

状态：`protocol-and-fault-contract-verified / real-cuda-ipc-deferred /
production-integration-not-authorized`

## 决策

D121ac 的进程内 CUDA bridge 虽然在 256–2048 rays 上通过 parity 和性能门槛，
但一次 post-init 驱动挂起证明 Minecraft JVM 无法安全取消 native driver call。
本轮将相同 bridge 移入可强制终止的独立进程，并冻结 JVM 与 worker 之间的
versioned framed-stdio 协议。

接受以下架构方向：

- CUDA context、NVRTC module、resident snapshot 和 device buffers 只属于 worker；
- Minecraft JVM 不再加载 CUDA bridge DLL；
- timeout、worker crash、协议/代际/校验失败或 worker 返回错误都产生明确的
  CPU fallback；
- 故障后的即时重试 fail fast，并按 `1/2/4/8/16/30 s` 指数退避；
- worker 强制终止和 pipe 回收不阻塞调用线程；
- 当前约 30 条 direct rays/update 仍由 Java CPU 计算。

这还不是 production 接入许可。当前 Java client 是单请求 research boundary，
不得从 render、tick 或 audio thread 同步调用。产品适配器必须放在独立 executor，
使用 bounded/latest-wins queue；音频线程只读取最后一个已验证结果或 CPU 结果。

## 冻结接口

公开 C ABI 位于 `native/cuda-dda-boundary/include/mcfpv_cuda_bridge.h`。它冻结
ABI version 1 的 create/submit/info/destroy/last-error 入口和配置、请求、timing
结构体。bridge 仍动态加载 `nvcuda.dll` 与 NVRTC，不依赖 CUDA Toolkit headers。

worker protocol 位于
`native/cuda-dda-boundary/include/mcfpv_cuda_worker_protocol.h`：

- magic：`0x5746434d`；
- protocol version：`1`；
- 48-byte frame header；
- 最大 payload：128 MiB；
- opcode：initialize、submit、ping、shutdown；
- 每帧包含 payload length、status、deadline milliseconds、generation、
  request ID 和 64-bit FNV-1a payload checksum；
- reserved 字段必须为零；
- initialization payload 为 16-byte header + frozen 24-byte cells +
  `material_count × 3 × FP64` transmission table；
- submit payload 为 8-byte header + frozen 64-byte rays；
- submit response 为 32-byte metrics + frozen 72-byte aggregate results。

worker 对所有长度乘法、总 payload、header、checksum、初始化状态和 bridge 返回值
fail closed。stdout 只传二进制帧，诊断只写 stderr。

## Watchdog 行为

`CudaDdaWorkerClient` 为 single-flight：

1. 启动 worker 并分配新的非零 generation；
2. 写入带 generation/request ID/deadline/checksum 的请求；
3. 只在 daemon reader 上执行 blocking read；
4. caller 使用 `Future.get(deadline)` 施加硬截止时间；
5. 完整验证 magic、version、opcode、deadline、generation、request ID、payload
   上限与 checksum；
6. 任一异常立即返回 `useCpuFallback=true`；
7. `destroyForcibly()` 先发出终止，再通过 `Process.onExit()` 异步关闭 pipes，
   不在 timeout path 等待 pipe close；
8. 退避期内不再访问 worker。

本轮测试曾发现“先 close pipe、后 terminate”会在 Windows 挂起 worker 上阻塞。
最终实现明确反转该顺序，这是故障注入直接改变实现的结果。

## CUDA-free 故障矩阵

Gradle task：

```text
:fabric-mod:cudaDdaWorkerWatchdogProbe
```

三次独立 JVM 均验证五类行为：

| case | 期望 | 三轮结果 |
|---|---|---|
| protocol-only ping | 正常响应 | `NONE`，不触发 CPU fallback |
| worker error | 非零 status | `WORKER_ERROR`，CPU fallback + backoff |
| generation mismatch | 陈旧/错误代际 | `PROTOCOL`，CPU fallback + backoff |
| crash | EOF/process exit | `PROCESS_EXIT`，CPU fallback + backoff |
| hang | 达到 2000 ms deadline | `TIMEOUT`，CPU fallback + backoff |

三轮 hang request 为 `2005.961 / 2005.467 / 2007.189 ms`；从请求开始到确认
worker 已退出为 `2031.410 / 2030.581 / 2033.373 ms`。因此强杀后的确认延迟约
25–26 ms，而不是 D121ac 中无法回收的 JVM native thread。这里的 2000 ms 是
包含冷启动的故障研究窗口，不是未来产品 deadline；warm worker 的产品 deadline
必须由真实 IPC 矩阵和 Minecraft shadow telemetry 冻结。

忽略的正式报告：

- run 1：1675 bytes，SHA-256
  `ea90fee3e484065a7f9ad8642d5b55de9379fcd83e7a207bacaf42a7b6d46431`
- run 2：1678 bytes，SHA-256
  `a4b75976f8edecb03701e33fc76fe48a9f0b56c7e89a6240fc0a5232ab34b251`
- run 3：1682 bytes，SHA-256
  `73505a3e61894bdf37ba36de8d57f80f4ff50168db6f21af2bdf6f39ec2e0e06`

三个报告都声明：
`cuda_executed=false / minecraft_started=false /
physical_endpoint_opened=false`。

## 未完成门槛

当前 NVIDIA driver 在 D121ac 后仍不可信，本轮没有调用 CUDA，也没有把协议成功
误写成 GPU 性能证据。恢复后必须依次完成：

1. bounded `nvidia-smi -L` 与已有 NVRTC fixture smoke；
2. worker initialize 的 resident snapshot parity；
3. 同一 worker 中 256/512/1024/2048 rays、3 个独立 JVM、
   `5 warmup + 30 measured` 的 IPC total matrix；
4. 对每个 prefix 用 `DirectPathSolver` 验证 count、material cells、flags、
   first hit、三频带 loss/gain；
5. 报告 pack、pipe write/read、H2D、kernel、D2H、unpack 和 total；
6. 冻结 warm deadline、offload crossover 与 restart policy；
7. 之后才允许 Minecraft 外 shadow mode；声音仍只消费 CPU；
8. parity、deadline miss、restart storm 均通过后，才讨论 product consumption。

本轮未启动 Minecraft，未访问 playback/capture endpoint，未修改 production
声音热路径。
