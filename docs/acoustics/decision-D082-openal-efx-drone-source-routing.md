# D082 — 真实 DroneLoopSoundInstance 的 EFX routing 可安全恢复

日期：2026-07-25  
状态：**live source attach/detach passed；persistent controller 与 device reload pending**

## 目标

D081 只证明活动 OpenAL context 能创建 EFX 资源。本轮进一步验证这些资源能否接到
Minecraft 已播放的无人机声道，而不是停留在孤立的 native object。

## 实现

新增两个只读 accessor：

- `SoundEngine.instanceToChannel`：寻找活动的 `DroneLoopSoundInstance`；
- `Channel.source`：在 `ChannelHandle.execute` 回调的声音线程中取得 OpenAL source。

Client GameTest 等待无人机程序化 PCM 已经形成活动 channel，然后在同一个
`Sound engine` callback 内：

1. 创建 standard reverb effect、low-pass filter 和 auxiliary slot；
2. 把 low-pass 设置为 gain `0.8`、gain-HF `0.35`；
3. 将 filter 作为 source direct filter；
4. 将 slot/filter 作为 send index `0` 的 auxiliary send；
5. 检查 AL error；
6. 将 aux send 恢复为 null slot/null filter，并清除 direct filter；
7. 检查恢复错误，再逆序释放 slot/filter/effect。

测试结束时没有持久 EFX resource，也没有 source routing 残留。

## 结果

| gate | result |
|---|---|
| active drone source found | true |
| instance type | `DroneLoopSoundInstance` |
| thread | `Sound engine` |
| resources created | true |
| direct filter + aux send attached | true |
| vanilla routing restored | true |
| resources released | true |
| final AL error | 0 |
| persistent EFX enabled | false |

这关闭了“官方原生 EFX 能否触达现有程序化无人机声道”的技术可行性问题。它没有决定
滤波强度、reverb 参数或 Java FDN/EFX 二选一策略；`0.8/0.35` 只用于确认参数化
filter 真的能被设置，是 `[H]` 诊断值。

## 可复现证据

```powershell
.\gradlew.bat --no-daemon :fabric-mod:runClientGameTest
.\gradlew.bat --no-daemon verifyOpenAlEfxSourceRouting
```

- live routing SHA-256：  
  `2ff0dc9142c6a54af35ba64f4a7fc289bbc3b67dd76f52159a9e6eb09a45e4f4`
- independent verification SHA-256：  
  `b85f1c670c484ac12afa093211124261dc30ba85f20e05a985ebc02562130426`
- Python regression：`128 tests passed`。

## 决策

1. EFX routing 可复用现有两条 drone layer channel，不增加 OpenAL voice；
2. 产品实现应持有一个 listener-shared auxiliary slot，而不是每个 drone 新建 slot；
3. 每个 active drone layer 最多使用一个 direct filter 和 send index 0；
4. controller 必须在 source stop 前 detach，并在 sound context/device 变化时丢弃旧
   IDs、在新 context 重建；
5. feature 必须独立 opt-in，且与 Java wet bus 互斥，避免双重 late reverb；
6. 在真实 device restart、source churn、click/underrun 与 matched RIR 通过前，
   不实现默认持久启用。

## Claim boundary

探针只在一个活动声道上瞬时附着后恢复。没有跨 tick persistence、没有多个 drone
churn、没有 device reload、没有 audible capture、没有 EFX 参数校准，也没有证明
native EFX 比 Java FDN 更好。
