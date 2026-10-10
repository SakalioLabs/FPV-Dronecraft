package com.tenicana.dronecraft.client.sound;

import com.google.gson.*;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.client.mixin.*;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Actual world RPM resumption through normal manager admission and asset resolution. */
final class ManagerAssetResumptionClientGameTest {
    private static final int WAIT=600;
    private static final List<Identifier> NORMAL_RESOURCES=List.of(
            Identifier.parse("fpvdrone:sounds/drone/motor_loop.ogg"),
            Identifier.parse("fpvdrone:sounds/drone/propeller_loop.ogg"));
    private static final List<String> PHASE_NAMES=List.of("WARM","SHORT_ZERO","SHORT_RESUME","LONG_ZERO","RESTART","FINAL_ZERO");
    private static final List<Integer> PHASE_RPM=List.of(12000,0,12000,0,12000,0);
    private static final List<Integer> PHASE_TICKS=List.of(6,6,12,60,12,60);
    private static final ByteArrayOutputStream input=new ByteArrayOutputStream(),output=new ByteArrayOutputStream();
    private static final JsonArray listenerReadiness=new JsonArray();
    private static ProbeDrone probe;
    private static ManagerAssetResumptionCapture.Session activeCapture;

    private static final class ProbeDrone extends DroneEntity {
        volatile float rpm=12000;
        ProbeDrone(ClientLevel level,int id) {super(DroneEntityTypes.DRONE,level);setId(id);}
        @Override public float getAverageMotorRpm() {return rpm;}
        @Override public float getMotorRpm(int index) {return rpm;}
        @Override public float getMotorPower() {return .5f;}
        @Override public float getMotorPower(int index) {return .5f;}
        @Override public void tick() {tickCount++;}
    }
    private record ListenerReadback(AcousticVector position,JsonObject observation) { }

    static boolean requested() {
        if(!AssetNativeCapture.requested())return false;
        JsonObject p=MainContextOutputCapture.protocol();
        return p.has("manager_asset_resumption_experiment")&&p.get("manager_asset_resumption_experiment").getAsBoolean();
    }

    static void run(ClientGameTestContext context) {
        JsonObject protocol=MainContextOutputCapture.protocol(),report=new JsonObject();
        Path root=Path.of(protocol.get("workspace_root").getAsString());
        JsonArray rows=new JsonArray();JsonObject controls=new JsonObject();Throwable failure=null;
        EnumMap<SoundSource,Double> options=new EnumMap<>(SoundSource.class);
        CalibratedAssetResources.Catalog originalCatalog=CalibratedAssetResources.snapshot();
        report.addProperty("started_utc",Instant.now().toString());
        report.addProperty("protocol_sha256",sha(bytes(Path.of(System.getProperty(AssetNativeCapture.PROPERTY)))));
        report.add("evidence_sha256",protocol.get("evidence_sha256").deepCopy());
        try {
            validateProtocol(protocol);
            if(!CalibratedGainCapture.requested() || !Objects.equals(System.getProperty(CalibratedGainCapture.PROPERTY),System.getProperty(AssetNativeCapture.PROPERTY)))
                throw new IllegalStateException("manager resumption requires calibratedGainProtocol and assetNativeProtocol bound to the same declaration");
            JsonObject loaded=new JsonObject();
            for(var item:protocol.getAsJsonArray("required_loaded_class_paths")) {
                String path=item.getAsString(),resource="/"+path.substring(path.indexOf("com/tenicana/"));
                try(var stream=ManagerAssetResumptionClientGameTest.class.getResourceAsStream(resource)) {
                    if(stream==null)throw new IllegalStateException("loaded class absent "+resource);
                    loaded.addProperty(path,sha(stream.readAllBytes()));
                }
            }
            report.add("loaded_class_sha256",loaded);
            byte[] encoded=bytes(root.resolve("fabric-mod/src/gametest/resources/assets/fpvdrone_gain_control/sounds/calibrated_160.ogg"));
            byte[] decoded=decodeReference(encoded);
            // Shared decoder bytes bound the independent offline cursor/C oracle; no physical qualification follows.
            writePcm(root,protocol,report,"decoded_reference",decoded);
            report.addProperty("encoded_reference_sha256",sha(encoded));report.addProperty("decoded_reference_sha256",sha(decoded));
            report.addProperty("decoded_reference_frames",decoded.length/2);
            context.runOnClient(client->{
                for(SoundSource source:SoundSource.values()) {options.put(source,client.options.getSoundSourceOptionInstance(source).get());client.options.getSoundSourceOptionInstance(source).set(source==SoundSource.MASTER||source==SoundSource.NEUTRAL?1.0:0.0);}
                client.getSoundManager().updateCategoryVolume(SoundSource.NEUTRAL,1);
            });
            try(TestSingleplayerContext world=context.worldBuilder().create()) {
                try {
                world.getClientWorld().waitForChunksDownload();world.getServer().runCommand("gamerule spawn_mobs false");
                drain(context,"world_ready");
                for(int epoch=0;epoch<protocol.get("epochs").getAsInt();epoch++) {
                    if(epoch>0)context.runOnClient(client->client.getSoundManager().reload());
                    AcousticVector listener=awaitNativeListener(context,"EPOCH_"+epoch);
                    for(var item:protocol.getAsJsonArray("expected_cases")) {
                        JsonObject row=item.getAsJsonObject().deepCopy();row.addProperty("epoch",epoch);rows.add(row);
                        ManagerAssetResumptionCapture.Session captured=captureCase(context,protocol,row,listener,encoded,decoded);
                        drain(context,"epoch_"+epoch+":"+row.get("name").getAsString());
                        row.add("clock",ManagerAssetResumptionCapture.snapshot(captured));
                        context.runOnClient(client->{row.addProperty("handles_after_cleanup",handles(client).size());row.addProperty("manager_sets_after_cleanup",ManagerAssetResumptionCapture.activeSets().size());});
                        onSoundThread(context,()->{sealInputs(row,captured);return null;});
                        if(captured.failure.get()!=null)throw new IllegalStateException("resumption observation failed during cleanup",captured.failure.get());
                    }
                    int finishedEpoch=epoch;
                    context.runOnClient(client->controls.add("epoch_"+finishedEpoch+"_cleanup",membership(client)));
                }
                } finally {
                    // Also on failure, disarm and pass an owner FIFO barrier before world/context teardown.
                    drain(context,"before_world_close");
                }
            }
            drain(context,"final");context.runOnClient(client->client.getSoundManager().destroy());
            report.addProperty("status","manager_asset_resumption_captured");
        } catch(Throwable error) {
            failure=error;report.addProperty("status","manager_asset_resumption_failed");report.addProperty("failure",error.toString());
            try {drain(context,"failure");context.runOnClient(client->client.getSoundManager().destroy());}
            catch(Throwable cleanup) {report.addProperty("cleanup_failure",cleanup.toString());}
        } finally {
            context.runOnClient(client->{setCatalog(originalCatalog);options.forEach((source,value)->client.options.getSoundSourceOptionInstance(source).set(value));});
        }
        report.add("rows",rows);report.add("controls",controls);report.add("listener_readiness",listenerReadiness);
        report.add("gain_observations",AssetNativeCapture.snapshot());report.add("envelope_observations",CalibratedGainCapture.snapshot());
        report.add("epochs",MainContextOutputCapture.epochs());report.add("runtime_events",DistanceOwnershipRuntimeTrace.snapshot());
        report.add("channel_allocations",WetChannelLifecycleCapture.allocations());report.add("channel_calls",WetChannelLifecycleCapture.calls());
        report.add("sound_admission",WetSoundAdmissionCapture.snapshot());
        JsonObject scope=new JsonObject();
        for(String name:List.of("actual_Minecraft_client_started","actual_minecraft_main_context_output","actual_client_world_entities",
                "actual_production_manager_selection","normal_resolved_asset_keys","actual_two_layer_calibrated_asset_streams",
                "test_injected_numeric_catalog","software_generated_asset_only","natural_manager_rpm_zero_release",
                "actual_manager_rpm_resumption_stimulus","gametest_only_nonmutating_taps","test_only_per_client_tick_loopback_clock",
                "decoded_reference_shared_JOrbis_decoder","actual_sound_tick_before_after_observations"))scope.addProperty(name,true);
        for(String name:List.of("measured_asset_profile_selected","asset_physical_qualification","physical_endpoint_verified",
                "real_pressure_or_rpm_accessed","ordinary_realtime_tick_playback_verified","hardware_mixer_sample_atomicity_verified",
                "resumption_contract_passed","release_tail_contract_passed","audible_click_verified",
                "queued_sample_playback_tick_alignment_verified","catalog_reload_selection_verified","full_goal_complete"))scope.addProperty(name,false);
        scope.addProperty("legacy_before_tick_target_used_as_oracle",false);report.add("scope",scope);
        writePcm(root,protocol,report,"input",input.toByteArray());writePcm(root,protocol,report,"output",output.toByteArray());
        report.addProperty("finished_utc",Instant.now().toString());
        try {Files.writeString(root.resolve(protocol.get("output_report").getAsString()),new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report)+"\n",StandardOpenOption.CREATE_NEW);}
        catch(IOException error) {throw new IllegalStateException(error);}
        if(failure!=null)throw new IllegalStateException("actual manager asset resumption capture failed",failure);
    }

    private static void validateProtocol(JsonObject p) {
        if(!p.get("manager_asset_resumption_experiment").getAsBoolean() || p.get("epochs").getAsInt()!=2
                || p.get("render_step_frames").getAsInt()!=2400 || p.get("entity_id").getAsInt()!=178001
                || p.get("distance_m").getAsDouble()!=1 || p.get("synthetic_rpm").getAsFloat()!=12000)
            throw new IllegalArgumentException("manager resumption clock/stimulus differs from declared bounded fixture");
        JsonArray cases=p.getAsJsonArray("expected_cases");
        if(cases.size()!=2 || !cases.get(0).getAsJsonObject().get("name").getAsString().equals("C1")
                || cases.get(0).getAsJsonObject().get("gain_db").getAsDouble()!=0
                || !cases.get(1).getAsJsonObject().get("name").getAsString().equals("C24")
                || cases.get(1).getAsJsonObject().get("gain_db").getAsDouble()!=24)
            throw new IllegalArgumentException("manager resumption requires C1 then C24");
        JsonArray phases=p.getAsJsonArray("phase_sequence");
        if(phases.size()!=PHASE_NAMES.size())throw new IllegalArgumentException("wrong resumption phase count");
        for(int i=0;i<PHASE_NAMES.size();i++) {
            JsonObject phase=phases.get(i).getAsJsonObject();
            if(!phase.get("name").getAsString().equals(PHASE_NAMES.get(i)) || phase.get("rpm").getAsInt()!=PHASE_RPM.get(i)
                    || phase.get("ticks").getAsInt()!=PHASE_TICKS.get(i))
                throw new IllegalArgumentException("wrong resumption phase "+i);
        }
    }

    private static ManagerAssetResumptionCapture.Session captureCase(ClientGameTestContext context,JsonObject protocol,JsonObject row,AcousticVector listener,
                                    byte[] encoded,byte[] decoded) {
        String name="EPOCH_"+row.get("epoch").getAsInt()+":"+row.get("name").getAsString();
        AssetNativeCapture.phase(name+":ADMISSION");CalibratedGainCapture.phase(name+":ADMISSION");
        double coefficient=Math.pow(10,row.get("gain_db").getAsDouble()/20);
        AudioAssetCalibration calibration=calibration(encoded,decoded,coefficient);final VerifiedAssetPcm pcm;
        try {pcm=CalibratedAssetDecoder.decode(calibration,encoded);}catch(IOException error){throw new IllegalStateException(error);}
        row.addProperty("coefficient",coefficient);row.addProperty("numeric_combined_pair_peak_bound",2*decodedPeak(decoded)*coefficient);
        if(2*decodedPeak(decoded)*coefficient>=.95)throw new IllegalStateException("generated two-layer input lacks combined native headroom");
        AtomicReference<CalibratedAssetResources.Catalog> installed=new AtomicReference<>();
        context.runOnClient(client->{
            probe=new ProbeDrone(client.level,protocol.get("entity_id").getAsInt());
            probe.setPos(listener.x(),listener.y()-probe.getBbHeight()*.5,listener.z()-protocol.get("distance_m").getAsDouble());
            var key=AcousticProfileResources.keyOf(probe);Map<CalibratedAssetResources.BindingKey,VerifiedAssetPcm> bindings=new HashMap<>();
            for(int i=0;i<2;i++)bindings.put(new CalibratedAssetResources.BindingKey(key,DroneLoopSoundInstance.Layer.values()[i].name(),NORMAL_RESOURCES.get(i)),pcm);
            var catalog=new CalibratedAssetResources.Catalog(bindings);setCatalog(catalog);installed.set(catalog);
            row.addProperty("fixture_catalog_identity",System.identityHashCode(catalog));row.addProperty("fixture_asset_identity",System.identityHashCode(pcm));
            row.addProperty("fixture_entity_identity",System.identityHashCode(probe));row.addProperty("fixture_entity_id",probe.getId());
            ProbeDrone capturedProbe=probe;
            // Observe constructors and all genuine admission ticks, before normal manager discovers the world entity.
            activeCapture=ManagerAssetResumptionCapture.begin(name,capturedProbe,protocol.get("render_step_frames").getAsInt(),rpm->capturedProbe.rpm=rpm);
            WetSoundAdmissionCapture.live();client.level.addEntity(probe);
        });
        context.waitFor(client->{
            if(!ManagerAssetResumptionCapture.initialStreamsReady(activeCapture,client))return false;
            Object set=ManagerAssetResumptionCapture.activeSets().get(probe.getId());if(set==null)return false;
            for(String layer:List.of("motor","propeller")) {
                Object value=ManagerAssetResumptionCapture.field(set,layer);
                if(!(value instanceof DroneLoopSoundInstance sound) || sound.getVolume()!=.99999994f)return false;
            }
            return true;
        },WAIT);
        onSoundThread(context,()->null);
        context.runOnClient(client->{
            Object set=ManagerAssetResumptionCapture.activeSets().get(probe.getId());
            if(ManagerAssetResumptionCapture.field(set,"drone")!=probe || client.level.getEntity(probe.getId())!=probe
                    || ManagerAssetResumptionCapture.activeSets().size()!=1 || handles(client).size()!=2)
                throw new IllegalStateException("actual normalmanager two-layer/world admission lost");
            verifyResolvedGenerations(activeCapture,installed.get(),pcm);
            row.add("membership_before_clock",membership(client));
        });
        onSoundThread(context,()->{
            identity(row);row.addProperty("version",AL10.alGetString(AL10.AL_VERSION));row.addProperty("native_listener_gain",AL10.alGetListenerf(AL10.AL_GAIN));
            row.addProperty("library_debug",((SoundEngineMainOutputAccessor)engine(Minecraft.getInstance())).mainOutput$getLibrary().getDebugString());return null;
        });
        context.runOnClient(client->ManagerAssetResumptionCapture.startClock(activeCapture));
        context.waitFor(client->activeCapture.complete.isDone(),WAIT);onSoundThread(context,()->null);
        ManagerAssetResumptionCapture.Session capture=activeCapture;
        context.runOnClient(client->{verifyResolvedGenerations(capture,installed.get(),pcm);row.add("membership_after_clock",membership(client));});
        JsonObject clock=ManagerAssetResumptionCapture.snapshot(capture);row.add("clock",clock);
        byte[] rendered=capture.output.toByteArray();row.addProperty("output_offset_bytes",output.size());row.addProperty("output_bytes",rendered.length);
        row.addProperty("output_sha256",sha(rendered));output.writeBytes(rendered);
        for(var generation:ManagerAssetResumptionCapture.generations(capture)) {
            if(generation.channel==null || generation.stream==null)throw new IllegalStateException("observed sound generation never obtained its actual calibrated stream");
        }
        if(capture.failure.get()!=null)throw new IllegalStateException("per-tick native owner clock failed",capture.failure.get());
        return capture;
    }

    private static void verifyResolvedGenerations(ManagerAssetResumptionCapture.Session capture,CalibratedAssetResources.Catalog installed,VerifiedAssetPcm pcm) {
        for(var generation:ManagerAssetResumptionCapture.generations(capture)) {
            DroneLoopSoundInstance sound=generation.sound;int layer=sound.layer().ordinal();
            if(sound.usesProceduralAudio() || ManagerAssetResumptionCapture.field(sound,"assetCatalog")!=installed
                    || ManagerAssetResumptionCapture.field(sound,"calibratedAsset")!=pcm
                    || !NORMAL_RESOURCES.get(layer).equals(ManagerAssetResumptionCapture.field(sound,"calibratedResource")))
                throw new IllegalStateException("normal resolution/catalog binding was bypassed or failed for generation "+generation.generation);
        }
    }

    private static void sealInputs(JsonObject row,ManagerAssetResumptionCapture.Session capture) {
        List<ManagerAssetResumptionCapture.Generation> generations=ManagerAssetResumptionCapture.generations(capture);
        JsonArray bindings=row.getAsJsonObject("clock").getAsJsonArray("generations");
        if(bindings.size()!=generations.size())throw new IllegalStateException("generation snapshot incomplete");
        for(int i=0;i<generations.size();i++) {
            var generation=generations.get(i);JsonObject binding=bindings.get(i).getAsJsonObject();CalibratedAssetAudioStream stream=generation.stream;
            if(generation.channel==null || stream==null)throw new IllegalStateException("cannot seal an unattached sound generation");
            binding.addProperty("stream_closed_after_cleanup",AssetNativeCapture.closed(stream));
            if(!AssetNativeCapture.closed(stream))throw new IllegalStateException("actual selected asset cursor not closed");
            byte[] bytes=MainContextOutputCapture.input(stream);binding.addProperty("input_offset_bytes",input.size());binding.addProperty("input_bytes",bytes.length);
            binding.addProperty("input_sha256",sha(bytes));binding.addProperty("input_record_identity",MainContextOutputCapture.identity(stream));
            binding.add("asset_read_batches",AssetNativeCapture.reads(stream));input.writeBytes(bytes);
            binding.add("native_after_cleanup",ManagerAssetResumptionCapture.nativeSnapshot(generation.channel));
        }
        row.addProperty("inputs_sealed_after_cleanup",true);
        row.addProperty("library_debug_after_cleanup",((SoundEngineMainOutputAccessor)engine(Minecraft.getInstance())).mainOutput$getLibrary().getDebugString());
    }

    private static JsonObject membership(Minecraft client) {
        JsonObject row=new JsonObject();row.addProperty("manager_set_count",ManagerAssetResumptionCapture.activeSets().size());row.addProperty("actual_handle_count",handles(client).size());
        JsonArray entities=new JsonArray();if(client.level!=null)for(Entity e:client.level.entitiesForRendering())if(e instanceof DroneEntity)entities.add(e.getId());row.add("world_drone_ids",entities);
        Object set=probe==null?null:ManagerAssetResumptionCapture.activeSets().get(probe.getId());
        row.addProperty("entity_id",probe==null?0:probe.getId());row.addProperty("world_entity_identity",probe==null||client.level==null?0:System.identityHashCode(client.level.getEntity(probe.getId())));
        row.addProperty("manager_entity_identity",set==null?0:System.identityHashCode(ManagerAssetResumptionCapture.field(set,"drone")));return row;
    }

    private static void drain(ClientGameTestContext context,String reason) {
        ManagerAssetResumptionCapture.Session abandoned=activeCapture;
        if(abandoned!=null) {
            context.runOnClient(client->{ManagerAssetResumptionCapture.markCleanup(abandoned);ManagerAssetResumptionCapture.disarm();});
            onSoundThread(context,()->null);
        }
        context.runOnClient(client->{
            WetSoundAdmissionCapture.close(reason);
            if(probe!=null && client.level!=null)client.level.removeEntity(probe.getId(),Entity.RemovalReason.DISCARDED);probe=null;
            try {Method stop=DroneSoundManager.class.getDeclaredMethod("stopAll",Minecraft.class);stop.setAccessible(true);stop.invoke(null,client);}
            catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        });
        context.waitFor(client->WetSoundAdmissionCapture.drained(),WAIT);onSoundThread(context,()->null);
        onSoundThread(context,()->{WetSoundAdmissionCapture.reclaimUnattached();return null;});
        context.runOnClient(client->engine(client).stopAll());onSoundThread(context,()->null);
        context.runOnClient(client->WetSoundAdmissionCapture.barrier(handles(client).size()));
        // Keep the read-only recorder alive for actual cleanup stops, then clear it before context destruction.
        if(abandoned!=null) {ManagerAssetResumptionCapture.abandonAfterOwnerBarrier(abandoned);activeCapture=null;}
    }

    private static AcousticVector awaitNativeListener(ClientGameTestContext context,String name) {
        JsonObject group=new JsonObject();JsonArray attempts=new JsonArray();group.addProperty("name",name);group.add("attempts",attempts);listenerReadiness.add(group);
        for(int attempt=0;attempt<WAIT;attempt++) {
            context.waitTick();AtomicReference<AcousticCameraListener.Sample> sample=new AtomicReference<>();
            context.runOnClient(client->sample.set(((AcousticCameraListener)ManagerAssetResumptionCapture.field(DroneSoundManager.class,"CAMERA_LISTENER")).latestFor(client.level)));
            if(sample.get()==null)continue;
            var expected=sample.get();ListenerReadback readback=onSoundThread(context,()->{
                FloatBuffer buffer=BufferUtils.createFloatBuffer(3);AL10.alGetListenerfv(AL10.AL_POSITION,buffer);
                AcousticVector position=new AcousticVector(buffer.get(0),buffer.get(1),buffer.get(2));var transform=engine(Minecraft.getInstance()).getListenerTransform().position();
                JsonObject nativeRow=new JsonObject();identity(nativeRow);nativeRow.add("camera_position",vector(expected.frame().positionMeters()));nativeRow.add("native_position",vector(position));
                nativeRow.addProperty("ready",position.subtract(expected.frame().positionMeters()).length()<=1e-5 && new AcousticVector(transform.x,transform.y,transform.z).subtract(expected.frame().positionMeters()).length()<=1e-5);
                nativeRow.addProperty("al_error",AL10.alGetError());if(nativeRow.get("al_error").getAsInt()!=0)throw new IllegalStateException("native listener getter failed");return new ListenerReadback(position,nativeRow);
            });readback.observation().addProperty("attempt",attempt);attempts.add(readback.observation());
            if(readback.observation().get("ready").getAsBoolean()){group.addProperty("ready",true);return readback.position();}
        }
        throw new IllegalStateException("normal native listener did not acknowledge camera pose");
    }

    private static AudioAssetCalibration calibration(byte[] encoded,byte[] decoded,double coefficient) {
        double squares=0;var shorts=ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        while(shorts.hasRemaining()){double x=shorts.get()/32768.0;squares+=x*x;}double rms=Math.sqrt(squares/(decoded.length/2));
        return new AudioAssetCalibration(sha(encoded),sha(decoded),48000,decoded.length/2,0,decoded.length/2,rms,rms*coefficient,1,
                12000,0,List.of(8000.0,12000.0,16000.0),List.of(-1.0,0.0,1.0),List.of(List.of(0.0,0.0,0.0),List.of(0.0,0.0,0.0),List.of(0.0,0.0,0.0)));
    }
    private static double decodedPeak(byte[] decoded) {double peak=0;var s=ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();while(s.hasRemaining())peak=Math.max(peak,Math.abs(s.get()/32768.0));return peak;}
    private static byte[] decodeReference(byte[] encoded) {
        try(var decoder=new JOrbisAudioStream(new ByteArrayInputStream(encoded))) {ByteArrayOutputStream out=new ByteArrayOutputStream();while(true){ByteBuffer chunk=decoder.read(8192);if(!chunk.hasRemaining())break;byte[] b=new byte[chunk.remaining()];chunk.get(b);out.writeBytes(b);}return out.toByteArray();}
        catch(IOException error){throw new IllegalStateException(error);}
    }
    private static void setCatalog(CalibratedAssetResources.Catalog catalog) {try{Field f=CalibratedAssetResources.class.getDeclaredField("catalog");f.setAccessible(true);f.set(null,catalog);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static SoundEngine engine(Minecraft client) {return ((SoundManagerEngineAccessor)client.getSoundManager()).fpvdrone$getSoundEngine();}
    private static Map<SoundInstance,ChannelAccess.ChannelHandle> handles(Minecraft client) {return ((SoundEngineChannelsAccessor)engine(client)).fpvdrone$getInstanceToChannel();}
    private static MainOutputLibraryAccessor library() {return (MainOutputLibraryAccessor)((SoundEngineMainOutputAccessor)engine(Minecraft.getInstance())).mainOutput$getLibrary();}
    private static <T> T onSoundThread(ClientGameTestContext context,Supplier<T> action) {
        CompletableFuture<T> done=new CompletableFuture<>();context.runOnClient(client->((SoundEngineExecutorAccessor)engine(client)).fpvdrone$getExecutor().schedule(()->{try{done.complete(action.get());}catch(Throwable error){done.completeExceptionally(error);}}));
        context.waitFor(client->done.isDone(),WAIT);return done.join();
    }
    private static JsonArray vector(AcousticVector v) {JsonArray a=new JsonArray();a.add(v.x());a.add(v.y());a.add(v.z());return a;}
    private static void identity(JsonObject row) {var l=library();row.addProperty("device",l.mainOutput$getDevice());row.addProperty("context",l.mainOutput$getContext());row.addProperty("generation",MainContextOutputCapture.generation(l.mainOutput$getDevice(),l.mainOutput$getContext()));}
    private static byte[] bytes(Path path) {try{return Files.readAllBytes(path);}catch(IOException error){throw new IllegalStateException(error);}}
    private static String sha(byte[] data) {return MainContextOutputCapture.sha(data);}
    private static void writePcm(Path root,JsonObject protocol,JsonObject report,String name,byte[] bytes) {try{Files.write(root.resolve(protocol.get(name+"_pcm_file").getAsString()),bytes,StandardOpenOption.CREATE_NEW);report.addProperty(name+"_pcm_bytes",bytes.length);report.addProperty(name+"_pcm_sha256",sha(bytes));}catch(IOException error){throw new IllegalStateException(error);}}
}
