# D121z — 有界驱动预检与预展平 device smoke

日期：2026-07-27

状态：`fail-fast-preflight-verified / host-preparation-device-verified /
small-prefix-crossover-pending`

## 决策

CUDA 研究 runner 在导入 CUDA bindings、读取 corpus 或调用 `cuInit` 前，必须先用
`nvidia-smi -L` 做有界健康检查。默认超时为 10 秒，可通过
`--driver-probe-timeout` 缩短；超时、非零退出、无设备输出均 fail closed，不创建
新的 CUDA context。

环境引导脚本只负责固定版本的 Python packages 与 NVRTC 可用性，不再把
`driver.cuInit(0)` 混入依赖检查。这样驱动失去响应时，不会在正式 runner 前先等待
60 秒，也不会因为设备状态短暂异常而错误重装依赖。

独立环境审计仍保留 30 秒上限，但现在把 `TimeoutExpired` 转成结构化的
`gpu-probe-failed` 证据，而不是向上传播未处理异常。

## 验证

纯函数测试覆盖：

- 健康探针必须执行 `nvidia-smi -L`、传递指定超时并返回非空设备行；
- 超时必须产生明确的 bounded probe error；
- 环境审计的 30 秒超时必须转换成稳定错误；
- 静态 source contract 要求预检函数、timeout forwarding 与报告字段存在。

共 14 个相关单元测试通过，Python 编译检查与 CUDA DDA 静态合同检查通过，
`missing_*_contracts=[]`。

执行验证开始前，上一时段的 `nvidia-smi` 仍曾在约 34 秒无响应。本次 2 秒预检时
驱动已经恢复，所以没有伪造 timeout 实测；runner 在约 1.07 秒内完成预检、NVRTC
编译、设备执行和 canonical fixture 校验。

随后在 RTX 3060 / `sm_86` / Driver API 13030 / NVRTC 13.3 上验证
`host-preparation=once`：

| mode | rays / verified segments | topology | kernel P95 | submit P95 |
|---|---:|---|---:|---:|
| full | 3 / 231 | verified | 0.168 ms | 1.120 ms |
| aggregate | 3 / 231 | intentionally omitted | 0.175 ms | 0.860 ms |

两档均为 `2 warmup + 10 measured`，且 `aggregate_parity_verified=true`。fixture
规模只证明一次性 host preparation 可真实进入 CUDA 路径并保持 parity，不用于
吞吐、crossover 或 Minecraft deadline 声明。

本地忽略报告：

- `cuda-dda-nvrtc-fixture-host-prepared-once-v1.json`，2,935 bytes，
  SHA-256 `80c3cfb138dfd3360a9ee26c1d144cac53974326257f7347635de87f5ed65c54`
- `cuda-dda-nvrtc-fixture-host-prepared-once-aggregate-v1.json`，2,929 bytes，
  SHA-256 `a3eaae3c7410abd980caed7f88a0c71dce896fdf9eb5c0b9c79d3d224505d6e8`

## 边界

- 没有启动 Minecraft；
- 没有打开 playback/capture endpoint；
- 没有触碰或终止外部 GPU workload；
- 没有运行 128→8192 paired matrix；
- 没有把 3-ray fixture timing 当产品性能。

## 下一步

D121aa 应把 full 与 aggregate 放入同一个 CUDA context，复用同一 cells、
transmission、ray arrays、events 和 device allocations，并按 trial 交替执行顺序。
对 `128/256/512/1024/2048/4096/8192`：

1. 每档至少 3 个独立 paired trials；
2. 每 trial 至少 `5 warmup + 30 measured`；
3. 同一时间窗运行 compiled CPU prefix；
4. 保存每个 raw sample、执行顺序、驱动/显存快照与 parity；
5. 只在连续三档同时通过 P95、P99 和 deadline-miss gate 后冻结最小 offload batch。

当前几十条 direct rays/update 继续使用 Java CPU DDA；该矩阵只决定未来
early-reflection shadow mode 是否值得启用 CUDA。
