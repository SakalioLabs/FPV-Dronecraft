package com.tenicana.dronecraft.performance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

/** Reads the final production FOV, including the normal camera override. */
@Mixin(GameRenderer.class)
public interface GameRendererFovAccess {
	@Invoker("getFov")
	float fpvPerformance$readFov(Camera camera, float partialTick, boolean useFovSetting);
}
