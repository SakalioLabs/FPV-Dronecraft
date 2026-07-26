package com.tenicana.dronecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.audio.Channel;

import net.minecraft.client.sounds.ChannelAccess;

@Mixin(ChannelAccess.ChannelHandle.class)
public interface ChannelHandleChannelAccessor {
	@Accessor("channel")
	Channel fpvdrone$getChannel();
}
