# D121c — 多 source 最新代际 worker handoff

## 结论

D121b 的连续局部平面场景现在可以通过一个真实后台线程传递，但仍未接入 Minecraft
实时渲染或音频端点。新 handoff 的目标不是“排完所有历史任务”，而是确保快速移动的
FPV source 永远优先使用最新完整快照：

```text
client tick producer
  -> 每 source 一个 pending slot
  -> latest generation wins
  -> 单 worker + reusable scene workspace
  -> 求解后再次比对最新 generation
  -> fixed-capacity primitive result DTO
  -> client consumer 只应用期望 generation
```

容量固定为 16 个 source；每个 source 最多一个待处理请求。尚未开始求解的旧请求允许
被覆盖；已经开始但在完成前被新 generation 超越的结果必须丢弃，不能发布。

## 所有权与线程边界

`LocalPlaneSceneWorkerHandoff` 只接收：

- source/listener 六个有限标量坐标；
- source id 与正整数 generation；
- 冻结的 `SceneSnapshot`：
  - immutable local-plane patch list；
  - immutable exact-shape blocker；
  - coarse DDA query；
  - 每条腿的 cell budget；
  - complete 标志。

worker 不持有或读取 `ClientLevel`、`BlockState`、`VoxelShape`、OpenAL、PCM、音频 stream
或 renderer state。它拥有一个复用的 `LocalPlaneSceneSolver.Workspace`。发布结果是固定
容量 primitive DTO，最多保存 6 条 selected paths 和 6 个 clusters。

线程同步使用一个固定 monitor 和预分配 source slots。submit、worker publication 与
consumer copy 都在很短的临界区内完成；连续几何求解位于锁外。consumer 必须以当前
期望 generation 调用 `pollLatest`，因此旧的已发布结果也不能冒充最新结果。

## 确定性功能夹具

三个夹具均由真实 worker thread 执行：

1. **pending overwrite**：worker 启动前依次提交 generation 1、2，只发布 2，
   `pending_overwrites=1`；
2. **stale solve**：generation 1 在可控 DDA query 内暂停，producer 提交 generation 2
   后释放；generation 1 完成但被丢弃，最终只发布 2，
   `stale_solved_results=1`；
3. **incomplete snapshot**：发布 `complete=false`、
   `conservative_fallback=true`、`selected_count=0`，即不生成显式 early path。

这避免了依靠线程竞争概率来“碰巧”覆盖 stale-result 分支。

## 离线多 source 基准

每种 source count 先执行 100 个 warm-up frames，再测 500 frames。每 frame 同时提交
该数量的 source，等待全部同 generation 结果应用。报告保存每个 batch 和每个 scene
的原始 queue、solve、publish、end-to-end、consumer-copy 样本；独立 Python verifier
重新计算 nearest-rank P50/P95/P99，并逐样本验证：

```text
end_to_end >= queue + solve + publish
```

| sources | batch P50 | batch P95 | batch P99 | queue P99 | solve P99 | apply P99 |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 8.5 µs | 24.7 µs | 38.6 µs | 10.0 µs | 20.4 µs | 8.5 µs |
| 4 | 12.7 µs | 22.1 µs | 45.8 µs | 26.0 µs | 3.1 µs | 2.2 µs |
| 8 | 26.5 µs | 48.1 µs | 83.5 µs | 48.1 µs | 5.4 µs | 1.6 µs |
| 16 | 48.8 µs | 94.4 µs | 206.9 µs | 148.9 µs | 6.0 µs | 1.2 µs |

所有 source counts 均通过冻结门槛：

- batch P99 `≤25 ms`，即不超过 50 ms Minecraft tick 的一半；
- per-scene solve P99 `≤100 µs`；
- consumer copy P99 `≤100 µs`。

结果不能解释为 16 架无人机的完整 Minecraft tick 成本：它包含实际 worker thread、
队列、连续场景求解与 DTO copy，但不包含 `ClientLevel` 读取、区块可用性检查、
`VoxelShape` AABB 提取、AABB union surface reconstruction、渲染参数平滑或音频处理。

## 分配与完整性

主线程 steady-state allocation 以五个各 2,000 次的窗口测量：

- submit-only：`[0,0,0,0,0] bytes`；
- submit + actual-worker + consumer-copy：`[0,0,0,0,0] bytes`。

独立 verifier 不信任 Java 报告的 percentile、gate 或 allocation aggregate：

- 从全部原始 timing samples 重算分位数；
- 校验样本数量等于 `frames × sources`；
- 逐样本校验时间分解；
- 重算五窗口 allocation aggregate；
- 验证 source counts 必须严格为 `[1,4,8,16]`；
- 验证 pending overwrite、stale solve 和 incomplete fallback 的 generation/counter。

最新 Python 回归集合共 11 项，全部通过。

## 声明边界

本轮状态为：

```text
worker_handoff_measured=true
minecraft_integration_enabled=false
live_early_renderer_enabled=false
physical_endpoint_opened=false
captures_audio=false
minecraft_client_started=false
cuda_executed=false
release_calibrated=false
```

这里的 `worker_handoff_measured=true` 只表示离线 core worker 的真实线程边界已经测量，
不表示 Minecraft client tick 或 sound thread 集成完成。

根 Gradle/Fabric Loom 配置仍会停滞，因此本轮继续只承认 Java 21
`javac --release 21`、直接 Java reference 与独立 Python verifier，不承认完整 Gradle
gate。

## 下一步

D121d 应测量真实 snapshot producer，而不是立刻启用声音：

1. 在 Minecraft client tick 仅针对已加载区块捕获 1/4/8/16 个移动 source 的
   `VoxelShape`；
2. 分开测量 world read、`optimize().toAabbs()`、union reconstruction、submit、
   queue、solve 与 apply；
3. 把 capture 半径、总 AABB 数、union grid cells 和每 tick 新建 snapshot 数设为明确
   上限；
4. 实现跨 source 共享或 listener-centred scene snapshot，避免 16 次重复读取同一体素；
5. 测量连续轨迹下 generation churn、pending overwrite 比例和 conservative fallback
   占比；
6. 在没有 endpoint/capture 的条件下验证 delay/gain slew DTO；
7. 只有上述门槛通过，才把 early cluster DTO 接入现有 render state。

CUDA 仍保持静态合同：本机没有 `nvcc`，未编译或运行 CUDA。GPU 版本必须复用同一
snapshot/expected-results contract，并在包含上传、同步和回读后优于当前 CPU worker
链路才有继续价值。

## 证据哈希

| artifact | SHA-256 |
|---|---|
| D121c contract | `ddf11b22f331247d78fff515a7a85050f41ef5739c42014b7d181f7ddd6bfe33` |
| Java worker report | `3452a70cf6ab70507630e02d696fc11f786b2f3f2aac97ac1d40c92a654802b7` |
| independent verification | `bf20c6ac68723189e436e055279b37ef07143257e066ac3b2ca47b105fa0de5e` |
