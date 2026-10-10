package com.tenicana.dronecraft.client.mixin.resetassets;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.sound.ManagerAssetResetDeliveryCapture;
import net.minecraft.client.sounds.ChannelAccess;
import org.spongepowered.asm.mixin.Mixin;
import java.util.function.Consumer;
@Mixin(value=ChannelAccess.ChannelHandle.class,remap=false)
abstract class ResetAssetHandleMixin {
    @WrapMethod(method="execute",remap=false)
    private void resetAsset$submit(Consumer<Channel> consumer,Operation<Void> original) {
        if(!ManagerAssetResetDeliveryCapture.requested()){original.call(consumer);return;}
        var self=(ChannelAccess.ChannelHandle)(Object)this;
        ManagerAssetResetDeliveryCapture.handleConsumer("handle_execute_head",self,consumer);
        original.call(consumer);
        ManagerAssetResetDeliveryCapture.handleConsumer("handle_execute_return",self,consumer);
    }
    @WrapMethod(method="method_19737(Ljava/util/function/Consumer;)V",remap=false)
    private void resetAsset$queued(Consumer<Channel> consumer,Operation<Void> original) {
        if(!ManagerAssetResetDeliveryCapture.requested()){original.call(consumer);return;}
        var self=(ChannelAccess.ChannelHandle)(Object)this;
        ManagerAssetResetDeliveryCapture.handleConsumer("handle_task_head",self,consumer);
        original.call(consumer);
        ManagerAssetResetDeliveryCapture.handleConsumer("handle_task_return",self,consumer);
    }
}
