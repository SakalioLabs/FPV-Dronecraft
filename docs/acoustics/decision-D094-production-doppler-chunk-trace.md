# D094 — 生产 AudioStream Doppler 分块与服务器黑匣子绑定

日期：2026-07-25

状态：真实 Client GameTest、精确生产 PCM sidecar、40 ms 振荡器 checkpoint、
integrated-server blackbox 绑定、独立 verifier 与反例测试通过；OpenAL queue、
callback underrun、loopback 与电声校准仍未观测

## 结论

D093 的孤立稳态 tone 已推进到 Minecraft 实际使用的
`ProceduralDroneAudioStream.read(int)`。本轮不是重新调用一个测试合成器，而是在
生产 `AudioStream` 返回 `ByteBuffer` 前复制同一批 PCM16 字节，并把同次
`DroneAcousticRenderState.AudioSnapshot`、emission hash、Doppler ratio、选定
oscillator 的前/40 ms/缓冲末状态和服务器端同一无人机的 blackbox CSV 一并固化。

本次真实运行获得 motor/propeller 各 4 个一秒分块，共 8 个、384,000 samples、
768,000 bytes。Doppler ratio 从 `1.000245603196` 穿越到
`0.999972530741`；每个变化分块都在第 `1,920` sample（48 kHz 下为 40 ms）达到
新 target 且 remaining ramp 为 `0`。所有 8 个分块都在服务器黑匣子的
`±2 ticks` 窗口内找到误差不超过 `0.2 RPM` 的对应 rotor RPM。跨生产分块的最大
PCM 样本跳变为 `71`，低于按各层内部 P99 step 推导的门限。

这关闭的是“服务器飞行动力学 → 客户端 emission/tonal target → 40 ms 连续平滑 →
混合生产 PCM buffer”的代码内证据链。它没有证明这些 bytes 已被 OpenAL 排队或
播放，也没有测量物理声卡、扬声器、空气、麦克风或人耳结果。

## 实现

### 原子音频快照

`DroneAcousticRenderState` 每次重建 emission 时发布一个不可变
`AudioSnapshot`，包含：

- source 与 listener frame；
- Doppler 前 emission 与最终 emission；
- Doppler frequency ratio；
- temperature-dependent sound speed。

`ProceduralDroneAudioStream` 每次 `read()` 只读取一次该快照，避免同一 PCM buffer
混入两个 update 的状态。PCM 明确以 little-endian mono signed 16-bit 写入。

### 有界、默认关闭的生产 tap

`DopplerAudioChunkTrace` 默认完全关闭，只在 Client GameTest 显式
`begin(4)` 后工作；每层最多保留 target 加 8 个候选，完成或 abort 后关闭。每个
接受的 chunk 记录：

- global/layer sequence、entity id、simulation timestamp/tick；
- Doppler ratio 与完整 emission SHA-256；
- tracked tone kind、rotor index、order、rotor RPM、blade count；
- emission frame 的 target frequency；
- frequency smoothing sample count；
- render 前、第 1,920 sample、render 后的 current/target/ramp；
- `read()` 将返回的精确 PCM bytes、offset、length 与 SHA-256。

为了观测精确的 40 ms 端点，只有 trace active 时才把同一次生产 render 分成
`1,920 + remainder` 两段。`PhaseContinuousSynthesizer` 原有 whole-vs-split
逐样本一致性测试保证分段不改变合成结果；新增诊断单测又直接验证
`1000→2000 Hz` 在前 480 samples 后为 `1250 Hz / 1440 remaining`，并在
1,920 samples 时为 `2000 Hz / 0 remaining`。

### 服务器黑匣子

Client GameTest 使用 Fabric 官方
`TestServerContext.computeOnServer(...)` 在受测试框架管理的服务器线程读取
owner drone。早期实现直接调用 `IntegratedServer.execute()`，虽然数据能写出，
却绕过 Client GameTest 的 packet synchronizer，并在 world close 时触发
`Network synchronizer in invalid state`。最终实现不关闭 synchronizer，也不增加
测试专用网络协议；官方 server-context 入口下完整测试正常退出。

blackbox CSV 原样写入 sidecar，并固定：

- server entity id；
- rows/columns 与 tick range；
- CSV SHA-256。

## 独立验证

`verify_doppler_production_chunk_trace.py` 不信任 Java 报告里的结论字段。它重新：

1. 验证 JSON、PCM、CSV 的 SHA-256 与每个 chunk 的 offset/hash；
2. 按 s16le 解码真实混合 PCM，拒绝 silent、clipped 或长度不足的分块；
3. 从 rotor RPM、blade count、tone kind/order 与 Doppler ratio 重算 target；
4. 要求 smoothing 和 checkpoint 都是 1,920 samples，并验证 checkpoint/after
   current、target、remaining；
5. 要求 ratio 跨越 unity；
6. 从 blackbox CSV 按 tracked rotor column 和 `±2 ticks` 重做 RPM 匹配，至少
   半数才通过；
7. 以各层内部 sample-step P99 推导分块边界门限，拒绝 phase reset；
8. fail closed 地拒绝任何 OpenAL queue、playback、underrun、真实录音或 release
   calibration overclaim。

9 个 Python 测试覆盖 hash 变化、更新 hash 后的 PCM discontinuity、错误 smoothing
长度、未完成 checkpoint、target 脱链、blackbox RPM 脱链、不跨 unity 与 OpenAL
overclaim。

运行：

```powershell
.\gradlew.bat :fabric-mod:runClientGameTest --no-daemon --console=plain
.\gradlew.bat verifyDopplerProductionChunkTrace --no-daemon --console=plain
python -m unittest discover -s docs/scripts `
  -p test_verify_doppler_production_chunk_trace.py -v
```

## 本次真实结果

| 字段 | 结果 |
|---|---:|
| entity id | 3 |
| trace ticks | 22–83 |
| blackbox ticks / rows | 1–84 / 84 |
| blackbox columns | 1,070 |
| motor / propeller chunks | 4 / 4 |
| samples / bytes per chunk | 48,000 / 96,000 |
| total PCM bytes | 768,000 |
| Doppler ratio range | 0.999972530741–1.000245603196 |
| changed chunks | 8 / 8 |
| 40 ms checkpoints complete | 8 / 8 |
| server RPM matches | 8 / 8 |
| maximum boundary step | 60 |
| motor / propeller boundary thresholds | 192 / 1,354 |

证据哈希：

- live JSON SHA-256:
  `1a3061d25a28994d4ce5e7346e5a187c8aa96df0324169cbf8f78130ba173582`
- exact PCM sidecar SHA-256:
  `7acc06240811bea912ed37520dc9813338802782a5f2a306dd0c26afaea71fd5`
- server blackbox CSV SHA-256:
  `c00d553ad8486723e0944f76481abefc85c927bffd2c98b2efc55ec2dca117c8`
- independent verification SHA-256:
  `e74a82c5e02d915985ea0e3671af858107d9a6560fda4148f43b6fe15afe05e2`

## 证据边界与下一步

报告明确保持：

- `openal_source_queue_observed=false`
- `openal_playback_capture=false`
- `callback_underrun_counter_available=false`
- `real_audio_capture=false`
- `release_calibrated=false`

一秒 `AudioStream.read()` buffer 内确实覆盖了 40 ms chirp，checkpoint 证明生产
oscillator 在准确 sample offset 完成 ramp；但混合 PCM 同时含其他 tonal orders 与
broadband，因此本轮没有声称能从混合波形单独反演每条 chirp。D093 的孤立 tone
测频与本轮的生产 checkpoint 是互补证据。

下一研究方向优先检查 Minecraft/OpenAL streaming queue 是否能在不改变播放路径的
前提下只读暴露 processed/queued buffer、sample offset 和 underrun 信息，并把它与
D090/D091 source-latency sample 及本轮 chunk sequence 绑定。若 OpenAL Soft/当前
Minecraft API 没有可靠 callback-underrun counter，应明确记录“不支持”，不能从
PCM 连续性或 source offset 间接冒充。真正电声端到端结论仍需操作者确认录音端点并
明确授权后执行 D085–D089 协议。
