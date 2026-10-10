package com.tenicana.dronecraft.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tenicana.dronecraft.client.performance.RuntimePerformanceMonitor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Isolates the native swap call from flipFrame's event polling and cleanup. */
@Mixin(RenderSystem.class)
abstract class RuntimePerformanceRenderSystemMixin {
    @WrapOperation(method = "flipFrame", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V", remap = false))
    private static void fpvdrone$measureRuntimeSwap(long window, Operation<Void> original) {
        if (RuntimePerformanceMonitor.currentFrameToken() == 0) {
            original.call(window);
            return;
        }
        long startedNs = System.nanoTime();
        original.call(window);
        RuntimePerformanceMonitor.recordSwap(System.nanoTime() - startedNs);
    }
}
