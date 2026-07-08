package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PropellerArchiveCtCpJActuatorDiskSourceFieldTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = 0.05;
	private static final Vec3 MOMENT_REFERENCE_WORLD = new Vec3(12.0, 64.0, -3.0);

	@Test
	void samplesAppliedSourceInsideCylindricalDiskVolume() {
		PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm =
				hoverSourceTerm();
		PropellerArchiveCtCpJActuatorDiskSourceField field =
				new PropellerArchiveCtCpJActuatorDiskSourceField(List.of(sourceTerm), SOURCE_THICKNESS);
		double diskRadius = Math.sqrt(sourceTerm.diskAreaSquareMeters() / Math.PI);
		Vec3 radial = perpendicularUnit(sourceTerm.diskNormalWorld());
		double sampleRadius = Math.min(sourceTerm.angularMomentumSwirlRadiusMeters(), diskRadius * 0.5);
		Vec3 samplePoint = sourceTerm.diskCenterWorldMeters().add(radial.multiply(sampleRadius));

		PropellerArchiveCtCpJActuatorDiskSourceField.SourceFieldSample sample = field.sampleAt(samplePoint);

		assertTrue(field.containsActuatorDiskVolume(sourceTerm, samplePoint));
		assertTrue(sample.insideAnySource());
		assertEquals(1, sample.contributingSourceCount());
		assertVectorEquals(sourceTerm.equivalentBodyForceWorldNewtonsPerCubicMeter(SOURCE_THICKNESS),
				sample.bodyForceDensityWorldNewtonsPerCubicMeter(), 1.0e-12);
		assertVectorEquals(expectedWakeTorqueDensity(sourceTerm, samplePoint),
				sample.wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter(), 1.0e-12);
		assertEquals(sourceTerm.pressureJumpPascals(), sample.pressureJumpPascals(), 1.0e-12);
		assertEquals(sourceTerm.massFluxKilogramsPerSecondSquareMeter(),
				sample.massFluxKilogramsPerSecondSquareMeter(), 1.0e-12);
		assertEquals(sourceTerm.idealMomentumPowerLoadingWattsPerSquareMeter(),
				sample.idealMomentumPowerLoadingWattsPerSquareMeter(), 1.0e-12);
		assertEquals(sourceTerm.wakeSwirlKineticPowerLoadingWattsPerSquareMeterAt(samplePoint),
				sample.wakeSwirlKineticPowerLoadingWattsPerSquareMeter(), 1.0e-12);
		assertEquals(sourceTerm.totalWakeKineticPowerLoadingWattsPerSquareMeterAt(samplePoint),
				sample.totalWakeKineticPowerLoadingWattsPerSquareMeter(), 1.0e-12);
		assertVectorEquals(sourceTerm.farWakeAxialVelocityWorldMetersPerSecond(),
				sample.farWakeAxialVelocityWorldMetersPerSecond(), 1.0e-15);
		assertEquals(expectedWakeSwirlSpeed(sourceTerm, sampleRadius),
				sample.wakeSwirlVelocityWorldMetersPerSecond().length(), 1.0e-12);
		assertVectorEquals(
				sample.farWakeAxialVelocityWorldMetersPerSecond()
						.add(sample.wakeSwirlVelocityWorldMetersPerSecond()),
				sample.targetWakeVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVectorEquals(sourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter()
						.multiply(sourceTerm.diskAreaSquareMeters()),
				field.integratedBodyForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(sourceTerm.wakeAngularMomentumTorqueWorldNewtonMeters(),
				field.integratedWakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-12);
		assertEquals(sourceTerm.idealMomentumPowerLoadingWattsPerSquareMeter()
						* sourceTerm.diskAreaSquareMeters(),
				field.integratedIdealMomentumPowerWatts(), 1.0e-12);
		assertEquals(sourceTerm.wakeSwirlKineticPowerWatts(),
				field.integratedWakeSwirlKineticPowerWatts(), 1.0e-12);
		assertEquals(sourceTerm.totalWakeKineticPowerWatts(),
				field.integratedTotalWakeKineticPowerWatts(), 1.0e-12);
	}

	@Test
	void keepsCylindricalDiskBoundaryInclusiveAndRejectsOutsidePoints() {
		PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm =
				hoverSourceTerm();
		PropellerArchiveCtCpJActuatorDiskSourceField field =
				new PropellerArchiveCtCpJActuatorDiskSourceField(List.of(sourceTerm), SOURCE_THICKNESS);
		double diskRadius = Math.sqrt(sourceTerm.diskAreaSquareMeters() / Math.PI);
		Vec3 radial = perpendicularUnit(sourceTerm.diskNormalWorld());
		Vec3 radialBoundary = sourceTerm.diskCenterWorldMeters().add(radial.multiply(diskRadius));
		Vec3 axialBoundary = sourceTerm.diskCenterWorldMeters()
				.add(sourceTerm.diskNormalWorld().multiply(SOURCE_THICKNESS * 0.5));
		Vec3 outsideRadial = sourceTerm.diskCenterWorldMeters().add(radial.multiply(diskRadius + 0.002));
		Vec3 outsideAxial = sourceTerm.diskCenterWorldMeters()
				.add(sourceTerm.diskNormalWorld().multiply(SOURCE_THICKNESS * 0.5 + 0.002));

		assertTrue(field.containsActuatorDiskVolume(sourceTerm, radialBoundary));
		assertTrue(field.containsActuatorDiskVolume(sourceTerm, axialBoundary));
		assertFalse(field.sampleAt(outsideRadial).insideAnySource());
		assertFalse(field.sampleAt(outsideAxial).insideAnySource());
		assertVectorEquals(Vec3.ZERO,
				field.sampleAt(outsideRadial).bodyForceDensityWorldNewtonsPerCubicMeter(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				field.sampleAt(outsideAxial).targetWakeVelocityWorldMetersPerSecond(), 1.0e-15);
	}

	@Test
	void wakeSwirlAndAngularMomentumDensityUseRadialProfile() {
		PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm =
				hoverSourceTerm();
		PropellerArchiveCtCpJActuatorDiskSourceField field =
				new PropellerArchiveCtCpJActuatorDiskSourceField(List.of(sourceTerm), SOURCE_THICKNESS);
		Vec3 radial = perpendicularUnit(sourceTerm.diskNormalWorld());
		double diskRadius = sourceTerm.diskRadiusMeters();
		double innerRadius = diskRadius * 0.25;
		double outerRadius = diskRadius * 0.75;

		PropellerArchiveCtCpJActuatorDiskSourceField.SourceFieldSample center =
				field.sampleAt(sourceTerm.diskCenterWorldMeters());
		PropellerArchiveCtCpJActuatorDiskSourceField.SourceFieldSample inner =
				field.sampleAt(sourceTerm.diskCenterWorldMeters().add(radial.multiply(innerRadius)));
		PropellerArchiveCtCpJActuatorDiskSourceField.SourceFieldSample outer =
				field.sampleAt(sourceTerm.diskCenterWorldMeters().add(radial.multiply(outerRadius)));

		assertVectorEquals(Vec3.ZERO, center.wakeSwirlVelocityWorldMetersPerSecond(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				center.wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter(), 1.0e-15);
		assertEquals(0.0, center.wakeSwirlKineticPowerLoadingWattsPerSquareMeter(), 1.0e-15);
		assertEquals(expectedWakeSwirlSpeed(sourceTerm, innerRadius),
				inner.wakeSwirlVelocityWorldMetersPerSecond().length(), 1.0e-12);
		assertEquals(expectedWakeSwirlSpeed(sourceTerm, outerRadius),
				outer.wakeSwirlVelocityWorldMetersPerSecond().length(), 1.0e-12);
		assertVectorEquals(expectedWakeTorqueDensity(
						sourceTerm,
						sourceTerm.diskCenterWorldMeters().add(radial.multiply(innerRadius))),
				inner.wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter(), 1.0e-12);
		assertVectorEquals(expectedWakeTorqueDensity(
						sourceTerm,
						sourceTerm.diskCenterWorldMeters().add(radial.multiply(outerRadius))),
				outer.wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter(), 1.0e-12);
		assertEquals(sourceTerm.wakeSwirlKineticPowerLoadingWattsPerSquareMeterAt(
						sourceTerm.diskCenterWorldMeters().add(radial.multiply(innerRadius))),
				inner.wakeSwirlKineticPowerLoadingWattsPerSquareMeter(), 1.0e-12);
		assertEquals(sourceTerm.wakeSwirlKineticPowerLoadingWattsPerSquareMeterAt(
						sourceTerm.diskCenterWorldMeters().add(radial.multiply(outerRadius))),
				outer.wakeSwirlKineticPowerLoadingWattsPerSquareMeter(), 1.0e-12);
		assertTrue(outer.wakeSwirlVelocityWorldMetersPerSecond().length()
				> inner.wakeSwirlVelocityWorldMetersPerSecond().length());
		assertTrue(outer.wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter().length()
				> inner.wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter().length());
		assertTrue(outer.wakeSwirlKineticPowerLoadingWattsPerSquareMeter()
				> inner.wakeSwirlKineticPowerLoadingWattsPerSquareMeter());
		assertEquals(0.0, outer.wakeSwirlVelocityWorldMetersPerSecond().dot(radial), 1.0e-12);
		assertEquals(0.0, outer.wakeSwirlVelocityWorldMetersPerSecond()
				.dot(sourceTerm.wakeAngularMomentumTorqueWorldNewtonMeters()), 1.0e-12);
	}

	@Test
	void superposesOverlappingAppliedSourcesAndSkipsBlockedSources() {
		PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample appliedSource =
				hoverSourceTerm();
		PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample blockedSource =
				blockedSourceTerm();
		PropellerArchiveCtCpJActuatorDiskSourceField field =
				new PropellerArchiveCtCpJActuatorDiskSourceField(
						List.of(appliedSource, appliedSource, blockedSource),
						SOURCE_THICKNESS
				);

		PropellerArchiveCtCpJActuatorDiskSourceField.SourceFieldSample sample =
				field.sampleAt(appliedSource.diskCenterWorldMeters());

		assertEquals(2, sample.contributingSourceCount());
		assertFalse(blockedSource.applied());
		assertVectorEquals(appliedSource.equivalentBodyForceWorldNewtonsPerCubicMeter(SOURCE_THICKNESS)
						.multiply(2.0),
				sample.bodyForceDensityWorldNewtonsPerCubicMeter(), 1.0e-12);
		assertVectorEquals(expectedWakeTorqueDensity(appliedSource, appliedSource.diskCenterWorldMeters())
						.multiply(2.0),
				sample.wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter(), 1.0e-12);
		assertEquals(appliedSource.pressureJumpPascals() * 2.0, sample.pressureJumpPascals(), 1.0e-12);
		assertVectorEquals(appliedSource.farWakeAxialVelocityWorldMetersPerSecond().multiply(2.0),
				sample.targetWakeVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVectorEquals(appliedSource.thrustSurfaceForceWorldNewtonsPerSquareMeter()
						.multiply(appliedSource.diskAreaSquareMeters() * 2.0),
				field.integratedBodyForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(appliedSource.wakeAngularMomentumTorqueWorldNewtonMeters().multiply(2.0),
				field.integratedWakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-12);
	}

	@Test
	void worldForceProviderExposesRawAndRuntimeReplacementSourceFields() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient());
		double[] omegas = fill(config.rotors().size(), hoverOmega);
		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample worldSample =
				PropellerArchiveCtCpJWorldForceApplicationProvider.sampleStaticAnchoredConfigurationFromWorldKinematics(
						"apDrone",
						"source_field_provider_hover",
						config,
						MOMENT_REFERENCE_WORLD,
						Quaternion.IDENTITY,
						Vec3.ZERO,
						Vec3.ZERO,
						Vec3.ZERO,
						null,
						omegas,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		PropellerArchiveCtCpJActuatorDiskSourceField rawField =
				worldSample.actuatorDiskSourceField(SOURCE_THICKNESS);
		PropellerArchiveCtCpJActuatorDiskSourceField runtimeField =
				worldSample.runtimeReplacementActuatorDiskSourceField(SOURCE_THICKNESS);
		Vec3 firstRotorCenter = worldSample.rotorActuatorDiskSourceTerms().get(0).diskCenterWorldMeters();

		assertTrue(rawField.sampleAt(firstRotorCenter).insideAnySource());
		assertTrue(runtimeField.sampleAt(firstRotorCenter).insideAnySource());
		assertVectorEquals(worldSample.totalActuatorDiskSurfaceForceWorldNewtons(),
				rawField.integratedBodyForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(worldSample.runtimeReplacementTotalActuatorDiskSurfaceForceWorldNewtons(),
				runtimeField.integratedBodyForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(rawField.integratedBodyForceWorldNewtons(),
				runtimeField.integratedBodyForceWorldNewtons(), 1.0e-12);
	}

	@Test
	void voxelGridSubcellSamplingApproximatesIntegratedDiskLoads() {
		PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm =
				hoverSourceTerm();
		PropellerArchiveCtCpJActuatorDiskSourceField field =
				new PropellerArchiveCtCpJActuatorDiskSourceField(List.of(sourceTerm), SOURCE_THICKNESS);
		double cellSize = 0.0125;
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						sourceTerm.diskCenterWorldMeters().add(new Vec3(-5.5 * cellSize, -2.0 * cellSize,
								-5.5 * cellSize)),
						cellSize,
						11,
						4,
						11
				);

		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample gridSample =
				field.sampleVoxelGrid(grid, 3);

		assertEquals(grid.totalCellCount(), gridSample.cells().size());
		assertTrue(gridSample.activeCellCount() > 0);
		assertTrue(gridSample.activeSubsampleCount() > gridSample.activeCellCount());
		assertRelativeClose(sourceTerm.sourceVolumeCubicMeters(SOURCE_THICKNESS),
				gridSample.sampledSourceVolumeCubicMeters(), 0.04);
		assertVectorRelativeClose(field.integratedBodyForceWorldNewtons(),
				gridSample.integratedBodyForceWorldNewtons(), 0.04);
		assertVectorRelativeClose(field.integratedWakeAngularMomentumTorqueWorldNewtonMeters(),
				gridSample.integratedWakeAngularMomentumTorqueWorldNewtonMeters(), 0.04);
		assertRelativeClose(field.integratedIdealMomentumPowerWatts(),
				gridSample.integratedIdealMomentumPowerWatts(SOURCE_THICKNESS), 0.04);
		assertRelativeClose(field.integratedWakeSwirlKineticPowerWatts(),
				gridSample.integratedWakeSwirlKineticPowerWatts(SOURCE_THICKNESS), 0.04);
		assertRelativeClose(field.integratedTotalWakeKineticPowerWatts(),
				gridSample.integratedTotalWakeKineticPowerWatts(SOURCE_THICKNESS), 0.04);
	}

	@Test
	void coarseVoxelSampleCarriesVolumeAveragedSourceTerms() {
		PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm =
				hoverSourceTerm();
		PropellerArchiveCtCpJActuatorDiskSourceField field =
				new PropellerArchiveCtCpJActuatorDiskSourceField(List.of(sourceTerm), SOURCE_THICKNESS);
		double diskRadius = Math.sqrt(sourceTerm.diskAreaSquareMeters() / Math.PI);
		double cellSize = diskRadius * 2.4;
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						sourceTerm.diskCenterWorldMeters().add(new Vec3(-0.5 * cellSize, -0.5 * cellSize,
								-0.5 * cellSize)),
						cellSize,
						1,
						1,
						1
				);

		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample cell =
				field.sampleVoxelGrid(grid, 5).cells().get(0);

		assertTrue(cell.active());
		assertTrue(cell.sourceVolumeFraction() > 0.0);
		assertTrue(cell.sourceVolumeFraction() < 1.0);
		assertVectorEquals(sourceTerm.equivalentBodyForceWorldNewtonsPerCubicMeter(SOURCE_THICKNESS)
						.multiply(cell.sourceVolumeFraction()),
				cell.bodyForceDensityWorldNewtonsPerCubicMeter(), 1.0e-12);
		assertEquals(sourceTerm.pressureJumpPascals() * cell.sourceVolumeFraction(),
				cell.pressureJumpPascals(), 1.0e-12);
		assertVectorEquals(sourceTerm.farWakeAxialVelocityWorldMetersPerSecond(),
				cell.farWakeAxialVelocityWorldMetersPerSecond(), 1.0e-12);
		assertTrue(cell.farWakeAxialVelocityWorldMetersPerSecond().length()
				> sourceTerm.farWakeAxialVelocityWorldMetersPerSecond().length()
				* cell.sourceVolumeFraction());
		assertVectorEquals(cell.farWakeAxialVelocityWorldMetersPerSecond()
						.add(cell.wakeSwirlVelocityWorldMetersPerSecond()),
				cell.targetWakeVelocityWorldMetersPerSecond(), 1.0e-12);
	}

	@Test
	void conservativeVoxelGridRescalesCoarseCellsToContinuousLoads() {
		PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm =
				hoverSourceTerm();
		PropellerArchiveCtCpJActuatorDiskSourceField field =
				new PropellerArchiveCtCpJActuatorDiskSourceField(List.of(sourceTerm), SOURCE_THICKNESS);
		double diskRadius = Math.sqrt(sourceTerm.diskAreaSquareMeters() / Math.PI);
		double cellSize = diskRadius * 2.4;
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						sourceTerm.diskCenterWorldMeters().add(new Vec3(-0.5 * cellSize, -0.5 * cellSize,
								-0.5 * cellSize)),
						cellSize,
						1,
						1,
						1
				);

		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample geometric =
				field.sampleVoxelGrid(grid, 5);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample conservative =
				field.sampleConservativeVoxelGrid(grid, 5);

		assertTrue(geometric.integratedBodyForceWorldNewtons()
				.subtract(field.integratedBodyForceWorldNewtons()).length() > 1.0e-6);
		assertVectorEquals(field.integratedBodyForceWorldNewtons(),
				conservative.integratedBodyForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(field.integratedWakeAngularMomentumTorqueWorldNewtonMeters(),
				conservative.integratedWakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-12);
		assertEquals(field.integratedIdealMomentumPowerWatts(),
				conservative.integratedIdealMomentumPowerWatts(SOURCE_THICKNESS), 1.0e-12);
		assertEquals(field.integratedWakeSwirlKineticPowerWatts(),
				conservative.integratedWakeSwirlKineticPowerWatts(SOURCE_THICKNESS), 1.0e-12);
		assertEquals(field.integratedTotalWakeKineticPowerWatts(),
				conservative.integratedTotalWakeKineticPowerWatts(SOURCE_THICKNESS), 1.0e-12);
		assertEquals(geometric.activeSubsampleCount(), conservative.activeSubsampleCount());
		assertEquals(geometric.activeCellCount(), conservative.activeCellCount());
		assertTrue(conservative.cells().get(0).pressureJumpPascals()
				> geometric.cells().get(0).pressureJumpPascals());
		assertVectorEquals(geometric.cells().get(0).farWakeAxialVelocityWorldMetersPerSecond(),
				conservative.cells().get(0).farWakeAxialVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVectorEquals(geometric.cells().get(0).wakeSwirlVelocityWorldMetersPerSecond(),
				conservative.cells().get(0).wakeSwirlVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVectorEquals(geometric.cells().get(0).targetWakeVelocityWorldMetersPerSecond(),
				conservative.cells().get(0).targetWakeVelocityWorldMetersPerSecond(), 1.0e-12);
	}

	@Test
	void enclosingVoxelGridBoundsTiltedActuatorDiskAndConservesLoads() {
		Quaternion bodyToWorld = new Quaternion(
				Math.cos(Math.PI * 0.25),
				0.0,
				0.0,
				Math.sin(Math.PI * 0.25)
		);
		PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm =
				hoverSourceTerm(bodyToWorld);
		PropellerArchiveCtCpJActuatorDiskSourceField field =
				new PropellerArchiveCtCpJActuatorDiskSourceField(List.of(sourceTerm), SOURCE_THICKNESS);
		double cellSize = 0.02;

		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				field.enclosingVoxelGrid(cellSize, 1);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sample =
				field.sampleConservativeVoxelGrid(grid, 3);
		double diskRadius = Math.sqrt(sourceTerm.diskAreaSquareMeters() / Math.PI);
		Vec3 tangentU = perpendicularUnit(sourceTerm.diskNormalWorld());
		Vec3 tangentV = sourceTerm.diskNormalWorld().cross(tangentU).normalized();

		assertTrue(grid.cellCountX() > 1);
		assertTrue(grid.cellCountY() > 1);
		assertTrue(grid.cellCountZ() > 1);
		assertTrue(pointInsideGrid(grid, sourceTerm.diskCenterWorldMeters().add(tangentU.multiply(diskRadius))));
		assertTrue(pointInsideGrid(grid, sourceTerm.diskCenterWorldMeters().subtract(tangentU.multiply(diskRadius))));
		assertTrue(pointInsideGrid(grid, sourceTerm.diskCenterWorldMeters().add(tangentV.multiply(diskRadius))));
		assertTrue(pointInsideGrid(grid, sourceTerm.diskCenterWorldMeters().subtract(tangentV.multiply(diskRadius))));
		assertTrue(pointInsideGrid(grid, sourceTerm.diskCenterWorldMeters()
				.add(sourceTerm.diskNormalWorld().multiply(SOURCE_THICKNESS * 0.5))));
		assertTrue(pointInsideGrid(grid, sourceTerm.diskCenterWorldMeters()
				.subtract(sourceTerm.diskNormalWorld().multiply(SOURCE_THICKNESS * 0.5))));
		assertVectorEquals(field.integratedBodyForceWorldNewtons(),
				sample.integratedBodyForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(field.integratedWakeAngularMomentumTorqueWorldNewtonMeters(),
				sample.integratedWakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-12);
		assertEquals(field.integratedIdealMomentumPowerWatts(),
				sample.integratedIdealMomentumPowerWatts(SOURCE_THICKNESS), 1.0e-12);
		assertEquals(field.integratedWakeSwirlKineticPowerWatts(),
				sample.integratedWakeSwirlKineticPowerWatts(SOURCE_THICKNESS), 1.0e-12);
		assertEquals(field.integratedTotalWakeKineticPowerWatts(),
				sample.integratedTotalWakeKineticPowerWatts(SOURCE_THICKNESS), 1.0e-12);
	}

	@Test
	void enclosingWakeVoxelGridExtendsDownstreamWithoutChangingConservativeLoads() {
		PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm =
				hoverSourceTerm();
		PropellerArchiveCtCpJActuatorDiskSourceField field =
				new PropellerArchiveCtCpJActuatorDiskSourceField(List.of(sourceTerm), SOURCE_THICKNESS);
		double cellSize = 0.02;
		double wakeLength = 0.40;

		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec sourceGrid =
				field.enclosingVoxelGrid(cellSize, 1);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec wakeGrid =
				field.enclosingWakeVoxelGrid(cellSize, 1, wakeLength);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sample =
				field.sampleConservativeVoxelGrid(wakeGrid, 3);
		Vec3 wakeDirection = sourceTerm.farWakeAxialVelocityWorldMetersPerSecond().normalized();

		assertTrue(wakeGrid.totalCellCount() > sourceGrid.totalCellCount());
		assertTrue(pointInsideGrid(wakeGrid, sourceTerm.diskCenterWorldMeters()));
		assertTrue(pointInsideGrid(wakeGrid,
				sourceTerm.diskCenterWorldMeters().add(wakeDirection.multiply(wakeLength))));
		assertVectorEquals(field.integratedBodyForceWorldNewtons(),
				sample.integratedBodyForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(field.integratedWakeAngularMomentumTorqueWorldNewtonMeters(),
				sample.integratedWakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-12);
		assertEquals(field.enclosingVoxelGrid(cellSize, 1),
				field.enclosingWakeVoxelGrid(cellSize, 1, 0.0));
	}

	@Test
	void enclosingVoxelGridForEmptyFieldProducesSingleZeroCell() {
		PropellerArchiveCtCpJActuatorDiskSourceField field =
				new PropellerArchiveCtCpJActuatorDiskSourceField(List.of(blockedSourceTerm()), SOURCE_THICKNESS);

		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid = field.enclosingVoxelGrid(0.05, 2);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sample =
				field.sampleConservativeVoxelGrid(grid, 2);

		assertEquals(Vec3.ZERO, grid.originWorldMeters());
		assertEquals(1, grid.cellCountX());
		assertEquals(1, grid.cellCountY());
		assertEquals(1, grid.cellCountZ());
		assertEquals(1, sample.cells().size());
		assertFalse(sample.cells().get(0).active());
		assertVectorEquals(Vec3.ZERO, sample.integratedBodyForceWorldNewtons(), 1.0e-15);
	}

	@Test
	void rejectsInvalidThicknessAndAllowsEmptyField() {
		assertThrows(IllegalArgumentException.class,
				() -> new PropellerArchiveCtCpJActuatorDiskSourceField(List.of(), 0.0));

		PropellerArchiveCtCpJActuatorDiskSourceField field =
				new PropellerArchiveCtCpJActuatorDiskSourceField(List.of(), SOURCE_THICKNESS);

		assertFalse(field.sampleAt(Vec3.ZERO).insideAnySource());
		assertVectorEquals(Vec3.ZERO, field.integratedBodyForceWorldNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, field.integratedWakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-15);
		assertEquals(0.0, field.integratedIdealMomentumPowerWatts(), 1.0e-15);
		assertEquals(0.0, field.integratedWakeSwirlKineticPowerWatts(), 1.0e-15);
		assertEquals(0.0, field.integratedTotalWakeKineticPowerWatts(), 1.0e-15);
	}

	private static PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample hoverSourceTerm() {
		return hoverSourceTerm(Quaternion.IDENTITY);
	}

	private static PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample hoverSourceTerm(
			Quaternion bodyToWorld
	) {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double omega = 6_000.0 * 2.0 * Math.PI / 60.0;
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromRelativeAirVelocity(
						"apDrone",
						"source_field_hover",
						rotor,
						Vec3.ZERO,
						omega,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		return sample.actuatorDiskSourceTerm(0, MOMENT_REFERENCE_WORLD, bodyToWorld);
	}

	private static PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample blockedSourceTerm() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double omega = 6_000.0 * 2.0 * Math.PI / 60.0;
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
						"apDrone",
						"source_field_reverse_block",
						rotor,
						-4.5,
						omega,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		return sample.actuatorDiskSourceTerm(0, MOMENT_REFERENCE_WORLD, Quaternion.IDENTITY);
	}

	private static Vec3 perpendicularUnit(Vec3 axis) {
		Vec3 candidate = new Vec3(axis.y(), -axis.x(), 0.0);
		if (candidate.lengthSquared() <= 1.0e-12) {
			candidate = new Vec3(0.0, axis.z(), -axis.y());
		}
		return candidate.normalized();
	}

	private static double[] fill(int count, double value) {
		double[] values = new double[count];
		for (int i = 0; i < values.length; i++) {
			values[i] = value;
		}
		return values;
	}

	private static double expectedWakeSwirlSpeed(
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm,
			double radialDistanceMeters
	) {
		return sourceTerm.wakeSwirlAngularVelocityRadiansPerSecond()
				* Math.min(radialDistanceMeters, sourceTerm.wakeSwirlSupportRadiusMeters());
	}

	private static Vec3 expectedWakeTorqueDensity(
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm,
			Vec3 samplePointWorldMeters
	) {
		return sourceTerm.equivalentWakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter(SOURCE_THICKNESS)
				.multiply(sourceTerm.wakeAngularMomentumTorqueDensityRadialWeight(samplePointWorldMeters));
	}

	private static void assertVectorEquals(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance);
		assertEquals(expected.y(), actual.y(), tolerance);
		assertEquals(expected.z(), actual.z(), tolerance);
	}

	private static boolean pointInsideGrid(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid,
			Vec3 point
	) {
		Vec3 origin = grid.originWorldMeters();
		double maxX = origin.x() + grid.cellCountX() * grid.cellSizeMeters();
		double maxY = origin.y() + grid.cellCountY() * grid.cellSizeMeters();
		double maxZ = origin.z() + grid.cellCountZ() * grid.cellSizeMeters();
		return point.x() >= origin.x() - 1.0e-12
				&& point.y() >= origin.y() - 1.0e-12
				&& point.z() >= origin.z() - 1.0e-12
				&& point.x() <= maxX + 1.0e-12
				&& point.y() <= maxY + 1.0e-12
				&& point.z() <= maxZ + 1.0e-12;
	}

	private static void assertVectorRelativeClose(Vec3 expected, Vec3 actual, double relativeTolerance) {
		double scale = Math.max(1.0e-12, expected.length());
		assertTrue(expected.subtract(actual).length() <= scale * relativeTolerance,
				"expected " + expected + " actual " + actual);
	}

	private static void assertRelativeClose(double expected, double actual, double relativeTolerance) {
		double scale = Math.max(1.0e-12, Math.abs(expected));
		assertTrue(Math.abs(expected - actual) <= scale * relativeTolerance,
				"expected " + expected + " actual " + actual);
	}
}
