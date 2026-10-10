package com.tenicana.dronecraft.client.sound;

import com.google.gson.*;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.mixin.ChannelSourceAccessor;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.openal.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Gametest-only read/queue/offset observations. Never changes PCM, gain or queue state. */
public final class CalibrationTransitionQueueCapture {
    public static final String PROPERTY = "fpvdrone.acoustics.calibrationTransitionProtocol";
    private static final IdentityHashMap<Object, ArrayDeque<JsonObject>> pending = new IdentityHashMap<>();
    private static final IdentityHashMap<Object, Integer> offsets = new IdentityHashMap<>();
    private static final JsonArray reads = new JsonArray(), queues = new JsonArray(), timeline = new JsonArray();
    private static long clock, order;
    private static String phase = "BOOT";
    private CalibrationTransitionQueueCapture() { }
    public static boolean requested() { return System.getProperty(PROPERTY) != null; }
    static synchronized void phase(String value) { phase = value; }
    static synchronized long clock() { return clock; }
    static synchronized void advanced(int count) { clock += count; }
    private static JsonObject base() {
        JsonObject r = new JsonObject(); r.addProperty("event_order", ++order); r.addProperty("render_clock_frames", clock);
        r.addProperty("phase", phase); r.addProperty("thread", Thread.currentThread().getName()); return r;
    }
    public static synchronized void read(Object stream, ByteBuffer result) {
        if (!requested() || result == null || !result.hasRemaining()) return;
        byte[] pcm = new byte[result.remaining()]; result.duplicate().get(pcm);
        JsonObject r = base(); r.addProperty("read_index", reads.size());
        r.addProperty("stream_identity", System.identityHashCode(stream)); r.addProperty("stream_class", stream.getClass().getName());
        int start = offsets.getOrDefault(stream, 0); offsets.put(stream, start + pcm.length);
        r.addProperty("pcm_offset_bytes", start); r.addProperty("pcm_bytes", pcm.length); r.addProperty("pcm_sha256", MainContextOutputCapture.sha(pcm));
        if (stream instanceof ProceduralDroneAudioStream) {
            var state = (DroneAcousticRenderState) CalibratedGainCapture.field(stream, "state");
            var snapshot = state.audioSnapshot();
            r.addProperty("source_distance_m", snapshot.source().positionMeters().subtract(snapshot.listener().positionMeters()).length());
            var layer = (com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer.Layer) CalibratedGainCapture.field(stream, "layer");
            r.addProperty("layer", layer.name()); r.addProperty("snapshot_identity", System.identityHashCode(snapshot));
            r.addProperty("calibration", layer == com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer.Layer.MOTOR
                    ? snapshot.calibration().motorPlaybackAmplitude() : snapshot.calibration().propellerPlaybackAmplitude());
        } else {
            var state = (ListenerReverbState) CalibratedGainCapture.field(stream, "state");
            var sources = state.snapshot().sources();
            if (sources.size() > 1) throw new IllegalStateException("transition wet read has foreign sources");
            r.addProperty("layer", "WET"); r.addProperty("source_count", sources.size());
            r.addProperty("motor_amplitude", sources.isEmpty() ? 0 : sources.get(0).motorAmplitude());
            r.addProperty("propeller_amplitude", sources.isEmpty() ? 0 : sources.get(0).propellerAmplitude());
        }
        reads.add(r); pending.computeIfAbsent(stream, ignored -> new ArrayDeque<>()).add(r);
        if (reads.size() > 8192) throw new IllegalStateException("bounded transition read recorder exhausted");
    }
    public static synchronized void queued(AudioStream stream, int source, int buffer) {
        if (!requested() || !(stream instanceof ProceduralDroneAudioStream || stream instanceof ListenerReverbAudioStream)) return;
        var p = pending.get(stream); if (p == null || p.isEmpty()) throw new IllegalStateException("native buffer lacks its observed read");
        JsonObject read = p.removeFirst(), r = base(); r.addProperty("queue_index", queues.size());
        r.addProperty("read_index", read.get("read_index").getAsInt()); r.addProperty("stream_identity", System.identityHashCode(stream));
        r.addProperty("source", source); r.addProperty("buffer", buffer);
        r.addProperty("buffer_valid", AL10.alIsBuffer(buffer)); r.addProperty("buffer_bytes", AL10.alGetBufferi(buffer, AL10.AL_SIZE));
        r.addProperty("buffer_frequency", AL10.alGetBufferi(buffer, AL10.AL_FREQUENCY));
        r.addProperty("buffer_bits", AL10.alGetBufferi(buffer, AL10.AL_BITS)); r.addProperty("buffer_channels", AL10.alGetBufferi(buffer, AL10.AL_CHANNELS));
        r.add("native", nativeState(source)); queues.add(r);
        if (queues.size() > 8192) throw new IllegalStateException("bounded transition queue recorder exhausted");
    }
    static synchronized JsonObject observe(Channel ch, String point) {
        int source = ((ChannelSourceAccessor) ch).fpvdrone$getSource(); JsonObject r = base();
        r.addProperty("source", source); r.addProperty("allocation_identity", WetChannelLifecycleCapture.identity(ch)); r.addProperty("point", point);
        r.add("native", nativeState(source)); timeline.add(r);
        if (timeline.size() > 16000) throw new IllegalStateException("bounded transition timeline exhausted"); return r.deepCopy();
    }
    private static JsonObject nativeState(int source) {
        JsonObject r = new JsonObject(); r.addProperty("state", AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE));
        r.addProperty("queued", AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED));
        r.addProperty("processed", AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED));
        r.addProperty("sample_offset", AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET));
        r.addProperty("gain", AL10.alGetSourcef(source, AL10.AL_GAIN)); r.addProperty("pitch", AL10.alGetSourcef(source, AL10.AL_PITCH));
        r.addProperty("al_error", AL10.alGetError());
        if (r.get("al_error").getAsInt() != 0) throw new IllegalStateException("native transition observation failed"); return r;
    }
    static synchronized JsonObject snapshot() {
        JsonObject r = new JsonObject(); r.add("reads", reads.deepCopy()); r.add("queues", queues.deepCopy()); r.add("timeline", timeline.deepCopy());
        r.addProperty("render_clock_frames", clock); r.addProperty("pending_read_count", pending.values().stream().mapToInt(ArrayDeque::size).sum()); return r;
    }
}
