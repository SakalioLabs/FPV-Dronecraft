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

/** Diagnostic synthetic calibration switch through actual Minecraft main streaming voices. */
final class CalibrationTransitionNativeClientGameTest {
    private static final int WAIT = 600, FRAMES = 48000, WARMUP = 240000, ENTITY = 164001;
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
        void calibration(double amplitude) {
            var s = state.audioSnapshot();
            double db = 20 * Math.log10(amplitude);
            try { put(state, "audioSnapshot", new DroneAcousticRenderState.AudioSnapshot(s.source(), s.listener(),
                    s.preDopplerEmission(), s.emission(), s.dopplerFrequencyRatio(), s.soundSpeedMetersPerSecond(),
                    new AcousticSourceProfile.Calibration(true, true, db, db))); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        void distance(double distance) {
            drone.setPos(listener.positionMeters().x(), listener.positionMeters().y() - drone.getBbHeight() * .5,
                    listener.positionMeters().z() - distance);
            var s = state.audioSnapshot();
            var source = new AcousticSourceFrame(ENTITY, 0,
                    new AcousticVector(drone.getX(), drone.getY() + drone.getBbHeight() * .5, drone.getZ()),
                    new AcousticVector(0, 0, 0), new AcousticVector(0, 1, 0), .127,
                    List.of(new RotorAcousticState(12000, .5, .5, .0635, 3, 7, 1, 0)));
            try {
                put(state, "sourceFrame", source);
                put(state, "audioSnapshot", new DroneAcousticRenderState.AudioSnapshot(source, s.listener(),
                        s.preDopplerEmission(), s.emission(), s.dopplerFrequencyRatio(), s.soundSpeedMetersPerSecond(), s.calibration()));
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
    static boolean requested() { return CalibrationTransitionQueueCapture.requested(); }
    static void run(ClientGameTestContext context) {
        JsonObject protocol = MainContextOutputCapture.protocol(); Path root = Path.of(protocol.get("workspace_root").getAsString());
        for (String name : List.of("mixed", "raw", "dry_raw"))
            if (Files.exists(root.resolve(protocol.get(name + "_pcm_file").getAsString()))) throw new IllegalStateException("existing " + name + " capture");
        JsonObject report = new JsonObject(), controls = new JsonObject(); JsonArray rows = new JsonArray();
        report.addProperty("started_utc", Instant.now().toString());
        report.addProperty("protocol_sha256", MainContextOutputCapture.sha(read(Path.of(System.getProperty(CalibrationTransitionQueueCapture.PROPERTY)))));
        report.add("evidence_sha256", protocol.get("evidence_sha256").deepCopy());
        EnumMap<SoundSource, Double> options = new EnumMap<>(SoundSource.class); Throwable failure = null;
        ClientTickEvents.END_CLIENT_TICK.register(client -> { Fixture f = active; if (f != null) f.publish(); });
        try {
            JsonObject loaded = new JsonObject();
            for (var item : protocol.getAsJsonArray("required_loaded_class_paths")) {
                String path = item.getAsString(), resource = "/" + path.substring(path.indexOf("com/tenicana/"));
                try (var stream = CalibrationTransitionNativeClientGameTest.class.getResourceAsStream(resource)) {
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
                for (var item : protocol.getAsJsonArray("expected_cases")) rows.add(captureTransition(context, item.getAsJsonObject()));
                context.runOnClient(client -> client.getSoundManager().reload());
                controls.add("reload_transition", captureTransition(context, protocol.getAsJsonObject("reload_case")));
            }
            closeAndDrain(context, "final"); context.runOnClient(client -> client.getSoundManager().destroy());
            report.addProperty("status", "calibration_transition_native_captured");
        } catch (Throwable e) {
            failure = e; report.addProperty("status", "calibration_transition_native_failed"); report.addProperty("failure", e.toString()); active = null;
            try { context.runOnClient(client -> client.getSoundManager().destroy()); } catch (Throwable cleanup) { report.addProperty("cleanup_failure", cleanup.toString()); }
        } finally { active = null; context.runOnClient(client -> options.forEach((s, v) -> client.options.getSoundSourceOptionInstance(s).set(v))); }
        report.add("rows", rows); report.add("controls", controls); report.add("listener_readiness", listenerReadiness); report.add("publications", WetMainOutputCapture.publications());
        report.add("transition_trace", CalibrationTransitionQueueCapture.snapshot());
        report.add("gain_observations", CalibratedGainCapture.snapshot()); report.add("epochs", MainContextOutputCapture.epochs());
        report.add("runtime_events", DistanceOwnershipRuntimeTrace.snapshot()); report.add("channel_allocations", WetChannelLifecycleCapture.allocations());
        report.add("channel_calls", WetChannelLifecycleCapture.calls()); report.add("sound_admission", WetSoundAdmissionCapture.snapshot());
        JsonObject scope = new JsonObject();
        for (String n : List.of("actual_Minecraft_client_started", "actual_minecraft_main_context_output", "synthetic_calibration_only", "controlled_client_entity_not_in_world", "real_production_sound_instances", "actual_production_publishSources", "HRTF_and_output_limiter_disabled_in_test", "gametest_only_nonmutating_taps", "read_to_buffer_fifo_recorded", "render_clock_recorded")) scope.addProperty(n, true);
        for (String n : List.of("measured_profile_catalog_selected", "actual_catalog_reload_calibration_switch", "physical_endpoint_verified", "real_pressure_or_rpm_accessed", "transient_qualification", "audible_click_verified", "six_drone_full_client_verified", "release_profile_eligible")) scope.addProperty(n, false);
        if (protocol.has("transition_kind") && protocol.get("transition_kind").getAsString().equals("distance")) {
            scope.addProperty("synthetic_calibration_only", false);
            scope.addProperty("synthetic_distance_jump_only", true);
            scope.addProperty("controlled_native_gain_isolation", true);
            scope.addProperty("production_telemetry_mapper_exercised", false);
        }
        report.add("scope", scope);
        writePcm(root, protocol, report, "input", input.toByteArray()); writePcm(root, protocol, report, "output", output.toByteArray());
        writePcm(root, protocol, report, "mixed", mixed.toByteArray()); writePcm(root, protocol, report, "raw", WetMainOutputCapture.rawBytes());
        writePcm(root, protocol, report, "dry_raw", CalibratedGainCapture.rawBytes()); report.addProperty("finished_utc", Instant.now().toString());
        try { Files.writeString(root.resolve(protocol.get("output_report").getAsString()), new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report) + "\n", StandardOpenOption.CREATE_NEW); }
        catch (Exception e) { throw new IllegalStateException(e); }
        if (failure != null) throw new IllegalStateException("calibration transition capture failed", failure);
    }
    private static JsonObject captureTransition(ClientGameTestContext context, JsonObject declared) {
        String name = declared.get("name").getAsString(); CalibratedGainCapture.phase(name + ":OLD"); CalibrationTransitionQueueCapture.phase(name + ":OLD");
        int readinessIndex = listenerReadiness.size(); AcousticVector pos = awaitNativeListener(context, name);
        AtomicReference<Fixture> ref = new AtomicReference<>();
        context.runOnClient(client -> { Fixture f = new Fixture(client, declared, pos); ref.set(f); active = f;
            WetSoundAdmissionCapture.exact(name, f.sounds); f.sounds.forEach(client.getSoundManager()::play); });
        Fixture f = ref.get();
        try {
            context.waitFor(client -> f.state.distanceStreamsVerified() && f.motor.getVolume() == .99999994f && f.propeller.getVolume() == .99999994f
                    && f.sounds.stream().allMatch(s -> handles(client).containsKey(s)), WAIT);
            // Freeze client publications before the executor owns this finite diagnostic clock.
            context.runOnClient(client -> { f.publish(); active = null; });
            List<ChannelAccess.ChannelHandle> selected = new ArrayList<>(); context.runOnClient(client -> f.sounds.forEach(s -> selected.add(handles(client).get(s))));
            List<Channel> channels = selected.stream().map(CalibrationTransitionNativeClientGameTest::channel).toList();
            JsonObject row = declared.deepCopy(); row.addProperty("listener_readiness_index", readinessIndex);
            onSoundThread(context, () -> {
                identity(row); if (handles(Minecraft.getInstance()).size() != 3) throw new IllegalStateException("transition voices not isolated");
                JsonArray snapshots = new JsonArray();
                for (int i = 0; i < channels.size(); i++) {
                    Channel ch = channels.get(i); SoundInstance sound = f.sounds.get(i); JsonObject s = new JsonObject();
                    s.addProperty("source", ((ChannelSourceAccessor) ch).fpvdrone$getSource()); s.addProperty("channel_allocation_identity", WetChannelLifecycleCapture.identity(ch));
                    s.addProperty("sound_identity", System.identityHashCode(sound)); s.addProperty("sound_instance_class", sound.getClass().getName());
                    if (sound instanceof DroneLoopSoundInstance dry) { s.addProperty("layer", dry.layer().name()); s.addProperty("seed", 31 * ENTITY + dry.layer().ordinal()); }
                    else s.addProperty("layer", "WET");
                    int bufferBytes = ((ChannelStreamingBufferAccessor) ch).fpvdrone$getStreamingBufferSize();
                    s.addProperty("streaming_buffer_bytes", bufferBytes);
                    JsonObject protocol = MainContextOutputCapture.protocol();
                    if (protocol.has("expected_streaming_buffer_bytes")
                            && bufferBytes != protocol.get("expected_streaming_buffer_bytes").getAsInt())
                        throw new IllegalStateException("unexpected drone streaming block size");
                    snapshots.add(s); pending.add(new PendingInput(s, ((MainOutputChannelAccessor) ch).mainOutput$getStream()));
                }
                if (declared.has("isolation")) {
                    String isolation = declared.get("isolation").getAsString();
                    if (!List.of("MIXED", "DRY_ONLY", "WET_ONLY").contains(isolation)) throw new IllegalArgumentException("unknown isolation");
                    for (int i = 0; i < channels.size(); i++) {
                        float gain = (isolation.equals("DRY_ONLY") && i == 2 || isolation.equals("WET_ONLY") && i < 2)
                                ? 0 : (i < 2 ? f.sounds.get(i).getVolume() : 1);
                        channels.get(i).setVolume(gain);
                        snapshots.get(i).getAsJsonObject().addProperty("isolation_gain", gain);
                    }
                }
                row.add("source_snapshots", snapshots); recordOptions(row);
                row.addProperty("output_clock_start_frames", CalibrationTransitionQueueCapture.clock());
                JsonObject before = new JsonObject(); appendOutput(before, render(channels, WARMUP + FRAMES)); row.add("before_change_output", before);
                row.addProperty("change_clock_frames", CalibrationTransitionQueueCapture.clock());
                row.add("publication_before_change", latestPublication());
                JsonArray atChange = new JsonArray(); channels.forEach(ch -> atChange.add(CalibrationTransitionQueueCapture.observe(ch, "change"))); row.add("native_at_change", atChange);
                // Change between actual sound-executor reads; do not flush, seek, stop, or rewrite queued PCM.
                JsonObject transitionProtocol = MainContextOutputCapture.protocol();
                if (transitionProtocol.has("transition_kind") && transitionProtocol.get("transition_kind").getAsString().equals("distance")) {
                    f.distance(declared.get("new_distance_m").getAsDouble());
                    var sourcePosition = f.state.audioSnapshot().source().positionMeters();
                    for (int i = 0; i < 2; i++) channels.get(i).setSelfPosition(new net.minecraft.world.phys.Vec3(
                            sourcePosition.x(), sourcePosition.y(), sourcePosition.z()));
                    JsonArray moved = new JsonArray();
                    for (int i = 0; i < channels.size(); i++) moved.add(nativeSnapshot(channels.get(i), f.sounds.get(i)));
                    row.add("native_after_distance_command", moved);
                } else f.calibration(declared.get("new_calibration_amplitude").getAsDouble());
                CalibratedGainCapture.phase(name + ":NEW"); CalibrationTransitionQueueCapture.phase(name + ":NEW");
                f.publish(); row.add("publication_after_change", latestPublication());
                JsonObject protocol = MainContextOutputCapture.protocol();
                int refillPause = protocol.has("refill_pause_frames") ? protocol.get("refill_pause_frames").getAsInt() : 0;
                JsonObject gap = new JsonObject(); gap.addProperty("requested_frames", refillPause);
                row.add("refill_pause", gap);
                JsonObject after = new JsonObject(); appendOutput(after, render(channels, FRAMES * 6, refillPause, gap)); row.add("after_change_output", after);
                row.addProperty("output_clock_end_frames", CalibrationTransitionQueueCapture.clock());
                for (int i = 0; i < channels.size(); i++) snapshots.get(i).getAsJsonObject().add("after", nativeSnapshot(channels.get(i), f.sounds.get(i)));
                return null;
            });
            active = null; closeAndDrain(context, name); row.add("history_after_stop", new Gson().toJsonTree(f.wetState.historyDiagnostics())); return row;
        } finally { active = null; f.close(); }
    }
    private static JsonObject latestPublication() { JsonArray a = WetMainOutputCapture.publications(); if (a.size() == 0) throw new IllegalStateException("no publication"); return a.get(a.size() - 1).getAsJsonObject().deepCopy(); }
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
        return render(channels, frames, 0, null);
    }
    private static byte[] render(List<Channel> channels, int frames, int refillPauseFrames, JsonObject gap) {
        if (refillPauseFrames < 0 || refillPauseFrames >= frames) throw new IllegalArgumentException("invalid refill pause");
        JsonObject protocol = MainContextOutputCapture.protocol();
        int renderStep = protocol.has("render_step_frames") ? protocol.get("render_step_frames").getAsInt() : 1024;
        if (renderStep <= 0 || renderStep > FRAMES) throw new IllegalArgumentException("invalid finite render step");
        var l = library(); MainContextOutputCapture.requireCurrent(l.mainOutput$getDevice(), l.mainOutput$getContext()); ByteBuffer out = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int f = 0; f < frames;) {
            int count = Math.min(renderStep, frames - f);
            if (f < refillPauseFrames) count = Math.min(count, refillPauseFrames - f);
            FloatBuffer samples = BufferUtils.createFloatBuffer(count); SOFTLoopback.alcRenderSamplesSOFT(l.mainOutput$getDevice(), samples, count);
            if (AL10.alGetError() != 0 || ALC10.alcGetError(l.mainOutput$getDevice()) != 0) throw new IllegalStateException("native render error");
            while (samples.hasRemaining()) out.putFloat(samples.get());
            CalibrationTransitionQueueCapture.advanced(count);
            channels.forEach(ch -> CalibrationTransitionQueueCapture.observe(ch, "rendered_before_update"));
            f += count;
            for (Channel c : channels) {
                int s = ((ChannelSourceAccessor) c).fpvdrone$getSource();
                if (AL10.alGetSourcei(s, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING
                        || AL10.alGetSourcei(s, AL10.AL_BUFFERS_QUEUED) <= AL10.alGetSourcei(s, AL10.AL_BUFFERS_PROCESSED))
                    throw new IllegalStateException("stream underrun before refill");
            }
            if (refillPauseFrames > 0 && f == refillPauseFrames) {
                gap.addProperty("completed_frames", f); gap.addProperty("clock_frames", CalibrationTransitionQueueCapture.clock());
                JsonArray atPause = new JsonArray(); channels.forEach(ch -> atPause.add(CalibrationTransitionQueueCapture.observe(ch, "refill_pause_boundary")));
                gap.add("native_before_refill", atPause);
            }
            if (f >= refillPauseFrames) for (Channel c : channels) {
                c.updateStream(); int s = ((ChannelSourceAccessor) c).fpvdrone$getSource();
                if (AL10.alGetSourcei(s, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING || AL10.alGetSourcei(s, AL10.AL_BUFFERS_QUEUED) < 1)
                    throw new IllegalStateException("stream underrun after refill");
            }
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
