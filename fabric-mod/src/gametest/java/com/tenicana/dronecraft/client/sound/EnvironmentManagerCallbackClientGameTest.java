package com.tenicana.dronecraft.client.sound;

import com.google.gson.*;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer.Layer;
import com.tenicana.dronecraft.client.mixin.*;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

/** Actual selected world entities; observes pending and owner-committed control frames. */
final class EnvironmentManagerCallbackClientGameTest {
    private static final int WAIT=600, ENTITY=172001, WARM=48000, WORKER=9600, COMMITTED=24000, STEP=960;
    static final int MAX_CAPTURE_FRAMES=200_000, MAX_CAPTURE_CALLS=4096;
    private static final ByteArrayOutputStream input=new ByteArrayOutputStream(),output=new ByteArrayOutputStream(),oracle=new ByteArrayOutputStream();
    private static final List<ProbeDrone> probes=new ArrayList<>();
    private static final JsonArray readiness=new JsonArray();
    private static WetOracleTrace.Epoch wetEpoch;
    private record Voice(int entityId, Layer layer, SoundInstance sound, Channel channel, AudioStream stream,
                         CallbackStreamCapture.Capture capture, NativeCallbackBinding binding) { }
    private static final class ProbeDrone extends DroneEntity {
        final AcousticVector near;
        volatile float power=.5f;
        ProbeDrone(ClientLevel level,int id,AcousticVector near){super(DroneEntityTypes.DRONE,level);setId(id);this.near=near;}
        @Override public float getAverageMotorRpm(){return 12000;}
        @Override public float getMotorRpm(int index){return 12000;}
        @Override public float getMotorPower(){return power;}
        @Override public float getMotorPower(int index){return power;}
        @Override public void tick(){tickCount++;}
        void move(AcousticVector listener,double scale){var p=listener.add(near.subtract(listener).multiply(scale));setPos(p.x(),p.y()-getBbHeight()*.5,p.z());}
    }
    private static final class Stall {
        volatile boolean ownerStarted,publicationReady,abort;
        volatile int completedFrames;
        volatile long workerId;
        Throwable workerError;
        byte[] rendered;
        JsonArray before,after;
        DroneAudioControlFrame frameBefore,frameAfter;
        long ownerNanos,workerWaitNanos,workerRenderNanos;
    }

    static boolean requested(){return EnvironmentWetStimulus.requested() && MainContextOutputCapture.requested() && MainContextOutputCapture.protocol().has("wet_history_oracle_experiment") && MainContextOutputCapture.protocol().get("wet_history_oracle_experiment").getAsBoolean() && MainContextOutputCapture.protocol().has("owner_committed_frame_experiment") && MainContextOutputCapture.protocol().get("owner_committed_frame_experiment").getAsBoolean() && MainContextOutputCapture.protocol().has("actual_manager_callback_experiment")
            && MainContextOutputCapture.protocol().get("actual_manager_callback_experiment").getAsBoolean();}

    static void run(ClientGameTestContext context) {
        JsonObject protocol=MainContextOutputCapture.protocol(),report=new JsonObject();JsonArray rows=new JsonArray();Throwable failure=null;
        Path root=Path.of(protocol.get("workspace_root").getAsString());EnumMap<SoundSource,Double> options=new EnumMap<>(SoundSource.class);
        String oldCallback=System.getProperty(NativeCallbackBinding.ENABLE_PROPERTY),oldProcedural=System.getProperty("fpvdrone.proceduralAudio");boolean backendClaimed=false;
        report.addProperty("started_utc",Instant.now().toString());report.addProperty("protocol_sha256",MainContextOutputCapture.sha(read(Path.of(System.getProperty(MainContextOutputCapture.PROPERTY)))));
        report.add("evidence_sha256",protocol.get("evidence_sha256").deepCopy());
        try {
            checkProtocol(protocol);EnvironmentWetStimulus.validateDeclaration(protocol);EnvironmentWetStimulus.setPhase(EnvironmentWetStimulus.Phase.A);
            report.add("environment_stimulus",protocol.get("generated_wet_environment").deepCopy());report.add("dynamic_wet_environments",protocol.get("dynamic_wet_environments").deepCopy());report.addProperty("test_only_generated_wet_environment",true);
            for(String name:List.of("input_pcm_file","output_pcm_file","oracle_pcm_file","output_report"))if(Files.exists(root.resolve(protocol.get(name).getAsString())))throw new IllegalStateException("existing manager callback output");
            JsonObject loaded=new JsonObject();for(var item:protocol.getAsJsonArray("required_loaded_class_paths")) {
                String path=item.getAsString(),resource="/"+path.substring(path.indexOf("com/tenicana/"));try(var stream=EnvironmentManagerCallbackClientGameTest.class.getResourceAsStream(resource)) {
                    if(stream==null)throw new IllegalStateException("missing loaded class "+resource);loaded.addProperty(path,MainContextOutputCapture.sha(stream.readAllBytes()));
                }
            }report.add("loaded_class_sha256",loaded);
            for(var method:ListenerReverbHistoryProducer.class.getDeclaredMethods())if(method.getName().contains("fpvdrone$begin")||method.getName().contains("fpvdrone$finish")||method.getName().contains("fpvdrone$layer"))throw new IllegalStateException("legacy allocating wet mixer probes present");
            report.addProperty("legacy_wet_mixer_probe_methods_absent",true);
            backendClaimed=AcousticBackendSelector.activate(AcousticBackendSelector.Mode.OPENAL_EFX);if(!backendClaimed)throw new IllegalStateException("diagnostic backend already owned");
            System.setProperty(NativeCallbackBinding.ENABLE_PROPERTY,"true");System.setProperty("fpvdrone.proceduralAudio","true");
            context.runOnClient(client->{for(SoundSource source:SoundSource.values()){options.put(source,client.options.getSoundSourceOptionInstance(source).get());client.options.getSoundSourceOptionInstance(source).set(source==SoundSource.MASTER||source==SoundSource.NEUTRAL?1.0:0.0);}client.getSoundManager().updateCategoryVolume(SoundSource.NEUTRAL,1);});
            try(TestSingleplayerContext world=context.worldBuilder().create()) {
                world.getClientWorld().waitForChunksDownload();world.getServer().runCommand("gamerule spawn_mobs false");drain(context,"world_ready");
                for(int epoch=0;epoch<2;epoch++) {
                    if(epoch==1){AcousticBackendSelector.restoreDefault();if(!AcousticBackendSelector.activate(AcousticBackendSelector.Mode.OPENAL_EFX))throw new IllegalStateException("EFX prelude mode unavailable");context.runOnClient(client->client.getSoundManager().reload());}
                    CallbackStreamCapture.clearRegistrations();ManagerWetPublicationCapture.reset();AcousticVector listener=awaitListener(context,epoch);
                    final int traceEpoch=epoch;context.runOnClient(client->{EnvironmentWetStimulus.setPhase(EnvironmentWetStimulus.Phase.A);if(!activeSets().isEmpty()||!handles(client).isEmpty())throw new IllegalStateException("nonempty initial fixture state");wetState().reset();wetEpoch=WetOracleTrace.beginEpoch(wetState(),traceEpoch,600,MAX_CAPTURE_CALLS,MAX_CAPTURE_FRAMES);WetSoundAdmissionCapture.live();for(int i=0;i<6;i++){var near=listener.add(new AcousticVector((i-2.5)*2,0,-8));var drone=new ProbeDrone(client.level,ENTITY+i,near);drone.move(listener,1);client.level.addEntity(drone);probes.add(drone);}});
                    context.waitFor(client->activeSets().size()==6&&handles(client).size()==12&&wetState().snapshot().sources().size()==6,WAIT);
                    for(int tick=0;tick<protocol.get("shadow_prelude_client_ticks").getAsInt();tick++)context.waitTick();
                    if(wetState().historyDiagnostics().historyFrames()!=24000)throw new IllegalStateException("nonzero complete shadow prefix required");
                    AcousticBackendSelector.restoreDefault();if(!AcousticBackendSelector.activate(AcousticBackendSelector.Mode.JAVA_FDN))throw new IllegalStateException("normal Java wet handoff mode unavailable");
                    context.waitFor(client->ready(client,listener,1),WAIT);for(int tick=0;tick<protocol.get("settle_client_ticks").getAsInt();tick++)context.waitTick();sound(context,()->null);
                    List<Voice> voices=new ArrayList<>();JsonObject row=new JsonObject();row.addProperty("generation",epoch);row.add("listener_anchor",vector(listener));rows.add(row);
                    context.runOnClient(client->{voices.addAll(voices(client));row.add("membership",membership(client));row.add("initial_publication",publication(client));});
                    if(voices.size()!=13)throw new IllegalStateException("actual six manager requires 13 voices");
                    sound(context,()->{identity(row);row.add("initial_native",nativeVoices(voices));row.add("initial_committed_frame",framePublication(requireCommitted()));row.addProperty("streaming_pool",library().getDebugString());for(Voice voice:voices){if(voice.capture().calls!=0||voice.capture().queuedReads!=0)throw new IllegalStateException("capture registration did not precede rendering");}appendOutput(row,"warm",render(device(),WARM));return null;});
                    JsonArray stages=new JsonArray();stages.add(stall(context,voices,listener,2,"FAR"));stages.add(stall(context,voices,listener,1,"NEAR"));row.add("stalls",stages);row.add("return_environment",returnEnvironment(context,voices,listener));
                    seal(row,voices);WetOracleTrace.finishEpoch(wetEpoch);sealWetHistory(row,voices,root,protocol);wetEpoch=null;drain(context,"epoch_"+epoch);row.add("cleanup",sound(context,()->closedVoices(voices)));context.runOnClient(client->{row.addProperty("handles_after_cleanup",handles(client).size());row.addProperty("manager_sets_after_cleanup",activeSets().size());});
                    row.addProperty("remaining_pool",sound(context,()->library().getDebugString()));CallbackStreamCapture.clearRegistrations();
                }
            }
            context.runOnClient(client->client.getSoundManager().destroy());report.addProperty("status","actual_manager_callback_captured");
        } catch(Throwable error) {
            failure=error;report.addProperty("status","actual_manager_callback_failed");report.addProperty("failure",error.toString());
            try{drain(context,"failure");context.runOnClient(client->client.getSoundManager().destroy());}catch(Throwable cleanup){report.addProperty("cleanup_failure",cleanup.toString());}
        } finally {
            if(wetEpoch!=null&&!wetEpoch.finished())WetOracleTrace.finishEpoch(wetEpoch);if(backendClaimed)AcousticBackendSelector.restoreDefault();EnvironmentWetStimulus.setPhase(EnvironmentWetStimulus.Phase.A);restore(NativeCallbackBinding.ENABLE_PROPERTY,oldCallback);restore("fpvdrone.proceduralAudio",oldProcedural);CallbackStreamCapture.clearRegistrations();
            context.runOnClient(client->options.forEach((source,value)->client.options.getSoundSourceOptionInstance(source).set(value)));
        }
        report.add("rows",rows);report.add("listener_readiness",readiness);report.add("epochs",MainContextOutputCapture.epochs());report.add("channel_allocations",WetChannelLifecycleCapture.allocations());report.add("channel_calls",WetChannelLifecycleCapture.calls());report.add("sound_admission",WetSoundAdmissionCapture.snapshot());report.add("runtime_events",DistanceOwnershipRuntimeTrace.snapshot());
        JsonObject scope=new JsonObject();for(String key:List.of("actual_Minecraft_client","actual_main_Library_software_loopback","actual_client_world_entities","actual_production_manager_selection","managed_Channel_callback","production_dry_wet_DSP","generated_controls_only","actual_consumed_control_references"))scope.addProperty(key,failure==null);
        for(String key:List.of("actual_consumed_shared_frame_references","long_owner_stall_control_coherence_verified"))scope.addProperty(key,failure==null);
        for(String key:List.of("control_synchronization_fixed","exact_tick_response_latency_qualified","ordinary_wall_clock_deadlines_qualified","JVM_GC_realtime_qualified","physical_endpoint_verified","actual_physical_environment_verified","full_wet_FDN_oracle_reconstructed","physical_error_recomputed","release_profile_eligible","full_goal_complete"))scope.addProperty(key,false);report.add("scope",scope);
        scope.addProperty("hardware_mixer_block_atomicity_verified",false);
        writePcm(root,protocol,report,"input",input.toByteArray());writePcm(root,protocol,report,"output",output.toByteArray());writePcm(root,protocol,report,"oracle",oracle.toByteArray());report.addProperty("finished_utc",Instant.now().toString());
        write(root.resolve(protocol.get("output_report").getAsString()),new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report)+"\n");if(failure!=null)throw new IllegalStateException("manager callback failed",failure);
    }

    private static JsonObject stall(ClientGameTestContext context,List<Voice> voices,AcousticVector listener,double scale,String direction) {
        Stall stall=new Stall();JsonObject row=new JsonObject();row.addProperty("direction",direction);row.addProperty("requested_radial_scale",scale);row.addProperty("total_worker_frames",WORKER);
        context.runOnClient(client->{row.addProperty("game_time_before",client.level.getGameTime());row.add("controls_before",publication(client));});
        CompletableFuture<Void> done=schedule(context,()->{runStallOwner(stall,voices);return null;});Throwable controlFailure=null;
        try {
            context.waitFor(client->stall.ownerStarted||done.isDone(),WAIT);if(done.isDone())done.join();
            context.runOnClient(client->{EnvironmentWetStimulus.setPhase(direction.equals("FAR")?EnvironmentWetStimulus.Phase.B:EnvironmentWetStimulus.Phase.C);probes.forEach(drone->{drone.power=direction.equals("FAR")?.75f:.4f;drone.move(listener,scale);});double category=direction.equals("FAR")?.55:.8;client.options.getSoundSourceOptionInstance(SoundSource.NEUTRAL).set(category);client.getSoundManager().updateCategoryVolume(SoundSource.NEUTRAL,(float)category);row.addProperty("requested_power",direction.equals("FAR")?.75:.4);row.addProperty("requested_category_volume",category);});context.waitFor(client->ready(client,listener,scale),WAIT);
            context.runOnClient(client->{row.addProperty("game_time_published",client.level.getGameTime());row.add("controls_published",publication(client));row.add("membership_published",membership(client));});
            row.addProperty("publication_observed_at_completed_worker_frames",stall.completedFrames);stall.publicationReady=true;
            context.waitFor(client->done.isDone(),WAIT);
        } catch(Throwable error){controlFailure=error;stall.abort=true;stall.publicationReady=true;}
        finally {if(!done.isDone()){stall.abort=true;stall.publicationReady=true;}try{done.join();}catch(Throwable error){if(controlFailure==null)controlFailure=error;}}
        if(controlFailure!=null)throw new IllegalStateException("client control failed after worker joined",controlFailure);
        row.add("native_before_stall",stall.before);row.add("native_after_worker_before_owner_return",stall.after);row.addProperty("worker_thread_id",stall.workerId);row.addProperty("worker_completed_frames",stall.completedFrames);row.addProperty("owner_task_wall_ns",stall.ownerNanos);row.addProperty("worker_publication_wait_wall_ns",stall.workerWaitNanos);row.addProperty("worker_render_wall_ns",stall.workerRenderNanos);row.addProperty("planned_worker_pause_ms",200);row.addProperty("owner_refill_calls_during_worker",0);row.addProperty("owner_AL_calls_during_worker",0);row.addProperty("worker_joined",true);appendOutput(row,"worker_output",stall.rendered);
        row.add("committed_before_stall",framePublication(stall.frameBefore));row.add("committed_after_worker_before_owner_return",framePublication(stall.frameAfter));
        // The subsequent FIFO barrier is after all channel changes enqueued by real client ticks.
        sound(context,()->null);sound(context,()->{row.add("native_after_queue_barrier",nativeVoices(voices));row.add("committed_after_queue_barrier",framePublication(requireCommitted()));appendOutput(row,"committed_output",render(device(),COMMITTED));row.add("native_after_committed_output",nativeVoices(voices));row.add("committed_after_committed_output",framePublication(requireCommitted()));return null;});
        context.runOnClient(client->{row.addProperty("game_time_after",client.level.getGameTime());row.add("controls_after",publication(client));});return row;
    }

    /** Environment-only return: source ownership and phase/history remain continuous. */
    private static JsonObject returnEnvironment(ClientGameTestContext context,List<Voice> voices,AcousticVector listener) {
        JsonObject row=new JsonObject();row.addProperty("phase","A");row.addProperty("frames",COMMITTED);
        sound(context,()->{row.add("native_before",nativeVoices(voices));row.add("committed_before",framePublication(requireCommitted()));return null;});
        context.runOnClient(client->EnvironmentWetStimulus.setPhase(EnvironmentWetStimulus.Phase.A));
        context.waitFor(client->ready(client,listener,1),WAIT);
        context.runOnClient(client->row.add("controls_published",publication(client)));
        // Await a real client-prepared frame with A, then a complete owner transaction.
        sound(context,()->null);
        sound(context,()->{
            var committed=requireCommitted();
            if(!committed.wetSnapshot().environment().equals(EnvironmentWetStimulus.selected()))throw new IllegalStateException("return environment was not committed");
            row.add("committed_after",framePublication(committed));row.add("native_after",nativeVoices(voices));
            appendOutput(row,"output",render(device(),COMMITTED));return null;
        });
        return row;
    }

    private static void runStallOwner(Stall stall,List<Voice> voices) {
        long started=System.nanoTime();long nativeDevice=device();MainContextOutputCapture.requireCurrent(nativeDevice,context());stall.before=nativeVoices(voices);stall.frameBefore=requireCommitted();var caps=ALC.getCapabilities();
        Thread worker=new Thread(()->{
            stall.workerId=Thread.currentThread().threadId();
            try {ALC.setCapabilities(caps);long waiting=System.nanoTime(),deadline=waiting+TimeUnit.SECONDS.toNanos(30);while(!stall.publicationReady&&!stall.abort){if(System.nanoTime()>deadline)throw new IllegalStateException("client publication timeout");LockSupport.parkNanos(1_000_000);}stall.workerWaitNanos=System.nanoTime()-waiting;
                if(stall.abort)throw new IllegalStateException("client aborted worker before render");
                ByteBuffer bytes=ByteBuffer.allocate(WORKER*4).order(ByteOrder.LITTLE_ENDIAN);FloatBuffer samples=BufferUtils.createFloatBuffer(STEP);
                long rendering=System.nanoTime();for(int frame=0;frame<WORKER;frame+=STEP){if(stall.abort)throw new IllegalStateException("client aborted worker");samples.clear().limit(STEP);SOFTLoopback.alcRenderSamplesSOFT(nativeDevice,samples,STEP);while(samples.hasRemaining())putFinite(bytes,samples.get());stall.completedFrames=frame+STEP;Thread.sleep(20);}stall.workerRenderNanos=System.nanoTime()-rendering;stall.rendered=bytes.array();
            } catch(Throwable error){stall.workerError=error;}finally{try{ALC.setCapabilities(null);}catch(Throwable error){if(stall.workerError==null)stall.workerError=error;}}
        },"D175-sole-loopback-renderer");worker.start();stall.ownerStarted=true;boolean interrupted=false;
        for(;;){try{worker.join();break;}catch(InterruptedException error){interrupted=true;stall.abort=true;stall.publicationReady=true;}}
        // Native source properties are read before this task permits queued setters to execute.
        stall.after=nativeVoices(voices);stall.frameAfter=requireCommitted();check(nativeDevice);stall.ownerNanos=System.nanoTime()-started;
        if(interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("sound owner interrupted after worker joined");}if(stall.workerError!=null)throw new IllegalStateException("worker failed after join",stall.workerError);
    }

    private static void seal(JsonObject row,List<Voice> voices) {
        JsonArray captures=new JsonArray(),frames=new JsonArray();row.add("captures",captures);row.add("control_frames",frames);String rejected=null;
        IdentityHashMap<DroneAudioControlFrame,Integer> frameIndexes=new IdentityHashMap<>();
        // Export every voice before rejecting a capture, so failed runs retain
        // the actual PCM, trace and native-stage observations without an oracle claim.
        for(Voice voice:voices) {
            var capture=voice.capture();
            byte[] pcm=capture.pcmBytes();JsonObject data=capture.snapshot();data.addProperty("entity_id",voice.entityId());data.addProperty("layer",voice.layer()==null?"WET":voice.layer().name());data.addProperty("stream_identity",System.identityHashCode(voice.stream()));data.addProperty("sound_identity",System.identityHashCode(voice.sound()));data.addProperty("source",voice.binding().source());data.addProperty("channel_allocation_identity",WetChannelLifecycleCapture.identity(voice.channel()));
            data.addProperty("input_record_identity",MainContextOutputCapture.identity(voice.stream()));data.addProperty("input_record_kind","callback_admission");data.addProperty("active",capture.active);data.addProperty("native_callback_calls",voice.binding().calls());data.addProperty("native_callback_fault",voice.binding().fault());data.addProperty("native_binding_failure",voice.binding().failure());captures.add(data);
            data.addProperty("initial_registration_calls",0);data.addProperty("input_byte_offset",input.size());data.addProperty("input_bytes",pcm.length);data.addProperty("input_sha256",MainContextOutputCapture.sha(pcm));input.writeBytes(pcm);
            IdentityHashMap<Object,Integer> indexes=new IdentityHashMap<>();JsonArray controls=new JsonArray(),trace=data.getAsJsonArray("trace");int missingControls=0;
            for(int call=0;call<Math.min(capture.calls,capture.maxCalls);call++) {
                DroneAudioControlFrame frame=(DroneAudioControlFrame)capture.controlFrames[call];if(frame==null){missingControls++;continue;}Integer frameIndex=frameIndexes.get(frame);
                if(frameIndex==null){frameIndex=frames.size();frameIndexes.put(frame,frameIndex);JsonObject encodedFrame=framePublication(frame);encodedFrame.addProperty("control_frame_reference_id",frameIndex);frames.add(encodedFrame);}
                trace.get(call).getAsJsonObject().addProperty("control_frame_reference_id",frameIndex);
                Object control=capture.controlSnapshots[call];if(control==null){missingControls++;continue;}Integer index=indexes.get(control);
                Object expected=frame.wetSnapshot();if(voice.layer()!=null){expected=null;for(var source:frame.sources())if(source.entityId()==voice.entityId()){expected=source.audioSnapshot();break;}}if(control!=expected)throw new IllegalStateException("actual callback snapshot differs from actual shared frame");
                if(index==null){index=controls.size();indexes.put(control,index);JsonObject encoded=CallbackControlSnapshotTrace.serialize(control);encoded.addProperty("control_reference_id",index);controls.add(encoded);}
                trace.get(call).getAsJsonObject().addProperty("control_reference_id",index);
            }data.add("consumed_controls",controls);
            data.addProperty("missing_control_references",missingControls);
            if(capture.fault!=0||capture.calls==0||capture.queuedReads!=0||capture.active||missingControls!=0){String reason="entity="+voice.entityId()+" layer="+(voice.layer()==null?"WET":voice.layer())+" fault="+capture.fault+" calls="+capture.calls+" queued="+capture.queuedReads+" active="+capture.active+" frames="+capture.cursor+" missing_controls="+missingControls+" native_calls="+voice.binding().calls()+" native_fault="+voice.binding().fault();data.addProperty("capture_rejection",reason);if(rejected==null)rejected=reason;}
        }
        if(rejected!=null)throw new IllegalStateException("callback capture incomplete: "+rejected);
        for(int index=0;index<voices.size();index++) {
            Voice voice=voices.get(index);var capture=voice.capture();JsonObject data=captures.get(index).getAsJsonObject();byte[] pcm=capture.pcmBytes();
            if(voice.layer()!=null) {
                int seed=31*voice.entityId()+voice.layer().ordinal();data.addProperty("seed",seed);byte[] reference=dryOracle(capture,voice.layer(),seed);data.addProperty("oracle_byte_offset",oracle.size());data.addProperty("oracle_bytes",reference.length);data.addProperty("oracle_sha256",MainContextOutputCapture.sha(reference));oracle.writeBytes(reference);
                int maximum=0;ByteBuffer a=ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN),b=ByteBuffer.wrap(reference).order(ByteOrder.LITTLE_ENDIAN);while(a.hasRemaining())maximum=Math.max(maximum,Math.abs((int)a.getShort()-b.getShort()));data.addProperty("maximum_S16_difference",maximum);if(maximum>1)throw new IllegalStateException("queued dry oracle differs by "+maximum);
            } else {data.addProperty("full_FDN_oracle_reconstructed",false);boolean nonzero=false;for(short sample:capture.pcm)if(sample!=0){nonzero=true;break;}data.addProperty("nonzero_PCM",nonzero);if(!nonzero)throw new IllegalStateException("actual wet PCM remained silent");}
        }
    }

    private static void sealWetHistory(JsonObject row,List<Voice> voices,Path root,JsonObject protocol) {
        Voice voice=voices.stream().filter(v->v.layer()==null).findFirst().orElseThrow();
        var cap=voice.capture();var t=wetEpoch;JsonObject data=new JsonObject();row.add("wet_history_oracle",data);
        data.addProperty("fault",t.fault());data.addProperty("reset_count",t.resetCount());data.addProperty("finished",t.finished());
        data.addProperty("producer_identity",System.identityHashCode(t.producerIdentity()));
        data.addProperty("session_identity",System.identityHashCode(t.sessionIdentity()));
        data.addProperty("stream_identity",System.identityHashCode(t.streamIdentity()));
        data.addProperty("shadow_calls",t.shadowCalls());data.addProperty("shadow_frames",t.shadowFrames());
        data.addProperty("shadow_calls_at_acquisition",t.shadowCallsAtAcquisition());
        data.addProperty("shadow_frames_at_acquisition",t.shadowFramesAtAcquisition());
        data.addProperty("preroll_frames",t.prerollFrames());data.addProperty("preroll_nanos",t.prerollNanos());
        data.addProperty("wet_calls_at_attach",t.wetCallsAtAttach());data.addProperty("wet_calls",t.wetCalls());data.addProperty("wet_frames",t.wetFrames());
        data.add("acquisition_snapshot",CallbackControlSnapshotTrace.serialize(t.acquisitionSnapshot()));
        data.addProperty("initializer_transition_seconds",0);data.addProperty("initial_FDN_state_zero",true);
        JsonArray shadow=new JsonArray(),controls=new JsonArray();data.add("shadow_trace",shadow);data.add("shadow_controls",controls);
        IdentityHashMap<Object,Integer> refs=new IdentityHashMap<>();
        for(int call=0;call<t.shadowCalls();call++) {
            Object plan=t.shadowPlan(call);Integer id=refs.get(plan);
            if(id==null){id=controls.size();refs.put(plan,id);JsonObject encoded=new JsonObject();encoded.addProperty("control_reference_id",id);encoded.addProperty("plan_identity",System.identityHashCode(plan));encoded.add("sources",CallbackControlSnapshotTrace.serialize(new ListenerReverbState.Snapshot(t.shadowSources(call),t.acquisitionSnapshot().environment())).get("sources"));controls.add(encoded);}
            JsonObject item=new JsonObject();item.addProperty("control_reference_id",id);item.addProperty("frames",t.shadowFrames(call));item.addProperty("accepted",t.shadowAccepted(call));item.addProperty("input_cursor",t.shadowCursor(call));shadow.add(item);
        }
        JsonArray wet=new JsonArray();data.add("wet_trace",wet);
        for(int call=0;call<t.wetCalls();call++){
            if(t.wetSnapshot(call)!=cap.controlSnapshots[call])throw new IllegalStateException("wet mixer/callback snapshot identity differs");
            JsonObject item=new JsonObject();item.addProperty("control_reference_id",row.getAsJsonArray("captures").get(12).getAsJsonObject().getAsJsonArray("trace").get(call).getAsJsonObject().get("control_reference_id").getAsInt());
            item.addProperty("frames",t.wetFrames(call));item.addProperty("accepted",t.wetAccepted(call));item.addProperty("input_cursor",t.wetCursor(call));wet.add(item);
        }
        String prefix=protocol.get("wet_oracle_pcm_prefix").getAsString()+"-epoch"+t.epoch();
        data.add("actual_shadow",wetPcm(root,prefix+"-actual-shadow-f32.pcm",t.shadowMixedCopy()));
        data.add("actual_preroll",wetPcm(root,prefix+"-actual-preroll-f32.pcm",t.prerollCopy()));
        data.add("actual_wet_mixed",wetPcm(root,prefix+"-actual-wet-mixed-f32.pcm",t.wetMixedCopy()));
        t.requireComplete(voice.stream(),cap.calls,Math.toIntExact(cap.cursor));
        var reference=WetQueuedMixReference.replay(t);
        data.add("expected_shadow",wetPcm(root,prefix+"-expected-shadow-f32.pcm",reference.expectedShadowMixed()));
        data.add("expected_preroll",wetPcm(root,prefix+"-expected-preroll-f32.pcm",reference.expectedPreroll()));
        data.add("expected_wet_mixed",wetPcm(root,prefix+"-expected-wet-mixed-f32.pcm",reference.expectedWetMixed()));
        data.add("shadow_comparison",mixComparison(reference.shadow()));data.add("preroll_comparison",mixComparison(reference.preroll()));data.add("wet_mix_comparison",mixComparison(reference.wet()));
        data.addProperty("shares_production_phase_kernel",reference.sharesProductionPhaseKernel());
        data.addProperty("reference_calls_production_FDN",reference.callsProductionFdn());
        data.addProperty("input_capture_complete",true);
    }
    private static JsonObject wetPcm(Path root,String path,float[] samples) {
        ByteBuffer bytes=ByteBuffer.allocate(samples.length*4).order(ByteOrder.LITTLE_ENDIAN);for(float sample:samples){if(!Float.isFinite(sample))throw new IllegalStateException("nonfinite wet oracle input");bytes.putFloat(sample);}
        try{Files.write(root.resolve(path),bytes.array(),StandardOpenOption.CREATE_NEW);}catch(Exception error){throw new IllegalStateException(error);}
        JsonObject record=new JsonObject();record.addProperty("path",path);record.addProperty("frames",samples.length);record.addProperty("bytes",bytes.capacity());record.addProperty("sha256",MainContextOutputCapture.sha(bytes.array()));return record;
    }
    private static JsonObject mixComparison(WetQueuedMixReference.Comparison value) {
        JsonObject r=new JsonObject();r.addProperty("frames",value.frames());r.addProperty("maximum_float_difference",value.maximumFloatDifference());r.addProperty("maximum_S16_difference",value.maximumS16Difference());r.addProperty("maximum_gain_difference_db",value.maximumGainDifferenceDb());return r;
    }

    private static byte[] dryOracle(CallbackStreamCapture.Capture capture,Layer layer,int seed) {
        DroneAcousticRenderState state=new DroneAcousticRenderState();ProceduralDroneAudioStream reference=new ProceduralDroneAudioStream(state,layer,seed);ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try {for(int call=0;call<capture.calls;call++){var snapshot=(DroneAcousticRenderState.AudioSnapshot)capture.controlSnapshots[call];set(state,"audioSnapshot",snapshot);ByteBuffer chunk=reference.read(capture.requestedBytes[call]);if(chunk.remaining()!=capture.returnedBytes[call])throw new IllegalStateException("dry oracle request contract");byte[] copied=new byte[chunk.remaining()];chunk.get(copied);bytes.writeBytes(copied);}return bytes.toByteArray();}finally{reference.close();}
    }

    private static boolean ready(Minecraft client,AcousticVector listener,double scale) {
        if(client.level==null||activeSets().size()!=6)return false;var frame=DroneSoundManager.callbackPendingFrame();if(frame==null||frame.sources().size()!=6||frame.wetSnapshot().sources().size()!=6||handles(client).size()!=13||!frame.wetSnapshot().environment().equals(EnvironmentWetStimulus.selected()))return false;
        var sample=cameraListener().latestFor(client.level);if(sample==null||sample.frame().positionMeters().subtract(listener).length()>1e-4)return false;
        var published=framePublication(frame).getAsJsonObject("source_publication").getAsJsonArray("sources");
        for(ProbeDrone drone:probes){Object set=activeSets().get(drone.getId());if(set==null||field(set,"drone")!=drone)return false;var state=(DroneAcousticRenderState)field(set,"acousticState");var snapshot=state.audioSnapshot();if(snapshot==null||!state.distanceStreamsVerified())return false;var expected=listener.add(drone.near.subtract(listener).multiply(scale));if(snapshot.source().positionMeters().subtract(expected).length()>1e-5)return false;
            boolean wetAtExpectedPosition=false;
            for(var source:published){var item=source.getAsJsonObject();if(item.get("entity_id").getAsInt()==drone.getId()){var p=item.getAsJsonArray("source_position");var actual=new AcousticVector(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());wetAtExpectedPosition=actual.subtract(expected).length()<=1e-5;break;}}
            if(!wetAtExpectedPosition)return false;
        }return true;
    }
    private static JsonObject publication(Minecraft client) {
        var frame=DroneSoundManager.callbackPendingFrame();if(frame==null)throw new IllegalStateException("manager pending frame absent");JsonObject result=framePublication(frame);var sample=cameraListener().latestFor(client.level);result.addProperty("game_time",client.level.getGameTime());result.addProperty("camera_epoch",sample.epoch());return result;
    }

    private static DroneAudioControlFrame requireCommitted() {
        var frame=DroneSoundManager.callbackCommittedFrame();
        if(frame==null||frame.sources().size()!=6||frame.wetSnapshot().sources().size()!=6)
            throw new IllegalStateException("complete owner-committed frame absent");
        return frame;
    }

    /** Serializes the frozen input and actual prepared wet output of the same frame. */
    private static JsonObject framePublication(DroneAudioControlFrame frame) {
        if(frame==null)throw new IllegalStateException("shared frame absent");
        JsonObject result=new JsonObject(),publication=new JsonObject();JsonArray dry=new JsonArray(),sources=new JsonArray();
        result.addProperty("control_generation",frame.generation());result.addProperty("frame_version",frame.version());
        result.addProperty("frame_identity",System.identityHashCode(frame));
        result.add("listener_position",vector(frame.listener().positionMeters()));result.add("listener_velocity",vector(frame.listener().velocityMetersPerSecond()));
        var nativeTransform=frame.listenerTransform();var nativePosition=nativeTransform.position();
        result.add("native_listener_position",vector(new AcousticVector(nativePosition.x,nativePosition.y,nativePosition.z)));
        var forward=nativeTransform.forward();result.add("native_listener_forward",vector(new AcousticVector(forward.x,forward.y,forward.z)));
        var up=nativeTransform.up();result.add("native_listener_up",vector(new AcousticVector(up.x,up.y,up.z)));
        result.addProperty("wet_native_gain",frame.wetNativeGain());result.addProperty("wet_native_pitch",frame.wetNativePitch());
        result.addProperty("wet_stream_identity",System.identityHashCode(frame.wetStream()));
        result.add("wet",CallbackControlSnapshotTrace.serialize(frame.wetSnapshot()));
        publication.addProperty("publication_id",frame.version());publication.add("listener_position",vector(frame.listener().positionMeters()));
        for(var source:frame.sources()) {
            var snapshot=source.audioSnapshot();dry.add(CallbackControlSnapshotTrace.serialize(snapshot));
            ListenerReverbState.Source wet=null;for(var candidate:frame.wetSnapshot().sources())if(candidate.entityId()==source.entityId()){if(wet!=null)throw new IllegalStateException("duplicate frozen wet source");wet=candidate;}
            if(wet==null)throw new IllegalStateException("frozen wet source missing");
            double distance=snapshot.source().positionMeters().subtract(frame.listener().positionMeters()).length();
            JsonObject item=new JsonObject();item.addProperty("entity_id",source.entityId());item.addProperty("E_motor",source.motorEffectiveEnvelope());item.addProperty("E_prop",source.propellerEffectiveEnvelope());
            item.addProperty("raw_E_motor",source.motorEnvelope());item.addProperty("raw_E_prop",source.propellerEnvelope());
            item.addProperty("C_motor",snapshot.calibration().motorPlaybackAmplitude());item.addProperty("C_prop",snapshot.calibration().propellerPlaybackAmplitude());
            item.addProperty("native_motor_gain",source.motorNativeGain());item.addProperty("native_propeller_gain",source.propellerNativeGain());
            item.addProperty("native_motor_pitch",source.motorNativePitch());item.addProperty("native_propeller_pitch",source.propellerNativePitch());
            item.addProperty("motor_stream_identity",System.identityHashCode(source.motorStream()));item.addProperty("propeller_stream_identity",System.identityHashCode(source.propellerStream()));
            item.addProperty("distance_m",distance);item.addProperty("pressure_distance_gain",1/Math.max(1,distance));
            item.addProperty("actual_motor_amplitude",wet.motorAmplitude());item.addProperty("actual_propeller_amplitude",wet.propellerAmplitude());
            item.add("source_position",vector(snapshot.source().positionMeters()));item.add("listener_position",vector(frame.listener().positionMeters()));
            item.addProperty("emission_identity_match",wet.emission()==source.unoccludedEmission());
            if(!item.get("emission_identity_match").getAsBoolean())throw new IllegalStateException("wet emission not frozen frame input");
            sources.add(item);
        }
        publication.add("sources",sources);result.add("source_publication",publication);result.add("dry",dry);
        return result;
    }
    private static List<Voice> voices(Minecraft client) {
        List<Voice> result=new ArrayList<>();activeSets().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{for(String name:List.of("motor","propeller")){SoundInstance sound=(SoundInstance)field(entry.getValue(),name);result.add(voice(client,entry.getKey(),name.equals("motor")?Layer.MOTOR:Layer.PROPELLER,sound));}});result.add(voice(client,-1,null,(SoundInstance)field(controller(),"sound")));return result;
    }
    private static Voice voice(Minecraft client,int id,Layer layer,SoundInstance sound) {
        var handle=handles(client).get(sound);if(handle==null)throw new IllegalStateException("actual manager handle absent");Channel channel=((ChannelHandleChannelAccessor)handle).fpvdrone$getChannel();if(channel==null)throw new IllegalStateException("actual manager handle retired");AudioStream stream=((MainOutputChannelAccessor)channel).mainOutput$getStream();var capture=CallbackStreamCapture.find(stream);var binding=((CallbackChannelAccess)channel).callbackBinding();if(capture==null||binding==null||!binding.attached())throw new IllegalStateException("actual manager callback not admitted");return new Voice(id,layer,sound,channel,stream,capture,binding);
    }
    private static JsonObject membership(Minecraft client) {
        JsonObject row=new JsonObject();JsonArray world=new JsonArray(),selected=new JsonArray();for(var entity:client.level.entitiesForRendering())if(entity instanceof DroneEntity)world.add(entity.getId());
        activeSets().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{JsonObject value=new JsonObject();value.addProperty("entity_id",entry.getKey());value.addProperty("world_entity_identity",System.identityHashCode(client.level.getEntity(entry.getKey())));value.addProperty("manager_entity_identity",System.identityHashCode(field(entry.getValue(),"drone")));for(String layer:List.of("motor","propeller")){Object sound=field(entry.getValue(),layer);value.addProperty(layer+"_identity",System.identityHashCode(sound));value.addProperty(layer+"_has_actual_handle",handles(client).containsKey(sound));}selected.add(value);});row.add("world_drone_ids",world);row.add("selected_sets",selected);row.addProperty("actual_handle_count",handles(client).size());row.addProperty("bus_identity",System.identityHashCode(field(controller(),"sound")));return row;
    }
    private static JsonArray nativeVoices(List<Voice> voices) {
        JsonArray rows=new JsonArray();for(Voice voice:voices){int source=voice.binding().source();JsonObject row=DistanceOwnershipRuntimeTrace.event("manager_callback_native",source);row.addProperty("entity_id",voice.entityId());row.addProperty("layer",voice.layer()==null?"WET":voice.layer().name());row.add("position",nativeVector(source));row.add("listener_position",listenerVector());row.add("listener_orientation",listenerOrientation());row.addProperty("pitch",AL10.alGetSourcef(source,AL10.AL_PITCH));row.addProperty("source_type",AL10.alGetSourcei(source,AL10.AL_SOURCE_TYPE));row.addProperty("source_state",AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE));row.addProperty("relative",AL10.alGetSourcei(source,AL10.AL_SOURCE_RELATIVE)!=0);row.addProperty("buffer",AL10.alGetSourcei(source,AL10.AL_BUFFER));row.addProperty("queued",AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED));row.addProperty("processed",AL10.alGetSourcei(source,AL10.AL_BUFFERS_PROCESSED));row.addProperty("callback_calls",voice.binding().calls());row.addProperty("callback_fault",voice.binding().fault());row.addProperty("capture_calls",voice.capture().calls);row.addProperty("capture_input_frames",voice.capture().cursor);row.addProperty("capture_active",voice.capture().active);row.addProperty("registered_callback",SOFTCallbackBuffer.alGetBufferPtrSOFT(voice.binding().buffer(),SOFTCallbackBuffer.AL_BUFFER_CALLBACK_FUNCTION_SOFT));row.addProperty("registered_user",SOFTCallbackBuffer.alGetBufferPtrSOFT(voice.binding().buffer(),SOFTCallbackBuffer.AL_BUFFER_CALLBACK_USER_PARAM_SOFT));row.addProperty("callback_address",voice.binding().callbackAddress());row.addProperty("user_pointer",voice.binding().userPointer());row.addProperty("cif_return_type",voice.binding().cifReturnType());row.addProperty("cif_return_size",voice.binding().cifReturnSize());rows.add(row);}check(device());return rows;
    }
    private static JsonArray closedVoices(List<Voice> voices) {
        JsonArray result=new JsonArray();long[] calls=new long[voices.size()];for(int i=0;i<voices.size();i++)calls[i]=voices.get(i).capture().calls;render(device(),4096);
        for(int i=0;i<voices.size();i++){Voice voice=voices.get(i);var binding=voice.binding();JsonObject row=new JsonObject();row.addProperty("source",binding.source());row.addProperty("source_deleted",!AL10.alIsSource(binding.source()));row.addProperty("callback_closed",binding.closed());row.addProperty("callback_cleared",binding.callbackCleared());row.addProperty("buffer_deleted",binding.bufferDeleted());row.addProperty("closure_freed",binding.closureFreed());row.addProperty("quarantined",binding.quarantined());row.addProperty("failure",binding.failure());row.addProperty("callback_fault",binding.fault());row.addProperty("capture_calls_before_detached_render",calls[i]);row.addProperty("capture_calls_after_detached_render",voice.capture().calls);if(!binding.closed()||binding.quarantined()||AL10.alIsSource(binding.source())||calls[i]!=voice.capture().calls)throw new IllegalStateException("manager callback cleanup incomplete");result.add(row);}check(device());return result;
    }

    private static void drain(ClientGameTestContext context,String reason) {
        context.runOnClient(client->{WetSoundAdmissionCapture.close(reason);for(ProbeDrone drone:probes)if(client.level!=null)client.level.removeEntity(drone.getId(),net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);probes.clear();try{Method method=DroneSoundManager.class.getDeclaredMethod("stopAll",Minecraft.class);method.setAccessible(true);method.invoke(null,client);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}});
        context.waitFor(client->WetSoundAdmissionCapture.drained(),WAIT);sound(context,()->null);sound(context,()->{WetSoundAdmissionCapture.reclaimUnattached();return null;});context.runOnClient(client->engine(client).stopAll());sound(context,()->null);context.runOnClient(client->WetSoundAdmissionCapture.barrier(handles(client).size()));
    }
    private static AcousticVector awaitListener(ClientGameTestContext context,int epoch) {
        for(int attempt=0;attempt<WAIT;attempt++){context.waitTick();var sampled=new java.util.concurrent.atomic.AtomicReference<AcousticCameraListener.Sample>();context.runOnClient(client->sampled.set(cameraListener().latestFor(client.level)));if(sampled.get()==null)continue;var expected=sampled.get();JsonObject point=sound(context,()->{JsonObject r=new JsonObject();identity(r);r.add("native_position",listenerVector());r.add("expected_position",vector(expected.frame().positionMeters()));return r;});point.addProperty("epoch",epoch);point.addProperty("attempt",attempt);readiness.add(point);JsonArray position=point.getAsJsonArray("native_position");var actual=new AcousticVector(position.get(0).getAsDouble(),position.get(1).getAsDouble(),position.get(2).getAsDouble());if(actual.subtract(expected.frame().positionMeters()).length()<=1e-5)return actual;}throw new IllegalStateException("native listener pose not ready");
    }
    private static byte[] render(long device,int frames) {
        ByteBuffer bytes=ByteBuffer.allocate(frames*4).order(ByteOrder.LITTLE_ENDIAN);FloatBuffer samples=BufferUtils.createFloatBuffer(STEP);for(int frame=0;frame<frames;frame+=STEP){int count=Math.min(STEP,frames-frame);samples.clear().limit(count);SOFTLoopback.alcRenderSamplesSOFT(device,samples,count);while(samples.hasRemaining())putFinite(bytes,samples.get());}check(device);return bytes.array();
    }
    private static JsonArray nativeVector(int source){FloatBuffer b=BufferUtils.createFloatBuffer(3);AL10.alGetSourcefv(source,AL10.AL_POSITION,b);return vector(new AcousticVector(b.get(0),b.get(1),b.get(2)));}
    private static JsonArray listenerVector(){FloatBuffer b=BufferUtils.createFloatBuffer(3);AL10.alGetListenerfv(AL10.AL_POSITION,b);return vector(new AcousticVector(b.get(0),b.get(1),b.get(2)));}
    private static JsonArray listenerOrientation(){FloatBuffer b=BufferUtils.createFloatBuffer(6);AL10.alGetListenerfv(AL10.AL_ORIENTATION,b);JsonArray values=new JsonArray();for(int i=0;i<6;i++)values.add(b.get(i));return values;}
    private static JsonArray vector(AcousticVector p){JsonArray a=new JsonArray();a.add(p.x());a.add(p.y());a.add(p.z());return a;}
    private static void appendOutput(JsonObject row,String stage,byte[] bytes){JsonObject slice=new JsonObject();slice.addProperty("byte_offset",output.size());slice.addProperty("bytes",bytes.length);slice.addProperty("sha256",MainContextOutputCapture.sha(bytes));output.writeBytes(bytes);row.add(stage,slice);}
    private static void identity(JsonObject row){row.addProperty("device",device());row.addProperty("context",context());row.addProperty("owner_thread_id",Thread.currentThread().threadId());row.addProperty("version",AL10.alGetString(AL10.AL_VERSION));row.addProperty("extension",AL.getCapabilities().AL_SOFT_callback_buffer);MainContextOutputCapture.requireCurrent(device(),context());}
    private static void check(long device){int al=AL10.alGetError(),alc=ALC10.alcGetError(device);if(al!=0||alc!=0)throw new IllegalStateException("native errors "+al+"/"+alc);}
    private static void putFinite(ByteBuffer bytes,float sample){if(!Float.isFinite(sample))throw new IllegalStateException("non-finite loopback output");bytes.putFloat(sample);}
    private static Object field(Object target,String name){try{Class<?> type=target instanceof Class<?> c?c:target.getClass();Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class<?>?null:target);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static void set(Object target,String name,Object value){try{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    @SuppressWarnings("unchecked") private static Map<Integer,Object> activeSets(){return (Map<Integer,Object>)field(DroneSoundManager.class,"ACTIVE_SOUNDS");}
    private static Object controller(){return field(DroneSoundManager.class,"LISTENER_REVERB");}
    private static ListenerReverbState wetState(){return (ListenerReverbState)field(controller(),"state");}
    private static AcousticCameraListener cameraListener(){return (AcousticCameraListener)field(DroneSoundManager.class,"CAMERA_LISTENER");}
    private static SoundEngine engine(Minecraft client){return ((SoundManagerEngineAccessor)client.getSoundManager()).fpvdrone$getSoundEngine();}
    private static Map<SoundInstance,ChannelAccess.ChannelHandle> handles(Minecraft client){return ((SoundEngineChannelsAccessor)engine(client)).fpvdrone$getInstanceToChannel();}
    private static Library library(){return ((SoundEngineMainOutputAccessor)engine(Minecraft.getInstance())).mainOutput$getLibrary();}
    private static long device(){return ((MainOutputLibraryAccessor)library()).mainOutput$getDevice();}
    private static long context(){return ((MainOutputLibraryAccessor)library()).mainOutput$getContext();}
    private static <T>CompletableFuture<T> schedule(ClientGameTestContext context,Supplier<T> action){CompletableFuture<T> done=new CompletableFuture<>();context.runOnClient(client->((SoundEngineExecutorAccessor)engine(client)).fpvdrone$getExecutor().schedule(()->{try{done.complete(action.get());}catch(Throwable error){done.completeExceptionally(error);}}));return done;}
    private static <T>T sound(ClientGameTestContext context,Supplier<T> action){var done=schedule(context,action);context.waitFor(client->done.isDone(),WAIT);return done.join();}
    private static void restore(String name,String value){if(value==null)System.clearProperty(name);else System.setProperty(name,value);}
    private static byte[] read(Path path){try{return Files.readAllBytes(path);}catch(Exception error){throw new IllegalStateException(error);}}
    private static void checkProtocol(JsonObject protocol) {
        if(!protocol.get("wet_history_oracle_experiment").getAsBoolean()||protocol.get("shadow_prelude_client_ticks").getAsInt()!=12)throw new IllegalStateException("D175 history declaration missing");
        if(!protocol.get("owner_committed_frame_experiment").getAsBoolean())throw new IllegalStateException("committed frame declaration missing");
        if(!protocol.get("production_callback_experiment").getAsBoolean()||!protocol.get("actual_manager_callback_experiment").getAsBoolean())throw new IllegalStateException("manager callback flags missing");
        if(!protocol.get("test_only_generated_wet_environment").getAsBoolean())throw new IllegalStateException("generated environment declaration missing");var environment=protocol.getAsJsonObject("generated_wet_environment");var rt60=environment.getAsJsonObject("rt60_seconds");if(environment.get("snapshot_generation").getAsLong()!=172004||rt60.get("low").getAsDouble()!=.8||rt60.get("mid").getAsDouble()!=.6||rt60.get("high").getAsDouble()!=.4||environment.get("wet_gain").getAsDouble()!=.35||environment.get("transition_seconds").getAsDouble()!=.15)throw new IllegalStateException("generated environment differs from fixed fixture contract");
        Map<String,Integer> constants=Map.ofEntries(Map.entry("epochs",2),Map.entry("sample_rate",48000),Map.entry("render_step_frames",STEP),Map.entry("warm_frames",WARM),Map.entry("worker_frames_per_stall",WORKER),Map.entry("committed_frames_per_direction",COMMITTED),Map.entry("return_environment_frames",COMMITTED),Map.entry("expected_output_frames_per_epoch",WARM+2*(WORKER+COMMITTED)+COMMITTED),Map.entry("maximum_capture_frames_per_voice",MAX_CAPTURE_FRAMES),Map.entry("maximum_capture_calls_per_voice",MAX_CAPTURE_CALLS));
        for(var entry:constants.entrySet())if(protocol.get(entry.getKey()).getAsInt()!=entry.getValue())throw new IllegalStateException("protocol constant differs: "+entry.getKey());
        var ids=protocol.getAsJsonArray("expected_drone_ids");if(ids.size()!=6)throw new IllegalStateException("six drone IDs required");for(int i=0;i<6;i++)if(ids.get(i).getAsInt()!=ENTITY+i)throw new IllegalStateException("declared drone ID differs");
        if(protocol.get("radial_far_scale").getAsDouble()!=2||protocol.get("radial_near_scale").getAsDouble()!=1||protocol.get("maximum_S16_oracle_difference").getAsInt()!=1)throw new IllegalStateException("declared control/oracle gates differ");
    }
    private static void write(Path path,String value){try{Files.writeString(path,value,StandardOpenOption.CREATE_NEW);}catch(Exception error){throw new IllegalStateException(error);}}
    private static void writePcm(Path root,JsonObject protocol,JsonObject report,String name,byte[] bytes){String path=protocol.get(name+"_pcm_file").getAsString();try{Files.write(root.resolve(path),bytes,StandardOpenOption.CREATE_NEW);}catch(Exception error){throw new IllegalStateException(error);}report.addProperty(name+"_pcm_file",path);report.addProperty(name+"_pcm_bytes",bytes.length);report.addProperty(name+"_pcm_sha256",MainContextOutputCapture.sha(bytes));}
}
