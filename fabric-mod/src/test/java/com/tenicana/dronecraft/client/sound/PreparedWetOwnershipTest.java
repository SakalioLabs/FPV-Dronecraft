package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import com.tenicana.dronecraft.acoustics.reverb.ListenerSharedFdn;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class PreparedWetOwnershipTest {
    private static int checks;
    private static long comparedSamples, allocatedBytes = -1;
    private static double maximumMixedDifference, maximumWetDifference;
    private static List<ListenerReverbState.Source> sources(int first, int count, double amplitude) {
        var result = new ArrayList<ListenerReverbState.Source>();
        for (int id = first; id < first + count; id++)
            result.add(new ListenerReverbState.Source(id, ListenerReverbStateTest.emission(), amplitude, amplitude * .7));
        return result;
    }
    private static FdnEnvironmentMapper.Controls environment(long generation) {
        return new FdnEnvironmentMapper.Controls(generation, new AcousticBands(1.2,.9,.6),.35,.2);
    }
    private static void compare(float[] expected, float[] actual, boolean wet) {
        for (int i=0;i<expected.length;i++) {
            assertTrue(Float.isFinite(actual[i]));
            double difference=Math.abs((double)expected[i]-actual[i]);
            if (wet) maximumWetDifference=Math.max(maximumWetDifference,difference);
            else maximumMixedDifference=Math.max(maximumMixedDifference,difference);
            assertTrue(difference<=2e-6,"same JVM frozen phase/history/FDN gate");
        }
        comparedSamples+=expected.length;
    }
    private static String fdnState(ListenerReverbHistoryProducer.ActiveSession session) throws Exception {
        var fdnField=session.getClass().getDeclaredField("fdn"); fdnField.setAccessible(true); Object fdn=fdnField.get(session);
        var result=new StringBuilder();
        for(var field:ListenerSharedFdn.class.getDeclaredFields()) {
            if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
            field.setAccessible(true); Object value=field.get(fdn); result.append(field.getName()).append(':');
            if(value instanceof double[][] arrays)result.append(Arrays.deepToString(arrays));
            else if(value instanceof double[] array)result.append(Arrays.toString(array));
            else if(value instanceof int[] array)result.append(Arrays.toString(array));
            else result.append(value);
        }
        return result.toString();
    }

    @Test void frozenShadowHistoryPhaseAndReplacementFdnPrerollConform() throws Exception {
        var state=new ListenerReverbState(); var source=sources(1,6,.3); state.publishSources(source); state.publishEnvironment(environment(1));
        var old=new LegacyListenerReverbHistoryProducerD169();
        for(int i=0;i<12;i++) { state.advanceShadowHistory(); old.advanceShadow(source); }
        var actual=state.acquireWetSession(); var expected=old.acquireWet(source);
        compare(expected.history(),actual.history(),false);
        var referenceFdn=new ListenerSharedFdn(48000); var env=state.snapshot().environment();
        referenceFdn.configure(env.rt60Seconds(),env.wetGain(),0);
        var history=expected.history(); referenceFdn.process(history,new float[history.length],0,history.length);
        for(int step=0;step<5;step++) {
            source=step==2?List.of():sources(step==3?21:1,6,step==4?.15:.3); state.publishSources(source);
            float[] before=new float[3000], mixed=new float[3000], expectedWet=new float[3000], wet=new float[3000];
            assertTrue(expected.render(source,before,before.length)); referenceFdn.process(before,expectedWet,0,before.length);
            assertTrue(actual.renderWet(state.snapshot(),mixed,wet,mixed.length)); compare(before,mixed,false); compare(expectedWet,wet,true);
        }
        // New sessions retain phase and shadow history, and start a new FDN preroll.
        var replacement=state.acquireWetSession(); var oldReplacement=old.acquireWet(source);
        compare(oldReplacement.history(),replacement.history(),false);
        float[] silence=new float[3000], mixed=new float[3000], wet=new float[3000], before=new float[3000];
        String inactiveFdn=fdnState(actual);
        assertFalse(actual.renderWet(state.snapshot(),mixed,wet,mixed.length)); assertArrayEquals(silence,mixed); assertArrayEquals(silence,wet);
        assertEquals(inactiveFdn,fdnState(actual),"invalid owner must not configure or advance any FDN state");
        actual.close(); expected.close(); assertTrue(state.historyDiagnostics().wetActive());
        var replacementFdn=new ListenerSharedFdn(48000); replacementFdn.configure(env.rt60Seconds(),env.wetGain(),0);
        float[] replacementHistory=oldReplacement.history(), expectedReplacementWet=new float[3000];
        replacementFdn.process(replacementHistory,new float[replacementHistory.length],0,replacementHistory.length);
        assertTrue(oldReplacement.render(source,before,before.length)); replacementFdn.process(before,expectedReplacementWet,0,before.length);
        assertTrue(replacement.renderWet(state.snapshot(),mixed,wet,mixed.length)); compare(before,mixed,false); compare(expectedReplacementWet,wet,true);
        replacement.close(); oldReplacement.close(); state.enterShadowHistoryMode(); assertEquals(0,state.historyDiagnostics().historyFrames());
        checks++;
    }

    @Test void renderDoesNotAcquireLifecycleMonitor() throws Exception {
        var producer=new ListenerReverbHistoryProducer(); var snapshot=new ListenerReverbState.Snapshot(sources(1,1,.3),environment(1));
        var session=producer.acquireWet(snapshot); var executor=Executors.newSingleThreadExecutor();
        try {
            synchronized(producer) {
                assertTrue(executor.submit(()->session.renderWet(snapshot,new float[3000],new float[3000],3000)).get(3,TimeUnit.SECONDS));
            }
        } finally { executor.shutdownNow(); session.close(); }
        checks++;
    }

    @Test void lifecycleClosesAdmissionAndWaitsForInFlightRenderAcknowledgement() throws Exception {
        var gate=new SingleOwnerRenderGate(); assertTrue(gate.tryEnter());
        var started=new CountDownLatch(1); var closed=new CountDownLatch(1); var reopen=new CountDownLatch(1);
        var thread=new AtomicReference<Thread>(); var executor=Executors.newSingleThreadExecutor();
        Future<?> control=executor.submit(()->{
            thread.set(Thread.currentThread()); started.countDown();
            synchronized(gate) {
                gate.closeForControl(); closed.countDown();
                try { if(!reopen.await(3,TimeUnit.SECONDS)) throw new AssertionError("reopen timeout"); }
                catch(InterruptedException ex) { throw new AssertionError(ex); }
                gate.openForControl();
            }
        });
        try {
            assertTrue(started.await(3,TimeUnit.SECONDS));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(thread.get().getState()!=Thread.State.TIMED_WAITING && System.nanoTime()<deadline) Thread.onSpinWait();
            assertEquals(Thread.State.TIMED_WAITING,thread.get().getState()); assertFalse(control.isDone());
            assertFalse(gate.tryEnter()); gate.leave(); assertTrue(closed.await(3,TimeUnit.SECONDS));
            assertFalse(gate.tryEnter()); reopen.countDown(); control.get(3,TimeUnit.SECONDS); assertTrue(gate.tryEnter()); gate.leave();
        } finally { reopen.countDown(); executor.shutdownNow(); }
        checks++;
    }

    @Test void dynamicSixSourceMixEnvironmentAndFdnRenderAllocateZeroBytes() {
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported()); bean.setThreadAllocatedMemoryEnabled(true);
        var snapshots=new ListenerReverbState.Snapshot[]{
            new ListenerReverbState.Snapshot(sources(1,6,.3),environment(1)),
            new ListenerReverbState.Snapshot(sources(21,6,.2),environment(2)),
            new ListenerReverbState.Snapshot(List.of(),environment(3)),
            new ListenerReverbState.Snapshot(sources(1,6,.15),environment(4))};
        var producer=new ListenerReverbHistoryProducer(); var session=producer.acquireWet(snapshots[0]);
        float[] mixed=new float[64],wet=new float[64]; int faults=0;
        for(int i=0;i<6000;i++) if(!session.renderWet(snapshots[i%4],mixed,wet,64)) faults++;
        assertEquals(0,faults); long id=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(id);
        for(int i=0;i<12000;i++) if(!session.renderWet(snapshots[i%4],mixed,wet,64)) faults++;
        allocatedBytes=bean.getThreadAllocatedBytes(id)-before;
        assertEquals(0,faults); assertEquals(0,allocatedBytes,"whole prepared mix/configure/FDN/render gate path"); session.close(); checks++;
    }

    @AfterAll static void preserve() throws Exception {
        String path=System.getenv("MCFPV_WET_CONFORMANCE_OUTPUT"); if(path==null)return;
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath(); if(!Files.isDirectory(root.resolve("build/research")))root=root.getParent();
        String result=String.format(Locale.ROOT,"{\n  \"passed\": %s,\n  \"completed_checks\": %d,\n  \"compared_frozen_history_mix_wet_samples\": %d,\n  \"maximum_mixed_float_difference\": %.17g,\n  \"maximum_wet_float_difference\": %.17g,\n  \"whole_prepared_wet_allocated_bytes\": %d,\n  \"dynamic_iterations\": 12000,\n  \"native_callback_integrated\": false,\n  \"ordinary_deadlines_qualified\": false,\n  \"physical_error_recomputed\": false,\n  \"full_goal_complete\": false\n}\n",checks==4,checks,comparedSamples,maximumMixedDifference,maximumWetDifference,allocatedBytes);
        Files.writeString(root.resolve(path),result,StandardOpenOption.CREATE_NEW);
    }
}
