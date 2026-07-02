# sim/lab 进度说明 2026-07-03

本文用于人工交接，回答“这几天做了什么、哪些没有做完、下一步应该怎么收束”。它不是新的审计清单。

## 当前状态

- 仓库：`SakalioLabs/FPV-Dronecraft`

- 分支：`sim/lab`

- 远端：`origin` 是原仓库，不使用 fork。

- 协作方式：用户明确要求不要 PR，不要 fork；有效进度直接提交并推送到 `origin/sim/lab`。

- 研发路线：`sim/lab` 只追求真实性、可验证性和可复现性；`playable/dev` 只在仿真结论成熟后消费参考数据或低成本近似。

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

### CT/CP/J lookup 还没有进入运行时物理

目前有 lookup 输入、合同、参考表和 SI 响应转换，但还没有形成一个可在 `DronePhysics` 或明确 rotor model 中调用的 runtime evaluator。

也就是说：

- 已经有数据链路和参考行。

- 已经有量纲化公式响应。

- 还缺“给定空气密度、桨径、转速、轴向来流，实时查表/插值并返回 thrust、power、torque”的模型对象。

- 还没有把它接到 `DronePhysics` 当前 rotor force 计算路径中。

### OpenFOAM 还没有实际结果导入

OpenFOAM 当前主要是验证计划、case/result contract 和 reference materialization 边界。还没有做到：

- 导入真实 OpenFOAM case 输出。

- 将 OpenFOAM 结果与 PropellerArchive CT/CP/J 行逐点比较。

- 输出可直接用于后续 rotor fit 的残差表。

### 审计层已经过量

最近几轮确实产生了太多 `Gate`、`Contract`、`Handoff`、`Readiness` 类代码。它们保护了数据可信度，但继续堆审计已经不能明显推进物理模型。

后续应只在实现具体物理模型时添加小而必要的验证，不再单独新建大批“还不能改变 runtime”的 gate。

## 下一步优先级

最高优先级是把现有 CT/CP/J 和 SI 响应链路收束成可运行物理模型：

1. 实现 CT/CP/J lookup evaluator。

2. 实现 rotor force、shaft power、torque runtime sample。

3. 用 PropellerArchive dimensional response 做 focused golden tests。

4. 形成 hover、forward flight、高 advance ratio 下的力和功率曲线。

5. 模型稳定后，再给 `playable/dev` 输出小型参考资料，例如简化公式、查表压缩建议、误差范围和性能成本。

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
