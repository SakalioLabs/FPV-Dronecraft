# D121f — Coverage-aware 快照缓存与移动轨迹

## 结论

D121f 已把 D121e 的无条件几何生产变成 exact-coverage 缓存。缓存身份为：

```text
inclusive coverage bounds + halo + coverage dirty token
+ world tracker identity
```

dirty token 是 coverage 内所有 block-cell revision 的最大值。存储按 16×16×16 section
分组，但查询仍逐 exact cell 筛选，因此即使范围外变化与 coverage 位于同一 section，
也不会产生误失效。

## 正确性

- coverage 外 dirty：复用同一 immutable snapshot 实例；
- coverage 内 dirty：同步重建；
- coverage 或 halo 变化：同步重建；
- 捕获过程中 token 改变：结果降级为 empty incomplete，绝不发布 stale geometry；
- unloaded/incomplete 捕获：永不进入缓存；
- chunk unload/range invalidation：批量标记范围内 cell；
- rebuild 期间仍由 `FrozenBlockView.generation()` 防止扫描中世界变化；
- cache hit 由 coverage tracker 负责，真实适配器必须报告全部相关 block/chunk 事件。

核心 propagation 回归共 63 项，全部通过，包含负坐标、同 section 范围外 dirty、范围内
dirty、bulk unload、并发 dirty、coverage 移动和 incomplete 不缓存。

## 移动轨迹

1/4/8/16 sources 分别运行 256 frames。source 每 64 帧跨一个 block；每 11 帧产生一次
范围内 dirty，每 7 帧产生一次远离 coverage 的 dirty，每 53 帧模拟一次 unload/reload。
四档轨迹的确定性事件账本均为：

```text
cache hits = 221
rebuilds = 35
fallbacks = 5
outside dirty events = 37
inside dirty events = 23
bounds changes = 3
load-state events = 10
```

独立 Python verifier 从帧编号集合重新计算 union，确认 221+35=256，并重算全部
1024 个原始时延样本的 P50/P99。

| sources | P50 | P99 | allocation/frame |
|---:|---:|---:|---:|
| 1 | 0.0138 ms | 0.6379 ms | 10.2 KiB |
| 4 | 0.0027 ms | 0.1971 ms | 6.6 KiB |
| 8 | 0.0027 ms | 0.1663 ms | 6.9 KiB |
| 16 | 0.0107 ms | 0.1563 ms | 8.0 KiB |

最大 P99 `0.6379 ms`，通过 `≤10 ms` 完整帧研究门。allocation 包含 planner arrays、
token queries 和偶发 immutable rebuild，当前只报告，不宣称 audio-thread 零分配。

## 边界

```text
trajectory_cache_measured=true
client_level_snapshot_producer_measured=false
client_level_read=false
minecraft_client_started=false
minecraft_integration_enabled=false
live_early_renderer_enabled=false
physical_endpoint_opened=false
captures_audio=false
cuda_executed=false
release_calibrated=false
```

本轮没有把 tracker 接到 Minecraft block/chunk 回调。因此它证明核心失效算法和轨迹
成本，不证明真实世界事件覆盖完整。根 Gradle/Fabric Loom 仍未作为通过证据；仅使用
Java 21 独立编译、JUnit Platform、reference CLI 和独立 Python verifier。

## 下一步

D121g 应研究 Minecraft 官方/原生可接入的 dirty 事件面，并先做静态/API 证据：

1. 区分客户端 block update、chunk load/unload、section rebuild 与维度切换；
2. 建立唯一的 client-thread tracker adapter，禁止 worker 直接读世界；
3. 为漏报事件提供 generation safety net，但避免任意范围外变化全局失效；
4. 将 group snapshot token 带入 D121c latest-generation handoff；
5. 随后实现 early cluster delay/gain/direction slew DTO，仍不打开音频端点；
6. CUDA DDA 继续复用 complete coverage/token；unknown/stale 必须返回 incomplete。

## 证据哈希

| artifact | SHA-256 |
|---|---|
| D121f contract | `92f20e50473843c8f97db8c86f14ab9b0ac3e483e1086aab41f584e6d21d4d10` |
| Java trajectory report | `ccc990bcce39b71d0c7b03944f8cb63f15e26ad20a16e64b810d866d134de752` |
| independent verification | `ab669421bd037ce1284d0c405afadc3e90cb5331ea26b02e6feac10bde5f59b7` |
