# D064 — live DDA gate 必须由真实集成客户端自动生成并跨语言重放

- 状态：accepted and implemented（2026-07-24）。
- 目的：关闭“只有人工命令入口和 synthetic fixture，没有真实 Minecraft
  registry/chunk/client-worker 产物”的证据缺口。

## 官方接入边界

实现使用 Fabric API 的
[`fabric-client-gametest-api-v1`](https://github.com/FabricMC/fabric-api/tree/1.21.11/fabric-client-gametest-api-v1)
和其
[`ClientGameTestTest` 官方示例](https://github.com/FabricMC/fabric-api/blob/1.21.11/fabric-client-gametest-api-v1/src/testmodClient/java/net/fabricmc/fabric/test/client/gametest/ClientGameTestTest.java)。
测试创建真正的 integrated singleplayer server/client，不构造假的 `ClientLevel`、
block registry 或 chunk snapshot。

`MinecraftDdaCaptureClientGameTest` 执行以下闭环：

1. 创建并等待单人世界 chunk 下载；
2. 以真实玩家执行 `fpvdrone spawn racing_quad`；
3. 把玩家沿声源反方向移动 `28 m`，依次构造 `5×4` 的 stone、glass、
   oak-planks、water、oak-leaves 五层屏障；
4. 执行 `fpvdrone diagnostic start 6`，等待客户端实体平均电机转速超过
   `1000 rpm`；
5. 通过正常 `DroneSoundManager` / acoustic worker 路径请求导出；
6. 等待原子文件出现，并在客户端进程内重新读取，要求 complete snapshot、
   Minecraft `1.21.11`、至少 `25 m` 的射线、跨 chunk，并同时包含 canonical
   stone/glass/wood/water/foliage。

这条路径没有在 render thread 遍历全 registry 或运行 DDA；Client GameTest 只登记
诊断请求，实际 snapshot/ray bundle 仍由产品声学 worker 生成。

## 固定运行入口

```powershell
.\gradlew.bat captureAndVerifyMinecraftDda --no-daemon
```

根任务先运行 `:fabric-mod:runClientGameTest`，然后在独立 Gradle build 中对固定输出
`build/research/minecraft-dda-client-gametest-v1.bin` 执行
`prepareMinecraftDdaParityOracle`。后半段要求：

- Java、独立 Python 和实编译 C++ 三读同一 complete `MCFPDDA1`；
- Java CPU reference 生成与 input/snapshot SHA 绑定的 `MCFPREF1`；
- Python 和 C++ 独立重放 Amanatides–Woo tie policy、material lookup、
  fill length 与三频带累积。

## 2026-07-24 实测证据

Client GameTest 在 Minecraft `1.21.11`、Fabric Loader `0.19.3`、Fabric API
`0.141.4+1.21.11` 上通过。日志确认 integrated server 启动、玩家进入、穿越机
spawn、玩家后移、五次各 20 个方块写入、6 秒诊断开始，并由
`fpvdrone-acoustic-propagation` worker 导出文件。

当前 canonical multi-material/cross-chunk bundle：

- `1053 bytes`
- `32 cells`
- `5 rays`
- 射线长度 `29.477–29.890 m`
- 每条射线从 chunk `(0,0)` 到 `(0,-2)`
- material set `air + foliage + glass + stone + water + wood`
- 每条射线恰有五个有效 material cells
- 三频带损失范围约
  `25.328–25.329 / 60.267–60.269 / 98.108–98.112 dB`
- snapshot generation `44`
- complete `true`
- material table SHA-256
  `8bed6d00ec4e433107ad44ad8178dc72fb4d6fb7633577c5cf9ea618f5ae84d8`
- snapshot SHA-256
  `1b63012f90ebee73f85daa32606f4082eb5c4ca755ba1e34eff3ffeaf2c2c098`
- file SHA-256
  `bf009d1e497cc3e00ea946458a3edb7c140439d6060ef6e4de125dfb51cb0e9b`

当前 expected-results sidecar：

- `4902 bytes`
- `5 rays`
- `159 segments`
- SHA-256
  `0cef2a955523f348ca8ca9e844251f6269deef35170be10f58a1654637f893f7`

`verifyMinecraftDdaCapture` 与 `prepareMinecraftDdaParityOracle` 都为
`BUILD SUCCESSFUL`；Java/Python/C++ 报告完全相同的 identity、cell/ray 数量，
Python/C++ 又接受同一个 Java CPU sidecar。

在加入多层场景之前，短距离和单层石墙 capture 也分别通过；不同随机世界的出生位置
和 snapshot generation 会改变文件 hash。因此该测试保证场景不变量、结构与算法
parity，不要求不同随机世界产生相同文件 hash。当前 Java 内置断言使未来退化到
短于 `25 m`、没有任一种目标材料或不跨 chunk 的 capture 直接失败。

## 结论与边界

该证据关闭 D040/D043/D044/D045 的 live CPU capture/parity gate：production
adapter 能从真实游戏状态生成 portable bundle，三种 host 实现也对同一输入达成
一致。

它不证明以下事项：

- `32 cells / 5 rays` 不是 CUDA throughput corpus；
- CPU cross-language parity 不等于 CUDA device reader 或 kernel parity；
- 尚未测量 H2D、kernel、D2H、端到端延迟或 crossover；
- 当前五层平面验证分类、透射累积与 chunk 边界，不替代洞口绕射、部分 aperture
  遮挡、斜射、复杂碰撞 shape 或大规模 resident-chunk corpus；
- 客户端日志中的既存 `drone.png` PNG chunk 警告没有中止测试或声学导出，但应作为
  独立渲染资产问题处理，不能混入声学成功声明。

因此 CUDA 状态仍保持 D063 的 `toolchain-missing`，四个 device/speedup 声明仍为
`false`。下一步可扩展洞口/部分遮挡 live corpus；安装可用 CUDA Toolkit 后，
才进入 device reader/kernel 和 C1 parity。
