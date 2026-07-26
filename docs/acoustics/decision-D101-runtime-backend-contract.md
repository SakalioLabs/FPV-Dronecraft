# D101 — 实时声学后端选择、故障回退与预算合同

日期：2026-07-26  
状态：实现、真实 Client GameTest、独立预算验证通过；物理端点与听感校准仍未执行

## 决策

Minecraft 运行时只允许一个 late-reverb 湿声所有者：

1. 未显式启用研究后端时保持 clean/dry，不把未校准参数当作发布默认值；
2. 正常模式同时启用 EFX 与 Java FDN 时，优先使用一个共享的 OpenAL EFX effect/slot；
3. EFX 启动期间保持 `OPENAL_EFX_PENDING`，不抢先启动 Java 湿声；
4. 只有 EFX 对当前 OpenAL context 明确报告
   `EXTENSION_UNAVAILABLE` 或 `CONTEXT_FAILED`，Java FDN 才可接管；
5. context 替换后，处于 Java fallback 的一帧先让禁用的 EFX controller 观察并清理新
   context，下一帧进入 pending、停止 Java，随后才重建 EFX；
6. 显式 audio-lab 后端不自动换算法。选择 EFX 的实验即使失败也不会悄悄变成 Java，
   从而保持 A/B 证据纯净；
7. `fpvdrone.proceduralAudio=false` 是最高优先级 clean fail-safe。

该状态机解决了 D100 后发现的实际缺口：旧逻辑能通过配置避免双启用，但无法区分
“等待 context”“等待 source”“缺少扩展”和“当前 context 已失败”，所以 EFX 失败后
Java 永远不会接管。

## 显式状态与路由

| EFX 状态 | 含义 | 正常模式（Java fallback 已启用） |
|---|---|---|
| `INACTIVE` | controller 尚未观察到启用请求或刚观察到替换 context | `OPENAL_EFX_PENDING` |
| `WAITING_CONTEXT` | 当前线程尚无有效 OpenAL context | `OPENAL_EFX_PENDING` |
| `WAITING_SOURCES` | EFX 能力存在，但生产 source 尚未挂载 | `OPENAL_EFX_PENDING` |
| `OPERATIONAL` | effect、slot、filters 与 source send 均有效 | `OPENAL_EFX` |
| `EXTENSION_UNAVAILABLE` | 当前 device/context 没有 `ALC_EXT_EFX` | `JAVA_FDN` |
| `CONTEXT_FAILED` | 当前 context 的资源创建、参数写入或路由产生 AL error | `JAVA_FDN` |

`EXTENSION_UNAVAILABLE` 和 `CONTEXT_FAILED` 是当前 context 的终止状态；context ID
变化会清除失败锁。状态注入单元测试覆盖
`OPERATIONAL → CONTEXT_FAILED → INACTIVE → WAITING_CONTEXT →
WAITING_SOURCES → OPERATIONAL`，每一步都验证 Java 与 EFX 不会同时拥有湿声。

## 无音频遥测

新增命令：

```text
/fpvdrone-acoustics backend-status
```

它只报告：

- 实际/等待中的 backend；
- EFX 状态与 AL error code；
- 环境 snapshot generation；
- low/mid/high RT60、wet gain、transition seconds；
- OpenAL context rebuild 次数；
- Java stream 与 EFX operational 标志；
- `doubleWetPath` 合同违规信号；
- 固定的 `capturesAudio=false`。

不记录 PCM、麦克风、物理输出端点、玩家身份或世界坐标。真实 Client GameTest 已在
EFX 首次运行和 `Minecraft.SoundManager.reload()` 后同时断言：

- backend 为 `OPENAL_EFX`；
- EFX 状态为 `OPERATIONAL`；
- environment generation 有效；
- Java stream 未运行；
- `doubleWetPath=false`；
- `capturesAudio=false`；
- reload 后 telemetry 的 context rebuild 计数严格增加。

## 最坏生产负载预算

### OpenAL EFX

输入是本轮 D094 的真实 `ProceduralDroneAudioStream.read` PCM sidecar。基准同时读取
D094 JSON，逐块验证 offset、长度与 SHA-256，再把本轮整体 sidecar hash
`2ab30fb525712bf9953dbf9d5bc56ac49307c52df2f7977908eace768dc27734`
写入报告；它不把某一次飞行输出误当作永久 golden。

拓扑与生产 controller 一致：

- 6 架无人机 × motor/propeller 两层 = 12 个 source；
- 1 个 shared standard-reverb effect；
- 1 个 shared auxiliary slot；
- 12 个 per-source low-pass send filter；
- 使用生产 `configureReverbEffect` 与 `configureSendFilter`；
- 4096 frames / 85.333 ms buffer；
- 60 次 warmup，240 次测量。

本轮 OpenAL Soft loopback wall-time：

| case | P50 | P95 | P99 | P99 / buffer |
|---|---:|---:|---:|---:|
| 12-source dry | 0.1514 ms | 0.1812 ms | 0.2754 ms | 0.3227% |
| 12-source shared EFX | 0.4686 ms | 0.5153 ms | 0.5644 ms | 0.6614% |

EFX 相对 dry 的 P99 增量是 `0.2890 ms`，即 buffer 的 `0.3387%`。

### Java FDN

既有生产 wet-stream benchmark 重新执行：

- 6 架无人机；
- 每架 4 个 rotor；
- 每 rotor 12 个 BPF harmonic + shaft/electrical/cogging，共 15 tones；
- 实际 `ListenerReverbAudioStream` 与 shared FDN；
- 80 次 warmup，500 次测量。

| request | buffer duration | P50 | P95 | P99 | P99 / buffer |
|---|---:|---:|---:|---:|---:|
| 4096 bytes | 42.667 ms | 1.9491 ms | 2.3131 ms | 2.6937 ms | 6.3134% |
| 16384 bytes | 170.667 ms | 11.2058 ms | 12.6951 ms | 12.9441 ms | 7.5844% |

两套基准都通过预注册的 `P99 <= 25% buffer duration` 门槛。

这些数值不能用于宣称 EFX 比 Java FDN 快多少：EFX 测的是当前主机 in-process
OpenAL Soft loopback 的 12-source 原生 mixer wall time，Java 测的是六机最坏合成与
wet stream read。它们是两个发布路径各自的预算证据，不是相同 DSP 的微基准。

## 可复现证据

```powershell
.\gradlew.bat :fabric-mod:runClientGameTest
.\gradlew.bat verifyAcousticBackendRuntimeBudget --rerun-tasks
.\gradlew.bat acousticResearchCheck --rerun-tasks
```

- EFX runtime report SHA-256：  
  `93f91ed29df7813f6bed843ce0c360acd97745878e623c023092f74f26d0a6ab`
- Java runtime report SHA-256：  
  `569fc55d86f54276dc0dc9c32f481e393341af17db5faffcd3848a3c16baa75b`
- independent runtime-budget verification SHA-256：  
  `b855f7f29caf93cadff4ac0604b8d0446e754433ac52bad88741fc6c07c95509`

独立 verifier 同时绑定 D094 report/PCM、EFX sharing topology、Java 最大声源/音调
负载、AL/ALC error、25% 门槛以及无端点/无录音/未发布校准声明。6 个负例覆盖
D094 脱链、错误 sharing topology、Java 负载缩水、预算超限、录音 overclaim 与增量
P99 自相矛盾。

本轮统一 Python regression 为 `304 tests passed`；Java/core/mod 测试、CPU DDA
reference 的 2/2 CTest 与静态 CUDA source contract 同时通过。当前主机仍没有
`nvcc`，因此没有 CUDA 编译、GPU 执行或 GPU 性能声明。

## Claim boundary 与下一步

本轮真实覆盖 Minecraft 声音引擎、真实 OpenAL Soft context reload、生产 source
挂载、运行时互斥路由、元数据遥测，以及两个后端各自的进程内最坏负载预算。

本轮没有：

- 制造真实硬件/驱动级 context loss；
- 在没有 `ALC_EXT_EFX` 的第二种 OpenAL 实现上运行；
- 测量物理 endpoint callback、driver CPU 或 underrun；
- 录制扬声器、系统 loopback 或麦克风；
- 执行听感 ABX；
- 将当前 RT60/wet 参数标记为 release calibrated。

D102 应优先增加可控的 sound-thread native fault injection：在资源创建、effect 参数
写入、source send 路由三处分别注入一次错误，验证 EFX 先 detach/cleanup、Java 下一
tick 接管、旧 context 不重试、新 context 才恢复，并把整条状态/cleanup 序列写成
无音频证据。物理设备与 ABX 仍需用户明确授权后再进行。
