# D110 — room RIR 与 block material 校准资格分层

日期：2026-07-26  
状态：AIR、AIR shoebox、PTB 与 Minecraft backend matrix 的 hash-bound 审计及独立
verifier 通过

## 决策

不使用 AIR 房间的等效吸声系数覆盖 Minecraft `stone_dense`。当前 stone 参数保留，
下一步参数层级转向 **scene composition / interior treatment**：用真实房间 RIR
约束“整间场景”的 RT60，用 PTB 试样约束“单类材料”的吸声先验。

这是量纲与语义分层，不是因为 AIR 数据质量不足：

- AIR 是真实采集 RIR，可用于房间级 RT60/EDT/DRR 目标；
- AIR 房间等效吸声同时包含边界、家具、座椅、安装和实际内装，不能被解释为一种
  Minecraft 方块材料；
- PTB 是材料试样数据，更接近 block material prior，但产品、厚度、安装和入射条件
  仍不足以构成发布校准；
- Minecraft closed/partial/open matrix 可验证相对单调性，不提供跨数据集绝对听感
  标定。

## 绑定证据

| 来源 | SHA-256 | 合法用途 |
|---|---|---|
| AIR measured RIR | `8c962c2ceca292475fb035057bfd4e3b6170eb698cd36902bb9c92bfa093948b` | scene-level RT60 目标 |
| AIR-matched shoebox voxel | `4de568d12ac116ac2f58197fe9125ca188ad5e2be3a4201c942e32e55953bb43` | 几何/材料误差分解 |
| PTB material absorption | `8e506562d92ea131af8ee402736c9ce498a7a3894acb883fa78637a1620d0bb9` | material prior |
| Minecraft backend matrix | `2d904badc6792720b408ed968e246ff59316f3fa440c17daab17196f656c8694` | 相对环境响应 |

AIR 17 个位置中 13 个复现到 published RT60 的 15% 内；并非所有位置都通过，因此
审计保留 `air_every_position_within_15_percent=false`，不把单个估计器误差隐藏在
房间平均值里。

## AIR 房间与当前 stone

| 房间 | band | measured RT60 | AIR 等效吸声 | 当前 stone 吸声 | stone RT60 / measured |
|---|---|---:|---:|---:|---:|
| booth | low | 0.1269 s | 0.3766 | 0.0300 | 15.52× |
| booth | mid | 0.1372 s | 0.3542 | 0.0500 | 8.52× |
| booth | high | 0.1082 s | 0.4254 | 0.0800 | 6.65× |
| lecture | low | 0.8572 s | 0.1708 | 0.0300 | 6.15× |
| lecture | mid | 0.9031 s | 0.1629 | 0.0500 | 3.47× |
| lecture | high | 0.6915 s | 0.2072 | 0.0800 | 2.78× |

AIR 等效房间吸声相对 stone 的最大倍率为 `12.5548×`。这证明“裸石材房间不能代替
真实内装房间”，但不证明 stone coefficient 应放大同样倍数。

## PTB dense stone 交叉检查

| band | 当前 runtime | PTB candidate | 相对差 |
|---|---:|---:|---:|
| low | 0.0300 | 0.02667 | 11.11% |
| mid | 0.0500 | 0.03500 | 30.00% |
| high | 0.0800 | 0.07000 | 12.50% |

最大相对差为 `30%`，低于本轮 material-prior 一致性门限 `50%`。这不是发布级拟合，
但足以拒绝“把 AIR 房间平均吸声直接赋给 stone”的方案。

## Minecraft 相对响应

当前生产映射仍严格满足 closed→partial→open 三频带 RT60 下降：

| 环境 | low | mid | high | wet |
|---|---:|---:|---:|---:|
| closed | 6.5475 s | 3.9198 s | 2.4429 s | 0.3883 |
| partial | 3.3893 s | 1.4949 s | 0.8320 s | 0.3874 |
| open | 2.1817 s | 0.8706 s | 0.4711 s | 0.3858 |

因此保持 portal/openness 相对行为；D110 不通过一个全局 RT60 clamp 掩盖场景材料
组成问题。

## 校准资格矩阵

- AIR measured RIR → scene-level RT60 target：允许
- AIR room effective absorption → block override：禁止
- PTB dense stone → material prior：允许
- PTB dense stone → release override：禁止
- Minecraft backend matrix → relative environment validation：允许
- cross-dataset absolute release calibration：禁止

最终：

- `replace_runtime_stone_absorption=false`
- `production_change_required=false`
- `next_parameter_level=scene-composition-and-interior-treatment`
- `release_calibrated=false`

## 独立 verifier

verifier 不信任 D110 report 的摘要或策略位。它从四个源报告重新计算：

- 两个 AIR 房间的三频带 RT60、等效吸声和 stone 比率；
- PTB candidate/current 的三频带相对差及最大值；
- Minecraft 三环境的 RT60 严格单调性；
- eligibility matrix 与 production decision。

15 个负向测试覆盖 source hash/status、AIR 绑定、房间和环境顺序、room/PTB/summary
metric、stone identity、非单调环境、block override、runtime replacement 以及 capture/
release overclaim。

## Claim boundary

本结果没有重新分析原始音频，也没有打开物理 endpoint。它只使用已经验证并固定
hash 的 AIR 数值统计、PTB 数值统计与软件 renderer 控制：

- `physical_endpoint_opened=false`
- `captures_audio=false`
- `release_calibrated=false`

结果不识别 AIR 房间内每件物体的材料，不证明玩家听感，也不产生发布 profile。

## 可复现证据

```powershell
.\gradlew.bat verifyAcousticCalibrationAdmissibility
```

- report：
  `build/research/acoustic-calibration-admissibility-v1.json`
- verification：
  `build/research/acoustic-calibration-admissibility-verification-v1.json`
- report SHA-256：
  `f6735684aad68eabcf1b12c2990357f666f01d2191630732080c12eb523316f5`
- verification SHA-256：
  `632d2294a5b4dfa2245829b953644b121f4cbb67e58c7db385858aebf5e8ac8c`

## 下一步

D111 应建立 scene-composition inverse fixture，而不是修改基础 stone：

1. 以 AIR booth/lecture 三频带等效吸声为房间级目标；
2. 只使用已经有 PTB 先验的 stone / wood / porous / glass 组成候选表面混合；
3. 解非负、和为 1 的材料面积占比，保留不可辨识解范围；
4. 在匹配尺寸的 voxel room 中构建可复现 surface mixture；
5. 用当前 `LateReverbEstimator` 验证 RT60 误差、射线预算与随机种子稳定性；
6. 只有跨房间 holdout 通过后，才考虑把“场景内装响应”接入生产；不得把 inverse
   mixture 解释为 AIR 的真实家具清单。
