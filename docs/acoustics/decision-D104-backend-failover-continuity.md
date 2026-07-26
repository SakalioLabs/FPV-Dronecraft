# D104 — EFX / Java FDN 独占所有权切换连续性

日期：2026-07-26  
状态：真实 D100 软件渲染、真实 D102 故障序列、派生 PCM 与独立 verifier 通过

## 决策

当前 EFX-first / Java-fallback 合同继续采用“任一时刻只有一个湿声所有者”，不引入
EFX 与 Java FDN 的 crossfade。D104 在不打开物理端点、不录音的前提下验证了这种
独占切换的 warm-state 软件边界：

- 三个 D102 fault cycle 各映射一次 `OPENAL_EFX → JAVA_FDN` fallback 和一次
  `JAVA_FDN → OPENAL_EFX` recovery；
- 六个切换点都位于 D100 同一环境稳态段内部，避免把开门参数变化误算为后端切换；
- 输出逐 sample 只复制 EFX 或 Java FDN 中的一条完整 render，不相加、不 crossfade；
- dry、EFX、Java FDN 均来自相同的四段连续生产 PCM 和相同 OpenAL Soft renderer。

因此，本轮没有用双尾声掩盖 click 或 dropout。与此同时，D100 的两个后端是从渲染
开始就持续运行的 warm-state source；这不等价于故障后新建 Java FDN 或 reload 后
新建 EFX 的 cold-start DSP 状态。报告固定写入
`warm_state_source_renders=true` 与 `cold_start_measured=false`。

## 协议

输入为 D100 的 192,000-frame dry / EFX / Java FDN PCM，以及 D102 的三类故障：

| D102 stage | environment | fallback frame | recovery frame |
|---|---|---:|---:|
| resource-create | closed | 12,000 | 24,000 |
| parameter-write | partial | 60,000 | 72,000 |
| source-route | open | 108,000 | 120,000 |

每对边界都位于对应 48,000-frame 环境段的 0.25 秒与 0.5 秒位置。输出所有权为：

```text
EFX → Java → EFX → Java → EFX → Java → EFX
```

校验门限只用于确定性软件诊断：

1. boundary 单 sample step 不超过两倍局部差分 P99；
2. 边界前后 5 ms 湿声 RMS 比位于 `[0.25, 4.0]`；
3. 边界后 20 ms 总输出不低于同窗 dry RMS 的 50%；
4. 边界后最长连续零样本不超过 48 samples（1 ms）；
5. 合成 PCM 必须逐 sample 等于当前所有者，拒绝相加或 crossfade。

这些门限不是听觉 click 阈值，也不是发布校准值。

## 结果

| frame | direction | step / local P99 | 5 ms wet ratio | 20 ms output/dry |
|---:|---|---:|---:|---:|
| 12,000 | EFX→Java | 754 / 647.12 = 1.1652 | 1.6576 | 1.2483 |
| 24,000 | Java→EFX | 91 / 242.03 = 0.3760 | 0.5956 | 1.0420 |
| 60,000 | EFX→Java | 86 / 106.03 = 0.8111 | 1.1107 | 1.0900 |
| 72,000 | Java→EFX | 53 / 105.03 = 0.5046 | 0.3854 | 0.9850 |
| 108,000 | EFX→Java | 171 / 167.03 = 1.0238 | 3.4918 | 1.0194 |
| 120,000 | Java→EFX | 10 / 167.03 = 0.0599 | 0.3708 | 1.0236 |

最大 step/P99 为 `1.1652`，低于 2.0；最小 20 ms output/dry 比为
`0.9850`，最长零样本 run 为 1 sample。六个边界均未解析到异常 click 或 20 ms
dropout，且输出严格保持单一湿声所有者。

## 可复现证据

```powershell
.\gradlew.bat verifyBackendFailoverContinuity --rerun-tasks
.\gradlew.bat acousticResearchCheck --rerun-tasks
```

- D104 report SHA-256：  
  `6cd35f20dfe95769506c74ef61dd8619983ed084fcbebdeea686d10311c20f88`
- D104 PCM SHA-256：  
  `02828d253bd226cf11d98c55c023cffd2240e95249ba1ac9384c2f9d03468dc4`
- independent verification SHA-256：  
  `e69778c7d25ccc1149d434c4ca98efccdc87c274d3f588ef90131d6ca768b300`
- bound D100 transition report SHA-256：  
  `c75a26c790260e9c21dcbc1610e1a32bafe05db720c6faf3797033cdb129e66d`
- bound D102 failover report SHA-256：  
  `d3c6a6d60467b1e2977e3c4e650040cb56aa181072577a05dc4b20b78f30ff99`

独立 verifier 重建完整所有权区间与 PCM，重新计算六个边界指标，并检查 D100/D102
哈希、故障顺序、双湿声、cold-start overclaim、端点/录音声明和全部正向 gate。
测试包含 1 个正例与 9 个负例。

本轮统一回归为 `314 Python tests passed`；Java/core/mod 测试、CPU DDA reference
的 2/2 CTest 与静态 CUDA source contract 同时通过。当前主机没有 `nvcc`，所以没有
CUDA 编译、GPU 执行或 GPU 性能声明。

## Claim boundary 与下一步

D104 证明的是现有两个 warm-state 软件 render 之间做独占选择时，没有解析出异常
sample step 或 20 ms dropout。它没有运行 Minecraft main-context 的 PCM readback，
没有测量新建 Java FDN 的 delay-line fill，也没有测量 reload 后 EFX 尾声建立过程；
更没有打开声卡 endpoint、系统 loopback 或麦克风。

D105 应专门测量 cold-start continuity：

1. 在每个 fallback 边界才创建并配置新的 `ListenerSharedFdn`，从空 delay lines 开始；
2. 在 recovery 边界创建新的 EFX effect/slot/filter，不继承旧 context 尾声；
3. dry 必须连续，分别量化 wet onset、尾声丢失、最长 dropout 与边界 step；
4. 若 cold-start 门限失败，优先研究仅对新 owner 的短 fade-in 或 dry-safe handoff，
   不允许预运行第二湿声或用 crossfade 违反单所有者合同；
5. 物理 endpoint 与听感验证继续等待用户明确授权。
