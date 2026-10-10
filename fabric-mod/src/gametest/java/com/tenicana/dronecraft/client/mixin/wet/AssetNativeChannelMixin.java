package com.tenicana.dronecraft.client.mixin.wet;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenicana.dronecraft.client.sound.AssetNativeCapture;
import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Channel.class)
abstract class AssetNativeChannelMixin {
    @WrapMethod(method="setVolume")
    private void asset$gain(float value,Operation<Void> original) {var r=AssetNativeCapture.beforeGain(this,value);original.call(value);AssetNativeCapture.afterGain(this,r);}
    @Inject(method="play",at=@At("HEAD"))
    private void asset$play(CallbackInfo ci) {AssetNativeCapture.play(this);}
}
