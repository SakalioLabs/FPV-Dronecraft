# D088 — 六条件重复采集、证据链与 ABX 打包协议通过

日期：2026-07-25  
状态：**deterministic session plan、18-take artifact binding、continuity
aggregation、balanced ABX packaging 与 independent verifier passed；真实录音和听测
仍 pending**

## 目的

D085–D087 已分别具备安全录音、连续性分析、Minecraft timeline 和 recorder 时间
对齐能力，但真实实验仍需手工管理：

- dry / Java FDN / OpenAL EFX；
- control / sound-engine reload；
- 每条件至少三次独立 take；
- 每次 WAV、capture report、timeline、alignment 和 continuity report；
- 后端匿名化的 A/B/ABX 刺激。

手工命名和配对容易把错误 timeline、WAV 或 take 混入分析，也容易在公开清单中泄露
后端。D088 新增
[`manage_audio_lab_session.py`](../../tools/acoustics/manage_audio_lab_session.py)，把这些
要求固化成机器可审计协议。该工具本身没有录音入口，不能绕过 D085 的显式授权。

## 安全的 session plan

生成计划不会枚举或打开任何 audio endpoint：

```powershell
python tools/acoustics/manage_audio_lab_session.py plan `
  --session-id minecraft-audio-lab-001 `
  --takes 3 `
  --seed "<operator-selected-random-seed>" `
  --output-json external-data/computational-acoustics/minecraft-loopback/session-001-plan.json
```

计划固定：

- 三个后端 × 两种 variant × 至少三次 take；
- 每个 take block 内按 SHA-256 rank 确定性重排；
- canonical case id 和五类相对路径；
- `recording_started=false`、`capture_authorized=false`；
- 同一输入 seed 可字节级复现计划。

计划不包含 `RECORD_AUDIO`，也不会调用 FFmpeg。操作者仍须对每个 take 单独使用
D085 capture 命令并明确授权。

## 五段证据链

`materialize` 对每个 case 要求以下文件全部存在：

```text
raw/<case>.wav
capture/<case>.json
timeline/<case>.json
alignment/<case>.json
continuity/<case>.json
```

随后严格检查：

1. D085/D086/D087 schema 和 status；
2. plan、timeline、alignment 的 backend/variant 一致；
3. control 不得宣称 reload，reload 必须实际调用 sound-engine reload；
4. capture、alignment、continuity 的 WAV SHA-256 必须与同一落盘 WAV 相同；
5. alignment 必须固定 timeline SHA-256；
6. continuity 必须固定 alignment SHA-256，并以
   `audio-lab-alignment-report` 为 boundary 来源；
7. 所有 case 必须使用同一 endpoint 和 PCM format；
8. 所有输入必须保持 `release_calibrated=false`。

单次 continuity gate 为：

| metric | gate |
|---|---:|
| marker score | ≥ 0.35 |
| maximum affine residual | ≤ 2 ms |
| reload/control guard clicks | 0 |
| dropout events | 0 |
| absolute post/pre level delta | ≤ 3 dB |

这是工程连续性门禁，不是听感质量或声学真实性门禁。任一 take 失败会使 session
`continuity_gate_passed=false`，不会通过平均值掩盖异常。

## ABX 打包

证据完整后，工具按每个 variant 建立三组 pair：

- dry ↔ Java FDN；
- dry ↔ OpenAL EFX；
- Java FDN ↔ OpenAL EFX。

三 take、两 variant 共生成 `18` 个 ABX trials。每个 pair 的 A/B 位置严格平衡，
X 答案严格为 `A=9 / B=9`。输出分为：

- `public-manifest.json`：只含 opaque trial id、variant 和匿名 A/B/X 文件名；
- `private-answer-key.json`：保存 backend、take、X answer、source case id 和 hashes。

公开清单不出现 backend 或 source id。ABX 目录中的 X 是 A 或 B 的 lossless copy；
因此这只是受控听测匿名化，不是密码学盲化。能访问文件系统或计算哈希的参与者可以
识别 X，正式听测必须通过隐藏文件访问和答案键的播放器/实验主持流程执行。

`materialize` 示例：

```powershell
python tools/acoustics/manage_audio_lab_session.py materialize `
  --plan-json <session-plan.json> `
  --session-root <session-root> `
  --output-json <session-evidence.json> `
  --blind-directory <new-empty-abx-directory>
```

工具拒绝覆盖已有 evidence report 或 ABX directory。

## Deterministic fixture

fixture 生成 `18` 个短 PCM24 文件以及完整的五段 JSON 链。它故意给所有 reload
cases 注入 `click_event_count=1`，验证：

- 18 个文件齐全不等于 continuity 通过；
- synthetic endpoint 不等于真实 loopback；
- ABX 文件已生成不等于已收集答案；
- 任一项都不能把 `release_calibrated` 改为 true。

结果：

| item | result |
|---|---:|
| conditions | 6 |
| captures | 18 |
| takes per condition | 3 |
| same capture chain | true |
| ABX trials | 18 |
| X balance | 9 / 9 |
| real loopback complete | false |
| continuity passed | false |
| listening passed | false |
| release calibrated | false |

## 可复现证据

```powershell
.\gradlew.bat --no-daemon verifyAudioLabSessionFixture
```

- plan SHA-256：  
  `f4e7a20b1493dc2a8a51b8f9b45a8d43e4858baaa249b0eaa0cb395a39cb4ea3`
- session report SHA-256：  
  `20e6e65e61ddd1e354c58790ef373209d4a187805bd270e2775ec6a2b92aad3c`
- public ABX manifest SHA-256：  
  `2267f161ddc65d208a64af65d32e8d95bfbac79fc0ee7cfa400ffca79e749375`
- private answer key SHA-256：  
  `3c56bf4de31512f7a8de2f2aeef0d2ccf44862f1954ea843100649e12e60edbd`
- independent verification SHA-256：  
  `6df237679661f018264a330fdc831b53f8fde61085f1d3638cc60ca028a07928`

负向测试覆盖：少于三次 take、WAV hash 脱链、fixture loopback overclaim、公开清单
泄露 backend 字段、X/source copy 不一致。

加入 D088 后的统一 Python regression 为 `176 tests passed`。

## 决策

1. 真实 audio-lab 不再接受临时文件名或手工配对表；
2. 六条件必须全部至少三 take，不能用缺失 case 的均值替代；
3. continuity 是逐 take 全通过 gate；
4. 公开 ABX 清单与私有答案键必须分别固定 hash；
5. ABX package 生成不代表听测完成，必须另行收集并统计回答；
6. 同一 capture chain 只证明实验内部可比，不证明 endpoint 是物理 loopback；
7. D088 不改变默认关闭的 Java FDN/EFX feature，也不改变声学参数。

## Claim boundary

本轮证明 session 调度、跨五段 artifact 的 hash binding、跨 take continuity 汇总和
ABX 匿名打包可以在 synthetic fixture 上被独立验证。没有打开音频端点、没有录制
Minecraft 输出、没有操作者听测、没有 callback underrun counter、没有物理设备切换、
没有真实 5-inch FPV 声源或 matched RIR，因此 `release_calibrated=false`。
