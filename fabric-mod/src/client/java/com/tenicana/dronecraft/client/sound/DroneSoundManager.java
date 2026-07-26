package com.tenicana.dronecraft.client.sound;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.AcousticSourceFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.concurrent.LatestWinsProcessor;
import com.tenicana.dronecraft.acoustics.path.AirPortalGraph;
import com.tenicana.dronecraft.acoustics.path.AirPortalGraphCache;
import com.tenicana.dronecraft.acoustics.propagation.EmpiricalDiffraction;
import com.tenicana.dronecraft.acoustics.propagation.HybridPropagationSolver;
import com.tenicana.dronecraft.acoustics.propagation.MultiPathSolver;
import com.tenicana.dronecraft.acoustics.propagation.RotorDiskProbes;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import com.tenicana.dronecraft.acoustics.reverb.LateReverbEstimator;
import com.tenicana.dronecraft.acoustics.reverb.ReflectionStatistics;
import com.tenicana.dronecraft.acoustics.reverb.ReflectionVolume;
import com.tenicana.dronecraft.acoustics.reverb.VoxelReflectionProbe;
import com.tenicana.dronecraft.acoustics.voxel.DdaProductionSnapshotBundle;
import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.sound.DroneSoundPhysics;

public final class DroneSoundManager {
	private static final int MAX_ACTIVE_DRONES = 6;
	private static final double START_DISTANCE_SQUARED = 48.0 * 48.0;
	private static final double RELEASE_DISTANCE_SQUARED = 64.0 * 64.0;
	private static final int FADE_OUT_TICKS = 20;
	private static final int RESTART_CHECK_INTERVAL_TICKS = 20;
	private static final String DDA_EXPORT_PATH_PROPERTY =
			"fpvdrone.acoustics.ddaExport";
	private static final Map<Integer, DroneSoundSet> ACTIVE_SOUNDS = new HashMap<>();
	private static final DirectPropagationController DIRECT_PROPAGATION = new DirectPropagationController();
	private static final ListenerReverbController LISTENER_REVERB =
			new ListenerReverbController();
	private static final OpenAlEfxController OPENAL_EFX =
			new OpenAlEfxController();
	private static final AcousticBackendRuntimeTelemetry BACKEND_TELEMETRY =
			new AcousticBackendRuntimeTelemetry();
	private static final OpenAlNativeDopplerGuard OPENAL_DOPPLER_GUARD =
			new OpenAlNativeDopplerGuard();
	private static final AudioLabController AUDIO_LAB =
			new AudioLabController();
	private static final MinecraftLocalPlaneSceneScheduler
			LOCAL_PLANE_RESEARCH =
			new MinecraftLocalPlaneSceneScheduler();
	private static final PropagationCompatibilityPolicy.Decision
			PROPAGATION_COMPATIBILITY = detectPropagationCompatibility();
	private static ClientLevel activeLevel;

	private DroneSoundManager() {
	}

	public static void initialize() {
		FpvDronecraftMod.LOGGER.info(
				"Acoustic propagation ownership: {} ({})",
				PROPAGATION_COMPATIBILITY.internalPropagationEnabled()
						? "Dronecraft internal"
						: "clean source / external",
				PROPAGATION_COMPATIBILITY.reason()
		);
		ClientTickEvents.END_CLIENT_TICK.register(DroneSoundManager::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			stopAll(client);
			DIRECT_PROPAGATION.close();
			LISTENER_REVERB.close();
			LOCAL_PLANE_RESEARCH.close();
		});
	}

	public static Path requestDdaDiagnosticExport(Path output) {
		return DIRECT_PROPAGATION.requestDiagnosticExport(output);
	}

	public static String ddaDiagnosticExportStatus() {
		return DIRECT_PROPAGATION.diagnosticExportStatus();
	}

	public static Path requestAudioLabDiagnostic(
			Path output,
			String backend,
			boolean reload
	) {
		if (ACTIVE_SOUNDS.isEmpty()) {
			throw new IllegalStateException(
					"audio lab requires at least one active drone sound"
			);
		}
		AcousticBackendSelector.Mode mode = switch (backend) {
			case "dry" -> AcousticBackendSelector.Mode.DRY;
			case "java-fdn" -> AcousticBackendSelector.Mode.JAVA_FDN;
			case "openal-efx" ->
					AcousticBackendSelector.Mode.OPENAL_EFX;
			default -> throw new IllegalArgumentException(
					"unknown audio-lab backend: " + backend
			);
		};
		return AUDIO_LAB.start(
				output,
				mode,
				reload
						? AudioLabController.Variant.RELOAD
						: AudioLabController.Variant.CONTROL
		);
	}

	public static String audioLabDiagnosticStatus() {
		return AUDIO_LAB.status();
	}

	public static String acousticBackendRuntimeStatus() {
		return BACKEND_TELEMETRY.status();
	}

	public static Path exportAcousticBackendTimeline(Path output) {
		Path normalized = output.toAbsolutePath().normalize();
		try {
			Files.createDirectories(normalized.getParent());
			Files.writeString(
					normalized,
					BACKEND_TELEMETRY.timelineJson(),
					StandardCharsets.UTF_8
			);
		} catch (IOException error) {
			throw new IllegalStateException(
					"could not export backend metadata timeline",
					error
			);
		}
		return normalized;
	}

	public static void onSoundEngineReload() {
		OPENAL_EFX.onSoundEngineReload();
	}

	static OpenAlEfxController.Diagnostics openAlEfxDiagnostics() {
		return OPENAL_EFX.diagnostics();
	}

	static OpenAlNativeDopplerGuard.Diagnostics
	openAlDopplerGuardDiagnostics() {
		return OPENAL_DOPPLER_GUARD.diagnostics();
	}

	static List<DopplerPcmRuntimeProbe.Result>
	captureDopplerPcmConformance() {
		List<DopplerPcmRuntimeProbe.Result> results = new ArrayList<>();
		for (DroneSoundSet sounds : ACTIVE_SOUNDS.values()) {
			results.add(DopplerPcmRuntimeProbe.capture(
					sounds.acousticState
			));
		}
		return List.copyOf(results);
	}

	static boolean hasMovingDopplerPcmFlightState() {
		for (DroneSoundSet sounds : ACTIVE_SOUNDS.values()) {
			AcousticSourceFrame source = sounds.acousticState.sourceFrame();
			if (source != null
					&& source.velocityMetersPerSecond().length() >= 0.25
					&& Math.abs(
							sounds.acousticState.dopplerFrequencyRatio() - 1.0
					) > 1.0e-6) {
				return true;
			}
		}
		return false;
	}

	static boolean javaListenerReverbActiveForDiagnostics() {
		return LISTENER_REVERB.historyDiagnostics().wetActive();
	}

	static AcousticBackendRuntimeTelemetry.Snapshot
	acousticBackendRuntimeDiagnostics() {
		return BACKEND_TELEMETRY.snapshot();
	}

	static List<AcousticBackendRuntimeTelemetry.Event>
	acousticBackendTimelineDiagnostics() {
		return BACKEND_TELEMETRY.timelineSnapshot();
	}

	private static void tick(Minecraft client) {
		if (client.level == null) {
			stopAll(client);
			activeLevel = null;
			return;
		}
		if (client.level != activeLevel) {
			stopAll(client);
			activeLevel = client.level;
		}
		if (client.options.getFinalSoundSourceVolume(SoundSource.NEUTRAL) <= 0.0f) {
			stopAll(client);
			return;
		}

		Entity listener = client.getCameraEntity() != null ? client.getCameraEntity() : client.player;
		if (listener == null) {
			stopAll(client);
			return;
		}

		List<DroneCandidate> candidates = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof DroneEntity drone) || !drone.isAlive() || drone.isRemoved()) {
				continue;
			}
			double distanceSquared = drone.distanceToSqr(listener);
			if (drone.isSilent()
					|| distanceSquared > START_DISTANCE_SQUARED
					|| !DroneSoundPhysics.isAudible(drone.getAverageMotorRpm())) {
				continue;
			}
			candidates.add(new DroneCandidate(drone, distanceSquared));
		}
		candidates.sort(Comparator.comparingDouble(DroneCandidate::distanceSquared));

		Set<Integer> selected = new HashSet<>();
		int selectedCount = Math.min(MAX_ACTIVE_DRONES, candidates.size());
		for (int index = 0; index < selectedCount; index++) {
			selected.add(candidates.get(index).drone().getId());
		}
		for (int index = 0; index < selectedCount; index++) {
			DroneEntity drone = candidates.get(index).drone();
			DroneSoundSet current = ACTIVE_SOUNDS.get(drone.getId());
			if (current != null && current.drone() != drone) {
				current.stop(client.getSoundManager());
				ACTIVE_SOUNDS.remove(drone.getId());
				current = null;
			}
			if (current == null) {
				makeRoomForSelectedSound(client.getSoundManager(), selected);
				if (ACTIVE_SOUNDS.size() >= MAX_ACTIVE_DRONES) {
					continue;
				}
				current = new DroneSoundSet(drone);
				ACTIVE_SOUNDS.put(drone.getId(), current);
			}
			current.ensureActive(client.getSoundManager(), listener);
		}

		Iterator<Map.Entry<Integer, DroneSoundSet>> iterator = ACTIVE_SOUNDS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, DroneSoundSet> entry = iterator.next();
			if (selected.contains(entry.getKey())) {
				continue;
			}
			DroneSoundSet sounds = entry.getValue();
			DroneEntity drone = sounds.drone();
			boolean valid = drone.level() == client.level
					&& drone.isAlive()
					&& !drone.isRemoved()
					&& !drone.isSilent();
			double distanceSquared = valid ? drone.distanceToSqr(listener) : Double.POSITIVE_INFINITY;
			boolean audible = valid && DroneSoundPhysics.isAudible(drone.getAverageMotorRpm());
			if (audible && distanceSquared <= RELEASE_DISTANCE_SQUARED) {
				continue;
			}
			boolean canFade = valid && distanceSquared <= RELEASE_DISTANCE_SQUARED && !audible;
			if (canFade && sounds.continueFadeOut()) {
				continue;
			}
			sounds.stop(client.getSoundManager());
			iterator.remove();
		}
		boolean internalPropagation =
				PROPAGATION_COMPATIBILITY.internalPropagationEnabled();
		if (internalPropagation || DIRECT_PROPAGATION.hasPendingDiagnosticExport()) {
			DIRECT_PROPAGATION.tick(
					client.level,
					listener,
					ACTIVE_SOUNDS,
					internalPropagation
			);
		} else {
			for (DroneSoundSet sounds : ACTIVE_SOUNDS.values()) {
				sounds.relaxTransmission();
			}
		}
		if (internalPropagation && LOCAL_PLANE_RESEARCH.enabled()) {
			Vec3 listenerPosition = listener.getEyePosition();
			List<MinecraftLocalPlaneSceneScheduler.Source> sources =
					new ArrayList<>(ACTIVE_SOUNDS.size());
			for (DroneSoundSet sounds : ACTIVE_SOUNDS.values()) {
				AcousticSourceFrame frame =
						sounds.acousticState.sourceFrame();
				if (frame != null) {
					sources.add(
							new MinecraftLocalPlaneSceneScheduler.Source(
									sounds.drone.getId(),
									sounds.drone.getUUID(),
									frame.positionMeters()
							)
					);
				}
			}
			LOCAL_PLANE_RESEARCH.tick(
					client.level,
					new AcousticVector(
							listenerPosition.x,
							listenerPosition.y,
							listenerPosition.z
					),
					sources
			);
		}
		AcousticBackendSelector.RuntimeBackend backend =
				AcousticBackendSelector.runtimeBackend(
						OPENAL_EFX.diagnostics().status()
				);
		LISTENER_REVERB.tick(
				client,
				listener,
				ACTIVE_SOUNDS,
				internalPropagation,
				backend
		);
		List<DroneLoopSoundInstance> activeLayers = new ArrayList<>();
		for (DroneSoundSet sounds : ACTIVE_SOUNDS.values()) {
			sounds.addActiveLayers(activeLayers);
		}
		OPENAL_DOPPLER_GUARD.tick(
				client.getSoundManager(),
				activeLayers
		);
		boolean efxMayOwnWetPath = backend
				== AcousticBackendSelector.RuntimeBackend.OPENAL_EFX
				|| backend
				== AcousticBackendSelector.RuntimeBackend.OPENAL_EFX_PENDING;
		if (internalPropagation && efxMayOwnWetPath) {
			OPENAL_EFX.tick(
					client.getSoundManager(),
					activeLayers,
					LISTENER_REVERB.environment()
			);
		} else {
			OPENAL_EFX.stop(client.getSoundManager());
		}
		BACKEND_TELEMETRY.publish(
				Math.max(0L, client.level.getGameTime()),
				backend,
				LISTENER_REVERB.environment(),
				OPENAL_EFX.diagnostics(),
				LISTENER_REVERB.historyDiagnostics()
		);
		AUDIO_LAB.tick(
				client,
				ACTIVE_SOUNDS.size(),
				LISTENER_REVERB.historyDiagnostics().wetActive(),
				OPENAL_EFX.diagnostics()
		);
	}

	private static PropagationCompatibilityPolicy.Decision
	detectPropagationCompatibility() {
		String configured = System.getProperty(
				PropagationCompatibilityPolicy.MODE_PROPERTY
		);
		try {
			return PropagationCompatibilityPolicy.resolve(
					configured,
					FabricLoader.getInstance()::isModLoaded
			);
		} catch (IllegalArgumentException error) {
			FpvDronecraftMod.LOGGER.warn(
					"Invalid {}; falling back to auto detection: {}",
					PropagationCompatibilityPolicy.MODE_PROPERTY,
					error.getMessage()
			);
			return PropagationCompatibilityPolicy.resolve(
					"auto",
					FabricLoader.getInstance()::isModLoaded
			);
		}
	}

	private static void makeRoomForSelectedSound(SoundManager soundManager, Set<Integer> selected) {
		if (ACTIVE_SOUNDS.size() < MAX_ACTIVE_DRONES) {
			return;
		}
		Iterator<Map.Entry<Integer, DroneSoundSet>> iterator = ACTIVE_SOUNDS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, DroneSoundSet> entry = iterator.next();
			if (selected.contains(entry.getKey())) {
				continue;
			}
			entry.getValue().stop(soundManager);
			iterator.remove();
			return;
		}
	}

	private static void stopAll(Minecraft client) {
		SoundManager soundManager = client.getSoundManager();
		AUDIO_LAB.abort("audio lab aborted: sound manager stopped");
		OPENAL_DOPPLER_GUARD.stop(soundManager);
		OPENAL_EFX.stop(soundManager);
		for (DroneSoundSet sounds : ACTIVE_SOUNDS.values()) {
			sounds.stop(soundManager);
		}
		ACTIVE_SOUNDS.clear();
		LISTENER_REVERB.stop(soundManager);
	}

	private static final class DroneSoundSet {
		private final DroneEntity drone;
		private final DroneAcousticRenderState acousticState = new DroneAcousticRenderState();
		private DroneLoopSoundInstance motor;
		private DroneLoopSoundInstance propeller;
		private int restartCheckTicks;
		private int fadeOutTicks;
		private int propagationStaleTicks;

		private DroneSoundSet(DroneEntity drone) {
			this.drone = drone;
		}

		private DroneEntity drone() {
			return drone;
		}

		private void ensureActive(SoundManager soundManager, Entity listener) {
			fadeOutTicks = 0;
			acousticState.updateSource(drone, listener);
			if (restartCheckTicks > 0) {
				restartCheckTicks--;
				return;
			}
			restartCheckTicks = RESTART_CHECK_INTERVAL_TICKS;
			motor = ensureLayer(soundManager, motor, DroneLoopSoundInstance.Layer.MOTOR);
			propeller = ensureLayer(soundManager, propeller, DroneLoopSoundInstance.Layer.PROPELLER);
		}

		private DroneLoopSoundInstance ensureLayer(
				SoundManager soundManager,
				DroneLoopSoundInstance current,
				DroneLoopSoundInstance.Layer layer
		) {
			if (current != null && soundManager.isActive(current)) {
				return current;
			}
			stopLayer(soundManager, current);
			if (drone.isSilent()) {
				return null;
			}

			DroneLoopSoundInstance replacement = new DroneLoopSoundInstance(drone, layer, acousticState);
			SoundEngine.PlayResult result = soundManager.play(replacement);
			if (result == SoundEngine.PlayResult.NOT_STARTED) {
				replacement.end();
				return null;
			}
			return replacement;
		}

		private boolean continueFadeOut() {
			fadeOutTicks++;
			return fadeOutTicks <= FADE_OUT_TICKS;
		}

		private void applyTransmission(AcousticBands gain) {
			propagationStaleTicks = 0;
			acousticState.applyTransmission(gain);
		}

		private void advancePropagationAge() {
			propagationStaleTicks++;
			if (propagationStaleTicks > 20) {
				acousticState.relaxTransmission();
			}
		}

		private void relaxTransmission() {
			propagationStaleTicks = 0;
			acousticState.relaxTransmission();
		}

		private void addActiveLayers(
				List<DroneLoopSoundInstance> output
		) {
			if (motor != null) {
				output.add(motor);
			}
			if (propeller != null) {
				output.add(propeller);
			}
		}

		private void stop(SoundManager soundManager) {
			stopLayer(soundManager, motor);
			stopLayer(soundManager, propeller);
			motor = null;
			propeller = null;
		}

		private static void stopLayer(SoundManager soundManager, DroneLoopSoundInstance sound) {
			if (sound == null) {
				return;
			}
			sound.end();
			soundManager.stop(sound);
		}
	}

	private record DroneCandidate(DroneEntity drone, double distanceSquared) {
	}

	private static final class ListenerReverbController
			implements AutoCloseable {
		private static final int UPDATE_INTERVAL_TICKS = 20;
		private static final int HORIZONTAL_RADIUS = 6;
		private static final int VERTICAL_RADIUS = 4;
		private static final VoxelReflectionProbe.Config RUNTIME_CONFIG =
				new VoxelReflectionProbe.Config(
						128,
						8,
						24.0,
						64,
						1.0e-6
				);

		private final ListenerReverbState state = new ListenerReverbState();
		private final LatestWinsProcessor<
				ReflectionRequest,
				ReflectionResult
				> processor = new LatestWinsProcessor<>(
						"fpvdrone-listener-reverb",
						this::solve
				);
		private ListenerReverbSoundInstance sound;
		private long lastAppliedSequence;
		private int ticksUntilUpdate;

		private void tick(
				Minecraft client,
				Entity listener,
				Map<Integer, DroneSoundSet> activeSounds,
				boolean internalPropagation,
				AcousticBackendSelector.RuntimeBackend backend
			) {
			SoundManager soundManager = client.getSoundManager();
			boolean javaEnabled = backend
					== AcousticBackendSelector.RuntimeBackend.JAVA_FDN;
			boolean environmentNeeded =
					javaEnabled
							|| backend == AcousticBackendSelector
									.RuntimeBackend.OPENAL_EFX
							|| backend == AcousticBackendSelector
									.RuntimeBackend.OPENAL_EFX_PENDING;
			if (!environmentNeeded || !internalPropagation) {
				stop(soundManager);
				return;
			}
			publishSources(listener, activeSounds);
			applyLatest();
			if (javaEnabled) {
				state.requestWetHistoryOwner();
				ListenerReverbQueueHandoffProbe.onFallbackRequested(
						state.historyDiagnostics()
				);
				if (!activeSounds.isEmpty()) {
					ensureSound(soundManager);
				} else {
					stopSound(soundManager);
				}
			} else {
				stopSound(soundManager);
				state.enterShadowHistoryMode();
				state.advanceShadowHistory();
				ListenerReverbQueueHandoffProbe.onShadowAdvanced(
						state.historyDiagnostics()
				);
			}
			if (activeSounds.isEmpty()) {
				return;
			}
			if (ticksUntilUpdate > 0) {
				ticksUntilUpdate--;
				return;
			}
			ticksUntilUpdate = UPDATE_INTERVAL_TICKS - 1;
			Vec3 eye = listener.getEyePosition();
			AcousticVector listenerPoint = new AcousticVector(
					eye.x,
					eye.y,
					eye.z
			);
			long generation = Math.max(0L, client.level.getGameTime());
			ReflectionVolume volume = MinecraftReflectionSnapshot.capture(
					client.level,
					listenerPoint,
					HORIZONTAL_RADIUS,
					VERTICAL_RADIUS,
					generation
			);
			processor.submit(new ReflectionRequest(
					listenerPoint,
					volume
			));
		}

		private void publishSources(
				Entity listener,
				Map<Integer, DroneSoundSet> activeSounds
			) {
			List<ListenerReverbState.Source> sources =
					new ArrayList<>(activeSounds.size());
			for (DroneSoundSet sounds : activeSounds.values()) {
				if (!DroneSoundPhysics.isAudible(
						sounds.drone.getAverageMotorRpm()
				)) {
					continue;
				}
				AcousticEmissionFrame emission =
						sounds.acousticState.unoccludedEmissionFrame();
				double distance = Math.sqrt(
						sounds.drone.distanceToSqr(listener)
				);
				double distanceGain = Math.max(
						0.0,
						1.0 - distance / 48.0
				);
				if (distanceGain == 0.0) {
					continue;
				}
				sources.add(new ListenerReverbState.Source(
						sounds.drone.getId(),
						emission,
						Math.min(
								4.0,
								distanceGain * sounds.acousticState
										.motorPlaybackAmplitude()
						),
						Math.min(
								4.0,
								distanceGain * sounds.acousticState
										.propellerPlaybackAmplitude()
						)
				));
			}
			state.publishSources(sources);
		}

		private void ensureSound(SoundManager soundManager) {
			if (sound != null && soundManager.isActive(sound)) {
				return;
			}
			if (sound != null) {
				sound.end();
				soundManager.stop(sound);
			}
			ListenerReverbSoundInstance replacement =
					new ListenerReverbSoundInstance(state);
			SoundEngine.PlayResult result = soundManager.play(replacement);
			if (result == SoundEngine.PlayResult.NOT_STARTED) {
				replacement.end();
				sound = null;
				return;
			}
			sound = replacement;
		}

		private ReflectionResult solve(ReflectionRequest request) {
			if (!request.volume().complete()) {
				return ReflectionResult.incomplete();
			}
			try {
				ReflectionStatistics statistics =
						VoxelReflectionProbe.analyze(
								request.listener(),
								request.volume(),
								RUNTIME_CONFIG
						);
				LateReverbEstimator.Parameters parameters =
						LateReverbEstimator.estimate(statistics, 343.0);
				return new ReflectionResult(
						true,
						FdnEnvironmentMapper.map(parameters)
				);
			} catch (IllegalArgumentException invalidSnapshot) {
				return ReflectionResult.incomplete();
			}
		}

		private void applyLatest() {
			LatestWinsProcessor.Result<ReflectionResult> latest =
					processor.latest();
			if (latest == null || latest.sequence() <= lastAppliedSequence) {
				return;
			}
			lastAppliedSequence = latest.sequence();
			if (!latest.succeeded() || !latest.value().complete()) {
				return;
			}
			state.publishEnvironment(latest.value().controls());
		}

		private void stop(SoundManager soundManager) {
			state.reset();
			ticksUntilUpdate = 0;
			stopSound(soundManager);
		}

		private void stopSound(SoundManager soundManager) {
			if (sound == null) {
				return;
			}
			sound.end();
			soundManager.stop(sound);
			sound = null;
		}

		private FdnEnvironmentMapper.Controls environment() {
			return state.snapshot().environment();
		}

		private ListenerReverbHistoryProducer.Diagnostics
		historyDiagnostics() {
			return state.historyDiagnostics();
		}

		@Override
		public void close() {
			processor.close();
		}
	}

	private record ReflectionRequest(
			AcousticVector listener,
			ReflectionVolume volume
	) {
	}

	private record ReflectionResult(
			boolean complete,
			FdnEnvironmentMapper.Controls controls
	) {
		private static ReflectionResult incomplete() {
			return new ReflectionResult(false, null);
		}
	}

	private static final class DirectPropagationController implements AutoCloseable {
		private static final int UPDATE_INTERVAL_TICKS = 1;
		private static final int MAX_DDA_CELLS = 192;
		private static final int MAX_ASTAR_VISITED_NODES = 4096;
		private static final int PORTAL_PARTITION_SIZE = 8;

		private final LatestWinsProcessor<PropagationBatch, PropagationBatchResult> processor;
		private final Map<UUID, AirPortalGraphCache> portalCaches = new HashMap<>();
		private final AtomicReference<DiagnosticExportRequest>
				diagnosticExportRequest =
				new AtomicReference<>();
		private final AtomicReference<DiagnosticExportState>
				diagnosticExportState = new AtomicReference<>(
						new DiagnosticExportState(
								0L,
								"No acoustic DDA export has been requested."
						)
				);
		private final AtomicLong diagnosticExportRevision = new AtomicLong();
		private long lastAppliedSequence;
		private int ticksUntilUpdate;
		private boolean propertyExportAttempted;

		private DirectPropagationController() {
			processor = new LatestWinsProcessor<>(
					"fpvdrone-acoustic-propagation",
					this::solve
			);
		}

		private void tick(
				ClientLevel level,
				Entity listener,
				Map<Integer, DroneSoundSet> activeSounds,
				boolean applyPropagationResults
		) {
			if (applyPropagationResults) {
				for (DroneSoundSet sounds : activeSounds.values()) {
					sounds.advancePropagationAge();
				}
				applyLatest(activeSounds);
			}
			if (ticksUntilUpdate > 0) {
				ticksUntilUpdate--;
				return;
			}
			ticksUntilUpdate = UPDATE_INTERVAL_TICKS - 1;

			Vec3 listenerPosition = listener.getEyePosition();
			AcousticVector listenerPoint = new AcousticVector(
					listenerPosition.x,
					listenerPosition.y,
					listenerPosition.z
			);
			List<PropagationRequest> requests = new ArrayList<>(activeSounds.size());
			for (DroneSoundSet sounds : activeSounds.values()) {
				AcousticSourceFrame sourceFrame = sounds.acousticState.sourceFrame();
				if (sourceFrame == null || sounds.drone.isRemoved() || !sounds.drone.isAlive()) {
					continue;
				}
				List<MultiPathSolver.Probe> probes = RotorDiskProbes.generate(sourceFrame);
				boolean captureAirCorridor =
						sounds.acousticState.transmissionEnergyGain().mid() < 0.92;
				MinecraftPropagationSnapshot snapshot = MinecraftDirectPathSnapshot.capture(
						level,
						probes,
						listenerPoint,
						MAX_DDA_CELLS,
						captureAirCorridor
				);
				requests.add(new PropagationRequest(
						sounds.drone.getId(),
						sounds.drone.getUUID(),
						probes,
						listenerPoint,
						snapshot,
						Math.max(0L, level.getGameTime())
				));
			}
			if (!requests.isEmpty()) {
				processor.submit(new PropagationBatch(requests));
			}
		}

		private boolean hasPendingDiagnosticExport() {
			if (diagnosticExportRequest.get() != null) {
				return true;
			}
			if (propertyExportAttempted) {
				return false;
			}
			String configuredPath = System.getProperty(
					DDA_EXPORT_PATH_PROPERTY
			);
			return configuredPath != null && !configuredPath.isBlank();
		}

		private void applyLatest(Map<Integer, DroneSoundSet> activeSounds) {
			LatestWinsProcessor.Result<PropagationBatchResult> latest = processor.latest();
			if (latest == null || latest.sequence() <= lastAppliedSequence) {
				return;
			}
			lastAppliedSequence = latest.sequence();
			if (!latest.succeeded()) {
				return;
			}
			for (PropagationResult path : latest.value().results()) {
				DroneSoundSet sounds = activeSounds.get(path.entityId());
				if (sounds == null
						|| !sounds.drone.getUUID().equals(path.entityUuid())
						|| !path.complete()) {
					continue;
				}
				sounds.applyTransmission(path.result().finalEnergyGain());
			}
		}

		private PropagationBatchResult solve(PropagationBatch batch) {
			List<PropagationResult> results = new ArrayList<>(batch.requests().size());
			for (PropagationRequest request : batch.requests()) {
				maybeExportDiagnosticBundle(request);
				Optional<AirPortalGraph> portalGraph = request.snapshot().airGrid()
						.filter(grid -> grid.complete())
						.map(grid -> portalCaches.computeIfAbsent(
								request.entityUuid(),
								ignored -> new AirPortalGraphCache(PORTAL_PARTITION_SIZE)
						).update(grid).graph());
				HybridPropagationSolver.Result result = HybridPropagationSolver.solve(
						request.probes(),
						request.listener(),
						request.snapshot().materials(),
						MAX_DDA_CELLS,
						request.snapshot().airGrid(),
						portalGraph,
						request.snapshot().sourceCell(),
						request.snapshot().listenerCell(),
						MAX_ASTAR_VISITED_NODES,
						EmpiricalDiffraction.Parameters.researchDefaults()
				);
				results.add(new PropagationResult(
						request.entityId(),
						request.entityUuid(),
						result,
						request.snapshot().materials().complete() && result.direct().complete()
				));
			}
			portalCaches.keySet().removeIf(uuid -> batch.requests().stream()
					.noneMatch(request -> request.entityUuid().equals(uuid)));
			return new PropagationBatchResult(results);
		}

		private void maybeExportDiagnosticBundle(PropagationRequest request) {
			DiagnosticExportRequest commandRequest =
					diagnosticExportRequest.get();
			Path output = commandRequest == null
					? null
					: commandRequest.output();
			boolean propertyRequest = false;
			if (output == null && !propertyExportAttempted) {
				String configuredPath = System.getProperty(
						DDA_EXPORT_PATH_PROPERTY
				);
				if (configuredPath != null && !configuredPath.isBlank()) {
					output = Path.of(configuredPath)
							.toAbsolutePath()
							.normalize();
					propertyRequest = true;
				}
			}
			if (output == null) {
				return;
			}
			if (!request.snapshot().materials().complete()) {
				return;
			}
			if (propertyRequest) {
				propertyExportAttempted = true;
			} else if (!diagnosticExportRequest.compareAndSet(
					commandRequest,
					null
			)) {
				return;
			}
			try {
				DdaProductionSnapshotBundle.writeAtomic(
						output,
						MinecraftDirectPathSnapshot.diagnosticBundle(
								request.snapshot(),
								request.probes(),
								request.listener(),
								MAX_DDA_CELLS,
								modVersion("minecraft"),
								modVersion(FpvDronecraftMod.MOD_ID),
								request.snapshotGeneration()
						)
				);
				FpvDronecraftMod.LOGGER.info(
						"Exported acoustic DDA production bundle to {}",
						output
				);
				updateDiagnosticExportState(
						commandRequest,
						"Exported acoustic DDA production bundle to " + output
				);
			} catch (IOException | RuntimeException error) {
				FpvDronecraftMod.LOGGER.error(
						"Failed to export acoustic DDA production bundle to {}",
						output,
						error
				);
				updateDiagnosticExportState(
						commandRequest,
						"Failed to export acoustic DDA production bundle to "
								+ output + ": " + error.getMessage()
				);
			}
		}

		private Path requestDiagnosticExport(Path output) {
			Path normalized = output.toAbsolutePath().normalize();
			long revision = diagnosticExportRevision.incrementAndGet();
			diagnosticExportRequest.set(new DiagnosticExportRequest(
					normalized,
					revision
			));
			diagnosticExportState.set(
					new DiagnosticExportState(
							revision,
							"Waiting for a complete acoustic snapshot; output: "
									+ normalized
					)
			);
			return normalized;
		}

		private String diagnosticExportStatus() {
			return diagnosticExportState.get().message();
		}

		private void updateDiagnosticExportState(
				DiagnosticExportRequest request,
				String message
		) {
			if (request == null) {
				diagnosticExportState.updateAndGet(current ->
						current.revision() == 0L
								? new DiagnosticExportState(0L, message)
								: current
				);
				return;
			}
			diagnosticExportState.updateAndGet(current ->
					current.revision() == request.revision()
							? new DiagnosticExportState(
									request.revision(),
									message
							)
							: current
			);
		}

		private static String modVersion(String modId) {
			return FabricLoader.getInstance()
					.getModContainer(modId)
					.map(container -> container.getMetadata()
							.getVersion()
							.getFriendlyString())
					.orElse("unknown");
		}

		@Override
		public void close() {
			processor.close();
			portalCaches.clear();
			diagnosticExportRequest.set(null);
		}

		private record DiagnosticExportRequest(Path output, long revision) {
		}

		private record DiagnosticExportState(long revision, String message) {
		}
	}

	private record PropagationRequest(
			int entityId,
			UUID entityUuid,
			List<MultiPathSolver.Probe> probes,
			AcousticVector listener,
			MinecraftPropagationSnapshot snapshot,
			long snapshotGeneration
	) {
		private PropagationRequest {
			probes = List.copyOf(probes);
		}
	}

	private record PropagationResult(
			int entityId,
			UUID entityUuid,
			HybridPropagationSolver.Result result,
			boolean complete
	) {
	}

	private record PropagationBatch(List<PropagationRequest> requests) {
		private PropagationBatch {
			requests = List.copyOf(requests);
		}
	}

	private record PropagationBatchResult(List<PropagationResult> results) {
		private PropagationBatchResult {
			results = List.copyOf(results);
		}
	}
}
