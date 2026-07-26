package com.tenicana.dronecraft.client.sound;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import com.tenicana.dronecraft.acoustics.reverb.LateReverbEstimator;
import com.tenicana.dronecraft.acoustics.reverb.ReflectionStatistics;
import com.tenicana.dronecraft.acoustics.reverb.ReflectionVolume;
import com.tenicana.dronecraft.acoustics.reverb.VoxelReflectionProbe;
import com.tenicana.dronecraft.acoustics.voxel.DdaProductionSnapshotBundle;
import com.tenicana.dronecraft.entity.DroneEntity;

/**
 * Creates a real integrated-client world and exports one complete production
 * DDA bundle from the normal DroneSoundManager/acoustic-worker path.
 */
public final class MinecraftDdaCaptureClientGameTest
		implements FabricClientGameTest {
	private static final String OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestDdaOutput";
	private static final String REVERB_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestReverbOutput";
	private static final String REVERB_PERFORMANCE_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestReverbPerformanceOutput";
	private static final String PORTAL_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestPortalOutput";
	private static final String OPENAL_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestOpenAlOutput";
	private static final String OPENAL_ROUTING_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestOpenAlRoutingOutput";
	private static final String OPENAL_CONTROLLER_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestOpenAlControllerOutput";
	private static final String OPENAL_RELOAD_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestOpenAlReloadOutput";
	private static final String OPENAL_FAILOVER_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestOpenAlFailoverOutput";
	private static final String LISTENER_HANDOFF_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestListenerHandoffOutput";
	private static final String LISTENER_HANDOFF_D107_REPORT_PROPERTY =
			"fpvdrone.acoustics.listenerHandoffD107Report";
	private static final String BACKEND_CAPABILITY_POLICY_OUTPUT_PROPERTY =
			"fpvdrone.acoustics."
					+ "clientGameTestBackendCapabilityPolicyOutput";
	private static final String OPENAL_CLOCK_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestOpenAlClockOutput";
	private static final String OPENAL_DOPPLER_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestOpenAlDopplerOutput";
	private static final String DOPPLER_PCM_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestDopplerPcmOutput";
	private static final String DOPPLER_CHUNK_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestDopplerChunkOutput";
	private static final String DOPPLER_CHUNK_PCM_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestDopplerChunkPcmOutput";
	private static final String DOPPLER_CHUNK_BLACKBOX_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestDopplerChunkBlackboxOutput";
	private static final String OPENAL_STREAMING_QUEUE_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestOpenAlStreamingQueueOutput";
	private static final String OPENAL_EVENT_QUEUE_HEALTH_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestOpenAlEventQueueHealthOutput";
	private static final String OPENAL_LOOPBACK_RENDER_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestOpenAlLoopbackRenderOutput";
	private static final String OPENAL_LOOPBACK_RENDER_PCM_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestOpenAlLoopbackRenderPcmOutput";
	private static final String BACKEND_TRANSFER_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestBackendTransferOutput";
	private static final String BACKEND_TRANSFER_DRY_PCM_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestBackendTransferDryPcmOutput";
	private static final String BACKEND_TRANSFER_EFX_PCM_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestBackendTransferEfxPcmOutput";
	private static final String BACKEND_TRANSFER_JAVA_PCM_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestBackendTransferJavaPcmOutput";
	private static final String BACKEND_MATRIX_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestBackendMatrixOutput";
	private static final String BACKEND_MATRIX_PCM_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestBackendMatrixPcmOutput";
	private static final String BACKEND_TRANSITION_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestBackendTransitionOutput";
	private static final String BACKEND_TRANSITION_DRY_PCM_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestBackendTransitionDryPcmOutput";
	private static final String BACKEND_TRANSITION_EFX_PCM_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestBackendTransitionEfxPcmOutput";
	private static final String BACKEND_TRANSITION_JAVA_PCM_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestBackendTransitionJavaPcmOutput";
	private static final String AUDIO_LAB_DRY_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestAudioLabDryOutput";
	private static final String AUDIO_LAB_JAVA_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestAudioLabJavaOutput";
	private static final String AUDIO_LAB_EFX_OUTPUT_PROPERTY =
			"fpvdrone.acoustics.clientGameTestAudioLabEfxOutput";
	private static final int PERFORMANCE_WARMUP_ITERATIONS = 20;
	private static final int PERFORMANCE_MEASURED_ITERATIONS = 200;
	private static final int MAXIMUM_WAIT_TICKS = 20 * 30;
	private static final VoxelReflectionProbe.Config REVERB_CONFIG =
			new VoxelReflectionProbe.Config(
					128,
					8,
					24.0,
					64,
					1.0e-6
			);

	@Override
	public void runTest(ClientGameTestContext context) {
		Path output = requiredOutputPath();
		AtomicReference<OpenAlEfxController.Diagnostics>
				activeEfxDiagnostics = new AtomicReference<>();
		AtomicReference<OpenAlEfxController.Diagnostics>
				reloadedEfxDiagnostics = new AtomicReference<>();
		AtomicReference<OpenAlNativeDopplerGuard.Diagnostics>
				activeDopplerDiagnostics = new AtomicReference<>();
		AtomicReference<OpenAlNativeDopplerGuard.Diagnostics>
				reloadedDopplerDiagnostics = new AtomicReference<>();
		try {
			Files.deleteIfExists(output);
		} catch (java.io.IOException error) {
			throw new IllegalStateException(
					"could not clear preceding DDA client capture",
					error
			);
		}

		try (TestSingleplayerContext singleplayer =
					 context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksDownload();
			singleplayer.getServer().runCommand(
					"execute as @p at @s run fpvdrone spawn racing_quad"
			);
			singleplayer.getServer().runCommand(
					"execute as @p at @s run tp @s ~ ~ ~-28"
			);
			singleplayer.getServer().runCommand(
					"execute as @p at @s run fill "
							+ "~-2 ~ ~4 ~2 ~3 ~4 minecraft:stone"
			);
			singleplayer.getServer().runCommand(
					"execute as @p at @s run fill "
							+ "~-2 ~ ~8 ~2 ~3 ~8 minecraft:glass"
			);
			singleplayer.getServer().runCommand(
					"execute as @p at @s run fill "
							+ "~-2 ~ ~12 ~2 ~3 ~12 minecraft:oak_planks"
			);
			singleplayer.getServer().runCommand(
					"execute as @p at @s run fill "
							+ "~-2 ~ ~16 ~2 ~3 ~16 minecraft:water"
			);
			singleplayer.getServer().runCommand(
					"execute as @p at @s run fill "
							+ "~-2 ~ ~20 ~2 ~3 ~20 "
							+ "minecraft:oak_leaves[persistent=true]"
			);
			singleplayer.getClientWorld().waitForChunksDownload();
			singleplayer.getServer().runCommand(
					"execute as @p at @s run fpvdrone diagnostic start 12"
			);
			context.waitFor(
					MinecraftDdaCaptureClientGameTest::hasAudibleDrone,
					MAXIMUM_WAIT_TICKS
			);
			captureOpenAlCapabilities(context);
			context.waitFor(
					client -> OpenAlEfxSourceRoutingProbe
							.hasActiveDroneChannel(
									client.getSoundManager()
							),
					MAXIMUM_WAIT_TICKS
			);
			captureOpenAlSourceRouting(context);
			capturePersistentEfxActive(
					context,
					activeEfxDiagnostics
			);
			captureNativeDopplerGuard(
					context,
					null,
					activeDopplerDiagnostics
			);
			captureDopplerPcmConformance(context);
			DopplerAudioChunkTrace.Snapshot productionTrace =
					captureDopplerAudioChunkTrace(
							context,
							singleplayer
					);
			OpenAlClockLatencyProbe.PairResult clockBeforeReload =
					captureOpenAlClockPair(context);
			capturePersistentEfxReload(
					context,
					activeEfxDiagnostics.get(),
					reloadedEfxDiagnostics
			);
			OpenAlClockLatencyProbe.PairResult clockAfterReload =
					captureOpenAlClockPair(context);
			captureNativeDopplerGuard(
					context,
					activeDopplerDiagnostics.get(),
					reloadedDopplerDiagnostics
			);
			writeNativeDopplerGuardEvidence(
					activeDopplerDiagnostics.get(),
					reloadedDopplerDiagnostics.get()
			);
			writeOpenAlClockEvidence(
					clockBeforeReload,
					clockAfterReload
			);
			context.runOnClient(client ->
					DroneSoundManager.requestDdaDiagnosticExport(output)
			);
			context.waitFor(
					client -> Files.isRegularFile(output),
					MAXIMUM_WAIT_TICKS
			);
			verifyCapture(output);
			verifyMinecraftReverbSnapshots(context, singleplayer);
			PortalEnvironments portalEnvironments =
					verifyMinecraftPortalSnapshots(
							context,
							singleplayer
					);
			captureOpenAlBackendTransfer(
					context,
					productionTrace,
					portalEnvironments.closed()
			);
			captureOpenAlBackendEnvironmentMatrix(
					context,
					productionTrace,
					portalEnvironments
			);
			captureOpenAlBackendTransition(
					context,
					productionTrace,
					portalEnvironments
			);
			benchmarkMinecraftReverb(context);
			captureAudioLabProtocol(context, singleplayer);
			captureOpenAlFaultFailover(context, singleplayer);
			captureBackendCapabilityPolicyAndTimeline(context);
			finishPersistentEfxEvidence(
					context,
					singleplayer,
					activeEfxDiagnostics.get(),
					reloadedEfxDiagnostics.get()
			);
		}
	}

	private static Path requiredOutputPath() {
		String configured = System.getProperty(OUTPUT_PROPERTY);
		if (configured == null || configured.isBlank()) {
			throw new IllegalStateException(
					"missing required system property " + OUTPUT_PROPERTY
			);
		}
		return Path.of(configured).toAbsolutePath().normalize();
	}

	private static Path requiredReverbOutputPath() {
		String configured = System.getProperty(REVERB_OUTPUT_PROPERTY);
		if (configured == null || configured.isBlank()) {
			throw new IllegalStateException(
					"missing required system property "
							+ REVERB_OUTPUT_PROPERTY
			);
		}
		return Path.of(configured).toAbsolutePath().normalize();
	}

	private static Path requiredReverbPerformanceOutputPath() {
		String configured = System.getProperty(
				REVERB_PERFORMANCE_OUTPUT_PROPERTY
		);
		if (configured == null || configured.isBlank()) {
			throw new IllegalStateException(
					"missing required system property "
							+ REVERB_PERFORMANCE_OUTPUT_PROPERTY
			);
		}
		return Path.of(configured).toAbsolutePath().normalize();
	}

	private static Path requiredPortalOutputPath() {
		String configured = System.getProperty(PORTAL_OUTPUT_PROPERTY);
		if (configured == null || configured.isBlank()) {
			throw new IllegalStateException(
					"missing required system property " + PORTAL_OUTPUT_PROPERTY
			);
		}
		return Path.of(configured).toAbsolutePath().normalize();
	}

	private static Path requiredOpenAlOutputPath() {
		String configured = System.getProperty(OPENAL_OUTPUT_PROPERTY);
		if (configured == null || configured.isBlank()) {
			throw new IllegalStateException(
					"missing required system property "
							+ OPENAL_OUTPUT_PROPERTY
			);
		}
		return Path.of(configured).toAbsolutePath().normalize();
	}

	private static Path requiredOpenAlRoutingOutputPath() {
		String configured = System.getProperty(
				OPENAL_ROUTING_OUTPUT_PROPERTY
		);
		if (configured == null || configured.isBlank()) {
			throw new IllegalStateException(
					"missing required system property "
							+ OPENAL_ROUTING_OUTPUT_PROPERTY
			);
		}
		return Path.of(configured).toAbsolutePath().normalize();
	}

	private static Path requiredOpenAlControllerOutputPath() {
		String configured = System.getProperty(
				OPENAL_CONTROLLER_OUTPUT_PROPERTY
		);
		if (configured == null || configured.isBlank()) {
			throw new IllegalStateException(
					"missing required system property "
							+ OPENAL_CONTROLLER_OUTPUT_PROPERTY
			);
		}
		return Path.of(configured).toAbsolutePath().normalize();
	}

	private static Path requiredOpenAlReloadOutputPath() {
		String configured = System.getProperty(
				OPENAL_RELOAD_OUTPUT_PROPERTY
		);
		if (configured == null || configured.isBlank()) {
			throw new IllegalStateException(
					"missing required system property "
							+ OPENAL_RELOAD_OUTPUT_PROPERTY
			);
		}
		return Path.of(configured).toAbsolutePath().normalize();
	}

	private static Path requiredOpenAlFailoverOutputPath() {
		String configured = System.getProperty(
				OPENAL_FAILOVER_OUTPUT_PROPERTY
		);
		if (configured == null || configured.isBlank()) {
			throw new IllegalStateException(
					"missing required system property "
							+ OPENAL_FAILOVER_OUTPUT_PROPERTY
			);
		}
		return Path.of(configured).toAbsolutePath().normalize();
	}

	private static Path requiredBackendCapabilityPolicyOutputPath() {
		String configured = System.getProperty(
				BACKEND_CAPABILITY_POLICY_OUTPUT_PROPERTY
		);
		if (configured == null || configured.isBlank()) {
			throw new IllegalStateException(
					"missing required system property "
							+ BACKEND_CAPABILITY_POLICY_OUTPUT_PROPERTY
			);
		}
		return Path.of(configured).toAbsolutePath().normalize();
	}

	private static Path requiredPropertyPath(
			String property
	) {
		String configured = System.getProperty(property);
		if (configured == null || configured.isBlank()) {
			throw new IllegalStateException(
					"missing required system property " + property
			);
		}
		return Path.of(configured).toAbsolutePath().normalize();
	}

	private static void captureOpenAlCapabilities(
			ClientGameTestContext context
	) {
		Path output = requiredOpenAlOutputPath();
		try {
			Files.deleteIfExists(output);
		} catch (java.io.IOException error) {
			throw new IllegalStateException(
					"could not clear preceding OpenAL capability capture",
					error
			);
		}
		AtomicReference<CompletableFuture<
				OpenAlNativeCapabilityProbe.Result>> pending =
				new AtomicReference<>();
		context.runOnClient(client -> pending.set(
				OpenAlNativeCapabilityProbe.capture(
						client.getSoundManager()
				)
		));
		context.waitFor(
				client -> pending.get() != null
						&& pending.get().isDone(),
				MAXIMUM_WAIT_TICKS
		);
		OpenAlNativeCapabilityProbe.Result result = pending.get().join();
		if (!result.activeContext()) {
			throw new AssertionError(
					"OpenAL probe did not execute with an active context"
			);
		}
		if (result.efxSupported()
				&& (!result.efxResourcesCreated()
						|| !result.efxResourcesReleased()
						|| result.maximumAuxiliarySends() < 1
						|| result.alErrorCode() != 0)) {
			throw new AssertionError(
					"advertised OpenAL EFX did not pass resource lifecycle"
			);
		}
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(
					output,
					result.toJson(),
					StandardCharsets.UTF_8
			);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write OpenAL capability evidence",
					error
			);
		}
	}

	private static void captureOpenAlSourceRouting(
			ClientGameTestContext context
	) {
		Path output = requiredOpenAlRoutingOutputPath();
		try {
			Files.deleteIfExists(output);
		} catch (java.io.IOException error) {
			throw new IllegalStateException(
					"could not clear preceding OpenAL routing capture",
					error
			);
		}
		AtomicReference<CompletableFuture<
				OpenAlEfxSourceRoutingProbe.Result>> pending =
				new AtomicReference<>();
		context.runOnClient(client -> pending.set(
				OpenAlEfxSourceRoutingProbe.capture(
						client.getSoundManager()
				)
		));
		context.waitFor(
				client -> pending.get() != null
						&& pending.get().isDone(),
				MAXIMUM_WAIT_TICKS
		);
		OpenAlEfxSourceRoutingProbe.Result result =
				pending.get().join();
		if (!result.sourceFound()
				|| !result.resourcesCreated()
				|| !result.filterAndSendAttached()
				|| !result.vanillaRoutingRestored()
				|| !result.resourcesReleased()
				|| result.alErrorCode() != 0) {
			throw new AssertionError(
					"OpenAL EFX source routing exercise failed"
			);
		}
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(
					output,
					result.toJson(),
					StandardCharsets.UTF_8
			);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write OpenAL routing evidence",
					error
			);
		}
	}

	private static void capturePersistentEfxActive(
			ClientGameTestContext context,
			AtomicReference<OpenAlEfxController.Diagnostics> output
	) {
		context.waitFor(client -> {
			OpenAlEfxController.Diagnostics diagnostics =
					DroneSoundManager.openAlEfxDiagnostics();
			AcousticBackendRuntimeTelemetry.Snapshot runtime =
					DroneSoundManager.acousticBackendRuntimeDiagnostics();
			if (diagnostics.operational()
					&& diagnostics.sharedResourcesCreated()
					&& diagnostics.attachedSources() >= 2
					&& diagnostics.allocatedSourceFilters()
							== diagnostics.attachedSources()
					&& diagnostics.alErrorCode() == 0
					&& runtime.backend() == AcousticBackendSelector
							.RuntimeBackend.OPENAL_EFX
					&& runtime.efxStatus()
							== OpenAlEfxController.Status.OPERATIONAL
					&& runtime.environmentGeneration() >= 0L
					&& !runtime.javaStreamActive()
					&& !runtime.doubleWetPath()
					&& !runtime.capturesAudio()) {
				output.set(diagnostics);
				return true;
			}
			return false;
		}, MAXIMUM_WAIT_TICKS);
		if (DroneSoundManager
				.javaListenerReverbActiveForDiagnostics()) {
			throw new AssertionError(
					"Java wet bus must be suppressed while EFX is enabled"
			);
		}
	}

	private static void captureNativeDopplerGuard(
			ClientGameTestContext context,
			OpenAlNativeDopplerGuard.Diagnostics beforeReload,
			AtomicReference<OpenAlNativeDopplerGuard.Diagnostics> output
	) {
		context.waitFor(client -> {
			OpenAlNativeDopplerGuard.Diagnostics diagnostics =
					DroneSoundManager.openAlDopplerGuardDiagnostics();
			boolean contextReady = beforeReload == null
					|| diagnostics.contextRebuilds()
							> beforeReload.contextRebuilds();
			if (diagnostics.nativeDopplerNeutralized()
					&& diagnostics.guardedSources() >= 2
					&& diagnostics.maximumGuardVelocityDifference()
							<= 1.0e-6
					&& diagnostics.maximumNativeRatioDeviationAfter()
							<= 1.0e-6
					&& Math.abs(diagnostics.minimumSourcePitch() - 1.0)
							<= 1.0e-6
					&& Math.abs(diagnostics.maximumSourcePitch() - 1.0)
							<= 1.0e-6
					&& diagnostics.speedOfSoundMetersPerSecond() > 0.0
					&& diagnostics.alErrorCode() == 0
					&& contextReady) {
				output.set(diagnostics);
				return true;
			}
			return false;
		}, MAXIMUM_WAIT_TICKS);
	}

	private static void writeNativeDopplerGuardEvidence(
			OpenAlNativeDopplerGuard.Diagnostics beforeReload,
			OpenAlNativeDopplerGuard.Diagnostics afterReload
	) {
		if (beforeReload == null || afterReload == null) {
			throw new AssertionError(
					"missing native Doppler guard diagnostics"
			);
		}
		String json = String.format(
				Locale.ROOT,
				"{\n"
						+ "  \"schema_version\": 1,\n"
						+ "  \"status\": "
						+ "\"valid-openal-native-doppler-guard\",\n"
						+ "  \"before_reload\": %s,\n"
						+ "  \"after_reload\": %s,\n"
						+ "  \"minecraft_channel_velocity_api_available\": "
						+ "false,\n"
						+ "  \"minecraft_listener_velocity_api_available\": "
						+ "false,\n"
						+ "  \"procedural_pcm_tonal_doppler\": true,\n"
						+ "  \"procedural_pcm_broadband_doppler\": false,\n"
						+ "  \"native_complete_stream_resampling_rejected\": "
						+ "true,\n"
						+ "  \"native_doppler_neutralized\": true,\n"
						+ "  \"other_sources_modified\": false,\n"
						+ "  \"sound_engine_reload_exercised\": true,\n"
						+ "  \"physical_device_switch_exercised\": false,\n"
						+ "  \"real_audio_capture\": false,\n"
						+ "  \"release_calibrated\": false,\n"
						+ "  \"claim_boundary\": \"Current Minecraft 1.21.11 "
						+ "OpenAL source state and sound-engine reload only; "
						+ "the report proves per-source native Doppler "
						+ "neutrality without changing unrelated sources, "
						+ "not audible Doppler correctness, physical-device "
						+ "switching, or release calibration.\"\n"
						+ "}\n",
				dopplerDiagnosticsJson(beforeReload),
				dopplerDiagnosticsJson(afterReload)
		);
		Path output = requiredPropertyPath(
				OPENAL_DOPPLER_OUTPUT_PROPERTY
		);
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(output, json, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write native Doppler guard evidence",
					error
			);
		}
	}

	private static void captureDopplerPcmConformance(
			ClientGameTestContext context
	) {
		context.waitFor(
				client -> DroneSoundManager
						.hasMovingDopplerPcmFlightState(),
				MAXIMUM_WAIT_TICKS
		);
		AtomicReference<List<DopplerPcmRuntimeProbe.Result>> pending =
				new AtomicReference<>();
		context.runOnClient(client -> pending.set(
				DroneSoundManager.captureDopplerPcmConformance()
		));
		context.waitFor(
				client -> pending.get() != null,
				MAXIMUM_WAIT_TICKS
		);
		List<DopplerPcmRuntimeProbe.Result> captures = pending.get();
		if (captures.size() != 1) {
			throw new AssertionError(
					"expected exactly one Doppler PCM runtime capture"
			);
		}
		DopplerPcmRuntimeProbe.Result capture = captures.getFirst();
		if (Math.abs(capture.renderStateDopplerRatio() - 1.0)
					<= 1.0e-6
				|| Math.abs(capture.motor().frequencyErrorPpm()) > 25.0
				|| Math.abs(capture.propeller().frequencyErrorPpm()) > 25.0
				|| capture.motor().clippedSamples() != 0
				|| capture.propeller().clippedSamples() != 0
				|| capture.motor().positiveCrossings() < 3
				|| capture.propeller().positiveCrossings() < 3) {
			throw new AssertionError(
					"Doppler PCM production-synthesizer conformance failed"
			);
		}
		String json = String.format(
				Locale.ROOT,
				"{\n"
						+ "  \"schema_version\": 1,\n"
						+ "  \"status\": "
						+ "\"valid-doppler-pcm-conformance\",\n"
						+ "  \"capture\": %s,\n"
						+ "  \"synchronized_minecraft_entity_kinematics\": "
						+ "true,\n"
						+ "  \"server_blackbox_csv_bound\": false,\n"
						+ "  \"production_doppler_shift\": true,\n"
						+ "  \"production_phase_continuous_synthesizer\": "
						+ "true,\n"
						+ "  \"isolated_tone_measurement\": true,\n"
						+ "  \"mixed_live_stream_measured\": false,\n"
						+ "  \"openal_playback_capture\": false,\n"
						+ "  \"real_audio_capture\": false,\n"
						+ "  \"release_calibrated\": false,\n"
						+ "  \"claim_boundary\": \"A synchronized Minecraft "
						+ "entity source/listener frame is re-rendered through "
						+ "the production DopplerShift and "
						+ "PhaseContinuousSynthesizer classes; frequency is "
						+ "estimated after soft clipping and PCM16 "
						+ "quantization. This is an isolated-tone in-process "
						+ "measurement, not the mixed live stream, server "
						+ "blackbox CSV, OpenAL playback, loopback, or an "
						+ "audible calibration.\"\n"
						+ "}\n",
				dopplerPcmRuntimeJson(capture)
		);
		Path output = requiredPropertyPath(DOPPLER_PCM_OUTPUT_PROPERTY);
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(output, json, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write Doppler PCM conformance evidence",
					error
			);
		}
	}

	private static DopplerAudioChunkTrace.Snapshot
			captureDopplerAudioChunkTrace(
			ClientGameTestContext context,
			TestSingleplayerContext singleplayer
	) {
		AtomicReference<CompletableFuture<
				OpenAlEventQueueHealthProbe.Session>> eventStart =
				new AtomicReference<>();
		context.runOnClient(client -> eventStart.set(
				OpenAlEventQueueHealthProbe.start(
						client.getSoundManager(),
						256
				)
		));
		context.waitFor(
				client -> eventStart.get() != null
						&& eventStart.get().isDone(),
				MAXIMUM_WAIT_TICKS
		);
		OpenAlEventQueueHealthProbe.Session eventSession =
				eventStart.get().join();
		try {
		context.runOnClient(client -> {
			DopplerAudioChunkTrace.begin(4);
			OpenAlStreamingQueueProbe.begin(4);
		});
		context.waitFor(
				client -> DopplerAudioChunkTrace.ready()
						&& OpenAlStreamingQueueProbe.ready(),
				MAXIMUM_WAIT_TICKS
		);
		AtomicReference<DopplerAudioChunkTrace.Snapshot> trace =
				new AtomicReference<>();
		AtomicReference<OpenAlStreamingQueueProbe.Snapshot> queue =
				new AtomicReference<>();
		context.runOnClient(client -> {
			trace.set(DopplerAudioChunkTrace.finish());
			queue.set(OpenAlStreamingQueueProbe.finish());
		});
		context.waitFor(
				client -> trace.get() != null && queue.get() != null,
				MAXIMUM_WAIT_TICKS
		);
		int motorSource = queue.get().events().stream()
				.filter(event -> event.layer()
						== com.tenicana.dronecraft.acoustics
								.PhaseContinuousSynthesizer.Layer.MOTOR)
				.mapToInt(OpenAlStreamingQueueProbe.QueueEvent::source)
				.findFirst()
				.orElseThrow();
		int propellerSource = queue.get().events().stream()
				.filter(event -> event.layer()
						== com.tenicana.dronecraft.acoustics
								.PhaseContinuousSynthesizer.Layer.PROPELLER)
				.mapToInt(OpenAlStreamingQueueProbe.QueueEvent::source)
				.findFirst()
				.orElseThrow();
		Set<Integer> productionSources = Set.of(
				motorSource,
				propellerSource
		);
		context.waitFor(
				client -> eventSession.hasCoverage(
						productionSources,
						3
				),
				MAXIMUM_WAIT_TICKS
		);
		AtomicReference<CompletableFuture<
				OpenAlEventQueueHealthProbe.Cleanup>> eventStop =
				new AtomicReference<>();
		context.runOnClient(client -> eventStop.set(eventSession.stop()));
		context.waitFor(
				client -> eventStop.get() != null
						&& eventStop.get().isDone(),
				MAXIMUM_WAIT_TICKS
		);
		OpenAlEventQueueHealthProbe.Cleanup cleanup =
				eventStop.get().join();
		long quietWindowStart = System.nanoTime();
		context.waitFor(
				client -> System.nanoTime() - quietWindowStart
						>= 200_000_000L,
				MAXIMUM_WAIT_TICKS
		);
		OpenAlEventQueueHealthProbe.Snapshot eventSnapshot =
				eventSession.snapshotAfterQuietWindow(
						cleanup,
						System.nanoTime() - quietWindowStart
				);
		ServerBlackboxEvidence blackbox =
				captureServerBlackbox(singleplayer);
		writeDopplerAudioChunkEvidence(trace.get(), blackbox);
		writeOpenAlStreamingQueueEvidence(trace.get(), queue.get());
		writeOpenAlEventQueueHealthEvidence(
				eventSnapshot,
				motorSource,
				propellerSource
		);
		captureOpenAlLoopbackRender(context, trace.get());
		return trace.get();
		} catch (Throwable error) {
			eventSession.abort();
			throw error;
		}
	}

	private static void captureOpenAlBackendTransfer(
			ClientGameTestContext context,
			DopplerAudioChunkTrace.Snapshot trace,
			FdnEnvironmentMapper.Controls environment
	) {
		AtomicReference<CompletableFuture<
				OpenAlBackendTransferProbe.Result>> future =
				new AtomicReference<>();
		context.runOnClient(client -> future.set(
				OpenAlBackendTransferProbe.capture(
						client.getSoundManager(),
						trace,
						environment
				)
		));
		context.waitFor(
				client -> future.get() != null && future.get().isDone(),
				MAXIMUM_WAIT_TICKS
		);
		writeOpenAlBackendTransferEvidence(future.get().join());
	}

	private static void writeOpenAlBackendTransferEvidence(
			OpenAlBackendTransferProbe.Result result
	) {
		Path traceReport = requiredPropertyPath(
				DOPPLER_CHUNK_OUTPUT_PROPERTY
		);
		Path report = requiredPropertyPath(
				BACKEND_TRANSFER_OUTPUT_PROPERTY
		);
		Path dryOutput = requiredPropertyPath(
				BACKEND_TRANSFER_DRY_PCM_OUTPUT_PROPERTY
		);
		Path efxOutput = requiredPropertyPath(
				BACKEND_TRANSFER_EFX_PCM_OUTPUT_PROPERTY
		);
		Path javaOutput = requiredPropertyPath(
				BACKEND_TRANSFER_JAVA_PCM_OUTPUT_PROPERTY
		);
		FdnEnvironmentMapper.Controls environment = result.environment();
		if (!result.minecraftContextUnchanged()
				|| !result.minecraftDeviceUnchanged()
				|| !result.minecraftSoundThreadUnchanged()
				|| environment.snapshotGeneration() < 0L
				|| environment.wetGain() <= 0.0
				|| environment.rt60Seconds().mid() <= 0.0
				|| result.dryAlError() != org.lwjgl.openal.AL10.AL_NO_ERROR
				|| result.dryAlcError()
						!= org.lwjgl.openal.ALC10.ALC_NO_ERROR
				|| result.efxAlError() != org.lwjgl.openal.AL10.AL_NO_ERROR
				|| result.efxAlcError()
						!= org.lwjgl.openal.ALC10.ALC_NO_ERROR
				|| result.javaFdnAlError()
						!= org.lwjgl.openal.AL10.AL_NO_ERROR
				|| result.javaFdnAlcError()
						!= org.lwjgl.openal.ALC10.ALC_NO_ERROR) {
			throw new AssertionError(
					"backend transfer render controls failed: context="
							+ result.minecraftContextUnchanged() + "/"
							+ result.minecraftDeviceUnchanged() + "/"
							+ result.minecraftSoundThreadUnchanged()
							+ " environment="
							+ environment.snapshotGeneration() + "/"
							+ environment.rt60Seconds() + "/"
							+ environment.wetGain()
							+ " errors="
							+ result.dryAlError() + "/"
							+ result.dryAlcError() + ","
							+ result.efxAlError() + "/"
							+ result.efxAlcError() + ","
							+ result.javaFdnAlError() + "/"
							+ result.javaFdnAlcError()
			);
		}
		byte[] traceReportBytes;
		try {
			traceReportBytes = Files.readAllBytes(traceReport);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not bind backend transfer to Doppler trace",
					error
			);
		}
		String json = String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": "
						+ "\"valid-controlled-backend-transfer\",%n"
						+ "  \"render_format\": "
						+ "\"s16le-mono-48000\",%n"
						+ "  \"input_frames\": %d,%n"
						+ "  \"tail_frames\": %d,%n"
						+ "  \"render_frames\": %d,%n"
						+ "  \"doppler_trace_report_sha256\": \"%s\",%n"
						+ "  \"motor_sequence\": %d,%n"
						+ "  \"motor_pcm_sha256\": \"%s\",%n"
						+ "  \"propeller_sequence\": %d,%n"
						+ "  \"propeller_pcm_sha256\": \"%s\",%n"
						+ "  \"snapshot_generation\": %d,%n"
						+ "  \"rt60_seconds\": "
						+ "{\"low\":%.9f,\"mid\":%.9f,"
						+ "\"high\":%.9f},%n"
						+ "  \"wet_gain\": %.9f,%n"
						+ "  \"transition_seconds\": %.9f,%n"
						+ "  \"dry_pcm_bytes\": %d,%n"
						+ "  \"dry_pcm_sha256\": \"%s\",%n"
						+ "  \"efx_pcm_bytes\": %d,%n"
						+ "  \"efx_pcm_sha256\": \"%s\",%n"
						+ "  \"java_fdn_pcm_bytes\": %d,%n"
						+ "  \"java_fdn_pcm_sha256\": \"%s\",%n"
						+ "  \"minecraft_context_unchanged\": %s,%n"
						+ "  \"minecraft_device_unchanged\": %s,%n"
						+ "  \"minecraft_sound_thread_unchanged\": %s,%n"
						+ "  \"minecraft_sound_thread\": \"%s\",%n"
						+ "  \"loopback_worker_thread\": \"%s\",%n"
						+ "  \"production_parameter_mapping_reused\": true,%n"
						+ "  \"production_java_fdn_reused\": true,%n"
						+ "  \"physical_playback_device_opened\": false,%n"
						+ "  \"capture_device_opened\": false,%n"
						+ "  \"real_audio_capture\": false,%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"claim_boundary\": \"Controlled isolated "
						+ "OpenAL Soft render of identical production input. "
						+ "It compares backend transfer and tail behavior; "
						+ "it is not Minecraft main-context output, endpoint "
						+ "loopback, listening validation, or calibration.\"%n"
						+ "}%n",
				OpenAlBackendTransferProbe.INPUT_FRAMES,
				OpenAlBackendTransferProbe.TAIL_FRAMES,
				OpenAlBackendTransferProbe.RENDER_FRAMES,
				sha256Hex(traceReportBytes),
				result.motorSequence(),
				result.motorPcmSha256(),
				result.propellerSequence(),
				result.propellerPcmSha256(),
				environment.snapshotGeneration(),
				environment.rt60Seconds().low(),
				environment.rt60Seconds().mid(),
				environment.rt60Seconds().high(),
				environment.wetGain(),
				environment.transitionSeconds(),
				result.dryPcm().length,
				result.dryPcmSha256(),
				result.efxPcm().length,
				result.efxPcmSha256(),
				result.javaFdnPcm().length,
				result.javaFdnPcmSha256(),
				result.minecraftContextUnchanged(),
				result.minecraftDeviceUnchanged(),
				result.minecraftSoundThreadUnchanged(),
				jsonEscape(result.minecraftSoundThread()),
				jsonEscape(result.workerThread())
		);
		try {
			Files.createDirectories(report.getParent());
			Files.write(dryOutput, result.dryPcm());
			Files.write(efxOutput, result.efxPcm());
			Files.write(javaOutput, result.javaFdnPcm());
			Files.writeString(report, json, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write backend transfer evidence",
					error
			);
		}
	}

	private static void captureOpenAlBackendEnvironmentMatrix(
			ClientGameTestContext context,
			DopplerAudioChunkTrace.Snapshot trace,
			PortalEnvironments environments
	) {
		List<BackendMatrixEnvironment> results = new ArrayList<>();
		for (BackendMatrixEnvironment requested : List.of(
				new BackendMatrixEnvironment(
						"closed",
						environments.closed(),
						null
				),
				new BackendMatrixEnvironment(
						"partial",
						environments.partial(),
						null
				),
				new BackendMatrixEnvironment(
						"open",
						environments.open(),
						null
				)
		)) {
			AtomicReference<CompletableFuture<
					OpenAlBackendTransferProbe.Result>> future =
					new AtomicReference<>();
			context.runOnClient(client -> future.set(
					OpenAlBackendTransferProbe.capture(
							client.getSoundManager(),
							trace,
							requested.controls()
					)
			));
			context.waitFor(
					client -> future.get() != null
							&& future.get().isDone(),
					MAXIMUM_WAIT_TICKS
			);
			results.add(new BackendMatrixEnvironment(
					requested.name(),
					requested.controls(),
					future.get().join()
			));
		}
		writeOpenAlBackendEnvironmentMatrixEvidence(results);
	}

	private static void writeOpenAlBackendEnvironmentMatrixEvidence(
			List<BackendMatrixEnvironment> environments
	) {
		Path report = requiredPropertyPath(
				BACKEND_MATRIX_OUTPUT_PROPERTY
		);
		Path pcmOutput = requiredPropertyPath(
				BACKEND_MATRIX_PCM_OUTPUT_PROPERTY
		);
		Path traceReport = requiredPropertyPath(
				DOPPLER_CHUNK_OUTPUT_PROPERTY
		);
		Path portalReport = requiredPropertyPath(
				PORTAL_OUTPUT_PROPERTY
		);
		byte[] traceReportBytes;
		byte[] portalReportBytes;
		try {
			traceReportBytes = Files.readAllBytes(traceReport);
			portalReportBytes = Files.readAllBytes(portalReport);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not bind backend matrix source reports",
					error
			);
		}
		if (environments.size() != 3
				|| !environments.get(0).name().equals("closed")
				|| !environments.get(1).name().equals("partial")
				|| !environments.get(2).name().equals("open")) {
			throw new AssertionError(
					"backend matrix environment order is invalid"
			);
		}
		double closedMid = environments.get(0).controls()
				.rt60Seconds().mid();
		double partialMid = environments.get(1).controls()
				.rt60Seconds().mid();
		double openMid = environments.get(2).controls()
				.rt60Seconds().mid();
		if (!(closedMid > partialMid && partialMid > openMid)) {
			throw new AssertionError(
					"backend matrix mid RT60 is not strictly ordered"
			);
		}

		ByteArrayOutputStream pcm = new ByteArrayOutputStream();
		StringBuilder environmentJson = new StringBuilder();
		for (int environmentIndex = 0;
				environmentIndex < environments.size();
				environmentIndex++) {
			BackendMatrixEnvironment environment =
					environments.get(environmentIndex);
			OpenAlBackendTransferProbe.Result result =
					environment.result();
			if (!result.minecraftContextUnchanged()
					|| !result.minecraftDeviceUnchanged()
					|| !result.minecraftSoundThreadUnchanged()
					|| result.environment() != environment.controls()) {
				throw new AssertionError(
						"backend matrix context or controls detached"
				);
			}
			environmentJson.append(String.format(
					Locale.ROOT,
					"    {%n"
							+ "      \"name\": \"%s\",%n"
							+ "      \"snapshot_generation\": %d,%n"
							+ "      \"rt60_seconds\": "
							+ "{\"low\":%.9f,\"mid\":%.9f,"
							+ "\"high\":%.9f},%n"
							+ "      \"wet_gain\": %.9f,%n"
							+ "      \"transition_seconds\": %.9f,%n"
							+ "      \"outputs\": [%n",
					environment.name(),
					environment.controls().snapshotGeneration(),
					environment.controls().rt60Seconds().low(),
					environment.controls().rt60Seconds().mid(),
					environment.controls().rt60Seconds().high(),
					environment.controls().wetGain(),
					environment.controls().transitionSeconds()
			));
			String[] backendNames = {"dry", "efx", "java_fdn"};
			byte[][] backendPcm = {
					result.dryPcm(),
					result.efxPcm(),
					result.javaFdnPcm()
			};
			for (int backend = 0;
					backend < backendNames.length;
					backend++) {
				int offset = pcm.size();
				byte[] payload = backendPcm[backend];
				pcm.writeBytes(payload);
				environmentJson.append(String.format(
						Locale.ROOT,
						"        {\"backend\":\"%s\","
								+ "\"pcm_offset\":%d,"
								+ "\"pcm_bytes\":%d,"
								+ "\"pcm_sha256\":\"%s\"}%s%n",
						backendNames[backend],
						offset,
						payload.length,
						sha256Hex(payload),
						backend + 1 == backendNames.length
								? ""
								: ","
				));
			}
			environmentJson.append("      ]\n    }");
			if (environmentIndex + 1 < environments.size()) {
				environmentJson.append(',');
			}
			environmentJson.append('\n');
		}
		OpenAlBackendTransferProbe.Result first =
				environments.get(0).result();
		byte[] pcmBytes = pcm.toByteArray();
		String json = String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": "
						+ "\"valid-backend-environment-matrix\",%n"
						+ "  \"render_format\": "
						+ "\"s16le-mono-48000\",%n"
						+ "  \"input_frames\": %d,%n"
						+ "  \"tail_frames\": %d,%n"
						+ "  \"render_frames_per_output\": %d,%n"
						+ "  \"doppler_trace_report_sha256\": \"%s\",%n"
						+ "  \"portal_report_sha256\": \"%s\",%n"
						+ "  \"motor_sequence\": %d,%n"
						+ "  \"motor_pcm_sha256\": \"%s\",%n"
						+ "  \"propeller_sequence\": %d,%n"
						+ "  \"propeller_pcm_sha256\": \"%s\",%n"
						+ "  \"matrix_pcm_bytes\": %d,%n"
						+ "  \"matrix_pcm_sha256\": \"%s\",%n"
						+ "  \"environments\": [%n%s  ],%n"
						+ "  \"production_parameter_mapping_reused\": true,%n"
						+ "  \"production_java_fdn_reused\": true,%n"
						+ "  \"minecraft_context_unchanged\": true,%n"
						+ "  \"minecraft_device_unchanged\": true,%n"
						+ "  \"physical_playback_device_opened\": false,%n"
						+ "  \"capture_device_opened\": false,%n"
						+ "  \"real_audio_capture\": false,%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"claim_boundary\": \"Steady-state isolated "
						+ "software-renderer matrix using production PCM and "
						+ "three integrated Minecraft portal environments; "
						+ "not a dynamic transition, endpoint capture, "
						+ "listening result, or release calibration.\"%n"
						+ "}%n",
				OpenAlBackendTransferProbe.INPUT_FRAMES,
				OpenAlBackendTransferProbe.TAIL_FRAMES,
				OpenAlBackendTransferProbe.RENDER_FRAMES,
				sha256Hex(traceReportBytes),
				sha256Hex(portalReportBytes),
				first.motorSequence(),
				first.motorPcmSha256(),
				first.propellerSequence(),
				first.propellerPcmSha256(),
				pcmBytes.length,
				sha256Hex(pcmBytes),
				environmentJson
		);
		try {
			Files.createDirectories(report.getParent());
			Files.write(pcmOutput, pcmBytes);
			Files.writeString(report, json, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write backend environment matrix",
					error
			);
		}
	}

	private static void captureOpenAlBackendTransition(
			ClientGameTestContext context,
			DopplerAudioChunkTrace.Snapshot trace,
			PortalEnvironments environments
	) {
		List<FdnEnvironmentMapper.Controls> sequence = List.of(
				environments.closed(),
				environments.partial(),
				environments.open(),
				environments.closed()
		);
		AtomicReference<CompletableFuture<
				OpenAlBackendTransitionProbe.Result>> future =
				new AtomicReference<>();
		context.runOnClient(client -> future.set(
				OpenAlBackendTransitionProbe.capture(
						client.getSoundManager(),
						trace,
						sequence
				)
		));
		context.waitFor(
				client -> future.get() != null && future.get().isDone(),
				MAXIMUM_WAIT_TICKS
		);
		writeOpenAlBackendTransitionEvidence(future.get().join());
	}

	private static void writeOpenAlBackendTransitionEvidence(
			OpenAlBackendTransitionProbe.Result result
	) {
		Path report = requiredPropertyPath(
				BACKEND_TRANSITION_OUTPUT_PROPERTY
		);
		Path dryOutput = requiredPropertyPath(
				BACKEND_TRANSITION_DRY_PCM_OUTPUT_PROPERTY
		);
		Path efxOutput = requiredPropertyPath(
				BACKEND_TRANSITION_EFX_PCM_OUTPUT_PROPERTY
		);
		Path javaOutput = requiredPropertyPath(
				BACKEND_TRANSITION_JAVA_PCM_OUTPUT_PROPERTY
		);
		Path traceReport = requiredPropertyPath(
				DOPPLER_CHUNK_OUTPUT_PROPERTY
		);
		Path portalReport = requiredPropertyPath(PORTAL_OUTPUT_PROPERTY);
		if (!result.minecraftContextUnchanged()
				|| !result.minecraftDeviceUnchanged()
				|| !result.minecraftSoundThreadUnchanged()
				|| result.inputs().size() != 8
				|| result.environments().size() != 4
				|| result.dryAlError() != org.lwjgl.openal.AL10.AL_NO_ERROR
				|| result.dryAlcError()
						!= org.lwjgl.openal.ALC10.ALC_NO_ERROR
				|| result.efxAlError() != org.lwjgl.openal.AL10.AL_NO_ERROR
				|| result.efxAlcError()
						!= org.lwjgl.openal.ALC10.ALC_NO_ERROR
				|| result.javaFdnAlError()
						!= org.lwjgl.openal.AL10.AL_NO_ERROR
				|| result.javaFdnAlcError()
						!= org.lwjgl.openal.ALC10.ALC_NO_ERROR) {
			throw new AssertionError(
					"backend transition controls failed"
			);
		}
		byte[] traceReportBytes;
		byte[] portalReportBytes;
		try {
			traceReportBytes = Files.readAllBytes(traceReport);
			portalReportBytes = Files.readAllBytes(portalReport);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not bind backend transition sources",
					error
			);
		}
		StringBuilder inputs = new StringBuilder();
		for (int index = 0; index < result.inputs().size(); index++) {
			OpenAlBackendTransitionProbe.InputChunk input =
					result.inputs().get(index);
			inputs.append(String.format(
					Locale.ROOT,
					"    {\"layer\":\"%s\",\"sequence\":%d,"
							+ "\"pcm_sha256\":\"%s\"}%s%n",
					input.layer(),
					input.sequence(),
					input.pcmSha256(),
					index + 1 == result.inputs().size() ? "" : ","
			));
		}
		String[] names = {"closed", "partial", "open", "closed"};
		StringBuilder environments = new StringBuilder();
		for (int index = 0;
				index < result.environments().size();
				index++) {
			FdnEnvironmentMapper.Controls controls =
					result.environments().get(index);
			environments.append(String.format(
					Locale.ROOT,
					"    {\"segment\":%d,\"name\":\"%s\","
							+ "\"snapshot_generation\":%d,"
							+ "\"rt60_seconds\":{\"low\":%.9f,"
							+ "\"mid\":%.9f,\"high\":%.9f},"
							+ "\"wet_gain\":%.9f,"
							+ "\"transition_seconds\":%.9f}%s%n",
					index,
					names[index],
					controls.snapshotGeneration(),
					controls.rt60Seconds().low(),
					controls.rt60Seconds().mid(),
					controls.rt60Seconds().high(),
					controls.wetGain(),
					index == 0 ? 0.0 : controls.transitionSeconds(),
					index + 1 == result.environments().size()
							? ""
							: ","
			));
		}
		byte[] dryPcm = result.dryPcm();
		byte[] efxPcm = result.efxPcm();
		byte[] javaPcm = result.javaFdnPcm();
		String json = String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": "
						+ "\"valid-backend-dynamic-transition\",%n"
						+ "  \"render_format\": "
						+ "\"s16le-mono-48000\",%n"
						+ "  \"segment_frames\": %d,%n"
						+ "  \"segment_count\": %d,%n"
						+ "  \"render_frames\": %d,%n"
						+ "  \"boundary_frames\": [48000,96000,144000],%n"
						+ "  \"doppler_trace_report_sha256\": \"%s\",%n"
						+ "  \"portal_report_sha256\": \"%s\",%n"
						+ "  \"motor_pcm_sha256\": \"%s\",%n"
						+ "  \"propeller_pcm_sha256\": \"%s\",%n"
						+ "  \"inputs\": [%n%s  ],%n"
						+ "  \"environments\": [%n%s  ],%n"
						+ "  \"dry_pcm_bytes\": %d,%n"
						+ "  \"dry_pcm_sha256\": \"%s\",%n"
						+ "  \"efx_pcm_bytes\": %d,%n"
						+ "  \"efx_pcm_sha256\": \"%s\",%n"
						+ "  \"java_fdn_pcm_bytes\": %d,%n"
						+ "  \"java_fdn_pcm_sha256\": \"%s\",%n"
						+ "  \"efx_parameter_update\": "
						+ "\"immediate-production-write\",%n"
						+ "  \"java_fdn_parameter_update\": "
						+ "\"production-0.2-second-exponential-smoothing\",%n"
						+ "  \"fdn_tail_cleared_at_boundary\": false,%n"
						+ "  \"minecraft_context_unchanged\": true,%n"
						+ "  \"minecraft_device_unchanged\": true,%n"
						+ "  \"minecraft_sound_thread_unchanged\": true,%n"
						+ "  \"minecraft_sound_thread\": \"%s\",%n"
						+ "  \"loopback_worker_thread\": \"%s\",%n"
						+ "  \"physical_playback_device_opened\": false,%n"
						+ "  \"capture_device_opened\": false,%n"
						+ "  \"real_audio_capture\": false,%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"claim_boundary\": \"Continuous four-chunk "
						+ "production input with exact software-render "
						+ "parameter boundaries. This is not Minecraft "
						+ "main-context output, an endpoint capture, "
						+ "perceptual validation, or release calibration.\"%n"
						+ "}%n",
				OpenAlBackendTransitionProbe.SEGMENT_FRAMES,
				OpenAlBackendTransitionProbe.SEGMENT_COUNT,
				OpenAlBackendTransitionProbe.RENDER_FRAMES,
				sha256Hex(traceReportBytes),
				sha256Hex(portalReportBytes),
				result.motorPcmSha256(),
				result.propellerPcmSha256(),
				inputs,
				environments,
				dryPcm.length,
				result.dryPcmSha256(),
				efxPcm.length,
				result.efxPcmSha256(),
				javaPcm.length,
				result.javaFdnPcmSha256(),
				jsonEscape(result.minecraftSoundThread()),
				jsonEscape(result.workerThread())
		);
		try {
			Files.createDirectories(report.getParent());
			Files.write(dryOutput, dryPcm);
			Files.write(efxOutput, efxPcm);
			Files.write(javaOutput, javaPcm);
			Files.writeString(report, json, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write backend transition evidence",
					error
			);
		}
	}

	private static void captureOpenAlLoopbackRender(
			ClientGameTestContext context,
			DopplerAudioChunkTrace.Snapshot trace
	) {
		AtomicReference<CompletableFuture<
				OpenAlLoopbackRenderProbe.Result>> future =
				new AtomicReference<>();
		context.runOnClient(client -> future.set(
				OpenAlLoopbackRenderProbe.capture(
						client.getSoundManager(),
						trace
				)
		));
		context.waitFor(
				client -> future.get() != null && future.get().isDone(),
				MAXIMUM_WAIT_TICKS
		);
		writeOpenAlLoopbackRenderEvidence(future.get().join());
	}

	private static void writeOpenAlLoopbackRenderEvidence(
			OpenAlLoopbackRenderProbe.Result result
	) {
		Path traceReport = requiredPropertyPath(
				DOPPLER_CHUNK_OUTPUT_PROPERTY
		);
		Path report = requiredPropertyPath(
				OPENAL_LOOPBACK_RENDER_OUTPUT_PROPERTY
		);
		Path pcmOutput = requiredPropertyPath(
				OPENAL_LOOPBACK_RENDER_PCM_OUTPUT_PROPERTY
		);
		byte[] traceReportBytes;
		try {
			traceReportBytes = Files.readAllBytes(traceReport);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not read Doppler trace for loopback binding",
					error
			);
		}
		int midpoint = result.renderFrames() / 2;
		if (!result.minecraftContextUnchanged()
				|| !result.minecraftDeviceUnchanged()
				|| !result.minecraftSoundThreadUnchanged()
				|| !result.loopbackSupported()
				|| !result.threadContextSupported()
				|| !result.formatSupported()
				|| result.silencePeakAbsoluteSample() > 1
				|| result.motorOffsetBeforeHold() != 0
				|| result.propellerOffsetBeforeHold() != 0
				|| result.motorOffsetAfterHold() != 0
				|| result.propellerOffsetAfterHold() != 0
				|| Math.abs(result.motorMidpointOffset() - midpoint) > 1
				|| Math.abs(result.propellerMidpointOffset() - midpoint) > 1
				|| result.motorMidpointState() !=
						org.lwjgl.openal.AL10.AL_PLAYING
				|| result.propellerMidpointState() !=
						org.lwjgl.openal.AL10.AL_PLAYING
				|| result.motorFinalState() !=
						org.lwjgl.openal.AL10.AL_STOPPED
				|| result.propellerFinalState() !=
						org.lwjgl.openal.AL10.AL_STOPPED
				|| result.renderedNonzeroSamples() < midpoint
				|| result.renderedPeakAbsoluteSample() <= 0
				|| result.renderedPeakAbsoluteSample() > 32_767
				|| result.renderedRms() <= 1.0
				|| result.summedInputCorrelation() < 0.90
				|| result.alError() != org.lwjgl.openal.AL10.AL_NO_ERROR
				|| result.alcError() !=
						org.lwjgl.openal.ALC10.ALC_NO_ERROR) {
			throw new AssertionError(
					"isolated OpenAL loopback render gates failed: "
							+ "main=" + result.minecraftContextUnchanged()
							+ "/" + result.minecraftDeviceUnchanged()
							+ "/" + result.minecraftSoundThreadUnchanged()
							+ " ext=" + result.loopbackSupported()
							+ "/" + result.threadContextSupported()
							+ "/" + result.formatSupported()
							+ " silence=" + result.silenceNonzeroSamples()
							+ "/" + result.silencePeakAbsoluteSample()
							+ " offsets="
							+ result.motorOffsetBeforeHold() + "/"
							+ result.propellerOffsetBeforeHold() + " -> "
							+ result.motorOffsetAfterHold() + "/"
							+ result.propellerOffsetAfterHold() + " -> "
							+ result.motorMidpointOffset() + "/"
							+ result.propellerMidpointOffset()
							+ " states=" + result.motorMidpointState()
							+ "/" + result.propellerMidpointState()
							+ " -> " + result.motorFinalState()
							+ "/" + result.propellerFinalState()
							+ " signal=" + result.renderedNonzeroSamples()
							+ "/" + result.renderedPeakAbsoluteSample()
							+ "/" + result.renderedRms()
							+ "/" + result.summedInputCorrelation()
							+ " lag=" + result.summedInputLagSamples()
							+ " errors=" + result.alError()
							+ "/" + result.alcError()
			);
		}
		byte[] renderedPcm = result.renderedPcm();
		String json = String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": \"valid-openal-loopback-render\",%n"
						+ "  \"extension\": \"ALC_SOFT_loopback\",%n"
						+ "  \"thread_context_extension\": "
						+ "\"ALC_EXT_thread_local_context\",%n"
						+ "  \"loopback_supported\": %s,%n"
						+ "  \"thread_context_supported\": %s,%n"
						+ "  \"format_supported\": %s,%n"
						+ "  \"render_format\": "
						+ "\"s16le-mono-48000\",%n"
						+ "  \"render_frames\": %d,%n"
						+ "  \"rendered_pcm_bytes\": %d,%n"
						+ "  \"rendered_pcm_sha256\": \"%s\",%n"
						+ "  \"doppler_trace_report_sha256\": \"%s\",%n"
						+ "  \"inputs\": [%n"
						+ "    {\"layer\":\"motor\",\"sequence\":%d,"
						+ "\"pcm_bytes\":%d,\"pcm_sha256\":\"%s\"},%n"
						+ "    {\"layer\":\"propeller\",\"sequence\":%d,"
						+ "\"pcm_bytes\":%d,\"pcm_sha256\":\"%s\"}%n"
						+ "  ],%n"
						+ "  \"silence_control_frames\": %d,%n"
						+ "  \"silence_nonzero_samples\": %d,%n"
						+ "  \"silence_peak_absolute_sample\": %d,%n"
						+ "  \"silence_quantization_dither_bounded\": true,%n"
						+ "  \"wall_clock_hold_millis\": %d,%n"
						+ "  \"offsets_before_hold\": [%d,%d],%n"
						+ "  \"offsets_after_hold\": [%d,%d],%n"
						+ "  \"midpoint_offsets\": [%d,%d],%n"
						+ "  \"midpoint_states\": [%d,%d],%n"
						+ "  \"final_states\": [%d,%d],%n"
						+ "  \"rendered_nonzero_samples\": %d,%n"
						+ "  \"rendered_peak_absolute_sample\": %d,%n"
						+ "  \"rendered_rms\": %.9f,%n"
						+ "  \"summed_input_correlation\": %.9f,%n"
						+ "  \"summed_input_lag_samples\": %d,%n"
						+ "  \"minecraft_context_unchanged\": %s,%n"
						+ "  \"minecraft_device_unchanged\": %s,%n"
						+ "  \"minecraft_sound_thread_unchanged\": %s,%n"
						+ "  \"minecraft_sound_thread\": \"%s\",%n"
						+ "  \"loopback_worker_thread\": \"%s\",%n"
						+ "  \"al_error_code\": %d,%n"
						+ "  \"alc_error_code\": %d,%n"
						+ "  \"minecraft_audio_path_changed\": false,%n"
						+ "  \"physical_playback_device_opened\": false,%n"
						+ "  \"capture_device_opened\": false,%n"
						+ "  \"real_audio_capture\": false,%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"claim_boundary\": \"Exact production motor "
						+ "and propeller PCM chunks were rendered by an "
						+ "isolated OpenAL Soft loopback context. This is "
						+ "renderer readback, not Minecraft main-context "
						+ "output, physical playback, endpoint loopback, "
						+ "underrun evidence, or listening calibration.\"%n"
						+ "}%n",
				result.loopbackSupported(),
				result.threadContextSupported(),
				result.formatSupported(),
				result.renderFrames(),
				renderedPcm.length,
				result.renderedPcmSha256(),
				sha256Hex(traceReportBytes),
				result.motorSequence(),
				result.motorPcmBytes(),
				result.motorPcmSha256(),
				result.propellerSequence(),
				result.propellerPcmBytes(),
				result.propellerPcmSha256(),
				OpenAlLoopbackRenderProbe.SILENCE_CONTROL_FRAMES,
				result.silenceNonzeroSamples(),
				result.silencePeakAbsoluteSample(),
				OpenAlLoopbackRenderProbe.WALL_CLOCK_HOLD_MILLIS,
				result.motorOffsetBeforeHold(),
				result.propellerOffsetBeforeHold(),
				result.motorOffsetAfterHold(),
				result.propellerOffsetAfterHold(),
				result.motorMidpointOffset(),
				result.propellerMidpointOffset(),
				result.motorMidpointState(),
				result.propellerMidpointState(),
				result.motorFinalState(),
				result.propellerFinalState(),
				result.renderedNonzeroSamples(),
				result.renderedPeakAbsoluteSample(),
				result.renderedRms(),
				result.summedInputCorrelation(),
				result.summedInputLagSamples(),
				result.minecraftContextUnchanged(),
				result.minecraftDeviceUnchanged(),
				result.minecraftSoundThreadUnchanged(),
				jsonEscape(result.minecraftSoundThread()),
				jsonEscape(result.workerThread()),
				result.alError(),
				result.alcError()
		);
		try {
			Files.createDirectories(report.getParent());
			Files.createDirectories(pcmOutput.getParent());
			Files.write(pcmOutput, renderedPcm);
			Files.writeString(report, json, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write OpenAL loopback render evidence",
					error
			);
		}
	}

	private static void writeOpenAlEventQueueHealthEvidence(
			OpenAlEventQueueHealthProbe.Snapshot snapshot,
			int motorSource,
			int propellerSource
	) {
		List<String> events = new ArrayList<>();
		int motorCompleted = 0;
		int propellerCompleted = 0;
		int controlCompleted = 0;
		int controlStopped = 0;
		int productionStopped = 0;
		Set<String> callbackThreads = new java.util.HashSet<>();
		for (int index = 0; index < snapshot.events().size(); index++) {
			OpenAlEventQueueHealthProbe.Event event =
					snapshot.events().get(index);
			if (event.sequence() != index
					|| event.monotonicNs() < snapshot.registeredNs()
					|| event.monotonicNs()
							> snapshot.cleanup().cleanupNs()
					|| event.messageLength() < 0
					|| event.userParameter() != 0L
					|| event.callbackThread().isBlank()) {
				throw new AssertionError(
						"OpenAL callback event metadata is invalid"
				);
			}
			callbackThreads.add(event.callbackThread());
			if (event.eventType()
					== org.lwjgl.openal.SOFTEvents
							.AL_EVENT_TYPE_BUFFER_COMPLETED_SOFT) {
				if (event.parameter() <= 0) {
					throw new AssertionError(
							"buffer-completed event count is invalid"
					);
				}
				if (event.object() == motorSource) {
					motorCompleted += event.parameter();
				} else if (event.object() == propellerSource) {
					propellerCompleted += event.parameter();
				} else if (event.object() == snapshot.controlSource()) {
					controlCompleted += event.parameter();
				}
			} else if (event.eventType()
					== org.lwjgl.openal.SOFTEvents
							.AL_EVENT_TYPE_SOURCE_STATE_CHANGED_SOFT) {
				if (event.object() == snapshot.controlSource()
						&& event.parameter()
								== org.lwjgl.openal.AL10.AL_STOPPED) {
					controlStopped++;
				}
				if ((event.object() == motorSource
							|| event.object() == propellerSource)
						&& event.parameter()
								== org.lwjgl.openal.AL10.AL_STOPPED) {
					productionStopped++;
				}
			} else {
				throw new AssertionError(
						"unexpected OpenAL callback event type"
				);
			}
			events.add(openAlQueueHealthEventJson(event));
		}

		if (!snapshot.activeContext()
				|| !snapshot.extensionSupported()
				|| !"Sound engine".equals(snapshot.registrationThread())
				|| !snapshot.existingCallbackPointerZero()
				|| !snapshot.existingUserPointerZero()
				|| !snapshot.callbackRegistered()
				|| !snapshot.callbackPointerMatched()
				|| !snapshot.ownedCallbackPointerNonzero()
				|| snapshot.registrationAlError() != 0
				|| snapshot.droppedEvents() != 0
				|| snapshot.controlSource() <= 0
				|| snapshot.controlBuffer() <= 0
				|| snapshot.controlSource() == motorSource
				|| snapshot.controlSource() == propellerSource
				|| motorCompleted < 3
				|| propellerCompleted < 3
				|| controlCompleted < 1
				|| controlStopped < 1
				|| productionStopped != 0
				|| callbackThreads.isEmpty()
				|| !"Sound engine".equals(
						snapshot.cleanup().threadName()
				)
				|| !snapshot.cleanup().callbackPointerZero()
				|| !snapshot.cleanup().userPointerZero()
				|| !snapshot.cleanup().controlSourceDeleted()
				|| !snapshot.cleanup().controlBufferDeleted()
				|| snapshot.cleanup().alError() != 0
				|| snapshot.quietWindowNs() < 150_000_000L
				|| !snapshot.noEventsAfterCleanup()
				|| snapshot.eventsAfterQuietWindow()
						!= snapshot.cleanup().eventsAtCleanup()) {
			throw new AssertionError(
					"OpenAL event queue-health lifecycle failed"
			);
		}

		Path queueReport = requiredPropertyPath(
				OPENAL_STREAMING_QUEUE_OUTPUT_PROPERTY
		);
		Path output = requiredPropertyPath(
				OPENAL_EVENT_QUEUE_HEALTH_OUTPUT_PROPERTY
		);
		byte[] queueReportBytes;
		try {
			queueReportBytes = Files.readAllBytes(queueReport);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not bind event trace to queue report",
					error
			);
		}
		String json = String.format(
				Locale.ROOT,
				"{\n"
						+ "  \"schema_version\": 1,\n"
						+ "  \"status\": "
						+ "\"valid-openal-events-queue-health-trace\",\n"
						+ "  \"queue_report_sha256\": \"%s\",\n"
						+ "  \"registration\": {"
						+ "\"active_context\":true,"
						+ "\"extension_supported\":true,"
						+ "\"thread_name\":\"Sound engine\","
						+ "\"existing_callback_pointer_zero\":true,"
						+ "\"existing_user_pointer_zero\":true,"
						+ "\"callback_registered\":true,"
						+ "\"callback_pointer_matches_owned\":true,"
						+ "\"buffer_completed_events_enabled\":true,"
						+ "\"source_state_events_enabled\":true,"
						+ "\"registered_ns\":%d,"
						+ "\"al_error\":0},\n"
						+ "  \"positive_control\": {"
						+ "\"silent\":true,"
						+ "\"source_id\":%d,"
						+ "\"buffer_id\":%d,"
						+ "\"sample_rate_hz\":%d,"
						+ "\"sample_width_bits\":%d,"
						+ "\"channels\":1,"
						+ "\"sample_frames\":%d,"
						+ "\"buffer_completed_count\":%d,"
						+ "\"stopped_event_count\":%d},\n"
						+ "  \"production_sources\": {"
						+ "\"motor\":%d,\"propeller\":%d},\n"
						+ "  \"production_buffer_completed_count\": {"
						+ "\"motor\":%d,\"propeller\":%d},\n"
						+ "  \"production_source_stopped_events\": %d,\n"
						+ "  \"events\": [%s],\n"
						+ "  \"callback_thread_count\": %d,\n"
						+ "  \"maximum_events\": %d,\n"
						+ "  \"dropped_events\": %d,\n"
						+ "  \"cleanup\": {"
						+ "\"thread_name\":\"Sound engine\","
						+ "\"cleanup_ns\":%d,"
						+ "\"event_types_disabled\":true,"
						+ "\"callback_unregistered\":true,"
						+ "\"callback_pointer_zero\":true,"
						+ "\"user_pointer_zero\":true,"
						+ "\"control_source_deleted\":true,"
						+ "\"control_buffer_deleted\":true,"
						+ "\"events_at_cleanup\":%d,"
						+ "\"quiet_window_ns\":%d,"
						+ "\"events_after_quiet_window\":%d,"
						+ "\"no_events_after_cleanup\":true,"
						+ "\"al_error\":0},\n"
						+ "  \"bounded_callback_observation\": true,\n"
						+ "  \"underrun_event_defined_by_extension\": false,\n"
						+ "  \"continuous_underrun_observation\": false,\n"
						+ "  \"callback_underrun_counter_available\": false,\n"
						+ "  \"openal_playback_capture\": false,\n"
						+ "  \"real_audio_capture\": false,\n"
						+ "  \"release_calibrated\": false,\n"
						+ "  \"claim_boundary\": \"A temporarily owned "
						+ "AL_SOFT_events callback observed buffer-completed "
						+ "events for both production streaming sources and "
						+ "a silent positive control, with no callback-"
						+ "reported production source stop. The extension "
						+ "does not define underrun events; this is not "
						+ "physical playback or continuous-underrun proof.\"\n"
						+ "}\n",
				sha256Hex(queueReportBytes),
				snapshot.registeredNs(),
				snapshot.controlSource(),
				snapshot.controlBuffer(),
				OpenAlEventQueueHealthProbe.CONTROL_SAMPLE_RATE_HZ,
				OpenAlEventQueueHealthProbe.CONTROL_SAMPLE_WIDTH_BITS,
				OpenAlEventQueueHealthProbe.CONTROL_SAMPLE_FRAMES,
				controlCompleted,
				controlStopped,
				motorSource,
				propellerSource,
				motorCompleted,
				propellerCompleted,
				productionStopped,
				String.join(",", events),
				callbackThreads.size(),
				snapshot.maximumEvents(),
				snapshot.droppedEvents(),
				snapshot.cleanup().cleanupNs(),
				snapshot.cleanup().eventsAtCleanup(),
				snapshot.quietWindowNs(),
				snapshot.eventsAfterQuietWindow()
		);
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(output, json, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write OpenAL event queue-health evidence",
					error
			);
		}
	}

	private static ServerBlackboxEvidence captureServerBlackbox(
			TestSingleplayerContext singleplayer
	) {
		return singleplayer.getServer().computeOnServer(server -> {
			net.minecraft.server.level.ServerPlayer player =
					server.getPlayerList()
							.getPlayers()
							.getFirst();
			List<DroneEntity> drones = player.level().getEntities(
					net.minecraft.world.level.entity.EntityTypeTest
							.forClass(DroneEntity.class),
					player.getBoundingBox().inflate(512.0),
					drone -> drone.isAlive()
							&& drone.isOwnedBy(player.getUUID())
			);
			DroneEntity drone = drones.stream()
					.min(java.util.Comparator.comparingDouble(
							value -> value.distanceToSqr(player)
					))
					.orElseThrow(() ->
							new IllegalStateException(
									"server drone is unavailable"
							)
					);
			return new ServerBlackboxEvidence(
					drone.getId(),
					drone.blackbox().toCsv()
			);
		});
	}

	private static void writeDopplerAudioChunkEvidence(
			DopplerAudioChunkTrace.Snapshot trace,
			ServerBlackboxEvidence blackbox
	) {
		if (trace == null || trace.chunks().isEmpty()) {
			throw new AssertionError(
					"Doppler production chunk trace is empty"
			);
		}
		ByteArrayOutputStream pcm = new ByteArrayOutputStream();
		List<String> chunks = new ArrayList<>();
		long minimumTraceTick = Long.MAX_VALUE;
		long maximumTraceTick = Long.MIN_VALUE;
		int motorChunks = 0;
		int propellerChunks = 0;
		int changedMotorChunks = 0;
		int changedPropellerChunks = 0;
		for (DopplerAudioChunkTrace.Chunk chunk : trace.chunks()) {
			int offset = pcm.size();
			byte[] bytes = chunk.pcmBytes();
			pcm.writeBytes(bytes);
			chunks.add(dopplerAudioChunkJson(
					chunk,
					offset,
					bytes.length
			));
			long tick = chunk.simulationTimeNanos() / 50_000_000L;
			minimumTraceTick = Math.min(minimumTraceTick, tick);
			maximumTraceTick = Math.max(maximumTraceTick, tick);
			if (chunk.layer()
					== com.tenicana.dronecraft.acoustics
							.PhaseContinuousSynthesizer.Layer.MOTOR) {
				motorChunks++;
				if (chunk.targetChanged()) {
					changedMotorChunks++;
				}
			} else {
				propellerChunks++;
				if (chunk.targetChanged()) {
					changedPropellerChunks++;
				}
			}
		}
		BlackboxStats blackboxStats = analyzeBlackbox(blackbox.csv());
		if (blackbox.serverEntityId()
					!= trace.chunks().getFirst().entityId()
				|| minimumTraceTick < blackboxStats.minimumTick()
				|| maximumTraceTick > blackboxStats.maximumTick()
				|| motorChunks < trace.targetChunksPerLayer()
				|| propellerChunks < trace.targetChunksPerLayer()
				|| changedMotorChunks < 1
				|| changedPropellerChunks < 1) {
			throw new AssertionError(
					"Doppler chunk/blackbox identity or coverage failed"
			);
		}
		byte[] pcmBytes = pcm.toByteArray();
		byte[] blackboxBytes =
				blackbox.csv().getBytes(StandardCharsets.UTF_8);
		Path report = requiredPropertyPath(
				DOPPLER_CHUNK_OUTPUT_PROPERTY
		);
		Path pcmOutput = requiredPropertyPath(
				DOPPLER_CHUNK_PCM_OUTPUT_PROPERTY
		);
		Path blackboxOutput = requiredPropertyPath(
				DOPPLER_CHUNK_BLACKBOX_OUTPUT_PROPERTY
		);
		String json = String.format(
				Locale.ROOT,
				"{\n"
						+ "  \"schema_version\": 1,\n"
						+ "  \"status\": "
						+ "\"valid-doppler-production-chunk-trace\",\n"
						+ "  \"entity_id\": %d,\n"
						+ "  \"target_chunks_per_layer\": %d,\n"
						+ "  \"motor_chunks\": %d,\n"
						+ "  \"propeller_chunks\": %d,\n"
						+ "  \"changed_motor_chunks\": %d,\n"
						+ "  \"changed_propeller_chunks\": %d,\n"
						+ "  \"minimum_trace_tick\": %d,\n"
						+ "  \"maximum_trace_tick\": %d,\n"
						+ "  \"pcm_encoding\": \"s16le-mono-48000\",\n"
						+ "  \"pcm_bytes\": %d,\n"
						+ "  \"pcm_sha256\": \"%s\",\n"
						+ "  \"blackbox_rows\": %d,\n"
						+ "  \"blackbox_columns\": %d,\n"
						+ "  \"blackbox_minimum_tick\": %d,\n"
						+ "  \"blackbox_maximum_tick\": %d,\n"
						+ "  \"blackbox_sha256\": \"%s\",\n"
						+ "  \"chunks\": [%s],\n"
						+ "  \"production_stream_read_tapped\": true,\n"
						+ "  \"exact_returned_pcm_bytes\": true,\n"
						+ "  \"mixed_live_stream_measured\": true,\n"
						+ "  \"frequency_smoothing_state_captured\": true,\n"
						+ "  \"frequency_smoothing_checkpoint_captured\": true,\n"
						+ "  \"server_blackbox_csv_bound\": true,\n"
						+ "  \"openal_source_queue_observed\": false,\n"
						+ "  \"openal_playback_capture\": false,\n"
						+ "  \"callback_underrun_counter_available\": false,\n"
						+ "  \"real_audio_capture\": false,\n"
						+ "  \"release_calibrated\": false,\n"
						+ "  \"claim_boundary\": \"Exact mixed PCM16 buffers "
						+ "returned by ProceduralDroneAudioStream.read, "
						+ "before/40-ms/after oscillator state, and the "
						+ "integrated-server blackbox CSV are hash-bound. "
						+ "This does not observe the OpenAL queue, physical "
						+ "playback, callback underruns, or loopback.\"\n"
						+ "}\n",
				blackbox.serverEntityId(),
				trace.targetChunksPerLayer(),
				motorChunks,
				propellerChunks,
				changedMotorChunks,
				changedPropellerChunks,
				minimumTraceTick,
				maximumTraceTick,
				pcmBytes.length,
				sha256Hex(pcmBytes),
				blackboxStats.rows(),
				blackboxStats.columns(),
				blackboxStats.minimumTick(),
				blackboxStats.maximumTick(),
				sha256Hex(blackboxBytes),
				String.join(",", chunks)
		);
		try {
			Files.createDirectories(report.getParent());
			Files.createDirectories(pcmOutput.getParent());
			Files.createDirectories(blackboxOutput.getParent());
			Files.write(pcmOutput, pcmBytes);
			Files.write(
					blackboxOutput,
					blackboxBytes
			);
			Files.writeString(report, json, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write Doppler production chunk evidence",
					error
			);
		}
	}

	private static void writeOpenAlStreamingQueueEvidence(
			DopplerAudioChunkTrace.Snapshot pcmTrace,
			OpenAlStreamingQueueProbe.Snapshot queueTrace
	) {
		if (pcmTrace == null || queueTrace == null) {
			throw new AssertionError(
					"OpenAL streaming queue evidence is incomplete"
			);
		}
		List<String> events = new ArrayList<>();
		int motorEvents = 0;
		int propellerEvents = 0;
		boolean eventsExtensionSupported = false;
		boolean eventCallbackRegistered = false;
		for (OpenAlStreamingQueueProbe.QueueEvent event
				: queueTrace.events()) {
			DopplerAudioChunkTrace.Chunk matching =
					pcmTrace.chunks().stream()
							.filter(chunk ->
									chunk.layer() == event.layer()
											&& chunk.layerSequence()
											== event.layerSequence()
							)
							.findFirst()
							.orElseThrow(() ->
									new AssertionError(
											"queued buffer has no PCM chunk"
									)
							);
			if (!matching.pcmSha256().equals(event.pcmSha256())
					|| matching.pcmBytes().length != event.pcmBytes()
					|| matching.entityId() != event.entityId()
					|| matching.simulationTimeNanos()
							!= event.simulationTimeNanos()) {
				throw new AssertionError(
						"queued OpenAL buffer detached from PCM chunk"
				);
			}
			if (event.layer()
					== com.tenicana.dronecraft.acoustics
							.PhaseContinuousSynthesizer.Layer.MOTOR) {
				motorEvents++;
			} else {
				propellerEvents++;
			}
			eventsExtensionSupported |= event.state().eventsSupported();
			eventCallbackRegistered |=
					event.state().eventCallbackRegistered();
			events.add(openAlStreamingQueueEventJson(event));
		}
		if (motorEvents < queueTrace.targetEventsPerLayer()
				|| propellerEvents < queueTrace.targetEventsPerLayer()) {
			throw new AssertionError(
					"OpenAL streaming queue layer coverage failed"
			);
		}
		Path pcmReport = requiredPropertyPath(
				DOPPLER_CHUNK_OUTPUT_PROPERTY
		);
		Path output = requiredPropertyPath(
				OPENAL_STREAMING_QUEUE_OUTPUT_PROPERTY
		);
		byte[] pcmReportBytes;
		try {
			pcmReportBytes = Files.readAllBytes(pcmReport);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not bind OpenAL queue to PCM report",
					error
			);
		}
		String json = String.format(
				Locale.ROOT,
				"{\n"
						+ "  \"schema_version\": 1,\n"
						+ "  \"status\": "
						+ "\"valid-openal-streaming-queue-trace\",\n"
						+ "  \"target_events_per_layer\": %d,\n"
						+ "  \"motor_events\": %d,\n"
						+ "  \"propeller_events\": %d,\n"
						+ "  \"pcm_report_sha256\": \"%s\",\n"
						+ "  \"events\": [%s],\n"
						+ "  \"minecraft_streaming_buffer_seconds\": 1,\n"
						+ "  \"minecraft_initial_queue_target\": 4,\n"
						+ "  \"production_pcm_to_al_buffer_id_bound\": true,\n"
						+ "  \"openal_queue_state_observed\": true,\n"
						+ "  \"openal_source_state_observed\": true,\n"
						+ "  \"openal_events_extension_supported\": %s,\n"
						+ "  \"openal_event_callback_registered\": %s,\n"
						+ "  \"continuous_underrun_observation\": false,\n"
						+ "  \"callback_underrun_counter_available\": false,\n"
						+ "  \"openal_playback_capture\": false,\n"
						+ "  \"real_audio_capture\": false,\n"
						+ "  \"release_calibrated\": false,\n"
						+ "  \"claim_boundary\": \"Exact production PCM "
						+ "hashes are bound to the OpenAL buffer ids passed "
						+ "to alSourceQueueBuffers and post-queue source "
						+ "state. This is not continuous underrun telemetry, "
						+ "rendered output capture, loopback, or physical "
						+ "playback proof.\"\n"
						+ "}\n",
				queueTrace.targetEventsPerLayer(),
				motorEvents,
				propellerEvents,
				sha256Hex(pcmReportBytes),
				String.join(",", events),
				eventsExtensionSupported,
				eventCallbackRegistered
		);
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(output, json, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write OpenAL streaming queue evidence",
					error
			);
		}
	}

	private static OpenAlClockLatencyProbe.PairResult
	captureOpenAlClockPair(ClientGameTestContext context) {
		OpenAlClockLatencyProbe.Sample first =
				captureOpenAlClockSample(context);
		long notBefore = System.nanoTime() + 150_000_000L;
		context.waitFor(
				client -> System.nanoTime() >= notBefore,
				MAXIMUM_WAIT_TICKS
		);
		OpenAlClockLatencyProbe.Sample second =
				captureOpenAlClockSample(context);
		return OpenAlClockLatencyProbe.validatePair(first, second);
	}

	private static OpenAlClockLatencyProbe.Sample
	captureOpenAlClockSample(ClientGameTestContext context) {
		AtomicReference<CompletableFuture<
				OpenAlClockLatencyProbe.Sample>> pending =
				new AtomicReference<>();
		context.runOnClient(client -> pending.set(
				OpenAlClockLatencyProbe.capture(
						client.getSoundManager()
				)
		));
		context.waitFor(
				client -> pending.get() != null
						&& pending.get().isDone(),
				MAXIMUM_WAIT_TICKS
		);
		return pending.get().join();
	}

	private static void writeOpenAlClockEvidence(
			OpenAlClockLatencyProbe.PairResult beforeReload,
			OpenAlClockLatencyProbe.PairResult afterReload
	) {
		boolean supportStable =
				beforeReload.first().deviceClockSupported()
						== afterReload.first()
								.deviceClockSupported()
				&& beforeReload.first().sourceLatencySupported()
						== afterReload.first()
								.sourceLatencySupported()
				&& beforeReload.first().nativeTelemetryAvailable()
						== afterReload.first()
								.nativeTelemetryAvailable();
		if (!supportStable) {
			throw new AssertionError(
					"OpenAL clock/latency support changed across reload"
			);
		}
		if (!beforeReload.first().deviceName().equals(
				afterReload.first().deviceName()
		)) {
			throw new AssertionError(
					"OpenAL device name changed across sound-engine reload"
			);
		}
		String json = String.format(
				Locale.ROOT,
				"{\n"
						+ "  \"schema_version\": 1,\n"
						+ "  \"status\": "
						+ "\"valid-openal-clock-latency-diagnostic\",\n"
						+ "  \"extension\": "
						+ "\"ALC_SOFT_device_clock\",\n"
						+ "  \"source_extension\": "
						+ "\"AL_SOFT_source_latency\",\n"
						+ "  \"before_reload\": %s,\n"
						+ "  \"after_reload\": %s,\n"
						+ "  \"extension_support_stable\": true,\n"
						+ "  \"device_name_stable\": true,\n"
						+ "  \"sound_engine_reload_exercised\": true,\n"
						+ "  \"physical_device_switch_exercised\": false,\n"
						+ "  \"callback_underrun_counter_available\": false,\n"
						+ "  \"audio_path_changed\": false,\n"
						+ "  \"release_calibrated\": false,\n"
						+ "  \"claim_boundary\": \"Read-only capability "
						+ "probe plus whichever OpenAL Soft device-clock "
						+ "and source-latency telemetry the active runtime "
						+ "exposes around a Minecraft sound-engine reload; "
						+ "no callback underrun count, physical device "
						+ "switch, audible loopback, end-to-end latency, "
						+ "or acoustic calibration.\"\n"
						+ "}\n",
				beforeReload.toJson(),
				afterReload.toJson()
		);
		Path output = requiredPropertyPath(
				OPENAL_CLOCK_OUTPUT_PROPERTY
		);
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(output, json, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write OpenAL clock/latency evidence",
					error
			);
		}
	}

	private static void capturePersistentEfxReload(
			ClientGameTestContext context,
			OpenAlEfxController.Diagnostics before,
			AtomicReference<OpenAlEfxController.Diagnostics> output
	) {
		if (before == null) {
			throw new AssertionError(
					"missing pre-reload persistent EFX diagnostics"
			);
		}
		context.runOnClient(client ->
				client.getSoundManager().reload()
		);
		context.waitFor(client -> {
			OpenAlEfxController.Diagnostics diagnostics =
					DroneSoundManager.openAlEfxDiagnostics();
			AcousticBackendRuntimeTelemetry.Snapshot runtime =
					DroneSoundManager.acousticBackendRuntimeDiagnostics();
			if (diagnostics.operational()
					&& diagnostics.sharedResourcesCreated()
					&& diagnostics.attachedSources() >= 2
					&& diagnostics.allocatedSourceFilters()
							== diagnostics.attachedSources()
					&& diagnostics.contextRebuilds()
							> before.contextRebuilds()
					&& diagnostics.alErrorCode() == 0
					&& runtime.backend() == AcousticBackendSelector
							.RuntimeBackend.OPENAL_EFX
					&& runtime.efxStatus()
							== OpenAlEfxController.Status.OPERATIONAL
					&& runtime.contextRebuilds()
							> before.contextRebuilds()
					&& !runtime.javaStreamActive()
					&& !runtime.doubleWetPath()
					&& !runtime.capturesAudio()) {
				output.set(diagnostics);
				return true;
			}
			return false;
		}, MAXIMUM_WAIT_TICKS);
		if (DroneSoundManager
				.javaListenerReverbActiveForDiagnostics()) {
			throw new AssertionError(
					"Java wet bus must remain suppressed after sound reload"
			);
		}
		OpenAlEfxController.Diagnostics after = output.get();
		String json = String.format(
				Locale.ROOT,
				"{\n"
						+ "  \"schema_version\": 1,\n"
						+ "  \"status\": \"valid-sound-engine-reload\","
						+ "\n"
						+ "  \"reload_entrypoint\": "
						+ "\"Minecraft.SoundManager.reload\",\n"
						+ "  \"before\": %s,\n"
						+ "  \"after\": %s,\n"
						+ "  \"java_wet_bus_suppressed\": true,\n"
						+ "  \"sound_engine_reload_exercised\": true,\n"
						+ "  \"physical_device_switch_exercised\": false,\n"
						+ "  \"release_calibrated\": false,\n"
						+ "  \"claim_boundary\": \"Minecraft sound-engine "
						+ "destroy/load and OpenAL context rebuild only; "
						+ "physical output-device switching, audible "
						+ "calibration, and callback underrun remain "
						+ "unmeasured.\"\n"
						+ "}\n",
				diagnosticsJson(before),
				diagnosticsJson(after)
		);
		Path report = requiredOpenAlReloadOutputPath();
		try {
			Files.createDirectories(report.getParent());
			Files.writeString(report, json, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write sound-engine reload evidence",
					error
			);
		}
	}

	private static void captureAudioLabProtocol(
			ClientGameTestContext context,
			TestSingleplayerContext singleplayer
	) {
		runAudioLabCase(
				context,
				singleplayer,
				requiredPropertyPath(AUDIO_LAB_DRY_OUTPUT_PROPERTY),
				"dry",
				false
		);
		runAudioLabCase(
				context,
				singleplayer,
				requiredPropertyPath(AUDIO_LAB_JAVA_OUTPUT_PROPERTY),
				"java-fdn",
				false
		);
		runAudioLabCase(
				context,
				singleplayer,
				requiredPropertyPath(AUDIO_LAB_EFX_OUTPUT_PROPERTY),
				"openal-efx",
				true
		);
	}

	private static void runAudioLabCase(
			ClientGameTestContext context,
			TestSingleplayerContext singleplayer,
			Path output,
			String backend,
			boolean reload
	) {
		try {
			Files.deleteIfExists(output);
		} catch (java.io.IOException error) {
			throw new IllegalStateException(
					"could not clear preceding audio-lab report",
					error
			);
		}
		singleplayer.getServer().runCommand(
				"execute as @p at @s run fpvdrone diagnostic start 12"
		);
		context.waitFor(
				MinecraftDdaCaptureClientGameTest::hasAudibleDrone,
				MAXIMUM_WAIT_TICKS
		);
		context.runOnClient(client ->
				DroneSoundManager.requestAudioLabDiagnostic(
						output,
						backend,
						reload
				)
		);
		context.waitFor(
				client -> Files.isRegularFile(output),
				MAXIMUM_WAIT_TICKS
		);
		context.waitFor(
				client -> AcousticBackendSelector.mode()
						== AcousticBackendSelector.Mode.DEFAULT,
				MAXIMUM_WAIT_TICKS
		);
	}

	private static String diagnosticsJson(
			OpenAlEfxController.Diagnostics diagnostics
	) {
		return String.format(
				Locale.ROOT,
				"{\"status\":\"%s\",\"operational\":%s,"
						+ "\"shared_resources_created\":%s,"
						+ "\"attached_sources\":%d,"
						+ "\"allocated_source_filters\":%d,"
						+ "\"context_rebuilds\":%d,"
						+ "\"cleanup_count\":%d,"
						+ "\"al_error_code\":%d,"
						+ "\"last_fault_stage\":\"%s\","
						+ "\"fault_injection_count\":%d}",
				diagnostics.status(),
				diagnostics.operational(),
				diagnostics.sharedResourcesCreated(),
				diagnostics.attachedSources(),
				diagnostics.allocatedSourceFilters(),
				diagnostics.contextRebuilds(),
				diagnostics.cleanupCount(),
				diagnostics.alErrorCode(),
				diagnostics.lastFaultStage().token(),
				diagnostics.faultInjectionCount()
		);
	}

	private static String dopplerDiagnosticsJson(
			OpenAlNativeDopplerGuard.Diagnostics diagnostics
	) {
		return String.format(
				Locale.ROOT,
				"{\"enabled\":%s,"
						+ "\"operational\":%s,"
						+ "\"guarded_sources\":%d,"
						+ "\"context_rebuilds\":%d,"
						+ "\"restored_sources\":%d,"
						+ "\"doppler_factor\":%.9f,"
						+ "\"speed_of_sound_meters_per_second\":%.9f,"
						+ "\"distance_model\":%d,"
						+ "\"listener_velocity_magnitude\":%.9f,"
						+ "\"maximum_guard_velocity_difference\":%.12f,"
						+ "\"maximum_native_ratio_deviation_before\":%.12f,"
						+ "\"maximum_native_ratio_deviation_after\":%.12f,"
						+ "\"minimum_internal_doppler_ratio\":%.12f,"
						+ "\"maximum_internal_doppler_ratio\":%.12f,"
						+ "\"internally_shifted_sources\":%d,"
						+ "\"minimum_source_pitch\":%.9f,"
						+ "\"maximum_source_pitch\":%.9f,"
						+ "\"al_error_code\":%d}",
				diagnostics.enabled(),
				diagnostics.operational(),
				diagnostics.guardedSources(),
				diagnostics.contextRebuilds(),
				diagnostics.restoredSources(),
				diagnostics.dopplerFactor(),
				diagnostics.speedOfSoundMetersPerSecond(),
				diagnostics.distanceModel(),
				diagnostics.listenerVelocityMagnitude(),
				diagnostics.maximumGuardVelocityDifference(),
				diagnostics.maximumNativeRatioDeviationBefore(),
				diagnostics.maximumNativeRatioDeviationAfter(),
				diagnostics.minimumInternalDopplerRatio(),
				diagnostics.maximumInternalDopplerRatio(),
				diagnostics.internallyShiftedSources(),
				diagnostics.minimumSourcePitch(),
				diagnostics.maximumSourcePitch(),
				diagnostics.alErrorCode()
		);
	}

	private static String dopplerPcmRuntimeJson(
			DopplerPcmRuntimeProbe.Result result
	) {
		return String.format(
				Locale.ROOT,
				"{\"entity_id\":%d,"
						+ "\"simulation_time_nanos\":%d,"
						+ "\"source_position_m\":%s,"
						+ "\"source_velocity_mps\":%s,"
						+ "\"listener_position_m\":%s,"
						+ "\"listener_velocity_mps\":%s,"
						+ "\"sound_speed_mps\":%.12f,"
						+ "\"render_state_doppler_ratio\":%.12f,"
						+ "\"motor\":%s,"
						+ "\"propeller\":%s}",
				result.entityId(),
				result.simulationTimeNanos(),
				vectorJson(result.sourcePositionMeters()),
				vectorJson(result.sourceVelocityMetersPerSecond()),
				vectorJson(result.listenerPositionMeters()),
				vectorJson(result.listenerVelocityMetersPerSecond()),
				result.soundSpeedMetersPerSecond(),
				result.renderStateDopplerRatio(),
				dopplerPcmMeasurementJson(result.motor()),
				dopplerPcmMeasurementJson(result.propeller())
		);
	}

	private static String dopplerPcmMeasurementJson(
			com.tenicana.dronecraft.acoustics.DopplerPcmConformance.Result
					result
	) {
		return String.format(
				Locale.ROOT,
				"{\"layer\":\"%s\","
						+ "\"tone_kind\":\"%s\","
						+ "\"rotor_index\":%d,"
						+ "\"order\":%d,"
						+ "\"base_frequency_hz\":%.12f,"
						+ "\"doppler_frequency_ratio\":%.12f,"
						+ "\"expected_frequency_hz\":%.12f,"
						+ "\"measured_frequency_hz\":%.12f,"
						+ "\"frequency_error_hz\":%.12f,"
						+ "\"frequency_error_ppm\":%.12f,"
						+ "\"sound_speed_mps\":%.12f,"
						+ "\"listener_radial_velocity_mps\":%.12f,"
						+ "\"source_radial_velocity_mps\":%.12f,"
						+ "\"sample_rate_hz\":%d,"
						+ "\"analyzed_samples\":%d,"
						+ "\"positive_crossings\":%d,"
						+ "\"clipped_samples\":%d,"
						+ "\"broadband_excluded\":%s,"
						+ "\"pcm16_quantized\":%s}",
				result.layer().name().toLowerCase(Locale.ROOT),
				result.toneKind().name().toLowerCase(Locale.ROOT),
				result.rotorIndex(),
				result.order(),
				result.baseFrequencyHz(),
				result.dopplerFrequencyRatio(),
				result.expectedFrequencyHz(),
				result.measuredFrequencyHz(),
				result.frequencyErrorHz(),
				result.frequencyErrorPpm(),
				result.kinematics().soundSpeedMetersPerSecond(),
				result.kinematics()
						.listenerRadialVelocityMetersPerSecond(),
				result.kinematics()
						.sourceRadialVelocityMetersPerSecond(),
				result.sampleRateHz(),
				result.analyzedSamples(),
				result.positiveCrossings(),
				result.clippedSamples(),
				result.broadbandExcluded(),
				result.pcm16Quantized()
		);
	}

	private static String vectorJson(AcousticVector vector) {
		return String.format(
				Locale.ROOT,
				"[%.12f,%.12f,%.12f]",
				vector.x(),
				vector.y(),
				vector.z()
		);
	}

	private static String dopplerAudioChunkJson(
			DopplerAudioChunkTrace.Chunk chunk,
			int pcmOffset,
			int pcmBytes
	) {
		return String.format(
				Locale.ROOT,
				"{\"sequence\":%d,"
						+ "\"layer_sequence\":%d,"
						+ "\"layer\":\"%s\","
						+ "\"entity_id\":%d,"
						+ "\"simulation_time_nanos\":%d,"
						+ "\"simulation_tick\":%d,"
						+ "\"doppler_frequency_ratio\":%.12f,"
						+ "\"emission_sha256\":\"%s\","
						+ "\"tracked_tone_kind\":\"%s\","
						+ "\"tracked_rotor_index\":%d,"
						+ "\"tracked_order\":%d,"
						+ "\"source_rotor_rpm\":%.12f,"
						+ "\"source_rotor_blade_count\":%d,"
						+ "\"frame_target_frequency_hz\":%.12f,"
						+ "\"frequency_smoothing_samples\":%d,"
						+ "\"before_current_frequency_hz\":%s,"
						+ "\"before_target_frequency_hz\":%s,"
						+ "\"before_ramp_samples_remaining\":%d,"
						+ "\"smoothing_checkpoint_samples\":%d,"
						+ "\"checkpoint_current_frequency_hz\":%.12f,"
						+ "\"checkpoint_target_frequency_hz\":%.12f,"
						+ "\"checkpoint_ramp_samples_remaining\":%d,"
						+ "\"after_current_frequency_hz\":%.12f,"
						+ "\"after_target_frequency_hz\":%.12f,"
						+ "\"after_ramp_samples_remaining\":%d,"
						+ "\"target_changed\":%s,"
						+ "\"pcm_offset\":%d,"
						+ "\"pcm_bytes\":%d,"
						+ "\"pcm_samples\":%d,"
						+ "\"pcm_sha256\":\"%s\"}",
				chunk.sequence(),
				chunk.layerSequence(),
				chunk.layer().name().toLowerCase(Locale.ROOT),
				chunk.entityId(),
				chunk.simulationTimeNanos(),
				chunk.simulationTimeNanos() / 50_000_000L,
				chunk.dopplerFrequencyRatio(),
				chunk.emissionSha256(),
				chunk.trackedToneKind().name()
						.toLowerCase(Locale.ROOT),
				chunk.trackedRotorIndex(),
				chunk.trackedOrder(),
				chunk.sourceRotorRpm(),
				chunk.sourceRotorBladeCount(),
				chunk.frameTargetFrequencyHz(),
				chunk.frequencySmoothingSamples(),
				jsonNumber(chunk.beforeCurrentFrequencyHz()),
				jsonNumber(chunk.beforeTargetFrequencyHz()),
				chunk.beforeRampSamplesRemaining(),
				chunk.smoothingCheckpointSamples(),
				chunk.checkpointCurrentFrequencyHz(),
				chunk.checkpointTargetFrequencyHz(),
				chunk.checkpointRampSamplesRemaining(),
				chunk.afterCurrentFrequencyHz(),
				chunk.afterTargetFrequencyHz(),
				chunk.afterRampSamplesRemaining(),
				chunk.targetChanged(),
				pcmOffset,
				pcmBytes,
				pcmBytes / Short.BYTES,
				chunk.pcmSha256()
		);
	}

	private static String openAlStreamingQueueEventJson(
			OpenAlStreamingQueueProbe.QueueEvent event
	) {
		OpenAlStreamingQueueProbe.QueueState state = event.state();
		return String.format(
				Locale.ROOT,
				"{\"sequence\":%d,"
						+ "\"layer_sequence\":%d,"
						+ "\"layer\":\"%s\","
						+ "\"entity_id\":%d,"
						+ "\"simulation_time_nanos\":%d,"
						+ "\"simulation_tick\":%d,"
						+ "\"doppler_frequency_ratio\":%.12f,"
						+ "\"pcm_bytes\":%d,"
						+ "\"pcm_sha256\":\"%s\","
						+ "\"source_id\":%d,"
						+ "\"buffer_id\":%d,"
						+ "\"thread_name\":\"%s\","
						+ "\"host_monotonic_ns\":%d,"
						+ "\"buffer_valid\":%s,"
						+ "\"buffer_frequency_hz\":%d,"
						+ "\"buffer_bits\":%d,"
						+ "\"buffer_channels\":%d,"
						+ "\"buffer_bytes\":%d,"
						+ "\"source_state\":%d,"
						+ "\"source_type\":%d,"
						+ "\"buffers_queued\":%d,"
						+ "\"buffers_processed\":%d,"
						+ "\"sample_offset\":%d,"
						+ "\"source_latency_supported\":%s,"
						+ "\"source_offset_seconds\":%.12f,"
						+ "\"source_latency_seconds\":%.12f,"
						+ "\"events_supported\":%s,"
						+ "\"event_callback_registered\":%s,"
						+ "\"al_error\":%d}",
				event.sequence(),
				event.layerSequence(),
				event.layer().name().toLowerCase(Locale.ROOT),
				event.entityId(),
				event.simulationTimeNanos(),
				event.simulationTimeNanos() / 50_000_000L,
				event.dopplerFrequencyRatio(),
				event.pcmBytes(),
				event.pcmSha256(),
				event.source(),
				event.buffer(),
				event.threadName(),
				state.hostMonotonicNs(),
				state.bufferValid(),
				state.bufferFrequency(),
				state.bufferBits(),
				state.bufferChannels(),
				state.bufferBytes(),
				state.sourceState(),
				state.sourceType(),
				state.buffersQueued(),
				state.buffersProcessed(),
				state.sampleOffset(),
				state.sourceLatencySupported(),
				state.sourceOffsetSeconds(),
				state.sourceLatencySeconds(),
				state.eventsSupported(),
				state.eventCallbackRegistered(),
				state.alError()
		);
	}

	private static String openAlQueueHealthEventJson(
			OpenAlEventQueueHealthProbe.Event event
	) {
		String type;
		if (event.eventType()
				== org.lwjgl.openal.SOFTEvents
						.AL_EVENT_TYPE_BUFFER_COMPLETED_SOFT) {
			type = "buffer_completed";
		} else if (event.eventType()
				== org.lwjgl.openal.SOFTEvents
						.AL_EVENT_TYPE_SOURCE_STATE_CHANGED_SOFT) {
			type = "source_state_changed";
		} else {
			throw new IllegalArgumentException(
					"unsupported OpenAL event type"
			);
		}
		return String.format(
				Locale.ROOT,
				"{\"sequence\":%d,"
						+ "\"event_type\":\"%s\","
						+ "\"event_type_code\":%d,"
						+ "\"source_id\":%d,"
						+ "\"parameter\":%d,"
						+ "\"message_length\":%d,"
						+ "\"host_monotonic_ns\":%d,"
						+ "\"callback_thread\":\"%s\","
						+ "\"user_parameter_zero\":%s}",
				event.sequence(),
				type,
				event.eventType(),
				event.object(),
				event.parameter(),
				event.messageLength(),
				event.monotonicNs(),
				event.callbackThread().replace("\\", "\\\\")
						.replace("\"", "\\\""),
				event.userParameter() == 0L
		);
	}

	private static String jsonNumber(double value) {
		return Double.isFinite(value)
				? String.format(Locale.ROOT, "%.12f", value)
				: "null";
	}

	private static BlackboxStats analyzeBlackbox(String csv) {
		String[] lines = csv.split("\\R");
		if (lines.length < 3) {
			throw new AssertionError(
					"server blackbox has too few rows"
			);
		}
		String[] header = lines[0].split(",", -1);
		int tickIndex = Arrays.asList(header).indexOf("tick");
		int rpmIndex = Arrays.asList(header).indexOf("avg_motor_rpm");
		if (tickIndex < 0 || rpmIndex < 0) {
			throw new AssertionError(
					"server blackbox identity columns are missing"
			);
		}
		long minimumTick = Long.MAX_VALUE;
		long maximumTick = Long.MIN_VALUE;
		double maximumAverageRpm = 0.0;
		int rows = 0;
		for (int index = 1; index < lines.length; index++) {
			if (lines[index].isBlank()) {
				continue;
			}
			String[] row = lines[index].split(",", -1);
			if (row.length != header.length) {
				throw new AssertionError(
						"server blackbox row width changed"
				);
			}
			long tick = Long.parseLong(row[tickIndex]);
			double rpm = Double.parseDouble(row[rpmIndex]);
			minimumTick = Math.min(minimumTick, tick);
			maximumTick = Math.max(maximumTick, tick);
			maximumAverageRpm = Math.max(maximumAverageRpm, rpm);
			rows++;
		}
		if (rows < 2 || maximumAverageRpm <= 1_000.0) {
			throw new AssertionError(
					"server blackbox does not cover audible flight"
			);
		}
		return new BlackboxStats(
				rows,
				header.length,
				minimumTick,
				maximumTick,
				maximumAverageRpm
		);
	}

	private static String sha256Hex(byte[] bytes) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(bytes);
			char[] hex = new char[digest.length * 2];
			char[] alphabet = "0123456789abcdef".toCharArray();
			for (int index = 0; index < digest.length; index++) {
				int value = digest[index] & 0xff;
				hex[index * 2] = alphabet[value >>> 4];
				hex[index * 2 + 1] = alphabet[value & 0x0f];
			}
			return new String(hex);
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException(
					"SHA-256 is unavailable",
					error
			);
		}
	}

	private static String jsonEscape(String value) {
		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\r", "\\r")
				.replace("\n", "\\n");
	}

	private static void captureOpenAlFaultFailover(
			ClientGameTestContext context,
			TestSingleplayerContext singleplayer
	) {
		List<EfxFaultCycle> cycles = new ArrayList<>();
		ListenerReverbQueueHandoffProbe.begin();
		boolean handoffFinished = false;
		ListenerReverbQueueHandoffProbe.Snapshot handoffSnapshot = null;
		try {
			for (OpenAlEfxController.FaultStage stage : List.of(
					OpenAlEfxController.FaultStage.RESOURCE_CREATE,
					OpenAlEfxController.FaultStage.PARAMETER_WRITE,
					OpenAlEfxController.FaultStage.SOURCE_ROUTE
			)) {
				singleplayer.getServer().runCommand(
						"execute as @p at @s run "
								+ "fpvdrone diagnostic start 12"
				);
				AtomicReference<OpenAlEfxController.Diagnostics> before =
						new AtomicReference<>();
				context.waitFor(client -> {
					OpenAlEfxController.Diagnostics diagnostics =
							DroneSoundManager.openAlEfxDiagnostics();
					AcousticBackendRuntimeTelemetry.Snapshot runtime =
							DroneSoundManager
									.acousticBackendRuntimeDiagnostics();
					if (diagnostics.operational()
							&& diagnostics.attachedSources() >= 2
							&& diagnostics.alErrorCode() == 0
							&& runtime.javaShadowActive()
							&& !runtime.javaWetActive()
							&& runtime.javaHistoryFrames()
								== ListenerReverbHistoryProducer
										.HISTORY_FRAMES) {
						before.set(diagnostics);
						return true;
					}
					return false;
				}, MAXIMUM_WAIT_TICKS);
				context.runOnClient(client -> {
					AcousticBackendRuntimeTelemetry.Snapshot runtime =
							DroneSoundManager
									.acousticBackendRuntimeDiagnostics();
					ListenerReverbQueueHandoffProbe.arm(
							stage.token(),
							runtime.javaHistoryFrames()
					);
					System.setProperty(
							OpenAlEfxController.FAULT_STAGE_PROPERTY,
							stage.token()
					);
				});

				AtomicReference<OpenAlEfxController.Diagnostics> failed =
						new AtomicReference<>();
				AtomicReference<AcousticBackendRuntimeTelemetry.Snapshot>
						fallback = new AtomicReference<>();
				context.waitFor(client -> {
					OpenAlEfxController.Diagnostics diagnostics =
							DroneSoundManager.openAlEfxDiagnostics();
					AcousticBackendRuntimeTelemetry.Snapshot runtime =
							DroneSoundManager
									.acousticBackendRuntimeDiagnostics();
					if (diagnostics.status()
								== OpenAlEfxController.Status.CONTEXT_FAILED
							&& diagnostics.lastFaultStage() == stage
							&& diagnostics.faultInjectionCount()
								== before.get().faultInjectionCount() + 1
							&& diagnostics.alErrorCode() != 0
							&& !diagnostics.sharedResourcesCreated()
							&& diagnostics.attachedSources() == 0
							&& diagnostics.allocatedSourceFilters() == 0
							&& diagnostics.cleanupCount()
								> before.get().cleanupCount()
							&& runtime.backend()
								== AcousticBackendSelector.RuntimeBackend
										.JAVA_FDN
							&& runtime.efxStatus()
								== OpenAlEfxController.Status.CONTEXT_FAILED
							&& runtime.lastFaultStage() == stage
							&& runtime.javaStreamActive()
							&& !runtime.javaShadowActive()
							&& runtime.javaWetActive()
							&& runtime.javaHistoryFrames()
								== ListenerReverbHistoryProducer
										.HISTORY_FRAMES
							&& !runtime.efxOperational()
							&& !runtime.doubleWetPath()
							&& !runtime.capturesAudio()) {
						failed.set(diagnostics);
						fallback.set(runtime);
						return true;
					}
					return false;
				}, MAXIMUM_WAIT_TICKS);

				AtomicReference<AcousticBackendRuntimeTelemetry.Snapshot>
						stable = new AtomicReference<>();
				context.waitFor(client -> {
					OpenAlEfxController.Diagnostics diagnostics =
							DroneSoundManager.openAlEfxDiagnostics();
					AcousticBackendRuntimeTelemetry.Snapshot runtime =
							DroneSoundManager
									.acousticBackendRuntimeDiagnostics();
					if (runtime.sequence()
								>= fallback.get().sequence() + 3
							&& runtime.backend()
								== AcousticBackendSelector.RuntimeBackend
										.JAVA_FDN
							&& runtime.javaStreamActive()
							&& !runtime.efxOperational()
							&& !runtime.doubleWetPath()
							&& diagnostics.status()
								== OpenAlEfxController.Status.CONTEXT_FAILED
							&& diagnostics.contextRebuilds()
								== failed.get().contextRebuilds()
							&& diagnostics.faultInjectionCount()
								== failed.get().faultInjectionCount()) {
						stable.set(runtime);
						return true;
					}
					return false;
				}, MAXIMUM_WAIT_TICKS);

				context.runOnClient(client -> {
					System.clearProperty(
							OpenAlEfxController.FAULT_STAGE_PROPERTY
					);
					client.getSoundManager().reload();
				});
				AtomicReference<OpenAlEfxController.Diagnostics> recovered =
						new AtomicReference<>();
				AtomicReference<AcousticBackendRuntimeTelemetry.Snapshot>
						recoveryRuntime = new AtomicReference<>();
				context.waitFor(client -> {
					OpenAlEfxController.Diagnostics diagnostics =
							DroneSoundManager.openAlEfxDiagnostics();
					AcousticBackendRuntimeTelemetry.Snapshot runtime =
							DroneSoundManager
									.acousticBackendRuntimeDiagnostics();
					if (diagnostics.status()
								== OpenAlEfxController.Status.OPERATIONAL
							&& diagnostics.contextRebuilds()
								> failed.get().contextRebuilds()
							&& diagnostics.faultInjectionCount()
								== failed.get().faultInjectionCount()
							&& diagnostics.alErrorCode() == 0
							&& runtime.backend()
								== AcousticBackendSelector.RuntimeBackend
										.OPENAL_EFX
							&& !runtime.javaStreamActive()
							&& runtime.javaShadowActive()
							&& !runtime.javaWetActive()
							&& runtime.javaHistoryFrames()
								>= ListenerReverbHistoryProducer.TICK_FRAMES
							&& runtime.efxOperational()
							&& !runtime.doubleWetPath()
							&& !runtime.capturesAudio()) {
						recovered.set(diagnostics);
						recoveryRuntime.set(runtime);
						return true;
					}
					return false;
				}, MAXIMUM_WAIT_TICKS);
				cycles.add(new EfxFaultCycle(
						stage,
						before.get(),
						failed.get(),
						fallback.get(),
						stable.get(),
						recovered.get(),
						recoveryRuntime.get()
				));
			}
			context.waitFor(client -> {
				AcousticBackendRuntimeTelemetry.Snapshot runtime =
						DroneSoundManager
								.acousticBackendRuntimeDiagnostics();
				return runtime.backend()
								== AcousticBackendSelector.RuntimeBackend
										.OPENAL_EFX
						&& runtime.javaShadowActive()
						&& !runtime.javaWetActive()
						&& runtime.javaHistoryFrames()
								== ListenerReverbHistoryProducer
										.HISTORY_FRAMES;
			}, MAXIMUM_WAIT_TICKS);
			ListenerReverbQueueHandoffProbe.Snapshot handoff =
					ListenerReverbQueueHandoffProbe.finish();
			handoffFinished = true;
			handoffSnapshot = handoff;
		} finally {
			System.clearProperty(OpenAlEfxController.FAULT_STAGE_PROPERTY);
			if (!handoffFinished) {
				ListenerReverbQueueHandoffProbe.abort();
			}
		}

		String cycleJson = cycles.stream()
				.map(MinecraftDdaCaptureClientGameTest::faultCycleJson)
				.collect(java.util.stream.Collectors.joining(",\n    "));
		String json = String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": "
						+ "\"valid-openal-efx-fault-failover\",%n"
						+ "  \"development_environment_required\": true,%n"
						+ "  \"fault_property_cleared\": %s,%n"
						+ "  \"fault_cycles\": [%n    %s%n  ],%n"
						+ "  \"gates\": {"
						+ "\"all_three_stages_exercised\":%s,"
						+ "\"resources_cleaned_before_fallback\":true,"
						+ "\"java_fallback_next_tick\":true,"
						+ "\"same_context_retry_suppressed\":true,"
						+ "\"new_context_recovery\":true,"
						+ "\"double_wet_path_never_observed\":true},%n"
						+ "  \"physical_endpoint_changed\": false,%n"
						+ "  \"captures_audio\": false,%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"claim_boundary\": \"Development-only "
						+ "AL_INVALID_ENUM injection on Minecraft's real "
						+ "sound thread. It validates controller cleanup, "
						+ "Java fallback, same-context failure latching, and "
						+ "SoundManager-reload recovery; it is not a physical "
						+ "device or driver failure.\"%n"
						+ "}%n",
				System.getProperty(
						OpenAlEfxController.FAULT_STAGE_PROPERTY
				) == null,
				cycleJson,
				cycles.size() == 3
		);
		Path output = requiredOpenAlFailoverOutputPath();
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(output, json, StandardCharsets.UTF_8);
			writeListenerHandoffEvidence(
					handoffSnapshot,
					sha256Hex(Files.readAllBytes(output))
			);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write EFX fault failover evidence",
					error
			);
		}
	}

	private static void writeListenerHandoffEvidence(
			ListenerReverbQueueHandoffProbe.Snapshot snapshot,
			String faultFailoverSha256
	) throws java.io.IOException {
		if (snapshot == null) {
			throw new IllegalStateException(
					"missing listener handoff snapshot"
			);
		}
		Path d107Report = requiredPropertyPath(
				LISTENER_HANDOFF_D107_REPORT_PROPERTY
		);
		String d107Sha256 = sha256Hex(Files.readAllBytes(d107Report));
		String cycles = snapshot.cycles().stream()
				.map(MinecraftDdaCaptureClientGameTest
						::listenerHandoffCycleJson)
				.collect(java.util.stream.Collectors.joining(",\n    "));
		String json = String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": "
						+ "\"valid-listener-reverb-queue-handoff\",%n"
						+ "  \"sample_rate_hz\": 48000,%n"
						+ "  \"history_frames\": 24000,%n"
						+ "  \"minecraft_stream_buffer_seconds\": 1,%n"
						+ "  \"minecraft_initial_queue_buffers\": 4,%n"
						+ "  \"source_d107_report_sha256\": \"%s\",%n"
						+ "  \"source_fault_failover_report_sha256\": "
						+ "\"%s\",%n"
						+ "  \"thresholds\": {"
						+ "\"maximum_first_queue_latency_ms\":"
						+ "42.666666666666664,"
						+ "\"maximum_initial_queue_fill_ms\":50.0,"
						+ "\"maximum_preroll_ms\":"
						+ "10.666666666666666},%n"
						+ "  \"cycles\": [%n    %s%n  ],%n"
						+ "  \"openal_shadow_source_created\": false,%n"
						+ "  \"active_source_restarted\": true,%n"
						+ "  \"exclusive_wet_owner\": true,%n"
						+ "  \"client_gametest_measured\": true,%n"
						+ "  \"physical_endpoint_opened\": true,%n"
						+ "  \"physical_output_captured\": false,%n"
						+ "  \"captures_audio\": false,%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"claim_boundary\": \"Real Minecraft sound-thread "
						+ "source and buffer queue evidence after controlled "
						+ "EFX faults on Minecraft's live OpenAL output "
						+ "device. It does not read back or record rendered "
						+ "endpoint audio and does not prove audibility.\"%n"
						+ "}%n",
				d107Sha256,
				faultFailoverSha256,
				cycles
		);
		Path output = requiredPropertyPath(
				LISTENER_HANDOFF_OUTPUT_PROPERTY
		);
		Files.createDirectories(output.getParent());
		Files.writeString(output, json, StandardCharsets.UTF_8);
	}

	private static String listenerHandoffCycleJson(
			ListenerReverbQueueHandoffProbe.Cycle cycle
	) {
		String queued = cycle.queued().stream()
				.map(MinecraftDdaCaptureClientGameTest
						::listenerHandoffQueueJson)
				.collect(java.util.stream.Collectors.joining(","));
		return String.format(
				Locale.ROOT,
				"{\"stage\":\"%s\",\"armed_nanos\":%d,"
						+ "\"history_frames_at_arm\":%d,"
						+ "\"fallback_requested_nanos\":%d,"
						+ "\"history_frames_at_fallback\":%d,"
						+ "\"synthesizers_at_fallback\":%d,"
						+ "\"shadow_active_at_fallback\":%s,"
						+ "\"wet_active_at_fallback\":%s,"
						+ "\"queued\":[%s],"
						+ "\"shadow_restart_nanos\":%d,"
						+ "\"history_frames_at_shadow_restart\":%d,"
						+ "\"synthesizers_at_shadow_restart\":%d,"
						+ "\"history_ready_nanos\":%d}",
				cycle.stage(),
				cycle.armedNanos(),
				cycle.historyFramesAtArm(),
				cycle.fallbackRequestedNanos(),
				cycle.historyFramesAtFallback(),
				cycle.synthesizersAtFallback(),
				cycle.shadowActiveAtFallback(),
				cycle.wetActiveAtFallback(),
				queued,
				cycle.shadowRestartNanos(),
				cycle.historyFramesAtShadowRestart(),
				cycle.synthesizersAtShadowRestart(),
				cycle.historyReadyNanos()
		);
	}

	private static String listenerHandoffQueueJson(
			ListenerReverbQueueHandoffProbe.QueueEvent event
	) {
		return String.format(
				Locale.ROOT,
				"{\"queue_index\":%d,\"stream_sequence\":%d,"
						+ "\"source\":%d,\"buffer\":%d,"
						+ "\"queued_nanos\":%d,"
						+ "\"fallback_to_queue_nanos\":%d,"
						+ "\"pcm_bytes\":%d,\"pcm_sha256\":\"%s\","
						+ "\"first_nonzero_frame\":%d,"
						+ "\"preroll_frames\":%d,"
						+ "\"preroll_nanos\":%d,"
						+ "\"buffer_frequency\":%d,"
						+ "\"buffer_bits\":%d,"
						+ "\"buffer_channels\":%d,"
						+ "\"buffer_bytes\":%d,"
						+ "\"source_state\":%d,"
						+ "\"source_type\":%d,"
						+ "\"buffers_queued\":%d,"
						+ "\"buffers_processed\":%d,"
						+ "\"sample_offset\":%d}",
				event.queueIndex(),
				event.streamSequence(),
				event.source(),
				event.buffer(),
				event.queuedNanos(),
				event.fallbackToQueueNanos(),
				event.pcmBytes(),
				event.pcmSha256(),
				event.firstNonZeroFrame(),
				event.prerollFrames(),
				event.prerollNanos(),
				event.bufferFrequency(),
				event.bufferBits(),
				event.bufferChannels(),
				event.bufferBytes(),
				event.sourceState(),
				event.sourceType(),
				event.buffersQueued(),
				event.buffersProcessed(),
				event.sampleOffset()
		);
	}

	private static String faultCycleJson(EfxFaultCycle cycle) {
		return String.format(
				Locale.ROOT,
				"{\"stage\":\"%s\",\"before\":%s,\"failed\":%s,"
						+ "\"fallback\":%s,\"same_context_stable\":%s,"
						+ "\"recovered\":%s,\"recovery_runtime\":%s}",
				cycle.stage().token(),
				diagnosticsJson(cycle.before()),
				diagnosticsJson(cycle.failed()),
				runtimeTelemetryJson(cycle.fallback()),
				runtimeTelemetryJson(cycle.sameContextStable()),
				diagnosticsJson(cycle.recovered()),
				runtimeTelemetryJson(cycle.recoveryRuntime())
		);
	}

	private static String runtimeTelemetryJson(
			AcousticBackendRuntimeTelemetry.Snapshot snapshot
	) {
		return String.format(
				Locale.ROOT,
				"{\"sequence\":%d,\"backend\":\"%s\","
						+ "\"efx_status\":\"%s\","
						+ "\"environment_generation\":%d,"
						+ "\"context_rebuilds\":%d,"
						+ "\"al_error_code\":%d,"
						+ "\"last_fault_stage\":\"%s\","
						+ "\"fault_injection_count\":%d,"
						+ "\"java_stream_active\":%s,"
						+ "\"java_shadow_active\":%s,"
						+ "\"java_wet_active\":%s,"
						+ "\"java_history_frames\":%d,"
						+ "\"efx_operational\":%s,"
						+ "\"double_wet_path\":%s,"
						+ "\"captures_audio\":%s}",
				snapshot.sequence(),
				snapshot.backend(),
				snapshot.efxStatus(),
				snapshot.environmentGeneration(),
				snapshot.contextRebuilds(),
				snapshot.alErrorCode(),
				snapshot.lastFaultStage().token(),
				snapshot.faultInjectionCount(),
				snapshot.javaStreamActive(),
				snapshot.javaShadowActive(),
				snapshot.javaWetActive(),
				snapshot.javaHistoryFrames(),
				snapshot.efxOperational(),
				snapshot.doubleWetPath(),
				snapshot.capturesAudio()
		);
	}

	private static void captureBackendCapabilityPolicyAndTimeline(
			ClientGameTestContext context
	) {
		AtomicReference<CompletableFuture<
				OpenAlNativeCapabilityProbe.Result>> pending =
				new AtomicReference<>();
		context.runOnClient(client -> pending.set(
				OpenAlNativeCapabilityProbe.capture(
						client.getSoundManager()
				)
		));
		context.waitFor(
				client -> pending.get() != null && pending.get().isDone(),
				MAXIMUM_WAIT_TICKS
		);
		OpenAlNativeCapabilityProbe.Result capability =
				pending.get().join();
		AcousticBackendCapabilityPolicyProbe.Result policy =
				AcousticBackendCapabilityPolicyProbe.evaluate(capability);
		if (!policy.actualNativeEfxEligible()) {
			throw new AssertionError(
					"current device lost its previously verified EFX capability"
			);
		}
		List<AcousticBackendRuntimeTelemetry.Event> timeline =
				DroneSoundManager.acousticBackendTimelineDiagnostics();
		if (timeline.isEmpty()) {
			throw new AssertionError("backend timeline is empty");
		}
		long previousSequence = 0L;
		long previousHostNanos = 0L;
		for (AcousticBackendRuntimeTelemetry.Event event : timeline) {
			if (event.telemetrySequence() <= previousSequence
					|| event.hostMonotonicNanos() <= previousHostNanos
					|| event.doubleWetPath()
					|| event.capturesAudio()) {
				throw new AssertionError(
						"backend timeline ordering/safety contract failed"
				);
			}
			previousSequence = event.telemetrySequence();
			previousHostNanos = event.hostMonotonicNanos();
		}
		for (OpenAlEfxController.FaultStage stage : List.of(
				OpenAlEfxController.FaultStage.RESOURCE_CREATE,
				OpenAlEfxController.FaultStage.PARAMETER_WRITE,
				OpenAlEfxController.FaultStage.SOURCE_ROUTE
		)) {
			boolean fallback = timeline.stream().anyMatch(event ->
					event.lastFaultStage() == stage
							&& event.backend()
								== AcousticBackendSelector.RuntimeBackend
										.JAVA_FDN
							&& event.efxStatus()
								== OpenAlEfxController.Status.CONTEXT_FAILED
			);
			boolean recovery = timeline.stream().anyMatch(event ->
					event.lastFaultStage() == stage
							&& event.backend()
								== AcousticBackendSelector.RuntimeBackend
										.OPENAL_EFX
							&& event.efxStatus()
								== OpenAlEfxController.Status.OPERATIONAL
			);
			if (!fallback || !recovery) {
				throw new AssertionError(
						"timeline missed fault transition " + stage
				);
			}
		}

		byte[] capabilityBytes;
		byte[] failoverBytes;
		try {
			capabilityBytes = Files.readAllBytes(
					requiredOpenAlOutputPath()
			);
			failoverBytes = Files.readAllBytes(
					requiredOpenAlFailoverOutputPath()
			);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not bind capability/failover reports",
					error
			);
		}
		String timelineJson = timeline.stream()
				.map(MinecraftDdaCaptureClientGameTest::timelineEventJson)
				.collect(java.util.stream.Collectors.joining(",\n    "));
		String json = String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": "
						+ "\"valid-backend-capability-policy-timeline\",%n"
						+ "  \"capability_report_sha256\": \"%s\",%n"
						+ "  \"fault_failover_report_sha256\": \"%s\",%n"
						+ "  \"actual_device\": {"
						+ "\"efx_extension_supported\":%s,"
						+ "\"native_efx_eligible\":%s,"
						+ "\"maximum_auxiliary_sends\":%d},%n"
						+ "  \"policy_cases\": [%n    %s%n  ],%n"
						+ "  \"timeline_capacity\": 256,%n"
						+ "  \"timeline_events\": [%n    %s%n  ],%n"
						+ "  \"gates\": {"
						+ "\"actual_capability_hash_bound\":true,"
						+ "\"counterfactual_cases_marked_simulated\":true,"
						+ "\"all_fault_fallbacks_and_recoveries_present\":true,"
						+ "\"timeline_monotonic\":true,"
						+ "\"double_wet_path_never_observed\":true},%n"
						+ "  \"no_efx_hardware_exercised\": false,%n"
						+ "  \"policy_simulation_exercised\": true,%n"
						+ "  \"physical_endpoint_changed\": false,%n"
						+ "  \"captures_audio\": false,%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"claim_boundary\": \"The active device "
						+ "capability is measured on Minecraft's sound thread. "
						+ "Unsupported-EFX routes are explicitly marked policy "
						+ "simulations. The transition timeline contains "
						+ "metadata clocks and controls only, not PCM or a "
						+ "physical endpoint capture.\"%n"
						+ "}%n",
				sha256Hex(capabilityBytes),
				sha256Hex(failoverBytes),
				policy.actualEfxExtensionSupported(),
				policy.actualNativeEfxEligible(),
				policy.actualMaximumAuxiliarySends(),
				policy.casesJson(),
				timelineJson
		);
		Path output = requiredBackendCapabilityPolicyOutputPath();
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(output, json, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write capability policy timeline",
					error
			);
		}
	}

	private static String timelineEventJson(
			AcousticBackendRuntimeTelemetry.Event event
	) {
		return String.format(
				Locale.ROOT,
				"{\"telemetry_sequence\":%d,"
						+ "\"host_monotonic_nanos\":%d,"
						+ "\"minecraft_tick\":%d,"
						+ "\"backend\":\"%s\","
						+ "\"efx_status\":\"%s\","
						+ "\"environment_generation\":%d,"
						+ "\"rt60_seconds\":{\"low\":%.17g,"
						+ "\"mid\":%.17g,\"high\":%.17g},"
						+ "\"wet_gain\":%.17g,"
						+ "\"transition_seconds\":%.17g,"
						+ "\"context_rebuilds\":%d,"
						+ "\"al_error_code\":%d,"
						+ "\"last_fault_stage\":\"%s\","
						+ "\"fault_injection_count\":%d,"
						+ "\"java_stream_active\":%s,"
						+ "\"java_shadow_active\":%s,"
						+ "\"java_wet_active\":%s,"
						+ "\"java_history_frames\":%d,"
						+ "\"efx_operational\":%s,"
						+ "\"double_wet_path\":%s,"
						+ "\"captures_audio\":%s}",
				event.telemetrySequence(),
				event.hostMonotonicNanos(),
				event.minecraftTick(),
				event.backend(),
				event.efxStatus(),
				event.environmentGeneration(),
				event.rt60Seconds().low(),
				event.rt60Seconds().mid(),
				event.rt60Seconds().high(),
				event.wetGain(),
				event.transitionSeconds(),
				event.contextRebuilds(),
				event.alErrorCode(),
				event.lastFaultStage().token(),
				event.faultInjectionCount(),
				event.javaStreamActive(),
				event.javaShadowActive(),
				event.javaWetActive(),
				event.javaHistoryFrames(),
				event.efxOperational(),
				event.doubleWetPath(),
				event.capturesAudio()
		);
	}

	private static void finishPersistentEfxEvidence(
			ClientGameTestContext context,
			TestSingleplayerContext singleplayer,
			OpenAlEfxController.Diagnostics active,
			OpenAlEfxController.Diagnostics reloaded
	) {
		if (active == null) {
			throw new AssertionError(
					"missing active persistent EFX diagnostics"
			);
		}
		if (reloaded == null) {
			throw new AssertionError(
					"missing post-reload persistent EFX diagnostics"
			);
		}
		singleplayer.getServer().runCommand(
				"kill @e[type=fpvdrone:drone]"
		);
		context.waitFor(
				client -> !hasAnyDrone(client),
				MAXIMUM_WAIT_TICKS
		);
		AtomicReference<OpenAlEfxController.Diagnostics> dormant =
				new AtomicReference<>();
		context.waitFor(client -> {
			OpenAlEfxController.Diagnostics diagnostics =
					DroneSoundManager.openAlEfxDiagnostics();
			if (!diagnostics.operational()
					&& !diagnostics.sharedResourcesCreated()
					&& diagnostics.attachedSources() == 0
					&& diagnostics.allocatedSourceFilters() == 0
					&& diagnostics.cleanupCount()
							> reloaded.cleanupCount()
					&& diagnostics.alErrorCode() == 0) {
				dormant.set(diagnostics);
				return true;
			}
			return false;
		}, MAXIMUM_WAIT_TICKS);
		OpenAlEfxController.Diagnostics cleaned = dormant.get();
		String json = String.format(
				Locale.ROOT,
				"{\n"
						+ "  \"schema_version\": 1,\n"
						+ "  \"status\": \"valid-controller-diagnostic\",\n"
						+ "  \"feature_property\": \"fpvdrone.openalEfx\",\n"
						+ "  \"feature_default\": false,\n"
						+ "  \"test_feature_enabled\": true,\n"
						+ "  \"test_java_reverb_enabled\": true,\n"
						+ "  \"java_wet_bus_suppressed\": true,\n"
						+ "  \"active\": %s,\n"
						+ "  \"after_sound_engine_reload\": %s,\n"
						+ "  \"after_source_removal\": %s,\n"
						+ "  \"sound_engine_reload_exercised\": true,\n"
						+ "  \"physical_device_switch_exercised\": false,\n"
						+ "  \"release_calibrated\": false,\n"
						+ "  \"claim_boundary\": \"Opt-in persistent shared "
						+ "EFX slot, Minecraft sound-engine context rebuild, "
						+ "and source-churn cleanup; physical device switch, "
						+ "audible calibration, and callback underrun remain "
						+ "unmeasured.\"\n"
						+ "}\n",
				diagnosticsJson(active),
				diagnosticsJson(reloaded),
				diagnosticsJson(cleaned)
		);
		Path output = requiredOpenAlControllerOutputPath();
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(output, json, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write persistent EFX controller evidence",
					error
			);
		}
	}

	private static void benchmarkMinecraftReverb(
			ClientGameTestContext context
	) {
		Path output = requiredReverbPerformanceOutputPath();
		AtomicReference<String> result = new AtomicReference<>();
		context.runOnClient(client -> {
			if (client.level == null || client.player == null) {
				return;
			}
			net.minecraft.world.phys.Vec3 eye =
					client.player.getEyePosition();
			AcousticVector listener = new AcousticVector(
					eye.x,
					eye.y,
					eye.z
			);
			for (int iteration = 0;
					iteration < PERFORMANCE_WARMUP_ITERATIONS;
					iteration++) {
				ReflectionVolume volume = MinecraftReflectionSnapshot.capture(
						client.level,
						listener,
						6,
						4,
						iteration
				);
				VoxelReflectionProbe.analyze(
						listener,
						volume,
						REVERB_CONFIG
				);
			}
			long[] captureNanos =
					new long[PERFORMANCE_MEASURED_ITERATIONS];
			long[] probeNanos =
					new long[PERFORMANCE_MEASURED_ITERATIONS];
			int incompleteSnapshots = 0;
			for (int iteration = 0;
					iteration < PERFORMANCE_MEASURED_ITERATIONS;
					iteration++) {
				long start = System.nanoTime();
				ReflectionVolume volume = MinecraftReflectionSnapshot.capture(
						client.level,
						listener,
						6,
						4,
						iteration
				);
				captureNanos[iteration] = System.nanoTime() - start;
				if (!volume.complete()) {
					incompleteSnapshots++;
				}
				start = System.nanoTime();
				VoxelReflectionProbe.analyze(
						listener,
						volume,
						REVERB_CONFIG
				);
				probeNanos[iteration] = System.nanoTime() - start;
			}
			Arrays.sort(captureNanos);
			Arrays.sort(probeNanos);
			double captureP99 = percentile(captureNanos, 0.99);
			double probeP99 = percentile(probeNanos, 0.99);
			result.set(String.format(
					Locale.ROOT,
					"{\n"
							+ "  \"schema_version\": 1,\n"
							+ "  \"status\": \"valid-benchmark\",\n"
							+ "  \"source\": \"integrated-client-world\",\n"
							+ "  \"snapshot_cells\": 1521,\n"
							+ "  \"ray_count\": %d,\n"
							+ "  \"maximum_bounces\": %d,\n"
							+ "  \"warmup_iterations\": %d,\n"
							+ "  \"measured_iterations\": %d,\n"
							+ "  \"incomplete_snapshots\": %d,\n"
							+ "  \"capture_p50_ms\": %.17g,\n"
							+ "  \"capture_p95_ms\": %.17g,\n"
							+ "  \"capture_p99_ms\": %.17g,\n"
							+ "  \"probe_p50_ms\": %.17g,\n"
							+ "  \"probe_p95_ms\": %.17g,\n"
							+ "  \"probe_p99_ms\": %.17g,\n"
							+ "  \"gates\": {"
							+ "\"snapshots_complete\":%s,"
							+ "\"capture_p99_at_most_4_ms\":%s,"
							+ "\"worker_probe_p99_at_most_2_ms\":%s},\n"
							+ "  \"release_calibrated\": false,\n"
							+ "  \"claim_boundary\": \"Capture runs on the "
							+ "integrated client thread; probe timing is the "
							+ "same algorithm but measured synchronously by "
							+ "the test, not worker scheduling latency.\"\n"
							+ "}\n",
					REVERB_CONFIG.rayCount(),
					REVERB_CONFIG.maximumBounces(),
					PERFORMANCE_WARMUP_ITERATIONS,
					PERFORMANCE_MEASURED_ITERATIONS,
					incompleteSnapshots,
					percentile(captureNanos, 0.50),
					percentile(captureNanos, 0.95),
					captureP99,
					percentile(probeNanos, 0.50),
					percentile(probeNanos, 0.95),
					probeP99,
					incompleteSnapshots == 0,
					captureP99 <= 4.0,
					probeP99 <= 2.0
			));
		});
		context.waitFor(
				client -> result.get() != null,
				MAXIMUM_WAIT_TICKS
		);
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(
					output,
					result.get(),
					StandardCharsets.UTF_8
			);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write reverb performance evidence",
					error
			);
		}
	}

	private static double percentile(long[] sortedNanos, double quantile) {
		int index = (int) Math.ceil(quantile * sortedNanos.length) - 1;
		return sortedNanos[
				Math.max(0, Math.min(sortedNanos.length - 1, index))
		] / 1_000_000.0;
	}

	private static void verifyMinecraftReverbSnapshots(
			ClientGameTestContext context,
			TestSingleplayerContext singleplayer
	) {
		Path output = requiredReverbOutputPath();
		try {
			Files.deleteIfExists(output);
		} catch (java.io.IOException error) {
			throw new IllegalStateException(
					"could not clear preceding reverb client capture",
					error
			);
		}
		singleplayer.getServer().runCommand(
				"execute as @p at @s run fill "
						+ "~-6 ~-3 ~-6 ~6 ~5 ~6 minecraft:air"
		);
		singleplayer.getServer().runCommand(
				"execute as @p at @s run fill "
						+ "~-6 ~-3 ~-6 ~6 ~5 ~6 minecraft:stone hollow"
		);
		context.waitFor(
				client -> playerRoomBoundaryIs(
						client,
						"minecraft:stone"
				),
				MAXIMUM_WAIT_TICKS
		);
		LateReverbEstimator.Parameters stone = analyzeCurrentEnvironment(
				context
		);

		singleplayer.getServer().runCommand(
				"execute as @p at @s run fill "
						+ "~-6 ~-3 ~-6 ~6 ~5 ~6 minecraft:air"
		);
		singleplayer.getServer().runCommand(
				"execute as @p at @s run fill "
						+ "~-6 ~-3 ~-6 ~6 ~5 ~6 minecraft:white_wool hollow"
		);
		context.waitFor(
				client -> playerRoomBoundaryIs(
						client,
						"minecraft:white_wool"
				),
				MAXIMUM_WAIT_TICKS
		);
		LateReverbEstimator.Parameters soft = analyzeCurrentEnvironment(
				context
		);

		if (!(stone.rt60Seconds().low() > stone.rt60Seconds().mid()
				&& stone.rt60Seconds().mid()
						> stone.rt60Seconds().high())) {
			throw new AssertionError(
					"Minecraft stone room must decay low > mid > high"
			);
		}
		if (!(stone.rt60Seconds().low() > soft.rt60Seconds().low()
				&& stone.rt60Seconds().mid() > soft.rt60Seconds().mid()
				&& stone.rt60Seconds().high()
						> soft.rt60Seconds().high())) {
			throw new AssertionError(
					"Minecraft stone room must decay longer than wool"
			);
		}
		if (stone.openness() != 0.0 || soft.openness() != 0.0) {
			throw new AssertionError(
					"closed Minecraft rooms must not report open rays"
			);
		}
		String json = String.format(
				Locale.ROOT,
				"{\n"
						+ "  \"schema_version\": 1,\n"
						+ "  \"status\": \"valid\",\n"
						+ "  \"source\": \"integrated-client-world\",\n"
						+ "  \"release_calibrated\": false,\n"
						+ "  \"ray_count\": %d,\n"
						+ "  \"maximum_bounces\": %d,\n"
						+ "  \"stone_rt60_s\": [%.17g, %.17g, %.17g],\n"
						+ "  \"wool_rt60_s\": [%.17g, %.17g, %.17g],\n"
						+ "  \"stone_openness\": %.17g,\n"
						+ "  \"wool_openness\": %.17g\n"
						+ "}\n",
				REVERB_CONFIG.rayCount(),
				REVERB_CONFIG.maximumBounces(),
				stone.rt60Seconds().low(),
				stone.rt60Seconds().mid(),
				stone.rt60Seconds().high(),
				soft.rt60Seconds().low(),
				soft.rt60Seconds().mid(),
				soft.rt60Seconds().high(),
				stone.openness(),
				soft.openness()
		);
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(output, json, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write reverb client evidence",
					error
			);
		}
	}

	private static PortalEnvironments
			verifyMinecraftPortalSnapshots(
			ClientGameTestContext context,
			TestSingleplayerContext singleplayer
	) {
		Path output = requiredPortalOutputPath();
		AtomicReference<net.minecraft.core.BlockPos> anchor =
				new AtomicReference<>();
		context.runOnClient(client -> {
			if (client.player != null) {
				anchor.set(client.player.blockPosition());
			}
		});
		context.waitFor(
				client -> anchor.get() != null,
				MAXIMUM_WAIT_TICKS
		);
		try {
			Files.deleteIfExists(output);
		} catch (java.io.IOException error) {
			throw new IllegalStateException(
					"could not clear preceding portal client capture",
					error
			);
		}
		singleplayer.getServer().runCommand(
				"execute as @p at @s run fill "
						+ "~-6 ~-4 ~-6 ~16 ~6 ~6 minecraft:air"
		);
		singleplayer.getServer().runCommand(
				"execute as @p at @s run fill "
						+ "~-5 ~-3 ~-5 ~5 ~5 ~5 minecraft:stone hollow"
		);
		singleplayer.getServer().runCommand(
				"execute as @p at @s run fill "
						+ "~5 ~-3 ~-5 ~15 ~5 ~5 minecraft:white_wool hollow"
		);
		singleplayer.getServer().runCommand(
				"execute as @p at @s run fill "
						+ "~5 ~-3 ~-5 ~5 ~5 ~5 minecraft:glass"
		);
		context.waitFor(
				client -> portalApertureIs(
						client,
						anchor.get(),
						"minecraft:glass"
				),
				MAXIMUM_WAIT_TICKS
		);
		PortalSnapshot closed =
				analyzePortalEnvironment(context, anchor.get(), 0);

		singleplayer.getServer().runCommand(
				"execute as @p at @s run fill "
						+ "~5 ~0 ~0 ~5 ~1 ~1 minecraft:air"
		);
		context.waitFor(
				client -> partialPortalApertureReady(
						client,
						anchor.get()
				),
				MAXIMUM_WAIT_TICKS
		);
		PortalSnapshot partial =
				analyzePortalEnvironment(context, anchor.get(), 4);

		singleplayer.getServer().runCommand(
				"execute as @p at @s run fill "
						+ "~5 ~-1 ~-1 ~5 ~2 ~1 minecraft:air"
		);
		context.waitFor(
				client -> portalApertureIs(
						client,
						anchor.get(),
						"minecraft:air"
				),
				MAXIMUM_WAIT_TICKS
		);
		PortalSnapshot open =
				analyzePortalEnvironment(context, anchor.get(), 12);

		singleplayer.getServer().runCommand(
				"execute as @p at @s run fill "
						+ "~5 ~-1 ~-1 ~5 ~2 ~1 minecraft:glass"
		);
		context.waitFor(
				client -> portalApertureIs(
						client,
						anchor.get(),
						"minecraft:glass"
				),
				MAXIMUM_WAIT_TICKS
		);
		PortalSnapshot closedAgain =
				analyzePortalEnvironment(context, anchor.get(), 0);

		if (closed.roomBHits() != 0 || closedAgain.roomBHits() != 0) {
			throw new AssertionError(
					"closed Minecraft divider leaked into the wool room"
			);
		}
		if (open.roomBHits() <= 0 || open.enteredRoomBRays() <= 0
				|| open.returnedToRoomARays() <= 0) {
			throw new AssertionError(
					"open Minecraft portal did not transport and return rays"
			);
		}
		if (closed.parameters().openness() != 0.0
				|| open.parameters().openness() != 0.0
				|| closedAgain.parameters().openness() != 0.0) {
			throw new AssertionError(
					"closed compound Minecraft volume reported escape"
			);
		}
		if (!(open.parameters().rt60Seconds().mid()
				< closed.parameters().rt60Seconds().mid())) {
			throw new AssertionError(
					"soft adjacent room did not reduce mid RT60"
			);
		}
		if (!samePortalEndpoint(closed, closedAgain)) {
			throw new AssertionError(
					"closed-open-closed Minecraft snapshots did not return "
							+ "to the same deterministic endpoint"
			);
		}

		FdnEnvironmentMapper.Controls closedControls =
				FdnEnvironmentMapper.map(closed.parameters());
		FdnEnvironmentMapper.Controls partialControls =
				FdnEnvironmentMapper.map(partial.parameters());
		FdnEnvironmentMapper.Controls openControls =
				FdnEnvironmentMapper.map(open.parameters());
		if (!(closedControls.rt60Seconds().mid()
				> partialControls.rt60Seconds().mid()
				&& partialControls.rt60Seconds().mid()
				> openControls.rt60Seconds().mid())) {
			throw new AssertionError(
					"partial portal controls are not ordered by mid RT60"
			);
		}
		String json = String.format(
				Locale.ROOT,
				"{\n"
						+ "  \"schema_version\": 1,\n"
						+ "  \"status\": \"valid-diagnostic\",\n"
						+ "  \"source\": \"integrated-client-world\",\n"
						+ "  \"sequence\": [\"closed\",\"open\",\"closed\"],\n"
						+ "  \"portal_aperture_cells\": 12,\n"
						+ "  \"ray_count\": 4096,\n"
						+ "  \"maximum_bounces\": 48,\n"
						+ "  \"closed_room_b_hits\": %d,\n"
						+ "  \"open_room_b_hits\": %d,\n"
						+ "  \"open_rays_entering_room_b\": %d,\n"
						+ "  \"open_rays_returning_room_a\": %d,\n"
						+ "  \"closed_mid_rt60_s\": %.17g,\n"
						+ "  \"open_mid_rt60_s\": %.17g,\n"
						+ "  \"closed_wet_gain\": %.17g,\n"
						+ "  \"open_wet_gain\": %.17g,\n"
						+ "  \"closed_openness\": %.17g,\n"
						+ "  \"open_openness\": %.17g,\n"
						+ "  \"backend_matrix_environments\": [\n"
						+ "    {\"name\":\"closed\","
						+ "\"aperture_cells\":0,"
						+ "\"rt60_seconds\":{\"low\":%.17g,"
						+ "\"mid\":%.17g,\"high\":%.17g},"
						+ "\"wet_gain\":%.17g},\n"
						+ "    {\"name\":\"partial\","
						+ "\"aperture_cells\":4,"
						+ "\"rt60_seconds\":{\"low\":%.17g,"
						+ "\"mid\":%.17g,\"high\":%.17g},"
						+ "\"wet_gain\":%.17g},\n"
						+ "    {\"name\":\"open\","
						+ "\"aperture_cells\":12,"
						+ "\"rt60_seconds\":{\"low\":%.17g,"
						+ "\"mid\":%.17g,\"high\":%.17g},"
						+ "\"wet_gain\":%.17g}\n"
						+ "  ],\n"
						+ "  \"closed_endpoint_repeatable\": true,\n"
						+ "  \"release_calibrated\": false,\n"
						+ "  \"claim_boundary\": \"Real Minecraft block-state "
						+ "capture and material mapping; not measured coupled "
						+ "RIR, moving-door interpolation, or OpenAL scheduling.\"\n"
						+ "}\n",
				closed.roomBHits(),
				open.roomBHits(),
				open.enteredRoomBRays(),
				open.returnedToRoomARays(),
				closed.parameters().rt60Seconds().mid(),
				open.parameters().rt60Seconds().mid(),
				closedControls.wetGain(),
				openControls.wetGain(),
				closed.parameters().openness(),
				open.parameters().openness(),
				closedControls.rt60Seconds().low(),
				closedControls.rt60Seconds().mid(),
				closedControls.rt60Seconds().high(),
				closedControls.wetGain(),
				partialControls.rt60Seconds().low(),
				partialControls.rt60Seconds().mid(),
				partialControls.rt60Seconds().high(),
				partialControls.wetGain(),
				openControls.rt60Seconds().low(),
				openControls.rt60Seconds().mid(),
				openControls.rt60Seconds().high(),
				openControls.wetGain()
		);
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(output, json, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"could not write Minecraft portal evidence",
					error
			);
		}
		return new PortalEnvironments(
				closedControls,
				partialControls,
				openControls
		);
	}

	private static PortalSnapshot analyzePortalEnvironment(
			ClientGameTestContext context,
			net.minecraft.core.BlockPos anchor,
			int apertureCells
	) {
		AtomicReference<PortalSnapshot> result = new AtomicReference<>();
		context.runOnClient(client -> {
			if (client.level == null || client.player == null) {
				return;
			}
			AcousticVector listener = new AcousticVector(
					anchor.getX() + 0.5,
					anchor.getY() + 1.5,
					anchor.getZ() + 0.5
			);
			ReflectionVolume volume = MinecraftReflectionSnapshot.capture(
					client.level,
					listener,
					16,
					6,
					Math.max(0L, client.level.getGameTime())
			);
			PortalAudit audit = new PortalAudit();
			double mixingMfp = portalMeanFreePath(apertureCells);
			ReflectionStatistics statistics = VoxelReflectionProbe.analyze(
					listener,
					volume,
					new VoxelReflectionProbe.Config(
							4_096,
							48,
							192.0,
							512,
							1.0e-300
					),
					audit,
					(material, bounce, cumulativePathMeters) ->
							cumulativePathMeters >= 2.0 * mixingMfp
									? 1.0
									: material.scattering()
			);
			result.set(new PortalSnapshot(
					LateReverbEstimator.estimate(statistics, 343.0),
					audit.roomBHits,
					audit.enteredRoomBRays,
					audit.returnedToRoomARays
			));
		});
		context.waitFor(
				client -> result.get() != null,
				MAXIMUM_WAIT_TICKS
		);
		return result.get();
	}

	private static double portalMeanFreePath(int apertureCells) {
		double volume = 2.0 * 9.0 * 9.0 * 7.0;
		double externalSurface = 2.0 * (
				18.0 * 9.0 + 18.0 * 7.0 + 9.0 * 7.0
		);
		double dividerTwoSided = 2.0 * (9.0 * 7.0 - apertureCells);
		return 4.0 * volume / (externalSurface + dividerTwoSided);
	}

	private static boolean samePortalEndpoint(
			PortalSnapshot left,
			PortalSnapshot right
	) {
		return left.roomBHits() == right.roomBHits()
				&& left.enteredRoomBRays() == right.enteredRoomBRays()
				&& left.returnedToRoomARays() == right.returnedToRoomARays()
				&& left.parameters().rt60Seconds().equals(
						right.parameters().rt60Seconds()
				)
				&& left.parameters().edtSeconds().equals(
						right.parameters().edtSeconds()
				)
				&& left.parameters().openness()
						== right.parameters().openness();
	}

	private static boolean portalApertureIs(
			Minecraft client,
			net.minecraft.core.BlockPos anchor,
			String expectedBlockId
	) {
		if (client.level == null || client.player == null) {
			return false;
		}
		for (int y = -1; y <= 2; y++) {
			for (int z = -1; z <= 1; z++) {
				net.minecraft.core.BlockPos portal =
						anchor.offset(5, y, z);
				String actual =
						net.minecraft.core.registries.BuiltInRegistries.BLOCK
								.getKey(
										client.level.getBlockState(portal)
												.getBlock()
								)
								.toString();
				if (!actual.equals(expectedBlockId)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean partialPortalApertureReady(
			Minecraft client,
			net.minecraft.core.BlockPos anchor
	) {
		if (client.level == null || client.player == null) {
			return false;
		}
		for (int y = -1; y <= 2; y++) {
			for (int z = -1; z <= 1; z++) {
				String expected = y >= 0 && y <= 1 && z >= 0
						? "minecraft:air"
						: "minecraft:glass";
				net.minecraft.core.BlockPos portal =
						anchor.offset(5, y, z);
				String actual =
						net.minecraft.core.registries.BuiltInRegistries.BLOCK
								.getKey(
										client.level.getBlockState(portal)
												.getBlock()
								)
								.toString();
				if (!actual.equals(expected)) {
					return false;
				}
			}
		}
		return true;
	}

	private static final class PortalAudit
			implements VoxelReflectionProbe.SurfaceHitObserver {
		private final boolean[] enteredRoomB = new boolean[4_096];
		private final boolean[] returnedRoomA = new boolean[4_096];
		private int roomBHits;
		private int enteredRoomBRays;
		private int returnedToRoomARays;

		@Override
		public void onHit(
				int rayIndex,
				int bounce,
				AcousticMaterial material,
				AcousticVector normal,
				double distanceMeters
		) {
			if (material.equals(AcousticMaterials.SOFT)) {
				roomBHits++;
				if (!enteredRoomB[rayIndex]) {
					enteredRoomB[rayIndex] = true;
					enteredRoomBRays++;
				}
			} else if (material.equals(AcousticMaterials.STONE)
					&& enteredRoomB[rayIndex]
					&& !returnedRoomA[rayIndex]) {
				returnedRoomA[rayIndex] = true;
				returnedToRoomARays++;
			}
		}
	}

	private record PortalSnapshot(
			LateReverbEstimator.Parameters parameters,
			int roomBHits,
			int enteredRoomBRays,
			int returnedToRoomARays
	) {
	}

	private record PortalEnvironments(
			FdnEnvironmentMapper.Controls closed,
			FdnEnvironmentMapper.Controls partial,
			FdnEnvironmentMapper.Controls open
	) {
	}

	private record BackendMatrixEnvironment(
			String name,
			FdnEnvironmentMapper.Controls controls,
			OpenAlBackendTransferProbe.Result result
	) {
	}

	private record ServerBlackboxEvidence(
			int serverEntityId,
			String csv
	) {
	}

	private record BlackboxStats(
			int rows,
			int columns,
			long minimumTick,
			long maximumTick,
			double maximumAverageRpm
	) {
	}

	private static boolean playerRoomBoundaryIs(
			Minecraft client,
			String expectedBlockId
	) {
		if (client.level == null || client.player == null) {
			return false;
		}
		net.minecraft.core.BlockPos player = client.player.blockPosition();
		net.minecraft.core.BlockPos boundary = player.offset(6, 0, 0);
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK
				.getKey(client.level.getBlockState(boundary).getBlock())
				.toString()
				.equals(expectedBlockId);
	}

	private static LateReverbEstimator.Parameters analyzeCurrentEnvironment(
			ClientGameTestContext context
	) {
		AtomicReference<LateReverbEstimator.Parameters> result =
				new AtomicReference<>();
		context.runOnClient(client -> {
			if (client.level == null || client.player == null) {
				return;
			}
			net.minecraft.world.phys.Vec3 eye =
					client.player.getEyePosition();
			AcousticVector listener = new AcousticVector(
					eye.x,
					eye.y,
					eye.z
			);
			ReflectionVolume volume = MinecraftReflectionSnapshot.capture(
					client.level,
					listener,
					6,
					4,
					Math.max(0L, client.level.getGameTime())
			);
			ReflectionStatistics statistics =
					VoxelReflectionProbe.analyze(
							listener,
							volume,
							REVERB_CONFIG
					);
			result.set(LateReverbEstimator.estimate(statistics, 343.0));
		});
		context.waitFor(
				client -> result.get() != null,
				MAXIMUM_WAIT_TICKS
		);
		return result.get();
	}

	private static boolean hasAudibleDrone(Minecraft client) {
		if (client.level == null) {
			return false;
		}
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof DroneEntity drone
					&& drone.getAverageMotorRpm() > 1_000.0) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasAnyDrone(Minecraft client) {
		if (client.level == null) {
			return false;
		}
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof DroneEntity) {
				return true;
			}
		}
		return false;
	}

	private static void verifyCapture(Path output) {
		try {
			DdaProductionSnapshotBundle.Bundle bundle =
					DdaProductionSnapshotBundle.read(output);
			if (!bundle.snapshot().complete()) {
				throw new AssertionError(
						"client DDA capture snapshot must be complete"
				);
			}
			if (bundle.rays().isEmpty()) {
				throw new AssertionError(
						"client DDA capture must contain acoustic rays"
				);
			}
			Set<AcousticMaterial> capturedMaterials =
					bundle.snapshot().diagnosticEntries()
					.stream()
					.map(cell -> cell.sample().material())
					.collect(java.util.stream.Collectors.toUnmodifiableSet());
			for (AcousticMaterial expected : Set.of(
					AcousticMaterials.STONE,
					AcousticMaterials.GLASS,
					AcousticMaterials.WOOD,
					AcousticMaterials.WATER,
					AcousticMaterials.FOLIAGE
			)) {
				if (!capturedMaterials.contains(expected)) {
					throw new AssertionError(
							"client DDA capture is missing material "
									+ expected.id()
					);
				}
			}
			double shortestRayMeters = bundle.rays().stream()
					.mapToDouble(ray -> ray.end()
							.subtract(ray.start())
							.length())
					.min()
					.orElseThrow();
			if (shortestRayMeters < 25.0) {
				throw new AssertionError(
						"client DDA capture ray is too short: "
								+ shortestRayMeters
				);
			}
			if (bundle.rays().stream().noneMatch(ray ->
					chunkCoordinate(ray.start().x())
							!= chunkCoordinate(ray.end().x())
							|| chunkCoordinate(ray.start().z())
							!= chunkCoordinate(ray.end().z())
			)) {
				throw new AssertionError(
						"client DDA capture must cross a chunk boundary"
				);
			}
			if (!"1.21.11".equals(
					bundle.metadata().minecraftVersion()
			)) {
				throw new AssertionError(
						"client DDA capture Minecraft version changed: "
								+ bundle.metadata().minecraftVersion()
				);
			}
		} catch (java.io.IOException error) {
			throw new AssertionError(
					"client DDA capture did not round-trip",
					error
			);
		}
	}

	private static int chunkCoordinate(double blockCoordinate) {
		return Math.floorDiv((int) Math.floor(blockCoordinate), 16);
	}

	private record EfxFaultCycle(
			OpenAlEfxController.FaultStage stage,
			OpenAlEfxController.Diagnostics before,
			OpenAlEfxController.Diagnostics failed,
			AcousticBackendRuntimeTelemetry.Snapshot fallback,
			AcousticBackendRuntimeTelemetry.Snapshot sameContextStable,
			OpenAlEfxController.Diagnostics recovered,
			AcousticBackendRuntimeTelemetry.Snapshot recoveryRuntime
	) {
	}
}
