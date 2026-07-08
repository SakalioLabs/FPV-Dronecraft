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
		double activeCellMass = RHO * activeCell.cellVolumeCubicMeters();
		assertEquals(0.5 * activeCellMass
						* (activeCell.velocityAfterStepWorldMetersPerSecond().lengthSquared()
						- activeCell.initialVelocityWorldMetersPerSecond().lengthSquared()),
				activeCell.sourceMechanicalWorkEnergyJoules(), 1.0e-15);
		assertTrue(Double.isFinite(step.totalSourceMechanicalWorkEnergyJoules()));
		assertEquals(step.totalSourceMechanicalWorkEnergyJoules() / DT,
				step.meanSourceMechanicalPowerWatts(), 1.0e-12);
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
		assertEquals(0.0, step.totalSourceMechanicalWorkEnergyJoules(), 1.0e-15);
		assertEquals(0.0, step.meanSourceMechanicalPowerWatts(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, step.totalWakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				step.cells().get(0).velocityAfterStepWorldMetersPerSecond(), 1.0e-15);
	}

	@Test
	void massFluxResidenceStepReducesWakeResidualWithoutChangingSourceClosure() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample gridSample =
				conservativeGridForSignedAxialSpeed(
						"local_voxel_residence_hover",
						0.0,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		Vec3 initialVelocity = new Vec3(0.0, 0.15, -0.05);

		PropellerArchiveCtCpJLocalVoxelMomentumStep.MassFluxResidenceStepSample residence =
				PropellerArchiveCtCpJLocalVoxelMomentumStep.stepWithMassFluxResidence(
						gridSample,
						RHO,
						DT,
						SOURCE_THICKNESS,
						initialVelocity
				);
		PropellerArchiveCtCpJLocalVoxelMomentumStep.CellMassFluxResidenceStep activeCell =
				residence.activeCells().stream()
						.filter(cell -> cell.sourceMassFlowRateKilogramsPerSecond() > 0.0)
						.findFirst()
						.orElseThrow();
		PropellerArchiveCtCpJLocalVoxelMomentumStep.CellMomentumStep sourceStep =
				activeCell.sourceMomentumStep();

		assertEquals(gridSample.activeCellCount(), residence.activeCellCount());
		assertVectorEquals(gridSample.integratedBodyForceWorldNewtons(),
				residence.totalSourceMomentumRateWorldNewtons(), 1.0e-10);
		assertVectorEquals(gridSample.integratedBodyForceWorldNewtons().multiply(DT),
				residence.totalSourceImpulseWorldNewtonSeconds(), 1.0e-12);
		assertEquals(gridSample.integratedIdealMomentumPowerWatts(SOURCE_THICKNESS),
				residence.totalIdealMomentumPowerWatts(), 1.0e-12);
		assertEquals(gridSample.integratedWakeSwirlKineticPowerWatts(SOURCE_THICKNESS),
				residence.totalWakeSwirlKineticPowerWatts(), 1.0e-12);
		assertEquals(gridSample.integratedTotalWakeKineticPowerWatts(SOURCE_THICKNESS),
				residence.totalWakeKineticPowerWatts(), 1.0e-12);
		double cellIdealMomentumPower = 0.0;
		double cellWakeSwirlKineticPower = 0.0;
		double cellTotalWakeKineticPower = 0.0;
		for (PropellerArchiveCtCpJLocalVoxelMomentumStep.CellMassFluxResidenceStep cell : residence.cells()) {
			cellIdealMomentumPower += cell.sourceIdealMomentumPowerWatts();
			cellWakeSwirlKineticPower += cell.sourceWakeSwirlKineticPowerWatts();
			cellTotalWakeKineticPower += cell.sourceTotalWakeKineticPowerWatts();
		}
		assertEquals(cellIdealMomentumPower, residence.totalIdealMomentumPowerWatts(), 1.0e-12);
		assertEquals(cellWakeSwirlKineticPower, residence.totalWakeSwirlKineticPowerWatts(), 1.0e-12);
		assertEquals(cellTotalWakeKineticPower, residence.totalWakeKineticPowerWatts(), 1.0e-12);
		assertTrue(activeCell.sourceIdealMomentumPowerWatts() > 0.0);
		assertTrue(activeCell.sourceWakeSwirlKineticPowerWatts() >= 0.0);
		assertTrue(activeCell.sourceTotalWakeKineticPowerWatts()
				>= activeCell.sourceIdealMomentumPowerWatts());
		assertTrue(residence.totalSourceMassFlowRateKilogramsPerSecond() > 0.0);
		assertTrue(residence.maxResidenceAlpha() > 0.0);
		assertTrue(residence.maxResidenceAlpha() < 1.0);
		assertEquals(RHO * sourceStep.cellVolumeCubicMeters(),
				activeCell.cellAirMassKilograms(), 1.0e-15);
		assertEquals(sourceStep.cellVolumeCubicMeters() * sourceStep.sourceVolumeFraction() / SOURCE_THICKNESS,
				activeCell.sampledSourceAreaSquareMeters(), 1.0e-15);
		assertVectorEquals(sourceStep.targetWakeVelocityWorldMetersPerSecond()
						.subtract(sourceStep.velocityAfterStepWorldMetersPerSecond())
						.multiply(activeCell.residenceAlpha()),
				activeCell.throughFlowVelocityDeltaWorldMetersPerSecond(), 1.0e-15);
		assertVectorEquals(sourceStep.velocityAfterStepWorldMetersPerSecond()
						.add(activeCell.throughFlowVelocityDeltaWorldMetersPerSecond()),
				activeCell.velocityAfterResidenceWorldMetersPerSecond(), 1.0e-15);
		assertTrue(activeCell.targetWakeVelocityResidualAfterResidenceWorldMetersPerSecond().length()
				< sourceStep.targetWakeVelocityResidualWorldMetersPerSecond().length());
		assertVectorEquals(activeCell.throughFlowVelocityDeltaWorldMetersPerSecond()
						.multiply(activeCell.cellAirMassKilograms()),
				activeCell.throughFlowImpulseWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(activeCell.throughFlowImpulseWorldNewtonSeconds().multiply(1.0 / DT),
				activeCell.throughFlowMomentumRateWorldNewtons(), 1.0e-12);
		assertEquals(0.5 * activeCell.cellAirMassKilograms()
						* (activeCell.velocityAfterResidenceWorldMetersPerSecond().lengthSquared()
						- sourceStep.velocityAfterStepWorldMetersPerSecond().lengthSquared()),
				activeCell.throughFlowMechanicalWorkEnergyJoules(), 1.0e-15);
		assertEquals(residence.sourceMomentumSample().totalSourceMechanicalWorkEnergyJoules()
						+ residence.totalThroughFlowMechanicalWorkEnergyJoules(),
				residence.totalCombinedMechanicalWorkEnergyJoules(), 1.0e-15);
		assertEquals(residence.totalThroughFlowMechanicalWorkEnergyJoules() / DT,
				residence.meanThroughFlowMechanicalPowerWatts(), 1.0e-12);
		assertEquals(residence.totalCombinedMechanicalWorkEnergyJoules() / DT,
				residence.meanCombinedMechanicalPowerWatts(), 1.0e-12);
		assertVectorEquals(residence.totalSourceMomentumRateWorldNewtons()
						.add(residence.totalThroughFlowMomentumRateWorldNewtons()),
				residence.totalCombinedMomentumRateWorldNewtons(), 1.0e-12);
	}

	@Test
	void residenceMassFlowIntegratesCellAveragedFluxWithoutDoubleCoverageScaling() {
		double sourceThickness = 0.5;
		double cellVolume = 1.0;
		double sourceVolumeFraction = 0.25;
		double cellAveragedMassFlux = 2.0;
		Vec3 targetWakeVelocity = new Vec3(0.0, 8.0, 0.0);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						1,
						1,
						1
				);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample sourceCell =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample(
						0,
						0,
						0,
						grid.cellCenterWorldMeters(0, 0, 0),
						cellVolume,
						4,
						1,
						sourceVolumeFraction,
						Vec3.ZERO,
						Vec3.ZERO,
						0.0,
						cellAveragedMassFlux,
						0.0,
						0.0,
						0.0,
						targetWakeVelocity,
						Vec3.ZERO,
						targetWakeVelocity
				);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGrid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample(
						grid,
						2,
						List.of(sourceCell)
				);

		PropellerArchiveCtCpJLocalVoxelMomentumStep.MassFluxResidenceStepSample residence =
				PropellerArchiveCtCpJLocalVoxelMomentumStep.stepWithMassFluxResidence(
						sourceGrid,
						RHO,
						DT,
						sourceThickness,
						Vec3.ZERO
				);
		PropellerArchiveCtCpJLocalVoxelMomentumStep.CellMassFluxResidenceStep cell =
				residence.activeCells().get(0);
		double expectedProjectedArea = cellVolume / sourceThickness;
		double expectedSourceMassFlow = cellAveragedMassFlux * expectedProjectedArea;
		double expectedResidenceAlpha = 1.0 - Math.exp(-expectedSourceMassFlow * DT / (RHO * cellVolume));

		assertEquals(sourceVolumeFraction * expectedProjectedArea,
				cell.sampledSourceAreaSquareMeters(), 1.0e-15);
		assertEquals(expectedSourceMassFlow, cell.sourceMassFlowRateKilogramsPerSecond(), 1.0e-15);
		assertEquals(expectedSourceMassFlow, residence.totalSourceMassFlowRateKilogramsPerSecond(), 1.0e-15);
		assertEquals(expectedResidenceAlpha, cell.residenceAlpha(), 1.0e-15);
		assertVectorEquals(targetWakeVelocity.multiply(expectedResidenceAlpha),
				cell.throughFlowVelocityDeltaWorldMetersPerSecond(), 1.0e-15);
		assertTrue(cell.sourceMassFlowRateKilogramsPerSecond()
				> cellAveragedMassFlux * cell.sampledSourceAreaSquareMeters());
	}

	@Test
	void blockedMassFluxResidenceStepHasNoExchangeAndRejectsInvalidThickness() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample blockedGrid =
				conservativeGridForAdvanceRatio(
						"local_voxel_blocked_residence",
						1.20,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		PropellerArchiveCtCpJLocalVoxelMomentumStep.MassFluxResidenceStepSample residence =
				PropellerArchiveCtCpJLocalVoxelMomentumStep.stepWithMassFluxResidence(
						blockedGrid,
						RHO,
						DT,
						SOURCE_THICKNESS
				);

		assertEquals(0, residence.activeCellCount());
		assertEquals(0.0, residence.totalSourceMassFlowRateKilogramsPerSecond(), 1.0e-15);
		assertEquals(0.0, residence.totalIdealMomentumPowerWatts(), 1.0e-15);
		assertEquals(0.0, residence.totalWakeSwirlKineticPowerWatts(), 1.0e-15);
		assertEquals(0.0, residence.totalWakeKineticPowerWatts(), 1.0e-15);
		assertEquals(0.0, residence.cells().get(0).sourceIdealMomentumPowerWatts(), 1.0e-15);
		assertEquals(0.0, residence.cells().get(0).sourceWakeSwirlKineticPowerWatts(), 1.0e-15);
		assertEquals(0.0, residence.cells().get(0).sourceTotalWakeKineticPowerWatts(), 1.0e-15);
		assertEquals(0.0, residence.maxResidenceAlpha(), 1.0e-15);
		assertEquals(0.0, residence.totalThroughFlowMechanicalWorkEnergyJoules(), 1.0e-15);
		assertEquals(0.0, residence.totalCombinedMechanicalWorkEnergyJoules(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, residence.totalSourceMomentumRateWorldNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, residence.totalThroughFlowMomentumRateWorldNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, residence.totalCombinedMomentumRateWorldNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				residence.cells().get(0).velocityAfterResidenceWorldMetersPerSecond(), 1.0e-15);
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLocalVoxelMomentumStep.stepWithMassFluxResidence(
						blockedGrid,
						RHO,
						DT,
						0.0
				));
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
