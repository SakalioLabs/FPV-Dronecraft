package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.*;
import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer.Layer;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.management.ManagementFactory;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Generated controls only; same-JVM queued PCM and callback ownership gates. */
class CallbackPcmStreamTest {
    private static final VarHandle AUDIO;
    static {
        try {
            AUDIO = MethodHandles.privateLookupIn(DroneAcousticRenderState.class, MethodHandles.lookup())
                    .findVarHandle(DroneAcousticRenderState.class, "audioSnapshot", DroneAcousticRenderState.AudioSnapshot.class);
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
    private static int checks, maximumDryS16Difference, maximumWetS16Difference;
    private static long drySamples, wetSamples, dryAllocated = -1, wetAllocated = -1;

    static DroneAcousticRenderState.AudioSnapshot snapshot(int version, double gain) {
        var zero = new AcousticVector(0,0,0);
        var emission = new AcousticEmissionFrame(List.of(
                new TonalComponent(TonalComponent.Kind.ELECTRICAL,0,1,700+version*37,.08,0),
                new TonalComponent(TonalComponent.Kind.BLADE_PASS,0,1,1000+version*53,.1,.2),
                new TonalComponent(TonalComponent.Kind.BLADE_PASS,1,2,1900+version*41,.04,.5)),
                new AcousticBands(.00001,.00002,.00001));
        return new DroneAcousticRenderState.AudioSnapshot(
                new AcousticSourceFrame(157,version,zero,zero,new AcousticVector(0,1,0),.127,
                        List.of(new RotorAcousticState(12000,.5,.5,.0635,3,7,1,0))),
                new AcousticListenerFrame(new AcousticVector(0,0,1),zero),emission,emission,1,343,
                new AcousticSourceProfile.Calibration(true,true,gain,gain));
    }
    static DroneAcousticRenderState state(DroneAcousticRenderState.AudioSnapshot snapshot) {
        var state = new DroneAcousticRenderState(); AUDIO.setVolatile(state,snapshot); return state;
    }
    private static ListenerReverbState.Snapshot wetSnapshot(int version) {
        var sources = new ArrayList<ListenerReverbState.Source>();
        if (version != 2) for (int id=0;id<6;id++)
            sources.add(new ListenerReverbState.Source((version==1?21:1)+id,snapshot(version,0).emission(),.3,.2));
        return new ListenerReverbState.Snapshot(sources,new FdnEnvironmentMapper.Controls(
                version,new AcousticBands(1.2,.9,.6),.3+version*.05,.2));
    }
    @SuppressWarnings("unchecked")
    private static AtomicReference<ListenerReverbState.Snapshot> publication(ListenerReverbState state) throws Exception {
        var field=ListenerReverbState.class.getDeclaredField("current"); field.setAccessible(true);
        return (AtomicReference<ListenerReverbState.Snapshot>)field.get(state);
    }

    @Test void preparedDryMatchesQueuedPcmAcrossFrequencyAndCalibrationUpdates() throws Exception {
        var storage=MemoryUtil.memAlloc(CallbackPcmStream.MAXIMUM_CALLBACK_FRAMES*2);
        try {
            for(Layer layer:new Layer[]{Layer.MOTOR,Layer.PROPELLER}) {
                var state=state(snapshot(0,0)); var queued=new ProceduralDroneAudioStream(state,layer,157);
                var callback=new ProceduralDroneAudioStream(state,layer,157); assertTrue(callback.prepareCallback());
                for(int block=0;block<48;block++) {
                    var current=snapshot(block%4,new double[]{0,12,-12,24}[block%4]); AUDIO.setVolatile(state,current);
                    int frames=new int[]{64,3000,1024,960}[block%4];
                    var expected=queued.read(frames*2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                    assertEquals(frames*2,callback.readCallback(MemoryUtil.memAddress(storage),frames*2));
                    for(int i=0;i<frames;i++) {
                        int difference=Math.abs(expected.get(i)-MemoryUtil.memGetShort(MemoryUtil.memAddress(storage)+2L*i));
                        maximumDryS16Difference=Math.max(maximumDryS16Difference,difference);
                        assertTrue(difference<=1,"fixed same-JVM queued/prepared S16 gate"); drySamples++;
                    }
                }
                callback.close();queued.close();
            }
        } finally { MemoryUtil.memFree(storage); }
        checks++;
    }

    @Test void preparedWetMatchesQueuedSixSourceHistoryAndEnvironmentPcm() throws Exception {
        var a=new ListenerReverbState();var b=new ListenerReverbState();
        var ap=publication(a);var bp=publication(b);var initial=wetSnapshot(0);ap.set(initial);bp.set(initial);
        for(int tick=0;tick<12;tick++){a.advanceShadowHistory();b.advanceShadowHistory();}
        var queued=new ListenerReverbAudioStream(a);var callback=new ListenerReverbAudioStream(b);
        assertEquals(queued.prerollFrames(),callback.prerollFrames());assertTrue(callback.prepareCallback());
        var storage=MemoryUtil.memAlloc(6000);
        try {
            for(int block=0;block<32;block++) {
                var current=wetSnapshot(block%4);ap.set(current);bp.set(current);
                var expected=queued.read(6000).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                assertEquals(6000,callback.readCallback(MemoryUtil.memAddress(storage),6000));
                for(int i=0;i<3000;i++) {
                    int difference=Math.abs(expected.get(i)-MemoryUtil.memGetShort(MemoryUtil.memAddress(storage)+2L*i));
                    maximumWetS16Difference=Math.max(maximumWetS16Difference,difference);
                    assertEquals(0,difference,"unchanged prepared wet mix/FDN/quantization");wetSamples++;
                }
            }
        } finally { callback.close();queued.close();MemoryUtil.memFree(storage); }
        checks++;
    }

    @Test void nativeWriterPreservesCalibrationSaturationAndDoesNotTouchGuardBytes() {
        float[] values={-2,-1,-.5f,-1f/32767,0,1f/32767,.5f,1,2};var storage=MemoryUtil.memAlloc(64);
        try {
            for(double amplitude:new double[]{0,.25,1,Math.pow(10,24.0/20)}) {
                long base=MemoryUtil.memAddress(storage);MemoryUtil.memSet(base,0x5a,64);
                Pcm16NativeWriter.write(values,values.length,amplitude,base+16);
                for(int i=0;i<values.length;i++) {
                    float value=(float)(values[i]*amplitude);
                    short expected=(short)Math.round(Math.max(-1f,Math.min(1f,value))*32767f);
                    assertEquals(expected,MemoryUtil.memGetShort(base+16+2L*i));
                }
                for(int i=0;i<16;i++)assertEquals((byte)0x5a,MemoryUtil.memGetByte(base+i));
                for(int i=16+values.length*2;i<64;i++)assertEquals((byte)0x5a,MemoryUtil.memGetByte(base+i));
            }
        } finally {MemoryUtil.memFree(storage);}checks++;
    }

    @Test void callbackAdmissionInvalidRequestsClosureAndQueuedFallbackAreExplicit() throws Exception {
        var storage=MemoryUtil.memAlloc(128);long address=MemoryUtil.memAddress(storage);
        var dry=new ProceduralDroneAudioStream(new DroneAcousticRenderState(),Layer.MOTOR,157);
        var wet=new ListenerReverbAudioStream(new ListenerReverbState());
        try {
            for(CallbackPcmStream stream:List.of(dry,wet)) {
                assertEquals(0,stream.readCallback(address,128));assertTrue(stream.prepareCallback());assertTrue(stream.prepareCallback());
                MemoryUtil.memSet(address,0x5a,128);
                for(int bytes:new int[]{0,-2,1,CallbackPcmStream.MAXIMUM_CALLBACK_FRAMES*2+2})
                    assertEquals(0,stream.readCallback(address,bytes));
                assertEquals(0,stream.readCallback(0,128));
                for(int i=0;i<128;i++)assertEquals((byte)0x5a,MemoryUtil.memGetByte(address+i));
                assertEquals(128,stream.readCallback(address,128));
                for(int i=0;i<128;i++)assertEquals(0,MemoryUtil.memGetByte(address+i));
                assertEquals(0,stream.read(128).remaining());stream.close();stream.close();
                assertEquals(0,stream.readCallback(address,128));assertFalse(stream.prepareCallback());
            }
            var fallback=new ProceduralDroneAudioStream(state(snapshot(0,0)),Layer.MOTOR,157);
            assertEquals(128,fallback.read(128).remaining());assertFalse(fallback.prepareCallback());fallback.close();
            var diagnostic=new ProceduralDroneAudioStream(state(snapshot(0,24)),Layer.FULL,157);
            assertFalse(diagnostic.prepareCallback());assertEquals(128,diagnostic.read(128).remaining());diagnostic.close();
        } finally {dry.close();wet.close();MemoryUtil.memFree(storage);}checks++;
    }

    @Test void callbackDoesNotAcquireStreamOrDistanceLifecycleMonitors() throws Exception {
        var state=state(snapshot(0,0));var dry=new ProceduralDroneAudioStream(state,Layer.MOTOR,157);
        var wet=new ListenerReverbAudioStream(new ListenerReverbState());var executor=Executors.newSingleThreadExecutor();
        var storage=MemoryUtil.memAlloc(128);long address=MemoryUtil.memAddress(storage);
        try {
            assertTrue(dry.prepareCallback());assertTrue(wet.prepareCallback());
            synchronized(state) { synchronized(dry) {
                assertEquals(128,executor.submit(()->dry.readCallback(address,128)).get(3,TimeUnit.SECONDS));
            }}
            synchronized(wet) {assertEquals(128,executor.submit(()->wet.readCallback(address,128)).get(3,TimeUnit.SECONDS));}
        } finally {executor.shutdownNow();dry.close();wet.close();MemoryUtil.memFree(storage);}checks++;
    }

    @Test void completeDryAndSixSourceWetNativePcmPathsAllocateZeroAfterPreparation() throws Exception {
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());bean.setThreadAllocatedMemoryEnabled(true);
        var controls=new DroneAcousticRenderState.AudioSnapshot[]{snapshot(0,0),snapshot(1,12),snapshot(2,-12),snapshot(3,24)};
        var state=state(controls[0]);var dry=new ProceduralDroneAudioStream(state,Layer.PROPELLER,157);assertTrue(dry.prepareCallback());
        var wetControls=new ListenerReverbState.Snapshot[]{wetSnapshot(0),wetSnapshot(1),wetSnapshot(2),wetSnapshot(3)};
        var wetState=new ListenerReverbState();var publish=publication(wetState);publish.set(wetControls[0]);
        var wet=new ListenerReverbAudioStream(wetState);assertTrue(wet.prepareCallback());
        var storage=MemoryUtil.memAlloc(128);long address=MemoryUtil.memAddress(storage);int faults=0;
        try {
            for(int i=0;i<6000;i++) {AUDIO.setVolatile(state,controls[i%4]);publish.set(wetControls[i%4]);
                if(dry.readCallback(address,128)!=128)faults++;if(wet.readCallback(address,128)!=128)faults++;}
            assertEquals(0,faults);long id=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(id);
            for(int i=0;i<12000;i++){AUDIO.setVolatile(state,controls[i%4]);if(dry.readCallback(address,128)!=128)faults++;}
            dryAllocated=bean.getThreadAllocatedBytes(id)-before;before=bean.getThreadAllocatedBytes(id);
            for(int i=0;i<12000;i++){publish.set(wetControls[i%4]);if(wet.readCallback(address,128)!=128)faults++;}
            wetAllocated=bean.getThreadAllocatedBytes(id)-before;
            assertEquals(0,faults);assertEquals(0,dryAllocated);assertEquals(0,wetAllocated);
        } finally {dry.close();wet.close();MemoryUtil.memFree(storage);}checks++;
    }

    @AfterAll static void report() throws Exception {
        String relative=System.getenv("MCFPV_CALLBACK_CONFORMANCE_OUTPUT");if(relative==null)return;
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();if(!Files.isDirectory(root.resolve("build/research")))root=root.getParent();
        String json=String.format(Locale.ROOT,"{\n  \"passed\": %s,\n  \"completed_checks\": %d,\n  \"dry_compared_S16_samples\": %d,\n  \"wet_compared_S16_samples\": %d,\n  \"maximum_dry_S16_difference\": %d,\n  \"maximum_wet_S16_difference\": %d,\n  \"dry_dynamic_allocated_bytes\": %d,\n  \"wet_dynamic_allocated_bytes\": %d,\n  \"dynamic_iterations_per_path\": 12000,\n  \"normal_endpoint_deadline_qualified\": false,\n  \"physical_error_recomputed\": false\n}\n",
                checks==6,checks,drySamples,wetSamples,maximumDryS16Difference,maximumWetS16Difference,dryAllocated,wetAllocated);
        Files.writeString(root.resolve(relative),json,StandardOpenOption.CREATE_NEW);
    }
}
