package com.tenicana.dronecraft.client.sound;

import com.google.gson.*;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.client.mixin.*;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Genuine manager/engine lifecycle with only the calibrated stream delivery latency injected. */
final class ManagerAssetLateDeliveryClientGameTest {
    private static final int WAIT=600,MAX_READ_BYTES=8*1024*1024,MAX_READ_BATCHES=1024;
    private static final String ENCODED="fabric-mod/src/gametest/resources/assets/fpvdrone_gain_control/sounds/calibrated_160.ogg";
    private static final String ENCODED_SHA="a50cc62c14de32045c12c94e7de0880cb50749b55a223386c3d1c69c8bc3ebda";
    private static final List<Identifier> RESOURCES=List.of(Identifier.parse("fpvdrone:sounds/drone/motor_loop.ogg"),Identifier.parse("fpvdrone:sounds/drone/propeller_loop.ogg"));
    private static final List<Integer> INITIAL=List.of(182001,182002,182003,182004,182005,182006);
    private static final int HELD=182006,NEWCOMER=182007;
    private static JsonObject declaration;
    private static final class ProbeDrone extends DroneEntity {
        ProbeDrone(ClientLevel level,int id){super(DroneEntityTypes.DRONE,level);setId(id);}
        @Override public float getAverageMotorRpm(){return 12000;}
        @Override public float getMotorRpm(int index){return 12000;}
        @Override public float getMotorPower(){return .5f;}
        @Override public float getMotorPower(int index){return .5f;}
        @Override public void tick(){tickCount++;}
    }

    static boolean requested(){return System.getProperty(ManagerAssetLateDeliveryCapture.PROPERTY)!=null && protocol().get("manager_late_asset_delivery_experiment").getAsBoolean();}

    private static synchronized JsonObject protocol(){
        if(declaration!=null)return declaration;
        Path path=Path.of(System.getProperty(ManagerAssetLateDeliveryCapture.PROPERTY));JsonObject p=JsonParser.parseString(new String(bytes(path),StandardCharsets.UTF_8)).getAsJsonObject();Path root=Path.of(p.get("workspace_root").getAsString());
        if(!p.get("manager_late_asset_delivery_experiment").getAsBoolean() || p.get("epochs").getAsInt()!=2 || p.get("warm_manager_ticks").getAsInt()!=6)throw new IllegalStateException("wrong D182 experiment declaration");
        List<Integer> ids=new ArrayList<>();p.getAsJsonArray("initial_entity_ids").forEach(value->ids.add(value.getAsInt()));if(!ids.equals(INITIAL) || p.get("delayed_entity_id").getAsInt()!=HELD || p.get("newcomer_entity_id").getAsInt()!=NEWCOMER)throw new IllegalStateException("wrong D182 real entity identities");
        if(p.get("synthetic_rpm").getAsInt()!=12000 || p.get("generated_reference_frames").getAsInt()!=48000 || p.get("calibration_gain_db").getAsInt()!=0 || p.get("full_scale_pressure_pa").getAsDouble()!=1)throw new IllegalStateException("wrong D182 numeric stimulus");
        if(p.get("maximum_read_bytes_per_stream").getAsInt()!=MAX_READ_BYTES || p.get("maximum_read_batches_per_stream").getAsInt()!=MAX_READ_BATCHES)throw new IllegalStateException("wrong D182 bounded read trace limits");
        if(p.has("input_pcm_file") || p.has("output_pcm_file"))throw new IllegalStateException("D182 observes input lifetime and does not render an output waveform");
        for(var e:p.getAsJsonObject("evidence_sha256").entrySet())if(!sha(bytes(root.resolve(e.getKey()))).equals(e.getValue().getAsString()))throw new IllegalStateException("D182 evidence changed "+e.getKey());
        for(String key:List.of("output_report","decoded_reference_pcm_file"))if(Files.exists(root.resolve(p.get(key).getAsString())))throw new IllegalStateException("refusing existing D182 output "+key);
        if(!"null".equals(System.getenv("ALSOFT_DRIVERS")))throw new IllegalStateException("D182 requires the declared NullAL driver");
        if(!"false".equals(System.getProperty("fpvdrone.proceduralAudio")) || Boolean.getBoolean("fpvdrone.listenerReverb") || Boolean.getBoolean("fpvdrone.openalEfx") || Boolean.getBoolean(NativeCallbackBinding.ENABLE_PROPERTY))throw new IllegalStateException("D182 must use the normal dry calibrated queued path");
        declaration=p;return p;
    }

    static void run(ClientGameTestContext context){
        JsonObject p=protocol(),report=new JsonObject(),controls=new JsonObject();Path root=Path.of(p.get("workspace_root").getAsString());JsonArray rows=new JsonArray();
        EnumMap<SoundSource,Double> savedOptions=new EnumMap<>(SoundSource.class);var original=CalibratedAssetResources.snapshot();Throwable failure=null;
        report.addProperty("started_utc",Instant.now().toString());report.addProperty("protocol_path",System.getProperty(ManagerAssetLateDeliveryCapture.PROPERTY));report.addProperty("protocol_sha256",sha(bytes(Path.of(System.getProperty(ManagerAssetLateDeliveryCapture.PROPERTY)))));report.add("evidence_sha256",p.get("evidence_sha256").deepCopy());
        try{
            JsonObject forbidden=new JsonObject();for(String property:List.of(AssetNativeCapture.PROPERTY,CalibratedGainCapture.PROPERTY,MainContextOutputCapture.PROPERTY,"fpvdrone.acoustics.wetOutputProtocol","fpvdrone.acoustics.assetCatalogGuardProtocol")){String value=System.getProperty(property);forbidden.add(property,value==null?JsonNull.INSTANCE:new JsonPrimitive(value));if(value!=null)throw new IllegalStateException("old waveform/phase-guard property active "+property);}controls.add("forbidden_old_properties",forbidden);
            controls.addProperty("old_main_output_capture_requested",MainContextOutputCapture.requested());controls.addProperty("old_wet_admission_guard_requested",WetMainOutputCapture.requested());controls.addProperty("old_asset_capture_requested",AssetNativeCapture.requested());controls.addProperty("old_calibrated_gain_capture_requested",CalibratedGainCapture.requested());
            if(WetMainOutputCapture.requested() || MainContextOutputCapture.requested() || AssetNativeCapture.requested() || CalibratedGainCapture.requested())throw new IllegalStateException("old guarded capture pipeline active");
            JsonObject loaded=new JsonObject();for(var value:p.getAsJsonArray("required_loaded_class_paths")){String path=value.getAsString();int start=path.indexOf("com/tenicana/");if(start<0 || !path.endsWith(".class"))throw new IllegalStateException("bad raw class resource path "+path);try(var stream=ManagerAssetLateDeliveryClientGameTest.class.getResourceAsStream("/"+path.substring(start))){if(stream==null)throw new IllegalStateException("missing actual loaded raw resource "+path);loaded.addProperty(path,sha(stream.readAllBytes()));}}report.add("loaded_class_sha256",loaded);
            byte[] encoded=bytes(root.resolve(ENCODED));if(!sha(encoded).equals(ENCODED_SHA))throw new IllegalStateException("generated D160 encoded control changed");byte[] decoded=decode(encoded);if(decoded.length!=96000)throw new IllegalStateException("D182 reference must be exactly 48000 mono S16 frames");
            Files.write(root.resolve(p.get("decoded_reference_pcm_file").getAsString()),decoded,StandardOpenOption.CREATE_NEW);report.addProperty("encoded_reference_path",ENCODED);report.addProperty("encoded_reference_sha256",sha(encoded));report.addProperty("decoded_reference_pcm_path",p.get("decoded_reference_pcm_file").getAsString());report.addProperty("decoded_reference_pcm_bytes",decoded.length);report.addProperty("decoded_reference_pcm_sha256",sha(decoded));report.addProperty("decoded_reference_pcm_frames",decoded.length/2);report.addProperty("decoded_reference_pcm_format","mono-S16LE-48000Hz");
            VerifiedAssetPcm pcm=CalibratedAssetDecoder.decode(calibration(encoded,decoded),encoded);report.add("numeric_asset_descriptor",assetRecord(pcm));
            context.runOnClient(client->{if(!original.assets().isEmpty())throw new IllegalStateException("D182 must restore the genuine original empty catalog");for(SoundSource source:SoundSource.values()){savedOptions.put(source,client.options.getSoundSourceOptionInstance(source).get());client.options.getSoundSourceOptionInstance(source).set(source==SoundSource.MASTER||source==SoundSource.NEUTRAL?1.0:0.0);}client.getSoundManager().updateCategoryVolume(SoundSource.NEUTRAL,1);controls.addProperty("actual_client_thread",client.isSameThread());controls.addProperty("backend_selector",AcousticBackendSelector.mode().token());if(AcousticBackendSelector.mode()!=AcousticBackendSelector.Mode.DEFAULT)throw new IllegalStateException("D182 must retain the normal backend selector");});
            try(TestSingleplayerContext world=context.worldBuilder().create()){
                world.getClientWorld().waitForChunksDownload();world.getServer().runCommand("gamerule spawn_mobs false");initialDrain(context);
                for(int epoch=0;epoch<2;epoch++){
                    if(epoch>0){JsonObject reload=new JsonObject();context.runOnClient(client->{reload.addProperty("before_library_context",ManagerAssetLateDeliveryCapture.library().mainOutput$getContext());reload.addProperty("actual_client_thread",client.isSameThread());reload.addProperty("production_method","SoundManager.reload");client.getSoundManager().reload();});onOwner(context,()->{ManagerAssetLateDeliveryCapture.identity(reload);return null;});controls.add("epoch_"+epoch+"_reload",reload);}
                    JsonObject row=new JsonObject();row.addProperty("epoch",epoch);row.addProperty("name","INJECTED_LATE_DELIVERY");rows.add(row);captureEpoch(context,p,row,pcm);
                }
            }
            context.runOnClient(client->client.getSoundManager().destroy());report.addProperty("status","manager_late_asset_delivery_captured");
        }catch(Throwable error){
            failure=error;report.addProperty("status","manager_late_asset_delivery_failed");report.addProperty("failure",error.toString());
            try{initialDrain(context);context.runOnClient(client->client.getSoundManager().destroy());}
            catch(Throwable cleanup){report.addProperty("final_failure_cleanup_error",cleanup.toString());}
        }
        finally{
            try{context.runOnClient(client->{if(!ManagerAssetLateDeliveryCapture.activeSets().isEmpty() || !handles().isEmpty())throw new IllegalStateException("catalog restoration before actual manager/engine cleanup");JsonObject r=new JsonObject();r.addProperty("before_catalog_identity",id(CalibratedAssetResources.snapshot()));r.addProperty("original_catalog_identity",id(original));new CalibratedAssetResources().apply(original,client.getResourceManager(),Runnable::run).join();r.addProperty("after_catalog_identity",id(CalibratedAssetResources.snapshot()));r.addProperty("original_empty_catalog_restored",CalibratedAssetResources.snapshot()==original);r.addProperty("actual_client_thread",client.isSameThread());controls.add("original_catalog_restoration",r);savedOptions.forEach((source,value)->client.options.getSoundSourceOptionInstance(source).set(value));});}catch(Throwable error){report.addProperty("restoration_failure",error.toString());if(failure==null)failure=error;report.addProperty("status","manager_late_asset_delivery_failed");}
        }
        report.add("rows",rows);report.add("controls",controls);report.add("library_lifecycle_events",ManagerAssetLateDeliveryCapture.libraryEvents());report.add("scope",p.get("scope").deepCopy());report.addProperty("producer_semantic_passed",false);report.addProperty("normal_completedFuture_latency_counterexample_claimed",false);report.addProperty("finished_utc",Instant.now().toString());
        try{Files.writeString(root.resolve(p.get("output_report").getAsString()),new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report)+"\n",StandardOpenOption.CREATE_NEW);}catch(IOException error){throw new IllegalStateException(error);}
        if(failure!=null)throw new IllegalStateException("D182 transport/order capture failed; retained partial observations",failure);
    }

    private static void captureEpoch(ClientGameTestContext context,JsonObject p,JsonObject row,VerifiedAssetPcm pcm){
        List<ProbeDrone> drones=new ArrayList<>();AtomicReference<ManagerAssetLateDeliveryCapture.Session> session=new AtomicReference<>();AcousticVector listener=awaitListener(context,row);
        Throwable failure=null;
        try{
            context.runOnClient(client->{
                if(!ManagerAssetLateDeliveryCapture.activeSets().isEmpty() || !handles().isEmpty())throw new IllegalStateException("late epoch admission before empty baseline");
                for(int i=0;i<7;i++){int entityId=i<6?INITIAL.get(i):NEWCOMER;ProbeDrone d=new ProbeDrone(client.level,entityId);double distance=entityId==HELD?3:entityId==NEWCOMER?1:2;double x=entityId==HELD||entityId==NEWCOMER?0:(i-2)*.05;d.setPos(listener.x()+x,listener.y()-d.getBbHeight()*.5,listener.z()-distance);drones.add(d);}
                Map<CalibratedAssetResources.BindingKey,VerifiedAssetPcm> bindings=new HashMap<>();for(ProbeDrone d:drones)for(int layer=0;layer<2;layer++)bindings.put(new CalibratedAssetResources.BindingKey(AcousticProfileResources.keyOf(d),DroneLoopSoundInstance.Layer.values()[layer].name(),RESOURCES.get(layer)),pcm);
                var catalog=new CalibratedAssetResources.Catalog(bindings);new CalibratedAssetResources().apply(catalog,client.getResourceManager(),Runnable::run).join();if(CalibratedAssetResources.snapshot()!=catalog)throw new IllegalStateException("actual production catalog apply did not publish the exact test catalog");
                row.addProperty("catalog_identity",id(catalog));row.addProperty("asset_identity",id(pcm));row.addProperty("actual_catalog_apply_client_thread",client.isSameThread());row.addProperty("production_catalog_apply_method","CalibratedAssetResources.apply");row.addProperty("coefficient",pcm.calibration().referenceAmplitude());row.addProperty("calibration_full_scale_pressure_pa",pcm.calibration().fullScalePressurePa());row.addProperty("internal_propagation_enabled",DroneSoundManager.internalPropagationEnabled());
                JsonArray entities=new JsonArray();for(ProbeDrone d:drones){JsonObject x=new JsonObject();x.addProperty("entity_id",d.getId());x.addProperty("entity_identity",id(d));x.addProperty("profile_key",AcousticProfileResources.keyOf(d).toString());x.addProperty("initial_world_admission",d.getId()!=NEWCOMER);x.addProperty("relative_requested_distance_m",d.getId()==HELD?3:d.getId()==NEWCOMER?1:2);entities.add(x);}row.add("fixture_entities",entities);
                List<Integer> ids=new ArrayList<>(INITIAL);ids.add(NEWCOMER);session.set(ManagerAssetLateDeliveryCapture.beginEpoch(row.get("epoch").getAsInt(),HELD,NEWCOMER,ids,p.get("warm_manager_ticks").getAsInt(),MAX_READ_BYTES,MAX_READ_BATCHES,pcm,catalog));
                for(int i=0;i<6;i++)client.level.addEntity(drones.get(i));
            });
            var s=session.get();context.waitFor(client->ManagerAssetLateDeliveryCapture.warmReady(s),WAIT);
            AtomicReference<CompletableFuture<JsonObject>> warm=new AtomicReference<>();context.runOnClient(client->warm.set(ManagerAssetLateDeliveryCapture.warmNative(s)));context.waitFor(client->warm.get().isDone(),WAIT);warm.get().join();
            context.runOnClient(client->{ManagerAssetLateDeliveryCapture.armEviction(s);row.addProperty("newcomer_world_admission_actual_client_thread",client.isSameThread());client.level.addEntity(drones.get(6));});
            context.waitFor(client->s.primaryComplete.isDone(),WAIT);s.primaryComplete.join();
        }catch(Throwable error){failure=error;row.addProperty("capture_failure",error.toString());}
        finally{
            var s=session.get();if(s!=null){
                try{cleanup(context,drones,s,row);}catch(Throwable error){row.addProperty("cleanup_failure",error.toString());if(failure==null)failure=error;}
                row.add("trace",ManagerAssetLateDeliveryCapture.snapshot(s));
                try{context.runOnClient(client->ManagerAssetLateDeliveryCapture.finish(s));}catch(Throwable error){row.addProperty("finish_failure",error.toString());if(failure==null)failure=error;}
            }
        }
        if(failure!=null)throw new IllegalStateException("late-delivery epoch failed",failure);
    }

    private static void cleanup(ClientGameTestContext context,List<ProbeDrone> drones,ManagerAssetLateDeliveryCapture.Session s,JsonObject row){
        context.runOnClient(client->{ManagerAssetLateDeliveryCapture.markCleanup(s);if(client.level!=null)for(ProbeDrone d:drones)client.level.removeEntity(d.getId(),Entity.RemovalReason.DISCARDED);invokeManagerStop(client);});
        onOwner(context,()->null);context.runOnClient(client->ManagerAssetLateDeliveryCapture.engine().stopAll());
        onOwner(context,()->{ManagerAssetLateDeliveryCapture.cleanupNative(s);ManagerAssetLateDeliveryCapture.closeUnattachedAfterPrimaryAndStopAll(s);row.addProperty("library_debug_after_cleanup",((SoundEngineMainOutputAccessor)ManagerAssetLateDeliveryCapture.engine()).mainOutput$getLibrary().getDebugString());return null;});
        context.runOnClient(client->{row.addProperty("handles_after_cleanup",handles().size());row.addProperty("manager_sets_after_cleanup",ManagerAssetLateDeliveryCapture.activeSets().size());JsonArray world=new JsonArray();for(ProbeDrone d:drones){JsonObject x=new JsonObject();x.addProperty("entity_id",d.getId());x.addProperty("world_contains_expected_entity",client.level!=null&&client.level.getEntity(d.getId())==d);world.add(x);}row.add("world_after_cleanup",world);});
    }

    private static void initialDrain(ClientGameTestContext context){context.runOnClient(client->invokeManagerStop(client));onOwner(context,()->null);context.runOnClient(client->ManagerAssetLateDeliveryCapture.engine().stopAll());onOwner(context,()->null);}
    private static void invokeManagerStop(Minecraft client){try{Method m=DroneSoundManager.class.getDeclaredMethod("stopAll",Minecraft.class);m.setAccessible(true);m.invoke(null,client);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static AcousticVector awaitListener(ClientGameTestContext context,JsonObject row){
        JsonArray attempts=new JsonArray();row.add("listener_readiness",attempts);
        for(int i=0;i<WAIT;i++){context.waitTick();AtomicReference<AcousticCameraListener.Sample> sample=new AtomicReference<>();context.runOnClient(client->sample.set(((AcousticCameraListener)ManagerAssetLateDeliveryCapture.field(DroneSoundManager.class,"CAMERA_LISTENER")).latestFor(client.level)));if(sample.get()==null)continue;var expected=sample.get();JsonObject nativeRow=onOwner(context,()->{FloatBuffer b=BufferUtils.createFloatBuffer(3);AL10.alGetListenerfv(AL10.AL_POSITION,b);AcousticVector actual=new AcousticVector(b.get(0),b.get(1),b.get(2));var transform=ManagerAssetLateDeliveryCapture.engine().getListenerTransform().position();JsonObject r=new JsonObject();ManagerAssetLateDeliveryCapture.identity(r);r.add("camera_position",vector(expected.frame().positionMeters()));r.add("native_position",vector(actual));r.addProperty("ready",actual.subtract(expected.frame().positionMeters()).length()<=1e-5&&new AcousticVector(transform.x,transform.y,transform.z).subtract(expected.frame().positionMeters()).length()<=1e-5);r.addProperty("al_error",AL10.alGetError());if(r.get("al_error").getAsInt()!=0)throw new IllegalStateException("late listener native getter failed");return r;});nativeRow.addProperty("attempt",i);attempts.add(nativeRow);if(nativeRow.get("ready").getAsBoolean())return expected.frame().positionMeters();}throw new IllegalStateException("actual camera/native listener did not agree");
    }

    private static AudioAssetCalibration calibration(byte[] encoded,byte[] decoded){double squares=0;var b=ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();while(b.hasRemaining()){double x=b.get()/32768.0;squares+=x*x;}double rms=Math.sqrt(squares/(decoded.length/2));return new AudioAssetCalibration(sha(encoded),sha(decoded),48000,decoded.length/2,0,decoded.length/2,rms,rms,1,12000,0,List.of(8000.0,12000.0,16000.0),List.of(-1.0,0.0,1.0),List.of(List.of(0.0,0.0,0.0),List.of(0.0,0.0,0.0),List.of(0.0,0.0,0.0)));}
    private static JsonObject assetRecord(VerifiedAssetPcm pcm){JsonObject r=new JsonObject();var c=pcm.calibration();r.addProperty("asset_identity",id(pcm));r.addProperty("encoded_sha256",c.encodedSha256());r.addProperty("decoded_sha256",c.decodedSha256());r.addProperty("sample_rate",c.sampleRate());r.addProperty("frames",c.frames());r.addProperty("reference_start_frame",c.referenceStartFrame());r.addProperty("reference_frame_count",c.referenceFrameCount());r.addProperty("reference_digital_rms",c.referenceDigitalRms());r.addProperty("reference_pressure_rms_pa",c.referencePressureRmsPa());r.addProperty("full_scale_pressure_pa",c.fullScalePressurePa());r.addProperty("reference_rpm",c.referenceRpm());r.addProperty("reference_axis_cosine",c.referenceAxisCosine());r.addProperty("reference_amplitude",c.referenceAmplitude());r.addProperty("maximum_amplitude",c.maximumAmplitude());r.addProperty("maximum_quantized_absolute_sample",pcm.maximumQuantizedAbsoluteSample());r.addProperty("twelve_input_peak_bound",12*pcm.maximumQuantizedAbsoluteSample()/32768.0);
        JsonArray rpms=new JsonArray(),axes=new JsonArray(),matrix=new JsonArray();c.rpmAnchors().forEach(rpms::add);c.axisCosineAnchors().forEach(axes::add);for(var response:c.responseGainDb()){JsonArray values=new JsonArray();response.forEach(values::add);matrix.add(values);}r.add("rpm_points",rpms);r.add("axis_cosines",axes);r.add("response_gain_db",matrix);return r;}
    private static byte[] decode(byte[] encoded)throws IOException{try(var decoder=new JOrbisAudioStream(new ByteArrayInputStream(encoded))){var f=decoder.getFormat();if(f.getChannels()!=1 || f.getSampleRate()!=48000 || f.getSampleSizeInBits()!=16 || f.isBigEndian() || !f.getEncoding().toString().equals("PCM_SIGNED"))throw new IOException("wrong generated reference format");ByteArrayOutputStream out=new ByteArrayOutputStream();while(true){ByteBuffer b=decoder.read(8192);if(!b.hasRemaining())break;byte[] data=new byte[b.remaining()];b.get(data);out.writeBytes(data);}return out.toByteArray();}}
    private static Map<SoundInstance,ChannelAccess.ChannelHandle> handles(){return ((SoundEngineChannelsAccessor)ManagerAssetLateDeliveryCapture.engine()).fpvdrone$getInstanceToChannel();}
    private static <T>T onOwner(ClientGameTestContext context,Supplier<T> action){CompletableFuture<T> done=new CompletableFuture<>();context.runOnClient(client->((SoundEngineExecutorAccessor)ManagerAssetLateDeliveryCapture.engine()).fpvdrone$getExecutor().schedule(()->{try{done.complete(action.get());}catch(Throwable error){done.completeExceptionally(error);}}));context.waitFor(client->done.isDone(),WAIT);return done.join();}
    private static JsonArray vector(AcousticVector v){JsonArray r=new JsonArray();r.add(v.x());r.add(v.y());r.add(v.z());return r;}
    private static byte[] bytes(Path p){try{return Files.readAllBytes(p);}catch(IOException error){throw new IllegalStateException(error);}}
    private static String sha(byte[] data){return ManagerAssetLateDeliveryCapture.sha(data);}
    private static int id(Object object){return ManagerAssetLateDeliveryCapture.id(object);}
}
