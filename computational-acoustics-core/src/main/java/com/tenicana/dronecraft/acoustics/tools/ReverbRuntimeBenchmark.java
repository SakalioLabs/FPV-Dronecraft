package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;
import com.tenicana.dronecraft.acoustics.propagation.SparseMaterialSnapshot;
import com.tenicana.dronecraft.acoustics.reverb.ListenerSharedFdn;
import com.tenicana.dronecraft.acoustics.reverb.ReflectionVolume;
import com.tenicana.dronecraft.acoustics.reverb.VoxelReflectionProbe;

import java.util.Arrays;
import java.util.Locale;

/**
 * Lightweight same-process baseline for the runtime probe and shared FDN.
 */
public final class ReverbRuntimeBenchmark {
	private static final AcousticVector LISTENER =
			new AcousticVector(0.5, 0.5, 0.5);
	private static final VoxelReflectionProbe.Config CONFIG =
			new VoxelReflectionProbe.Config(128, 8, 24.0, 64, 1.0e-6);

	private ReverbRuntimeBenchmark() {
	}

	public static void main(String[] arguments) {
		int iterations = arguments.length == 0
				? 200
				: Integer.parseInt(arguments[0]);
		if (iterations < 20) {
			throw new IllegalArgumentException(
					"iterations must be at least 20"
			);
		}
		Locale.setDefault(Locale.ROOT);
		ReflectionVolume room = stoneRoom();
		for (int index = 0; index < 20; index++) {
			VoxelReflectionProbe.analyze(LISTENER, room, CONFIG);
		}
		long[] probeNanos = new long[iterations];
		long checksum = 0L;
		for (int index = 0; index < iterations; index++) {
			long start = System.nanoTime();
			checksum += VoxelReflectionProbe.analyze(
					LISTENER,
					room,
					CONFIG
			).surfaceHits();
			probeNanos[index] = System.nanoTime() - start;
		}

		ListenerSharedFdn fdn = new ListenerSharedFdn(48_000);
		fdn.configure(new AcousticBands(1.2, 0.9, 0.6), 0.35, 0.0);
		float[] input = new float[480];
		float[] output = new float[480];
		input[0] = 0.2F;
		for (int index = 0; index < 200; index++) {
			fdn.process(input, output, 0, input.length);
		}
		int fdnIterations = iterations * 20;
		long fdnStart = System.nanoTime();
		for (int index = 0; index < fdnIterations; index++) {
			fdn.process(input, output, 0, input.length);
			checksum += Float.floatToRawIntBits(output[index % output.length]);
		}
		long fdnNanos = System.nanoTime() - fdnStart;

		Arrays.sort(probeNanos);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid\",\"schema\":1,"
						+ "\"probe_rays\":%d,\"probe_bounces\":%d,"
						+ "\"probe_iterations\":%d,"
						+ "\"probe_p50_ms\":%.6f,"
						+ "\"probe_p95_ms\":%.6f,"
						+ "\"probe_p99_ms\":%.6f,"
						+ "\"fdn_lines\":%d,\"fdn_ns_per_sample\":%.3f,"
						+ "\"checksum\":%d}%n",
				CONFIG.rayCount(),
				CONFIG.maximumBounces(),
				iterations,
				percentile(probeNanos, 0.50) / 1.0e6,
				percentile(probeNanos, 0.95) / 1.0e6,
				percentile(probeNanos, 0.99) / 1.0e6,
				ListenerSharedFdn.DELAY_LINE_COUNT,
				(double) fdnNanos
						/ (fdnIterations * input.length),
				checksum
		);
	}

	private static long percentile(long[] sorted, double quantile) {
		int index = Math.max(
				0,
				(int) Math.ceil(quantile * sorted.length) - 1
		);
		return sorted[Math.min(index, sorted.length - 1)];
	}

	private static ReflectionVolume stoneRoom() {
		int minimumX = -6;
		int minimumY = -4;
		int minimumZ = -6;
		int maximumX = 7;
		int maximumY = 5;
		int maximumZ = 7;
		SparseMaterialSnapshot.Builder builder =
				SparseMaterialSnapshot.builder();
		DirectPathSolver.MaterialSample stone =
				DirectPathSolver.MaterialSample.full(
						AcousticMaterials.STONE
				);
		for (int x = minimumX; x < maximumX; x++) {
			for (int y = minimumY; y < maximumY; y++) {
				for (int z = minimumZ; z < maximumZ; z++) {
					if (x == minimumX || x == maximumX - 1
							|| y == minimumY || y == maximumY - 1
							|| z == minimumZ || z == maximumZ - 1) {
						builder.put(x, y, z, stone);
					}
				}
			}
		}
		return new ReflectionVolume(
				minimumX,
				minimumY,
				minimumZ,
				maximumX,
				maximumY,
				maximumZ,
				1L,
				true,
				builder.build()
		);
	}
}
