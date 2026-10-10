package com.tenicana.dronecraft.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.sound.ProceduralDistanceOwner;
import com.tenicana.dronecraft.client.sound.CalibratedStreamLifecycle;
import com.tenicana.dronecraft.client.sound.DistanceOwnershipRuntimeTrace;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Channel.class)
abstract class ChannelDistanceOwnershipMixin {
    @Shadow @Final private int source;
    @Unique private boolean fpvdrone$distanceRejected;
    @Unique private boolean fpvdrone$distanceOwned;
    @Unique private AudioStream fpvdrone$distanceStream;

    @WrapMethod(method = "attachBufferStream")
    private void fpvdrone$attachOnlyLiveStream(AudioStream stream, Operation<Void> original) {
        if (!CalibratedStreamLifecycle.attach(stream, () -> original.call(stream))) {
            fpvdrone$distanceStream = stream;
            fpvdrone$distanceOwned = false;
            fpvdrone$distanceRejected = true;
        }
    }

    @Inject(method = "attachBufferStream", at = @At("HEAD"), cancellable = true)
    private void fpvdrone$assignDistanceBeforeStream(AudioStream stream, CallbackInfo callback) {
        var ownership = ProceduralDistanceOwner.onStreamAttached(stream, source);
        fpvdrone$distanceRejected = !ownership.mayPlay();
        fpvdrone$distanceOwned = ownership.status() == ProceduralDistanceOwner.Status.VERIFIED;
        fpvdrone$distanceStream = stream;
        DistanceOwnershipRuntimeTrace.attached(stream, source, ownership);
        if (fpvdrone$distanceRejected) {
            try {
                stream.close();
            } catch (java.io.IOException error) {
                FpvDronecraftMod.LOGGER.warn("Could not close rejected procedural stream", error);
            }
            callback.cancel();
        }
    }

    @Inject(method = {"play", "unpause"}, at = @At("HEAD"), cancellable = true)
    private void fpvdrone$rejectUnownedPlayback(CallbackInfo callback) {
        if (!CalibratedStreamLifecycle.mayPlay(fpvdrone$distanceStream)
                || !ProceduralDistanceOwner.mayPlayAttachedStream(fpvdrone$distanceStream)) {
            fpvdrone$distanceRejected = true;
        }
        DistanceOwnershipRuntimeTrace.playback(source, fpvdrone$distanceOwned, fpvdrone$distanceRejected, false);
        if (fpvdrone$distanceRejected) callback.cancel();
    }

    @WrapOperation(method = {"play", "unpause"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alSourcePlay(I)V"))
    private void fpvdrone$playUnderDistanceOwnership(int nativeSource, Operation<Void> original) {
        if (!CalibratedStreamLifecycle.play(fpvdrone$distanceStream,
                () -> ProceduralDistanceOwner.runAttachedPlayback(fpvdrone$distanceStream, () -> original.call(nativeSource)))) {
            fpvdrone$distanceRejected = true;
        }
    }

    @Inject(method = {"play", "unpause"}, at = @At("TAIL"))
    private void fpvdrone$observeOwnedPlayback(CallbackInfo callback) {
        DistanceOwnershipRuntimeTrace.playback(source, fpvdrone$distanceOwned, fpvdrone$distanceRejected, true);
    }

    @Inject(method = "destroy", at = @At("TAIL"))
    private void fpvdrone$observeDistanceCleanup(CallbackInfo callback) {
        DistanceOwnershipRuntimeTrace.destroyed(source);
    }

    @Inject(method = "stopped", at = @At("HEAD"), cancellable = true)
    private void fpvdrone$retireRejectedChannel(CallbackInfoReturnable<Boolean> callback) {
        // A never-played AL_INITIAL source cannot signal its own completion.
        // Let vanilla ChannelAccess release/destroy this rejected handle normally.
        if (fpvdrone$distanceRejected) callback.setReturnValue(true);
    }
}
