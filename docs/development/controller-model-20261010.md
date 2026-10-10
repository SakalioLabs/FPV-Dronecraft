# 遥控器模型与飞行视角

交付版本：`0.1.1-playtest.10`，Minecraft 1.21.11 / Fabric Loader 0.19.3 / Java 21。

遥控器物品使用 `controller-design-20261010` 中的 Blockbench 导出模型和贴图，包含 283 个立方体及一张 2048 × 2048 贴图。物品注册、配对和遥控操作沿用原有逻辑；物品栏、第一人称、第三人称、掉落物和展示框使用模型包中的显示姿态。模型中的摇杆、拨杆和屏幕目前呈现静态设计。

目视时可以看到手中的遥控器。切换至 FPV 或无人机第三人称后，隐藏本人的第一人称手臂和主副手物品；切回目视后恢复显示。`.9` 已包含这项隐藏处理，本轮新增实际持有物品的运行验证。B 的循环保持 **目视 → FPV → 无人机第三人称 → 目视**。独立玩家身体、区块更新、无人机模型和飞行控制保持 `.9` 的实现。

## 安装与回退

安装包目录：`F:/Codex-Project/MCFPV/deliverables/controller-model-20261010`。

退出游戏，在 Windows PowerShell 运行：

```powershell
& 'F:/Codex-Project/MCFPV/deliverables/controller-model-20261010/install-controller-model.ps1'
```

安装器核对 `.9`、Fabric API 和 Sodium 的校验值，将原 `.9` 移至实例中的 `fpvdrone-controller-model-backups`，再安装 `.10`。设置和存档保留，重复执行不会添加重复 Mod。安装完成后重新启动原来的 FPV 实例。

如需恢复 `.9`，退出游戏，使用安装回执中的备份目录：

```powershell
& 'F:/Codex-Project/MCFPV/deliverables/controller-model-20261010/rollback-controller-model.ps1' -ReceiptPath '备份目录/install-receipt.json'
```

## 资源与验证

`tools/import-controller-model.ps1` 可重新导入原始模型，将导出 JSON 原样保存为 `models/item/fpv_controller_v1.json`，PNG 与交付资产逐字节一致。现有 `assets/fpvdrone/items/drone_controller.json` 继续指向 `fpvdrone:item/drone_controller`；这个简短的子模型继承原设计并补上缺省的 `particle` 贴图引用，避免原版粒子使用缺失贴图。几何、UV、显示姿态和贴图内容保持原设计。

本轮检查在一次性世界进行，1,389 项单元测试、10 项服务端 GameTest、客户端操作流程及加载 Sodium 的普通生产客户端检查均通过。生产客户端实际持有遥控器和盾牌，检查目视、FPV、无人机第三人称及主副手交换后的显示；四个无人机视角场景中，原版手持物绘制调用均为零。八张遥控器截图及三张视角回归截图已逐张查看，保存重进和性能监测检查也通过。447 个生产 Java 类及嵌套声学、飞行模块的类与 `.9` 字节一致；诊断代码不进入生产 Mod。包内 `verification` 和 `SHA256SUMS.json` 保存报告、截图和校验值。

游玩时拿出遥控器，按 B 切换视角即可检查。已有无人机 HUD 继续显示。身体与其他实体在世界中的物品显示遵循正常渲染。
