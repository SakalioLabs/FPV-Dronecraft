# 2026-10-08 正式安装包启动崩溃

状态：已定位故障模块并提供经过启动验证的独立环境候选；原故障尚未稳定复现。

## 报告与定位

用户错误报告：`错误报告-2026-10-8_22.35.35.zip`。Minecraft 1.21.11、Fabric Loader 0.19.3、Fabric API 0.141.6+1.21.11、Microsoft Java 21.0.7、Mod 0.1.1-playtest.1。

游戏在 Mod 客户端初始化完成后、窗口后端初始化日志之前退出。Windows Application Error / WER 确认退出为 `0xC0000005`，故障模块为 `lwjgl.dll`，偏移 `0x10c5`。报告中的启动脚本含认证参数，仅作为脱敏后的诊断数据读取，未执行或纳入交付包。

实际 LWJGL 3.3.3 Java JAR、Windows x64 DLL 与开发缓存一致；客户端和有校验值的运行依赖均通过 SHA-1 核对。未发现错架构、缺失或损坏的依赖。

DLL 反汇编显示故障位置处于 JavaVM 指针解引用 / JNI GetEnv 路径。对照 [LWJGL 3.3.3 官方源码](https://raw.githubusercontent.com/LWJGL/lwjgl3/3.3.3/modules/lwjgl/core/src/main/c/common_tools.c)，Windows DLL 的线程退出处理会无条件查询线程 JNI 环境。[3.3.6 的对应源码](https://raw.githubusercontent.com/LWJGL/lwjgl3/3.3.6/modules/lwjgl/core/src/main/c/common_tools.c)限制了这段调用，仅在存在对应线程数据且为异步线程时执行。

这是升级相关依赖的依据，不能据此确认本次崩溃一定来自某个初始化竞态。没有可用的 minidump / 寄存器现场；Mod 在此阶段的注册和声学初始化没有发现直接 native 调用。

## 本次处理

- 新建 `F:/Desktop/.minecraft/versions/FPV-Playable-20261008`，保留独立版本隔离设置。
- 固定 8 个 LWJGL 模块的 Java JAR 和 Windows x64 native JAR 至 3.3.6，共 16 个文件；从 Maven Central 获取并核对官方 SHA-1。
- Mod JAR 保持 0.1.1-playtest.1，SHA-256 `d381bbe7a8d85576ec1c9933e5bfbe5b150eff93311ff2fbb5dcaf1de21a238e`。
- 使用 Fabric API 0.141.6+1.21.11，官方 Maven SHA-1 `c98467cbbaf4d197377266795ae015f4130d65b6`。
- 原版本 JSON、PCL 设置和两份 Mod JAR 的哈希保持一致。安装器不写原存档或配置。验证期间另一个正常游戏进程启动了原版本，原游戏自行更新了 options.txt 和日志，未回退这些变化。

安装脚本为 `tools/install-playable-startup-fix.ps1`。它先检查全部输入及目标冲突，再创建新版本；拒绝覆盖已有版本目录或内容不一致的同名依赖。新环境只复制客户端软件及两份 Mod，不复制存档、账户或旧控制配置。

## 验证及范围

独立 Java 探针使用用户实际 Java 和 LWJGL 3.3.3，核心库及 GLFW 初始化 / 退出均成功。原环境的正式 JAR 也完成了启动和正常退出；原故障未稳定复现，且原版本后续运行日志显示进入了单人世界。

3.3.6 候选与最终新实例的实际 JSON / 已安装依赖均通过生产运行验证：普通 KnotClient、intermediary 命名空间、`developmentEnvironment=false`、`gameTestActive=false`，加载正式 Mod JAR和发布版 Fabric API。窗口 GL 上下文、声音引擎、资源加载、连续标题渲染及 PNG 截图检查通过，调用 Minecraft.stop 后进程退出码为 0。

生产测试探针默认隐藏自身测试窗口，避免抢占用户输入；仅存在于隔离诊断运行目录，未安装到交付实例。第一次候选截图曾遇到窗口输入导致的切屏；该记录不作为标题截图通过证据。修正探针为连续 40 ticks、截图回调及退出前再次确认标题，重新测试后截图人工核对通过。

结果目录：`build/playtest-20261008/production-lwjgl336-2` 和 `build/playtest-20261008/production-installed-instance`。本次新增验证针对启动和退出；此前在开发环境通过的完整飞行 / FPV / 控制界面 GameTest 仍有效，但不能代替新环境中的人工试玩。

## 用户测试入口

刷新 PCL 的版本列表，选择 `FPV-Playable-20261008`，保持 Java 21，使用新建的创造世界测试。运行 `/fpvdrone spawn racing_quad`，开启虚拟控制器，先把油门降到 0，再解锁和升油门。完整键位仍以试玩包说明及 I 设置页为准。

若再次崩溃，保留该新实例对应的完整错误报告及其 `latest.log`；只有再次发生同类 native 故障并获得现场，才能验证本次依赖升级是否解决原问题。
