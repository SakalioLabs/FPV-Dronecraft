package com.tenicana.dronecraft.client.sound;

import com.google.gson.*;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import com.tenicana.dronecraft.acoustics.*;
import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer.Layer;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import com.tenicana.dronecraft.client.mixin.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Actual managed Library Channels and production streams; generated controls only. */
final class ProductionCallbackClientGameTest {
    private static final int WARM=24_000,POST=48_000,STEP=960;
    private static final ByteArrayOutputStream input=new ByteArrayOutputStream(),output=new ByteArrayOutputStream(),oracle=new ByteArrayOutputStream();
    private record Voice(Channel channel,AudioStream stream,CallbackStreamCapture.Capture capture,
                         DroneAcousticRenderState state,Layer layer) { }
    static boolean requested() {
        return MainContextOutputCapture.requested() && MainContextOutputCapture.protocol().has("production_callback_experiment");
    }
    static void run(ClientGameTestContext context) {
        JsonObject protocol=MainContextOutputCapture.protocol(),report=new JsonObject();JsonArray rows=new JsonArray();Throwable failure=null;
        Path root=Path.of(protocol.get("workspace_root").getAsString());report.addProperty("started_utc",Instant.now().toString());
        report.addProperty("protocol_sha256",MainContextOutputCapture.sha(read(Path.of(System.getProperty(MainContextOutputCapture.PROPERTY)))));
        report.add("evidence_sha256",protocol.get("evidence_sha256").deepCopy());
        try {
            JsonObject loaded=new JsonObject();
            for(var item:protocol.getAsJsonArray("required_loaded_class_paths")) {
                String path=item.getAsString(),resource="/"+path.substring(path.indexOf("com/tenicana/"));
                try(var stream=ProductionCallbackClientGameTest.class.getResourceAsStream(resource)) {
                    if(stream==null)throw new IllegalStateException("missing loaded class "+resource);
                    loaded.addProperty(path,MainContextOutputCapture.sha(stream.readAllBytes()));
                }
            }
            report.add("loaded_class_sha256",loaded);
            report.addProperty("legacy_wet_mixer_probe_methods_absent",Arrays.stream(ListenerReverbHistoryProducer.class.getDeclaredMethods())
                    .noneMatch(method->method.getName().contains("fpvdrone$begin") || method.getName().contains("fpvdrone$finish") || method.getName().contains("fpvdrone$layer")));
            try(TestSingleplayerContext world=context.worldBuilder().create()) {
                world.getClientWorld().waitForChunksDownload();world.getServer().runCommand("gamerule spawn_mobs false");
                for(int epoch=0;epoch<2;epoch++) {
                    context.runOnClient(client->engine(client).stopAll());final int generation=epoch;
                    sound(context,()->{identity(report,generation);return null;});
                    for(var item:protocol.getAsJsonArray("expected_cases")) {
                        JsonObject declared=item.getAsJsonObject();rows.add(sound(context,()->capture(declared,generation)));
                    }
                    if(epoch==0)context.runOnClient(client->client.getSoundManager().reload());
                }
            }
            context.runOnClient(client->client.getSoundManager().destroy());report.addProperty("status","production_callback_captured");
        } catch(Throwable error) {
            failure=error;report.addProperty("status","production_callback_failed");report.addProperty("failure",error.toString());
            try{context.runOnClient(client->client.getSoundManager().destroy());}catch(Throwable cleanup){report.addProperty("cleanup_failure",cleanup.toString());}
        } finally {System.clearProperty(NativeCallbackBinding.ENABLE_PROPERTY);CallbackStreamCapture.clearRegistrations();}
        report.add("rows",rows);report.add("epochs",MainContextOutputCapture.epochs());
        JsonObject scope=new JsonObject();
        for(String key:new String[]{"actual_Minecraft_client","actual_main_Library_software_loopback","managed_Channel_callback","production_dry_wet_DSP","generated_controls_only"})scope.addProperty(key,failure==null);
        for(String key:new String[]{"actual_six_entity_manager_selection","full_six_drone_capacity","physical_endpoint_verified","ordinary_wall_clock_deadlines_qualified","JVM_GC_realtime_qualified","physical_error_recomputed","release_profile_eligible","full_goal_complete"})scope.addProperty(key,false);
        report.add("scope",scope);writePcm(root,protocol,report,"input",input.toByteArray());writePcm(root,protocol,report,"output",output.toByteArray());
        writePcm(root,protocol,report,"oracle",oracle.toByteArray());
        report.addProperty("finished_utc",Instant.now().toString());
        try{Files.writeString(root.resolve(protocol.get("output_report").getAsString()),new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report)+"\n",StandardOpenOption.CREATE_NEW);}
        catch(Exception error){throw new IllegalStateException(error);}
        if(failure!=null)throw new IllegalStateException("production callback capture failed",failure);
    }
    private static JsonObject capture(JsonObject declared,int generation) {
        JsonObject row=declared.deepCopy();row.addProperty("generation",generation);
        Library library=library();var access=(MainOutputLibraryAccessor)library;long device=access.mainOutput$getDevice();
        MainContextOutputCapture.requireCurrent(device,access.mainOutput$getContext());
        row.addProperty("device",device);row.addProperty("context",access.mainOutput$getContext());row.addProperty("owner_thread_id",Thread.currentThread().threadId());
        render(device,4096);CallbackStreamCapture.clearRegistrations();
        boolean fallback=declared.get("fallback").getAsBoolean();System.setProperty(NativeCallbackBinding.ENABLE_PROPERTY,Boolean.toString(!fallback));
        List<Voice> voices=new ArrayList<>();List<NativeCallbackBinding> bindings=new ArrayList<>();Set<Channel> released=Collections.newSetFromMap(new IdentityHashMap<>());
        String kind=declared.get("kind").getAsString();boolean cohort=kind.equals("six");ListenerReverbState wetState=null;
        try {
            if(cohort) {
                List<ListenerReverbState.Source> wetSources=new ArrayList<>();
                for(int id=1;id<=6;id++) {
                    var state=state(id,0);
                    voices.add(dry(library,state,Layer.MOTOR,id));voices.add(dry(library,state,Layer.PROPELLER,id));
                    wetSources.add(new ListenerReverbState.Source(id,state.audioSnapshot().emission(),.3,.2));
                }
                wetState=wetState(wetSources);voices.add(wet(library,wetState));
            } else if(kind.equals("wet")) {
                wetState=wetState(List.of(new ListenerReverbState.Source(1,emission(1),.3,.2)));voices.add(wet(library,wetState));
            } else voices.add(dry(library,state(1,0),Layer.valueOf(kind),1));
            JsonArray before=new JsonArray();
            for(Voice voice:voices) {
                Channel channel=voice.channel();channel.setRelative(true);channel.setSelfPosition(new Vec3(0,0,-1));channel.setPitch(1);channel.setVolume(.5f);channel.setLooping(false);
                if(voice.state()==null)channel.disableAttenuation();
                var binding=((CallbackChannelAccess)channel).callbackBinding();
                if(fallback && binding!=null || !fallback && (binding==null || !binding.attached()))throw new IllegalStateException("wrong callback admission "+((CallbackChannelAccess)channel).callbackDisabledReason());
                if(binding!=null)bindings.add(binding);channel.play();before.add(nativeSnapshot(voice,binding));
            }
            row.add("before",before);row.addProperty("managed_channels",voices.size());row.addProperty("streaming_pool",library.getDebugString());
            appendRender(row,"warm",fallback?renderQueued(device,voices,WARM):render(device,WARM));
            double gainDb=declared.get("new_calibration_db").getAsDouble();
            JsonArray changes=new JsonArray();
            for(Voice voice:voices) {
                JsonObject change=new JsonObject();change.addProperty("calls",voice.capture().calls);change.addProperty("input_frames",voice.capture().cursor);
                voice.capture().controlVersion=1;
                if(voice.state()!=null)publish(voice.state(),snapshot(Math.toIntExact(voice.state().audioSnapshot().source().entityId()),gainDb));
                changes.add(change);
            }
            if(wetState!=null && gainDb!=0)wetState.publishSources(List.of(new ListenerReverbState.Source(1,emission(1),.15,.1)));
            row.add("change",changes);
            boolean concurrentStop=declared.has("concurrent_stop") && declared.get("concurrent_stop").getAsBoolean();
            if(declared.get("worker").getAsBoolean())appendRender(row,"post",workerRender(device,row,concurrentStop?voices:List.of(),released));
            else appendRender(row,"post",fallback?renderQueued(device,voices,POST):render(device,POST));
            JsonArray after=new JsonArray(),captures=new JsonArray();
            for(int index=0;index<voices.size();index++) {
                Voice voice=voices.get(index);voice.channel().updateStream();
                var binding=((CallbackChannelAccess)voice.channel()).callbackBinding();if(!concurrentStop)after.add(nativeSnapshot(voice,binding));
                var data=voice.capture().pcmBytes();var capture=voice.capture().snapshot();capture.addProperty("layer",voice.layer()==null?"WET":voice.layer().name());
                int entityId=voice.state()==null?0:Math.toIntExact(voice.state().audioSnapshot().source().entityId());
                capture.addProperty("entity_id",entityId);capture.addProperty("seed",entityId==0?0:31*entityId+(voice.layer()==Layer.MOTOR?17:23));
                capture.addProperty("input_byte_offset",input.size());capture.addProperty("input_bytes",data.length);capture.addProperty("input_sha256",MainContextOutputCapture.sha(data));input.writeBytes(data);captures.add(capture);
                if(!fallback) {
                    byte[] reference=queuedOracle(voice,gainDb,cohort);
                    capture.addProperty("oracle_byte_offset",oracle.size());capture.addProperty("oracle_bytes",reference.length);capture.addProperty("oracle_sha256",MainContextOutputCapture.sha(reference));oracle.writeBytes(reference);
                }
            }
            row.add("after",after);row.add("captures",captures);
            JsonArray cleanup=new JsonArray();
            for(Voice voice:voices) {
                var binding=((CallbackChannelAccess)voice.channel()).callbackBinding();long calls=voice.capture().calls;
                int source=((ChannelSourceAccessor)voice.channel()).fpvdrone$getSource();
                if(!concurrentStop){library.releaseChannel(voice.channel());released.add(voice.channel());}JsonObject closed=new JsonObject();closed.addProperty("source_deleted",!AL10.alIsSource(source));closed.addProperty("calls_before_detach",calls);
                if(binding!=null) {
                    closed.addProperty("callback_closed",binding.closed());closed.addProperty("callback_cleared",binding.callbackCleared());closed.addProperty("buffer_deleted",binding.bufferDeleted());closed.addProperty("closure_freed",binding.closureFreed());closed.addProperty("quarantined",binding.quarantined());closed.addProperty("failure",binding.failure());
                }
                cleanup.add(closed);
            }
            render(device,4096);
            for(int index=0;index<voices.size();index++)cleanup.get(index).getAsJsonObject().addProperty("calls_after_detach_render",voices.get(index).capture().calls);
            row.add("cleanup",cleanup);voices.clear();check(device);row.addProperty("remaining_pool",library.getDebugString());
        } finally {
            for(Voice voice:voices)if(!released.contains(voice.channel()))library.releaseChannel(voice.channel());CallbackStreamCapture.clearRegistrations();System.clearProperty(NativeCallbackBinding.ENABLE_PROPERTY);
        }
        return row;
    }
    private static Voice dry(Library library,DroneAcousticRenderState state,Layer layer,int id) {
        var stream=new ProceduralDroneAudioStream(state,layer,31*id+(layer==Layer.MOTOR?17:23),true);
        var capture=CallbackStreamCapture.register(stream,200_000,2048);Channel channel=null;
        try{channel=library.acquireChannel(Library.Pool.STREAMING);if(channel==null)throw new IllegalStateException("managed streaming pool exhausted");channel.attachBufferStream(stream);return new Voice(channel,stream,capture,state,layer);}
        catch(Throwable error){if(channel!=null)library.releaseChannel(channel);stream.close();throw error;}
    }
    private static Voice wet(Library library,ListenerReverbState state) {
        var stream=new ListenerReverbAudioStream(state);var capture=CallbackStreamCapture.register(stream,200_000,2048);Channel channel=null;
        try{channel=library.acquireChannel(Library.Pool.STREAMING);if(channel==null)throw new IllegalStateException("managed wet pool exhausted");channel.attachBufferStream(stream);return new Voice(channel,stream,capture,null,null);}
        catch(Throwable error){if(channel!=null)library.releaseChannel(channel);stream.close();throw error;}
    }
    private static ListenerReverbState wetState(List<ListenerReverbState.Source> sources) {
        var state=new ListenerReverbState();state.publishSources(sources);state.publishEnvironment(new FdnEnvironmentMapper.Controls(1,new AcousticBands(1.2,.9,.6),.35,.2));
        for(int tick=0;tick<12;tick++)state.advanceShadowHistory();return state;
    }
    private static byte[] queuedOracle(Voice voice,double gainDb,boolean cohort) {
        CallbackStreamCapture.Capture capture=voice.capture();ByteArrayOutputStream result=new ByteArrayOutputStream();
        AudioStream reference;DroneAcousticRenderState dryState=null;ListenerReverbState wetState=null;
        if(voice.state()!=null) {
            int id=Math.toIntExact(voice.state().audioSnapshot().source().entityId());dryState=state(id,0);
            reference=new ProceduralDroneAudioStream(dryState,voice.layer(),31*id+(voice.layer()==Layer.MOTOR?17:23));
        } else {
            List<ListenerReverbState.Source> sources=new ArrayList<>();
            for(int id=1;id<=(cohort?6:1);id++)sources.add(new ListenerReverbState.Source(id,emission(id),.3,.2));
            wetState=wetState(sources);reference=new ListenerReverbAudioStream(wetState);
        }
        int version=0;
        try {
            for(int call=0;call<capture.calls;call++) {
                if(capture.controlVersions[call]!=version) {
                    version=capture.controlVersions[call];
                    if(dryState!=null)publish(dryState,snapshot(Math.toIntExact(dryState.audioSnapshot().source().entityId()),gainDb));
                    if(wetState!=null && gainDb!=0)wetState.publishSources(List.of(new ListenerReverbState.Source(1,emission(1),.15,.1)));
                }
                ByteBuffer bytes=reference.read(capture.returnedBytes[call]);byte[] chunk=new byte[bytes.remaining()];bytes.get(chunk);result.writeBytes(chunk);
            }
            reference.close();
        }catch(Exception error){throw new IllegalStateException("queued oracle",error);}
        return result.toByteArray();
    }
    private static AcousticEmissionFrame emission(int id) {
        return new AcousticEmissionFrame(List.of(new TonalComponent(TonalComponent.Kind.ELECTRICAL,0,1,700+id*11,.08,0),new TonalComponent(TonalComponent.Kind.BLADE_PASS,0,1,1000+id*17,.1,.2)),new AcousticBands(.00001,.00002,.00001));
    }
    private static DroneAcousticRenderState state(int id,double gainDb) {var state=new DroneAcousticRenderState();publish(state,snapshot(id,gainDb));return state;}
    private static DroneAcousticRenderState.AudioSnapshot snapshot(int id,double gainDb) {
        var zero=new AcousticVector(0,0,0);var emission=emission(id);
        return new DroneAcousticRenderState.AudioSnapshot(new AcousticSourceFrame(id,0,zero,zero,new AcousticVector(0,1,0),.127,List.of(new RotorAcousticState(12000,.5,.5,.0635,3,7,1,0))),new AcousticListenerFrame(new AcousticVector(0,0,1),zero),emission,emission,1,343,new AcousticSourceProfile.Calibration(true,true,gainDb,gainDb));
    }
    private static void publish(DroneAcousticRenderState state,DroneAcousticRenderState.AudioSnapshot snapshot) {
        try{Field field=DroneAcousticRenderState.class.getDeclaredField("audioSnapshot");field.setAccessible(true);field.set(state,snapshot);}catch(Exception error){throw new IllegalStateException(error);}
    }
    private static JsonObject nativeSnapshot(Voice voice,NativeCallbackBinding binding) {
        int source=((ChannelSourceAccessor)voice.channel()).fpvdrone$getSource();JsonObject row=new JsonObject();row.addProperty("source",source);
        row.addProperty("source_type",AL10.alGetSourcei(source,AL10.AL_SOURCE_TYPE));row.addProperty("source_state",AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE));row.addProperty("buffer",AL10.alGetSourcei(source,AL10.AL_BUFFER));
        row.addProperty("queued",AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED));row.addProperty("processed",AL10.alGetSourcei(source,AL10.AL_BUFFERS_PROCESSED));
        row.addProperty("native_gain",AL10.alGetSourcef(source,AL10.AL_GAIN));row.addProperty("reference_distance",AL10.alGetSourcef(source,AL10.AL_REFERENCE_DISTANCE));row.addProperty("rolloff",AL10.alGetSourcef(source,AL10.AL_ROLLOFF_FACTOR));
        row.addProperty("distance_verified",voice.stream() instanceof ProceduralDroneAudioStream dry && dry.distanceOwnerVerified());
        if(binding!=null) {
            row.addProperty("registered_callback",SOFTCallbackBuffer.alGetBufferPtrSOFT(binding.buffer(),SOFTCallbackBuffer.AL_BUFFER_CALLBACK_FUNCTION_SOFT));row.addProperty("registered_user",SOFTCallbackBuffer.alGetBufferPtrSOFT(binding.buffer(),SOFTCallbackBuffer.AL_BUFFER_CALLBACK_USER_PARAM_SOFT));
            row.addProperty("callback_address",binding.callbackAddress());row.addProperty("user_pointer",binding.userPointer());row.addProperty("cif_return_type",binding.cifReturnType());row.addProperty("cif_return_size",binding.cifReturnSize());
            row.addProperty("callback_calls",binding.calls());row.addProperty("callback_fault",binding.fault());row.addProperty("requested_bytes",binding.requestedBytes());row.addProperty("returned_bytes",binding.returnedBytes());
        }return row;
    }
    private static byte[] workerRender(long device,JsonObject row,List<Voice> closeDuringMix,Set<Channel> released) {
        var capabilities=ALC.getCapabilities();Throwable[] error=new Throwable[1];long[] id=new long[1];byte[][] result=new byte[1][];long start=System.nanoTime();
        Thread worker=new Thread(()->{
            id[0]=Thread.currentThread().threadId();
            try{ALC.setCapabilities(capabilities);result[0]=renderWorker(device,POST);}catch(Throwable t){error[0]=t;}finally{try{ALC.setCapabilities(null);}catch(Throwable t){if(error[0]==null)error[0]=t;}}
        },"D171-sole-loopback-renderer");worker.start();boolean interrupted=false;
        Throwable ownerFailure=null;
        try { if(!closeDuringMix.isEmpty()) {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);boolean observed=false;
            while(worker.isAlive() && System.nanoTime()<deadline) {
                for(Voice voice:closeDuringMix)if(voice.capture().active){observed=true;break;}
                if(observed)break;Thread.onSpinWait();
            }
            row.addProperty("callback_active_observed_before_stop",observed);
            long stopStarted=System.nanoTime();for(Voice voice:closeDuringMix){library().releaseChannel(voice.channel());released.add(voice.channel());}
            row.addProperty("concurrent_stop_wall_ns",System.nanoTime()-stopStarted);
            JsonArray atStop=new JsonArray();for(Voice voice:closeDuringMix){JsonObject point=new JsonObject();point.addProperty("calls_after_stop",voice.capture().calls);point.addProperty("active_after_stop",voice.capture().active);atStop.add(point);}row.add("at_stop",atStop);
        }}catch(Throwable errorDuringStop){ownerFailure=errorDuringStop;}
        for(;;){try{worker.join();break;}catch(InterruptedException e){interrupted=true;}}
        if(interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("owner interrupted after worker joined");}
        if(ownerFailure!=null)throw new IllegalStateException("owner stop failed after worker joined",ownerFailure);
        if(error[0]!=null)throw new IllegalStateException("worker render failed",error[0]);
        row.addProperty("worker_thread_id",id[0]);row.addProperty("owner_blocked_wall_ns",System.nanoTime()-start);row.addProperty("worker_first_no_refill_frames",9600);
        row.addProperty("owner_refill_calls_during_worker",0);
        if(closeDuringMix.isEmpty())row.addProperty("owner_AL_calls_during_worker",0);
        else row.addProperty("owner_native_cleanup_during_worker",true);
        row.addProperty("worker_joined",true);return result[0];
    }
    private static byte[] renderWorker(long device,int frames) throws InterruptedException {
        ByteBuffer out=ByteBuffer.allocate(frames*4).order(ByteOrder.LITTLE_ENDIAN);FloatBuffer samples=BufferUtils.createFloatBuffer(STEP);
        for(int frame=0;frame<frames;frame+=STEP) {int count=Math.min(STEP,frames-frame);samples.clear().limit(count);SOFTLoopback.alcRenderSamplesSOFT(device,samples,count);while(samples.hasRemaining())out.putFloat(samples.get());if(frame<9600)Thread.sleep(20);}
        return out.array();
    }
    private static byte[] render(long device,int frames) {try{byte[] bytes=renderWorkerNoPause(device,frames);check(device);return bytes;}catch(Exception error){throw new IllegalStateException(error);}}
    private static byte[] renderQueued(long device,List<Voice> voices,int frames) {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        for(int frame=0;frame<frames;frame+=STEP) {
            bytes.writeBytes(render(device,Math.min(STEP,frames-frame)));
            for(Voice voice:voices)voice.channel().updateStream();
        }
        return bytes.toByteArray();
    }
    private static byte[] renderWorkerNoPause(long device,int frames) {
        ByteBuffer out=ByteBuffer.allocate(frames*4).order(ByteOrder.LITTLE_ENDIAN);FloatBuffer samples=BufferUtils.createFloatBuffer(STEP);
        for(int frame=0;frame<frames;frame+=STEP){int count=Math.min(STEP,frames-frame);samples.clear().limit(count);SOFTLoopback.alcRenderSamplesSOFT(device,samples,count);while(samples.hasRemaining())out.putFloat(samples.get());}return out.array();
    }
    private static void appendRender(JsonObject row,String stage,byte[] bytes) {JsonObject slice=new JsonObject();slice.addProperty("byte_offset",output.size());slice.addProperty("bytes",bytes.length);slice.addProperty("sha256",MainContextOutputCapture.sha(bytes));output.writeBytes(bytes);row.add(stage,slice);}
    private static void identity(JsonObject report,int generation) {var library=(MainOutputLibraryAccessor)library();MainContextOutputCapture.requireCurrent(library.mainOutput$getDevice(),library.mainOutput$getContext());JsonObject row=new JsonObject();row.addProperty("device",library.mainOutput$getDevice());row.addProperty("context",library.mainOutput$getContext());row.addProperty("version",AL10.alGetString(AL10.AL_VERSION));row.addProperty("extension",AL.getCapabilities().AL_SOFT_callback_buffer);row.addProperty("listener_gain",AL10.alGetListenerf(AL10.AL_GAIN));report.add("identity_"+generation,row);check(library.mainOutput$getDevice());}
    private static void check(long device){int al=AL10.alGetError(),alc=ALC10.alcGetError(device);if(al!=0||alc!=0)throw new IllegalStateException("native errors "+al+"/"+alc);}
    private static SoundEngine engine(Minecraft client){return ((SoundManagerEngineAccessor)client.getSoundManager()).fpvdrone$getSoundEngine();}
    private static Library library(){return ((SoundEngineMainOutputAccessor)engine(Minecraft.getInstance())).mainOutput$getLibrary();}
    private static <T>T sound(ClientGameTestContext context,Supplier<T> action){CompletableFuture<T> done=new CompletableFuture<>();context.runOnClient(client->((SoundEngineExecutorAccessor)engine(client)).fpvdrone$getExecutor().schedule(()->{try{done.complete(action.get());}catch(Throwable error){done.completeExceptionally(error);}}));context.waitFor(client->done.isDone(),600);return done.join();}
    private static byte[] read(Path path){try{return Files.readAllBytes(path);}catch(Exception error){throw new IllegalStateException(error);}}
    private static void writePcm(Path root,JsonObject protocol,JsonObject report,String name,byte[] pcm){String path=protocol.get(name+"_pcm_file").getAsString();try{Files.write(root.resolve(path),pcm,StandardOpenOption.CREATE_NEW);}catch(Exception error){throw new IllegalStateException(error);}report.addProperty(name+"_pcm_file",path);report.addProperty(name+"_pcm_bytes",pcm.length);report.addProperty(name+"_pcm_sha256",MainContextOutputCapture.sha(pcm));}
}
