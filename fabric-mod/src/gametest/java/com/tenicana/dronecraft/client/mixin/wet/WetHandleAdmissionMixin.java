package com.tenicana.dronecraft.client.mixin.wet;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.sound.*;
import net.minecraft.client.sounds.ChannelAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

@Mixin(ChannelAccess.ChannelHandle.class)
public abstract class WetHandleAdmissionMixin {
    @WrapOperation(method = "execute", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/Executor;execute(Ljava/lang/Runnable;)V", remap = false))
    private void wetAdmission$execute(Executor executor, Runnable runnable, Operation<Void> original) {
        if (!WetMainOutputCapture.requested()) { original.call(executor, runnable); return; }
        original.call(executor, WetSoundAdmissionCapture.guardTask((ChannelAccess.ChannelHandle) (Object) this, runnable));
    }
}
