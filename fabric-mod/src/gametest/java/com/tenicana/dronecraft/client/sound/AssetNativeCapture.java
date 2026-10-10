package com.tenicana.dronecraft.client.sound;

import com.google.gson.*;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.mixin.ChannelSourceAccessor;
import com.tenicana.dronecraft.client.mixin.SoundEngineMainOutputAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.*;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;

/** Test-only observations. Production calls always run outside the recorder monitor. */
public final class AssetNativeCapture {
    public static final String PROPERTY="fpvdrone.acoustics.assetNativeProtocol";
    private static long order;
    private static volatile String phase="BOOT";
    private static final JsonArray scalars=new JsonArray(), gains=new JsonArray(), plays=new JsonArray();
    private static final IdentityHashMap<Object,JsonArray> reads=new IdentityHashMap<>();
    private static final IdentityHashMap<Object,Integer> offsets=new IdentityHashMap<>();
    private static final ThreadLocal<Double> coefficient=new ThreadLocal<>();
    private AssetNativeCapture() { }
    public static boolean requested() { return System.getProperty(PROPERTY)!=null; }
    public static boolean closed(net.minecraft.client.sounds.AudioStream stream) {
        if(stream instanceof CalibratedAssetAudioStream)
            return (Boolean)CalibratedGainCapture.field(CalibratedGainCapture.field(stream,"cursor"),"closed");
        return WetMainOutputCapture.closed(stream);
    }
    static void phase(String value) { phase=value; }
    private static JsonObject row() { var r=new JsonObject();r.addProperty("order",++order);r.addProperty("phase",phase);r.addProperty("thread",Thread.currentThread().getName());return r; }
    public static synchronized void scalar(Object engine,SoundInstance sound,float input,SoundSource source,float result) {
        if(!requested() || !(sound instanceof DroneLoopSoundInstance))return;
        var r=row();r.addProperty("sound_identity",System.identityHashCode(sound));r.addProperty("input",input);r.addProperty("result",result);
        r.addProperty("source",source.name());r.addProperty("final_category_volume",Minecraft.getInstance().options.getFinalSoundSourceVolume(source));
        r.addProperty("gain_by_source",((SoundEngineMainOutputAccessor)engine).mainOutput$getGains().getFloat(source));scalars.add(r);
    }
    public static JsonObject beforeGain(Object object,float input) {
        if(!requested())return null;
        Channel c=(Channel)object;int source=((ChannelSourceAccessor)c).fpvdrone$getSource();
        float before=AL10.alGetSourcef(source,AL10.AL_GAIN);int error=AL10.alGetError();
        synchronized(AssetNativeCapture.class) {var r=row();r.addProperty("allocation_identity",WetChannelLifecycleCapture.identity(c));r.addProperty("source",source);r.addProperty("input",input);r.addProperty("before",before);r.addProperty("al_error_before",error);return r;}
    }
    public static void afterGain(Object object,JsonObject r) {
        if(r==null)return;float after=AL10.alGetSourcef(r.get("source").getAsInt(),AL10.AL_GAIN);int error=AL10.alGetError();
        synchronized(AssetNativeCapture.class) {r.addProperty("after",after);r.addProperty("al_error_after",error);r.addProperty("end_order",++order);gains.add(r);if(gains.size()>40000)throw new IllegalStateException("gain recorder exhausted");}
    }
    public static void play(Object object) {
        if(!requested())return;Channel c=(Channel)object;int source=((ChannelSourceAccessor)c).fpvdrone$getSource();
        float gain=AL10.alGetSourcef(source,AL10.AL_GAIN);int error=AL10.alGetError();long context=ALC10.alcGetCurrentContext();
        synchronized(AssetNativeCapture.class) {var r=row();r.addProperty("allocation_identity",WetChannelLifecycleCapture.identity(c));r.addProperty("source",source);r.addProperty("gain",gain);r.addProperty("context",context);r.addProperty("al_error",error);plays.add(r);}
    }
    public static void coefficient(double value) { if(requested())coefficient.set(value); }
    public static void read(Object stream,ByteBuffer bytes) {
        if(!requested())return;Double c=coefficient.get();coefficient.remove();
        if(c==null)throw new IllegalStateException("actual asset supplier invocation missing");
        byte[] pcm=new byte[bytes.remaining()];bytes.duplicate().get(pcm);MainContextOutputCapture.record(stream,bytes);
        synchronized(AssetNativeCapture.class) {
            var r=row();r.addProperty("coefficient",c);r.addProperty("pcm_offset_bytes",offsets.getOrDefault(stream,0));r.addProperty("pcm_bytes",pcm.length);r.addProperty("pcm_sha256",MainContextOutputCapture.sha(pcm));
            offsets.put(stream,offsets.getOrDefault(stream,0)+pcm.length);reads.computeIfAbsent(stream,x->new JsonArray()).add(r);
        }
    }
    static synchronized JsonArray reads(Object stream) { var a=reads.get(stream);if(a==null)throw new IllegalStateException("unobserved asset stream");return a.deepCopy(); }
    static synchronized JsonObject snapshot() {var r=new JsonObject();r.add("first_scalar_calls",scalars.deepCopy());r.add("channel_gains",gains.deepCopy());r.add("native_play_calls",plays.deepCopy());return r;}
}
