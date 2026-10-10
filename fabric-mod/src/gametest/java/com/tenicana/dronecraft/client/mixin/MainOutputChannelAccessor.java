package com.tenicana.dronecraft.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Channel.class)
public interface MainOutputChannelAccessor {
    @Accessor("stream") AudioStream mainOutput$getStream();
}
