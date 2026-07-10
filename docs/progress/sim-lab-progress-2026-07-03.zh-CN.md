# sim/lab 进度说明 2026-07-03

本文用于人工交接，回答“这几天做了什么、哪些没有做完、下一步应该怎么收束”。它不是新的审计清单。

## 当前状态

- 仓库：`SakalioLabs/FPV-Dronecraft`

- 分支：`sim/lab`

- 远端：`origin` 是原仓库，不使用 fork。

- 协作方式：用户明确要求不要 PR，不要 fork；有效进度直接提交并推送到 `origin/sim/lab`。

- 研发路线：`sim/lab` 只追求真实性、可验证性和可复现性；`playable/dev` 只在仿真结论成熟后消费参考数据或低成本近似。

## 2026-07-10 收敛更新

最初列出的 CT/CP/J 主链路已经从“合同和参考表”推进为可运行模型：

- `UiucDa4002MeasuredRotorModel` 将 32 个 UIUC 静态行和 112 个轴向前飞行组成有界的 DA4002 `CT(J, RPM)` / `CP(J, RPM)` 曲面。静态点、相邻 J、重叠风洞转速段和相邻名义 RPM 的插值规则均显式；缺少相邻支撑或超出数据域时直接 blocked，不外推。

- 同一个模型按 `T = CT rho n^2 D^4`、`P = CP rho n^3 D^5`、`Q = P / omega` 输出 N/W/Nm，并派生盘载、理想诱导速度、动量功率和 Re75。负 CT 实测尾段保留为参考，但不允许作为正推力施加。

- `UiucDa4002MeasuredRotorForceModel` 进一步把标量响应装配为机体系推力、轴反扭矩、`r x F` 和总力矩。只有桨径匹配、非反向、纯轴向且正推力的实测查询可输出非零 applied force；斜流只保留轴向投影参考，反向流、曲面缺口和桨径错配显式 blocked。

- 这条路径仍是 `drone-sim-core` 的离线/可调用物理参考，没有修改 `DronePhysics`，也没有接入 `playable/dev`。这是有意保持的边界：UIUC 当前数据能验证轴向 DA4002 模型，不能证明任意桨、斜流或反向流模型。

## 已完成工作

### 1. 远端归位

本地进度已经推回原仓库 `origin/sim/lab`。最近确认过本地分支与远端同步，远端提交为 `2e87d161 Gate OpenFOAM result contract on run budgets`。

这一步解决的是协作入口问题。后续 agent 不应再把工作放到 fork、临时 PR 或用户看不到的旁路分支。

### 2. Aerodynamics4MC 参考研究

已经围绕 `MozillaFiredoge/Aerodynamics4MC-Core` 研究了可用能力，重点是 Minecraft 体素环境中的局部风场、压力中心、近场尾流、powered source 和 A4MC L2 请求/结果边界。

当前判断：A4MC 适合作为 Minecraft 环境气动场和体素相互作用的参考层，但不应直接替代无人机桨盘、马达和整机动力学模型。真正的 rotor force model 仍应在 `drone-sim-core` 内实现。

### 3. PropellerArchive 数据链路

围绕用户提供的数据集，已经建立了 PropellerArchive 的 CT/CP/J 数据链路。

已完成内容包括：

- 数据源 fingerprint。

- 曲线形状检查。

- CT/CP/J lookup query envelope。

- interpolation policy。

- lookup execution input、target queue、execution contract。

- reviewed coefficient payload。

- compact/reference table。

- SI 量纲化响应：`T = CT * rho * n^2 * D^4`，`P = CP * rho * n^3 * D^5`，并派生 torque、disk loading、ideal induced velocity。

关键文件：

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/PropellerArchiveCtCpJLookupReviewedCoefficientPayload.java`

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/PropellerArchiveCtCpJLookupReferenceTable.java`

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/PropellerArchiveCtCpJLookupInterpolationPolicy.java`

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/PropellerArchiveCtCpJDimensionalRotorResponse.java`

### 4. OpenFOAM 参考边界

OpenFOAM 对 `sim/lab` 有帮助，但应作为离线 CFD 对照和真值参考，不应成为 Minecraft tick runtime 的直接依赖。

当前已经做了：

- CT/CP/J OpenFOAM validation plan。

- case manifest。

- run setup。

- numerical budget。

- solver quality contract。

- result contract。

- dimensional reference materialization gate/handoff。

关键文件：

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/PropellerArchiveCtCpJOpenFoamValidationPlan.java`

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/PropellerArchiveCtCpJOpenFoamCaseManifest.java`

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/PropellerArchiveCtCpJOpenFoamResultContract.java`

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.java`

## 没有完成的部分

### 可信 DA4002 模型还没有替换 `DronePhysics`

轴向 lookup、SI 响应和机体系 force/torque 模型对象已经完成。尚未完成的是把它当作 Minecraft tick 中现有 rotor force 的替代项；当前只允许离线调用和 shadow comparison。

继续接入前仍需解决的物理缺口是：独立数据或 CFD 对轴向曲面的复核、斜流/侧向力方向模型、反向流，以及非 DA4002 桨的可追溯数据。不能用手感参数填补这些缺口，也不能把轴向投影误当作斜流模型。

### OpenFOAM 还没有实际结果导入

OpenFOAM 当前主要是验证计划、case/result contract 和 reference materialization 边界。还没有做到：

- 导入真实 OpenFOAM case 输出。

- 将 OpenFOAM 结果与 PropellerArchive CT/CP/J 行逐点比较。

- 输出可直接用于后续 rotor fit 的残差表。

### 审计层已经过量

最近几轮确实产生了太多 `Gate`、`Contract`、`Handoff`、`Readiness` 类代码。它们保护了数据可信度，但继续堆审计已经不能明显推进物理模型。

后续应只在实现具体物理模型时添加小而必要的验证，不再单独新建大批“还不能改变 runtime”的 gate。

## 下一步优先级

当前优先级从“继续搭结构”改为“冻结基线并补实证”：

1. 将 DA4002 轴向曲面、N/W/Nm 和机体系向量闭合作为已实现基线，不再为它新增独立 gate。

2. 用 UIUC 原始行、现有 curve exporter 和独立实验/CFD 结果复核 hover、mid-J、high-J 曲线及残差；OpenFOAM 只作离线对照。

3. 有可信斜流或反向流数据后再扩展 force envelope；此前保持 reference-only/blocked。

4. 若后续考虑接入 `DronePhysics`，先做不改状态的 shadow comparison，再依据误差和覆盖率决定是否替换。

5. `playable/dev` 目前只可消费已标明 DA4002 几何及 J/RPM 有效域的轴向有界曲线、量纲公式和零推力趋势，不可消费负推力尾段、斜流投影或跨桨外推。

## 本次清理

按用户要求，只清理了 Codex 自身和当前仓库的缓存/构建产物，没有清用户全局开发缓存。

已清理：

- `%USERPROFILE%\.codex\.tmp`

- `%USERPROFILE%\.codex\tmp`

- `%USERPROFILE%\.codex\cache`

- `%USERPROFILE%\.codex\.sandbox`

- `%USERPROFILE%\.codex\worktrees\d27d`

- `.gradle`

- `build`

- `drone-sim-core/build`

- `fabric-mod/build`

没有清理：

- 用户级 `Temp`

- 用户级 `npm-cache`

- 用户级 `pip` cache

- 用户级 Gradle cache

- Codex sessions、sqlite、plugins 主体

释放量约为：C 盘 279 MB，当前仓库所在 F 盘 276 MB。
