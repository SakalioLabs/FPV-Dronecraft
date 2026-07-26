# D063 — CUDA 可见性、可构建性与性能证明必须分开

- 状态：environment audit implemented，device backend pending（2026-07-24）。
- 工具：`docs/scripts/audit_cuda_dda_environment.py` 以 schema-v1 JSON 记录
  GPU、driver、显存、compute capability、`nvcc`、CMake 和 host compilers。
  `auditCudaDdaEnvironment` 是可重复的 Gradle 入口。
- 当前机器：RTX 3060 12 GiB、compute capability 8.6、driver 610.47；
  CMake 与 MinGW `g++` 可见，但 `nvcc` 不可见。因此状态严格为
  `toolchain-missing`。
- 声明 gate：报告中的 `device_reader_implemented`、
  `device_kernel_implemented`、`device_parity_verified` 和
  `speedup_verified` 当前全部固定为 `false`。环境探针不能自行把这些字段变为
  true；它们必须由未来独立的构建、parity 与基准产物证明。
- 契约：审计同时绑定 production bundle magic `MCFPDDA1`、schema 1 和
  Java `VoxelDda/DirectPathSolver` CPU reference，防止未来 kernel 对另一个简化
  问题做性能测试却宣称完成 Minecraft parity。
- 验证：5 个测试覆盖 ready、GPU 可见但 toolkit 缺失、无设备、probe failure
  和损坏 CSV。
  本机 Gradle 探针成功原子写出
  `build/research/cuda-dda-environment-v1.json`。
- 决策：当前可以继续 host layout、capture 和 corpus 工作；不能编译 CUDA
  kernel，不能报告 H2D/kernel/D2H 时间或 GPU speedup。
