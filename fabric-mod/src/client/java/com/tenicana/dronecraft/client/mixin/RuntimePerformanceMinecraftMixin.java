package com.tenicana.dronecraft.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tenicana.dronecraft.client.performance.AsyncGpuTimer;
import com.tenicana.dronecraft.client.performance.RuntimePerformanceMonitor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measures normal frames while preserving vanilla's nested runTick(false) calls. */
@Mixin(Minecraft.class)
abstract class RuntimePerformanceMinecraftMixin {
    @WrapMethod(method = "runTick")
    private void fpvdrone$measureRuntimeFrame(boolean render, Operation<Void> original) {
        long frameToken = RuntimePerformanceMonitor.beginFrame((Minecraft) (Object) this, render);
        long startedNs = frameToken == 0 ? 0 : System.nanoTime();
        boolean completed = false;
        try {
            if (frameToken != 0) {
                AsyncGpuTimer.service();
            } else if (!RuntimePerformanceMonitor.enabled()) {
                AsyncGpuTimer.close();
            }
            original.call(render);
            completed = true;
        } finally {
            long elapsedNs = frameToken == 0 ? 0 : System.nanoTime() - startedNs;
            if (!completed) AsyncGpuTimer.close();
            RuntimePerformanceMonitor.endFrame(frameToken, elapsedNs, completed);
            if (!RuntimePerformanceMonitor.enabled()) AsyncGpuTimer.close();
        }
    }

    @WrapMethod(method = "tick")
    private void fpvdrone$measureRuntimeClientTick(Operation<Void> original) {
        if (RuntimePerformanceMonitor.currentFrameToken() == 0) {
            original.call();
            return;
        }
        long startedNs = System.nanoTime();
        original.call();
        RuntimePerformanceMonitor.recordClientTick(System.nanoTime() - startedNs);
    }

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
    private void fpvdrone$measureRuntimeFrameLimit(int limit, Operation<Void> original) {
        if (RuntimePerformanceMonitor.currentFrameToken() == 0) {
            original.call(limit);
            return;
        }
        long startedNs = System.nanoTime();
        original.call(limit);
        RuntimePerformanceMonitor.recordFrameLimit(System.nanoTime() - startedNs);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void fpvdrone$releaseRuntimeGpuQueries(CallbackInfo callback) {
        // Release before vanilla closes the GpuDevice and destroys the GL context.
        AsyncGpuTimer.close();
    }
}
