# D099 — 三环境后端传递矩阵

日期：2026-07-26  
状态：真实 Client GameTest、九条隔离软件渲染、独立校验通过

## 决策

D098 的双后端结论在三种 Minecraft 环境中保持成立：

- OpenAL EFX 可作为对中频 RT60 敏感、较快收敛到噪声底的原生简化；
- Java FDN 对三频带 RT60 的响应更强，尤其保留低频尾声，继续作为确定性回退和
  研究后端；
- 稳态单调性已经验证，但动态切换尚未测量。EFX 参数当前直接更新，Java FDN 使用
  0.2 秒平滑，因此下一步必须单独检查开门/关门瞬间，而不能从本次九条独立渲染
  外推“无 click”。

## 环境矩阵

Client GameTest 在相邻 stone / wool 房间之间构造 glass divider，依次采集：

| 环境 | 门洞格数 | low RT60 | mid RT60 | high RT60 | wet gain |
|---|---:|---:|---:|---:|---:|
| closed | 0 | 6.547526 | 3.919805 | 2.442873 | 0.388324 |
| partial | 4 | 3.389319 | 1.494898 | 0.832008 | 0.387386 |
| open | 12 | 2.181656 | 0.870613 | 0.471071 | 0.385751 |

三频带 RT60 都严格下降，而 wet gain 只下降约 0.66%。因此输出差异主要来自衰减
时间变化，不是简单降低 send gain。

每个环境都用同一对 D094 生产 motor/propeller PCM 分别渲染 dry、EFX 和 Java
FDN，共 9 个 4 秒输出。三份 dry PCM bit-identical；所有输出均在 48 samples
对齐输入。

## 结果

总 RMS：

| 后端/环境 | early tail | middle tail | late tail |
|---|---:|---:|---:|
| EFX closed | 30.001317 | 5.309092 | 0.636494 |
| EFX partial | 10.766427 | 0.516882 | 0.505346 |
| EFX open | 4.901520 | 0.501996 | 0.505264 |
| Java closed | 51.716722 | 15.368318 | 2.711357 |
| Java partial | 26.853951 | 2.874420 | 0.505264 |
| Java open | 18.513029 | 0.788300 | 0.505264 |

early tail 为输入结束后 1,024 samples 到 0.5 秒；middle 为 1.0–1.5 秒；
late 为 3.5–4.0 秒。约 0.505 RMS 是 OpenAL PCM16 dither floor。

两个后端的 early 和 middle 响应都随门洞增大而单调减小。EFX 在 partial 环境的
middle tail 已接近底噪，Java FDN 到 open 环境仍有 `0.841` RMS。这与模型结构
一致：EFX 只接收 mid decay 与 high-frequency ratio，Java FDN 独立接收仍为
`2.18 s` 的 open low RT60。

这不是 Java 更真实或 EFX 更好的听感结论。它只证明两条生产参数路径都响应真实
Minecraft 几何变化，且差异可由各自模型自由度解释。

## 证据

- matrix report SHA-256：
  `bbb99f20ede874e32319b584d78c48440a0c264bcb0e1d3d424b9e8c4bf762e4`
- 3,456,000-byte matrix PCM SHA-256：
  `3893404f9589683911ea2b60f74b9a2f93a91872c5d5b191c26568770579de29`
- independent verification SHA-256：
  `ed569ac97374374665c14724c13e406fb7c24e2e798bfb22b15e39406086ac55`
- bound Minecraft portal report SHA-256：
  `ce1e6dd91bb1505ef10d65af8ab45e630d8064720cb12fb27bca0c4df3bdacbf`

校验器重新检查 portal report 与 D094 trace 的哈希、全部 9 个连续 PCM range、
每段 PCM hash、输入 lag/correlation、三份 dry identity、三频带 RT60 顺序、
尾声存在/衰减和两后端稳态单调性。7 个负例覆盖控制参数脱链、PCM 篡改、range
重叠、环境乱序、缺失 Java tail 和 capture overclaim。

## 边界与下一步

本次是三条独立稳态，不是一次连续开门动作；`dynamic_transition_measured=false`。
没有打开物理播放或 capture device，也没有真实录音、ABX 或发布校准。

D100 应在同一个隔离 loopback context 中持续播放定常输入，在明确 sample boundary
执行 closed→partial→open→closed 参数更新：

1. Java FDN 验证 0.2 秒指数平滑与既有尾声不被清空；
2. EFX 测量直接写 effect 参数时的 boundary step/click；
3. 若 EFX 不连续，研究 control-rate slew 或双 effect-slot crossfade，不能用 Java
   FDN 的平滑结果替 EFX 背书；
4. 继续保持主 Minecraft context 不变，并明确这仍不是物理端点录音。
