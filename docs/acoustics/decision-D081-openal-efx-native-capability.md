# D081 — Minecraft 声音线程上的 OpenAL EFX 原生能力通过

日期：2026-07-25  
状态：**live capability/resource lifecycle passed；source attachment 与听觉校准 pending**

## 目标

研究计划把 Fabric 官方程序化 PCM 作为默认主线，同时保留 OpenAL EFX 作为
capability-gated 的原生简化层。本轮回答三个此前没有实证的问题：

1. Minecraft 当前设备是否真的是 OpenAL Soft，而不只是存在 LWJGL 类；
2. 活动 context 是否公开 `ALC_EXT_EFX`、auxiliary send 和 HRTF 状态；
3. 能否在 Minecraft 自己的声音线程安全创建并释放 EFX 资源。

## 实现边界

新增两个只读 Mixin accessor：

- `SoundManager.soundEngine`；
- `SoundEngine.executor`。

探针通过 `SoundEngineExecutor.schedule` 在 `"Sound engine"` 线程运行。它读取当前
ALC context/device，并在 EFX 存在时临时创建：

- 一个标准 `AL_EFFECT_REVERB` effect；
- 一个 `AL_FILTER_LOWPASS` filter；
- 一个 auxiliary effect slot，并把 effect 绑定到 slot。

资源随后按 slot→filter→effect 逆序释放并检查 AL error。探针没有取得
`ChannelAccess`，没有修改任何 Minecraft source，也没有改变默认 PCM/FDN 路径。

## 本机实测

| field | result |
|---|---|
| thread | `Sound engine` |
| active context | true |
| device / renderer | `OpenAL Soft` |
| vendor | `OpenAL Community` |
| version | `1.1 ALSOFT 1.23.1` |
| `ALC_EXT_EFX` | true |
| maximum auxiliary sends | 2 |
| temporary resources created | true |
| temporary resources released | true |
| AL error | 0 |
| `ALC_SOFT_HRTF` | true |
| HRTF enabled | false |

结论是：本机的 Minecraft/OpenAL 组合具备一个共享 reverb slot 加逐源 low-pass/send
的技术能力，且无需引入 Steam Audio/JNI。`2` 个 sends 不是“每架无人机两条 voice”
的预算；aux send 是 OpenAL source 的 effect routing 数量，仍应保持一个 listener
shared environment effect，不能按无人机复制长混响。

HRTF 扩展存在不等于 HRTF 已启用。本次设备明确报告 `hrtf_enabled=false`，因此不能
声称当前已有双耳渲染。

## 可复现证据

```powershell
.\gradlew.bat --no-daemon :fabric-mod:runClientGameTest
.\gradlew.bat --no-daemon verifyOpenAlNativeCapability
```

- live capability SHA-256：  
  `83d93756e2c873c0917000c1a050a9c211133e9e185fead255ee217f19f2aea1`
- independent verification SHA-256：  
  `22c4ce2fe6ae901cfe762710286053eff0b3131a4b08a4c933473966896e2d40`
- Python regression：`125 tests passed`。

verifier 接受自洽的无 EFX fallback，但只在 active sound-thread context、至少一个
send、资源创建/释放与零 AL error 同时满足时输出
`native_efx_eligible=true`。HRTF enabled 也必须以扩展存在为前提。

## 决策

1. OpenAL EFX 从“API 可能存在”升级为本机 `native_efx_eligible=true`；
2. 默认程序化 PCM 与 listener-shared Java FDN 保持不变；
3. 下一步只实现 opt-in、默认关闭的一个 shared auxiliary reverb slot，加已有
   drone channels 的 low-pass/send；所有 OpenAL 操作必须留在 sound thread；
4. device reload、sound restart 和 source removal 必须各自有资源释放/重建门禁；
5. EFX 参数在 matched RIR/听测前只能标记 `[H]`，不得把 capability 通过写成音质
   或发布校准通过；
6. Steam Audio JNI 的优先级继续低于 EFX，因为当前原生能力已覆盖首个简化目标。

## Claim boundary

本轮只是能力与临时资源生命周期验证。没有 Minecraft source filter/aux-send、没有
audible A/B、没有 device reload、没有 click/underrun 计数，也没有 measured RIR。
报告保持 `default_audio_path_changed=false` 和 `release_calibrated=false`。
