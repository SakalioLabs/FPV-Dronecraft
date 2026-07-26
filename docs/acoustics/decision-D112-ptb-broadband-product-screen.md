# D112 — PTB 宽带产品级筛选与 target-leakage 门禁

日期：2026-07-26  
状态：PTB 全量宽带候选筛选、产品级 discovery/holdout、独立原始 CSV 复算与 24 个负向测试通过

## 决策

不向 D111 的四材料 basis 增加第五类材料，也不修改 Minecraft runtime material table。

PTB 原始表中没有 discovery 产品能在 low/mid/high 三频带同时把 AIR booth 等效吸声系数的最大相对误差压到 `10%` 以内；独立 holdout 同样没有。一个看似接近目标的单行配置（row 1088）属于 holdout 产品，且同一产品的全部配置合并后明显不再接近目标。选择该行会同时造成目标泄漏和产品配置挑选偏差。

- `basis_extension_selected=false`
- `production_change_required=false`
- D111 的 booth 四材料 basis 不充分结论保持不变
- 不把单个有利安装配置包装成新的 Minecraft 材料类别

## 数据资格与边界

输入绑定：

- PTB manifest：`docs/acoustics/ptb-material-selection-v1.json`
- PTB selection CSV：`external-data/computational-acoustics/material-absorption/ptb-selection-table.csv`
- AIR shoebox report：`build/research/air-shoebox-voxel-reference-v1.json`
- PTB 四类材料 report：`build/research/ptb-material-absorption-v1.json`
- D111 inverse report：`build/research/air-scene-composition-inverse-v1.json`

PTB 固定归档 SHA-256：

`d40814d54b90ed6cd22bb4a7584cef082b758ec01faa6cabf05493da06fe92fe`

筛选只接受：

1. `character of absorption == 2`，即 PTB 表内标记的 wide-band absorbent；
2. 125、250、500、1000、2000、4000 Hz 六个频带齐全；
3. 六个系数均为有限数且处于 `[0,1]`；
4. 禁止 clamping，异常值直接排除。

PTB 表内数值来自厂商记录或互联网汇编，PTB 明确不保证正确性。因此这些结果仍是 research prior，不是发布校准值。

## 产品身份与防泄漏拆分

产品键在读取目标值之前确定：

```text
normalize(manufacturer first line)
  + "|"
  + normalize(first non-empty of trade name, type, description)
```

规范化采用 Unicode NFKD、ASCII 转写、小写化和非字母数字折叠。输出报告只保留产品键 SHA-256、安全标签和 row id，不输出厂商地址或联系方式。

同一产品的所有厚度、安装距离和构造配置必须进入同一组。产品代表值为所有配置的逐频带中位数，再以固定平坦 octave energy 权重降为 runtime low/mid/high：

- low：125、250、500 Hz 均值
- mid：1000、2000 Hz 均值
- high：4000 Hz

拆分规则：

```text
bucket = uint32(sha256(product_key)[0:8]) mod 5
bucket 0       -> holdout
bucket 1..4    -> discovery
```

该拆分完全不读取 AIR booth 目标。所有同产品配置共享相同 bucket，禁止 row-level 泄漏。

## 全语料结果

| 项目 | 数量 |
|---|---:|
| PTB 原始有效编号行 | 2,574 |
| 六频带完整且区间合法的宽带行 | 1,384 |
| 产品组 | 868 |
| discovery 产品 | 683 |
| holdout 产品 | 185 |

AIR booth 目标等效吸声：

| 频带 | 目标 |
|---|---:|
| low | 0.376645 |
| mid | 0.354159 |
| high | 0.425425 |

排名先最小化三频带最大相对误差，再最小化相对 RMSE，最后用产品键哈希确定性打破并列。

最佳 discovery 产品：

- 标签：`hanged heavy pleated curtains`
- row：188
- runtime absorption：`0.350 / 0.325 / 0.500`
- 最大相对目标误差：`17.5297%`

最佳 holdout 产品：

- 标签：`moderato 40`
- row：1842
- runtime absorption：`0.380 / 0.390 / 0.340`
- 最大相对目标误差：`20.0798%`

两者均未通过 `<=10%` 门限：

- discovery 合格产品数：0
- holdout 合格产品数：0

holdout 不是用来从失败的 discovery 中继续挑参数；它只用于检查相同筛选策略是否存在未见产品上的反例。这里两个集合独立地给出相同否定结论。

## Row 1088 泄漏诊断

PTB row 1088 单独降频带后为：

```text
low/mid/high = 0.383333 / 0.345000 / 0.380000
maximum relative AIR-booth error = 10.6775%
```

它属于 `Travertin micro` 产品组的 holdout，组内共有：

```text
1081, 1082, 1083, 1087, 1088, 1089
```

同产品六种配置的逐频带中位数降为：

```text
low/mid/high = 0.463333 / 0.460000 / 0.475000
maximum relative AIR-booth error = 29.8853%
```

所以 row 1088 不能作为第五材料：

1. 它位于预先确定的产品级 holdout；
2. 它是同产品多个配置中的单个有利样本；
3. 单行本身也没有通过 `10%` 三频带门限；
4. 事后发现它能改善五材料 inverse，不构成事前可采纳证据。

报告将 `single_row_target_screen_forbidden=true` 固化为机器可检验契约。

## 独立 verifier

verifier 不导入分析器的产品分组或排名实现，而是从原始 CSV 独立完成：

- CSV header 定位、数字解析和六频带合法性检查；
- 产品键规范化、产品配置聚合和中位数估计；
- SHA-256 bucket discovery/holdout 拆分；
- 868 个产品的完整误差排名；
- discovery/holdout 最佳值和 `10%` gate；
- row 1088 与所属产品中位数的泄漏诊断；
- source hash、D111 booth 失败前提及 production/capture/release 边界检查。

24 个负向测试覆盖 source、目标、数量、winner、误差、产品中位数、泄漏标志、split、隐私边界、threshold、basis decision 以及 capture/release overclaim 篡改。

## Claim boundary

本轮只进行原始表格数值筛选：

- `physical_endpoint_opened=false`
- `captures_audio=false`
- `release_calibrated=false`

结果不识别 AIR booth 的真实装修和家具，也不证明 PTB 产品可直接对应 Minecraft 方块，更不构成听感真实性结论。

## 可复现证据

```powershell
.\gradlew.bat verifyPtbBroadbandProductScreen
```

- report：`build/research/ptb-broadband-product-screen-v1.json`
- verification：`build/research/ptb-broadband-product-screen-verification-v1.json`
- report SHA-256：`3fcf6b35af5d81dd4da5ed7c1dcffd57276aca786448085fdb6e476426eaab41`
- verification SHA-256：`96737ead7e5e840538958e021d07a39be1275d603009c99b5fbd62f474e01d7b`

## 下一步

D113 不应继续用 AIR booth 目标在 PTB 表中逐行搜索。后续研究应按证据强度依次进行：

1. 优先寻找 AIR booth 的真实表面/家具清单、建模说明或同一房间的可辨识构造资料；
2. 若无法获得，建立不读取 AIR 目标的语义材料 taxonomy，例如厚帘、穿孔板、悬挂吸声体和软包家具，并在独立房间数据上验证；
3. 将空气吸收作为距离与温湿度相关的独立传播项研究，而不是全局材料 damping；
4. 分离 specular/diffuse scattering 与 absorption，避免用吸声参数补偿有限 voxel 表面排列；
5. 在材料与传播模型均有独立依据后，再重跑完整 simplex、空间 voxel holdout 和 Minecraft closed→partial→open 单调性；
6. 在 booth 与 lecture 均通过前，不生成统一 production preset。
