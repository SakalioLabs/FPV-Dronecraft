# D121i — Null-backend 分数延迟 early renderer 基线

## 结论

D121i 已将 D121h 控制 DTO 变成纯内存 stereo wet bus，但它仍是验证基线，不是最终
听感算法。`NullEarlyReflectionRenderer` 不持有 Minecraft、OpenAL、PCM endpoint、
native handle 或音频线程。

信号链为：

```text
mono dry sample
→ 500 Hz / 4000 Hz 两个一阶低通
→ low = lp500
→ mid = lp4000 - lp500
→ high = input - lp4000
→ 共享三频带环形历史
→ 最多 12 个 fractional-delay taps
→ 每 tap 三带 amplitude + direction-X equal-power pan
→ stereo wet-only early bus
```

三个分频分量逐样本严格相加回原输入，因此当三带 gain 相同且 delay 相同时，不改变
整数延迟 impulse 的总波形。dry path、late path 和最终 headroom 不属于此 renderer。

## 延迟与控制过渡

- 默认最大 delay：16,384 samples（约 341 ms）；
- 三带共享 power-of-two ring buffers；
- 当前使用线性分数延迟插值；
- delay、三带 amplitude、direction X 在 D121h 的 2,400 samples 内逐样本 ramp；
- stereo pan 为 `sqrt(0.5*(1∓x))`，保持左右声道平方和；
- fade-to-zero slot 在 ramp 完成后停止读取。

线性插值的半采样 impulse 被精确拆为相邻两个 `0.5` 权重，但它在高频存在明显下垂，
尤其半采样处接近 Nyquist 时会产生零点。因此这只是连续性/预算/运行时基线，不能认定
为“最适合 Minecraft 穿越机”的最终 fractional-delay 算法。

## Early 能量门

提交控制帧前，renderer 对 low/mid/high 分别检查：

```text
sum(start_amplitude²) <= 1
sum(target_amplitude²) <= 1
```

这与 D121h 的 `amplitude=sqrt(energy)` 对应，并防止一个 control frame 绕过 D121a
early-energy ledger。检查在修改 renderer 状态前完成；非法/重复 slot、NaN、越界 delay、
越界 gain 或超预算帧被原子拒绝。renderer 不声称包含 limiter。

## 离线信号证据

- integer delay 4 samples：中心声像左右峰值均为 `sqrt(0.5)`，峰值 index 4；
- fractional delay 4.5 samples：index 4/5 各为 `0.5*sqrt(0.5)`；
- 997 Hz、0.2 amplitude moving-delay 最大相邻样本变化 `0.02345`；
- hard topology replacement 最大相邻样本变化 `0.06153`；
- 全部输出有限，无 NaN/Inf；
- 两个 `0.64 energy` clusters 的 endpoint 总能量 `1.28` 被拒绝。

最坏 12-slot crossfade、256-sample block 的 10,000 个 timing blocks得到：

```text
P50 = 128.125 ns/output frame
P99 = 235.938 ns/output frame
```

通过冻结的 `≤500 ns/output frame` 门。五个各 5,000 blocks 的 steady-state
submit+render allocation windows 全为 `0 bytes`。独立 Python verifier 重算 impulse、
continuity、raw timing P50/P99、allocation 和边界声明。

当前相关 Java 回归 78 项、Python 回归 13 项，全部通过。

## 边界

```text
live_early_renderer_enabled=false
minecraft_integration_enabled=false
client_level_read=false
minecraft_client_started=false
physical_endpoint_opened=false
captures_audio=false
cuda_executed=false
release_calibrated=false
```

没有生成或播放物理声音。本轮的 stereo pan 也不是 HRTF/binaural spatialization。

## 下一步

D121j 不应直接把线性插值接入游戏，而应在同一 null backend 中比较：

1. linear interpolation；
2. 3rd-order Lagrange FIR fractional delay；
3. 1st/2nd-order Thiran all-pass（移动 delay 时检查状态稳定性）；
4. 各 fractional phase 的 magnitude/phase/group-delay error；
5. 高频旋翼谐波与宽带桨噪的 moving-delay sidebands；
6. hard topology switch 的 click、峰值和计算预算；
7. 最终仅保留在高频误差、运动稳定性和 12-slot 成本之间占优的算法。

随后才研究离线 HRTF/ambisonic spatializer 与 Minecraft 实际声音路径。

## 证据哈希

| artifact | SHA-256 |
|---|---|
| D121i contract | `1d6771f9b1de79f09e1220d4f8bf1362dc765c8cc6c438ac67838e42d24e769a` |
| Java null-renderer report | `2831ecdb6ddfd3438948e986e1f05e9e8f27aec97d6853f320cb0569e6032a13` |
| independent verification | `a5039498bfb0ab89ce6f7ca43ed02bc93f28fc80927dd4505391361d10a11988` |
