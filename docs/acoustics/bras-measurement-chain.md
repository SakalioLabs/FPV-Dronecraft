# BRAS RS5 测量链、方向性与全矩阵门控

更新日期：2026-07-24

## 结论

BRAS RS5 的 16 条 RIR 可以可靠用于几何、路径分类和到达时延诊断，但当前不能作为
single-edge/double-edge 频谱模型的绝对 golden。原因不是数据质量差，而是项目先前的
1 ms 单频门控没有完整复现官方测量链：

- 官方 RIR 由 swept sine 和 spectral division 得到，但数据包没有发布原始 sweep
  和 inverse filter，无法重新反卷积；
- Genelec 8020c 的实测方向性属于独立约 3.32 GB 数据包，场景 ZIP 中没有；
- 1 ms 门的频率分辨率约为 1 kHz，不能在 1 kHz 附近给出稳定的三分之一倍频程结果；
- 延长到 4 ms 会开始混入硬地板和屏障联合路径；
- 绝对校准的五条 direct RIR 在 1 ms 门中仍有明显离散，说明短门频谱强烈依赖
  packet alignment、系统脉冲形状和场景路径。

因此，BRAS 当前承担三个角色：

1. **几何 golden**：坐标、路径类别和最短传播长度；
2. **到达诊断**：threshold 与 learned energy-template 两种方法交叉验证；
3. **端到端频谱 diagnostic**：方向性校正后揭示模型/测量链差异，但不决定产品算法。

## 官方测量链证据

来源：

- [BRAS 官方数据库说明](https://doi.org/10.14279/depositonce-6726.3)；
- Brinkmann et al., *A benchmark for room acoustical simulation. Concept and
  database*, Applied Acoustics 176 (2021), DOI
  [`10.1016/j.apacoust.2020.107867`](https://doi.org/10.1016/j.apacoust.2020.107867)。

官方说明给出的 RS5 条件：

- hemi-anechoic chamber，低频边界约 100 Hz；
- 25 mm MDF 屏，高约 2.07 m；
- 44.1 kHz，16 条单声道 RIR，每条 11,025 samples；
- Genelec 8020c 声源；
- G.R.A.S. 40AF free-field microphone，数据库中按 omnidirectional 处理；
- RIR 使用 fully calibrated measurement chain、swept sine 和
  spectral-division deconvolution；
- 单声道 RIR 单位为 Pascal，输出链在扬声器正前方 2 m、1 kHz 校准为 80 dB SPL。

这说明仿真不能只比较一个无方向性的 edge transfer。至少需要：

```text
source absolute response
× measured source directivity
× free-field distance
× diffraction/transmission
× hard-floor and fixture paths
× receiver response convention
```

## 只下载需要的 29.6 MB 方向性数据

官方 Genelec ZIP 约 3.32 GB，其中：

- third-octave MPS MAT：29,579,140 bytes；
- full IR MAT：约 2.03 GB compressed；
- full IR CSV：约 1.21 GB compressed。

脚本
[`fetch_bras_genelec_mps.py`](../scripts/fetch_bras_genelec_mps.py)
通过 HTTP Range 读取 ZIP central directory，只下载 MPS MAT local entry：

```powershell
python docs/scripts/fetch_bras_genelec_mps.py `
  build/research-bras/Genelec8020_MPS_front_pole.mat
```

验证：

```text
SHA-256
b368fa00951ac92f942ef13244d4f1039e8310f4931baa2765085d8a22cdd45d

shape
MPS:       31 × 64442 complex128
Phi:       1 × 64442
Theta:     1 × 64442
Frequency: 31 × 1, 20 Hz to 20 kHz
```

原始 MAT、ZIP byte ranges 和临时文件继续位于被忽略的 `build/research-bras/`，
不进入仓库。

## LS1-MP1 方向性校正

LS1 为水平正对 `+x`，到屏幕顶边的出射 elevation 约 15.5°；LS3-MP4
可见参考为 on-axis。使用官方 MPS 对测得的 shadow/visible ratio 去除声源方向性：

| 频率 | 原始测量 | Genelec 方向性 | 传播校正后 |
|---:|---:|---:|---:|
| 1 kHz | -19.377 dB | -0.474 dB | -18.904 dB |
| 2 kHz | -18.387 dB | -0.409 dB | -17.978 dB |
| 4 kHz | -23.859 dB | -1.266 dB | -22.593 dB |
| 8 kHz | -25.245 dB | -1.267 dB | -23.977 dB |
| 12 kHz | -24.981 dB | -0.515 dB | -24.466 dB |

模型 RMS：

| 模型 | 未校正 | 方向性校正后 |
|---|---:|---:|
| ideal single-edge UDFA | 5.829 dB | 5.077 dB |
| 2024 double-edge UDFA | 10.284 dB | 11.020 dB |

方向性解释约 0.75 dB RMS 改善，但没有消除主要差异；双边模型仍明显过度衰减。
这进一步支持“不为 LS1-MP1 单点拟合”的决定。

## 全 16 路径到达诊断

`analyze_bras_rs5.py` 从五条 direct RIR 学习双侧、归一化的能量 packet，
在几何预测附近做 normalized correlation。它保留 spectral-division
pre-ringing 的形状信息，但因为原始 sweep/inverse filter 不存在，明确不称为
“重新反卷积”。

| 子集 | threshold RMS / max | matched RMS / max |
|---|---:|---:|
| 全 16 路径 | 0.806 / 2.712 ms | 0.391 / 0.992 ms |
| 标准高度 9 路径 | 0.201 / 0.385 ms | 0.186 / 0.487 ms |

matched template 显著降低近地板异常的总体影响，但在标准高度最大误差上没有单调
改善。因此两种 landmark 都保留；matched 结果是诊断与不确定度估计，不能替换
几何传播时间。

## MDF 传输与硬地板边界

脚本
[`fetch_bras_rs5_materials.py`](../scripts/fetch_bras_rs5_materials.py)
从官方约 250 MB surface archive 只 range-fetch 约 390 kB：

```powershell
python docs/scripts/fetch_bras_rs5_materials.py `
  build/research-bras/rs5-materials
```

官方 `mat_MDF25mmB_plane` 参数：

- density：`742.4 kg/m³`；
- thickness：`0.025 m`；
- surface mass：`18.56 kg/m²`；
- absorption：约 0.03-0.05；
- impedance 未提供；官方说明允许由 density 近似 sound transmission。

`LimpMassPanelTransmission` 实现无限 limp sheet 的 normal-incidence reference：

```text
|p_t / p_i| = 1 / sqrt(1 + (π f m'' / ρ c)^2)
```

| 频率 | pressure transmission |
|---:|---:|
| 1 kHz | -42.996 dB |
| 2 kHz | -49.017 dB |
| 4 kHz | -55.037 dB |
| 8 kHz | -61.058 dB |
| 12 kHz | -64.580 dB |

在 LS1-MP1 上，校正后的绕射实测为约 -18 至 -24 dB。即使传输与绕射完全同相，
1 kHz 的 MDF 直穿声最多改变约 0.55 dB，高频更小；它不能解释 5 dB 级模型差异。
该结论只适用于 mass-law reference，不声称捕获 panel resonances、边框泄漏或
coincidence effect。

官方 `mat_Tiles` 在 1-12 kHz 的 absorption 约 `0.02-0.03`，可以近似为接近刚性
反射面。LS1-MP1 最短“地板一次反射 + 双边绕射”相对双边直达路径晚
**3.929 ms**。因此：

- 1 ms gate 在时间上排除了第一条硬地板联合路径；
- 4 ms sensitivity gate 正好开始包含该路径，不能视为纯绕射；
- 完整 RIR reference 必须相干加入地板路径，不能只延长 gate。

## 为什么全矩阵 1 ms 频谱没有通过 golden gate

脚本按官方绝对校准设计了全矩阵诊断：

1. direct RIR 使用高 SNR threshold gate；
2. shadow 路径使用几何预测 gate，避免近地板 pre-ringing 提前触发；
3. 每条路径按最短路径长度归一化；
4. 使用 Genelec MPS 去除第一段出射方向性；
5. 五条 direct 路径的中位数估计公共 source spectrum。

如果该方法可作为绝对 golden，五条 direct 路径归一化后应接近 0 dB。实际离散为：

| 频率 | direct RMS | direct max abs |
|---:|---:|---:|
| 1 kHz | 6.368 dB | 12.954 dB |
| 2 kHz | 3.349 dB | 7.110 dB |
| 4 kHz | 1.190 dB | 1.749 dB |
| 8 kHz | 3.407 dB | 5.868 dB |
| 12 kHz | 2.730 dB | 4.988 dB |

只有 4 kHz 暂时满足 `≤3 dB` 的候选门槛；其余频带连 direct calibration
control 都失败。因此不能用同一流程宣称 shadow 模型误差达到论文级精度。

## 相干整段 RIR 路径预算

`analyze_bras_rs5.py` 还构造了一个不拟合参数的完整复压力预算：

1. 2024 double-edge UDFA 主路径；
2. floor-before-diffraction 镜像路径；
3. diffraction-before-floor 镜像路径；
4. normal-incidence limp-sheet transmission；
5. 每条路径使用独立传播距离、相位、Genelec complex directivity；
6. tile specular pressure 使用 `sqrt((1-absorption)(1-scattering))`。

Java 的 `DoubleEdgeGeometry.squareBarrierInPlane` 现在可从任意 source/receiver
高度构造主路径和镜像路径；对称 floor-before/floor-after 几何通过距离、互易幅度和
`3.928846 ms` excess-delay 测试。

| 频率 | 完整实测 | 主双边 | 主双边 + 两条 floor | 再加 MDF transmission |
|---:|---:|---:|---:|---:|
| 1 kHz | -11.846 dB | -20.883 dB | -16.171 dB | -16.542 dB |
| 2 kHz | -15.727 dB | -26.192 dB | -24.019 dB | -23.822 dB |
| 4 kHz | -22.386 dB | -32.888 dB | -35.952 dB | -36.999 dB |
| 8 kHz | -29.220 dB | -38.865 dB | -40.810 dB | -41.570 dB |
| 12 kHz | -26.673 dB | -41.625 dB | -41.180 dB | -41.261 dB |

五点 RMS：

| 路径预算 | RMS error |
|---|---:|
| 主双边 | 11.118 dB |
| 主双边 + floor | 11.103 dB |
| 主双边 + floor + transmission | 11.545 dB |

floor 在 1-2 kHz 相干增强、4-8 kHz 相干抵消，但总 RMS 几乎不变。Mass-law
transmission 也没有改善结果。整段 RIR 还含 chamber/fixture residual，而当前
double-edge 模型在高频明显过衰减；这两项不能通过增加更多未验证路径来互相抵消。

此外，mass-law transmission 应比双边路径早约 `0.729 ms`，而官方
spectral-division system packet 约 2 ms。两个 packet 在时间上重叠，且缺少原始
sweep/inverse filter，因此无法从发布 RIR 中可靠反演 transmission。它继续只作为
物理上界，不进入算法拟合。

## 可复现命令

```powershell
python docs/scripts/analyze_bras_rs5.py `
  "build/research-bras/rs5-extracted/1 Scene descriptions/RS5 diffraction (infinite wedge)" `
  --archive build/research-bras/rs5.zip `
  --genelec-mps build/research-bras/Genelec8020_MPS_front_pole.mat `
  --json
```

不提供 `--genelec-mps` 时，脚本仍保持纯 Python 标准库路径并显式报告未应用方向性。
提供 MAT 时才延迟导入 SciPy。

## 下一步

1. 不再尝试从 1 ms gate 强行生成 1 kHz third-octave golden；
2. 完整 source directivity + hard floor + screen transmission 路径预算已完成，
   但 control failure 证明它只能作 diagnostic；
3. 寻找或生成 25 mm two-edge wave/BTMS reference，在无 chamber residual 的
   条件下先验证模型本身；
4. 项目自采 RIR 时保存 raw sweep、inverse filter、reference loopback、设备朝向、
   温湿度和无障碍 free-field calibration；
5. 自采场景同时提供硬地板与吸声地板版本，以分离 floor interaction；
6. 只有 direct calibration control 在目标频带先达到 `≤3 dB RMS / ≤6 dB max`，
   才允许用 shadow 路径选择产品绕射算法。

独立 reference 的具体数值方法、收敛条件与 Agent 执行顺序见
[`two-edge-independent-reference.md`](two-edge-independent-reference.md)。2024 论文的模拟
数值目前只声明“可向作者索取”，不能用曲线描图替代可复现数据。
