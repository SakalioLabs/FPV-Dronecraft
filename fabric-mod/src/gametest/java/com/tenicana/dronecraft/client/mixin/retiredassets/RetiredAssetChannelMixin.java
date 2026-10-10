package com.tenicana.dronecraft.client.mixin.retiredassets;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.sound.ManagerAssetRetiredDeliveryCapture;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Mixin;

/** Observes the original body, including an ownership rejection; never cancels it. */
@Mixin(Channel.class)
abstract class RetiredAssetChannelMixin {
    @WrapMethod(method="attachBufferStream")
    private void retiredAsset$attach(AudioStream stream,Operation<Void> original) {
        if(!ManagerAssetRetiredDeliveryCapture.requested()){original.call(stream);return;}
        ManagerAssetRetiredDeliveryCapture.channelEvent("attach_head",(Channel)(Object)this,stream);
        original.call(stream);
        ManagerAssetRetiredDeliveryCapture.channelEvent("attach_return",(Channel)(Object)this,stream);
    }
    @WrapMethod(method="play")
    private void retiredAsset$play(Operation<Void> original) {
        if(!ManagerAssetRetiredDeliveryCapture.requested()){original.call();return;}
        ManagerAssetRetiredDeliveryCapture.channelEvent("play_head",(Channel)(Object)this,null);
        original.call();
        ManagerAssetRetiredDeliveryCapture.channelEvent("play_return",(Channel)(Object)this,null);
    }
    @WrapMethod(method="stop")
    private void retiredAsset$stop(Operation<Void> original) {
        if(!ManagerAssetRetiredDeliveryCapture.requested()){original.call();return;}
        ManagerAssetRetiredDeliveryCapture.channelEvent("stop_head",(Channel)(Object)this,null);
        original.call();
        ManagerAssetRetiredDeliveryCapture.channelEvent("stop_return",(Channel)(Object)this,null);
    }
    @WrapMethod(method="destroy")
    private void retiredAsset$destroy(Operation<Void> original) {
        if(!ManagerAssetRetiredDeliveryCapture.requested()){original.call();return;}
        ManagerAssetRetiredDeliveryCapture.channelEvent("destroy_head",(Channel)(Object)this,null);
        original.call();
        ManagerAssetRetiredDeliveryCapture.channelEvent("destroy_return",(Channel)(Object)this,null);
    }
}
