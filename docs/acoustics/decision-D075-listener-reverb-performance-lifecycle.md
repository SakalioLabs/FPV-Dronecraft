# D075 — listener-shared reverb 的 Java 侧实时预算与生命周期门禁通过

日期：2026-07-25  
状态：**integrated capture/probe、worst-load wet stream 与可控生命周期通过；
OpenAL end-to-end 和 release calibration 仍未关闭**

## 问题

D070 已实现 listener-shared FDN，但当时的轻量基准没有覆盖：

1. Minecraft 客户端世界的 1521-cell snapshot capture；
2. runtime `128 rays × 8 bounces` probe；
3. 多架无人机的阶次合成与 FDN 同时运行；
4. source removal、重复 close 和 stream replacement；
5. OpenAL scheduling、音频设备替换与真实 underrun。

D075 关闭前四项可在仓库中确定控制的门禁，并把第五项保持为显式未测边界。

## Minecraft 集成性能

官方 Fabric Client GameTest 启动真实集成服务器和 OpenAL Soft，在玩家 eye position
捕获 `13 × 9 × 13 = 1521` cells。20 次 warmup 后运行 200 次：

| 阶段 | P50 | P95 | P99 | 门槛 |
|---|---:|---:|---:|---:|
| client-thread snapshot capture | 0.1287 ms | 0.3353 ms | 0.4501 ms | P99 ≤ 4 ms |
| runtime probe algorithm | 0.4463 ms | 0.8937 ms | 1.0397 ms | P99 ≤ 2 ms |

所有 200 个 snapshot 完整。probe 在测试中同步计时，算法与 worker 相同，但该数字
不包含 worker queue/scheduling latency。

权威命令：

```powershell
.\gradlew.bat --no-daemon :fabric-mod:runClientGameTest
```

输出：

```text
build/research/minecraft-reverb-performance-v1.json
```

同一次运行还重新生成确定性的 stone/wool 集成环境报告
`minecraft-reverb-client-gametest-v1.json`，SHA-256：

```text
20aaf4f80914aa3f3a53c478a1c18ac64be3d7aec4e61d31e1381635b0a6780c
```

GameTest 日志确认 OpenAL Soft 初始化成功；但是性能计时没有围绕 OpenAL enqueue、
device callback 或声卡输出，因此不能把“设备已启动”写成端到端音频延迟证据。

## 最重 wet-stream 负载

`ListenerReverbRuntimeBenchmark` 固定：

```text
6 sources × 4 rotors × 15 tones = 360 simultaneous tones
80 warmup reads + 500 measured reads
```

| requested bytes | buffer duration | P50 | P95 | P99 | P99 / duration |
|---:|---:|---:|---:|---:|---:|
| 4096 | 42.667 ms | 1.9590 ms | 2.2141 ms | 2.4007 ms | 5.63% |
| 16384 | 170.667 ms | 12.7093 ms | 13.3258 ms | 13.7304 ms | 8.05% |

两档都通过 `P99 < 25% of buffer duration`。JVM thread allocation counter 分别报告
约 `770` 与 `768 bytes/read`；该计数不包含 direct `ByteBuffer` 的 native bytes。
基准是机器相关结果，不固定 SHA。

## 生命周期

自动测试和 benchmark 共同验证：

- source 移除后，其 synthesizer 立即释放，而已有 FDN tail 继续衰减；
- `close()` 清空 synthesizer 与内部数组，重复 close 安全；
- closed stream 的 read 返回空 buffer；
- 关闭旧 stream 不会破坏新建 replacement stream；
- 两档 benchmark 结束时均满足 cleanup gate。

replacement 测试是 Java resource-rebuild 近似，不是操作系统音频设备热插拔或 OpenAL
device recreation 测试。

## 组合验证

先生成 Minecraft 集成报告，再执行：

```powershell
.\gradlew.bat --no-daemon verifyListenerReverbPerformance
```

组合报告：

```text
build/research/listener-reverb-performance-verification-v1.json
```

固定 gate：

- complete snapshots：通过；
- capture P99 ≤ 4 ms：通过；
- probe P99 ≤ 2 ms：通过；
- audio P99 < 25% buffer：通过；
- wet-stream lifecycle cleanup：通过；
- OpenAL end-to-end measured：**否**；
- Minecraft release calibrated：**否**。

## 决策

当前 listener-shared 架构在本机 Java 侧具有足够实时余量；不需要为了这些结果把
FDN 复制到每架无人机，也没有证据要求将 runtime probe 移到 CUDA。

feature 仍保持默认关闭，因为发布阻塞项已经从 Java 侧预算转为：

1. 按 D074 分离 early material BRDF 与 late diffuse transport；
2. 用 matched/accepted RIR 校准 RT60、EDT、DRR；
3. 在可观测 underrun/xrun 的长时间游戏会话中测 OpenAL scheduling；
4. 实测 world unload、resource reload 与真实 audio-device replacement。

本决策不授权修改 `AcousticMaterials`，也不把本机 P99 外推到所有硬件。
