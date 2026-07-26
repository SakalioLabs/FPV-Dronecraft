# D121u — RTX 3060 NVRTC CUDA DDA 夹具一致性通过

日期：2026-07-27

状态：`nvrtc-compiled / cuda-executed / fixture-parity-verified /
nvcc-unavailable`

## 决策

本机没有完整 CUDA Toolkit 与 `nvcc`，但这不等于无法执行 CUDA device code。
Windows NVIDIA 驱动提供 `nvcuda.dll`；官方 `cuda-python` 可直接调用 Driver API，
官方 `nvidia-cuda-nvrtc` wheel 提供 NVRTC。D121u 因而增加一条独立于 CMake/
`nvcc` 的研究执行链：

1. 在 gitignored venv 中安装固定版本的 CUDA Python、NVRTC 与 NumPy；
2. 读取 device compute capability；
3. 由 NVRTC 把 device-only CUDA C++ 编译成对应架构 cubin；
4. 通过 Driver API 创建 context、加载 module、分配/传输显存并 launch kernel；
5. 把每个 batch 的结果复制回 host，逐 ray、逐 segment 与独立 Python CPU oracle
   比较。

可重复入口：

```powershell
.\gradlew.bat --no-daemon verifyCudaDdaNvrtcFixture
```

该任务会创建或复核 `build/cuda-python-env`，不会修改系统 Python。固定依赖位于
`native/cuda-dda/cuda-python-requirements.txt`：

- `cuda-python==13.3.1`；
- `nvidia-cuda-nvrtc==13.3.33`；
- `numpy==2.5.1`。

## 实现合同

`native/cuda-dda/src/dda_nvrtc_kernel.cu` 是 device-only kernel，不包含 C++ host
executor，也不冒充 `nvcc` 构建的 `mcfpv_dda_cuda`。它保持 D121s 的语义：

- FP64 Amanatides–Woo traversal；
- simultaneous-axis tie epsilon：
  `max(1e-12, abs(crossing) * 1e-12)`；
- 对 unsigned-sorted packed sparse cells 做 binary search；
- 逐段输出 packed cell、length、material id 与 fill fraction；
- 输出 reached/stopped/truncated、首个有效材料与三频带 loss/gain；
- 以 `8192 rays / 1,048,576 reserved segments` 默认上限确定性连续分批；
- 每批重新基准 `segment_offset`，显存按 peak batch 而不是完整语料分配。

Python runner 使用显式 padding 的 NumPy dtype 冻结 CUDA ABI：

| record | bytes |
|---|---:|
| `DeviceCell` | 24 |
| `DeviceRay` | 64 |
| `DeviceSegment` | 32 |
| `DeviceResult` | 72 |

静态 source-contract gate 现在除原 CMake/`nvcc` source 外，还检查 NVRTC kernel 与
runner 的编译、加载、launch、bounded batching、offset rebase、逐段 parity 及
claim boundary。

## 设备与结果

设备：

- `NVIDIA GeForce RTX 3060`；
- compute capability `8.6`；
- Driver API version `13030`；
- NVRTC `13.3`；
- 编译目标 `sm_86`。

在正式 DDA gate 前，单独的 1024-element `add_one` smoke kernel 已完成 NVRTC
编译、cubin 加载、H2D、device launch、D2H 与 exact array equality。

canonical Java fixture：

- bundle SHA-256：
  `f905c3039d4e0815f4ece5e1fc91aed9b6ab07ff907cdcc889da9fd3b159e9ff`；
- snapshot SHA-256：
  `d7fbeb8e26df3c85bb937c91c59d993a55726310f18e5c22273a2ac85871a990`；
- `8 cells / 3 rays / 231 actual segments`；
- 默认规划 `1 batch / 577 reserved segments / 18,464 segment bytes`；
- `2 warmup / 10 measured` passes；
- `verified_rays=3 / verified_segments=231`，无 mismatch。

该次正式 Gradle run 的观测值：

| stage | P50 (ms) | P95 (ms) |
|---|---:|---:|
| H2D | 0.0075 | 0.0106 |
| kernel | 0.1792 | 0.187392 |
| D2H | 0.0295 | 0.0354 |
| submit-to-result | 0.2493 | 0.2788 |

报告位于 gitignored
`build/research/cuda-dda-nvrtc-fixture-v1.json`，为 1,079 bytes，SHA-256：
`a4b424fa0c02b21913f9bac3d80333e42bb5474879d1e3a86dd01f97dc71f9d6`。

## 结论与边界

现在可以准确声明：

- `nvrtc_compiled=true`；
- `cuda_executed=true`；
- `fixture_device_parity_verified=true`；
- `nvcc_compiled=false`。

不能声明：

- 100k production corpus device parity 已通过；
- RTX 3060 比 CPU 更快；
- 已建立 workload crossover；
- CMake 的 `mcfpv_dda_cuda` 已构建；
- 已接入 Minecraft、JNI/JNA、音频热路径或发布配置。

3-ray 夹具的 launch/context/WDDM 固定成本占主导，表中时间只证明 timing
instrumentation 可用，不用于性能决策。下一步 D121v 必须用 D121s/t 的同一
100,008-ray bundle、全部 24 batches 与三轮重复合同运行 device parity/performance；
同时记录桌面 GPU 占用，避免把单次最好值作为 crossover。

本轮未启动 Minecraft client，未读取 `ClientLevel`，未打开 playback/capture
endpoint，也未捕获音频。
