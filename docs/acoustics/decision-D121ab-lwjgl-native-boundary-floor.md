# D121ab — LWJGL/C ABI native boundary 成本下限

日期：2026-07-27

状态：`native-boundary-floor-verified / cuda-bridge-not-implemented /
minecraft-shadow-mode-deferred`

## 决策

当前项目的最小研究边界采用：

```text
fabric-mod client research CLI
  → Minecraft 已提供的 LWJGL 3.3.3 Library/SharedLibrary/JNI
  → direct ByteBuffer address
  → 独立、窄 C ABI shared library
```

不在 `computational-acoustics-core` 引入 LWJGL；不新增 JNA；不启用 Java 21 preview
FFM。原因是：

- 项目固定 Java 21 bytecode，core dependency test 明确禁止 `org.lwjgl`；
- Minecraft client runtime 已提供 LWJGL 3.3.3 及平台 native；
- JNA 会新增 runtime/打包面，并不能证明 hot-path 优势；
- Java 21 FFM 仍需要 preview 选项，不适合当前 release boundary；
- LWJGL function-pointer path 可在不生成 JNI headers 的情况下验证 direct-buffer
  ABI，但正式产品仍必须 pin Minecraft/LWJGL/JVM 兼容矩阵。

本轮实现的是 **C ABI 成本下限**，不是 CUDA bridge。native probe 不链接 CUDA，
也不执行 DDA；它只消费与 runner 一致形状的 64-byte ray records，写出 72-byte
aggregate result records，并让 Java 分别测量 pack、native call、unpack 和 total。

## 实现合同

- `native/cuda-dda-boundary`：C++20 shared library；
- `mcfpv_dda_boundary_abi_version()`：显式 ABI version 1；
- `mcfpv_dda_boundary_aggregate()`：`Ray* + Result* + uint32 ray_count`；
- C++ `static_assert` 冻结 64/72-byte size 及关键 offsets；
- Java 使用 off-heap direct buffers、native byte order、`MemoryUtil.memAddress` 与
  `JNI.invokePPI`；
- 输出明确声明
  `cuda_executed=false / minecraft_started=false /
  physical_endpoint_opened=false`；
- checksum 随 pass 写入变化且三轮一致，防止把未消费结果误当有效 benchmark；
- CMake MSVC `/W4 /WX`，非 MSVC `-Wall -Wextra -Wpedantic -Werror`；
- Windows/Linux/macOS boundary probe 均有 library filename 分支；macOS 只用于
  C ABI probe，不改变 CUDA 无 macOS backend 的事实。

## 正式测量

环境：

- Windows 11；
- MSVC 19.44 Release C++20；
- Gradle runtime：Microsoft OpenJDK 25.0.1；
- compiled bytecode target：Java 21；
- LWJGL 3.3.3；
- 每档 3 个独立 JVM；
- 每 JVM `100 warmup + 1000 measured`。

三轮 P95/P99 median：

| rays | pack P95 | C ABI call P95 | unpack P95 | total P95 | total P99 |
|---:|---:|---:|---:|---:|---:|
| 256 | 0.0076 ms | 0.0067 ms | 0.0035 ms | 0.0231 ms | 0.0626 ms |
| 512 | 0.0009 ms | 0.0095 ms | 0.0007 ms | 0.0123 ms | 0.0327 ms |
| 1,024 | 0.0020 ms | 0.0218 ms | 0.0012 ms | 0.0251 ms | 0.0267 ms |
| 2,048 | 0.0038 ms | 0.0408 ms | 0.0029 ms | 0.0483 ms | 0.0569 ms |

256-ray pack 的 P95 高于 512-ray 档，说明微秒级结果仍受 JIT、timer 与 shared
desktop scheduling 影响；因此只保留三轮 median 和全部 raw samples，不拟合线性
常数。

忽略的正式报告：

- run 1：129,300 bytes，SHA-256
  `0165b6889aefa9180c2ef7cb99b77e94f95fd7bc83aacd60cddb418e69ac7851`
- run 2：130,106 bytes，SHA-256
  `da3719fb5b7a95875bf544d20ce569e6b49e4f9d9453cc5608f32c982cfcb647`
- run 3：129,369 bytes，SHA-256
  `7d16f28619be77370ca2e45b9b6551c8570a72af62e32590f1642eafd9143331`
- 三轮 checksum：
  `40641514778762028`

## 与 D121aa 的关系

同档 boundary total P95 约为 paired aggregate CUDA submit P95 的
`0.9%–2.5%`。简单相加后，256-ray screening 仍约为
`1.157 + 0.023 = 1.180 ms`，低于同时间窗 CPU P95 `1.518 ms`。

这只能说明 direct-buffer/C-ABI plumbing 本身没有立刻耗尽 256-ray 的约
0.361 ms margin；不能证明真实 CUDA bridge 仍保留 margin。尚未计入：

- native 内 CUDA Driver API/NVRTC lifecycle；
- Java record 到真正 CUDA SoA/ABI 的最终映射；
- resident 49,494-cell snapshot 建立和 chunk delta updates；
- async completion/fence 与工作线程 handoff；
- WDDM context contention；
- Minecraft frame/tick scheduling；
- native binary 提取、签名、版本和 unload；
- Windows/Linux NVIDIA driver matrix。

## JVM/LWJGL 风险

JDK 25 下，`--enable-native-access=ALL-UNNAMED` 可消除 `System.load` restricted
warning，但 LWJGL 3.3.3 仍打印：

```text
[LWJGL] [ThreadLocalUtil] Unsupported JNI version detected
```

三轮与 smoke 均成功完成，不能据此宣称 JDK 25 产品安全。正式 Minecraft gate
必须在实际支持的 Java 21 runtime 复测；未来若产品允许更新 JVM，还必须随
Minecraft 提供的 LWJGL 版本重新验证。这个警告也是不把研究 CLI 直接升格为产品
bridge 的原因。

## 下一步

D121ac 应实现一个 Minecraft 外、但真实调用 CUDA 的 native aggregate bridge：

1. C ABI 内动态加载 NVIDIA Driver API，失败时不影响 CPU fallback；
2. context/module/cells/transmission 常驻；
3. Java 每次只提交预展平 rays 与读取 72-byte aggregate results；
4. 对 256/512/1024/2048 做 3×(5+30) 完整
   pack→H2D→kernel→D2H→unpack；
5. Java 21 与 Windows RTX 3060 先关闭 gate，再设计 Linux NVIDIA 打包；
6. 全程保持 Minecraft 未启动，直到连续三档 parity/performance gate 通过；
7. 首个 Minecraft 阶段只能 shadow mode，音频仍消费 Java CPU 输出。

本轮未启动 Minecraft，未访问 playback/capture endpoint，也未把 native probe
接入 production runtime。
