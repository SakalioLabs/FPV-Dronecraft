package com.tenicana.dronecraft.client.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

/** Test-only metadata from the actual Channel hooks; it does not capture audio. */
public final class DistanceOwnershipRuntimeTrace {
    private static final boolean ENABLED = System.getProperty(
            "fpvdrone.acoustics.distanceIntegrationProtocol") != null;
    private static final JsonArray EVENTS = new JsonArray();
    private static volatile java.util.function.BiConsumer<AudioStream, Integer> afterAttach;

    private DistanceOwnershipRuntimeTrace() { }

    public static void attached(AudioStream stream, int source, ProceduralDistanceOwner.Result result) {
        if (!ENABLED) return;
        JsonObject row = event("attach_head", source);
        row.addProperty("stream_class", stream.getClass().getName());
        row.addProperty("stream_identity", System.identityHashCode(stream));
        row.addProperty("owner_status", result.status().name());
        row.addProperty("owner_error", result.alError());
        row.addProperty("rollback_error", result.rollbackError());
        append(row);
        var action = afterAttach;
        if (action != null) action.accept(stream, source);
    }

    public static void setAfterAttach(java.util.function.BiConsumer<AudioStream, Integer> action) {
        if (!ENABLED) throw new IllegalStateException("distance integration trace is not enabled");
        afterAttach = action;
    }

    public static void playback(int source, boolean owned, boolean rejected, boolean after) {
        if (!ENABLED) return;
        JsonObject row = event(after ? "play_tail" : "play_head", source);
        row.addProperty("owned", owned);
        row.addProperty("rejected", rejected);
        append(row);
    }

    public static void destroyed(int source) {
        if (!ENABLED) return;
        JsonObject row = new JsonObject();
        row.addProperty("stage", "destroy_tail");
        row.addProperty("context", ALC10.alcGetCurrentContext());
        row.addProperty("source", source);
        row.addProperty("source_still_valid", AL10.alIsSource(source));
        row.addProperty("al_error", AL10.alGetError());
        append(row);
    }

    public static JsonObject event(String stage, int source) {
        JsonObject row = new JsonObject();
        row.addProperty("stage", stage);
        row.addProperty("context", ALC10.alcGetCurrentContext());
        row.addProperty("source", source);
        row.addProperty("source_model", AL10.alGetSourcei(source, AL10.AL_DISTANCE_MODEL));
        row.addProperty("reference", AL10.alGetSourcef(source, AL10.AL_REFERENCE_DISTANCE));
        row.addProperty("maximum", AL10.alGetSourcef(source, AL10.AL_MAX_DISTANCE));
        row.addProperty("rolloff", AL10.alGetSourcef(source, AL10.AL_ROLLOFF_FACTOR));
        row.addProperty("gain", AL10.alGetSourcef(source, AL10.AL_GAIN));
        row.addProperty("global_model", AL10.alGetInteger(AL10.AL_DISTANCE_MODEL));
        row.addProperty("source_state", AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE));
        row.addProperty("buffers_queued", AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED));
        row.addProperty("al_error", AL10.alGetError());
        return row;
    }

    private static synchronized void append(JsonObject row) {
        if (EVENTS.size() >= 4096) throw new IllegalStateException("distance trace exceeded frozen bound");
        row.addProperty("event_index", EVENTS.size());
        row.addProperty("thread", Thread.currentThread().getName());
        EVENTS.add(row);
    }

    public static synchronized JsonArray snapshot() { return EVENTS.deepCopy(); }
}
