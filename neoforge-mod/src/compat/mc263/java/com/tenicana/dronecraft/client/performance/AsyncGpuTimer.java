package com.tenicana.dronecraft.client.performance;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.device.GpuDevice;
import java.util.Arrays;
import java.util.OptionalLong;

/** Bounded, asynchronous timestamps through the native OpenGL/Vulkan abstraction. */
public final class AsyncGpuTimer {
    public static final int MAX_PENDING_SAMPLES = 8;
    private static final byte FREE = 0, RECORDING = 1, PENDING = 2;
    private static final byte[] STATES = new byte[MAX_PENDING_SAMPLES];
    private static final long[] FRAME_TOKENS = new long[MAX_PENDING_SAMPLES];
    private static final long[] SAMPLE_TOKENS = new long[MAX_PENDING_SAMPLES];
    private static GpuDevice owner;
    private static GpuQueryPool queries;
    private static double timestampPeriodNs;
    private static long nextSampleToken;
    private static boolean failed;
    private static volatile boolean cleanupRequested;

    private AsyncGpuTimer() { }

    public static void service() {
        if (!RenderSystem.isOnRenderThread()) return;
        if (!RuntimePerformanceMonitor.gpuTimingEnabled()) { close(); return; }
        try {
            if (cleanupRequested) release();
            if (failed) return;
            if (queries == null && !initialize()) return;
            if (!ownsDevice()) { fail("unavailable: GPU device changed"); return; }
            for (int slot = 0; slot < MAX_PENDING_SAMPLES; slot++) {
                if (STATES[slot] != PENDING) continue;
                OptionalLong started = queries.getValue(slot * 2);
                OptionalLong ended = queries.getValue(slot * 2 + 1);
                if (started.isEmpty() || ended.isEmpty()) continue;
                long frame = FRAME_TOKENS[slot];
                long ticks = ended.getAsLong() - started.getAsLong();
                clearSlot(slot);
                double duration = ticks * timestampPeriodNs;
                if (ticks > 0 && Double.isFinite(duration) && duration < Long.MAX_VALUE) {
                    RuntimePerformanceMonitor.acceptGpuTiming(frame, Math.max(1L, Math.round(duration)));
                } else RuntimePerformanceMonitor.gpuSampleSkipped();
            }
        } catch (RuntimeException | LinkageError failure) {
            fail("unavailable: Renderpearl timestamp query failed (" + failure.getClass().getSimpleName() + ")");
        }
    }

    public static long begin(long frameToken) {
        if (frameToken == 0 || !RuntimePerformanceMonitor.gpuTimingEnabled()) return 0;
        if (!RenderSystem.isOnRenderThread() || queries == null || failed || cleanupRequested) {
            RuntimePerformanceMonitor.gpuSampleSkipped(); return 0;
        }
        try {
            if (!ownsDevice()) { fail("unavailable: GPU device changed"); return 0; }
            for (int slot = 0; slot < MAX_PENDING_SAMPLES; slot++) {
                if (STATES[slot] != FREE) continue;
                long token = ++nextSampleToken;
                if (token == 0) token = ++nextSampleToken;
                FRAME_TOKENS[slot] = frameToken; SAMPLE_TOKENS[slot] = token; STATES[slot] = RECORDING;
                writeTimestamp(slot * 2);
                return token;
            }
            RuntimePerformanceMonitor.gpuSampleSkipped();
        } catch (RuntimeException | LinkageError failure) {
            fail("unavailable: Renderpearl timestamp submission failed (" + failure.getClass().getSimpleName() + ")");
        }
        return 0;
    }

    public static void end(long token, boolean completed) {
        if (token == 0) return;
        if (!completed || !RuntimePerformanceMonitor.gpuTimingEnabled()) { close(); return; }
        if (!RenderSystem.isOnRenderThread() || queries == null || cleanupRequested) return;
        try {
            if (!ownsDevice()) { fail("unavailable: GPU device changed"); return; }
            for (int slot = 0; slot < MAX_PENDING_SAMPLES; slot++) {
                if (STATES[slot] != RECORDING || SAMPLE_TOKENS[slot] != token) continue;
                writeTimestamp(slot * 2 + 1); STATES[slot] = PENDING; return;
            }
        } catch (RuntimeException | LinkageError failure) {
            fail("unavailable: Renderpearl timestamp submission failed (" + failure.getClass().getSimpleName() + ")");
        }
    }

    private static void writeTimestamp(int index) {
        CommandEncoder encoder = owner.createCommandEncoder();
        encoder.writeTimestamp(queries, index);
        encoder.submit();
    }

    private static boolean initialize() {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) { fail("unavailable: no active Renderpearl device"); return false; }
        double period = device.getDeviceInfo().timestampPeriod();
        if (!(period > 0) || !Double.isFinite(period)) {
            fail("unavailable: backend exposes no GPU timestamp period"); return false;
        }
        owner = device;
        queries = device.createTimestampQueryPool(MAX_PENDING_SAMPLES * 2);
        if (queries == null || queries.size() < MAX_PENDING_SAMPLES * 2) {
            fail("unavailable: backend cannot allocate GPU timestamps"); return false;
        }
        timestampPeriodNs = period;
        RuntimePerformanceMonitor.setGpuStatus("active: asynchronous " + device.getDeviceInfo().backendName() + " render timestamps");
        return true;
    }

    private static boolean ownsDevice() { return owner != null && owner == RenderSystem.tryGetDevice(); }

    public static void close() {
        cleanupRequested = true;
        if (!RenderSystem.isOnRenderThread()) return;
        try { release(); } catch (RuntimeException | LinkageError ignored) { }
    }

    private static void fail(String reason) {
        try { release(); } catch (RuntimeException | LinkageError ignored) { }
        failed = true;
        RuntimePerformanceMonitor.setGpuStatus(reason);
    }

    private static void release() {
        // The old device owns objects after replacement; never submit destruction to a new device.
        if (queries != null && ownsDevice()) queries.close();
        queries = null; owner = null; timestampPeriodNs = 0;
        Arrays.fill(STATES, FREE); Arrays.fill(FRAME_TOKENS, 0); Arrays.fill(SAMPLE_TOKENS, 0);
        failed = false; cleanupRequested = false;
    }

    private static void clearSlot(int slot) { STATES[slot] = FREE; FRAME_TOKENS[slot] = 0; SAMPLE_TOKENS[slot] = 0; }
}
