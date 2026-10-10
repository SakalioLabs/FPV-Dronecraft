package com.tenicana.dronecraft.acoustics;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Same-JVM frozen reference prevents Enum hash/order differences between processes. */
class PhaseKernelConformanceTest {
    private static long exactSamples, preparedSamples, allocatedBytes = -1;
    private static double maximumPreparedDifference, maximumGainDifferenceDb;
    private static int maximumS16Difference, checks;
    private static int measuredSlotRetirementReuseCycles;
    private static final int[] CHUNKS = {1, 47, 480, 1024, 3000, 8192};
    private static List<AcousticEmissionFrame> frames() {
        List<TonalComponent> wide = new ArrayList<>(), changed = new ArrayList<>();
        for (int rotor = 0; rotor < 4; rotor++) {
            wide.add(new TonalComponent(TonalComponent.Kind.ELECTRICAL, rotor, 1, 700 + rotor * 17, .3, rotor * .2));
            changed.add(new TonalComponent(TonalComponent.Kind.ELECTRICAL, rotor, 1, 1300 + rotor * 19, .2, rotor * .7));
            for (int order = 1; order <= 4; order++) {
                wide.add(new TonalComponent(TonalComponent.Kind.BLADE_PASS, rotor, order, 1000 * order + rotor * 11, .25 / order, rotor * .3));
                changed.add(new TonalComponent(TonalComponent.Kind.BLADE_PASS, rotor, order, 600 * order + rotor * 7, .3 / order, rotor * .8));
            }
        }
        return List.of(new AcousticEmissionFrame(wide, new AcousticBands(.08, .03, .05)),
                new AcousticEmissionFrame(changed, new AcousticBands(.02, .07, .01)),
                new AcousticEmissionFrame(List.of(new TonalComponent(TonalComponent.Kind.SHAFT, 8, 1, 230, .5, .9)), AcousticBands.SILENT),
                new AcousticEmissionFrame(List.of(), AcousticBands.SILENT),
                new AcousticEmissionFrame(List.of(new TonalComponent(TonalComponent.Kind.ELECTRICAL, 0, 1, 400, .3, .1),
                        new TonalComponent(TonalComponent.Kind.ELECTRICAL, 0, 1, 900, .15, .9),
                        new TonalComponent(TonalComponent.Kind.BLADE_PASS, 0, 1, 23900, .1, .7)), new AcousticBands(.01, .02, .03)));
    }
    private static short pcm(float sample) { return (short) Math.round(Math.max(-1f, Math.min(1f, sample)) * 32767f); }

    @Test void queuedFacadeMatchesFrozenD168ExactlyAcrossTargetsLayersAndChunkSizes() {
        var frames = frames();
        for (int rate : new int[]{8000,48000}) for (int seed : new int[]{7,123}) for (var layer : PhaseContinuousSynthesizer.Layer.values()) {
            var current = new PhaseContinuousSynthesizer(rate, seed);
            var legacy = new LegacyPhaseContinuousSynthesizerD168(rate, seed);
            var legacyLayer = LegacyPhaseContinuousSynthesizerD168.Layer.valueOf(layer.name());
            for (int cycle = 0; cycle < 4; cycle++) for (var frame : frames) for (int count : CHUNKS) {
                float[] expected = new float[count + 4], actual = new float[count + 4]; Arrays.fill(expected, -9); Arrays.fill(actual, -9);
                legacy.render(frame, legacyLayer, expected, 2, count); current.render(frame, layer, actual, 2, count);
                assertArrayEquals(expected, actual, "queued numeric kernel must retain exact old expression/order");
                for (int sample = 2; sample < count + 2; sample++) { assertTrue(Float.isFinite(actual[sample])); assertEquals(pcm(expected[sample]), pcm(actual[sample])); }
                exactSamples += count;
            }
        }
        checks++;
    }

    @Test void preparedFacadePreservesSignalWithinFloatOrderingAndS16Bounds() {
        var frames = frames();
        for (int seed : new int[]{7,123}) for (var layer : PhaseContinuousSynthesizer.Layer.values()) {
            var legacy = new LegacyPhaseContinuousSynthesizerD168(48000, seed);
            var legacyLayer = LegacyPhaseContinuousSynthesizerD168.Layer.valueOf(layer.name());
            var prepared = new PreparedPhaseContinuousSynthesizer(48000, seed, 64);
            var controls = frames.stream().map(frame -> PreparedPhaseContinuousSynthesizer.prepare(frame, layer, 48000)).toList();
            for (int cycle = 0; cycle < 4; cycle++) for (int index = 0; index < frames.size(); index++) for (int count : CHUNKS) {
                float[] expected = new float[count], actual = new float[count]; legacy.render(frames.get(index), legacyLayer, expected);
                assertTrue(prepared.render(controls.get(index), actual, 0, count));
                double before = 0, after = 0;
                for (int sample = 0; sample < count; sample++) {
                    assertTrue(Float.isFinite(actual[sample]));
                    maximumPreparedDifference = Math.max(maximumPreparedDifference, Math.abs((double) expected[sample] - actual[sample]));
                    maximumS16Difference = Math.max(maximumS16Difference, Math.abs((int) pcm(expected[sample]) - pcm(actual[sample])));
                    before += (double) expected[sample] * expected[sample]; after += (double) actual[sample] * actual[sample];
                }
                if (before > 1e-10 && after > 1e-10) maximumGainDifferenceDb = Math.max(maximumGainDifferenceDb, Math.abs(10 * Math.log10(after / before)));
                preparedSamples += count;
            }
        }
        assertTrue(maximumPreparedDifference <= 2e-6, "shared kernel storage-order float gate");
        assertTrue(maximumS16Difference <= 1, "shared kernel storage-order S16 gate");
        assertTrue(maximumGainDifferenceDb <= .005, "prepared vs legacy relative gain gate");
        checks++;
    }

    @Test void capacityFailureIsAtomicAndZeroLengthDoesNotApplyTargets() {
        var layer = PhaseContinuousSynthesizer.Layer.MOTOR;
        var frame = new AcousticEmissionFrame(List.of(new TonalComponent(TonalComponent.Kind.ELECTRICAL, 0, 1, 700, .3, .1)), new AcousticBands(.03,.02,.01));
        var other = new AcousticEmissionFrame(List.of(new TonalComponent(TonalComponent.Kind.SHAFT, 1, 1, 1300, .2, .7)), AcousticBands.SILENT);
        var oldControl = PreparedPhaseContinuousSynthesizer.prepare(frame, layer, 48000); var newControl = PreparedPhaseContinuousSynthesizer.prepare(other, layer, 48000);
        var a = new PreparedPhaseContinuousSynthesizer(48000, 99, 1); var b = new PreparedPhaseContinuousSynthesizer(48000, 99, 1);
        float[] x = new float[3000], y = new float[3000]; assertTrue(a.render(oldControl,x,0,x.length)); assertTrue(b.render(oldControl,y,0,y.length)); assertArrayEquals(x,y);
        var before = a.oscillatorDiagnostics(); Arrays.fill(x,-9);
        assertFalse(a.render(newControl,x,0,x.length), "old releasing key plus new key exceed storage even though new frame fits alone");
        assertTrue(Arrays.stream(new double[]{x[0],x[x.length-1]}).allMatch(v->v==-9));
        assertEquals(before,a.oscillatorDiagnostics()); assertTrue(a.render(newControl,x,0,0)); assertEquals(before,a.oscillatorDiagnostics());
        assertTrue(a.render(oldControl,x,0,x.length)); assertTrue(b.render(oldControl,y,0,y.length)); assertArrayEquals(x,y,"failure must not advance phase, targets or noise");
        var silence = PreparedPhaseContinuousSynthesizer.prepare(new AcousticEmissionFrame(List.of(),AcousticBands.SILENT),layer,48000);
        for (int block=0;block<32;block++) assertTrue(a.render(silence,x,0,x.length));
        assertEquals(0,a.activeOscillatorCount()); assertTrue(a.render(newControl,x,0,x.length));
        var fresh = new PreparedPhaseContinuousSynthesizer(48000,99,1); assertTrue(fresh.render(newControl,y,0,y.length));
        // Retained noise release state is intentionally not reset by slot reuse;
        // compare oscillator state itself to prove all tonal fields reset.
        assertEquals(fresh.oscillatorDiagnostics(),a.oscillatorDiagnostics());
        checks++;
    }

    @Test void duplicateEntriesUseOneSlotAndKeepSequentialFrequencyTargets() {
        var layer = PhaseContinuousSynthesizer.Layer.MOTOR;
        var frame = new AcousticEmissionFrame(List.of(
                new TonalComponent(TonalComponent.Kind.ELECTRICAL, 2, 1, 400, .3, .1),
                new TonalComponent(TonalComponent.Kind.ELECTRICAL, 2, 1, 900, .2, .9),
                new TonalComponent(TonalComponent.Kind.ELECTRICAL, 2, 1, 600, .15, 1.4)), AcousticBands.SILENT);
        var control = PreparedPhaseContinuousSynthesizer.prepare(frame, layer, 48000);
        assertEquals(3, control.acceptedToneCount()); assertEquals(1, control.uniqueToneCount());
        var prepared = new PreparedPhaseContinuousSynthesizer(48000, 17, 1);
        var legacy = new LegacyPhaseContinuousSynthesizerD168(48000, 17);
        for (int count : CHUNKS) {
            float[] expected = new float[count], actual = new float[count];
            legacy.render(frame, LegacyPhaseContinuousSynthesizerD168.Layer.MOTOR, expected);
            assertTrue(prepared.render(control, actual, 0, count)); assertEquals(1, prepared.activeOscillatorCount());
            assertArrayEquals(expected, actual, "duplicate entries retain first phase and ordered ramp targets");
            for (int sample = 0; sample < count; sample++) assertEquals(pcm(expected[sample]), pcm(actual[sample]));
        }
        checks++;
    }

    @Test void retiredSlotResetsPhaseAndChirpStateExactlyToFreshRenderer() {
        var layer = PhaseContinuousSynthesizer.Layer.FULL;
        var old = PreparedPhaseContinuousSynthesizer.prepare(new AcousticEmissionFrame(List.of(
                new TonalComponent(TonalComponent.Kind.SHAFT, 0, 1, 200, .3, .8),
                new TonalComponent(TonalComponent.Kind.SHAFT, 0, 1, 1400, .3, 1.2)), AcousticBands.SILENT), layer, 8000);
        var next = PreparedPhaseContinuousSynthesizer.prepare(new AcousticEmissionFrame(List.of(
                new TonalComponent(TonalComponent.Kind.ELECTRICAL, 1, 2, 600, .2, .4)), AcousticBands.SILENT), layer, 8000);
        var silence = PreparedPhaseContinuousSynthesizer.prepare(new AcousticEmissionFrame(List.of(), AcousticBands.SILENT), layer, 8000);
        var reused = new PreparedPhaseContinuousSynthesizer(8000, 13, 1);
        var fresh = new PreparedPhaseContinuousSynthesizer(8000, 13, 1);
        float[] release = new float[16000]; assertTrue(reused.render(old, release, 0, 47));
        assertTrue(reused.render(silence, release, 0, release.length)); assertEquals(0, reused.activeOscillatorCount());
        for (int count : CHUNKS) {
            float[] expected = new float[count], actual = new float[count];
            assertTrue(fresh.render(next, expected, 0, count)); assertTrue(reused.render(next, actual, 0, count));
            assertArrayEquals(expected, actual, "silent broadband isolates every reset tonal field");
            for (int sample = 0; sample < count; sample++) assertEquals(pcm(expected[sample]), pcm(actual[sample]));
        }
        checks++;
    }

    private static int retirementReuseCycles(PreparedPhaseContinuousSynthesizer renderer,
            PreparedPhaseContinuousSynthesizer.PreparedEmission[] targets,
            PreparedPhaseContinuousSynthesizer.PreparedEmission silence, float[] output, int cycles) {
        int faults = 0;
        for (int cycle = 0; cycle < cycles; cycle++) {
            if (!renderer.render(silence, output, 0, output.length)) faults++;
            if (renderer.activeOscillatorCount() != 0) faults++;
            if (!renderer.render(targets[cycle % targets.length], output, 0, 64)) faults++;
            if (renderer.activeOscillatorCount() != 1) faults++;
        }
        return faults;
    }

    @Test void dynamicPreparedRenderingAndCapacityFailureAllocateZeroBytes() {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported()); bean.setThreadAllocatedMemoryEnabled(true);
        var controls = frames().stream().map(frame -> PreparedPhaseContinuousSynthesizer.prepare(frame,PhaseContinuousSynthesizer.Layer.FULL,48000)).toArray(PreparedPhaseContinuousSynthesizer.PreparedEmission[]::new);
        var renderer = new PreparedPhaseContinuousSynthesizer(48000,37,64); float[] output = new float[64];
        var constrained = new PreparedPhaseContinuousSynthesizer(48000,37,1);
        var first = PreparedPhaseContinuousSynthesizer.prepare(new AcousticEmissionFrame(List.of(new TonalComponent(TonalComponent.Kind.SHAFT,0,1,200,.3,0)),AcousticBands.SILENT),PhaseContinuousSynthesizer.Layer.FULL,48000);
        var rejected = PreparedPhaseContinuousSynthesizer.prepare(new AcousticEmissionFrame(List.of(new TonalComponent(TonalComponent.Kind.ELECTRICAL,1,1,400,.3,0)),AcousticBands.SILENT),PhaseContinuousSynthesizer.Layer.FULL,48000);
        assertTrue(constrained.render(first,output,0,output.length));
        var reusable = new PreparedPhaseContinuousSynthesizer(8000, 71, 1);
        var reuseTargets = new PreparedPhaseContinuousSynthesizer.PreparedEmission[]{
                PreparedPhaseContinuousSynthesizer.prepare(new AcousticEmissionFrame(List.of(new TonalComponent(TonalComponent.Kind.SHAFT,0,1,200,.3,.7)),AcousticBands.SILENT),PhaseContinuousSynthesizer.Layer.FULL,8000),
                PreparedPhaseContinuousSynthesizer.prepare(new AcousticEmissionFrame(List.of(new TonalComponent(TonalComponent.Kind.ELECTRICAL,1,2,400,.2,.3)),AcousticBands.SILENT),PhaseContinuousSynthesizer.Layer.FULL,8000)};
        var reuseSilence = PreparedPhaseContinuousSynthesizer.prepare(new AcousticEmissionFrame(List.of(),AcousticBands.SILENT),PhaseContinuousSynthesizer.Layer.FULL,8000);
        float[] reuseOutput = new float[16000];
        int faults=0; for(int i=0;i<6000;i++) { if(!renderer.render(controls[i%controls.length],output,0,output.length)) faults++; if(constrained.render(rejected,output,0,output.length)) faults++; }
        faults += retirementReuseCycles(reusable, reuseTargets, reuseSilence, reuseOutput, 32);
        assertEquals(0,faults); long thread=Thread.currentThread().threadId();
        long before=bean.getThreadAllocatedBytes(thread);
        for(int i=0;i<12000;i++) { if(!renderer.render(controls[i%controls.length],output,0,output.length)) faults++; if(constrained.render(rejected,output,0,output.length)) faults++; }
        faults += retirementReuseCycles(reusable, reuseTargets, reuseSilence, reuseOutput, 16);
        allocatedBytes=bean.getThreadAllocatedBytes(thread)-before;
        measuredSlotRetirementReuseCycles = 16;
        assertEquals(0,faults); assertEquals(0,allocatedBytes,"valid dynamic prepared rendering including rejected control must not allocate");
        checks++;
    }

    @AfterAll static void preserveResult() throws Exception {
        String evidencePath = System.getenv("MCFPV_PHASE_CONFORMANCE_OUTPUT");
        if (evidencePath == null) return;
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if(!Files.isDirectory(root.resolve("build/research"))) root=root.getParent();
        String json=String.format(Locale.ROOT,"{\n  \"passed\": %s,\n  \"completed_checks\": %d,\n  \"same_JVM_frozen_queued_exact_samples\": %d,\n  \"prepared_vs_frozen_samples\": %d,\n  \"maximum_prepared_float_difference\": %.17g,\n  \"maximum_prepared_S16_difference\": %d,\n  \"maximum_prepared_gain_difference_db\": %.17g,\n  \"measured_dynamic_render_allocated_bytes\": %d,\n  \"measured_iterations\": 12000,\n  \"measured_slot_retirement_reuse_cycles\": %d,\n  \"duplicate_entries_share_one_slot\": true,\n  \"silent_slot_reuse_PCM_exact_to_fresh\": true,\n  \"production_Channel_callback_integrated\": false,\n  \"wet_history_ownership_changed\": false,\n  \"JVM_realtime_deadlines_qualified\": false,\n  \"physical_error_recomputed\": false,\n  \"full_goal_complete\": false\n}\n",checks==6,checks,exactSamples,preparedSamples,maximumPreparedDifference,maximumS16Difference,maximumGainDifferenceDb,allocatedBytes,measuredSlotRetirementReuseCycles);
        Files.writeString(root.resolve(evidencePath),json,StandardOpenOption.CREATE_NEW);
    }
}
