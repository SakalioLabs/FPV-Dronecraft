package com.tenicana.dronecraft.acoustics.propagation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NullEarlyReflectionRendererTest {
	private static final double EPSILON = 1.0e-10;
	private static final double PAN_CENTER = Math.sqrt(0.5);

	@Test
	void integerDelayImpulseReconstructsAcrossThreeBands() {
		NullEarlyReflectionRenderer renderer =
				new NullEarlyReflectionRenderer(64);
		submitSingle(renderer, 4.0, 1.0, 0.0, 1);
		double[] input = new double[16];
		double[] left = new double[16];
		double[] right = new double[16];
		input[0] = 1.0;

		renderer.render(input, 0, left, right, 0, input.length);

		assertEquals(PAN_CENTER, left[4], EPSILON);
		assertEquals(PAN_CENTER, right[4], EPSILON);
		for (int index = 0; index < input.length; index++) {
			if (index != 4) {
				assertEquals(0.0, left[index], EPSILON);
				assertEquals(0.0, right[index], EPSILON);
			}
		}
	}

	@Test
	void halfSampleDelayUsesLinearFractionalInterpolation() {
		NullEarlyReflectionRenderer renderer =
				new NullEarlyReflectionRenderer(64);
		submitSingle(renderer, 4.5, 1.0, 0.0, 1);
		double[] input = new double[16];
		double[] left = new double[16];
		double[] right = new double[16];
		input[0] = 1.0;

		renderer.render(input, 0, left, right, 0, input.length);

		assertEquals(PAN_CENTER * 0.5, left[4], EPSILON);
		assertEquals(PAN_CENTER * 0.5, left[5], EPSILON);
		assertEquals(left[4], right[4], EPSILON);
		assertEquals(left[5], right[5], EPSILON);
	}

	@Test
	void cubicLagrangeHalfSampleUsesFourPointKernel() {
		NullEarlyReflectionRenderer renderer =
				new NullEarlyReflectionRenderer(
						64,
						NullEarlyReflectionRenderer.Interpolation
								.LAGRANGE_CUBIC
				);
		submitSingle(renderer, 4.5, 1.0, 0.0, 1);
		double[] input = new double[16];
		double[] left = new double[16];
		double[] right = new double[16];
		input[0] = 1.0;

		renderer.render(input, 0, left, right, 0, input.length);

		assertEquals(PAN_CENTER * -0.0625, left[3], EPSILON);
		assertEquals(PAN_CENTER * 0.5625, left[4], EPSILON);
		assertEquals(PAN_CENTER * 0.5625, left[5], EPSILON);
		assertEquals(PAN_CENTER * -0.0625, left[6], EPSILON);
		for (int index = 0; index < input.length; index++) {
			assertEquals(left[index], right[index], EPSILON);
		}
	}

	@Test
	void highBandSincFallsBackToCubicBelowFourSamples() {
		NullEarlyReflectionRenderer cubic =
				new NullEarlyReflectionRenderer(
						64,
						NullEarlyReflectionRenderer.Interpolation
								.LAGRANGE_CUBIC
				);
		NullEarlyReflectionRenderer hybrid =
				new NullEarlyReflectionRenderer(
						64,
						NullEarlyReflectionRenderer.Interpolation
								.HIGH_BAND_KAISER_SINC8
				);
		submitSingle(cubic, 3.5, 1.0, 0.0, 1);
		submitSingle(hybrid, 3.5, 1.0, 0.0, 1);
		double[] input = sine(128, 12_000.0);
		double[] cubicLeft = new double[input.length];
		double[] cubicRight = new double[input.length];
		double[] hybridLeft = new double[input.length];
		double[] hybridRight = new double[input.length];

		cubic.render(
				input, 0, cubicLeft, cubicRight, 0, input.length
		);
		hybrid.render(
				input, 0, hybridLeft, hybridRight, 0, input.length
		);

		assertTrue(Arrays.equals(cubicLeft, hybridLeft));
		assertTrue(Arrays.equals(cubicRight, hybridRight));
	}

	@Test
	void movingDelayAndTopologyCrossfadeRemainFiniteAndContinuous() {
		NullEarlyReflectionRenderer renderer =
				new NullEarlyReflectionRenderer(256);
		EarlyReflectionClusterSlew slew =
				new EarlyReflectionClusterSlew(8.0, 2_400);
		EarlyReflectionClusterSlew.Frame frame =
				new EarlyReflectionClusterSlew.Frame();
		Input input = Input.single(20, 0.25, 0.0);
		slew.update(input, frame);
		renderer.submit(frame);
		double[] prime = sine(4_096, 997.0);
		double[] left = new double[prime.length];
		double[] right = new double[prime.length];
		renderer.render(prime, 0, left, right, 0, prime.length);

		input.arrival[0] = 24;
		slew.update(input, frame);
		renderer.submit(frame);
		double[] moving = sine(2_400, 997.0);
		left = new double[moving.length];
		right = new double[moving.length];
		renderer.render(moving, 0, left, right, 0, moving.length);
		assertFiniteAndContinuous(left, 0.25);
		assertFiniteAndContinuous(right, 0.25);

		input.arrival[0] = 100;
		slew.update(input, frame);
		assertEquals(2, frame.slotCount());
		renderer.submit(frame);
		double[] switched = sine(2_400, 997.0);
		left = new double[switched.length];
		right = new double[switched.length];
		renderer.render(
				switched, 0, left, right, 0, switched.length
		);
		assertFiniteAndContinuous(left, 0.25);
		assertFiniteAndContinuous(right, 0.25);
	}

	@Test
	void incompleteFadeRetiresWetOutput() {
		NullEarlyReflectionRenderer renderer =
				new NullEarlyReflectionRenderer(64);
		EarlyReflectionClusterSlew slew =
				new EarlyReflectionClusterSlew(96.0, 32);
		EarlyReflectionClusterSlew.Frame frame =
				new EarlyReflectionClusterSlew.Frame();
		Input input = Input.single(4, 0.25, 0.0);
		slew.update(input, frame);
		renderer.submit(frame);
		double[] ones = new double[128];
		Arrays.fill(ones, 1.0);
		double[] left = new double[128];
		double[] right = new double[128];
		renderer.render(ones, 0, left, right, 0, 64);

		input.complete = false;
		input.count = 0;
		slew.update(input, frame);
		renderer.submit(frame);
		renderer.render(ones, 64, left, right, 64, 32);
		slew.update(input, frame);
		assertEquals(0, frame.slotCount());
		renderer.submit(frame);
		renderer.render(ones, 96, left, right, 96, 32);

		for (int index = 96; index < 128; index++) {
			assertEquals(0.0, left[index], EPSILON);
			assertEquals(0.0, right[index], EPSILON);
		}
	}

	@Test
	void endpointEnergyAboveLedgerBudgetIsRejected() {
		NullEarlyReflectionRenderer renderer =
				new NullEarlyReflectionRenderer(64);
		EarlyReflectionClusterSlew slew =
				new EarlyReflectionClusterSlew(96.0, 32);
		EarlyReflectionClusterSlew.Frame frame =
				new EarlyReflectionClusterSlew.Frame();
		Input input = new Input(2);
		for (int index = 0; index < 2; index++) {
			input.arrival[index] = 4 + index * 8;
			input.low[index] = 0.64;
			input.mid[index] = 0.64;
			input.high[index] = 0.64;
			input.z[index] = 1.0;
		}
		slew.update(input, frame);

		assertThrows(
				IllegalArgumentException.class,
				() -> renderer.submit(frame)
		);
	}

	private static void submitSingle(
			NullEarlyReflectionRenderer renderer,
			double delay,
			double energy,
			double directionX,
			int rampSamples
	) {
		EarlyReflectionClusterSlew slew =
				new EarlyReflectionClusterSlew(96.0, rampSamples);
		EarlyReflectionClusterSlew.Frame frame =
				new EarlyReflectionClusterSlew.Frame();
		slew.update(Input.single(delay, energy, directionX), frame);
		renderer.submit(frame);
	}

	private static double[] sine(int length, double frequency) {
		double[] output = new double[length];
		for (int index = 0; index < length; index++) {
			output[index] = 0.2 * Math.sin(
					2.0 * Math.PI * frequency * index
							/ NullEarlyReflectionRenderer.SAMPLE_RATE_HZ
			);
		}
		return output;
	}

	private static void assertFiniteAndContinuous(
			double[] samples,
			double maximumStep
	) {
		for (int index = 0; index < samples.length; index++) {
			assertTrue(Double.isFinite(samples[index]));
			if (index > 0) {
				assertTrue(
						Math.abs(samples[index] - samples[index - 1])
								<= maximumStep
				);
			}
		}
	}

	private static final class Input
			implements EarlyReflectionClusterSlew.Input {
		private boolean complete = true;
		private int count;
		private final double[] arrival;
		private final double[] low;
		private final double[] mid;
		private final double[] high;
		private final double[] x;
		private final double[] y;
		private final double[] z;

		private Input(int count) {
			this.count = count;
			arrival = new double[count];
			low = new double[count];
			mid = new double[count];
			high = new double[count];
			x = new double[count];
			y = new double[count];
			z = new double[count];
		}

		private static Input single(
				double arrival,
				double energy,
				double directionX
		) {
			Input input = new Input(1);
			input.arrival[0] = arrival;
			input.low[0] = energy;
			input.mid[0] = energy;
			input.high[0] = energy;
			input.x[0] = directionX;
			input.z[0] = 1.0;
			return input;
		}

		@Override
		public boolean complete() {
			return complete;
		}

		@Override
		public int clusterCount() {
			return count;
		}

		@Override
		public double clusterArrivalSamples(int index) {
			return arrival[index];
		}

		@Override
		public double clusterLow(int index) {
			return low[index];
		}

		@Override
		public double clusterMid(int index) {
			return mid[index];
		}

		@Override
		public double clusterHigh(int index) {
			return high[index];
		}

		@Override
		public double clusterDirectionX(int index) {
			return x[index];
		}

		@Override
		public double clusterDirectionY(int index) {
			return y[index];
		}

		@Override
		public double clusterDirectionZ(int index) {
			return z[index];
		}
	}
}
