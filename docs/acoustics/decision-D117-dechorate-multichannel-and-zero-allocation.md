# D117 — 330 通道位置泛化与零分配 Java DDA

## 结论

D117 在没有新增下载的情况下，把 D115/D116 的 66 条选定 receiver 波形扩展为现有 SOFA
文件内部的全部 5 个 receiver channels：

- 66 个 SOFA 文件；
- 每文件 5 channels；
- 共 `330` 条 RIR；
- `330` 个不同 waveform SHA-256；
- `330` 组 source/receiver 坐标全部与官方 metadata CSV 对齐；
- 66 个文件的 stale `RoomDescription` 仍被拒绝，身份继续由文件名、Drive id、文件 hash、
  array id 和 receiver index 共同确定。

本轮同时完成两个不同层级的结果：

1. 去掉 per-microphone 系数的 geometry-interaction 模型在位置验证中显著优于随机候选，
   说明运行时几何特征值得继续；
2. Java 七路径 CPU DDA 的 reusable primitive workspace 与对象参考实现完全 parity，
   并在五个独立热路径窗口中测得零 heap allocation。

但 D117 **不构成确认性模型验证**。在正式固定分析前，开发过程中已经查看过拟议
room×position joint partition 的聚合结果，所以该分区被明确标记为 contaminated /
exploratory。任何把它写成“独立 holdout 已通过”的结论都由 verifier 拒绝。

## 330-RIR 通道身份

已下载 SOFA 文件的数组覆盖为：

| source | arrays | microphone ids | RIR count |
|---|---|---|---:|
| 4，directional/annotation-compatible | 3, 4, 5 | 10–24 | 165 |
| 6，omnidirectional control | 1, 3, 6 | 0–4, 10–14, 25–29 | 165 |

每条通道使用确定性映射：

`microphone_id = (sofa_array_id - 1) × 5 + receiver_index`

并逐项核对：

- file bytes 与 SHA-256；
- `Data.IR = [1, 5, 48000]`；
- 48 kHz；
- `Data.Delay = 0`；
- source position；
- receiver position；
- metadata 的 room/source/microphone 行；
- signal finite 且非静音；
- channel waveform SHA-256 不重复。

330 条 RIR 的 direct peak 相对全吸声同 source/mic baseline，绝对 shift 中位数仍为
`1 sample`、最大值仍为 `8 samples`，与 D115 的六位置子集边界一致。

## 位置泛化模型

### 划分

房间沿用 D116：

- room discovery：`000000, 000001, 000010, 000100, 001000, 010000,
  011000, 011100`；
- room validation：`011110, 011111, 020002`。

source 4 的 microphones 10–24 按 id 奇偶拆分：

- position discovery：`10, 12, 14, 16, 18, 20, 22, 24`；
- position validation：`11, 13, 15, 17, 19, 21, 23`。

形成四个 partition：

| partition | rows | groups |
|---|---:|---:|
| fit | 384 | 64 |
| position validation | 336 | 56 |
| room validation | 144 | 24 |
| joint exploratory | 126 | 21 |

每 group 是一个 room×microphone，包含六个 target facets。

### 特征与选择

禁止使用 microphone id 或 per-microphone coefficients。允许的特征全部可由运行时场景
获得：

- 五个可切换 facet states；
- target facet orientation；
- state×target coupling；
- continuous path length 与 inverse length；
- reflection point 的归一化三维位置；
- source/listener incidence cosine；
- listener 的归一化房间坐标；
- direct distance；
- 上述几何与 state/target-state 的交互。

两个预设 family 为：

- `surface-coupling`；
- `geometry-interaction`。

ridge λ 网格固定为 `0.01, 0.1, 1, 10, 100`。只按 position-validation 的
Top-4、Top-1、RMSE、复杂度和 λ 顺序选择。最终选择：

- family：`geometry-interaction`；
- λ：`0.1`；
- feature count：`420`；
- zero-state prediction：严格 `0 dB`；
- coefficients 和 feature scales 写入报告并由 frozen-model SHA-256 固定。

### 探索性结果

| partition | RMSE dB | MAE dB | Top-4 overlap | Top-1 capture |
|---|---:|---:|---:|---:|
| fit | 2.5594 | 1.5593 | 0.8828 | 0.9688 |
| position validation | 2.5549 | 1.4991 | 0.8705 | 1.0000 |
| room validation | 5.0154 | 3.1941 | 0.8854 | 1.0000 |
| joint exploratory | 4.4573 | 3.0458 | 0.9643 | 1.0000 |

随机选择六面中的四面，期望 Top-4 overlap 为 `2/3`。结果支持几何交互用于候选排序；
但跨房间幅度 RMSE 明显高于 `3 dB`，说明局部固定 echo window 仍混入相邻反射、
source directivity 与房间耦合。故：

- `position_general_features_admitted_for_further_study=true`；
- `amplitude_rmse_gate_passed=false`；
- `joint_result_confirmatory=false`；
- `production_candidate_model_eligible=false`。

## 研究完整性边界

D117 开发期间曾运行一次临时探索，查看了奇数 microphones 与 room holdout 的聚合
Top-4/RMSE。即使最终代码的 family/λ 只由 position validation 自动选择，该先验查看仍会
影响人的模型选择判断。因此：

- 现有 joint partition 不再称 holdout；
- 报告记录 contamination reason；
- verifier 要求 `confirmatory_holdout_eligible=false`；
- D118 必须使用在本轮从未读取的新官方 SOFA shards；
- D117 的 frozen model 不得重新拟合或改变阈值。

## 零分配七路径 CPU DDA

D116 对象型研究 API 每场景 P95 分配约 2 KiB。D117 新增：

- `VoxelDda.traceOpenBoundsPacked(...)`：以 primitive packed result 返回 visited count
  与 reached-end；
- `DdaFirstOrderBatchSolver.Workspace`：预分配 7 个 path 的 length、visited cells、
  visibility 和 reflection point arrays；
- direct + 六面 first-order solve 全程复用 workspace；
- 不改变 Amanatides–Woo crossing/tie rule；
- 对象 API 保留为易读正确性参考。

### Parity

在 D116 的 6 个 source/mic pairs 上比较 42 条路径：

- maximum length error：`0 m`；
- maximum reflection-point error：`0 m`；
- visited-cell mismatches：`0`；
- visibility mismatches：`0`。

### 本地微基准

经过 1,000,000 场景 warm-up 后，运行五个 allocation windows，每窗口 250,000 场景：

- allocation windows：`[0, 0, 0, 0, 0] bytes`；
- 每场景 7 paths；
- 本机热 JVM P99 远低于研究门 `50 µs/scenario`。

这只关闭核心算法 heap-allocation 门，不包括：

- Minecraft snapshot 建立；
- worker queue/handoff；
- moving source/listener cache invalidation；
- audio backend 参数更新；
- GC pause 与其他模组争用；
- CUDA transfer/kernel/synchronization。

因此 `minecraft_integration_measured=false`。

## CUDA

本轮仍无 `nvcc`，`cuda_executed=false`。零分配 CPU batch 反而进一步明确了 CUDA 的
竞争基线：未来 GPU 不只要快过对象型研究 API，还要在包含传输和同步后，快过当前
primitive CPU batch，并保持同一 42-path/production-bundle parity。CUDA 依旧是可选加速，
不是声学正确性的依赖。

## D118 预注册确认集

在发现 Drive ids、下载文件或读取波形之前，已创建
`dechorate-confirmatory-shards-v1.json`。它固定 source 4 的此前未访问 arrays：

- array 1：microphones 0–4；
- array 2：microphones 5–9；
- array 6：microphones 25–29；
- 11 room codes；
- 33 SOFA files；
- 165 全新 RIR channels。

冻结内容：

- D117 model family `geometry-interaction`；
- λ `0.1`；
- 420 features；
- frozen coefficients/scale/model hash；
- 不允许 refit；
- Top-4 overlap `≥0.80`；
- Top-1 capture `≥0.90`；
- amplitude RMSE `≤3 dB` 独立报告；
- 失败后保留结果，任何新模型必须换版本并取得新的 unseen data。

### D118 Codex Agent 执行顺序

1. hash 固定预注册合同；
2. 从官方 public folder 只发现 33 个精确 filenames 的 Drive ids；
3. 生成 id-only manifest，检查无文件缺失/重复；
4. 下载到新的 gitignored directory，以 `.part` + atomic replace；
5. 生成 bytes/SHA-256 inventory；
6. 核对 165 channel identities、metadata coordinates 和 waveform uniqueness；
7. 读取 D117 frozen coefficients/scale，不允许调用 fit；
8. 一次性计算 confirmatory metrics；
9. 无论成功或失败都生成不可覆盖的 D118 report/verifier；
10. 生产参数、Minecraft 集成和 release calibration 仍保持 false。

## 决策

D117 接受 330-RIR 数据扩展、接受运行时几何特征作为候选排序研究方向，并接受 reusable
primitive CPU DDA 作为零分配正确性基线；拒绝把污染的 joint partition 当作确认性
holdout，拒绝 amplitude 模型，拒绝发布候选参数，也拒绝宣称 CUDA 已运行。

下一步是严格按已固定的 D118 33-file/165-RIR 合同进行一次真正的 unseen confirmation。

## 最终证据快照

以下 SHA-256 在完整 `acousticResearchCheck` 通过后固定；其中基准延迟会随本机负载波动，
哈希只标识本次报告快照，不把单次纳秒数解释为跨机器性能承诺。

| artifact | SHA-256 |
|---|---|
| `dechorate-multichannel-generalization-v1.json` | `c79dcbd2ad0983eb20f7d4a2cd312f01214bc7532a4ffbd7d197a1dd5d39b824` |
| `dechorate-multichannel-generalization-verification-v1.json` | `aa2ce3d6079fd67142bea8ad6870e07c94e401b7310bf8af33deabcf60efe7c6` |
| `dda-first-order-batch-benchmark-v1.json` | `89db8f2ac229b4a18742541d787bc225c84ee6bbe2e2a2c7d78a45d04b622030` |
| `dda-first-order-batch-benchmark-verification-v1.json` | `be0627739bd23dc4d8c625fde8cdebef93671bfacd92ab9c4b839978009e27a4` |
| `dechorate-confirmatory-shards-v1.json` | `49a9527f4d23bf878ec2696d2b341598e08e4f068a95fb88aa2ad5a99fc84cea` |

本次基准快照的 P50/P95/P99 分别为
`0.303/0.665/0.698 µs/scenario`；五个 allocation windows 仍全部为 `0 bytes`。
