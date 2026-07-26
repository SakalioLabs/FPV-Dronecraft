# NASA 小型 rotor-motor operating-point 证据

日期：2026-07-24

状态：官方实验参数已核验；拒绝通用 RPM/tip-speed 响度幂律

## 1. 来源

NASA NTRS：
[Acoustic Characterization and Prediction of Representative, Small-Scale Rotary-Wing UAS Components](https://ntrs.nasa.gov/citations/20160009054)

本地研究 PDF：

```text
tmp/pdfs/NASA-20160009054-small-rotor-acoustics.pdf
size: 7,525,697 bytes
sha256: 76bc8fd97be1f553f2c70e9a08640ec7ca3c9148c455af8f4f41fdfd67e79391
```

NTRS 标记为美国政府作品、public use permitted。本文只记录实验事实和实现约束，
不把图像或论文全文打包进 mod。

## 2. 经 PDF 图文核验的实验参数

| 项目 | DJI-CF | APC-SF |
|---|---:|---:|
| propeller | DJI 9443 carbon-fiber | APC 11×4.7 slow-flyer |
| diameter | 9.4 in / 0.24 m | 11.0 in / 0.28 m |
| blades | 2 | 2 |
| RPM test limits | 3000–7200 | 1800–5100 |
| RPM increment | 300 | 300 |
| motor coils / magnetic poles | 12 / 14 | 12 / 14 |

两套系统使用同一 DJI OPTO E300 ESC，但电机不同。声学实验位于 NASA Langley SALT
消声室：

- 5 支 1/4-inch Brüel & Kjær 4939 free-field microphone；
- 距 motor hub `1.905 m / 75 in / 13R`；
- elevation `-45°,-22.5°,0°,22.5°,45°`；
- 80 kHz、每个工况 30 s；
- 因封闭房间可能产生 recirculation，后处理只使用 onset 前约 5 s；
- 同步 laser tachometer 与单轴 load cell。

这些参数由 PDF 第 3–4 页实验装置与 Table 1/2 的渲染图核验，不只依赖文本抽取。

## 3. 论文支持和不支持的幅值规律

PDF 第 8–11 页的频谱、1/3-octave 和 OASPL 图文说明：

1. 两套 rotor 的 BPF acoustic amplitude 都随 RPM 增加。
2. APC-SF 的 `2×BPF` 也呈清晰增加；DJI-CF 的该项没有同样明确的单调趋势。
3. 两套系统高频 broadband roll-off 的位置都会随 RPM 上移。
4. APC-SF 的 unweighted OASPL 在全部测角随 RPM 增加。
5. DJI-CF 的 unweighted OASPL 在三个绘制转速下近似相似，A-weighted OASPL
   甚至随 RPM 增加而下降。作者把难以提取简单趋势归因于 high-frequency
   broadband 和 loaded-motor noise。
6. 隔离电机在约 `10–14 × shaft frequency` 范围仍贡献明显 tonal content，
   与 14 magnetic poles 相关的机械 cogging tone 是重要候选。

同推力比较使用：

```text
DJI-CF: 6000 RPM, T = 0.623 lb / 2.77 N
APC-SF: 3600 RPM, T = 0.642 lb / 2.86 N
```

较低 tip-speed 的 APC-SF 在 plane 外观察位置具有明显 OASPL 优势；论文报告
unweighted OASPL 最多约 `8 dB at -45°`、`6.5 dB at -22.5°`。这证明 rotor
tip speed 很重要，但也同时证明“RPM 或 tip speed 单变量决定整机 OASPL”不成立：
不同 rotor、motor、观察角度和频带会得到不同甚至反向趋势。

## 4. 对 MCFPV 的决策

禁止把以下形式作为通用默认：

```text
amplitude = constant * RPM^n
amplitude = constant * tipSpeed^n
OASPL = offset + n * log10(RPM)
```

除非 `n` 来自同一 airframe+rotor+motor+propeller profile 的实测 holdout。

core 应使用 profile-owned RPM anchors，并至少分离：

- rotor tonal gain；
- motor/electrical tonal gain；
- broadband energy gain。

相邻锚点在 log-RPM 与 dB 空间插值，超出实测范围钳制到端点，不外推幂律。该结构由
`RotorOperatingPointGainCurve` 实现。`OrderTrackedRotorModel` 的研究默认使用
unity curve，因此不会把 NASA 的 9.4/11-inch 双叶结果伪装成 5-inch 三叶 FPV
默认值。

## 5. 后续校准门禁

每个可发布 FPV profile 至少需要：

1. 同步逐 rotor RPM、推力/负载、电流和电压；
2. 8k–30k RPM 范围的稳态锚点和独立 ramp holdout；
3. motor-only、propeller-only、rotor-motor 三套录音；
4. rotor tonal、motor tonal、broadband 分别拟合；
5. 未见 RPM 点的 band/order level 误差 `<=3 dB`；
6. 不同观察角度分别处理 directivity，不能把角度差吸收到 RPM gain；
7. profile 生效后移除或固定 Minecraft 外层的旧 RPM volume heuristic，避免双重
   响应；在此之前旧 heuristic 仍是 unity profile 下唯一的 RPM 响度 fallback。
