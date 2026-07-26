# Real-time Acoustics Runtime Integration

更新日期：2026-07-24

## 当前端到端路径

```text
DroneEntity synced telemetry (client tick)
  -> DroneAcousticSourceMapper
  -> immutable AcousticSourceFrame
  -> OrderTrackedRotorModel
  -> immutable AcousticEmissionFrame
  -> volatile snapshot hand-off
  -> PhaseContinuousSynthesizer (sound streaming thread)
  -> 48 kHz / mono / signed PCM16
  -> FabricSoundInstance#getAudioStream
  -> Minecraft streaming Channel
```

游戏线程不生成 PCM，音频线程也不直接读取 `DroneEntity`。线程边界只交换不可变
`AcousticSourceFrame` 和 `AcousticEmissionFrame` 引用。

实验性 listener reverb 仍遵守同一边界：client thread 每秒至多捕获一次局部
`13×9×13` material snapshot；独立 latest-wins worker 运行 128×8 reflection probe
并发布 FDN controls；sound thread 只读取不可变 source/environment snapshot，
把所有无人机预混后送入一份 8-line FDN。实现和当前边界见
[`decision-D070-listener-shared-fdn.md`](decision-D070-listener-shared-fdn.md)。

## 已同步的静态声学元数据

`DroneEntity` 现在通过 `SynchedEntityData` 同步：

- rotor radius，单位 m；
- blade count；
- motor pole pairs。

每旋翼 RPM、功率和聚合气动载荷使用已有同步遥测。客户端以 1 block = 1 m、
20 tick/s 映射位置和速度。旋翼转向暂由偶/奇索引交替推断；它只影响相位方向，
必须在后续 airframe metadata 工作中替换为实际 `RotorSpec` 转向同步。

## 声源实现

- 机械轴频：`RPM / 60`；
- blade-pass frequency：`bladeCount * RPM / 60`；
- electrical frequency：`polePairs * RPM / 60`；
- 候选 cogging order：`2 * polePairs * RPM / 60`；
- BPF 默认最多 12 阶，并在 `0.475 * sampleRate` 截止；
- 每个 tone 以 `(kind, rotorIndex, order)` 保持振荡器状态；
- 游戏 tick 改变频率时不重置 phase；
- tone amplitude 在一个音频块内线性平滑；
- 三频带噪声由确定性 white-noise、300 Hz 与 3.2 kHz 一阶分频构造；
- 最终使用无状态软限幅，保证输出有限且位于 `(-1, 1)`。

当前幅值、滤波频率和 motor/propeller broadband split 仍为 `[H]` 参数，必须用真实
录音完成标定，不能作为绝对 SPL 结论。

## Minecraft/Fabric 原生接入

连续 motor 与 propeller sound definition 设置 `"stream": true`。
`DroneLoopSoundInstance` 实现 Fabric API 的 `FabricSoundInstance` 并返回自定义
`AudioStream`。Minecraft 继续拥有声道、位置衰减、停止、reload 和设备生命周期。

程序化频谱已经由 RPM 决定，因此 procedural 模式的 OpenAL source pitch 固定为
`1.0`，避免再次使用旧 pitch-loop 映射造成双重升调。

每 tick 使用源与听者沿视线的径向速度计算经典 Doppler ratio；温度决定声速，
异常/teleport 速度先限制在 `±0.9c`，最终 ratio 限制为 `0.5–2.0`。它只移动
tonal frequency，宽带谱搬移留待实测标定。

安全回退：

```text
-Dfpvdrone.proceduralAudio=false
```

关闭后仍通过同一 streaming channel 解码原有 OGG loop。

实验性共享晚期混响：

```text
-Dfpvdrone.listenerReverb=true
```

它默认关闭，还要求程序化 PCM 开启；外部 Sound Physics owner 存在时自动停止。

## 尚未完成

- 每旋翼真实 spin direction/phase/center metadata；
- 方位指向性；
- boundary-level portal adjacency 增量组装与产品 UDFA/UTD edge response；五探针
  acoustic-aperture 直达射线、sparse voxel snapshot、材质透射、
  connected-air-region/portal hierarchy、partition fingerprint 局部 topology
  invalidation、cell A* correctness fallback、频域 UDFA infinite-wedge oracle
  和异步调度已经实现；
- OpenAL EFX low-pass/reverb send；当前共享 FDN 不依赖 EFX；
- 共享 FDN 的 stone/wool Minecraft 材料与 estimator 固定 Client GameTest 已通过；
  启用实际 wet stream 的音频集成测试、snapshot capture frame P99 和真实 RIR
  RT60/EDT/DRR 校准仍待完成；
- 音频设备 reload 和真实游戏长时间 underrun 测试；
- 实测 FPV 录音标定及主观听测。
