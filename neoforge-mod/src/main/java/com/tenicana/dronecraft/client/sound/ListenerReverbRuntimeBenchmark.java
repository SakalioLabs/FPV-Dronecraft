package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.TonalComponent;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Measures the shared wet stream with the maximum six active drones and the
 * research fallback's 4 x (12 BPF + 3 motor) tones per drone.
 */
public final class ListenerReverbRuntimeBenchmark {
	private static final int WARMUP_ITERATIONS = 80;
	private static final int MEASURED_ITERATIONS = 500;
	private static final int[] REQUEST_BYTES = {4_096, 16_384};
	private static final int SOURCE_COUNT = 6;

	private ListenerReverbRuntimeBenchmark() {
	}

	public static void main(String[] arguments) throws Exception {
		if (arguments.length != 1) {
			throw new IllegalArgumentException(
					"usage: ListenerReverbRuntimeBenchmark <output-json>"
			);
		}
		Locale.setDefault(Locale.ROOT);
		List<CaseResult> results = new ArrayList<>();
		long checksum = 0L;
		for (int requestedBytes : REQUEST_BYTES) {
			CaseRun run = runCase(requestedBytes);
			results.add(run.result());
			checksum += run.checksum();
		}
		String json = json(results, checksum);
		Path output = Path.of(arguments[0]).toAbsolutePath().normalize();
		Files.createDirectories(output.getParent());
		Files.writeString(output, json, StandardCharsets.UTF_8);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-benchmark\",\"cases\":%d,"
						+ "\"checksum\":%d,\"output\":\"%s\"}%n",
				results.size(),
				checksum,
				escape(output.toString())
		);
	}

	private static CaseRun runCase(int requestedBytes) {
		ListenerReverbState state = populatedState();
		ListenerReverbAudioStream stream =
				new ListenerReverbAudioStream(state);
		long checksum = 0L;
		for (int index = 0; index < WARMUP_ITERATIONS; index++) {
			checksum += consume(stream.read(requestedBytes));
		}
		com.sun.management.ThreadMXBean allocationBean = allocationBean();
		long allocatedBefore = allocatedBytes(allocationBean);
		long[] elapsed = new long[MEASURED_ITERATIONS];
		for (int index = 0; index < MEASURED_ITERATIONS; index++) {
			long start = System.nanoTime();
			ByteBuffer output = stream.read(requestedBytes);
			elapsed[index] = System.nanoTime() - start;
			checksum += consume(output);
		}
		long allocatedAfter = allocatedBytes(allocationBean);
		Arrays.sort(elapsed);
		double durationMilliseconds = requestedBytes
				/ (double) Short.BYTES
				/ DroneAcousticRenderState.SAMPLE_RATE
				* 1_000.0;
		double p99Milliseconds = percentile(elapsed, 0.99) / 1_000_000.0;
		long allocatedPerRead = allocatedBefore < 0L || allocatedAfter < 0L
				? -1L
				: (allocatedAfter - allocatedBefore) / MEASURED_ITERATIONS;

		state.clearSources();
		checksum += consume(stream.read(requestedBytes));
		int sourcesAfterRemoval = stream.synthesizerCount();
		stream.close();
		boolean closedReadEmpty = stream.read(requestedBytes).remaining() == 0;
		boolean closeReleasedBuffers = stream.bufferCapacitySamples() == 0;
		return new CaseRun(
				new CaseResult(
						requestedBytes,
						requestedBytes / Short.BYTES,
						durationMilliseconds,
						elapsed[MEASURED_ITERATIONS / 2] / 1_000_000.0,
						percentile(elapsed, 0.95) / 1_000_000.0,
						p99Milliseconds,
						p99Milliseconds / durationMilliseconds,
						allocatedPerRead,
						sourcesAfterRemoval,
						stream.closed(),
						closedReadEmpty,
						closeReleasedBuffers
				),
				checksum
		);
	}

	private static ListenerReverbState populatedState() {
		ListenerReverbState state = new ListenerReverbState();
		state.publishEnvironment(new FdnEnvironmentMapper.Controls(
				75L,
				new AcousticBands(1.2, 0.8, 0.45),
				0.35,
				0.2
		));
		AcousticEmissionFrame emission = maximumResearchEmission();
		List<ListenerReverbState.Source> sources =
				new ArrayList<>(SOURCE_COUNT);
		for (int index = 0; index < SOURCE_COUNT; index++) {
			sources.add(new ListenerReverbState.Source(
					index + 1,
					emission,
					0.5,
					0.7
			));
		}
		state.publishSources(sources);
		return state;
	}

	private static AcousticEmissionFrame maximumResearchEmission() {
		List<TonalComponent> tones = new ArrayList<>();
		for (int rotor = 0; rotor < 4; rotor++) {
			double shaft = 240.0 + 7.0 * rotor;
			tones.add(tone(
					TonalComponent.Kind.SHAFT,
					rotor,
					1,
					shaft,
					0.05
			));
			tones.add(tone(
					TonalComponent.Kind.ELECTRICAL,
					rotor,
					1,
					shaft * 7.0,
					0.035
			));
			tones.add(tone(
					TonalComponent.Kind.COGGING_CANDIDATE,
					rotor,
					1,
					shaft * 14.0,
					0.012
			));
			for (int harmonic = 1; harmonic <= 12; harmonic++) {
				tones.add(tone(
						TonalComponent.Kind.BLADE_PASS,
						rotor,
						harmonic,
						shaft * 3.0 * harmonic,
						0.18 / Math.pow(harmonic, 1.15)
				));
			}
		}
		return new AcousticEmissionFrame(
				tones,
				new AcousticBands(0.016, 0.040, 0.024)
		);
	}

	private static TonalComponent tone(
			TonalComponent.Kind kind,
			int rotor,
			int order,
			double frequency,
			double amplitude
	) {
		return new TonalComponent(
				kind,
				rotor,
				order,
				frequency,
				amplitude,
				0.31 * rotor + 0.07 * order
		);
	}

	private static long consume(ByteBuffer output) {
		if (output.remaining() < Short.BYTES) {
			return 0L;
		}
		return output.getShort(output.position());
	}

	private static double percentile(long[] sorted, double quantile) {
		int index = (int) Math.ceil(quantile * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
	}

	private static com.sun.management.ThreadMXBean allocationBean() {
		java.lang.management.ThreadMXBean bean =
				ManagementFactory.getThreadMXBean();
		if (!(bean instanceof com.sun.management.ThreadMXBean allocation)
				|| !allocation.isThreadAllocatedMemorySupported()) {
			return null;
		}
		if (!allocation.isThreadAllocatedMemoryEnabled()) {
			allocation.setThreadAllocatedMemoryEnabled(true);
		}
		return allocation;
	}

	private static long allocatedBytes(
			com.sun.management.ThreadMXBean bean
	) {
		return bean == null
				? -1L
				: bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
	}

	private static String json(List<CaseResult> results, long checksum) {
		List<String> cases = results.stream()
				.map(ListenerReverbRuntimeBenchmark::caseJson)
				.toList();
		boolean lifecyclePassed = results.stream().allMatch(result ->
				result.synthesizersAfterSourceRemoval() == 0
						&& result.streamClosed()
						&& result.closedReadEmpty()
						&& result.closeReleasedBuffers()
		);
		return String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": \"valid-benchmark\",%n"
						+ "  \"source_count\": %d,%n"
						+ "  \"rotors_per_source\": 4,%n"
						+ "  \"tones_per_rotor\": 15,%n"
						+ "  \"warmup_iterations\": %d,%n"
						+ "  \"measured_iterations\": %d,%n"
						+ "  \"checksum\": %d,%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"gates\": {"
						+ "\"lifecycle_cleanup\":%s,"
						+ "\"p99_below_25_percent_of_buffer\":%s},%n"
						+ "  \"claim_boundary\": \"Single-process Java/LWJGL "
						+ "wet-stream benchmark; excludes OpenAL scheduling, "
						+ "device replacement, and Minecraft frame time.\",%n"
						+ "  \"cases\": [%n    %s%n  ]%n"
						+ "}%n",
				SOURCE_COUNT,
				WARMUP_ITERATIONS,
				MEASURED_ITERATIONS,
				checksum,
				lifecyclePassed,
				results.stream().allMatch(
						result -> result.p99BufferFraction() <= 0.25
				),
				String.join(",\n    ", cases)
		);
	}

	private static String caseJson(CaseResult result) {
		return String.format(
				Locale.ROOT,
				"{\"requested_bytes\":%d,\"samples\":%d,"
						+ "\"buffer_duration_ms\":%.17g,"
						+ "\"p50_ms\":%.17g,\"p95_ms\":%.17g,"
						+ "\"p99_ms\":%.17g,"
						+ "\"p99_buffer_fraction\":%.17g,"
						+ "\"thread_allocated_bytes_per_read\":%d,"
						+ "\"synthesizers_after_source_removal\":%d,"
						+ "\"stream_closed\":%s,"
						+ "\"closed_read_empty\":%s,"
						+ "\"close_released_buffers\":%s}",
				result.requestedBytes(),
				result.samples(),
				result.bufferDurationMilliseconds(),
				result.p50Milliseconds(),
				result.p95Milliseconds(),
				result.p99Milliseconds(),
				result.p99BufferFraction(),
				result.threadAllocatedBytesPerRead(),
				result.synthesizersAfterSourceRemoval(),
				result.streamClosed(),
				result.closedReadEmpty(),
				result.closeReleasedBuffers()
		);
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private record CaseRun(CaseResult result, long checksum) {
	}

	private record CaseResult(
			int requestedBytes,
			int samples,
			double bufferDurationMilliseconds,
			double p50Milliseconds,
			double p95Milliseconds,
			double p99Milliseconds,
			double p99BufferFraction,
			long threadAllocatedBytesPerRead,
			int synthesizersAfterSourceRemoval,
			boolean streamClosed,
			boolean closedReadEmpty,
			boolean closeReleasedBuffers
	) {
	}
}
