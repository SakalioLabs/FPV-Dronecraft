package com.tenicana.dronecraft.client.mixin;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.tenicana.dronecraft.camera.FpvCameraMount;
import com.tenicana.dronecraft.camera.FpvCameraOrientation;
import com.tenicana.dronecraft.camera.DroneThirdPersonCamera;
import com.tenicana.dronecraft.client.camera.ClientCameraSafety;
import com.tenicana.dronecraft.client.camera.CameraShake;
import com.tenicana.dronecraft.client.camera.CameraAcousticOwnerAccess;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.client.config.DroneClientConfig;
import com.tenicana.dronecraft.client.control.DroneClientControls;
import com.tenicana.dronecraft.camera.FpvCameraPoseDelay;
import com.tenicana.dronecraft.camera.FpvCameraVibration;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.sim.RotorSpec;

@Mixin(Camera.class)
public abstract class CameraMixin implements CameraAcousticOwnerAccess {
	@Unique
	private Entity fpvdrone$acousticVelocityOwner;
	@Unique
	private int fpvdrone$acousticViewMode;

	@Override
	public Entity fpvdrone$acousticVelocityOwner() {
		return fpvdrone$acousticVelocityOwner;
	}

	@Override
	public int fpvdrone$acousticViewMode() {
		return fpvdrone$acousticViewMode;
	}

	private static final float RPM_TO_RADIANS_PER_TICK = (float) (Math.PI * 2.0 / 1200.0);
	private static final FpvCameraPoseDelay FPV_POSE_DELAY = new FpvCameraPoseDelay();
	private static int delayedDroneId = -1;
	private static Level delayedLevel;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yawDegrees, float pitchDegrees);

	@Shadow
	private float getMaxZoom(float distanceMeters) {
		throw new AssertionError("Shadow method");
	}

	@Shadow
	private boolean detached;

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

	@Shadow
	private float xRot;

	@Shadow
	private float yRot;

	@Shadow private Level level;
	@Shadow private Entity entity;
	@Shadow private int matrixPropertiesDirty;
	@Shadow public abstract float getCameraEntityPartialTicks(DeltaTracker delta);

	// Change the pose before native FOV, culling and projection extraction observe it.
	@Inject(method = "update", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
	private void fpvdrone$setupFpvCamera(DeltaTracker delta, CallbackInfo ci) {
		float partialTick = getCameraEntityPartialTicks(delta);
		boolean thirdPersonReverse = Minecraft.getInstance().options.getCameraType().isMirrored();
		fpvdrone$acousticVelocityOwner = entity;
		fpvdrone$acousticViewMode = (detached ? 1 : 0) | (thirdPersonReverse ? 2 : 0);
		DroneEntity drone = DroneClientState.controlledDrone();
		if (!ClientCameraSafety.isUsableFpvDroneReference(
				DroneClientState.isDroneCameraActive(level),
				drone != null,
				drone != null && drone.level() == level,
				drone != null && drone.isRemoved(),
				drone != null && drone.isAlive()
		)) {
			resetCameraDelay();
			return;
		}

		if (DroneClientState.isThirdPersonActive(level)) {
			resetCameraDelay();
			fpvdrone$setupThirdPersonCamera(drone, partialTick);
			return;
		}
		if (!DroneClientState.isFpvActive(level)) {
			resetCameraDelay();
			return;
		}

		DroneClientConfig config = DroneClientControls.config();
		if (ClientCameraSafety.shouldResetFpvPoseDelay(drone.getId(), delayedDroneId, level, delayedLevel)) {
			FPV_POSE_DELAY.reset();
			delayedDroneId = drone.getId();
			delayedLevel = level;
		}

		Vec3 rawPosition = drone.getPosition(partialTick).add(0.0, drone.getPhysicsCenterYOffsetMeters(), 0.0);
		double sampleTimeSeconds = Math.max(0.0, (drone.tickCount + partialTick) / 20.0);
		FpvCameraPoseDelay.Pose delayedPose = FPV_POSE_DELAY.sample(
				new FpvCameraPoseDelay.Pose(
						rawPosition.x(),
						rawPosition.y(),
						rawPosition.z(),
						(float) Math.toDegrees(drone.getInterpolatedRenderYawRadians(partialTick)),
						(float) Math.toDegrees(drone.getInterpolatedRenderPitchRadians(partialTick)),
						drone.getInterpolatedRenderRollRadians(partialTick),
						sampleTimeSeconds
				),
				config.cameraLatencySeconds()
		);

		float pitchDegrees = delayedPose.pitchDegrees();
		float yawDegrees = delayedPose.yawDegrees();
		float rollRadians = delayedPose.rollRadians();
		CameraShake shake = cameraShake(drone, partialTick, config);
		FpvCameraOrientation.Orientation cameraOrientation = FpvCameraOrientation.fromFpvMount(
				yawDegrees,
				pitchDegrees,
				rollRadians,
				config.cameraTiltDegrees(),
				shake.pitchDegrees(),
				shake.yawDegrees(),
				shake.rollRadians()
		);
		FpvCameraOrientation.Orientation bodyOrientation = FpvCameraOrientation.fromCameraAngles(yawDegrees, pitchDegrees, rollRadians);
		float clearForwardOffsetMeters = (float) FpvCameraMount.clearForwardOffset(config.cameraForwardOffsetMeters(), shake.forwardMeters());
		float clearUpOffsetMeters = (float) FpvCameraMount.clearUpOffset(config.cameraUpOffsetMeters(), shake.verticalMeters());
		Vector3f cameraOffset = new Vector3f(
				shake.lateralMeters(),
				clearUpOffsetMeters,
				-clearForwardOffsetMeters
		).rotate(bodyOrientation.rotation());
		Vec3 cameraOrigin = new Vec3(delayedPose.xMeters(), delayedPose.yMeters(), delayedPose.zMeters());
		Vec3 desiredPosition = cameraOrigin.add(cameraOffset.x(), cameraOffset.y(), cameraOffset.z());

		setPosition(desiredPosition);
		this.detached = false;
		fpvdrone$acousticVelocityOwner = drone;
		fpvdrone$acousticViewMode = 4;
		xRot = Mth.wrapDegrees(cameraOrientation.pitchDegrees());
		yRot = Mth.wrapDegrees(cameraOrientation.yawDegrees());
		rotation.set(cameraOrientation.rotation());
		forwards.set(cameraOrientation.forwards());
		up.set(cameraOrientation.up());
		left.set(cameraOrientation.left());
		matrixPropertiesDirty |= 3; // DIRTY_VIEW_ROT | DIRTY_VIEW_ROT_PROJ
	}

	@Unique
	private void fpvdrone$setupThirdPersonCamera(DroneEntity drone, float partialTick) {
		Vec3 center = drone.getPosition(partialTick).add(0.0, drone.getPhysicsCenterYOffsetMeters(), 0.0);
		float yawRadians = drone.getInterpolatedRenderYawRadians(partialTick);
		DroneThirdPersonCamera.Pose pose = DroneThirdPersonCamera.fromDroneCenter(
				center.x(), center.y(), center.z(), yawRadians, DroneThirdPersonCamera.MAX_BOOM_LENGTH_METERS);
		setRotation(pose.yawDegrees(), pose.pitchDegrees());
		setPosition(center);
		// Native zoom checks eight 0.1 m corner rays against local visual block shapes.
		double clearDistance = DroneThirdPersonCamera.clearBoomLength(
				getMaxZoom((float) DroneThirdPersonCamera.MAX_BOOM_LENGTH_METERS));
		if (clearDistance < DroneThirdPersonCamera.MAX_BOOM_LENGTH_METERS) {
			pose = DroneThirdPersonCamera.fromDroneCenter(center.x(), center.y(), center.z(), yawRadians, clearDistance);
		}
		setPosition(new Vec3(pose.xMeters(), pose.yMeters(), pose.zMeters()));
		this.detached = true;
		fpvdrone$acousticVelocityOwner = drone;
		fpvdrone$acousticViewMode = 8 | 1;
	}

	private static void resetCameraDelay() {
		FPV_POSE_DELAY.reset();
		delayedDroneId = -1;
		delayedLevel = null;
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

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void fpvdrone$overrideFpvFov(float partialTick,
			CallbackInfoReturnable<Float> cir) {
		DroneEntity drone = DroneClientState.controlledDrone();
		Minecraft client = Minecraft.getInstance();
		if (DroneClientState.isThirdPersonActive(client.level)) {
			cir.setReturnValue(DroneThirdPersonCamera.FOV_DEGREES);
			return;
		}
		if (!ClientCameraSafety.isUsableFpvDroneReference(
				DroneClientState.isFpvActive(client.level),
				drone != null,
				drone != null && drone.level() == client.level,
				drone != null && drone.isRemoved(),
				drone != null && drone.isAlive()
		)) {
			return;
		}

		DroneClientConfig config = DroneClientControls.config();
		float speed = Math.max(drone.getAirspeedMetersPerSecond(), drone.getSpeedMetersPerSecond());
		float speedStretch = Mth.clamp((speed - 2.0f) / 22.0f, 0.0f, 1.0f);
		float throttleStretch = Mth.clamp((drone.getMotorPower() - 0.25f) / 0.65f, 0.0f, 1.0f);
		float dynamicFov = config.cameraDynamicFovDegrees() * (0.70f * speedStretch + 0.30f * throttleStretch);
		cir.setReturnValue(Mth.clamp(config.cameraFovDegrees() + dynamicFov, 70.0f, 140.0f));
	}
}
