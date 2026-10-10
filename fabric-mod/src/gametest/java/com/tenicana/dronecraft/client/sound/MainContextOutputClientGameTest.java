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

/** Actual Library/SoundManager software output, with a bounded application-driven clock. */
final class MainContextOutputClientGameTest {
    private static final int WAIT = 600, FRAMES = 48000, SEED = 153154;
    private static final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private static final ByteArrayOutputStream input = new ByteArrayOutputStream();

    static void run(ClientGameTestContext context) {
        JsonObject protocol = MainContextOutputCapture.protocol();
        Path root = Path.of(protocol.get("workspace_root").getAsString());
        Path reportPath = root.resolve(protocol.get("output_report").getAsString());
        JsonObject report = new JsonObject(), controls = new JsonObject(); JsonArray rows = new JsonArray();
        report.addProperty("started_utc", Instant.now().toString());
        report.addProperty("protocol_sha256", MainContextOutputCapture.sha(read(Path.of(System.getProperty(MainContextOutputCapture.PROPERTY)))));
        report.add("evidence_sha256", protocol.getAsJsonObject("evidence_sha256").deepCopy());
        var options = new EnumMap<SoundSource, Double>(SoundSource.class);
        Throwable failure = null;
        try {
            JsonObject loaded = new JsonObject();
            for (var value : protocol.getAsJsonArray("required_loaded_class_paths")) {
                String path = value.getAsString();
                String resource = "/" + path.substring(path.indexOf("com/tenicana/"));
                try (var stream = MainContextOutputClientGameTest.class.getResourceAsStream(resource)) {
                    if (stream == null) throw new IllegalStateException("loaded class resource missing: " + path);
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
            try (TestSingleplayerContext world = context.worldBuilder().create()) {
                world.getClientWorld().waitForChunksDownload();
                world.getServer().runCommand("execute as @p at @s run fpvdrone spawn racing_quad");
                world.getServer().runCommand("execute as @p at @s run fpvdrone diagnostic start 60");
                waitLive(context);
                controls.add("live_two_layer", captureLive(context));
                var frozen = new AtomicReference<DroneAcousticRenderState>();
                context.runOnClient(client -> {
                    DroneEntity drone = audibleDrone(client);
                    var state = new DroneAcousticRenderState();
                    state.updateSource(drone, new AcousticListenerFrame(new AcousticVector(drone.getX(), drone.getY(), drone.getZ() - 1), new AcousticVector(0, 0, 0)));
                    frozen.set(state);
                });
                world.getServer().runCommand("execute as @p at @s run fpvdrone diagnostic stop");
                context.waitFor(client -> !anyDroneAudible(client), WAIT);
                context.runOnClient(client -> engine(client).stopAll());
                controls.add("no_source", captureSilence(context));
                for (var value : protocol.getAsJsonArray("expected_cases"))
                    rows.add(captureCase(context, value.getAsJsonObject(), frozen.get()));
                JsonObject mute = new JsonObject(); mute.addProperty("name", "mute"); mute.addProperty("mode", "owned");
                mute.addProperty("distance_m", 1); mute.addProperty("instance_volume", .8); mute.addProperty("master", 0); mute.addProperty("category", 1);
                controls.add("mute", captureCase(context, mute, frozen.get()));
                setOptions(context, 1, 1);
                context.runOnClient(client -> client.getSoundManager().reload());
                world.getServer().runCommand("execute as @p at @s run fpvdrone diagnostic start 60");
                waitLive(context); controls.add("after_reload", captureLive(context));
                world.getServer().runCommand("execute as @p at @s run fpvdrone diagnostic stop");
            }
            context.runOnClient(client -> client.getSoundManager().destroy());
            report.addProperty("status", "main_context_output_captured");
        } catch (Throwable error) {
            failure = error; report.addProperty("status", "main_context_output_failed");
            report.addProperty("failure", error.toString());
            try { context.runOnClient(client -> client.getSoundManager().destroy()); }
            catch (Throwable cleanup) { report.addProperty("cleanup_failure", cleanup.toString()); }
        } finally {
            context.runOnClient(client -> options.forEach((source, value) -> client.options.getSoundSourceOptionInstance(source).set(value)));
        }
        report.add("rows", rows); report.add("controls", controls); report.add("epochs", MainContextOutputCapture.epochs());
        report.add("runtime_events", DistanceOwnershipRuntimeTrace.snapshot());
        JsonObject scope = new JsonObject(); scope.addProperty("actual_minecraft_main_context_output", output.size() > 0);
        scope.addProperty("actual_Minecraft_client_started", true); scope.addProperty("simulated_entity_rpm_only", true);
        for (String name : List.of("physical_endpoint_verified", "real_pressure_or_rpm_accessed", "wet_verified", "release_profile_eligible")) scope.addProperty(name, false);
        scope.addProperty("controlled_relative_source_only", true); scope.addProperty("HRTF_and_output_limiter_disabled_in_test", true);
        report.add("scope", scope);
        writePcm(root, protocol, report, "input", input.toByteArray());
        writePcm(root, protocol, report, "output", output.toByteArray());
        report.addProperty("finished_utc", Instant.now().toString());
        try { Files.writeString(reportPath, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardOpenOption.CREATE_NEW); }
        catch (Exception error) { throw new IllegalStateException("cannot preserve main-output report", error); }
        if (failure != null) throw new IllegalStateException("main-context output test failed", failure);
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

    private static byte[] render(List<Channel> channels) {
        var library = library(); long device = library.mainOutput$getDevice(), context = library.mainOutput$getContext();
        MainContextOutputCapture.requireCurrent(device, context);
        ByteBuffer bytes = ByteBuffer.allocate(FRAMES * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int frame = 0; frame < FRAMES; frame += 1024) {
            int count = Math.min(1024, FRAMES - frame);
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

    private static void appendInput(JsonObject row, ProceduralDroneAudioStream stream) {
        byte[] pcm = MainContextOutputCapture.input(stream);
        row.addProperty("input_offset_bytes", input.size()); row.addProperty("input_bytes", pcm.length);
        row.addProperty("input_sha256", MainContextOutputCapture.sha(pcm)); row.addProperty("input_record_identity", MainContextOutputCapture.identity(stream));
        row.addProperty("stream_identity", System.identityHashCode(stream)); input.writeBytes(pcm);
    }

    private static void setOptions(ClientGameTestContext context, double master, double category) {
        context.runOnClient(client -> {
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(master);
            client.options.getSoundSourceOptionInstance(SoundSource.NEUTRAL).set(category);
            client.getSoundManager().updateCategoryVolume(SoundSource.NEUTRAL, 1.0f);
        });
    }

    private static JsonObject captureCase(ClientGameTestContext context, JsonObject declared, DroneAcousticRenderState state) {
        setOptions(context, declared.get("master").getAsDouble(), declared.get("category").getAsDouble());
        boolean owned = declared.get("mode").getAsString().equals("owned");
        var stream = new ProceduralDroneAudioStream(state, Layer.MOTOR, SEED, owned);
        var sound = new TestSound(declared, stream);
        context.runOnClient(client -> client.getSoundManager().play(sound));
        context.waitFor(client -> handles(client).containsKey(sound), WAIT);
        var selected = new AtomicReference<ChannelAccess.ChannelHandle>();
        var allHandles = new ArrayList<ChannelAccess.ChannelHandle>();
        context.runOnClient(client -> { selected.set(handles(client).get(sound)); allHandles.addAll(handles(client).values()); });
        JsonObject row = onSoundThread(context, () -> {
            Channel channel = channel(selected.get());
            JsonObject actual = declared.deepCopy(); identity(actual);
            actual.addProperty("source", ((ChannelSourceAccessor) channel).fpvdrone$getSource());
            actual.addProperty("owner_verified", stream.distanceOwnerVerified());
            JsonArray playing = new JsonArray();
            for (var handle : allHandles) {
                Channel candidate = ((ChannelHandleChannelAccessor) handle).fpvdrone$getChannel();
                if (candidate != null) {
                    int source = ((ChannelSourceAccessor) candidate).fpvdrone$getSource();
                    if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) playing.add(source);
                }
            }
            actual.add("playing_sources", playing); actual.addProperty("active_playing_source_count", playing.size());
            if (playing.size() != 1 || playing.get(0).getAsInt() != actual.get("source").getAsInt())
                throw new IllegalStateException("isolated matrix has other actual playing sources");
            var client = Minecraft.getInstance();
            actual.addProperty("options_master", client.options.getSoundSourceVolume(SoundSource.MASTER));
            actual.addProperty("options_category", client.options.getSoundSourceVolume(SoundSource.NEUTRAL));
            actual.addProperty("gain_by_source_neutral", ((SoundEngineMainOutputAccessor) engine(client)).mainOutput$getGains().getFloat(SoundSource.NEUTRAL));
            actual.add("before", nativeSnapshot(channel)); appendOutput(actual, render(List.of(channel)));
            actual.add("after", nativeSnapshot(channel)); appendInput(actual, stream); return actual;
        });
        context.runOnClient(client -> client.getSoundManager().stop(sound));
        onSoundThread(context, () -> null); // Observe the actual queued stop before the next case.
        return row;
    }

    private static JsonObject captureSilence(ClientGameTestContext context) {
        onSoundThread(context, () -> null);
        return onSoundThread(context, () -> {
            JsonObject row = new JsonObject(); identity(row); int count = handles(Minecraft.getInstance()).size();
            row.addProperty("active_source_count", count); if (count != 0) throw new IllegalStateException("no-source control still has sound handles");
            appendOutput(row, render(List.of())); return row;
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

    private static void waitLive(ClientGameTestContext context) {
        context.waitFor(client -> {
            var active = handles(client).entrySet().stream().filter(item -> item.getKey() instanceof DroneLoopSoundInstance && !item.getValue().isStopped()).toList();
            return active.size() == 2 && active.stream().allMatch(item -> {
                int source = ((ChannelSourceAccessor) channel(item.getValue())).fpvdrone$getSource();
                return AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING && AL10.alGetSourcef(source, AL10.AL_GAIN) > 0;
            });
        }, WAIT);
    }

    private static JsonObject captureLive(ClientGameTestContext context) {
        var selected = new ArrayList<ChannelAccess.ChannelHandle>();
        var allHandles = new ArrayList<ChannelAccess.ChannelHandle>();
        context.runOnClient(client -> handles(client).forEach((sound, handle) -> {
            allHandles.add(handle);
            if (sound instanceof DroneLoopSoundInstance && !handle.isStopped()) selected.add(handle);
        }));
        return onSoundThread(context, () -> {
            List<Channel> channels = selected.stream().map(MainContextOutputClientGameTest::channel).toList();
            JsonObject row = new JsonObject(); identity(row); JsonArray sources = new JsonArray();
            JsonArray playing = new JsonArray();
            for (var handle : allHandles) {
                Channel candidate = ((ChannelHandleChannelAccessor) handle).fpvdrone$getChannel();
                if (candidate != null) {
                    int source = ((ChannelSourceAccessor) candidate).fpvdrone$getSource();
                    if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) playing.add(source);
                }
            }
            row.add("playing_sources", playing); row.addProperty("active_playing_source_count", playing.size());
            if (playing.size() != 2 || channels.size() != 2) throw new IllegalStateException("live capture does not isolate the actual drone pair");
            for (Channel channel : channels) {
                JsonObject source = new JsonObject(); var before = nativeSnapshot(channel);
                var stream = (ProceduralDroneAudioStream) ((MainOutputChannelAccessor) channel).mainOutput$getStream();
                source.addProperty("source", before.get("source").getAsInt()); source.addProperty("owner_verified", stream.distanceOwnerVerified());
                appendInput(source, stream);
                source.add("before", before); sources.add(source);
            }
            appendOutput(row, render(channels));
            for (int i = 0; i < channels.size(); i++) sources.get(i).getAsJsonObject().add("after", nativeSnapshot(channels.get(i)));
            row.add("source_snapshots", sources); return row;
        });
    }

    private static final class TestSound extends AbstractTickableSoundInstance implements FabricSoundInstance {
        final ProceduralDroneAudioStream stream;
        TestSound(JsonObject row, ProceduralDroneAudioStream stream) {
            super(DroneSoundEvents.MOTOR_LOOP, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.stream = stream; volume = row.get("instance_volume").getAsFloat(); pitch = 1; looping = true; relative = true;
            attenuation = row.get("mode").getAsString().equals("unity") ? Attenuation.NONE : Attenuation.LINEAR;
            x = 0; y = 0; z = -row.get("distance_m").getAsDouble();
        }
        @Override public void tick() { }
        @Override public boolean canStartSilent() { return true; }
        @Override public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary buffers, Identifier id, boolean loop) { return CompletableFuture.completedFuture(stream); }
    }
}
