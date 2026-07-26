# D120 — 有界首阶增益、空间支持回退与碰撞事件簇

## 结论

D120 实现并验证了一个不会重复 D118 灾难性空间外推的首阶反射运行时内核：

- 保留连续 image-source 路径长度与既有 Java CPU DDA topology/visibility；
- 三频带反射能量只来自路径扩散、表面吸收、散射与一个严格限幅的小校准项；
- 校准项固定限制为 `[-6,+3] dB`；
- 正能量增益固定在 `[1e-6,1]`，不可见路径严格为零；
- source 与 listener 使用分离的闭区间空间支持域，任何一端越界都把校准修正强制归零；
- 六个首阶 arrival 按路径长度稳定排序，相邻间隔 `≤16 samples @ 48 kHz` 时合并事件簇；
- gain 与 clustering 都使用可复用 primitive workspace，热路径不建立对象。

这关闭的是“数值安全、回退语义、跨语言 parity 与核心分配”问题，不是声学精度问题。
对 330 条已经解盲的 dEchorate RIR 的探索性复算显示，未拟合
`SOFT/WOOD` 材质先验的 Top-4 overlap 为 `0.66333`，与随机期望 `0.66667`
相当；幅度 RMSE 为 `6.13547 dB`。因此本模型只获准成为安全 fallback，不获准成为
发布校准或 strongest-echo ranking 模型。

## 冻结公式

每个频带的首阶反射能量增益为：

```text
G_b =
  visible
  ? clamp_positive(
      (max(d_direct, 0.25) / max(d_reflected, 0.25))²
      × (1 - absorption_b)
      × (1 - scattering)
      × 10^(clamp(correction_b_db, -6, +3) / 10),
      1e-6,
      1
    )
  : 0
```

校准不受空间支持时，`correction_b_db` 必须精确等于 `0`。D120 没有拟合校准项。
`1e-6` 对应能量 `-60 dB`，只作为正路径数值下限；完全吸收或不可见路径仍可为零。
上限 1 防止反射路径相对直达参考产生能量增益。

材质状态映射 `0 → AcousticMaterials.SOFT`、
`1 → AcousticMaterials.WOOD` 只是当前集中材质表上的研究假设。dEchorate 的真实构造是
perforated rock-wool panel 与 Formica；本结果没有把二者测成 Minecraft 方块参数。

## 空间支持

D118 的主要失败是把几何特征离开训练范围后继续代入无约束 ridge。D120 明确分开：

- `sourceBounds`：只由训练 source 坐标建立；
- `listenerBounds`：只由训练 listener 坐标建立；
- 两者都包含查询端点时，才允许使用未来的有界 calibration correction；
- 否则回退到零修正的材质/路径公式。

以旧 microphones `10–24` 的 listener AABB 为支持域时：

| 分区 | supported | fallback |
|---|---:|---:|
| 旧位置 10–24 | 15/15 | 0 |
| 新位置 0–9、25–29 | 0/15 | 15 |

source 与 listener 不共用一个 AABB，避免固定 source 人为扩大 listener 插值区域。
轴对齐盒只是保守的 v1 机制；未来若引入非零校准，仍需预注册 spatial blocks、最近支持
距离或 convex hull，并另取 unseen 数据。

## 碰撞事件簇

D120 对每个位置的六个首阶 annotation 按 arrival 排序，以相邻 `≤16 samples`
连接为同一事件簇。330-RIR 语料中的 30 个空间位置得到：

- 每位置事件簇最少/中位/最多：`3/5/6`；
- `19/30` 个位置至少存在一次合并；
- 总计 `25` 条相邻连接被合并。

Java runtime 对每个 cluster：

- 三频带能量分别求和；
- arrival sample 以各路径总三频带能量加权；
- 保留 cluster 内路径数；
- 不分配列表或事件对象。

这解决了“相近路径不能作为独立监督/事件”的表示问题，但不自动解决 source
directionality、有限窗口互相污染或高阶反射。

## 跨语言与运行时验证

Java 参考向量包含：

- 真实 `5.705×5.965×2.355 m` 连续房间；
- direct + 六面首阶路径；
- 六种现有材质；
- 超出校准界限的正/负修正；
- supported 与 unsupported 两种结果；
- 六路径碰撞事件簇。

独立 Python verifier 重新计算 image-source 长度、每频带限幅、unsupported 回退与
cluster membership/energy/arrival。所有数值以 `1e-12` 相对/绝对容差通过。

本机热 JVM 的 gain + clustering 合并微基准：

- 五个 allocation windows：`[0,0,0,0,0] bytes`；
- P99：`481.25 ns/solve`；
- 研究门：`≤20 µs/solve`。

这不包含 Minecraft snapshot、worker handoff、moving-source cache、audio render、
其他模组争用或 GC pause，因此 `minecraft_integration_measured=false`。

## 330-RIR 探索性压力结果

所有 330 条 source-4 RIR 都重新读原 SOFA 波形并使用固定 17-sample echo window；
不执行 fit，不更换材料或阈值，并排除全吸声 baseline room 后比较 300 个
room×position groups、1,800 个 facet rows。

| 分区 | Top-4 overlap | Top-1 capture | RMSE dB | MAE dB |
|---|---:|---:|---:|---:|
| 全部位置 | 0.66333 | 0.65333 | 6.13547 | 4.48969 |
| 旧位置 | 0.67000 | 0.76667 | 6.00215 | 4.47087 |
| 新位置 | 0.65667 | 0.54000 | 6.26595 | 4.50851 |

输出本身保持安全：探索场景的正增益在 `[-25.8747,-4.8175] dB`，处于
`[-60,0] dB` 合同内。可是安全不等于预测准确；新位置排名仍低于随机期望。因此：

```text
bounded_material_fallback_admitted=true
unfitted_material_ranking_admitted=false
production_candidate_eligible=false
release_calibrated=false
```

## Minecraft 与 CUDA 决策

D120 代码尚未接入 Minecraft production audio graph。下一阶段可安全推进：

1. 把 material-only fallback 接到离线 Minecraft scene fixture，而不是直接改玩家声音；
2. 在 snapshot worker 中绑定真实 block material 与 shape-derived local plane；
3. 对移动 source/listener 测量 cache invalidation、每 tick source budget 与参数平滑；
4. 分开验证 early-event 能量是否与 FDN/EFX late energy 双重计数；
5. 取得新的、在读波形前预注册的空间数据后，才研究非零有界 correction。

本机仍没有 `nvcc`，所以 `cuda_executed=false`。当前 Java CPU 的七路径 DDA 和
gain/clustering 都已是亚微秒级核心基线；CUDA 只有在二阶/高射线批量、动态实体数量使
端到端 `transfer + kernel + synchronization` 稳定优于 CPU，并保持同一 snapshot、
材料表和 expected-results contract 时才值得继续。

## 证据

| artifact | SHA-256 |
|---|---|
| frozen contract | `8772d94f210e1b3f4412b7e26c057f8a67d8f1307e3be72482af77cc04c0dd88` |
| Java reference report | `016d9188dcd636bd8322893c9449d79655675539326655408eef0cd9a8412049` |
| independent reference verification | `ce544436d58a206143c69557bd5da93dc8d9be8e0794ae286b208d940f812cef` |
| 330-RIR exploratory report | `2195dd6f5f70ae220b9ca06410f465a53c75761ec24648722789f8a4c0862464` |
| full recomputation verification | `c57988ac92f337d2b442a32e1b4ef7dc37b33735fa14b01640d276fb077100f3` |

本轮没有打开物理音频端点、没有播放或录制声音，也没有执行 CUDA。

