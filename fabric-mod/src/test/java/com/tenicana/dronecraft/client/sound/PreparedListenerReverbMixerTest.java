package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;
import com.tenicana.dronecraft.acoustics.TonalComponent;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreparedListenerReverbMixerTest {
    private static final int SAMPLE_RATE = DroneAcousticRenderState.SAMPLE_RATE;
    private static final int MAX_FRAMES = 3_000;

    @Test
    void boundedSixSourceTraceKeepsQueuedMixLawPhaseNoiseAndEntryOrder() {
        var mixer = new PreparedListenerReverbMixer(6, MAX_FRAMES, 128);
        var reference = new QueuedReferenceMixer(MAX_FRAMES);
        float[] actual = new float[MAX_FRAMES + 1], expected = new float[MAX_FRAMES + 1];
        int[] sizes = {1, 47, 480, 1_024, MAX_FRAMES};
        double largestDifference = 0, largestGainDifferenceDb = 0;
        int largestS16Difference = 0;
        long samples = 0;
        for (int block = 0; block < 90; block++) {
            List<ListenerReverbState.Source> sources = traceSources(block);
            var prepared = PreparedListenerReverbMixer.prepare(sources);
            int frames = sizes[block % sizes.length];
            actual[frames] = expected[frames] = 13.25F;
            assertTrue(mixer.render(prepared, actual, frames));
            reference.render(sources, expected, frames);
            assertEquals(reference.size(), mixer.synthesizerCount());
            assertEquals(13.25F, actual[frames], "render must leave the buffer suffix alone");
            double actualEnergy = 0, expectedEnergy = 0;
            for (int sample = 0; sample < frames; sample++) {
                largestDifference = Math.max(largestDifference, Math.abs((double) actual[sample] - expected[sample]));
                largestS16Difference = Math.max(largestS16Difference, Math.abs(s16(actual[sample]) - s16(expected[sample])));
                actualEnergy += (double) actual[sample] * actual[sample];
                expectedEnergy += (double) expected[sample] * expected[sample];
            }
            if (actualEnergy > 0 && expectedEnergy > 0) {
                largestGainDifferenceDb = Math.max(largestGainDifferenceDb, Math.abs(10 * Math.log10(actualEnergy / expectedEnergy)));
            } else {
                assertEquals(expectedEnergy, actualEnergy);
            }
            samples += frames;
        }
        assertTrue(samples > 80_000);
        assertTrue(largestDifference <= 2e-6, "prepared float difference: " + largestDifference);
        assertTrue(largestS16Difference <= 1, "prepared S16 difference: " + largestS16Difference);
        assertTrue(largestGainDifferenceDb <= .005, "prepared gain difference dB: " + largestGainDifferenceDb);
    }

    @Test
    void sixNewIdsReclaimAbsentSlotsAndReintroducedIdsRestartPhaseAndNoise() {
        var mixer = new PreparedListenerReverbMixer(6, 480, 8);
        var reference = new QueuedReferenceMixer(480);
        float[] actual = new float[480], expected = new float[480];
        List<ListenerReverbState.Source> oldIds = sixSources(1, 0);
        List<ListenerReverbState.Source> newIds = sixSources(101, 1);
        for (List<ListenerReverbState.Source> sources : List.of(oldIds, oldIds, newIds, List.<ListenerReverbState.Source>of(), oldIds)) {
            assertTrue(mixer.render(PreparedListenerReverbMixer.prepare(sources), actual, actual.length));
            reference.render(sources, expected, expected.length);
            assertNear(expected, actual, 2e-6);
            assertEquals(sources.size(), mixer.synthesizerCount());
        }

        // A removed entity is a fresh legacy pair on reintroduction, including
        // broadband filter/noise seed. This equality uses one tone per layer.
        var isolated = new PreparedListenerReverbMixer(1, 480, 2);
        var fresh = new PreparedListenerReverbMixer(1, 480, 2);
        var one = PreparedListenerReverbMixer.prepare(List.of(source(Integer.MAX_VALUE, singleFrame(1, 1, .25), .9, .7)));
        var empty = PreparedListenerReverbMixer.prepare(List.of());
        assertTrue(isolated.render(one, actual, 480));
        assertTrue(isolated.render(empty, actual, 480));
        assertEquals(0, isolated.synthesizerCount());
        assertTrue(isolated.render(one, actual, 480));
        assertTrue(fresh.render(one, expected, 480));
        assertArrayEquals(expected, actual);
        isolated.reset();
        fresh.reset();
        assertEquals(0, isolated.synthesizerCount());
        assertTrue(isolated.render(one, actual, 480));
        assertTrue(fresh.render(one, expected, 480));
        assertArrayEquals(expected, actual);
    }

    @Test
    void duplicateEntityEntriesKeepSequentialRenderingAndPreflightTheirUnion() {
        var sources = new ArrayList<ListenerReverbState.Source>();
        sources.add(source(7, singleFrame(1, 1, .1), .6, .4));
        sources.add(source(7, singleFrame(1, 1, .8), .3, .9));
        var plan = PreparedListenerReverbMixer.prepare(sources);
        sources.clear();
        assertEquals(2, plan.originalSources().size());
        assertThrows(UnsupportedOperationException.class, () -> plan.originalSources().clear());
        var mixer = new PreparedListenerReverbMixer(1, 512, 1);
        var reference = new QueuedReferenceMixer(512);
        float[] actual = new float[512], expected = new float[512];
        for (int block = 0; block < 8; block++) {
            assertTrue(mixer.render(plan, actual, 512));
            reference.render(plan.originalSources(), expected, 512);
            assertArrayEquals(expected, actual, "same key duplicates share one slot and advance phase per entry");
        }
        assertEquals(1, mixer.synthesizerCount());

        // Neither entry individually exceeds one tone, but their union does.
        // Reject on a fresh source before the first entry can mutate anything.
        var tooMany = PreparedListenerReverbMixer.prepare(List.of(
                source(8, singleFrame(1, 1, .2), .6, .4),
                source(8, singleFrame(2, 1, .3), .3, .9)));
        var fresh = new PreparedListenerReverbMixer(1, 512, 1);
        Arrays.fill(actual, 9.5F);
        assertFalse(fresh.render(tooMany, actual, 512));
        assertEquals(0, fresh.synthesizerCount());
        for (float value : actual) assertEquals(9.5F, value);

        // Enough storage accepts different keys in later duplicate entries;
        // the old key's release also advances in that later render.
        mixer = new PreparedListenerReverbMixer(1, 512, 2);
        reference = new QueuedReferenceMixer(512);
        for (int block = 0; block < 8; block++) {
            assertTrue(mixer.render(tooMany, actual, 512));
            reference.render(tooMany.originalSources(), expected, 512);
            assertNear(expected, actual, 2e-6);
        }
    }

    @Test
    void failedWholeMixLeavesDestinationMembershipAndEverySourceDspUnchanged() {
        var mixer = new PreparedListenerReverbMixer(2, 512, 1);
        var untouched = new PreparedListenerReverbMixer(2, 512, 1);
        var valid = PreparedListenerReverbMixer.prepare(List.of(
                source(1, singleFrame(1, 1, .1), .8, .7),
                source(2, singleFrame(1, 1, .2), .5, .4)));
        float[] actual = new float[513], expected = new float[513];
        assertTrue(mixer.render(valid, actual, 512));
        assertTrue(untouched.render(valid, expected, 512));
        var laterLayerFails = PreparedListenerReverbMixer.prepare(List.of(
                source(1, singleFrame(1, 1, .9), .2, .1),
                source(2, singleFrame(1, 2, .8), .5, .4)));
        var replacedThenFails = PreparedListenerReverbMixer.prepare(List.of(
                source(101, singleFrame(1, 1, .9), .2, .1),
                source(2, singleFrame(1, 2, .8), .5, .4)));
        var tooManySources = PreparedListenerReverbMixer.prepare(List.of(
                source(3, singleFrame(1, 1, .1), .5, .5),
                source(4, singleFrame(1, 1, .1), .5, .5),
                source(5, singleFrame(1, 1, .1), .5, .5)));
        var duplicateUnionFails = PreparedListenerReverbMixer.prepare(List.of(
                source(1, singleFrame(1, 1, .2), .4, .4),
                source(1, singleFrame(2, 1, .3), .4, .4),
                source(2, singleFrame(1, 1, .2), .4, .4)));
        for (var rejected : List.of(laterLayerFails, replacedThenFails, tooManySources, duplicateUnionFails)) {
            Arrays.fill(actual, 17.25F);
            assertFalse(mixer.render(rejected, actual, 512));
            assertEquals(2, mixer.synthesizerCount());
            for (float value : actual) assertEquals(17.25F, value);
            assertTrue(mixer.render(valid, actual, 512));
            assertTrue(untouched.render(valid, expected, 512));
            assertArrayEquals(Arrays.copyOf(expected, 512), Arrays.copyOf(actual, 512), "rejected controls must not advance phase/noise/targets");
        }
        for (int frames : new int[]{-1, 513, 514}) {
            Arrays.fill(actual, 17.25F);
            assertFalse(mixer.render(valid, actual, frames));
            for (float value : actual) assertEquals(17.25F, value);
        }
        assertFalse(mixer.render(valid, new float[511], 512));
        assertTrue(mixer.render(valid, actual, 0));
        assertTrue(mixer.render(valid, actual, 512));
        assertTrue(untouched.render(valid, expected, 512));
        assertNear(expected, actual, 0, 512);
    }

    @Test
    void preparedDynamicReplacementDuplicatesRetirementResetAndRejectionAllocateZeroBytes() {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported(), "this allocation gate requires ThreadMXBean byte counters");
        bean.setThreadAllocatedMemoryEnabled(true);
        var mixer = new PreparedListenerReverbMixer(6, 64, 4);
        var plans = new PreparedListenerReverbMixer.PreparedSources[]{
                PreparedListenerReverbMixer.prepare(sixSources(1, 0)),
                PreparedListenerReverbMixer.prepare(sixSources(101, 1)),
                PreparedListenerReverbMixer.prepare(List.of(
                        source(101, singleFrame(1, 1, .1), .5, .5),
                        source(101, singleFrame(2, 1, .8), .3, .9),
                        source(102, singleFrame(1, 1, .3), .4, .7))),
                PreparedListenerReverbMixer.prepare(List.of()),
                PreparedListenerReverbMixer.prepare(List.of(source(501, manyMotorKeys(5), .5, .5)))
        };
        float[] output = new float[64];
        int faults = 0;
        // Warm all successful/rejected/reset paths before the measured window.
        for (int cycle = 0; cycle < 8_000; cycle++) faults += measuredCycle(mixer, plans, output, cycle);
        assertEquals(0, faults);
        long thread = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(thread);
        faults = 0;
        for (int cycle = 0; cycle < 12_000; cycle++) faults += measuredCycle(mixer, plans, output, cycle);
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        assertEquals(0, faults);
        assertEquals(0, allocated, "only preparation/ownership control may allocate; measured rendering must reuse storage");
    }

    private static int measuredCycle(PreparedListenerReverbMixer mixer, PreparedListenerReverbMixer.PreparedSources[] plans,
            float[] output, int cycle) {
        int mode = cycle & 7;
        if (mode == 7) {
            mixer.reset();
            return mixer.render(plans[0], output, 32) && mixer.synthesizerCount() == 6 ? 0 : 1;
        }
        if (mode == 6) return !mixer.render(plans[0], output, 65) ? 0 : 1;
        int index = mode == 5 ? 0 : mode;
        boolean accepted = mixer.render(plans[index], output, 32);
        if (mode == 4) return !accepted ? 0 : 1;
        int expectedSources = mode == 2 ? 2 : mode == 3 ? 0 : 6;
        return accepted && mixer.synthesizerCount() == expectedSources ? 0 : 1;
    }

    private static List<ListenerReverbState.Source> traceSources(int block) {
        var result = new ArrayList<ListenerReverbState.Source>();
        int count = block % 17 == 16 ? 0 : block % 11 == 10 ? 3 : 6;
        int firstId = block < 30 || block >= 60 ? 1 : 101;
        for (int index = 0; index < count; index++) {
            int entityId = firstId + (block % 2 == 0 ? index : count - 1 - index);
            var tones = new ArrayList<TonalComponent>();
            for (int rotor = 0; rotor < 2; rotor++) {
                tones.add(new TonalComponent(TonalComponent.Kind.ELECTRICAL, rotor, 1,
                        540 + 13 * block + 37 * rotor, .04 + .01 * (block % 4), .23 * rotor));
                tones.add(new TonalComponent(TonalComponent.Kind.BLADE_PASS, rotor, 1,
                        780 + 19 * block + 57 * rotor, .07, .31 + .21 * rotor));
                if (block % 3 != 0) tones.add(new TonalComponent(TonalComponent.Kind.SHAFT, rotor, 2, 240 + 11 * block, .025, .4));
            }
            var frame = new AcousticEmissionFrame(tones, new AcousticBands(.001 + .0001 * block, .002, .003));
            result.add(source(entityId, frame, .2 + .07 * index, .35 + .05 * (block % 5)));
        }
        if (count > 0 && block % 7 == 6) result.add(source(result.get(0).entityId(), singleFrame(1, 1, .6), .3, .8));
        return result;
    }

    private static List<ListenerReverbState.Source> sixSources(int firstId, int variant) {
        var result = new ArrayList<ListenerReverbState.Source>();
        for (int index = 0; index < 6; index++) result.add(source(firstId + index, singleFrame(1, 1, .1 * (index + variant)), .3 + .02 * index, .4));
        return result;
    }

    private static ListenerReverbState.Source source(int id, AcousticEmissionFrame frame, double motor, double propeller) {
        return new ListenerReverbState.Source(id, frame, motor, propeller);
    }

    private static AcousticEmissionFrame singleFrame(int motorOrder, int propellerOrder, double variation) {
        return new AcousticEmissionFrame(List.of(
                new TonalComponent(TonalComponent.Kind.ELECTRICAL, 0, motorOrder, 630 + 97 * variation, .1 + .03 * variation, .2 + variation),
                new TonalComponent(TonalComponent.Kind.BLADE_PASS, 0, propellerOrder, 970 + 113 * variation, .17 + .02 * variation, .4 + variation)),
                new AcousticBands(.002, .003 + .001 * variation, .004));
    }

    private static AcousticEmissionFrame manyMotorKeys(int count) {
        var tones = new ArrayList<TonalComponent>();
        for (int order = 1; order <= count; order++) tones.add(new TonalComponent(TonalComponent.Kind.ELECTRICAL, 0, order, 400 + 100 * order, .1, .2));
        return new AcousticEmissionFrame(tones, AcousticBands.SILENT);
    }

    private static int s16(float sample) {
        return Math.round(Math.max(-1, Math.min(1, sample)) * 32767F);
    }

    private static void assertNear(float[] expected, float[] actual, double tolerance) { assertNear(expected, actual, tolerance, expected.length); }

    private static void assertNear(float[] expected, float[] actual, double tolerance, int frames) {
        for (int sample = 0; sample < frames; sample++) assertEquals(expected[sample], actual[sample], tolerance, "sample " + sample);
    }

    /**
     * The previous producer's map membership, source-loop order, layer order,
     * float accumulation and final limiter, with the retained queued facade.
     * This test reference does not duplicate oscillator/broadband DSP.
     */
    private static final class QueuedReferenceMixer {
        private final Map<Integer, QueuedPair> pairs = new HashMap<>();
        private final HashSet<Integer> activeIds = new HashSet<>();
        private final float[] scratch;

        private QueuedReferenceMixer(int maximumFrames) { scratch = new float[maximumFrames]; }

        private void render(List<ListenerReverbState.Source> sources, float[] output, int frames) {
            Arrays.fill(output, 0, frames, 0);
            activeIds.clear();
            for (var source : sources) {
                activeIds.add(source.entityId());
                var pair = pairs.computeIfAbsent(source.entityId(), QueuedPair::new);
                pair.motor.render(source.emission(), PhaseContinuousSynthesizer.Layer.MOTOR, scratch, 0, frames);
                for (int index = 0; index < frames; index++) output[index] += (float) (scratch[index] * source.motorAmplitude());
                pair.propeller.render(source.emission(), PhaseContinuousSynthesizer.Layer.PROPELLER, scratch, 0, frames);
                for (int index = 0; index < frames; index++) output[index] += (float) (scratch[index] * source.propellerAmplitude());
            }
            pairs.keySet().removeIf(id -> !activeIds.contains(id));
            for (int index = 0; index < frames; index++) {
                float value = output[index];
                output[index] = value / (1F + Math.abs(value));
            }
        }

        private int size() { return pairs.size(); }
    }

    private static final class QueuedPair {
        private final PhaseContinuousSynthesizer motor, propeller;

        private QueuedPair(int entityId) {
            motor = new PhaseContinuousSynthesizer(SAMPLE_RATE, 31 * entityId + 17);
            propeller = new PhaseContinuousSynthesizer(SAMPLE_RATE, 31 * entityId + 23);
        }
    }
}
