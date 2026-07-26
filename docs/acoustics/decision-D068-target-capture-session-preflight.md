# D068 — 目标 5 英寸采集必须在录音前固定独立 holdout

状态：**planner/materializer implemented; real capture pending**
（2026-07-25）。

## 决策

不再依靠录音完成后手工挑选 train/validation。新增
`tools/acoustics/plan_capture_session.py`，先由严格配置生成：

1. immutable capture plan JSON；
2. 操作员填写的 capture results CSV；
3. 只有全部状态和文件完整后才生成 analyzer manifest；
4. 另写 materialization report，绑定 plan、results 和 manifest SHA-256。

这避免同一 recording/maneuver、同一 RPM 或相同
`mu=abs(sin(elevation))` 同时出现在训练与验证侧。

## 默认 5 英寸矩阵

示例配置：
[`capture-session-config-v1.example.json`](../../tools/acoustics/capture-session-config-v1.example.json)。
它是 2207/1750 KV、5×4.3×3、7 pole-pairs 的**示例**，实际采集前必须替换所有
`example:` ID 和电气/环境值。

### Plane RPM

| split | RPM | takes |
|---|---|---:|
| train | 8000、12000、16000、20000、24000、28000 | 2 |
| validation | 10000、14000、18000、22000、26000 | 1 |

validation RPM 全部未见、且严格位于 training anchor 范围内部，允许插值误差验证，
不拿外推失败混入 release gate。

### Directivity

reference RPM 为 16000，是 training plane anchor。

| split | elevation from rotor plane | takes |
|---|---|---:|
| train | ±30°、±60°、±90° | 2 |
| validation | ±15°、±45°、±75° | 1 |

正负半球都采集，用于暴露 runtime even model 无法表达的 hemisphere asymmetry。
holdout 不是按 signed angle，而是按 axis cosine 检查；例如 train `-30°` 与
validation `+30°` 会被判定泄漏。

默认总量：

```text
train plane:        6 RPM × 2 takes = 12
train directivity:  6 angles × 2 takes = 12
validation plane:   5 RPM × 1 take  = 5
validation angle:   6 angles × 1 take = 6
total: 35 measurement recordings
background: 13 geometry-matched files
calibration: one 94 dB / 1 kHz file
```

## 预检

generator 在产生文件名前验证：

- session/file ID 格式；
- airframe、source configuration、motor、propeller、microphone 和 signal-chain
  provenance；
- blade count、pole pairs 和 harmonic count；
- sample rate、PCM bit depth、channel、距离、segment duration；
- calibration 与 analyzer 参数；
- train/validation RPM 不相交且 validation 位于 training range；
- train/validation axis cosine 不相交；
- 每组 directivity angle 都包含正负半球且至少 3 个 distinct `mu`；
- 最高 BPF harmonic `<0.45×sampleRate`；
- 每个计划 RPM 上，BPF、shaft、electrical 和 twice-electrical 的 `±toneWidth`
  windows 不重叠。

最后一项很重要：例如 two-blade、7 pole-pair、7 BPF harmonics 会让第 7 BPF 与
`2×electrical` 完全重合。这样的工况在录音前就被拒绝，而不是采完后才发现 analyzer
无法唯一归因。

## 文件与 materialization

generate：

```powershell
python tools/acoustics/plan_capture_session.py generate `
  --config-json tools/acoustics/capture-session-config-v1.example.json `
  --output-plan-json external-data/computational-acoustics/5inch-session/capture-plan.json `
  --output-results-csv external-data/computational-acoustics/5inch-session/capture-results.csv
```

CSV 预填 capture ID、预期 WAV/tach 路径、segment 和环境默认值。操作员必须填写：

- `status=captured`；
- actual thrust；
- actual voltage/current；
- 当时温度、湿度、气压；
- 与计划完全一致的 WAV 和 RPM CSV。

每个 RPM CSV 仍需满足 D059 的至少 5 个严格递增时间样本和 RPM CV gate。planner
不从 target RPM 伪造 measured RPM。

materialize：

```powershell
python tools/acoustics/plan_capture_session.py materialize `
  --plan-json external-data/computational-acoustics/5inch-session/capture-plan.json `
  --results-csv external-data/computational-acoustics/5inch-session/capture-results.csv `
  --output-manifest-json external-data/computational-acoustics/5inch-session/recording-manifest.json `
  --output-report-json external-data/computational-acoustics/5inch-session/materialization-report.json
```

任何 planned/failed row、空 actual value、缺 WAV/tach/background/calibration、路径与
计划不一致或重复 capture ID 都会阻止 manifest。

## Airframe/install provenance

D059 manifest 新增：

```text
airframe_id
source_configuration =
  single_rotor_bench | full_airframe_bench | free_flight
```

CIRA 数据已经证明 isolated rotor、安装板和完整四旋翼不能互换。motor/propeller ID
相同不再意味着 acoustic source 相同。

当前示例明确为 `single_rotor_bench`，用于建立可归因的单转子基础模型。它不能被
改名为 full-airframe profile。完整四旋翼需要后续安装效应实验；若各电机 RPM
不一致，当前单 tach schema 还不足以形成 release evidence，必须扩展为 per-rotor
telemetry。

## 已执行的软件证据

示例配置已实际生成：

```text
build/research/example-5inch-capture-plan-v1.json
captures: 35
background files: 13
bytes: 26,300
SHA-256: 809e71b39b9362d7b3c56aa552f8b8ac2fb71972c457fad08aeb57734afeb49c

build/research/example-5inch-capture-results-v1.csv
status: all planned
bytes: 10,798
SHA-256: 243e3392782d81dd14934d8dc7d4fc4d12cf4115e4504524103eb8cefb58e1aa
```

这是 planning evidence，不是 measurement。release state 保持
`planned_not_measured`；只有真实文件 materialize 后也只是
`captured_not_yet_analyzed`，仍需 analyzer、order/profile holdout 和 Java decoder。

