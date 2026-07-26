package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticSourceFrame;
import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.DeterministicRotorPhase;
import com.tenicana.dronecraft.acoustics.RotorAcousticState;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.entity.RotorLayoutCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

final class DroneAcousticSourceMapper {
	private static final double TICK_RATE_HZ = 20.0;

	private DroneAcousticSourceMapper() {
	}

	static AcousticSourceFrame map(DroneEntity drone) {
		int rotorCount = drone.getRotorCount();
		List<RotorAcousticState> rotors = new ArrayList<>(rotorCount);
		double load = clamp(drone.getRotorAerodynamicLoadFactor(), 0.0, 2.0);
		double radius = clamp(drone.getRotorRadiusMeters(), 0.005, 1.0);
		int bladeCount = clamp(drone.getRotorBladeCount(), 1, 16);
		int polePairs = clamp(drone.getMotorPolePairs(), 1, 64);
		RotorLayoutCodec.Layout layout = RotorLayoutCodec.decode(drone.getRotorLayout());
		boolean layoutMatchesTelemetry = layout.rotorCount() == rotorCount;
		for (int index = 0; index < rotorCount; index++) {
			rotors.add(new RotorAcousticState(
					clamp(drone.getMotorRpm(index), 0.0, 200_000.0),
					clamp(drone.getMotorPower(index), 0.0, 1.0),
					load,
					radius,
					bladeCount,
					polePairs,
					layoutMatchesTelemetry
							? layout.spinDirection(index)
							: fallbackSpinDirection(index),
					DeterministicRotorPhase.phaseRadians(drone.getId(), index)
			));
		}

		Vec3 velocityPerTick = drone.getDeltaMovement();
		return new AcousticSourceFrame(
				drone.getId(),
				Math.max(0L, drone.tickCount) * 50_000_000L,
				new AcousticVector(drone.getX(), drone.getY() + drone.getBbHeight() * 0.5, drone.getZ()),
				new AcousticVector(
						velocityPerTick.x * TICK_RATE_HZ,
						velocityPerTick.y * TICK_RATE_HZ,
						velocityPerTick.z * TICK_RATE_HZ
				),
				diskNormal(
						drone.getRenderPitchRadians(),
						drone.getRenderYawRadians(),
						drone.getRenderRollRadians()
				),
				clamp(drone.getAcousticApertureRadiusMeters(), 0.005, 5.0),
				rotors
		);
	}

	static AcousticListenerFrame mapListener(Entity listener) {
		Vec3 position = listener.getEyePosition();
		Vec3 velocityPerTick = listener.getDeltaMovement();
		return new AcousticListenerFrame(
				new AcousticVector(position.x, position.y, position.z),
				new AcousticVector(
						velocityPerTick.x * TICK_RATE_HZ,
						velocityPerTick.y * TICK_RATE_HZ,
						velocityPerTick.z * TICK_RATE_HZ
				)
		);
	}

	static AcousticVector diskNormal(double pitch, double yaw, double roll) {
		double sinPitch = Math.sin(pitch);
		double cosPitch = Math.cos(pitch);
		double sinYaw = Math.sin(yaw);
		double cosYaw = Math.cos(yaw);
		double sinRoll = Math.sin(roll);
		double cosRoll = Math.cos(roll);
		return new AcousticVector(
				-cosYaw * sinRoll + sinYaw * sinPitch * cosRoll,
				cosPitch * cosRoll,
				sinYaw * sinRoll + cosYaw * sinPitch * cosRoll
		).normalized();
	}

	private static double clamp(double value, double minimum, double maximum) {
		if (!Double.isFinite(value)) {
			return minimum;
		}
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static int fallbackSpinDirection(int rotorIndex) {
		return rotorIndex % 2 == 0 ? 1 : -1;
	}
}
