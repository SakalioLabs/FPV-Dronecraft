package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJRotorForceModel;
import com.tenicana.dronecraft.sim.RotorSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CtCpJConfigurationCurveExporterTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void csvLinesExportWholeConfigurationHoverClosure() {
		List<String> lines = CtCpJConfigurationCurveExporter.csvLines("apDrone", RHO);
		Map<String, Integer> columns = columns(lines);
		String hover = lineForCaseAndJ(lines, columns, "static_anchored_configuration_hover", 0.0);

		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = hoverRpm(config, rotor) * 2.0 * Math.PI / 60.0;
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample rotorSample =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
						"apDrone",
						"static_anchored_configuration_hover",
						rotor,
						0.0,
						hoverOmega,
						RHO,
						config.centerOfMassOffsetBodyMeters(),
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertEquals(33, lines.size());
		assertTrue(lines.get(0).startsWith(
				"preset,case,query_j,query_rpm,target_thrust_n,target_thrust_residual_n"));
		assertEquals(4, integerCell(hover, columns, "rotor_count"));
		assertEquals(4, integerCell(hover, columns, "accepted_rotor_count"));
		assertEquals(4, integerCell(hover, columns, "runtime_force_replacement_accepted_rotor_count"));
		assertEquals(0, integerCell(hover, columns, "blocked_rotor_count"));
		assertEquals(0, integerCell(hover, columns, "clamped_rotor_count"));
		assertEquals("ACCEPTED", textCell(hover, columns, "runtime_eligibility_status"));
		assertEquals("DIRECT_SAMPLE", textCell(hover, columns, "target_thrust_solve_status"));
		assertEquals(0, integerCell(hover, columns, "target_thrust_solve_iterations"));
		assertEquals("DIRECT_SAMPLE", textCell(hover, columns, "trim_solve_status"));
		assertEquals(0.0, numberCell(hover, columns, "trim_target_body_torque_x_nm"), 1.0e-15);
		assertEquals(0.0, numberCell(hover, columns, "query_signed_axial_speed_mps"), 1.0e-15);
		assertEquals(rotorSample.thrustNewtons() * 4.0,
				numberCell(hover, columns, "total_thrust_n"), 1.0e-12);
		assertEquals(numberCell(hover, columns, "total_thrust_n"),
				numberCell(hover, columns, "target_thrust_n"), 1.0e-12);
		assertEquals(0.0, numberCell(hover, columns, "target_thrust_residual_n"), 1.0e-15);
		assertEquals(rotorSample.shaftPowerWatts() * 4.0,
				numberCell(hover, columns, "total_shaft_power_w"), 1.0e-12);
		assertEquals(rotorSample.shaftTorqueNewtonMeters() * 4.0,
				numberCell(hover, columns, "total_shaft_torque_nm"), 1.0e-15);
		assertEquals(rotorSample.dimensionalSample().diskMassFlowKilogramsPerSecond() * 4.0,
				numberCell(hover, columns, "total_disk_mass_flow_kg_s"), 1.0e-12);
		assertEquals(0.0, numberCell(hover, columns, "total_useful_axial_thrust_power_w"), 1.0e-15);
		assertEquals(rotorSample.dimensionalSample().idealInducedPowerWatts() * 4.0,
				numberCell(hover, columns, "total_ideal_induced_power_w"), 1.0e-12);
		assertEquals(rotorSample.dimensionalSample().idealMomentumPowerWatts() * 4.0,
				numberCell(hover, columns, "total_ideal_momentum_power_w"), 1.0e-12);
		assertEquals(
				numberCell(hover, columns, "total_useful_axial_thrust_power_w")
						+ numberCell(hover, columns, "total_ideal_induced_power_w"),
				numberCell(hover, columns, "total_ideal_momentum_power_w"),
				1.0e-12
		);
		assertEquals(
				numberCell(hover, columns, "total_ideal_momentum_power_w")
						/ numberCell(hover, columns, "total_shaft_power_w"),
				numberCell(hover, columns, "total_ideal_momentum_power_over_shaft_power"),
				1.0e-14
		);
		assertEquals(0.0, numberCell(hover, columns, "total_reaction_torque_body_y_nm"), 1.0e-15);
		assertEquals(0.0, numberCell(hover, columns, "total_body_torque_x_nm"), 1.0e-15);
		assertEquals(0.0, numberCell(hover, columns, "total_body_torque_y_nm"), 1.0e-15);
		assertEquals(0.0, numberCell(hover, columns, "total_body_torque_z_nm"), 1.0e-15);
		assertEquals(numberCell(hover, columns, "total_thrust_n"),
				numberCell(hover, columns, "runtime_replacement_total_thrust_n"), 1.0e-12);
		assertEquals(numberCell(hover, columns, "total_shaft_power_w"),
				numberCell(hover, columns, "runtime_replacement_total_shaft_power_w"), 1.0e-12);
		assertEquals(numberCell(hover, columns, "total_disk_mass_flow_kg_s"),
				numberCell(hover, columns, "runtime_replacement_disk_mass_flow_kg_s"), 1.0e-12);
		assertEquals(numberCell(hover, columns, "total_ideal_induced_power_w"),
				numberCell(hover, columns, "runtime_replacement_ideal_induced_power_w"), 1.0e-12);
	}

	@Test
	void trimRowsExposeBodyTorqueSolutionAndBlockedRotorSolve() {
		List<String> lines = CtCpJConfigurationCurveExporter.csvLines("apDrone", RHO);
		Map<String, Integer> columns = columns(lines);
		String trim = lineForCase(lines, columns, "static_anchored_configuration_trim_body_torque");
		String bodyKinematics = lineForCase(lines, columns,
				"static_anchored_configuration_trim_body_kinematics");
		String blocked = lineForCase(lines, columns, "static_anchored_configuration_trim_upper_below_target");

		assertEquals("TRIM_SOLVE", textCell(trim, columns, "target_thrust_solve_status"));
		assertEquals("SOLVED", textCell(trim, columns, "trim_solve_status"));
		assertEquals("ACCEPTED", textCell(trim, columns, "runtime_eligibility_status"));
		assertEquals(4, integerCell(trim, columns, "accepted_rotor_count"));
		assertEquals(4, integerCell(trim, columns, "runtime_force_replacement_accepted_rotor_count"));
		assertEquals(numberCell(trim, columns, "target_thrust_n"),
				numberCell(trim, columns, "total_thrust_n"), 1.0e-5);
		assertEquals(numberCell(trim, columns, "trim_target_body_torque_x_nm"),
				numberCell(trim, columns, "total_body_torque_x_nm"), 3.0e-5);
		assertEquals(numberCell(trim, columns, "trim_target_body_torque_y_nm"),
				numberCell(trim, columns, "total_body_torque_y_nm"), 3.0e-5);
		assertEquals(numberCell(trim, columns, "trim_target_body_torque_z_nm"),
				numberCell(trim, columns, "total_body_torque_z_nm"), 3.0e-5);
		assertEquals(0.0, numberCell(trim, columns, "trim_body_torque_residual_x_nm"), 3.0e-5);
		assertEquals(0.0, numberCell(trim, columns, "trim_body_torque_residual_y_nm"), 3.0e-5);
		assertEquals(0.0, numberCell(trim, columns, "trim_body_torque_residual_z_nm"), 3.0e-5);
		assertTrue(numberCell(trim, columns, "trim_max_allocated_rotor_thrust_n")
				> numberCell(trim, columns, "trim_min_allocated_rotor_thrust_n"));
		assertTrue(numberCell(trim, columns, "effective_rpm_max")
				> numberCell(trim, columns, "effective_rpm_min"));

		assertEquals("TRIM_SOLVE", textCell(bodyKinematics, columns, "target_thrust_solve_status"));
		assertEquals("SOLVED", textCell(bodyKinematics, columns, "trim_solve_status"));
		assertEquals("ACCEPTED", textCell(bodyKinematics, columns, "runtime_eligibility_status"));
		assertEquals(3.0, numberCell(bodyKinematics, columns, "query_signed_axial_speed_mps"), 1.0e-12);
		assertEquals(4.0, numberCell(bodyKinematics, columns, "body_angular_rate_x_rad_s"), 1.0e-12);
		assertEquals(0.0, numberCell(bodyKinematics, columns, "trim_target_body_torque_x_nm"), 1.0e-15);
		assertEquals(numberCell(bodyKinematics, columns, "target_thrust_n"),
				numberCell(bodyKinematics, columns, "total_thrust_n"), 1.0e-5);
		assertEquals(0.0, numberCell(bodyKinematics, columns, "trim_body_torque_residual_x_nm"), 2.0e-5);
		assertEquals(0.0, numberCell(bodyKinematics, columns, "trim_body_torque_residual_y_nm"), 2.0e-5);
		assertEquals(0.0, numberCell(bodyKinematics, columns, "trim_body_torque_residual_z_nm"), 2.0e-5);
		assertTrue(numberCell(bodyKinematics, columns, "effective_j_max")
				> numberCell(bodyKinematics, columns, "effective_j_min"));
		assertTrue(numberCell(bodyKinematics, columns, "effective_rpm_max")
				> numberCell(bodyKinematics, columns, "effective_rpm_min"));
		assertTrue(numberCell(bodyKinematics, columns, "total_shaft_power_w")
				> numberCell(bodyKinematics, columns, "total_ideal_momentum_power_w"));

		assertEquals("TRIM_SOLVE", textCell(blocked, columns, "target_thrust_solve_status"));
		assertEquals("ROTOR_SOLVE_BLOCKED", textCell(blocked, columns, "trim_solve_status"));
		assertEquals(4, integerCell(blocked, columns, "accepted_rotor_count"));
		assertEquals(4, integerCell(blocked, columns, "runtime_force_replacement_accepted_rotor_count"));
		assertEquals(0, integerCell(blocked, columns, "blocked_rotor_count"));
		assertTrue(numberCell(blocked, columns, "target_thrust_n")
				> numberCell(blocked, columns, "total_thrust_n"));
		assertTrue(numberCell(blocked, columns, "target_thrust_residual_n") < 0.0);
		assertEquals(0.0, numberCell(blocked, columns, "trim_target_body_torque_x_nm"), 1.0e-15);
	}

	@Test
	void targetThrustSolveRowsExposeSolvedAndBlockedConfigurationSamples() {
		List<String> lines = CtCpJConfigurationCurveExporter.csvLines("apDrone", RHO);
		Map<String, Integer> columns = columns(lines);
		String directHover = lineForCaseAndJ(lines, columns, "static_anchored_configuration_hover", 0.0);
		String hoverSolve = lineForCase(lines, columns, "static_anchored_configuration_target_hover_solve");
		String forwardSolve = lineForCase(lines, columns, "static_anchored_configuration_target_forward_solve");
		String upperBelow =
				lineForCase(lines, columns, "static_anchored_configuration_target_upper_below_target");
		String highBlock = lineForCase(lines, columns, "static_anchored_configuration_target_high_j_block");

		assertEquals("SOLVED", textCell(hoverSolve, columns, "target_thrust_solve_status"));
		assertTrue(integerCell(hoverSolve, columns, "target_thrust_solve_iterations") > 0);
		assertEquals(numberCell(directHover, columns, "total_thrust_n"),
				numberCell(hoverSolve, columns, "target_thrust_n"), 1.0e-12);
		assertEquals(numberCell(hoverSolve, columns, "target_thrust_n"),
				numberCell(hoverSolve, columns, "total_thrust_n"), 1.0e-6);
		assertEquals(0.0, numberCell(hoverSolve, columns, "query_j"), 1.0e-12);
		assertEquals(numberCell(directHover, columns, "query_rpm"),
				numberCell(hoverSolve, columns, "query_rpm"), 1.0e-8);
		assertEquals("ACCEPTED", textCell(hoverSolve, columns, "runtime_eligibility_status"));

		assertEquals("SOLVED", textCell(forwardSolve, columns, "target_thrust_solve_status"));
		assertTrue(integerCell(forwardSolve, columns, "target_thrust_solve_iterations") > 0);
		assertEquals(numberCell(directHover, columns, "total_thrust_n"),
				numberCell(forwardSolve, columns, "target_thrust_n"), 1.0e-12);
		assertEquals(numberCell(forwardSolve, columns, "target_thrust_n"),
				numberCell(forwardSolve, columns, "total_thrust_n"), 1.0e-5);
		assertTrue(numberCell(forwardSolve, columns, "query_j") > 0.0);
		assertTrue(numberCell(forwardSolve, columns, "query_rpm")
				> numberCell(hoverSolve, columns, "query_rpm"));
		assertTrue(numberCell(forwardSolve, columns, "total_useful_axial_thrust_power_w") > 0.0);
		assertEquals("ACCEPTED", textCell(forwardSolve, columns, "runtime_eligibility_status"));

		assertEquals("UPPER_BOUND_BELOW_TARGET",
				textCell(upperBelow, columns, "target_thrust_solve_status"));
		assertEquals(0, integerCell(upperBelow, columns, "target_thrust_solve_iterations"));
		assertEquals(4, integerCell(upperBelow, columns, "accepted_rotor_count"));
		assertEquals(0, integerCell(upperBelow, columns, "blocked_rotor_count"));
		assertTrue(numberCell(upperBelow, columns, "target_thrust_n")
				> numberCell(upperBelow, columns, "total_thrust_n"));
		assertTrue(numberCell(upperBelow, columns, "target_thrust_residual_n") < 0.0);

		assertEquals("UPPER_BOUND_BLOCKED", textCell(highBlock, columns, "target_thrust_solve_status"));
		assertEquals(4, integerCell(highBlock, columns, "blocked_rotor_count"));
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", textCell(highBlock, columns, "runtime_eligibility_status"));
		assertEquals(0.0, numberCell(highBlock, columns, "total_thrust_n"), 1.0e-15);
		assertTrue(numberCell(highBlock, columns, "target_thrust_residual_n") < 0.0);
	}

	@Test
	void forwardConfigurationRowKeepsTrendAndTorqueCancellation() {
		List<String> lines = CtCpJConfigurationCurveExporter.csvLines("apDrone", RHO);
		Map<String, Integer> columns = columns(lines);
		String hover = lineForCaseAndJ(lines, columns, "static_anchored_configuration_hover", 0.0);
		String forward = lineForCaseAndJ(lines, columns, "static_anchored_configuration_hover", 0.4064);

		assertEquals("ACCEPTED", textCell(forward, columns, "runtime_eligibility_status"));
		assertEquals(4, integerCell(forward, columns, "runtime_force_replacement_accepted_rotor_count"));
		assertTrue(numberCell(forward, columns, "query_signed_axial_speed_mps") > 0.0);
		assertEquals(0.0, numberCell(forward, columns, "transverse_air_speed_mps"), 1.0e-15);
		assertEquals(0.0, numberCell(forward, columns, "inflow_angle_deg"), 1.0e-15);
		assertTrue(numberCell(forward, columns, "total_thrust_n")
				< numberCell(hover, columns, "total_thrust_n"));
		assertTrue(numberCell(forward, columns, "total_shaft_power_w")
				> numberCell(hover, columns, "total_shaft_power_w"));
		assertTrue(numberCell(forward, columns, "total_ideal_momentum_power_over_shaft_power") > 0.0);
		assertTrue(numberCell(forward, columns, "total_ideal_momentum_power_over_shaft_power") <= 1.0);
		assertEquals(0.0, numberCell(forward, columns, "total_reaction_torque_body_y_nm"), 1.0e-15);
		assertEquals(0.0, numberCell(forward, columns, "total_body_torque_y_nm"), 1.0e-15);
		assertEquals(numberCell(forward, columns, "total_thrust_n"),
				numberCell(forward, columns, "runtime_replacement_total_thrust_n"), 1.0e-12);
		assertEquals(numberCell(forward, columns, "total_wake_kinetic_power_w")
						/ numberCell(forward, columns, "total_shaft_power_w"),
				numberCell(forward, columns, "total_wake_kinetic_power_over_shaft_power"), 1.0e-14);
		assertTrue(numberCell(forward, columns, "total_disk_mass_flow_kg_s")
				> numberCell(hover, columns, "total_disk_mass_flow_kg_s"));
		assertTrue(numberCell(forward, columns, "total_useful_axial_thrust_power_w") > 0.0);
		assertTrue(numberCell(forward, columns, "total_ideal_induced_power_w") > 0.0);
		assertEquals(
				numberCell(forward, columns, "total_useful_axial_thrust_power_w")
						+ numberCell(forward, columns, "total_ideal_induced_power_w"),
				numberCell(forward, columns, "total_ideal_momentum_power_w"),
				1.0e-12
		);
		assertEquals(numberCell(forward, columns, "total_useful_axial_thrust_power_w"),
				numberCell(forward, columns, "runtime_replacement_useful_axial_thrust_power_w"), 1.0e-12);
	}

	@Test
	void diagnosticsExposeClampBlockAndObliqueInflowWithoutRuntimeReplacement() {
		List<String> lines = CtCpJConfigurationCurveExporter.csvLines("apDrone", RHO);
		Map<String, Integer> columns = columns(lines);
		String reverseClamp = lineForCase(lines, columns, "static_anchored_configuration_reverse_axial_clamp");
		String highBlock = lineForCase(lines, columns, "static_anchored_configuration_high_j_block");
		String transverse = lineForCase(lines, columns, "static_anchored_configuration_transverse_inflow_diagnostic");
		String bodyRate = lineForCase(lines, columns, "static_anchored_configuration_body_rate_roll_diagnostic");

		assertEquals(4, integerCell(reverseClamp, columns, "accepted_rotor_count"));
		assertEquals(4, integerCell(reverseClamp, columns, "clamped_rotor_count"));
		assertEquals(0, integerCell(reverseClamp, columns, "runtime_force_replacement_accepted_rotor_count"));
		assertEquals("CLAMPED", textCell(reverseClamp, columns, "runtime_eligibility_status"));
		assertTrue(numberCell(reverseClamp, columns, "query_signed_axial_speed_mps") < 0.0);
		assertTrue(numberCell(reverseClamp, columns, "total_thrust_n") > 0.0);
		assertEquals(0.0, numberCell(reverseClamp, columns, "runtime_replacement_total_thrust_n"), 1.0e-15);

		assertEquals(0, integerCell(highBlock, columns, "accepted_rotor_count"));
		assertEquals(4, integerCell(highBlock, columns, "blocked_rotor_count"));
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", textCell(highBlock, columns, "runtime_eligibility_status"));
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", textCell(highBlock, columns, "lookup_status_summary"));
		assertEquals(0.0, numberCell(highBlock, columns, "total_thrust_n"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "total_shaft_power_w"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "total_disk_mass_flow_kg_s"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "total_useful_axial_thrust_power_w"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "total_ideal_induced_power_w"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "runtime_replacement_total_shaft_power_w"), 1.0e-15);

		assertEquals(4, integerCell(transverse, columns, "accepted_rotor_count"));
		assertEquals(0, integerCell(transverse, columns, "runtime_force_replacement_accepted_rotor_count"));
		assertEquals("OBLIQUE_INFLOW_OUTSIDE_RUNTIME_ENVELOPE",
				textCell(transverse, columns, "runtime_eligibility_status"));
		assertEquals(2.5, numberCell(transverse, columns, "transverse_air_speed_mps"), 1.0e-15);
		assertTrue(numberCell(transverse, columns, "inflow_angle_deg") > 15.0);
		assertTrue(numberCell(transverse, columns, "total_thrust_n") > 0.0);
		assertEquals(0.0, numberCell(transverse, columns, "runtime_replacement_total_thrust_n"), 1.0e-15);

		assertEquals(4, integerCell(bodyRate, columns, "accepted_rotor_count"));
		assertEquals(4, integerCell(bodyRate, columns, "runtime_force_replacement_accepted_rotor_count"));
		assertEquals("ACCEPTED", textCell(bodyRate, columns, "runtime_eligibility_status"));
		assertEquals(5.0, numberCell(bodyRate, columns, "body_angular_rate_x_rad_s"), 1.0e-15);
		assertEquals(0.0, numberCell(bodyRate, columns, "body_angular_rate_y_rad_s"), 1.0e-15);
		assertEquals(0.0, numberCell(bodyRate, columns, "body_angular_rate_z_rad_s"), 1.0e-15);
		assertTrue(numberCell(bodyRate, columns, "effective_j_min")
				< numberCell(bodyRate, columns, "effective_j_max"));
		assertTrue(Math.abs(numberCell(bodyRate, columns, "total_body_torque_x_nm")) > 1.0e-3);
		assertEquals(0.0, numberCell(bodyRate, columns, "total_reaction_torque_body_y_nm"), 1.0e-12);
		assertEquals(numberCell(bodyRate, columns, "total_thrust_n"),
				numberCell(bodyRate, columns, "runtime_replacement_total_thrust_n"), 1.0e-12);
		assertEquals(numberCell(bodyRate, columns, "total_body_torque_x_nm"),
				numberCell(bodyRate, columns, "runtime_replacement_total_body_torque_x_nm"), 1.0e-12);
		assertEquals(
				numberCell(bodyRate, columns, "total_useful_axial_thrust_power_w")
						+ numberCell(bodyRate, columns, "total_ideal_induced_power_w"),
				numberCell(bodyRate, columns, "total_ideal_momentum_power_w"),
				1.0e-12
		);
	}

	@Test
	void writeCreatesParentDirectoriesAndCsvFile(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("nested").resolve("configuration.csv");
		CtCpJConfigurationCurveExporter.write("apDrone", output, RHO);

		List<String> lines = Files.readAllLines(output);
		assertEquals(33, lines.size());
		assertTrue(lines.get(0).contains("total_thrust_n"));
		assertTrue(lines.get(0).contains("target_thrust_n"));
		assertTrue(lines.get(0).contains("target_thrust_solve_status"));
		assertTrue(lines.get(0).contains("trim_solve_status"));
		assertTrue(lines.get(0).contains("trim_target_body_torque_x_nm"));
		assertTrue(lines.get(0).contains("total_shaft_power_w"));
		assertTrue(lines.get(0).contains("total_disk_mass_flow_kg_s"));
		assertTrue(lines.get(0).contains("total_useful_axial_thrust_power_w"));
		assertTrue(lines.get(0).contains("total_ideal_induced_power_w"));
		assertTrue(lines.get(0).contains("total_body_torque_y_nm"));
		assertTrue(lines.get(0).contains("body_angular_rate_x_rad_s"));
		assertTrue(lines.get(0).contains("runtime_replacement_total_thrust_n"));
		assertTrue(lines.get(0).contains("runtime_replacement_disk_mass_flow_kg_s"));
		assertTrue(lines.get(0).contains("runtime_eligibility_status"));
		assertFalse(lines.stream().skip(1).anyMatch(line -> line.contains("NaN")));
	}

	@Test
	void versionedApDroneConfigurationCurvePacketMatchesExporter() throws IOException {
		List<String> expected = CtCpJConfigurationCurveExporter.csvLines("apDrone", RHO);
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_configuration_curve_packet.csv");
		List<String> actual = Files.readAllLines(packet);

		assertEquals(expected, actual);
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_configuration_reverse_axial_clamp,")));
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_configuration_high_j_block,")
						&& line.contains(",OUT_OF_ENVELOPE_BLOCKED,")));
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_configuration_transverse_inflow_diagnostic,")));
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_configuration_body_rate_roll_diagnostic,")));
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_configuration_target_hover_solve,")
						&& line.contains(",SOLVED,")));
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_configuration_target_high_j_block,")
						&& line.contains(",UPPER_BOUND_BLOCKED,")));
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_configuration_trim_body_torque,")
						&& line.contains(",TRIM_SOLVE,0,SOLVED,")));
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_configuration_trim_body_kinematics,")
						&& line.contains(",TRIM_SOLVE,0,SOLVED,")));
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_configuration_trim_upper_below_target,")
						&& line.contains(",TRIM_SOLVE,0,ROTOR_SOLVE_BLOCKED,")));
	}

	private static Map<String, Integer> columns(List<String> lines) {
		String[] header = lines.get(0).split(",", -1);
		Map<String, Integer> columns = new LinkedHashMap<>();
		for (int i = 0; i < header.length; i++) {
			columns.put(header[i], i);
		}
		return columns;
	}

	private static String lineForCaseAndJ(
			List<String> lines,
			Map<String, Integer> columns,
			String caseName,
			double queryJ
	) {
		int caseColumn = columns.get("case");
		int queryJColumn = columns.get("query_j");
		return lines.stream()
				.skip(1)
				.filter(line -> {
					String[] cells = line.split(",", -1);
					return cells[caseColumn].equals(caseName)
							&& Math.abs(Double.parseDouble(cells[queryJColumn]) - queryJ) <= 1.0e-12;
				})
				.findFirst()
				.orElseThrow();
	}

	private static String lineForCase(List<String> lines, Map<String, Integer> columns, String caseName) {
		int caseColumn = columns.get("case");
		return lines.stream()
				.skip(1)
				.filter(line -> line.split(",", -1)[caseColumn].equals(caseName))
				.findFirst()
				.orElseThrow();
	}

	private static String textCell(String line, Map<String, Integer> columns, String columnName) {
		return line.split(",", -1)[columns.get(columnName)];
	}

	private static int integerCell(String line, Map<String, Integer> columns, String columnName) {
		return Integer.parseInt(textCell(line, columns, columnName));
	}

	private static double numberCell(String line, Map<String, Integer> columns, String columnName) {
		return Double.parseDouble(textCell(line, columns, columnName));
	}

	private static double hoverRpm(DroneConfig config, RotorSpec rotor) {
		double perRotorHoverThrust = config.massKg()
				* config.gravityMetersPerSecondSquared()
				/ config.rotors().size();
		return Math.sqrt(perRotorHoverThrust / rotor.thrustCoefficient()) * 60.0 / (2.0 * Math.PI);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isDirectory(path.resolve("docs/data"))) {
				return path;
			}
		}
		throw new IllegalStateException("Cannot locate repository root");
	}
}
