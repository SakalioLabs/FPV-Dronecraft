# D077 — 无状态漫射采样与 MFP 路径阈值通过多几何诊断

日期：2026-07-25  
状态：**diffuse sampler correlation removed；4-room path-scaled diagnostic
passed；measured mixing-time pending**

## 发现并修正采样相关性

D076 之后把相同策略扩展到 cube、corridor 和 hall。旧的纯漫射 cube 在
`4096 rays × 48 bounces = 196,608 hits` 下仍有约 `5 pp` 面积分布误差和
`16.61%` RT60 误差。这不符合该样本量的合理 Monte Carlo 波动。

旧采样器用 `rayIndex` 和 `bounce` 的两组线性无理数序列生成半球坐标。每个后续
位置又由此前方向决定，因此线性序列与射线路径形成系统相关性。

采样现改为：

```text
key = (rayIndex << 32) xor bounce xor fixed-seed
u1  = top-53-bits(SplitMix64(key))
u2  = top-53-bits(SplitMix64(key + golden-ratio-constant))
direction = cosine-weighted hemisphere(u1, u2)
```

它仍完全确定、无分配、无共享 RNG 状态，但不同 ray/bounce 的方向不再由线性组合
耦合。原 runtime API、ray 数、材料与初始 Fibonacci directions 不变。

修正后，同一 low-square 4096×48 all-diffuse 对照：

- 最大 hit-fraction 误差：`4.02 pp → 0.26 pp`；
- 最大 RT60 误差：`7.55% → 0.38%`；
- MFP：`4.088 m → 3.895 m`，解析值 `3.882 m`。

因此旧的 7% 级 residual 主要是采样器伪影；configured material-scattering 的长期
偏置仍存在，但重算值为 `25.02%`，不是旧的 `33.20%`。

## 物理化的切换变量

`ReflectionDirectionPolicy` 现在同时接收：

```text
material, bounce index, cumulative path meters
```

默认策略仍只返回 `material.scattering`。研究策略用解析 mean free path

```text
MFP = 4V/S
mixing threshold = multiplier × MFP
time = threshold / 343 m/s
```

替代固定 bounce。累计路径包括当前命中的 leg；达到阈值后的下一反射方向转为漫射。

## 四种几何

固定 PTB stone floor、wool ceiling、wood x-walls、glass z-walls：

| 场景 | interior dimensions |
|---|---:|
| low-square | 11×11×3 m |
| cube | 7×7×7 m |
| corridor | 21×5×3 m |
| hall | 15×9×5 m |

每个场景运行 configured、fixed-two-hit，以及
`0/0.5/1/1.5/2/3/4 × MFP`，每项均为 4096×48。

跨四房间最大误差：

| 策略 | 最大 RT60 误差 | 最大 hit-fraction 误差 |
|---|---:|---:|
| configured material scattering | 29.36% | 11.83 pp |
| all diffuse / 0×MFP | 0.38% | 0.75 pp |
| fixed two hits | 0.76% | — |
| 2×MFP | 1.04% | 0.77 pp |
| 全部 0–4×MFP path candidates | 2.23% | <1 pp at 2×MFP |

所有 path candidates 都通过预设 3% 诊断门。2×MFP 在四个房间对应约
`20–31 ms`，它保留一个按房间尺度变化的 early 区间；但该数值尚未由真实 RIR
mixing time 测得。

## 证据

```powershell
.\gradlew.bat --no-daemon verifyPtbPathMixingTime
```

- multi-room matrix：
  `cbeb9cb3ae3d0e2a6920bb5b5c201823c2ee626bfa28c96027581d251bf3c6be`；
- independent verification：
  `e8af5394df9c3aa07bffcfb016bdd0e36e33f0f78fa5ee4afc1026a4f774a869`。

verifier 固定房间、维度、4096×48 预算、阈值矩阵、`threshold=MFP×multiplier`、
343 m/s、命中数和材料法线一致性。

## 决策

1. 保留 SplitMix64 无状态漫射采样，旧线性序列不再作为有效 evidence；
2. late-field 切换的研究变量从 bounce count 升级为累计路径/时间；
3. `2×MFP` 是下一轮 matched-RIR 的首选候选，不是 release constant；
4. runtime 默认仍使用 material scattering，避免在验证开放空间与 RIR 前改变产品；
5. 下一步加入 doorway/open-room 与多连通空间，并从实测 RIR 的 echo density 或
   normalized echo density 估计 mixing time。

## Claim boundary

四个场景都是闭合 voxel shoebox，Eyring 面积公式在这些场景适用。结果不能外推到
洞穴、门廊、室外或多房间，也没有证明 PTB 材料就是 Minecraft 方块。feature 继续
默认关闭，`minecraft_release_calibrated=false`。
