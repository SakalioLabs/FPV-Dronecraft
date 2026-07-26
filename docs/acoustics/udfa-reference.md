# UDFA Infinite-wedge Reference

更新日期：2026-07-24

## 目的与许可边界

`UdfaInfiniteWedgeFilter` 是一个只依据论文公开公式独立实现的频域 oracle，用于回答
RQ2 的 C 路径是否比经验 B 基线具有可闻收益。它目前不会直接改变游戏声音。

依据：

- Kirsch and Ewert, *A Universal Filter Approximation of Edge Diffraction for
  Geometrical Acoustics*, IEEE/ACM TASLP 31 (2023), 1636-1651，
  DOI `10.1109/TASLP.2023.3264737`；
- [论文 Zenodo 存档](https://zenodo.org/records/12656189)；
- [2024 高阶扩展](https://doi.org/10.1051/aacus/2024059)。

论文 PDF 与 MATLAB toolbox 是独立分发物。本实现没有下载、读取或复制 toolbox
源码；代码注释只引用论文编号和公式。有限楔、高阶绕射和 IIR 系数仍未实现，不能用
无限楔 oracle 冒充完整 UDFA。

## 已实现的论文公式

当前对应 2023 论文 Eq. (2)-(6)：

1. 两项 incident/reflection diffraction pressure；
2. `ν = π / θw` 的角度增益 `Gν±`；
3. 由 source/receiver 距离、入射角和楔角计算的 `fc±`；
4. 修改型 fractional low-pass：

```text
H(f) = (
  (j f/fc)^(2/b)
  + (j f/(Q fc))^(1/(b r))
  + 1
)^(-alpha b/2)
```

论文参数以 `[P]` 记录：

| alpha | b | Q | r |
|---:|---:|---:|---:|
| 0.5 | 1.44 | 0.2 | 1.6 |

这组参数产生平滑的截止区，并在无限楔高频渐近为半阶低通，即约
`-3 dB/octave` pressure magnitude。

## 当前验证

- knife edge 的截止频率逐项对照 Eq. (4)；
- DC pressure magnitude 为 unity；
- 250/1000/4000 Hz 响应单调衰减；
- 200-400 kHz 渐近斜率在 `-3.0103 ± 0.08 dB/octave`；
- source/receiver 径向距离交换保持结果不变；
- 参数和几何输入拒绝非有限或非法范围。

## Voxel wedge geometry

`VoxelWedgeGeometryMapper` 已实现 90° solid block edge 的 `3π/2` exterior wedge：

- 沿 edge axis 解析 source/receiver 的 axial 与 radial 分量；
- 在无限 edge 上解析最短折线路径 apex，使两侧 incidence angle 一致；
- 以 `-incoming` 为 source-facing reference plane，构造正向 azimuth basis；
- source 或 receiver 投影落入 solid `π/2` sector 时拒绝候选；
- apex 超出当前一格 physical edge 的 `±0.5 m` 时显式标记，留给 finite-edge 模型，
  不静默套用无限楔。

zone classification 直接采用论文 Section II：

- shadow boundary：`theta_s + pi`；
- first reflection boundary：`pi - theta_s`；
- second reflection boundary：`2 theta_w - pi - theta_s`。

结果保留 `directVisible` 与两个 reflection flags，而不是互斥 enum，因为 reflected
GA field 可以和 direct/view 状态同时存在。shadow boundary 本身仍归 direct visible，
便于连续 crossfade。

## 时域 IIR reference

已依据论文 Eq. (18)-(20) 实现串联一阶 shelving filter：

- 默认 4 shelves，计算量等价于 2 SOS；
- bilinear transform 的 shelf knee 限制在 Nyquist 以下；
- 项目带宽采用 `0.475 × sampleRate [H]`；
- 额外常数 gain 在 20 Hz-16 kHz 对数频率上做最小 RMS dB 配准 `[H]`，不改变
  论文 fractional target 的频谱形状；
- geometry 更新使用旧/新两个稳定 filter 的 sample crossfade，不直接插值 IIR
  coefficient。

命令：

```text
gradlew :computational-acoustics-core:udfaIirBenchmark -Psamples=2000000
```

Intel Core i7-14700KF / Microsoft OpenJDK 25.0.1 的四组无限 knife-edge 几何：

| ds/dr | bending | 4-shelf RMS | max abs |
|---|---:|---:|---:|
| 1/1 m | 0.15 rad | 0.110 dB | 0.318 dB |
| 2/3 m | 0.60 rad | 0.169 dB | 0.431 dB |
| 8/2 m | 1.20 rad | 0.173 dB | 0.437 dB |
| 24/12 m | 1.50 rad | 0.172 dB | 0.448 dB |

2,000,000 samples 的四 shelf 处理约 `7.78 ns/sample`，相当于约 2677 个
48 kHz real-time channels 的纯 DSP 吞吐；不含声音引擎、crossfade 双处理或其他
声源成本。6 shelves 在这些几何上没有稳定降低误差，因此当前 reference 保留 4。

## Finite edge 与复数路径

已实现论文 Eq. (10)-(17)：

- apex 位于 physical edge 内：两个截断 finite half-wedges；
- `g_fin = 2/pi atan(pi sqrt(2 fc t_z))`；
- `fc,fin = fc/g_fin^2`，并按 Eq. (12) 调整 `b` 与 `Q`；
- apex 位于 edge 外：`1/[pi(t_z2-t_z1)]` first-order branch；
- edge corner 附近使用论文四次方 blending function；
- `t_z` 由 source/receiver 经一格 edge 两端点的 excess travel time 直接计算。

确定性渐近测试覆盖：

- 零长度 edge 的贡献为零；
- 很长的对称 finite edge 收敛 infinite-wedge response；
- apex 远在 edge 外时高频斜率趋近 `-6 dB/octave`；
- apex 以 `0.005 m` 步长跨过 physical edge 端点，1 kHz 最大相邻变化
  `0.054580 dB`，通过 `<3 dB/update` 门槛。

多路径使用显式 complex pressure：

- 同一路径的连续多个 edge transfer 相乘；
- 不同替代路径按 `exp(-j 2 pi f deltaLength/c)` 相干相加；
- 公共路径长度只进入 reference delay，避免巨大绝对 phase 降低数值精度；
- 稳定 BPF/电机 tones 使用 coherent pressure；
- 随机 broadband 使用各路径 pressure energy 之和，不制造稳定梳状干涉。

BRAS RS5 真实 RIR candidate golden 已建立：当前理想 `2π` knife-edge 在
1-12 kHz、1 ms 首次到达门上的 RMS 误差为 5.829 dB，整体少衰减 3.1-8.3 dB。
Java 结果已与独立 Python Eq. (2)-(6) oracle 逐点交叉核验。实测隔板厚 25 mm，
所以该结果要求继续研究双 `3π/2` edge 的相干组合，不能直接解释为论文公式失效，
也不能声称 finite-edge 绝对误差已经达到论文结果。详见
[`bras-rs5-validation.md`](bras-rs5-validation.md)。

## 2024 higher-order double edge reference

已依据 Kirsch and Ewert, *Filter-based first- and higher-order diffraction
modeling for geometrical acoustics*, Acta Acustica 8 (2024), Eq. (8)-(12)
独立实现：

- `UdfaSingleTermWedgeFilter`：Eq. (5) 的 unity-gain single-term cutoff；
- `DoubleEdgeGeometry`：source-assigned edge、receiver-assigned edge、两组远近
  azimuth、edge width 与 oblique effective width；
- `UdfaDoubleEdgeFilter`：
  - Eq. (8) modified exterior wedge angle；
  - Eq. (9) 两个 reciprocal two-term first-edge filters；
  - Eq. (10) 两个 single-term second-order filters；
  - Eq. (11)-(12) cutoff-weighted complex pressure mixing。

确定性测试覆盖：

- `d_w -> 0` 收敛到组合外楔角的单 knife-edge；
- source/receiver 及两 edge 对换保持 reciprocity；
- double shadow 高频趋近 `-6 dB/oct`；
- BRAS RS5 五个频点与独立 Python 复写逐点一致。

BRAS LS1-MP1 上，论文双边 reference 的 1 ms gate RMS 为 `10.284 dB`，高于
单边模型的 `5.829 dB`。这不是产品候选胜出：它要求先用 BTMS 数值 reference
复现论文误差，再处理真实测量的短门控、硬地板、设备方向性和反卷积 pre-ringing。
frequency-domain 参数 oracle 约 `1376.804 ns/evaluation`，单边约
`332.194 ns/evaluation`；几何更新成本可接受，但尚未实现四支路时域 IIR。

`DoubleEdgeGeometry.squareBarrierInPlane` 已把论文参数扩展为可审计的平面截面
构造，可直接生成主双边与 source/receiver floor-image 几何。BRAS 对称镜像路径的
距离、互易幅度与 `3.928846 ms` excess delay 已验证。相干加入两条硬地板路径和
MDF mass-law transmission 后，整段 RIR RMS 仍为 `11.103/11.545 dB`，没有优于
主双边的 `11.118 dB`。因此该扩展保留为路径预算能力，不构成产品候选胜出。

## 精确 BTMS 交叉验证

`BtmsInfiniteWedgeReference` 已按 Calamia 2009 thesis Eq. (3.4)、(3.8)、
(3.15)-(3.16) 独立实现。在 BRAS LS1-MP1 的理想 `2π` knife-edge 几何上，
UDFA 与 BTMS 在 1/2/4/8/12 kHz 的 RMS 差异为 `0.0905 dB`；12 kHz
reference/strict 积分收敛差异小于 `0.001 dB`。这说明当前无限楔 UDFA
reference 的频域逼近已经足够准确，5.829 dB 实测差异应转向厚屏、短门控、
方向性和反卷积链路排查。公式、数值策略与表格见
[`btms-reference.md`](btms-reference.md)。

## 接入前仍需完成

1. 已从 cardinal A* path 提取最多 3 个 `[H]` 主 edge candidate：只有转角内侧 cell
   已知且 solid 时才输出精确 block-edge apex、edge axis 与入/出方向；
2. 90° convex block 的 exterior wedge、azimuth、incidence、infinite apex 与
   shadow/reflection/view zone classification 已实现；
3. 时变 IIR、finite edge 与多 edge phase/delay reference 已完成；仍需声音流集成；
4. 高阶 edge interaction 仍只有 transfer cascade，不等于论文完整 HO 模型；
5. 用门洞、墙边和 L-room 实测 RIR/BRAS reference 做误差及 ABX；
6. 只有 C 相对经验 B 有稳定收益且连续性/预算通过，才接入产品路径。
