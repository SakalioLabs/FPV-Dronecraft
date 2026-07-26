# D121j — 实时 early reflection 分数延迟算法选择

## 决策

在当前 48 kHz、最多 12 个早期反射 tap、延迟随无人机和监听者运动而连续变化的约束下，
选择 **三阶 Lagrange FIR** 作为下一版 null renderer 的分数延迟算法。

这是一项离线工程选择，不是听感校准结论。当前实现仍未接入 Minecraft 声音路径，也没有
打开音频端点或采集声音。

## 文献依据

Laakso、Välimäki、Karjalainen 与 Laine 的综述把 FIR/Lagrange 和 all-pass
列为分数延迟的主要实现族，并系统讨论了幅频、相位和实现成本之间的取舍：

- [Splitting the Unit Delay—Tools for Fractional Delay Filter Design](https://doi.org/10.1109/79.482137)
- [Principles of Fractional Delay Filters](https://doi.org/10.1109/ICASSP.2000.860248)

Thiran all-pass 对固定系数具有全通幅频优势，但本项目的 delay 每个控制帧都可能变化。
Välimäki、Laakso 与 Mackenzie 专门指出，时变递归 all-pass 在修改系数时会产生瞬态，
需要同步修正滤波器状态：

- [Elimination of Transients in Time-Varying Allpass Fractional Delay Filters](https://hdl.handle.net/2027/spo.bbp2372.1995.096)

因此，不能只凭固定 delay 的全通幅频就把 Thiran 放进 12 个会创建、移动、交叉淡化和
销毁的反射 slot；每个 slot 还需要可审计的递归状态生命周期。

## 冻结比较

比较对象为 linear、三阶 Lagrange、一级 Thiran 和二级 Thiran。固定分数延迟取
`0.1/0.25/0.5/0.75/0.9 sample`；动态测试使用 48 kHz FPV-like 多谐波加确定性噪声，
delay 在 `100.05–100.95 samples` 间往返并包含一次方向反转/跳变。参考为归一化
32-tap Lanczos-windowed sinc。所有 23,000 个评价样本均由独立 verifier 重算。

| 算法 | 0–12 kHz 最大幅度误差 | 0–12 kHz 最大相位误差 | 动态 RMSE | 最大动态误差 |
|---|---:|---:|---:|---:|
| linear | 3.0103 dB | 4.0651° | 0.059933 | 0.288995 |
| Lagrange 3 | 1.0721 dB | 1.6022° | 0.045294 | 0.201671 |
| Thiran 1 | ≈0 dB | 8.1301° | 0.078013 | 0.328299 |
| Thiran 2 | ≈0 dB | 2.4990° | 0.064667 | 0.276816 |

三阶 Lagrange 相对 linear 将该动态夹具的 RMSE 降低约 **24.4%**，同时明显降低
12 kHz 内的幅度和相位误差。两种 Thiran 虽保持固定 delay 的幅度，但在未做递归状态
修正的时变路径上均比 linear 更差，因此不进入运行时。

## 实际 Java 热路径

同一个 `NullEarlyReflectionRenderer` 对 12 个活动 slot、256-sample block、
5,000 个测量 block 执行 linear 与 cubic-Lagrange 两种模式。报告保存全部 timing
样本，独立 verifier 重算分位数和分配窗口：

| 模式 | P99 / output frame | steady-state allocation |
|---|---:|---:|
| linear | 241.406 ns | 5/5 windows = 0 bytes |
| Lagrange 3 | 348.438 ns | 5/5 windows = 0 bytes |

Lagrange 通过冻结的 `P99 <= 500 ns/output frame` 研究门槛。该结果是本机离线微基准，
不包含 Minecraft tick、OpenAL、混音器或操作系统调度。

## 高频限制

三阶 Lagrange 在 0–20 kHz 屏幕上的最大幅度误差仍为 **8.4144 dB**，因此不能声称
全音频带透明，也不能据此完成穿越机高频旋翼声的发布校准。当前选择只说明它在已有四个
候选中满足 12 kHz、时变稳定性、12-slot 成本和零分配的联合约束。

在接入实际声音链之前，D121k 应单独研究高频缓解方案：

1. 仅对 high band 使用更高阶 Lagrange/Farrow；
2. 2× oversampling 后执行分数延迟，再安全降采样；
3. 以受限短 sinc/polyphase 表作为离线质量上界；
4. 比较运动 sideband、20 kHz 误差、12-slot P99、内存和零分配；
5. 保持 low/mid band 与 D121a 能量账本不变。

不得静默修改 D121i 的历史 linear 基线；三阶 Lagrange 应通过新版本合同显式晋升。

## 可重复检查

- `analyzeMinecraftFractionalDelaySelection`：重算四种算法的固定/时变指标；
- `fractionalDelayRendererBenchmark`：运行实际 Java 12-slot 热路径；
- `verifyMinecraftFractionalDelaySelection`：独立重算指标、分位数、hash 和负向边界；
- `acousticResearchCheck`：在完整研究数据可用时包含上述检查。

本轮核心 Java 回归为 `226/226`；相关 Python verifier 回归通过。完整 Gradle/Fabric
Loom gate 没有执行，因此不作该项声明。

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
| D121j contract | `cad1603de08b8cb6d43cd8a10a25259d9e02d2a69dd3c72587013962881ea2c5` |
| offline algorithm report | `26bc04ec47878b59387dd642dce88d1eea2ea48a56c518e267f22c2162280111` |
| Java renderer benchmark | `8a55894688753a1224bed22f1a48b82b8faa4c28875665d11d6f997df2604b6c` |
| independent verification | `8897c864a6393338ca6315a9ec1b1d6ae89e9cb5ed9d5c5f4f8dfeb1413141bc` |
