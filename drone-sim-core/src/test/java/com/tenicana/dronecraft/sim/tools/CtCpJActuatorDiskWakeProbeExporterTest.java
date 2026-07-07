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

class CtCpJActuatorDiskWakeProbeExporterTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;

	@Test
	void csvLinesExportDeterministicCenterlineProbeRows() {
		List<String> lines = CtCpJActuatorDiskWakeProbeExporter.csvLines(
				"apDrone",
				RHO,
				SOURCE_THICKNESS,
				25.0,
				0.0
		);
		Map<String, Integer> columns = columns(lines);
		String hover = lineFor(lines, columns, "static_anchored_source_hover", "raw_source", 0, 1.0);
		String midJ = lineFor(lines, columns, "static_anchored_source_mid_j", "raw_source", 0, 2.0);
		String highJ = lineFor(lines, columns, "static_anchored_source_high_j", "raw_source", 0, 0.5);
		String highBlock = lineFor(lines, columns, "static_anchored_source_high_j_block", "raw_source", 0, 4.0);

		assertEquals(161, lines.size());
		assertTrue(lines.get(0).startsWith("preset,case,row_kind,rotor_index,source_name"));
		assertTrue(lines.get(0).contains("probe_point_world_y_m"));
		assertTrue(lines.get(0).contains("expected_far_wake_speed_mps"));

		assertEquals("true", textCell(hover, columns, "source_enabled"));
		assertEquals("centerline_axial", textCell(hover, columns, "probe_kind"));
		assertEquals(1.0, numberCell(hover, columns, "probe_distance_radius"), 1.0e-15);
		assertEquals(numberCell(hover, columns, "disk_radius_m"),
				numberCell(hover, columns, "probe_distance_m"), 1.0e-15);
		assertEquals(numberCell(hover, columns, "disk_center_world_y_m")
						+ numberCell(hover, columns, "disk_normal_world_y")
						* numberCell(hover, columns, "probe_distance_m"),
				numberCell(hover, columns, "probe_point_world_y_m"), 1.0e-15);
		assertEquals(0.0, numberCell(hover, columns, "expected_far_wake_velocity_world_x_mps"), 1.0e-15);
		assertTrue(numberCell(hover, columns, "expected_far_wake_velocity_world_y_mps") > 0.0);
		assertEquals(vectorLength(
						hover,
						columns,
						"expected_far_wake_velocity_world",
						"_mps"),
				numberCell(hover, columns, "expected_far_wake_speed_mps"), 1.0e-12);
		assertEquals(numberCell(hover, columns, "pressure_jump_pa") / SOURCE_THICKNESS,
				numberCell(hover, columns, "body_force_density_world_y_n_m3"), 1.0e-9);
		assertEquals(numberCell(hover, columns, "body_force_density_world_y_n_m3")
						* Math.PI
						* numberCell(hover, columns, "disk_radius_m")
						* numberCell(hover, columns, "disk_radius_m")
						* SOURCE_THICKNESS,
				numberCell(hover, columns, "total_force_world_y_n"), 1.0e-12);

		assertEquals("true", textCell(midJ, columns, "source_enabled"));
		assertEquals(0.4064, numberCell(midJ, columns, "query_j"), 1.0e-12);
		assertTrue(numberCell(midJ, columns, "expected_far_wake_speed_mps") > 0.0);
		assertTrue(numberCell(midJ, columns, "eta") > 0.0);

		assertEquals("true", textCell(highJ, columns, "source_enabled"));
		assertEquals(0.73152, numberCell(highJ, columns, "query_j"), 1.0e-12);
		assertEquals(numberCell(highJ, columns, "disk_center_world_x_m")
						+ numberCell(highJ, columns, "disk_normal_world_x")
						* numberCell(highJ, columns, "probe_distance_m"),
				numberCell(highJ, columns, "probe_point_world_x_m"), 1.0e-15);
		assertTrue(numberCell(highJ, columns, "expected_far_wake_speed_mps") > 0.0);
		assertTrue(numberCell(highJ, columns, "cp") > numberCell(midJ, columns, "cp"));

		assertEquals("false", textCell(highBlock, columns, "source_enabled"));
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", textCell(highBlock, columns, "lookup_status"));
		assertEquals(4.0 * numberCell(highBlock, columns, "disk_radius_m"),
				numberCell(highBlock, columns, "probe_distance_m"), 1.0e-15);
		assertEquals(numberCell(highBlock, columns, "disk_center_world_y_m")
						+ numberCell(highBlock, columns, "disk_normal_world_y")
						* numberCell(highBlock, columns, "probe_distance_m"),
				numberCell(highBlock, columns, "probe_point_world_y_m"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "expected_far_wake_speed_mps"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "body_force_density_world_y_n_m3"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlock, columns, "total_force_world_y_n"), 1.0e-15);
	}

	@Test
	void writeCreatesParentDirectoriesAndCsvFile(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("nested").resolve("wake-probes.csv");
		CtCpJActuatorDiskWakeProbeExporter.write("apDrone", output, RHO);

		List<String> lines = Files.readAllLines(output);
		assertEquals(161, lines.size());
		assertTrue(lines.get(0).contains("probe_distance_radius"));
		assertTrue(lines.get(0).contains("source_bounding_sphere_radius_m"));
		assertTrue(lines.get(0).contains("total_force_world_y_n"));
	}

	@Test
	void versionedApDroneWakeProbePacketMatchesExporter() throws IOException {
		List<String> expected = CtCpJActuatorDiskWakeProbeExporter.csvLines("apDrone", RHO);
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_wake_probe_packet.csv");
		List<String> actual = Files.readAllLines(packet);

		assertEquals(expected, actual);
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_source_hover,raw_source,0,"
						+ "ctcpj_apdrone_static_anchored_source_hover_raw_source_rotor_0,true,"
						+ "INTERPOLATED,false,false,centerline_axial,1.00000000000000,")));
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_source_high_j_block,raw_source,0,")
						&& line.contains(",false,OUT_OF_ENVELOPE_BLOCKED,false,true,centerline_axial,4.00000000000000,")));
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
			int rotorIndex,
			double probeDistanceRadius
	) {
		int caseColumn = columns.get("case");
		int rowKindColumn = columns.get("row_kind");
		int rotorIndexColumn = columns.get("rotor_index");
		int probeDistanceRadiusColumn = columns.get("probe_distance_radius");
		return lines.stream()
				.skip(1)
				.filter(line -> {
					String[] cells = line.split(",", -1);
					return cells[caseColumn].equals(caseName)
							&& cells[rowKindColumn].equals(rowKind)
							&& Integer.parseInt(cells[rotorIndexColumn]) == rotorIndex
							&& Math.abs(Double.parseDouble(cells[probeDistanceRadiusColumn])
									- probeDistanceRadius) < 1.0e-15;
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

	private static double vectorLength(
			String line,
			Map<String, Integer> columns,
			String prefix,
			String suffix
	) {
		double x = numberCell(line, columns, prefix + "_x" + suffix);
		double y = numberCell(line, columns, prefix + "_y" + suffix);
		double z = numberCell(line, columns, prefix + "_z" + suffix);
		return Math.sqrt(x * x + y * y + z * z);
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
