# FPV Dronecraft 分支策略

本项目同时做两件性质不同的事：玩家可直接体验的 Minecraft FPV 无人机玩法，以及可验证、可复现实验的多轴飞行动力学仿真。两者需要共享代码和数据，但不应该在同一条开发线里互相阻塞。

## 常驻分支

### `main`

稳定发布主线。

- 只接受已经验证为玩家可用的版本。
- 用于打 tag、发 release、生成玩家下载包。
- 不直接承载大规模手感试验或物理模型试验。
- 进入 `main` 的代码必须来自 `playable/dev` 或短期 `integration/vNext`，并且 CI 全绿。

当前 GitHub 默认分支仍可能是历史遗留的 `master`。在 GitHub 仓库设置切换默认分支前，`master` 只作为兼容别名保留，不再作为新的研发入口。

### `playable/dev`

游玩体验研发线。

目标是：

- 好玩；
- 稳定；
- 低延迟；
- 易上手；
- 键鼠、遥控器、HUD、FPV/目视视角、服务器联机体验可靠。

适合进入这里的改动：

- 输入链路、遥控器校准、解锁安全、HUD、相机、诊断；
- `PlayableFlightModel` 和 playable preset；
- 经过产品判断的手感调整；
- 从 `sim/lab` 转化而来的、已经证明能提升玩法且不会破坏体验的仿真成果。

不适合长期停留在这里的改动：

- 尚未验证的复杂气动模型；
- 需要大量离线数据对照的物理假设；
- 会让玩家体验不稳定的实验性数值模型。

### `sim/lab`

仿真实验线。

目标是：

- 可验证；
- 可复现；
- 可分析；
- 可对照真实数据、论文、bench 数据和 golden trace。

适合进入这里的改动：

- `drone-sim-core` 的电机、桨叶、气动、湍流、电池、接触、传感器模型；
- 离线验证工具、数据源、模型对照报告；
- golden trace、route equivalence、数值诊断；
- 不以“立刻好玩”为目标的高保真物理实验。

从 `sim/lab` 合入 `playable/dev` 前，必须先回答：

- 这项模型改进是否有可复现数据或测试支撑？
- 它是否会改变玩家手感？
- 如果会改变，是否需要作为候选 preset 或可配置项先进入 playable？
- 是否保留旧 playable 行为作为回退？

## 短期分支

### `integration/vNext`

大版本集成线，只在准备 `v0.2.0`、`v0.3.0` 等版本时创建。

- 不常驻。
- 从 `playable/dev` 创建。
- 只做发布前集成、冲突解决、版本号、文档、迁移说明、最终验收。
- 发布并合回 `main` 后删除。

## 推荐流转

```text
sim/lab
  └─ 物理模型验证完成后，选择性合入 playable/dev

playable/dev
  └─ 玩家体验稳定后，进入 integration/vNext

integration/vNext
  └─ 发布验收通过后，合入 main 并打 tag

main
  └─ 只发布玩家可用版本
```

## 分支清理规则

- `codex/*`、`diagnose/*`、`refactor/*` 默认视为短期工作分支。
- 短期分支的提交如果已经被 `main`、`playable/dev` 或 `sim/lab` 包含，应删除远端分支。
- 正被其他 worktree 使用的本地分支可以暂时保留，但不再作为协作入口。
- 删除前必须确认：

```bash
git merge-base --is-ancestor <old-branch> <target-branch>
```

返回成功后，才能删除 `<old-branch>`。
