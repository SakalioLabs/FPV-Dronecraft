# D121k — 高频分数延迟筛查与运行时门失败

## 结论

D121k 找到一个明显优于三阶 Lagrange 的高频候选：

```text
low/mid band: third-order Lagrange
high band: 8-tap β=2 Kaiser-windowed sinc
fraction control: 1024 phase intervals / 1025 immutable rows
table size: 65,600 bytes shared
delay < 4 samples: fall back to third-order Lagrange
```

但它**没有晋升为生产默认值**。冻结的 `P99 <= 750 ns/output frame` 绝对运行时门在
三轮最坏值检查中失败，独立 verifier 的状态为：

```text
verified-minecraft-high-band-fractional-delay-runtime-gate-failed
```

实现保留为纯内存、显式 opt-in 研究模式
`HIGH_BAND_KAISER_SINC8`；默认构造器继续使用历史 linear，D121j 的 Lagrange 也未被
静默改成默认值。

## 研究依据

宽带可变分数延迟的困难不是只靠提高 Lagrange 阶数就能解决。多速率/Farrow 文献指出，
带宽接近全带时复杂度快速增长，并提出半带预滤波加短 Lagrange 的两阶段方案：

- [Adjustable Fractional-Delay FIR Filters Using the Farrow Structure and Multirate Techniques](https://doi.org/10.1109/APCCAS.2006.342270)

后续工作使用 sinc 辅助改善 Lagrange/Farrow 的 midpoint 与宽带误差：

- [Farrow Structured Variable Fractional Delay Lagrange Filters with Improved Midpoint Response](https://doi.org/10.1109/TSP.2017.8076038)

近年的宽带 Farrow 研究仍把乘加数量和稀疏度作为显式优化目标，而不是只比较频响：

- [Sparsity-Optimised Farrow Structure Variable Fractional Delay Filter for Wideband Array](https://doi.org/10.1049/sil2.12228)

## 离线结果

固定分数延迟为 `0.1/0.25/0.5/0.75/0.9 sample`。动态夹具继续使用 D121j 的
23,000 个确定性 FPV-like 样本，并以 32-tap Lanczos-windowed sinc 为参考。

| 算法 | 20 kHz 最大幅度误差 | 20 kHz 最大相位误差 | 动态 RMSE |
|---|---:|---:|---:|
| Lagrange 3 | 8.4144 dB | 19.5631° | 0.045294 |
| Lagrange 5 | 6.6687 dB | 16.3363° | 0.038766 |
| Lagrange 7 | 5.5187 dB | 13.9336° | 0.034690 |
| Kaiser sinc8 | 0.8881 dB | 4.0346° | 0.020055 |
| Kaiser sinc8, 1024 phases | 0.8881 dB | 4.0346° | 0.020056 |
| Lanczos sinc12 bound | 0.8945 dB | 2.9209° | 0.017606 |
| ideal 2× + Lagrange 3 bound | 0.5341 dB | 0.6640° | 未执行 |

1024 相位量化相对连续 Kaiser 系数只增加约 `0.00000104` 的动态 RMSE；相对 D121j
Lagrange 3，动态 RMSE 下降约 **55.7%**。

`ideal 2×` 只计算理想升/降采样下的静态上界，未包含半带滤波器、抗混叠、延迟、状态和
实际成本，因此明确标记 `runtime_eligible=false`，不能凭 `0.5341 dB` 结果入选。

## 实际 Java 运行时

实际 renderer 使用 12 个活动 slot、256-sample block、每种模式 3 个完整 trial；
报告保存每个 trial 的 P99、所有最坏 trial 原始 timing 样本和全部 allocation windows。

| 模式 | 三轮 P99（ns/frame） | 最坏 P99 | 分配 |
|---|---|---:|---:|
| Lagrange 3 | 539.45 / 710.55 / 874.61 | 874.61 | 15/15 windows = 0 |
| hybrid sinc8 | 859.77 / 908.98 / 978.52 | 978.52 | 15/15 windows = 0 |

候选 P50 为 `518.75 ns/frame`，基线为 `491.41 ns/frame`，说明典型增量不大；但冻结
合同要求的是绝对 P99，不允许用 P50 或一次较快运行替代。此前单轮曾得到约 408 ns，
也曾观测到超过 800 ns 的值，正是因此改成三轮最坏值并保留失败。

该绝对门同时被本轮 Lagrange 基线的最坏 trial 超过，表明操作系统/JVM 调度抖动对
256-sample 微基准影响显著；这不是放宽 D121k 冻结门或挑选最快结果的理由。

## 下一步

D121l 应冻结一个新的、配对且可解释的运行时合同：

1. 在同一 trial/block 中交错运行 Lagrange 与 hybrid，报告 paired delta；
2. 同时保留绝对 P50/P95/P99、CPU 时间和 wall time，不删除 D121k 失败；
3. 加入 1/4/6 source 的完整 renderer CPU 占比预算，而不只测单 source；
4. 比较 8-tap 直接展开、对称/稀疏 Farrow 和 SIMD-friendly 布局；
5. 只有质量门、配对增量门和多 source 总预算同时通过，才建立新 renderer contract；
6. 随后才允许进入无物理音频端点的 Minecraft packet/GameTest replay。

## 验证边界

核心 Java 回归为 `227/227`；相关 Python 回归通过。没有启动 Minecraft client、
读取真实 `ClientLevel`、打开声音端点、采集音频或执行 CUDA。

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
| D121k contract | `c697a0229d87bd1097fbe2386adc9dc9b6b33146a38c70e7686366c47b890f12` |
| offline candidate report | `f9f2c534dc8cfe7bff28ce158becc51db6c3c980bc97c2b6d90ba438bb8ad858` |
| three-trial Java benchmark | `1bfc4adc1062602e7c57b2696da9b14703e8408740683630b3cd35d545d20fe0` |
| independent verification | `292cdd73900b5feb9016caa85c6707c5ef6fe364118d2a0d97c868110554c114` |
