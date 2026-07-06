package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.MotorBenchCurrentModel;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;
import com.tenicana.dronecraft.sim.RotorStaticCtCpModel;

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

		assertEquals(44, lines.size());
		assertTrue(lines.get(0).startsWith("preset,case,query_j,query_rpm,effective_j,effective_rpm"));
		assertTrue(lines.get(0).endsWith(
				",source_id,lookup_status,lookup_message,runtime_force_replacement_accepted,query_signed_axial_speed_mps"));
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
				.anyMatch(line -> line.contains(",true,") || line.contains(",BLOCKED,")));

		double midThrust = numericCell(lines, "mid_domain_mid_rpm", "0.406400000000000", 13);
		double highThrust = numericCell(lines, "high_domain_max_rpm", "0.731520000000000", 13);
		double midPower = numericCell(lines, "mid_domain_mid_rpm", "0.406400000000000", 14);
		double highPower = numericCell(lines, "high_domain_max_rpm", "0.731520000000000", 14);
		assertTrue(midThrust > 0.0);
		assertTrue(highThrust > midThrust);
		assertTrue(highPower > midPower);

		String foxeerStatic = lineForCase(lines, "static_rotor_spec_foxeer_public_test");
		assertEquals(RotorStaticCtCpModel.SOURCE_ID, foxeerStatic.split(",", -1)[20]);
		assertEquals(0.159299848814191, Double.parseDouble(foxeerStatic.split(",", -1)[9]), 1.0e-15);
		assertEquals(MotorBenchCurrentModel.FOXEER_DONUT_5145_PUBLIC_TEST_THRUST_NEWTONS,
				Double.parseDouble(foxeerStatic.split(",", -1)[13]), 1.0e-12);

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
		assertTrue(Double.parseDouble(runtimeHoverMidJ.split(",", -1)[13])
				< Double.parseDouble(runtimeHoverStatic.split(",", -1)[13]));
		assertTrue(Double.parseDouble(runtimeHoverMidJ.split(",", -1)[14])
				> Double.parseDouble(runtimeHoverStatic.split(",", -1)[14]));

		String reverseClamp = lineForCase(lines, "static_anchored_runtime_reverse_axial_clamp");
		String[] reverseCells = reverseClamp.split(",", -1);
		assertEquals("CLAMPED_EXACT", reverseCells[6]);
		assertEquals("true", reverseCells[7]);
		assertEquals("false", reverseCells[8]);
		assertEquals("CLAMPED", reverseCells[21]);
		assertEquals("reverse-axial-flow-clamped-to-static-anchor", reverseCells[22]);
		assertEquals("false", reverseCells[23]);
		assertTrue(Double.parseDouble(reverseCells[24]) < 0.0);
		assertEquals(0.0, Double.parseDouble(reverseCells[12]), 1.0e-15);
		assertTrue(Double.parseDouble(reverseCells[13]) > 0.0);

		String blockedHighJ = lineForCase(lines, "static_anchored_runtime_high_j_block");
		String[] blockedCells = blockedHighJ.split(",", -1);
		assertEquals("BLOCKED", blockedCells[6]);
		assertEquals("false", blockedCells[7]);
		assertEquals("true", blockedCells[8]);
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", blockedCells[21]);
		assertEquals("query-outside-accepted-advance-shape-window", blockedCells[22]);
		assertEquals("false", blockedCells[23]);
		assertTrue(Double.parseDouble(blockedCells[24]) > 0.0);
		assertEquals(0.0, Double.parseDouble(blockedCells[13]), 1.0e-15);
		assertEquals(0.0, Double.parseDouble(blockedCells[14]), 1.0e-15);
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
		assertEquals(44, lines.size());
		assertTrue(lines.get(0).contains("shaft_torque_nm"));
		assertTrue(lines.get(0).contains("source_id"));
		assertTrue(lines.get(0).contains("query_signed_axial_speed_mps"));
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
	}

	private static double numericCell(List<String> lines, String caseName, String queryJ, int cellIndex) {
		String line = lineForCaseAndQueryJ(lines, caseName, queryJ);
		return Double.parseDouble(line.split(",", -1)[cellIndex]);
	}

	private static String lineForCaseAndQueryJ(List<String> lines, String caseName, String queryJ) {
		return lines.stream()
				.filter(candidate -> candidate.startsWith("apDrone," + caseName + "," + queryJ + ","))
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
