package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RotorAxialBladeElementModelTest {
	private static final double RHO = 1.225;
	private static final double DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.81e-5;
	private static final double RPM = 2_013.0;
	private static final double OMEGA = RPM * 2.0 * Math.PI / 60.0;
	private static final double DIAMETER = UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS;

	@Test
	void zeroAdvanceRatioIsExactlyTheExistingHoverSolution() {
		RotorAxialBladeElementModel.AxialSample axial = solve(0.0);
		RotorHoverBladeElementModel.HoverSample hover = RotorHoverBladeElementModel.solve(
				new RotorHoverBladeElementModel.HoverQuery(
						UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade(),
						DIAMETER * 0.5,
						RHO,
						DYNAMIC_VISCOSITY_PASCAL_SECONDS,
						OMEGA,
						RotorHoverBladeElementModel.DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL,
						Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
				)
		);

		assertTrue(axial.solved());
		assertEquals(0.0, axial.advanceRatioJ(), 0.0);
		assertEquals(hover.thrustNewtons(), axial.thrustNewtons(), 0.0);
		assertEquals(hover.shaftPowerWatts(), axial.shaftPowerWatts(), 0.0);
		assertEquals(hover.shaftTorqueNewtonMeters(), axial.shaftTorqueNewtonMeters(), 0.0);
		assertEquals(hover.thrustCoefficientCt(), axial.thrustCoefficientCt(), 0.0);
		assertEquals(hover.powerCoefficientCp(), axial.powerCoefficientCp(), 0.0);
		assertEquals(0.0, axial.propulsiveEfficiencyEta(), 0.0);
	}

	@Test
	void lowMidAndHighAdvanceSamplesRemainFiniteClosedAndTrendDown() {
		RotorAxialBladeElementModel.AxialSample low = solve(0.30);
		RotorAxialBladeElementModel.AxialSample mid = solve(0.60);
		RotorAxialBladeElementModel.AxialSample high = solve(0.75);

		assertTrue(low.solved() && mid.solved() && high.solved());
		assertTrue(low.thrustCoefficientCt() > mid.thrustCoefficientCt());
		assertTrue(mid.thrustCoefficientCt() > high.thrustCoefficientCt());
		assertTrue(low.powerCoefficientCp() > mid.powerCoefficientCp());
		assertTrue(mid.powerCoefficientCp() > high.powerCoefficientCp());
		assertTrue(low.propulsiveEfficiencyEta() > 0.0);
		assertTrue(mid.propulsiveEfficiencyEta() > low.propulsiveEfficiencyEta());
		assertTrue(high.propulsiveEfficiencyEta() < mid.propulsiveEfficiencyEta());
		assertDimensionalClosure(low);
		assertDimensionalClosure(mid);
		assertDimensionalClosure(high);
	}

	@Test
	void windmillOrNegativeTotalThrustQueryIsExplicitlyBlocked() {
		RotorAxialBladeElementModel.AxialSample sample = solve(0.80);

		assertEquals(RotorAxialBladeElementModel.Status.BLOCKED_NON_POSITIVE_THRUST,
				sample.status());
		assertTrue(sample.blocked());
		assertEquals(0.0, sample.thrustNewtons(), 0.0);
		assertEquals(0.0, sample.shaftPowerWatts(), 0.0);
		assertEquals(0.0, sample.propulsiveEfficiencyEta(), 0.0);
		assertTrue(sample.bladeElementSample().thrustNewtons() < 0.0);
	}

	private static RotorAxialBladeElementModel.AxialSample solve(double advanceRatio) {
		return RotorAxialBladeElementModel.solve(
				RotorAxialBladeElementModel.AxialQuery.atAdvanceRatio(
						UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade(),
						DIAMETER * 0.5,
						RHO,
						DYNAMIC_VISCOSITY_PASCAL_SECONDS,
						OMEGA,
						advanceRatio
				)
		);
	}

	private static void assertDimensionalClosure(RotorAxialBladeElementModel.AxialSample sample) {
		double revolutionsPerSecond = RPM / 60.0;
		assertTrue(Double.isFinite(sample.thrustNewtons()) && sample.thrustNewtons() > 0.0);
		assertTrue(Double.isFinite(sample.shaftPowerWatts()) && sample.shaftPowerWatts() > 0.0);
		assertTrue(Double.isFinite(sample.shaftTorqueNewtonMeters())
				&& sample.shaftTorqueNewtonMeters() > 0.0);
		assertEquals(
				sample.thrustCoefficientCt()
						* RHO
						* revolutionsPerSecond
						* revolutionsPerSecond
						* Math.pow(DIAMETER, 4.0),
				sample.thrustNewtons(),
				1.0e-12
		);
		assertEquals(
				sample.powerCoefficientCp()
						* RHO
						* Math.pow(revolutionsPerSecond, 3.0)
						* Math.pow(DIAMETER, 5.0),
				sample.shaftPowerWatts(),
				1.0e-12
		);
		assertEquals(sample.shaftPowerWatts(), sample.shaftTorqueNewtonMeters() * OMEGA,
				1.0e-12);
		assertEquals(
				sample.thrustCoefficientCt() * sample.advanceRatioJ()
						/ sample.powerCoefficientCp(),
				sample.propulsiveEfficiencyEta(),
				1.0e-12
		);
		double diskArea = Math.PI * DIAMETER * DIAMETER * 0.25;
		double inducedVelocity = sample.idealInducedVelocityMetersPerSecond();
		assertEquals(sample.thrustNewtons() / diskArea,
				sample.diskLoadingNewtonsPerSquareMeter(), 1.0e-12);
		assertEquals(
				2.0
						* RHO
						* diskArea
						* inducedVelocity
						* sample.diskAxialVelocityMetersPerSecond(),
				sample.thrustNewtons(),
				1.0e-12
		);
		assertEquals(sample.thrustNewtons() * inducedVelocity,
				sample.idealInducedPowerWatts(), 1.0e-12);
		assertEquals(sample.usefulPropulsivePowerWatts() + sample.idealInducedPowerWatts(),
				sample.idealActuatorDiskPowerWatts(), 1.0e-12);
		assertEquals(sample.idealActuatorDiskPowerWatts() / sample.shaftPowerWatts(),
				sample.idealMomentumPowerOverShaftPower(), 1.0e-12);
		assertEquals(0.0, sample.bladeElementSample().thrustClosureResidualNewtons(), 1.0e-10);
	}
}
