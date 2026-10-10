package com.tenicana.dronecraft.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.sound.CallbackChannelAccess;
import com.tenicana.dronecraft.client.sound.CallbackPcmStream;
import com.tenicana.dronecraft.client.sound.DroneCallbackControlCoordinator;
import com.tenicana.dronecraft.client.sound.NativeCallbackBinding;
import net.minecraft.client.sounds.AudioStream;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Channel.class, priority = 900)
abstract class ChannelCallbackBufferMixin implements CallbackChannelAccess {
    @Shadow @Final private int source;
    @Unique private NativeCallbackBinding fpvdrone$callbackBinding;
    @Unique private boolean fpvdrone$callbackRejected;
    @Unique private String fpvdrone$callbackDisabledReason;
    @Unique private DroneCallbackControlCoordinator fpvdrone$controlOwner;

    @Inject(method = {"setVolume", "setPitch"}, at = @At("HEAD"), cancellable = true)
    private void fpvdrone$keepCommittedScalar(float value, CallbackInfo info) {
        if (fpvdrone$controlOwner != null && !fpvdrone$controlOwner.ownerMutationAllowed()) info.cancel();
    }

    @Inject(method = "setSelfPosition", at = @At("HEAD"), cancellable = true)
    private void fpvdrone$keepCommittedPosition(net.minecraft.world.phys.Vec3 value, CallbackInfo info) {
        if (fpvdrone$controlOwner != null && !fpvdrone$controlOwner.ownerMutationAllowed()) info.cancel();
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void fpvdrone$releaseControlEnrollment(CallbackInfo info) {
        if (fpvdrone$controlOwner != null) {
            fpvdrone$controlOwner.channelClosing();
            fpvdrone$controlOwner = null;
        }
    }

    @Inject(method = "attachBufferStream", at = @At("HEAD"), cancellable = true)
    private void fpvdrone$rejectCallbackReattachment(AudioStream stream, CallbackInfo info) {
        if (fpvdrone$callbackBinding == null) return;
        // Library pool release destroys a Channel; it never reuses its source.
        // Retire an invalid second attach instead of losing the first binding.
        fpvdrone$callbackRejected = true;
        try { stream.close(); }
        catch (java.io.IOException error) { FpvDronecraftMod.LOGGER.warn("Could not close reattached callback stream", error); }
        info.cancel();
    }

    // The distance HEAD admission hook must have succeeded to reach this field.
    // Retain vanilla stream ownership so destroy closes it after native detach.
    @Inject(method = "attachBufferStream", at = @At(value = "FIELD",
            target = "Lcom/mojang/blaze3d/audio/Channel;stream:Lnet/minecraft/client/sounds/AudioStream;",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER), cancellable = true)
    private void fpvdrone$attachCallbackAfterAdmission(AudioStream stream, CallbackInfo info) {
        if (!(stream instanceof CallbackPcmStream candidate)) return;
        fpvdrone$callbackDisabledReason = NativeCallbackBinding.disabledReason(stream.getFormat());
        if (fpvdrone$callbackDisabledReason != null) {
            FpvDronecraftMod.LOGGER.debug("Procedural callback queue fallback: {}", fpvdrone$callbackDisabledReason);
            return;
        }
        try {
            if (!candidate.prepareCallback()) {
                fpvdrone$callbackDisabledReason = "stream cannot claim prepared callback ownership";
                return;
            }
            fpvdrone$callbackBinding = NativeCallbackBinding.attach(source, candidate);
            fpvdrone$callbackRejected = !fpvdrone$callbackBinding.attached();
            if (fpvdrone$callbackRejected) FpvDronecraftMod.LOGGER.warn("Could not attach procedural callback: {}", fpvdrone$callbackBinding.failure());
        } catch (Throwable error) {
            fpvdrone$callbackRejected = true;
            fpvdrone$callbackDisabledReason = "callback preparation failed: " + error;
            FpvDronecraftMod.LOGGER.warn("Could not prepare procedural callback", error);
        }
        // Success uses one static callback buffer. Failure retires this handle;
        // neither path may also pump vanilla queued buffers into this source.
        info.cancel();
    }

    @Inject(method = "updateStream", at = @At("HEAD"), cancellable = true)
    private void fpvdrone$skipCallbackQueueUpdate(CallbackInfo info) {
        if (fpvdrone$callbackBinding != null || fpvdrone$callbackRejected) info.cancel();
    }

    @Inject(method = "removeProcessedBuffers", at = @At("HEAD"), cancellable = true)
    private void fpvdrone$skipCallbackBufferUnqueue(CallbackInfoReturnable<Integer> info) {
        if (fpvdrone$callbackBinding != null || fpvdrone$callbackRejected) info.setReturnValue(0);
    }

    @Inject(method = {"play", "unpause"}, at = @At("HEAD"), cancellable = true)
    private void fpvdrone$skipFailedCallbackPlayback(CallbackInfo info) {
        if (fpvdrone$callbackRejected || (fpvdrone$callbackBinding != null && fpvdrone$callbackBinding.closed())) info.cancel();
    }

    @Inject(method = "stopped", at = @At("HEAD"), cancellable = true)
    private void fpvdrone$retireFailedCallback(CallbackInfoReturnable<Boolean> info) {
        if (fpvdrone$callbackRejected) info.setReturnValue(true);
    }

    @WrapOperation(method = "destroy", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alSourceStop(I)V"))
    private void fpvdrone$detachCallbackBeforeStreamClose(int nativeSource, Operation<Void> original) {
        original.call(nativeSource);
        if (fpvdrone$callbackBinding != null && !fpvdrone$callbackBinding.closeAfterSourceStop()) {
            FpvDronecraftMod.LOGGER.error("Callback native cleanup remains quarantined: {}", fpvdrone$callbackBinding.failure());
        }
    }

    /** Off-callback diagnostic on the transformed Channel; no source allocation. */
    @Override public NativeCallbackBinding callbackBinding() { return fpvdrone$callbackBinding; }
    @Override public String callbackDisabledReason() { return fpvdrone$callbackDisabledReason; }
    @Override public void enrollCallbackControls(DroneCallbackControlCoordinator owner) {
        if (fpvdrone$controlOwner == owner) return;
        if (fpvdrone$controlOwner != null) fpvdrone$controlOwner.channelClosing();
        fpvdrone$controlOwner = owner;
        owner.channelEnrolled();
    }
    @Override public void rejectCallbackControls(String reason) {
        fpvdrone$callbackRejected = true;
        fpvdrone$callbackDisabledReason = reason;
    }
}
