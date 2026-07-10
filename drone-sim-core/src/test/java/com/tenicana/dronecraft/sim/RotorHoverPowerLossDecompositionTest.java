package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RotorHoverPowerLossDecompositionTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.81e-5;
	private static final double RPM = 5_943.333;

	@Test
	void referenceConditionedPowerAndTorquePartitionsCloseExactly() {
		UiucDa4002StaticPerformanceLookup.DimensionalSample reference = reference(
				UiucDa4002StaticPerformanceLookup.nineBySixPointSevenFive(),
				RPM
		);
		RotorHoverBladeElementModel.HoverSample bladeElement = bladeElement(
				UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade(),
				UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS,
				RPM
		);
		RotorHoverPowerLossDecomposition.PowerLossSample sample =
				RotorHoverPowerLossDecomposition.decompose(reference, bladeElement);

		assertTrue(sample.rawModelUnderpredictsPower());
		assertTrue(sample.conditionedModelUnderpredictsPower());
		assertEquals(sample.referenceIdealInducedPowerWatts()
					* sample.bladeElementInducedPowerFactor(),
				sample.referenceConditionedLiftInducedPowerWatts(), 1.0e-12);
		assertEquals(sample.referenceConditionedLiftInducedPowerWatts()
					+ sample.requiredNonLiftPowerWatts(),
				sample.measuredShaftPowerWatts(), 1.0e-12);
		assertEquals(sample.referenceConditionedLiftInducedPowerWatts()
					+ sample.bladeElementProfilePowerWatts(),
				sample.referenceConditionedModeledPowerWatts(), 1.0e-12);
		assertEquals(sample.requiredNonLiftPowerWatts()
					- sample.bladeElementProfilePowerWatts(),
				sample.conditionedUnresolvedPowerWatts(), 1.0e-12);
		assertEquals(0.0, sample.requiredPowerClosureResidualWatts(), 1.0e-12);
		assertEquals(0.0, sample.requiredTorqueClosureResidualNewtonMeters(), 1.0e-15);
		assertEquals(reference.lookup().powerCoefficientCp(),
				sample.referenceConditionedLiftInducedPowerCoefficientCp()
						+ sample.requiredNonLiftPowerCoefficientCp(), 1.0e-15);
		assertEquals(sample.conditionedUnresolvedPowerCoefficientCp(),
				sample.requiredNonLiftPowerCoefficientCp()
						- sample.bladeElementProfilePowerCoefficientCp(), 1.0e-15);
		assertTrue(sample.effectiveProfilePowerScaleAvailable());
		assertTrue(sample.effectiveProfilePowerScaleIfAllNonLiftLossWereProfile() > 2.0);
		assertTrue(sample.effectiveProfilePowerScaleIfAllNonLiftLossWereProfile() < 2.2);
		assertTrue(sample.conditionedUnresolvedPowerFraction() > 0.15);
		assertTrue(sample.conditionedUnresolvedPowerFraction() < 0.20);
	}

	@Test
	void decompositionRejectsDifferentReferenceAndBladeGeometry() {
		UiucDa4002StaticPerformanceLookup.DimensionalSample fiveInchReference = reference(
				UiucDa4002StaticPerformanceLookup.fiveByThreePointSevenFive(),
				3_986.667
		);
		RotorHoverBladeElementModel.HoverSample nineInchBladeElement = bladeElement(
				UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade(),
				UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS,
				3_986.667
		);

		assertThrows(IllegalArgumentException.class, () ->
				RotorHoverPowerLossDecomposition.decompose(
						fiveInchReference,
						nineInchBladeElement
				));
	}

	private static UiucDa4002StaticPerformanceLookup.DimensionalSample reference(
			UiucDa4002StaticPerformanceLookup.StaticCurve curve,
			double rpm
	) {
		return UiucDa4002StaticPerformanceLookup.sample(
				curve,
				rpm,
				RHO,
				DYNAMIC_VISCOSITY_PASCAL_SECONDS,
				UiucDa4002StaticPerformanceLookup.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
		);
	}

	private static RotorHoverBladeElementModel.HoverSample bladeElement(
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
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE,
				RotorHoverBladeElementModel.WakeRotationPolicy
						.COUPLED_LIFT_TORQUE_ANGULAR_MOMENTUM
		));
	}
}
