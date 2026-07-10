package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UiucDa4002MeasuredRotorForceModelTest {
	private static final double RHO = 1.225;
	private static final double DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.81e-5;
	private static final UiucDa4002MeasuredRotorModel.Propeller PROPELLER =
			UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75;

	@Test
	void hoverClosesBodyFrameForceReactionTorqueAndMomentArm() {
		Vec3 position = new Vec3(0.18, -0.03, -0.11);
		Vec3 axis = new Vec3(0.20, 1.0, 0.35).normalized();
		Vec3 momentReference = new Vec3(-0.04, 0.02, 0.06);
		RotorSpec rotor = rotor(position, axis, 1, PROPELLER.diameterMeters() * 0.5);
		UiucDa4002MeasuredRotorForceModel.ForceSample sample =
				UiucDa4002MeasuredRotorForceModel.sample(
						new UiucDa4002MeasuredRotorForceModel.Query(
								PROPELLER,
								rotor,
								4_000.0,
								RHO,
								DYNAMIC_VISCOSITY_PASCAL_SECONDS,
								momentReference,
								Vec3.ZERO
						)
				);
		UiucDa4002MeasuredRotorModel.DimensionalSample dimensional =
				sample.dimensionalSample().orElseThrow();

		assertEquals(UiucDa4002MeasuredRotorForceModel.ForceApplicationStatus
				.APPLIED_AXIAL_MEASURED, sample.forceApplicationStatus());
		assertFalse(sample.blocked());
		assertFalse(sample.referenceOnly());
		assertTrue(sample.runtimeForceEligible());
		assertEquals(0.0, sample.signedAdvanceRatioJ(), 0.0);
		assertEquals(0.0, sample.inflowAngleRadians(), 0.0);
		assertTrue(dimensional.thrustNewtons() > 0.0);
		assertTrue(dimensional.shaftPowerWatts() > 0.0);
		assertTrue(dimensional.shaftTorqueNewtonMeters() > 0.0);
		assertVectorEquals(axis.multiply(dimensional.thrustNewtons()),
				sample.referenceThrustForceBodyNewtons(), 1.0e-14);
		assertVectorEquals(axis.multiply(dimensional.shaftTorqueNewtonMeters()),
				sample.referenceReactionTorqueBodyNewtonMeters(), 1.0e-14);
		assertVectorEquals(position.subtract(momentReference), sample.momentArmBodyMeters(), 0.0);
		assertVectorEquals(sample.momentArmBodyMeters().cross(
				sample.referenceThrustForceBodyNewtons()),
				sample.referenceThrustMomentBodyNewtonMeters(), 1.0e-14);
		assertVectorEquals(sample.referenceThrustMomentBodyNewtonMeters().add(
				sample.referenceReactionTorqueBodyNewtonMeters()),
				sample.referenceTotalTorqueBodyNewtonMeters(), 1.0e-14);
		assertVectorEquals(sample.referenceThrustForceBodyNewtons(),
				sample.appliedThrustForceBodyNewtons(), 0.0);
		assertVectorEquals(sample.referenceTotalTorqueBodyNewtonMeters(),
				sample.appliedTotalTorqueBodyNewtonMeters(), 0.0);
	}

	@Test
	void axialForwardFlowMatchesMeasuredScalarSurfaceAndSpinSign() {
		double rpm = 3_500.0;
		double advanceRatio = 0.40;
		Vec3 axis = new Vec3(-0.15, 0.96, 0.24).normalized();
		RotorSpec rotor = rotor(new Vec3(-0.12, 0.01, 0.09), axis, -1,
				PROPELLER.diameterMeters() * 0.5);
		double axialSpeed = advanceRatio * rpm / 60.0 * PROPELLER.diameterMeters();
		UiucDa4002MeasuredRotorForceModel.ForceSample sample =
				UiucDa4002MeasuredRotorForceModel.sample(
						PROPELLER,
						rotor,
						rpm,
						RHO,
						DYNAMIC_VISCOSITY_PASCAL_SECONDS,
						axis.multiply(axialSpeed)
				);
		UiucDa4002MeasuredRotorModel.DimensionalSample expected =
				UiucDa4002MeasuredRotorModel.sample(
						PROPELLER,
						advanceRatio,
						rpm,
						RHO,
						DYNAMIC_VISCOSITY_PASCAL_SECONDS
				);
		UiucDa4002MeasuredRotorModel.DimensionalSample actual =
				sample.dimensionalSample().orElseThrow();

		assertEquals(UiucDa4002MeasuredRotorForceModel.ForceApplicationStatus
				.APPLIED_AXIAL_MEASURED, sample.forceApplicationStatus());
		assertEquals(advanceRatio, sample.signedAdvanceRatioJ(), 1.0e-15);
		assertEquals(axialSpeed, sample.signedAxialAdvanceSpeedMetersPerSecond(), 1.0e-14);
		assertEquals(expected.thrustNewtons(), actual.thrustNewtons(), 1.0e-14);
		assertEquals(expected.shaftPowerWatts(), actual.shaftPowerWatts(), 1.0e-14);
		assertEquals(expected.shaftTorqueNewtonMeters(), actual.shaftTorqueNewtonMeters(),
				1.0e-14);
		assertEquals(-actual.shaftTorqueNewtonMeters(),
				sample.referenceReactionTorqueBodyNewtonMeters().dot(axis), 1.0e-14);
		assertEquals(actual.shaftTorqueNewtonMeters() / actual.thrustNewtons(),
				sample.yawTorquePerThrustMeterEquivalent(), 1.0e-14);
		assertTrue(sample.referenceThrustForceBodyNewtons().isFinite());
		assertTrue(sample.referenceTotalTorqueBodyNewtonMeters().isFinite());
	}

	@Test
	void transverseFlowRetainsAxialReferenceButCannotApplyIt() {
		double rpm = 4_000.0;
		double advanceRatio = 0.40;
		double axialSpeed = advanceRatio * rpm / 60.0 * PROPELLER.diameterMeters();
		RotorSpec rotor = rotor(Vec3.ZERO, new Vec3(0.0, 1.0, 0.0), 1,
				PROPELLER.diameterMeters() * 0.5);
		UiucDa4002MeasuredRotorForceModel.ForceSample sample =
				UiucDa4002MeasuredRotorForceModel.sample(
						PROPELLER,
						rotor,
						rpm,
						RHO,
						DYNAMIC_VISCOSITY_PASCAL_SECONDS,
						new Vec3(2.0, axialSpeed, -1.0)
				);
		UiucDa4002MeasuredRotorForceModel.ForceSample numericalZeroAxial =
				UiucDa4002MeasuredRotorForceModel.sample(
						PROPELLER,
						rotor,
						rpm,
						RHO,
						DYNAMIC_VISCOSITY_PASCAL_SECONDS,
						new Vec3(2.0, -5.0e-10, 0.0)
				);

		assertEquals(UiucDa4002MeasuredRotorForceModel.ForceApplicationStatus
				.REFERENCE_ONLY_TRANSVERSE_INFLOW, sample.forceApplicationStatus());
		assertFalse(sample.blocked());
		assertTrue(sample.referenceOnly());
		assertFalse(sample.runtimeForceEligible());
		assertEquals(advanceRatio, sample.signedAdvanceRatioJ(), 1.0e-15);
		assertVectorEquals(new Vec3(2.0, 0.0, -1.0),
				sample.transverseAirVelocityBodyMetersPerSecond(), 1.0e-15);
		assertEquals(Math.sqrt(5.0), sample.transverseAirSpeedMetersPerSecond(), 1.0e-15);
		assertEquals(Math.atan2(Math.sqrt(5.0), axialSpeed), sample.inflowAngleRadians(),
				1.0e-15);
		assertTrue(sample.referenceThrustForceBodyNewtons().length() > 0.0);
		assertVectorEquals(Vec3.ZERO, sample.appliedThrustForceBodyNewtons(), 0.0);
		assertVectorEquals(Vec3.ZERO, sample.appliedTotalTorqueBodyNewtonMeters(), 0.0);
		assertEquals(UiucDa4002MeasuredRotorForceModel.ForceApplicationStatus
				.REFERENCE_ONLY_TRANSVERSE_INFLOW,
				numericalZeroAxial.forceApplicationStatus());
		assertEquals(0.0, numericalZeroAxial.signedAdvanceRatioJ(), 0.0);
	}

	@Test
	void highAdvanceReverseFlowDiameterMismatchAndNegativeTailRemainExplicit() {
		RotorSpec rotor = rotor(Vec3.ZERO, new Vec3(0.0, 1.0, 0.0), 1,
				PROPELLER.diameterMeters() * 0.5);
		UiucDa4002MeasuredRotorForceModel.ForceSample highAdvance = axialSample(
				rotor,
				4_000.0,
				0.875
		);
		UiucDa4002MeasuredRotorForceModel.ForceSample negativeTail = axialSample(
				rotor,
				5_000.0,
				0.914534
		);
		UiucDa4002MeasuredRotorForceModel.ForceSample reverse =
				UiucDa4002MeasuredRotorForceModel.sample(
						PROPELLER,
						rotor,
						4_000.0,
						RHO,
						DYNAMIC_VISCOSITY_PASCAL_SECONDS,
						new Vec3(0.0, -1.0, 0.0)
				);
		RotorSpec wrongDiameter = rotor(Vec3.ZERO, new Vec3(0.0, 1.0, 0.0), 1,
				PROPELLER.diameterMeters() * 0.5 + 0.005);
		UiucDa4002MeasuredRotorForceModel.ForceSample mismatch =
				UiucDa4002MeasuredRotorForceModel.sample(
						PROPELLER,
						wrongDiameter,
						4_000.0,
						RHO,
						DYNAMIC_VISCOSITY_PASCAL_SECONDS,
						Vec3.ZERO
				);

		assertEquals(UiucDa4002MeasuredRotorForceModel.ForceApplicationStatus
				.BLOCKED_COEFFICIENT_SURFACE, highAdvance.forceApplicationStatus());
		assertTrue(highAdvance.blocked());
		assertTrue(highAdvance.coefficientSampleAvailable());
		assertTrue(highAdvance.lookup().orElseThrow().blocked());
		assertFalse(highAdvance.clamped());
		assertVectorEquals(Vec3.ZERO, highAdvance.referenceThrustForceBodyNewtons(), 0.0);

		assertEquals(UiucDa4002MeasuredRotorForceModel.ForceApplicationStatus
				.REFERENCE_ONLY_NON_POSITIVE_THRUST, negativeTail.forceApplicationStatus());
		assertFalse(negativeTail.blocked());
		assertTrue(negativeTail.referenceOnly());
		assertTrue(negativeTail.referenceThrustForceBodyNewtons().dot(
				rotor.thrustAxisBody()) < 0.0);
		assertTrue(negativeTail.referenceReactionTorqueBodyNewtonMeters().length() > 0.0);
		assertVectorEquals(Vec3.ZERO, negativeTail.appliedThrustForceBodyNewtons(), 0.0);
		assertVectorEquals(Vec3.ZERO, negativeTail.appliedTotalTorqueBodyNewtonMeters(), 0.0);

		assertEquals(UiucDa4002MeasuredRotorForceModel.ForceApplicationStatus
				.BLOCKED_REVERSE_AXIAL_FLOW, reverse.forceApplicationStatus());
		assertTrue(reverse.blocked());
		assertFalse(reverse.coefficientSampleAvailable());
		assertEquals(Math.PI, reverse.inflowAngleRadians(), 0.0);
		assertVectorEquals(Vec3.ZERO, reverse.appliedThrustForceBodyNewtons(), 0.0);

		assertEquals(UiucDa4002MeasuredRotorForceModel.ForceApplicationStatus
				.BLOCKED_ROTOR_DIAMETER_MISMATCH, mismatch.forceApplicationStatus());
		assertTrue(mismatch.blocked());
		assertFalse(mismatch.coefficientSampleAvailable());
	}

	private static UiucDa4002MeasuredRotorForceModel.ForceSample axialSample(
			RotorSpec rotor,
			double rpm,
			double advanceRatio
	) {
		double axialSpeed = advanceRatio * rpm / 60.0 * PROPELLER.diameterMeters();
		return UiucDa4002MeasuredRotorForceModel.sample(
				PROPELLER,
				rotor,
				rpm,
				RHO,
				DYNAMIC_VISCOSITY_PASCAL_SECONDS,
				rotor.thrustAxisBody().multiply(axialSpeed)
		);
	}

	private static RotorSpec rotor(
			Vec3 position,
			Vec3 axis,
			int spinDirection,
			double radiusMeters
	) {
		return new RotorSpec(
				position,
				axis,
				spinDirection,
				100.0,
				1.0e-5,
				0.02,
				radiusMeters,
				PROPELLER.diameterMeters() * 0.75,
				0.0,
				0.0,
				0.0,
				1.0e-5,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				2
		);
	}

	private static void assertVectorEquals(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance);
		assertEquals(expected.y(), actual.y(), tolerance);
		assertEquals(expected.z(), actual.z(), tolerance);
	}
}
