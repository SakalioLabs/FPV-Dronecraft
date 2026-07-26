# D107 — Listener 共享历史、相位所有权与 Java 湿声交接

日期：2026-07-26  
状态：生产代码接入、三环境离线基准、独立 verifier 通过；真实 Client GameTest 待办

## 决策

Java FDN fallback 不使用常驻静音 OpenAL shadow source。Minecraft 1.21.11 的原生
streaming `Channel` 使用一秒缓冲，启动时预取 4 个；若静音 shadow source 已经播放，
其队列中会保留约三至四秒的未来静音，切换流内部状态不能保证立即出现湿声。

生产路径改为 listener 级、没有 OpenAL source 的 `ListenerReverbHistoryProducer`：

1. OpenAL EFX 或 pending 拥有湿声时，每个 Minecraft tick 生成精确
   `2,400 frames / 50 ms` 的 listener 混合输入；
2. 环形缓冲固定保存最近 `24,000 frames / 500 ms`；
3. motor/propeller synthesizer 由该 producer 持有，shadow→wet 时不重建；
4. Java fallback 创建新 `ListenerReverbAudioStream`，复制一次历史并预运行新 FDN，
   然后显式启动新的 OpenAL streaming source；
5. active stream 从同一 producer 继续相位；没有 silent OpenAL shadow queue；
6. 每次只有一个 wet owner。新 stream 获取所有权后，旧 stream 即使仍被 sound thread
   调用，也只返回零；关闭旧 stream 不会释放新 owner；
7. EFX 恢复后启动新的 shadow epoch，清空 Java active 阶段可能已预生成到未来的相位
   与历史，避免把 OpenAL 四缓冲预取误当作已播放时间。

这不是 dry PCM 的逐样本复制。listener reverb 从一开始就是独立的确定性混合器；
本决策保证其自身 shadow 与 active 生命周期连续，并保留 D106 选择的实际输入历史。

## 为什么不是以 OpenAL sample offset 驱动 shadow

`AL_SAMPLE_OFFSET` 和 `AL_SOFT_source_latency` 适合诊断真实 source 游标，但项目的
motor、propeller 与 listener wet bus 是独立 OpenAL sources，没有单一 dry master
clock。用任意一个 dry source 作为 listener 历史时钟会在 source 创建、停止、距离裁剪
或 buffer rollover 时产生歧义。游戏 tick 是 acoustic emission snapshot 的生产时钟，
所以 shadow 每 tick 固定生成 2,400 帧；真正的 queue-to-first-wet 延迟必须留给下一轮
Minecraft Client GameTest 实测，不能由本轮离线计时冒充。

## 生产遥测

后端 runtime snapshot 与 timeline 新增：

- `java_shadow_active`：无 OpenAL source 的历史生产者正在持有 shadow epoch；
- `java_wet_active`：Java stream 已经实际取得唯一 wet owner；
- `java_history_frames`：当前可用于 fallback 的历史帧数；
- 兼容字段 `java_stream_active` 与 `java_wet_active` 必须相等；
- `double_wet_path` 只由 `java_wet_active && efx_operational` 判定，shadow 不算湿声。

因此 `SoundManager.play()` 已提交但 `AudioStream` 尚未创建的短窗口不再被误报为
Java 已出声。

## 基准协议与结果

基准绑定 D106 report SHA-256
`b2107cef3199c3fd7fc5934d331e6542d305933eeadcc8d42b969d065f9dc99d`，
使用其 closed、partial、open 三组 Minecraft 映射控制。每个 case 使用 6 个 sources，
10 次 warmup、50 次计时：

| 环境 | shadow tick P99 | 完整 handoff P99 | FDN pre-roll P99 | first wet |
|---|---:|---:|---:|---:|
| closed | 1.1038 ms | 1.6327 ms | 见 report | frame 0 |
| partial | 0.5487 ms | 2.8095 ms | 见 report | frame 0 |
| open | 0.4248 ms | 1.3563 ms | 见 report | frame 0 |

门限是 shadow P99 `<=12.5 ms`（50 ms tick 的 25%）、handoff P99
`<=10.6667 ms`（2048-frame buffer 的 25%）以及 first wet frame `==0`。
三环境全部通过。每例都验证：

- handoff 前与 pre-roll 都是完整 24,000 帧；
- 6 个 synthesizer 在 shadow→wet 获取所有权时保持存在；
- 被替换 owner 输出全零；
- 关闭旧 owner 后 replacement 仍然有效；
- wet→shadow restart 后 wet owner、旧 history 和未来 phase 都已清除。

## 可复现证据

```powershell
.\gradlew.bat verifyListenerReverbHistoryHandoff --rerun-tasks
python -m unittest discover -s docs/scripts -p "test_*.py"
```

- report：
  `build/research/listener-reverb-history-handoff-v1.json`
- PCM sidecar：
  `build/research/listener-reverb-history-handoff-v1.pcm`
- independent verification：
  `build/research/listener-reverb-history-handoff-verification-v1.json`
- report SHA-256：
  `8d6d981654f59a9794fd7c096ff5a28a723c9b3d14ce605041950e591f2a4406`
- PCM SHA-256：
  `5fcea694e28d91ce30546839e4e65436d1101d7f2f241a9b92c97ae71d54b877`
- verification SHA-256：
  `306e1bd1e8e60e1ed05ba437ca8a3c462452b83a9a2fa03c3d431dfd2fbde231`

## Claim boundary 与下一步

本轮 D107 离线 probe 没有打开物理 endpoint、没有录音，也没有测量
Minecraft/OpenAL source 的实际 restart latency；`client_gametest_measured=false`。
离线 first-wet frame 0 证明已预热
FDN 的第一块 PCM 没有算法性 1,423-frame onset，但不等于玩家设备在 frame 0 听到声音。

D108 应扩展真实 Client GameTest 的 EFX fault→Java fallback→reload recovery 循环：

1. fault 前等待 `java_history_frames == 24,000`；
2. 记录 fault、`java_shadow_active=false`、`java_wet_active=true` 的 tick/host 时间；
3. 在 `Channel` queue hook 绑定 listener reverb stream 的第一个非零 buffer；
4. 验证旧 source 被停止、新 source 第一块非零 PCM 入队、没有旧静音 buffer 被继承；
5. 测量 queue-to-first-wet 与 source restart 延迟；
6. recovery 后验证 `java_wet_active=false`、`java_shadow_active=true`、
   `double_wet_path=false`，并等待历史重新填满；
7. 只有上述真实循环通过后，才把 D107 从“生产代码已接入”提升为
   “Minecraft runtime failover 已验证”。

上述 D108 门禁现已完成，详见
[`decision-D108-listener-reverb-live-queue-handoff.md`](decision-D108-listener-reverb-live-queue-handoff.md)。
