# D121e — 冻结方块视图快照生产器

## 结论

D121e 已把 Minecraft 世界读取适配层与局部声学几何生产器分离。核心生产器只依赖
三个同步方法：

```text
generation()
isLoaded(x,y,z)
appendMaterialBoxes(x,y,z,output)
```

它不持有 `ClientLevel`、声音设备或 renderer 对象。Minecraft 适配器负责把已加载
`BlockState` 的 named 1.21.11 `VoxelShape.optimize().toAabbs()` 结果和声学材质写成
cell-local AABB；核心负责 coverage、完整性、union、halo ownership 和不可变发布。

## 完整性规则

一次快照只有同时满足以下条件才能发布几何：

- inclusive coverage 内所有 cell 均已加载；
- 扫描前后的 generation 完全相同；
- 所有 AABB 有限、正体积且不逃出所属 unit block cell；
- union canonical coordinate grid 不超过 2,000,000 cells；
- halo 收缩后仍有正 inner volume。

任一 cell 未加载、generation 改变或 union 超预算时，结果为 `complete=false`，公开的
box/patch 列表必须为空。非法 AABB 则直接拒绝，避免污染随后所有 group 的 union。

surface ownership 在原始小矩形阶段、共面合并之前判定。owner 位于 halo 的面先删除，
剩余同材质共面矩形再严格合并；这样 halo 面不能通过与 inner 面合并后改变质心而重新
进入快照。

## 离线夹具

独立冻结视图覆盖五个失败面：

| fixture | 结果 |
|---|---|
| complete 3×3×3 | 27 sampled、18 empty、11 boxes、4 inner patches，完整发布 |
| one unloaded cell | 26 sampled、1 unloaded，空几何 incomplete |
| generation 1→2 | generation unstable，空几何 incomplete |
| 65 diagonal boxes | union grid 超 2M，空几何 incomplete |
| escaping AABB | 抛出非法参数并拒绝 |

Python verifier 自行重建程序化 full/slab/stair AABB、solid→air boundary、owner cell
筛选和重复 coplanar coalescing，重新得到 11 boxes/4 patches；同时重算共享捕获 planner
和全部原始 timing 分位数。

## 生产基准

基准沿用 D121d clustered source 布局与共享 capture planner，每个 source count 记录
128 个完整批次样本：

| sources | groups | assigned/fallback | shared cells | P99 |
|---:|---:|---:|---:|---:|
| 1 | 1 | 1/0 | 360 | 0.224 ms |
| 4 | 1 | 4/0 | 400 | 0.128 ms |
| 8 | 1 | 8/0 | 440 | 0.058 ms |
| 16 | 1 | 16/0 | 550 | 0.222 ms |

最大 P99 为 `0.2236 ms`，通过冻结的 `≤10 ms` 研究门。该基准会创建 immutable
boxes/patches，因此 allocation 只报告、不设零分配门；16-source 为约
`76.2 KiB/batch`。后续 cache/rebuild 研究必须用 rebuild ratio 约束这一成本，不能把
每 tick 无条件重建当作已获准设计。

## Minecraft 适配器

`MinecraftLocalPlaneSnapshot` 已改为调用同一个核心生产器。它使用：

```text
ClientLevel.hasChunk(...)
ClientLevel.getBlockState(...)
BlockState.getCollisionShape(...)
VoxelShape.optimize().toAabbs()
MinecraftAcousticMaterials.sample(...)
```

适配器与材质映射已用 Java 21 对本地 named Minecraft 1.21.11 client/common jars 独立
编译通过。这里验证的是 API/类型契约，并未实例化或读取真实 `ClientLevel`。

## 自动验证

- 新核心生产器 5 项 JUnit 全部通过；
- coverage、capture planner、union 与生产器合计 18 项 JUnit 全部通过；
- 当前相关 Python 集合 5 项全部通过；
- 独立 verifier 通过 5 fixtures、4 source counts、512 个 raw timing samples；
- 核心全部 Java 源以 `--release 21` 编译并执行 reference；
- named 1.21.11 Minecraft adapter 独立编译通过。

根 Gradle/Fabric Loom 配置在当前环境仍可能停滞，因此不宣称完整 Gradle gate 已运行。
新任务已接入 `acousticResearchCheck`，供 Loom 环境恢复后统一执行。

## 声明边界

```text
frozen_block_view_producer_measured=true
client_level_snapshot_producer_measured=false
snapshot_producer_measured=false
client_level_read=false
minecraft_client_started=false
minecraft_integration_enabled=false
live_early_renderer_enabled=false
physical_endpoint_opened=false
captures_audio=false
cuda_executed=false
release_calibrated=false
```

这轮证明冻结输入到不可变局部几何的生产规则与离线预算可行，不证明真实世界读取、
移动轨迹 churn、chunk unload、tick thread 开销或声音输出可行。

## 下一步

D121f 应继续保持无音频、无 client 的可审计边界，先完成：

1. coverage bounds + world generation/dirty token 的快照 cache key；
2. bounds 外方块变化不重建、bounds 内变化精确失效；
3. chunk unload 或 generation overwrite 永不发布 stale geometry；
4. 1/4/8/16 sources 移动轨迹的 rebuild/share/fallback ratio 与分配量；
5. worker 对同一 group snapshot 的共享生命周期和 latest-generation 回收；
6. early cluster delay/gain/direction DTO 的有界 slew 单元测试；
7. 把 `ClientLevel` generation 从当前适配器占位参数升级为可审计的 dirty tracker。

真实 `ClientLevel` tick 基准只有在可强制禁用物理声音端点的启动方式建立后，或取得
明确授权后才能进行。CUDA 仍只保留静态契约；本机无 `nvcc`，未执行 GPU 路径。

## 证据哈希

| artifact | SHA-256 |
|---|---|
| D121e contract | `ce5f34b03243e4bc34e5cf48608f86b3d9b5c86895bc7a8aab4ee14f0dc9828a` |
| Java frozen-view report | `56a2b012f399347f4ab01130fd4778492bc7cba05a7d9536dbff96feb18397aa` |
| independent verification | `0b59d6a40908a165407f14506c174af7ac2e21ff4ee7158d0cdf27cab16b6dc8` |
