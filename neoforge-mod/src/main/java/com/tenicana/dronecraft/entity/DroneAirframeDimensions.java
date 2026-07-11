package com.tenicana.dronecraft.entity;

import net.minecraft.world.entity.EntityDimensions;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.MathUtil;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

public final class DroneAirframeDimensions {
	private static final float MIN_COLLISION_WIDTH_METERS = 0.85f;
	private static final float MAX_COLLISION_WIDTH_METERS = 2.20f;
	private static final float COLLISION_HEIGHT_METERS = 0.35f;
	private static final float COLLISION_EYE_HEIGHT_METERS = 0.25f;
	private static final double COLLISION_ARM_MARGIN_METERS = 0.08;

	private DroneAirframeDimensions() {
	}

	public static EntityDimensions forConfig(DroneConfig config) {
		double footprintRadius = MIN_COLLISION_WIDTH_METERS * 0.5;
		for (RotorSpec rotor : config.rotors()) {
			Vec3 position = rotor.positionBodyMeters();
			footprintRadius = Math.max(
					footprintRadius,
					Math.hypot(position.x(), position.z()) + rotor.radiusMeters() + COLLISION_ARM_MARGIN_METERS
			);
		}
		float width = (float) MathUtil.clamp(footprintRadius * 2.0, MIN_COLLISION_WIDTH_METERS, MAX_COLLISION_WIDTH_METERS);
		return EntityDimensions.fixed(width, COLLISION_HEIGHT_METERS).withEyeHeight(COLLISION_EYE_HEIGHT_METERS);
	}
}
