# D121af — CUDA worker checksum 负控与恢复门槛审计

日期：2026-07-27

状态：`checksum-negative-controls-verified / reboot-recovery-absent /
cuda-execution-prohibited`

## 决策

D121ad 的动态故障矩阵已覆盖 timeout、crash、worker status 与 generation
mismatch，但 checksum 当时只由 source contract 间接覆盖。D121af 增加两个
CUDA-free worker fault modes，直接验证 request 与 response 两个方向的数据损坏
都不会进入声学结果：

- `--mode=request-checksum-mismatch`：worker 在验证前破坏收到的 checksum，
  必须返回 checksum error；Java 将其归类为 `WORKER_ERROR`、终止 worker、返回
  CPU fallback 并进入 backoff；
- `--mode=response-checksum-mismatch`：worker 返回字段匹配但 payload checksum
  错误的 frame；Java 必须归类为 `PROTOCOL`、终止 worker、返回 CPU fallback
  并进入 backoff。

这两条负控与正常 ping、worker error、generation mismatch、crash、hang 一起
组成 7-case matrix。所有 corrupt/error cases 的 immediate retry 都必须是
`BACKOFF`，且故障 worker 必须被确认退出。

## 三轮证据

三次独立 JVM 的正式忽略报告：

- run 1：2205 bytes，SHA-256
  `4a687e60dc5bc98c407f7683cb0c84aca0258879f7e8d6e070e91ce9b5917f0f`
- run 2：2201 bytes，SHA-256
  `83315122d8bf8fd81b3f25f0b63571851c00b709ff06daa724d7420d5fc87b7a`
- run 3：2201 bytes，SHA-256
  `bbcf67f0f4806be72c2dba960d22d9f5c20a42e7db9d5c3b1b4c57015b4d7977`

三轮结果一致：

| fault | classification | CPU fallback | retry | process alive |
|---|---|:---:|---|:---:|
| request checksum corruption | `WORKER_ERROR` | yes | `BACKOFF` | no |
| response checksum corruption | `PROTOCOL` | yes | `BACKOFF` | no |

hang request 分别在 `2006.123 / 2006.809 / 2007.486 ms` 返回 timeout；从请求
开始到确认 worker 退出为 `2031.960 / 2032.701 / 2033.174 ms`。三轮报告都声明
`cuda_executed=false / minecraft_started=false /
physical_endpoint_opened=false`。

## 驱动恢复审计

Windows System event log 中最新的 EventLog service start（event ID 6005）为：

```text
2026-07-17 08:57:11
```

D121ac 的 post-init CUDA driver hang 发生在 2026-07-27，因此挂起之后没有系统
重启证据。进程消失、DLL 解锁或一次 `nvidia-smi` 成功都不能替代 driver reset；
D121ac 已证明 preflight 成功后仍可能在 context/submit 阶段挂起。

此外，本轮通用 WMI/系统状态查询出现过 bounded timeout，进一步不支持将当前机器
状态认定为稳定。结论是继续禁止运行 `cudaDdaWorkerBenchmark`。这不是 GPU
失败样本，也不得写入 CUDA performance matrix。

## 下一步

需要用户或外部环境完成 Windows 重启（或可证明等价的 NVIDIA driver reset）。
恢复后依次运行：

1. bounded `nvidia-smi -L`；
2. 已冻结的 NVRTC smoke 与 3-ray fixture parity；
3. D121ae 三轮 Java 21 worker IPC matrix。

在系统恢复前继续修改 shadow-mode 或 production audio routing 会越过研究 gate，
因此不执行。
