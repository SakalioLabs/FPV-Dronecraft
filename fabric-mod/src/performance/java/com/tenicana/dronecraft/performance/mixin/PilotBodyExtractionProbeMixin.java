package com.tenicana.dronecraft.performance.mixin;

import com.tenicana.dronecraft.performance.DronePlacementBindingSmoke;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes only the real native world extraction path, after all render filters. */
@Mixin(LevelRenderer.class)
public abstract class PilotBodyExtractionProbeMixin {
	@Inject(method = "extractEntity", at = @At("RETURN"))
	private void fpvPerformance$pilotExtracted(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> callback) {
		if (DronePlacementBindingSmoke.ENABLED) DronePlacementBindingSmoke.onPilotExtracted(entity, callback.getReturnValue());
	}
}
