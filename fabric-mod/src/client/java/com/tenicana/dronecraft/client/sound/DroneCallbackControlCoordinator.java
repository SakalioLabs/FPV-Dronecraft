package com.tenicana.dronecraft.client.sound;

import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.mixin.ChannelHandleChannelAccessor;
import com.tenicana.dronecraft.client.mixin.SoundEngineControlInvoker;
import com.tenicana.dronecraft.client.mixin.SoundEngineExecutorAccessor;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.SOFTDeferredUpdates;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * One sound-executor transaction publishes the Java controls after applying
 * their native parameters. This removes prolonged owner-stall control splits;
 * it does not provide a hardware mixer-block or sample-atomic native/Java fence.
 */
public final class DroneCallbackControlCoordinator {
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong versions = new AtomicLong();
    private final AtomicReference<DroneAudioControlFrame> pending = new AtomicReference<>();
    private final AtomicReference<DroneAudioControlFrame> latestPrepared = new AtomicReference<>();
    private final AtomicReference<DroneAudioControlFrame> committed = new AtomicReference<>();
    private final AtomicReference<Drain> scheduled = new AtomicReference<>();
    private final AtomicInteger enrolledChannels = new AtomicInteger();
    private final ThreadLocal<Boolean> ownerMutation = ThreadLocal.withInitial(() -> false);
    // Control/lifecycle synchronization only. Callback getters never acquire it.
    private final Object lifecycle = new Object();
    private final BiPredicate<DroneAudioControlFrame, SoundEngine> nativeCommit;
    private volatile long nativeContext;
    private volatile String lastRejection;
    private volatile long rejectedFrames;
    private volatile boolean nativeBatchUsed;

    public DroneCallbackControlCoordinator() { nativeCommit = this::commitNative; }

    DroneCallbackControlCoordinator(BiPredicate<DroneAudioControlFrame, SoundEngine> nativeCommit) {
        this.nativeCommit = nativeCommit;
    }

    public long generation() { return generation.get(); }
    long nextVersion() { return versions.incrementAndGet(); }
    public DroneAudioControlFrame committedFrame() { return committed.get(); }
    /** Latest client-prepared publication, retained after the mailbox drains. */
    public DroneAudioControlFrame pendingFrame() { return latestPrepared.get(); }
    public long rejectedFrames() { return rejectedFrames; }
    public String lastRejection() { return lastRejection; }
    public boolean nativeBatchUsed() { return nativeBatchUsed; }

    /** Old admitted streams retain their last complete frame until native close. */
    public void invalidate() {
        synchronized (lifecycle) {
            generation.incrementAndGet();
            pending.set(null);
            latestPrepared.set(null);
            scheduled.set(null);
        }
    }

    void stage(DroneAudioControlFrame frame, SoundEngine engine) {
        stage(frame, engine, action -> ((SoundEngineExecutorAccessor) engine).fpvdrone$getExecutor().execute(action));
    }

    void stageForTest(DroneAudioControlFrame frame, Consumer<Runnable> executor) {
        stage(frame, null, executor);
    }

    private void stage(DroneAudioControlFrame frame, SoundEngine engine, Consumer<Runnable> executor) {
        synchronized (lifecycle) {
            if (frame.generation() != generation.get()) return;
            DroneAudioControlFrame latest = latestPrepared.get();
            if (latest != null && latest.generation() == frame.generation() && latest.version() >= frame.version()) return;
            latestPrepared.set(frame);
            pending.set(frame);
            schedule(engine, executor);
        }
    }

    private void schedule(SoundEngine engine, Consumer<Runnable> executor) {
        Drain task = new Drain(generation.get(), engine, executor);
        if (scheduled.compareAndSet(null, task)) {
            try { executor.accept(() -> drain(task)); }
            catch (RuntimeException error) {
                scheduled.compareAndSet(task, null);
                reject("sound executor refused control transaction");
            }
        }
    }

    private void drain(Drain task) {
        synchronized (lifecycle) {
            if (scheduled.get() != task || task.generation != generation.get()) return;
            DroneAudioControlFrame frame = pending.getAndSet(null);
            try {
                if (frame != null && frame.generation() == generation.get()
                        && nativeCommit.test(frame, task.engine)
                        && frame.generation() == generation.get()) {
                    committed.set(frame);
                    if (task.engine != null) enroll(frame);
                }
            } finally {
                if (scheduled.compareAndSet(task, null) && pending.get() != null) schedule(task.engine, task.executor);
            }
        }
    }

    public boolean ownerMutationAllowed() { return ownerMutation.get(); }

    public boolean suppressListenerMutation() {
        return enrolledChannels.get() != 0 && !ownerMutationAllowed()
                && nativeContext != 0 && ALC10.alcGetCurrentContext() == nativeContext;
    }

    /** Called only by native Channel admission/destroy on the sound executor. */
    public void channelEnrolled() { enrolledChannels.incrementAndGet(); }
    public void channelClosing() {
        if (enrolledChannels.updateAndGet(value -> Math.max(0, value - 1)) == 0) {
            nativeContext = 0;
            // Each retiring stream owns its last snapshot until native stop.
            // Release the global frame's SoundInstances/world references now.
            committed.set(null);
        }
    }

    private boolean commitNative(DroneAudioControlFrame frame, SoundEngine engine) {
        Targets targets = targets(frame);
        if (targets == null) return false;
        long context = ALC10.alcGetCurrentContext();
        if (context == 0 || (nativeContext != 0 && nativeContext != context)) return reject("native control context changed");
        if (frame.generation() != generation.get()) return reject("control generation invalidated before commit");
        boolean deferred = false;
        boolean batched = false;
        ownerMutation.set(true);
        try {
            check("before native control transaction");
            var caps = AL.getCapabilities();
            if (caps.AL_SOFT_deferred_updates && caps.alDeferUpdatesSOFT != 0 && caps.alProcessUpdatesSOFT != 0
                    && AL10.alIsExtensionPresent("AL_SOFT_deferred_updates")) {
                if (AL10.alGetBoolean(SOFTDeferredUpdates.AL_DEFERRED_UPDATES_SOFT))
                    return reject("another owner already deferred native updates");
                SOFTDeferredUpdates.alDeferUpdatesSOFT();
                deferred = true;
                batched = true;
                check("defer native controls");
            }
            ((SoundEngineControlInvoker) engine).fpvdrone$getListener().setTransform(frame.listenerTransform());
            for (int index = 0; index < frame.sources().size(); index++) {
                var source = frame.sources().get(index);
                var position = source.audioSnapshot().source().positionMeters();
                var nativePosition = new net.minecraft.world.phys.Vec3(position.x(), position.y(), position.z());
                Channel motor = targets.channels[index * 2], propeller = targets.channels[index * 2 + 1];
                motor.setVolume(source.motorNativeGain()); motor.setPitch(source.motorNativePitch()); motor.setSelfPosition(nativePosition);
                propeller.setVolume(source.propellerNativeGain()); propeller.setPitch(source.propellerNativePitch()); propeller.setSelfPosition(nativePosition);
            }
            Channel wet = targets.channels[targets.channels.length - 1];
            wet.setVolume(frame.wetNativeGain()); wet.setPitch(frame.wetNativePitch());
            check("apply native control frame");
            if (deferred) {
                SOFTDeferredUpdates.alProcessUpdatesSOFT();
                deferred = false;
                check("process native controls");
            }
            if (frame.generation() != generation.get()) throw new IllegalStateException("control generation invalidated during native transaction");
            nativeContext = context;
            nativeBatchUsed = batched;
            lastRejection = null;
            return true;
        } catch (Throwable error) {
            reject(error.toString());
            // A partial native write cannot remain audible with the old Java
            // publication. Stop only this exceptional failed group; never
            // restart sources on an ordinary control update.
            for (Channel channel : targets.channels) {
                try { channel.stop(); ((CallbackChannelAccess) channel).rejectCallbackControls(lastRejection); }
                catch (Throwable stopError) { error.addSuppressed(stopError); }
            }
            FpvDronecraftMod.LOGGER.error("Callback control transaction failed closed", error);
            return false;
        } finally {
            if (deferred) {
                try { SOFTDeferredUpdates.alProcessUpdatesSOFT(); }
                catch (Throwable error) { FpvDronecraftMod.LOGGER.error("Could not resume native control updates", error); }
            }
            ownerMutation.remove();
        }
    }

    private Targets targets(DroneAudioControlFrame frame) {
        if (frame.sources().isEmpty() || frame.sources().size() > StreamingVoiceBudget.MAX_DRONES)
            { reject("control source membership unavailable"); return null; }
        Channel[] channels = new Channel[frame.sources().size() * 2 + 1];
        for (int index = 0; index < frame.sources().size(); index++) {
            var source = frame.sources().get(index);
            if (!source.state().distanceStreamsVerified()
                    || source.audioSnapshot().preparedMotor().uniqueToneCount() > 128
                    || source.audioSnapshot().preparedPropeller().uniqueToneCount() > 128) {
                reject("source distance/capacity admission unavailable"); return null;
            }
            channels[index * 2] = target(source.motorHandle(), source.motorStream(), source.state(), 0);
            channels[index * 2 + 1] = target(source.propellerHandle(), source.propellerStream(), source.state(), 1);
            if (channels[index * 2] == null || channels[index * 2 + 1] == null) return null;
        }
        channels[channels.length - 1] = target(frame.wetHandle(), frame.wetStream(), frame.wetState(), 2);
        if (channels[channels.length - 1] == null) return null;
        for (int index = 0; index < channels.length; index++)
            for (int previous = 0; previous < index; previous++)
                if (channels[index] == channels[previous]) { reject("duplicate native control Channel"); return null; }
        return new Targets(channels);
    }

    private Channel target(ChannelAccess.ChannelHandle handle, Object expectedStream, Object state, int layer) {
        if (handle == null || expectedStream == null) { reject("control handle/stream missing"); return null; }
        Channel channel = ((ChannelHandleChannelAccessor) handle).fpvdrone$getChannel();
        if (channel == null || !(channel instanceof CallbackChannelAccess access)) { reject("control Channel released"); return null; }
        NativeCallbackBinding binding = access.callbackBinding();
        if (binding == null || !binding.attached() || binding.closed() || binding.quarantined()
                || binding.failure() != null || binding.fault() != 0 || access.callbackDisabledReason() != null
                || binding.stream() != expectedStream || binding.stream().callbackControlOwner() != state
                || binding.stream().callbackControlLayer() != layer || !AL10.alIsSource(binding.source())
                || AL10.alGetSourcei(binding.source(), AL10.AL_BUFFER) != binding.buffer()
                || AL10.alGetSourcei(binding.source(), AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED) {
            reject("control callback binding missing/mixed/closed"); return null;
        }
        return channel;
    }

    private void enroll(DroneAudioControlFrame frame) {
        for (var source : frame.sources()) {
            enroll(source.motorHandle(), source.audioSnapshot()); enroll(source.propellerHandle(), source.audioSnapshot());
        }
        enroll(frame.wetHandle(), frame.wetSnapshot());
    }

    private void enroll(ChannelAccess.ChannelHandle handle, Object snapshot) {
        Channel channel = ((ChannelHandleChannelAccessor) handle).fpvdrone$getChannel();
        if (channel != null) {
            CallbackChannelAccess access = (CallbackChannelAccess) channel;
            access.enrollCallbackControls(this);
            access.callbackBinding().stream().enrollCallbackControls(snapshot);
        }
    }

    private boolean reject(String reason) { lastRejection = reason; rejectedFrames++; return false; }
    private static void check(String operation) {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) throw new IllegalStateException(operation + " OpenAL error " + error);
    }
    private record Drain(long generation, SoundEngine engine, Consumer<Runnable> executor) { }
    private record Targets(Channel[] channels) { }
}
