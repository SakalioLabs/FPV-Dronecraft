# D091 — OpenAL 原生 timing 已接入 audio-lab 哈希证据链

日期：2026-07-25；streaming-offset 判定修订：2026-07-26  
状态：**三个真实 backend、六对 native samples、timeline v2、D087/D088
hash binding 与独立 verifier passed；真实录音 pending**

## 目的

D090 证明当前运行时可读取 `AL_SOFT_source_latency`，但独立诊断报告尚未与 D086
audio-lab 的每个 backend/take 绑定。若未来只保存 WAV、marker timeline 和 host
timestamp，就会丢失采集时的 renderer-side 延迟状态；若使用一个全局 sidecar，
也无法证明它与某个 take 来自同一次实验。

本轮把 native timing 直接写入每个 D086 timeline。D087 alignment 已固定 timeline
SHA-256，D088 session materializer 又固定 alignment SHA-256，因此无需新增松散文件
即可把原生样本纳入既有证据链。

## timeline v2

每个 session 现在执行：

1. tick `20` 请求 boundary 前第一份 sound-thread sample；
2. 至少间隔 `4` ticks 且宿主单调时钟至少前进 `150 ms` 后请求第二份并验证为一对；
3. tick `50` 前若该 pair 未完成，session fail closed；
4. post-boundary backend ready、第三个 marker 已开始后请求第一份 post sample；
5. 至少间隔 `4` ticks 后请求第二份；只有 pair 验证完成且 post tail 结束才能写报告。

新增 `native_timing` section 包含：

- before/after boundary 的完整 `OpenAlClockLatencyProbe.PairResult`；
- `read_only=true`；
- support 与 device identity 跨 boundary 稳定；
- `audio_path_changed=false`；
- `end_to_end_latency_measured=false`；
- `callback_underrun_counter_available=false`。

probe 仍通过真实 `ChannelHandle.execute` 在 `Sound engine` 线程读取活跃
`DroneLoopSoundInstance`，不修改 source、context、device、EFX 或音频调度。

## 真实 Client GameTest

| backend | phase | host interval | source offset advance | source latency |
|---|---|---:|---:|---:|
| dry-control | before | 197.5661 ms | 200 ms | 51 / 61 ms |
| dry-control | after | 195.8648 ms | 200 ms | 51 / 51 ms |
| Java-FDN-control | before | 193.9198 ms | 200 ms | 61 / 61 ms |
| Java-FDN-control | after | 209.9529 ms | 220 ms | 51 / 61 ms |
| OpenAL-EFX-reload | before | 215.1037 ms | 200 ms | 61 / 51 ms |
| OpenAL-EFX-reload | after | 202.2330 ms | 200 ms | 61 / 61 ms |

完整回归随后证明“source offset 必须严格递增”不适用于 Minecraft 的一秒流式
buffer：已处理 buffer 被 unqueue 后，offset 相对新的 queued-buffer 起点会回绕。
pair 现在限定 `<800 ms`，raw delta `<=0` 时只允许加恰好一秒，并把
`source_offset_advance_seconds` 与 `source_offset_wrapped` 写入证据；修正 advance
还必须与 host elapsed 满足 `max(100 ms, 50%)` 一致性门限。因此合法
`0.90→0.10 s` 被识别为 `200 ms` 回绕，相同 offset 则会被识别成不可信的
`1 s` advance 并拒绝。本表六对样本均未发生回绕。

三种 backend 的共同身份：

- device：`OpenAL Soft`；
- `AL_SOFT_source_latency=true`；
- `ALC_SOFT_device_clock=false`；
- 六对样本均在 sound thread、有 active context、AL/ALC error `0`；
- OpenAL-EFX reload 后 context rebuilds `2→3`，post backend ready tick `63`。

## 独立验证与 D088 绑定

`verify_audio_lab_protocol.py` 现在要求 timeline schema v2，独立重算六对样本的 host
elapsed、可选 device-clock rate、source offset progress，并验证设备/能力身份跨三个
case 一致。它拒绝缺失 timing、篡改 elapsed、错误码、unsupported telemetry 和
端到端/underrun overclaim。

`manage_audio_lab_session.py materialize` 对计划中的每个 take 再执行相同的
fail-closed 结构与数值检查：

- timeline 必须是 schema v2；
- D087 alignment 必须绑定该 timeline SHA-256；
- timing identity 必须在全部 18 cases 一致；
- session report 保存每 case timing 摘要和全局 identity；
- renderer latency 明确不能替代 marker-based end-to-end alignment。

确定性 18-case fixture 已覆盖 timing hash shape，并继续故意让 reload cases 含
click，所以 continuity、real loopback、listening 和 release gates 仍为 false。

## 可复现证据

```powershell
.\gradlew.bat --no-daemon :fabric-mod:runClientGameTest
.\gradlew.bat --no-daemon verifyAudioLabProtocol
.\gradlew.bat --no-daemon verifyAudioLabSessionFixture
```

真实 timeline：

- dry SHA-256：  
  `fbb156eb1181de985a0fd5aae7cc79ad4b020db025de97fa241ef67eff0432e6`
- Java FDN SHA-256：  
  `64e6690b0a35b88b8f8cb6b840e18d33d77ad3494a7dfb6145292a575a79c27b`
- OpenAL EFX reload SHA-256：  
  `2948107439ce077895c8799f870d2eab4d2d44a41d60c9e163ea84848eaf779f`
- protocol verification SHA-256：  
  `e2fc682adbaf4e5e1f2d7f2c2d53efc76116a26c3bd35ee19dbf6a2050cd2283`

D088 deterministic fixture：

- session report SHA-256：  
  `f6b01ecff3f337e5d39fb46c4ea09b34dda25a0d592c5378ba79a8e078081645`
- independent verification SHA-256：  
  `4ed98e8d4c7182c6041e8efbcb2b4582b0d64563a0032458def7c26fd1ae893e`

## 决策

1. timeline schema v2 成为后续 D088 实际 capture 的最低版本；旧 schema v1 仍可由
   D087 alignment 工具读取以复现实验，但不能进入新的 session materialization。
2. 每个 take 都必须自带 before/after native timing，不允许用另一次运行的全局
   D090 report 代替。
3. 原生延迟只作 renderer-side diagnostic，不用于移动 marker、补偿 WAV、修改
   backend 或宣称端到端低延迟。
4. 若未来运行时支持 device clock，同一 schema 可启用 drift ratio 验证；当前
   `device_clock_supported=false` 是明确证据。
5. 下一真实门禁仍是用户确认 loopback endpoint 并明确授权录音后执行 D088 矩阵；
   native timing 集成没有扩大录音授权范围。

## Claim boundary

本轮证明真实 Minecraft audio-lab 的 dry、Java FDN 和 OpenAL EFX reload timelines
可以各自保存并哈希绑定只读 OpenAL timing evidence。它没有录音，没有确认物理
loopback，没有端到端延迟、callback underrun、物理设备热切换、听测、matched RIR
或 release calibration 结论。
