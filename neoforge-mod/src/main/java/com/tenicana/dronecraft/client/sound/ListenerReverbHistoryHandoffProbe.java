package com.tenicana.dronecraft.client.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.TonalComponent;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * D107 deterministic production-path probe for shadow-history to wet-stream
 * ownership handoff. No OpenAL device or physical endpoint is opened.
 */
public final class ListenerReverbHistoryHandoffProbe {
	private static final int WARMUP_ITERATIONS = 10;
	private static final int MEASURED_ITERATIONS = 50;
	private static final int OUTPUT_FRAMES = 2_048;
	private static final int OUTPUT_BYTES = OUTPUT_FRAMES * Short.BYTES;
	private static final double MAXIMUM_SHADOW_P99_MS = 12.5;
	private static final double MAXIMUM_HANDOFF_P99_MS =
			(OUTPUT_FRAMES / 48_000.0) * 1_000.0 * 0.25;

	private ListenerReverbHistoryHandoffProbe() {
	}

	public static void main(String[] arguments) throws Exception {
		if (arguments.length != 3) {
			throw new IllegalArgumentException(
					"usage: ListenerReverbHistoryHandoffProbe "
							+ "<D106-json> <output-json> <output-pcm>"
			);
		}
		Locale.setDefault(Locale.ROOT);
		Path d106Path = Path.of(arguments[0]).toAbsolutePath().normalize();
		byte[] d106Bytes = Files.readAllBytes(d106Path);
		JsonObject d106 = JsonParser.parseString(
				new String(d106Bytes, StandardCharsets.UTF_8)
		).getAsJsonObject();
		validateD106(d106);
		List<Environment> environments = readEnvironments(d106);
		List<CaseResult> cases = new ArrayList<>();
		byte[] sidecar = new byte[environments.size() * OUTPUT_BYTES];
		int sidecarOffset = 0;
		for (Environment environment : environments) {
			CaseResult result = runCase(
					environment,
					sidecar,
					sidecarOffset
			);
			cases.add(result);
			sidecarOffset += OUTPUT_BYTES;
		}

		Path reportPath = Path.of(arguments[1])
				.toAbsolutePath().normalize();
		Path pcmPath = Path.of(arguments[2])
				.toAbsolutePath().normalize();
		Files.createDirectories(reportPath.getParent());
		Files.createDirectories(pcmPath.getParent());
		Files.write(pcmPath, sidecar);
		JsonObject report = report(
				sha256(d106Bytes),
				sha256(sidecar),
				environments,
				cases
		);
		Files.writeString(
				reportPath,
				new GsonBuilder().disableHtmlEscaping().create()
						.toJson(report),
				StandardCharsets.UTF_8
		);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-listener-reverb-history-handoff\","
						+ "\"cases\":%d,\"output\":\"%s\"}%n",
				cases.size(),
				reportPath
		);
	}

	private static CaseResult runCase(
			Environment environment,
			byte[] sidecar,
			int sidecarOffset
	) {
		ListenerReverbState state = configuredState(environment);
		List<ListenerReverbState.Source> sources = sources();
		state.publishSources(sources);
		for (int index = 0; index < WARMUP_ITERATIONS; index++) {
			state.advanceShadowHistory();
		}
		double[] shadowTimings = new double[MEASURED_ITERATIONS];
		for (int index = 0; index < MEASURED_ITERATIONS; index++) {
			long started = System.nanoTime();
			state.advanceShadowHistory();
			shadowTimings[index] = elapsedMilliseconds(started);
		}
		ListenerReverbHistoryProducer.Diagnostics before =
				state.historyDiagnostics();

		for (int index = 0; index < WARMUP_ITERATIONS; index++) {
			ListenerReverbAudioStream warmup =
					new ListenerReverbAudioStream(state);
			warmup.close();
		}
		double[] handoffTimings = new double[MEASURED_ITERATIONS];
		double[] fdnPrerollTimings = new double[MEASURED_ITERATIONS];
		for (int index = 0; index < MEASURED_ITERATIONS; index++) {
			long started = System.nanoTime();
			ListenerReverbAudioStream measured =
					new ListenerReverbAudioStream(state);
			handoffTimings[index] = elapsedMilliseconds(started);
			fdnPrerollTimings[index] =
					measured.prerollNanos() / 1_000_000.0;
			measured.close();
		}

		ListenerReverbAudioStream preceding =
				new ListenerReverbAudioStream(state);
		ListenerReverbAudioStream active =
				new ListenerReverbAudioStream(state);
		byte[] invalidatedPcm = bytes(preceding.read(OUTPUT_BYTES));
		byte[] activePcm = bytes(active.read(OUTPUT_BYTES));
		ListenerReverbHistoryProducer.Diagnostics during =
				state.historyDiagnostics();
		System.arraycopy(
				activePcm,
				0,
				sidecar,
				sidecarOffset,
				activePcm.length
		);
		int firstWetFrame = firstNonZeroFrame(activePcm);
		boolean invalidatedOwnerSilent =
				firstNonZeroFrame(invalidatedPcm) < 0;
		preceding.close();
		boolean closingOldOwnerPreservedReplacement =
				state.historyDiagnostics().wetActive();
		active.close();
		state.enterShadowHistoryMode();
		ListenerReverbHistoryProducer.Diagnostics restarted =
				state.historyDiagnostics();

		return new CaseResult(
				environment.name(),
				percentile(shadowTimings, 50.0),
				percentile(shadowTimings, 95.0),
				percentile(shadowTimings, 99.0),
				percentile(handoffTimings, 50.0),
				percentile(handoffTimings, 95.0),
				percentile(handoffTimings, 99.0),
				percentile(fdnPrerollTimings, 99.0),
				before.historyFrames(),
				active.prerollFrames(),
				before.synthesizerCount(),
				during.synthesizerCount(),
				firstWetFrame,
				invalidatedOwnerSilent,
				closingOldOwnerPreservedReplacement,
				restarted.shadowActive(),
				!restarted.wetActive(),
				restarted.historyFrames() == 0,
				restarted.synthesizerCount() == 0,
				sidecarOffset,
				activePcm.length,
				sha256(activePcm)
		);
	}

	private static ListenerReverbState configuredState(
			Environment environment
	) {
		ListenerReverbState state = new ListenerReverbState();
		state.publishEnvironment(new FdnEnvironmentMapper.Controls(
				1L,
				environment.rt60(),
				environment.wetGain(),
				0.2
		));
		return state;
	}

	private static List<ListenerReverbState.Source> sources() {
		AcousticEmissionFrame emission = new AcousticEmissionFrame(
				List.of(
						new TonalComponent(
								TonalComponent.Kind.ELECTRICAL,
								0,
								1,
								720.0,
								0.28,
								0.2
						),
						new TonalComponent(
								TonalComponent.Kind.BLADE_PASS,
								0,
								1,
								1_080.0,
								0.42,
								0.7
						)
				),
				new AcousticBands(0.012, 0.020, 0.028)
		);
		List<ListenerReverbState.Source> result = new ArrayList<>();
		for (int index = 0; index < 6; index++) {
			result.add(new ListenerReverbState.Source(
					100 + index,
					emission,
					0.18 + index * 0.02,
					0.24 + index * 0.02
			));
		}
		return List.copyOf(result);
	}

	private static void validateD106(JsonObject report) {
		if (report.get("schema_version").getAsInt() != 1
				|| !"valid-fdn-history-preroll-sweep".equals(
						report.get("status").getAsString()
				)
				|| report.get("sample_rate_hz").getAsInt() != 48_000
				|| report.get("selected_pre_roll_ms").getAsInt() != 500
				|| !report.get("selection_available").getAsBoolean()) {
			throw new IllegalArgumentException(
					"D106 report does not select a valid 500-ms history"
			);
		}
	}

	private static List<Environment> readEnvironments(JsonObject d106) {
		List<Environment> result = new ArrayList<>();
		for (var value : d106.getAsJsonArray("environments")) {
			JsonObject environment = value.getAsJsonObject();
			JsonObject rt60 = environment.getAsJsonObject(
					"rt60_seconds"
			);
			result.add(new Environment(
					environment.get("name").getAsString(),
					new AcousticBands(
							rt60.get("low").getAsDouble(),
							rt60.get("mid").getAsDouble(),
							rt60.get("high").getAsDouble()
					),
					environment.get("wet_gain").getAsDouble()
			));
		}
		if (!result.stream().map(Environment::name).toList().equals(
				List.of("closed", "partial", "open")
		)) {
			throw new IllegalArgumentException(
					"D106 environment order is not closed/partial/open"
			);
		}
		return List.copyOf(result);
	}

	private static JsonObject report(
			String d106Sha256,
			String sidecarSha256,
			List<Environment> environments,
			List<CaseResult> cases
	) {
		JsonObject report = new JsonObject();
		report.addProperty("schema_version", 1);
		report.addProperty(
				"status",
				"valid-listener-reverb-history-handoff"
		);
		report.addProperty("sample_rate_hz", 48_000);
		report.addProperty("minecraft_stream_buffer_seconds", 1);
		report.addProperty("minecraft_initial_queue_buffers", 4);
		report.addProperty("history_frames", 24_000);
		report.addProperty("history_milliseconds", 500);
		report.addProperty("shadow_tick_frames", 2_400);
		report.addProperty("source_count", 6);
		report.addProperty("warmup_iterations", WARMUP_ITERATIONS);
		report.addProperty("measured_iterations", MEASURED_ITERATIONS);
		report.addProperty(
				"source_d106_report_sha256",
				d106Sha256
		);
		report.addProperty("sidecar_sha256", sidecarSha256);
		report.addProperty("sidecar_format", "s16le-mono-48000");
		report.addProperty("sidecar_bytes", cases.size() * OUTPUT_BYTES);
		JsonObject thresholds = new JsonObject();
		thresholds.addProperty(
				"maximum_shadow_p99_ms",
				MAXIMUM_SHADOW_P99_MS
		);
		thresholds.addProperty(
				"maximum_handoff_p99_ms",
				MAXIMUM_HANDOFF_P99_MS
		);
		thresholds.addProperty("maximum_first_wet_frame", 0);
		report.add("thresholds", thresholds);
		JsonArray environmentJson = new JsonArray();
		for (Environment environment : environments) {
			JsonObject value = new JsonObject();
			value.addProperty("name", environment.name());
			JsonObject rt60 = new JsonObject();
			rt60.addProperty("low", environment.rt60().low());
			rt60.addProperty("mid", environment.rt60().mid());
			rt60.addProperty("high", environment.rt60().high());
			value.add("rt60_seconds", rt60);
			value.addProperty("wet_gain", environment.wetGain());
			environmentJson.add(value);
		}
		report.add("environments", environmentJson);
		JsonArray caseJson = new JsonArray();
		boolean allPassed = true;
		for (CaseResult result : cases) {
			JsonObject value = result.toJson();
			caseJson.add(value);
			allPassed &= value.get("passes").getAsBoolean();
		}
		report.add("cases", caseJson);
		report.addProperty("all_cases_passed", allPassed);
		report.addProperty("openal_shadow_source_created", false);
		report.addProperty("active_source_restart_required", true);
		report.addProperty("listener_shared_phase_owner", true);
		report.addProperty("exclusive_wet_owner", true);
		report.addProperty("captures_audio", false);
		report.addProperty("physical_endpoint_opened", false);
		report.addProperty("client_gametest_measured", false);
		report.addProperty("release_calibrated", false);
		report.addProperty(
				"claim_boundary",
				"Deterministic production Java history/handoff path only. "
						+ "Timing covers six-source shadow synthesis and "
						+ "fresh-stream FDN pre-roll on this host; it does not "
						+ "measure a Minecraft/OpenAL source restart, physical "
						+ "endpoint, audibility, or release calibration."
		);
		return report;
	}

	private static byte[] bytes(ByteBuffer buffer) {
		ByteBuffer copy = buffer.asReadOnlyBuffer();
		byte[] result = new byte[copy.remaining()];
		copy.get(result);
		return result;
	}

	private static int firstNonZeroFrame(byte[] pcm) {
		for (int frame = 0; frame < pcm.length / 2; frame++) {
			int low = pcm[frame * 2] & 0xff;
			int high = pcm[frame * 2 + 1];
			if ((short) (low | high << 8) != 0) {
				return frame;
			}
		}
		return -1;
	}

	private static double elapsedMilliseconds(long startedNanos) {
		return (System.nanoTime() - startedNanos) / 1_000_000.0;
	}

	private static double percentile(double[] values, double percentile) {
		double[] sorted = values.clone();
		Arrays.sort(sorted);
		double position = (percentile / 100.0) * (sorted.length - 1);
		int lower = (int) Math.floor(position);
		int upper = (int) Math.ceil(position);
		if (lower == upper) {
			return sorted[lower];
		}
		double fraction = position - lower;
		return sorted[lower] * (1.0 - fraction)
				+ sorted[upper] * fraction;
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (Exception error) {
			throw new IllegalStateException("SHA-256 unavailable", error);
		}
	}

	private record Environment(
			String name,
			AcousticBands rt60,
			double wetGain
	) {
	}

	private record CaseResult(
			String environment,
			double shadowP50Ms,
			double shadowP95Ms,
			double shadowP99Ms,
			double handoffP50Ms,
			double handoffP95Ms,
			double handoffP99Ms,
			double fdnPrerollP99Ms,
			int historyFramesBefore,
			int prerollFrames,
			int synthesizersBefore,
			int synthesizersDuring,
			int firstWetFrame,
			boolean invalidatedOwnerSilent,
			boolean closingOldOwnerPreservedReplacement,
			boolean shadowRestarted,
			boolean wetOwnerReleased,
			boolean historyClearedOnRestart,
			boolean phaseStateClearedOnRestart,
			int pcmOffset,
			int pcmBytes,
			String pcmSha256
	) {
		private JsonObject toJson() {
			JsonObject value = new JsonObject();
			value.addProperty("environment", environment);
			value.addProperty("shadow_p50_ms", shadowP50Ms);
			value.addProperty("shadow_p95_ms", shadowP95Ms);
			value.addProperty("shadow_p99_ms", shadowP99Ms);
			value.addProperty("handoff_p50_ms", handoffP50Ms);
			value.addProperty("handoff_p95_ms", handoffP95Ms);
			value.addProperty("handoff_p99_ms", handoffP99Ms);
			value.addProperty("fdn_preroll_p99_ms", fdnPrerollP99Ms);
			value.addProperty(
					"history_frames_before",
					historyFramesBefore
			);
			value.addProperty("preroll_frames", prerollFrames);
			value.addProperty(
					"synthesizers_before",
					synthesizersBefore
			);
			value.addProperty(
					"synthesizers_during",
					synthesizersDuring
			);
			value.addProperty("first_wet_frame", firstWetFrame);
			value.addProperty(
					"invalidated_owner_silent",
					invalidatedOwnerSilent
			);
			value.addProperty(
					"closing_old_owner_preserved_replacement",
					closingOldOwnerPreservedReplacement
			);
			value.addProperty("shadow_restarted", shadowRestarted);
			value.addProperty("wet_owner_released", wetOwnerReleased);
			value.addProperty(
					"history_cleared_on_restart",
					historyClearedOnRestart
			);
			value.addProperty(
					"phase_state_cleared_on_restart",
					phaseStateClearedOnRestart
			);
			value.addProperty("pcm_offset", pcmOffset);
			value.addProperty("pcm_bytes", pcmBytes);
			value.addProperty("pcm_sha256", pcmSha256);
			boolean passes = shadowP99Ms <= MAXIMUM_SHADOW_P99_MS
					&& handoffP99Ms <= MAXIMUM_HANDOFF_P99_MS
					&& historyFramesBefore
					== ListenerReverbHistoryProducer.HISTORY_FRAMES
					&& prerollFrames
					== ListenerReverbHistoryProducer.HISTORY_FRAMES
					&& synthesizersBefore == 6
					&& synthesizersDuring == 6
					&& firstWetFrame == 0
					&& invalidatedOwnerSilent
					&& closingOldOwnerPreservedReplacement
					&& shadowRestarted
					&& wetOwnerReleased
					&& historyClearedOnRestart
					&& phaseStateClearedOnRestart;
			value.addProperty("passes", passes);
			return value;
		}
	}
}
