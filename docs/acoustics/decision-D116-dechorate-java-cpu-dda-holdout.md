# D116 — dEchorate Java CPU DDA、连续边界与受控 holdout

## 结论

D116 已把 D115 的解析几何比较推进为真实的项目 Java CPU DDA 实验。66 个
room/source/microphone 场景共执行 `924` 条 direct/first-order 路径；每条开放空气腿都经过
项目现有的 Amanatides–Woo `VoxelDda`，而不是由 Python 模拟另一个实现。

结果同时支持和拒绝了几项假设：

- **接受 CPU DDA 作为拓扑基线**：924 条路径全部在预算内到达，单路径最多访问
  `12` 个 cells。
- **接受连续边界修正**：相对官方 21 条 direct/一阶 echo annotation，最大绝对误差
  `4.3400 samples`。
- **拒绝纯一米边界时序**：最大绝对误差仍为 `83.9773 samples`。
- **拒绝简单材料/距离候选排序**：`material_prior / path_length²` 的 Top-4 在 discovery
  上重合率 `0.65625`、holdout 上 `0.69444`；随机选择 4/6 的期望已经是 `0.66667`。
- **暂时接受表面耦合可学习性**：只在 8 个 discovery 房间拟合的固定位置线性耦合模型，
  在 3 个房间 holdout 的 9 组 source-4/microphone 比较中，Top-1 全部落入预测 Top-4，
  Top-4 重合率 `1.0`。
- **拒绝把该系数用于生产**：holdout 幅度 RMSE 仍为 `3.0811 dB`，而且系数是
  per-microphone/per-facet，尚未证明对新位置泛化。
- **本地速度门通过、零分配门失败**：最终回归的 JVM 微基准场景 P99 约 `3.17 µs`，但 P95
  每场景仍分配约 `2.0 KiB`。该数字不是 Minecraft tick 集成测量，也不是跨机器承诺。

本轮没有执行 CUDA kernel；当前环境仍缺少 `nvcc`。没有打开物理音频端点、没有播放或
录制声音，也没有建立听觉或 release calibration 结论。

最终回归报告 SHA-256：

| 报告 | SHA-256 |
|---|---|
| Java CPU DDA 原始语料 | `6078899d16f3f0e5b7b1846c47a462a4e0abe236da2e5481ed04ca6fbf5a7479` |
| D116 分析 | `8f71539021868caba67e29e62a84306dd1e8ab5af7586aaf3847ee45c3d16f29` |
| D116 独立验证 | `4f5401e7a5d46b6a2d869766071c7986baa1794b284aa3a67a07f5ceaac83204` |

## 实现边界

新增的 `DdaFirstOrderPathSolver` 使用分层职责：

1. image-source 几何只计算 direct/一阶反射的连续 reflection point 和路径长度；
2. source→reflection 与 reflection→listener 两条腿都由生产 CPU `VoxelDda.walk`
   遍历；
3. DDA 负责 open-cell topology、visited-cell budget 和确定性 corner crossing；
4. room boundary 可分别取 `6×6×2 m` 或真实的
   `5.705×5.965×2.355 m`；
5. path report 同时保留 reflection point、传播长度、arrival sample、visited cells 和
   topology-visible 标志。

这不是让解析 image-source 取代 DDA。Minecraft 中 DDA 仍决定阻挡、可见面和材料序列；
连续边界信息只修正命中面的空间位置和 delay。

## 66 场景语料

每个房间使用两类 source、各三个预注册距离位置：

| source | microphones | distance ranks |
|---|---|---|
| 4，inverse-directional/echo annotation | 10, 19, 20 | 1, 15, 30 |
| 6，omnidirectional control | 14, 29, 0 | 1, 15, 30 |

11 个 room codes × 6 个 source/mic pairs = 66 个场景。每场景计算：

- voxel-boundary direct + 六面一阶路径；
- continuous-boundary direct + 六面一阶路径；
- 一个固定最多 4 条的候选调度列表。

所以真实 Java DDA 路径数为 `66 × 14 = 924`。场景坐标逐一反查 D115 的 hash-bound SOFA
报告；Java 输出的 arrival samples 与 D115 独立 Python image-source 结果最大差异低于
`1e-9 sample`。

同一 source/mic 的几何跨 11 个房间不变，因此实验中的精确几何缓存为 6 次 miss、
60 次 hit，命中率 `0.90909`。这只证明静态语料存在复用，不代表移动中的 Minecraft
source/listener 能获得同样的精确 cache 命中率；生产缓存必须使用量化位置、snapshot
generation 和失效边界重新测量。

## 模型 A：纯一米边界

模型 A 使用 `6×6×2 m` 六面边界并实际运行 CPU DDA。它在 direct path 上没有几何误差，
但 ceiling、east、north 的取整会改变传播长度：

- 21 条 annotation 的绝对残差中位数 `2.4957 samples`；
- 最大值 `83.9773 samples`；
- 拓扑全部可见并不能保证声学时序正确。

因此“DDA 命中了正确方块”不是 early-reflection delay 的充分条件。禁止用材料吸声参数去
补偿这个误差。

## 模型 B：体素拓扑 + 连续边界

模型 B 保留相同 Java DDA 路径流程，但使用实测连续边界定位 reflection point：

- 绝对残差中位数 `2.0332 samples`；
- 最大值 `4.3400 samples`；
- 21 条路径均不超过 D115 预设的 5-sample sanity gate。

这支持 Minecraft 采用“粗体素发现候选面，再用方块 shape/sub-voxel plane 修正路径长度”
的结构。连续修正需要来自真实 collision/voxel shape 或稳定的局部平面拟合，不能凭房间
目标值反向移动墙面。

## 模型 C：有限候选

### 未拟合启发式

候选上限固定为 4，排序分数固定为：

`unfitted material prior / continuous path length²`

反射状态 prior 为 `1.0`，吸声状态 prior 为 `0.2`，没有读取实测 echo energy 调参。

| split | source-4 groups | Top-4 overlap | random expectation | reflective recall |
|---|---:|---:|---:|---:|
| discovery | 24 | 0.65625 | 0.66667 | 0.98333 |
| holdout | 9 | 0.69444 | 0.66667 | 0.86364 |

它擅长把标记为 reflective 的面放入队列，却不能可靠预测实测 strongest echo；holdout
相对随机期望的提升只有约 `0.028`。因此
`unfitted_material_distance_ranking_admitted=false`。

### 受控表面响应

为了判断失败来自“候选不可学习”还是“特征太简单”，D116 只在 source 4 的固定三个
microphones 上执行受控筛查：

- 输入：intercept + ceiling/west/south/east/north 的五个二值状态；
- 输出：每 microphone × observed facet 的 echo-energy gain dB；
- discovery：8 个 room codes；
- holdout：`011110, 011111, 020002`；
- 不使用 holdout 选特征、阈值或系数；
- `020002` 仍按 `010001 + furniture`，不拟合家具系数。

| split | groups | RMSE dB | MAE dB | Top-4 overlap | Top-1 captured |
|---|---:|---:|---:|---:|---:|
| discovery | 24 | 0.9937 | 0.5781 | 0.9583 | 24/24 |
| holdout | 9 | 3.0811 | 2.3485 | 1.0000 | 9/9 |

这证明表面之间存在可利用的耦合结构，并支持“有限 early candidates”方向；但它没有
position holdout，不能作为 Minecraft 参数表、材料系数或 release preset。

## 本地运行预算

微基准每场景计算 continuous-boundary direct + 六面 reflection，共 7 条路径；300 个
batch，每 batch 66 场景。它包含 Java path/result 构造和 DDA 遍历，但不包含 Minecraft
snapshot 建立、线程切换、OpenAL/FDN 更新或 GC pause。

当前结果满足研究级 `P99 ≤ 50 µs/scenario` 门，但未满足零分配门：

- P50/P95/P99 均处于微秒以下至数微秒量级；
- allocation counter 可用；
- P95 约 `2,045 bytes/scenario`；
- `allocation_free_gate_passed=false`。

生产化前需要批量、可复用 buffer API，避免为每个 `AcousticVector`、`Path` 和候选列表
建立短生命周期对象；之后必须在 Minecraft worker/snapshot 流程中重新测量。

## CUDA 结论

D116 没有改变 CUDA 边界：

- CPU DDA 是正确性基线；
- CUDA 必须复用已有 production snapshot bundle、材料表和 expected-results sidecar；
- 当前无 `nvcc`，因此只承认既有静态 source/ABI contract，不承认 kernel 已编译或运行；
- 即使未来 CUDA 更快，也不能解决一米边界的 84-sample 几何误差；
- GPU benchmark 必须包含 transfer、kernel、synchronization 和 fallback，不能只报 kernel。

## D117 — Codex Agent 可执行计划

### 扩展现有数据，而不是继续下载

每个已下载 SOFA 文件包含 5 个 receiver channels。当前 66 条分析只使用预注册的一个
receiver index，但现有文件实际上覆盖：

- source 4：arrays 3/4/5，即 microphones 10–24，共 15 个位置；
- source 6：arrays 3/6/1，即 microphones 10–14、25–29、0–4，共 15 个位置；
- 11 房间 × 2 sources × 15 microphones = `330` 条可用 RIR。

D117 必须先逐 channel 固定 waveform hash、坐标和 channel identity，确认没有重复或
array/channel 错配，再承认 330-RIR 扩展集。

### 双重 holdout

source 4 的 microphones 10–24 固定按 id 奇偶划分：

- position-discovery：偶数 microphones；
- position-holdout：奇数 microphones。

房间继续沿用 D116：

- room-discovery：前 8 个 room codes；
- room-holdout：`011110, 011111, 020002`。

形成四个互斥评估区：

1. discovery room + discovery position；
2. discovery room + holdout position；
3. holdout room + discovery position；
4. holdout room + holdout position（joint holdout）。

禁止在 joint holdout 失败后覆盖结果；必须版本化新模型。

### 几何泛化模型

移除 per-microphone 系数，只允许使用运行时可获得的特征：

- facet state 与相邻表面状态；
- continuous path length/delay；
- source→surface 与 surface→listener 的入射/出射角；
- reflection point 在面的归一化位置；
- source/listener 高度与归一化房间坐标；
- source role/directivity；
- 遮挡、DDA cell/material sequence；
- 固定的低维 facet orientation 编码。

先比较可解释的线性/ridge 模型和单调受约束模型；在样本规模不足时不引入神经网络。

### 验收

- 330 个 channel 的坐标、波形和源文件 hash 全部绑定；
- joint holdout Top-4 overlap 明显超过随机 `2/3`，预设门建议 `≥0.80`；
- joint holdout Top-1 captured 建议 `≥90%`；
- 幅度 RMSE 报告但不单独决定发布；目标 `≤3 dB`；
- continuous timing 最大误差保持 `≤5 samples`；
- 新批量 DDA API 的 worker 热路径 P95 allocation 为 `0 bytes/scenario`；
- Minecraft 集成 P99 在固定 rays/source/tick 预算内通过；
- CUDA 仍只在可编译、可运行、CPU parity 与端到端时间均通过后承认。

## 决策

D116 接受“CPU DDA 拓扑 + 连续局部平面 delay + 有限早期候选 + EFX/FDN 晚期混响”的
总体架构；拒绝纯一米反射时序、拒绝简单材料/距离排序、拒绝固定麦克风系数进入生产，也
拒绝因本地微基准很快就宣称完成实时集成。

下一步是 D117 的 330-RIR 位置泛化与零分配批处理，而不是调整 Minecraft release 参数。
