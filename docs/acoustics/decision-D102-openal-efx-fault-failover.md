# D102 — OpenAL EFX 分阶段故障注入、Java 接管与 context 恢复

日期：2026-07-26  
状态：真实 Client GameTest 与独立 verifier 通过；物理设备/驱动故障未执行

## 目的

D101 定义了 EFX-first/Java-fallback 状态合同，但终止失败只由单元测试注入枚举状态。
D102 在 Minecraft 实际 sound thread、实际生产 controller 和真实 drone source 上制造
OpenAL error，验证故障检测之后的清理、接管、锁存和恢复顺序。

## 安全的测试注入边界

controller 内有三个一次性注入点：

| stage | 注入位置 |
|---|---|
| `resource-create` | shared effect/slot 创建后、资源错误检查前 |
| `parameter-write` | 生产 reverb 参数与 shared slot 写入后 |
| `source-route` | per-source filter 与 auxiliary send 路由后 |

注入调用一个无效 OpenAL 查询产生 context-local error，不修改其他有效 object。它只有在
`FabricLoader.isDevelopmentEnvironment()` 为 true 且显式设置测试属性时才工作；发布环境
无法启用。每次循环结束都会清除属性，报告与 verifier 都要求
`fault_property_cleared=true`。

## 真实恢复序列

每个阶段都执行：

1. 等待 EFX operational，确认 2 个生产 source 与 2 个 filter 已挂载；
2. 在 sound thread 注入一次 OpenAL error；
3. 生产错误检查进入 `CONTEXT_FAILED`；
4. effect、slot、filters 与 source sends 清理，attached/filter 数变为 0；
5. 下一 client tick 的实际 backend 变为 `JAVA_FDN`；
6. 连续至少 3 个 telemetry tick 保持 Java fallback，context rebuild、error 与 fault
   count 不变，证明同一失败 context 不重试；
7. 清除 fault 属性并调用真实 `Minecraft.SoundManager.reload()`；
8. 新声音引擎 generation 到达后重建 EFX，实际 backend 回到 `OPENAL_EFX`，Java
   stream 停止。

三次循环均得到 AL error `40963`，且 fault count 严格从 0 增加到 3：

| stage | rebuild before → recovered | cleanup before → failed | fallback | stable ticks | recovered |
|---|---:|---:|---|---:|---|
| resource-create | 3 → 4 | 1 → 2 | Java FDN | 3 | EFX |
| parameter-write | 4 → 5 | 2 → 3 | Java FDN | 3 | EFX |
| source-route | 5 → 6 | 3 → 4 | Java FDN | 3 | EFX |

所有 fallback 与 recovery snapshot 都满足：

- `double_wet_path=false`；
- fallback 时 Java active、EFX non-operational；
- recovery 时 Java inactive、EFX operational；
- `captures_audio=false`。

## 测试发现并修复的 context identity 缺陷

第一次真实运行在 recovery 阶段超时。故障清理与 Java 接管都成功，但 OpenAL allocator
在一次声音引擎 reload 后可能复用相同的 native context 地址。旧 controller 只比较
`alcGetCurrentContext()` 的数值，因此把一个新 context 错认成失败的旧 context，并永久
保持 fallback。

修复后：

- `SoundEngine.reload()` 的 mixin 只递增一个单调 generation；
- controller 在 sound thread 同时比较 native context handle 与 generation；
- 任一变化都会清除失败锁、丢弃旧 object names 并重建资源；
- 即使 pointer 地址复用，新的声音引擎生命周期也不会被旧失败状态污染。

这比测试专用 reset 更接近生产语义：reload generation 由真实声音引擎入口产生，正常
游戏同样受益。

## 证据

```powershell
.\gradlew.bat :fabric-mod:runClientGameTest
.\gradlew.bat verifyOpenAlEfxFaultFailover
```

- failover report SHA-256：  
  `d3c6a6d60467b1e2977e3c4e650040cb56aa181072577a05dc4b20b78f30ff99`
- independent verification SHA-256：  
  `a292e8f8126ca8f6e306c924832546d92c9dd4f03c7f15372ff7f5a65175b99b`

独立 verifier 重新验证三个阶段的顺序、一次性 fault count、错误非零、资源归零、
cleanup 递增、Java ownership、至少 3 tick 的同 context 稳定期、新 generation
恢复、telemetry/diagnostics 绑定、无双湿声与无录音边界。7 个负例覆盖缺失阶段、
资源泄漏、同 context 重试、双湿声、无 rebuild、fault 属性泄漏和物理故障 overclaim。

加入本轮 8 个 verifier 测试后，统一 Python regression 为 `295 tests passed`；
Java/core/mod 测试、CPU DDA reference 2/2 与静态 CUDA source contract 同时通过。
当前主机仍无 `nvcc`，因此没有 CUDA 编译、GPU 执行或 GPU 性能声明。

## Claim boundary 与下一步

本轮是真实 Minecraft sound thread 与真实 OpenAL Soft controller 生命周期，但错误由
开发模式中的确定性无效调用产生。它不是：

- USB/声卡断开；
- Windows 默认输出设备切换；
- OpenAL driver 崩溃；
- 没有 `ALC_EXT_EFX` 的另一实现；
- callback underrun；
- 可听 dropout/click；
- 物理录音或 release calibration。

D103 应研究并实现“能力缺失而非 AL error”的真实 fallback 证据：用隔离 loopback
capability probe 验证无 EFX 路由决策，或在可用的第二 OpenAL 实现/设备上运行。如果
环境无法提供无 EFX device，则应保留为 capability-policy 仿真，不能冒充硬件实测。
同时可为后端切换增加元数据 timeline 导出，供未来获得授权的 endpoint capture 做
样本级对齐。
