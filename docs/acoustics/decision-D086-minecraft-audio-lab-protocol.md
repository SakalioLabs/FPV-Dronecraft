# D086 — Minecraft dry/Java-FDN/OpenAL-EFX audio-lab 协议通过

日期：2026-07-25  
状态：**互斥 backend override、三频同步 marker、control/reload timeline、真实 Client GameTest 与 independent verifier passed；外部录音 pending**

## 目的

D085 已有安全 recorder 和 waveform continuity analyzer，但外部录音若没有与
Minecraft 事件共享的同步标记，就无法精确定位 reload，也无法保证 dry、Java FDN
和 OpenAL EFX 使用相同实验节奏。D086 在 Minecraft 客户端内增加有界 audio-lab
状态机；它只控制声音后端和 marker，绝不打开录音设备。

## 后端选择

新增 `AcousticBackendSelector`，模式为：

- `DEFAULT`：正常游戏，继续读取原来的默认关闭 properties；
- `DRY`：强制 Java FDN 与 OpenAL EFX 都关闭；
- `JAVA_FDN`：只启用 listener-shared Java FDN；
- `OPENAL_EFX`：只启用 listener-shared OpenAL EFX。

只有 active audio-lab session 可以从 `DEFAULT` 原子切到显式模式；第二个并发 session
会被拒绝。session 完成、失败、world/sound-manager stop 时都恢复 `DEFAULT`。
`fpvdrone.proceduralAudio=false` 时拒绝启动实验。

因此 audio lab 不会永久改变用户 property，也不会允许 Java FDN 与 EFX 叠加。

## Audible marker 与时序

新增 listener-relative、finite streaming PCM marker：

| event | tick | frequency | duration |
|---|---:|---:|---:|
| session start | 0 | 880 Hz | 80 ms |
| pre-boundary | 40 | 1320 Hz | 80 ms |
| reload/control boundary | 50 | — | — |
| post-boundary | ≥60 | 1760 Hz | 80 ms |
| session complete | post + 100 | — | — |

marker 为 48 kHz、mono、PCM16 正弦，首尾各 5 ms fade，避免 marker 自己产生
click。每个 marker 必须得到 Minecraft `PlayResult.STARTED`；否则 session 失败。

post marker 只有在目标 backend 真正 ready 后才播放，因此它同时是恢复完成标记：

- dry：Java/EFX 都 inactive，EFX resources 已释放；
- Java FDN：Java wet stream active，EFX inactive/resources false；
- EFX：Java wet stream inactive，shared resources active，至少两个 source/filter
  且数量相等。

## 客户端命令

至少一个 audible drone 已 active 时：

```text
/fpvdrone-acoustics audio-lab dry control
/fpvdrone-acoustics audio-lab dry reload
/fpvdrone-acoustics audio-lab java-fdn control
/fpvdrone-acoustics audio-lab java-fdn reload
/fpvdrone-acoustics audio-lab openal-efx control
/fpvdrone-acoustics audio-lab openal-efx reload
/fpvdrone-acoustics audio-lab status
```

timeline 写入游戏目录的 `acoustic-diagnostics/`。报告包含：

- backend/variant；
- marker contract；
- 每个 event 的 client tick 与 session-relative `System.nanoTime`；
- reload call duration；
- boundary 前后 Java/EFX/source/filter/context/AL error 状态；
- 明确的 no-capture/loopback/release-calibration flags。

## Client GameTest 结果

真实集成世界顺序执行三个代表 case：

| case | backend before/after | reload | result |
|---|---|---:|---|
| dry-control | Java false, EFX false | false | passed |
| java-fdn-control | Java true, EFX false | false | passed |
| openal-efx-reload | Java false, EFX true, 2 sources/2 filters | true | passed |

EFX case：

- context rebuilds `2→3`；
- `SoundManager.reload()` call duration `1,246.1978 ms`；
- backend/source reattach 在 tick `65` ready，即 boundary 后 `15 ticks`；
- AL error 始终 `0`；
- session 完成后 override 已恢复 `DEFAULT`；
- 后续删除 drone 仍释放所有 native resources。

这些 tick 和 duration 是一次真实运行结果，不是发布性能阈值；机器负载、音频设备
和资源 reload 可能改变恢复时间。独立 verifier 的 hang-sanity bound 为严格
`0 < duration < 5 s`，并有 1.5 s 合法 loaded-Windows test 与 5 s 拒绝测试；
它不把 reload call duration 命名为端到端音频延迟。

## 可复现证据

```powershell
.\gradlew.bat --no-daemon :fabric-mod:runClientGameTest
.\gradlew.bat --no-daemon verifyAudioLabProtocol
```

- dry report SHA-256：  
  `946eef6be898f7ed657715c24b1497a32cc8a4ed68de199cdd7b94d724acb1ec`
- Java FDN report SHA-256：  
  `b1145f83d677295568ec2cdd1e53709461bbfc3bc01110d87a447e318d1b321b`
- OpenAL EFX report SHA-256：  
  `7af5bf2fe24f1717efec8e27fc237dc31facc7b64263f1668813230840ff8bba`
- independent verification SHA-256：  
  `95a441abd5bab21b26cde21f9179687dcbfba7b239adcf5a66b539c39b049e6b`

## 决策

1. dry/Java-FDN/EFX A/B 必须通过 audio-lab override，而不是人工修改 properties
   后假定互斥；
2. control 与 reload 必须使用相同 marker/tail 时序；
3. 外部 capture 用三个 audible marker 把 recorder 时间轴映射到 Minecraft timeline；
4. post marker 表示 backend ready，不等于中间没有 click/dropout；
5. 真实发布 gate 仍需六个 backend×variant case、每个至少三次独立 capture；
6. 本轮没有打开 recorder，不能声称 marker 已在扬声器/loopback WAV 中检出；
7. backend 参数仍未 matched-RIR 校准，`release_calibrated=false`。

## Claim boundary

本轮证明 Minecraft 内部后端互斥、marker stream 启动、事件时间轴、EFX reload 恢复和
override restoration。没有麦克风或 loopback WAV，没有自动 marker-to-WAV 对齐，
没有 callback underrun count、A/B/ABX 或 matched RIR。
