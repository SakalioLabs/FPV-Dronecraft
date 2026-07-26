# D121q — native trace 与 ALC 的只读证据导出

## 结论

D121q 已实现授权运行后所需的只读证据导出链，但本轮没有调用真实 ALC 或启动
Minecraft。链路为：

```text
ARMED launch gate
  → Minecraft SoundEngineExecutor 上只读 ALC snapshot
  → 普通 CompletableFuture worker 上复制 256-slot trace
  → 核心 exporter 重新验证
  → CREATE_NEW JSON
  → Minecraft client executor 返回命令结果
```

离线 reference 与 independent verifier 状态为
`verified-minecraft-native-trace-alc-evidence`。reference 使用明确标记的假 ALC
snapshot 和 4 条 trace，验证 JSON schema；五个负控分别为 unarmed、物理 device
name、capture device 存在、inactive context 和非单调 trace，全部在创建输出文件前
拒绝。

## ALC 只读探针

`MinecraftNativeEventTraceEvidence` 不引用生产 drone audio manager，只通过项目已有的
Minecraft mixin accessor 取得 `SoundEngineExecutor`，并在声音线程调用：

- `alcGetCurrentContext()`；
- `alcGetContextsDevice(context)`；
- `alcGetString(device, ALC_DEVICE_SPECIFIER)`；
- `alcGetString(0, ALC_CAPTURE_DEVICE_SPECIFIER)`。

它不调用 `alcOpenDevice`、`alcCaptureOpenDevice`、context create/destroy、source、
buffer、EFX 或 capture API。探针只返回：

```text
activeContext
activeDevice
deviceName
captureDeviceSpecifier
```

在 null backend 下 capture device list 应为空；如果 LWJGL 返回任何非空 capture
specifier，exporter 立即拒绝，不尝试打开该设备。

## 再验证与写文件顺序

核心 `NativeEventTraceEvidenceExporter` 不信任 Fabric 调用方。它在任何目录或文件创建
前重新要求：

1. launch decision 为 `ARMED`；
2. context/device 均 active；
3. device name 精确为 `No Output`；
4. capture device specifier 为空；
5. retained count 不超过调用方数组；
6. overwritten 非负；
7. 所有非空 trace entry ordinal 严格递增。

全部通过后才创建父目录，并使用 `CREATE_NEW` 写文件；已有同名文件不会被覆盖。

报告明确区分：

- `alc_device_opened`：逻辑 ALC device；
- `physical_endpoint_opened`：物理播放 endpoint；
- `captures_audio`：录音/capture；
- `reference_fixture`：是否只是离线假快照。

离线 reference 固定为 `reference_fixture=true`、
`minecraft_client_started=false`、`alc_read_performed=false`、
`alc_device_opened=false`。授权后的真实命令才会把前三项切换为 runtime 语义。

## Trace 一致性

导出 worker 按 trace capacity（256）而不是瞬时 size 创建 `Entry[]`，避免 size 读取与
后续 callback 之间的竞态导致输出数组不足。`copyChronological` 自身同步，返回实际
copied count；exporter 只序列化该 count，并同时写入 overwritten。

reference 保存：

- retained `4`；
- overwritten `2`；
- ordinals `1,2,3,4`；
- thread id 全部为 `41`；
- world replace、coverage 内/外 block 与 chunk unload。

这些是假 trace DTO，用来验证字段和拒绝条件，不能解释为真实 callback。

## 专用命令

只有 D121p 的 `traceMode.armed()` 分支注册
`fpvdrone-native-acoustic-trace export`。普通通用声学命令仍只在 NORMAL 分支注册。
专用命令：

- 不引用 `DroneSoundManager`；
- 将 ALC 查询调度到 sound executor；
- 将 JSON 创建移出 sound thread；
- 将成功/失败反馈调度回 client executor。

## 可复现验证

```powershell
.\gradlew.bat --no-daemon verifyMinecraftNativeTraceAlcEvidence
.\gradlew.bat --no-daemon `
  :computational-acoustics-core:test :fabric-mod:test
python -m pytest -q `
  docs\scripts\test_verify_minecraft_endpoint_disabled_acoustic_replay.py `
  docs\scripts\test_verify_minecraft_canonical_world_event_replay.py `
  docs\scripts\test_verify_minecraft_native_world_event_trace.py `
  docs\scripts\test_verify_minecraft_native_trace_launch_gate.py `
  docs\scripts\test_verify_minecraft_native_trace_alc_evidence.py
```

最终回归：

- core：`239/239`，0 failure/error；
- Fabric：`471/471`，0 failure/error；
- D121m–D121q Python tests：`13/13`；
- named Minecraft 1.21.11 client compile：通过；
- `git diff --check`：通过。

冻结 SHA-256：

- contract：
  `d4d3d9551f8f00a1008047637eb5fbbf57d04004f4c6e925870c78d8dbe46dd9`
- report：
  `b35edd1ce7f741a56b336a7b3b803234b2a92e765045059bc23f51d36399fcfa`
- verifier：
  `4e351ea52083098d5de797b5416846c60af14f3c509f1c3b87deb8d72b542604`
- verification：
  `cdffb6afdb8a27977b3a1024fc76e494513651aed68715b5f460580f778b78c7`
- core exporter：
  `6564a2f59354ffab193f14e96dd738bfa57a60b70d1c7e73618b1c55fbd3ffa0`
- Minecraft ALC probe：
  `c32a018a6a486879a06a9b002f473621731bcfc9fed1ec040f31754c55d9a485`
- dedicated command：
  `f1eaf2c38b3b09bc5228d937f6fecdea43ad9e1dc7f54c8a550f44c0f2e095db`
- client initializer：
  `6570fbd477db1194196ec28d0721c010d09938d358fd11ceac212e0e97ade520`

## 边界

本轮仍为：

- `minecraft_client_started=false`
- `alc_read_performed=false`
- `physical_endpoint_opened=false`
- `captures_audio=false`
- `native_callback_delivery_measured=false`
- `cuda_executed=false`
- `release_calibrated=false`

因此 reference 中的 `No Output` 是测试输入，不是真实设备回读。

## 下一步

D121r 已没有必要再增加语义假回放。下一项有判别力的证据必须来自用户明确授权的
Minecraft client 运行：

1. 进程启动前设置 `ALSOFT_DRIVERS=null`；
2. 只设置 `fpvdrone.acoustics.nativeEventTrace=true`，保持 local-plane property
   缺失；
3. 确认 client 初始化日志进入 ARMED；
4. 执行有限 block/chunk/dimension 事件；
5. 运行专用 export 命令；
6. 在进程外验证 `device_name=No Output`、capture 为空、trace thread/epoch/sequence；
7. 退出 client 后确认没有物理播放或 capture 证据。

在获得授权前不能执行这些步骤，也不能把 D121q reference 声称为 runtime evidence。
