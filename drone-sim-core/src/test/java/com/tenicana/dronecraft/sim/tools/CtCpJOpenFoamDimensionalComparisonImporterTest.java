package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJOpenFoamValidationPlan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CtCpJOpenFoamDimensionalComparisonImporterTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void apDroneMidDomainOpenFoamRowClosesAgainstCtCpJReference() {
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample reference =
				referenceSample("mid_domain_mid_rpm");
		String csv = String.join("\n",
				"preset,case,cfd_thrust_n,cfd_shaft_power_w,cfd_induced_velocity_mps,cfd_momentum_power_w"
						+ ",cfd_wake_swirl_kinetic_power_w,cfd_total_wake_kinetic_power_w"
						+ ",cfd_far_wake_contracted_area_m2,cfd_far_wake_equivalent_radius_m"
						+ ",cfd_far_wake_contracted_area_over_disk_area"
						+ ",cfd_far_wake_equivalent_radius_over_rotor_radius,source_case_sha256,solver_status",
				row(
						"apDrone",
						"mid_domain_mid_rpm",
						reference.thrustNewtons(),
						reference.shaftPowerWatts(),
						reference.idealInducedVelocityMetersPerSecond(),
						reference.idealMomentumPowerWatts(),
						reference.wakeSwirlKineticPowerWatts(),
						reference.totalWakeKineticPowerWatts(),
						reference.farWakeContractedAreaSquareMeters(),
						reference.farWakeEquivalentRadiusMeters(),
						reference.farWakeContractedAreaOverDiskArea(),
						reference.farWakeEquivalentRadiusOverRotorRadius(),
						"synthetic-reviewed-openfoam-mid",
						"CONVERGED"
				));

		List<CtCpJOpenFoamDimensionalComparisonImporter.ComparisonRow> rows =
				CtCpJOpenFoamDimensionalComparisonImporter.compare(csv, RHO);
		CtCpJOpenFoamDimensionalComparisonImporter.ComparisonRow comparison = rows.get(0);

		assertEquals(1, rows.size());
		assertTrue(comparison.comparable());
		assertEquals("ct-cp-j-openfoam-dimensional-comparison-ready", comparison.message());
		assertEquals(reference.lookup().thrustCoefficientCt(), comparison.cfdThrustCoefficientCt(), 1.0e-15);
		assertEquals(reference.lookup().powerCoefficientCp(), comparison.cfdPowerCoefficientCp(), 1.0e-15);
		assertEquals(reference.lookup().propulsiveEfficiencyEta(),
				comparison.cfdPropulsiveEfficiencyEta(), 1.0e-15);
		assertEquals(reference.shaftTorqueNewtonMeters(),
				comparison.cfdTorqueFromPowerNewtonMeters(), 1.0e-17);
		assertEquals(0.0, comparison.cfdPowerTorqueResidualNewtonMeters(), 1.0e-17);
		assertEquals(0.0, comparison.ctResidual(), 1.0e-15);
		assertEquals(0.0, comparison.cpResidual(), 1.0e-15);
		assertEquals(0.0, comparison.etaResidual(), 1.0e-15);
		assertEquals(0.0, comparison.thrustResidualNewtons(), 1.0e-15);
		assertEquals(0.0, comparison.powerResidualWatts(), 1.0e-15);
		assertEquals(0.0, comparison.torqueResidualNewtonMeters(), 1.0e-17);
		assertEquals(0.0, comparison.inducedVelocityResidualMetersPerSecond(), 1.0e-15);
		assertEquals(0.0, comparison.momentumPowerResidualWatts(), 1.0e-15);
		assertEquals(0.0, comparison.wakeSwirlKineticPowerResidualWatts(), 1.0e-15);
		assertEquals(0.0, comparison.wakeSwirlKineticPowerResidualFraction(), 1.0e-15);
		assertEquals(0.0, comparison.totalWakeKineticPowerResidualWatts(), 1.0e-15);
		assertEquals(0.0, comparison.totalWakeKineticPowerResidualFraction(), 1.0e-15);
		assertEquals(0.0, comparison.totalWakeKineticPowerOverShaftPowerResidual(), 1.0e-15);
		assertEquals(0.0, comparison.farWakeContractedAreaResidualSquareMeters(), 1.0e-18);
		assertEquals(0.0, comparison.farWakeContractedAreaResidualFraction(), 1.0e-15);
		assertEquals(0.0, comparison.farWakeEquivalentRadiusResidualMeters(), 1.0e-18);
		assertEquals(0.0, comparison.farWakeEquivalentRadiusResidualFraction(), 1.0e-15);
		assertEquals(0.0, comparison.farWakeContractedAreaRatioResidual(), 1.0e-15);
		assertEquals(0.0, comparison.farWakeEquivalentRadiusRatioResidual(), 1.0e-15);

		Map<String, String> output = outputRecord(
				CtCpJOpenFoamDimensionalComparisonImporter.csvLines(csv, RHO));
		assertEquals("apDrone", output.get("preset"));
		assertEquals("mid_domain_mid_rpm", output.get("case"));
		assertEquals("INTERPOLATED", output.get("reference_status"));
		assertEquals("false", output.get("reference_blocked"));
		assertEquals("CONVERGED", output.get("cfd_solver_status"));
		assertEquals("true", output.get("comparable"));
		assertEquals("synthetic-reviewed-openfoam-mid", output.get("source_case_sha256"));
		assertEquals(reference.farWakeContractedAreaSquareMeters(),
				Double.parseDouble(output.get("reference_far_wake_contracted_area_m2")), 1.0e-15);
		assertEquals(reference.farWakeContractedAreaOverDiskArea(),
				Double.parseDouble(output.get("cfd_far_wake_contracted_area_over_disk_area")), 1.0e-15);
		assertEquals(reference.totalWakeKineticPowerWatts(),
				Double.parseDouble(output.get("cfd_total_wake_kinetic_power_w")), 1.0e-13);
		assertEquals(0.0, Double.parseDouble(output.get("total_wake_kinetic_power_over_shaft_power_residual")),
				1.0e-15);
		assertEquals(0.0, Double.parseDouble(output.get("far_wake_equivalent_radius_ratio_residual")),
				1.0e-15);
	}

	@Test
	void perturbedOpenFoamRowReportsSignedDimensionalAndCoefficientResiduals() {
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample reference =
				referenceSample("mid_domain_mid_rpm");
		double thrustScale = 1.05;
		double powerScale = 0.98;
		double torqueScale = 1.01;
		double wakeSwirlScale = 1.03;
		double farWakeAreaScale = 1.04;
		double farWakeRadiusScale = 1.02;
		String csv = String.join("\n",
				"preset,case,cfd_thrust_n,cfd_shaft_power_w,cfd_shaft_torque_nm,cfd_induced_velocity_mps"
						+ ",cfd_momentum_power_w,cfd_wake_swirl_kinetic_power_w"
						+ ",cfd_far_wake_contracted_area_m2,cfd_far_wake_equivalent_radius_m",
				row(
						"apDrone",
						"mid_domain_mid_rpm",
						reference.thrustNewtons() * thrustScale,
						reference.shaftPowerWatts() * powerScale,
						reference.shaftTorqueNewtonMeters() * torqueScale,
						reference.idealInducedVelocityMetersPerSecond(),
						reference.idealMomentumPowerWatts(),
						reference.wakeSwirlKineticPowerWatts() * wakeSwirlScale,
						reference.farWakeContractedAreaSquareMeters() * farWakeAreaScale,
						reference.farWakeEquivalentRadiusMeters() * farWakeRadiusScale
				));

		CtCpJOpenFoamDimensionalComparisonImporter.ComparisonRow comparison =
				CtCpJOpenFoamDimensionalComparisonImporter.compare(csv, RHO).get(0);

		assertTrue(comparison.comparable());
		assertEquals(reference.lookup().thrustCoefficientCt() * (thrustScale - 1.0),
				comparison.ctResidual(), 1.0e-15);
		assertEquals(thrustScale - 1.0, comparison.ctResidualFraction(), 1.0e-15);
		assertEquals(reference.lookup().powerCoefficientCp() * (powerScale - 1.0),
				comparison.cpResidual(), 1.0e-15);
		assertEquals(powerScale - 1.0, comparison.cpResidualFraction(), 1.0e-15);
		assertEquals(reference.thrustNewtons() * (thrustScale - 1.0),
				comparison.thrustResidualNewtons(), 1.0e-15);
		assertEquals(thrustScale - 1.0, comparison.thrustResidualFraction(), 1.0e-15);
		assertEquals(reference.shaftPowerWatts() * (powerScale - 1.0),
				comparison.powerResidualWatts(), 1.0e-15);
		assertEquals(powerScale - 1.0, comparison.powerResidualFraction(), 1.0e-15);
		assertEquals(reference.shaftTorqueNewtonMeters() * (torqueScale - 1.0),
				comparison.torqueResidualNewtonMeters(), 1.0e-17);
		assertEquals(torqueScale - 1.0, comparison.torqueResidualFraction(), 1.0e-15);
		assertTrue(comparison.cfdPowerTorqueResidualNewtonMeters() > 0.0);
		assertEquals(0.0, comparison.inducedVelocityResidualMetersPerSecond(), 1.0e-15);
		assertEquals(reference.wakeSwirlKineticPowerWatts() * (wakeSwirlScale - 1.0),
				comparison.wakeSwirlKineticPowerResidualWatts(), 1.0e-15);
		assertEquals(wakeSwirlScale - 1.0, comparison.wakeSwirlKineticPowerResidualFraction(),
				1.0e-15);
		assertEquals(reference.wakeSwirlKineticPowerWatts() * (wakeSwirlScale - 1.0),
				comparison.totalWakeKineticPowerResidualWatts(), 1.0e-15);
		assertEquals(comparison.totalWakeKineticPowerResidualWatts()
						/ reference.totalWakeKineticPowerWatts(),
				comparison.totalWakeKineticPowerResidualFraction(), 1.0e-15);
		double expectedTotalWakeOverShaftResidual =
				comparison.cfd().cfdTotalWakeKineticPowerWatts()
						/ comparison.cfd().cfdShaftPowerWatts()
						- reference.totalWakeKineticPowerOverShaftPower();
		assertEquals(expectedTotalWakeOverShaftResidual,
				comparison.totalWakeKineticPowerOverShaftPowerResidual(), 1.0e-15);
		assertEquals(reference.farWakeContractedAreaSquareMeters() * (farWakeAreaScale - 1.0),
				comparison.farWakeContractedAreaResidualSquareMeters(), 1.0e-18);
		assertEquals(farWakeAreaScale - 1.0, comparison.farWakeContractedAreaResidualFraction(),
				1.0e-15);
		assertEquals(reference.farWakeEquivalentRadiusMeters() * (farWakeRadiusScale - 1.0),
				comparison.farWakeEquivalentRadiusResidualMeters(), 1.0e-17);
		assertEquals(farWakeRadiusScale - 1.0, comparison.farWakeEquivalentRadiusResidualFraction(),
				1.0e-15);
		assertEquals(reference.farWakeContractedAreaOverDiskArea() * (farWakeAreaScale - 1.0),
				comparison.farWakeContractedAreaRatioResidual(), 1.0e-15);
		assertEquals(reference.farWakeEquivalentRadiusOverRotorRadius() * (farWakeRadiusScale - 1.0),
				comparison.farWakeEquivalentRadiusRatioResidual(), 1.0e-15);
	}

	@Test
	void unsupportedPresetStaysNonComparableInsteadOfBorrowingApDroneReference() {
		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target =
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow(
						"racingQuad",
						"mid_domain_mid_rpm"
				);
		double diameter = DroneConfig.racingQuad().rotors().get(0).radiusMeters() * 2.0;
		String csv = String.join("\n",
				"preset,case,query_j,query_rpm,diameter_m,cfd_thrust_n,cfd_shaft_power_w,cfd_shaft_torque_nm",
				row(
						"racingQuad",
						"mid_domain_mid_rpm",
						target.queryAdvanceRatioJ(),
						target.queryRpm(),
						diameter,
						1.0,
						4.0,
						0.01
				));

		CtCpJOpenFoamDimensionalComparisonImporter.ComparisonRow comparison =
				CtCpJOpenFoamDimensionalComparisonImporter.compare(csv, RHO).get(0);

		assertFalse(comparison.comparable());
		assertTrue(comparison.reference().blocked());
		assertEquals("REFERENCE_WINDOW_UNAVAILABLE", comparison.reference().lookup().status());
		assertEquals("no-accepted-reference-window-brackets-query", comparison.message());
		assertEquals(0.0, comparison.reference().thrustNewtons(), 1.0e-15);
		assertTrue(Double.isFinite(comparison.cfdThrustCoefficientCt()));
		assertTrue(Double.isFinite(comparison.cfdPowerCoefficientCp()));
	}

	private static PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample referenceSample(String caseName) {
		double diameter = DroneConfig.apDrone().rotors().get(0).radiusMeters() * 2.0;
		return PropellerArchiveCtCpJLookupEvaluator.sampleRotor(
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						caseName,
						diameter,
						RHO
				));
	}

	private static String row(Object... values) {
		List<String> cells = List.of(values).stream()
				.map(value -> value instanceof Number number
						? String.format(Locale.ROOT, "%.17g", number.doubleValue())
						: String.valueOf(value))
				.toList();
		return String.join(",", cells);
	}

	private static Map<String, String> outputRecord(List<String> lines) {
		String[] header = lines.get(0).split(",", -1);
		String[] cells = lines.get(1).split(",", -1);
		Map<String, String> values = new LinkedHashMap<>();
		for (int i = 0; i < header.length; i++) {
			values.put(header[i], cells[i]);
		}
		return values;
	}
}
