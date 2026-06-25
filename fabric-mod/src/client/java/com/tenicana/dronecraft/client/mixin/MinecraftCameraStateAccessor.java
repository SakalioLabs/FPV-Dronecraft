package com.tenicana.dronecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;

@Mixin(Minecraft.class)
public interface MinecraftCameraStateAccessor {
	@Accessor("crosshairPickEntity")
	void fpvdrone$setCrosshairPickEntity(Entity entity);

	@Accessor("hitResult")
	void fpvdrone$setHitResult(HitResult hitResult);
}
