# D121o — 默认关闭的固定容量原生事件追踪

## 结论

D121o 为后续经授权的 Minecraft packet/GameTest 实测补上了一个可审计、固定内存、
默认关闭的 callback trace。`MinecraftAcousticWorldDirtyTracker` 现在可以把
world replace、block apply、chunk load/unload、规范端口结果和 adapter 层旧世界拒绝
记录到 `BoundedAcousticWorldEventTrace`，但只有 JVM 属性
`fpvdrone.acoustics.nativeEventTrace=true` 时才启用。

离线参考状态为 `verified-minecraft-native-world-event-trace`：

- capacity `8`；
- recorded `12`；
- retained `8`；
- overwritten `4`；
- 保留 ordinal `5..12`，严格按时间顺序导出；
- 全部参考事件具有相同且为正的 producer thread id；
- disabled probe 即使收到无效参数也保持 `0 recorded / 0 retained / 0 overwritten`；
- 五个各 100,000 次 `record(...)` 的稳态 allocation window 均为 `0 bytes`。

Fabric 实际容量冻结为 256。满载后覆盖最旧条目并增加 `overwritten`，不阻塞 producer，
不扩大内存，也不声称完整保存所有历史。

## 数据合同

每条记录只包含：

1. 全局 trace ordinal；
2. 当前 active world epoch；
3. 当前世界内的 local sequence；
4. producer thread id；
5. kind；
6. disposition；
7. inclusive integer bounds。

它不包含 block state、材质、实体、玩家信息、PCM、音频对象或 `ClientLevel` 引用。

事件类型为 `WORLD_REPLACE`、`BLOCK_APPLY`、`BLOCK_ROLLBACK`、`CHUNK_LOAD`、
`CHUNK_REPLACE` 和 `CHUNK_UNLOAD`。结果既包括规范端口的 accepted/duplicate/conflict/
out-of-order/world-mismatch，也包括 adapter 在调用端口前发现旧 `ClientLevel` identity
时使用的 `ADAPTER_WORLD_MISMATCH_REJECTED`。

旧世界 callback 在 adapter 层没有可恢复的旧 epoch/packet sequence，因此 trace 记录
当前 active epoch、`localSequence=0` 和 adapter mismatch disposition。这是明确的
“到达但未进入规范端口”语义，不能解释为服务器原始序号。

## 有界性与分配

构造时一次性创建 primitive/enum arrays，之后 `record(...)` 只写已有槽位。disabled
检查位于参数验证前，所以关闭状态是严格 no-op，不会因诊断调用影响生产行为。

导出 API 要求调用方预先创建 `Entry[]` 及每个 mutable `Entry`，并使用
`copyChronological(...)` 覆盖填充；记录路径不会为导出预留临时对象。若输出数组不足
或 entry 为空，导出立即失败，不返回截断的、容易误读的轨迹。

JVM allocation counter 在全新 Gradle JVM 的第一个测量窗观察到一次性 `1416 bytes`；
增加一个明确不计入合同的测量预热窗后，其后五个稳态窗口均为零。这个处理只排除
计数器/monitor/JIT 一次性初始化，没有删选五个正式窗口中的较差样本。

## Fabric 接线

`MinecraftAcousticWorldDirtyTracker` 使用：

```text
Boolean.getBoolean("fpvdrone.acoustics.nativeEventTrace")
```

属性缺失、拼写错误或任何非 `true` 值都会保持关闭。启用时：

- world replacement 记录 sequence 0；
- 当前世界的 block/chunk 先取得 local sequence，调用规范端口，再记录 outcome；
- 旧世界 callback 不消耗当前世界 sequence，记录 sequence 0 和 adapter mismatch；
- thread id 在 callback 所在线程通过 `Thread.currentThread().threadId()` 取得。

这仍不是运行时线程证明。离线 replay 的单线程结果只证明字段保存与独立重算正确；
必须运行真实 client 后，才能判断 Fabric/mixin callback 是否全部在预期 client thread。

## 可复现验证

```powershell
.\gradlew.bat --no-daemon verifyMinecraftNativeWorldEventTrace
python -m pytest -q `
  docs\scripts\test_verify_minecraft_endpoint_disabled_acoustic_replay.py `
  docs\scripts\test_verify_minecraft_canonical_world_event_replay.py `
  docs\scripts\test_verify_minecraft_native_world_event_trace.py
.\gradlew.bat --no-daemon `
  :computational-acoustics-core:test :fabric-mod:test
```

Independent verifier 重建全部 retained entries，检查 ordinal/epoch/sequence/type/
disposition/bounds、单线程 identity、计数、禁用行为和 allocation，并扫描 Fabric
adapter 的默认门、容量、thread id 和 stale-world disposition。

最终相关回归：

- computational-acoustics-core：`233/233`，0 failure/error；
- Fabric：`467/467`，0 failure/error；
- D121m/D121n/D121o Python verifier tests：`7/7`；
- named Minecraft 1.21.11 `compileClientJava`：通过；
- `git diff --check`：通过。

冻结 SHA-256：

- contract：
  `fd3946486567b2de648e45785f8f58864311b7191d3e8cc623cdb9b8c5161a46`
- report：
  `0848a40915d20e52854acde6a200ee3ecbb54e234026e800ea3dfa80004f55bd`
- verifier：
  `bf8f8e8a4129529956f554bd9b98c1127b254b9aa3b877bd11f8ae5148ea137e`
- verification：
  `32cc44da516585aa9902e38f60eaf7af0e191a153a902296d902b75a14eb2bea`
- Fabric adapter：
  `0f639cfc63ff58fb30efdf6c15381103e77d1fc9dbeee6637fcfc9ff04930cbb`

## 边界

本轮保持：

- `minecraft_client_started=false`
- `client_level_read=false`
- `native_callback_delivery_measured=false`
- `physical_endpoint_opened=false`
- `captures_audio=false`
- `cuda_executed=false`
- `release_calibrated=false`

因此没有真实 callback 被采集，也没有证明 packet、prediction rollback、chunk replace、
dimension switch 的运行时事件数量或顺序。

## 下一步

D121p 的真实验证需要用户明确授权启动 Minecraft client。授权后应使用专用诊断启动
配置，同时满足：

1. `fpvdrone.acoustics.nativeEventTrace=true`；
2. live early renderer、EFX/FDN 写入和所有 D121g research scheduler 保持关闭；
3. 禁止创建 capture device，不调用麦克风；
4. 固定时间/事件数量后导出最多 256 条记录和 overwritten 计数；
5. 在进程外用 D121n 规范端口与独立 verifier 重放；
6. 测试结束后确认属性恢复为缺失/false，且没有常驻诊断 owner。

未获得授权前，不应把 D121o 的离线 ring replay 称为 native callback 实测。
