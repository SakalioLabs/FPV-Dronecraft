package com.tenicana.dronecraft.playtest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.phys.Vec3;

import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.camera.DroneViewMode;
import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.config.DroneControllerSettingsScreen;
import com.tenicana.dronecraft.client.control.DroneClientControls;
import com.tenicana.dronecraft.client.render.DroneEntityRenderer;
import com.tenicana.dronecraft.client.render.DroneEntityRenderState;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import com.tenicana.dronecraft.sim.FlightMode;

/** A short player-input smoke test, isolated from the acoustic capture suite. */
public final class PlayableClientGameTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Boolean.getBoolean("fpvdrone.playtest.hideWindow")) {
			context.runOnClient(client -> GLFW.glfwHideWindow(client.getWindow().handle()));
		}
		List<String> screenshots = new ArrayList<>();
		double climb;
		JsonObject runtimePerformance;
		RuntimePerformanceSmoke performanceSmoke = new RuntimePerformanceSmoke(context);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientWorld().waitForChunksDownload();
			context.waitForScreen(null);
			context.runOnClient(client -> checkClientPoseInterpolation(new DroneEntity(DroneEntityTypes.DRONE, client.level)));
			performanceSmoke.start();
			performanceSmoke.recordPoseFixture(context.computeOnClient(DronePosePacketSmoke::verifyAndReturnFixtureId));
			world.getServer().runCommand("gamemode creative @p");
			world.getServer().runCommand("execute as @p at @s run fpvdrone spawn racing_quad");
			context.waitFor(client -> DroneClientState.hasControlledDrone(client), 200);
			context.runOnClient(client -> {
				check(!DroneClientState.controlledDrone().isArmed(), "new drone must start disarmed");
				check(DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT, "new session must start in line of sight");
				check(DroneClientState.controlledDrone().getFlightMode() == FlightMode.ANGLE,
						"new drone must start in ANGLE mode");
			});

			context.getInput().pressKey(GLFW.GLFW_KEY_V);
			context.waitFor(client -> DroneClientState.isVirtualControllerEnabled());
			if (context.computeOnClient(client -> DroneClientControls.config().gamepadEnabled())) {
				context.getInput().pressKey(GLFW.GLFW_KEY_G);
			}
			context.waitFor(client -> DroneClientState.inputSource() == DroneClientState.InputSource.KEYBOARD);
			context.getInput().holdKeyFor(GLFW.GLFW_KEY_PAGE_DOWN, 40);
			context.waitFor(client -> DroneClientState.throttle() == 0.0f);
			context.getInput().pressKey(GLFW.GLFW_KEY_R);
			context.waitFor(client -> DroneClientState.armed() && DroneClientState.controlledDrone().isArmed());
			double startY = context.computeOnClient(client -> DroneClientState.controlledDrone().getY());
			context.getInput().holdKey(GLFW.GLFW_KEY_PAGE_UP);
			for (int ticks : List.of(10, 20, 25)) {
				context.waitTicks(ticks);
				context.runOnClient(client -> {
					DroneEntity drone = DroneClientState.controlledDrone();
					FpvDronecraftMod.LOGGER.info(
							"Playtest input: clientThrottle={}, serverThrottle={}, source={}, armed={}, virtual={}, rpm={}, y={}, tick={}",
							DroneClientState.throttle(), drone.getControlThrottle(), DroneClientState.inputSource(),
							drone.isArmed(), DroneClientState.isVirtualControllerEnabled(), drone.getMotorRpm(0), drone.getY(), drone.tickCount
					);
				});
			}
			context.getInput().releaseKey(GLFW.GLFW_KEY_PAGE_UP);
			context.waitFor(client -> DroneClientState.controlledDrone().getY() > startY + 0.5, 200);
			climb = context.computeOnClient(client -> DroneClientState.controlledDrone().getY() - startY);
			context.runOnClient(client -> {
				check(DroneClientState.inputSource() == DroneClientState.InputSource.KEYBOARD,
						"flight must use keyboard input");
				check(Float.isFinite(DroneClientState.controlledDrone().getSpeedMetersPerSecond()),
						"flight speed must be finite");
			});
			screenshots.add(context.takeScreenshot("playtest-line-of-sight").toString());

			context.getInput().pressKey(GLFW.GLFW_KEY_B);
			context.waitFor(client -> DroneClientState.isFpvActive(client.level));
			context.waitTicks(10);
			screenshots.add(context.takeScreenshot("playtest-fpv").toString());
			context.runOnClient(client -> {
				DroneEntity drone = DroneClientState.controlledDrone();
				check(client.gameRenderer.getMainCamera().position().distanceTo(drone.position()) < 3.0,
						"rendered FPV camera must follow the drone");
			});
			context.getInput().pressKey(GLFW.GLFW_KEY_B);
			context.waitFor(client -> DroneClientState.isThirdPersonActive(client.level));
			context.waitTicks(10);
			screenshots.add(context.takeScreenshot("playtest-third-person").toString());
			context.runOnClient(client -> {
				DroneEntity drone = DroneClientState.controlledDrone();
				check(!DroneClientState.isFpvActive(client.level), "third person must not use FPV-only behavior");
				check(client.getCameraEntity() == drone && client.gameRenderer.getMainCamera().position().distanceTo(drone.position()) < 7,
						"third-person camera must follow the controlled drone");
				Object renderer = client.getEntityRenderDispatcher().getRenderer(drone);
				check(renderer instanceof DroneEntityRenderer, "third person must use the production drone renderer");
				DroneEntityRenderState state = new DroneEntityRenderState();
				((DroneEntityRenderer) renderer).extractRenderState(drone, state, 1);
				check(!state.hiddenInFpv, "controlled drone mesh must remain visible in third person");
				check(Math.abs(client.gameRenderer.getMainCamera().leftVector().y()) < 0.0001,
						"third-person horizon must remain upright");
			});
			context.getInput().pressKey(GLFW.GLFW_KEY_B);
			context.waitFor(client -> DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT
					&& client.getCameraEntity() == client.player && !DroneClientState.isDroneCameraActive(client.level));
			context.getInput().pressKey(GLFW.GLFW_KEY_B);
			context.waitFor(client -> DroneClientState.isFpvActive(client.level));

			for (FlightMode mode : List.of(FlightMode.HORIZON, FlightMode.ACRO, FlightMode.ANGLE)) {
				context.getInput().pressKey(GLFW.GLFW_KEY_M);
				context.waitFor(client -> DroneClientState.flightMode() == mode
						&& DroneClientState.controlledDrone().getFlightMode() == mode);
			}
			context.getInput().holdKeyFor(GLFW.GLFW_KEY_PAGE_DOWN, 90);
			context.waitFor(client -> DroneClientState.throttle() == 0.0f);
			context.getInput().pressKey(GLFW.GLFW_KEY_R);
			context.waitFor(client -> !DroneClientState.armed() && !DroneClientState.controlledDrone().isArmed());
			context.getInput().holdKeyFor(GLFW.GLFW_KEY_PAGE_UP, 50);
			context.runOnClient(client -> {
				check(DroneClientState.throttle() > 0.1f, "disarmed test must hold a nonzero throttle command");
				check(!DroneClientState.controlledDrone().isArmed(), "held throttle must not arm the drone");
				check(DroneClientState.controlledDrone().getMotorRpm(0) == 0.0f,
						"disarmed drone must ignore held throttle and stop its motors");
			});
			context.getInput().holdKeyFor(GLFW.GLFW_KEY_PAGE_DOWN, 90);
			context.waitFor(client -> DroneClientState.throttle() == 0.0f);
			context.getInput().pressKey(GLFW.GLFW_KEY_B);
			context.waitFor(client -> DroneClientState.isThirdPersonActive(client.level));
			context.getInput().pressKey(GLFW.GLFW_KEY_B);
			context.waitFor(client -> DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT && client.getCameraEntity() == client.player);
			context.getInput().pressKey(GLFW.GLFW_KEY_I);
			context.waitForScreen(DroneControllerSettingsScreen.class);
			screenshots.add(context.takeScreenshot("playtest-controller-settings").toString());
			context.getInput().setCursorPos(427, 240);
			context.getInput().scroll(-20);
			context.waitTicks(2);
			screenshots.add(context.takeScreenshot("playtest-controller-settings-scrolled").toString());
			context.getInput().resizeWindow(1280, 720);
			context.waitTicks(2);
			screenshots.add(context.takeScreenshot("playtest-controller-settings-1280").toString());
			context.clickScreenButton("gui.done");
			context.waitForScreen(null);
			runtimePerformance = performanceSmoke.finishAndVerify();
		} finally {
			performanceSmoke.close();
		}
		context.waitFor(client -> client.player == null && DroneClientState.controlledDrone() == null
				&& DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT && !DroneClientState.armed());
		writeReport(climb, screenshots, runtimePerformance);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void checkClientPoseInterpolation(DroneEntity drone) {
		Vec3 start = new Vec3(10.0, 100.0, 10.0);
		Vec3 target = new Vec3(20.0, 100.0, 10.0);
		drone.setPos(start);
		drone.setOldPosAndRot();
		drone.tick();
		Vec3 renderedBeforePacket = drone.getPosition(0.6f);
		drone.moveOrInterpolateTo(target, 0.0f, 0.0f);
		check(drone.getPosition(0.6f).distanceTo(renderedBeforePacket) < 1.0e-6,
				"incoming pose between ticks must not jump the current rendered frame");
		drone.setOldPosAndRot();
		drone.tick();
		check(drone.getPosition(0.0f).distanceTo(start) < 1.0e-6, "new interval must begin at the previous endpoint");
		check(drone.getPosition(0.5f).distanceTo(start.lerp(target, 0.5)) < 1.0e-6, "position must interpolate within the tick");
		check(drone.getPosition(1.0f).distanceTo(target) < 1.0e-6, "one-step interpolation must reach the queued target");
		drone.moveOrInterpolateTo(new Vec3(30.0, 100.0, 10.0), 0.0f, 0.0f);
		Vec3 teleported = new Vec3(15.0, 100.0, 10.0);
		drone.setPos(teleported);
		drone.setOldPosAndRot();
		drone.tick();
		check(drone.getPosition(1.0f).distanceTo(teleported) < 1.0e-6,
				"teleport must cancel a stale queued movement target");
	}

	private static void writeReport(double climb, List<String> screenshots, JsonObject runtimePerformance) {
		JsonObject report = new JsonObject();
		report.addProperty("schema", "fpvdrone-playable-client-smoke-v1");
		report.addProperty("passed", true);
		report.addProperty("input", "Fabric client GameTest keyboard events");
		report.addProperty("fabricPacketSynchronizer", "disabled; normal asynchronous packet delivery with bounded state waits");
		report.addProperty("climbMeters", climb);
		report.addProperty("checks", "tick pose interpolation, real metadata/movement/teleport packet handlers, signed pitch/roll 720-degree metadata turns and held attitude, spawn, default ANGLE and LOS, V virtual controller, G keyboard selection, R arm, PageUp climb, real B key LOS/FPV/THIRD_PERSON/LOS cycle, rendered drone camera and visible production mesh in third person, upright third-person horizon, M mode cycle, PageDown, R disarm, held throttle while disarmed stops motors, I settings, disconnect reset to LOS, registered runtime performance commands and bounds, actual packet fixture monitor events, CPU frame/render/swap/server hooks, midframe stop and background export");
		report.add("runtimePerformanceFunctionalSmoke", runtimePerformance);
		report.add("screenshots", new GsonBuilder().create().toJsonTree(screenshots));
		Path output = Path.of(System.getProperty("fpvdrone.playtest.report", "playtest-client-report.json"));
		try {
			Files.createDirectories(output.toAbsolutePath().getParent());
			Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
		} catch (IOException error) {
			throw new IllegalStateException("could not write playable client smoke report", error);
		}
	}
}
