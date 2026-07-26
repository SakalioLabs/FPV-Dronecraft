# NEAPTIDE 三频带桨盘指向性验证

日期：2026-07-24
状态：五机型 transfer test 完成；产品默认值禁用

## 1. 结论

NEAPTIDE 的 DJI Mavic 2 Enterprise hover 实录证明，当前程序化声源不能使用一个
标量音量表达桨盘指向性：

- 整体与 `20–300 Hz` 低频在桨盘平面最强，轴向分别约低 `12.94 dB` 和
  `15.41 dB`；
- `300–3200 Hz` 中频在轴向约高 `5.73 dB`；
- `3.2–20 kHz` 高频在轴向约高 `4.85 dB`，个别近轴测点达到约 `8.86 dB`；
- 对 `D(mu)=c2*mu²+c4*mu⁴` 的偶对称模型，四个指标的留一法 RMSE 为
  `1.75–2.29 dB`，可作为低阶 runtime 模型结构的证据。

这里 `mu=|rotorDiskNormal·sourceToListener|`。论文图中的角度是相对水平面的
elevation；对水平 hover 桨盘应使用 `mu=|sin(elevation)|`。Mic 1 的 `90°` 是轴向，
Mic 6/7 的 `0°/-3.5°` 才是桨盘平面附近。把这些角度直接当作与法向的夹角会得到
相反结论。

这些系数**不得成为产品默认值**：数据许可证是 CC BY-NC 4.0，机型不是 5 英寸
FPV，且七个 elevation 不能证明完整 azimuthal directivity。

## 2. 权威输入与许可边界

来源：[NEAPTIDE Dataset, Zenodo record 10512044](https://zenodo.org/records/10512044)。
方法 PDF 说明：

- 七支 Behringer ECM-8000 同步采集；
- 每组测量前后使用 Brüel & Kjær 4230 的 `94 dB / 1 kHz` 校准；
- hover 文件为 10 s；
- 文件已做麦克风校准、1 m 距离归一化和地面麦克风 `-3 dB` 修正；
- PCM 保存值是以 `µPa` 表示的压力再乘 `20`，所以
  `20,000,000 PCM units = 1 Pa`；
- 数据许可为 CC BY-NC 4.0，只用于研究验证，不随 mod 分发。

本次只下载：

| 文件 | 大小 | SHA-256 |
|---|---:|---|
| `NEAPTIDE.pdf` | 4,412,505 bytes | `18509a4f06878c1f8e2c7396cb3412c0d78c3d069d727c6e180ed0c07a335b9d` |
| `SegmentedAudio_Mavic.zip` | 33,521,822 bytes | `e3080c558e754af03bea6b7b6aa0a29e23a7fe4d118a441674443ff58cf90cd8` |
| `SegmentedAudio_HolybroS500.zip` | 34,866,761 bytes | `f808d67e25c49994af6759f36b12f0045ba14632ae00c5c90cc14974d2723a63` |
| `SegmentedAudio_Matrice.zip` | 34,239,969 bytes | `a7d656d43f26c81365f492a3a742ce4f9814b48ebe3771ec94894b054d31d3e7` |
| `SegmentedAudio_TarotX6_B1.zip` | 34,373,930 bytes | `03643c66900bf7b688d0a2869fadb0fa8bcf49cb1e4c9ed128f62d6427833f1e` |
| `SegmentedAudio_TarotX6_B2.zip` | 34,226,343 bytes | `ed3973c99f7604ecb5d30c7dbde5660524194e74077418294b477a09b03521fe` |
| `Calibration.wav` | 619,564 bytes | `6719c58e365c57f6462163d361d18ccce2a3694c01148b61855987c4563ccd8a` |

输入保存在忽略的 `tmp/`，输出保存在忽略的 `build/research/`；仓库只保存分析代码、
测试和可核查的数值摘要。

## 3. 阵列几何

七个 elevation 为：

```text
Mic:        1      2      3      4      5      6      7
elevation: 90.0   71.6   56.4   45.0   26.6    0.0   -3.5 degrees
axis mu:    1.0   0.949  0.833  0.707  0.448   0.0    0.061
```

以 Mic 6/7 的线性能量平均作为 `0 dB` 桨盘平面 reference。这两个通道的整体声级
只差 `0.018 dB`，足以作为本次内部一致性检查，但不代表上下半球已被完整测量。

## 4. 信号处理

`analyze_neaptide_directivity.py` 执行：

1. 严格读取 mono、44.1 kHz、32-bit PCM；
2. 按论文约定换算 Pa，并验证校准 WAV 为 `94.000 dB SPL`，容差 `0.25 dB`；
3. 整体声级使用去直流后的时域均方；
4. 三频带使用 8192-sample Hann Welch、50% overlap、PSD 积分：
   `20–300`、`300–3200`、`3200–20000 Hz`；
5. 各频带相对 Mic 6/7 能量平均归一化；
6. 最小二乘拟合
   `D(mu)=c2*mu²+c4*mu⁴`，并计算 leave-one-angle-out RMSE；
7. 内部门禁为 fit RMSE `<=2.5 dB` 且 LOO RMSE `<=3.0 dB`。

复现：

```powershell
python docs/scripts/analyze_neaptide_directivity.py `
  --archive tmp/neaptide/SegmentedAudio_Mavic.zip `
  --calibration tmp/neaptide/Calibration.wav `
  --output build/research/neaptide-mavic-hover-directivity.json
```

当前 JSON SHA-256：

```text
f715f0a78b3e3a18924055b44896ad923b3a4ea21de8cf9bf1c6725fa908c811
```

相同绝对输入路径下重复运行得到相同字节。

## 5. 测量结果

相对桨盘平面 reference 的声级：

| Mic | elevation | axis `mu` | overall | low | mid | high |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 90.0° | 1.000 | -12.94 | -15.41 | +5.73 | +4.85 |
| 2 | 71.6° | 0.949 | -12.26 | -13.19 | +3.14 | +8.86 |
| 3 | 56.4° | 0.833 | -15.40 | -14.91 | -0.55 | +5.44 |
| 4 | 45.0° | 0.707 | -11.86 | -9.71 | +0.43 | +6.83 |
| 5 | 26.6° | 0.448 | -3.60 | -1.53 | +2.38 | +6.25 |
| 6 | 0.0° | 0.000 | -0.01 | +0.24 | +0.36 | +0.57 |
| 7 | -3.5° | 0.061 | +0.01 | -0.26 | -0.39 | -0.65 |

单位均为 dB。平面 reference 的 1 m 绝对声级为：

| metric | dB SPL at 1 m |
|---|---:|
| overall | 98.545 |
| low | 93.994 |
| mid | 77.878 |
| high | 71.732 |

绝对值只描述该数据集和工况，不应复制为 FPV profile 的响度。

## 6. 低阶拟合

模型：

```text
D_band(mu) = c2_band * mu^2 + c4_band * mu^4  [dB]
mu = abs(rotorDiskNormal dot sourceToListener)
D_band(0) = 0 dB
```

| metric | `c2` dB | `c4` dB | axis dB | fit RMSE | LOO RMSE | internal gate |
|---|---:|---:|---:|---:|---:|---|
| overall | -33.732 | +20.848 | -12.885 | 1.256 | 1.752 | pass |
| low | -23.054 | +7.618 | -15.436 | 1.533 | 2.137 | pass |
| mid | -3.377 | +8.005 | +4.628 | 1.379 | 2.146 | pass |
| high | +23.281 | -17.683 | +5.598 | 1.545 | 2.293 | pass |

内部 gate 只说明“两项偶多项式能压缩这七个观测点”，不说明跨机型泛化。

## 7. 五机型 transfer test

相同分析随后覆盖 DJI Matrice 300 RTK、Holybro S500 和 Tarot X6 两种桨。Mic 1
轴向相对 Mic 6/7 平面的实测结果：

| aircraft/profile | overall | low | mid | high |
|---|---:|---:|---:|---:|
| DJI Matrice 300 RTK | +16.05 | +17.22 | +9.43 | +3.77 |
| DJI Mavic 2 Enterprise | -12.94 | -15.41 | +5.73 | +4.85 |
| Holybro S500 | -6.30 | -8.03 | +8.27 | +6.82 |
| Tarot X6 Blade 1 | +1.23 | -6.30 | +15.38 | +8.58 |
| Tarot X6 Blade 2 | +6.60 | -1.88 | +13.81 | +10.13 |

单位为 dB。overall/low 不但幅度不同，Matrice 与 Mavic/Holybro 的符号也相反；同一
Tarot X6 换桨就改变了 overall/low，说明 directivity 必须属于
airframe+rotor+propeller profile，而不是全局“无人机类型”参数。

`compare_neaptide_directivity.py` 把四次 leave-one-aircraft-out 扩展为五次：每次用
其余四机型的全部 28 个角度点拟合一个共享 `mu²+mu⁴` 模型，再对未见机型的 7 点
计算 RMSE。

| metric | pooled fit RMSE | mean LOAO RMSE | max LOAO RMSE | `<=3 dB` transfer |
|---|---:|---:|---:|---|
| overall | 7.341 | 8.120 | 12.916 | fail |
| low | 7.423 | 7.913 | 15.703 | fail |
| mid | 2.958 | 3.246 | 5.057 | fail |
| high | 1.816 | 1.920 | 2.154 | pass |

聚合命令：

```powershell
python docs/scripts/compare_neaptide_directivity.py `
  --input build/research/neaptide-mavic-hover-directivity.json `
  --input build/research/neaptide-holybro-s500-hover-directivity.json `
  --input build/research/neaptide-matrice-hover-directivity.json `
  --input build/research/neaptide-tarot-x6-b1-hover-directivity.json `
  --input build/research/neaptide-tarot-x6-b2-hover-directivity.json `
  --output build/research/neaptide-five-aircraft-directivity-transfer.json
```

聚合 JSON SHA-256：

```text
a1c37cea02e45c5c58a3f69866b7cd549edd357d9319edbf61caf91eaab6d7be
```

调换五个输入文件的 CLI 顺序后字节不变。只有 high band 通过不允许启用一个
high-only 产品默认：它仍来自 CC BY-NC 数据，且没有 5 英寸 FPV holdout。

## 8. 对实现的约束

core 的 `AxisymmetricSourceDirectivity` 已实现以下 profile API 结构：

- 把 directivity 作为 profile 参数，不把本页系数硬编码进默认 profile；
- 对 broadband 在能量域分别应用 low/mid/high 的 amplitude gain 平方；
- 对 tone 按实际频率选择或插值 directivity band，再乘 linear amplitude；
- 以 `abs(dot)` 保持上下偶对称，直到有可信上下半球数据；
- 在 source directivity 后再应用 propagation transmission，避免把两者混为同一个
  occlusion gain；
- 对 dB gain 设 profile-defined 有界范围，拒绝 NaN、非单位法向和零长度方向；
- 通过完全 omnidirectional profile 保留显式 fallback，但不得把 fallback 宣称为
  已完成的物理指向性。

该类没有内置 NEAPTIDE 系数，也尚未接入 Fabric `DroneAcousticRenderState`。
`Parameters.omnidirectional()` 只用于显式 fallback/测试；在合规 FPV profile
关闭产品 gate 前，把它接线不会产生物理指向性，不应伪装成该功能已交付。

产品默认值重新开放至少需要：

1. 获得许可证允许分发的 5 英寸三叶 FPV 数据，至少覆盖平面、45°、轴向及上下两侧；
2. 为每个 airframe+rotor+propeller profile 单独拟合，不采用五机型 pooled
   overall/low/mid；
3. 使用未参与拟合的角度/工况达到 `<=3 dB` directivity holdout；
4. 在 Minecraft 中转动机体，确认 source directivity 变化不与 OpenAL 距离衰减或
   DDA transmission 重复计算；
5. A/B 听测确认频带变化优于单标量 gain 和 omnidirectional fallback。
