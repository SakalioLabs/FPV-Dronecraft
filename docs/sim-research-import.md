# 仿真研究资料导入说明

导入时间：2026-06-26

本次整理只面向 `sim/lab` 仿真实验线。目标是保留另一个 agent 已经完成、可以复用的论文和数据整理成果，同时避免把旧分支中的半成品源码改动、手感调参和大体积缓存混入主线。

## 已保留内容

- `docs/fpv-sim-model-validation.md`：当前物理模型与公开数据的验证报告，包含静态推力、前飞流场、电池 ESR、地效、VRS、风场和传感器噪声等对照结论。
- `docs/fpv-sim-data-sources.md`：可追溯数据源清单，覆盖 UIUC、Tyto、IMAV 2021、Kolaei 2018、RATM、NeuroBEM、APdrone 等来源。
- `docs/fpv-sim-targeted-calibration-gap-hunt.md`：下一轮校准缺口列表，适合作为 `sim/lab` 后续实验入口。
- `docs/data/*.csv`：顶层机器可读数据包和校准线索，共 134 个 CSV 文件。
- `docs/scripts/*.py`：数据抓取、缓存、解析和验证脚本，共 63 个 Python 脚本。
- `docs/archive/rotor_physics_research.md`：较早的转子物理研究笔记。它保留为历史上下文；若与新版验证报告冲突，以 `fpv-sim-model-validation.md` 和对应 CSV 包为准。

## 已明确排除内容

- `docs/data/raw/**`：原始下载缓存和依赖文件，体积约 3.6 GB，不适合进入 Git 仓库。需要复现时请使用脚本重新抓取或在本地缓存。
- 旧 dirty worktree 中的源码删除状态：该工作树存在大量 `drone-sim-core` 和 `fabric-mod` 文件删除，不能作为有效成果采纳。
- `codex/physics-feel-tuning` 中的旧手感调参代码：该分支落后当前架构较多，直接合并会冲突当前输入、相机、飞行模型候选和诊断系统。
- 任何飞行模型、适配器、路由器、相机、控制器、ACRO 参数或 golden trace 修改。

## 建议阅读顺序

1. 先读 `docs/fpv-sim-targeted-calibration-gap-hunt.md`，确认当前最缺的数据和建模风险。
2. 再读 `docs/fpv-sim-model-validation.md`，看现有参数与公开数据的差异位置。
3. 使用 `docs/data/*.csv` 中的 packet 文件做数值复核，不要从 Markdown 摘要直接调参。
4. 需要复现或扩展数据时，从 `docs/scripts/analyze_fpv_model_sources.py` 开始，但不要把生成的 `docs/data/raw/**` 提交进仓库。

## 对分支的影响

这次导入不改变运行时行为。`playable/dev` 继续负责可玩性、控制手感和稳定性；`sim/lab` 负责可验证、可复现、可分析的仿真实验。只有当 `sim/lab` 的结论形成明确、可测试的模型改动时，才进入后续集成分支。
