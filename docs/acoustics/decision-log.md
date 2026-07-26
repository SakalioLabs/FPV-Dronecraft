# Computational Acoustics Decision Log

本文件记录实现阶段的可推翻技术决策。论文数据、实测数据和许可仍以主研究计划的
证据标签与数据台账为准。

## D001 — 纯 Java 核心边界

- 状态：Accepted（2026-07-23）
- 决策：新增 `computational-acoustics-core`，不得依赖 Minecraft、Fabric、Mojang
  或 LWJGL 类型。
- 理由：声源、传播、标定和 accelerated backend 必须共享同一组可离线复现的契约。
- 验证：模块含依赖边界测试；Fabric 只负责采集快照与渲染。

## D002 — 程序化 PCM 使用 Fabric 官方流式扩展点

- 状态：Implemented for prototype（2026-07-24）
- 决策：在 Fabric 侧通过
  `net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance#getAudioStream`
  返回自定义 `AudioStream`，对应 sound event 使用 streaming 资源。
- 本地证据：Fabric API 0.141.4+1.21.11 所带 `fabric-sound-api-v1` 的
  `SoundEngineMixin` 将 vanilla `SoundBufferLibrary#getStream` 重定向至上述方法；
  Minecraft 1.21.11 的 `AudioStream` 公开 `getFormat()` 与 `read(int)`。
- 影响：无需为首版程序声源访问私有 `SoundEngine`、`ChannelAccess` 或 OpenAL
  source id；设备重载和声道生命周期继续由游戏管理。
- 限制：EFX per-source filter/aux-send 不在该接口中，必须单独做 capability probe
  和最小 accessor，失败时保持稳定的无 EFX 路径。
- 实现证据：连续 motor/propeller 资源声明为 streaming；
  `DroneLoopSoundInstance` 返回 48 kHz mono PCM16 `AudioStream`；系统属性
  `fpvdrone.proceduralAudio=false` 可退回原 OGG。
- 上游参考：
  [Fabric API repository](https://github.com/FabricMC/fabric-api)；
  [Fabric sound API package](https://maven.fabricmc.net/docs/fabric-api-0.100.3%2B1.21/net/fabricmc/fabric/api/client/sound/v1/package-summary.html)。

## D003 — CPU DDA 是规范实现，CUDA 是可选批处理实验

- 状态：Accepted（2026-07-23）
- 决策：直达声和遮挡默认使用 Java CPU Amanatides–Woo DDA。CUDA 只在早期反射形成
  足够大的射线批次且基准证明净收益后启用，不成为运行必需项。
- 理由：
  1. 当前上限约为 6 个活跃无人机，每个声源少量直达/绕射探针、20–40 Hz 更新；
     这种规模通常由 kernel launch、JNI 和同步开销主导。
  2. DDA 路径长度和分支不同，会产生 SIMT warp divergence；只有大量、驻留 GPU 的
     体素与结构化批次才有吞吐优势。
  3. 当前 LWJGL 运行时未包含 CUDA binding；CUDA 还引入 NVIDIA、driver、native
     打包与跨平台回退成本。
- CUDA 实验 gate：
  - CPU 路径已经满足确定性和 golden trace；
  - 同一体素快照常驻 GPU，只上传 chunk delta；
  - 从 `>=8192 rays/update` 开始做批次扫描（阈值是待测假设，不是产品常量）；
  - 比较 CPU、可选 OpenGL compute 与 CUDA 的端到端 P50/P95，包括 JNI、map/unmap
    和同步，而非只计 kernel；
  - 每条路径的命中、首障碍、步数和能量结果在规定误差内匹配 CPU reference。
- 官方依据：
  [CUDA SIMT/warp divergence](https://docs.nvidia.com/cuda/cuda-programming-guide/01-introduction/programming-model.html)；
  [CUDA–OpenGL interoperability](https://docs.nvidia.com/cuda/cuda-programming-guide/04-special-topics/graphics-interop.html)；
  [CUDA Runtime OpenGL API](https://docs.nvidia.com/cuda/cuda-runtime-api/group__CUDART__OPENGL.html)。
- 实施证据（2026-07-24）：CPU segment DDA、稀疏材质快照、单 worker latest-wins
  batch scheduler 与三频带透射已接入；i7-14700KF 上 8192-ray batch 的完整 Java
  reference 为约 1764 ns/ray。当前最多 6 架无人机、每架 5 条 aperture rays，
  合计约 30 条直达射线，继续使用 CPU。
- 本机 CUDA 审计（2026-07-24）：RTX 3060、12 GB、compute capability 8.6、
  driver 610.47 可见，但 `nvcc` 不存在；因此只能证明硬件可行，不能证明 kernel
  或端到端收益。完整 C0–C4 parity/benchmark 规范见
  [`cuda-voxel-dda-feasibility.md`](cuda-voxel-dda-feasibility.md)。

## D004 — CUDA parity oracle 与产品 DDA 热路径分离

- 状态：C1 partial（2026-07-24）。
- 原因：现有 `DirectPathSolver.Result` 只有聚合 band loss/cell count，不足以定位
  GPU traversal mismatch；直接让产品结果携带 segment list 会给每次更新增加分配。
- 实现：独立 allocation-heavy `DdaParityOracle` 复用规范 `VoxelDda.walk`，输出
  每个 cell、物理段长、material id、首非空气 cell、visited count、completion、
  stopped/truncated flags；产品仍直接使用无列表的 `VoxelDda`/`DirectPathSolver`。
- 验证：4 个测试覆盖 axis 段长/首材料、exact-corner tie、budget truncation 和
  负 material id。随后新增 schema-v1 big-endian corpus、deterministic
  adversarial/random generator、bounded reader、disk round-trip 重算和 SHA-256；
  另 4 个测试覆盖 deterministic、round-trip、corrupt magic 和 signed coordinate
  material field。最新 XML 为 `108` tests、0 failures/errors。
- band parity：schema-v1 还固定 9-entry synthetic material table 与其 SHA-256，
  并保存每条 ray 的 low/mid/high loss 和 energy gain；reader 同时重算 traversal
  与 band accumulation。material table SHA-256 为
  `e165344c00540668c2afcbec0ac617eddc14e4f37454b7a17cd68c867440b76d`。
- smoke：`72` rays、`8558` segments、`214464` bytes，SHA-256
  `991ad38338f86841905cd52223fe034aaf09ebf8734c03f5dd4282f69609bed4`；
  CLI 明确报告 `round_trip=true`。streaming writer 与 in-memory writer
  byte-identical；core 重跑 `BUILD SUCCESSFUL`，`109` tests、0 failures/errors。
- 100k gate：streaming CLI 生成 `100008` total rays、`12617210` segments、
  `315354012` bytes，wall `7.483 s`，round-trip true，无残留 `.tmp`，SHA-256
  `37532b76cc41fac515b08b86ecd19e5361a1b9eb5129f515c7f899948324344b`。
  315 MB build artifact 被忽略，不提交仓库。
- 边界：synthetic material field 不是 Minecraft snapshot；尚无 production
  snapshot/material-table hash 或 CUDA reader，故 C1 仍是 partial；100k synthetic
  CPU corpus 与 heap 风险已经关闭。

## D005 — 五探针部分遮挡与显式 Doppler

- 状态：Implemented for prototype（2026-07-24）
- 决策：聚合声源使用中心与四个 acoustic-aperture rim probes，按能量加权路径增益；
  不采用 `min loss`、`max loss` 或单中心射线。
- aperture：服务器由 rotor center 到机体中心的水平距离加 prop radius 得到，客户端
  同步使用；这比用单个 prop radius 更接近整架四旋翼/X8 的辐射范围。
- Doppler：程序化 tone 直接乘经典 moving-source/moving-listener ratio，OpenAL
  source pitch 保持 1.0，避免重复处理。teleport/outlier 使用可审计的稳定限幅。
- 限制：能量平均隐含多旋翼路径不完全相干，后续真实录音若显示稳定相干干涉，需要
  在低阶 BPF 上引入复数相位路径聚合。

## D004 — OpenAL 原生能力分层

- 状态：Accepted（2026-07-23）
- 基础层：Minecraft/Fabric 的 position、attenuation、streaming channel 和设备重载。
- 增强层：检测到 EFX 后才启用低通/带通、reverb auxiliary send 与空气吸收；任何
  初始化或 context 变化都立即退回基础层。
- HRTF：视为 OpenAL Soft 设备/上下文能力，不把“已安装 OpenAL”误判为“已启用 HRTF”。
- 官方依据：
  [LWJGL EXTEfx](https://javadoc.lwjgl.org/org/lwjgl/openal/EXTEfx.html)；
  [LWJGL OpenAL package](https://javadoc.lwjgl.org/org/lwjgl/openal/package-summary.html)；
  [LWJGL AL capabilities](https://javadoc.lwjgl.org/org/lwjgl/openal/AL.html)。

## D006 — 经验 A* 只作为绕射对照基线

- 状态：Implemented for prototype（2026-07-24）
- 决策：直达中频能量低于 `0.92 [H]` 时，在主线程捕获半径 `3 blocks [H]`
  的局部空气走廊；worker 在 4096-node 上限内运行确定性 6-neighbour A*。
- 声学响应：由额外路径长度与转向数产生三频带经验损失；所有系数标记为 `[H]`，
  不称为 UDFA、UTD 或论文验证模型。
- 理由：先建立低成本 B 基线，才能量化独立 edge-diffraction C 模型是否带来稳定
  可闻收益；unknown/unloaded cell 不可通行，保持线程安全和保守失效。
- gate：固定 3185-cell 场景 1000 次搜索的 p95 为 312.1 µs、max 1.90 ms，
  通过 worker p95 ≤ 4 ms；三频带传播变化限制为每次 3 dB。
- 后续：实现 connected-air-region/portal abstraction，并在 wall edge、door、
  L corridor、rooms、vertical shaft、cave 场景做 B/C 盲听与录音对比。

## D007 — Portal hierarchy 不替代 cell correctness oracle

- 状态：Implemented for prototype（2026-07-24）
- 决策：8-block `[H]` partition 内分解 connected-air regions，跨 partition face
  聚合 portal aperture；region A* 只选择候选 corridor，最终路径仍由受限 cell A*
  求解，失败自动回退 unrestricted cell A*。
- 理由：region graph 能减少长走廊的 cell expansion，但粗分区可能丢失局部几何；
  correctness fallback 使性能优化不会静默改变可达性。
- 证据：3185-cell 场景 cold hierarchical p95 1.131 ms；warm cache p95
  484.6 µs；单 partition 变化 p95 537.6 µs；平均 cell visit 由 453 降到 229。
- 增量策略：每个 worker-owned drone cache 以 64-bit voxel-state fingerprint 判断
  partition topology 是否复用；窗口平移时保留重叠 partition 并移除离开区域。
- 未完成：portal adjacency 目前仍从当前局部快照重新组装，但不读取世界。

## D008 — UDFA 先作为频域 oracle，不直接进入产品

- 状态：Reference implemented（2026-07-24）
- 决策：只依据 2023 论文 Eq. (2)-(6) 独立实现无限楔 two-term reference；不读取
  MATLAB toolbox 源码。
- `[P]` 参数：`alpha=0.5, b=1.44, Q=0.2, r=1.6`。
- gate：voxel edge geometry、zone classification、time-varying IIR、有限楔/多边缘和
  实测对比全部通过后，才允许替代经验三频带产品基线。
- 证据：knife-edge Eq. (4)、DC、频率单调性、径向互易和 `-3 dB/octave`
  高频渐近测试。

## D009 — Voxel edge 提取必须由已知 solid 支撑

- 状态：Implemented for geometry metadata（2026-07-24）
- 决策：对简化后的 cardinal air path，最多按路径顺序提取 3 个主转折 `[H]`；
  只有内侧对角 cell 已知且不可通行时才产生 edge candidate。
- 输出：block-edge apex、signed unit edge axis、incoming/outgoing direction、air turn
  与 obstacle cell，可用于后续 zone/wedge geometry 推导和 debug overlay。
- 理由：单凭 A* 转向会把开放空间中的等价最短路转角误判为物理绕射边；unknown
  chunk 也不能被推断为 obstacle。
- 限制：当前只提取几何 metadata，经验 B 仍是产品响应；外楔角、azimuth、
  incidence angle 和 UDFA zone 尚未求解。

## D010 — UDFA IIR 用稳定 filter crossfade 更新

- 状态：Reference implemented（2026-07-24）
- 决策：90° solid block 映射为 `3π/2` exterior wedge；无限 edge apex 由最短折线
  条件解析求解。四个一阶 shelf 依据论文 Eq. (18)-(20) 设计。
- 时变策略：不逐系数插值；旧/新两个稳定 cascade 并行处理并做 sample crossfade，
  过渡结束后接管已预热的新 filter state。
- 工程适配 `[H]`：设计带宽上限 `0.475 fs`，overall gain 在 20 Hz-16 kHz 做
  log-frequency RMS dB 配准。
- 证据：四组 1-36 m 路径/0.15-1.50 rad bending 的 RMS 为
  0.110-0.173 dB，max abs 0.318-0.448 dB；四 shelf 约 7.78 ns/sample。
- zone：按论文 Section II 的 `theta_s + pi` shadow boundary 以及两条 reflection
  boundary 输出 direct/reflection flags，边界属于 direct visible。
- 限制：仍是 oracle/IIR reference，未接入产品 audio stream；本决策当时尚未覆盖
  finite edge 与多 edge，相应实现见 D011；ABX 尚未完成。

## D011 — Tonal 相干、broadband 非相干

- 状态：Reference implemented（2026-07-24）
- finite edge：按论文 Eq. (10)-(17) 实现 inside/outside apex、两个 half-wedges、
  first-order off-edge branch 与四次方 blend。
- 连续性：apex 跨一格 edge 端点的 1 kHz / 0.005 m sweep 最大变化
  `0.054580 dB`，通过 3 dB gate。
- 多路径：BPF/电机 tonal components 以 complex pressure 和相对传播 phase 相加；
  broadband 路径以 energy 相加。
- 理由：稳定 tones 会产生可听相位干涉；随机宽带若强制相干会产生不可靠的固定
  comb filtering。
- 限制：BRAS RS5 candidate golden 只覆盖无限长厚屏顶边，尚无 BTMS/有限边
  golden 证明 finite-edge 绝对误差；高阶 cascade 只是接口能力，不冒充完整
  higher-order diffraction。

## D012 — BRAS RS5 否决单个理想 knife-edge 直接接入

- 状态：Candidate golden established（2026-07-24）
- 数据：BRAS Reference Scene 5，CC BY-SA 4.0；原始归档 SHA-256
  `b9cb03c945fcf46bf742135108d1acc5246b576e8afb39090547e0f3093f58b5`，
  raw ZIP/WAV/SOFA 不进入仓库。
- 方法：用同距离可见 `LS3-MP4` 归一化阴影 `LS1-MP1`，截取首次到达 1 ms；
  几何额外时延预测 0.656 ms、实测 0.703 ms，误差约 2.07 samples。
- 结果：发布参数的理想 `2π` knife-edge 在 1/2/4/8/12 kHz 相对实测少衰减
  8.284/4.317/6.787/5.166/3.143 dB，RMS 5.829 dB。
- 决策：不把单个理想 knife-edge 原样接入产品；下一候选是 25 mm/voxel 厚屏的
  双 `3π/2` edge sequential/coherent path 模型。
- 暂定 gate：跨多个 RS5 阴影深度，1-8 kHz 平均 RMS `<=3 dB`、单点
  `<=6 dB`，并继续满足 `<3 dB/update` 连续性，才进入项目自采 RIR 与 ABX。
- 限制：RS5 有硬地板、设备方向性和有限门控误差；它是 candidate golden，
  不是对 UDFA 论文或产品质量的最终判决。

## D013 — 2024 双边 UDFA 保留为 reference，不接产品

- 状态：Reference implemented, product rejected pending BTMS（2026-07-24）
- 依据：Kirsch and Ewert, Acta Acustica 8 (2024), Eq. (8)-(12)，CC BY 4.0；
  只从论文公式独立实现，不读取 UDFA toolbox 源码。
- 实现：single-term Eq. (5)、modified wedge angle、两个 reciprocal
  two-term × single-term contributions、cutoff-weighted complex mixing。
- 正确性：零宽度 knife-edge 极限、reciprocity、`-6 dB/oct` 渐近与独立 Python
  oracle 通过。
- BRAS 结果：LS1-MP1 五点 RMS `10.284 dB`，高于单边 `5.829 dB`；1 kHz
  接近实测，但 2-12 kHz 过度衰减 7.4-16.1 dB。
- 性能：frequency-domain geometry oracle `1376.804 ns/evaluation`，为单边
  `4.145×`；20 Hz/voice 更新不是瓶颈，但该数值不代表实时 IIR sample cost。
- 决策：不为单测点调参，不接入 audio stream；先建立 BTMS reference 和
  deconvolution-aware 全 16 路径处理，再决定 single/double crossfade。

## D014 — 精确 BTMS 验证 UDFA，无限楔实测差异归入测量链

- 状态：Reference implemented（2026-07-24）。
- 依据：Calamia 2009 thesis Eq. (3.4)、(3.8)、(3.15)-(3.16)；实现复数
  phase-adaptive Gauss–Legendre 半无限积分，没有读取任何 toolbox 源码。
- 独立核验：复现 thesis 示例 10/100/1000 Hz 数值，并逐点匹配独立 SciPy
  oracle；BRAS 12 kHz 的 reference/strict 积分设置差异 `<0.001 dB`。
- 结果：BRAS 理想 `2π` knife-edge 上，2023 UDFA 相对精确 BTMS 的五点 RMS
  仅 `0.0905 dB`，而同一 UDFA 相对 1 ms 实测门控为 `5.829 dB`。
- 决策：不再把该 5.829 dB 差异解释为无限楔 UDFA 近似误差，也不据此拟合
  filter 参数。BRAS 保留为端到端 candidate golden；下一步优先解决反卷积感知
  门控、设备方向性与真实 25 mm 双边屏 reference。
- 运行边界：BTMS 数值积分只作离线 oracle；实时路径仍以 UDFA/IIR 或经验基线
  为候选。angular kernel 的精确 zone-boundary 奇点在加入解析极限前显式拒绝。

## D015 — BRAS 全矩阵短门频谱只作 diagnostic

- 状态：Accepted（2026-07-24）。
- 官方证据：RS5 RIR 使用 Genelec 8020c、G.R.A.S. 40AF、swept sine 与
  spectral-division deconvolution；单声道链路按 Pascal 绝对校准。原始
  sweep/inverse filter 未随场景包发布。
- 新实现：从 3.32 GB 官方 Genelec ZIP 以 HTTP Range 只提取 29.6 MB MPS MAT；
  对全 16 路径加入 source orientation correction、几何 anchored gate 和
  learned direct-energy template timing diagnostic。
- 结果：方向性校正使 LS1-MP1 single-edge RMS 从 `5.829` 降到 `5.077 dB`，
  double-edge 从 `10.284` 升到 `11.020 dB`。全 16 matched timing RMS 从
  `0.806` 降至 `0.391 ms`，但标准高度 max 从 `0.385` 增至 `0.487 ms`。
- Control failure：五条 direct 路径短门校准除 4 kHz 外均超过暂定误差门槛；
  1 kHz RMS/max 达 `6.368/12.954 dB`。因此 shadow 频谱不能被提升为绝对 golden。
- 决策：BRAS 继续作为 geometry/timing golden 和 end-to-end diagnostic；
  产品算法选择必须先有 25 mm two-edge 独立 reference，或完整复现 source +
  floor + transmission + diffraction 测量链。禁止为短门单点拟合 UDFA 参数。

## D016 — MDF mass-law 传输不是 RS5 主误差来源

- 状态：Reference implemented（2026-07-24）。
- 官方参数：`mat_MDF25mmB_plane` density `742.4 kg/m³`、thickness `25 mm`、
  surface mass `18.56 kg/m²`；tile floor absorption 在目标频带约 `0.02-0.03`。
- 实现：新增 normal-incidence `LimpMassPanelTransmission`，明确作为 surface
  boundary reference，不混入 `AcousticMaterial` 的 per-metre bulk loss。
- 结果：MDF pressure transmission 在 1/2/4/8/12 kHz 为
  `-42.996/-49.017/-55.037/-61.058/-64.580 dB`。相对实测绕射，完全同相的
  transmission 在 1 kHz 也只会改变约 `0.55 dB`。
- 时序：LS1-MP1 第一条 tile-floor + double-edge 路径晚 `3.929 ms`，解释了
  4 ms gate 的敏感性，但不污染 1 ms gate。
- 决策：不再把高频 5 dB 级差异归因于 MDF 直穿声；完整 RIR 模型必须相干加入
  floor interaction，而短门模型继续专注双边绕射与测量 packet。

## D017 — 相干 floor/transmission 预算不能替代独立双边 reference

- 状态：Reference implemented, product rejected（2026-07-24）。
- 实现：通用 planar double-edge geometry、两条互易 tile-floor 镜像路径、
  Genelec complex directivity、相对传播相位和 limp-sheet transmission。
- 正确性：对称 floor 路径长度与 UDFA 幅度互易；相对主双边路径晚
  `3.928846 ms`；既有 LS1-MP1 五频点 oracle 保持不变。
- 整段 RIR RMS：主双边 `11.118 dB`，加入 floor `11.103 dB`，再加入
  transmission `11.545 dB`。新增路径没有稳定解释测量差异。
- 可辨识性：transmission 比双边绕射早约 `0.729 ms`，小于约 2 ms 的
  spectral-division packet 宽度；没有 raw sweep/inverse filter 时不能可靠分离。
- 决策：停止扩展 RS5 经验路径以追逐整段 RIR。下一 gate 必须来自无 chamber
  residual 的 25 mm two-edge wave/BTMS reference，或项目自采且保留原始激励链的
  测量。

## D018 — 双边缘产品 gate 采用独立波动求解，不用论文曲线描图

- 状态：Accepted, implementation pending（2026-07-24）。
- 数据边界：2024 高阶 UDFA 论文说明模拟数据可向通讯作者索取，但没有公开数值附件；
  未获数据与再分发许可前，不把 Figure 8/9 的像素描图当作测试 golden。
- provenance：继续不读取或复制 UDFA toolbox；同源实现不能充当被测 UDFA 的独立
  oracle。
- 维度修正：普通二维 point injection 是线源，不是 BTMS/Minecraft 的三维点源；
  2D FDTD 只作为离散、边界与吞吐 harness，不直接与 BTMS 幅度比较。
- 决策：产品 oracle 采用轴向空间 Fourier 的 2.5D point-source 重建；每个 `k_y`
  解二维 frequency-domain 问题再逆变换，并分别通过三维自由场、`k_y` 收敛、
  精确无限楔 BTMS 和网格收敛 gate 后，才求解 25 mm 刚性矩形双边缘。
- 带宽：第一阶段只做 1/2/4 kHz；通过 `dx`/`dx/1.5` 收敛且确有模型区分力后再扩展
  8/12 kHz。离线求解器是否用 CUDA 只由端到端吞吐决定，不改变数值定义。
- 明细与阈值：见
  [`two-edge-independent-reference.md`](two-edge-independent-reference.md)。

## D019 — 2D 只作 harness，2.5D 自由场重建通过

- 状态：Transform reference implemented, obstacle solver pending（2026-07-24）。
- 2D harness：自由场时序互易、几何到达时间、刚性厚屏声影和首边界回波
  `<= -50 dB` 通过；1 kHz 的 0.6/1.2 m 幅度比在 `0.25 dB` 内匹配独立
  Hankel oracle。
- 维度证明：三维点源需要全部轴向 `k_y` 分量；`k_y=0` 只对应二维线源。
- 变换：传播段使用 `k_y=k sin(theta)`，evanescent 段使用
  `k_y=k cosh(u)`，两段分别作 Gauss–Legendre 积分，避开临界波数直接采样。
- 结果：1/2/4 kHz、轴向偏移 0/0.5/1.0 m 的九个 free-field case 中，64-node
  最大幅度误差 `0.0000111 dB`、相位误差 `0.0000467°`，64/96-node 最大变化
  `0.00000885 dB`。
- 决策：轴向 quadrature 结构可进入下一阶段；下一 gate 是单个
  `(frequency, k_y)` 的复数 Helmholtz 障碍物求解与空间网格收敛，不能把解析
  free-field transform 的通过误报为双边缘模型通过。

## D020 — 2.5D Helmholtz 单波数求解通过，16 cells/λ 不足

- 状态：Single transformed solve implemented（2026-07-24）。
- 实现：复坐标 PML、五点 conservative flux stencil、外圈 Dirichlet、点源
  `-1/dx²` 和刚性 solid-face zero normal flux；矩阵用 SciPy sparse LU 离线求解。
- 24 cells/λ：1 kHz 传播分支自由场最大 `0.0398 dB / 3.618°`，相对 spreading
  `0.00330 dB`，互易误差 `4.65e-15`；evanescent 分支最大
  `0.00897 dB / 0.000331°`。
- 刚性验证：24 cells/λ 相对解析镜像为 `0.523 dB / 0.0268°`，通过暂定
  `1 dB / 10°` gate；16 cells/λ 为 `1.194 dB / 1.033°`，幅度失败。
- 决策：默认 reference 至少 24 cells/λ，不通过降低采样换取速度。24/32 的直接
  比较暂不宣称空间收敛，因为 cell-centred source 与 solid face 在不同网格上会有
  半 cell 几何偏移；先实现固定物理坐标插值/边界误差核算。
- 限制：这只证明一个 `(frequency, k_y)` 系统；尚未证明几十个轴向波数障碍物解的
  inverse transform、单边 BTMS 或 25 mm 双边模型。

## D021 — 连续坐标与 origin alignment 关闭空间离散 gate

- 状态：Accepted（2026-07-24）。
- 问题：仅按整数 cell index 缩放时，source/receiver 或 rigid face 会在不同
  resolution 移动半格；这种比较混合了 PDE 离散误差与几何误差。
- 实现：任意物理坐标的双线性 source injection 和 receiver interpolation；
  source/receiver 使用同一权重，从而保持离散复数互易。每个离线网格平移 origin，
  使目标刚性面精确位于数值 face，同时保持世界物理坐标不变。
- 32 cells/λ：自由场最大 `0.0251 dB / 2.036°`、evanescent
  `0.00813 dB / 0.000335°`、刚性镜像 `0.218 dB / 0.720°`、互易
  `1.76e-14`。
- 收敛：32/24 固定几何最大变化 `0.161 dB / 1.580°`，通过
  `0.5 dB / 5°`；24/16 刚性幅度变化 `0.646 dB`，失败。
- 决策：32 cells/λ 是当前离线 reference grid，24 cells/λ 必须作为配套收敛对照；
  16 cells/λ 不进入 BTMS/双边判定。下一步才是批量 `k_y` 障碍物 inverse transform。

## D022 — 完整刚性障碍物 2.5D inverse transform 通过

- 状态：End-to-end obstacle transform implemented（2026-07-24）。
- 方法：每个 propagating/evanescent Gauss node 实际组装并求解
  Helmholtz/PML 刚性墙系统，之后才沿 `k_y` 逆变换；不在 integrand 中代入解析
  障碍物解。最终只用三维点源镜像作外部 oracle。
- 节点 gate：8/12 nodes 不收敛；18-node 绝对误差
  `0.063 dB / 0.481°`，24-node 为 `-0.0783 dB / -0.694°`；24/18 变化
  `-0.142 dB / -1.174°`，通过 `0.5 dB / 5°`。
- 成本：24 cells/λ、`42,781` unknowns/system，18/24 合计 84 solves；本机本次
  sparse-LU wall time 约 `101.3 s`。各 `k_y` 独立，适合离线并行，但不是实时
  Minecraft 算法。
- 决策：最低 quadrature 使用 18 nodes/branch，正式对照使用 24；12 nodes
  禁止用于模型选择。下一 gate 是零厚度半平面相对精确 BTMS，而不是直接跳到厚屏。

## D023 — 2.5D 零厚度半平面通过精确 BTMS gate

- 状态：Accepted（2026-07-24）。
- 几何修正：`2π` wedge 的 source/receiver azimuth 必须相对屏幕边界射线；
  正确值为 `5.03414/1.24905 rad`。把 source 单独旋转到 0 会改变相对 wedge
  boundary，制造约 `-3 dB` 假差异。
- BTMS fixture：1 kHz、`rs=rr=0.632456 m`，raw
  `0.0450134595 + 0.2385584689i`，normalized `-10.25497 dB`。
- 2.5D：零厚度屏用 blocked flux faces，保持两侧 fluid；FDFD 的 `exp(+ikr)`
  Green 乘 `4π` 并与 BTMS `exp(-ikr)` 共轭对齐。
- 结果：24-grid/24-node 相对 BTMS `+0.311 dB / +8.930°`；
  24/18-node `-0.292 dB / +0.507°`。既有 32-grid raw solve 换算为
  `+0.382 dB / +6.754°`，32/24 变化 `+0.071 dB / -2.176°`。
- 决策：半平面 wave/BTMS gate 关闭；允许进入 25 mm 双边缘 wave reference，
  但不得把半平面结果外推为厚屏已经验证。

## D024 — 25 mm wave reference 在 1 kHz 否决当前 2024 UDFA

- 状态：1 kHz candidate reference accepted, product model rejected（2026-07-24）。
- 几何：25 mm 刚性矩形 half-plane；粗/细网格以 2/3 cells 精确表示厚度，
  `dx=12.5/8.333 mm`，source/receiver 对称，shortest path `1.289911 m`。
- wave reference：fine raw `0.146882446 - 0.164678953i`，
  normalized `-10.91408 dB`。
- 收敛：fine 24/18-node `-0.1535 dB / +0.514°`，coarse
  `-0.1559 dB / +0.559°`；3/2-cell spatial
  `+0.0387 dB / -2.041°`。全部通过 `0.5 dB / 5°`。
- 2024 UDFA：同几何 `-15.03292 dB`，相对 wave 为 `-4.11884 dB`，
  超过暂定单点 `4 dB` gate。
- 决策：不接入当前双边 UDFA，不调论文参数贴合该点。继续生成 2/4 kHz reference，
  判断误差是稳定偏置还是频率相关；只有多频 gate 通过的新候选才能进入 ABX。

## D025 — 2 kHz wave reference 确认 UDFA 误差随频率扩大

- 状态：2 kHz candidate reference accepted, product model rejected（2026-07-24）。
- 网格：粗/细网格用 4/6 cells 精确表示 25 mm，细网格
  `dx=4.167 mm`、`41.16 cells/λ`、每次求解 `159,313` unknowns。
- 求积审计：18/24-node 变化约 `-4.28 dB / +16.71°`，明确失败；
  32/24 为 `+0.7895 dB / -1.364°`，40/32 为
  `-0.9932 dB / -1.392°`，均未冒充收敛；48/40 最终为
  `+0.0295 dB / +0.678°`，通过 `0.5 dB / 5°`。
- wave reference：fine raw `-0.103464649 - 0.126995266i`，
  normalized `-13.50216 dB`；48-node 的 6/4-cell spatial 为
  `+0.0374 dB / -3.217°`，通过。
- 2024 UDFA：同几何 `-20.11027 dB`，相对 wave 多衰减
  `6.60811 dB`；1/2 kHz 两点 RMS 为 `5.5060 dB`。
- 决策：产品拒绝已不依赖单一频点。4 kHz 仍生成以完成 golden，但先验证
  紧凑 PML 域与嵌套/可缓存 `k_y` 求积；不得用放宽 quadrature gate 降低成本。

## D026 — 固定内部区域、按波长缩放 PML 通过域不变性 gate

- 状态：accepted for 4 kHz reference generation（2026-07-24）。
- 规则：非 PML 区固定为 `x=[0.25,1.75] m`、`z=[0.25,1.15] m`；
  PML 保持 1 kHz 基准的 `0.729 λ`，故 2/4 kHz 厚度为
  `0.125/0.0625 m`。屏幕、source、receiver 与内部传播距离不变。
- 2 kHz 对照：fine 6-cell、48-node 紧凑域相对原大域变化
  `+0.01668 dB / +0.648°`，通过暂定 `0.25 dB / 2°` 域 gate。
- 成本：unknowns `159,313 -> 114,253`（`-28.28%`），总 solve time
  `307.65 -> 188.65 s`（`1.63×`）。
- 决策：4 kHz 可使用同一缩放规则；仍需独立通过 `k_y` quadrature 与
  8/12-cell spatial gate。该规则只影响离线 oracle，不进入 Minecraft runtime。

## D027 — 嵌套 Clenshaw–Curtis 替代重复的非嵌套 Gauss 加密

- 状态：accepted for 4 kHz reference generation（2026-07-24）。
- 原因：Gauss 24/32/40/48 的节点互不复用；2 kHz 已显示非单调收敛，继续提高
  阶数会重复全部稀疏 LU。Clenshaw–Curtis 加倍区间时保留所有旧 cosine nodes。
- 基础检查：24-interval 权重对 `1/x²/x⁴` 的积分为
  `2/2⁄3/2⁄5`，达到浮点精度。
- 2 kHz fine compact 交叉验证：nested 48/24 变化
  `+0.00880 dB / -0.0754°`；相对独立 Gauss-48 为
  `-0.02439 dB / -0.218°`。
- 成本：96 次唯一 Helmholtz solves，复用 50 个 integrand samples；
  一次 refined run 同时给出收敛证据。
- 决策：4 kHz 从 nested 48/96 起步；若不通过则继续复用节点加密，
  不降低 `0.25 dB / 2°` 求积 gate。

## D028 — 4 kHz 需要 96/192 求积，长任务改为 checkpoint 批次

- 状态：18-cell quadrature、12/18-cell spatial 与 golden promotion 全部通过
  （2026-07-24）。
- 48/96 结果明确失败：变化 `-1.3183 dB / +14.946°`，因此
  `-16.28971 dB` 没有被发布为 golden。
- 96/192 结果通过：变化 `+0.00395 dB / -0.1085°`；fine raw
  `0.048658184 + 0.108478828i`，normalized `-16.28576 dB`。
- 计算规模：12-cell 网格 `378,961` unknowns/solve；96/192 共
  `384` 个唯一 Helmholtz solves，并复用旧 48/96 的全部 `192` 个样本。
- 鲁棒性：首次单进程在约 `5357 CPU s` 后被外部终止且没有末尾 JSON。
  求解器现按每个 integrand sample 原子写 checkpoint，Windows replace
  采用有限退避重试；批次 runner 用独立 32-solve 子进程自动续跑并保存逐批
  stdout/stderr。故障注入 smoke 已证明 `1 -> 8` solves 可跨进程恢复。
- 2024 UDFA：同几何 `-25.87245 dB`，相对 fine wave 多衰减
  `9.58669 dB`；1/2/4 kHz 三点 RMS 为 `7.13061 dB`。
- 8-cell coarse 也通过 96/192 quadrature：`+0.00188 dB / -0.1126°`，
  normalized `-16.29992 dB`。但 12/8-cell spatial 为
  `+0.01416 dB / -6.208°`；幅度通过、相位超过 `5°`，所以 spatial gate
  明确失败。
- 12/18-cell：18-cell 为 `853,633` unknowns/solve、`61.74 cells/λ`。
  runner 完成 `48` batches / `384` unique solves；内部 96/192 求积变化
  `+0.005008 dB / -0.100356°`，12/18-cell spatial 为
  `+0.002248 dB / -2.815115°`，通过固定 `0.5 dB / 5°` gate。
- promotion：18-cell refined 为 `-16.28351 dB`，受控 finalizer 给出
  `promotion_eligible=true`；独立 verifier 接受
  `double-edge-wave-4000hz-v1.json`，SHA-256
  `fabb3690b778e9b0eb293b2b60e0cba895254f0ce1833d9a6e075af7ce07a9ff`。
- 决策：UDFA 产品拒绝进一步加强；相对 18-cell wave 多衰减
  `9.58894 dB`，1/2/4 kHz RMS 为 `7.13161 dB`。

## D029 — 空间收敛必须由复压力报告自动判定

- 状态：accepted（2026-07-24）。
- 原因：4 kHz 的 8/12-cell 幅度变化仅 `+0.01416 dB`，但相位变化
  `-6.208°`；只比较 normalized dB 会错误地宣布收敛。
- 实现：`evaluate_2p5d_spatial_gate.py` 直接计算 candidate/reference 复压力比，
  并要求频率、source/receiver/screen 几何、物理屏厚、计算域、PML、refined
  quadrature 阶数完全一致；
  candidate 必须是更细网格，且两份输入必须各自通过内部 quadrature gate。
- 回归：8/12-cell 报告复现 `+0.014156 dB / -6.208380°`，以退出码 `1`
  表示数值未收敛；反转粗细输入以退出码 `2` 拒绝。另有 5 个标准库
  `unittest` 覆盖 gate 内通过、相位单独失败、quadrature 失败、物理厚度不一致
  和 candidate 更粗五条路径。
- 验证入口：Gradle `testAcousticResearchScripts` 运行轻量 Python 回归；
  `acousticResearchCheck` 再聚合四个相关子项目的 Java 测试。首次统一运行
  `19` 个 Gradle tasks（`1 executed / 18 up-to-date`）通过。
- 决策：18-cell runner 完成后只接受 evaluator 退出码 `0` 的结果；
  gate 固定为 `<= 0.5 dB / <= 5°`，不得根据计算成本放宽。

## D030 — 长 runner 状态只从已关闭批次推导

- 状态：accepted（2026-07-24）。
- 原因：Windows 上读取正在原子替换的 checkpoint 曾与 writer 竞争并触发
  `PermissionError`；checkpoint 内容也不构成完成证据。
- 实现：`inspect_2p5d_batch_run.py` 只读取已关闭 batch JSON 和 stderr，检查
  批号连续、stderr 为空、unique solve 单调递增、active/terminal 位置合法，
  并按已完成批次估算吞吐。
- 实测：前三批 `24` 个新 solves 共 `1502.773 s`，即
  `62.616 s/solve`；状态为 `healthy-incomplete`、`25/384`，剩余约 `6.24 h`。
  主 runner stdout/stderr 仍为 0 bytes，进一步确认逐批文件才是运行期权威证据。
- 状态：`healthy-incomplete` 表示有活跃批次，`paused-incomplete` 表示可恢复但
  当前未运行，`complete` 必须同时满足 terminal unique solve 数等于目标。
- 回归：6 个 `unittest` 覆盖健康活跃、暂停、terminal、terminal 计数不足、
  非空 stderr 和批号缺口；D030 接受时轻量 Python 总测试数为 `11`。

## D031 — terminal 审计与空间 gate 合并为一条原子收尾命令

- 状态：accepted（2026-07-24）。
- 原因：人工选择 `batch48` 仍可能在 runner 未完成、批次大小改变或恢复点不同
  时选错文件；单独运行 evaluator 也不能证明整条 batch run 健康。
- 实现：`finalize_2p5d_spatial_gate.py` 先调用 D030 batch audit，只有
  `complete` 且 unique solves 恰好等于 `384` 时才自动定位 terminal JSON，
  随后调用 D029 complex-pressure evaluator。
- 退出码：`0` 空间通过、`1` 空间失败、`2` 输入无效、`3` runner 尚未完成；
  输出把 batch-run audit 和 spatial gate 合并为同一 JSON。
- 回归：新增 3 个 `unittest` 覆盖 not-ready、terminal pass、terminal spatial
  fail；真实活跃第四批返回 `not-ready`/`3`。轻量 Python 总测试数现为 `14`。

## D032 — wave golden 必须显式携带几何，domain CLI 使用完整 PML 域

- 状态：accepted（2026-07-24）。
- 缺口：nested validator 的报告此前只有频率、网格、域和复压力，没有
  source/receiver/screen 坐标；两个数值相同的 JSON 不能证明属于同一几何。
- 修复：最终报告新增 source/receiver 坐标、screen front/back/top 和物理厚度；
  spatial evaluator 强制逐字段比较几何。新增 geometry mismatch 回归后，轻量
  Python 总测试数为 `15`。
- 12-cell 重导出：复用已完成 `384`-solve checkpoint，零新增求解生成
  `fine96-192-geometry-batch01.json`；复压力仍为
  `0.04865818375798219 + 0.10847882780722039i`，normalized
  `-16.285762043603707 dB`，stderr 为空。
- 真实回归：8-cell checkpoint 也零求解重导出 geometry 报告；新 evaluator
  在两份显式同几何报告上仍复现 `+0.014156363 dB / -6.208380414°`，
  正确返回 spatial failure `1`。
- 命令修正：validator 的 domain 参数是含 PML 的完整域
  `x=[0.1875,1.8125] / z=[0.1875,1.2125]`；此前计划误写的是内部非 PML
  边界 `x=[0.25,1.75] / z=[0.25,1.15]`。checkpoint metadata 正确拒绝了
  错误组合；活跃 18-cell run 使用正确完整域。

## D033 — complex wave fixture 固化 schema、声速与相位约定

- 状态：accepted（2026-07-24）。
- 原因：几何一致仍不足以解释复数相位。当前 FDFD 输出采用 solver-native
  outgoing `exp(+i*k*r)`，而精确 BTMS 文献式采用 `exp(-i*k*r)` 并在对照时
  共轭；不记录约定会允许符号相反的复压力被错误复用。
- schema-v1：报告固定 `schema_version=1`、模型
  `2.5d-point-source-helmholtz-double-edge`、声速 `343 m/s` 和
  `complex_phase_convention=outgoing exp(+i*k*r)`；spatial evaluator 要求两份
  报告完全一致。
- 重导出：8/12-cell 均复用各自 `384`-solve checkpoint，零新增求解生成
  schema-v1 报告；真实 spatial 回归仍为
  `+0.014156363 dB / -6.208380414°`、退出码 `1`，两份 stderr 为空。
- 回归：新增 phase-convention mismatch 用例；轻量 Python 总测试数为 `16`。

## D034 — finalizer 输出自包含 candidate summary，计数保持整数

- 状态：accepted（2026-07-24）。
- 原因：只保存 terminal batch 的临时路径不足以形成可迁移证据；通用 number
  reader 还会把 cell/solve/order 等 JSON 整数转成浮点数。
- 实现：terminal 后内嵌 schema、模型、声速、相位约定、完整几何、域、网格、
  求积和复压力 `candidate_summary`；只有 spatial gate 通过时
  `promotion_eligible=true`。thickness cells、unknowns、quadrature intervals
  和 unique solves 使用严格整数读取器。
- 鲁棒性：所有数值读取器拒绝 Python/JSON boolean 冒充 `0/1`；batch inspector
  同样拒绝 boolean unique solve count。
- 真实回归：以 schema-v1 8-cell 为 reference、12-cell 为 terminal candidate
  生成完整自包含摘要，保持 spatial failure 与 `promotion_eligible=false`。
- 回归：新增两个 boolean/type 用例并断言 summary 整数类型；轻量 Python 总测试数
  为 `18`。

## D035 — terminal 合并证据必须原子持久化

- 状态：accepted（2026-07-24）。
- 原因：约 6 小时的最终结论不能只存在控制台输出；同时 runner 中间状态绝不能
  覆盖先前的 terminal 证据。
- 实现：finalizer 新增 `--output-json`，仅对 `status=complete` 的 spatial pass
  或 failure 写同目录临时文件，再用 `os.replace` 原子发布；Windows
  `PermissionError` 使用有限退避。not-ready/invalid 不写目标。
- 真实运行验证：活跃第六批返回 `not-ready`、退出码 `3`、
  `persisted=false`，指定目标文件保持不存在。
- 回归：新增 complete 原子写入和 not-ready 保留旧证据两项测试；轻量 Python
  总测试数为 `20`。

## D036 — golden promotion 与数值求解和 finalization 分离

- 状态：accepted（2026-07-24）。
- 原因：仓库当前没有双边缘 JSON golden；提前加入 12-cell candidate 会把已知
  `6.208°` spatial failure 固化成错误测试真值。
- 实现：`promote_2p5d_wave_golden.py` 只接受 terminal、batch complete、
  solve 数等于目标、spatial pass、`promotion_eligible=true` 和 schema-v1
  candidate；fixture 去掉临时 batch 路径与时间戳并原子写入。
- 真实负向验证：8/12-cell finalizer 原子保存 spatial failure（退出码 `1`）；
  promotion 返回 `2`/`spatial gate did not pass`，golden 目标文件不存在。
- 结果：12/18-cell 已通过并由该工具创建
  `two-edge-wave-reference/src/test/resources/golden/double-edge-wave-4000hz-v1.json`；
  后续修改仍必须重新经过 terminal finalizer、promotion 与独立 verifier。
- 回归：4 个 promotion 测试覆盖确定性输出、原子持久化、incomplete batch 和
  spatial failure；轻量 Python 总测试数为 `24`。

## D037 — promoted fixture 必须通过独立物理一致性验证

- 状态：accepted（2026-07-24）。
- 原因：受控生成不能防止 fixture 之后被手工修改；字段存在也不证明复压力、dB、
  网格厚度和门槛内部自洽。
- 实现：`verify_2p5d_wave_golden.py` 校验 schema/model/`343 m/s`/
  `exp(+i*k*r)`、固定 source/receiver/screen、完整 PML 域、25 mm/18-cell
  厚度、最低 24 cells/λ、96/192 quadrature、384 solves，并从 raw complex
  pressure 与最短绕屏路径重算 normalized dB；spatial candidate pressure/cell
  size 也必须与 candidate summary 一致；provenance generator 与 promotion rule
  固定，防止 fixture 脱离受控生成链。
- 缺失语义：目标 fixture 尚不存在时显式返回 `not-present`/退出码 `3`，不跳过、
  不伪装通过。Gradle 提供独立 `verifyPromotedWaveGolden`；统一
  `acousticResearchCheck` 只在源树 fixture 实际存在时自动依赖它。
- Gradle 配置验证：`help --task verifyPromotedWaveGolden` 成功并显示 Exec
  任务、verification group 与正确描述；缺失 fixture 的 Python 权威检查为
  `not-present/3`。一次资源竞争下的完整 Gradle Exec 超过 120 s，未作为通过证据，
  且检查确认没有遗留 Java 进程。
- 回归：4 个 verifier 测试覆盖有效 fixture、dB 篡改、spatial raw pressure
  不一致和超门槛；另有 promotion 输出直接进入 verifier 的生产者—消费者契约测试，
  轻量 Python 总测试数为 `29`。

## D038 — production material snapshot identity 懒计算，不进入热路径

- 状态：implemented contract, export pending（2026-07-24）。
- 原因：CUDA resident snapshot/delta 必须有稳定 identity；但每个 20–40 Hz capture
  排序并做 SHA 会给当前 CPU 产品路径增加无必要成本。
- 实现：`SparseMaterialSnapshot.diagnosticEntries()` 只在显式调用时按 unsigned
  packed cell 排序；`diagnosticSha256()` 懒计算并缓存，覆盖 complete flag、坐标、
  material UTF-8 id、transmission/absorption 三频带、scattering 和 fill fraction。
  正常 `sampleAt` 不触发该工作。
- 验证：相同内容不同插入序得到相同 hash；complete 或 fill 改变会改变 hash；
  `SparseMaterialSnapshotTest` 4 项定向测试 `BUILD SUCCESSFUL`。
- 后续：D039/D040 已补齐 mapping/table identity、bundle、Minecraft adapter 与
  opt-in 写出入口，D042 已补普通 C++ host reader；仍待真实游戏 capture 验证与
  CUDA device reader/kernel。

## D039 — Minecraft mapping 版本与声学系数表哈希必须分离

- 状态：implemented identity and capture adapter（2026-07-24）。
- 原因：Minecraft 方块分类不是有限静态表；结果同时依赖 tags、`SoundType`、fluid
  state、collision shape 与 fill fallback。只哈希声学系数无法识别分类规则变化，
  而枚举并哈希所有 `BlockState` 又会被 Minecraft/数据包注册内容影响，不能作为
  跨语言 CUDA 契约。
- 实现：`MinecraftAcousticMaterials.MAPPING_ALGORITHM_VERSION=1` 单独标识映射规则；
  `AcousticMaterials` 以固定八材料顺序、大端 schema-v1 惰性计算全部三频带
  transmission/absorption 与 scattering 的 SHA-256。当前 hash 为
  `8bed6d00ec4e433107ad44ad8178dc72fb4d6fb7633577c5cf9ea618f5ae84d8`。
- 内容指纹：显式诊断时遍历并排序当前 registry 的全部 block states，把 state
  descriptor 与映射后的 canonical material id 连同 mapping version/material hash
  写入 `MCFMAP01` 后计算 SHA-256。这样 relevant tags 或 state-dependent
  `SoundType` 改变会改变指纹；fluid/collision/fill 由实际 snapshot hash 覆盖。
- 性能：hash 位于 lazy holder；正常 Minecraft block sampling 不初始化或计算它。
- 验证：核心 identity 定向测试与 Fabric client compile 均
  `BUILD SUCCESSFUL`；后续 D045 后的完整 core suite 为 `38` suites、`120` tests、
  0 failures/errors/skipped。
- 边界：系数仍为 `[H]`，hash 不是校准证据。adapter 已能把真实 capture、语义内容
  fingerprint、Minecraft/mod 版本、mapping/table identity、snapshot hash、
  generation 与 ray batch 组成 bundle，并由 D040 的 opt-in 入口写出；但尚无真实
  游戏 capture 证据与 CUDA device reader/kernel，因此 C1 仍为 partial。

## D040 — production DDA bundle 必须自校验，不接受隐式材质

- 状态：core protocol、opt-in export 与 live capture implemented（2026-07-24）。
- 原因：只有 snapshot hash 字符串不能供 C++/CUDA 重建输入；如果 cell 携带任意
  Java `AcousticMaterial`，native 端还会在无版本约束下猜测系数。
- 实现：新增大端 `MCFPDDA1` schema-v1，单文件绑定 Minecraft/mod 版本、内容
  fingerprint、mapping/table identity、snapshot generation/complete/hash、
  unsigned 排序 packed cells、canonical material id/fill 与 ray batch。reader
  重新构建 snapshot 并重算 hash；writer 拒绝规范八材料表之外的对象。
- 验证：负坐标与 identity round-trip、snapshot hash corruption、非法 UTF-8、
  未知材料拒绝四项定向测试 `BUILD SUCCESSFUL`；同时复用了 snapshot hash 4 项
  回归。该命令不是本次改动后的完整 core suite。
- 游戏入口：仅当 JVM property `fpvdrone.acoustics.ddaExport` 非空时，acoustic
  worker 才等待首个 complete snapshot 并一次性原子写出；默认热路径不遍历 registry、
  不计算 fingerprint、不写磁盘。失败只记日志，不使传播 batch 失败。
- 验证：bundle 四项定向测试与 `:fabric-mod:compileClientJava` 分别
  `BUILD SUCCESSFUL`；atomic round-trip 另断言同目录无临时文件残留。
- 后续：D042 已补普通 C++ host reader，D043/D044 已补 client command 与三读 gate，
  D064 已由官方 Client GameTest 生成并验证真实游戏 capture。CUDA device
  reader/kernel 仍未完成；不得把该文件格式宣称为 GPU 加速证据。

## D041 — CUDA reader 之前必须先有独立语言的 schema 证据

- 状态：accepted and implemented（2026-07-24）。
- 原因：Java writer/read-back 可能共享同一个布局错误；在缺少 `nvcc` 时直接写
  未编译的 C++ parser 也不能形成证据。先用标准库 Python 独立实现 material table、
  snapshot hash、unsigned cell ordering 与 bounds，可在不改变系统 toolchain 的
  情况下关闭协议歧义。
- 实现：Java `DdaProductionSnapshotBundleCli` 生成包含八材料、正负坐标和三种 rays
  的确定性 fixture；`verify_dda_production_bundle.py` 完全独立解析 `MCFPDDA1`，
  重算 material/snapshot/file SHA。根 Gradle task
  `verifyDdaProductionBundleContract` 串联 generator 与 verifier，并加入
  `acousticResearchCheck`。
- 固定证据：`482` bytes、`8` cells、`3` rays，snapshot SHA-256
  `d7fbeb8e26df3c85bb937c91c59d993a55726310f18e5c22273a2ac85871a990`，
  file SHA-256
  `f905c3039d4e0815f4ece5e1fc91aed9b6ab07ff907cdcc889da9fd3b159e9ff`；
  Java→Python gate `BUILD SUCCESSFUL`。
- 回归：Python 新增 5 项 valid/corruption/order/UTF-8/trailing 测试；轻量脚本总数
  `29 -> 34`，全部通过。
- 后续：D042 已实编译普通 C++ host reader，D064 已完成真实 Minecraft capture
  的 Java/Python/C++ 三读；这些仍不等于 CUDA device reader、kernel parity 或
  crossover。下一步等待可用 CUDA toolchain。

## D042 — 缺少 nvcc 不阻止实编译 C++ host reader，但禁止伪造 device 进度

- 状态：C++ host reader accepted, CUDA device work blocked by toolchain
  （2026-07-24）。
- 环境：CMake `3.31.8` 找到 Visual Studio 2022 MSVC `19.44.35213.0` 与 Windows
  SDK `10.0.26100.0`；CUDA Toolkit/`nvcc` 仍不存在。
- 实现：新增无第三方依赖的 C++20 verifier，独立实现严格 UTF-8、big-endian
  primitives、SHA-256、material/snapshot 重建、unsigned packed cells 与完整 ray
  inputs。MSVC 使用 `/W4 /WX /permissive-`，warnings 即 errors。
- 验证：Gradle `verifyNativeDdaProductionBundle` 串联 Java fixture、CMake configure、
  Release build 与 CTest；SHA/material self-test、Java fixture、5-case corruption
  harness 共 3 项 CTest，`100% tests passed, 0 failed`。随后完整 core suite 为
  `38` suites、`120` tests、0 failures/errors/skipped。
- 决策：C1 的普通 C++ schema reader 已有可执行证据。尚未实现 CUDA device layout、
  reader/kernel、H2D/D2H 或 parity；不得把 C++ executable 称为 CUDA 后端，也不
  自动安装系统级 Toolkit。D064 已关闭真实 Minecraft capture 输入 gate，但本机
  Toolkit gate 仍未通过。

## D043 — 真实 capture 请求使用官方 client command，重工作业留在 worker

- 状态：implemented；自动 integrated-client 路径已验证（2026-07-24）。
- 原因：只支持启动前 JVM property 会让真实 capture 验证依赖重启且难以确认请求
  状态；直接在 client command 回调遍历 registry/写文件又会违反 Fabric API 关于
  command 默认运行于 client game thread 的约束。
- 实现：通过 `ClientCommandRegistrationCallback` 注册
  `/fpvdrone-acoustics export-dda` 与 `export-status`。前者只向
  `DirectPropagationController` 写入带 revision 的 atomic latest request，目标为
  游戏目录下唯一的
  `acoustic-diagnostics/dda-production-v1-<epoch-ms>.bin`；worker 等待 complete
  snapshot 后执行原子写出且不覆盖旧 capture。后者只读取 atomic 状态字符串。
- 并发：新请求替换未开始的旧请求；旧写出完成时只在 revision 仍匹配时更新状态，
  不覆盖更新请求。incomplete snapshot 不消费请求。命令不接受任意路径，减少误写
  工作区外文件的风险。
- 验证：`:fabric-mod:compileClientJava` `BUILD SUCCESSFUL`，证明当前 Fabric
  command v2 与 Minecraft mappings 契约成立。D064 使用同一 atomic request/worker
  路径在 integrated client 自动生成产物并由三种 reader 验证；人工交互命令仍可
  用于现场诊断，但不再是 release 证据的唯一入口。

## D044 — live capture 只能由 require-complete 三读 gate 接受

- 状态：live artifact accepted by three-reader gate（2026-07-24）。
- 原因：Java、Python、C++ 各自存在 reader 并不保证操作者对同一文件运行了三者；
  incomplete snapshot 虽可用于诊断，也不能成为 CUDA resident-world 输入证据。
- 实现：新增 Java verify CLI，并为 Python/C++ CLI 增加 `--require-complete`。
  Gradle `verifyMinecraftDdaCapture -Pbundle=<path>` 串联三种 reader；任一端失败使
  aggregate task 失败。Java JSON 输出对全部控制字符做合法 escaping。
- 验证：先有一次资源争用下的 300 秒无输出超时，审计并终止其两个遗留 Java 进程，
  未计为通过；随后相同命令对 canonical fixture 正常 `BUILD SUCCESSFUL`，Java、
  Python、C++ 均报告 schema 1、complete、相同 material/snapshot identity、
  `8` cells、`3` rays。Python 新增 incomplete 拒绝测试，轻量总数 `34 -> 35`。
- live 证据：D064 当前生成 `1053 bytes / 32 cells / 5 rays` 的 complete
  multi-material/cross-chunk 文件，Java、Python、C++ 均报告 snapshot
  generation `44`、
  相同 material/snapshot/file SHA。
  file SHA-256 为
  `bf009d1e497cc3e00ea946458a3edb7c140439d6060ef6e4de125dfb51cb0e9b`。

## D045 — production 输入与 Java CPU expected results 分文件绑定

- 状态：fixture and live CPU oracle implemented（2026-07-24）。
- 原因：`MCFPDDA1` 只含 snapshot/rays，未来 device 输出若没有 Java CPU 的逐 ray
  topology/bands 真值就无法 parity；直接修改已固定的 input schema 会破坏现有
  bundle SHA、三语言 reader 和 live capture 协议。
- 设计：新增 `MCFPREF1` schema-v1 sidecar，以 input bundle SHA-256 与 snapshot
  SHA-256 为外键；逐 ray 保存 flags、visited/material counts、loss/gain、首个有效
  material cell，以及每段 packed cell、几何长度、canonical material id 与 fill。
  generator 逐 ray streaming 写出并原子发布。
- 双重 oracle：Java sidecar trace 自己按 `VoxelDda.walk` 累积，同时必须与产品
  `DirectPathSolver` 的 counts、loss/gain/completion 完全一致；避免新 diagnostic
  实现悄悄偏离产品语义。
- 跨语言：Python 与 C++ 都独立实现 tie policy、snapshot lookup、fill 有效长度与
  三频带累积。fixture 为 `231` segments、`6746` bytes，sidecar SHA-256
  `30c9e506c6adaa2dbf25fe603d43ec8db8c5505dc353938d437a0b7fd1d1c67c`；
  Java→Python gate、MSVC `/W4 /WX` build 与 CTest 均通过。native corruption
  harness 从 5 增至 9 cases。
- 统一入口：`prepareMinecraftDdaParityOracle -Pbundle=<path>` 强制三读 complete
  input，生成 Java sidecar，再让 Python/C++ 重放。canonical fixture 的完整命令
  `BUILD SUCCESSFUL`。
- 回归：Java 新增 4 项 sidecar deterministic/identity 测试；完整 core 为
  `38` suites、`120` tests、0 failures/errors/skipped。Python 新增 4 项 sidecar
  测试，轻量总数 `35 -> 39`，全部通过。
- live 证据：D064 对真实 Minecraft 文件生成 `4902 bytes / 5 rays / 159 segments`
  sidecar，SHA-256
  `0cef2a955523f348ca8ca9e844251f6269deef35170be10f58a1654637f893f7`；
  Python/C++ 重放通过。CUDA device kernel/parity/timing 仍完全未发生。

## D064 — live DDA gate 使用官方 Client GameTest 自动闭环

- 状态：accepted and implemented（2026-07-24）。
- 决策：使用 `fabric-client-gametest-api-v1` 创建真实 integrated
  singleplayer world，通过服务器命令生成并启动 `racing_quad`、把听者后移
  `28 m`、构造 stone/glass/wood/water/foliage 五层屏障，等待客户端收到
  `>1000 rpm` 遥测，再从正常
  `DroneSoundManager` / acoustic worker 路径原子导出。不伪造 `ClientLevel`、
  registry、chunk snapshot 或产品 ray batch；测试还要求射线至少 `25 m`、
  跨 chunk 且 snapshot 含全部五种 canonical material。
- 一键入口：`captureAndVerifyMinecraftDda` 先运行 Client GameTest，再对固定输出
  执行 complete 三读和 Java→Python/C++ expected-results replay。
- 实测：live input `1053 bytes / 32 cells / 5 rays`，ray length
  `29.477–29.890 m`；live sidecar `4902 bytes / 159 segments`。五条射线都
  穿过五种材料并跨 chunk，三频带损失约 `25.33/60.27/98.11 dB`；
  两个 aggregate task 均
  `BUILD SUCCESSFUL`。详细身份 SHA 与边界见
  `decision-D064-live-minecraft-dda-capture.md`。
- 边界：该小型 live corpus 关闭 host capture/parity，不是 CUDA throughput、
  洞口绕射或大规模 resident-chunk 性能证据；D063 的 device/speedup flags
  保持 false。

## D046 — tonal 转速变化必须按音频时间平滑，不能按 PCM buffer 跳频

- 状态：accepted and implemented（2026-07-24）。
- 原因：源遥测以约 20 Hz 更新；旧 renderer 虽保留 oscillator phase，却在新 frame
  到达时直接替换频率。频率的一阶导数因此在 buffer 边界突变，而且实际过渡行为受
  sound engine 每次请求的样本数影响，会产生可听的 zipper/阶梯感。研究计划给出的
  tonal parameter smoothing 门限为 `20–80 ms [H]`。
- 实现：`PhaseContinuousSynthesizer` 对已有 tone 的目标频率使用固定 `40 ms` 线性
  chirp；新 tone 直接从其观测频率起振，避免从 0 Hz 滑入。相位、瞬时频率与未完成
  ramp 均跨 render 调用保存；中途收到新 RPM 时从当前瞬时频率重新规划。渲染使用
  复数旋转与每样本递推的 chirp step，每个 span 只计算常数次三角函数，没有引入
  per-sample allocation 或 per-sample `sin`。
- 验证：定向测试比较同一 `1000 -> 2000 Hz`、40 ms 过渡的一次 `1920` 样本调用与
  `4 × 480` 样本调用，逐样本误差门限 `2e-5`；另以 5 ms 零交叉窗口确认起始窗口
  尚未瞬跳到 2 kHz、末窗口已到达目标。完整 core 为 `38` suites、`122` tests，
  0 failures/errors/skipped；轻量 Python 研究契约为 `39` tests，全部通过。项目级
  `build -x :fabric-mod:runGameTest` 通过；未排除的 `build` 仍只在既有
  `racing_quad_diagnostic_climbs_in_game` blackbox tick-0 零样本问题失败，其余
  8/9 GameTest 通过，该 server-only 失败未经过客户端音频路径。
- 边界：`40 ms` 仍是待实录/听测标定的 `[H]`，不是论文测得的 FPV 常数。tonal
  amplitude 与 broadband 三带 gain 的 buffer-independent 时间常数随后由 D047
  关闭；桨盘 directivity 尚未被 renderer 消费，也不在本决策中假装完成。

## D047 — source gain 包络必须使用固定 attack/release 时间常数

- 状态：accepted and implemented（2026-07-24）。
- 原因：旧 tonal amplitude 在每次 `render` 的整个 buffer 内线性到达目标，所以
  256、480 或 2048 样本请求会产生不同的起音/消音速度；broadband 三带 gain 则在
  frame 切换时直接跳变。两者都会把 sound engine 的内部 buffer 调度泄漏成可听包络，
  并违反计划中的 `50–150 ms gain [H]` 门限。
- 实现：tonal linear amplitude 与 broadband 的 `sqrt(energy)` amplitude 分别保留
  跨调用状态，按样本应用一阶 `60 ms` attack、`120 ms` release。静音 frame 仍继续
  驱动 release 和 noise filter，直到三带 gain 低于 silence threshold；tone 也只在
  release 后才清理。该路径没有 per-sample allocation。
- 验证：新增 tonal+broadband 组合 frame，把一次 `4800` 样本渲染与 `10 × 480`
  渲染逐样本比较，误差门限 `2e-5`；release 测试先运行 200 ms attack，再确认随后
  200 ms 的末 10 ms RMS 低于首 10 ms 的 30%。完整 core 为 `38` suites、
  `123` tests，0 failures/errors/skipped；轻量 Python 研究契约为 `39` tests，
  全部通过。项目级 `build -x :fabric-mod:runGameTest` 通过。
- 边界：`60/120 ms` 是位于研究范围内的 `[H]` 产品起点，不是测量结论；后续需以
  同步 RPM/推力实录和主观听测分别拟合 motor tonal、prop tonal 与 broadband 包络。
  这也不等于传播 gain smoothing、limiter loudness calibration 或 source
  directivity。

## D048 — 桨盘指向性必须分频带；NEAPTIDE 系数不得直接成为产品默认

- 状态：research fit accepted, product profile blocked（2026-07-24）。
- 输入：Zenodo NEAPTIDE record 10512044 的方法 PDF、Mavic hover 七通道与
  Calibration WAV；SHA-256 和 CC BY-NC 4.0 边界登记于
  `neaptide-directivity-validation.md`。原始文件只在忽略的 `tmp/`。
- 几何修正：论文角度是相对水平面的 elevation；水平 hover 时 runtime 的
  `mu=|rotorDiskNormal·sourceToListener|=|sin(elevation)|`。因此 Mic 1 的 90°
  是轴向，Mic 6/7 的 0°/-3.5° 是桨盘平面附近。
- 结果：轴向 overall/low 相对平面为 `-12.94/-15.41 dB`，mid/high 却为
  `+5.73/+4.85 dB`。一个标量 directivity gain 会把至少两个频带做反。
- 模型证据：对 overall/low/mid/high 拟合
  `D(mu)=c2*mu²+c4*mu⁴`，fit RMSE `1.26–1.54 dB`，leave-one-angle-out RMSE
  `1.75–2.29 dB`，均过 `2.5/3.0 dB` 内部门禁。分析脚本包含 PCM Pa 换算、94 dB
  calibration gate、Welch 三频带、偶模型和 deterministic JSON；3 项定向测试通过，
  并随完整轻量 Python 契约共 `42` tests 全部通过。
- 产品决策：只接受“频带化、偶对称、profile-driven”的 API 结构，不把 Mavic 数值
  硬编码到默认 profile。CC BY-NC、单机型 domain gap 与七 elevation 缺少 azimuth
  覆盖，使 `eligible_for_product_default=false`。
- 重新开放：至少完成其余 NEAPTIDE 机型 transfer test，并取得可分发的 5 英寸三叶
  FPV 多角度校准数据；未参与拟合的角度/工况须达到 `<=3 dB`，再做 Minecraft
  机体旋转 A/B，确认不与距离衰减和 DDA transmission 重复。

## D049 — directivity core 接受 profile 结构，不暗带研究数据默认值

- 状态：core API implemented, Fabric activation blocked（2026-07-24）。
- 实现：新增 `AxisymmetricSourceDirectivity`，从
  `abs(rotorDiskNormal·sourceToListener)` 计算 `mu`；low/mid/high 各使用有界
  `c2*mu²+c4*mu⁴` dB profile。tone 在三个 profile anchor 之间按 log-frequency
  插值 pressure amplitude，避免 order 穿越 band boundary 时跳变；broadband
  分别把 amplitude dB 转为 energy gain。
- 安全性：上下半球偶对称；桨盘平面强制为 0 dB reference；profile bounds 必须包含
  0 dB 且位于 `[-120,+60] dB`；source/listener 重合时保持原 emission；所有
  frequency、anchor、coefficient 和 axis 输入均验证有限性/范围。
- 验证：5 项定向测试覆盖 axis 三频带 pressure/energy、上下对称与平面 unity、
  log-frequency 几何中点、重合位置 identity、非法 bounds/anchor order，
  `BUILD SUCCESSFUL`。完整 core 为 `39` suites、`128` tests，
  0 failures/errors/skipped；轻量 Python 为 `42` tests，项目级
  `build -x :fabric-mod:runGameTest` 也通过。
- 许可边界：Java 中没有 D048 的 NEAPTIDE/Mavic 系数；唯一静态构造器是
  `omnidirectional()` fallback。该类尚未接入 Fabric render state，因为在合规
  5 英寸 FPV profile 之前接入 unity 不会产生任何物理收益，接入 Mavic 数值又会
  越过许可/domain gate。

## D050 — 五机型 transfer test 拒绝通用无人机 directivity

- 状态：cross-aircraft gate completed and failed（2026-07-24）。
- 输入：D048 相同校准链扩展到 Matrice、Holybro S500、Tarot X6 Blade 1/2，加上
  Mavic 共五套 hover profile；五个 ZIP 的 SHA-256 登记于
  `neaptide-directivity-validation.md`，原始音频仍只在忽略的 `tmp/`。
- 方法：`compare_neaptide_directivity.py` 对每个 metric 用四机型全部角度拟合共享
  `mu²+mu⁴`，再预测未见第五机型，轮换五次；transfer gate 为最大
  leave-one-aircraft-out RMSE `<=3 dB`。聚合器有 identical/opposite-trend 两项
  合成测试，并对输入顺序确定性做了实文件复核。
- 结果：overall/low/mid/high 的 pooled RMSE 分别为
  `7.341/7.423/2.958/1.816 dB`，最大 LOAO RMSE 为
  `12.916/15.703/5.057/2.154 dB`；只有 high 通过。Matrice 的轴向
  overall/low 为 `+16.05/+17.22 dB`，Mavic 为 `-12.94/-15.41 dB`，符号相反；
  Tarot 同机架换桨也改变结果。
- 证据：聚合 JSON SHA-256
  `a1c37cea02e45c5c58a3f69866b7cd549edd357d9319edbf61caf91eaab6d7be`；
  CLI 输入顺序反转后字节不变。新增 2 项聚合器测试后，轻量 Python 研究契约共
  `44` tests，全部通过。
- 决策：拒绝全局“无人机 directivity”系数。profile key 至少包含
  airframe+rotor+propeller；缺失合规 profile 时显式 omni fallback。即使 high
  transfer 通过，也不能单独启用，因为来源仍为 CC BY-NC 且没有 5 英寸 FPV
  holdout。

## D051 — 声学旋向必须读取同步 layout，不按 rotor index 猜测

- 状态：implemented（2026-07-24）。
- 原因：`RotorLayoutCodec` 已同步数量、位置和逐 rotor `spinDirection`，渲染模型
  正在消费它；`DroneAcousticSourceMapper` 却仍用偶数 `+1`、奇数 `-1`。普通四旋翼
  可能碰巧一致，但自定义六/八旋翼和同轴布局没有该保证。
- 实现：mapper 每次 source mapping 解码 entity 的同步 layout；只有
  `layout.rotorCount != telemetry rotorCount` 时才显式退回旧交替规则，避免损坏
  layout 把不存在的条目伪装成有效旋向。
- 验证：`:fabric-mod:compileClientJava` `BUILD SUCCESSFUL`，证明当前 client split
  source、entity API 与 codec 契约成立。
- 边界：当前轴对称 scalar pressure source 不使用 observer azimuth，所以本次只
  修正遥测事实，不把 `spinDirection=-1` 粗暴转换为负 sound frequency。旋向影响
  complex phase 与 rotor-airframe interaction 必须等相应方位模型和实测参数后
  单独验证。

## D052 — broadband band energy 必须独立且做滤波器方差归一化

- 状态：implemented（2026-07-24）。
- 原因：`AcousticBands` 的契约是线性能量，renderer 却把同一 white noise 直接分成
  300/3200 Hz low-pass difference，且不归一化。相同 `energy=1` 的三个 band 会
  因有效带宽不同得到不同 RMS，high 天然压过 low；profile 能量参数因而没有可移植
  含义。
- 实现：low、mid、high 各消费确定性 xorshift 序列的独立样本支路；low 使用
  `LP300`，mid 使用 `LP3200-LP300`，high 使用 `white-LP3200`。constructor 从
  一阶滤波器解析稳态 variance、同输入双 low-pass covariance 和 white/low-pass
  covariance 计算每带 inverse-RMS normalization；render hot loop 仍无 allocation。
- 验证：新增单带测试分别渲染 2 s 的 `energy=0.001`，丢弃前 1 s attack/settling，
  后 1 s 的 soft-limited output RMS 均在 `0.011–0.014`，最大/最小 `<1.08`。
  更新后的 `PhaseContinuousSynthesizerTest` 定向任务 `BUILD SUCCESSFUL`。完整 core
  为 `39` suites、`129` tests，0 failures/errors/skipped；Python `44` tests 与
  项目级 `build -x :fabric-mod:runGameTest` 也通过。
- 边界：三支路是伪随机独立近似，解析式采用理想均匀 white variance `1/3`；测试
  用实际 xorshift 输出关闭有限样本偏差。该改动只保证 band energy 语义，不校准
  `OrderTrackedRotorModel` 当前 `20/50/30% [H]` 谱包络，也不等于 1/3-octave
  filter bank。

## D053 — order model 的 Nyquist guard 固定为计划规定的 0.45

- 状态：implemented（2026-07-24）。
- 原因：研究计划和 alias 验收写明
  `K=floor(0.45*sampleRate/fBPF)`，`OrderTrackedRotorModel` 却使用 `0.475`；
  synthesizer 的 `0.495` 只应是最终防御，不应替代 source model 的 guard。
- 实现：模型引入单一 `NYQUIST_GUARD_RATIO=0.45`，所有 shaft、BPF、electrical 和
  cogging candidate 共用严格 `<0.45*sampleRate`。
- 验证：8 kHz、9000 RPM、三叶的 BPF 为 450 Hz；测试确认第 7 次
  `3150 Hz` 保留、恰在 guard 的第 8 次 `3600 Hz` 排除。更新后的
  `OrderTrackedRotorModelTest` `BUILD SUCCESSFUL`。
- 边界：这只关闭 alias 安全余量不一致；不证明当前默认 12 harmonics 的幅值包络或
  8 kHz 低采样率是产品配置。

## D054 — 未测 rotor 初相必须稳定去相关，不能等间隔制造精确相消

- 状态：implemented（2026-07-24）。
- 原因：旧 mapper 使用 `2π*rotorIndex/rotorCount`。model 再把 blade-pass phase
  乘 `bladeCount*harmonic`；四 rotor/三叶等常见组合在相同 RPM/幅值时会形成规则
  roots of unity，使若干 BPF 谐波在 mono 中精确消失。这是初始化公式伪影，不是
  测得的相干干涉。
- 实现：新增 `DeterministicRotorPhase`，以 source/entity seed 和 rotor index 经
  SplitMix64 产生 `[0,2π)` 初相；相同实体重启 layer 时稳定，不同 rotor/source
  去相关。mapper 只在 oscillator 创建时提供它，后续仍由
  `PhaseContinuousSynthesizer` 对 RPM 连续积分，不逐 tick 重置。
- 验证：3 项 core 测试覆盖稳定性/跨 rotor 与 source 差异/范围、4/6/8 rotor ×
  2/3 blade × 12 harmonics 无精确 phasor cancellation、负 index 拒绝；定向 core
  test 与 `:fabric-mod:compileClientJava` 均 `BUILD SUCCESSFUL`。首次组合命令只因
  测试 assertion-message lambda 捕获非 final 循环变量编译失败，改为即时字符串后
  同一命令通过；产品算法未因此改变。随后完整 core 为 `40` suites、`133` tests，
  0 failures/errors/skipped；Python `44` tests 与项目级
  `build -x :fabric-mod:runGameTest` 也通过。
- 边界：hashed phase 不是 mechanical/ESC phase 真值，也没有模拟 phase drift、
  AM/FM 或真实 path-length complex phase。获得同步 phase telemetry 后应替换；
  在此之前它只优于已知会精确相消的等间隔假设。

## D055 — NASA RPM sweep 不支持通用 tip-speed/RPM 响度幂律

- 状态：evidence accepted, universal exponent rejected（2026-07-24）。
- 来源：NASA NTRS 20160009054，美国政府作品/public use permitted；PDF SHA-256
  `76bc8fd97be1f553f2c70e9a08640ec7ca3c9148c455af8f4f41fdfd67e79391`。
  实验装置、Table 1/2、spectra 与 OASPL 页均已渲染视觉核验。
- 参数：DJI 9.4-inch 与 APC 11-inch 双叶，RPM 分别 `3000–7200`、
  `1800–5100`，300 RPM step；5 microphones、1.905 m、`-45°..45°`、
  80 kHz/30 s，因 recirculation 只取 onset 前约 5 s。
- 证据：两者 BPF 随 RPM 增强，高频 broadband roll-off 上移；APC OASPL 随
  RPM 增长，但 DJI unweighted OASPL 近似相似、A-weighted 反而下降，loaded
  motor 与 broadband 使简单趋势失效。同推力 `6000 RPM DJI` 对
  `3600 RPM APC` 的 OASPL 差可达约 8 dB。
- 决策：拒绝全局 `RPM^n` 或 `tipSpeed^n` 默认。RPM gain 必须由同一
  airframe+rotor+motor+propeller profile 分组件标定；NASA 数值不外推为 5-inch
  三叶 FPV 默认。

## D056 — operating-point profile 分离 rotor、motor 与 broadband gain

- 状态：core implemented, calibrated profiles pending（2026-07-24）。
- 实现：新增 `RotorOperatingPointGainCurve`。严格递增 RPM anchors 分别保存
  rotor-tonal、motor-tonal、broadband dB；相邻点在 log-RPM/dB 空间插值，范围外
  钳制端点，gain 限于 `[-120,+60] dB`。pressure tone 使用 `/20` 转换，
  broadband energy 使用 `/10` 转换。
- 集成：`OrderTrackedRotorModel` 对 BPF harmonics 使用 rotor gain，对
  shaft/electrical/cogging 使用 motor gain，对三带 broadband energy 使用
  broadband gain。旧七参数 constructor 与 `researchDefaults()` 均注入 unity
  curve，所以本次不改变未校准默认响度。
- 验证：curve 的 4 项测试覆盖 log-RPM/dB 几何中点、端点 clamp、unity 无隐式
  RPM law、无序/out-of-range/NaN 拒绝；source model 集成测试验证
  `+6 dB rotor pressure`、`-6 dB motor pressure`、`+3 dB broadband energy`
  分别落在正确分量。两类定向测试 `BUILD SUCCESSFUL`；完整 core 为 `41`
  suites、`138` tests，0 failures/errors/skipped，Python `44` tests 与项目级
  `build -x :fabric-mod:runGameTest` 也通过。
- 下一 gate：只有合规 5-inch FPV anchors 和未见 RPM `<=3 dB` holdout 后才启用
  非 unity curve；启用时必须把 Minecraft 外层旧 RPM volume heuristic 固定为
  master gain，避免双重 RPM 响应。

## D057 — 实测参数只通过严格、版本化、原子重载的 source profile 激活

- 状态：schema/runtime path implemented, measured profile pending（2026-07-24）。
- 核心契约：新增不可变 `AcousticSourceProfile`、`AcousticProfileKey` 与
  `AcousticProfileCatalog`。v1 使用同步的 airframe preset、rotor count、blade
  count、毫米级 radius 与 motor pole pairs 做精确匹配；重复 key/id 拒绝，未知
  配置显式回退 `research_unity_fallback`，不做“最近机型”外推。
- 来源与许可：每个 profile 必须至少含一项 citation、absolute source URI、
  license 与 measurement conditions。schema 不内置 NEAPTIDE/NASA 系数；
  这两组数据仍分别受许可/domain-transfer 与 rotor-geometry gate 阻止成为默认。
- loader：Fabric client 从
  `assets/<namespace>/acoustic_profiles/<path>.json` 严格解析；resource 路径必须
  与 JSON namespaced id 相等。未知/缺失/重复字段、非有限数、无序 RPM anchor、
  非法 directivity bounds 或重复配置会拒绝整次 reload，并保留上一个不可变
  catalog，避免音频线程观察到半套参数。
- renderer：同步 `airframe_preset`；profile 同时驱动 order model operating-point
  curve 与频带 directivity。只有 `measured=true` 的 profile 才能声明
  `replaces_legacy_rpm_volume=true`，此时外层旧 RPM heuristic 改用固定 motor/
  propeller playback gain，关闭 D056 指出的双重 RPM 响应。
- 验证：core 新增 exact match、fallback、duplicate-key/id 与毫米量化测试；
  Fabric decoder 新增完整 v1、unknown/duplicate field、empty evidence 与 RPM
  order 测试。完整 core 为 `42` suites、`143` tests；项目四个 Java 模块合计
  `138` suites、`1076` tests，0 failures/errors/skipped；Python `44` tests 与
  项目级 `build -x :fabric-mod:runGameTest` 均通过。尚未发布任何 measured
  profile；v1 仅能唯一
  区分几何相同的一个 propeller，若将来同尺寸/叶片数存在多型号，schema v2 与实体
  元数据必须增加稳定 motor/propeller id，不能覆盖 key。

## D058 — profile fitter 必须按运行时可表达的模型做独立 holdout

- 状态：deterministic fitting and cross-language gate implemented,
  real 5-inch data pending（2026-07-24）。
- 契约补强：D057 的 v1 增加 machine-readable `validation` 和逐 evidence
  `sha256`。实测 profile 必须有非空 unseen-RPM/unseen-angle 样本，两类最大误差
  都 `<=3 dB`，且每项 evidence 有 SHA-256；否则 core constructor 直接拒绝。
  unmeasured resource 也不得进入 catalog，只能使用内建显式 fallback。
- broadband 语义修正：旧 `20/50/30% [H]` 从 `OrderTrackedRotorModel` 硬编码迁移为
  profile-owned energy distribution，三项必须非负且和为 1。运行时仍只有一个
  broadband RPM gain；fitter 因此使用 reference-RPM 的固定三带分布，并让
  band-specific RPM spectral drift 作为 holdout error 暴露，不能用三个隐藏曲线
  伪造通过。
- 工具：新增 `tools/acoustics/fit_acoustic_profile.py` 与 README。严格 CSV 包含
  recording/maneuver split、RPM、elevation、rotor/motor tonal 及三带 broadband；
  train/validation 不得共享 recording 或 maneuver。训练 rotor-plane RPM anchors，
  在 log-RPM/dB 空间拟合三类 operating gain，并拟合
  `c2*mu²+c4*mu⁴` 三频带 directivity；validation RPM 必须未见且位于训练范围，
  validation off-plane angle 也必须未见。
- 发布行为：输入 evidence 至少一个 SHA 必须精确绑定 measurement CSV。只有两类
  最大误差都通过时才原子写 profile；失败只写 `profile_written=false` report。
  输出稳定排序并记录 measurement/profile SHA。随后必须运行
  `:fabric-mod:verifyAcousticProfileFile`，用实际 Java strict decoder/core
  constructor 做跨语言复核。
- 验证：6 项 Python 合成真值测试覆盖解析/拟合真值、独立 split、SHA binding、
  `>3 dB` 拒绝、不生成失败 profile 与 byte determinism；另以 12 train +
  6 validation 合成记录实际生成 profile，Python 报告两个最大误差均 0，Java CLI
  验证 `3` RPM anchors、`2` unseen-RPM rows、`4` unseen-angle rows。core 新增
  profile broadband distribution 生效与非 unity-sum 拒绝测试。
- 回归：完整 core 为 `43` suites、`150` tests；四个 Java 模块合计 `139`
  suites、`1083` tests，0 failures/errors/skipped；轻量 Python 契约由 `44`
  增至 `50` tests，项目级 `build -x :fabric-mod:runGameTest` 通过。
- 边界：当前 wide CSV 只验 aggregate rotor/motor trends，固定的 harmonic count/
  rolloff 与 motor order amplitudes 必须来自另一个有 provenance 的 order analysis。
  本 gate 是 measured profile 的必要条件，不替代 renderer spectral golden、
  Minecraft A/B 或最终主观听测；当前仍没有真实 5-inch profile 被提交。

## D059 — 录音 descriptor 只接受校准压力与同步稳态 RPM

- 状态：steady bench analyzer implemented, real capture pending
  （2026-07-24）。
- 输入：新增严格 recording manifest、示例和
  `tools/acoustics/analyze_recording.py`。每条记录绑定 measurement/background WAV、
  tach CSV、channel/time range、elevation、distance、blade/poles/harmonics、
  motor/propeller/microphone/signal-chain ID、thrust/voltage/current 和温湿压。
- 校准：原生解码 uncompressed 8/16/24/32-bit PCM，不重采样；用已知声校准器
  tone-window RMS 把 normalized PCM 转 Pa。calibration、signal、background、
  tach、manifest 与最终 descriptor 均记录 SHA-256。
- 频谱：实现 5 Hz Hann、75% overlap Welch 与 background PSD spectral
  subtraction；`±40 Hz` 窗口积分 BPF harmonics 和
  shaft/electrical/2×electrical candidates，遵守 runtime `0.45 sampleRate`
  guard。所有 tone bins 从 low/mid/high broadband 移除；rotor/motor 窗口重叠时
  拒绝，不能双计。
- 物理 gate：tach 时间严格递增且 segment 内至少 5 samples；RPM CV 默认
  `<=2.5%`；signal/background sample rate 相同；至少 2 Welch frames；分析带
  SNR 和 peak 均通过。输出按 `20log10(r/1m)` 归一化，额外 correction 必须显式
  留在 manifest/report，不能作为听感旋钮。
- 验证：8 项合成录音测试覆盖 signed 24-bit endpoints、已知 BPF/electrical
  level（含 2 m 和 -1 dB correction，误差 `<=0.25 dB`）、byte determinism、
  low SNR、unsteady/nonmonotonic RPM、peak 与 window-overlap 拒绝。18 条真实
  PCM 编码的合成录音进一步贯通 analyzer→fitter→Java：12 train、6 validation，
  最大 unseen-RPM/angle error 分别为 `0.000013878/0.000002508 dB`，Java 确认
  3 anchors、2/4 holdout rows。
- 回归：轻量 Python 由 `50` 增至 `58` tests，全部通过；四个 Java 模块保持
  `139` suites、`1083` tests，0 failures/errors/skipped。组合命令第一次在 4 kHz
  worker 并行占用下达到 184 s 外层时限，未计为通过；随后拆分运行
  `testAcousticResearchScripts` 与 `build -x :fabric-mod:runGameTest`，分别
  `BUILD SUCCESSFUL`，后者耗时 4m31s。`git diff --check` 通过。
- 边界：本实现只处理 steady bench point；不会从 audio 猜 RPM，也未实现
  ramp/gust AM-FM、individual BPF harmonic regression、forward-flight load、
  damage/wet spectra 或 psychoacoustics。electrical/cogging 仍是 candidate
  order label，真实成因必须由 component isolation 验证。合成通过不是实测 profile。

## D060 — tonal aggregate 只能累加通过局部 prominence 的独立阶次

- 状态：per-order evidence implemented, order-parameter fitter pending
  （2026-07-24）。
- 原因：只对整组 `rotor_tonal_db`/`motor_tonal_db` 积分会把 broadband noise
  填进每个预期 order window；一个实际上不存在的高次 BPF 或 cogging candidate
  因而可能被错误写进 runtime amplitude。
- 实现：每个 BPF harmonic、shaft、electrical 和 2×electrical candidate 现在单独
  输出 center、background SNR、local prominence、isolated 1 m level 与 detected。
  local floor 取离中心 `1.5–3.5 × half-width` 且排除全部 tone windows 的 residual
  PSD 中位数，并按目标 window bin 数换算后从能量域扣除。aggregate 只累加达到
  `minimum_tone_prominence_db` 的 isolated power。
- 歧义 gate：任意两个 candidate windows（不只 rotor↔motor）相交即拒绝；未检出
  order 仍从 broadband bins 排除，防止候选窗口泄漏回 broadband。整段没有任何
  detected BPF 或 motor order 时失败，不能生成伪造 aggregate。
- 验证：24-bit 合成录音明确包含 600 Hz BPF 与 1400 Hz electrical，不包含
  1200 Hz BPF、200 Hz shaft 或 2800 Hz twice-electrical；报告 detected 分别为
  `[true,false]` 与 `[false,true,false]`，同时 aggregate level 仍在既有
  `<=0.25 dB` 真值门内。analyzer→fitter holdout 测试继续通过。
- 下一步：以这些逐阶次证据拟合 BPF harmonic rolloff、shaft/electrical/cogging
  相对幅值及其独立 holdout；在此之前 profile metadata 中这些固定参数仍必须由
  外部 order analysis 提供，D059/D060 不声称已自动标定。

## D065 — 公开数据必须逐硬门槛审计，不能替代目标 5 英寸自采

- 状态：admissibility registry and deterministic verifier implemented；
  当前 `0/9` 数据集可直接发布 profile（2026-07-24）。
- 硬门槛：public raw time history、absolute pressure calibration、同步 measured
  RPM trace、mic geometry、propulsion metadata、background/noise floor、独立
  holdout、商业再分发兼容、target 5-inch FPV domain 必须全部为 true。
- 结论：Bristol/NEAPTIDE/CIRA/serration 数据用于 source structure/directivity；
  NASA/DroneNoise 用于 flyover propagation；DroneAudioSet/Glasgow/DDL 用于
  microphone placement、分类与听感。它们都不得冒充目标三叶竞速机实测 profile。
- 工具：新增 machine-readable
  `public-source-data-admissibility-v1.json`、独立 verifier 和 3 项回归测试。
  eligibility 从全部 gate 推导；手工 override 或缺 gate 会失败。
- 下一步：P0 仍是自采 5-inch calibrated WAV + synchronized tach/ESC + load；
  P1 是向 CIRA 申请 raw/许可并为公开 Pa² spectra 实现只读 spectral-reference
  adapter；P2 用 NASA calibrated flyover 闭环传播，但不把 source mismatch 算成
  solver error。

## D066 — processed Pa² autopower 只允许形成 spectral-reference

- 状态：adapter、pinned fetch、manifest 与真实 HDF5 execution implemented
  （2026-07-25）。
- 输入契约：真实 BP-T 文件为 `Autopower[8193,13,5]`，RPM 4000–8000、theta
  +60°…-60°、radius 1.62 m、frequency 0–25.6 kHz/3.125 Hz bins；文件 MD5/
  SHA-256 与官方 datafile id 固定。
- 方法：BPF harmonic window、local sideband floor subtraction、6 dB prominence、
  1 m 显式 free-field normalization、`c2*mu²+c4*mu⁴` directivity 和独立
  per-harmonic log-RPM trend。±60° 到 90° 必须标 extrapolated。
- 实测派生：BPF plane `55.480–77.979 dB SPL @1m`，4000–8000 RPM slope
  `22.315 dB/doubling`、RMSE `0.249 dB`；40 directivity fits 与 8 RPM trends。
  部分 higher-order power-law 最大残差约 `4.41 dB`，不能共享一个 universal
  harmonic exponent。
- 隔离：report 强制 release-profile/raw-time-history/phase/PCM/profile-fitting
  全部 false；validator 拒绝弱化。它只减少结构模型不确定性，不满足 D059 raw
  evidence，也不改变 D065 的 `0/9` release eligibility。

## D067 — processed spectra 的设计变化必须用同 RPM/angle paired delta

- 状态：strict comparator、BPF-removed band output 与真实 straight/tripped
  comparison implemented（2026-07-25）。
- 契约：baseline/candidate 的 RPM、theta、radius、frequency grid、blade count、
  harmonic window 和 normalization 必须完全一致；只比较共同 detected orders 和
  共同 complete directivity fits。
- 数据：`straight_B` 与 `straight_tripped_BT` 各 4.3 MB，官方 MD5/SHA 固定；
  65 个 measurement pairs、39 个共同 directivity fits。
- 派生：所有点 BPF-removed high delta 均为负，mean `-2.342 dB`；mid
  `-1.832 dB`；low `+1.129 dB` 但范围 `-5.164…+4.646 dB`；BPF mean
  `-0.480 dB`。变化强烈依赖 band/RPM/angle，不能变成 scalar tripping gain。
- 隔离：因果 attribution 仍需 matched CAD metadata；全部 release/PCM/profile
  gates 保持 false，数值不外推成目标 5-inch profile。

## D068 — 目标采集的 unseen RPM/axis holdout 必须在录音前锁定

- 状态：capture planner/materializer implemented；real 5-inch capture pending
  （2026-07-25）。
- 默认矩阵：6 train plane RPM×2、6 train signed angles×2、5 unseen validation
  RPM×1、6 unseen signed angles×1，共 35 measurements、13 geometry-matched
  backgrounds；train/validation 为 24/11。
- preflight：按 `mu=abs(sin(elevation))` 防 angle leakage，validation RPM 必须
  unseen 且位于 training range；预先验证 0.45 sample-rate guard 与所有 BPF/
  shaft/electrical/twice-electrical window overlap。
- evidence：results CSV 必须全部 `captured`、actual thrust/current/environment
  完整且 calibration/background/WAV/tach 存在，才物化 analyzer manifest；sidecar
  绑定 plan/results/manifest SHA。
- provenance：D059 新增 `airframe_id` 与受控 `source_configuration`。当前示例是
  single-rotor bench，不能冒充 full-airframe；多转子不同 RPM 的 release evidence
  仍需 per-rotor telemetry schema。

## D069 — CUDA source、编译与 device parity 分开记账

- 状态：conditional FP64 source + compiled CPU runner implemented；CUDA build/
  execution pending（2026-07-25）。
- 实现：`native/cuda-dda` 始终构建 C++20 CPU reference；只有
  `check_language(CUDA)` 成功才构建 FP64 device target。kernel 输出逐 visited
  segment、first material、flags 与三频带结果，并相对既有 CPU oracle 逐项比较。
- 当前证据：MSVC runner 对 canonical Java fixture/sidecar 的 2 项 CTest 通过；
  200-batch tiny fixture CPU P95 `0.0036 ms`。CMake 明确报告 CUDA compiler
  unavailable；静态 verifier 明确报告 compiled/executed 都为 false。
- 边界：存在 `.cu` 不是 CUDA 编译证据，CPU tiny fixture 不是 crossover。
  device parity、100k corpus、batch matrix、resident delta 和 native bridge 仍 pending。
- 完整说明：
  [`decision-D069-conditional-cuda-dda-prototype.md`](decision-D069-conditional-cuda-dda-prototype.md)。

## D070 — 晚期混响只建立一份 listener-shared FDN

- 状态：core/probe/official Fabric wet bus 与 stone/wool Minecraft 材料集成场景
  implemented；默认关闭，真实 RIR、wet-stream lifecycle 与 release performance
  gate pending（2026-07-25）。
- 实现：listener-centred voxel reflection probe 估计 openness/MFP/三频带
  RT60/EDT/DRR；8-line Hadamard FDN 对 100–250 ms 环境变化平滑但不清 tail；
  所有 drone emission 先预混，再通过一个 listener-relative
  `FabricSoundInstance#getAudioStream` 输出 wet PCM。
- 性能：128 rays×8 bounces 的 synthetic stone room P95 `0.614 ms`；
  FDN `44.0 ns/sample`。不含 Minecraft snapshot capture/render frame。
- 环境方向证据：同几何 stone/wood/soft RT60 依次下降；open-air 为零。reference
  SHA-256
  `411ff09c420e5b2640965345a6262b13ada10c1f63e75fea979f75504fb78cb2`。
- Minecraft 集成证据：官方 Client GameTest 经真实 block-state material mapping
  得到 stone `7.870/4.673/2.875 s`、wool `0.905/0.326/0.172 s`，两者 openness
  均为零；artifact SHA-256
  `20aaf4f80914aa3f3a53c478a1c18ac64be3d7aec4e61d31e1381635b0a6780c`，
  `release_calibrated=false`。
- 边界：材料和 wet mapping 仍为 `[H]`，probe 不是 early-tap solver；feature
  `fpvdrone.listenerReverb` 默认 false，external propagation owner 或 OGG fallback
  时停止。
- 完整说明：
  [`decision-D070-listener-shared-fdn.md`](decision-D070-listener-shared-fdn.md)。

## D071 — measured RIR reference 与 Minecraft calibration 分开记账

- 状态：AIR v1.4 pinned downloader + deterministic RT60/EDT/DRR analyzer
  implemented；matching-scene release calibration pending（2026-07-25）。
- 数据：官方 ZIP `202650373` bytes，SHA-256
  `d2fd52767505c402e8aed9299dcd046493c8254cfab2f769fcf480dc7c616a5f`；
  内嵌 MIT license 也单独固定 SHA。
- 真实证据：17 positions × 2 channels 的 T20 最低 R² `0.9394`；相对论文
  Table 2 RT60 的 median error `11.56%`，13/17 在 15% 内；short booth
  maximum error `29.35%`，因此逐场景 gate 明确未关闭。
- 结论：真实排序为 booth < meeting < office < lecture；office/meeting 的低频
  衰减显著更长，支持三带 FDN，但不支持把 AIR 房间直接映射到 Minecraft 材料。
  `minecraft_release_calibrated=false` 强制保持。
- 完整说明：
  [`decision-D071-measured-air-rir-reference.md`](decision-D071-measured-air-rir-reference.md)。

## D072 — 同吸收 shoebox 证明主要偏差在材料假设而非 voxel probe

- 状态：analytic Eyring + AIR-derived effective absorption + 1 m voxel probe
  error decomposition passed（2026-07-25）。
- 几何：AIR booth `3.0×1.8×2.2 m`→`3×2×2 cells`；lecture
  `10.8×10.9×3.15 m`→`11×11×3 cells`。
- 结果：D077 sampler 修正后，probe MFP maximum error `2.66%`、三带 RT60
  maximum error `1.32%`，
  均通过固定 10% diagnostic gate；artifact SHA-256
  `4de568d12ac116ac2f58197fe9125ca188ad5e2be3a4201c942e32e55953bb43`。
- 定位：当前 stone `0.03/0.05/0.08` 在相同 AIR 几何下的 RT60 至少为实测
  `2.78×`，最高 `15.52×`；数秒级结果主要来自未校准材料 `[H]`，不是 CPU/CUDA
  DDA traversal 或 Eyring 公式。
- 边界：AIR 只反演 room-average absorption，不能写回单一 Minecraft stone；
  `minecraft_release_calibrated=false`。
- 完整说明：
  [`decision-D072-air-shoebox-voxel-error-decomposition.md`](decision-D072-air-shoebox-voxel-error-decomposition.md)。

## D073 — PTB 材料候选不能绕过异质表面 gate

- 状态：PTB archive/XLS-entry pinned，四类材料 train/holdout 与显式能量加权
  reducer implemented；heterogeneous voxel gate failed（2026-07-25）。
- corpus：2574 rows，2517 行有完整六倍频程；518 行存在 `alpha>1`，禁止静默
  clamp；archive SHA-256
  `d40814d54b90ed6cd22bb4a7584cef082b758ec01faa6cabf05493da06fe92fe`。
- 候选：stone `.0267/.035/.07`、wood `.12/.09/.10`、glass
  `.1767/.06/.03`、wool `.40/.775/.85`，均为 flat-energy-per-octave
  diagnostic，不是目标无人机频谱加权 release values。
- 关键结果：11×11×3 stone/wool/wood/glass mixed room 中，256×12 probe 相对
  arithmetic Eyring 最大差 `51.93%`，相对 mean-log 仍差 `17.77%`；10% gate
  明确失败。当前参数不写回 `AcousticMaterials`。
- 完整说明：
  [`decision-D073-ptb-material-absorption-and-mixture.md`](decision-D073-ptb-material-absorption-and-mixture.md)。

## D074 — late-field 偏差由 configured scattering 路径分布触发

- 状态：35-point rays×bounces configured matrix + 35-point all-diffuse causal
  control passed（2026-07-25）。
- 收敛：configured 48-bounce 的 64–4096 ray sweep 相对 4096 reference 最大仅
  `1.735%`；256×12 相对 4096×48 仍差 `9.66%`，所以不是 ray count 不足。
- 路径：configured 4096×48 的 horizontal hits `77.21%`，diffuse-area 预期
  `64.71%`；MFP `3.413 m` vs `4V/S=3.882 m`；normal/material mismatch 为零。
- 因果对照：修正漫射采样相关性后，只把 scattering 设为 1，保持同
  geometry/absorption，最大 RT60 误差从 `25.02%` 降到 `0.38%`。
- 决策：early BRDF 与 late diffuse transport 必须分离；当前 block scattering
  `[H]` 不得直接支配长期 Eyring hit distribution。
- 完整说明：
  [`decision-D074-late-field-scattering-control.md`](decision-D074-late-field-scattering-control.md)。

## D075 — listener-shared reverb 的 Java 侧预算与生命周期通过

- 状态：Minecraft integrated capture/probe、6-source/360-tone wet-stream P99 与
  cleanup lifecycle gates passed（2026-07-25）。
- 集成世界：D077 sampler 修正后，1521-cell capture P99 `0.4501 ms`，
  128×8 probe P99 `1.0397 ms`，200/200 snapshots 完整。
- 音频：4096-byte read P99 `2.4007 ms`（buffer 的 `5.63%`）；
  16384-byte read P99 `13.7304 ms`（`8.05%`），都低于 25% 门槛。
- 生命周期：source removal 释放 synthesizer 但保留 FDN tail；重复 close、安全空
  read 与 replacement stream independence 均通过。
- 边界：JVM allocation counter 不含 direct-buffer native bytes；未测 OpenAL
  enqueue/device callback、真实 device replacement 和可观测 underrun。
  `openal_end_to_end_measured=false`、`minecraft_release_calibrated=false`，feature
  继续默认关闭。
- 完整说明：
  [`decision-D075-listener-reverb-performance-lifecycle.md`](decision-D075-listener-reverb-performance-lifecycle.md)。

## D076 — two-hit early / diffuse-late transport 候选通过

- 状态：research-only direction policy + 35-point convergence + combined
  verifier passed（2026-07-25）；runtime default 未改变。
- 策略：bounce 0–1 使用真实 material scattering，bounce 2+ 使用 diffuse
  direction；吸收、几何和材料统计保持相同。
- 结果：4096×48 最大 RT60 公式误差 `0.40%`、hit-fraction 误差 `0.17 pp`、
  MFP `3.874 m` vs `4V/S=3.882 m`；通过 10% diagnostic gate。
- 对照：configured 为 `25.02%`，all-diffuse 为 `0.38%`；因此 two-hit split
  保留早期材料方向假设且恢复 late-field 一致性。
- 边界：一个闭合 shoebox 不能决定普适 mixing time；多几何、doorway、
  matched-RIR 与 Minecraft integrated P99 重验仍 pending，feature 默认关闭。
- 完整说明：
  [`decision-D076-two-hit-early-late-transport-split.md`](decision-D076-two-hit-early-late-transport-split.md)。

## D077 — 无状态漫射采样与 MFP 路径阈值通过多几何诊断

- 状态：SplitMix64 stateless hemisphere sampling、4-room/36-run matrix 与
  independent verifier passed（2026-07-25）。
- 修正：旧 `rayIndex+bounce` 线性序列会与路径相关；纯漫射 low-square 的
  hit-fraction 误差由 `4.02 pp` 降至 `0.26 pp`，RT60 误差由 `7.55%`
  降至 `0.38%`。
- 多几何：11×11×3 low-square、7×7×7 cube、21×5×3 corridor、
  15×9×5 hall，均运行 4096×48。
- 结果：configured 跨房间最大误差 `29.36%`；fixed-two `0.76%`；
  `2×MFP` 为 `1.04%` 且 hit-fraction 误差 `0.77 pp`；全部 0–4×MFP
  candidates 最大 `2.23%`。
- 决策：研究变量改为 cumulative path/time；`2×MFP` 进入 matched-RIR 候选，
  但 closed shoebox 不能校准 doorway/cave/outdoor，runtime 默认不变。
- 完整说明：
  [`decision-D077-path-scaled-mixing-time-and-diffuse-sampler.md`](decision-D077-path-scaled-mixing-time-and-diffuse-sampler.md)。

## D078 — doorway escape 与 reverb control 单调

- 状态：11×11×5 room、9 个 nested apertures、4096×48 与 independent
  monotonic verifier passed（2026-07-25）。
- 端点：aperture `0→55 cells` 时 openness `0→0.9917`，mid RT60
  `4.112→1.284 s`，wet gain `0.38919→0.00311`。
- 全路径：escaped rays/openness 严格递增；三带 RT60/EDT 非递增；三带 DRR
  非递减；wet gain 非递增。
- 解释：近全开 RT60 非零代表逃逸前仍命中其他表面的 surviving paths；产品必须
  同时使用 openness 与 wet gain，不能把 RT60 单独解释成室外混响强度。
- 边界：没有邻室、动态门板、portal return、绕射或 measured doorway RIR；
  runtime 默认不变。
- 完整说明：
  [`decision-D078-doorway-escape-and-reverb-continuity.md`](decision-D078-doorway-escape-and-reverb-continuity.md)。

## D079 — two-room portal 回返与 FDN 动态门参数过渡通过

- 状态：7-step coupled-room portal matrix、真实 `ListenerSharedFdn` closed/open
  transition 与 independent verifiers passed（2026-07-25）。
- portal：aperture `0→45 cells` 时 room-B hit fraction `0→0.4482`、进入 B 的
  rays `0→4086`、返回 A 的 rays `0→4012`；mid RT60 `3.438→0.570 s`、mid
  EDT `3.017→1.191 s`。
- 语义：全部步骤 `escaped_rays=0`、`openness=0`，证明封闭邻室传输不会被误记成
  D078 的室外 escape；wet 仅 `0.38850→0.38077`，显示当前 mapper 对 coupled-room
  late decay 的表达有限。
- 动态：48 kHz、200 ms 一阶平滑在一个样本后仅前进 `0.0104%`，200 ms 前进
  `63.2121%`，1 s residual `0.6738%`；wet 与三带 feedback 开/关双向单调。
- 边界：room membership 使用测试专用 material tags；没有 measured coupled RIR、
  移动门几何或 OpenAL scheduling，runtime 默认不变。
- 完整说明：
  [`decision-D079-two-room-portal-and-dynamic-door.md`](decision-D079-two-room-portal-and-dynamic-door.md)。

## D080 — Minecraft block-state portal snapshot 集成通过

- 状态：真实 Client GameTest closed→open→closed、完整 12-cell 同步、固定 listener
  anchor 与 independent verifier passed（2026-07-25）。
- 场景：stone/white-wool 两个约 `9×9×7` 内部空间、glass 隔墙、12-cell 门洞，
  `4096×48` 与 `2×effective MFP`。
- 传输：closed room-B hits 为 `0`；open 后 room-B hits `53,420`、进入 B 的
  rays `2,795`、返回 A 的 rays `1,334`。
- 环境：mid RT60 `3.920→0.871 s`，wet `0.38832→0.38575`，两态 openness
  均为零；第二个 closed 端点完全复现。
- 集成约束：门状态同步必须覆盖完整变更区域，listener 必须固定到场景 anchor，
  否则网络分批更新或玩家微移会污染动态环境比较。
- 边界：未测 coupled RIR、移动门连续几何、绕射或 OpenAL scheduling；feature
  默认不变。
- 完整说明：
  [`decision-D080-minecraft-portal-snapshot-integration.md`](decision-D080-minecraft-portal-snapshot-integration.md)。

## D081 — OpenAL EFX 原生 capability 与资源生命周期通过

- 状态：真实 Client GameTest sound-thread context、EFX/HRTF query、临时
  effect/filter/aux-slot lifecycle 与 independent verifier passed（2026-07-25）。
- 设备：OpenAL Soft / OpenAL Community，`1.1 ALSOFT 1.23.1`；活动线程名为
  `Sound engine`。
- 能力：`ALC_EXT_EFX=true`、maximum auxiliary sends `2`；标准 reverb effect、
  low-pass filter 和 auxiliary slot 创建/绑定/逆序释放后 AL error 为 `0`。
- HRTF：`ALC_SOFT_HRTF=true`，但当前 `hrtf_enabled=false`，不得声称已有双耳输出。
- 集成：只新增 `SoundManager.soundEngine` 与 `SoundEngine.executor` 两个只读
  accessor；没有取得 channel、没有修改 source、默认音频路径不变。
- 决策：允许下一步实现默认关闭的 shared EFX slot + per-source low-pass/send；
  device reload、source removal 与听觉/RIR 门禁通过前不替换 Java FDN。
- 完整说明：
  [`decision-D081-openal-efx-native-capability.md`](decision-D081-openal-efx-native-capability.md)。

## D082 — EFX 已触达真实 DroneLoopSoundInstance 并恢复 vanilla routing

- 状态：live drone channel direct-filter/aux-send attach、detach、resource cleanup
  与 independent verifier passed（2026-07-25）。
- 通道：从 `SoundEngine.instanceToChannel` 选择活动
  `DroneLoopSoundInstance`，通过 `ChannelHandle.execute` 在 `Sound engine` 获取
  native source。
- 操作：standard reverb slot + low-pass filter 接入 direct filter 与 send index 0；
  attach 后 AL error `0`，随后 null slot/filter 恢复、资源逆序释放，最终 error `0`。
- 结果：不增加 voice，`persistent_efx_enabled=false`，默认程序化 PCM/Java FDN
  不变。
- 边界：只有一个瞬时 source；跨 tick controller、source churn、device reload、
  click/underrun 与 matched RIR 仍 pending。
- 完整说明：
  [`decision-D082-openal-efx-drone-source-routing.md`](decision-D082-openal-efx-drone-source-routing.md)。

## D083 — opt-in listener-shared EFX controller 与 source churn 通过

- 状态：默认关闭的 persistent controller、two-layer shared routing、Java FDN
  互斥、sound-engine reload、source removal cleanup 与 independent verifier
  passed（2026-07-25）。
- 资源：一个 standard reverb effect + 一个 auxiliary slot 由全部 drone layer 共享；
  每个 source 一个 low-pass send filter，send index `0`，不增加 OpenAL voice。
- 实测：motor/propeller 两条 source 同时 attached，filters `2`；D086 额外执行
  backend switch/reload 后，删除无人机使 attached/filters `0`、shared resources
  false，累计 cleanup `0→2`、context rebuilds `1→3`，AL error 始终 `0`。
- 互斥：GameTest 同时设置 `fpvdrone.openalEfx=true` 与
  `fpvdrone.listenerReverb=true`，Java wet bus 保持 suppressed，环境 probe 仍为
  EFX 生成 controls。
- context：真实 `SoundManager.reload()` 使 rebuilds `1→2`；shared effect/slot 和
  motor/propeller 两个 filter/source 均在新 context 恢复，AL error `0`。
- 边界：这不是物理输出设备切换；EFX 与 Java FDN 均继续默认关闭；参数仍是 `[H]`，
  未测 callback underrun、audible output 或 matched RIR。
- 完整说明：
  [`decision-D083-persistent-shared-openal-efx.md`](decision-D083-persistent-shared-openal-efx.md)。

## D084 — EFX 跨 Minecraft 声音引擎重载恢复通过

- 状态：真实 Client GameTest `SoundManager.reload()`、OpenAL context replacement、
  shared-resource recreation、two-source reattach 与 independent verifier passed
  （2026-07-25）。
- 实测：重载前后 operational/shared resources 均为 true，attached sources/source
  filters 均为 `2/2`，context rebuilds `1→2`，cleanup `0→0`，AL error 始终 `0`。
- 后续生命周期：删除无人机后 attached/filter `0/0`、shared resources false、
  cleanup `0→1`，证明 reload 后 controller 仍可正常清理。
- 互斥：Java wet bus 在重载前后保持 suppressed。
- 记账：`sound_engine_reload_exercised=true`；
  `physical_device_switch_exercised=false`，两者不得合并为“device reload passed”。
- 边界：未切换 Windows 输出设备，未测 callback underrun、audible output、
  HRTF、matched RIR 或跨平台行为；`release_calibrated=false`。
- 完整说明：
  [`decision-D084-openal-efx-sound-engine-reload.md`](decision-D084-openal-efx-sound-engine-reload.md)。

## D085 — 显式授权采集与 audio continuity 门禁就绪

- 状态：默认不录音的 FFmpeg/DirectShow capture tool、PCM24 click/dropout
  analyzer、deterministic fixture 与 independent verifier passed（2026-07-25）。
- 安全边界：只有 `capture` + exact enumerated device +
  `--consent-to-record RECORD_AUDIO` 才会录音；时长限制 `0.1–600 s`，拒绝覆盖，
  输出经过完整 PCM/WAV/hash 验证后原子落盘。
- 分析：围绕已知 reload timestamp 计算 pre/post RMS、level delta、robust
  first-difference click 与 5 ms-frame dropout events。
- fixture：48 kHz mono PCM24 6 s；`3.000 s` click 检出 1 event，
  `3.650–3.710 s` 的 60 ms dropout 检出 1 event。
- 记账：本轮只枚举四个 DirectShow endpoints，没有录音；FFmpeg 无 WASAPI
  demuxer，端点名称不能证明 loopback。`real_minecraft_capture=false`、
  `physical_output_loopback_confirmed=false`、
  `openal_callback_underrun_counter_available=false`、
  `release_calibrated=false`。
- 下一 gate：经操作者确认 endpoint/授权后，以相同场景采集 dry、Java FDN、EFX
  的 control/reload 各三次，并生成盲化 A/B/ABX。
- 完整说明：
  [`decision-D085-audio-capture-and-continuity-gate.md`](decision-D085-audio-capture-and-continuity-gate.md)。

## D086 — Minecraft audio-lab 后端与同步 timeline 通过

- 状态：dry/Java-FDN/OpenAL-EFX 临时互斥 override、880/1320/1760 Hz finite
  markers、control/reload timeline、真实 Client GameTest 与 independent verifier
  passed（2026-07-25）。
- 默认行为：session 外始终为 `DEFAULT`，继续读取原来默认关闭 properties；并发
  session、已有输出、无 active drone 或 procedural audio 关闭时拒绝启动。
- marker：48 kHz mono PCM16、80 ms、5 ms fade；tick `0/40/≥60` 启动，所有
  `PlayResult.STARTED`；tick 50 是 control/reload boundary。
- matrix：dry-control 保持 Java/EFX false；Java-FDN-control 只启用 Java；
  OpenAL-EFX-reload 保持 2 sources/2 filters、context rebuilds `2→3`、AL error
  `0`。
- 恢复：最终证据的 EFX reload call `73.1107 ms`，post-ready marker 在 tick `70`，
  即 boundary 后 `20 ticks`；这只是一次 diagnostic，不是 release threshold。
- 操作：新增
  `/fpvdrone-acoustics audio-lab <dry|java-fdn|openal-efx> <control|reload>`
  与 `status`。
- 边界：报告明确 `real_audio_capture=false`、
  `physical_output_loopback_confirmed=false`、`release_calibrated=false`；未测
  marker-to-WAV alignment、callback underrun、A/B/ABX 或 matched RIR。
- 完整说明：
  [`decision-D086-minecraft-audio-lab-protocol.md`](decision-D086-minecraft-audio-lab-protocol.md)。

## D087 — 三频 marker 的 recorder/Minecraft 自动对齐通过

- 状态：880/1320/1760 Hz normalized quadrature detector、three-point affine
  clock fit、boundary transfer、deterministic interference fixture 与 independent
  verifier passed（2026-07-25）。
- fixture：48 kHz mono PCM24 8 s，含 233/701 Hz tones 与固定 seed noise；
  recorder clock slope `1.002`、offset `0.75 s`。
- 结果：minimum marker score `0.982008`、maximum marker timing error
  `0.8000 ms`、affine residual `0.4658 ms`、reload boundary error
  `0.2301 ms`、slope error `328.77 ppm`，均通过 2 ms/500 ppm fixture gates。
- 集成：D085 continuity analyzer 接受 `--alignment-json`，严格检查 schema/status，
  读取 `boundary_audio_s` 并固定 alignment SHA-256，避免人工抄写 reload timestamp。
- 边界：`real_audio_capture=false`、`physical_output_loopback_confirmed=false`、
  `release_calibrated=false`；没有真实扬声器输出、click/dropout、callback underrun
  或听觉结论。
- 完整说明：
  [`decision-D087-audio-lab-marker-alignment.md`](decision-D087-audio-lab-marker-alignment.md)。

## D088 — 六条件重复采集、证据链与 ABX 打包协议通过

- 状态：deterministic session schedule、18-case artifact hash chain、逐 take
  continuity aggregation、balanced ABX package 与 independent verifier passed
  （2026-07-25）。
- matrix：dry / Java FDN / OpenAL EFX × control / reload × 3 takes，共 `18`
  captures；计划阶段明确不枚举、不授权、不开始录音。
- 绑定：每次 WAV 同时绑定 D085 capture、D087 alignment 和 continuity；alignment
  绑定 D086 timeline，continuity 再绑定 alignment，backend/variant/reload 语义必须
  一致。
- gate：每次 marker score `≥0.35`、residual `≤2 ms`、click/dropout 均为 `0`、
  level delta `≤3 dB`，任一 take 失败则整个 continuity gate 失败。
- ABX：dry↔Java、dry↔EFX、Java↔EFX 在 control/reload 和三 take 上生成 `18`
  trials；A/B pair 平衡，X 为 `9/9`，公开匿名清单与私有答案键分离并固定 hash。
- fixture：18 个 synthetic PCM24 case 完整，但 reload case 故意含 click；因此
  evidence complete=true，同时 loopback、continuity、listening 和 release gates
  保持 false。
- 边界：没有录音、听测、callback underrun、物理设备切换、真实 FPV 声源或
  matched RIR；ABX 文件名匿名化不是密码学盲化。
- 完整说明：
  [`decision-D088-audio-lab-session-and-abx-evidence.md`](decision-D088-audio-lab-session-and-abx-evidence.md)。

## D089 — 预注册 participant-level ABX/偏好统计通过

- 状态：response-free preregistration、participant-specific presentation order、
  complete-response validation、exact binomial/sign tests、Holm correction、
  synthetic fixture 与 independent verifier passed（2026-07-25）。
- 预注册：至少 `12` participants、每人 `18` trials、每 trial 最多 `3` extra
  replays、alpha `0.05`、majority effect `≥0.75`；固定 D088 report/public/private
  hashes。
- 顺序：按 `SHA-256(seed NUL participant NUL trial)` 为每名 pseudonymous
  participant 独立排序；CSV presentation index 必须与预注册严格一致。
- 推断单位：每个 backend-pair×variant 的三 take 先归并为 participant majority；
  discrimination 用 one-sided exact binomial，preference 用 two-sided exact
  binomial，两组各六项分别 Holm correction。
- fixture：12 synthetic participants × 18 trials=`216` answers；四个 planted
  dry-vs-wet discriminability 和四个 wet-over-dry preference 被检出，两个
  Java-vs-EFX comparisons 均保持未检出。
- 边界：realism 1–5 只作 ordinal descriptive summary；没有真人、个人数据、
  真实 loopback、伦理审批、matched RIR 或 release-calibration 结论。
- 完整说明：
  [`decision-D089-preregistered-abx-statistics.md`](decision-D089-preregistered-abx-statistics.md)。

## D090 — OpenAL 原生 source-latency 通过，device clock 不可用

- 状态：真实 Minecraft `Sound engine` thread、`SoundManager.reload()` 前后两组
  native samples 与 independent verifier passed（2026-07-25）。
- 能力：当前 OpenAL Soft `1.23.1` 暴露 `AL_SOFT_source_latency=true`，但
  `ALC_SOFT_device_clock=false`；部分支持被视为明确能力结果，不冒充完整 clock
  telemetry。
- 实测：reload 前后 host interval 分别为 `239.9648/247.2006 ms`，source offset
  都前进 `240 ms`；source-to-output latency samples 为 `51/61 ms` 与
  `61/51 ms`，AL/ALC error 均为 `0`。
- 语义：`51–61 ms` 是 OpenAL renderer/device pipeline estimate，不含 DAC、声卡、
  扬声器、空气、麦克风和 capture buffer，不能替代 D086/D087 loopback marker
  端到端对齐。
- 边界：没有 device clock drift、callback underrun counter、物理设备切换、
  loopback、听测或 release calibration；探针只读且不改变音频路径。
- 完整说明：
  [`decision-D090-openal-source-latency.md`](decision-D090-openal-source-latency.md)。

## D091 — audio-lab native timing 哈希证据链通过

- 状态：dry-control、Java-FDN-control、OpenAL-EFX-reload 三个真实 timeline v2，
  共六对 sound-thread native samples、protocol verifier 与 D088 session verifier
  passed（2026-07-25）。
- 时序：每 case 在 boundary 前后各取同时相隔至少 4 ticks/150 ms host time 的
  sample pair；pre pair 必须在 tick 50 前完成，post pair 必须在 session report
  写入前完成。
- 实测：六组 host interval 为 `194.0219–215.5776 ms`，回绕修正后的 source
  offset advance `200–220 ms`，source latency `51–61 ms`；设备均为
  `OpenAL Soft`，
  source-latency true、device-clock false、AL/ALC error `0`。
- 回绕：pair 小于 800 ms，raw offset delta `<=0` 时只允许一次一秒 streaming
  buffer rollover，并要求修正 advance 与 host elapsed 一致；相同 offset 不会被
  误判为进展。
- 链路：timeline 保存完整 pair；D087 alignment 固定 timeline SHA-256；D088
  materializer 重验 timing 并固定 alignment SHA-256，18-case report 保存每 case
  摘要与全局 identity。
- 边界：renderer latency 不是端到端 latency；没有录音、loopback、callback
  underrun、物理设备切换、听测或 release calibration。
- 完整说明：
  [`decision-D091-audio-lab-native-timing-chain.md`](decision-D091-audio-lab-native-timing-chain.md)。

## D092 — OpenAL 原生 Doppler 防重复移频通过

- 状态：真实两条 drone source、Minecraft sound-engine reload、纯函数单测与
  independent verifier passed（2026-07-25）。
- 决策：OpenAL native Doppler 会重采样完整 stream，不适合已经在 PCM 内只移动
  tonal orders、保留 broadband noise 的声源结构；继续由 Dronecraft 拥有 tonal
  Doppler，OpenAL source pitch 固定 `1.0`。
- guard：只把活动 drone source velocity 写成 listener velocity，使 native
  relative ratio 为 `1`；不修改 context-global factor 或任何其他 Minecraft
  source，source 离开/功能停止时恢复原 velocity，context rebuild 时不复用旧 id。
- 实测：reload 前后 guarded sources `2/2`、context rebuilds `1→2`、factor
  `1.0`、speed `343.29999 m/s`、velocity error/native ratio deviation `0`、
  AL pitch `1.0`、AL error `0`；内部 tonal ratios 分别约
  `1.000214/1.000200`，两条 source 均实际移频。
- 边界：这是 source-state/no-double-shift 证据，没有 loopback、可听正确性、
  物理设备切换或 release calibration。
- 完整说明：
  [`decision-D092-openal-native-doppler-ownership.md`](decision-D092-openal-native-doppler-ownership.md)。

## D093 — Minecraft 运动学到 PCM16 tonal frequency 通过

- 状态：真实移动 drone/client listener frame、生产 `DopplerShift` 和
  `PhaseContinuousSynthesizer`、PCM16 零交叉测量与 independent verifier passed
  （2026-07-25）。
- 绑定：同次 render update 保存 source/listener position/velocity、simulation
  timestamp、temperature-dependent sound speed、Doppler 前 emission 和已发布
  ratio；verifier 从三维向量独立重算 radial velocity 与物理 ratio。
- 实测：source speed `0.2686 m/s`、listener speed `1.5680 m/s`，radial
  `+0.07241/-0.01240 m/s`，ratio `1.000244828829`；motor 115.2977 Hz 和
  propeller 345.8931 Hz 的 PCM16 反测误差为 `-3.3095/+0.1287 ppm`，clipping
  均为零。
- 修正：`DopplerShift` 现在零距离也先验证 sound speed，并把实际 kinematics
  纳入 result，防止非法参数绕过。
- 边界：孤立 tone 稳态重渲染，不是 server blackbox CSV、40 ms smoothing chirp、
  混合 live stream、OpenAL playback、loopback 或 release calibration。
- 完整说明：
  [`decision-D093-doppler-kinematics-to-pcm.md`](decision-D093-doppler-kinematics-to-pcm.md)。

## D094 — 生产 AudioStream 分块、40 ms checkpoint 与服务器黑匣子通过

- 状态：真实 `ProceduralDroneAudioStream.read()` 精确 PCM、前/40 ms/after
  oscillator state、integrated-server blackbox 与 independent verifier passed
  （2026-07-25）。
- 实测：motor/propeller 各 4 个一秒分块，共 768,000 PCM bytes；ratio
  `1.000245603196→0.999972530741` 跨越 unity，8/8 target 均在第 1,920 sample
  完成平滑，最大跨分块 step `71`。
- 服务器绑定：trace ticks `22–83` 被 blackbox ticks `1–84` 覆盖，8/8 分块在
  `±2 ticks` 内匹配 tracked-rotor RPM；JSON、PCM 和 1,070-column CSV 分别独立
  hash-bound。
- 测试协调：server 读取改用 Fabric 官方 `computeOnServer`，网络 synchronizer
  保持启用，完整 Client GameTest 正常关闭。
- 边界：没有观测 OpenAL queue/playback、callback underrun、loopback、物理输出或
  release calibration。
- 完整说明：
  [`decision-D094-production-doppler-chunk-trace.md`](decision-D094-production-doppler-chunk-trace.md)。

## D095 — 生产 PCM 到 OpenAL streaming queue 绑定通过

- 状态：真实 Minecraft `Sound engine` thread 上 8 个生产 PCM chunk 与实际
  OpenAL buffer id、queue/source state 及独立 verifier passed（2026-07-26）。
- 原生简化：Minecraft 1.21.11 为 streaming stream 生成一秒 buffer，初始 pump
  4 个；每次按 `AL_BUFFERS_PROCESSED` unqueue/delete 后补回相同数量，继续直接
  复用，不另造播放器。
- 实测：motor/propeller 各一个稳定 source，共 8 个 distinct buffers；全部为
  48 kHz mono PCM16、96,000 bytes，post-refill 均为 `PLAYING + STREAMING`、
  queued `4`、processed `0`、offset `20–60 ms`、latency `51–61 ms`、AL error
  `0`。
- 绑定：D095 固定 D094 report SHA-256，并按 layer sequence 逐一重验 entity、
  simulation time、Doppler ratio、PCM bytes/hash 与 OpenAL buffer metadata。
- 修正：audio-lab timing 同时等待至少 4 ticks/150 ms，并识别 Minecraft 一秒
  buffer unqueue 导致的合法 offset 回绕；回绕修正必须与宿主时钟一致。
- 边界：`AL_SOFT_events` 可用但未注册 context-global callback；离散 post-refill
  观测不证明连续无 underrun，也没有 rendered output、loopback 或 release
  calibration。
- 完整说明：
  [`decision-D095-openal-streaming-queue-binding.md`](decision-D095-openal-streaming-queue-binding.md)。

## D096 — `AL_SOFT_events` 有界 queue-health 事件链通过

- 状态：真实 context callback、20 ms 全静音正控、两条生产 source、D095 refill
  hash binding、清理与 independent verifier passed（2026-07-26）。
- 所有权：registration 前 callback/user pointer 均为零；pointer 必须回读为 owned
  callback。任一已有 owner 会 fail closed，不覆盖。
- 实测：3.3319 s 内 16 events、单 callback thread、dropped `0`；motor/propeller
  各 4 个 buffer-completed count，8/8 D095 refill 前有累计 completion 覆盖，
  production `AL_STOPPED` 为 `0`。
- 正控：48 kHz mono PCM16、960 samples、全零 buffer，真实收到一个 completion
  和一个 stopped event，不产生非零波形。
- 清理：sound thread 上 disable/unregister，callback/user pointer 归零，正控
  source/buffer 删除，AL error `0`；248.8 ms quiet window 内 event count
  `16→16`。context-global callback 还观察到一个具有 state event 的 Minecraft
  瞬态 source completion；它不计入 drone coverage。
- 边界：扩展没有定义 underrun event；无 callback-reported stop 不是 rendered
  或物理输出连续性证明。callback 仅用于默认关闭的有界诊断，不永久占有 context。
- 完整说明：
  [`decision-D096-openal-events-queue-health.md`](decision-D096-openal-events-queue-health.md)。

## D097 — `ALC_SOFT_loopback` 隔离生产 PCM 渲染通过

- 状态：真实 OpenAL Soft loopback context、D094 motor/propeller 生产 PCM、
  rendered PCM sidecar 和 independent verifier passed（2026-07-26）。
- 隔离：独立 worker 使用 `ALC_EXT_thread_local_context`；Minecraft
  context/device/`Sound engine` identity 前后不变。
- 时钟语义：50 ms 墙钟等待期间两个 source offset 均保持 `0`；显式渲染
  24,000 frames 后均为 `24,000 + AL_PLAYING`，48,000 frames 后均为
  `AL_STOPPED`。
- 输出：48 kHz mono PCM16、96,000 bytes；nonzero `47,877`、peak `4,482`、
  RMS `405.5049`，对输入和信号在 48-sample lag 后 correlation
  `0.999999239`。
- 空白正控：256 frames 中 65 个 sample 非零但 peak 仅 1 LSB，符合 OpenAL Soft
  的 PCM16 output dithering；不再错误要求 bit-exact zero。
- 边界：这是隔离软件 renderer readback，不是 Minecraft 主 context、物理设备、
  endpoint loopback、连续无 underrun 或听感校准。
- 完整说明：
  [`decision-D097-openal-loopback-render.md`](decision-D097-openal-loopback-render.md)。

## D098 — 同输入的 OpenAL EFX / Java FDN 传递对照通过

- 状态：D094 真实生产 PCM、Minecraft 封闭房间生产参数、三条隔离 loopback 输出与
  independent verifier passed（2026-07-26）。
- 环境：生产映射得到 low/mid/high RT60
  `6.547526/3.919805/2.442873 s`，wet gain `0.388324`；开放监听点 wet gain
  为零时 fail closed，没有人为补湿声。
- 对齐：dry/EFX/Java 都为 48-sample lag，输入相关系数分别
  `0.999999236/0.980697365/0.952751811`。
- 尾声：排除 1,024-sample renderer lag 后，early→late RMS 为 dry
  `0.5028→0.5053`、EFX `30.2686→0.6387`、Java
  `56.0013→3.1302`；两种混响都可分辨并衰减，但传递函数不相同。
- 决策：EFX 保留为支持设备上的原生低成本简化；Java FDN 保留为确定性回退和三频带
  RT60 研究路径。当前不宣称听感等价或发布校准。
- 边界：隔离软件 renderer readback，不是主 context、物理 endpoint、真实录音或
  ABX。
- 完整说明：
  [`decision-D098-openal-efx-java-fdn-transfer.md`](decision-D098-openal-efx-java-fdn-transfer.md)。

## D099 — 封闭/部分门洞/开放的后端稳态矩阵通过

- 状态：3 个生产 Minecraft portal controls × dry/EFX/Java FDN，共 9 条隔离
  loopback 输出与 independent verifier passed（2026-07-26）。
- 参数：门洞 `0/4/12` 格时 mid RT60
  `3.919805→1.494898→0.870613 s`，wet gain 仅
  `0.388324→0.387386→0.385751`；三频带 RT60 都严格下降。
- EFX：early tail RMS `30.2686→11.5601→5.1758`，middle
  `5.4770→0.5181→0.5023`。
- Java FDN：early tail RMS `56.0013→34.1475→23.7437`，middle
  `15.8982→3.2192→0.8415`。
- 决策：两后端都对真实几何变化保持稳态单调；EFX 更快接近底噪，Java 保留更强的
  三频带、尤其低频尾声。没有听感优劣结论。
- 边界：三条独立稳态，不是连续开门；尚未验证 EFX 直接参数更新是否 click-free。
- 完整说明：
  [`decision-D099-backend-environment-matrix.md`](decision-D099-backend-environment-matrix.md)。

## D100 — 连续环境切换未解析到软件边界 click

- 状态：D094 连续 4 秒生产输入、closed→partial→open→closed 精确 sample
  boundaries、EFX/Java/dry 三条隔离输出与 independent verifier passed
  （2026-07-26）。
- EFX：直接生产参数写入的三个残差 step/P99 为
  `19/38.01`、`7/10`、`2/7`；5 ms RMS ratios 为
  `0.9248/1.2449/0.9658`。
- Java：0.2 秒指数平滑、同一 FDN/tail 不清空；step/P99
  `30/71`、`9/24`、`2/31`；5 ms ratios
  `0.7790/0.8978/0.6239`。
- 响应：EFX segment residual RMS
  `84.67→31.63→19.27→52.46`，Java
  `133.74→51.16→48.44→109.55`，都正确响应开门和关门。
- 决策：当前 OpenAL Soft 保留 EFX 直接更新，不增加双 slot crossfade；Java 保持
  0.2 秒平滑。物理 endpoint 与听感仍需授权录音。
- 完整说明：
  [`decision-D100-backend-dynamic-transition.md`](decision-D100-backend-dynamic-transition.md)。

## D121n — 规范世界事件端口与离线顺序回放通过

- 状态：核心 event port、Fabric adapter 静态接线、13 点离线轨迹和 independent
  verifier passed（2026-07-26）。
- 协议：world epoch 单调；每世界 sequence 严格递增；只保留最后一个签名。exact
  duplicate 忽略，冲突/乱序/旧世界拒绝，rollback 作为新 dirty event。
- 轨迹：`7 accepted / 5 dirty / 1 duplicate / 1 conflict / 1 out-of-order /
  1 world-mismatch`，共 `8195 marked cells`；dimension replacement 更换 tracker
  identity 并将新世界 revision 重新从 1 开始。
- 接线：Fabric 原生 block/chunk/world-change 入口全部经过规范端口，不再直接
  `tracker.markDirty`；named 1.21.11 client source 静态编译通过。
- 限制：Fabric 回调不暴露服务器 packet sequence，adapter 使用本地到达序号；重复
  原生回调会保守再失效。显式 rollback/chunk replace 尚只有核心语义证据。
- 边界：未启动 Minecraft、未读 `ClientLevel`、未开 renderer/声音/capture endpoint、
  未执行 CUDA、未做 release calibration。
- 完整说明：
  [`decision-D121n-canonical-world-event-port.md`](decision-D121n-canonical-world-event-port.md)。

## D121o — 默认关闭的有界 native callback trace 通过

- 状态：固定容量 ring、Fabric 接线、溢出/禁用/线程字段/零分配离线回放和 independent
  verifier passed（2026-07-26）。
- 门：仅 `fpvdrone.acoustics.nativeEventTrace=true` 启用；Fabric capacity 256。
- 参考：capacity 8 写入 12 条，保留 ordinal `5..12`、overwritten `4`，顺序导出。
- 热路径：一个不计入合同的 JVM 测量预热窗后，五个各 100,000 次 record allocation
  windows 全为零。
- 数据：只含 epoch、local sequence、thread id、kind、disposition 和整数 bounds；
  不持有 world/block/entity/audio 对象。
- 回归：core `233/233`、Fabric `467/467`、相关 Python `7/7`。
- 边界：未启动 client、未读 `ClientLevel`、未测真实 callback delivery、未开
  renderer/物理/capture endpoint、未执行 CUDA。
- 完整说明：
  [`decision-D121o-bounded-native-event-trace.md`](decision-D121o-bounded-native-event-trace.md)。

## D121p — native trace fail-closed 启动门通过

- 状态：生产初始化 gate、10-case truth table、Fabric suppression source contract 和
  independent verifier passed（2026-07-26）。
- OpenAL：仅接受 `ALSOFT_DRIVERS=null` 精确值；禁止缺失、大小写变化、物理 backend
  和带逗号的 fallback list。
- ARMED：local-plane 未请求，跳过 `DroneSoundManager` 与通用声学诊断命令。
- 矩阵：`1 NORMAL / 1 ARMED / 8 REJECTED`。
- 官方依据：OpenAL Soft commit
  `a81b7e61ba30c8330fd0c1990c8008ca364ab072` 的 driver-list 配置与 null backend；
  null device 名为 `No Output`，不提供 capture。
- 回归：core `237/237`、Fabric `469/469`、相关 Python `10/10`。
- 边界：未启动 client、未打开/回读 ALC、未测 callback delivery、未执行 CUDA。
- 完整说明：
  [`decision-D121p-native-trace-launch-gate.md`](decision-D121p-native-trace-launch-gate.md)。

## D121q — native trace/ALC 只读证据导出通过

- 状态：核心 exporter、sound-thread ALC snapshot、ARMED-only 专用命令、离线
  reference 和 independent verifier passed（2026-07-26）。
- ALC：只读 current context/device、playback device name 与 capture specifier；不
  open/create/destroy device/context/source/buffer/capture。
- 写入门：ARMED、`No Output`、capture 为空、trace 单调全部通过后才
  `CREATE_NEW`。
- 并发：按 capacity 256 分配导出数组，避免瞬时 size 竞态；JSON 写入不在声音线程。
- 负控：unarmed、物理 device、capture 存在、inactive context、乱序 trace 全拒绝。
- 回归：core `239/239`、Fabric `471/471`、相关 Python `13/13`。
- 边界：reference 是假 ALC/trace；未启动 client、未读真实 ALC、未测 callback
  delivery、未执行 CUDA。
- 完整说明：
  [`decision-D121q-native-trace-alc-evidence-export.md`](decision-D121q-native-trace-alc-evidence-export.md)。

## D121r — CUDA DDA 有界批次安全门通过

- 状态：普通 C++20 batch planner 已实编译并运行；CUDA source fail-closed gate
  静态验证通过（2026-07-26）。
- 默认上限：`8192 rays / 1,048,576 segments`；冻结 32-byte segment layout，
  segment budget 为 32 MiB。
- 策略：按输入顺序确定性连续分批；零上限、单射线超限与 byte overflow 全拒绝。
- CUDA 边界：当前 executor 仍只执行单批；若 planner 返回多批，在任何
  `cudaMalloc` 前拒绝，禁止回退到整个语料的无界 reservation。
- 夹具：默认限制为 `1 batch / 577 segments / 18,464 bytes`；ray limit 2 与
  segment limit 512 均得到 `2 batches / peak 384 segments`；segment limit 128
  正确拒绝。
- 回归：MSVC `/W4 /WX /permissive-` build passed；CTest `4/4`、Python static
  tests `2/2` passed。
- 边界：没有 `nvcc`，未编译/执行 CUDA；旧 100k parity corpus 不是 current
  production-bundle magic，未冒充 100k device evidence。
- 完整说明：
  [`decision-D121r-cuda-dda-bounded-batch-gate.md`](decision-D121r-cuda-dda-bounded-batch-gate.md)。

## D121s — 100k production corpus 与 CUDA 多批执行器通过主机门

- 状态：current `MCFPDDA1` 100k corpus、Java expected-results、三语言 reader
  parity、bounded multi-batch CUDA source 与 host typecheck 通过（2026-07-27）。
- 语料：`100,008 rays / 49,494 cells / 24,974,697 reserved segments`；
  bundle `6,190,460 bytes`，实际 CPU oracle trace `14,172,009 segments`。
- 哈希：bundle
  `83d73cdc86d90dd5dbe3ed049c92c8bd226679f4612b9d6811399cba7b89bcf2`；
  expected-results
  `5f8f538be276f3efdeafedfe0601982a5dc1fb044dd1bf45b19a8ae400a7994d`。
- 独立验证：Java、Python、compiled C++20 reader 对 bundle/sidecar 的 rays、
  segments、snapshot 与文件哈希一致。
- 默认规划：`24 batches / peak 1,048,497 segments / 33,551,904 bytes`；
  原 single-batch reservation 为 `799,190,304 bytes`，峰值降低约 23.8 倍。
- CUDA source：按 peak batch 分配 host/device buffer；每批重基准
  `segment_offset`；每批 D2H 后与同一 CPU oracle 比较；一次 timing sample 是完整
  corpus 内所有批次之和。
- 无 `nvcc` 门：MSVC `/W4 /WX /permissive-` host translation-unit typecheck
  passed；故意注入未声明标识符的负控正确失败。static CUDA contract 为 `27/27`。
- 边界：仍未编译或执行 CUDA，未测 device parity/latency/crossover，未接入
  Minecraft audio hot path。
- 完整说明：
  [`decision-D121s-cuda-multi-batch-corpus.md`](decision-D121s-cuda-multi-batch-corpus.md)。

## D121t — 100k CUDA DDA CPU correctness 基线通过

- 状态：三轮 compiled C++20 CPU oracle benchmark 与 workload identity gate
  通过（2026-07-27）。
- 合同：每轮 `5 warmup / 30 measured` full-corpus batches；三轮 snapshot/hash、
  rays/cells/checksum 必须一致；保留每轮原始值与 min/median/max。
- P50 min/median/max：`850.0525 / 872.4689 / 935.0673 ms`。
- P95 min/median/max：`1050.2913 / 1206.2000 / 1210.3220 ms`；
  median 约 `82,912 rays/s`。
- P99 min/median/max：`1054.4735 / 1208.1123 / 1272.7716 ms`。
- 边界：单线程、保留全部 segments 的 correctness workload；不是 Minecraft Java
  热路径或优化 CPU 上限；未编译/执行 CUDA。
- 完整说明：
  [`decision-D121t-cuda-dda-cpu-corpus-baseline.md`](decision-D121t-cuda-dda-cpu-corpus-baseline.md)。

## D121u — RTX 3060 NVRTC CUDA DDA 夹具一致性通过

- 状态：NVRTC 13.3 编译、Driver API cubin load/launch 与 RTX 3060 device parity
  通过（2026-07-27）。
- 工具链：固定 gitignored venv；`cuda-python 13.3.1`、
  `nvidia-cuda-nvrtc 13.3.33`、`numpy 2.5.1`；不依赖完整 Toolkit。
- 设备：RTX 3060、compute capability `8.6`、Driver API `13030`、目标 `sm_86`。
- smoke：1024-element kernel 的 H2D/device/D2H/exact equality 通过。
- fixture：`8 cells / 3 rays / 231 actual segments`；逐段 topology/material/fill/
  length、flags、first material 与三频带全部和 Python oracle 一致。
- 正式 2 warmup/10 measured：kernel P50/P95 `0.1792/0.187392 ms`；
  submit-to-result `0.2493/0.2788 ms`。tiny fixture 不用于性能结论。
- 状态边界：`nvrtc_compiled=true / cuda_executed=true /
  nvcc_compiled=false`；尚无 100k device parity、crossover 或 Minecraft 接入。
- 完整说明：
  [`decision-D121u-nvrtc-cuda-dda-fixture-parity.md`](decision-D121u-nvrtc-cuda-dda-fixture-parity.md)。

## D121v — RTX 3060 CUDA DDA 100k 全语料通过

- 状态：三轮 production-corpus device parity 与 repeat benchmark 通过；
  synchronous Minecraft hot path 拒绝（2026-07-27）。
- 合同：同一 `100,008 rays / 49,494 cells / 14,172,009 actual segments`；
  每轮一次 timing-excluded parity、`5 warmup / 30 measured`、全部 24 batches。
- kernel P95 min/median/max：`37.439 / 44.194 / 51.136 ms`。
- D2H P95 min/median/max：`153.154 / 155.607 / 171.313 ms`。
- submit P95 min/median/max：`363.906 / 373.223 / 404.050 ms`，
  median `267,958 rays/s`。
- correctness-workload 比值：相对 CPU P95 median `1206.2 ms` 为 `3.232×`；
  不代表产品 Java 热路径。
- 瓶颈：每 pass D2H 约 799 MB reserved segment records；D2H P95 是 kernel 的
  约 3.52 倍；完整 submit 是 50 ms tick 的约 7.46 倍。
- scaling：8k/16k/32k/65k/100k submit P95
  `28.878/57.688/110.730/243.103/373.223 ms`；8k 已基本摊薄固定开销。
- 决策：Java CPU DDA 继续服务小批直达声；CUDA 只保留为 aggregate-only、
  compacted、异步大批 early-reflection/shadow-mode 候选。
- 完整说明：
  [`decision-D121v-rtx3060-cuda-dda-production-corpus.md`](decision-D121v-rtx3060-cuda-dda-production-corpus.md)。

## D121w — CUDA DDA aggregate-only 输出消融部分通过

- 状态：aggregate correctness 与 D2H 消融通过；端到端 gate 不确定
  （2026-07-27）。
- 实现：full/aggregate 为同一 templated FP64 traversal 的两个编译期 specialization；
  aggregate cubin 不含 segment store。
- 正确性：三轮各验证 `100,008 rays / 14,172,009 implicit segments / 24 batches`；
  aggregate flags、counts、first material 与三频带全部一致。
- 内存/传输：peak segment buffer `33,551,904→0 bytes`；每 pass reserved segment
  D2H `799,190,304→0 bytes`；只回读 7,200,576-byte results。
- D2H P95 median：`155.607→5.847 ms`，减少约 96.2%、约 26.6×。
- kernel P95 median：`44.194→35.086 ms`，但三轮有 `130.100 ms` 离群。
- submit P95：`283.902/449.393/469.385 ms`，未稳定优于 full；共享桌面 GPU
  显存占用升至约 9.3 GiB，非 paired 环境不允许性能晋升。
- 决策：接受传输瓶颈已删除；拒绝端到端加速 claim 与同步 hot-path 接入。
- 完整说明：
  [`decision-D121w-cuda-dda-aggregate-output-ablation.md`](decision-D121w-cuda-dda-aggregate-output-ablation.md)。

## D121x — 8k→100k CPU/GPU prefix crossover 筛查通过

- 状态：compiled C++20 CPU 三轮五档矩阵通过；`>=8192 rays` 未见 crossover
  （2026-07-27）。
- CPU P95 median：8k/16k/32k/65k/100k 为
  `64.074/133.308/268.599/532.197/813.781 ms`。
- CPU P95 throughput：约 `121,996–127,852 rays/s`，区间内近似线性。
- GPU full-topology submit P95：`28.878/57.688/110.730/243.103/373.223 ms`。
- 筛查比值：CPU/GPU P95 `2.219/2.311/2.426/2.189/2.180×`；交叉点若存在，
  低于 8192 rays。
- 环境边界：新 100k CPU P95 比 D121t 低约 32.5%，证明共享桌面绝对值不稳定；
  非 full GPU prefix 也只是单进程诊断，ratio 不是 release threshold。
- 产品决策：当前几十条 direct rays/update 继续使用 Java CPU DDA。
- 完整说明：
  [`decision-D121x-cpu-gpu-dda-prefix-crossover.md`](decision-D121x-cpu-gpu-dda-prefix-crossover.md)。
