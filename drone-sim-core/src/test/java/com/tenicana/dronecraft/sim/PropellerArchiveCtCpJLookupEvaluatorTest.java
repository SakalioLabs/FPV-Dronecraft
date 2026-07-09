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

		assertFalse(lookup.blocked());
		assertFalse(lookup.clamped());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.EXACT,
				lookup.interpolationStatus());
		assertEquals("accepted-reference-payload-bridge", lookup.dataSourceId());
		assertEquals(0.120, lookup.thrustCoefficientCt(), 1.0e-12);
		assertEquals(0.040, lookup.powerCoefficientCp(), 1.0e-12);
		assertEquals(0.0, lookup.propulsiveEfficiencyEta(), 1.0e-12);
		assertEquals(0.025110868242593, sample.thrustNewtons(), 1.0e-15);
		assertEquals(0.026705995970315, sample.shaftPowerWatts(), 1.0e-15);
		assertEquals(0.000172569682049040, sample.shaftTorqueNewtonMeters(), 1.0e-18);
		assertEquals(lookup.powerCoefficientCp() / (2.0 * Math.PI),
				sample.torqueCoefficientCq(), 1.0e-18);
		assertEquals(expectedShaftTorqueFromCq(sample),
				sample.shaftTorqueNewtonMeters(), 1.0e-17);
		assertEquals(0.881858670062071, sample.idealInducedVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(expectedIdealMomentumPower(sample), sample.idealMomentumPowerWatts(), 1.0e-15);
		assertWakeMomentumAndSwirlClosure(sample);
		assertEquals(sample.diskAreaSquareMeters() * 0.5,
				sample.farWakeContractedAreaSquareMeters(), 1.0e-18);
		assertEquals(0.5, sample.farWakeContractedAreaOverDiskArea(), 1.0e-15);
		assertEquals(Math.sqrt(0.5), sample.farWakeEquivalentRadiusOverRotorRadius(), 1.0e-15);
	}

	@Test
	void midDomainBilinearSampleMatchesReferenceAndScalesWithUnits() {
		double diameter = apDroneDiameterMeters();
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery query =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						null, "mid_domain_mid_rpm", diameter, RHO);

		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup =
				PropellerArchiveCtCpJLookupEvaluator.evaluate(query);
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(query);

		assertEquals("apDrone", lookup.presetName());
		assertEquals("mid_domain_mid_rpm", lookup.caseName());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BILINEAR,
				lookup.interpolationStatus());
		assertEquals(4, lookup.observedNeighborRows());
		assertEquals(4, lookup.minimumNeighborRowsRequired());
		assertEquals(0.09325, lookup.thrustCoefficientCt(), 1.0e-12);
		assertEquals(0.05075, lookup.powerCoefficientCp(), 1.0e-12);
		assertEquals(0.7467349753694581, lookup.propulsiveEfficiencyEta(), 1.0e-15);
		assertEquals(0.19840592872073343, sample.thrustNewtons(), 1.0e-15);
		assertEquals(1.0985575597709705, sample.shaftPowerWatts(), 1.0e-15);
		assertEquals(0.0022262087016841664, sample.shaftTorqueNewtonMeters(), 1.0e-18);
		assertFinitePositive(sample);
		assertEquals(expectedAxialMomentumInducedVelocity(sample),
				sample.idealInducedVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(expectedIdealMomentumPower(sample), sample.idealMomentumPowerWatts(), 1.0e-15);
		assertEquals(sample.idealMomentumPowerWatts() / sample.shaftPowerWatts(),
				sample.idealMomentumPowerOverShaftPower(), 1.0e-15);
		assertEquals(sample.shaftPowerWatts() - sample.idealMomentumPowerWatts(),
				sample.shaftPowerResidualWatts(), 1.0e-15);
		assertEquals(sample.shaftPowerResidualWatts() / sample.shaftPowerWatts(),
				sample.shaftPowerResidualFraction(), 1.0e-15);
		assertTrue(sample.shaftPowerResidualWatts() > 0.0);
		assertTrue(sample.idealMomentumPowerOverShaftPower() > 0.90);
		assertWakeMomentumAndSwirlClosure(sample);
		assertTrue(sample.farWakeContractedAreaSquareMeters() > sample.diskAreaSquareMeters() * 0.5);
		assertTrue(sample.farWakeContractedAreaSquareMeters() < sample.diskAreaSquareMeters());
		assertTrue(sample.farWakeContractedAreaOverDiskArea() > 0.5);
		assertTrue(sample.farWakeContractedAreaOverDiskArea() < 1.0);
		assertEquals(Math.sqrt(sample.farWakeContractedAreaOverDiskArea()),
				sample.farWakeEquivalentRadiusOverRotorRadius(), 1.0e-15);
		assertTrue(sample.totalWakeKineticPowerWatts() > sample.idealMomentumPowerWatts());
		assertEquals(sample.shaftPowerWatts() - sample.totalWakeKineticPowerWatts(),
				sample.totalWakeKineticPowerResidualWatts(), 1.0e-15);

		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample dense =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(
						lookup, diameter, RHO * 2.0);
		assertEquals(sample.thrustNewtons() * 2.0, dense.thrustNewtons(), 1.0e-15);
		assertEquals(sample.shaftPowerWatts() * 2.0, dense.shaftPowerWatts(), 1.0e-15);
		assertEquals(sample.shaftTorqueNewtonMeters() * 2.0, dense.shaftTorqueNewtonMeters(), 1.0e-18);
		assertEquals(sample.torqueCoefficientCq(), dense.torqueCoefficientCq(), 1.0e-18);
		assertEquals(sample.idealInducedVelocityMetersPerSecond(),
				dense.idealInducedVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(sample.idealMomentumPowerWatts() * 2.0,
				dense.idealMomentumPowerWatts(), 1.0e-15);
		assertEquals(sample.usefulAxialThrustPowerWatts() * 2.0,
				dense.usefulAxialThrustPowerWatts(), 1.0e-15);
		assertEquals(sample.idealInducedPowerWatts() * 2.0,
				dense.idealInducedPowerWatts(), 1.0e-15);
		assertEquals(sample.axialPropulsiveEfficiency(),
				dense.axialPropulsiveEfficiency(), 1.0e-15);
		assertEquals(sample.actuatorDiskAxialVelocityMetersPerSecond(),
				dense.actuatorDiskAxialVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(sample.diskMassFlowKilogramsPerSecond() * 2.0,
				dense.diskMassFlowKilogramsPerSecond(), 1.0e-15);
		assertEquals(sample.farWakeAxialVelocityMetersPerSecond(),
				dense.farWakeAxialVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(sample.farWakeContractedAreaOverDiskArea(),
				dense.farWakeContractedAreaOverDiskArea(), 1.0e-15);
		assertEquals(sample.farWakeEquivalentRadiusOverRotorRadius(),
				dense.farWakeEquivalentRadiusOverRotorRadius(), 1.0e-15);
		assertEquals(sample.wakeTangentialVelocityMetersPerSecond(),
				dense.wakeTangentialVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(sample.wakeSwirlKineticPowerWatts() * 2.0,
				dense.wakeSwirlKineticPowerWatts(), 1.0e-15);
		assertEquals(sample.wakeSwirlKineticPowerOverShaftPower(),
				dense.wakeSwirlKineticPowerOverShaftPower(), 1.0e-15);
		assertEquals(sample.totalWakeKineticPowerResidualWatts() * 2.0,
				dense.totalWakeKineticPowerResidualWatts(), 1.0e-15);
		assertEquals(sample.totalWakeKineticPowerResidualFraction(),
				dense.totalWakeKineticPowerResidualFraction(), 1.0e-15);
		assertEquals(sample.shaftPowerResidualWatts() * 2.0,
				dense.shaftPowerResidualWatts(), 1.0e-15);
		assertEquals(sample.shaftPowerResidualFraction(),
				dense.shaftPowerResidualFraction(), 1.0e-15);

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
		assertEquals(sample.torqueCoefficientCq(), larger.torqueCoefficientCq(), 1.0e-18);
		assertEquals(sample.idealInducedVelocityMetersPerSecond() * diameterScale,
				larger.idealInducedVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(sample.idealMomentumPowerWatts() * Math.pow(diameterScale, 5.0),
				larger.idealMomentumPowerWatts(), 1.0e-15);
		assertEquals(sample.usefulAxialThrustPowerWatts() * Math.pow(diameterScale, 5.0),
				larger.usefulAxialThrustPowerWatts(), 1.0e-15);
		assertEquals(sample.idealInducedPowerWatts() * Math.pow(diameterScale, 5.0),
				larger.idealInducedPowerWatts(), 1.0e-15);
		assertEquals(sample.axialPropulsiveEfficiency(),
				larger.axialPropulsiveEfficiency(), 1.0e-15);
		assertEquals(sample.actuatorDiskAxialVelocityMetersPerSecond() * diameterScale,
				larger.actuatorDiskAxialVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(sample.diskMassFlowKilogramsPerSecond() * Math.pow(diameterScale, 3.0),
				larger.diskMassFlowKilogramsPerSecond(), 1.0e-15);
		assertEquals(sample.farWakeAxialVelocityMetersPerSecond() * diameterScale,
				larger.farWakeAxialVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(sample.farWakeContractedAreaSquareMeters() * diameterScale * diameterScale,
				larger.farWakeContractedAreaSquareMeters(), 1.0e-17);
		assertEquals(sample.farWakeContractedAreaOverDiskArea(),
				larger.farWakeContractedAreaOverDiskArea(), 1.0e-15);
		assertEquals(sample.farWakeEquivalentRadiusOverRotorRadius(),
				larger.farWakeEquivalentRadiusOverRotorRadius(), 1.0e-15);
		assertEquals(sample.wakeSwirlKineticPowerWatts() * Math.pow(diameterScale, 5.0),
				larger.wakeSwirlKineticPowerWatts(), 1.0e-15);
		assertEquals(sample.wakeSwirlKineticPowerOverShaftPower(),
				larger.wakeSwirlKineticPowerOverShaftPower(), 1.0e-15);
		assertEquals(sample.totalWakeKineticPowerResidualWatts() * Math.pow(diameterScale, 5.0),
				larger.totalWakeKineticPowerResidualWatts(), 1.0e-15);
		assertEquals(sample.totalWakeKineticPowerResidualFraction(),
				larger.totalWakeKineticPowerResidualFraction(), 1.0e-15);
		assertEquals(sample.shaftPowerResidualWatts() * Math.pow(diameterScale, 5.0),
				larger.shaftPowerResidualWatts(), 1.0e-15);
		assertEquals(sample.shaftPowerResidualFraction(),
				larger.shaftPowerResidualFraction(), 1.0e-15);
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
				sample.shaftTorqueNewtonMeters(), 1.0e-17);
		assertEquals(high.powerCoefficientCp() / (2.0 * Math.PI),
				sample.torqueCoefficientCq(), 1.0e-18);
		assertEquals(expectedShaftTorqueFromCq(sample),
				sample.shaftTorqueNewtonMeters(), 1.0e-17);
		assertEquals(expectedAxialMomentumInducedVelocity(sample),
				sample.idealInducedVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(expectedIdealMomentumPower(sample), sample.idealMomentumPowerWatts(), 1.0e-15);
		assertTrue(sample.idealMomentumPowerOverShaftPower() > 1.0);
		assertEquals(sample.shaftPowerWatts() - sample.idealMomentumPowerWatts(),
				sample.shaftPowerResidualWatts(), 1.0e-15);
		assertTrue(sample.shaftPowerResidualWatts() < 0.0);
		assertTrue(sample.shaftPowerResidualFraction() < 0.0);
		assertWakeMomentumAndSwirlClosure(sample);
		assertTrue(sample.totalWakeKineticPowerResidualWatts() < sample.shaftPowerResidualWatts());
		assertTrue(sample.totalWakeKineticPowerOverShaftPower()
				> sample.idealMomentumPowerOverShaftPower());
	}

	@Test
	void staticAnchoredSampleUsesRotorSpecStaticCoefficientsAtRuntimeRpm() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double diameter = rotor.radiusMeters() * 2.0;
		double rpm = 10_000.0;
		RotorStaticCtCpModel.StaticRotorSample staticSample = RotorStaticCtCpModel.sample(
				"apDrone", "static_anchored", rotor, rpm, RHO);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery query =
				new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						"apDrone",
						"static_anchored_hover",
						0.0,
						rpm,
						diameter,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE);

		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup =
				PropellerArchiveCtCpJLookupEvaluator.evaluateStaticAnchored(
						query,
						staticSample.thrustCoefficientCt(),
						staticSample.powerCoefficientCp());
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample =
				PropellerArchiveCtCpJLookupEvaluator.sampleStaticAnchoredRotor(
						query,
						staticSample.thrustCoefficientCt(),
						staticSample.powerCoefficientCp());

		assertFalse(lookup.blocked());
		assertFalse(lookup.clamped());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.EXACT,
				lookup.interpolationStatus());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.STATIC_ANCHORED_DATA_SOURCE_ID,
				lookup.dataSourceId());
		assertEquals(staticSample.thrustCoefficientCt(), lookup.thrustCoefficientCt(), 1.0e-15);
		assertEquals(staticSample.powerCoefficientCp(), lookup.powerCoefficientCp(), 1.0e-15);
		assertEquals(staticSample.thrustNewtons(), sample.thrustNewtons(), 1.0e-12);
		assertEquals(staticSample.shaftPowerWatts(), sample.shaftPowerWatts(), 1.0e-12);
		assertEquals(staticSample.shaftTorqueNewtonMeters(), sample.shaftTorqueNewtonMeters(), 1.0e-15);
	}

	@Test
	void staticAnchoredMidAdvanceUsesAcceptedShapeWithoutRpmClamp() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double diameter = rotor.radiusMeters() * 2.0;
		double rpm = 12_000.0;
		RotorStaticCtCpModel.StaticRotorSample staticSample = RotorStaticCtCpModel.sample(
				"apDrone", "static_anchored", rotor, rpm, RHO);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery query =
				new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						"apDrone",
						"static_anchored_mid_j",
						0.40,
						rpm,
						diameter,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE);

		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup =
				PropellerArchiveCtCpJLookupEvaluator.evaluateStaticAnchored(
						query,
						staticSample.thrustCoefficientCt(),
						staticSample.powerCoefficientCp());
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample =
				PropellerArchiveCtCpJLookupEvaluator.sampleStaticAnchoredRotor(
						query,
						staticSample.thrustCoefficientCt(),
						staticSample.powerCoefficientCp());

		assertFalse(lookup.blocked());
		assertFalse(lookup.clamped());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.LINEAR_ADVANCE,
				lookup.interpolationStatus());
		assertEquals(rpm, lookup.effectiveRpm(), 1.0e-9);
		assertTrue(lookup.thrustCoefficientCt() < staticSample.thrustCoefficientCt());
		assertTrue(lookup.powerCoefficientCp() > staticSample.powerCoefficientCp());
		assertFinitePositive(sample);
		assertEquals(expectedThrust(lookup, diameter, RHO), sample.thrustNewtons(), 1.0e-12);
		assertEquals(expectedShaftPower(lookup, diameter, RHO), sample.shaftPowerWatts(), 1.0e-12);
		assertEquals(expectedIdealMomentumPower(sample), sample.idealMomentumPowerWatts(), 1.0e-12);
	}

	@Test
	void staticAnchoredOutOfEnvelopeBlocksOrExplicitlyClampsAdvanceShape() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double diameter = rotor.radiusMeters() * 2.0;
		double rpm = 12_000.0;
		RotorStaticCtCpModel.StaticRotorSample staticSample = RotorStaticCtCpModel.sample(
				"apDrone", "static_anchored", rotor, rpm, RHO);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery blockedQuery =
				new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						"apDrone",
						"static_anchored_high_j",
						1.20,
						rpm,
						diameter,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE);
		PropellerArchiveCtCpJLookupEvaluator.LookupResult blocked =
				PropellerArchiveCtCpJLookupEvaluator.evaluateStaticAnchored(
						blockedQuery,
						staticSample.thrustCoefficientCt(),
						staticSample.powerCoefficientCp());
		assertTrue(blocked.blocked());
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", blocked.status());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.STATIC_ANCHORED_DATA_SOURCE_ID,
				blocked.dataSourceId());

		PropellerArchiveCtCpJLookupEvaluator.LookupQuery clampQuery =
				new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						"apDrone",
						"static_anchored_high_j",
						1.20,
						rpm,
						diameter,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.CLAMP_TO_ENVELOPE);
		PropellerArchiveCtCpJLookupEvaluator.LookupResult clamped =
				PropellerArchiveCtCpJLookupEvaluator.evaluateStaticAnchored(
						clampQuery,
						staticSample.thrustCoefficientCt(),
						staticSample.powerCoefficientCp());
		assertFalse(clamped.blocked());
		assertTrue(clamped.clamped());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.CLAMPED_EXACT,
				clamped.interpolationStatus());
		assertEquals(0.8128, clamped.effectiveAdvanceRatioJ(), 1.0e-12);
		assertEquals(rpm, clamped.effectiveRpm(), 1.0e-9);
	}

	@Test
	void outOfEnvelopeQueryBlocksOrExplicitlyClamps() {
		double diameter = apDroneDiameterMeters();
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery high =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone", "high_domain_max_rpm", diameter, RHO);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery blockedQuery =
				new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						"apDrone",
						"high_domain_max_rpm",
						high.advanceRatioJ() + 0.20,
						high.rpm(),
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
		assertEquals(0.0, blockedSample.torqueCoefficientCq(), 1.0e-12);
		assertEquals(Math.PI * Math.pow(diameter * 0.5, 2.0),
				blockedSample.diskAreaSquareMeters(), 1.0e-15);
		assertEquals(0.0, blockedSample.diskLoadingNewtonsPerSquareMeter(), 1.0e-12);
		assertEquals(0.0, blockedSample.diskMassFlowKilogramsPerSecond(), 1.0e-12);
		assertEquals(0.0, blockedSample.axialMomentumThrustNewtons(), 1.0e-12);
		assertEquals(0.0, blockedSample.axialMomentumThrustResidualNewtons(), 1.0e-12);
		assertEquals(0.0, blockedSample.axialMomentumThrustResidualFraction(), 1.0e-12);
		assertEquals(0.0, blockedSample.axialMomentumPowerWatts(), 1.0e-12);
		assertEquals(0.0, blockedSample.axialMomentumPowerResidualWatts(), 1.0e-12);
		assertEquals(0.0, blockedSample.axialMomentumPowerResidualFraction(), 1.0e-12);
		assertEquals(0.0, blockedSample.farWakeContractedAreaSquareMeters(), 1.0e-12);
		assertEquals(0.0, blockedSample.farWakeEquivalentRadiusMeters(), 1.0e-12);
		assertEquals(0.0, blockedSample.usefulAxialThrustPowerWatts(), 1.0e-12);
		assertEquals(0.0, blockedSample.idealInducedPowerWatts(), 1.0e-12);
		assertEquals(0.0, blockedSample.axialPropulsiveEfficiency(), 1.0e-12);
		assertEquals(0.0, blockedSample.wakeSwirlKineticPowerOverShaftPower(), 1.0e-12);
		assertEquals(0.0, blockedSample.totalWakeKineticPowerResidualWatts(), 1.0e-12);
		assertEquals(0.0, blockedSample.totalWakeKineticPowerResidualFraction(), 1.0e-12);
		assertEquals(0.0, blockedSample.shaftPowerResidualWatts(), 1.0e-12);
		assertEquals(0.0, blockedSample.shaftPowerResidualFraction(), 1.0e-12);

		PropellerArchiveCtCpJLookupEvaluator.LookupQuery clampQuery =
				new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						"apDrone",
						"high_domain_max_rpm",
						high.advanceRatioJ() + 0.20,
						high.rpm(),
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
	void clampedCoefficientSampleUsesEffectiveEnvelopeForDimensionalScaling() {
		double diameter = apDroneDiameterMeters();
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery high =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone", "high_domain_max_rpm", diameter, RHO);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery clampQuery =
				new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						"apDrone",
						"high_domain_max_rpm",
						high.advanceRatioJ() + 0.20,
						high.rpm() * 1.25,
						diameter,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.CLAMP_TO_ENVELOPE);

		PropellerArchiveCtCpJLookupEvaluator.LookupResult clamped =
				PropellerArchiveCtCpJLookupEvaluator.evaluate(clampQuery);
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(clampQuery);

		assertTrue(clamped.clamped());
		assertEquals(high.rpm(), clamped.effectiveRpm(), 1.0e-9);
		assertEquals(clamped.effectiveRpm(), sample.revolutionsPerSecond() * 60.0, 1.0e-9);
		assertEquals(clamped.effectiveAdvanceRatioJ() * clamped.effectiveRpm() / 60.0 * diameter,
				sample.axialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(expectedThrust(clamped, diameter, RHO), sample.thrustNewtons(), 1.0e-15);
		assertEquals(expectedShaftPower(clamped, diameter, RHO), sample.shaftPowerWatts(), 1.0e-15);
		assertEquals(expectedIdealMomentumPower(sample), sample.idealMomentumPowerWatts(), 1.0e-15);
		assertEquals(sample.shaftPowerWatts() - sample.idealMomentumPowerWatts(),
				sample.shaftPowerResidualWatts(), 1.0e-15);
		assertTrue(sample.thrustNewtons()
				< PropellerArchiveCtCpJLookupEvaluator.sampleRotor(high).thrustNewtons());
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

	private static double expectedShaftTorqueFromCq(
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample
	) {
		double n = sample.revolutionsPerSecond();
		return sample.torqueCoefficientCq()
				* sample.airDensityKgPerCubicMeter()
				* n
				* n
				* Math.pow(sample.propellerDiameterMeters(), 5.0);
	}

	private static double expectedAxialMomentumInducedVelocity(
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample
	) {
		double axialAdvanceSpeed = Math.max(0.0, sample.axialAdvanceSpeedMetersPerSecond());
		double diskTerm = 2.0 * sample.thrustNewtons()
				/ (sample.airDensityKgPerCubicMeter() * sample.diskAreaSquareMeters());
		return 0.5 * (Math.sqrt(axialAdvanceSpeed * axialAdvanceSpeed + diskTerm) - axialAdvanceSpeed);
	}

	private static double expectedIdealMomentumPower(
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample
	) {
		return sample.thrustNewtons()
				* (Math.max(0.0, sample.axialAdvanceSpeedMetersPerSecond())
				+ expectedAxialMomentumInducedVelocity(sample));
	}

	private static void assertWakeMomentumAndSwirlClosure(
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample
	) {
		double axialSpeed = sample.nonnegativeAxialAdvanceSpeedMetersPerSecond();
		double wakeDelta = sample.farWakeAxialVelocityMetersPerSecond() - axialSpeed;
		assertEquals(2.0 * sample.idealInducedVelocityMetersPerSecond(), wakeDelta, 1.0e-15);
		assertEquals(axialSpeed + sample.idealInducedVelocityMetersPerSecond(),
				sample.actuatorDiskAxialVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(
				sample.airDensityKgPerCubicMeter()
						* sample.diskAreaSquareMeters()
						* sample.actuatorDiskAxialVelocityMetersPerSecond(),
				sample.diskMassFlowKilogramsPerSecond(),
				1.0e-15
		);
		assertEquals(sample.thrustNewtons(), sample.axialMomentumThrustNewtons(), 1.0e-12);
		assertEquals(0.0, sample.axialMomentumThrustResidualNewtons(), 1.0e-12);
		assertEquals(0.0, sample.axialMomentumThrustResidualFraction(), 1.0e-12);
		assertEquals(sample.thrustNewtons(),
				sample.diskMassFlowKilogramsPerSecond() * wakeDelta, 1.0e-12);
		assertEquals(sample.idealMomentumPowerWatts(), sample.axialMomentumPowerWatts(), 1.0e-12);
		assertEquals(0.0, sample.axialMomentumPowerResidualWatts(), 1.0e-12);
		assertEquals(0.0, sample.axialMomentumPowerResidualFraction(), 1.0e-12);
		assertEquals(
				0.5 * sample.diskMassFlowKilogramsPerSecond()
						* (sample.farWakeAxialVelocityMetersPerSecond()
						* sample.farWakeAxialVelocityMetersPerSecond()
						- axialSpeed * axialSpeed),
				sample.idealMomentumPowerWatts(),
				1.0e-12
		);
		assertEquals(sample.thrustNewtons() * axialSpeed,
				sample.usefulAxialThrustPowerWatts(), 1.0e-12);
		assertEquals(sample.thrustNewtons() * sample.idealInducedVelocityMetersPerSecond(),
				sample.idealInducedPowerWatts(), 1.0e-12);
		assertEquals(sample.usefulAxialThrustPowerWatts() + sample.idealInducedPowerWatts(),
				sample.idealMomentumPowerWatts(), 1.0e-12);
		assertEquals(sample.usefulAxialThrustPowerWatts() / sample.shaftPowerWatts(),
				sample.axialPropulsiveEfficiency(), 1.0e-15);
		assertEquals(sample.lookup().propulsiveEfficiencyEta(),
				sample.axialPropulsiveEfficiency(), 1.0e-15);
		assertEquals(
				sample.farWakeEquivalentRadiusMeters()
						* RotorSpec.BLADE_GEOMETRY_REFERENCE_STATION_FRACTION,
				sample.angularMomentumSwirlRadiusMeters(),
				1.0e-15
		);
		assertEquals(sample.farWakeContractedAreaSquareMeters() / sample.diskAreaSquareMeters(),
				sample.farWakeContractedAreaOverDiskArea(), 1.0e-15);
		assertEquals(sample.farWakeEquivalentRadiusMeters() / sample.rotorRadiusMeters(),
				sample.farWakeEquivalentRadiusOverRotorRadius(), 1.0e-15);
		assertEquals(
				Math.abs(sample.shaftTorqueNewtonMeters()),
				sample.diskMassFlowKilogramsPerSecond()
						* sample.angularMomentumSwirlRadiusMeters()
						* sample.wakeTangentialVelocityMetersPerSecond(),
				1.0e-15
		);
		assertEquals(Math.abs(sample.shaftTorqueNewtonMeters()),
				sample.wakeAngularMomentumTorqueNewtonMeters(), 1.0e-15);
		assertEquals(0.0, sample.wakeAngularMomentumTorqueResidualNewtonMeters(), 1.0e-15);
		assertEquals(0.0, sample.wakeAngularMomentumTorqueResidualFraction(), 1.0e-15);
		assertEquals(
				sample.diskMassFlowKilogramsPerSecond()
						* sample.angularMomentumSwirlRadiusMeters()
						* sample.angularMomentumSwirlRadiusMeters()
						* sample.wakeTangentialVelocityMetersPerSecond()
						* sample.wakeTangentialVelocityMetersPerSecond()
						/ (sample.farWakeEquivalentRadiusMeters()
						* sample.farWakeEquivalentRadiusMeters()),
				sample.wakeSwirlKineticPowerWatts(),
				1.0e-15
		);
		assertEquals(sample.idealMomentumPowerWatts() + sample.wakeSwirlKineticPowerWatts(),
				sample.totalWakeKineticPowerWatts(), 1.0e-15);
		assertEquals(sample.totalWakeKineticPowerWatts() / sample.shaftPowerWatts(),
				sample.totalWakeKineticPowerOverShaftPower(), 1.0e-15);
		assertEquals(sample.wakeSwirlKineticPowerWatts() / sample.shaftPowerWatts(),
				sample.wakeSwirlKineticPowerOverShaftPower(), 1.0e-15);
		assertEquals(sample.shaftPowerWatts() - sample.totalWakeKineticPowerWatts(),
				sample.totalWakeKineticPowerResidualWatts(), 1.0e-15);
		assertEquals(sample.totalWakeKineticPowerResidualWatts() / sample.shaftPowerWatts(),
				sample.totalWakeKineticPowerResidualFraction(), 1.0e-15);
		assertEquals(sample.lookup().powerCoefficientCp() / (2.0 * Math.PI),
				sample.torqueCoefficientCq(), 1.0e-18);
		assertEquals(expectedShaftTorqueFromCq(sample),
				sample.shaftTorqueNewtonMeters(), 1.0e-17);
		assertEquals(sample.shaftPowerWatts() / sample.angularVelocityRadiansPerSecond(),
				sample.shaftTorqueNewtonMeters(), 1.0e-17);
	}

	private static void assertFinitePositive(
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample
	) {
		assertTrue(Double.isFinite(sample.thrustNewtons()) && sample.thrustNewtons() > 0.0);
		assertTrue(Double.isFinite(sample.shaftPowerWatts()) && sample.shaftPowerWatts() > 0.0);
		assertTrue(Double.isFinite(sample.shaftTorqueNewtonMeters()) && sample.shaftTorqueNewtonMeters() > 0.0);
		assertTrue(Double.isFinite(sample.torqueCoefficientCq()) && sample.torqueCoefficientCq() > 0.0);
		assertTrue(Double.isFinite(sample.usefulAxialThrustPowerWatts())
				&& sample.usefulAxialThrustPowerWatts() > 0.0);
		assertTrue(Double.isFinite(sample.idealInducedPowerWatts())
				&& sample.idealInducedPowerWatts() > 0.0);
		assertTrue(Double.isFinite(sample.axialPropulsiveEfficiency())
				&& sample.axialPropulsiveEfficiency() > 0.0);
		assertTrue(Double.isFinite(sample.diskLoadingNewtonsPerSquareMeter())
				&& sample.diskLoadingNewtonsPerSquareMeter() > 0.0);
		assertTrue(Double.isFinite(sample.idealInducedVelocityMetersPerSecond())
				&& sample.idealInducedVelocityMetersPerSecond() > 0.0);
		assertTrue(Double.isFinite(sample.idealMomentumPowerOverShaftPower())
				&& sample.idealMomentumPowerOverShaftPower() > 0.0);
		assertTrue(Double.isFinite(sample.actuatorDiskAxialVelocityMetersPerSecond())
				&& sample.actuatorDiskAxialVelocityMetersPerSecond() > 0.0);
		assertTrue(Double.isFinite(sample.diskMassFlowKilogramsPerSecond())
				&& sample.diskMassFlowKilogramsPerSecond() > 0.0);
		assertTrue(Double.isFinite(sample.farWakeAxialVelocityMetersPerSecond())
				&& sample.farWakeAxialVelocityMetersPerSecond() > 0.0);
		assertTrue(Double.isFinite(sample.farWakeContractedAreaSquareMeters())
				&& sample.farWakeContractedAreaSquareMeters() > 0.0);
		assertTrue(Double.isFinite(sample.farWakeEquivalentRadiusMeters())
				&& sample.farWakeEquivalentRadiusMeters() > 0.0);
		assertTrue(Double.isFinite(sample.angularMomentumSwirlRadiusMeters())
				&& sample.angularMomentumSwirlRadiusMeters() > 0.0);
		assertTrue(Double.isFinite(sample.wakeTangentialVelocityMetersPerSecond())
				&& sample.wakeTangentialVelocityMetersPerSecond() > 0.0);
		assertTrue(Double.isFinite(sample.wakeAngularMomentumTorqueNewtonMeters())
				&& sample.wakeAngularMomentumTorqueNewtonMeters() > 0.0);
		assertTrue(Double.isFinite(sample.wakeAngularMomentumTorqueResidualNewtonMeters()));
		assertTrue(Double.isFinite(sample.wakeAngularMomentumTorqueResidualFraction()));
		assertTrue(Double.isFinite(sample.wakeSwirlKineticPowerWatts())
				&& sample.wakeSwirlKineticPowerWatts() > 0.0);
		assertTrue(Double.isFinite(sample.totalWakeKineticPowerWatts())
				&& sample.totalWakeKineticPowerWatts() > 0.0);
		assertTrue(Double.isFinite(sample.totalWakeKineticPowerOverShaftPower())
				&& sample.totalWakeKineticPowerOverShaftPower() > 0.0);
		assertTrue(Double.isFinite(sample.wakeSwirlKineticPowerOverShaftPower())
				&& sample.wakeSwirlKineticPowerOverShaftPower() > 0.0);
		assertTrue(Double.isFinite(sample.totalWakeKineticPowerResidualWatts()));
		assertTrue(Double.isFinite(sample.totalWakeKineticPowerResidualFraction()));
		assertTrue(Double.isFinite(sample.shaftPowerResidualWatts()));
		assertTrue(Double.isFinite(sample.shaftPowerResidualFraction()));
	}

	private static double apDroneDiameterMeters() {
		return DroneConfig.apDrone().rotors().get(0).radiusMeters() * 2.0;
	}

}
