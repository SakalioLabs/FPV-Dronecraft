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

/** Bounded normal-manager asset capacity falsification; aggregate headroom is not a producer acceptance gate. */
final class ManagerAssetCapacityClientGameTest {
    private static final int WAIT=600,SETTLE=48000,STEADY=48000;
    private static final List<Identifier> NORMAL_RESOURCES=List.of(Identifier.parse("fpvdrone:sounds/drone/motor_loop.ogg"),Identifier.parse("fpvdrone:sounds/drone/propeller_loop.ogg"));
    private static final List<String> CASE_NAMES=List.of("C1_R1","C24_R6","C24_R1");
    private static final List<Integer> GAINS=List.of(0,24,24),DISTANCES=List.of(1,6,1);
    private static final List<ProbeDrone> probes=new ArrayList<>();
    private static final ByteArrayOutputStream input=new ByteArrayOutputStream(),output=new ByteArrayOutputStream();
    private static final JsonArray listenerReadiness=new JsonArray();
    private static final class ProbeDrone extends DroneEntity {
        volatile float rpm=12000;
        ProbeDrone(ClientLevel level,int id) {super(DroneEntityTypes.DRONE,level);setId(id);}
        @Override public float getAverageMotorRpm() {return rpm;}
        @Override public float getMotorRpm(int index) {return rpm;}
        @Override public float getMotorPower() {return .5f;}
        @Override public float getMotorPower(int index) {return .5f;}
        @Override public void tick() {tickCount++;}
    }
    private record Voice(ProbeDrone drone,Object set,DroneLoopSoundInstance sound,Channel channel,CalibratedAssetAudioStream stream) { }
    private record ListenerReadback(AcousticVector position,JsonObject observation) { }

    static boolean requested() {
        if(!AssetNativeCapture.requested())return false;JsonObject p=MainContextOutputCapture.protocol();
        return p.has("manager_asset_capacity_experiment")&&p.get("manager_asset_capacity_experiment").getAsBoolean();
    }

    static void run(ClientGameTestContext context) {
        JsonObject protocol=MainContextOutputCapture.protocol(),report=new JsonObject(),controls=new JsonObject();
        Path root=Path.of(protocol.get("workspace_root").getAsString());JsonArray rows=new JsonArray();Throwable failure=null;
        EnumMap<SoundSource,Double> options=new EnumMap<>(SoundSource.class);var originalCatalog=CalibratedAssetResources.snapshot();
        report.addProperty("started_utc",Instant.now().toString());report.addProperty("protocol_sha256",sha(bytes(Path.of(System.getProperty(AssetNativeCapture.PROPERTY)))));
        report.add("evidence_sha256",protocol.get("evidence_sha256").deepCopy());
        try {
            validateProtocol(protocol);
            if(!CalibratedGainCapture.requested() || !Objects.equals(System.getProperty(CalibratedGainCapture.PROPERTY),System.getProperty(AssetNativeCapture.PROPERTY)))throw new IllegalStateException("capacity fixture requires both asset properties bound to the same declaration");
            JsonObject loaded=new JsonObject();for(var value:protocol.getAsJsonArray("required_loaded_class_paths")) {
                String path=value.getAsString(),resource="/"+path.substring(path.indexOf("com/tenicana/"));
                try(var stream=ManagerAssetCapacityClientGameTest.class.getResourceAsStream(resource)) {if(stream==null)throw new IllegalStateException("loaded class absent "+resource);loaded.addProperty(path,sha(stream.readAllBytes()));}
            }report.add("loaded_class_sha256",loaded);
            byte[] encoded=bytes(root.resolve("fabric-mod/src/gametest/resources/assets/fpvdrone_gain_control/sounds/calibrated_160.ogg")),decoded=decodeReference(encoded);
            if(decoded.length!=48000*2)throw new IllegalStateException("generated whole-period reference must contain exactly 48000 mono S16 samples");
            writePcm(root,protocol,report,"decoded_reference",decoded);report.addProperty("encoded_reference_sha256",sha(encoded));report.addProperty("decoded_reference_sha256",sha(decoded));report.addProperty("decoded_reference_frames",decoded.length/2);
            context.runOnClient(client->{for(SoundSource source:SoundSource.values()) {options.put(source,client.options.getSoundSourceOptionInstance(source).get());client.options.getSoundSourceOptionInstance(source).set(source==SoundSource.MASTER||source==SoundSource.NEUTRAL?1.0:0.0);}client.getSoundManager().updateCategoryVolume(SoundSource.NEUTRAL,1);});
            try(TestSingleplayerContext world=context.worldBuilder().create()) {
                try {
                    world.getClientWorld().waitForChunksDownload();world.getServer().runCommand("gamerule spawn_mobs false");cleanup(context,"world_ready");
                    for(int epoch=0;epoch<2;epoch++) {
                        if(epoch>0)context.runOnClient(client->{if(!activeSets().isEmpty() || !handles(client).isEmpty())throw new IllegalStateException("context reload before empty capacity cleanup");client.getSoundManager().reload();});
                        for(var value:protocol.getAsJsonArray("expected_cases")) {
                            JsonObject row=value.getAsJsonObject().deepCopy();row.addProperty("epoch",epoch);rows.add(row);
                            AcousticVector listener=awaitNativeListener(context,"EPOCH_"+epoch+":"+row.get("name").getAsString());
                            captureCase(context,protocol,row,listener,encoded,decoded);
                        }
                        int finishedEpoch=epoch;context.runOnClient(client->controls.add("epoch_"+finishedEpoch+"_cleanup",membership(client)));
                    }
                } finally {cleanup(context,"before_world_close");}
            }
            cleanup(context,"final");context.runOnClient(client->client.getSoundManager().destroy());report.addProperty("status","manager_asset_capacity_captured");
        } catch(Throwable error) {
            failure=error;report.addProperty("status","manager_asset_capacity_failed");report.addProperty("failure",error.toString());
            try {cleanup(context,"failure");context.runOnClient(client->client.getSoundManager().destroy());}catch(Throwable cleanup){report.addProperty("cleanup_failure",cleanup.toString());}
        } finally {
            try {context.runOnClient(client->{
                if(!activeSets().isEmpty() || !handles(client).isEmpty())throw new IllegalStateException("original catalog restoration before empty capacity cleanup");
                JsonObject event=new JsonObject();event.addProperty("before_catalog_identity",System.identityHashCode(CalibratedAssetResources.snapshot()));new CalibratedAssetResources().apply(originalCatalog,client.getResourceManager(),Runnable::run).join();
                event.addProperty("original_catalog_identity",System.identityHashCode(originalCatalog));event.addProperty("after_catalog_identity",System.identityHashCode(CalibratedAssetResources.snapshot()));event.addProperty("actual_client_thread",client.isSameThread());event.addProperty("thread",Thread.currentThread().getName());controls.add("original_catalog_restoration",event);
                options.forEach((source,value)->client.options.getSoundSourceOptionInstance(source).set(value));
            });}catch(Throwable restore){report.addProperty("restoration_failure",restore.toString());report.addProperty("status","manager_asset_capacity_failed");if(failure==null)failure=restore;}
        }
        report.add("rows",rows);report.add("controls",controls);report.add("listener_readiness",listenerReadiness);report.add("gain_observations",AssetNativeCapture.snapshot());report.add("envelope_observations",CalibratedGainCapture.snapshot());
        report.add("epochs",MainContextOutputCapture.epochs());report.add("runtime_events",DistanceOwnershipRuntimeTrace.snapshot());report.add("channel_allocations",WetChannelLifecycleCapture.allocations());report.add("channel_calls",WetChannelLifecycleCapture.calls());report.add("sound_admission",WetSoundAdmissionCapture.snapshot());
        JsonObject scope=new JsonObject();for(String name:List.of("actual_Minecraft_client_started","actual_minecraft_main_context_output","actual_client_world_entities","actual_production_manager_selection","normal_resolved_asset_keys","test_constructed_numeric_catalog","actual_production_catalog_apply_publication","software_generated_asset_only","decoded_reference_shared_JOrbis_decoder","actual_twelve_asset_streams_per_case","complete_prefix_and_steady_output_retained","test_only_sound_owner_queue_service","natural_rpm_zero_cleanup_stimulus","bounded_static_steady_capacity_falsification"))scope.addProperty(name,true);
        for(String name:List.of("capacity_headroom_contract_passed","maximum_safe_gain_established","successful_resource_prepare_qualified","full_resource_pack_reload_selection_verified","physical_asset_qualification","measured_five_inch_source_verified","physical_error_recomputed","real_pressure_or_rpm_accessed","physical_endpoint_verified","release_wave_contract_verified","native_zero_gain_at_stop_boundary_verified","queued_sample_playback_tick_alignment_verified","late_future_quarantine_verified","ordinary_realtime_playback_verified","hardware_mixer_sample_atomicity_verified","OpenAL_mixed_transient_independently_reconstructed","full_goal_complete"))scope.addProperty(name,false);report.add("scope",scope);
        writePcm(root,protocol,report,"input",input.toByteArray());writePcm(root,protocol,report,"output",output.toByteArray());report.addProperty("finished_utc",Instant.now().toString());
        try {Files.writeString(root.resolve(protocol.get("output_report").getAsString()),new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report)+"\n",StandardOpenOption.CREATE_NEW);}catch(IOException error){throw new IllegalStateException(error);}
        if(failure!=null)throw new IllegalStateException("actual manager asset capacity capture failed",failure);
    }

    private static void validateProtocol(JsonObject p) {
        if(!p.get("manager_asset_capacity_experiment").getAsBoolean() || p.get("epochs").getAsInt()!=2 || p.get("synthetic_rpm").getAsInt()!=12000 || p.get("settle_frames").getAsInt()!=SETTLE || p.get("steady_frames").getAsInt()!=STEADY || p.get("render_chunk_frames").getAsInt()!=1024 || p.get("total_output_frames").getAsInt()!=576000)throw new IllegalArgumentException("capacity clock/stimulus differs");
        JsonArray ids=p.getAsJsonArray("entity_ids"),cases=p.getAsJsonArray("expected_cases");if(ids.size()!=6 || cases.size()!=3)throw new IllegalArgumentException("capacity requires six entities and three cases");
        for(int i=0;i<6;i++)if(ids.get(i).getAsInt()!=180001+i)throw new IllegalArgumentException("wrong capacity entity id");
        for(int i=0;i<3;i++){JsonObject row=cases.get(i).getAsJsonObject();if(!row.get("name").getAsString().equals(CASE_NAMES.get(i)) || row.get("gain_db").getAsInt()!=GAINS.get(i) || row.get("distance_m").getAsInt()!=DISTANCES.get(i))throw new IllegalArgumentException("wrong capacity case "+i);}
    }

    private static void captureCase(ClientGameTestContext context,JsonObject protocol,JsonObject row,AcousticVector listener,byte[] encoded,byte[] decoded) {
        String phase="EPOCH_"+row.get("epoch").getAsInt()+":"+row.get("name").getAsString();setPhase(phase+":ADMISSION");
        double coefficient=Math.pow(10,row.get("gain_db").getAsDouble()/20),distance=row.get("distance_m").getAsDouble();final VerifiedAssetPcm pcm;
        try {pcm=CalibratedAssetDecoder.decode(calibration(encoded,decoded,coefficient),encoded);}catch(IOException error){throw new IllegalStateException(error);}
        row.addProperty("coefficient",coefficient);row.addProperty("actual_calibration_reference_amplitude",pcm.calibration().referenceAmplitude());row.addProperty("calibration_reference_distance_m",1);
        row.addProperty("decoded_reference_peak",decodedPeak(decoded));row.addProperty("analytic_unattenuated_twelve_stream_peak_bound",12*decodedPeak(decoded)*coefficient);
        row.addProperty("analytic_inverse_clamped_aggregate_peak_bound",12*decodedPeak(decoded)*coefficient/Math.max(1,distance));row.addProperty("aggregate_bound_is_diagnostic_only",true);row.addProperty("capacity_headroom_passed",false);
        AtomicReference<CalibratedAssetResources.Catalog> installed=new AtomicReference<>();
        context.runOnClient(client->{
            if(!client.isSameThread() || !probes.isEmpty() || !activeSets().isEmpty() || !handles(client).isEmpty())throw new IllegalStateException("capacity admission before empty actual client state");
            for(var id:protocol.getAsJsonArray("entity_ids")){ProbeDrone drone=new ProbeDrone(client.level,id.getAsInt());drone.setPos(listener.x(),listener.y()-drone.getBbHeight()*.5,listener.z()-distance);probes.add(drone);}
            Map<CalibratedAssetResources.BindingKey,VerifiedAssetPcm> bindings=new HashMap<>();for(ProbeDrone drone:probes)for(int i=0;i<2;i++)bindings.put(new CalibratedAssetResources.BindingKey(AcousticProfileResources.keyOf(drone),DroneLoopSoundInstance.Layer.values()[i].name(),NORMAL_RESOURCES.get(i)),pcm);
            var catalog=new CalibratedAssetResources.Catalog(bindings);installed.set(catalog);JsonObject applied=new JsonObject();applied.addProperty("before_catalog_identity",System.identityHashCode(CalibratedAssetResources.snapshot()));applied.addProperty("next_catalog_identity",System.identityHashCode(catalog));applied.addProperty("asset_identity",System.identityHashCode(pcm));applied.addProperty("actual_client_thread",true);applied.addProperty("thread",Thread.currentThread().getName());applied.addProperty("production_method","CalibratedAssetResources.apply");
            new CalibratedAssetResources().apply(catalog,client.getResourceManager(),Runnable::run).join();applied.addProperty("apply_future_joined",true);applied.addProperty("after_catalog_identity",System.identityHashCode(CalibratedAssetResources.snapshot()));row.add("catalog_apply",applied);if(CalibratedAssetResources.snapshot()!=catalog)throw new IllegalStateException("actual capacity catalog publication failed");
            row.addProperty("catalog_identity",System.identityHashCode(catalog));row.addProperty("asset_identity",System.identityHashCode(pcm));JsonArray entities=new JsonArray();for(ProbeDrone drone:probes){JsonObject e=new JsonObject();e.addProperty("entity_id",drone.getId());e.addProperty("entity_identity",System.identityHashCode(drone));e.addProperty("profile_key",AcousticProfileResources.keyOf(drone).toString());entities.add(e);}row.add("fixture_entities",entities);
            WetSoundAdmissionCapture.live();for(ProbeDrone drone:probes)client.level.addEntity(drone);
        });
        context.waitFor(client->selectedReady(client,installed.get(),pcm),WAIT);onSoundThread(context,()->null);
        AtomicReference<List<Voice>> captured=new AtomicReference<>();context.runOnClient(client->{List<Voice> voices=discoverSelected(client,installed.get(),pcm);captured.set(voices);row.add("membership_before_render",membership(client));recordOptions(client,row);JsonArray bindings=new JsonArray();for(int i=0;i<voices.size();i++)bindings.add(voiceBinding(voices.get(i),i));row.add("voices",bindings);});
        List<Voice> voices=captured.get();setPhase(phase+":STATIC_RENDER");
        onSoundThread(context,()->{
            identity(row);row.addProperty("version",AL10.alGetString(AL10.AL_VERSION));JsonArray before=nativeVoices(voices,distance);row.add("native_before",before);
            for(var value:before)if(value.getAsJsonObject().get("sample_offset").getAsInt()!=0)throw new IllegalStateException("fresh coherent source sample offset is not zero before first render");
            row.addProperty("all_initial_source_sample_offsets_zero",true);row.addProperty("output_offset_bytes",output.size());JsonArray blocks=new JsonArray();row.add("render_blocks",blocks);int start=output.size();
            try {
                row.addProperty("settle_output_offset_bytes",output.size());renderWindow(row,voices,distance,"SETTLE",SETTLE,blocks,start);row.addProperty("settle_output_bytes",SETTLE*4);row.add("native_after_settle",nativeVoices(voices,distance));
                row.addProperty("steady_output_offset_bytes",output.size());renderWindow(row,voices,distance,"STEADY",STEADY,blocks,start);row.addProperty("steady_output_bytes",STEADY*4);row.add("native_after_steady",nativeVoices(voices,distance));
            } finally {byte[] whole=Arrays.copyOfRange(output.toByteArray(),start,output.size());row.addProperty("output_bytes",whole.length);row.addProperty("output_sha256",sha(whole));row.addProperty("actual_output_frames",whole.length/4);}
            return null;
        });
        context.runOnClient(client->{requireRetained(client,voices,installed.get(),pcm);row.add("membership_after_render",membership(client));row.add("voices_after_render",voiceState(voices));});
        naturalRetire(context,row,voices,phase);cleanup(context,phase+":after_natural_retirement");
        onSoundThread(context,()->{sealInputs(row,voices);return null;});context.runOnClient(client->{row.addProperty("handles_after_cleanup",handles(client).size());row.addProperty("manager_sets_after_cleanup",activeSets().size());});
    }

    private static boolean selectedReady(Minecraft client,CalibratedAssetResources.Catalog catalog,VerifiedAssetPcm pcm) {
        if(activeSets().size()!=6 || handles(client).size()!=12)return false;
        for(ProbeDrone drone:probes) {
            Object set=activeSets().get(drone.getId());if(set==null || field(set,"drone")!=drone || client.level.getEntity(drone.getId())!=drone)return false;
            for(String layer:List.of("motor","propeller")) {
                Object value=field(set,layer);if(!(value instanceof DroneLoopSoundInstance sound) || sound.getVolume()!=.99999994f || sound.getPitch()!=1 || sound.usesProceduralAudio() || field(sound,"assetCatalog")!=catalog || field(sound,"calibratedAsset")!=pcm)return false;
                var handle=handles(client).get(sound);Channel channel=handle==null?null:((ChannelHandleChannelAccessor)handle).fpvdrone$getChannel();if(channel==null || !(((MainOutputChannelAccessor)channel).mainOutput$getStream() instanceof CalibratedAssetAudioStream))return false;
            }
        }return true;
    }
    private static List<Voice> discoverSelected(Minecraft client,CalibratedAssetResources.Catalog catalog,VerifiedAssetPcm pcm) {
        if(!selectedReady(client,catalog,pcm))throw new IllegalStateException("six normal manager sets/twelve steady asset voices not ready");
        List<Voice> voices=new ArrayList<>();Set<DroneLoopSoundInstance> sounds=Collections.newSetFromMap(new IdentityHashMap<>());Set<Channel> channels=Collections.newSetFromMap(new IdentityHashMap<>());Set<CalibratedAssetAudioStream> streams=Collections.newSetFromMap(new IdentityHashMap<>());
        for(ProbeDrone drone:probes) {Object set=activeSets().get(drone.getId());for(String layer:List.of("motor","propeller")) {
            DroneLoopSoundInstance sound=(DroneLoopSoundInstance)field(set,layer);Channel channel=((ChannelHandleChannelAccessor)handles(client).get(sound)).fpvdrone$getChannel();var stream=(CalibratedAssetAudioStream)((MainOutputChannelAccessor)channel).mainOutput$getStream();
            if(!sounds.add(sound)||!channels.add(channel)||!streams.add(stream)||!stream.distanceOwnerVerified())throw new IllegalStateException("selected normal asset voices are not distinct or lack verified ownership");voices.add(new Voice(drone,set,sound,channel,stream));
        }}return List.copyOf(voices);
    }
    private static JsonObject voiceBinding(Voice voice,int index) {
        JsonObject row=new JsonObject();join(row,voice,index);row.addProperty("resource",String.valueOf(field(voice.sound,"calibratedResource")));row.addProperty("procedural_backend",voice.sound.usesProceduralAudio());row.addProperty("owner_verified",voice.stream.distanceOwnerVerified());row.addProperty("raw_envelope",(Float)field(voice.sound,"volume"));row.addProperty("pitch",voice.sound.getPitch());row.addProperty("coefficient",(Double)field(voice.sound,"assetAmplitude"));row.addProperty("input_record_identity",MainContextOutputCapture.identity(voice.stream));row.addProperty("streaming_buffer_bytes",((ChannelStreamingBufferAccessor)voice.channel).fpvdrone$getStreamingBufferSize());
        var format=voice.stream.getFormat();JsonObject f=new JsonObject();f.addProperty("sample_rate",format.getSampleRate());f.addProperty("sample_size_bits",format.getSampleSizeInBits());f.addProperty("channels",format.getChannels());f.addProperty("frame_size",format.getFrameSize());f.addProperty("big_endian",format.isBigEndian());f.addProperty("encoding",format.getEncoding().toString());row.add("format",f);return row;
    }
    private static void join(JsonObject row,Voice voice,int index) {
        row.addProperty("voice_index",index);row.addProperty("entity_id",voice.drone.getId());row.addProperty("entity_identity",System.identityHashCode(voice.drone));row.addProperty("set_identity",System.identityHashCode(voice.set));row.addProperty("sound_identity",System.identityHashCode(voice.sound));row.addProperty("layer",voice.sound.layer().name());row.addProperty("catalog_identity",System.identityHashCode(field(voice.sound,"assetCatalog")));row.addProperty("asset_identity",System.identityHashCode(field(voice.sound,"calibratedAsset")));row.addProperty("channel_identity",System.identityHashCode(voice.channel));row.addProperty("channel_allocation_identity",WetChannelLifecycleCapture.identity(voice.channel));row.addProperty("stream_identity",System.identityHashCode(voice.stream));
    }
    private static JsonArray nativeVoices(List<Voice> voices,double expectedDistance) {
        JsonArray rows=new JsonArray();for(int i=0;i<voices.size();i++) {
            Voice voice=voices.get(i);JsonObject row=ManagerAssetResumptionCapture.nativeSnapshot(voice.channel);join(row,voice,i);
            if(!row.get("source_valid").getAsBoolean() || row.get("source_state").getAsInt()!=AL10.AL_PLAYING || row.get("queued").getAsInt()<1 || !voice.stream.distanceOwnerVerified())throw new IllegalStateException("selected capacity source is invalid, stopped, empty or unowned");
            int source=row.get("source").getAsInt();row.addProperty("relative",AL10.alGetSourcei(source,AL10.AL_SOURCE_RELATIVE)!=0);row.addProperty("minimum_gain",AL10.alGetSourcef(source,AL10.AL_MIN_GAIN));row.addProperty("maximum_gain",AL10.alGetSourcef(source,AL10.AL_MAX_GAIN));
            row.addProperty("raw_envelope",(Float)field(voice.sound,"volume"));row.addProperty("coefficient",(Double)field(voice.sound,"assetAmplitude"));row.addProperty("owner_verified",voice.stream.distanceOwnerVerified());
            var p=row.getAsJsonArray("position");var l=row.getAsJsonArray("listener_position");double x=p.get(0).getAsDouble()-l.get(0).getAsDouble(),y=p.get(1).getAsDouble()-l.get(1).getAsDouble(),z=p.get(2).getAsDouble()-l.get(2).getAsDouble();double distance=Math.sqrt(x*x+y*y+z*z);row.addProperty("actual_source_listener_distance_m",distance);
            if(Math.abs(distance-expectedDistance)>1e-5)throw new IllegalStateException("capacity native geometry differs from declared source pose");row.addProperty("al_error_extra",AL10.alGetError());if(row.get("al_error_extra").getAsInt()!=0)throw new IllegalStateException("capacity extra native getters failed");rows.add(row);
        }return rows;
    }
    private static void renderWindow(JsonObject row,List<Voice> voices,double distance,String phase,int frames,JsonArray blocks,int caseStart) {
        var library=library();MainContextOutputCapture.requireCurrent(library.mainOutput$getDevice(),library.mainOutput$getContext());int windowStart=output.size();
        for(int f=0;f<frames;f+=1024) {
            int count=Math.min(1024,frames-f);JsonObject block=new JsonObject();block.addProperty("block_index",blocks.size());block.addProperty("phase",phase);block.addProperty("phase_frame_offset",f);block.addProperty("frames",count);block.addProperty("case_frame_offset",(output.size()-caseStart)/4);block.addProperty("output_offset_bytes",output.size());block.addProperty("case_output_offset_bytes",output.size()-caseStart);block.addProperty("owner_thread",Thread.currentThread().getName());identity(block);block.add("native_before",nativeVoices(voices,distance));
            FloatBuffer samples=BufferUtils.createFloatBuffer(count);SOFTLoopback.alcRenderSamplesSOFT(library.mainOutput$getDevice(),samples,count);if(AL10.alGetError()!=0||ALC10.alcGetError(library.mainOutput$getDevice())!=0)throw new IllegalStateException("capacity native render failed");
            ByteBuffer pcm=ByteBuffer.allocate(count*4).order(ByteOrder.LITTLE_ENDIAN);while(samples.hasRemaining())pcm.putFloat(samples.get());byte[] bytes=pcm.array();output.writeBytes(bytes);block.addProperty("output_bytes",bytes.length);block.addProperty("output_sha256",sha(bytes));
            for(Voice voice:voices){voice.channel.updateStream();int source=((ChannelSourceAccessor)voice.channel).fpvdrone$getSource();if(AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE)!=AL10.AL_PLAYING||AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED)<1)throw new IllegalStateException("capacity asset queue underrun");}
            block.add("native_after",nativeVoices(voices,distance));block.addProperty("queue_service_calls",voices.size());block.addProperty("al_error_after_service",AL10.alGetError());if(block.get("al_error_after_service").getAsInt()!=0)throw new IllegalStateException("capacity queue service getters failed");blocks.add(block);
        }if(output.size()-windowStart!=frames*4)throw new IllegalStateException("capacity render window byte count differs");MainContextOutputCapture.requireCurrent(library.mainOutput$getDevice(),library.mainOutput$getContext());
    }
    private static void requireRetained(Minecraft client,List<Voice> voices,CalibratedAssetResources.Catalog catalog,VerifiedAssetPcm pcm) {
        if(!selectedReady(client,catalog,pcm))throw new IllegalStateException("normal capacity voices changed during owner render");for(Voice voice:voices)if(activeSets().get(voice.drone.getId())!=voice.set || field(voice.set,voice.sound.layer()==DroneLoopSoundInstance.Layer.MOTOR?"motor":"propeller")!=voice.sound || ((ChannelHandleChannelAccessor)handles(client).get(voice.sound)).fpvdrone$getChannel()!=voice.channel || ((MainOutputChannelAccessor)voice.channel).mainOutput$getStream()!=voice.stream)throw new IllegalStateException("capacity set/sound/Channel/stream identity changed");
    }
    private static JsonArray voiceState(List<Voice> voices) {JsonArray rows=new JsonArray();for(int i=0;i<voices.size();i++){Voice voice=voices.get(i);JsonObject row=new JsonObject();join(row,voice,i);row.addProperty("raw_envelope",(Float)field(voice.sound,"volume"));row.addProperty("pitch",voice.sound.getPitch());row.addProperty("coefficient",(Double)field(voice.sound,"assetAmplitude"));row.addProperty("rpm",voice.drone.getAverageMotorRpm());row.addProperty("stopped",voice.sound.isStopped());row.addProperty("entity_alive",voice.drone.isAlive());row.addProperty("entity_removed",voice.drone.isRemoved());rows.add(row);}return rows;}
    private static void naturalRetire(ClientGameTestContext context,JsonObject row,List<Voice> voices,String phase) {
        setPhase(phase+":RPM_ZERO_RELEASE");context.runOnClient(client->{row.add("release_membership_before",membership(client));row.add("release_voices_before",voiceState(voices));for(ProbeDrone drone:probes)drone.rpm=0;});
        JsonArray polls=new JsonArray();row.add("release_poll_observations",polls);context.waitFor(client->{JsonObject poll=membership(client);poll.addProperty("poll_index",polls.size());poll.addProperty("client_game_time",client.level==null?-1:client.level.getGameTime());poll.add("voices",voiceState(voices));polls.add(poll);return activeSets().isEmpty()&&handles(client).isEmpty();},WAIT);onSoundThread(context,()->null);
        context.runOnClient(client->{row.add("release_membership_after",membership(client));row.add("release_voices_after",voiceState(voices));for(Voice voice:voices)if((Float)field(voice.sound,"volume")!=0 || voice.drone.getAverageMotorRpm()!=0)throw new IllegalStateException("natural retirement did not end at raw envelope/RPM zero");});row.addProperty("natural_rpm_zero_retirement_complete",true);
    }
    private static void sealInputs(JsonObject row,List<Voice> voices) {
        JsonArray bindings=row.getAsJsonArray("voices");for(int i=0;i<voices.size();i++) {Voice voice=voices.get(i);JsonObject binding=bindings.get(i).getAsJsonObject();boolean closed=AssetNativeCapture.closed(voice.stream);binding.addProperty("stream_closed_after_cleanup",closed);if(!closed)throw new IllegalStateException("capacity selected stream not closed after natural retirement");byte[] data=MainContextOutputCapture.input(voice.stream);binding.addProperty("input_offset_bytes",input.size());binding.addProperty("input_bytes",data.length);binding.addProperty("input_sha256",sha(data));binding.add("asset_read_batches",AssetNativeCapture.reads(voice.stream));binding.add("native_after_cleanup",ManagerAssetResumptionCapture.nativeSnapshot(voice.channel));input.writeBytes(data);}row.addProperty("inputs_sealed_after_cleanup",true);row.addProperty("library_debug_after_cleanup",((SoundEngineMainOutputAccessor)engine(Minecraft.getInstance())).mainOutput$getLibrary().getDebugString());
    }
    private static JsonObject membership(Minecraft client) {
        JsonObject row=new JsonObject();row.addProperty("manager_set_count",activeSets().size());row.addProperty("actual_handle_count",handles(client).size());int wet=0;for(SoundInstance sound:handles(client).keySet())if(sound instanceof ListenerReverbSoundInstance)wet++;row.addProperty("wet_handle_count",wet);JsonArray entities=new JsonArray(),sets=new JsonArray();if(client.level!=null)for(Entity e:client.level.entitiesForRendering())if(e instanceof DroneEntity)entities.add(e.getId());row.add("world_drone_ids",entities);
        for(ProbeDrone drone:probes){JsonObject e=new JsonObject();e.addProperty("entity_id",drone.getId());e.addProperty("expected_entity_identity",System.identityHashCode(drone));e.addProperty("world_entity_identity",client.level==null?0:System.identityHashCode(client.level.getEntity(drone.getId())));Object set=activeSets().get(drone.getId());e.addProperty("manager_set_identity",System.identityHashCode(set));e.addProperty("manager_entity_identity",set==null?0:System.identityHashCode(field(set,"drone")));e.addProperty("rpm",drone.getAverageMotorRpm());sets.add(e);}row.add("entities",sets);return row;
    }
    private static void cleanup(ClientGameTestContext context,String reason) {
        context.runOnClient(client->{WetSoundAdmissionCapture.close(reason);if(client.level!=null)for(ProbeDrone drone:probes)client.level.removeEntity(drone.getId(),Entity.RemovalReason.DISCARDED);probes.clear();try{Method stop=DroneSoundManager.class.getDeclaredMethod("stopAll",Minecraft.class);stop.setAccessible(true);stop.invoke(null,client);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}});
        context.waitFor(client->WetSoundAdmissionCapture.drained(),WAIT);onSoundThread(context,()->null);onSoundThread(context,()->{WetSoundAdmissionCapture.reclaimUnattached();return null;});context.runOnClient(client->engine(client).stopAll());onSoundThread(context,()->null);context.runOnClient(client->WetSoundAdmissionCapture.barrier(handles(client).size()));
    }
    private static void recordOptions(Minecraft client,JsonObject row) {row.addProperty("options_master",client.options.getSoundSourceVolume(SoundSource.MASTER));row.addProperty("options_category",client.options.getSoundSourceVolume(SoundSource.NEUTRAL));row.addProperty("gain_by_source_neutral",((SoundEngineMainOutputAccessor)engine(client)).mainOutput$getGains().getFloat(SoundSource.NEUTRAL));row.addProperty("library_debug",((SoundEngineMainOutputAccessor)engine(client)).mainOutput$getLibrary().getDebugString());}
    private static AcousticVector awaitNativeListener(ClientGameTestContext context,String name) {
        JsonObject group=new JsonObject();JsonArray attempts=new JsonArray();group.addProperty("name",name);group.add("attempts",attempts);listenerReadiness.add(group);
        for(int attempt=0;attempt<WAIT;attempt++) {context.waitTick();AtomicReference<AcousticCameraListener.Sample> sample=new AtomicReference<>();context.runOnClient(client->sample.set(((AcousticCameraListener)field(DroneSoundManager.class,"CAMERA_LISTENER")).latestFor(client.level)));if(sample.get()==null)continue;var expected=sample.get();ListenerReadback readback=onSoundThread(context,()->{
            FloatBuffer buffer=BufferUtils.createFloatBuffer(3);AL10.alGetListenerfv(AL10.AL_POSITION,buffer);AcousticVector position=new AcousticVector(buffer.get(0),buffer.get(1),buffer.get(2));var transform=engine(Minecraft.getInstance()).getListenerTransform().position();JsonObject nativeRow=new JsonObject();identity(nativeRow);nativeRow.add("camera_position",vector(expected.frame().positionMeters()));nativeRow.add("native_position",vector(position));nativeRow.addProperty("ready",position.subtract(expected.frame().positionMeters()).length()<=1e-5&&new AcousticVector(transform.x,transform.y,transform.z).subtract(expected.frame().positionMeters()).length()<=1e-5);nativeRow.addProperty("al_error",AL10.alGetError());if(nativeRow.get("al_error").getAsInt()!=0)throw new IllegalStateException("capacity listener getter failed");return new ListenerReadback(position,nativeRow);
        });readback.observation().addProperty("attempt",attempt);attempts.add(readback.observation());if(readback.observation().get("ready").getAsBoolean()){group.addProperty("ready",true);return readback.position();}}throw new IllegalStateException("capacity native listener did not acknowledge camera pose");
    }
    private static AudioAssetCalibration calibration(byte[] encoded,byte[] decoded,double coefficient) {double squares=0;var shorts=ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();while(shorts.hasRemaining()){double x=shorts.get()/32768.0;squares+=x*x;}double rms=Math.sqrt(squares/(decoded.length/2));return new AudioAssetCalibration(sha(encoded),sha(decoded),48000,decoded.length/2,0,decoded.length/2,rms,rms*coefficient,1,12000,0,List.of(8000.0,12000.0,16000.0),List.of(-1.0,0.0,1.0),List.of(List.of(0.0,0.0,0.0),List.of(0.0,0.0,0.0),List.of(0.0,0.0,0.0)));}
    private static double decodedPeak(byte[] decoded) {double peak=0;var samples=ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();while(samples.hasRemaining())peak=Math.max(peak,Math.abs(samples.get()/32768.0));return peak;}
    private static byte[] decodeReference(byte[] encoded) {try(var decoder=new JOrbisAudioStream(new ByteArrayInputStream(encoded))){ByteArrayOutputStream out=new ByteArrayOutputStream();while(true){ByteBuffer chunk=decoder.read(8192);if(!chunk.hasRemaining())break;byte[] bytes=new byte[chunk.remaining()];chunk.get(bytes);out.writeBytes(bytes);}return out.toByteArray();}catch(IOException error){throw new IllegalStateException(error);}}
    private static void setPhase(String name){AssetNativeCapture.phase(name);CalibratedGainCapture.phase(name);}
    private static Object field(Object target,String name){return ManagerAssetResumptionCapture.field(target,name);}
    private static Map<Integer,Object> activeSets(){return ManagerAssetResumptionCapture.activeSets();}
    private static SoundEngine engine(Minecraft client){return ((SoundManagerEngineAccessor)client.getSoundManager()).fpvdrone$getSoundEngine();}
    private static Map<SoundInstance,ChannelAccess.ChannelHandle> handles(Minecraft client){return ((SoundEngineChannelsAccessor)engine(client)).fpvdrone$getInstanceToChannel();}
    private static MainOutputLibraryAccessor library(){return (MainOutputLibraryAccessor)((SoundEngineMainOutputAccessor)engine(Minecraft.getInstance())).mainOutput$getLibrary();}
    private static <T> T onSoundThread(ClientGameTestContext context,Supplier<T> action){CompletableFuture<T> done=new CompletableFuture<>();context.runOnClient(client->((SoundEngineExecutorAccessor)engine(client)).fpvdrone$getExecutor().schedule(()->{try{done.complete(action.get());}catch(Throwable error){done.completeExceptionally(error);}}));context.waitFor(client->done.isDone(),WAIT);return done.join();}
    private static void identity(JsonObject row){var library=library();row.addProperty("device",library.mainOutput$getDevice());row.addProperty("context",library.mainOutput$getContext());row.addProperty("generation",MainContextOutputCapture.generation(library.mainOutput$getDevice(),library.mainOutput$getContext()));}
    private static JsonArray vector(AcousticVector position){JsonArray values=new JsonArray();values.add(position.x());values.add(position.y());values.add(position.z());return values;}
    private static byte[] bytes(Path path){try{return Files.readAllBytes(path);}catch(IOException error){throw new IllegalStateException(error);}}
    private static String sha(byte[] bytes){return MainContextOutputCapture.sha(bytes);}
    private static void writePcm(Path root,JsonObject protocol,JsonObject report,String name,byte[] bytes){try{Files.write(root.resolve(protocol.get(name+"_pcm_file").getAsString()),bytes,StandardOpenOption.CREATE_NEW);report.addProperty(name+"_pcm_bytes",bytes.length);report.addProperty(name+"_pcm_sha256",sha(bytes));}catch(IOException error){throw new IllegalStateException(error);}}
}
