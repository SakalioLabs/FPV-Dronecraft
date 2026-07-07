package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CtCpJActuatorDiskSourceTermExporterTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;

	@Test
	void csvLinesExportSourceTermsForHoverForwardHighJAndEnvelopeDiagnostics() {
		List<String> lines = CtCpJActuatorDiskSourceTermExporter.csvLines(
				"apDrone",
				RHO,
				SOURCE_THICKNESS,
				25.0,
				0.0
		);
		Map<String, Integer> columns = columns(lines);

		assertEquals(41, lines.size());
		assertTrue(lines.get(0).startsWith("preset,case,row_kind,rotor_index,query_j,query_rpm"));
		assertFalse(lines.stream().skip(1).anyMatch(line -> line.contains("NaN")));

		String hover = lineFor(lines, columns, "static_anchored_source_hover", "raw_source", 0);
		String mid = lineFor(lines, columns, "static_anchored_source_mid_j", "raw_source", 0);
		String high = lineFor(lines, columns, "static_anchored_source_high_j", "raw_source", 0);
		String reverseRaw = lineFor(lines, columns,
				"static_anchored_source_reverse_axial_clamp", "raw_source", 0);
		String reverseRuntime = lineFor(lines, columns,
				"static_anchored_source_reverse_axial_clamp", "runtime_replacement_source", 0);
		String blockedRaw = lineFor(lines, columns,
				"static_anchored_source_high_j_block", "raw_source", 0);
		String blockedRuntime = lineFor(lines, columns,
				"static_anchored_source_high_j_block", "runtime_replacement_source", 0);

		assertEquals(0.0, numberCell(hover, columns, "query_j"), 1.0e-15);
		assertEquals("false", textCell(hover, columns, "blocked"));
		assertEquals("true", textCell(hover, columns, "applied"));
		assertTrue(numberCell(hover, columns, "ct") > 0.0);
		assertTrue(numberCell(hover, columns, "cp") > 0.0);
		assertTrue(numberCell(hover, columns, "pressure_jump_pa") > 0.0);
		assertTrue(numberCell(hover, columns, "mass_flux_kg_s_m2") > 0.0);
		assertTrue(numberCell(hover, columns, "ideal_momentum_power_loading_w_m2") > 0.0);
		assertEquals(0.0, numberCell(hover, columns, "eta"), 1.0e-15);
		assertEquals(1.0, numberCell(hover, columns, "disk_normal_world_y"), 1.0e-15);
		assertEquals(Math.sqrt(numberCell(hover, columns, "disk_area_m2") / Math.PI),
				numberCell(hover, columns, "disk_radius_m"), 1.0e-15);
		assertEquals(numberCell(hover, columns, "disk_area_m2") * SOURCE_THICKNESS,
				numberCell(hover, columns, "source_volume_m3"), 1.0e-15);
		assertEquals(0.0, numberCell(hover, columns, "thrust_surface_force_world_x_n_m2"), 1.0e-15);
		assertEquals(numberCell(hover, columns, "pressure_jump_pa"),
				numberCell(hover, columns, "thrust_surface_force_world_y_n_m2"), 1.0e-12);
		assertEquals(numberCell(hover, columns, "thrust_surface_force_world_y_n_m2")
						* numberCell(hover, columns, "disk_area_m2"),
				numberCell(hover, columns, "integrated_thrust_force_world_y_n"), 1.0e-12);
		assertEquals(numberCell(hover, columns, "thrust_surface_force_world_y_n_m2") / SOURCE_THICKNESS,
				numberCell(hover, columns, "body_force_density_world_y_n_m3"), 1.0e-9);
		assertEquals(numberCell(hover, columns, "body_force_density_world_y_n_m3")
						* numberCell(hover, columns, "source_volume_m3"),
				numberCell(hover, columns, "equivalent_body_force_integral_world_y_n"), 1.0e-12);
		assertEquals(numberCell(hover, columns, "integrated_thrust_force_world_y_n"),
				numberCell(hover, columns, "equivalent_body_force_integral_world_y_n"), 1.0e-12);
		assertTrue(numberCell(hover, columns, "shaft_power_w") > 0.0);
		assertTrue(numberCell(hover, columns, "shaft_torque_nm") > 0.0);
		assertTrue(numberCell(hover, columns, "ideal_induced_velocity_mps") > 0.0);
		assertTrue(numberCell(hover, columns, "ideal_momentum_power_over_shaft_power") > 0.0);

		assertEquals(0.4064, numberCell(mid, columns, "query_j"), 1.0e-12);
		assertEquals("false", textCell(mid, columns, "blocked"));
		assertEquals("true", textCell(mid, columns, "applied"));
		assertTrue(numberCell(mid, columns, "eta") > 0.0);
		assertTrue(numberCell(mid, columns, "pressure_jump_pa") > 0.0);
		assertTrue(numberCell(mid, columns, "far_wake_axial_velocity_world_y_mps")
				> numberCell(mid, columns, "query_signed_axial_speed_mps"));

		assertEquals(0.73152, numberCell(high, columns, "query_j"), 1.0e-12);
		assertEquals("false", textCell(high, columns, "blocked"));
		assertEquals("true", textCell(high, columns, "applied"));
		assertEquals(Math.cos(Math.PI / 4.0), numberCell(high, columns, "body_to_world_qw"), 1.0e-15);
		assertEquals(Math.sin(Math.PI / 4.0), numberCell(high, columns, "body_to_world_qz"), 1.0e-15);
		assertEquals(-1.0, numberCell(high, columns, "disk_normal_world_x"), 1.0e-15);
		assertEquals(0.0, numberCell(high, columns, "disk_normal_world_y"), 1.0e-15);
		assertTrue(numberCell(high, columns, "pressure_jump_pa") > 0.0);

		assertEquals("true", textCell(reverseRaw, columns, "clamped"));
		assertEquals("CLAMPED", textCell(reverseRaw, columns, "lookup_status"));
		assertEquals("true", textCell(reverseRaw, columns, "applied"));
		assertEquals("false", textCell(reverseRuntime, columns, "applied"));
		assertEquals(0.0, numberCell(reverseRuntime, columns, "pressure_jump_pa"), 1.0e-15);
		assertEquals(0.0, numberCell(reverseRuntime, columns, "body_force_density_world_y_n_m3"), 1.0e-15);

		assertEquals("true", textCell(blockedRaw, columns, "blocked"));
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", textCell(blockedRaw, columns, "lookup_status"));
		assertEquals("false", textCell(blockedRaw, columns, "applied"));
		assertEquals(0.0, numberCell(blockedRaw, columns, "pressure_jump_pa"), 1.0e-15);
		assertEquals(0.0, numberCell(blockedRaw, columns, "integrated_thrust_force_world_y_n"), 1.0e-15);
		assertEquals(0.0, numberCell(blockedRaw, columns,
				"equivalent_body_force_integral_world_y_n"), 1.0e-15);
		assertEquals("false", textCell(blockedRuntime, columns, "runtime_force_replacement_accepted"));
		assertEquals(0.0, numberCell(blockedRuntime, columns, "body_force_density_world_y_n_m3"), 1.0e-15);
	}

	@Test
	void writeCreatesParentDirectoriesAndCsvFile(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("nested").resolve("source-terms.csv");
		CtCpJActuatorDiskSourceTermExporter.write("apDrone", output, RHO);

		List<String> lines = Files.readAllLines(output);
		assertEquals(41, lines.size());
		assertTrue(lines.get(0).contains("pressure_jump_pa"));
		assertTrue(lines.get(0).contains("source_volume_m3"));
		assertTrue(lines.get(0).contains("body_force_density_world_y_n_m3"));
		assertTrue(lines.get(0).contains("equivalent_body_force_integral_world_y_n"));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_source_high_j_block,raw_source,")));
	}

	@Test
	void versionedApDroneSourceTermPacketMatchesExporter() throws IOException {
		List<String> expected = CtCpJActuatorDiskSourceTermExporter.csvLines("apDrone", RHO);
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_actuator_disk_source_term_packet.csv");
		List<String> actual = Files.readAllLines(packet);

		assertEquals(expected, actual);
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_source_reverse_axial_clamp,raw_source,")
						&& line.contains(",CLAMPED,true,false,true,false,")));
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_source_high_j_block,raw_source,")
						&& line.contains(",OUT_OF_ENVELOPE_BLOCKED,false,true,false,false,")));
	}

	private static Map<String, Integer> columns(List<String> lines) {
		String[] header = lines.get(0).split(",", -1);
		Map<String, Integer> columns = new LinkedHashMap<>();
		for (int i = 0; i < header.length; i++) {
			columns.put(header[i], i);
		}
		return columns;
	}

	private static String lineFor(
			List<String> lines,
			Map<String, Integer> columns,
			String caseName,
			String rowKind,
			int rotorIndex
	) {
		int caseColumn = columns.get("case");
		int rowKindColumn = columns.get("row_kind");
		int rotorIndexColumn = columns.get("rotor_index");
		return lines.stream()
				.skip(1)
				.filter(line -> {
					String[] cells = line.split(",", -1);
					return cells[caseColumn].equals(caseName)
							&& cells[rowKindColumn].equals(rowKind)
							&& Integer.parseInt(cells[rotorIndexColumn]) == rotorIndex;
				})
				.findFirst()
				.orElseThrow();
	}

	private static String textCell(String line, Map<String, Integer> columns, String columnName) {
		return line.split(",", -1)[columns.get(columnName)];
	}

	private static double numberCell(String line, Map<String, Integer> columns, String columnName) {
		return Double.parseDouble(textCell(line, columns, columnName));
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
