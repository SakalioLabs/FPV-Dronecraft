# D121h — Early reflection cluster 平滑 DTO

## 结论

D121h 补齐了 worker→renderer 之间缺失的数据和拓扑过渡语义，但仍未实现音频渲染。

`LocalPlaneSceneWorkerHandoff.Result` 现在完整携带每个 cluster 的：

```text
arrival samples
low/mid/high energy
listener-to-reflection unit direction
path count
```

此前 worker 只复制 arrival 和 direction，丢失三频带能量与 path count，任何 early tap
renderer 都无法据此构造正确增益。新的后台线程测试直接求解一个连续地面反射，并确认
全部字段跨 handoff 后保持有效。

## 匹配与交叉淡化

`EarlyReflectionClusterSlew` 固定支持最多 6 个输入 clusters、12 个临时 render slots：

- arrival 差 `≤96 samples` 的最近未占用 slot 视为同一路径簇；
- 同簇在原 slot 内平滑 delay、三带 amplitude 和 direction；
- 无匹配新簇从零增益淡入；
- 无匹配旧簇保持 delay、淡出到零；
- 因而一次完整拓扑替换最多需要 6 个旧 slot + 6 个新 slot；
- 零增益 slot 在下一 control frame 退休；
- incomplete worker result 等价于零 clusters，使全部 early slots 保守淡出。

输入 band 值是能量，输出线性幅度严格为：

```text
amplitude = sqrt(clamp(energy, 0, 1))
```

方向重新归一化；退化向量使用 `+Z`，防止把 NaN 送往未来 spatializer。

## Ramp 契约

当前控制 DTO 冻结 `2400 samples @ 48 kHz = 50 ms` 的 start→target ramp。它与 20 Hz
client control tick 对齐，使 renderer 将一个 control frame 连续插值到下一个。拓扑
替换使用双 slot crossfade，不把远距离 delay 突变解释成一次长时间 pitch glide。

50 ms 是研究参数，不是听感校准结果。实际 delay-line 插值、分数延迟、三频带滤波和
空间化仍需离线 PCM/null-backend 连续性测试后才能调整。

## 验证

确定性夹具覆盖：

- 新 cluster 从零幅度淡入，`0.36 energy → 0.6 amplitude`；
- arrival `120→150 samples` 保持同一 slot；
- arrival `150→400 samples` 触发两个 slot 的同时淡入/淡出；
- incomplete frame 先淡出、下一 frame 退休；
- 非单位方向重新归一化。

100,000 次六簇 update 的本机 P50/P99 为 `0.1/0.2 µs`，远低于冻结的 `10 µs`
研究门。五个各 250,000 次的 steady-state allocation windows 全为 `0 bytes`。
独立 Python verifier 重算能量→幅度、50 ms ramp、fixture identity、100,000 个原始
timing 样本的 P50/P99 和五个 allocation gates。

当前核心、worker DTO、scheduler/source-contract 回归为 73 项，全部通过。

## 边界与下一步

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

D121i 应实现纯内存/null-backend 的 fractional-delay early tap renderer：

1. 12 slots、三频带 gain 与 delay ramp；
2. topology crossfade 不产生 sample discontinuity、NaN、denormal 或超预算峰值；
3. explicit early energy 必须继续受 D121a ledger 限制，不能与 late EFX/FDN 重复；
4. 用 impulse、sine sweep、moving-delay 和 hard topology switch 生成离线 PCM；
5. 验证峰值、能量、频谱、click 指标和零分配；
6. 通过后才能考虑把 DTO 接到实际声音路径，且仍需显式音频端点授权。

## 证据哈希

| artifact | SHA-256 |
|---|---|
| D121h contract | `55d01bbfcca18e379cae7e4b910510fbfcaf21e752d2f710e2d4924b2f3b8925` |
| Java slew report | `2e2b9bb4085d38d11be7a1643e4c1eab9a8d9292a587b8d5c7a81740ab388cd8` |
| independent verification | `e98cc9b8c35b0904cef79fe3e74613e912b4a79ffb2b21a2b8ab93fd1b83036c` |
