# D118：dEchorate 独立位置确认失败与受控失效诊断

## 结论

D117 的 `geometry-interaction` ridge 模型没有通过真正的 unseen 位置确认，必须拒绝：

| metric | preregistered gate | D118 result | decision |
|---|---:|---:|---|
| Top-4 overlap | `≥0.80` | `0.690909` | fail |
| Top-1 capture rate | `≥0.90` | `0.569697` | fail |
| amplitude RMSE | `≤3 dB` | `30.721787 dB` | fail |

Top-4 只比六面随机选四面的期望 `0.666667` 略高。该模型不得进入 Minecraft
集成、不得成为生产参数，也不得在这 165 条已经解盲的 RIR 上重新拟合后冒充确认结果。

失败后的 D119 诊断表明，主要问题不是 17-sample echo target 完全失效，而是无约束
ridge 几何交互项离开训练位置范围后发生灾难性外推：

- 新位置实测绝对变化最大 `28.1726 dB`；
- 冻结模型预测绝对值最大 `161.0097 dB`；
- 最大绝对误差 `155.8690 dB`；
- 新位置 `990` 行中有 `660` 行至少一个特征超出 D117 fit 范围，旧位置仅 `60` 行；
- 33-sample 窗口相对 17-sample 窗口的 Top-4 稳定度，新/旧位置分别为
  `0.8803/0.8833`，几乎相同。

新位置确有更多相邻一阶反射：15 个位置的 225 对 facet arrivals 中，间隔不超过
16 samples 的数量为 `16`，旧位置为 `9`。这说明未来需要 collision-aware event
grouping，但它不足以解释预测值从实测 `<30 dB` 外推到 `161 dB`；首要失败仍是模型类。

## 不可更改的解盲顺序

1. 在 Drive ID 发现前固定 33 文件、165 RIR、模型、阈值和失败保留政策；
2. 只读取官方 public-folder 目录，33 个精确文件名均唯一命中；
3. 在下载后、打开 HDF5 payload 前固定全部字节数和逐文件 SHA-256；
4. 编写允许成功或失败的机械 gate、负向测试和禁止 refit 约束；
5. 首次运行超过工具等待窗口且未生成结果；它可能已经打开波形，因此从该时刻起按
   “已解盲”处理；
6. 只对同一模型、数据和阈值做确定性复算，得到上述失败；
7. 将失败保存为 tracked JSON，独立 verifier 从 165 条 RIR 完整重算；
8. 所有后续诊断明确标记 post-confirmatory exploratory。

首次超时没有被用来更改任何特征、系数、尺度、阈值或目标。确认报告的
`refit_performed=false`，验证器同时要求阈值精确为 `0.8/0.9/3.0`。

## 数据身份

- 官方 public folder listing：`599` 个对象；
- 精确命中：`33/33`，缺失 `0`，重复 `0`；
- 下载字节数：`58,056,851`；
- 文件聚合 SHA-256：
  `0a52e237710084428b84a07cbcadc706dcef174432e4a71fdeab256bde8157eb`；
- RIR：`165`；
- waveform SHA-256 唯一数：`165`；
- 与 D117 330 RIR 的 waveform hash overlap：`0`；
- metadata coordinate bindings：`165/165`；
- direct shift 绝对值中位数/最大值：`0/2 samples`；
- 33 个 SOFA 的 stale `RoomDescription` 仍全部存在，房间身份继续只使用
  filename、Drive ID、file hash 与官方 metadata。

## 稳定模型身份

D117 顶层报告含有 D116 本机微基准字段，所以完整报告 SHA-256 会随回归运行变化。
D118 在解盲前同时固定：

- 原始 D117 report snapshot：
  `c79dcbd2ad0983eb20f7d4a2cd312f01214bc7532a4ffbd7d197a1dd5d39b824`；
- 包含 family、λ、420 个 coefficients 与 420 个 scales 的 canonical model SHA-256：
  `7ac69464c042fd054218e96f47aaa4d3456f89766e34d94398466731288ab97c`。

复算保留前者作为历史来源证据，但运行时以真正包含全部模型数值的后者验证身份。
回归重生成的 D117 report hash 已变化，而 frozen-model hash 保持逐位一致；这不是模型
替换。

## 分阵列诊断

| array / microphones | Top-4 | Top-1 capture | RMSE dB |
|---|---:|---:|---:|
| 1 / 0–4 | 0.6591 | 0.5455 | 44.0490 |
| 2 / 5–9 | 0.7136 | 0.4000 | 23.2313 |
| 6 / 25–29 | 0.7000 | 0.7636 | 18.7476 |

三个阵列都没有显示可接受的空间泛化，不能把失败归因于单个坏阵列。

## D119 明确研究方向

### 1. 保留已经通过的确定性物理层

- direct 与一阶路径继续使用 continuous shoebox image-source timing；
- voxel DDA 只负责 topology、遮挡、portal 与环境统计，不承担一米 voxel 的精确
  early timing；
- Java primitive 7-path batch 保持零分配 CPU 基线；
- 材料参数继续来自可追溯 specimen/room priors，不从本次失败模型导出。

### 2. 拒绝无约束高维幅度回归

下一版本不得继续使用“state × target × geometry”全展开 ridge。候选只能是：

- 有物理上下界的 reflection/path-retention 公式；
- 单调或符号受限的 surface-state contribution；
- 对输入几何做明确 training-support distance / convex-hull 检查；
- 超出支持范围时退回材料 prior，而不是线性外推；
- 输出先限于候选路径排序或 bounded gain correction，不直接生成任意 dB 幅度。

D118 全部位置已经成为 discovery/diagnostic 数据，不能再作新模型 holdout。

### 3. collision-aware early events

若两个一阶 arrivals 的间隔小于所用窗口总宽度，不再把它们当作可独立监督的 facet
echo。D119 应先按 arrival proximity 合并 event cluster，再比较：

- event timing；
- cluster energy；
- active facet set；
- predicted/observed Top-K。

17/33/65-sample 窗口敏感度必须作为 target-quality gate，而不是模型失败后再检查。

### 4. 空间阻塞验证

位置拆分不得再用 microphone ID 奇偶。应按三维坐标形成不相交的 spatial blocks，
并在训练前固定：

- training convex hull；
- interpolation validation；
- extrapolation validation；
- support-distance fallback threshold；
- 每一分区的 source/microphone/room identities。

任何新模型都需要另一批未访问数据。可选来源必须在读取波形前重新预注册；不能把
D118 的 165 RIR 重新命名为 holdout。

### 5. Minecraft 与 CUDA 决策

当前不把失败的 amplitude model 接入 Minecraft。可以继续集成的只有已验证的
continuous timing、DDA visibility、bounded material prior 与现有 EFX/FDN 环境层。

本机 primitive CPU batch 已远低于 `50 µs/scenario` 研究门；本轮仍无 `nvcc`，
`cuda_executed=false`。GPU 只有在二阶以上候选、射线数和动态实体数量使“传输 +
kernel + 同步”的端到端时间稳定快于 CPU batch 时才重新评估，不能因为 CUDA 可写就
让它成为声学正确性的依赖。

## 证据快照

| artifact | SHA-256 |
|---|---|
| preregistration | `49a9527f4d23bf878ec2696d2b341598e08e4f068a95fb88aa2ad5a99fc84cea` |
| Drive manifest | `0068fedcbc62fd1f39fce261afefa86a913df299f8661bee7701f724940689b4` |
| pre-waveform byte pins | `ed9c53257a352b36d6a29aeaa47f5554fc5a334bb0ef7b10a10cae60c828d0cd` |
| preserved D118 result | `de0afcba20a5d1b3c1e10d5f202986c3ee225b0cb976051f1606f43021aa08dc` |
| D118 verification | `161d8eef3af05ace19cbbbed21846260da1276af76e77bcc4c3e8c53fe77af22` |
| D119 failure diagnostic | `2dc40feb96fa6cefce887ec542f474eb38f3918d3e6c24eeb893486ae7ee71b4` |
| D119 diagnostic verification | `bbaf05c9ca9f4a5388f030c56baf9a257c6744d4d04c34a73e8d88e1e3581d2c` |

## 决策

D118 以失败完成：确认结果有效，模型无效。D119 接受“空间外推是主要失败机制”的
诊断，拒绝继续调 ridge、拒绝任何生产参数更改，也拒绝 CUDA/endpoint/release
overclaim。下一步是实现 bounded physical fallback 与 spatial-support gate 的离线
候选合同；在获得新 unseen 数据前，它只能是 exploratory implementation。
