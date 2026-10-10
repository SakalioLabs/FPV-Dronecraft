package com.tenicana.dronecraft.acoustics;

/** Single numerical implementation shared by queued and prepared oscillator storage. */
final class PhaseContinuousAudioKernel {
	private static final double TWO_PI = 2.0 * Math.PI;
	static final double SILENCE_THRESHOLD = 1.0e-7;
	private static final double OUTPUT_GAIN = 0.42;
	private static final double FREQUENCY_SMOOTHING_SECONDS = 0.040;
	private static final double GAIN_ATTACK_SECONDS = 0.060;
	private static final double GAIN_RELEASE_SECONDS = 0.120;
	private static final double WHITE_NOISE_VARIANCE = 1.0 / 3.0;

	final int sampleRate;
	final int frequencySmoothingSamples;
	private final double gainAttackAlpha;
	private final double gainReleaseAlpha;
	private final double lowFilterAlpha;
	private final double midFilterAlpha;
	private final double lowNoiseNormalization;
	private final double midNoiseNormalization;
	private final double highNoiseNormalization;
	private int noiseState;
	private double lowFilter;
	private double midLowFilter;
	private double midHighFilter;
	private double highLowFilter;
	private double broadbandLowGain;
	private double broadbandMidGain;
	private double broadbandHighGain;

	PhaseContinuousAudioKernel(int sampleRate, int noiseSeed) {
		if (sampleRate < 8_000 || sampleRate > 192_000) {
			throw new IllegalArgumentException("sampleRate must be in [8000, 192000]");
		}
		this.sampleRate = sampleRate;
		this.frequencySmoothingSamples = Math.max(
				1,
				(int) Math.round(FREQUENCY_SMOOTHING_SECONDS * sampleRate)
		);
		this.gainAttackAlpha = timeConstantAlpha(GAIN_ATTACK_SECONDS, sampleRate);
		this.gainReleaseAlpha = timeConstantAlpha(GAIN_RELEASE_SECONDS, sampleRate);
		this.noiseState = noiseSeed == 0 ? 0x6d2b79f5 : noiseSeed;
		this.lowFilterAlpha = onePoleAlpha(300.0, sampleRate);
		this.midFilterAlpha = onePoleAlpha(3_200.0, sampleRate);
		this.lowNoiseNormalization = inverseRms(
				onePoleVariance(lowFilterAlpha)
		);
		this.midNoiseNormalization = inverseRms(
				differenceOfOnePolesVariance(lowFilterAlpha, midFilterAlpha)
		);
		this.highNoiseNormalization = inverseRms(
				highPassVariance(midFilterAlpha)
		);
	}

	void reset(int noiseSeed) {
		noiseState = noiseSeed == 0 ? 0x6d2b79f5 : noiseSeed;
		lowFilter = midLowFilter = midHighFilter = highLowFilter = 0;
		broadbandLowGain = broadbandMidGain = broadbandHighGain = 0;
	}

	void renderOscillator(Oscillator oscillator, float[] output, int offset, int length) {
		double amplitude = oscillator.amplitude;
		double phaseSin = oscillator.phaseSin;
		double phaseCos = oscillator.phaseCos;
		int sample = offset;
		int end = offset + length;

		while (sample < end) {
			boolean ramping = oscillator.frequencyRampSamplesRemaining > 0;
			int spanLength = ramping
					? Math.min(end - sample, oscillator.frequencyRampSamplesRemaining)
					: end - sample;
			double phaseStep = TWO_PI * oscillator.frequencyHz / sampleRate;
			double stepSin = Math.sin(phaseStep);
			double stepCos = Math.cos(phaseStep);
			double stepDelta = ramping
					? TWO_PI * oscillator.frequencyStepHz / sampleRate
					: 0.0;
			double deltaSin = Math.sin(stepDelta);
			double deltaCos = Math.cos(stepDelta);
			int spanEnd = sample + spanLength;

			while (sample < spanEnd) {
				double gainAlpha = oscillator.targetAmplitude > amplitude
						? gainAttackAlpha
						: gainReleaseAlpha;
				amplitude += gainAlpha * (oscillator.targetAmplitude - amplitude);
				output[sample] += (float) (phaseSin * amplitude);
				double nextPhaseSin = phaseSin * stepCos + phaseCos * stepSin;
				double nextPhaseCos = phaseCos * stepCos - phaseSin * stepSin;
				phaseSin = nextPhaseSin;
				phaseCos = nextPhaseCos;

				if (ramping) {
					double nextStepSin = stepSin * deltaCos + stepCos * deltaSin;
					double nextStepCos = stepCos * deltaCos - stepSin * deltaSin;
					stepSin = nextStepSin;
					stepCos = nextStepCos;
					oscillator.frequencyHz += oscillator.frequencyStepHz;
					oscillator.frequencyRampSamplesRemaining--;
				}
				sample++;
			}

			if (oscillator.frequencyRampSamplesRemaining == 0) {
				oscillator.frequencyHz = oscillator.targetFrequencyHz;
				oscillator.frequencyStepHz = 0.0;
			}
		}

		double magnitude = Math.hypot(phaseSin, phaseCos);
		if (magnitude > 1.0e-12) {
			phaseSin /= magnitude;
			phaseCos /= magnitude;
		}
		oscillator.phaseSin = phaseSin;
		oscillator.phaseCos = phaseCos;
		oscillator.amplitude = amplitude;
	}

	void renderBroadband(
			AcousticBands energy,
			PhaseContinuousSynthesizer.Layer layer,
			float[] output,
			int offset,
			int length
	) {
		double layerGain = layer.broadbandGain();
		double targetLowGain = Math.sqrt(energy.low()) * layerGain;
		double targetMidGain = Math.sqrt(energy.mid()) * layerGain;
		double targetHighGain = Math.sqrt(energy.high()) * layerGain;
		if (targetLowGain <= SILENCE_THRESHOLD
				&& targetMidGain <= SILENCE_THRESHOLD
				&& targetHighGain <= SILENCE_THRESHOLD
				&& broadbandLowGain <= SILENCE_THRESHOLD
				&& broadbandMidGain <= SILENCE_THRESHOLD
				&& broadbandHighGain <= SILENCE_THRESHOLD) {
			return;
		}

		for (int sample = offset; sample < offset + length; sample++) {
			broadbandLowGain = smoothGain(broadbandLowGain, targetLowGain);
			broadbandMidGain = smoothGain(broadbandMidGain, targetMidGain);
			broadbandHighGain = smoothGain(broadbandHighGain, targetHighGain);

			double lowWhite = nextWhiteNoise();
			lowFilter += lowFilterAlpha * (lowWhite - lowFilter);
			double low = lowFilter * lowNoiseNormalization;

			double midWhite = nextWhiteNoise();
			midLowFilter += lowFilterAlpha * (midWhite - midLowFilter);
			midHighFilter += midFilterAlpha * (midWhite - midHighFilter);
			double mid = (midHighFilter - midLowFilter) * midNoiseNormalization;

			double highWhite = nextWhiteNoise();
			highLowFilter += midFilterAlpha * (highWhite - highLowFilter);
			double high = (highWhite - highLowFilter) * highNoiseNormalization;
			output[sample] += (float) (
					low * broadbandLowGain
							+ mid * broadbandMidGain
							+ high * broadbandHighGain
			);
		}
	}

	private double smoothGain(double current, double target) {
		double alpha = target > current ? gainAttackAlpha : gainReleaseAlpha;
		return current + alpha * (target - current);
	}

	private double nextWhiteNoise() {
		int value = noiseState;
		value ^= value << 13;
		value ^= value >>> 17;
		value ^= value << 5;
		noiseState = value;
		return value / (double) Integer.MAX_VALUE;
	}

	void finishOutput(float[] output, int offset, int length) {
		for (int sample = offset; sample < offset + length; sample++) {
			double value = output[sample] * OUTPUT_GAIN;
			output[sample] = (float) (value / (1.0 + Math.abs(value)));
		}
	}

	private static double onePoleAlpha(double cutoffHz, int sampleRate) {
		return 1.0 - Math.exp(-TWO_PI * cutoffHz / sampleRate);
	}

	private static double timeConstantAlpha(double seconds, int sampleRate) {
		return 1.0 - Math.exp(-1.0 / (seconds * sampleRate));
	}

	private static double onePoleVariance(double alpha) {
		return WHITE_NOISE_VARIANCE * alpha / (2.0 - alpha);
	}

	private static double differenceOfOnePolesVariance(
			double firstAlpha,
			double secondAlpha
	) {
		double firstVariance = onePoleVariance(firstAlpha);
		double secondVariance = onePoleVariance(secondAlpha);
		double covariance = WHITE_NOISE_VARIANCE * firstAlpha * secondAlpha
				/ (firstAlpha + secondAlpha - firstAlpha * secondAlpha);
		return firstVariance + secondVariance - 2.0 * covariance;
	}

	private static double highPassVariance(double lowPassAlpha) {
		return WHITE_NOISE_VARIANCE
				+ onePoleVariance(lowPassAlpha)
				- 2.0 * lowPassAlpha * WHITE_NOISE_VARIANCE;
	}

	private static double inverseRms(double variance) {
		if (!Double.isFinite(variance) || variance <= 0.0) {
			throw new IllegalArgumentException("noise band variance must be positive");
		}
		return 1.0 / Math.sqrt(variance);
	}

	static final class Oscillator {
		double phaseSin;
		double phaseCos;
		double frequencyHz;
		double targetFrequencyHz;
		double frequencyStepHz;
		int frequencyRampSamplesRemaining;
		double amplitude;
		double targetAmplitude;

		Oscillator(double phaseRadians, double frequencyHz) {
			phaseSin = Math.sin(phaseRadians);
			phaseCos = Math.cos(phaseRadians);
			this.frequencyHz = frequencyHz;
			targetFrequencyHz = frequencyHz;
		}

		void setTargetFrequency(double frequencyHz, int smoothingSamples) {
			if (Double.compare(frequencyHz, targetFrequencyHz) == 0) {
				return;
			}
			targetFrequencyHz = frequencyHz;
			frequencyStepHz = (targetFrequencyHz - this.frequencyHz) / smoothingSamples;
			frequencyRampSamplesRemaining = smoothingSamples;
		}
	}
}
