package com.tenicana.dronecraft.client.sound;
import com.google.gson.*;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.acoustics.*;
import com.tenicana.dronecraft.client.mixin.*;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import com.tenicana.dronecraft.sound.DroneSoundEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Controlled numeric fixture through the real SoundEngine and its actual main context. */
final class AssetNativeClientGameTest {
    private static final int WAIT=600,FRAMES=48000,ENTITY=161001;
    private static final ByteArrayOutputStream output=new ByteArrayOutputStream(),input=new ByteArrayOutputStream();
    private static final JsonArray listenerReadiness=new JsonArray(),faults=new JsonArray();
    private static final List<PendingInput> pending=new ArrayList<>();
    private record PendingInput(JsonObject row,AudioStream stream) { }
    private record ListenerReadback(AcousticVector position,JsonObject observation) { }
    private static final class ProbeDrone extends DroneEntity {
        volatile float rpm=12000;
        ProbeDrone(ClientLevel level) {super(DroneEntityTypes.DRONE,level);setId(ENTITY);}
        @Override public float getAverageMotorRpm() {return rpm;}
    }
    private record Fixture(ProbeDrone drone,List<DroneLoopSoundInstance> sounds) { }
    static void run(ClientGameTestContext context) {
        JsonObject protocol=MainContextOutputCapture.protocol();Path root=Path.of(protocol.get("workspace_root").getAsString());
        JsonObject report=new JsonObject(),controls=new JsonObject();JsonArray rows=new JsonArray();Throwable failure=null;
        report.addProperty("started_utc",Instant.now().toString());report.addProperty("protocol_sha256",sha(bytes(Path.of(System.getProperty(AssetNativeCapture.PROPERTY)))));
        EnumMap<SoundSource,Double> options=new EnumMap<>(SoundSource.class);
        byte[] encoded=bytes(root.resolve("fabric-mod/src/gametest/resources/assets/fpvdrone_gain_control/sounds/calibrated_160.ogg"));
        byte[] decoded=decodeReference(encoded);report.addProperty("decoded_reference_sha256",sha(decoded));report.addProperty("decoded_reference_frames",decoded.length/2);
        try {
            JsonObject loaded=new JsonObject();
            for(Class<?> type:List.of(AssetNativeClientGameTest.class,ProbeDrone.class,Fixture.class,PendingInput.class,ListenerReadback.class,AssetNativeCapture.class,
                    DroneLoopSoundInstance.class,CalibratedAssetAudioStream.class,CalibratedAssetDecoder.class,VerifiedAssetPcm.class,AudioAssetCalibration.class,ProceduralDistanceOwner.class,
                    MainContextOutputCapture.class,WetSoundAdmissionCapture.class,WetChannelLifecycleCapture.class,JOrbisAudioStream.class)) {
                String resource="/"+type.getName().replace('.','/')+".class";
                try(var stream=type.getResourceAsStream(resource)) {if(stream==null)throw new IllegalStateException("class bytes absent "+resource);loaded.addProperty(type.getName(),sha(stream.readAllBytes()));}
            }
            report.add("loaded_class_sha256",loaded);
            context.runOnClient(client->{for(SoundSource s:SoundSource.values()){options.put(s,client.options.getSoundSourceOptionInstance(s).get());client.options.getSoundSourceOptionInstance(s).set(s==SoundSource.MASTER||s==SoundSource.NEUTRAL?1.0:0.0);}client.getSoundManager().updateCategoryVolume(SoundSource.NEUTRAL,1);SoundInstance probe=new ForeignProbe();WetSoundAdmissionCapture.probe("closed_music",probe,client.getSoundManager().play(probe));});
            try(TestSingleplayerContext world=context.worldBuilder().create()) {
                world.getClientWorld().waitForChunksDownload();world.getServer().runCommand("gamerule spawn_mobs false");
                closeAndDrain(context,"world_ready");controls.add("no_source",silence(context));
                int index=0;for(var item:protocol.getAsJsonArray("expected_cases"))rows.add(captureCase(context,item.getAsJsonObject(),encoded,decoded,index++==0,controls));
                setOptions(context,1,1);context.runOnClient(c->c.getSoundManager().reload());
                controls.add("reload_anchor",captureCase(context,protocol.getAsJsonObject("reload_anchor"),encoded,decoded,false,controls));
            }
            closeAndDrain(context,"final");context.runOnClient(c->c.getSoundManager().destroy());
            if(faults.size()!=0)throw new IllegalStateException("native owner gate rejected "+faults.size()+" observations");
            report.addProperty("status","asset_native_captured");
        } catch(Throwable e) {failure=e;report.addProperty("status","asset_native_failed");report.addProperty("failure",e.toString());try{context.runOnClient(c->c.getSoundManager().destroy());}catch(Throwable clean){report.addProperty("cleanup_failure",clean.toString());}}
        finally {context.runOnClient(c->options.forEach((s,v)->c.options.getSoundSourceOptionInstance(s).set(v)));}
        report.add("rows",rows);report.add("controls",controls);report.add("native_owner_faults",faults);report.add("listener_readiness",listenerReadiness);
        report.add("gain_observations",AssetNativeCapture.snapshot());report.add("epochs",MainContextOutputCapture.epochs());report.add("runtime_events",DistanceOwnershipRuntimeTrace.snapshot());
        report.add("channel_allocations",WetChannelLifecycleCapture.allocations());report.add("channel_calls",WetChannelLifecycleCapture.calls());report.add("sound_admission",WetSoundAdmissionCapture.snapshot());
        var scope=new JsonObject();for(String n:List.of("actual_Minecraft_client_started","actual_minecraft_main_context_output","software_generated_asset_only","controlled_client_entity_not_in_world","real_production_sound_instances","gametest_only_nonmutating_taps"))scope.addProperty(n,true);
        for(String n:List.of("measured_asset_profile_selected","asset_physical_qualification","physical_endpoint_verified","real_pressure_or_rpm_accessed","queued_sample_playback_tick_alignment_verified","six_drone_full_client_verified","full_goal_complete"))scope.addProperty(n,false);report.add("scope",scope);
        writePcm(root,protocol,report,"input",input.toByteArray());writePcm(root,protocol,report,"output",output.toByteArray());report.addProperty("finished_utc",Instant.now().toString());
        try{Files.writeString(root.resolve(protocol.get("output_report").getAsString()),new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report)+"\n",StandardOpenOption.CREATE_NEW);}catch(Exception e){throw new IllegalStateException(e);}
        if(failure!=null)throw new IllegalStateException("asset native capture failed",failure);
    }
    private static Fixture fixture(Minecraft client,JsonObject declared,AcousticVector pos,byte[] encoded,byte[] decoded) {
        var drone=new ProbeDrone(client.level);double distance=declared.get("distance_m").getAsDouble();drone.setPos(pos.x(),pos.y()-drone.getBbHeight()*.5,pos.z()-distance);
        var zero=new AcousticVector(0,0,0);var source=new AcousticSourceFrame(ENTITY,0,new AcousticVector(drone.getX(),drone.getY()+drone.getBbHeight()*.5,drone.getZ()),zero,new AcousticVector(0,1,0),.127,List.of(new RotorAcousticState(12000,.5,.5,.0635,3,7,1,0)));
        var state=new DroneAcousticRenderState();var silent=new AcousticEmissionFrame(List.of(),AcousticBands.SILENT);
        // A numerical calibration exercises software transfer; no independent measured profile is fabricated.
        put(state,"audioSnapshot",new DroneAcousticRenderState.AudioSnapshot(source,new AcousticListenerFrame(pos,zero),silent,silent,1,343,new AcousticSourceProfile.Calibration(true,true,24,24)));
        var resource=Identifier.parse(MainContextOutputCapture.protocol().get("fixture_resource").getAsString());var key=AcousticProfileResources.keyOf(drone);List<DroneLoopSoundInstance> sounds=new ArrayList<>();
        var cal=calibration(encoded,decoded,20*Math.log10(declared.get("coefficient").getAsDouble()),1);
        final VerifiedAssetPcm pcm;try{pcm=CalibratedAssetDecoder.decode(cal,encoded);}catch(IOException e){throw new IllegalStateException(e);}
        for(var item:declared.getAsJsonArray("layers")) {
            var layer=DroneLoopSoundInstance.Layer.valueOf(item.getAsString());var sound=new DroneLoopSoundInstance(drone,layer,state);
            put(sound,"assetCatalog",new CalibratedAssetResources.Catalog(Map.of(new CalibratedAssetResources.BindingKey(key,layer.name(),resource),pcm)));
            put(sound,"identifier",Identifier.parse("fpvdrone_gain_control:calibrated_160"));sounds.add(sound);
        }return new Fixture(drone,List.copyOf(sounds));
    }
    private static JsonObject captureCase(ClientGameTestContext context,JsonObject declared,byte[] encoded,byte[] decoded,boolean release,JsonObject controls) {
        String name=declared.get("name").getAsString();AssetNativeCapture.phase(name);setOptions(context,declared.get("master").getAsDouble(),declared.get("category").getAsDouble());
        int ready=listenerReadiness.size();AcousticVector pos=awaitNativeListener(context,name);AtomicReference<Fixture> ref=new AtomicReference<>();
        context.runOnClient(c->{var f=fixture(c,declared,pos,encoded,decoded);ref.set(f);WetSoundAdmissionCapture.exact(name,new ArrayList<SoundInstance>(f.sounds()));if(release){var p=new DroneLoopSoundInstance(f.drone(),DroneLoopSoundInstance.Layer.MOTOR,new DroneAcousticRenderState());WetSoundAdmissionCapture.probe("unregistered_same_class",p,c.getSoundManager().play(p));}f.sounds().forEach(c.getSoundManager()::play);});
        var f=ref.get();context.waitFor(c->f.sounds().stream().allMatch(s->s.getVolume()==.99999994f&&handles(c).containsKey(s)),WAIT);
        List<ChannelAccess.ChannelHandle> selected=new ArrayList<>();context.runOnClient(c->f.sounds().forEach(s->selected.add(handles(c).get(s))));List<Channel> channels=selected.stream().map(AssetNativeClientGameTest::channel).toList();
        onSoundThread(context,()->{render(channels,MainContextOutputCapture.protocol().get("warmup_frames").getAsInt());return null;});
        JsonObject row=declared.deepCopy();row.addProperty("listener_readiness_index",ready);
        onSoundThread(context,()->{capture(row,channels,f.sounds(),true);return null;});
        if(release){AssetNativeCapture.phase(name+":RELEASE");context.runOnClient(c->f.drone().rpm=0);context.waitFor(c->f.sounds().stream().allMatch(s->s.getVolume()==0),WAIT);var tail=new JsonObject();int settle=MainContextOutputCapture.protocol().has("release_settle_frames")?MainContextOutputCapture.protocol().get("release_settle_frames").getAsInt():0;tail.addProperty("settle_frames",settle);onSoundThread(context,()->{if(settle>0)render(channels,settle);capture(tail,channels,f.sounds(),false);return null;});controls.add("release",tail);}
        closeAndDrain(context,name);return row;
    }
    private static void capture(JsonObject row,List<Channel> channels,List<DroneLoopSoundInstance> sounds,boolean collect) {
        identity(row);row.addProperty("render_frames",FRAMES);row.addProperty("active_playing_source_count",channels.size());if(handles(Minecraft.getInstance()).size()!=channels.size())throw new IllegalStateException("voice isolation lost");
        JsonArray snapshots=new JsonArray();for(int i=0;i<channels.size();i++){var ch=channels.get(i);var sound=sounds.get(i);var s=new JsonObject();s.addProperty("source",((ChannelSourceAccessor)ch).fpvdrone$getSource());s.addProperty("channel_allocation_identity",WetChannelLifecycleCapture.identity(ch));s.addProperty("sound_identity",System.identityHashCode(sound));s.addProperty("layer",sound.layer().name());s.addProperty("sound_instance_volume",sound.getVolume());s.addProperty("sound_instance_pitch",sound.getPitch());s.addProperty("procedural_backend",sound.usesProceduralAudio());s.add("before",nativeSnapshot(ch,sound));snapshots.add(s);}
        recordOptions(row);appendOutput(row,render(channels,FRAMES));
        for(int i=0;i<channels.size();i++){var s=snapshots.get(i).getAsJsonObject();s.add("after",nativeSnapshot(channels.get(i),sounds.get(i)));var stream=((MainOutputChannelAccessor)channels.get(i)).mainOutput$getStream();if(!(stream instanceof CalibratedAssetAudioStream))throw new IllegalStateException("wrong actual asset stream");
            if(collect){pending.add(new PendingInput(s,stream));var b=s.getAsJsonObject("before");if(b.get("source_model").getAsInt()!=53250||b.get("reference").getAsFloat()!=1||b.get("maximum").getAsFloat()!=1000000||b.get("rolloff").getAsFloat()!=1)faults.add(row.has("name")?row.get("name").getAsString():"control");}}
        row.add("source_snapshots",snapshots);
    }
    private static void sealInputs() {
        for(var p:pending){var s=p.stream();var r=p.row();boolean closed=(Boolean)CalibratedGainCapture.field(CalibratedGainCapture.field(s,"cursor"),"closed");r.addProperty("stream_closed_after_cleanup",closed);if(!closed)throw new IllegalStateException("asset stream not closed");byte[] bytes=MainContextOutputCapture.input(s);r.addProperty("input_offset_bytes",input.size());r.addProperty("input_bytes",bytes.length);r.addProperty("input_sha256",sha(bytes));r.addProperty("input_record_identity",MainContextOutputCapture.identity(s));r.addProperty("stream_identity",System.identityHashCode(s));r.addProperty("stream_class",s.getClass().getName());r.add("asset_read_batches",AssetNativeCapture.reads(s));input.writeBytes(bytes);}pending.clear();
    }
    private static void closeAndDrain(ClientGameTestContext context,String reason) {
        context.runOnClient(c->WetSoundAdmissionCapture.close(reason));context.waitFor(c->WetSoundAdmissionCapture.drained(),WAIT);onSoundThread(context,()->null);if(!WetSoundAdmissionCapture.drained())throw new IllegalStateException("old executor pending");onSoundThread(context,()->{WetSoundAdmissionCapture.reclaimUnattached();return null;});context.runOnClient(c->engine(c).stopAll());onSoundThread(context,()->{sealInputs();return null;});context.runOnClient(c->WetSoundAdmissionCapture.barrier(handles(c).size()));
    }
    private static JsonObject silence(ClientGameTestContext context) {return onSoundThread(context,()->{var r=new JsonObject();identity(r);r.addProperty("active_source_count",handles(Minecraft.getInstance()).size());if(!handles(Minecraft.getInstance()).isEmpty())throw new IllegalStateException("no source not empty");appendOutput(r,render(List.of(),FRAMES));return r;});}
    private static final class ForeignProbe extends AbstractTickableSoundInstance {
        ForeignProbe(){super(DroneSoundEvents.MOTOR_LOOP,SoundSource.MUSIC,SoundInstance.createUnseededRandom());volume=0;}
        @Override public void tick() { }
        @Override public boolean canStartSilent(){return true;}
    }
    private static void writePcm(Path root,JsonObject protocol,JsonObject report,String name,byte[] bytes) {try{Files.write(root.resolve(protocol.get(name+"_pcm_file").getAsString()),bytes,StandardOpenOption.CREATE_NEW);report.addProperty(name+"_pcm_sha256",sha(bytes));report.addProperty(name+"_pcm_bytes",bytes.length);}catch(IOException e){throw new IllegalStateException(e);}}
    private static SoundEngine engine(Minecraft client) { return ((SoundManagerEngineAccessor) client.getSoundManager()).fpvdrone$getSoundEngine(); }
    private static Map<SoundInstance, ChannelAccess.ChannelHandle> handles(Minecraft client) { return ((SoundEngineChannelsAccessor) engine(client)).fpvdrone$getInstanceToChannel(); }
    private static Channel channel(ChannelAccess.ChannelHandle h) { Channel c = ((ChannelHandleChannelAccessor) h).fpvdrone$getChannel(); if (c == null) throw new IllegalStateException("retired channel"); return c; }
    private static <T> T onSoundThread(ClientGameTestContext context, Supplier<T> action) {
        CompletableFuture<T> done = new CompletableFuture<>(); context.runOnClient(client -> ((SoundEngineExecutorAccessor) engine(client)).fpvdrone$getExecutor().schedule(() -> { try { done.complete(action.get()); } catch (Throwable e) { done.completeExceptionally(e); } }));
        context.waitFor(client -> done.isDone(), WAIT); return done.join();
    }
    private static MainOutputLibraryAccessor library() { return (MainOutputLibraryAccessor) ((SoundEngineMainOutputAccessor) engine(Minecraft.getInstance())).mainOutput$getLibrary(); }
    private static byte[] render(List<Channel> channels, int frames) {
        var l = library(); MainContextOutputCapture.requireCurrent(l.mainOutput$getDevice(), l.mainOutput$getContext()); ByteBuffer out = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int f = 0; f < frames; f += 1024) {
            int count = Math.min(1024, frames - f); FloatBuffer samples = BufferUtils.createFloatBuffer(count); SOFTLoopback.alcRenderSamplesSOFT(l.mainOutput$getDevice(), samples, count);
            if (AL10.alGetError() != 0 || ALC10.alcGetError(l.mainOutput$getDevice()) != 0) throw new IllegalStateException("native render error");
            while (samples.hasRemaining()) out.putFloat(samples.get());
            for (Channel c : channels) { c.updateStream(); int s = ((ChannelSourceAccessor) c).fpvdrone$getSource(); if (AL10.alGetSourcei(s, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING || AL10.alGetSourcei(s, AL10.AL_BUFFERS_QUEUED) < 1) throw new IllegalStateException("stream underrun"); }
        }
        MainContextOutputCapture.requireCurrent(l.mainOutput$getDevice(), l.mainOutput$getContext()); return out.array();
    }
    private static AcousticCameraListener cameraListener() {
        try { Field field = DroneSoundManager.class.getDeclaredField("CAMERA_LISTENER"); field.setAccessible(true); return (AcousticCameraListener) field.get(null); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static AcousticVector awaitNativeListener(ClientGameTestContext context, String name) {
        JsonObject group = new JsonObject(); JsonArray attempts = new JsonArray();
        group.addProperty("case_name", name); group.addProperty("ready", false); group.add("attempts", attempts); listenerReadiness.add(group);
        for (int attempt = 0; attempt < WAIT; attempt++) {
            // waitFor may return immediately for a stale existing sample. A real
            // client tick must run so vanilla can enqueue its post-reload pose.
            context.waitTick();
            AtomicReference<AcousticCameraListener.Sample> sample = new AtomicReference<>();
            context.runOnClient(client -> sample.set(cameraListener().latestFor(client.level)));
            if (sample.get() == null) {
                JsonObject missing = new JsonObject(); missing.addProperty("attempt_index", attempt); missing.addProperty("sample_available", false); missing.addProperty("ready", false); attempts.add(missing); continue;
            }
            AcousticCameraListener.Sample expectedSample = sample.get();
            ListenerReadback readback = onSoundThread(context, () -> {
                FloatBuffer b = BufferUtils.createFloatBuffer(3); AL10.alGetListenerfv(AL10.AL_POSITION, b);
                int error = AL10.alGetError(); if (error != 0) throw new IllegalStateException("listener query failed: " + error);
                AcousticVector nativePosition = new AcousticVector(b.get(0), b.get(1), b.get(2));
                var transform = engine(Minecraft.getInstance()).getListenerTransform().position();
                AcousticVector worldPosition = new AcousticVector(transform.x, transform.y, transform.z), expected = expectedSample.frame().positionMeters();
                JsonObject observed = new JsonObject(); identity(observed);
                observed.addProperty("utc", Instant.now().toString()); observed.addProperty("thread", Thread.currentThread().getName());
                observed.addProperty("sample_available", true); observed.addProperty("camera_epoch", expectedSample.epoch());
                observed.add("camera_position", vector(expected)); observed.add("listener_position", vector(nativePosition)); observed.add("listener_world_position", vector(worldPosition));
                observed.addProperty("al_error", error);
                observed.addProperty("ready", nativePosition.subtract(expected).length() <= 1e-5 && worldPosition.subtract(expected).length() <= 1e-5);
                return new ListenerReadback(nativePosition, observed);
            });
            readback.observation().addProperty("attempt_index", attempt); attempts.add(readback.observation());
            if (readback.observation().get("ready").getAsBoolean()) { group.addProperty("ready", true); return readback.position(); }
        }
        throw new IllegalStateException("actual native listener did not acknowledge camera pose within " + WAIT + " client-tick attempts");
    }
    private static JsonArray vector(AcousticVector position) { JsonArray a = new JsonArray(); a.add(position.x()); a.add(position.y()); a.add(position.z()); return a; }
    private static JsonObject nativeSnapshot(Channel c, SoundInstance sound) {
        int s = ((ChannelSourceAccessor) c).fpvdrone$getSource(); JsonObject r = DistanceOwnershipRuntimeTrace.event("calibrated_native_snapshot", s);
        r.addProperty("relative", AL10.alGetSourcei(s, AL10.AL_SOURCE_RELATIVE) != 0); r.addProperty("pitch", AL10.alGetSourcef(s, AL10.AL_PITCH)); r.addProperty("listener_gain", AL10.alGetListenerf(AL10.AL_GAIN));
        r.addProperty("sample_offset", AL10.alGetSourcei(s, AL11.AL_SAMPLE_OFFSET)); FloatBuffer b = BufferUtils.createFloatBuffer(3); AL10.alGetSourcefv(s, AL10.AL_POSITION, b); JsonArray a = new JsonArray(); while (b.hasRemaining()) a.add(b.get()); r.add("position", a);
        FloatBuffer listener = BufferUtils.createFloatBuffer(3); AL10.alGetListenerfv(AL10.AL_POSITION, listener); JsonArray nativeListener = new JsonArray(); while (listener.hasRemaining()) nativeListener.add(listener.get()); r.add("listener_position", nativeListener);
        var world = engine(Minecraft.getInstance()).getListenerTransform().position(); JsonArray worldListener = new JsonArray(); worldListener.add(world.x); worldListener.add(world.y); worldListener.add(world.z); r.add("listener_world_position", worldListener);
        JsonArray worldSource = new JsonArray(); worldSource.add(sound.getX()); worldSource.add(sound.getY()); worldSource.add(sound.getZ()); r.add("source_world_position", worldSource);
        r.addProperty("al_error_after_extra_getters", AL10.alGetError()); r.addProperty("alc_error", ALC10.alcGetError(library().mainOutput$getDevice())); return r;
    }
    private static void identity(JsonObject r) { var l = library(); r.addProperty("device", l.mainOutput$getDevice()); r.addProperty("context", l.mainOutput$getContext()); r.addProperty("generation", MainContextOutputCapture.generation(l.mainOutput$getDevice(), l.mainOutput$getContext())); }
    private static void appendOutput(JsonObject r, byte[] b) { r.addProperty("output_offset_bytes", output.size()); r.addProperty("output_bytes", b.length); r.addProperty("output_sha256", MainContextOutputCapture.sha(b)); output.writeBytes(b); }
    private static void recordOptions(JsonObject r) { var c = Minecraft.getInstance(); r.addProperty("options_master", c.options.getSoundSourceVolume(SoundSource.MASTER)); r.addProperty("options_category", c.options.getSoundSourceVolume(SoundSource.NEUTRAL)); r.addProperty("gain_by_source_neutral", ((SoundEngineMainOutputAccessor) engine(c)).mainOutput$getGains().getFloat(SoundSource.NEUTRAL)); r.addProperty("library_debug", ((SoundEngineMainOutputAccessor) engine(c)).mainOutput$getLibrary().getDebugString()); }
    private static void setOptions(ClientGameTestContext context, double master, double category) { context.runOnClient(c -> { c.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(master); c.options.getSoundSourceOptionInstance(SoundSource.NEUTRAL).set(category); c.getSoundManager().updateCategoryVolume(SoundSource.NEUTRAL, 1); }); }
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
}
