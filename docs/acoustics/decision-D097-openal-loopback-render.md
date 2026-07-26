# D097 — OpenAL Soft 隔离 loopback 渲染正控

状态：**真实 OpenAL Soft loopback context、真实 D094 生产 PCM、独立输出
sidecar 与 verifier 已通过；不等于 Minecraft 主 context 或物理端点 capture。**

## 决策

保留 `ALC_SOFT_loopback`，但只把它用作诊断/回归用的离线软件渲染器，
不把 Minecraft 的默认播放设备替换为 loopback。

本轮采用独立工作线程和 `ALC_EXT_thread_local_context`：

1. 在 Minecraft `Sound engine` 线程读取主 context/device identity；
2. 在 `Dronecraft OpenAL loopback probe` 线程打开 loopback device；
3. 明确创建 `48,000 Hz / mono / signed PCM16` context；
4. 把 D094 最早的 motor 与 propeller 生产 chunk 分别作为两个 source；
5. 显式调用 `alcRenderSamplesSOFT` 生成一秒 PCM；
6. 删除 source/buffer、清除 thread-local context、销毁 context 并关闭 device；
7. 再次读取 Minecraft 主 context/device，要求 identity 与声音线程均不变。

## 官方语义

OpenAL Soft 的
[`ALC_SOFT_loopback`](https://openal-soft.org/openal-extensions/SOFT_loopback.txt)
规范明确规定：

- loopback 把渲染结果返回给应用，而不是送往系统音频设备；
- context 必须同时指定 frequency、channel configuration 和 sample type；
- device 不随时间自动更新；
- source/buffer 状态只在应用调用 `alcRenderSamplesSOFT` 时推进。

因此它适合做确定性的 renderer readback，但不能直接证明 Minecraft 当前物理播放
链路的结果。

## 生产输入绑定

输入不是合成正弦 fixture，而是同一次 Client GameTest 中由
`ProceduralDroneAudioStream.read` 返回并经 D094 hash-bound 的两个一秒 PCM16 chunk：

| layer | D094 sequence | bytes | SHA-256 |
|---|---:|---:|---|
| motor | 1 | 96,000 | `5c2eaaa8f6beadbc5be53ce1a3f1f4911739008ccdbcf48dd957b0f1953d6e57` |
| propeller | 0 | 96,000 | `db63dd9965805252c060ced355cb5de19ec77e5fd4e44993c55f86c8b7f75e36` |

独立 verifier 重新读取 D094 PCM sidecar，按 `pcm_offset/pcm_bytes` 切片并验证
两个 chunk 的 SHA-256，不能只相信 D097 自报的 input metadata。

## 现场结果

| 字段 | 结果 |
|---|---:|
| render format | s16le mono 48 kHz |
| render frames / bytes | 48,000 / 96,000 |
| wall-clock hold | 50 ms |
| source offset before / after hold | 0 / 0，两个 source 均相同 |
| midpoint offsets | 24,000 / 24,000 |
| midpoint states | `AL_PLAYING` / `AL_PLAYING` |
| final states | `AL_STOPPED` / `AL_STOPPED` |
| rendered nonzero samples | 47,877 |
| rendered peak | 4,482 |
| rendered RMS | 405.504886710 |
| best normalized correlation | 0.999999239 |
| renderer lag | 48 samples（1.0 ms） |
| AL / ALC errors | 0 / 0 |
| Minecraft context/device/thread unchanged | true / true / true |

输出 PCM SHA-256：
`da8d459a15ccf6de50d2dfc990bd4d0c49566e4cb43ecd00154e4214ebc69433`。

零 source 正控的 256 frames 中有 65 个非零 sample，但峰值严格为 1 LSB。
这与 OpenAL Soft
[`ChangeLog`](https://github.com/kcat/openal-soft/blob/master/ChangeLog)
记录的 8/16-bit output dithering 一致。因此正确门禁不是“每个 sample 必须为零”，
而是“空白输出量化抖动峰值不超过 1 LSB”。探针在写入 render buffer 前先放入
非零 sentinel，避免把未写内存误判为静音。

## 独立验证

`verify_openal_loopback_render.py` 不信任 Java summary，并重新验证：

1. D097 → D094 report SHA-256；
2. D094 report → D094 PCM sidecar SHA-256；
3. 两个 input chunk 的 offset、byte count 和独立 SHA-256；
4. D097 rendered PCM sidecar 的 byte count、SHA-256、nonzero、peak 与 RMS；
5. ±1,024 samples 内重新计算 normalized cross-correlation 和最佳 lag；
6. 墙钟不推进、显式 render 推进、midpoint/final state；
7. thread-local 隔离和 Minecraft context/device/thread identity；
8. capture/physical playback/release-calibration false claims。

10 个 Python tests 覆盖成功路径以及 trace hash、trace PCM、input sequence、物理
播放 overclaim、墙钟推进、midpoint、dither、rendered PCM 和信号指标脱链。

运行：

```powershell
.\gradlew.bat :fabric-mod:runClientGameTest --rerun-tasks
.\gradlew.bat verifyOpenAlLoopbackRender --rerun-tasks
python -m unittest discover -s docs/scripts `
  -p test_verify_openal_loopback_render.py -v
```

证据 SHA-256：

- D097 report：
  `8aff0e929212c726dfa62c4fac8fb467aba91fa837903feca9a1cc158a1c175b`
- D097 rendered PCM：
  `da8d459a15ccf6de50d2dfc990bd4d0c49566e4cb43ecd00154e4214ebc69433`
- D097 independent verification：
  `d9c21b56476920b09c7e8f8362b920f8bbbfc71c9a6d6015fcb1135f17bb3852`

## Claim boundary

本轮证明当前 OpenAL Soft 驱动能够在不改变 Minecraft 主 context/device 的条件下，
用独立 thread-local loopback context 消费两条真实生产 PCM，并返回与输入和信号
在 48-sample renderer lag 后高度相关的 PCM。

它不证明：

- Minecraft 当前主 context 实际渲染出的波形；
- 系统播放设备、扬声器或耳机上的物理输出；
- WASAPI/系统 loopback 或麦克风 capture；
- 连续无 underrun、reload 无 click/dropout；
- EFX/FDN 的听感优劣或 release 参数。

## 下一方向

D098 应复用同一个隔离 loopback oracle，对完全相同的 D094 生产输入运行：

1. dry；
2. OpenAL EFX reverb + low-pass；
3. Java FDN reference。

输出需对齐 48-sample renderer lag，比较频带能量、DRR、早期/晚期能量与尾音长度。
这能在不录音的前提下回答“官方原生简化支持的渲染传递函数是否符合预期”，但最终
release 参数仍必须由真实 capture 与听测校准。
