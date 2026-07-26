# D113 — AIR 房间构造证据与可辨识性边界

日期：2026-07-26  
状态：AIR 原始论文逐页视觉核对、hash-bound 构造清单、dEchorate v2 元数据审计、独立 verifier 与 17 个测试通过

## 决策

AIR 不是完全没有房间材料信息。原始论文 Table 1 明确给出了 booth 和 lecture 的表面与家具语义；D112 的“不能任意搜索宽带产品”仍然正确，但下一步材料 taxonomy 现在可以受真实房间清单约束。

同时，AIR 仍然不能用于直接拟合 block material 参数，因为论文没有发布：

- ceiling construction；
- 各表面的 octave absorption；
- 各表面的 octave scattering；
- 完整表面积比例；
- lecture 桌椅数量和暴露面积；
- booth 定制声学面板的构造、厚度与安装方式。

最终门禁：

```text
air_semantic_scene_constraints_eligible=true
air_material_parameter_fit_eligible=false
d112_unconstrained_product_search_remains_rejected=true
production_change_required=false
```

## AIR 原始证据

来源：

- Marco Jeub, Magnus Schäfer, Peter Vary,
  “A Binaural Room Impulse Response Database for the Evaluation of
  Dereverberation Algorithms”, DSP 2009；
- DOI：`10.1109/ICDSP.2009.5201259`；
- [RWTH 官方论文 PDF](https://www.iks.rwth-aachen.de/fileadmin/publications/jeub09a.pdf)；
- bytes：`188568`；
- SHA-256：`f2cb2d4194fceb050df3f8403762488f1dd4fe4e2c255961a9fe07261db94c53`。

PDF 第 3 页 Table 1 和正文已用 Poppler 渲染为 PNG 并视觉核对；第 4 页 Figure 3 也已核对 lecture 平面图与测量位置。只记录事实与引用，不在仓库重新分发论文。

### Studio booth

| 字段 | 论文证据 |
|---|---|
| 尺寸 | `3.00 × 1.80 × 2.20 m` |
| wall surface | custom-made low-reflective panels |
| floor cover | carpet |
| furniture | `-`，明确无家具 |
| 正文补充 | 面板的特殊材料与排列实现低且近似平坦的 RT60 |

合法语义类因此仅为：

```text
engineered-acoustic-panel
carpet
```

这直接否定把 row 1088 的微孔板产品事后塞入 booth：论文没有给出该产品身份或对应构造。

### Lecture room

| 字段 | 论文证据 |
|---|---|
| 尺寸 | `10.80 × 10.90 × 3.15 m` |
| wall surface | 3× glass windows，1× concrete wall |
| floor cover | parquet |
| furniture | wooden tables，chairs |
| Figure 3 | 多排长桌/座位和讲台到接收位置的距离布局 |

合法语义类为：

```text
glass
concrete
wood-floor
table
chair
```

D111 的 lecture inverse 只能保留为数值解释。它使用的 wool/玻璃占比不能被改称论文识别出的真实表面比例，因为 ceiling、窗面积和家具面积都未知。

## 为什么仍不可拟合材料参数

尺寸和标签只能限制候选集合，不能完成吸声/散射反演。一个三频带房间平均 RT60 可由许多不同组合产生，而 AIR 缺少为每个表面提供独立约束的面积和声学系数。

尤其是：

- booth 的墙面面板标签没有说明 ceiling 是否使用相同构造；
- lecture 的“三面窗”是墙面类别计数，不等于三面墙全部为玻璃；
- 桌椅是内部散射体和吸声体，不能用墙面面积权重替代；
- AIR RIR 包含真实扩散、衍射、设备响应和有限测量长度，三频带 Eyring inverse 不会唯一识别这些成分。

因此 D113 允许使用 AIR 标签做 semantic gate，但禁止：

- 从 AIR RT60 直接回写 Minecraft block absorption；
- 用未知 ceiling/furniture 面积补齐到和为 1；
- 再次对 PTB 全表逐行以 AIR 目标排名；
- 把 inverse weights 声称为真实装修清单。

## 下一受控参考：dEchorate

选择 [dEchorate v2 Zenodo record](https://zenodo.org/records/6576203) 作为下一校准参考，原因是它提供：

- 真实测量 RIR；
- 精确 source/microphone 三维坐标；
- floor、ceiling、west/south/east/north 六个 facet 的独立反射状态；
- reflective Formica 与 perforated-panel/rock-wool 吸声面的公开构造；
- furniture true/false 条件；
- real RIR 与 image-source synthetic RIR 的成对研究路径；
- CC BY 4.0 数据许可；
- 论文 DOI：`10.1186/s13636-021-00229-0`。

完整 v2 数据为 25.8 GB，其中 RIR HDF5 为 4.2 GB；D113 不盲目下载整个 payload。先固定 2.9 MB metadata：

| 字段 | 值 |
|---|---:|
| 文件 | `dEchorate_database.csv` |
| bytes | 2,931,172 |
| SHA-256 | `0851ff6c4d0a83248bc1f78cef9b8d2506ad9592b2b3069e430c56dea10850f2` |
| rows | 10,912 |
| room codes | 11 |
| rows/code | 992 |
| microphone ids | 31 |
| source ids | 10 |

元数据覆盖 11 个 facet-state/furniture configurations：

```text
000000 000001 000010 000100 001000
010000 011000 011100 011110 011111 020002
```

每个 configuration 恰有 992 行。所有非 silence source 均有三维坐标，所有实体 microphone capsule 均有三维坐标。

id 30 是 loopback reference，不是空间麦克风：

- physical microphone rows：10,560；
- coordinate-less loopback rows：352；
- verifier 显式隔离该通道，禁止把它误计为缺失空间坐标的传感器。

## 工程方向

D114 采用 metadata-first：

1. 从 11 个 room codes 构造精确的六面 facet-state snapshots；
2. 用全吸声 `000000`、逐面 one-hot 和逐步 reflective 序列形成最小实验矩阵；
3. 先下载或远程提取仅覆盖该矩阵的最小 RIR 子集，而不是 25.8 GB 全量数据；
4. 比较真实 RIR 与 dataset 自带 synthetic RIR，分别量化 early reflection timing、RT60/EDT 和 late-tail mismatch；
5. 把 facet state 映射到 voxel surface material，不使用 AIR 目标反向挑选产品；
6. 在 CPU reference 通过后，复用同一 snapshot/expected-results contract 验证 CUDA DDA，而不是建立第二套 GPU 声学语义。

## 独立 verifier

verifier 独立完成：

- 五个 source artifact 的 SHA-256 绑定；
- AIR Table 1 两房间尺寸与清单的硬编码交叉检查；
- ceiling、系数、面积和 furniture quantity 缺失门禁；
- dEchorate 10,912 行、11 room codes、每 code 992 行的重算；
- 六个 facet-state columns、温度、source/microphone 坐标和 loopback 例外检查；
- metadata-first、禁止全量 payload、禁止 production/capture/release overclaim 的决策检查。

17 个测试覆盖 source、论文定位、房间顺序、booth panel/furniture、lecture wall/floor、缺失证据、metadata、fit eligibility、payload 与 capture/release 篡改。

## Claim boundary

本轮没有播放或录制声音：

```text
physical_endpoint_opened=false
captures_audio=false
release_calibrated=false
```

dEchorate 只完成 metadata eligibility，尚未分析其 RIR，不能宣称 Minecraft material、散射或衍射参数已经校准。

## 可复现证据

```powershell
.\gradlew.bat verifyAirRoomConstructionEvidence
```

- manifest：`docs/acoustics/air-room-construction-evidence-v1.json`
- report：`build/research/air-room-construction-evidence-v1.json`
- verification：`build/research/air-room-construction-evidence-verification-v1.json`
- report SHA-256：`3676da154ea48d19cffb79fa699ec19f5ddee76796a7b240e0aead108e66a82f`
- verification SHA-256：`bccfa8d32426d042fca8d535431e24b38fbe8720449e2ef8e606a1da0702983b`
