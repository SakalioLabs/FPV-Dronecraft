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

## M0-M13 收敛路线

这条路线是后续工作的固定顺序，不再用新的 Gate、Contract、Handoff 或 Readiness 类替代物理实现。每个节点完成后更新本节状态，并直接提交、推送到 `origin/sim/lab`。

| 节点 | 状态 | 收敛产物 |
| --- | --- | --- |
| M0 数据与查表基线 | 已完成 | 32 个静态点、112 个前飞点、有界 J/RPM 插值和显式 blocked |
| M1 SI 量纲模型 | 已完成 | CT/CP/eta、N/W/Nm、盘载、诱导速度、动量功率和 Re75 |
| M2 机体系力模型 | 已完成 | 推力、轴反扭矩、`r x F`、总力矩及 reference/applied 边界 |
| M3 插值误差闭环 | 已完成 | 内部行留出、名义 RPM 轨道留出、残差 CSV、零推力区间和误差摘要 |
| M4 独立轴向验证 | 已完成 | 已发表 CFX 逐点结果、OpenFOAM 摘要锚点及同量纲残差 |
| M5 物理差异修正 | 已评估，不执行 | CFD 模型间差异不足以支持覆盖直接风洞测量的修正项 |
| M6 DA4002 axial surface v1 | 已完成 | 已冻结源数据/算法/曲线指纹、非矩形包络、Re75 诊断范围和确定性 bundle |
| M7 只读 runtime shadow adapter | 已完成 | 从 state/config/environment 构造逐转子 v1 查询与残差，不修改状态、不施力 |
| M8 shadow 场景验证 | 下一节点 | hover、mid-J、高 J、零推力附近、倾斜轴和旋向的残差 |
| M9 runtime 决策 | 条件执行 | 证据充分则进入 M10；否则收敛为离线轴向参考 v1 |
| M10 条件式 sim/lab runtime 接入 | 条件执行 | 仅精确 DA4002 实验配置，包络外不得静默外推或 clamp |
| M11 集成物理闭合 | 条件执行 | `Q omega = P`、马达负载、力矩、时间步收敛和确定性重放 |
| M12 playable 参考输出 | 条件执行 | 一份短说明和紧凑有界曲线，不新增 handoff/gate 代码 |
| M13 最终收口 | 待开始 | 文档、最小关键回归、完整 build、泄漏扫描和远端同步 |

允许两种合法终态：一是证据不足时冻结为可信但不接 runtime 的 `DA4002 axial reference v1`；二是独立验证和 shadow 均通过后形成严格包络内的 `DA4002 sim/lab runtime v1`。Runtime 接入不是为了“看起来完成”而必须发生，未验证区域始终保持 reference-only 或 blocked。

### M3 插值误差闭环结果

`UiucDa4002MeasuredRotorCrossValidation` 调用生产 lookup 做真实留出：静态和前飞源行预测会先删除目标行，名义 RPM 轨道预测只使用上下相邻轨道。`./gradlew :drone-sim-core:uiucDa4002CrossValidation` 在 `build/uiuc-da4002-cross-validation` 生成一份 170 行 residual CSV 和人读摘要。

- 28 个静态内部行和 94 个前飞内部行全部得到相邻点支撑。两种桨的静态平均绝对 CT 误差为 `0.001058-0.001105`，前飞平均绝对 CT 误差为 `0.000513-0.000645`；对应非零推力邻域平均 CT 相对误差约为 `0.84-0.85%` 和 `1.14-1.15%`。

- 名义 RPM 轨道有 49 个候选点，其中 48 个有共同上下轨道支撑。平均绝对 CT 误差为 `0.000798-0.000867`，非零推力邻域平均相对误差为 `1.97-2.94%`。唯一 blocked 候选是 9x6.75 名义 3000 RPM、`J=0.887498`；上邻 4000 RPM 轨道已超出其 J 包络，因此没有外推。

- 全部留出结果的最大 SI 误差为 `0.0411012 N`、`0.639855 W` 和 `0.00152754 Nm`。五条源曲线具有实测 CT 符号变化区间，分段线性零推力交点位于 `J=0.846649-0.891173`。符号变化相邻行只汇总绝对 CT 误差，不用接近零的分母制造夸大的百分比。

这些结果证明当前分段线性策略在已有数据内部的重建误差和覆盖边界，但不能证明独立气动精度。M4 必须使用真实台架或实际求解结果；没有外部结果时，本路线停在有界轴向参考，而不是继续生产审计文件。

### M4 独立轴向验证与 M5 决策

`UiucDa4002PublishedCfdComparison` 将 DA4002 9x6.75 实测曲面与 Oliveira 2019 年 UFJF 硕士论文《Simulação em Dinâmica dos Fluidos Computacional de Hélices》的已发表 CFD 结果放到相同定义下比较。论文 Table 11 给出 2000 RPM、`J=0.0/0.2/0.4/0.5/0.6` 的两组 CFX 湍流模型逐点 `KT/KP/eta/T/Q`，Table 17 给出 OpenFOAM 的静态 `KT/KP` 和最大效率摘要。比较严格使用 `J=V/(nD)`、`T=KT rho n^2 D^4`、`P=KP rho n^3 D^5`、`Q=P/omega`，并以由自洽载荷行反推的 `rho=1.18 kg/m^3` 生成同密度 N/W/Nm 残差。`./gradlew :drone-sim-core:uiucDa4002PublishedCfdComparison` 可复现 CSV 和 Markdown 摘要。

- 10 个 CFX 点全部落在 9x6.75 实测曲面的严格包络内。9 个源行的 T/KT 与 Q/KP 反推密度在 3% 内闭合；k-omega `J=0.2` 行原文打印 `0.076 Nm`，相对同一行 KP 大约高一个数量级，导出保留原值、标记不自洽且不静默修正。

- 相对 k-epsilon，实测曲面的平均绝对 CT/CP 残差为 `0.015774/0.011077`，最大值为 `0.036135/0.020379`；同密度最大 T/P/Q 残差为 `0.129383 N`、`0.556016 W`、`0.002655 Nm`。

- 相对 k-omega，平均绝对 CT/CP 残差为 `0.004354/0.017397`，最大值为 `0.012774/0.028079`；同密度最大 T/P/Q 残差为 `0.045736 N`、`0.766099 W`、`0.003658 Nm`。OpenFOAM 舍入摘要相对实测曲面的静态 CT、静态 CP 和 `J=0.6` 效率差分别为 `-0.001226`、`+0.007979` 和 `+0.085721`。

这组证据是独立数值复核，不是比直接 UIUC 风洞测量更高优先级的“真值”。两种 CFX 湍流模型之间已存在明显 CT/CP 分歧，OpenFOAM 又没有可逐点读取的数值表，因此 M5 不添加经验修正或手感拟合。M4 的外部覆盖只到 9x6.75、2000 RPM、轴向 `0 <= J <= 0.6`；5x3.75、斜流和反向流仍未获得独立验证。

### M6 DA4002 axial surface v1 冻结

`UiucDa4002AxialSurfaceV1` 是实测轴向模型的稳定可调用入口，不改变 `UiucDa4002MeasuredRotorModel` 的数值。它冻结 2 套 UIUC 几何、32 个静态行、9 条前飞源曲线的 112 个数据行及其来源元数据；规范化源数据 SHA-256 为 `abf67ed5ba706cba92f97fc18834e846ee7241a03a1179d09c381969421951ad`，插值算法 SHA-256 为 `2d0457e49d0032d33d896890a92362fd1b324a3467e2f902106a14fc97ece8e5`。

- v1 显式公开非矩形包络：5x3.75 静态 RPM 为 `1410-7440`、前飞名义轨道为 `4000/5000/6000`，轨道最大 J 为 `0.857870/0.851340/0.895451`；9x6.75 静态 RPM 为 `1546.667-5943.333`、前飞轨道为 `2000/3000/4000/5000`，轨道最大 J 为 `0.894262/0.887498/0.865364/0.914534`。相邻 RPM 的有效最大 J 取两条相邻轨道较小值，J=0 单独使用静态 RPM 包络，范围外始终 blocked、不 clamp、不外推。

- 标准导出环境固定为 `rho=1.225 kg/m^3`、`mu=1.81e-5 Pa*s`。该环境下 5x3.75 的静态/前飞 resultant Re75 范围为 `5452-28768` / `15466-24819`，9x6.75 为 `19172-73673` / `24792-66484`。Re75 是随 RPM、J 和环境派生的诊断范围，不是独立插值轴，也不扩大实测包络。

- `./gradlew :drone-sim-core:uiucDa4002AxialSurfaceV1` 输出 12 条名义/中间 RPM 固定切片，每条从 J=0 到 1、步长 0.025，共 41 个样本并保留显式 blocked 行。每个 CSV 有独立 SHA-256，整体 curve bundle SHA-256 为 `49f20e2f7ea42771ce07bc2b4b1f371b54e6966616921da09c8bbf82612043cf`；manifest 同时带 M3 内部误差上界和 M4 已发表 CFD 残差范围。

M6 只冻结离线/可调用轴向参考，不代表斜流、反向流或任意桨 runtime 已验证，也没有修改 `DronePhysics`。下一步 M7 只允许从运行状态构造 v1 查询并输出 shadow residual，不允许修改状态或施加 v1 力。

### M7 只读 runtime shadow adapter

`UiucDa4002AxialSurfaceV1RuntimeShadowAdapter` 从现有 `DroneConfig`、`DroneState` 和 `DroneEnvironment` 构造逐转子 v1 shadow query。坐标映射与现有 runtime 一致：先将 `(vehicle velocity - local rotor wind)` 从世界系旋到机体系，再加 `omega_body x (rotor position - center of mass)`；沿各自 `thrustAxisBody` 得到轴向速度和 J，RPM 直接读取 state 的机械转速。

- 只有显式选择的 DA4002 桨与每个 `RotorSpec` 的直径、名义螺距和叶片数全部匹配时才查询 v1。5x3.75 要求 5 英寸、3.75 英寸螺距、两叶；9x6.75 要求 9 英寸、6.75 英寸螺距、两叶。零 RPM、几何错配、反向轴流和曲面包络外分别显式 blocked。

- 纯轴向正推力输出可比较的 reference force/torque；斜流只输出轴向投影标量参考并标为 `OBLIQUE_AXIAL_PROJECTION_REFERENCE`，不声称侧向力方向可信；实测非正推力尾段仍是 reference-only。每个转子及总计都输出实际 telemetry 与 reference 的推力、轴功率、轴扭矩、机体系力和力矩残差，并单独给出实际 `P-Q omega` 闭合残差。

- adapter 不持有或写回 `DroneState`，也不调用 force sample 的 applied 输出；每个 rotor 和 aggregate 的 `runtimeForceApplied` 恒为 false。focused tests 对调用前后 state 的运动学、RPM、N/W/Nm 和力/力矩数组逐项比对，覆盖 hover 零残差、世界系姿态/局部风/机臂速度映射、旋向、斜流 reference-only、零 RPM、错误桨几何、反向流和高 J blocked。

M7 仍未进入 `DronePhysics.tick`。下一步 M8 使用固定场景批量运行这个只读 adapter，量化可比较率和残差分布，再做 M9 runtime 决策。

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

9x6.75、2000 RPM、`0 <= J <= 0.6` 已有独立发表 CFD 复核，但它不足以覆盖 5x3.75、其余转速、斜流/侧向力方向、反向流或非 DA4002 桨。不能用手感参数填补这些缺口，也不能把轴向投影误当作斜流模型。

### OpenFOAM 还没有实际结果导入

仓库现在能比较已发表 OpenFOAM 摘要和逐点 CFX 结果，但本机没有 OpenFOAM 求解环境，也没有导入可追溯的原始 OpenFOAM case 输出。仍未完成的是：

- 导入真实 OpenFOAM case 输出。

- 将同一 DA4002 case 的原始 OpenFOAM 逐点结果与 UIUC CT/CP/J 曲面比较。

- 输出可直接用于后续 rotor fit 的残差表。

### 审计层已经过量

最近几轮确实产生了太多 `Gate`、`Contract`、`Handoff`、`Readiness` 类代码。它们保护了数据可信度，但继续堆审计已经不能明显推进物理模型。

后续应只在实现具体物理模型时添加小而必要的验证，不再单独新建大批“还不能改变 runtime”的 gate。

## 下一步优先级

当前优先级从“继续搭结构”改为“冻结已复核基线并决定 runtime 边界”：

1. 将 DA4002 轴向曲面、N/W/Nm 和机体系向量闭合作为已实现基线，不再为它新增独立 gate。

2. M8 用固定 hover、mid-J、高 J、零推力邻域、倾斜轴和旋向场景运行只读 shadow，汇总可比较率及 N/W/Nm 残差。

3. 有可信斜流或反向流数据后再扩展 force envelope；此前保持 reference-only/blocked。

4. M7/M8 完成后依据 shadow 覆盖率决定收敛为离线 reference v1，还是进入严格实验配置的 runtime。

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
