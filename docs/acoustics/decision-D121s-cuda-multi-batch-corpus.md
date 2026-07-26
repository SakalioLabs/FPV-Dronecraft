# D121s — 100k 生产语料与 CUDA 有界多批执行器

日期：2026-07-27
状态：`corpus-verified / multi-batch-implemented / cuda-uncompiled / cuda-unexecuted`

## 决策

D121r 只关闭了无界分配风险，但把整个 100k 语料挡在门外：CUDA executor 只实现
单批，规划出多批即拒绝。本轮补齐两件事，使 CUDA 路径真正可执行。

### 1. 当前 production-bundle 格式的 100k 语料

历史 `dda-parity-100k-v1.bin` 属于 `MCFDDAC1` parity 格式，magic 与当前
`MCFPDDA1` production bundle 不同，直接喂给 production reader 会正确报
`invalid production bundle magic`。因此新增
`DdaProductionCorpusCli` 按当前格式生成确定性语料。

场景是一个封闭合成房间：石质地面、木质天花板、被玻璃窗带穿透的石墙、金属立柱、
部分填充的水池、散布植被与软性挡板。射线取 10 个发射点的球面斐波那契扇形，
并保留 8 条对抗射线（零长度、轴对齐正负向、对角、面对齐、竖直、截断、反向）。
所有浮点运算使用 `StrictMath`，坐标量化到 1/256，保证跨平台字节一致。

实测规模：

| 指标 | 值 |
|---|---:|
| rays | 100,008 |
| cells | 49,494 |
| total segment capacity | 24,974,697 |
| bundle bytes | 6,190,460 |
| expected-results bytes | 404,194,220 |
| 实际 traced segments | 14,172,009 |

三个独立实现读取同一文件并给出一致哈希：

- Java `DdaProductionSnapshotBundleVerifyCli`；
- Python `verify_dda_production_bundle.py`；
- 编译后的 C++20 `mcfpv_dda_bundle_verify`。

`snapshot_sha256` 一致为
`7952f0f752371a2d72149312b634312d666c0909961f78237f1c528f10d26a9d`，
`file_sha256` 为
`83d73cdc86d90dd5dbe3ed049c92c8bd226679f4612b9d6811399cba7b89bcf2`。
expected-results 由 Java 生成后，C++ 与 Python 独立复算一致，
`expected_results_sha256` 为
`5f8f538be276f3efdeafedfe0601982a5dc1fb044dd1bf45b19a8ae400a7994d`。
Java 侧每条射线额外与 `DirectPathSolver` 交叉校验。

### 2. CUDA 有界多批执行器

`dda_cuda.cu` 不再拒绝多批语料，而是执行完整 plan：

- device buffer 按 `peak_batch_rays` / `peak_batch_segments` 分配，
  而不是整个语料的总量；
- 每批把 `segment_offset` 重新基于该批起点，避免跨批越界；
- 每批 D2H 后立即与同一 CPU oracle 比较 topology、materials、flags、
  first hit 与三频带；
- 计时按“整个 corpus pass 内所有批次求和”上报，并显式输出
  `timing_scope`，防止把单批当成一次完整更新；
- `checked_bytes` 覆盖所有分配的字节计算。

10 万射线语料的实际规划结果：

| 显存预算 | batch_count | peak segments | peak bytes |
|---|---:|---:|---:|
| 默认 32 MiB | 24 | 1,048,497 | 33,551,904 |
| 128 MiB | 13 | 2,045,880 | 65,468,160 |
| 不限（单批） | 1 | 24,974,697 | 799,190,304 |

也就是说，原先的单次分配需要约 762 MiB segment buffer；默认有界批处理把峰值
降到约 32 MiB，降幅约 23.8 倍，使该语料可在 RTX 3060 上执行。

## 无 CUDA 工具链下的主机类型检查

本机没有 `nvcc`，`.cu` 无法编译，多批循环存在“改坏了也发现不了”的风险。
新增 `docs/scripts/typecheck_cuda_dda_host_logic.py`：把 device 专有语法改写为
主机 translation unit，用普通 C++ 编译器以 `/W4 /WX /permissive-` 编译。

- 当前结果：`host_typecheck=true`，零警告；
- 负控：在多批区域注入一个未声明标识符后，编译如期失败，证明该门确实覆盖新代码；
- 报告显式声明 `cuda_compiled=false`、`cuda_executed=false`。

该脚本已接入 `buildCudaDdaResearch`，并在缺少任何 C++ 编译器时输出
`skipped` 而非伪装通过。

## 回归

- `computational-acoustics-core`、`two-edge-wave-reference`、`drone-sim-core`、
  `fabric-mod` Gradle 测试全部通过；
- 新增 `DdaProductionCorpusCliTest` 8 项通过，覆盖确定性、字节级可复现、
  严格 reader round-trip、扇形来源、混合截断预算、材质覆盖与非法射线数拒绝；
- native CTest `4/4`；batch planner self-test 扩到 8 例，新增“每条射线恰好
  规划一次且 rebased offset 等于批次段数”的不变量；
- 静态 CUDA source contract：28 个 CUDA token、8 个 CMake token，缺失 0。

完整可重复入口：

```powershell
.\gradlew.bat --no-daemon verifyCudaDdaProductionCorpus
```

该入口生成 corpus/sidecar，运行 Python 与 compiled C++20 独立验证、native
regression、CUDA host typecheck 和 batch planner。大文件只写入已忽略的
`build/research`。

## 证据边界

本轮仍未发生 CUDA 编译或执行：

- `cuda_compiled=false`、`cuda_executed=false`；
- 未测 H2D/kernel/D2H 实际延迟，表中 batch 数与字节数均为主机端确定性计算；
- 未证明 device parity 或吞吐，未与 CPU 建立 crossover；
- 未接入 Fabric/Minecraft audio thread，未启动 client，未打开任何端点。

主机类型检查不是 CUDA 编译证据。多批执行器的正确性目前由 CPU oracle 语义、
主机类型检查与规划器不变量支撑，真实 device parity 仍需在具备 `nvcc` 的机器上
运行 `verifyCudaDdaResearch` 才能确认。

## 下一步

1. 在具备 CUDA Toolkit 的机器上用同一入口构建并运行 100k 语料，逐批 parity；
2. 通过后运行 `8192/16384/32768/65536/100008` batch-size 性能矩阵；
3. 仅当早期反射调度稳定产生 `>=8192` rays/update 且端到端优于 CPU 时，才考虑
   产品路径迁移；否则保留 Java CPU DDA。
