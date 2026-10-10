package com.tenicana.dronecraft.client.mixin.wet;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.tenicana.dronecraft.client.sound.AssetNativeCapture;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(SoundEngine.class)
abstract class AssetNativeVolumeMixin {
    @WrapOperation(method="play",at=@At(value="INVOKE",target="Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float asset$scalar(SoundEngine engine,float input,SoundSource source,Operation<Float> original,@Local(argsOnly=true) SoundInstance sound) {
        float result=original.call(engine,input,source);AssetNativeCapture.scalar(engine,sound,input,source,result);return result;
    }
}
