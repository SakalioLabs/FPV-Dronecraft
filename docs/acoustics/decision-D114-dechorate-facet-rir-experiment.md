# D114 — dEchorate 六面体素快照与真实 RIR 最小实验集

## 结论

D114 已把 dEchorate 从“候选数据集”推进为可执行、可复算的实验合同：

- 11 个房间状态全部转换为 `6 × 6 × 2` 的一米体素内部空间与六个有向表面；
- 每个快照包含 120 个表面单元，表面顺序固定为
  `floor, ceiling, west, south, east, north`；
- `0` 固定表示 perforated rock-wool absorptive panel，`1` 固定表示
  Formica reflective panel；
- `020002` 不按六位数直接解释，而严格转换为 `010001 + furniture=true`；
- 家具只有条件标签，没有公开的数量、尺寸和坐标，因此
  `furniture_geometry_known=false`，不得伪造家具 voxel；
- 最小真实波形集固定为 2 个声源 × 3 个距离层 × 11 个房间状态，共 66 条 RIR；
- 4.2 GB HDF5 尚未完整取得，因此当前只承认几何、元数据、提取路径和比较协议，
  不承认任何真实波形指标、材料拟合或发布参数。

决策报告 SHA-256：
`7918da554a71a42e69854dab96ff0ea37e7fe1e8b4300bc694674d7adecc6415`。

独立验证报告 SHA-256：
`ba85cb22a1984a41bd4eaf0bc9adc99b2519c9603316637d00dbe3727196e88a`。

## 官方证据绑定

官方代码仓库固定在提交
`d3e664f1e7a7d46241d7f7b3b3761448686b9537`，并逐文件绑定：

| 文件 | 用途 |
|---|---|
| `dechorate/__init__.py` | 48 kHz、19,556 samples、房间尺寸、房间 code 列表与 code 顺序 |
| `main_build_annotation_database.py` | `floor, ceiling, west, south, east, north` 编码顺序及家具特殊 code |
| `main_build_sound_datasets.py` | `020002 → 010001 + furniture` 的真实录音选择逻辑 |
| `main_estimate_rirs.py` | HDF5 路径、31 列（30 capsule + loopback）、RIR 裁剪和 gzip 存储 |

元数据继续使用 D113 固定的 Zenodo v2 CSV：

- 2,931,172 bytes；
- SHA-256
  `0851ff6c4d0a83248bc1f78cef9b8d2506ad9592b2b3069e430c56dea10850f2`；
- 10,912 行；
- 11 个房间状态，每状态 992 行；
- 30 个有坐标的实体麦克风，另有 id 30 电子 loopback。

RIR 文件的服务器响应已确认：

- 文件名 `dEchorate_rirs_gzip7.hdf5`；
- 4,192,401,641 bytes；
- 发布页给出的 MD5 为
  `8b42a0e99766acce8a100130774bd5b3`；
- HTTP Range 请求返回 `206 Partial Content` 与正确的
  `Content-Range`。

一次只读远程 HDF5 探索发现：当前服务的前 48 字节 superblock 报告的
end-of-address 是 2,048 bytes，而文件实际为约 4.19 GB；强制扩大边界后，
遍历深层 group 又在远端大偏移处遇到无效 symbol-table signature。由于尚未下载
全文件并核对 MD5，这只能记录为“远程按需读取当前不可采信”，不能据此宣称
Zenodo 原文件损坏。D115 必须以完整文件 MD5 或官方重新发布的可分片格式解决此门。

## 一米体素化误差

物理房间为 `5.705 × 5.965 × 2.355 m`。按项目既有的最近整数策略转换为
`6 × 6 × 2`：

| 指标 | 结果 |
|---|---:|
| 物理体积 | 80.141415375 m³ |
| 体素体积 | 72.0 m³ |
| 体积相对误差 | −10.1588% |
| 物理总表面积 | 123.02635 m² |
| 体素总表面积 | 120.0 m² |
| 总表面积相对误差 | −2.4599% |
| floor / ceiling 面积误差 | +5.7880% |
| west / east 面积误差 | −14.5760% |
| south / north 面积误差 | −10.6829% |

这意味着真实 RIR 对比必须至少拆成三个残差：

1. 连续 shoebox/image-source 几何相对真实 RIR 的残差；
2. 一米 voxel 几何相对连续 shoebox 的离散残差；
3. Minecraft 材料、散射和 late-reverb 模型的剩余残差。

禁止把三者全部吸收到一个“最佳吸声系数”中，否则会用材料参数补偿几何取整。

## 66 条 RIR 的确定性选择

只使用元数据中的 0-based id；HDF5 dataset 路径为
`/rir/{room_code}/{source_id}`，麦克风 id 就是矩阵列号。

### 声源 4：早期反射标注兼容

- 类型：`invdirectional`；
- 坐标：`[1.6330509, 0.6820041, 1.16493109] m`；
- 属于官方 SOFA 构建逻辑中具有 echo timing 的前六个声源；
- 距离 rank 1：mic 10，1.193157364 m；
- 距离 rank 15：mic 19，2.700565952 m；
- 距离 rank 30：mic 20，3.948538590 m。

### 声源 6：全向对照

- 类型：`omnidirectional`；
- 坐标：`[3.651, 1.004, 1.38] m`；
- 距离 rank 1：mic 14，1.352350080 m；
- 距离 rank 15：mic 29，2.681217777 m；
- 距离 rank 30：mic 0，4.027084906 m。

30 个麦克风是偶数集合，因此“中位距离”固定使用 lower median，即 rank 15；
排序键固定为 `(distance, microphone_id)`。该规则不读房间目标、不读 RIR
波形，也不因实验结果更换位置。

选择两个声源而不是只选全向源的原因是：

- source 4 提供早期反射时序的可标注对照；
- source 6 更接近不把扬声器方向性混进房间衰减的基线；
- 两者共同区分“房间模型失配”与“声源方向性失配”。

## D115：Codex Agent 可直接执行的下一阶段

### 数据门

1. 下载完整 `dEchorate_rirs_gzip7.hdf5` 到 gitignored external-data；
2. 支持断点续传，并在完成后核对：
   - bytes = `4,192,401,641`；
   - MD5 = `8b42a0e99766acce8a100130774bd5b3`；
3. 若 MD5 不符，停止波形分析，保存响应头、文件 hash 和失败报告；
4. 若官方提供 SOFA 或重新打包版本，优先选择可逐文件验证的 66 条子集，
   但必须继续绑定同一 66-entry manifest。

### 波形提取

1. 只读打开 HDF5；
2. 验证根路径、11 个 room code、source 4/6、shape
   `[19556, 31]`、48 kHz；
3. 按 manifest 提取 66 条实体麦克风列，拒绝 loopback column 30；
4. 每条输出 `.npy` 或无损 WAV，并生成 SHA-256 sidecar；
5. 记录原 dataset path、column、source/mic 坐标、距离 rank 和 room facet code。

### 三模型对比

对每一条 RIR 同时生成：

1. 连续 shoebox image-source 一阶路径；
2. 当前 CPU voxel DDA 的同几何结果；
3. 与 CPU 使用同一 snapshot/expected-results contract 的 CUDA DDA
   （有 `nvcc` 后才执行，不得以静态源码审计冒充运行）。

CUDA 只负责批量射线/路径归约；最终 OpenAL EFX 或 Java FDN 仍是实时渲染层。
不要让 GPU 路径引入另一套材料表、坐标约定或浮点容差。

### 指标

逐 RIR 报告：

- direct-arrival sample error（相对 `distance / 346.98 × 48000`）；
- source 4 的六个一阶反射 timing residual；
- octave-band EDT、T20、T30；
- 0–80 ms early energy、80 ms 后 late energy 与 early/late ratio；
- measured ↔ continuous image source；
- continuous image source ↔ one-metre voxel；
- CPU DDA ↔ CUDA DDA。

聚合必须按 room code、source role、distance rank 分层；禁止只报告一个全局平均值。

### 接受门

- CPU/CUDA path contract：完全相同的 hit/material 序列，数值误差另设容差；
- direct timing：先报告实测分布，再在 discovery/holdout 分离后定阈值；
- early reflection：不得用同一 66 条数据同时调参和验收；
- RT 指标：至少把 11 个 room code 分成 discovery 与 holdout，家具状态
  `020002` 必须保留为独立 holdout；
- 在真实波形 gate 通过前：
  `production_material_fit_eligible=false`、
  `production_change_required=false`。

## 实时 Minecraft 研究方向

D114 支持继续采用分层混合模型，而不是单一“万能算法”：

- 直达声与遮挡：CPU voxel DDA，固定 tick budget，并对参数做平滑；
- 早期反射：有限阶 image-source/voxel ray，优先保留到达时间和方向；
- 晚期混响：OpenAL EFX 为支持设备上的低成本路径，Java 三频带 FDN 为确定性回退；
- 开放区：直达声、空气吸收、地面/墙面少量反射，禁止强行套室内 RT60；
- 门洞/多房间：portal/A* 传播与 listener-shared reverb history；
- CUDA：离线批量校准、快照烘焙或高端可选加速，不作为所有玩家的正确性依赖。

本轮没有打开物理输出端点，没有采集麦克风或声卡输出，也没有进行 ABX。
