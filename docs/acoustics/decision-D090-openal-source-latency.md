# D090 — OpenAL 原生 source-latency 可用，device clock 不可用

日期：2026-07-25  
状态：**真实 Minecraft sound-thread、reload 前后原生遥测与独立 verifier
passed；端到端延迟和 callback underrun pending**

## 研究问题

D084–D089 已建立 EFX reload、录音安全、时间对齐、证据打包和 ABX 统计协议，但仍
需要确认当前 Minecraft/OpenAL Soft 运行时能否提供比 Java host timestamp 更接近
renderer 的原生时序证据。本轮只读探测两个标准扩展：

- [`ALC_SOFT_device_clock`](https://javadoc.lwjgl.org/org/lwjgl/openal/SOFTDeviceClock.html)：
  原子读取设备时钟与设备输出延迟；设备时钟可用于观察设备 timer drift。
- [`AL_SOFT_source_latency`](https://javadoc.lwjgl.org/org/lwjgl/openal/SOFTSourceLatency.html)：
  原子读取 source playback offset 与 source-to-output latency。

能力位通过 LWJGL
[`ALCCapabilities`](https://javadoc.lwjgl.org/org/lwjgl/openal/ALCCapabilities.html)
和 AL capabilities 核对。当前 Minecraft 使用 LWJGL `3.3.3-snapshot`，实际后端为
[OpenAL Soft](https://github.com/kcat/openal-soft) `1.23.1`。

## 实现

`OpenAlClockLatencyProbe` 从 `SoundEngine.instanceToChannel` 选择一个活跃
`DroneLoopSoundInstance`，再通过 `ChannelHandle.execute` 在 Minecraft
`Sound engine` 线程执行 native query。探针：

- 不修改 source、context、device、EFX 或音频路径；
- 允许两个扩展仅有一个存在，不把部分支持误判为失败；
- 每组取两个样本，检查 host monotonic、source offset、可选 device clock、AL/ALC
  error 和 device/support identity；
- 在真实 `SoundManager.reload()` 前后各执行一组；
- 明确记录没有切换物理设备、没有 callback underrun counter、没有 loopback、
  没有端到端延迟和发布标定。

独立 Python verifier 重新计算 elapsed time 与可选 clock-rate ratio，验证四个
sample 的线程、context、设备、能力位、数值范围和 error code，并拒绝：

- 不支持 device clock 时出现非零 device-clock 字段；
- 把 callback underrun、物理设备切换或发布标定写成 true；
- source offset 不前进、elapsed 不一致、NaN/负值或 AL/ALC error。

## 本机真实结果

| 指标 | reload 前 | reload 后 |
|---|---:|---:|
| host sample interval | 195.1052 ms | 246.9286 ms |
| source offset advance | 200 ms | 260 ms |
| source latency sample 1 | 51 ms | 61 ms |
| source latency sample 2 | 61 ms | 61 ms |
| AL / ALC error | 0 / 0 | 0 / 0 |

能力与身份在 reload 前后保持稳定：

- device：`OpenAL Soft`；
- `AL_SOFT_source_latency=true`；
- `ALC_SOFT_device_clock=false`；
- `native_telemetry_validated=true`；
- sound-engine reload 已真实执行，完整 Client GameTest 通过。

因此当前运行时可以直接获得约 `51–61 ms` 的 OpenAL source-to-output pipeline
估计值，作为 D085/D087 capture 协议中的 renderer-side 诊断元数据。它不能替代
loopback marker 对齐：该数值不包含 DAC、USB/声卡、扬声器、空气、麦克风和采集
buffer，所以不是电声端到端延迟。

`ALC_SOFT_device_clock` 在当前打包运行时不可用，不能计算 OpenAL device clock 相对
host clock 的 drift；也不能通过这两个扩展取得 callback underrun 计数器。

## 可复现证据

```powershell
.\gradlew.bat --no-daemon :fabric-mod:runClientGameTest
.\gradlew.bat --no-daemon verifyOpenAlClockLatency
python -m unittest discover -s docs/scripts `
  -p test_verify_openal_clock_latency.py -v
```

- live report SHA-256：  
  `c749934f24fda3a9c3de157268aa3a9aeccf333e65f2d2ef0137bb91745ed28d`
- independent verification SHA-256：  
  `f92f62a2baffadfb27f1aa249eae4002639e8438ad864465accbbd9a2bf66aa0`

## 决策

1. 将 `AL_SOFT_source_latency` 作为可选的只读诊断字段接入后续真实录音证据，不用
   它移动、补偿或重采样当前音频路径。
2. source latency 只标记 renderer/device pipeline estimate；端到端对齐继续以
   D086 marker + D087 affine recorder mapping 为准。
3. `ALC_SOFT_device_clock=false` 是本运行时的合法能力结果，而不是测试失败；只有
   未来设备/发行包实际暴露扩展时才启用 drift gate。
4. 不把 source-offset 连续性冒充 callback underrun。原生 underrun 证据仍需
   callback/backend instrumentation；可听 click/dropout 仍需用户确认 endpoint 并
   明确授权后执行真实 loopback capture。
5. 该探针保持 diagnostics-only，不改变默认后端、EFX/Java FDN 互斥或
   `release_calibrated=false`。

## Claim boundary

本轮证明当前 Windows/Minecraft/OpenAL Soft 组合在真实 sound thread 与声音引擎
reload 前后可读取 `AL_SOFT_source_latency`，而且 AL/ALC error 为零。它没有证明
所有 OpenAL Soft 设备都支持相同扩展，没有设备时钟、物理设备热切换、callback
underrun、loopback、声学端到端延迟、跨平台稳定性或听觉真实性结论。
