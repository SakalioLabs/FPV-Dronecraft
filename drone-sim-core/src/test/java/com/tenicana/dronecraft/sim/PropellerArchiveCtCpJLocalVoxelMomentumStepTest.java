package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PropellerArchiveCtCpJLocalVoxelMomentumStepTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = 0.05;
	private static final double CELL_SIZE = 0.04;
	private static final double DT = 0.01;
	private static final double RPM_PER_RADIAN_PER_SECOND = 60.0 / (2.0 * Math.PI);

	@Test
	void hoverMomentumStepConservesSourceImpulseAndVelocityDeltaUnits() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample gridSample =
				conservativeGridForSignedAxialSpeed(
						"local_voxel_hover",
						0.0,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		Vec3 initialVelocity = new Vec3(0.40, -0.20, 0.10);

		PropellerArchiveCtCpJLocalVoxelMomentumStep.MomentumStepSample step =
				PropellerArchiveCtCpJLocalVoxelMomentumStep.step(
						gridSample,
						RHO,
						DT,
						initialVelocity
				);
		PropellerArchiveCtCpJLocalVoxelMomentumStep.CellMomentumStep activeCell =
				step.activeCells().get(0);

		assertEquals(gridSample.activeCellCount(), step.activeCellCount());
		assertFalse(step.activeCells().isEmpty());
		assertTrue(step.maxVelocityDeltaMetersPerSecond() > 0.0);
		assertVectorEquals(gridSample.integratedBodyForceWorldNewtons(),
				step.totalMomentumRateWorldNewtons(), 1.0e-10);
		assertVectorEquals(gridSample.integratedBodyForceWorldNewtons().multiply(DT),
				step.totalImpulseWorldNewtonSeconds(), 1.0e-12);
		assertVectorEquals(gridSample.integratedWakeAngularMomentumTorqueWorldNewtonMeters(),
				step.totalWakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(gridSample.integratedWakeAngularMomentumTorqueWorldNewtonMeters().multiply(DT),
				step.totalWakeAngularMomentumImpulseWorldNewtonMeterSeconds(), 1.0e-14);
		assertVectorEquals(activeCell.sourceAccelerationWorldMetersPerSecondSquared().multiply(DT),
				activeCell.velocityDeltaWorldMetersPerSecond(), 1.0e-15);
		assertVectorEquals(initialVelocity.add(activeCell.velocityDeltaWorldMetersPerSecond()),
				activeCell.velocityAfterStepWorldMetersPerSecond(), 1.0e-15);
		assertVectorEquals(activeCell.targetWakeVelocityWorldMetersPerSecond()
						.subtract(activeCell.velocityAfterStepWorldMetersPerSecond()),
				activeCell.targetWakeVelocityResidualWorldMetersPerSecond(), 1.0e-15);
		assertEquals(activeCell.momentumRateWorldNewtons().y(),
				RHO * activeCell.cellVolumeCubicMeters()
						* activeCell.velocityDeltaWorldMetersPerSecond().y() / DT,
				1.0e-12);
		assertTrue(step.totalMomentumRateWorldNewtons().y() > 0.0);
	}

	@Test
	void forwardAndYawedHighAdvanceStepsKeepForceAndTorqueClosure() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample midGrid =
				conservativeGridForAdvanceRatio(
						"local_voxel_mid_j",
						0.4064,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		Quaternion yawedWorldProjection =
				new Quaternion(Math.cos(Math.PI / 4.0), 0.0, 0.0, Math.sin(Math.PI / 4.0));
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample highGrid =
				conservativeGridForAdvanceRatio(
						"local_voxel_high_j",
						0.73152,
						yawedWorldProjection,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		PropellerArchiveCtCpJLocalVoxelMomentumStep.MomentumStepSample midStep =
				PropellerArchiveCtCpJLocalVoxelMomentumStep.step(midGrid, RHO, DT);
		PropellerArchiveCtCpJLocalVoxelMomentumStep.MomentumStepSample highStep =
				PropellerArchiveCtCpJLocalVoxelMomentumStep.step(highGrid, RHO, DT);

		assertVectorEquals(midGrid.integratedBodyForceWorldNewtons(),
				midStep.totalMomentumRateWorldNewtons(), 1.0e-10);
		assertVectorEquals(highGrid.integratedBodyForceWorldNewtons(),
				highStep.totalMomentumRateWorldNewtons(), 1.0e-10);
		assertVectorEquals(highGrid.integratedWakeAngularMomentumTorqueWorldNewtonMeters().multiply(DT),
				highStep.totalWakeAngularMomentumImpulseWorldNewtonMeterSeconds(), 1.0e-14);
		assertTrue(midStep.totalMomentumRateWorldNewtons().y() > 0.0);
		assertTrue(midStep.totalImpulseWorldNewtonSeconds().y() > 0.0);
		assertTrue(highStep.totalMomentumRateWorldNewtons().x() < 0.0);
		assertEquals(0.0, highStep.totalMomentumRateWorldNewtons().y(), 1.0e-12);
		assertTrue(highStep.maxVelocityDeltaMetersPerSecond() > 0.0);
	}

	@Test
	void blockedOutOfEnvelopeGridProducesNoMomentumStep() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample blockedGrid =
				conservativeGridForAdvanceRatio(
						"local_voxel_blocked_high_j",
						1.20,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		PropellerArchiveCtCpJLocalVoxelMomentumStep.MomentumStepSample step =
				PropellerArchiveCtCpJLocalVoxelMomentumStep.step(blockedGrid, RHO, DT);

		assertEquals(1, blockedGrid.cells().size());
		assertEquals(0, step.activeCellCount());
		assertTrue(step.activeCells().isEmpty());
		assertEquals(0.0, step.maxVelocityDeltaMetersPerSecond(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, step.totalMomentumRateWorldNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, step.totalImpulseWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, step.totalWakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				step.cells().get(0).velocityAfterStepWorldMetersPerSecond(), 1.0e-15);
	}

	@Test
	void rejectsInvalidDensityTimeStepAndInitialVelocityCounts() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample gridSample =
				conservativeGridForSignedAxialSpeed(
						"local_voxel_invalid_inputs",
						0.0,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLocalVoxelMomentumStep.step(gridSample, 0.0, DT));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLocalVoxelMomentumStep.step(gridSample, RHO, 0.0));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLocalVoxelMomentumStep.step(
						gridSample,
						RHO,
						DT,
						List.of(Vec3.ZERO)
				));
	}

	private static PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample conservativeGridForAdvanceRatio(
			String caseName,
			double advanceRatioJ,
			Quaternion bodyToWorldOrientation,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverRpm = hoverRpm(config, rotor);
		double signedAxialSpeed = advanceRatioJ * hoverRpm / 60.0 * rotor.radiusMeters() * 2.0;
		return conservativeGridForSignedAxialSpeed(
				caseName,
				signedAxialSpeed,
				bodyToWorldOrientation,
				envelopePolicy
		);
	}

	private static PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample conservativeGridForSignedAxialSpeed(
			String caseName,
			double signedAxialSpeedMetersPerSecond,
			Quaternion bodyToWorldOrientation,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = hoverRpm(config, rotor) / RPM_PER_RADIAN_PER_SECOND;
		Vec3 relativeAirBody = rotorAxisBody(rotor).multiply(signedAxialSpeedMetersPerSecond);
		Vec3 vehicleVelocityWorld = bodyToWorldOrientation.rotate(relativeAirBody);
		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample sample =
				PropellerArchiveCtCpJWorldForceApplicationProvider.sampleStaticAnchoredConfigurationFromWorldKinematics(
						"apDrone",
						caseName,
						config,
						Vec3.ZERO,
						bodyToWorldOrientation,
						vehicleVelocityWorld,
						Vec3.ZERO,
						Vec3.ZERO,
						null,
						fill(config.rotors().size(), hoverOmega),
						RHO,
						envelopePolicy
				);
		PropellerArchiveCtCpJActuatorDiskSourceField field =
				sample.actuatorDiskSourceField(SOURCE_THICKNESS);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				field.enclosingVoxelGrid(CELL_SIZE, 1);
		return field.sampleConservativeVoxelGrid(grid, 3);
	}

	private static double hoverRpm(DroneConfig config, RotorSpec rotor) {
		double perRotorHoverThrust = config.massKg()
				* config.gravityMetersPerSecondSquared()
				/ config.rotors().size();
		return Math.sqrt(perRotorHoverThrust / rotor.thrustCoefficient()) * RPM_PER_RADIAN_PER_SECOND;
	}

	private static Vec3 rotorAxisBody(RotorSpec rotor) {
		Vec3 axis = rotor.thrustAxisBody();
		if (axis == null || !axis.isFinite() || axis.lengthSquared() <= 1.0e-9) {
			return new Vec3(0.0, 1.0, 0.0);
		}
		return axis.normalized();
	}

	private static double[] fill(int count, double value) {
		double[] values = new double[count];
		for (int i = 0; i < values.length; i++) {
			values[i] = value;
		}
		return values;
	}

	private static void assertVectorEquals(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance);
		assertEquals(expected.y(), actual.y(), tolerance);
		assertEquals(expected.z(), actual.z(), tolerance);
	}
}
