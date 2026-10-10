package com.tenicana.dronecraft.client.mixin.retiredcleanassets;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.sound.ManagerAssetRetiredCleanDeliveryCapture;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Mixin;

/** Observes the original body, including an ownership rejection; never cancels it. */
@Mixin(Channel.class)
abstract class RetiredCleanAssetChannelMixin {
    @WrapMethod(method="linearAttenuation")
    private void retiredCleanAsset$linearAttenuation(float argument,Operation<Void> original) {
        if(!ManagerAssetRetiredCleanDeliveryCapture.requested()){original.call(argument);return;}
        ManagerAssetRetiredCleanDeliveryCapture.nativeParameter("linear_attenuation_head",(Channel)(Object)this,argument);
        original.call(argument);
        ManagerAssetRetiredCleanDeliveryCapture.nativeParameter("linear_attenuation_return",(Channel)(Object)this,argument);
    }
    @WrapMethod(method="setVolume")
    private void retiredCleanAsset$setVolume(float argument,Operation<Void> original) {
        if(!ManagerAssetRetiredCleanDeliveryCapture.requested()){original.call(argument);return;}
        ManagerAssetRetiredCleanDeliveryCapture.nativeParameter("set_volume_head",(Channel)(Object)this,argument);
        original.call(argument);
        ManagerAssetRetiredCleanDeliveryCapture.nativeParameter("set_volume_return",(Channel)(Object)this,argument);
    }
    @WrapMethod(method="setPitch")
    private void retiredCleanAsset$setPitch(float argument,Operation<Void> original) {
        if(!ManagerAssetRetiredCleanDeliveryCapture.requested()){original.call(argument);return;}
        ManagerAssetRetiredCleanDeliveryCapture.nativeParameter("set_pitch_head",(Channel)(Object)this,argument);
        original.call(argument);
        ManagerAssetRetiredCleanDeliveryCapture.nativeParameter("set_pitch_return",(Channel)(Object)this,argument);
    }
    @WrapMethod(method="attachBufferStream")
    private void retiredCleanAsset$attach(AudioStream stream,Operation<Void> original) {
        if(!ManagerAssetRetiredCleanDeliveryCapture.requested()){original.call(stream);return;}
        ManagerAssetRetiredCleanDeliveryCapture.channelEvent("attach_head",(Channel)(Object)this,stream);
        original.call(stream);
        ManagerAssetRetiredCleanDeliveryCapture.channelEvent("attach_return",(Channel)(Object)this,stream);
    }
    @WrapMethod(method="play")
    private void retiredCleanAsset$play(Operation<Void> original) {
        if(!ManagerAssetRetiredCleanDeliveryCapture.requested()){original.call();return;}
        ManagerAssetRetiredCleanDeliveryCapture.channelEvent("play_head",(Channel)(Object)this,null);
        original.call();
        ManagerAssetRetiredCleanDeliveryCapture.channelEvent("play_return",(Channel)(Object)this,null);
    }
    @WrapMethod(method="stop")
    private void retiredCleanAsset$stop(Operation<Void> original) {
        if(!ManagerAssetRetiredCleanDeliveryCapture.requested()){original.call();return;}
        ManagerAssetRetiredCleanDeliveryCapture.channelEvent("stop_head",(Channel)(Object)this,null);
        original.call();
        ManagerAssetRetiredCleanDeliveryCapture.channelEvent("stop_return",(Channel)(Object)this,null);
    }
    @WrapMethod(method="destroy")
    private void retiredCleanAsset$destroy(Operation<Void> original) {
        if(!ManagerAssetRetiredCleanDeliveryCapture.requested()){original.call();return;}
        ManagerAssetRetiredCleanDeliveryCapture.channelEvent("destroy_head",(Channel)(Object)this,null);
        original.call();
        ManagerAssetRetiredCleanDeliveryCapture.channelEvent("destroy_return",(Channel)(Object)this,null);
    }
}
