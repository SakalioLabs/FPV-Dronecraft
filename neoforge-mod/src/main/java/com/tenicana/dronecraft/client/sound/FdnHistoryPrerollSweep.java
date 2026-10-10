package com.tenicana.dronecraft.client.sound;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import com.tenicana.dronecraft.acoustics.reverb.ListenerSharedFdn;

/**
 * Sweeps bounded mixed-input history used to pre-roll a newly created FDN.
 */
public final class FdnHistoryPrerollSweep {
	private static final int SAMPLE_RATE_HZ = 48_000;
	private static final int SEGMENT_FRAMES = 48_000;
	private static final int SEGMENT_COUNT = 4;
	private static final int RENDER_FRAMES =
			SEGMENT_FRAMES * SEGMENT_COUNT;
	private static final int HISTORY_END_FRAME = 96_000;
	private static final int EVALUATION_FRAMES = 12_000;
	private static final int REFERENCE_HISTORY_MS = 1_000;
	private static final int[] PREROLL_MILLISECONDS = {
		0, 50, 100, 250, 500, 1_000
	};
	private static final int WARMUP_ITERATIONS = 10;
	private static final int MEASURED_ITERATIONS = 50;
	private static final double MAXIMUM_P99_MILLISECONDS =
			(2_048.0 / SAMPLE_RATE_HZ) * 1_000.0 * 0.25;
	private static final double MINIMUM_CORRELATION = 0.90;
	private static final double MINIMUM_RMS_RATIO = 0.90;
	private static final double MAXIMUM_RMS_RATIO = 1.10;
	private static final double MAXIMUM_NORMALIZED_RMSE = 0.50;

	private FdnHistoryPrerollSweep() {
	}

	public static void main(String[] arguments) throws Exception {
		if (arguments.length != 5) {
			throw new IllegalArgumentException(
					"usage: FdnHistoryPrerollSweep <D100-json> "
							+ "<D094-json> <D094-pcm> "
							+ "<output-json> <output-pcm>"
			);
		}
		Locale.setDefault(Locale.ROOT);
		byte[] transitionBytes = Files.readAllBytes(
				Path.of(arguments[0])
		);
		byte[] traceBytes = Files.readAllBytes(Path.of(arguments[1]));
		byte[] tracePcm = Files.readAllBytes(Path.of(arguments[2]));
		JsonObject transition = JsonParser.parseString(
				new String(transitionBytes, StandardCharsets.UTF_8)
		).getAsJsonObject();
		JsonObject trace = JsonParser.parseString(
				new String(traceBytes, StandardCharsets.UTF_8)
		).getAsJsonObject();
		validateReports(transition, trace, tracePcm);
		byte[] motor = concatenateLayer(
				"motor",
				transition,
				trace,
				tracePcm
		);
		byte[] propeller = concatenateLayer(
				"propeller",
				transition,
				trace,
				tracePcm
		);
		float[] mixedInput = mixInput(motor, propeller);
		List<Environment> environments = readEnvironments(transition);
		byte[] sidecar = new byte[
				environments.size()
						* (PREROLL_MILLISECONDS.length + 1)
						* EVALUATION_FRAMES * 2
		];
		int sidecarOffset = 0;
		List<CaseResult> cases = new ArrayList<>();
		List<SignalRange> references = new ArrayList<>();
		for (Environment environment : environments) {
			float[] reference = render(
					mixedInput,
					environment.controls(),
					REFERENCE_HISTORY_MS
			);
			byte[] referencePcm = pcm16Le(reference);
			System.arraycopy(
					referencePcm,
					0,
					sidecar,
					sidecarOffset,
					referencePcm.length
			);
			references.add(new SignalRange(
					environment.name(),
					sidecarOffset,
					referencePcm.length,
					sha256(referencePcm)
			));
			sidecarOffset += referencePcm.length;
			for (int preRollMs : PREROLL_MILLISECONDS) {
				float[] candidate = render(
						mixedInput,
						environment.controls(),
						preRollMs
				);
				byte[] candidatePcm = pcm16Le(candidate);
				System.arraycopy(
						candidatePcm,
						0,
						sidecar,
						sidecarOffset,
						candidatePcm.length
				);
				double[] timings = benchmark(
						mixedInput,
						environment.controls(),
						preRollMs
				);
				cases.add(new CaseResult(
						environment.name(),
						preRollMs,
						correlation(candidate, reference),
						rms(candidate) / Math.max(
								rms(reference),
								1.0e-12
						),
						rmsDifference(candidate, reference)
								/ Math.max(rms(reference), 1.0e-12),
						percentile(timings, 50.0),
						percentile(timings, 95.0),
						percentile(timings, 99.0),
						sidecarOffset,
						candidatePcm.length,
						sha256(candidatePcm)
				));
				sidecarOffset += candidatePcm.length;
			}
		}
		int selected = selectPreRoll(cases);
		Path reportOutput = Path.of(arguments[3])
				.toAbsolutePath().normalize();
		Path pcmOutput = Path.of(arguments[4])
				.toAbsolutePath().normalize();
		Files.createDirectories(reportOutput.getParent());
		Files.createDirectories(pcmOutput.getParent());
		Files.write(pcmOutput, sidecar);
		Files.writeString(
				reportOutput,
				reportJson(
						sha256(transitionBytes),
						sha256(traceBytes),
						sha256(tracePcm),
						sha256(motor),
						sha256(propeller),
						sha256(sidecar),
						environments,
						references,
						cases,
						selected
				),
				StandardCharsets.UTF_8
		);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-fdn-history-preroll-sweep\","
						+ "\"selected_pre_roll_ms\":%d,"
						+ "\"output\":\"%s\"}%n",
				selected,
				reportOutput.toString().replace("\\", "\\\\")
		);
	}

	private static void validateReports(
			JsonObject transition,
			JsonObject trace,
			byte[] tracePcm
	) {
		if (!"valid-backend-dynamic-transition".equals(
				transition.get("status").getAsString()
		) || transition.get("render_frames").getAsInt()
				!= RENDER_FRAMES) {
			throw new IllegalArgumentException(
					"D100 transition report is incompatible"
			);
		}
		if (!"valid-doppler-production-chunk-trace".equals(
				trace.get("status").getAsString()
		) || !sha256(tracePcm).equals(
				trace.get("pcm_sha256").getAsString()
		)) {
			throw new IllegalArgumentException(
					"D094 trace report or sidecar is incompatible"
			);
		}
	}

	private static byte[] concatenateLayer(
			String layer,
			JsonObject transition,
			JsonObject trace,
			byte[] sidecar
	) {
		List<JsonObject> chunks = new ArrayList<>();
		for (var element : trace.getAsJsonArray("chunks")) {
			JsonObject chunk = element.getAsJsonObject();
			if (layer.equals(chunk.get("layer").getAsString())) {
				chunks.add(chunk);
			}
		}
		chunks.sort(Comparator.comparingLong(
				value -> value.get("sequence").getAsLong()
		));
		JsonArray manifest = transition.getAsJsonArray("inputs");
		byte[] result = new byte[RENDER_FRAMES * 2];
		int outputOffset = 0;
		for (int index = 0; index < SEGMENT_COUNT; index++) {
			JsonObject chunk = chunks.get(index);
			int offset = chunk.get("pcm_offset").getAsInt();
			int count = chunk.get("pcm_bytes").getAsInt();
			String hash = chunk.get("pcm_sha256").getAsString();
			long sequence = chunk.get("sequence").getAsLong();
			if (count != SEGMENT_FRAMES * 2
					|| offset < 0
					|| offset + count > sidecar.length
					|| !manifestContains(
							manifest,
							layer,
							sequence,
							hash
					)) {
				throw new IllegalArgumentException(
						"D100/D094 " + layer + " input detached"
				);
			}
			byte[] payload = Arrays.copyOfRange(
					sidecar,
					offset,
					offset + count
			);
			if (!hash.equals(sha256(payload))) {
				throw new IllegalArgumentException(
						"D094 " + layer + " chunk hash changed"
				);
			}
			System.arraycopy(
					payload,
					0,
					result,
					outputOffset,
					payload.length
			);
			outputOffset += payload.length;
		}
		if (!sha256(result).equals(
				transition.get(layer + "_pcm_sha256").getAsString()
		)) {
			throw new IllegalArgumentException(
					"D100 combined " + layer + " hash changed"
			);
		}
		return result;
	}

	private static boolean manifestContains(
			JsonArray manifest,
			String layer,
			long sequence,
			String hash
	) {
		for (var element : manifest) {
			JsonObject item = element.getAsJsonObject();
			if (layer.equals(item.get("layer").getAsString())
					&& sequence == item.get("sequence").getAsLong()
					&& hash.equals(
							item.get("pcm_sha256").getAsString()
					)) {
				return true;
			}
		}
		return false;
	}

	private static List<Environment> readEnvironments(
			JsonObject transition
	) {
		JsonArray values = transition.getAsJsonArray("environments");
		String[] names = {"closed", "partial", "open"};
		List<Environment> result = new ArrayList<>();
		for (int index = 0; index < names.length; index++) {
			JsonObject value = values.get(index).getAsJsonObject();
			JsonObject rt60 = value.getAsJsonObject("rt60_seconds");
			result.add(new Environment(
					names[index],
					new FdnEnvironmentMapper.Controls(
							value.get("snapshot_generation")
									.getAsLong(),
							new AcousticBands(
									rt60.get("low").getAsDouble(),
									rt60.get("mid").getAsDouble(),
									rt60.get("high").getAsDouble()
							),
							value.get("wet_gain").getAsDouble(),
							0.0
					)
			));
		}
		return List.copyOf(result);
	}

	private static float[] mixInput(
			byte[] motorPcm,
			byte[] propellerPcm
	) {
		float[] result = new float[RENDER_FRAMES];
		for (int frame = 0; frame < RENDER_FRAMES; frame++) {
			float mixed = pcm16At(motorPcm, frame) / 32_768.0F
					+ pcm16At(propellerPcm, frame) / 32_768.0F;
			result[frame] = mixed / (1.0F + Math.abs(mixed));
		}
		return result;
	}

	private static float[] render(
			float[] mixedInput,
			FdnEnvironmentMapper.Controls environment,
			int preRollMilliseconds
	) {
		ListenerSharedFdn fdn = new ListenerSharedFdn(SAMPLE_RATE_HZ);
		fdn.configure(
				environment.rt60Seconds(),
				environment.wetGain(),
				0.0
		);
		int historyFrames = preRollMilliseconds
				* SAMPLE_RATE_HZ / 1_000;
		int historyStart = HISTORY_END_FRAME - historyFrames;
		float[] output = new float[RENDER_FRAMES];
		if (historyFrames > 0) {
			fdn.process(
					mixedInput,
					output,
					historyStart,
					historyFrames
			);
		}
		fdn.process(
				mixedInput,
				output,
				HISTORY_END_FRAME,
				EVALUATION_FRAMES
		);
		return Arrays.copyOfRange(
				output,
				HISTORY_END_FRAME,
				HISTORY_END_FRAME + EVALUATION_FRAMES
		);
	}

	private static double[] benchmark(
			float[] mixedInput,
			FdnEnvironmentMapper.Controls environment,
			int preRollMilliseconds
	) {
		for (int iteration = 0;
				iteration < WARMUP_ITERATIONS;
				iteration++) {
			render(mixedInput, environment, preRollMilliseconds);
		}
		double[] milliseconds = new double[MEASURED_ITERATIONS];
		for (int iteration = 0;
				iteration < MEASURED_ITERATIONS;
				iteration++) {
			long started = System.nanoTime();
			render(mixedInput, environment, preRollMilliseconds);
			milliseconds[iteration] =
					(System.nanoTime() - started) / 1_000_000.0;
		}
		Arrays.sort(milliseconds);
		return milliseconds;
	}

	private static int selectPreRoll(List<CaseResult> cases) {
		for (int preRollMs : PREROLL_MILLISECONDS) {
			List<CaseResult> matches = cases.stream()
					.filter(value ->
							value.preRollMilliseconds() == preRollMs
					)
					.toList();
			if (matches.size() == 3 && matches.stream().allMatch(
					FdnHistoryPrerollSweep::passes
			)) {
				return preRollMs;
			}
		}
		return -1;
	}

	private static boolean passes(CaseResult value) {
		return value.correlation() >= MINIMUM_CORRELATION
				&& value.rmsRatio() >= MINIMUM_RMS_RATIO
				&& value.rmsRatio() <= MAXIMUM_RMS_RATIO
				&& value.normalizedRmse() <= MAXIMUM_NORMALIZED_RMSE
				&& value.p99Milliseconds()
						<= MAXIMUM_P99_MILLISECONDS;
	}

	private static double rms(float[] signal) {
		double sum = 0.0;
		for (float value : signal) {
			sum += value * value;
		}
		return Math.sqrt(sum / signal.length);
	}

	private static double rmsDifference(float[] left, float[] right) {
		double sum = 0.0;
		for (int index = 0; index < left.length; index++) {
			double difference = left[index] - right[index];
			sum += difference * difference;
		}
		return Math.sqrt(sum / left.length);
	}

	private static double correlation(float[] left, float[] right) {
		double numerator = 0.0;
		double leftEnergy = 0.0;
		double rightEnergy = 0.0;
		for (int index = 0; index < left.length; index++) {
			numerator += left[index] * right[index];
			leftEnergy += left[index] * left[index];
			rightEnergy += right[index] * right[index];
		}
		return numerator / Math.sqrt(leftEnergy * rightEnergy);
	}

	private static double percentile(
			double[] sorted,
			double percentile
	) {
		double position = percentile / 100.0 * (sorted.length - 1);
		int lower = (int) Math.floor(position);
		int upper = (int) Math.ceil(position);
		double fraction = position - lower;
		return sorted[lower]
				+ (sorted[upper] - sorted[lower]) * fraction;
	}

	private static byte[] pcm16Le(float[] samples) {
		byte[] result = new byte[samples.length * 2];
		for (int index = 0; index < samples.length; index++) {
			int value = Math.round(
					Math.max(-1.0F, Math.min(1.0F, samples[index]))
							* 32_767.0F
			);
			result[index * 2] = (byte) value;
			result[index * 2 + 1] = (byte) (value >>> 8);
		}
		return result;
	}

	private static int pcm16At(byte[] pcm, int frame) {
		int low = pcm[frame * 2] & 0xff;
		int high = pcm[frame * 2 + 1];
		return (short) (low | (high << 8));
	}

	private static String reportJson(
			String transitionSha,
			String traceSha,
			String tracePcmSha,
			String motorSha,
			String propellerSha,
			String sidecarSha,
			List<Environment> environments,
			List<SignalRange> references,
			List<CaseResult> cases,
			int selected
	) {
		JsonObject report = new JsonObject();
		report.addProperty("schema_version", 1);
		report.addProperty(
				"status",
				"valid-fdn-history-preroll-sweep"
		);
		report.addProperty("sample_rate_hz", SAMPLE_RATE_HZ);
		report.addProperty(
				"history_end_frame",
				HISTORY_END_FRAME
		);
		report.addProperty(
				"evaluation_frames",
				EVALUATION_FRAMES
		);
		report.addProperty(
				"reference_history_ms",
				REFERENCE_HISTORY_MS
		);
		report.addProperty(
				"source_transition_report_sha256",
				transitionSha
		);
		report.addProperty("source_trace_report_sha256", traceSha);
		report.addProperty("source_trace_pcm_sha256", tracePcmSha);
		report.addProperty("motor_pcm_sha256", motorSha);
		report.addProperty("propeller_pcm_sha256", propellerSha);
		report.addProperty("sidecar_sha256", sidecarSha);
		report.addProperty("sidecar_format", "s16le-mono-48000");
		report.addProperty(
				"sidecar_bytes",
				environments.size()
						* (PREROLL_MILLISECONDS.length + 1)
						* EVALUATION_FRAMES * 2
		);
		report.addProperty("warmup_iterations", WARMUP_ITERATIONS);
		report.addProperty(
				"measured_iterations",
				MEASURED_ITERATIONS
		);
		JsonObject thresholds = new JsonObject();
		thresholds.addProperty(
				"minimum_correlation",
				MINIMUM_CORRELATION
		);
		thresholds.addProperty(
				"minimum_rms_ratio",
				MINIMUM_RMS_RATIO
		);
		thresholds.addProperty(
				"maximum_rms_ratio",
				MAXIMUM_RMS_RATIO
		);
		thresholds.addProperty(
				"maximum_normalized_rmse",
				MAXIMUM_NORMALIZED_RMSE
		);
		thresholds.addProperty(
				"maximum_p99_ms",
				MAXIMUM_P99_MILLISECONDS
		);
		report.add("thresholds", thresholds);
		JsonArray environmentJson = new JsonArray();
		for (Environment environment : environments) {
			JsonObject item = new JsonObject();
			item.addProperty("name", environment.name());
			JsonObject rt60 = new JsonObject();
			rt60.addProperty(
					"low",
					environment.controls().rt60Seconds().low()
			);
			rt60.addProperty(
					"mid",
					environment.controls().rt60Seconds().mid()
			);
			rt60.addProperty(
					"high",
					environment.controls().rt60Seconds().high()
			);
			item.add("rt60_seconds", rt60);
			item.addProperty(
					"wet_gain",
					environment.controls().wetGain()
			);
			environmentJson.add(item);
		}
		report.add("environments", environmentJson);
		JsonArray referenceJson = new JsonArray();
		for (SignalRange reference : references) {
			referenceJson.add(rangeJson(
					reference.environment(),
					REFERENCE_HISTORY_MS,
					reference.offset(),
					reference.bytes(),
					reference.sha256()
			));
		}
		report.add("references", referenceJson);
		JsonArray caseJson = new JsonArray();
		for (CaseResult value : cases) {
			JsonObject item = rangeJson(
					value.environment(),
					value.preRollMilliseconds(),
					value.offset(),
					value.bytes(),
					value.sha256()
			);
			item.addProperty("correlation", value.correlation());
			item.addProperty("rms_ratio", value.rmsRatio());
			item.addProperty(
					"normalized_rmse",
					value.normalizedRmse()
			);
			item.addProperty("p50_ms", value.p50Milliseconds());
			item.addProperty("p95_ms", value.p95Milliseconds());
			item.addProperty("p99_ms", value.p99Milliseconds());
			item.addProperty("passes", passes(value));
			caseJson.add(item);
		}
		report.add("cases", caseJson);
		report.addProperty("selected_pre_roll_ms", selected);
		report.addProperty(
				"selection_available",
				selected >= 0
		);
		report.addProperty(
				"mixed_input_history_only",
				true
		);
		report.addProperty("physical_endpoint_opened", false);
		report.addProperty("captures_audio", false);
		report.addProperty("release_calibrated", false);
		report.addProperty(
				"claim_boundary",
				"Single-listener pure Java FDN pre-roll sweep using "
						+ "production mixed PCM and Minecraft-mapped "
						+ "controls. The 1000-ms case is a bounded "
						+ "reference, not infinite warm state; timings "
						+ "exclude source synthesis, OpenAL, Minecraft "
						+ "frame time, and physical endpoints."
		);
		return report.toString() + System.lineSeparator();
	}

	private static JsonObject rangeJson(
			String environment,
			int preRollMs,
			int offset,
			int bytes,
			String sha
	) {
		JsonObject item = new JsonObject();
		item.addProperty("environment", environment);
		item.addProperty("pre_roll_ms", preRollMs);
		item.addProperty("pcm_offset", offset);
		item.addProperty("pcm_bytes", bytes);
		item.addProperty("pcm_sha256", sha);
		return item;
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
			FdnEnvironmentMapper.Controls controls
	) {
	}

	private record SignalRange(
			String environment,
			int offset,
			int bytes,
			String sha256
	) {
	}

	private record CaseResult(
			String environment,
			int preRollMilliseconds,
			double correlation,
			double rmsRatio,
			double normalizedRmse,
			double p50Milliseconds,
			double p95Milliseconds,
			double p99Milliseconds,
			int offset,
			int bytes,
			String sha256
	) {
	}
}
