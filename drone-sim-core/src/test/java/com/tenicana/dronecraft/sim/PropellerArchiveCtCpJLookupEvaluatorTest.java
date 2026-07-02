package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PropellerArchiveCtCpJLookupEvaluatorTest {
	private static final double RHO = PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void staticAnchorSampleMatchesDimensionalRotorResponseReference() {
		double diameter = apDroneDiameterMeters();
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery query =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone", "static_anchor_low_rpm", diameter, RHO);

		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup =
				PropellerArchiveCtCpJLookupEvaluator.evaluate(query);
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(query);
		PropellerArchiveCtCpJDimensionalRotorResponse.RotorDimensionalResponse reference =
				dimensionalScenario("synthetic_static_anchor_exact");

		assertFalse(lookup.blocked());
		assertFalse(lookup.clamped());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.EXACT,
				lookup.interpolationStatus());
		assertEquals("accepted-reference-payload-bridge", lookup.dataSourceId());
		assertEquals(0.120, lookup.thrustCoefficientCt(), 1.0e-12);
		assertEquals(0.040, lookup.powerCoefficientCp(), 1.0e-12);
		assertEquals(0.0, lookup.propulsiveEfficiencyEta(), 1.0e-12);
		assertEquals(reference.thrustNewtons(), sample.thrustNewtons(), 1.0e-15);
		assertEquals(reference.shaftPowerWatts(), sample.shaftPowerWatts(), 1.0e-15);
		assertEquals(reference.shaftTorqueNewtonMeters(), sample.shaftTorqueNewtonMeters(), 1.0e-18);
		assertEquals(reference.idealInducedVelocityMetersPerSecond(),
				sample.idealInducedVelocityMetersPerSecond(), 1.0e-15);
	}

	@Test
	void midDomainBilinearSampleMatchesReferenceAndScalesWithUnits() {
		double diameter = apDroneDiameterMeters();
		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract contract =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract("apDrone", "mid_domain_mid_rpm");
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery query =
				new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						null,
						"",
						contract.queryAdvanceRatioJ(),
						contract.queryRpm(),
						diameter,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE);

		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup =
				PropellerArchiveCtCpJLookupEvaluator.evaluate(query);
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(query);
		PropellerArchiveCtCpJDimensionalRotorResponse.RotorDimensionalResponse reference =
				dimensionalScenario("synthetic_mid_bilinear_pass");

		assertEquals("apDrone", lookup.presetName());
		assertEquals("mid_domain_mid_rpm", lookup.caseName());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BILINEAR,
				lookup.interpolationStatus());
		assertEquals(4, lookup.observedNeighborRows());
		assertEquals(4, lookup.minimumNeighborRowsRequired());
		assertEquals(0.09325, lookup.thrustCoefficientCt(), 1.0e-12);
		assertEquals(0.05075, lookup.powerCoefficientCp(), 1.0e-12);
		assertEquals(reference.propulsiveEfficiencyEta(), lookup.propulsiveEfficiencyEta(), 1.0e-15);
		assertEquals(reference.thrustNewtons(), sample.thrustNewtons(), 1.0e-15);
		assertEquals(reference.shaftPowerWatts(), sample.shaftPowerWatts(), 1.0e-15);
		assertEquals(reference.shaftTorqueNewtonMeters(), sample.shaftTorqueNewtonMeters(), 1.0e-18);
		assertFinitePositive(sample);

		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample dense =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(
						lookup, diameter, RHO * 2.0);
		assertEquals(sample.thrustNewtons() * 2.0, dense.thrustNewtons(), 1.0e-15);
		assertEquals(sample.shaftPowerWatts() * 2.0, dense.shaftPowerWatts(), 1.0e-15);
		assertEquals(sample.shaftTorqueNewtonMeters() * 2.0, dense.shaftTorqueNewtonMeters(), 1.0e-18);
		assertEquals(sample.idealInducedVelocityMetersPerSecond(),
				dense.idealInducedVelocityMetersPerSecond(), 1.0e-15);

		double diameterScale = 1.10;
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample larger =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(
						lookup, diameter * diameterScale, RHO);
		assertEquals(sample.thrustNewtons() * Math.pow(diameterScale, 4.0),
				larger.thrustNewtons(), 1.0e-15);
		assertEquals(sample.shaftPowerWatts() * Math.pow(diameterScale, 5.0),
				larger.shaftPowerWatts(), 1.0e-15);
		assertEquals(sample.shaftTorqueNewtonMeters() * Math.pow(diameterScale, 5.0),
				larger.shaftTorqueNewtonMeters(), 1.0e-17);
	}

	@Test
	void highAdvanceRatioUsesLinearEdgeAndProducesFiniteDimensionalSample() {
		double diameter = apDroneDiameterMeters();
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery highQuery =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone", "high_domain_max_rpm", diameter, RHO);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery midQuery =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone", "mid_domain_mid_rpm", diameter, RHO);

		PropellerArchiveCtCpJLookupEvaluator.LookupResult high =
				PropellerArchiveCtCpJLookupEvaluator.evaluate(highQuery);
		PropellerArchiveCtCpJLookupEvaluator.LookupResult mid =
				PropellerArchiveCtCpJLookupEvaluator.evaluate(midQuery);
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(highQuery);

		assertFalse(high.blocked());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.LINEAR_ADVANCE,
				high.interpolationStatus());
		assertEquals(2, high.observedNeighborRows());
		assertEquals(0.5, high.advanceInterpolationFraction(), 1.0e-12);
		assertEquals(0.084, high.thrustCoefficientCt(), 1.0e-12);
		assertEquals(0.057, high.powerCoefficientCp(), 1.0e-12);
		assertEquals(high.effectiveAdvanceRatioJ() * high.thrustCoefficientCt() / high.powerCoefficientCp(),
				high.propulsiveEfficiencyEta(), 1.0e-12);
		assertTrue(high.thrustCoefficientCt() < mid.thrustCoefficientCt());
		assertTrue(high.powerCoefficientCp() > mid.powerCoefficientCp());
		assertTrue(sample.axialAdvanceSpeedMetersPerSecond()
				> PropellerArchiveCtCpJLookupEvaluator.sampleRotor(midQuery).axialAdvanceSpeedMetersPerSecond());
		assertFinitePositive(sample);
		assertEquals(expectedThrust(high, diameter, RHO), sample.thrustNewtons(), 1.0e-15);
		assertEquals(expectedShaftPower(high, diameter, RHO), sample.shaftPowerWatts(), 1.0e-15);
		assertEquals(sample.shaftPowerWatts() / sample.angularVelocityRadiansPerSecond(),
				sample.shaftTorqueNewtonMeters(), 1.0e-18);
	}

	@Test
	void outOfEnvelopeQueryBlocksOrExplicitlyClamps() {
		double diameter = apDroneDiameterMeters();
		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract high =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract("apDrone", "high_domain_max_rpm");
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery blockedQuery =
				new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						"apDrone",
						"high_domain_max_rpm",
						high.queryAdvanceRatioJ() + 0.20,
						high.queryRpm(),
						diameter,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE);

		PropellerArchiveCtCpJLookupEvaluator.LookupResult blocked =
				PropellerArchiveCtCpJLookupEvaluator.evaluate(blockedQuery);
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample blockedSample =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(blockedQuery);
		assertTrue(blocked.blocked());
		assertFalse(blocked.clamped());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BLOCKED,
				blocked.interpolationStatus());
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", blocked.status());
		assertEquals(0.0, blockedSample.thrustNewtons(), 1.0e-12);
		assertEquals(0.0, blockedSample.shaftPowerWatts(), 1.0e-12);

		PropellerArchiveCtCpJLookupEvaluator.LookupQuery clampQuery =
				new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						"apDrone",
						"high_domain_max_rpm",
						high.queryAdvanceRatioJ() + 0.20,
						high.queryRpm(),
						diameter,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.CLAMP_TO_ENVELOPE);
		PropellerArchiveCtCpJLookupEvaluator.LookupResult clamped =
				PropellerArchiveCtCpJLookupEvaluator.evaluate(clampQuery);
		assertFalse(clamped.blocked());
		assertTrue(clamped.clamped());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.CLAMPED_EXACT,
				clamped.interpolationStatus());
		assertEquals(clamped.upperAdvanceRatioJ(), clamped.effectiveAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.080, clamped.thrustCoefficientCt(), 1.0e-12);
		assertEquals(0.060, clamped.powerCoefficientCp(), 1.0e-12);
		assertTrue(PropellerArchiveCtCpJLookupEvaluator.sampleRotor(clampQuery).thrustNewtons() > 0.0);
	}

	@Test
	void lookupRejectsInvalidDimensionalInputs() {
		double diameter = apDroneDiameterMeters();
		assertThrows(IllegalArgumentException.class,
				() -> new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						"apDrone", "mid_domain_mid_rpm", -0.1, 4_000.0, diameter, RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE));
		assertThrows(IllegalArgumentException.class,
				() -> new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						"apDrone", "mid_domain_mid_rpm", 0.4, 0.0, diameter, RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE));
		assertThrows(IllegalArgumentException.class,
				() -> new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						"apDrone", "mid_domain_mid_rpm", 0.4, 4_000.0, 0.0, RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE));
		assertThrows(IllegalArgumentException.class,
				() -> new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						"apDrone", "mid_domain_mid_rpm", 0.4, 4_000.0, diameter, 0.0,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupEvaluator.LookupQuery.fromRotorRadius(
						"apDrone", "mid_domain_mid_rpm", 0.4, 4_000.0, -0.1, RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE));
	}

	private static double expectedThrust(
			PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup,
			double diameter,
			double density
	) {
		double n = lookup.effectiveRpm() / 60.0;
		return lookup.thrustCoefficientCt() * density * n * n * Math.pow(diameter, 4.0);
	}

	private static double expectedShaftPower(
			PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup,
			double diameter,
			double density
	) {
		double n = lookup.effectiveRpm() / 60.0;
		return lookup.powerCoefficientCp() * density * n * n * n * Math.pow(diameter, 5.0);
	}

	private static void assertFinitePositive(
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample
	) {
		assertTrue(Double.isFinite(sample.thrustNewtons()) && sample.thrustNewtons() > 0.0);
		assertTrue(Double.isFinite(sample.shaftPowerWatts()) && sample.shaftPowerWatts() > 0.0);
		assertTrue(Double.isFinite(sample.shaftTorqueNewtonMeters()) && sample.shaftTorqueNewtonMeters() > 0.0);
		assertTrue(Double.isFinite(sample.diskLoadingNewtonsPerSquareMeter())
				&& sample.diskLoadingNewtonsPerSquareMeter() > 0.0);
		assertTrue(Double.isFinite(sample.idealInducedVelocityMetersPerSecond())
				&& sample.idealInducedVelocityMetersPerSecond() > 0.0);
		assertTrue(Double.isFinite(sample.idealMomentumPowerOverShaftPower())
				&& sample.idealMomentumPowerOverShaftPower() > 0.0);
	}

	private static double apDroneDiameterMeters() {
		return DroneConfig.apDrone().rotors().get(0).radiusMeters() * 2.0;
	}

	private static PropellerArchiveCtCpJDimensionalRotorResponse.RotorDimensionalResponse dimensionalScenario(
			String name
	) {
		return PropellerArchiveCtCpJDimensionalRotorResponse.audit()
				.scenarios()
				.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.response();
	}
}
