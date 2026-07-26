# D109 — fresh EFX recovery onset-slew policy

日期：2026-07-26  
状态：五档候选筛选、精确 PCM 绑定与独立重算 verifier 通过

## 决策

Java FDN fallback 返回 fresh OpenAL EFX 时保持 **0 ms 直接恢复**。不增加 slot-gain
slew，不保留旧 Java wet tail，不创建第二个 EFX slot，也不做双湿声 crossfade。

D105 已经证明 fresh EFX 的 100 ms 湿声能量不等于 D104 的 warm-state tail，但本轮
进一步证明：对已经较弱的 fresh EFX 再做 5–50 ms gain fade，不能恢复丢失的历史，
反而增加最坏 sample boundary step，并继续削弱湿声能量。因此没有生产代码变更。

## 实验输入

筛选严格绑定当前 D105 cold-start 与 D104 warm-state 软件渲染：

- D105 report SHA-256：
  `f8cc919e6e7d36fe562bcd82dd6b02715590b99a96a7c93155157ace48a3f13a`
- D104 report SHA-256：
  `ac77350d82f49c81ab621195d1c0708cdf1d1d71734e57dd9318a2ad4870bb40`
- cold PCM：
  `142b88e66c0f79a28969c7d63d60ec4456e16a2d58c6bbb32b14967d7dd092a0`
- dry PCM：
  `796bd29c6aecaed9673df38ffdc29bf2fe963c00a21952eb08fd594480ea25b6`
- Java-wet ownership mask：
  `6251a9fbd76b1093171d4b6f18e3f007ce7e07f40b12ba9af4e20c27a6387e12`
- warm PCM：
  `434efe527605a6198a0e41d8e042bc6b8e9b77b73fb5b1400a347abe87e1f2f4`

三个 Java→EFX recovery 边界是 frame `24,000 / 72,000 / 120,000`，分别对应
`resource-create / parameter-write / source-route` fault。Java-wet sidecar 在三个
recovery 后 100 ms 窗口中必须全零，以证明候选没有把旧 Java tail 混入 EFX。

## 候选模型与门限

候选是对 D105 exact cold PCM 的确定性后处理：

```text
candidate = dry + fresh_efx_wet × min(elapsed_frames / slew_frames, 1)
```

比较 `0 / 5 / 10 / 20 / 50 ms`。每个候选独立检查：

- 最大 boundary step / 邻域 difference P99 `<=2.0`；
- 最小 20 ms output/dry RMS `>=0.5`；
- 100 ms wet/direct RMS 保留率 `>=0.95`；
- 正斜坡只有在最坏 step 至少改善 10% 时才可被选择。

选择规则不是“最短正值”，而是：如果 direct 已安全，只有同时保留能量且带来至少
10% step 改善的正斜坡才能替换它。

## 结果

| slew | 最坏 step/P99 | 最小 20 ms output/dry | 最小 100 ms wet/direct | 相对 direct step |
|---:|---:|---:|---:|---:|
| 0 ms | 0.608857 | 0.993407 | 1.000000 | 基准 |
| 5 ms | 0.717811 | 0.994642 | 0.929882 | 恶化 17.89% |
| 10 ms | 0.717811 | 0.994779 | 0.929193 | 恶化 17.89% |
| 20 ms | 0.717811 | 0.995215 | 0.928516 | 恶化 17.89% |
| 50 ms | 0.717811 | 0.998055 | 0.899747 | 恶化 17.89% |

0 ms 在三个 fault 上均通过 click/dropout 工程门限。所有正斜坡都：

- 没有改善边界 step；
- 最坏 100 ms wet retention 低于 `0.95`；
- 无法补回 warm-state EFX 的历史 tail；
- 因而 `slew_justified=false`。

最终：

- `selected_slew_ms=0`
- `direct_reset_accepted=true`
- `production_change_required=false`
- `exclusive_wet_owner=true`
- `double_wet_crossfade=false`

## 独立校验

verifier 不信任 report 中的 metric、aggregate、flag 或 selection。它从六份精确绑定
的 PCM/report 重新构造五个候选，并重算三个边界的：

- absolute step 与 local P99；
- 20 ms output/dry RMS；
- 100 ms wet/direct 与 wet/warm RMS；
- candidate/direct NRMSE；
- pass flag、改善比例和最终 selection。

14 个负向测试覆盖 source/PCM 脱离、Java wet 越权、候选与边界顺序、单项与聚合
metric、门限、flag、selection、double-wet 和 capture overclaim。

## Claim boundary

五个候选不是五次新的 OpenAL render。只有 0 ms 是原始 D105 isolated OpenAL Soft
cold render；正斜坡是在 exact PCM 上模拟的线性 wet-slot gain postprocess。该方法
足以拒绝“只会削弱已有 fresh wet、且不改善边界”的斜坡，但不证明：

- 物理声卡或扬声器端行为；
- 玩家是否能听到 tail reset；
- 听感偏好或发布响度；
- 驱动特定的 gain ramp 实现细节。

本轮：

- `physical_endpoint_opened=false`
- `captures_audio=false`
- `release_calibrated=false`

## 可复现证据

```powershell
.\gradlew.bat verifyEfxRecoverySlew --rerun-tasks
```

- screen report：
  `build/research/efx-recovery-slew-screen-v1.json`
- independent verification：
  `build/research/efx-recovery-slew-verification-v1.json`
- screen report SHA-256：
  `d33dae594ccc1fdb5d89f80f6d63b691af235be331f5096b4138af321eb63369`
- verification SHA-256：
  `180559cc28b2e0cc12b8a9803f53de6d24457023e7fcdaeff4cd85b530cc366e`

## 下一步

D110 不应继续在没有证据的情况下加工 recovery gain。更有价值的方向是把现有
Minecraft/OpenAL 双后端从“故障连续性已验证”推进到“环境响应参数可校准”：

1. 固化一组无需录音的可重复 impulse/probe 输入；
2. 对 closed / partial / open 以及距离、遮挡、材质组合导出 EFX 与 Java FDN 的
   early/late、频带衰减和 RT60 响应矩阵；
3. 将矩阵与公开真实 RIR 数据的可再分发数值统计对齐，而不是复制音频；
4. 只把通过单调性、稳定性和实时预算的参数映射接入生产；
5. 物理 endpoint 录音、ABX 和发布校准仍等待操作者明确授权。
