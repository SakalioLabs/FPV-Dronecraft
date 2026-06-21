# Flight Model Convergence V1

本文档记录 `refactor/unified-flight-contract-v1` 的第一阶段架构收敛方案。目标是在不改变
`PlayableFlightModel` 与 `DronePhysics` 数值行为的前提下，统一飞行模型契约、状态边界、路由、
诊断和对比工具。

## 当前边界

- 分支基点：`603cf1175a5b2977d5e74ce447ce40b51ed79865`。
- 默认游戏内 playable 路径保持不变。
- `PlayableFlightModel` 保留为 Fabric 模块内的可玩手感模型。
- `DronePhysics` 保留为 `drone-sim-core` 内的仿真模型。
- 本阶段不合并两个数值实现，不删除任一模型，不调整任何飞行手感或气动参数。
- `docs/data`、`docs/scripts` 与其他 agent 的研究文件不属于本分支改动范围。

## 禁止事项

本阶段不得修改以下内容：

- `ACRO_*` 常量；
- drag、sidewash、crossflow、dynamic inflow、gyro、flapping、thrust loss；
- rate、expo、deadband；
- 相机手感参数；
- PID 或辅助阈值；
- 默认 flight model、默认控制模式和玩家当前操作行为。

如后续发现客观 bug，必须先增加失败测试，再单独提交修复。

## 行为基线

在结构性重构前，先保存两套模型的逐 tick 黄金轨迹：

- Fabric playable direct route：`fabric-mod/src/test/resources/golden/flight/playable-direct-v1.csv`
- Core simulation：`drone-sim-core/src/test/resources/golden/flight/simulation-v1.csv`

基线覆盖以下确定性合成场景：

- 静止未解锁；
- 解锁悬停；
- 油门阶跃；
- 单独 pitch 输入；
- 单独 roll 输入；
- 单独 yaw 输入；
- pitch+roll 对角输入；
- 360 度 roll；
- 360 度 pitch loop；
- 高速前飞；
- 横向初速度；
- 风场；
- 地面接触；
- 碰撞/约束；
- reset/respawn；
- 模型选择与初始化。

每 tick 记录：

- 输入；
- position；
- world velocity；
- body velocity；
- quaternion；
- body angular rate；
- flight mode；
- armed/link 状态；
- motor power、平均 RPM、平均 rotor thrust；
- finite 检查；
- 显式 correction 标签。

这些 trace 只证明后续重构是否保持当前行为，不证明当前手感或物理正确。

## Trace Schema

CSV header 固定为：

```text
model,scenario,tick,dt_s,input_throttle,input_pitch,input_roll,input_yaw,input_armed,input_link_active,input_mode,position_x_m,position_y_m,position_z_m,world_velocity_x_mps,world_velocity_y_mps,world_velocity_z_mps,body_velocity_x_mps,body_velocity_y_mps,body_velocity_z_mps,quat_w,quat_x,quat_y,quat_z,body_rate_x_radps,body_rate_y_radps,body_rate_z_radps,motor_power,average_rpm,rotor_thrust_avg_n,flight_mode,armed,finite,correction
```

字符串字段必须完全匹配；数值字段使用预定义的小容差比较，不能在重构后放宽：

- playable trace：绝对容差 `1.0e-5`，相对容差 `1.0e-6`；
- simulation trace：绝对容差 `1.0e-7`，相对容差 `1.0e-7`。

## 统一契约计划

公共契约放在 `drone-sim-core`，不得依赖 Minecraft、Fabric、Entity、`Vec3d` 或客户端类。

拟新增接口和数据结构：

- `FlightModel`
- `FlightStepContext`
- `FlightStepResult`
- `FlightStateSnapshot`
- `FlightModelCapabilities`
- `StateCorrection`

契约使用 SI 单位，显式区分 world/body 坐标，姿态只以 quaternion 作为权威表示，Euler 只允许通过集中工具转换。

## 适配器计划

- `LegacyPlayableFlightModelAdapter`：包装现有 `PlayableFlightModel`，不改变算法。
- `SimulationFlightModelAdapter`：包装现有 `DronePhysics`，不改变算法。
- 无法无损表示的状态写入 diagnostics 和 convergence report，不静默丢弃。

## 路由计划

后续 `DroneEntity` 只面向统一的 `FlightModel` facade，不直接分支访问具体模型内部。模型差异通过
capabilities、adapter diagnostics 和 state correction 暴露，避免在 `DroneEntity` 继续扩散
`if playable ... else simulation ...`。

## 状态写入审计

后续需要审计并分类所有直接状态写入：

- A：正常积分；
- B：碰撞/接触求解；
- C：reset/teleport；
- D：网络修正；
- E：玩家辅助；
- F：未知/遗留。

E 类必须输出显式 `StateCorrection`，例如：

- `COMPLETED_ROLL_VELOCITY_TRIM`
- `ASSISTED_LEVEL_RECOVERY`
- `SPEED_ENVELOPE_LIMIT`
- `GROUND_STABILIZATION`

本阶段先记录 correction 标签，不删除也不改变现有修正行为。

## 验证入口

第一阶段最小验证：

```powershell
./gradlew :drone-sim-core:test --tests com.tenicana.dronecraft.sim.SimulationFlightGoldenTraceTest
./gradlew :fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightGoldenTraceTest
```

完整完成标准仍包括：

- `:drone-sim-core:test`
- `:fabric-mod:test`
- `gradlew build`
- simulation server self-test
- playable angle/horizon/acro server self-tests
- golden comparison
- shadow comparison matrix
- dependency boundary test
- serialization round-trip
- coordinate invariants

## 当前状态

本文件仅描述行为保持型收敛的第一阶段边界。任何后续契约、适配器、路由或诊断改动都必须先通过上述
golden trace，证明 playable 与 simulation 的现有输出没有因重构漂移。
