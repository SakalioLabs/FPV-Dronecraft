package com.tenicana.dronecraft.client.performance;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Arrays;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GLCapabilities;

/**
 * Optional render-thread GPU timing with a fixed pool of timestamp pairs.
 *
 * <p>The two timestamps describe the GPU execution span of GameRenderer.render,
 * including idle gaps during submission. They exclude the main-target clear,
 * final blit, swap and FPS limiter. Timestamp queries do not compete with
 * vanilla's GL_TIME_ELAPSED profiler. No result is read before both queries
 * report availability, and a full pool skips a sample instead of waiting.</p>
 */
public final class AsyncGpuTimer {
    public static final int MAX_PENDING_SAMPLES = 8;
    private static final byte FREE = 0;
    private static final byte RECORDING = 1;
    private static final byte PENDING = 2;
    private static final int[] START_QUERIES = new int[MAX_PENDING_SAMPLES];
    private static final int[] END_QUERIES = new int[MAX_PENDING_SAMPLES];
    private static final long[] FRAME_TOKENS = new long[MAX_PENDING_SAMPLES];
    private static final long[] SAMPLE_TOKENS = new long[MAX_PENDING_SAMPLES];
    private static final byte[] STATES = new byte[MAX_PENDING_SAMPLES];
    private static GLCapabilities ownerCapabilities;
    private static long ownerContext;
    private static long nextSampleToken;
    private static boolean initialized;
    private static boolean failed;
    private static volatile boolean cleanupRequested;

    private AsyncGpuTimer() {}

    /** Called once at a normal frame boundary, before issuing this frame's queries. */
    public static void service() {
        if (!RuntimePerformanceMonitor.gpuTimingEnabled()) {
            close();
            return;
        }
        if (!RenderSystem.isOnRenderThread()) return;
        try {
            if (cleanupRequested && !releasePool()) return;
            if (failed) return;
            if (!initialized && !initialize()) return;
            if (!ownsCurrentContext()) {
                fail("unavailable: GPU timing context changed");
                return;
            }
            for (int slot = 0; slot < MAX_PENDING_SAMPLES; slot++) {
                if (STATES[slot] != PENDING) continue;
                // Availability queries do not request completion or block on a result.
                if (GL15C.glGetQueryObjecti(END_QUERIES[slot], GL15C.GL_QUERY_RESULT_AVAILABLE) == 0
                        || GL15C.glGetQueryObjecti(START_QUERIES[slot], GL15C.GL_QUERY_RESULT_AVAILABLE) == 0) {
                    continue;
                }
                long started = GL33C.glGetQueryObjectui64(START_QUERIES[slot], GL15C.GL_QUERY_RESULT);
                long ended = GL33C.glGetQueryObjectui64(END_QUERIES[slot], GL15C.GL_QUERY_RESULT);
                long elapsedNs = ended - started;
                long frameToken = FRAME_TOKENS[slot];
                clearSlot(slot);
                if (elapsedNs > 0) {
                    // The monitor rejects tokens from a stopped or replaced capture.
                    RuntimePerformanceMonitor.acceptGpuTiming(frameToken, elapsedNs);
                } else {
                    RuntimePerformanceMonitor.gpuSampleSkipped();
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            fail("unavailable: GPU timestamp query failed (" + failure.getClass().getSimpleName() + ")");
        }
    }

    /** Returns an opaque query token, or zero when this frame cannot be sampled. */
    public static long begin(long frameToken) {
        if (frameToken == 0 || !RuntimePerformanceMonitor.gpuTimingEnabled()) return 0;
        if (!RenderSystem.isOnRenderThread() || !initialized || failed || cleanupRequested) {
            RuntimePerformanceMonitor.gpuSampleSkipped();
            return 0;
        }
        try {
            if (!ownsCurrentContext()) {
                fail("unavailable: GPU timing context changed");
                RuntimePerformanceMonitor.gpuSampleSkipped();
                return 0;
            }
            for (int slot = 0; slot < MAX_PENDING_SAMPLES; slot++) {
                if (STATES[slot] != FREE) continue;
                long sampleToken = ++nextSampleToken;
                // Zero is reserved for an unsampled render invocation.
                if (sampleToken == 0) sampleToken = ++nextSampleToken;
                FRAME_TOKENS[slot] = frameToken;
                SAMPLE_TOKENS[slot] = sampleToken;
                STATES[slot] = RECORDING;
                GL33C.glQueryCounter(START_QUERIES[slot], GL33C.GL_TIMESTAMP);
                return sampleToken;
            }
            RuntimePerformanceMonitor.gpuSampleSkipped();
        } catch (RuntimeException | LinkageError failure) {
            fail("unavailable: GPU timestamp query failed (" + failure.getClass().getSimpleName() + ")");
            RuntimePerformanceMonitor.gpuSampleSkipped();
        }
        return 0;
    }

    /** Never reads a query result; incomplete renders cancel the pool safely. */
    public static void end(long sampleToken, boolean completed) {
        if (sampleToken == 0) return;
        if (!completed || !RuntimePerformanceMonitor.gpuTimingEnabled()) {
            close();
            return;
        }
        if (!RenderSystem.isOnRenderThread() || !initialized || cleanupRequested) return;
        try {
            if (!ownsCurrentContext()) {
                fail("unavailable: GPU timing context changed");
                return;
            }
            for (int slot = 0; slot < MAX_PENDING_SAMPLES; slot++) {
                if (STATES[slot] != RECORDING || SAMPLE_TOKENS[slot] != sampleToken) continue;
                GL33C.glQueryCounter(END_QUERIES[slot], GL33C.GL_TIMESTAMP);
                STATES[slot] = PENDING;
                return;
            }
        } catch (RuntimeException | LinkageError failure) {
            fail("unavailable: GPU timestamp query failed (" + failure.getClass().getSimpleName() + ")");
        }
    }

    /**
     * Cancels outstanding samples without waiting. Safe to request off-thread;
     * GL deletion is deferred until the owning render context is current.
     */
    public static void close() {
        if (!RenderSystem.isOnRenderThread()) {
            cleanupRequested = true;
            return;
        }
        if (!initialized && ownerContext == 0 && !failed && !cleanupRequested) return;
        cleanupRequested = true;
        try {
            releasePool();
        } catch (RuntimeException | LinkageError ignored) {
            // A destroyed context owns the remaining GL object lifetime. Never
            // issue deletion in another context or change vanilla shutdown.
        }
    }

    private static boolean initialize() {
        long context = GLFW.glfwGetCurrentContext();
        if (context == 0) {
            fail("unavailable: no current OpenGL context");
            return false;
        }
        GLCapabilities capabilities = GL.getCapabilities();
        if (!(capabilities.OpenGL33 || capabilities.GL_ARB_timer_query)
                || capabilities.glQueryCounter == 0 || capabilities.glGetQueryObjectui64v == 0
                || capabilities.glGenQueries == 0 || capabilities.glDeleteQueries == 0
                || capabilities.glGetQueryObjectiv == 0 || capabilities.glGetQueryiv == 0) {
            fail("unavailable: OpenGL timestamp queries unsupported");
            return false;
        }
        if (GL15C.glGetQueryi(GL33C.GL_TIMESTAMP, GL15C.GL_QUERY_COUNTER_BITS) < Long.SIZE) {
            fail("unavailable: 64-bit GPU timestamp counter unsupported");
            return false;
        }
        ownerContext = context;
        ownerCapabilities = capabilities;
        for (int slot = 0; slot < MAX_PENDING_SAMPLES; slot++) {
            START_QUERIES[slot] = GL15C.glGenQueries();
            END_QUERIES[slot] = GL15C.glGenQueries();
            if (START_QUERIES[slot] == 0 || END_QUERIES[slot] == 0) {
                fail("unavailable: GPU timestamp query allocation failed");
                return false;
            }
        }
        initialized = true;
        RuntimePerformanceMonitor.setGpuStatus("active: asynchronous GPU render timestamps");
        return true;
    }

    private static boolean ownsCurrentContext() {
        return RenderSystem.isOnRenderThread() && ownerContext != 0
                && GLFW.glfwGetCurrentContext() == ownerContext
                && GL.getCapabilities() == ownerCapabilities;
    }

    private static void fail(String status) {
        cleanupRequested = true;
        try {
            releasePool();
        } catch (RuntimeException | LinkageError ignored) {
            // Keep cleanup pending for a later boundary or the shutdown hook.
        }
        failed = true;
        RuntimePerformanceMonitor.setGpuStatus(status);
    }

    /** Returns false instead of deleting query names in a different context. */
    private static boolean releasePool() {
        if (ownerContext != 0 && !ownsCurrentContext()) return false;
        for (int slot = 0; slot < MAX_PENDING_SAMPLES; slot++) {
            if (START_QUERIES[slot] != 0) {
                GL15C.glDeleteQueries(START_QUERIES[slot]);
                START_QUERIES[slot] = 0;
            }
            if (END_QUERIES[slot] != 0) {
                GL15C.glDeleteQueries(END_QUERIES[slot]);
                END_QUERIES[slot] = 0;
            }
        }
        Arrays.fill(STATES, FREE);
        Arrays.fill(FRAME_TOKENS, 0);
        Arrays.fill(SAMPLE_TOKENS, 0);
        ownerContext = 0;
        ownerCapabilities = null;
        initialized = false;
        failed = false;
        cleanupRequested = false;
        return true;
    }

    private static void clearSlot(int slot) {
        STATES[slot] = FREE;
        FRAME_TOKENS[slot] = 0;
        SAMPLE_TOKENS[slot] = 0;
    }
}
