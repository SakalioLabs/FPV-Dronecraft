package com.tenicana.dronecraft.acoustics.diffraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runtime-oriented serial first-order shelving approximation of a UDFA
 * frequency-domain target, following the topology of equations (18)-(20).
 *
 * <p>The paper evaluates targets beyond Nyquist for some shelf counts. This
 * digital implementation deliberately limits its design range to 0.475 times
 * the sample rate [H], matching the project's synthesis band limit. Individual
 * shelf knees are additionally guarded below Nyquist.</p>
 */
public final class UdfaSerialShelvingIirDesigner {
	private static final double MIN_GAIN = 1.0e-9;
	private static final double DESIGN_BAND_LIMIT = 0.475;

	private UdfaSerialShelvingIirDesigner() {
	}

	public static Design design(
			DiffractionFilterModel target,
			InfiniteWedgeGeometry geometry,
			double sampleRateHz,
			int shelfCount
	) {
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(geometry, "geometry");
		if (!(sampleRateHz > 0.0) || !Double.isFinite(sampleRateHz)) {
			throw new IllegalArgumentException("sampleRateHz must be positive and finite");
		}
		if (shelfCount < 1) {
			throw new IllegalArgumentException("shelfCount must be positive");
		}
		double minimumFrequency = shelfCount < 3 ? 20.0 : 10.0;
		double maximumFrequency = sampleRateHz * DESIGN_BAND_LIMIT;
		double[] targetFrequencies = logarithmicRange(
				minimumFrequency, maximumFrequency, shelfCount + 1
		);
		double[] targetGains = new double[targetFrequencies.length];
		for (int index = 0; index < targetFrequencies.length; index++) {
			targetGains[index] = Math.max(
					MIN_GAIN,
					target.pressureMagnitude(targetFrequencies[index], geometry)
			);
		}

		List<FirstOrderSection> sections = new ArrayList<>(shelfCount);
		for (int index = 0; index < shelfCount; index++) {
			double firstFrequency = targetFrequencies[index];
			double secondFrequency = targetFrequencies[index + 1];
			double centerFrequency = Math.sqrt(firstFrequency * secondFrequency);
			double firstGain = targetGains[index];
			double shelfGain = clampRatio(
					targetGains[index + 1] / firstGain
			);
			double desiredCenterRatio = clampBetween(
					target.pressureMagnitude(centerFrequency, geometry) / firstGain,
					shelfGain,
					1.0
			);
			double shelfFrequency = adjustedShelfFrequency(
					centerFrequency,
					shelfGain,
					desiredCenterRatio
			);
			sections.add(bilinearShelf(
					shelfGain,
					Math.min(shelfFrequency, sampleRateHz * 0.499),
					sampleRateHz
			));
		}

		double overallGain = fitOverallGain(
				target, geometry, sections, sampleRateHz
		);
		return new Design(sampleRateHz, overallGain, sections);
	}

	private static double fitOverallGain(
			DiffractionFilterModel target,
			InfiniteWedgeGeometry geometry,
			List<FirstOrderSection> sections,
			double sampleRateHz
	) {
		int points = 96;
		double maximum = Math.min(16_000.0, sampleRateHz * 0.45);
		double logarithmicRatioSum = 0.0;
		for (int index = 0; index < points; index++) {
			double frequency = 20.0 * Math.pow(
					maximum / 20.0,
					(double) index / (points - 1)
			);
			double desired = Math.max(
					MIN_GAIN,
					target.pressureMagnitude(frequency, geometry)
			);
			double actual = Math.max(
					MIN_GAIN,
					cascadeMagnitude(sections, frequency, sampleRateHz)
			);
			logarithmicRatioSum += Math.log(desired / actual);
		}
		return Math.exp(logarithmicRatioSum / points);
	}

	private static double adjustedShelfFrequency(
			double centerFrequency,
			double shelfGain,
			double desiredCenterRatio
	) {
		double gainSquared = shelfGain * shelfGain;
		double desiredSquared = desiredCenterRatio * desiredCenterRatio;
		double numerator = Math.max(0.0, desiredSquared - gainSquared);
		double denominator = Math.max(
				MIN_GAIN,
				shelfGain * (1.0 - desiredSquared)
		);
		double correction = 1.0 + gainSquared / 12.0;
		return centerFrequency * Math.sqrt(numerator / denominator) * correction;
	}

	private static FirstOrderSection bilinearShelf(
			double shelfGain,
			double shelfFrequency,
			double sampleRate
	) {
		double rootGain = Math.sqrt(shelfGain);
		double prewarp = 1.0 / Math.tan(Math.PI * shelfFrequency / sampleRate);
		double numerator0 = 1.0 + rootGain * prewarp;
		double numerator1 = 1.0 - rootGain * prewarp;
		double denominator0 = 1.0 + prewarp / rootGain;
		double denominator1 = 1.0 - prewarp / rootGain;
		return new FirstOrderSection(
				numerator0 / denominator0,
				numerator1 / denominator0,
				denominator1 / denominator0
		);
	}

	private static double cascadeMagnitude(
			List<FirstOrderSection> sections,
			double frequencyHz,
			double sampleRateHz
	) {
		double magnitude = 1.0;
		for (FirstOrderSection section : sections) {
			magnitude *= section.magnitude(frequencyHz, sampleRateHz);
		}
		return magnitude;
	}

	private static double[] logarithmicRange(double first, double last, int count) {
		double[] values = new double[count];
		double ratio = Math.pow(last / first, 1.0 / (count - 1));
		values[0] = first;
		for (int index = 1; index < count; index++) {
			values[index] = values[index - 1] * ratio;
		}
		values[count - 1] = last;
		return values;
	}

	private static double clampRatio(double value) {
		return Math.max(MIN_GAIN, Math.min(1.0 - 1.0e-12, value));
	}

	private static double clampBetween(double value, double minimum, double maximum) {
		return Math.max(minimum + 1.0e-12, Math.min(maximum - 1.0e-12, value));
	}

	public record Design(
			double sampleRateHz,
			double overallGain,
			List<FirstOrderSection> sections
	) {
		public Design {
			if (!(sampleRateHz > 0.0)
					|| !(overallGain >= 0.0)
					|| !Double.isFinite(sampleRateHz + overallGain)) {
				throw new IllegalArgumentException("design values must be finite");
			}
			sections = List.copyOf(sections);
		}

		public double pressureMagnitude(double frequencyHz) {
			if (!(frequencyHz >= 0.0 && frequencyHz <= sampleRateHz / 2.0)
					|| !Double.isFinite(frequencyHz)) {
				throw new IllegalArgumentException(
						"frequencyHz must be within the digital Nyquist range"
				);
			}
			return overallGain * cascadeMagnitude(
					sections, frequencyHz, sampleRateHz
			);
		}

		public Processor newProcessor() {
			return new Processor(this);
		}

		public TimeVaryingProcessor newTimeVaryingProcessor() {
			return new TimeVaryingProcessor(this);
		}
	}

	public record FirstOrderSection(double b0, double b1, double a1) {
		public FirstOrderSection {
			if (!Double.isFinite(b0 + b1 + a1) || Math.abs(a1) >= 1.0) {
				throw new IllegalArgumentException("section must be finite and stable");
			}
		}

		public double magnitude(double frequencyHz, double sampleRateHz) {
			double angle = 2.0 * Math.PI * frequencyHz / sampleRateHz;
			double cosine = Math.cos(angle);
			double sine = Math.sin(angle);
			double numeratorReal = b0 + b1 * cosine;
			double numeratorImaginary = -b1 * sine;
			double denominatorReal = 1.0 + a1 * cosine;
			double denominatorImaginary = -a1 * sine;
			return Math.hypot(numeratorReal, numeratorImaginary)
					/ Math.hypot(denominatorReal, denominatorImaginary);
		}
	}

	public static final class Processor {
		private final Design design;
		private final double[] previousInput;
		private final double[] previousOutput;

		private Processor(Design design) {
			this.design = design;
			previousInput = new double[design.sections().size()];
			previousOutput = new double[design.sections().size()];
		}

		public double process(double input) {
			double value = input * design.overallGain();
			for (int index = 0; index < design.sections().size(); index++) {
				FirstOrderSection section = design.sections().get(index);
				double output = section.b0() * value
						+ section.b1() * previousInput[index]
						- section.a1() * previousOutput[index];
				previousInput[index] = value;
				previousOutput[index] = output;
				value = output;
			}
			return value;
		}
	}

	/**
	 * Stability-preserving dual-filter crossfade for geometry updates. No
	 * coefficients are interpolated through potentially unstable values.
	 */
	public static final class TimeVaryingProcessor {
		private Processor current;
		private Processor pending;
		private int transitionSamples;
		private int transitionPosition;

		private TimeVaryingProcessor(Design initialDesign) {
			current = initialDesign.newProcessor();
		}

		public void update(Design nextDesign, int transitionSamples) {
			Objects.requireNonNull(nextDesign, "nextDesign");
			if (transitionSamples < 1) {
				throw new IllegalArgumentException("transitionSamples must be positive");
			}
			if (Math.abs(
					nextDesign.sampleRateHz() - current.design.sampleRateHz()
			) > 1.0e-9) {
				throw new IllegalArgumentException("sample rates must match");
			}
			pending = nextDesign.newProcessor();
			this.transitionSamples = transitionSamples;
			transitionPosition = 0;
		}

		public double process(double input) {
			if (pending == null) {
				return current.process(input);
			}
			double currentOutput = current.process(input);
			double pendingOutput = pending.process(input);
			double blend = (double) transitionPosition / transitionSamples;
			double output = currentOutput + (pendingOutput - currentOutput) * blend;
			transitionPosition++;
			if (transitionPosition >= transitionSamples) {
				current = pending;
				pending = null;
			}
			return output;
		}

		public boolean transitioning() {
			return pending != null;
		}
	}
}
