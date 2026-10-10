package com.tenicana.dronecraft.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenicana.dronecraft.client.performance.AsyncGpuTimer;
import com.tenicana.dronecraft.client.performance.RuntimePerformanceMonitor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** CPU submission and optional GPU timestamp spans cover GameRenderer.render only. */
@Mixin(GameRenderer.class)
abstract class RuntimePerformanceGameRendererMixin {
    @WrapMethod(method = "render")
    private void fpvdrone$measureRuntimeRender(DeltaTracker delta, boolean render,
            Operation<Void> original) {
        long frameToken = RuntimePerformanceMonitor.beginRender(render);
        long gpuToken = AsyncGpuTimer.begin(frameToken);
        long startedNs = frameToken == 0 ? 0 : System.nanoTime();
        boolean completed = false;
        try {
            original.call(delta, render);
            completed = true;
        } finally {
            long elapsedNs = frameToken == 0 ? 0 : System.nanoTime() - startedNs;
            AsyncGpuTimer.end(gpuToken, completed);
            RuntimePerformanceMonitor.endRender(frameToken, elapsedNs, completed);
        }
    }
}
