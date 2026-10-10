package com.tenicana.dronecraft.client.sound;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;

/**
 * Offline LWJGL/C-ABI boundary benchmark. It does not start Minecraft, load
 * CUDA, traverse voxels, or touch an audio endpoint.
 */
public final class CudaDdaNativeBoundaryBenchmark {
	private static final int RAY_BYTES = 64;
	private static final int RESULT_BYTES = 72;

	private CudaDdaNativeBoundaryBenchmark() {
	}

	public static void main(String[] arguments) throws IOException {
		if (arguments.length != 5) {
			throw new IllegalArgumentException(
				"usage: <library> <output.json> <warmup> <iterations> "
					+ "<comma-separated-ray-limits>"
			);
		}
		Path libraryPath = Path.of(arguments[0]).toAbsolutePath();
		Path output = Path.of(arguments[1]).toAbsolutePath();
		int warmup = positive(arguments[2], "warmup");
		int iterations = positive(arguments[3], "iterations");
		int[] rayLimits = parseRayLimits(arguments[4]);
		int maximumRays = rayLimits[rayLimits.length - 1];
		ByteBuffer rays = BufferUtils.createByteBuffer(
			Math.multiplyExact(maximumRays, RAY_BYTES)
		).order(ByteOrder.nativeOrder());
		ByteBuffer results = BufferUtils.createByteBuffer(
			Math.multiplyExact(maximumRays, RESULT_BYTES)
		).order(ByteOrder.nativeOrder());

		try (SharedLibrary library = Library.loadNative(
				"mcfpv_dda_boundary",
				libraryPath.toString()
		)) {
			long abiFunction = required(
				library,
				"mcfpv_dda_boundary_abi_version"
			);
			long aggregateFunction = required(
				library,
				"mcfpv_dda_boundary_aggregate"
			);
			int abiVersion = JNI.invokeI(abiFunction);
			if (abiVersion != 1) {
				throw new IllegalStateException(
					"unsupported native boundary ABI " + abiVersion
				);
			}
			List<Entry> entries = new ArrayList<>();
			long checksum = 0L;
			for (int rayLimit : rayLimits) {
				for (int index = 0; index < warmup; ++index) {
					pack(rays, rayLimit, index);
					invoke(aggregateFunction, rays, results, rayLimit);
					checksum = Long.rotateLeft(checksum, 1)
						^ unpack(results, rayLimit);
				}
				List<Double> packSamples = new ArrayList<>();
				List<Double> nativeSamples = new ArrayList<>();
				List<Double> unpackSamples = new ArrayList<>();
				List<Double> totalSamples = new ArrayList<>();
				for (int index = 0; index < iterations; ++index) {
					long totalStart = System.nanoTime();
					long stageStart = totalStart;
					pack(rays, rayLimit, warmup + index);
					packSamples.add(elapsedMillis(stageStart));
					stageStart = System.nanoTime();
					invoke(
						aggregateFunction,
						rays,
						results,
						rayLimit
					);
					nativeSamples.add(elapsedMillis(stageStart));
					stageStart = System.nanoTime();
					checksum = Long.rotateLeft(checksum, 1)
						^ unpack(results, rayLimit);
					unpackSamples.add(elapsedMillis(stageStart));
					totalSamples.add(elapsedMillis(totalStart));
				}
				entries.add(new Entry(
					rayLimit,
					packSamples,
					nativeSamples,
					unpackSamples,
					totalSamples
				));
			}
			writeAtomic(
				output,
				json(
					libraryPath,
					warmup,
					iterations,
					checksum,
					entries
				)
			);
		}
	}

	private static int positive(String value, String label) {
		int parsed = Integer.parseInt(value);
		if (parsed < 1) {
			throw new IllegalArgumentException(label + " must be positive");
		}
		return parsed;
	}

	private static int[] parseRayLimits(String text) {
		int[] result = Arrays.stream(text.split(","))
			.mapToInt(value -> positive(value, "ray limit"))
			.toArray();
		for (int index = 1; index < result.length; ++index) {
			if (result[index] <= result[index - 1]) {
				throw new IllegalArgumentException(
					"ray limits must be unique and ascending"
				);
			}
		}
		return result;
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

	private static void pack(ByteBuffer target, int rays, int pass) {
		for (int index = 0; index < rays; ++index) {
			int base = index * RAY_BYTES;
			double seed = index * 0.03125 + pass * 0.0001;
			target.putDouble(base, seed);
			target.putDouble(base + 8, seed * 0.5);
			target.putDouble(base + 16, seed * -0.25);
			target.putDouble(base + 24, seed + 16.0);
			target.putDouble(base + 32, seed * 0.5 + 8.0);
			target.putDouble(base + 40, seed * -0.25 + 4.0);
			target.putLong(base + 48, 512L + (index & 255));
			target.putLong(
				base + 56,
				((long) index * 257L) ^ Integer.toUnsignedLong(pass)
			);
		}
	}

	private static void invoke(
			long function,
			ByteBuffer rays,
			ByteBuffer results,
			int rayCount
	) {
		int status = JNI.invokePPI(
			MemoryUtil.memAddress(rays),
			MemoryUtil.memAddress(results),
			rayCount,
			function
		);
		if (status != 0) {
			throw new IllegalStateException(
				"native boundary returned " + status
			);
		}
	}

	private static long unpack(ByteBuffer source, int rays) {
		long checksum = 0L;
		for (int index = 0; index < rays; ++index) {
			int base = index * RESULT_BYTES;
			checksum ^= Integer.toUnsignedLong(source.getInt(base));
			checksum ^= source.getLong(base + 8);
			checksum ^= Double.doubleToRawLongBits(
				source.getDouble(base + 48)
			);
		}
		return checksum;
	}

	private static double elapsedMillis(long start) {
		return (System.nanoTime() - start) / 1_000_000.0;
	}

	private static String json(
			Path library,
			int warmup,
			int iterations,
			long checksum,
			List<Entry> entries
	) {
		StringBuilder builder = new StringBuilder();
		builder.append("{\n")
			.append("  \"status\": \"valid\",\n")
			.append("  \"schema\": 1,\n")
			.append("  \"backend\": \"lwjgl-jni-c-abi\",\n")
			.append("  \"cuda_executed\": false,\n")
			.append("  \"minecraft_started\": false,\n")
			.append("  \"physical_endpoint_opened\": false,\n")
			.append("  \"ray_bytes\": ").append(RAY_BYTES).append(",\n")
			.append("  \"result_bytes\": ").append(RESULT_BYTES)
			.append(",\n")
			.append("  \"warmup_passes\": ").append(warmup).append(",\n")
			.append("  \"measured_passes\": ").append(iterations)
			.append(",\n")
			.append("  \"library\": \"")
			.append(escape(library.toString())).append("\",\n")
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
			.append("  \"claim_boundary\": \"C ABI packing/call/unpacking ")
			.append("only; no CUDA, voxel traversal, Minecraft scheduling, ")
			.append("or audio endpoint.\"\n")
			.append("}\n")
			.toString();
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static void writeAtomic(Path output, String payload)
			throws IOException {
		Files.createDirectories(output.getParent());
		Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
		Files.writeString(temporary, payload);
		Files.move(
			temporary,
			output,
			StandardCopyOption.REPLACE_EXISTING,
			StandardCopyOption.ATOMIC_MOVE
		);
	}

	private record Entry(
		int rays,
		List<Double> pack,
		List<Double> nativeCall,
		List<Double> unpack,
		List<Double> total
	) {
		String json() {
			return "    {\n"
				+ "      \"rays\": " + rays + ",\n"
				+ stage("pack", pack) + ",\n"
				+ stage("native_call", nativeCall) + ",\n"
				+ stage("unpack", unpack) + ",\n"
				+ stage("total", total) + "\n"
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
