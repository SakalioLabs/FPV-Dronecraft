package com.tenicana.dronecraft.client.mixin.lateassets;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.sound.ManagerAssetLateDeliveryCapture;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Mixin;

/** Observes the original body, including an ownership rejection; never cancels it. */
@Mixin(Channel.class)
abstract class LateAssetChannelMixin {
    @WrapMethod(method="attachBufferStream")
    private void lateAsset$attach(AudioStream stream,Operation<Void> original) {
        if(!ManagerAssetLateDeliveryCapture.requested()){original.call(stream);return;}
        ManagerAssetLateDeliveryCapture.channelEvent("attach_head",(Channel)(Object)this,stream);
        original.call(stream);
        ManagerAssetLateDeliveryCapture.channelEvent("attach_return",(Channel)(Object)this,stream);
    }
    @WrapMethod(method="play")
    private void lateAsset$play(Operation<Void> original) {
        if(!ManagerAssetLateDeliveryCapture.requested()){original.call();return;}
        ManagerAssetLateDeliveryCapture.channelEvent("play_head",(Channel)(Object)this,null);
        original.call();
        ManagerAssetLateDeliveryCapture.channelEvent("play_return",(Channel)(Object)this,null);
    }
    @WrapMethod(method="stop")
    private void lateAsset$stop(Operation<Void> original) {
        if(!ManagerAssetLateDeliveryCapture.requested()){original.call();return;}
        ManagerAssetLateDeliveryCapture.channelEvent("stop_head",(Channel)(Object)this,null);
        original.call();
        ManagerAssetLateDeliveryCapture.channelEvent("stop_return",(Channel)(Object)this,null);
    }
    @WrapMethod(method="destroy")
    private void lateAsset$destroy(Operation<Void> original) {
        if(!ManagerAssetLateDeliveryCapture.requested()){original.call();return;}
        ManagerAssetLateDeliveryCapture.channelEvent("destroy_head",(Channel)(Object)this,null);
        original.call();
        ManagerAssetLateDeliveryCapture.channelEvent("destroy_return",(Channel)(Object)this,null);
    }
}
