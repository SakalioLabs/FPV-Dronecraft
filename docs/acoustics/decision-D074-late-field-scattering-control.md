# D074 — 异质房间偏差来自 late-field scattering 路径分布

日期：2026-07-25  
状态：**35-point rays×bounces convergence + all-diffuse causal control passed；
configured-scattering diffuse-field gate failed**

## 问题

D073 的 11×11×3 mixed room 使用相同 PTB absorption，却在 256 rays × 12 bounces
下相对面积 mean-log Eyring 偏差 `17.77%`。候选原因有：

1. ray 数不足；
2. bounce 数不足；
3. edge/corner cell 的材料与实际入射面法线不一致；
4. 当前 material scattering `[H]` 使路径未进入 Eyring 所假定的 diffuse field；
5. mean-log 公式自身错误。

D074 用固定 absorption/geometry 逐项分离。

## 观察接口

`VoxelReflectionProbe` 新增 allocation-free diagnostic observer。每个 surface hit
报告 ray、bounce、material、normal 和 leg distance；普通运行仍走原三参数入口，
不增加 hit collection。

审计按材料预期法线：

- stone floor / wool ceiling：Y normal；
- wood x walls：X normal；
- glass z walls：Z normal。

全部 70 个 configured/diffuse runs 的 `normal_material_axis_mismatches=0`。因此当前
闭合矩形里 edge/corner 赋材不是误差来源。

## 收敛矩阵

两种 scattering 模式都运行：

```text
rays    = 64, 128, 256, 512, 1024, 2048, 4096
bounces = 4, 8, 12, 24, 48
```

最大预算 `4096×48 = 196,608 hits` 是数值收敛参考，不是真实房间 truth。

### 当前 configured scattering

surface scattering 保持：

```text
stone .18 / wool .65 / wood .45 / glass .08
```

| 预算 | horizontal hits | 最大 surface-fraction 误差 | 最大 RT60 误差 vs area mean-log |
|---|---:|---:|---:|
| 256×12 | 73.63% | 4.92 pp | 17.77% |
| 4096×12 | 74.51% | 5.64 pp | 19.27% |
| 256×48 | 76.16% | 6.23 pp | 23.71% |
| 4096×48 | 77.21% | 6.88 pp | 25.02% |

diffuse surface-area 预期 horizontal hits 为 `64.71%`。更深 bounce 让射线越来越
偏向低矮房间的 floor/ceiling，而不是趋近面积分布。4096×48 的 MFP 为
`3.413 m`，解析 `4V/S = 3.882 m`。

固定 48 bounces 时，64–4096 rays 相对最大预算的最大三带误差只有 `1.735%`，
通过 2% 收敛门。说明 ray count 已足够；256×12 相对 4096×48 仍差 `9.66%`，
真正变化来自路径深度下的方向分布。

### 完全漫射对照

保持 geometry 和四组 absorption 完全不变，只把四个表面的 reflection direction
scattering 强制为 `1.0`：

| 预算 | 最大 surface-fraction 误差 | 最大 RT60 误差 vs area mean-log |
|---|---:|---:|
| 256×12 | 0.79 pp | 2.95% |
| 4096×48 | 0.26 pp | 0.38% |

4096×48 MFP `3.895 m`，相对解析值约 `+0.34%`。完全漫射对照通过 10%
diagnostic gate，而 configured scattering 为 `25.02%`。由于唯一改变的是反射方向
scattering，本轮把主要偏差定位到**把未校准的方块 scattering 直接用于 late-field
路径传播**，而不是 absorption reducer、ray count 或 corner assignment。

## 证据

```powershell
.\gradlew.bat --no-daemon verifyPtbMaterialMixtureConvergence
```

确定性 artifact：

- configured convergence：
  `4b4713926acd1e48ab3b405109c1c12a4075adaf57b304acc2f9ee71871cc153`；
- all-diffuse control：
  `e52bb76390db916ece01488c019afbda33f365407489b44debaf68bf6335ed26`；
- independent verification：
  `0888fe856d9dcfb47c22da0ab0eb8c4c2ffbf040d31af9e14b2787289d3f4236`。

固定 gates：

- same absorption/geometry：通过；
- normal/material axis mismatch = 0：通过；
- configured 48-bounce ray sweep `<=2%`：通过，`1.735%`；
- configured scattering vs diffuse formula `<=10%`：**失败，25.02%**；
- all-diffuse control vs diffuse formula `<=10%`：通过，`0.38%`；
- `minecraft_release_calibrated=false`：保持。

## 决策

listener-shared FDN 的 late-field geometry probe 不应把当前 block scattering `[H]`
同时当作 early-reflection BRDF 和长期混合率。后续实现方向固定为：

1. direct/early reflection 保留 material-specific specular/diffuse 候选；
2. late-field probe 在 mixing time 后使用 diffuse transport，或单独估计达到 diffuse
   field 的转换过程；
3. `meanScattering` 可继续作为 FDN diffusion/damping 控制输入，但不能再隐式改变
   Eyring MFP/表面命中分布；
4. 用 measured scattering 或 matched RIR 决定 mixing time，不能用本轮
   all-diffuse control 冒充 release 参数；
5. 修正后重新跑 D073 mixed-room gate、Minecraft integrated lab 和 frame/audio
   P99。

## Claim boundary

本决策是同吸收因果对照，证明当前偏差主要由 configured scattering path dynamics
触发。它不证明每个 Minecraft 材料的真实 scattering，也不授权立即把 runtime
probe 永久改成全漫射；产品 feature 继续默认关闭。

D076 已验证最小分离候选：保留前两次 material-scattering hits、从第三次起转为
漫射。修正无状态采样后，4096×48 最大公式误差为 `0.40%`、hit-fraction 误差
`0.17 pp`，恢复
10% gate，同时 runtime 默认入口未变。详见
[`decision-D076-two-hit-early-late-transport-split.md`](decision-D076-two-hit-early-late-transport-split.md)。

D077 记录了旧线性半球序列的路径相关性、SplitMix64 修正和四种房间的复验。旧
artifact 已被上文新 hash 取代；完整因果证据见
[`decision-D077-path-scaled-mixing-time-and-diffuse-sampler.md`](decision-D077-path-scaled-mixing-time-and-diffuse-sampler.md)。
