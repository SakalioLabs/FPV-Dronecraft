# 25 mm 双边缘独立数值参考路线

> 实施状态（2026-07-24）：已新增独立 Gradle 模块
> `two-edge-wave-reference`，实现二维压力/速度交错网格、二维 CFL 校验、刚性
> cell face、明确标记为未验证 PML 替代品的 polynomial sponge、Ricker source、
> receiver trace 和确定性 smoke tool。当前自动测试已覆盖确定性、互易性、自由场
> 几何传播时间、厚屏遮挡，以及用大域作无回波对照时首个 sponge boundary return
> `<= -50 dB`；1 kHz、0.6/1.2 m 稳态幅度比也在 `0.25 dB` 内匹配独立 Hankel
> oracle。普通 2D 求解得到的是线源场，不能和 3D 点源 BTMS 直接比较。2.5D
> free-field inverse transform 已通过 1/2/4 kHz、三个轴向偏移的解析幅相和
> 64/96-node 收敛 gate。单个 `(frequency, k_y)` 的复数稀疏 Helmholtz/PML
> 求解也已实现，并在 24 cells/λ 下通过传播、evanescent、互易性和刚性镜像 gate；
> 16 cells/λ 明确失败。连续物理坐标插值和逐网格 origin alignment 已消除半格墙面
> 偏差；32/24 cells/λ 的固定几何收敛为 `0.161 dB / 1.580°`，已通过。完整障碍物
> rigid-wall inverse transform 也已在实际求解每个 `k_y` 节点后通过；
> zero-thickness BTMS 半平面 gate 也已通过。25 mm 双边缘 1/2 kHz wave
> candidate 均已生成并通过 quadrature/空间收敛；2024 UDFA 分别多衰减
> `4.119/6.608 dB`，两个频点都未通过当前产品 gate。4 kHz 前必须先关闭
> 更高阶 `k_y` 求积和紧凑 PML 域的独立收敛 gate。

## 为什么需要单独的 reference

BRAS RS5 已经能验证坐标、到达时间、真实测量链和端到端脚本，但它不是纯双边缘
数值真值：发布 RIR 包含硬地面、隔板传输、设备方向性以及 chamber/fixture residual，
且场景包没有原始 sweep 与 inverse filter。相干加入地面和 mass-law 传输后，五频点
RMS 仍为 `11.545 dB`，所以继续为该 RIR 拟合路径会混淆模型误差和测量链误差。

Kirsch and Ewert 2024 的高阶 UDFA 论文说明其模拟数据可向通讯作者索取，但没有提供
可直接下载的数值附件。论文中的 ED-Toolbox/BTMS 曲线可以作为目标定义，不能在当前
仓库中被当作可自动复现的 golden。UDFA toolbox 也不是独立 oracle：它与被测模型
同源，并且本项目继续保持“不读取或复制其实现源码”的 provenance 边界。

## 两条互不混淆的获取路线

### A. 作者数值数据

这是最低工程成本路线，但需要用户授权对外联系。请求内容应限定为：

- 论文 Figure 8/9 的频率向量、复数或幅度 transfer function；
- 对应 source、receiver、edge separation、wedge angle 和 zone 分类；
- second-order BTMS/ED-Toolbox 配置、归一化定义和声速；
- 数据许可与能否作为仓库测试 fixture 再分发。

收到数据后先保存原始文件、SHA-256 和许可，不做手工描图。若不能再分发，只保存下载
脚本/校验值并在 CI 中跳过数据测试。

### B. 项目自建 2.5D 点源波动 reference

默认推进这条路线，但分清两个层次：

- **B0：2D 数值 harness。** 当前 staggered-grid FDTD 用于验证离散稳定性、刚性
  cross-section、吸收边界和 CPU/GPU 吞吐。它的 point injection 在二维代表无限
  线源，Green function 与距离衰减都不同于 Minecraft/BTMS 的三维点源，因此不能
  直接拿它的绕射 dB 对照 BTMS。
- **B1：2.5D point-source oracle。** 对 edge-axis 方向不变的 3D Helmholtz 方程
  沿轴向作空间 Fourier transform；对每个轴向波数 `k_y` 解一个二维
  frequency-domain boundary-value problem，再对 `k_y` 逆变换。点源包含全部
  `k_y` 分量，不能只解 `k_y=0`；后者正是 2D 线源。

2.5D 路线仍不复用 UDFA/BTMS 的近似结构，并能表示无限长、25 mm 刚性矩形截面。
它以三维自由场点源解和现有无限楔 BTMS 为绝对 gate。轴向波数在临界点附近可能出现
谱奇异/尖峰，必须做显式采样收敛，不能仅使用均匀少量 `k_y`。

自由场 inverse-transform 原型已实现为
[`validate_2p5d_free_field.py`](../scripts/validate_2p5d_free_field.py)。它用
`k_y=k sin(theta)` 处理传播分支、`k_y=k cosh(u)` 处理 evanescent 分支，并分别
使用 Gauss-Legendre quadrature；这避免直接跨越 `|k_y|=k` 临界点。脚本只验证
2.5D 重建与采样策略，尚未包含障碍物 Helmholtz 求解。

64 nodes/branch 相对 96-node 加密及解析点源的九个 case 结果：

- 最大幅度误差 `0.0000111 dB`；
- 最大相位误差 `0.0000467°`；
- 最大 64/96-node 幅度变化 `0.00000885 dB`。

三项均通过脚本中更严格的 `0.01 dB / 0.01° / 0.01 dB` 自由场 gate。

单个 transformed Helmholtz 系统已实现为
[`validate_2p5d_helmholtz.py`](../scripts/validate_2p5d_helmholtz.py)。它使用
复坐标 stretch PML、五点 conservative flux stencil、外圈 Dirichlet 和
solid-face zero-normal-flux。1 kHz、`k_y=0`、24 cells/λ 的结果：

- 自由场最大幅度误差 `0.0398 dB`、最大相位误差 `3.618°`；
- 0.6/1.2 m 相对 spreading 误差 `0.00330 dB`；
- source/receiver 复数互易误差 `4.65e-15`；
- evanescent `k_perp=i·5 m⁻¹` 最大幅度误差 `0.00897 dB`、相位
  `0.000331°`；
- 刚性平墙相对镜像解误差 `0.523 dB / 0.0268°`。

随后已加入连续物理坐标的双线性 source injection/receiver interpolation，并让每个
网格通过 origin shift 将目标刚性面精确落在 face 上；插值后的 source/receiver
复数互易仍为机器精度。32 cells/λ 相对解析解：

- 自由场最大 `0.0251 dB / 2.036°`；
- evanescent 最大 `0.00813 dB / 0.000335°`；
- 刚性镜像 `0.218 dB / 0.720°`。

32/24 cells/λ 固定物理几何的最大变化为 `0.161 dB / 1.580°`，通过
`0.5 dB / 5°` gate。24/16 的刚性幅度变化仍为 `0.646 dB`，明确否决
16 cells/λ。后续批量 `k_y` reference 使用 32 cells/λ 结果，并保留 24 cells/λ
作收敛对照；不能只运行较便宜的 24-grid 就声称最终收敛。

完整障碍物 inverse transform 已实现为
[`validate_2p5d_obstacle_transform.py`](../scripts/validate_2p5d_obstacle_transform.py)。
它不再把解析 Hankel 值放进 integrand，而是在每个 propagating/evanescent
Gauss node 上实际组装并求解刚性墙 Helmholtz/PML 系统，再重建三维点源场：

- 8/12 nodes/branch 不收敛；12-node 绝对幅度仍差 `1.317 dB`；
- 18-node 为 `0.063 dB / 0.481°`，但相对 12-node 仍变化 `1.254 dB`；
- 24-node 为 `-0.0783 dB / -0.694°`；
- 24 相对 18-node 变化 `-0.142 dB / -1.174°`，通过
  `0.5 dB / 5°` quadrature gate。

24 cells/λ 网格每个系统 `42,781` unknowns；18/24 合计 84 次独立 sparse-LU
在本机该次运行约 `101.3 s`。这证明完整 2.5D 障碍物重建可行，也说明批量 `k_y`
是离线成本主项。各节点天然可并行，但在 correctness oracle 稳定前不引入 CUDA；
实时 Minecraft 路径仍不运行此 wave solver。

零厚度刚性半平面已实现为 blocked flux faces，并由
[`validate_2p5d_btms_halfplane.py`](../scripts/validate_2p5d_btms_halfplane.py)
完成完整 2.5D 求解。BTMS azimuth 必须相对屏幕两侧的边界射线测量；不能只把
source 旋转为 0 而不旋转 wedge boundary。当前几何
`rs=rr=0.632456 m`、`theta_s=5.03414`、`theta_r=1.24905 rad`：

- Java 精确 BTMS raw pressure：
  `0.0450134595 + 0.2385584689i`（`exp(-ikr)`）；
- normalized BTMS：`-10.25497 dB`；
- 24-grid/24-node FDFD 相对 BTMS：`+0.311 dB / +8.930°`；
- 24/18-node：`-0.292 dB / +0.507°`；
- 32-grid 换算后：`+0.382 dB / +6.754°`；
- 32/24-grid：`+0.071 dB / -2.176°`。

幅度、相位、quadrature 与空间收敛均通过 `1 dB / 10°`、
`0.5 dB / 5°` gate。此前错误的 source-zero azimuth fixture 会造成约 `-3 dB`
假误差，已移除。

## 25 mm 双边缘 1/2 kHz wave reference

[`generate_2p5d_double_edge_reference.py`](../scripts/generate_2p5d_double_edge_reference.py)
使用实体矩形 half-plane；粗/细网格分别让 25 mm 厚度精确占 2/3 cells，
`dx=12.5/8.333 mm`，所以两侧面和顶面都没有厚度量化误差。1 kHz 对称几何：

- source/front-edge、back-edge/receiver 均为 `0.632456 m`；
- shortest path `1.289911 m`；
- fine raw pressure（`1/r` normalization）：
  `0.146882446 - 0.164678953i`；
- fine normalized magnitude：`-10.91408 dB`；
- fine 24/18-node：`-0.1535 dB / +0.514°`；
- coarse 24/18-node：`-0.1559 dB / +0.559°`；
- 3-cell/2-cell spatial：`+0.0387 dB / -2.041°`。

reference 通过 `0.5 dB / 5°` quadrature 与 spatial gate。相同几何的独立
Python 2024 UDFA 为 `-15.03292 dB`，相对 wave reference 为 `-4.11884 dB`，
超过暂定单点 `4 dB` gate；因此现有 2024 UDFA 仍不进入产品 audio stream。
这项拒绝不依赖 BRAS chamber、短门控或扬声器方向性。

2 kHz 使用 4/6 cells 精确表示 25 mm 厚度，即 `dx=6.25/4.167 mm`，
细网格仍有 `41.16 cells/λ`。18/24-node 首轮的空间差异只有
`+0.0176 dB / -3.170°`，但求积变化达到约 `-4.28 dB / +16.71°`，
所以被正确拒绝，没有把未收敛值发布为 candidate。随后用单配置模式追加节点：

- fine 32/24-node：`+0.7895 dB / -1.364°`，幅度仍未通过；
- fine 40/32-node：`-0.9932 dB / -1.392°`，显示非单调收敛；
- fine 48/40-node：`+0.0295 dB / +0.678°`，通过；
- 6-cell/4-cell、48-node spatial：`+0.0374 dB / -3.217°`，通过；
- fine raw pressure（`1/r` normalization）：
  `-0.103464649 - 0.126995266i`；
- fine normalized magnitude：`-13.50216 dB`。

同几何 2024 UDFA 为 `-20.11027 dB`，相对 wave reference 多衰减
`6.60811 dB`，再次超过单点 gate。1/2 kHz 两点幅度误差 RMS 已达
`5.5060 dB`，高于多频产品门槛 `2 dB`；4 kHz 用于确定误差趋势和建立
完整 golden，不会改变当前“不接入”的产品决策。

为控制 4 kHz 成本，域缩减采用可审计规则而非任意裁剪：内部非 PML 区域固定为
`x=[0.25,1.75] m`、`z=[0.25,1.15] m`，PML 壳层维持 1 kHz 基准的
`0.729 λ` 厚度。因此 2/4 kHz 的 PML 分别为 `0.125/0.0625 m`。
在已收敛的 2 kHz fine/48-node case 上，紧凑域相对原 `2.0×1.4 m` 大域：

- 复数压力变化 `+0.01668 dB / +0.648°`，通过 `0.25 dB / 2°` 域 gate；
- unknowns 从 `159,313` 降到 `114,253`，减少 `28.28%`；
- 同机 solve time 从 `307.65 s` 降到 `188.65 s`，加速 `1.63×`。

4 kHz 可以采用该 PML 规则，但仍必须单独通过 quadrature 与 8/12-cell spatial
收敛；域 gate 通过不等于频率点本身已通过。

非嵌套 Gauss 阶数变化会丢弃全部旧 `k_y` 节点。为 4 kHz 新增
[`validate_2p5d_double_edge_nested_quadrature.py`](../scripts/validate_2p5d_double_edge_nested_quadrature.py)，
用 Clenshaw–Curtis cosine nodes 分别积分 propagating/evanescent 分支；加倍区间数
时保留全部旧节点。权重先通过 `1/x²/x⁴` 在 `[-1,1]` 上的精确积分
`2/2⁄3/2⁄5` 检查。2 kHz fine compact 域的 24→48 交叉验证：

- 内部 quadrature 变化 `+0.00880 dB / -0.0754°`；
- refined 相对独立 Gauss-48 为 `-0.02439 dB / -0.218°`；
- 96 次唯一 Helmholtz solves，复用 50 个 integrand samples。

因此 4 kHz 从 nested 48→96 起步，而不再运行两套互不复用的 Gauss 阶数。
实际 48→96 仍变化 `-1.3183 dB / +14.946°`，明确失败；复用全部节点继续
96→192 后，变化降为 `+0.00395 dB / -0.1085°`，通过严格 gate。12-cell raw 为
`0.048658184 + 0.108478828i`，normalized `-16.28576 dB`。
4 kHz 紧凑域粗/细矩阵分别为 `168,145/378,961` unknowns per solve；
8-cell coarse 也通过 96→192 quadrature（`+0.00188 dB / -0.1126°`），
但 12/8-cell spatial 为 `+0.01416 dB / -6.208°`：幅度通过，相位超过
`5°`，所以该层没有发布。

下一层保持 `dx/1.5` 比较 12/18 cells。18-cell 有 `853,633`
unknowns/solve、`61.74 cells/λ`；checkpoint runner 最终完成 `48` batches、
`384/384` unique Helmholtz solves，已关闭 batch 的实测总时间
`29,385.98 s`，平均 `78.154 s/solve`。18-cell 96→192 求积变化为
`+0.005008 dB / -0.100356°`，refined raw 为
`0.053941169 + 0.105985577i`，normalized `-16.28351 dB`。
12/18-cell spatial 变化为 `+0.002248 dB / -2.815115°`，通过固定
`0.5 dB / 5°` gate。受控 finalizer 因而给出 `promotion_eligible=true`，
独立 verifier 已接受正式 4 kHz golden，fixture SHA-256
`fabb3690b778e9b0eb293b2b60e0cba895254f0ce1833d9a6e075af7ce07a9ff`。
同几何 UDFA `-25.87245 dB` 多衰减 `9.58894 dB`，1/2/4 kHz RMS
为 `7.13161 dB`。

长求解不再依赖进程末尾一次性输出。validator 支持每个样本原子 checkpoint、
有限 Windows replace 退避和 `--maximum-new-solves`；配套
[`run_2p5d_double_edge_batches.py`](../scripts/run_2p5d_double_edge_batches.py)
用独立 CPU-bounded 子进程自动续跑。一次约 `5357 CPU s` 的外部终止没有留下
JSON，促成该修复；故障注入 smoke 已验证跨进程恢复不会重算已保存节点。

完整报告现在还显式携带 source、receiver、screen front/back/top 和物理厚度；
空间比较除频率、域、PML、求积阶数和网格层级外，也必须逐字段匹配这些几何。
schema-v1 同时固定模型标识、`343 m/s` 声速和 solver-native outgoing
`exp(+i*k*r)` complex phase convention；与 `exp(-i*k*r)` BTMS 比较时必须显式
共轭，不能只按幅度看作等价。

边界与场景按以下顺序增加：

1. B0 二维自由场、刚性 cell、吸收层和互易性；
2. B1 三维自由场点源重建，对照 `exp(-ikr)/(4πr)`；
3. 单个零厚度刚性半平面，对照精确无限楔 BTMS；
4. 自由场中的 25 mm 刚性矩形屏，只测双顶边；
5. 加入无限刚性地面镜像，复现 RS5 截面；
6. 最后才加入有限阻抗；不得在前五步通过前拟合材料。

核心实现：

- B0 压力位于 cell center，粒子速度位于交错 face，时间步满足二维 CFL；
- B1 每个 `(frequency, k_y)` 构造复数稀疏 Helmholtz 系统；
- 刚性表面使用法向速度/法向压力梯度为零的 Neumann 边界；
- 外边界使用 CPML 或经独立回波测试通过的吸收层；
- 逆 Fourier 积分同时覆盖传播与必要的 evanescent `k_y` 分量；
- 所有输出保存复数 transfer function、网格、`k_y` 节点/权重和 SHA-256。

## 分阶段带宽与资源预算

第一 gate 只覆盖 `1/2/4 kHz`。按已验证的最低每波长 24 cells，
`dx <= c/(24 f_max)`；取 `c=343 m/s` 时 4 kHz 的 `dx <= 3.57 mm`。
为精确表示 25 mm 厚度，4 kHz 粗/细网格采用 8/12 cells，
即 `dx=3.125/2.083 mm`。
当前 B0 时间步满足二维 CFL：

`dt <= 0.95 * dx / (c * sqrt(2))`

8/12 kHz 只有在 1–4 kHz 已经能区分模型且数值收敛通过后才扩展。B0 固定物理域和
仿真时长时，二维 cell-update 成本近似随频率三次方增长；B1 还要乘以收敛所需的
`k_y` 求解数。离线求解器不进入 Minecraft runtime；CPU、OpenGL compute 或 CUDA
只按同一离散问题的端到端吞吐选择，不改变 reference 定义。

## 数值正确性 gate

每个 gate 都必须自动化，且细网格结果不能由 UDFA 参数参与校准：

| 检查 | 通过门槛 |
|---|---:|
| B0 二维 line-source 幅度相对 Hankel 解 | `<= 0.25 dB` |
| B1 三维 point-source 幅度相对 `1/(4πr)` | `<= 0.25 dB` |
| source/receiver reciprocity | `<= 0.10 dB` |
| 吸收边界回波相对主到达 | `<= -50 dB` |
| `k_y` 与加密 1.5 倍采样的 2.5D 收敛 | 每频点 `<= 0.25 dB` |
| 单边缘相对精确 BTMS，1–4 kHz | RMS `<= 1.0 dB` |
| `dx` 与 `dx/1.5` 网格收敛 | 每频点 `<= 0.5 dB` |
| 仿真窗口加长 25% 的频响变化 | 每频点 `<= 0.25 dB` |

通过后才比较 25 mm 双边缘：

- 2024 UDFA、sequential single-edge 和波动 reference 使用相同几何与归一化；
- 先报告各频点复数误差，再报告幅度 RMS，不能只给一条平均数；
- 暂定产品候选门槛为 1–4 kHz RMS `<= 2 dB`、单点 `<= 4 dB`；
- 未通过时优先判定 zone、角度、相位或高阶项，不对 BRAS 单测点反向调参。

## Codex Agent 执行顺序

1. 新建纯离线 `two-edge-wave-reference` 工具，不依赖 Fabric/Minecraft；
2. 完成 B0 确定性 source、网格、CFL、receiver、互易性和吸收回波测试；
3. 加入二维 line-source Hankel 幅度检查及 `dx` 收敛，封存 B0 harness；
4. 实现 B1 frequency-domain Helmholtz 单个 `(frequency, k_y)` 求解；**已完成**
5. 用固定物理坐标插值和边界定位完成 32/24 cells/λ 空间收敛；**已完成**
6. 实现 obstacle point-source `k_y` inverse transform，并对三维刚性镜像作端到端复核；**已完成**
7. 用现有 `BtmsInfiniteWedgeReference` 生成单边缘 golden；**已完成**
8. 实现 25 mm 自由场双边缘场景，输出 1/2/4 kHz 复数 pressure；
   **已完成，4 kHz 18-cell golden 已通过独立验证**
9. 加入刚性地面版本，仅作为 RS5 几何解释，不与整段实测 RIR 拟合；
10. 运行 2024 UDFA 与 sequential baseline 对比，写入 decision log；
11. 只有数值 gate 全通过且 C 模型相对经验 B 有明确收益时，才设计实时 IIR 接入。

当前可复现命令：

```powershell
python docs/scripts/validate_2p5d_free_field.py --nodes 64 --json
python docs/scripts/validate_2p5d_helmholtz.py --points-per-wavelength 24 --json
python docs/scripts/validate_2p5d_helmholtz.py `
  --points-per-wavelength 32 `
  --compare-points-per-wavelength 24 `
  --json
python docs/scripts/validate_2p5d_obstacle_transform.py `
  --points-per-wavelength 24 `
  --nodes 18 `
  --refined-nodes 24 `
  --json
python docs/scripts/validate_2p5d_btms_halfplane.py `
  --points-per-wavelength 24 `
  --nodes 18 `
  --refined-nodes 24 `
  --json
python docs/scripts/generate_2p5d_double_edge_reference.py `
  --coarse-thickness-cells 2 `
  --fine-thickness-cells 3 `
  --nodes 18 `
  --refined-nodes 24 `
  --json
python docs/scripts/generate_2p5d_double_edge_reference.py `
  --frequency-hz 2000 `
  --single-thickness-cells 6 `
  --single-nodes 40 `
  --json
python docs/scripts/generate_2p5d_double_edge_reference.py `
  --frequency-hz 2000 `
  --single-thickness-cells 6 `
  --single-nodes 48 `
  --json
python docs/scripts/generate_2p5d_double_edge_reference.py `
  --frequency-hz 2000 `
  --single-thickness-cells 4 `
  --single-nodes 48 `
  --json
python docs/scripts/generate_2p5d_double_edge_reference.py `
  --frequency-hz 2000 `
  --single-thickness-cells 6 `
  --single-nodes 48 `
  --domain-minimum-x-m 0.125 `
  --domain-maximum-x-m 1.875 `
  --domain-minimum-z-m 0.125 `
  --domain-maximum-z-m 1.275 `
  --pml-width-m 0.125 `
  --json
python docs/scripts/validate_2p5d_double_edge_nested_quadrature.py `
  --frequency-hz 2000 `
  --thickness-cells 6 `
  --intervals 24 `
  --refined-intervals 48 `
  --json
.\gradlew.bat :two-edge-wave-reference:test
.\gradlew.bat :two-edge-wave-reference:waveReferenceSmoke
```

## 外部证据

- [Kirsch and Ewert 2024，高阶 UDFA](https://doi.org/10.1051/aacus/2024059)
- [Planeverb：动态场景 2D wave simulation](https://www.microsoft.com/en-us/research/publication/interactive-sound-propagation-for-dynamic-scenes-using-2d-wave-simulation/)
- [Calamia 2009 BTMS thesis](https://www.researchgate.net/publication/265025245_Advances_in_edge-diffraction_modeling_for_virtual-acoustic_simulations)
- [2.5D point-source/full-wave formulation](https://academic.oup.com/gji/article/193/2/938/634529)
- [2.5D `k_y` sampling and line-source distinction](https://academic.oup.com/gji/article/188/1/223/633345)
