package com.tenicana.dronecraft.performance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenicana.dronecraft.performance.ControllerModelSmoke;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Observes the model/texture vanilla resolves for actual held and GUI items. */
@Mixin(ItemModelResolver.class)
public abstract class ControllerItemModelProbeMixin {
	@Inject(method = "updateForTopItem", at = @At("RETURN"))
	private void fpvPerformance$controllerItemModel(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context,
			Level level, ItemOwner owner, int seed, CallbackInfo ci) {
		if (ControllerModelSmoke.ENABLED) ControllerModelSmoke.onControllerModel(stack, context, state);
	}
}
