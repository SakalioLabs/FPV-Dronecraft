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

		assertEquals(25, lines.size());
		assertTrue(lines.get(0).startsWith("preset,case,query_j,query_rpm,effective_j_min"));
		assertEquals(4, integerCell(hover, columns, "rotor_count"));
		assertEquals(4, integerCell(hover, columns, "accepted_rotor_count"));
		assertEquals(4, integerCell(hover, columns, "runtime_force_replacement_accepted_rotor_count"));
		assertEquals(0, integerCell(hover, columns, "blocked_rotor_count"));
		assertEquals(0, integerCell(hover, columns, "clamped_rotor_count"));
		assertEquals("ACCEPTED", textCell(hover, columns, "runtime_eligibility_status"));
		assertEquals(0.0, numberCell(hover, columns, "query_signed_axial_speed_mps"), 1.0e-15);
		assertEquals(rotorSample.thrustNewtons() * 4.0,
				numberCell(hover, columns, "total_thrust_n"), 1.0e-12);
		assertEquals(rotorSample.shaftPowerWatts() * 4.0,
				numberCell(hover, columns, "total_shaft_power_w"), 1.0e-12);
		assertEquals(rotorSample.shaftTorqueNewtonMeters() * 4.0,
				numberCell(hover, columns, "total_shaft_torque_nm"), 1.0e-15);
		assertEquals(rotorSample.dimensionalSample().idealMomentumPowerWatts() * 4.0,
				numberCell(hover, columns, "total_ideal_momentum_power_w"), 1.0e-12);
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
	}

	@Test
	void diagnosticsExposeClampBlockAndObliqueInflowWithoutRuntimeReplacement() {
		List<String> lines = CtCpJConfigurationCurveExporter.csvLines("apDrone", RHO);
		Map<String, Integer> columns = columns(lines);
		String reverseClamp = lineForCase(lines, columns, "static_anchored_configuration_reverse_axial_clamp");
		String highBlock = lineForCase(lines, columns, "static_anchored_configuration_high_j_block");
		String transverse = lineForCase(lines, columns, "static_anchored_configuration_transverse_inflow_diagnostic");

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
		assertEquals(0.0, numberCell(highBlock, columns, "runtime_replacement_total_shaft_power_w"), 1.0e-15);

		assertEquals(4, integerCell(transverse, columns, "accepted_rotor_count"));
		assertEquals(0, integerCell(transverse, columns, "runtime_force_replacement_accepted_rotor_count"));
		assertEquals("OBLIQUE_INFLOW_OUTSIDE_RUNTIME_ENVELOPE",
				textCell(transverse, columns, "runtime_eligibility_status"));
		assertEquals(2.5, numberCell(transverse, columns, "transverse_air_speed_mps"), 1.0e-15);
		assertTrue(numberCell(transverse, columns, "inflow_angle_deg") > 15.0);
		assertTrue(numberCell(transverse, columns, "total_thrust_n") > 0.0);
		assertEquals(0.0, numberCell(transverse, columns, "runtime_replacement_total_thrust_n"), 1.0e-15);
	}

	@Test
	void writeCreatesParentDirectoriesAndCsvFile(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("nested").resolve("configuration.csv");
		CtCpJConfigurationCurveExporter.write("apDrone", output, RHO);

		List<String> lines = Files.readAllLines(output);
		assertEquals(25, lines.size());
		assertTrue(lines.get(0).contains("total_thrust_n"));
		assertTrue(lines.get(0).contains("total_shaft_power_w"));
		assertTrue(lines.get(0).contains("total_body_torque_y_nm"));
		assertTrue(lines.get(0).contains("runtime_replacement_total_thrust_n"));
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
