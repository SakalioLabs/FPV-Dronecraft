package com.tenicana.dronecraft.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.GpuSurface;
import com.tenicana.dronecraft.client.performance.RuntimePerformanceMonitor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Measures the native presentation operation on either Renderpearl backend. */
@Mixin(Minecraft.class)
abstract class RuntimePerformanceRenderSystemMixin {
    @WrapOperation(method = "renderFrame", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuSurface;present()V"))
    private void fpvdrone$measureRuntimeSwap(GpuSurface surface, Operation<Void> original) {
        if (RuntimePerformanceMonitor.currentFrameToken() == 0) { original.call(surface); return; }
        long started = System.nanoTime();
        original.call(surface);
        RuntimePerformanceMonitor.recordSwap(System.nanoTime() - started);
    }
}
