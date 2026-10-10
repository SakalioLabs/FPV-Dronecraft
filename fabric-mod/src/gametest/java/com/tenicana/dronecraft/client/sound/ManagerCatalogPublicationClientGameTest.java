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
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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

/** Actual catalog publication and normal manager selection, using generated numeric assets only. */
final class ManagerCatalogPublicationClientGameTest {
    private static final int WAIT=600;
    private static final List<Identifier> NORMAL_RESOURCES=List.of(
            Identifier.parse("fpvdrone:sounds/drone/motor_loop.ogg"),
            Identifier.parse("fpvdrone:sounds/drone/propeller_loop.ogg"));
    private static final List<String> PHASE_NAMES=List.of("WARM_A","PUBLISH_B_OLD","ADMIT_NEW_B","REJECT_B","ZERO_BOTH","RESTART_OLD_B","FINAL_ZERO");
    private static final List<Integer> OLD_RPM=List.of(12000,12000,12000,12000,0,12000,0);
    private static final List<Integer> NEW_RPM=List.of(0,0,12000,12000,0,0,0);
    private static final List<Integer> PHASE_TICKS=List.of(6,8,24,6,60,12,60);
    private static final ByteArrayOutputStream input=new ByteArrayOutputStream(),output=new ByteArrayOutputStream();
    private static final JsonArray listenerReadiness=new JsonArray();
    private static ProbeDrone oldProbe,newProbe;
    private static ManagerCatalogPublicationCapture.Session activeCapture;

    private static final class ProbeDrone extends DroneEntity {
        volatile float rpm;
        ProbeDrone(ClientLevel level,int id,float rpm) {super(DroneEntityTypes.DRONE,level);setId(id);this.rpm=rpm;}
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
        return p.has("manager_catalog_publication_experiment")&&p.get("manager_catalog_publication_experiment").getAsBoolean();
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
                throw new IllegalStateException("catalog publication requires calibratedGainProtocol and assetNativeProtocol bound to the same declaration");
            JsonObject loaded=new JsonObject();
            for(var item:protocol.getAsJsonArray("required_loaded_class_paths")) {
                String path=item.getAsString(),resource="/"+path.substring(path.indexOf("com/tenicana/"));
                try(var stream=ManagerCatalogPublicationClientGameTest.class.getResourceAsStream(resource)) {
                    if(stream==null)throw new IllegalStateException("loaded class absent "+resource);
                    loaded.addProperty(path,sha(stream.readAllBytes()));
                }
            }
            report.add("loaded_class_sha256",loaded);
            byte[] encoded=bytes(root.resolve("fabric-mod/src/gametest/resources/assets/fpvdrone_gain_control/sounds/calibrated_160.ogg"));
            byte[] decoded=decodeReference(encoded);
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
                    for(int epoch=0;epoch<2;epoch++) {
                        // Context reload occurs only with no entity, manager set or selected channel alive.
                        if(epoch>0)context.runOnClient(client->{
                            if(!ManagerCatalogPublicationCapture.activeSets().isEmpty() || !handles(client).isEmpty())throw new IllegalStateException("context reload before empty cleanup");
                            client.getSoundManager().reload();
                        });
                        AcousticVector listener=awaitNativeListener(context,"EPOCH_"+epoch);
                        JsonObject row=protocol.getAsJsonArray("expected_cases").get(0).getAsJsonObject().deepCopy();row.addProperty("epoch",epoch);rows.add(row);
                        ManagerCatalogPublicationCapture.Session captured=captureCase(context,protocol,row,listener,encoded,decoded);
                        drain(context,"epoch_"+epoch+":A_TO_B");
                        row.add("clock",ManagerCatalogPublicationCapture.snapshot(captured));
                        context.runOnClient(client->{row.addProperty("handles_after_cleanup",handles(client).size());row.addProperty("manager_sets_after_cleanup",ManagerCatalogPublicationCapture.activeSets().size());});
                        onSoundThread(context,()->{sealInputs(row,captured);return null;});
                        if(captured.failure.get()!=null)throw new IllegalStateException("catalog observations failed during cleanup",captured.failure.get());
                        int finishedEpoch=epoch;context.runOnClient(client->controls.add("epoch_"+finishedEpoch+"_cleanup",membership(client)));
                    }
                } finally {
                    drain(context,"before_world_close");
                }
            }
            drain(context,"final");context.runOnClient(client->client.getSoundManager().destroy());
            report.addProperty("status","manager_catalog_publication_captured");
        } catch(Throwable error) {
            failure=error;report.addProperty("status","manager_catalog_publication_failed");report.addProperty("failure",error.toString());
            try {drain(context,"failure");context.runOnClient(client->client.getSoundManager().destroy());}
            catch(Throwable cleanup) {report.addProperty("cleanup_failure",cleanup.toString());}
        } finally {
            try {
                context.runOnClient(client->{
                    if(!ManagerCatalogPublicationCapture.activeSets().isEmpty() || !handles(client).isEmpty())throw new IllegalStateException("original catalog restoration before empty cleanup");
                    JsonObject restored=new JsonObject();restored.addProperty("before_catalog_identity",System.identityHashCode(CalibratedAssetResources.snapshot()));
                    new CalibratedAssetResources().apply(originalCatalog,client.getResourceManager(),Runnable::run).join();
                    restored.addProperty("original_catalog_identity",System.identityHashCode(originalCatalog));restored.addProperty("after_catalog_identity",System.identityHashCode(CalibratedAssetResources.snapshot()));
                    restored.addProperty("actual_client_thread",client.isSameThread());restored.addProperty("thread",Thread.currentThread().getName());controls.add("original_catalog_restoration",restored);
                    options.forEach((source,value)->client.options.getSoundSourceOptionInstance(source).set(value));
                });
            } catch(Throwable restore) {report.addProperty("restoration_failure",restore.toString());if(failure==null)failure=restore;report.addProperty("status","manager_catalog_publication_failed");}
        }
        report.add("rows",rows);report.add("controls",controls);report.add("listener_readiness",listenerReadiness);
        report.add("gain_observations",AssetNativeCapture.snapshot());report.add("envelope_observations",CalibratedGainCapture.snapshot());
        report.add("epochs",MainContextOutputCapture.epochs());report.add("runtime_events",DistanceOwnershipRuntimeTrace.snapshot());
        report.add("channel_allocations",WetChannelLifecycleCapture.allocations());report.add("channel_calls",WetChannelLifecycleCapture.calls());
        report.add("sound_admission",WetSoundAdmissionCapture.snapshot());
        JsonObject scope=new JsonObject();
        for(String name:List.of("actual_Minecraft_client_started","actual_minecraft_main_context_output","actual_client_world_entities",
                "actual_production_manager_selection","normal_resolved_asset_keys","actual_two_layer_calibrated_asset_streams",
                "test_constructed_numeric_catalogs","software_generated_asset_only","actual_production_catalog_apply_publication",
                "actual_production_failed_load_retention","natural_manager_rpm_zero_release","gametest_only_nonmutating_taps",
                "test_only_per_client_tick_loopback_clock","decoded_reference_shared_JOrbis_decoder","actual_sound_tick_before_after_observations"))scope.addProperty(name,true);
        for(String name:List.of("successful_resource_prepare_qualified","full_resource_pack_reload_selection_verified","measured_asset_profile_selected",
                "asset_physical_qualification","physical_endpoint_verified","real_pressure_or_rpm_accessed","ordinary_realtime_tick_playback_verified",
                "hardware_mixer_sample_atomicity_verified","catalog_publication_contract_passed","release_tail_contract_passed","audible_click_verified",
                "queued_sample_playback_tick_alignment_verified","legacy_before_tick_target_used_as_oracle","full_goal_complete"))scope.addProperty(name,false);
        report.add("scope",scope);
        writePcm(root,protocol,report,"input",input.toByteArray());writePcm(root,protocol,report,"output",output.toByteArray());
        report.addProperty("finished_utc",Instant.now().toString());
        try {Files.writeString(root.resolve(protocol.get("output_report").getAsString()),new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report)+"\n",StandardOpenOption.CREATE_NEW);}
        catch(IOException error) {throw new IllegalStateException(error);}
        if(failure!=null)throw new IllegalStateException("actual manager catalog publication capture failed",failure);
    }

    private static void validateProtocol(JsonObject p) {
        if(!p.get("manager_catalog_publication_experiment").getAsBoolean() || p.get("epochs").getAsInt()!=2 || p.get("render_step_frames").getAsInt()!=2400
                || p.get("old_entity_id").getAsInt()!=179001 || p.get("new_entity_id").getAsInt()!=179002
                || p.get("distance_m").getAsDouble()!=1 || p.get("synthetic_rpm").getAsFloat()!=12000)
            throw new IllegalArgumentException("catalog publication clock/stimulus differs from bounded fixture");
        JsonArray cases=p.getAsJsonArray("expected_cases");JsonObject gains=p.getAsJsonObject("catalog_gain_db");
        if(cases.size()!=1 || !cases.get(0).getAsJsonObject().get("name").getAsString().equals("A_TO_B") || gains.size()!=2
                || gains.get("A").getAsDouble()!=0 || gains.get("B").getAsDouble()!=24)
            throw new IllegalArgumentException("catalog publication requires exactly A=C1 then B=C24");
        JsonArray phases=p.getAsJsonArray("phase_sequence");if(phases.size()!=PHASE_NAMES.size())throw new IllegalArgumentException("wrong catalog phase count");
        for(int i=0;i<PHASE_NAMES.size();i++) {
            JsonObject phase=phases.get(i).getAsJsonObject();
            if(!phase.get("name").getAsString().equals(PHASE_NAMES.get(i)) || phase.get("old_rpm").getAsInt()!=OLD_RPM.get(i)
                    || phase.get("new_rpm").getAsInt()!=NEW_RPM.get(i) || phase.get("ticks").getAsInt()!=PHASE_TICKS.get(i))
                throw new IllegalArgumentException("wrong catalog phase "+i);
        }
    }

    private static ManagerCatalogPublicationCapture.Session captureCase(ClientGameTestContext context,JsonObject protocol,JsonObject row,AcousticVector listener,byte[] encoded,byte[] decoded) {
        String name="EPOCH_"+row.get("epoch").getAsInt()+":A_TO_B";
        AssetNativeCapture.phase(name+":ADMISSION");CalibratedGainCapture.phase(name+":ADMISSION");
        double cA=Math.pow(10,0/20.0),cB=Math.pow(10,24/20.0);final VerifiedAssetPcm pcmA,pcmB;
        try {pcmA=CalibratedAssetDecoder.decode(calibration(encoded,decoded,cA),encoded);pcmB=CalibratedAssetDecoder.decode(calibration(encoded,decoded,cB),encoded);}
        catch(IOException error){throw new IllegalStateException(error);}
        double peak=decodedPeak(decoded);row.addProperty("decoded_reference_peak",peak);row.addProperty("numeric_overlap_input_peak_bound",2*peak*(cA+cB));row.addProperty("numeric_restart_input_peak_bound",2*peak*cB);
        if(2*peak*(cA+cB)>=.95 || 2*peak*cB>=.95)throw new IllegalStateException("generated combined input lacks declared headroom");
        JsonArray catalogEvents=new JsonArray();row.add("catalog_events",catalogEvents);
        AtomicReference<CalibratedAssetResources.Catalog> a=new AtomicReference<>(),b=new AtomicReference<>();
        context.runOnClient(client->{
            oldProbe=new ProbeDrone(client.level,protocol.get("old_entity_id").getAsInt(),12000);
            newProbe=new ProbeDrone(client.level,protocol.get("new_entity_id").getAsInt(),0);
            for(ProbeDrone drone:List.of(oldProbe,newProbe))drone.setPos(listener.x(),listener.y()-drone.getBbHeight()*.5,listener.z()-protocol.get("distance_m").getAsDouble());
            a.set(catalog(pcmA));b.set(catalog(pcmB));JsonObject catalogs=new JsonObject();
            catalogs.add("A",catalogDescription("A",a.get(),pcmA,cA,0));catalogs.add("B",catalogDescription("B",b.get(),pcmB,cB,24));row.add("catalogs",catalogs);
            JsonObject entities=new JsonObject();entities.add("OLD",entityDescription(oldProbe));entities.add("NEW",entityDescription(newProbe));row.add("fixture_entities",entities);
            applyCatalog(client,"APPLY_A_INITIAL",a.get(),catalogEvents);
            ProbeDrone capturedOld=oldProbe,capturedNew=newProbe;
            activeCapture=ManagerCatalogPublicationCapture.begin(name,capturedOld,capturedNew,2400,
                    phase->phaseAction(phase,a.get(),b.get(),catalogEvents),
                    (oldRpm,newRpm)->{if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("RPM stimulus off client thread");capturedOld.rpm=oldRpm;capturedNew.rpm=newRpm;});
            WetSoundAdmissionCapture.live();client.level.addEntity(oldProbe);
        });
        context.waitFor(client->{
            if(!ManagerCatalogPublicationCapture.initialStreamsReady(activeCapture,client))return false;
            Object set=ManagerCatalogPublicationCapture.activeSets().get(oldProbe.getId());if(set==null)return false;
            for(String layer:List.of("motor","propeller")) {Object value=ManagerCatalogPublicationCapture.field(set,layer);if(!(value instanceof DroneLoopSoundInstance sound) || sound.getVolume()!=.99999994f)return false;}
            return true;
        },WAIT);
        onSoundThread(context,()->null);
        context.runOnClient(client->{
            Object set=ManagerCatalogPublicationCapture.activeSets().get(oldProbe.getId());
            if(ManagerCatalogPublicationCapture.field(set,"drone")!=oldProbe || client.level.getEntity(oldProbe.getId())!=oldProbe || client.level.getEntity(newProbe.getId())!=null
                    || ManagerCatalogPublicationCapture.activeSets().size()!=1 || handles(client).size()!=2)throw new IllegalStateException("initial OLD-only normal manager admission lost");
            verifyResolvedGenerations(activeCapture,a.get(),b.get(),pcmA,pcmB,false);row.add("membership_before_clock",membership(client));
        });
        onSoundThread(context,()->{identity(row);row.addProperty("version",AL10.alGetString(AL10.AL_VERSION));row.addProperty("native_listener_gain",AL10.alGetListenerf(AL10.AL_GAIN));row.addProperty("library_debug",((SoundEngineMainOutputAccessor)engine(Minecraft.getInstance())).mainOutput$getLibrary().getDebugString());return null;});
        context.runOnClient(client->ManagerCatalogPublicationCapture.startClock(activeCapture));
        context.waitFor(client->activeCapture.complete.isDone(),WAIT);onSoundThread(context,()->null);
        ManagerCatalogPublicationCapture.Session captured=activeCapture;
        context.runOnClient(client->{verifyResolvedGenerations(captured,a.get(),b.get(),pcmA,pcmB,true);row.add("membership_after_clock",membership(client));});
        row.add("clock",ManagerCatalogPublicationCapture.snapshot(captured));
        byte[] rendered=captured.output.toByteArray();row.addProperty("output_offset_bytes",output.size());row.addProperty("output_bytes",rendered.length);row.addProperty("output_sha256",sha(rendered));output.writeBytes(rendered);
        for(var generation:ManagerCatalogPublicationCapture.generations(captured))if(generation.channel==null || generation.stream==null)throw new IllegalStateException("selected generation has no actual calibrated stream");
        if(captured.failure.get()!=null)throw new IllegalStateException("per-tick native owner clock failed",captured.failure.get());
        return captured;
    }

    private static CalibratedAssetResources.Catalog catalog(VerifiedAssetPcm pcm) {
        Map<CalibratedAssetResources.BindingKey,VerifiedAssetPcm> bindings=new HashMap<>();
        for(ProbeDrone drone:List.of(oldProbe,newProbe))for(int i=0;i<2;i++)bindings.put(new CalibratedAssetResources.BindingKey(AcousticProfileResources.keyOf(drone),DroneLoopSoundInstance.Layer.values()[i].name(),NORMAL_RESOURCES.get(i)),pcm);
        return new CalibratedAssetResources.Catalog(bindings);
    }
    private static JsonObject catalogDescription(String name,CalibratedAssetResources.Catalog catalog,VerifiedAssetPcm pcm,double coefficient,double gainDb) {
        JsonObject row=new JsonObject();row.addProperty("name",name);row.addProperty("catalog_identity",System.identityHashCode(catalog));row.addProperty("asset_identity",System.identityHashCode(pcm));row.addProperty("coefficient",coefficient);row.addProperty("gain_db",gainDb);
        row.addProperty("actual_calibration_reference_amplitude",pcm.calibration().referenceAmplitude());
        JsonArray bindings=new JsonArray();for(var item:catalog.assets().entrySet()) {JsonObject e=new JsonObject();e.addProperty("profile_key",item.getKey().key().toString());e.addProperty("layer",item.getKey().layer());e.addProperty("resource",item.getKey().resource().toString());e.addProperty("asset_identity",System.identityHashCode(item.getValue()));bindings.add(e);}row.add("bindings",bindings);return row;
    }
    private static JsonObject entityDescription(ProbeDrone drone) {JsonObject row=new JsonObject();row.addProperty("entity_id",drone.getId());row.addProperty("entity_identity",System.identityHashCode(drone));row.addProperty("profile_key",AcousticProfileResources.keyOf(drone).toString());return row;}
    private static JsonObject actionEvent(Minecraft client,String action) {
        if(!client.isSameThread())throw new IllegalStateException("catalog/world phase action off actual client thread");
        JsonObject e=new JsonObject();e.addProperty("action",action);e.addProperty("clock_tick_index",activeCapture==null?-1:activeCapture.scheduledTicks);e.addProperty("thread",Thread.currentThread().getName());e.addProperty("actual_client_thread",true);
        if(activeCapture==null)e.add("start_order",JsonNull.INSTANCE);else e.addProperty("start_order",ManagerCatalogPublicationCapture.nextObservationOrder(activeCapture));
        e.addProperty("old_rpm_before",oldProbe==null?0:oldProbe.getAverageMotorRpm());e.addProperty("new_rpm_before",newProbe==null?0:newProbe.getAverageMotorRpm());
        e.addProperty("before_catalog_identity",System.identityHashCode(CalibratedAssetResources.snapshot()));e.add("membership_before",membership(client));return e;
    }
    private static void finishAction(Minecraft client,JsonObject event,JsonArray events) {
        event.addProperty("after_catalog_identity",System.identityHashCode(CalibratedAssetResources.snapshot()));event.add("membership_after",membership(client));
        event.addProperty("old_rpm_after",oldProbe==null?0:oldProbe.getAverageMotorRpm());event.addProperty("new_rpm_after",newProbe==null?0:newProbe.getAverageMotorRpm());
        if(activeCapture==null)event.add("end_order",JsonNull.INSTANCE);else event.addProperty("end_order",ManagerCatalogPublicationCapture.nextObservationOrder(activeCapture));events.add(event);
    }
    private static void applyCatalog(Minecraft client,String action,CalibratedAssetResources.Catalog next,JsonArray events) {
        JsonObject event=actionEvent(client,action);event.addProperty("next_catalog_identity",System.identityHashCode(next));event.addProperty("production_method","CalibratedAssetResources.apply");
        new CalibratedAssetResources().apply(next,client.getResourceManager(),Runnable::run).join();event.addProperty("apply_future_joined",true);finishAction(client,event,events);
        if(CalibratedAssetResources.snapshot()!=next)throw new IllegalStateException("actual catalog apply did not publish requested object");
    }
    private static void phaseAction(String phase,CalibratedAssetResources.Catalog a,CalibratedAssetResources.Catalog b,JsonArray events) {
        Minecraft client=Minecraft.getInstance();if(!client.isSameThread())throw new IllegalStateException("phase publication off client thread");
        switch(phase) {
            case "WARM_A" -> {if(CalibratedAssetResources.snapshot()!=a)throw new IllegalStateException("A changed before warm clock");}
            case "PUBLISH_B_OLD" -> applyCatalog(client,phase,b,events);
            case "ADMIT_NEW_B" -> {
                JsonObject event=actionEvent(client,phase);
                if(client.level.getEntity(newProbe.getId())!=null || CalibratedAssetResources.snapshot()!=b)throw new IllegalStateException("NEW admitted before B publication");
                event.addProperty("admitted_entity_id",newProbe.getId());event.addProperty("admitted_entity_identity",System.identityHashCode(newProbe));client.level.addEntity(newProbe);
                finishAction(client,event,events);
            }
            case "REJECT_B" -> {
                JsonObject event=actionEvent(client,phase);JsonArray calls=new JsonArray();event.add("broken_resource_calls",calls);
                ResourceManager broken=(ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},(proxy,method,args)->{
                    JsonObject call=new JsonObject();call.addProperty("method",method.getName());call.addProperty("thread",Thread.currentThread().getName());calls.add(call);throw new IllegalStateException("D179 deliberately broken resource manager");
                });
                event.addProperty("broken_resource_manager_identity",System.identityHashCode(broken));event.addProperty("production_load_method","CalibratedAssetResources.load");
                var loader=new CalibratedAssetResources();var retained=loader.load(broken,Runnable::run).join();event.addProperty("load_future_joined",true);event.addProperty("returned_catalog_identity",System.identityHashCode(retained));
                if(retained!=b || CalibratedAssetResources.snapshot()!=b)throw new IllegalStateException("failed actual load did not retain same B object");
                event.addProperty("next_catalog_identity",System.identityHashCode(retained));event.addProperty("production_apply_method","CalibratedAssetResources.apply");loader.apply(retained,client.getResourceManager(),Runnable::run).join();event.addProperty("apply_future_joined",true);
                finishAction(client,event,events);if(CalibratedAssetResources.snapshot()!=b)throw new IllegalStateException("retained B apply changed object");
            }
            case "ZERO_BOTH","RESTART_OLD_B","FINAL_ZERO" -> {if(CalibratedAssetResources.snapshot()!=b)throw new IllegalStateException("B catalog changed during lifecycle stimulus");}
            default -> throw new IllegalArgumentException("unknown declared phase "+phase);
        }
    }

    private static void verifyResolvedGenerations(ManagerCatalogPublicationCapture.Session capture,CalibratedAssetResources.Catalog a,CalibratedAssetResources.Catalog b,VerifiedAssetPcm pcmA,VerifiedAssetPcm pcmB,boolean finalCheck) {
        var generations=ManagerCatalogPublicationCapture.generations(capture);if(generations.size()!=(finalCheck?6:2))throw new IllegalStateException("incomplete normal manager generation bindings");
        for(var generation:generations) {
            DroneLoopSoundInstance sound=generation.sound;int i=generation.generation;String role=i<2||i>=4?"OLD":"NEW";
            if(!generation.role.equals(role) || generation.drone!=(role.equals("OLD")?oldProbe:newProbe) || generation.setGeneration!=i/2
                    || sound.usesProceduralAudio() || ManagerCatalogPublicationCapture.field(sound,"assetCatalog")!=(i<2?a:b)
                    || ManagerCatalogPublicationCapture.field(sound,"calibratedAsset")!=(i<2?pcmA:pcmB)
                    || !NORMAL_RESOURCES.get(sound.layer().ordinal()).equals(ManagerCatalogPublicationCapture.field(sound,"calibratedResource")))
                throw new IllegalStateException("actual catalog/asset/entity resolution differs for generation "+i);
        }
    }
    private static void sealInputs(JsonObject row,ManagerCatalogPublicationCapture.Session capture) {
        var generations=ManagerCatalogPublicationCapture.generations(capture);JsonArray bindings=row.getAsJsonObject("clock").getAsJsonArray("generations");
        if(bindings.size()!=generations.size())throw new IllegalStateException("generation snapshot incomplete");
        for(int i=0;i<generations.size();i++) {
            var generation=generations.get(i);JsonObject binding=bindings.get(i).getAsJsonObject();var stream=generation.stream;
            if(generation.channel==null || stream==null)throw new IllegalStateException("cannot seal unattached selected generation");
            binding.addProperty("stream_closed_after_cleanup",AssetNativeCapture.closed(stream));if(!AssetNativeCapture.closed(stream))throw new IllegalStateException("selected cursor not closed");
            byte[] data=MainContextOutputCapture.input(stream);binding.addProperty("input_offset_bytes",input.size());binding.addProperty("input_bytes",data.length);binding.addProperty("input_sha256",sha(data));binding.addProperty("input_record_identity",MainContextOutputCapture.identity(stream));
            binding.add("asset_read_batches",AssetNativeCapture.reads(stream));input.writeBytes(data);binding.add("native_after_cleanup",ManagerCatalogPublicationCapture.nativeSnapshot(generation.channel));
        }
        row.addProperty("inputs_sealed_after_cleanup",true);row.addProperty("library_debug_after_cleanup",((SoundEngineMainOutputAccessor)engine(Minecraft.getInstance())).mainOutput$getLibrary().getDebugString());
    }
    private static JsonObject membership(Minecraft client) {
        JsonObject row=new JsonObject();row.addProperty("manager_set_count",ManagerCatalogPublicationCapture.activeSets().size());row.addProperty("actual_handle_count",handles(client).size());
        JsonArray entities=new JsonArray();if(client.level!=null)for(Entity e:client.level.entitiesForRendering())if(e instanceof DroneEntity)entities.add(e.getId());row.add("world_drone_ids",entities);
        JsonArray roles=new JsonArray();for(int i=0;i<2;i++) {ProbeDrone drone=i==0?oldProbe:newProbe;JsonObject e=new JsonObject();e.addProperty("role",i==0?"OLD":"NEW");
            Object set=drone==null?null:ManagerCatalogPublicationCapture.activeSets().get(drone.getId());e.addProperty("entity_id",drone==null?0:drone.getId());e.addProperty("expected_entity_identity",drone==null?0:System.identityHashCode(drone));
            e.addProperty("world_entity_identity",drone==null||client.level==null?0:System.identityHashCode(client.level.getEntity(drone.getId())));e.addProperty("manager_entity_identity",set==null?0:System.identityHashCode(ManagerCatalogPublicationCapture.field(set,"drone")));e.addProperty("manager_set_identity",set==null?0:System.identityHashCode(set));roles.add(e);
        }row.add("roles",roles);return row;
    }
    private static void drain(ClientGameTestContext context,String reason) {
        ManagerCatalogPublicationCapture.Session abandoned=activeCapture;
        if(abandoned!=null) {context.runOnClient(client->{ManagerCatalogPublicationCapture.markCleanup(abandoned);ManagerCatalogPublicationCapture.disarm();});onSoundThread(context,()->null);}
        context.runOnClient(client->{
            WetSoundAdmissionCapture.close(reason);if(client.level!=null) {if(oldProbe!=null)client.level.removeEntity(oldProbe.getId(),Entity.RemovalReason.DISCARDED);if(newProbe!=null)client.level.removeEntity(newProbe.getId(),Entity.RemovalReason.DISCARDED);}oldProbe=null;newProbe=null;
            try {Method stop=DroneSoundManager.class.getDeclaredMethod("stopAll",Minecraft.class);stop.setAccessible(true);stop.invoke(null,client);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        });
        context.waitFor(client->WetSoundAdmissionCapture.drained(),WAIT);onSoundThread(context,()->null);onSoundThread(context,()->{WetSoundAdmissionCapture.reclaimUnattached();return null;});
        context.runOnClient(client->engine(client).stopAll());onSoundThread(context,()->null);context.runOnClient(client->WetSoundAdmissionCapture.barrier(handles(client).size()));
        if(abandoned!=null) {ManagerCatalogPublicationCapture.abandonAfterOwnerBarrier(abandoned);activeCapture=null;}
    }
    private static AcousticVector awaitNativeListener(ClientGameTestContext context,String name) {
        JsonObject group=new JsonObject();JsonArray attempts=new JsonArray();group.addProperty("name",name);group.add("attempts",attempts);listenerReadiness.add(group);
        for(int attempt=0;attempt<WAIT;attempt++) {
            context.waitTick();AtomicReference<AcousticCameraListener.Sample> sample=new AtomicReference<>();context.runOnClient(client->sample.set(((AcousticCameraListener)ManagerCatalogPublicationCapture.field(DroneSoundManager.class,"CAMERA_LISTENER")).latestFor(client.level)));
            if(sample.get()==null)continue;var expected=sample.get();ListenerReadback readback=onSoundThread(context,()->{
                FloatBuffer buffer=BufferUtils.createFloatBuffer(3);AL10.alGetListenerfv(AL10.AL_POSITION,buffer);AcousticVector position=new AcousticVector(buffer.get(0),buffer.get(1),buffer.get(2));var transform=engine(Minecraft.getInstance()).getListenerTransform().position();
                JsonObject nativeRow=new JsonObject();identity(nativeRow);nativeRow.add("camera_position",vector(expected.frame().positionMeters()));nativeRow.add("native_position",vector(position));nativeRow.addProperty("ready",position.subtract(expected.frame().positionMeters()).length()<=1e-5&&new AcousticVector(transform.x,transform.y,transform.z).subtract(expected.frame().positionMeters()).length()<=1e-5);
                nativeRow.addProperty("al_error",AL10.alGetError());if(nativeRow.get("al_error").getAsInt()!=0)throw new IllegalStateException("native listener getter failed");return new ListenerReadback(position,nativeRow);
            });readback.observation().addProperty("attempt",attempt);attempts.add(readback.observation());if(readback.observation().get("ready").getAsBoolean()){group.addProperty("ready",true);return readback.position();}
        }throw new IllegalStateException("native listener did not acknowledge camera pose");
    }
    private static AudioAssetCalibration calibration(byte[] encoded,byte[] decoded,double coefficient) {
        double squares=0;var shorts=ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();while(shorts.hasRemaining()){double x=shorts.get()/32768.0;squares+=x*x;}double rms=Math.sqrt(squares/(decoded.length/2));
        return new AudioAssetCalibration(sha(encoded),sha(decoded),48000,decoded.length/2,0,decoded.length/2,rms,rms*coefficient,1,12000,0,List.of(8000.0,12000.0,16000.0),List.of(-1.0,0.0,1.0),List.of(List.of(0.0,0.0,0.0),List.of(0.0,0.0,0.0),List.of(0.0,0.0,0.0)));
    }
    private static double decodedPeak(byte[] decoded) {double peak=0;var s=ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();while(s.hasRemaining())peak=Math.max(peak,Math.abs(s.get()/32768.0));return peak;}
    private static byte[] decodeReference(byte[] encoded) {try(var decoder=new JOrbisAudioStream(new ByteArrayInputStream(encoded))) {ByteArrayOutputStream out=new ByteArrayOutputStream();while(true){ByteBuffer chunk=decoder.read(8192);if(!chunk.hasRemaining())break;byte[] b=new byte[chunk.remaining()];chunk.get(b);out.writeBytes(b);}return out.toByteArray();}catch(IOException error){throw new IllegalStateException(error);}}
    private static SoundEngine engine(Minecraft client) {return ((SoundManagerEngineAccessor)client.getSoundManager()).fpvdrone$getSoundEngine();}
    private static Map<SoundInstance,ChannelAccess.ChannelHandle> handles(Minecraft client) {return ((SoundEngineChannelsAccessor)engine(client)).fpvdrone$getInstanceToChannel();}
    private static MainOutputLibraryAccessor library() {return (MainOutputLibraryAccessor)((SoundEngineMainOutputAccessor)engine(Minecraft.getInstance())).mainOutput$getLibrary();}
    private static <T> T onSoundThread(ClientGameTestContext context,Supplier<T> action) {CompletableFuture<T> done=new CompletableFuture<>();context.runOnClient(client->((SoundEngineExecutorAccessor)engine(client)).fpvdrone$getExecutor().schedule(()->{try{done.complete(action.get());}catch(Throwable error){done.completeExceptionally(error);}}));context.waitFor(client->done.isDone(),WAIT);return done.join();}
    private static JsonArray vector(AcousticVector v) {JsonArray a=new JsonArray();a.add(v.x());a.add(v.y());a.add(v.z());return a;}
    private static void identity(JsonObject row) {var l=library();row.addProperty("device",l.mainOutput$getDevice());row.addProperty("context",l.mainOutput$getContext());row.addProperty("generation",MainContextOutputCapture.generation(l.mainOutput$getDevice(),l.mainOutput$getContext()));}
    private static byte[] bytes(Path path) {try{return Files.readAllBytes(path);}catch(IOException error){throw new IllegalStateException(error);}}
    private static String sha(byte[] data) {return MainContextOutputCapture.sha(data);}
    private static void writePcm(Path root,JsonObject protocol,JsonObject report,String name,byte[] bytes) {try{Files.write(root.resolve(protocol.get(name+"_pcm_file").getAsString()),bytes,StandardOpenOption.CREATE_NEW);report.addProperty(name+"_pcm_bytes",bytes.length);report.addProperty(name+"_pcm_sha256",sha(bytes));}catch(IOException error){throw new IllegalStateException(error);}}
}
