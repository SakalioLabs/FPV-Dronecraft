package com.tenicana.dronecraft.client.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Bounded, non-mutating taps for the production Java wet path. */
public final class WetMainOutputCapture {
    public static final String PROPERTY = "fpvdrone.acoustics.wetOutputProtocol";
    private static final int MAX_STREAM_BYTES = 4_000_000;
    private static final long MAX_RAW_BYTES = 512L * 1024 * 1024;
    private static final ThreadLocal<ListenerReverbAudioStream> ACTIVE_STREAM = new ThreadLocal<>();
    private static final ThreadLocal<Batch> ACTIVE_BATCH = new ThreadLocal<>();
    private static final IdentityHashMap<Object, ByteArrayOutputStream> mixed = new IdentityHashMap<>();
    private static final List<JsonObject> publicationLog = new ArrayList<>();
    private static final ByteArrayOutputStream raw = new ByteArrayOutputStream();

    private WetMainOutputCapture() { }
    public static boolean requested() { return System.getProperty(PROPERTY) != null; }
    public static void bind(Object object) { if (requested()) ACTIVE_STREAM.set((ListenerReverbAudioStream) object); }
    public static void clearRead() { ACTIVE_BATCH.remove(); ACTIVE_STREAM.remove(); }
    static boolean active() { return requested() && ACTIVE_STREAM.get() != null; }

    public static void beginBatch(List<?> sources, int frames) {
        if (!active()) return;
        Batch batch = new Batch(ACTIVE_STREAM.get(), frames);
        for (Object object : sources) {
            ListenerReverbState.Source source = (ListenerReverbState.Source) object;
            JsonObject entry = new JsonObject();
            entry.addProperty("entity_id", source.entityId());
            entry.addProperty("motor_amplitude", source.motorAmplitude());
            entry.addProperty("propeller_amplitude", source.propellerAmplitude());
            entry.add("layers", new JsonArray());
            batch.sources.add(entry);
        }
        ACTIVE_BATCH.set(batch);
    }

    public static void layer(float[] output, float[] source, double amplitude, int frames) {
        Batch batch = ACTIVE_BATCH.get();
        if (batch == null) return;
        int layerIndex = batch.layerIndex++;
        int sourceIndex = layerIndex / 2;
        if (sourceIndex >= batch.sources.size() || batch.rawLength != frames)
            throw new IllegalStateException("wet layer sequence/length differs from source batch");
        JsonObject layer = new JsonObject();
        layer.addProperty("layer", (layerIndex & 1) == 0 ? "MOTOR" : "PROPELLER");
        layer.addProperty("amplitude", amplitude);
        layer.add("raw", storeFloats(source, frames));
        layer.add("sum_after", storeFloats(output, frames));
        batch.sources.get(sourceIndex).getAsJsonArray("layers").add(layer);
    }

    public static void finishBatch(float[] output, int frames) {
        Batch batch = ACTIVE_BATCH.get();
        if (batch == null) return;
        if (batch.layerIndex != batch.sources.size() * 2 || frames != batch.rawLength)
            throw new IllegalStateException("wet mixer did not render all captured layers");
        batch.json.addProperty("frame_count", frames);
        JsonArray sourceArray = new JsonArray(); batch.sources.forEach(source -> sourceArray.add(source));
        batch.json.add("sources", sourceArray);
        batch.json.add("post_limiter", storeFloats(output, frames));
        batch.completed = batch.json;
    }

    public static void mixedRead(Object object, float[] mixedInput, int frames) {
        if (!requested()) return;
        ListenerReverbAudioStream stream = (ListenerReverbAudioStream) object;
        Batch batch = ACTIVE_BATCH.get();
        if (batch == null || batch.stream != stream || batch.completed == null)
            throw new IllegalStateException("wet stream read returned without a captured mixer batch");
        {
            if (frames != batch.rawLength || mixedInput.length < frames) throw new IllegalStateException("wet mixed-input frame mismatch");
            synchronized (mixed) {
                ByteArrayOutputStream streamBytes = mixed.computeIfAbsent(batch.stream, ignored -> new ByteArrayOutputStream());
                batch.completed.addProperty("mixed_offset_bytes", streamBytes.size());
                byte[] pcm = floatBytes(mixedInput, frames);
                if (streamBytes.size() + pcm.length > MAX_STREAM_BYTES) throw new IllegalStateException("wet mixed-input recorder exhausted");
                streamBytes.writeBytes(pcm);
            }
            batch.completed.addProperty("input_record_identity", MainContextOutputCapture.identity(stream));
            synchronized (streamBatches) { streamBatches.computeIfAbsent(stream, ignored -> new JsonArray()).add(batch.completed); }
        }
    }

    private static final IdentityHashMap<Object, JsonArray> streamBatches = new IdentityHashMap<>();
    public static synchronized byte[] mixed(Object stream) {
        synchronized (mixed) {
            ByteArrayOutputStream bytes = mixed.get(stream);
            if (bytes == null) throw new IllegalStateException("wet mixed input was not captured");
            return bytes.toByteArray();
        }
    }
    public static synchronized JsonArray batches(Object stream) {
        synchronized (streamBatches) {
            JsonArray value = streamBatches.get(stream);
            if (value == null) throw new IllegalStateException("wet mixer batches were not captured");
            return value.deepCopy();
        }
    }
    public static synchronized byte[] rawBytes() { synchronized (raw) { return raw.toByteArray(); } }
    public static boolean closed(Object stream) { return (Boolean) field(stream, "closed"); }
    public static synchronized JsonArray publications() {
        JsonArray result = new JsonArray(); publicationLog.forEach(value -> result.add(value.deepCopy())); return result;
    }
    public static synchronized boolean hasQualifiedPublication() {
        return publicationLog.stream().anyMatch(p -> p.getAsJsonArray("sources").size() > 0
                && p.getAsJsonArray("sources").asList().stream().allMatch(s -> s.getAsJsonObject().get("distance_streams_verified").getAsBoolean()));
    }

    public static synchronized void publish(AcousticListenerFrame listener, Map<Integer, ?> activeSounds, Object controller) {
        if (!requested()) return;
        ListenerReverbState.Snapshot snapshot = ((ListenerReverbState) field(controller, "state")).snapshot();
        if (publicationLog.size() >= 4096) throw new IllegalStateException("wet publication recorder exhausted");
        JsonObject record = new JsonObject(); record.addProperty("publication_index", publicationLog.size());
        record.addProperty("utc", Instant.now().toString()); record.addProperty("thread", Thread.currentThread().getName());
        JsonArray sources = new JsonArray();
        for (ListenerReverbState.Source source : snapshot.sources()) {
            Object selected = null;
            for (Object set : activeSounds.values()) {
                if (((com.tenicana.dronecraft.entity.DroneEntity) field(set, "drone")).getId() == source.entityId()) { selected = set; break; }
            }
            if (selected == null) throw new IllegalStateException("published wet source has no active DroneSoundSet: " + source.entityId());
            DroneAcousticRenderState state = (DroneAcousticRenderState) field(selected, "acousticState");
            JsonObject row = new JsonObject();
            row.addProperty("entity_id", source.entityId()); row.addProperty("motor_amplitude", source.motorAmplitude());
            row.addProperty("propeller_amplitude", source.propellerAmplitude());
            DroneLoopSoundInstance motor = (DroneLoopSoundInstance) field(selected, "motor"), propeller = (DroneLoopSoundInstance) field(selected, "propeller");
            row.addProperty("motor_present", motor != null); row.addProperty("propeller_present", propeller != null);
            row.addProperty("motor_volume", motor == null ? 0 : motor.getVolume()); row.addProperty("propeller_volume", propeller == null ? 0 : propeller.getVolume());
            row.addProperty("motor_sound_identity", motor == null ? 0 : System.identityHashCode(motor));
            row.addProperty("propeller_sound_identity", propeller == null ? 0 : System.identityHashCode(propeller));
            row.addProperty("profile_motor_amplitude", state.motorPlaybackAmplitude());
            row.addProperty("profile_propeller_amplitude", state.propellerPlaybackAmplitude());
            row.addProperty("replaces_legacy_rpm_volume", state.replacesLegacyRpmVolume());
            row.addProperty("distance_streams_verified", state.distanceStreamsVerified());
            var drone = (com.tenicana.dronecraft.entity.DroneEntity) field(selected, "drone");
            AcousticVector pos = new AcousticVector(drone.getX(), drone.getY() + drone.getBbHeight() * .5, drone.getZ());
            row.addProperty("distance_m", pos.subtract(listener.positionMeters()).length()); sources.add(row);
        }
        record.addProperty("source_count", snapshot.sources().size()); record.add("sources", sources); publicationLog.add(record);
    }

    private static Object field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("actual diagnostic field unavailable: " + name, error);
        }
    }

    private static JsonObject storeFloats(float[] values, int length) {
        byte[] bytes = floatBytes(values, length); synchronized (raw) {
            if ((long) raw.size() + bytes.length > MAX_RAW_BYTES) throw new IllegalStateException("wet raw recorder exhausted");
            JsonObject blob = new JsonObject(); blob.addProperty("offset_bytes", raw.size()); blob.addProperty("bytes", bytes.length); blob.addProperty("sha256", sha(bytes)); raw.writeBytes(bytes); return blob;
        }
    }
    private static byte[] floatBytes(float[] values, int length) {
        ByteBuffer out = ByteBuffer.allocate(Math.multiplyExact(length, 4)).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < length; i++) out.putFloat(values[i]); return out.array();
    }
    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static final class Batch {
        final ListenerReverbAudioStream stream; final int rawLength; final List<JsonObject> sources = new ArrayList<>();
        final JsonObject json = new JsonObject(); int layerIndex; JsonObject completed;
        Batch(ListenerReverbAudioStream stream, int frames) { this.stream = stream; rawLength = frames; }
    }
}
