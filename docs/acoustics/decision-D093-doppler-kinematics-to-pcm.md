# D093 — Minecraft 运动学到 PCM16 的 Doppler 一致性

日期：2026-07-25

状态：真实 Client GameTest、生产合成器 PCM16 测量、独立物理重算与反例测试通过；
server blackbox、混合实时流和可听输出未绑定

## 结论

当前 tonal Doppler 不只是内部 target 参数发生了变化。对真实 Minecraft
source/listener 同步帧，项目用生产 `DopplerShift` 计算径向速度与频率 ratio，
再由生产 `PhaseContinuousSynthesizer` 生成 motor/propeller 音调。经过相同 soft
clip 和 PCM16 量化后，从样本的正向零交叉独立反测频率，motor 与 propeller 的误差
分别为 `-3.3095 ppm` 和 `+0.1287 ppm`，通过 `25 ppm` 门禁。

该结果关闭的是“物理运动学 → tonal target → 稳态 PCM”的代码内一致性，不是
OpenAL、扬声器或人耳端到端验证。

## 实现

### 物理数据绑定

`DroneAcousticRenderState` 现在保留同次更新所用的：

- source position/velocity 和 simulation timestamp；
- listener position/velocity；
- 环境温度推导的 sound speed；
- directivity 后、Doppler 前的 emission；
- 已发布到实时渲染状态的 Doppler ratio。

`DopplerShift.Result` 同时返回 sound speed、listener radial velocity 和 source
radial velocity。即使 source/listener 共点也先验证 sound speed，避免旧路径在
零距离时绕过非法物理参数检查。

### PCM 测量

`DopplerPcmConformance` 执行：

1. 用实际 source/listener frame 和 temperature-dependent sound speed 再运行
   生产 `DopplerShift`；
2. motor layer 选择最强非 blade-pass tone，propeller layer 选择最强
   blade-pass tone；
3. 每层只保留所选 tone，明确移除 broadband，避免随机噪声污染 estimator；
4. 用生产 `PhaseContinuousSynthesizer` 在 48 kHz 生成 1 s；
5. 经过 soft clip 和 PCM16 quantization；
6. 丢弃前 200 ms 增益 attack，使用剩余 38,400 samples 的所有正向零交叉和
   线性亚采样插值估计频率；
7. 报告 expected/measured/error Hz、ppm、crossing 数和 clipping 数。

这里新 oscillator 直接以目标频率创建，因此测量的是稳态频率一致性。它没有覆盖
已存在的 40 ms target-frequency smoothing，也没有测量运动中连续 chirp。

## 真实 Client GameTest

测试先等待 source speed 至少 `0.25 m/s` 且 ratio 明显偏离 1，避免把静止 drone
冒充飞行动力学证据。本次捕获：

| 字段 | 值 |
|---|---:|
| entity id | 3 |
| simulation time | 600,000,000 ns |
| source velocity | `[0, 0.26857108, 0] m/s` |
| listener velocity | `[0, -1.56800003, 0] m/s` |
| listener radial velocity | `+0.0724145591 m/s` |
| source radial velocity | `-0.0124033520 m/s` |
| sound speed | `346.45 m/s` |
| physical/render ratio | `1.000244828829` |

频率结果：

| layer | base | expected | measured | error |
|---|---:|---:|---:|---:|
| motor shaft | 115.269466 Hz | 115.297687 Hz | 115.297306 Hz | -3.309486 ppm |
| propeller blade-pass | 345.808398 Hz | 345.893062 Hz | 345.893107 Hz | +0.128676 ppm |

两层均为 48 kHz、38,400 analyzed samples、PCM16、`clipped_samples=0`；motor/
propeller 分别有 `92/277` 个正向 crossing。

## 独立验证

`verify_doppler_pcm_conformance.py` 不信任 Java 报告中的投影或 ratio。它从报告的
三维位置与速度重新计算 normalized listener-to-source direction、两个 radial
velocity，并重新计算：

```text
ratio = (sound_speed + listener_radial)
        / (sound_speed + source_radial)
expected_frequency = base_frequency × ratio
```

随后重验 Hz/ppm 算术、layer/tone 分类、48 kHz/38,400 sample contract、crossing、
clipping、broadband exclusion 和所有 claim boundary。8 个 Python 反例覆盖：
径向速度脱链、render ratio 脱链、PCM 频率超限、broadband 混入、blackbox/OpenAL
overclaim 和非有限 vector。Java 测试另覆盖 approaching/receding/stationary
运动及 silent PCM 拒绝。

运行：

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :fabric-mod:runClientGameTest
.\gradlew.bat --no-daemon --max-workers=1 verifyDopplerPcmConformance
```

证据哈希：

- live report SHA-256:
  `ca9fe96284bcc84bbcc56daba11b353ef90bf5b158b39ad54bcb80decf6d39f1`
- independent verification SHA-256:
  `2a534967bc3a8a7a1e6238818cea62d25bf036227c40be31c4ee337e2fd36543`

## 证据边界与下一步

报告明确保持：

- `server_blackbox_csv_bound=false`
- `mixed_live_stream_measured=false`
- `openal_playback_capture=false`
- `real_audio_capture=false`
- `release_calibrated=false`

下一步 D094 应捕获多个连续生产 audio callback/chunk，而不是重新合成孤立 tone，把每个
chunk 的实际混合 PCM、当时 emission hash、source/listener frame、frequency
smoothing state 和 server blackbox tick 绑定。至少需要 approaching、closest
approach、receding 三段，验证 40 ms smoothing 的连续 chirp、无 phase reset 和
broadband 不被整体 resample。获得明确录音授权后，才把同一轨迹交给 D086/D087
loopback marker 链做真正电声端到端验证。
