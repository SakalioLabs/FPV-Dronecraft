package com.tenicana.dronecraft.acoustics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AxisymmetricSourceDirectivityTest {
	private static final AcousticEmissionFrame EMISSION = new AcousticEmissionFrame(
			List.of(
					tone(100.0),
					tone(1_000.0),
					tone(10_000.0)
			),
			new AcousticBands(2.0, 3.0, 4.0)
	);
	private static final AxisymmetricSourceDirectivity.Parameters PARAMETERS =
			new AxisymmetricSourceDirectivity.Parameters(
					100.0,
					1_000.0,
					10_000.0,
					new AxisymmetricSourceDirectivity.EvenPolynomial(
							-6.0, 0.0, -12.0, 6.0
					),
					new AxisymmetricSourceDirectivity.EvenPolynomial(
							0.0, 0.0, -12.0, 6.0
					),
					new AxisymmetricSourceDirectivity.EvenPolynomial(
							6.0, 0.0, -12.0, 6.0
					)
			);

	@Test
	void appliesBandDependentPressureAndEnergyGainOnAxis() {
		AcousticEmissionFrame directed = AxisymmetricSourceDirectivity.apply(
				EMISSION,
				source(new AcousticVector(0.0, 1.0, 0.0)),
				listener(new AcousticVector(0.0, 10.0, 0.0)),
				PARAMETERS
		);

		assertEquals(dbToAmplitude(-6.0), directed.tones().get(0).linearAmplitude(), 1.0e-12);
		assertEquals(1.0, directed.tones().get(1).linearAmplitude(), 1.0e-12);
		assertEquals(dbToAmplitude(6.0), directed.tones().get(2).linearAmplitude(), 1.0e-12);
		assertEquals(2.0 * dbToEnergy(-6.0), directed.broadbandEnergy().low(), 1.0e-12);
		assertEquals(3.0, directed.broadbandEnergy().mid(), 1.0e-12);
		assertEquals(4.0 * dbToEnergy(6.0), directed.broadbandEnergy().high(), 1.0e-12);
	}

	@Test
	void isEvenAcrossTheRotorPlaneAndUnityInThePlane() {
		AcousticSourceFrame source = source(new AcousticVector(0.0, 1.0, 0.0));

		AcousticEmissionFrame above = AxisymmetricSourceDirectivity.apply(
				EMISSION,
				source,
				listener(new AcousticVector(0.0, 10.0, 0.0)),
				PARAMETERS
		);
		AcousticEmissionFrame below = AxisymmetricSourceDirectivity.apply(
				EMISSION,
				source,
				listener(new AcousticVector(0.0, -10.0, 0.0)),
				PARAMETERS
		);
		AcousticEmissionFrame inPlane = AxisymmetricSourceDirectivity.apply(
				EMISSION,
				source,
				listener(new AcousticVector(10.0, 0.0, 0.0)),
				PARAMETERS
		);

		assertEquals(above, below);
		assertEquals(EMISSION, inPlane);
	}

	@Test
	void interpolatesTonalGainContinuouslyInLogFrequency() {
		double geometricMidpoint = Math.sqrt(100.0 * 1_000.0);

		double gain = PARAMETERS.tonalAmplitudeGain(geometricMidpoint, 1.0);

		assertEquals(dbToAmplitude(-3.0), gain, 1.0e-12);
	}

	@Test
	void coincidentSourceAndListenerLeaveEmissionUnchanged() {
		AcousticSourceFrame source = source(new AcousticVector(0.0, 1.0, 0.0));

		AcousticEmissionFrame result = AxisymmetricSourceDirectivity.apply(
				EMISSION,
				source,
				listener(source.positionMeters()),
				PARAMETERS
		);

		assertSame(EMISSION, result);
	}

	@Test
	void rejectsInvalidProfileBoundsAndAnchorOrder() {
		assertThrows(IllegalArgumentException.class, () ->
				new AxisymmetricSourceDirectivity.EvenPolynomial(
						0.0, 0.0, 1.0, -1.0
				)
		);
		assertThrows(IllegalArgumentException.class, () ->
				new AxisymmetricSourceDirectivity.Parameters(
						1_000.0,
						100.0,
						10_000.0,
						PARAMETERS.low(),
						PARAMETERS.mid(),
						PARAMETERS.high()
				)
		);
		assertThrows(IllegalArgumentException.class, () ->
				new AxisymmetricSourceDirectivity.EvenPolynomial(
						0.0, 0.0, -121.0, 0.0
				)
		);
		assertThrows(IllegalArgumentException.class, () ->
				new AxisymmetricSourceDirectivity.EvenPolynomial(
						0.0, 0.0, 1.0, 2.0
				)
		);
	}

	private static AcousticSourceFrame source(AcousticVector normal) {
		return new AcousticSourceFrame(
				1L,
				0L,
				AcousticVector.ZERO,
				AcousticVector.ZERO,
				normal,
				0.1,
				List.of(new RotorAcousticState(
						10_000.0,
						0.5,
						0.5,
						0.0635,
						3,
						7,
						1,
						0.0
				))
		);
	}

	private static AcousticListenerFrame listener(AcousticVector position) {
		return new AcousticListenerFrame(position, AcousticVector.ZERO);
	}

	private static TonalComponent tone(double frequencyHz) {
		return new TonalComponent(
				TonalComponent.Kind.BLADE_PASS,
				0,
				1,
				frequencyHz,
				1.0,
				0.0
		);
	}

	private static double dbToAmplitude(double gainDb) {
		return Math.pow(10.0, gainDb / 20.0);
	}

	private static double dbToEnergy(double gainDb) {
		return Math.pow(10.0, gainDb / 10.0);
	}
}
