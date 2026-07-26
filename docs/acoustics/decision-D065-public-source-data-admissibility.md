# D065 — 公开声源数据的准入审计

状态：**implemented; no public dataset currently release-profile eligible**
（审查截止 2026-07-24）。

## 结论

本轮审查没有找到一套可以直接生成本项目 **5 英寸三叶 FPV、可商业发布**
`AcousticProfile v1` 的公开数据。这里的“没有”是按 D057–D061 当前证据契约推导
出的工程结论，不是声称互联网中绝对不存在此类数据。

公开数据仍然非常有用，但必须按用途分层：

1. **声源结构/指向性**：Bristol、NEAPTIDE、CIRA、Recherche Data Gouv；
2. **实飞传播**：NASA Small UAS Flyover、DroneNoise v3；
3. **听感、检测与麦克风布局**：DroneAudioSet、Glasgow、DDL；
4. **可发布 5 英寸 profile**：仍需本项目自采的校准压力、同步 tach/ESC telemetry
   和独立 holdout。

机器可读审查结果位于
[`public-source-data-admissibility-v1.json`](public-source-data-admissibility-v1.json)。
`docs/scripts/verify_public_source_data_registry.py` 会从硬门槛重新计算 eligibility，
不能仅修改一个布尔值绕过证据要求。

## 硬门槛

一个公开数据集只有同时满足以下九项，才允许标记
`release_profile_eligible=true`：

| 门槛 | 为什么是硬条件 |
|---|---|
| public raw time history | 当前 analyzer 需要原始 PCM/压力时序来做校准、Welch、背景扣除和逐阶次 prominence |
| absolute pressure calibration | 没有 Pa/FS 或可复现校准链，只能比较相对频谱，不能声称 1 m 声级 |
| synchronized measured RPM trace | throttle、文件名 RPM 或从 BPF 反推 RPM 都不能检验稳态性、slip 和独立阶次 |
| microphone geometry | 距离归一化和 rotor-disk directivity 需要位置/角度 |
| propulsion metadata | blade count、桨型、电机/极对数和整机布局决定 BPF、电气阶次及安装效应 |
| background/noise floor | 不扣背景就会把房间/风/DAQ noise 当成 broadband rotor energy |
| independent holdout possible | profile 不能用参与拟合的同一 recording/maneuver 自证准确 |
| commercial redistribution compatible | 研究许可不能自动变成可随 mod 发布的参数或资产许可 |
| target 5-inch FPV domain | 大型 DJI、孤立双叶或 F450 数据不能被重命名为目标三叶竞速机实测 |

注意最后一项不是说跨域数据“无效”，而是禁止将其 provenance 冒充成目标整机。
跨域数据可以验证公式、趋势和传播；目标 profile 仍必须由目标硬件闭环。

## 逐数据集判定

### Bristol 参数化孤立桨

[University of Bristol 数据库](https://doi.org/10.5523/bris.29vtf33wbb4tu2vpxz3es8pilv)
提供约 16.1 GiB 数据，工况层级覆盖 blade number、diameter、pitch、RPM 和
inflow。它最适合离线研究“桨参数如何改变 tonal/broadband”，也是 order-source
结构的重要交叉验证。

不能用于发行 profile 的原因是 Non-Commercial Government Licence、目标域不符，
并且当前公开层级中的 operating-point RPM 不能替代 analyzer 所需的同步 tach
trace。只允许选择性下载，不能把原始或派生 profile 打包进商业兼容发行物。

### NEAPTIDE 与 DroneNoise v3

[NEAPTIDE](https://zenodo.org/records/10512044) 已证明不同 airframe/propeller
的低频指向性不能共享；它保留为 CC BY-NC 的研究-only directivity evidence。
[DroneNoise v3](https://salford.figshare.com/articles/dataset/DroneNoise_Database/22133411)
适合校准 flyover、多麦克风一致性和动态听感。二者都没有同步实测 rotor RPM，
也不是目标 5 英寸三叶整机，因此不进入 release profile fitter。

### NASA Small UAS Flyover

[NASA 数据集](https://data.nasa.gov/dataset/small-uas-flyover-acoustics-data) 的
[数据说明](https://data.nasa.gov/docs/datasets/rfk401li/Data_Description_20160203.pdf)
明确给出：

- `incident_pascals` 校准声压；
- microphone NED geometry；
- UTC 对齐的 RTK/GPS 与 roll/pitch/yaw；
- AP Hill 的气象数据以及已记录的 DAQ noise-floor 差异；
- 3DR Y6、DJI Phantom 2 等多旋翼实飞。

这使它成为当前最强的公开 **传播/飞越** validation 候选之一，但 vehicle structure
没有 rotor RPM，catalog 又未给出明确许可证。结论是：可核对距离、姿态、多麦克风
和传播，不可拟合或分发目标声源 profile。

### DroneAudioSet

[NeurIPS 2025 论文](https://proceedings.neurips.cc/paper_files/paper/2025/file/adececbc73a58724db0f3c0d5c77f338-Paper-Datasets_and_Benchmarks_Track.pdf)
及其 [MIT 数据发布](https://huggingface.co/datasets/ahlab-drone-project/DroneAudioSet)
包含 23.5 h、多种 throttle、F330/F450、上/中/下麦克风和 indoor environments。
论文报告的代表性 fundamental 为 156–259 Hz，并给出 1 m dBA 统计，所以它很适合
检查 ego-noise、麦克风位置与 throttle 类别的频谱趋势。

它不满足 source-profile gate：

- 发布数据经过 offset correction 并从 48 kHz 重采样到 16 kHz；
- 论文明确说明 raw data 未公开，可按具体用途联系作者；
- 只有 throttle 类别及从频谱观察到的 fundamental，没有同步 measured RPM；
- 论文的汇总 SPL 不能恢复每条发布录音的 Pa/FS 校准链；
- 两套平台均非目标 5 英寸三叶 FPV。

因此不能把 168/235/156/259 Hz 直接写成目标机的 BPF 参数。

### Glasgow 与 DDL

[Glasgow Drone Authentication](https://doi.org/10.5525/gla.researchdata.1348)
公开约 2.9 GB raw WAV 与模型/状态/距离标签；[DDL](https://zenodo.org/records/6459183)
公开 DJI Mini 2/Phantom 4 的真实与合成检测/定位数据。它们可用于分类、距离/方位
sanity check 和听感对照，但缺少 absolute calibration、同步 RPM 与完整 propulsion
metadata，不能送入严格 analyzer。

### CIRA MATIM 2025

[CIRA 论文](https://doi.org/10.3390/aerospace12070647) 是目前参数完整度最高的
候选之一：

- 3000–8000 RPM；
- 10 个校准 1/2-inch microphones、50 kHz 同步采样；
- thrust、torque、shaft speed 同步获取；
- isolated rotor、plate-installed rotor、four-rotor assembly；
- 角向 directivity 与 `L/D≈0.9–10.4` radial decay；
- motor、propeller、installation contribution 分离。

它直接证明“孤立桨 profile + 简单乘以四”不足：plate 会重排 BPF directivity，
四旋翼还会 broaden harmonic lines。该结果支持 runtime 将 airframe installation
effect 放进 profile，而不是只按 blade diameter 分类。

当前 blocker 是论文的 Data Availability Statement 仅说明 raw data 可向作者申请，
没有公共下载与 raw-data reuse terms；平台又是 Intel Aero-derived two-blade，而非
目标三叶竞速机。下一步应申请：

1. raw pressure/tach/load time histories；
2. calibration constants、background runs 和 exact microphone coordinates；
3. author-recommended train/holdout split；
4. 是否允许公开发布派生 reduced-order coefficients。

收到文件和书面条款前，登记表保持 `public_raw_time_history=false` 与
`commercial_redistribution_compatible=false`。

### 2025/2026 trailing-edge serration 数据

[Recherche Data Gouv 数据集](https://doi.org/10.57745/L8MNF5) 采用 Etalab Open
Licence 2.0，公开 RPM-indexed thrust/torque、CAD 和 13 个麦克风的 acoustic
autopower。麦克风距轴 1.62 m，覆盖 rotor plane `+60°` 到 `-60°`、10° 间隔；
autopower 单位 Pa²、频率分辨率 3.125 Hz，来自 16 s pressure signal 的
Hann/50% overlap 处理。

这是很好的 **spectral validation** 数据，却不是 raw-recording input：公开产品是
Pa²-frequency autopower，不含原始 16 s pressure waveform 或同步 tach trace，也
未记录独立 background product。后续可增加一个与 release fitter 隔离的
`spectral-reference` adapter：

- 保留 published bin width、window、overlap、distance、angle 和 license；
- 只比较 BPF harmonic level、spectral slope、RPM trend 与 directivity；
- 禁止补造 phase、AM/FM、transient 或 PCM；
- 输出永远是 validation report，不是 `AcousticProfile v1` evidence。

该 adapter 已于 2026-07-25 按 D066 实现并在一个真实 4.3 MB HDF5 上执行。
它强制关闭 release/PCM/profile-fitting 开关，并明确标记 ±60° 数据向 90° axis
的外推；详见
[`decision-D066-processed-autopower-spectral-reference.md`](decision-D066-processed-autopower-spectral-reference.md)。

## 可执行研究顺序

### P0 — 自采目标 5 英寸 profile

沿现有 §12.7 capture protocol 获取 5-inch/3-blade/2207-or-2306 的 calibrated
24-bit WAV、同步 optical tach 或 ESC eRPM、thrust/current/voltage、背景、motor-only
和 prop-on。先 steady RPM/angle split，再采 ramp、gust、damage/wet。完成条件仍是：

- analyzer 的 SNR、tone prominence、RPM CV gate；
- order fitter 的 unseen-recording holdout；
- profile fitter 的 unseen-RPM 与 unseen-angle `<=3 dB`；
- Java strict decoder 和 runtime spectral golden；
- 原始数据不入 git，manifest/hash/license/provenance 入 git。

### P1 — 两条公开证据并行推进

1. 向 CIRA 作者申请 raw MATIM time histories 与派生系数发布许可；
2. 已实现只读 spectral-reference adapter，并在 Recherche Data Gouv BP-T 数据
   上验证；下一步扩展到 baseline/serration paired deltas，不改 release eligibility。

### P2 — 传播闭环

选择 NASA Phantom/Y6 中 GPS/RTK 完整的若干 flyover，将 source–mic geometry、
attitude、气象和 calibrated pressure 转为传播测试 fixture。这里只比较相对时间、
距离衰减、姿态趋势和多麦克风一致性；缺 RPM 时不得把 source mismatch 记成
propagation solver 误差。

### P3 — 感知与鲁棒性

DroneAudioSet 用于 microphone-placement/ego-noise；Glasgow、DDL 用于未知录音的
分类与 listening checks。它们不得成为 absolute SPL 或 order amplitude 的真值。

## Agent 执行约束

每次新增或更新公开数据时：

1. 先更新机器登记表，不先下载；
2. 用 primary repository/paper 核对版本、许可、文件类型和 data availability；
3. `python docs/scripts/verify_public_source_data_registry.py` 必须通过；
4. 任何 gate 未满足时，必须列出 blocker，不能手工设 eligible；
5. 原始数据只进 gitignored `external-data/computational-acoustics/`；
6. 论文图上读数必须标记 digitized/inferred，不能标 measured raw；
7. 作者私下提供的数据必须另存许可/邮件摘要和 SHA，不能假定论文 CC BY 自动覆盖
   raw data；
8. 只有全部九个硬门槛通过，才允许调用 release profile pipeline。

## 验证

```powershell
python docs/scripts/verify_public_source_data_registry.py
python -m unittest discover -s docs/scripts -p "test_*.py" -v
.\gradlew.bat --no-daemon acousticResearchCheck
```

登记表测试至少覆盖：

- repository registry schema 与所有 dataset gate 完整；
- 当前 eligible 数量必须为 0；
- 手工把失败数据改成 eligible 会被拒绝；
- 删除任意硬门槛会被拒绝。
