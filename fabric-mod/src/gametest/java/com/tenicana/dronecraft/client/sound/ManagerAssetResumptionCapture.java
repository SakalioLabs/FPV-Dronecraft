package com.tenicana.dronecraft.client.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.mixin.*;
import com.tenicana.dronecraft.entity.DroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/** Test-only observations and owner FIFO clock; never calls or cancels a sound/manager tick. */
public final class ManagerAssetResumptionCapture {
    private static volatile Session current;
    private static final List<Phase> PHASES=List.of(new Phase("WARM",12000,6),new Phase("SHORT_ZERO",0,6),
            new Phase("SHORT_RESUME",12000,12),new Phase("LONG_ZERO",0,60),
            new Phase("RESTART",12000,12),new Phase("FINAL_ZERO",0,60));
    static final int TOTAL_TICKS=156;
    private ManagerAssetResumptionCapture() { }
    private record Phase(String name,int rpm,int ticks) { }

    static final class Generation {
        final int generation,setGeneration;
        final Object set;
        final DroneLoopSoundInstance sound;
        final String layer;
        final JsonObject binding=new JsonObject();
        volatile Channel channel;
        volatile CalibratedAssetAudioStream stream;
        Generation(int generation,int setGeneration,Object set,DroneLoopSoundInstance sound) {
            this.generation=generation;this.setGeneration=setGeneration;this.set=set;this.sound=sound;
            this.layer=sound.layer().name();
        }
    }

    static final class Session {
        final String name;
        final DroneEntity drone;
        final int frames;
        final IntConsumer rpmStimulus;
        final JsonArray ticks=new JsonArray(),soundTicks=new JsonArray(),initializations=new JsonArray(),engineTicks=new JsonArray();
        final JsonArray managerStops=new JsonArray(),channelStops=new JsonArray();
        final IdentityHashMap<DroneLoopSoundInstance,Generation> bySound=new IdentityHashMap<>();
        final IdentityHashMap<Object,Integer> bySet=new IdentityHashMap<>();
        final List<Generation> generations=new ArrayList<>();
        final ByteArrayOutputStream output=new ByteArrayOutputStream();
        final CompletableFuture<Void> complete=new CompletableFuture<>();
        final AtomicReference<Throwable> failure=new AtomicReference<>();
        volatile boolean armed,clockStarted,startPending,cleanup;
        volatile int scheduledTicks,renderedTicks,phaseIndex,phaseTicks;
        volatile int currentEngineTick=-1,completedEngineTick=-1,lastScheduledEngineTick=-1;
        int observedEngineTicks;
        JsonObject activeEngineTick;
        volatile long renderedFrames;
        long order;
        Session(String name,DroneEntity drone,int frames,IntConsumer rpmStimulus) {
            this.name=name;this.drone=drone;this.frames=frames;this.rpmStimulus=rpmStimulus;
        }
    }

    /** Begin before adding the fixture-owned world entity, so constructor and admission ticks are observed. */
    static Session begin(String name,DroneEntity drone,int frames,IntConsumer rpmStimulus) {
        if(current!=null || drone==null || rpmStimulus==null || frames!=2400)
            throw new IllegalArgumentException("invalid/busy bounded manager resumption observer");
        Session s=new Session(name,drone,frames,rpmStimulus);current=s;return s;
    }

    static boolean initialStreamsReady(Session s,Minecraft client) {
        if(current!=s || s.clockStarted)throw new IllegalStateException("not an admission session");
        Generation[] voices=discoverCurrent(s,client);
        for(Generation g:voices) {
            if(g==null || g.channel==null || !( ((MainOutputChannelAccessor)g.channel).mainOutput$getStream()
                    instanceof CalibratedAssetAudioStream))return false;
        }
        return true;
    }

    /** The fixture first checks actual initial stream readiness and awaits its normal sound-owner barrier. */
    static void startClock(Session s) {
        if(current!=s || s.clockStarted || s.drone.getAverageMotorRpm()!=12000)
            throw new IllegalStateException("resumption clock must start once at actual RPM 12000");
        Generation[] voices=discoverCurrent(s,Minecraft.getInstance());
        for(Generation g:voices)if(g==null || g.channel==null
                || !(((MainOutputChannelAccessor)g.channel).mainOutput$getStream() instanceof CalibratedAssetAudioStream))
            throw new IllegalStateException("initial normalmanager two-layer streams are not attached");
        s.clockStarted=true;s.startPending=true;
    }

    /** Keep observing cleanup stops after the bounded render clock has finished. */
    static void markCleanup(Session s) {
        if(current!=s)throw new IllegalStateException("unknown cleanup session");
        s.cleanup=true;phase(s,"CLEANUP");
    }

    static void disarm() {Session s=current;if(s!=null){s.armed=false;s.startPending=false;}}
    static void abandonAfterOwnerBarrier(Session s) {
        if(current!=s || s.armed || s.renderedTicks!=s.scheduledTicks)
            throw new IllegalStateException("cannot abandon resumption observer with owner work outstanding");
        current=null;
    }
    static void end(Session s) {
        if(current!=s || !s.complete.isDone() || s.armed || s.renderedTicks!=s.scheduledTicks)
            throw new IllegalStateException("resumption clock is not quiescent");
        current=null;
    }
    static List<Generation> generations(Session s) {synchronized(s){return List.copyOf(s.generations);}}

    /** HEAD of the actual SoundEngine.tick(boolean); a requested start waits for this genuine unpaused boundary. */
    public static void beforeEngineTick(boolean paused) {
        Session s=current;if(s==null || !ManagerAssetResumptionClientGameTest.requested())return;
        try {
            if(s.activeEngineTick!=null)throw new IllegalStateException("nested actual SoundEngine tick");
            if(s.startPending && !paused) {s.startPending=false;s.armed=true;phase(s,"WARM");}
            JsonObject row=new JsonObject();row.addProperty("sound_engine_tick_id",s.observedEngineTicks++);
            row.addProperty("paused",paused);row.addProperty("clock_tick_index",s.armed?s.scheduledTicks:-1);
            row.addProperty("phase",phaseName(s));row.addProperty("rpm_before",s.drone.getAverageMotorRpm());
            Minecraft client=Minecraft.getInstance();row.addProperty("client_game_time",client.level==null?-1:client.level.getGameTime());
            synchronized(s){stamp(s,row);s.engineTicks.add(row);}
            s.currentEngineTick=row.get("sound_engine_tick_id").getAsInt();s.activeEngineTick=row;
        }catch(Throwable error){s.failure.compareAndSet(null,error);}
    }

    /** RETURN of that same production call, after every genuine sound tick and before manager END_CLIENT_TICK. */
    public static void afterEngineTick() {
        Session s=current;if(s==null || s.activeEngineTick==null)return;
        JsonObject row=s.activeEngineTick;row.addProperty("rpm_after",s.drone.getAverageMotorRpm());
        synchronized(s){row.addProperty("end_order",++s.order);row.addProperty("returned_normally",true);}
        s.completedEngineTick=s.currentEngineTick;s.currentEngineTick=-1;s.activeEngineTick=null;
    }

    /** Genuine constructor RETURN, before resolution/play; this observer changes no production field. */
    public static void initialized(Object object) {
        Session s=current;if(s==null || !ManagerAssetResumptionClientGameTest.requested())return;
        DroneLoopSoundInstance sound=(DroneLoopSoundInstance)object;if(field(sound,"drone")!=s.drone)return;
        try {
            Generation g=observeSound(s,sound,"CONSTRUCTOR_RETURN");JsonObject row=rawEnvelope(sound);
            row.addProperty("pre_resolution_raw_fields_only",true);
            row.addProperty("observation","CONSTRUCTOR_RETURN");row.addProperty("sound_generation",g.generation);
            row.addProperty("set_generation",g.setGeneration);
            synchronized(s){stamp(s,row);g.binding.add("constructor_initial",row.deepCopy());s.initializations.add(row);}
        }catch(Throwable error){s.failure.compareAndSet(null,error);}
    }

    /** HEAD values deliberately exclude targetVolume(): that field can be stale on RPM resumption. */
    public static JsonObject beforeSoundTick(Object object) {
        Session s=current;if(s==null || !ManagerAssetResumptionClientGameTest.requested())return null;
        DroneLoopSoundInstance sound=(DroneLoopSoundInstance)object;if(field(sound,"drone")!=s.drone)return null;
        try {
            Generation g=observeSound(s,sound,"SOUND_TICK_HEAD");JsonObject row=new JsonObject();
            row.addProperty("sound_generation",g.generation);row.addProperty("set_generation",g.setGeneration);
            row.addProperty("sound_identity",System.identityHashCode(sound));row.addProperty("layer",g.layer);
            row.addProperty("sound_engine_tick_id",s.currentEngineTick);
            row.addProperty("clock_tick_index",s.clockStarted?s.scheduledTicks:-1);
            row.addProperty("phase",phaseName(s));row.addProperty("rpm_before",s.drone.getAverageMotorRpm());
            row.addProperty("before",(Float)field(sound,"volume"));row.addProperty("coefficient_before",(Double)field(sound,"assetAmplitude"));
            row.addProperty("asset_operating_point_valid_before",(Boolean)field(sound,"assetOperatingPointValid"));
            row.addProperty("stopped_before",sound.isStopped());row.addProperty("entity_alive_before",s.drone.isAlive());
            row.addProperty("entity_removed_before",s.drone.isRemoved());
            synchronized(s){stamp(s,row);}
            return row;
        }catch(Throwable error){s.failure.compareAndSet(null,error);return null;}
    }

    public static void afterSoundTick(Object object,JsonObject row,boolean returnedNormally) {
        if(row==null)return;Session s=current;if(s==null)return;
        try {
            DroneLoopSoundInstance sound=(DroneLoopSoundInstance)object;
            row.addProperty("after",(Float)field(sound,"volume"));row.addProperty("rpm_after",s.drone.getAverageMotorRpm());
            row.addProperty("coefficient_after",(Double)field(sound,"assetAmplitude"));
            row.addProperty("asset_operating_point_valid_after",(Boolean)field(sound,"assetOperatingPointValid"));
            row.addProperty("effective_volume_after",sound.getVolume());row.addProperty("pitch_after",sound.getPitch());
            row.addProperty("stopped_after",sound.isStopped());row.addProperty("returned_normally",returnedNormally);
            Method target=DroneLoopSoundInstance.class.getDeclaredMethod("targetVolume");target.setAccessible(true);
            row.addProperty("target_after_diagnostic",(Float)target.invoke(sound));
            row.addProperty("target_after_is_semantic_oracle",false);
            synchronized(s){row.addProperty("end_order",++s.order);s.soundTicks.add(row);
                if(s.soundTicks.size()>20000)throw new IllegalStateException("bounded resumption sound tick recorder exhausted");}
        }catch(Throwable error){s.failure.compareAndSet(null,error);}
    }

    /** RETURN of actual production manager.tick; only the test driver schedules owner rendering and RPM changes. */
    public static void afterManagerTick(Minecraft client) {
        Session s=current;if(s==null || !ManagerAssetResumptionClientGameTest.requested())return;
        try {
            Generation[] voices=discoverCurrent(s,client);if(!s.armed)return;
            if(s.completedEngineTick<0 || s.completedEngineTick==s.lastScheduledEngineTick || s.activeEngineTick!=null)
                throw new IllegalStateException("manager render tick has no fresh completed actual SoundEngine tick");
            int index=s.scheduledTicks;Phase p=PHASES.get(s.phaseIndex);JsonObject row=new JsonObject();
            row.addProperty("sound_engine_tick_id",s.completedEngineTick);s.lastScheduledEngineTick=s.completedEngineTick;
            row.addProperty("tick_index",index);row.addProperty("phase",p.name);row.addProperty("phase_tick_index",s.phaseTicks);
            row.addProperty("expected_rpm",p.rpm);row.addProperty("rpm",s.drone.getAverageMotorRpm());
            row.addProperty("client_game_time",client.level==null?-1:client.level.getGameTime());row.addProperty("client_paused",client.isPaused());
            row.addProperty("world_entity_identity",client.level==null?0:System.identityHashCode(client.level.getEntity(s.drone.getId())));
            row.addProperty("expected_entity_identity",System.identityHashCode(s.drone));
            Object set=activeSets().get(s.drone.getId());row.addProperty("manager_contains_entity",set!=null);
            row.addProperty("manager_set_identity",set==null?0:System.identityHashCode(set));
            row.addProperty("manager_set_count",activeSets().size());row.addProperty("client_handle_count",handles(client).size());
            JsonArray envelopes=new JsonArray();for(int i=0;i<2;i++)envelopes.add(layerEnvelope(voices[i],i));
            row.add("envelopes_after_manager_tick",envelopes);
            synchronized(s){stamp(s,row);s.ticks.add(row);}
            s.scheduledTicks++;
            ((SoundEngineExecutorAccessor)engine(client)).fpvdrone$getExecutor().schedule(()->renderTick(s,row,voices));
            if(++s.phaseTicks==p.ticks) {
                s.phaseTicks=0;
                if(++s.phaseIndex==PHASES.size())s.armed=false;
                else {
                    Phase next=PHASES.get(s.phaseIndex);
                    // Test-driver stimulus only: the following genuine engine/manager tick consumes this RPM.
                    s.rpmStimulus.accept(next.rpm);phase(s,next.name);
                }
            }
        }catch(Throwable error){s.failure.compareAndSet(null,error);s.armed=false;s.complete.complete(null);}
    }

    /** HEAD of the real manager set stop, including fixture cleanup outside the active clock. */
    public static void beforeManagerStop(Object set) {
        Session s=current;if(s==null || field(set,"drone")!=s.drone)return;
        try {
            JsonObject row=new JsonObject();row.addProperty("set_identity",System.identityHashCode(set));
            synchronized(s){row.addProperty("set_generation",setGeneration(s,set));stamp(s,row);}
            stopContext(s,row);JsonArray layers=new JsonArray();
            for(String name:List.of("motor","propeller")) {
                Object value=field(set,name);
                if(value instanceof DroneLoopSoundInstance sound) {
                    Generation g=observeSound(s,sound,"MANAGER_STOP_HEAD");JsonObject e=envelope(sound);
                    e.addProperty("sound_generation",g.generation);e.addProperty("set_generation",g.setGeneration);layers.add(e);
                }else {JsonObject e=new JsonObject();e.addProperty("layer",name.equals("motor")?"MOTOR":"PROPELLER");
                    e.add("sound_generation",JsonNull.INSTANCE);e.add("sound_identity",JsonNull.INSTANCE);e.addProperty("manager_layer_present",false);layers.add(e);}
            }
            row.add("layers",layers);synchronized(s){s.managerStops.add(row);}
        }catch(Throwable error){s.failure.compareAndSet(null,error);}
    }

    /** HEAD of actual Channel.stop, read on its real owner before stop changes native state. */
    public static void beforeChannelStop(Object object) {
        Session s=current;if(s==null)return;Channel channel=(Channel)object;Generation found=null;
        synchronized(s){for(Generation g:s.generations)if(g.channel==channel){found=g;break;}}
        if(found==null)return;
        try {
            bindStreamOwner(s,found);JsonObject row=nativeSnapshot(channel);join(row,found);stopContext(s,row);
            row.addProperty("rendered_frames_before_stop",s.renderedFrames);row.addProperty("rendered_ticks_before_stop",s.renderedTicks);
            row.addProperty("scheduled_client_ticks_at_stop",s.scheduledTicks);
            synchronized(s){stamp(s,row);s.channelStops.add(row);}
        }catch(Throwable error){s.failure.compareAndSet(null,error);}
    }

    private static void stopContext(Session s,JsonObject row) {
        row.addProperty("tick_index",s.scheduledTicks);row.addProperty("phase",phaseName(s));row.addProperty("clock_active",s.armed);
        row.addProperty("sound_engine_tick_id",s.currentEngineTick>=0?s.currentEngineTick:s.completedEngineTick);
        row.addProperty("fixture_cleanup",s.cleanup);row.addProperty("rpm",s.drone.getAverageMotorRpm());row.addProperty("entity_id",s.drone.getId());
        row.addProperty("entity_alive",s.drone.isAlive());row.addProperty("entity_removed",s.drone.isRemoved());row.addProperty("entity_silent",s.drone.isSilent());
        row.addProperty("world_identity",System.identityHashCode(s.drone.level()));
        row.addProperty("cause_classification",s.cleanup?"FIXTURE_CLEANUP":
                !s.drone.isAlive() || s.drone.isRemoved()?"INVALID_ENTITY":s.drone.isSilent()?"SILENT_ENTITY":
                        s.drone.getAverageMotorRpm()==0?"RPM_ZERO_OBSERVED":"OTHER_OBSERVED_STOP");
        row.addProperty("cause_classification_is_semantic_oracle",false);
    }

    private static void renderTick(Session s,JsonObject row,Generation[] voices) {
        try {
            MainOutputLibraryAccessor library=library();MainContextOutputCapture.requireCurrent(library.mainOutput$getDevice(),library.mainOutput$getContext());
            row.addProperty("owner_thread",Thread.currentThread().getName());row.addProperty("device",library.mainOutput$getDevice());row.addProperty("context",library.mainOutput$getContext());
            row.addProperty("generation",MainContextOutputCapture.generation(library.mainOutput$getDevice(),library.mainOutput$getContext()));
            JsonArray before=new JsonArray();for(int i=0;i<2;i++)before.add(nativeLayer(s,voices[i],i));row.add("native_before",before);
            ByteBuffer bytes=ByteBuffer.allocate(s.frames*4).order(ByteOrder.LITTLE_ENDIAN);
            for(int f=0;f<s.frames;f+=1024) {
                int count=Math.min(1024,s.frames-f);FloatBuffer samples=BufferUtils.createFloatBuffer(count);
                SOFTLoopback.alcRenderSamplesSOFT(library.mainOutput$getDevice(),samples,count);
                if(AL10.alGetError()!=0 || ALC10.alcGetError(library.mainOutput$getDevice())!=0)
                    throw new IllegalStateException("actual resumption loopback render failed");
                while(samples.hasRemaining())bytes.putFloat(samples.get());
                for(Generation g:voices)if(g!=null && g.channel!=null) {
                    Channel c=g.channel;int source=((ChannelSourceAccessor)c).fpvdrone$getSource();
                    if(initialized(c) && AL10.alIsSource(source) && AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE)==AL10.AL_PLAYING) {
                        // Test-driver queue service only; scalar/lifecycle observers above never call updateStream.
                        c.updateStream();
                        if(AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED)<1)throw new IllegalStateException("resumption stream underrun");
                    }
                }
                if(AL10.alGetError()!=0)throw new IllegalStateException("resumption queue update failed");
            }
            byte[] pcm=bytes.array();row.addProperty("output_offset_bytes",s.output.size());row.addProperty("output_bytes",pcm.length);
            row.addProperty("output_sha256",MainContextOutputCapture.sha(pcm));s.output.writeBytes(pcm);s.renderedFrames+=s.frames;
            JsonArray after=new JsonArray();for(int i=0;i<2;i++)after.add(nativeLayer(s,voices[i],i));row.add("native_after",after);
        }catch(Throwable error){s.failure.compareAndSet(null,error);row.addProperty("failure",error.toString());}
        finally {s.renderedTicks++;if(s.renderedTicks==TOTAL_TICKS)s.complete.complete(null);}
    }

    private static JsonObject nativeLayer(Session s,Generation g,int layer) {
        if(g==null)return absentLayer(layer);
        bindStreamOwner(s,g);JsonObject row;
        if(g.channel==null) {row=new JsonObject();row.addProperty("channel_pending",true);row.addProperty("source_valid",false);}
        else {row=nativeSnapshot(g.channel);row.addProperty("channel_pending",false);}
        join(row,g);row.addProperty("manager_layer_present",true);row.addProperty("stream_pending",g.stream==null);return row;
    }
    private static JsonObject layerEnvelope(Generation g,int layer) {
        if(g==null)return absentLayer(layer);
        JsonObject row=envelope(g.sound);join(row,g);row.addProperty("manager_layer_present",true);
        row.addProperty("channel_pending",g.channel==null);row.addProperty("stream_pending",g.stream==null);return row;
    }
    private static JsonObject absentLayer(int layer) {
        JsonObject row=new JsonObject();row.addProperty("layer",layer==0?"MOTOR":"PROPELLER");row.addProperty("manager_layer_present",false);
        for(String name:List.of("sound_generation","set_generation","sound_identity","set_identity","channel_allocation_identity","stream_identity"))row.add(name,JsonNull.INSTANCE);
        row.addProperty("channel_pending",false);row.addProperty("stream_pending",false);row.addProperty("source_valid",false);return row;
    }
    private static void join(JsonObject row,Generation g) {
        row.addProperty("sound_generation",g.generation);row.addProperty("set_generation",g.setGeneration);row.addProperty("set_identity",System.identityHashCode(g.set));
        row.addProperty("sound_identity",System.identityHashCode(g.sound));row.addProperty("layer",g.layer);
        if(g.channel==null)row.add("channel_allocation_identity",JsonNull.INSTANCE);
        else row.addProperty("channel_allocation_identity",WetChannelLifecycleCapture.identity(g.channel));
        if(g.stream==null)row.add("stream_identity",JsonNull.INSTANCE);else row.addProperty("stream_identity",System.identityHashCode(g.stream));
    }

    private static Generation[] discoverCurrent(Session s,Minecraft client) {
        Object set=activeSets().get(s.drone.getId());Generation[] voices=new Generation[2];if(set==null)return voices;
        if(field(set,"drone")!=s.drone)throw new IllegalStateException("manager entity identity differs from actual world fixture");
        int i=0;for(String layer:List.of("motor","propeller")) {
            Object value=field(set,layer);
            if(value instanceof DroneLoopSoundInstance sound) {
                Generation g=observeSound(s,sound,"MANAGER_RETURN");voices[i]=g;
                ChannelAccess.ChannelHandle handle=handles(client).get(sound);
                Channel c=handle==null?null:((ChannelHandleChannelAccessor)handle).fpvdrone$getChannel();
                if(c!=null) {
                    if(g.channel!=null && g.channel!=c)throw new IllegalStateException("actual sound changed its Channel identity");
                    g.channel=c;synchronized(s){g.binding.addProperty("channel_identity",System.identityHashCode(c));
                        g.binding.addProperty("channel_allocation_identity",WetChannelLifecycleCapture.identity(c));}
                    Object stream=((MainOutputChannelAccessor)c).mainOutput$getStream();
                    if(stream instanceof CalibratedAssetAudioStream assetStream)bindStreamIdentity(s,g,assetStream);
                }
            }
            i++;
        }
        return voices;
    }
    private static Generation observeSound(Session s,DroneLoopSoundInstance sound,String origin) {
        synchronized(s) {
            Generation previous=s.bySound.get(sound);if(previous!=null)return previous;
            Object set=activeSets().get(s.drone.getId());
            if(set==null || field(set,"drone")!=s.drone)throw new IllegalStateException("observed sound has no actual manager set");
            if(s.generations.size()>=128)throw new IllegalStateException("bounded resumption sound generations exhausted");
            Generation g=new Generation(s.generations.size(),setGeneration(s,set),set,sound);
            g.binding.addProperty("generation",g.generation);g.binding.addProperty("set_generation",g.setGeneration);
            g.binding.addProperty("set_identity",System.identityHashCode(set));g.binding.addProperty("sound_identity",System.identityHashCode(sound));
            g.binding.addProperty("layer",g.layer);g.binding.addProperty("entity_id",s.drone.getId());g.binding.addProperty("entity_identity",System.identityHashCode(s.drone));
            g.binding.addProperty("first_observation",origin);g.binding.addProperty("first_observation_clock_tick",s.clockStarted?s.scheduledTicks:-1);
            g.binding.addProperty("catalog_identity",System.identityHashCode(field(sound,"assetCatalog")));
            g.binding.add("initial_observed_envelope",rawEnvelope(sound));s.bySound.put(sound,g);s.generations.add(g);return g;
        }
    }
    private static int setGeneration(Session s,Object set) {
        Integer existing=s.bySet.get(set);if(existing!=null)return existing;
        int generation=s.bySet.size();s.bySet.put(set,generation);return generation;
    }
    private static void bindStreamOwner(Session s,Generation g) {
        if(g.channel==null)return;
        Object value=((MainOutputChannelAccessor)g.channel).mainOutput$getStream();if(value==null)return;
        if(!(value instanceof CalibratedAssetAudioStream stream))throw new IllegalStateException("normal resolved asset did not attach calibrated stream");
        bindStreamIdentity(s,g,stream);
        if(!g.binding.has("native_first_bound")) {
            JsonObject nativeState=nativeSnapshot(g.channel);
            synchronized(s) {
                g.binding.addProperty("streaming_buffer_bytes",((ChannelStreamingBufferAccessor)g.channel).fpvdrone$getStreamingBufferSize());
                g.binding.addProperty("owner_verified",stream.distanceOwnerVerified());g.binding.addProperty("procedural_backend",g.sound.usesProceduralAudio());
                g.binding.addProperty("resource",String.valueOf(field(g.sound,"calibratedResource")));
                g.binding.addProperty("asset_identity",System.identityHashCode(field(g.sound,"calibratedAsset")));
                g.binding.add("native_first_bound",nativeState);
            }
        }
    }
    private static void bindStreamIdentity(Session s,Generation g,CalibratedAssetAudioStream stream) {
        if(g.stream!=null && g.stream!=stream)throw new IllegalStateException("sound generation changed its actual calibrated stream");
        g.stream=stream;synchronized(s){g.binding.addProperty("stream_identity",System.identityHashCode(stream));
            g.binding.addProperty("stream_class",stream.getClass().getName());}
    }

    static JsonObject snapshot(Session s) {
        if(!s.complete.isDone() || s.renderedTicks!=s.scheduledTicks)throw new IllegalStateException("partial resumption clock snapshot");
        synchronized(s) {
            JsonObject row=new JsonObject();JsonArray phases=new JsonArray();for(Phase p:PHASES){JsonObject r=new JsonObject();r.addProperty("name",p.name);r.addProperty("rpm",p.rpm);r.addProperty("ticks",p.ticks);phases.add(r);}
            row.add("phases",phases);row.addProperty("render_step_frames",s.frames);row.addProperty("scheduled_ticks",s.scheduledTicks);
            row.addProperty("rendered_ticks",s.renderedTicks);row.addProperty("rendered_frames",s.renderedFrames);
            row.add("ticks",s.ticks.deepCopy());row.add("sound_tick_observations",s.soundTicks.deepCopy());row.add("initialization_observations",s.initializations.deepCopy());
            row.add("sound_engine_tick_observations",s.engineTicks.deepCopy());
            row.add("manager_stop_observations",s.managerStops.deepCopy());row.add("native_channel_stop_observations",s.channelStops.deepCopy());
            JsonArray bindings=new JsonArray();for(Generation g:s.generations)bindings.add(g.binding.deepCopy());row.add("generations",bindings);
            row.addProperty("release_resumption_semantics_passed",false);return row;
        }
    }
    static JsonObject envelope(DroneLoopSoundInstance sound) {
        JsonObject row=rawEnvelope(sound);row.addProperty("effective_volume",sound.getVolume());row.addProperty("pitch",sound.getPitch());
        return row;
    }
    private static JsonObject rawEnvelope(DroneLoopSoundInstance sound) {
        JsonObject row=new JsonObject();row.addProperty("sound_identity",System.identityHashCode(sound));row.addProperty("layer",sound.layer().name());
        row.addProperty("raw_envelope",(Float)field(sound,"volume"));row.addProperty("raw_pitch",(Float)field(sound,"pitch"));
        row.addProperty("coefficient",(Double)field(sound,"assetAmplitude"));row.addProperty("asset_operating_point_valid",(Boolean)field(sound,"assetOperatingPointValid"));
        row.addProperty("stopped",sound.isStopped());row.addProperty("procedural_backend",sound.usesProceduralAudio());return row;
    }
    static JsonObject nativeSnapshot(Channel channel) {
        MainOutputLibraryAccessor library=library();MainContextOutputCapture.requireCurrent(library.mainOutput$getDevice(),library.mainOutput$getContext());
        JsonObject row=new JsonObject();int source=((ChannelSourceAccessor)channel).fpvdrone$getSource();boolean initialized=initialized(channel),exists=AL10.alIsSource(source);
        row.addProperty("source",source);row.addProperty("allocation_identity",WetChannelLifecycleCapture.identity(channel));row.addProperty("channel_identity",System.identityHashCode(channel));
        row.addProperty("initialized",initialized);row.addProperty("native_source_exists",exists);row.addProperty("source_valid",initialized && exists);
        // An AL integer may already belong to another allocation after this Channel has retired.
        if(initialized && exists) {
            row.addProperty("gain",AL10.alGetSourcef(source,AL10.AL_GAIN));row.addProperty("pitch",AL10.alGetSourcef(source,AL10.AL_PITCH));
            row.addProperty("source_state",AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE));row.addProperty("queued",AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED));
            row.addProperty("processed",AL10.alGetSourcei(source,AL10.AL_BUFFERS_PROCESSED));row.addProperty("sample_offset",AL10.alGetSourcei(source,AL11.AL_SAMPLE_OFFSET));
            row.addProperty("source_model",AL10.alGetSourcei(source,AL10.AL_DISTANCE_MODEL));row.addProperty("reference",AL10.alGetSourcef(source,AL10.AL_REFERENCE_DISTANCE));
            row.addProperty("maximum",AL10.alGetSourcef(source,AL10.AL_MAX_DISTANCE));row.addProperty("rolloff",AL10.alGetSourcef(source,AL10.AL_ROLLOFF_FACTOR));
            FloatBuffer position=BufferUtils.createFloatBuffer(3);AL10.alGetSourcefv(source,AL10.AL_POSITION,position);JsonArray p=new JsonArray();while(position.hasRemaining())p.add(position.get());row.add("position",p);
        }
        FloatBuffer listener=BufferUtils.createFloatBuffer(3);AL10.alGetListenerfv(AL10.AL_POSITION,listener);JsonArray p=new JsonArray();while(listener.hasRemaining())p.add(listener.get());row.add("listener_position",p);
        row.addProperty("listener_gain",AL10.alGetListenerf(AL10.AL_GAIN));row.addProperty("al_error",AL10.alGetError());
        if(row.get("al_error").getAsInt()!=0)throw new IllegalStateException("resumption native getter failed");return row;
    }
    private static void stamp(Session s,JsonObject row) {row.addProperty("order",++s.order);row.addProperty("thread",Thread.currentThread().getName());}
    private static String phaseName(Session s) {return s.cleanup?"CLEANUP":!s.clockStarted || s.startPending?"ADMISSION":s.phaseIndex>=PHASES.size()?"POST_CLOCK":PHASES.get(s.phaseIndex).name;}
    private static void phase(Session s,String phase) {AssetNativeCapture.phase(s.name+":"+phase);CalibratedGainCapture.phase(s.name+":"+phase);}
    static Object field(Object target,String name) {
        for(Class<?> type=target instanceof Class<?> c?c:target.getClass();type!=null;type=type.getSuperclass()) {
            try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class<?>?null:target);}
            catch(NoSuchFieldException ignored){}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        }
        throw new IllegalStateException("missing observed field "+name);
    }
    @SuppressWarnings("unchecked") static Map<Integer,Object> activeSets(){return (Map<Integer,Object>)field(DroneSoundManager.class,"ACTIVE_SOUNDS");}
    private static boolean initialized(Channel channel){return ((AtomicBoolean)field(channel,"initialized")).get();}
    private static Map<SoundInstance,ChannelAccess.ChannelHandle> handles(Minecraft client){return ((SoundEngineChannelsAccessor)engine(client)).fpvdrone$getInstanceToChannel();}
    private static SoundEngine engine(Minecraft client){return ((SoundManagerEngineAccessor)client.getSoundManager()).fpvdrone$getSoundEngine();}
    private static MainOutputLibraryAccessor library(){return (MainOutputLibraryAccessor)((SoundEngineMainOutputAccessor)engine(Minecraft.getInstance())).mainOutput$getLibrary();}
}
