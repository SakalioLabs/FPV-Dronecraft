# D095 — 生产 PCM 已绑定 Minecraft/OpenAL 流式队列

日期：2026-07-26  
状态：**真实 Minecraft Client GameTest、8 个生产 PCM→OpenAL buffer 绑定、
队列/source state 与独立 verifier passed；连续 underrun 与物理输出 pending**

## 目的

D094 已证明 `ProceduralDroneAudioStream.read()` 返回的精确 PCM 与飞行黑匣子、
Doppler ratio 和 40 ms oscillator ramp 一致，但证据止于 Java `AudioStream`
边界。它尚未证明这些 bytes 实际成为 Minecraft 随后排入 OpenAL 的 buffer，也未
观察 queue depth、processed count 或 source playback state。

本轮只读接入 Minecraft 的既有流式声道，不替换或重排音频路径。目标是把 D094 的
每个生产 chunk 逐一绑定到传给 `alSourceQueueBuffers` 的真实 buffer id，并记录
queue 操作完成后的 OpenAL 状态。

## 官方语义与 Minecraft 实现

[OpenAL 1.1 specification](https://www.openal.org/documentation/openal-1.1-specification.pdf)
明确规定：

- OpenAL 没有内建的“向 buffer 流数据”机制；应用通过排队静态 buffers 实现
  streaming；
- 首个以 `alSourceQueueBuffers` 附加的 buffer 令 source 成为
  `AL_STREAMING`；
- 已处理 entries 才能 unqueue；最后一个 buffer 播完时 source 进入
  `AL_STOPPED`；
- `AL_SEC_OFFSET` / `AL_SAMPLE_OFFSET` 相对当前排队 buffers 的起点，因此应用
  unqueue 已处理的一秒 buffer 后，数值可以合法回绕。

对 Minecraft 1.21.11 的 `com.mojang.blaze3d.audio.Channel` 实际 bytecode 审计又
确认：

1. streaming buffer size 由音频格式计算为恰好一秒；
2. attach stream 时先 pump 4 个 buffers；
3. update 时读取 `AL_BUFFERS_PROCESSED`；
4. unqueue、删除相同数量的已处理 buffers，再 pump 同样数量。

这就是当前版本可直接复用的官方原生简化：Dronecraft 继续生成一秒 PCM，
Minecraft 负责 4-buffer queue 的生命周期，OpenAL 负责播放、空间化和 EFX。

## 默认关闭的只读探针

`OpenAlStreamingQueueProbe` 默认关闭，只有 Client GameTest 显式 `begin(4)` 才
启用。生产 stream 在 `read()` 返回后保存：

- stream identity、layer、entity 与 simulation timestamp；
- Doppler ratio；
- 精确 PCM byte length 与 SHA-256。

`ChannelStreamingQueueMixin` 在 Minecraft 实际调用 `alSourceQueueBuffers` 后
立即把同一 `AudioStream` 的下一份 pending PCM 与 source/buffer id 配对，然后在
`Sound engine` 线程只读查询：

- buffer validity、frequency、bits、channels、bytes；
- `AL_SOURCE_STATE`、`AL_SOURCE_TYPE`；
- `AL_BUFFERS_QUEUED`、`AL_BUFFERS_PROCESSED`；
- `AL_SAMPLE_OFFSET`；
- 可用时的 `AL_SOFT_source_latency`；
- `AL_SOFT_events` 能力与 AL error。

探针按对象身份配对，不依靠跨线程时间猜测；达到 motor/propeller 各 4 次后关闭并
清空 pending state。正常运行只有一个 volatile false fast path。

## `AL_SOFT_events` 的取舍

当前 OpenAL 实现暴露 `AL_SOFT_events=true`。该扩展的
[正式说明](https://openal-soft.org/openal-extensions/SOFT_events.txt)提供 buffer
completed、source state changed 和 disconnect 的异步通知，但：

- 每个 context 只有一个 callback；
- callback 不能调用 AL functions；
- 默认没有任何 event type 被启用；
- buffer completed 只说明一个或多个 queue entries 已处理，不等同于“发生
  underrun”。

后续 D096 已在严格的零 callback/user-pointer 前置门禁下，于 D095 同一 trace window
有界注册 callback。因此当前 D095 报告不再写死 false，而是查询真实 callback
pointer；本次严格记录：

- `openal_event_callback_registered=true`
- `continuous_underrun_observation=false`
- `callback_underrun_counter_available=false`

callback 的所有权、事件与清理由 D096 单独 hash-bound；D095 仍只证明离散 queue
状态。看到“补充后 queue=4、source=PLAYING”不能推出两个观测点之间从未停播。

## 真实 Client GameTest 结果

| 字段 | 结果 |
|---|---:|
| entity id | 3 |
| motor / propeller queue events | 4 / 4 |
| distinct sources | 2 |
| distinct buffers | 8 |
| buffer format | 48,000 Hz / mono / PCM16 / 96,000 bytes |
| source state / type | `AL_PLAYING` / `AL_STREAMING` |
| queue depth after every refill | 4 / 4 |
| processed after every refill | 0 / 8 |
| sample offset range | 0–2,880 samples |
| source offset range | 0–60 ms |
| source latency range | 51–61 ms |
| `AL_SOFT_events` supported | true |
| event callback registered | true（D096-owned bounded window） |
| AL errors | 0 / 8 |

每个 queue event 的 layer/layer-sequence、entity、simulation timestamp、Doppler
ratio、PCM bytes 和 PCM SHA-256 都与 D094 的同一 chunk 完全相等。motor 始终使用
一个 source id，propeller 使用另一个 source id；8 个 buffer id 在本次 trace 内
互不重复。

## 独立验证

`verify_openal_streaming_queue.py` 不信任 Java summary。它重新：

1. 固定 D095 report 与 D094 PCM report 的 SHA-256；
2. 按 layer/layer-sequence 重做逐字段 PCM 绑定；
3. 验证 source/buffer id、线程与 per-layer source identity；
4. 验证实际 OpenAL buffer 为 48 kHz mono PCM16、恰好一秒；
5. 要求每次 post-queue 都是 `PLAYING + STREAMING + queued 4 + processed 0`；
6. 交叉检查 sample offset 与 `AL_SOFT_source_latency` offset；
7. 要求 callback pointer 状态在 8 次 refill 与 summary 间一致，同时 fail closed
   地拒绝连续 underrun、playback capture、录音和 release calibration overclaim。

11 个 Python tests 覆盖成功路径、PCM hash 脱链、format、queue depth、stopped
source、offset/host timestamp 脱链、callback 状态一致性、continuous-underrun
overclaim 与 AL error。

运行：

```powershell
.\gradlew.bat :fabric-mod:runClientGameTest --rerun-tasks
.\gradlew.bat verifyDopplerProductionChunkTrace `
  verifyOpenAlStreamingQueue --rerun-tasks
python -m unittest discover -s docs/scripts `
  -p test_verify_openal_streaming_queue.py -v
```

证据哈希：

- D094 PCM report SHA-256：  
  `1a3061d25a28994d4ce5e7346e5a187c8aa96df0324169cbf8f78130ba173582`
- D095 live queue report SHA-256：  
  `909357c882c4889f3b75fba08bf7117d2f7a640631ef5ac1c84a488dc71d9c9e`
- D095 independent verification SHA-256：  
  `f82d1ce3d746ffe49f2f1a27921bcd7ad9139755b9240b49273eef3ee7b45c92`

## 同时修正的 streaming offset 判定

完整客户端回归暴露了 D091 的两个错误假设：Client GameTest tick 可以快于真实声卡
时钟，而且 unqueue 一秒 buffer 后 source offset 可以回绕。现在 audio-lab：

- 第二份 sample 同时等待至少 4 ticks 与 150 ms 宿主时间；
- pair 限定小于 800 ms，确保最多只允许一次一秒回绕；
- raw offset delta `<=0` 时只允许加恰好一秒，并显式记录
  `source_offset_wrapped=true`；
- 修正后的 advance 必须与 host elapsed 相差不超过
  `max(100 ms, 50% × host elapsed)`。

所以相同 offset 不会被伪装成一次回绕：例如 200 ms 后 offset 不变会被重算为
1,000 ms advance，因与宿主时间不一致而拒绝。Java、独立 verifier、
audio-lab protocol 与 D088 materializer 使用同一规则，并包含合法
`0.90→0.10 s` rollover test。

## 决策与下一步

1. 保留 Minecraft 原生的一秒 × 4-buffer streaming 生命周期，不在 Dronecraft
   内另造播放器或 queue scheduler。
2. D094 的生产 PCM→D095 的 OpenAL buffer id 绑定成为后续播放侧回归门禁。
3. 离散 post-refill 状态只用于发现 format、queue depth、source state 和
   PCM/buffer 脱链；不能命名为 underrun monitor。
4. D096 已完成有生命周期归属的 `AL_SOFT_events` callback 实验，并把
   buffer-completed 与 source-state event 序列定义为诊断；没有 stop event 仍不
   等同于物理输出连续。
5. 真正的 rendered/physical continuity、端到端延迟和可听质量仍需操作者确认
   loopback endpoint 并明确授权后执行 D085–D089 协议。

## Claim boundary

本轮证明 D094 的精确生产 PCM 确实成为 Minecraft 排入 OpenAL 的 buffer，并证明
8 个离散补充点的 format、queue depth、processed count、source state/type、
offset/latency、callback-pointer presence 与错误状态。callback 事件和清理由 D096
证明；D095 本身没有连续观察两个补充点之间的 mixer，不包含 callback underrun
counter、OpenAL rendered-output capture、物理播放、录音、loopback、听测或
release calibration。
