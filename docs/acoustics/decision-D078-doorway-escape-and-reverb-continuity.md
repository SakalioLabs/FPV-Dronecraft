# D078 — 嵌套门洞的逃逸、衰减与 wet control 保持单调

日期：2026-07-25  
状态：**9-step doorway continuity passed；multi-room portal/RIR truth pending**

## 场景

固定 `11×11×5` stone room、4096 rays×48 bounces 和 D077 的 `2×MFP`
late-diffuse path threshold。在同一 z wall 上构造严格嵌套开口：

```text
0, 1, 2, 3, 9, 12, 20, 28, 55 cells
```

最后一步移除整面 `11×5` wall。缺失 wall cell 后射线继续一个 air cell并越出
`ReflectionVolume`，因此使用真实 DDA escape path，而不是人为设置 openness。

本场景不把 Eyring 当作 open-room truth，只验证端点、方向和连续性。

## 结果

| aperture | openness | mid RT60 | mid EDT | wet gain |
|---:|---:|---:|---:|---:|
| 0 cells | 0.0000 | 4.112 s | 3.690 s | 0.38919 |
| 3 cells | 0.1716 | 3.803 s | 3.418 s | 0.32152 |
| 9 cells | 0.5010 | 3.174 s | 2.855 s | 0.19274 |
| 20 cells | 0.8215 | 2.369 s | 2.125 s | 0.06829 |
| 55 cells | 0.9917 | 1.284 s | 1.166 s | 0.00311 |

全部九步满足：

- escaped rays 与 openness 严格递增；
- low/mid/high RT60 非递增；
- low/mid/high EDT 非递增；
- low/mid/high DRR 非递减；
- FDN wet gain 非递增；
- closed openness 恰为 0；
- full-wall openness `>=98%`；
- full-wall wet gain `<1%`。

近全开时 RT60 仍非零，因为少量射线在逃逸前会在其他五个表面产生多次命中。
`LateReverbEstimator` 描述这些 surviving reverberant paths；实际输出强度由
`FdnEnvironmentMapper` 的 `(1-openness)` 抑制。因此产品端不能只读取 RT60 判断
“室外是否有混响”。

## 证据

```powershell
.\gradlew.bat --no-daemon verifyDoorwayReverbContinuity
```

- deterministic doorway matrix：
  `1bb6ae99c39662f6031da0f964f51b47cd9c89277d4e1d0ac8ced77fb1b15cbf`；
- independent verification：
  `3a2ad76dee8ec93cf79373709dddbe1d12f36e342ecbaa5f9cc411104870ab33`。

verifier 固定几何、开口序列、4096×48、`2×4V/S`、343 m/s，并逐频带检查所有
单调关系。任何中间开口的 RT60 反转都会失败。

## 决策

1. openness、RT60/EDT/DRR 和 wet gain 必须作为一组环境参数使用；
2. D077 的 path-scaled direction policy 在单房间开放边界下没有产生反向跳变；
3. 下一步建立两个相连房间与 portal 尺寸/听者位置矩阵，检查能量是逃逸、传入邻室
   还是返回；
4. 接入 runtime 前，加入 20-tick 环境更新与 200 ms smoother 的动态 door
   open/close 测试；
5. 没有 open-room/doorway measured RIR，不能把本轮 RT60 当真实衰减时间。

## Claim boundary

本轮只验证静态嵌套 aperture 的单调性。它没有模拟门板运动、邻室回返、绕射或
portal transmission，也不改变 runtime 默认策略与 feature 开关。
