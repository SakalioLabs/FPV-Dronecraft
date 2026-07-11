# NeoForge Installation and Verification

## 发布状态

`NeoForge` 分支面向以下固定运行环境：

| Component | Version |
| --- | --- |
| Minecraft | `1.21.11` |
| NeoForge | `21.11.42` |
| Java | `21` |
| FPV Dronecraft | `0.1.0` |

公开的 GitHub `v0.1.0` Release 当前仍是 Fabric 构建，不包含 NeoForge jar。
在 NeoForge 正式打 tag 前，请使用 [`NeoForge` 分支成功 CI](https://github.com/SakalioLabs/FPV-Dronecraft/actions/workflows/ci.yml?query=branch%3ANeoForge)
生成的 `neoforge-verified-distributions` artifact，或在本地从该分支构建。

## 获取已验证 Jar

在仓库根目录运行：

```powershell
$env:FPVDRONE_UPDATE_GOLDEN_TRACES = "false"
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 :neoforge-mod:verifyNeoForgePackaging
```

输出包括：

- `neoforge-mod/build/libs/fpv-dronecraft-neoforge-0.1.0.jar`：生产包，默认使用 legacy heavy racing quad 手感。
- `neoforge-mod/build/fpvdrone-distributions/legacy-heavy-racing-quad.jar`：生产包的逐字节副本。
- `neoforge-mod/build/fpvdrone-distributions/5inch-agile-candidate.jar`：仅把默认 playable preset 改为 `5inch_agile_candidate`。

三个 jar 只能选择一个安装。打包验证器会检查 NeoForge 元数据、版本范围、
嵌套 `drone-sim-core`、六个客户端 Mixin、许可证、资源 JSON、测试/Fabric
条目隔离和两个变体的逐 entry 差异。

## 客户端安装

1. 使用 Java `21` 安装 Minecraft `1.21.11` 与 NeoForge `21.11.42`。
2. 启动一次 NeoForge profile，让游戏目录生成 `mods` 文件夹。
3. 把一个已验证的 FPV Dronecraft jar 放进 `mods`；不要同时放入 base、legacy 和 candidate。
4. 此 NeoForge 构建不需要 Fabric Loader 或 Fabric API。
5. 启动游戏并确认 Mods 列表包含 `FPV Dronecraft 0.1.0`。

## 专用服务器安装

1. 使用官方 [NeoForge `21.11.42` installer](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.11.42/neoforge-21.11.42-installer.jar) 创建 Minecraft `1.21.11` 服务端。
2. 阅读 Mojang EULA；只有接受后才把服务端目录的 `eula.txt` 设置为 `eula=true`。
3. 把一个已验证的 FPV Dronecraft jar 放入服务端 `mods` 目录。
4. 使用 installer 生成的 `run.bat` 或 `run.sh` 启动，不要把开发环境的
   `build/classes`、`fml.modFolders` 或 GameTest 资源加入生产实例。

会修改全局 debug、故障注入、环境 override 或 tuning 状态的命令要求
Minecraft GameMaster 权限。状态查询和普通 `/fpvdrone preset ...` 玩法命令
仍可由普通玩家使用。

## 开发与发布验证

```powershell
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 :neoforge-mod:build
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 :neoforge-mod:runGameTestServer
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 :neoforge-mod:verifyNeoForgePackaging
```

四种服务端飞行自测还需要在 `neoforge-mod/run/eula.txt` 中显式接受 EULA：

```powershell
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 :neoforge-mod:runServerSelfTest
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 :neoforge-mod:runPlayableServerSelfTest
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 :neoforge-mod:runPlayableHorizonServerSelfTest
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 :neoforge-mod:runPlayableAcroServerSelfTest
```

## English Summary

Install Java 21, Minecraft `1.21.11`, and NeoForge `21.11.42`. The public
`v0.1.0` release currently contains the Fabric build only, so obtain the
NeoForge jar from a successful `NeoForge` branch CI artifact or build it with
`:neoforge-mod:verifyNeoForgePackaging`. Install exactly one of the production,
legacy, or candidate jars in both client and server `mods` directories. Fabric
Loader and Fabric API are not dependencies of the NeoForge build.
