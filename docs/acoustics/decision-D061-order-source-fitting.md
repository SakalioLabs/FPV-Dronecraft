# D061 — 实测 source-model 参数必须由带独立留出的阶次证据自动生成

- 状态：implemented，真实 5-inch 数据仍待采集（2026-07-24）。
- 拟合：`fit_order_source_model.py` 要求训练集的每条 rotor-plane 记录连续
  检出至少两个 BPF 谐波；在同一记录内用
  `L_h-L_1=-20*r*log10(h)` 拟合 pressure-amplitude rolloff，避免绝对声级和
  RPM operating gain 混入谐波包络。shaft/electrical/twice-electrical
  detection pattern 在训练记录间必须一致，相对幅值以 reference RPM 的
  isolated 1 m pressure 求得；broadband energy 相对 BPF h1 pressure² 标定。
- 门限：validation rotor-plane RPM 不得出现在训练 RPM 中。已检出阶次比较
  相对声级；未检出高次谐波以 local sideband floor 作为上界；意外出现的
  excluded motor order 或更高 BPF harmonic 直接失败。全部 plane records
  必须分析相同的连续 BPF candidate orders，并至少包含一个位于 fitted cutoff
  之上的未检出候选，防止“只分析已知会出现的谐波”造成虚假通过。训练与 validation 最大误差都必须
  `<=3 dB`，产物记录 analyzer-report SHA。
- 发布链：`fit_acoustic_profile.py` 已删除 metadata 中手填的
  `source_model`。它只接受严格 schema、通过 3 dB 门限、reference RPM 与
  plane limit 一致的 order-model；evidence 必须同时绑定 descriptor CSV
  SHA 和 analyzer-report SHA。Java v1 validation 同时强制非空
  unseen-RPM、unseen-angle、order-spectrum 样本及三项 `<=3 dB`。
- 验证：独立解析真值覆盖 4 个 BPF 谐波、rolloff 1.2、三类 motor ratio、
  broadband energy、byte determinism、3.2 dB validation 失败与混合
  detection 拒绝。原始 24-bit PCM 合成链现贯通
  analyzer → order fitter → profile fitter，并恢复 rolloff `1.2±0.02`。
- 边界：合成真值只验证可识别性和软件契约，不是可发布的 FPV 参数。
  twice-electrical 仍是候选成因；真实 component-isolation、载荷/来流扫掠、
  Minecraft A/B 与听感验证仍不可省略。
