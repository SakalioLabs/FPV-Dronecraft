package com.tenicana.dronecraft.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Library;
import com.tenicana.dronecraft.client.sound.StreamingVoiceBudget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Reserve drone streams before vanilla derives the remaining static pool. */
@Mixin(Library.class)
abstract class LibraryStreamingBudgetMixin {
    @WrapOperation(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/Mth;clamp(III)I", ordinal = 0))
    private int fpvdrone$reserveStreamingVoices(int requested, int low, int high,
            Operation<Integer> original, @Local(index = 5) int totalChannels) {
        int vanillaLimit = original.call(requested, low, high);
        return StreamingVoiceBudget.streamingLimit(totalChannels, vanillaLimit);
    }
}
