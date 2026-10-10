package com.tenicana.dronecraft.client.sound;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Actual SoundManager/Channel/mixin metadata; no output PCM or physical capture. */
final class ProductionDistanceClientGameTest {
    private static final int WAIT = 600;

    static void run(ClientGameTestContext context) {
        try {
            Path protocolPath = Path.of(System.getProperty("fpvdrone.acoustics.distanceIntegrationProtocol"));
            JsonObject protocol = JsonParser.parseString(Files.readString(protocolPath)).getAsJsonObject();
            Path root = Path.of(protocol.get("workspace_root").getAsString());
            Path output = root.resolve(protocol.get("output_minecraft_report").getAsString());
            if (Files.exists(output)) throw new IllegalStateException("refusing existing integration report");
            for (var item : protocol.getAsJsonObject("evidence_sha256").entrySet()) {
                if (!sha(Files.readAllBytes(root.resolve(item.getKey()))).equals(item.getValue().getAsString()))
                    throw new IllegalStateException("integration evidence changed: " + item.getKey());
            }
            JsonObject report = new JsonObject();
            report.addProperty("started_utc", Instant.now().toString());
            report.addProperty("protocol_sha256", sha(Files.readAllBytes(protocolPath)));
            report.add("evidence_sha256", protocol.getAsJsonObject("evidence_sha256").deepCopy());
            JsonArray stages = new JsonArray(), controls = new JsonArray();
            try (TestSingleplayerContext world = context.worldBuilder().create()) {
                world.getClientWorld().waitForChunksDownload();
                world.getServer().runCommand("execute as @p at @s run fpvdrone spawn racing_quad");
                world.getServer().runCommand("execute as @p at @s run fpvdrone diagnostic start 60");
                context.waitFor(ProductionDistanceClientGameTest::hasDroneChannels, WAIT);
                stages.add(captureLive(context, "initial"));
                context.runOnClient(client -> client.getSoundManager().reload());
                context.waitFor(ProductionDistanceClientGameTest::hasDroneChannels, WAIT);
                stages.add(captureLive(context, "after_reload"));
                List<TestSound> sounds = new ArrayList<>();
                context.runOnClient(client -> {
                    DroneEntity drone = audibleDrone(client);
                    AcousticListenerFrame listener = new AcousticListenerFrame(
                            new AcousticVector(client.player.getX(), client.player.getY(), client.player.getZ()),
                            new AcousticVector(0, 0, 0));
                    var ownedState = new DroneAcousticRenderState(); ownedState.updateSource(drone, listener);
                    var cleanState = new DroneAcousticRenderState(); cleanState.updateSource(drone, listener);
                    sounds.add(new TestSound("owned_volume4", 4, CompletableFuture.completedFuture(
                            new ProceduralDroneAudioStream(ownedState, Layer.MOTOR, 2, true))));
                    sounds.add(new TestSound("clean_procedural", .8f, CompletableFuture.completedFuture(
                            new ProceduralDroneAudioStream(cleanState, Layer.MOTOR, 3, false))));
                    sounds.add(new TestSound("ordinary_stream", .2f, CompletableFuture.completedFuture(new ToneStream())));
                    sounds.add(new TestSound("resource_ogg", .3f, new SoundBufferLibrary(client.getResourceManager())
                            .getStream(Identifier.fromNamespaceAndPath("fpvdrone", "sounds/drone/motor_loop.ogg"), true)));
                    for (TestSound sound : sounds) client.getSoundManager().play(sound);
                });
                context.waitFor(client -> sounds.stream().allMatch(sound -> handles(client).containsKey(sound)), WAIT);
                for (TestSound sound : sounds) controls.add(captureSound(context, sound));
                context.runOnClient(client -> sounds.forEach(sound -> client.getSoundManager().stop(sound)));
                for (String variant : List.of("stale_before_attach", "reset_after_attach", "native_error_after_attach")) {
                    AtomicReference<TestSound> sound = new AtomicReference<>();
                    AtomicReference<DroneAcousticRenderState> state = new AtomicReference<>();
                    AtomicReference<ProceduralDistanceOwner.Result> fault = new AtomicReference<>();
                    context.runOnClient(client -> {
                        var frame = new DroneAcousticRenderState();
                        frame.updateSource(audibleDrone(client), new AcousticListenerFrame(new AcousticVector(0, 0, 0), new AcousticVector(0, 0, 0)));
                        var stream = new ProceduralDroneAudioStream(frame, Layer.MOTOR, 8, true);
                        if (variant.equals("stale_before_attach")) new ProceduralDroneAudioStream(frame, Layer.MOTOR, 9, true);
                        DistanceOwnershipRuntimeTrace.setAfterAttach((attached, source) -> {
                            if (attached != stream) return;
                            if (variant.equals("reset_after_attach")) frame.resetDistanceOwnership();
                            if (variant.equals("native_error_after_attach")) {
                                AL10.alSourcef(source, -1, 1);
                                fault.set(ProceduralDistanceOwner.onStreamAttached(stream, source));
                            }
                        });
                        state.set(frame);
                        sound.set(new TestSound(variant, .4f, CompletableFuture.completedFuture(stream)));
                        client.getSoundManager().play(sound.get());
                    });
                    int identity = System.identityHashCode(sound.get().stream.join());
                    context.waitFor(client -> rejectedAndDestroyed(identity), WAIT);
                    JsonObject row = new JsonObject();
                    row.addProperty("name", variant); row.addProperty("stream_identity", identity);
                    row.addProperty("rejected_and_destroyed", true);
                    row.addProperty("sticky_failure", state.get().distanceOwnershipFailed());
                    if (fault.get() != null) {
                        row.addProperty("native_fault_status", fault.get().status().name());
                        row.addProperty("native_fault_error", fault.get().alError());
                    }
                    controls.add(row);
                    DistanceOwnershipRuntimeTrace.setAfterAttach(null);
                }
                stages.add(captureLive(context, "after_controls"));
            } finally {
                DistanceOwnershipRuntimeTrace.setAfterAttach(null);
            }
            context.waitFor(client -> handles(client).keySet().stream().noneMatch(instance -> instance instanceof DroneLoopSoundInstance), WAIT);
            report.add("stages", stages); report.add("controls", controls);
            report.add("runtime_events", DistanceOwnershipRuntimeTrace.snapshot());
            report.addProperty("minecraft_sound_manager_and_channel_hooks_exercised", true);
            report.addProperty("sound_reload_and_world_unload_exercised", true);
            report.addProperty("real_pressure_or_output_pcm_captured", false);
            report.addProperty("physical_endpoint_verified", false);
            report.addProperty("release_profile_eligible", false);
            report.addProperty("finished_utc", Instant.now().toString());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardOpenOption.CREATE_NEW);
        } catch (Exception error) {
            throw new IllegalStateException("production distance integration failed", error);
        }
    }

    private static Map<SoundInstance, ChannelAccess.ChannelHandle> handles(Minecraft client) {
        return ((SoundEngineChannelsAccessor) ((SoundManagerEngineAccessor) client.getSoundManager()).fpvdrone$getSoundEngine()).fpvdrone$getInstanceToChannel();
    }

    private static DroneEntity audibleDrone(Minecraft client) {
        for (var entity : client.level.entitiesForRendering())
            if (entity instanceof DroneEntity drone && drone.getAverageMotorRpm() > 1000) return drone;
        throw new IllegalStateException("audible production drone not ready");
    }

    private static boolean hasDroneChannels(Minecraft client) {
        return handles(client).entrySet().stream().filter(item -> item.getKey() instanceof DroneLoopSoundInstance
                && !item.getValue().isStopped()).count() >= 2;
    }

    private static <T> T onSoundThread(ClientGameTestContext context, Supplier<T> action) {
        CompletableFuture<T> done = new CompletableFuture<>();
        context.runOnClient(client -> ((SoundEngineExecutorAccessor)
                ((SoundManagerEngineAccessor) client.getSoundManager()).fpvdrone$getSoundEngine())
                .fpvdrone$getExecutor().schedule(() -> {
                    try { done.complete(action.get()); } catch (Throwable error) { done.completeExceptionally(error); }
                }));
        context.waitFor(client -> done.isDone(), WAIT);
        return done.join();
    }

    private static JsonObject captureLive(ClientGameTestContext context, String stage) {
        List<ChannelAccess.ChannelHandle> selected = new ArrayList<>();
        context.runOnClient(client -> handles(client).forEach((instance, handle) -> {
            if (instance instanceof DroneLoopSoundInstance && !handle.isStopped()) selected.add(handle);
        }));
        return onSoundThread(context, () -> {
            JsonObject row = new JsonObject(); row.addProperty("stage", stage);
            JsonArray sources = new JsonArray();
            for (var handle : selected) {
                var channel = ((ChannelHandleChannelAccessor) handle).fpvdrone$getChannel();
                if (channel == null) throw new IllegalStateException("drone handle retired before capture");
                int source = ((ChannelSourceAccessor) channel).fpvdrone$getSource();
                JsonObject actual = DistanceOwnershipRuntimeTrace.event(stage, source);
                if (actual.get("source_model").getAsInt() != AL10.AL_INVERSE_DISTANCE_CLAMPED
                        || actual.get("al_error").getAsInt() != 0)
                    throw new IllegalStateException("production distance source not verified: " + actual);
                sources.add(actual);
            }
            if (sources.size() < 2) throw new IllegalStateException("two production sources required");
            row.add("sources", sources); return row;
        });
    }

    private static JsonObject captureSound(ClientGameTestContext context, TestSound sound) {
        AtomicReference<ChannelAccess.ChannelHandle> handle = new AtomicReference<>();
        context.runOnClient(client -> handle.set(handles(client).get(sound)));
        JsonObject row = onSoundThread(context, () -> {
            var channel = ((ChannelHandleChannelAccessor) handle.get()).fpvdrone$getChannel();
            if (channel == null) throw new IllegalStateException("control channel retired");
            int source = ((ChannelSourceAccessor) channel).fpvdrone$getSource();
            JsonObject result = DistanceOwnershipRuntimeTrace.event(sound.name, source);
            int expected = sound.name.startsWith("owned") ? AL10.AL_INVERSE_DISTANCE_CLAMPED : 53251;
            if (result.get("source_model").getAsInt() != expected || result.get("al_error").getAsInt() != 0)
                throw new IllegalStateException("control distance model mismatch: " + result);
            channel.setSelfPosition(new Vec3(0, 0, -56)); channel.setVolume(.25f);
            JsonObject moved = DistanceOwnershipRuntimeTrace.event("controlled_position_gain_update", source);
            if (moved.get("source_model").getAsInt() != expected || moved.get("gain").getAsFloat() != .25f
                    || moved.get("al_error").getAsInt() != 0) throw new IllegalStateException("distance update drift");
            result.add("after_position_gain_update", moved); return result;
        });
        row.addProperty("name", sound.name);
        row.addProperty("stream_identity", System.identityHashCode(sound.stream.join()));
        row.addProperty("requested_instance_volume", sound.volumeValue);
        return row;
    }

    private static boolean rejectedAndDestroyed(int identity) {
        var events = DistanceOwnershipRuntimeTrace.snapshot();
        int source = -1, attach = -1; long context = 0;
        for (var value : events) {
            var row = value.getAsJsonObject();
            if (row.has("stream_identity") && row.get("stream_identity").getAsInt() == identity) {
                source = row.get("source").getAsInt(); context = row.get("context").getAsLong(); attach = row.get("event_index").getAsInt();
            }
        }
        boolean rejected = false;
        for (var value : events) {
            var row = value.getAsJsonObject();
            if (row.get("event_index").getAsInt() <= attach || row.get("source").getAsInt() != source
                    || row.get("context").getAsLong() != context) continue;
            if (row.get("stage").getAsString().equals("attach_head")) return false;
            if (row.get("stage").getAsString().equals("play_head") && row.get("rejected").getAsBoolean()) rejected = true;
            if (row.get("stage").getAsString().equals("play_tail") && !row.get("rejected").getAsBoolean()) throw new IllegalStateException("rejected source played");
            if (row.get("stage").getAsString().equals("destroy_tail") && !row.get("source_still_valid").getAsBoolean()) return rejected;
        }
        return false;
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class TestSound extends AbstractTickableSoundInstance implements FabricSoundInstance {
        final String name; final float volumeValue; final CompletableFuture<AudioStream> stream;
        TestSound(String name, float volume, CompletableFuture<AudioStream> stream) {
            super(DroneSoundEvents.MOTOR_LOOP, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.name = name; this.volumeValue = volume; this.stream = stream;
            this.volume = volume; this.pitch = 1; this.looping = true; this.attenuation = Attenuation.LINEAR;
        }
        @Override public void tick() { }
        @Override public boolean canStartSilent() { return true; }
        @Override public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary buffers, Identifier id, boolean repeat) { return stream; }
    }

    private static final class ToneStream implements AudioStream {
        private long sample;
        @Override public AudioFormat getFormat() { return new AudioFormat(48000, 16, 1, true, false); }
        @Override public ByteBuffer read(int requested) {
            ByteBuffer pcm = BufferUtils.createByteBuffer(requested & ~1).order(ByteOrder.LITTLE_ENDIAN);
            while (pcm.remaining() >= 2) pcm.putShort((short) Math.round(8000 * Math.sin(2 * Math.PI * 1000 * sample++ / 48000)));
            return pcm.flip();
        }
        @Override public void close() { }
    }
}
