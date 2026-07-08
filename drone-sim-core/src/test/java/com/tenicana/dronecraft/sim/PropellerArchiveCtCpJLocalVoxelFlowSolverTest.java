package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PropellerArchiveCtCpJLocalVoxelFlowSolverTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = 0.05;
	private static final double CELL_SIZE = 0.04;
	private static final double DT = 0.005;
	private static final double RPM_PER_RADIAN_PER_SECOND = 60.0 / (2.0 * Math.PI);

	@Test
	void multiStepRunAccumulatesSourceImpulseAndProjectsEachStep() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample gridSample =
				conservativeGridForSignedAxialSpeed(
						"local_voxel_solver_hover",
						0.0,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		double diffusionNumber = 0.04;
		PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig config =
				new PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig(
						RHO,
						DT,
						SOURCE_THICKNESS,
						diffusionNumber * CELL_SIZE * CELL_SIZE / DT,
						4
				);

		PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run =
				PropellerArchiveCtCpJLocalVoxelFlowSolver.run(gridSample, config);

		assertEquals(4, run.completedStepCount());
		assertEquals(4, run.iterations().size());
		assertEquals(diffusionNumber, config.diffusionNumber(gridSample.gridSpec()), 1.0e-15);
		assertEquals(diffusionNumber, run.maxDiffusionNumber(), 1.0e-15);
		assertTrue(run.maxAdvectionCourantNumber() > 0.0);
		assertTrue(run.maxAdvectionCourantNumber()
				<= config.maxAdvectionCourantNumber() + 1.0e-12);
		assertTrue(run.maxAdvectionSubstepCount() > 1);
		assertTrue(run.maxDivergenceBeforeProjectionPerSecond() > 0.0);
		assertTrue(run.maxDivergenceAfterProjectionPerSecond()
				< run.maxDivergenceBeforeProjectionPerSecond());
		assertEquals(0.0, run.initialKineticEnergyJoules(), 1.0e-15);
		assertTrue(run.finalKineticEnergyJoules() > 0.0);
		assertTrue(run.finalMaxSpeedMetersPerSecond() > 0.0);
		assertTrue(run.totalSourceMassKilograms() > 0.0);
		assertTrue(run.maxResidenceAlpha() > 0.0);
		assertVectorEquals(gridSample.integratedBodyForceWorldNewtons()
						.multiply(DT * run.completedStepCount()),
				run.totalSourceImpulseWorldNewtonSeconds(), 1.0e-12);
		assertEquals(gridSample.integratedIdealMomentumPowerWatts(SOURCE_THICKNESS)
						* DT * run.completedStepCount(),
				run.totalIdealMomentumEnergyJoules(), 1.0e-12);
		assertTrue(run.totalSourceFlowKineticEnergyDeltaJoules() > 0.0);
		assertEquals(run.totalSourceFlowKineticEnergyDeltaJoules() - run.totalIdealMomentumEnergyJoules(),
				run.sourceFlowKineticEnergyDeltaMinusIdealMomentumEnergyJoules(), 1.0e-12);
		assertTrue(run.totalThroughFlowImpulseWorldNewtonSeconds().length() > 0.0);
		assertEquals(0, run.maxSolidCellCount());
		assertEquals(0, run.maxSolidClampedCellCount());
		assertVectorEquals(Vec3.ZERO, run.totalSolidBoundaryMomentumResidualWorldNewtonSeconds(), 1.0e-15);
		for (int i = 0; i < run.iterations().size(); i++) {
			assertEquals(i, run.iterations().get(i).stepIndex());
			assertEquals(run.iterations().get(i).stateAfterSource(),
					run.iterations().get(i).advectionRun().previousState());
			assertEquals(run.iterations().get(i).stateAfterAdvection(),
					run.iterations().get(i).diffusionStep().previousState());
			assertEquals(run.iterations().get(i).stateAfterDiffusion(),
					run.iterations().get(i).projectionStep().previousState());
			assertEquals(run.iterations().get(i).stateAfterProjection(),
					run.iterations().get(i).solidBoundaryStep().previousState());
			assertEquals(run.iterations().get(i).stateAfterSolidBoundary(),
					run.iterations().get(i).solidBoundaryStep().nextState());
			assertEquals(config.pressureProjectionIterations(),
					run.iterations().get(i).projectionStep().pressureProjectionIterations());
			assertTrue(run.iterations().get(i).projectionStep().divergenceAfter().maxAbsDivergencePerSecond()
					<= run.iterations().get(i).projectionStep().divergenceBefore().maxAbsDivergencePerSecond()
					+ 1.0e-12);
			assertTrue(run.iterations().get(i).advectionRun().maxCourantNumber() > 0.0);
			assertTrue(run.iterations().get(i).advectionRun().maxCourantNumber()
					<= config.maxAdvectionCourantNumber() + 1.0e-12);
			assertVectorEquals(Vec3.ZERO,
					run.iterations().get(i).diffusionStep().momentumResidualWorldNewtonSeconds(), 1.0e-12);
			assertEquals(0, run.iterations().get(i).solidBoundaryStep().solidCellCount());
			assertEquals(0, run.iterations().get(i).solidBoundaryStep().clampedCellCount());
			assertVectorEquals(Vec3.ZERO,
					run.iterations().get(i).solidBoundaryStep().momentumResidualWorldNewtonSeconds(), 1.0e-15);
			if (i > 0) {
				assertEquals(run.iterations().get(i - 1).stateAfterSolidBoundary(),
						run.iterations().get(i).stateBeforeStep());
			}
		}
		assertEquals(run.finalState(), run.iterations().get(run.iterations().size() - 1).stateAfterSolidBoundary());
	}

	@Test
	void singleRotorRunAccumulatesWakeAngularMomentumImpulseFromSourceTorque() {
		SingleRotorGridSource singleRotor =
				singleRotorGridSourceForSignedAxialSpeed(
						"local_voxel_solver_single_rotor_wake_torque",
						0.0,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample gridSample = singleRotor.gridSample();
		PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig config =
				new PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig(
						RHO,
						DT,
						SOURCE_THICKNESS,
						0.0,
						2,
						PropellerArchiveCtCpJLocalVoxelFlowSolver.DEFAULT_MAX_ADVECTION_COURANT_NUMBER,
						0
				);

		PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run =
				PropellerArchiveCtCpJLocalVoxelFlowSolver.run(gridSample, config);
		Vec3 sourceWakeTorque = gridSample.integratedWakeAngularMomentumTorqueWorldNewtonMeters();

		assertTrue(sourceWakeTorque.length() > 0.0);
		assertVectorEquals(sourceWakeTorque.multiply(DT * run.completedStepCount()),
				run.totalWakeAngularMomentumImpulseWorldNewtonMeterSeconds(), 1.0e-14);
		for (PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverIteration iteration : run.iterations()) {
			assertVectorEquals(sourceWakeTorque,
					iteration.sourceAdvance().totalWakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-12);
			assertVectorEquals(sourceWakeTorque.multiply(DT),
					iteration.sourceAdvance().totalWakeAngularMomentumImpulseWorldNewtonMeterSeconds(), 1.0e-14);
		}
		Vec3 totalSourceFlowAngularMomentumDelta =
				run.totalSourceFlowAngularMomentumDeltaWorldNewtonMeterSeconds(singleRotor.diskCenterWorldMeters());
		assertTrue(totalSourceFlowAngularMomentumDelta.dot(singleRotor.wakeAngularMomentumTorqueWorldNewtonMeters())
				> 0.0);
		assertVectorEquals(totalSourceFlowAngularMomentumDelta
						.subtract(run.totalWakeAngularMomentumImpulseWorldNewtonMeterSeconds()),
				run.sourceFlowAngularMomentumDeltaMinusWakeImpulseWorldNewtonMeterSeconds(
						singleRotor.diskCenterWorldMeters()),
				1.0e-14);
		Vec3 afterSourceAngularMomentum = run.iterations().get(0)
				.stateAfterSource()
				.totalAngularMomentumWorldNewtonMeterSeconds(RHO, singleRotor.diskCenterWorldMeters());
		assertTrue(afterSourceAngularMomentum.dot(singleRotor.wakeAngularMomentumTorqueWorldNewtonMeters()) > 0.0);
		assertTrue(run.finalAngularMomentumWorldNewtonMeterSeconds(singleRotor.diskCenterWorldMeters())
				.dot(singleRotor.wakeAngularMomentumTorqueWorldNewtonMeters()) > 0.0);
	}

	@Test
	void zeroStepRunKeepsInitialStateAndZeroAccumulators() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample gridSample =
				conservativeGridForSignedAxialSpeed(
						"local_voxel_solver_zero_step",
						0.0,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJLocalVoxelFlowState initialState =
				PropellerArchiveCtCpJLocalVoxelFlowState.uniform(
						gridSample.gridSpec(),
						new Vec3(0.25, -0.10, 0.05)
				);
		PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig config =
				new PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig(
						RHO,
						DT,
						SOURCE_THICKNESS,
						0.0,
						0
				);

		PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run =
				PropellerArchiveCtCpJLocalVoxelFlowSolver.run(initialState, gridSample, config);

		assertEquals(0, run.completedStepCount());
		assertTrue(run.iterations().isEmpty());
		assertEquals(initialState, run.finalState());
		assertVectorEquals(Vec3.ZERO, run.totalSourceImpulseWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				run.totalWakeAngularMomentumImpulseWorldNewtonMeterSeconds(), 1.0e-15);
		assertEquals(0.0, run.totalIdealMomentumEnergyJoules(), 1.0e-15);
		assertEquals(0.0, run.totalSourceFlowKineticEnergyDeltaJoules(), 1.0e-15);
		assertEquals(0.0, run.sourceFlowKineticEnergyDeltaMinusIdealMomentumEnergyJoules(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				run.totalSourceFlowAngularMomentumDeltaWorldNewtonMeterSeconds(Vec3.ZERO), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				run.sourceFlowAngularMomentumDeltaMinusWakeImpulseWorldNewtonMeterSeconds(Vec3.ZERO), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, run.totalThroughFlowImpulseWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, run.totalAdvectionMomentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, run.totalProjectionMomentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, run.totalSolidBoundaryMomentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertEquals(0.0, run.totalSourceMassKilograms(), 1.0e-15);
		assertEquals(0.0, run.maxAdvectionCourantNumber(), 1.0e-15);
		assertEquals(0, run.maxAdvectionSubstepCount());
		assertEquals(0, run.maxSolidCellCount());
		assertEquals(0, run.maxSolidClampedCellCount());
		assertEquals(0.0, run.maxDivergenceBeforeProjectionPerSecond(), 1.0e-15);
		assertEquals(0.0, run.maxDivergenceAfterProjectionPerSecond(), 1.0e-15);
		assertEquals(initialState.totalKineticEnergyJoules(RHO), run.finalKineticEnergyJoules(), 1.0e-15);
	}

	@Test
	void blockedRunStaysCalmAndStillReportsStableDiffusionNumber() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample blockedGrid =
				conservativeGridForAdvanceRatio(
						"local_voxel_solver_blocked",
						1.20,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		double diffusionNumber = 0.02;
		PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig config =
				new PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig(
						RHO,
						DT,
						SOURCE_THICKNESS,
						diffusionNumber * blockedGrid.gridSpec().cellSizeMeters()
								* blockedGrid.gridSpec().cellSizeMeters() / DT,
						3
				);

		PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run =
				PropellerArchiveCtCpJLocalVoxelFlowSolver.run(blockedGrid, config);

		assertEquals(3, run.completedStepCount());
		assertEquals(diffusionNumber, run.maxDiffusionNumber(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, run.totalSourceImpulseWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				run.totalWakeAngularMomentumImpulseWorldNewtonMeterSeconds(), 1.0e-15);
		assertEquals(0.0, run.totalIdealMomentumEnergyJoules(), 1.0e-15);
		assertEquals(0.0, run.totalSourceFlowKineticEnergyDeltaJoules(), 1.0e-15);
		assertEquals(0.0, run.sourceFlowKineticEnergyDeltaMinusIdealMomentumEnergyJoules(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				run.totalSourceFlowAngularMomentumDeltaWorldNewtonMeterSeconds(Vec3.ZERO), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				run.sourceFlowAngularMomentumDeltaMinusWakeImpulseWorldNewtonMeterSeconds(Vec3.ZERO), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, run.totalThroughFlowImpulseWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, run.totalAdvectionMomentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, run.totalProjectionMomentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, run.totalSolidBoundaryMomentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertEquals(0.0, run.totalSourceMassKilograms(), 1.0e-15);
		assertEquals(0.0, run.maxAdvectionCourantNumber(), 1.0e-15);
		assertEquals(1, run.maxAdvectionSubstepCount());
		assertEquals(0, run.maxSolidCellCount());
		assertEquals(0, run.maxSolidClampedCellCount());
		assertEquals(0.0, run.maxDivergenceBeforeProjectionPerSecond(), 1.0e-15);
		assertEquals(0.0, run.maxDivergenceAfterProjectionPerSecond(), 1.0e-15);
		assertEquals(0.0, run.finalKineticEnergyJoules(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, run.finalMomentumWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, run.finalState().velocityAt(0, 0, 0), 1.0e-15);
	}

	@Test
	void customSolidMaskClampsSolidCellMomentumInsideSolverStep() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						Vec3.ZERO,
						1.0,
						2,
						1,
						1
				);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample(
						grid,
						1,
						List.of(
								zeroSourceCell(grid, 0, 0, 0),
								zeroSourceCell(grid, 1, 0, 0)
						)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState initialState =
				new PropellerArchiveCtCpJLocalVoxelFlowState(
						grid,
						List.of(
								Vec3.ZERO,
								new Vec3(0.0, 2.0, 0.0)
						)
				);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask solidMask =
				new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask(
						grid,
						List.of(Boolean.FALSE, Boolean.TRUE),
						List.of(0.0, 0.5)
				);
		PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig config =
				new PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig(
						RHO,
						0.01,
						0.5,
						0.0,
						1,
						1.0,
						0
				);

		PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run =
				PropellerArchiveCtCpJLocalVoxelFlowSolver.run(
						initialState,
						sourceGridSample,
						config,
						solidMask
				);

		assertEquals(1, run.completedStepCount());
		assertEquals(1, run.maxSolidCellCount());
		assertEquals(1, run.maxSolidClampedCellCount());
		assertVectorEquals(Vec3.ZERO, run.totalSourceImpulseWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, run.totalThroughFlowImpulseWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(new Vec3(0.0, -1.0 * RHO, 0.0),
				run.totalSolidBoundaryMomentumResidualWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, run.finalMomentumWorldNewtonSeconds(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, run.finalState().velocityAt(0, 0, 0), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, run.finalState().velocityAt(1, 0, 0), 1.0e-15);
		assertEquals(run.finalState(), run.iterations().get(0).stateAfterSolidBoundary());
		assertEquals(1, run.iterations().get(0).solidBoundaryStep().solidCellCount());
		assertEquals(1, run.iterations().get(0).solidBoundaryStep().clampedCellCount());
		assertTrue(run.iterations().get(0).solidBoundaryStep().kineticEnergyDeltaJoules() < 0.0);
	}

	@Test
	void rejectsInvalidConfigMismatchedGridAndUnstableDiffusion() {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample hoverGrid =
				conservativeGridForSignedAxialSpeed(
						"local_voxel_solver_invalid_hover",
						0.0,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample blockedGrid =
				conservativeGridForAdvanceRatio(
						"local_voxel_solver_invalid_blocked",
						1.20,
						Quaternion.IDENTITY,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJLocalVoxelFlowState state =
				PropellerArchiveCtCpJLocalVoxelFlowState.calm(hoverGrid.gridSpec());
		PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig validConfig =
				new PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig(
						RHO,
						DT,
						SOURCE_THICKNESS,
						0.0,
						1
				);
		PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig unstableConfig =
				new PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig(
						RHO,
						DT,
						SOURCE_THICKNESS,
						0.20 * hoverGrid.gridSpec().cellSizeMeters() * hoverGrid.gridSpec().cellSizeMeters() / DT,
						1
				);

		assertThrows(IllegalArgumentException.class,
				() -> new PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig(
						0.0,
						DT,
						SOURCE_THICKNESS,
						0.0,
						1
				));
		assertThrows(IllegalArgumentException.class,
				() -> new PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig(
						RHO,
						DT,
						SOURCE_THICKNESS,
						0.0,
						-1
				));
		assertThrows(IllegalArgumentException.class,
				() -> new PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig(
						RHO,
						DT,
						SOURCE_THICKNESS,
						0.0,
						1,
						0.0
				));
		assertThrows(IllegalArgumentException.class,
				() -> new PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig(
						RHO,
						DT,
						SOURCE_THICKNESS,
						0.0,
						1,
						1.0,
						-1
				));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLocalVoxelFlowSolver.run(state, blockedGrid, validConfig));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLocalVoxelFlowSolver.run(
						state,
						hoverGrid,
						validConfig,
						PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.open(blockedGrid.gridSpec())));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLocalVoxelFlowSolver.run(hoverGrid, unstableConfig));
	}

	private static PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample zeroSourceCell(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid,
			int xIndex,
			int yIndex,
			int zIndex
	) {
		return new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample(
				xIndex,
				yIndex,
				zIndex,
				grid.cellCenterWorldMeters(xIndex, yIndex, zIndex),
				grid.cellVolumeCubicMeters(),
				1,
				0,
				0.0,
				Vec3.ZERO,
				Vec3.ZERO,
				0.0,
				0.0,
				0.0,
				Vec3.ZERO,
				Vec3.ZERO,
				Vec3.ZERO
		);
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

	private record SingleRotorGridSource(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample gridSample,
			Vec3 diskCenterWorldMeters,
			Vec3 wakeAngularMomentumTorqueWorldNewtonMeters
	) {
	}

	private static SingleRotorGridSource singleRotorGridSourceForSignedAxialSpeed(
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
		PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm =
				sample.rotorActuatorDiskSourceTerms().get(0);
		PropellerArchiveCtCpJActuatorDiskSourceField field =
				new PropellerArchiveCtCpJActuatorDiskSourceField(
						List.of(sourceTerm),
						SOURCE_THICKNESS
				);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				field.enclosingVoxelGrid(CELL_SIZE, 1);
		return new SingleRotorGridSource(
				field.sampleConservativeVoxelGrid(grid, 3),
				sourceTerm.diskCenterWorldMeters(),
				sourceTerm.wakeAngularMomentumTorqueWorldNewtonMeters()
		);
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
