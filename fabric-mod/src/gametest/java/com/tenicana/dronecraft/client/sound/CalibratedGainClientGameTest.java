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

/** Explicit synthetic calibration, real production sound/stream/publication/native path. */
final class CalibratedGainClientGameTest {
    private static final int WAIT = 600, FRAMES = 48000, WARMUP = 240000, ENTITY = 158001;
    private static final ByteArrayOutputStream output = new ByteArrayOutputStream(), input = new ByteArrayOutputStream(), mixed = new ByteArrayOutputStream();
    private static final List<PendingInput> pending = new ArrayList<>();
    private static final JsonArray listenerReadiness = new JsonArray();
    private record PendingInput(JsonObject row, AudioStream stream) { }
    private record ListenerReadback(AcousticVector position, JsonObject observation) { }
    private static volatile Fixture active;
    private static final class ProbeDrone extends DroneEntity {
        volatile float rpm = 12000;
        ProbeDrone(ClientLevel level) { super(DroneEntityTypes.DRONE, level); setId(ENTITY); }
        @Override public float getAverageMotorRpm() { return rpm; }
    }
    private static final class Fixture implements AutoCloseable {
        final ProbeDrone drone;
        final DroneAcousticRenderState state;
        final Object soundSet, controller;
        final Method publish;
        final AcousticListenerFrame listener;
        final ListenerReverbState wetState;
        final DroneLoopSoundInstance motor, propeller;
        final ListenerReverbSoundInstance bus;
        final List<SoundInstance> sounds;
        Fixture(Minecraft client, JsonObject declared, AcousticVector listenerPosition) {
            try {
                drone = new ProbeDrone(client.level);
                double distance = declared.get("distance_m").getAsDouble();
                drone.setPos(listenerPosition.x(), listenerPosition.y() - drone.getBbHeight() * .5, listenerPosition.z() - distance);
                listener = new AcousticListenerFrame(listenerPosition, new AcousticVector(0, 0, 0));
                Class<?> setClass = Class.forName(DroneSoundManager.class.getName() + "$DroneSoundSet");
                Constructor<?> setCtor = setClass.getDeclaredConstructor(DroneEntity.class); setCtor.setAccessible(true); soundSet = setCtor.newInstance(drone);
                state = (DroneAcousticRenderState) CalibratedGainCapture.field(soundSet, "acousticState");
                var emission = new AcousticEmissionFrame(List.of(
                        new TonalComponent(TonalComponent.Kind.ELECTRICAL, 0, 1, 700, .08, 0),
                        new TonalComponent(TonalComponent.Kind.BLADE_PASS, 0, 1, 1000, .08, 0)), AcousticBands.SILENT);
                var zero = new AcousticVector(0, 0, 0);
                var source = new AcousticSourceFrame(ENTITY, 0,
                        new AcousticVector(drone.getX(), drone.getY() + drone.getBbHeight() * .5, drone.getZ()), zero,
                        new AcousticVector(0, 1, 0), .127, List.of(new RotorAcousticState(12000, .5, .5, .0635, 3, 7, 1, 0)));
                double db = declared.get("calibration_db").getAsDouble();
                var calibration = new AcousticSourceProfile.Calibration(true, true, db, db);
                // No measured catalog profile, holdouts or validation evidence are invented.
                put(state, "audioSnapshot", new DroneAcousticRenderState.AudioSnapshot(source, listener, emission, emission, 1, 343, calibration));
                put(state, "sourceFrame", source); put(state, "listenerFrame", listener);
                put(state, "rawEmissionFrame", emission); put(state, "emissionFrame", emission); put(state, "preDopplerEmissionFrame", emission);
                motor = new DroneLoopSoundInstance(drone, DroneLoopSoundInstance.Layer.MOTOR, state);
                propeller = new DroneLoopSoundInstance(drone, DroneLoopSoundInstance.Layer.PROPELLER, state);
                put(soundSet, "motor", motor); put(soundSet, "propeller", propeller);
                Class<?> controllerClass = Class.forName(DroneSoundManager.class.getName() + "$ListenerReverbController");
                Constructor<?> ctor = controllerClass.getDeclaredConstructor(); ctor.setAccessible(true); controller = ctor.newInstance();
                wetState = (ListenerReverbState) CalibratedGainCapture.field(controller, "state");
                wetState.publishEnvironment(new FdnEnvironmentMapper.Controls(158, new AcousticBands(.6, .5, .4), .35, 0));
                wetState.requestWetHistoryOwner();
                publish = controllerClass.getDeclaredMethod("publishSources", AcousticListenerFrame.class, Map.class); publish.setAccessible(true);
                bus = new ListenerReverbSoundInstance(wetState); sounds = List.of(motor, propeller, bus);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        void publish() {
            try { publish.invoke(controller, listener, Map.of(ENTITY, soundSet)); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        @Override public void close() {
            try { ((AutoCloseable) controller).close(); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
    }
    private static void put(Object target, String name, Object value) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    static void run(ClientGameTestContext context) {
        JsonObject protocol = MainContextOutputCapture.protocol(); Path root = Path.of(protocol.get("workspace_root").getAsString());
        for (String name : List.of("mixed", "raw", "dry_raw"))
            if (Files.exists(root.resolve(protocol.get(name + "_pcm_file").getAsString()))) throw new IllegalStateException("existing " + name + " capture");
        JsonObject report = new JsonObject(), controls = new JsonObject(); JsonArray rows = new JsonArray();
        report.addProperty("started_utc", Instant.now().toString());
        report.addProperty("protocol_sha256", MainContextOutputCapture.sha(read(Path.of(System.getProperty(CalibratedGainCapture.PROPERTY)))));
        report.add("evidence_sha256", protocol.get("evidence_sha256").deepCopy());
        EnumMap<SoundSource, Double> options = new EnumMap<>(SoundSource.class); Throwable failure = null;
        ClientTickEvents.END_CLIENT_TICK.register(client -> { Fixture f = active; if (f != null) f.publish(); });
        try {
            JsonObject loaded = new JsonObject();
            for (var item : protocol.getAsJsonArray("required_loaded_class_paths")) {
                String path = item.getAsString(), resource = "/" + path.substring(path.indexOf("com/tenicana/"));
                try (var stream = CalibratedGainClientGameTest.class.getResourceAsStream(resource)) {
                    if (stream == null) throw new IllegalStateException("missing loaded " + path);
                    loaded.addProperty(path, MainContextOutputCapture.sha(stream.readAllBytes()));
                }
            }
            report.add("loaded_class_sha256", loaded);
            context.runOnClient(client -> {
                for (SoundSource source : SoundSource.values()) {
                    options.put(source, client.options.getSoundSourceOptionInstance(source).get());
                    client.options.getSoundSourceOptionInstance(source).set(source == SoundSource.MASTER || source == SoundSource.NEUTRAL ? 1.0 : 0.0);
                }
                client.getSoundManager().updateCategoryVolume(SoundSource.NEUTRAL, 1);
                SoundInstance probe = new ForeignProbe();
                WetSoundAdmissionCapture.probe("closed_music", probe, client.getSoundManager().play(probe));
            });
            try (TestSingleplayerContext world = context.worldBuilder().create()) {
                world.getClientWorld().waitForChunksDownload(); world.getServer().runCommand("gamerule spawn_mobs false");
                closeAndDrain(context, "world_ready"); controls.add("no_source", silence(context));
                int index = 0;
                for (var item : protocol.getAsJsonArray("expected_cases")) rows.add(captureCase(context, item.getAsJsonObject(), index++ == 0, controls));
                setOptions(context, 1, 1); context.runOnClient(client -> client.getSoundManager().reload());
                controls.add("reload_anchor", captureCase(context, protocol.getAsJsonObject("reload_anchor"), false, controls));
            }
            closeAndDrain(context, "final"); context.runOnClient(client -> client.getSoundManager().destroy());
            report.addProperty("status", "calibrated_native_gain_captured");
        } catch (Throwable e) {
            failure = e; report.addProperty("status", "calibrated_native_gain_failed"); report.addProperty("failure", e.toString()); active = null;
            try { context.runOnClient(client -> client.getSoundManager().destroy()); } catch (Throwable cleanup) { report.addProperty("cleanup_failure", cleanup.toString()); }
        } finally { active = null; context.runOnClient(client -> options.forEach((s, v) -> client.options.getSoundSourceOptionInstance(s).set(v))); }
        report.add("rows", rows); report.add("controls", controls); report.add("listener_readiness", listenerReadiness); report.add("publications", WetMainOutputCapture.publications());
        report.add("gain_observations", CalibratedGainCapture.snapshot()); report.add("epochs", MainContextOutputCapture.epochs());
        report.add("runtime_events", DistanceOwnershipRuntimeTrace.snapshot()); report.add("channel_allocations", WetChannelLifecycleCapture.allocations());
        report.add("channel_calls", WetChannelLifecycleCapture.calls()); report.add("sound_admission", WetSoundAdmissionCapture.snapshot());
        JsonObject scope = new JsonObject();
        for (String n : List.of("actual_Minecraft_client_started", "actual_minecraft_main_context_output", "synthetic_calibration_only", "controlled_client_entity_not_in_world", "real_production_sound_instances", "actual_production_publishSources", "HRTF_and_output_limiter_disabled_in_test", "gametest_only_nonmutating_taps")) scope.addProperty(n, true);
        for (String n : List.of("measured_profile_catalog_selected", "physical_endpoint_verified", "real_pressure_or_rpm_accessed", "queued_sample_playback_tick_alignment_verified", "six_drone_full_client_verified", "release_profile_eligible")) scope.addProperty(n, false);
        scope.addProperty("test_stimulus_admission_before_allocation", true);
        report.add("scope", scope);
        writePcm(root, protocol, report, "input", input.toByteArray()); writePcm(root, protocol, report, "output", output.toByteArray());
        writePcm(root, protocol, report, "mixed", mixed.toByteArray()); writePcm(root, protocol, report, "raw", WetMainOutputCapture.rawBytes());
        writePcm(root, protocol, report, "dry_raw", CalibratedGainCapture.rawBytes()); report.addProperty("finished_utc", Instant.now().toString());
        try { Files.writeString(root.resolve(protocol.get("output_report").getAsString()), new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report) + "\n", StandardOpenOption.CREATE_NEW); }
        catch (Exception e) { throw new IllegalStateException(e); }
        if (failure != null) throw new IllegalStateException("calibrated gain capture failed", failure);
    }
    private static JsonObject captureCase(ClientGameTestContext context, JsonObject declared, boolean release, JsonObject controls) {
        String name = declared.get("name").getAsString(); CalibratedGainCapture.phase(name);
        setOptions(context, declared.get("master").getAsDouble(), declared.get("category").getAsDouble());
        int readinessIndex = listenerReadiness.size();
        AcousticVector pos = awaitNativeListener(context, name);
        AtomicReference<Fixture> ref = new AtomicReference<>();
        context.runOnClient(client -> { Fixture f = new Fixture(client, declared, pos); ref.set(f); active = f; WetSoundAdmissionCapture.exact(name, f.sounds);
            if (release) { SoundInstance probe = new DroneLoopSoundInstance(f.drone, DroneLoopSoundInstance.Layer.MOTOR, f.state); WetSoundAdmissionCapture.probe("unregistered_same_class", probe, client.getSoundManager().play(probe)); }
            f.sounds.forEach(client.getSoundManager()::play);
        });
        Fixture f = ref.get();
        try {
            context.waitFor(client -> f.state.distanceStreamsVerified() && f.motor.getVolume() >= .99999f && f.propeller.getVolume() >= .99999f && f.sounds.stream().allMatch(s -> handles(client).containsKey(s)), WAIT);
            // The final float32 fixed point is reached before the finite steady window.
            context.waitFor(client -> f.motor.getVolume() == .99999994f && f.propeller.getVolume() == .99999994f, WAIT);
            context.runOnClient(client -> f.publish());
            List<ChannelAccess.ChannelHandle> selected = new ArrayList<>(); context.runOnClient(client -> f.sounds.forEach(s -> selected.add(handles(client).get(s))));
            List<Channel> channels = selected.stream().map(CalibratedGainClientGameTest::channel).toList();
            JsonObject row = declared.deepCopy();
            row.addProperty("listener_readiness_index", readinessIndex);
            onSoundThread(context, () -> { render(channels, WARMUP); return null; });
            onSoundThread(context, () -> { capture(row, channels, f.sounds, FRAMES, true); return null; });
            row.addProperty("warmup_frames", WARMUP); row.add("publication_before_render", latestPublication());
            if (release) {
                CalibratedGainCapture.phase(name + ":RELEASE"); context.runOnClient(client -> f.drone.rpm = 0);
                context.waitFor(client -> f.motor.getVolume() == 0 && f.propeller.getVolume() == 0, WAIT);
                context.runOnClient(client -> f.publish());
                JsonObject tail = new JsonObject(); tail.add("publication_at_zero", latestPublication());
                onSoundThread(context, () -> { capture(tail, channels, f.sounds, FRAMES, false); return null; }); controls.add("release", tail);
            }
            active = null; closeAndDrain(context, name); row.add("history_after_stop", new Gson().toJsonTree(f.wetState.historyDiagnostics())); return row;
        } finally { active = null; f.close(); }
    }
    private static JsonObject latestPublication() { JsonArray a = WetMainOutputCapture.publications(); if (a.size() == 0) throw new IllegalStateException("no publication"); return a.get(a.size() - 1).getAsJsonObject().deepCopy(); }
    private static void capture(JsonObject row, List<Channel> channels, List<SoundInstance> sounds, int frames, boolean inputs) {
        identity(row); row.addProperty("render_frames", frames); row.addProperty("active_playing_source_count", channels.size());
        if (handles(Minecraft.getInstance()).size() != 3) throw new IllegalStateException("three voices not isolated");
        JsonArray playing = new JsonArray();
        for (Channel c : channels) {
            int s = ((ChannelSourceAccessor) c).fpvdrone$getSource();
            if (AL10.alGetSourcei(s, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) playing.add(s);
        }
        if (playing.size() != 3 || AL10.alGetError() != 0) throw new IllegalStateException("three native voices not playing");
        row.add("playing_sources", playing);
        JsonArray snapshots = new JsonArray();
        for (int i = 0; i < channels.size(); i++) {
            Channel ch = channels.get(i); SoundInstance sound = sounds.get(i); JsonObject s = new JsonObject();
            s.addProperty("source", ((ChannelSourceAccessor) ch).fpvdrone$getSource()); s.addProperty("channel_allocation_identity", WetChannelLifecycleCapture.identity(ch));
            s.addProperty("sound_identity", System.identityHashCode(sound)); s.addProperty("sound_instance_volume", sound.getVolume()); s.addProperty("sound_instance_class", sound.getClass().getName());
            if (sound instanceof DroneLoopSoundInstance dry) { s.addProperty("layer", dry.layer().name()); s.addProperty("entity_id", ENTITY); s.addProperty("seed", 31 * ENTITY + dry.layer().ordinal()); }
            s.add("before", nativeSnapshot(ch, sound)); snapshots.add(s);
        }
        recordOptions(row); appendOutput(row, render(channels, frames));
        for (int i = 0; i < channels.size(); i++) {
            JsonObject s = snapshots.get(i).getAsJsonObject(); s.add("after", nativeSnapshot(channels.get(i), sounds.get(i)));
            if (inputs) {
                AudioStream stream = ((MainOutputChannelAccessor) channels.get(i)).mainOutput$getStream();
                s.addProperty("owner_verified", stream instanceof ProceduralDroneAudioStream dry && dry.distanceOwnerVerified()); pending.add(new PendingInput(s, stream));
            }
        }
        row.add("source_snapshots", snapshots);
    }
    private static void sealInputs() {
        for (PendingInput p : pending) {
            JsonObject r = p.row(); AudioStream s = p.stream(); boolean closed = WetMainOutputCapture.closed(s);
            r.addProperty("stream_closed_after_cleanup", closed); if (!closed) throw new IllegalStateException("selected stream not closed");
            byte[] b = MainContextOutputCapture.input(s); r.addProperty("input_offset_bytes", input.size()); r.addProperty("input_bytes", b.length); r.addProperty("input_sha256", MainContextOutputCapture.sha(b));
            r.addProperty("input_record_identity", MainContextOutputCapture.identity(s)); r.addProperty("stream_identity", System.identityHashCode(s)); r.addProperty("stream_class", s.getClass().getName()); input.writeBytes(b);
            if (s instanceof ProceduralDroneAudioStream) r.add("dry_read_batches", CalibratedGainCapture.reads(s));
            else if (s instanceof ListenerReverbAudioStream wet) { byte[] m = WetMainOutputCapture.mixed(s); r.addProperty("preroll_frames", wet.prerollFrames()); r.addProperty("mixed_offset_bytes", mixed.size()); r.addProperty("mixed_bytes", m.length); r.addProperty("mixed_sha256", MainContextOutputCapture.sha(m)); mixed.writeBytes(m); r.add("mixer_batches", WetMainOutputCapture.batches(s)); }
        }
        pending.clear();
    }
    private static void closeAndDrain(ClientGameTestContext context, String reason) {
        context.runOnClient(client -> WetSoundAdmissionCapture.close(reason)); context.waitFor(client -> WetSoundAdmissionCapture.drained(), WAIT);
        onSoundThread(context, () -> null); if (!WetSoundAdmissionCapture.drained()) throw new IllegalStateException("old executor pending");
        onSoundThread(context, () -> { WetSoundAdmissionCapture.reclaimUnattached(); return null; });
        context.runOnClient(client -> engine(client).stopAll()); onSoundThread(context, () -> { sealInputs(); return null; });
        context.runOnClient(client -> WetSoundAdmissionCapture.barrier(handles(client).size()));
    }
    private static JsonObject silence(ClientGameTestContext context) { return onSoundThread(context, () -> { JsonObject r = new JsonObject(); identity(r); r.addProperty("render_frames", FRAMES); r.addProperty("active_source_count", handles(Minecraft.getInstance()).size()); if (!handles(Minecraft.getInstance()).isEmpty()) throw new IllegalStateException("no-source not empty"); appendOutput(r, render(List.of(), FRAMES)); return r; }); }
    private static final class ForeignProbe extends AbstractTickableSoundInstance {
        ForeignProbe() { super(DroneSoundEvents.MOTOR_LOOP, SoundSource.MUSIC, SoundInstance.createUnseededRandom()); volume = 0; }
        @Override public void tick() { }
        @Override public boolean canStartSilent() { return true; }
    }
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
