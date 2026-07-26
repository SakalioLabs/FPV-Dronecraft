# D106 — Java FDN 历史输入 pre-roll 长度与预算

日期：2026-07-26  
状态：三环境生产输入 sweep、P99 基准、独立 verifier 通过

## 决策

选择 `500 ms` 作为下一阶段 Java FDN fallback 历史输入目标。它是 closed、partial、
open 三种 Minecraft 控制同时通过以下门限的最短候选：

- 相对 `1000 ms` 有界参考的相关性 `>= 0.90`；
- wet RMS 比位于 `[0.90, 1.10]`；
- normalized RMSE `<= 0.50`；
- 新建 FDN、pre-roll 和 250 ms evaluation 的 P99
  `<= 10.667 ms`，即 2048-sample/42.667 ms buffer 的 25%。

本轮不直接把 silent OpenAL shadow stream 接入生产。Minecraft 1.21.11 streaming
source 会预排多个一秒 buffer；若 shadow stream 已排入静音，简单切换状态可能让
fallback 湿声延后数秒。并且当前 `ListenerReverbAudioStream` 的 synthesizer phase
属于 stream 实例，停止 shadow、创建 active stream 会重置相位。D107 必须先解决
“历史生产者、相位所有权、OpenAL queue 重启”三者的生命周期，再实现生产 pre-roll。

## 实验协议

实验固定使用 D100/D094 的同一段连续 motor/propeller 生产 PCM：

- history 结束位置：frame `96,000`；
- evaluation：后续 `12,000 frames / 250 ms`；
- 环境：closed、partial、open；
- history：`0 / 50 / 100 / 250 / 500 / 1000 ms`；
- 每个候选都新建生产 `ListenerSharedFdn`，使用当前环境控制立即配置；
- `1000 ms` 候选同时作为该环境的有界参考；
- 每 case 10 次 warmup、50 次计时。

将当前环境参数应用到完整历史是故意的：fallback 时有当前 snapshot，但不能假设已
保存过去每帧的环境轨迹。本轮只选择有界混合输入历史长度，不宣称重建无限 warm tail。

## 结果

### Closed

| pre-roll | correlation | RMS ratio | NRMSE | P99 | pass |
|---:|---:|---:|---:|---:|---|
| 0 ms | 0.6024 | 0.5887 | 0.7983 | 2.9264 ms | no |
| 50 ms | 0.6619 | 0.6394 | 0.7500 | 0.7854 ms | no |
| 100 ms | 0.7218 | 0.7248 | 0.6921 | 1.1090 ms | no |
| 250 ms | 0.8384 | 0.8881 | 0.5473 | 1.2833 ms | no |
| 500 ms | 0.9335 | 1.0086 | 0.3664 | 1.9868 ms | yes |
| 1000 ms | 1.0000 | 1.0000 | 0.0000 | 3.0409 ms | yes |

Closed 是限制环境。250 ms 同时未达到 correlation、RMS 与 NRMSE 门限。

### Partial / open

- partial 从 `250 ms` 起通过；
- open 从 `250 ms` 起通过；
- `500 ms` 在三环境的最小相关性为 `0.93346`；
- `500 ms` RMS 比范围为 `[1.00857, 1.02584]`；
- `500 ms` 最大 NRMSE 为 `0.36647`；
- 最新独立验证绑定的 selected-case 最大 P99 为 `2.05461 ms`，
  即 42.667 ms buffer 的 `4.8155%`。

P99 数字会随主机调度变化，因此报告保存每轮实测，选择器始终重新检查门限；PCM
sidecar 和质量指标是确定性的。

## 可复现证据

```powershell
.\gradlew.bat verifyFdnHistoryPrerollSweep --rerun-tasks
.\gradlew.bat acousticResearchCheck --rerun-tasks
```

- D106 report SHA-256：  
  `b2107cef3199c3fd7fc5934d331e6542d305933eeadcc8d42b969d065f9dc99d`
- 504,000-byte sidecar SHA-256：  
  `d515de94447f2e05454dcd0d677ca518344da463f2e9bd4af4605c7a66365721`
- independent verification SHA-256：  
  `5ff786373277f06acef5adf704435435fedce8de400c4d25eb019fee09169b5e`
- bound D100 report SHA-256：  
  `c75a26c790260e9c21dcbc1610e1a32bafe05db720c6faf3797033cdb129e66d`
- bound D094 trace report SHA-256：  
  `9eda3288a42a26ee74feb1b9a594e88c9a978e2415ee0de466ed0af5d932eaea`

独立 verifier 重建八个 D094 input chunk，重新检查三环境控制、21 个 sidecar range、
相关性/RMS/NRMSE、计时分布、pass flag、1000 ms reference identity，以及
500 ms 是否确为三环境共同通过的最短候选。测试包含 1 个正例与 13 个负例。

本轮统一回归为 `340 Python tests passed`；Java/core/mod 测试、CPU DDA reference
的 2/2 CTest 与静态 CUDA source contract 同时通过。当前主机没有 `nvcc`，因此没有
CUDA 编译、GPU 执行或 GPU 性能声明。

## Claim boundary 与下一步

D106 是单 listener、纯 Java FDN 基准。它不包含多无人机 source synthesis 成本、
OpenAL scheduling、Minecraft frame time、声卡或录音；1000 ms 是有界参考，不是
无限稳态或听觉真值。`500 ms` 是下一阶段实现目标，不是已经上线的生产行为。

D107 应先构造不产生双湿声的 history producer：

1. 历史 ring 容量固定为 `24,000 samples / 500 ms`；
2. shadow 阶段只生成并保存 mixed input，输出必须为全零，不运行 FDN；
3. fallback 时新 active stream 读取一次原子 history snapshot，pre-roll 新 FDN；
4. 不能继承 OpenAL 已排队的一秒静音 buffer；必须显式重启 active source；
5. synthesizer phase 必须由 listener-shared mixer 持有，不能随 stream replacement
   重置；
6. 记录 shadow synthesis P99、fallback pre-roll P99、queue-to-first-wet latency；
7. telemetry 区分 `java_shadow_active` 与 `java_wet_active`，只有后者参与
   double-wet contract；
8. 通过真实 Client GameTest 的 EFX fault→Java fallback→reload recovery 后，才允许
   把 pre-roll 标记为生产路径。
