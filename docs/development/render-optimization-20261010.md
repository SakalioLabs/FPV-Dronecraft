# FPV Dronecraft .5：渲染优化

继续用 PCL 启动原 **FPV**。本次更新目标为 `F:/Desktop/.minecraft/versions/FPV/mods`，包含 `fpv-dronecraft-fabric-0.1.1-playtest.5.jar` 和客户端渲染 Mod `sodium-fabric-0.8.14+mc1.21.11.jar`。视距 32、模拟距离 12 和现有显示选项由原实例继续管理，安装器不会写选项、手柄配置或存档。

Sodium 是 Minecraft 1.21.11 对应的稳定 Fabric 版本 [0.8.14](https://modrinth.com/mod/sodium/version/mc1.21.11-0.8.14-fabric)。下载自官方项目，已核对发布者 SHA-512；随包保留未经修改的 JAR 和原许可证。它优化客户端区块构建、上传与绘制，不接管本 Mod 的动力学或世界保存。

无人机渲染缓存最多八种桨布局。配置字符串变化立即重新解析，缓存不持有实体引用，几何快照只开放标量读取，避免共享可变数组。转速和姿态仍每帧更新。这消除了一项已确认的重复分配，但不能单凭这项小改动解释实测的数十毫秒尖峰。

性能报告也修正了曲线配对：`frames.csv` 的帧间隔属于相邻两帧起点之间的时间，在 HTML 中与已验证的前一帧渲染、交换缓冲、GPU、限帧耗时配对。环形覆盖或未完成帧导致前一帧缺失时，组件曲线留空；CSV 和统计计算保持原有含义。最后一帧的工作没有后续起点区间，仍保留在 CSV。

用户两次真实高速跨区域采集分别测得帧间隔 P99 36.93 ms / 32.64 ms，平均约 109 / 117 FPS。两次没有保存事件。对齐后渲染墙钟时间与帧间隔的相关系数均超过 0.96，支持优先改善渲染路径，但渲染墙钟时间同时包含 GC、调度、驱动等待，现有数据不足以分离具体成因。GPU 时间跨度也可能包含等待 CPU 提交的空闲，不能将其直接当作 GPU 饱和证据。

代码与 Minecraft 1.21.11 实现核查确认，FPV 的服务端相机切到无人机后，玩家与区块跟踪中心会随相机移动。本次没有加入额外区块票据或改写保存。提高视距扩大驻留和绘制范围，预加载仍受生成、构建、上传吞吐影响，不能保证高速移动中的稳定帧时间。

验证资料在包的 `verification/`。生产检查使用原实例的 68 项运行库、LWJGL 3.3.3-snapshot、Fabric API 0.141.6；游戏目录为项目 `build/performance/` 下的独立世界，使用合成离线账户，不访问用户存档。正常运行循环检查监测 CPU/GPU、采集重启、保存、退出和重开；自动飞行检查使用固定种子普通地形、32 区块视距、12 区块模拟距离及 3440×1351 窗口，诊断驱动以约 64 m/s 沿固定圆周移动无人机，并检查标准 FPV 与模式切换。辅助诊断 Mod 不进入交付的 mods。

隐藏窗口和新生成的测试世界只能证明功能兼容与合成负载行为，不能换算成用户显示器上的改善百分比。真实游玩后的帧时间仍需后续采集判断，本轮不要求用户重复相同路线。

安装器核对现有 .4、两份新 JAR 和 Fabric API 哈希，确认实例退出后先备份再更新，失败恢复原文件。旧 .4 与安装收据位于 FPV 的 `fpvdrone-render-optimization-backups/`。如需回退，退出 FPV，在本包运行 `rollback-render-optimization.ps1 -ReceiptPath '<安装收据的完整路径>'`；脚本只在现有 .5 与新增 Sodium 文件哈希正确时操作，恢复 .4 并移除本轮新增 Sodium。不要同时保留两份 FPV Mod。

构建：Java 21，`./gradlew.bat --offline -Pmod_version=0.1.1-playtest.5 :fabric-mod:build`。正常生产验证入口：`tools/start-runtime-monitor-production-smoke.ps1`；自动飞行附加诊断属性 `-Dfpvdrone.performance.renderStress=true`、`phaseSeconds=20`、`warmupSeconds=10`，通过脚本 `DiagnosticProperties` 数组传入。仅在项目独立测试目录运行这些诊断。
