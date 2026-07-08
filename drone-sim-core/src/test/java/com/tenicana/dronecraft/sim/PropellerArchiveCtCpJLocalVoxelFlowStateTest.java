package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PropellerArchiveCtCpJLocalVoxelFlowStateTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = 0.05;
	private static final double CELL_SIZE = 0.04;
	private static final double DT = 0.005;
	private static final double RPM_PER_RADIAN_PER_SECOND = 60.0 / (2.0 * Math.PI);

	@Test
	void advanceWithSourceMaterializesNextVoxelVelocityState() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample gridSample =
				conservativeGridForSignedAxialSpeed(
						"local_voxel_flow_state_hover",
						0.0,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				PropellerArchiveCtCpJLocalVoxelFlowState.calm(gridSample.gridSpec());

		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelFlowAdvance advance =
				state.advanceWithSource(gridSample, RHO, DT, SOURCE_THICKNESS);
		PropellerArchiveCtCpJLocalVoxelMomentumStep.CellMassFluxResidenceStep activeCell =
				advance.residenceStep().activeCells().get(0);

		assertEquals(gridSample.gridSpec(), advance.nextState().gridSpec());
		assertEquals(gridSample.cells().size(), advance.nextState().velocitiesWorldMetersPerSecond().size());
		assertVectorEquals(Vec3.ZERO,
				advance.previousState().velocityAt(activeCell.sourceMomentumStep().xIndex(),
						activeCell.sourceMomentumStep().yIndex(),
						activeCell.sourceMomentumStep().zIndex()),
				1.0e-15);
		assertVectorEquals(activeCell.velocityAfterResidenceWorldMetersPerSecond(),
				advance.nextState().velocityAt(activeCell.sourceMomentumStep().xIndex(),
						activeCell.sourceMomentumStep().yIndex(),
						activeCell.sourceMomentumStep().zIndex()),
				1.0e-15);
		assertVectorEquals(gridSample.integratedBodyForceWorldNewtons(),
				advance.totalSourceMomentumRateWorldNewtons(), 1.0e-10);
		assertVectorEquals(advance.totalSourceMomentumRateWorldNewtons()
						.add(advance.totalThroughFlowMomentumRateWorldNewtons()),
				advance.totalCombinedMomentumRateWorldNewtons(), 1.0e-12);
		assertTrue(advance.totalSourceMassFlowRateKilogramsPerSecond() > 0.0);
		assertTrue(advance.maxResidenceAlpha() > 0.0);
		assertTrue(advance.meanActiveWakeResidualAfterResidenceMetersPerSecond() > 0.0);
		assertEquals(gridSample.integratedIdealMomentumPowerWatts(SOURCE_THICKNESS),
				advance.totalIdealMomentumPowerWatts(), 1.0e-12);
		assertEquals(gridSample.integratedWakeSwirlKineticPowerWatts(SOURCE_THICKNESS),
				advance.totalWakeSwirlKineticPowerWatts(), 1.0e-12);
		assertEquals(gridSample.integratedTotalWakeKineticPowerWatts(SOURCE_THICKNESS),
				advance.totalWakeKineticPowerWatts(), 1.0e-12);
		assertEquals(advance.totalWakeSwirlKineticPowerWatts() * DT,
				advance.wakeSwirlKineticEnergyJoules(), 1.0e-12);
		assertEquals(advance.totalWakeKineticPowerWatts() * DT,
				advance.totalWakeKineticEnergyJoules(), 1.0e-12);
		assertTrue(advance.sourceMechanicalWorkEnergyJoules() > 0.0);
		assertEquals(advance.sourceMechanicalWorkEnergyJoules() / DT,
				advance.sourceMechanicalWorkPowerWatts(), 1.0e-12);
		assertTrue(Double.isFinite(advance.throughFlowMechanicalWorkEnergyJoules()));
		assertEquals(advance.sourceMechanicalWorkEnergyJoules()
						+ advance.throughFlowMechanicalWorkEnergyJoules(),
				advance.combinedMechanicalWorkEnergyJoules(), 1.0e-12);
		assertEquals(advance.combinedMechanicalWorkEnergyJoules() / DT,
				advance.combinedMechanicalWorkPowerWatts(), 1.0e-12);
		assertEquals(advance.flowKineticEnergyDeltaJoules() - advance.sourceMechanicalWorkEnergyJoules(),
				advance.flowKineticEnergyDeltaMinusSourceMechanicalWorkJoules(), 1.0e-12);
		assertEquals(0.0, advance.flowKineticEnergyDeltaMinusCombinedMechanicalWorkJoules(), 1.0e-12);
		assertEquals(0.0, state.totalKineticEnergyJoules(RHO), 1.0e-15);
		assertTrue(advance.nextState().totalKineticEnergyJoules(RHO) > 0.0);
		assertTrue(advance.nextState().maxSpeedMetersPerSecond() > 0.0);
	}

	@Test
	void advanceWithSourceSkipsSourceTermsInsideSolidCells() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample gridSample =
				conservativeGridForSignedAxialSpeed(
						"local_voxel_flow_state_solid_source_mask",
						0.0,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				PropellerArchiveCtCpJLocalVoxelFlowState.calm(gridSample.gridSpec());
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask solidMask =
				PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.fromWorldSolidBoxes(
						gridSample.gridSpec(),
						List.of(new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox(
								new Vec3(-10.0, -10.0, -10.0),
								new Vec3(10.0, 10.0, 10.0)))
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelFlowAdvance advance =
				state.advanceWithSource(gridSample, RHO, DT, SOURCE_THICKNESS, solidMask);

		assertTrue(gridSample.activeCellCount() > 0);
		assertEquals(gridSample.gridSpec().totalCellCount(), solidMask.solidCellCount());
		assertEquals(0, advance.residenceStep().activeCellCount());
		assertEquals(0, advance.residenceStep().sourceMomentumSample().activeCellCount());
		assertVectorEquals(Vec3.ZERO, advance.totalSourceMomentumRateWorldNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, advance.totalSourceImpulseWorldNewtonSeconds(), 1.0e-15);
		assertEquals(0.0, advance.totalSourceMassFlowRateKilogramsPerSecond(), 1.0e-15);
		assertEquals(0.0, advance.totalIdealMomentumPowerWatts(), 1.0e-15);
		assertEquals(0.0, advance.totalWakeSwirlKineticPowerWatts(), 1.0e-15);
		assertEquals(0.0, advance.totalWakeKineticPowerWatts(), 1.0e-15);
		assertEquals(0.0, advance.wakeSwirlKineticEnergyJoules(), 1.0e-15);
		assertEquals(0.0, advance.totalWakeKineticEnergyJoules(), 1.0e-15);
		assertEquals(0.0, advance.sourceMechanicalWorkEnergyJoules(), 1.0e-15);
		assertEquals(0.0, advance.sourceMechanicalWorkPowerWatts(), 1.0e-15);
		assertEquals(0.0, advance.throughFlowMechanicalWorkEnergyJoules(), 1.0e-15);
		assertEquals(0.0, advance.combinedMechanicalWorkEnergyJoules(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, advance.totalThroughFlowMomentumRateWorldNewtons(), 1.0e-15);
		assertEquals(0.0, advance.nextState().totalKineticEnergyJoules(RHO), 1.0e-15);
		assertEquals(0.0, advance.nextState().maxSpeedMetersPerSecond(), 1.0e-15);
	}

	@Test
	void advanceWithSourceScalesSourceTermsBySolidVolumeFraction() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						1,
						1,
						1
				);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample(
						grid,
						1,
						List.of(new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample(
								0,
								0,
								0,
								grid.cellCenterWorldMeters(0, 0, 0),
								grid.cellVolumeCubicMeters(),
								1,
								1,
								1.0,
								new Vec3(0.0, 10.0, 0.0),
								Vec3.ZERO,
								0.0,
								0.0,
								0.0,
								0.0,
								0.0,
								Vec3.ZERO,
								Vec3.ZERO,
								Vec3.ZERO
						))
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				PropellerArchiveCtCpJLocalVoxelFlowState.calm(grid);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask halfSolidButOpenWall =
				PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.fromWorldSolidBoxes(
						grid,
						List.of(new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox(
								new Vec3(0.0, 0.0, 0.0),
								new Vec3(0.5, 1.0, 1.0))),
						1.0
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelFlowAdvance advance =
				state.advanceWithSource(sourceGridSample, RHO, DT, SOURCE_THICKNESS, halfSolidButOpenWall);

		assertEquals(0, halfSolidButOpenWall.solidCellCount());
		assertEquals(0.5, halfSolidButOpenWall.solidVolumeFraction(0, 0, 0), 1.0e-15);
		assertEquals(0.5, halfSolidButOpenWall.openVolumeFractionCellIndex(0), 1.0e-15);
		assertEquals(1, advance.residenceStep().activeCellCount());
		assertEquals(1.0, advance.residenceStep().activeCells().get(0)
				.sourceMomentumStep().sourceVolumeFraction(), 1.0e-15);
		assertEquals(0.5, advance.residenceStep().activeCells().get(0)
				.sourceMomentumStep().cellVolumeCubicMeters(), 1.0e-15);
		assertVectorEquals(new Vec3(0.0, 5.0, 0.0),
				advance.totalSourceMomentumRateWorldNewtons(), 1.0e-15);
		assertVectorEquals(new Vec3(0.0, 5.0 * DT, 0.0),
				advance.totalSourceImpulseWorldNewtonSeconds(), 1.0e-15);
		assertEquals(10.0 * DT / RHO,
				advance.nextState().velocityAt(0, 0, 0).y(), 1.0e-15);
	}

	@Test
	void uniformStatePreservesVoxelOrderingForCellIndexLookups() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample gridSample =
				conservativeGridForSignedAxialSpeed(
						"local_voxel_flow_state_ordering",
						0.0,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		ArrayList<Vec3> velocities = new ArrayList<>(gridSample.cells().size());
		for (int i = 0; i < gridSample.cells().size(); i++) {
			velocities.add(new Vec3(i + 0.25, -i - 0.5, i * 0.125));
		}

		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(gridSample.gridSpec(), velocities);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample middleCell =
				gridSample.cells().get(gridSample.cells().size() / 2);
		int expectedIndex = (middleCell.yIndex() * gridSample.gridSpec().cellCountZ()
				+ middleCell.zIndex()) * gridSample.gridSpec().cellCountX()
				+ middleCell.xIndex();

		assertVectorEquals(velocities.get(expectedIndex),
				state.velocityAt(middleCell.xIndex(), middleCell.yIndex(), middleCell.zIndex()), 1.0e-15);
		assertThrows(IndexOutOfBoundsException.class,
				() -> state.velocityAt(gridSample.gridSpec().cellCountX(), 0, 0));
	}

	@Test
	void kineticEnergyDistributionReportsEnergyWeightedCentroidAndRmsRadius() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						2,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(
								new Vec3(2.0, 0.0, 0.0),
								new Vec3(0.0, 1.0, 0.0)
						)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask firstCellOnly =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask(
						grid,
						List.of(false, true)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.KineticEnergyDistributionMetrics distribution =
				state.kineticEnergyDistributionMetrics(1.0);
		PropellerArchiveCtCpJLocalVoxelFlowState.KineticEnergyDistributionMetrics maskedDistribution =
				state.kineticEnergyDistributionMetrics(1.0, firstCellOnly);
		PropellerArchiveCtCpJLocalVoxelFlowState.KineticEnergyDistributionMetrics calmDistribution =
				PropellerArchiveCtCpJLocalVoxelFlowState.calm(grid).kineticEnergyDistributionMetrics(1.0);

		assertEquals(2.5, distribution.totalKineticEnergyJoules(), 1.0e-15);
		assertVectorEquals(new Vec3(0.7, 0.5, 0.5),
				distribution.centroidWorldMeters(), 1.0e-15);
		assertEquals(0.4, distribution.rmsRadiusMeters(), 1.0e-15);
		assertEquals(2.0, maskedDistribution.totalKineticEnergyJoules(), 1.0e-15);
		assertVectorEquals(new Vec3(0.5, 0.5, 0.5),
				maskedDistribution.centroidWorldMeters(), 1.0e-15);
		assertEquals(0.0, maskedDistribution.rmsRadiusMeters(), 1.0e-15);
		assertVectorEquals(grid.gridCenterWorldMeters(), calmDistribution.centroidWorldMeters(), 1.0e-15);
		assertEquals(0.0, calmDistribution.rmsRadiusMeters(), 1.0e-15);
	}

	@Test
	void openBoundaryFluxReportsOutwardAndInwardVolumeAndMassFlow() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						2,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				PropellerArchiveCtCpJLocalVoxelFlowState.uniform(
						grid,
						new Vec3(2.0, -3.0, 4.0)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.OpenBoundaryFluxMetrics flux =
				state.openBoundaryFluxMetrics();
		PropellerArchiveCtCpJLocalVoxelFlowState.OpenBoundaryFluxMetrics physicalFlux =
				state.openBoundaryFluxMetrics(RHO);

		assertEquals(0.0, flux.netOutwardVolumeFlowRateCubicMetersPerSecond(), 1.0e-15);
		assertEquals(16.0, flux.outwardVolumeFlowRateCubicMetersPerSecond(), 1.0e-15);
		assertEquals(16.0, flux.inwardVolumeFlowRateCubicMetersPerSecond(), 1.0e-15);
		assertVectorEquals(new Vec3(2.0, 6.0, 8.0),
				flux.outwardAxisVolumeFlowRateCubicMetersPerSecond(), 1.0e-15);
		assertVectorEquals(new Vec3(2.0, 6.0, 8.0),
				flux.inwardAxisVolumeFlowRateCubicMetersPerSecond(), 1.0e-15);
		assertEquals(16.0 * RHO, flux.outwardMassFlowRateKilogramsPerSecond(RHO), 1.0e-15);
		assertEquals(16.0 * RHO, flux.inwardMassFlowRateKilogramsPerSecond(RHO), 1.0e-15);
		assertEquals(0.0, flux.netOutwardMassFlowRateKilogramsPerSecond(RHO), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, physicalFlux.netOutwardMomentumFluxWorldNewtons(), 1.0e-12);
		assertVectorEquals(new Vec3(32.0 * RHO, -48.0 * RHO, 64.0 * RHO),
				physicalFlux.outwardMomentumFluxWorldNewtons(), 1.0e-12);
		assertVectorEquals(physicalFlux.outwardMomentumFluxWorldNewtons(),
				physicalFlux.inwardMomentumFluxWorldNewtons(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				physicalFlux.netOutwardAngularMomentumFluxWorldNewtonMeters(), 1.0e-12);
		assertEquals(232.0 * RHO, physicalFlux.outwardKineticEnergyPowerWatts(), 1.0e-12);
		assertEquals(232.0 * RHO, physicalFlux.inwardKineticEnergyPowerWatts(), 1.0e-12);
		assertEquals(0.0, physicalFlux.netOutwardKineticEnergyPowerWatts(), 1.0e-12);
		assertThrows(IllegalArgumentException.class,
				() -> flux.outwardMassFlowRateKilogramsPerSecond(0.0));
		assertThrows(IllegalArgumentException.class,
				() -> state.openBoundaryFluxMetrics(0.0));
	}

	@Test
	void openBoundaryFluxIgnoresSolidCellsAndScalesPartialOpenCells() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						2,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(
								new Vec3(2.0, 0.0, 0.0),
								new Vec3(100.0, 0.0, 0.0)
						)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask mask =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask(
						grid,
						List.of(Boolean.FALSE, Boolean.TRUE),
						List.of(0.5, 1.0)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.OpenBoundaryFluxMetrics flux =
				state.openBoundaryFluxMetrics(RHO, mask);

		assertEquals(-1.0, flux.netOutwardVolumeFlowRateCubicMetersPerSecond(), 1.0e-15);
		assertEquals(0.0, flux.outwardVolumeFlowRateCubicMetersPerSecond(), 1.0e-15);
		assertEquals(1.0, flux.inwardVolumeFlowRateCubicMetersPerSecond(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, flux.outwardAxisVolumeFlowRateCubicMetersPerSecond(), 1.0e-15);
		assertVectorEquals(new Vec3(1.0, 0.0, 0.0),
				flux.inwardAxisVolumeFlowRateCubicMetersPerSecond(), 1.0e-15);
		assertVectorEquals(new Vec3(-2.0 * RHO, 0.0, 0.0),
				flux.netOutwardMomentumFluxWorldNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, flux.netOutwardAngularMomentumFluxWorldNewtonMeters(), 1.0e-15);
		assertEquals(-2.0 * RHO, flux.netOutwardKineticEnergyPowerWatts(), 1.0e-15);
		assertEquals(0.0, flux.outwardKineticEnergyPowerWatts(), 1.0e-15);
		assertEquals(2.0 * RHO, flux.inwardKineticEnergyPowerWatts(), 1.0e-15);
	}

	@Test
	void divergenceIntegralScalesPartialOpenInternalFacesLikeBoundaryFlux() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						2,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(
								Vec3.ZERO,
								new Vec3(10.0, 0.0, 0.0)
						)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask mask =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask(
						grid,
						List.of(Boolean.FALSE, Boolean.FALSE),
						List.of(0.0, 0.5)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceMetrics divergence =
				state.divergenceMetrics(mask);
		PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceIntegralMetrics integrals =
				state.divergenceIntegralMetrics(mask);
		PropellerArchiveCtCpJLocalVoxelFlowState.OpenBoundaryFluxMetrics boundaryFlux =
				state.openBoundaryFluxMetrics(mask);

		assertEquals(5.0, divergence.maxAbsDivergencePerSecond(), 1.0e-15);
		assertEquals(Math.sqrt(12.5), divergence.rmsDivergencePerSecond(), 1.0e-15);
		assertEquals(10.0 / 3.0, divergence.meanDivergencePerSecond(), 1.0e-15);
		assertEquals(5.0, integrals.netVolumeFlowRateCubicMetersPerSecond(), 1.0e-15);
		assertEquals(5.0, integrals.grossAbsVolumeFlowRateCubicMetersPerSecond(), 1.0e-15);
		assertEquals(1.5, integrals.openVolumeCubicMeters(), 1.0e-15);
		assertEquals(5.0 * RHO, integrals.netMassFlowRateKilogramsPerSecond(RHO), 1.0e-15);
		assertEquals(boundaryFlux.netOutwardVolumeFlowRateCubicMetersPerSecond(),
				integrals.netVolumeFlowRateCubicMetersPerSecond(), 1.0e-15);
	}

	@Test
	void solidMaskZerosSolidCellVelocityAndReportsMomentumLoss() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						2,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(
								new Vec3(2.0, 0.0, 0.0),
								new Vec3(0.0, 3.0, 0.0)
						)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask mask =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask(
						grid,
						List.of(Boolean.FALSE, Boolean.TRUE),
						List.of(0.0, 0.5)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.SolidBoundaryStep step =
				state.applySolidMask(mask, RHO);

		assertEquals(1, mask.solidCellCount());
		assertTrue(!mask.isSolid(0, 0, 0));
		assertTrue(mask.isSolid(1, 0, 0));
		assertEquals(0.5, mask.openVolumeFractionCellIndex(1), 1.0e-15);
		assertEquals(1, step.solidCellCount());
		assertEquals(1, step.clampedCellCount());
		assertVectorEquals(new Vec3(2.0, 0.0, 0.0), step.nextState().velocityAt(0, 0, 0), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, step.nextState().velocityAt(1, 0, 0), 1.0e-15);
		assertVectorEquals(new Vec3(2.0 * RHO, 1.5 * RHO, 0.0),
				step.totalMomentumBeforeWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(new Vec3(2.0 * RHO, 0.0, 0.0),
				step.totalMomentumAfterWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(new Vec3(0.0, -1.5 * RHO, 0.0),
				step.momentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(step.momentumResidualWorldNewtonSeconds(),
				step.boundaryImpulseOnFlowWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(new Vec3(0.0, 1.5 * RHO, 0.0),
				step.flowImpulseOnSolidBoundaryWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(new Vec3(-0.75 * RHO, RHO, 1.25 * RHO),
				step.angularMomentumBeforeWorldNewtonMeterSeconds(Vec3.ZERO), 1.0e-15);
		assertVectorEquals(new Vec3(0.0, RHO, -RHO),
				step.angularMomentumAfterWorldNewtonMeterSeconds(Vec3.ZERO), 1.0e-15);
		assertVectorEquals(new Vec3(0.75 * RHO, 0.0, -2.25 * RHO),
				step.angularMomentumResidualWorldNewtonMeterSeconds(Vec3.ZERO), 1.0e-15);
		assertVectorEquals(step.angularMomentumResidualWorldNewtonMeterSeconds(Vec3.ZERO),
				step.boundaryAngularImpulseOnFlowWorldNewtonMeterSeconds(Vec3.ZERO), 1.0e-15);
		assertVectorEquals(new Vec3(-0.75 * RHO, 0.0, 2.25 * RHO),
				step.flowAngularImpulseOnSolidBoundaryWorldNewtonMeterSeconds(Vec3.ZERO), 1.0e-15);
		assertEquals(0.5 * RHO * 4.0 + 0.5 * RHO * 0.5 * 9.0,
				step.kineticEnergyBeforeJoules(), 1.0e-15);
		assertEquals(0.5 * RHO * 4.0, step.kineticEnergyAfterJoules(), 1.0e-15);
		assertTrue(step.kineticEnergyDeltaJoules() < 0.0);
		assertEquals(-step.kineticEnergyDeltaJoules(), step.dissipatedKineticEnergyJoules(), 1.0e-15);
	}

	@Test
	void totalAngularMomentumUsesCellCentersAndOpenVolumeMass() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						2,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(
								new Vec3(0.0, 2.0, 0.0),
								new Vec3(0.0, 0.0, 3.0)
						)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask halfOpenSecondCell =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask(
						grid,
						List.of(Boolean.FALSE, Boolean.TRUE),
						List.of(0.0, 0.5)
				);

		assertEquals(new Vec3(1.0, 0.5, 0.5), grid.gridCenterWorldMeters());
		assertVectorEquals(
				new Vec3(0.5 * RHO, -4.5 * RHO, RHO),
				state.totalAngularMomentumWorldNewtonMeterSeconds(RHO, Vec3.ZERO),
				1.0e-15);
		assertVectorEquals(
				new Vec3(-0.25 * RHO, -2.25 * RHO, RHO),
				state.totalAngularMomentumWorldNewtonMeterSeconds(RHO, Vec3.ZERO, halfOpenSecondCell),
				1.0e-15);
		assertThrows(IllegalArgumentException.class,
				() -> state.totalAngularMomentumWorldNewtonMeterSeconds(0.0, Vec3.ZERO));
	}

	@Test
	void vorticityMetricsRecoverSolidBodyRotationCurl() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						3,
						3,
						1
				);
		double omega = 4.0;
		double axialVelocity = 3.0;
		ArrayList<Vec3> velocities = new ArrayList<>(grid.totalCellCount());
		for (int y = 0; y < grid.cellCountY(); y++) {
			for (int z = 0; z < grid.cellCountZ(); z++) {
				for (int x = 0; x < grid.cellCountX(); x++) {
					Vec3 center = grid.cellCenterWorldMeters(x, y, z);
					velocities.add(new Vec3(-omega * center.y(), omega * center.x(), axialVelocity));
				}
			}
		}
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(grid, velocities);
		PropellerArchiveCtCpJLocalVoxelFlowState.VorticityMetrics metrics = state.vorticityMetrics();
		PropellerArchiveCtCpJLocalVoxelFlowState.VorticityIntegralMetrics integrals =
				state.vorticityIntegralMetrics();
		double openVolume = grid.totalCellCount() * grid.cellVolumeCubicMeters();
		double expectedVorticity = 2.0 * omega;

		assertVectorEquals(new Vec3(0.0, 0.0, expectedVorticity), state.vorticityAt(1, 1, 0), 1.0e-15);
		assertEquals(expectedVorticity, metrics.maxMagnitudePerSecond(), 1.0e-15);
		assertEquals(expectedVorticity, metrics.rmsMagnitudePerSecond(), 1.0e-15);
		assertVectorEquals(new Vec3(0.0, 0.0, expectedVorticity),
				metrics.meanVorticityWorldPerSecond(), 1.0e-15);
		assertEquals(0.5 * expectedVorticity * expectedVorticity * openVolume,
				integrals.enstrophyCubicMetersPerSecondSquared(), 1.0e-15);
		assertEquals(axialVelocity * expectedVorticity * openVolume,
				integrals.helicityFourthMetersPerSecondSquared(), 1.0e-15);
		assertEquals(axialVelocity * expectedVorticity,
				integrals.meanHelicityDensityMetersPerSecondSquared(), 1.0e-15);
	}

	@Test
	void solidMaskRasterizesWorldSolidBoxesIntoVoxelCells() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						3,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox block =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox(
						new Vec3(0.2, 0.0, 0.0),
						new Vec3(1.2, 1.0, 1.0)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox faceTouching =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox(
						new Vec3(3.0, 0.0, 0.0),
						new Vec3(4.0, 1.0, 1.0)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask anyOverlap =
				PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.fromWorldSolidBoxes(
						grid,
						List.of(block, faceTouching)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask halfCellThreshold =
				PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.fromWorldSolidBoxes(
						grid,
						List.of(block),
						0.5
				);

		assertEquals(2, anyOverlap.solidCellCount());
		assertTrue(anyOverlap.isSolid(0, 0, 0));
		assertTrue(anyOverlap.isSolid(1, 0, 0));
		assertTrue(!anyOverlap.isSolid(2, 0, 0));
		assertEquals(0.8, anyOverlap.solidVolumeFraction(0, 0, 0), 1.0e-15);
		assertEquals(0.2, anyOverlap.solidVolumeFraction(1, 0, 0), 1.0e-15);
		assertEquals(0.0, anyOverlap.solidVolumeFraction(2, 0, 0), 1.0e-15);
		assertEquals(1, halfCellThreshold.solidCellCount());
		assertTrue(halfCellThreshold.isSolid(0, 0, 0));
		assertTrue(!halfCellThreshold.isSolid(1, 0, 0));
		assertEquals(0.2, halfCellThreshold.solidVolumeFraction(1, 0, 0), 1.0e-15);
		assertEquals(0.8, halfCellThreshold.openVolumeFractionCellIndex(1), 1.0e-15);
		assertEquals(0,
				PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.fromWorldSolidBoxes(
						grid,
						List.of()
				).solidCellCount());
	}

	@Test
	void solidMaskUsesUnionVolumeForOverlappingWorldSolidBoxes() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						1,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox first =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox(
						new Vec3(0.0, 0.0, 0.0),
						new Vec3(0.4, 1.0, 1.0)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox second =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox(
						new Vec3(0.2, 0.0, 0.0),
						new Vec3(0.6, 1.0, 1.0)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask unionMask =
				PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.fromWorldSolidBoxes(
						grid,
						List.of(first, second),
						0.75
				);

		assertEquals(0.6, unionMask.solidVolumeFraction(0, 0, 0), 1.0e-15);
		assertEquals(0.4, unionMask.openVolumeFractionCellIndex(0), 1.0e-15);
		assertTrue(!unionMask.isSolid(0, 0, 0));
		assertEquals(0, unionMask.solidCellCount());
	}

	@Test
	void openSolidMaskIsNoOpAndRejectsInvalidInputs() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						0.5,
						2,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				PropellerArchiveCtCpJLocalVoxelFlowState.uniform(grid, new Vec3(0.25, -0.5, 0.75));
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask openMask =
				PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.open(grid);

		PropellerArchiveCtCpJLocalVoxelFlowState.SolidBoundaryStep step =
				state.applySolidMask(openMask, RHO);

		assertEquals(0, step.solidCellCount());
		assertEquals(0, step.clampedCellCount());
		assertEquals(state, step.nextState());
		assertVectorEquals(Vec3.ZERO, step.momentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, step.flowImpulseOnSolidBoundaryWorldNewtonSeconds(), 1.0e-15);
		assertEquals(state.totalKineticEnergyJoules(RHO), step.kineticEnergyAfterJoules(), 1.0e-15);
		assertEquals(0.0, step.dissipatedKineticEnergyJoules(), 1.0e-15);

		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec otherGrid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						0.5,
						1,
						1,
						1
				);
		assertThrows(IllegalArgumentException.class,
				() -> new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask(grid, List.of(Boolean.TRUE)));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.fromWorldSolidBoxes(
						grid,
						List.of(new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox(
								new Vec3(0.0, 0.0, 0.0),
								new Vec3(1.0, 1.0, 1.0))),
						-0.1));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.fromWorldSolidBoxes(
						grid,
						java.util.Collections.singletonList(
								(PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox) null)));
		assertThrows(IllegalArgumentException.class,
				() -> new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox(
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(1.0, 1.0, 1.0)));
		assertThrows(IllegalArgumentException.class,
				() -> state.applySolidMask(
						PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.open(otherGrid),
						RHO));
		assertThrows(IllegalArgumentException.class, () -> state.applySolidMask(openMask, 0.0));
		assertThrows(IndexOutOfBoundsException.class, () -> openMask.isSolid(2, 0, 0));
	}

	@Test
	void blockedAdvanceKeepsCalmStateAndZeroMetrics() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample blockedGrid =
				conservativeGridForAdvanceRatio(
						"local_voxel_flow_state_blocked",
						1.20,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				PropellerArchiveCtCpJLocalVoxelFlowState.calm(blockedGrid.gridSpec());

		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelFlowAdvance advance =
				state.advanceWithSource(blockedGrid, RHO, DT, SOURCE_THICKNESS);

		assertEquals(1, blockedGrid.cells().size());
		assertEquals(0, advance.residenceStep().activeCellCount());
		assertVectorEquals(Vec3.ZERO, advance.totalSourceMomentumRateWorldNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, advance.totalThroughFlowMomentumRateWorldNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, advance.totalCombinedMomentumRateWorldNewtons(), 1.0e-15);
		assertEquals(0.0, advance.totalSourceMassFlowRateKilogramsPerSecond(), 1.0e-15);
		assertEquals(0.0, advance.sourceMechanicalWorkEnergyJoules(), 1.0e-15);
		assertEquals(0.0, advance.sourceMechanicalWorkPowerWatts(), 1.0e-15);
		assertEquals(0.0, advance.throughFlowMechanicalWorkEnergyJoules(), 1.0e-15);
		assertEquals(0.0, advance.combinedMechanicalWorkEnergyJoules(), 1.0e-15);
		assertEquals(0.0, advance.nextState().totalKineticEnergyJoules(RHO), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, advance.nextState().velocityAt(0, 0, 0), 1.0e-15);
	}

	@Test
	void velocityAdvectionBacktracesAndInterpolatesFlowField() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						3,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(
								Vec3.ZERO,
								new Vec3(10.0, 0.0, 0.0),
								new Vec3(20.0, 0.0, 0.0)
						)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityAdvectionStep advection =
				state.advectVelocity(RHO, 0.05);

		assertEquals(1.0, advection.maxCourantNumber(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				advection.nextState().velocityAt(0, 0, 0), 1.0e-15);
		assertVectorEquals(new Vec3(5.0, 0.0, 0.0),
				advection.nextState().velocityAt(1, 0, 0), 1.0e-15);
		assertVectorEquals(new Vec3(10.0, 0.0, 0.0),
				advection.nextState().velocityAt(2, 0, 0), 1.0e-15);
		assertTrue(advection.momentumResidualWorldNewtonSeconds().x() < 0.0);
		assertEquals(0.0, advection.momentumResidualWorldNewtonSeconds().y(), 1.0e-15);
		assertEquals(0.0, advection.momentumResidualWorldNewtonSeconds().z(), 1.0e-15);
		assertTrue(advection.kineticEnergyAfterJoules() < advection.kineticEnergyBeforeJoules());
		assertTrue(advection.kineticEnergyDeltaJoules() < 0.0);
	}

	@Test
	void velocityAdvectionSamplesSolidCellsAsNoSlipWallVelocity() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						2,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(
								new Vec3(-0.5, 0.0, 0.0),
								new Vec3(10.0, 0.0, 0.0)
						)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask mask =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask(
						grid,
						List.of(Boolean.FALSE, Boolean.TRUE)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityAdvectionStep advection =
				state.advectVelocity(RHO, 1.0, mask);
		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityAdvectionRun run =
				state.advectVelocityWithCourantLimit(RHO, 1.0, 1.0, mask);

		assertEquals(0.5, advection.maxCourantNumber(), 1.0e-15);
		assertVectorEquals(new Vec3(-0.25, 0.0, 0.0),
				advection.nextState().velocityAt(0, 0, 0), 1.0e-15);
		assertVectorEquals(new Vec3(10.0, 0.0, 0.0),
				advection.nextState().velocityAt(1, 0, 0), 1.0e-15);
		assertVectorEquals(new Vec3(0.25 * RHO, 0.0, 0.0),
				advection.momentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertTrue(advection.kineticEnergyAfterJoules() < advection.kineticEnergyBeforeJoules());
		assertEquals(1, run.completedSubstepCount());
		assertEquals(0.5, run.maxCourantNumber(), 1.0e-15);
		assertVectorEquals(advection.nextState().velocityAt(0, 0, 0),
				run.nextState().velocityAt(0, 0, 0), 1.0e-15);
		assertThrows(IllegalArgumentException.class,
				() -> state.advectVelocity(RHO, 1.0,
						PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.open(
								new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
										Vec3.ZERO,
										1.0,
										1,
										1,
										1
								))));
	}

	@Test
	void velocityAdvectionDampsPartialSolidCellsAsNoSlipVolumeFraction() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						2,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(
								new Vec3(-0.5, 0.0, 0.0),
								new Vec3(10.0, 0.0, 0.0)
						)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask halfSolidNeighbor =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask(
						grid,
						List.of(Boolean.FALSE, Boolean.FALSE),
						List.of(0.0, 0.5)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityAdvectionStep advection =
				state.advectVelocity(RHO, 1.0, halfSolidNeighbor);

		assertEquals(0, halfSolidNeighbor.solidCellCount());
		assertTrue(halfSolidNeighbor.hasSolidVolume());
		assertEquals(0.5, halfSolidNeighbor.openVolumeFractionCellIndex(1), 1.0e-15);
		assertVectorEquals(new Vec3(2.25, 0.0, 0.0),
				advection.nextState().velocityAt(0, 0, 0), 1.0e-15);
		assertVectorEquals(new Vec3(-0.5, 0.0, 0.0),
				advection.nextState().velocityAt(1, 0, 0), 1.0e-15);
		assertTrue(advection.kineticEnergyAfterJoules() < advection.kineticEnergyBeforeJoules());
	}

	@Test
	void velocityAdvectionRunSplitsLargeCourantStep() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						3,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(
								Vec3.ZERO,
								new Vec3(10.0, 0.0, 0.0),
								new Vec3(20.0, 0.0, 0.0)
						)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityAdvectionRun advection =
				state.advectVelocityWithCourantLimit(RHO, 0.10, 0.75);

		assertEquals(3, advection.completedSubstepCount());
		assertTrue(advection.maxCourantNumber() <= 0.75 + 1.0e-12);
		assertEquals(0.10, advection.timeStepSeconds(), 1.0e-15);
		assertEquals(0.75, advection.maxAllowedCourantNumber(), 1.0e-15);
		assertEquals(state, advection.previousState());
		assertEquals(advection.substeps().get(advection.substeps().size() - 1).nextState(),
				advection.nextState());
		assertVectorEquals(advection.totalMomentumAfterWorldNewtonSeconds()
						.subtract(advection.totalMomentumBeforeWorldNewtonSeconds()),
				advection.momentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertTrue(advection.kineticEnergyAfterJoules() < advection.kineticEnergyBeforeJoules());
		assertThrows(IllegalArgumentException.class,
				() -> state.advectVelocityWithCourantLimit(RHO, DT, 0.0));
	}

	@Test
	void uniformAdvectionIsNoOpAndRejectsInvalidInputs() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						new Vec3(-1.0, -1.0, -1.0),
						0.5,
						2,
						2,
						2
				);
		Vec3 velocity = new Vec3(1.0, -2.0, 0.5);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				PropellerArchiveCtCpJLocalVoxelFlowState.uniform(grid, velocity);

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityAdvectionStep advection =
				state.advectVelocity(RHO, 0.125);

		assertEquals(velocity.length() * 0.125 / grid.cellSizeMeters(),
				advection.maxCourantNumber(), 1.0e-15);
		for (int y = 0; y < grid.cellCountY(); y++) {
			for (int z = 0; z < grid.cellCountZ(); z++) {
				for (int x = 0; x < grid.cellCountX(); x++) {
					assertVectorEquals(velocity,
							advection.nextState().velocityAt(x, y, z), 1.0e-15);
				}
			}
		}
		assertVectorEquals(Vec3.ZERO, advection.momentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertEquals(state.totalKineticEnergyJoules(RHO),
				advection.kineticEnergyAfterJoules(), 1.0e-15);
		assertThrows(IllegalArgumentException.class, () -> state.advectVelocity(0.0, DT));
		assertThrows(IllegalArgumentException.class, () -> state.advectVelocity(RHO, 0.0));
	}

	@Test
	void pressureProjectionReducesLocalDivergence() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						3,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), Vec3.ZERO)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceIntegralMetrics integralsBefore =
				state.divergenceIntegralMetrics();

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityProjectionStep projection =
				state.projectVelocityDivergence(RHO, 8);
		PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceIntegralMetrics integralsAfter =
				projection.nextState().divergenceIntegralMetrics();

		assertEquals(0.5, projection.divergenceBefore().maxAbsDivergencePerSecond(), 1.0e-15);
		assertEquals(0.0, integralsBefore.netVolumeFlowRateCubicMetersPerSecond(), 1.0e-15);
		assertEquals(1.0, integralsBefore.grossAbsVolumeFlowRateCubicMetersPerSecond(), 1.0e-15);
		assertEquals(RHO, integralsBefore.grossAbsMassFlowRateKilogramsPerSecond(RHO), 1.0e-15);
		assertTrue(projection.divergenceAfter().maxAbsDivergencePerSecond()
				< projection.divergenceBefore().maxAbsDivergencePerSecond());
		assertTrue(projection.divergenceAfter().rmsDivergencePerSecond()
				< projection.divergenceBefore().rmsDivergencePerSecond());
		assertTrue(integralsAfter.grossAbsVolumeFlowRateCubicMetersPerSecond()
				< integralsBefore.grossAbsVolumeFlowRateCubicMetersPerSecond());
		assertEquals(integralsAfter.grossAbsVolumeFlowRateCubicMetersPerSecond() * RHO,
				integralsAfter.grossAbsMassFlowRateKilogramsPerSecond(RHO), 1.0e-15);
		assertEquals(8, projection.pressureProjectionIterations());
		assertVectorEquals(projection.totalMomentumAfterWorldNewtonSeconds()
						.subtract(projection.totalMomentumBeforeWorldNewtonSeconds()),
				projection.momentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertEquals(projection.nextState().divergenceMetrics().maxAbsDivergencePerSecond(),
				projection.divergenceAfter().maxAbsDivergencePerSecond(), 1.0e-15);
	}

	@Test
	void pressureProjectionUsesPartialOpenFaceWeightsForPoissonSolve() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						3,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(
								Vec3.ZERO,
								new Vec3(10.0, 0.0, 0.0),
								Vec3.ZERO
						)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask mask =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask(
						grid,
						List.of(Boolean.FALSE, Boolean.FALSE, Boolean.FALSE),
						List.of(0.0, 0.5, 0.0)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityProjectionStep projection =
				state.projectVelocityDivergence(RHO, 1, mask);

		assertEquals(2.5, projection.divergenceBefore().maxAbsDivergencePerSecond(), 1.0e-15);
		assertEquals(0.0, projection.divergenceBefore().meanDivergencePerSecond(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				projection.nextState().velocityAt(0, 0, 0), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				projection.nextState().velocityAt(1, 0, 0), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				projection.nextState().velocityAt(2, 0, 0), 1.0e-15);
		assertEquals(0.0, projection.divergenceAfter().maxAbsDivergencePerSecond(), 1.0e-15);
		assertEquals(0.0,
				projection.nextState()
						.divergenceIntegralMetrics(mask)
						.grossAbsVolumeFlowRateCubicMetersPerSecond(),
				1.0e-15);
		assertTrue(projection.kineticEnergyAfterJoules() < projection.kineticEnergyBeforeJoules());
	}

	@Test
	void pressureProjectionLeavesUniformOpenBoundaryFlowUnchanged() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						3,
						2,
						1
				);
		Vec3 velocity = new Vec3(1.0, -0.25, 0.0);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				PropellerArchiveCtCpJLocalVoxelFlowState.uniform(grid, velocity);

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityProjectionStep projection =
				state.projectVelocityDivergence(RHO, 8);

		assertEquals(0.0, projection.divergenceBefore().maxAbsDivergencePerSecond(), 1.0e-15);
		assertEquals(0.0, projection.divergenceAfter().maxAbsDivergencePerSecond(), 1.0e-15);
		for (int y = 0; y < grid.cellCountY(); y++) {
			for (int z = 0; z < grid.cellCountZ(); z++) {
				for (int x = 0; x < grid.cellCountX(); x++) {
					assertVectorEquals(velocity,
							projection.nextState().velocityAt(x, y, z), 1.0e-15);
				}
			}
		}
		assertVectorEquals(Vec3.ZERO, projection.momentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertThrows(IllegalArgumentException.class, () -> state.projectVelocityDivergence(RHO, -1));
		assertThrows(IllegalArgumentException.class, () -> state.projectVelocityDivergence(0.0, 1));
	}

	@Test
	void pressureProjectionUsesSolidFacesAsNoFluxBoundaries() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						2,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(
								new Vec3(2.0, 0.0, 0.0),
								Vec3.ZERO
						)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask mask =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask(
						grid,
						List.of(Boolean.FALSE, Boolean.TRUE)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityProjectionStep projection =
				state.projectVelocityDivergence(RHO, 8, mask);

		assertEquals(2.0, projection.divergenceBefore().maxAbsDivergencePerSecond(), 1.0e-15);
		assertTrue(projection.divergenceAfter().maxAbsDivergencePerSecond()
				< projection.divergenceBefore().maxAbsDivergencePerSecond());
		assertVectorEquals(new Vec3(1.0, 0.0, 0.0),
				projection.nextState().velocityAt(0, 0, 0), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				projection.nextState().velocityAt(1, 0, 0), 1.0e-15);
		assertTrue(projection.momentumResidualWorldNewtonSeconds().x() < 0.0);
	}

	@Test
	void diffusionSpreadsVelocityToNeighborsWhileConservingMomentum() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						3,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(Vec3.ZERO, new Vec3(0.0, 3.0, 0.0), Vec3.ZERO)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityDiffusionStep diffusion =
				state.diffuseVelocity(RHO, 0.10, 1.0);

		assertEquals(0.10, diffusion.diffusionNumber(), 1.0e-15);
		assertVectorEquals(new Vec3(0.0, 0.3, 0.0),
				diffusion.nextState().velocityAt(0, 0, 0), 1.0e-15);
		assertVectorEquals(new Vec3(0.0, 2.4, 0.0),
				diffusion.nextState().velocityAt(1, 0, 0), 1.0e-15);
		assertVectorEquals(new Vec3(0.0, 0.3, 0.0),
				diffusion.nextState().velocityAt(2, 0, 0), 1.0e-15);
		assertVectorEquals(state.totalMomentumWorldNewtonSeconds(RHO),
				diffusion.nextState().totalMomentumWorldNewtonSeconds(RHO), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, diffusion.momentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertTrue(diffusion.kineticEnergyAfterJoules() < diffusion.kineticEnergyBeforeJoules());
		assertTrue(diffusion.kineticEnergyDeltaJoules() < 0.0);
		assertEquals(-diffusion.kineticEnergyDeltaJoules(),
				diffusion.viscousDissipatedEnergyJoules(), 1.0e-15);
		assertEquals(diffusion.viscousDissipatedEnergyJoules() / diffusion.timeStepSeconds(),
				diffusion.meanViscousDissipationPowerWatts(), 1.0e-15);
		assertTrue(diffusion.nextState().maxSpeedMetersPerSecond() < state.maxSpeedMetersPerSecond());
	}

	@Test
	void diffusionDoesNotExchangeVelocityAcrossSolidMaskFaces() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						3,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(
								Vec3.ZERO,
								new Vec3(0.0, 3.0, 0.0),
								Vec3.ZERO
						)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask mask =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask(
						grid,
						List.of(Boolean.FALSE, Boolean.TRUE, Boolean.FALSE)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityDiffusionStep diffusion =
				state.diffuseVelocity(RHO, 0.10, 1.0, mask);

		assertVectorEquals(Vec3.ZERO,
				diffusion.nextState().velocityAt(0, 0, 0), 1.0e-15);
		assertVectorEquals(new Vec3(0.0, 3.0, 0.0),
				diffusion.nextState().velocityAt(1, 0, 0), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				diffusion.nextState().velocityAt(2, 0, 0), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, diffusion.momentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertEquals(state.totalKineticEnergyJoules(RHO, mask), diffusion.kineticEnergyAfterJoules(), 1.0e-15);
		assertEquals(0.0, diffusion.kineticEnergyAfterJoules(), 1.0e-15);
		assertEquals(0.0, diffusion.viscousDissipatedEnergyJoules(), 1.0e-15);
		assertEquals(0.0, diffusion.meanViscousDissipationPowerWatts(), 1.0e-15);
	}

	@Test
	void diffusionAppliesNoSlipWallDragAtSolidMaskFaces() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						2,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(
								new Vec3(10.0, 2.0, 0.0),
								new Vec3(0.0, 99.0, 0.0)
						)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask mask =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask(
						grid,
						List.of(Boolean.FALSE, Boolean.TRUE)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityDiffusionStep diffusion =
				state.diffuseVelocity(RHO, 0.10, 1.0, mask);

		assertEquals(0.10, diffusion.diffusionNumber(), 1.0e-15);
		assertVectorEquals(new Vec3(9.0, 1.8, 0.0),
				diffusion.nextState().velocityAt(0, 0, 0), 1.0e-15);
		assertVectorEquals(new Vec3(0.0, 99.0, 0.0),
				diffusion.nextState().velocityAt(1, 0, 0), 1.0e-15);
		assertVectorEquals(new Vec3(-RHO, -0.2 * RHO, 0.0),
				diffusion.momentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertTrue(diffusion.kineticEnergyDeltaJoules() < 0.0);
		assertEquals(-diffusion.kineticEnergyDeltaJoules(),
				diffusion.viscousDissipatedEnergyJoules(), 1.0e-15);
		assertEquals(diffusion.viscousDissipatedEnergyJoules(),
				diffusion.meanViscousDissipationPowerWatts(), 1.0e-15);
	}

	@Test
	void diffusionAcrossPartialOpenCellsConservesOpenVolumeMomentum() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						2,
						1,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(
								Vec3.ZERO,
								new Vec3(10.0, 0.0, 0.0)
						)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask mask =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask(
						grid,
						List.of(Boolean.FALSE, Boolean.FALSE),
						List.of(0.0, 0.5)
				);

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityDiffusionStep diffusion =
				state.diffuseVelocity(RHO, 0.10, 1.0, mask);

		assertEquals(0.10, diffusion.diffusionNumber(), 1.0e-15);
		assertVectorEquals(new Vec3(0.5, 0.0, 0.0),
				diffusion.nextState().velocityAt(0, 0, 0), 1.0e-15);
		assertVectorEquals(new Vec3(9.0, 0.0, 0.0),
				diffusion.nextState().velocityAt(1, 0, 0), 1.0e-15);
		assertVectorEquals(state.totalMomentumWorldNewtonSeconds(RHO, mask),
				diffusion.nextState().totalMomentumWorldNewtonSeconds(RHO, mask), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, diffusion.momentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertTrue(diffusion.kineticEnergyAfterJoules() < diffusion.kineticEnergyBeforeJoules());
		assertEquals(-diffusion.kineticEnergyDeltaJoules(),
				diffusion.viscousDissipatedEnergyJoules(), 1.0e-15);
	}

	@Test
	void sourceAdvanceCanDiffuseVelocityWithoutChangingSourceBookkeeping() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample gridSample =
				conservativeGridForSignedAxialSpeed(
						"local_voxel_flow_state_diffused_hover",
						0.0,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				PropellerArchiveCtCpJLocalVoxelFlowState.calm(gridSample.gridSpec());
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelFlowAdvance advance =
				state.advanceWithSource(gridSample, RHO, DT, SOURCE_THICKNESS);
		double diffusionNumber = 0.05;
		double kinematicViscosity = diffusionNumber
				* gridSample.gridSpec().cellSizeMeters()
				* gridSample.gridSpec().cellSizeMeters()
				/ DT;

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityDiffusionStep diffusion =
				advance.nextState().diffuseVelocity(RHO, kinematicViscosity, DT);

		assertEquals(diffusionNumber, diffusion.diffusionNumber(), 1.0e-15);
		assertVectorEquals(advance.nextState().totalMomentumWorldNewtonSeconds(RHO),
				diffusion.nextState().totalMomentumWorldNewtonSeconds(RHO), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, diffusion.momentumResidualWorldNewtonSeconds(), 1.0e-12);
		assertTrue(diffusion.kineticEnergyAfterJoules() <= diffusion.kineticEnergyBeforeJoules() + 1.0e-15);
		assertEquals(Math.max(0.0, -diffusion.kineticEnergyDeltaJoules()),
				diffusion.viscousDissipatedEnergyJoules(), 1.0e-15);
		assertVectorEquals(gridSample.integratedBodyForceWorldNewtons(),
				advance.totalSourceMomentumRateWorldNewtons(), 1.0e-10);
		assertTrue(diffusion.nextState().maxSpeedMetersPerSecond() <= advance.nextState().maxSpeedMetersPerSecond());
	}

	@Test
	void uniformDiffusionIsNoOpAndRejectsUnstableDiffusionNumber() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						0.5,
						2,
						2,
						2
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				PropellerArchiveCtCpJLocalVoxelFlowState.uniform(grid, new Vec3(1.0, -2.0, 0.5));

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityDiffusionStep diffusion =
				state.diffuseVelocity(RHO, 0.01, 1.0);

		for (int y = 0; y < grid.cellCountY(); y++) {
			for (int z = 0; z < grid.cellCountZ(); z++) {
				for (int x = 0; x < grid.cellCountX(); x++) {
					assertVectorEquals(state.velocityAt(x, y, z),
							diffusion.nextState().velocityAt(x, y, z), 1.0e-15);
				}
			}
		}
		assertEquals(state.totalKineticEnergyJoules(RHO),
				diffusion.kineticEnergyAfterJoules(), 1.0e-15);
		assertThrows(IllegalArgumentException.class,
				() -> state.diffuseVelocity(RHO, 0.20, 1.0));
		assertThrows(IllegalArgumentException.class,
				() -> state.diffuseVelocity(RHO, -0.01, 1.0));
		assertThrows(IllegalArgumentException.class,
				() -> state.diffuseVelocity(0.0, 0.01, 1.0));
	}

	@Test
	void rejectsMismatchedGridAndInvalidStateInputs() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample hoverGrid =
				conservativeGridForSignedAxialSpeed(
						"local_voxel_flow_state_invalid_hover",
						0.0,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample blockedGrid =
				conservativeGridForAdvanceRatio(
						"local_voxel_flow_state_invalid_blocked",
						1.20,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				PropellerArchiveCtCpJLocalVoxelFlowState.calm(hoverGrid.gridSpec());

		assertThrows(IllegalArgumentException.class,
				() -> new PropellerArchiveCtCpJLocalVoxelFlowState(hoverGrid.gridSpec(), List.of()));
		assertThrows(IllegalArgumentException.class, () -> state.totalKineticEnergyJoules(0.0));
		assertThrows(IllegalArgumentException.class, () -> state.kineticEnergyDistributionMetrics(0.0));
		assertThrows(IllegalArgumentException.class,
				() -> state.advanceWithSource(blockedGrid, RHO, DT, SOURCE_THICKNESS));
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
