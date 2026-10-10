# 可玩版本恢复入口

当前交付为 `0.1.1-playtest.11`，用于单人飞行、FPV、HUD 和声音体验测试。无人机独立物品放置，遥控器 Shift 右键精确绑定；修复无人机视角中真实玩家身体的显示，见 [放置绑定与玩家模型](placement-binding-20261010.md)。遥控器已接入指定 Blockbench 模型，FPV 与无人机第三人称隐藏第一人称手臂和主副手物品，见 [遥控器模型与飞行视角](controller-model-20261010.md)。FPV 与无人机第三人称保留玩家原处的正常身体，受伤、击退和重力仍然有效；无人机单独加载和更新附近区块，目视时保持遥控连接。见 [独立遥控安装与回退](remote-pilot-20261010.md)。默认采用 `PLAYABLE` 飞行模型、`5inch_agile_candidate` 预设和 `ANGLE` 模式。B 键三视角循环保持：目视 → FPV → 无人机第三人称 → 目视。第三人称跟随机身、保持地平线水平并进行方块碰撞检查；保留指定 Blockbench 模型、贴图和三叶桨动画；沿用 ACRO 连续翻转与松杆保持修复，保留手柄 ACRO 油门修正、位置与姿态插值、可开关的帧时间监测、桨布局缓存和 Sodium。见 [三视角安装与回退](camera-views-20261010.md)、[无人机模型接入与回退](drone-model-20261010.md)、[连续翻滚修复与回退](rotation-continuity-20261010.md)、[ACRO 下坠回拉修正](acro-descent-recovery-20261008.md)、[移动平滑与保存排查](smooth-motion-and-saving-20261009.md)、[运行时性能监测](runtime-performance-monitor-20261010.md) 和 [渲染优化](render-optimization-20261010.md)。

## 项目结构

| 目录 | 用途 | 本轮开发入口 |
| --- | --- | --- |
| `fabric-mod` | Minecraft 实体、控制、网络、渲染、FPV、HUD、声音 | 客户端体验与测试包 |
| `drone-sim-core` | 飞行物理、飞控、预设、遥测 | 飞行手感和回归测试 |
| `computational-acoustics-core` | 声源合成、传播和混响算法 | 后续声音改进 |
| `two-edge-wave-reference` | 波动声学参考实现 | 研究验证 |
| `docs`、`tools`、`native`、`build/research` | 研究文档、验证工具和实验输出 | 按具体课题查阅 |
| `fabric-mod/src/playtest` | 独立的客户端游玩流程检查 | 不包含声学采集测试的探针 Mixin |

原工作区的研究记录和未提交修改保留。可玩恢复工作在独立工作区 `F:/CodexHome/worktrees/playable-recovery/MCFPV` 的 `codex/playable-recovery` 分支继续，避免研究输出与交付文件混在一起。

## 安装与第一次飞行

1. 使用 Minecraft **1.21.11**、Java **21**，安装 [Fabric Loader](https://fabricmc.net/use/installer/) **0.19.3**。
2. 使用放置绑定包的安装器，核对并备份原 .10 后安装 `fpv-dronecraft-fabric-0.1.1-playtest.11.jar`，沿用 Sodium 0.8.14 和 Fabric API `0.141.6+1.21.11`，同一 Mod 只保留一个版本。原 FPV 的视距、手柄和存档保留；先前 `FPV-Smooth-20261009` 仍为独立 .3 测试实例。
3. 新建**创造模式、允许作弊、超平坦**世界，选择空旷位置，在 FPV Dronecraft 物品分类获取无人机和遥控器。手持无人机对方块顶面右键放置，再手持遥控器 Shift 右键机体绑定。已有机体也需配对一次。
4. 关闭聊天窗口，保持手持已绑定遥控器；开发时也可用 `/fpvdrone spawn` 和 **V** 虚拟遥控器。若 HUD 的输入源是 `GAMEPAD`，按 **G** 关闭遥控输入，切到 `KEYBOARD`。切换输入源会保留油门；先按住 **PageDown** 直到油门为 **0%**，松开方向键，再按 **R** 解锁。HUD 应显示 `ANGLE` 和键盘输入。
5. 短按或按住 **PageUp** 增加油门，观察高度变化；松开后油门会保持。用 **PageDown** 降低油门。首次测试先低空起降。
6. 按 **B** 依次切换**目视 → FPV → 无人机第三人称 → 目视**，第一次按 B 进入 FPV。方向键控制俯仰/横滚，**Z/X** 控制偏航。降到地面、油门归零后按 **R** 上锁。

笔记本没有独立 PageUp/PageDown 时，在“选项 → 控制 → 按键绑定 → FPV Dronecraft”中改成方便的按键。按键冲突也在这里处理。

| 默认按键 | 功能 |
| --- | --- |
| PageUp / PageDown | 增加 / 减少油门，松开保持 |
| ↑ / ↓ | 俯仰 |
| ← / → | 横滚 |
| Z / X | 偏航 |
| R | 解锁 / 上锁 |
| B | 目视 → FPV → 无人机第三人称 → 目视 |
| V | 启用 / 关闭虚拟遥控器 |
| G | 启用 / 关闭手柄输入；键盘测试时关闭 |
| M | ANGLE → HORIZON → ACRO → ANGLE |
| N | 切换 HUD 显示 |
| I | 遥控器设置 |

`ANGLE` 适合第一次测试。`ACRO` 不会自动回正，熟悉操作后再尝试。若发生碰撞，可执行 `/fpvdrone repair`。`/fpvdrone status` 查看当前绑定无人机。

遥控器设置页支持滚轮、滚动条和 PageUp/PageDown，底部“完成”按钮始终可见。本轮修复了该页的小窗口布局与文字透明问题，并替换了损坏的透明无人机贴图。

## 建议这轮测试的内容

- 能否进入世界、独立放置无人机、Shift 右键绑定、用 R 正常开始飞行；多架机体是否只控制选中的那架。
- 油门增减是否可控，方向是否符合预期，起降和碰撞恢复是否正常。
- 三视角切换是否稳定，第三人称能否看清机身，镜头抖动、视野和 HUD 是否影响操作。
- 声音是否随转速和距离变化，是否出现爆音、卡顿、重复播放或切换视角后的异常。
- 退出重进世界后能否重新开始，持续游玩约 10 分钟是否稳定。

反馈时写清按键顺序、模式、出现问题的场景，并附该实例的 `logs/latest.log`。飞行问题可先执行 `/fpvdiag start`，复现后执行 `/fpvdiag stop`；聊天会显示记录的完整路径，所在目录名为 `fpvdiag-traces`。

声学数值精度仍是后续研发项。这份包用于功能和主观体验测试，尚未建立目标机型的实测声压验收。

## 开发与验证

PowerShell 在上述恢复工作区运行：

```powershell
.\tools\playtest.ps1 -Action build
.\tools\playtest.ps1 -Action smoke
.\tools\playtest.ps1 -Action client
```

使用已有依赖缓存时加 `-Offline`；指定 JDK 时加 `-JdkHome 'C:/path/to/jdk-21'`。脚本会选择 Java 21，保留真实退出码。`build` 仅构建当前交付使用的三个模块，运行单元测试与服务端 GameTest；`smoke` 启动真实客户端，自动检查键盘起飞、FPV、模式切换、上锁、设置页和离开世界；`client` 启动开发客户端供人工测试。

测试产物为 `fabric-mod/build/libs/fpv-dronecraft-fabric-0.1.1-playtest.11.jar`。安装使用这个 JAR，`-sources.jar` 供阅读源码。客户端检查的报告和截图在 `fabric-mod/build/run/playableClientGameTest/<RunId>/`；脚本默认以时间生成 RunId，也可传 `-RunId` 指定。独立检查关闭 Fabric 测试工具的包同步器，使用正常异步网络传递和有超时的状态检查，隐藏检查自身的窗口以免影响正在游玩的实例；生产 JAR 不包含测试模块。

后续先根据这轮反馈修复启动、控制和镜头问题，再收敛飞行手感及声音体验；声学标定另行使用可信公开数据或目标机型实测推进。
