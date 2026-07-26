# D087 — 三频 marker 的 recorder↔Minecraft 自动时间对齐通过

日期：2026-07-25  
状态：**three-tone detector、affine clock fit、boundary transfer、deterministic interference fixture 与 independent verifier passed；真实 loopback WAV pending**

## 目的

D086 的 Minecraft timeline 使用 session-relative `System.nanoTime`，而外部 recorder
使用自己的 sample clock。即使二者同时启动，也存在未知 offset 和 clock drift；
直接把 Minecraft 的 reload 秒数复制到 WAV 会产生系统误差。

D087 新增
[`align_audio_lab_capture.py`](../../tools/acoustics/align_audio_lab_capture.py)，从 WAV
中识别 D086 的 880/1320/1760 Hz markers，并拟合：

```text
recorder_time = slope × minecraft_relative_time + offset
```

随后把 `sound_engine_reload_requested` 或 `control_boundary_no_reload` 映射到精确的
`boundary_audio_s`。

## Marker detector

对每个 marker：

1. 按 timeline 的 frequency、80 ms duration 和 5 ms fade 构造 cosine/sine
   quadrature templates；
2. 以默认 2 ms hop 扫描指定 PCM WAV channel；
3. 对每个 window 做 normalized two-quadrature least-squares energy score；
4. 选择最高分，并要求三个 marker score 均至少 `0.35`；
5. 要求检测时刻严格保持 start < pre-boundary < post-boundary；
6. 使用三个对应点拟合 affine clock，输出 residual、offset、slope 和 ppm delta。

quadrature score 对未知声卡/recorder phase 不敏感；normalized score 减少音量差异的
影响。它仍可能受强窄带干扰、AGC、重采样或 marker 被设备滤除影响，因此真实 gate
必须保存每个 score 和人工可视复核。

## Continuity analyzer 绑定

`analyze_audio_continuity.py analyze` 新增：

```text
--alignment-json <alignment-report.json>
```

启用后：

- 严格检查 alignment schema/status；
- 读取 `alignment.boundary_audio_s`；
- 将其作为 click/dropout analysis center；
- 输出 alignment report SHA-256；
- `reload_time_source` 记录为 `audio-lab-alignment-report`。

这消除了人工抄写 reload timestamp，但不改变 D085 的 waveform-domain
click/dropout claim boundary。

## Deterministic fixture

fixture 为 48 kHz、mono、PCM24、8 s：

- 背景包含 233 Hz、701 Hz tones 和固定 seed broadband noise；
- Minecraft marker time 为 `0.2/2.2/3.6 s`；
- recorder clock 使用 slope `1.002`、offset `0.75 s`；
- 预期 WAV markers 为 `0.9504/2.9544/4.3572 s`；
- 预期 reload boundary 为 `3.4554 s`。

结果：

| metric | result | gate |
|---|---:|---:|
| minimum marker score | 0.982008 | ≥0.9 fixture gate |
| maximum marker timing error | 0.8000 ms | ≤2 ms |
| maximum affine residual | 0.4658 ms | ≤2 ms |
| boundary error | 0.2301 ms | ≤2 ms |
| recovered slope error | 328.77 ppm | ≤500 ppm |

fixture 故意包含 `2000 ppm` clock difference，证明实现不是只做单一 offset。

## 可复现证据

```powershell
.\gradlew.bat --no-daemon verifyAudioLabAlignmentFixture
```

- PCM24 WAV SHA-256：  
  `f53f00ac30c3fd0d7b6665c96f1030a39115c6578fe0930728695e8071449d63`
- timeline SHA-256：  
  `47c39814ec43a5e1d4b349c5b48d8aacf6dba551b03a6439198fe6ccea762363`
- alignment report SHA-256：  
  `699a36383a05a2054e2c2930ff9664450c6e68949f9f033ce3c1fc13ac3a2112`
- independent verification SHA-256：  
  `6b6be975416d6167f7b3f0e5c6a98d2b59bb60a5c2029d00c854c4f18da7a0ba`
- 当前统一 Python regression：`168 tests passed`。

## 真实运行顺序

1. 用 D085 recorder 显式选择已确认的 endpoint 并开始有限时长 capture；
2. 在 Minecraft 中启动 D086 audio-lab case；
3. 完成后停止 recorder，保留 WAV、capture report 和 Minecraft timeline；
4. 执行 alignment：

```powershell
python tools/acoustics/align_audio_lab_capture.py analyze `
  --wav <capture.wav> `
  --timeline-json <minecraft-timeline.json> `
  --output-json <alignment.json> `
  --real-audio-capture
```

5. 把 alignment 交给 continuity analyzer：

```powershell
python tools/acoustics/analyze_audio_continuity.py analyze `
  --wav <capture.wav> `
  --alignment-json <alignment.json> `
  --output-json <continuity.json> `
  --real-minecraft-capture
```

只有 endpoint routing 经独立确认后，两个命令才可加入各自的
`--physical-output-loopback-confirmed`。

## 决策

1. 真实 audio-lab WAV 不再接受纯人工 reload timestamp 作为首选证据；
2. 三 marker affine fit 是 recorder/Minecraft 时间同步的最低门禁；
3. 任一 marker score 失败、顺序错误或 residual 超 gate 时整次 take 作废；
4. clock ppm 只描述 recorder 对 Minecraft monotonic clock 的映射，不是声卡质量；
5. alignment report、timeline 和 WAV 必须分别固定 hash；
6. 本轮仍没有真实录音，`release_calibrated=false`。

## Claim boundary

本轮证明在带 tones/noise 干扰的确定性 PCM24 fixture 中，三频 marker 能以 2 ms hop
恢复 affine clock 和 boundary。没有捕获 Minecraft 扬声器输出，没有证明实际端点
是 loopback，没有 click/dropout、callback underrun、A/B/ABX 或 matched RIR 结论。
