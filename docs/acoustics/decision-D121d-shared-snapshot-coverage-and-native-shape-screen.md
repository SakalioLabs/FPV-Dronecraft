# D121d — 共享局部快照 coverage 与原生形状构造筛查

## 结论

D121d 关闭了一个会直接产生错误 early reflection 的语义缺口：有限局部快照之外的
Minecraft 单元现在是 **unknown**，不再被默认视为空气。

每条 source→reflection 与 listener→reflection DDA 腿都必须满足：

```text
每个访问 cell 都位于 complete capture coverage 内
```

若任一 cell 离开 coverage：

```text
candidate_geometry=true
topology_visible=false
complete=false
coverage_miss=true
```

若 blocker 位于已覆盖区域：

```text
topology_visible=false
complete=true
coverage_miss=false
```

因此 unknown、blocked 和 cell-budget exhausted 不再混为一谈。unknown 或预算不足
只能回退到 direct + conservative late，不能发布显式 early path。

## 共享捕获规划

`LocalPlaneCapturePlanner` 为 listener 和最多 16 个 source 构造确定性捕获组。每个
source 的最低需求是包含 listener/source block cells 的 inclusive cuboid，再扩张：

- 水平 local-reflection radius：2 blocks；
- 垂直 radius：1 block；
- chunk/union halo：1 block。

冻结限制为：

- 每 tick 最多 4 个 capture groups；
- 每组最多 4096 sampled block states；
- 水平每轴最大 32 blocks；
- 垂直最大 16 blocks。

source 按 listener 距离、source index 排序，再做 first-fit。候选组以最小新增捕获
体积选择；只有合并后的新增 cells 不超过该 source 独立捕获 cells 时才允许共享。
因此对所有已分配 source：

```text
shared sampled cells <= sum(independent sampled cells)
```

超过 axis/cell/group budget 的 source 标记 fallback，不扩大为无界大快照。

## 连续表面压缩

原始 AABB union 会按 canonical coordinate cells 输出大量相邻小矩形。
`MaterialBoxUnionSurfaceExtractor` 现在重复执行两轴严格 edge merge：

- axis、normal、continuous plane coordinate 和 material 必须完全相同；
- 另一轴范围必须完全相同；
- 合并轴必须精确首尾相接；
- 不跨空隙、不跨部分边、不跨材料；
- 半砖高度和楼梯轮廓保持连续精度。

独立 Python union 实现执行相同的重复严格合并，并继续验证相邻完整方块内部面为零。
在当前 clustered-16 原生形状场中，最终快照为 137 个 material boxes、189 个 exposed
patches；场景求解仍扫描真实合并后 patch 列表，而不是一个小型替代夹具。

## 原生 Minecraft 形状筛查

离线 Java 进程实际加载 Minecraft 1.21.11 named：

```text
Shapes.block()
Shapes.box(...)
Shapes.or(...).optimize()
VoxelShape.optimize().toAabbs()
```

合成地面按坐标确定性混合完整方块、半砖和两 AABB 楼梯。它没有启动 Minecraft
client，也没有读取 `ClientLevel`。16 个 case 覆盖：

- source counts：1、4、8、16；
- layouts：coincident、clustered、corridor、dispersed；
- 独立每 source 捕获 cells 与共享 planner cells；
- 原生 AABB 数、coordinate-grid cells、合并后 exposed patches；
- planner + native-shape AABB + union construction；
- 每个 assigned source 对共享 patches、exact AABB blockers 和 coverage 的完整场景求解。

代表性 16-source 结果：

| layout | assigned/fallback | independent cells | shared cells | patches | capture P99 | solve batch P99 |
|---|---:|---:|---:|---:|---:|---:|
| coincident | 16/0 | 3920 | 245 | 93 | 0.210 ms | 0.051 ms |
| clustered | 16/0 | 5120 | 550 | 189 | 0.460 ms | 0.099 ms |
| corridor | 13/3 | 9520 | 1395 | 438 | 1.172 ms | 0.138 ms |
| dispersed | 0/16 | 0 | 0 | 0 | 0.003 ms | 0 ms |

全部 case 通过：

- native planning + shape + union P99 `≤10 ms`；
- 全 assigned-source scene batch P99 `≤5 ms`；
- shared cells 不超过对应 assigned independent cells；
- coincident-16 全部分配；
- dispersed-16 全部保守 fallback。

单次捕获基准的最坏 P99 为 `3.8129 ms`，来自一次小 case 的 Windows/JIT 调度离群，
仍低于冻结门槛；不能用各 case 的单次 P99 排名来推断几何规模速度关系。

## 独立验证

独立 Python verifier 对 16 个 case 全部重新计算：

- 距离排序、first-fit group 与最小新增体积；
- source→group assignment 和 fallback；
- group bounds、cell counts、独立/共享 cells；
- 原生 full/slab/stair 预期 AABBs；
- coordinate-grid cell count；
- solid→air union boundary 与重复 coplanar merge；
- 64 个 capture timing 样本的 P50/P99；
- 128 个 shared-scene batch 样本的 P99；
- coverage unknown 与 covered blocker 的不同状态。

当前相关 Python 回归集合为 12 项，全部通过。修改后的核心以 Java 21 编译后，又通过
JUnit Platform 直接执行 coverage、capture planner 和 union extractor 的 13 项测试，
无跳过、无失败。

## 声明边界

本轮严格保持：

```text
native_shape_construction_measured=true
snapshot_producer_measured=false
client_level_read=false
minecraft_client_started=false
minecraft_integration_enabled=false
physical_endpoint_opened=false
captures_audio=false
cuda_executed=false
release_calibrated=false
```

这证明共享规划、原生形状转换、union 和场景求解可行，但没有测量真实世界读取：

- `ClientLevel.hasChunk/getBlockState`；
- 每 tick 区块/方块访问；
- block tag/SoundType/fluid material mapping 成本；
- chunk unload 与 capture generation 一致性；
- 真实移动轨迹 snapshot churn；
- client tick→worker 的整链成本。

受“未经明确授权不得打开物理音频端点”边界约束，本轮没有启动 Minecraft client。
根 Gradle/Fabric Loom 配置仍会停滞，因此也不宣称完整 Gradle gate 通过。

## 下一步

D121e 应将真实 world-read 与声音设备彻底解耦：

1. 抽取只依赖最小 block-view 接口的 snapshot producer，使测试可提供冻结
   `BlockState/VoxelShape` 视图而无需启动 client；
2. 对已加载、部分缺块、capture 中途 generation 变化分别测试；
3. 分开统计 world reads、empty shapes、optimized AABBs、material boxes、
   coordinate-grid cells、patch compaction ratio 和 capture time；
4. 让同一 group snapshot 被多个 worker source request 共享引用，并验证生命周期；
5. 加入 snapshot cache：只有覆盖 bounds 内 block/chunk generation 变化才重建；
6. 使用移动轨迹测 snapshot rebuild ratio、fallback ratio 和 generation overwrite；
7. 在此后才设计 cluster delay/gain slew DTO；仍不打开 endpoint。

如果最终必须使用真实 `ClientLevel` GameTest 才能验证 world-read，需先取得允许启动
Minecraft client/物理输出端点的明确授权，或建立能强制禁用声音设备的可审计启动方式。

CUDA 仍未执行。本机没有 `nvcc`；GPU DDA 必须复用 coverage、capture bounds 和
expected-results contract，unknown cell 也必须返回 incomplete，不能为了并行吞吐把
未知世界当成空气。

## 证据哈希

| artifact | SHA-256 |
|---|---|
| D121d contract | `c8aba859f3725ffcf8768568cd4765c284d1975119cd9ce23e7ffaf497014eb7` |
| Java native-shape report | `cf9dc49c4e77631c1c5521be3f149e0b21a6b26ad30bcb812813122a049204f5` |
| independent verification | `0bdbe7a279f63ade5749915ad4c408fece6636e7b38c3bfa862239a182f17e5c` |
