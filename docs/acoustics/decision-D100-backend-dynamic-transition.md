# D100 — EFX / Java FDN 动态环境边界

日期：2026-07-26  
状态：真实 Client GameTest、连续软件渲染、独立校验通过

## 决策

在当前 Minecraft 捆绑的 OpenAL Soft 软件 renderer 上，保留生产 EFX 的直接参数
更新，不引入双 effect-slot crossfade。原因是本次 closed→partial→open→closed
连续输入中，三个精确更新边界都没有解析到超出局部波形差分分布的 click，5 ms
能量比也保持有界。

Java FDN 继续使用生产 0.2 秒指数参数平滑，并保持同一个 FDN 实例与已有尾声，
不在环境变化时 reset。

该决策只覆盖当前软件 renderer 与本次参数范围。它不是物理端点连续性或听觉阈值；
D085–D089 的授权录音仍是发布前门禁。

## 协议

输入为同一次 D094 trace 的 4 个 motor 和 4 个 propeller 生产 chunk，各 chunk
1 秒。不是把第一秒循环四次；8 个 chunk 的 sequence、单段 SHA-256、拼接后每层
SHA-256 和完整 D094 report/PCM sidecar 都被独立校验。

在一个隔离 `ALC_SOFT_loopback` context 中连续渲染 192,000 frames：

| segment | frames | 环境 | 更新语义 |
|---|---:|---|---|
| 0 | 0–47,999 | closed | 初始配置 |
| 1 | 48,000–95,999 | partial | 精确边界更新 |
| 2 | 96,000–143,999 | open | 精确边界更新 |
| 3 | 144,000–191,999 | closed | 精确边界更新 |

EFX 在每个边界调用生产 `configureReverbEffect`、重新绑定 shared slot，并更新两个
send filters。Java FDN 在同一实例上调用 `configure(..., 0.2)`；内部 delay lines
与尾声不清空。dry、EFX、Java 三条输出都通过同一种 OpenAL mixer，便于用
`backend - dry` 残差隔离湿声变化。

## 边界结果

残差单 sample step 与边界附近、排除 ±2 ms 后的差分 P99：

| 边界 | EFX step / P99 | Java step / P99 |
|---:|---:|---:|
| 48,000 | 19 / 38 | 31 / 71 |
| 96,000 | 9 / 10 | 12 / 24 |
| 144,000 | 2 / 7 | 4 / 30 |

所有 step 都小于局部 P99，没有形成可解析的异常脉冲。

边界前后 5 ms 残差 RMS 比：

| 边界 | EFX after/before | Java after/before |
|---:|---:|---:|
| 48,000 | 0.9622 | 0.7399 |
| 96,000 | 0.9073 | 0.7563 |
| 144,000 | 1.0302 | 0.9661 |

全部位于预注册的 `[0.5, 2.0]` 诊断范围。这个范围是防止极端短窗能量跳变的工程
门禁，不是人耳 click 阈值。

去掉每段前 100 ms 后的湿声残差 RMS：

| 后端 | closed | partial | open | closed again |
|---|---:|---:|---:|---:|
| EFX | 82.2053 | 32.2091 | 20.5916 | 53.2918 |
| Java FDN | 119.9679 | 52.6417 | 51.5837 | 101.6177 |

两条后端都随开门减弱、关门增强。closing segment 没有要求一秒内返回初始 closed
值，因为 Java 明确平滑参数，且两种混响都保留历史尾声。

三条完整输出相对连续输入的 lag 都为 48 samples；correlation 为 dry
`0.999998451`、EFX `0.983455326`、Java `0.957339720`。

## 证据

- report SHA-256：
  `c75a26c790260e9c21dcbc1610e1a32bafe05db720c6faf3797033cdb129e66d`
- dry PCM SHA-256：
  `b6757d9bf99cdf0b236bc5f087ac1914424c12f3ca1778b4bbf3dd0cab840f70`
- EFX PCM SHA-256：
  `ba9e7c5756d7a1d2fa430d70f25c98fed2fe9bc9f602a7e1c5fc4858da93d9c8`
- Java FDN PCM SHA-256：
  `12142485187338fd3ef9e9e2d122b090ecefb32eebc16b51df800a80ea7900b8`
- independent verification SHA-256：
  `ae7b43278f9f02ea861f58288235aec8547fce567ad899bd82fabe891ddc7fee`

7 个负例覆盖输入 sequence 脱链、portal controls 脱链、PCM 篡改、人工注入
5,000-LSB boundary click、后端不响应环境和 capture overclaim。

## 边界与下一步

`efx_no_resolved_software_boundary_click=true` 只表示本次 OpenAL Soft readback 中没有
解析到异常；不等于扬声器、声卡、endpoint loopback 或人耳都无 click。没有录音、
没有 ABX、没有发布校准。

D101 应把 D098–D100 的结论固化为后端选择与遥测合同：

1. EFX 可用时优先 native shared effect，Java FDN 为 fallback；
2. 记录实际 backend、环境 generation、RT60/wet 和 context rebuild，不记录音频；
3. 对 unsupported/error/context-loss 做 fail-safe fallback，不允许双混响；
4. 用现有 D084 性能证据补齐 EFX/Java 真实 workload 的相对 CPU 预算；
5. 最终听感参数仍由授权物理录音和 ABX 决定。
