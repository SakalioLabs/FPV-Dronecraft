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

/** D188 generated-asset release-clock control; no native output headroom conclusion is made here. */
final class ManagerAssetReleaseClockClientGameTest {
    private static final int WAIT=600,SETTLE=48000,STEADY=48000,TRANSITION_STEPS=40,STEP_FRAMES=2400,CHUNK=1024,CASE_FRAMES=192000;
    private static final int RELEASE_STEPS=60,FINAL_EMPTY_FRAMES=48000;
    private static final double INPUT_BUDGET=.9495;
    private static final String GENERATED_ASSET="fabric-mod/src/gametest/resources/assets/fpvdrone_gain_control/sounds/calibrated_160.ogg";
    private static final List<Identifier> NORMAL_RESOURCES=List.of(Identifier.parse("fpvdrone:sounds/drone/motor_loop.ogg"),Identifier.parse("fpvdrone:sounds/drone/propeller_loop.ogg"));
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
    private record Voice(ProbeDrone drone,Object set,DroneLoopSoundInstance sound,Channel channel,CalibratedAssetAudioStream stream,int nativeSource) { }
    private record ListenerReadback(AcousticVector position,JsonObject observation) { }

    static boolean requested() {
        if(!AssetNativeCapture.requested())return false;JsonObject p=MainContextOutputCapture.protocol();
        return p.has("manager_asset_release_clock_experiment")&&p.get("manager_asset_release_clock_experiment").getAsBoolean();
    }

    static void run(ClientGameTestContext context) {
        JsonObject protocol=MainContextOutputCapture.protocol(),report=new JsonObject(),controls=new JsonObject();
        Path root=Path.of(protocol.get("workspace_root").getAsString());
        JsonArray rows=new JsonArray();Throwable failure=null;
        EnumMap<SoundSource,Double> options=new EnumMap<>(SoundSource.class);
        var originalCatalog=CalibratedAssetResources.snapshot();
        report.addProperty("started_utc",Instant.now().toString());
        report.addProperty("protocol_sha256",sha(bytes(Path.of(System.getProperty(AssetNativeCapture.PROPERTY)))));
        report.add("declaration",protocol.deepCopy());report.add("evidence_sha256",protocol.get("evidence_sha256").deepCopy());
        report.addProperty("encoded_reference_file",GENERATED_ASSET);
        report.addProperty("case_frames",CASE_FRAMES);report.addProperty("capture_family","D188");report.addProperty("declared_case_count",2);report.addProperty("decision_id","D188");report.addProperty("arm",protocol.get("arm").getAsString());
        try {
            validateProtocol(protocol);
            if(!CalibratedGainCapture.requested() || !Objects.equals(System.getProperty(MainContextOutputCapture.PROPERTY),System.getProperty(AssetNativeCapture.PROPERTY)) || !Objects.equals(System.getProperty(CalibratedGainCapture.PROPERTY),System.getProperty(AssetNativeCapture.PROPERTY)))
                throw new IllegalStateException("D188 release clock requires all three capture properties bound to the same declaration");
            JsonObject loaded=new JsonObject();
            for(var value:protocol.getAsJsonArray("required_loaded_class_paths")) {
                String path=value.getAsString(),resource="/"+path.substring(path.indexOf("com/tenicana/"));
                try(var stream=ManagerAssetReleaseClockClientGameTest.class.getResourceAsStream(resource)) {
                    if(stream==null)throw new IllegalStateException("loaded class absent "+resource);
                    loaded.addProperty(path,sha(stream.readAllBytes()));
                }
            }
            report.add("loaded_class_sha256",loaded);
            byte[] encoded=bytes(root.resolve(GENERATED_ASSET)),decoded=decodeReference(encoded);
            if(decoded.length!=48000*2)throw new IllegalStateException("generated reference must contain exactly 48000 mono S16 frames");
            writePcm(root,protocol,report,"decoded_reference",decoded);
            report.addProperty("decoded_reference_file",protocol.get("decoded_reference_pcm_file").getAsString());
            report.addProperty("encoded_reference_sha256",sha(encoded));report.addProperty("decoded_reference_sha256",sha(decoded));
            report.addProperty("decoded_reference_frames",decoded.length/2);report.addProperty("decoded_reference_max_absolute_sample",decodedMaximumAbsoluteSample(decoded));
            for(var value:protocol.getAsJsonArray("cases")) {
                JsonObject row=value.getAsJsonObject().deepCopy();row.addProperty("case_index",rows.size());row.addProperty("status","not_started");rows.add(row);
            }
            context.runOnClient(client->{
                for(SoundSource source:SoundSource.values()) {
                    options.put(source,client.options.getSoundSourceOptionInstance(source).get());
                    client.options.getSoundSourceOptionInstance(source).set(source==SoundSource.MASTER||source==SoundSource.NEUTRAL?1.0:0.0);
                }
                client.getSoundManager().updateCategoryVolume(SoundSource.NEUTRAL,1);
            });
            try(TestSingleplayerContext world=context.worldBuilder().create()) {
                try {
                    world.getClientWorld().waitForChunksDownload();world.getServer().runCommand("gamerule spawn_mobs false");cleanup(context,"world_ready");
                    for(var value:rows) {
                        JsonObject row=value.getAsJsonObject();row.addProperty("status","capturing");
                        try {
                            AcousticVector listener=awaitNativeListener(context,row.get("name").getAsString());
                            captureCase(context,protocol,row,listener,encoded,decoded);row.addProperty("status","captured");
                        } catch(Throwable error) {row.addProperty("status","failed");row.addProperty("failure",error.toString());throw error;}
                    }
                    context.runOnClient(client->controls.add("all_cases_cleanup",membership(client)));
                    captureFinalEmpty(context,report);
                } finally {cleanup(context,"before_world_close");}
            }
            if(output.size()!=protocol.get("total_output_frames").getAsInt()*4)throw new IllegalStateException("D188 total output count differs from declaration");
            cleanup(context,"final");context.runOnClient(client->client.getSoundManager().destroy());
            report.addProperty("status","manager_asset_release_clock_captured");
        } catch(Throwable error) {
            failure=error;report.addProperty("status","manager_asset_release_clock_failed");report.addProperty("failure",error.toString());
            try {cleanup(context,"failure");context.runOnClient(client->client.getSoundManager().destroy());}
            catch(Throwable cleanup){report.addProperty("cleanup_failure",cleanup.toString());}
        } finally {
            try {context.runOnClient(client->{
                if(!activeSets().isEmpty() || !handles(client).isEmpty())throw new IllegalStateException("catalog restoration before empty sweep cleanup");
                JsonObject event=new JsonObject();event.addProperty("before_catalog_identity",System.identityHashCode(CalibratedAssetResources.snapshot()));
                new CalibratedAssetResources().apply(originalCatalog,client.getResourceManager(),Runnable::run).join();
                event.addProperty("original_catalog_identity",System.identityHashCode(originalCatalog));event.addProperty("after_catalog_identity",System.identityHashCode(CalibratedAssetResources.snapshot()));
                event.addProperty("actual_client_thread",client.isSameThread());event.addProperty("thread",Thread.currentThread().getName());controls.add("original_catalog_restoration",event);
                options.forEach((source,value)->client.options.getSoundSourceOptionInstance(source).set(value));
            });} catch(Throwable restore) {
                report.addProperty("restoration_failure",restore.toString());report.addProperty("status","manager_asset_release_clock_failed");if(failure==null)failure=restore;
            }
        }
        report.add("rows",rows);report.add("controls",controls);report.add("listener_readiness",listenerReadiness);
        report.add("pitch_setters",PitchHeadroomCapture.snapshot());report.add("gain_observations",AssetNativeCapture.snapshot());report.add("envelope_observations",CalibratedGainCapture.snapshot());
        report.add("actual_tick_target_observations",PitchHeadroomCapture.tickSnapshot());
        report.add("epochs",MainContextOutputCapture.epochs());report.add("runtime_events",DistanceOwnershipRuntimeTrace.snapshot());
        report.add("channel_allocations",WetChannelLifecycleCapture.allocations());report.add("channel_calls",WetChannelLifecycleCapture.calls());report.add("sound_admission",WetSoundAdmissionCapture.snapshot());
        JsonObject scope=new JsonObject();
        for(String name:List.of("actual_Minecraft_client_started","actual_minecraft_main_context_output","actual_client_world_entities","actual_production_manager_selection","normal_resolved_asset_keys","test_constructed_numeric_catalog","actual_production_catalog_apply_publication","software_generated_asset_only","decoded_reference_shared_JOrbis_decoder","actual_twelve_asset_streams_per_case","complete_settle_transition_steady_release_and_final_empty_output_retained","test_only_sound_owner_queue_service","natural_rpm_zero_cleanup_stimulus","bounded_release_clock_control","default_native_resampler_observed","native_pitch_and_gain_set_by_production_ticks_only","float_output_above_one_retained","fixed_sixty_release_observations_in_both_arms","no_empty_render_between_prior_and_fresh_case"))scope.addProperty(name,true);
        for(String name:List.of("native_output_peak_contract_passed","causal_contribution_proven","ordinary_device_timing_or_clipping_verified","all_D187_failures_resolved","arbitrary_high_frequency_or_complex_asset_resampling_bounded","arbitrary_PCM_peak_bound","full_wet_mix_qualified","procedural_mix_qualified","full_mix_qualified","physical_asset_qualification","measured_five_inch_source_verified","real_pressure_or_rpm_accessed","physical_endpoint_verified","release_wave_contract_verified","native_zero_gain_at_stop_boundary_verified","queued_sample_playback_tick_alignment_verified","ordinary_realtime_playback_verified","hardware_mixer_sample_atomicity_verified","each_transition_step_is_exactly_one_minecraft_tick","full_goal_complete"))scope.addProperty(name,false);
        scope.addProperty("asset_scope","existing software-generated D160 calibrated_160.ogg only");report.add("scope",scope);
        report.addProperty("actual_output_frames",output.size()/4);int captured=0;for(var value:rows)if(value.getAsJsonObject().get("status").getAsString().equals("captured"))captured++;
        report.addProperty("captured_case_count",captured);report.addProperty("all_declared_rows_retained",rows.size()==2);
        for(String name:List.of("input","output")) {
            try {writePcm(root,protocol,report,name,name.equals("input")?input.toByteArray():output.toByteArray());}
            catch(Throwable error) {report.addProperty(name+"_artifact_failure",error.toString());report.addProperty("status","manager_asset_release_clock_failed");if(failure==null)failure=error;}
        }
        report.addProperty("finished_utc",Instant.now().toString());
        try {Files.writeString(root.resolve(protocol.get("output_report").getAsString()),new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report)+"\n",StandardOpenOption.CREATE_NEW);}
        catch(IOException error){throw new IllegalStateException("cannot create D188 release clock report",error);}
        if(failure!=null)throw new IllegalStateException("D188 actual manager asset release clock capture failed",failure);
    }

    private static void validateProtocol(JsonObject p) {
        String arm=p.get("arm").getAsString();
        if(!Set.of("PAUSED","CONTINUOUS").contains(arm))throw new IllegalArgumentException("D188 unknown clock arm");
        int releaseFrames=arm.equals("PAUSED")?0:STEP_FRAMES,totalFrames=arm.equals("PAUSED")?432000:720000;
        if(!p.get("manager_asset_release_clock_experiment").getAsBoolean() || !p.get("manager_asset_pitch_headroom_experiment").getAsBoolean()
                || p.get("declared_case_count").getAsInt()!=2 || !p.get("coefficient_policy").getAsString().equals("NEAR_BUDGET")
                || p.get("settle_frames").getAsInt()!=SETTLE || p.get("steady_frames").getAsInt()!=STEADY
                || p.get("transition_ticks").getAsInt()!=TRANSITION_STEPS || p.get("transition_step_frames").getAsInt()!=STEP_FRAMES
                || p.get("render_chunk_frames").getAsInt()!=CHUNK || p.get("release_observation_steps").getAsInt()!=RELEASE_STEPS
                || p.get("release_frames_per_step").getAsInt()!=releaseFrames || p.get("final_empty_probe_frames").getAsInt()!=FINAL_EMPTY_FRAMES
                || p.get("total_output_frames").getAsInt()!=totalFrames || Double.compare(p.get("target_input_peak_budget").getAsDouble(),INPUT_BUDGET)!=0)
            throw new IllegalArgumentException("D188 predeclared clock/input budget differs");
        JsonArray ids=p.getAsJsonArray("entity_ids"),cases=p.getAsJsonArray("cases");
        if(ids.size()!=6 || cases.size()!=2)throw new IllegalArgumentException("D188 requires six entities and exactly two ordered cases");
        for(int i=0;i<6;i++)if(ids.get(i).getAsInt()!=188001+i)throw new IllegalArgumentException("wrong D188 entity id");
        for(int i=0;i<2;i++) {
            JsonObject row=cases.get(i).getAsJsonObject();String expectedName=i==0?"PRIOR_9000":"FRESH_12000";int expectedRpm=i==0?9000:12000;
            if(!row.get("name").getAsString().equals(expectedName) || !row.get("coefficient_policy").getAsString().equals("NEAR_BUDGET"))throw new IllegalArgumentException("D188 case order/policy differs");
            JsonArray rpm=row.getAsJsonArray("rpm_targets");if(rpm.size()!=6)throw new IllegalArgumentException("D188 needs six RPM targets per case");
            for(var target:rpm)if(Double.compare(target.getAsDouble(),expectedRpm)!=0)throw new IllegalArgumentException("D188 target vector differs");
        }
        for(String name:List.of("output_report","output_pcm_file","input_pcm_file","decoded_reference_pcm_file"))
            if(Files.exists(Path.of(p.get("workspace_root").getAsString()).resolve(p.get(name).getAsString())))throw new IllegalArgumentException("D188 artifact already exists: "+name);
    }

    private static void captureCase(ClientGameTestContext context,JsonObject protocol,JsonObject row,AcousticVector listener,byte[] encoded,byte[] decoded) {
        String arm=protocol.get("arm").getAsString(),phase="D188:"+arm+":"+row.get("name").getAsString();
        row.addProperty("capture_family","D188");row.addProperty("arm",arm);setPhase(phase+":ADMISSION");
        int caseStart=output.size();row.addProperty("output_offset_bytes",caseStart);
        row.addProperty("distance_m",1);row.addProperty("starting_rpm",12000);row.addProperty("transition_observation_steps",TRANSITION_STEPS);
        row.addProperty("transition_step_virtual_frames",STEP_FRAMES);row.addProperty("virtual_sample_rate",48000);
        row.addProperty("transition_step_is_exactly_one_minecraft_tick",false);row.addProperty("render_timing_is_ordinary_realtime",false);
        String policy=row.get("coefficient_policy").getAsString();int maxQuantizedPerVoice=(int)Math.floor(INPUT_BUDGET*32768/12);
        double decodedMaximum=decodedMaximumAbsoluteSample(decoded);
        double coefficient=(maxQuantizedPerVoice-.51)/(decodedMaximum*(1+1e-12));
        row.addProperty("coefficient",coefficient);row.addProperty("coefficient_algorithm","(floor(.9495*32768/12)-.51)/(decodedMaxAbsoluteSample*(1+1e-12))");
        row.addProperty("target_input_peak_budget",INPUT_BUDGET);row.addProperty("max_quantized_per_voice_target",maxQuantizedPerVoice);
        row.addProperty("decoded_reference_peak",decodedPeak(decoded));row.addProperty("decoded_reference_max_absolute_sample",decodedMaximum);
        row.addProperty("analytic_unquantized_twelve_voice_peak",12*decodedPeak(decoded)*coefficient);
        row.addProperty("native_output_peak_acceptance_is_independent",true);
        final VerifiedAssetPcm pcm;
        try {pcm=CalibratedAssetDecoder.decode(calibration(encoded,decoded,coefficient),encoded);}catch(IOException error){throw new IllegalStateException(error);}
        row.addProperty("actual_calibration_reference_amplitude",pcm.calibration().referenceAmplitude());
        row.addProperty("calibration_reference_distance_m",1);row.addProperty("calibration_reference_rpm",12000);row.addProperty("synthetic_full_scale_pressure_pa",1);
        row.addProperty("maximum_quantized_absolute_sample",pcm.maximumQuantizedAbsoluteSample());
        double inputBound=12*pcm.maximumQuantizedAbsoluteSample()/32768.0;
        row.addProperty("actual_quantized_twelve_voice_input_peak_bound",inputBound);
        if(policy.equals("NEAR_BUDGET") && !(inputBound<=INPUT_BUDGET))throw new IllegalStateException("declared coefficient algorithm exceeded target input budget");
        AtomicReference<CalibratedAssetResources.Catalog> installed=new AtomicReference<>();
        AtomicReference<List<Voice>> captured=new AtomicReference<>();
        JsonArray blocks=new JsonArray(),observations=new JsonArray();row.add("render_blocks",blocks);row.add("transition_observations",observations);
        try {
            context.runOnClient(client->{
                if(!client.isSameThread() || !probes.isEmpty() || !activeSets().isEmpty() || !handles(client).isEmpty())throw new IllegalStateException("pitch admission before empty actual client state");
                for(var id:protocol.getAsJsonArray("entity_ids")) {
                    ProbeDrone drone=new ProbeDrone(client.level,id.getAsInt());drone.setPos(listener.x(),listener.y()-drone.getBbHeight()*.5,listener.z()-1);probes.add(drone);
                }
                Map<CalibratedAssetResources.BindingKey,VerifiedAssetPcm> bindings=new HashMap<>();
                for(ProbeDrone drone:probes)for(int i=0;i<2;i++)bindings.put(new CalibratedAssetResources.BindingKey(AcousticProfileResources.keyOf(drone),DroneLoopSoundInstance.Layer.values()[i].name(),NORMAL_RESOURCES.get(i)),pcm);
                var catalog=new CalibratedAssetResources.Catalog(bindings);installed.set(catalog);
                row.addProperty("catalog_headroom_peak_bound",catalog.headroom().peakBound());
                row.addProperty("catalog_headroom_full_scale_pressure_pa",catalog.headroom().fullScalePressurePa().orElseThrow());
                row.addProperty("catalog_asset_binding_count",catalog.assets().size());
                JsonObject applied=new JsonObject();row.add("catalog_apply",applied);
                applied.addProperty("before_catalog_identity",System.identityHashCode(CalibratedAssetResources.snapshot()));applied.addProperty("next_catalog_identity",System.identityHashCode(catalog));
                applied.addProperty("asset_identity",System.identityHashCode(pcm));applied.addProperty("actual_client_thread",true);applied.addProperty("thread",Thread.currentThread().getName());applied.addProperty("production_method","CalibratedAssetResources.apply");
                new CalibratedAssetResources().apply(catalog,client.getResourceManager(),Runnable::run).join();
                applied.addProperty("apply_future_joined",true);applied.addProperty("after_catalog_identity",System.identityHashCode(CalibratedAssetResources.snapshot()));
                if(CalibratedAssetResources.snapshot()!=catalog)throw new IllegalStateException("actual pitch catalog publication failed");
                row.addProperty("catalog_identity",System.identityHashCode(catalog));row.addProperty("asset_identity",System.identityHashCode(pcm));
                JsonArray entities=new JsonArray();for(ProbeDrone drone:probes){JsonObject e=new JsonObject();e.addProperty("entity_id",drone.getId());e.addProperty("entity_identity",System.identityHashCode(drone));e.addProperty("profile_key",AcousticProfileResources.keyOf(drone).toString());entities.add(e);}row.add("fixture_entities",entities);
                WetSoundAdmissionCapture.live();for(ProbeDrone drone:probes)client.level.addEntity(drone);
            });
            context.waitFor(client->selectedReady(client,installed.get(),pcm),WAIT);
            context.runOnClient(client->{
                List<Voice> voices=discoverSelected(client,installed.get(),pcm);captured.set(voices);row.add("membership_before_render",membership(client));recordOptions(client,row);
                JsonArray bindings=new JsonArray();for(int i=0;i<voices.size();i++)bindings.add(voiceBinding(voices.get(i),i));row.add("voices",bindings);row.add("client_before_settle",clientObservation(client,voices));
            });
            List<Voice> voices=captured.get();setPhase(phase+":SETTLE");
            onSoundThread(context,()->{
                backendReadback(row);JsonArray before=nativeVoices(voices,1);row.add("native_before",before);requireNativePlaying(before);
                for(var value:before)if(value.getAsJsonObject().get("sample_offset").getAsInt()!=0)throw new IllegalStateException("fresh source sample offset is not zero before first render");
                row.addProperty("all_initial_source_sample_offsets_zero",true);row.addProperty("settle_output_offset_bytes",output.size());
                renderWindow(row,voices,1,"SETTLE",SETTLE,blocks,caseStart);row.addProperty("settle_output_bytes",SETTLE*4);row.add("native_after_settle",nativeVoices(voices,1));return null;
            });
            context.runOnClient(client->{
                requireRetained(client,voices,installed.get(),pcm);row.add("client_before_rpm_mutation",clientObservation(client,voices));
                JsonArray targets=row.getAsJsonArray("rpm_targets");for(int i=0;i<probes.size();i++)probes.get(i).rpm=targets.get(i).getAsFloat();
                row.add("client_after_rpm_mutation",clientObservation(client,voices));
            });
            row.addProperty("transition_output_offset_bytes",output.size());setPhase(phase+":PITCH_TRANSITION");
            for(int step=0;step<TRANSITION_STEPS;step++) {
                JsonObject observation=new JsonObject();observation.addProperty("step_index",step);observation.addProperty("requested_wait_tick_calls",1);
                observation.addProperty("virtual_frame_offset",step*STEP_FRAMES);observation.addProperty("virtual_frames",STEP_FRAMES);observations.add(observation);
                context.runOnClient(client->{requireRetained(client,voices,installed.get(),pcm);observation.add("client_before_wait_tick",clientObservation(client,voices));});
                context.waitTick();
                context.runOnClient(client->{requireRetained(client,voices,installed.get(),pcm);observation.add("client_after_wait_tick",clientObservation(client,voices));});
                final int index=step;
                onSoundThread(context,()->{
                    observation.addProperty("owner_thread",Thread.currentThread().getName());identity(observation);
                    observation.addProperty("output_offset_bytes",output.size());observation.add("native_after_fifo_barrier",nativeVoices(voices,1));
                    renderWindow(row,voices,1,"TRANSITION_"+index,STEP_FRAMES,blocks,caseStart);
                    observation.add("native_after_render",nativeVoices(voices,1));observation.addProperty("rendered_frames",STEP_FRAMES);return null;
                });
                context.runOnClient(client->{requireRetained(client,voices,installed.get(),pcm);observation.add("client_after_owner_render",clientObservation(client,voices));});
            }
            row.addProperty("transition_output_bytes",TRANSITION_STEPS*STEP_FRAMES*4);row.addProperty("completed_transition_observation_steps",observations.size());
            context.runOnClient(client->{requireRetained(client,voices,installed.get(),pcm);row.add("client_after_transition",clientObservation(client,voices));});
            setPhase(phase+":STEADY_MEASUREMENT");
            onSoundThread(context,()->{
                row.add("native_before_steady",nativeVoices(voices,1));row.addProperty("steady_output_offset_bytes",output.size());
                renderWindow(row,voices,1,"STEADY",STEADY,blocks,caseStart);row.addProperty("steady_output_bytes",STEADY*4);row.add("native_after_steady",nativeVoices(voices,1));return null;
            });
            context.runOnClient(client->{requireRetained(client,voices,installed.get(),pcm);row.add("membership_after_render",membership(client));row.add("voices_after_render",voiceState(voices));row.add("client_after_steady",clientObservation(client,voices));});
            if(output.size()-caseStart!=CASE_FRAMES*4)throw new IllegalStateException("case output count differs from predeclared settle/transition/steady schedule");
            byte[] active=Arrays.copyOfRange(output.toByteArray(),caseStart,output.size());
            row.addProperty("active_output_bytes",active.length);row.addProperty("active_output_frames",active.length/4);row.addProperty("active_output_sha256",sha(active));row.add("active_output_statistics",floatStatistics(active));
            naturalRetire(context,row,voices,phase,arm,blocks,caseStart);
        } catch(Throwable error) {
            row.addProperty("capture_failure",error.toString());throw error;
        } finally {
            byte[] whole=Arrays.copyOfRange(output.toByteArray(),caseStart,output.size());row.addProperty("output_bytes",whole.length);row.addProperty("output_sha256",sha(whole));row.addProperty("actual_output_frames",whole.length/4);row.add("output_statistics",floatStatistics(whole));
            try {cleanup(context,phase+":after_case");}catch(Throwable error){row.addProperty("case_cleanup_failure",error.toString());throw error;}
            if(captured.get()!=null)try {sealInputs(row,captured.get());}catch(Throwable error){row.addProperty("input_seal_failure",error.toString());throw error;}
            context.runOnClient(client->{row.addProperty("handles_after_cleanup",handles(client).size());row.addProperty("manager_sets_after_cleanup",activeSets().size());});
        }
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
            if(!sounds.add(sound)||!channels.add(channel)||!streams.add(stream)||!stream.distanceOwnerVerified())throw new IllegalStateException("selected normal asset voices are not distinct or lack verified ownership");voices.add(new Voice(drone,set,sound,channel,stream,((ChannelSourceAccessor)channel).fpvdrone$getSource()));
        }}return List.copyOf(voices);
    }
    private static JsonObject voiceBinding(Voice voice,int index) {
        JsonObject row=new JsonObject();join(row,voice,index);row.addProperty("resource",String.valueOf(field(voice.sound,"calibratedResource")));row.addProperty("procedural_backend",voice.sound.usesProceduralAudio());row.addProperty("owner_verified",voice.stream.distanceOwnerVerified());row.addProperty("raw_envelope",(Float)field(voice.sound,"volume"));row.addProperty("pitch",voice.sound.getPitch());row.addProperty("coefficient",(Double)field(voice.sound,"assetAmplitude"));row.addProperty("input_record_identity",MainContextOutputCapture.identity(voice.stream));row.addProperty("streaming_buffer_bytes",((ChannelStreamingBufferAccessor)voice.channel).fpvdrone$getStreamingBufferSize());
        var format=voice.stream.getFormat();JsonObject f=new JsonObject();f.addProperty("sample_rate",format.getSampleRate());f.addProperty("sample_size_bits",format.getSampleSizeInBits());f.addProperty("channels",format.getChannels());f.addProperty("frame_size",format.getFrameSize());f.addProperty("big_endian",format.isBigEndian());f.addProperty("encoding",format.getEncoding().toString());row.add("format",f);return row;
    }
    private static void join(JsonObject row,Voice voice,int index) {
        row.addProperty("voice_index",index);row.addProperty("source",voice.nativeSource);row.addProperty("entity_id",voice.drone.getId());row.addProperty("entity_identity",System.identityHashCode(voice.drone));row.addProperty("set_identity",System.identityHashCode(voice.set));row.addProperty("sound_identity",System.identityHashCode(voice.sound));row.addProperty("layer",voice.sound.layer().name());row.addProperty("catalog_identity",System.identityHashCode(field(voice.sound,"assetCatalog")));row.addProperty("asset_identity",System.identityHashCode(field(voice.sound,"calibratedAsset")));row.addProperty("channel_identity",System.identityHashCode(voice.channel));row.addProperty("channel_allocation_identity",WetChannelLifecycleCapture.identity(voice.channel));row.addProperty("stream_identity",System.identityHashCode(voice.stream));
    }
    private static JsonArray nativeVoices(List<Voice> voices,double expectedDistance) {
        var library=library();MainContextOutputCapture.requireCurrent(library.mainOutput$getDevice(),library.mainOutput$getContext());
        JsonArray rows=new JsonArray();boolean resamplers=AL.getCapabilities().AL_SOFT_source_resampler;
        for(int i=0;i<voices.size();i++) {
            Voice voice=voices.get(i);boolean initialized=channelInitialized(voice.channel);JsonObject row;
            if(initialized) {
                if(((ChannelSourceAccessor)voice.channel).fpvdrone$getSource()!=voice.nativeSource)throw new IllegalStateException("D188 retained Channel changed live source id");
                row=ManagerAssetResumptionCapture.nativeSnapshot(voice.channel);row.addProperty("source_query_performed",true);
            } else {
                // The old integer may already identify another allocation; do not even call alIsSource.
                row=new JsonObject();row.addProperty("initialized",false);row.addProperty("source_valid",false);
                row.add("native_source_exists",JsonNull.INSTANCE);row.addProperty("native_source_exists_checked",false);row.addProperty("source_query_performed",false);
                FloatBuffer listener=BufferUtils.createFloatBuffer(3);AL10.alGetListenerfv(AL10.AL_POSITION,listener);JsonArray p=new JsonArray();while(listener.hasRemaining())p.add(listener.get());
                row.add("listener_position",p);row.addProperty("listener_gain",AL10.alGetListenerf(AL10.AL_GAIN));row.addProperty("al_error",AL10.alGetError());
                row.addProperty("released_native_source_not_queried",true);
            }
            join(row,voice,i);identity(row);row.addProperty("pitch_setter_cursor",PitchHeadroomCapture.cursor());
            if(initialized)row.addProperty("gain_setter_cursor",gainSetterCursor());rows.add(row);
            row.addProperty("raw_envelope",(Float)field(voice.sound,"volume"));row.addProperty("coefficient",(Double)field(voice.sound,"assetAmplitude"));
            row.addProperty("owner_verified",voice.stream.distanceOwnerVerified());row.addProperty("stream_closed",AssetNativeCapture.closed(voice.stream));
            row.addProperty("target_pitch",(Float)field(voice.sound,"assetPitch"));row.addProperty("smoothed_sound_pitch",voice.sound.getPitch());row.addProperty("rpm_target",voice.drone.getAverageMotorRpm());
            row.addProperty("declared_source_listener_distance_m",expectedDistance);
            if(row.get("source_valid").getAsBoolean()) {
                int source=voice.nativeSource;
                row.addProperty("relative",AL10.alGetSourcei(source,AL10.AL_SOURCE_RELATIVE)!=0);row.addProperty("minimum_gain",AL10.alGetSourcef(source,AL10.AL_MIN_GAIN));row.addProperty("maximum_gain",AL10.alGetSourcef(source,AL10.AL_MAX_GAIN));
                if(resamplers) {
                    int id=AL10.alGetSourcei(source,SOFTSourceResampler.AL_SOURCE_RESAMPLER_SOFT);row.addProperty("source_resampler_id",id);
                    row.addProperty("source_resampler_name",SOFTSourceResampler.alGetStringiSOFT(SOFTSourceResampler.AL_RESAMPLER_NAME_SOFT,id));
                }
                var p=row.getAsJsonArray("position");var l=row.getAsJsonArray("listener_position");
                double x=p.get(0).getAsDouble()-l.get(0).getAsDouble(),y=p.get(1).getAsDouble()-l.get(1).getAsDouble(),z=p.get(2).getAsDouble()-l.get(2).getAsDouble();
                row.addProperty("actual_source_listener_distance_m",Math.sqrt(x*x+y*y+z*z));
            }
            row.addProperty("al_error_extra",AL10.alGetError());
        }
        return rows;
    }
    private static long gainSetterCursor() {synchronized(AssetNativeCapture.class){return ((Number)field(AssetNativeCapture.class,"order")).longValue();}}
    private static boolean channelInitialized(Channel channel) {return ((java.util.concurrent.atomic.AtomicBoolean)field(channel,"initialized")).get();}
    private static void requireReleaseNativeReadable(JsonArray observations) {
        if(observations.size()!=12)throw new IllegalStateException("D188 twelve retained release observations required");
        for(var value:observations) {
            JsonObject row=value.getAsJsonObject();boolean initialized=row.get("initialized").getAsBoolean();
            if(row.get("al_error").getAsInt()!=0 || row.get("al_error_extra").getAsInt()!=0)throw new IllegalStateException("D188 release native getter failed");
            if(!initialized && row.get("source_query_performed").getAsBoolean())throw new IllegalStateException("D188 released source was queried");
            if(initialized && !row.get("source_valid").getAsBoolean())throw new IllegalStateException("D188 initialized source is absent");
            if(initialized && Math.abs(row.get("actual_source_listener_distance_m").getAsDouble()-1)>1e-5)throw new IllegalStateException("D188 release geometry differs");
        }
    }

    private static void requireNativePlaying(JsonArray observations) {
        if(observations.size()!=12)throw new IllegalStateException("twelve native observations required");
        for(var value:observations) {
            JsonObject row=value.getAsJsonObject();
            if(!row.get("source_valid").getAsBoolean() || row.get("source_state").getAsInt()!=AL10.AL_PLAYING || row.get("queued").getAsInt()<1 || !row.get("owner_verified").getAsBoolean())throw new IllegalStateException("pitch source invalid, stopped, empty or unowned");
            if(row.get("al_error_extra").getAsInt()!=0 || Math.abs(row.get("actual_source_listener_distance_m").getAsDouble()-1)>1e-5)throw new IllegalStateException("pitch native getter or one metre geometry failed");
        }
    }

    private static void renderWindow(JsonObject row,List<Voice> voices,double distance,String phase,int frames,JsonArray blocks,int caseStart) {
        var library=library();MainContextOutputCapture.requireCurrent(library.mainOutput$getDevice(),library.mainOutput$getContext());int windowStart=output.size();
        for(int f=0;f<frames;f+=CHUNK) {
            int count=Math.min(CHUNK,frames-f);JsonObject block=new JsonObject();blocks.add(block);
            block.addProperty("block_index",blocks.size()-1);block.addProperty("phase",phase);block.addProperty("phase_frame_offset",f);block.addProperty("frames",count);
            block.addProperty("case_frame_offset",(output.size()-caseStart)/4);block.addProperty("output_offset_bytes",output.size());block.addProperty("case_output_offset_bytes",output.size()-caseStart);
            block.addProperty("owner_thread",Thread.currentThread().getName());identity(block);
            JsonArray before=nativeVoices(voices,distance);block.add("native_before",before);requireNativePlaying(before);
            FloatBuffer samples=BufferUtils.createFloatBuffer(count);SOFTLoopback.alcRenderSamplesSOFT(library.mainOutput$getDevice(),samples,count);
            block.addProperty("render_al_error",AL10.alGetError());block.addProperty("render_alc_error",ALC10.alcGetError(library.mainOutput$getDevice()));
            ByteBuffer pcm=ByteBuffer.allocate(count*4).order(ByteOrder.LITTLE_ENDIAN);while(samples.hasRemaining())pcm.putFloat(samples.get());
            byte[] bytes=pcm.array();output.writeBytes(bytes);block.addProperty("output_bytes",bytes.length);block.addProperty("output_sha256",sha(bytes));block.add("output_statistics",floatStatistics(bytes));
            if(block.get("render_al_error").getAsInt()!=0 || block.get("render_alc_error").getAsInt()!=0)throw new IllegalStateException("pitch native render failed");
            for(Voice voice:voices)voice.channel.updateStream();
            JsonArray after=nativeVoices(voices,distance);block.add("native_after",after);block.addProperty("queue_service_calls",voices.size());
            block.addProperty("al_error_after_service",AL10.alGetError());requireNativePlaying(after);
            if(block.get("al_error_after_service").getAsInt()!=0)throw new IllegalStateException("pitch queue service failed");
            if(block.getAsJsonObject("output_statistics").get("nonfinite_samples").getAsInt()!=0)throw new IllegalStateException("pitch float output contains a nonfinite sample");
        }
        if(output.size()-windowStart!=frames*4)throw new IllegalStateException("pitch render window byte count differs");
        MainContextOutputCapture.requireCurrent(library.mainOutput$getDevice(),library.mainOutput$getContext());
    }

    private static void renderReleaseWindow(List<Voice> voices,String phase,int frames,JsonArray blocks,int caseStart) {
        var library=library();MainContextOutputCapture.requireCurrent(library.mainOutput$getDevice(),library.mainOutput$getContext());int start=output.size();
        for(int f=0;f<frames;f+=CHUNK) {
            int count=Math.min(CHUNK,frames-f);JsonObject block=new JsonObject();blocks.add(block);
            block.addProperty("block_index",blocks.size()-1);block.addProperty("phase",phase);block.addProperty("phase_frame_offset",f);block.addProperty("frames",count);
            block.addProperty("case_frame_offset",(output.size()-caseStart)/4);block.addProperty("output_offset_bytes",output.size());block.addProperty("case_output_offset_bytes",output.size()-caseStart);
            block.addProperty("owner_thread",Thread.currentThread().getName());identity(block);block.addProperty("release_playing_requirement",false);
            JsonArray before=nativeVoices(voices,1);block.add("native_before",before);requireReleaseNativeReadable(before);
            FloatBuffer samples=BufferUtils.createFloatBuffer(count);SOFTLoopback.alcRenderSamplesSOFT(library.mainOutput$getDevice(),samples,count);
            block.addProperty("render_al_error",AL10.alGetError());block.addProperty("render_alc_error",ALC10.alcGetError(library.mainOutput$getDevice()));
            ByteBuffer pcm=ByteBuffer.allocate(count*4).order(ByteOrder.LITTLE_ENDIAN);while(samples.hasRemaining())pcm.putFloat(samples.get());
            byte[] bytes=pcm.array();output.writeBytes(bytes);block.addProperty("output_bytes",bytes.length);block.addProperty("output_sha256",sha(bytes));block.add("output_statistics",floatStatistics(bytes));
            if(block.get("render_al_error").getAsInt()!=0 || block.get("render_alc_error").getAsInt()!=0)throw new IllegalStateException("D188 release native render failed");
            JsonArray services=new JsonArray();int serviced=0;
            for(int i=0;i<voices.size();i++) {
                Voice voice=voices.get(i);JsonObject service=new JsonObject();join(service,voice,i);
                boolean initialized=channelInitialized(voice.channel),sameSource=initialized&&((ChannelSourceAccessor)voice.channel).fpvdrone$getSource()==voice.nativeSource;
                boolean sameStream=((MainOutputChannelAccessor)voice.channel).mainOutput$getStream()==voice.stream,closed=AssetNativeCapture.closed(voice.stream);
                service.addProperty("initialized",initialized);service.addProperty("captured_source_retained",sameSource);service.addProperty("captured_stream_retained",sameStream);service.addProperty("stream_closed",closed);
                boolean run=initialized&&sameSource&&sameStream&&!closed;service.addProperty("update_stream_called",run);services.add(service);
                if(run){voice.channel.updateStream();serviced++;}
            }
            block.add("queue_service_observations",services);block.addProperty("queue_service_calls",serviced);
            JsonArray after=nativeVoices(voices,1);block.add("native_after",after);block.addProperty("al_error_after_service",AL10.alGetError());requireReleaseNativeReadable(after);
            if(block.get("al_error_after_service").getAsInt()!=0)throw new IllegalStateException("D188 release queue service failed");
            if(block.getAsJsonObject("output_statistics").get("nonfinite_samples").getAsInt()!=0)throw new IllegalStateException("D188 release float output contains nonfinite samples");
        }
        if(output.size()-start!=frames*4)throw new IllegalStateException("D188 release window byte count differs");
        MainContextOutputCapture.requireCurrent(library.mainOutput$getDevice(),library.mainOutput$getContext());
    }
    private static void requireCpuEmpty(Minecraft client,String reason) {
        if(!activeSets().isEmpty() || !handles(client).isEmpty())throw new IllegalStateException(reason+": CPU manager/handles not empty");
    }
    private static void captureFinalEmpty(ClientGameTestContext context,JsonObject report) {
        JsonObject row=new JsonObject();report.add("final_empty_probe",row);int start=output.size();
        row.addProperty("capture_family","D188");row.addProperty("output_offset_bytes",start);row.addProperty("declared_frames",FINAL_EMPTY_FRAMES);
        row.addProperty("queue_service_calls",0);row.addProperty("source_query_calls",0);row.addProperty("source_admission_requested",false);
        JsonArray blocks=new JsonArray();row.add("render_blocks",blocks);setPhase("D188:"+report.get("arm").getAsString()+":FINAL_EMPTY");
        try {
            context.runOnClient(client->{
                requireCpuEmpty(client,"D188 final empty before render");row.add("membership_before",membership(client));row.addProperty("client_game_time_before",client.level.getGameTime());
                recordEmptyAdmission(row,"before");
            });
            onSoundThread(context,()->{
                backendReadback(row);var library=library();
                for(int f=0;f<FINAL_EMPTY_FRAMES;f+=CHUNK) {
                    requireCpuEmpty(Minecraft.getInstance(),"D188 final empty owner block");MainContextOutputCapture.requireCurrent(library.mainOutput$getDevice(),library.mainOutput$getContext());
                    int count=Math.min(CHUNK,FINAL_EMPTY_FRAMES-f);JsonObject block=new JsonObject();blocks.add(block);
                    block.addProperty("block_index",blocks.size()-1);block.addProperty("phase","FINAL_EMPTY");block.addProperty("phase_frame_offset",f);block.addProperty("frames",count);
                    block.addProperty("output_offset_bytes",output.size());block.addProperty("probe_output_offset_bytes",output.size()-start);block.addProperty("owner_thread",Thread.currentThread().getName());identity(block);
                    block.addProperty("manager_set_count",activeSets().size());block.addProperty("actual_handle_count",handles(Minecraft.getInstance()).size());block.addProperty("queue_service_calls",0);block.addProperty("source_query_calls",0);
                    FloatBuffer samples=BufferUtils.createFloatBuffer(count);SOFTLoopback.alcRenderSamplesSOFT(library.mainOutput$getDevice(),samples,count);
                    block.addProperty("render_al_error",AL10.alGetError());block.addProperty("render_alc_error",ALC10.alcGetError(library.mainOutput$getDevice()));
                    ByteBuffer pcm=ByteBuffer.allocate(count*4).order(ByteOrder.LITTLE_ENDIAN);while(samples.hasRemaining())pcm.putFloat(samples.get());byte[] bytes=pcm.array();output.writeBytes(bytes);
                    block.addProperty("output_bytes",bytes.length);block.addProperty("output_sha256",sha(bytes));block.add("output_statistics",floatStatistics(bytes));
                    if(block.get("render_al_error").getAsInt()!=0 || block.get("render_alc_error").getAsInt()!=0)throw new IllegalStateException("D188 final empty native render failed");
                    if(block.getAsJsonObject("output_statistics").get("nonfinite_samples").getAsInt()!=0)throw new IllegalStateException("D188 final empty output contains nonfinite samples");
                }
                return null;
            });
            context.runOnClient(client->{
                requireCpuEmpty(client,"D188 final empty after render");row.add("membership_after",membership(client));row.addProperty("client_game_time_after",client.level.getGameTime());
                recordEmptyAdmission(row,"after");
            });
            if(row.get("allowed_admission_requests_before").getAsInt()!=row.get("allowed_admission_requests_after").getAsInt())throw new IllegalStateException("D188 new allowed source admission during final empty render");
            row.addProperty("no_new_allowed_source_admission",true);
            if(output.size()-start!=FINAL_EMPTY_FRAMES*4)throw new IllegalStateException("D188 final empty byte count differs");row.addProperty("status","captured");
        } catch(Throwable error) {row.addProperty("status","failed");row.addProperty("failure",error.toString());throw error;}
        finally {byte[] bytes=Arrays.copyOfRange(output.toByteArray(),start,output.size());row.addProperty("output_bytes",bytes.length);row.addProperty("actual_output_frames",bytes.length/4);row.addProperty("output_sha256",sha(bytes));row.add("output_statistics",floatStatistics(bytes));}
    }

    private static void recordEmptyAdmission(JsonObject row,String suffix) {
        JsonObject admission=WetSoundAdmissionCapture.snapshot();row.add("admission_snapshot_"+suffix,admission);
        row.addProperty("admission_request_count_"+suffix,admission.getAsJsonArray("requests").size());int allowed=0;
        for(var value:admission.getAsJsonArray("requests"))if(value.getAsJsonObject().get("allowed").getAsBoolean())allowed++;
        row.addProperty("allowed_admission_requests_"+suffix,allowed);String mode=String.valueOf(field(WetSoundAdmissionCapture.class,"mode"));
        row.addProperty("admission_mode_"+suffix,mode);if(!mode.equals("CLOSED"))throw new IllegalStateException("D188 final empty admission isolation is not closed");
    }

    private static void requireRetained(Minecraft client,List<Voice> voices,CalibratedAssetResources.Catalog catalog,VerifiedAssetPcm pcm) {
        if(activeSets().size()!=6 || handles(client).size()!=12 || CalibratedAssetResources.snapshot()!=catalog)throw new IllegalStateException("normal pitch manager membership/catalog changed");
        for(Voice voice:voices) {
            var handle=handles(client).get(voice.sound);
            if(activeSets().get(voice.drone.getId())!=voice.set || client.level.getEntity(voice.drone.getId())!=voice.drone || field(voice.set,"drone")!=voice.drone
                    || field(voice.set,voice.sound.layer()==DroneLoopSoundInstance.Layer.MOTOR?"motor":"propeller")!=voice.sound
                    || handle==null || ((ChannelHandleChannelAccessor)handle).fpvdrone$getChannel()!=voice.channel
                    || ((MainOutputChannelAccessor)voice.channel).mainOutput$getStream()!=voice.stream || ((ChannelSourceAccessor)voice.channel).fpvdrone$getSource()!=voice.nativeSource
                    || field(voice.sound,"assetCatalog")!=catalog || field(voice.sound,"calibratedAsset")!=pcm || voice.sound.usesProceduralAudio() || voice.sound.isStopped()
                    || voice.sound.getVolume()!=.99999994f || !voice.stream.distanceOwnerVerified())throw new IllegalStateException("pitch set/entity/sound/source/Channel/stream identity or active envelope changed");
        }
    }

    private static JsonArray voiceState(List<Voice> voices) {
        JsonArray rows=new JsonArray();
        for(int i=0;i<voices.size();i++) {
            Voice voice=voices.get(i);JsonObject row=new JsonObject();join(row,voice,i);
            row.addProperty("raw_envelope",(Float)field(voice.sound,"volume"));row.addProperty("pitch",voice.sound.getPitch());
            row.addProperty("target_pitch",(Float)field(voice.sound,"assetPitch"));row.addProperty("smoothed_sound_pitch",voice.sound.getPitch());
            row.addProperty("coefficient",(Double)field(voice.sound,"assetAmplitude"));row.addProperty("rpm",voice.drone.getAverageMotorRpm());
            row.addProperty("declared_rpm_ratio_target",voice.drone.getAverageMotorRpm()/12000.0);row.addProperty("probe_entity_tick_count",voice.drone.tickCount);
            row.addProperty("stopped",voice.sound.isStopped());row.addProperty("entity_alive",voice.drone.isAlive());row.addProperty("entity_removed",voice.drone.isRemoved());rows.add(row);
        }
        return rows;
    }
    private static JsonObject clientObservation(Minecraft client,List<Voice> voices) {
        JsonObject row=membership(client);row.addProperty("actual_client_thread",client.isSameThread());row.addProperty("thread",Thread.currentThread().getName());
        row.addProperty("client_game_time",client.level==null?-1:client.level.getGameTime());row.add("voices",voiceState(voices));
        Map<Integer,Integer> counts=new HashMap<>();Map<Integer,JsonObject> lastBySound=new HashMap<>();
        // Read only completed hook rows, without copying every recorder on each observation.
        synchronized(CalibratedGainCapture.class) {
            JsonArray completed=(JsonArray)field(CalibratedGainCapture.class,"ticks");
            for(var value:completed) {
                JsonObject tick=value.getAsJsonObject();int id=tick.get("sound_identity").getAsInt();
                counts.merge(id,1,Integer::sum);lastBySound.put(id,tick);
            }
        }
        JsonArray indices=new JsonArray();
        for(int i=0;i<voices.size();i++) {
            Voice voice=voices.get(i);int id=System.identityHashCode(voice.sound),count=counts.getOrDefault(id,0);JsonObject last=lastBySound.get(id);
            JsonObject index=new JsonObject();join(index,voice,i);index.addProperty("completed_sound_tick_count",count);index.addProperty("last_completed_sound_tick_index",count-1);
            if(last!=null) {index.addProperty("last_completed_sound_tick_begin_order",last.get("order").getAsLong());index.addProperty("last_completed_sound_tick_end_order",last.get("end_order").getAsLong());}
            indices.add(index);
        }
        row.add("actual_sound_tick_indices",indices);row.addProperty("indices_are_completed_tick_hooks_for_each_sound",true);
        return row;
    }

    private static void naturalRetire(ClientGameTestContext context,JsonObject row,List<Voice> voices,String phase,String arm,JsonArray blocks,int caseStart) {
        setPhase(phase+":RPM_ZERO_RELEASE");int releaseStart=output.size(),framesPerStep=arm.equals("PAUSED")?0:STEP_FRAMES;
        row.addProperty("release_output_offset_bytes",releaseStart);row.addProperty("release_observation_steps",RELEASE_STEPS);
        row.addProperty("release_frames_per_step",framesPerStep);row.addProperty("release_clock_arm",arm);
        context.runOnClient(client->{
            row.add("release_membership_before",membership(client));row.add("release_voices_before",voiceState(voices));
            for(ProbeDrone drone:probes)drone.rpm=0;
            row.add("release_client_after_rpm_zero",clientObservation(client,voices));
        });
        JsonArray observations=new JsonArray();row.add("release_observations",observations);
        try {
            for(int step=0;step<RELEASE_STEPS;step++) {
                JsonObject observation=new JsonObject();observations.add(observation);observation.addProperty("step_index",step);
                observation.addProperty("requested_wait_tick_calls",1);observation.addProperty("virtual_release_frame_offset",step*framesPerStep);observation.addProperty("virtual_frames",framesPerStep);
                context.runOnClient(client->observation.add("client_before_wait_tick",clientObservation(client,voices)));
                context.waitTick();
                context.runOnClient(client->observation.add("client_after_wait_tick",clientObservation(client,voices)));
                final int index=step;
                onSoundThread(context,()->{
                    identity(observation);observation.addProperty("owner_thread",Thread.currentThread().getName());observation.addProperty("output_offset_bytes",output.size());
                    JsonArray before=nativeVoices(voices,1);observation.add("native_after_fifo_barrier",before);requireReleaseNativeReadable(before);
                    if(framesPerStep>0)renderReleaseWindow(voices,"RELEASE_"+index,framesPerStep,blocks,caseStart);
                    JsonArray after=nativeVoices(voices,1);observation.add("native_after_render",after);requireReleaseNativeReadable(after);
                    observation.addProperty("rendered_frames",framesPerStep);observation.addProperty("completed",true);return null;
                });
                context.runOnClient(client->observation.add("client_after_owner_render",clientObservation(client,voices)));
            }
            context.runOnClient(client->{
                row.add("release_membership_after",membership(client));row.add("release_voices_after",voiceState(voices));
                row.add("release_client_after_all_steps",clientObservation(client,voices));requireCpuEmpty(client,"D188 sixty-step release end");
                for(Voice voice:voices)if((Float)field(voice.sound,"volume")!=0 || voice.drone.getAverageMotorRpm()!=0 || !AssetNativeCapture.closed(voice.stream))throw new IllegalStateException("D188 fixed release did not end at raw envelope/RPM zero with old stream closed");
            });
            if(output.size()-releaseStart!=RELEASE_STEPS*framesPerStep*4)throw new IllegalStateException("D188 release render byte count differs");
            row.addProperty("completed_release_observation_steps",RELEASE_STEPS);row.addProperty("natural_rpm_zero_retirement_complete",true);
        } finally {
            byte[] release=Arrays.copyOfRange(output.toByteArray(),releaseStart,output.size());row.addProperty("release_output_bytes",release.length);
            row.addProperty("release_output_frames",release.length/4);row.addProperty("release_output_sha256",sha(release));row.add("release_output_statistics",floatStatistics(release));
        }
    }

    private static void sealInputs(JsonObject row,List<Voice> voices) {
        JsonArray bindings=row.getAsJsonArray("voices");
        for(int i=0;i<voices.size();i++) {
            Voice voice=voices.get(i);JsonObject binding=bindings.get(i).getAsJsonObject();boolean closed=AssetNativeCapture.closed(voice.stream);
            binding.addProperty("stream_closed_after_cleanup",closed);binding.addProperty("released_native_source_not_queried",true);
            if(!closed)throw new IllegalStateException("selected pitch stream not closed after cleanup");
            byte[] data=MainContextOutputCapture.input(voice.stream);binding.addProperty("input_offset_bytes",input.size());binding.addProperty("input_bytes",data.length);
            binding.addProperty("input_frames",data.length/2);binding.addProperty("input_sha256",sha(data));binding.add("asset_read_batches",AssetNativeCapture.reads(voice.stream));
            binding.addProperty("input_peak",decodedPeak(data));binding.addProperty("input_max_absolute_s16_sample",decodedMaximumAbsoluteSample(data));input.writeBytes(data);
        }
        row.addProperty("inputs_sealed_after_cleanup",true);
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
            FloatBuffer buffer=BufferUtils.createFloatBuffer(3);AL10.alGetListenerfv(AL10.AL_POSITION,buffer);AcousticVector position=new AcousticVector(buffer.get(0),buffer.get(1),buffer.get(2));var transform=engine(Minecraft.getInstance()).getListenerTransform().position();JsonObject nativeRow=new JsonObject();identity(nativeRow);nativeRow.add("camera_position",vector(expected.frame().positionMeters()));nativeRow.add("native_position",vector(position));nativeRow.addProperty("ready",position.subtract(expected.frame().positionMeters()).length()<=1e-5&&new AcousticVector(transform.x,transform.y,transform.z).subtract(expected.frame().positionMeters()).length()<=1e-5);nativeRow.addProperty("al_error",AL10.alGetError());if(nativeRow.get("al_error").getAsInt()!=0)throw new IllegalStateException("pitch headroom listener getter failed");return new ListenerReadback(position,nativeRow);
        });readback.observation().addProperty("attempt",attempt);attempts.add(readback.observation());if(readback.observation().get("ready").getAsBoolean()){group.addProperty("ready",true);return readback.position();}}throw new IllegalStateException("pitch headroom native listener did not acknowledge camera pose");
    }
    private static AudioAssetCalibration calibration(byte[] encoded,byte[] decoded,double coefficient) {
        double squares=0;var shorts=ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        while(shorts.hasRemaining()){double x=shorts.get()/32768.0;squares+=x*x;}
        double rms=Math.sqrt(squares/(decoded.length/2));
        return new AudioAssetCalibration(sha(encoded),sha(decoded),48000,decoded.length/2,0,decoded.length/2,rms,rms*coefficient,1,12000,0,
                List.of(6000.0,12000.0,24000.0),List.of(-1.0,0.0,1.0),List.of(List.of(0.0,0.0,0.0),List.of(0.0,0.0,0.0),List.of(0.0,0.0,0.0)));
    }
    private static double decodedMaximumAbsoluteSample(byte[] decoded) {
        double maximum=0;var samples=ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        while(samples.hasRemaining())maximum=Math.max(maximum,Math.abs((int)samples.get()));return maximum;
    }
    private static JsonObject floatStatistics(byte[] bytes) {
        JsonObject row=new JsonObject();double peak=0,squares=0;int nonfinite=0,aboveOne=0;var samples=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        while(samples.hasRemaining()) {
            float value=samples.get();if(!Float.isFinite(value)){nonfinite++;continue;}
            peak=Math.max(peak,Math.abs((double)value));squares+=(double)value*value;if(Math.abs(value)>1)aboveOne++;
        }
        row.addProperty("frames",bytes.length/4);row.addProperty("finite_peak",peak);row.addProperty("finite_rms",bytes.length==0?0:Math.sqrt(squares/(bytes.length/4)));
        row.addProperty("nonfinite_samples",nonfinite);row.addProperty("samples_above_absolute_one",aboveOne);row.addProperty("samples_retained_without_clamp",true);return row;
    }
    private static void backendReadback(JsonObject row) {
        identity(row);var library=library();long device=library.mainOutput$getDevice();MainContextOutputCapture.requireCurrent(device,library.mainOutput$getContext());
        row.addProperty("library_identity",System.identityHashCode(library));row.addProperty("library_class",library.getClass().getName());
        row.addProperty("version",AL10.alGetString(AL10.AL_VERSION));row.addProperty("vendor",AL10.alGetString(AL10.AL_VENDOR));row.addProperty("renderer",AL10.alGetString(AL10.AL_RENDERER));
        JsonObject attrs=new JsonObject();row.add("native_context_readback",attrs);
        attrs.addProperty("frequency",ALC10.alcGetInteger(device,ALC10.ALC_FREQUENCY));
        attrs.addProperty("format_channels",ALC10.alcGetInteger(device,SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT));attrs.addProperty("format_type",ALC10.alcGetInteger(device,SOFTLoopback.ALC_FORMAT_TYPE_SOFT));
        attrs.addProperty("hrtf",ALC10.alcGetInteger(device,SOFTHRTF.ALC_HRTF_SOFT));attrs.addProperty("output_limiter",ALC10.alcGetInteger(device,SOFTOutputLimiter.ALC_OUTPUT_LIMITER_SOFT));attrs.addProperty("alc_error",ALC10.alcGetError(device));
        if(attrs.get("alc_error").getAsInt()!=0 || attrs.get("frequency").getAsInt()!=48000 || attrs.get("format_channels").getAsInt()!=SOFTLoopback.ALC_MONO_SOFT || attrs.get("format_type").getAsInt()!=SOFTLoopback.ALC_FLOAT_SOFT || attrs.get("hrtf").getAsInt()!=ALC10.ALC_FALSE || attrs.get("output_limiter").getAsInt()!=ALC10.ALC_FALSE)
            throw new IllegalStateException("pitch main context readback differs from predeclared unlimited mono float output");
        JsonObject resampling=new JsonObject();row.add("resamplers",resampling);boolean supported=AL.getCapabilities().AL_SOFT_source_resampler;
        resampling.addProperty("extension_supported",supported);resampling.addProperty("default_was_changed",false);
        if(supported) {
            int count=AL10.alGetInteger(SOFTSourceResampler.AL_NUM_RESAMPLERS_SOFT),defaultId=AL10.alGetInteger(SOFTSourceResampler.AL_DEFAULT_RESAMPLER_SOFT);
            resampling.addProperty("default_id",defaultId);JsonArray list=new JsonArray();resampling.add("available",list);
            for(int id=0;id<count;id++){JsonObject r=new JsonObject();r.addProperty("id",id);r.addProperty("name",SOFTSourceResampler.alGetStringiSOFT(SOFTSourceResampler.AL_RESAMPLER_NAME_SOFT,id));list.add(r);}
            resampling.addProperty("al_error",AL10.alGetError());if(resampling.get("al_error").getAsInt()!=0)throw new IllegalStateException("pitch native resampler readback failed");
        }
    }

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
