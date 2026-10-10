package com.tenicana.dronecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.platform.FramerateLimitTracker;

import net.minecraft.client.Minecraft;

@Mixin(Minecraft.class)
public interface MinecraftFramerateLimitTrackerAccessor {
	@Accessor("framerateLimitTracker")
	FramerateLimitTracker fpvdrone$getFramerateLimitTracker();
}
