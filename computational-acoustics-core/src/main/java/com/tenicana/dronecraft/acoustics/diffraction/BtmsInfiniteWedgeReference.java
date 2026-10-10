package com.tenicana.dronecraft.acoustics.diffraction;

import java.util.Objects;

/**
 * Exact first-order infinite-wedge BTMS frequency-domain reference.
 *
 * <p>Implements Calamia, {@code Advances in Edge-Diffraction Modeling for
 * Virtual-Acoustic Simulations} (2009), equations (3.4), (3.8), and
 * (3.15)-(3.16). The oscillatory contour integral is evaluated using
 * phase-bounded composite 16-point Gauss-Legendre quadrature. This is an
 * offline correctness oracle, not a real-time rendering filter.</p>
 */
public final class BtmsInfiniteWedgeReference implements DiffractionFilterModel {
	private static final double TWO_PI = 2.0 * Math.PI;
	private static final double ANGULAR_SINGULARITY_TOLERANCE = 1.0e-11;
	private static final int MAX_SEGMENTS = 2_000_000;
	private static final double[] NODES = {
			-0.9894009349916499,
			-0.9445750230732326,
			-0.8656312023878318,
			-0.7554044083550030,
			-0.6178762444026438,
			-0.4580167776572274,
			-0.2816035507792589,
			-0.09501250983763744,
			0.09501250983763744,
			0.2816035507792589,
			0.4580167776572274,
			0.6178762444026438,
			0.7554044083550030,
			0.8656312023878318,
			0.9445750230732326,
			0.9894009349916499
	};
	private static final double[] WEIGHTS = {
			0.027152459411754096,
			0.06225352393864789,
			0.09515851168249278,
			0.12462897125553387,
			0.14959598881657674,
			0.16915651939500254,
			0.18260341504492358,
			0.18945061045506850,
			0.18945061045506850,
			0.18260341504492358,
			0.16915651939500254,
			0.14959598881657674,
			0.12462897125553387,
			0.09515851168249278,
			0.06225352393864789,
			0.027152459411754096
	};

	private final Settings settings;

	public BtmsInfiniteWedgeReference(Settings settings) {
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	/**
	 * Adapts the existing UDFA geometry contract when source and receiver lie
	 * in the same radial plane perpendicular to the edge.
	 */
	@Override
	public double pressureMagnitude(double frequencyHz, InfiniteWedgeGeometry geometry) {
		Objects.requireNonNull(geometry, "geometry");
		if (Math.abs(geometry.incidenceAngleRadians() - Math.PI / 2.0) > 1.0e-9) {
			throw new IllegalArgumentException(
					"the DiffractionFilterModel adapter requires pi/2 incidence"
			);
		}
		BtmsInfiniteWedgeGeometry btmsGeometry = new BtmsInfiniteWedgeGeometry(
				geometry.sourceDistanceMeters(),
				geometry.receiverDistanceMeters(),
				0.0,
				geometry.sourceAzimuthRadians(),
				geometry.receiverAzimuthRadians(),
				geometry.exteriorWedgeAngleRadians(),
				geometry.speedOfSoundMetersPerSecond()
		);
		return normalizedPressureMagnitude(frequencyHz, btmsGeometry);
	}

	public double normalizedPressureMagnitude(
			double frequencyHz,
			BtmsInfiniteWedgeGeometry geometry
	) {
		return complexPressure(frequencyHz, geometry).magnitude()
				* geometry.shortestDiffractedPathMeters();
	}

	/**
	 * Returns the raw transfer including propagation attenuation (1/metre).
	 */
	public ComplexPressure complexPressure(
			double frequencyHz,
			BtmsInfiniteWedgeGeometry geometry
	) {
		if (!(frequencyHz >= 0.0) || !Double.isFinite(frequencyHz)) {
			throw new IllegalArgumentException("frequencyHz must be finite and non-negative");
		}
		Objects.requireNonNull(geometry, "geometry");
		double wedgeIndex = Math.PI / geometry.exteriorWedgeAngleRadians();
		double[] phi = angularTerms(geometry);
		double[] sine = new double[4];
		double[] cosine = new double[4];
		for (int index = 0; index < phi.length; index++) {
			double angle = wedgeIndex * phi[index];
			sine[index] = Math.sin(angle);
			cosine[index] = Math.cos(angle);
			if (Math.abs(sine[index]) < ANGULAR_SINGULARITY_TOLERANCE
					&& cosine[index] > 0.0) {
				throw new IllegalArgumentException(
						"BTMS zone-boundary singularity requires the analytic "
								+ "boundary expansion"
				);
			}
		}

		double waveNumber = TWO_PI * frequencyHz
				/ geometry.speedOfSoundMetersPerSecond();
		double eta = 0.0;
		double integratedReal = 0.0;
		double integratedImaginary = 0.0;
		int segments = 0;
		while (eta < settings.maximumEta()) {
			double radius = referenceDistance(eta, geometry);
			double phaseDerivative = waveNumber
					* geometry.sourceRadialDistanceMeters()
					* geometry.receiverRadialDistanceMeters()
					* Math.sinh(eta) / radius;
			double width = Math.min(
					settings.maximumEtaStep(),
					settings.maximumPhaseAdvanceRadians()
							/ Math.max(phaseDerivative, Double.MIN_NORMAL)
			);
			width = Math.min(width, settings.maximumEta() - eta);
			double midpoint = eta + 0.5 * width;
			double halfWidth = 0.5 * width;
			for (int node = 0; node < NODES.length; node++) {
				double sampleEta = midpoint + halfWidth * NODES[node];
				double sampleRadius = referenceDistance(sampleEta, geometry);
				double beta = betaSum(sampleEta, wedgeIndex, sine, cosine);
				double amplitude = beta / sampleRadius;
				double phase = -waveNumber * sampleRadius;
				double weightedAmplitude = halfWidth * WEIGHTS[node] * amplitude;
				integratedReal += weightedAmplitude * Math.cos(phase);
				integratedImaginary += weightedAmplitude * Math.sin(phase);
			}
			eta += width;
			segments++;
			if (segments > MAX_SEGMENTS) {
				throw new IllegalStateException(
						"BTMS quadrature exceeded segment safety limit"
				);
			}
		}
		double factor = -wedgeIndex / (2.0 * Math.PI);
		return new ComplexPressure(
				factor * integratedReal,
				factor * integratedImaginary
		);
	}

	private static double[] angularTerms(BtmsInfiniteWedgeGeometry geometry) {
		double source = geometry.sourceAzimuthRadians();
		double receiver = geometry.receiverAzimuthRadians();
		return new double[] {
				Math.PI + source + receiver,
				Math.PI + source - receiver,
				Math.PI - source + receiver,
				Math.PI - source - receiver
		};
	}

	private static double betaSum(
			double eta,
			double wedgeIndex,
			double[] sine,
			double[] cosine
	) {
		double hyperbolic = Math.cosh(wedgeIndex * eta);
		double sum = 0.0;
		for (int index = 0; index < sine.length; index++) {
			sum += sine[index] / (hyperbolic - cosine[index]);
		}
		return sum;
	}

	private static double referenceDistance(
			double eta,
			BtmsInfiniteWedgeGeometry geometry
	) {
		double source = geometry.sourceRadialDistanceMeters();
		double receiver = geometry.receiverRadialDistanceMeters();
		double axial = geometry.axialSeparationMeters();
		return Math.sqrt(
				source * source + receiver * receiver + axial * axial
						+ 2.0 * source * receiver * Math.cosh(eta)
		);
	}

	public record Settings(
			double maximumEta,
			double maximumEtaStep,
			double maximumPhaseAdvanceRadians
	) {
		public Settings {
			if (!(maximumEta > 0.0)
					|| !(maximumEtaStep > 0.0)
					|| !(maximumPhaseAdvanceRadians > 0.0)
					|| !Double.isFinite(
							maximumEta + maximumEtaStep
									+ maximumPhaseAdvanceRadians
					)) {
				throw new IllegalArgumentException(
						"BTMS quadrature settings must be positive and finite"
				);
			}
		}

		/**
		 * Eta=8 gives sub-0.001 dB convergence for the published and BRAS
		 * validation geometries through 12 kHz.
		 */
		public static Settings reference() {
			return new Settings(8.0, 0.1, Math.PI);
		}

		public static Settings strict() {
			return new Settings(10.0, 0.075, Math.PI / 2.0);
		}
	}
}
