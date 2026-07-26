# D111 — AIR scene-composition inverse 与 voxel holdout

日期：2026-07-26  
状态：完整 simplex 穷举、两房间各八种空间布局、独立全局最优重算与 16 个负向测试通过

## 决策

四类 PTB material prior 可以解释 AIR lecture 的三频带房间响应，但不能共同解释 AIR
booth。因此不生成统一 production scene preset，也不修改 runtime material table。

- lecture：作为可行的研究级 inverse explanation 保留；
- booth：判定当前四材料 basis 不充分；
- 两房间共同 production profile：拒绝；
- 下一步扩展有真实 PTB 数值依据的材料 basis，而不是增加无来源的全局 damping。

inverse mixture 只回答“哪些表面积组合能产生相似三频带衰减”，不识别 AIR 房间中
实际使用了哪些材料或家具。

## 输入与算法

绑定来源：

- D110：
  `f6735684aad68eabcf1b12c2990357f666f01d2191630732080c12eb523316f5`
- AIR shoebox：
  `4de568d12ac116ac2f58197fe9125ca188ad5e2be3a4201c942e32e55953bb43`
- PTB material：
  `8e506562d92ea131af8ee402736c9ce498a7a3894acb883fa78637a1620d0bb9`

材料顺序固定为：

1. dense stone
2. solid wood panel
3. porous wool
4. glass window

使用 PTB research candidate 三频带吸声，并沿用当前 runtime scattering。面积权重满足：

```text
w_i >= 0
sum(w_i) = 1
```

异质路径按现有 estimator 的 mean-log retention 合成：

```text
alpha_mix(band) =
  1 - exp(sum_i(w_i * log(1 - alpha_i(band))))
```

对 `1/200` 权重网格完整枚举，每个房间恰好 `1,373,701` 个候选。目标函数先最小化
三频带最大相对吸声误差，再以相对误差平方和打破并列。

## Inverse 结果

| 房间 | stone | wood | wool | glass | 最大吸声误差 |
|---|---:|---:|---:|---:|---:|
| booth | 0.000 | 0.000 | 0.380 | 0.620 | 28.33% |
| lecture | 0.230 | 0.125 | 0.090 | 0.555 | 5.64% |

booth 的最优解已经落在 stone/wood 为零的 simplex 边界，仍无法同时匹配 low、mid、
high。这不是优化器提前停止：Java 生成器和 Python verifier 分别完整枚举相同的
`1,373,701` 个候选并得到同一全局最优点。

## 空间 voxel holdout

每个权重解被离散到匹配 AIR 尺寸的封闭 voxel shell：

- booth：`3×2×2` interior，68 个 surface cells；
- lecture：`11×11×3` interior，482 个 surface cells。

每个房间生成 8 个确定性低离散度空间排列；每个排列使用 256 rays、12 bounces，
得到 3,072 个 surface hits，且：

- `escaped_rays=0`
- `truncated_legs=0`
- 所有 material cell fraction 与连续权重相差不超过一个 surface cell

| 房间 | voxel median RT60 low/mid/high | 最大 median 误差 | 最大 layout span/median |
|---|---|---:|---:|
| booth | 0.1920 / 0.0990 / 0.0809 s | 51.37% | 13.91% |
| lecture | 0.8360 / 0.7763 / 0.6681 s | 14.03% | 18.86% |

门限：

- inverse 最大相对误差 `<=10%`
- voxel median RT60 最大相对误差 `<=15%`
- 八布局 max-min span / median `<=20%`

lecture 三项均通过；booth 的解析 inverse 与 voxel holdout 均失败。两房间的空间布局
变化均低于 20%，因此 booth 失败不能归因于一次不利的表面排列。

## 工程含义

结果支持以下方向：

- Minecraft 已有基于实际 block material 的 scene composition 路径值得保留；
- 大房间 lecture 级响应可由当前材料 basis 近似；
- 小 booth 需要另一种具有更宽带吸收形状的有据材料类别，或经数据验证的额外损耗
  机制；
- 不应通过全局缩短 stone RT60、统一 clamp 或伪造 furniture multiplier 解决；
- 在 booth 与 lecture 都通过前，不接入生产 preset。

## 独立 verifier

verifier 从 D110、AIR shoebox 与 PTB report 重新取得目标和材料值，然后：

- 向量化完整枚举两个 `1/200` simplex；
- 验证 material order、全局最优 weights、预测吸声与误差；
- 验证 16 个空间布局的 surface-cell 数量、probe completeness 和 RT60；
- 重算 median/min/max、layout span 和房间 feasibility；
- 强制拒绝 both-room production profile。

16 个负向测试覆盖 source policy/hash、材料与房间顺序、目标、非最优权重、inverse
metric、layout 顺序、surface counts、RT60 error、probe truncation、median、
production/capture overclaim。

## Claim boundary

这是数值 inverse 与软件 voxel probe：

- `production_change_required=false`
- `physical_endpoint_opened=false`
- `captures_audio=false`
- `release_calibrated=false`

lecture 的可行权重不是 AIR 家具清单、Minecraft 推荐装修配方或听感等价证明。

## 可复现证据

```powershell
.\gradlew.bat verifyAirSceneCompositionInverse
```

- report：
  `build/research/air-scene-composition-inverse-v1.json`
- verification：
  `build/research/air-scene-composition-inverse-verification-v1.json`
- report SHA-256：
  `776086db511c0197116d31007df7bf2042fd7934c76e1dfe348fcb1b6486b392`
- verification SHA-256：
  `fe6bc43e4b2337a3d86a80d21fb15db5f62f4b069beca1705a176309b0259031`

## 下一步

D112 应回到 PTB 原始材料行而不是发明参数：

1. 搜索具有 booth 所需 broadband 形状的公开 PTB 产品/安装候选；
2. 按产品或构造分组建立 train/holdout，禁止同一产品行泄漏；
3. 每次只向 basis 增加一个可解释类别；
4. 先重跑完整 simplex，再重跑八布局 voxel holdout；
5. 要求 booth 与 lecture 同时通过，同时检查原有 Minecraft
   closed→partial→open 单调性；
6. 若真实材料 basis 仍失败，再单独研究空气损耗、非镜面散射或有限体素边界模型，
   不把残差直接包装为经验 damping。
