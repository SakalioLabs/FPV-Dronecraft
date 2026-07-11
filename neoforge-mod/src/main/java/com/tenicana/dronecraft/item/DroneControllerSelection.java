package com.tenicana.dronecraft.item;

import java.util.Comparator;
import java.util.UUID;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.tenicana.dronecraft.entity.DroneEntity;

public final class DroneControllerSelection {
	private DroneControllerSelection() {
	}

	public static DroneEntity nearestReusableOwnedDrone(Level level, UUID owner, Vec3 origin, double radiusMeters) {
		if (level == null || owner == null || origin == null || !Double.isFinite(radiusMeters) || radiusMeters <= 0.0) {
			return null;
		}
		AABB search = new AABB(origin, origin).inflate(radiusMeters);
		return level.getEntitiesOfClass(
						DroneEntity.class,
						search,
						drone -> drone.isAlive() && drone.isOwnedBy(owner)
				)
				.stream()
				.min(Comparator.comparingDouble(drone -> drone.position().distanceToSqr(origin)))
				.orElse(null);
	}
}
