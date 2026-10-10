package com.tenicana.dronecraft.client.mixin;

import com.mojang.blaze3d.platform.Window;
import com.tenicana.dronecraft.client.performance.AsyncGpuTimer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fallback release when a window is closed without Minecraft.close completing. */
@Mixin(Window.class)
abstract class RuntimePerformanceWindowMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void fpvdrone$releaseRuntimeGpuQueriesBeforeContextDestruction(CallbackInfo callback) {
        AsyncGpuTimer.close();
    }
}
