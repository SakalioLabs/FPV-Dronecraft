# D073 — PTB 实测材料候选与异质表面误差门

日期：2026-07-25  
状态：**official corpus pinned；four research candidates + holdout implemented；
heterogeneous voxel gate failed；release calibration remains open**

## 数据准入

采用德国国家计量院 PTB 的
[Room Acoustics Absorption Coefficient Database](https://www.ptb.de/cms/en/ptb/fachabteilungen/abt1/fb-16/ag-163/absorption-coefficient-database.html)。
官方页面说明数据库包含 2000 多组数据、以倍频程为主，并允许下载和使用；同时
PTB 已停止该方向研究，不再提供支持。工作簿自身进一步声明，数据来自厂商资料或
互联网，PTB 不保证其正确性。因此它是**有权使用的大型实测/汇编研究语料**，不是
逐行都具有原始试验报告的 release truth。

权威输入固定为 ZIP 本身及其内部原始 XLS 字节：

| 对象 | bytes | SHA-256 |
|---|---:|---|
| `ptb-abstab-wf.zip` | 565,539 | `d40814d54b90ed6cd22bb4a7584cef082b758ec01faa6cabf05493da06fe92fe` |
| ZIP entry `abstab_wf.xls` | 2,263,040 | `1d6dab85024bfea8126cd2b0543ece6072c507004d8ac89a44fd82fa8dc75ff1` |

Excel 打开/转换旧 XLS 时会重写 workbook cache；本机转换后的 XLSX/CSV 只作为提取
缓存，绝不取代 ZIP-entry hash。`fetch_ptb_material_absorption.py` 同时验证 archive
大小、archive hash、唯一 entry 名称和 entry hash。

原表共 `2574` 个编号行：

- `2517` 行有完整 125/250/500/1000/2000/4000 Hz 数值；
- `518` 行至少一个系数大于 1；
- `341` 行填写 `primary reference`；
- `441` 行标记了 diffuse-field measurement。

吸声系数大于 1 可出现在混响室“实用吸声系数”中，但不能直接进入当前
`AcousticMaterial` 的能量保留率。处理器禁止静默 clamp；这 518 行留在 corpus
audit 中，但不进入本轮候选。

## 可审计选择与 holdout

`ptb-material-selection-v1.json` 固定每一行的 PTB row id、描述、材料代码、六个
倍频程值和 train/holdout。候选是逐倍频程 training median：

| 类别 | train / holdout | 125 / 250 / 500 / 1k / 2k / 4k |
|---|---:|---|
| dense stone | 5 / 4 | .02 / .03 / .03 / .03 / .04 / .07 |
| solid/panel wood | 3 / 3 | .15 / .11 / .10 / .08 / .10 / .10 |
| window glass | 2 / 3 | .265 / .155 / .11 / .075 / .045 / .03 |
| porous mineral wool | 5 / 2 | .15 / .45 / .60 / .70 / .85 / .85 |

留出集 mean six-octave RMSE 分别为 `0.0224 / 0.0547 / 0.0847 / 0.2195`。
wool 和 glass 对厚度、空腔、安装方式很敏感，较大的 holdout spread 是阻止直接
发布的证据，不是应被平均掉的“噪声”。

## 六倍频程到三频带

运行时仍使用 `125–700 / 700–4000 / 4000–20000 Hz`。新
`OctaveBandAbsorptionReducer` 要求调用者显式提供六个 source-energy weights：

```text
alpha_runtime = sum(E_i alpha_i) / sum(E_i)
```

4 kHz datum 归入 half-open high band。当前报告使用明确标记的
`flat-energy-per-octave-diagnostic = 1,1,1,1,1,1`，只为比较旧参数；目标 5-inch
实测 profile 尚未产生，所以不能称为 drone-weighted calibration。

| 类别 | PTB diagnostic low/mid/high | 当前 `[H]` |
|---|---:|---:|
| stone | .0267 / .035 / .070 | .03 / .05 / .08 |
| wood | .120 / .090 / .100 | .12 / .22 / .35 |
| glass | .1767 / .060 / .030 | .05 / .08 / .12 |
| wool | .400 / .775 / .850 | `.SOFT` .25 / .55 / .78 |

结论不是“stone 应改成 .0267/.035/.07”。更重要的是：当前 stone 已接近 dense
stone 汇编中位数，D072 的 AIR lecture 差异来自**整个房间的表面组合**；wood
mid/high 与 glass low 的当前假设偏差更值得优先验证。

## 异质表面 voxel lab

11×11×3 closed room 使用：

- floor = stone；
- ceiling = wool/soft；
- 两面 x wall = wood；
- 两面 z wall = glass。

旧参数和 PTB 候选均保持当前 scattering `[H]`，因此本实验只隔离 absorption。
对每组同时计算面积算术平均 Eyring、面积平均 log-retention Eyring 和 256 rays ×
12 bounces voxel probe。

| 参数组 | probe vs arithmetic 最大误差 | probe vs mean-log 最大误差 |
|---|---:|---:|
| 当前 `[H]` | 37.55% | 14.23% |
| PTB candidates | 51.93% | 17.77% |

probe 在三个频带都更接近 mean-log，但仍未达到 10%。这说明 D072 的 uniform
surface 1.32% 结果不能外推到异质房间。当前 probe 对 surface-hit distribution、
路径能量统计和边/角体素材料归属存在耦合；在解决该问题前，不允许把 PTB 候选写回
`AcousticMaterials`。

## 可执行命令与证据

```powershell
.\gradlew.bat --no-daemon verifyPtbMaterialMixtureReference
```

生成：

- `ptb-material-absorption-v1.json`：
  `8e506562d92ea131af8ee402736c9ce498a7a3894acb883fa78637a1620d0bb9`；
- `ptb-material-mixture-v1.json`：
  `5e215c7c3bbc6fb1a471f2754834d8f7b7942b0d8d9c5fe03496d670da0b7ecf`；
- `ptb-material-mixture-verification-v1.json`：
  `bcf554bf569ec874efd3001562e61d4fa91370e23af805b84d77664ac7e85ca6`。

verifier 要求 Python material report 与 Java mixture report 绑定同一个 manifest
SHA，并逐类别/逐频带核对候选和旧值。固定门禁：

- raw rows match manifest：通过；
- candidate probe 更接近 mean-log：通过；
- candidate probe 相对 mean-log `<=10%`：**失败，17.77%**；
- `minecraft_release_calibrated=false`：保持。

## 下一步研究方向

1. 把 reflection statistics 从单一 `mean(log retention)` 扩展为逐 ray/逐时间窗
   energy decay，比较 `log(mean energy)` 与 `mean(log energy)`；
2. 提高 ray/bounce 数做收敛矩阵，分离 256×12 预算误差与公式误差；
3. 对面法线记录 material hit histogram，与几何理论 surface-area probability
   比较，单独量化 edge/corner voxel assignment；
4. 用目标穿越机实测六倍频程能量替换 flat diagnostic weights；
5. 建造真实 matched small-room RIR：分别改变 stone/wood/glass/wool 面积，保留
   一个完全未参与拟合的房间；
6. 只有异质 room RT60/EDT holdout `<=15%` 且 P99 预算通过，才建立新的 material
   schema/version，并同步 Java/native parity corpus。

D074 已完成前 3 项误差分解。修正漫射采样相关性后，configured 48-bounce ray
sweep 已在 `1.735%` 内收敛，
normal/material mismatch 为零；只把 scattering 强制为完全漫射，就把最大公式误差
从 `25.02%` 降到 `0.38%`。因此后续改为分离 early material BRDF 与 late diffuse
transport；详见
[`decision-D074-late-field-scattering-control.md`](decision-D074-late-field-scattering-control.md)。

## Claim boundary

本决策提供有来源、可复算、带 holdout 的材料候选，并证明当前异质表面估计器尚未
通过。它不证明 PTB 建材等于 Minecraft 方块，不标定 transmission loss/scattering，
也不启用 listener reverb。
