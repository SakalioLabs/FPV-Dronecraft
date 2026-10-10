package com.tenicana.dronecraft.client.mixin.resetassets;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.sound.ManagerAssetResetDeliveryCapture;
import net.minecraft.client.sounds.*;
import org.spongepowered.asm.mixin.Mixin;
/** Pure passthrough observers of the two pinned original future consumers. */
@Mixin(value=SoundEngine.class,remap=false)
abstract class ResetAssetConsumerMixin {
    @WrapMethod(method="method_19758(Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;Lnet/minecraft/client/sounds/AudioStream;)V",remap=false)
    private static void resetAsset$delivery(ChannelAccess.ChannelHandle handle,AudioStream stream,Operation<Void> original) {
        if(!ManagerAssetResetDeliveryCapture.requested()){original.call(handle,stream);return;}
        ManagerAssetResetDeliveryCapture.deliveryConsumer("delivery_consumer_head",handle,stream,null);
        original.call(handle,stream);
        ManagerAssetResetDeliveryCapture.deliveryConsumer("delivery_consumer_return",handle,stream,null);
    }
    @WrapMethod(method="method_19755(Lnet/minecraft/client/sounds/AudioStream;Lcom/mojang/blaze3d/audio/Channel;)V",remap=false)
    private static void resetAsset$nativeConsumer(AudioStream stream,Channel channel,Operation<Void> original) {
        if(!ManagerAssetResetDeliveryCapture.requested()){original.call(stream,channel);return;}
        ManagerAssetResetDeliveryCapture.deliveryConsumer("native_consumer_head",null,stream,channel);
        original.call(stream,channel);
        ManagerAssetResetDeliveryCapture.deliveryConsumer("native_consumer_return",null,stream,channel);
    }
}
