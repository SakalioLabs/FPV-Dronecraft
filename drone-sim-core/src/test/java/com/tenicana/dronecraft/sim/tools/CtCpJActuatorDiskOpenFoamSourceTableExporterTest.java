package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class CtCpJActuatorDiskOpenFoamSourceTableExporterTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;

	@Test
	void csvLinesExportOpenFoamReadySlabSourceRows() {
		List<String> lines = CtCpJActuatorDiskOpenFoamSourceTableExporter.csvLines(
				"apDrone",
				RHO,
				SOURCE_THICKNESS,
				25.0,
				0.0
		);
		Map<String, Integer> columns = columns(lines);
		String hover = lineFor(lines, columns, "static_anchored_source_hover", "raw_source", 0);
		String highBlock = lineFor(lines, columns, "static_anchored_source_high_j_block", "raw_source", 0);

		assertEquals(41, lines.size());
		assertTrue(lines.get(0).startsWith("preset,case,row_kind,rotor_index,source_name"));
		assertEquals("ctcpj_apdrone_static_anchored_source_hover_raw_source_rotor_0",
				textCell(hover, columns, "source_name"));
		assertEquals("true", textCell(hover, columns, "source_enabled"));
		assertEquals("cylindrical_slab", textCell(hover, columns, "selection_shape"));
		assertEquals("world", textCell(hover, columns, "coordinate_frame"));
		assertEquals(0.0, numberCell(hover, columns, "normal_x"), 1.0e-15);
		assertEquals(1.0, numberCell(hover, columns, "normal_y"), 1.0e-15);
		assertEquals(0.0, numberCell(hover, columns, "normal_z"), 1.0e-15);
		assertEquals(1.0, vectorLength(hover, columns, "tangent_u"), 1.0e-15);
		assertEquals(1.0, vectorLength(hover, columns, "tangent_v"), 1.0e-15);
		assertEquals(0.0, vectorDot(hover, columns, "normal", "tangent_u"), 1.0e-15);
		assertEquals(0.0, vectorDot(hover, columns, "normal", "tangent_v"), 1.0e-15);
		assertEquals(SOURCE_THICKNESS * 0.5, numberCell(hover, columns, "half_thickness_m"), 1.0e-15);
		assertEquals(numberCell(hover, columns, "center_y_m") - SOURCE_THICKNESS * 0.5,
				numberCell(hover, columns, "axis_min_y_m"), 1.0e-15);
		assertEquals(numberCell(hover, columns, "center_y_m") + SOURCE_THICKNESS * 0.5,
				numberCell(hover, columns, "axis_max_y_m"), 1.0e-15);
		assertEquals(Math.PI * numberCell(hover, columns, "radius_m")
						* numberCell(hover, columns, "radius_m")
						* SOURCE_THICKNESS,
				numberCell(hover, columns, "source_volume_m3"), 1.0e-15);
		assertEquals(numberCell(hover, columns, "body_force_density_y_n_m3")
						* numberCell(hover, columns, "source_volume_m3"),
				numberCell(hover, columns, "total_force_y_n"), 1.0e-12);
		assertEquals(numberCell(hover, columns, "pressure_jump_pa") / SOURCE_THICKNESS,
				numberCell(hover, columns, "body_force_density_y_n_m3"), 1.0e-9);
		assertEquals(0.0, numberCell(hover, columns, "total_force_x_n"), 1.0e-15);
		assertTrue(numberCell(hover, columns, "far_wake_axial_velocity_y_mps") > 0.0);
		assertTrue(Math.abs(numberCell(hover, columns, "reaction_torque_y_nm")) > 0.0);
		assertEquals(numberCell(hover, columns, "reaction_torque_y_nm"),
				numberCell(hover, columns, "wake_angular_momentum_torque_y_nm"), 1.0e-18);
		assertEquals(numberCell(hover, columns, "wake_angular_momentum_torque_y_nm")
						/ numberCell(hover, columns, "source_volume_m3"),
				numberCell(hover, columns, "wake_angular_momentum_torque_density_y_nm_m3"),
				1.0e-12);
		assertTrue(numberCell(hover, columns, "wake_tangential_velocity_mps") > 0.0);
		double swirlReferenceRadius = Math.sqrt(
				squared(numberCell(hover, columns, "wake_swirl_reference_point_x_m")
						- numberCell(hover, columns, "center_x_m"))
						+ squared(numberCell(hover, columns, "wake_swirl_reference_point_y_m")
						- numberCell(hover, columns, "center_y_m"))
						+ squared(numberCell(hover, columns, "wake_swirl_reference_point_z_m")
						- numberCell(hover, columns, "center_z_m")));
		double expectedSourcePlaneSwirlSpeed =
				2.0
						* swirlReferenceRadius
						* swirlReferenceRadius
						/ (numberCell(hover, columns, "radius_m")
						* numberCell(hover, columns, "radius_m"))
						* numberCell(hover, columns, "wake_tangential_velocity_mps");
		double swirlVelocityLength = Math.sqrt(
				numberCell(hover, columns, "wake_swirl_velocity_x_mps")
						* numberCell(hover, columns, "wake_swirl_velocity_x_mps")
						+ numberCell(hover, columns, "wake_swirl_velocity_y_mps")
						* numberCell(hover, columns, "wake_swirl_velocity_y_mps")
						+ numberCell(hover, columns, "wake_swirl_velocity_z_mps")
						* numberCell(hover, columns, "wake_swirl_velocity_z_mps"));
		assertEquals(expectedSourcePlaneSwirlSpeed, swirlVelocityLength, 1.0e-12);
		assertTrue(numberCell(hover, columns, "wake_swirl_reference_point_x_m")
				> numberCell(hover, columns, "center_x_m"));
		assertTrue(numberCell(hover, columns, "wake_swirl_kinetic_power_w") > 0.0);
		assertTrue(numberCell(hover, columns, "total_wake_kinetic_power_w")
				> numberCell(hover, columns, "wake_swirl_kinetic_power_w"));

		assertEquals("false", textCell(highBlock, columns, "source_enabled"));
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", textCell(highBlock, columns, "lookup_status"));
		assertEquals(0.0, numberCell(highBlock, columns, "body_force_density_y_n_m3"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "total_force_y_n"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "reaction_torque_y_nm"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "wake_angular_momentum_torque_y_nm"), 1.0e-15);
		assertEquals(0.0,
				numberCell(highBlock, columns, "wake_angular_momentum_torque_density_y_nm_m3"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "wake_tangential_velocity_mps"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "wake_swirl_velocity_x_mps"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "wake_swirl_velocity_y_mps"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "wake_swirl_velocity_z_mps"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "total_wake_kinetic_power_w"), 1.0e-15);
		assertTrue(numberCell(highBlock, columns, "source_volume_m3") > 0.0);
	}

	@Test
	void writeCreatesParentDirectoriesAndCsvFile(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("nested").resolve("openfoam-source-table.csv");
		CtCpJActuatorDiskOpenFoamSourceTableExporter.write("apDrone", output, RHO);

		List<String> lines = Files.readAllLines(output);
		assertEquals(41, lines.size());
		assertTrue(lines.get(0).contains("selection_shape"));
		assertTrue(lines.get(0).contains("body_force_density_y_n_m3"));
		assertTrue(lines.get(0).contains("total_force_y_n"));
		assertTrue(lines.get(0).contains("wake_angular_momentum_torque_density_y_nm_m3"));
		assertTrue(lines.get(0).contains("wake_swirl_velocity_y_mps"));
		assertTrue(lines.get(0).contains("wake_swirl_kinetic_power_w"));
	}

	@Test
	void versionedApDroneOpenFoamSourceTablePacketMatchesExporter() throws IOException {
		List<String> expected = CtCpJActuatorDiskOpenFoamSourceTableExporter.csvLines("apDrone", RHO);
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_openfoam_source_table_packet.csv");
		List<String> actual = Files.readAllLines(packet);

		assertEquals(expected, actual);
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_source_hover,raw_source,0,"
						+ "ctcpj_apdrone_static_anchored_source_hover_raw_source_rotor_0,true,")));
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_source_high_j_block,raw_source,0,")
						&& line.contains(",false,OUT_OF_ENVELOPE_BLOCKED,")));
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

	private static double vectorLength(String line, Map<String, Integer> columns, String prefix) {
		return Math.sqrt(vectorDot(line, columns, prefix, prefix));
	}

	private static double vectorDot(
			String line,
			Map<String, Integer> columns,
			String firstPrefix,
			String secondPrefix
	) {
		return numberCell(line, columns, firstPrefix + "_x") * numberCell(line, columns, secondPrefix + "_x")
				+ numberCell(line, columns, firstPrefix + "_y") * numberCell(line, columns, secondPrefix + "_y")
				+ numberCell(line, columns, firstPrefix + "_z") * numberCell(line, columns, secondPrefix + "_z");
	}

	private static double squared(double value) {
		return value * value;
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
