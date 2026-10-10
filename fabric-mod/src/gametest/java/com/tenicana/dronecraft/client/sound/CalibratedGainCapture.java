package com.tenicana.dronecraft.client.sound;

import com.google.gson.*;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.mixin.ChannelSourceAccessor;
import com.tenicana.dronecraft.client.mixin.SoundEngineMainOutputAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.lwjgl.openal.AL10;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.*;
import java.util.*;

/** Nonmutating observations; never holds a recorder monitor around a production call. */
public final class CalibratedGainCapture {
    public static final String PROPERTY = "fpvdrone.acoustics.calibratedGainProtocol";
    private static volatile String phase = "BOOT";
    private static long order;
    private static final JsonArray ticks = new JsonArray(), calculations = new JsonArray(), gains = new JsonArray(), starts = new JsonArray();
    private static final IdentityHashMap<Object, JsonArray> reads = new IdentityHashMap<>();
    private static final IdentityHashMap<Object, Integer> offsets = new IdentityHashMap<>();
    private static final ByteArrayOutputStream raw = new ByteArrayOutputStream();
    private static final ThreadLocal<DroneAcousticRenderState.AudioSnapshot> reading = new ThreadLocal<>();
    private CalibratedGainCapture() { }
    public static boolean requested() { return System.getProperty(PROPERTY) != null; }
    static void phase(String name) { phase = name; }
    public static Object field(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(target); }
            catch (NoSuchFieldException ignored) { }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        throw new IllegalStateException("missing field " + name);
    }
    private static JsonObject row() {
        JsonObject r = new JsonObject(); r.addProperty("order", ++order); r.addProperty("phase", phase);
        r.addProperty("thread", Thread.currentThread().getName()); return r;
    }
    public static synchronized void start(SoundInstance sound) {
        if (!requested() || !(sound instanceof DroneLoopSoundInstance || sound instanceof ListenerReverbSoundInstance)) return;
        JsonObject r = row(); r.addProperty("sound_identity", System.identityHashCode(sound));
        r.addProperty("sound_class", sound.getClass().getName()); r.addProperty("volume", (Float) field(sound, "volume"));
        r.addProperty("pre_resolution_volume_field", true); starts.add(r);
    }
    public static synchronized JsonObject beforeTick(Object object) {
        if (!requested()) return null;
        DroneLoopSoundInstance sound = (DroneLoopSoundInstance) object;
        JsonObject r = row(); r.addProperty("sound_identity", System.identityHashCode(sound)); r.addProperty("layer", sound.layer().name());
        r.addProperty("before", (Float) field(sound, "volume"));
        var drone = (com.tenicana.dronecraft.entity.DroneEntity) field(sound, "drone");
        r.addProperty("rpm", drone.getAverageMotorRpm());
        try { var m = DroneLoopSoundInstance.class.getDeclaredMethod("targetVolume"); m.setAccessible(true); r.addProperty("target", (Float) m.invoke(sound)); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        return r;
    }
    public static synchronized void afterTick(Object object, JsonObject r) {
        if (r == null) return;
        r.addProperty("after", (Float) field(object, "volume")); r.addProperty("end_order", ++order); ticks.add(r);
        if (ticks.size() > 20000) throw new IllegalStateException("bounded tick recorder exhausted");
    }
    public static synchronized void calculate(Object engine, SoundInstance sound, float value) {
        if (!requested()) return;
        JsonObject r = row(); r.addProperty("sound_identity", System.identityHashCode(sound)); r.addProperty("sound_class", sound.getClass().getName());
        r.addProperty("volume", sound.getVolume()); r.addProperty("result", value);
        r.addProperty("final_category_volume", Minecraft.getInstance().options.getFinalSoundSourceVolume(sound.getSource()));
        r.addProperty("gain_by_source", ((SoundEngineMainOutputAccessor) engine).mainOutput$getGains().getFloat(sound.getSource())); calculations.add(r);
        if (calculations.size() > 20000) throw new IllegalStateException("bounded calculate recorder exhausted");
    }
    public static JsonObject beforeGain(Object object, float value) {
        if (!requested()) return null;
        Channel ch = (Channel) object; int source = ((ChannelSourceAccessor) ch).fpvdrone$getSource();
        float before = AL10.alGetSourcef(source, AL10.AL_GAIN); int error = AL10.alGetError();
        synchronized (CalibratedGainCapture.class) {
            JsonObject r = row(); r.addProperty("allocation_identity", WetChannelLifecycleCapture.identity(ch));
            r.addProperty("source", source); r.addProperty("input", value); r.addProperty("before", before); r.addProperty("al_error_before", error); return r;
        }
    }
    public static void afterGain(Object object, JsonObject r) {
        if (r == null) return;
        float after = AL10.alGetSourcef(r.get("source").getAsInt(), AL10.AL_GAIN); int error = AL10.alGetError();
        synchronized (CalibratedGainCapture.class) { r.addProperty("after", after); r.addProperty("al_error_after", error); r.addProperty("end_order", ++order); gains.add(r); }
    }
    public static void beforeRead(Object object) {
        if (!requested()) return;
        reading.set(((DroneAcousticRenderState) field(object, "state")).audioSnapshot());
    }
    public static void afterRead(Object object, ByteBuffer returned) {
        if (!requested()) return;
        var snapshot = reading.get(); reading.remove();
        if (returned == null || !returned.hasRemaining()) return;
        var state = (DroneAcousticRenderState) field(object, "state");
        if (snapshot == null || state.audioSnapshot() != snapshot) throw new IllegalStateException("synthetic snapshot changed within actual read");
        var layer = (com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer.Layer) field(object, "layer");
        double c = layer == com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer.Layer.MOTOR
                ? snapshot.calibration().motorPlaybackAmplitude() : snapshot.calibration().propellerPlaybackAmplitude();
        int frames = returned.remaining() / 2;
        float[] values = (float[]) field(object, "sampleBuffer");
        ByteBuffer b = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) b.putFloat(values[i]);
        byte[] pcm = new byte[returned.remaining()]; returned.duplicate().get(pcm);
        synchronized (CalibratedGainCapture.class) {
            if (raw.size() + b.capacity() > 128 * 1024 * 1024) throw new IllegalStateException("bounded dry raw recorder exhausted");
            JsonObject r = row(); r.addProperty("stream_identity", System.identityHashCode(object)); r.addProperty("layer", layer.name());
            r.addProperty("snapshot_identity", System.identityHashCode(snapshot)); r.addProperty("calibration", c);
            r.addProperty("raw_offset_bytes", raw.size()); r.addProperty("raw_bytes", b.capacity()); r.addProperty("raw_sha256", MainContextOutputCapture.sha(b.array()));
            int offset = offsets.getOrDefault(object, 0); r.addProperty("pcm_offset_bytes", offset); r.addProperty("pcm_bytes", pcm.length); r.addProperty("pcm_sha256", MainContextOutputCapture.sha(pcm));
            offsets.put(object, offset + pcm.length); raw.writeBytes(b.array()); reads.computeIfAbsent(object, ignored -> new JsonArray()).add(r);
        }
    }
    static synchronized JsonArray reads(Object stream) { return reads.get(stream).deepCopy(); }
    static synchronized byte[] rawBytes() { return raw.toByteArray(); }
    static synchronized JsonObject snapshot() {
        JsonObject r = new JsonObject(); r.add("ticks", ticks.deepCopy()); r.add("calculations", calculations.deepCopy());
        r.add("channel_gains", gains.deepCopy()); r.add("play_boundaries", starts.deepCopy()); return r;
    }
}
