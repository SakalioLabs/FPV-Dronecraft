package com.tenicana.dronecraft.client.mixin.wet;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.tenicana.dronecraft.client.sound.*;
import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Mixin(SoundEngine.class)
public abstract class WetSoundAdmissionMixin {
    @WrapMethod(method = "play")
    private SoundEngine.PlayResult wetAdmission$play(SoundInstance sound, Operation<SoundEngine.PlayResult> original) {
        if (!WetMainOutputCapture.requested()) return original.call(sound);
        var ticket = WetSoundAdmissionCapture.begin(sound);
        if (!ticket.allowed) return SoundEngine.PlayResult.NOT_STARTED;
        SoundEngine.PlayResult result = null; Throwable failure = null;
        try { result = original.call(sound); return result; }
        catch (Throwable error) { failure = error; throw error; }
        finally { WetSoundAdmissionCapture.end(ticket, result, failure); }
    }
    @WrapOperation(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess;createHandle(Lcom/mojang/blaze3d/audio/Library$Pool;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChannelAccess.ChannelHandle> wetAdmission$allocate(ChannelAccess access, Library.Pool pool, Operation<CompletableFuture<ChannelAccess.ChannelHandle>> original) {
        if (!WetMainOutputCapture.requested()) return original.call(access, pool);
        var ticket = WetSoundAdmissionCapture.ticket();
        return original.call(access, pool).thenApply(handle -> WetSoundAdmissionCapture.bind(ticket, handle));
    }
    @WrapOperation(method = "play", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenAccept(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;", remap = false))
    private CompletableFuture<Void> wetAdmission$delivery(CompletableFuture<Object> future, Consumer<Object> consumer, Operation<CompletableFuture<Void>> original) {
        if (!WetMainOutputCapture.requested()) return original.call(future, consumer);
        var delivery = WetSoundAdmissionCapture.delivery();
        CompletableFuture<Void> result;
        try { result = original.call(future, delivery.wrap(consumer)); }
        catch (Throwable error) { delivery.complete(error); throw error; }
        result.whenComplete((ignored, failure) -> delivery.complete(failure)); return result;
    }
}
