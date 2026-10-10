package com.tenicana.dronecraft.client.render;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.entity.RotorLayoutCodec;
/** Minecraft 1.21.1 draws the imported model directly instead of submitting render states. */
public class DroneEntityRenderer extends EntityRenderer<DroneEntity> {
 private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID,"textures/entity/drone.png");
 private final DroneEntityModel model;
 private final RotorLayoutRenderCache rotorLayouts=new RotorLayoutRenderCache();
 private final DroneEntityRenderState state=new DroneEntityRenderState();
 public DroneEntityRenderer(EntityRendererProvider.Context context) { super(context);model=new DroneEntityModel(context.bakeLayer(DroneModelLayers.DRONE));shadowRadius=0.28f; }
 @Override public ResourceLocation getTextureLocation(DroneEntity entity) { return TEXTURE; }
 @Override public void render(DroneEntity entity,float entityYaw,float tickProgress,PoseStack poses,MultiBufferSource buffers,int light) {
  if (DroneClientState.isFpvActive(entity.level())&&DroneClientState.controlledDrone()==entity) return;
  state.ageInTicks=entity.tickCount+tickProgress;
  state.pitchRadians=entity.getInterpolatedRenderPitchRadians(tickProgress);
  state.yawRadians=entity.getInterpolatedRenderYawRadians(tickProgress);
  state.rollRadians=entity.getInterpolatedRenderRollRadians(tickProgress);
  state.motorPower=entity.getMotorPower();
  RotorLayoutRenderCache.Snapshot layout=rotorLayouts.get(entity.getRotorLayout());state.rotorCount=layout.rotorCount();
  for (int i=0;i<RotorLayoutCodec.MAX_RENDER_ROTORS;i++) {
   state.rotorRpm[i]=i<entity.getRotorCount()?entity.getMotorRpm(i):0.0f;
   state.rotorXModelUnits[i]=layout.xModelUnits(i);state.rotorYModelUnits[i]=layout.yModelUnits(i);state.rotorZModelUnits[i]=layout.zModelUnits(i);state.rotorSpinDirection[i]=layout.spinDirection(i);
  }
  state.armed=entity.isArmed();model.setupAnim(state);
  poses.pushPose();poses.scale(-1,-1,1);poses.translate(0,-1.501,0);
  model.renderToBuffer(poses,buffers.getBuffer(model.renderType(TEXTURE)),light,OverlayTexture.NO_OVERLAY,-1);
  poses.popPose();super.render(entity,entityYaw,tickProgress,poses,buffers,light);
 }
}
