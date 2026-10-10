package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;

import java.util.Locale;

/**
 * Dependency-free CPU reference benchmark. Results include traversal, material
 * accumulation and result construction, rather than timing only the DDA loop.
 */
public final class DdaBatchBenchmark {
	private static final int[] BATCH_SIZES = {32, 128, 512, 2_048, 8_192, 32_768};
	private static final int MAX_CELLS = 192;
	private static final DirectPathSolver.MaterialSample STONE =
			DirectPathSolver.MaterialSample.full(AcousticMaterials.STONE);

	private DdaBatchBenchmark() {
	}

	public static void main(String[] args) {
		Locale.setDefault(Locale.ROOT);
		int iterations = args.length == 0 ? 5 : Math.max(1, Integer.parseInt(args[0]));
		System.out.println("backend,batch_size,iterations,total_rays,total_ms,ns_per_ray,rays_per_second,checksum");
		for (int batchSize : BATCH_SIZES) {
			runBatch(batchSize, 2, false);
			Measurement measurement = runBatch(batchSize, iterations, true);
			double nanosPerRay = measurement.elapsedNanos / (double) measurement.rays;
			double raysPerSecond = 1_000_000_000.0 / nanosPerRay;
			System.out.printf(
					"java-cpu,%d,%d,%d,%.3f,%.1f,%.0f,%.9f%n",
					batchSize,
					iterations,
					measurement.rays,
					measurement.elapsedNanos / 1_000_000.0,
					nanosPerRay,
					raysPerSecond,
					measurement.checksum
			);
		}
	}

	private static Measurement runBatch(int batchSize, int iterations, boolean measure) {
		long started = System.nanoTime();
		double checksum = 0.0;
		for (int iteration = 0; iteration < iterations; iteration++) {
			for (int ray = 0; ray < batchSize; ray++) {
				AcousticVector source = new AcousticVector(
						0.25 + (ray & 3) * 0.01,
						64.25 + (ray & 7) * 0.02,
						0.25 + (ray & 15) * 0.01
				);
				AcousticVector listener = new AcousticVector(
						64.75,
						60.25 + ((ray * 17) & 31) * 0.25,
						-24.75 + ((ray * 31) & 63) * 0.75
				);
				DirectPathSolver.Result result = DirectPathSolver.solve(
						source,
						listener,
						(x, y, z) -> materialAt(x, y, z),
						MAX_CELLS
				);
				checksum += result.transmissionEnergyGain().mid()
						+ result.visitedCellCount() * 1.0e-9;
			}
		}
		long elapsed = System.nanoTime() - started;
		return new Measurement(
				(long) batchSize * iterations,
				measure ? elapsed : 0L,
				checksum
		);
	}

	private static DirectPathSolver.MaterialSample materialAt(int x, int y, int z) {
		int hash = x * 73_856_093 ^ y * 19_349_663 ^ z * 83_492_791;
		return (hash & 63) == 0 ? STONE : DirectPathSolver.MaterialSample.AIR;
	}

	private record Measurement(long rays, long elapsedNanos, double checksum) {
	}
}
