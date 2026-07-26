# D121l — 配对增量与多声源 renderer 总预算

## 决策

D121l 允许 D121k 的 high-band sinc8 候选进入**新的离线 renderer 合同和禁用端点的
replay 研究**，但不允许启用 live Minecraft renderer、创建物理音频端点或声称发布
校准。

D121k 的绝对 wall-clock P99 失败原样保留：

```text
verified-minecraft-high-band-fractional-delay-runtime-gate-failed
```

D121l 回答的是不同问题：在同一线程、相同 dry blocks、相同 12-slot control frames
和交错顺序下，候选相对 Lagrange 3 增加多少线程 CPU，以及 6 个最坏 source 是否仍在
一帧采样周期的总预算内。它不追溯修改 D121k 的冻结门。

## 为什么改用整段线程 CPU 累计

Windows/JVM 的 `getCurrentThreadCpuTime()` 在本机的短区间分辨率不足：20-block 聚合仍
可能返回 0。最终合同对每个模式每个 trial 累计 `2,000 × 256` 个输出帧，再除回
`ns/mixed output frame`。baseline/candidate 顺序按 trial 交替。

wall time 只保留为诊断：每个 trial 使用 10 个原始聚合样本，每个样本包含 20 blocks，
并交替执行顺序。wall P99 不得覆盖线程 CPU 门的失败。

该方法仍受粗粒度 CPU 计数影响。尤其单 source 三个 trial 都量化为
`305.176 ns/frame`，其 ratio `1.0` 不能解释为候选完全无成本；4/6-source 的累计时间
更长，分辨率相对更可靠。

## 冻结结果

每个 source 均为 12 个活动 early-reflection slots，包含同一 control-frame submit
和 256-sample render。

| sources | baseline CPU（3 trials） | candidate CPU（3 trials） | 最大 ratio |
|---:|---|---|---:|
| 1 | 305.18 / 305.18 / 305.18 ns | 305.18 / 305.18 / 305.18 ns | 1.0000 |
| 4 | 1739.50 / 2075.20 / 2014.16 ns | 2166.75 / 2136.23 / 2075.20 ns | 1.2456 |
| 6 | 3021.24 / 2960.21 / 2899.17 ns | 3143.31 / 3143.31 / 3204.35 ns | 1.1053 |

冻结 ratio 门为 `candidate/baseline <= 1.25`。最大值 `1.245614`，通过但余量很小，
不能据此放宽未来回归。

6-source 最坏候选线程 CPU 为：

```text
3204.346 ns / mixed output frame
= 15.3809% of 20,833.333 ns @ 48 kHz
```

通过冻结的 `<=20%` 门。作为诊断，6-source wall P99 约为：

| trial | baseline wall P99 | candidate wall P99 |
|---:|---:|---:|
| 0 | 3201.62 ns | 3381.80 ns |
| 1 | 3159.38 ns | 3314.30 ns |
| 2 | 3192.77 ns | 3338.95 ns |

每个 source-count/trial 有五个 allocation windows，共 **45 个窗口**，全部为
`0 bytes`；所有 checksum 有限。

## 允许与禁止

允许：

1. 建立显式版本化的 offline renderer contract；
2. 保持 low/mid Lagrange 3、high sinc8 的 opt-in 实现；
3. 研究不创建物理端点的 Minecraft packet/event replay；
4. 在 1/4/6-source 固定预算上继续回归。

禁止：

1. 把 `NullEarlyReflectionRenderer` 默认值静默改为候选；
2. 把 D121k 的失败报告替换成 D121l；
3. 启用 `live_early_renderer_enabled`；
4. 启动 Minecraft client 或 OpenAL/PCM endpoint；
5. 声称听感、HRTF、真实无人机或 release calibration 已完成。

## 下一步

D121m 应建立 renderer contract v2，但仍是 null backend：

1. 显式选择 low/mid Lagrange 3 + high sinc8；
2. 固定 4-sample 最小 sinc delay 与短延迟回退；
3. 把 D121a energy ledger、D121h slew、D121k quality 和 D121l multi-source budget
   作为联合前置条件；
4. 构造不依赖真实 `ClientLevel` 和声音设备的 block/chunk/world-change packet trace；
5. replay D121g dirty tracker → D121f cache → D121c worker → D121h control →
   null renderer；
6. 检查 generation、fallback、click/continuity、能量守恒和零分配；
7. 只有 endpoint-disabled replay 通过后，才请求运行真实 GameTest/client 的授权。

## 验证边界

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
| D121l contract | `194eb8d85033e51096a6483d94d0d1e9272c3291b125584779cea4d81ef5b2ca` |
| paired Java report | `bf61f2c5047a4510a3c2b1b3086e7f647c2750ed249ab7f76bd200e4da98e6e8` |
| independent verification | `de91d4fc7edc53ef5057d0c863fa2af1f0ffaf068d3d47c410f7b7ab4a3ddfe9` |
| preserved D121k verification | `292cdd73900b5feb9016caa85c6707c5ef6fe364118d2a0d97c868110554c114` |
