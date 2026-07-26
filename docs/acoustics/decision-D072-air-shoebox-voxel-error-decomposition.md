# D072 — 用同一房间平均吸收分离 voxel probe 与材料误差

日期：2026-07-25  
状态：**analytic Eyring + one-metre voxel matched reference passed；block
material calibration remains open**

## 问题

D070 的 synthetic/Minecraft stone room 给出数秒级 RT60，而 D071 的真实 AIR
普通房间约为 `0.1–0.9 s`。直接把两者比较会同时混入：

1. 房间尺寸差异；
2. 一米体素量化；
3. reflection probe 的 mean-free-path 采样误差；
4. Eyring/late-decay 公式误差；
5. 材料吸收假设误差。

本决策把前四项固定后，才检查第 5 项。

## 解析参考

`ShoeboxRoomDecay` 对长宽高为 `L/W/H` 的闭合矩形房间计算：

```text
V = L W H
S = 2 (LW + LH + WH)
mean free path = 4V/S
RT60 = -6 ln(10) (4V/S) / (c ln(1-alpha))
alpha = 1 - exp[-6 ln(10) (4V/S) / (c RT60)]
```

这是 energy-domain Eyring reference。它与 `LateReverbEstimator` 的 per-hit
log-retention 公式采用同一物理量，但实现路径独立：一个从解析 `4V/S` 出发，另一个
从实际 DDA reflection legs 的均值出发。

房间尺寸来自 [AIR 论文](https://doi.org/10.1109/ICDSP.2009.5201259) 与
[RWTH 收录文档](https://publications.rwth-aachen.de/record/462454/files/462454.pdf)：

- booth：`3.00 × 1.80 × 2.20 m`；
- lecture：`10.80 × 10.90 × 3.15 m`。

D071 报告中的真实三带 room-mean RT60 反演成**房间平均有效吸收**，不是 glass、
concrete、parquet 或 Minecraft stone 的单材料系数：

| 房间 | low | mid | high |
|---|---:|---:|---:|
| booth | 0.3766 | 0.3542 | 0.4254 |
| lecture | 0.1708 | 0.1629 | 0.2072 |

## 一米体素匹配

真实尺寸分别量化为 Minecraft 尺度的 `3×2×2` 与 `11×11×3` interior cells。
所有六个表面使用上表同一 room-average absorption，并设置完全 diffuse
scattering，仅用于验证 mean-free-path/decay，不映射任何方块名称。

```powershell
.\gradlew.bat --no-daemon \
  :computational-acoustics-core:airShoeboxVoxelReference
.\gradlew.bat --no-daemon verifyAirShoeboxVoxelReference
```

输出 `build/research/air-shoebox-voxel-reference-v1.json`，SHA-256：

```text
4de568d12ac116ac2f58197fe9125ca188ad5e2be3a4201c942e32e55953bb43
```

| 房间 | analytic MFP | voxel analytic MFP | probe MFP | 最大三带 RT60 误差 |
|---|---:|---:|---:|---:|
| booth | 1.489 m | 1.500 m | 1.508 m | 1.32% |
| lecture | 3.986 m | 3.882 m | 3.985 m | 0.006% |

固定门禁：

- probe MFP relative error `<=10%`：通过，maximum `2.66%`；
- probe RT60 relative error `<=10%`：通过，maximum `1.32%`；
- no escape/no truncation：通过；
- analytic Eyring 正反演：单元测试在 `1e-12` 内互逆；
- `minecraft_release_calibrated=false`：保持。

独立 Python verifier 重新计算 D071 AIR report SHA-256，要求 Java report 的
`source_air_report_sha256` 完全相同，并逐房间/逐频带核对 measured RT60。只更新
哈希但不更新数值、只更新数值但不更新哈希、放宽 gate 或提升 release flag 都会
失败。

## 当前 stone 假设的定位

`AcousticMaterials.STONE.surfaceAbsorption = 0.03/0.05/0.08`。在完全相同的
AIR 几何下，它的解析 RT60 为：

| 房间 | stone low/mid/high | 相对真实三带倍数 |
|---|---:|---:|
| booth | 1.969 / 1.169 / 0.719 s | 15.52× / 8.52× / 6.65× |
| lecture | 5.271 / 3.130 / 1.925 s | 6.15× / 3.47× / 2.78× |

因此，本轮证据支持：

- `LateReverbEstimator` 的 log-retention 公式在 uniform diffuse shoebox 中正确；
- 256×12 reflection probe 的一米体素/采样误差低于当前 10% diagnostic gate；
- 当前 stone 数秒级衰减主要来自低吸收 `[H]`，不是 CUDA/CPU DDA traversal；
- 但 AIR lecture 是 glass/concrete/parquet/ceiling 的房间平均响应，不能把
  `0.17/0.16/0.21` 写成 Minecraft stone。

## 下一步

1. 为 Minecraft stone、wood、glass、wool 建立可复现的材料/小房间 RIR 测量；
2. 保存 source/receiver geometry、空房背景、温湿度、sweep/inverse filter；
3. 同一几何构造 Minecraft acoustic lab；
4. 先拟合 room-average absorption，再用多种表面组合解材料系数；
5. 保留独立房间 holdout，逐场景检查 RT60/EDT `<=15%`；
6. 没有 published/measured DRR truth 前，DRR `<=3 dB` 仍不关闭。

D073 已完成第 1 轮公开数据候选和异质表面 diagnostic。PTB dense-stone 中位数
与当前 stone 接近，但 stone/wool/wood/glass mixed voxel room 相对 mean-log
reference 最大仍差 `17.77%`，因此材料写回继续被阻止；见
[`decision-D073-ptb-material-absorption-and-mixture.md`](decision-D073-ptb-material-absorption-and-mixture.md)。

## Claim boundary

本决策证明解析衰减与 voxel probe 在两个 uniform-effective shoebox 上一致。它不证明
AIR 材料等于 Minecraft 方块，不证明 cave RT60，也不产生可发布 block material
table。当前 feature 继续默认关闭。
