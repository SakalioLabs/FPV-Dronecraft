# D098 — OpenAL EFX 与 Java FDN 受控传递对照

日期：2026-07-26  
状态：真实 Client GameTest、隔离软件渲染、独立校验通过

## 决策

支持 `ALC_EXT_EFX` 时，OpenAL EFX 保留为低成本的原生晚期混响简化路径；
项目的 listener-shared Java FDN 保留为无 EFX 时的确定性回退，以及需要三频带
RT60 控制时的研究路径。两者当前不宣称听感等价，也不以本次结果完成发布校准。

原因是两条路径的模型能力不同：

- EFX 生产映射只有一个中频 `AL_REVERB_DECAY_TIME`，再用
  `sqrt(high_rt60 / mid_rt60)` 映射 `GAINHF` 与 send low-pass。它不能独立表达低频
  RT60，但能复用 OpenAL 的 listener-shared effect slot 与每声源 send filter。
- Java `ListenerSharedFdn` 直接接收 low/mid/high 三个 RT60，以 8 条延迟线生成独立
  wet bus，频带表达能力更强，但混响计算由项目承担。

因此“原生简化”与“频带可控回退”是互补关系，不应通过强行调参把两条输出伪装成
同一个算法。

## 实验输入与环境

实验复用 D094 最早的真实生产 motor/propeller PCM，各 48 kHz mono PCM16、
1 秒。输入 sequence 与 PCM SHA-256 均重新绑定到 D094 report 和 sidecar。

混响环境不是手填参数。Client GameTest 在 Minecraft 集成世界构造封闭石质房间，
运行生产 `MinecraftReflectionSnapshot`、`VoxelReflectionProbe`、
`LateReverbEstimator` 和 `FdnEnvironmentMapper`，得到：

| 参数 | 数值 |
|---|---:|
| snapshot generation | 166 |
| low RT60 | 6.547525842 s |
| mid RT60 | 3.919804879 s |
| high RT60 | 2.442873467 s |
| wet gain | 0.388323554 |
| transition | 0.2 s |

此前在 D094 开放监听点观测到 RT60 非零但 wet gain 为 0；该控制正确表示没有可用
晚期反射注入，因此被明确拒绝作为后端对照环境。D098 改用上述生产映射的封闭房间
快照，未人为指定 wet gain。

## 渲染方法

三个输出均为 4 秒、192,000 frames：

1. dry：两个生产 source，无混响；
2. EFX：两个相同 source，调用生产 `OpenAlEfxController` 的参数配置函数；
3. Java FDN：使用生产 `ListenerSharedFdn` 生成 4 秒 wet bus，再与相同两个 dry
   source 一起交给 OpenAL mixer。

每个输出使用独立 `ALC_SOFT_loopback` device/context，并通过
`ALC_EXT_thread_local_context` 限定在同一后台线程。实验前后 Minecraft
context、device 和 `Sound engine` thread identity 均不变；没有打开 capture
device 或物理播放端点。

## 结果

三条输出都在 48 samples 的 renderer lag 对齐输入：

| 路径 | 输入相关系数 | early tail RMS | late tail RMS |
|---|---:|---:|---:|
| dry | 0.999999234 | 0.502821 | 0.505264 |
| OpenAL EFX | 0.981917654 | 30.001317 | 0.636494 |
| Java FDN | 0.958047125 | 51.716722 | 2.711357 |

early tail 从输入结束后 1,024 samples 开始，到 0.5 秒结束，排除了已独立测得的
OpenAL mixer lag；late tail 为 3.5–4.0 秒。dry 的约 0.5 LSB RMS 是 PCM16
dither floor。两条混响路径的 early tail 都远高于它，且 late RMS 严格下降。

early tail 三频带 RMS：

| 路径 | low 40–700 Hz | mid 700–4000 Hz | high 4–20 kHz |
|---|---:|---:|---:|
| OpenAL EFX | 24.433581 | 15.559543 | 4.623049 |
| Java FDN | 41.548808 | 24.683510 | 11.554909 |

这说明在相同输入与控制下两种模型确实生成不同传递函数：Java FDN 在本次长低频
RT60 房间中保留更强、更长的尾声；EFX 尾声更快接近量化底噪。它不是“谁更真实”
的听觉结论，仍需 D085–D089 的授权录音与盲听流程。

## 证据

- report SHA-256：
  `dd3b8558905906b8c593e4806a7802a29d7715f1fb7676914ec44f4afe216d0e`
- dry PCM SHA-256：
  `42e28b76023ad2c7376cad33c23502e8ab87f4317d230cc7c8396aaa33d74470`
- EFX PCM SHA-256：
  `9169b3063c37c8c74c42611e1056c81fb5bc18aa52ec8a5d61f13399d062447b`
- Java FDN PCM SHA-256：
  `b3335df6fc553a4bba76e1e005e6ff2b3c671a37827f9d965241aa81debaadf1`
- independent verification SHA-256：
  `cbdeb31d26265858a800e3eacccbb614d4eca4e849ef94959fe35f4557fd49de`

校验器会拒绝输入脱链、sidecar 篡改、三输出相同、缺失或不衰减的混响尾声、
context ownership 变化，以及录音/校准越界声明。

## 边界与下一步

本实验是隔离软件 renderer readback，不是 Minecraft 主 context 的最终输出、
声卡 endpoint loopback、扬声器—空气—麦克风链路或听感校准。

下一步 D099 应把封闭房间、开放环境和门洞过渡至少三个生产环境送入同一对照，
验证环境变化在 EFX 与 Java FDN 中都保持单调且无参数跳变；随后才能选取 D088
录音矩阵中的调参候选。CUDA voxel DDA 仍是传播几何加速研究，不应进入音频线程，
也不替代本次 listener-shared late-reverb 后端。
