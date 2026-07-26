package com.tenicana.dronecraft.acoustics;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic order-domain source model. Amplitude constants are provisional
 * hypotheses and intentionally isolated in {@link Parameters} for calibration.
 */
public final class OrderTrackedRotorModel {
	private static final double NYQUIST_GUARD_RATIO = 0.45;

	private final Parameters parameters;

	public OrderTrackedRotorModel(Parameters parameters) {
		this.parameters = parameters;
	}

	public AcousticEmissionFrame evaluate(AcousticSourceFrame source, int sampleRate) {
		if (sampleRate < 8_000) {
			throw new IllegalArgumentException("sampleRate must be at least 8000 Hz");
		}
		double nyquistGuardHz = sampleRate * NYQUIST_GUARD_RATIO;
		List<TonalComponent> tones = new ArrayList<>();
		AcousticBands broadband = AcousticBands.SILENT;

		for (int rotorIndex = 0; rotorIndex < source.rotors().size(); rotorIndex++) {
			RotorAcousticState rotor = source.rotors().get(rotorIndex);
			if (rotor.rpm() <= 1.0 || rotor.normalizedPower() <= 1.0e-6) {
				continue;
			}

			double amplitudeScale = Math.sqrt(rotor.normalizedPower())
					* (0.35 + 0.65 * Math.min(rotor.normalizedLoad(), 1.5));
			RotorOperatingPointGainCurve.Gain operatingPointGain =
					parameters.operatingPointGainCurve().evaluate(rotor.rpm());
			double motorAmplitudeScale = amplitudeScale
					* operatingPointGain.motorTonalAmplitude();
			double rotorAmplitudeScale = amplitudeScale
					* operatingPointGain.rotorTonalAmplitude();
			addTone(tones, TonalComponent.Kind.SHAFT, rotorIndex, 1,
					rotor.shaftFrequencyHz(), parameters.shaftAmplitude() * motorAmplitudeScale,
					rotor.phaseRadians(), nyquistGuardHz);
			addTone(tones, TonalComponent.Kind.ELECTRICAL, rotorIndex, 1,
					rotor.electricalFrequencyHz(), parameters.electricalAmplitude() * motorAmplitudeScale,
					rotor.phaseRadians() * rotor.motorPolePairs(), nyquistGuardHz);
			addTone(tones, TonalComponent.Kind.COGGING_CANDIDATE, rotorIndex, 1,
					2.0 * rotor.electricalFrequencyHz(),
					parameters.coggingCandidateAmplitude() * motorAmplitudeScale,
					2.0 * rotor.phaseRadians() * rotor.motorPolePairs(), nyquistGuardHz);

			for (int harmonic = 1; harmonic <= parameters.bladePassHarmonics(); harmonic++) {
				double harmonicAmplitude = parameters.bladePassAmplitude() * rotorAmplitudeScale
						/ Math.pow(harmonic, parameters.harmonicRolloff());
				addTone(tones, TonalComponent.Kind.BLADE_PASS, rotorIndex, harmonic,
						rotor.bladePassFrequencyHz() * harmonic, harmonicAmplitude,
						rotor.phaseRadians() * rotor.bladeCount() * harmonic, nyquistGuardHz);
			}

			double broadbandScale = parameters.broadbandEnergy()
					* rotor.normalizedPower()
					* (0.25 + 0.75 * Math.min(rotor.normalizedLoad(), 1.5))
					* operatingPointGain.broadbandEnergy();
			broadband = broadband.add(
					parameters.broadbandDistribution().multiply(broadbandScale)
			);
		}
		return new AcousticEmissionFrame(tones, broadband);
	}

	private static void addTone(
			List<TonalComponent> tones,
			TonalComponent.Kind kind,
			int rotorIndex,
			int order,
			double frequencyHz,
			double amplitude,
			double phase,
			double nyquistGuardHz
	) {
		if (frequencyHz > 0.0 && frequencyHz < nyquistGuardHz && amplitude > 0.0) {
			tones.add(new TonalComponent(kind, rotorIndex, order, frequencyHz, amplitude, phase));
		}
	}

	public record Parameters(
			int bladePassHarmonics,
			double harmonicRolloff,
			double shaftAmplitude,
			double bladePassAmplitude,
			double electricalAmplitude,
			double coggingCandidateAmplitude,
			double broadbandEnergy,
			RotorOperatingPointGainCurve operatingPointGainCurve,
			AcousticBands broadbandDistribution
	) {
		public static Parameters researchDefaults() {
			return new Parameters(
					12,
					1.15,
					0.05,
					0.18,
					0.035,
					0.012,
					0.08,
					RotorOperatingPointGainCurve.unity(),
					new AcousticBands(0.20, 0.50, 0.30)
			);
		}

		public Parameters(
				int bladePassHarmonics,
				double harmonicRolloff,
				double shaftAmplitude,
				double bladePassAmplitude,
				double electricalAmplitude,
				double coggingCandidateAmplitude,
				double broadbandEnergy,
				RotorOperatingPointGainCurve operatingPointGainCurve
		) {
			this(
					bladePassHarmonics,
					harmonicRolloff,
					shaftAmplitude,
					bladePassAmplitude,
					electricalAmplitude,
					coggingCandidateAmplitude,
					broadbandEnergy,
					operatingPointGainCurve,
					new AcousticBands(0.20, 0.50, 0.30)
			);
		}

		public Parameters(
				int bladePassHarmonics,
				double harmonicRolloff,
				double shaftAmplitude,
				double bladePassAmplitude,
				double electricalAmplitude,
				double coggingCandidateAmplitude,
				double broadbandEnergy
		) {
			this(
					bladePassHarmonics,
					harmonicRolloff,
					shaftAmplitude,
					bladePassAmplitude,
					electricalAmplitude,
					coggingCandidateAmplitude,
					broadbandEnergy,
					RotorOperatingPointGainCurve.unity(),
					new AcousticBands(0.20, 0.50, 0.30)
			);
		}

		public Parameters {
			if (bladePassHarmonics < 1 || bladePassHarmonics > 64) {
				throw new IllegalArgumentException("bladePassHarmonics must be in [1, 64]");
			}
			requirePositive(harmonicRolloff, "harmonicRolloff");
			requireNonNegative(shaftAmplitude, "shaftAmplitude");
			requireNonNegative(bladePassAmplitude, "bladePassAmplitude");
			requireNonNegative(electricalAmplitude, "electricalAmplitude");
			requireNonNegative(coggingCandidateAmplitude, "coggingCandidateAmplitude");
			requireNonNegative(broadbandEnergy, "broadbandEnergy");
			if (operatingPointGainCurve == null) {
				throw new NullPointerException("operatingPointGainCurve");
			}
			if (broadbandDistribution == null) {
				throw new NullPointerException("broadbandDistribution");
			}
			if (Math.abs(broadbandDistribution.totalEnergy() - 1.0) > 1.0e-6) {
				throw new IllegalArgumentException(
						"broadbandDistribution energy fractions must sum to 1"
				);
			}
		}

		private static void requirePositive(double value, String name) {
			if (!Double.isFinite(value) || value <= 0.0) {
				throw new IllegalArgumentException(name + " must be finite and positive");
			}
		}

		private static void requireNonNegative(double value, String name) {
			if (!Double.isFinite(value) || value < 0.0) {
				throw new IllegalArgumentException(name + " must be finite and non-negative");
			}
		}
	}
}
