# Flight Model Convergence V1

## 2026-06-22 blackbox/config 边界增量

- `SimulationFlightRuntime.blackboxSample(...)` 已承接 blackbox 采样所需的 `DroneState`、`DroneConfig`、average motor power 和 average rotor health 读取。
- `DroneEntity.config()` 与 `saveConfig(...)` 改为通过 `SimulationFlightRuntime.currentConfig()` 明确跨 runtime 边界访问配置。
- 本增量不改变 CSV 字段、配置保存字段、公开 `DroneEntity.config()` 返回值或 blackbox 记录时机。

## 2026-06-22 prop strike / collision damage 投影增量

- `SimulationFlightRuntime.PropStrikeState` 已承接 prop strike 采样所需的机体速度、rotor geometry 和 motor omega。
- `SimulationFlightRuntime.exposedRotorIndex(...)` 已承接碰撞损伤按 impact direction 选择暴露 rotor 的 config/orientation 读取。
- 本增量只移动 prop strike / collision damage 边界，保留原有 tip speed、frame speed、surface scrape、cooldown、damage severity 和 frame health 语义。

## 2026-06-22 movement/collision 投影增量

- `SimulationFlightRuntime.MovementState` 已承接实体移动前的目标位置和碰撞前速度读取。
- `SimulationFlightRuntime.contactAngularVelocityImpulseBody(...)` 已承接接触角冲量所需的 config/orientation 读取。
- 本增量只移动 `applyPhysicsMovement(...)` 的读取边界，保留原有 Minecraft `move(...)`、simple contact、advanced contact、damage 和 state correction 语义。

## 2026-06-22 rotor environment geometry 投影增量

- `SimulationFlightRuntime.RotorGeometry` 已承接 rotor 列表、body X/Z 世界方向和 rotor 世界偏移投影。
- 降雨湿度、水浸、rotor ground/ceiling surface sample、侧向遮挡采样不再直接读取 simulation state/config。
- 本增量只移动 rotor environment 几何与 ground/ceiling multiplier 边界，保留原有采样权重、disk radius、obstruction scan 和 thrust multiplier 公式。

## 2026-06-22 drone wake 投影增量

- `SimulationFlightRuntime.DroneWakeSource` 已承接 wake source 的平均电机功率、平均诱导速度、机体速度和尾流半径计算。
- `DroneEntity` 的 `sampleDroneWakeAirflow(...)` / `wakeFromDrone(...)` 不再直接读取其他无人机的 simulation state/config。
- 本增量只移动尾流源读取边界，保留原有 vertical drop、wake radius、motorPower、inducedVelocity、carrierVelocity 和 turbulence 公式。

## 2026-06-22 ground/takeoff 投影增量

- `SimulationFlightRuntime` 已承接 ground/ceiling clearance 射线长度、ceiling effect height、低速下沉判断、起飞油门阈值、垂直推力阈值和起飞释放运动学写入。
- `DroneEntity` 的 ground sleep、ground spool、takeoff release 判断不再直接读取 simulation state/config。
- 本增量只移动 ground/takeoff 边界，保留原有 `0.05 m/s` 下沉判断、`0.95 * hoverThrottle`、`TAKEOFF_THRUST_TO_WEIGHT` 和 release vertical-speed 语义。

## 2026-06-22 direct/playable 输入与遥测投影增量

- `SimulationFlightRuntime` 已承接 owner control input、flight model context config、playable/simulation snapshot、direct failsafe hover throttle 和 direct per-rotor telemetry 预览。
- `DroneEntity` 的 direct/playable 调试飞行路径不再直接把 `DroneState`/`DroneConfig` 传给控制输入、快照和每桨遥测计算。
- 本增量只移动读取边界，保留原有 failsafe throttle clamp、rotor mixer preview、actuator fallback 和模型输出角速度语义。

## 2026-06-22 rotor health 投影增量

- `SimulationFlightRuntime.RotorHealthState` 已承接 rotor health / average rotor health 的 damage sync 读取。
- `DroneEntity` 的 airworthy 判断、repair 后同步、fault 注入下标校验和 `updateDamageSyncedState(...)` 不再直接读取 simulation state/config。
- 本增量只移动健康状态读取边界，不改变伤害、修复、prop strike 计数或同步字段。

## 2026-06-22 layout/config 投影增量

- `SimulationFlightRuntime` 已承接 airframe dimensions、synced rotor count、rotor layout code 和 config rotor-count comparison。
- `DroneEntity.getDimensions(...)`、`syncAirframeLayout(...)` 与配置切换判断不再直接读取 `simulationRuntime.config()`。
- `saveConfig(...)` 仍保留显式配置字段序列化，后续应作为单独 config snapshot 收敛，不与 layout 投影混合。

## 2026-06-22 存档投影 snapshot 增量

- `SimulationFlightRuntime.PersistenceState` 已承接 `DroneEntity.addAdditionalSaveData(...)` 中的 battery、powertrain thermal 与 rotor health 持久化读取。
- `SimulationFlightRuntime.BatteryTransientState`、`rotorCount()` 与 `motorCount()` 已用于 load/save 辅助路径，减少 entity 层直接读取 simulation state/config 的存档投影。
- 本增量不修改任何存档 key、默认值或恢复顺序；dynamic/aerodynamic transient 字段仍沿用既有专用 snapshot。

## 2026-06-22 同步遥测 snapshot 增量

- `SimulationFlightRuntime.SyncedFlightTelemetry` 已承接 `DroneEntity.updateSyncedFlightState(...)` 中的 HUD/网络同步遥测读取与单位投影。
- `DroneEntity.updateSyncedFlightState(...)` 现在只消费 runtime snapshot 并写入 `entityData`，该方法内部不再直接读取 `simulationRuntime.state()` 或 `simulationRuntime.config()`。
- 本增量不改变任何 `entityData` 字段、存档字段、网络协议字段、飞行参数或 golden trace 容差；当时尚未覆盖的环境采样、碰撞/接触几何、layout、存档和调试记录投影已在后续增量中继续收敛。

## 2026-06-22 简单诊断 getter 投影增量

- `SimulationFlightRuntime` 已承接 ESC frame/error、rotor damage/dynamic inflow/coaxial/icing、airframe drag、battery resistance scale、control frame 和 gyro notch/blade-pass 等纯 scalar 诊断 getter。
- `DroneEntity` 对这些 public getter 仅保留委托，不再直接读取对应 `DroneState` 字段。
- 本增量不触碰 tick 同步、环境采样、碰撞几何、存档恢复或任何飞行/手感参数；仍属于读侧边界收敛。

## 2026-06-22 读侧遥测投影增量

- `SimulationFlightRuntime` 已承接电机 RPM 遥测、RPM 有效性、Betaflight eRPM/100 和 e-period 微秒投影。
- `DroneEntity` 的对应 public getter 现在只委托给 `SimulationFlightRuntime`，不再直接读取 `DroneState` 的 RPM 遥测字段，也不再在实体层计算 motor pole-pair 或 Betaflight 协议投影。
- `DroneEntityFlightModelRoutingTest` 新增边界断言，防止 RPM/Betaflight 遥测投影重新散落回实体层。
- 本轮没有修改飞行参数、控制参数、相机参数、气动模型或 golden trace 容差。
- 本增量之后尚未覆盖的环境采样、碰撞几何、存档字段、layout 信息和 HUD/诊断 telemetry 已在后续增量中继续收敛成 runtime/telemetry snapshot 与显式投影入口。

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

已审计以下直接状态写入入口。分类定义：

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

### 已识别写入点

| 文件 | 行为入口 | 写入对象 | 分类 | 当前处理 |
| --- | --- | --- | --- | --- |
| `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java` | `step` 线性积分 | position、world velocity | A | 由 `SimulationFlightModelAdapter` 通过 `FlightStepResult.nextState` 暴露 |
| `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java` | `step` 角速度/姿态积分 | quaternion、body angular rate | A | 由 `SimulationFlightModelAdapter` 通过 snapshot 暴露 |
| `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java` | `sleepAtRest`、`constrainAtRest`、`levelAtRest` | position、velocity、attitude、angular rate | B/C | entity 调用后通过 facade 输出 `GROUND_STABILIZATION`；核心内部事件边界仍需细化 |
| `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java` | attitude estimator | estimated orientation | A | 非权威姿态，保留为 diagnostics/telemetry |
| `fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java` | direct playable 初始化 | physics position | C | 后续由 playable adapter initialize/reset 承接 |
| `fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java` | `applyDebugFlight` idle clear | entity delta movement、debug velocity | E | `LegacyPlayableFlightModelAdapter` 已输出 `GROUND_STABILIZATION / IDLE_CLEAR` |
| `fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java` | `applyDebugFlight` yaw/pitch write | entity yaw、entity pitch | A/E | 后续 facade 接入时保留为 render/entity projection，不作为权威 quaternion |
| `fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java` | `applyDebugMovement` | entity move、physics position、physics velocity | A/B | 通过 `applyResolvedFlightModelState` 回灌；无碰撞为 `NORMAL_INTEGRATION`，碰撞为 `COLLISION_CONTACT_SOLVE` |
| `fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java` | `clearDebugFlightState` | physics position、velocity、direct telemetry | C/E | 通过 playable facade 回灌 `RESET_TELEPORT / DIRECT_CLEAR` |
| `fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java` | `prepareGroundTakeoff` | position nudge、minimum vertical velocity | E | 通过 simulation facade 回灌 `GROUND_STABILIZATION / TAKEOFF_RELEASE` |
| `fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java` | `applyPhysicsMovement` | entity move、physics position | A/B | 通过 `applySimulationResolvedState` 回灌；无碰撞为 `NORMAL_INTEGRATION`，碰撞为 `COLLISION_CONTACT_SOLVE` |
| `fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java` | horizontal/vertical collision solve | velocity、delta movement、angular rate | B | 简化接触与高级接触均输出 `COLLISION_CONTACT_SOLVE` |
| `fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java` | entity replacement / layout switch | position、velocity、orientation、estimated orientation、angular rate | C | 后续需要 `MODEL_INITIALIZATION` 或 `RESET_TELEPORT` |
| `fabric-mod/src/main/java/com/tenicana/dronecraft/entity/LegacyPlayableFlightModelAdapter.java` | initialize/reset from canonical snapshot | position、local velocity、Euler projection | C/F | diagnostics 记录 `initial_state.euler_attitude_projection` |
| `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/flight/SimulationFlightModelAdapter.java` | initialize/reset from canonical snapshot | position、velocity、quaternion、estimated orientation、angular rate | C | reset 已输出 `RESET_TELEPORT` |

### 尚需收敛的 correction

- `DroneEntity` 替换机体布局时复制旧状态属于 model selection/init，应输出 `MODEL_INITIALIZATION`。
- `DronePhysics.sleepAtRest`、`constrainAtRest`、`levelAtRest` 目前仍是核心方法内部状态写入；entity 层已补充 state correction event，后续需要继续把核心内部事件拆细。
- Playable adapter 已记录 wind、air density、turbulence 等无法表达的 environment 字段为 lossy diagnostics；后续 convergence report 必须保留这类 loss map。

## 验证入口

第一阶段最小验证：

```powershell
./gradlew :drone-sim-core:test --tests com.tenicana.dronecraft.sim.SimulationFlightGoldenTraceTest
./gradlew :fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightGoldenTraceTest
./gradlew :fabric-mod:flightModelComparison
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

截至 2026-06-22，本分支已完成以下行为保持型收敛工作：

- simulation 路径已通过 `SimulationFlightModelAdapter` 和 `FlightModelRouter` 进入统一 `FlightModel` 边界。
- playable 路径已通过 `LegacyPlayableFlightModelAdapter` 和 `FlightModelRouter` 进入统一 `FlightModel` 边界；`DroneEntity` 不再直接调用 `PlayableFlightModel.*` 或 `DronePhysics.step(...)`。
- `DroneEntity` 不再直接 import、new 或持有 `DronePhysics`；simulation 构造、layout replacement 以及 transient snapshot/restore 已收进 `SimulationFlightRuntime`。
- `DroneEntity` 不再直接写入 simulation position、world velocity、body angular rate、contact telemetry、rotor damage/repair 或 battery persistence state；这些内部可变状态写入已通过 `SimulationFlightRuntime` 方法集中。
- `FlightModel.applyResolvedState(...)` 用于把 Minecraft entity move / collision 后的解析状态显式回灌到当前模型，避免把 entity 层状态写入静默藏在模型外部。
- simulation entity move、ground sleep/level、takeoff release 与 collision contact solve 已经通过 `applySimulationResolvedState(...)` 输出 `NORMAL_INTEGRATION`、`GROUND_STABILIZATION` 或 `COLLISION_CONTACT_SOLVE`。
- `FlightStepContext.modelConfiguration` 仅承载 adapter 级选项；当前用于 failsafe damping 这类旧 playable 路径已有行为，不新增手感或气动物理参数。
- `FlightSerializationCodec` 已为 canonical state、input 和 model id 提供纯 Java 键值序列化 round-trip，缺字段和非有限数会失败。
- `/fpvdiag start|status|stop` 已实现为默认关闭的一键真人录制入口；停止后写入服务器目录 `fpvdiag-traces`，文件名包含开始时间、commit SHA 和玩家 UUID。

本轮已通过的本地验证：

```powershell
./gradlew.bat --no-daemon :drone-sim-core:test --tests com.tenicana.dronecraft.sim.flight.FlightSerializationCodecTest --tests com.tenicana.dronecraft.sim.flight.FlightStateSnapshotTest
./gradlew.bat --no-daemon :fabric-mod:test --tests com.tenicana.dronecraft.entity.SimulationFlightRuntimeTest --tests com.tenicana.dronecraft.entity.DroneEntityFlightModelRoutingTest
./gradlew.bat --no-daemon :drone-sim-core:test --tests com.tenicana.dronecraft.sim.SimulationFlightGoldenTraceTest :fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightGoldenTraceTest --tests com.tenicana.dronecraft.entity.DroneEntityFlightModelRoutingTest --tests com.tenicana.dronecraft.entity.SimulationFlightRuntimeTest :fabric-mod:flightModelComparison
./gradlew.bat --no-daemon :drone-sim-core:test --tests com.tenicana.dronecraft.sim.flight.FlightModelRouterTest :fabric-mod:test --tests com.tenicana.dronecraft.command.DroneCommandsTest --tests com.tenicana.dronecraft.blackbox.DroneFlightTraceFilesTest --tests com.tenicana.dronecraft.entity.LegacyPlayableFlightModelAdapterTest --tests com.tenicana.dronecraft.entity.DroneEntityFlightModelRoutingTest --tests com.tenicana.dronecraft.entity.PlayableFlightGoldenTraceTest
./gradlew.bat --no-daemon :drone-sim-core:test :fabric-mod:test
./gradlew.bat --no-daemon :fabric-mod:flightModelComparison
./gradlew.bat --no-daemon build
./gradlew.bat --no-daemon :fabric-mod:runServerSelfTest
./gradlew.bat --no-daemon :fabric-mod:runPlayableServerSelfTest
./gradlew.bat --no-daemon :fabric-mod:runPlayableHorizonServerSelfTest
./gradlew.bat --no-daemon :fabric-mod:runPlayableAcroServerSelfTest
```

尚未完成的收敛门禁：

- `DroneEntity` 已不再直接调用 `SimulationFlightRuntime.state()` / `config()` 读取 simulation state/config；telemetry、环境采样、碰撞几何、存档字段和 layout 信息均通过 `SimulationFlightRuntime` 的显式投影入口读取。
- 后续仍可继续把 runtime 内部的 canonical state/config 所有权做得更细，例如 replacement/model initialization 的 correction 颗粒度、核心模型内部 state correction 事件和可选的纯 `ConfigSnapshot`。

任何后续契约、适配器、路由或诊断改动都必须先通过上述 golden trace，证明 playable 与 simulation 的现有输出没有因重构漂移。

## 最终收敛报告

### 已完成的行为保持型架构收敛

- 新增纯 Java `FlightModel` 契约族：`FlightStepContext`、`FlightStepResult`、`FlightStateSnapshot`、`FlightModelCapabilities`、`StateCorrection`、`FlightModelDiagnostics`。
- `SimulationFlightModelAdapter` 包装 `DronePhysics`，`LegacyPlayableFlightModelAdapter` 包装 `PlayableFlightModel`；两者均不修改原数值算法。
- `FlightModelRouter` 已成为 playable 与 simulation 的统一 step/snapshot/diagnostics 入口。
- `DroneEntity` 的 playable tick 不再直接调用 `PlayableFlightModel.step(...)`，simulation tick 不再直接调用 `DronePhysics.step(...)`；entity movement/collision 后的解析状态通过 `FlightModel.applyResolvedState(...)` 回灌。
- `SimulationFlightRuntime` 已把 `DronePhysics` 的构造、替换、transient state snapshot/restore 和 Betaflight telemetry 编码从 `DroneEntity` 中移出。
- `SimulationFlightRuntime` 已承接 `DroneEntity` 原先对 position、velocity、angular rate、contact telemetry、rotor damage/repair 和 battery persistence 的直接写入。
- 默认 playable 路径、显式 simulation 路径、现有 debug/self-test 配置、网络协议和存档字段保持兼容。
- 结构性重构前建立了 playable 与 simulation golden trace，并用重构后测试持续比较，避免把行为漂移合法化。
- `/fpvdiag start|status|stop` 已实现为默认关闭的真人录制入口，不启动时没有持续磁盘写入；停止后写入 `fpvdiag-traces`。
- `FlightSerializationCodec` 覆盖 canonical model id、input 和 state 的序列化 round-trip，防止模型选择、输入或状态字段静默丢失。
- `FlightModelComparisonRunner` 可以离线运行同一 canonical 场景下的 playable/simulation shadow comparison，并输出逐 tick 差异。

### 客观修复的 bug

- 修正 simulation wind golden trace 的基线文件内容，使其与重构前记录器输出一致；这是基线采集/文件同步问题，不是飞行参数改动。
- `SimulationFlightModelAdapter.applyResolvedState(...)` 现在只在 reset/teleport/model initialization/network correction 时同步 attitude estimator，普通 entity move/contact correction 保留 estimator 的旧路径演化；该修复由 adapter 测试约束，避免 state correction 误伤仿真内部估计器。
- 新增序列化缺字段/非有限数拒绝测试；当前实现会在缺字段、`NaN`、`Infinity` 或非法布尔/模式值时失败。

### 只发现但未修改的问题

- `DroneEntity` 仍保留公开 `config()` 兼容入口和既有存档字段语义，但 entity 层现在通过 `SimulationFlightRuntime.currentConfig()` 读取；未来若要进一步纯化，可以引入完整 `ConfigSnapshot`，但本阶段为兼容网络、存档和旧调用方没有移除这些公开 API。
- `DroneEntity` 替换机体布局时复制旧状态属于 model initialization/reset 类事件；当前已在审计中列出，仍需后续把这一类 replacement 更完整地纳入 `StateCorrectionReason.MODEL_INITIALIZATION`。
- `DronePhysics.sleepAtRest`、`constrainAtRest`、`levelAtRest` 仍是核心模型内部的直接状态写入；entity 层已经在调用后补充 `GROUND_STABILIZATION` correction，但核心内部事件边界还可以继续细化。
- playable adapter 对 wind、air density、turbulence 等 environment 字段仍是 lossy diagnostics，因为旧 playable 模型本身没有等价输入；本阶段只记录，不猜测性改物理。
- shadow comparison 显示两套模型从同一 canonical state 起步会快速产生明显差异，说明它们是不同语义模型；这些差异不能自动用于判断哪一套“手感更好”。

### 两套模型的差异

- playable 以游戏内可玩手感和辅助修正为中心，状态中存在 Euler/render projection、failsafe damping、direct telemetry restore 等路径。
- simulation 以 `DronePhysics` 的高保真动力学为中心，包含电机、ESC、电池、转子气动、IMU/估计器、环境与接触遥测等大量内部状态。
- playable adapter 的 actuator output 主要来自直接遥测/旧模型结果；simulation adapter 可以暴露更细的 motor、rotor thrust、force/torque 和 powertrain diagnostics。
- playable 对部分 environment 输入无损表达能力不足；simulation 对环境、碰撞、动力系统 transient 的内部状态更完整。
- 两套模型的 shadow delta 是研究语义距离的工具，不是本阶段的调参依据。

### 未来可以共享的模块

- canonical input/state/frame serialization。
- world/body 坐标转换与 quaternion 工具。
- `StateCorrection` 事件分类和 trace schema。
- shadow scenario runner、golden trace recorder、finite/quaternion/dt 不变量测试。
- actuator/motor telemetry 的公共投影层。
- 录制文件命名、commit SHA 解析和诊断输出格式。

### 未来不能在没有人工试飞时决定的项目

- playable 与 simulation 谁应该作为最终默认手感模型。
- rate/expo/deadband、辅助阈值、速度包线、相机参数和 FPV 视觉舒适度。
- 是否削弱或删除 playable assist，例如回正、速度限制、ground stabilization。
- 气动系数、drag、sidewash、crossflow、dynamic inflow、gyro、flapping、thrust loss 等参数取值。
- 哪些 shadow delta 属于“错误”，哪些只是两套模型语义不同。
- 高速穿越机模式和大疆式自稳模式之间的产品体验取舍。

### 第二阶段候选顺序

1. 统一坐标和状态所有权。
2. 统一刚体积分。
3. 统一环境与碰撞输入。
4. 统一推进器/电机接口。
5. 判断气动实现是否适合共享。
6. 将 playable 差异逐步限制为 controller、assist 和明确的 `StateCorrection`。

### 第一阶段交付状态

- 分支：`refactor/unified-flight-contract-v1`
- 分支地址：`https://github.com/SakalioLabs/FPV-Dronecraft/tree/refactor/unified-flight-contract-v1`
- 当前结论：行为保持型契约、adapter、router、golden trace、shadow comparison、一键录制器、round-trip 测试和 `SimulationFlightRuntime` 读写侧投影边界已经落地；更深层的 canonical state/config 所有权统一仍作为第二阶段首要候选继续推进。
