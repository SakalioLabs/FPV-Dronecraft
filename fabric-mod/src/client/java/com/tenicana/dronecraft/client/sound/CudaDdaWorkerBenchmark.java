package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;
import com.tenicana.dronecraft.acoustics.propagation.SparseMaterialSnapshot;
import com.tenicana.dronecraft.acoustics.voxel.DdaProductionSnapshotBundle;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Offline end-to-end benchmark for the watchdog-controlled CUDA DDA worker.
 * It never starts Minecraft or opens an audio endpoint.
 */
public final class CudaDdaWorkerBenchmark {
	private static final ByteOrder WIRE_ORDER = ByteOrder.LITTLE_ENDIAN;
	private static final int INITIALIZE_HEADER_BYTES = 16;
	private static final int SUBMIT_HEADER_BYTES = 8;
	private static final int CELL_BYTES = 24;
	private static final int RAY_BYTES = 64;
	private static final int METRICS_BYTES = 32;
	private static final int RESULT_BYTES = 72;
	private static final int INFO_BYTES = 32;
	private static final double ABSOLUTE_TOLERANCE = 1.0e-5;
	private static final double RELATIVE_TOLERANCE = 1.0e-9;

	private CudaDdaWorkerBenchmark() {
	}

	public static void main(String[] arguments) throws Exception {
		if (arguments.length != 11) {
			throw new IllegalArgumentException(
				"usage: <worker.exe> <nvrtc-library> <kernel.cu> "
					+ "<bundle.bin> <output.json> <warmup> "
					+ "<iterations> <comma-separated-ray-limits> "
					+ "<driver-probe-timeout-seconds> "
					+ "<initialize-timeout-ms> <submit-timeout-ms>"
			);
		}
		Path worker = absoluteFile(arguments[0], "worker");
		Path nvrtc = absoluteFile(arguments[1], "NVRTC library");
		Path kernel = absoluteFile(arguments[2], "NVRTC kernel");
		Path bundlePath = absoluteFile(arguments[3], "production bundle");
		Path output = Path.of(arguments[4]).toAbsolutePath();
		int warmup = positive(arguments[5], "warmup");
		int iterations = positive(arguments[6], "iterations");
		int[] rayLimits = parseRayLimits(arguments[7]);
		int driverProbeTimeout = positive(
			arguments[8],
			"driver probe timeout"
		);
		Duration initializeTimeout = Duration.ofMillis(
			positive(arguments[9], "initialize timeout")
		);
		Duration submitTimeout = Duration.ofMillis(
			positive(arguments[10], "submit timeout")
		);
		String driverProbe = probeDriver(
			Duration.ofSeconds(driverProbeTimeout)
		);
		DdaProductionSnapshotBundle.Bundle bundle =
			DdaProductionSnapshotBundle.read(bundlePath);
		int maximumRays = rayLimits[rayLimits.length - 1];
		if (maximumRays > bundle.rays().size()) {
			throw new IllegalArgumentException(
				"largest ray limit exceeds bundle ray count"
			);
		}

		byte[] initializePayload = packInitialization(
			bundle.snapshot(),
			maximumRays
		);
		List<Entry> entries = new ArrayList<>();
		long checksum = 0L;
		long initializeStart = System.nanoTime();
		try (CudaDdaWorkerClient client = new CudaDdaWorkerClient(
				worker,
				List.of(
					"--nvrtc",
					nvrtc.toString(),
					"--kernel",
					kernel.toString()
				),
				submitTimeout
			)) {
			CudaDdaWorkerClient.Attempt initialization = client.request(
				CudaDdaWorkerClient.OPCODE_INITIALIZE,
				initializePayload,
				initializeTimeout
			);
			requireSuccess(initialization, "worker initialization");
			double initializeMillis = elapsedMillis(initializeStart);
			BridgeInfo info = unpackInfo(initialization.payload());
			if (info.cellCount != bundle.snapshot().size()
					|| info.maximumRays != maximumRays) {
				throw new IllegalStateException(
					"worker resident state does not match initialization"
				);
			}

			for (int rayLimit : rayLimits) {
				Submission parity = submit(
					client,
					bundle.rays(),
					rayLimit,
					submitTimeout
				);
				verifyParity(bundle, parity.results, rayLimit);
				for (int index = 0; index < warmup; ++index) {
					Submission workerResult = submit(
						client,
						bundle.rays(),
						rayLimit,
						submitTimeout
					);
					checksum = Long.rotateLeft(checksum, 1)
						^ unpackChecksum(
							workerResult.results,
							rayLimit
						);
					checksum = Long.rotateLeft(checksum, 1)
						^ cpuBatchChecksum(bundle, rayLimit);
				}
				Samples samples = new Samples();
				for (int index = 0; index < iterations; ++index) {
					if (index % 2 == 0) {
						checksum = measureCpu(
							bundle,
							rayLimit,
							samples,
							checksum
						);
						checksum = measureWorker(
							client,
							bundle,
							rayLimit,
							submitTimeout,
							samples,
							checksum
						);
					} else {
						checksum = measureWorker(
							client,
							bundle,
							rayLimit,
							submitTimeout,
							samples,
							checksum
						);
						checksum = measureCpu(
							bundle,
							rayLimit,
							samples,
							checksum
						);
					}
				}
				entries.add(new Entry(rayLimit, samples));
			}
			writeAtomic(
				output,
				json(
					worker,
					nvrtc,
					kernel,
					bundlePath,
					bundle,
					driverProbe,
					initializeMillis,
					initializeTimeout,
					submitTimeout,
					info,
					warmup,
					iterations,
					checksum,
					entries
				)
			);
		}
	}

	private static Path absoluteFile(String value, String label) {
		Path path = Path.of(value).toAbsolutePath();
		if (!Files.isRegularFile(path)) {
			throw new IllegalArgumentException(label + " is unavailable");
		}
		return path;
	}

	private static int positive(String value, String label) {
		int parsed = Integer.parseInt(value);
		if (parsed < 1) {
			throw new IllegalArgumentException(label + " must be positive");
		}
		return parsed;
	}

	private static int[] parseRayLimits(String value) {
		int[] limits = Arrays.stream(value.split(","))
			.mapToInt(part -> positive(part, "ray limit"))
			.toArray();
		if (limits.length == 0) {
			throw new IllegalArgumentException("ray limits are empty");
		}
		for (int index = 1; index < limits.length; ++index) {
			if (limits[index] <= limits[index - 1]) {
				throw new IllegalArgumentException(
					"ray limits must be unique and ascending"
				);
			}
		}
		return limits;
	}

	private static String probeDriver(Duration timeout)
			throws IOException, InterruptedException {
		Process process = new ProcessBuilder("nvidia-smi", "-L")
			.redirectErrorStream(true)
			.start();
		if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
			process.destroyForcibly();
			process.waitFor(2, TimeUnit.SECONDS);
			throw new IllegalStateException(
				"NVIDIA driver probe timed out after "
					+ timeout.toSeconds() + " seconds"
			);
		}
		String output = new String(
			process.getInputStream().readAllBytes(),
			StandardCharsets.UTF_8
		).strip();
		if (process.exitValue() != 0 || output.isEmpty()) {
			throw new IllegalStateException(
				"NVIDIA driver probe failed: " + output
			);
		}
		return output;
	}

	private static byte[] packInitialization(
			SparseMaterialSnapshot snapshot,
			int maximumRays
		) {
		List<SparseMaterialSnapshot.CellSample> cells =
			snapshot.diagnosticEntries();
		List<AcousticMaterial> materials =
			AcousticMaterials.diagnosticTable();
		ByteBuffer output = allocate(
			INITIALIZE_HEADER_BYTES
				+ cells.size() * CELL_BYTES
				+ materials.size() * 3 * Double.BYTES
		);
		output.putInt(cells.size());
		output.putInt(materials.size());
		output.putInt(maximumRays);
		output.putInt(0);
		for (SparseMaterialSnapshot.CellSample cell : cells) {
			output.putLong(cell.packedCell());
			output.putInt(
				AcousticMaterials.diagnosticMaterialId(
					cell.sample().material()
				)
			);
			output.putInt(0);
			output.putDouble(cell.sample().fillFraction());
		}
		for (AcousticMaterial material : materials) {
			AcousticBands loss = material.transmissionLossDbPerMeter();
			output.putDouble(loss.low());
			output.putDouble(loss.mid());
			output.putDouble(loss.high());
		}
		return output.array();
	}

	private static byte[] packSubmit(
			List<DdaProductionSnapshotBundle.Ray> rays,
			int rayCount
		) {
		ByteBuffer output = allocate(
			SUBMIT_HEADER_BYTES + rayCount * RAY_BYTES
		);
		output.putInt(rayCount);
		output.putInt(0);
		for (int index = 0; index < rayCount; ++index) {
			DdaProductionSnapshotBundle.Ray ray = rays.get(index);
			output.putDouble(ray.start().x());
			output.putDouble(ray.start().y());
			output.putDouble(ray.start().z());
			output.putDouble(ray.end().x());
			output.putDouble(ray.end().y());
			output.putDouble(ray.end().z());
			output.putInt(ray.maximumCells());
			output.putInt(0);
			output.putLong(0L);
		}
		return output.array();
	}

	private static ByteBuffer allocate(int bytes) {
		return ByteBuffer.allocate(bytes).order(WIRE_ORDER);
	}

	private static Submission submit(
			CudaDdaWorkerClient client,
			List<DdaProductionSnapshotBundle.Ray> rays,
			int rayCount,
			Duration deadline
		) {
		long packStart = System.nanoTime();
		byte[] payload = packSubmit(rays, rayCount);
		double packMillis = elapsedMillis(packStart);
		long requestStart = System.nanoTime();
		CudaDdaWorkerClient.Attempt attempt = client.request(
			CudaDdaWorkerClient.OPCODE_SUBMIT,
			payload,
			deadline
		);
		double requestMillis = elapsedMillis(requestStart);
		requireSuccess(attempt, "worker submit");
		int expectedBytes = METRICS_BYTES + rayCount * RESULT_BYTES;
		if (attempt.payload().length != expectedBytes) {
			throw new IllegalStateException(
				"worker submit response length mismatch"
			);
		}
		ByteBuffer response = ByteBuffer.wrap(attempt.payload())
			.order(WIRE_ORDER);
		NativeMetrics metrics = new NativeMetrics(
			response.getDouble(),
			response.getDouble(),
			response.getDouble(),
			response.getDouble()
		);
		byte[] results = new byte[rayCount * RESULT_BYTES];
		response.get(results);
		return new Submission(
			packMillis,
			requestMillis,
			metrics,
			results
		);
	}

	private static void requireSuccess(
			CudaDdaWorkerClient.Attempt attempt,
			String operation
		) {
		if (attempt.useCpuFallback()
				|| attempt.failure() != CudaDdaWorkerClient.Failure.NONE
				|| attempt.status() != 0) {
			throw new IllegalStateException(
				operation + " failed closed: " + attempt.failure()
			);
		}
	}

	private static BridgeInfo unpackInfo(byte[] payload) {
		if (payload.length != INFO_BYTES) {
			throw new IllegalStateException(
				"worker bridge info length mismatch"
			);
		}
		ByteBuffer info = ByteBuffer.wrap(payload).order(WIRE_ORDER);
		BridgeInfo result = new BridgeInfo(
			info.getInt(),
			info.getInt(),
			info.getInt(),
			info.getInt(),
			info.getInt(),
			info.getInt(),
			info.getInt()
		);
		if (info.getInt() != 0) {
			throw new IllegalStateException(
				"worker bridge info reserved field is nonzero"
			);
		}
		return result;
	}

	private static void verifyParity(
			DdaProductionSnapshotBundle.Bundle bundle,
			byte[] resultBytes,
			int rayCount
		) {
		ByteBuffer results = ByteBuffer.wrap(resultBytes).order(WIRE_ORDER);
		for (int index = 0; index < rayCount; ++index) {
			DdaProductionSnapshotBundle.Ray ray = bundle.rays().get(index);
			DirectPathSolver.Result expected = DirectPathSolver.solve(
				ray.start(),
				ray.end(),
				bundle.snapshot(),
				ray.maximumCells()
			);
			int base = index * RESULT_BYTES;
			int segmentCount = results.getInt(base);
			int materialCells = results.getInt(base + 4);
			int flags = Byte.toUnsignedInt(results.get(base + 8));
			boolean hasFirst = results.get(base + 9) != 0;
			long firstPacked = results.getLong(base + 16);
			FirstMaterial first = firstMaterial(bundle.snapshot(), ray);
			int expectedFlags = expected.complete() ? 1 : 4;
			if (segmentCount != expected.visitedCellCount()
					|| materialCells != expected.materialCellCount()
					|| flags != expectedFlags
					|| hasFirst != first.present
					|| (hasFirst && firstPacked != first.packed)) {
				throw new IllegalStateException(
					"worker aggregate topology mismatch for ray " + index
				);
			}
			AcousticBands loss = expected.transmissionLossDb();
			AcousticBands gain = expected.transmissionEnergyGain();
			double[] expectedBands = {
				loss.low(),
				loss.mid(),
				loss.high(),
				gain.low(),
				gain.mid(),
				gain.high()
			};
			for (int band = 0; band < expectedBands.length; ++band) {
				double actual = results.getDouble(
					base + 24 + band * Double.BYTES
				);
				if (!close(actual, expectedBands[band])) {
					throw new IllegalStateException(
						"worker aggregate band mismatch for ray "
							+ index + ", band " + band
					);
				}
			}
		}
	}

	private static FirstMaterial firstMaterial(
			SparseMaterialSnapshot snapshot,
			DdaProductionSnapshotBundle.Ray ray
		) {
		long[] first = {0L};
		boolean[] present = {false};
		VoxelDda.walk(
			ray.start(),
			ray.end(),
			(x, y, z, length) -> {
				DirectPathSolver.MaterialSample sample =
					snapshot.sampleAt(x, y, z);
				if (!present[0]
						&& !sample.material().isAir()
						&& length * sample.fillFraction() > 0.0) {
					present[0] = true;
					first[0] = packCell(x, y, z);
				}
				return true;
			},
			ray.maximumCells()
		);
		return new FirstMaterial(present[0], first[0]);
	}

	private static long packCell(int x, int y, int z) {
		return ((long) x & 0x3ffffffL) << 38
			| ((long) z & 0x3ffffffL) << 12
			| (long) y & 0xfffL;
	}

	private static boolean close(double actual, double expected) {
		return Math.abs(actual - expected) <= ABSOLUTE_TOLERANCE
			+ RELATIVE_TOLERANCE * Math.abs(expected);
	}

	private static long unpackChecksum(byte[] resultBytes, int rayCount) {
		ByteBuffer results = ByteBuffer.wrap(resultBytes).order(WIRE_ORDER);
		long checksum = 0L;
		for (int index = 0; index < rayCount; ++index) {
			int base = index * RESULT_BYTES;
			checksum ^= Integer.toUnsignedLong(results.getInt(base));
			checksum ^= results.getLong(base + 16);
			checksum ^= Double.doubleToRawLongBits(
				results.getDouble(base + 56)
			);
		}
		return checksum;
	}

	private static long cpuBatchChecksum(
			DdaProductionSnapshotBundle.Bundle bundle,
			int rayCount
		) {
		long checksum = 0L;
		for (int index = 0; index < rayCount; ++index) {
			DdaProductionSnapshotBundle.Ray ray = bundle.rays().get(index);
			DirectPathSolver.Result result = DirectPathSolver.solve(
				ray.start(),
				ray.end(),
				bundle.snapshot(),
				ray.maximumCells()
			);
			checksum ^= Integer.toUnsignedLong(result.visitedCellCount());
			checksum ^= Integer.toUnsignedLong(result.materialCellCount())
				<< 32;
			checksum ^= Double.doubleToRawLongBits(
				result.transmissionEnergyGain().mid()
			);
		}
		return checksum;
	}

	private static long measureCpu(
			DdaProductionSnapshotBundle.Bundle bundle,
			int rayCount,
			Samples samples,
			long checksum
		) {
		long start = System.nanoTime();
		long batchChecksum = cpuBatchChecksum(bundle, rayCount);
		samples.cpuJava.add(elapsedMillis(start));
		return Long.rotateLeft(checksum, 1) ^ batchChecksum;
	}

	private static long measureWorker(
			CudaDdaWorkerClient client,
			DdaProductionSnapshotBundle.Bundle bundle,
			int rayCount,
			Duration deadline,
			Samples samples,
			long checksum
		) {
		Submission submission = submit(
			client,
			bundle.rays(),
			rayCount,
			deadline
		);
		long unpackStart = System.nanoTime();
		long resultChecksum = unpackChecksum(
			submission.results,
			rayCount
		);
		samples.unpack.add(elapsedMillis(unpackStart));
		samples.pack.add(submission.packMillis);
		samples.ipcRoundTrip.add(submission.requestMillis);
		samples.h2d.add(submission.metrics.h2dMillis);
		samples.kernel.add(submission.metrics.kernelMillis);
		samples.d2h.add(submission.metrics.d2hMillis);
		samples.nativeTotal.add(submission.metrics.totalMillis);
		samples.javaTotal.add(
			submission.packMillis
				+ submission.requestMillis
				+ samples.unpack.get(samples.unpack.size() - 1)
		);
		return Long.rotateLeft(checksum, 1) ^ resultChecksum;
	}

	private static double elapsedMillis(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000.0;
	}

	private static String json(
			Path worker,
			Path nvrtc,
			Path kernel,
			Path bundlePath,
			DdaProductionSnapshotBundle.Bundle bundle,
			String driverProbe,
			double initializeMillis,
			Duration initializeTimeout,
			Duration submitTimeout,
			BridgeInfo info,
			int warmup,
			int iterations,
			long checksum,
			List<Entry> entries
		) throws IOException {
		StringBuilder builder = new StringBuilder()
			.append("{\n")
			.append("  \"status\": \"valid\",\n")
			.append("  \"schema\": 1,\n")
			.append("  \"backend\": \"watchdog-stdio-cuda-worker\",\n")
			.append("  \"cuda_executed\": true,\n")
			.append("  \"nvrtc_compiled\": true,\n")
			.append("  \"resident_cells\": true,\n")
			.append("  \"minecraft_started\": false,\n")
			.append("  \"physical_endpoint_opened\": false,\n")
			.append("  \"driver_probe\": \"")
			.append(escape(driverProbe)).append("\",\n")
			.append("  \"driver_version\": ")
			.append(info.driverVersion).append(",\n")
			.append("  \"nvrtc_version\": \"")
			.append(info.nvrtcMajor).append(".")
			.append(info.nvrtcMinor).append("\",\n")
			.append("  \"compute_capability\": \"")
			.append(info.computeMajor).append(".")
			.append(info.computeMinor).append("\",\n")
			.append("  \"worker_initialize_ms\": ")
			.append(format(initializeMillis)).append(",\n")
			.append("  \"initialize_timeout_ms\": ")
			.append(initializeTimeout.toMillis()).append(",\n")
			.append("  \"submit_timeout_ms\": ")
			.append(submitTimeout.toMillis()).append(",\n")
			.append("  \"resident_cell_count\": ")
			.append(info.cellCount).append(",\n")
			.append("  \"maximum_rays\": ")
			.append(info.maximumRays).append(",\n")
			.append("  \"bundle_rays\": ")
			.append(bundle.rays().size()).append(",\n")
			.append("  \"bundle_sha256\": \"")
			.append(sha256(bundlePath)).append("\",\n")
			.append("  \"snapshot_sha256\": \"")
			.append(bundle.snapshot().diagnosticSha256()).append("\",\n")
			.append("  \"kernel_sha256\": \"")
			.append(sha256(kernel)).append("\",\n")
			.append("  \"worker_sha256\": \"")
			.append(sha256(worker)).append("\",\n")
			.append("  \"nvrtc_sha256\": \"")
			.append(sha256(nvrtc)).append("\",\n")
			.append("  \"warmup_passes\": ").append(warmup)
			.append(",\n")
			.append("  \"measured_passes\": ").append(iterations)
			.append(",\n")
			.append("  \"paired_execution_order\": ")
			.append("\"cpu-worker / worker-cpu alternating\",\n")
			.append("  \"parity_verified\": true,\n")
			.append("  \"checksum\": \"")
			.append(Long.toUnsignedString(checksum)).append("\",\n")
			.append("  \"entries\": [\n");
		for (int index = 0; index < entries.size(); ++index) {
			if (index > 0) {
				builder.append(",\n");
			}
			builder.append(entries.get(index).json());
		}
		return builder.append("\n  ],\n")
			.append("  \"claim_boundary\": \"Offline Java/stdio/native ")
			.append("CUDA aggregate worker measurement; not Minecraft ")
			.append("frame, audio-thread, packaging, or release evidence.\"\n")
			.append("}\n")
			.toString();
	}

	private static String sha256(Path path) throws IOException {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256")
					.digest(Files.readAllBytes(path))
			);
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\r", "\\r")
			.replace("\n", "\\n");
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.9f", value);
	}

	private static void writeAtomic(Path output, String payload)
			throws IOException {
		Files.createDirectories(output.getParent());
		Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
		Files.writeString(temporary, payload, StandardCharsets.UTF_8);
		Files.move(
			temporary,
			output,
			StandardCopyOption.REPLACE_EXISTING,
			StandardCopyOption.ATOMIC_MOVE
		);
	}

	private record FirstMaterial(boolean present, long packed) {
	}

	private record BridgeInfo(
		int driverVersion,
		int nvrtcMajor,
		int nvrtcMinor,
		int computeMajor,
		int computeMinor,
		int cellCount,
		int maximumRays
	) {
	}

	private record NativeMetrics(
		double h2dMillis,
		double kernelMillis,
		double d2hMillis,
		double totalMillis
	) {
	}

	private record Submission(
		double packMillis,
		double requestMillis,
		NativeMetrics metrics,
		byte[] results
	) {
	}

	private static final class Samples {
		private final List<Double> cpuJava = new ArrayList<>();
		private final List<Double> pack = new ArrayList<>();
		private final List<Double> ipcRoundTrip = new ArrayList<>();
		private final List<Double> h2d = new ArrayList<>();
		private final List<Double> kernel = new ArrayList<>();
		private final List<Double> d2h = new ArrayList<>();
		private final List<Double> nativeTotal = new ArrayList<>();
		private final List<Double> unpack = new ArrayList<>();
		private final List<Double> javaTotal = new ArrayList<>();
	}

	private record Entry(int rays, Samples samples) {
		String json() {
			return "    {\n"
				+ "      \"rays\": " + rays + ",\n"
				+ stage("cpu_java", samples.cpuJava) + ",\n"
				+ stage("pack", samples.pack) + ",\n"
				+ stage("ipc_round_trip", samples.ipcRoundTrip) + ",\n"
				+ stage("h2d", samples.h2d) + ",\n"
				+ stage("kernel", samples.kernel) + ",\n"
				+ stage("d2h", samples.d2h) + ",\n"
				+ stage("native_total", samples.nativeTotal) + ",\n"
				+ stage("unpack", samples.unpack) + ",\n"
				+ stage("java_total", samples.javaTotal) + "\n"
				+ "    }";
		}

		private static String stage(String name, List<Double> samples) {
			return String.format(
				Locale.ROOT,
				"      \"%s_p50_ms\": %.9f,\n"
					+ "      \"%s_p95_ms\": %.9f,\n"
					+ "      \"%s_p99_ms\": %.9f,\n"
					+ "      \"%s_samples_ms\": %s",
				name,
				percentile(samples, 0.50),
				name,
				percentile(samples, 0.95),
				name,
				percentile(samples, 0.99),
				name,
				samples
			);
		}

		private static double percentile(
				List<Double> samples,
				double quantile
			) {
			List<Double> ordered = new ArrayList<>(samples);
			ordered.sort(Double::compare);
			int index = Math.max(
				0,
				(int) Math.ceil(quantile * ordered.size()) - 1
			);
			return ordered.get(Math.min(index, ordered.size() - 1));
		}
	}
}
