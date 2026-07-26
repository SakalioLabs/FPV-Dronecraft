# D085 — 显式授权采集与 reload click/dropout 分析门禁就绪

日期：2026-07-25  
状态：**默认不录音的 Windows capture tool、PCM24 continuity analyzer、deterministic fixture 与 independent verifier passed；真实 Minecraft loopback capture pending**

## 目的

D084 已证明 OpenAL object 生命周期正确，但 AL error `0` 不能证明扬声器输出没有
click、dropout 或 callback underrun。真实音频采集同时涉及隐私和端点语义，因此本轮
先建立可审计的工具链，不自动打开任何麦克风。

## 有界采集工具

新增
[`capture_audio_session.py`](../../tools/acoustics/capture_audio_session.py)：

- 默认必须选择 `list-devices`，只枚举 FFmpeg DirectShow audio endpoints；
- `capture` 必须提供枚举结果中的精确设备名；
- 必须显式提供 `--consent-to-record RECORD_AUDIO`；
- capture 时长限制为 `0.1–600 s`；
- 输出固定转码为 1/2-channel PCM24 WAV；
- 拒绝覆盖已有 WAV 或 report；
- 临时文件完成验证后才原子移动到目标路径；
- report 固定 FFmpeg binary/version、枚举输出、stderr 和 WAV SHA-256；
- 只有操作者明确给出相应 flag 时才记录
  `real_minecraft_capture` 或 `physical_output_loopback_confirmed`。

本机只做了无录音枚举，检测到四个 DirectShow audio endpoints。FFmpeg build
没有 WASAPI demuxer，因此这些名称本身不能证明其中任何一个是物理输出 loopback。

```powershell
python tools/acoustics/capture_audio_session.py list-devices
```

真正录音需要操作者明确执行类似：

```powershell
python tools/acoustics/capture_audio_session.py capture `
  --device "<exact enumerated endpoint>" `
  --duration-s 12 `
  --sample-rate-hz 48000 `
  --channels 2 `
  --output-wav external-data/computational-acoustics/minecraft-loopback/run.wav `
  --output-report external-data/computational-acoustics/minecraft-loopback/run.json `
  --consent-to-record RECORD_AUDIO
```

在端点路由经过独立确认前不得加入
`--physical-output-loopback-confirmed`。

## 连续性分析

新增
[`analyze_audio_continuity.py`](../../tools/acoustics/analyze_audio_continuity.py)。
它读取未压缩 PCM WAV 的指定 channel，并围绕已知 reload timestamp：

1. 建立 reload 前后 RMS baseline；
2. 报告 post/pre level delta；
3. 用 baseline first-difference median/MAD 和 absolute minimum step 检测 click；
4. 用 5 ms frame RMS、相对 baseline 的 24 dB 阈值和至少 20 ms 的持续时间检测
   dropout；
5. 输出 click peak/threshold ratio、dropout count、最长持续时间和逐事件时刻；
6. 显式保留 `openal_callback_underrun_counter_available=false`，因为 waveform
   discontinuity 不能反推出 OpenAL callback 内部计数。

这是一套 waveform-domain diagnostic，不是 AES/ITU 标准化主观质量指标。真实
无人机声源含自然 impulsiveness 和快速调制，因此 release gate 必须结合 dry control、
同步 reload marker 与人工复核，不能把所有大斜率自动解释为系统 click。

## Deterministic fixture

`generateAudioContinuityFixture` 生成 48 kHz、mono、PCM24、6 s 的确定性双音信号，
在 `3.000 s` 注入一个 reload click，并在 `3.650–3.710 s` 注入 60 ms dropout。

实测：

| gate | result |
|---|---:|
| injected reload click events | 1 |
| reload peak / threshold | 18.5709 |
| dropout events | 1 |
| detected dropout | 3.650–3.710 s |
| longest dropout | 60 ms |
| real Minecraft capture | false |
| physical loopback confirmed | false |
| release calibrated | false |

## 可复现证据

```powershell
.\gradlew.bat --no-daemon verifyAudioContinuityFixture
.\gradlew.bat --no-daemon acousticResearchCheck
```

- fixture WAV SHA-256：  
  `ed19cf1309ae32c826624c983f57c487a3256a438aa178ab99e4c3f29108ffdf`
- fixture report SHA-256：  
  `054c8f62532e8f46bf23f0fa59fb4445443882e06e23db5da1c61a4ca4bc30cf`
- independent verification SHA-256：  
  `7871a3bc47b12f19d8bbcf5b1e575333c663ff8cc29f6ce3a3ac89af69b87497`

## 决策

1. 后续真实音频证据必须使用显式设备选择与有限时长 capture，不允许隐式 default
   microphone；
2. 设备枚举结果不等于 loopback 证明，必须记录 Windows routing/endpoint
   configuration；
3. Minecraft 诊断运行必须提供同步 reload timestamp 或可检测 marker；
4. 对 dry、Java FDN、OpenAL EFX 使用相同场景、音量、endpoint 与 capture chain；
5. click/dropout analyzer 通过合成阳性/阴性测试后才能处理真实 capture；
6. waveform dropout 与 callback underrun 分开记账；没有 native counter 时不得写成
   “OpenAL underrun count = 0”；
7. 本轮没有录音，`release_calibrated=false`。

D087 后 continuity analyzer 也接受 `--alignment-json`，从经过 schema/status
检查的 marker-alignment report 读取 `boundary_audio_s` 并固定 report SHA-256；
这优先于人工填写 `--reload-time-s`。

## 下一门禁

经操作者确认端点并授权后，依次采集：

1. dry control；
2. Java listener-shared FDN；
3. OpenAL EFX；
4. 每种后端的无 reload control 与一次带 marker 的 sound-engine reload；
5. 至少三次独立 take。

每次报告 WAV/FFmpeg/config hashes、reload timestamp、click/dropout、level delta，并
盲化为 A/B/ABX 文件。物理设备切换、真实 5-inch FPV 声源和 matched RIR 仍是独立
后续工作。

## Claim boundary

本轮证明采集工具不会默认录音、能生成可追溯 PCM24 report，并证明分析器能检测已知
合成 click/dropout。没有真实麦克风或 loopback capture，没有 OpenAL callback
underrun counter，没有听觉实验，也没有校准 EFX/FDN 参数。
