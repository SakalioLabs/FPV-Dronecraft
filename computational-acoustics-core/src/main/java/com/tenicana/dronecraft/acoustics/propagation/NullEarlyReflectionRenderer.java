package com.tenicana.dronecraft.acoustics.propagation;

import java.util.Arrays;
import java.util.Objects;

/**
 * Pure-memory stereo early-reflection renderer. It owns no audio device,
 * thread, Minecraft object or native resource.
 */
public final class NullEarlyReflectionRenderer {
	public static final int SAMPLE_RATE_HZ = 48_000;
	public static final int DEFAULT_MAXIMUM_DELAY_SAMPLES = 16_384;
	private static final int SLOT_COUNT =
			EarlyReflectionClusterSlew.MAXIMUM_RENDER_SLOTS;
	private static final double ENERGY_EPSILON = 1.0e-9;
	private static final double LOW_ALPHA =
			Math.exp(-2.0 * Math.PI * 500.0 / SAMPLE_RATE_HZ);
	private static final double MID_ALPHA =
			Math.exp(-2.0 * Math.PI * 4_000.0 / SAMPLE_RATE_HZ);
	private static final int SINC_PHASE_INTERVALS = 1_024;
	private static final int SINC_TAPS = 8;
	private static final int SINC_FIRST_NODE = -3;
	private static final double SINC_KAISER_BETA = 2.0;
	private static final double[] SINC8_PHASE_TABLE =
			createSinc8PhaseTable();

	private final int maximumDelaySamples;
	private final Interpolation interpolation;
	private final int ringMask;
	private final double[] lowRing;
	private final double[] midRing;
	private final double[] highRing;
	private final boolean[] active = new boolean[SLOT_COUNT];
	private final boolean[] seen = new boolean[SLOT_COUNT];
	private final double[] startDelay = new double[SLOT_COUNT];
	private final double[] targetDelay = new double[SLOT_COUNT];
	private final double[] startLow = new double[SLOT_COUNT];
	private final double[] targetLow = new double[SLOT_COUNT];
	private final double[] startMid = new double[SLOT_COUNT];
	private final double[] targetMid = new double[SLOT_COUNT];
	private final double[] startHigh = new double[SLOT_COUNT];
	private final double[] targetHigh = new double[SLOT_COUNT];
	private final double[] startDirectionX = new double[SLOT_COUNT];
	private final double[] targetDirectionX = new double[SLOT_COUNT];
	private final int[] rampSamples = new int[SLOT_COUNT];
	private final int[] rampPosition = new int[SLOT_COUNT];
	private long writeCounter;
	private double lowState;
	private double broadLowState;

	public NullEarlyReflectionRenderer() {
		this(DEFAULT_MAXIMUM_DELAY_SAMPLES, Interpolation.LINEAR);
	}

	public NullEarlyReflectionRenderer(int maximumDelaySamples) {
		this(maximumDelaySamples, Interpolation.LINEAR);
	}

	public NullEarlyReflectionRenderer(
			int maximumDelaySamples,
			Interpolation interpolation
	) {
		if (maximumDelaySamples < 4 || maximumDelaySamples > SAMPLE_RATE_HZ) {
			throw new IllegalArgumentException(
					"maximum delay must be in [4, 48000] samples"
			);
		}
		this.maximumDelaySamples = maximumDelaySamples;
		this.interpolation = Objects.requireNonNull(
				interpolation,
				"interpolation"
		);
		int ringSize = 1;
		while (ringSize < maximumDelaySamples + 4) {
			ringSize <<= 1;
		}
		ringMask = ringSize - 1;
		lowRing = new double[ringSize];
		midRing = new double[ringSize];
		highRing = new double[ringSize];
	}

	public void submit(EarlyReflectionClusterSlew.Frame frame) {
		Objects.requireNonNull(frame, "frame");
		Arrays.fill(seen, false);
		double startLowEnergy = 0.0;
		double targetLowEnergy = 0.0;
		double startMidEnergy = 0.0;
		double targetMidEnergy = 0.0;
		double startHighEnergy = 0.0;
		double targetHighEnergy = 0.0;
		for (int index = 0; index < frame.slotCount(); index++) {
			int slot = frame.slot(index);
			if (slot < 0 || slot >= SLOT_COUNT || seen[slot]) {
				throw new IllegalArgumentException(
						"control frame contains invalid or duplicate slot"
				);
			}
			seen[slot] = true;
			double nextStartDelay = checkedDelay(
					frame.startDelaySamples(index)
			);
			double nextTargetDelay = checkedDelay(
					frame.targetDelaySamples(index)
			);
			double nextStartLow = checkedGain(frame.startLow(index));
			double nextTargetLow = checkedGain(frame.targetLow(index));
			double nextStartMid = checkedGain(frame.startMid(index));
			double nextTargetMid = checkedGain(frame.targetMid(index));
			double nextStartHigh = checkedGain(frame.startHigh(index));
			double nextTargetHigh = checkedGain(frame.targetHigh(index));
			double nextStartDirectionX = checkedDirection(
					frame.startDirectionX(index)
			);
			double nextTargetDirectionX = checkedDirection(
					frame.targetDirectionX(index)
			);
			startLowEnergy += nextStartLow * nextStartLow;
			targetLowEnergy += nextTargetLow * nextTargetLow;
			startMidEnergy += nextStartMid * nextStartMid;
			targetMidEnergy += nextTargetMid * nextTargetMid;
			startHighEnergy += nextStartHigh * nextStartHigh;
			targetHighEnergy += nextTargetHigh * nextTargetHigh;
		}
		requireEnergy("low", startLowEnergy, targetLowEnergy);
		requireEnergy("mid", startMidEnergy, targetMidEnergy);
		requireEnergy("high", startHighEnergy, targetHighEnergy);
		for (int index = 0; index < frame.slotCount(); index++) {
			int slot = frame.slot(index);
			startDelay[slot] = frame.startDelaySamples(index);
			targetDelay[slot] = frame.targetDelaySamples(index);
			startLow[slot] = frame.startLow(index);
			targetLow[slot] = frame.targetLow(index);
			startMid[slot] = frame.startMid(index);
			targetMid[slot] = frame.targetMid(index);
			startHigh[slot] = frame.startHigh(index);
			targetHigh[slot] = frame.targetHigh(index);
			startDirectionX[slot] = checkedDirection(
					frame.startDirectionX(index)
			);
			targetDirectionX[slot] = checkedDirection(
					frame.targetDirectionX(index)
			);
			rampSamples[slot] = frame.rampSamples();
			rampPosition[slot] = 0;
			active[slot] = true;
		}
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			if (!seen[slot]) {
				active[slot] = false;
			}
		}
	}

	public void render(
			double[] input,
			int inputOffset,
			double[] outputLeft,
			double[] outputRight,
			int outputOffset,
			int length
	) {
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(outputLeft, "outputLeft");
		Objects.requireNonNull(outputRight, "outputRight");
		if (inputOffset < 0
				|| outputOffset < 0
				|| length < 0
				|| inputOffset + length > input.length
				|| outputOffset + length > outputLeft.length
				|| outputOffset + length > outputRight.length) {
			throw new IndexOutOfBoundsException("render range");
		}
		boolean hybridHighBand =
				interpolation == Interpolation.HIGH_BAND_KAISER_SINC8;
		for (int frame = 0; frame < length; frame++) {
			double sample = input[inputOffset + frame];
			if (!Double.isFinite(sample)) {
				throw new IllegalArgumentException(
						"input samples must be finite"
				);
			}
			lowState = (1.0 - LOW_ALPHA) * sample
					+ LOW_ALPHA * lowState;
			broadLowState = (1.0 - MID_ALPHA) * sample
					+ MID_ALPHA * broadLowState;
			double lowSample = lowState;
			double midSample = broadLowState - lowState;
			double highSample = sample - broadLowState;
			int writeIndex = (int) writeCounter & ringMask;
			lowRing[writeIndex] = lowSample;
			midRing[writeIndex] = midSample;
			highRing[writeIndex] = highSample;
			double left = 0.0;
			double right = 0.0;
			for (int slot = 0; slot < SLOT_COUNT; slot++) {
				if (!active[slot]) {
					continue;
				}
				int position = rampPosition[slot];
				int duration = rampSamples[slot];
				double t = Math.min(
						1.0,
						(position + 1.0) / duration
				);
				double delay = interpolate(
						startDelay[slot],
						targetDelay[slot],
						t
				);
				double wet = read(lowRing, delay, false)
						* interpolate(
								startLow[slot],
								targetLow[slot],
								t
						)
						+ read(midRing, delay, false)
						* interpolate(
								startMid[slot],
								targetMid[slot],
								t
						)
						+ read(highRing, delay, hybridHighBand)
						* interpolate(
								startHigh[slot],
								targetHigh[slot],
								t
						);
				double directionX = Math.max(
						-1.0,
						Math.min(
								1.0,
								interpolate(
										startDirectionX[slot],
										targetDirectionX[slot],
										t
								)
						)
				);
				left += wet * Math.sqrt(0.5 * (1.0 - directionX));
				right += wet * Math.sqrt(0.5 * (1.0 + directionX));
				if (position < duration) {
					rampPosition[slot] = position + 1;
				}
				if (rampPosition[slot] >= duration
						&& targetLow[slot] == 0.0
						&& targetMid[slot] == 0.0
						&& targetHigh[slot] == 0.0) {
					active[slot] = false;
				}
			}
			if (!Double.isFinite(left) || !Double.isFinite(right)) {
				throw new IllegalStateException(
						"early renderer produced non-finite output"
				);
			}
			outputLeft[outputOffset + frame] = left;
			outputRight[outputOffset + frame] = right;
			writeCounter++;
		}
	}

	public void reset() {
		Arrays.fill(lowRing, 0.0);
		Arrays.fill(midRing, 0.0);
		Arrays.fill(highRing, 0.0);
		Arrays.fill(active, false);
		writeCounter = 0L;
		lowState = 0.0;
		broadLowState = 0.0;
	}

	private double read(
			double[] ring,
			double delay,
			boolean highBand
	) {
		double position = writeCounter - delay;
		long lower = (long) Math.floor(position);
		double fraction = position - lower;
		if (highBand && delay >= 4.0) {
			int phase = (int) Math.floor(
					fraction * SINC_PHASE_INTERVALS + 0.5
			);
			int tableOffset = phase * SINC_TAPS;
			int first = (int) (lower + SINC_FIRST_NODE);
			return ring[first & ringMask]
							* SINC8_PHASE_TABLE[tableOffset]
					+ ring[(first + 1) & ringMask]
							* SINC8_PHASE_TABLE[tableOffset + 1]
					+ ring[(first + 2) & ringMask]
							* SINC8_PHASE_TABLE[tableOffset + 2]
					+ ring[(first + 3) & ringMask]
							* SINC8_PHASE_TABLE[tableOffset + 3]
					+ ring[(first + 4) & ringMask]
							* SINC8_PHASE_TABLE[tableOffset + 4]
					+ ring[(first + 5) & ringMask]
							* SINC8_PHASE_TABLE[tableOffset + 5]
					+ ring[(first + 6) & ringMask]
							* SINC8_PHASE_TABLE[tableOffset + 6]
					+ ring[(first + 7) & ringMask]
							* SINC8_PHASE_TABLE[tableOffset + 7];
		}
		if ((interpolation == Interpolation.LAGRANGE_CUBIC
				|| interpolation
						== Interpolation.HIGH_BAND_KAISER_SINC8)
				&& delay >= 2.0) {
			double minusOne =
					ring[(int) (lower - 1L) & ringMask];
			double zero = ring[(int) lower & ringMask];
			double one = ring[(int) (lower + 1L) & ringMask];
			double two = ring[(int) (lower + 2L) & ringMask];
			double hMinusOne = -fraction
					* (fraction - 1.0)
					* (fraction - 2.0) / 6.0;
			double hZero = (fraction + 1.0)
					* (fraction - 1.0)
					* (fraction - 2.0) / 2.0;
			double hOne = -(fraction + 1.0)
					* fraction
					* (fraction - 2.0) / 2.0;
			double hTwo = (fraction + 1.0)
					* fraction
					* (fraction - 1.0) / 6.0;
			return minusOne * hMinusOne
					+ zero * hZero
					+ one * hOne
					+ two * hTwo;
		}
		double older = ring[(int) lower & ringMask];
		double newer = ring[(int) (lower + 1L) & ringMask];
		return older + (newer - older) * fraction;
	}

	private static double[] createSinc8PhaseTable() {
		double[] table = new double[
				(SINC_PHASE_INTERVALS + 1) * SINC_TAPS
		];
		double denominator = besselI0(SINC_KAISER_BETA);
		for (int phase = 0; phase <= SINC_PHASE_INTERVALS; phase++) {
			double fraction =
					(double) phase / SINC_PHASE_INTERVALS;
			double sum = 0.0;
			int offset = phase * SINC_TAPS;
			for (int tap = 0; tap < SINC_TAPS; tap++) {
				double node = SINC_FIRST_NODE + tap;
				double distance = node - fraction;
				double sinc = distance == 0.0
						? 1.0
						: Math.sin(Math.PI * distance)
								/ (Math.PI * distance);
				double radius = distance / 4.0;
				double window = Math.abs(radius) <= 1.0
						? besselI0(
								SINC_KAISER_BETA
										* Math.sqrt(
												Math.max(
														0.0,
														1.0
																- radius
																* radius
												)
										)
						) / denominator
						: 0.0;
				double coefficient = sinc * window;
				table[offset + tap] = coefficient;
				sum += coefficient;
			}
			for (int tap = 0; tap < SINC_TAPS; tap++) {
				table[offset + tap] /= sum;
			}
		}
		return table;
	}

	private static double besselI0(double value) {
		double scaled = value * value * 0.25;
		double term = 1.0;
		double sum = 1.0;
		for (int order = 1; order <= 24; order++) {
			term *= scaled / (order * (double) order);
			sum += term;
		}
		return sum;
	}

	private double checkedDelay(double delay) {
		if (!Double.isFinite(delay)
				|| delay < 0.0
				|| delay > maximumDelaySamples - 2.0) {
			throw new IllegalArgumentException(
					"delay outside renderer history"
			);
		}
		return delay;
	}

	private static double checkedGain(double gain) {
		if (!Double.isFinite(gain) || gain < 0.0 || gain > 1.0) {
			throw new IllegalArgumentException(
					"band amplitude must be in [0,1]"
			);
		}
		return gain;
	}

	private static double checkedDirection(double direction) {
		if (!Double.isFinite(direction)) {
			throw new IllegalArgumentException(
					"direction must be finite"
			);
		}
		return Math.max(-1.0, Math.min(1.0, direction));
	}

	private static void requireEnergy(
			String band,
			double start,
			double target
	) {
		if (start > 1.0 + ENERGY_EPSILON
				|| target > 1.0 + ENERGY_EPSILON) {
			throw new IllegalArgumentException(
					band + " early energy exceeds ledger budget"
			);
		}
	}

	private static double interpolate(
			double start,
			double target,
			double t
	) {
		return start + (target - start) * t;
	}

	public enum Interpolation {
		LINEAR,
		LAGRANGE_CUBIC,
		HIGH_BAND_KAISER_SINC8
	}
}
