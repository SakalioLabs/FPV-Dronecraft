package com.tenicana.dronecraft.acoustics.diffraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BtmsInfiniteWedgeReferenceTest {
	private static final BtmsInfiniteWedgeReference REFERENCE =
			new BtmsInfiniteWedgeReference(
					BtmsInfiniteWedgeReference.Settings.reference()
			);

	@Test
	void reproducesCalamiaPublishedInfiniteEdgeExample() {
		BtmsInfiniteWedgeGeometry geometry = new BtmsInfiniteWedgeGeometry(
				2.0,
				5.0,
				0.0,
				Math.toRadians(45.0),
				Math.toRadians(270.0),
				Math.toRadians(315.0),
				343.0
		);
		double[] frequencies = {10.0, 100.0, 1_000.0};
		double[] scipyOracleDb = {
				-19.020601706002967,
				-23.89275109429572,
				-32.64634233694481
		};

		for (int index = 0; index < frequencies.length; index++) {
			double actualDb = 20.0 * Math.log10(
					REFERENCE.complexPressure(
							frequencies[index],
							geometry
					).magnitude()
			);
			assertEquals(scipyOracleDb[index], actualDb, 0.002);
		}
	}

	@Test
	void publishedUdfaTracksExactBtmsForBrasKnifeEdge() {
		BtmsInfiniteWedgeGeometry btmsGeometry = brasGeometry();
		InfiniteWedgeGeometry udfaGeometry = new InfiniteWedgeGeometry(
				btmsGeometry.sourceRadialDistanceMeters(),
				btmsGeometry.receiverRadialDistanceMeters(),
				btmsGeometry.sourceAzimuthRadians(),
				btmsGeometry.receiverAzimuthRadians(),
				btmsGeometry.exteriorWedgeAngleRadians(),
				Math.PI / 2.0,
				btmsGeometry.speedOfSoundMetersPerSecond()
		);
		UdfaInfiniteWedgeFilter udfa = new UdfaInfiniteWedgeFilter(
				UdfaInfiniteWedgeFilter.Parameters.published2023()
		);
		double[] frequencies = {1_000.0, 2_000.0, 4_000.0, 8_000.0, 12_000.0};
		double[] scipyBtmsDb = {
				-11.286666050441596,
				-14.127814976833363,
				-17.084330360721808,
				-20.079887214715235,
				-21.838015396618772
		};

		double squaredError = 0.0;
		for (int index = 0; index < frequencies.length; index++) {
			double btmsDb = 20.0 * Math.log10(
					REFERENCE.normalizedPressureMagnitude(
							frequencies[index],
							btmsGeometry
					)
			);
			assertEquals(scipyBtmsDb[index], btmsDb, 0.002);
			double udfaDb = 20.0 * Math.log10(
					udfa.pressureMagnitude(frequencies[index], udfaGeometry)
			);
			double error = udfaDb - btmsDb;
			squaredError += error * error;
		}

		double rmsErrorDb = Math.sqrt(squaredError / frequencies.length);
		assertEquals(0.090526, rmsErrorDb, 0.0001);
		assertTrue(rmsErrorDb < 0.1);
	}

	@Test
	void referenceSettingsConvergeToStrictSettingsAtTwelveKilohertz() {
		BtmsInfiniteWedgeGeometry geometry = brasGeometry();
		BtmsInfiniteWedgeReference strict = new BtmsInfiniteWedgeReference(
				BtmsInfiniteWedgeReference.Settings.strict()
		);
		double referenceDb = 20.0 * Math.log10(
				REFERENCE.normalizedPressureMagnitude(12_000.0, geometry)
		);
		double strictDb = 20.0 * Math.log10(
				strict.normalizedPressureMagnitude(12_000.0, geometry)
		);

		assertEquals(strictDb, referenceDb, 0.001);
	}

	@Test
	void sourceReceiverSwapPreservesExactPressureMagnitude() {
		BtmsInfiniteWedgeGeometry forward = new BtmsInfiniteWedgeGeometry(
				1.7,
				4.3,
				0.8,
				0.4,
				4.0,
				3.0 * Math.PI / 2.0,
				343.0
		);
		BtmsInfiniteWedgeGeometry reverse = new BtmsInfiniteWedgeGeometry(
				forward.receiverRadialDistanceMeters(),
				forward.sourceRadialDistanceMeters(),
				forward.axialSeparationMeters(),
				forward.receiverAzimuthRadians(),
				forward.sourceAzimuthRadians(),
				forward.exteriorWedgeAngleRadians(),
				forward.speedOfSoundMetersPerSecond()
		);

		assertEquals(
				REFERENCE.complexPressure(2_000.0, forward).magnitude(),
				REFERENCE.complexPressure(2_000.0, reverse).magnitude(),
				1.0e-12
		);
	}

	@Test
	void exactZoneBoundaryRequiresAnalyticSingularityExpansion() {
		BtmsInfiniteWedgeGeometry boundary = new BtmsInfiniteWedgeGeometry(
				1.0,
				2.0,
				0.0,
				0.0,
				Math.PI,
				2.0 * Math.PI,
				343.0
		);

		assertThrows(
				IllegalArgumentException.class,
				() -> REFERENCE.complexPressure(1_000.0, boundary)
		);
	}

	private static BtmsInfiniteWedgeGeometry brasGeometry() {
		double source = Math.hypot(3.0, 0.831);
		double receiver = Math.hypot(3.025, 0.831);
		double rayAngle = Math.acos(
				(-3.0 * 3.025 + 0.831 * 0.831) / (source * receiver)
		);
		return new BtmsInfiniteWedgeGeometry(
				source,
				receiver,
				0.0,
				0.0,
				2.0 * Math.PI - rayAngle,
				2.0 * Math.PI,
				343.0
		);
	}
}
