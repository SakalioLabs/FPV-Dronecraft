package com.tenicana.dronecraft.client.mixin.wet;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.tenicana.dronecraft.client.sound.AssetNativeCapture;
import java.nio.ByteBuffer;
import java.util.function.DoubleSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="com.tenicana.dronecraft.client.sound.CalibratedAssetAudioStream")
abstract class AssetNativeReadMixin {
    @WrapOperation(method="read",at=@At(value="INVOKE",target="Ljava/util/function/DoubleSupplier;getAsDouble()D"))
    private double asset$coefficient(DoubleSupplier supplier,Operation<Double> original) {double c=original.call(supplier);AssetNativeCapture.coefficient(c);return c;}
    @WrapMethod(method="read")
    private ByteBuffer asset$read(int count,Operation<ByteBuffer> original) {ByteBuffer bytes=original.call(count);AssetNativeCapture.read(this,bytes);return bytes;}
}
