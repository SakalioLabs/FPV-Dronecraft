# D103 — EFX 能力缺失策略与后端 metadata timeline

日期：2026-07-26  
状态：真实 capability、真实 D102 转移时间线、策略仿真与独立 verifier 通过

## 决策

“当前设备支持 EFX”和“无 EFX 时如何路由”必须是两类证据：

- 当前设备能力来自 Minecraft 实际 sound thread 上的
  `OpenAlNativeCapabilityProbe`；
- 无 EFX 路径来自纯函数 policy matrix，并逐项写
  `simulated=true`；
- 当前环境没有第二个无 EFX 的 OpenAL 实现或硬件，因此
  `no_efx_hardware_exercised=false`；
- policy 仿真可以证明软件决策，不可以证明另一驱动、声卡或实现的生命周期行为。

这保留了“官方原生简化支持”的准确边界：Minecraft 已通过 LWJGL 使用 OpenAL Soft，
当前 native context 真实公开 `ALC_EXT_EFX`，所以 shared standard-reverb effect 是
可接入的原生简化层；Fabric/Minecraft 没有面向模组公开一个稳定的高级 EFX API，本项目
仍需 sound-thread accessor、OpenAL object 生命周期和 source-send 路由。它不是纯
Fabric API 功能，也不是 Steam Audio。

## 当前设备真实能力

D081 capability report 在本轮重新绑定：

| field | result |
|---|---|
| active Minecraft sound-thread context | true |
| renderer | OpenAL Soft |
| `ALC_EXT_EFX` | true |
| maximum auxiliary sends | 2 |
| temporary EFX resources created/released | true / true |
| AL error | 0 |
| `native_efx_eligible` | true |

capability report SHA-256：
`83d93756e2c873c0917000c1a050a9c211133e9e185fead255ee217f19f2aea1`。

## 纯策略矩阵

`AcousticBackendSelector.resolve(...)` 现在是无全局状态的纯决策函数；运行时属性入口只
负责把当前模式与配置传给它。

| case | simulated | input status | result |
|---|---|---|---|
| actual device | false | `OPERATIONAL` | `OPENAL_EFX` |
| EFX unavailable + Java requested | true | `EXTENSION_UNAVAILABLE` | `JAVA_FDN` |
| EFX unavailable + no Java | true | `EXTENSION_UNAVAILABLE` | `CLEAN` |
| EFX pending + Java requested | true | `WAITING_SOURCES` | `OPENAL_EFX_PENDING` |
| explicit EFX lab failure | true | `CONTEXT_FAILED` | `OPENAL_EFX_PENDING` |
| procedural audio disabled | true | `OPERATIONAL` | `CLEAN` |

两个容易出错的边界得到保留：

1. pending 不提前启动 Java，避免启动阶段双混响；
2. 显式 EFX audio-lab 不静默回退，避免 A/B 证据被另一算法污染。

## Metadata timeline

运行时维护一个最多 256 个 transition event 的环形缓冲。只有以下字段变化时才追加
事件，不按每帧无条件写日志：

- resolved backend；
- EFX status；
- environment snapshot generation；
- context rebuild / AL error；
- fault stage / injection count；
- Java/EFX ownership 与 double-wet signal。

每个事件同时记录：

- `System.nanoTime()` 单调主机时钟；
- Minecraft game tick；
- low/mid/high RT60、wet gain、transition seconds；
- `captures_audio=false`。

本轮真实 Client GameTest 产生 48 个事件，绑定 D102 的三个 fault cycle。每个
resource-create、parameter-write、source-route 阶段都能在时间线中找到：

```text
OPENAL_EFX/OPERATIONAL
→ JAVA_FDN/CONTEXT_FAILED
→ OPENAL_EFX/OPERATIONAL（context rebuild +1）
```

全部 telemetry sequence 与 host monotonic time 严格增加，Minecraft tick
非递减；没有 double wet path。

游戏内可导出：

```text
/fpvdrone-acoustics export-backend-timeline
```

文件写入 `acoustic-diagnostics/backend-timeline-v1-<timestamp>.json`。导出只复制
metadata，不控制或打开输出端点、系统 loopback、麦克风，也不记录玩家身份和世界坐标。
未来若用户授权真实录音，可用主机单调时钟、Minecraft tick 与 D087 marker 对齐外部
recorder，而无需改变当前隐私边界。

## 可复现证据

```powershell
.\gradlew.bat :fabric-mod:runClientGameTest
.\gradlew.bat verifyBackendCapabilityPolicyTimeline
.\gradlew.bat acousticResearchCheck --rerun-tasks
```

- D103 report SHA-256：  
  `580f3c9b874f26fb2ec59ac762786c48e833f8201fceda1f609cc6062fd73cb7`
- independent verification SHA-256：  
  `f026dd8e4bdae9232087058f5f011cec656e71743abe40ba550fe73df1b1c10c`
- bound D102 failover report SHA-256：  
  `d3c6a6d60467b1e2977e3c4e650040cb56aa181072577a05dc4b20b78f30ff99`

独立 verifier 重新计算 capability/D102 hash，验证 6 个 policy case 的
simulated/route 边界、48 个 event 的容量和双时钟顺序、有限 RT60/wet 参数、无双湿声，
并逐个把 D102 的 fault count/context rebuild 绑定到 fallback 与 recovery event。
8 个负例覆盖 capability 脱链、仿真冒充实测、错误路由、时钟倒退、双湿声、缺失恢复、
录音 overclaim 和无 EFX 硬件 overclaim。

本轮统一回归为 `304 Python tests passed`；Java/core/mod 测试、CPU DDA reference
的 2/2 CTest 与静态 CUDA source contract 同时通过。当前主机没有 `nvcc`，所以仍不
声明 CUDA 编译、GPU 执行或 GPU 性能。

## Claim boundary 与下一步

本轮真实测量了当前 OpenAL Soft device 的 EFX capability 和 Minecraft 内部的后端
转移 metadata。没有运行第二种无 EFX 实现，没有切换 Windows 物理设备，没有捕获
endpoint PCM，也没有听感校准。

D104 应把 timeline 用于“运行时连续性而非听感”的自动门禁：在 D102 的故障边界附近
用隔离软件 renderer 生成 dry/EFX→Java/Java→EFX 连续 PCM，验证 fallback 本身不会
引入 resolved click、长 dropout 或双尾声。它仍不能代替物理 endpoint capture，但能
先关闭软件切换边界的确定性风险。
