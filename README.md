# FPV Dronecraft

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
