# D121m — 禁用物理端点的端到端声学 replay

## 决策

D121m 建立了 renderer contract v2 的第一条完整离线数据链：

```text
world-event trace
→ exact coverage dirty token
→ immutable snapshot cache
→ latest-generation worker
→ early reflection clusters
→ 64-sample slew/fallback fade
→ low/mid Lagrange 3 + high sinc8 renderer
→ pure-memory stereo wet bus
```

该 replay 通过。它证明核心接口可以端到端组合，但没有证明 Fabric/Minecraft 在真实客户
端上会投递相同事件，也没有读取 `ClientLevel`、启动 client 或创建 OpenAL/PCM endpoint。

## 冻结事件轨迹

场景为 `5×4×5` inclusive coverage、halo 1、stone diagnostic hypothesis 地面、
一个移动 source 和一个固定 listener。stone 参数仍是未校准假设，不是实测 Minecraft
材料。

| tick | 事件 | cache/snapshot 结果 | worker/render 结果 |
|---:|---|---|---|
| 0 | initial capture | rebuild，complete，2 patches | 1 cluster |
| 1 | coverage 外 block update | cache hit | 1 cluster |
| 2 | coverage 内 block update | rebuild，complete | 1 cluster |
| 3 | chunk unload | rebuild，incomplete | conservative fade |
| 4 | chunk reload | rebuild，complete | 1 cluster |
| 5 | capture 中再次 block update | dirty capture discarded | conservative fade |
| 6 | stable retry | rebuild，complete | 1 cluster |
| 7 | no world change | cache hit | 1 cluster |
| 8 | world change | new tracker identity，rebuild | 1 cluster |

总计：

```text
events = 9
cache hits = 2
rebuilds = 7
incomplete fallbacks = 2
complete results with clusters = 7
dirty-during-capture discards = 1
```

coverage 外更新不重建，coverage 内更新重建；unload 不会把未知 cell 当作空气；
world change 即使几何内容相同也会因 tracker identity 改变而重建。

## Latest-generation probe

一个独立 handoff 在 worker 启动前依次提交 generation 1 和 2。结果为：

```text
applied_generation = 2
pending_overwrites = 1
published_results = 1
stale_solved_results = 0
```

generation 1 没有发布或进入 renderer。

## Wet bus 证据

报告保存全部 `9×256×2 = 4,608` 个 stereo wet samples。独立 Python verifier 重算
每 tick RMS、末样本、全局 checksum 和最大相邻变化：

```text
maximum adjacent wet step = 0.1439379919
frozen limit = 0.25
wet checksum = 0.5537800981
all samples finite = true
```

初始 block 为零是因为反射到达时间超过当时已有的 delay history，不是缺少 cluster；
后续 complete ticks 产生非零 wet bus。两个 incomplete ticks 均在 64-sample ramp 后
于同一 256-sample block 内严格归零。

renderer 的每次 control submit 均通过逐频带 endpoint energy 检查。最终固定 control
frame 的五个稳态 allocation windows 均为 `0 bytes`。

## 与先前门的关系

独立 verifier 强制要求：

- D121k 仍为
  `verified-minecraft-high-band-fractional-delay-runtime-gate-failed`；
- D121l 为
  `verified-minecraft-paired-high-band-renderer-budget`。

因此 D121m 不能用 replay 通过来抹去 D121k 的绝对 wall P99 失败。renderer v2 仍只能
显式选择，默认 renderer 和 live Minecraft 声音路径没有改变。

## 尚未证明的部分

1. 轨迹是语义等价的确定性事件，不是真实网络 packet；
2. 没有验证 Fabric `ClientChunkEvents`、mixin `ClientLevel.setBlock` 和
   `ClientWorldEvents` 的运行时投递顺序；
3. 没有测真实 Minecraft tick 与 render/audio thread 调度；
4. 没有声卡输出、听感、HRTF 或真实无人机校准；
5. 没有执行 CUDA；本轮与 native DDA 后端无关。

## 下一步

D121n 应把 Fabric 事件入口抽取为不持有 `ClientLevel` 的小型 event DTO/port：

1. block update、chunk load/unload、world change 使用同一 canonical event enum；
2. 当前 mixin/Fabric callback 只负责把坐标和 world identity 写入 port；
3. endpoint-disabled trace 通过同一 port replay，而不再直接调用 core tracker；
4. 静态编译验证 1.21.11 named/remapped Fabric 签名；
5. 增加 packet reorder、duplicate、rollback 和 dimension replacement 轨迹；
6. 仍不启动 client；只有 port replay 和静态边界通过后，再请求真实 GameTest 授权。

## 边界

```text
live_early_renderer_enabled=false
minecraft_integration_enabled=false
client_level_read=false
minecraft_client_started=false
physical_endpoint_opened=false
captures_audio=false
cuda_executed=false
release_calibrated=false
```

## 证据哈希

| artifact | SHA-256 |
|---|---|
| D121m contract | `4d01e8e9ecdaada8dba80a0e8528ae989ef5cc34ca06aa7507693aa8fd692264` |
| Java replay report | `369d8c978fbf0e492618ebfe928bd2ae995832927270bfc0574b86f74b3bf6cf` |
| independent verification | `a25a8a0241d0d52998340eabe3b8dffd0f31b03031720d2806fc91fef74b7e90` |
