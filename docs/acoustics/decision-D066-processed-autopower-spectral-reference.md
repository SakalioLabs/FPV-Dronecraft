# D066 — 处理后 Pa² 频谱只能进入 spectral-reference 验证线

状态：**implemented and exercised on real public data; release gate closed**
（2026-07-25）。

## 决策

公开 acoustic autopower 可以补充 BPF harmonic、RPM trend 和 directivity 证据，
但不能成为 D059 raw-recording analyzer 的替代输入。新增
`docs/scripts/analyze_spectral_reference.py`，其输出类型固定为
`processed_autopower_spectral_reference`，并强制：

- `release_profile_eligible=false`；
- `raw_pressure_time_history_available=false`；
- `phase_available=false`；
- `pcm_synthesis_allowed=false`；
- `release_profile_fitting_allowed=false`。

报告 validator 会重新核对完整约束对象。修改单个布尔值或删除 limitation 都会失败。

## 真实输入

来源：[Recherche Data Gouv DOI 10.57745/L8MNF5](https://doi.org/10.57745/L8MNF5)，
Etalab Open Licence 2.0。固定文件：

```text
datafile id: 709440
file: straight_plate_tripped_BPT_acoustic_autopower.h5
bytes: 4,332,048
MD5: bc0b9c815bdca9f2e073303d315eed7b
SHA-256: ce86109218a1cb871205a3abfbfa3b2d74e69634757266dc03eb73ba762fdffe
```

完整来源、版本、HDF5 contract 和分析参数见
[`serration-spectral-reference-manifest-v1.json`](serration-spectral-reference-manifest-v1.json)。
原始 HDF5 只保存在 gitignored `external-data/`。

实际 HDF5 结构经文件读取而非网页猜测：

| dataset | shape/value | 语义 |
|---|---|---|
| `Autopower` | `[8193,13,5]`, float64 | `(frequency,theta,rpm)` Pa² |
| `RPM` | 4000、5000、6000、7000、8000 | 5 个 operating points |
| `frequency_Hz` | 0–25600 Hz，3.125 Hz uniform bins | 8193 bins |
| `theta_deg` | +60° 到 -60°，10° 间隔 | 0° 是 rotor plane |
| `radius_m` | 1.62 m | microphone radius |

`blade_count=2` 当前由 dominant line `≈2×RPM/60` 与第二线
`≈4×RPM/60` 推断；它不是同步 tach 或已解析 CAD 的替代品。manifest 明确保留该
provenance，完成论文/CAD 人工核对前不能扩大成 rotor geometry 真值声明。

## 处理算法

1. 严格验证 dataset 名称、shape、axis 单调性、3.125 Hz 等间隔、RPM 顺序、
   0° plane、单一正 radius，以及全部 autopower finite/nonnegative；
2. 预期 order center 为 `harmonic × blade_count × RPM / 60`；
3. tone window 为 `±6.25 Hz`；
4. local floor 取距中心 `2–4 × half-width` 的 sideband median，再按 tone-bin
   数量从 window power 扣除；
5. local prominence 至少 6 dB 才记为 detected；
6. Pa² 用 `10log10(P/(20 µPa)²)` 转 SPL；
7. 1.62 m 到 1 m 只做显式 free-field `20log10(1.62/1)` 数学归一化；
8. directivity 使用 runtime 同构的 `c2*mu²+c4*mu⁴`，其中
   `mu=abs(sin(theta))`；
9. RPM trend 只报告 `level ~ log2(RPM)` slope、RMSE 和最大残差，不直接复制进
   profile。

数据只覆盖 `|theta|<=60°`，所以 fit 的 `axis_db` 是向 90° 的**外推**。报告新增
`axis_is_extrapolated=true` 和 `maximum_measured_axis_cosine=sin(60°)`，禁止将其
标成 axis microphone measurement。

## 真实运行结果

命令对固定 BP-T 文件生成：

- 5 RPM points；
- 13 angles；
- 8193 frequency bins；
- 40 个 complete harmonic-directivity fits（8 harmonics × 5 RPM）；
- 8 个 complete harmonic RPM trends；
- release eligibility 始终 false。

在 rotor plane、归一到 1 m 的 BPF isolated levels：

| RPM | BPF level dB SPL |
|---:|---:|
| 4000 | 55.480 |
| 5000 | 62.819 |
| 6000 | 69.029 |
| 7000 | 73.229 |
| 8000 | 77.979 |

BPF 的 log-RPM fit 为 `22.315 dB/RPM-doubling`，RMSE `0.249 dB`，最大残差
`0.382 dB`。这是该 rotor/processed dataset 的经验趋势，不是 5-inch FPV 默认律。

BPF directivity 的 90° extrapolated axis relative level 为约
`-19.215…-21.267 dB`；内部 fit RMSE `0.398…1.287 dB`。正负半球最大差随工况为
`0.544…4.831 dB`，说明即使 isolated rotor 也不能只存一个未经误差说明的
“完美轴对称”曲线。

第 2 harmonic 的 log-RPM fit 最大误差约 `1.082 dB`，而第 3–7 harmonic 中部分
简单 power-law fit 最大误差达到约 `2.68–4.41 dB`。因此 runtime 不能把一个 BPF
RPM exponent 无条件复制到所有 harmonics；目标 5-inch capture 必须保留逐阶次
holdout。

本次输出：

```text
build/research/serration-bpt-spectral-reference-v1.json
bytes: 331,424
SHA-256: 9c1f2f0ca016b3d2520c4e09784f813947cbb04fff27403c6b18882a6ec7a06b
```

该 report 是本机研究证据，不提交 raw HDF5；只有 manifest、工具、测试和上述摘要
进入仓库。

## 复现

```powershell
python docs/scripts/fetch_serration_spectral_reference.py `
  external-data/computational-acoustics/serration-reference

python -m pip install h5py==3.12.1

python docs/scripts/analyze_spectral_reference.py `
  --h5 external-data/computational-acoustics/serration-reference/straight_plate_tripped_BPT_acoustic_autopower.h5 `
  --blade-count 2 `
  --maximum-harmonics 8 `
  --tone-half-width-hz 6.25 `
  --reference-distance-m 1.0 `
  --dataset-doi doi:10.57745/L8MNF5 `
  --expected-md5 bc0b9c815bdca9f2e073303d315eed7b `
  --output-json build/research/serration-bpt-spectral-reference-v1.json
```

若默认 Python 环境需要保持原有 NumPy/SciPy 约束，应在独立 venv 安装 h5py，不要
为此升级项目级 NumPy。

## 下一步

1. 从论文或 CAD 独立确认 BP-T blade count、radius/chord/twist；
2. 依次运行 straight baseline、tripped baseline 和 serration variants；
3. 对同 RPM/angle 做 paired spectral delta，分开 tonal 与 broadband；
4. 用 clean/tripped 对照判断 BPF 外 broadband 是否受 transition/serration 影响；
5. 所有结果继续停留在 validation report，直到目标 5-inch raw capture 通过
   D059–D061。

第 2–3 项的 straight/tripped 基线比较已由 D067 完成；analyzer 同时加入用 local
floor 替换 BPF bins 的 `bpf_removed_band_levels`，详见
[`decision-D067-paired-autopower-deltas.md`](decision-D067-paired-autopower-deltas.md)。
