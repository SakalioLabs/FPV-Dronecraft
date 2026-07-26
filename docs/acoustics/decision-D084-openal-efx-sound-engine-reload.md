# D084 — OpenAL EFX 跨 Minecraft 声音引擎重载恢复通过

日期：2026-07-25  
状态：**真实 SoundManager/SoundEngine reload、context rebuild、source reattach 与独立 verifier passed；物理设备切换 pending**

## 测试目的

D083 的 controller 已按 ALC context ID 实现代际隔离，但仅靠代码路径不能证明
Minecraft 实际销毁声音引擎时能恢复。本轮在真实 Client GameTest 内、两个无人机
layer 已挂载 EFX 时调用 `Minecraft.SoundManager.reload()`。该入口执行
`SoundEngine.destroy()`，随后 `loadLibrary()` 重建 OpenAL engine/context。

这不是模拟计数器，也不是直接调用 controller 测试 hook。

## 结果

| state | before reload | after reload |
|---|---:|---:|
| controller operational | true | true |
| shared resources | true | true |
| attached sources | 2 | 2 |
| source filters | 2 | 2 |
| context rebuilds | 1 | 2 |
| cleanup count | 0 | 0 |
| AL error | 0 | 0 |

重载后：

- 新 context 被 controller 识别；
- 一个 shared standard-reverb effect 与一个 auxiliary slot 被重建；
- motor 与 propeller 两个 source 各自重新获得一个 low-pass send filter；
- Java wet bus 仍被抑制，没有与 EFX 形成双重 late-reverb tail；
- 后续删除无人机仍将 attached sources/filter 降为 `0/0`，shared resources 释放，
  cleanup count 从 `0` 增至 `1`。

## 可复现证据

```powershell
.\gradlew.bat --no-daemon :fabric-mod:runClientGameTest
.\gradlew.bat --no-daemon verifyOpenAlEfxReload
.\gradlew.bat --no-daemon verifyOpenAlEfxController
```

- reload report SHA-256：  
  `f1d4c718c1fb9f2fc8713c03d8f1a98637c58092a87e094f09739a9477042cce`
- reload independent verification SHA-256：  
  `c4e1337abfea840d4b18048f2b9b5b40010f77b1601fe02bd954c6373367c7b7`
- controller report SHA-256：  
  `bc308674d31982a4c5e81ac7da47902ebbf2f928821ed25895f83dd0f05365ae`
- 当前统一 Python regression：`168 tests passed`。

## 决策

1. WP8 的 Minecraft sound-engine/context reload 门禁关闭；
2. controller 的旧 native object names 不跨 context 删除，避免在新 context 错删
   同名对象；新资源按 desired source state 重建；
3. `sound_engine_reload_exercised=true` 与
   `physical_device_switch_exercised=false` 必须分开记录；
4. EFX 与 Java FDN 继续默认关闭，matched RIR 前不发布当前 `[H]` 参数；
5. 下一音频门禁是 loopback/麦克风 capture：记录 reload 前后 click、dropout、
   callback underrun、decay 与 EFX/Java-FDN/dry A/B/ABX；
6. 物理输出设备热切换需在可自动化或人工可复现的设备矩阵中单独验证。

## Claim boundary

本轮覆盖 Minecraft 声音引擎的真实 destroy/load 和 OpenAL context replacement，但
没有在运行中切换 Windows 输出设备。它没有测量可听输出、OpenAL callback
underrun、HRTF、matched RIR 或跨平台行为，也不证明 EFX 比 Java FDN 更真实。
`release_calibrated=false`。
