package com.tenicana.dronecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.platform.FramerateLimitTracker;

@Mixin(FramerateLimitTracker.class)
public interface FramerateLimitTrackerAccessor {
	@Accessor("latestInputTime")
	long fpvdrone$getLatestInputTime();
}
