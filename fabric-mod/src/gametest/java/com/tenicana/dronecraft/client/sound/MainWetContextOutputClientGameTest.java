package com.tenicana.dronecraft.client.sound;

import com.google.gson.*;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer.Layer;
import com.tenicana.dronecraft.client.mixin.*;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.sound.DroneSoundEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;

/** Finite actual-main-context wet/dry baseline. No production algorithm override. */
final class MainWetContextOutputClientGameTest {
    private static final int WAIT = 600, FRAMES = 48000, ENTITY = 155001;
    private static final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private static final ByteArrayOutputStream input = new ByteArrayOutputStream();
    private static final ByteArrayOutputStream mixed = new ByteArrayOutputStream();
    private static final List<PendingInput> pendingInputs = new ArrayList<>();
    private record PendingInput(JsonObject row, AudioStream stream) { }

    static void run(ClientGameTestContext context) {
        JsonObject protocol = MainContextOutputCapture.protocol();
        Path root = Path.of(protocol.get("workspace_root").getAsString());
        for (String name : List.of("mixed_pcm_file", "raw_pcm_file"))
            if (Files.exists(root.resolve(protocol.get(name).getAsString())))
                throw new IllegalStateException("refusing existing wet artifact " + name);
        JsonObject report = new JsonObject(), controls = new JsonObject(); JsonArray rows = new JsonArray();
        report.addProperty("started_utc", Instant.now().toString());
        report.addProperty("protocol_sha256", MainContextOutputCapture.sha(read(Path.of(System.getProperty(WetMainOutputCapture.PROPERTY)))));
        report.add("evidence_sha256", protocol.getAsJsonObject("evidence_sha256").deepCopy());
        var options = new EnumMap<SoundSource, Double>(SoundSource.class);
        Throwable failure = null;
        try {
            JsonObject loaded = new JsonObject();
            for (var value : protocol.getAsJsonArray("required_loaded_class_paths")) {
                String path = value.getAsString(), resource = "/" + path.substring(path.indexOf("com/tenicana/"));
                try (var stream = MainWetContextOutputClientGameTest.class.getResourceAsStream(resource)) {
                    if (stream == null) throw new IllegalStateException("loaded class missing " + path);
                    loaded.addProperty(path, MainContextOutputCapture.sha(stream.readAllBytes()));
                }
            }
            report.add("loaded_class_sha256", loaded);
            context.runOnClient(client -> {
                for (SoundSource source : SoundSource.values()) {
                    options.put(source, client.options.getSoundSourceOptionInstance(source).get());
                    client.options.getSoundSourceOptionInstance(source).set(source == SoundSource.NEUTRAL || source == SoundSource.MASTER ? 1.0 : 0.0);
                }
                client.getSoundManager().updateCategoryVolume(SoundSource.NEUTRAL, 1.0f);
            });
            context.runOnClient(client -> {
                SoundInstance probe = new ForeignProbeSound();
                WetSoundAdmissionCapture.probe("closed_music", probe, client.getSoundManager().play(probe));
            });
            WetSoundAdmissionCapture.live();
            try (TestSingleplayerContext world = context.worldBuilder().create()) {
                world.getClientWorld().waitForChunksDownload();
                world.getServer().runCommand("gamerule spawn_mobs false");
                world.getServer().runCommand("execute as @p at @s run fpvdrone spawn racing_quad");
                world.getServer().runCommand("execute as @p at @s run fpvdrone diagnostic start 60");
                context.waitFor(client -> {
                    long dry = handles(client).keySet().stream().filter(s -> s instanceof DroneLoopSoundInstance).count();
                    long wet = handles(client).keySet().stream().filter(s -> s instanceof ListenerReverbSoundInstance).count();
                    return dry == 2 && wet == 1 && WetMainOutputCapture.hasQualifiedPublication()
                            && handles(client).keySet().stream().filter(s -> s instanceof DroneLoopSoundInstance).allMatch(s -> s.getVolume() > 0);
                }, WAIT);
                waitNativeLive(context);
                controls.add("live_manager", captureLive(context));
                var frozen = new AtomicReference<DroneAcousticRenderState>();
                context.runOnClient(client -> {
                    DroneEntity drone = audibleDrone(client); var state = new DroneAcousticRenderState();
                    state.updateSource(drone, new AcousticListenerFrame(new AcousticVector(drone.getX(), drone.getY(), drone.getZ() - 1), new AcousticVector(0, 0, 0)));
                    frozen.set(state);
                });
                String frozenJson = new Gson().toJson(frozen.get().unoccludedEmissionFrame());
                report.addProperty("frozen_emission_json", frozenJson);
                report.addProperty("frozen_emission_sha256", MainContextOutputCapture.sha(frozenJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                world.getServer().runCommand("execute as @p at @s run fpvdrone diagnostic stop");
                context.waitFor(client -> !anyDroneAudible(client), WAIT);
                // Stop the normal manager before the finite isolated matrix.
                System.setProperty("fpvdrone.listenerReverb", "false");
                closeAndDrain(context, "live_complete");
                world.getServer().runCommand("kill @e[type=fpvdrone:drone]");
                context.waitFor(client -> {
                    for (var entity : client.level.entitiesForRendering()) if (entity instanceof DroneEntity) return false;
                    return true;
                }, WAIT);
                context.runOnClient(client -> engine(client).stopAll());
                onSoundThread(context, () -> null);
                controls.add("no_source", captureSilence(context));
                for (var value : protocol.getAsJsonArray("expected_cases"))
                    rows.add(captureCase(context, value.getAsJsonObject(), frozen.get()));
                controls.add("zero_wet_gain", captureCase(context, protocol.getAsJsonObject("zero_wet_gain"), frozen.get()));
                setOptions(context, 1, 1);
                context.runOnClient(client -> client.getSoundManager().reload());
                controls.add("reload_wet_anchor", captureCase(context, protocol.getAsJsonObject("reload_wet_anchor"), frozen.get()));
            }
            closeAndDrain(context, "final");
            context.runOnClient(client -> client.getSoundManager().destroy());
            report.addProperty("status", "wet_main_context_output_captured");
        } catch (Throwable error) {
            failure = error; report.addProperty("status", "wet_main_context_output_failed"); report.addProperty("failure", error.toString());
            try { context.runOnClient(client -> client.getSoundManager().destroy()); }
            catch (Throwable cleanup) { report.addProperty("cleanup_failure", cleanup.toString()); }
        } finally {
            context.runOnClient(client -> options.forEach((source, value) -> client.options.getSoundSourceOptionInstance(source).set(value)));
        }
        report.add("rows", rows); report.add("controls", controls); report.add("publications", WetMainOutputCapture.publications());
        report.add("epochs", MainContextOutputCapture.epochs()); report.add("runtime_events", DistanceOwnershipRuntimeTrace.snapshot());
        report.add("channel_allocations", WetChannelLifecycleCapture.allocations());
        report.add("channel_calls", WetChannelLifecycleCapture.calls());
        report.add("sound_admission", WetSoundAdmissionCapture.snapshot());
        JsonObject scope = new JsonObject();
        for (String name : List.of("actual_minecraft_main_context_output", "actual_Minecraft_client_started", "simulated_entity_rpm_only", "controlled_relative_source_only", "HRTF_and_output_limiter_disabled_in_test", "gametest_only_nonmutating_taps")) scope.addProperty(name, true);
        for (String name : List.of("physical_endpoint_verified", "real_pressure_or_rpm_accessed", "absolute_wet_calibration_verified", "six_drone_full_client_verified", "release_profile_eligible")) scope.addProperty(name, false);
        report.add("scope", scope);
        scope.addProperty("test_stimulus_admission_before_allocation", true);
        writePcm(root, protocol, report, "input", input.toByteArray()); writePcm(root, protocol, report, "output", output.toByteArray());
        writePcm(root, protocol, report, "mixed", mixed.toByteArray()); writePcm(root, protocol, report, "raw", WetMainOutputCapture.rawBytes());
        report.addProperty("finished_utc", Instant.now().toString());
        try { Files.writeString(root.resolve(protocol.get("output_report").getAsString()), new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report) + "\n", StandardOpenOption.CREATE_NEW); }
        catch (Exception error) { throw new IllegalStateException("cannot preserve wet report", error); }
        if (failure != null) throw new IllegalStateException("wet main output failed", failure);
    }

    private static void appendInput(JsonObject row, AudioStream stream) {
        byte[] pcm = MainContextOutputCapture.input(stream);
        row.addProperty("input_offset_bytes", input.size()); row.addProperty("input_bytes", pcm.length);
        row.addProperty("input_sha256", MainContextOutputCapture.sha(pcm)); row.addProperty("input_record_identity", MainContextOutputCapture.identity(stream));
        row.addProperty("stream_identity", System.identityHashCode(stream)); row.addProperty("stream_class", stream.getClass().getName()); input.writeBytes(pcm);
        if (stream instanceof ListenerReverbAudioStream wet) {
            row.addProperty("owner_verified", false);
            byte[] values = WetMainOutputCapture.mixed(wet);
            row.addProperty("preroll_frames", wet.prerollFrames()); row.addProperty("mixed_offset_bytes", mixed.size());
            row.addProperty("mixed_bytes", values.length); row.addProperty("mixed_sha256", MainContextOutputCapture.sha(values)); mixed.writeBytes(values);
            row.add("mixer_batches", WetMainOutputCapture.batches(wet));
        }
    }

    private static void queueInput(JsonObject row, AudioStream stream) {
        row.addProperty("owner_verified", stream instanceof ProceduralDroneAudioStream dry && dry.distanceOwnerVerified());
        pendingInputs.add(new PendingInput(row, stream));
    }

    private static void sealInputs() {
        for (PendingInput item : pendingInputs) {
            boolean closed = WetMainOutputCapture.closed(item.stream());
            item.row().addProperty("stream_closed_after_cleanup", closed);
            if (!closed) throw new IllegalStateException("cannot seal input before actual selected stream closure");
            appendInput(item.row(), item.stream());
        }
        pendingInputs.clear();
    }

    private static JsonObject captureCase(ClientGameTestContext context, JsonObject declared, DroneAcousticRenderState fixed) {
        setOptions(context, declared.get("master").getAsDouble(), declared.get("category").getAsDouble());
        boolean wet = declared.get("mode").getAsString().equals("wet");
        var sounds = new ArrayList<SoundInstance>();
        ListenerReverbState wetState = null;
        JsonObject row = declared.deepCopy();
        if (wet) {
            wetState = new ListenerReverbState();
            double a = declared.get("instance_volume").getAsDouble() / declared.get("distance_m").getAsDouble();
            var sources = new ArrayList<ListenerReverbState.Source>();
            for (int i = 0; i < declared.get("source_count").getAsInt(); i++)
                sources.add(new ListenerReverbState.Source(ENTITY + i, fixed.unoccludedEmissionFrame(), a, a));
            wetState.publishSources(sources);
            wetState.publishEnvironment(new FdnEnvironmentMapper.Controls(155L, new AcousticBands(1.2, .9, .6), declared.get("wet_gain").getAsDouble(), 0));
            row.add("wet_sources", new Gson().toJsonTree(sources));
            row.add("wet_environment", new Gson().toJsonTree(wetState.snapshot().environment()));
            row.addProperty("history_frames_before_play", wetState.historyDiagnostics().historyFrames());
            sounds.add(new ListenerReverbSoundInstance(wetState));
        } else {
            sounds.add(new TestSound(declared, new ProceduralDroneAudioStream(fixed, Layer.MOTOR, 31 * ENTITY + 17, true)));
            sounds.add(new TestSound(declared, new ProceduralDroneAudioStream(fixed, Layer.PROPELLER, 31 * ENTITY + 23, true)));
        }
        WetSoundAdmissionCapture.exact(declared.get("name").getAsString(), sounds);
        if (!wet && declared.get("name").getAsString().equals("dry_n1_p_low_a_m1_c1")) {
            context.runOnClient(client -> {
                SoundInstance probe = new TestSound(declared, ((TestSound) sounds.get(0)).stream);
                WetSoundAdmissionCapture.probe("unregistered_same_class", probe, client.getSoundManager().play(probe));
            });
        }
        context.runOnClient(client -> sounds.forEach(client.getSoundManager()::play));
        context.waitFor(client -> sounds.stream().allMatch(s -> handles(client).containsKey(s)), WAIT);
        var selected = new ArrayList<ChannelAccess.ChannelHandle>();
        context.runOnClient(client -> sounds.forEach(s -> selected.add(handles(client).get(s))));
        JsonObject actual = onSoundThread(context, () -> {
            identity(row); row.addProperty("render_frames", FRAMES); row.addProperty("rendered_frames_before", 0);
            JsonArray playing = playing(); row.add("playing_sources", playing); row.addProperty("active_playing_source_count", playing.size());
            if (playing.size() != sounds.size()) throw new IllegalStateException("matrix not isolated");
            List<Channel> channels = selected.stream().map(MainWetContextOutputClientGameTest::channel).toList();
            JsonArray snapshots = new JsonArray();
            for (int i = 0; i < channels.size(); i++) {
                JsonObject source = new JsonObject(); source.addProperty("source", ((ChannelSourceAccessor) channels.get(i)).fpvdrone$getSource());
                source.addProperty("channel_allocation_identity", WetChannelLifecycleCapture.identity(channels.get(i)));
                source.addProperty("sound_instance_volume", sounds.get(i).getVolume());
                source.addProperty("sound_instance_class", sounds.get(i).getClass().getName());
                if (!wet) { source.addProperty("entity_id", ENTITY); source.addProperty("layer", i == 0 ? "MOTOR" : "PROPELLER"); source.addProperty("seed", 31 * ENTITY + (i == 0 ? 17 : 23)); }
                source.add("before", nativeSnapshot(channels.get(i))); snapshots.add(source);
            }
            recordOptions(row); appendOutput(row, render(channels, FRAMES));
            for (int i = 0; i < channels.size(); i++) {
                var source = snapshots.get(i).getAsJsonObject(); source.add("after", nativeSnapshot(channels.get(i)));
                queueInput(source, ((MainOutputChannelAccessor) channels.get(i)).mainOutput$getStream());
            }
            row.add("source_snapshots", snapshots); return row;
        });
        closeAndDrain(context, declared.get("name").getAsString());
        if (wetState != null) actual.add("history_after_stop", new Gson().toJsonTree(wetState.historyDiagnostics()));
        return actual;
    }

    private static void recordOptions(JsonObject row) {
        Minecraft client = Minecraft.getInstance();
        row.addProperty("options_master", client.options.getSoundSourceVolume(SoundSource.MASTER));
        row.addProperty("options_category", client.options.getSoundSourceVolume(SoundSource.NEUTRAL));
        row.addProperty("gain_by_source_neutral", ((SoundEngineMainOutputAccessor) engine(client)).mainOutput$getGains().getFloat(SoundSource.NEUTRAL));
        row.addProperty("library_debug", ((SoundEngineMainOutputAccessor) engine(client)).mainOutput$getLibrary().getDebugString());
    }

    private static JsonArray playing() {
        JsonArray result = new JsonArray();
        for (var handle : handles(Minecraft.getInstance()).values()) {
            Channel candidate = ((ChannelHandleChannelAccessor) handle).fpvdrone$getChannel();
            if (candidate != null) {
                int source = ((ChannelSourceAccessor) candidate).fpvdrone$getSource();
                if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) result.add(source);
            }
        }
        return result;
    }

    private static JsonObject captureSilence(ClientGameTestContext context) {
        onSoundThread(context, () -> null);
        return onSoundThread(context, () -> {
            JsonObject row = new JsonObject(); identity(row); row.addProperty("render_frames", FRAMES);
            row.addProperty("active_source_count", handles(Minecraft.getInstance()).size());
            if (handles(Minecraft.getInstance()).size() != 0) throw new IllegalStateException("stale no-source handles");
            appendOutput(row, render(List.of(), FRAMES)); recordOptions(row); return row;
        });
    }

    private static void closeAndDrain(ClientGameTestContext context, String reason) {
        context.runOnClient(client -> WetSoundAdmissionCapture.close(reason));
        // Drain the OLD executor before stopAll shuts it down and drops queued work.
        context.waitFor(client -> WetSoundAdmissionCapture.drained(), WAIT);
        onSoundThread(context, () -> null);
        if (!WetSoundAdmissionCapture.drained()) throw new IllegalStateException("old executor did not drain");
        onSoundThread(context, () -> { WetSoundAdmissionCapture.reclaimUnattached(); return null; });
        context.runOnClient(client -> engine(client).stopAll());
        onSoundThread(context, () -> { sealInputs(); return null; });
        context.runOnClient(client -> WetSoundAdmissionCapture.barrier(handles(client).size()));
    }

    private static JsonObject captureLive(ClientGameTestContext context) {
        var selected = new ArrayList<ChannelAccess.ChannelHandle>(); var sounds = new ArrayList<SoundInstance>();
        context.runOnClient(client -> handles(client).forEach((sound, handle) -> {
            if (sound instanceof DroneLoopSoundInstance || sound instanceof ListenerReverbSoundInstance) { selected.add(handle); sounds.add(sound); }
        }));
        JsonArray publication = WetMainOutputCapture.publications();
        return onSoundThread(context, () -> {
            JsonObject row = new JsonObject(); identity(row); row.addProperty("render_frames", 240000);
            row.addProperty("publication_count_before_render", publication.size());
            if (publication.size() == 0) throw new IllegalStateException("no actual publication");
            row.add("publication_before_render", publication.get(publication.size() - 1).deepCopy());
            JsonArray playing = playing(); row.add("playing_sources", playing); row.addProperty("active_playing_source_count", playing.size());
            if (selected.size() != 3 || playing.size() != 3) throw new IllegalStateException("live manager lacks actual two dry plus wet bus");
            List<Channel> channels = selected.stream().map(MainWetContextOutputClientGameTest::channel).toList();
            JsonArray snapshots = new JsonArray();
            for (int i = 0; i < channels.size(); i++) {
                var source = new JsonObject(); source.addProperty("source", ((ChannelSourceAccessor) channels.get(i)).fpvdrone$getSource());
                source.addProperty("channel_allocation_identity", WetChannelLifecycleCapture.identity(channels.get(i)));
                source.addProperty("sound_instance_volume", sounds.get(i).getVolume()); source.addProperty("sound_instance_class", sounds.get(i).getClass().getName());
                source.addProperty("sound_identity", System.identityHashCode(sounds.get(i)));
                if (sounds.get(i) instanceof DroneLoopSoundInstance dry) source.addProperty("layer", dry.layer().name());
                source.add("before", nativeSnapshot(channels.get(i))); snapshots.add(source);
            }
            recordOptions(row); appendOutput(row, render(channels, 240000));
            for (int i = 0; i < channels.size(); i++) {
                var source = snapshots.get(i).getAsJsonObject(); source.add("after", nativeSnapshot(channels.get(i)));
                queueInput(source, ((MainOutputChannelAccessor) channels.get(i)).mainOutput$getStream());
            }
            row.add("source_snapshots", snapshots); return row;
        });
    }

    private static void waitNativeLive(ClientGameTestContext context) {
        for (int attempt = 0; attempt < WAIT; attempt++) {
            var selected = new ArrayList<ChannelAccess.ChannelHandle>();
            context.runOnClient(client -> handles(client).forEach((sound, handle) -> {
                if (sound instanceof DroneLoopSoundInstance || sound instanceof ListenerReverbSoundInstance) selected.add(handle);
            }));
            boolean ready = onSoundThread(context, () -> {
                if (selected.size() != 3) return false;
                int owned = 0, bus = 0;
                for (var handle : selected) {
                    Channel channel = channel(handle);
                    var stream = ((MainOutputChannelAccessor) channel).mainOutput$getStream();
                    if (stream instanceof ProceduralDroneAudioStream dry && dry.distanceOwnerVerified()) owned++;
                    else if (stream instanceof ListenerReverbAudioStream) bus++;
                    else return false;
                    int source = ((ChannelSourceAccessor) channel).fpvdrone$getSource();
                    if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING || AL10.alGetSourcef(source, AL10.AL_GAIN) <= 0) return false;
                }
                if (AL10.alGetError() != 0) throw new IllegalStateException("native live readiness error");
                return owned == 2 && bus == 1;
            });
            if (ready) return;
        }
        throw new IllegalStateException("current native live voices never reached positive gain");
    }

    private static final class TestSound extends AbstractTickableSoundInstance implements FabricSoundInstance {
        final ProceduralDroneAudioStream stream;
        TestSound(JsonObject row, ProceduralDroneAudioStream stream) {
            super(DroneSoundEvents.MOTOR_LOOP, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.stream = stream; volume = row.get("instance_volume").getAsFloat(); pitch = 1; looping = true; relative = true;
            attenuation = Attenuation.LINEAR; x = 0; y = 0; z = -row.get("distance_m").getAsDouble();
        }
        @Override public void tick() { }
        @Override public boolean canStartSilent() { return true; }
        @Override public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary buffers, Identifier id, boolean loop) { return CompletableFuture.completedFuture(stream); }
    }
    private static final class ForeignProbeSound extends AbstractTickableSoundInstance {
        ForeignProbeSound() { super(DroneSoundEvents.MOTOR_LOOP, SoundSource.MUSIC, SoundInstance.createUnseededRandom()); volume = 0; }
        @Override public void tick() { }
        @Override public boolean canStartSilent() { return true; }
    }
    private static void writePcm(Path root, JsonObject protocol, JsonObject report, String name, byte[] pcm) {
        String path = protocol.get(name + "_pcm_file").getAsString();
        try { Files.write(root.resolve(path), pcm, StandardOpenOption.CREATE_NEW); }
        catch (Exception error) { throw new IllegalStateException("cannot preserve " + name + " PCM", error); }
        report.addProperty(name + "_pcm_file", path); report.addProperty(name + "_pcm_bytes", pcm.length);
        report.addProperty(name + "_pcm_sha256", MainContextOutputCapture.sha(pcm));
    }

    private static byte[] read(Path path) {
        try { return Files.readAllBytes(path); } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static SoundEngine engine(Minecraft client) {
        return ((SoundManagerEngineAccessor) client.getSoundManager()).fpvdrone$getSoundEngine();
    }

    private static Map<SoundInstance, ChannelAccess.ChannelHandle> handles(Minecraft client) {
        return ((SoundEngineChannelsAccessor) engine(client)).fpvdrone$getInstanceToChannel();
    }

    private static Channel channel(ChannelAccess.ChannelHandle handle) {
        Channel channel = ((ChannelHandleChannelAccessor) handle).fpvdrone$getChannel();
        if (channel == null) throw new IllegalStateException("selected real channel retired"); return channel;
    }

    private static <T> T onSoundThread(ClientGameTestContext context, Supplier<T> action) {
        CompletableFuture<T> done = new CompletableFuture<>();
        context.runOnClient(client -> ((SoundEngineExecutorAccessor) engine(client)).fpvdrone$getExecutor().schedule(() -> {
            try { done.complete(action.get()); } catch (Throwable error) { done.completeExceptionally(error); }
        }));
        context.waitFor(client -> done.isDone(), WAIT); return done.join();
    }

    private static MainOutputLibraryAccessor library() {
        var library = ((SoundEngineMainOutputAccessor) engine(Minecraft.getInstance())).mainOutput$getLibrary();
        return (MainOutputLibraryAccessor) library;
    }

    private static byte[] render(List<Channel> channels, int frames) {
        var library = library(); long device = library.mainOutput$getDevice(), context = library.mainOutput$getContext();
        MainContextOutputCapture.requireCurrent(device, context);
        ByteBuffer bytes = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int frame = 0; frame < frames; frame += 1024) {
            int count = Math.min(1024, frames - frame);
            FloatBuffer samples = BufferUtils.createFloatBuffer(count);
            SOFTLoopback.alcRenderSamplesSOFT(device, samples, count);
            if (ALC10.alcGetError(device) != 0 || AL10.alGetError() != 0) throw new IllegalStateException("actual main render native error");
            while (samples.hasRemaining()) bytes.putFloat(samples.get());
            for (Channel channel : channels) {
                channel.updateStream(); int source = ((ChannelSourceAccessor) channel).fpvdrone$getSource();
                if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING || AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) < 1)
                    throw new IllegalStateException("main render streaming source underrun");
            }
        }
        MainContextOutputCapture.requireCurrent(device, context); return bytes.array();
    }

    private static JsonObject nativeSnapshot(Channel channel) {
        int source = ((ChannelSourceAccessor) channel).fpvdrone$getSource();
        JsonObject row = DistanceOwnershipRuntimeTrace.event("main_output_source", source);
        row.addProperty("relative", AL10.alGetSourcei(source, AL10.AL_SOURCE_RELATIVE) != 0);
        row.addProperty("pitch", AL10.alGetSourcef(source, AL10.AL_PITCH));
        row.addProperty("listener_gain", AL10.alGetListenerf(AL10.AL_GAIN));
        row.addProperty("sample_offset", AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET));
        FloatBuffer position = BufferUtils.createFloatBuffer(3); AL10.alGetSourcefv(source, AL10.AL_POSITION, position);
        JsonArray coordinates = new JsonArray(); while (position.hasRemaining()) coordinates.add(position.get()); row.add("position", coordinates);
        row.addProperty("al_error_after_extra_getters", AL10.alGetError());
        row.addProperty("alc_error", ALC10.alcGetError(library().mainOutput$getDevice())); return row;
    }

    private static void identity(JsonObject row) {
        var library = library(); row.addProperty("device", library.mainOutput$getDevice()); row.addProperty("context", library.mainOutput$getContext());
        row.addProperty("generation", MainContextOutputCapture.generation(library.mainOutput$getDevice(), library.mainOutput$getContext()));
    }

    private static void appendOutput(JsonObject row, byte[] pcm) {
        row.addProperty("output_offset_bytes", output.size()); row.addProperty("output_bytes", pcm.length);
        row.addProperty("output_sha256", MainContextOutputCapture.sha(pcm)); output.writeBytes(pcm);
    }

    private static void setOptions(ClientGameTestContext context, double master, double category) {
        context.runOnClient(client -> {
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(master);
            client.options.getSoundSourceOptionInstance(SoundSource.NEUTRAL).set(category);
            client.getSoundManager().updateCategoryVolume(SoundSource.NEUTRAL, 1.0f);
        });
    }

    private static DroneEntity audibleDrone(Minecraft client) {
        for (var entity : client.level.entitiesForRendering()) if (entity instanceof DroneEntity drone && drone.getAverageMotorRpm() > 1000) return drone;
        throw new IllegalStateException("actual drone not audible");
    }

    private static boolean anyDroneAudible(Minecraft client) {
        for (var entity : client.level.entitiesForRendering()) if (entity instanceof DroneEntity drone && drone.getAverageMotorRpm() > 1000) return true;
        return false;
    }

}
