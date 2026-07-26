# D069 — CUDA 源码存在、成功编译与 device parity 必须分别记账

日期：2026-07-25  
状态：**conditional CUDA source implemented；device build/execution pending**

## 决策

`native/cuda-dda` 采用两个明确分离的 executable：

1. `mcfpv_dda_cpu_reference` 始终用 C++20 构建，复用严格 production bundle reader
   和规范 CPU trace；
2. `mcfpv_dda_cuda` 只有在 CMake `check_language(CUDA)` 找到编译器时才进入构建图。

缺少 `nvcc` 时配置成功但打印 `CUDA compiler unavailable`；CPU fixture gate 继续
执行，CUDA test 不会被伪造为 skipped-pass。静态 source verifier 输出
`cuda_compiled=false` 和 `cuda_executed=false`，不能作为 device 证据。

## FP64 correctness kernel 契约

第一版 GPU kernel 故意不做 brick skipping、occupancy macro-step、FP32 或产品 bridge：

- 输入是已由 host 严格验证的 `MCFPDDA1` cell/ray SoA；
- sparse cells 按 Minecraft unsigned packed coordinate binary search；
- 与 Java/C++ oracle 相同的 floor、`tMax/tDelta` 和 simultaneous-axis epsilon policy；
- 每条 ray 保留最大 cell 数与独立 segment offset；
- 输出每个 visited segment 的 packed cell、几何长度、material id、fill fraction；
- 输出 reached/stopped/truncated、首个有效材料、material-cell count 与三频带结果；
- host 对 topology/segments 做精确比较，对 band loss/gain 使用 `<=1e-5` gate；
- H2D、kernel、D2H 与完整 submit-to-result 分开用 CUDA events/host clock 计时。

该 correctness layout 会按 `sum(maximum_cells)` 分配 segment buffer，目的是保留完整
失败证据，不是最终大批量性能布局。100k corpus 的后续 runner 必须分块或使用
two-pass count/prefix-sum，不能无界分配。

## 当前实证

权威命令：

```powershell
.\gradlew.bat --no-daemon verifyCudaDdaResearch
```

当前机器：

- MSVC `19.44` 实编译 CPU reference；
- canonical Java fixture：`8 cells / 3 rays / 231 segments`；
- `MCFPREF1` sidecar verification 通过；
- CPU runner 2 项 CTest 全部通过；
- 20 warmup + 200 measured batches：P50 `0.0035 ms`、P95 `0.0036 ms`、
  P99 `0.0037 ms`，P95 `1200 ns/ray`；
- CMake：`CUDA compiler - NOTFOUND`；
- CUDA 编译次数 `0`、device execution 次数 `0`、device parity `false`、
  speedup claim `false`。

tiny fixture timing 只证明 runner 能稳定执行，不代表 8192-ray crossover。

## 下一 gate

安装 CUDA Toolkit 属于机器级变更，当前工作没有静默执行。工具链可用后必须依次：

1. 同一 Gradle 入口成功生成 `mcfpv_dda_cuda`；
2. canonical fixture 的逐 segment device parity 通过；
3. adversarial + 100,008-ray corpus 100% topology/flags/first-hit 匹配；
4. 相同输入重复 100 次，记录 bit stability；
5. 才运行 `32…32768` batch 的交错 CPU/CUDA P50/P95/P99；
6. 完整端到端 P95 未达到 `2×` 或 8192 rays 超过 `4 ms` 就停止 C3。

在这些证据出现前，Java CPU DDA 仍是唯一产品后端，Fabric 音频路径不引用任何 CUDA
产物。
