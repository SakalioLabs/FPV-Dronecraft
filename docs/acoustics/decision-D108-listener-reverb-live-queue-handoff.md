# D108 — Minecraft 实时 listener reverb queue handoff

日期：2026-07-26  
状态：三类真实 sound-thread fault、source restart、首个非零 buffer 与独立 verifier 通过

## 决策

D107 的 listener-shared 500 ms history 已通过真实 Minecraft Client GameTest 的
EFX fault→Java fallback→SoundManager reload→EFX recovery 循环。生产策略保持：

- shadow 阶段没有 OpenAL source；
- fallback 必须创建新的 active `ListenerReverbAudioStream`；
- 新流先预运行 24,000-frame FDN history；
- Minecraft 再按原生 `Channel` 合同生成并排入 4 个一秒 buffer；
- recovery 停止 Java wet owner，重启空 history 的 shadow epoch；
- 下一次 fault 前必须重新填满 24,000 帧。

不引入常驻静音流、queue flush hack、双湿声 crossfade 或 context-global
`AL_SOFT_events` callback。

## 实测协议

在开发环境中依次向真实 Minecraft sound thread 注入：

1. `resource-create`
2. `parameter-write`
3. `source-route`

每个周期在 fault 前独立重启 12 秒诊断飞行，并等待：

- EFX operational；
- `java_shadow_active=true`；
- `java_wet_active=false`；
- `java_history_frames=24,000`。

fallback 请求发生在 manager 将 history producer 从 shadow 转为 wet-requested、但
新 stream 尚未获取 owner 的精确点。随后 `ListenerReverbAudioStream.read()` 的 PCM
按对象身份绑定到 `Channel.method_19648` 实际执行的 `alSourceQueueBuffers`。探针只
复制 PCM hash、first-nonzero index 和 OpenAL metadata，不保存 PCM sidecar、不读回
renderer、不录音。

## 结果

| fault | 新 stream | 首 buffer 入队 | 4-buffer fill | FDN pre-roll | first wet |
|---|---:|---:|---:|---:|---:|
| resource-create | 1 | 10.9937 ms | 42.8676 ms | 0.8992 ms | frame 0 |
| parameter-write | 2 | 9.1012 ms | 32.9697 ms | 0.8760 ms | frame 0 |
| source-route | 3 | 9.1336 ms | 32.2754 ms | 0.8691 ms | frame 0 |

三周期均满足：

- fault arm 与 fallback 时 history 都是 24,000 帧；
- fallback 请求点 `shadow=false`、`wet=false`，没有双 owner；
- 每周期恰好 4 个 96,000-byte、48 kHz、mono PCM16 buffer；
- 队列深度严格为 `1→2→3→4`，processed 与 sample offset 均为 0；
- source state 为 `AL_INITIAL`，source type 为 `AL_STREAMING`；
- 每周期 4 个 PCM hash 互异，且首 buffer 从 frame 0 非零；
- 4 个 buffer 属于同一 stream/source；三个 fault 使用三个递增 stream sequence；
- recovery 后首个 shadow tick 是 2,400 帧，并最终重新达到 24,000 帧；
- 全程 `double_wet_path=false`，旧 wet owner 不参与 shadow。

门限为：

- fallback request→首 buffer queue `<=42.6667 ms`；
- 4-buffer initial fill `<=50 ms`；
- FDN pre-roll `<=10.6667 ms`。

最坏实测分别为 `10.9937 / 42.8676 / 0.8992 ms`。

## 测试可重复性修正

一次高负载重跑暴露了测试编排依赖：若三次 sound-engine reload 总时长超过原先单个
12 秒诊断飞行，最后 recovery 会因无人机停止产生可听源而等待超时。音频 handoff
本身在此前三个周期均已完成。测试现改为每个 fault 周期独立重启 12 秒诊断飞行；
最终重跑在 1 分 19 秒内完整通过。该修正不放宽音频门限，只消除了声源存活时间对
主机调度的偶然依赖。

## 遥测与 claim boundary

本轮使用 Minecraft 的实时 OpenAL 输出 device，因此
`physical_endpoint_opened=true`。但是：

- `physical_output_captured=false`
- `captures_audio=false`
- 没有 microphone、loopback readback 或 endpoint recording
- `release_calibrated=false`

本结果证明 PCM 已经在真实 sound thread 进入正确的新 OpenAL source queue，不能
证明扬声器端可听响度、玩家听感、驱动后的连续性或发布参数校准。

## 可复现证据

```powershell
.\gradlew.bat :fabric-mod:runClientGameTest --rerun-tasks
.\gradlew.bat verifyListenerReverbQueueHandoff `
  verifyOpenAlEfxFaultFailover `
  verifyBackendCapabilityPolicyTimeline
```

- live report：
  `build/research/listener-reverb-queue-handoff-v1.json`
- independent verification：
  `build/research/listener-reverb-queue-handoff-verification-v1.json`
- D107 source SHA-256：
  `8d6d981654f59a9794fd7c096ff5a28a723c9b3d14ce605041950e591f2a4406`
- D102 source SHA-256：
  `1fdec1796ff56b5f26a2c153dba496ef8638aba4f175f9de61a2c2599e4c1c96`
- live report SHA-256：
  `c7d35bbf1516a213a09dfaca6ddd58d6a5c6a6a4c13b6a721fd499bbbfef0765`
- verification SHA-256：
  `22e19e5bb91444caac00ab4eabbc67e3de556e15fa3cd0cb22a810a9c36b8a01`

## 下一步

D109 应处理 Java→EFX recovery 的新-context tail reset。D105 已证明 fresh EFX 不与
旧 warm tail 等价；D108 只证明 Java owner 正确停止、shadow 正确重建。下一轮应在
隔离 OpenAL Soft loopback 中比较：

1. 直接接受 fresh EFX tail reset；
2. 对 fresh EFX 仅做短 onset gain slew；
3. 不允许 Java 与 EFX wet crossfade；
4. 用 click、20 ms dropout、100 ms wet-energy recovery 与额外 CPU 预算选择最短
   policy；
5. 选定后再回到 Client GameTest 验证 recovery queue/telemetry，不把软件 renderer
   结果冒充物理听感。
