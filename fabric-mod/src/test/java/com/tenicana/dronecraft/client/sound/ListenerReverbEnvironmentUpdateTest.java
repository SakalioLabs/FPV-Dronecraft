package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.lang.management.ManagementFactory;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Output laws for immutable environment changes; no private cache or FDN-state inspection. */
class ListenerReverbEnvironmentUpdateTest {
    private static final long GENERATION = 4000;
    private static final int FRAMES = 3000;
    private static final AcousticBands INITIAL_RT60 = new AcousticBands(.8, .6, .4);

    private static FdnEnvironmentMapper.Controls environment(long generation, AcousticBands rt60,
            double wet, double transition) {
        return new FdnEnvironmentMapper.Controls(generation, rt60, wet, transition);
    }

    private static FdnEnvironmentMapper.Controls initial() {
        return environment(GENERATION, INITIAL_RT60, .35, .15);
    }

    private static ListenerReverbState preparedState() {
        var state = new ListenerReverbState();
        var sources = new ArrayList<ListenerReverbState.Source>();
        var emission = ListenerReverbStateTest.emission();
        for (int id = 1; id <= 6; id++) {
            sources.add(new ListenerReverbState.Source(id, emission, .3, .2));
        }
        state.publishSources(sources);
        state.publishEnvironment(initial());
        for (int tick = 0; tick < 12; tick++) state.advanceShadowHistory();
        assertEquals(24000, state.historyDiagnostics().historyFrames());
        return state;
    }

    @Test
    void sameGenerationImmediateMuteAndRestoreRetainRunningMixAndTail() {
        try (Rendered fixture = new Rendered()) {
            fixture.render(initial());
            assertTrue(nonzero(fixture.mixed));
            assertTrue(nonzero(fixture.wet), "nonzero shadow preroll must already reach the wet output");

            fixture.render(environment(GENERATION, INITIAL_RT60, 0, 0));
            assertTrue(nonzero(fixture.mixed), "muting wet output must keep the source mix running");
            assertFalse(nonzero(fixture.wet), "same-generation zero wet gain applies to the very next block");

            fixture.render(environment(GENERATION, INITIAL_RT60, .35, 0));
            assertTrue(nonzero(fixture.mixed));
            assertTrue(nonzero(fixture.wet), "restoring wet gain continues existing phase and delayed history");
        }
    }

    @Test
    void queuedPcmHonorsSameGenerationImmediateMuteAndRestore() {
        var state = preparedState();
        var stream = new ListenerReverbAudioStream(state);
        try {
            assertPcmMuteRestore(state, () -> {
                var buffer = stream.read(FRAMES * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                assertEquals(FRAMES, buffer.remaining());
                short[] samples = new short[FRAMES];
                buffer.get(samples);
                return samples;
            });
        } finally { stream.close(); }
    }

    @Test
    void callbackPcmHonorsSameGenerationImmediateMuteAndRestore() {
        var state = preparedState();
        var stream = new ListenerReverbAudioStream(state);
        var storage = MemoryUtil.memAlloc(FRAMES * Short.BYTES);
        long address = MemoryUtil.memAddress(storage);
        try {
            assertTrue(stream.prepareCallback());
            assertPcmMuteRestore(state, () -> {
                assertEquals(FRAMES * Short.BYTES, stream.readCallback(address, FRAMES * Short.BYTES));
                short[] samples = new short[FRAMES];
                for (int index = 0; index < samples.length; index++) {
                    samples[index] = MemoryUtil.memGetShort(address + (long) index * Short.BYTES);
                }
                return samples;
            });
        } finally { stream.close(); MemoryUtil.memFree(storage); }
    }

    private static void assertPcmMuteRestore(ListenerReverbState state, Supplier<short[]> read) {
        assertTrue(nonzero(read.get()), "the complete initial history must produce audible generated PCM");
        state.publishEnvironment(environment(GENERATION, INITIAL_RT60, 0, 0));
        assertFalse(nonzero(read.get()), "next returned PCM block must be entirely zero");
        state.publishEnvironment(environment(GENERATION, INITIAL_RT60, .35, 0));
        assertTrue(nonzero(read.get()), "restore must keep the stream live and continue its wet history");
    }

    @Test
    void rt60OnlyChangeAtSameGenerationMatchesDifferentGenerationWithIdenticalHistory() {
        try (Rendered actual = new Rendered(); Rendered reference = new Rendered(); Rendered unchanged = new Rendered()) {
            assertArrayEquals(actual.session.history(), reference.session.history());
            for (int block = 0; block < 4; block++) {
                actual.render(initial()); reference.render(initial()); unchanged.render(initial());
                assertArrayEquals(reference.mixed, actual.mixed);
                assertArrayEquals(reference.wet, actual.wet);
            }
            var shortened = new AcousticBands(.25, .2, .15);
            var sameGeneration = environment(GENERATION, shortened, .35, .15);
            var nextGeneration = environment(GENERATION + 1, shortened, .35, .15);
            double differenceFromReference = 0, responseToRt60Change = 0;
            for (int block = 0; block < 8; block++) {
                actual.render(sameGeneration); reference.render(nextGeneration); unchanged.render(initial());
                assertArrayEquals(reference.mixed, actual.mixed, "RT60 controls do not reset source phase");
                differenceFromReference = Math.max(differenceFromReference, maximumDifference(reference.wet, actual.wet));
                responseToRt60Change = Math.max(responseToRt60Change, maximumDifference(reference.wet, unchanged.wet));
            }
            assertTrue(responseToRt60Change > 1.0e-7, "the reference stimulus must measurably change the persistent tail");
            assertEquals(0, differenceFromReference, "environment values, rather than generation uniqueness, determine wet output");
        }
    }

    @Test
    void transitionOnlyChangeAtSameGenerationFinishesTheExistingWetGainTransition() {
        try (Rendered actual = new Rendered(); Rendered reference = new Rendered(); Rendered unchanged = new Rendered()) {
            var slow = environment(GENERATION + 1, INITIAL_RT60, .1, .3);
            for (int block = 0; block < 2; block++) {
                actual.render(slow); reference.render(slow); unchanged.render(slow);
                assertArrayEquals(reference.wet, actual.wet);
            }
            var immediate = environment(GENERATION + 1, INITIAL_RT60, .1, 0);
            var immediateNextGeneration = environment(GENERATION + 2, INITIAL_RT60, .1, 0);
            actual.render(immediate); reference.render(immediateNextGeneration); unchanged.render(slow);
            assertArrayEquals(reference.mixed, actual.mixed);
            assertTrue(maximumDifference(reference.wet, unchanged.wet) > 1.0e-7,
                    "transition-only control must change output while the gain is still approaching its target");
            assertArrayEquals(reference.wet, actual.wet, "zero transition finishes the existing target without clearing the tail");
        }
    }

    @Test
    void equalValueEnvironmentCopiesPreservePhaseTailAndOngoingSmoothing() {
        try (Rendered copied = new Rendered(); Rendered stable = new Rendered()) {
            var target = environment(GENERATION + 1, new AcousticBands(.25, .2, .15), .1, .2);
            for (int block = 0; block < 24; block++) {
                var equalCopy = environment(GENERATION + 1, new AcousticBands(.25, .2, .15), .1, .2);
                assertNotSame(target, equalCopy);
                assertEquals(target, equalCopy);
                copied.render(equalCopy); stable.render(target);
                assertTrue(nonzero(copied.wet), "object replacement must not lose the existing wet tail");
                assertArrayEquals(stable.mixed, copied.mixed);
                assertArrayEquals(stable.wet, copied.wet, "equal immutable controls preserve the complete smoothing trajectory");
            }
        }
    }

    @Test
    void fullSameGenerationControlUpdatesAndEqualCopiesAllocateZeroDuringPreparedSixSourceRender() {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        var state = preparedState();
        var publication = state.snapshot();
        var controls = List.of(
                initial(),
                environment(GENERATION, new AcousticBands(.8, .6, .4), .35, .15),
                environment(GENERATION, new AcousticBands(.25, .2, .15), .1, .05),
                environment(GENERATION, new AcousticBands(.25, .2, .15), .1, .05),
                environment(GENERATION, INITIAL_RT60, 0, 0),
                environment(GENERATION, INITIAL_RT60, .35, 0));
        var snapshots = new ListenerReverbState.Snapshot[controls.size()];
        for (int index = 0; index < snapshots.length; index++) {
            snapshots[index] = new ListenerReverbState.Snapshot(publication.sources(), controls.get(index), publication.preparedSources());
        }
        float[] mixed = new float[64], wet = new float[64];
        try (var session = state.acquireWetSession()) {
            int rejected = 0, unexpectedlyUnmuted = 0, restoredBlocks = 0;
            for (int iteration = 0; iteration < 6000; iteration++) {
                int index = iteration % snapshots.length;
                if (!session.renderWet(snapshots[index], mixed, wet, mixed.length)) rejected++;
                if (index == 4 && nonzero(wet)) unexpectedlyUnmuted++;
                if (index == 5 && nonzero(wet)) restoredBlocks++;
            }
            assertEquals(0, rejected);
            assertEquals(0, unexpectedlyUnmuted);
            assertEquals(1000, restoredBlocks);
            unexpectedlyUnmuted = restoredBlocks = 0;
            long thread = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(thread);
            for (int iteration = 0; iteration < 12000; iteration++) {
                int index = iteration % snapshots.length;
                if (!session.renderWet(snapshots[index], mixed, wet, mixed.length)) rejected++;
                if (index == 4 && nonzero(wet)) unexpectedlyUnmuted++;
                if (index == 5 && nonzero(wet)) restoredBlocks++;
            }
            long allocated = bean.getThreadAllocatedBytes(thread) - before;
            assertEquals(0, allocated, "prepared control comparison, six-source mix and FDN updates allocate no render memory");
            assertEquals(0, rejected);
            assertEquals(0, unexpectedlyUnmuted, "every immediate mute must apply, even though every control shares one generation");
            assertEquals(2000, restoredBlocks, "every restore preserves a running nonzero phase/history tail");
        }
    }

    private static boolean nonzero(float[] samples) {
        for (float sample : samples) if (sample != 0.0f) return true;
        return false;
    }

    private static boolean nonzero(short[] samples) {
        for (short sample : samples) if (sample != 0) return true;
        return false;
    }

    private static double maximumDifference(float[] a, float[] b) {
        double maximum = 0;
        for (int index = 0; index < a.length; index++) {
            assertTrue(Float.isFinite(a[index]));
            assertTrue(Float.isFinite(b[index]));
            maximum = Math.max(maximum, Math.abs((double) a[index] - b[index]));
        }
        return maximum;
    }

    private static final class Rendered implements AutoCloseable {
        final ListenerReverbState state = preparedState();
        final ListenerReverbHistoryProducer.ActiveSession session = state.acquireWetSession();
        final float[] mixed = new float[FRAMES], wet = new float[FRAMES];

        void render(FdnEnvironmentMapper.Controls controls) {
            state.publishEnvironment(controls);
            assertTrue(session.renderWet(state.snapshot(), mixed, wet, mixed.length));
        }

        @Override public void close() { session.close(); }
    }
}
