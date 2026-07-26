# D062 — 环境传播必须只有一个 owner

- 状态：implemented（2026-07-24）。
- 原因：程序化 PCM 仍作为普通 Minecraft streaming source 进入 sound engine。
  如果 Sound Physics Remastered 同时处理该 source，而 Dronecraft 已把 DDA
  transmission/diffraction 烘进 PCM，就会发生双重遮挡和频谱衰减。
- 官方边界：使用 Fabric Loader 的 `isModLoaded` 做 capability detection，不读取
  外部模组内部类。Fabric 的 `fabric.mod.json` 将 `id` 定义为稳定模组标识；
  Sound Physics Remastered 当前 manifest 的 id 是
  `sound_physics_remastered`。
- 行为：`auto` 模式在检测到该 id 时保留 Dronecraft 的 order synthesis、
  source directivity 与 Doppler，但不调度内部传播 worker，并把既有 transmission
  平滑释放到 unity，让外部模组成为唯一环境传播 owner。
- 覆盖：JVM property
  `-Dfpvdrone.acoustics.propagationMode=auto|internal|clean` 可用于对照实验。
  非法值记录 warning 并回到 `auto`，不让拼写错误静默启用双处理。
- 诊断例外：显式 DDA export request 或 `fpvdrone.acoustics.ddaExport` 即使处于
  clean 模式也可临时调度 snapshot/worker；结果不写回音频，只生成验证 bundle。
- 验证：纯策略测试覆盖无外部模组、自动 clean、显式覆盖和非法模式；
  `:fabric-mod:compileClientJava` 与定向 Fabric test 通过。
- 边界：当前 allowlist 只包含有权威 manifest 证据的 Sound Physics Remastered。
  不能因为模组名称含 “sound” 就自动禁用传播；新增 owner 必须先确认它确实处理
  普通 `SoundInstance` 的遮挡/混响。

## Sources

- [Fabric project structure and mod id](https://github.com/FabricMC/fabric-docs/blob/main/develop/getting-started/project-structure.md)
- [Sound Physics Remastered repository](https://github.com/henkelmax/sound-physics-remastered)
- [Sound Physics Remastered Fabric manifest](https://raw.githubusercontent.com/henkelmax/sound-physics-remastered/master/fabric/src/main/resources/fabric.mod.json)
