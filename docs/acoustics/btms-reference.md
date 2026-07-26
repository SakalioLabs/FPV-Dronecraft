# BTMS 无限楔精确数值参考

更新日期：2026-07-24

## 结论

项目现在拥有一个不依赖 UDFA 工具箱的 Biot–Tolstoy–Medwin–Svensson
（BTMS）无限楔频域参考。它直接数值计算 Calamia 论文 Eq. (3.4)、(3.8)、
(3.15)-(3.16)，用于区分“快速滤波近似误差”和“实测链路误差”，不进入实时音频线程。

在 BRAS RS5 的理想 `2π` knife-edge 几何上，2023 UDFA 与精确 BTMS 在
1/2/4/8/12 kHz 的 RMS 差异只有 **0.091 dB**。因此，UDFA 相对 1 ms
实测门控的 **5.829 dB** RMS 差异不能归因于 UDFA 对无限楔 BTMS 的近似。
更可能的来源是 25 mm 双边屏、短门控、扬声器/麦克风方向性、硬地板和反卷积
pre-ringing。BRAS RS5 仍是有价值的端到端测量 benchmark，但在完成测量链校准前，
不能作为 single-edge 与 double-edge 算法的绝对选择依据。

## 来源与实现边界

- 公式来源：V. A. Calamia, *Advances in Edge-Diffraction Modeling for
  Virtual-Acoustic Simulations*, Princeton University PhD thesis, 2009，
  [官方技术报告 PDF](https://www.cs.princeton.edu/techreports/2009/853.pdf)。
- 角度项采用 Eq. (3.4)，`ν = π/θw`。
- directivity kernel 采用 Eq. (3.8)：
  `β = sin(νφ) / (cosh(νη) - cos(νφ))`。
- 频域 pressure 采用 Eq. (3.15)，reference distance 采用 Eq. (3.16)。
- 实现只使用公开公式；没有下载、读取或复制 UDFA/BTMS toolbox 源码。
- 返回的 raw pressure 单位为 `1/m`；与 UDFA 比较时乘最短绕射路径长度，
  得到自由场距离归一化的无量纲幅度。

核心实现：

- `BtmsInfiniteWedgeGeometry`：径向距离、轴向分离、source/receiver azimuth、
  exterior wedge angle 与声速；
- `BtmsInfiniteWedgeReference`：复数 Gauss–Legendre 数值积分和
  `DiffractionFilterModel` 适配器；
- `BtmsInfiniteWedgeReferenceTest`：论文示例、独立 SciPy oracle、积分收敛、
  互易性和 zone-boundary 保护。

## 数值策略

半无限积分按 `η` 分段，每段使用 16 点 Gauss–Legendre 求积。步长同时受两项限制：

- 最大 `η` 步长 `0.1`；
- `k Rref` 每段最大相位推进 `π`。

默认积分到 `η = 8`。严格设置积分到 `η = 10`，最大步长 `0.075`，最大相位推进
`π/2`。BRAS 几何 12 kHz 上两者差异小于 `0.001 dB`。该参考的目标是离线
validation，不是每个 voice 的实时求值。

当 `η = 0` 同时落在 angular kernel 的解析奇点上时，当前实现显式拒绝输入。
未来若需要精确计算 zone boundary，必须加入解析极限展开，不能靠 epsilon
偷偷偏移角度。

## 独立数值核验

Calamia thesis 示例几何：

- `rS = 2 m`，`θS = 45°`；
- `rR = 5 m`，`θR = 270°`；
- exterior wedge `315°`，轴向分离为 0。

Java 结果与独立 SciPy 分段积分参考一致：

| 频率 | raw BTMS pressure |
|---:|---:|
| 10 Hz | -19.020602 dB |
| 100 Hz | -23.892751 dB |
| 1 kHz | -32.646342 dB |

BRAS LS1-MP1 理想无限 knife-edge：

| 频率 | 精确 BTMS | 2023 UDFA | UDFA − BTMS |
|---:|---:|---:|---:|
| 1 kHz | -11.287 dB | -11.093 dB | +0.194 dB |
| 2 kHz | -14.128 dB | -14.070 dB | +0.058 dB |
| 4 kHz | -17.084 dB | -17.071 dB | +0.013 dB |
| 8 kHz | -20.080 dB | -20.078 dB | +0.002 dB |
| 12 kHz | -21.838 dB | -21.838 dB | -0.000 dB |

五点 RMS 为 `0.0905 dB`。这满足“快速模型相对物理 reference 小于 1 dB”
的研究要求，并把后续工作重点移到真实厚屏与测量处理，而不是继续调整无限楔
UDFA 参数。

## 后续研究

1. 对全部 16 条 BRAS 路径做 matched/deconvolution-aware 首次到达和频谱门控；
2. 对 25 mm 屏建立 finite-width、two-edge BTMS/数值参考，验证 2024
   higher-order UDFA，而不是用单测点拟合；
3. 加入 Genelec 8020c 与 GRAS 40AF 的方向性/校准信息；
4. 在 Minecraft 1 m 厚 voxel 几何上比较 single-edge、double-edge 与
   geometry-dependent crossfade；
5. 只有通过实测 RIR、连续性、预算和 ABX gate 后，才把绕射滤波接入产品声流。
