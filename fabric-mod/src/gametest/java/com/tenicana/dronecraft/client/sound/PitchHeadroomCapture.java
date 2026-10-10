package com.tenicana.dronecraft.client.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.mixin.ChannelSourceAccessor;
import org.lwjgl.openal.AL10;

/** D187 read-only setter observations; no monitor spans a production call. */
public final class PitchHeadroomCapture {
    private static final JsonArray setters = new JsonArray();
    private static final JsonArray ticks = new JsonArray();
    private static long order;
    private PitchHeadroomCapture() { }
    public static JsonObject before(Object object, float argument) {
        if (!AssetNativeCapture.requested()
                || !MainContextOutputCapture.protocol().has("manager_asset_pitch_headroom_experiment")) return null;
        Channel channel = (Channel) object;
        int source = ((ChannelSourceAccessor) channel).fpvdrone$getSource();
        JsonObject row = new JsonObject();
        row.addProperty("channel_identity", System.identityHashCode(channel));
        row.addProperty("allocation_identity", WetChannelLifecycleCapture.identity(channel));
        row.addProperty("source", source);
        row.addProperty("argument", argument);
        row.addProperty("before", AL10.alGetSourcef(source, AL10.AL_PITCH));
        row.addProperty("al_error_before", AL10.alGetError());
        row.addProperty("thread", Thread.currentThread().getName());
        synchronized (PitchHeadroomCapture.class) { row.addProperty("begin_order", ++order); }
        return row;
    }
    public static void after(JsonObject row) {
        if (row == null) return;
        row.addProperty("after", AL10.alGetSourcef(row.get("source").getAsInt(), AL10.AL_PITCH));
        row.addProperty("al_error_after", AL10.alGetError());
        synchronized (PitchHeadroomCapture.class) {
            row.addProperty("end_order", ++order);
            setters.add(row);
            if (setters.size() > 40000) throw new IllegalStateException("D187 pitch observer exhausted");
        }
    }
    static synchronized long cursor() { return order; }
    static synchronized JsonArray snapshot() { return setters.deepCopy(); }
    public static JsonObject beforeTick(Object object) {
        if (!AssetNativeCapture.requested()
                || !MainContextOutputCapture.protocol().has("manager_asset_pitch_headroom_experiment")) return null;
        JsonObject row = new JsonObject();
        row.addProperty("sound_identity", System.identityHashCode(object));
        row.addProperty("before", ((Number) field(object, "volume")).floatValue());
        row.addProperty("pitch_before", ((Number) field(object, "pitch")).floatValue());
        row.addProperty("target_return_count", 0);
        row.addProperty("pitch_target_return_count", 0);
        synchronized (PitchHeadroomCapture.class) { row.addProperty("begin_order", ++order); }
        return row;
    }
    public static void target(JsonObject row, float value, boolean pitch) {
        if (row == null) return;
        String count = pitch ? "pitch_target_return_count" : "target_return_count";
        row.addProperty(count, row.get(count).getAsInt() + 1);
        row.addProperty(pitch ? "pitch_target" : "target", value);
    }
    public static void afterTick(Object object, JsonObject row) {
        if (row == null) return;
        row.addProperty("after", ((Number) field(object, "volume")).floatValue());
        row.addProperty("pitch_after", ((Number) field(object, "pitch")).floatValue());
        row.addProperty("asset_operating_point_valid_after", (Boolean) field(object, "assetOperatingPointValid"));
        synchronized (PitchHeadroomCapture.class) {
            row.addProperty("end_order", ++order);
            ticks.add(row);
            if (ticks.size() > 20000) throw new IllegalStateException("D187 tick observer exhausted");
        }
    }
    static synchronized JsonArray tickSnapshot() { return ticks.deepCopy(); }
    private static Object field(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { }
            catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
        throw new IllegalStateException("missing D187 observer field " + name);
    }
}
