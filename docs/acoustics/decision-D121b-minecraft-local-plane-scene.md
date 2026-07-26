# D121b — Minecraft 连续局部平面场景与精确形状遮挡

## 结论

D121 的离线场景主链已经成立，但尚未接入实时音频图：

```text
Minecraft VoxelShape
  -> optimize().toAabbs()
  -> 材料 AABB 并集的外露连续平面
  -> image-source 连续反射点
  -> 有界体素 DDA 遍历/预算
  -> 精确 AABB 两段遮挡
  -> 最强 6 候选
  -> 16 samples 到达聚类
  -> early/late 能量账本
```

关键修正是把“体素拓扑”和“连续碰撞形状”分开。非空碰撞形状不能把整个
Minecraft 方块单元都标成实心，否则半砖顶面反射点位于
`y=0.5000001` 时，粗粒度 DDA 仍处于 `y=0` 单元，会把有效反射错误遮挡。
当前语义因此是：

- DDA 负责确定性步进、访问预算和粗拓扑；
- `VoxelShape.optimize().toAabbs()` 的冻结 AABB 负责实际线段遮挡；
- 每个 AABB 向内缩 `1e-9 m`，反射端点沿空气法线偏移 `1e-7 m`；
- 反射体本身不遮挡空气侧端点，但任一反射腿进入其他形状内部即拒绝。

这不是绕过 Minecraft 原生几何。原生 `VoxelShape` 仍是几何事实来源；新增层只把
它转换为可审计、可缓存、音频线程之外构造的连续表面和遮挡盒。

## 实现结果

`MinecraftLocalPlaneSnapshot` 以 source/listener 周围
`horizontal radius=2`、`vertical radius=1` 和一格 halo 捕获已加载区块：

- `BlockState.getCollisionShape(...)`；
- `shape.optimize().toAabbs()`；
- `MinecraftAcousticMaterials` 的集中材料映射；
- AABB 并集外露表面重建，禁止内部盒面和相邻方块面；
- 坐标网格超过 `2,000,000` cells 时返回 incomplete，不生成显式 early event；
- 快照保留精确遮挡盒和连续 patch，列表在构造后冻结。

`LocalPlaneSceneSolver` 的重复求解只使用预分配 primitive workspace：

- 最多输出 6 条路径；
- 排序键为三频带有界能量之和、较短连续路径、较小 canonical patch index；
- 到达间隔不超过 `16 samples @ 48 kHz` 时合并；
- 聚类输出能量加和、能量加权到达与 listener→reflection 的归一化方向；
- exact generation 与 source/listener IEEE-754 坐标构成 cache identity，任何移动或
  generation 变化都会失效，`+0/-0` 规范化；
- selected 能量进入 D121a 账本，逐频带满足
  `explicit allocated + late residual = environment budget`。

## 离线证据

Minecraft 1.21.11 named API 夹具确实执行了：

- `Shapes.block()`；
- 半砖 `Shapes.box(..., y=0.5, ...)`；
- 两 AABB 楼梯 `Shapes.or(...).optimize()`；
- 两个相邻完整方块。

结果为半砖 1 个 AABB、楼梯 2 个 AABB、相邻方块内部面 0。该 Java 进程没有启动
Minecraft 客户端，只加载了命名后的 Minecraft 几何类；SLF4J 使用 NOP provider。

场景级夹具得到：

- 半砖场景：6 个外露 patch 中只有顶面形成几何候选，并被保留为 1 条显式路径；
- 精确自遮挡：false；
- 插入另一 AABB 后任一反射腿遮挡：true；
- 8 个可见候选严格选择 6 个；
- 6 条路径形成 5 个 arrival clusters；
- 所有 cluster direction 为单位向量；
- early/late 三频带能量独立重算守恒；
- source、listener 或 generation 任一变化均 cache miss；
- 五个热路径 allocation windows 均为 `0 bytes`；
- 本机离线 P99 为 `590.625 ns/scene`，低于冻结的 `100 µs` 研究门槛。

独立 Python verifier 不信任 Java 的 selection、gain、反射点、AABB 相交、方向或
能量守恒标志，而是重新计算这些量。相关 10 项 Python 回归全部通过。

## 未完成边界

本轮明确保持：

```text
minecraft_integration_enabled=false
worker_handoff_measured=false
physical_endpoint_opened=false
captures_audio=false
cuda_executed=false
release_calibrated=false
```

尚不能声称：

- 多无人机每 tick worker handoff 与 snapshot 构造预算已经测量；
- early clusters 已写入 OpenAL/Java FDN 的实时渲染路径；
- 移动 source 的听感平滑、点击或 dropout 已验证；
- D120 的材料强度排序已由 unseen 实测数据校准；
- CUDA 已编译或运行；
- 根 Gradle 验证门已通过。

最后一项仍受 Fabric Loom 配置阶段停滞阻挡：本轮再次运行
`:computational-acoustics-core:tasks --no-daemon`，60 秒内无输出并超时。
因此当前证据采用 Java 21 `javac --release 21`、直接 Java reference、真实 named
Minecraft jar 夹具和独立 Python verifier；不能把它写成完整 Gradle 构建成功。

## 下一步

D121c 应先实现纯数据 worker handoff，而不是直接打开声音设备：

1. 在 client tick 冻结 source/listener、snapshot generation、patch 和 AABB 列表；
2. 有界 worker 队列只保留每 source 最新 generation，旧任务可丢弃；
3. 测量 1/4/8/16 sources 的 snapshot 构造、scene solve、队列等待和主线程 apply
   P50/P95/P99；
4. incomplete、超预算、过期 generation 一律回退为 direct + conservative late；
5. 对连续移动轨迹验证 cache miss、event identity、delay/gain slew，不生成 PCM；
6. 通过后才允许把 cluster DTO 接到现有渲染状态，并另行做隔离 loopback；
7. CUDA 仍只复用 canonical snapshot/expected-results contract；本机没有 `nvcc`，
   不执行、不宣称加速。只有包含传输和同步后优于零分配 CPU 路径才继续。

## 证据哈希

| artifact | SHA-256 |
|---|---|
| D121b scene contract | `84e0396841a9b2668d873e525febc938a0fb2c7aa7f2e8d9fd8639aba1d1a510` |
| Java scene report | `ca9268e1e6c339836f0215132f8ac0e33d524f78db1ac57a5ff34b3265dea3db` |
| independent scene verification | `15b97336f0936e2501647b9b3f387495be00a635564c2ccfdaedead17cdb0153` |
| native VoxelShape report | `562b1f21cfb7a40af4bb906101a0505c23ab134f993c16e90d2f74c05734a176` |
| native VoxelShape verification | `67f76770e032c557b0bd532c99dea0747edc1b52fbaaec340d7541163dc2fa72` |
