# Direct Voxel Propagation

更新日期：2026-07-24

## 运行时设计

直达传播以 20 Hz（每客户端 tick）更新。每个声源使用
`中心 0.40 + 四个 aperture rim 各 0.15` 的五探针：

```text
client/game thread
  -> enumerate five probe-listener DDA paths
  -> query only loaded chunks
  -> map BlockState + collision volume to a sparse immutable material snapshot
  -> submit one batch containing every active drone

single acoustic worker
  -> discard/replace stale pending batch
  -> repeat five deterministic DDA paths over immutable snapshots
  -> accumulate low/mid/high transmission loss by in-voxel path length
  -> energy-weight the five path gains and publish one immutable batch result

client/game thread
  -> reject stale UUID, incomplete snapshot and over-budget result
  -> temporal smoothing
  -> transform tonal pressure amplitude by sqrt(energy gain)
  -> transform broadband energy directly by energy gain
```

Minecraft world/chunk/block APIs never run on the worker or sound streaming thread。两个
motor/propeller `AudioStream` 共享同一个 `DroneAcousticRenderState`，因此不会重复
声源模型或 DDA。

## DDA 语义

- Amanatides–Woo 参数范围为 `[0, 1]`；
- visitor 接收每个 voxel 内的实际穿行长度，单位 m；
- 射线恰好穿过 edge/corner 时同时推进所有相等轴，不插入零厚度旁侧 voxel；
- 默认预算为 192 cells，覆盖当前 64 m voice release distance；
- acoustic aperture radius 由 `max(horizontal rotor-center radius + prop radius)` 从
  `RotorSpec` 计算并同步；rim 使用其 85%，而不是只使用单片桨半径；
- 未加载 chunk、采样预算耗尽或 worker 失败时结果标记 incomplete，旧的有效结果继续
  使用，不在音频线程读取世界作为补救；
- CPU 实现是 CUDA/OpenGL compute 后端必须逐射线匹配的规范结果。

## 初始材料映射

材料参数仍为 `[H]`，不是测量得到的绝对 transmission loss：

| 类别 | 主要 Minecraft 映射 | 低/中/高频 TL，dB/m |
|---|---|---:|
| foliage | leaves、grass、vine、crop、cobweb | 0.3 / 1.2 / 3.0 |
| soft | wool、carpet、dampens-vibrations | 2 / 7 / 13 |
| wood | wood、bamboo、ladder、scaffolding | 4 / 9 / 15 |
| glass | glass SoundType | 3 / 8 / 14 |
| stone | 默认致密方块 | 12 / 24 / 36 |
| metal | metal、iron、copper、chain、anvil | 8 / 20 / 35 |
| water | 无碰撞流体 voxel | 6 / 18 / 30 |

台阶、门、栅栏、玻璃板等非满方块按 collision AABB 总体积缩放有效厚度；树叶至少
使用 `0.35` 的多孔占比，无碰撞植物使用 `0.15`。这些近似必须通过封闭门/开门、
玻璃、树叶和水下 acoustic lab 场景标定。

## 2026-07-24 CPU 基准

命令：

```text
gradlew :computational-acoustics-core:ddaBenchmark -Piterations=3
```

环境：Intel Core i7-14700KF，20 cores / 28 logical processors，Microsoft OpenJDK
25.0.1。每条射线约 64–100 cells，计时包含 DDA、材质查询、三频带累计和结果构造。

| Batch | ns/ray | rays/s |
|---:|---:|---:|
| 32 | 24,042 | 41,594 |
| 128 | 4,016 | 249,027 |
| 512 | 2,682 | 372,797 |
| 2,048 | 1,959 | 510,447 |
| 8,192 | 1,764 | 566,991 |
| 32,768 | 1,606 | 622,669 |

32-ray 行受短基准和 JIT 预热影响较大，不能用作精确 latency 结论；它接近当前产品
最多 `6 drones × 5 probes = 30 rays`、20 Hz 更新的规模，CPU 成本仍远低于音频预算。
CUDA 在这里会增加 JNI、kernel launch、数据驻留和同步成本，预计没有净收益。

CUDA 评估继续限定在早期反射的 `>=8192 rays/update` 大批次。未来后端必须输出同一
CSV schema，并报告完整端到端时间；只报告 kernel 时间不通过 gate。

## 当前限制

- 已有局部 sparse-air cell A* 经验绕射基线，但还没有 connected-region portal
  abstraction、UDFA/UTD edge response 或 early reflections；
- 多探针目前表达整个多旋翼 acoustic aperture，尚未同步每个 rotor center 和真实
  spin direction；
- 不完整快照保持上一有效结果；连续 20 tick 没有有效更新后缓慢回归 unity；
- 还没有实际游戏音频设备的长时间 underrun/重载验证。
