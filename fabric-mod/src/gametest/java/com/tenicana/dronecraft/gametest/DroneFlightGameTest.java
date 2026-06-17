package com.tenicana.dronecraft.gametest;

import java.util.Locale;
import java.lang.reflect.Method;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.tenicana.dronecraft.control.DroneControlManager;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;

public final class DroneFlightGameTest implements CustomTestMethodInvoker {
	private static final UUID TEST_OWNER = UUID.fromString("00000000-0000-0000-0000-00000000f002");
	private static final UUID RESET_OWNER = UUID.fromString("00000000-0000-0000-0000-00000000f003");
	private static final int DURATION_TICKS = 260;
	private static final int ASSERT_TICKS = 170;

	@Override
	public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
		method.invoke(this, context);
	}

	@GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 360)
	public void racingQuadDiagnosticClimbsInGame(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos spawn = context.absolutePos(new BlockPos(1, 4, 1));
		DroneEntity drone = new DroneEntity(DroneEntityTypes.DRONE, level);
		drone.applyConfig(DroneConfig.racingQuad(), "racing_quad");
		drone.setOwner(TEST_OWNER);
		drone.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
		level.addFreshEntity(drone);

		double initialY = drone.getY();
		DroneControlManager.startDiagnostic(TEST_OWNER, drone.tickCount, DURATION_TICKS);

		context.runAfterDelay(145, () -> {
			float yawRadians = drone.getRenderYawRadians();
			assertTrue(Math.abs(yawRadians) > 0.02f, "direct flight did not yaw enough to verify camera units: " + yawRadians);
			assertTrue(Math.abs(yawRadians) < 0.50f, "render yaw is not radians: " + yawRadians);
		});

		context.runAfterDelay(ASSERT_TICKS, () -> {
			DroneControlManager.stopDiagnostic(TEST_OWNER);
			assertTrue(!drone.isRemoved(), "drone was removed during GameTest");
			assertTrue(drone.tickCount > 120, "drone did not tick enough in GameTest: " + drone.tickCount);
			assertTrue(drone.blackbox().size() > 120, "blackbox did not collect enough samples: " + drone.blackbox().size());
			assertTrue(drone.getY() > initialY + 0.35, String.format(
					Locale.ROOT,
					"drone did not climb: initial=%.3f final=%.3f",
					initialY,
					drone.getY()
			));
			assertTrue(Double.isFinite(drone.getSpeedMetersPerSecond()), "drone speed became non-finite");
			assertTrue(Math.abs(drone.getRenderYawRadians()) < Math.PI * 2.0, "render yaw is not radians: " + drone.getRenderYawRadians());
			assertTrue(Math.abs(drone.getRenderPitchRadians()) < Math.toRadians(45.0), "render pitch is not a playable attitude: " + drone.getRenderPitchRadians());
			assertTrue(Math.abs(drone.getRenderRollRadians()) < Math.toRadians(50.0), "render roll is not a playable attitude: " + drone.getRenderRollRadians());
			assertTrue(drone.getAverageMotorRpm() > 2000.0f, "direct flight did not animate motor RPM: " + drone.getAverageMotorRpm());
			assertTrue(drone.getAverageMotorRpm() < 18000.0f, "direct flight RPM is implausibly high: " + drone.getAverageMotorRpm());
			drone.discard();
			context.succeed();
		});
	}

	@GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 140)
	public void directFlightDisarmClearsPlayableAttitude(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos spawn = context.absolutePos(new BlockPos(1, 5, 1));
		DroneEntity drone = new DroneEntity(DroneEntityTypes.DRONE, level);
		drone.applyConfig(DroneConfig.racingQuad(), "racing_quad");
		drone.setOwner(RESET_OWNER);
		drone.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
		level.addFreshEntity(drone);

		DroneInput tilted = new DroneInput(0.45, 0.95, -0.80, 0.0, true, true, FlightMode.HORIZON);
		DroneInput disarmed = new DroneInput(0.0, 0.0, 0.0, 0.0, false, true, FlightMode.HORIZON);
		DroneInput centeredRearm = new DroneInput(0.20, 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON);
		scheduleInput(context, drone, RESET_OWNER, 1, 36, tilted);
		scheduleInput(context, drone, RESET_OWNER, 43, 55, disarmed);
		scheduleInput(context, drone, RESET_OWNER, 58, 70, centeredRearm);

		context.runAfterDelay(40, () -> {
			assertTrue(
					Math.abs(drone.getRenderPitchRadians()) > Math.toRadians(12.0),
					"test did not build enough pitch attitude before disarm: " + drone.getRenderPitchRadians()
			);
			assertTrue(
					Math.abs(drone.getRenderRollRadians()) > Math.toRadians(10.0),
					"test did not build enough roll attitude before disarm: " + drone.getRenderRollRadians()
			);
		});

		context.runAfterDelay(62, () -> {
			assertTrue(
					Math.abs(drone.getRenderPitchRadians()) < Math.toRadians(5.0),
					"direct flight kept stale pitch after disarm/rearm: " + drone.getRenderPitchRadians()
			);
			assertTrue(
					Math.abs(drone.getRenderRollRadians()) < Math.toRadians(5.0),
					"direct flight kept stale roll after disarm/rearm: " + drone.getRenderRollRadians()
			);
			drone.discard();
			context.succeed();
		});
	}

	private static void scheduleInput(GameTestHelper context, DroneEntity drone, UUID owner, int firstTick, int lastTick, DroneInput input) {
		for (int tick = firstTick; tick <= lastTick; tick += 4) {
			context.runAfterDelay(tick, () -> DroneControlManager.update(owner, input, drone.tickCount));
		}
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new GameTestAssertException(Component.literal(message), 0);
		}
	}
}
