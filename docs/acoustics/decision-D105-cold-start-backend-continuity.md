# D105 — 后端 cold-start 连续性与尾声损失

日期：2026-07-26  
状态：真实生产 PCM、生产 EFX/FDN 生命周期、隔离 OpenAL Soft 渲染与独立 verifier 通过

## 决策

D104 的 warm-state 独占切换不能代表真实故障后的新后端。D105 在每个所有权边界才
创建目标后端：

- EFX→Java fallback：删除 EFX send/filter/slot/effect，随后创建一个 delay lines
  全为空的新 `ListenerSharedFdn`；
- Java→EFX recovery：丢弃 Java FDN，在该边界创建新的 EFX effect、slot 和两个
  source filter；
- dry motor/propeller source 在四秒渲染中从不停止；
- 不预热第二后端、不 crossfade、任一时刻只有一个湿声所有者。

结果将两个结论严格分开：

1. **没有解析到总输出 click/dropout。** dry 始终连续，六个边界的最大
   step/local-P99 比为 `0.7468`，最小 20 ms output/dry RMS 比为 `0.9949`。
2. **cold-start 与 warm-state 尾声不等价。** 切换后 100 ms 的最低
   cold/warm wet RMS 比只有 `0.1938`；Java fallback 和 EFX recovery 两个方向都
   未通过 `>= 0.5` 的工程等价门限。

因此当前不增加额外 fade-in：FDN 已由空 delay lines 自然渐入，EFX recovery 也没有
形成异常 sample step，额外淡入只会进一步减少湿声且不能恢复丢失尾声。下一步必须
研究 Java 历史输入 pre-roll；EFX recovery 另行制定“接受新 context 尾声重置”或受控
恢复策略，不能用 Java pre-roll 结果替它背书。

## 实验实现

`BackendColdStartContinuityProbe` 作为独立 JavaExec 使用 Fabric 客户端运行时类路径，
打开 `ALC_SOFT_loopback` 和 thread-local context，不接触 Minecraft 当前 context。
它复用：

- D100 的四段连续 motor/propeller 输入 manifest 与三个环境控制；
- D094 的原始 PCM sidecar；
- D102 的 resource-create、parameter-write、source-route 三次真实故障顺序；
- 生产 `OpenAlEfxController.configureReverbEffect` /
  `configureSendFilter`；
- 生产 `ListenerSharedFdn` delay lines 与反馈参数。

Java wet PCM 只在三个 fallback ownership window 非零。每个 window 都使用新实例，
第一条 delay line 长 `1,423 samples`，因此第一份非零湿声固定在 fallback 后
`29.6458 ms`；该值由 PCM 与 `diagnosticDelaySamples()` 双重绑定。

## 结果

| frame | direction | step/P99 | 20 ms output/dry | 100 ms cold/warm wet |
|---:|---|---:|---:|---:|
| 12,000 | EFX→Java | 0.2735 | 0.9965 | 0.2166 |
| 24,000 | Java→EFX | 0.5097 | 1.0091 | 0.1938 |
| 60,000 | EFX→Java | 0.3662 | 1.0074 | 0.3596 |
| 72,000 | Java→EFX | 0.2571 | 0.9949 | 0.3620 |
| 108,000 | EFX→Java | 0.7468 | 0.9999 | 0.4432 |
| 120,000 | Java→EFX | 0.1726 | 1.0041 | 0.5087 |

全部 step/P99 低于 2.0；边界后 20 ms 总输出均不低于 dry 的 99.49%，最长连续零
输出为 1 sample。Java wet onset 为 `1,423 samples / 29.6458 ms`，低于本轮注册的
50 ms 上限。

另一方面，三个 Java fallback 的 cold/warm wet 比分别为
`0.2166 / 0.3596 / 0.4432`，三个 EFX recovery 为
`0.1938 / 0.3620 / 0.5087`。因此：

```text
no_resolved_software_boundary_click = true
no_total_output_dropout_resolved = true
java_fallback_tail_equivalent = false
efx_recovery_tail_equivalent = false
java_history_preroll_research_required = true
efx_recovery_tail_policy_required = true
```

“尾声不等价”是波形域工程诊断，不等于已证明人耳可辨。

## 可复现证据

```powershell
.\gradlew.bat verifyBackendColdStartContinuity --rerun-tasks
.\gradlew.bat acousticResearchCheck --rerun-tasks
```

- D105 report SHA-256：  
  `ac4b73bd03cdc675070e11b7c4cac94acd6ed389ac2660f70674185899222153`
- cold-start PCM SHA-256：  
  `cbef0477c06be8ca0096e4708c7475cca029b72b37c37344191b5792b876b136`
- dry PCM SHA-256（与 D100 bit-identical）：  
  `b6757d9bf99cdf0b236bc5f087ac1914424c12f3ca1778b4bbf3dd0cab840f70`
- Java cold-start wet PCM SHA-256：  
  `1e49c3030c80f0db2d66eda48f1ad10d5b6a1b62b07f5d8385795fd9b7c674ce`
- independent verification SHA-256：  
  `2dcf82cb80cdb6aed417d3db3c1efd83b7f9053d11d9e8116e5f965bce32257c`
- bound D100 report SHA-256：  
  `c75a26c790260e9c21dcbc1610e1a32bafe05db720c6faf3797033cdb129e66d`
- bound D102 report SHA-256：  
  `d3c6a6d60467b1e2977e3c4e650040cb56aa181072577a05dc4b20b78f30ff99`
- bound D104 warm-state report SHA-256：  
  `6cd35f20dfe95769506c74ef61dd8619983ed084fcbebdeea686d10311c20f88`

独立 verifier 重建 D094 四段输入、检查 D100 环境控制、D102 exclusive ownership、
D104 warm-state 对照、Java wet ownership window、delay-line onset、OpenAL error、
PCM 哈希和无端点/无录音声明。测试包含 1 个正例与 11 个负例。

本轮统一回归为 `326 Python tests passed`；Java/core/mod 测试、CPU DDA reference
的 2/2 CTest 与静态 CUDA source contract 同时通过。当前主机没有 `nvcc`，因此没有
CUDA 编译、GPU 执行或 GPU 性能声明。

## Claim boundary 与下一步

D105 使用真实 OpenAL Soft loopback 和生产 DSP 代码，但不是 Minecraft main-context
输出、声卡 endpoint、系统 loopback 或麦克风录音。50 ms onset 与 0.5 tail ratio 是
工程研究门限，不是听觉阈值或发布校准。

D106 应对 Java fallback 做受限历史输入 pre-roll sweep：

1. 对 `0 / 50 / 100 / 250 / 500 / 1000 ms` 历史混合输入分别新建 FDN 并预推进；
2. 比较 fallback 后 100/250 ms 与 D104 warm-state 的 wet RMS、相关性和边界 step；
3. 同时测量一次性 pre-roll wall time，禁止在 sound thread 上产生不可控阻塞；
4. 若短历史不足而长历史超预算，再比较“后台 shadow state、输出保持静音”方案；
5. EFX recovery 单独评估短 fade-in 与直接重置；fade-in 只能降低 onset，不可宣称
   恢复旧 context 尾声；
6. 任何生产修改仍必须保持 `doubleWetPath=false`。
