package com.tenicana.dronecraft.client.sound;

import com.google.gson.*;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import com.tenicana.dronecraft.client.mixin.*;
import com.tenicana.dronecraft.entity.DroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Dedicated test stimulus and observations. It never suppresses a production consumer or tick. */
public final class ManagerAssetRetiredDeliveryCapture {
    public static final String PROPERTY = "fpvdrone.acoustics.assetRetiredDeliveryProtocol";
    private static volatile Session current;
    private static final JsonArray libraryEvents = new JsonArray();
    private static long libraryOrder;
    private static int libraryGeneration;
    private ManagerAssetRetiredDeliveryCapture() { }

    public static boolean requested() { return System.getProperty(PROPERTY) != null; }

    static final class Session {
        final int epoch, heldEntityId, newcomerEntityId, minimumWarmTicks, maximumReadBytes, maximumReadBatches;
        final Set<Integer> entityIds;
        final VerifiedAssetPcm asset;
        final CalibratedAssetResources.Catalog catalog;
        final List<Voice> voices = new ArrayList<>();
        final IdentityHashMap<DroneLoopSoundInstance,Voice> bySound = new IdentityHashMap<>();
        final IdentityHashMap<AudioStream,Voice> byStream = new IdentityHashMap<>();
        final JsonArray events = new JsonArray(), soundTicks = new JsonArray(), managerTicks = new JsonArray(), engineTicks = new JsonArray();
        final JsonObject stages = new JsonObject();
        final CompletableFuture<Void> primaryComplete = new CompletableFuture<>();
        long order, engineTickId, managerTickId, releaseEngineTickId;
        boolean paused, inGameSoundTick, evictionRequested, evictionObserved, released, cleanup;
        String failure;
        JsonObject warmClient, evictionClient;
        Session(int epoch,int held,int newcomer,Collection<Integer> ids,int warm,int maxBytes,int maxBatches,
                VerifiedAssetPcm asset,CalibratedAssetResources.Catalog catalog) {
            this.epoch=epoch;heldEntityId=held;newcomerEntityId=newcomer;entityIds=Set.copyOf(ids);
            minimumWarmTicks=warm;maximumReadBytes=maxBytes;maximumReadBatches=maxBatches;
            this.asset=asset;this.catalog=catalog;
        }
    }

    private static final class Voice {
        final int generation;
        final DroneLoopSoundInstance sound;
        final DroneEntity drone;
        final JsonObject constructor;
        final JsonArray reads = new JsonArray();
        Object set;
        ChannelAccess.ChannelHandle handle;
        Channel channel;
        CalibratedAssetAudioStream stream;
        CompletableFuture<AudioStream> originalFuture, heldFuture;
        boolean held, repeat, closed;
        long soundTickCount, cumulativeReadBytes;
        JsonObject lastTick, pendingTick;
        Voice(int generation,DroneLoopSoundInstance sound,DroneEntity drone,JsonObject constructor) {
            this.generation=generation;this.sound=sound;this.drone=drone;this.constructor=constructor;
        }
    }

    public static final class ReadToken {
        private final Session session;
        private final Voice voice;
        private final Object cursor;
        private final JsonObject row;
        private ReadToken(Session session,Voice voice,Object cursor,JsonObject row) {
            this.session=session;this.voice=voice;this.cursor=cursor;this.row=row;
        }
    }

    static synchronized Session beginEpoch(int epoch,int held,int newcomer,Collection<Integer> ids,
            int warm,int maxBytes,int maxBatches,VerifiedAssetPcm asset,CalibratedAssetResources.Catalog catalog) {
        requireClient();
        if(current!=null)throw new IllegalStateException("previous late-delivery session not drained");
        Session s=new Session(epoch,held,newcomer,ids,warm,maxBytes,maxBatches,asset,catalog);current=s;
        event(s,"begin_epoch");return s;
    }

    public static synchronized void constructed(Object object) {
        Session s=current;if(s==null || !requested() || !(object instanceof DroneLoopSoundInstance sound))return;
        DroneEntity drone=(DroneEntity)field(sound,"drone");if(!s.entityIds.contains(drone.getId()))return;
        requireClient();
        if(s.bySound.containsKey(sound))throw new IllegalStateException("duplicate genuine constructor observation");
        JsonObject raw=rawSound(sound);raw.addProperty("pre_resolution_raw_fields_only",true);stamp(s,raw);
        Voice v=new Voice(s.voices.size(),sound,drone,raw);s.voices.add(v);s.bySound.put(sound,v);
        v.set=activeSets().get(drone.getId());event(s,"constructed",v);
    }

    public static synchronized CompletableFuture<AudioStream> streamReturned(Object object,
            CompletableFuture<AudioStream> original,boolean repeat) {
        Session s=current;if(s==null || !requested() || !(object instanceof DroneLoopSoundInstance sound))return original;
        Voice v=s.bySound.get(sound);if(v==null)return original;requireClient();
        if(v.originalFuture!=null)throw new IllegalStateException("duplicate stream request for one sound");
        if(!original.isDone() || original.isCompletedExceptionally())throw new IllegalStateException("normal calibrated future did not complete immediately");
        AudioStream actual=original.join();if(!(actual instanceof CalibratedAssetAudioStream stream))throw new IllegalStateException("normal manager did not create calibrated asset stream");
        v.originalFuture=original;v.stream=stream;v.repeat=repeat;v.held=v.drone.getId()==s.heldEntityId && !s.cleanup;
        s.byStream.put(stream,v);bindClient(s,v);
        if(v.held)v.heldFuture=new CompletableFuture<>();
        JsonObject e=event(s,"stream_future_return",v);e.addProperty("original_future_identity",id(original));
        e.addProperty("original_future_done",original.isDone());e.addProperty("original_future_exceptional",original.isCompletedExceptionally());
        e.addProperty("returned_future_identity",id(v.held?v.heldFuture:original));e.addProperty("returned_future_done",v.held?v.heldFuture.isDone():original.isDone());
        e.addProperty("injected_delivery_delay",v.held);e.addProperty("repeat_instantly",repeat);
        return v.held?v.heldFuture:original;
    }

    public static synchronized void beforeSoundTick(Object object) {
        Session s=current;if(s==null || !(object instanceof DroneLoopSoundInstance sound))return;
        Voice v=s.bySound.get(sound);if(v==null)return;requireClient();
        JsonObject r=event(s,"sound_tick_head",v);r.add("before",rawSound(sound));r.addProperty("sound_engine_tick_id",s.engineTickId);
        r.addProperty("within_tickInGameSound",s.inGameSoundTick);v.pendingTick=r.deepCopy();
    }

    public static synchronized void afterSoundTick(Object object) {
        Session s=current;if(s==null || !(object instanceof DroneLoopSoundInstance sound))return;
        Voice v=s.bySound.get(sound);if(v==null)return;requireClient();
        if(v.pendingTick==null)throw new IllegalStateException("genuine sound tick RETURN without HEAD");
        JsonObject r=v.pendingTick;v.pendingTick=null;r.add("after",rawSound(sound));r.addProperty("returned_normally",true);
        r.addProperty("end_order",++s.order);r.addProperty("sound_tick_count",++v.soundTickCount);s.soundTicks.add(r);v.lastTick=r;
    }

    public static synchronized void beforeEngineTick(boolean paused) {
        Session s=current;if(s==null)return;requireClient();s.paused=paused;s.engineTickId++;
        JsonObject r=event(s,"engine_tick_head");r.addProperty("sound_engine_tick_id",s.engineTickId);r.addProperty("paused",paused);s.engineTicks.add(r.deepCopy());
    }

    public static synchronized void beforeGameSoundTick() {
        Session s=current;if(s==null)return;requireClient();s.inGameSoundTick=true;
        JsonObject r=event(s,"tickInGameSound_head");r.addProperty("sound_engine_tick_id",s.engineTickId);
    }

    public static synchronized void afterGameSoundTick() {
        Session s=current;if(s==null)return;requireClient();s.inGameSoundTick=false;
        JsonObject end=event(s,"tickInGameSound_return");end.addProperty("sound_engine_tick_id",s.engineTickId);
        if(s.cleanup || !s.evictionObserved || s.released)return;
        s.released=true;s.releaseEngineTickId=s.engineTickId;
        JsonObject frozen=clientSnapshot(s);frozen.addProperty("snapshot_point","tickInGameSound_RETURN_before_future_completion");
        s.stages.add("release_client",frozen.deepCopy());
        List<Voice> held=s.voices.stream().filter(v->v.held).toList();
        if(held.size()!=2)throw new IllegalStateException("late experiment did not hold exactly two real streams");
        for(Voice v:held) {
            JsonObject begin=event(s,"held_future_complete_head",v);begin.addProperty("held_future_done",v.heldFuture.isDone());begin.addProperty("sound_engine_tick_id",s.engineTickId);
            boolean completed=v.heldFuture.complete(v.stream);
            JsonObject done=event(s,"held_future_complete_return",v);done.addProperty("completion_changed_future",completed);done.addProperty("held_future_done",v.heldFuture.isDone());done.addProperty("sound_engine_tick_id",s.engineTickId);
            if(!completed)throw new IllegalStateException("held future completed before declared injection boundary");
        }
        // CompletableFuture.thenAccept submits the untouched production attach/play consumer first.
        // This observer is then submitted before the outer tick submits vanilla scheduleTick.
        scheduleStage(s,"late_consumer_before_cleanup",s.engineTickId,frozen);
    }

    public static synchronized void afterEngineTick() {
        Session s=current;if(s==null)return;requireClient();
        JsonObject r=event(s,"engine_tick_return");r.addProperty("sound_engine_tick_id",s.engineTickId);r.addProperty("paused",s.paused);s.engineTicks.add(r.deepCopy());
        if(s.cleanup || !s.released || s.paused)return;
        if(s.engineTickId==s.releaseEngineTickId)scheduleStage(s,"same_tick_cleanup",s.engineTickId,clientSnapshot(s));
        else if(s.engineTickId==s.releaseEngineTickId+1)scheduleStage(s,"next_tick_cleanup",s.engineTickId,clientSnapshot(s));
    }

    public static synchronized void vanillaScheduleTick(String kind) {
        Session s=current;if(s==null)return;requireClient();
        JsonObject r=event(s,kind);r.addProperty("sound_engine_tick_id",s.engineTickId);r.addProperty("paused",s.paused);
    }

    public static synchronized void beforeManagerTick() {
        Session s=current;if(s==null)return;requireClient();s.managerTickId++;
        JsonObject r=event(s,"manager_tick_head");r.addProperty("manager_tick_id",s.managerTickId);
    }

    public static synchronized void afterManagerTick() {
        Session s=current;if(s==null)return;requireClient();
        for(Voice v:s.voices)bindClient(s,v);
        JsonObject snapshot=clientSnapshot(s);snapshot.addProperty("manager_tick_id",s.managerTickId);snapshot.addProperty("sound_engine_tick_id",s.engineTickId);stamp(s,snapshot);s.managerTicks.add(snapshot);
        if(!s.cleanup && s.evictionRequested && !s.evictionObserved
                && !activeSets().containsKey(s.heldEntityId) && activeSets().containsKey(s.newcomerEntityId)) {
            s.evictionObserved=true;s.evictionClient=snapshot.deepCopy();
            JsonObject e=event(s,"manager_eviction_observed");e.addProperty("manager_tick_id",s.managerTickId);e.addProperty("sound_engine_tick_id",s.engineTickId);
            scheduleStage(s,"eviction_after_owner",s.engineTickId,snapshot.deepCopy());
        }
    }

    static synchronized boolean warmReady(Session s) {
        requireClient();check(s);if(s.failure!=null)throw new IllegalStateException(s.failure);
        if(s.voices.size()!=12 || activeSets().size()!=6)return false;
        for(Voice v:s.voices) {
            bindClient(s,v);
            if(v.stream==null || v.channel==null || v.soundTickCount<s.minimumWarmTicks || !(v.sound.getVolume()>0)
                    || v.sound.isStopped() || field(v.sound,"assetCatalog")!=s.catalog || field(v.sound,"calibratedAsset")!=s.asset)return false;
        }
        s.warmClient=clientSnapshot(s);return true;
    }

    static synchronized CompletableFuture<JsonObject> warmNative(Session s) {
        requireClient();check(s);CompletableFuture<JsonObject> done=new CompletableFuture<>();JsonObject frozen=clientSnapshot(s);long tick=s.engineTickId;
        JsonObject submitted=event(s,"owner_observation_submit");submitted.addProperty("stage","warm_native");long order=submitted.get("order").getAsLong();
        ((SoundEngineExecutorAccessor)engine()).fpvdrone$getExecutor().schedule(()->{
            try {synchronized(ManagerAssetRetiredDeliveryCapture.class){JsonObject r=nativeStage(s,"warm_native",tick,frozen,order);s.stages.add("warm_native",r);done.complete(r);}}
            catch(Throwable error){fail(s,error);done.completeExceptionally(error);}
        });return done;
    }

    static synchronized void armEviction(Session s) {requireClient();check(s);if(s.warmClient==null)throw new IllegalStateException("eviction before genuine warm ticks");s.evictionRequested=true;event(s,"eviction_stimulus_armed");}

    private static void scheduleStage(Session s,String stage,long tick,JsonObject client) {
        if(s.stages.has(stage))throw new IllegalStateException("duplicate native stage "+stage);
        JsonObject submit=event(s,"owner_observation_submit");submit.addProperty("stage",stage);submit.addProperty("sound_engine_tick_id",tick);long order=submit.get("order").getAsLong();
        ((SoundEngineExecutorAccessor)engine()).fpvdrone$getExecutor().schedule(()->{
            try {synchronized(ManagerAssetRetiredDeliveryCapture.class){JsonObject r=nativeStage(s,stage,tick,client,order);s.stages.add(stage,r);if(stage.equals("next_tick_cleanup"))s.primaryComplete.complete(null);}}
            catch(Throwable error){fail(s,error);}
        });
    }

    private static synchronized void fail(Session s,Throwable error) {s.failure=error.toString();event(s,"capture_failure").addProperty("failure",s.failure);s.primaryComplete.completeExceptionally(error);}

    public static synchronized void channelEvent(String kind,Channel channel,AudioStream attaching) {
        Session s=current;if(s==null)return;
        Voice v=attaching==null?findChannel(s,channel):s.byStream.get(attaching);if(v==null)return;
        boolean owner=((SoundEngineExecutorAccessor)engine()).fpvdrone$getExecutor().isSameThread();
        if(!owner && !s.cleanup)throw new IllegalStateException("primary channel consumer off actual sound owner");
        if(attaching!=null){if(v.channel!=null && v.channel!=channel)throw new IllegalStateException("stream attached to a different allocated channel");v.channel=channel;}
        JsonObject e=event(s,kind,v);e.addProperty("channel_identity",id(channel));e.addProperty("stream_identity",id(v.stream));
        e.addProperty("actual_sound_executor_thread",owner);
        if(owner)e.add("native",nativeChannel(channel));
        else {JsonObject lifecycle=new JsonObject();lifecycle.addProperty("native_query_skipped_non_owner_lifecycle",true);lifecycle.addProperty("initialized",((AtomicBoolean)field(channel,"initialized")).get());lifecycle.addProperty("source",((ChannelSourceAccessor)channel).fpvdrone$getSource());e.add("native",lifecycle);}
        e.addProperty("held_future_done",v.heldFuture==null || v.heldFuture.isDone());
        e.addProperty("release_sound_engine_tick_id",s.releaseEngineTickId);
    }

    public static synchronized ReadToken beforeCursorRead(Object object,Object cursor,int requested,double amplitude) {
        Session s=current;if(s==null || !(object instanceof AudioStream stream))return null;
        Voice v=s.byStream.get(stream);if(v==null)return null;requireOwner();
        JsonObject r=event(s,"asset_read_head",v);r.addProperty("requested_bytes",requested);r.addProperty("coefficient",amplitude);
        r.addProperty("cursor_index_before",(Integer)field(cursor,"index"));r.addProperty("cursor_closed_before",(Boolean)field(cursor,"closed"));
        r.addProperty("cumulative_offset_bytes",v.cumulativeReadBytes);return new ReadToken(s,v,cursor,r);
    }

    public static synchronized void afterCursorRead(ReadToken token,ByteBuffer returned,Throwable failure) {
        if(token==null)return;Session s=token.session;Voice v=token.voice;JsonObject r=token.row;
        if(failure!=null){r.addProperty("failure",failure.toString());r.addProperty("returned_normally",false);r.addProperty("end_order",++s.order);v.reads.add(r);return;}
        byte[] data=new byte[returned.remaining()];returned.duplicate().get(data);
        if(v.reads.size()>=s.maximumReadBatches || v.cumulativeReadBytes+data.length>s.maximumReadBytes)throw new IllegalStateException("bounded late asset read trace exhausted");
        r.addProperty("returned_normally",true);r.addProperty("returned_bytes",data.length);r.addProperty("pcm_sha256",sha(data));
        r.addProperty("cursor_index_after",(Integer)field(token.cursor,"index"));r.addProperty("cursor_closed_after",(Boolean)field(token.cursor,"closed"));
        int peak=0;var samples=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();while(samples.hasRemaining())peak=Math.max(peak,Math.abs((int)samples.get()));r.addProperty("maximum_absolute_s16",peak);
        v.cumulativeReadBytes+=data.length;r.addProperty("cumulative_end_bytes",v.cumulativeReadBytes);r.addProperty("end_order",++s.order);v.reads.add(r);
    }

    public static synchronized void streamClose(String kind,Object object) {
        Session s=current;if(s==null || !(object instanceof AudioStream stream))return;Voice v=s.byStream.get(stream);if(v==null)return;
        boolean owner=((SoundEngineExecutorAccessor)engine()).fpvdrone$getExecutor().isSameThread();
        // Calibrated retirement closes CPU PCM under its lifecycle gate; no AL operation occurs here.
        JsonObject e=event(s,kind,v);e.addProperty("actual_sound_executor_thread",owner);e.addProperty("stream_closed",(Boolean)field(stream,"closed"));e.addProperty("cursor_closed",(Boolean)field(field(stream,"cursor"),"closed"));e.addProperty("stream_close_cpu_only",true);e.addProperty("actual_client_thread",Minecraft.getInstance().isSameThread());
        if(kind.equals("stream_close_return"))v.closed=(Boolean)field(stream,"closed");
    }

    public static synchronized void libraryEvent(String kind,Object object) {
        if(!requested() || !(object instanceof Library library))return;
        var accessor=(MainOutputLibraryAccessor)library;JsonObject r=new JsonObject();r.addProperty("order",++libraryOrder);r.addProperty("kind",kind);r.addProperty("thread",Thread.currentThread().getName());
        if(kind.equals("library_init_return"))libraryGeneration++;
        r.addProperty("generation",libraryGeneration);r.addProperty("device",accessor.mainOutput$getDevice());r.addProperty("context",accessor.mainOutput$getContext());r.addProperty("library_identity",id(library));libraryEvents.add(r);
    }

    static synchronized JsonObject clientSnapshot(Session s) {
        requireClient();JsonObject r=new JsonObject();r.addProperty("actual_client_thread",Minecraft.getInstance().isSameThread());r.addProperty("thread",Thread.currentThread().getName());
        r.addProperty("sound_engine_tick_id",s.engineTickId);r.addProperty("manager_tick_id",s.managerTickId);r.addProperty("manager_set_count",activeSets().size());
        JsonArray sets=new JsonArray();for(var entry:activeSets().entrySet()){JsonObject x=new JsonObject();x.addProperty("entity_id",entry.getKey());x.addProperty("set_identity",id(entry.getValue()));x.addProperty("entity_identity",id(field(entry.getValue(),"drone")));sets.add(x);}r.add("manager_sets",sets);
        JsonArray voices=new JsonArray();for(Voice v:s.voices){bindClient(s,v);JsonObject x=binding(v);x.add("sound",resolvedSound(v.sound));x.addProperty("sound_tick_count",v.soundTickCount);x.add("last_actual_sound_tick",v.lastTick==null?JsonNull.INSTANCE:v.lastTick.deepCopy());Object actual=activeSets().get(v.drone.getId());x.addProperty("manager_contains_original_set",actual==v.set);x.addProperty("world_contains_expected_entity",Minecraft.getInstance().level!=null && Minecraft.getInstance().level.getEntity(v.drone.getId())==v.drone);voices.add(x);}r.add("voices",voices);
        r.addProperty("options_master",Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER));r.addProperty("options_neutral",Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.NEUTRAL));
        r.addProperty("gain_by_source_neutral",((SoundEngineMainOutputAccessor)engine()).mainOutput$getGains().getFloat(SoundSource.NEUTRAL));return r;
    }

    private static JsonObject nativeStage(Session s,String stage,long tick,JsonObject client,long submission) {
        requireOwner();JsonObject r=event(s,"owner_observation_execute");r.addProperty("stage",stage);r.addProperty("submission_order",submission);r.addProperty("sound_engine_tick_id",tick);r.add("client_at_submission",client.deepCopy());
        identity(r);r.addProperty("library_debug",((SoundEngineMainOutputAccessor)engine()).mainOutput$getLibrary().getDebugString());
        JsonArray voices=new JsonArray();int open=0,playing=0,positive=0;
        for(Voice v:s.voices){JsonObject x=binding(v);x.addProperty("stream_closed",v.stream!=null && (Boolean)field(v.stream,"closed"));x.addProperty("cursor_closed",v.stream!=null && (Boolean)field(field(v.stream,"cursor"),"closed"));x.addProperty("recorded_read_bytes",v.cumulativeReadBytes);JsonObject n=v.channel==null?missingNative():nativeChannel(v.channel);x.add("native",n);voices.add(x);}
        r.add("voices",voices);JsonArray actualChannels=new JsonArray();Object access=field(engine(),"channelAccess");
        @SuppressWarnings("unchecked") Set<ChannelAccess.ChannelHandle> handles=(Set<ChannelAccess.ChannelHandle>)field(access,"channels");
        for(ChannelAccess.ChannelHandle h:handles){Channel c=((ChannelHandleChannelAccessor)h).fpvdrone$getChannel();if(c==null)continue;AudioStream attached=((MainOutputChannelAccessor)c).mainOutput$getStream();JsonObject x=nativeChannel(c);x.addProperty("handle_identity",id(h));x.addProperty("attached_stream_identity",id(attached));x.addProperty("attached_calibrated_asset_stream",attached instanceof CalibratedAssetAudioStream);Voice v=findChannel(s,c);x.addProperty("known_voice_generation",v==null?-1:v.generation);actualChannels.add(x);
            if(attached instanceof CalibratedAssetAudioStream){open++;if(x.get("source_valid").getAsBoolean() && x.get("source_state").getAsInt()==AL10.AL_PLAYING){playing++;if(x.get("gain").getAsFloat()>0 && x.get("queued").getAsInt()>0)positive++;}}
        }
        r.add("actual_channel_access_handles",actualChannels);r.addProperty("actual_channel_handle_count",handles.size());r.addProperty("open_attached_calibrated_streams",open);r.addProperty("playing_calibrated_streams",playing);r.addProperty("positive_gain_queued_playing_calibrated_streams",positive);
        r.addProperty("al_error",AL10.alGetError());r.addProperty("alc_error",ALC10.alcGetError(library().mainOutput$getDevice()));
        if(r.get("al_error").getAsInt()!=0 || r.get("alc_error").getAsInt()!=0)throw new IllegalStateException("native observation error");return r;
    }

    private static JsonObject nativeChannel(Channel channel) {
        requireOwner();int source=((ChannelSourceAccessor)channel).fpvdrone$getSource();boolean initialized=((AtomicBoolean)field(channel,"initialized")).get(),exists=AL10.alIsSource(source);
        JsonObject n=new JsonObject();n.addProperty("channel_identity",id(channel));n.addProperty("source",source);n.addProperty("initialized",initialized);n.addProperty("native_source_exists",exists);n.addProperty("source_valid",initialized&&exists);
        n.addProperty("attached_stream_identity",id(((MainOutputChannelAccessor)channel).mainOutput$getStream()));
        if(initialized&&exists){n.addProperty("source_state",AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE));n.addProperty("gain",AL10.alGetSourcef(source,AL10.AL_GAIN));n.addProperty("pitch",AL10.alGetSourcef(source,AL10.AL_PITCH));n.addProperty("queued",AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED));n.addProperty("processed",AL10.alGetSourcei(source,AL10.AL_BUFFERS_PROCESSED));n.addProperty("sample_offset",AL10.alGetSourcei(source,AL11.AL_SAMPLE_OFFSET));n.addProperty("source_relative",AL10.alGetSourcei(source,AL10.AL_SOURCE_RELATIVE));n.addProperty("source_model",AL10.alGetSourcei(source,AL10.AL_DISTANCE_MODEL));n.addProperty("reference",AL10.alGetSourcef(source,AL10.AL_REFERENCE_DISTANCE));n.addProperty("maximum",AL10.alGetSourcef(source,AL10.AL_MAX_DISTANCE));n.addProperty("rolloff",AL10.alGetSourcef(source,AL10.AL_ROLLOFF_FACTOR));n.add("position",nativeVector(source,false));}
        n.add("listener_position",nativeVector(0,true));n.addProperty("listener_gain",AL10.alGetListenerf(AL10.AL_GAIN));n.addProperty("al_error",AL10.alGetError());if(n.get("al_error").getAsInt()!=0)throw new IllegalStateException("native Channel getter error");return n;
    }

    private static JsonObject missingNative() {JsonObject r=new JsonObject();r.addProperty("source_valid",false);r.addProperty("channel_pending",true);return r;}
    private static JsonArray nativeVector(int source,boolean listener) {FloatBuffer b=BufferUtils.createFloatBuffer(3);if(listener)AL10.alGetListenerfv(AL10.AL_POSITION,b);else AL10.alGetSourcefv(source,AL10.AL_POSITION,b);JsonArray r=new JsonArray();for(int i=0;i<3;i++)r.add(b.get(i));return r;}

    private static JsonObject binding(Voice v) {
        JsonObject r=new JsonObject();r.addProperty("generation",v.generation);r.addProperty("entity_id",v.drone.getId());r.addProperty("entity_identity",id(v.drone));r.addProperty("set_identity",id(v.set));r.addProperty("sound_identity",id(v.sound));r.addProperty("layer",v.sound.layer().name());r.addProperty("handle_identity",id(v.handle));r.addProperty("channel_identity",id(v.channel));r.addProperty("stream_identity",id(v.stream));r.addProperty("catalog_identity",id(field(v.sound,"assetCatalog")));r.addProperty("asset_identity",id(field(v.sound,"calibratedAsset")));r.addProperty("resource",String.valueOf(field(v.sound,"calibratedResource")));r.addProperty("held",v.held);r.addProperty("repeat_instantly",v.repeat);r.addProperty("original_future_identity",id(v.originalFuture));r.addProperty("returned_future_identity",id(v.held?v.heldFuture:v.originalFuture));return r;
    }

    private static JsonObject rawSound(DroneLoopSoundInstance sound) {
        DroneEntity drone=(DroneEntity)field(sound,"drone");JsonObject r=new JsonObject();r.addProperty("raw_envelope",(Float)field(sound,"volume"));r.addProperty("raw_pitch",(Float)field(sound,"pitch"));r.addProperty("rpm",drone.getAverageMotorRpm());r.addProperty("alive",drone.isAlive());r.addProperty("removed",drone.isRemoved());r.addProperty("stopped",sound.isStopped());r.addProperty("procedural_backend",sound.usesProceduralAudio());r.addProperty("coefficient",(Double)field(sound,"assetAmplitude"));r.addProperty("asset_operating_point_valid",(Boolean)field(sound,"assetOperatingPointValid"));return r;
    }
    private static JsonObject resolvedSound(DroneLoopSoundInstance sound) {JsonObject r=rawSound(sound);r.addProperty("effective_volume",sound.getVolume());r.addProperty("effective_pitch",sound.getPitch());return r;}

    private static void bindClient(Session s,Voice v) {
        requireClient();if(v.set==null)v.set=activeSets().get(v.drone.getId());ChannelAccess.ChannelHandle h=((SoundEngineChannelsAccessor)engine()).fpvdrone$getInstanceToChannel().get(v.sound);
        if(h!=null){if(v.handle!=null && v.handle!=h)throw new IllegalStateException("one sound changed allocated handle");v.handle=h;Channel c=((ChannelHandleChannelAccessor)h).fpvdrone$getChannel();if(c!=null){if(v.channel!=null&&v.channel!=c)throw new IllegalStateException("one handle changed actual Channel");v.channel=c;}}
    }
    private static Voice findChannel(Session s,Channel c) {for(Voice v:s.voices)if(v.channel==c)return v;return null;}
    private static JsonObject event(Session s,String kind) {JsonObject r=new JsonObject();r.addProperty("kind",kind);stamp(s,r);s.events.add(r);return r;}
    private static JsonObject event(Session s,String kind,Voice v) {JsonObject r=event(s,kind);r.addProperty("generation",v.generation);r.addProperty("entity_id",v.drone.getId());r.addProperty("sound_identity",id(v.sound));r.addProperty("layer",v.sound.layer().name());return r;}
    private static void stamp(Session s,JsonObject r) {r.addProperty("order",++s.order);r.addProperty("thread",Thread.currentThread().getName());r.addProperty("actual_client_thread",Minecraft.getInstance().isSameThread());}

    static synchronized void markCleanup(Session s) {requireClient();check(s);s.cleanup=true;event(s,"fixture_cleanup_begin");for(Voice v:s.voices)if(v.heldFuture!=null&&!v.heldFuture.isDone()){event(s,"failure_cleanup_completes_held_future",v);v.heldFuture.complete(v.stream);}}
    static synchronized JsonObject cleanupNative(Session s) {requireOwner();JsonObject r=nativeStage(s,"post_stopAll_before_fixture_reclaim",s.engineTickId,new JsonObject(),0);s.stages.add("post_stopAll_before_fixture_reclaim",r);return r;}
    static synchronized void closeUnattachedAfterPrimaryAndStopAll(Session s) {
        requireOwner();if(!s.cleanup)throw new IllegalStateException("fixture reclaim before primary observation and stopAll");
        for(Voice v:s.voices)if(v.stream!=null && !(Boolean)field(v.stream,"closed")){JsonObject e=event(s,"fixture_closes_unattached_after_stopAll",v);e.addProperty("closed_before",false);v.stream.close();e.addProperty("closed_after",(Boolean)field(v.stream,"closed"));}
    }
    static synchronized JsonObject snapshot(Session s) {
        JsonObject r=new JsonObject();r.addProperty("epoch",s.epoch);r.addProperty("held_entity_id",s.heldEntityId);r.addProperty("newcomer_entity_id",s.newcomerEntityId);r.addProperty("release_sound_engine_tick_id",s.releaseEngineTickId);r.addProperty("eviction_observed",s.evictionObserved);r.addProperty("held_futures_released",s.released);r.addProperty("capture_failure",s.failure);r.addProperty("primary_observation_completed",s.primaryComplete.isDone()&&!s.primaryComplete.isCompletedExceptionally());
        r.add("warm_client",s.warmClient==null?JsonNull.INSTANCE:s.warmClient.deepCopy());r.add("eviction_client",s.evictionClient==null?JsonNull.INSTANCE:s.evictionClient.deepCopy());r.add("stages",s.stages.deepCopy());r.add("events",s.events.deepCopy());r.add("sound_tick_observations",s.soundTicks.deepCopy());r.add("manager_tick_observations",s.managerTicks.deepCopy());r.add("engine_tick_observations",s.engineTicks.deepCopy());
        JsonArray voices=new JsonArray();for(Voice v:s.voices){JsonObject x=binding(v);x.add("constructor_initial",v.constructor.deepCopy());x.add("asset_read_batches",v.reads.deepCopy());x.addProperty("total_read_bytes",v.cumulativeReadBytes);x.addProperty("stream_closed_after_cleanup",v.stream!=null&&(Boolean)field(v.stream,"closed"));x.addProperty("sound_tick_count",v.soundTickCount);voices.add(x);}r.add("voices",voices);return r;
    }
    static synchronized void finish(Session s) {requireClient();check(s);if(!s.cleanup)throw new IllegalStateException("session finished before cleanup");current=null;}
    static synchronized JsonArray libraryEvents() {return libraryEvents.deepCopy();}

    static SoundEngine engine() {return ((SoundManagerEngineAccessor)Minecraft.getInstance().getSoundManager()).fpvdrone$getSoundEngine();}
    static MainOutputLibraryAccessor library() {return (MainOutputLibraryAccessor)((SoundEngineMainOutputAccessor)engine()).mainOutput$getLibrary();}
    static void identity(JsonObject r) {requireOwner();var l=library();r.addProperty("device",l.mainOutput$getDevice());r.addProperty("context",l.mainOutput$getContext());r.addProperty("actual_current_context",ALC10.alcGetCurrentContext());r.addProperty("library_generation",libraryGeneration);r.addProperty("version",AL10.alGetString(AL10.AL_VERSION));r.addProperty("owner_thread",Thread.currentThread().getName());r.addProperty("actual_sound_executor_thread",((SoundEngineExecutorAccessor)engine()).fpvdrone$getExecutor().isSameThread());r.addProperty("null_driver_environment",System.getenv("ALSOFT_DRIVERS"));}
    private static void requireClient() {if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("late-delivery client control off actual client");}
    private static void requireOwner() {if(!((SoundEngineExecutorAccessor)engine()).fpvdrone$getExecutor().isSameThread() || library().mainOutput$getContext()==0 || ALC10.alcGetCurrentContext()!=library().mainOutput$getContext())throw new IllegalStateException("late-delivery native observation off actual sound owner/current context");}
    private static void check(Session s) {if(current!=s)throw new IllegalStateException("stale late-delivery session");}
    @SuppressWarnings("unchecked") static Map<Integer,Object> activeSets() {return (Map<Integer,Object>)field(DroneSoundManager.class,"ACTIVE_SOUNDS");}
    static Object field(Object object,String name) {Class<?> type=object instanceof Class<?> c?c:object.getClass();for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object instanceof Class<?>?null:object);}catch(NoSuchFieldException ignored){}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}throw new IllegalStateException("missing actual field "+type.getName()+"."+name);}
    static int id(Object object) {return object==null?0:System.identityHashCode(object);}
    static String sha(byte[] data) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}catch(Exception error){throw new IllegalStateException(error);}}
}
