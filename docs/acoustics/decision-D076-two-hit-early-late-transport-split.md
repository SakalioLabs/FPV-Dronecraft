# D076 — 前两次材料反射后切换漫射可恢复 late-field 一致性

日期：2026-07-25  
状态：**two-hit early / diffuse-late causal candidate passed；
runtime default 与 release calibration 未改变**

## 假设

D074 证明，把未校准的材料 scattering 用于全部 bounce 会让低矮异质房间的长期
surface-hit 分布偏向 floor/ceiling。D076 测试最小分离策略：

```text
bounce 0–1: material.scattering
bounce 2+:  diffuse direction (scattering = 1)
```

吸收、材料标识、几何、初始 Fibonacci rays、DDA、能量终止与统计方式全部不变。
前两次 hit 仍进入现有 early statistics；只有后续传播方向策略改变。

## 实现边界

`VoxelReflectionProbe` 增加显式
`ReflectionDirectionPolicy` research overload。原三参数/observer overload 都固定委托
`MATERIAL_SCATTERING`，所以 Minecraft runtime 行为不变。

策略返回值必须为有限 `[0,1]`；越界立即拒绝。observer 继续报告真实命中材料，
`ReflectionStatistics.meanScattering` 也继续记录材料属性，而不是用方向策略覆盖
材料数据。

## 35-point 收敛矩阵

与 D074 完全相同：

```text
rays    = 64, 128, 256, 512, 1024, 2048, 4096
bounces = 4, 8, 12, 24, 48
```

关键结果：

| 模式/预算 | 最大 RT60 误差 vs area mean-log | 最大 hit-fraction 误差 | MFP |
|---|---:|---:|---:|
| configured 4096×48 | 25.02% | 6.88 pp | 3.413 m |
| all-diffuse 4096×48 | 0.38% | 0.26 pp | 3.895 m |
| two-hit split 256×12 | 0.56% | 0.88 pp | 3.932 m |
| two-hit split 4096×48 | 0.40% | 0.17 pp | 3.874 m |

解析 `4V/S = 3.882 m`；two-hit 最大预算 MFP 相对误差约 `-0.23%`。其最大
三带 RT60 误差通过固定 10% diagnostic gate，并与完全漫射对照同级。说明不必
牺牲前两次材料方向假设，也能消除 configured scattering 的长期偏置。

## 证据

```powershell
.\gradlew.bat --no-daemon verifyPtbMaterialMixtureConvergence
```

确定性 artifact：

- two-hit convergence：
  `834fc34c0d32a673c9946a45cc73315cf3a9e02936bbed91781873d307276a1d`；
- configured/diffuse/two-hit combined verification：
  `0888fe856d9dcfb47c22da0ab0eb8c4c2ffbf040d31af9e14b2787289d3f4236`。

新增 gates：

- same absorption/geometry：通过；
- normal/material axis mismatch = 0：通过；
- two-hit split 保留两个 early material hits：通过；
- two-hit split vs diffuse formula `<=10%`：通过，`0.40%`；
- `minecraft_release_calibrated=false`：保持。

## 决策

下一版 listener late-field probe 的首选研究候选是“前两次材料反射、之后漫射”，
而不是增加 rays 或把所有 bounce 永久设为全漫射。接入 runtime 前仍需：

1. 在多个长宽高比与 doorway/open-room 场景验证，而非只看一个 closed shoebox；
2. 将两次 hit 换算为物理 mixing time/path length，并用 matched RIR 约束；
3. 确认 openness、DRR 与环境平滑不会在策略切换处产生跳变；
4. 重跑 Minecraft integrated scene 与 D075 P99；
5. 在完成上述验证前保持 `fpvdrone.listenerReverb=false`。

## Claim boundary

本轮只证明 two-hit 分离在一个 PTB 异质闭合房间恢复 diffuse-field diagnostic
一致性。它不证明“两个 bounce”对所有 Minecraft 空间都是正确 mixing time，也不
提供真实材料 scattering 测量。

D077 已把该假设扩展到四种闭合几何，并将策略变量升级为累计路径：
`2×(4V/S)` 跨房间最大 RT60 误差 `1.04%`。它是 matched-RIR 候选而非发布常数；
详见
[`decision-D077-path-scaled-mixing-time-and-diffuse-sampler.md`](decision-D077-path-scaled-mixing-time-and-diffuse-sampler.md)。
