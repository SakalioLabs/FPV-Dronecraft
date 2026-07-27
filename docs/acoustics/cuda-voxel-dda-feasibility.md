# CUDA 体素 DDA 可行性与实验规范

日期：2026-07-25  
状态：**FP64 CUDA 原型源代码已实现但未编译；工具链缺失；不得宣称 GPU 加速已验证**

## 1. 结论

CUDA 可以实现 Minecraft 体素 DDA，但不适合作为当前直达声默认后端。

当前产品上限约为 6 架无人机 × 5 条 aperture rays，即约 30 rays/update。这个规模
远低于 GPU kernel launch、native boundary、同步和结果回读通常需要的摊销批量。
Java CPU Amanatides–Woo 因而继续作为规范实现和产品后端。CUDA 只针对未来稀疏早期
反射形成的批量 rays 做实验，首轮扫描从 `8192 rays/update` 开始；这个数字是实验
起点，不是已测出的 crossover，也不是产品常量。

当前仓库已有条件编译的 standalone FP64 correctness kernel、实编译 C++ CPU
reference runner 和 CMake capability gate，但本机没有 `nvcc`，所以 `.cu` 尚未被
CUDA 编译器接受，也没有任何 device execution。仓库仍没有 JNI/JNA bridge、CUDA
binding 或产品运行时后端。任何把现状描述成“CUDA 已接入”“device parity 已通过”
或“8192 条后 GPU 一定更快”的说法都是错误的。

离线 CPU parity contract 与 schema-v1 binary corpus 已开始实现：
[`DdaParityOracle`](../../computational-acoustics-core/src/main/java/com/tenicana/dronecraft/acoustics/voxel/DdaParityOracle.java)
记录逐 cell 坐标、物理段长、material id、首个非空气 cell、completion 和
truncation。它是 allocation-heavy diagnostic，不接入产品传播热路径。
`DdaParityCorpus`/`DdaParityCorpusCli` 已提供 deterministic adversarial/random
generator、big-endian streaming writer/reader、计数上限、disk round-trip 重算、
临时文件原子发布和 streaming SHA-256。
尚无已导出的 production Minecraft snapshot corpus、material-table version 或
CUDA consumer，因此 C1 仍是 **partial**。

`SparseMaterialSnapshot` 现提供懒计算 `diagnosticEntries()`/
`diagnosticSha256()`：只有离线导出或 shadow mode 主动调用时，才按 unsigned packed
cell 排序并哈希 completeness、packed coordinate、material UTF-8 id、三频带
transmission、三频带 absorption、scattering 和 fill fraction。正常产品
`sampleAt` 不触发排序或 SHA。这个接口给 production snapshot 稳定 identity，但尚未
把真实 Minecraft capture 写入 corpus。

## 2. 当前机器证据

2026-07-24 的只读探测：

```text
nvidia-smi: C:\Windows\system32\nvidia-smi.exe
GPU: NVIDIA GeForce RTX 3060
driver: 610.47
memory: 12288 MiB
compute capability: 8.6
nvcc: NOT_FOUND
```

因此这台机器可以运行兼容驱动的 CUDA 程序，但当前不能编译仓库内 CUDA 源码。
安装 CUDA Toolkit 是系统级变更，不由本研究分支静默执行。缺少 `nvcc` 时允许继续
CPU 基线、数据布局和 parity corpus 工作，不允许伪造 GPU 吞吐。

已有 CPU 证据是在 i7-14700KF 上对 8192-ray batch 测得约 `1764 ns/ray`。该结果
包含 traversal、material accumulation 和 result construction，但只报告均值，
且不是与 CUDA 同机同进程的 A/B；它只能作为历史基线，不能证明 crossover。

## 3. 规范语义

GPU 实验必须匹配
[`VoxelDda`](../../computational-acoustics-core/src/main/java/com/tenicana/dronecraft/acoustics/voxel/VoxelDda.java)
和 `DirectPathSolver` 的可观察语义：

- source/listener 所在 cell；
- 每轴 `tMax/tDelta` 推进和完全相等时的 tie policy；
- 访问 cell 顺序、总步数与 `MAX_DDA_CELLS` 截断；
- 首个非空气 cell；
- 每个材料段的长度；
- low/mid/high 三频带 transmission energy；
- reached-listener、truncated 和 invalid-input flags。

普通非边界 rays 要求访问 cell 序列完全一致。穿过 face/edge/corner、负坐标、零长度
和恰落整数坐标的 adversarial rays 必须单列 corpus；不能用 epsilon 偶然改变 CPU
tie policy。性能 kernel 若用 FP32，必须先相对 FP64/fixed-policy kernel 证明 cell
序列一致，否则停止，不以“声学误差很小”掩盖拓扑错误。

## 4. GPU 数据布局

首个 standalone 原型采用 SoA，不直接映射 Java object：

```text
RayBatch
  origin_x[], origin_y[], origin_z[]
  end_x[], end_y[], end_z[]
  maximum_cells[]

VoxelSnapshot
  chunk_page_table[]
  occupancy_bits[]          # 快速空气跳过
  material_id[]             # 紧凑 uint8/uint16
  material_band_table[]     # low/mid/high energy coefficients
  generation                # 与结果一起回传

RayResult
  first_hit_x/y/z[]
  visited_cells[]
  material_segments[]
  low/mid/high_gain[]
  flags[]
  snapshot_generation[]
```

Minecraft chunk snapshot 必须常驻 GPU。更新只上传 dirty chunk 的紧凑 delta；每帧重传
整个局部世界会使实验失去意义。page table 与 material buffer 使用双缓冲 generation，
worker 只能消费同一 generation 的结果。不得从 render thread 等待 CUDA event。

第一版不做“空区间宏跳跃”，因为它会同时改变算法和后端。先实现逐 cell parity；
只有正确性关闭后，才单独评估 brick/occupancy acceleration。

## 5. 必须测量的时间边界

禁止只报告 kernel 时间。每个 batch size `32/128/512/2048/8192/32768` 至少报告：

1. Java request flatten；
2. host buffer 填充；
3. H2D ray upload；
4. kernel；
5. D2H result readback；
6. Java result construction；
7. 完整 submit-to-result latency；
8. dirty chunk delta upload（0、1、8、32 chunks 四档）；
9. Minecraft render frame-time P50/P95/P99 变化。

每档先 warm-up，再记录至少 200 个 batch 的 P50/P95/P99、rays/s 和置信区间。CPU 与
CUDA 必须在同一机器、同一 corpus、同一 snapshot generation 上交错运行，避免温度、
频率和后台负载偏差。当前 18-cell Helmholtz runner 占用 CPU 时不得采集发布级 CPU
crossover 数据。

## 6. 正确性 gate

CUDA 进入性能比较前必须全部通过：

- deterministic random corpus 至少 100,000 rays；
- face/edge/corner/negative/zero-length adversarial corpus；
- visited cell count、first hit、flags 100% 匹配；
- 每段 material id 与长度匹配；
- 三频带 energy 相对/绝对误差 `<= 1e-5`；
- truncation 和 snapshot generation 100% 匹配；
- 相同输入重复 100 次 bit-stable，或明确记录仅浮点 energy 非 bit-stable；
- CPU reference 单元测试继续通过。

失败 ray 必须输出最小可复现输入，不得只计一个 aggregate mismatch rate。

## 7. 性能与产品 gate

以下是首轮工程假设 `[H]`，不是论文参数：

- 只有完整端到端 CUDA P95 相对 CPU 至少 `2×` 加速，才继续 native 集成；
- 8192-ray batch 的 submit-to-result P95 必须在 4 ms acoustic worker budget 内；
- dirty chunk delta 不得使同批 P95 超预算；
- 启用后 Minecraft render frame P99 回退不得超过 0.2 ms；
- 30-ray 当前直达声路径不得改走 CUDA；
- NVIDIA 不可用、driver/toolkit 不兼容或 native load 失败时，CPU fallback 必须产生
  同一契约结果，且游戏可启动。

若收益只存在于不含 JNI/回读的 kernel 时间、只存在于 32768 rays 但实际调度无法形成
该批量，或导致帧时间抖动，则拒绝 CUDA 产品后端。

## 8. 实施顺序

### C0 — 环境审计

```powershell
nvidia-smi --query-gpu=name,driver_version,memory.total,compute_cap --format=csv,noheader
nvcc --version
.\gradlew.bat :computational-acoustics-core:ddaBenchmark -Piterations=20
```

保存 OS、CPU、GPU、driver、toolkit、Java 和 Minecraft/LWJGL 版本。缺少 `nvcc`
时状态为 `toolchain-missing`，不是 benchmark failure。

### C1 — parity corpus

由 Java CPU reference 导出版本化二进制 corpus 和 expected results。格式包含
schema、seed、snapshot hash、tie policy 和 material table hash。先写 reader/
round-trip test，再写任何 CUDA kernel。

schema-v1 使用 big-endian，布局为：

```text
8 bytes magic "MCFDDAC1"
int32 schema=1
int32 material_algorithm_id=1
int32 tie_policy_id=1
int64 ray_seed
int64 material_seed
32 bytes material_table_sha256
int32 ray_count
repeated ray:
  int32 ray_id
  6 × float64 start/end
  int32 maximum_cells
  int32 segment_count
  int32 visited_cell_count
  uint8 flags (reached=1, stopped=2, truncated=4)
  3 × float64 expected low/mid/high loss_db
  3 × float64 expected low/mid/high energy_gain
  uint8 has_first_material
  optional 3 × int32 first_material_cell
  repeated segment:
    3 × int32 cell
    float64 length_m
    int32 material_id
```

tie policy id 1 与 Java 完全相同：`crossing=min(tMax)`，
`epsilon=max(1e-12, abs(crossing)*1e-12)`，所有满足
`abs(tMaxAxis-crossing)<=epsilon` 的轴同时推进。

material algorithm id 1 以 `uint32(x/y/z)` 分别乘
`0x9E3779B185EBCA87 / 0xC2B2AE3D27D4EB4F / 0x165667B19E3779F9`，
与 `material_seed` XOR 后执行标准 SplitMix64 finalizer；低 5 bit 非零为 air，
否则 material id 为 `1 + bits[5..7]`。这只是跨语言 synthetic parity field，
不是 Minecraft snapshot。

schema-v1 synthetic material table 固定 id `0..8`；每米 low/mid/high loss 分别为
`id×0.25 / id×0.75 / id×1.5 dB`，canonical big-endian table SHA-256 为
`e165344c00540668c2afcbec0ac617eddc14e4f37454b7a17cd68c867440b76d`。
每条 ray 的 expected loss 从 segment length 重算，energy gain 使用
`10^(-loss_db/10)`；CUDA 必须同时匹配 traversal 和三频带结果。

当前进度：oracle 4 个边界测试与 corpus 4 个 deterministic/round-trip/corruption/
signed-material 测试，加 1 个 in-memory/streaming byte-identical 测试已完成。
64 random + 8 adversarial smoke 为 `72` rays、
`8558` segments、`214464` bytes，disk round-trip 后逐 ray traversal/bands 重算
通过，SHA-256：
`991ad38338f86841905cd52223fe034aaf09ebf8734c03f5dd4282f69609bed4`。
streaming 与旧 in-memory writer 的输出逐字节相同。

Windows 正确命令必须引用含 `-v1` 的 `-P` 参数：

```powershell
.\gradlew.bat :computational-acoustics-core:ddaParityCorpus "-Poutput=build/research/dda-parity-v1.bin" "-PrandomRays=4096"
```

core 最新完整重跑 `BUILD SUCCESSFUL`，XML 为 `38` suites、`120` tests、
0 failures/errors/skipped。此前一次在 Helmholtz 并发负载下的 Gradle 命令曾在 XML
完成后、JVM 退出前超时，未作为正常终态证据；随后重跑已关闭该缺口。之后新增的
material-table identity、production bundle 与 fixture generator 已包含在这次完整
suite 中。仍缺真实 Minecraft capture 证据和 CUDA device reader/kernel，故 C1
保持 partial。
CLI 已改为 O(单 ray segments) 的 streaming I/O，
默认仍保守生成 `4096` random rays。

100k gate 已显式执行：

```text
random rays: 100000
adversarial rays: 8
total rays: 100008
segments: 12617210
bytes: 315354012
wall time: 7.483 s
round_trip: true
temporary file after publish: absent
SHA-256: 37532b76cc41fac515b08b86ecd19e5361a1b9eb5129f515c7f899948324344b
```

该文件位于 ignored `build/research/`，不提交 315 MB artifact。它关闭了 synthetic
CPU corpus 的 100k 规模与 heap 风险，但仍不是 CUDA parity 结果，也不包含真实
Minecraft chunk snapshot。

production snapshot hash 的代码契约已有定向验证：相同 cell/material 以不同插入
顺序构建时 SHA-256 相同；complete flag 或 fill fraction 改变时 hash 必须改变。
`SparseMaterialSnapshotTest` 4 项定向测试 `BUILD SUCCESSFUL`。

真实运行时材料身份现拆成两个正交字段：

- `minecraft_mapping_algorithm_version=1`：覆盖
  `BlockTags`、`SoundType`、fluid、collision fill 与 porous fallback 的分类规则；
- `material_table_schema=1` 与
  `material_table_sha256=8bed6d00ec4e433107ad44ad8178dc72fb4d6fb7633577c5cf9ea618f5ae84d8`：
  覆盖按 `air/foliage/wood/glass/stone/metal/water/soft` 固定顺序序列化的全部
  transmission、absorption 与 scattering 系数。

材料表 canonical serialization 为大端序：

```text
8 bytes magic "MCFMAT01"
int32 schema=1
int32 material_count=8
repeated material:
  int32 UTF-8 id byte_count
  byte[id byte_count] id
  3 × float64 transmission_loss_db_per_meter
  3 × float64 surface_absorption
  float64 scattering
```

材料表哈希按需惰性计算；正常 `sample()` 和 `sampleAt()` 热路径不计算 SHA。
`AcousticMaterialTest` 的 identity 定向测试与 `:fabric-mod:compileClientJava`
均为 `BUILD SUCCESSFUL`。这些系数仍是 `[H]` 待校准初值，稳定哈希只证明输入
可复现，不证明物理真实性。Minecraft tags 可被数据包改变；诊断适配器因此按当前
已加载 registry 的全部 block states 排序，记录 state descriptor 与最终分类 material
id，并连同 mapping version/material hash 生成 `MCFMAP01` 内容指纹。该遍历只在显式
导出时发生，不进入 capture 热路径。逐 cell 的 fluid/collision/fill 仍由已经解析
完成的 snapshot hash 覆盖。

下一步是把真实 capture 的 diagnostic entries、snapshot hash、上述两个材料身份、
环境 fingerprint 和 ray batch 一起导出，不能只把 hash 字符串附在 synthetic
corpus 上。

production bundle schema-v1 的 core writer/reader 已实现为
`DdaProductionSnapshotBundle`，大端布局为：

```text
8 bytes magic "MCFPDDA1"
int32 schema=1
int32 minecraft_mapping_algorithm_version
int32 material_table_schema
32 bytes material_table_sha256
int32 + UTF-8 bytes minecraft_version
int32 + UTF-8 bytes mod_version
32 bytes content_fingerprint_sha256
int64 snapshot_generation
uint8 snapshot_complete
32 bytes snapshot_sha256
int32 cell_count
repeated cell:
  uint64 Minecraft packed_cell, unsigned ascending
  int32 canonical_material_id
  float64 fill_fraction
int32 ray_count
repeated ray:
  6 × float64 start/end
  int32 maximum_cells
```

reader 会重新构建 `SparseMaterialSnapshot`、重算 snapshot hash，并拒绝 material
schema/hash 不一致、cell 非严格排序、非法 material/fill、非法计数、截断和尾随字节。
writer 也拒绝不在 canonical material table 中的运行时材料，避免 CUDA 收到无法解释
的隐式系数。负坐标往返、hash corruption、非法 UTF-8 与未知材料四项定向测试
`BUILD SUCCESSFUL`；atomic writer 使用同目录临时文件、优先 atomic move、Windows
不支持时回退 replace，并由测试确认无临时文件残留。真实
`MinecraftPropagationSnapshot` 可通过 diagnostic adapter 转换为包含上述语义内容
指纹和全部 probe-to-listener rays 的 bundle。

游戏内显式导出入口是 JVM property
`-Dfpvdrone.acoustics.ddaExport=<absolute-path>`。它默认不存在；设置后，acoustic
worker 等待第一个 complete material snapshot，离开 render thread 计算内容指纹并
只原子写出一次，成功/失败均记录日志，失败不改变声音传播结果。开发环境示例：

```powershell
$env:JAVA_TOOL_OPTIONS='-Dfpvdrone.acoustics.ddaExport=F:\Codex-Project\MCFPV\build\research\minecraft-dda-production-v1.bin'
.\gradlew.bat :fabric-mod:runClient
```

退出该 PowerShell 会话或运行
`Remove-Item Env:JAVA_TOOL_OPTIONS` 后恢复默认。尚未在真实游戏会话生成并 round-trip
一份 capture，且没有 CUDA reader，所以 C1 仍是 partial。

不重启游戏也可使用 Fabric 官方 client-command API：

```text
/fpvdrone-acoustics export-dda
/fpvdrone-acoustics export-status
```

`export-dda` 只原子登记一个带 revision 的 latest request，输出到当前游戏目录
`acoustic-diagnostics/dda-production-v1-<epoch-ms>.bin`，不接受任意路径参数且不覆盖
较早 capture；实际 registry
fingerprint、bundle 构造与磁盘写入仍在 acoustic worker。若 snapshot incomplete，
请求保持等待而不是发布残缺 fixture。`export-status` 返回 waiting/exported/failed
状态；revision gate 防止旧导出完成后覆盖更新请求的状态。该入口通过
`:fabric-mod:compileClientJava`，但尚需真实客户端交互证据。

为避免等到 CUDA toolchain 才发现协议歧义，仓库现在还有独立 Python schema-v1
reader：`verify_dda_production_bundle.py`。Java fixture generator 使用所有八种
canonical materials、正负坐标、零长度与跨多 cell rays 生成确定性文件；Python
不调用 Java 类，而是独立重建 material-table serialization、snapshot serialization、
unsigned packed-cell 顺序和所有 bounds。权威入口：

```powershell
.\gradlew.bat verifyDdaProductionBundleContract
```

当前 Java→Python 结果：

```text
bytes: 482
cells: 8
rays: 3
material table SHA-256:
  8bed6d00ec4e433107ad44ad8178dc72fb4d6fb7633577c5cf9ea618f5ae84d8
snapshot SHA-256:
  d7fbeb8e26df3c85bb937c91c59d993a55726310f18e5c22273a2ac85871a990
file SHA-256:
  f905c3039d4e0815f4ece5e1fc91aed9b6ab07ff907cdcc889da9fd3b159e9ff
status: valid
```

Python reader 的 6 项正负向测试覆盖 valid/unpack、material hash drift、非法 UTF-8、
unsigned cell order、trailing bytes 与 require-complete；全部轻量研究脚本现为
`35` tests，
`BUILD SUCCESSFUL`。fixture 位于 ignored `build/research/`，不作为二进制源文件
提交；hash 与 generator 才是受控契约。这个结果证明 Java 以外可以无歧义读取
schema，不证明 C++/CUDA 实现正确或更快。

本机虽无 CUDA Toolkit，但存在 Visual Studio 2022 MSVC `19.44.35213.0` 和 CMake
`3.31.8`，因此 C1 继续实现了无第三方依赖的 C++20 host reader，而没有编写无法编译
的 `.cu` 文件。reader 独立实现 big-endian primitives、严格 UTF-8、SHA-256、
canonical material table、snapshot 重建、packed coordinates、cells 与完整 ray
inputs；编译使用 MSVC `/W4 /WX /permissive-`。权威入口：

```powershell
.\gradlew.bat verifyNativeDdaProductionBundle
```

该任务按顺序生成 Java fixture、CMake configure、Release build 与 CTest。当前三项
CTest 全部通过：

1. SHA-256 `"abc"` 与 canonical material-table self-test；
2. C++ 读取并重算 Java fixture；
3. 独立 corruption harness 的 valid、material hash、malformed UTF-8、truncated、
   trailing 五条路径。

这证明普通 C++ host reader 在当前 MSVC 环境可编译且与 Java/Python 契约一致。
它仍不是 CUDA reader/kernel：设备端结构、H2D、kernel、D2H、parity 与 timing
全部尚未发生；`nvcc` 仍不存在。

真实或 fixture bundle 的三读入口已经统一：

```powershell
.\gradlew.bat verifyMinecraftDdaCapture "-Pbundle=<path-to-bundle>"
```

该 gate 强制 `snapshot_complete=true`，然后分别运行 Java reader、Python reader 与
Release C++ reader；任一 schema/hash/order/bounds 错误都会使 Gradle 失败。固定 Java
fixture 已通过该完整命令，三端报告同一 material/snapshot identity、`8` cells 与
`3` rays。真实游戏导出后必须直接运行同一命令，不能用 fixture 成功替代 live
capture 证据。

输入 bundle 只描述 snapshot/rays，不能单独证明未来 device traversal 正确。为保持
已固定 `MCFPDDA1` 文件 SHA 不变，Java CPU oracle 另写与输入 hash 绑定的
`MCFPREF1` schema-v1 expected-results sidecar：

```text
8 bytes magic "MCFPREF1"
int32 schema=1
32 bytes input_bundle_sha256
32 bytes snapshot_sha256
int32 ray_count
repeated ray:
  int32 ray_id
  int32 segment_count
  int32 visited_cell_count
  int32 material_cell_count
  uint8 flags (reached=1, stopped=2, truncated=4)
  3 × float64 expected loss_db
  3 × float64 expected energy_gain
  uint8 has_first_effective_material
  optional uint64 first_material_packed_cell
  repeated segment:
    uint64 packed_cell
    float64 geometric_length_m
    int32 canonical_material_id
    float64 fill_fraction
```

sidecar generator 逐 ray streaming 写出，不在内存保留整个 corpus；每条 trace 还与
现有 `DirectPathSolver` 的 visited/material counts、loss、gain 和 completion
交叉检查。input bundle 变化、snapshot hash 篡改或任一 ray/segment 变化都会使
复核失败。

权威完整准备命令为：

```powershell
.\gradlew.bat prepareMinecraftDdaParityOracle "-Pbundle=<path-to-live-bundle>"
```

它先由 Java/Python/C++ 三读 complete input，再生成 Java expected sidecar，最后由
Python 与实编译 C++ 各自重放 Amanatides–Woo traversal 和三频带累积。canonical
fixture 当前为 `231` segments、`6746` bytes，sidecar SHA-256：
`30c9e506c6adaa2dbf25fe603d43ec8db8c5505dc353938d437a0b7fd1d1c67c`。
Python/C++ 都得到相同结果；native corruption harness 现覆盖 9 条 input/sidecar
正常与损坏路径。完整 core 为 `38` suites、`120` tests，轻量 Python 为 `39`
tests，全部通过。

2026-07-24 又通过官方 Fabric Client GameTest 创建真实 integrated world、命令
启动穿越机、把听者后移 28 m，并构造 stone/glass/wood/water/foliage 五层屏障，
再从产品 acoustic worker 导出 live bundle。该文件为
`1053 bytes / 32 cells / 5 rays`，每条 `29.477–29.890 m` 射线都穿过五种
material 并从 chunk `(0,0)` 到 `(0,-2)`，file SHA-256
`bf009d1e497cc3e00ea946458a3edb7c140439d6060ef6e4de125dfb51cb0e9b`；
同一命令生成 `4902 bytes / 159 segments` 的 live sidecar，SHA-256
`0cef2a955523f348ca8ca9e844251f6269deef35170be10f58a1654637f893f7`，
并通过 Java/Python/C++ 全链。可重复入口为：

```powershell
.\gradlew.bat captureAndVerifyMinecraftDda --no-daemon
```

该结果关闭真实 host capture/parity gate，但小型 `5 rays`
multi-material/cross-chunk capture 不是 CUDA throughput corpus，也没有发生
device parsing、kernel execution 或 timing。

### C2 — standalone CUDA

独立命令行原型位于 `native/cuda-dda`，不连接 Minecraft：

- `mcfpv_dda_cpu_reference` 始终构建，复用严格 `MCFPDDA1` reader 与 CPU oracle；
- `mcfpv_dda_batch_plan` 始终构建，用同一 production bundle 计算有界连续批次；
- `mcfpv_dda_cuda` 只在 CMake 找到 CUDA compiler 时构建；
- FP64 kernel 对稀疏、unsigned-sorted packed cells 做 binary search，逐射线输出
  visited segments、首个有效材料、flags、三频带 loss/gain；
- host parity 对每个 segment 的 packed cell、material、fill、length 及 aggregate
  结果逐项比较，三频带容差固定为 `1e-5`；
- JSON 分开报告 parse、host flatten、H2D、kernel、D2H 与 submit-to-result 的
  P50/P95/P99。

权威入口：

```powershell
.\gradlew.bat --no-daemon verifyCudaDdaResearch
```

当前 MSVC CPU reference、host batch planner 与 Java fixture/expected sidecar 的
原生测试通过；
200-batch tiny fixture 的 CPU P95 为 `0.0036 ms/batch`、`1200 ns/ray`。它只有
`3 rays / 8 cells`，不能用于 crossover。CMake 明确输出
`CUDA compiler - NOTFOUND`，因此 CUDA CTest 不存在，device parity/timing 仍未发生。
静态 source-contract verifier 只防止源码丢失 tie policy、逐段比较、分阶段计时、
有界规划与多批 fail-closed gate；
它也明确输出 `cuda_compiled=false`、`cuda_executed=false`。

D121r 冻结默认 `8192 rays / 1,048,576 segments`，32-byte segment 布局对应
32 MiB segment budget。普通 C++ planner 已用 production fixture 验证
ray-limit、segment-limit、单射线超限与 overflow。
详见
[`decision-D121r-cuda-dda-bounded-batch-gate.md`](decision-D121r-cuda-dda-bounded-batch-gate.md)。

D121s 已生成 current production-bundle 格式的 `100,008 rays / 49,494 cells`
corpus 与 Java expected-results；Java、Python、compiled C++20 reader 全部一致。
默认规划为 `24 batches / peak 1,048,497 segments / 33,551,904 bytes`，而无界
single-batch segment reservation 为 `799,190,304 bytes`。`.cu` 已实现 planned
multi-batch loop、逐批 offset rebase、逐批 CPU oracle parity 与完整 corpus-pass
timing aggregation。无 `nvcc` 环境下，host translation-unit 以 MSVC
`/W4 /WX /permissive-` 通过，但这不是 CUDA 编译或执行证据。详见
[`decision-D121s-cuda-multi-batch-corpus.md`](decision-D121s-cuda-multi-batch-corpus.md)。

D121t 在同一 bundle/sidecar 上执行三轮 compiled C++20 CPU correctness
baseline，每轮 `5 warmup / 30 measured` full-corpus batches。P95
min/median/max 为 `1050.2913 / 1206.2000 / 1210.3220 ms`，median throughput
约 `82,912 rays/s`。该执行器单线程并保留全部 visited segments，因此只作为未来
CUDA correctness workload 的同负载对照，不代表产品 Java 热路径或优化 CPU 上限。
详见
[`decision-D121t-cuda-dda-cpu-corpus-baseline.md`](decision-D121t-cuda-dda-cpu-corpus-baseline.md)。

D121u 已用 NVIDIA 官方 CUDA Python/NVRTC wheel 绕过完整 Toolkit/`nvcc` 缺失，
在本机 RTX 3060 上真实编译、加载并执行 device-only FP64 DDA kernel。canonical
fixture 的 `3 rays / 231 segments` 已逐段与 Python CPU oracle 一致；精确状态为
`nvrtc_compiled=true / cuda_executed=true / nvcc_compiled=false`。tiny fixture
timing 不能用于 crossover，详见
[`decision-D121u-nvrtc-cuda-dda-fixture-parity.md`](decision-D121u-nvrtc-cuda-dda-fixture-parity.md)。

D121v 已用这条路径对同一 100,008-ray corpus 完成三轮 device parity 与
3×(5 warmup + 30 measured) benchmark。kernel P95 median `44.194 ms`，D2H P95
median `155.607 ms`，Python submit P95 median `373.223 ms`。相对 D121t CPU
correctness P95 median 的研究 workload 比值为 `3.232×`；但同步 submit 仍为 50 ms
tick 的约 7.46 倍。8k prefix 已基本达到端到端吞吐平台，主要瓶颈是每 pass 回读
799 MB reserved segment records，而不是缺少更多 rays。详见
[`decision-D121v-rtx3060-cuda-dda-production-corpus.md`](decision-D121v-rtx3060-cuda-dda-production-corpus.md)。

D121w 已实现编译期 aggregate-only specialization。它保持 counts、flags、first
material 与三频带一致，同时把 segment buffer 和 799 MB reserved-segment D2H
归零；D2H P95 median 从 `155.607` 降至 `5.847 ms`。但共享 WDDM 环境出现
约 9.3 GiB 显存占用和一个 `130.100 ms` kernel P95 离群，Python submit P95
三轮也未稳定改善。因此只关闭输出传输风险，不晋升端到端性能。详见
[`decision-D121w-cuda-dda-aggregate-output-ablation.md`](decision-D121w-cuda-dda-aggregate-output-ablation.md)。

D121x 已补相同 8k→100k prefixes 的 compiled CPU full-topology baseline。
CPU P95 median 为 `64.074/133.308/268.599/532.197/813.781 ms`，GPU submit
筛查比值在全部五档为 `2.18×–2.43×`，因此 crossover 低于 8192 rays、尚未定位。
新 100k CPU 结果比 D121t 快约 32.5%，强调必须 paired。详见
[`decision-D121x-cpu-gpu-dda-prefix-crossover.md`](decision-D121x-cpu-gpu-dda-prefix-crossover.md)。

D121y 已补 `128..8192` CPU floor，P95 median 为
`1.279/2.540/4.551/11.206/25.410/49.598/85.865 ms`，并实现 measured passes
外的一次性 host ray preparation。GPU 端因外部训练后的驱动管理查询持续超时而未
运行；该实现目前只有纯主机合同，不冒充 device timing。详见
[`decision-D121y-small-prefix-cpu-floor-and-host-preparation.md`](decision-D121y-small-prefix-cpu-floor-and-host-preparation.md)。

下一步必须先恢复 device smoke，再做 `128..8192` 同 context、预展平 buffer 的
paired full/aggregate A/B。

D121z 已加入 device init 前的有界驱动预检：默认 10 秒，超时、非零退出或无设备
输出均 fail closed；依赖环境检查不再调用 `cuInit`。驱动恢复后，
`host-preparation=once` 已在 canonical fixture 上真实执行 full/aggregate，
两者均通过 `3 rays / 231 implicit segments` aggregate parity，full 另通过逐段
topology。该 smoke 关闭 implementation gate，不构成小批量性能结论。详见
[`decision-D121z-bounded-driver-preflight-and-host-prepared-device-smoke.md`](decision-D121z-bounded-driver-preflight-and-host-prepared-device-smoke.md)。

下一步是把两种 kernel 放进同一 CUDA context，按 trial 交替执行
`128..8192`，并在相同桌面时间窗重跑 compiled CPU prefixes；在此之前仍不得冻结
最小 CUDA offload batch。

D121aa 已把独立进程 A/B 替换为真正的同 context paired runner。full 与
aggregate 复用同一 module、resident inputs、预展平 rays、events 与 allocations，
并逐 pass 交替顺序。对 `128..8192` 的 3×(5+30) 正式矩阵中，full topology 与
aggregate 数值 parity 全部通过，CPU/GPU 也在相同时间窗按 trial 交替相邻运行。

按连续三档的 P95、P99 与 50 ms 零 miss gate，aggregate 最小研究 batch 为
`256 rays`，full-debug 为 `512 rays`。这只关闭 CUDA executor crossover，不关闭
Java/native 边界：实验后段共享 GPU 利用率最高 90%，且尚未计入 direct buffer
packing、JNI/JNA/Driver API 调用、resident cell updates 或 Minecraft scheduling。
详见
[`decision-D121aa-same-context-paired-cuda-dda-crossover.md`](decision-D121aa-same-context-paired-cuda-dda-crossover.md)。

D121ab 用项目已有 LWJGL 3.3.3 建立了独立 direct-buffer/C-ABI boundary floor。
64-byte rays 与 72-byte aggregate results 在 Java/C++ 两端均由 offsets/size
合同冻结；3×(100 warmup + 1000 measured) 的 256→2048 total P95 median 仅
`0.012–0.048 ms`。这说明 plumbing 下限没有单独耗尽 256-ray margin，但 probe
不链接 CUDA、不遍历体素，也不测 resident snapshot、driver lifecycle 或
Minecraft scheduling，所以不得与 D121aa 相加后直接批准 shadow mode。

Gradle 当前运行在 JDK 25.0.1，LWJGL 3.3.3 给出 unsupported JNI version 警告；
正式 CUDA bridge 必须在实际支持的 Java 21 runtime 复测。详见
[`decision-D121ab-lwjgl-native-boundary-floor.md`](decision-D121ab-lwjgl-native-boundary-floor.md)。
旧
`dda-parity-100k-v1.bin` 的 magic 不属于当前 production bundle，仍不能作为
CUDA executor 证据。FP32/SoA 性能 kernel 只能在 FP64 correctness gate 关闭后
实现。此阶段不得修改 Fabric audio backend。

### C3 — native boundary

只有 C2 通过才比较 JNI/JNA、CUDA Driver API 与 CUDA–OpenGL interop。当前 LWJGL
依赖没有 CUDA binding；新增 native 产物必须覆盖 Windows/Linux/macOS CPU fallback，
并明确 NVIDIA-only backend 的打包与许可证。

### C4 — Minecraft shadow mode

同一 request 同时运行 CPU 与 CUDA，音频仍消费 CPU，只记录 parity、latency 和
frame time。连续运行无 mismatch 后才能做 capability-gated A/B；永不删除 CPU
reference。

## 9. OpenGL compute 对照

Minecraft 已有 OpenGL context，compute shader 可能减少额外 CUDA runtime 打包，
但会直接与渲染争用 GPU，且 buffer/context 生命周期受游戏版本影响。它必须使用同一
SoA、snapshot、corpus 和端到端指标与 CUDA 对照，不能用另一套简化算法。若 CUDA
只比 OpenGL compute 的 kernel 快、却在 native boundary 后更慢，则优先跨厂商路径。

## 10. 当前决策

- **已采用**：Java CPU DDA，用于当前直达声与遮挡。
- **100k device path 已验证**：RTX 3060 上的 FP64 NVRTC kernel 已真实执行，
  canonical fixture 与 100,008-ray production corpus 已逐段通过；完整 CMake/
  `nvcc` target 仍未构建。
- **已实现的主机与源码安全门**：确定性有界 batch planner；默认 8192 rays、
  1,048,576 segments；current production-format 100k corpus/sidecar；
  CUDA bounded multi-batch source；无 CUDA toolchain 的 host typecheck。
- **未实现**：同前缀 CPU crossover、aggregate/compacted output、native bridge、
  resident chunk delta snapshot；
  production bundle 已有 CPU writer/reader、Minecraft capture adapter 与语义内容
  指纹、JVM property/client command 两个 opt-in 游戏内原子导出入口、独立 Python
  reader、实编译 C++20 host reader、真实 Client GameTest capture 与条件编译 CUDA
  consumer source。
- **禁止**：把 8192 当已测 crossover；把 CPU benchmark 当 GPU 对照；在约 30 条
  直达 rays 上引入 CUDA；缺少 parity 时用听感接受拓扑不一致。
- **重新打开条件**：早期反射调度实际稳定产生 `>=8192` rays/update，CUDA Toolkit
  可用，并有人按 C0–C4 完整执行端到端实验。
