# D067 — 同坐标 processed-autopower 必须成对比较，不能归因于全局增益

状态：**implemented and exercised on straight/tripped public data**
（2026-07-25）。

## 决策

D066 的单文件统计扩展为严格 paired comparison：
`docs/scripts/compare_spectral_references.py` 只有在 baseline 与 candidate 的 RPM、
theta、radius、frequency grid、blade count、harmonic window 和 normalization
完全一致时才运行。每个 delta 都对应相同 `(RPM, theta)`，禁止把不同工况的平均谱
相减。

comparison report 与输入 report 一样永久关闭：

- release profile；
- raw time-history 声明；
- phase/PCM 恢复；
- profile fitting。

## 输入对

本轮只比较同名 straight family：

| role | file | datafile id | SHA-256 |
|---|---|---:|---|
| baseline | `straight_B_acoustic_autopower.h5` | 709433 | `5ce7411ccaaee7d13a32a5ad8fec0bb16c720ad335b0540a7d3af17cd90d0faf` |
| candidate | `straight_tripped_BT_acoustic_autopower.h5` | 709441 | `a56a9fa7dd3b6019686c32ac426b9630228a287dc8d17db5fff825c767afdbfb` |

文件名与论文主题支持把这组关系记录为
`boundary-layer-tripping-straight-rotor`，但 report 同时声明：因果归因仍需核对
matched CAD/geometry metadata。`B`/`BT` 标签本身不能证明除了 trip 之外绝无差异。

两份文件各自先通过 D066 adapter。baseline 有 39 个、tripped 有 40 个 complete
harmonic-directivity fits；comparison 仅取 39 个共同 fit，不用缺失点补值。

## 新增 BPF-removed broadband

原始 band SPL 包含 BPF lines，不适合判断 broadband。D066 analyzer 现在另外生成：

```text
bpf_removed_band_levels_db_spl_at_reference_distance
```

每个已分析 BPF harmonic 的 tone bins 被 local sideband median 替换，而不是直接
置零。这保留估计的 broadband floor，同时去掉最多 8 个 BPF lines。限制仍然明确：

- 只移除配置的 BPF harmonics；
- 未移除 shaft/electrical/unknown discrete tones；
- 没有独立 background product；
- 不能恢复 time-domain roughness 或 modulation。

## 真实 paired 结果

总计 `5 RPM × 13 theta = 65` 个一一对应 measurement pairs。

### 所有角度、所有 RPM

candidate `straight_tripped_BT` 相对 baseline `straight_B`：

| metric | mean delta dB | min | max |
|---|---:|---:|---:|
| BPF-removed low 20–300 Hz | +1.129 | -5.164 | +4.646 |
| BPF-removed mid 300–3200 Hz | -1.832 | -3.663 | +0.466 |
| BPF-removed high 3.2–20 kHz | -2.342 | -3.507 | -1.263 |
| BPF harmonic 1 | -0.480 | -3.355 | +2.018 |
| harmonic 5 | -3.157 | -6.419 | +0.465 |
| harmonic 6 | -2.164 | -5.955 | +1.059 |
| harmonic 7 | -2.515 | -5.087 | -0.621 |
| harmonic 8 | -1.741 | -4.152 | +0.753 |

high-band delta 在全部 65 点都为负，是这组数据中最稳定的变化；low-band 与各
harmonic 的角向范围较大，不能压成一个 scalar gain。

### 按 RPM 对 13 个角度取均值

| RPM | low residual | mid residual | high residual | BPF |
|---:|---:|---:|---:|---:|
| 4000 | +3.530 | -2.669 | -3.307 | -0.757 |
| 5000 | -0.721 | -1.282 | -2.697 | -0.452 |
| 6000 | -0.326 | -1.873 | -2.226 | -0.612 |
| 7000 | +2.209 | -1.036 | -1.561 | -0.071 |
| 8000 | +0.956 | -2.298 | -1.917 | -0.508 |

tripping-related high-band difference随 RPM 改变；不能只在所有 RPM 上应用固定
`-2.34 dB`。

### Rotor plane

0° plane 的 BPF delta 很稳定，为 `-0.242…-0.336 dB`，但 BPF-removed high-band
delta 从 `-2.888 dB @4000 RPM` 变化到约 `-1.435…-1.610 dB @7000–8000 RPM`。
plane low-band 反而是 `+0.565…+3.272 dB`。

因此“总声级降低/升高”不是充分模型；tonal、low/mid/high broadband 和 RPM 必须
分开。

### Directivity

BPF even-polynomial axis delta 随 RPM 为：

```text
4000: -2.792 dB
5000: +0.032 dB
6000: -0.809 dB
7000: +1.224 dB
8000: -1.499 dB
```

这些 axis 值都由 ±60° 向 90° 外推。符号随 RPM 改变，进一步否定一个通用的
“tripped directivity correction”。

## 对 Minecraft 声源模型的影响

本证据支持现有架构中的以下选择：

1. rotor orders 与 broadband 必须分层；
2. broadband 至少保持 low/mid/high 分带；
3. harmonic amplitude 与 RPM trend 应逐阶次验证；
4. directivity 属于 airframe/propeller/operating-state profile；
5. 不新增一个对所有 FPV propeller 通用的 `trippingGain`。

它没有证明目标 5-inch propeller 会产生相同 delta。可迁移的是**模型结构需求**，
不是上述数值。

## 复现

先用 D066 命令生成 baseline/candidate report，然后：

```powershell
python docs/scripts/compare_spectral_references.py `
  --baseline-report build/research/serration-straight-b-spectral-reference-v1.json `
  --candidate-report build/research/serration-straight-bt-spectral-reference-v1.json `
  --baseline-label straight_B `
  --candidate-label straight_tripped_BT `
  --relationship boundary-layer-tripping-straight-rotor `
  --output-json build/research/serration-straight-b-vs-bt-paired-v1.json
```

当前输出：

```text
paired measurements: 65
common complete directivity fits: 39
bytes: 180,545
SHA-256: fda4be800f2dbd6cd69aaf763b0c1f0b99369c9cb7056c8f2c05c187a4db171c
release_profile_eligible: false
```

后续 serration comparison 必须复用相同 comparator，且先由 CAD/论文确认真正
matched 的 baseline/candidate pair。

