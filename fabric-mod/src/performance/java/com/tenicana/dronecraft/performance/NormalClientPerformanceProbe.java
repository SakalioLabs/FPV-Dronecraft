package com.tenicana.dronecraft.performance;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.camera.DroneViewMode;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.client.control.DroneClientControls;
import com.tenicana.dronecraft.client.performance.RuntimePerformanceMonitor;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.network.DroneRemoteSessions;
import com.tenicana.dronecraft.performance.PerformanceTimings.Metric;
import com.tenicana.dronecraft.performance.PerformanceTimings.Phase;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import com.tenicana.dronecraft.sim.FlightMode;

/**
 * Opt-in synthetic save probe on the normal Minecraft loop. No ClientGameTest
 * entrypoint, regulated test ticks, persistence replacement, or async world reads.
 */
public final class NormalClientPerformanceProbe implements ClientModInitializer {
	public static final boolean ENABLED = Boolean.getBoolean("fpvdrone.performance");
	public static final int FRAME_LIMIT = 260;
	private static final long SEED = 0x46505653415645L;
	private static volatile UUID probeDroneId;
	private static NormalClientPerformanceProbe active;
	private final AtomicInteger queuedPings = new AtomicInteger();
	private final long started = System.nanoTime();
	private final ModelPreviewCapture modelPreview = ModelPreviewCapture.ENABLED ? new ModelPreviewCapture() : null;
	private final ThreeViewSmoke threeViewSmoke = ThreeViewSmoke.ENABLED ? new ThreeViewSmoke() : null;
	private final ControllerModelSmoke controllerModelSmoke = ControllerModelSmoke.ENABLED ? new ControllerModelSmoke() : null;
	private final DronePlacementBindingSmoke placementBindingSmoke = DronePlacementBindingSmoke.ENABLED ? new DronePlacementBindingSmoke() : null;
	private final RemotePilotSmoke remotePilotSmoke = RemotePilotSmoke.ENABLED ? new RemotePilotSmoke() : null;
	private boolean threeViewStarted;
	private boolean controllerModelStarted;
	private boolean placementBindingStarted;
	private boolean remotePilotStarted;
	private final boolean renderStress = Boolean.getBoolean("fpvdrone.performance.renderStress") && modelPreview == null;
	private final long phaseDuration = secondsProperty("fpvdrone.performance.phaseSeconds", renderStress ? 20 : 8);
	private final long warmupDuration = secondsProperty("fpvdrone.performance.warmupSeconds", 10);
	private final int renderDistance = integerProperty("fpvdrone.performance.renderDistance", renderStress ? 32 : 8, 2, 32);
	private final int simulationDistance = integerProperty("fpvdrone.performance.simulationDistance", renderStress ? 12 : 8, 5, 32);
	private final int windowWidth = integerProperty("fpvdrone.performance.width", renderStress ? 3440 : modelPreview != null ? 1600 : 1280, 640, 3840);
	private final int windowHeight = integerProperty("fpvdrone.performance.height", renderStress ? 1351 : modelPreview != null ? 1000 : 720, 360, 2160);
	private final boolean hidden = Boolean.parseBoolean(System.getProperty("fpvdrone.performance.hidden", "true"));
	private final boolean runtimeMonitorSmoke = Boolean.getBoolean("fpvdrone.performance.runtimeMonitorSmoke");
	private static final int STALE_RUNTIME_ENTITY_ID = -990_017;
	private final String worldId = "fpv-performance-" + UUID.randomUUID();
	private long phaseStarted;
	private Path output;
	private Path runDirectory;
	private boolean inDriver;
	private boolean worldCreationRequested;
	private boolean closingRequested;
	private volatile boolean finished;
	private boolean autosaveSucceeded;
	private boolean flushSucceeded;
	private boolean reopenSucceeded;
	private String failure;
	private String jfrStatus = "disabled";
	private Recording recording;
	private CompletableFuture<?> operation;
	private Fixture fixture;
	private JsonObject verification = new JsonObject();
	private final JsonObject runtimeSmokeReport = new JsonObject();
	private CompletableFuture<JsonObject> runtimeValidation;
	private CompletableFuture<String> runtimeScreenshot;
	private long runtimeCpuId, runtimeGpuId, runtimeRestartId, runtimeNestedClosingFrames, runtimeExportWaitFrames, runtimeScreenshotCaptureId;
	private boolean runtimeNestedTokensValid = true;
	private boolean runtimeCpuValidated, runtimeGpuValidated, runtimeSmokeSucceeded;
	private boolean gpuDebugOverridden, previousHideGui;
	private DebugScreenEntryStatus previousGpuDebugStatus;
	private Path runtimeScreenshotPath;
	private static final double STRESS_RADIUS = 96.0;
	private static final double STRESS_SPEED = 64.0;
	private volatile Fixture stressFixture;
	private volatile boolean stressRouteActive;
	private volatile boolean stressServerRemoteView;
	private volatile int stressServerModeId = -1;
	private volatile long stressBaselineTicks, stressAfterSaveTicks;
	private Phase stressServerPhase;
	private long stressRouteTick;
	private int stressCameraStage;
	private int stressCameraStageTick;
	private boolean stressCameraSmokeSucceeded, stressReturnForFlush, stressAwaitingFpv;
	private final JsonObject stressReport = new JsonObject();

	@Override
	public void onInitializeClient() {
		if (!ENABLED) return;
		active = this;
		ClientLifecycleEvents.CLIENT_STARTED.register(this::clientStarted);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		if (renderStress) ServerTickEvents.END_SERVER_TICK.register(this::driveRenderStressRoute);
		if (modelPreview != null) ServerTickEvents.END_SERVER_TICK.register(modelPreview::holdDrone);
		if (threeViewSmoke != null) ServerTickEvents.END_SERVER_TICK.register(threeViewSmoke::serverTick);
		if (controllerModelSmoke != null) ServerTickEvents.END_SERVER_TICK.register(controllerModelSmoke::serverTick);
		if (placementBindingSmoke != null) ServerTickEvents.END_SERVER_TICK.register(placementBindingSmoke::serverTick);
		if (remotePilotSmoke != null) {
			ServerTickEvents.END_SERVER_TICK.register(remotePilotSmoke::serverTick);
			ServerLifecycleEvents.SERVER_STOPPING.register(remotePilotSmoke::serverStopping);
			ServerLifecycleEvents.SERVER_STOPPED.register(remotePilotSmoke::serverStopped);
		}
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (!finished) finish(client, false, "client_stopped_before_probe_completed");
		});
	}

	public static boolean isProbeDrone(UUID id) { return ENABLED && id.equals(probeDroneId); }
	public static void onFrameBoundary(Minecraft client, boolean render) {
		if (ENABLED && active != null) active.frameBoundary(client, render);
	}
	public static boolean measuring() {
		Phase current = PerformanceTimings.phase();
		return ENABLED && (current == Phase.WARMUP || current == Phase.BASELINE || current == Phase.AUTOSAVE || current == Phase.AFTER_SAVE);
	}

	private void clientStarted(Minecraft client) {
		try {
			if (renderStress && (remotePilotSmoke != null || placementBindingSmoke != null)) throw new IllegalStateException("Remote-pilot/placement-binding and render-stress fixtures must run separately because they drive the same controls");
			Path workspace = Path.of(requiredProperty("fpvdrone.performance.workspace")).toRealPath();
			Path allowed = workspace.resolve("build/performance").toAbsolutePath().normalize();
			runDirectory = client.gameDirectory.toPath().toRealPath();
			output = Path.of(requiredProperty("fpvdrone.performance.output")).toAbsolutePath().normalize();
			if (!runDirectory.startsWith(allowed) || !output.startsWith(allowed)) {
				throw new IllegalStateException("Probe game directory and outputs must be inside this workspace's build/performance directory");
			}
			Files.createDirectories(output.getParent());
			client.options.pauseOnLostFocus = false;
			client.options.enableVsync().set(false);
			client.options.framerateLimit().set(FRAME_LIMIT);
			client.options.renderDistance().set(renderDistance);
			client.options.simulationDistance().set(simulationDistance);
			client.getWindow().setWindowed(windowWidth, windowHeight);
			if (hidden) GLFW.glfwHideWindow(client.getWindow().handle());
			FpvDronecraftMod.LOGGER.info("Normal client performance probe armed; disposable world {}, hidden={}", worldId, hidden);
		} catch (Exception exception) {
			finish(client, false, exception.toString());
		}
	}

	private void tick(Minecraft client) {
		if (finished || runDirectory == null) return;
		checkDeadline(client);
		if (finished || inDriver) return;
		inDriver = true;
		try {
			long now = System.nanoTime();
			Phase current = PerformanceTimings.phase();
			if (measuring() && client.getSingleplayerServer() != null) queueTimingPing(client.getSingleplayerServer());
			switch (current) {
				case OPENING -> {
					if (!worldCreationRequested && client.getOverlay() == null) {
						worldCreationRequested = true;
						GameRules rules = new GameRules(FeatureFlags.DEFAULT_FLAGS);
						LevelSettings settings = new LevelSettings(renderStress ? "FPV render stress fixture" : "FPV save timing fixture", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
						client.createWorldOpenFlows().createFreshLevel(worldId, settings, new WorldOptions(SEED, false, false), registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(renderStress ? WorldPresets.NORMAL : WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
					}
					if (ready(client)) {
						transition(Phase.SETUP);
						operation = submit(client.getSingleplayerServer(), this::createFixture);
					}
				}
				case SETUP -> {
					if (operation.isDone()) {
						fixture = (Fixture) operation.join();
						if (renderStress) stressFixture = fixture;
						probeDroneId = fixture.droneId;
						client.setScreen(null);
						if (modelPreview != null) modelPreview.start(client, output, runDirectory, fixture.droneId, fixture.droneHome, fixture.playerHome, fixture.modelId);
						startJfr();
						transition(Phase.WARMUP);
					}
				}
				case WARMUP -> {
					if (modelPreview != null && !modelPreview.tick(client)) break;
					if (threeViewSmoke != null) {
						if (!threeViewStarted) {
							threeViewSmoke.start(client, runDirectory, fixture.droneId, fixture.droneHome, fixture.playerHome);
							threeViewStarted = true;
						}
						if (!threeViewSmoke.tick(client)) break;
					}
					if (remotePilotSmoke != null) {
						if (!remotePilotStarted) {
							remotePilotSmoke.start(client, runDirectory, fixture.droneId, fixture.droneHome, fixture.playerHome);
							remotePilotStarted = true;
						}
						if (!remotePilotSmoke.tick(client)) break;
					}
					if (controllerModelSmoke != null) {
						if (!controllerModelStarted) {
							controllerModelSmoke.start(client, runDirectory, fixture.droneId, fixture.droneHome, fixture.playerHome);
							controllerModelStarted = true;
						}
						if (!controllerModelSmoke.tick(client)) break;
					}
					if (placementBindingSmoke != null) {
						if (!placementBindingStarted) {
							placementBindingSmoke.start(client, runDirectory, fixture.droneHome, fixture.playerHome);
							placementBindingStarted = true;
						}
						if (!placementBindingSmoke.tick(client)) break;
					}
					if (renderStress && !exerciseRenderStressCamera(client)) break;
					if (now - phaseStarted >= warmupDuration) {
						if (runtimeMonitorSmoke) {
							if (!RuntimePerformanceMonitor.start(client, 180, false)) throw new IllegalStateException("Runtime CPU smoke capture could not start");
							runtimeCpuId = RuntimePerformanceMonitor.captureId();
							if (runtimeCpuId == 0) throw new IllegalStateException("Runtime CPU smoke capture has no generation ID");
						}
						transition(Phase.BASELINE);
						if (renderStress) stressRouteActive = true;
					}
				}
				case BASELINE -> {
					if (now - phaseStarted >= phaseDuration) {
						if (renderStress) {
							beginRenderStressReturn(client, false);
							break;
						}
						transition(Phase.AUTOSAVE);
						operation = submit(client.getSingleplayerServer(), server -> {
							// Eager vanilla saves may clean setup chunks during warmup.
							// Make existing fixture chunks dirty before the timed save starts.
							for (BlockPos pos : fixture.dirtyPositions) server.overworld().setBlock(pos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
							return server.saveEverything(true, false, false);
						});
					}
				}
				case CAMERA_RETURN -> {
					if (operation.isDone()) {
						operation.join();
						if (DroneClientState.isDroneCameraActive(client.level) || client.getCameraEntity() != client.player || stressServerRemoteView) break;
						stressReport.addProperty(stressReturnForFlush ? "camera_return_after_second_route" : "camera_return_after_first_route", true);
						if (stressReturnForFlush) endMeasurement(client);
						else {
							transition(Phase.AUTOSAVE);
							operation = submit(client.getSingleplayerServer(), server -> {
								for (BlockPos pos : fixture.dirtyPositions) server.overworld().setBlock(pos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
								return server.saveEverything(true, false, false);
							});
						}
					}
				}
				case AUTOSAVE -> {
					if (operation.isDone()) {
						autosaveSucceeded = Boolean.TRUE.equals(operation.join());
						if (!autosaveSucceeded) throw new IllegalStateException("Vanilla autosave-equivalent call returned false");
						transition(Phase.AFTER_SAVE);
						if (renderStress) {
							stressAwaitingFpv = true;
							clickKey(GLFW.GLFW_KEY_B);
						}
					}
				}
				case AFTER_SAVE -> {
					if (renderStress && stressAwaitingFpv) {
						if (!renderStressFpvReady(client)) break;
						stressAwaitingFpv = false;
						stressRouteActive = true;
						phaseStarted = now;
					}
					if (now - phaseStarted >= phaseDuration) {
						if (renderStress) beginRenderStressReturn(client, true);
						else endMeasurement(client);
					}
				}
				case RUNTIME_CPU_EXPORT -> {
					if (runtimeValidation.isDone()) {
						if (!runtimeCpuValidated) {
							runtimeSmokeReport.add("cpu", runtimeValidation.join());
							runtimeCpuValidated = true;
							prepareGpuSmoke(client);
						}
						// Export completion callbacks may still be clearing the monitor's busy flag.
						if (RuntimePerformanceMonitor.start(client, 5, true)) {
							runtimeGpuId = RuntimePerformanceMonitor.captureId();
							if (runtimeGpuId <= runtimeCpuId) throw new IllegalStateException("Runtime GPU capture reused a generation ID");
							transition(Phase.RUNTIME_GPU);
						}
					}
				}
				case RUNTIME_GPU -> {
					long currentId = RuntimePerformanceMonitor.captureId();
					if (currentId != 0 && currentId != runtimeGpuId) throw new IllegalStateException("Unexpected runtime GPU capture generation");
					if (currentId == 0) {
						restoreGpuSmoke(client);
						if (runtimeScreenshot == null) throw new IllegalStateException("GPU HUD screenshot was not requested before automatic stop");
						CompletableFuture<Path> export = RuntimePerformanceMonitor.lastExport();
						if (export == null) throw new IllegalStateException("GPU deadline did not start an automatic export");
						runtimeValidation = export.thenCombine(runtimeScreenshot.orTimeout(15, TimeUnit.SECONDS), (path, message) -> path)
								.thenApplyAsync(path -> verifyRuntimeReportUnchecked(path, runtimeGpuId, false, true, false));
						transition(Phase.RUNTIME_GPU_EXPORT);
					}
				}
				case RUNTIME_GPU_EXPORT -> {
					if (runtimeValidation.isDone()) {
						if (!runtimeGpuValidated) {
							runtimeSmokeReport.add("gpu", runtimeValidation.join());
							runtimeGpuValidated = true;
						}
						if (RuntimePerformanceMonitor.start(client, 120, false)) {
							runtimeRestartId = RuntimePerformanceMonitor.captureId();
							if (runtimeRestartId <= runtimeGpuId) throw new IllegalStateException("Restarted runtime CPU capture reused a generation ID");
							// Old-generation hooks must be ignored, including late GPU results.
							RuntimePerformanceMonitor.acceptGpuTiming((runtimeGpuId << 32) | 1, 123_456_789_000_000L);
							RuntimePerformanceMonitor.recordDroneTick(runtimeGpuId, true, STALE_RUNTIME_ENTITY_ID, now, now + 7_777_777L, 7_777_777L);
							transition(Phase.RUNTIME_RESTART);
						}
					}
				}
				case RUNTIME_RESTART -> {
					if (now - phaseStarted >= 1_000_000_000L) {
						transition(Phase.FLUSH);
						operation = submit(client.getSingleplayerServer(), server -> server.saveEverything(true, true, false));
					}
				}
				case RUNTIME_DISCONNECT_EXPORT -> {
					if (runtimeValidation.isDone()) {
						runtimeSmokeReport.add("disconnect", runtimeValidation.join());
						runtimeSmokeSucceeded = runtimeCpuValidated && runtimeGpuValidated && runtimeNestedTokensValid
								&& runtimeNestedClosingFrames > 0 && runtimeExportWaitFrames > 0;
						if (!runtimeSmokeSucceeded) throw new IllegalStateException("Runtime restart/disconnect smoke did not exercise valid nested runTick(false) frames");
						transition(Phase.REOPENING);
						client.createWorldOpenFlows().openWorld(worldId, () -> finish(client, false, "Vanilla world reopen failed"));
					}
				}
				case FLUSH -> {
					if (operation.isDone()) {
						flushSucceeded = Boolean.TRUE.equals(operation.join());
						if (!flushSucceeded) throw new IllegalStateException("Vanilla flush save returned false");
						if (threeViewSmoke != null && !threeViewSmoke.prepareDisconnect(client)) break;
						if (remotePilotSmoke != null && !remotePilotSmoke.prepareDisconnect(client)) break;
						transition(Phase.CLOSING);
						closingRequested = true;
					}
				}
				case REOPENING -> {
					if (ready(client)) {
						if (threeViewSmoke != null) threeViewSmoke.verifyReopened(client);
						if (remotePilotSmoke != null) remotePilotSmoke.verifyReopened(client);
						transition(Phase.VERIFYING);
						operation = submit(client.getSingleplayerServer(), this::verifyFixture);
					}
				}
				case VERIFYING -> {
					if (operation.isDone()) {
						verification = (JsonObject) operation.join();
						if (placementBindingSmoke != null && !placementBindingSmoke.verifyClientReopened(client)) verification.addProperty("passed", false);
						reopenSucceeded = verification.get("passed").getAsBoolean();
						if (!reopenSucceeded && now - phaseStarted < 10_000_000_000L) {
							// Entity region reads/deserialization complete asynchronously in
							// vanilla. Poll only through fresh server-thread operations.
							operation = submit(client.getSingleplayerServer(), this::verifyFixture);
							break;
						}
						finish(client, reopenSucceeded, reopenSucceeded ? null : "Synthetic world/drone persistence verification failed");
					}
				}
				default -> { }
			}
		} catch (Throwable exception) {
			finish(client, false, exception.toString());
		} finally {
			inDriver = false;
		}
	}

	private void frameBoundary(Minecraft client, boolean render) {
		if (finished || runDirectory == null) return;
		Phase framePhase = PerformanceTimings.phase();
		if (runtimeMonitorSmoke && render && (framePhase == Phase.RUNTIME_CPU_EXPORT || framePhase == Phase.RUNTIME_GPU_EXPORT
				|| framePhase == Phase.RUNTIME_DISCONNECT_EXPORT)) runtimeExportWaitFrames++;
		if (runtimeMonitorSmoke && !render && PerformanceTimings.phase() == Phase.CLOSING) {
			runtimeNestedClosingFrames++;
			runtimeNestedTokensValid &= RuntimePerformanceMonitor.currentFrameToken() == 0;
		}
		// Vanilla disconnect renders nested runTick(false) frames while waiting
		// for server shutdown. Check the deadline before any reentrancy guard.
		checkDeadline(client);
		if (modelPreview != null && !finished && render && !inDriver && framePhase == Phase.WARMUP) {
			try { modelPreview.frameBoundary(client); }
			catch (Throwable exception) { finish(client, false, "Model preview screenshot failed: " + exception); }
		}
		if (runtimeMonitorSmoke && !finished && render && !inDriver && PerformanceTimings.phase() == Phase.RUNTIME_GPU
				&& runtimeScreenshot == null && RuntimePerformanceMonitor.captureId() == runtimeGpuId
				&& RuntimePerformanceMonitor.gpuTimingEnabled() && System.nanoTime() - phaseStarted >= 2_000_000_000L) {
			try {
				runtimeScreenshotPath = runDirectory.resolve("screenshots/runtime-monitor-gpu-hud.png");
				runtimeScreenshotCaptureId = RuntimePerformanceMonitor.captureId();
				runtimeScreenshot = new CompletableFuture<>();
				Screenshot.grab(runDirectory.toFile(), runtimeScreenshotPath.getFileName().toString(), client.getMainRenderTarget(), 1,
						message -> runtimeScreenshot.complete(message.getString()));
			} catch (Throwable exception) {
				finish(client, false, "Runtime GPU HUD screenshot failed: " + exception);
			}
		}
		if (finished || !render || !closingRequested || inDriver) return;
		closingRequested = false;
		inDriver = true;
		try {
			// The complete vanilla menu path disconnects the actual Connection
			// before the saving-screen teardown waits for server shutdown.
			// disconnectWithSavingScreen alone does not request that shutdown.
			client.disconnectFromWorld(Component.literal("Synthetic performance probe closing"));
			if (finished) return;
			if (threeViewSmoke != null) threeViewSmoke.verifyDisconnected(client);
			if (remotePilotSmoke != null) remotePilotSmoke.verifyDisconnected(client);
			if (runtimeMonitorSmoke) {
				runtimeValidation = stopAndValidateRuntime(client, runtimeRestartId, false, false, true);
				transition(Phase.RUNTIME_DISCONNECT_EXPORT);
			} else {
				transition(Phase.REOPENING);
				client.createWorldOpenFlows().openWorld(worldId, () -> finish(client, false, "Vanilla world reopen failed"));
			}
		} catch (Throwable exception) {
			finish(client, false, exception.toString());
		} finally {
			inDriver = false;
		}
	}

	private void prepareGpuSmoke(Minecraft client) {
		// clientStarted has already proven that this profile is under build/performance.
		if (gpuDebugOverridden) return;
		previousGpuDebugStatus = client.debugEntries.getStatus(DebugScreenEntries.GPU_UTILIZATION);
		if (previousGpuDebugStatus == null) throw new IllegalStateException("Disposable GPU debug entry has no previous status");
		previousHideGui = client.options.hideGui;
		gpuDebugOverridden = true;
		client.debugEntries.setStatus(DebugScreenEntries.GPU_UTILIZATION, DebugScreenEntryStatus.ALWAYS_ON);
		client.options.hideGui = false;
	}

	private void restoreGpuSmoke(Minecraft client) {
		if (!gpuDebugOverridden) return;
		client.debugEntries.setStatus(DebugScreenEntries.GPU_UTILIZATION, previousGpuDebugStatus);
		client.options.hideGui = previousHideGui;
		if (client.debugEntries.getStatus(DebugScreenEntries.GPU_UTILIZATION) != previousGpuDebugStatus) {
			throw new IllegalStateException("Disposable GPU debug-entry status was not restored");
		}
		gpuDebugOverridden = false;
	}

	private CompletableFuture<JsonObject> stopAndValidateRuntime(
			Minecraft client, long expectedId, boolean ordinarySave, boolean gpu, boolean disconnected
	) {
		if (RuntimePerformanceMonitor.captureId() != expectedId || expectedId == 0) {
			throw new IllegalStateException("Runtime smoke capture stopped or changed before the intended stop");
		}
		CompletableFuture<Path> export = RuntimePerformanceMonitor.stop(client);
		if (RuntimePerformanceMonitor.captureId() != 0 || export == null) {
			throw new IllegalStateException("Runtime stop did not clear the active generation and return an export future");
		}
		return export.thenApplyAsync(path -> verifyRuntimeReportUnchecked(path, expectedId, ordinarySave, gpu, disconnected));
	}

	private JsonObject verifyRuntimeReportUnchecked(Path path, long expectedId, boolean ordinarySave, boolean gpu, boolean disconnected) {
		try {
			return verifyRuntimeReport(path, expectedId, ordinarySave, gpu, disconnected);
		} catch (Exception exception) {
			throw new CompletionException(exception);
		}
	}

	/** Only sealed exported files are read here; this runs off the client and server threads. */
	private JsonObject verifyRuntimeReport(Path path, long expectedId, boolean ordinarySave, boolean gpu, boolean disconnected) throws IOException {
		Path report = path.toRealPath();
		Path allowedReports = runDirectory.resolve("performance-diagnostics").toRealPath();
		requireSmoke(report.startsWith(allowedReports) && !report.getFileName().toString().endsWith(".partial"), "Runtime export path is outside the completed disposable reports");
		for (String file : List.of("frames.csv", "events.csv", "summary.json", "README.md", "report.html")) {
			requireSmoke(Files.isRegularFile(report.resolve(file)) && Files.size(report.resolve(file)) > 0, "Runtime export is missing " + file);
		}
		JsonObject summary = JsonParser.parseString(Files.readString(report.resolve("summary.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		requireSmoke("mcfpv-runtime-performance-v1".equals(summary.get("schema").getAsString()), "Unexpected runtime report schema");
		long frameRows = 0, gpuRows = 0, saveOverlapRows = 0, clientTicks = 0, lastSequence = 0;
		boolean firstIntervalMissing = false;
		try (BufferedReader reader = Files.newBufferedReader(report.resolve("frames.csv"), StandardCharsets.UTF_8)) {
			Map<String, Integer> header = csvHeader(reader.readLine(), List.of("frame_token", "start_ms", "interval_ms", "total_wall_ms",
					"render_wall_ms", "swap_wall_ms", "limiter_wait_ms", "work_wall_ms", "gpu_render_ms", "client_tick_count",
					"client_tick_sum_ms", "client_tick_max_ms", "flags", "entity_id", "heap_used_bytes", "gc_delta_ms",
					"server_tick_age_ms", "pose_delivery_age_ms", "save_overlap"));
			String line;
			while ((line = reader.readLine()) != null) {
				List<String> row = csvFields(line);
				requireSmoke(row.size() == header.size(), "Runtime frame CSV has an inconsistent column count");
				long token = Long.parseLong(csv(row, header, "frame_token"));
				long sequence = token & 0xffffffffL;
				requireSmoke(token >>> 32 == expectedId && sequence > lastSequence, "Runtime frame tokens crossed capture generations or were duplicated");
				lastSequence = sequence;
				double total = nonNegativeCsv(row, header, "total_wall_ms", false);
				double swap = nonNegativeCsv(row, header, "swap_wall_ms", false);
				double limiter = nonNegativeCsv(row, header, "limiter_wait_ms", false);
				double work = nonNegativeCsv(row, header, "work_wall_ms", false);
				requireSmoke(Math.abs(work - Math.max(0, total - swap - limiter)) <= 0.000001, "Runtime work-wall field does not subtract explicit waits");
				for (String field : List.of("start_ms", "render_wall_ms", "client_tick_sum_ms", "client_tick_max_ms")) nonNegativeCsv(row, header, field, false);
				for (String field : List.of("interval_ms", "server_tick_age_ms", "pose_delivery_age_ms")) nonNegativeCsv(row, header, field, true);
				if (!csv(row, header, "gpu_render_ms").isEmpty()) {
					nonNegativeCsv(row, header, "gpu_render_ms", false);
					gpuRows++;
				}
				if (frameRows == 0) firstIntervalMissing = csv(row, header, "interval_ms").isEmpty();
				clientTicks += Long.parseLong(csv(row, header, "client_tick_count"));
				if ("1".equals(csv(row, header, "save_overlap"))) saveOverlapRows++;
				frameRows++;
			}
		}
		requireSmoke(frameRows >= 2 && clientTicks > 0, "Runtime report did not capture normal frames and client ticks");
		JsonObject all = summary.getAsJsonObject("all"), retention = summary.getAsJsonObject("retention");
		requireSmoke(all.get("frame_rows").getAsLong() == frameRows && all.get("gpu_missing_frames").getAsLong() == frameRows - gpuRows, "Runtime summary frame/GPU counts disagree with raw CSV");
		requireSmoke(all.getAsJsonObject("gpu_render_ms").get("samples").getAsLong() == gpuRows, "Runtime GPU statistics counted missing samples as zero");
		if (retention.get("overwritten_frame_rows").getAsLong() == 0) requireSmoke(firstIntervalMissing, "First runtime frame interval was not left missing");
		String gpuStatus = summary.get("gpu_status").getAsString();
		boolean gpuUnavailable = gpuStatus.startsWith("unavailable:");
		if (gpu) requireSmoke(gpuRows > 0 || gpuUnavailable, "Runtime GPU capture returned no samples without a clear unsupported/unavailable reason: " + gpuStatus);
		else requireSmoke(gpuRows == 0, "CPU-only capture accepted a GPU result, including an old-generation result");
		long eventRows = 0, serverTicks = 0, clientDroneTicks = 0, serverDroneTicks = 0, saveEvents = 0, metadataEvents = 0, movementEvents = 0;
		try (BufferedReader reader = Files.newBufferedReader(report.resolve("events.csv"), StandardCharsets.UTF_8)) {
			Map<String, Integer> header = csvHeader(reader.readLine(), List.of("type", "start_ms", "duration_ms", "interval_ms", "entity_id",
					"save_silent", "save_flush", "save_force", "save_result", "value_a", "value_a_semantics", "value_b_semantics"));
			String line;
			while ((line = reader.readLine()) != null) {
				List<String> row = csvFields(line);
				requireSmoke(row.size() == header.size(), "Runtime event CSV has an inconsistent column count");
				nonNegativeCsv(row, header, "start_ms", false);
				nonNegativeCsv(row, header, "duration_ms", true);
				nonNegativeCsv(row, header, "interval_ms", true);
				requireSmoke(!Integer.toString(STALE_RUNTIME_ENTITY_ID).equals(csv(row, header, "entity_id")), "Old-generation drone event leaked into a new capture");
				switch (csv(row, header, "type")) {
					case "SERVER_TICK" -> serverTicks++;
					case "CLIENT_DRONE" -> clientDroneTicks++;
					case "SERVER_DRONE" -> serverDroneTicks++;
					case "SAVE" -> saveEvents++;
					case "METADATA" -> metadataEvents++;
					case "MOVEMENT" -> movementEvents++;
					default -> { }
				}
				eventRows++;
			}
		}
		requireSmoke(serverTicks > 0 && clientDroneTicks > 0 && serverDroneTicks > 0, "Runtime server/client drone timing hooks did not produce events");
		requireSmoke(retention.get("retained_event_rows").getAsLong() == eventRows, "Runtime event count disagrees with raw CSV");
		boolean ordinarySaveFound = false;
		for (var element : summary.getAsJsonArray("save_timeline")) {
			JsonObject save = element.getAsJsonObject();
			if (save.get("begin_retained").getAsBoolean() && save.get("finish_retained").getAsBoolean()
					&& save.get("silent").getAsBoolean() && !save.get("flush").getAsBoolean() && !save.get("force").getAsBoolean()
					&& "returned_true".equals(save.get("result").getAsString())) ordinarySaveFound = true;
		}
		if (ordinarySave) requireSmoke(ordinarySaveFound && saveEvents > 0 && saveOverlapRows > 0, "Runtime ordinary save flags/results or frame overlap were not captured");
		if (disconnected) {
			requireSmoke(saveEvents > 0 && runtimeNestedClosingFrames > 0 && runtimeNestedTokensValid, "Runtime disconnect capture missed saves or nested runTick(false) validation");
			requireSmoke(retention.get("incomplete_frames").getAsLong() <= 1, "Nested disconnect frames corrupted multiple outer capture rows");
		}
		JsonObject result = new JsonObject();
		result.addProperty("passed", true);
		result.addProperty("report_directory", runDirectory.relativize(report).toString());
		result.addProperty("capture_generation_verified", expectedId);
		result.addProperty("frame_rows", frameRows);
		result.addProperty("event_rows", eventRows);
		result.addProperty("client_ticks", clientTicks);
		result.addProperty("gpu_samples", gpuRows);
		result.addProperty("gpu_status", gpuStatus);
		result.addProperty("gpu_unavailable_with_explicit_reason", gpuUnavailable);
		result.addProperty("save_events", saveEvents);
		result.addProperty("ordinary_save_verified", ordinarySaveFound);
		result.addProperty("save_overlap_frames", saveOverlapRows);
		result.addProperty("metadata_delivery_events", metadataEvents);
		result.addProperty("movement_delivery_events", movementEvents);
		if (gpu) {
			requireSmoke(runtimeScreenshotCaptureId == expectedId && runtimeScreenshotPath != null
					&& Files.isRegularFile(runtimeScreenshotPath) && Files.size(runtimeScreenshotPath) > 0, "Runtime GPU HUD screenshot did not save during the active GPU capture");
			result.addProperty("gpu_hud_screenshot", runDirectory.relativize(runtimeScreenshotPath).toString());
			result.addProperty("gpu_hud_screenshot_capture_id", runtimeScreenshotCaptureId);
			result.addProperty("vanilla_gpu_debug_enabled_during_capture", true);
		}
		return result;
	}

	private static void requireSmoke(boolean condition, String message) {
		if (!condition) throw new IllegalStateException(message);
	}

	private static Map<String, Integer> csvHeader(String line, List<String> required) {
		requireSmoke(line != null, "Runtime CSV is empty");
		List<String> fields = csvFields(line);
		Map<String, Integer> header = new LinkedHashMap<>();
		for (int index = 0; index < fields.size(); index++) header.put(fields.get(index), index);
		for (String name : required) requireSmoke(header.containsKey(name), "Runtime CSV is missing " + name);
		return header;
	}

	private static List<String> csvFields(String line) {
		List<String> fields = new ArrayList<>();
		StringBuilder value = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < line.length(); index++) {
			char character = line.charAt(index);
			if (character == '"') {
				if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') { value.append('"'); index++; }
				else quoted = !quoted;
			} else if (character == ',' && !quoted) { fields.add(value.toString()); value.setLength(0); }
			else value.append(character);
		}
		requireSmoke(!quoted, "Runtime CSV has an unterminated quoted field");
		fields.add(value.toString());
		return fields;
	}

	private static String csv(List<String> row, Map<String, Integer> header, String name) { return row.get(header.get(name)); }
	private static double nonNegativeCsv(List<String> row, Map<String, Integer> header, String name, boolean missingAllowed) {
		String text = csv(row, header, name);
		if (missingAllowed && text.isEmpty()) return -1;
		double value = Double.parseDouble(text);
		requireSmoke(Double.isFinite(value) && value >= 0, "Runtime CSV has invalid " + name);
		return value;
	}

	private void checkDeadline(Minecraft client) {
		long now = System.nanoTime();
		int deadlineSeconds = remotePilotSmoke == null && placementBindingSmoke == null ? 180 : 300;
		if (now - started > deadlineSeconds * 1_000_000_000L) {
			finish(client, false, "Probe exceeded its " + deadlineSeconds + " second deadline");
		} else if (PerformanceTimings.phase() == Phase.CLOSING && now - phaseStarted > (renderStress ? 90_000_000_000L : 30_000_000_000L)) {
			finish(client, false, "Vanilla synthetic world teardown exceeded its bounded deadline");
		}
	}

	private void endMeasurement(Minecraft client) {
		if (runtimeMonitorSmoke) {
			runtimeValidation = stopAndValidateRuntime(client, runtimeCpuId, true, false, false);
			transition(Phase.RUNTIME_CPU_EXPORT);
		} else {
			transition(Phase.FLUSH);
			operation = submit(client.getSingleplayerServer(), server -> server.saveEverything(true, true, false));
		}
	}

	/** Queued vanilla key mappings exercise the production controls and packet path. */
	private boolean exerciseRenderStressCamera(Minecraft client) {
		DroneEntity drone = DroneClientState.controlledDrone();
		if (client.player == null || drone == null || !drone.getUUID().equals(fixture.droneId)) return false;
		if (stressCameraStageTick == 0) stressCameraStageTick = client.player.tickCount;
		if (client.player.tickCount - stressCameraStageTick > 300) throw new IllegalStateException("Render stress camera flow stalled at stage " + stressCameraStage);
		boolean advance = false;
		switch (stressCameraStage) {
			case 0 -> {
				if (DroneClientControls.config().gamepadEnabled()) clickKey(GLFW.GLFW_KEY_G);
				clickKey(GLFW.GLFW_KEY_V); advance = true;
			}
			case 1 -> {
				if (DroneClientState.isVirtualControllerEnabled() && !DroneClientControls.config().gamepadEnabled()
						&& DroneClientState.inputSource() == DroneClientState.InputSource.KEYBOARD) {
					stressReport.addProperty("keyboard_input_selected", true);
					clickKey(GLFW.GLFW_KEY_B); advance = true;
				}
			}
			case 2 -> {
				if (renderStressFpvReady(client)) {
					stressReport.addProperty("fpv_camera_followed_drone", true);
					clickKey(GLFW.GLFW_KEY_M); advance = true;
				}
			}
			case 3, 4, 5 -> {
				FlightMode expected = stressCameraStage == 3 ? FlightMode.HORIZON : stressCameraStage == 4 ? FlightMode.ACRO : FlightMode.ANGLE;
				if (DroneClientState.flightMode() == expected && drone.getFlightMode() == expected && stressServerModeId == expected.id()) {
					stressReport.addProperty("mode_" + expected.name().toLowerCase(), true);
					clickKey(stressCameraStage == 5 ? GLFW.GLFW_KEY_B : GLFW.GLFW_KEY_M); advance = true;
				}
			}
			case 6 -> {
				if (DroneClientState.isThirdPersonActive(client.level) && client.getCameraEntity() == drone && stressServerRemoteView
						&& client.player.tickCount - stressCameraStageTick >= 3) {
					stressReport.addProperty("third_person_drone_camera_rendered", true);
					clickKey(GLFW.GLFW_KEY_B); advance = true;
				}
			}
			case 7 -> {
				if (DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT && client.getCameraEntity() == client.player && !stressServerRemoteView) {
					stressReport.addProperty("initial_camera_return", true);
					clickKey(GLFW.GLFW_KEY_B); advance = true;
				}
			}
			case 8 -> {
				if (renderStressFpvReady(client)) { stressCameraSmokeSucceeded = true; advance = true; }
			}
			default -> { return stressCameraSmokeSucceeded; }
		}
		if (advance) { stressCameraStage++; stressCameraStageTick = client.player.tickCount; }
		return stressCameraSmokeSucceeded;
	}

	private boolean renderStressFpvReady(Minecraft client) {
		DroneEntity drone = DroneClientState.controlledDrone();
		return drone != null && drone.getUUID().equals(fixture.droneId) && DroneClientState.isFpvActive(client.level)
				&& client.getCameraEntity() == drone && stressServerRemoteView
				&& client.gameRenderer.getMainCamera().position().distanceTo(drone.position()) < 3.0;
	}

	private static void clickKey(int key) {
		KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(key));
	}

	private void beginRenderStressReturn(Minecraft client, boolean forFlush) {
		stressRouteActive = false;
		stressReturnForFlush = forFlush;
		stressReport.addProperty(forFlush ? "terrain_render_complete_after_second_route" : "terrain_render_complete_after_first_route", client.levelRenderer.hasRenderedAllSections());
		stressReport.addProperty(forFlush ? "section_statistics_after_second_route" : "section_statistics_after_first_route", client.levelRenderer.getSectionStatistics());
		if (DroneClientState.viewMode() == DroneViewMode.FPV) { clickKey(GLFW.GLFW_KEY_B); clickKey(GLFW.GLFW_KEY_B); }
		else if (DroneClientState.viewMode() == DroneViewMode.THIRD_PERSON) clickKey(GLFW.GLFW_KEY_B);
		operation = submit(client.getSingleplayerServer(), server -> {
			DroneEntity drone = (DroneEntity) server.overworld().getEntity(fixture.droneId);
			requireSmoke(drone != null, "Render stress drone disappeared during its route");
			drone.setPos(fixture.droneHome);
			drone.setDeltaMovement(Vec3.ZERO);
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.setCamera(player);
			player.teleportTo(fixture.playerHome.x(), fixture.playerHome.y(), fixture.playerHome.z());
			return true;
		});
		transition(Phase.CAMERA_RETURN);
	}

	/** This drives a synthetic pose, not the flight model or physical controller. */
	private void driveRenderStressRoute(MinecraftServer server) {
		Fixture currentFixture = stressFixture;
		if (currentFixture == null || finished || server.getPlayerList().getPlayers().isEmpty()) return;
		assertServerThread(server);
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		if (!(server.overworld().getEntity(currentFixture.droneId) instanceof DroneEntity drone)) return;
		Phase current = PerformanceTimings.phase();
		stressServerRemoteView = player.getCamera() == player && DroneRemoteSessions.isRemoteView(player) && DroneRemoteSessions.trackedDrone(player) == drone;
		stressServerModeId = drone.getFlightMode().id();
		double x = currentFixture.droneHome.x(), z = currentFixture.droneHome.z();
		if (stressRouteActive && (current == Phase.BASELINE || current == Phase.AFTER_SAVE)) {
			if (stressServerPhase != current) { stressServerPhase = current; stressRouteTick = 0; }
			double angle = stressRouteTick++ * STRESS_SPEED / (20.0 * STRESS_RADIUS);
			x += STRESS_RADIUS * (1.0 - Math.cos(angle));
			z += STRESS_RADIUS * Math.sin(angle);
			if (current == Phase.BASELINE) stressBaselineTicks++; else stressAfterSaveTicks++;
		}
		drone.setPos(x, currentFixture.droneHome.y(), z);
		drone.setDeltaMovement(Vec3.ZERO);
	}

	private Fixture createFixture(MinecraftServer server) {
		assertServerThread(server);
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		level.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
		level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
		level.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
		level.setDayTime(6000);
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8);
		if (modelPreview != null) ModelPreviewCapture.preparePlatform(level, surface);
		int radius = Math.clamp(Integer.getInteger("fpvdrone.performance.dirtyRadius", 2), 0, 4);
		List<BlockPos> dirtyPositions = new ArrayList<>();
		for (int chunkX = -radius; chunkX <= radius; chunkX++) {
			for (int chunkZ = -radius; chunkZ <= radius; chunkZ++) {
				BlockPos pos = new BlockPos(chunkX * 16 + 8, surface - 1, chunkZ * 16 + 8);
				level.setBlock(pos, Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
				dirtyPositions.add(pos);
			}
		}
		BlockPos marker = new BlockPos(8, surface, 8);
		level.setBlock(marker, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
		Vec3 playerHome = new Vec3(12, surface + (renderStress ? 97 : 1), 12);
		Vec3 droneHome = new Vec3(8, surface + (renderStress ? 96 : modelPreview != null ? 0.075 : 0), 12);
		player.teleportTo(playerHome.x(), playerHome.y(), playerHome.z());
		DroneEntity drone = new DroneEntity(DroneEntityTypes.DRONE, level);
		drone.setOwner(player.getUUID());
		drone.setPos(droneHome);
		drone.setInvulnerable(true);
		drone.setCustomName(Component.literal("FPV synthetic save fixture"));
		if (!level.addFreshEntity(drone)) throw new IllegalStateException("Could not spawn synthetic drone");
		return new Fixture(drone.getUUID(), player.getUUID(), marker, drone.fixedFlightModelIdForDiagnostics(), drone.config().massKg(), List.copyOf(dirtyPositions), droneHome, playerHome);
	}

	private JsonObject verifyFixture(MinecraftServer server) {
		assertServerThread(server);
		ServerLevel level = server.overworld();
		// World/entity lookup happens only here, on the server thread after reopen.
		level.getChunk(fixture.marker);
		DroneEntity restored = level.getEntitiesOfClass(DroneEntity.class, new AABB(fixture.droneHome, fixture.droneHome).inflate(32), entity -> entity.getUUID().equals(fixture.droneId)).stream().findFirst().orElse(null);
		JsonObject result = new JsonObject();
		boolean marker = level.getBlockState(fixture.marker).is(Blocks.GOLD_BLOCK);
		boolean changedChunks = fixture.dirtyPositions.stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.DIAMOND_BLOCK));
		boolean owner = restored != null && fixture.ownerId.equals(restored.getOwner());
		boolean model = restored != null && fixture.modelId.equals(restored.fixedFlightModelIdForDiagnostics());
		boolean mass = restored != null && Math.abs(fixture.massKg - restored.config().massKg()) < 1e-9;
		boolean name = restored != null && restored.getCustomName() != null && "FPV synthetic save fixture".equals(restored.getCustomName().getString());
		result.addProperty("marker_block_persisted", marker);
		result.addProperty("fixture_chunk_changes_persisted_after_flush_and_reopen", changedChunks);
		result.addProperty("drone_uuid_persisted", restored != null);
		result.addProperty("drone_owner_persisted", owner);
		result.addProperty("drone_model_persisted", model);
		result.addProperty("drone_mass_persisted", mass);
		result.addProperty("drone_name_persisted", name);
		boolean placementBinding = placementBindingSmoke == null || placementBindingSmoke.verifyReopened(server);
		if (placementBindingSmoke != null) result.addProperty("placement_binding_and_native_entities_persisted", placementBinding);
		result.addProperty("passed", marker && changedChunks && owner && model && mass && name && placementBinding);
		return result;
	}

	private void queueTimingPing(MinecraftServer server) {
		if (queuedPings.get() >= 8) {
			PerformanceTimings.controlDropped();
			return;
		}
		long submitted = System.nanoTime();
		Phase phase = PerformanceTimings.phase();
		queuedPings.incrementAndGet();
		PerformanceTimings.controlSubmitted();
		server.execute(() -> {
			PerformanceTimings.add(phase, Metric.CLIENT_TO_SERVER_QUEUE_DELAY, System.nanoTime() - submitted);
			PerformanceTimings.controlCompleted();
			queuedPings.decrementAndGet();
		});
	}

	private void startJfr() {
		if (!Boolean.getBoolean("fpvdrone.performance.jfr")) return;
		try {
			recording = new Recording(Configuration.getConfiguration("profile"));
			recording.setName(renderStress ? "fpv-normal-client-render-stress-probe" : "fpv-normal-client-save-probe");
			recording.setToDisk(true);
			recording.setMaxSize(64 * 1024 * 1024L);
			recording.start();
			jfrStatus = "recording";
		} catch (Exception exception) {
			jfrStatus = "unavailable: " + exception;
		}
	}

	private void finish(Minecraft client, boolean success, String reason) {
		if (finished) return;
		finished = true;
		failure = reason;
		if (modelPreview != null) modelPreview.restore(client);
		if (threeViewSmoke != null) threeViewSmoke.restore(client);
		if (controllerModelSmoke != null) controllerModelSmoke.restore(client);
		if (placementBindingSmoke != null) placementBindingSmoke.restore(client);
		if (remotePilotSmoke != null) remotePilotSmoke.restore(client);
		if (runtimeMonitorSmoke) {
			try {
				restoreGpuSmoke(client);
				if (RuntimePerformanceMonitor.enabled()) RuntimePerformanceMonitor.stop(client);
			} catch (Exception exception) {
				runtimeSmokeSucceeded = false;
				if (failure == null) failure = "Runtime smoke cleanup failed: " + exception;
			}
		}
		transition(Phase.REPORT);
		if (recording != null) {
			try {
				recording.stop();
				recording.dump(output.resolveSibling("normal-client-save-probe.jfr"));
				jfrStatus = "saved";
			} catch (Exception exception) {
				jfrStatus = "failed: " + exception;
			} finally {
				recording.close();
			}
		}
		try {
			JsonObject report = PerformanceTimings.report();
			report.addProperty("schema", "fpv-normal-client-save-probe-v1");
			report.addProperty("passed", success && autosaveSucceeded && flushSucceeded && reopenSucceeded && (!runtimeMonitorSmoke || runtimeSmokeSucceeded)
					&& (!renderStress || (stressCameraSmokeSucceeded && stressBaselineTicks > 0 && stressAfterSaveTicks > 0))
					&& (threeViewSmoke == null || threeViewSmoke.passed()) && (remotePilotSmoke == null || remotePilotSmoke.passed())
					&& (controllerModelSmoke == null || controllerModelSmoke.passed())
					&& (placementBindingSmoke == null || placementBindingSmoke.passed()));
			report.addProperty("failure", failure);
			report.addProperty("world_id", worldId);
			report.addProperty("world_seed", SEED);
			report.addProperty("game_directory", runDirectory == null ? "uninitialized" : runDirectory.toString());
			report.addProperty("hidden_window", hidden);
			report.addProperty("configured_framerate_option", FRAME_LIMIT);
			report.addProperty("limiter_disabled_at_max_option", true);
			report.addProperty("window_width", windowWidth);
			report.addProperty("window_height", windowHeight);
			report.addProperty("framebuffer_width", client.getWindow().getWidth());
			report.addProperty("framebuffer_height", client.getWindow().getHeight());
			report.addProperty("render_distance", renderDistance);
			report.addProperty("simulation_distance", simulationDistance);
			report.addProperty("warmup_seconds", warmupDuration / 1_000_000_000.0);
			report.addProperty("steady_phase_seconds", phaseDuration / 1_000_000_000.0);
			report.addProperty("fixture_chunks_dirtied_before_autosave", fixture == null ? 0 : fixture.dirtyPositions.size());
			report.addProperty("autosave_equivalent_returned_success", autosaveSucceeded);
			report.addProperty("explicit_flush_returned_success", flushSucceeded);
			report.addProperty("reopen_verification_passed", reopenSucceeded);
			report.addProperty("jfr", jfrStatus);
			report.add("verification", verification);
			if (threeViewSmoke != null) report.add("three_view_smoke", threeViewSmoke.summary());
			if (controllerModelSmoke != null) report.add("controller_model_smoke", controllerModelSmoke.summary());
			if (placementBindingSmoke != null) report.add("placement_binding_smoke", placementBindingSmoke.summary());
			if (remotePilotSmoke != null) report.add("remote_pilot_smoke", remotePilotSmoke.summary());
			if (renderStress) {
				stressReport.addProperty("enabled", true);
				stressReport.addProperty("world_preset", "normal");
				stressReport.addProperty("scope", "synthetic camera/render/chunk-loading fixture; not a user-world FPS or flight-physics validation");
				stressReport.addProperty("camera_and_mode_flow_passed", stressCameraSmokeSucceeded);
				stressReport.addProperty("route", "server-tick-driven horizontal circuit; reset to the same origin before each measured phase");
				stressReport.addProperty("radius_meters", STRESS_RADIUS);
				stressReport.addProperty("commanded_meters_per_simulation_second", STRESS_SPEED);
				stressReport.addProperty("baseline_route_server_ticks", stressBaselineTicks);
				stressReport.addProperty("after_save_route_server_ticks", stressAfterSaveTicks);
				stressReport.addProperty("baseline_commanded_distance_meters", Math.max(0, stressBaselineTicks - 1) * STRESS_SPEED / 20.0);
				stressReport.addProperty("after_save_commanded_distance_meters", Math.max(0, stressAfterSaveTicks - 1) * STRESS_SPEED / 20.0);
				stressReport.addProperty("route_displacement_is_diagnostic_pose_drive", true);
				report.add("render_stress", stressReport);
			}
			if (runtimeMonitorSmoke) {
				runtimeSmokeReport.addProperty("enabled", true);
				runtimeSmokeReport.addProperty("passed", runtimeSmokeSucceeded && reopenSucceeded);
				runtimeSmokeReport.addProperty("scope", "functional smoke only; not performance-score evidence");
				runtimeSmokeReport.addProperty("cpu_capture_id", runtimeCpuId);
				runtimeSmokeReport.addProperty("gpu_capture_id", runtimeGpuId);
				runtimeSmokeReport.addProperty("restarted_cpu_capture_id", runtimeRestartId);
				runtimeSmokeReport.addProperty("nested_closing_runTick_false_calls", runtimeNestedClosingFrames);
				runtimeSmokeReport.addProperty("frames_during_async_export_wait", runtimeExportWaitFrames);
				runtimeSmokeReport.addProperty("nested_frames_have_no_capture_token", runtimeNestedTokensValid);
				runtimeSmokeReport.addProperty("gpu_debug_status_restored", !gpuDebugOverridden);
				report.add("runtime_monitor_smoke", runtimeSmokeReport);
			}
			if (modelPreview != null) {
				JsonObject preview = modelPreview.summary();
				boolean probePassed = report.get("passed").getAsBoolean();
				modelPreview.writeReport(probePassed, failure);
				report.add("model_preview", preview);
				report.addProperty("passed", probePassed && preview.get("passed").getAsBoolean());
			}
			if (remotePilotSmoke != null) remotePilotSmoke.writeReport(report.get("passed").getAsBoolean(), failure);
			if (controllerModelSmoke != null) controllerModelSmoke.writeReport(report.get("passed").getAsBoolean(), failure);
			if (placementBindingSmoke != null) placementBindingSmoke.writeReport(report.get("passed").getAsBoolean(), failure);
			JsonArray limits = new JsonArray();
			limits.add("Normal Minecraft run loop; no ClientGameTest entrypoint, test mode, or regulated test ticks are active. The client-gametest API module remains present within bundled Fabric API.");
			limits.add("Hidden-window timing is synthetic and does not establish physical display FPS or a 115 FPS guarantee.");
			limits.add("The configured framerate option is 260, which disables vanilla's frame limiter at its maximum option value; it is not a 260 Hz cap.");
			limits.add(renderStress
					? "A fixed-seed normal-terrain scene and a scripted camera circuit do not reproduce the user's route, populated world, physical flight or controller. New generation, worker caches and hidden-window behavior can differ between runs."
					: "A fixed-seed flat scene with one grounded unarmed drone and bounded initial dirty chunks does not reproduce an existing user's world load.");
			limits.add("Frame interval measures normal runTick(true) starts; render duration measures GameRenderer CPU submission, not GPU completion.");
			limits.add("Client-to-server probes measure task queue delay, not controller hardware polling or actual input packet round-trip latency.");
			limits.add("Opening, closing, reopening, explicit flush, and paused server ticks are reported separately from steady gameplay.");
			limits.add("Autosave-equivalent call uses vanilla saveEverything(true,false,false); all world IO and flush/shutdown behavior remain vanilla.");
			limits.add("Counters and nanosecond hooks add diagnostic overhead; optional JFR adds further overhead.");
			if (renderStress) {
				limits.add("The route commands 64 meters per simulation second through server-thread pose updates, not physical flight. A slow server advances fewer route ticks in each wall-clock phase; compare the recorded route ticks and commanded distance as well as frame timings.");
				limits.add("Baseline and after-save each repeat the same bounded circuit. They are not guaranteed cold/warm cache phases; terrain render completion is recorded separately and may be false while work is pending.");
			}
			if (runtimeMonitorSmoke) limits.add("Runtime monitor smoke adds GPU queries, a disposable GPU debug-entry setting, screenshot readback, exports and report validation; this is functional evidence only, not a performance benchmark.");
			if (threeViewSmoke != null) limits.add("Three-view smoke uses real production key mappings, client metadata, model submissions and a disposable block wall. Camera/renderer compatibility and reset checks are functional evidence, not physical flight or a performance benchmark.");
			if (controllerModelSmoke != null) limits.add("Controller-model smoke equips real synchronized controller/shield stacks in both hand arrangements, observes native hand passes and resolved item models, and captures ordinary LOS, FPV, drone-third-person and inventory rendering. Hooks observe rendering without changing production suppression; this is functional evidence, not a performance benchmark.");
			if (placementBindingSmoke != null) limits.add("Placement/binding smoke uses native survival item placement and shift/entity interaction packets, exact farther-drone controller selection, real arming and yaw inputs, native player model extraction/submission and ordinary save/reopen. Its static drone camera pose is a diagnostic fixture; body rendering hooks only observe and never override vanilla filtering.");
			if (remotePilotSmoke != null) limits.add("Remote-pilot smoke uses a survival body, actual Husk melee and native gravity, plus an 8m/server-tick diagnostic drone route through new chunks in FPV and LOS; this proves functional behavior in a disposable world, not physical flight performance.");
			report.add("measurement_limits", limits);
			JsonArray evidence = new JsonArray();
			evidence.add("Minecraft.runTick(boolean), Minecraft.tick(), GameRenderer.render(DeltaTracker,boolean)");
			evidence.add("IntegratedServer.tickServer(BooleanSupplier), MinecraftServer.saveEverything(boolean,boolean,boolean)");
			evidence.add("MinecraftServer.autoSave calls saveEverything(true,false,false) in the local mapped 1.21.11 implementation");
			report.add("observed_method_evidence", evidence);
			if (output != null) Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
			FpvDronecraftMod.LOGGER.info("Normal client performance probe completed, passed={}, report={}", report.get("passed"), output);
		} catch (Exception exception) {
			FpvDronecraftMod.LOGGER.error("Could not write normal client performance report", exception);
		} finally {
			// Normal client stop lets Minecraft perform its own world shutdown/flush.
			client.stop();
		}
	}

	private static boolean ready(Minecraft client) {
		return client.level != null && client.player != null && client.getSingleplayerServer() != null && client.getSingleplayerServer().isReady();
	}
	private void transition(Phase phase) {
		PerformanceTimings.phase(phase);
		phaseStarted = System.nanoTime();
		FpvDronecraftMod.LOGGER.info("Normal client performance probe phase: {}", phase);
	}
	private static String requiredProperty(String name) {
		String value = System.getProperty(name);
		if (value == null || value.isBlank()) throw new IllegalStateException("Missing opt-in probe property: " + name);
		return value;
	}
	private static long secondsProperty(String name, int defaultValue) {
		return Math.clamp(Integer.getInteger(name, defaultValue), 2, 30) * 1_000_000_000L;
	}
	private static int integerProperty(String name, int defaultValue, int minimum, int maximum) {
		return Math.clamp(Integer.getInteger(name, defaultValue), minimum, maximum);
	}
	private static void assertServerThread(MinecraftServer server) {
		if (!server.isSameThread()) throw new IllegalStateException("Synthetic world access must run on the server thread");
	}
	private static <T> CompletableFuture<T> submit(MinecraftServer server, ServerOperation<T> operation) {
		if (server == null) throw new IllegalStateException("Integrated server is unavailable");
		CompletableFuture<T> result = new CompletableFuture<>();
		server.execute(() -> {
			try {
				assertServerThread(server);
				result.complete(operation.run(server));
			} catch (Throwable exception) {
				result.completeExceptionally(exception);
			}
		});
		return result;
	}
	@FunctionalInterface private interface ServerOperation<T> { T run(MinecraftServer server); }
	private record Fixture(UUID droneId, UUID ownerId, BlockPos marker, String modelId, double massKg, List<BlockPos> dirtyPositions, Vec3 droneHome, Vec3 playerHome) { }
}
