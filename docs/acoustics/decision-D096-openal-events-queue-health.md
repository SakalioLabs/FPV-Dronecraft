# D096 — `AL_SOFT_events` 有界 queue-health 事件链通过

日期：2026-07-26  
状态：**真实 Minecraft/OpenAL callback、静音正控、两条生产 source、8 次
buffer completion→refill 覆盖、清理与独立 verifier passed；underrun/物理输出
仍未证明**

## 目的

D095 在 8 个离散 post-refill 点证明生产 PCM 已进入 OpenAL，source 都是
`PLAYING + STREAMING` 且 queue depth 为 4。但离散查询看不到两个 refill 之间的
事件，也不能验证当前运行时的 `AL_SOFT_events` 是否真能安全接入 Minecraft
context。

D096 的目标是：

1. 不覆盖任何已有 context callback；
2. 在有界窗口内接收真实 buffer-completed/source-state events；
3. 用静音正控证明两种 event type 确实会送达；
4. 把生产 completion 与 D095 的每次 refill 交叉绑定；
5. 在 sound reload 前完整注销并证明没有残留；
6. 明确拒绝把该扩展冒充 underrun counter。

## 扩展的实际语义

OpenAL Soft 的
[`AL_SOFT_events`](https://openal-soft.org/openal-extensions/SOFT_events.txt)
只定义：

- `AL_EVENT_TYPE_BUFFER_COMPLETED_SOFT`
- `AL_EVENT_TYPE_SOURCE_STATE_CHANGED_SOFT`
- `AL_EVENT_TYPE_DISCONNECTED_SOFT`

其中 buffer-completed 的 parameter 是本次完成处理的 queue entries 数；source
state event 的 parameter 是新状态。规范没有定义 `UNDERRUN` event，也没有
underrun counter。callback 不能调用 AL functions；每个 context 只有一个 callback，
默认 event types 全部关闭。

所以本轮能证明“callback 报告了哪些事件”，不能由“没有 `AL_STOPPED`”推出物理
输出绝对连续。

## 所有权与 fail-closed 生命周期

`OpenAlEventQueueHealthProbe` 只由 Client GameTest 显式启动，生产默认路径不注册
callback。所有 AL lifecycle 操作都由 Minecraft `SoundEngineExecutor.schedule`
放到 `Sound engine` 线程：

1. 确认 active context 与 `AL_SOFT_events`；
2. 用 `alGetPointerSOFT` 读取 callback 与 user pointer；
3. 任一 pointer 非零时拒绝启动，不覆盖未知 owner；
4. 创建 LWJGL-owned callback，注册后重新查询 pointer，必须等于 owned address；
5. 只启用 buffer-completed 与 source-state event；
6. callback 内不做任何 AL query，只写入最多 256 个固定字段事件；
7. 停止时先 disable event types，再把 callback/user pointer 清零；
8. 删除静音正控 source/buffer，读取 AL error；
9. 释放 LWJGL callback trampoline；
10. 额外观察至少 150 ms，要求 event count 不再变化。

失败路径会异步执行同样的回滚；不会把 callback 留给后续 sound reload。

## 20 ms 全静音正控

为避免仅凭“扩展 advertised”声称 callback 可用，探针创建一个隔离的诊断 streaming
source：

- 48,000 Hz；
- mono PCM16；
- 960 samples / 20 ms；
- 所有 samples 严格为零；
- 一个 queued buffer。

本次 source id `3`、buffer id `9`，与生产 motor/propeller source `1/2` 及 D095
的 8 个 buffers 都不同。它真实产生：

- 1 个 buffer-completed count；
- 1 个 `AL_STOPPED` state event。

因此 event delivery 有正控，同时不向输出加入非零波形。

## 真实生产事件

| 字段 | 结果 |
|---|---:|
| callback observation | 3.3319009 s |
| callback events | 16 |
| callback threads | 1 (`Thread-13`) |
| bounded-probe dropped events | 0 |
| motor buffer-completed count | 4 |
| propeller buffer-completed count | 4 |
| production `AL_STOPPED` events | 0 |
| silent-control buffer completed / stopped | 1 / 1 |
| quiet window after cleanup | 248.8109 ms |
| events at cleanup / after quiet | 16 / 16 |
| registration / cleanup AL error | 0 / 0 |

其余 6 个 events 是 Minecraft 测试期间其他短 source 的 `AL_PLAYING` state
transition；verifier 允许非生产 source-state events，但拒绝未知 source 的
buffer-completed event。

D095 现在真实查询 callback pointer，不再把字段写死。本次 8/8 refill 点均记录
`event_callback_registered=true`。独立 verifier 对每条生产 source：

- 从 D095 固定唯一 source id；
- 展开 buffer-completed event 的 count；
- 按 host monotonic timestamp 要求第 `n` 次 refill 前累计至少已有 `n` 次
  completion；
- 要求所有 8 次 refill 均位于 callback registration/cleanup window；
- 要求 motor/propeller 都没有 callback-reported `AL_STOPPED`。

这证明 callback completion 与本轮被观察的 refill 有因果顺序覆盖，但不证明
OpenAL mixer、驱动或物理设备从未出现不可见的中断。

## 独立验证与负向测试

`verify_openal_events_queue_health.py` 重新验证：

1. D096 report 固定 D095 queue report SHA-256；
2. D095 每个 refill 都真实看到 callback pointer；
3. registration 前 callback/user pointer 都是零；
4. 静音正控格式、独立 ids、completion 与 stopped positive control；
5. event type/code、sequence、timestamp、callback thread、user parameter；
6. 两条生产 source 的 completion/refill 累计覆盖；
7. bounded queue 没有丢事件；
8. callback/user pointer 清零、正控资源删除、quiet window 无新增事件；
9. fail closed 地拒绝 underrun、playback capture、录音和 release overclaim。

14 个 Python tests 覆盖成功路径、context-global 瞬态 source、queue hash、已有 callback、非静音正控、source
脱链、refill 早于 completion、production stop、丢事件、callback pointer 残留、
underrun overclaim、缺失正控 stop 与 refill 未看到 callback pointer。

运行：

```powershell
.\gradlew.bat :fabric-mod:runClientGameTest --rerun-tasks
.\gradlew.bat verifyOpenAlStreamingQueue `
  verifyOpenAlEventsQueueHealth --rerun-tasks
python -m unittest discover -s docs/scripts `
  -p test_verify_openal_events_queue_health.py -v
```

证据哈希：

- D095 live queue report SHA-256：  
  `909357c882c4889f3b75fba08bf7117d2f7a640631ef5ac1c84a488dc71d9c9e`
- D096 live events report SHA-256：  
  `ef00dd31161841cef3eb40762b23570d6fc6dfa5aae6b4284fc1c17ad61978c9`
- D096 independent verification SHA-256：  
  `45a4669298210ecdc3f34761aa63afaa7bd436987f6b1ea22c4e24023202e1c7`

## 决策

1. `AL_SOFT_events` 可用于默认关闭的有界诊断：当前实现真实送达生产
   buffer-completed 与 source-state events。
2. 不把它永久安装为 Dronecraft runtime callback。它是 context-global singleton，
   Minecraft、其他 mod 或未来运行时一旦已有 owner，探针必须让路。
3. 正式 runtime 继续复用 Minecraft 一秒 × 4-buffer scheduler；D095 的 post-refill
   checks 与 D096 的 callback timeline 作为测试证据，而不是新的播放调度器。
4. “本窗口无 callback-reported production stop”是允许的结论；“连续无 underrun”
   不是。
5. 下一官方原生研究方向优先评估隔离的 `ALC_SOFT_loopback` context，判断能否在不
   录麦克风、不替换 Minecraft live device 的情况下取得 OpenAL rendered-buffer
   参考。物理连续性与可听结论仍必须等待 endpoint 确认和明确录音授权。

## Claim boundary

D096 证明当前 OpenAL context 的 callback ownership 可以在零冲突前提下有界取得、
两类事件有静音正控、两条生产 streaming source 的 8 次 completion 覆盖 D095 的
8 次 refill，并在 reload 前恢复零 callback/user pointer、删除正控资源。它不定义
或计数 underrun，不捕获 OpenAL rendered samples，不证明驱动/声卡/扬声器输出，
也没有录音、loopback、听测或 release calibration。
