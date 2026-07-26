# D092 — OpenAL 原生 Doppler 所有权与防重复移频

日期：2026-07-25

状态：真实 Minecraft Client GameTest、sound-engine reload 与独立 verifier 通过；
可听正确性和 release calibration 未通过

## 结论

不把 OpenAL 原生 Doppler 作为穿越机程序化声源的产品移频器。Dronecraft 继续在
PCM 合成前只移动电机/桨叶的 tonal orders，保留 broadband turbulence/noise 的
频谱统计；OpenAL source pitch 固定为 `1.0`。新增的 per-source guard 只处理活动的
两条 `DroneLoopSoundInstance`，令它们的 `AL_VELOCITY` 等于当前 listener velocity，
从而把 OpenAL 的相对 Doppler ratio 固定为 `1.0`，不改变 context-global
`AL_DOPPLER_FACTOR`，也不改变任何非无人机声源。

这不是关闭整个游戏的 Doppler，而是明确分配算法所有权：

| 信号部分 | Doppler 所有者 | 处理 |
|---|---|---|
| motor/propeller tonal orders | Dronecraft PCM synthesizer | 按传播方向的径向相对速度移动频率 |
| broadband turbulence/noise | 无 Doppler 重采样 | 保持随机宽带统计，只接受传播滤波/增益 |
| 完整 OpenAL drone stream | guard 后 native ratio = 1 | `AL_PITCH=1`，source/listener velocity 相等 |
| Minecraft 其他声源 | Minecraft/OpenAL | 完全不触碰 |

## 为什么不直接采用官方 native Doppler

OpenAL 1.1 的 Doppler 模型用声源、listener 的径向速度计算 playback-rate ratio；
`AL_DOPPLER_FACTOR` 是 context-global 状态，默认 `1`，设为 `0` 才会全局关闭。
该模型作用于一条 source 的完整 sample stream，而不是程序化 PCM 内部的某组
spectral components。参见 [OpenAL 1.1 Specification（Doppler Shift）](https://www.openal.org/documentation/openal-1.1-specification.pdf)
和 [LWJGL AL10 API](https://javadoc.lwjgl.org/org/lwjgl/openal/AL10.html)。
[OpenAL Soft](https://github.com/kcat/openal-soft) 也把 Doppler、distance
attenuation 和 directional emitters列为 API 内建的 source/listener 处理。

对本项目而言，直接填入物理速度会造成两个独立错误：

1. tonal orders 已由 `DopplerShift.apply(...)` 移频，native playback-rate 会再次
   乘上一个 Doppler ratio；
2. native resampling 会同时移动 broadband noise，而现有声源模型刻意只移动
   周期性转子音调。

因此 native Doppler 是“官方有支持，但处理粒度与本项目 source architecture
不匹配”，不是可直接替换当前模型的简化路径。官方 EFX listener-shared reverb
仍是合适的原生简化；两者结论不冲突。

## Minecraft 1.21.11 当前路径审计

对 Loom remapped Minecraft 1.21.11 classpath 执行 `javap -c -p`：

- `com.mojang.blaze3d.audio.Channel` 写入 `AL_POSITION`、`AL_PITCH` 等状态，没有
  velocity setter，也未写 `AL_VELOCITY`；
- `com.mojang.blaze3d.audio.Listener` 写入 position/orientation，没有写
  `AL_VELOCITY`；
- `DroneLoopSoundInstance` 的程序化 layer 始终给 OpenAL `pitch=1.0`；
- `DroneAcousticRenderState.updateSource(...)` 在生成 PCM 前调用项目自己的
  `DopplerShift.apply(...)`。

所以当前 vanilla source/listener velocity 都保持 OpenAL 默认零值，原生 Doppler
原本是 inert。guard 仍然有产品价值：它把“不能重复移频”变为每帧可观测 invariant，
并对未来 Minecraft、音频 mod 或 listener velocity 接入保持防御性。

## 实现

`OpenAlNativeDopplerGuard` 使用与 persistent EFX controller 相同的
sound-executor 所有权规则：

1. render/client thread 只收集活动 drone `ChannelHandle` 和内部 Doppler ratio；
2. latest-wins request 在 Minecraft `Sound engine` executor 上执行所有 AL 调用；
3. 每个 OpenAL context 首次见到 source 时保存原始 velocity；
4. 只把目标 drone source velocity 写成当前 listener velocity；
5. source 离开目标集或功能停止时恢复原始 velocity；
6. context 改变时丢弃旧 context 的 source id，不对失效 id 做恢复或删除；
7. 每次发布 factor、speed of sound、distance model、source pitch、内部 ratio、
   guard velocity error、native ratio error 和 AL error diagnostics。

纯函数 `nativeDopplerRatio(...)` 按 OpenAL 1.1 方程实现，并对非法 global state、
非有限 vector 和不稳定分母 fail closed。单元测试同时证明：相同 velocity 得到
ratio `1`、factor `0` 得到 `1`，以及内部 ratio 与非中性 native ratio 会相乘，
所以不能同时启用两个所有者。

## 真实运行证据

运行：

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :fabric-mod:runClientGameTest
python docs/scripts/verify_openal_native_doppler_guard.py `
  --report build/research/openal-native-doppler-guard-v1.json `
  --output-json build/research/openal-native-doppler-guard-verification-v1.json
```

当前 USB PnP Sound Device 上的 OpenAL Soft 结果：

| 指标 | reload 前 | reload 后 |
|---|---:|---:|
| guarded sources | 2 | 2 |
| OpenAL context rebuild count | 1 | 2 |
| `AL_DOPPLER_FACTOR` | 1.0 | 1.0 |
| speed of sound | 343.299987793 m/s | 343.299987793 m/s |
| maximum source/listener velocity error | 0 | 0 |
| maximum native ratio deviation after guard | 0 | 0 |
| internal tonal Doppler ratio | 1.000214224966 | 1.000200305720 |
| internally shifted sources | 2 | 2 |
| min/max `AL_PITCH` | 1.0 / 1.0 | 1.0 / 1.0 |
| AL error | 0 | 0 |

证据哈希：

- live report SHA-256:
  `695ec673ecdffc904047dcf0f3afce1bd688d0e309951a8db1ff9c520df4a0cf`
- independent verification SHA-256:
  `28ada6472f6557fd2ef5706b3667e90a2538dfb458f12242026a324db09c5495`

独立 verifier 的 8 个测试包含反例：非中性 native ratio、非 unity source pitch、
broadband Doppler overclaim、接受完整流 native resampling、修改其他声源、没有
context rebuild 和 release-calibration overclaim。

## 证据边界与下一步

这次证据证明的是实时 source-state ownership、内部/原生 ratio 不重复和
sound-engine reload 后恢复，不证明人耳听到的 Doppler 绝对正确。测试没有录音、
物理 loopback、物理设备切换、真实 FPV reference 或听测，因此：

- `real_audio_capture=false`
- `physical_device_switch_exercised=false`
- `audible_doppler_validated=false`
- `release_calibrated=false`

下一项真正的声源物理门禁应把 blackbox 的径向速度、预期 tonal ratio、程序化
oscillator 实际零交叉频率和 D086 marker timeline 绑定到同一份报告；随后在获得
明确录音授权后，用真实 loopback 验证靠近/远离 sweep 的频率轨迹。不能用本次
AL state 证据替代可听或电声端到端证据。
