package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.MotorBenchCurrentModel;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJRotorForceModel;
import com.tenicana.dronecraft.sim.RotorStaticCtCpModel;
import com.tenicana.dronecraft.sim.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CtCpJCurveExporterTest {
	@Test
	void csvLinesExportAcceptedReferenceDimensionalCurve() {
		double diameter = DroneConfig.apDrone().rotors().get(0).radiusMeters() * 2.0;
		List<String> lines = CtCpJCurveExporter.csvLines(
				"apDrone",
				PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER,
				diameter
		);

		assertEquals(45, lines.size());
		assertTrue(lines.get(0).startsWith("preset,case,query_j,query_rpm,effective_j,effective_rpm"));
		assertTrue(lines.get(0).endsWith(
				",source_id,lookup_status,lookup_message,runtime_force_replacement_accepted,query_signed_axial_speed_mps,relative_air_body_x_mps,relative_air_body_y_mps,relative_air_body_z_mps,transverse_air_body_x_mps,transverse_air_body_y_mps,transverse_air_body_z_mps,transverse_air_speed_mps,inflow_angle_deg,thrust_force_body_x_n,thrust_force_body_y_n,thrust_force_body_z_n,reaction_torque_body_x_nm,reaction_torque_body_y_nm,reaction_torque_body_z_nm,thrust_moment_body_x_nm,thrust_moment_body_y_nm,thrust_moment_body_z_nm,total_torque_body_x_nm,total_torque_body_y_nm,total_torque_body_z_nm,momentum_power_closure_satisfied,runtime_eligibility_status,shaft_power_residual_w,shaft_power_residual_fraction,operating_point_temperature_c,operating_point_humidity,operating_point_dynamic_viscosity_pa_s,operating_point_speed_of_sound_mps,rotational_tip_speed_mps,helical_tip_speed_mps,tip_mach,representative_blade_station_speed_mps,representative_blade_chord_m,reynolds_number,reynolds_index,tip_mach_runtime_margin,reynolds_index_runtime_margin,operating_envelope_margin_fraction,disk_mass_flow_kg_s,far_wake_axial_velocity_mps,far_wake_contracted_area_m2,far_wake_equivalent_radius_m,angular_momentum_swirl_radius_m,wake_tangential_velocity_mps,wake_swirl_kinetic_power_w,total_wake_kinetic_power_w,total_wake_kinetic_power_over_shaft_power,wake_swirl_kinetic_power_over_shaft_power,total_wake_kinetic_power_residual_w,total_wake_kinetic_power_residual_fraction,torque_coefficient_cq,useful_axial_thrust_power_w,ideal_induced_power_w,axial_propulsive_efficiency,far_wake_contracted_area_over_disk_area,far_wake_equivalent_radius_over_rotor_radius,wake_angular_momentum_torque_nm,wake_angular_momentum_torque_residual_nm,wake_angular_momentum_torque_residual_fraction,wake_angular_momentum_torque_body_x_nm,wake_angular_momentum_torque_body_y_nm,wake_angular_momentum_torque_body_z_nm,wake_angular_momentum_torque_residual_body_x_nm,wake_angular_momentum_torque_residual_body_y_nm,wake_angular_momentum_torque_residual_body_z_nm"));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchor_low_rpm,0.00000000000000,1477.80000000000")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("apDrone,mid_domain_mid_rpm,0.406400000000000,4712.25000000000")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("apDrone,high_domain_max_rpm,0.731520000000000,7946.70000000000")));
		assertFalse(lines.stream()
				.skip(1)
				.filter(line -> !line.contains("static_anchored_runtime_reverse_axial_clamp"))
				.filter(line -> !line.contains("static_anchored_runtime_high_j_block"))
				.map(line -> line.split(",", -1))
				.anyMatch(cells -> "true".equals(cells[7]) || "true".equals(cells[8])
						|| "BLOCKED".equals(cells[6])));

		double midThrust = numericCell(lines, "mid_domain_mid_rpm",
				"0.406400000000000", "4712.25000000000", 13);
		double highThrust = numericCell(lines, "high_domain_max_rpm", "0.731520000000000", 13);
		double midPower = numericCell(lines, "mid_domain_mid_rpm",
				"0.406400000000000", "4712.25000000000", 14);
		double highPower = numericCell(lines, "high_domain_max_rpm", "0.731520000000000", 14);
		String midLine = lineForCaseAndQuery(lines, "mid_domain_mid_rpm",
				"0.406400000000000", "4712.25000000000");
		String[] midCells = midLine.split(",", -1);
		String highLine = lineForCaseAndQueryJ(lines, "high_domain_max_rpm", "0.731520000000000");
		String[] highCells = highLine.split(",", -1);
		assertTrue(midThrust > 0.0);
		assertTrue(highThrust > midThrust);
		assertTrue(highPower > midPower);
		assertEquals(0.0, Double.parseDouble(midCells[25]), 1.0e-15);
		assertEquals(Double.parseDouble(midCells[12]), Double.parseDouble(midCells[26]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(midCells[27]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(midCells[28]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(midCells[29]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(midCells[30]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(midCells[31]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(midCells[32]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(midCells[33]), 1.0e-15);
		assertEquals(midThrust, Double.parseDouble(midCells[34]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(midCells[35]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(midCells[36]), 1.0e-18);
		assertEquals(Double.parseDouble(midCells[15]) * DroneConfig.apDrone().rotors().get(0).spinDirection(),
				Double.parseDouble(midCells[37]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(midCells[38]), 1.0e-18);
		Vec3 rotorArm = DroneConfig.apDrone().rotors().get(0).positionBodyMeters()
				.subtract(DroneConfig.apDrone().centerOfMassOffsetBodyMeters());
		Vec3 thrustForce = new Vec3(
				Double.parseDouble(midCells[33]),
				Double.parseDouble(midCells[34]),
				Double.parseDouble(midCells[35]));
		Vec3 reactionTorque = new Vec3(
				Double.parseDouble(midCells[36]),
				Double.parseDouble(midCells[37]),
				Double.parseDouble(midCells[38]));
		Vec3 thrustMoment = rotorArm.cross(thrustForce);
		Vec3 totalTorque = thrustMoment.add(reactionTorque);
		assertEquals(thrustMoment.x(), Double.parseDouble(midCells[39]), 1.0e-15);
		assertEquals(thrustMoment.y(), Double.parseDouble(midCells[40]), 1.0e-15);
		assertEquals(thrustMoment.z(), Double.parseDouble(midCells[41]), 1.0e-15);
		assertEquals(totalTorque.x(), Double.parseDouble(midCells[42]), 1.0e-15);
		assertEquals(totalTorque.y(), Double.parseDouble(midCells[43]), 1.0e-15);
		assertEquals(totalTorque.z(), Double.parseDouble(midCells[44]), 1.0e-15);
		assertEquals("true", midCells[45]);
		assertEquals("NOT_RUNTIME_CANDIDATE", midCells[46]);
		assertEquals(midPower - Double.parseDouble(midCells[18]), Double.parseDouble(midCells[47]), 1.0e-13);
		assertEquals(Double.parseDouble(midCells[47]) / midPower, Double.parseDouble(midCells[48]), 1.0e-13);
		assertTrue(Double.parseDouble(midCells[47]) > 0.0);
		PropellerArchiveCtCpJRotorForceModel.RotorOperatingPoint midOperatingPoint =
				PropellerArchiveCtCpJRotorForceModel.standardOperatingPoint(
						DroneConfig.apDrone().rotors().get(0),
						DroneConfig.apDrone().rotors().get(0).thrustAxisBody()
								.multiply(Double.parseDouble(midCells[12])),
						Double.parseDouble(midCells[5]) * 2.0 * Math.PI / 60.0,
						PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER);
		assertEquals(midOperatingPoint.ambientTemperatureCelsius(), Double.parseDouble(midCells[49]), 1.0e-12);
		assertEquals(midOperatingPoint.ambientHumidity(), Double.parseDouble(midCells[50]), 1.0e-15);
		assertEquals(midOperatingPoint.dynamicViscosityPascalSeconds(), Double.parseDouble(midCells[51]), 1.0e-18);
		assertEquals(midOperatingPoint.speedOfSoundMetersPerSecond(), Double.parseDouble(midCells[52]), 1.0e-12);
		assertEquals(midOperatingPoint.rotationalTipSpeedMetersPerSecond(),
				Double.parseDouble(midCells[53]), 1.0e-13);
		assertEquals(midOperatingPoint.helicalTipSpeedMetersPerSecond(),
				Double.parseDouble(midCells[54]), 1.0e-13);
		assertEquals(midOperatingPoint.tipMach(), Double.parseDouble(midCells[55]), 1.0e-15);
		assertEquals(midOperatingPoint.representativeBladeStationSpeedMetersPerSecond(),
				Double.parseDouble(midCells[56]), 1.0e-13);
		assertEquals(midOperatingPoint.representativeBladeChordMeters(), Double.parseDouble(midCells[57]), 1.0e-15);
		assertEquals(midOperatingPoint.reynoldsNumber(), Double.parseDouble(midCells[58]), 1.0e-8);
		assertEquals(midOperatingPoint.reynoldsIndex(), Double.parseDouble(midCells[59]), 1.0e-13);
		assertEquals(midOperatingPoint.runtimeTipMachMargin(), Double.parseDouble(midCells[60]), 1.0e-15);
		assertEquals(midOperatingPoint.runtimeReynoldsIndexMargin(), Double.parseDouble(midCells[61]), 1.0e-13);
		assertEquals(midOperatingPoint.runtimeOperatingEnvelopeMarginFraction(),
				Double.parseDouble(midCells[62]), 1.0e-13);
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample midDimensional =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(
						PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
								"apDrone",
								"mid_domain_mid_rpm",
								DroneConfig.apDrone().rotors().get(0).radiusMeters() * 2.0,
								PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER));
		assertEquals(midDimensional.diskMassFlowKilogramsPerSecond(), Double.parseDouble(midCells[63]), 1.0e-14);
		assertEquals(midDimensional.farWakeAxialVelocityMetersPerSecond(), Double.parseDouble(midCells[64]), 1.0e-13);
		assertEquals(midDimensional.farWakeContractedAreaSquareMeters(), Double.parseDouble(midCells[65]), 1.0e-16);
		assertEquals(midDimensional.farWakeEquivalentRadiusMeters(), Double.parseDouble(midCells[66]), 1.0e-16);
		assertEquals(midDimensional.angularMomentumSwirlRadiusMeters(), Double.parseDouble(midCells[67]), 1.0e-16);
		assertEquals(midDimensional.wakeTangentialVelocityMetersPerSecond(), Double.parseDouble(midCells[68]), 1.0e-13);
		assertEquals(midDimensional.wakeSwirlKineticPowerWatts(), Double.parseDouble(midCells[69]), 1.0e-14);
		assertEquals(midDimensional.totalWakeKineticPowerWatts(), Double.parseDouble(midCells[70]), 1.0e-14);
		assertEquals(midDimensional.totalWakeKineticPowerOverShaftPower(),
				Double.parseDouble(midCells[71]), 1.0e-14);
		assertEquals(midDimensional.wakeSwirlKineticPowerOverShaftPower(),
				Double.parseDouble(midCells[72]), 1.0e-14);
		assertEquals(midDimensional.totalWakeKineticPowerResidualWatts(),
				Double.parseDouble(midCells[73]), 1.0e-14);
		assertEquals(midDimensional.totalWakeKineticPowerResidualFraction(),
				Double.parseDouble(midCells[74]), 1.0e-14);
		assertEquals(midDimensional.torqueCoefficientCq(), Double.parseDouble(midCells[75]), 5.0e-18);
		assertEquals(midDimensional.shaftTorqueNewtonMeters(), torqueFromCq(midDimensional, midCells), 1.0e-17);
		assertEquals(midDimensional.usefulAxialThrustPowerWatts(), Double.parseDouble(midCells[76]), 1.0e-14);
		assertEquals(midDimensional.idealInducedPowerWatts(), Double.parseDouble(midCells[77]), 1.0e-14);
		assertEquals(midDimensional.axialPropulsiveEfficiency(), Double.parseDouble(midCells[78]), 1.0e-14);
		assertEquals(Double.parseDouble(midCells[11]), Double.parseDouble(midCells[78]), 1.0e-14);
		assertEquals(midDimensional.farWakeContractedAreaOverDiskArea(),
				Double.parseDouble(midCells[79]), 1.0e-14);
		assertEquals(midDimensional.farWakeEquivalentRadiusOverRotorRadius(),
				Double.parseDouble(midCells[80]), 1.0e-14);
		assertEquals(midDimensional.wakeAngularMomentumTorqueNewtonMeters(),
				Double.parseDouble(midCells[81]), 1.0e-17);
		assertEquals(midDimensional.wakeAngularMomentumTorqueResidualNewtonMeters(),
				Double.parseDouble(midCells[82]), 1.0e-17);
		assertEquals(midDimensional.wakeAngularMomentumTorqueResidualFraction(),
				Double.parseDouble(midCells[83]), 1.0e-15);
		assertEquals(midDimensional.shaftTorqueNewtonMeters(), Double.parseDouble(midCells[81]), 1.0e-17);
		assertEquals(0.0, Double.parseDouble(midCells[82]), 1.0e-17);
		assertEquals(0.0, Double.parseDouble(midCells[83]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(midCells[84]), 1.0e-18);
		assertEquals(Double.parseDouble(midCells[37]), Double.parseDouble(midCells[85]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(midCells[86]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(midCells[87]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(midCells[88]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(midCells[89]), 1.0e-18);
		assertEquals(Double.parseDouble(midCells[76]) + Double.parseDouble(midCells[77]),
				Double.parseDouble(midCells[18]), 1.0e-14);
		assertEquals("false", highCells[45]);
		assertEquals("MOMENTUM_POWER_CLOSURE_FAILED", highCells[46]);
		assertTrue(Double.parseDouble(highCells[47]) < 0.0);
		assertTrue(Double.parseDouble(highCells[48]) < 0.0);
		assertTrue(Double.parseDouble(highCells[73]) < Double.parseDouble(highCells[47]));
		assertTrue(Double.parseDouble(highCells[75]) > Double.parseDouble(midCells[75]));
		assertTrue(Double.parseDouble(highCells[78]) > 1.0);

		String foxeerStatic = lineForCase(lines, "static_rotor_spec_foxeer_public_test");
		assertEquals(RotorStaticCtCpModel.SOURCE_ID, foxeerStatic.split(",", -1)[20]);
		assertEquals(0.159299848814191, Double.parseDouble(foxeerStatic.split(",", -1)[9]), 1.0e-15);
		assertEquals(MotorBenchCurrentModel.FOXEER_DONUT_5145_PUBLIC_TEST_THRUST_NEWTONS,
				Double.parseDouble(foxeerStatic.split(",", -1)[13]), 1.0e-12);
		assertEquals("true", foxeerStatic.split(",", -1)[45]);
		assertEquals("NOT_RUNTIME_CANDIDATE", foxeerStatic.split(",", -1)[46]);
		assertTrue(Double.parseDouble(foxeerStatic.split(",", -1)[47]) > 0.0);
		assertEquals(0.5, Double.parseDouble(foxeerStatic.split(",", -1)[79]), 1.0e-15);
		assertEquals(Math.sqrt(0.5), Double.parseDouble(foxeerStatic.split(",", -1)[80]), 1.0e-15);
		assertEquals(Double.parseDouble(foxeerStatic.split(",", -1)[15]),
				Double.parseDouble(foxeerStatic.split(",", -1)[81]), 1.0e-17);
		assertEquals(0.0, Double.parseDouble(foxeerStatic.split(",", -1)[82]), 1.0e-17);
		assertEquals(0.0, Double.parseDouble(foxeerStatic.split(",", -1)[83]), 1.0e-15);

		String staticHover = lineForCase(lines, "static_rotor_spec_hover");
		String runtimeHoverStatic = lineForCaseAndQueryJ(lines,
				"static_anchored_runtime_hover",
				"0.00000000000000");
		String runtimeHoverMidJ = lineForCaseAndQueryJ(lines,
				"static_anchored_runtime_hover",
				"0.406400000000000");
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.STATIC_ANCHORED_DATA_SOURCE_ID,
				runtimeHoverMidJ.split(",", -1)[20]);
		assertEquals(Double.parseDouble(staticHover.split(",", -1)[9]),
				Double.parseDouble(runtimeHoverStatic.split(",", -1)[9]), 1.0e-15);
		assertEquals(Double.parseDouble(staticHover.split(",", -1)[10]),
				Double.parseDouble(runtimeHoverStatic.split(",", -1)[10]), 1.0e-15);
		assertEquals(Double.parseDouble(staticHover.split(",", -1)[13]),
				Double.parseDouble(runtimeHoverStatic.split(",", -1)[13]), 1.0e-12);
		assertEquals("ACCEPTED", runtimeHoverStatic.split(",", -1)[46]);
		assertTrue(Double.parseDouble(runtimeHoverMidJ.split(",", -1)[13])
				< Double.parseDouble(runtimeHoverStatic.split(",", -1)[13]));
		assertTrue(Double.parseDouble(runtimeHoverMidJ.split(",", -1)[14])
				> Double.parseDouble(runtimeHoverStatic.split(",", -1)[14]));
		assertEquals("ACCEPTED", runtimeHoverMidJ.split(",", -1)[46]);

		String transverseDiagnostic = lineForCase(lines,
				"static_anchored_runtime_transverse_inflow_diagnostic");
		String[] transverseCells = transverseDiagnostic.split(",", -1);
		assertEquals("EXACT", transverseCells[6]);
		assertEquals("false", transverseCells[7]);
		assertEquals("false", transverseCells[8]);
		assertEquals("false", transverseCells[23]);
		assertEquals("true", transverseCells[45]);
		assertEquals("OBLIQUE_INFLOW_OUTSIDE_RUNTIME_ENVELOPE", transverseCells[46]);
		assertEquals("0.406400000000000", transverseCells[2]);
		assertEquals(Double.parseDouble(runtimeHoverMidJ.split(",", -1)[9]),
				Double.parseDouble(transverseCells[9]), 1.0e-15);
		assertEquals(Double.parseDouble(runtimeHoverMidJ.split(",", -1)[10]),
				Double.parseDouble(transverseCells[10]), 1.0e-15);
		assertEquals(Double.parseDouble(runtimeHoverMidJ.split(",", -1)[13]),
				Double.parseDouble(transverseCells[13]), 1.0e-12);
		assertEquals(Double.parseDouble(transverseCells[24]), Double.parseDouble(transverseCells[12]), 1.0e-12);
		assertEquals(2.5, Double.parseDouble(transverseCells[25]), 1.0e-15);
		assertEquals(Double.parseDouble(transverseCells[12]), Double.parseDouble(transverseCells[26]), 1.0e-12);
		assertEquals(0.0, Double.parseDouble(transverseCells[27]), 1.0e-15);
		assertEquals(2.5, Double.parseDouble(transverseCells[28]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(transverseCells[29]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(transverseCells[30]), 1.0e-15);
		assertEquals(2.5, Double.parseDouble(transverseCells[31]), 1.0e-15);
		assertEquals(
				Math.toDegrees(Math.atan2(2.5, Double.parseDouble(transverseCells[12]))),
				Double.parseDouble(transverseCells[32]),
				1.0e-12
		);
		assertEquals(Double.parseDouble(runtimeHoverMidJ.split(",", -1)[34]),
				Double.parseDouble(transverseCells[34]), 1.0e-12);

		String reverseClamp = lineForCase(lines, "static_anchored_runtime_reverse_axial_clamp");
		String[] reverseCells = reverseClamp.split(",", -1);
		assertEquals("CLAMPED_EXACT", reverseCells[6]);
		assertEquals("true", reverseCells[7]);
		assertEquals("false", reverseCells[8]);
		assertEquals("CLAMPED", reverseCells[21]);
		assertEquals("reverse-axial-flow-clamped-to-static-anchor", reverseCells[22]);
		assertEquals("false", reverseCells[23]);
		assertEquals("true", reverseCells[45]);
		assertEquals("CLAMPED", reverseCells[46]);
		assertTrue(Double.parseDouble(reverseCells[24]) < 0.0);
		assertEquals(Double.parseDouble(reverseCells[24]), Double.parseDouble(reverseCells[26]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(reverseCells[31]), 1.0e-15);
		assertEquals(180.0, Double.parseDouble(reverseCells[32]), 1.0e-12);
		assertEquals(0.0, Double.parseDouble(reverseCells[12]), 1.0e-15);
		assertTrue(Double.parseDouble(reverseCells[13]) > 0.0);
		assertEquals(Double.parseDouble(reverseCells[13]), Double.parseDouble(reverseCells[34]), 1.0e-15);
		assertEquals(Double.parseDouble(reverseCells[15]) * DroneConfig.apDrone().rotors().get(0).spinDirection(),
				Double.parseDouble(reverseCells[37]), 1.0e-18);

		String blockedHighJ = lineForCase(lines, "static_anchored_runtime_high_j_block");
		String[] blockedCells = blockedHighJ.split(",", -1);
		assertEquals("BLOCKED", blockedCells[6]);
		assertEquals("false", blockedCells[7]);
		assertEquals("true", blockedCells[8]);
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", blockedCells[21]);
		assertEquals("query-outside-accepted-advance-shape-window", blockedCells[22]);
		assertEquals("false", blockedCells[23]);
		assertEquals("false", blockedCells[45]);
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", blockedCells[46]);
		assertTrue(Double.parseDouble(blockedCells[24]) > 0.0);
		assertEquals(Double.parseDouble(blockedCells[24]), Double.parseDouble(blockedCells[26]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[31]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[32]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[13]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[14]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[33]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[34]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[35]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[36]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(blockedCells[37]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(blockedCells[38]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(blockedCells[39]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(blockedCells[40]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(blockedCells[41]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(blockedCells[42]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(blockedCells[43]), 1.0e-18);
		assertEquals(0.0, Double.parseDouble(blockedCells[44]), 1.0e-18);
		assertTrue(Double.parseDouble(blockedCells[53]) > 0.0);
		assertTrue(Double.parseDouble(blockedCells[54]) > Double.parseDouble(blockedCells[53]));
		assertTrue(Double.parseDouble(blockedCells[55]) > 0.0);
		assertTrue(Double.parseDouble(blockedCells[58]) > 0.0);
		assertTrue(Double.isFinite(Double.parseDouble(blockedCells[60])));
		assertTrue(Double.isFinite(Double.parseDouble(blockedCells[61])));
		assertTrue(Double.isFinite(Double.parseDouble(blockedCells[62])));
		assertEquals(0.0, Double.parseDouble(blockedCells[63]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[70]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[75]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[76]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[77]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[78]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[81]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[82]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[83]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[84]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[85]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[86]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[87]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[88]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[89]), 1.0e-15);
	}

	@Test
	void csvLinesCanExportHotHumidOperatingPointEnvelope() {
		double airDensity =
				PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
		double diameter = DroneConfig.apDrone().rotors().get(0).radiusMeters() * 2.0;
		List<String> standard = CtCpJCurveExporter.csvLines("apDrone", airDensity, diameter);
		List<String> hotHumid = CtCpJCurveExporter.csvLines("apDrone", airDensity, diameter, 55.0, 1.0);

		assertEquals(standard.size(), hotHumid.size());
		String[] standardMidCells = lineForCaseAndQuery(standard, "mid_domain_mid_rpm",
				"0.406400000000000", "4712.25000000000").split(",", -1);
		String[] hotHumidMidCells = lineForCaseAndQuery(hotHumid, "mid_domain_mid_rpm",
				"0.406400000000000", "4712.25000000000").split(",", -1);
		assertEquals(standardMidCells[9], hotHumidMidCells[9]);
		assertEquals(standardMidCells[10], hotHumidMidCells[10]);
		assertEquals(standardMidCells[13], hotHumidMidCells[13]);
		assertEquals(standardMidCells[14], hotHumidMidCells[14]);
		assertEquals(55.0, Double.parseDouble(hotHumidMidCells[49]), 1.0e-12);
		assertEquals(1.0, Double.parseDouble(hotHumidMidCells[50]), 1.0e-15);

		PropellerArchiveCtCpJRotorForceModel.RotorOperatingPoint hotHumidOperatingPoint =
				PropellerArchiveCtCpJRotorForceModel.operatingPoint(
						DroneConfig.apDrone().rotors().get(0),
						DroneConfig.apDrone().rotors().get(0).thrustAxisBody()
								.multiply(Double.parseDouble(hotHumidMidCells[12])),
						Double.parseDouble(hotHumidMidCells[5]) * 2.0 * Math.PI / 60.0,
						airDensity,
						55.0,
						1.0);
		assertEquals(hotHumidOperatingPoint.dynamicViscosityPascalSeconds(),
				Double.parseDouble(hotHumidMidCells[51]), 1.0e-18);
		assertEquals(hotHumidOperatingPoint.speedOfSoundMetersPerSecond(),
				Double.parseDouble(hotHumidMidCells[52]), 1.0e-12);
		assertEquals(hotHumidOperatingPoint.tipMach(), Double.parseDouble(hotHumidMidCells[55]), 1.0e-15);
		assertEquals(hotHumidOperatingPoint.reynoldsNumber(),
				Double.parseDouble(hotHumidMidCells[58]), 1.0e-8);
		assertEquals(hotHumidOperatingPoint.reynoldsIndex(),
				Double.parseDouble(hotHumidMidCells[59]), 1.0e-13);
		assertEquals(hotHumidOperatingPoint.runtimeTipMachMargin(),
				Double.parseDouble(hotHumidMidCells[60]), 1.0e-15);
		assertEquals(hotHumidOperatingPoint.runtimeReynoldsIndexMargin(),
				Double.parseDouble(hotHumidMidCells[61]), 1.0e-13);
		assertEquals(hotHumidOperatingPoint.runtimeOperatingEnvelopeMarginFraction(),
				Double.parseDouble(hotHumidMidCells[62]), 1.0e-13);
		assertTrue(Double.parseDouble(hotHumidMidCells[51]) != Double.parseDouble(standardMidCells[51]));
		assertTrue(Double.parseDouble(hotHumidMidCells[52]) != Double.parseDouble(standardMidCells[52]));
		assertTrue(Double.parseDouble(hotHumidMidCells[58]) != Double.parseDouble(standardMidCells[58]));
	}

	@Test
	void csvLinesRuntimeEligibilityUsesExportAmbientEnvelope() {
		double airDensity =
				PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER * 0.28;
		double diameter = DroneConfig.apDrone().rotors().get(0).radiusMeters() * 2.0;
		List<String> standard = CtCpJCurveExporter.csvLines("apDrone", airDensity, diameter);
		List<String> hotDry = CtCpJCurveExporter.csvLines("apDrone", airDensity, diameter, 65.0, 0.0);

		String[] standardHoverCells = lineForCaseAndQueryJ(standard,
				"static_anchored_runtime_hover",
				"0.00000000000000").split(",", -1);
		String[] hotDryHoverCells = lineForCaseAndQueryJ(hotDry,
				"static_anchored_runtime_hover",
				"0.00000000000000").split(",", -1);

		assertEquals("true", standardHoverCells[23]);
		assertEquals("ACCEPTED", standardHoverCells[46]);
		assertTrue(Double.parseDouble(standardHoverCells[59]) >= 0.52);
		assertTrue(Double.parseDouble(standardHoverCells[62]) >= 0.0);
		assertEquals("false", hotDryHoverCells[23]);
		assertEquals("OPERATING_POINT_OUTSIDE_RUNTIME_ENVELOPE", hotDryHoverCells[46]);
		assertEquals(65.0, Double.parseDouble(hotDryHoverCells[49]), 1.0e-12);
		assertEquals(0.0, Double.parseDouble(hotDryHoverCells[50]), 1.0e-15);
		assertTrue(Double.parseDouble(hotDryHoverCells[59]) < 0.52);
		assertTrue(Double.parseDouble(hotDryHoverCells[61]) < 0.0);
		assertTrue(Double.parseDouble(hotDryHoverCells[62]) < 0.0);
		assertEquals(standardHoverCells[13], hotDryHoverCells[13]);
		assertEquals(standardHoverCells[14], hotDryHoverCells[14]);
	}

	@Test
	void writeCreatesParentDirectoriesAndCsvFile(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("nested").resolve("curve.csv");
		double diameter = DroneConfig.apDrone().rotors().get(0).radiusMeters() * 2.0;
		CtCpJCurveExporter.write(
				"apDrone",
				output,
				PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER,
				diameter
		);

		List<String> lines = Files.readAllLines(output);
		assertEquals(45, lines.size());
		assertTrue(lines.get(0).contains("shaft_torque_nm"));
		assertTrue(lines.get(0).contains("source_id"));
		assertTrue(lines.get(0).contains("query_signed_axial_speed_mps"));
		assertTrue(lines.get(0).contains("relative_air_body_y_mps"));
		assertTrue(lines.get(0).contains("transverse_air_speed_mps"));
		assertTrue(lines.get(0).contains("inflow_angle_deg"));
		assertTrue(lines.get(0).contains("thrust_force_body_y_n"));
		assertTrue(lines.get(0).contains("reaction_torque_body_y_nm"));
		assertTrue(lines.get(0).contains("thrust_moment_body_x_nm"));
		assertTrue(lines.get(0).contains("total_torque_body_z_nm"));
		assertTrue(lines.get(0).contains("momentum_power_closure_satisfied"));
		assertTrue(lines.get(0).contains("runtime_eligibility_status"));
		assertTrue(lines.get(0).contains("shaft_power_residual_w"));
		assertTrue(lines.get(0).contains("shaft_power_residual_fraction"));
		assertTrue(lines.get(0).contains("operating_point_humidity"));
		assertTrue(lines.get(0).contains("operating_point_speed_of_sound_mps"));
		assertTrue(lines.get(0).contains("tip_mach"));
		assertTrue(lines.get(0).contains("tip_mach_runtime_margin"));
		assertTrue(lines.get(0).contains("operating_envelope_margin_fraction"));
		assertTrue(lines.get(0).contains("disk_mass_flow_kg_s"));
		assertTrue(lines.get(0).contains("wake_swirl_kinetic_power_w"));
		assertTrue(lines.get(0).contains("torque_coefficient_cq"));
		assertTrue(lines.get(0).contains("useful_axial_thrust_power_w"));
		assertTrue(lines.get(0).contains("ideal_induced_power_w"));
		assertTrue(lines.get(0).contains("far_wake_contracted_area_over_disk_area"));
		assertTrue(lines.get(0).contains("far_wake_equivalent_radius_over_rotor_radius"));
		assertTrue(lines.get(0).contains("wake_angular_momentum_torque_nm"));
		assertTrue(lines.get(0).contains("wake_angular_momentum_torque_residual_nm"));
		assertTrue(lines.get(0).contains("wake_angular_momentum_torque_residual_fraction"));
		assertTrue(lines.get(0).contains("wake_angular_momentum_torque_body_y_nm"));
		assertTrue(lines.get(0).contains("wake_angular_momentum_torque_residual_body_y_nm"));
		assertTrue(lines.get(0).contains("representative_blade_chord_m"));
		assertTrue(lines.get(0).contains("reynolds_number"));
	}

	@Test
	void versionedApDroneRuntimeCurvePacketMatchesExporter() throws IOException {
		double diameter = DroneConfig.apDrone().rotors().get(0).radiusMeters() * 2.0;
		List<String> expected = CtCpJCurveExporter.csvLines(
				"apDrone",
				PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER,
				diameter
		);
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_runtime_curve_packet.csv");
		List<String> actual = Files.readAllLines(packet);

		assertEquals(expected, actual);
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_runtime_reverse_axial_clamp,")));
		assertTrue(actual.stream().anyMatch(line ->
				line.contains(",static_anchored_runtime_high_j_block,")
						&& line.contains(",OUT_OF_ENVELOPE_BLOCKED,")));
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_runtime_transverse_inflow_diagnostic,")));
	}

	private static double numericCell(List<String> lines, String caseName, String queryJ, int cellIndex) {
		String line = lineForCaseAndQueryJ(lines, caseName, queryJ);
		return Double.parseDouble(line.split(",", -1)[cellIndex]);
	}

	private static double torqueFromCq(
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample,
			String[] cells
	) {
		double n = sample.revolutionsPerSecond();
		return Double.parseDouble(cells[75])
				* sample.airDensityKgPerCubicMeter()
				* n
				* n
				* Math.pow(sample.propellerDiameterMeters(), 5.0);
	}

	private static double numericCell(
			List<String> lines,
			String caseName,
			String queryJ,
			String queryRpm,
			int cellIndex
	) {
		String line = lineForCaseAndQuery(lines, caseName, queryJ, queryRpm);
		return Double.parseDouble(line.split(",", -1)[cellIndex]);
	}

	private static String lineForCaseAndQueryJ(List<String> lines, String caseName, String queryJ) {
		return lines.stream()
				.filter(candidate -> candidate.startsWith("apDrone," + caseName + "," + queryJ + ","))
				.findFirst()
				.orElseThrow();
	}

	private static String lineForCaseAndQuery(
			List<String> lines,
			String caseName,
			String queryJ,
			String queryRpm
	) {
		return lines.stream()
				.filter(candidate -> candidate.startsWith(
						"apDrone," + caseName + "," + queryJ + "," + queryRpm + ","))
				.findFirst()
				.orElseThrow();
	}

	private static String lineForCase(List<String> lines, String caseName) {
		return lines.stream()
				.filter(candidate -> candidate.startsWith("apDrone," + caseName + ","))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_runtime_curve_packet.csv"))) {
				return path;
			}
		}
		throw new IllegalStateException("Cannot locate repository root");
	}
}
