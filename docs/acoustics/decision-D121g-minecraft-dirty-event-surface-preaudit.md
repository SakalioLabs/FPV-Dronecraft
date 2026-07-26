# D121g — Minecraft 声学 dirty 事件面预审计

## 当前结论

Minecraft/Fabric 1.21.11 可以用较小的原生事件面驱动 D121f tracker：

- Fabric `ClientChunkEvents.CHUNK_LOAD`：chunk 已进入 client world 后触发；
- Fabric `ClientChunkEvents.CHUNK_UNLOAD`：chunk 仍在 world、即将卸载时触发；
- Fabric `ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE`：维度/世界实例切换后触发；
- Fabric lifecycle-events v1 没有通用 client block-state change 事件；
- named `ClientLevel` 明确覆盖四参数
  `setBlock(BlockPos, BlockState, int, int)`，因此使用 RETURN 注入并只接受 `true`。

Fabric 事件语义可在其
[ClientChunkEvents API](https://maven.fabricmc.net/docs/fabric-api-0.110.0%2B1.21.1/net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientChunkEvents.html)
核对；1.21.11 的 native 方法面可在
[ClientLevel mappings](https://mappings.dev/1.21.11/net/minecraft/client/multiplayer/ClientLevel.html)
核对。本地实际依赖为 Fabric API `0.141.4+1.21.11`，并直接读取了该版本 source jar。

## 已实现但尚未 live 测量

`MinecraftAcousticWorldDirtyTracker` 已注册 chunk/world 官方事件，最多保存 D121d 的
4 个 active capture coverages。block change 只在 exact coverage 内写 revision；
chunk load/unload 只标记 chunk XZ 与 coverage XYZ 的交集，不遍历完整世界高度。

`ClientLevelAcousticDirtyMixin` 只在 `setBlock` 成功返回后标记 block cell。世界切换会
替换整个 `CoverageDirtyTracker` 实例并清空 active bounds；D121f cache 现在把 tracker
identity 纳入 key，因此相同数值 token 也不能跨维度误复用。

适配器与 mixin 已对 named Minecraft 1.21.11、remapped Fabric lifecycle API 和 Mixin
库独立编译通过；两项 source-contract 测试覆盖事件注册、四组上限、成功返回门和 mixin
配置。

## Opt-in scheduler 静态接入

`MinecraftLocalPlaneSceneScheduler` 已接到现有 `END_CLIENT_TICK` 控制链，但默认关闭：

```text
fpvdrone.acoustics.localPlaneSchedulerResearch=false
```

只有显式设为 `true` 且 internal propagation 获准时才启动 worker。数据流为：

```text
最多 6 个 active drones
→ D121d 最多 4 个 shared coverages
→ D121g active dirty coverage
→ D121f immutable snapshot cache
→ exact material-box blockers + coverage
→ D121c latest-generation worker
```

worker 结果当前只被 poll 进入 telemetry 计数，不写入
`DroneAcousticRenderState`、OpenAL、PCM、gain、delay 或 renderer。scheduler、snapshot
adapter、dirty adapter、mixin 和修改后的完整 `DroneSoundManager` 均已对 named 1.21.11
直接编译通过；scheduler/事件 source-contract 与核心回归合计 68 项通过。

## 尚未证明

- packet-driven block update 是否全部汇聚到 `ClientLevel.setBlock`；
- chunk packet replace 与预测回滚是否存在绕过路径；
- callback 的实际 client-thread 顺序；
- active coverage 更新与事件到达同帧时的真实回调线性化；
- 维度切换、disconnect、resource reload 的真实时序；
- live rebuild/fallback ratio。

因此当前只能标记默认关闭的静态/API scheduler 接入完成，不能标记
`runtime_event_delivery_measured`、`client_level_read` 或
`minecraft_integration_enabled`。

## 下一验证

需要建立一个强制禁用声音设备的 client GameTest/回放夹具，记录：

1. 单 block packet、邻居更新、流体变化和预测回滚；
2. whole-chunk load、unload、重新进入；
3. world/维度切换；
4. 每个事件对应的 tracker revision、cache hit/rebuild/fallback；
5. worker publication token 是否拒绝旧世界/旧 generation。

在可审计的无音频启动方式完成前，不执行该 live 夹具。本阶段也不打开 OpenAL/PCM
端点。CUDA DDA 的 coverage/stale 语义不变：任何 unknown 或过期 token 必须返回
incomplete。
