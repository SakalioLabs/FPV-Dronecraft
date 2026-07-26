package com.tenicana.dronecraft.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.sound.OpenAlStreamingQueueProbe;
import com.tenicana.dronecraft.client.sound.ListenerReverbQueueHandoffProbe;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
abstract class ChannelStreamingQueueMixin {
	@Shadow
	@Final
	private int source;

	@Shadow
	private AudioStream stream;

	@Inject(method = "method_19648(I)V", at = @At("TAIL"))
	private void fpvdrone$observeQueuedStreamingBuffer(
			int buffer,
			CallbackInfo callback
	) {
		OpenAlStreamingQueueProbe.onBufferQueued(
				stream,
				source,
				buffer
		);
		ListenerReverbQueueHandoffProbe.onBufferQueued(
				stream,
				source,
				buffer
		);
	}
}
