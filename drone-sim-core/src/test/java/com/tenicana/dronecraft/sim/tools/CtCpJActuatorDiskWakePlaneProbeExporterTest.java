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

class CtCpJActuatorDiskWakePlaneProbeExporterTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;

	@Test
	void csvLinesExportDeterministicWakePlaneRows() {
		List<String> lines = CtCpJActuatorDiskWakePlaneProbeExporter.csvLines(
				"apDrone",
				RHO,
				SOURCE_THICKNESS,
				25.0,
				0.0
		);
		Map<String, Integer> columns = columns(lines);
		String hoverCenter = lineFor(lines, columns, "static_anchored_source_hover", "raw_source", 0, 1.0, "center");
		String hoverOuter = lineFor(lines, columns, "static_anchored_source_hover", "raw_source", 0, 1.0, "u_pos_1p25");
		String highJInner = lineFor(lines, columns, "static_anchored_source_high_j", "raw_source", 0, 0.5, "v_pos_0p5");
		String highJEdge = lineFor(lines, columns, "static_anchored_source_high_j", "raw_source", 0, 0.5, "v_pos_1p0");
		String skewCenter = lineFor(lines, columns,
				"static_anchored_source_mid_j_skew", "raw_source", 0, 2.0, "center");
		String highBlockCenter = lineFor(
				lines,
				columns,
				"static_anchored_source_high_j_block",
				"raw_source",
				0,
				4.0,
				"center"
		);

		assertEquals(2497, lines.size());
		assertTrue(lines.get(0).startsWith("preset,case,row_kind,rotor_index,source_name"));
		assertTrue(lines.get(0).contains("plane_sample"));
		assertTrue(lines.get(0).contains("wake_centerline_direction_world_y"));
		assertTrue(lines.get(0).contains("wake_plane_tangent_u_world_x"));
		assertTrue(lines.get(0).contains("wake_support_radius_m"));
		assertTrue(lines.get(0).contains("expected_top_hat_velocity_world_y_mps"));
		assertTrue(lines.get(0).contains("expected_freestream_velocity_world_y_mps"));
		assertTrue(lines.get(0).contains("expected_wake_excess_velocity_world_y_mps"));

		assertEquals("wake_plane_top_hat", textCell(hoverCenter, columns, "probe_kind"));
		assertEquals("centerline", textCell(hoverCenter, columns, "probe_region"));
		assertEquals(0.0, numberCell(hoverCenter, columns, "plane_radial_fraction"), 1.0e-15);
		assertEquals(numberCell(hoverCenter, columns, "disk_center_world_y_m")
						+ numberCell(hoverCenter, columns, "disk_normal_world_y")
						* numberCell(hoverCenter, columns, "probe_distance_m"),
				numberCell(hoverCenter, columns, "probe_point_world_y_m"), 1.0e-15);
		assertTrue(numberCell(hoverCenter, columns, "expected_top_hat_speed_mps") > 0.0);
		assertEquals(numberCell(hoverCenter, columns, "expected_top_hat_speed_mps"),
				numberCell(hoverCenter, columns, "expected_axial_velocity_mps"), 1.0e-12);
		assertEquals(0.0, numberCell(hoverCenter, columns, "expected_transverse_velocity_mps"), 1.0e-12);
		assertEquals(0.0, numberCell(hoverCenter, columns, "expected_freestream_speed_mps"), 1.0e-12);
		assertEquals(numberCell(hoverCenter, columns, "expected_top_hat_velocity_world_y_mps"),
				numberCell(hoverCenter, columns, "expected_wake_excess_velocity_world_y_mps"), 1.0e-12);
		assertEquals(numberCell(hoverCenter, columns, "expected_top_hat_speed_mps"),
				numberCell(hoverCenter, columns, "expected_wake_excess_speed_mps"), 1.0e-12);

		assertEquals("outer_reference", textCell(hoverOuter, columns, "probe_region"));
		assertEquals(1.25, numberCell(hoverOuter, columns, "plane_radial_fraction"), 1.0e-15);
		assertEquals(numberCell(hoverOuter, columns, "disk_center_world_x_m")
						+ numberCell(hoverOuter, columns, "disk_tangent_u_world_x")
						* 1.25
						* numberCell(hoverOuter, columns, "disk_radius_m"),
				numberCell(hoverOuter, columns, "probe_point_world_x_m"), 1.0e-15);
		assertEquals(0.0, numberCell(hoverOuter, columns, "expected_top_hat_speed_mps"), 1.0e-15);
		assertEquals(0.0, numberCell(hoverOuter, columns, "expected_wake_excess_speed_mps"), 1.0e-15);
		assertEquals(0.0, numberCell(hoverOuter, columns, "pressure_jump_pa"), 1.0e-15);

		assertEquals("wake_core_top_hat", textCell(highJInner, columns, "probe_region"));
		assertTrue(numberCell(highJInner, columns, "expected_axial_velocity_mps") > 0.0);
		assertEquals("outer_reference", textCell(highJEdge, columns, "probe_region"));
		assertEquals(-1.0, numberCell(highJEdge, columns, "disk_normal_world_x"), 1.0e-12);
		assertEquals(1.0, numberCell(highJEdge, columns, "plane_radial_fraction"), 1.0e-15);
		assertTrue(numberCell(highJEdge, columns, "wake_support_radius_m")
				< numberCell(highJEdge, columns, "disk_radius_m"));
		assertEquals(numberCell(highJEdge, columns, "disk_center_world_z_m")
						+ numberCell(highJEdge, columns, "disk_tangent_v_world_z")
						* numberCell(highJEdge, columns, "disk_radius_m"),
				numberCell(highJEdge, columns, "probe_point_world_z_m"), 1.0e-15);
		assertEquals(0.0, numberCell(highJEdge, columns, "expected_axial_velocity_mps"), 1.0e-15);
		assertEquals(0.0, numberCell(highJEdge, columns, "expected_transverse_velocity_mps"), 1.0e-9);

		assertEquals("centerline", textCell(skewCenter, columns, "probe_region"));
		assertTrue(numberCell(skewCenter, columns, "wake_centerline_direction_world_x") < 0.0);
		assertTrue(numberCell(skewCenter, columns, "wake_centerline_direction_world_y") > 0.0);
		assertEquals(numberCell(skewCenter, columns, "disk_center_world_x_m")
						+ numberCell(skewCenter, columns, "wake_centerline_direction_world_x")
						* numberCell(skewCenter, columns, "probe_distance_m"),
				numberCell(skewCenter, columns, "probe_point_world_x_m"), 1.0e-15);
		assertEquals(numberCell(skewCenter, columns, "disk_center_world_y_m")
						+ numberCell(skewCenter, columns, "wake_centerline_direction_world_y")
						* numberCell(skewCenter, columns, "probe_distance_m"),
				numberCell(skewCenter, columns, "probe_point_world_y_m"), 1.0e-15);
		assertEquals(0.0, vectorDot(skewCenter, columns,
				"wake_centerline_direction_world", "wake_plane_tangent_u_world"), 1.0e-15);
		assertEquals(0.0, vectorDot(skewCenter, columns,
				"wake_centerline_direction_world", "wake_plane_tangent_v_world"), 1.0e-15);
		assertEquals(-2.4, numberCell(skewCenter, columns,
				"expected_freestream_velocity_world_x_mps"), 1.0e-15);
		assertEquals(2.4, numberCell(skewCenter, columns,
				"expected_wake_excess_velocity_world_x_mps"), 1.0e-15);
		assertTrue(numberCell(skewCenter, columns, "expected_wake_excess_speed_mps") > 0.0);

		assertEquals("false", textCell(highBlockCenter, columns, "source_enabled"));
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", textCell(highBlockCenter, columns, "lookup_status"));
		assertEquals(0.0, numberCell(highBlockCenter, columns, "expected_top_hat_speed_mps"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlockCenter, columns, "expected_freestream_speed_mps"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlockCenter, columns, "expected_wake_excess_speed_mps"), 1.0e-15);
		assertEquals(0.0, numberCell(highBlockCenter, columns, "total_force_world_y_n"), 1.0e-15);
	}

	@Test
	void writeCreatesParentDirectoriesAndCsvFile(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("nested").resolve("wake-plane-probes.csv");
		CtCpJActuatorDiskWakePlaneProbeExporter.write("apDrone", output, RHO);

		List<String> lines = Files.readAllLines(output);
		assertEquals(2497, lines.size());
		assertTrue(lines.get(0).contains("plane_offset_u_radius"));
		assertTrue(lines.get(0).contains("wake_centerline_direction_world_y"));
		assertTrue(lines.get(0).contains("probe_region"));
		assertTrue(lines.get(0).contains("expected_axial_velocity_mps"));
		assertTrue(lines.get(0).contains("expected_freestream_velocity_world_y_mps"));
		assertTrue(lines.get(0).contains("expected_wake_excess_velocity_world_y_mps"));
	}

	@Test
	void versionedApDroneWakePlaneProbePacketMatchesExporter() throws IOException {
		List<String> expected = CtCpJActuatorDiskWakePlaneProbeExporter.csvLines("apDrone", RHO);
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_wake_plane_probe_packet.csv");
		List<String> actual = Files.readAllLines(packet);

		assertEquals(expected, actual);
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_source_hover,raw_source,0,"
						+ "ctcpj_apdrone_static_anchored_source_hover_raw_source_rotor_0,true,"
						+ "INTERPOLATED,false,false,wake_plane_top_hat,center,1.00000000000000,")));
		assertTrue(actual.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_source_high_j_block,raw_source,0,")
						&& line.contains(",false,OUT_OF_ENVELOPE_BLOCKED,false,true,wake_plane_top_hat,center,"
								+ "4.00000000000000,")));
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
			double probeDistanceRadius,
			String planeSample
	) {
		int caseColumn = columns.get("case");
		int rowKindColumn = columns.get("row_kind");
		int rotorIndexColumn = columns.get("rotor_index");
		int probeDistanceRadiusColumn = columns.get("probe_distance_radius");
		int planeSampleColumn = columns.get("plane_sample");
		return lines.stream()
				.skip(1)
				.filter(line -> {
					String[] cells = line.split(",", -1);
					return cells[caseColumn].equals(caseName)
							&& cells[rowKindColumn].equals(rowKind)
							&& Integer.parseInt(cells[rotorIndexColumn]) == rotorIndex
							&& Math.abs(Double.parseDouble(cells[probeDistanceRadiusColumn])
									- probeDistanceRadius) < 1.0e-15
							&& cells[planeSampleColumn].equals(planeSample);
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

	private static double vectorDot(
			String line,
			Map<String, Integer> columns,
			String firstPrefix,
			String secondPrefix
	) {
		return numberCell(line, columns, firstPrefix + "_x")
				* numberCell(line, columns, secondPrefix + "_x")
				+ numberCell(line, columns, firstPrefix + "_y")
				* numberCell(line, columns, secondPrefix + "_y")
				+ numberCell(line, columns, firstPrefix + "_z")
				* numberCell(line, columns, secondPrefix + "_z");
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
