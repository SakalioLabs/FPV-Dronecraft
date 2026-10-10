package com.tenicana.dronecraft.client.mixin;

import com.tenicana.dronecraft.client.sound.MinecraftAcousticWorldDirtyTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
abstract class ClientLevelAcousticDirtyMixin {
	@Inject(method = "setBlock", at = @At("RETURN"))
	private void fpvdrone$markAcousticCellDirty(
			BlockPos position,
			BlockState state,
			int flags,
			int recursionLeft,
			CallbackInfoReturnable<Boolean> callback
	) {
		if (Boolean.TRUE.equals(callback.getReturnValue())) {
			MinecraftAcousticWorldDirtyTracker.onBlockChanged(
					(ClientLevel) (Object) this,
					position
			);
		}
	}
}
