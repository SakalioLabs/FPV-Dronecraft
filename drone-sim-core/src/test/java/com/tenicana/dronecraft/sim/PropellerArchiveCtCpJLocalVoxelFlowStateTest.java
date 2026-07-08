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
		assertEquals(0.0, state.totalKineticEnergyJoules(RHO), 1.0e-15);
		assertTrue(advance.nextState().totalKineticEnergyJoules(RHO) > 0.0);
		assertTrue(advance.nextState().maxSpeedMetersPerSecond() > 0.0);
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

		PropellerArchiveCtCpJLocalVoxelFlowState.VelocityProjectionStep projection =
				state.projectVelocityDivergence(RHO, 8);

		assertEquals(0.5, projection.divergenceBefore().maxAbsDivergencePerSecond(), 1.0e-15);
		assertTrue(projection.divergenceAfter().maxAbsDivergencePerSecond()
				< projection.divergenceBefore().maxAbsDivergencePerSecond());
		assertTrue(projection.divergenceAfter().rmsDivergencePerSecond()
				< projection.divergenceBefore().rmsDivergencePerSecond());
		assertEquals(8, projection.pressureProjectionIterations());
		assertVectorEquals(projection.totalMomentumAfterWorldNewtonSeconds()
						.subtract(projection.totalMomentumBeforeWorldNewtonSeconds()),
				projection.momentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertEquals(projection.nextState().divergenceMetrics().maxAbsDivergencePerSecond(),
				projection.divergenceAfter().maxAbsDivergencePerSecond(), 1.0e-15);
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
		assertTrue(diffusion.nextState().maxSpeedMetersPerSecond() < state.maxSpeedMetersPerSecond());
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
