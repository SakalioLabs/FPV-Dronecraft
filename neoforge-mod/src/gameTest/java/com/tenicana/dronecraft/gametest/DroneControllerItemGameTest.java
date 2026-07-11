package com.tenicana.dronecraft.gametest;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.item.DroneControllerSelection;
import com.tenicana.dronecraft.registry.DroneEntityTypes;

public final class DroneControllerItemGameTest {
	private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000c001");
	private static final UUID OTHER_OWNER = UUID.fromString("00000000-0000-0000-0000-00000000c002");

	private DroneControllerItemGameTest() {
	}

	public static void controllerReusesNearestOwnedDrone(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		Vec3 origin = Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 5, 1)));
		DroneEntity farOwned = spawnDrone(level, OWNER, origin.add(6.0, 0.0, 0.0));
		DroneEntity nearOwned = spawnDrone(level, OWNER, origin.add(2.0, 0.0, 0.0));
		DroneEntity otherOwned = spawnDrone(level, OTHER_OWNER, origin.add(1.0, 0.0, 0.0));

		context.runAfterDelay(2, () -> {
			DroneEntity reusable = DroneControllerSelection.nearestReusableOwnedDrone(level, OWNER, origin, 8.0);
			assertTrue(reusable == nearOwned, "controller did not reuse the nearest owned drone");
			assertTrue(reusable != otherOwned, "controller selected another player's drone");
			assertTrue(DroneControllerSelection.nearestReusableOwnedDrone(level, OWNER, origin, 1.0) == null, "controller reused a drone outside radius");
			farOwned.discard();
			nearOwned.discard();
			otherOwned.discard();
			context.succeed();
		});
	}

	private static DroneEntity spawnDrone(ServerLevel level, UUID owner, Vec3 position) {
		DroneEntity drone = new DroneEntity(DroneEntityTypes.drone(), level);
		drone.setOwner(owner);
		drone.setPos(position.x(), position.y(), position.z());
		level.addFreshEntity(drone);
		return drone;
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new GameTestAssertException(Component.literal(message), 0);
		}
	}
}
