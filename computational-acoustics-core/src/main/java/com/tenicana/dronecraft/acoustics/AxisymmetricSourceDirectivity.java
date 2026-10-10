package com.tenicana.dronecraft.acoustics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Applies a profile-defined, axisymmetric source directivity to an emission.
 * The model is even across the rotor plane and deliberately separate from
 * distance attenuation and propagation transmission.
 */
public final class AxisymmetricSourceDirectivity {
	private static final double COINCIDENT_POSITION_EPSILON_METERS = 1.0e-9;

	private AxisymmetricSourceDirectivity() {
	}

	public static AcousticEmissionFrame apply(
			AcousticEmissionFrame emission,
			AcousticSourceFrame source,
			AcousticListenerFrame listener,
			Parameters parameters
	) {
		Objects.requireNonNull(emission, "emission");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(listener, "listener");
		Objects.requireNonNull(parameters, "parameters");

		AcousticVector sourceToListener = listener.positionMeters()
				.subtract(source.positionMeters());
		double distance = sourceToListener.length();
		if (distance <= COINCIDENT_POSITION_EPSILON_METERS) {
			return emission;
		}
		double axisCosine = Math.abs(
				source.rotorDiskNormal().dot(sourceToListener.multiply(1.0 / distance))
		);
		axisCosine = Math.max(0.0, Math.min(1.0, axisCosine));

		List<TonalComponent> directedTones = new ArrayList<>(emission.tones().size());
		for (TonalComponent tone : emission.tones()) {
			directedTones.add(new TonalComponent(
					tone.kind(),
					tone.rotorIndex(),
					tone.order(),
					tone.frequencyHz(),
					tone.linearAmplitude()
							* parameters.tonalAmplitudeGain(tone.frequencyHz(), axisCosine),
					tone.phaseRadians()
			));
		}
		return new AcousticEmissionFrame(
				directedTones,
				emission.broadbandEnergy().multiply(
						parameters.broadbandEnergyGain(axisCosine)
				)
		);
	}

	/**
	 * Three directivity anchors. Tonal dB gain is interpolated in log-frequency
	 * space so a moving order does not jump at an arbitrary band boundary.
	 */
	public record Parameters(
			double lowAnchorHz,
			double midAnchorHz,
			double highAnchorHz,
			EvenPolynomial low,
			EvenPolynomial mid,
			EvenPolynomial high
	) {
		public Parameters {
			requirePositive(lowAnchorHz, "lowAnchorHz");
			requirePositive(midAnchorHz, "midAnchorHz");
			requirePositive(highAnchorHz, "highAnchorHz");
			if (!(lowAnchorHz < midAnchorHz && midAnchorHz < highAnchorHz)) {
				throw new IllegalArgumentException(
						"directivity anchors must be strictly increasing"
				);
			}
			Objects.requireNonNull(low, "low");
			Objects.requireNonNull(mid, "mid");
			Objects.requireNonNull(high, "high");
		}

		public static Parameters omnidirectional() {
			EvenPolynomial unity = new EvenPolynomial(0.0, 0.0, 0.0, 0.0);
			return new Parameters(150.0, 1_000.0, 8_000.0, unity, unity, unity);
		}

		public AcousticBands broadbandEnergyGain(double axisCosine) {
			requireAxisCosine(axisCosine);
			return new AcousticBands(
					low.energyGain(axisCosine),
					mid.energyGain(axisCosine),
					high.energyGain(axisCosine)
			);
		}

		public double tonalAmplitudeGain(double frequencyHz, double axisCosine) {
			requirePositive(frequencyHz, "frequencyHz");
			requireAxisCosine(axisCosine);
			double gainDb;
			if (frequencyHz <= lowAnchorHz) {
				gainDb = low.gainDb(axisCosine);
			} else if (frequencyHz < midAnchorHz) {
				gainDb = interpolateLogFrequency(
						frequencyHz,
						lowAnchorHz,
						midAnchorHz,
						low.gainDb(axisCosine),
						mid.gainDb(axisCosine)
				);
			} else if (frequencyHz < highAnchorHz) {
				gainDb = interpolateLogFrequency(
						frequencyHz,
						midAnchorHz,
						highAnchorHz,
						mid.gainDb(axisCosine),
						high.gainDb(axisCosine)
				);
			} else {
				gainDb = high.gainDb(axisCosine);
			}
			return Math.pow(10.0, gainDb / 20.0);
		}

		private static double interpolateLogFrequency(
				double frequencyHz,
				double lowerFrequencyHz,
				double upperFrequencyHz,
				double lowerGainDb,
				double upperGainDb
		) {
			double fraction = Math.log(frequencyHz / lowerFrequencyHz)
					/ Math.log(upperFrequencyHz / lowerFrequencyHz);
			return lowerGainDb + fraction * (upperGainDb - lowerGainDb);
		}
	}

	/**
	 * Even polynomial D(mu)=c2*mu^2+c4*mu^4 in dB, with explicit safety bounds.
	 */
	public record EvenPolynomial(
			double c2Db,
			double c4Db,
			double minimumDb,
			double maximumDb
	) {
		private static final double MINIMUM_ALLOWED_DB = -120.0;
		private static final double MAXIMUM_ALLOWED_DB = 60.0;

		public EvenPolynomial {
			requireFinite(c2Db, "c2Db");
			requireFinite(c4Db, "c4Db");
			requireFinite(minimumDb, "minimumDb");
			requireFinite(maximumDb, "maximumDb");
			if (minimumDb > maximumDb) {
				throw new IllegalArgumentException(
						"minimumDb must not exceed maximumDb"
				);
			}
			if (minimumDb < MINIMUM_ALLOWED_DB || maximumDb > MAXIMUM_ALLOWED_DB) {
				throw new IllegalArgumentException(
						"directivity bounds must be within [-120, 60] dB"
				);
			}
			if (minimumDb > 0.0 || maximumDb < 0.0) {
				throw new IllegalArgumentException(
						"directivity bounds must include the 0 dB rotor-plane reference"
				);
			}
		}

		public double gainDb(double axisCosine) {
			requireAxisCosine(axisCosine);
			double squared = axisCosine * axisCosine;
			double gain = c2Db * squared + c4Db * squared * squared;
			return Math.max(minimumDb, Math.min(maximumDb, gain));
		}

		public double amplitudeGain(double axisCosine) {
			return Math.pow(10.0, gainDb(axisCosine) / 20.0);
		}

		public double energyGain(double axisCosine) {
			return Math.pow(10.0, gainDb(axisCosine) / 10.0);
		}
	}

	private static void requireAxisCosine(double value) {
		if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
			throw new IllegalArgumentException("axisCosine must be in [0, 1]");
		}
	}

	private static void requirePositive(double value, String name) {
		if (!Double.isFinite(value) || value <= 0.0) {
			throw new IllegalArgumentException(name + " must be finite and positive");
		}
	}

	private static void requireFinite(double value, String name) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be finite");
		}
	}
}
