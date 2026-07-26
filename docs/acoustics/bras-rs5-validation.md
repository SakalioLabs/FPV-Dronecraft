# BRAS RS5 实测绕射基线

更新日期：2026-07-24

## 结论

BRAS RS5 可以作为当前 UDFA 实现的首个真实采集数据基线，但不能把整段 RIR
直接当作纯绕射传递函数。使用同距离的可见直达 `LS3-MP4` 作为参考、被隔板遮挡的
`LS1-MP1` 作为测试路径，并截取首次到达后的 1 ms，可得到：

| 频率 | 实测 | 单 `2π` edge | 单边误差 | 2024 双 `3π/2` edge | 双边误差 |
|---:|---:|---:|---:|---:|---:|
| 1 kHz | -19.377 dB | -11.093 dB | +8.284 dB | -20.409 dB | -1.031 dB |
| 2 kHz | -18.387 dB | -14.070 dB | +4.317 dB | -25.783 dB | -7.395 dB |
| 4 kHz | -23.859 dB | -17.071 dB | +6.787 dB | -31.622 dB | -7.763 dB |
| 8 kHz | -25.245 dB | -20.078 dB | +5.166 dB | -37.597 dB | -12.353 dB |
| 12 kHz | -24.981 dB | -21.838 dB | +3.143 dB | -41.110 dB | -16.129 dB |

五点 RMS 误差为 **5.829 dB**。正误差表示模型预测的阴影声过响。这个结果否决
“把单个零厚度 knife-edge 和论文默认参数直接接入产品”，但不否决 UDFA：

- 实测 MDF 隔板厚 25 mm，不是零厚度屏；
- 声音需要绕过相隔 25 mm 的两个 `3π/2` 顶边，可能存在相干级联/替代路径；
- 1 ms 门控分辨率有限，4 ms 门控已经含地板和支架的早期能量；
- 可见参考和阴影测量使用同型号音箱、麦克风及相同水平距离，但高度不同，仍有
  未消除的设备方向性和房间差异。

因此此基线是 **candidate golden**：可防止实现漂移，并指导下一模型实验；它还不是
产品验收 golden。

2024 higher-order UDFA Eq. (8)-(12) 的双边模型未使用拟合参数，五点 RMS 为
**10.284 dB**。它在 1 kHz 更接近实测，但 2 kHz 以上明显过度衰减。该实现通过
互易性、零宽度收敛到单 knife edge、DC 和高频 `-6 dB/oct` 渐近测试，所以当前
不能用“加一次绕射”解释单边模型的误差，也不能针对 LS1-MP1 单点调参。优先排查
短门控、扬声器/麦克风响应及 BTMS reference 与真实隔板测量的可比性。

## BTMS 复核后的解释

精确无限楔 BTMS reference 已完成，详见
[`btms-reference.md`](btms-reference.md)。在与该 BRAS 路径相同的理想
`2π` knife-edge 几何上：

| 频率 | 实测 1 ms gate | 精确 BTMS | 2023 UDFA |
|---:|---:|---:|---:|
| 1 kHz | -19.377 dB | -11.287 dB | -11.093 dB |
| 2 kHz | -18.387 dB | -14.128 dB | -14.070 dB |
| 4 kHz | -23.859 dB | -17.084 dB | -17.071 dB |
| 8 kHz | -25.245 dB | -20.080 dB | -20.078 dB |
| 12 kHz | -24.981 dB | -21.838 dB | -21.838 dB |

UDFA 相对精确 BTMS 的五点 RMS 只有 `0.0905 dB`。这项结果否定了
“5.829 dB 实测差异主要来自 UDFA 对无限楔近似不准”的假设。candidate golden
仍然可以发现端到端处理漂移，但 single/double-edge 产品决策必须等待
deconvolution-aware 门控、设备方向性校准以及真实 25 mm 屏 reference。

后续测量链审计已经完成，详见
[`bras-measurement-chain.md`](bras-measurement-chain.md)。官方 Genelec 8020c
MPS 校正将 single-edge 五点 RMS 从 `5.829 dB` 降到 `5.077 dB`，但五条
direct control 在 1 ms gate 上除 4 kHz 外仍未通过稳定性门槛。因此全 16 路径
短门频谱保留为 diagnostic，不升级为模型选择 golden。

## 数据来源、许可与完整性

- 数据集：TU Berlin, *BRAS - Benchmark for Room Acoustical Simulation*，
  Reference Scene 5 “diffraction (infinite wedge)”；
- 最新数据页：<https://depositonce.tu-berlin.de/items/38410727-febb-4769-8002-9c710ba393c4>；
- 数据集许可：CC BY-SA 4.0；
- 文件：`1_scene_descriptions-RS5.zip`；
- 大小：22,138,580 bytes；
- SHA-256：
  `b9cb03c945fcf46bf742135108d1acc5246b576e8afb39090547e0f3093f58b5`。

仓库不保存原始 ZIP、WAV、SOFA 或图片；`build/research-bras/` 已由现有
`build/` ignore 规则排除。脚本只输出可复算的标量指标。

下载地址：

```text
https://api-depositonce.tu-berlin.de/server/api/core/bitstreams/ccce535a-c508-4046-8748-4458b8e73d13/content
```

## 几何和时延核验

坐标来自数据集 `Geometry/RS5_RIR.png`，单位由 mm 转为 m：

| 对象 | 坐标 `(x,y,z)` m |
|---|---|
| LS3，可见参考声源 | `(2.487, 2.985, 3.000)` |
| MP4，可见参考接收点 | `(8.512, 2.985, 3.000)` |
| LS1，阴影声源 | `(2.487, 2.985, 1.235)` |
| MP1，阴影接收点 | `(8.512, 2.985, 1.235)` |
| 隔板前侧顶边点 | `(5.487, 2.985, 2.066)` |

可见直达距离为 `6.025 m`，阴影路径经顶边为 `6.250033 m`。取声速
`343 m/s`，几何预测额外时延 `0.656073 ms`。RIR 首次到达相差 31 个
44.1 kHz 采样，即 `0.702948 ms`；误差 `0.046875 ms`，约 2.07 个采样。
这项独立核验说明文件配对、坐标和首次到达检测没有发生一个路径量级的错位。

## 可复现方法

脚本 [analyze_bras_rs5.py](../scripts/analyze_bras_rs5.py) 仅依赖 Python
标准库，执行以下步骤：

1. 可选校验原始 ZIP 的 SHA-256；
2. 自行解析 mono `PCM_F32LE` RIFF/WAV，不依赖 SOFA/HDF5；
3. 以 8-sample RMS detector 找首次到达，阈值取预噪声 RMS 的 12 倍与峰值
   `1e-4` 中的较大值；
4. 对首次到达使用保留前沿、末 25% cosine fade 的 1 ms 和 4 ms 门；
5. 计算阴影/可见 DFT 比值，再补偿 `6.250033/6.025` 的自由场距离差；
6. 用独立的 Python 复写计算 2023 UDFA Eq. (2)-(6)，并由 Java 单元测试
   `BrasRs5UdfaReferenceTest` 逐点交叉核验；
7. 依据 Kirsch and Ewert 2024 Eq. (8)-(12) 独立复写 modified exterior angle、
   two-term/single-term cascade 和 reciprocal mixing，并与 Java
   `UdfaDoubleEdgeFilterTest` 逐点交叉核验。

示例：

```powershell
python docs/scripts/analyze_bras_rs5.py `
  "build/research-bras/rs5-extracted/1 Scene descriptions/RS5 diffraction (infinite wedge)" `
  --archive build/research-bras/rs5.zip
```

4 ms sensitivity gate 在 1/2/4/8/12 kHz 得到
`-17.442/-21.565/-23.591/-26.438/-25.290 dB`。它与 1 ms 结果的差异证实
门外早期能量不可忽略，所以不能使用整段 RIR 评价纯 edge filter。

## 全部 16 条路径的到达时延

脚本还解析 RS5 的全部 `4 × 4` 个 LS/MP RIR，用由 source、front top、
back top、receiver 构成的二维 visibility graph 求最短路径：

- 5 条 direct；
- 1 条 front-edge；
- 1 条 back-edge；
- 9 条 double-edge。

以 5 条 direct 的中位数校准测量系统固定延时后，全部 16 条 threshold onset 的
RMS 为 `0.806 ms`、最大误差 `2.712 ms`。异常主要来自接近硬地板的 LS4/MP2：
深阴影 RIR 的反卷积 pre-ringing 会让简单阈值提前触发，因此该全矩阵数值只作
诊断，不是 golden。

排除近地板 LS4 和 MP2 后，9 条标准高度路径的 RMS 为 `0.201 ms`，最大误差
`0.385 ms`。这证明 direct/single-edge/double-edge 路径分类在正常高度成立，也
说明后续全矩阵频谱验证必须换成 matched/deconvolution-aware onset，不能简单提高
threshold 隐藏异常。

## 计算成本

`udfaDoubleEdgeBenchmark` 在 Intel Core i7-14700KF / Microsoft OpenJDK 25.0.1
上以 1,000,000 次 frequency-domain geometry oracle 评估得到：

| reference | ns/evaluation | 相对单边 |
|---|---:|---:|
| 单 edge，两项 | 332.194 ns | 1.000× |
| 双 edge，Eq. (8)-(12) | 1376.804 ns | 4.145× |

双边 oracle 约 `1.38 µs/update`；即使 6 架无人机各 20 Hz 更新，纯参数参考计算也
不是瓶颈。该 benchmark 不包含实时 IIR audio sample processing，不能拿来证明
四条滤波支路的最终 voice 成本。

## Scene 02 纠偏

BRAS Scene 02 是有限板反射场景。归档虽列出 LS01、MP04 坐标，但实际 WAV/SOFA
测量矩阵只包含同侧组合：

- `LS1..3` 对 `MP1..3`；
- `LS4..6` 对 `MP4..5`。

不存在 `LS1-MP4` 的穿板测量。因此 Scene 02 不能作为先前设想的有限边缘阴影
golden；它以后只用于反射/有限板联合验证。该结论来自归档清单，不再依靠示意图
推断。

## 下一研究实验与进入产品的门槛

按以下顺序继续：

1. 全部 16 条路径的 learned-energy matched timing、几何 anchored 频谱门控与
   Genelec source directivity correction 已实现；control failure 证明短门频谱
   不能作为绝对 golden，下一步应复现完整测量链。
2. 获取或生成同一几何的 BTMS reference，先复现 2024 论文对 Eq. (8)-(12) 的
   `≤2 dB` 级结果，再与 BRAS 实测比较，分离模型实现误差和测量链误差。
3. 加入 Genelec 8020c 与 GRAS 40AF 的方向性/校准响应后重算，区分传播误差与
   测量链误差。
4. 比较单 edge、论文双 edge 和 geometry-dependent crossfade；禁止只用
   LS1-MP1 拟合 crossfade 参数。
5. 将 1–8 kHz、多个阴影深度的平均 RMS `≤3 dB` 设为暂定模型门槛，同时要求
   单点最大误差 `≤6 dB`、几何更新相邻块增益变化 `<3 dB/update`。
6. 达标后才进入墙边、门洞、L-room 的项目自采 RIR 与 A/B/ABX；未达标时维持
   当前经验 A* 传播作为产品基线。

Minecraft 方块厚 1 m，比 BRAS 的 25 mm 屏更不能被默认视为单个 knife edge。
因此厚屏双边实验不仅是修正 benchmark，也是 voxel 声学产品化前的必要模型。
