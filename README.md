# FPV Dronecraft

## 最新进展（2026-06-21，ACRO 高速侧滑风标 yaw 增强，斜飞更少像平移）
这一轮继续沿着“斜向飞行不要像屏幕平移”的主线收敛。复查当前 playable 代码后确认，ACRO 速度已经主要走物理加速度积分，`shouldAirBrake` 不作用于 ACRO，`settledVelocity` 也只是 `0.018m/s` 的近零归零阈值；剩余更像问题的是高速侧滑时的被动风标 yaw 太弱。对照 [RotorPy](https://github.com/spencerfolk/rotorpy) 的建模说明，高速多旋翼手感来自相对空速下的寄生阻力、rotor drag、blade flapping、induced/translational drag 等空气动力力/力矩，而不是一个目标速度控制器。因此这轮不改速度上限和油门，只让机头在明显侧滑里更愿意随来流转向。
- `ACRO_WEATHERCOCK_YAW_GAIN_DEGREES_PER_TICK` 从 `0.065` 提到 `0.085`，`ACRO_WEATHERCOCK_YAW_MAX_DEGREES_PER_TICK` 从 `0.48` 提到 `0.72`。`16m/s body-right + 16m/s body-forward` 的 settled 侧滑被动 yaw 从约 `0.38°/tick` 提到约 `0.50°/tick`，纯 broadside `18m/s` 也从弱风标提升到约 `0.25°/tick`。
- 这不是自动回正，也不是稳定模式：它只在 ACRO、高速侧滑、没有主动 yaw stick 时作为空气风标力矩出现；主动 yaw 输入仍会压制这条路径，`activeYaw` 回归继续保持大于 `4.3..4.5°/tick`。
- 保留 `acroSidewashMemory` 语义：刚切入侧滑时风标 yaw 仍按 fresh sidewash 延迟建立，持续侧滑后才接近 settled。这样刚翻滚/刚切进斜飞不会被瞬间吸正，但持续斜飞时机头会更明显地跟随空气，而不是一直横着滑。
- 更新回归 `acroWeathercockYawClearlyTurnsNoseIntoSideslip`、`acroForwardSideslipAddsClearPassiveYawWithoutStealingActiveYaw`、`acroSidewashMemoryDelaysPassiveWeathercockYawAfterFastSlipEntry` 和 broadside passive yaw 测试。已通过 targeted 测试、完整 `PlayableFlightModelTest` + `DroneEntityModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-064303.json`，ACRO playable 诊断通过，最大水平位移约 `16.39m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 体轴 rate 与欧拉积分拆分，减少翻滚后平面侧飞）
这一轮继续处理“翻滚一周后持续侧飞、斜飞/大 bank 时机头和轨迹不像同一个三维刚体”的问题。复查 [do-a-barrel-roll](https://github.com/enjarai/do-a-barrel-roll) 后确认，它的核心不是在欧拉角上硬堆 pitch/yaw/roll，而是按当前 `facing / left / up` 向量做旋转：pitch 绕当前 left，yaw 绕当前 up，roll 绕当前 facing，最后再重新提取姿态。当前 playable ACRO 之前虽然已经加了 banked yaw coupling，但 `acroPitchRateRadiansPerTick` 同时被当作“机体系角速率状态”和“直接加到 Minecraft pitch/roll 的欧拉增量”，大倾角时要么仍像地平系滑块，要么会把摇杆权威一起吃掉。
- `PlayableFlightModel` 现在把 ACRO rate 拆成两层：飞控状态、惯性负载、yaw 耦合继续保留未投影的机体系 body rate；真正积分到 `pitchRadians / rollRadians` 前，才按当前交叉姿态做欧拉投影。这样大 bank 下 pitch 会更多转成 heading/yaw，而不是把机体继续当屏幕平面 pitch，同时不会因为投影就让下一帧摇杆 rate 变小。
- `ACRO_BODY_RATE_BANKED_PITCH_MAX_EULER_LOSS` 从旧的 `0.22` 收敛到 `0.54`，`ACRO_BODY_RATE_VERTICAL_ROLL_MAX_EULER_LOSS` 从 `0.18` 收敛到 `0.48`；yaw 耦合从 `0.42` 提到 `0.47`，vertical roll yaw 权重从 `0.70` 提到 `1.15`，但 `ACRO_BODY_RATE_YAW_COUPLING_MAX_DEGREES_PER_TICK` 仍封顶在 `2.35°/tick`。`60°` bank 下无 yaw-stick 的 full pitch step 现在约 `2.22°/tick` yaw，不再停在旧的 `2.15°/tick` 上限。
- 这不是自动回正，也不是给 ACRO 加稳定模式。主动 yaw 仍优先；松杆后的完整 roll 捕获、侧洗记忆、横向惯性和之前的空气动力项不改变。这轮只修正“机体系角速度如何投影到 Minecraft 欧拉姿态/航向”的路径，让大 bank、vertical roll 和翻滚后的斜飞更像真实 rate mode。
- 回归新增/更新了 `acroBankedPitchProjectsAwayFromPlanarEulerSlide`、`acroVerticalRollProjectsAwayFromPlanarEulerSlide`、`acroBodyRateYawCouplingAddsBankedPitchAndVerticalRollHeadingChange`、`bankedAcroPitchInputCreatesHeadingChangeWithoutYawStick` 和 ACRO gamepad 中杆边界。已通过 targeted 测试、完整 `PlayableFlightModelTest` + `DroneEntityModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-063446.json`，ACRO playable 诊断通过，最大水平位移约 `16.39m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO banked pitch 体轴耦合增强，并修正目视 pitch 符号）
这一轮继续处理“斜向飞行像平移，不像真机在三维姿态里飞”的手感问题。对照 [RotorPy](https://github.com/spencerfolk/rotorpy) / RotorPy 论文的建模思路：真实多旋翼高速机动不是只靠一个平面速度刹车，frame drag、rotor drag、blade flapping、induced/translational drag 和姿态角速度都会随相对空气速度共同作用。复查当前 playable 后发现，空气阻力和 sidewash 已经做了多轮收敛，下一处更像根因的是 ACRO 姿态链路：banked pitch / vertical roll 仍然只是保守地给 yaw 加一点补偿，太像地平系 pitch/roll 滑块。
- `ACRO_BODY_RATE_YAW_COUPLING_SCALE` 从 `0.28` 提到 `0.42`，`ACRO_BODY_RATE_YAW_COUPLING_MAX_DEGREES_PER_TICK` 从 `1.55` 提到 `2.35`。`60°` bank 下 `4°/tick` pitch 的无 yaw-stick 航向耦合从约 `0.97°/tick` 提到约 `1.45°/tick`；完整 step 里的 banked pitch yaw 从约 `1.5°/tick` 提到约 `1.90°/tick`。
- 这不是自动回正，也不是额外平面刹车：主动 yaw 输入仍优先，普通水平 pitch 不产生 yaw；增强只在大 bank / 大 pitch 姿态下把体轴角速度更明显投到航向变化上，让斜飞/翻滚后的轨迹更像机体在空气里转向，而不是屏幕平面平移。
- 顺手按实测反馈修正目视/第三人称 pitch 符号：`DroneEntityModel.bodyPitchRotationRadians` 现在用 `-pitchRadians`，避免前飞时目视模型从压头显示成抬头。这个改动不改 FPV 相机矩阵，也不改服务端物理。
- 已通过 targeted banked-body-rate / 模型 pitch 测试、完整 `PlayableFlightModelTest` + `DroneEntityModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-062012.json`，ACRO playable 诊断通过，最大水平位移约 `16.39m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 侧滑侧力增强，斜飞先弯轨迹再付代价）
这一轮继续收敛你反馈的“翻滚后/斜飞时像持续侧飞，轨迹不跟机头和气流自然耦合”的问题。复查当前 ACRO 分解后发现，`16m/s body-right + 16m/s body-forward` 的 settled 斜飞里，body-right 基础阻力约 `-6.59m/s²`，而实际弯轨迹的侧滑侧力只有约 `1.74m/s²`；fresh sidewash 时还只有约 `32%` 响应。这会让玩家感觉主要是在被横向刹车，而不是速度矢量被空气动力逐步弯向机头方向。
- `ACRO_SIDEFORCE_GAIN` 从 `0.300` 提到 `0.360`。斜飞 settled 侧滑侧力从约 `1.74m/s²` 提到约 `2.09m/s²`，并且仍保持与速度垂直：侧力只负责弯轨迹，不凭空增减速度能量。
- 侧滑诱导阻力同步随侧力平方提高，`16+16m/s` 斜飞的诱导阻力从约 `0.36m/s²` 提到约 `0.46m/s²`。这让“更会咬住空气转弯”同时付出能量代价，避免变成无成本空气舵。
- 更新回归测试锁住新包线：侧滑侧力必须在约 `2.00..2.25m/s²`，诱导阻力必须仍明显小于侧力，且 fresh/settled sidewash 记忆、斜飞惯性和 broadside coast 不被破坏。
- 已通过 targeted 侧滑/斜飞测试、完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-061200.json`，ACRO playable 诊断通过，最大水平位移约 `16.39m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 横向线性阻尼降档，保留 broadside 惯性）
这一轮继续围绕“斜向飞行不要像被平面刹住”的问题做标定。先对照资料包和当前 playable 数值：前向 `20 -> 5m/s` coastdown 现在约 `8.5s / 85.9m`，已经接近资料里 IMAV 质量匹配参考的 `7.6s / 82.2m`，所以前向惯性不是主短板；更可疑的是纯横向/大侧滑，旧 playable `20 -> 5m/s` 只有约 `2.8s / 28.3m`，body-right 分量容易被横向线性阻尼过早吃掉。
- `ACRO_LATERAL_LINEAR_DRAG_PER_SECOND` 从 `0.19/s` 降到 `0.14/s`，与垂向线性阻尼同档。横向 CdA、分离流、侧滑侧力、侧滑诱导阻力和 sidewash memory 都保留，所以这不是取消横向空气阻力，而是先移除一截更像“游戏刹车”的线性耗能。
- 新增 `acroBroadsideCoastKeepsInertiaWithoutBecomingFreeSideSlide` 回归：`20m/s` 纯横向释放后，`1s` 速度保持在约 `11.2..12.2m/s`，`2s` 仍有 `7.0..7.8m/s`，降到 `5m/s` 需要 `2.85..3.25s`、距离 `29..32m`；同时仍要求它不能变成无阻力横移。
- 斜向 `16m/s right + 16m/s forward` 的 2 秒释放仍会从横滑弯回机头方向，body-right/body-forward 约 `0.65`，不是纯平移；静态斜飞阻力包线也同步收敛到新的 lateral damping。
- 已通过 targeted coast/sidewash 测试、完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-060500.json`，ACRO playable 诊断通过，最大水平位移约 `16.39m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 侧滑诱导阻力按侧洗平方建立）
这一轮继续收敛“高速斜飞不要像平面平移，也不要刚切入就被硬刹”的手感。复查发现 `acroBodyAerodynamicAcceleration` 里的侧滑侧力已经按 `acroSidewashMemory` 延迟建立，但由侧力派生的 `acroSideslipInducedDragAcceleration` 仍用已经缩放后的侧力线性计算；也就是说 fresh sidewash 下侧力是约 `32%`，诱导阻力也会约 `32%` 建立。真实空气动力上这类由侧力带来的能量成本更接近随载荷平方增长，刚切入斜飞/翻滚后第一段不应该马上吃满平面刹车。
- `acroBodyAerodynamicAcceleration` 现在先计算 settled 侧力，再按 `acroSidewashForceResponse(...)` 给实际侧力；诱导阻力改走新的三参数 `acroSideslipInducedDragAcceleration(bodyVelocity, settledSideforce, sidewashResponse)`，并按 `sidewashResponse^2` 建立。持续侧滑时结果与 settled/full-response 保持一致，fresh 侧滑时则保留更多横向惯性距离。
- 旧的二参数 `acroSideslipInducedDragAcceleration` 仍代表 settled/full-response 静态标定语义；新的三参数入口只用于运行时侧洗记忆包线，避免把已有测试、资料标定和旧 helper 语义一起漂移。
- 新增 `acroSidewashMemoryBuildsSideslipInducedDragSlowerThanSideforce` 回归：直线 `25m/s` 仍不产生侧滑诱导阻力；`16m/s right + 16m/s forward` 在 fresh sidewash 下的诱导阻力必须约为 settled 的 `9%..12%`，而不是和侧力同样按 `32%` 线性建立。
- 已通过 targeted 侧滑/惯性测试、完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-055651.json`，ACRO playable 诊断通过，最大水平位移约 `16.39m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 平移升力 sideflow 脏流接入侧洗记忆）
这一轮继续沿着“斜飞不要像平面平移”的主线收敛。复查发现 `acroTranslationalLiftThrustScale` 还把侧向来流的 dirty ETL 权重直接用即时 `acroAdvanceSideflowExposure(...)` 计算：刚切进斜飞时，平移升力增益会立刻按 settled 侧流被压低，桨盘像瞬间进入脏横流，而不是随着侧洗/入流逐步建立。
- `acroTranslationalLiftThrustScale` 现在增加带 `acroAeroCrossflowLag` / `acroSidewashMemory` 的重载，运行时由 `acroPhysicalVelocity` 传入真实 memory；旧三参数入口保持 settled 语义，避免已有静态调参和测试漂移。
- `acroTranslationalLiftDragBodyAcceleration` 同步接入同一套 memory 语义，让 fresh 斜飞保留更多 clean-flow ETL 增益，同时也付出相匹配的诱导阻力成本；直线 clean-flow 不受 memory 影响。
- 新增 `acroSidewashMemoryDelaysDirtyTranslationalLiftWithoutTouchingCleanFlow` 回归：`9m/s right + 9m/s forward` 在 fresh sidewash 下的 ETL gain 必须明显高于 settled，但仍低于纯直线 clean-flow；对应 ETL drag 也随 gain 增强，避免只加推力不加能量成本。
- 已通过 targeted ETL 测试、完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-054847.json`，ACRO playable 诊断通过，最大水平位移约 `16.39m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 桨盘侧洗转弯载荷拆分 yaw 侧滑与 pitch 迎角）
这一轮继续清理“高速斜飞像平移”的剩余空气动力路径。复查发现 `acroRotorSidewashTurnAcceleration` 还把 yaw-plane sideslip 和 pitch-plane angle-of-attack 先取 `max(...)`，再整体乘 `acroSidewashForceResponse(lag, memory)`。这会让机头下压/拉起带来的 pitch 迎角响应也被当成 yaw 侧洗延迟处理，导致桨盘在 pitch 动作里的转弯载荷建立过慢，和真实穿越机“机身姿态变化立刻改变迎角/推力方向”的手感不一致。
- `acroRotorSidewashTurnAcceleration` 现在把两条路径拆开：yaw 侧滑使用 `acroYawSidewashExposure(...)`，刚切入横向侧滑时逐步建立；pitch 迎角使用 `acroPitchLagExposure(...)`，不再受 `acroSidewashMemory` 隐藏。
- 这不是自动回正，也不是增加平面刹车：转弯载荷仍沿当前速度/推力几何方向计算，不给速度凭空加能量；只是让“侧滑气流逐步建立”和“机头迎角即时生效”分别符合自己的物理语义。
- 新增 `acroSidewashMemoryDelaysYawRotorSidewashTurnWithoutHidingPitchAoa` 回归，锁住 full lag/fresh memory 下 yaw 侧滑桨盘转弯载荷低于 settled，而 pure pitch 迎角在 fresh/settled 中完全一致。
- 已通过 targeted rotor-sidewash turn 测试、完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-054321.json`，ACRO playable 诊断通过，最大水平位移约 `16.41m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 高前进比侧向推力损失接入侧洗记忆，并修正目视 pitch）
这一轮继续收敛“高速斜向飞行像平面平移”的手感。复查剩余 ACRO 物理链路后发现，`acroAdvanceRatioThrustScale` 的基础高前进比 prop rolloff 已经存在，但侧向来流的额外推力损失仍只读较快的 `acroAeroCrossflowLag`。这样刚翻滚或刚切进斜飞时，桨盘侧向来流损失可能比机身/桨盘侧洗记忆更早进入 settled 状态，造成第一段动作显得被贴在平面里滑。
- `acroAdvanceRatioThrustScale` 现在增加带 `acroSidewashMemory` 的重载，运行时由 `acroPhysicalVelocity` 传入真实 memory；旧 helper 仍保持 `memory = lag`，所以已有静态标定和旧测试入口不漂移。
- 侧向来流额外损失现在通过 `acroYawSidewashExposure(...)` 建立：直线高速 prop rolloff 仍即时有效，pitch 迎角/机头下压路径不被隐藏；只有 yaw-plane 侧滑的额外 sideflow loss 会在 fresh 侧洗时更轻，持续斜飞后再逐步变重。
- 同轮修正目视/第三人称模型 pitch 符号：`DroneEntityModel.bodyPitchRotationRadians` 重新直接使用 playable pitch。结合 renderer 的 `scale(-1,-1,1)` 和机头局部 `+Z` 语义，正 pitch / 前飞在目视模型上重新显示为机头下压，负 pitch 显示为抬头；不改 FPV 相机矩阵和服务端物理。
- 新增 `acroSidewashMemoryDelaysYawAdvanceRatioLossWithoutHidingPitchAoa` 与更新 `DroneEntityModelTest` 回归。已通过 targeted advance-ratio / 模型测试、完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-053741.json`，ACRO playable 诊断通过，最大水平位移约 `16.41m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 偏航转弯载荷接入侧洗记忆）
这一轮继续收敛“翻滚/高速斜飞后像被平面拖着侧滑”的手感问题。复查发现大部分机身侧力、桨盘侧滑、动态入流、body-rate 负载和角速度控制链路已经接入 `acroSidewashMemory`，但 `acroYawTurnLoadBodyAcceleration` 仍只读较快的 `acroAeroCrossflowLag`。结果是刚翻滚或刚切进高速斜向飞行时，偏航转弯能量成本可能比真实侧洗建立得更快，容易把飞机过早按成 settled 横流状态。
- `acroPhysicalVelocity` 现在把真实 `acroSidewashMemory` 传入 yaw-turn load；旧的三参数 helper 仍保持 `memory = lag` 的 settled 语义，避免已有静态标定漂移。
- yaw-plane sideslip 现在通过 `acroYawSidewashExposure(...)` 计算：刚进入高速侧滑时保留更多侧向惯性，持续侧滑后侧洗记忆逐步建立，偏航转弯载荷再变重。直线高速 yaw 不受 memory 影响，所以这不是自动回正，也不是削弱普通转向。
- 新增 `acroSidewashMemoryDelaysYawTurnLoadAfterFastSlipEntry` 回归：`16m/s right + 16m/s forward`、满 crossflow 但 fresh sidewash 的偏航转弯载荷必须明显低于 settled，同时仍高于直线巡航载荷，防止重新退回“完全无空气负载”。
- 已通过 targeted yaw-turn 测试、完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-053041.json`，ACRO playable 诊断通过，最大水平位移约 `16.41m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 角速度控制链接入侧洗记忆）
本轮继续围绕“斜向飞行像平移、不像真机穿过空气”的核心手感收敛。复查发现，虽然机身侧力、桨盘侧滑、动态压阻力、动态入流和 body-rate 线性负载已经逐步接入 `acroSidewashMemory`，但 ACRO 的角速度控制链仍有几处把 yaw 侧滑和 pitch 迎角混成一个即时 crossflow：rate inertia smoothing、气动 rate damping、motor rate authority、yaw rate inertia smoothing 和 residual torque load。这样刚切入高速侧滑时，飞机的 pitch/roll/yaw 控制负载会过早进入 settled 横流状态，手感容易像“被平面拖着转”，而不是速度、姿态和空气负载逐步追上。

- `acroRateResponse` 现在把 `acroSidewashMemory` 传入角速度链路：`responsiveAcroRate`、`acroAerodynamicRateDamped`、`acroMotorRateAuthorityScale` 和 `acroResidualTorqueRateLoadFraction` 都区分 yaw-plane sideslip 与 pitch-plane angle-of-attack。yaw 侧滑负载读 `acroSidewashForceResponse(lag, memory)`，pitch 迎角仍读基础 lag。
- `yawSmoothing` / `acroYawRateInertiaSmoothingScale` 也接入同一语义，避免刚进入侧滑时 yaw 平滑和惯性负载瞬间按 settled 横流处理。旧 helper 继续保持 `memory = lag`，所以历史 settled 标定和旧测试入口不漂移。
- 这不是自动回正，也不是削掉空气负载：fresh 高速侧滑仍会明显降低 roll/yaw 控制响应，但不再第一帧给满 settled 负载；持续侧滑后侧洗记忆建立，rate authority、阻尼和残余扭矩负载会逐步变重。pure pitch 迎角路径在 memory 为 `0` 和 `1` 时保持完全一致，防止机头压低/拉起响应被错误延迟。
- 新增 `acroSidewashMemoryDelaysYawAngularControlLoadsWithoutHidingPitchAoa` 回归，并把 `acroHighSpeedDiagonalFlowBuildsRollRateWithMoreWeight` 改成 fresh-sidewash 语义：仍要求高速斜飞 roll rate 明显变重，但不要求第一帧达到 settled 侧洗负载。已通过 targeted 角速度测试、完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。
- 本轮服务端自测报告为 `server-selftest-playable-20260621-052310.json`，ACRO playable 诊断通过，最大水平位移约 `16.41m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO body-rate 高速负载接入侧洗记忆）
本轮继续收敛高速斜飞、翻滚切入后像“平面滑块”的手感。前几轮已经把机身侧力、分离流、weathercock yaw、桨盘侧滑、动态压阻力和动态入流逐步接入 `acroSidewashMemory`；这次复查后发现 `acroBodyRateLoadBodyAcceleration` 仍然把 yaw 侧滑和 pitch 迎角混进同一个即时 crossflow 权重，再直接乘较快的 `acroAeroCrossflowLag`。这会让刚进入高速侧滑并带 pitch/roll rate 时，高速角速度空气负载比侧洗记忆更早打满。

- `acroBodyRateLoadBodyAcceleration` 现在增加带 `acroSidewashMemory` 的重载，运行时由 `acroPhysicalVelocity` 传入真实 memory；旧的 5 参数 helper 保持 `memory = lag`，因此历史测试语义不变。
- 新逻辑把 yaw-plane sideslip 和 pitch-plane angle-of-attack 拆开：yaw 侧滑 body-rate 负载乘 `acroSidewashForceResponse(lag, memory)`，pitch 迎角负载仍乘基础 lag。刚翻滚或刚切入斜飞时不会瞬间给满横流角速度负载，持续侧滑后负载会随侧洗记忆逐步建立；机头压低或拉起时的 pitch 迎角响应不被隐藏。
- 新增 `acroSidewashMemoryDelaysYawBodyRateLoadWithoutHidingPitchAoa` 回归，锁住 fresh yaw body-rate load 明显低于 settled，同时 pure pitch 在 memory 为 `0` 和 `1` 时完全一致。已经通过 targeted body-rate 测试、完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。
- 本轮服务端自测报告为 `server-selftest-playable-20260621-051046.json`，ACRO playable 诊断通过，最大水平位移约 `16.41m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 动态入流推力损失接入侧洗记忆）
本轮继续收敛高速斜飞/翻滚切入的“平面感”。前几轮已经把机身、桨盘和 yaw/roll 横流项逐步接入 `acroSidewashMemory`；复查后发现 `acroDynamicInflowThrustScale` 仍把 yaw 侧滑和 pitch 迎角混成一个即时 crossflow，再直接乘较快的 `acroAeroCrossflowLag`。这会让刚进入高速侧滑并带 pitch/roll rate 时，桨盘动态入流推力软化比侧洗记忆更早打满。
- `acroDynamicInflowThrustScale` 现在增加带 `acroSidewashMemory` 的重载，运行时由 `acroPhysicalVelocity` 传入真实 memory；旧的 6 参数 helper 保持 `memory = lag`，因此历史测试语义不变。
- 新逻辑把 yaw-plane sideslip 和 pitch-plane angle-of-attack 分开：yaw 侧滑动态入流损失乘 `acroSidewashForceResponse(lag, memory)`，pitch 迎角损失仍乘基础 lag。刚翻滚/刚切入斜飞时不会瞬间掉完整推力，持续侧滑后推力软化逐步建立；机头压低或拉起时的 pitch 迎角响应不被隐藏。
- 新增 `acroSidewashMemoryDelaysYawDynamicInflowLossWithoutHidingPitchAoa` 回归，锁住 fresh yaw 比 settled yaw 保留更多推力，同时 pure pitch 在 memory 为 `0` 和 `1` 时完全一致。已通过 targeted 测试、完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。
- 本轮服务端自测报告为 `server-selftest-playable-20260621-050452.json`，ACRO playable 诊断通过，最大水平位移约 `16.41m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 桨盘侧滑负载接入侧洗记忆）
本轮继续处理“高速斜飞/翻滚切入时像平面滑动”的手感细节。前几轮已经把机身侧力、分离流、weathercock yaw、横流 roll 和 coupled dynamic pressure 接入 `acroSidewashMemory`；复查后发现桨盘 flapping 与 rotor in-plane drag 的侧滑权重仍只读取较快的 `acroAeroCrossflowLag`。这会让刚切入高速侧滑时，桨盘侧向气动比机身侧洗记忆更早打满。
- `acroRotorFlappingBodyAcceleration` 与 `acroRotorInPlaneDragBodyAcceleration` 现在增加带 `acroSidewashMemory` 的重载，运行时由 `acroPhysicalVelocity` 传入真实 memory。旧的 5 参数 helper 保持兼容，仍按 `memory = lag` 的 settled 语义工作，避免历史测试语义漂移。
- 直线前飞的 straight-flow 权重不变；只有 yaw-plane sideslip exposure 乘 `acroSidewashForceResponse(lag, memory)`。因此刚翻滚或刚进入斜飞时，桨盘不会瞬间给满 flapping/in-plane drag；持续侧滑后，侧洗记忆建立，桨盘才逐渐更明显地咬住横流。
- 新增 `acroSidewashMemoryDelaysRotorDiskSideLoadsAfterFastSlipEntry` 回归，锁住 `crossflowLag=1` 但 `sidewashMemory=0` 时桨盘侧滑负载明显低于 settled，同时直线前飞完全不受 memory 影响。已通过 targeted 测试、完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。
- 本轮服务端自测报告为 `server-selftest-playable-20260621-050004.json`，ACRO playable 诊断通过，最大水平位移约 `16.41m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 侧滑动态压阻力接入侧洗记忆）
本轮继续沿着“高速斜飞不要像平面滑块”的主线收敛。上一轮已经把 yaw 侧滑分离流接入 `acroSidewashMemory`，但 `acroCoupledDynamicPressureDragAcceleration` 仍然把 pitch 迎角和 yaw 侧滑混成一个即时 crossflow exposure，再由运行时整体乘 `acroAeroCrossflowLag`；这会让刚翻滚或刚切入斜飞时，额外动态压阻力比侧洗/侧力/侧滑分离更早打满。
- `acroCoupledDynamicPressureDragAcceleration` 现在增加带 `crossflowLag` 和 `sidewashMemory` 的重载。旧的一参数方法仍代表 settled/full-response 语义；运行时 `acroBodyAerodynamicAcceleration` 改用三参数版本。
- 新逻辑把 yaw-plane sideslip exposure 与 pitch-plane angle-of-attack exposure 分开：yaw 侧滑动态压乘 `acroSidewashForceResponse(lag, memory)`，pitch 迎角动态压仍乘基础 lag。这样刚进入高速侧滑时不会立刻出现完整额外“空气墙”，但机头前压/拉起时的迎角阻力仍然即时有效。
- 新增 `acroSidewashMemoryDelaysYawCoupledDynamicPressureWithoutHidingPitchAoa` 回归，锁住 fresh yaw 动态压明显低于 settled，同时 pure pitch 在 memory 为 `0` 和 `1` 时完全一致。已通过 targeted 测试、完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。
- 本轮服务端自测报告为 `server-selftest-playable-20260621-045431.json`，ACRO playable 诊断通过，最大水平位移约 `16.41m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 侧滑分离流接入侧洗记忆）
本轮继续收敛“翻滚/斜飞后持续侧飞、像平面滑动而不像真实机体穿过空气”的手感问题。上一轮已经让横流 roll 和 weathercock yaw 读取 `acroSidewashMemory`，但机身分离流强度仍然直接乘即时 `acroAeroCrossflowLag`；这会让刚切入高速侧滑时，机身侧面积和 yaw-plane stall drag 太快打满，容易把飞机硬拽进持续横滑。
- `acroAirframeSeparationIntensity` 现在增加带 `crossflowLag` 和 `sidewashMemory` 的重载，并把 pitch 迎角分离与 yaw 侧滑分离拆开：pitch/俯仰迎角仍按基础 lag 生效，保证机头前压/拉起的升力和阻力不被隐藏；yaw/侧滑分离则读取 `acroSidewashForceResponse(lag, memory)`，刚翻滚或刚进入斜飞时保留更多侧向惯性，持续侧滑后才逐渐被空气咬住。
- `acroBodyAerodynamicAcceleration` 改用新的分离流重载，因此 separated-flow drag、侧滑侧力的 stall 负载会跟侧洗记忆连续建立；老的三参数 `acroAirframeSeparationIntensity` 保持 settled/full-response 语义，避免已有测试和文档里的静态标定语义漂移。
- 这不是自动回正，也不是削掉空气力：`16m/s right + 16m/s forward` 的 fresh 状态仍有明显侧向阻力和侧向力，但比 settled 状态少一截；pure pitch 的气动结果在 memory 为 `0` 和 `1` 时完全一致，防止把机头压低时的迎角响应误做成“侧洗延迟”。
- 新增 `acroSidewashMemoryDelaysYawSeparationWithoutHidingPitchAoa` 与 `acroSidewashMemoryDelaysSeparatedBodyLoadsAfterFastSlipEntry` 回归，已通过 targeted 测试、完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-044931.json`，ACRO playable 诊断通过，最大水平位移约 `16.41m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 侧滑 weathercock yaw 接入侧洗记忆）
本轮继续处理“高速斜飞时像平面滑块，而不是速度矢量和机头在空气里互相追逐”的手感问题。上一轮已经让横流 roll 力矩读取 `acroSidewashMemory`，但 passive weathercock yaw、yaw damping 和高侧滑主动 yaw 负载仍然直接读取当前侧滑速度；这会让机头被气流转向的反馈和侧力/侧洗/roll 的建立过程不一致。
- `acroAerodynamicYawRate` 现在接收 `acroAeroCrossflowLag` 和 `acroSidewashMemory`，并把 sidewash response 同时作用到 passive weathercock yaw、侧滑 yaw damping、以及高侧滑下的主动 yaw command load。刚切入斜飞时机头不会瞬间被完整 weathercock 拉走；持续斜飞后，侧洗记忆建立，机头才更明显地追向气流方向。
- 这仍然不是自动航向锁定：玩家主动打 yaw 时 command suppression 仍然优先，满 yaw 仍保留可用控制权；只是 settled 高速侧滑下 yaw 会比 calm yaw 稍重，符合“速度越高、横流越稳，偏航越有空气负载”的手感目标。
- 新增 `acroSidewashMemoryDelaysPassiveWeathercockYawAfterFastSlipEntry` 回归，并把原有 passive yaw / broadside yaw / yaw command load 测试改成明确的 settled sidewash 语义。这样以后不会又把 weathercock 改回“第一帧瞬时满额”。
- 已通过 yaw targeted 测试、完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-044006.json`，ACRO playable 诊断通过，最大水平位移约 `16.41m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 横流滚转力矩接入侧洗记忆）
本轮继续收敛“斜向飞行像平移，不像真机被空气咬住走线”的主手感问题。对照本地研究包里 Kolaei inflow-angle rotor `CMx` 线索、NeuroBEM 残差力矩量级，以及 RotorPy/Faessler 这类把相对气流拆成机身阻力、桨盘阻力和 flapping/rotor-drag 的参考系统后，确认当前 playable ACRO 还有一个不够连续的地方：横流滚转力矩虽然已经存在，但它只乘即时 `acroAeroCrossflowLag`，刚切入高速斜飞时仍可能太快把“机体被横流扭动”的姿态手感打满。
- `acroRateResponse` 现在把 `acroSidewashMemory` 一起传入姿态速率链路；`acroTransverseFlowRollMomentRate` 的被动横流滚转力矩不再直接乘即时 lag，而是乘 `acroSidewashForceResponse(acroAeroCrossflowLag, acroSidewashMemory)`。这样刚进入斜飞时仍保留速度惯性和姿态惯性，持续斜飞后气流/侧洗逐渐建立，机体才更明显地被横流推着滚入气流。
- 这不是 ACRO 自稳：松杆不会自动回水平，主动 roll 输入仍会压制被动横流力矩；完整 roll 恢复窗口仍保持优先，避免翻滚完成后又被残余侧滑重新带出侧飞。改动只让“被动气动力矩的建立速度”与上一轮侧洗力/侧洗转弯保持同一套记忆语义。
- 新增 `acroSidewashMemoryDelaysPassiveTransverseRollMomentAfterFastSlipEntry` 回归：即使 crossflow lag 已接近满值，只要 sidewash memory 还未建立，第一帧被动横流 roll rate 也必须明显低于 settled 状态；memory 建立后才回到原来的高侧滑滚转力矩量级。
- 已通过 targeted passive-roll 测试、完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-043259.json`，ACRO playable 诊断通过，最大水平位移约 `16.41m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 姿态中点积分与目视压头修正）
本轮继续处理“高速斜飞/全向转动时像平面位移，而不是连续物理步长里的真实机体”的核心手感问题。复查 playable ACRO 后，发现一个很关键的积分细节：50ms tick 内 pitch/roll 已经在变化，但旧模型把 tick 末尾姿态直接用于整帧推力、阻力、桨盘进速和侧洗投影，这会让快速压杆、翻滚或斜向切入时看起来像下一帧姿态瞬间作用了一整帧。
- `PlayableFlightModel.acroPhysicalVelocity` 现在使用 pitch/roll 的中点姿态进行物理投影：推力轴、机体系速度、advance-ratio thrust scale、机身阻力、桨盘/侧洗转弯、yaw turn load 和 body-rate load 都基于 `current attitude - 0.5 * rate`。这不是自稳，也不是改变最终姿态，只是让本 tick 的空气动力更像连续时间积分。
- ACRO 的摇杆 rate、显示姿态、FPV 相机、yaw 积分和世界动量 rebase 都保持原语义；改动只影响本帧物理力/速度投影，目标是减少“末态姿态整帧生效”带来的瞬时平移感和高速斜飞回抽感。
- 同步修正目视/第三人称模型的 pitch 显示符号：`DroneEntityModel.bodyPitchRotationRadians` 现在把 playable pitch 映射到模型压头方向，前飞正 pitch 在目视状态下重新表现为机头下压。这个修正不改 FPV 相机矩阵，也不改物理 pitch。
- 新增/更新回归测试锁住中点姿态投影和模型 pitch 符号。已通过 targeted `PlayableFlightModelTest`、`DroneEntityModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-042439.json`，ACRO playable 诊断通过，最大水平位移约 `16.41m`，最大速度约 `6.71m/s`，平均电机遥测峰值约 `6984 RPM`。

## 最新进展（2026-06-21，ACRO 侧洗记忆与斜飞气动尾迹）
本轮继续针对“斜向飞行像平移，不像真机在空气里走线”的主问题收敛。复查现有 playable ACRO 后，问题不再是“没有侧向空气力”，而是很多横流/侧洗效果共用同一个即时 `acroAeroCrossflowLag`：基础阻力、侧滑侧力、桨盘侧洗转弯几乎一起起效。真机的横流进桨盘和机身侧滑力会有建立与释放尾迹，所以这轮加入一个专门的 sidewash memory。

- `PlayableFlightModel.State`/`Step` 新增 `acroSidewashMemory`。它只在 ACRO 下根据机体系横向速度、速度大小和侧滑角建立，建立速度比基础 crossflow lag 慢，释放也更慢。目的不是加自稳，而是让“刚切入斜飞时保留惯性，持续斜飞后空气逐渐咬住轨迹，退出横流后还有短尾迹”。
- `acroBodyAerodynamicAcceleration` 的侧滑侧力、`acroRotorSidewashTurnAcceleration` 的桨盘侧洗转弯现在读取 `acroSidewashForceResponse(crossflowLag, sidewashMemory)`；基础阻力仍走原来的 crossflow lag。因此第一帧不会立刻给满侧力，稳定横流时仍能弯轨迹，释放时不会像开关一样突然消失。
- `DroneEntity` 接入运行时 memory 字段，旧的 `State` 构造器保持兼容：历史测试里只传 `acroAeroCrossflowLag` 的“settled”状态会默认把 memory 设为相同值，避免把已有边界语义悄悄改掉。
- 新增回归测试锁住 sidewash memory 比基础 crossflow 慢建立、释放保留尾迹，以及侧滑侧力第一帧使用 memory 响应而不是瞬间 full sideforce。已通过完整 `PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-041800.json`，ACRO playable 诊断通过，最大水平位移约 `16.53m`，最大速度约 `6.75m/s`，平均电机遥测峰值约 `6979 RPM`。

## 最新进展（2026-06-21，playable yaw 中点位移积分）
本轮继续针对你反馈的“高速/斜向飞行还有回抽、旋转不通畅、像平面平移”的问题收敛。复查后确认目视模式下前飞压头的渲染符号当前已经由 `DroneEntityModelTest` 锁住，正 pitch 会让第三人称机体朝 Minecraft 里的低头方向显示，所以这次没有继续改 FPV/目视相机矩阵，重点放到实体移动积分本身。

- `DroneEntity.applyDebugFlight` 过去在同一个 tick 里先用旧 `getYRot()` 把机体系速度转成世界位移，然后 tick 末尾才把 yaw 更新到新朝向。这会造成玩家打 yaw 时，画面看起来像“机头已经转了，但位移还沿上一帧方向滑”，尤其高速斜飞时容易放大成回抽感或平面横移感。
- 新增纯逻辑 helper `PlayableMovementYaw`，对可玩飞行的世界位移使用 yaw midpoint：`currentYaw + yawDelta * 0.5`。最终 yaw rate、ACRO rate 模式、速度惯性和世界动量 rebase 都不改，只把这一帧的位移投影放在旋转前后之间的位置，让转向和位移更像同一个连续物理步长。
- 新增回归测试覆盖三件事：可见 yaw rate 会使用中点朝向；极小 yaw 噪声/NaN 不污染移动；`20m/s` 前向速度在 `10deg/tick` yaw 下会在同 tick 内开始弯入新航向，而不是整帧贴着旧航向直滑。
- 已通过定向测试、完整 `:fabric-mod:test`、完整 `gradlew build`（Fabric GameTest 7/7 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-040626.json`，ACRO playable 诊断通过，最大水平位移约 `16.53m`，最大速度约 `6.75m/s`，平均电机遥测峰值约 `6979 RPM`。

## 最新进展（2026-06-21，ACRO 桨盘侧洗转弯曲率）
本轮继续针对“斜向飞行速度够了，但像平面平移而不像真实穿越机走线”的核心手感收敛。复查本仓库 `docs/data/airframe_drag_calibration_packet.csv`、`docs/fpv-sim-data-sources.md`，并对照 [Faessler/RPG rotor-drag 高速轨迹模型](https://rpg.ifi.uzh.ch/docs/RAL18_Faessler.pdf) 和 [RotorPy](https://github.com/spencerfolk/rotorpy) 的 aerodynamic wrench 思路后，这轮不再继续堆全局阻力，而是补“推力矢量已经在改航迹时，桨盘/侧洗让航迹更愿意弯过去”的小曲率项。
- `PlayableFlightModel` 新增 `acroRotorSidewashTurnAcceleration`，并接入 ACRO 速度积分。它读取当前水平速度、推力水平分量、机体系横流/迎角和 `acroAeroCrossflowLag`，只输出垂直于当前水平速度的加速度；也就是说它改变航迹方向，不沿速度方向凭空加速，也不做自动回正。
- 当前标定非常保守：直线高速压坡只给约 `0.3m/s^2` 级别的额外曲率，持续 `16m/s right + 16m/s forward` 斜向横流下才建立到约 `0.7m/s^2`，上限 `0.90m/s^2`。这样刚进入斜飞仍保留惯性，横流建立后会更像机体和桨盘咬住空气转弯，而不是靠抽象平面阻尼把侧向速度洗掉。
- 新增 `acroRotorSidewashTurnCurvesWithoutAddingPlanarEnergy`：锁住推力与速度同向时不产生假转弯、banked straight 只有轻微曲率、fresh diagonal 明显小于 settled diagonal，并验证该曲率项对水平速度点积为 `0`，防止以后把它误调成隐藏加速或隐藏刹车。
- 已通过 targeted sidewash/thrust-turn-load 测试、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-035844.json`，ACRO playable 诊断通过，最大水平位移约 `16.16m`，最大速度约 `6.53m/s`，平均电机遥测峰值约 `6985 RPM`。

## 最新进展（2026-06-21，ACRO yaw 轴惯量响应）
本轮继续沿着“斜向飞行不要像平面平移，而要像真实穿越机带着质量转入气流”的主线收敛。上一轮已经加强了横向来流的咬风；这轮不再继续加机体阻力，而是补主动 yaw 轴的惯量感：真实 5 寸机的航向变化依赖电机反扭矩和桨盘气动余量，高速侧滑或低油门时不应该像理想数学 yaw rate 源那样瞬间贴到目标速率。
- `PlayableFlightModel` 新增 `acroYawRateInertiaSmoothingScale`，并只在 ACRO yaw smoothing 上应用。hover/低速下 scale 仍为 `1.0`，普通 yaw 手感不被闷掉；零油门附近保留约 `90%` yaw 响应，模拟 AirMode/电机怠速下仍有控制权；高速直线前飞只给很轻的航向惯量，高速斜向横流则随着 `acroAeroCrossflowLag` 建立逐步变重。
- 这个改动不改变最终 yaw 目标，也不加入自动回正。它只是让 yaw 建立和释放有一点真实惯量尾巴，尤其是 `16m/s right + 16m/s forward` 这类稳态斜滑中，满 yaw 不再像屏幕平面直接旋转，而是略微受横流和电机余量影响，同时仍保留足够主动控制权。
- 新增 `acroYawRateInertiaLoadsSettledCrossflowWithoutKillingYawAuthority`：锁住 hover scale、idle scale、直线高速 scale、fresh diagonal scale 和 settled diagonal scale 的顺序，并验证稳态斜滑下满 yaw 低于 calm yaw 但仍大于 `82%`，避免把 yaw 轴调死。
- 已通过 yaw targeted 测试、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-034945.json`，ACRO playable 诊断通过，最大水平位移约 `16.16m`，最大速度约 `6.53m/s`，平均电机遥测峰值约 `6985 RPM`。

## 最新进展（2026-06-21，ACRO 斜飞横向咬风再收敛）
本轮继续处理你最新反馈的“翻滚一周之后持续侧飞、无法自然回到可控航迹”的手感问题。复查后确认 ACRO 速度积分本身已经不是目标速度回正，也不是平面速度插值；问题更像是持续 sideslip 下，机身横向迎风面积和 yaw-plane sideforce 还不够咬空气，导致 `16m/s right + 16m/s forward` 这类高速斜滑在两秒后仍然保留过多横向分量。
- `PlayableFlightModel` 只加强横向来流相关项，不加全局前向阻力：ACRO lateral drag area 从 `0.0269m^2` 提到 `0.0340m^2`，lateral linear drag 从 `0.16/s` 提到 `0.19/s`。直线机头前飞的 forward drag 没有提高，避免把 25m/s 巡航和松杆 coastdown 重新做成“撞空气墙”。
- yaw-plane sideslip sideforce 进一步从 `0.185` 提到 `0.300`，同时 induced-drag gain 从 `0.44` 降到 `0.27`。也就是说，横向滑行时更多用垂直于速度的侧力把航迹弯回机头方向，而不是靠额外能量损失硬刹车。新的单元测试继续锁住 sideforce 对当前速度不做功，并要求 sideforce magnitude 明显大于 induced drag。
- 新增 `acroDiagonalCoastKeepsInertiaButCurvesTowardBodyForward`：从 `16m/s right + 16m/s forward` 斜向惯性开始，松杆 2 秒后仍必须保留 `>10.5m/s` 的水平速度、轨迹距离 `>29m`，但横向/前向速度比例必须收敛到 `<0.64`。这条测试专门防止“像平面平移一样一直侧飞”，同时也防止我用过强阻力把速度感吃掉。
- 同步更新斜向总气动、crossflow dynamic-pressure、weathercock yaw 和主动 yaw 优先级的数值边界：横向切风现在会更重、更愿意转机头，但主动 yaw 仍保留足够权重，不会被被动 weathercock 偷走控制权。
- 已通过完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-034158.json`，ACRO playable 诊断通过，最大水平位移约 `16.17m`，最大速度约 `6.53m/s`，平均电机遥测峰值约 `6985 RPM`。

## 最新进展（2026-06-21，目视模式前飞压头修正）
本轮处理一个你提过但不是主线的可见问题：目视状态下，向前飞时机体外观看起来从“压头”反成了“抬头”。复查后确认 `PlayableFlightModel` 的 pitch 符号仍用于飞控/FPV/遥测，问题在客户端实体模型把 pitch 又反了一次。
- `DroneEntityModel.bodyPitchRotationRadians` 现在直接使用 playable pitch，不再取负。这样物理/FPV 相机仍沿用原来的 pitch 数据，只有第三人称/目视看到的无人机模型改回“正 pitch = Minecraft 模型低头方向”。
- 更新 `DroneEntityModelTest`，把旧的错误符号断言改成“正 playable pitch 使用正模型 X 旋转、负 playable pitch 使用抬头方向”，防止后续再把目视前飞改回抬头。
- 这次没有改 ACRO 物理参数，也没有回退上一轮斜飞/转弯负载调参。它只是把目视模型的姿态显示和玩家对“机头前压”的直觉重新对齐，方便继续人工试飞判断真实手感。
- 已通过 `DroneEntityModelTest`、完整 `:fabric-mod:test`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-032816.json`，ACRO playable 诊断通过，最大水平位移约 `16.17m`，最大速度约 `6.53m/s`，平均电机遥测峰值约 `6985 RPM`。

## 最新进展（2026-06-21，ACRO 推力矢量转弯负载）
本轮继续收敛“斜向飞行像平移，而不是像真实穿越机带着质量和空气负载在转弯”的手感。复核 RotorPy、Faessler/RPG rotor-drag 模型和 TU Delft/OSU 高速多旋翼辨识资料后，这次不做全局加阻，也不做自动回正；重点放在高速时用推力矢量改变航迹所需付出的能量代价。
- `PlayableFlightModel` 将 ACRO thrust-vector turn load 的 gain 从 `0.11` 提到 `0.16`，最大负载加速度从 `1.25m/s^2` 提到 `1.65m/s^2`。当推力水平分量和当前高速速度方向对齐时仍然没有额外负载；当 `25m/s` 前飞突然用侧向推力改航迹时，沿速度反向的能量损耗从约 `0.88m/s^2` 提到约 `1.28m/s^2`。
- 新增 `acroThrustVectorTurnLoadMakesDiagonalSlipPayForChangingTrack`：锁住 `16m/s + 16m/s` 斜向惯性航迹里，如果推力方向仍沿航迹，不制造假阻力；如果用侧向推力改变航迹，会产生约 `0.52..0.72m/s^2` 的沿速度反向负载。这样斜飞仍有惯性，但高速下“硬拐/侧推”不再像无质量平移。
- 这次改动和上一轮侧流 ETL 脏化、侧滑 sideforce 是互补关系：ETL 脏化减少错误升力，sideforce 弯流改变速度方向，turn load 则让改变航迹付出能量成本。三者都避免把 ACRO 做成自稳，目标是保留真实穿越机的惯性和速度，同时让斜向飞行更有重量感。
- 已通过 targeted thrust-turn-load 测试、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-032343.json`，ACRO playable 诊断通过，最大水平位移约 `16.17m`，最大速度约 `6.53m/s`，平均电机遥测峰值约 `6985 RPM`。

## 最新进展（2026-06-21，ACRO 侧流 ETL 脏化）
本轮继续针对“翻滚/斜飞后一段时间持续侧飞、像平面平移而不是穿越机在空气里走线”的问题收敛。补充复核了 TU Delft 高速四旋翼辨识资料、OSU 多旋翼气动相互作用论文以及 FPV 竞速机架阻力经验资料：高速飞行里 sideslip/skew angle 不只是普通 Cd 阻力问题，干净前飞、斜向来流和纯侧流对桨盘/机架的作用不应该拿同一份 translational lift 增益。
- `PlayableFlightModel` 将 ACRO translational lift 的侧流保留比例从 `0.30` 收紧到 `0.18`。干净前飞仍能获得中速 ETL 甜点，保留速度感；但 `9m/s right + 9m/s forward` 这类斜向侧滑不再白拿太多前飞升力，纯侧流只剩很小的脏 ETL 残余。
- 这不是给 ACRO 加自稳，也不是直接刹车。相反，它是减少错误方向上的“免费升力”：当飞机翻滚后机体系来流主要变成侧向时，升力/推力效率更接近被 skew flow 污染后的桨盘，而不是仍按机头正前方 clean inflow 处理。预期手感是斜飞仍有惯性，但不再像一个带升力补偿的平面滑块。
- 回归测试新增 `acroTranslationalLiftTreatsSideflowAsDirtyEtL`，并更新 translational-lift drag 边界：锁住 clean forward ETL 仍强、diagonal gain 明显低于 clean gain、side gain 低于 diagonal gain，防止以后调参又把侧飞误做成前飞。
- 已通过 targeted ETL 测试、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-031742.json`，ACRO playable 诊断通过，最大水平位移约 `16.17m`，最大速度约 `6.53m/s`，平均电机遥测峰值约 `6985 RPM`。

## 最新进展（2026-06-21，ACRO 侧滑侧力弯流）
本轮继续针对“斜向飞行像平移，而不是像真机在空气里飞”的手感收敛。复核 Faessler/RPG rotor-drag 模型、STARMAC 气动效应和 NASA 小型多旋翼相互作用气动资料后，重点不是继续加普通刹车：真实高速侧滑会让机体系相对来流产生侧力、桨盘 flapping / induced drag 和 rotor drag，这些力会改变速度方向，而不只是把速度数值沿原方向扣掉。
- `PlayableFlightModel` 把 ACRO yaw-plane sideslip sideforce 从 `0.128` 提到 `0.185`，同时把对应 induced-drag gain 从 `0.56` 压到 `0.44`。这样 `16m/s right + 16m/s forward` 的斜向来流里，sideforce 从约 `0.47m/s^2` 提到约 `0.68m/s^2`，并且仍严格垂直于当前速度、不直接做功；附带的 induced drag 只小幅增加，用来保留真实能量损耗，而不是把它做成纯刹车。
- 这次的目标是让 sustained sideslip 的速度矢量更愿意被空气弯回机头方向：玩家会更容易感觉到“机身/桨盘在气流里咬住并转弯”，而不是侧向速度被一个抽象阻尼项平移式洗掉。前一轮的 broadside drag lag 负责“刚进入横流时先保留惯性”，这一轮负责“横流建立后主要先弯方向，再付出适量能量代价”。
- 回归测试新增 `acroSideslipSideforceCurvesMoreThanItBrakesDiagonalSlip`，并更新 sideforce、induced-drag、diagonal aero 边界：锁住 sideforce 对速度不做功、sideforce magnitude 明显高于 induced-drag magnitude、斜向总气动仍有横向洗出但前向刹车允许被转弯侧力部分抵消。
- 已通过 targeted sideforce/diagonal/coastdown 测试、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-031011.json`，ACRO playable 诊断通过，最大水平位移约 `16.17m`，最大速度约 `6.53m/s`，平均电机遥测峰值约 `6985 RPM`。

## 最新进展（2026-06-21，ACRO broadside 基础阻力滞后）
本轮继续沿着“斜向飞行不要像几何平移，而要像真实穿越机在空气里带惯性滑出去再被气流咬住”的目标收敛。复核资料后，RotorPy/RPG 这类参考系统都把气动力放在机体系相对空速上处理：寄生阻力、rotor drag、blade flapping 和 actuator/motor lag 都不是一帧满量开关；本仓库 `docs/fpv-sim-model-validation.md` 也提示旧的 coastdown/drag 标定容易让速度过快被洗掉，造成“没有惯性”的手感。
- `PlayableFlightModel` 新增 `acroBaseBodyDragAcceleration`，把基础机身阻力拆成两层：forward 轴阻力仍即时生效，保证直线巡航和 25m/s 速度包络不被放空；body-right/body-up 的额外 broadside 面积只随 `acroAeroCrossflowLag` 建立。也就是说刚进入 `16m/s right + 16m/s forward` 这类斜向横流时，侧向基础减速度从约 `-6m/s^2` 量级降到约 `-2.8m/s^2` 的干净轴向基线，随后才逐渐恢复到约 `-7.9m/s^2` 的稳态横流负载。
- 这次不是削掉空气，也不是给 ACRO 加自稳。持续侧滑时，分离流、总动压耦合阻力、sideforce、induced drag、桨盘 flapping / in-plane drag 仍会逐步建立；改变的是“第一帧横向空气墙”被拆掉，让翻滚/斜飞后的速度先按惯性走一小段，再被空气和桨盘慢慢咬回来，手感上更接近真实穿越机的 slip + washout。
- 回归测试新增 `acroBaseBodyDragKeepsStraightCruiseInstantButDelaysBroadsideArea` 和 `acroFreshDiagonalSlipCarriesMoreSideInertiaThanSettledCrossflow`，并更新 `acroLaggedCrossflowReducesFirstTickBroadsideDragWithoutRemovingBaseDrag`：锁住直线巡航不受 lag 影响、fresh diagonal slip 比 settled crossflow 保留更多 body-right 速度、稳态斜向横流仍保持明显 lateral washout。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-025957.json`，ACRO playable 诊断通过，最大水平位移约 `16.17m`，最大速度约 `6.53m/s`，平均电机遥测峰值约 `6985 RPM`。

## 最新进展（2026-06-21，ACRO 电机差动控制余量）
本轮继续从“真实穿越机为什么不像几何平移”的方向收敛。前几轮已经补了机身/桨盘横流气动、动态入流、残余扭矩和横流滞后；这次补的是主动操控侧的一层：ACRO 的 pitch/roll rate 不能完全像理想角速度源，真实飞机需要靠四个电机和桨盘的差动推力来建立角速度，高速横流和低 RPM 都会影响这部分余量。
- `PlayableFlightModel` 新增 `acroMotorRateAuthorityScale`，并在主动 pitch/roll rate 进入 `responsiveAcroRate` 前应用。hover/正常油门下直线高速前飞保持 `1.0` 权限，不削弱巡航；零油门附近保留约 `72%` 的 AirMode 式控制权，避免低油门完全失控；高速侧滑/大迎角横流在滞后建立后最多吃掉约 `10%` 主动 rate 权限。
- 这不是自稳，也不是限速。它只让 `16m/s right + 16m/s forward` 这类斜向横流里的主动满杆滚转/俯仰带一点电机差动余量负载：第一帧因横流 lag 影响很轻，持续横流后才逐步变重。这样高速斜飞或翻滚转换时，姿态响应更像电机和桨在带着 5 寸机的重量转，而不是屏幕坐标直接旋转。
- 回归测试新增 `acroMotorRateAuthorityKeepsAirmodeButLoadsSettledCrossflow` 和 `acroHighSpeedCrossflowMakesActiveRollRateFeelLoadedButControllable`：锁住低油门仍可控、hover/直线高速不被误伤、稳态斜向横流满杆更重但仍保留连续控制权。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-025034.json`，ACRO playable 诊断通过，最大水平位移约 `16.01m`，最大速度约 `6.69m/s`，平均电机遥测峰值约 `6946 RPM`。

## 最新进展（2026-06-21，ACRO 动态横流负载滞后）
本轮继续沿着“斜飞/翻滚转换不要像平移或瞬间被空气抽回”的主线收敛。上一轮已经把横流滞后接到旋翼盘 flapping、盘面阻力、动态入流和推进损失；这次继续补上仍然瞬时生效的动态负载路径，让高速转动时的空气/桨盘负载也有建立过程。
- `PlayableFlightModel` 现在把 `acroAeroCrossflowLag` 传入 `acroResidualTorqueRateLoadFraction`、`acroYawTurnLoadBodyAcceleration` 和 `acroBodyRateLoadBodyAcceleration`。这些路径原本在机体已经有高速侧滑/大迎角时会直接按完整横流暴露计算负载，现在只让直线流动基础项即时生效，侧滑/迎角额外项跟随 lag 建立。
- 具体效果是：高速直线 pitch/roll/yaw 动作仍有基础速率负载，不会被削成无反馈；但 `16m/s right + 16m/s forward` 这类斜向横流里的 residual torque、yaw turn load、body-rate load 不会第一帧满量咬住机体。持续侧滑或持续大迎角后，这些真实空气负载仍会逐渐恢复完整强度。
- 这次没有调最高速度、没有再加普通 CdA，也没有把 ACRO 做成自稳。它是把已有的真实动态负载从“几何瞬时开关”改成“流场逐步建立”，目标是减少翻滚一圈后横向力突然打满造成的抽动，同时保留高速机动时空气咬住机体的重量感。
- 回归测试新增 `acroLaggedCrossflowSoftensFirstTickResidualTorqueLoad`、`acroLaggedCrossflowSoftensFirstTickYawTurnLoad`、`acroLaggedCrossflowSoftensFirstTickBodyRateLoad`，并继续保留旧的稳态重载测试，确保第一帧柔和、稳态仍真实、直线高速巡航不被误伤。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-024452.json`，ACRO playable 诊断通过，最大水平位移约 `17.88m`，最大速度约 `6.40m/s`，平均电机遥测峰值约 `6957 RPM`。

## 最新进展（2026-06-21，ACRO 横流滞后扩展到旋翼盘）
本轮继续收敛你实测的“翻滚一周后持续侧飞/回抽、旋转不够通畅”的问题。上一轮已经让机身气动力的侧滑/大迎角效应不再瞬时打满；这次把同一个横流滞后状态继续接到旋翼盘和被动力矩上，避免出现“机身气动力慢建立，但桨盘横流负载第一帧全量生效”的不一致。
- `PlayableFlightModel` 现在会在 ACRO tick 前半段基于上一帧速度和姿态先更新 `acroAeroCrossflowLag`，再把它传入 pitch/roll rate 响应。横向入流滚转力矩与高迎角 pitch 被动力矩都会按该滞后量缩放，因此刚从翻滚/侧滑进入横流时不会立刻被被动力矩抽走；稳定侧滑后仍会逐步建立真实的空气咬合。
- ACRO 速度积分中的 `acroAdvanceRatioThrustScale`、`acroDynamicInflowThrustScale`、`acroRotorFlappingBodyAcceleration`、`acroRotorInPlaneDragBodyAcceleration` 增加带 lag 的重载。直线高速前飞的基础盘面流仍保持即时生效，只有侧流额外项跟随 `acroAeroCrossflowLag` 建立；这能保留速度感，同时减少翻滚后横向力瞬时满载造成的回抽。
- 本轮刻意没有把 `acroTranslationalLiftThrustScale` 的侧流惩罚做成滞后，因为那会在第一帧错误奖励侧滑 ETL，反而可能让斜飞更像平移。现在的边界是：直线巡航响应不变，侧滑横流的额外损失、flapping、盘面阻力和动态入流下陷逐步建立。
- 回归测试新增 `acroLaggedCrossflowSoftensFirstTickRotorDiskLoads`、`acroLaggedCrossflowSoftensFirstTickRotorInPlaneDrag`、`acroLaggedCrossflowSoftensFirstTickThrustLossesWithoutTouchingStraightCruise`、`acroLaggedCrossflowSoftensFirstTickPassiveRollMoment`，并把旧的稳态高迎角/侧滑被动力矩测试显式固定为“横流已建立”场景，防止测试语义混在一起。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-023847.json`，ACRO playable 诊断通过，最大水平位移约 `17.88m`，最大速度约 `6.40m/s`，平均电机遥测峰值约 `6957 RPM`。

## 最新进展（2026-06-21，ACRO 横流气动滞后）
本轮继续针对“高速斜飞/翻滚后像屏幕平移或突然被空气抽回”的核心手感问题收敛。改动重点不是继续堆普通空阻，而是把已经存在的侧滑/大迎角气动力做成有建立时间的状态量：真实机体、桨盘附近流场和分离流不会在一个 tick 内从 0 瞬间满量生效。
- `PlayableFlightModel` 新增 `acroAeroCrossflowLagTarget` 和状态字段 `acroAeroCrossflowLag`。目标值只在 ACRO 下、高速且有明显侧滑/迎角时建立：速度窗口约 `4..14m/s`，横流角窗口约 `8..55deg`；上升平滑 `0.34`，衰减平滑 `0.16`，让横流气动负载快进慢退。
- ACRO 物理层现在用该滞后量缩放耦合动压阻力、分离流阻力、pitch-plane lift、sideslip sideforce 和 sideforce-induced drag；基础机身阻力保持即时生效。这样直线巡航不变，高速斜飞第一帧不会像撞上空气墙，但持续侧滑会逐步洗掉横向能量。
- `DroneEntity` 增加 `debugAcroAeroCrossflowLag` 并在服务端实体 tick 中持久化，避免纯模型测试有状态、进游戏后每帧被重置的问题。`State`/`Step` 同步扩展，并保留旧构造函数兼容现有测试。
- 回归测试新增 `acroAeroCrossflowLagTargetNeedsFastCrossflow`、`acroAeroCrossflowLagBuildsOverTicks`、`acroLaggedCrossflowReducesFirstTickSideforceWithoutRemovingBaseDrag`，锁住“慢建立但不取消基础阻力”的边界。`16m/s right + 16m/s forward` 场景下第一 tick 横流滞后约 `0.30`，后续几帧继续建立，稳定后才接近完整气动洗出。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-022729.json`，ACRO playable 诊断通过，最大水平位移约 `16.76m`，最大速度约 `6.31m/s`，平均电机遥测峰值约 `6952 RPM`。

## 最新进展（2026-06-21，ACRO 高速交叉流残余扭矩负载）
本轮继续把“斜向飞行像平移”的问题往真实穿越机手感收敛，但没有继续堆普通空气阻力。参考本仓库 `docs/fpv-sim-targeted-calibration-gap-hunt.md` 和 `docs/fpv-sim-model-validation.md` 里的 NeuroBEM/RATM 残余力/力矩资料：这些资料提示高速残余气动不能简单合并成一个 CdA，尤其 residual torque / torque-damping 会在有角速度和交叉流时给机体动作增加一点负载。
- `PlayableFlightModel` 新增 `acroResidualTorqueRateLoadFraction`，放在 passive AOA/transverse-flow 力矩之后、rotor gyro 负载之前。它只看当前机体系速度、pitch/roll rate 和侧滑/迎角暴露：低速、直线高速巡航、低角速度、以及单纯的弱被动横流力矩都不触发。
- 当前标定下，`16m/s right + 16m/s forward` 且 pitch/roll 均约 `6deg/tick` 的高速斜向动作会额外吃掉约 `2%` 角速度；`12m/s up + 16m/s forward` 的高迎角动作约 `1%` 级别。这个量级是“空气扭矩咬住一点”的重量感，不是自动回正，也不会让 ACRO 满杆失去连续翻滚能力。
- 回归测试新增 `acroResidualTorqueRateLoadRequiresHighSpeedCrossflowAndBodyRate`，并复测高 RPM 双轴负载、横向入流滚转力矩、完整 `PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-021715.json`。

## 最新进展（2026-06-21，ACRO 横向入流滚转力矩 mu 窗口）
本轮继续收敛“高速斜飞像贴图平移”的手感，但没有再加普通刹车。对照本仓库 `docs/data/kolaei2018_inflow_angle_rotor_packet.csv` 和 `docs/fpv-sim-data-sources.md` 里的 Kolaei 2018 入流角资料后，重点修正 playable ACRO 横向来流滚转力矩的前进比定义：该资料用 `mu = V/(Omega*R)` 描述横向/大入流角下的 `CMx` 滚转力矩趋势，当前可玩层应把它作为形状约束，而不是让 powered disk moment 在高 `mu` 区间无限饱和。
- `PlayableFlightModel` 现在让 `acroTransverseFlowRollMomentRate` 复用已有的 `acroRotorDiskAdvanceRatioMu`，避免横向来流力矩和前面 ETL/flapping/H-force 使用两套不一致的盘面前进比口径。
- 新增 `acroTransverseFlowPoweredMuShape`：`mu <= 0.055` 不触发，`mu ~= 0.22..0.30` 保持完整 powered disk moment，超过 Kolaei 资料主要覆盖的 `mu ~= 0.30` 后逐步降到保守残留量。这样 `16m/s right + 16m/s forward` 这类斜飞仍有约 `0.5deg/tick` 的被动滚转咬合感，而 `30/30m/s` 的超高速斜飞不会因为外推过头继续线性变强。
- 这不是自动回正：主动 roll stick 仍会按原有 `ACRO_TRANSVERSE_ROLL_ACTIVE_KEEP` 抢回控制权，低油门也只保留很小的 airframe residual moment；目标只是让高速横向入流下的桨盘非对称力矩更像真实旋翼数据提示的形状。
- 回归测试新增 `acroTransverseFlowPoweredMuShapeUsesKolaeiRangeWithoutHardExtrapolation`，并扩展 `acroTransverseFlowRollMomentDependsOnPoweredDiskAdvanceRatio` 覆盖高 `mu` 外推边界。已通过 targeted transverse-flow 测试、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-020952.json`。

## 最新进展（2026-06-21，ACRO 旋翼平移升力与诱导阻力）
本轮继续补真实穿越机手感里的“空气和桨盘在参与飞行”，不是再加速度倍率。RotorPy 的气动模型说明里把 translational lift/drag 列为高速/强机动时会变明显的旋翼气动项；结合 UIUC 小桨 advance-ratio 衰减和本仓库已有的 5 寸机 CdA/惯性锚点，这轮给 ACRO 可玩层加入一个很保守的有效推力增益：中等干净横向来流会让桨盘效率略升，但侧滑和高 advance ratio 会把它淡出。
- `PlayableFlightModel` 新增 `acroTranslationalLiftThrustScale`，并把它乘进 ACRO 物理层有效推力。静止、低速、零油门不触发；`mu ~= 0.085..0.135` 的干净前向盘面流达到峰值，最大只给约 `5.5%` 推力增益；高 advance ratio 到 `mu ~= 0.36` 前逐步退回 `1.0`，避免覆盖 UIUC/forward-flow 的高速推力衰减。
- 本轮进一步补上配套的 `acroTranslationalLiftDragBodyAcceleration`：ETL 不是白送推力，出现有效推力增益时会沿盘面来流方向加入很小的诱导阻力。当前标定下，`12.5m/s` 干净前向盘面流、`12.5m/s^2` 推力加速度时会产生约 `0.22m/s^2` 的前向阻力；斜向来流因 ETL 增益被压低，对应阻力也更小。
- 侧滑不会被奖励成“平移”：同样速度下，`9m/s right + 9m/s forward` 的斜向来流增益明显低于 `12.5m/s` 直线前飞；`25m/s` 纯侧向横流只保留极弱增益。这样轻微前飞/巡航会有一点真机 ETL 的效率感，高速斜飞仍然主要由侧滑阻力、总动压耦合阻力、flapping、盘面阻力和动态入流决定。
- 回归测试新增 `acroTranslationalLiftBoostsCleanMidSpeedFlowWithoutFlatteningSideSlip` 和 `acroTranslationalLiftDragCostsEnergyWhenLiftBoostAppears`，同时复测了 ACRO 巡航速度、高速斜向机体气动、25m/s coastdown、steep pitch 不变世界竖直爬升等边界，确保没有把前压机头又做回“推油门还往上飞”。
- 已通过 targeted translational-lift/cruise/diagonal/coastdown 测试、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-020259.json`。

## 最新进展（2026-06-21，ACRO 机体系总动压耦合阻力）
本轮继续针对“斜向飞行像平移，不像真机在空气里咬着飞”的核心手感收敛。之前的 ACRO 已经有 1.10kg 级 5 寸机惯性、机体系 CdA、侧滑侧向力、旋翼盘阻力、flapping、动态入流和高转速双轴负载；这次补的是更基础的一层：机体阻力不能只看各轴自己的速度平方，高速斜飞时总空速本身也会抬高机体系每个暴露方向的动压。
- 参考方向来自 RotorPy 这类带气动 wrench 的多旋翼仿真：其 README 明确把寄生阻力视为随相对空速快速变强的二次阻力，并把 rotor drag、flapping、induced drag、translational lift/drag 都列为高速/强机动时不可忽略的项；UIUC Propeller Database 继续作为小桨 advance ratio 衰减参考，UZH-FPV/RATM 这类竞速数据继续作为 `20m/s+` 速度包络锚点。
- `PlayableFlightModel` 新增 `acroCoupledDynamicPressureDragAcceleration`。直线 `25m/s` 前飞、低速斜飞都不触发；当 body-frame 侧滑或迎角超过约 `8deg` 且速度进入 `6..24m/s` 区间时，按 `totalSpeed - abs(axisSpeed)` 给横向、竖向、前向二次阻力补上总动压差额，并限制每轴额外减速度不超过 `2.20m/s^2`。
- 当前标定下，`16m/s right + 16m/s forward` 的斜向来流会额外产生约 `-0.78m/s^2` 横向、`-0.37m/s^2` 前向减速度；完整机体气动加速度从约 `x=-7.5,z=-3.1m/s^2` 收紧到约 `x=-8.36,z=-3.5m/s^2`。这会让高速斜飞更快洗掉侧向能量，但不会把直线巡航改成刹车。
- 回归测试新增 `acroCoupledDynamicPressureDragLoadsCrossflowWithoutChangingStraightCruise`，并同步收紧 `acroDiagonalHighSpeedFlowGetsExtraSeparatedDragAndSideforce`。已通过 targeted crossflow/diagonal/coastdown 测试、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-015123.json`。

## 最新进展（2026-06-21，ACRO 动态入流推力下陷）
本轮继续减少“高速斜向改向像几何平移”的感觉，但仍然不靠继续加普通机体空阻。参考本仓库 `docs/fpv-sim-model-validation.md` 的 Motor and inflow response sanity：`racingQuad` 的 `rotor_inflow_tau=0.035s` 约为一倍半径 wake transit 的 `5.14x`、一倍直径 wake transit 的 `2.57x`，说明可玩层应保留一点入流/尾流滞后，而不是让推力矢量在高速机动中完全瞬时有效。
- `PlayableFlightModel` 新增 `acroDynamicInflowThrustScale`，并把它乘进 ACRO 物理层的有效推力。它只在高速、有 pitch/roll 体轴角速度、且存在明显侧滑/迎角来流时产生几个百分点的推力下陷。
- 低速、静止、无角速度、普通巡航不触发；高速直线单轴动作只约 `1%` 量级，高速斜向双轴动作约 `3%..6%`，满油门斜向双轴动作仍限制在约 `7.5%` 内。目标是增加“桨盘入流还没完全跟上”的重量感，而不是把飞机刹停。
- 回归测试新增 `acroDynamicInflowThrustSagRequiresSpeedAndBodyRate`，锁住静止/无角速度为 `1.0`、直线高速单轴为弱影响、斜向双轴为更明显但受控的推力下陷；完整 `PlayableFlightModelTest` 继续覆盖起飞、巡航、整圈翻滚恢复和速度惯性边界。
- 已通过 targeted rotor/inflow 测试、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-014245.json`。

## 最新进展（2026-06-21，ACRO 高转速双轴陀螺负载）
本轮继续围绕“斜向飞行和双轴机动像平移/欧拉角滑块”的手感收敛，没有再继续加普通空阻。参考本仓库 `docs/fpv-sim-model-validation.md` 里的 rotor inertia and gyroscopic torque sanity：当前 5 寸 racingQuad 的单桨惯量约 `5.376e-06 kg*m^2`，最大转速下单桨角动量约 `0.016 N*m*s`，720deg/s 体轴角速度时单桨陀螺力矩量级可到 `0.206 N*m`；但四桨对转会大量抵消，所以运行时只落成很弱的可玩层负载。
- `PlayableFlightModel` 新增 `acroRotorGyroRateLoadFraction`：在 ACRO 的最终 pitch/roll rate 输出前，根据当前 RPM、体轴总角速度和 pitch+roll 双轴比例加入一个小比例缩放。高 RPM 对角满杆会损失约 `3%..8%` 角速度，单轴满杆只有约 `1%` 量级。
- 这不是自动回正，也不是速度刹车：低油门、hover 附近和中油门基础 ramp 基本不触发；单轴 pitch/roll 连续翻滚仍保留，主要让高油门双轴动作更有“桨盘和转子在带重量”的感觉。
- 回归测试覆盖：hover RPM 不触发；单轴和双轴负载分档；`0.68` 油门双轴满杆在完整 `step()` 中比单轴略重但仍保持 `94%+` 权限；旧的中油门 ACRO rate ramp 继续通过，避免让基础操控变钝。
- 已通过 targeted gyro/rate 测试、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-013713.json`。

## 最新进展（2026-06-21，ACRO 高迎角弱被动 pitch 载荷）
本轮继续收敛“高速斜飞/翻转后仍有点像理想刚体平移”的 ACRO 手感，但刻意避免把它做成自动回正。上一版高迎角 pitch 载荷只在玩家打 pitch 杆，或上一帧还留有 pitch rate 尾巴时生效；这样能防止自稳味道，但在某些松杆后的高迎角来流里又会显得机体太“无空气”。
- `PlayableFlightModel` 新增 `acroAngleOfAttackPitchMomentScale`：高迎角 pitch moment 现在有一个很弱的被动保留量，零 pitch / 零杆量下约保留 36% 的载荷，让 `18m/s` 前向、`10m/s` 垂向来流这类场景能产生约 `0.03deg/tick` 的轻微 pitch 响应。
- 这条被动项加入了“禁止自动回正”边界：如果当前 pitch 姿态已经明显偏离 0，而被动 AOA 力矩方向会把 pitch residual 拉回水平，就直接禁用。主动打 pitch 杆或仍有残余 pitch rate 时，原有完整气动载荷仍生效，用来表现动作中的空气负载。
- 回归测试新增两层保护：中立姿态高迎角允许弱被动 pitch rate；普通 ACRO 高速巡航和 HORIZON 切 ACRO 的居中杆软捕获不能被这条弱被动项慢慢拉回 pitch，避免重新出现“穿越机自己回正”的手感。
- 已通过 targeted AOA/cruise/mode-switch 测试、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-012837.json`。

## 最新进展（2026-06-21，目视前飞 pitch 符号回归修正）
本轮先修一个明确的目视飞行回归：你反馈“目视状态下无人机向前时从压头变成了抬头”。问题集中在客户端 `DroneEntityModel` 的 pitch 符号，前一轮测试把 renderer 的 `scale(-1,-1,1)` 和模型机头前向点的最终 Y 偏移语义判断反了。
- `DroneEntityModel.bodyPitchRotationRadians` 现在重新使用 `-pitchRadians`，让 playable 正 pitch / 前飞在第三人称目视模型上表现为机头下压，而不是抬头。
- `DroneEntityModelTest` 同步改为固定这条实飞语义：正 playable pitch 的模型 pitch 为负值，经过 renderer transform 后机头前向点落到目视压头方向；负 playable pitch 对称显示为抬头。
- 这个改动只影响 LOS/第三人称实体模型显示，不改变 FPV 相机矩阵，也不改变服务端 ACRO 物理、速度、空阻、惯性或斜滑侧向力。

## 最新进展（2026-06-21，ACRO 斜滑侧向力重标定）
本轮继续处理“斜向飞行像平移、不像机体在空气里转弯”的手感问题。上一轮增强了大姿态下 pitch/roll 到航向的耦合；这轮把 yaw-plane sideforce 做得更明显一些，让 `16m/s right + 16m/s forward` 这类斜向来流的速度矢量更愿意往机头方向弯，而不是只靠横向阻力把侧向速度刹掉。
- `PlayableFlightModel` 将 ACRO 侧滑侧向力增益从 `0.092` 提到 `0.128`，诱导阻力增益从 `0.50` 提到 `0.56`，诱导阻力上限从 `1.60m/s^2` 提到 `1.85m/s^2`。侧向力本身仍保持近似零做功，用来改变速度方向；诱导阻力只给明显斜滑追加能量成本。
- 当前标定下，`16/16m/s` 斜向来流的 sideforce 分量约为 `0.47m/s^2`，诱导阻力分量约为 `0.21m/s^2`。这比上一版更能让机体“咬住空气”拐过去，但不是把 ACRO 改成硬刹车或自动航向锁定。
- 回归测试继续守住三条边界：直线 `25m/s` 前飞不触发侧滑诱导阻力；斜滑 sideforce 仍对速度近似零做功；`acroHighSpeedCoastPreservesInertiaDistanceWhileSideslipWashesOut` 和 thrust-vector turn load 仍通过，避免牺牲已经调好的速度感和惯性距离。
- 已通过 targeted sideforce/diagonal/coastdown 测试、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-011413.json`，ACRO 诊断通过，最终速度 `0.00m/s`，最大水平位移约 `16.79m`，最大速度约 `6.35m/s`。

## 最新进展（2026-06-21，ACRO 大姿态机体系航向耦合）
本轮继续针对“全向旋转不通畅、高速下仍有回抽、翻滚后像持续侧飞”的 ACRO 手感收敛。前几轮已经把速度积分、推力轴、机体系空阻、侧滑、桨盘横流和整圈捕获逐层拆开；这次补的是大倾角下 pitch/roll 角速度和机头航向之间的耦合，让 banked pitch、接近垂直时的 roll 不再像简单欧拉角滑块，而更像真实穿越机会把姿态变化投成航向变化。
- `PlayableFlightModel` 将 `ACRO_BODY_RATE_YAW_COUPLING_SCALE` 从 `0.24` 提到 `0.28`，并把被动 body-rate yaw coupling 上限从 `1.35deg/tick` 提到 `1.55deg/tick`。这不是自动回正，也不是航向锁定；只有 ACRO 大 bank/大 pitch 且玩家没有主动 yaw stick 抢权时才生效。
- 当前标定下，`60deg` 右横滚时给 `4deg/tick` pitch rate 会产生约 `0.97deg/tick` 的自然航向变化；`88deg` 接近垂直姿态下给 `4.5deg/tick` roll rate 会产生约 `0.88deg/tick` 的航向变化。主动 yaw 仍保持优先，满 yaw 输入仍在 `4.8..5.45deg/tick` 的玩家控制窗口内。
- 回归测试同步收紧了 banked pitch、vertical roll、主动 yaw 抑制和 planar Euler slide 边界，防止后续又退回“大角度只是在屏幕平面里滑”的手感。
- 已通过 targeted body-rate/yaw 测试、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测报告为 `server-selftest-playable-20260621-010749.json`，ACRO 诊断通过，最终速度 `0.00m/s`，最大水平位移约 `16.79m`，最大可视 yaw rate 约 `55.26dps`。

## 最新进展（2026-06-21，目视飞行 pitch 显示符号修正）
本轮补上一个明确的目视飞行问题：ACRO 前飞/正 pitch 在第三人称目视里不应该显示成抬头。这个改动只作用于实体模型渲染符号，不改变 FPV 相机矩阵、不改变服务端飞控物理、不改变速度/空阻/惯性参数。

- `DroneEntityModel.bodyPitchRotationRadians` 现在直接使用 playable pitch 符号；结合 renderer 的 `scale(-1,-1,1)`，正 pitch 会让机头最终落到世界 Y 负方向，也就是目视中的压头，而负 pitch 会显示为抬头。
- 更新 `DroneEntityModelTest` 的语义：正 playable pitch 必须在 renderer 变换后让机头下压，负 playable pitch 必须对称抬头，避免以后再次把模型空间/世界空间的 Y 轴符号写反。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.client.render.DroneEntityModelTest` 和完整 `gradlew build`（7 个 Fabric GameTest 通过）。下一次客户端实飞重点确认：目视模式前飞姿态与 FPV/物理前进方向一致，且这个修正没有影响 FPV 第一人称画面。

## 最新进展（2026-06-21，ACRO 低油门横流机架残余力矩）
本轮继续处理“斜向/侧向飞行像屏幕平移”的手感问题，但没有再加普通速度刹车。上一版已经让 powered disk 在高速侧滑时产生横向来流 roll moment；这次补的是更弱的机架残余力矩：真实穿越机即使松油或低油门，高速侧滑时机架、机臂、相机座和桨盘附近的残余气动力也会轻微拧动姿态，不应该完全退化成只剩线性阻力的滑块。

- `PlayableFlightModel` 的 `acroTransverseFlowRollMomentRate` 现在拆成两层：原有的 powered disk moment 继续由 RPM、`mu`、侧滑角和速度控制；新增 airframe residual moment 只看高速横流和侧滑角，量级更小，用来覆盖低油门/松油侧滑时“空气仍在拧机体”的感觉。
- 当前标定下，`16m/s right + 16m/s forward` 的 powered 侧滑 roll moment 从约 `0.40deg/tick` 提高到约 `0.516deg/tick`；idle/无动力同场景保留约 `0.112deg/tick` 的弱残余。满 roll 主动输入仍会把这条被动力矩压到约 `8%`，不会抢杆，也不会把 ACRO 改成自稳。
- 回归测试同步锁住三类边界：低速/直线前飞不触发；idle 残余必须存在但 powered disk 仍至少是 idle 的 4 倍；完整 `step()` 中无 roll stick 的高速侧滑会产生更明显但受控的被动滚转。
- 已通过完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`；本次服务端自测报告为 `server-selftest-playable-20260621-005734.json`，ACRO 诊断飞行通过，最终速度 `0.00m/s`，最大水平位移约 `16.73m`，平均电机遥测峰值约 `6993 RPM`。

## 最新进展（2026-06-21，ACRO 大侧滑风标偏航重标定）
本轮继续收敛“斜向飞行像屏幕平移、不像真机在空气里被带着转”的手感问题。上一轮已经把直线前飞的惯性拉长，问题就变得更清楚：如果纯侧滑和大侧风状态下机头几乎不受相对气流影响，玩家高速斜飞时会觉得速度矢量只是被平移，而不是整台 5 寸机在空气里带着重量、侧滑角和转弯半径运动。

- `PlayableFlightModel` 提高了 ACRO broadside weathercock 的基准量级：纯横向 `16m/s` 侧滑的被动 yaw 现在约 `-0.175deg/tick`（约 `3.5deg/s`），不再是旧版接近感觉不到的 `~1deg/s`；`16m/s right + 16m/s forward` 这类斜向来流仍保持更强的机头迎风趋势。
- 这不是自动回正，也不是把 ACRO 改成航向锁定：只有明显侧滑/高速来流才触发，主动 yaw 输入仍然优先，满 yaw 仍保持 `>4.5deg/tick` 的玩家权限；松杆时只是给机体一个更真实的“相对气流会把机头带向迎风方向”的弱力矩。
- 同步收紧 yaw damping 测试边界，让纯侧滑下的 yaw 阻尼从旧量级提升到约 `0.063`，避免高速侧飞时机头像无空气阻力的贴图一样继续横着滑。
- 已通过完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`；本次服务端自测报告为 `server-selftest-playable-20260621-004820.json`，ACRO 诊断飞行通过，最终速度 `0.00m/s`，最大水平位移约 `16.73m`，平均电机遥测峰值约 `6993 RPM`。

## 最新进展（2026-06-21，ACRO 正向基准空阻与滑行惯性）
本轮继续收敛“速度够了但手感仍像被空气刹住、翻滚后侧飞标准不够严”的问题。重点没有再堆普通刹车，而是把 playable ACRO 的直线基准空阻和斜向/侧向气动负载拆得更清楚：直线前飞松杆应该保留更长惯性，斜向来流仍要靠侧力、诱导阻力、分离流和桨盘力矩去洗掉横向分量。

- `PlayableFlightModel` 将 ACRO 正向机体阻力面积从 `0.0144m^2` 降到 `0.0128m^2`，正向线性阻尼从 `0.075/s` 降到 `0.060/s`；`25m/s` 直线前飞的机体系正向减速度现在约 `-5.95m/s^2`，1 秒松杆滑行后速度约 `19.82m/s`，比上一版更有穿越机的滑行惯性。
- 横向/斜向负载没有一起削弱：侧向阻力、侧滑侧向力、sideforce-induced drag、横向来流 roll moment 仍保留原量级；pitch-plane lift 也用 `0.085 -> 0.090` 的小幅增益补偿回原来的迎角升力量级，避免降低正向阻力时误伤机身迎角气动。
- 翻滚后侧飞的回归标准进一步收紧：两个高速整圈 roll 释放测试（含 yaw reframe 场景）把允许残余 body-right 侧向速度从 `<0.75m/s` 收紧到 `<0.25m/s`，避免“测试算过了但实飞仍觉得横着拖”的灰区。
- 已通过相关 targeted 回归、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和无头 `:fabric-mod:runPlayableAcroServerSelfTest`；本次服务端自测报告为 `server-selftest-playable-20260621-004015.json`。

## 最新进展（2026-06-21，ACRO 横向来流桨盘滚转力矩）
本轮继续处理“高速斜飞像平移”的手感核心，但没有再加普通速度刹车。对照本仓库 `docs/data/kolaei2018_inflow_angle_rotor_packet.csv` 里的 Kolaei 2018 inflow-angle 旋翼资料后，重点补强 playable ACRO 中的横向来流滚转力矩：该资料明确记录同定义 `mu = V/(Omega*R)`，并把 `CMx` 滚转力矩列为横向/大入流角下的重要测量项。之前 playable 层已经有简化 `roll moment`，但量级偏轻，而且不看桨盘转速，容易在玩家侧感知成“侧着平移”。

- `PlayableFlightModel` 现在让 `acroRateResponse` 把油门和悬停油门传入横向来流力矩计算；`acroTransverseFlowRollMomentRate` 不再只看速度和侧滑角，而是同时看桨盘是否真正有动力、以及当前 `mu` 是否进入横向来流区间。
- 被动 roll moment 的默认高速侧滑量级从约 `0.23deg/tick` 提到约 `0.40deg/tick`，也就是从几乎感觉不到的轻微扰动提升到能让 20m/s 级斜向来流明显“带着机体滚”的程度；主动 roll 输入仍然优先，满杆时这条被动力矩只保留约 `8%`，不会抢杆或把 ACRO 改成自动回正。
- 新增回归测试确认：直线巡航、低速侧移、低 `mu` 小速度和 idle 空转桨盘都不触发；`16m/s right + 16m/s forward` 的 powered disk 会触发可感 roll moment；完整 `step()` 中无 roll stick 的高速侧滑会产生更明显但受控的被动滚转。
- 已通过定向横向来流测试和完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`。下一步仍应在客户端实飞里重点试高速 45 度斜飞、侧滑松杆、带油门的 bank turn，确认这个被动桨盘力矩是否让飞机更像“在空气里被扯着转弯”，而不是屏幕平移。

## 最新进展（2026-06-21，ACRO 高速推力改向负载）
本轮继续收敛“速度够了，但高速斜向飞行还是有平移感，不像真实 5 寸穿越机在空气里带着重量转弯”的问题。参考方向来自本仓库的 RATM/NeuroBEM 高速残余力资料、`docs/fpv-sim-model-validation.md` 中对高前进比/机体阻力的校准记录，以及 RotorPy 把 multirotor 气动力拆成机体阻力、rotor drag、blade flapping、induced/translational drag 和电机动态的建模方式。

- `PlayableFlightModel` 新增 ACRO thrust-vector turn load：当无人机已经在 `8..24m/s` 以上高速水平飞行，而当前推力水平分量主要在“横着改向”而不是顺着速度加速时，用 `|v x a_thrust| / |v|` 估计转弯负载，并沿当前水平速度反向加入一个很小的能量成本，最大约 `1.25m/s^2`。
- 这不是普通速度刹车，也不是自动回正：低速起飞、悬停、直线加速、顺速度方向给油都不会触发；只有高速 bank/斜飞时推力大量用于改变速度方向，才会感觉更重、需要提前量，减少“屏幕平移”的味道。
- 新增回归测试覆盖三类边界：低速/顺向推力不触发；`25m/s` 高速且推力近似垂直速度时出现约 `0.9m/s^2` 的负载；完整 `step()` 中 banked turn 会保留横向转向能力，但同时付出可测的前向能量成本。
- 已通过完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和 JDK 21 无头 `:fabric-mod:runPlayableAcroServerSelfTest`；本次服务端自测报告为 `server-selftest-playable-20260621-001841.json`。这一轮还不是最终手感完成版，但比之前更接近“真实穿越机高速改向会吃能量、会有转弯半径和空气负载”的方向。

## 最新进展（2026-06-20，高速横滚释放预测与侧滑耦合收敛）
本轮继续处理“尝试翻转一周之后会持续侧飞、无法回正”的复发反馈。新增复现发现：如果满 roll 后在约 `230..245deg` 的高速释放尾段松杆，旧模型还没有跨过 `250deg` 的 filtered-release 捕获阈值；虽然上一帧横滚率仍有 `8deg/tick+`，下一帧会停在约 `238deg` 的侧飞姿态，并留下 `6m/s+` 的局部侧向速度。

- `PlayableFlightModel` 给 filtered release tail 增加了 `2 tick` 的角速度预测：只有当摇杆已经回中/处于释放尾巴，且上一帧角速度明显高于当前目标角速度时，才用这个极短预测判断是否进入完整 roll recovery。这样能吃掉“看起来已经翻完、实际只差一点”的侧飞边界；主动刀锋、低角速度停姿态和继续打 roll 仍不会被自动拉平。
- 同步收紧高速斜飞的气动耦合：ACRO sideforce gain 从 `0.065` 提到 `0.092`，sideforce-induced drag 从 `0.42/1.35m/s^2` 提到 `0.50/1.60m/s^2`。它不是普通速度刹车，而是让 `16m/s right + 16m/s forward` 这种斜向来流更快洗掉横向分量，并付出可感但不过强的能量成本。
- 新增回归测试 `filteredReleaseProjectedNearCompletedRollCapturesInsteadOfParkingSideways`：满 roll 26 tick 后松杆，要求进入 recovery window、roll rate 清零、body-right 侧向速度压回 `0.32m/s` 内；同时继续保留 `activeNearKnifeEdgeRollCommandDoesNotUseFilteredReleaseCapture`，防止主动刀锋被误吞。
- 已通过 targeted ACRO 回归、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和 JDK 21 无头 `:fabric-mod:runPlayableAcroServerSelfTest`。第一次自测在 `25565` 端口被占用时失败于 bind，随后临时改用 `25566` 通过，结束后已恢复 `25565`；报告为 `server-selftest-playable-20260620-235109.json`。

## 最新进展（2026-06-20，5 寸桨前飞/侧流推力滚降重标定）
本轮继续处理“斜向飞行像平移、不像真机吃风”的手感问题。重新对照本仓库 `docs/fpv-sim-model-validation.md` 里的 UIUC 5 寸前飞拟合、RATM 高速窗口和 airframe drag calibration packet 后，发现 playable ACRO 的 advance-ratio 推力损失仍然太慷慨：旧测试允许 `12.5m/s` 巡航来流时仍保留约 `0.86..0.91` 的推力比例，而 UIUC 5 寸参考在等效 `J≈0.45` 附近的 CT/static CT 均值约 `0.55`。这会让高速侧飞/斜飞时桨盘几乎不吃风，视觉和操作上就容易像在平面里平移。

- `PlayableFlightModel` 将 ACRO advance-ratio loss 的 full 区间从 `J=0.82` 提前到 `J=0.62`，最大前向/侧向来流推力损失从 `0.30/0.42` 提高到 `0.48/0.62`。低速 `5m/s` 仍基本不损失推力；`12.5m/s` 巡航推力比例现在收敛到约 `0.64..0.71`；`25m/s` 侧向/斜向来流会明显更重，减少贴图式横移感。
- 这不是普通速度刹车，也不是降低最高速度：`acroCruiseCanReachFpvSpeedWithoutInstantVelocitySnap` 仍通过，`0.68` 油门的 ACRO 巡航仍能达到 `25m/s+`。区别是高速斜向动作需要更多油门和提前量，松杆后也更容易体现真实 5 寸机的能量成本。
- 已更新 advance-ratio 回归测试区间，并通过完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和 JDK 21 无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本次服务端自测因 `25565` 被占用临时使用 `25566`，结束后已恢复；报告为 `server-selftest-playable-20260620-232810.json`。接下来仍建议客户端实飞重点试：高速 45 度斜飞、满 pitch 后带 roll 的穿门修正、以及 25m/s 左右松杆滑行，看是否更像“机体在穿空气”而不是屏幕平移。

## 最新进展（2026-06-20，整圈翻滚后持续侧飞复发修正）
本轮针对“尝试翻转一周之后会持续侧飞、无法回正”的新反馈继续收敛 playable ACRO。定位结果不是玩家手法问题，而是完整 roll 捕获后的恢复窗口把真实遥控器/输入滤波的回杆尾巴误判成新的主动 roll 命令：姿态刚被吸附回水平，接下来几帧如果 roll 轴还残留约 `0.2..0.3`，旧逻辑会立刻退出 recovery window，残余 body-right 侧向速度和横向来流力矩又会把飞机带回侧飞。

- `PlayableFlightModel` 现在给完整 roll recovery 增加了随时间衰减的回杆尾巴容忍阈值：窗口开始时最高约 `0.42`，随后逐步回到原来的 `0.18`。这样松杆/滤波尾巴不会打断恢复，但 `0.65` 这类明确主动继续 roll 的输入仍会立刻接管，不会把 ACRO 改成自动回正。
- 新增回归测试 `completedRollRecoverySurvivesModerateStickReturnTail`：先用 `428deg` 完整 roll 捕获，再连续 6 tick 保持 `0.28` 的中等回杆残留，要求 recovery window 仍然存活、roll rate 被钉回 0、body-right 侧向速度压到 `0.10m/s` 内，同时保留前向惯性。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest.completedRollRecoverySurvivesModerateStickReturnTail`、完整 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和 JDK 21 无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本次服务端自测因 `25565` 被占用临时使用 `25566`，结束后已恢复；报告为 `server-selftest-playable-20260620-232010.json`。接下来仍要在客户端实飞里重点确认真实遥控器松杆后的尾巴是否还会触发二次侧飞。

## 最新进展（2026-06-20，ACRO 高速角速度负载与目视 pitch 修正）
本轮继续围绕“速度已经够，但斜向飞行像平移、不像真实穿越机有重量和惯性”的手感问题收敛。参考方向主要来自三类资料：本仓库 `docs/data/apdrone_inertia_reference.csv` 里的 APdrone 5 寸机质量/惯量锚点（约 `0.628kg`，惯量约 `0.0013..0.0025kg*m^2`）、`docs/fpv-sim-model-validation.md` 里 RATM/AI-IO/NeuroBEM 高速日志和残余力线索，以及 [RotorPy](https://github.com/spencerfolk/rotorpy) 对 multirotor 高速空气动力的拆分方式：机架二次阻力、rotor drag、blade flapping、induced drag 和一阶电机响应在悬停时不明显，但高速/有角速度/交叉流时会快速变得可感。Betaflight 的 [Rate Calculator](https://betaflight.com/docs/wiki/guides/current/Rate-Calculator) 也继续作为 ACRO rate 手感边界参考：真实穿越机不是速度目标控制器，而是摇杆到角速度的 rate mode。

- `PlayableFlightModel` 新增 ACRO body-rate load：把当前 pitch/roll rate 和 yaw rate 转成 body-frame 角速度，用 `omega x v` 的量级估算机体在高速动作时扫过气流的附加载荷，再沿当前机体系空速反向做负功。它只在 `8m/s+`、角速度超过约 `70deg/s` 且交叉流明显时逐步出现；低速、无角速度、普通松杆滑行不会触发。
- 这个项不是自动回正，也不是把 ACRO 改成自稳刹车。满 rate 的 `-16m/s right + 16m/s forward` 斜向气流约产生 `1.2m/s^2` 量级的附加载荷，每 tick 只吃掉约 `0.06m/s`，主要作用是让高速斜飞、滚转/俯仰组合动作更有重量、提前量和能量损失，而不是像贴图在平面里平移。
- 修正目视/第三人称实体模型的 pitch 符号：旧测试只看模型局部坐标，没有覆盖 `DroneEntityRenderer` 的 `scale(-1,-1,1)` 变换，所以实际目视前飞可能显示成抬头。现在正 pitch / 前飞在最终渲染变换后重新显示为机头下压，FPV 相机矩阵不受影响。
- 新增回归测试覆盖 body-rate load 的低速/无角速度不触发、高速斜向 rate 负功、直线巡航负载更弱，以及目视模型最终渲染变换后的 pitch 方向。已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest --tests com.tenicana.dronecraft.client.render.DroneEntityModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过）和 JDK 21 无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本次服务端自测因 `25565` 被占用临时使用 `25566`，结束后已恢复；报告为 `server-selftest-playable-20260620-230818.json`。

## 最新进展（2026-06-20，ACRO 接近整圈翻滚后的侧飞捕获）
本轮针对你最新反馈的“尝试翻转一周之后会持续侧飞、无法回正”继续收敛 playable ACRO。复现点不在已经覆盖的 `360/428/540deg` 完整捕获，而是在更贴近玩家手感的释放尾段：如果 FPV 延迟和摇杆滤波让玩家在约 `260..270deg` 以为已经翻完并松杆，旧模型可能因为没跨过 `275deg` 释放捕获阈值而停在接近刀锋/侧飞姿态，角速度几乎归零但机体系侧向速度仍很大。

- `PlayableFlightModel` 将“滤波释放尾段视为整圈完成”的最低角度从 `275deg` 放宽到 `250deg`。它只在松杆/释放尾巴且上一帧角速度明显高于当前指令时生效，主动 roll 输入、连续翻滚和故意保持刀锋仍不会被偷走。
- 完整 roll recovery window 从 `12 tick` 延长到 `28 tick`，并且在窗口内把 roll 残差和 roll rate 一起钉回 0；这只作用于已经捕获的整圈 roll 恢复阶段，用来清掉残余侧滑和被动横流 roll 力矩，不是普通 ACRO 松杆自稳。
- 新增回归测试覆盖两类之前漏掉的场景：实体真实 tick 顺序里的 yaw 重投影不会让高速整圈 roll 后重新侧飞；`264deg + 残余 roll rate` 的松杆释放会捕获回水平并把 body-right 侧向速度压低，而不是停在 `8m/s+` 侧滑里。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过），以及 JDK 21 无头 `:fabric-mod:runPlayableAcroServerSelfTest`。服务端自测临时使用 25566，结束后已恢复 25565；报告为 `server-selftest-playable-20260620-225539.json`。

## 最新进展（2026-06-20，ACRO 高速斜飞转弯载荷）
本轮继续处理“速度够了但斜向飞行仍像平移、不像真实穿越机有重量和转弯半径”的手感问题。已有 playable ACRO 现在包含机体系空阻、分离流、侧滑侧向力、诱导阻力、桨盘 flapping/H-force、高前进比推力 rolloff、气动 yaw/weathercock、横向来流滚转力矩和高姿态体轴投影；这次补的是持续高速 yaw/斜向转弯时的惯性载荷。

- `PlayableFlightModel` 现在把上一帧 ACRO yaw rate 传入速度积分，在 body-frame 里按 `a = v * omega` 的转弯加速度量级加入一个很克制的能量损失项。它只在高速且 yaw rate 足够明显时出现，低速、无 yaw、普通悬停不会触发。
- 该载荷沿体轴水平速度反向作用，不会偷偷给飞机自稳或自动转向；它只是让高速斜飞/偏航时的速度矢量更有“甩出去、需要提前量、会损失能量”的重量感。
- 量级按本仓库资料里的 RATM/NeuroBEM 残余力提示保守处理：`25m/s` 直线高速 yaw 只有约 `0.6..0.8m/s^2` 额外损失，`16m/s right + 16m/s forward` 的斜向高速 yaw 约 `1.0m/s^2`，不会退回之前那种过强刹车。
- 新增回归测试覆盖：无 yaw、低速、低 yaw rate 都不触发；高速直线 yaw 有轻微能量成本；高速斜向 yaw 的成本更明显并且始终沿速度反向做负功。

## 最新进展（2026-06-20，ACRO 翻滚后侧飞与主动 yaw 优先级）
本轮继续针对你反馈的“翻转一周之后持续侧飞、无法回正”收敛 playable ACRO。之前已经修过整圈 roll/pitch 捕获、释放尾巴、侧滑清理和 FPV 姿态插值；这次补的是高姿态操纵链路里的另一个缺口：pitch/roll 仍然像两个独立欧拉角滑块一样累积，接近侧立或倒立时容易把体轴动作表现成屏幕平面侧滑。

- `PlayableFlightModel` 现在给 ACRO 的 pitch/roll rate 增加高姿态体轴投影：大 bank 时 pitch rate 会少量转化为航向变化而不是继续平面 pitch 累积；接近垂直俯仰时 roll rate 也会少量投影，减少翻滚/倒飞后“机体已经回来了但速度和姿态像横着抽不回来”的感觉。
- 投影做得很保守：约 50/60 度后才开始介入，最大只削掉约 22%/18% 的平面欧拉角速率；这不是 ACRO 自稳，松杆不会自动回水平，满杆 pitch/roll 仍保留穿越机 rate mode 的连续全向翻滚权限。
- 修正 `banked pitch / vertical roll` 的隐式 yaw 耦合：无 yaw stick 时它继续提供真实的 heading change；但玩家主动打 yaw 时，这条耦合会淡出，避免满 yaw 被 banked pitch/roll 抵消，解决“偏航像没实现/反应弱”的一部分手感问题。
- 新增回归测试覆盖：主动 yaw 优先于 banked body-rate coupling；banked pitch 和 vertical roll 在高姿态下会投影，避免纯二维欧拉角滑动；手柄 ACRO preset 中杆仍渐进、满杆仍保持 pitch/roll/yaw 权限。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`（7 个 Fabric GameTest 通过），以及 JDK 21 无头 `:fabric-mod:runPlayableAcroServerSelfTest`。服务端自测临时使用 25566，结束后已恢复 25565；报告为 `server-selftest-playable-20260620-222817.json`。

## 最新进展（2026-06-20，完整翻转后的 FPV 姿态回抽修复）
本轮针对“尝试翻转一周之后出现持续侧飞、无法回正”的新反馈，先把问题拆成物理残留和视角残留两条线验证。纯 `PlayableFlightModel` 高速前飞后完整 roll、松杆恢复的回归没有复现失控侧滑；真正发现的漏洞在客户端姿态插值：ACRO 已允许 pitch/roll 超过 360 度，但渲染 pitch 和 FPV 延迟缓冲里的 pitch 仍按普通线性数值插值，完整 pitch flip 捕获回 0 时会从 `358deg` 线性倒插到 `2deg`，视觉上穿过倒飞/侧飞姿态，像是翻完后画面一直横着抽不回来。
- `DroneEntity#getInterpolatedRenderPitchRadians` 现在和 yaw/roll 一样使用最短角度插值，避免 ACRO pitch loop 从一整圈捕获回水平时出现 360 度数值回抽。
- `FpvCameraPoseDelay` 的 pitch 延迟采样也改成角度插值；FPV 相机延迟不再把 `358deg -> 2deg` 当成反向转过 `356deg`，而是走最短的 `4deg` 过零路径。
- 新增 `FpvCameraPoseDelayTest` 覆盖 pitch/roll 完整翻转捕获边界；新增 `PlayableFlightModelTest` 高速前飞后完整 roll、松杆 30 tick 的物理回归，确认恢复窗口结束后不会重新进入被动侧飞 roll rate，且前向惯性仍保留。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.camera.FpvCameraPoseDelayTest`、`:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`、7 个 Fabric GameTest，以及 JDK 21 下无头 `:fabric-mod:runPlayableAcroServerSelfTest`。服务端自测临时使用 25566，结束后已恢复 25565；报告为 `server-selftest-playable-20260620-221002.json`。

## 最新进展（2026-06-20，ACRO 高速惯性滑行距离重标定）
本轮继续围绕“斜向飞行像平移、缺少真实惯性距离”收敛 playable ACRO。根据本仓库 RATM 高速窗口和 drag guard 资料，旧 playable 前向阻力在 `25m/s` 级松杆滑行时仍偏像游戏刹车；这会让速度矢量太快被吸住，斜飞时缺少真实穿越机那种带重量的滑行。
- `PlayableFlightModel` 将 ACRO 前向等效 CdA 从 `0.0216m^2` 降到 `0.0144m^2`，前向线性阻力从 `0.12/s` 降到 `0.075/s`；横向/竖向阻力保持不变，所以侧滑仍会被空气更快削掉，不是把整台机变成无阻力滑块。
- 新增 coastdown 回归：`25m/s` 直线滑行 1 秒后保留约 `19m/s`，2 秒后仍在 `14..16m/s` 区间；`16m/s right + 16m/s forward` 的斜向滑行会保留总体速度，但侧向分量比前向分量更快洗掉。
- 因前向 CdA 降低会间接削弱 weathercock 的力矩面积，本轮同步补偿 ACRO sideslip yaw/weathercock damping 增益，保持上一轮斜滑机头反馈量级，不让“滑行距离变长”退化成“平面平移更滑”。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`、7 个 Fabric GameTest，以及 JDK 21 下无头 `:fabric-mod:runPlayableAcroServerSelfTest`。服务端自测临时使用 25566，结束后已恢复 25565；报告为 `server-selftest-playable-20260620-215901.json`。

## 最新进展（2026-06-20，ACRO 高速侧滑 yaw 载荷）
本轮继续处理“斜向飞行像平移”的手感问题。当前 ACRO 已有机体系阻力、侧滑 sideforce、诱导阻力、桨盘 flapping/H-force、高前进比推力损失、transverse roll moment 和高迎角 pitch load；这次补的是 yaw 轴的高速侧滑反馈，让机头和速度矢量之间的空气交互更明显。
- `PlayableFlightModel` 提高了 ACRO sideslip weathercock yaw：`16m/s right + 16m/s forward` 的无 yaw 输入斜滑现在约为 `0.30deg/tick` 被动转头，纯横向 broadside 仍保持弱反馈，避免变成自动航向锁定。
- 新增 ACRO sideslip yaw command load：高速侧滑下主动 yaw 会有约 `4%..8%` 的空气载荷损失，满 yaw 仍保持足够快，但不再像完全无空气阻力的贴图旋转。
- 新增回归测试覆盖：直线巡航/低速侧滑不加 yaw 载荷；高速斜滑和 broadside 载荷分档；主动 yaw 在斜滑中比静止略重但不被偷走；passive weathercock 的左右符号保持对称。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`、7 个 Fabric GameTest，以及 JDK 21 下无头 `:fabric-mod:runPlayableAcroServerSelfTest`。服务端自测临时使用 25566，结束后已恢复 25565；报告为 `server-selftest-playable-20260620-215040.json`。

## 最新进展（2026-06-20，ACRO 高迎角 pitch 动态载荷）
本轮继续把“全向机动后不像真实机体、容易有平面漂移感”的手感往真实穿越机收敛。上一轮已经补了高速侧滑下的 transverse-flow roll moment，但 pitch 轴还缺少一个对应的高速迎角载荷：快速俯仰/翻转后，如果机体系前向速度和垂直来流同时存在，真实气流会轻微吃掉 pitch rate 尾巴，而不是像理想数学姿态那样完全无负载滑过去。
- `PlayableFlightModel` 新增 ACRO angle-of-attack pitch moment：低速、直线巡航、悬停、倒退来流都不介入；在 `18m/s` 前向、`10m/s` 垂直来流这类高速大迎角状态下，会产生约 `0.09deg/tick` 量级的弱 pitch 力矩。
- 这不是自动回正：该力矩还加了 pitch-rate activity gate。松杆且 ACRO pitch rate 已经收住时，飞机继续保持当前姿态；只有刚做完动作、角速度还有尾巴，或者玩家仍在主动打 pitch 时，气动载荷才会出现。完整 build 里的巡航持姿与模式切换持姿回归测试已经覆盖这个边界。
- 参考依据沿用本仓库研究资料里的 NeuroBEM 残余力矩线索和 Faessler/Franchi/Scaramuzza rotor-drag 速度相关力矩结构，但本轮只落成可玩层的克制近似项，避免一步加太多物理特性。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build`、7 个 Fabric GameTest，以及 JDK 21 下的无头 `:fabric-mod:runPlayableAcroServerSelfTest`。服务端自测临时使用 25566，结束后已恢复 25565；报告为 `server-selftest-playable-20260620-214228.json`。

## 最新进展（2026-06-20，ACRO 整圈 roll 后侧飞复发修复）
本轮针对“尝试翻转一周之后会持续侧飞、无法回正”的复发点继续收敛。前面已经能在整圈 roll 释放时捕获到水平并清理 body-right 侧滑，但还有两个漏口：一是 `540deg` 附近的完整 roll 释放死区仍可能不被当作动作结束；二是完整 roll 恢复窗口里，残余高速侧滑会触发 transverse-flow 被动 roll moment，把刚捕获到水平的机体又轻微带出 roll rate。
- `PlayableFlightModel` 现在把完整旋转释放捕获窗口扩到精确半圈残差，避免完成一圈后因为惯性/滤波刚好停在旧 `170deg` 边界外而继续保留侧飞姿态。
- 完整 roll recovery window 期间，如果 roll 摇杆仍处于释放尾巴且姿态接近水平，会临时压住残余 roll rate 和被动横流 roll 力矩，让恢复窗口真正结束，不再被空气动力近似项每帧重新推回侧飞。
- 这不是 ACRO 自稳：只有“已经完成至少一整圈 roll 且处于松杆/滤波释放尾段”的恢复窗口触发；主动 roll 输入仍会立即取消恢复窗口，连续翻滚、刀锋、倒飞保持 rate mode 语义。
- 新增回归测试覆盖：服务端实际 `PlayableDebugAxisFilter` 的满 roll 后松杆尾巴；`540deg roll + 残余 roll rate` 的旧死区；以及恢复窗口遇到 `16m/s right + 16m/s forward` 残余侧滑时不会再次生成 roll rate。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build` 和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测临时使用 25566，结束后已恢复 25565；报告为 `server-selftest-playable-20260620-213048.json`。

## 最新进展（2026-06-20，目视机头方向与 pitch 符号再修正）
本轮先修一个会干扰目视飞行判断的视觉问题：之前虽然有“目视 pitch 修复”的测试，但测试辅助常量把模型局部 `-Z` 当作机头；实际模型的机头/相机凸起在局部 `+Z`，所以正 pitch 前飞时，真实可见机头仍可能表现成抬头。
- `DroneEntityModel` 现在把模型前向语义统一到机头凸起所在的 `+Z`，并让 `bodyPitchRotationRadians` 直接使用 playable 正 pitch。这样物理正 pitch / 向前飞在目视视角中重新显示为机头下压。
- `DroneEntityModelTest` 同步改成检查 `+Z` 机头的 Y 位移：正 pitch 必须让机头下压，负 pitch 必须让机头上抬，并保留正负幅度对称断言。
- 这次只改第三人称/目视实体模型，不改 FPV 相机矩阵，也不改 ACRO 物理模型；上一轮侧滑入流滚转力矩和高前进比推力 rolloff 保持不变。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.client.render.DroneEntityModelTest`、`PlayableFlightModelTest + DroneEntityModelTest` 定向组合、完整 `gradlew build` 和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测临时使用 25566，结束后已恢复 25565；报告为 `server-selftest-playable-20260620-211612.json`。

## 最新进展（2026-06-20，ACRO 侧滑入流滚转力矩）
本轮继续收敛“斜向飞行像平移、不像真机”的核心手感。上一轮已经把 5 寸桨高前进比推力 rolloff 提前到 `J≈0.45` 区间；这轮补的是姿态层：旧 playable ACRO 里横向/斜向来流主要只改变线加速度、阻力和推力软化，缺少“气流也会拧动机体”的弱力矩反馈，所以高速斜飞仍容易像在平面里滑。
- `PlayableFlightModel` 现在新增 ACRO transverse-flow roll moment：低于约 `8m/s` 不介入，直线前飞不介入；在 `16m/s right + 16m/s forward` 这类高速侧滑/斜飞里，会产生约 `0.24deg/tick` 的被动 roll rate，让机体轻微向侧滑来流压过去，减少纯平移感。
- 这不是自稳回正：满 roll 杆时该力矩会被压到约 `8%`，不会偷走主动翻滚、刀锋或连续 rate 操作；没有明显侧向来流时也不会把 ACRO 拉回水平。
- 依据来自资料包中 Kolaei 2018 入流角 rotor `CMx` 横向来流滚转力矩线索，以及 NeuroBEM 残差力矩量级警告：真实小型多旋翼高速侧滑并不是只有平动阻力，残余气动力矩也会进入姿态手感。当前实现仍是 playable 近似，先补方向和量级，不硬套大桨系数。
- 新增回归测试覆盖：低速/直线前飞力矩为 0；左右侧滑力矩符号对称；高速斜向侧滑的被动 roll rate 落在 `0.20..0.27deg/tick`；主动 roll 输入时该力矩被明显抑制。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build` 和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮服务端自测临时使用 25566，结束后已恢复 25565；当前系统默认 Java 25 仍会在 dedicated server bootstrap 触发 ASM 异常，服务端自测使用便携 JDK 21；报告为 `server-selftest-playable-20260620-210911.json`。

## 最新进展（2026-06-20，ACRO 5 寸桨前进比推力 rolloff）
本轮继续处理“斜向飞行像平移、不像真机”的手感问题。上一轮把高速惯性距离拉长后，这轮转到推力本身：资料包里 `docs/fpv-sim-model-validation.md` 反复提示 UIUC/IMAV/Mejzlik 等前飞资料显示，5 寸桨在 `J≈0.45` 附近已经出现明显 CT rolloff；旧 playable 模型在这个区域仍接近理想静态推力，容易让斜向/高速穿盘像几何推进器。
- `PlayableFlightModel` 现在把 playable ACRO 的高前进比损失起点从 `J=0.25` 提前到 `J=0.18`，full 区间从 `J=1.00` 提前到 `J=0.82`。前向最大推力损失从 `20%` 提到 `30%`，明显侧向/斜向穿盘最大损失从 `36%` 提到 `42%`。
- 这不是砍掉速度：`acroCruiseCanReachFpvSpeedWithoutInstantVelocitySnap` 仍验证 25m/s 巡航可以达到；调整重点是让 `12.5m/s`、`J≈0.45` 的前飞开始有约 `0.86..0.91` 的有效推力比例，并让 `25m/s` 纯侧向穿盘落到约 `0.55..0.62`，斜向穿盘也明显弱于鼻头前压的前飞。
- 新增/更新回归测试覆盖：低速 `5m/s` 不削推力；`J≈0.45` 前飞必须有可见 rolloff；高速横向、斜向和鼻头前压前飞三者的推力损失顺序保持正确，避免再次退回“任意方向都像理想推力矢量”的平移感。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build` 和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮自测因 25565 被占用临时使用 25566，结束后已恢复 25565；当前系统默认 Java 25 会在 Fabric bootstrap 触发 ASM 异常，已用便携 JDK 21 完成验证；报告为 `server-selftest-playable-20260620-210013.json`。

## 最新进展（2026-06-20，ACRO 高速惯性距离降阻）
本轮继续处理“速度够了但斜向飞行仍像平移、真机惯性不够”的核心手感问题。复核资料包后，`docs/fpv-sim-model-validation.md` 和 `docs/fpv-sim-targeted-calibration-gap-hunt.md` 都指向同一处风险：当前阻力/阻尼若按物理解释，明显高于 IMAV/RPG/RotorPy/NASA/RATM 等参考，会把高速 coastdown 距离压得太短，手感像被游戏刹车粘住。
- `PlayableFlightModel` 现在把 playable ACRO 的 forward linear drag 从 `0.18/s` 降到 `0.12/s`，lateral linear drag 从 `0.24/s` 降到 `0.16/s`。二次 CdA、侧滑 sideforce、induced drag、rotor flapping、in-plane H-force 和软超速阻尼都保留，所以不是取消空气阻力，而是先把“线性游戏刹车”降到更接近真实惯性的一档。
- 25m/s 松杆 coastdown 包线同步变长：1 秒后速度目标从约 `15.8..16.8m/s` 改为 `16.4..17.4m/s`，2 秒后从约 `10.8..12.2m/s` 改为 `12.0..13.8m/s`；距离包线也随之增加。这样高速前飞/斜飞释放后会有更长惯性尾巴，不会像速度矢量被每帧硬刹。
- 斜向 `16m/s right + 16m/s forward` 的气动测试同步收敛到新阻尼：横向阻力仍明显强于纵向，保持“侧滑会被空气咬住”的特征，但不再把侧向速度过快吃掉，给玩家留出更真实的滑移和修正空间。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build` 和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮自测因 25565 被占用临时使用 25566，结束后已恢复 25565；报告为 `server-selftest-playable-20260620-204519.json`。

## 最新进展（2026-06-20，ACRO 近完整 roll 释放尾巴捕获）
本轮继续修你刚反馈的“尝试翻转一周之后持续侧飞、无法回正”。这次定位到一个更贴近实操的漏口：手柄/键盘输入经过滤波后，玩家松开 roll 的第一帧可能还残留约 `0.30` 的命令；如果此时姿态在 `275..300deg` 这种接近完成一圈但还没跨过旧 `300deg` 门槛的位置，旧逻辑不会捕获到水平，油门会继续把机体停在近刀锋姿态上推，表现就是翻完以后持续侧飞。
- `PlayableFlightModel` 现在会识别“上一帧 roll/pitch rate 很高、当前命令明显回落”的滤波释放尾巴。只有满足这个衰减特征时，才把近完整一圈的释放当作动作结束；普通半杆/主动刀锋仍不会被吞掉。
- 完整/近完整 roll 捕获后仍按机体系处理速度：body-right 侧滑会被压到约 `0.32m/s` 以内，前向惯性保留在 `4.6m/s+`，所以这不是给 ACRO 加自稳回正，而是只清掉“动作结束帧卡在侧飞姿态”的离散积分残留。
- 新增回归测试覆盖 `292deg roll + 8deg/tick 残余 roll rate + 0.30 滤波释放尾巴 + 12m/s 横向残留 + 6m/s 前向惯性`：必须捕获到 `0deg roll`、roll rate 清零、target side velocity 清零；同时又覆盖普通 `0.30` 主动 near-knife-edge roll 不会被误捕获。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build` 和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮自测因 25565 被占用临时使用 25566，结束后已恢复 25565；报告为 `server-selftest-playable-20260620-203814.json`。

## 最新进展（2026-06-20，ACRO 斜向桨盘 flapping 加强）
本轮继续处理“斜向飞行像平移、不像真实穿越机”的手感问题。复核当前资料包后，最有价值的线索集中在 5 寸桨的前飞/横向来流：UIUC/IMAV/Kolaei/STARMAC 都指向同一个方向，即 FPV 速度区间里桨盘不会保持理想静态推力，横向穿盘会带来更明显的 flapping、H-force、推力 rolloff 和气动负载。
- `PlayableFlightModel` 现在把 ACRO playable 层的 rotor flapping 从 `0.055` 提到 `0.075`，让 `16m/s right + 16m/s forward` 这种高速斜向横流更明显地产生反向桨盘偏转加速度，机体不再那么像在屏幕平面里被平移。
- 为了保护已经调好的速度感和惯性距离，直线流基线权重从 `0.38` 降到 `0.28`。这样 `25m/s` 直线巡航的 flapping 基线基本不变，主要增强的是明显 sideslip/diagonal disk flow，而不是把正常前飞变成硬刹车。
- 依据来自本仓库资料包：`docs/fpv-sim-model-validation.md` 记录 STARMAC II flapping 尺度检查，`docs/fpv-sim-data-sources.md` 记录 UIUC/IMAV/Kolaei 的 `J/mu` 与前飞桨盘趋势。这里仍是 playable 近似，不直接把大桨或非 5 寸曲线硬套到游戏手感上。
- 回归测试已更新：powered diagonal disk flow 的 flapping 反向加速度从约 `0.4m/s^2` 级提高到约 `0.6m/s^2` 级；直线 `25m/s` 巡航 flapping 仍保持原区间，diagonal/straight 权重比提高到 `>3.0x`，避免斜飞继续像无代价侧移。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build` 和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮自测因 25565 被占用临时使用 25566，结束后已恢复 25565；报告为 `server-selftest-playable-20260620-202645.json`。

## 最新进展（2026-06-20，ACRO 整圈 roll 后恢复窗口）
本轮继续修你反馈的“翻转一周之后持续侧飞、无法回正”。前几轮已经把完整 roll 释放那一帧捕获到水平姿态，并把 body-right 侧滑压到很小；这次定位到实机手感里更明显的问题：捕获只在单帧生效，实体状态没有记住“刚完成一圈 roll”，所以后续数帧如果还有离散积分/输入滤波尾巴，侧滑可能重新显得像被锁住。
- `PlayableFlightModel` 现在在完整 roll 捕获后开启一个短的 ACRO roll recovery window。窗口期间，只有当 roll 摇杆仍处于释放/回中尾段、机体姿态已经接近水平时，才继续把 body-right 残留侧滑压到约 `0.075m/s` 级别；一旦玩家主动打 roll，窗口立即取消，保留连续翻滚、刀锋和 rate mode 权限。
- `DroneEntity` 现在持久保存这个恢复窗口的 tick 状态，避免服务端下一帧忘记刚刚完成过整圈 roll。它不是自稳回正，也不会清掉前向惯性；修的是整圈动作结束后的横向残留被误当作真实持续侧飞。
- 新增回归测试覆盖 `428deg roll + 14m/s 横向残留 + 6m/s 前向惯性 + 残余 roll rate`：捕获后必须打开恢复窗口，继续回中飞行 8 tick 后 body-side velocity 必须低于 `0.10m/s`，前向速度仍保留；另有测试确认主动 roll 输入会取消恢复窗口。
- 同轮顺手保留了目视模型 pitch 方向修复：物理正 pitch 对应机头下压时，第三人称/目视模型不再显示成抬头，方便目视飞行判断姿态。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest --tests com.tenicana.dronecraft.client.render.DroneEntityModelTest`、完整 `gradlew build` 和无头 `:fabric-mod:runPlayableAcroServerSelfTest`。本轮自测因 25565 被占用临时使用 25566，结束后已恢复 25565；报告为 `server-selftest-playable-20260620-202008.json`。

## 最新进展（2026-06-20，ACRO 整圈 roll 后侧飞残留收紧）
本轮针对你反馈的“尝试翻转一周之后会持续侧飞、无法回正”继续收敛 playable ACRO。复核发现，前面已经能把完整 roll 释放捕获到水平姿态，但捕获后仍允许最多约 `1.10m/s` 的 body-right 残留侧滑；在游戏视角里这会表现成机体看似回正了，却还在横着漂，尤其松杆后没有自稳刹车时很像“锁死侧飞”。
- `PlayableFlightModel` 现在把完整 roll 捕获后的 body-right 侧滑余量从 `1.10m/s` 收紧到 `0.28m/s`。这只在“已经完成至少一整圈 roll 且处于松杆/释放尾段”的捕获帧触发，不影响主动刀锋、倒飞、连续翻滚或普通 ACRO rate 模式。
- 前向惯性仍保留，所以不是给穿越机加自动回正；修的是动作结束帧把离散积分残留当作真实侧飞继续带走的问题。也就是说，翻完一圈松杆后应该回到可控的前向滑行，而不是长期横移。
- 回归测试同步收紧：`428deg/507deg roll + 残余 roll rate + 12..14m/s 横向残留 + 6m/s 前向惯性` 的释放场景，捕获后 roll rate 必须归零、roll 必须归零、body-side velocity 必须低于约 `0.32m/s`，前向速度仍保持在 `4.6m/s` 以上。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`；25565 被占用时临时使用 25566，结束后已恢复 25565，报告为 `server-selftest-playable-20260620-200744.json`。

## 最新进展（2026-06-20，ACRO 侧滑诱导阻力）
本轮继续处理“斜向飞行像平移、不像真实穿越机”的手感问题。上一轮补了高速斜向来流下的角惯性负载；这轮进一步检查线速度气动项，发现 playable ACRO 已有的 sideslip sideforce 会把斜向速度矢量往机头方向弯，但这个侧向力本身接近零做功，容易留下“空气免费帮我拐弯”的理想化味道。
- `PlayableFlightModel` 现在在侧滑侧向力之外叠加一层很小的 sideslip induced drag：直线前飞不会触发；明显斜向/横向来流时，侧向力越强，越会沿当前水平来流反向吃掉一点能量。
- 这层不改直线巡航速度、不改油门曲线，也不是自动回正；它只让“空气把横向速度掰向机头方向”的过程付出合理阻力，让斜飞更像有重量和气动代价的飞行，而不是纯几何平移。
- 新增回归测试覆盖：直线 `25m/s` 前飞的 induced drag 必须为 0；`16m/s right + 16m/s forward` 斜向侧滑时，原侧向力对速度做功仍为 0，但新增诱导阻力必须对速度做负功，量级保持在约 `0.10..0.24m/s^2`，避免把它变成新的硬刹车。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`；25565 被占用时临时使用 25566，结束后已恢复 25565，报告为 `server-selftest-playable-20260620-200058.json`。

## 最新进展（2026-06-20，ACRO 高速斜向角惯性负载）
本轮继续针对“斜向飞行像平移、不像真实穿越机”的核心手感收敛。复核后发现，playable ACRO 的线速度层已经有推力轴积分、机体系 CdA、分离流、侧滑侧向力、桨盘 flapping/H-force、高前进比推力软化和弱 weathercock yaw；但姿态层的 roll/pitch rate 响应仍偏理想化，主要靠固定平滑和空气阻尼，没有把真实 5 寸机的转动惯量、机体系横流负载转化成“角速度建立要吃一点力”的感觉。
- `PlayableFlightModel` 新增 ACRO rate inertia smoothing：低速基本不介入；直线 25m/s 高速巡航只轻微降低 rate 建立；`16m/s right + 16m/s forward` 这种斜向横流会让 roll rate 建立明显更有重量，pitch 轴在大迎角下也会有类似负载。
- 这不是自动回正，也不改最大速度/油门曲线/推力模型；它只影响 ACRO pitch/roll rate 从上一帧向目标 rate 逼近的速度。也就是说，玩家仍然可以全向翻滚、倒飞、刀锋飞行，但高速斜飞时机体不再像无质量贴图一样立刻换姿态。
- 参考锚点来自当前资料包：`docs/fpv-sim-model-validation.md` 里 5 寸 `racingQuad` 的转动惯量半径量级已经接近 RotorS Hummingbird/PX4 Iris；同时 Betaflight rate 资料说明当前预设是偏快速 race/freestyle 的 rate 档。playable 层这次没有重写完整刚体角动力学，而是先把“真实惯量 + 高速气动载荷”压缩成一个可控的手感近似。
- 新增回归测试覆盖：低速 roll inertia scale 必须为 `1.0`；直线高速只小幅降低；斜向横流 roll scale 必须低于直线巡航；完整 `step()` 下同样满 roll 输入在高速斜向来流中建立得比低速更重，但仍保留 70% 以上响应，避免变成迟钝或失控。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`；25565 被占用时临时使用 25566，结束后已恢复 25565，报告为 `server-selftest-playable-20260620-195331.json`。

## 最新进展（2026-06-20，ACRO 整圈翻滚反向释放尾巴修复）
本轮继续针对你反馈的“尝试翻转一周后持续侧飞、无法回正”收敛 playable ACRO。新的复盘点在于手柄和输入滤波的释放尾段：完成一整圈 roll 以后，摇杆回弹不一定总是同方向慢慢归零，实际经常会短暂出现一个反向小命令。旧逻辑把这个反向小尾巴当成玩家仍在主动保持姿态，导致整圈后的 60..150 度残余横滚没有被捕获，油门继续给出侧向推力，于是看起来像“翻完一圈后锁死侧飞”。
- `PlayableFlightModel` 现在把“完成至少一整圈后的低幅摇杆命令”统一视为释放尾段：只要幅度低于释放阈值，就允许捕获到水平姿态并清掉残余 roll rate；明显主动的半杆/满杆仍然不会被捕获，刀锋、倒飞和持续翻滚的 ACRO rate 语义保持不变。
- 完整 roll 捕获后仍会按机体系处理速度：body-right 方向的侧滑被压到可控范围，前向速度继续保留，所以它不是自动回正或粗暴清速，而是只清理动作结束帧的离散积分残留。
- 新增回归测试覆盖 `428deg roll + 反向 -0.10 roll 尾巴 + 12m/s 横向速度 + 6m/s 前向速度`：释放后一帧必须捕获到 `0deg roll`、残余 roll rate 必须归零、横向 body slip 必须低于约 `1.12m/s`，同时前向速度保持在 `4.6m/s` 以上。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`；25565 被占用时临时使用 25566，结束后已恢复 25565，报告为 `server-selftest-playable-20260620-194449.json`。

## 最新进展（2026-06-20，ACRO 横向来流弱偏航稳定）
本轮继续朝“斜向飞行不要像平移贴图，而要像真实穿越机在空气里被横向来流咬住”的目标收敛。上一轮已经补了完整翻滚释放尾段；这轮定位到另一个手感缺口：原来的 playable ACRO 只有在 body-forward 为正时才产生 weathercock yaw，纯横向或近横向高速滑行时机头/速度矢量关系会冻结，玩家看到的就是“机体横着平移”。
- `PlayableFlightModel` 现在保留已有前向 sideslip weathercock 逻辑，同时给 broadside/纯横向来流增加一条更弱的被动偏航路径。它只在 ACRO、横向速度足够大、主动 yaw 输入很小时出现；一旦玩家打 yaw，仍由主动 yaw 主导，不会偷走穿越机 rate mode 权限。
- 量级被刻意压小：`18m/s` 纯横向来流只产生约 `0.04..0.07 deg/tick` 的弱偏航，明显弱于 `16m/s right + 16m/s forward` 的前向侧滑偏航；目标是打破“平移锁死感”，不是把无人机做成自航向稳定的航模。
- 参考方向来自高速四旋翼的机体系气动建模：Faessler/Franchi/Scaramuzza 的 rotor-drag 模型把高速阻力写成 `R D R^T v` 的机体系速度项，Abeywardena 等人的速度估计论文也强调 blade flapping 让机体系横向/纵向速度与加速度直接相关。这里的 broadside yaw 是 playable 层对这类机体系横向来流效果的克制近似。
- 新增/更新回归测试覆盖：纯横向来流会产生弱 weathercock yaw；前向侧滑仍明显更强；纯横向侧滑会产生小 yaw damping；主动 yaw 输入仍保持 `>4.5 deg/tick`，不会被被动气动项压住。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`；25565 被占用时临时使用 25566，结束后已恢复 25565，报告为 `server-selftest-playable-20260620-193655.json`。

## 最新进展（2026-06-20，ACRO 完整翻滚释放尾段侧飞修复）
本轮针对“翻转一周之后持续侧飞、无法回正”的新反馈继续收敛 playable ACRO。根因进一步缩小到完整 roll 释放尾段：玩家完成一圈后松杆，如果滤波和残余角速度把累计 roll 推到旧捕捉窗口外一点点，物理层会停在约 150 度左右的等效横滚姿态；此时油门仍会产生强侧向推力，看起来就像翻完一圈后飞机锁死在侧飞。
- `PlayableFlightModel` 将“已经完成至少一整圈并释放摇杆”的捕捉窗口从 `145deg` 放宽到 `170deg`。这只作用于完成整圈后的释放尾段，不改变普通 ACRO 半滚、刀锋飞行、倒飞和主动持续翻滚的 rate mode 语义。
- 完整 roll 捕捉后的 body-right 侧滑残差上限从 `2.75m/s` 收紧到 `1.10m/s`。也就是说，完成一圈时不会把翻滚积分误差留下来继续横着拖飞，但前向惯性仍会保留，不会把速度粗暴清零。
- 新增回归测试覆盖 `507deg roll + 残余 roll rate + 12m/s 横向速度 + 6m/s 前向速度` 的释放尾段：12 tick 后 roll 必须归零、残余 roll rate 必须归零、横向 body slip 必须低于 `1.05m/s`，前向速度仍保持在 `4.6m/s` 以上。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest`、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`；25565 被占用时临时使用 25566，结束后已恢复 25565，报告为 `server-selftest-playable-20260620-192935.json`。

## 最新进展（2026-06-20，ACRO 高速角速度空气阻尼）
本轮继续处理“斜向/高速机动还是有点像数学旋转、平移感偏重”的手感问题。当前 playable ACRO 已经有推力轴积分、机体系 CdA、分离流、侧滑侧向力、桨盘 H-force、高前进比推力软化和 body-rate yaw 耦合；这次补的是高速动态压下的 pitch/roll rate 空气动力阻尼，让高速前飞、斜飞、横飞时机体旋转也能感到一点真实空气负载。

- `PlayableFlightModel` 现在在 ACRO pitch/roll rate 响应后加入一层机体系相对风阻尼：低速基本为 `0`，到 FPV 速度区间后按总速度、AOA/sideslip 暴露逐步增强。pitch 轴更看重迎角，roll 轴更看重侧滑/横向穿流。
- 这不是自动回正，也不是自稳；它只削一点当前角速度，最大阻尼保持在克制范围内，仍保留穿越机全向 rate mode 和主动翻滚权限。目的是让高速斜飞/横飞时机体不是“无代价几何旋转”，而是像在气流里滚/俯仰。
- 参考依据继续沿用高速四轴 body-frame 气动建模方向：Faessler/Franchi/Scaramuzza rotor-drag 说明高速轨迹需要显式气动项，Bangura/Mahony 的 blade-element 资料把水平力与功率放在机体系，NeuroBEM/竞速数据也说明高速/激烈动作中经典一阶模型的残余力矩不可忽略；Do a Barrel Roll 仍作为 Minecraft 全向姿态交互参考，而不是物理来源。
- 新增回归测试覆盖：低速不产生角速度空气阻尼；`25m/s` 直线巡航给 pitch/roll 很小阻尼；`16m/s right + 16m/s forward` 斜向侧滑的 roll 阻尼强于直线巡航；有迎角的高速流会给 pitch rate 阻尼；单次阻尼不会吃掉超过约 10% 的主动 rate。
- 已通过 `PlayableFlightModelTest` 定向测试、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`；25565 被占用时临时使用 25566，结束后已恢复 25565，报告为 `server-selftest-playable-20260620-192022.json`。

## 最新进展（2026-06-20，目视 pitch 渲染方向修复）
本轮补一个会明显影响目视飞行判断的视觉一致性问题：playable 物理里正 pitch 表示机头下压，但第三人称/目视模型曾经把这个 pitch 反号套到机体上，导致“向前飞/压头”在画面里看起来像抬头，和 FPV 相机、飞控内部姿态不一致。

- `DroneEntityModel` 现在按当前相机/机体系约定把模型本地 `-Z` 当作机头前向，并让物理 pitch 直接映射到机体 pitch；渲染器原本的 Y 轴翻转不再额外制造一次方向反转。
- 新增几何语义回归测试：正 pitch 必须让目视机头向下，负 pitch 必须让目视机头向上，并检查两个方向的位移幅度对称。这样以后不会只靠“正负号看起来对”而漏掉模型坐标系前后向的问题。
- 这次只改目视实体模型，不改 FPV 相机矩阵，也不改 playable ACRO 物理；前面已推送的整圈 roll 侧滑修复、body-rate yaw 耦合和软超速阻尼仍保持原语义。
- 已通过 `DroneEntityModelTest` 定向测试、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`；25565 被占用时临时使用 25566，结束后已恢复 25565，报告为 `server-selftest-playable-20260620-191229.json`。

## 最新进展（2026-06-20，ACRO 超速软阻尼替代硬限速）
本轮继续处理“斜飞/高速时像平移、像被游戏速度墙拖住”的手感问题。审计发现 playable ACRO 虽然主体速度已经用推力、机体系阻力、桨盘 H-force 和侧滑力积分，但每帧末尾仍会把水平速度硬裁切到 `32m/s`，这会让高速斜向飞行在速度包线附近出现很明显的平面限速器质感。

- `PlayableFlightModel` 现在把 ACRO 的 `32m/s` 水平速度硬裁切改成软超速阻尼：超过速度包线时按超速量施加反向加速度，让阻力自然把速度拉回，而不是一帧缩放速度矢量。只保留约 `1.45x` 包线的安全兜底，防止数值失控。
- 这层只作用于 ACRO 超速段；普通 25m/s 巡航、25m/s 松杆 coastdown、侧滑气动和桨盘推进逻辑仍由原来的物理项决定。目标是减少“撞上无形速度墙”的游戏感，同时保留可玩的安全边界。
- 新增回归测试覆盖 `35m/s` 水平超速释放：下一帧速度必须被空气阻力和软阻尼降低，但仍明显高于 `32m/s`，证明没有回到硬裁切。
- 已通过 `PlayableFlightModelTest` 定向测试、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`；本轮自测临时使用 25566，结束后已恢复 25565，报告为 `server-selftest-playable-20260620-190447.json`。

## 最新进展（2026-06-20，ACRO body-rate 航向耦合）
本轮继续收敛“斜向飞行像平移，而不是像真机在三维空间里飞”的手感问题。当前 playable ACRO 已经有机体系阻力、侧滑、桨盘 H-force 和高前进比推力软化，但 pitch/roll 的姿态控制仍偏游戏化：复合动作基本是两个独立角度累加，banked pitch 或机头接近垂直时的 roll 不会给航向带来足够的 body-rate 耦合。

- `PlayableFlightModel` 现在给 ACRO 增加一层克制的 body-rate yaw coupling：大 roll 下继续打 pitch 会给机头航向一点随动，机头接近垂直时打 roll 也会产生受控的 heading 变化。这更接近真实 3D 姿态运动，也贴近 Do a Barrel Roll 那种“完全解锁 pitch/yaw/roll + banking”的参考方向。
- 这不是自动回正，也不是自稳：只有当前帧存在 pitch/roll 角速度时触发，松杆定姿和纯惯性滑行不会自己转头；耦合量封顶在 `1.35 deg/tick`，主动 yaw 和已有 weathercock yaw 仍走原来的通道。
- 新增回归测试覆盖：水平 pitch 不产生偷偷偏航；60 度 bank 下 pitch 输入会产生约 `0.8 deg/tick` 的航向耦合；88 度 pitch 下 roll 输入会产生约 `0.7 deg/tick` 的垂直滚转航向耦合；完整 `step()` 场景确认无 yaw stick 时 banked pitch 也能给出受控 heading change。
- 已通过 `PlayableFlightModelTest` 定向测试、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`；本轮自测临时使用 25566，结束后已恢复 25565，报告为 `server-selftest-playable-20260620-185856.json`。

## 最新进展（2026-06-20，ACRO 整圈 roll 侧滑锁死修复）
本轮针对你新反馈的“尝试翻转一周之后会持续侧飞、无法回正”继续收敛 playable ACRO。问题不是普通的摇杆映射，而是整圈 roll 被捕获到水平姿态时，旧逻辑只按局部 X/Z 小速度做清零；如果翻滚过程中已经积累了较大的横向速度，它会被完整保留下来，玩家看到的就是机身回正了但还在侧向漂移。

- `PlayableFlightModel` 现在在 ACRO 整圈捕获后先把速度投到当前机体系，再只处理对应动作轴：完成 roll 时吃掉释放尾巴里的 body-right 侧滑，并把剩余横向侧滑限制到可控范围；完成 pitch loop 时仍沿用原来的低速漂移清理，不会把正常前向惯性粗暴清零。
- 这个修复不等于给穿越机加自动回正。pitch/roll 姿态仍然是 ACRO rate 模式，前向惯性仍保留；它只处理“数学上已经完成一整圈且摇杆释放”的那一帧，避免离散积分在动作结束时留下一个持续横飞的错误状态。
- 新增回归测试覆盖 `428° roll + 14m/s 横向残留 + 6m/s 前向惯性`：释放后 roll 必须归零、残余 roll rate 必须归零、机体系侧滑被压到约 `2.75m/s` 内，同时前向速度仍大于 `4.6m/s`。
- 已通过 `PlayableFlightModelTest` 定向测试、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`；本轮自测临时使用 25566，结束后已恢复 25565，报告为 `server-selftest-playable-20260620-185127.json`。

## 最新进展（2026-06-20，ACRO 方向性高前进比推力软化）
本轮继续处理“斜向飞行像平移、不像真机”的手感问题。当前 playable ACRO 已经有机体系 CdA、分离流、侧滑侧向力、weathercock yaw、桨盘 flapping 和 H-force，但高前进比推力软化仍偏保守：`J` 很高时最多只掉约 20% 推力，导致高速横向/斜向穿过桨盘时仍有点像理想推进器。

- `PlayableFlightModel` 现在把高前进比推力损失按机体系穿盘方向分开：鼻头前压的前飞仍使用较温和的 20% 上限，保护已经可玩的 25m/s+ 前飞速度；纯侧向/明显斜侧向穿盘会提高到约 36% 上限，让斜飞/横飞更有掉高、掉速和提前量。
- 方向判定使用 body-frame `right/forward` 的 sideflow exposure：直线前流为 `0`，`16m/s right + 16m/s forward` 的斜向来流接近满 exposure，纯横向穿盘为满 exposure。它不会自动扶平姿态，也不吞玩家主动 knife-edge，只改变桨在不利来流里的有效推力。
- 依据继续来自仓库资料包里的 UIUC 5 寸桨前进比表、IMAV/RMIT 5 寸桨风洞线索和 `docs/fpv-sim-model-validation.md` 的 J/mu 警告：5 寸桨在 `J≈0.45` 时 CT/static 已可能只有约 `0.55`，所以 playable 原来的 20% 损失对侧向高速穿盘偏理想化。
- 新增回归测试覆盖：低速不削推力；`25m/s` 纯横向穿盘缩到约 `0.62..0.70`；`16/16m/s` 斜向穿盘弱于鼻头前压前飞；直线前流 sideflow exposure 为 `0`，斜向/横向接近满值。
- 已通过 `PlayableFlightModelTest`、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`。本轮服务端自测临时使用 25566，结束后已恢复 25565；报告文件为 `server-selftest-playable-20260620-184315.json`。

## 最新进展（2026-06-20，ACRO 满圈姿态归一化）
本轮针对你最新反馈的“尝试翻转一周之后持续侧飞、无法回正”继续修。根因进一步收敛到 ACRO 整圈捕获的状态表示：旧逻辑会把完成动作后的姿态留在 `360°/720°` 这类数学等价角度上，目标速度虽然归零，但后续物理状态、释放尾巴、气动投影和调试遥测仍带着“大角度动作未真正结束”的历史，实机手感就会像翻完一圈后还在侧向拖飞。

- `PlayableFlightModel` 现在在 ACRO roll/pitch 完成整圈并释放时，把物理姿态直接归一化到 `0°`，而不是保留 `360°`。这不是给 ACRO 加自动回正；只有接近整圈且摇杆已经回中/进入释放尾巴时触发，半滚、倒飞、主动 knife-edge、持续全向翻滚仍按 rate mode 保留。
- 整圈捕获仍同步清掉对应轴的残余 ACRO 角速度，并修剪小的特技残留漂移；新增实测化回归覆盖 `428° roll + 12m/s 横向速度 + 残余 roll rate` 松杆后必须归零，并在 1 秒内由气动阻尼明显吃掉侧滑。
- 上轮未收尾的机身 pitch-plane 迎角升力也补齐测试：直线巡航为零，正/负迎角时产生与速度近似正交的上/下洗与后向分量，让高速俯仰段更像有机身气动，而不是纯推力点质量。
- 为了保持穿越机速度感，直线桨盘 H-force 权重从 `0.12` 微调到 `0.10`，侧滑/斜飞权重仍保留；25m/s coastdown 锚点下限同步放宽到 `10.8m/s`，避免因为新气动项把直线滑行调成过度刹车。
- 已通过 `PlayableFlightModelTest`、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`。本轮服务端自测临时使用 25566，结束后已恢复 25565；报告文件为 `server-selftest-playable-20260620-183659.json`。

## 最新进展（2026-06-20，ACRO 旋翼盘面 H-force）
本轮继续针对“斜向飞行像平移、不像真机”的手感问题做低阶物理补齐。上一轮已经加入横向来流下的桨盘 flapping 偏转；这次补上完整仿真核心里 `rotor_in_plane_drag_force_n` 对应的盘面 H-force：有推力的旋翼盘遇到横向穿盘气流时，会产生反向的 in-plane drag，而不只是靠机体 CdA 在空气里被动刹车。

- `PlayableFlightModel` 的 ACRO 速度积分现在会同时叠加 flapping 偏转和 in-plane H-force，并把合成后的 body-frame 盘面加速度按当前 pitch/roll 姿态投回 yaw-local 速度坐标；所以全向翻滚、斜飞、侧飞时仍跟随机体姿态，不会退回屏幕平移。
- 量级沿用完整 `racingQuad` 的 5 寸桨盘参数：四个桨盘、半径 `0.0635m`、盘面阻力系数 `0.0028`、参考最大转速约 `29137 RPM`，使用 `mu = V/(omega R)`、转速 active disk、推力耦合项和动态压 profile 项共同计算。
- 为了保护已经校好的 25m/s 惯性滑行距离，直线巡航只保留很弱的 H-force 基线；明显 sideslip/diagonal flow 才接近完整盘面阻力权重。这一层是为斜飞提供提前量和“空气咬住”的重量感，不是给 ACRO 加自动回正。
- 新增回归测试覆盖：零推力不产生 H-force；`16m/s right + 16m/s forward` 的 powered disk flow 会得到约 `1.0m/s^2` 级反向盘面加速度；直线 `25m/s` 巡航 H-force 明显弱于斜向侧滑，并且原有 25m/s coastdown 锚点继续通过。
- 已通过 `PlayableFlightModelTest`；完整构建和服务端自测见下一轮验证记录。

## 最新进展（2026-06-20，ACRO 横向来流桨盘偏转）
本轮继续收敛“斜向飞行像平移、不像真机”的核心手感问题。前几轮已经把 playable ACRO 改成推力轴积分、机体系 CdA 空阻、高前进比推力软化、分离流侧滑力和弱 weathercock yaw；这次补的是桨盘本身在横向来流下的 in-plane/flapping 力，让高速斜飞时速度矢量不再只像一个平面目标速度被拖着走。

- `PlayableFlightModel` 现在在 ACRO 速度积分里加入简化的横向来流桨盘偏转项：先把 yaw-local 速度投到机体系，按 5 寸桨 `mu = V / (omega R)` 计算横向来流响应，再把一个小的 body-frame in-plane 加速度投回当前 pitch/roll 姿态。
- 量级锚定到完整物理核心的 `racingQuad` 默认值：`rotor_flapping = 0.055`，满响应参考 `mu ~= 0.095`，最大偏转仍守在 `18 deg` 内。直线高速巡航已有 CdA 阻力，所以 playable 层只给直线流保留较弱权重；侧滑/斜飞会明显加强，避免双算直线阻力又能处理你反馈的斜向平移感。
- 力的方向不是自动回正，也不是把 ACRO 改成自稳：它只在有推力和桨盘平面来流时出现，方向主要反向咬住 body-frame 横向速度，并扣掉极小的垂直有效升力。玩家主动 pitch/roll/yaw 的 rate mode 语义不变。
- 参考依据：Faessler/Franchi/Scaramuzza 的 rotor-drag 高速四轴模型说明高速飞行不能忽略机体系速度相关气动项；Bangura/Mahony 的 quadrotor rotor-blade 气动报告明确把 body-frame 水平力纳入旋翼建模；本仓库完整仿真核心已有 `rotor_flapping_tilt`、`rotor_in_plane_drag_force_n` 和 STARMAC II Fig. 9 flapping 尺度检查，本轮是把这层思路以可玩低阶形式落到 playable ACRO。
- 新增回归测试覆盖：桨盘 flapping 使用 `mu = V/(omega R)` 而不是 UIUC 的 `J = V/(nD)`；`16m/s right + 16m/s forward` 的 powered diagonal disk flow 会得到约 `0.4m/s^2` 级反向桨盘 in-plane 加速度；直线 `25m/s` 巡航只保留较弱桨盘权重，避免过度刹车。
- 已通过 `PlayableFlightModelTest`、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`。本轮服务端自测临时使用 25566，结束后已恢复 25565；报告文件为 `server-selftest-playable-20260620-181728.json`。

## 最新进展（2026-06-20，ACRO 侧滑 yaw 气动耦合）
本轮继续沿着“斜向飞行还是有平移感”的核心反馈往下收敛。上一轮已经给 ACRO 平动加入分离流阻力和侧滑侧向力，但 yaw 角运动仍完全由玩家输入/滤波决定：高速前向侧滑时，机体没有任何空气动力 yaw 阻尼或顺风标趋势，视觉上仍可能像一个侧着滑动的点。

- `PlayableFlightModel` 现在在 ACRO yaw rate 末端加入很小的被动气动项：当机体系同时有前向速度和侧向速度时，会计算 sideslip exposure、前向/侧向速度 exposure 和 CdA 派生面积，得到弱 `weathercock` yaw bias 与 yaw damping。
- 这个效果只在前向侧滑时出现：直线 25m/s 巡航为 `0`，纯横移也为 `0`；约 `16m/s right + 16m/s forward` 的斜滑会产生约 `0.2 deg/tick` 以内的被动 yaw，并提供约 `0.1` 级 yaw 阻尼。
- 主动 yaw 输入会压制这层被动气动，避免玩家想做 yaw/knife-edge/连续动作时被系统抢杆；它也不会改变 pitch/roll 自稳语义，只是让高速斜滑时机头和速度之间有一点真实空气动力耦合。
- 新增回归测试覆盖 weathercock yaw 的符号、量级、直线/纯横移不触发、侧滑 yaw damping 的范围，以及 `step()` 中松杆高速斜滑会出现小被动 yaw、满 yaw 输入仍保持主控权限。
- 已通过 `PlayableFlightModelTest`、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`。本轮服务端自测临时使用 25566，结束后已恢复 25565；报告文件为 `server-selftest-playable-20260620-180809.json`。

## 最新进展（2026-06-20，ACRO 分离流与侧滑侧向力）
本轮继续处理“速度够了，但斜向飞行仍像平移”的核心手感问题。这次没有再调最高速度，也没有把 ACRO 改成自稳，而是把完整 6DoF 仿真里已经存在的空气动力思路移到 playable 层：高速斜飞/横飞时，机体不应只受到 right/up/forward 三轴基础阻力，还应在大攻角/大侧滑时出现分离流阻力，并在中等侧滑角产生把速度矢量往机头方向弯的侧向力。

- `PlayableFlightModel` 的 ACRO 机体系气动力现在由三部分组成：基础线性/二次 CdA 阻力、分离流额外阻力、侧滑侧向力。直线前飞的 25m/s coastdown 锚点不变，低速起飞也不受影响。
- 分离流强度按机体系 angle-of-attack 和 sideslip 计算：直线巡航为 `0`，约 45 度斜向侧滑会进入中等分离，纯横向 broadside 来流接近 `1.0`。这会让高速斜飞更有空气阻力和提前量，而不是像二维速度目标在平面里拉着走。
- 侧滑侧向力参考完整仿真中的 yaw-plane sideforce 形式：在 `bodyRight/bodyForward` 同时存在时，生成与速度近似正交的力，减少横滑分量、增加沿机头方向的弯曲趋势；它不会自动扶平 pitch/roll，也不会吞掉主动 knife-edge 或连续翻滚。
- 新增回归测试覆盖：直线巡航不触发分离流、45 度侧滑有中等分离、纯横向来流接近满分离、侧滑侧向力方向与速度正交且把 diagonal velocity 往 body-forward 弯，以及高速斜向来流会得到额外分离流阻力。
- 已通过 `PlayableFlightModelTest`、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`。本轮服务端自测临时使用 25566，结束后已恢复 25565；报告文件为 `server-selftest-playable-20260620-175932.json`。

## 最新进展（2026-06-20，ACRO 高前进比桨推力软化）
本轮继续沿着“斜向飞行不像真机、像平移”的核心手感问题收敛。当前可玩层已经有 1.10kg 质量锚点、机体系 CdA 空阻、推力轴积分和 ACRO 角速度响应，但螺旋桨推力仍接近静态推力台：只要油门给到，推力几乎不关心高速前飞/侧飞/斜飞时穿过桨盘的来流。真实 5 寸穿越机桨在前进比 `J = U/(nD)` 变高时会明显变软，这会直接影响高速斜向机动的“重量感”和提前量。

- `PlayableFlightModel` 现在给 ACRO 增加高前进比推力软化：把 yaw-local 速度投到机体系 right/up/forward，计算桨盘平面来流和一部分轴向来流，再用 5 寸桨直径和 `13k RPM` 级 racing-quad 参考转速换算 `J`。
- 低速悬停/起飞基本不受影响；高速横向掠过桨盘时推力会温和下降，`25m/s` 侧向来流约落到 `0.8x` 推力，鼻头前压的高速前飞保留更多推力，因此不会把刚修好的 `25m/s+` 速度能力打掉。
- 依据参考：[UIUC propeller database](https://m-selig.ae.illinois.edu/props/propDB.html) 使用的前进比是 `J = U/(nD)`，本轮测试把 `12.5m/s` 5 寸桨参考点固定在 `J≈0.45`，防止和代码侧 `mu = U/(omega R)` 混淆；[IMAV 2021 的 5 寸桨风洞矩阵](https://www.imavs.org/papers/2021/21.pdf) 仍作为后续更精细曲线的资料锚点。
- 新增回归测试覆盖 `J` 尺度、低速不削弱、高速侧向来流削弱，以及鼻头前压高速前飞比纯侧向来流保留更多推力，避免模型退回“理想平移推进器”。
- 已通过 `PlayableFlightModelTest`、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`。本轮服务端自测因 25565 被占用临时改用 25566，结束后已恢复；报告文件为 `server-selftest-playable-20260620-174844.json`。

## 最新进展（2026-06-20，ACRO 整圈释放尾巴修复）
本轮继续修你实测的“尝试翻转一周之后持续侧飞、无法回正”。这次重点不再只看理想的松杆值 `0`，而是按真实链路处理：客户端/服务端都有输入滤波，玩家物理摇杆已经回中时，服务端 roll/pitch 可能还会残留约 `0.10` 的释放尾巴，旧逻辑会把这段尾巴当成主动继续打杆，于是一圈后仍可能保留侧向推力。

- `PlayableFlightModel` 扩大了 ACRO 整圈释放捕获：在完成一圈附近、输入已经明显回落到释放尾巴时，也会吸附到最近的 360 度整圈，并同步清掉该轴的 ACRO 角速度。
- 整圈捕获那一帧会清掉对应轴上小的数值侧滑残留：roll 完成时修整局部 X 小漂移，pitch loop 完成时修整局部 Z 小漂移，避免“姿态已经回水平但还被一次特技残余速度拖着侧飞”。高速惯性滑行和主动 knife-edge 侧飞不受这个清理影响。
- 新增回归测试覆盖 `480° roll/pitch + 0.10` 释放尾巴场景，确认不会继续生成侧向/前向目标，也不会把捕获后的残余角速度带进下一帧。
- 已通过 `PlayableFlightModelTest` 定向测试、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`。本轮服务端自测因 25565 被占用临时改用 25566，结束后已恢复；报告文件为 `server-selftest-playable-20260620-174157.json`。

## 最新进展（2026-06-20，ACRO 质量/CdA 惯性距离锚点）
本轮继续把“真实穿越机重量和空阻设计惯性距离”落到可测约束上。上一版已经有机体系阻力，但代码里仍是裸的二次阻力系数；现在把 ACRO 可玩层的高速阻力明确锚定到 1.10kg 级 5 寸机、标准空气密度和机体等效迎风面积。

- `PlayableFlightModel` 的 ACRO 二次阻力现在由 `0.5 * rho * CdA / mass` 派生：质量锚点为 `1.10kg`，空气密度 `1.225kg/m^3`，前向/侧向/竖向等效 CdA 分别约 `0.0216/0.0269/0.0180 m^2`。这样后续调参能按真实量纲移动，而不是直接改神秘阻力系数。
- 新增 25m/s 高速松杆 coastdown 回归：水平 25m/s 松杆后 1 秒仍约 `16m/s`、滑行约 `20m`；2 秒后仍约 `11-12m/s`、总滑行约 `33m`。这个区间防止模型变成“松杆刹停”的游戏手感，也防止变成几乎无阻力的漂移。
- 旧的 18m/s 松杆测试仍保留，新的高速测试专门覆盖穿越机速度区间下的惯性距离；这比单看最高速度更能约束真实飞行手感。
- 已通过 `PlayableFlightModelTest`、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`。本轮服务端自测因 25565 被占用临时改用 25566，结束后已恢复；报告最大速度约 `5.42m/s`、水平位移约 `14.90m`、yaw 漂移 `0.00°`。

## 最新进展（2026-06-20，ACRO pitch/roll 角速度响应）
本轮继续收敛“速度够了但不像真机、斜向飞行仍有数学平移感”的核心手感问题。上一版已经把平动改成推力、重力、机体系空阻和电机/桨推力响应，但 ACRO 的 pitch/roll 姿态仍是理想化的 `上一帧姿态 + 摇杆 * 最大角速度`：摇杆一打，角速度瞬间到位；摇杆一松，pitch/roll 角速度瞬间消失。这比真实穿越机飞控/电机/桨响应更硬，也会让姿态变化像几何变换。

- `PlayableFlightModel` 现在给 ACRO pitch/roll 增加独立角速度状态：输入目标角速度会快速建立，但首帧不再瞬间达到满 rate；松杆后角速度会在几个 tick 内刹住，保留一点真实动力系统和飞控响应的“重量”。
- 整圈翻滚捕获和新角速度状态已联动：当 roll/pitch 在松杆后吸附到最近的 360° 倍数时，对应轴的角速度会同步清零，避免“刚吸到一整圈，下一帧又被残余角速度推走”。
- `DroneEntity` 的 playable 直控路径现在持久保存 ACRO pitch/roll rate，因此服务端 tick、碰撞修正、yaw 重投影之后不会丢失角速度响应状态。
- 回归测试新增“满杆第一帧不会瞬时满角速度、数 tick 后仍能快速接近目标 rate”，并把松杆后 ACRO 姿态保持改成允许几度以内的刹车续转；斜向速度包络测试也改为在角速度建立后验证，不再把合理响应滞后误判成速度能力下降。
- 已通过 `PlayableFlightModelTest`、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`。本轮服务端自测因 25565 被占用临时改用 25566，结束后已恢复；报告最大速度约 `5.43m/s`、水平位移约 `14.90m`、yaw 漂移 `0.00°`。

## 最新进展（2026-06-20，ACRO 松杆整圈捕获与推力响应）
本轮继续修你刚反馈的 bug：“尝试翻转一周之后会出现持续侧飞无法回正”。根因不是速度上限，而是 ACRO 的累计 roll/pitch 在一整圈附近太“数学精确”：玩家做完一圈稍微放晚，姿态可能停在 450° 左右，旧逻辑会把它当成真实 90° 刀锋姿态继续侧推。现在松杆后的整圈完成判定更符合实际操作。

- `PlayableFlightModel` 扩大了 ACRO 松杆整圈捕获窗口：完全回中或只有很小释放残余输入时，roll/pitch 在最近 360° 倍数约 `115°` 内会吸附为完成的一圈，避免一圈动作后持续产生错误侧向/前向推力。
- 主动打杆仍保留穿越机 rate mode：如果 roll/pitch 仍有明确输入，`450°` 这类 knife-edge 姿态不会被吞掉，仍可以继续全向翻滚和大角度机动；这不是把 ACRO 改回自稳。
- 新增回归测试覆盖 `451° roll` 松杆后不再保留侧向目标/侧向加速，同时把 `450°` 的真实刀锋场景改为“主动打杆”才保留强水平推力，贴近玩家释放动作。
- 可玩层 ACRO 还加入了轻量电机/桨集体推力响应：油门到推重比不再瞬间跳变，使用上升/下降平滑状态模拟几十毫秒量级的动力系统响应，让油门手感更有重量，不再像理想速度控制器。
- 当前已通过 `PlayableFlightModelTest` 定向测试、完整 `gradlew build` 和无头 `runPlayableAcroServerSelfTest`；本次服务端自测因 25565 被占用临时改到 25566 运行，结束后已恢复运行配置。

## 最新进展（2026-06-20，ACRO 机体姿态推力轴与 LOS pitch 修复）
本轮继续针对“斜向飞行像平移、不像真机”的核心手感问题收敛。上一版已经把速度积分改成 `推力 + 重力 + 空阻`，但 ACRO 的推力方向仍先用 pitch/roll 算一个水平目标方向，再把它归一化到推力锥上；这会在斜向飞行时保留一点“二维摇杆平移”的味道。

- `PlayableFlightModel` 的 ACRO 推力轴现在直接取当前姿态矩阵的 body-up 轴：`(-sin(roll), cos(pitch)*cos(roll), sin(pitch)*cos(roll))`。这样 pitch+roll 组合不再被平均成对称水平目标，而是按照真实机体姿态决定侧向、前向和竖直推力分量。
- 可玩层仍保留上一轮的 1.1kg 级 5 寸穿越机锚点、`3.35g` 满油门推重比、机体系线性/二次空阻和 `25m/s+` 速度能力；这轮改的是“推力往哪儿打”，不是简单调大速度或加假惯性。
- 新增回归测试要求 45° pitch + 45° roll 的目标速度和实际一 tick 加速度都呈现 body-up 姿态耦合：侧向分量会明显大于前向分量，避免旧模型继续像平面速度混控。
- 目视飞行的 pitch 符号也修回：正 pitch / 前飞在 LOS 模型上重新表现为机头前压，不再看起来抬头；新增 `DroneEntityModelTest` 固定这个符号语义。

## 最新进展（2026-06-20，ACRO 翻滚后残余侧飞二次修复）
本轮针对你实测的“尝试翻转一周之后会持续侧飞、无法回正”继续收敛。上一版已经能处理 360° 和 395° 左右的残余角，但阈值仍然太窄：真实操作里完成一圈 roll 后很容易停在 420°~428°，画面上玩家会认为已经做完一圈，物理层却把残余 60° 左右横滚当成持续侧向推力。

- `PlayableFlightModel` 的 ACRO 整圈完成捕获从固定 `40°` 改成按当前 ACRO 姿态权限计算：roll 约 `68°+4°`、pitch 约 `64°+4°` 内的松杆过冲会吸附到最近的 360° 倍数，减少一圈动作后的残余侧飞。
- 这是上一轮的中间记录；最新行为见 README 顶部：松杆时 `450°` 附近现在会被视作完成一整圈，主动继续打 roll/pitch 时才保留 knife-edge 侧向推力。
- ACRO 空阻也从 yaw 局部轴改成 pitch/roll 参与的机体系 right/up/forward 三轴阻力，再投回当前局部坐标；翻滚后残留侧滑不会再被一个不随机体姿态旋转的平面阻力模型处理。
- 新增回归测试覆盖 `428° roll`、`425° pitch loop` 松杆后的目标速度归零；最新测试还覆盖松杆 `451° roll` 会完成整圈捕获，以及主动打杆时 `450°` 姿态仍可保留强水平推力。机体系速度投影往返测试用于防止跨过 360° 后坐标轴失真。

## 最新进展（2026-06-20，ACRO 3D 推力积分与惯性手感）
本轮继续针对“斜向飞行还是像平移、不像真机”的手感问题收敛。前一版已经把 pitch/roll 合成到同一个推力锥目标里，但实际速度更新仍主要沿“水平目标速度”工作；这会让玩家感觉飞机被一个平面速度控制器拉着走，而不是被四轴的总推力、重力、惯性和空阻共同带着飞。
- `PlayableFlightModel` 的 ACRO 实际速度更新改为 3D 推力轴积分：先由当前 pitch/roll 得到同一个单位推力轴，再按油门得到集体推力/重量比，最后用 `a = thrustAxis * thrustAcceleration - gravity + drag(v)` 积分速度。中等 pitch+roll 现在会同时产生侧向/前向加速和真实下沉，而不是只改两个水平速度目标。
- 可玩层暂用 5 寸穿越机量级参数做手感锚点：质量按 `1.1kg` racing quad，悬停为 `1g`，满油门可玩上限设为约 `3.35g`，不直接把测试台极限推重比全塞进 Minecraft。前/侧/竖向空阻分开，侧向阻力略大于前向阻力，松杆后保留惯性但会被空气逐步吃掉。
- 参考依据继续沿用并补强：[Faessler/Franchi/Scaramuzza rotor-drag 模型](http://rpg.ifi.uzh.ch/docs/RAL18_Faessler.pdf)和 UZH [`fpv.yaml`](https://raw.githubusercontent.com/uzh-rpg/rpg_quadrotor_control/master/control/position_controller/parameters/fpv.yaml) 说明高速四轴需要机体系阻力；Betaflight [Rate Calculator](https://betaflight.com/docs/wiki/guides/current/Rate-Calculator) / [Feed Forward](https://betaflight.com/docs/wiki/guides/current/Feed-Forward-2-0) 文档说明真机手感来自角速度响应和电机/前馈，而不是位置保持；[ADR-VINS/TII-RATM](https://arxiv.org/abs/2603.02742) 竞速数据给出 `20m/s+` 实飞速度锚点；[`do-a-barrel-roll`](https://github.com/enjarai/do-a-barrel-roll) 仍作为 Minecraft 全向相机/姿态交互参考。
- 目视模型的 pitch 符号按当前实测反馈修回：正 pitch/向前飞在目视下应表现为机头前压，不再出现“向前飞看起来抬头”的 LOS 误导。
- 新增回归测试约束 45 度 pitch+roll 的 ACRO 速度积分：同一个推力锥会给出前向/侧向加速，同时因为竖直投影不足而轻微下沉。`PlayableFlightModelTest` 已通过。
- 无头 ACRO 可玩层自测同步修正：playable 简化路径没有完整空气动力学 `airspeed` 遥测时，只在 playable 模式下用实际 `max_speed` 作为运动判定兜底；完整仿真核心仍要求真实 `airspeed`。本轮 `runPlayableAcroServerSelfTest` 已通过，报告最大速度约 `5.44m/s`、水平位移约 `15.71m`、最大可视 pitch/roll 约 `21.94/25.56 deg`。

## 最新进展（2026-06-20，ACRO 斜向推力锥与目视 pitch 符号）

本轮继续收敛“斜向飞行像平移、不像真机”的手感问题。上一版已经加了惯性、限速和机体系阻力，但 ACRO 水平目标仍是先分别按 pitch/roll 算两个平面速度轴，再拼成一个向量；这在中等斜向姿态下仍会有“二维平移摇杆”的味道。

- `PlayableFlightModel` 的 ACRO 水平目标改为先计算同一个姿态推力锥的水平投影：pitch/roll 只决定水平推力方向，目标强度来自 `sqrt(1 - verticalProjection^2)`，并按最大 ACRO 倾角归一化。这样中等斜向飞行不再像两个平面轴速度叠加，而更像整台四轴把总推力倾到某个方向。
- 速度能力没有回退：大角度前压、横滚和满斜向仍能进入 `25m/s+` 的穿越机速度区间；只是 45 度左右的组合姿态会按真实倾斜锥收敛，不给额外平移感。
- 目视飞行里“向前飞看起来抬头”的符号问题也顺手修了：渲染模型的 pitch 改为与可玩层正 pitch 的“机头前压/向前飞”语义一致，FPV 相机链路不受影响。
- 新增回归测试覆盖 45 度 pitch+roll 的中等斜向目标速度，确认它低于旧的二维轴叠加结果，同时保留强前进/强侧向和整圈翻滚后的行为。

## 最新进展（2026-06-20，ACRO 整圈翻滚过冲侧飞修复）

本轮针对“尝试翻转一周之后会持续侧飞、很难回正”的实测 bug 做了收敛。根因不是单纯的空阻或速度大小，而是 ACRO 下 pitch/roll 会无限累计：玩家想做一圈 roll 时，松杆常常停在 390 度左右；画面上像是完成了一圈，但物理层仍把残余的 30 多度横滚当成持续侧向推力，所以会继续侧飞。

- `PlayableFlightModel` 增加了 ACRO 整圈完成捕获：只有在摇杆已经回中、累计姿态已经接近一整圈、并且距离整圈不超过约 40 度时，才把 pitch/roll 吸附到最近的 360 度倍数，消除一圈特技后的残余侧向目标。
- 这个修复不会把 ACRO 改回自稳：普通 20/45/90 度倾斜、半滚倒飞、持续拉杆翻滚仍然保留原来的 rate mode 语义；只有“完成整圈后松杆”的数值过冲会被当作玩家意图的一整圈。
- 新增回归测试覆盖 395 度 roll、394 度 pitch loop 松杆后 `targetVelocityX/Z` 归零，同时验证 450 度等真实侧倾仍有强水平推力，避免把穿越机手感偷偷磨平。

## 最新进展（2026-06-20，真实穿越机惯性与斜向飞行手感）

本轮按“速度已经够，但斜向飞行像平移、不像真机”的反馈继续收敛可玩层。核心判断是：之前 `playable` 仍然偏“姿态角 -> 局部目标速度 -> 平滑追目标”，尤其 pitch+roll 斜向输入会得到接近两个轴叠加的免费合速度；同时实体把速度存在机体系里，yaw 改变后会把已有动量也跟着机头旋过去，转弯/斜飞就会像被轨道平移。

- 参考资料：Faessler/Franchi/Scaramuzza 的 quadrotor rotor-drag 模型把高速四轴写成 `v_dot = thrust - gravity - R D R^T v`，说明高速飞行必须把机体系线性阻力纳入；RATM 开源竞速机数据集给出 5 寸 Betaflight 平台和 `>21m/s` 实飞锚点；Betaflight 官方 Rate/FeedForward 文档强调真实手感来自角速度曲线、输入前馈和电机响应，而不是位置/速度保持。
- `ACRO` 可玩层现在改成“质量归一化推力加速度 + 机体系线性/二次空阻”的水平速度积分：前飞阻力和侧滑阻力分开，侧向阻力略大，松杆后保留惯性但会被空气逐步吃掉，不再像目标速度刹车。
- pitch+roll 斜向输入现在使用单一水平速度包线做向量限幅，不再让满俯仰+满横滚获得 `sqrt(2)` 的免费速度；这会让斜飞更像带阻力的真实推力矢量，而不是平面平移。
- `DroneEntity` 的可玩层动量参考系修正：yaw 改变时会先把旧局部速度重投影到新机头坐标，世界速度保持不变；机头转向不再凭空旋转已有动量，转弯需要靠后续推力和阻力自然改变轨迹。
- 新增回归测试覆盖 yaw 变向世界动量保持、ACRO 斜向速度包线、ACRO 松杆后空气阻力滑行，以及新阻力模型下仍能逐步加速到 `25m/s+`。

## 最新进展（2026-06-19，ACRO 翻滚后侧飞回归修复）

本轮修复“尝试翻转一周后持续侧飞无法回正”的问题。根因是 ACRO 允许 pitch/roll 姿态角无限累计后，水平速度目标仍直接使用累计角度限幅；roll 到 360 度后数值仍远超最大横滚角，算法会持续给满侧向速度目标。

- `PlayableFlightModel` 现在只在 ACRO 水平速度计算中把 pitch/roll 折算为周期等效倾斜：360 度回到 0，180 度倒飞附近不会被误当成满侧倾，90/270 度仍保留最大水平推力方向。
- 姿态本身仍保持完整累计角度，FPV 相机和机体全向旋转能力不回退；修复只影响“倾斜量 -> 水平速度目标”的物理投影。
- 新增回归测试覆盖完整 roll/pitch 一圈后 `targetVelocityX/Z` 归零，同时验证 90 度倾斜仍有强水平速度目标；ACRO 高速巡航测试改为真实飞法：前压建立姿态后松杆保持并加速到 `25m/s+`。
- 已通过 `:fabric-mod:test --tests com.tenicana.dronecraft.entity.PlayableFlightModelTest` 和完整 `gradlew build`，其中包含 Fabric GameTest。

## 最新进展（2026-06-19，参考 do-a-barrel-roll 的全向 FPV 视角）

本轮按“FPV 仍有回抽、旋转不通畅、无法全向旋转”的实测反馈收敛相机和 ACRO 姿态链路。参考 `enjarai/do-a-barrel-roll` 的核心思路后，重点从“给原版 yaw/pitch 相机补 roll”改成“直接维护完整相机姿态”。
- FPV 相机新增 `FpvCameraOrientation`，由无人机 yaw/pitch/roll 直接生成完整 Quaternion 和 `forwards/up/left` 三个正交方向轴；`CameraMixin` 不再先调用原版 `setRotation(yaw, pitch)` 再补 roll，而是直接写入 `Camera.rotation` 和方向向量，减少高速、大角度、倒飞时被原版二轴视角语义拉回的回抽感。
- `Camera.xRot/yRot` 现在只作为兼容字段写入，并限制在 `-180..180`；真实画面方向以 Quaternion 为准。这样长时间翻滚不会让兼容角度无限增长，也不会再把完整姿态压回 Minecraft 原生 pitch/yaw 的有限表示。
- FPV 挂点不再做类似第三人称相机的方块碰撞回退；机载相机现在固定在机体挂点上，高速贴地、侧滚或倒飞时不会因为挂点射线碰到方块而把画面突然拉回机体中心。
- `ACRO` 从“最大 pitch 115 度 / roll 125 度”的伪 rate mode 改成真正的累计角速度控制：限制输入角速度，不限制累计姿态角。现在持续拉杆可以超过 360 度翻滚/俯仰，松杆后继续保持当前姿态；`ANGLE/HORIZON` 仍保留辅助/自稳角度限制。
- 服务端 playable ACRO 自测规则同步更新：ACRO 中立窗口不再要求 pitch/roll 回到 70 度以内，只要求 yaw rate 收敛；否则会把正确的倒飞/翻滚保持误判成“不稳定”。
- 新增 `FpvCameraOrientationTest` 覆盖水平前向、接近垂直、倒飞邻近姿态、超过一整圈 roll 的 Quaternion 正交和连续性；`PlayableFlightModelTest` 新增 ACRO 超过 360 度并松杆保持的回归测试。
- 已通过针对性测试、完整 `gradlew build`、以及无头 `runPlayableAcroServerSelfTest`。本轮 ACRO 真服报告通过，最大可视 pitch/roll 约 `21.94/25.56 deg`，yaw rate 收敛为 `0.00 dps`；360 度全向能力由单元测试固定。

## 最新进展（2026-06-19，FPV 回抽、零油门重力与惯性）

本轮继续按实测手感收敛：上一版 FPV 帧率改善后仍有“回抽”，同时 `playable` 可玩层的零油门下降太慢，10m 下落会像被空气托住一样拖到 5-6 秒。
- FPV 相机默认延迟从 `0.006s` 改为 `0.0s`，并迁移旧的 sightline 默认配置；保留渲染 `partialTick` 插值，但默认不再把画面送入历史姿态/位置队列，减少高速飞行时的持续回抽感。
- 零油门现在进入更接近重力的快速下落路径：目标下降速度提高到 `18 m/s`，纵向加速度提高到 `10.5..11.5 m/s^2`；新增测试要求 ACRO 从静止零油门下落 `10m` 不超过 `45 tick`。
- 零油门不再凭空保留水平推力权限：即使 ACRO 已经大角度俯仰/横滚，收死油门也只保留已有速度惯性和阻力衰减，不再根据姿态生成新的水平速度目标。
- 普通低油门下降仍保持可控：悬停 detent 和 hover band 继续是 `0 m/s`，只把下推油门的下降范围略放宽；`ANGLE/HORIZON` 自测仍要求中杆稳定，辅助档最终漂移阈值随真实尾滑放宽到 `1.10m`。

## 最新进展（2026-06-19，FPV 流畅度、25m/s 与惯性）

本轮按实测反馈继续收敛穿越机手感：FPV 画面卡顿主要来自相机/机体姿态只按服务端 tick 跳变，速度上限还停留在偏训练档的 12 m/s，并且 `playable` 可玩层更多是速度平滑，不是明确的限加速度惯性。
- `DroneEntity` 现在维护客户端渲染姿态历史，`DroneEntityRenderer` 和 `CameraMixin` 都改用 `partialTick` 插值后的 pitch/yaw/roll，FPV 视角和目视模型不再只跟 20 tick 姿态跳变走。
- `PlayableFlightModel` 的 `ACRO` 水平速度目标提高到 `25 m/s`、限幅 `32 m/s`；`HORIZON` 提高到 `8.8/12.0 m/s`，但继续保留自稳/运动档定位。
- `playable` 速度更新改成“平滑目标 + 每 tick 限加速度”：ACRO 加速上限约 `14 m/s^2`，刹车上限约 `8 m/s^2`，松杆不会把速度瞬间归零；HORIZON 空中刹车也进一步放松，保留更多重量感。
- 完整 `DronePhysics` 原本已经有质量、转动惯量、线加速度 `F/m`、角加速度 `torque/I` 和陀螺项；这次是把游戏内默认可玩直控层也补上可感知的惯性约束。新增回归测试守住 ACRO 持续压杆能达到 `25 m/s+`，且第一 tick 不会瞬间贴到目标速度。
- 无头验证同步更新：`ANGLE/HORIZON` playable 自测仍要求松杆后停稳，`ACRO` 允许带惯性速度结束但限制在 `34 m/s` 内；本轮 `build`、GameTest、`ANGLE/HORIZON/ACRO` playable 服务端自测均已通过。

## 最新进展（2026-06-19，侧飞符号与目视机头对齐）

本轮按实测反馈修正两个方向感问题：`pitch` 前后已经对齐后，`roll` 侧向推力仍沿旧符号运行，导致机顶/升力方向明显倒向一侧时，飞机却向另一侧滑；同时目视模型前后过于对称，LOS 飞行时很难判断哪边才是当前物理前向。
- `PlayableFlightModel` 将 roll 产生的局部 X 速度反号，侧飞现在会跟可视机体倾倒方向一致，不再出现“机顶朝左但还往右飞”的反向感。
- `DroneEntityModel` 在局部 `+Z` 前向增加了一个小机头/相机凸起，让目视飞行能看出哪边是和 FPV/物理一致的机头方向。
- 回归测试新增/更新了 roll 侧向速度符号约束；`pitch` 前后方向、ACRO 不自动回正、姿态推力投影保持上一轮语义。

## 最新进展（2026-06-19，姿态推力耦合与 FPV 加载）

本轮按实测反馈修正两个可玩性问题：一是 `playable` 层不再把油门永远当成世界竖直升力，二是 FPV 模式会把视角状态同步给服务端，让服务端知道玩家正在通过无人机观察世界。
- 姿态物理：`PlayableFlightModel` 现在使用 `cos(pitch) * cos(roll)` 计算竖直推力投影。机头/机身大幅倾斜时，爬升能力会明显下降；ACRO 接近垂直前压时，推油门会转成前向飞行/下沉，而不是继续原地上升。
- 可玩保底：`ANGLE/HORIZON` 小角度仍保留原本的训练手感；低空保护减少倾角后，会自然保留更多竖直爬升余量，不再用旧的“世界竖直速度完全不变”假设。
- FPV 加载链路：新增 `drone_view` 客户端到服务端网络包。按 `B` 进入 FPV 或持续控制时，客户端会同步 FPV 状态；服务端把玩家 camera 切到拥有的无人机，退出 FPV 时切回玩家，避免只在客户端移动相机而服务端仍完全按玩家本体处理观察实体。
- 回归测试新增了大角度 ACRO 推油门不继续竖直爬升、倾斜悬停会损失升力、竖直推力投影随 pitch/roll 衰减的断言；`ANGLE/HORIZON/ACRO` 无头 Minecraft 真服自测均通过。

## 最新进展（2026-06-19，FPV 前推方向修正）

本轮修正了一个会直接破坏 FPV 手感的坐标约定错误：可玩飞行层之前把正 pitch / 机头前压映射到机体系 `-Z`，但 Minecraft 实体朝向和 FPV 机头前方使用的是 `+Z` 前向，所以玩家会看到机头往前压，实际速度却往后退。
- `PlayableFlightModel` 现在把正 pitch 水平速度映射到机体系 `+Z`，再通过 yaw 转换到世界速度，和 `/fpvdrone spawn`、实体朝向、FPV 视角保持同一套前向定义。
- 回归测试同步改成“机头前方 = 局部 +Z”：`yaw=0` 前飞到世界 `+Z`，`yaw=90` 前飞到世界 `-X`，并覆盖 ACRO 松杆持姿后仍沿机头方向漂移。
- 这个改动只修正前后方向，不改变油门、速度上限、Acro 不自动回正、HUD 或校准 UI 的参数。

## 最新进展（2026-06-19，ACRO 不回正与速度提升）

本轮按“速度偏慢、穿越机不应自动回正”的反馈继续收敛可玩层：`ACRO` 飞行模式现在是真正的 rate/acro 语义，松开右杆后不会再把 pitch/roll 往水平姿态拉回；`ANGLE/HORIZON` 仍保留自稳/回正，方便首飞和运动档练习。

- `PlayableFlightModel` 移除了 ACRO 中杆持姿阻尼：松杆后 pitch/roll 会保持上一帧姿态，单元测试现在直接断言释放摇杆 6 tick 后姿态数值不变，避免以后又悄悄把自动回正加回来。
- 水平速度和油门包线整体上调：`ACRO` 可玩层水平速度目标提高到 `25 m/s`、限幅 `32 m/s`，`HORIZON` 提高到 `8.8/12.0 m/s`，`ANGLE` 只提高水平速度但保留上一版姿态/yaw 权限，避免默认自稳档重新变得过敏；竖直速度限幅提高到 `7.2 m/s`。
- `I` 键设置里的 `Acro/穿越` 手感预设改得更直接：`expo=0.52`、roll/pitch/yaw scale `1.00`、输入进入 `0.34/tick`、回中 `0.70/tick`；旧 `0.70/1.00/1.00/0.25/0.55` 穿越手感会自动迁移到新预设。
- 贴地 `ANGLE` 横向权限按新速度重新压低，避免提速后低油门/刚解锁又开始和地面打架；无头自测平台半径从 4 格扩大到 16 格，保证更快的 ACRO 手动漂移不会在诊断尾段飞出平台后因为坠落误报。
- 已通过针对性单元测试、三组无头 Minecraft 真服自测和完整 `gradlew build`：`ANGLE/HORIZON/ACRO` 报告均 `passed: true`，最终速度均为 `0.00 m/s`；诊断脚本最大速度约 `2.41/1.93/3.55 m/s`，最大可视 pitch/roll 约 `7.69/8.20 deg`、`9.22/10.23 deg`、`21.94/25.56 deg`，ACRO 中杆窗口保持姿态约 `13.31/3.53 deg` 且 yaw rate 为 0。

## 最新进展（2026-06-19，校准界面与重量感二次收敛）

本轮按你的新反馈重做 `I` 键遥控器设置页，并继续收敛“先能飞、飞得好”的可玩层手感：校准页不再是一堆分区，而是屏幕中央两个大摇杆，左摇杆显示偏航/油门，右摇杆显示横滚/俯仰，实时指针直接使用游戏内同一条校准和输入塑形链路。

- 新的遥控器校准页支持一键交换左摇杆的“偏航 / 油门”轴，用来修正部分 FPV 遥控器油门推拉和左右偏航对调的问题；也支持交换右摇杆“横滚 / 俯仰”，每个轴仍可单独捕获和反向。
- 重新绑定或交换油门轴时会清掉旧油门行程校准，重新绑定横滚/俯仰/偏航轴时会清掉对应旧中心点，避免把上一根物理轴的校准值错误套到新轴上。
- 可玩飞行层提高了速度包线和竖直速度上限：`ANGLE` 不再像被限速的悬浮实体，`HORIZON/ACRO` 有更明显的速度空间；同时降低速度进入平滑，让机体有惯性和重量感，不是一打杆就瞬间贴到目标速度。
- `ANGLE` 贴地悬停横向权限进一步压低，减少解锁或低油门时和地面打架；给足起飞油门后才恢复横向速度权威。松杆后仍保留自稳/空中刹车，真服自测最终速度均为 `0.00 m/s`。
- 无头 Minecraft 真服自测已通过：`ANGLE/HORIZON/ACRO` 最大可视 pitch/roll 约 `7.69/8.20 deg`、`8.02/8.95 deg`、`18.44/21.75 deg`；辅助模式最终水平距离约 `0.38/0.42 m`，ACRO 保留手动漂移语义；平均电机遥测峰值约 `7.59k/7.55k/7.34k RPM`。

## 最新进展（2026-06-19）

本轮按玩家实测反馈先修“能不能顺手飞”，没有继续堆复杂气动特性；目标是让默认 `ANGLE` 可玩层先像一台能响应遥控的穿越机，而不是像被过度限速的悬浮实体。

- 手柄输入曲线从旧的极慢训练档改成直接可控：核心摇杆命令死区从 `0.25` 降到 `0.08`，默认 `Training` 改为 `expo=0.60`、roll/pitch scale `0.86`、yaw scale `0.95`、输入进入速度 `0.16/tick`、回中 `0.45/tick`；旧 `1.00/0.42/0.38/0.032/0.32` 慢训练配置会自动迁移，不需要手动删 JSON。
- `ANGLE` 可玩飞行层的 pitch/roll/yaw 权限已经放大：默认 70% 原始右杆短促修正现在会在 12 tick 内给出约 `8.35 deg` 的可视 pitch/roll、约 `0.77 deg/tick` 的 yaw，满杆 `ANGLE` 能接近 `22 deg` 姿态和 `1.8 deg/tick` yaw，不再像除油门外的摇杆没实现。
- 零油门和明确下降杆位更快下降：`playable` 下降增益从旧首飞慢档提高到约 `2.2 m/s` 目标量级，同时保留 `0.48..0.52` 手柄悬停卡点和 `ANGLE` 的 `0.055` 悬停带，避免中位轻抖导致慢慢飘高/飘低。
- 贴地悬停的水平权限重新压低：满右杆贴地悬停目标水平速度约束在 `0.2 m/s` 内，但给足起飞油门后仍恢复完整横向权限，减少“还没起飞就在地上打架”，同时不牺牲离地后的操控。
- FPV 默认相机从“宽但畸变”改成“先看清”：默认 FOV 从 `116 deg` 降到 `96 deg`，动态 FOV 关闭，rolling shutter 默认关闭，机载震动降到 `0.02`，延迟降到 `6 ms`；旧宽视角默认配置会自动迁移。这里参考了 do-a-barrel-roll 的思路：重点是稳定的相机/姿态控制，而不是靠夸张广角制造速度感。
- 回归测试已经同步改成新的可玩门槛：覆盖手柄输入塑形、旧配置迁移、FPV 相机默认/迁移、`ANGLE/HORIZON/ACRO` 可玩层响应、松杆回正、贴地权限、零油门下降和 yaw 响应；无头 Minecraft 真服 `ANGLE/HORIZON/ACRO` 自测均通过，辅助模式最终水平距离约 `0.269/0.264 m`，ACRO 保留手动漂移语义，完整 `gradlew build` 通过。

## 本轮追加进展（2026-06-18）
本轮继续按“先能飞、再飞得好”的节奏收敛可玩层手感，README 后续可以继续用中文维护。
- `ANGLE/HORIZON` 辅助模式新增“小姿态水平速度曲线”：机体可视姿态仍按原来的平滑逻辑建立，但小角度不会再线性放大成同等比例的水平速度；`ANGLE` 在细修区约从 55% 水平速度增益起步，接近半姿态后回到线性，`HORIZON` 只做轻微细化，`ACRO` 保持完全手动线性映射。
- 回归测试现在收紧默认 `Training` 手柄中等杆位的水平速度目标：`0.70` 原始右杆经默认曲线后，`ANGLE` 目标水平速度必须低于 `0.035 m/s`，同时满杆仍要超过 `0.15 m/s`，避免“轻/中杆太灵敏”和“满杆没权威”互相挤占。
- 诊断脚本同步适配新的小姿态速度曲线：`ANGLE/HORIZON` 服务端自测会用更明确的水平练习杆量来证明水平通道仍被激活，`ACRO` 仍保留原手动脚本强度；最新真服报告中 `ANGLE/HORIZON/ACRO` 最大水平位移约 `0.059/0.265/0.967 m`，最终速度均为 `0.00 m/s`。
- `ANGLE/HORIZON` 辅助模式的水平 air-brake 现在会在悬停带及以上油门生效：爬升时松开右杆也会主动收掉水平漂移，不再只在悬停油门附近刹车；`ACRO` 仍保留手动持姿和漂移语义。
- 新增回归测试确认默认 `ANGLE` 在 `0.60` 油门建立明显水平速度后，松开右杆 4 tick 内会把水平速度收进 `0.04 m/s`，避免“轻打杆后爬升阶段还继续飘”的训练模式手感。
- `playable` 内部竖直速度现在有独立的回悬停刹车：当目标竖直速度已经回到 0、但机体还带着上升/下降速度时，会使用 `0.56` 的竖直刹车平滑，而不是继续沿用横向速度的较慢刹车；这能减少轻推油门后回中仍继续飘高/飘低的感觉。
- 新增回归测试确认默认 `ANGLE` 明显爬升后把油门放回 `0.20` 悬停点，4 tick 内竖直速度会收进 `0.08 m/s`，同时原有起飞权威测试继续守住 `0.60` 油门能正常离地。
- `playable` 服务端油门滤波现在区分上油和回油：上油仍按 `0.24` 平滑进入，回油/回悬停提高到 `0.42`，减少玩家把左杆放回悬停后服务端残余油门继续爬升的拖尾感。
- 新增回归测试确认 `0.60 -> 0.20` 的服务端油门回收会在 4 tick 内回到默认 `ANGLE` 悬停带附近，同时 `0.20 -> 0.60` 仍然是渐进进入，避免一推油门就突兀窜升。
- `playable` 垂直油门响应现在会在悬停油门带边缘增加一小段软启动：油门刚刚越过悬停卡点时不会立刻把整段 `throttle - hoverThrottle` 都打到爬升/下降速度上，减轻“轻推油门就突然窜/沉一下”的不连续手感。
- 新增回归测试锁住悬停带边缘：默认 `ANGLE` 下 `0.254/0.146` 仍保持 0 垂直速度，刚越过边缘的 `0.256/0.144` 只给极小爬升/下降速度，而 `0.320` 和手柄 `0.40/0.60` 这类明确杆位仍能稳定建立下降/爬升。

## 最新进展（2026-06-18）

本轮继续按“先能飞、再飞得好”的方向收敛，没有一次性打开更多复杂气动特性；重点是把玩家最明显的手感问题转成可自动回归的门槛。

- `playable` 无头服务端自测现在会把中杆稳定性纳入通过条件：至少 20 个“已解锁、有电机输出、pitch/roll/yaw 输入为中立”的样本，且可视 pitch/roll 不超过 `1.5 deg`、可视 yaw rate 不超过 `0.35 dps`。
- 自测在判定通过/失败前会先刷新黑盒摘要，不再等到 JSON 报告生成时才计算中杆窗口指标；这样“右杆没动还自转/乱歪”的回归会直接让 Gradle 自测失败。
- 游戏内遥控器设置界面现在会显示经过反向、中心校准、油门校准、死区、expo 和 rate scale 后的实际 `T/P/R/Y` 输出预览；如果绑定轴缺失，会直接在设置界面提示，方便定位“轻碰就歪”“轴反了”“摇杆中心漂移”等手感问题。
- 本轮重新核对空气阻力链路：真实 `DronePhysics` 仍按 `linearDragCoefficient` 的线性阻尼 `F=-c*v` 加机体系数二次阻力 `F_i=-c_i*v_i*|v_i|` 施力，10 m/s 前向 runtime 阻力约 `2.25 N`，不是 CSV 投影行里的 `18.45 N`；新增测试会把 `AirframeDragCalibration` 的 CdA guard sample 与物理步进 telemetry 对齐，避免后续把线性阻尼误当二次 CdA 去调参。
- 可玩飞行层现在把“机体系速度 ↔ 世界速度”的 yaw 相对映射提成可测试 helper，实体移动和碰撞后的速度回写都复用同一套公式；新增回归测试守住机头转向后前推/横滚仍跟随无人机朝向，避免 FPV 或目视飞行出现“机头转了但速度还按世界坐标走”的怪手感。
- 本轮给 `playable` 模式切换加入 6 tick 软接管窗口，并把该窗口接入实体状态保存：从 `HORIZON` 切到 `ACRO` 后，如果右杆已经回中，会温和刹掉旧水平速度和偏航残留，同时保留 Acro 的持姿特性；新增测试守住“切到 Acro 仍是 Acro，但不会继承过强旧状态突然甩出去”。
- 无头服务端可玩层自测现在支持指定诊断飞行模式，并新增 `runPlayableHorizonServerSelfTest`：报告会写入并校验 `self_test_control_mode`，本轮 `HORIZON` 真服自测通过，最大可视 pitch/roll 约 `4.48/5.21 deg`，中杆窗口 87 个样本，neutral pitch 最大 `0.70 deg`、roll/yaw 为 0；这样第二档手感也进入 Minecraft 服务端级回归，而不只是在单元测试里存在。
- `ACRO` 现在也有独立无头服务端回归入口 `runPlayableAcroServerSelfTest`：Acro 的中杆判定不再错误要求机身自动回水平，而是要求姿态/偏航权威被真实激活、松杆后 yaw rate 收住、持有姿态仍在可控范围内；本轮真服自测通过，最大可视 pitch/roll 约 `11.96/14.41 deg`、yaw rate 约 `27.64 dps`，中杆 yaw 为 0。
- 客户端 `ACRO` 手感档从“满血竞速”改成更适合 Minecraft 游玩的渐进档：expo 提高到 `1.00`、roll/pitch scale 保留 `0.96` 满杆权威、yaw scale 降到 `0.84`、起杆从 `0.20/tick` 放慢到 `0.14/tick`，旧 Acro 配置会自动迁移；新增回归测试守住 70% 杆位 1 秒内仍是可控中段，而满杆仍能到大角度。
- 默认 FPV 相机继续往“先看得清再谈高拟真”收敛：挂点推到 `1.20 m` 前 / `0.72 m` 上、默认视野到 `116 deg`、机载震动降到 `0.08`、rolling shutter 降到 `0.04`、相机延迟降到 `12 ms`；上一轮 `1.12/0.68/112 deg` 默认配置会自动迁移，新测试守住所有内置机架的机体外视线余量。
- `playable` 平均转速遥测从早期 `sqrt(throttle)` 占位曲线改成悬停油门锚定曲线：armed idle 仍是约 `2.2k RPM`，默认 `0.20` 悬停油门约 `6.6k RPM`，轻推爬升仍低于 `7k RPM`，高油门平滑爬升到约 `12.3k RPM`；新增测试守住这个范围，避免 HUD 再出现“刚起步就像 18k 疯转”的误导。
- `playable` 无头服务端自测现在也会拒绝平均电机 RPM telemetry 峰值超过 `11k RPM` 的报告；这个阈值高于当前 `ANGLE/HORIZON/ACRO` 自测的 `7.7k..8.7k`，但能挡住 HUD/黑盒重新回到夸张转速的回归。
- `ANGLE/HORIZON` 可玩层无头自测现在还会检查诊断结束后的最终水平距离：辅助稳定模式必须回到距起飞点 `0.30 m` 内，当前 `ANGLE` 约 `0.05 m`、`HORIZON` 约 `0.15 m`；`ACRO` 保留手动持姿漂移语义，不套用这个辅助模式上限。
- `playable` 自测结束时还必须真正停住：所有飞行模式的最终速度都要低于 `0.08 m/s`，当前 `ANGLE/HORIZON/ACRO` 报告均为 `0.00 m/s`，避免落地或上锁后仍缓慢滑行的手感回归。
- 当前 README 可以继续用中文维护；英文长段技术记录先保留在后面，新的研究和调参结论会优先写在中文进展区。

这是一个面向 Minecraft/Fabric 的高频多旋翼无人机/穿越机模拟 Mod。项目目标不是做一个会飘起来的简单实体，而是在 Minecraft 世界里逐步实现可玩、可测试、可调参的 FPV 多轴无人机仿真系统。

项目分为两个主要模块：

- `drone-sim-core`：纯 Java 物理核心，包含 6DOF 多旋翼动力学、PID/模式控制、电机/桨/电池/扰流等模型，便于离线测试。
- `fabric-mod`：Minecraft/Fabric 接入层，包含无人机实体、遥控器物品、客户端控制、HUD、FPV 相机、网络同步和 GameTest。

## 当前进展

2026-06-18 的重点是先保证“能飞、能控、能调”，再逐步打开更复杂的空气动力学特性。

- 默认飞行模式是 `ANGLE`，首次起飞更接近稳定训练模式，不会直接进入全手动 Acro。
- 空闲、断链 failsafe、控制包超时和物理状态空值回退现在也统一保持玩家首飞用的 `ANGLE` 语义；诊断结束或上锁后的 HUD/黑盒不会再短暂跳成 `ACRO`，减少误把全手动状态当默认手感来调参的风险。
- `playable` 可玩飞行层的兜底模式和零状态也统一回到首飞 `ANGLE`；客户端摇杆平滑器新增回中零点吸附，短促修正后的小残余命令会在回中帧直接清零，减少松杆后继续歪一两帧的拖尾感。
- 无人机实体同步、可玩层调试状态、direct playable failsafe 和稳定空闲输入现在共用同一个首飞默认模式常量；GameTest 会验证新生成实体和 direct failsafe 都保持 `ANGLE`，避免默认模式以后再次分叉。
- 解锁需要低油门和摇杆回中；也支持 Mode 2 遥控器的双摇杆底角解锁/上锁手势。
- 解锁和 FPV 视角已经分离：`B` 切换 FPV/目视飞行，`N` 切换 HUD 精简/隐藏/完整遥测。
- HUD 模式现在会写入客户端配置，隐藏 HUD 后重进游戏仍会保持隐藏。
- 中英文语言文件现在有测试守护：设置界面、HUD 和客户端提示直接引用的 `fpvdrone` 翻译 key 必须同时存在于 `en_us` 和 `zh_cn`，避免遥控器设置界面出现未翻译 key。
- FPV 相机的最终挂载点现在会把振动/jello 位移也计入清晰下限，旧配置或强振动不会再把视角短暂拉回机身附近，降低“画面被无人机挡住”的风险。
- FPV 默认相机继续向机头外和机身上方移动：默认挂点为前 `1.20 m`、上 `0.72 m`，旧 `1.05/0.62` 和 `1.12/0.68` 清晰相机配置都会自动迁移；测试会把所有内置机型的碰撞盒纳入校验，避免 FPV 视野再次被机身挡住。
- `playable` 层的 HUD/黑盒平均转速现在以悬停油门为锚点，而不是把低油门直接开根号放大；默认悬停油门显示约 `6.6k RPM`，轻推油门不会马上跳到夸张高转速，高油门仍保留明显上升空间。
- 手柄/遥控器的俯仰、横滚、偏航经过死区、expo、倍率和每 tick 输入限速，再发送给无人机；默认 `Training` 档进一步降低了右摇杆中段和半杆响应，轻打杆不会直接把机体打歪。
- `Training` 手感继续收敛：默认曲线改为纯三次 `expo=1.00`，命令中心软区从 `0.22` 加宽到 `0.25`，横滚/俯仰倍率维持 `0.42`、偏航维持 `0.38`、输入进入速度维持 `0.032/tick`、回中速度维持 `0.32/tick`；旧 `0.48/0.44` 训练档也会自动迁移，进一步减少“右杆轻碰就歪”的首飞挫败感。
- 客户端真实手柄链路现在有包内测试覆盖：原始摇杆先做物理中心死区和校准，再进入 Training/Sport/Acro 控制曲线，避免只测理论曲线却漏掉游戏内实际发包路径。
- 新增默认 `Training` 客户端手感到 `playable` 飞行层的合成测试：`0.70` 原始右杆短促修正经过默认手柄曲线和每 tick 输入限速后，可视 pitch/roll 必须低于 `0.75 deg`、水平目标速度低于 `0.045 m/s`，松杆 8 tick 后姿态回到 `0.15 deg` 内且偏航/水平目标归零，用自动化守住“轻打杆能微调，不会一碰就乱歪”。
- 手柄油门曲线新增很小的悬停卡点：原始油门 `0.48..0.52` 会稳定映射到 `0.20` 悬停命令，减少左摇杆中位轻微抖动导致 HUD/RPM/电机输出来回跳；`0.55` 仍留在悬停带内，`0.60` 开始明确爬升，`0.40` 仍可下降。
- 手柄油门悬停卡点现在会跟随当前绑定无人机的 `hoverThrottle()`：默认 5 寸 racing quad 仍是 `0.20`，但 cinewhoop、重载机或自定义机架不再把左摇杆中位固定当成 `0.20`，`ANGLE` 可玩层也有测试覆盖 `0.40` 悬停油门的稳定窗口。
- 手柄油门现在也经过轻量输入限速：上升最多 `0.08/tick`、下降最多 `0.14/tick`，到悬停油门约 3 tick，回油更快；这样能压掉手柄轴瞬时跳变和卡点外小抖动，同时不挡正常起飞或油门修正。
- 解锁、上锁、Mode 2 双摇杆底角手势切换，以及暂时失去遥控/虚拟遥控控制权时，客户端会统一清空残留油门、手柄输入平滑器和键盘虚拟轴；这样下一次重新解锁不会继承上一段高油门或旧杆量，降低“刚解锁就窜一下/抖一下”的手感风险。
- 客户端现在会把本地解锁状态和油门平滑器绑定到当前受控无人机；如果绑定目标从一台机体切到另一台或变为空，会自动清空旧 `armed`、油门和摇杆残留，避免不同机型/不同 `hoverThrottle()` 之间串状态。
- 遥控器设置界面新增“校准摇杆中心”，会记录横滚、俯仰、偏航三轴的静止偏移并在发包前扣掉，用来处理手柄中心漂移导致的无输入自转或缓慢偏移。
- 服务端可玩飞行层不再对客户端已经塑形过的摇杆命令重复套大死区/expo，只保留极小噪声门槛和限速；`ANGLE`/`HORIZON` 稳定模式会把松杆后的极小残余速度、姿态和偏航率收敛到 0，减少“没打杆还慢慢歪/转”的手感。
- `playable` 可玩层新增低油门/贴地横向权限曲线：解锁后贴地悬停区会压低横滚、俯仰带来的水平速度，给足起飞油门后再恢复完整权限，减少刚离地轻推杆就窜歪的训练档手感问题。
- 上锁、断链落地或退出 `playable` 直接飞行层时，会显式清空电机、ESC、RPM telemetry 和 per-rotor 推力，避免黑盒/HUD 在上锁后还显示上一帧的旧转速或旧推力。
- 真实 `simulation` 物理层的地面休眠现在会同时记录 raw 输入、零油门 processed 输入、链路状态和 receiver frame error，避免上锁落地后黑盒/HUD 混入上一帧飞行命令。
- 调试命令新增 `/fpvdrone debug mode playable|sim`，可以明确切换当前稳定可玩的 Minecraft 飞行层和 6DOF 物理仿真层，方便分阶段对比手感和物理问题。
- 黑盒 CSV 和摘要现在记录 `flight_model`，能区分本次日志来自 `playable` 可玩飞行层还是 `simulation` 6DOF 仿真层，后续排查“能飞但手感差”和“物理层自己发散”会更直接。
- `I` 打开游戏内遥控器设置界面，`Feel` 按钮可循环 `Training`、`Sport`、`Acro` 三档手感，不用手动改 JSON。
- 最新无头服务端 `simulation` 自测通过：12 秒默认 `ANGLE` 诊断脚本飞行、最大爬升约 `2.58 m`、最大速度约 `2.91 m/s`、最终速度 `0.00 m/s`、最终水平距离约 `3.91 m`、平均电机遥测峰值约 `15.41k RPM`、249 个样本、200 Hz 物理步进。
- 新增并跑通无头服务端 `playable` 可玩飞行层自测；诊断脚本现在使用玩家默认的 `ANGLE` 首飞模式：12 秒脚本飞行、最大爬升约 `1.82 m`、最大速度约 `4.24 m/s`、最终速度 `0.00 m/s`、最终水平距离约 `0.05 m`、平均电机遥测峰值约 `8.67k RPM`、249 个样本；黑盒 CSV 会明确写入 `flight_model=playable` 和 `flight_mode=angle`，并记录可玩层的 `control_*`、ESC 输出、RPM telemetry 和 per-rotor 推力。
- 诊断飞行脚本的尾段现在会提前归零横向/姿态指令，先软着陆到 ground-lock 附近再上锁，并在服务端自测 JSON 中记录最终高度增益和最终水平距离；后续调手感时可以区分“脚本半空锁桨坠落”和“真正松杆/落地收敛”。
- `playable` 可玩层新增离地低空横向权限缓释：刚脱离 ground-lock 后会把俯仰/横滚带来的水平速度从约 62% 平滑放开到 100%，减少刚起飞轻碰右杆就突然窜歪；这条保护不钳制垂直速度，所以不会挡住正常起飞或下降。
- 黑盒 CSV 新增 `playable_low_altitude_authority`，`playable` 日志能直接看到低空保护是否正在压低水平权限；后续排查“刚起飞歪”“轻杆过敏”时，不必只靠主观手感猜。
- 服务端自测 JSON 现在会汇总 `min_playable_low_altitude_authority` 和最大低空压制百分比，`/fpvdrone blackbox summary` 也会显示 `lowAlt`；以后调低空起飞手感时，能从无头测试和游戏内摘要同时确认保护是否真的介入。
- `playable` 低空保护现在同步压低刚起飞阶段的可视俯仰/横滚和偏航权限，不再只是压水平速度；满油门爬升仍不被限制，减少离地瞬间机身先大幅歪斜或自转带来的挫败感。
- 黑盒 CSV、`/fpvdrone blackbox summary` 和无头服务端自测现在额外记录 `playable` 可玩层真实可视 pitch/roll、偏航速率和首尾 yaw 漂移；最新默认 `ANGLE` playable 自测最大可视 pitch 约 `1.43 deg`、roll 约 `1.56 deg`、yaw rate 约 `3.92 dps`、最终 yaw 漂移 `0.00 deg`，后续调参可以直接看玩家画面姿态是否过敏或自转。
- 服务端 `playable` 输入滤波现在在摇杆回中和反向过零时会快速归零残余命令，减少松杆后几帧仍继续倾斜/偏航的拖尾；本次把服务端第二道轴输入进入速度从 `0.18` 收到 `0.14`，回中/反向清尾从 `0.56` 提到 `0.68`，短促轻杆会更慢进入、松杆会更快归零，同时 10 tick 满杆仍能建立足够控制权。
- `ANGLE` 训练模式的可玩层姿态和偏航上限继续收敛：最大可视俯仰/横滚从 `12 deg` 降到 `10 deg`，水平速度上限从 `0.88 m/s` 降到 `0.78 m/s`，偏航指令从 `0.48 deg/tick` 降到 `0.40 deg/tick`；满杆仍能起飞修正，但轻推右杆不再那么容易把机身打歪。
- 解锁安全规则现在同时在客户端和服务端生效：服务器只允许低油门/摇杆安全或 Mode 2 底角手势从上锁进入解锁，拒绝异常高油门 armed 包；安全解锁后正常推油门不受限制，最新 playable 无头自测仍通过。
- HUD 现在会在未解锁时显示“就绪”或“降油门 / 回中”，并复用同一套服务端解锁安全规则；这样按了解锁没反应时，玩家能直接看出是油门/摇杆状态不安全，而不是误以为物理模型或遥控链路坏了。
- 服务端自测 JSON 现在直接写入 `flight_mode`，Gradle 会同时校验 JSON、`flight_mode` 和 `control_flight_mode` 三者一致；当前默认诊断链路明确锁定在玩家首飞用的 `ANGLE` 模式，避免之后误把 HORIZON/ACRO 的手感当成默认体验来调。
- `playable` 无头自测现在会单独统计“右杆中立且已解锁/电机有输出”的稳定窗口：Gradle 要求至少 20 个中杆样本，且中杆可视 pitch/roll 不超过 `1.5 deg`、yaw rate 不超过 `0.35 dps`；最新默认 `ANGLE` playable 报告中杆样本 87 个，最大 pitch `0.00 deg`、roll `0.00 deg`、yaw rate `0.00 dps`，用自动化方式守住“没打杆不自转/不乱歪”。
- 键盘/虚拟遥控器输入现在单独走训练级整形器：短按 5 tick 只到 `0.25` 虚拟轴，整形后右杆命令小于 `0.06`，松开后一帧内基本回中；长按约 1 秒仍能到满舵，方便没有实体遥控器时微调起飞和目视飞行。
- 键盘油门也加入了当前机型 `hoverThrottle()` 附近的细调/吸附：远离悬停点仍按 `0.014/tick` 快速加减，进入悬停窗口后改为 `0.007/tick`，跨过悬停油门会吸附一次；继续按同方向会离开吸附点，方便 `PageUp/PageDown` 做稳定悬停而不是反复越过油门甜点。
- HUD 顶部现在会显示当前输入源（键盘/手柄）；完整 HUD 会显示手柄油门校准状态，精简 HUD 只在油门未校准或正在校准时提示，减少误以为“手柄没接管/油门坏了”的排查成本，同时不额外遮挡大块视野。
- 完整 HUD 现在会在手柄输入时显示当前手感预设（`Training` / `Sport` / `Acro` / `Custom`），并复用中英文设置界面翻译；精简 HUD 不显示这行，避免为了排查手感状态又牺牲目视/FPV 视野。
- `M` 飞行模式切换现在从默认 `ANGLE` 先到 `HORIZON`，再到 `ACRO`；网络包里非法 `flightMode` id 也会回退到 `ANGLE`，避免误触或异常包直接进入全手动模式。

## 常用键位

- `R`：解锁 / 上锁无人机。
- `B`：切换 FPV 视角和目视飞行。
- `N`：切换 HUD 精简 / 隐藏 / 完整遥测，并保存偏好。
- `I`：打开遥控器设置界面，绑定轴/按钮、校准油门、切换手感预设。
- `H`：重新加载客户端配置文件。
- `M`：按 `ANGLE -> HORIZON -> ACRO` 顺序切换飞行模式。
- `V`：启用/关闭虚拟遥控器，方便没有手柄时测试。
- `PageUp` / `PageDown`：键盘油门。
- 方向键、`Z`、`X`：键盘俯仰/横滚/偏航测试输入。

## 常用命令

- `/fpvdrone spawn racing_quad`：生成并绑定默认 5 英寸穿越机。
- `/fpvdrone debug mode playable`：使用当前稳定的可玩飞行层，适合日常试玩和手感调试。
- `/fpvdrone debug mode sim`：切到 6DOF 物理仿真层，用来定位物理模型和飞控问题。
- `/fpvdrone debug status`：查看当前调试状态，包括飞行层模式。
- `/fpvdrone diagnostic record 12`：运行 12 秒诊断飞行并记录黑盒数据。

## 当前设计原则

- 先解决可玩性：起飞、悬停、低速修正、视角清晰、HUD 不挡视野、遥控器可调。
- 物理模型逐项打开：先保留稳定的基础飞行，再逐步增强桨洗、涡环、侧流遮挡、损伤、电池/电机热模型等复杂效应。
- 每个改动都尽量有单元测试、GameTest 或无头服务端自测兜底，避免“这次能飞、下次又翻”的回归。

下面保留了较完整的英文技术记录和调参说明，后续会逐步把关键内容迁移为中文。

## 构建

```powershell
.\gradlew.bat build
```

可游玩的 Fabric Mod jar 会生成在：

```text
fabric-mod/build/libs/fpv-dronecraft-fabric-0.1.0.jar
```

## 离线物理基准

不启动 Minecraft 也可以生成确定性的飞行 CSV：

```powershell
.\gradlew.bat :drone-sim-core:offlineFlight
```

默认输出到：

```text
drone-sim-core/build/offline-flight/racing_quad.csv
```

The scripted profile runs the same 200 Hz physics loop used by the mod and samples a CSV at 50 Hz. It includes takeoff, hover, zero-throttle descent, high-throttle propwash recovery, crosswind slip, a shared-geometry wall-skim pass, pitch/roll/yaw steps, a rain burst for wet-prop load/thrust-loss telemetry, throttle punch, throttle-chop active braking, and a light single-prop fault check. The columns include position, velocity, true acceleration, true and estimated attitude, estimator error and accelerometer trust, true and gyro-measured body rates, thermal/vibration-driven gyro bias, g-sensitive gyro error, and clipping, motor-fundamental and blade-pass dynamic gyro notch frequency/attenuation, accelerometer specific force at the configured IMU mount plus scale-factor/cross-axis error, bias, and clipping, pressure-altitude barometer altitude/vertical-speed/pressure/error telemetry with sensor noise split from `barometer_pressure_port_error_m` static-port dynamic-pressure/rapid-rotation bias and `barometer_propwash_error_m` propwash/ground-pressure/ceiling-pressure bias, raw and processed RC commands, receiver frame age/interval/error telemetry, flight mode, link-loss/failsafe telemetry, ESC output plus command frame age/interval/error telemetry, ESC desync, per-ESC temperature/cooling/thermal-limit telemetry, per-motor RPM/target RPM/tracking error/actuator authority/angular acceleration/aerodynamic torque/mechanical-loss torque/commutation torque ripple/shaft power/battery current/regenerative current/phase current/current ripple/electrical-efficiency/voltage-headroom with voltage-limit loss, battery nonlinear LiPo open-circuit voltage, low-SOC resistance rise, ohmic sag, transient sag recovery, active-braking regenerative current, battery bus voltage spike, battery bus ripple voltage, battery pack temperature/cooling/thermal-limit telemetry, battery current-limit foldback, motor heat, local motor cooling factor, rotor thrust plus body-frame force/torque vector telemetry, per-rotor spring-damped arm-flex intensity, millimeter deflection, and thrust-axis tilt, healthy-prop rotor imbalance with rotating lateral force, axis-aware induced inflow, effective translational lift, rotor advance ratio plus UIUC-equivalent propeller advance ratio J, rotor tip Mach, rotor compressibility thrust scale, rotor blade angle-of-attack, blade-element stall, advancing/retreating blade lift-dissymmetry, blade-pass thrust-ripple intensity, rotor aerodynamic load, blade lift-dissymmetry hub torque, rotor inflow-skew torque, same-frame rotor wake-interference intensity, crossflow-convected wake sweep, wake-swirl velocity, low-throttle rotor windmilling intensity, rotor inertia/gyroscopic torque, dynamic flapping tilt/force and separated flapping force-arm torque, elastic blade-coning intensity and angle, rotor stall intensity, rotor vibration, propwash wake retention, vortex-ring-state intensity plus VRS thrust-buffet amplitude/force, mixer saturation, split low/high mixer saturation and headroom, achieved mixer torque and per-axis mixer authority, PID setpoint/error and P/I/D/feedforward/output terms, self-level target/error/blend telemetry, PID attenuation, dynamic D-term low-pass cutoff, anti-gravity boost, scalar and per-axis I-term relax/anti-windup telemetry, propwash torque, airframe aerodynamic torque, dynamic-pressure airframe angular-drag torque, airframe lift/sideforce, body-drag force, linear damping drag, separated-flow drag rise/buffeting, near-ground cushion drag, rotor-wash slipstream drag, rotor near-wall sidewash/cushion force, body-frame relative air velocity, airspeed, angle of attack, sideslip, turbulence intensity, obstacle proximity, standard-atmosphere and wet-air effective air density, ambient temperature, average and per-rotor water-immersion intensity, precipitation wetness, raw weather wind and boundary-layer-adjusted effective air-mass wind, gust speed, wind-shear acceleration, ceiling clearance/effect, per-rotor environment thrust multipliers, per-rotor health, per-rotor side-flow obstruction, wind-turbulence torque, and appended rotor 4..7 extension telemetry for six- and eight-rotor frames.

Low-Reynolds rotor efficiency loss is logged as `rotor_low_reynolds_loss` plus per-rotor columns through `rotor_7_low_reynolds_loss`; the physical audit columns `rotor_reynolds_number` and `rotor_reynolds_index` are logged with per-rotor columns through rotor 7, so tiny-prop efficiency loss can be compared against its Reynolds regime, tip Mach, advance ratio, and blade-stall telemetry.
Rotor tip-Mach compressibility thrust retention is logged as `rotor_compressibility_thrust_scale` plus per-rotor columns through `rotor_7_compressibility_thrust_scale`, so high-KV or oversized-prop thrust loss can be compared directly against tip Mach.
The low-Re proxy now uses a derived representative blade chord from pitch, blade count, and utility-lift prop geometry, so wide three-blade FPV/cinewhoop props and broader low-pitch lift props do not share the old fixed 0.12R chord assumption.
The in-game status/HUD exposes low-Re loss as `lowre`/`Re` and Mach compressibility loss as `machloss`/`MC`, raising `low-re` or `compressibility-loss` warnings when either becomes a meaningful tuning risk.
Live status/HUD output also shows minimum motor voltage headroom, making battery sag or back-EMF-limited punch-outs visible without opening a saved CSV.
Rotor blade angle-of-attack and blade-element stall are also surfaced live as `blade`/`bstall` and `BA`/`BS`, so prop pitch or steep-descent tuning can be checked before saving a blackbox log.
Induced inflow velocity and throttle-punch inflow-lag thrust loss are surfaced live as `ind`/`iloss` and `Iv`/`IL`, logged as `avg_induced_velocity_mps` plus `min_induced_lag_thrust_scale` offline and `rotor_induced_velocity_mps` plus `rotor_induced_lag_thrust_scale` in blackbox CSV, so soft punch-outs can be separated from battery sag, ESC slew, propwash, and wake-interference loss. The runtime wake response now scales the configured `rotor_inflow_tau` by the current momentum-theory induced velocity, disk-plane flush, climb flow, and descending wake carryover, then logs the effective value as `rotor_dynamic_inflow_tau_s` with per-rotor columns through `rotor_7_dynamic_inflow_tau_s`.
High-advance blade lift-dissymmetry now feeds a body-frame hub-moment torque and blackbox/offline CSV columns `rotor_blade_dissymmetry_pitch_torque_nm`, `rotor_blade_dissymmetry_yaw_torque_nm`, and `rotor_blade_dissymmetry_roll_torque_nm`, so crosswind or fast forward-flight prop loading can be separated from inflow-skew and rotor-inertia torque.
High-advance rotor reverse flow is logged as `rotor_reverse_flow_fraction` plus per-rotor columns through `rotor_7_reverse_flow_fraction`, using the retreating-blade inboard fraction implied by `mu = V/(omega R)` so fast FPV slip can be compared against blade-dissymmetry and stall telemetry.
Live status and HUD output expose the same hub moment as `bdiss`/`BD`, with a `blade-dissymmetry` warning when high-advance blade loading becomes a live handling risk.
Rotor in-plane H-force from disk-plane airflow is logged as `rotor_in_plane_drag_force_n` plus per-rotor columns through `rotor_7_in_plane_drag_force_n`; it is surfaced live as `hforce`/`H`, included in blackbox summaries and offline reports as `max_hforce`, and raises `rotor-hforce` when fast forward/crosswind prop loading becomes a live handling risk.
Airframe drag is split in CSV logs as `airframe_body_drag_n` and `linear_damping_drag_n`; offline summaries now print `max_body_drag`/`max_linear_drag`, and blackbox summaries print `bodyD`/`linD`, so the physical body-drag term can be checked separately from the shared low-speed damping term when matching coastdown data.
Same-frame wake swirl is also surfaced live as `swirl`/`SW` in meters per second, so coaxial or stacked-rotor wake coupling can be tuned before saving a blackbox log. Its velocity cap now scales with the receiver rotor's disk loading and tip speed instead of a fixed 8 m/s ceiling, keeping high-load X8 wake-swirl telemetry tied to rotor scale.
Coaxial wake thrust loss is logged as `rotor_wake_thrust_scale` plus per-rotor columns through `rotor_7_wake_thrust_scale`, making lower-stack prop efficiency visible for X8 tuning.
Coaxial X8 load sharing is logged as `rotor_coaxial_load_bias` plus signed per-rotor columns through `rotor_7_coaxial_load_bias`, where upper rotors are positive and lower rotors are negative; the allocation strength follows the New Dexterity-style z/D efficiency windows for stacked rotors, status/HUD expose it as `coax`/`CX`, blackbox summaries print `coax`, offline reports print `max_coax_bias`, and `coax-load-bias` warns when upper/lower thrust allocation becomes a live X8 tuning risk. The z/D=0.72 runtime command-map prior now uses a five-point smoothed lookup and logs audit columns for `rotor_coaxial_load_bias_target`, `rotor_coaxial_load_bias_clipping`, `rotor_coaxial_allocation_load`, `rotor_coaxial_allocation_ratio`, `rotor_coaxial_allocation_mech_gain_pct`, `rotor_coaxial_allocation_elec_gain_pct`, and `rotor_coaxial_allocation_uncertainty_pct`; realized mechanical gain also scales coaxial aerodynamic shaft torque and shaft power when the requested upper/lower bias is actually achieved, so command-limit clipping, model uncertainty, and expected efficiency gain can be checked from blackbox/offline CSV output.
Water immersion and precipitation prop-thrust loss is logged as `rotor_wet_thrust_scale`, surfaced live as `wetloss`/`WW`, printed in blackbox summaries as `wetloss`, included in offline recorder summaries as `max_wet_loss`, and raises `wet-thrust-loss` when the wet prop model is visibly reducing authority. Pure precipitation is capped near the ICAS heavy-rain CT-loss scale of a few percent, now builds through disk-averaged per-rotor rain exposure and a per-rotor wet-prop surface film, dries faster at high RPM or high disk airflow, and is exported as `rotor_N_precipitation_wetness`; water immersion remains the severe water-ingestion path.
Wake swirl now also feeds a body-frame rotor hub moment and blackbox/offline CSV columns `rotor_wake_swirl_pitch_torque_nm`, `rotor_wake_swirl_yaw_torque_nm`, and `rotor_wake_swirl_roll_torque_nm`, so asymmetric stacked-rotor swirl can be separated from ordinary inflow-skew and blade-dissymmetry moments.
Live status and HUD output expose the same wake-swirl hub moment as `swirlT`/`WT`; blackbox summaries print `swirlT`, and the offline recorder summary prints `max_wake_swirl_torque` alongside `max_bdiss_torque`, making peak wake-swirl and blade-dissymmetry hub moments visible in automated tuning runs without opening the CSV.
Low-throttle reverse-axial-flow windmilling is logged as `rotor_windmilling` plus per-rotor columns through `rotor_7_windmilling`; live status/HUD expose it as `wmill`/`WM`, and offline reports include `max_windmill`. With active braking enabled, windmilling also adds a conservative generator load and records average/per-motor regenerative current as `motor_regen_current_a` and `motor_0_regen_current_a` through `motor_7_regen_current_a`; blackbox summaries print the peak as `motor-regen`, and offline reports include `max_motor_regen`.
Active braking now also exposes a body-frame braking reaction torque in blackbox/offline CSV columns `rotor_active_braking_pitch_torque_nm`, `rotor_active_braking_yaw_torque_nm`, and `rotor_active_braking_roll_torque_nm`; live status/HUD show it as `brakeT`/`BT`, and summaries report its peak so asymmetric throttle chops or damaged-prop deceleration can be tuned without guessing from RPM alone.
Rotor spin acceleration reaction torque is also split into `rotor_acceleration_reaction_pitch_torque_nm`, `rotor_acceleration_reaction_yaw_torque_nm`, and `rotor_acceleration_reaction_roll_torque_nm`; live status/HUD expose it as `accelT`/`AT`, blackbox summaries print `accelT`, and offline reports include `max_rotor_accel_torque` so throttle-punch spin-up moments can be separated from braking and gyroscopic coupling.
Rotor gyroscopic/precession reaction torque is split out into `rotor_gyroscopic_pitch_torque_nm`, `rotor_gyroscopic_yaw_torque_nm`, and `rotor_gyroscopic_roll_torque_nm` while the existing `rotor_inertia_*` columns keep the total inertia-plus-gyro torque; live status/HUD expose the split as `gyroT`/`GT`, blackbox summaries print `gyroT`, and offline reports include `max_rotor_gyro_torque`.
Rotor flapping force-arm torque is logged in `rotor_flapping_pitch_torque_nm`, `rotor_flapping_yaw_torque_nm`, and `rotor_flapping_roll_torque_nm`; live status/HUD expose it as `flapT`/`FT`, blackbox summaries print `flapT`, and offline reports include `max_flap_torque` so transverse-flow thrust-vector tilt can be separated from wake swirl, blade dissymmetry, and active braking moments. The default racing-quad flapping response is scaled against the low-speed STARMAC II Fig. 9 stiff-blade/measured deflection band, treating that data as an order-of-magnitude rotor-dynamics anchor rather than a direct 5-inch prop fit.
Rotor blade count and motor pole pairs are now part of each `RotorSpec`; 5-inch FPV-style `racingQuad` and `cinewhoop` presets use three-blade blade-pass ripple and gyro notch frequencies while the APDrone preset explicitly carries a 7-pole-pair motor setting for Betaflight eRPM conversion and commutation ripple. The `racingQuad` rotor inertia now comes from the same `I = c*m*R^2` uniform-blade proxy used by the validation notes, with a 4.0 g 5-inch tri-blade mass anchor, so spool response, acceleration reaction torque, active-braking torque, shaft power, and gyroscopic torque are tied to a plausible prop inertia scale instead of a loose response constant. Large lift, hex, octo, and coaxial X8 presets use a 10x5/10x4.5-like `P/D=0.50` instead of inheriting the 5-inch `0.85` default, so pitch speed, blade angle of attack, stall, reverse-flow, and windmilling telemetry follow utility-lift prop geometry. The live `/fpvdrone tune rotor_blade_count`, `/fpvdrone tune rotor_pitch_to_diameter`, and `/fpvdrone tune motor_pole_pairs` commands persist with the drone entity, and blackbox/offline CSV output includes `tune_rotor_blade_count`, `tune_rotor_pitch_to_diameter`, `tune_rotor_pitch_angle_70r_deg`, and `tune_motor_pole_pairs` beside blade pitch for log-to-prop-spectrum and eRPM validation.
Blade-pass thrust-ripple intensity is surfaced live as `bpass`/`BP`, printed in blackbox summaries as `bpass`, and included in offline recorder summaries as `max_bpass`, so prop count, prop pitch, RPM, and filter tuning can be checked without opening the CSV.
Damaged-prop vibration is split out from aggregate rotor vibration as `rotor_damage_vibration` plus per-rotor `rotor_0_damage_vibration` through `rotor_7_damage_vibration`; blackbox summaries print it as `dvib`, and offline recorder summaries include `max_damage_vib`, so bent or chipped props can be diagnosed separately from propwash, stall, and blade-pass vibration. Offline summaries also run a single-rotor severe-fault hover audit that compares runtime gyro/accelerometer dynamic RMS ratios against UAV Realistic Fault and PADRE prop-fault dataset ratios, checking the full damage-to-sensor path instead of only the static rotor-health curve.
Battery sag, bus ripple, regenerative voltage spikes, and motor current ripple now feed an `imu_supply_noise` telemetry channel. It is applied to gyro, accelerometer, and barometer noise, appears live as `pwr`/`P`, and blackbox/offline summaries report `imuP`/`max_imu_power_noise` so electrical-noise-driven sensor trouble can be separated from mechanical vibration. The APDrone preset uses Mendeley Blackbox zero-throttle low-motion P90 vector RMS values for configured gyro/accelerometer noise, while its quiet barometer floor is fitted to the APDrone low-motion detrended baroAlt P50 and reported against strict-static, low-motion P50/P90, and DPS310 pressure-noise anchors. The bus ripple source is also exposed live as `ripple`/`Rp` and raises `bus-ripple` before it is hidden behind the aggregate IMU power-noise channel.
The runtime LiPo model also exposes SOC-, temperature-, cycle-aging-, and high-current-polarization-adjusted pack resistance as `battery_effective_resistance_ohm` in blackbox/offline CSV output. Its open-circuit voltage follows a CALCE low-current OCV-shaped lookup over each preset's configured usable empty/full pack-voltage window, its SOC curve keeps ordinary `R0` rise close to open LiPo ECM data while reserving the steepest penalty for the near-empty knee, its cold-temperature ESR rise is shaped against RC LiPo 71F-to-42F resistance-ratio measurements, and the separate `battery_resistance_aging_scale`, `battery_soc_resistance_scale`, `battery_temp_resistance_scale`, `battery_polarization_resistance_scale`, `battery_slow_polarization_v`, and `battery_equivalent_cycles` columns track slow cycle growth, near-empty and cold-pack ESR rise, reversible punch-out resistance growth, and CALCE DST-style long voltage recovery without changing each preset's high-C baseline ESR. The `battery_20pct_sag_current_a` and `battery_20pct_sag_current_margin` columns project the current that would create a 20% configured pack-voltage sag from the current effective ESR, so high-C pack tuning can be compared directly with each preset's max-current limit. `/fpvdrone status` prints effective IR as `ir` in mOhm, the SOC/temperature/polarization split as `irx`, and `sag20` current and margin; blackbox summaries report peak `ir` and `irx`, and offline reports include `max_ir` plus `max_irx`, so voltage sag can be tuned against measured pack internal resistance instead of inferred only after the fact.
Blackbox/offline CSV also exports MQTB HQ v1s 5x4x3 thrust-equivalent current and electrical-power audit columns as `mqtb_hq5x4x3_current_a`, `mqtb_hq5x4x3_power_w`, `mqtb_hq5x4x3_current_ratio`, and `mqtb_hq5x4x3_current_residual_a`, so the simulated motor-current model can be compared against a representative 5-inch FPV test-stand curve without replacing the runtime battery, ESC, braking, and thermal models. The same curve is used as a one-sided steady-current anchor for 5-inch three-blade rotors when the runtime base propulsion-current estimate is above the bench curve, while dynamic ESC desync, voltage-headroom loss, active braking, and ripple currents remain additive. Offline CLI summaries also print a Tyto Robotics x3nm static-powertrain audit comparing each preset's configured max rotor thrust, thrust coefficient, and max RPM against a public Five33 2207 / Azure VanoverProp 6S static test; this is a source-calibration sanity check for FPV-scale thrust constants, not a direct APDrone 2507/Foxeer 5145 proof. For the APDrone preset specifically, each rotor carries the YSIDO 2507 motor PDF winding resistance of `0.0586 ohm`, the clean steady-current estimate is anchored against the APDrone PDF 5045/6S current fit, and offline CSV exports `apdrone_pdf5045_current_a`, `apdrone_pdf5045_power_w`, `apdrone_pdf5045_current_ratio`, and `apdrone_pdf5045_current_residual_a` beside the MQTB columns. Offline summaries also print APDrone motor PDF and Foxeer Donut 5145 prop audits comparing configured thrust, KV-derived RPM, thrust coefficient, reaction-torque ratio, per-motor current margin, and winding resistance against the PDF, Betaflight KV, and public Donut thrust-image anchors. A separate Tyto static yaw-torque audit compares `rotor_yaw_torque` against lower-Q/T Azure VanoverProp and higher-Q/T Gemfan 5040 public bench fits, giving reaction-torque tuning a measured window instead of forcing a single prop sample onto every 5-inch airframe.
Temperature-adjusted motor winding resistance is also surfaced live as `mR`, logged as `motor_winding_resistance_scale`, summarized as peak `mR`/`max_motor_winding_r`, and raises `hot-winding` when hot copper resistance becomes a tuning risk separate from ESC thermal limiting or voltage headroom.
Airframe drag is split between true low-speed linear damping `F=-cv` and body-axis quadratic coefficients `F_i=-c_i v_i |v_i|`; the default racing quad is calibrated near the IMAV 5-inch drag scale at 10 m/s and its passive 20->5 m/s coastdown stays in the same order as the IMAV mass-fit reference. The offline recorder prints that coastdown ratio, the X/Z body-drag coefficients that would match the IMAV target with the current linear damping, a base-drag level-flight envelope for AI-IO/RATM/UZH FPV speed anchors, and a RATM 500 Hz high-speed audit that recomputes the 36-flight `>=21 m/s` packet against the current drag, thrust, RPM, ESC-current, and prop-radius model, while separated-flow, rotor-wash, and near-ground cushion terms remain separate telemetry channels.
Airframe lift now includes both ordinary body angle-of-attack/sideslip lift and a conservative powered rotor-wash lift component along the rotor axis, so a loaded hover can produce a small prop-on body lift even with zero freestream airspeed. The powered component is scaled by current total thrust, induced velocity, projected body-drag area, and air-density ratio, bounded as a small fraction of vehicle weight, and treated as a NASA powered-lift order anchor rather than a direct large-prop force fit.
Ground-effect thrust uses a ZJU FAST-Lab-style `g2 / (h^2 + g1)` height response scaled by each preset's ground-effect strength and faded at the configured effect height, so the default racing quad sits near the ZJU additional-thrust band at `h/R = 1..4` without treating the separate near-ground cushion drag as a rotor-drag coefficient fit.
Near-ground attitude coupling also uses the ZJU `M_G(h)` leveling-torque shape, normalized to its measured peak and scaled by weight, rotor radius, and configured ground-effect boost. The resulting body-frame pitch/roll restoring torque includes finite-response pressure lag, crossflow fade, and rate damping, is logged in blackbox/offline CSV as `ground_effect_leveling_pitch_torque_nm`, `ground_effect_leveling_yaw_torque_nm`, `ground_effect_leveling_roll_torque_nm`, and `ground_effect_leveling_torque_nm`, and is surfaced live as `glev`/`GL` with summary peaks in blackbox/offline reports.
Rotor side-flow blockage near walls now blends directional samples with the analytic circle-segment area of a rotor disk cut by a flat wall, so single-wall, corner, and tunnel-like Minecraft geometry produce different obstruction intensity and wall-attraction direction. Near-contact thrust loss is capped separately from the sidewall dirty-air, cooling, vibration, and wall-effect force paths, keeping sidewall total lift changes in the small-effect range while preserving the handling texture of wall skims.
Vortex-ring-state mean thrust loss is calibrated against the small-prop reference band around `1.2..1.3 vi`, so a fully loaded vertical descent can lose roughly one third of clean thrust before the separate low-frequency VRS buffet adds Shetty/Selig-style unsteady thrust and lateral-force texture with a logged envelope below the reported `+/-30%` upper-bound fluctuation. The buffet phase rate now follows a Stack/Leishman-style `20..50` rotor-revolution interval, keeping VRS thrust texture slow relative to blade-pass vibration while still scaling with actual rotor RPM. The normalized descent envelope now ramps up toward that peak band instead of flattening at early entry, while the buffet amplitude follows a digitized early-entry/peak/deep-descent envelope before fading through high-descent clean-air exit and crossflow escape.

To compare airframe presets or choose a custom output file:

```powershell
.\gradlew.bat :drone-sim-core:offlineFlight -Ppreset=apdrone
.\gradlew.bat :drone-sim-core:offlineFlight -Ppreset=cinewhoop
.\gradlew.bat :drone-sim-core:offlineFlight -Ppreset=heavy_lift -Poutput=C:\temp\heavy_lift.csv
.\gradlew.bat :drone-sim-core:offlineFlight -Ppreset=hex_lift
.\gradlew.bat :drone-sim-core:offlineFlight -Ppreset=octo_lift
.\gradlew.bat :drone-sim-core:offlineFlight -Ppreset=coaxial_x8
```

## 原型控制

1. 从 FPV Dronecraft 创造物品栏拿到 `FPV Drone Controller`。
2. 手持遥控器右键可生成并绑定无人机，也可以执行 `/fpvdrone spawn` 生成并绑定默认 `racing_quad`。
3. 飞行时手持遥控器；没有实体遥控器时，按 `V` 可启用最近已绑定无人机的虚拟遥控器。
4. 按 `R` 解锁或上锁。解锁前需要低油门、摇杆回中，或使用 Mode 2 底角手势。
5. `PageUp` / `PageDown` 调整键盘油门。
6. 方向键控制俯仰/横滚，`Z` / `X` 控制偏航。
7. 按 `B` 切换 FPV 视角和目视飞行，按 `N` 循环 HUD 精简 / 隐藏 / 完整遥测。
8. 按 `M` 切换 `ANGLE`、`HORIZON`、`ACRO` 飞行模式；默认首飞建议留在 `ANGLE`。
9. 按 `G` 启用/关闭手柄输入，按 `I` 打开遥控器设置，按 `H` 重新加载客户端配置。
10. 右键已有无人机可重新绑定；潜行右键可修复机架和旋翼损伤。

For command-based setup and testing, use:

```text
/fpvdrone spawn
/fpvdrone spawn racing_quad
/fpvdrone spawn apdrone
/fpvdrone spawn cinewhoop
/fpvdrone spawn heavy_lift
/fpvdrone spawn hex_lift
/fpvdrone spawn octo_lift
/fpvdrone spawn coaxial_x8
/fpvdrone status
/fpvdrone fault rotor 2 0.25
/fpvdrone fault propstrike 1 0.12
/fpvdrone repair
```

After spawning by command, press `V` to enable client-side virtual control, then `R` to arm. Press `V` again to return normal player movement keys to Minecraft. `/fpvdrone status` prints a one-line flight readiness digest for the nearest bound drone: flight mode, armed/link/failsafe state, receiver frame age/interval/error, processed stick command, speed, contact impact/slip/bounce speed, contact angular impulse, airspeed, airframe lift/sideforce, near-ground cushion drag, rotor-wash slipstream drag, rotor near-wall sidewash/cushion force, barometer altitude/vertical-speed/pressure/error, battery sag/current/regenerative-current/bus-spike/bus-ripple/limit/current-limit foldback, IMU clipping, dynamic D-term low-pass cutoff, frame and rotor health, motor and ESC thermal limiting/cooling, ESC command signal age/interval/error, ESC desync, rotor aerodynamic load, in-plane H-force as `hforce`, prop-surface scrape load, propwash, vortex-ring-state intensity plus VRS thrust-buffet amplitude/force as `vrsbuf`/`vrsF`, effective translational lift, induced inflow velocity and inflow-lag thrust loss as `ind`/`iloss`, rotor advance ratio, UIUC propeller advance ratio J, propeller thrust scale, propeller power scale, reverse-flow fraction, rotor tip Mach, rotor blade angle, blade-element stall, blade-pass ripple as `bpass`, rotor inflow skew, same-frame rotor wake interference, wake-induced thrust loss as `wloss`, coaxial load bias as `coax`, wet prop thrust loss as `wetloss`, wake-swirl velocity and hub torque, rotor active-braking, spin-acceleration, and gyroscopic torque, nearby-drone wake, ceiling effect, per-rotor environment thrust asymmetry, rotor side-flow blockage, water immersion, rain wetness, ambient temperature, rotor stall, vibration, blade coning, rotor flapping tilt and force-arm torque, mixer saturation, raw/effective air-mass wind, gust, wind shear, turbulence/obstacle airflow, blackbox sample count, diagnostic state, and active warnings such as `battery-limit`, `current-limit`, `bus-spike`, `bus-ripple`, `gusty-air`, `contact-impact`, `ground-slide`, `contact-tumble`, `prop-scrape`, `wall-effect`, `water-ingress`, `rain-wet`, `wet-thrust-loss`, `inflow-lag`, `rotor-hforce`, `coax-load-bias`, `cold-air`, `hot-air`, `imu-clip`, `thermal-limit`, `hot-winding`, `esc-thermal-limit`, `esc-desync`, `mixer-saturation`, `rotor-stall`, `rotor-coning`, `rotor-flapping`, `rotor-flapping-torque`, `rotor-accel-torque`, `rotor-gyro-torque`, `tip-mach`, `high-advance`, `prop-advance`, `prop-thrust-loss`, `prop-power-loss`, `reverse-flow`, `blade-pass-ripple`, `rotor-wake`, `wake-thrust-loss`, `wake-swirl-torque`, `vrs`, `vrs-buffet`, `propwash`, `baro-disturbed`, `ceiling-effect`, `env-asymmetry`, `rotor-flow-blocked`, or `dirty-air`. `fault rotor` injects direct single-rotor efficiency loss without pretending a collision happened. `fault propstrike` injects a prop-strike event as if that rotor hit an obstacle, so HUD/status prop-strike counters and blackbox prop-strike columns update. `repair` resets frame, rotor health, and prop-strike telemetry.

Status output also includes active-braking reaction torque as `brakeT`, spin-acceleration reaction torque as `accelT`, gyroscopic/precession torque as `gyroT`, and rotor flapping force-arm torque as `flapT`, raising `active-brake-torque`, `rotor-accel-torque`, `rotor-gyro-torque`, or `rotor-flapping-torque` when those body moments become handling risks.
It also includes IMU supply-noise intensity as `pwr` and raises `imu-power-noise` when battery ripple or regenerative spikes are large enough to disturb the sensor model.

This first prototype runs the authoritative physics on the server at 200 Hz by doing ten 5 ms substeps per Minecraft tick.
The in-game blackbox records the substep count, physics step duration, and resulting physics rate for each sampled entity tick so diagnostic logs can prove which integration loop generated the flight data.
Controller packets pass through a simulated RC link before the flight controller: receiver frame-rate holding, channel-resolution quantization, command latency, stick smoothing, last-valid-frame hold during short packet loss, and a configurable failsafe cut after sustained link loss. After the mixer and ESC curve/slew/deadband stage, motor commands also pass through a separate ESC signal model with command-frame holding, per-motor sub-frame phase staggering, and output-resolution quantization, so low-rate PWM-style and high-rate digital ESC behavior can be compared. Default presets now use DShot600's 2000-step throttle semantics while still treating the 400 Hz command loop as ESC scheduling rather than raw wire speed; blackbox/offline logs include DShot bitrate, raw-frame duration, wire utilization, and command-interval/raw-frame ratio columns for protocol-timing audits. The held ESC command then drives a millisecond-scale electrical-output lag model whose time constant follows protocol frame timing, active braking, voltage-headroom stress, current ripple, and desync; motor target RPM, current draw, thermal rise, bus ripple, and IMU supply-noise now use this electrical output while CSV logs keep both `esc_output` and `esc_electrical_output` plus per-ESC lag error columns through `esc_7_electrical_error`. Active braking applies regenerative current and reaction torque while bounding per-step negative motor-RPM slew against Betaflight blackbox-scale response windows, so throttle chops avoid idealized instant braking spikes while still spinning down faster than free coasting. Gyro motor-notch and blade-pass notch tracking now follow bidirectional DShot-style `eRPM/100` RPM telemetry sampled on the ESC frame cadence, including low-speed invalid eInterval frames plus desync/electrical-noise frame dropouts that hold stale RPM and lower validity, so filter centers can lag true motor RPM or report invalid eInterval during stalled, near-stalled, or electrically noisy conditions instead of reading ideal physics truth. Gyro vibration now sums per-motor synchronous and blade-pass phases instead of a single averaged tone, and blackbox/offline logs include `gyro_notch_spread_hz` plus `gyro_blade_pass_notch_spread_hz` so asymmetric mixer output or damaged rotors reveal the RPM-filter band spread hidden by the average notch center. Rotor forward-flow thrust rolloff converts the simulator advance ratio to UIUC propeller `J = pi * mu`, logs it as `rotor_prop_advance_ratio_j` with per-rotor columns, exports the CT rolloff as `rotor_prop_thrust_scale`, and models the same-RPM propeller power coefficient as `rotor_prop_power_scale` so torque per thrust rises around `J = 0.45` like public 5-inch prop data instead of being hidden by translational-lift boost. Offline summaries also print an APC high-J axial-propeller audit against selected 5-inch and 5.1-inch prop files, keeping axial CT/CP trend checks separate from edgewise high-mu retreating-blade-stall behavior, plus a prop geometry audit that compares the selected preset's diameter, pitch, 70%R geometric pitch angle, chord/R proxy, and pitch speed against official HQ/Gemfan product rows and UIUC station geometry. They also print a precipitation/water audit that cross-checks NWS rain-rate water flux, ICAS 2020 heavy-rain CT loss, current wet-prop thrust/load/vibration formulas, hot moist-air density relief, and the intentionally severe water-immersion drag path, plus a wind/gust audit that keeps low-altitude Dryden turbulence, deterministic dirty-air burble, and ICAS hover-gust rotor CT response in separate calibration lanes. Axial gust thrust response is logged as `rotor_axial_gust_thrust_scale` with per-rotor columns through `rotor_7_axial_gust_thrust_scale`, separating direct upwash/downwash CT perturbation from horizontal gust kinematics. The residual post-peak washout beyond the public 5-inch fit range now uses the same `J` scale, so normal `J=0.45..0.65` validation points and extreme-speed extrapolation no longer mix two advance-ratio conventions.
Offline and in-game CSV logs keep mechanical `*_rpm` columns and also emit Betaflight-style telemetry `*_erpm100`, `*_einterval_us`, and `*_rpm_telemetry_valid` columns using each rotor's configurable motor pole-pair count; invalid telemetry writes `65535` in eInterval-style fields while the SI-friendly mechanical RPM values remain available. The configured value is exported as `tune_motor_pole_pairs`, defaults to 7.0, and can be changed live with `/fpvdrone tune motor_pole_pairs`.
Offline recorder summaries also print `max_erpm100`, `min_eint`, `max_rpm_valid`, `notch`, `notch_spread`, `hnotch`, `bpass_notch`, and `bpass_spread`, so expected RPM-filter and blade-pass bands can be checked from automated runs without opening the CSV. The summary also prints an AI-IO rotor-speed audit that treats AI-IO `rotor_spd` as MAVROS ESCStatus mechanical RPM, comparing the extracted 29147 RPM test-set maximum with the preset's configured max RPM and showing that a three-blade blade-pass line near 1457 Hz sits far above the AI-IO 100 Hz HDF5 telemetry cadence. A strict low-dynamic AI-IO subset is reported as `low_dyn`; its 13642 RPM mean is a hover-like slow-flight sanity check that lands at about 1.05x the `racing_quad` configured hover RPM, while still being tracked separately from the full-test maximum RPM envelope. The same summary includes an APDrone urban motor-RPM audit that converts logged Betaflight eRPM/100 with the APDrone 14-pole header, compares normalized motor commands to mechanical RPM, reports effective KV, fitted command/RPM curves, and the urban throttle P50/P95 project-RPM versus decoded-RPM mismatch, and checks blade-pass frequency against the configured max RPM. A motor response dynamics audit then cross-checks RotorS/PX4 actuator lag, Betaflight PR12562 50 ms RPM slew, and APDrone command/RPM lag against the selected preset's motor tau, active-braking tau, ESC frame interval, and max-RPM slew. It also includes an APDrone setpoint-to-gyro control-response audit with roll/pitch/yaw Blackbox correlation lag percentiles, configured control plus RC latency, RC/ESC frame intervals, P50 lag ratios, correlation, gain, and MAE anchors, plus a Betaflight Actual rate-envelope audit that compares center sensitivity, max setpoint, 1998 deg/s rate limit, and stick 25/50/75% samples against the selected APDrone logs.

When the drone is armed and you are holding the controller, the client switches into an FPV camera and renders a compact flight HUD with flight mode, throttle, RC link state, processed flight-controller command, rate setpoints, gyro-measured rates, IMU clipping, PID output torque, PID attenuation, dynamic D-term low-pass cutoff, I-term relax, anti-gravity boost, estimated attitude, estimator error, accelerometer trust, average and per-motor output, average motor RPM, per-rotor thrust, battery voltage, state of charge, net current draw, active-braking regenerative current, bus voltage spike and ripple, current-limit foldback, low-battery power limiting, motor temperature, ESC temperature, motor/ESC thermal thrust limiting, rotor aerodynamic load, speed, contact impact/slip/bounce speed and angular impulse, altitude, synced barometer state, attitude, damage, rotor vibration, prop-surface scrape load, airspeed, angle of attack, sideslip, effective translational lift, induced inflow velocity and inflow-lag loss as `Iv`/`IL`, rotor advance ratio, UIUC propeller advance ratio J as `J`, propeller thrust scale as `PT`, propeller power scale as `PW`, reverse-flow fraction as `RF`, rotor tip Mach, blade angle/stall, blade-pass ripple as `BP`, rotor inflow skew, same-frame rotor wake interference, wake-induced thrust loss as `WL`, coaxial load bias as `CX`, wet prop thrust loss as `WW`, wake-swirl velocity and hub torque, mixer saturation, propwash intensity, vortex-ring-state intensity, rotor stall intensity, rotor flapping tilt and force-arm torque, airframe lift/sideforce force, near-ground cushion drag, rotor-wash slipstream drag, rotor in-plane H-force as `H`, rotor near-wall sidewash/cushion force, nearby-drone wake intensity, ceiling-effect intensity, turbulence intensity, obstacle proximity, water immersion, rain wetness, ambient temperature, ground-effect multiplier, raw/effective wind speed, gust speed, and wind-shear telemetry. Multi-rotor HUD motor bars, thrust readout, and rotor-health summary use the synced rotor count up to eight rotors, so `hex_lift` shows all six motor outputs instead of only the first four. The entity renderer also consumes a synced rotor-layout string derived from the active `RotorSpec` geometry, including vertical rotor offsets for stacked coaxial layouts, so six-rotor and future larger frames render their actual rotor positions instead of a fixed quad shell. The collision footprint is recomputed from the active rotor geometry and refreshed when tuning or presets change, so a larger six-rotor frame no longer keeps the compact racing-quad body box. The rendered rotor blades are attached to the airframe transform and spin from the synced per-motor RPM telemetry, including opposite spin directions from the configured rotor layout.
The HUD torque line includes active-braking reaction torque as `BT`, spin-acceleration reaction torque as `AT`, gyroscopic/precession torque as `GT`, and flapping force-arm torque as `FT`, next to blade-dissymmetry and wake-swirl hub torque.
The gyro HUD line includes IMU supply-noise intensity as `P`, next to notch and clipping telemetry.
The FPV camera uses the drone body attitude, configurable camera tilt/offset, a configurable wide FPV field of view, speed/throttle-based dynamic FOV stretch, video-link pose latency, rotor/propwash vibration, and rolling-shutter jello driven by motor RPM, configured rotor blade count, and rotor roughness. The HUD shows whether the current command source is keyboard or gamepad, and keyboard input remains the fallback if no compatible joystick is detected.
The HUD also shows accumulated crash damage, per-rotor health, cumulative prop-strike count, and the last struck rotor/severity. Contact response separates blocked-axis impact, tangential slip, rebound, and angular impulse before applying damage, so a hard vertical landing, a wall bounce, and a fast scraping slide no longer look identical to the damage model or blackbox. Minecraft block material is sampled at the contact patch: ice keeps slides long, slime boosts rebound, honey/mud/soul-sand surfaces brake harder, hard stone/concrete raises friction and scrape load, wood is less abrasive, metal slides more but punishes props, wool/hay damps rebound, and abrasive cactus contact increases prop scrape damage. The active material multipliers are logged as `contact_surface_friction`, `contact_surface_restitution`, and `contact_surface_scrape`, making material-tuned collisions and prop scrapes auditable from CSV rows and blackbox summaries. Off-center contact uses the current attitude, rotor-arm geometry, and configured body inertia to inject a short angular-velocity kick, letting glancing wall hits and tilted landings tumble the frame instead of only changing linear velocity. Hard impacts reduce frame integrity and individual rotor efficiency; a badly damaged drone will lose thrust authority or disarm until repaired. Spinning prop disks are sampled independently against nearby block collision, so clipping a gate, wall edge, floor, or ceiling can damage one rotor before the whole frame hits. While a disk keeps scraping a surface, the physics core applies a decaying surface-scrape load that drags that motor RPM down, raises ESC desync risk, increases current/load, and adds vibration without counting a new damage event every tick. The damaged rotor then loses thrust authority and behaves like a bent prop: rotor health is folded into effective 1x RPM imbalance and damage-profile mechanical loss, adding rotating lateral force, commutation ripple, current ripple, and vibration through the same physics path used by healthy-prop imbalance. `/fpvdrone status` reports the same prop-strike count and last struck rotor/severity for command-based checks, and `/fpvdrone fault rotor` lets you inject that asymmetric damage on demand for tuning.

## Scripted In-Game Diagnostic

After binding a drone, you can run a repeatable server-side diagnostic flight in a clear open area:

```text
/fpvdrone diagnostic start
/fpvdrone diagnostic start 24
/fpvdrone diagnostic record
/fpvdrone diagnostic record 24
/fpvdrone diagnostic status
/fpvdrone diagnostic stop
```

The optional `seconds` argument accepts `6..60`; the default is 16 seconds. `start` clears that drone's blackbox and runs the scripted profile. `record` does the same but automatically saves a CSV under `fpvdrone-blackbox` when the diagnostic finishes. The diagnostic temporarily overrides player stick packets with a deterministic Horizon-mode command profile. The profile uses the active airframe's `hoverThrottle()` plus a small altitude-hold outer loop, then runs takeoff, roll step, pitch step, yaw step, throttle punch, descent, settle, and disarm phases. The entity still moves through the normal 200 Hz physics path, so wind, air-mass inertia, gusts, wind shear, ambient temperature, ground effect, ceiling effect, obstacle turbulence, water immersion, rain wetness, propwash, vortex ring state, rotor blade stall, ESC slew, ESC command frame holding/quantization, active braking, battery sag/regeneration/bus spike, thermal limits, contact response/collisions, HUD sync, and blackbox telemetry are all exercised in-game rather than in a separate fake harness.

After the script finishes, use `/fpvdrone blackbox status` and `/fpvdrone blackbox save` to inspect or export the resulting CSV.

## Repeatable Wind Tunnel Conditions

For controlled tuning runs, the nearest bound drone can override selected parts of the Minecraft environment while still using the normal entity physics, obstacle wind shadow, ground effect, ceiling effect, and blackbox path:

```text
/fpvdrone environment status
/fpvdrone environment wind 6 0 -2
/fpvdrone environment turbulence 0.35
/fpvdrone environment density 0.85
/fpvdrone diagnostic record 20
/fpvdrone blackbox summary
/fpvdrone environment clear
```

`wind` sets a fixed world-space wind vector in meters per second before near-ground boundary-layer attenuation, near-obstacle wind shadow, and dirty-air turbulence are sampled. The physics core does not apply that vector as a perfectly rigid field: near the ground it reduces horizontal wind through a surface boundary-layer profile, filters the result through a finite-response air-mass model, then adds reproducible low-altitude Dryden colored-noise gusts with transverse lead-lag shaping, localized fast burble for obstacle/wake/surface dirty air, wind-shear telemetry, and body disturbance torque from turbulence, ground-layer shear, obstacle proximity, nearby-drone wake, and ceiling effect. `turbulence` sets the baseline atmospheric turbulence intensity from `0.0` to `1.5`; ground-layer shear, obstacle, nearby-drone wake, and near-ceiling turbulence are still added on top. `density` sets the air-density ratio from `0.35` to `1.35`, which changes thrust scaling, drag, induced inflow, and thermal cooling. Natural density is also shaped by altitude and ambient temperature sampled from biome baseline, weather, time of day, and height; rain/thunder wetness applies an additional hot-wet-air density correction before thrust, drag, induced inflow, dynamic-pressure disturbance, and cooling calculations use it. Minecraft water immersion is always sampled naturally from the drone body and rotor positions, while rain/thunder exposure is sampled across the body and each prop disk from sky visibility and current weather to produce average and per-rotor rain wetness; both remain active during wind/density override runs, so water-ingress and rain-wet behavior remain tied to the world. Blackbox and offline CSV exports include `rotor_wet_thrust_scale`, per-rotor `rotor_N_wet_thrust_scale`, and per-rotor `rotor_N_precipitation_wetness` columns for the combined water-immersion and precipitation prop-thrust loss. Rotor ground effect and ceiling effect are sampled across each prop disk instead of only at the rotor hub, so platform edges and partial ceiling proximity produce continuous per-rotor thrust multipliers. Each channel can be cleared independently with `wind clear`, `turbulence clear`, or `density clear`; `environment clear` restores natural Minecraft weather, altitude/temperature-based density, wet-air effective density, ground effect, ceiling effect, and obstacle airflow. The override is saved on the drone entity so repeated blackbox runs can be compared after reloading the world.

Dryden gust strength follows the atmospheric turbulence channel, while obstacle, nearby-drone wake, and surface-boundary dirty-air boosts remain in the localized burble channel exposed as `wind_burble_speed_mps`.

## 无头服务端自测

不用打开图形客户端也可以跑 Minecraft dedicated server 自测。完整 6DOF 物理层使用：

```powershell
.\gradlew.bat :fabric-mod:runServerSelfTest
```

默认玩家可玩层使用：

```powershell
.\gradlew.bat :fabric-mod:runPlayableServerSelfTest
```

首次启动可能会生成 `fabric-mod/run/eula.txt` 并停止；确认 Mojang EULA 后把它设为 `eula=true` 再重跑。自测会在主世界生成 `racing_quad`，通过普通服务端实体 tick 路径执行同一套诊断脚本，写出 `server-selftest-simulation-*.csv/json` 或 `server-selftest-playable-*.csv/json` 到 `fabric-mod/run/fpvdrone-selftest`，然后自动停止服务器。Gradle 验证器会检查本次报告的 `passed: true`、样本数量、CSV 列对齐、`flight_model` 行值、爬升/移动能力和电机输出；`simulation` 还会额外检查电池 sag、电机/RPM telemetry、gyro notch 和空气动力遥测，`playable` 则聚焦默认玩家手感层是否能稳定起飞和记录可解释的电机转速。修改时长可以加 `-PfpvdroneSelfTestSeconds=20`；复查已有报告可用 `.\gradlew.bat :fabric-mod:validateServerSelfTestReport` 或 `-PfpvdroneSelfTestReport=C:\path\server-selftest.json`。The JSON report includes peak in-plane rotor H-force as `max_rotor_in_plane_drag_force_n`, active X8 coaxial allocation telemetry as `max_rotor_coaxial_load_bias`, `max_rotor_coaxial_load_bias_target`, `max_rotor_coaxial_load_bias_clipping`, `max_rotor_coaxial_allocation_load`, `max_rotor_coaxial_allocation_ratio`, `max_rotor_coaxial_allocation_mech_gain_pct`, `max_rotor_coaxial_allocation_elec_gain_pct`, and `max_rotor_coaxial_allocation_uncertainty_pct`, peak wake-swirl hub torque as `max_rotor_wake_swirl_torque_nm`, low-throttle windmilling as `max_rotor_windmilling`, spin-acceleration reaction torque as `max_rotor_acceleration_reaction_torque_nm`, gyroscopic/precession torque as `max_rotor_gyroscopic_torque_nm`, flapping force-arm torque as `max_rotor_flapping_torque_nm`, ground-effect leveling torque as `max_ground_effect_leveling_torque_nm`, peak effective battery resistance as `max_battery_effective_resistance_ohm`, battery resistance split telemetry as `max_battery_soc_resistance_scale`, `max_battery_temp_resistance_scale`, and `max_battery_polarization_resistance_scale`, IMU supply noise as `max_imu_supply_noise`, hot-winding copper resistance rise as `max_motor_winding_resistance_scale`, rotor Mach compressibility thrust loss as `max_rotor_compressibility_loss_percent`, and damaged-prop vibration as `max_rotor_damage_vibration`. Blackbox/offline CSV now splits total `wind_gust_speed_mps` into slow Dryden turbulence (`wind_dryden_speed_mps`) and localized burble/dirty-air (`wind_burble_speed_mps`) telemetry for wind-model tuning. The JSON and CSV validator checks required blackbox columns such as `physics_substeps`, `physics_dt_s`, `physics_rate_hz`, `flight_model`, `flight_mode`, `control_frame_error`, `esc_command_error`, `pid_dterm_lpf_hz`, `pid_integral_relax_pitch`, `pid_integral_relax_yaw`, `pid_integral_relax_roll`, `gyro_blade_pass_notch_hz`, `gyro_blade_pass_notch_attenuation`, `motor_commutation_ripple`, `motor_regen_current_a`, `motor_5_regen_current_a`, `motor_phase_current_a`, `motor_current_ripple_a`, `motor_torque_ripple_nm`, `avg_motor_mechanical_loss_torque_nm`, `motor_electrical_efficiency`, `motor_voltage_headroom`, `motor_winding_resistance_scale`, `motor_5_winding_resistance_scale`, `avg_motor_erpm100`, `motor_5_erpm100`, `avg_motor_einterval_us`, `motor_5_einterval_us`, `avg_motor_rpm_telemetry_valid`, `motor_5_rpm_telemetry_valid`, `avg_motor_target_rpm`, `avg_motor_target_erpm100`, `motor_5_target_erpm100`, `avg_motor_target_einterval_us`, `motor_5_target_einterval_us`, `avg_motor_tracking_error`, `avg_motor_actuator_authority`, `mixer_saturation`, `mixer_yaw_authority`, `mixer_min_axis_authority`, `mixer_low_saturation`, `mixer_high_saturation`, `mixer_low_headroom`, `mixer_high_headroom`, `battery_effective_resistance_ohm`, `battery_bus_ripple_v`, `imu_supply_noise`, `battery_temp_c`, `battery_cooling_factor`, `battery_thermal_limit`, `tune_cg_x_m`, `tune_cg_z_m`, `tune_imu_x_m`, `tune_cp_x_m`, `tune_rotor_outward_cant_deg`, `tune_rotor_imbalance`, `rotor_advance_ratio`, `rotor_prop_advance_ratio_j`, `rotor_5_prop_advance_ratio_j`, `rotor_stall_intensity`, `rotor_damage_vibration`, `rotor_5_damage_vibration`, `rotor_coning`, `rotor_5_coning`, `rotor_arm_flex`, `rotor_surface_scrape`, `rotor_wake_interference`, `rotor_wake_thrust_scale`, `rotor_5_wake_thrust_scale`, `rotor_coaxial_load_bias`, `rotor_5_coaxial_load_bias`, `rotor_coaxial_load_bias_target`, `rotor_coaxial_load_bias_clipping`, `rotor_coaxial_allocation_load`, `rotor_coaxial_allocation_ratio`, `rotor_coaxial_allocation_mech_gain_pct`, `rotor_coaxial_allocation_elec_gain_pct`, `rotor_coaxial_allocation_uncertainty_pct`, `rotor_wet_thrust_scale`, `rotor_5_wet_thrust_scale`, `rotor_in_plane_drag_force_n`, `rotor_5_in_plane_drag_force_n`, `rotor_wake_swirl_mps`, `rotor_5_wake_swirl_mps`, `rotor_windmilling`, `rotor_5_windmilling`, `rotor_wake_swirl_pitch_torque_nm`, `rotor_wake_swirl_yaw_torque_nm`, `rotor_wake_swirl_roll_torque_nm`, `rotor_acceleration_reaction_pitch_torque_nm`, `rotor_acceleration_reaction_yaw_torque_nm`, `rotor_acceleration_reaction_roll_torque_nm`, `rotor_gyroscopic_pitch_torque_nm`, `rotor_gyroscopic_yaw_torque_nm`, `rotor_gyroscopic_roll_torque_nm`, `rotor_flapping_pitch_torque_nm`, `rotor_flapping_yaw_torque_nm`, `rotor_flapping_roll_torque_nm`, `rotor_angular_drag_roll_torque_nm`, `contact_impact_mps`, `contact_slip_mps`, `contact_bounce_mps`, `contact_surface_friction`, `contact_surface_restitution`, `contact_surface_scrape`, `contact_angular_impulse_dps`, `barometer_error_m`, `effective_wind_x_mps`, `wind_gust_speed_mps`, `wind_dryden_speed_mps`, `wind_burble_speed_mps`, `wind_shear_accel_mps2`, `water_immersion`, `precipitation_wetness`, `effective_air_density_ratio`, `ambient_temperature_c`, `rotor_0_water_immersion`, `rotor_5_water_immersion`, `battery_regen_current_a`, `battery_voltage_spike_v`, `max_esc_temp_c`, `esc_thermal_limit`, `tune_esc_command_frame_rate_hz`, `tune_esc_command_resolution_steps`, `tune_rotor_blade_pitch_m`, `tune_rotor_pitch_to_diameter`, `tune_rotor_pitch_angle_70r_deg`, `tune_rotor_blade_count`, `tune_motor_pole_pairs`, `rotor_blade_aoa_deg`, `rotor_5_blade_element_stall`, `rotor_blade_dissymmetry`, `rotor_5_blade_dissymmetry`, `rotor_blade_pass_ripple`, `rotor_5_blade_pass_ripple`, `rotor_flapping_tilt_deg`, `rotor_5_flapping_tilt_deg`, and `tune_rotor_stall_loss`.

The core Dryden wind tests lock the low-altitude pole frequencies and longitudinal/lateral/vertical spectral magnitude helpers used by the runtime transverse lead-lag shaper.
The required blackbox column checks also include `rotor_0_precipitation_wetness` and `rotor_5_precipitation_wetness`, so the server smoke test guards both quad and X8 per-rotor rain telemetry.
The server self-test JSON also aggregates rotor inflow and propeller-aero peaks as `max_rotor_induced_velocity_mps`, `max_rotor_inflow_lag_loss_percent`, `max_rotor_dynamic_inflow_tau_s`, `max_rotor_translational_lift`, `max_rotor_propeller_advance_ratio_j`, `max_rotor_propeller_thrust_loss_percent`, `max_rotor_propeller_power_loss_percent`, `max_rotor_reverse_flow`, `max_rotor_low_reynolds_loss`, `max_rotor_blade_pass_ripple`, `max_rotor_damage_vibration`, `max_rotor_wet_thrust_loss_percent`, `max_vortex_ring_thrust_buffet`, and `max_vortex_ring_buffet_force_n`.
The server self-test JSON also aggregates Betaflight-style RPM telemetry and RPM-filter activity as `max_avg_motor_rpm_telemetry_rpm`, `max_motor_5_rpm_telemetry_rpm`, `max_avg_motor_erpm100`, `max_motor_5_erpm100`, `min_avg_motor_einterval_us`, `min_motor_5_einterval_us`, `max_avg_motor_rpm_telemetry_valid`, `max_motor_5_rpm_telemetry_valid`, `max_gyro_notch_hz`, `max_gyro_notch_attenuation`, `max_gyro_notch_spread_hz`, `max_gyro_rpm_harmonic_notch_attenuation`, `max_gyro_blade_pass_notch_hz`, `max_gyro_blade_pass_notch_attenuation`, and `max_gyro_blade_pass_notch_spread_hz`, so a server smoke run now proves the in-game entity path is feeding RPM telemetry into motor-fundamental and blade-pass notch tracking.
The same JSON report now carries airframe drag calibration peaks as `max_airframe_body_drag_n`, `max_linear_damping_drag_n`, `max_airframe_drag_along_flow_n`, `max_airframe_drag_equivalent_linear_k`, `max_airframe_drag_equivalent_cda_m2`, and `max_airframe_drag_imav_ratio`.
The server self-test validator also requires `gyro_notch_hz`, `gyro_notch_attenuation`, `gyro_notch_spread_hz`, `gyro_blade_pass_notch_spread_hz`, `avg_motor_erpm100`, `motor_5_erpm100`, `avg_motor_einterval_us`, `motor_5_einterval_us`, `avg_motor_rpm_telemetry_valid`, `motor_5_rpm_telemetry_valid`, `tune_motor_pole_pairs`, `rotor_dynamic_inflow_tau_s`, `rotor_5_dynamic_inflow_tau_s`, `rotor_prop_thrust_scale`, `rotor_5_prop_thrust_scale`, `rotor_prop_power_scale`, `rotor_5_prop_power_scale`, `rotor_axial_gust_thrust_scale`, `rotor_5_axial_gust_thrust_scale`, `rotor_compressibility_thrust_scale`, `rotor_5_compressibility_thrust_scale`, `rotor_reynolds_number`, `rotor_5_reynolds_number`, `rotor_reynolds_index`, `rotor_5_reynolds_index`, `rotor_low_reynolds_loss`, `rotor_5_low_reynolds_loss`, `rotor_damage_vibration`, `rotor_5_damage_vibration`, `rotor_coning_angle_deg`, `rotor_5_coning_angle_deg`, `rotor_arm_flex_deflection_mm`, `rotor_5_arm_flex_deflection_mm`, `rotor_arm_flex_tilt_deg`, `rotor_5_arm_flex_tilt_deg`, `rotor_coaxial_load_bias_target`, `rotor_coaxial_load_bias_clipping`, `rotor_coaxial_allocation_load`, `rotor_coaxial_allocation_ratio`, `rotor_coaxial_allocation_mech_gain_pct`, `rotor_coaxial_allocation_elec_gain_pct`, and `rotor_coaxial_allocation_uncertainty_pct`, keeping the automated smoke path aligned with blackbox exports.
The validator still covers the IMU offset columns, including `tune_imu_y_m` and `tune_imu_z_m`, alongside the new rotor H-force columns.

## 手柄 / 遥控器设置

客户端第一次启动时会生成 `config/fpvdrone-client.json`。当前默认配置如下：

```json
{
  "gamepadEnabled": true,
  "armButton": -1,
  "disarmButton": -1,
  "throttleCalibrateButton": -1,
  "throttleCalibrated": true,
  "throttleCalibrationMin": 0.0,
  "throttleCalibrationMax": 1.0,
  "rollAxis": 2,
  "pitchAxis": 3,
  "yawAxis": 0,
  "throttleAxis": 1,
  "rollInverted": false,
  "pitchInverted": true,
  "yawInverted": false,
  "throttleInverted": true,
  "gamepadDeadband": 0.10,
  "gamepadExpo": 1.0,
  "gamepadRollPitchRateScale": 0.42,
  "gamepadYawRateScale": 0.38,
  "gamepadAxisRisePerTick": 0.032,
  "gamepadAxisFallPerTick": 0.32,
  "hudMode": "MINIMAL",
  "cameraTiltDegrees": 16.0,
  "cameraForwardOffsetMeters": 1.20,
  "cameraUpOffsetMeters": 0.72,
  "cameraVibrationScale": 0.08,
  "cameraRollingShutterScale": 0.04,
  "cameraLatencySeconds": 0.012,
  "cameraFovDegrees": 116.0,
  "cameraDynamicFovDegrees": 1.0
}
```

摇杆轴使用中心死区，并重新映射到完整的 `-1..1` 控制范围。油门按行程轴处理，映射到 `0..1`，并带有端点吸附，让真实遥控器的最低油门和最高油门更稳定。

`gamepadExpo`、`gamepadRollPitchRateScale`、`gamepadYawRateScale`、`gamepadAxisRisePerTick`、`gamepadAxisFallPerTick` 调整的是手感层，而不是机体物理。较低的 rate scale 会降低同样摇杆行程下的最大控制权；更高的 expo 会让中位更细腻；较低的 rise speed 会让新指令进入更柔和；较高的 fall speed 会让松杆或反打时更快恢复。默认值偏向稳定首飞，熟悉穿越机手感后可以把倍率逐步提高到接近 `1.0`。

游戏内遥控器设置界面（默认 `I`）提供三档手感预设：

- `Training`：默认档，中位和半杆都更稳，指令进入较慢、松杆回中更快，适合首飞和找手感。
- `Sport`：响应更快，但保留一定中位柔和度。
- `Acro`：完整俯仰/横滚/偏航控制权，expo 更低、输入限速更快，更接近 FPV 飞行。

注意：这里的 `Acro/穿越` 是遥控手感曲线，只负责摇杆塑形；真正取消自动回正需要在飞行中按 `M` 切换到 `ACRO` 飞行模式。`ANGLE` 和 `HORIZON` 会继续按自稳/运动模式回正，这是刻意保留的。

如果手动编辑 JSON 后参数不匹配任何预设，设置界面会显示 `Custom`；再次点击 `Feel` 会回到 `Training`。

通用 RC 遥控器的解锁/上锁与油门校准：

- 将 `armButton` 和 `disarmButton` 设为遥控器 HID/手柄按钮编号。
- 将 `throttleCalibrateButton` 设为一个专门用于校准的按钮。
- 按游戏内 `Calibrate Drone Throttle`（默认 `C`）开始校准：
  - 确保手柄输入已启用且无人机已绑定；
  - 将油门推到最低和最高；
  - 再按一次保存范围。
- 如果更喜欢键盘控制，可直接按 `Arm / Disarm Drone`（默认 `R`）。

FPV 相机支持机体相对倾角、安装偏移、宽 FOV、速度/油门动态 FOV、短延迟缓存等设置，因此可以调出低角度 cinewhoop、较陡竞速机相机或模拟图传延迟的感觉，而不需要改变物理模型。相机震动来自同步电机 RPM、桨损伤震动和桨洗遥测；`cameraVibrationScale` 设为 `0.0` 可以获得更稳定的视角，最高可到 `2.0`。`cameraRollingShutterScale` 提供高转速下的 CMOS 果冻感，`cameraLatencySeconds` 限制在 `0.0..0.20` 秒。修改配置文件后，在游戏内按 `H` 重新加载。

## Blackbox

The server records a five-minute rolling blackbox log for each drone at Minecraft tick rate. Use these commands after a flight:

```text
/fpvdrone blackbox status
/fpvdrone blackbox summary
/fpvdrone blackbox save
/fpvdrone blackbox clear
```

`summary` prints an in-game tuning digest with sample count, authoritative physics loop rate, max speed/airspeed, max contact impact/slip/bounce speed, max contact angular impulse, minimum voltage, max sag/effective-resistance/current/regenerative-current/per-motor-regenerative-current/bus-spike/bus-ripple, battery temperature and thermal limit, minimum current-limit foldback, propwash, vortex-ring-state intensity, induced inflow velocity and inflow-lag thrust loss, effective translational lift, rotor advance ratio, UIUC propeller advance ratio J, minimum propeller power scale, reverse-flow fraction, rotor aerodynamic load, peak rotor in-plane H-force, motor mechanical-loss torque, actuator tracking error and authority, rotor inflow skew, blade lift-dissymmetry hub torque, same-frame rotor wake interference, coaxial load-bias compensation, peak rotor wake-swirl velocity and hub torque, flapping force-arm torque, rotor angular-damping torque, airframe angular-drag torque, airframe lift/sideforce plus separated-flow drag rise/buffeting, split body/linear airframe drag as `bodyD`/`linD`, near-ground cushion drag, rotor-wash slipstream drag, rotor near-wall sidewash/cushion force, max barometer error, max barometer propwash/ground-pressure disturbance, minimum barometer pressure, nearby-drone wake intensity, max local water immersion, max rain wetness, ambient temperature range, max gust speed, max wind-shear acceleration, ceiling-effect multiplier/clearance, environment thrust asymmetry, rotor side-flow blockage, rotor stall intensity, rotor vibration, rotor coning, rotor flapping tilt, rotor arm flex, rotor surface scrape, mixer saturation, split low/high mixer saturation/headroom, and minimum per-axis mixer authority, ESC command error/desync, motor temperature, minimum motor electrical efficiency and voltage headroom, ESC temperature and thermal limit, worst single-rotor health, prop-strike samples/max severity/count, altitude, link loss, receiver frame age/error, failsafe samples, and collision samples. `save` writes a CSV file under `fpvdrone-blackbox` in the server directory. The CSV includes physics substep count, physics step duration, physics rate, position, speed, contact impact/slip/bounce speed and angular impulse, true and estimated attitude, estimator error and accelerometer trust, true body rates, gyro-measured body rates, thermal/vibration-driven gyro bias, g-sensitive gyro error, and clipping, motor-fundamental and blade-pass dynamic gyro notch frequency/attenuation, true world acceleration, accelerometer specific force plus scale-factor/cross-axis error, bias, and clipping in the drone body frame, pressure-altitude barometer altitude/vertical-speed/pressure/error telemetry with sensor noise split from static-port dynamic-pressure/rapid-rotation bias and propwash/ground-pressure/ceiling-pressure bias, raw player inputs, processed flight-controller commands, receiver frame age/interval/error telemetry, flight mode, link-loss/failsafe state, average motor output, ESC output command frame age/interval/error, ESC desync, ESC temperature/cooling/thermal-limit telemetry, per-motor output, per-motor RPM/target RPM/tracking error/actuator authority/angular acceleration/aerodynamic torque/mechanical-loss torque/shaft power/current/regenerative-current/electrical-efficiency/voltage-headroom, motor temperature, local motor cooling factor, thermal thrust limiting, per-rotor thrust, induced inflow velocity, induced-lag thrust scale, effective translational lift, rotor advance ratio, UIUC propeller advance ratio J, rotor tip Mach, rotor blade angle-of-attack, blade-element stall, blade lift-dissymmetry, rotor aerodynamic load, rotor in-plane H-force, rotor surface scrape, rotor arm-flex intensity, blade lift-dissymmetry hub torque, rotor inflow-skew torque, same-frame rotor wake-interference intensity, coaxial load-bias allocation, crossflow-convected wake sweep, and wake-swirl velocity/hub torque, rotor inertia/gyroscopic torque, rotor angular-drag torque, dynamic flapping tilt/force and flapping force-arm torque, elastic blade-coning intensity, healthy-prop rotor imbalance tuning and its resulting force/torque vector path, rotor stall intensity, rotor vibration, mixer saturation, split low/high mixer saturation and headroom, achieved mixer torque, per-axis mixer authority, PID setpoint/error, self-level target/error/blend telemetry, per-axis P/I/D/feedforward/output torque terms, PID attenuation, dynamic D-term low-pass cutoff, scalar and per-axis I-term relax, anti-gravity boost, battery voltage, open-circuit voltage, ohmic sag, transient sag recovery, effective resistance, active-braking regenerative current, bus voltage spike, bus ripple voltage, battery pack temperature/cooling/thermal-limit telemetry, state of charge, current draw, current-limit foldback, low-battery power limiting, frame health, average and per-rotor health, collision severity, prop-strike event rotor/severity/count/per-rotor severity pulses, weather wind, effective air-mass wind, gust speed, wind-shear acceleration, air-density ratio, wet-air effective density, ambient temperature, average and per-rotor water immersion, precipitation wetness, ground clearance, ground-effect multiplier, ceiling clearance, ceiling-effect multiplier, disk-averaged per-rotor environment thrust multipliers, per-rotor side-flow obstruction, propwash intensity, propwash wake retention, vortex-ring-state intensity, nearby-drone wake intensity, propwash torque, airframe aerodynamic torque, dynamic-pressure airframe angular-drag torque, airframe lift/sideforce, body-drag force, linear damping drag, near-ground cushion drag, rotor-wash slipstream drag, rotor near-wall sidewash/cushion force, body-frame relative air velocity, airspeed, angle of attack, sideslip, turbulence intensity, obstacle proximity, and wind-turbulence torque.
Blackbox summaries and CSV saves expose blade coning as degrees plus arm-flex deflection in millimeters and arm-flex thrust-axis tilt in degrees, using `rotor_coning_angle_deg`, `rotor_arm_flex_deflection_mm`, and `rotor_arm_flex_tilt_deg` with per-rotor columns.
Blackbox summaries include `brakeT` for peak active-braking reaction torque, and CSV saves include the matching body-frame pitch/yaw/roll torque columns.
Blackbox summaries include `accelT` for peak spin-acceleration reaction torque, and CSV saves include the matching `rotor_acceleration_reaction_*_torque_nm` columns.
Blackbox summaries include `gyroT` for peak gyroscopic/precession reaction torque, and CSV saves include the matching `rotor_gyroscopic_*_torque_nm` columns.
Blackbox summaries include `motor-regen`, the peak single-motor regenerative current produced by active braking and windmilling generator load.
Blackbox summaries include `imuP`, the peak IMU supply-noise intensity derived from pack sag, bus ripple, regenerative spikes, and motor current ripple.
Blackbox summaries include `machloss`, the peak rotor tip-Mach compressibility thrust loss, and CSV saves include both average and per-rotor compressibility thrust scale columns.
Blackbox summaries include `lowre`, the peak rotor low-Reynolds loss, and CSV saves include both average and per-rotor low-Reynolds loss columns.
Blackbox summaries include `coax`, `target`, `clip`, `cload`, `cratio`, `cgain`, and `cunc` for the peak balanced upper/lower coaxial load-bias allocation, command-limit clipping, load fraction, runtime command-map ratio, mechanical/electrical gain priors, and allocation-model uncertainty used during X8 tuning.
Blackbox summaries include `hforce`, the peak rotor in-plane drag/H-force seen during the flight.
Blackbox summaries include `dvib`, the peak damaged-prop vibration separated from aggregate rotor vibration, and CSV saves include per-rotor damage-vibration columns.

It also records the active airframe, motor, battery, rotor, rotor-stall, rate, self-level, PID, feedforward, D-term filter, TPA, I-term relax, RC receiver, ESC command signal, and drag tuning values so a CSV can be interpreted later even after you change settings.

## Runtime Tuning

Use presets to switch the currently linked drone to a coherent airframe baseline:

```text
/fpvdrone preset list
/fpvdrone preset racing_quad
/fpvdrone preset apdrone
/fpvdrone preset cinewhoop
/fpvdrone preset heavy_lift
/fpvdrone preset hex_lift
/fpvdrone preset octo_lift
/fpvdrone preset coaxial_x8
```

`racing_quad` is the default fast 5-inch-style acro frame. `apdrone` is a compact APDrone/Mendeley FPV reference frame with measured mass/inertia, 0.095 m motor-center radius, 5.1x4.5 three-blade prop geometry, 4S 1500 mAh pack limits, Betaflight Actual 670 deg/s high-rate setpoint envelope, and DShot600 at the Betaflight dump's 480 Hz motor command anchor. `cinewhoop` is slower, draggier, and more protected-feeling. `heavy_lift` uses larger rotors, higher mass and inertia, slower motor response, and lower rates for stable camera or cargo-style flight. `hex_lift` is a six-rotor X/flat-hex lift frame that exercises the generic rotor-geometry mixer with three clockwise and three counter-clockwise rotors. `octo_lift` is an eight-rotor lift frame with heavier inertia, slower rates, wider collision footprint, and the full rotor 4..7 telemetry extension range active. `coaxial_x8` is a compact four-arm X8 with upper/lower counter-rotating prop pairs, so the lower disks fly in same-frame rotor wake and expose the wake-interference, wake-swirl, and z/D-windowed upper/lower load-bias allocation model with a z/D=0.72 runtime command-map prior in normal gameplay. When a preset changes rotor count, the Fabric entity rebuilds its physics stack around the new airframe while carrying over position, velocity, attitude, and angular velocity. Preset name and tuning values are saved on the drone entity, then the saved preset is restored before tuning overrides are applied, so a saved multi-rotor drone reloads with the same airframe. Blackbox and offline CSV logs keep the legacy rotor 0..3 columns stable and append rotor 4..7 extension columns, including in-plane H-force, wake-interference, coaxial load-bias, wake-swirl velocity, and wake-swirl hub torque telemetry, for six- and eight-rotor tuning.

Use the tuning commands on your currently linked drone to refine a preset:

```text
/fpvdrone tune status
/fpvdrone tune reset
/fpvdrone tune set pitch_p 0.05
/fpvdrone tune set pitch_i 0.016
/fpvdrone tune set pitch_d 0.0008
/fpvdrone tune set feedforward 0.000018
/fpvdrone tune set dterm_lpf 90
/fpvdrone tune set anti_gravity 1.7
/fpvdrone tune set tpa_breakpoint 0.65
/fpvdrone tune set tpa_strength 0.22
/fpvdrone tune set iterm_relax 0.70
/fpvdrone tune set pitch_rate 720
/fpvdrone tune set pitch_expo 0.35
/fpvdrone tune set pitch_super_rate 0.45
/fpvdrone tune set yaw_rate 520
/fpvdrone tune set yaw_super_rate 0.20
/fpvdrone tune set roll_rate 720
/fpvdrone tune set roll_super_rate 0.45
/fpvdrone tune set mass_kg 1.10
/fpvdrone tune set inertia_x 0.012
/fpvdrone tune set cg_z 0.025
/fpvdrone tune set imu_z 0.030
/fpvdrone tune set cp_y 0.020
/fpvdrone tune set motor_tau 0.045
/fpvdrone tune set esc_curve 1.0
/fpvdrone tune set esc_slew 160
/fpvdrone tune set esc_down_slew 360
/fpvdrone tune set esc_deadband 0.018
/fpvdrone tune set motor_brake 0.55
/fpvdrone tune set voltage_compensation 0.85
/fpvdrone tune set esc_frame_rate 400
/fpvdrone tune set esc_resolution 2000
/fpvdrone tune set esc_dshot_bitrate 600
/fpvdrone tune set motor_heat_rate 12.0
/fpvdrone tune set motor_temp_limit 95
/fpvdrone tune set gyro_lpf 120
/fpvdrone tune set accel_lpf 80
/fpvdrone tune set accel_noise 0.22
/fpvdrone tune set attitude_accel_gain 1.8
/fpvdrone tune set attitude_accel_trust 4.0
/fpvdrone tune set control_latency 0.015
/fpvdrone tune set rc_smoothing 0.018
/fpvdrone tune set rc_latency 0.018
/fpvdrone tune set rc_failsafe 0.35
/fpvdrone tune set rc_frame_rate 150
/fpvdrone tune set rc_resolution 2048
/fpvdrone tune set battery_resistance 0.018
/fpvdrone tune set battery_capacity_ah 1.5
/fpvdrone tune set linear_drag 0.18
/fpvdrone tune set body_drag_z 0.20
/fpvdrone tune set rotor_max_thrust 13.5
/fpvdrone tune set rotor_thrust_coefficient 0.000018
/fpvdrone tune set rotor_radius 0.0635
/fpvdrone tune set rotor_blade_pitch 0.108
/fpvdrone tune set rotor_pitch_to_diameter 0.86
/fpvdrone tune set rotor_transverse_lift 0.08
/fpvdrone tune set rotor_axial_loss 0.16
/fpvdrone tune set rotor_disk_drag 0.0028
/fpvdrone tune set rotor_flapping 0.055
/fpvdrone tune set rotor_stall_loss 0.34
/fpvdrone tune set rotor_yaw_torque 0.018
/fpvdrone tune set rotor_outward_cant 0
/fpvdrone tune set rotor_inertia 0.000016
/fpvdrone tune set rotor_inflow_tau 0.035
/fpvdrone tune set rotor_inflow_lag 0.16
/fpvdrone tune set ground_effect_height 0.6
/fpvdrone tune set ground_effect_boost 0.18
/fpvdrone tune set propwash_start 2.2
/fpvdrone tune set propwash_full 7.5
/fpvdrone tune set propwash_torque 0.035
/fpvdrone tune set motor_idle 0.055
/fpvdrone tune set airmode_strength 1.0
```

`rotor_imbalance` models healthy-prop 1x RPM imbalance, while rotor health damage is folded into the same effective imbalance path for bent-prop force, mechanical loss, commutation ripple, current ripple, and vibration.

Available PID keys are `pitch_p`, `pitch_i`, `pitch_d`, `pitch_limit`, `yaw_p`, `yaw_i`, `yaw_d`, `yaw_limit`, `roll_p`, `roll_i`, `roll_d`, and `roll_limit`. Flight-controller assist keys are `feedforward`, `dterm_lpf`, `anti_gravity`, `tpa_breakpoint`, `tpa_strength`, and `iterm_relax`; they apply to all three axes. The D term is applied to gyro measurement derivative rather than target-rate error derivative, so sharp stick setpoint changes do not create D-kick; `feedforward` handles intentional setpoint acceleration instead. The configured `dterm_lpf` is the dynamic filter's high-throttle ceiling: low throttle and low motor RPM lower the effective cutoff, while rotor vibration, blade-pass roughness, rotor stall, and vortex-ring-state buffet add extra foldback. `iterm_relax` ranges from `0.0` to `1.0` and reduces each axis' integrator accumulation during that axis' fast setpoint changes or measured mixer authority loss. Rate values are degrees per second. Expo keys are `pitch_expo`, `yaw_expo`, and `roll_expo`, from `0.0` to `1.0`; higher values soften mid-stick control. Super-rate keys are `pitch_super_rate`, `yaw_super_rate`, and `roll_super_rate`, from `0.0` to `0.95`; they reshape mid-stick response while preserving full-stick maximum rate. Self-level keys are `level_angle` in degrees, `level_gain`, `horizon_start`, and `horizon_end`; they control Angle-mode maximum tilt and the Horizon-mode transition from self-level to Acro authority. Airframe keys are `mass_kg`, `inertia_x`, `inertia_y`, `inertia_z`, `cg_x`, `cg_y`, `cg_z`, `imu_x`, `imu_y`, `imu_z`, `cp_x`, `cp_y`, `cp_z`, and `angular_drag`; the `cg_*` values are meters in body axes and shift the simulated center of mass used by the mixer, rotor local airflow, and force-arm torque, `imu_*` values place the flight-controller sensor package relative to the center of mass so accelerometer specific force includes angular and centripetal lever-arm acceleration, and `cp_*` values place the baseline airframe center of pressure before angle-of-attack, sideslip, and separated-flow migration, so body drag/lift can produce an additional force-arm torque. Motor and battery keys are `motor_tau`, `esc_curve`, `throttle_curve`, `esc_slew`, `esc_down_slew`, `esc_deadband`, `motor_brake`, `voltage_compensation`, `esc_frame_rate`, `esc_resolution`, `esc_dshot_bitrate`, `motor_heat_rate`, `motor_cooling_rate`, `motor_temp_limit`, `motor_temp_cutoff`, `battery_nominal_voltage`, `battery_empty_voltage`, `battery_resistance`, `battery_capacity_ah`, and `battery_max_current`; `motor_tau` is the baseline response time and is dynamically lengthened by low voltage, power limiting, high rotor inertia, high prop aerodynamic load, and back-EMF saturation near the inferred KV no-load speed. `throttle_curve` maps pilot or Betaflight throttle command to direct thrust fraction before mixer torque allocation, so presets can match log-command hover points without changing hardware max thrust. `battery_nominal_voltage`, `battery_max_current`, rotor max RPM, and rotor inertia are also used to infer motor KV, torque constant, and winding resistance for torque-limited spin-up. `esc_slew` limits command rise, `esc_down_slew` limits command fall, `esc_deadband` suppresses tiny commands, `motor_brake` controls active braking strength during spin-down, `esc_frame_rate` controls how often the ESC accepts a new output command, `esc_resolution` controls command quantization steps, `esc_dshot_bitrate` selects generic `0` or DShot `150`/`300`/`600` wire-timing metadata and DShot's 2000 throttle steps, and `battery_max_current` scales load current and drives dynamic over-current thrust foldback. Set ESC frame rate or resolution to `0` to disable that part of the ESC signal model for idealized tests. Flight-controller sensor keys are `gyro_lpf` in Hz, `gyro_noise` in rad/s, `accel_lpf` in Hz, `accel_noise` in m/s^2, and `control_latency` in seconds. Attitude-estimator keys are `attitude_accel_gain` for complementary accelerometer correction strength and `attitude_accel_trust` in m/s^2 for how far measured specific force may drift from gravity before correction is faded out. RC link keys are `rc_smoothing` in seconds, `rc_latency` in seconds, `rc_failsafe` in seconds before a lost link cuts to idle, `rc_frame_rate` in Hz for the receiver command-frame rate, and `rc_resolution` for channel quantization steps; set frame rate or resolution to `0` to disable that part of the receiver model for idealized tests. Drag keys are `linear_drag`, `body_drag_x`, `body_drag_y`, `body_drag_z`, and `rotor_disk_drag`; body drag also scales airframe pitch/yaw/roll moments from angle of attack and sideslip, while `rotor_disk_drag` also damps body angular rates through the spinning prop disk. Rotor keys are `rotor_max_thrust`, `rotor_thrust_coefficient`, `rotor_radius`, `rotor_transverse_lift`, `rotor_axial_loss`, `rotor_flapping` for transverse-flow thrust-vector tilt and the derived force-arm torque telemetry, `rotor_stall_loss` from `0.0` to `0.65` for high-advance-ratio and reverse-axial-flow thrust loss, `rotor_imbalance` from `0.0` to `0.35` for healthy-prop 1x RPM vibration and current ripple, `rotor_yaw_torque` in meters of reaction-torque leverage along the current flapped disk axis, `rotor_outward_cant` in degrees for symmetric inward/outward motor tilt, `rotor_inertia` in kg*m^2 for spin-up reaction torque and gyroscopic coupling along the same disk axis, `rotor_inflow_tau` in seconds, and `rotor_inflow_lag` as a transient thrust-loss coefficient. Ground-effect keys are `ground_effect_height` in meters and `ground_effect_boost` from `0.0` to `0.6`. Propwash keys are `propwash_start` and `propwash_full` in meters per second of descent, plus `propwash_torque` in N-m. Mixer keys are `motor_idle` as a rotor max-thrust fraction and `airmode_strength` from `0.0` to `1.0`. Tuning is saved on the drone entity.

`motor_brake` also drives the live `brakeT`/`BT` and CSV active-braking reaction-torque telemetry, so aggressive ESC braking can be tuned against body torque rather than only RPM decay.

`rotor_inertia` still drives spin-up reaction torque and body-rate gyroscopic/precession coupling; the spin-acceleration component is logged as `accelT`/`AT` plus `rotor_acceleration_reaction_*_torque_nm`, and the gyroscopic component is logged separately as `gyroT`/`GT` plus `rotor_gyroscopic_*_torque_nm` for tuning.

`rotor_blade_pitch` is the prop pitch distance in meters; raising it increases pitch-speed margin and prop load, while lowering it makes the prop unload sooner in high axial climb flow. `rotor_pitch_to_diameter` is an equivalent geometry shortcut for real prop labels: a 5x4.3 prop is about 0.86, and the CSV columns `tune_rotor_pitch_to_diameter` plus `tune_rotor_pitch_angle_70r_deg` expose the derived geometry used by the 70% radius blade-element angle-of-attack model.

Rotor advance ratio, flapping, blade stall, induced inflow, disk drag, same-frame wake checks, and spring-damped arm-flex feedback use each rotor's current axis, so canted motors and flexed motor mounts change both force direction and local airflow.

The core model includes:

- body-frame quadratic drag plus global drag, with angle-of-attack/sideslip aerodynamic lift, sideforce, dynamic separated-flow build-up/recovery, angle-dependent pressure-center migration, near-ground cushion drag, finite-response projected-area rotor-wash slipstream drag plus pressure-center moment, and moments;
- relative-air drag, rotor-induced slipstream drag over the airframe, dynamic-pressure, separated-flow, body-rotation, sideslip weathercock yaw-rate, and finite-response rotor-wash-enhanced airframe angular damping, deterministic weather wind, near-ground boundary-layer wind attenuation and shear, finite-response air-mass inertia, reproducible low-altitude Dryden colored-noise gust vectors with transverse lead-lag shaping, wind-shear acceleration telemetry, wind-speed/weather/ground-proximity turbulence torque, near-obstacle wind shadow and dirty-air turbulence, standard-atmosphere altitude/temperature air density and barometric pressure, rain/thunder wet-air effective-density correction for thrust/drag/induced-flow/cooling, ambient heat-soak effects on battery sag/current limiting plus recirculation-sensitive battery/motor/ESC cooling, Minecraft water immersion sampled from the body and each rotor disk, water drag plus per-rotor air-prop thrust/load/vibration/desync effects during local water ingress, precipitation wetness sampled from exposed rain/thunder that adds wet-prop load, vibration, mild thrust loss, turbulence, and ESC risk without water-drag force, disk-averaged per-rotor ground-effect thrust boost plus low-altitude lateral cushion drag and ZJU-shaped leveling torque, disk-averaged per-rotor near-ceiling suction/thrust boost with added turbulence, finite-response per-rotor surface-pressure lag for ground/ceiling effect build-up and release, per-rotor side-flow obstruction near walls/gates before prop strike, rotor near-wall sidewash/cushion force from obstruction direction and disk pressure, obstruction-induced rotor vibration that feeds gyro/accelerometer noise and dynamic notch telemetry, and body-frame airspeed/angle-of-attack/sideslip telemetry;
- per-rotor local airflow from body velocity and angular velocity, including effective translational lift in clean transverse flow, reduced induced inflow at speed, UIUC-advance-ratio forward-flow propeller CT rolloff, ICAS-scaled hover axial-gust CT gain/loss for near-vertical 10 m/s inflow, airflow-dependent rotor aerodynamic load/unload for motor current and heat, ambient dirty-air load roughness for turbulence/ground-shear/obstacle wake, zero-throttle axial windmilling RPM with prop-disk drag and low-drive windmilling intensity telemetry during fast descents, tunable prop blade pitch and blade count with blade-element angle-of-attack telemetry, pitch-speed axial unloading, blade-element stall load/vibration, advancing/retreating blade lift-dissymmetry load/vibration/thrust loss, configured-blade-count blade-pass thrust ripple that grows under load, stall, dirty air, obstruction, or prop scrape, high-advance blade-stall low-frequency thrust and side-force buffeting, dynamic rotor-stall hysteresis with slower attached-flow recovery, finite-response blade coning thrust loss/load/vibration, low-Reynolds small-prop efficiency loss from density and temperature-dependent air viscosity, and pitch-dependent motor load, temperature-dependent rotor tip Mach telemetry with high-speed compressibility thrust loss/load/reaction-torque rise/vibration, inflow-skew hub torque from high-speed disk-plane flow, transverse-flow lift, rotor-disk drag, spinning-disk angular damping, first-order dynamic transverse-flow flapping tilt/force plus separated flapping force-arm torque telemetry, reaction torque and rotor-inertia torque aligned to the flapped disk axis, high-advance-ratio/reverse-axial-flow blade stall, descending axial-flow thrust loss, and vortex-ring/washout thrust loss plus low-frequency per-rotor thrust and side-force buffeting when the drone descends into retained rotor wake;
- per-rotor dynamic induced inflow, so each prop disk has a finite wake build-up time during throttle punches and rapid unloading;
- rotor-axis-aware retained propwash wake memory, so low-throttle descents along the prop-disk axes can build dirty wake that hits harder on the next punch-out while disk-plane crossflow flushes it away;
- same-frame rotor wake interference, wake-swirl velocity, and wake-swirl hub moment for overlapping or vertically stacked rotors, reducing lower-disk thrust while adding load, vibration, local tangential inflow telemetry, and asymmetric stacked-rotor body torque;
- spring-damped arm and motor-mount flex resonance driven by per-rotor force/torque transients, with short throttle-chop ring-down feeding rotor vibration, IMU noise, dynamic notch telemetry, blackbox/offline CSV traces, and a small load-dependent rotor force-arm/thrust-axis deflection;
- deterministic propwash disturbance torque during retained high-throttle descent through the drone's own wake;
- nearby-drone downwash and wake turbulence, so a drone flying under another active drone receives downward local wind, added turbulence, and dedicated wake telemetry;
- tunable airframe mass, inertia, center-of-mass offset, IMU mounting offset, center-of-pressure offset, ESC response, motor response, battery sag, rotor thrust, and rotor aerodynamic coefficients, with per-motor RPM telemetry for filter and powertrain tuning;
- motor spool-up lag with runtime load/voltage/inertia/back-EMF-headroom/hot-winding-resistance-dependent response, inferred KV/Kt/winding-resistance torque limits, temperature-adjusted copper resistance and torque authority, low-RPM static breakaway/cogging torque with cold-bearing viscosity drag, active braking spin-down, temperature-sensitive bearing friction, windage, healthy-prop and damage-driven imbalance with spinning lateral force injection, wet-prop drag, prop-scrape mechanical-loss torque, ESC output curve, command deadband, separate rise/fall slew limiting, dynamic voltage compensation, ESC command-frame rate/resolution signal modeling with per-motor sub-frame phase staggering, and BLDC commutation/phase-current ripple driven by duty cycle, load, voltage headroom, desync stress, rotor imbalance, and rotor speed, with torque ripple fed back into the rotor reaction-torque path;
- active-braking and rotor-flapping force-arm torque telemetry that separates asymmetric throttle-chop and transverse-flow thrust-vector body moments from ordinary rotor inertia, wake-swirl, and blade-dissymmetry hub moments;
- deterministic ESC desync/stutter under dirty airflow, water ingress, rain wetness, rotor stall, high load, low voltage, low motor voltage headroom, battery rail ripple/active-braking overvoltage spikes, or motor/ESC thermal stress, with RPM drop, current spike, HUD/status warnings, and blackbox telemetry;
- rotor rotational inertia, motor acceleration reaction torque, prop aerodynamic shaft torque, mechanical-loss shaft torque, inertial spin-up shaft power/current load, rotor gyroscopic coupling, spinning-prop angular drag torque, and blackbox telemetry for the resulting body torque, motor angular acceleration, mechanical loss, and motor shaft power;
- motor and ESC thermal rise, MOSFET-style current/switching/braking/desync heat, per-motor local airflow/RPM/air-density/obstruction cooling with recirculated dirty-air cooling loss near ground, walls, ceilings, and wake, ESC cooling-factor telemetry, and heat-based thrust limiting;
- gyro and accelerometer low-pass filtering, thermal/vibration-driven MEMS bias drift, gyro specific-force sensitivity, accelerometer high-g scale/cross-axis error, realistic full-scale gyro/accelerometer clipping, separate motor-fundamental and blade-pass dynamic notch-style gyro attenuation, deterministic vibration noise, IMU-mount specific-force sensing with angular and centripetal lever-arm acceleration, pressure-altitude barometer sensing with low-pass lag plus separate sensor noise, static-port dynamic-pressure/rapid-rotation bias, propwash/ground-pressure/ceiling-pressure bias, and battery sag/ripple/spike-derived IMU supply-noise error telemetry, configurable control-loop latency, receiver-side control-frame rate/quantization telemetry, and ESC command signal age/error telemetry;
- complementary IMU attitude estimation from gyro integration and accelerometer gravity correction, with correction trust reduced during high specific-force maneuvers, rotor vibration, or accelerometer clipping;
- RC receiver frame holding, channel quantization, command latency, stick smoothing, short link-loss hold, failsafe disarm, and raw-vs-processed command telemetry;
- armed motor idle, coupled least-squares mixer allocation across pitch/yaw/roll that preserves collective thrust on canted/asymmetric rotor layouts, airmode-style mixer desaturation, plus mixer saturation, split low/high saturation and headroom, achieved torque, and per-axis control-authority telemetry;
- per-motor RPM/angular acceleration/thrust/airflow-load/mechanical-loss torque/commutation torque ripple, shaft-power and back-EMF-based current draw with phase-current/current-ripple/RPM/load/thermal/desync electrical-efficiency telemetry and battery-voltage headroom telemetry, battery state-of-charge, Mendeley-shaped SOC/SOH internal-resistance lookup normalized onto each preset pack ESR, pack heat capacity with current/ripple I2R heating and airflow/water cooling, temperature-dependent internal resistance and current capability, instantaneous internal-resistance voltage sag, slower transient LiPo sag/recovery, active-braking regenerative current, battery bus voltage spike that feeds ESC rail-stability stress, current-ripple-driven battery bus ripple with ESC rail-stability stress and mild IMU supply-noise coupling, low-battery and battery-thermal power limiting, and dynamic over-current power limiting;
- blocked-axis contact response with separate impact, slip, bounce, and angular-impulse telemetry feeding tumble response plus crash-induced frame and rotor efficiency damage;
- per-rotor prop-strike checks against Minecraft block collision, so a spinning prop clipping an obstacle can damage only that corner before full-frame impact;
- Minecraft contact materials for block-aware friction, rebound, and prop-scrape severity on ice, slime, honey, loose sand/gravel, mud/soul-sand, hard stone/concrete, wood, metal, wool/hay, and cactus-style abrasive surfaces, with active friction/restitution/scrape multipliers logged for calibration;
- decaying prop-surface scrape loads from sustained disk contact, feeding per-rotor load, motor RPM drag, ESC desync risk, current draw, vibration, HUD/status warnings, and blackbox telemetry;
- rotor damage uses a nonlinear fault-vibration curve inspired by open DJI Mini 2 prop-damage data, then adds deterministic high-frequency gyro and accelerometer vibration plus effective bent-prop imbalance, so damaged props inject rotating force, commutation/current ripple, control feel changes, and blackbox traces, with motor-fundamental and blade-pass notch frequency/attenuation telemetry for filter tuning;
- per-axis Betaflight Actual-style rate/expo/super-rate input curves that preserve configured full-stick maximum rate while exposing softer center-stick sensitivity;
- Acro/rate-mode PID control with dynamic D-term low-pass filtering, target-rate feedforward, anti-gravity I-term boost on throttle punches, throttle PID attenuation (TPA), IMU clipping-aware PID authority foldback, and per-axis I-term relax/anti-windup during rapid setpoint changes or measured mixer authority loss;
- tunable Angle and Horizon self-level modes that convert attitude error into rate setpoints while still reusing the same PID, mixer, ESC, motor, and airframe physics path as Acro;
- blackbox-style rate setpoint/error and per-axis P/I/D/feedforward/output torque telemetry, with gyro-derivative D-term damping and setpoint feedforward separated for tuning;
- geometry-derived multirotor mixer using each rotor's force arm and actual thrust axis relative to the configured center of mass, plus configurable rotor outward cant, benchmark-spacing-window coaxial upper/lower load bias with a five-point New Dexterity z/D=0.72 command-map allocation prior and target/clipping/efficiency telemetry that now feeds aerodynamic shaft-torque and shaft-power scaling for complete X8 stacks, and airframe center-of-pressure moment arms for drag/lift torque, currently exposed through quad, cinewhoop, heavy-lift quad, six-rotor hex-lift, eight-rotor octo-lift, and compact coaxial-X8 presets, with per-rotor thrust and yaw reaction torque.
