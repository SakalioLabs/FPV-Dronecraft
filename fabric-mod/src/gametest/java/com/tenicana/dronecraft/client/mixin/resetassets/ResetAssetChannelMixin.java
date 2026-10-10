package com.tenicana.dronecraft.client.mixin.resetassets;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.sound.ManagerAssetResetDeliveryCapture;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Mixin;

/** Observes the original body, including an ownership rejection; never cancels it. */
@Mixin(Channel.class)
abstract class ResetAssetChannelMixin {
    @WrapMethod(method="linearAttenuation")
    private void resetAsset$linearAttenuation(float argument,Operation<Void> original) {
        if(!ManagerAssetResetDeliveryCapture.requested()){original.call(argument);return;}
        ManagerAssetResetDeliveryCapture.nativeParameter("linear_attenuation_head",(Channel)(Object)this,argument);
        original.call(argument);
        ManagerAssetResetDeliveryCapture.nativeParameter("linear_attenuation_return",(Channel)(Object)this,argument);
    }
    @WrapMethod(method="setVolume")
    private void resetAsset$setVolume(float argument,Operation<Void> original) {
        if(!ManagerAssetResetDeliveryCapture.requested()){original.call(argument);return;}
        ManagerAssetResetDeliveryCapture.nativeParameter("set_volume_head",(Channel)(Object)this,argument);
        original.call(argument);
        ManagerAssetResetDeliveryCapture.nativeParameter("set_volume_return",(Channel)(Object)this,argument);
    }
    @WrapMethod(method="setPitch")
    private void resetAsset$setPitch(float argument,Operation<Void> original) {
        if(!ManagerAssetResetDeliveryCapture.requested()){original.call(argument);return;}
        ManagerAssetResetDeliveryCapture.nativeParameter("set_pitch_head",(Channel)(Object)this,argument);
        original.call(argument);
        ManagerAssetResetDeliveryCapture.nativeParameter("set_pitch_return",(Channel)(Object)this,argument);
    }
    @WrapMethod(method="attachBufferStream")
    private void resetAsset$attach(AudioStream stream,Operation<Void> original) {
        if(!ManagerAssetResetDeliveryCapture.requested()){original.call(stream);return;}
        ManagerAssetResetDeliveryCapture.channelEvent("attach_head",(Channel)(Object)this,stream);
        original.call(stream);
        ManagerAssetResetDeliveryCapture.channelEvent("attach_return",(Channel)(Object)this,stream);
    }
    @WrapMethod(method="play")
    private void resetAsset$play(Operation<Void> original) {
        if(!ManagerAssetResetDeliveryCapture.requested()){original.call();return;}
        ManagerAssetResetDeliveryCapture.channelEvent("play_head",(Channel)(Object)this,null);
        original.call();
        ManagerAssetResetDeliveryCapture.channelEvent("play_return",(Channel)(Object)this,null);
    }
    @WrapMethod(method="stop")
    private void resetAsset$stop(Operation<Void> original) {
        if(!ManagerAssetResetDeliveryCapture.requested()){original.call();return;}
        ManagerAssetResetDeliveryCapture.channelEvent("stop_head",(Channel)(Object)this,null);
        original.call();
        ManagerAssetResetDeliveryCapture.channelEvent("stop_return",(Channel)(Object)this,null);
    }
    @WrapMethod(method="destroy")
    private void resetAsset$destroy(Operation<Void> original) {
        if(!ManagerAssetResetDeliveryCapture.requested()){original.call();return;}
        ManagerAssetResetDeliveryCapture.channelEvent("destroy_head",(Channel)(Object)this,null);
        original.call();
        ManagerAssetResetDeliveryCapture.channelEvent("destroy_return",(Channel)(Object)this,null);
    }
}
