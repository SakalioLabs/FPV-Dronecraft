# D079 — 邻室回返与 FDN 动态门过渡通过确定性诊断

日期：2026-07-25  
状态：**two-room portal transport 与 parameter-domain smoothing passed；measured coupled RIR、移动几何和 OpenAL scheduling pending**

## 1. 问题拆分

D078 只证明单个房间向外界开口时，escape、衰减指标和 wet control 随开口面积单调。
门洞也可能连接另一个封闭空间，此时穿过门洞的能量不应被误算成室外逃逸，而且射线
应能进入邻室并返回。动态开关门还必须避免把每 20 tick 更新一次的环境参数直接变成
听得见的阶跃。

本轮将这两个问题分开：

1. 静态 two-room portal 检查空间传输、邻室命中和回返；
2. 真实 `ListenerSharedFdn` 检查环境参数由 closed 到 open、再由 open 到 closed
   的 200 ms 指数平滑。

## 2. Two-room portal 场景

两个内部尺寸均为 `9×9×5` cells 的封闭房间共用一面 glass 隔墙。听者位于 stone
房间 A，房间 B 使用 soft 表面，以便在确定性报告中追踪跨房间命中。隔墙上依次打开
`0, 1, 2, 3, 9, 15, 45` cells 的严格嵌套门洞；每步运行 `4096 rays × 48
bounces`，late diffuse 切换阈值为 `2×effective MFP`。

| aperture | B hit fraction | rays entering B | rays returning A | mid RT60 | mid EDT | wet |
|---:|---:|---:|---:|---:|---:|---:|
| 0 | 0.0000 | 0 | 0 | 3.438 s | 3.017 s | 0.38850 |
| 1 | 0.0321 | 306 | 22 | 2.395 s | 2.929 s | 0.38838 |
| 3 | 0.0978 | 940 | 131 | 1.485 s | 2.670 s | 0.38783 |
| 9 | 0.2459 | 2533 | 1050 | 0.814 s | 2.200 s | 0.38657 |
| 15 | 0.3296 | 3354 | 2108 | 0.658 s | 1.873 s | 0.38543 |
| 45 | 0.4482 | 4086 | 4012 | 0.570 s | 1.191 s | 0.38077 |

全部 aperture 中 `escaped_rays=0` 且 `openness=0`。这是需要的语义：门洞通向封闭
邻室，不是 snapshot 外部。B hit fraction、进入 B 的射线数和返回 A 的射线数随
门洞非递减；三带 RT60/EDT 与 wet gain 非递增。满开时 `4086/4096` 射线进入 B，
其中 `4012/4096` 后续重新命中 A。

wet 仅由 `0.38850` 降到 `0.38077`，远小于 D078 向室外开口的变化。这不是传输
缺失：当前 mapper 的 wet control 主要读取 openness 和第一反射，而第一反射仍多在
本地房间。邻室的强吸收更明显地进入多次反射衰减、RT60 和 EDT。因此产品实现不能
只靠 wet 标量表达 coupled-room late field；后续需要 matched coupled-room RIR
决定是否扩充 mapper 状态。

## 3. 动态 FDN 参数过渡

测试直接运行 `ListenerSharedFdn`，用 D078 的 closed/full-open 报告作为目标端点，
采样率 48 kHz、时间常数 200 ms。检查点为 `0, 1, 2400, 4800, 9600, 19200,
48000` samples，并同时观察 wet gain 和三带 feedback gain 的均值。

- 单样本 opening/closing progress 均为 `0.0001041612`，即约 `0.0104%`；
- 200 ms progress 均为 `0.6321205588`，与 `1-e^-1` 一致；
- 1 s residual 均为 `0.0067379470`，低于 `0.7%`；
- opening 时 wet 与三带 feedback 均非递增，closing 时均非递减；
- 两个目标端点与 D078 报告绑定。

这证明 FDN 的参数域不会把低频率的环境 snapshot 更新直接变成一步跳变。它没有证明
Minecraft 门板几何在每次 snapshot 捕获时连续，也没有测量 OpenAL buffer 调度、
设备 callback 或真实音频输出中的 click/underrun。

## 4. 可复现证据

```powershell
.\gradlew.bat --no-daemon verifyTwoRoomPortalCoupling verifyFdnDoorTransition
```

- portal matrix SHA-256：  
  `475feab8a3b9d1d2bf4f270f8b2d587b471b1dd8eb464179bf9c2bb3ff28379f`
- portal verification SHA-256：  
  `64724378db143afc80ac30285e7c8299b1f84a4a653941ad9935d12831df7900`
- FDN transition matrix SHA-256：  
  `b9eb2b8ad5fd38c1878efad5c4d207962bb6aed5d01cb3bd2ffaae184c6d9b18`
- FDN transition verification SHA-256：  
  `889764e8ff13d9c2a62050ed0478fcb94b1bf10b414a11227fc48147e0695a97`

verifier 固定几何、材料标签、射线预算、MFP 规则、时间常数、采样率、检查点和全部
单调/端点门禁。报告均保留 `release_calibrated=false`。

## 5. 决策与下一方向

1. D078 的“向外逃逸”和 D079 的“向邻室传输”必须继续使用不同语义；
2. 200 ms FDN parameter smoothing 可保留为 runtime 候选，不需要额外的门状态专用
   插值器；
3. 下一步在 Minecraft Client GameTest 中构造真实 block-state 双房间/门洞，
   连续采集 closed→open→closed snapshots，验证 material mapping、20-tick 更新和
   FDN 参数端到端绑定；
4. matched coupled-room RIR 可用前，不根据本轮结果调高或发布 wet mapping；
5. runtime reverb feature 继续默认关闭。

## Claim boundary

房间 A/B 的识别依赖此测试专用的 stone/soft material tags，只在已构造几何中代表
room membership。没有 measured coupled-room RIR、真实移动门板、绕射、portal
透射损耗或 OpenAL 设备端测量；因此本轮是 transport/control diagnostic，不是发布
校准。
