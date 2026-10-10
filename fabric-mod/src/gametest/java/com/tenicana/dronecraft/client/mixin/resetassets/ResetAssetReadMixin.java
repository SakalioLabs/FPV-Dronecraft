package com.tenicana.dronecraft.client.mixin.resetassets;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenicana.dronecraft.client.sound.ManagerAssetResetDeliveryCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.ByteBuffer;

@Mixin(targets="com.tenicana.dronecraft.client.sound.CalibratedAssetAudioStream",remap=false)
abstract class ResetAssetReadMixin {
    @WrapOperation(method="read",at=@At(value="INVOKE",target="Lcom/tenicana/dronecraft/client/sound/VerifiedAssetPcm$Cursor;read(ID)Ljava/nio/ByteBuffer;",remap=false),remap=false)
    private ByteBuffer resetAsset$actualCursor(@Coerce Object cursor,int bytes,double amplitude,Operation<ByteBuffer> original) {
        if(!ManagerAssetResetDeliveryCapture.requested())return original.call(cursor,bytes,amplitude);
        var token=ManagerAssetResetDeliveryCapture.beforeCursorRead(this,cursor,bytes,amplitude);
        try {ByteBuffer result=original.call(cursor,bytes,amplitude);ManagerAssetResetDeliveryCapture.afterCursorRead(token,result,null);return result;}
        catch(RuntimeException|Error failure){ManagerAssetResetDeliveryCapture.afterCursorRead(token,null,failure);throw failure;}
    }
    @Inject(method="close",at=@At("HEAD"),remap=false)
    private void resetAsset$closeHead(CallbackInfo ci) {
        if(ManagerAssetResetDeliveryCapture.requested())ManagerAssetResetDeliveryCapture.streamClose("stream_close_head",this);
    }
    @Inject(method="close",at=@At("RETURN"),remap=false)
    private void resetAsset$closeReturn(CallbackInfo ci) {
        if(ManagerAssetResetDeliveryCapture.requested())ManagerAssetResetDeliveryCapture.streamClose("stream_close_return",this);
    }
}
