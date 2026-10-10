package com.tenicana.dronecraft.client.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;

@Mixin(SoundEngine.class)
public interface SoundEngineChannelsAccessor {
	@Accessor("instanceToChannel")
	Map<SoundInstance, ChannelAccess.ChannelHandle>
			fpvdrone$getInstanceToChannel();
}
