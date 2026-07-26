# D070 — 晚期混响必须按听者共享，不能按无人机复制

日期：2026-07-25  
状态：**core + official Fabric wet bus + fixed Minecraft material scene implemented；
Java-side performance/lifecycle passed，measured-RIR/OpenAL end-to-end pending**

## 决策

晚期混响采用单个 listener-relative 程序化流：

```text
active drone AcousticEmissionFrame snapshots
  -> sound-thread source synthesizers
  -> one pre-mixed mono environment send
  -> one 8-line multiband FDN
  -> one FabricSoundInstance#getAudioStream wet bus
```

无论当前有 1 架还是 6 架无人机，FDN 都只有一份。每架无人机仍保留原有 motor/
propeller positional dry voices；wet bus 使用 `Attenuation.NONE`、listener-relative
position，由 Minecraft 继续管理 voice、category volume、stream 和 device lifecycle。
没有访问私有 OpenAL source id、EFX slot 或 channel mixin。

外部 Sound Physics mod 接管 propagation 时，`PropagationCompatibilityPolicy` 会停止
该 wet bus，避免重复混响。OGG fallback 模式也不启动它。

## 环境统计

`VoxelReflectionProbe` 是 listener-centred 的确定性 Fibonacci ray probe：

- runtime：`128 rays × 8 bounces`；
- research reference：`256 rays × 12 bounces`；
- 与 DDA 相同的 simultaneous-axis tie epsilon；
- 进入体素表面时记录 segment length、材料 absorption/scattering 和逃逸；
- partial fill 的反射能量为 `fill × (1 - absorption)`；
- material scattering 在 specular 与 deterministic cosine-hemisphere 方向间混合；
- incomplete snapshot、listener-in-solid 或 traversal truncation fail closed。

它估计 openness、mean free path、diffusion、三频带 RT60/EDT、DRR 和首反射能量。
这不是 source-to-listener early-tap solver，不能宣称已经获得第一反射方向/时延。

Fabric runtime 每 20 ticks 在 client thread 捕获听者周围 `13 × 9 × 13 = 1521`
cell immutable snapshot，worker 才执行 probe。未加载 chunk 使 snapshot
incomplete，旧环境参数继续使用。D077 采样修正后的集成世界测试测得 capture P99
`0.4501 ms`、probe P99 `1.0397 ms`；feature 默认关闭的剩余原因是声学校准、
late-field transport 与 OpenAL end-to-end 门禁，而不是该机 Java 侧预算。

## FDN

`ListenerSharedFdn` 使用：

- 8 条互质近似 delay：48 kHz 下
  `1423/1613/1789/1999/2131/2347/2539/2741 samples` **[H]**；
- normalized Hadamard feedback；
- 700 Hz 与 4 kHz 一阶 crossover **[H]**；
- 每条 delay/每频带按
  `g = 10^(-3 * delaySeconds / RT60Seconds)` 设反馈；
- 环境参数默认 200 ms 平滑，更新时不清 delay memory；
- wet gain 从首反射能量与 openness 映射，最大 `0.45` **[H]**。

冲激测试证明最短物理 delay 前输出为零；长 RT60 的 late energy 高于短 RT60；
任意 buffer chunking 逐样本一致；参数切换不会把已有 tail 清零。

## 确定性环境 reference

权威命令：

```powershell
.\gradlew.bat --no-daemon \
  :computational-acoustics-core:reverbEnvironmentReference
```

输出
`build/research/reverb-environment-reference-v1.json`，连续两次 SHA-256 均为：

```text
411ff09c420e5b2640965345a6262b13ada10c1f63e75fea979f75504fb78cb2
```

固定同几何结果：

| 场景 | openness | RT60 low/mid/high (s) |
|---|---:|---:|
| open air | 1.0 | 0 / 0 / 0 |
| stone room | 0.0 | 5.720 / 3.396 / 2.089 |
| wood room | 0.0 | 1.453 / 0.748 / 0.431 |
| soft room | 0.0 | 0.655 / 0.236 / 0.124 |

这些值仅证明材料/环境方向排序，没有经过真实 RIR 校准，`evidence_class=H`、
`release_calibrated=false`。

真实 measured-RIR 分析链现已由 AIR v1.4 闭环，但没有与 Minecraft scene 匹配的
几何/材质，因此不改变上述 release 状态。结果与边界见
[`decision-D071-measured-air-rir-reference.md`](decision-D071-measured-air-rir-reference.md)。
D072 经 D077 sampler 修正后证明同 room-average absorption 下 voxel probe RT60
maximum error 只有 `1.32%`；当前 stone 在相同 AIR 几何下至少慢 `2.78×`，所以主要未决项是
block material calibration，而不是继续调 decay 公式。

## 官方 Minecraft 集成场景

官方 Fabric Client GameTest 会进入集成客户端世界，以玩家 eye position 为中心构造
闭合 stone 与 wool 房间，经真实 `BlockState -> MinecraftAcousticMaterials` 映射、
`MinecraftReflectionSnapshot.capture` 和 runtime `128 rays × 8 bounces` probe
生成：

```powershell
.\gradlew.bat --no-daemon :fabric-mod:runClientGameTest
```

输出根项目 `build/research/minecraft-reverb-client-gametest-v1.json`，本次
SHA-256：

```text
20aaf4f80914aa3f3a53c478a1c18ac64be3d7aec4e61d31e1381635b0a6780c
```

| 场景 | openness | RT60 low/mid/high (s) |
|---|---:|---:|
| Minecraft stone room | 0.0 | 7.870 / 4.673 / 2.875 |
| Minecraft wool room | 0.0 | 0.905 / 0.326 / 0.172 |

该测试关闭了“合成 volume 与 Minecraft 材料映射脱节”的风险，并验证石材衰减
时间大于羊毛、同材质 low > mid > high。它没有启用实际声卡上的 wet bus，也没有
测量真实 RIR；输出仍明确为 `release_calibrated=false`。

## 性能状态

同机轻量基准：

```powershell
.\gradlew.bat --no-daemon \
  :computational-acoustics-core:reverbRuntimeBenchmark
```

在 i7-14700KF、128×8 stone-room probe、200 iterations：

- probe P50 `0.443 ms`；
- P95 `0.614 ms`；
- P99 `0.803 ms`；
- 8-line FDN `44.0 ns/sample`。

这通过当前 worker `1–2 ms` 假设。D075 又补充了真实 Minecraft 1521-cell
snapshot、runtime probe、6 架无人机/360 tones wet stream 和生命周期测试：

- integrated capture P99 `0.4501 ms`；
- integrated probe P99 `1.0397 ms`；
- 4096-byte audio read P99 `2.4007 ms`，占 buffer `5.63%`；
- 16384-byte audio read P99 `13.7304 ms`，占 buffer `8.05%`；
- source removal、tail preservation、重复 close 和 replacement stream 均通过。

完整证据见
[`decision-D075-listener-reverb-performance-lifecycle.md`](decision-D075-listener-reverb-performance-lifecycle.md)。
这些数字仍不包含 OpenAL enqueue/device callback、真实 device replacement 或
可观测 underrun，因此不是发布级端到端音频结论。

## 启用方式与剩余 gate

当前实验默认关闭：

```text
-Dfpvdrone.listenerReverb=true
```

还要求 `fpvdrone.proceduralAudio=true` 且内部 propagation owner 有效。

发布前仍必须：

1. 实测 world unload、resource reload、真实 audio-device replacement 和无 voice leak；
2. 用 measured/accepted RIR 检查 RT60/EDT `<=15%`、DRR `<=3 dB`；
3. 在真实游戏长时间运行中测 OpenAL scheduling 与 sound-thread underrun；
4. 按 D074 分离 early BRDF 与 late diffuse transport；
5. 若任一发布门槛失败，保持 property off，不影响 dry procedural/OGG fallback。

D073 的 PTB train/holdout 研究进一步表明，公开材料候选不能直接解除此边界：
异质 stone/wool/wood/glass 房间的 256×12 probe 相对 mean-log reference 最大仍差
`17.77%`，所以本文件的 material `[H]` 与默认关闭状态保持不变。

D074 又用同 absorption/geometry 的完全漫射对照把长期最大误差从 `25.02%`
降到 `0.38%`，将问题定位到 configured scattering 路径分布。后续必须分离 early
material BRDF 与 late diffuse transport，不能通过单纯增加 ray 数解除门禁。
D076 的 research-only two-hit split 已把误差降至 `0.40%`，证明该方向可行；
但尚未完成多几何、physical mixing-time 与 matched-RIR 验证，所以 runtime 默认
策略和 feature 开关仍不改变。
