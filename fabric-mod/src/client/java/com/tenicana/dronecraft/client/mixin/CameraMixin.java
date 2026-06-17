package com.tenicana.dronecraft.client.mixin;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.tenicana.dronecraft.camera.FpvCameraMount;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.client.config.DroneClientConfig;
import com.tenicana.dronecraft.client.control.DroneClientControls;
import com.tenicana.dronecraft.camera.FpvCameraPoseDelay;
import com.tenicana.dronecraft.camera.FpvCameraVibration;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.sim.RotorSpec;

@Mixin(Camera.class)
public abstract class CameraMixin {
	private static final float RPM_TO_RADIANS_PER_TICK = (float) (Math.PI * 2.0 / 1200.0);
	private static final FpvCameraPoseDelay FPV_POSE_DELAY = new FpvCameraPoseDelay();
	private static int delayedDroneId = -1;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	@Final
	private Quaternionf rotation;

	@Shadow
	@Final
	private Vector3f forwards;

	@Shadow
	@Final
	private Vector3f up;

	@Shadow
	@Final
	private Vector3f left;

	@Inject(method = "setup", at = @At("RETURN"))
	private void fpvdrone$setupFpvCamera(Level level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
		DroneEntity drone = DroneClientState.controlledDrone();
		if (!DroneClientState.isFpvActive() || drone == null) {
			resetCameraDelay();
			return;
		}

		DroneClientConfig config = DroneClientControls.config();
		if (drone.getId() != delayedDroneId) {
			FPV_POSE_DELAY.reset();
			delayedDroneId = drone.getId();
		}

		Vec3 rawPosition = drone.getPosition(partialTick).add(0.0, drone.getPhysicsCenterYOffsetMeters(), 0.0);
		double sampleTimeSeconds = Math.max(0.0, (drone.tickCount + partialTick) / 20.0);
		FpvCameraPoseDelay.Pose delayedPose = FPV_POSE_DELAY.sample(
				new FpvCameraPoseDelay.Pose(
						rawPosition.x(),
						rawPosition.y(),
						rawPosition.z(),
						(float) Math.toDegrees(drone.getRenderYawRadians()),
						(float) Math.toDegrees(drone.getRenderPitchRadians()),
						drone.getRenderRollRadians(),
						sampleTimeSeconds
				),
				config.cameraLatencySeconds()
		);

		float pitchDegrees = delayedPose.pitchDegrees();
		float yawDegrees = delayedPose.yawDegrees();
		float rollRadians = delayedPose.rollRadians();
		CameraShake shake = cameraShake(drone, partialTick, config);
		float cameraPitchDegrees = pitchDegrees - config.cameraTiltDegrees() + shake.pitchDegrees();
		float cameraYawDegrees = yawDegrees + shake.yawDegrees();
		float cameraRollRadians = rollRadians + shake.rollRadians();
		Quaternionf bodyRotation = new Quaternionf().rotationYXZ(
				(float) Math.PI - yawDegrees * Mth.DEG_TO_RAD,
				-pitchDegrees * Mth.DEG_TO_RAD,
				-rollRadians
		);
		float clearForwardOffsetMeters = (float) FpvCameraMount.clearForwardOffset(config.cameraForwardOffsetMeters(), shake.forwardMeters());
		float clearUpOffsetMeters = (float) FpvCameraMount.clearUpOffset(config.cameraUpOffsetMeters(), shake.verticalMeters());
		Vector3f cameraOffset = new Vector3f(
				shake.lateralMeters(),
				clearUpOffsetMeters,
				-clearForwardOffsetMeters
		).rotate(bodyRotation);
		Vec3 cameraOrigin = new Vec3(delayedPose.xMeters(), delayedPose.yMeters(), delayedPose.zMeters());
		Vec3 desiredPosition = cameraOrigin.add(cameraOffset.x(), cameraOffset.y(), cameraOffset.z());
		Vec3 position = collisionAdjustedCameraPosition(level, drone, cameraOrigin, desiredPosition);

		setPosition(position);
		setRotation(cameraYawDegrees, cameraPitchDegrees);

		rotation.rotationYXZ(
				(float) Math.PI - cameraYawDegrees * Mth.DEG_TO_RAD,
				-cameraPitchDegrees * Mth.DEG_TO_RAD,
				-cameraRollRadians
		);
		new Vector3f(0.0f, 0.0f, -1.0f).rotate(rotation, forwards);
		new Vector3f(0.0f, 1.0f, 0.0f).rotate(rotation, up);
		new Vector3f(-1.0f, 0.0f, 0.0f).rotate(rotation, left);
	}

	private static void resetCameraDelay() {
		FPV_POSE_DELAY.reset();
		delayedDroneId = -1;
	}

	private static Vec3 collisionAdjustedCameraPosition(Level level, Entity entity, Vec3 origin, Vec3 desiredPosition) {
		if (level == null || origin.distanceToSqr(desiredPosition) <= 1.0e-8) {
			return desiredPosition;
		}
		HitResult hit = level.clip(new ClipContext(
				origin,
				desiredPosition,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				entity
		));
		if (hit.getType() == HitResult.Type.MISS) {
			return desiredPosition;
		}
		return FpvCameraMount.retreatFromHit(origin, desiredPosition, hit.getLocation(), FpvCameraMount.COLLISION_CLEARANCE_METERS);
	}

	private static CameraShake cameraShake(DroneEntity drone, float partialTick, DroneClientConfig config) {
		float scale = config.cameraVibrationScale();
		if (scale <= 0.0f) {
			return CameraShake.NONE;
		}

		float rotorVibration = Mth.clamp(drone.getRotorVibration(), 0.0f, 1.0f);
		float propwash = Mth.clamp(drone.getPropwashIntensity(), 0.0f, 1.0f);
		float rotorShake = Mth.sqrt(rotorVibration);
		float shake = scale * (rotorShake + 0.35f * propwash);
		if (shake <= 1.0e-4f) {
			return CameraShake.NONE;
		}

		float ageTicks = drone.tickCount + partialTick;
		float averageRpm = Math.max(0.0f, drone.getAverageMotorRpm());
		float motorPhase = ageTicks * averageRpm * RPM_TO_RADIANS_PER_TICK;
		float washPhase = ageTicks * (0.47f + 0.13f * propwash);
		float positionMeters = Mth.clamp(0.018f * shake, 0.0f, 0.045f);
		float angleDegrees = Mth.clamp(1.35f * shake, 0.0f, 3.0f);
		float rpmFactor = Mth.clamp((averageRpm - 1200.0f) / 18000.0f, 0.0f, 1.0f);
		float wideLensFactor = Mth.clamp(config.cameraFovDegrees() / 105.0f, 0.75f, 1.35f);
		float rollingShutter = config.cameraRollingShutterScale() * rotorShake * rpmFactor * wideLensFactor;
		float jelloDegrees = Mth.clamp(0.75f * rollingShutter * (0.55f + 0.45f * propwash), 0.0f, 1.4f);
		float bladePassPhase = FpvCameraVibration.bladePassPhaseRadians(motorPhase, cameraBladeCount(drone), washPhase);

		float lateralMeters = positionMeters * (0.45f * Mth.sin(motorPhase + 1.3f) + 0.25f * Mth.sin(washPhase + 0.2f));
		float verticalMeters = positionMeters * (0.65f * Mth.sin(motorPhase * 1.91f + 2.1f) + 0.20f * Mth.sin(washPhase * 1.7f));
		float forwardMeters = positionMeters * 0.30f * Mth.sin(motorPhase * 0.73f + 0.7f);
		float pitchDegrees = angleDegrees * (0.55f * Mth.sin(motorPhase * 1.37f + 1.1f) + 0.22f * Mth.sin(washPhase + 2.6f));
		float yawDegrees = angleDegrees * 0.28f * Mth.sin(motorPhase * 0.83f + 0.5f);
		float rollRadians = angleDegrees * 0.34f * Mth.DEG_TO_RAD * Mth.sin(motorPhase * 1.19f + 2.4f);
		lateralMeters += positionMeters * 0.18f * rollingShutter * Mth.sin(bladePassPhase + 2.0f);
		verticalMeters += positionMeters * 0.12f * rollingShutter * Mth.sin(bladePassPhase * 1.11f + 0.4f);
		pitchDegrees += jelloDegrees * 0.30f * Mth.sin(bladePassPhase * 1.07f + 0.4f);
		yawDegrees += jelloDegrees * 0.75f * Mth.sin(bladePassPhase + 1.8f);
		rollRadians += jelloDegrees * 0.65f * Mth.DEG_TO_RAD * Mth.sin(bladePassPhase * 0.93f + 2.9f);
		return new CameraShake(lateralMeters, verticalMeters, forwardMeters, pitchDegrees, yawDegrees, rollRadians);
	}

	private static int cameraBladeCount(DroneEntity drone) {
		if (drone == null || drone.config().rotors().isEmpty()) {
			return FpvCameraVibration.sanitizedBladeCount(0);
		}

		double sum = 0.0;
		for (RotorSpec rotor : drone.config().rotors()) {
			sum += rotor.bladeCount();
		}
		return FpvCameraVibration.sanitizedBladeCount((int) Math.round(sum / drone.config().rotors().size()));
	}

	private record CameraShake(
			float lateralMeters,
			float verticalMeters,
			float forwardMeters,
			float pitchDegrees,
			float yawDegrees,
			float rollRadians
	) {
		private static final CameraShake NONE = new CameraShake(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
	}
}
