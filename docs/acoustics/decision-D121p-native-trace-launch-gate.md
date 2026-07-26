# D121p — native trace 的 fail-closed 启动门

## 结论

D121p 把 D121o 的“安全启动约定”变成生产初始化路径中的强制门。只有以下条件同时成立，
native callback trace 模式才进入 `ARMED`：

1. `fpvdrone.acoustics.nativeEventTrace=true`；
2. trace 实现确实启用；
3. 进程环境变量 `ALSOFT_DRIVERS` **精确等于** `null`；
4. local-plane research scheduler 未请求；
5. `DroneSoundManager` 被抑制；
6. 现有通用声学诊断命令被抑制。

任一条件不满足都会在 client mod 初始化时抛出异常，而不是回退到普通声音路径。trace
未请求时返回 `NORMAL`，原有初始化行为保持不变。

离线 truth table 与 independent verifier 状态为
`verified-minecraft-native-trace-launch-gate`：10 个 case 中 `1 NORMAL / 1 ARMED /
8 REJECTED`。缺失 driver、`Null`、`null,`、`wasapi`、scheduler 请求以及任一声音
writer 未抑制都不能 armed。

## 为什么要求精确的 `ALSOFT_DRIVERS=null`

OpenAL Soft 固定 commit
`a81b7e61ba30c8330fd0c1990c8008ca364ab072` 的官方配置样例说明：

- driver 列表未以逗号结尾时，只尝试列出的 backend；
- 以逗号结尾时，列出的 backend 失败后仍允许其他 backend；
- 空列表会尝试全部 backend。

因此 `null,` 不是安全等价物，`null,wasapi` 也不是；gate 不 trim、不忽略大小写，
只接受精确字符串 `null`。来源：
[OpenAL Soft configuration sample](https://raw.githubusercontent.com/kcat/openal-soft/a81b7e61ba30c8330fd0c1990c8008ca364ab072/alsoftrc.sample)。

同一 commit 的官方 null backend：

- 将 device name 固定为 `No Output`；
- mixer 调用 `renderSamples(nullptr, ...)`；
- `querySupport` 只支持 playback；
- capture enumeration 返回空；
- capture backend creation 返回空。

来源：
[OpenAL Soft null backend](https://raw.githubusercontent.com/kcat/openal-soft/a81b7e61ba30c8330fd0c1990c8008ca364ab072/alc/backends/null.cpp)。

这说明 null backend 的设计不会把样本发送给物理播放设备，也不提供 capture backend，
但 launch gate 只验证启动输入。真实 client 仍必须回读 `ALC_DEVICE_SPECIFIER` 为
`No Output`，才能证明 Minecraft 进程实际选择了该 backend。

## 初始化行为

`MinecraftNativeEventTraceDiagnosticMode.evaluate()` 在任何 Dronecraft 声音 manager
初始化前读取：

- D121o trace property；
- `System.getenv("ALSOFT_DRIVERS")`；
- local-plane scheduler property。

`FpvDronecraftClient` 的行为为：

```text
REJECTED → 中止 client mod 初始化
ARMED    → 注册 world trace，但跳过通用声学命令和 DroneSoundManager
NORMAL   → 保持原有命令与 DroneSoundManager 初始化
```

ARMED 模式因此不会构造 Dronecraft 的 direct propagation、Java FDN、OpenAL EFX、
audio lab、local-plane worker 或 drone streaming sound 管理链。Minecraft 自己仍可能
创建逻辑 OpenAL context；该 context 必须由 null backend 承载，不能把“跳过
DroneSoundManager”错误描述为“OpenAL 从未打开”。

## Truth table

冻结的 10 个输入覆盖：

| case | 结果 | 原因 |
|---|---|---|
| normal | NORMAL | trace 未请求 |
| armed | ARMED | 精确 null + 所有 writer 抑制 |
| implementation-disabled | REJECTED | trace 实现未启用 |
| drivers-missing | REJECTED | null backend 不独占 |
| drivers-fallback-list | REJECTED | `null,` 允许 fallback |
| drivers-physical | REJECTED | `wasapi` |
| drivers-wrong-case | REJECTED | `Null` 不精确 |
| local-plane-requested | REJECTED | scheduler 请求 |
| sound-manager-not-suppressed | REJECTED | production writer 可达 |
| commands-not-suppressed | REJECTED | audio-lab 命令可达 |

Independent verifier 逐项重算 case/state/reason、计数和唯一 armed 输入；同时扫描 Fabric
mode adapter 与 client initializer，要求 suppression `else` 分支位于命令和
`DroneSoundManager.initialize()` 之前，并固定两份官方来源的 commit URL。

## 可复现验证

```powershell
.\gradlew.bat --no-daemon verifyMinecraftNativeTraceLaunchGate
.\gradlew.bat --no-daemon `
  :computational-acoustics-core:test :fabric-mod:test
python -m pytest -q `
  docs\scripts\test_verify_minecraft_endpoint_disabled_acoustic_replay.py `
  docs\scripts\test_verify_minecraft_canonical_world_event_replay.py `
  docs\scripts\test_verify_minecraft_native_world_event_trace.py `
  docs\scripts\test_verify_minecraft_native_trace_launch_gate.py
```

最终回归：

- core：`237/237`，0 failure/error；
- Fabric：`469/469`，0 failure/error；
- D121m–D121p Python tests：`10/10`；
- named Minecraft 1.21.11 client compile：通过；
- `git diff --check`：通过。

冻结 SHA-256：

- contract：
  `2c350c92e56fcba0686417ecae7851a508ae32265fa2fa7e729ca15d78da14db`
- report：
  `56505b2f83aaa4ab13bb8f9e00c73e0947c33a8668aa2556d0ba603bc674c590`
- verifier：
  `e69f69f6023aaa7ec285f5729da168b21f6582e0ea29869ab806b65f602ae03c`
- verification：
  `65b832355b098757ad533f94142fab54b62dd75d57d3e9021651752f05a07ef8`
- mode adapter：
  `fddfad15a1df5393001725674cf23dbab40fa75d7e8034360bf677b11220a8e4`
- client initializer：
  `d627632d16597b8c546fc4f8640d201b52684f1986dc920a8dabb39accb18cbf`

## 边界

本轮保持：

- `minecraft_client_started=false`
- `alc_device_opened=false`
- `physical_endpoint_opened=false`
- `captures_audio=false`
- `native_callback_delivery_measured=false`
- `cuda_executed=false`
- `release_calibrated=false`

`ARMED` 只表示允许在后续获得用户授权后开始 runtime verification，不表示已经验证
null device、线程身份或 native callback delivery。

## 下一步

D121q 应在不引用 `DroneSoundManager` 的独立诊断类中实现：

1. 固定容量 trace 的只读 JSON 导出；
2. 当前 ALC context/device presence；
3. `ALC_DEVICE_SPECIFIER == "No Output"`；
4. playback/capture device enumeration 的只读摘要；
5. trace overwritten、线程集合、epoch/sequence；
6. 导出前再次运行 launch gate，非 ARMED 时拒绝。

这些接口可继续静态编译和离线验证，但真正调用 ALC、运行 client 或采集 callback 仍需
用户明确授权。
