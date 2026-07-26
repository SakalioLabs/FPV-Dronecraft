# D115 — dEchorate 实测 SOFA 子集与几何/衰减分析

## 结论

D115 已取得并逐文件固定 D114 预注册的 66 条真实 RIR，不再依赖无法可靠按需遍历的
4.2 GB 单体 HDF5。实测结果支持继续采用“精细早期路径 + 统计晚期混响”的分层架构，
但不支持把一米 Minecraft 体素边界直接当作精确的一阶反射面，也不支持从本子集直接拟合
可发布的逐方块材料参数。

- 官方 Google Drive 公共文件夹中的 66 个独立 SOFA 文件均按文件名、Drive id、字节数和
  SHA-256 固定；总大小为 `115,271,864 bytes`，按 manifest 顺序聚合的 SHA-256 为
  `944e528bea93e30352f3b28a580ca85ef1a36b5cbf0d6e6102aab6ebf43c1d5e`。
- 官方 annotation HDF5 为 `30,324 bytes`，SHA-256 为
  `1c2810a2dc3870c75276a4b954c36b884e31b291483190634b2030040061317e`。
- 连续 shoebox/image-source 几何相对官方 echo annotation 的 21 条路径中，绝对残差中位数
  `2.0332 samples`，最大值 `4.3400 samples`。
- 同一几何取整为 `6×6×2` 的一米体素后，绝对残差中位数 `2.4957 samples`，最大值
  `83.9773 samples`。最大误差约 `1.75 ms`，足以破坏早期反射的梳状干涉相位。
- 相对全吸声房间的实测 direct-arrival shift 中位数为 `1 sample`、最大值为
  `8 samples`，因此后续算法必须使用稳健的到达窗，不能把每个房间的波形峰值强制锁到
  同一采样。
- 反射面数量与房间 T20 在 500/1000/2000 Hz 上均呈显著正相关，Spearman ρ 分别为
  `0.89865 / 0.83400 / 0.93744`。
- 全吸声到五个可切换表面全反射时，T20 从
  `0.16559/0.10548/0.09353 s` 增长至
  `0.59039/0.61334/0.58888 s`（500/1000/2000 Hz）。
- ceiling 的局部 echo 能量可完全区分反射/吸声状态（AUC `1.0`），但 west/east/north
  仅为 `0.6778/0.6620/0.6481`，south 为 `0.4405`。单个固定时间窗中的反射幅度不是
  跨表面、跨位置可分离的材料观测量。
- `020002` 等价于 `010001 + furniture`，但数据集中没有无家具的 `010001` 配对房间，
  因而家具的因果影响不可识别。

分析报告 SHA-256：
`85b706250a6a0d377419be60de9f4c00a8f7484058c5a785996fbb4c818b00f8`。

独立验证报告 SHA-256：
`efbf655d07e19111e3ce17ca361c84c6032c8c0b59b421307714f0b9b27d3bc3`。

## 数据取得与身份边界

官方 README 指向公共 Google Drive 文件夹；其中存在逐
`room/source/microphone` 分片的 SOFA 文件，因此只需获取预注册的 66 个文件。下载器具备：

- 最多六路并发、`.part` 临时文件和原子替换；
- 已有精确文件的跳过；
- 每文件字节数、SHA-256 和 HDF5 magic 校验；
- annotation 文件同等级固定；
- 最终 inventory 的文件顺序、总字节数和聚合 SHA-256 校验。

SOFA 文件身份存在一个重要陷阱：66 个 `Title` 均与文件名一致，但 66 个
`RoomDescription` 全部错误地写成 `room_code:020002`。因此本实验只接受由公共文件夹中的
文件名、Drive id 与逐文件 hash 共同定义的房间身份，明确设置
`sofa_room_description_admitted=false`。这不是根据测量结果事后修正标签，而是对互相冲突
元数据的可审计来源优先级。

固定文件：

| 合同 | SHA-256 |
|---|---|
| `dechorate-sofa-subset-v1.json` | `6005c4bc87444dd20e96991689f0c561c46dfcd6661e931aa88dcf993ac55244` |
| `dechorate-sofa-subset-pins-v1.json` | `7e596333d69337ad46dbdcbea13882fc573cea6a56b2e053e55b56975ae7e453` |
| 下载 inventory | `8eea41cc4761df451dd20c2d4462d6502b24ac9b09fb3ccfb676789763664392` |
| D114 选择报告 | `7918da554a71a42e69854dab96ff0ea37e7fe1e8b4300bc694674d7adecc6415` |

被删除的 Zenodo SOFA record 返回 HTTP 410；Zenodo v2 的单体 HDF5 仍声明
`4,192,401,641 bytes` 和 MD5 `8b42a0e99766acce8a100130774bd5b3`，但远程 range
遍历无法建立可信的 HDF5 结构。由于没有下载全文并核对 MD5，本研究不宣称原文件损坏；
逐文件官方 SOFA 是更小且可完整校验的替代发布路径。

## 实测方法

每条 SOFA 波形都从原文件直接读取并重新计算，不使用缓存的派生指标。分析固定为 48 kHz，
并按 room code、source role、distance rank 分层保留记录：

1. 核对文件标题、采样率、源/接收器坐标和波形 SHA-256；
2. 用全吸声 `000000` 的同 source/mic 波形作为 direct-arrival 比较基线；
3. 对 source 4 使用官方 annotation 的 direct 与六面一阶 echo timing；
4. 同时计算连续物理房间和 `6×6×2` 一米体素房间的一阶 image-source 到达时间；
5. 对 500/1000/2000 Hz 计算 EDT、T20、T30，并记录每个拟合是否拥有足够动态范围；
6. 计算 early/late energy 和以官方 echo timing 为中心的局部能量增益；
7. 使用 Mann–Whitney 式成对排序 AUC，而不是凭单个均值宣称材料可识别；
8. 按 11 个房间聚合，并检验 reflective facet count 与 T20 的 Spearman 相关。

本轮没有打开物理音频端点、没有播放或录制声音，也没有进行听觉实验。

## 对 Minecraft 实时架构的约束

### 保留

- CPU voxel DDA 继续承担直达遮挡、可见性、portal 和材料路径归约；
- OpenAL EFX 继续作为支持设备上的低成本 late-reverb 后端；
- Java 三频带 FDN 继续作为确定性回退与可测后端；
- CUDA 仍只允许作为批处理或高端可选加速，且必须复用 CPU 的 snapshot、材料表和
  expected-results contract。

### 调整研究方向

- 一米方块边界可以描述“哪个区域封闭、哪些材料大致暴露”，但不能直接给早期反射提供精确
  时序。需要在命中方块后构建局部 sub-voxel plane，或对到达时间使用连续坐标修正。
- 早期反射要优先保留 delay、方向与稳定性；幅度应由方向性、表面响应、遮挡和相邻反射
  联合决定，不能只查一个“材料反射率”。
- 晚期混响应从房间体积、暴露面积、portal openness 和三频带等效吸收推导；dEchorate
  支持反射面增多会显著延长衰减，但不提供 Minecraft 方块级发布参数。
- `production_material_fit_eligible=false`、`production_change_required=false`、
  `release_calibrated=false` 保持不变。

## D116 — Codex Agent 可执行计划

### 目标

在不使用 D115 holdout 调参的前提下，建立一个受控表面响应模型，并用真实 CPU DDA
路径测试“粗体素拓扑 + 连续命中修正”能否同时满足直达遮挡、早期时序和实时预算。

### 数据划分

1. 固定 discovery：
   `000000, 010000, 001000, 000100, 000010, 000001, 011000, 011100`。
2. 固定 holdout：
   `011110, 011111, 020002`。
3. `020002` 只检验泛化，不估计家具系数。
4. source 4 用于 timing/echo；source 6 用于全向衰减对照。两者不得合并后只报告全局均值。

### 候选模型

1. **模型 A：纯一米体素面**
   - 当前 CPU DDA 命中面；
   - 方块中心/整数边界的一阶 image-source delay；
   - 三频带材料先验。
2. **模型 B：体素拓扑 + 连续房间平面**
   - DDA 只确定可见面和阻挡；
   - 由连续 source/listener 坐标与局部平面重新计算传播长度；
   - 不改变材料序列。
3. **模型 C：模型 B + 有限一阶反射候选**
   - 每 source-listener 只保留固定数量、固定距离上限的候选；
   - 以 delay 稳定性、方向分散和能量上限排序；
   - 晚期能量仍交给 EFX/FDN，防止双重计数。

### 必须实现的实验

1. 在 `computational-acoustics-core` 中为 66 个 snapshot/source/mic 运行真实 CPU DDA，
   输出命中面、材料序列、传播长度和预算统计。
2. 对模型 A/B/C 逐条报告：
   - direct 与一阶 echo timing absolute error；
   - 50 ms 内早期能量误差；
   - 三频带 EDT/T20 的场景级误差；
   - 每 tick 射线数、P50/P95/P99 延迟、分配量和 cache hit rate。
3. discovery 上只允许拟合少量有物理意义的参数：
   - 每频带吸声/反射上限；
   - specular/diffuse 混合；
   - early-to-late crossover；
   - temporal smoothing。
4. holdout 只运行一次主判定；失败后新增模型版本，禁止覆盖原结果。
5. 把 CUDA 可行性拆成两个门：
   - 无 `nvcc`：仅验证静态 source/ABI/snapshot contract，不声称运行；
   - 有 `nvcc`：同一 bundle 上逐路径比较 CPU/CUDA hit/material 序列和数值容差，再测
     transfer + kernel + synchronization 总时间。

### 建议验收门

- 连续修正后 21 条 annotation 路径最大 timing residual 不劣于 D115 的
  `5 samples` 基准；
- holdout direct timing 最大偏移不超过实测的 `8 samples` 量级；
- 不允许以材料系数补偿离散几何误差；
- 生产 tick 线程只提交 snapshot，不执行无界射线循环；
- CPU 路径是正确性基线，CUDA 缺失或失败时结果语义不改变；
- 只有 holdout、运行预算和离线 loopback 都通过后，才讨论 production 参数变更；
- 未经操作者明确授权，不打开物理录音端点。

## 最终决策

D115 接受 dEchorate 66 条 SOFA 为真实波形研究基准，接受连续 shoebox 几何作为一阶反射
timing sanity check，接受 reflective facet count 对三频带衰减的场景级约束；拒绝 SOFA
`RoomDescription`、拒绝用一米体素直接生成精确早期反射时序、拒绝从单面 echo 窗直接拟合
通用材料幅度，并拒绝将家具状态解释为已识别参数。

D116 应优先实现并比较真实 CPU DDA 的模型 A/B/C。CUDA 仍是可选加速研究，而不是
Minecraft 声学正确性的依赖。
