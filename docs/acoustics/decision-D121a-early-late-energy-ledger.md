# D121a — Minecraft 离线场景前的 early/late 能量账本

## 结论

D121 的第一项前置风险已关闭：未来显式首阶反射事件与现有 listener-shared
EFX/FDN late reverb 不再可以无约束地重复使用同一份一阶反射能量。

现有接口语义不同：

- D120 六路径 gain 是 source→listener 的确定性候选能量；
- `VoxelReflectionProbe.firstHitReflectedEnergySum / rayCount` 是 listener-centred
  球面采样的一阶环境平均；
- `FdnEnvironmentMapper` 以该环境平均、openness 和固定 `0.4` scale 生成 wet gain。

D121a 不把两者直接相加，而是先都归一化为每频带平均能量预算：

```text
explicit_candidate_b = sum(six first-order path gains_b) / 6
environment_budget_b = clamp(listener first-hit mean energy_b, 0, 1)
explicit_early_b = min(explicit_candidate_b, environment_budget_b)
explicit_rejected_b = max(0, explicit_candidate_b - environment_budget_b)
late_residual_b = max(0, environment_budget_b - explicit_early_b)
```

逐频带严格满足：

```text
explicit_early_b + late_residual_b = environment_budget_b
```

late wet gain 只从 residual 生成：

```text
wet = min(0.45, sqrt(mean(late_residual)) × 0.4 × (1 - openness))
```

这是保守的能量所有权账本，不是经听感标定的混音。六面均匀平均与 listener sphere
平均仍不是相同 quadrature；D121 scene fixture 必须报告被拒绝的显式能量，不能静默裁剪。

## 实现

`EarlyLateEnergyLedger` 使用预分配三频带 primitive arrays。每次 partition：

- 不创建 `AcousticBands` 或 result record；
- 对非有限 environment energy 拒绝；
- 对 candidate 做非负保护；
- 对 environment budget 限于 `[0,1]`；
- 输出 candidate、allocated、rejected、late residual、budget 与单一 late wet gain。

三种确定性场景覆盖：

1. closed/充足环境预算；
2. tight budget，显式候选被部分拒绝；
3. openness `1` + zero budget，early/late 都为零。

独立 Python verifier 重算所有 partition、逐频带守恒与 wet gain。五个各 100,000 次的
allocation windows 都是 `0 bytes`；消费输出以阻止 JIT 消除后的本机 P99 为
`39.0625 ns/partition`，研究门为 `10 µs`。

## 与 Minecraft 现有代码的连接边界

审计确认已有：

- `MinecraftAcousticMaterials`：BlockState/tag/SoundType/fluid/collision fill →
  8 类集中材质表，并有 mapping version 与 registry fingerprint；
- `MinecraftDirectPathSnapshot`：真实 probes→listener DDA corridor 与 portable
  production bundle；
- `MinecraftReflectionSnapshot`：listener-centred 完整材料体积；
- `VoxelReflectionProbe` / `LateReverbEstimator` / `FdnEnvironmentMapper`：
  listener-shared late environment；
- `DroneAcousticRenderState`：20 Hz source update、三频带 transmission 平滑、
  source/listener/Doppler snapshot。

仍缺少、因此 D121 尚未完成：

- 从真实 `VoxelShape` 命中面提取连续 local plane，而不是用一米 block 边界作为精确
  early delay；
- source→facet→listener 的反射可见性快照；
- D120 cluster 到实际 early-tap renderer 的方向与延迟数据；
- snapshot generation/cache invalidation 的移动 source 测试；
- ledger allocated/rejected/residual 与现有 FDN/EFX 控制的离线 scene fixture；
- worker handoff、每 tick 多 source budget 与平滑测试。

本轮没有把 ledger 接入 production audio graph，故：

```text
minecraft_integration_enabled=false
physical_endpoint_opened=false
captures_audio=false
cuda_executed=false
release_calibrated=false
```

## 证据

| artifact | SHA-256 |
|---|---|
| frozen contract | `d11f331d40fb378463b11a2292928c2c53882820f8bda5fe41c8ccbb0a9cf633` |
| Java reference report | `cbe1cc1b010912278c476bf73d0c426c183095e2e2d0c1788482cfe9a67b2f13` |
| independent verification | `6a032001af9564a6c42eda5cbcbb2358fac9ef06d32861c9e4332178c468a17f` |

