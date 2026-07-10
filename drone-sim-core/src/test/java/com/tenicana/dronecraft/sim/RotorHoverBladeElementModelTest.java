package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RotorHoverBladeElementModelTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.81e-5;
	private static final double HIGH_RPM = 5_943.333;
	private static final double UIUC_HIGH_RPM_CT = 0.140450;
	private static final double UIUC_HIGH_RPM_CP = 0.080081;

	@Test
	void da4002GeometryRetainsUiucMeasuredStationsAndBladeCount() {
		RotorHoverBladeProfilePowerModel.BladeGeometry fiveInch =
				UiucDa4002PropellerGeometry.fiveByThreePointSevenFiveTwoBlade();
		RotorHoverBladeProfilePowerModel.BladeGeometry nineInch =
				UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade();

		assertEquals(2, UiucDa4002PropellerGeometry.geometries().size());
		assertEquals(2, fiveInch.bladeCount());
		assertEquals(2, nineInch.bladeCount());
		assertEquals(18, fiveInch.stationCount());
		assertEquals(18, nineInch.stationCount());
		assertEquals(0.1804, fiveInch.chordToRadiusAt(0.75), 0.0);
		assertEquals(18.786, Math.toDegrees(nineInch.pitchAngleRadiansAt(0.75)), 1.0e-12);
		assertTrue(nineInch.sourceUrl().endsWith("da4002_9x6.75_geom.txt"));
		assertTrue(UiucDa4002PropellerGeometry.NINE_INCH_STATIC_SOURCE_URL
				.endsWith("da4002_9x6.75_static_1107rd.txt"));
	}

	@Test
	void highRpmHoverProducesClosedSiLoadsAndExposesMeasuredPowerResidual() {
		RotorHoverBladeElementModel.HoverSample sample = solve(
				HIGH_RPM,
				RotorHoverBladeElementModel.DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL,
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
		);
		double diameter = UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS;
		double revolutionsPerSecond = HIGH_RPM / 60.0;
		double omega = revolutionsPerSecond * 2.0 * Math.PI;
		double measuredPower = UIUC_HIGH_RPM_CP
				* RHO
				* Math.pow(revolutionsPerSecond, 3.0)
				* Math.pow(diameter, 5.0);
		double measuredPowerResidualFraction = (sample.shaftPowerWatts() - measuredPower)
				/ measuredPower;

		assertEquals(RotorHoverBladeElementModel.Status.SOLVED, sample.status());
		assertEquals(136, sample.annulusCount());
		assertTrue(Double.isFinite(sample.thrustNewtons()) && sample.thrustNewtons() > 0.0);
		assertTrue(Double.isFinite(sample.shaftPowerWatts()) && sample.shaftPowerWatts() > 0.0);
		assertTrue(Double.isFinite(sample.shaftTorqueNewtonMeters())
				&& sample.shaftTorqueNewtonMeters() > 0.0);
		assertEquals(
				sample.thrustCoefficientCt()
						* RHO
						* revolutionsPerSecond
						* revolutionsPerSecond
						* Math.pow(diameter, 4.0),
				sample.thrustNewtons(),
				1.0e-12
		);
		assertEquals(
				sample.powerCoefficientCp()
						* RHO
						* Math.pow(revolutionsPerSecond, 3.0)
						* Math.pow(diameter, 5.0),
				sample.shaftPowerWatts(),
				1.0e-12
		);
		assertEquals(sample.shaftPowerWatts(), sample.shaftTorqueNewtonMeters() * omega, 1.0e-12);
		assertEquals(sample.powerCoefficientCp(), 2.0 * Math.PI * sample.torqueCoefficientCq(),
				1.0e-15);
		assertEquals(0.0, sample.thrustClosureResidualNewtons(), 1.0e-12);
		assertEquals(0.0, sample.torqueClosureResidualNewtonMeters(), 1.0e-15);
		assertEquals(0.0, sample.powerClosureResidualWatts(), 1.0e-12);
		assertEquals(UIUC_HIGH_RPM_CT, sample.thrustCoefficientCt(), 0.003);
		assertTrue(measuredPowerResidualFraction < -0.15);
		assertTrue(measuredPowerResidualFraction > -0.25);
		assertTrue(sample.liftInducedPowerOverUniformIdeal() > 1.0);
		assertTrue(sample.liftInducedPowerOverUniformIdeal() < 1.5);
		assertTrue(sample.hoverFigureOfMerit() > 0.5 && sample.hoverFigureOfMerit() < 0.8);
		assertTrue(sample.minimumReynoldsNumber() < 40_000.0);
		assertTrue(sample.maximumReynoldsNumber() > 60_000.0);
		assertTrue(sample.maximumReynoldsNumber() < 100_000.0);
		assertEquals(0, sample.reynoldsClampedAnnulusCount());
		assertTrue(sample.lowReynoldsExtensionAnnulusCount() > 0);
		assertTrue(sample.lowReynoldsExtensionAnnulusCount() < sample.annulusCount());
		assertEquals(1.0, sample.reynoldsSupportedThrustWeightFraction(), 1.0e-15);
		assertTrue(sample.fullySupportedThrustWeightFraction() > 0.7);
		assertTrue(sample.angleOfAttackClampedAnnulusCount() > 0);
		assertTrue(sample.annuli().stream().allMatch(annulus ->
				annulus.prandtlTipLossFactor() > 0.0
						&& annulus.prandtlTipLossFactor() <= 1.0));
	}

	@Test
	void coupledWakeRotationClosesLiftTorqueAngularMomentumWithoutHidingPowerGap() {
		RotorHoverBladeElementModel.HoverSample axialOnly = solve(
				HIGH_RPM,
				RotorHoverBladeElementModel.DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL,
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE,
				RotorHoverBladeElementModel.WakeRotationPolicy.AXIAL_MOMENTUM_ONLY
		);
		RotorHoverBladeElementModel.HoverSample coupled = solve(
				HIGH_RPM,
				RotorHoverBladeElementModel.DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL,
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE,
				RotorHoverBladeElementModel.WakeRotationPolicy
						.COUPLED_LIFT_TORQUE_ANGULAR_MOMENTUM
		);

		assertTrue(coupled.solved());
		assertEquals(coupled.liftInducedTorqueNewtonMeters(),
				coupled.momentumWakeTorqueNewtonMeters(), 1.0e-10);
		assertEquals(0.0, coupled.angularMomentumClosureResidualNewtonMeters(), 1.0e-10);
		assertEquals(axialOnly.liftInducedTorqueNewtonMeters(),
				axialOnly.angularMomentumClosureResidualNewtonMeters(), 1.0e-15);
		assertEquals(0.0, axialOnly.momentumWakeTorqueNewtonMeters(), 0.0);
		assertTrue(coupled.maximumTangentialInducedVelocityMetersPerSecond() > 0.0);
		assertTrue(coupled.maximumTangentialInductionToBladeSpeed() > 0.0);
		assertTrue(coupled.maximumTangentialInductionToBladeSpeed() < 0.06);
		assertTrue(coupled.wakeSwirlKineticPowerWatts() > 0.0);
		assertTrue(coupled.wakeSwirlKineticPowerWatts() < coupled.shaftPowerWatts());
		assertTrue(coupled.thrustCoefficientCt() < axialOnly.thrustCoefficientCt());
		assertTrue(coupled.powerCoefficientCp() < axialOnly.powerCoefficientCp());
		assertTrue((coupled.powerCoefficientCp() - UIUC_HIGH_RPM_CP) / UIUC_HIGH_RPM_CP < -0.20);
		assertTrue(coupled.annuli().stream().allMatch(annulus ->
				annulus.farWakeTangentialVelocityMetersPerSecond()
						== 2.0 * annulus.tangentialInducedVelocityMetersPerSecond()));
		assertTrue(coupled.annuli().stream().allMatch(annulus ->
				annulus.rootIterations() > 0
						&& annulus.rootIterations() < 1_100));
	}

	@Test
	void snelMcCrinkSensitivityRaisesNormalLoadWithinSourceSpanWithoutClosingPowerGap() {
		RotorHoverBladeElementModel.HoverSample baseline = solve(
				HIGH_RPM,
				RotorHoverBladeElementModel.DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL,
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE,
				RotorHoverBladeElementModel.WakeRotationPolicy.AXIAL_MOMENTUM_ONLY,
				SnelMcCrinkRotationalAugmentation.Policy.NONE
		);
		RotorHoverBladeElementModel.HoverSample augmented = solve(
				HIGH_RPM,
				RotorHoverBladeElementModel.DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL,
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE,
				RotorHoverBladeElementModel.WakeRotationPolicy.AXIAL_MOMENTUM_ONLY,
				SnelMcCrinkRotationalAugmentation.Policy.SNEL_MCCRINK_NORMAL_FORCE
		);
		RotorHoverBladeElementModel.HoverSample coupledAugmented = solve(
				HIGH_RPM,
				RotorHoverBladeElementModel.DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL,
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE,
				RotorHoverBladeElementModel.WakeRotationPolicy
						.COUPLED_LIFT_TORQUE_ANGULAR_MOMENTUM,
				SnelMcCrinkRotationalAugmentation.Policy.SNEL_MCCRINK_NORMAL_FORCE
		);

		assertTrue(augmented.solved());
		assertTrue(augmented.thrustCoefficientCt() > baseline.thrustCoefficientCt());
		assertTrue(augmented.powerCoefficientCp() > baseline.powerCoefficientCp());
		assertTrue(augmented.thrustNewtons() > baseline.thrustNewtons());
		assertTrue(augmented.shaftPowerWatts() > baseline.shaftPowerWatts());
		assertTrue(augmented.shaftTorqueNewtonMeters() > baseline.shaftTorqueNewtonMeters());
		assertTrue((augmented.powerCoefficientCp() - UIUC_HIGH_RPM_CP) / UIUC_HIGH_RPM_CP
				< -0.15);
		assertEquals(0.0, augmented.thrustClosureResidualNewtons(), 1.0e-12);
		assertEquals(0.0, augmented.torqueClosureResidualNewtonMeters(), 1.0e-15);
		assertEquals(0.0, augmented.powerClosureResidualWatts(), 1.0e-12);
		assertTrue(augmented.rotationalAugmentationTorqueNewtonMeters() > 0.0);
		assertTrue(augmented.rotationalAugmentationTorqueNewtonMeters()
				< augmented.liftInducedTorqueNewtonMeters());
		assertEquals(
				augmented.rotationalAugmentationTorqueNewtonMeters()
						* augmented.query().angularVelocityRadiansPerSecond(),
				augmented.rotationalAugmentationPowerWatts(),
				1.0e-12
		);
		assertTrue(coupledAugmented.solved());
		assertEquals(
				coupledAugmented.liftInducedTorqueNewtonMeters(),
				coupledAugmented.momentumWakeTorqueNewtonMeters(),
				1.0e-10
		);
		assertEquals(0.0, coupledAugmented.angularMomentumClosureResidualNewtonMeters(),
				1.0e-10);
		assertTrue(coupledAugmented.maximumTangentialInductionToBladeSpeed() > 0.09);
		assertTrue(coupledAugmented.maximumTangentialInductionToBladeSpeed() < 0.12);
		assertEquals(
				augmented.annulusCount(),
				augmented.rotationalAugmentationAppliedAnnulusCount()
						+ augmented.rotationalAugmentationSourceSpanLimitedAnnulusCount()
		);
		assertTrue(augmented.rotationalAugmentationAppliedOnClampedPolarAnnulusCount() > 0);
		assertTrue(augmented.rotationalAugmentationAppliedOnClampedPolarAnnulusCount()
				< augmented.rotationalAugmentationAppliedAnnulusCount());
		assertTrue(augmented.maximumAbsoluteRotationalLiftCoefficientDelta() > 0.4);
		assertTrue(augmented.maximumAbsoluteRotationalDragCoefficientDelta() > 0.09);
		assertTrue(augmented.rotationalAugmentationRequiresPropellerSpecificValidation());
		assertTrue(augmented.annuli().stream()
				.filter(annulus -> annulus.rotationalAugmentation().applied())
				.allMatch(annulus -> annulus.radialFraction()
						<= SnelMcCrinkRotationalAugmentation.MAX_APPLIED_RADIAL_FRACTION));
		assertTrue(augmented.annuli().stream()
				.filter(annulus -> annulus.rotationalAugmentation().sourceSpanLimited())
				.allMatch(annulus -> annulus.radialFraction()
						> SnelMcCrinkRotationalAugmentation.MAX_APPLIED_RADIAL_FRACTION));
		assertEquals(0, baseline.rotationalAugmentationAppliedAnnulusCount());
		assertEquals(0.0, baseline.rotationalAugmentationTorqueNewtonMeters(), 0.0);
		assertEquals(0.0, baseline.maximumAbsoluteRotationalLiftCoefficientDelta(), 1.0e-15);
		assertTrue(!baseline.rotationalAugmentationRequiresPropellerSpecificValidation());
	}

	@Test
	void geometricallySimilarFiveAndNineInchPropsRemainConsistentAtMatchedReynolds() {
		RotorHoverBladeElementModel.HoverSample fiveInch = solveGeometry(
				UiucDa4002PropellerGeometry.fiveByThreePointSevenFiveTwoBlade(),
				UiucDa4002PropellerGeometry.FIVE_INCH_DIAMETER_METERS,
				6_480.0
		);
		RotorHoverBladeElementModel.HoverSample nineInch = solveGeometry(
				UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade(),
				UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS,
				2_000.0
		);

		assertTrue(fiveInch.solved());
		assertTrue(nineInch.solved());
		assertEquals(nineInch.thrustCoefficientCt(), fiveInch.thrustCoefficientCt(), 0.003);
		assertEquals(nineInch.powerCoefficientCp(), fiveInch.powerCoefficientCp(), 0.004);
		assertTrue(fiveInch.reynoldsSupportedThrustWeightFraction() > 0.95);
		assertTrue(nineInch.reynoldsSupportedThrustWeightFraction() > 0.95);
	}

	@Test
	void reynoldsTrendConvergesNumericallyAndStrictEnvelopeBlocksUnsupportedSections() {
		RotorHoverBladeElementModel.HoverSample lowRpm = solve(
				1_546.667,
				RotorHoverBladeElementModel.DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL,
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
		);
		RotorHoverBladeElementModel.HoverSample highRpm = solve(
				HIGH_RPM,
				RotorHoverBladeElementModel.DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL,
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
		);
		RotorHoverBladeElementModel.HoverSample coarseHighRpm = solve(
				HIGH_RPM,
				4,
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
		);
		RotorHoverBladeElementModel.HoverSample strict = solve(
				HIGH_RPM,
				RotorHoverBladeElementModel.DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL,
				Sda1075XfoilSectionPolar.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
		);

		assertTrue(lowRpm.solved());
		assertTrue(highRpm.solved());
		assertTrue(highRpm.thrustCoefficientCt() > lowRpm.thrustCoefficientCt());
		assertTrue(lowRpm.reynoldsClampedAnnulusCount() > 0);
		assertTrue(lowRpm.reynoldsClampedAnnulusCount() < lowRpm.annulusCount());
		assertTrue(lowRpm.reynoldsSupportedThrustWeightFraction() > 0.9);
		assertEquals(0, highRpm.reynoldsClampedAnnulusCount());
		assertEquals(highRpm.thrustCoefficientCt(), coarseHighRpm.thrustCoefficientCt(), 0.003);
		assertEquals(highRpm.powerCoefficientCp(), coarseHighRpm.powerCoefficientCp(), 0.002);
		assertEquals(RotorHoverBladeElementModel.Status.BLOCKED_SECTION_POLAR_ENVELOPE,
				strict.status());
		assertTrue(strict.blocked());
		assertEquals(0.0, strict.thrustNewtons(), 0.0);
		assertEquals(0.0, strict.shaftPowerWatts(), 0.0);
		assertTrue(strict.annuli().get(strict.annuli().size() - 1).polar().blocked());
	}

	private static RotorHoverBladeElementModel.HoverSample solve(
			double rpm,
			int annuliPerGeometryInterval,
			Sda1075XfoilSectionPolar.EnvelopePolicy envelopePolicy
	) {
		return solve(
				rpm,
				annuliPerGeometryInterval,
				envelopePolicy,
				RotorHoverBladeElementModel.WakeRotationPolicy.AXIAL_MOMENTUM_ONLY,
				SnelMcCrinkRotationalAugmentation.Policy.NONE
		);
	}

	private static RotorHoverBladeElementModel.HoverSample solve(
			double rpm,
			int annuliPerGeometryInterval,
			Sda1075XfoilSectionPolar.EnvelopePolicy envelopePolicy,
			RotorHoverBladeElementModel.WakeRotationPolicy wakeRotationPolicy
	) {
		return solve(
				rpm,
				annuliPerGeometryInterval,
				envelopePolicy,
				wakeRotationPolicy,
				SnelMcCrinkRotationalAugmentation.Policy.NONE
		);
	}

	private static RotorHoverBladeElementModel.HoverSample solve(
			double rpm,
			int annuliPerGeometryInterval,
			Sda1075XfoilSectionPolar.EnvelopePolicy envelopePolicy,
			RotorHoverBladeElementModel.WakeRotationPolicy wakeRotationPolicy,
			SnelMcCrinkRotationalAugmentation.Policy rotationalAugmentationPolicy
	) {
		RotorHoverBladeElementModel.HoverQuery query = new RotorHoverBladeElementModel.HoverQuery(
				UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade(),
				UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS * 0.5,
				RHO,
				DYNAMIC_VISCOSITY_PASCAL_SECONDS,
				rpm * 2.0 * Math.PI / 60.0,
				annuliPerGeometryInterval,
				envelopePolicy,
				wakeRotationPolicy,
				rotationalAugmentationPolicy
		);
		return RotorHoverBladeElementModel.solve(query);
	}

	private static RotorHoverBladeElementModel.HoverSample solveGeometry(
			RotorHoverBladeProfilePowerModel.BladeGeometry geometry,
			double diameterMeters,
			double rpm
	) {
		return RotorHoverBladeElementModel.solve(new RotorHoverBladeElementModel.HoverQuery(
				geometry,
				diameterMeters * 0.5,
				RHO,
				DYNAMIC_VISCOSITY_PASCAL_SECONDS,
				rpm * 2.0 * Math.PI / 60.0,
				RotorHoverBladeElementModel.DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL,
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
		));
	}
}
