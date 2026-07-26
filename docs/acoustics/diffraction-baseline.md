# Local-air A* Diffraction Baseline

更新日期：2026-07-24

## 实现边界

当前产品响应是用于 RQ2 对比的 **经验绕射基线**，不是 UDFA、UTD 或 Planeverb。
路径搜索已经加入 partition-local connected-air-region/portal hierarchy，并保留
cell A* correctness fallback。它只在直达路径的中频能量增益低于 `0.92` 时启用：

```text
client/game thread
  -> 沿声源—听者中心 DDA 建立半径 3 blocks 的局部空气走廊
  -> 只读取已加载 chunk 的 collision shape
  -> unknown/unloaded cell 标记为不可通行且快照 incomplete

single acoustic worker
  -> 8-block partition 内建立 connected-air regions
  -> 跨 partition boundary 聚合 portal aperture
  -> region A* 选择走廊
  -> 走廊约束的 6-neighbour deterministic cell A*
  -> 受限搜索失败时回退 unrestricted cell A*
  -> 4096 visited-node hard cap
  -> path simplification
  -> known-solid inside-corner voxel edge extraction（最多 3 条）
  -> extra path length + turn count
  -> empirical low/mid/high diffraction energy
  -> 与五探针直达能量相加并限制为 unity
```

直达结果首次变为遮挡后，下一个客户端 tick 才捕获空气走廊。因此方块变化和高速进入
声影区不会触发工作线程读取世界；最坏只保留上一份有效结果，随后由 latest-wins
批处理替换。

## 经验参数

下列全部是 `[H]` 初值，目的只是建立可比较、可替换的 B 基线：

| 参数 | low | mid | high |
|---|---:|---:|---:|
| 基础损失，dB | 4 | 10 | 18 |
| 每额外米损失，dB/m | 0.6 | 1.2 | 2.0 |
| 每次转向损失，dB | 1.5 | 3.0 | 5.0 |

最终绕射损失为 `base + extraLength × perMeter + turns × perTurn`。该形式只保证
距离更长、转角更多和频率更高时衰减单调增加，不宣称具有物理精度。参数必须由
墙边、门、L 形走廊、相邻房间、竖井和洞穴的实测/主观 ABX 标定替换。

## 平滑与失效策略

- 直达和绕射在能量域合并；
- 三频带每次传播结果的变化限制为 `3 dB`；
- occlusion attack 插值系数为 `0.42 [H]`，release 为 `0.20 [H]`；
- 不完整材质快照、未加载 chunk、DDA/A* 超预算不发布新结果；
- 连续 20 tick 没有有效结果后向无传播损失状态松弛。

## 可复现性能门槛

命令：

```text
gradlew :computational-acoustics-core:portalBenchmark -Psearches=1000
```

2026-07-24，Intel Core i7-14700KF / Microsoft OpenJDK 25.0.1，固定
`65 × 7 × 7 = 3185` 个采样 cell 的墙洞绕行场景：

| mode | searches | average cell visited | average | p50 | p95 | max |
|---|---:|---:|---:|---:|---:|---:|
| cell oracle | 1000 | 453 | 213.2 µs | 189.5 µs | 317.1 µs | 530.0 µs |
| hierarchical cold rebuild | 1000 | 229 | 740.0 µs | 695.4 µs | 1.131 ms | 2.277 ms |
| cached warm | 1000 | 229 | 331.6 µs | 304.4 µs | 484.6 µs | 905.5 µs |
| cached one-partition change | 1000 | 229 | 357.6 µs | 326.0 µs | 537.6 µs | 1.325 ms |

所有路径均通过 `worker p95 ≤ 4 ms` 门槛。hierarchical 模式的 cell visit 减半；
partition fingerprint 缓存把 p95 从 1.131 ms 降到 484.6 µs，单 partition 变化为
537.6 µs。它不包含 Minecraft 主线程 collision-shape
采样，也不是跨机器承诺；后续游戏内 profiler 必须分别报告 snapshot 与 worker 时间。

## 下一研究方向

1. 用相同 wall-edge/door/L-corridor 场景比较：
   A. boolean occlusion；B. 当前 extra-length 经验模型；C. 独立实现的
   UDFA/UTD edge response。
2. 为 portal adjacency assembly 增加 boundary-level 增量缓存；connected component
   topology 已按 fingerprint 只重建变化 partition，但当前仍重新组装全局 adjacency。
3. C 只有在录音或盲听中相对 B 有稳定可闻收益且仍满足预算时才进入产品路径。
4. Planeverb 风格 2.5D 低频层只作为高质量实验，不与本基线混写。
