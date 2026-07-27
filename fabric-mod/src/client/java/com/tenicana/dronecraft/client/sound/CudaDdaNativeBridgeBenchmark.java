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
import org.lwjgl.BufferUtils;
import org.lwjgl.Version;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;

/**
 * Offline production-bundle benchmark for the persistent native CUDA
 * aggregate bridge. It never starts Minecraft or opens an audio endpoint.
 */
public final class CudaDdaNativeBridgeBenchmark {
	private static final int CELL_BYTES = 24;
	private static final int RAY_BYTES = 64;
	private static final int RESULT_BYTES = 72;
	private static final int CONFIG_BYTES = 56;
	private static final int REQUEST_BYTES = 32;
	private static final int METRICS_BYTES = 32;
	private static final int INFO_BYTES = 32;
	private static final double ABSOLUTE_TOLERANCE = 1.0e-5;
	private static final double RELATIVE_TOLERANCE = 1.0e-9;

	private CudaDdaNativeBridgeBenchmark() {
	}

	public static void main(String[] arguments) throws Exception {
		if (arguments.length != 9) {
			throw new IllegalArgumentException(
				"usage: <bridge-library> <nvrtc-library> <kernel.cu> "
					+ "<bundle.bin> <output.json> <warmup> <iterations> "
					+ "<comma-separated-ray-limits> "
					+ "<driver-probe-timeout-seconds>"
			);
		}
		Path libraryPath = absoluteFile(arguments[0], "bridge library");
		Path nvrtcPath = absoluteFile(arguments[1], "NVRTC library");
		Path kernelPath = absoluteFile(arguments[2], "NVRTC kernel");
		Path bundlePath = absoluteFile(arguments[3], "production bundle");
		Path output = Path.of(arguments[4]).toAbsolutePath();
		int warmup = positive(arguments[5], "warmup");
		int iterations = positive(arguments[6], "iterations");
		int[] rayLimits = parseRayLimits(arguments[7]);
		int probeTimeout = positive(
			arguments[8],
			"driver probe timeout"
		);
		String driverProbe = probeDriver(Duration.ofSeconds(probeTimeout));
		DdaProductionSnapshotBundle.Bundle bundle =
			DdaProductionSnapshotBundle.read(bundlePath);
		if (rayLimits[rayLimits.length - 1] > bundle.rays().size()) {
			throw new IllegalArgumentException(
				"largest ray limit exceeds bundle ray count"
			);
		}

		ByteBuffer nvrtcName = cString(nvrtcPath.toString());
		ByteBuffer kernelSource = cString(
			Files.readString(kernelPath, StandardCharsets.UTF_8)
		);
		ByteBuffer cells = packCells(bundle.snapshot());
		ByteBuffer transmission = packTransmission();
		int maximumRays = rayLimits[rayLimits.length - 1];
		ByteBuffer rays = direct(maximumRays * RAY_BYTES);
		ByteBuffer results = direct(maximumRays * RESULT_BYTES);
		ByteBuffer metrics = direct(METRICS_BYTES);
		ByteBuffer request = direct(REQUEST_BYTES);
		ByteBuffer config = config(
			nvrtcName,
			kernelSource,
			cells,
			bundle.snapshot().size(),
			transmission,
			AcousticMaterials.diagnosticTable().size(),
			maximumRays
		);

		long createStart = System.nanoTime();
		try (Bridge bridge = Bridge.create(libraryPath, config)) {
			double createMillis = elapsedMillis(createStart);
			BridgeInfo info = bridge.info();
			List<Entry> entries = new ArrayList<>();
			long checksum = 0L;
			for (int rayLimit : rayLimits) {
				packRays(rays, bundle.rays(), rayLimit);
				bridge.submit(
					request,
					rays,
					results,
					metrics,
					rayLimit
				);
				verifyParity(
					bundle,
					results,
					rayLimit
				);
				for (int index = 0; index < warmup; ++index) {
					packRays(rays, bundle.rays(), rayLimit);
					bridge.submit(
						request,
						rays,
						results,
						metrics,
						rayLimit
					);
					checksum = Long.rotateLeft(checksum, 1)
						^ unpackChecksum(results, rayLimit);
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
						checksum = measureGpu(
							bridge,
							request,
							rays,
							results,
							metrics,
							bundle.rays(),
							rayLimit,
							samples,
							checksum
						);
					} else {
						checksum = measureGpu(
							bridge,
							request,
							rays,
							results,
							metrics,
							bundle.rays(),
							rayLimit,
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
					libraryPath,
					nvrtcPath,
					kernelPath,
					bundlePath,
					bundle,
					driverProbe,
					createMillis,
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

	private static ByteBuffer direct(int bytes) {
		return BufferUtils.createByteBuffer(bytes).order(
			ByteOrder.nativeOrder()
		);
	}

	private static ByteBuffer cString(String value) {
		byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
		ByteBuffer result = direct(encoded.length + 1);
		result.put(encoded).put((byte) 0).flip();
		return result;
	}

	private static ByteBuffer config(
			ByteBuffer nvrtcPath,
			ByteBuffer kernelSource,
			ByteBuffer cells,
			int cellCount,
			ByteBuffer transmission,
			int materialCount,
			int maximumRays
	) {
		ByteBuffer result = direct(CONFIG_BYTES);
		result.putInt(0, 1);
		result.putInt(4, 0);
		result.putLong(8, MemoryUtil.memAddress(nvrtcPath));
		result.putLong(16, MemoryUtil.memAddress(kernelSource));
		result.putLong(24, MemoryUtil.memAddress(cells));
		result.putInt(32, cellCount);
		result.putInt(36, materialCount);
		result.putLong(40, MemoryUtil.memAddress(transmission));
		result.putInt(48, maximumRays);
		result.putInt(52, 0);
		return result;
	}

	private static ByteBuffer packCells(SparseMaterialSnapshot snapshot) {
		List<SparseMaterialSnapshot.CellSample> entries =
			snapshot.diagnosticEntries();
		ByteBuffer result = direct(entries.size() * CELL_BYTES);
		for (int index = 0; index < entries.size(); ++index) {
			SparseMaterialSnapshot.CellSample cell = entries.get(index);
			int base = index * CELL_BYTES;
			result.putLong(base, cell.packedCell());
			result.putInt(
				base + 8,
				AcousticMaterials.diagnosticMaterialId(
					cell.sample().material()
				)
			);
			result.putInt(base + 12, 0);
			result.putDouble(base + 16, cell.sample().fillFraction());
		}
		return result;
	}

	private static ByteBuffer packTransmission() {
		List<AcousticMaterial> materials =
			AcousticMaterials.diagnosticTable();
		ByteBuffer result = direct(materials.size() * 3 * Double.BYTES);
		for (int index = 0; index < materials.size(); ++index) {
			AcousticBands loss =
				materials.get(index).transmissionLossDbPerMeter();
			int base = index * 3 * Double.BYTES;
			result.putDouble(base, loss.low());
			result.putDouble(base + 8, loss.mid());
			result.putDouble(base + 16, loss.high());
		}
		return result;
	}

	private static void packRays(
			ByteBuffer target,
			List<DdaProductionSnapshotBundle.Ray> rays,
			int rayCount
	) {
		for (int index = 0; index < rayCount; ++index) {
			DdaProductionSnapshotBundle.Ray ray = rays.get(index);
			int base = index * RAY_BYTES;
			target.putDouble(base, ray.start().x());
			target.putDouble(base + 8, ray.start().y());
			target.putDouble(base + 16, ray.start().z());
			target.putDouble(base + 24, ray.end().x());
			target.putDouble(base + 32, ray.end().y());
			target.putDouble(base + 40, ray.end().z());
			target.putInt(base + 48, ray.maximumCells());
			target.putInt(base + 52, 0);
			target.putLong(base + 56, 0L);
		}
	}

	private static void verifyParity(
			DdaProductionSnapshotBundle.Bundle bundle,
			ByteBuffer results,
			int rayCount
	) {
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
					"CUDA aggregate topology mismatch for ray " + index
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
						"CUDA aggregate band mismatch for ray "
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

	private static long unpackChecksum(ByteBuffer results, int rayCount) {
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

	private static long measureGpu(
			Bridge bridge,
			ByteBuffer request,
			ByteBuffer rays,
			ByteBuffer results,
			ByteBuffer metrics,
			List<DdaProductionSnapshotBundle.Ray> bundleRays,
			int rayCount,
			Samples samples,
			long checksum
	) {
		long totalStart = System.nanoTime();
		long stageStart = totalStart;
		packRays(rays, bundleRays, rayCount);
		samples.pack.add(elapsedMillis(stageStart));
		stageStart = System.nanoTime();
		bridge.submit(request, rays, results, metrics, rayCount);
		samples.javaNativeCall.add(elapsedMillis(stageStart));
		samples.h2d.add(metrics.getDouble(0));
		samples.kernel.add(metrics.getDouble(8));
		samples.d2h.add(metrics.getDouble(16));
		samples.nativeTotal.add(metrics.getDouble(24));
		stageStart = System.nanoTime();
		long resultChecksum = unpackChecksum(results, rayCount);
		samples.unpack.add(elapsedMillis(stageStart));
		samples.javaTotal.add(elapsedMillis(totalStart));
		return Long.rotateLeft(checksum, 1) ^ resultChecksum;
	}

	private static double elapsedMillis(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000.0;
	}

	private static String json(
			Path library,
			Path nvrtc,
			Path kernel,
			Path bundlePath,
			DdaProductionSnapshotBundle.Bundle bundle,
			String driverProbe,
			double createMillis,
			BridgeInfo info,
			int warmup,
			int iterations,
			long checksum,
			List<Entry> entries
	) throws IOException {
		StringBuilder builder = new StringBuilder();
		builder.append("{\n")
			.append("  \"status\": \"valid\",\n")
			.append("  \"schema\": 1,\n")
			.append("  \"backend\": \"lwjgl-c-abi-cuda-driver-nvrtc\",\n")
			.append("  \"cuda_executed\": true,\n")
			.append("  \"nvrtc_compiled\": true,\n")
			.append("  \"resident_cells\": true,\n")
			.append("  \"minecraft_started\": false,\n")
			.append("  \"physical_endpoint_opened\": false,\n")
			.append("  \"java_runtime\": \"")
			.append(escape(System.getProperty("java.runtime.version")))
			.append("\",\n")
			.append("  \"lwjgl_version\": \"")
			.append(escape(Version.getVersion())).append("\",\n")
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
			.append("  \"bridge_create_ms\": ")
			.append(format(createMillis)).append(",\n")
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
			.append("  \"bridge_library\": \"")
			.append(escape(library.toString())).append("\",\n")
			.append("  \"nvrtc_library\": \"")
			.append(escape(nvrtc.toString())).append("\",\n")
			.append("  \"warmup_passes\": ").append(warmup)
			.append(",\n")
			.append("  \"measured_passes\": ").append(iterations)
			.append(",\n")
			.append("  \"paired_execution_order\": ")
			.append("\"cpu-gpu / gpu-cpu alternating\",\n")
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
			.append("  \"claim_boundary\": \"Offline Java/native CUDA ")
			.append("aggregate bridge measurement; not Minecraft frame, ")
			.append("audio-thread, packaging, or release evidence.\"\n")
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

	private static final class Bridge implements AutoCloseable {
		private final SharedLibrary library;
		private final long handle;
		private final long submitFunction;
		private final long infoFunction;
		private final long destroyFunction;
		private final long lastErrorFunction;

		private Bridge(
				SharedLibrary library,
				long handle,
				long submitFunction,
				long infoFunction,
				long destroyFunction,
				long lastErrorFunction
		) {
			this.library = library;
			this.handle = handle;
			this.submitFunction = submitFunction;
			this.infoFunction = infoFunction;
			this.destroyFunction = destroyFunction;
			this.lastErrorFunction = lastErrorFunction;
		}

		static Bridge create(Path libraryPath, ByteBuffer config) {
			SharedLibrary library = Library.loadNative(
				"mcfpv_dda_boundary",
				libraryPath.toString()
			);
			try {
				long abi = required(
					library,
					"mcfpv_cuda_bridge_abi_version"
				);
				long create = required(
					library,
					"mcfpv_cuda_bridge_create"
				);
				long submit = required(
					library,
					"mcfpv_cuda_bridge_submit"
				);
				long info = required(
					library,
					"mcfpv_cuda_bridge_info"
				);
				long destroy = required(
					library,
					"mcfpv_cuda_bridge_destroy"
				);
				long lastError = required(
					library,
					"mcfpv_cuda_bridge_last_error"
				);
				if (JNI.invokeI(abi) != 1) {
					throw new IllegalStateException(
						"unsupported CUDA bridge ABI"
					);
				}
				long handle = JNI.invokePP(
					MemoryUtil.memAddress(config),
					create
				);
				if (handle == MemoryUtil.NULL) {
					throw new IllegalStateException(
						lastError(lastError)
					);
				}
				return new Bridge(
					library,
					handle,
					submit,
					info,
					destroy,
					lastError
				);
			} catch (RuntimeException | Error error) {
				library.free();
				throw error;
			}
		}

		BridgeInfo info() {
			ByteBuffer info = direct(INFO_BYTES);
			int status = JNI.invokePPI(
				handle,
				MemoryUtil.memAddress(info),
				infoFunction
			);
			if (status != 0) {
				throw new IllegalStateException(lastError(lastErrorFunction));
			}
			return new BridgeInfo(
				info.getInt(0),
				info.getInt(4),
				info.getInt(8),
				info.getInt(12),
				info.getInt(16),
				info.getInt(20),
				info.getInt(24)
			);
		}

		void submit(
				ByteBuffer request,
				ByteBuffer rays,
				ByteBuffer results,
				ByteBuffer metrics,
				int rayCount
		) {
			request.putLong(0, MemoryUtil.memAddress(rays));
			request.putLong(8, MemoryUtil.memAddress(results));
			request.putInt(16, rayCount);
			request.putInt(20, 0);
			request.putLong(24, MemoryUtil.memAddress(metrics));
			int status = JNI.invokePPI(
				handle,
				MemoryUtil.memAddress(request),
				submitFunction
			);
			if (status != 0) {
				throw new IllegalStateException(lastError(lastErrorFunction));
			}
		}

		@Override
		public void close() {
			try {
				int status = JNI.invokePI(handle, destroyFunction);
				if (status != 0) {
					throw new IllegalStateException(
						lastError(lastErrorFunction)
					);
				}
			} finally {
				library.free();
			}
		}

		private static long required(
				SharedLibrary library,
				String functionName
		) {
			long address = library.getFunctionAddress(functionName);
			if (address == MemoryUtil.NULL) {
				throw new IllegalStateException(
					"missing native function " + functionName
				);
			}
			return address;
		}

		private static String lastError(long function) {
			long address = JNI.invokeP(function);
			return address == MemoryUtil.NULL
				? "native CUDA bridge returned no error detail"
				: MemoryUtil.memUTF8(address);
		}
	}

	private static final class Samples {
		private final List<Double> cpuJava = new ArrayList<>();
		private final List<Double> pack = new ArrayList<>();
		private final List<Double> javaNativeCall = new ArrayList<>();
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
				+ stage(
					"java_native_call",
					samples.javaNativeCall
				) + ",\n"
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
