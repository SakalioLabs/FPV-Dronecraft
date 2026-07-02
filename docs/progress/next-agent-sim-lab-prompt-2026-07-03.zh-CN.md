# 下一任 agent 提示词 2026-07-03

请把下面内容交给接手 `sim/lab` 的 agent。

## 任务身份

你正在接手 `SakalioLabs/FPV-Dronecraft` 的 `sim/lab` 分支研发。

`sim/lab` 是物理仿真路线，目标是真实、可验证、可复现。`playable/dev` 是游戏娱乐路线，目标是好玩、低性能成本、稳定手感。不要因为要帮助 `playable/dev`，就把 `sim/lab` 改成手感调参分支。

只有当某个物理特性在 `sim/lab` 中已经有可信模型、数据或曲线后，才给 `playable/dev` 输出参考模块、近似公式或数据资料。

## 硬性协作规则

- 只在原仓库 `SakalioLabs/FPV-Dronecraft` 工作。

- 分支是 `sim/lab`。

- 不要 fork。

- 不要创建 PR。

- 每次完成有效改动后，直接 commit 并 push 到 `origin/sim/lab`。

- 不要把任何 token、邮箱鉴权、GitHub 个人访问令牌前缀或用户私密信息写入仓库。提交前必须搜索常见密钥前缀和本次线程中出现过的私密账号标识，确认没有落盘。

## 重要提醒

- 上一阶段已经做了太多 `Gate`、`Contract`、`Handoff`、`Readiness` 类审计层。

- 接下来不要继续无止境新建审计。

- 可以有小型验证，但必须服务于一个正在实现的物理模型。

- 当前最需要的是可运行的 CT/CP/J lookup 和 rotor force model，不是更多证明“还不能接 runtime”的文件。

## 先读文件

- `docs/progress/sim-lab-progress-2026-07-03.zh-CN.md`

- `docs/aerodynamics4mc-integration.md`

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java`

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneConfig.java`

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/RotorSpec.java`

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/PropellerArchiveCtCpJLookupReviewedCoefficientPayload.java`

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/PropellerArchiveCtCpJLookupReferenceTable.java`

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/PropellerArchiveCtCpJLookupInterpolationPolicy.java`

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/PropellerArchiveCtCpJDimensionalRotorResponse.java`

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.java`

## 立即执行的实现任务

### 1. 实现 CT/CP/J lookup evaluator

输入应包括 advance ratio J、RPM、桨径或半径、空气密度、可选 preset/case。

输出应包括 CT、CP、eta、插值状态、是否 clamp 或 blocked。

优先使用已经 reviewed/accepted 的 PropellerArchive payload/reference table。插值先做保守线性或分段线性，范围外必须明确 clamp 或返回 blocked，不要静默外推。

### 2. 实现 rotor dimensional sample

使用 `T = CT * rho * n^2 * D^4` 计算推力。

使用 `P = CP * rho * n^3 * D^5` 计算轴功率。

使用 `torque = P / omega` 计算扭矩。

派生 disk loading、ideal induced velocity、momentum power ratio。

输出应能与 `PropellerArchiveCtCpJDimensionalRotorResponse` 的当前参考值对齐。

### 3. 添加 focused tests

测试至少覆盖：

- hover/static anchor。

- mid advance ratio。

- high advance ratio。

- blocked/out-of-envelope query。

- 单位和量纲检查。

测试数量要少而硬，不要再扩成一大片审计矩阵。

### 4. 暂时不要急着接入 playable/dev

先让 `sim/lab` 能生成可信的力、功率、扭矩曲线。

只有通过测试后，再写一份很短的 playable reference note，说明哪些曲线可用于低成本拟真手感，哪些不能。

### 5. OpenFOAM 的使用方式

OpenFOAM 对本项目有帮助，但把它当离线 CFD 对照，不要让 Minecraft runtime 依赖 OpenFOAM。

可以做 importer 或 reference comparison，但优先级低于 CT/CP/J runtime lookup。

若做 OpenFOAM，目标应该是“导入真实结果并输出 residual/reference comparison”，不是再写新的 gate。

## 验收标准

- 有一个实际可调用的 lookup/model 类，而不只是新的 contract。

- 有测试证明 hover、forward flow、高 advance ratio 至少三类样本能得到有限、量纲正确、趋势合理的 N/W/Nm 输出。

- 没有新增一串独立 gate。

- 至少运行 `./gradlew :drone-sim-core:test` 中与新增代码相关的测试；时间允许再跑完整 `./gradlew build`。

- commit 并 push 到 `origin/sim/lab`。
