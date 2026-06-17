package com.tenicana.dronecraft.gametest;

import java.util.Locale;
import java.lang.reflect.Method;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

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
	private static final UUID CEILING_OWNER = UUID.fromString("00000000-0000-0000-0000-00000000f004");
	private static final UUID FAILSAFE_OWNER = UUID.fromString("00000000-0000-0000-0000-00000000f005");
	private static final int DURATION_TICKS = 260;
	private static final int ASSERT_TICKS = 170;
	private static final DroneInput SAFE_HORIZON_ARM = new DroneInput(0.02, 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON);

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

		context.runAfterDelay(160, () -> {
			float yawRadians = drone.getRenderYawRadians();
			assertTrue(Math.abs(yawRadians) > 0.006f, "direct flight did not yaw enough to verify camera units: " + yawRadians);
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

	@GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 60)
	public void newDroneStartsInStableAngleMode(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos spawn = context.absolutePos(new BlockPos(1, 4, 1));
		DroneEntity drone = new DroneEntity(DroneEntityTypes.DRONE, level);
		drone.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
		level.addFreshEntity(drone);

		context.runAfterDelay(2, () -> {
			assertTrue(drone.getFlightMode() == FlightMode.DEFAULT_FIRST_FLIGHT, "new drone did not default to stable angle mode: " + drone.getFlightMode());
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
		scheduleInput(context, drone, RESET_OWNER, 1, 1, SAFE_HORIZON_ARM);
		scheduleInput(context, drone, RESET_OWNER, 5, 36, tilted);
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

	@GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 140)
	public void directFlightRespectsCeilingCollision(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos spawn = context.absolutePos(new BlockPos(1, 4, 1));
		BlockPos ceiling = context.absolutePos(new BlockPos(1, 5, 1));
		level.setBlock(ceiling, Blocks.STONE.defaultBlockState(), 3);

		DroneEntity drone = new DroneEntity(DroneEntityTypes.DRONE, level);
		drone.applyConfig(DroneConfig.racingQuad(), "racing_quad");
		drone.setOwner(CEILING_OWNER);
		drone.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
		level.addFreshEntity(drone);

		double initialY = drone.getY();
		DroneInput climb = new DroneInput(1.0, 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON);
		scheduleInput(context, drone, CEILING_OWNER, 1, 1, SAFE_HORIZON_ARM);
		scheduleInput(context, drone, CEILING_OWNER, 5, 80, climb);

		context.runAfterDelay(90, () -> {
			assertTrue(
					drone.getY() < initialY + 0.75,
					String.format(Locale.ROOT, "direct flight ignored ceiling collision: initial=%.3f final=%.3f", initialY, drone.getY())
			);
			assertTrue(
					drone.getDeltaMovement().y() <= 0.01,
					"direct flight kept upward velocity after ceiling collision: " + drone.getDeltaMovement().y()
			);
			drone.discard();
			context.succeed();
		});
	}

	@GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 150)
	public void directFlightLinkLossUsesPlayableFailsafe(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos spawn = context.absolutePos(new BlockPos(1, 5, 1));
		DroneEntity drone = new DroneEntity(DroneEntityTypes.DRONE, level);
		drone.applyConfig(DroneConfig.racingQuad(), "racing_quad");
		drone.setOwner(FAILSAFE_OWNER);
		drone.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
		level.addFreshEntity(drone);

		double initialY = drone.getY();
		double[] speedBeforeLoss = new double[1];
		DroneInput cruise = new DroneInput(0.56, 0.36, 0.20, 0.0, true, true, FlightMode.HORIZON);
		scheduleInput(context, drone, FAILSAFE_OWNER, 1, 1, SAFE_HORIZON_ARM);
		scheduleInput(context, drone, FAILSAFE_OWNER, 5, 60, cruise);

		context.runAfterDelay(62, () -> {
			speedBeforeLoss[0] = drone.getSpeedMetersPerSecond();
			assertTrue(drone.getY() > initialY + 0.12, String.format(
					Locale.ROOT,
					"test did not build airborne state before link loss: initial=%.3f current=%.3f",
					initialY,
					drone.getY()
			));
			assertTrue(speedBeforeLoss[0] > 0.15, "test did not build enough cruise speed before link loss: " + speedBeforeLoss[0]);
			assertTrue(!drone.isControlFailsafeActive(), "failsafe activated before control packets timed out");
		});

		context.runAfterDelay(78, () -> {
			assertTrue(drone.isControlFailsafeActive(), "direct flight did not enter playable failsafe after link loss");
			assertTrue(drone.getFlightMode() == FlightMode.DEFAULT_FIRST_FLIGHT, "direct failsafe did not fall back to stable angle mode: " + drone.getFlightMode());
			assertTrue(!drone.isRawControlLinkActive(), "raw control link stayed active during failsafe");
			assertTrue(!drone.isProcessedControlLinkActive(), "processed control link stayed active during failsafe");
			assertTrue(drone.getControlLinkLossSeconds() > 0.0f, "failsafe did not report link-loss time");
			assertTrue(
					drone.getSpeedMetersPerSecond() <= speedBeforeLoss[0] + 0.25,
					String.format(Locale.ROOT, "failsafe accelerated unexpectedly: before=%.3f after=%.3f", speedBeforeLoss[0], drone.getSpeedMetersPerSecond())
			);
			assertTrue(
					drone.getY() > initialY - 0.05,
					String.format(Locale.ROOT, "failsafe dropped too abruptly: initial=%.3f current=%.3f", initialY, drone.getY())
			);
			assertTrue(
					Math.abs(drone.getRenderPitchRadians()) < Math.toRadians(24.0),
					"failsafe did not level pitch enough for playable recovery: " + drone.getRenderPitchRadians()
			);
			assertTrue(
					Math.abs(drone.getRenderRollRadians()) < Math.toRadians(24.0),
					"failsafe did not level roll enough for playable recovery: " + drone.getRenderRollRadians()
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
