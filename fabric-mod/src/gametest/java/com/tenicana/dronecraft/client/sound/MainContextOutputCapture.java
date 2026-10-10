package com.tenicana.dronecraft.client.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Gametest-only backend and read observer; never creates a second audio context. */
public final class MainContextOutputCapture {
    public static final String PROPERTY = "fpvdrone.acoustics.mainOutputProtocol";
    private static JsonObject protocol;
    private static final List<JsonObject> epochs = new ArrayList<>();
    private static final IdentityHashMap<Object, ByteArrayOutputStream> inputs = new IdentityHashMap<>();
    private static final IdentityHashMap<Object, Integer> identities = new IdentityHashMap<>();
    private static int nextIdentity = 1;

    private MainContextOutputCapture() { }

    public static boolean requested() { return System.getProperty(PROPERTY) != null; }

    public static synchronized JsonObject protocol() {
        if (protocol != null) return protocol;
        try {
            Path path = Path.of(System.getProperty(PROPERTY));
            JsonObject value = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            Path root = Path.of(value.get("workspace_root").getAsString());
            for (var entry : value.getAsJsonObject("evidence_sha256").entrySet())
                if (!sha(Files.readAllBytes(root.resolve(entry.getKey()))).equals(entry.getValue().getAsString()))
                    throw new IllegalStateException("main-output evidence changed: " + entry.getKey());
            for (String name : List.of("output_report", "output_pcm_file", "input_pcm_file"))
                if (Files.exists(root.resolve(value.get(name).getAsString())))
                    throw new IllegalStateException("refusing existing main-output artifact: " + name);
            if (!"null".equals(System.getenv("ALSOFT_DRIVERS")))
                throw new IllegalStateException("main-output test requires null driver launch");
            protocol = value;
            return protocol;
        } catch (Exception error) { throw new IllegalStateException("main-output preflight failed", error); }
    }

    public static synchronized long openDevice() {
        protocol();
        if (!ALC.createCapabilities(0).ALC_SOFT_loopback)
            throw new IllegalStateException("main-device loopback extension missing");
        long device = SOFTLoopback.alcLoopbackOpenDeviceSOFT((CharSequence) null);
        if (device == 0) throw new IllegalStateException("main loopback open failed; no playback fallback allowed");
        JsonObject epoch = new JsonObject();
        epoch.addProperty("generation", epochs.size()); epoch.addProperty("device", device);
        epoch.addProperty("opened_utc", Instant.now().toString()); epoch.addProperty("context", 0);
        epoch.addProperty("context_destroyed", false); epoch.addProperty("close_result", false);
        epochs.add(epoch);
        if (!SOFTLoopback.alcIsRenderFormatSupportedSOFT(device, 48000, SOFTLoopback.ALC_MONO_SOFT, SOFTLoopback.ALC_FLOAT_SOFT)) {
            epoch.addProperty("close_result", ALC10.alcCloseDevice(device));
            epoch.addProperty("failure", "unsupported main output format");
            throw new IllegalStateException("main-device mono float format unavailable");
        }
        return device;
    }

    public static synchronized IntBuffer attributes(long device, IntBuffer original) {
        find(device);
        var values = new LinkedHashMap<Integer, Integer>();
        IntBuffer copy = original.duplicate();
        while (copy.hasRemaining()) {
            int key = copy.get(); if (key == 0) break;
            if (!copy.hasRemaining()) throw new IllegalStateException("unterminated vanilla attributes");
            values.put(key, copy.get());
        }
        JsonArray vanilla = new JsonArray();
        values.forEach((key, value) -> { vanilla.add(key); vanilla.add(value); }); vanilla.add(0);
        find(device).add("vanilla_attrs", vanilla);
        var contract = new LinkedHashMap<Integer, Integer>();
        // Canonical ordering is fixed before capture; other vanilla attrs remain intact.
        contract.put(SOFTHRTF.ALC_HRTF_SOFT, ALC10.ALC_FALSE);
        contract.put(SOFTHRTF.ALC_HRTF_ID_SOFT, 0);
        contract.put(SOFTOutputLimiter.ALC_OUTPUT_LIMITER_SOFT, ALC10.ALC_FALSE);
        contract.put(ALC10.ALC_FREQUENCY, 48000);
        contract.put(SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT, SOFTLoopback.ALC_MONO_SOFT);
        contract.put(SOFTLoopback.ALC_FORMAT_TYPE_SOFT, SOFTLoopback.ALC_FLOAT_SOFT);
        values.forEach(contract::putIfAbsent); values = contract;
        IntBuffer result = BufferUtils.createIntBuffer(values.size() * 2 + 1);
        JsonArray stored = new JsonArray();
        for (var entry : values.entrySet()) {
            result.put(entry.getKey()).put(entry.getValue());
            stored.add(entry.getKey()); stored.add(entry.getValue());
        }
        result.put(0).flip(); stored.add(0); find(device).add("attrs", stored);
        return result;
    }

    public static synchronized void created(long device, long context) {
        JsonObject epoch = find(device);
        epoch.addProperty("context", context); epoch.addProperty("created_utc", Instant.now().toString());
        epoch.addProperty("creation_alc_error", ALC10.alcGetError(device));
        if (context == 0 || epoch.get("creation_alc_error").getAsInt() != 0) {
            if (context != 0) { ALC10.alcDestroyContext(context); epoch.addProperty("context_destroyed", true); }
            epoch.addProperty("close_result", ALC10.alcCloseDevice(device));
            throw new IllegalStateException("actual Library context creation failed");
        }
    }

    public static synchronized void destroyed(long context) {
        for (JsonObject epoch : epochs) if (epoch.get("context").getAsLong() == context
                && !epoch.get("context_destroyed").getAsBoolean()) {
            epoch.addProperty("context_destroyed", true);
            epoch.addProperty("destroyed_utc", Instant.now().toString());
            epoch.addProperty("destroy_alc_error", ALC10.alcGetError(epoch.get("device").getAsLong()));
            return;
        }
        throw new IllegalStateException("unknown destroyed main context");
    }

    public static synchronized void closed(long device, boolean result) {
        JsonObject epoch = find(device); epoch.addProperty("close_result", result);
        epoch.addProperty("closed_utc", Instant.now().toString());
        if (!result) throw new IllegalStateException("actual Library device close failed");
    }

    public static synchronized void requireCurrent(long device, long context) {
        JsonObject epoch = find(device);
        if (epoch.get("context").getAsLong() != context || epoch.get("context_destroyed").getAsBoolean()
                || epoch.get("close_result").getAsBoolean() || ALC10.alcGetCurrentContext() != context
                || ALC10.alcGetContextsDevice(context) != device)
            throw new IllegalStateException("capture is not the live Library main context/device");
    }

    private static JsonObject find(long device) {
        for (int i = epochs.size() - 1; i >= 0; i--)
            if (epochs.get(i).get("device").getAsLong() == device) return epochs.get(i);
        throw new IllegalStateException("unregistered main loopback device");
    }

    public static synchronized JsonArray epochs() {
        JsonArray result = new JsonArray(); epochs.forEach(epoch -> result.add(epoch.deepCopy())); return result;
    }

    static synchronized int generation(long device, long context) {
        JsonObject epoch = find(device);
        requireCurrent(device, context); return epoch.get("generation").getAsInt();
    }

    public static synchronized void record(Object stream, ByteBuffer returned) {
        if (!requested() || returned == null || !returned.hasRemaining()) return;
        ByteArrayOutputStream data = inputs.computeIfAbsent(stream, key -> new ByteArrayOutputStream());
        identities.computeIfAbsent(stream, key -> nextIdentity++);
        ByteBuffer copy = returned.duplicate();
        byte[] chunk = new byte[copy.remaining()]; copy.get(chunk);
        if (data.size() + chunk.length > 2_000_000) throw new IllegalStateException("bounded input recorder exhausted");
        data.writeBytes(chunk);
    }

    /** Logical stream admission only; callback streams have no queued PCM read. */
    public static synchronized void registerCallbackStream(Object stream) {
        if (!requested()) return;
        Objects.requireNonNull(stream, "stream");
        if (identities.containsKey(stream) || inputs.containsKey(stream))
            throw new IllegalStateException("callback stream was recorded before attach admission");
        identities.put(stream, nextIdentity++);
        inputs.put(stream, new ByteArrayOutputStream());
    }

    static synchronized byte[] input(Object stream) {
        ByteArrayOutputStream data = inputs.get(stream);
        if (data == null) throw new IllegalStateException("actual stream read not recorded");
        return data.toByteArray();
    }

    static synchronized int identity(Object stream) {
        Integer identity = identities.get(stream);
        if (identity == null) throw new IllegalStateException("unknown actual read stream");
        return identity;
    }

    static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
}
