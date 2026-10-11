# CNC 加工机床

NeoForge `0.2.0-neoforge-dev.5` 增加 CNC 方块和碳纤维板物品。

## 使用

1. 在创造模式的 FPV Dronecraft 物品栏取出 **CNC 加工机床** 和 **碳纤维板**。
2. 将机床放在地上，右键打开加工界面。
3. 在左侧材料槽放入至少 **4 块碳板**。右侧成品槽为空时开始加工。
4. 经过 **120 个服务端 tick**（正常 20 TPS 时约 6 秒），消耗 4 块碳板，产出 **1 个无人机机架**。
5. 从右侧取出机架，放到地上，空手右键按既有流程装配无人机。

界面支持原版点击、拖动和 Shift 点击移入/移出。材料槽只接收碳板；成品槽只可取出。成品未取出时不继续加工，避免非堆叠机架被覆盖。

机架仍需四个电机、四副飞桨、电调、飞控、接收器和电池才能解锁。相机可选：缺相机只能目视，装上后可用 B 切换 FPV 和无人机第三人称。

**碳板和 CNC 机床暂时没有新增合成表**，按当前阶段先从创造物品栏或命令获取。已有的条件化 Mekanism 机架合成表保持可用。

```mcfunction
/give @s fpvdrone:cnc_workstation
/give @s fpvdrone:carbon_plate 4
```

## 保存与加工

材料、成品及加工进度保存在机床方块实体中；关闭界面不会停止加工。只有区块正常 tick 时才推进进度，重新加载后从保存进度继续。拆掉机床会掉落机床和现有库存；尚未完成的加工不会额外产生机架。改变材料槽（取出或加入材料）会取消本轮进度，材料直到加工完成才消耗。

本阶段不增加电力需求。加工由服务端判断，客户端只显示库存、进度和模型动画。

## 资源来源

机床来自 `../MCFPV/deliverables/cnc-design-20261011/blockbench/cnc_workstation_v2.bbmodel`，使用配套 PNG 和 handoff contract。模型坐标以 -Z 为正面，放置方向跟随玩家；玻璃使用独立透明渲染层，加工部件按服务端进度驱动。

碳板图标通过内置 image_gen 生成，保留透明通道。游戏资源采用 64×64 像素，用最近邻采样转换标准纹理尺寸；生成原图另行留档。游戏资源路径为 `neoforge-mod/src/main/resources/assets/fpvdrone/textures/item/carbon_plate.png`。完整提示词及生成原图留在 `../MCFPV/deliverables/cnc-runtime-neoforge-20261011/artwork/`。

## 验证记录

当前构建的原生加工、库存、保存以及客户端世界证据随交付包保存；具体版本、通过数量和运行 UUID 见 `cnc-runtime-neoforge-20261011/validation-summary.json`。其他 Minecraft 目标的编译或启动检查不代表已完成其生存世界功能实测。

Minecraft 1.21.1 / NeoForge 21.1.256 已通过 479 项适配层单测、34 项原生 GameTest 和 46 项世界检查，并保存了 22 张截图。1.21.1 到 26.3 的 16 个固定目标均通过当前版本编译、打包和原生客户端模型加载检查；其他目标尚未做世界内加工验证。逐目标产物和验证来源见交付包的 `multiversion-manifest.json`。
