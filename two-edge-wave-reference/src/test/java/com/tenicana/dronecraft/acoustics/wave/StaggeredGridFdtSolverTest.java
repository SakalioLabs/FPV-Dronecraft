package com.tenicana.dronecraft.acoustics.wave;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaggeredGridFdtSolverTest {
	private static final WaveGrid2d GRID = WaveGrid2d.withCourantSafety(
			101,
			81,
			0.02,
			0.90,
			343.0,
			1.204
	);

	@Test
	void freeFieldIsReciprocalAndDeterministic() {
		StaggeredGridFdtSolver solver =
				new StaggeredGridFdtSolver(GRID, 12, 1_500.0);
		double[] source = RickerWavelet.generate(
				400,
				GRID.timeStepSeconds(),
				800.0,
				0.004
		);
		GridPoint first = new GridPoint(35, 35);
		GridPoint second = new GridPoint(65, 45);

		double[] forward = solver.run(first, source, second);
		double[] repeated = solver.run(first, source, second);
		double[] reverse = solver.run(second, source, first);

		assertArrayEquals(forward, repeated, 0.0);
		assertRelativeTraceErrorAtMost(forward, reverse, 1.0e-11);
		assertTrue(maximumAbsolute(forward) > 1.0e-6);
	}

	@Test
	void freeFieldPeakFollowsGeometricTravelTime() {
		StaggeredGridFdtSolver solver =
				new StaggeredGridFdtSolver(GRID, 12, 1_500.0);
		double centreTimeSeconds = 0.004;
		double[] source = RickerWavelet.generate(
				400,
				GRID.timeStepSeconds(),
				800.0,
				centreTimeSeconds
		);
		GridPoint sourcePoint = new GridPoint(30, 40);
		GridPoint receiverPoint = new GridPoint(70, 40);

		double[] trace = solver.run(sourcePoint, source, receiverPoint);
		double measuredPeakTimeSeconds =
				peakAbsoluteSample(trace) * GRID.timeStepSeconds();
		double expectedPeakTimeSeconds = centreTimeSeconds
				+ 40.0 * GRID.cellSizeMeters()
				/ GRID.speedOfSoundMetersPerSecond();

		assertEquals(
				expectedPeakTimeSeconds,
				measuredPeakTimeSeconds,
				4.0 * GRID.timeStepSeconds()
		);
	}

	@Test
	void solidRectangleClosesTouchedFacesAndCreatesShadow() {
		double[] source = RickerWavelet.generate(
				450,
				GRID.timeStepSeconds(),
				800.0,
				0.004
		);
		GridPoint sourcePoint = new GridPoint(30, 40);
		GridPoint receiverPoint = new GridPoint(70, 40);
		StaggeredGridFdtSolver freeField =
				new StaggeredGridFdtSolver(GRID, 12, 1_500.0);
		StaggeredGridFdtSolver barrier =
				new StaggeredGridFdtSolver(GRID, 12, 1_500.0);
		barrier.setSolidRectangle(49, 12, 52, 69);

		double freePeak = maximumAbsolute(
				freeField.run(sourcePoint, source, receiverPoint)
		);
		double shadowPeak = maximumAbsolute(
				barrier.run(sourcePoint, source, receiverPoint)
		);

		assertTrue(barrier.isSolidCell(50, 40));
		assertTrue(shadowPeak < freePeak * 0.65);
	}

	@Test
	void polynomialSpongeKeepsFirstBoundaryReturnBelowMinusFiftyDecibels() {
		WaveGrid2d smallGrid = WaveGrid2d.withCourantSafety(
				161,
				121,
				0.02,
				0.90,
				343.0,
				1.204
		);
		WaveGrid2d largeGrid = WaveGrid2d.withCourantSafety(
				321,
				241,
				0.02,
				0.90,
				343.0,
				1.204
		);
		int sampleCount = 450;
		double centreTimeSeconds = 0.004;
		double[] source = RickerWavelet.generate(
				sampleCount,
				smallGrid.timeStepSeconds(),
				1_000.0,
				centreTimeSeconds
		);
		StaggeredGridFdtSolver smallSolver =
				new StaggeredGridFdtSolver(smallGrid, 20, 20_000.0);
		StaggeredGridFdtSolver largeSolver =
				new StaggeredGridFdtSolver(largeGrid, 20, 20_000.0);

		double[] small = smallSolver.run(
				new GridPoint(80, 60),
				source,
				new GridPoint(85, 60)
		);
		double[] large = largeSolver.run(
				new GridPoint(160, 120),
				source,
				new GridPoint(165, 120)
		);
		int firstSmallBoundarySample = (int) Math.floor(
				(centreTimeSeconds + 2.0 * 1.2 / 343.0 - 0.001)
						/ smallGrid.timeStepSeconds()
		);
		int firstLargeBoundarySample = (int) Math.ceil(
				(centreTimeSeconds + 2.0 * 2.4 / 343.0 - 0.001)
						/ smallGrid.timeStepSeconds()
		);
		double directPeak = maximumAbsolute(large);
		double maximumReturn = 0.0;
		for (int sample = firstSmallBoundarySample;
				sample < Math.min(firstLargeBoundarySample, sampleCount);
				sample++) {
			maximumReturn = Math.max(
					maximumReturn,
					Math.abs(small[sample] - large[sample])
			);
		}
		double returnDecibels =
				20.0 * Math.log10(maximumReturn / directPeak);

		assertTrue(
				returnDecibels <= -50.0,
				() -> "first boundary return was " + returnDecibels + " dB"
		);
	}

	@Test
	void lineSourceSpreadingMatchesIndependentHankelOracle() {
		WaveGrid2d grid = WaveGrid2d.withCourantSafety(
				321,
				241,
				0.015,
				0.90,
				343.0,
				1.204
		);
		double frequencyHertz = 1_000.0;
		int sampleCount = (int) Math.ceil(0.040 / grid.timeStepSeconds());
		double[] source = rampedSine(
				sampleCount,
				grid.timeStepSeconds(),
				frequencyHertz,
				0.008
		);
		StaggeredGridFdtSolver solver =
				new StaggeredGridFdtSolver(grid, 30, 20_000.0);

		double[][] traces = solver.run(
				new GridPoint(100, 120),
				source,
				new GridPoint(140, 120),
				new GridPoint(180, 120)
		);
		int analysisStart = (int) Math.ceil(
				0.030 / grid.timeStepSeconds()
		);
		double nearAmplitude = harmonicAmplitude(
				traces[0],
				analysisStart,
				grid.timeStepSeconds(),
				frequencyHertz
		);
		double farAmplitude = harmonicAmplitude(
				traces[1],
				analysisStart,
				grid.timeStepSeconds(),
				frequencyHertz
		);
		double measuredRatioDb =
				20.0 * Math.log10(nearAmplitude / farAmplitude);
		// scipy.special.hankel2(0, k*r), c=343 m/s, r=0.6/1.2 m.
		double independentHankelRatioDb = 3.0069838324291496;

		assertEquals(independentHankelRatioDb, measuredRatioDb, 0.25);
	}

	@Test
	void rejectsSolidSourceAndInvalidSponge() {
		assertThrows(
				IllegalArgumentException.class,
				() -> new StaggeredGridFdtSolver(GRID, 41, 1_500.0)
		);
		StaggeredGridFdtSolver solver =
				new StaggeredGridFdtSolver(GRID, 12, 1_500.0);
		solver.setSolidCell(50, 40, true);
		assertThrows(
				IllegalArgumentException.class,
				() -> solver.run(
						new GridPoint(50, 40),
						new double[] {1.0},
						new GridPoint(60, 40)
				)
		);
	}

	private static void assertRelativeTraceErrorAtMost(
			double[] expected,
			double[] actual,
			double tolerance
	) {
		double maximumReference = maximumAbsolute(expected);
		double maximumError = 0.0;
		for (int sample = 0; sample < expected.length; sample++) {
			maximumError = Math.max(
					maximumError,
					Math.abs(expected[sample] - actual[sample])
			);
		}
		double relativeError = maximumError / maximumReference;
		assertTrue(
				relativeError <= tolerance,
				() -> "relative trace error was " + relativeError
		);
	}

	private static double maximumAbsolute(double[] samples) {
		double maximum = 0.0;
		for (double sample : samples) {
			maximum = Math.max(maximum, Math.abs(sample));
		}
		return maximum;
	}

	private static double[] rampedSine(
			int sampleCount,
			double timeStepSeconds,
			double frequencyHertz,
			double rampSeconds
	) {
		double[] result = new double[sampleCount];
		for (int sample = 0; sample < sampleCount; sample++) {
			double time = sample * timeStepSeconds;
			double envelope = time >= rampSeconds
					? 1.0
					: 0.5 * (1.0 - Math.cos(Math.PI * time / rampSeconds));
			result[sample] = envelope
					* Math.sin(2.0 * Math.PI * frequencyHertz * time);
		}
		return result;
	}

	private static double harmonicAmplitude(
			double[] samples,
			int startSample,
			double timeStepSeconds,
			double frequencyHertz
	) {
		double cosineCosine = 0.0;
		double cosineSine = 0.0;
		double sineSine = 0.0;
		double sampleCosine = 0.0;
		double sampleSine = 0.0;
		for (int sample = startSample; sample < samples.length; sample++) {
			double phase = 2.0 * Math.PI * frequencyHertz
					* sample * timeStepSeconds;
			double cosine = Math.cos(phase);
			double sine = Math.sin(phase);
			cosineCosine += cosine * cosine;
			cosineSine += cosine * sine;
			sineSine += sine * sine;
			sampleCosine += samples[sample] * cosine;
			sampleSine += samples[sample] * sine;
		}
		double determinant =
				cosineCosine * sineSine - cosineSine * cosineSine;
		double cosineCoefficient =
				(sampleCosine * sineSine - sampleSine * cosineSine)
				/ determinant;
		double sineCoefficient =
				(sampleSine * cosineCosine - sampleCosine * cosineSine)
				/ determinant;
		return Math.hypot(cosineCoefficient, sineCoefficient);
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
