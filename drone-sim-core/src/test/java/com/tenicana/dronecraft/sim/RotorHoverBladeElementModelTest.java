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
		assertTrue(sample.reynoldsClampedAnnulusCount() > 0);
		assertTrue(sample.reynoldsClampedAnnulusCount() < sample.annulusCount());
		assertTrue(sample.angleOfAttackClampedAnnulusCount() > 0);
		assertTrue(sample.annuli().stream().allMatch(annulus ->
				annulus.prandtlTipLossFactor() > 0.0
						&& annulus.prandtlTipLossFactor() <= 1.0));
	}

	@Test
	void reynoldsTrendConvergesNumericallyAndStrictEnvelopeBlocksLowReSections() {
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
		assertEquals(lowRpm.annulusCount(), lowRpm.reynoldsClampedAnnulusCount());
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
		RotorHoverBladeElementModel.HoverQuery query = new RotorHoverBladeElementModel.HoverQuery(
				UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade(),
				UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS * 0.5,
				RHO,
				DYNAMIC_VISCOSITY_PASCAL_SECONDS,
				rpm * 2.0 * Math.PI / 60.0,
				annuliPerGeometryInterval,
				envelopePolicy
		);
		return RotorHoverBladeElementModel.solve(query);
	}
}
