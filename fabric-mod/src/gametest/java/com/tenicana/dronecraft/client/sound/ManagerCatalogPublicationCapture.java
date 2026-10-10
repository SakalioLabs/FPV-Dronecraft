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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Test-only observations plus a bounded client/owner driver; never invokes or cancels a production tick. */
public final class ManagerCatalogPublicationCapture {
    private static volatile Session current;
    private static final List<Phase> PHASES=List.of(new Phase("WARM_A",12000,0,6),
            new Phase("PUBLISH_B_OLD",12000,0,8),new Phase("ADMIT_NEW_B",12000,12000,24),
            new Phase("REJECT_B",12000,12000,6),new Phase("ZERO_BOTH",0,0,60),
            new Phase("RESTART_OLD_B",12000,0,12),new Phase("FINAL_ZERO",0,0,60));
    static final int TOTAL_TICKS=176;
    private ManagerCatalogPublicationCapture() { }
    private record Phase(String name,int oldRpm,int newRpm,int ticks) { }

    static final class Generation {
        final int generation,setGeneration;
        final Object set;
        final DroneLoopSoundInstance sound;
        final DroneEntity drone;
        final String role,layer;
        final JsonObject binding=new JsonObject();
        volatile Channel channel;
        volatile CalibratedAssetAudioStream stream;
        Generation(int generation,int setGeneration,Object set,DroneLoopSoundInstance sound,DroneEntity drone,String role) {
            this.generation=generation;this.setGeneration=setGeneration;this.set=set;this.sound=sound;
            this.drone=drone;this.role=role;this.layer=sound.layer().name();
        }
    }

    static final class Session {
        final String name;
        final DroneEntity oldDrone,newDrone;
        final int frames;
        final Consumer<String> phaseAction;
        final BiConsumer<Float,Float> rpmSetter;
        final JsonArray ticks=new JsonArray(),soundTicks=new JsonArray(),initializations=new JsonArray(),engineTicks=new JsonArray();
        final JsonArray managerTicks=new JsonArray(),managerStops=new JsonArray(),channelStops=new JsonArray();
        final JsonArray phaseActions=new JsonArray(),rpmStimuli=new JsonArray();
        final IdentityHashMap<DroneLoopSoundInstance,Generation> bySound=new IdentityHashMap<>();
        final IdentityHashMap<Object,Integer> bySet=new IdentityHashMap<>();
        final List<Generation> generations=new ArrayList<>();
        final ByteArrayOutputStream output=new ByteArrayOutputStream();
        final CompletableFuture<Void> complete=new CompletableFuture<>();
        final AtomicReference<Throwable> failure=new AtomicReference<>();
        volatile CompletableFuture<Void> lastRender=CompletableFuture.completedFuture(null);
        volatile boolean armed,clockStarted,startPending,phaseActionPending,cleanup;
        volatile int scheduledTicks,renderedTicks,phaseIndex,phaseTicks;
        volatile int currentEngineTick=-1,completedEngineTick=-1,lastScheduledEngineTick=-1;
        volatile long renderedFrames;
        int observedEngineTicks,observedManagerTicks,lastPhaseAction=-1;
        JsonObject activeEngineTick,activeManagerTick;
        long order;
        Session(String name,DroneEntity oldDrone,DroneEntity newDrone,int frames,
                Consumer<String> phaseAction,BiConsumer<Float,Float> rpmSetter) {
            this.name=name;this.oldDrone=oldDrone;this.newDrone=newDrone;this.frames=frames;
            this.phaseAction=phaseAction;this.rpmSetter=rpmSetter;
        }
    }

    /** Begin before OLD world admission; NEW is a real precreated entity whose admission belongs to the fixture action. */
    static Session begin(String name,DroneEntity oldDrone,DroneEntity newDrone,int frames,
                         Consumer<String> phaseAction,BiConsumer<Float,Float> rpmSetter) {
        requireClientThread(Minecraft.getInstance());
        if(current!=null || oldDrone==null || newDrone==null || oldDrone==newDrone || oldDrone.getId()!=179001
                || newDrone.getId()!=179002 || frames!=2400 || phaseAction==null || rpmSetter==null)
            throw new IllegalArgumentException("invalid/busy bounded manager catalog publication observer");
        Session s=new Session(name,oldDrone,newDrone,frames,phaseAction,rpmSetter);current=s;return s;
    }

    static boolean initialStreamsReady(Session s,Minecraft client) {
        requireClientThread(client);
        if(current!=s || s.clockStarted)throw new IllegalStateException("not a catalog admission session");
        Generation[] voices=discoverCurrent(s,client);
        for(int i=0;i<2;i++)if(voices[i]==null || voices[i].channel==null || voices[i].stream==null)return false;
        return true;
    }

    /** Arm only at the next real unpaused SoundEngine HEAD, after initial OLD streams reached the owner. */
    static void startClock(Session s) {
        Minecraft client=Minecraft.getInstance();requireClientThread(client);
        if(current!=s || s.clockStarted || s.oldDrone.getAverageMotorRpm()!=12000 || s.newDrone.getAverageMotorRpm()!=0)
            throw new IllegalStateException("catalog clock must start once at OLD 12000 / NEW 0");
        Generation[] voices=discoverCurrent(s,client);
        for(int i=0;i<2;i++)if(voices[i]==null || voices[i].channel==null || voices[i].stream==null)
            throw new IllegalStateException("initial actual OLD two-layer asset streams are not attached");
        s.clockStarted=true;s.startPending=true;s.phaseActionPending=true;
    }

    static void markCleanup(Session s) {
        requireClientThread(Minecraft.getInstance());if(current!=s)throw new IllegalStateException("unknown catalog cleanup session");
        s.cleanup=true;phase(s,"CLEANUP");
    }
    static void disarm() {
        requireClientThread(Minecraft.getInstance());Session s=current;
        if(s!=null){s.armed=false;s.startPending=false;s.phaseActionPending=false;}
    }
    /** Caller disarms, drains real stops, and awaits the actual SoundExecutor FIFO barrier first. */
    static void abandonAfterOwnerBarrier(Session s) {
        if(current!=s || s.armed || !s.lastRender.isDone() || (s.renderedTicks!=s.scheduledTicks && s.failure.get()==null))
            throw new IllegalStateException("cannot abandon catalog observer with owner renders outstanding");
        current=null;
    }
    static void end(Session s) {
        if(current!=s || !s.complete.isDone() || s.armed || !s.lastRender.isDone() || s.renderedTicks!=s.scheduledTicks)
            throw new IllegalStateException("catalog clock is not quiescent");
        current=null;
    }
    static List<Generation> generations(Session s) {synchronized(s){return List.copyOf(s.generations);}}
    /** Shared ordering for fixture receipts; no recorder monitor is held around its actual load/apply calls. */
    static long nextObservationOrder(Session s) {synchronized(s){return ++s.order;}}

    public static void beforeEngineTick(boolean paused) {
        Session s=current;if(s==null || !ManagerCatalogPublicationClientGameTest.requested())return;
        try {
            requireClientThread(Minecraft.getInstance());
            if(s.activeEngineTick!=null)throw new IllegalStateException("nested actual SoundEngine tick");
            if(s.startPending && !paused){s.startPending=false;s.armed=true;phase(s,"WARM_A");}
            JsonObject row=new JsonObject();row.addProperty("sound_engine_tick_id",s.observedEngineTicks++);
            row.addProperty("paused",paused);row.addProperty("clock_tick_index",s.armed?s.scheduledTicks:-1);row.addProperty("phase",phaseName(s));
            row.addProperty("old_rpm_before",s.oldDrone.getAverageMotorRpm());row.addProperty("new_rpm_before",s.newDrone.getAverageMotorRpm());
            row.addProperty("published_catalog_identity_before",System.identityHashCode(CalibratedAssetResources.snapshot()));
            Minecraft client=Minecraft.getInstance();row.addProperty("client_game_time",client.level==null?-1:client.level.getGameTime());
            synchronized(s){stamp(s,row);s.engineTicks.add(row);}
            s.currentEngineTick=row.get("sound_engine_tick_id").getAsInt();s.activeEngineTick=row;
        }catch(Throwable error){s.failure.compareAndSet(null,error);}
    }
    public static void afterEngineTick() {
        Session s=current;if(s==null || s.activeEngineTick==null)return;JsonObject row=s.activeEngineTick;
        row.addProperty("old_rpm_after",s.oldDrone.getAverageMotorRpm());row.addProperty("new_rpm_after",s.newDrone.getAverageMotorRpm());
        row.addProperty("published_catalog_identity_after",System.identityHashCode(CalibratedAssetResources.snapshot()));
        synchronized(s){row.addProperty("end_order",++s.order);row.addProperty("returned_normally",true);}
        s.completedEngineTick=s.currentEngineTick;s.currentEngineTick=-1;s.activeEngineTick=null;
    }

    /** Genuine constructor RETURN uses raw fields only, before normal SoundInstance resolution/play. */
    public static void initialized(Object object) {
        Session s=current;if(s==null || !ManagerCatalogPublicationClientGameTest.requested())return;
        DroneLoopSoundInstance sound=(DroneLoopSoundInstance)object;DroneEntity drone=(DroneEntity)field(sound,"drone");
        if(role(s,drone)==null)return;
        try {
            Generation g=observeSound(s,sound,"CONSTRUCTOR_RETURN");JsonObject row=rawEnvelope(sound);joinSound(row,g);
            row.addProperty("pre_resolution_raw_fields_only",true);row.addProperty("observation","CONSTRUCTOR_RETURN");
            row.addProperty("clock_tick_index",s.armed?s.scheduledTicks:-1);row.addProperty("phase",phaseName(s));
            row.addProperty("published_catalog_identity",System.identityHashCode(CalibratedAssetResources.snapshot()));
            synchronized(s){stamp(s,row);g.binding.add("constructor_initial",row.deepCopy());s.initializations.add(row);}
        }catch(Throwable error){s.failure.compareAndSet(null,error);}
    }

    /** Do not sample the stale pre-update targetVolume as an envelope oracle. */
    public static JsonObject beforeSoundTick(Object object) {
        Session s=current;if(s==null || !ManagerCatalogPublicationClientGameTest.requested())return null;
        DroneLoopSoundInstance sound=(DroneLoopSoundInstance)object;DroneEntity drone=(DroneEntity)field(sound,"drone");
        if(role(s,drone)==null)return null;
        try {
            Generation g=observeSound(s,sound,"SOUND_TICK_HEAD");JsonObject row=new JsonObject();joinSound(row,g);
            row.addProperty("sound_engine_tick_id",s.currentEngineTick);row.addProperty("clock_tick_index",s.armed?s.scheduledTicks:-1);
            row.addProperty("phase",phaseName(s));row.addProperty("rpm_before",drone.getAverageMotorRpm());
            row.addProperty("before",(Float)field(sound,"volume"));row.addProperty("coefficient_before",(Double)field(sound,"assetAmplitude"));
            row.addProperty("asset_operating_point_valid_before",(Boolean)field(sound,"assetOperatingPointValid"));
            row.addProperty("stopped_before",sound.isStopped());row.addProperty("entity_alive_before",drone.isAlive());
            row.addProperty("entity_removed_before",drone.isRemoved());
            row.addProperty("published_catalog_identity_before",System.identityHashCode(CalibratedAssetResources.snapshot()));
            synchronized(s){stamp(s,row);}return row;
        }catch(Throwable error){s.failure.compareAndSet(null,error);return null;}
    }
    public static void afterSoundTick(Object object,JsonObject row,boolean returnedNormally) {
        if(row==null)return;Session s=current;if(s==null)return;
        try {
            DroneLoopSoundInstance sound=(DroneLoopSoundInstance)object;DroneEntity drone=(DroneEntity)field(sound,"drone");
            row.addProperty("after",(Float)field(sound,"volume"));row.addProperty("rpm_after",drone.getAverageMotorRpm());
            row.addProperty("coefficient_after",(Double)field(sound,"assetAmplitude"));
            row.addProperty("asset_operating_point_valid_after",(Boolean)field(sound,"assetOperatingPointValid"));
            row.addProperty("effective_volume_after",sound.getVolume());row.addProperty("pitch_after",sound.getPitch());
            row.addProperty("stopped_after",sound.isStopped());row.addProperty("returned_normally",returnedNormally);
            row.addProperty("published_catalog_identity_after",System.identityHashCode(CalibratedAssetResources.snapshot()));
            Method target=DroneLoopSoundInstance.class.getDeclaredMethod("targetVolume");target.setAccessible(true);
            row.addProperty("target_after_diagnostic",(Float)target.invoke(sound));row.addProperty("target_after_is_semantic_oracle",false);
            synchronized(s){row.addProperty("end_order",++s.order);s.soundTicks.add(row);
                if(s.soundTicks.size()>20000)throw new IllegalStateException("bounded catalog sound tick recorder exhausted");}
        }catch(Throwable error){s.failure.compareAndSet(null,error);}
    }

    /** HEAD driver boundary: actual catalog/world actions finish on the client before this manager selects voices. */
    public static void beforeManagerTick(Minecraft client) {
        Session s=current;if(s==null || !ManagerCatalogPublicationClientGameTest.requested())return;
        try {
            requireClientThread(client);
            if(s.activeManagerTick!=null)throw new IllegalStateException("nested actual manager tick");
            if(s.armed && s.phaseActionPending) {
                // Test-driver FIFO wait only. The owner render never asks the client to mutate state.
                s.lastRender.join();
                if(s.renderedTicks!=s.scheduledTicks)throw new IllegalStateException("phase action preceded previous owner output completion");
                Phase p=PHASES.get(s.phaseIndex);JsonObject action=new JsonObject();
                action.addProperty("phase",p.name);action.addProperty("phase_index",s.phaseIndex);action.addProperty("clock_tick_index",s.scheduledTicks);
                action.addProperty("sound_engine_tick_id",s.completedEngineTick);action.addProperty("manager_tick_id",s.observedManagerTicks);
                action.addProperty("scheduled_ticks_before",s.scheduledTicks);action.addProperty("rendered_ticks_before",s.renderedTicks);
                action.addProperty("rendered_frames_before",s.renderedFrames);action.addProperty("owner_previous_render_complete",s.lastRender.isDone());
                action.addProperty("client_thread_verified",true);action.addProperty("manager_selection_boundary","HEAD_BEFORE_ACTUAL_SELECTION");
                action.addProperty("phase_action_before_manager_selection",true);
                action.addProperty("published_catalog_identity_before",System.identityHashCode(CalibratedAssetResources.snapshot()));
                action.add("roles_before",roleSnapshots(s,client));
                action.addProperty("start_order",nextObservationOrder(s));action.addProperty("thread",Thread.currentThread().getName());
                s.phaseAction.accept(p.name);
                requireClientThread(client);
                action.addProperty("end_order",nextObservationOrder(s));
                action.addProperty("published_catalog_identity_after",System.identityHashCode(CalibratedAssetResources.snapshot()));
                action.addProperty("scheduled_ticks_after",s.scheduledTicks);action.addProperty("rendered_ticks_after",s.renderedTicks);
                action.addProperty("rendered_frames_after",s.renderedFrames);action.add("roles_after",roleSnapshots(s,client));
                synchronized(s){s.lastPhaseAction=s.phaseActions.size();s.phaseActions.add(action);}
                s.phaseActionPending=false;
            }
            JsonObject row=new JsonObject();row.addProperty("manager_tick_id",s.observedManagerTicks++);
            row.addProperty("sound_engine_tick_id",s.completedEngineTick);row.addProperty("clock_tick_index",s.armed?s.scheduledTicks:-1);
            row.addProperty("phase",phaseName(s));row.addProperty("published_catalog_identity_before",System.identityHashCode(CalibratedAssetResources.snapshot()));
            row.add("roles_before",roleSnapshots(s,client));synchronized(s){stamp(s,row);s.managerTicks.add(row);}s.activeManagerTick=row;
        }catch(Throwable error){failClock(s,error);}
    }

    /** RETURN observer and owner clock; RPM driver stimulus is applied here for the next genuine SoundEngine tick. */
    public static void afterManagerTick(Minecraft client) {
        Session s=current;if(s==null || !ManagerCatalogPublicationClientGameTest.requested())return;
        try {
            requireClientThread(client);Generation[] voices=discoverCurrent(s,client);
            JsonObject manager=s.activeManagerTick;
            if(manager!=null) {
                manager.addProperty("published_catalog_identity_after",System.identityHashCode(CalibratedAssetResources.snapshot()));
                manager.add("roles_after",roleSnapshots(s,client));synchronized(s){manager.addProperty("end_order",++s.order);manager.addProperty("returned_normally",true);}
                s.activeManagerTick=null;
            }
            if(!s.armed)return;
            if(s.phaseActionPending || manager==null || s.completedEngineTick<0 || s.completedEngineTick==s.lastScheduledEngineTick || s.activeEngineTick!=null)
                throw new IllegalStateException("catalog render tick lacks its fresh actual engine/manager/phase boundaries");
            int index=s.scheduledTicks;Phase p=PHASES.get(s.phaseIndex);JsonObject row=new JsonObject();
            row.addProperty("tick_index",index);row.addProperty("phase",p.name);row.addProperty("phase_tick_index",s.phaseTicks);
            row.addProperty("sound_engine_tick_id",s.completedEngineTick);row.addProperty("manager_tick_id",manager.get("manager_tick_id").getAsInt());
            row.addProperty("phase_action_index",s.lastPhaseAction);s.lastScheduledEngineTick=s.completedEngineTick;
            row.addProperty("expected_old_rpm",p.oldRpm);row.addProperty("expected_new_rpm",p.newRpm);
            row.addProperty("old_rpm",s.oldDrone.getAverageMotorRpm());row.addProperty("new_rpm",s.newDrone.getAverageMotorRpm());
            row.addProperty("client_game_time",client.level==null?-1:client.level.getGameTime());row.addProperty("client_paused",client.isPaused());
            row.addProperty("published_catalog_identity",System.identityHashCode(CalibratedAssetResources.snapshot()));
            row.addProperty("manager_set_count",activeSets().size());row.addProperty("client_handle_count",handles(client).size());
            row.add("roles",roleSnapshots(s,client));JsonArray envelopes=new JsonArray();
            for(int i=0;i<4;i++)envelopes.add(layerEnvelope(s,voices[i],i,client));row.add("envelopes_after_manager_tick",envelopes);
            synchronized(s){stamp(s,row);s.ticks.add(row);}
            CompletableFuture<Void> rendered=new CompletableFuture<>();s.lastRender=rendered;s.scheduledTicks++;
            try {((SoundEngineExecutorAccessor)engine(client)).fpvdrone$getExecutor().schedule(()->renderTick(s,row,voices,rendered));}
            catch(Throwable error){s.scheduledTicks--;rendered.complete(null);throw error;}
            if(++s.phaseTicks==p.ticks) {
                s.phaseTicks=0;
                if(++s.phaseIndex==PHASES.size())s.armed=false;
                else {Phase next=PHASES.get(s.phaseIndex);setRpm(s,client,next);s.phaseActionPending=true;phase(s,next.name);}
            }
        }catch(Throwable error){failClock(s,error);}
    }

    private static void setRpm(Session s,Minecraft client,Phase next) {
        requireClientThread(client);JsonObject row=new JsonObject();row.addProperty("next_phase",next.name);row.addProperty("clock_tick_index",s.scheduledTicks);
        row.addProperty("old_rpm_before",s.oldDrone.getAverageMotorRpm());row.addProperty("new_rpm_before",s.newDrone.getAverageMotorRpm());
        row.addProperty("requested_old_rpm",next.oldRpm);row.addProperty("requested_new_rpm",next.newRpm);
        row.addProperty("client_thread_verified",true);row.addProperty("boundary","PREVIOUS_MANAGER_RETURN_BEFORE_NEXT_ENGINE_HEAD");
        row.addProperty("start_order",nextObservationOrder(s));row.addProperty("thread",Thread.currentThread().getName());
        s.rpmSetter.accept((float)next.oldRpm,(float)next.newRpm);requireClientThread(client);
        row.addProperty("end_order",nextObservationOrder(s));row.addProperty("old_rpm_after",s.oldDrone.getAverageMotorRpm());
        row.addProperty("new_rpm_after",s.newDrone.getAverageMotorRpm());synchronized(s){s.rpmStimuli.add(row);}
    }

    public static void beforeManagerStop(Object set) {
        Session s=current;if(s==null)return;DroneEntity drone=(DroneEntity)field(set,"drone");String role=role(s,drone);if(role==null)return;
        try {
            JsonObject row=new JsonObject();row.addProperty("set_identity",System.identityHashCode(set));
            synchronized(s){row.addProperty("set_generation",setGeneration(s,set));stamp(s,row);}stopContext(s,drone,role,row);
            JsonArray layers=new JsonArray();for(String name:List.of("motor","propeller")) {
                Object value=field(set,name);
                if(value instanceof DroneLoopSoundInstance sound) {Generation g=observeSound(s,sound,"MANAGER_STOP_HEAD");JsonObject e=envelope(sound);joinSound(e,g);layers.add(e);}
                else {JsonObject e=new JsonObject();e.addProperty("role",role);e.addProperty("entity_id",drone.getId());
                    e.addProperty("layer",name.equals("motor")?"MOTOR":"PROPELLER");e.add("sound_generation",JsonNull.INSTANCE);
                    e.add("sound_identity",JsonNull.INSTANCE);e.addProperty("manager_layer_present",false);layers.add(e);}
            }
            row.add("layers",layers);synchronized(s){s.managerStops.add(row);}
        }catch(Throwable error){s.failure.compareAndSet(null,error);}
    }
    public static void beforeChannelStop(Object object) {
        Session s=current;if(s==null)return;Channel channel=(Channel)object;Generation found=null;
        synchronized(s){for(Generation g:s.generations)if(g.channel==channel){found=g;break;}}if(found==null)return;
        try {
            bindStreamOwner(s,found);JsonObject row=nativeSnapshot(channel);join(row,found);stopContext(s,found.drone,found.role,row);
            row.addProperty("rendered_frames_before_stop",s.renderedFrames);row.addProperty("rendered_ticks_before_stop",s.renderedTicks);
            row.addProperty("scheduled_client_ticks_at_stop",s.scheduledTicks);synchronized(s){stamp(s,row);s.channelStops.add(row);}
        }catch(Throwable error){s.failure.compareAndSet(null,error);}
    }
    private static void stopContext(Session s,DroneEntity drone,String role,JsonObject row) {
        row.addProperty("role",role);row.addProperty("entity_id",drone.getId());row.addProperty("entity_identity",System.identityHashCode(drone));
        row.addProperty("tick_index",s.scheduledTicks);row.addProperty("phase",phaseName(s));row.addProperty("clock_active",s.armed);
        row.addProperty("sound_engine_tick_id",s.currentEngineTick>=0?s.currentEngineTick:s.completedEngineTick);
        row.addProperty("fixture_cleanup",s.cleanup);row.addProperty("rpm",drone.getAverageMotorRpm());
        row.addProperty("entity_alive",drone.isAlive());row.addProperty("entity_removed",drone.isRemoved());row.addProperty("entity_silent",drone.isSilent());
        row.addProperty("world_identity",System.identityHashCode(drone.level()));
        row.addProperty("cause_classification",s.cleanup?"FIXTURE_CLEANUP":!drone.isAlive() || drone.isRemoved()?"INVALID_ENTITY":
                drone.isSilent()?"SILENT_ENTITY":drone.getAverageMotorRpm()==0?"RPM_ZERO_OBSERVED":"OTHER_OBSERVED_STOP");
        row.addProperty("cause_classification_is_semantic_oracle",false);
    }

    private static void renderTick(Session s,JsonObject row,Generation[] voices,CompletableFuture<Void> rendered) {
        try {
            MainOutputLibraryAccessor library=library();MainContextOutputCapture.requireCurrent(library.mainOutput$getDevice(),library.mainOutput$getContext());
            row.addProperty("owner_thread",Thread.currentThread().getName());row.addProperty("device",library.mainOutput$getDevice());row.addProperty("context",library.mainOutput$getContext());
            row.addProperty("generation",MainContextOutputCapture.generation(library.mainOutput$getDevice(),library.mainOutput$getContext()));
            JsonArray before=new JsonArray();for(int i=0;i<4;i++)before.add(nativeLayer(s,voices[i],i,row));row.add("native_before",before);
            ByteBuffer bytes=ByteBuffer.allocate(s.frames*4).order(ByteOrder.LITTLE_ENDIAN);
            for(int f=0;f<s.frames;f+=1024) {
                int count=Math.min(1024,s.frames-f);FloatBuffer samples=BufferUtils.createFloatBuffer(count);
                SOFTLoopback.alcRenderSamplesSOFT(library.mainOutput$getDevice(),samples,count);
                if(AL10.alGetError()!=0 || ALC10.alcGetError(library.mainOutput$getDevice())!=0)throw new IllegalStateException("actual catalog loopback render failed");
                while(samples.hasRemaining())bytes.putFloat(samples.get());
                for(Generation g:voices)if(g!=null && g.channel!=null) {
                    Channel c=g.channel;int source=((ChannelSourceAccessor)c).fpvdrone$getSource();
                    if(initialized(c) && AL10.alIsSource(source) && AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE)==AL10.AL_PLAYING) {
                        // Test-driver queue service only; lifecycle/scalar observers do not drive streams.
                        c.updateStream();if(AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED)<1)throw new IllegalStateException("catalog stream underrun");
                    }
                }
                if(AL10.alGetError()!=0)throw new IllegalStateException("catalog queue update failed");
            }
            byte[] pcm=bytes.array();row.addProperty("output_offset_bytes",s.output.size());row.addProperty("output_bytes",pcm.length);
            row.addProperty("output_sha256",MainContextOutputCapture.sha(pcm));s.output.writeBytes(pcm);s.renderedFrames+=s.frames;
            JsonArray after=new JsonArray();for(int i=0;i<4;i++)after.add(nativeLayer(s,voices[i],i,row));row.add("native_after",after);
        }catch(Throwable error){s.failure.compareAndSet(null,error);row.addProperty("failure",error.toString());}
        finally {s.renderedTicks++;rendered.complete(null);if(s.renderedTicks==TOTAL_TICKS)s.complete.complete(null);}
    }

    private static JsonObject nativeLayer(Session s,Generation g,int slot,JsonObject tick) {
        JsonObject identities=tick.getAsJsonArray("envelopes_after_manager_tick").get(slot).getAsJsonObject();
        if(g==null)return identities.deepCopy();bindStreamOwner(s,g);JsonObject row;
        if(g.channel==null){row=new JsonObject();row.addProperty("channel_pending",true);row.addProperty("source_valid",false);}
        else {row=nativeSnapshot(g.channel);row.addProperty("channel_pending",false);}
        join(row,g);row.addProperty("manager_layer_present",true);row.addProperty("stream_pending",g.stream==null);
        row.add("world_entity_identity",identities.get("world_entity_identity").deepCopy());return row;
    }
    private static JsonObject layerEnvelope(Session s,Generation g,int slot,Minecraft client) {
        DroneEntity drone=slot<2?s.oldDrone:s.newDrone;JsonObject row;
        if(g==null) {
            row=new JsonObject();row.addProperty("role",slot<2?"OLD":"NEW");row.addProperty("layer",slot%2==0?"MOTOR":"PROPELLER");
            row.addProperty("entity_id",drone.getId());row.addProperty("entity_identity",System.identityHashCode(drone));row.addProperty("manager_layer_present",false);
            for(String name:List.of("sound_generation","set_generation","sound_identity","set_identity","channel_allocation_identity","stream_identity"))row.add(name,JsonNull.INSTANCE);
            row.addProperty("channel_pending",false);row.addProperty("stream_pending",false);row.addProperty("source_valid",false);
        }else {row=envelope(g.sound);join(row,g);row.addProperty("manager_layer_present",true);
            row.addProperty("channel_pending",g.channel==null);row.addProperty("stream_pending",g.stream==null);}
        row.addProperty("world_entity_identity",client.level==null?0:System.identityHashCode(client.level.getEntity(drone.getId())));
        row.addProperty("expected_entity_identity",System.identityHashCode(drone));return row;
    }
    private static void joinSound(JsonObject row,Generation g) {
        row.addProperty("sound_generation",g.generation);row.addProperty("set_generation",g.setGeneration);row.addProperty("set_identity",System.identityHashCode(g.set));
        row.addProperty("sound_identity",System.identityHashCode(g.sound));row.addProperty("layer",g.layer);row.addProperty("role",g.role);
        row.addProperty("entity_id",g.drone.getId());row.addProperty("entity_identity",System.identityHashCode(g.drone));
        row.addProperty("catalog_identity",System.identityHashCode(field(g.sound,"assetCatalog")));
        row.addProperty("asset_identity",System.identityHashCode(field(g.sound,"calibratedAsset")));
    }
    private static void join(JsonObject row,Generation g) {
        joinSound(row,g);if(g.channel==null)row.add("channel_allocation_identity",JsonNull.INSTANCE);
        else row.addProperty("channel_allocation_identity",WetChannelLifecycleCapture.identity(g.channel));
        if(g.stream==null)row.add("stream_identity",JsonNull.INSTANCE);else row.addProperty("stream_identity",System.identityHashCode(g.stream));
    }

    private static Generation[] discoverCurrent(Session s,Minecraft client) {
        Generation[] voices=new Generation[4];
        for(int roleIndex=0;roleIndex<2;roleIndex++) {
            DroneEntity drone=roleIndex==0?s.oldDrone:s.newDrone;Object set=activeSets().get(drone.getId());if(set==null)continue;
            if(field(set,"drone")!=drone)throw new IllegalStateException("manager role identity differs from actual fixture entity");
            int layer=0;for(String name:List.of("motor","propeller")) {
                Object value=field(set,name);
                if(value instanceof DroneLoopSoundInstance sound) {
                    Generation g=observeSound(s,sound,"MANAGER_RETURN");voices[roleIndex*2+layer]=g;
                    ChannelAccess.ChannelHandle handle=handles(client).get(sound);Channel c=handle==null?null:((ChannelHandleChannelAccessor)handle).fpvdrone$getChannel();
                    if(c!=null) {
                        if(g.channel!=null && g.channel!=c)throw new IllegalStateException("actual sound changed its Channel identity");
                        g.channel=c;synchronized(s){g.binding.addProperty("channel_identity",System.identityHashCode(c));
                            g.binding.addProperty("channel_allocation_identity",WetChannelLifecycleCapture.identity(c));}
                        Object stream=((MainOutputChannelAccessor)c).mainOutput$getStream();
                        if(stream instanceof CalibratedAssetAudioStream assetStream)bindStreamIdentity(s,g,assetStream);
                    }
                }
                layer++;
            }
        }
        return voices;
    }
    private static Generation observeSound(Session s,DroneLoopSoundInstance sound,String origin) {
        synchronized(s) {
            Generation previous=s.bySound.get(sound);if(previous!=null)return previous;DroneEntity drone=(DroneEntity)field(sound,"drone");
            String role=role(s,drone);Object set=activeSets().get(drone.getId());
            if(role==null || set==null || field(set,"drone")!=drone)throw new IllegalStateException("observed sound has no actual role manager set");
            if(s.generations.size()>=128)throw new IllegalStateException("bounded catalog sound generations exhausted");
            Generation g=new Generation(s.generations.size(),setGeneration(s,set),set,sound,drone,role);
            joinSound(g.binding,g);g.binding.addProperty("generation",g.generation);g.binding.addProperty("first_observation",origin);
            g.binding.addProperty("first_observation_clock_tick",s.armed?s.scheduledTicks:-1);g.binding.addProperty("first_observation_phase",phaseName(s));
            g.binding.add("initial_observed_envelope",rawEnvelope(sound));s.bySound.put(sound,g);s.generations.add(g);return g;
        }
    }
    private static int setGeneration(Session s,Object set) {
        Integer existing=s.bySet.get(set);if(existing!=null)return existing;int generation=s.bySet.size();s.bySet.put(set,generation);return generation;
    }
    private static void bindStreamOwner(Session s,Generation g) {
        if(g.channel==null)return;Object value=((MainOutputChannelAccessor)g.channel).mainOutput$getStream();if(value==null)return;
        if(!(value instanceof CalibratedAssetAudioStream stream))throw new IllegalStateException("normal catalog voice did not attach calibrated asset stream");
        bindStreamIdentity(s,g,stream);
        boolean observed;synchronized(s){observed=g.binding.has("native_first_bound");}
        if(!observed) {
            JsonObject nativeState=nativeSnapshot(g.channel);synchronized(s){
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
        g.stream=stream;synchronized(s){g.binding.addProperty("stream_identity",System.identityHashCode(stream));g.binding.addProperty("stream_class",stream.getClass().getName());}
    }

    private static JsonArray roleSnapshots(Session s,Minecraft client) {
        JsonArray rows=new JsonArray();for(DroneEntity drone:List.of(s.oldDrone,s.newDrone)) {
            JsonObject row=new JsonObject();row.addProperty("role",role(s,drone));row.addProperty("entity_id",drone.getId());
            row.addProperty("expected_entity_identity",System.identityHashCode(drone));Object world=client.level==null?null:client.level.getEntity(drone.getId());
            row.addProperty("world_entity_identity",System.identityHashCode(world));row.addProperty("world_contains_expected_entity",world==drone);
            row.addProperty("rpm",drone.getAverageMotorRpm());row.addProperty("entity_alive",drone.isAlive());row.addProperty("entity_removed",drone.isRemoved());
            row.addProperty("entity_silent",drone.isSilent());Object set=activeSets().get(drone.getId());row.addProperty("manager_contains_entity",set!=null);
            row.addProperty("manager_set_identity",System.identityHashCode(set));row.addProperty("manager_entity_identity",set==null?0:System.identityHashCode(field(set,"drone")));
            if(set==null)row.add("set_generation",JsonNull.INSTANCE);else synchronized(s){row.addProperty("set_generation",setGeneration(s,set));}
            rows.add(row);
        }
        return rows;
    }
    static JsonObject snapshot(Session s) {
        if(!s.complete.isDone() || !s.lastRender.isDone() || s.renderedTicks!=s.scheduledTicks)throw new IllegalStateException("partial catalog publication clock snapshot");
        synchronized(s) {
            JsonObject row=new JsonObject();JsonArray phases=new JsonArray();for(Phase p:PHASES){JsonObject r=new JsonObject();r.addProperty("name",p.name);
                r.addProperty("old_rpm",p.oldRpm);r.addProperty("new_rpm",p.newRpm);r.addProperty("ticks",p.ticks);phases.add(r);}
            row.add("phases",phases);row.addProperty("render_step_frames",s.frames);row.addProperty("scheduled_ticks",s.scheduledTicks);
            row.addProperty("rendered_ticks",s.renderedTicks);row.addProperty("rendered_frames",s.renderedFrames);
            row.add("ticks",s.ticks.deepCopy());row.add("sound_tick_observations",s.soundTicks.deepCopy());row.add("initialization_observations",s.initializations.deepCopy());
            row.add("sound_engine_tick_observations",s.engineTicks.deepCopy());row.add("manager_tick_observations",s.managerTicks.deepCopy());
            row.add("phase_action_observations",s.phaseActions.deepCopy());row.add("rpm_stimulus_observations",s.rpmStimuli.deepCopy());
            row.add("manager_stop_observations",s.managerStops.deepCopy());row.add("native_channel_stop_observations",s.channelStops.deepCopy());
            JsonArray bindings=new JsonArray();for(Generation g:s.generations)bindings.add(g.binding.deepCopy());row.add("generations",bindings);
            row.addProperty("catalog_publication_semantics_passed",false);return row;
        }
    }
    static JsonObject envelope(DroneLoopSoundInstance sound) {
        JsonObject row=rawEnvelope(sound);row.addProperty("effective_volume",sound.getVolume());row.addProperty("pitch",sound.getPitch());return row;
    }
    private static JsonObject rawEnvelope(DroneLoopSoundInstance sound) {
        JsonObject row=new JsonObject();row.addProperty("sound_identity",System.identityHashCode(sound));row.addProperty("layer",sound.layer().name());
        row.addProperty("raw_envelope",(Float)field(sound,"volume"));row.addProperty("raw_pitch",(Float)field(sound,"pitch"));
        row.addProperty("coefficient",(Double)field(sound,"assetAmplitude"));row.addProperty("asset_operating_point_valid",(Boolean)field(sound,"assetOperatingPointValid"));
        row.addProperty("stopped",sound.isStopped());row.addProperty("procedural_backend",sound.usesProceduralAudio());return row;
    }
    /** Frozen D178 getter implementation remains a read-only dependency, including retired AL integer alias handling. */
    static JsonObject nativeSnapshot(Channel channel){return ManagerAssetResumptionCapture.nativeSnapshot(channel);}
    static Object field(Object target,String name){return ManagerAssetResumptionCapture.field(target,name);}
    static Map<Integer,Object> activeSets(){return ManagerAssetResumptionCapture.activeSets();}
    private static String role(Session s,DroneEntity drone){return drone==s.oldDrone?"OLD":drone==s.newDrone?"NEW":null;}
    private static void stamp(Session s,JsonObject row){row.addProperty("order",++s.order);row.addProperty("thread",Thread.currentThread().getName());}
    private static String phaseName(Session s){return s.cleanup?"CLEANUP":!s.clockStarted || s.startPending?"ADMISSION":s.phaseIndex>=PHASES.size()?"POST_CLOCK":PHASES.get(s.phaseIndex).name;}
    private static void phase(Session s,String name){AssetNativeCapture.phase(s.name+":"+name);CalibratedGainCapture.phase(s.name+":"+name);}
    private static void failClock(Session s,Throwable error){s.failure.compareAndSet(null,error);s.armed=false;s.startPending=false;s.phaseActionPending=false;s.complete.complete(null);}
    private static void requireClientThread(Minecraft client){if(!client.isSameThread())throw new IllegalStateException("catalog phase/RPM action is not on the actual client thread");}
    private static boolean initialized(Channel channel){return ((AtomicBoolean)field(channel,"initialized")).get();}
    private static Map<SoundInstance,ChannelAccess.ChannelHandle> handles(Minecraft client){return ((SoundEngineChannelsAccessor)engine(client)).fpvdrone$getInstanceToChannel();}
    private static SoundEngine engine(Minecraft client){return ((SoundManagerEngineAccessor)client.getSoundManager()).fpvdrone$getSoundEngine();}
    private static MainOutputLibraryAccessor library(){return (MainOutputLibraryAccessor)((SoundEngineMainOutputAccessor)engine(Minecraft.getInstance())).mainOutput$getLibrary();}
}
