package com.tenicana.dronecraft.client.sound;

import com.google.gson.*;
import com.tenicana.dronecraft.client.mixin.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryUtil;
import java.io.ByteArrayOutputStream;
import java.nio.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Main-Library loopback experiment. The callback source is a synthetic native fixture. */
final class CallbackBufferNativeClientGameTest {
    private static final int WARM = 24_000, POST = 48_000, STEP = 960;
    private static final ByteArrayOutputStream output = new ByteArrayOutputStream(), input = new ByteArrayOutputStream();
    static boolean requested() {
        return MainContextOutputCapture.requested() && MainContextOutputCapture.protocol().has("callback_buffer_experiment");
    }
    static void run(ClientGameTestContext context) {
        JsonObject protocol = MainContextOutputCapture.protocol(), report = new JsonObject(); JsonArray rows = new JsonArray();
        Path root = Path.of(protocol.get("workspace_root").getAsString()); Throwable failure = null;
        report.addProperty("started_utc", Instant.now().toString());
        report.addProperty("protocol_sha256", MainContextOutputCapture.sha(read(Path.of(System.getProperty(MainContextOutputCapture.PROPERTY)))));
        report.add("evidence_sha256", protocol.get("evidence_sha256").deepCopy());
        try {
            JsonObject loaded = new JsonObject();
            for (var item : protocol.getAsJsonArray("required_loaded_class_paths")) {
                String path = item.getAsString(), resource = "/" + path.substring(path.indexOf("com/tenicana/"));
                try (var stream = CallbackBufferNativeClientGameTest.class.getResourceAsStream(resource)) {
                    if (stream == null) throw new IllegalStateException("missing loaded " + resource);
                    loaded.addProperty(path, MainContextOutputCapture.sha(stream.readAllBytes()));
                }
            }
            report.add("loaded_class_sha256", loaded);
            try (TestSingleplayerContext world = context.worldBuilder().create()) {
                world.getClientWorld().waitForChunksDownload(); world.getServer().runCommand("gamerule spawn_mobs false");
                for (int epoch = 0; epoch < 2; epoch++) {
                    final int generation = epoch;
                    context.runOnClient(client -> engine(client).stopAll());
                    sound(context, () -> { identity(report, generation); return null; });
                    for (var item : protocol.getAsJsonArray("expected_cases")) {
                        JsonObject declared = item.getAsJsonObject();
                        rows.add(sound(context, () -> capture(declared, generation)));
                    }
                    if (epoch == 0) context.runOnClient(client -> client.getSoundManager().reload());
                }
            }
            context.runOnClient(client -> client.getSoundManager().destroy());
            report.addProperty("status", "callback_buffer_native_captured");
        } catch (Throwable error) {
            failure = error; report.addProperty("status", "callback_buffer_native_failed"); report.addProperty("failure", error.toString());
            try { context.runOnClient(client -> client.getSoundManager().destroy()); }
            catch (Throwable cleanup) { report.addProperty("cleanup_failure", cleanup.toString()); }
        }
        report.add("rows", rows); report.add("epochs", MainContextOutputCapture.epochs());
        JsonObject scope = new JsonObject();
        for (String key : new String[]{"actual_Minecraft_client_started", "actual_main_Library_loopback_context", "synthetic_native_static_source", "integer_ALsizei_CIF", "application_callback_preallocated_storage"}) scope.addProperty(key, failure == null);
        for (String key : new String[]{"production_AudioStream_callback", "production_Channel_callback", "production_DSP_verified", "physical_endpoint_verified", "normal_endpoint_scheduler_qualified", "JVM_GC_realtime_qualified", "full_six_drone_capacity", "physical_error_recomputed", "real_pressure_or_rpm_accessed", "release_profile_eligible", "full_goal_complete"}) scope.addProperty(key, false);
        report.add("scope", scope); writePcm(root, protocol, report, "input", input.toByteArray()); writePcm(root, protocol, report, "output", output.toByteArray());
        report.addProperty("finished_utc", Instant.now().toString());
        try { Files.writeString(root.resolve(protocol.get("output_report").getAsString()), new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report) + "\n", StandardOpenOption.CREATE_NEW); }
        catch (Exception error) { throw new IllegalStateException(error); }
        if (failure != null) throw new IllegalStateException("callback buffer experiment failed", failure);
    }
    private static JsonObject capture(JsonObject declared, int generation) {
        JsonObject row = declared.deepCopy(); row.addProperty("generation", generation);
        var library = library(); long device = library.mainOutput$getDevice(), context = library.mainOutput$getContext();
        MainContextOutputCapture.requireCurrent(device, context);
        if (!AL.getCapabilities().AL_SOFT_callback_buffer || !AL10.alIsExtensionPresent("AL_SOFT_callback_buffer"))
            throw new IllegalStateException("active main callback-buffer extension unavailable");
        row.addProperty("AL_SOFT_callback_buffer", true); row.addProperty("callback_function_pointer", AL.getCapabilities().alBufferCallbackSOFT);
        row.addProperty("device", device); row.addProperty("context", context); row.addProperty("owner_thread_id", Thread.currentThread().threadId());
        // Drain the previous source's native gain ramp before this independent fixture.
        FloatBuffer drain = BufferUtils.createFloatBuffer(4096); SOFTLoopback.alcRenderSamplesSOFT(device, drain, 4096); check(device);
        int source = AL10.alGenSources(), secondary = AL10.alGenSources(), buffer = AL10.alGenBuffers();
        ByteBuffer token = MemoryUtil.memAlloc(8); IntegerPcmCallback callback = new IntegerPcmCallback(MemoryUtil.memAddress(token), declared.get("finite_frames").getAsLong());
        row.addProperty("source", source); row.addProperty("secondary_source", secondary); row.addProperty("buffer", buffer);
        boolean workerJoined = true;
        try {
            SOFTCallbackBuffer.nalBufferCallbackSOFT(buffer, AL10.AL_FORMAT_MONO16, 48_000, callback.address(), callback.token); check(device);
            row.addProperty("registered_callback_pointer", SOFTCallbackBuffer.alGetBufferPtrSOFT(buffer, SOFTCallbackBuffer.AL_BUFFER_CALLBACK_FUNCTION_SOFT));
            row.addProperty("registered_user_pointer", SOFTCallbackBuffer.alGetBufferPtrSOFT(buffer, SOFTCallbackBuffer.AL_BUFFER_CALLBACK_USER_PARAM_SOFT));
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer); AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, -1); AL10.alSourcef(source, AL10.AL_GAIN, .5f);
            AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 1); AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1);
            AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, 1_000_000); check(device);
            row.addProperty("source_type", AL10.alGetSourcei(source, AL10.AL_SOURCE_TYPE));
            row.addProperty("sample_rate", AL10.alGetBufferi(buffer, AL10.AL_FREQUENCY)); row.addProperty("bits", AL10.alGetBufferi(buffer, AL10.AL_BITS));
            row.addProperty("channels", AL10.alGetBufferi(buffer, AL10.AL_CHANNELS));
            AL10.alSourceQueueBuffers(secondary, buffer); row.addProperty("queue_rejection_error", AL10.alGetError());
            AL10.alSourcei(secondary, AL10.AL_BUFFER, buffer); row.addProperty("second_source_rejection_error", AL10.alGetError());
            row.addProperty("secondary_buffer_after_rejection", AL10.alGetSourcei(secondary, AL10.AL_BUFFER)); check(device);
            AL10.alSourcePlay(source); check(device);
            int warmFrames = declared.get("eof").getAsBoolean() ? 0 : WARM;
            appendRender(row, "warm", render(device, warmFrames));
            row.addProperty("calls_at_change", callback.calls); row.addProperty("input_frames_at_change", callback.cursor);
            row.addProperty("source_offset_at_change", AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET));
            row.addProperty("output_frames_at_change", warmFrames);
            callback.control = new IntegerPcmCallback.Control(1, declared.get("new_amplitude").getAsDouble());
            if (declared.get("worker").getAsBoolean()) {
                var alc = ALC.getCapabilities(); FloatBuffer samples = BufferUtils.createFloatBuffer(STEP);
                ByteBuffer rendered = ByteBuffer.allocate(POST * 4).order(ByteOrder.LITTLE_ENDIAN); Throwable[] error = new Throwable[1];
                long[] workerId = new long[1]; long start = System.nanoTime();
                Thread worker = new Thread(() -> {
                    workerId[0] = Thread.currentThread().threadId();
                    try {
                        ALC.setCapabilities(alc);
                        for (int frame = 0; frame < POST; frame += STEP) {
                            samples.clear(); SOFTLoopback.alcRenderSamplesSOFT(device, samples, STEP);
                            while (samples.hasRemaining()) rendered.putFloat(samples.get());
                            // Advance a finite 200ms sample clock while the owner executor is blocked.
                            if (frame < 9600) Thread.sleep(20);
                        }
                    } catch (Throwable t) { error[0] = t; }
                    finally {
                        try { ALC.setCapabilities(null); } catch (Throwable t) { if (error[0] == null) error[0] = t; }
                    }
                }, "D168-sole-loopback-renderer");
                worker.start(); workerJoined = false;
                boolean interrupted = false;
                for (;;) {
                    try { worker.join(); workerJoined = true; break; }
                    catch (InterruptedException e) { interrupted = true; }
                }
                if (interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("owner interrupted after renderer quiesced"); }
                if (error[0] != null) throw new IllegalStateException("render worker", error[0]);
                row.addProperty("worker_thread_id", workerId[0]); row.addProperty("owner_blocked_wall_ns", System.nanoTime() - start);
                row.addProperty("owner_stream_refill_calls_during_worker", 0); row.addProperty("owner_AL_calls_during_worker", 0);
                row.addProperty("worker_first_no_refill_frames", 9600); row.addProperty("worker_joined", true);
                appendRender(row, "post", rendered.array()); check(device);
            } else appendRender(row, "post", render(device, POST));
            row.addProperty("state_after_post", AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE));
            row.addProperty("source_offset_after_post", AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET)); check(device);
            row.add("callback", callback.snapshot());
            byte[] pcm = new byte[(int) callback.cursor * 2]; ByteBuffer wrapped = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < callback.cursor; i++) wrapped.putShort(callback.pcm[i]);
            row.addProperty("input_byte_offset", input.size()); row.addProperty("input_bytes", pcm.length); row.addProperty("input_sha256", MainContextOutputCapture.sha(pcm)); input.writeBytes(pcm);
        } finally {
            // The sole renderer must finish before any callback/token can be released.
            if (!workerJoined) throw new IllegalStateException("cannot free callback while renderer is live");
            int calls = callback.calls; AL10.alSourceStop(source); AL10.alSourcei(source, AL10.AL_BUFFER, 0); check(device);
            ByteBuffer normal = BufferUtils.createByteBuffer(2); AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, normal, 48_000); check(device);
            JsonObject cleanup = new JsonObject();
            cleanup.addProperty("callback_pointer_after_alBufferData", SOFTCallbackBuffer.alGetBufferPtrSOFT(buffer, SOFTCallbackBuffer.AL_BUFFER_CALLBACK_FUNCTION_SOFT));
            cleanup.addProperty("user_pointer_after_alBufferData", SOFTCallbackBuffer.alGetBufferPtrSOFT(buffer, SOFTCallbackBuffer.AL_BUFFER_CALLBACK_USER_PARAM_SOFT));
            AL10.alDeleteBuffers(buffer); AL10.alDeleteSources(source); AL10.alDeleteSources(secondary); check(device);
            cleanup.addProperty("buffer_deleted", !AL10.alIsBuffer(buffer)); cleanup.addProperty("source_deleted", !AL10.alIsSource(source));
            cleanup.addProperty("secondary_source_deleted", !AL10.alIsSource(secondary)); cleanup.addProperty("renderer_quiescent_before_free", workerJoined);
            render(device, 4096); cleanup.addProperty("calls_after_detach_render", callback.calls); cleanup.addProperty("calls_before_detach", calls);
            callback.free(); MemoryUtil.memFree(token); cleanup.addProperty("callback_freed", true); cleanup.addProperty("token_freed", true);
            cleanup.addProperty("al_error", AL10.alGetError()); cleanup.addProperty("alc_error", ALC10.alcGetError(device)); row.add("cleanup", cleanup);
        }
        return row;
    }
    private static byte[] render(long device, int frames) {
        ByteBuffer bytes = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN); FloatBuffer samples = BufferUtils.createFloatBuffer(STEP);
        for (int frame = 0; frame < frames; frame += STEP) {
            int count = Math.min(STEP, frames - frame); samples.clear().limit(count); SOFTLoopback.alcRenderSamplesSOFT(device, samples, count);
            check(device); while (samples.hasRemaining()) bytes.putFloat(samples.get());
        }
        return bytes.array();
    }
    private static void appendRender(JsonObject row, String stage, byte[] data) {
        JsonObject slice = new JsonObject(); slice.addProperty("byte_offset", output.size()); slice.addProperty("bytes", data.length);
        slice.addProperty("sha256", MainContextOutputCapture.sha(data)); output.writeBytes(data); row.add(stage, slice);
    }
    private static void identity(JsonObject report, int generation) {
        var library = library(); long device = library.mainOutput$getDevice(), context = library.mainOutput$getContext(); MainContextOutputCapture.requireCurrent(device, context);
        JsonObject identity = new JsonObject(); identity.addProperty("device", device); identity.addProperty("context", context);
        identity.addProperty("vendor", AL10.alGetString(AL10.AL_VENDOR)); identity.addProperty("renderer", AL10.alGetString(AL10.AL_RENDERER));
        identity.addProperty("version", AL10.alGetString(AL10.AL_VERSION)); identity.addProperty("AL_SOFT_callback_buffer", AL.getCapabilities().AL_SOFT_callback_buffer);
        identity.addProperty("distance_model", AL10.alGetInteger(AL10.AL_DISTANCE_MODEL)); report.add("identity_" + generation, identity); check(device);
    }
    private static void check(long device) { int al = AL10.alGetError(), alc = ALC10.alcGetError(device); if (al != 0 || alc != 0) throw new IllegalStateException("native errors " + al + "/" + alc); }
    private static SoundEngine engine(Minecraft client) { return ((SoundManagerEngineAccessor) client.getSoundManager()).fpvdrone$getSoundEngine(); }
    private static MainOutputLibraryAccessor library() { return (MainOutputLibraryAccessor) ((SoundEngineMainOutputAccessor) engine(Minecraft.getInstance())).mainOutput$getLibrary(); }
    private static <T> T sound(ClientGameTestContext context, Supplier<T> action) {
        CompletableFuture<T> done = new CompletableFuture<>();
        context.runOnClient(client -> ((SoundEngineExecutorAccessor) engine(client)).fpvdrone$getExecutor().schedule(() -> {
            try { done.complete(action.get()); } catch (Throwable error) { done.completeExceptionally(error); }
        }));
        context.waitFor(client -> done.isDone(), 600); return done.join();
    }
    private static byte[] read(Path path) { try { return Files.readAllBytes(path); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static void writePcm(Path root, JsonObject protocol, JsonObject report, String name, byte[] pcm) {
        String path = protocol.get(name + "_pcm_file").getAsString();
        try { Files.write(root.resolve(path), pcm, StandardOpenOption.CREATE_NEW); } catch (Exception e) { throw new IllegalStateException(e); }
        report.addProperty(name + "_pcm_file", path); report.addProperty(name + "_pcm_bytes", pcm.length); report.addProperty(name + "_pcm_sha256", MainContextOutputCapture.sha(pcm));
    }
}
