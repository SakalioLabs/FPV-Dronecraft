# 无人机模型接入（0.1.1-playtest.7）

这版把 `deliverables/drone-design-20261010/blockbench/fpv_dronecraft_v1.bbmodel` 和配套贴图接入可玩版本。灰黑机架、顶部电池与绑带、前摄像头、后天线、四组电机、八边形护圈、金色三叶桨和脚垫均来自这份模型。

本轮沿用 .6 的飞行物理、预设、控制、FPV、性能监测、Sodium 和连续 ACRO 修复。机身目视渲染采用与飞行姿态一致的偏航、俯仰、横滚顺序；护圈随机架运动，桨叶按对应电机转速与旋向单独旋转。现有存档中的无人机会自动使用新外观。

实机截图还暴露了原渲染调用的参数错误：`submitModel` 的短重载最后一个整数是轮廓色，之前传入 `-1` 会以不透明白色覆盖整个模型。现在传入实体实际的 `outlineColor`，普通状态显示贴图和明暗，同时保留 Minecraft 原有的发光轮廓行为。

## 模型来源与开发入口

- 输入模型 SHA-256：`51740fa4a34503ddae79030edac43ea70fbfa273f6835636409e84389aa1d476`。
- 输入贴图 SHA-256：`f9c732723164c4e25687cc9a70b0c66300b43326baf0322fb0e78cd7a3fcbe57`。
- 原模型包含 280 个方块部件、23 个分组；UV 逻辑分辨率 512×512，PNG 实际为 2048×2048。
- 模型通过项目内转换工具生成原生模型定义，在客户端资源加载时烘焙。逐帧更新仅设置姿态、旋翼位置和动画，继续使用已有的旋翼布局缓存。
- 原素材的机头为 -Z、向上为 +Y；导入转换将其映射到项目的机头方向和模型坐标，并保留部件旋转中心、UV 和薄片变形量。

渲染尺寸沿用项目布局比例，物理质量、碰撞尺寸、飞控与油门参数沿用 .6。

开发入口为 `fabric-mod/src/client/java/com/tenicana/dronecraft/client/render/DroneEntityModel.java`、`ImportedDroneDesign.java` 与 `tools/import-drone-design.ps1`。更新 Blockbench 源资产后，从恢复工作区运行转换器，再用 `tools/playtest.ps1 -Action build` 构建；交付包使用普通 remap JAR。生成源码与源资产夹具纳入代码，游戏不依赖磁盘上的 `.bbmodel` 路径。

## 安装与回退

当前实例为 `F:/Desktop/.minecraft/versions/FPV/mods`。关闭游戏后，交付文件夹中的安装器可核对并备份已验证的 .6，再安装 .7：

```powershell
& './install-drone-model.ps1'
```

安装凭据和原 .6 JAR 保存在实例的 `fpvdrone-model-backups/<时间与标识>/`。Sodium、Fabric API、手柄设置、选项和世界保留。

需要恢复 .6 时，关闭游戏并使用本次凭据：

```powershell
& './rollback-drone-model.ps1' -ReceiptPath 'F:/Desktop/.minecraft/versions/FPV/fpvdrone-model-backups/<本次目录>/install-receipt.json'
```

## 验证与查看

交付包的 `verification/` 保存单元测试统计、服务端与客户端功能检查、普通生产客户端检查、安装器验证和模型实机截图。生产客户端与原实例相同的 Fabric API、Sodium 联合加载，在独立测试世界中检查模型与保存重开。

1,375 项单元测试通过（Fabric 614、飞行核心 491、声学核心 270），服务端 GameTest 10/10 通过。最终客户端起飞、FPV、720° 姿态包、上锁、设置页与监测导出通过；普通生产客户端的前、后、顶视图、桨叶动画、CPU/GPU 监测与保存重开通过，进程退出码 0。最终 JAR 核对表明原有类仅修改模型与模型渲染器，新增导入的几何类，飞行、相机和手柄类字节不变。

首次截图的纯白轮廓问题已修正并重新构建、检查。倾斜姿态截图使用地面静止渲染夹具，最低护圈部分会被平台遮住；完整四组结构见直立截图与实际烘焙几何测试。脚本截图用于模型外观与动画核对，帧率或手柄体验由实际游玩验证。

模型外观在目视模式可见；按 B 退出 FPV 查看，或执行 `/fpvdrone spawn` 生成新机。检查机头镜头朝向、电池与天线位置、金色三叶桨转动、护圈保持固定。FPV 模式继续隐藏自己控制的机身，以免挡住镜头。

自动验证使用独立世界和脚本输入，手柄实机体验由后续游玩反馈确认。
