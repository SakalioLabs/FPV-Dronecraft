# D071 — 真实 RIR 先验证独立分析链，不能直接拟合 Minecraft 材料

日期：2026-07-25  
状态：**measured reference + deterministic analyzer implemented；Minecraft
release calibration remains closed**

## 决策

WP7 的第一套真实房间脉冲响应采用 RWTH Aachen Impulse Response Database
（AIR）v1.4：

- [官方数据库页](https://www.iks.rwth-aachen.de/en/research/tools-downloads/databases/aachen-impulse-response-database/)；
- [论文 DOI](https://doi.org/10.1109/ICDSP.2009.5201259)；
- 真实测量的 booth、office、meeting、lecture room BRIR；
- 48 kHz double-precision MAT；
- 数据包内含 MIT license；
- 论文 Table 2 给出 17 个 source/receiver distance 的 full-band RT60，且说明
  数值由左右通道平均、保留声波传播延迟。

RAF 是更现代、密度更高的空间 RIR 数据，但完整 RIR 约 21.6 GB 且许可为
CC BY-NC 4.0；SoundSpaces 的主要 RIR 是几何声学仿真。两者继续保留为研究对照，
不替代当前“真实测量 + 商业兼容 + 小体量”的 AIR 门禁。

## 可重复数据契约

```powershell
.\gradlew.bat --no-daemon fetchAirRirReference
```

固定官方归档：

| 字段 | 值 |
|---|---|
| 文件 | `air_database_release_1_4.zip` |
| bytes | `202650373` |
| SHA-256 | `d2fd52767505c402e8aed9299dcd046493c8254cfab2f769fcf480dc7c616a5f` |
| 内嵌 license | `AIR_1_4/license.txt` |
| license SHA-256 | `576e14bfd0215f50085d9980f8586f2cd3e69b0db43cbf1cb12aa49807406347` |
| license | MIT |

下载器先检查 bytes、整个 ZIP SHA-256、内嵌 license SHA-256 与 `MIT License`
标识，再以原子替换完成写入。原始 193 MiB 数据位于 gitignored
`external-data/computational-acoustics/`，不会打包进 mod。

## 独立分析方法

```powershell
.\gradlew.bat --no-daemon analyzeAirRirReference
```

`analyze_air_rir_reference.py` 直接从 ZIP 读取 17 positions × 2 channels：

1. full-band 和 Minecraft 三带都使用 Schroeder reverse integration；
2. T20 在 `-5..-25 dB` 做线性拟合；
3. 普通 decay time 使用 `-60/slope`；
4. 与论文 Table 2 比较时，按论文说明把拟合直线外推到原始 IR 时间轴的
   `-60 dB` crossing，保留 propagation delay；
5. EDT 使用 `0..-10 dB`；
6. DRR 使用项目诊断约定：direct arrival 前 1 ms 至后 2.5 ms 为 direct
   energy，其余为 reverberant energy。

第 6 项不是 AIR 论文发布的 DRR truth，绝不能用来宣称 `<=3 dB` 校准通过。
三带为 `125–700 / 700–4000 / 4000–20000 Hz`，用于审计当前 FDN 降维，不是
ISO 1/3-octave 替代物。

## 当前真实测量结果

输出 `build/research/air-rir-reference-v1.json`，本次 SHA-256：

```text
8c962c2ceca292475fb035057bfd4e3b6170eb698cd36902bb9c92bfa093948b
```

论文 full-band RT60 复现：

- 17 个位置，34 条真实通道；
- T20 拟合最低 R² `0.9394`；
- absolute relative error mean `11.10%`、median `11.56%`；
- 13/17 positions 在 `15%` 内；
- P95 `27.02%`、maximum `29.35%`；
- 所有 booth positions 与 office 1 m 未通过逐位置 `15%` gate。

| 房间 | 论文 mean RT60 | 独立分析 mean | 相对误差 | 三带 RT60 low/mid/high |
|---|---:|---:|---:|---:|
| booth | 0.123 s | 0.156 s | 26.10% | 0.127 / 0.137 / 0.108 s |
| meeting | 0.226 s | 0.252 s | 11.38% | 0.380 / 0.305 / 0.233 s |
| office | 0.430 s | 0.492 s | 14.41% | 0.743 / 0.426 / 0.277 s |
| lecture | 0.775 s | 0.784 s | 1.21% | 0.857 / 0.903 / 0.692 s |

真实房间排序稳定为 `booth < meeting < office < lecture`，而且 office/meeting
显示明显的低频慢衰减。这证明单一 broadband RT60 不足，也给当前三带 FDN 提供了
真实量级参考。短 booth 的系统性偏差同时证明：不能因为 aggregate median 通过
就宣布逐场景 gate 通过。

## 对 D070 的影响

当前 stone synthetic/Minecraft RT60 为数秒，远高于 AIR 普通房间
`0.1–0.9 s` 的 measured range。石洞可能确实比办公室更长，但现有数值尚无匹配
几何或材质实测支持。因此：

- 不修改现有 `[H]` 材料吸收常数来追逐 AIR；
- 不把 AIR room name 映射成 Minecraft stone/wool；
- `fpvdrone.listenerReverb` 继续默认 false；
- 下一步必须建立匹配几何的 fixed acoustic lab：实测/离线 reference 与
  Minecraft voxel scene 一一对应；
- 每个匹配场景必须分别通过 RT60/EDT `<=15%`，不能只用 aggregate median；
- DRR `<=3 dB` 需要发布 DRR truth 或项目自采校准测量。

## Claim boundary

AIR 关闭的是“真实 RIR 是否能被独立、确定性地读取和分析”这一门。它没有提供
Minecraft voxel/material 几何，没有 FPV 声源，也没有本项目 DRR 窗口的 published
truth。报告强制：

```text
minecraft_release_calibrated=false
```

因此本决策是 measured propagation reference，不是发布参数提升。

后续 D072 已用 booth/lecture 已知尺寸，把真实三带 decay 反演为 room-average
Eyring absorption，并在相同吸收的一米 voxel shoebox 中把 probe RT60 maximum
error 关闭到 `1.32%`。这把当前主要误差定位到材料假设，但仍不允许把 AIR 的
房间平均吸收写成 Minecraft block 参数。见
[`decision-D072-air-shoebox-voxel-error-decomposition.md`](decision-D072-air-shoebox-voxel-error-decomposition.md)。
