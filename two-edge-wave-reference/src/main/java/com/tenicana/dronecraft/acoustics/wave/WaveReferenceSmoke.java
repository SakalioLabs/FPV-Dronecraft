package com.tenicana.dronecraft.acoustics.wave;

import java.util.Locale;

/**
 * Small command-line smoke run. It intentionally does not claim model
 * validation; the output is useful for deterministic build and timing checks.
 */
public final class WaveReferenceSmoke {
	private WaveReferenceSmoke() {
	}

	public static void main(String[] args) {
		WaveGrid2d grid = WaveGrid2d.withCourantSafety(
				121,
				81,
				0.02,
				0.90,
				343.0,
				1.204
		);
		StaggeredGridFdtSolver solver =
				new StaggeredGridFdtSolver(grid, 12, 1_500.0);
		double[] source = RickerWavelet.generate(
				600,
				grid.timeStepSeconds(),
				1_000.0,
				0.004
		);
		GridPoint sourcePoint = new GridPoint(40, 40);
		GridPoint receiverPoint = new GridPoint(80, 40);
		long start = System.nanoTime();
		double[] trace = solver.run(sourcePoint, source, receiverPoint);
		long elapsed = System.nanoTime() - start;
		int peakSample = peakAbsoluteSample(trace);
		System.out.printf(
				Locale.ROOT,
				"grid=%dx%d steps=%d courant=%.6f peakSample=%d "
						+ "peakTimeMs=%.6f elapsedMs=%.3f%n",
				grid.widthCells(),
				grid.heightCells(),
				trace.length,
				grid.courantNumber(),
				peakSample,
				peakSample * grid.timeStepSeconds() * 1_000.0,
				elapsed / 1_000_000.0
		);
	}

	private static int peakAbsoluteSample(double[] samples) {
		int peakSample = 0;
		for (int sample = 1; sample < samples.length; sample++) {
			if (Math.abs(samples[sample]) > Math.abs(samples[peakSample])) {
				peakSample = sample;
			}
		}
		return peakSample;
	}
}
