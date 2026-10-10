package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.diffraction.DoubleEdgeGeometry;
import com.tenicana.dronecraft.acoustics.diffraction.InfiniteWedgeGeometry;
import com.tenicana.dronecraft.acoustics.diffraction.UdfaDoubleEdgeFilter;
import com.tenicana.dronecraft.acoustics.diffraction.UdfaInfiniteWedgeFilter;

/**
 * Deterministic microbenchmark for geometry-update frequency-domain oracles.
 *
 * <p>This is not an audio-sample benchmark. It measures the cost of evaluating
 * the reference transfer functions when geometry changes.</p>
 */
public final class UdfaDoubleEdgeBenchmark {
	private static final int WARMUP_ITERATIONS = 100_000;

	private UdfaDoubleEdgeBenchmark() {
	}

	public static void main(String[] args) {
		int iterations = args.length == 0 ? 1_000_000 : Integer.parseInt(args[0]);
		if (iterations <= 0) {
			throw new IllegalArgumentException("iterations must be positive");
		}
		UdfaInfiniteWedgeFilter.Parameters parameters =
				UdfaInfiniteWedgeFilter.Parameters.published2023();
		UdfaInfiniteWedgeFilter single = new UdfaInfiniteWedgeFilter(parameters);
		UdfaDoubleEdgeFilter doubleEdge = new UdfaDoubleEdgeFilter(parameters);
		DoubleEdgeGeometry doubleGeometry = brasRs5Geometry();
		InfiniteWedgeGeometry singleGeometry = brasRs5KnifeEdgeGeometry();

		runSingle(single, singleGeometry, WARMUP_ITERATIONS);
		runDouble(doubleEdge, doubleGeometry, WARMUP_ITERATIONS);

		long singleStart = System.nanoTime();
		double singleChecksum = runSingle(single, singleGeometry, iterations);
		long singleElapsed = System.nanoTime() - singleStart;
		long doubleStart = System.nanoTime();
		double doubleChecksum = runDouble(doubleEdge, doubleGeometry, iterations);
		long doubleElapsed = System.nanoTime() - doubleStart;

		double singleNs = (double) singleElapsed / iterations;
		double doubleNs = (double) doubleElapsed / iterations;
		System.out.printf(
				"iterations=%d single_ns=%.3f double_ns=%.3f ratio=%.3f "
						+ "single_checksum=%.9f double_checksum=%.9f%n",
				iterations,
				singleNs,
				doubleNs,
				doubleNs / singleNs,
				singleChecksum,
				doubleChecksum
		);
	}

	private static double runSingle(
			UdfaInfiniteWedgeFilter filter,
			InfiniteWedgeGeometry geometry,
			int iterations
	) {
		double checksum = 0.0;
		for (int index = 0; index < iterations; index++) {
			double frequency = 50.0 + index % 15_951;
			checksum += filter.pressureMagnitude(frequency, geometry);
		}
		return checksum;
	}

	private static double runDouble(
			UdfaDoubleEdgeFilter filter,
			DoubleEdgeGeometry geometry,
			int iterations
	) {
		double checksum = 0.0;
		for (int index = 0; index < iterations; index++) {
			double frequency = 50.0 + index % 15_951;
			checksum += filter.pressureMagnitude(frequency, geometry);
		}
		return checksum;
	}

	private static InfiniteWedgeGeometry brasRs5KnifeEdgeGeometry() {
		double sourceDistance = Math.hypot(3.0, 0.831);
		double receiverDistance = Math.hypot(3.025, 0.831);
		double rayAngle = Math.acos(
				(-3.0 * 3.025 + 0.831 * 0.831)
						/ (sourceDistance * receiverDistance)
		);
		return new InfiniteWedgeGeometry(
				sourceDistance,
				receiverDistance,
				0.0,
				2.0 * Math.PI - rayAngle,
				2.0 * Math.PI,
				Math.PI / 2.0,
				343.0
		);
	}

	private static DoubleEdgeGeometry brasRs5Geometry() {
		double firstEdgeX = 5.487;
		double secondEdgeX = 5.512;
		double sourceX = 2.487;
		double receiverX = 8.512;
		double vertical = 2.066 - 1.235;
		return DoubleEdgeGeometry.squareBarrier(
				Math.hypot(firstEdgeX - sourceX, vertical),
				Math.hypot(secondEdgeX - sourceX, vertical),
				Math.hypot(receiverX - firstEdgeX, vertical),
				Math.hypot(receiverX - secondEdgeX, vertical),
				Math.atan2(firstEdgeX - sourceX, vertical),
				2.0 * Math.PI - Math.atan2(secondEdgeX - sourceX, vertical),
				2.0 * Math.PI - Math.atan2(receiverX - firstEdgeX, vertical),
				Math.atan2(receiverX - secondEdgeX, vertical),
				secondEdgeX - firstEdgeX,
				Math.PI / 2.0,
				343.0
		);
	}
}
