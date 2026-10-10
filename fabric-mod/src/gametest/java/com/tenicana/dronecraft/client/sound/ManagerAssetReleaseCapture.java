package com.tenicana.dronecraft.client.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.mixin.*;
import com.tenicana.dronecraft.entity.DroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Test-only observer and loopback clock. It never invokes or cancels a manager tick. */
public final class ManagerAssetReleaseCapture {
    private static volatile Session current;
    private ManagerAssetReleaseCapture() { }

    static final class Session {
        final String name;
        final DroneEntity drone;
        final List<DroneLoopSoundInstance> sounds;
        final List<Channel> channels;
        final int warmTicks, releaseTicks, frames;
        final Runnable releaseStimulus;
        final JsonArray ticks = new JsonArray(), managerStops = new JsonArray(), channelStops = new JsonArray();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final CompletableFuture<Void> complete = new CompletableFuture<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        volatile boolean armed = true;
        volatile int scheduledTicks, renderedTicks;
        volatile long renderedFrames;
        Session(String name, DroneEntity drone, List<DroneLoopSoundInstance> sounds, List<Channel> channels,
                int warmTicks, int releaseTicks, int frames, Runnable releaseStimulus) {
            this.name=name; this.drone=drone; this.sounds=List.copyOf(sounds); this.channels=List.copyOf(channels);
            this.warmTicks=warmTicks; this.releaseTicks=releaseTicks; this.frames=frames; this.releaseStimulus=releaseStimulus;
        }
    }

    static Session begin(String name, DroneEntity drone, List<DroneLoopSoundInstance> sounds, List<Channel> channels,
                         int warmTicks, int releaseTicks, int frames, Runnable releaseStimulus) {
        if (current!=null || sounds.size()!=2 || channels.size()!=2 || warmTicks<1 || releaseTicks<45
                || warmTicks+releaseTicks>256 || frames!=2400)
            throw new IllegalArgumentException("invalid/busy bounded manager release clock");
        Session s=new Session(name,drone,sounds,channels,warmTicks,releaseTicks,frames,releaseStimulus);
        current=s; return s;
    }

    static void end(Session s) {
        if (current!=s || !s.complete.isDone()) throw new IllegalStateException("release clock not quiescent");
        current=null;
    }

    static void disarm() { Session s=current; if(s!=null)s.armed=false; }

    /** Caller first disarms on the client and awaits a SoundExecutor FIFO barrier. */
    static void abandonAfterOwnerBarrier(Session s) {
        if(current!=s || s.armed || s.renderedTicks!=s.scheduledTicks)
            throw new IllegalStateException("cannot abandon release clock with owner work outstanding");
        current=null;
    }

    /** Called at RETURN of the actual END_CLIENT_TICK manager method. */
    public static void afterManagerTick(Minecraft client) {
        Session s=current;
        if(s==null || !s.armed || !ManagerAssetReleaseClientGameTest.requested()) return;
        int index=s.scheduledTicks++;
        JsonObject row=new JsonObject(); row.addProperty("tick_index",index);
        row.addProperty("phase",index<s.warmTicks?"WARM":"RELEASE");
        row.addProperty("release_tick_index",index-s.warmTicks);
        row.addProperty("client_game_time",client.level==null?-1:client.level.getGameTime());
        row.addProperty("client_paused",client.isPaused()); row.addProperty("rpm",s.drone.getAverageMotorRpm());
        row.addProperty("world_entity_identity",client.level==null?0:System.identityHashCode(client.level.getEntity(s.drone.getId())));
        row.addProperty("expected_entity_identity",System.identityHashCode(s.drone));
        row.addProperty("manager_contains_entity",activeSets().containsKey(s.drone.getId()));
        row.addProperty("manager_set_count",activeSets().size());
        row.addProperty("client_handle_count",((SoundEngineChannelsAccessor)engine(client)).fpvdrone$getInstanceToChannel().size());
        JsonArray envelopes=new JsonArray();
        for(DroneLoopSoundInstance sound:s.sounds) envelopes.add(envelope(sound));
        row.add("envelopes_after_manager_tick",envelopes);
        synchronized(s) { s.ticks.add(row); }
        ((SoundEngineExecutorAccessor)engine(client)).fpvdrone$getExecutor().schedule(()->renderTick(s,row));
        if(s.scheduledTicks==s.warmTicks) {
            // The next genuine SoundEngine tick consumes RPM zero; no manual sound.tick call.
            s.releaseStimulus.run();
            AssetNativeCapture.phase(s.name+":RELEASE"); CalibratedGainCapture.phase(s.name+":RELEASE");
        }
        if(s.scheduledTicks==s.warmTicks+s.releaseTicks) s.armed=false;
    }

    /** HEAD of production DroneSoundSet.stop; captures the values before end()/stop(). */
    public static void beforeManagerStop(Object set) {
        Session s=current; if(s==null || !s.armed || field(set,"drone")!=s.drone) return;
        JsonObject row=new JsonObject(); row.addProperty("tick_index",s.scheduledTicks);
        row.addProperty("rpm",s.drone.getAverageMotorRpm()); row.addProperty("entity_id",s.drone.getId());
        row.addProperty("entity_alive",s.drone.isAlive()); row.addProperty("entity_removed",s.drone.isRemoved());
        row.addProperty("entity_silent",s.drone.isSilent()); row.addProperty("world_identity",System.identityHashCode(s.drone.level()));
        JsonArray layers=new JsonArray();
        for(String name:List.of("motor","propeller")) {
            Object value=field(set,name); if(value instanceof DroneLoopSoundInstance sound) layers.add(envelope(sound));
        }
        row.add("layers",layers); synchronized(s) { s.managerStops.add(row); }
    }

    /** HEAD of actual native Channel.stop, outside any audio callback. */
    public static void beforeChannelStop(Object object) {
        Session s=current; if(s==null) return;
        Channel channel=(Channel)object; int i=s.channels.indexOf(channel); if(i<0)return;
        JsonObject row=nativeSnapshot(channel); row.addProperty("layer",s.sounds.get(i).layer().name());
        row.addProperty("sound_identity",System.identityHashCode(s.sounds.get(i)));
        row.addProperty("rendered_frames_before_stop",s.renderedFrames);
        row.addProperty("rendered_ticks_before_stop",s.renderedTicks);
        row.addProperty("scheduled_client_ticks_at_stop",s.scheduledTicks);
        synchronized(s) { s.channelStops.add(row); }
    }

    private static void renderTick(Session s,JsonObject row) {
        try {
            if(s.failure.get()!=null) return;
            MainOutputLibraryAccessor library=library();
            MainContextOutputCapture.requireCurrent(library.mainOutput$getDevice(),library.mainOutput$getContext());
            row.addProperty("owner_thread",Thread.currentThread().getName());
            row.addProperty("device",library.mainOutput$getDevice()); row.addProperty("context",library.mainOutput$getContext());
            row.addProperty("generation",MainContextOutputCapture.generation(library.mainOutput$getDevice(),library.mainOutput$getContext()));
            JsonArray before=new JsonArray(); for(Channel c:s.channels)before.add(nativeSnapshot(c)); row.add("native_before",before);
            ByteBuffer bytes=ByteBuffer.allocate(s.frames*4).order(ByteOrder.LITTLE_ENDIAN);
            for(int f=0;f<s.frames;f+=1024) {
                int count=Math.min(1024,s.frames-f); FloatBuffer samples=BufferUtils.createFloatBuffer(count);
                SOFTLoopback.alcRenderSamplesSOFT(library.mainOutput$getDevice(),samples,count);
                if(AL10.alGetError()!=0 || ALC10.alcGetError(library.mainOutput$getDevice())!=0)
                    throw new IllegalStateException("actual release loopback render failed");
                while(samples.hasRemaining())bytes.putFloat(samples.get());
                for(Channel c:s.channels) {
                    int source=((ChannelSourceAccessor)c).fpvdrone$getSource();
                    if(initialized(c) && AL10.alIsSource(source) && AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE)==AL10.AL_PLAYING) {
                        c.updateStream();
                        if(AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED)<1)
                            throw new IllegalStateException("release stream underrun");
                    }
                }
                if(AL10.alGetError()!=0)throw new IllegalStateException("release queue update failed");
            }
            byte[] pcm=bytes.array(); row.addProperty("output_offset_bytes",s.output.size()); row.addProperty("output_bytes",pcm.length);
            row.addProperty("output_sha256",MainContextOutputCapture.sha(pcm)); s.output.writeBytes(pcm); s.renderedFrames+=s.frames;
            JsonArray after=new JsonArray(); for(Channel c:s.channels)after.add(nativeSnapshot(c)); row.add("native_after",after);
        } catch(Throwable error) { s.failure.compareAndSet(null,error); row.addProperty("failure",error.toString()); }
        finally {
            s.renderedTicks++;
            if(s.renderedTicks==s.warmTicks+s.releaseTicks) s.complete.complete(null);
        }
    }

    static JsonObject snapshot(Session s) {
        if(!s.complete.isDone())throw new IllegalStateException("partial release clock snapshot");
        synchronized(s) {
            JsonObject row=new JsonObject(); row.addProperty("warmup_render_ticks",s.warmTicks); row.addProperty("release_render_ticks",s.releaseTicks);
            row.addProperty("render_step_frames",s.frames); row.addProperty("scheduled_ticks",s.scheduledTicks); row.addProperty("rendered_ticks",s.renderedTicks);
            row.addProperty("rendered_frames",s.renderedFrames); row.add("ticks",s.ticks.deepCopy());
            row.add("manager_stop_observations",s.managerStops.deepCopy()); row.add("native_channel_stop_observations",s.channelStops.deepCopy());
            return row;
        }
    }

    static JsonObject envelope(DroneLoopSoundInstance sound) {
        JsonObject row=new JsonObject(); row.addProperty("sound_identity",System.identityHashCode(sound));
        row.addProperty("layer",sound.layer().name()); row.addProperty("raw_envelope",(Float)field(sound,"volume"));
        row.addProperty("effective_volume",sound.getVolume()); row.addProperty("pitch",sound.getPitch());
        row.addProperty("stopped",sound.isStopped()); row.addProperty("procedural_backend",sound.usesProceduralAudio());
        return row;
    }

    static JsonObject nativeSnapshot(Channel channel) {
        MainOutputLibraryAccessor library=library(); MainContextOutputCapture.requireCurrent(library.mainOutput$getDevice(),library.mainOutput$getContext());
        int source=((ChannelSourceAccessor)channel).fpvdrone$getSource(); JsonObject row=new JsonObject();
        row.addProperty("source",source); row.addProperty("allocation_identity",WetChannelLifecycleCapture.identity(channel));
        row.addProperty("initialized",initialized(channel)); boolean valid=AL10.alIsSource(source); row.addProperty("source_valid",valid);
        if(valid) {
            row.addProperty("gain",AL10.alGetSourcef(source,AL10.AL_GAIN)); row.addProperty("pitch",AL10.alGetSourcef(source,AL10.AL_PITCH));
            row.addProperty("source_state",AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE));
            row.addProperty("queued",AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED)); row.addProperty("processed",AL10.alGetSourcei(source,AL10.AL_BUFFERS_PROCESSED));
            row.addProperty("sample_offset",AL10.alGetSourcei(source,AL11.AL_SAMPLE_OFFSET));
            row.addProperty("source_model",AL10.alGetSourcei(source,AL10.AL_DISTANCE_MODEL));
            row.addProperty("reference",AL10.alGetSourcef(source,AL10.AL_REFERENCE_DISTANCE)); row.addProperty("maximum",AL10.alGetSourcef(source,AL10.AL_MAX_DISTANCE));
            row.addProperty("rolloff",AL10.alGetSourcef(source,AL10.AL_ROLLOFF_FACTOR));
            FloatBuffer position=BufferUtils.createFloatBuffer(3); AL10.alGetSourcefv(source,AL10.AL_POSITION,position); JsonArray p=new JsonArray(); while(position.hasRemaining())p.add(position.get()); row.add("position",p);
        }
        FloatBuffer listener=BufferUtils.createFloatBuffer(3); AL10.alGetListenerfv(AL10.AL_POSITION,listener); JsonArray p=new JsonArray(); while(listener.hasRemaining())p.add(listener.get()); row.add("listener_position",p);
        row.addProperty("listener_gain",AL10.alGetListenerf(AL10.AL_GAIN)); row.addProperty("al_error",AL10.alGetError());
        if(row.get("al_error").getAsInt()!=0)throw new IllegalStateException("release native getter failed");
        return row;
    }

    static Object field(Object target,String name) {
        for(Class<?> type=target instanceof Class<?> c?c:target.getClass();type!=null;type=type.getSuperclass()) {
            try {Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class<?>?null:target);}
            catch(NoSuchFieldException ignored) { }
            catch(ReflectiveOperationException error) {throw new IllegalStateException(error);}
        }
        throw new IllegalStateException("missing observed field "+name);
    }
    @SuppressWarnings("unchecked") static Map<Integer,Object> activeSets() {return (Map<Integer,Object>)field(DroneSoundManager.class,"ACTIVE_SOUNDS");}
    private static boolean initialized(Channel channel) {return ((AtomicBoolean)field(channel,"initialized")).get();}
    private static SoundEngine engine(Minecraft client) {return ((SoundManagerEngineAccessor)client.getSoundManager()).fpvdrone$getSoundEngine();}
    private static MainOutputLibraryAccessor library() {return (MainOutputLibraryAccessor)((SoundEngineMainOutputAccessor)engine(Minecraft.getInstance())).mainOutput$getLibrary();}
}
