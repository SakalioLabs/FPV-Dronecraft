# D080 — Minecraft 真实方块双房间 portal snapshot 通过

日期：2026-07-25  
状态：**integrated Client GameTest evidence passed；measured coupled RIR 与 OpenAL pending**

## 场景与目的

D079 的 portal 几何来自 core 的合成 voxel volume。本轮在 Minecraft 1.21.11
integrated client world 中用真实 block state 构造相邻房间：

- 房间 A：stone，内部约 `9×9×7 cells`；
- 房间 B：white wool，内部约 `9×9×7 cells`；
- 共用 glass 隔墙；
- 门洞：`3×4=12 cells`；
- listener 固定在场景创建前的 block anchor；
- 每个状态捕获 `33×33×13` reflection snapshot，运行
  `4096 rays × 48 bounces` 与 `2×effective MFP` late-diffuse policy。

状态序列为 `closed → open → closed`。同步门禁检查门洞全部 12 个方块，而不是只检查
中心方块；这避免客户端尚未收齐完整 block update 时过早采样。listener 也不跟随
玩家碰撞/重力造成的细小位移。

## 结果

| metric | closed | open |
|---|---:|---:|
| room-B surface hits | 0 | 53,420 |
| rays entering room B | 0 | 2,795 |
| rays returning room A | 0 | 1,334 |
| mid RT60 | 3.920 s | 0.871 s |
| wet gain | 0.38832 | 0.38575 |
| openness | 0 | 0 |

第二个 closed snapshot 与第一个 closed snapshot 在固定空间端点上完全复现。真实
`MinecraftAcousticMaterials` 映射因此保留了 D079 的核心语义：门洞打开后能量进入
soft 邻室并返回，compound volume 没有被误报为室外 escape。mid RT60 明显下降，
而 wet 仅小幅下降，再次说明当前 wet mapper 主要受 openness/first reflection
控制，不能单独描述 coupled-room late decay。

测试开发中出现过两个被门禁捕获的非声学数值问题：

1. 只等待门洞中心同步会让第二个 closed snapshot 读到部分更新；
2. 使用实时玩家眼位会把碰撞/重力位移混入 closed endpoint 比较。

最终实现分别改为完整 12-cell 同步和固定 block anchor。这两项是后续动态环境
GameTest 的通用约束。

## 可复现证据

```powershell
.\gradlew.bat --no-daemon :fabric-mod:runClientGameTest
.\gradlew.bat --no-daemon verifyMinecraftPortalGameTest
```

- integrated report SHA-256：  
  `3b05e948f53625d3cff933fb048996017b573341d40de7a9b679e409895c1c07`
- independent verification SHA-256：  
  `3b8d361cd3a5a5133a908627dd93cdc42c644a0828c6f5f654f70c523822b4e6`
- Python regression suite：`121 tests passed`。

live report 必须先由 `runClientGameTest` 生成，统一的轻量
`acousticResearchCheck` 不会启动 Minecraft。独立 verifier 固定 sequence、aperture、
ray/bounce budget、transport/return、openness、RT60 direction、endpoint
repeatability 与 `release_calibrated=false`。

## 决策

1. Minecraft block-state capture 与 material mapping 可以进入后续 runtime research；
2. dynamic snapshot 的原子性必须按完整变更区域确认，不得用单点同步代替；
3. 环境比较使用固定 listener anchor；玩家移动属于另一个实验变量；
4. 下一步不再扩展合成 portal 尺寸矩阵，优先获取 matched coupled-room/doorway RIR，
   并在真实 OpenAL callback 中测 20-tick snapshot update 是否产生 click/underrun；
5. reverb feature 继续默认关闭。

## Claim boundary

本轮验证的是 Minecraft 方块捕获、材料映射、射线传输与参数端点，不是真实移动门板的
连续几何采样。没有 measured coupled RIR、portal 透射损耗、绕射、OpenAL scheduling
或听感实验；报告保持 `release_calibrated=false`。
