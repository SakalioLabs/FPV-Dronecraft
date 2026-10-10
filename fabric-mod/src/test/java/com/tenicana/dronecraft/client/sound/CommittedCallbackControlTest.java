package com.tenicana.dronecraft.client.sound;

import com.mojang.blaze3d.audio.ListenerTransform;
import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import com.tenicana.dronecraft.acoustics.AcousticSourceFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer.Layer;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Generated six-source controls; the native mixer/Java publication fence is not claimed here. */
class CommittedCallbackControlTest {
    private static final VarHandle AUDIO, CURRENT, COMMITTED;
    static {
        try {
            AUDIO = MethodHandles.privateLookupIn(DroneAcousticRenderState.class, MethodHandles.lookup())
                    .findVarHandle(DroneAcousticRenderState.class, "audioSnapshot", DroneAcousticRenderState.AudioSnapshot.class);
            CURRENT = MethodHandles.privateLookupIn(ListenerReverbState.class, MethodHandles.lookup())
                    .findVarHandle(ListenerReverbState.class, "current", AtomicReference.class);
            COMMITTED = MethodHandles.privateLookupIn(DroneCallbackControlCoordinator.class, MethodHandles.lookup())
                    .findVarHandle(DroneCallbackControlCoordinator.class, "committed", AtomicReference.class);
        } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }

    @Test
    void stalledOwnerKeepsDryAndWetOnCommittedControlsAndQueuedPcmUntilCommit() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var old = fixture.frame(1, 0);
            fixture.commit(old);
            var next = fixture.frame(2, 1);
            fixture.publishLive(next);
            fixture.coordinator.stageForTest(next, fixture.tasks::add);
            assertSame(next, fixture.coordinator.pendingFrame());
            assertSame(old, fixture.coordinator.committedFrame());
            assertSame(old.sources().get(5).audioSnapshot(), fixture.dryState().callbackAudioSnapshot(fixture.dry, Layer.PROPELLER));
            assertSame(old.wetSnapshot(), fixture.wetState.callbackSnapshot(fixture.wet));

            var oracleState = CallbackPcmStreamTest.state(old.sources().get(5).audioSnapshot());
            var dryOracle = new ProceduralDroneAudioStream(oracleState, Layer.PROPELLER, Fixture.SEED);
            var wetOracleState = new ListenerReverbState();
            var wetOracle = new ListenerReverbAudioStream(wetOracleState);
            // Both sessions acquire the same empty initial history/environment;
            // the first render, rather than acquisition, applies frozen controls.
            publication(wetOracleState).set(old.wetSnapshot());
            ByteBuffer storage = MemoryUtil.memAlloc(6000);
            try {
                for (int block = 0; block < 8; block++) compareBlock(fixture, dryOracle, wetOracle, storage);
                fixture.tasks.remove(0).run();
                fixture.enroll(next);
                assertSame(next, fixture.coordinator.committedFrame());
                assertSame(next, fixture.coordinator.pendingFrame(), "latest preparation remains observable after owner drain");
                AUDIO.setVolatile(oracleState, next.sources().get(5).audioSnapshot());
                publication(wetOracleState).set(next.wetSnapshot());
                for (int block = 0; block < 8; block++) compareBlock(fixture, dryOracle, wetOracle, storage);
            } finally { dryOracle.close(); wetOracle.close(); MemoryUtil.memFree(storage); }
        }
    }

    @Test
    void queuedOrUnenrolledStreamsKeepWorkingControlsAndRetiredOwnersRetainLastCommit() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var old = fixture.frame(1, 0);
            fixture.commit(old);
            var next = fixture.frame(2, 1);
            fixture.publishLive(next);
            var ordinary = new ProceduralDroneAudioStream(fixture.dryState(), Layer.PROPELLER, Fixture.SEED);
            try {
                assertSame(next.sources().get(5).audioSnapshot(), fixture.dryState().audioSnapshot());
                assertSame(next.sources().get(5).audioSnapshot(), fixture.dryState().callbackAudioSnapshot(ordinary, Layer.PROPELLER));
                assertSame(next.wetSnapshot(), fixture.wetState.snapshot());
                fixture.coordinator.invalidate();
                assertNull(fixture.coordinator.pendingFrame());
                assertSame(old, fixture.coordinator.committedFrame(), "already admitted streams survive until async native close");
                var replacement = new DroneAudioControlFrame(fixture.coordinator.generation(), 3,
                        next.listenerTransform(), next.listener(), List.of(), new ListenerReverbState(),
                        next.environment(), null, null, new Object(), .5f, 1);
                committed(fixture.coordinator).set(replacement);
                assertSame(old.sources().get(5).audioSnapshot(), fixture.dryState().callbackAudioSnapshot(fixture.dry, Layer.PROPELLER));
                assertSame(old.wetSnapshot(), fixture.wetState.callbackSnapshot(fixture.wet));
                assertEquals(128, fixture.dry.readCallback(fixture.address, 128), "retirement does not insert a commit-time EOF/gap");
                assertEquals(128, fixture.wet.readCallback(fixture.address, 128));
            } finally { ordinary.close(); }
        }
    }

    @Test
    void enrolledDryAndSixSourceWetCallbackPathsAllocateZeroAcrossPrecompiledFrameChanges() throws Exception {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        try (Fixture fixture = new Fixture()) {
            DroneAudioControlFrame[] controls = new DroneAudioControlFrame[4];
            for (int index = 0; index < controls.length; index++) controls[index] = fixture.frame(index + 1, index);
            fixture.commit(controls[0]);
            AtomicReference<DroneAudioControlFrame> reference = committed(fixture.coordinator);
            int faults = 0;
            for (int iteration = 0; iteration < 6000; iteration++) {
                reference.set(controls[iteration & 3]);
                if (fixture.dry.readCallback(fixture.address, 128) != 128) faults++;
                if (fixture.wet.readCallback(fixture.address, 128) != 128) faults++;
            }
            long id = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(id);
            for (int iteration = 0; iteration < 12000; iteration++) {
                reference.set(controls[iteration & 3]);
                if (fixture.dry.readCallback(fixture.address, 128) != 128) faults++;
            }
            long dryAllocated = bean.getThreadAllocatedBytes(id) - before;
            before = bean.getThreadAllocatedBytes(id);
            for (int iteration = 0; iteration < 12000; iteration++) {
                reference.set(controls[iteration & 3]);
                if (fixture.wet.readCallback(fixture.address, 128) != 128) faults++;
            }
            long wetAllocated = bean.getThreadAllocatedBytes(id) - before;
            assertEquals(0, faults);
            assertEquals(0, dryAllocated, "includes shared-frame lookup at source slot six");
            assertEquals(0, wetAllocated, "includes shared-frame lookup, prepared six-source mix and FDN");
        }
    }

    @Test
    void enrolledCallbacksDoNotAcquireCoordinatorLifecycleOrStateMonitors() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.commit(fixture.frame(1, 0));
            var field = DroneCallbackControlCoordinator.class.getDeclaredField("lifecycle");
            field.setAccessible(true);
            Object lifecycle = field.get(fixture.coordinator);
            var executor = Executors.newSingleThreadExecutor();
            try {
                synchronized (lifecycle) { synchronized (fixture.dryState()) {
                    assertEquals(128, executor.submit(() -> fixture.dry.readCallback(fixture.address, 128)).get(3, TimeUnit.SECONDS));
                    assertEquals(128, executor.submit(() -> fixture.wet.readCallback(fixture.address, 128)).get(3, TimeUnit.SECONDS));
                } }
            } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS)); }
        }
    }

    private static void compareBlock(Fixture fixture, ProceduralDroneAudioStream dryOracle,
            ListenerReverbAudioStream wetOracle, ByteBuffer storage) {
        long address = MemoryUtil.memAddress(storage);
        var dryExpected = dryOracle.read(6000).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        assertEquals(6000, fixture.dry.readCallback(address, 6000));
        for (int index = 0; index < 3000; index++)
            assertTrue(Math.abs(dryExpected.get(index) - MemoryUtil.memGetShort(address + 2L * index)) <= 1,
                    "independent queued dry differential remains within the existing S16 gate");
        var wetExpected = wetOracle.read(6000).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        assertEquals(6000, fixture.wet.readCallback(address, 6000));
        for (int index = 0; index < 3000; index++)
            assertEquals(wetExpected.get(index), MemoryUtil.memGetShort(address + 2L * index),
                    "same generated history and frozen control sequence keep queued wet PCM exact");
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<ListenerReverbState.Snapshot> publication(ListenerReverbState state) {
        return (AtomicReference<ListenerReverbState.Snapshot>) CURRENT.get(state);
    }
    @SuppressWarnings("unchecked")
    private static AtomicReference<DroneAudioControlFrame> committed(DroneCallbackControlCoordinator coordinator) {
        return (AtomicReference<DroneAudioControlFrame>) COMMITTED.get(coordinator);
    }

    private static final class Fixture implements AutoCloseable {
        static final int SEED = 31 * 162 + Layer.PROPELLER.ordinal();
        final DroneAcousticRenderState[] states = new DroneAcousticRenderState[6];
        final ListenerReverbState wetState = new ListenerReverbState();
        final DroneCallbackControlCoordinator coordinator = new DroneCallbackControlCoordinator((frame, engine) -> true);
        final List<Runnable> tasks = new ArrayList<>();
        final Object[] motors = new Object[6], propellers = new Object[6];
        final ProceduralDroneAudioStream dry;
        final ListenerReverbAudioStream wet;
        final ByteBuffer storage = MemoryUtil.memAlloc(128);
        final long address = MemoryUtil.memAddress(storage);

        Fixture() {
            for (int index = 0; index < 6; index++) {
                states[index] = new DroneAcousticRenderState();
                states[index].useCallbackControls(coordinator);
                motors[index] = new Object(); propellers[index] = new Object();
            }
            wetState.useCallbackControls(coordinator);
            dry = new ProceduralDroneAudioStream(states[5], Layer.PROPELLER, SEED);
            propellers[5] = dry;
            wet = new ListenerReverbAudioStream(wetState);
            assertTrue(dry.prepareCallback()); assertTrue(wet.prepareCallback());
        }

        DroneAcousticRenderState dryState() { return states[5]; }
        DroneAudioControlFrame frame(long version, int phase) {
            var base = CallbackPcmStreamTest.snapshot(phase, new double[]{0, 12, -12, 24}[phase & 3]);
            AcousticListenerFrame listener = base.listener();
            var sources = new ArrayList<DroneAudioControlFrame.SourceControl>();
            for (int index = 0; index < 6; index++) {
                var source = base.source();
                var geometry = new AcousticSourceFrame(157 + index, version,
                        new AcousticVector(index + 1 + phase, 0, 0), source.velocityMetersPerSecond(),
                        source.rotorDiskNormal(), source.acousticApertureRadiusMeters(), source.rotors());
                var snapshot = new DroneAcousticRenderState.AudioSnapshot(geometry, listener, base.preDopplerEmission(),
                        base.emission(), base.dopplerFrequencyRatio(), base.soundSpeedMetersPerSecond(), base.calibration());
                sources.add(new DroneAudioControlFrame.SourceControl(157 + index, states[index], snapshot,
                        base.emission(), null, null, null, null, motors[index], propellers[index],
                        .4 + phase * .1, .5 + phase * .1, .4f, .5f, 1, 1));
            }
            return new DroneAudioControlFrame(coordinator.generation(), version, ListenerTransform.INITIAL,
                    listener, sources, wetState, new FdnEnvironmentMapper.Controls(version,
                    new AcousticBands(.8, .6, .4), .3 + phase * .02, .15), null, null, wet, .5f, 1);
        }
        void publishLive(DroneAudioControlFrame frame) {
            for (int index = 0; index < 6; index++) AUDIO.setVolatile(states[index], frame.sources().get(index).audioSnapshot());
            publication(wetState).set(frame.wetSnapshot());
        }
        void enroll(DroneAudioControlFrame frame) {
            dry.enrollCallbackControls(frame.sources().get(5).audioSnapshot());
            wet.enrollCallbackControls(frame.wetSnapshot());
        }
        void commit(DroneAudioControlFrame frame) {
            coordinator.stageForTest(frame, tasks::add); tasks.remove(0).run(); enroll(frame);
        }
        @Override public void close() { dry.close(); wet.close(); MemoryUtil.memFree(storage); }
    }
}
