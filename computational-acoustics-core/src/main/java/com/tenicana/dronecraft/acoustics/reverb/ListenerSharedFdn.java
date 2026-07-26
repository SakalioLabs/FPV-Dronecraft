package com.tenicana.dronecraft.acoustics.reverb;

import com.tenicana.dronecraft.acoustics.AcousticBands;

import java.util.Arrays;
import java.util.Objects;

/**
 * Eight-line, listener-owned feedback delay network for a pre-mixed mono
 * reverb bus. One instance is intended per listener, never per sound source.
 *
 * <p>The delay lengths and crossover frequencies are research defaults [H].
 * They are deliberately exposed through diagnostics and are not claimed as
 * measured room parameters.</p>
 */
public final class ListenerSharedFdn {
	public static final int DELAY_LINE_COUNT = 8;
	public static final double LOW_CROSSOVER_HZ = 700.0;
	public static final double HIGH_CROSSOVER_HZ = 4_000.0;
	private static final int REFERENCE_SAMPLE_RATE = 48_000;
	private static final int[] REFERENCE_DELAYS = {
			1_423, 1_613, 1_789, 1_999, 2_131, 2_347, 2_539, 2_741
	};
	private static final double INVERSE_SQRT_LINE_COUNT =
			1.0 / Math.sqrt(DELAY_LINE_COUNT);
	private static final double MAXIMUM_RT60_SECONDS = 20.0;

	private final int sampleRate;
	private final double[][] delayLines = new double[DELAY_LINE_COUNT][];
	private final int[] writeIndices = new int[DELAY_LINE_COUNT];
	private final double[] lineOutput = new double[DELAY_LINE_COUNT];
	private final double[] feedback = new double[DELAY_LINE_COUNT];
	private final double[] lowState = new double[DELAY_LINE_COUNT];
	private final double[] highState = new double[DELAY_LINE_COUNT];
	private final double[][] currentBandGain =
			new double[DELAY_LINE_COUNT][3];
	private final double[][] targetBandGain =
			new double[DELAY_LINE_COUNT][3];
	private final double lowCoefficient;
	private final double highCoefficient;
	private double currentWetGain;
	private double targetWetGain;
	private double parameterSmoothing;

	public ListenerSharedFdn(int sampleRate) {
		if (sampleRate < 8_000 || sampleRate > 192_000) {
			throw new IllegalArgumentException(
					"sampleRate must be in [8000, 192000]"
			);
		}
		this.sampleRate = sampleRate;
		for (int line = 0; line < DELAY_LINE_COUNT; line++) {
			int delay = Math.max(
					3,
					(int) Math.round(
							REFERENCE_DELAYS[line]
									* (double) sampleRate
									/ REFERENCE_SAMPLE_RATE
					)
			);
			if ((delay & 1) == 0) {
				delay++;
			}
			delayLines[line] = new double[delay];
		}
		lowCoefficient = onePoleCoefficient(
				LOW_CROSSOVER_HZ,
				sampleRate
		);
		highCoefficient = onePoleCoefficient(
				Math.min(HIGH_CROSSOVER_HZ, sampleRate * 0.4),
				sampleRate
		);
		configure(AcousticBands.SILENT, 0.0, 0.0);
	}

	/**
	 * Changes decay and wet-send targets without clearing the existing tail.
	 */
	public void configure(
			AcousticBands rt60Seconds,
			double wetGain,
			double transitionSeconds
	) {
		Objects.requireNonNull(rt60Seconds, "rt60Seconds");
		if (!Double.isFinite(wetGain) || wetGain < 0.0 || wetGain > 1.0) {
			throw new IllegalArgumentException("wetGain must be in [0, 1]");
		}
		if (!Double.isFinite(transitionSeconds)
				|| transitionSeconds < 0.0 || transitionSeconds > 10.0) {
			throw new IllegalArgumentException(
					"transitionSeconds must be in [0, 10]"
			);
		}
		requireRt60(rt60Seconds.low(), "low RT60");
		requireRt60(rt60Seconds.mid(), "mid RT60");
		requireRt60(rt60Seconds.high(), "high RT60");

		for (int line = 0; line < DELAY_LINE_COUNT; line++) {
			double delaySeconds =
					(double) delayLines[line].length / sampleRate;
			targetBandGain[line][0] = feedbackGain(
					delaySeconds,
					rt60Seconds.low()
			);
			targetBandGain[line][1] = feedbackGain(
					delaySeconds,
					rt60Seconds.mid()
			);
			targetBandGain[line][2] = feedbackGain(
					delaySeconds,
					rt60Seconds.high()
			);
		}
		targetWetGain = wetGain;
		if (transitionSeconds == 0.0) {
			for (int line = 0; line < DELAY_LINE_COUNT; line++) {
				System.arraycopy(
						targetBandGain[line],
						0,
						currentBandGain[line],
						0,
						3
				);
			}
			currentWetGain = targetWetGain;
			parameterSmoothing = 0.0;
		} else {
			parameterSmoothing = Math.exp(
					-1.0 / (transitionSeconds * sampleRate)
			);
		}
	}

	/**
	 * Renders only the wet bus. Dry signal routing remains the caller's job.
	 */
	public void process(
			float[] mixedInput,
			float[] wetOutput,
			int offset,
			int length
	) {
		Objects.requireNonNull(mixedInput, "mixedInput");
		Objects.requireNonNull(wetOutput, "wetOutput");
		if (offset < 0 || length < 0
				|| offset + length > mixedInput.length
				|| offset + length > wetOutput.length) {
			throw new IndexOutOfBoundsException("invalid audio buffer range");
		}
		for (int sample = offset; sample < offset + length; sample++) {
			smoothParameters();
			for (int line = 0; line < DELAY_LINE_COUNT; line++) {
				lineOutput[line] =
						delayLines[line][writeIndices[line]];
				feedback[line] = damp(line, lineOutput[line]);
			}
			hadamardInPlace(feedback);
			double input = mixedInput[sample];
			double wet = 0.0;
			for (int line = 0; line < DELAY_LINE_COUNT; line++) {
				double injection = (line & 1) == 0
						? INVERSE_SQRT_LINE_COUNT
						: -INVERSE_SQRT_LINE_COUNT;
				delayLines[line][writeIndices[line]] =
						input * injection
								+ feedback[line]
										* INVERSE_SQRT_LINE_COUNT;
				writeIndices[line]++;
				if (writeIndices[line] == delayLines[line].length) {
					writeIndices[line] = 0;
				}
				double outputSign = (line & 2) == 0 ? 1.0 : -1.0;
				wet += lineOutput[line] * outputSign;
			}
			wetOutput[sample] = (float) (
					wet * INVERSE_SQRT_LINE_COUNT * currentWetGain
			);
		}
	}

	public void reset() {
		for (double[] delayLine : delayLines) {
			Arrays.fill(delayLine, 0.0);
		}
		Arrays.fill(writeIndices, 0);
		Arrays.fill(lineOutput, 0.0);
		Arrays.fill(feedback, 0.0);
		Arrays.fill(lowState, 0.0);
		Arrays.fill(highState, 0.0);
	}

	public int sampleRate() {
		return sampleRate;
	}

	public int[] diagnosticDelaySamples() {
		return Arrays.stream(delayLines).mapToInt(line -> line.length).toArray();
	}

	public double diagnosticCurrentWetGain() {
		return currentWetGain;
	}

	public double diagnosticTargetWetGain() {
		return targetWetGain;
	}

	public AcousticBands diagnosticMeanCurrentFeedbackGain() {
		return meanBandGain(currentBandGain);
	}

	public AcousticBands diagnosticMeanTargetFeedbackGain() {
		return meanBandGain(targetBandGain);
	}

	private static AcousticBands meanBandGain(double[][] gains) {
		double low = 0.0;
		double mid = 0.0;
		double high = 0.0;
		for (double[] line : gains) {
			low += line[0];
			mid += line[1];
			high += line[2];
		}
		return new AcousticBands(
				low / DELAY_LINE_COUNT,
				mid / DELAY_LINE_COUNT,
				high / DELAY_LINE_COUNT
		);
	}

	private double damp(int line, double input) {
		lowState[line] += lowCoefficient * (input - lowState[line]);
		highState[line] += highCoefficient * (input - highState[line]);
		double low = lowState[line];
		double mid = highState[line] - lowState[line];
		double high = input - highState[line];
		return low * currentBandGain[line][0]
				+ mid * currentBandGain[line][1]
				+ high * currentBandGain[line][2];
	}

	private void smoothParameters() {
		if (parameterSmoothing == 0.0) {
			return;
		}
		double update = 1.0 - parameterSmoothing;
		for (int line = 0; line < DELAY_LINE_COUNT; line++) {
			for (int band = 0; band < 3; band++) {
				currentBandGain[line][band] += update
						* (targetBandGain[line][band]
								- currentBandGain[line][band]);
			}
		}
		currentWetGain += update * (targetWetGain - currentWetGain);
	}

	private static void hadamardInPlace(double[] values) {
		for (int width = 1; width < values.length; width *= 2) {
			for (int base = 0; base < values.length; base += width * 2) {
				for (int index = 0; index < width; index++) {
					double first = values[base + index];
					double second = values[base + index + width];
					values[base + index] = first + second;
					values[base + index + width] = first - second;
				}
			}
		}
	}

	private static double feedbackGain(
			double delaySeconds,
			double rt60Seconds
	) {
		if (rt60Seconds == 0.0) {
			return 0.0;
		}
		return Math.pow(10.0, -3.0 * delaySeconds / rt60Seconds);
	}

	private static double onePoleCoefficient(
			double cutoffHz,
			double sampleRate
	) {
		return 1.0 - Math.exp(-2.0 * Math.PI * cutoffHz / sampleRate);
	}

	private static void requireRt60(double value, String name) {
		if (!Double.isFinite(value)
				|| value < 0.0 || value > MAXIMUM_RT60_SECONDS) {
			throw new IllegalArgumentException(
					name + " must be in [0, 20] seconds"
			);
		}
	}
}
