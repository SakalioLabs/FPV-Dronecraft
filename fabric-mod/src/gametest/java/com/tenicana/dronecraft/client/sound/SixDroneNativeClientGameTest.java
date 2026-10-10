package com.tenicana.dronecraft.client.sound;

import com.google.gson.*;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.acoustics.*;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import com.tenicana.dronecraft.client.mixin.*;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import com.tenicana.dronecraft.sound.DroneSoundEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;


/** Actual world membership and manager selection; stationary synthetic telemetry only. */
final class SixDroneNativeClientGameTest {
    static final String PROPERTY = "fpvdrone.acoustics.sixDroneProtocol";
    private static final int WAIT = 600, FRAMES = 48000, WARMUP = 48000, ENTITY = 162001;
    private static final ByteArrayOutputStream output = new ByteArrayOutputStream(), input = new ByteArrayOutputStream(), mixed = new ByteArrayOutputStream();
    private static final JsonArray listenerReadiness = new JsonArray();
    private static final List<ProbeDrone> probes = new ArrayList<>();
    private static final List<PendingInput> pending = new ArrayList<>();
    private record ListenerReadback(AcousticVector position, JsonObject observation) { }
    private record PendingInput(JsonObject row, AudioStream stream) { }
    private record Voice(int entityId, String layer, SoundInstance sound, Channel channel) { }
    private static final class ProbeDrone extends DroneEntity {
        ProbeDrone(ClientLevel level, int id) { super(DroneEntityTypes.DRONE, level); setId(id); }
        @Override public float getAverageMotorRpm() { return 12000; }
        @Override public float getMotorRpm(int index) { return 12000; }
        @Override public float getMotorPower() { return .5f; }
        @Override public float getMotorPower(int index) { return .5f; }
        @Override public void tick() { tickCount++; }
    }
    static boolean requested() { return System.getProperty(PROPERTY) != null; }
    private static Object field(Object target, String name) {
        try { Class<?> type = target instanceof Class<?> c ? c : target.getClass(); Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(target instanceof Class<?> ? null : target); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    @SuppressWarnings("unchecked") private static Map<Integer, Object> activeSets() { return (Map<Integer,Object>) field(DroneSoundManager.class, "ACTIVE_SOUNDS"); }
    private static Object controller() { return field(DroneSoundManager.class, "LISTENER_REVERB"); }
    private static JsonObject membership(Minecraft client) {
        JsonObject row = new JsonObject(); JsonArray world = new JsonArray(), selected = new JsonArray();
        for (var entity : client.level.entitiesForRendering()) if (entity instanceof DroneEntity) world.add(entity.getId());
        for (var item : activeSets().entrySet()) {
            JsonObject s = new JsonObject(); s.addProperty("entity_id", item.getKey()); s.addProperty("world_entity_identity", System.identityHashCode(client.level.getEntity(item.getKey())));
            s.addProperty("manager_entity_identity", System.identityHashCode(field(item.getValue(), "drone")));
            DroneAcousticRenderState state = (DroneAcousticRenderState) field(item.getValue(), "acousticState"); s.addProperty("distance_streams_verified", state.distanceStreamsVerified());
            for (String layer : List.of("motor", "propeller")) { Object sound = field(item.getValue(), layer); s.addProperty(layer + "_identity", sound == null ? 0 : System.identityHashCode(sound)); s.addProperty(layer + "_has_actual_handle", sound != null && handles(client).containsKey(sound)); }
            selected.add(s);
        }
        row.add("world_drone_ids", world); row.add("selected_sets", selected); row.addProperty("actual_handle_count", handles(client).size());
        row.addProperty("bus_identity", System.identityHashCode(field(controller(), "sound"))); return row;
    }
    private static List<Voice> voices(Minecraft client) {
        List<Voice> result = new ArrayList<>(); activeSets().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(item -> {
            for (String name : List.of("motor", "propeller")) { SoundInstance s = (SoundInstance) field(item.getValue(), name); if (s != null && handles(client).get(s) != null) result.add(new Voice(item.getKey(), name.toUpperCase(Locale.ROOT), s, channel(handles(client).get(s)))); }
        });
        SoundInstance bus = (SoundInstance) field(controller(), "sound"); if (bus != null && handles(client).get(bus) != null) result.add(new Voice(-1, "WET", bus, channel(handles(client).get(bus)))); return result;
    }
    static void run(ClientGameTestContext context) {
        JsonObject protocol = MainContextOutputCapture.protocol(), report = new JsonObject(); Path root = Path.of(protocol.get("workspace_root").getAsString()); JsonArray rows = new JsonArray(); JsonObject controls = new JsonObject();
        report.addProperty("started_utc", Instant.now().toString()); report.addProperty("protocol_sha256", MainContextOutputCapture.sha(read(Path.of(System.getProperty(PROPERTY))))); report.add("evidence_sha256", protocol.get("evidence_sha256").deepCopy());
        EnumMap<SoundSource, Double> options = new EnumMap<>(SoundSource.class); Throwable failure = null;
        try {
            JsonObject loaded = new JsonObject();
            for (var item : protocol.getAsJsonArray("required_loaded_class_paths")) { String path = item.getAsString(), resource = "/" + path.substring(path.indexOf("com/tenicana/")); try (var s = SixDroneNativeClientGameTest.class.getResourceAsStream(resource)) { if (s == null) throw new IllegalStateException("missing loaded " + path); loaded.addProperty(path, MainContextOutputCapture.sha(s.readAllBytes())); } }
            report.add("loaded_class_sha256", loaded);
            context.runOnClient(client -> { for (SoundSource s : SoundSource.values()) { options.put(s, client.options.getSoundSourceOptionInstance(s).get()); client.options.getSoundSourceOptionInstance(s).set(s == SoundSource.MASTER || s == SoundSource.NEUTRAL ? 1.0 : 0.0); } client.getSoundManager().updateCategoryVolume(SoundSource.NEUTRAL, 1); });
            try (TestSingleplayerContext world = context.worldBuilder().create()) {
                world.getClientWorld().waitForChunksDownload(); world.getServer().runCommand("gamerule spawn_mobs false");
                closeAndDrain(context, "world_ready"); controls.add("no_source", silence(context));
                for (int epoch = 0; epoch < 2; epoch++) {
                    if (epoch == 1) context.runOnClient(client -> client.getSoundManager().reload());
                    String name = epoch == 0 ? "INITIAL" : "RELOAD"; AcousticVector pos = awaitNativeListener(context, name);
                    context.runOnClient(client -> {
                        WetSoundAdmissionCapture.live();
                        for (int i = 0; i < 6; i++) { ProbeDrone drone = new ProbeDrone(client.level, ENTITY+i); drone.setPos(pos.x() + (i-2.5)*2, pos.y() - drone.getBbHeight()*.5, pos.z()-8); client.level.addEntity(drone); probes.add(drone); }
                    });
                    context.waitFor(client -> activeSets().size() == 6, WAIT);
                    for (int tick = 0; tick < protocol.get("settle_client_ticks").getAsInt(); tick++) context.waitTick();
                    onSoundThread(context, () -> null);
                    if (protocol.get("stage").getAsString().equals("fixed")) context.waitFor(client -> voices(client).size() == 13 && activeSets().values().stream().allMatch(s -> ((DroneAcousticRenderState) field(s,"acousticState")).distanceStreamsVerified()) && latestPublication().get("source_count").getAsInt() == 6, WAIT);
                    JsonObject row = new JsonObject(); row.addProperty("name", name); row.addProperty("synthetic_rpm", 12000); row.addProperty("synthetic_power", .5); row.addProperty("settle_client_ticks", protocol.get("settle_client_ticks").getAsInt()); AtomicReference<List<Voice>> selected = new AtomicReference<>();
                    context.runOnClient(client -> { row.add("membership", membership(client)); selected.set(voices(client)); });
                    List<Voice> v = selected.get();
                    onSoundThread(context, () -> { capture(row, v); return null; }); rows.add(row);
                    closeAndDrain(context, name); context.runOnClient(client -> row.addProperty("handles_after_cleanup", handles(client).size())); row.addProperty("manager_sets_after_cleanup", activeSets().size());
                    controls.add(name + "_no_source", silence(context));
                }
            }
            closeAndDrain(context, "final"); context.runOnClient(client -> client.getSoundManager().destroy()); report.addProperty("status", "six_drone_native_captured");
        } catch (Throwable e) {
            failure = e; report.addProperty("status", "six_drone_native_failed"); report.addProperty("failure", e.toString());
            try { closeAndDrain(context, "failure"); context.runOnClient(client -> client.getSoundManager().destroy()); } catch (Throwable cleanup) { report.addProperty("cleanup_failure", cleanup.toString()); }
        } finally { context.runOnClient(client -> options.forEach((s,v) -> client.options.getSoundSourceOptionInstance(s).set(v))); }
        report.add("rows", rows); report.add("controls", controls); report.add("listener_readiness", listenerReadiness); report.add("publications", WetMainOutputCapture.publications()); report.add("epochs", MainContextOutputCapture.epochs()); report.add("runtime_events", DistanceOwnershipRuntimeTrace.snapshot()); report.add("channel_allocations", WetChannelLifecycleCapture.allocations()); report.add("channel_calls", WetChannelLifecycleCapture.calls()); report.add("sound_admission", WetSoundAdmissionCapture.snapshot());
        JsonObject scope = new JsonObject(); for (String n : List.of("actual_Minecraft_client_started", "actual_minecraft_main_context_output", "actual_client_world_entities", "actual_production_manager_selection", "synthetic_stationary_telemetry_only", "HRTF_and_output_limiter_disabled_in_test")) scope.addProperty(n,true);
        for (String n : List.of("physical_endpoint_verified", "measured_profile_catalog_selected", "real_pressure_or_rpm_accessed", "multiplayer_flight_verified", "release_profile_eligible", "full_goal_complete", "wet_audibility_verified", "immediate_post_stop_silence_verified", "queued_sample_playback_tick_alignment_verified")) scope.addProperty(n,false); report.add("scope",scope);
        writePcm(root,protocol,report,"input",input.toByteArray()); writePcm(root,protocol,report,"output",output.toByteArray()); writePcm(root,protocol,report,"mixed",mixed.toByteArray()); writePcm(root,protocol,report,"raw",WetMainOutputCapture.rawBytes()); report.addProperty("finished_utc",Instant.now().toString());
        try { Files.writeString(root.resolve(protocol.get("output_report").getAsString()),new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report)+"\n",StandardOpenOption.CREATE_NEW); } catch (Exception e) { throw new IllegalStateException(e); }
        if (failure != null) throw new IllegalStateException("six drone capture failed",failure);
        if (rows.size()!=2 || rows.asList().stream().anyMatch(r -> r.getAsJsonObject().get("active_playing_source_count").getAsInt()!=13)) throw new IllegalStateException("six selected world drones require 12 dry + 1 wet actual native streams");
    }
    private static JsonObject latestPublication() { JsonArray a=WetMainOutputCapture.publications(); if(a.isEmpty()) throw new IllegalStateException("no actual publication"); return a.get(a.size()-1).getAsJsonObject().deepCopy(); }
    private static void capture(JsonObject row, List<Voice> voices) {
        identity(row); recordOptions(row); row.addProperty("warmup_frames", WARMUP); row.addProperty("render_frames", FRAMES); row.addProperty("active_playing_source_count", voices.size()); row.add("publication_before_render",latestPublication());
        List<Channel> channels=voices.stream().map(Voice::channel).toList(); JsonArray snapshots=new JsonArray();
        for(Voice v:voices) {
            JsonObject s=new JsonObject(); s.addProperty("entity_id",v.entityId()); s.addProperty("layer",v.layer()); s.addProperty("sound_identity",System.identityHashCode(v.sound()));
            s.addProperty("source",((ChannelSourceAccessor)v.channel()).fpvdrone$getSource()); s.addProperty("channel_allocation_identity",WetChannelLifecycleCapture.identity(v.channel()));
            int bufferBytes=((ChannelStreamingBufferAccessor)v.channel()).fpvdrone$getStreamingBufferSize(); s.addProperty("streaming_buffer_bytes",bufferBytes);
            JsonObject protocol=MainContextOutputCapture.protocol();
            if(protocol.has("expected_streaming_buffer_bytes") && bufferBytes!=protocol.get("expected_streaming_buffer_bytes").getAsInt()) throw new IllegalStateException("unexpected drone streaming block size");
            s.add("before",nativeSnapshot(v.channel(),v.sound())); snapshots.add(s);
        }
        render(channels,WARMUP); appendOutput(row,render(channels,FRAMES));
        for(int i=0;i<voices.size();i++) { Voice v=voices.get(i); JsonObject s=snapshots.get(i).getAsJsonObject(); s.add("after",nativeSnapshot(v.channel(),v.sound())); AudioStream stream=((MainOutputChannelAccessor)v.channel()).mainOutput$getStream(); s.addProperty("owner_verified",stream instanceof ProceduralDroneAudioStream dry && dry.distanceOwnerVerified()); pending.add(new PendingInput(s,stream)); }
        row.add("source_snapshots",snapshots); row.add("publication_after_render",latestPublication());
    }
    private static void sealInputs() {
        for(PendingInput p:pending) { JsonObject r=p.row(); AudioStream s=p.stream(); boolean closed=WetMainOutputCapture.closed(s); r.addProperty("stream_closed_after_cleanup",closed); if(!closed) throw new IllegalStateException("selected stream not closed"); byte[] b=MainContextOutputCapture.input(s); r.addProperty("input_offset_bytes",input.size()); r.addProperty("input_bytes",b.length); r.addProperty("input_sha256",MainContextOutputCapture.sha(b)); r.addProperty("input_record_identity",MainContextOutputCapture.identity(s)); r.addProperty("stream_identity",System.identityHashCode(s)); r.addProperty("stream_class",s.getClass().getName()); input.writeBytes(b);
            if(s instanceof ListenerReverbAudioStream wet) { byte[] m=WetMainOutputCapture.mixed(s); r.addProperty("preroll_frames",wet.prerollFrames()); r.addProperty("mixed_offset_bytes",mixed.size()); r.addProperty("mixed_bytes",m.length); r.addProperty("mixed_sha256",MainContextOutputCapture.sha(m)); mixed.writeBytes(m); r.add("mixer_batches",WetMainOutputCapture.batches(s)); }
        } pending.clear();
    }
    private static void closeAndDrain(ClientGameTestContext context,String reason) {
        context.runOnClient(client -> { WetSoundAdmissionCapture.close(reason); for(ProbeDrone p:probes) { if(client.level!=null) client.level.removeEntity(p.getId(),net.minecraft.world.entity.Entity.RemovalReason.DISCARDED); } probes.clear(); try { Method m=DroneSoundManager.class.getDeclaredMethod("stopAll", Minecraft.class); m.setAccessible(true); m.invoke(null,client); } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); } });
        context.waitFor(client -> WetSoundAdmissionCapture.drained(),WAIT); onSoundThread(context,()->null); onSoundThread(context,()->{ WetSoundAdmissionCapture.reclaimUnattached(); return null; }); context.runOnClient(client -> engine(client).stopAll()); onSoundThread(context,()->{ sealInputs(); return null; }); context.runOnClient(client -> WetSoundAdmissionCapture.barrier(handles(client).size()));
    }
    private static JsonObject silence(ClientGameTestContext context) { return onSoundThread(context,()->{ JsonObject r=new JsonObject(); identity(r); r.addProperty("render_frames",FRAMES); r.addProperty("active_source_count",handles(Minecraft.getInstance()).size()); if(!handles(Minecraft.getInstance()).isEmpty()) throw new IllegalStateException("no source not empty"); int settle=MainContextOutputCapture.protocol().has("post_stop_settle_frames")?MainContextOutputCapture.protocol().get("post_stop_settle_frames").getAsInt():0; r.addProperty("post_stop_settle_frames",settle); if(settle>0) render(List.of(),settle); appendOutput(r,render(List.of(),FRAMES)); return r; }); }
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
    private static byte[] read(Path p) { try { return Files.readAllBytes(p); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static void writePcm(Path root, JsonObject protocol, JsonObject report, String name, byte[] b) { String path = protocol.get(name + "_pcm_file").getAsString(); try { Files.write(root.resolve(path), b, StandardOpenOption.CREATE_NEW); } catch (Exception e) { throw new IllegalStateException(e); } report.addProperty(name + "_pcm_file", path); report.addProperty(name + "_pcm_bytes", b.length); report.addProperty(name + "_pcm_sha256", MainContextOutputCapture.sha(b)); }
}
