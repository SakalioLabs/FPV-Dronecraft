# D121n — Minecraft 规范声学世界事件端口

## 结论

D121n 把 Minecraft/Fabric 的 block、chunk、world-change 回调与声学快照失效逻辑
之间的协议抽成了不依赖 `ClientLevel` 的
`CanonicalAcousticWorldEventPort`。端口只拥有世界代次、事件序号、最多 4 个 active
coverage、最后一个已接受事件签名和 `CoverageDirtyTracker`；它不读取世界几何、不接触
renderer、声音设备或 CUDA。

固定轨迹的独立验证状态为
`verified-minecraft-canonical-world-event-replay`。13 个观测点覆盖：

- block apply；
- 完全重复；
- 同序号不同载荷；
- coverage 外更新；
- 预测回滚；
- 乱序旧事件；
- chunk unload / replace / coverage 外 load；
- 维度替换、旧世界迟到事件和新世界序号重启。

最终统计为 `7 accepted / 5 dirty / 1 duplicate / 1 conflicting /
1 out-of-order / 1 world-mismatch`，共标记 `8195` 个 cell。世界代次从 1 变为 2 时，
dirty tracker identity 被替换、coverage 被清空、事件序号重新从 1 开始；旧世界事件
未改变新 tracker。

## 协议

### 世界身份

世界身份使用正整数 `worldEpoch`，不把 Minecraft 对象或其 hash 带入核心：

1. epoch 必须单调增加；
2. 替换世界会创建新的 `CoverageDirtyTracker`；
3. 替换时清除 active coverage、最后序号与最后事件签名；
4. 事件携带的 epoch 与当前 epoch 不相等时 fail closed，不标脏。

Fabric adapter 仍以 `ClientLevel` 对象 identity 判断当前回调属于哪个世界，但只把本地
单调 epoch 交给核心。因此核心没有 `ClientLevel` 所有权，维度切换也不会复用旧 token。

### 顺序和去重

每个 epoch 内事件序号从 1 开始严格增加：

- `sequence > lastSequence`：接受；
- `sequence == lastSequence` 且 type/坐标完全一致：忽略为 exact duplicate；
- `sequence == lastSequence` 但载荷不同：拒绝为 conflicting duplicate；
- `sequence < lastSequence`：拒绝为 out of order。

端口只保留最后一个签名，内存上界固定，不维护随运行时间增长的集合。该规则要求未来
packet/GameTest producer 提供有语义的序号。当前 Fabric lifecycle 和
`ClientLevel.setBlock` 回调没有暴露服务器 packet sequence；adapter 因而按 client
thread 到达顺序生成本地序号。两个相同的原生回调会被保守地视为两次有效变化并再次
标脏，而不能冒充已证明的网络去重。这样会增加 rebuild，未观察到漏失效风险。

### 回滚

`BLOCK_ROLLBACK` 不撤销 dirty revision。声学缓存只需要知道捕获范围内的最终几何
可能已经改变，因此 apply 后的 rollback 是一个新序号的新 dirty 事件。当前
`ClientLevel.setBlock` RETURN hook 无法可靠区分普通 apply 与 prediction rollback，
所以 Fabric adapter 暂映射为 `BLOCK_APPLY`；显式 rollback type 已在核心协议和回放中
冻结，等待可区分的原生信号。

### chunk 事件

`CHUNK_LOAD`、`CHUNK_REPLACE` 与 `CHUNK_UNLOAD` 使用 inclusive X/Z footprint，只把
footprint 与每个 active coverage 的交集标脏，Y 范围沿用 coverage。当前 Fabric
adapter 有原生 load/unload 信号；没有独立 replace 信号，实际 unload→load 或再次
load 都会保守失效，核心的显式 replace 语义由离线轨迹验证。

## Minecraft 1.21.11 接线

`MinecraftAcousticWorldDirtyTracker` 仍只使用：

- `ClientChunkEvents.CHUNK_LOAD`；
- `ClientChunkEvents.CHUNK_UNLOAD`；
- `ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE`；
- 成功返回的 `ClientLevel.setBlock(...)` mixin。

它不再直接调用 `CoverageDirtyTracker.markDirty`，所有 block/chunk 变化都经过规范端口。
调度器继续通过 `tracker(level)` 取得当前 tracker，并通过
`replaceActiveCoverage(...)` 更新最多 4 个捕获范围。

`:fabric-mod:compileClientJava` 已针对项目锁定的 named Minecraft 1.21.11 和 Fabric
API 成功；对应 source-contract JUnit 也通过。这是静态签名/编译证据，不是运行时
callback delivery 证据。

## 可复现验证

```powershell
.\gradlew.bat --no-daemon verifyMinecraftCanonicalWorldEventReplay
python -m pytest -q docs\scripts\test_verify_minecraft_canonical_world_event_replay.py
.\gradlew.bat --no-daemon :fabric-mod:compileClientJava :fabric-mod:test `
  --tests "*MinecraftAcousticWorldDirtyTrackerContractTest"
```

独立 verifier 不信任 Java 报告中的 gates，而是重新检查全部 13 个 event identity、
outcome、epoch、sequence、revision 和 coverage token，并扫描 Fabric adapter，要求
原生入口通过 `CanonicalAcousticWorldEventPort` 且不存在直接
`tracker.markDirty(...)`。

最终相关回归为 core `230/230`、Fabric `467/467`，均无 failure/error；D121m 与
D121n 的 Python verifier 单测合计 `4/4`。`git diff --check` 通过。

本轮冻结 SHA-256：

- contract：
  `5d4e8e31a0605f25457fbf92a4f0b1ac5faa1d80e4ee6c555ab16b6c8da762ad`
- report：
  `1850e588226876bf157d509f86709c4ddef78b7b9ba9d63e8f7766a29e5af0d7`
- Fabric adapter：
  `0c95858f45a95d2c103d78772411118e41513010f07f3ad85075ab9f5e403358`
- verifier：
  `07eedca4be242e8af0da2bba9674360fc181fc87f9e32baf5eaa284066f5337a`
- verification：
  `c0370740c79e4ad4c1258d7f4818cfc89ec1a6e684db2b82332ca2ef9c82a1c2`

## 边界

本轮：

- `minecraft_client_started=false`
- `client_level_read=false`
- `physical_endpoint_opened=false`
- `captures_audio=false`
- `cuda_executed=false`
- `release_calibrated=false`

因此尚未证明真实 packet 到达是否会触发一次或多次 `setBlock`，也没有证明断线、
dimension replacement、预测修正和 chunk replace 在运行时的真实回调顺序。

## 下一步

D121o 应在用户明确允许启动 Minecraft client 后，建立强制禁用声学 renderer 和物理
endpoint 的有界 GameTest/packet-delivery 诊断：

1. 给每个 native callback 记录只含 epoch、local sequence、type、坐标与 thread
   identity 的固定容量 ring buffer；
2. 执行 block packet、相同 state、预测修正、chunk unload/reload、dimension switch；
3. 将实际 callback trace 导出后交给同一个规范端口离线重放；
4. 比较 native event 数、dirty token、cache rebuild/fallback 和 stale-world 拒绝；
5. 全程证明 renderer、OpenAL source、capture device 均未创建。

在获得这项授权前，可以继续离线设计 ring-buffer 合同和 fail-closed 启动门，但不能把
D121n 的语义回放描述成真实 Fabric callback 实测。
