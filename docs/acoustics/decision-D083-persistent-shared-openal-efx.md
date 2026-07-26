# D083 — 默认关闭的 listener-shared OpenAL EFX controller 通过

日期：2026-07-25  
状态：**persistent shared slot、双 layer routing、sound-engine reload 与 source-removal cleanup passed；物理设备切换与声学校准 pending**

## 产品实现

新增 `OpenAlEfxController`，由系统属性
`fpvdrone.openalEfx=true` 显式启用，默认值为 `false`。它复用 D070 的 listener
environment snapshot/probe 和 `FdnEnvironmentMapper.Controls`，但把控制量映射到
OpenAL EFX：

- 全部无人机共享一个 standard reverb effect；
- 全部无人机共享一个 auxiliary effect slot；
- 每个活动 motor/propeller source 只持有一个 low-pass send filter；
- send index 固定为 `0`；
- RT60 mid 映射到 reverb decay time，wet gain 映射到 effect gain；
- `sqrt(high RT60 / mid RT60)` 经 `0.1–1.0` clamp 后映射到 effect/send 的
  high-frequency gain。

这些映射是工程 `[H]`，不是 measured RIR 校准值。当前直接声仍由程序化 PCM 中的
三带 transmission 处理，因此 controller 不重复挂 direct filter；low-pass 只作用于
reverb send。

## 线程与生命周期

client thread 只发布 immutable desired state。所有 native IDs、AL 调用、context
识别、attach/detach 与删除都在 Minecraft `SoundEngineExecutor` 上执行。
`AtomicReference + AtomicBoolean` 将 client tick 更新合并为 latest-wins drain，避免
每 tick 无界堆积声音线程任务。

controller 记录当前 ALC context：

- context ID 未变：复用 shared effect/slot 与 per-source filters；
- context ID 改变：旧 context 已拥有并销毁旧 object names，只丢弃旧 IDs，不在新
  context 错删同名对象；随后重建；
- EFX 不存在或当前 context 曾发生 AL failure：保持 vanilla fallback；
- source 消失：若 source 仍有效则先 detach，随后删除 filter；
- 没有活动 source 或 feature 关闭：释放 filters、slot 和 effect。

Minecraft `SoundManager.reload()` 已真实执行 `SoundEngine.destroy()` 与
`loadLibrary()`，从而销毁并重建 OpenAL context。物理输出设备没有在测试过程中被
切换，因此只能声称 sound-engine/context reload，不能声称 physical device switch。

## 后端互斥

`fpvdrone.openalEfx=true` 时，即使同时设置
`fpvdrone.listenerReverb=true`，Java `ListenerReverbSoundInstance` 也不会启动。
环境 probe 继续运行并向 EFX 提供 controls，避免 Java FDN 与 native EFX 同时产生
两条 late-reverb tail。

## Client GameTest

测试显式同时启用两个 property，以验证互斥：

| state | active | after sound reload | after drone removal |
|---|---:|---:|---:|
| controller operational | true | true | false |
| shared resources | true | true | false |
| attached sources | 2 | 2 | 0 |
| source filters | 2 | 2 | 0 |
| context rebuilds | 1 | 2 | 3 |
| cleanup count | 0 | 0 | 2 |
| AL error | 0 | 0 | 0 |

两个 source 对应同一无人机的 motor 与 propeller layer。声音引擎重载后，共享
effect/slot 与两个 source filter 在新 context 自动重建并重新附着。D086 audio-lab
又执行了一次 EFX reload 和一次中间 backend cleanup，因此最终累计 rebuild/cleanup
为 `3/2`。删除实体后，controller 在正常 client tick/source churn 路径释放全部
资源。Java wet bus 全程保持未启动。

## 可复现证据

```powershell
.\gradlew.bat --no-daemon :fabric-mod:runClientGameTest
.\gradlew.bat --no-daemon verifyOpenAlEfxController
.\gradlew.bat --no-daemon verifyOpenAlEfxReload
```

- controller report SHA-256：  
  `bc308674d31982a4c5e81ac7da47902ebbf2f928821ed25895f83dd0f05365ae`
- independent verification SHA-256：  
  `e4908cee1a588838db096e5616a5f75d55eea6e0f236d12a9fbc0fb685922dc1`
- 独立 reload 报告与 verifier 见 D084。
- 当前统一 Python regression：`168 tests passed`。

## 决策

1. 官方原生简化支持现在已有真实 opt-in 产品路径，而不再只是 capability probe；
2. 一个 shared slot + 每 source 一个 send filter 是当前 voice/resource 模型；
3. EFX 与 Java FDN 必须保持互斥，且两者继续默认关闭；
4. sound-engine reload/context replacement 已通过；物理设备热切换仍是独立门禁；
5. 下一步采集 loopback/麦克风输出，比较 EFX、Java FDN 和 dry 的 click、underrun、
   decay 与 A/B/ABX；
6. matched RIR 前不发布当前 decay/gain/high-frequency mapping。

## Claim boundary

本轮证明 persistent routing、Minecraft 声音引擎导致的 OpenAL context replacement
与 source-removal cleanup。没有物理输出设备切换、没有 OpenAL callback underrun
计数、没有录制 audible output，也没有证明 EFX 优于 Java FDN。feature 与 release
calibration 仍为 false。
