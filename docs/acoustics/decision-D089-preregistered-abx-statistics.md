# D089 — 以参与者为统计单位的预注册 ABX/偏好分析通过

日期：2026-07-25  
状态：**preregistration、participant-specific presentation order、complete-response
validation、exact binomial/sign tests、Holm correction、synthetic fixture 与 independent
verifier passed；真实参与者 pending**

## 目的

D088 可以生成匿名 A/B/X 文件和私有答案键，但“生成 ABX 文件”并不等于听测完成。
如果直接把一名听者的 18 个回答当作 18 个独立样本，会低估重复测量的相关性；如果看完
结果再决定样本量、阈值或检验方法，也会产生明显的数据依赖。

D089 新增
[`score_audio_lab_abx.py`](../../tools/acoustics/score_audio_lab_abx.py)，先生成不可包含
回答的 preregistration，再验证和汇总响应。它不播放声音、不招募参与者，也不采集
身份信息。

## 预注册

在任何响应 CSV 存在前执行：

```powershell
python tools/acoustics/score_audio_lab_abx.py protocol `
  --session-report <D088-session-evidence.json> `
  --public-manifest <public-manifest.json> `
  --private-key <private-answer-key.json> `
  --minimum-participants 12 `
  --maximum-extra-replays 3 `
  --presentation-seed "<operator-selected-random-seed>" `
  --alpha 0.05 `
  --minimum-majority-fraction 0.75 `
  --output-json <listening-preregistration.json>
```

preregistration 固定 D088 report、public manifest 和 private key 的 SHA-256，并固定：

- 至少 `12` 名参与者；
- 每人完整回答 `18` trials；
- 每 trial 最多 `3` 次额外重放；
- familywise alpha `0.05`；
- participant-majority effect gate `≥0.75`；
- 六个 discriminability tests 内做 Holm correction；
- 六个 preference tests 另作一组 Holm correction；
- participant 而不是 individual trial 是推断单位。

`12` 是本项目当前的最低工程门禁，不是对所有听觉实验都充分的通用 power 结论。
真实发布前仍应根据预期 effect、设备差异和目标人群做独立 power/sensitivity 分析。

## 每名参与者独立的 presentation order

所有参与者使用同一个 trial 顺序会引入学习、疲劳和 carry-over 偏差。D089 按：

```text
SHA-256(presentation_seed NUL participant_id NUL trial_id)
```

升序生成每个 pseudonymous participant 的独立顺序。响应 CSV 必须记录
`presentation_index=1..18`，scorer 会逐人复算并拒绝重复、缺失或未经预注册的顺序。
这不是密码学随机化；它是可审计、可复现的实验顺序约束。

响应 CSV 字段严格为：

```text
participant_id,trial_id,presentation_index,x_response,preference,
realism_a,realism_b,extra_replays
```

- `participant_id` 必须为不含空白的 pseudonym；
- `x_response` 只能是 A/B；
- `preference` 只能是 A/B/NONE；
- realism 为 1–5 整数；
- 每名参与者必须恰好覆盖全部 trials；
- aggregate report 不写出 participant IDs，只固定响应 CSV hash。

真实人类研究还必须由操作者独立处理知情同意、退出机制、隐私保存期限和适用的机构/
地区要求；该工具不提供伦理审批。

## 统计方法

每个 variant × backend pair 有三个 take。对每名参与者：

1. ABX correctness 以三 take 中多数正确作为一个 participant-level Bernoulli；
2. preference 把匿名 A/B 映射回 backend，以三 take 多数偏好作为一个
   participant-level Bernoulli；
3. 偶数 take 的平票或 preference 的 NONE/tie 不进入相应检验；
4. discriminability 用 chance `p=0.5` 的 one-sided exact binomial；
5. preference 用 `p=0.5` 的 two-sided exact binomial；
6. 两个六检验 family 分别做 Holm correction；
7. 统计显著且 participant-majority fraction `≥0.75`、non-tied participant 数达到
   预注册下限，才记录 detected。

realism 1–5 是 ordinal rating。本轮只输出 count/mean/min/max 的描述值并明确
`inferential_claim=false`；不能把均值差直接写成校准或统计显著结论。

## Deterministic fixture

fixture 使用 D088 的 18 个 synthetic ABX trials 和 `12` 个 synthetic participant，
共 `216` 个回答。植入模式为：

- dry↔Java 和 dry↔EFX：每个 comparison 有 `11/12` participant majority
  correctly discriminating，并统一偏好 wet backend；
- Java↔EFX：`6/12` correctly discriminating，偏好也严格 `6/12`；
- realism：dry=`2`，Java/EFX=`4`；
- 每人按独立 SHA-256 presentation order 回答；
- replay count 保持 `0..3`。

结果：

| item | result |
|---|---:|
| participants | 12 synthetic |
| trials per participant | 18 |
| total answers | 216 |
| overall planted accuracy | 168/216 = 0.7778 |
| discriminability tests | 6 |
| detected planted dry comparisons | 4 |
| Java↔EFX detections | 0 |
| preference tests | 6 |
| detected planted wet-over-dry preferences | 4 |
| Java↔EFX preferences | 0 |
| real participants | false |
| release calibrated | false |

fixture 的检测结果只证明 scorer 能恢复已知植入效应，不能证明真实 dry、Java FDN 或
OpenAL EFX 之间存在这些听感差异。

## 可复现证据

```powershell
.\gradlew.bat --no-daemon verifyAudioLabAbxStatisticsFixture
```

- preregistration SHA-256：  
  `35e927128ad717827fa4a96ae60c29dcf0300f51e3b453de93df36dcb24ef305`
- synthetic responses SHA-256：  
  `d1b2d2ef09b630af4fc93b61b588c484ebd63e2788bd893e2dbb1a8f1ef48cda`
- analysis report SHA-256：  
  `9dc5cd689d1de82da03e82bd5b7cb7ea7e237d7863aa6b64a7f4b3bc9adbe494`
- independent verification SHA-256：  
  `a4290989269e2702bdb15712ef0f4cd0a3c8060474b36f5c786abd822606d5fb`

D091 将 D088 timeline 升级为含 native timing 的 schema v2，因此本节 protocol 与
analysis/verification hashes 随上游 session report identity 更新；synthetic
responses CSV hash 保持不变，统计检测结果也未改变。

负向测试覆盖：不完整 participant、超出 replay 上限、非预注册 presentation order、
trial-level inference overclaim 和 release-calibration overclaim。

加入 D089 后的统一 Python regression 为 `184 tests passed`。

## 决策

1. 真实 ABX 必须先落盘 preregistration，再接受响应；
2. 每名参与者必须使用独立且可复核的 trial 顺序；
3. 统计推断单位固定为 participant-majority，不把重复 trials 当独立参与者；
4. discriminability 和 preference 分属两个 Holm families；
5. “没有检出差异”只能写成当前设计/样本下未检出，不能证明两个 backend 完全相同；
6. ABX discrimination、preference 和 realism 分开报告；
7. 听测完成仍不能替代物理 loopback、连续性、matched RIR 或声学校准。

## Claim boundary

本轮证明预注册、顺序随机化、响应完整性、participant-level exact tests 和 multiplicity
correction 能在 synthetic answers 上恢复已知效应。没有真人听测、没有个人数据采集、
没有真实 Minecraft loopback、没有声学真实性或 release-calibration 结论。
