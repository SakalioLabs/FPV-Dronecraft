package com.tenicana.dronecraft.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tenicana.dronecraft.client.performance.RuntimePerformanceMonitor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(RenderSystem.class)
abstract class RuntimePerformanceRenderSystemMixin {
    @WrapOperation(method="flipFrame",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/GpuDevice;presentFrame()V"))
    private static void fpvdrone$measureRuntimeSwap(GpuDevice device,Operation<Void> original) {
        if (RuntimePerformanceMonitor.currentFrameToken()==0) { original.call(device);return; }
        long started=System.nanoTime();original.call(device);RuntimePerformanceMonitor.recordSwap(System.nanoTime()-started);
    }
}
