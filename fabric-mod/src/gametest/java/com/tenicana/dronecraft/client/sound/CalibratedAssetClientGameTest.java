package com.tenicana.dronecraft.client.sound;

import com.google.gson.*;
import com.tenicana.dronecraft.acoustics.*;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Actual decoder/instance, injected numerical catalog only; no invented measurement qualification. */
final class CalibratedAssetClientGameTest {
    static final String PROPERTY="fpvdrone.acoustics.calibratedAssetProtocol";
    private static final String BACKEND="fpvdrone.proceduralAudio";
    private static final class ProbeDrone extends DroneEntity {
        float rpm=12000;
        ProbeDrone(ClientLevel level) { super(DroneEntityTypes.DRONE,level); setId(160001); }
        @Override public float getAverageMotorRpm() { return rpm; }
    }
    static boolean requested() { return System.getProperty(PROPERTY)!=null; }
    static void run(ClientGameTestContext context) {
        Path contract=Path.of(System.getProperty(PROPERTY));
        Path output=Path.of(System.getProperty("fpvdrone.acoustics.calibratedAssetOutput"));
        Path pcmFile=Path.of(output.toString().replace(".json",".pcm"));
        if(Files.exists(output)||Files.exists(pcmFile)) throw new IllegalStateException("write-once output exists");
        JsonObject protocol=JsonParser.parseString(read(contract)).getAsJsonObject();
        JsonObject report=new JsonObject(); JsonArray rows=new JsonArray(),errors=new JsonArray();
        ByteArrayOutputStream pcm=new ByteArrayOutputStream(); String previous=System.getProperty(BACKEND); Throwable failure=null;
        report.addProperty("started_utc",Instant.now().toString()); report.addProperty("protocol_sha256",sha(bytes(contract)));
        report.addProperty("metadata_contract_sha256",sha(bytes(contract.resolveSibling("calibrated-asset-contract-d160-v2.json"))));
        report.add("scope",protocol.get("scope").deepCopy()); report.addProperty("test_injected_numeric_catalog",true);
        report.addProperty("production_resolve_select_and_stream_exercised",true);
        try {
            JsonObject loaded=new JsonObject();
            for(Class<?> type:List.of(AudioAssetCalibration.class,VerifiedAssetPcm.class,AudioAssetProfile.class,
                    AudioAssetProfileJsonDecoder.class,CalibratedAssetDecoder.class,CalibratedAssetAudioStream.class,
                    CalibratedAssetResources.class,CalibratedAssetResources.Catalog.class,DroneLoopSoundInstance.class,
                    AcousticProfileResources.class,JOrbisAudioStream.class,CalibratedAssetClientGameTest.class,ProbeDrone.class)) {
                try(var stream=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")) {
                    if(stream==null) throw new IOException("missing class "+type); loaded.addProperty(type.getName(),sha(stream.readAllBytes()));
                }
            }
            report.add("loaded_class_sha256",loaded);
            try(TestSingleplayerContext world=context.worldBuilder().create()) {
                world.getClientWorld().waitForChunksDownload(); world.getServer().runCommand("gamerule spawn_mobs false");
                context.runOnClient(client-> {
                    Identifier resource=Identifier.parse(protocol.getAsJsonObject("stimulus").get("resource").getAsString());
                    byte[] encoded;
                    try(var stream=client.getResourceManager().open(resource)) { encoded=stream.readAllBytes(); }
                    catch(IOException e) { throw new IllegalStateException(e); }
                    byte[] decoded=decodeReference(encoded);
                    report.addProperty("encoded_sha256",sha(encoded)); report.addProperty("encoded_bytes",encoded.length);
                    report.addProperty("decoded_sha256",sha(decoded)); report.addProperty("decoded_frames",decoded.length/2);
                    store(report,"decoded",decoded,pcm);
                    var reference=calibration(encoded,decoded,0,1.0);
                    decoderNegatives(reference,encoded,report,errors);
                    try {
                        if(!CalibratedAssetResources.prepare(client.getResourceManager()).assets().isEmpty()) throw new IllegalStateException("unexpected qualified asset catalog");
                    } catch(IOException e) { throw new IllegalStateException(e); }
                    for(JsonElement layer:protocol.getAsJsonArray("layers"))
                        for(JsonElement db:protocol.getAsJsonArray("gains_db"))
                            for(JsonElement repeat:protocol.getAsJsonArray("repeat"))
                                rows.add(capture(client,resource,encoded,decoded,layer.getAsString(),db.getAsDouble(),repeat.getAsBoolean(),protocol,pcm,errors));
                    failedReloadRetention(reference,encoded,report,errors);
                });
            }
        } catch(Throwable e) { failure=e; report.addProperty("failure",e.toString()); }
        finally { if(previous==null) System.clearProperty(BACKEND); else System.setProperty(BACKEND,previous); }
        report.add("rows",rows); report.add("gate_errors",errors);
        report.addProperty("gate_passed",failure==null&&errors.isEmpty()&&rows.size()==16);
        report.addProperty("pcm_sha256",sha(pcm.toByteArray())); report.addProperty("pcm_bytes",pcm.size());
        report.addProperty("finished_utc",Instant.now().toString());
        try { Files.write(pcmFile,pcm.toByteArray(),StandardOpenOption.CREATE_NEW);
            Files.writeString(output,new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report)+"\n",StandardOpenOption.CREATE_NEW); }
        catch(IOException e) { throw new IllegalStateException(e); }
        if(failure!=null||!errors.isEmpty()) throw new IllegalStateException("calibrated asset gate failed",failure);
    }
    private static JsonObject capture(Minecraft client,Identifier resource,byte[] encoded,byte[] decoded,String layerName,
                                      double db,boolean repeat,JsonObject protocol,ByteArrayOutputStream pcm,JsonArray errors) {
        JsonObject row=new JsonObject(); row.addProperty("layer",layerName); row.addProperty("gain_db",db); row.addProperty("repeat",repeat);
        var reference=calibration(encoded,decoded,db,1.0); VerifiedAssetPcm asset;
        try { asset=CalibratedAssetDecoder.decode(reference,encoded); } catch(IOException e) { throw new IllegalStateException(e); }
        row.addProperty("reference_rms",reference.referenceDigitalRms()); row.addProperty("reference_pressure_pa",reference.referencePressureRmsPa());
        row.addProperty("reference_amplitude",reference.referenceAmplitude());
        var drone=new ProbeDrone(client.level); var state=state(); var layer=DroneLoopSoundInstance.Layer.valueOf(layerName);
        System.setProperty(BACKEND,"false"); var sound=new DroneLoopSoundInstance(drone,layer,state);
        var key=AcousticProfileResources.keyOf(drone);
        put(sound,"assetCatalog",new CalibratedAssetResources.Catalog(Map.of(new CalibratedAssetResources.BindingKey(key,layerName,resource),asset)));
        put(sound,"identifier",Identifier.parse("fpvdrone_gain_control:calibrated_160"));
        if(sound.resolve(client.getSoundManager())==null) throw new IllegalStateException("fixture event absent");
        System.setProperty(BACKEND,"true");
        AudioStream first=null,fresh=null;
        try {
            first=sound.getAudioStream(new SoundBufferLibrary(client.getResourceManager()),resource,repeat).join();
            fresh=sound.getAudioStream(new SoundBufferLibrary(client.getResourceManager()),resource,repeat).join();
            row.addProperty("stream_class",first.getClass().getName()); row.addProperty("procedural_backend",sound.usesProceduralAudio());
            var ownership=ProceduralDistanceOwner.onStreamAttached(first,-1);
            row.addProperty("distance_owner_result",ownership.status().name());
            row.addProperty("procedural_distance_owner",ownership.status()!=ProceduralDistanceOwner.Status.BYPASS);
            row.addProperty("resolved_resource",sound.getSound().getPath().toString());
            store(row,"first",buffer(first.read(protocol.get("requested_pcm_bytes").getAsInt())),pcm);
            store(row,"fresh",buffer(fresh.read(protocol.get("requested_pcm_bytes").getAsInt())),pcm);
            JsonArray attack=new JsonArray();
            for(int i=0;i<protocol.get("attack_ticks").getAsInt();i++) attack.add(tick(sound,1,errors));
            row.add("attack",attack); JsonArray chunks=new JsonArray();
            for(int i=0;i<protocol.get("read_count").getAsInt();i++) { JsonObject chunk=new JsonObject(); store(chunk,"pcm",buffer(first.read(protocol.get("requested_pcm_bytes").getAsInt())),pcm); chunks.add(chunk); }
            row.add("chunks",chunks);
            drone.rpm=16001; row.add("out_of_domain",tick(sound,0,errors)); row.addProperty("retained_out_of_domain_amplitude",number(sound,"assetAmplitude"));
            drone.rpm=0; JsonArray release=new JsonArray();
            for(int i=0;i<protocol.get("release_ticks").getAsInt();i++) release.add(tick(sound,0,errors));
            row.add("release",release); row.addProperty("released_volume",number(sound,"volume"));
            row.addProperty("retained_release_amplitude",number(sound,"assetAmplitude"));
            fresh.close(); row.addProperty("closed_read_bytes",fresh.read(8192).remaining());
            try { sound.getAudioStream(new SoundBufferLibrary(client.getResourceManager()),Identifier.parse("fpvdrone_gain_control:sounds/wrong.ogg"),repeat).join(); errors.add("wrong_identifier_accepted"); }
            catch(java.util.concurrent.CompletionException expected) { row.addProperty("wrong_identifier_rejected",true); }
        } catch(Exception e) { throw new IllegalStateException("asset case "+layerName+" "+db,e); }
        finally { try { if(first!=null)first.close(); if(fresh!=null)fresh.close(); } catch(Exception e){throw new IllegalStateException(e);} sound.end(); }
        return row;
    }
    private static JsonObject tick(DroneLoopSoundInstance sound,float target,JsonArray errors) {
        float before=(float)number(sound,"volume"),expected=before+(target-before)*(target>before?.30f:.16f);
        if(target==0&&expected<.0005f) expected=0;
        sound.tick(); float after=(float)number(sound,"volume");
        JsonObject result=new JsonObject(); result.addProperty("before",before); result.addProperty("after",after);
        result.addProperty("effective_volume",sound.getVolume()); result.addProperty("effective_pitch",sound.getPitch());
        if(Float.floatToIntBits(sound.getVolume())!=Float.floatToIntBits(after)||sound.getPitch()!=1) errors.add("event_metadata_gain_or_pitch_leaked");
        if(Float.floatToIntBits(expected)!=Float.floatToIntBits(after)) errors.add("envelope_mismatch"); return result;
    }
    private static DroneAcousticRenderState state() {
        var zero=new AcousticVector(0,0,0); var source=new AcousticSourceFrame(160001,0,zero,zero,new AcousticVector(0,1,0),.127,
                List.of(new RotorAcousticState(12000,.5,.5,.0635,3,7,1,0)));
        var silent=new AcousticEmissionFrame(List.of(),AcousticBands.SILENT); var state=new DroneAcousticRenderState();
        put(state,"audioSnapshot",new DroneAcousticRenderState.AudioSnapshot(source,new AcousticListenerFrame(new AcousticVector(0,0,1),zero),
                silent,silent,1,343,new AcousticSourceProfile.Calibration(true,true,24,24)));
        return state;
    }
    private static AudioAssetCalibration calibration(byte[] encoded,byte[] decoded,double db,double rmsMultiplier) {
        var shorts=ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer(); double squares=0;
        while(shorts.hasRemaining()) { double x=shorts.get()/32768.0; squares+=x*x; }
        double rms=Math.sqrt(squares/(decoded.length/2))*rmsMultiplier;
        return new AudioAssetCalibration(sha(encoded),sha(decoded),48000,decoded.length/2,0,decoded.length/2,rms,rms*Math.pow(10,db/20),1,
                12000,0,List.of(8000.0,12000.0,16000.0),List.of(-1.0,0.0,1.0),List.of(List.of(0.0,0.0,0.0),List.of(0.0,0.0,0.0),List.of(0.0,0.0,0.0)));
    }
    private static byte[] decodeReference(byte[] encoded) {
        try(var decoder=new JOrbisAudioStream(new ByteArrayInputStream(encoded))) {
            var pcm=new ByteArrayOutputStream(); while(true) { var chunk=decoder.read(8192); if(!chunk.hasRemaining())break; pcm.writeBytes(buffer(chunk)); } return pcm.toByteArray();
        } catch(IOException e) { throw new IllegalStateException(e); }
    }
    private static void decoderNegatives(AudioAssetCalibration reference,byte[] encoded,JsonObject report,JsonArray errors) {
        JsonArray rejected=new JsonArray(); byte[] changed=encoded.clone(); changed[100]^=1;
        try { CalibratedAssetDecoder.decode(reference,changed); errors.add("encoded_mutation_accepted"); }
        catch(Exception e) { rejected.add("encoded_hash"); }
        var wrong=new AudioAssetCalibration(reference.encodedSha256(),"0".repeat(64),48000,reference.frames(),0,reference.frames(),
                reference.referenceDigitalRms(),reference.referencePressureRmsPa(),1,12000,0,reference.rpmAnchors(),reference.axisCosineAnchors(),reference.responseGainDb());
        try { CalibratedAssetDecoder.decode(wrong,encoded); errors.add("decoded_hash_accepted"); }
        catch(Exception e) { rejected.add("decoded_hash"); }
        try { CalibratedAssetDecoder.decode(calibration(encoded,decodeReference(encoded),0,1.01),encoded); errors.add("wrong_rms_accepted"); }
        catch(Exception e) { rejected.add("reference_rms"); }
        report.add("decoder_rejected_controls",rejected);
    }
    private static void failedReloadRetention(AudioAssetCalibration reference,byte[] encoded,JsonObject report,JsonArray errors) {
        var previous=CalibratedAssetResources.snapshot();
        try {
            var asset=CalibratedAssetDecoder.decode(reference,encoded);
            var key=new CalibratedAssetResources.BindingKey(AcousticProfileKey.fromMetres("test_quad",4,3,.0635,7),"MOTOR",Identifier.parse("fpvdrone_gain_control:sounds/calibrated_160.ogg"));
            var numeric=new CalibratedAssetResources.Catalog(Map.of(key,asset));
            putStatic(CalibratedAssetResources.class,"catalog",numeric);
            ResourceManager broken=(ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},
                    (proxy,method,args)->{throw new IllegalStateException("synthetic resource failure");});
            var loader=new CalibratedAssetResources(); var retained=loader.load(broken,Runnable::run).join();
            loader.apply(retained,broken,Runnable::run).join();
            boolean same=retained==numeric&&CalibratedAssetResources.snapshot()==numeric&&retained.assets().get(key)==asset;
            report.addProperty("failed_reload_retained_same_pcm_and_coefficients",same); if(!same)errors.add("reload_retention");
        } catch(Exception e) { throw new IllegalStateException(e); }
        finally { putStatic(CalibratedAssetResources.class,"catalog",previous); }
    }
    private static void put(Object target,String name,Object value) {
        try { Field f=find(target.getClass(),name); f.setAccessible(true); f.set(target,value); } catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void putStatic(Class<?> type,String name,Object value) { try { Field f=type.getDeclaredField(name); f.setAccessible(true); f.set(null,value); } catch(Exception e){throw new IllegalStateException(e);} }
    private static Field find(Class<?> type,String name) throws NoSuchFieldException { while(type!=null) { try{return type.getDeclaredField(name);}catch(NoSuchFieldException e){type=type.getSuperclass();} } throw new NoSuchFieldException(name); }
    private static double number(Object target,String name) { try { Field f=find(target.getClass(),name); f.setAccessible(true); return ((Number)f.get(target)).doubleValue(); } catch(Exception e){throw new IllegalStateException(e);} }
    private static byte[] buffer(ByteBuffer data) { byte[] bytes=new byte[data.remaining()];data.get(bytes);return bytes; }
    private static String read(Path path) { return new String(bytes(path),java.nio.charset.StandardCharsets.UTF_8); }
    private static byte[] bytes(Path path) { try{return Files.readAllBytes(path);}catch(IOException e){throw new IllegalStateException(e);} }
    private static String sha(byte[] data) { return VerifiedAssetPcm.sha256(data); }
    private static void store(JsonObject target,String name,byte[] bytes,ByteArrayOutputStream pcm) {
        JsonObject value=new JsonObject(); value.addProperty("offset",pcm.size());value.addProperty("bytes",bytes.length);value.addProperty("sha256",sha(bytes));target.add(name,value);pcm.writeBytes(bytes);
    }
}
