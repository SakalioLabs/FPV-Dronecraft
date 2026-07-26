# D121r — CUDA DDA 有界批次规划与无界分配拒绝

日期：2026-07-26  
状态：`verified-host-batch-plan / cuda-uncompiled / cuda-unexecuted`

## 决策

当前 CUDA correctness executor 不再允许按整个输入的
`sum(ray.maximum_cells)` 无条件申请 segment buffer。

新增的共享主机规划器先按两个硬上限把射线稳定地切成连续批次：

- `maximum_rays_per_batch = 8192`；
- `maximum_segments_per_batch = 1,048,576`；
- CUDA `DeviceSegment` 布局冻结为 32 bytes，因此默认 segment 上限代表
  32 MiB segment buffer；
- 单条射线超过 segment 上限、零上限和 byte-count overflow 全部拒绝；
- 分批保持输入顺序，不重排射线。

当前 `.cu` 仍只实现一个 device batch。因此它在任何 CUDA allocation 之前调用同一
规划器；只要规划结果不是恰好一个批次，就以
`current executor refuses unbounded allocation` fail closed。这样不会把尚未实现的
multi-batch execution 冒充完成，也不会继续此前的无界显存申请。

## 实现

- `native/cuda-dda/src/bounded_batch_plan.hpp`
  - 无 CUDA 依赖；
  - 确定性 contiguous greedy partition；
  - subtraction-first segment-capacity 检查，避免加法 overflow；
  - checked byte-count helper。
- `native/cuda-dda/src/dda_batch_plan.cpp`
  - 普通 C++20 可执行文件；
  - 严格读取与 CUDA executor 相同的 production bundle；
  - 输出 batch count、peak rays、peak segments 与假定的 peak segment bytes；
  - JSON 明确写出 `cuda_compiled=false / cuda_executed=false`。
- `native/cuda-dda/src/dda_cuda.cu`
  - 复用同一 planner；
  - 默认 8192 rays / 1,048,576 segments；
  - `static_assert(sizeof(DeviceSegment) == 32)`；
  - 多批输入在 allocation 前拒绝。
- `docs/scripts/verify_cuda_dda_source_contract.py`
  - 静态要求 planner、两个硬上限、32-byte layout 和 fail-closed 文本仍存在。

## 实测结果

本机 MSVC 17.14 以 `/W4 /WX /permissive-` 成功编译
`mcfpv_dda_batch_plan`。七项 self-test 覆盖：

1. segment 上限触发的连续分批；
2. ray-count 上限触发的连续分批；
3. 空输入；
4. 单射线超限拒绝；
5. 零 ray limit 拒绝；
6. byte count overflow 拒绝；
7. 批次 offset/容量断言。

production fixture `generation=42 / 3 rays / 577 reserved segments` 的结果：

| 限制 | 批次数 | peak rays | peak segments | peak bytes |
|---|---:|---:|---:|---:|
| 默认 `8192 / 1,048,576` | 1 | 3 | 577 | 18,464 |
| ray 上限改为 2 | 2 | 2 | 384 | 12,288 |
| segment 上限改为 512 | 2 | 2 | 384 | 12,288 |

segment 上限改为 128 时，fixture 的单条射线无法容纳，程序以
`single ray exceeds CUDA batch segment limit` 拒绝。

原生回归结果：

- CTest `4/4` passed：CPU reference self-test、planner self-test、CPU production
  fixture、planner production fixture；
- Python static source-contract tests：`2/2` passed；
- static contract：CUDA tokens `22`、CMake tokens `8`，缺失 `0`。

冻结的 SHA-256：

- `bounded_batch_plan.hpp`：
  `f87d6b92e59a452b364033805e48a49170644bd420674380a130c680425f0311`；
- `dda_batch_plan.cpp`：
  `694ffaad704518f0d72a5e325d776752657231676017d2689d57e43fbd433cd0`；
- `dda_cuda.cu`：
  `12b6b7a66fdfec746e52c8df3bf3b5685aea17dee6668ed95da9edc33a9e086a`；
- `CMakeLists.txt`：
  `f58ffaf24af2ab308e01e5d594de6dc2c14d83c98516333d037772bf4582ae05`；
- static verifier：
  `4a7a527b29ee1bf083193b4516834fb93d593e0c05bada519a6850d5147e5eb6`。

## 100k 语料边界

历史 D004 的 `dda-parity-100k-v1.bin` 是旧的 parity corpus，不是当前 CUDA
executor 消费的 production-bundle magic；直接喂给 production reader 会正确返回
`invalid production bundle magic`。因此本轮没有把旧文件转换或伪称为 CUDA 100k
执行证据。

历史已记录的该语料规模是 `100,008 rays / 12,617,210 segments`。仅按冻结的
32-byte segment layout，原先一次性 segment reservation 就是 403,750,720 bytes
（约 385.05 MiB），尚未计入 cells、rays、results 与运行时开销。这足以确认默认
32 MiB segment budget 会要求多批，但实际 batch count 必须等 production-bundle
版本生成后由本规划器按真实射线顺序计算。

## 证据边界

本机仍没有 `nvcc`，本轮：

- `cuda_compiled=false`；
- `cuda_executed=false`；
- 未测 H2D/kernel/D2H latency；
- 未证明 RTX 3060 parity 或吞吐；
- 未把 CUDA 接入 Minecraft/Fabric/audio thread；
- 未启动 Minecraft client；
- 未打开任何 playback/capture endpoint。

这轮只证明 host planner 已由 C++ 编译并运行、CUDA 源入口具有静态 fail-closed
门。静态 token 验证不是 CUDA 编译证据。

## 下一步

1. 生成与 current production bundle/expected-results 完全一致的 100k corpus；
2. 在可用 `nvcc` 的环境中实现 planned batch loop，逐批 rebased segment offsets；
3. 每个完整 corpus submission 汇总 H2D/kernel/D2H/submit-to-result，不把单个
   sub-batch 冒充一次更新；
4. 每批立即与同一 CPU oracle 比较 topology、materials、flags、first hit 与三频带；
5. 只有 100k correctness 全通过后，才运行 `8192/16384/32768/65536/100008`
   batch-size 性能矩阵；
6. 若实际 early-reflection scheduler 仍低于 8192 rays/update，继续保留 CPU DDA
   产品路径，不因 GPU 原型存在而迁移。
