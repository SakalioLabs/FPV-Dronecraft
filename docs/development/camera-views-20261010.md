# B 键三视角循环

交付版本：`0.1.1-playtest.8`，Minecraft 1.21.11 / Fabric Loader 0.19.3 / Java 21。

默认从**目视**开始。绑定无人机并持有遥控器或开启虚拟遥控器后，每按一次 **B**，按以下顺序切换：

**目视 → FPV → 无人机第三人称 → 目视**。

- 目视：镜头回到玩家，保持玩家原本的 F5 视角设置。
- FPV：沿用机载镜头、机身姿态和原有镜头设置，隐藏当前无人机的机身。
- 无人机第三人称：跟在无人机后方约 5 米、上方约 2 米，看向机身中心；地平线保持水平，机身俯仰和翻滚仍正常显示。镜头遇到实体方块时会缩短距离，使用原版摄像机的碰撞探测；不显示玩家的手臂和手持物品。

HUD 显示当前视角。关闭遥控器、失去无人机、离开世界或切换绑定时恢复目视。无人机视角中的 F5 不改变 B 的循环阶段；回到目视后使用玩家当前的 F5 设置。

该版本保留 `.7` 的指定 Blockbench 模型、贴图与桨叶动画，以及连续 ACRO 翻滚、油门回拉、位置姿态插值、性能监测和 Sodium。

## 安装与回退

安装包目录：`F:/Codex-Project/MCFPV/deliverables/camera-views-20261010`。

退出游戏，在 Windows PowerShell 运行：

```powershell
& 'F:/Codex-Project/MCFPV/deliverables/camera-views-20261010/install-camera-views.ps1'
```

安装器核对 `.7`、Fabric API 和 Sodium 的文件校验值，将原 `.7` 移至实例中的 `fpvdrone-camera-view-backups`，再安装 `.8`。它保留实例的设置和世界，重复执行不会添加重复 Mod。安装完成后重新启动原来的 FPV 实例。

如需恢复 `.7`，退出游戏，使用安装回执中记载的备份目录：

```powershell
& 'F:/Codex-Project/MCFPV/deliverables/camera-views-20261010/rollback-camera-views.ps1' -ReceiptPath '备份目录/install-receipt.json'
```

## 验证范围

交付前运行三个模块的 1,382 项单元测试、10 项服务端 GameTest、真实客户端键盘操作流程，以及加载 Sodium 的普通生产客户端检查。客户端检查覆盖三视角顺序、机身显示、镜头距离与水平姿态、墙体遮挡、F5、遥控权限丢失、退出世界和保存重进。

普通客户端的三视角截图还需人工查看后才能打包；检查代码只在诊断模块中，生产 Mod 不包含它。安装器另外以临时实例验证重复安装、回退及校验不匹配时保持文件不变。报告、截图、安装清单和校验值随包放在 `verification` 和 `SHA256SUMS.json` 中。自动检查使用一次性世界，手柄飞行手感由本轮实际游玩继续反馈。
